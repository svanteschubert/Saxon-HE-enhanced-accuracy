////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;


import net.sf.saxon.regex.charclass.CharacterClass;
import net.sf.saxon.regex.charclass.EmptyCharacterClass;
import net.sf.saxon.z.IntIterator;

/**
 * Represents an operation or instruction in the regular expression program. The class Operation
 * is abstract, and has concrete subclasses for each kind of operation/instruction
 */
public abstract class Operation {

    protected static final int MATCHES_ZLS_AT_START = 1;
    protected static final int MATCHES_ZLS_AT_END = 2;
    protected static final int MATCHES_ZLS_ANYWHERE = 7;
    protected static final int MATCHES_ZLS_NEVER = 1024;

    /**
     * Get an iterator returning all the matches for this operation
     *
     * @param matcher  supplies the context for the matching; may be updated with information about
     *                 captured groups
     * @param position the start position to seek a match
     * @return an iterator returning the endpoints of all matches starting at the supplied position
     */

    public abstract IntIterator iterateMatches(REMatcher matcher, int position);

    /**
     * Get the length of the matches returned by this operation if they are fixed-length
     *
     * @return the length of the matches, or -1 if the length is variable
     */

    public int getMatchLength() {
        return -1;
    }

    /**
     * Get the minimum length of the matches returned by this operation
     * @return the length of the shortest string that will match the operation
     */

    public int getMinimumMatchLength() {
        int fixed = getMatchLength();
        return Math.max(fixed, 0);
    }

    /**
     * Ask whether the regular expression is known, after static analysis, to match a
     * zero-length string
     * @return a value indicating whether the regex is statically known to match
     * a zero-length string. Specifically:
     * <ul>
     *     <li>returns {@link #MATCHES_ZLS_AT_START}
     * if the expression is statically known to match a zero-length string at the
     * start of the supplied input;</li>
     * <li>returns
     * {@link #MATCHES_ZLS_AT_END} if it is statically known to match a zero-length
     * string at the end of the supplied input;</li>
     * <li>returns {@link #MATCHES_ZLS_ANYWHERE}
     * if it is statically known to match a zero-length string anywhere in the input.
     * </li>
     * <li>returns {@link #MATCHES_ZLS_NEVER} if it is statically known that the
     * regex will never match a zero length string.</li>
     * </ul>
     * Returning 0 means that it is not known statically whether or not the regex
     * will match a zero-length string; this case typically arises when back-references
     * are involved.
     */

    public abstract int matchesEmptyString();

    /**
     * Ask whether the expression contains any capturing sub-expressions
     * @return true if the expression contains any capturing sub-expressions (but not
     * if it is a capturing expression itself, unless it contains nested capturing
     * expressions)
     */

    public boolean containsCapturingExpressions() {
        return false;
    }

    /**
     * Ask whether the expression is a zero-width assertion
     */

    public boolean isAssertion() {
        return false;
    }

    /**
     * Ask whether the expression is allowed within a lookbehind
     */

    public boolean isAllowedWithinLookbehind() {
        return false;
    }

    /**
     * Get a CharacterClass identifying the set of characters that can appear as the first
     * character of a non-empty string that matches this term. This is allowed to be an
     * over-estimate (that is, the returned Character class must match every character
     * that can legitimately appear at the start of the matched string, but it can
     * also match other characters).
     * @param caseBlind true if case-blind matching is in force ("i" flag)
     */

    public CharacterClass getInitialCharacterClass(boolean caseBlind) {
        return EmptyCharacterClass.getComplement();
    }

    /**
     * Optimize the operation
     * @return the optimized operation
     * @param program the program being optimized
     * @param flags the regular expression flags
     */

    public Operation optimize(REProgram program, REFlags flags) {
        return this;
    }

    /**
     * Display the operation as a regular expression, possibly in abbreviated form
     * @return the operation in a form that is recognizable as a regular expression or abbreviated
     * regular expression
     */

    public abstract String display();

    /**
     * Get the maximum depth of looping within this operation
     * @return the maximum number of nested iterations
     */

    public int getMaxLoopingDepth() {
        return 0;
    }


    /**
     * The ForceProgressIterator is used to protect against non-termination; specifically,
     * iterators that return an infinite number of zero-length matches. After getting a
     * certain number of zero-length matches at the same position, hasNext() returns false.
     * (Potentially this gives problems with an expression such as (a?|b?|c?|d) that can
     * legitimately return more than one zero-length match).
     */

    protected static class ForceProgressIterator implements IntIterator {
        private final IntIterator base;
        int countZeroLength = 0;
        int currentPos = -1;
        int loopingDepth = 1;
        int maxTries = 10;

        ForceProgressIterator(IntIterator base, int loopingDepth) {
            this.base = base;
            this.loopingDepth = Math.max(loopingDepth, 1);
        }

        @Override
        public boolean hasNext() {
            return countZeroLength <= maxTries && base.hasNext();
        }

        @Override
        public int next() {
            int p = base.next();
            if (p == currentPos) {
                countZeroLength++;
            } else {
                countZeroLength = 0;
                currentPos = p;
                // See bug #6426. We're computing an upper bound on the number of different ways
                // that a position p in the input can be reached, essentially ((p+2) ^ n)/2 where n is
                // the maximum depth of looping.
                double limit = Math.min(Integer.MAX_VALUE, Math.pow(currentPos+2, loopingDepth)/2);
                maxTries = (int)limit;
            }
            return p;
        }
    }



}

