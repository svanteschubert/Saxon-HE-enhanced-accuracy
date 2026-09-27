////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.expr.sort.EmptyIntIterator;
import net.sf.saxon.z.IntIterator;
import net.sf.saxon.z.IntSingletonIterator;

/**
 * Lookahead assertion (positive or negative)
 */

public class OpLookahead extends Operation {

    final private boolean positive;
    private Operation childOp;

    OpLookahead(Operation childOp, boolean positive) {
        this.childOp = childOp;
        this.positive = positive;
    }

    @Override
    public int getMatchLength() {
        return 0;
    }

    @Override
    public int matchesEmptyString() {
        return 0;
    }

    @Override
    public boolean isAssertion() {
        return true;
    }

    @Override
    public Operation optimize(REProgram program, REFlags flags) {
        childOp = childOp.optimize(program, flags);
        return this;
    }

    /**
     * Get the maximum depth of looping within this operation
     *
     * @return the maximum number of nested iterations
     */
    @Override
    public int getMaxLoopingDepth() {
        return childOp.getMaxLoopingDepth();
    }

    @Override
    public IntIterator iterateMatches(final REMatcher matcher, final int position) {
        final IntIterator basis = childOp.iterateMatches(matcher, position);
        if (basis.hasNext() == positive) {
            // Need to call basis.next() in case this captures any captured groups
            if (positive) {
                basis.next();
            }
            return new IntSingletonIterator(position);
        } else {
            return EmptyIntIterator.getInstance();
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
        return "(?" + (positive ? '=' : '!') + childOp.display() + ")";
    }
}
