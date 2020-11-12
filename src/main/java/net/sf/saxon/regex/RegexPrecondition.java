////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

/**
 * A precondition that must be true if a regular expression is to match
 */
public class RegexPrecondition {

    public Operation operation;
    public int fixedPosition;
    public int minPosition;

    /**
     * Create a precondition for a regular expression to be true
     * @param op the operation to be performed. The precondition is true if
     *           op.iterateMatches(p).hasNext() is true at some position p,
     *           subject to the constraints in the other two arguments
     * @param fixedPos indicates that the operation only needs to be tested
     *                 at this position. Used when all previous parts of the
     *                 regular expression are fixed length, and the regex
     *                 starts with a "^" anchor. Defaults to -1, indicating
     *                 that the operation needs to be tested at every position.
     * @param minPos   indicates that the operation needs to be tested at this
     *                 position and all subsequent positions within the input
     *                 string (until a match is found or the end of the string
     *                 is reached). Defaults to 0.
     */

    public RegexPrecondition(Operation op, int fixedPos, int minPos) {
        this.operation = op;
        this.fixedPosition = fixedPos;
        this.minPosition = minPos;
    }
}

