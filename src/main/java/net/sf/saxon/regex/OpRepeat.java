////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.expr.sort.EmptyIntIterator;
import net.sf.saxon.regex.charclass.CharacterClass;
import net.sf.saxon.transpile.CSharpInnerClass;
import net.sf.saxon.z.IntIterator;
import net.sf.saxon.z.IntSingletonIterator;

import java.util.Stack;

/**
 * Handle a repetition (with possible min and max) where the
 * size of the repeated unit is variable.
 */

public class OpRepeat extends Operation {
    protected Operation op;
    protected int min;
    protected int max;
    boolean greedy;
    int loopingDepth = -1;

    OpRepeat(Operation op, int min, int max, boolean greedy) {
        this.op = op;
        this.min = min;
        this.max = max;
        this.greedy = greedy;
    }

    /**
     * Get the operation being repeated
     *
     * @return the child operation
     */

    Operation getRepeatedOperation() {
        return op;
    }

    @Override
    public int matchesEmptyString() {
        if (min == 0) {
            return MATCHES_ZLS_ANYWHERE;
        }
        return op.matchesEmptyString();
    }

    @Override
    public boolean containsCapturingExpressions() {
        return op instanceof OpCapture || op.containsCapturingExpressions();
    }

    @Override
    public CharacterClass getInitialCharacterClass(boolean caseBlind) {
        return op.getInitialCharacterClass(caseBlind);
    }

    @Override
    public int getMatchLength() {
        return min == max && op.getMatchLength() >= 0 ? min * op.getMatchLength() : -1;
    }

    @Override
    public int getMinimumMatchLength() {
        return min * op.getMinimumMatchLength();
    }

    /**
     * Get the maximum depth of looping within this operation
     *
     * @return the maximum number of nested iterations
     */
    @Override
    public int getMaxLoopingDepth() {
        if (loopingDepth < 0) {
            loopingDepth = op.getMaxLoopingDepth() + 1;
        }
        return loopingDepth;
    }


    @Override
    public Operation optimize(REProgram program, REFlags flags) {
        op = op.optimize(program, flags);
        if (min == 0 && op.matchesEmptyString() == MATCHES_ZLS_ANYWHERE) {
            // turns (a?)* into (a?)+
            min = 1;
        }
        return this;
    }

    @Override
    @CSharpInnerClass(outer = true, extra = {
            "Saxon.Hej.regex.REMatcher matcher",
            "System.Collections.Generic.Stack<Saxon.Hej.z.IntIterator> iterators",
            "System.Collections.Generic.Stack<System.Int32> positions",
            "int bound",
            "int position"})
    public IntIterator iterateMatches(final REMatcher matcher, int position) {
        final Stack<IntIterator> iterators = new Stack<>();
        final Stack<Integer> positions = new Stack<>();
        final int bound = Math.min(max, matcher.search.length32() - position + 1);
        int p = position;
        if (greedy) {
            // Prime the arrays first with iterators up to the maximum length, stopping if there is no match
            if (min == 0 && !matcher.history.isDuplicateZeroLengthMatch(this, position)) {
                // add a match at the current position if zero occurrences are allowed
                iterators.push(new IntSingletonIterator(position));
                positions.push(p);
            }
            for (int i = 0; i < bound; i++) {
                IntIterator it = op.iterateMatches(matcher, p);
                if (it.hasNext()) {
                    p = it.next();
                    iterators.push(it);
                    positions.push(p);
                } else if (iterators.isEmpty()) {
                    return EmptyIntIterator.getInstance();
                } else {
                    break;
                }
            }
            // Now return an iterator which returns all the matching positions in order
            IntIterator base = new IntIterator() {
                boolean primed = true;

                /**
                 * advance() moves to the next (potential) match position,
                 * ignoring constraints on the minimum number of occurrences
                 */

                private void advance() {
                    IntIterator top = iterators.peek();
                    if (top.hasNext()) {
                        int p = top.next();
                        positions.pop();
                        positions.push(p);
                        while (iterators.size() < bound) {  // bug 3787
                            IntIterator it = op.iterateMatches(matcher, p);
                            if (it.hasNext()) {
                                p = it.next();
                                iterators.push(it);
                                positions.push(p);
                            } else {
                                break;
                            }
                        }
                    } else {
                        iterators.pop();
                        positions.pop();
                    }
                }

                @Override
                public boolean hasNext() {
                    if (primed && iterators.size() >= min) {
                        return !iterators.isEmpty();
                    } else if (iterators.isEmpty()) {
                        return false;
                    } else {
                        do {
                            advance();
                        } while (iterators.size() < min && !iterators.isEmpty());
                        return !iterators.isEmpty();
                    }
                }

                @Override
                public int next() {
                    primed = false;
                    return positions.peek();
                }
            };
            return new ForceProgressIterator(base, getMaxLoopingDepth());
        } else {
            // reluctant (non-greedy) repeat.
            // rewritten for bug 3902
            IntIterator iter = new IntIterator() {
                private int pos = position;
                private int counter = 0;

                private void advance() {
                    IntIterator it = op.iterateMatches(matcher, pos);
                    if (it.hasNext()) {
                        pos = it.next();
                        if (++counter > max) {
                            pos = -1;
                        }
                    } else if (min == 0 && counter == 0) {
                        counter++;
                    } else {
                        pos = -1;
                    }
                }

                @Override
                public boolean hasNext() {
                    do {
                        advance();
                    } while (counter < min && pos >= 0);
                    return pos >= 0;
                }

                @Override
                public int next() {
                    return pos;
                }
            };
            return new ForceProgressIterator(iter, getMaxLoopingDepth());
        }
    }

    /**
     * Display the operation as a regular expression, possibly in abbreviated form
     *
     * @return the operation in a form that is recognizable as a regular expression or abbreviated
     * regular expression
     */
    @Override
    public String display() {
        String quantifier;
        if (min == 0 && max == Integer.MAX_VALUE) {
            quantifier = "*";
        } else if (min == 1 && max == Integer.MAX_VALUE) {
            quantifier = "+";
        } else if (min == 0 && max == 1) {
            quantifier = "?";
        } else {
            quantifier = "{" + min + "," + max + "}";
        }
        if (!greedy) {
            quantifier += "?";
        }
        return op.display() + quantifier;
    }
}
