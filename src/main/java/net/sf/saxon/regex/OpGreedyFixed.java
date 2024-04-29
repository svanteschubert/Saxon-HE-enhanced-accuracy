////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.expr.sort.EmptyIntIterator;
import net.sf.saxon.z.IntIterator;
import net.sf.saxon.z.IntStepIterator;

/**
 * Handle a greedy repetition (with possible min and max) where the
 * size of the repeated unit is fixed.
 */

public class OpGreedyFixed extends OpRepeat {
    private final int len;

    OpGreedyFixed(Operation op, int min, int max, int len) {
        super(op, min, max, true);
        this.len = len;
    }

    @Override
    public int getMatchLength() {
        return min == max ? min * len : -1;
    }

    @Override
    public int matchesEmptyString() {
        if (min == 0) {
            return MATCHES_ZLS_ANYWHERE;
        }
        return op.matchesEmptyString();
    }

    @Override
    public Operation optimize(REProgram program, REFlags flags) {
        if (max == 0) {
            return new OpNothing();
        }
        if (op.getMatchLength() == 0) {
            return op;
        }
        op = op.optimize(program, flags);
        return this;
    }

    @Override
    public IntIterator iterateMatches(REMatcher matcher, int position) {
        int guard = matcher.search.length32();
        if (max < Integer.MAX_VALUE) {
            guard = Math.min(guard, position + len * max);
        }
        if (position >= guard && min > 0) {
            return EmptyIntIterator.getInstance();
        }

        int p = position;
        int matches = 0;
        while (p <= guard) {
            IntIterator it = op.iterateMatches(matcher, p);
            boolean matched = false;
            if (it.hasNext()) {
                matched = true;
                it.next();
            }
            if (matched) {
                matches++;
                p += len;
                if (matches == max) {
                    break;
                }
            } else {
                break;
            }
        }
        if (matches < min) {
            return EmptyIntIterator.getInstance();
        }

        return new IntStepIterator(p, -len, position + len * min);
    }
}

