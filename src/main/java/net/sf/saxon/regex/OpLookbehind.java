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

import java.util.ArrayList;
import java.util.List;

/**
 * Lookbehind assertion (positive or negative)
 */

public class OpLookbehind extends Operation {

    final private boolean positive;
    final private Operation childOp;
    private final List<Operation> alternatives = new ArrayList<>();

    OpLookbehind(Operation childOp, boolean positive) throws RESyntaxException {
        this.childOp = childOp;
        if (!childOp.isAllowedWithinLookbehind()) {
            throw new RESyntaxException("Disallowed construct within lookbehind: " + childOp.display());
        }
        if (childOp instanceof OpChoice) {
            for (Operation op : ((OpChoice) childOp).branches) {
                if (op.getMatchLength() == -1) {
                    throw new RESyntaxException("Lookbehind alternatives must all be fixed-length");
                }
                alternatives.add(op);
            }
        } else {
            if (childOp.getMatchLength() == -1) {
                throw new RESyntaxException("Lookbehind expressions must be fixed-length");
            }
            alternatives.add(childOp);
        }
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
    public IntIterator iterateMatches(final REMatcher matcher, final int position) {
        boolean foundMatch = false;
        for (Operation op : alternatives) {
            int len = op.getMatchLength();
            if (len <= position) {
                IntIterator prev = op.iterateMatches(matcher, position - len);
                if (prev.hasNext()) {
                    foundMatch = true;
                    break;
                }
            }
        }
        if (foundMatch == positive) {
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
        return "(?<" + (positive ? '=' : '!') + childOp.display() + ")";
    }


}
