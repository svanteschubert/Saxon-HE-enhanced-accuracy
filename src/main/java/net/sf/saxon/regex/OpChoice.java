////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.regex.charclass.CharacterClass;
import net.sf.saxon.regex.charclass.EmptyCharacterClass;
import net.sf.saxon.transpile.CSharpInnerClass;
import net.sf.saxon.transpile.CSharpSuppressWarnings;
import net.sf.saxon.z.IntIterator;

import java.util.Iterator;
import java.util.List;

/**
 * A choice of several branches within a regular expression
 */

public class OpChoice extends Operation {

    List<Operation> branches;

    OpChoice(List<Operation> branches) {
        this.branches = branches;
    }

    @Override
    public int getMatchLength() {
        int fixed = branches.get(0).getMatchLength();
        for (int i = 1; i < branches.size(); i++) {
            if (branches.get(i).getMatchLength() != fixed) {
                return -1;
            }
        }
        return fixed;
    }

    @Override
    public int getMinimumMatchLength() {
        int min = branches.get(0).getMinimumMatchLength();
        for (int i = 1; i < branches.size(); i++) {
            int m = branches.get(i).getMinimumMatchLength();
            if (m < min) {
                min = m;
            }
        }
        return min;
    }

    @Override
    public int matchesEmptyString() {
        int m = 0;
        for (Operation branch : branches) {
            int b = branch.matchesEmptyString();
            if (b != MATCHES_ZLS_NEVER) {
                m |= b;
            }
        }
        return m;
    }

    /**
     * Ask whether the expression is allowed within a lookbehind
     */

    @Override
    public boolean isAllowedWithinLookbehind() {
        for (Operation op : branches) {
            if (!op.isAllowedWithinLookbehind()) {
                return false;
            }
            if (op.getMatchLength() == -1) {
                throw new RESyntaxException("Lookbehind alternatives must all be fixed-length");
            }
        }
        return true;
    }

    @Override
    public boolean containsCapturingExpressions() {
        for (Operation o : branches) {
            if (o instanceof OpCapture || o.containsCapturingExpressions()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public CharacterClass getInitialCharacterClass(boolean caseBlind) {
        CharacterClass result = EmptyCharacterClass.getInstance();
        for (Operation o : branches) {
            result = RECompiler.makeUnion(result, o.getInitialCharacterClass(caseBlind));
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
        for (Operation o : branches) {
            max = Math.max(max, o.getMaxLoopingDepth());
        }
        return max;
    }


    @Override
    public Operation optimize(REProgram program, REFlags flags) {
        for (int i = 0; i < branches.size(); i++) {
            Operation o1 = branches.get(i);
            Operation o2 = o1.optimize(program, flags);
            if (o1 != o2) {
                branches.set(i, o2);
            }
        }
        return this;
    }

    @Override
    @CSharpInnerClass(outer = true, extra = {"Saxon.Hej.regex.REMatcher matcher", "int position"})
    @CSharpSuppressWarnings("UnsafeIteratorConversion")
    public IntIterator iterateMatches(final REMatcher matcher, final int position) {
        return new IntIterator() {
            final Iterator<Operation> branchIter = branches.iterator();
            IntIterator currentIter = null;
            Operation currentOp = null;

            @Override
            @CSharpSuppressWarnings("UnsafeIteratorConversion")
            public boolean hasNext() {
                while (true) {
                    if (currentIter == null) {
                        if (branchIter.hasNext()) {
                            matcher.clearCapturedGroupsBeyond(position);
                            currentOp = branchIter.next();
                            currentIter = currentOp.iterateMatches(matcher, position);
                        } else {
                            return false;
                        }
                    }
                    if (currentIter.hasNext()) {
                        return true;
                    } else {
                        currentIter = null;
                        //continue;
                    }
                }
            }

            @Override
            public int next() {
                return currentIter.next();
            }

        };
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
        fsb.append("(?:");
        boolean first = true;
        for (Operation branch : branches) {
            if (first) {
                first = false;
            } else {
                fsb.append('|');
            }
            fsb.append(branch.display());
        }
        fsb.append(")");
        return fsb.toString();
    }
}

