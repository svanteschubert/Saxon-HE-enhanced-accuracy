////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.regex.charclass.CharacterClass;
import net.sf.saxon.regex.charclass.EmptyCharacterClass;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpInnerClass;
import net.sf.saxon.z.IntIterator;

import java.util.List;
import java.util.Stack;

/**
 * A sequence of multiple pieces in a regular expression
 */

public class OpSequence extends Operation {
    protected final List<Operation> operations;

    OpSequence(List<Operation> operations) {
        this.operations = operations;
    }

    public List<Operation> getOperations() {
        return operations;
    }

    @Override
    public int getMatchLength() {
        int len = 0;
        for (Operation o : operations) {
            int i = o.getMatchLength();
            if (i == -1) {
                return -1;
            }
            len += i;
        }
        return len;
    }

    @Override
    public int getMinimumMatchLength() {
        int len = 0;
        for (Operation o : operations) {
            len += o.getMinimumMatchLength();
        }
        return len;
    }

    @Override
    public int matchesEmptyString() {

        // The operation matches empty anywhere if every suboperation matches empty anywhere
        boolean matchesEmptyAnywhere = true;
        for (Operation o : operations) {
            int m = o.matchesEmptyString();
            if (m == MATCHES_ZLS_NEVER) {
                return MATCHES_ZLS_NEVER;
            }
            if (m != MATCHES_ZLS_ANYWHERE) {
                matchesEmptyAnywhere = false;
                break;
            }
        }

        if (matchesEmptyAnywhere) {
            return MATCHES_ZLS_ANYWHERE;
        }

        // The operation matches BOL if every suboperation matches BOL (which includes
        // the case of matching empty anywhere)
        boolean matchesBOL = true;
        for (Operation o : operations) {
            if ((o.matchesEmptyString() & MATCHES_ZLS_AT_START) == 0) {
                matchesBOL = false;
                break;
            }
        }
        if (matchesBOL) {
            return MATCHES_ZLS_AT_START;
        }

        // The operation matches EOL if every suboperation matches EOL (which includes
        // the case of matching empty anywhere)
        boolean matchesEOL = true;

        for (Operation o : operations) {
            if ((o.matchesEmptyString() & MATCHES_ZLS_AT_END) == 0) {
                matchesEOL = false;
                break;
            }
        }
        if (matchesEOL) {
            return MATCHES_ZLS_AT_END;
        }

        return 0;
    }

    /**
     * Ask whether the expression is allowed within a lookbehind
     */

    @Override
    public boolean isAllowedWithinLookbehind() {
        for (Operation op : operations) {
            if (!op.isAllowedWithinLookbehind()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean containsCapturingExpressions() {
        for (Operation o : operations) {
            if (o instanceof OpCapture || o.containsCapturingExpressions()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public CharacterClass getInitialCharacterClass(boolean caseBlind) {
        CharacterClass result = EmptyCharacterClass.getInstance();
        for (Operation o : operations) {
            result = RECompiler.makeUnion(result, o.getInitialCharacterClass(caseBlind));
            if (o.matchesEmptyString() == MATCHES_ZLS_NEVER) {
                return result;
            }
        }
        return result;
    }

    /**
     * Get the maximum depth of looping within this operation
     *
     * @return the maximum number of nested iterations
     */
    @Override
    public int getMaxLoopingDepth() {
        int max = 0;
        for (Operation o : operations) {
            max = Math.max(max, o.getMaxLoopingDepth());
        }
        return max;
    }


    /**
     * Display the operation as a regular expression, possibly in abbreviated form
     *
     * @return the operation in a form that is recognizable as a regular expression or abbreviated
     * regular expression
     */
    @Override
    public String display() {
        StringBuilder fsb = new StringBuilder(64);
        for (Operation op : operations) {
            fsb.append(op.display());
        }
        return fsb.toString();
    }

    @Override
    public Operation optimize(REProgram program, REFlags flags) {
        if (operations.size() == 0) {
            return new OpNothing();
        } else if (operations.size() == 1) {
            return operations.get(0);
        } else {
            for (int i = 0; i < operations.size() - 1; i++) {
                Operation o1 = operations.get(i);
                Operation o2 = o1.optimize(program, flags);
                if (o1 != o2) {
                    operations.set(i, o2);
                }
                if (o2 instanceof OpRepeat) {
                    Operation o1r = ((OpRepeat) o1).getRepeatedOperation();
                    if (o1r instanceof OpAtom || o1r instanceof OpCharClass) {
                        Operation o2r = operations.get(i + 1);
                        if (((OpRepeat) o1).min == ((OpRepeat) o1).max ||
                                RECompiler.noAmbiguity(o1r, o2r, flags.isCaseIndependent(), !((OpRepeat) o1).greedy)) {
                            operations.set(i, new OpUnambiguousRepeat(o1r, ((OpRepeat) o1).min, ((OpRepeat) o1).max));
                        }
                    }
                }
            }
            return this;
        }


    }

    @Override
    @CSharpInnerClass(outer = true, extra = {
            "Saxon.Hej.regex.REMatcher matcher",
            "System.Collections.Generic.Stack<Saxon.Hej.z.IntIterator> iterators",
            "int backtrackingLimit",
            "Saxon.Hej.regex.REMatcher.CapturedGroupState savedState",
            "int position"})
    public IntIterator iterateMatches(final REMatcher matcher, final int position) {

        // A stack of iterators, one for each piece in the sequence
        final Stack<IntIterator> iterators = new Stack<>();
        final REMatcher.CapturedGroupState savedState =
                containsCapturingExpressions() ? matcher.captureState() : null;
        final int backtrackingLimit = matcher.getProgram().getBacktrackingLimit();

        return new IntIterator() {

            private boolean primed = false;
            private int nextPos;

            /**
             * Advance the current iterator if possible, getting the first match for all subsequent
             * iterators in the sequence. If we get all the way to the end of the sequence, return the
             * position in the input string that we have reached. If we don't get all the way to the
             * end of the sequence, work backwards getting the next match for each term in the sequence
             * until we find a route through.
             * @return if we find a match for the whole sequence, return the position in the input string
             * at which the match ends. Otherwise return -1.
             */

            private int advance() {
                int counter = 0;
                while (!iterators.isEmpty()) {
                    IntIterator top = iterators.peek();
                    while (top.hasNext()) {
                        int p = top.next();
                        matcher.clearCapturedGroupsBeyond(p);
                        int i = iterators.size();
                        if (i >= operations.size()) {
                            return p;
                        }
                        top = operations.get(i).iterateMatches(matcher, p);
                        iterators.push(top);
                    }
                    iterators.pop();
                    if (backtrackingLimit >= 0 && counter++ > backtrackingLimit) {
                        throw new UncheckedXPathException(new XPathException(
                                "Regex backtracking limit exceeded processing " +
                                        matcher.operation.display() + ". Simplify the regular expression, "
                                        + "or set Feature.REGEX_BACKTRACKING_LIMIT to -1 to remove this limit."));
                    }
                }
                if (savedState != null) {
                    matcher.resetState(savedState);
                }
                //matcher.clearCapturedGroupsBeyond(position);
                return -1;
            }


            @Override
            public boolean hasNext() {
                if (!primed) {
                    iterators.push(operations.get(0).iterateMatches(matcher, position));
                    primed = true;
                }
                nextPos = advance();
                return nextPos >= 0;
            }

            @Override
            public int next() {
                return nextPos;
            }
        };
    }

}

