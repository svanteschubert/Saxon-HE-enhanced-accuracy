////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.transpile.CSharpInnerClass;
import net.sf.saxon.z.IntIterator;

/**
 * Handle a reluctant repetition (with possible min and max) where the
 * size of the repeated unit is fixed.
 */

public class OpReluctantFixed extends OpRepeat {
    private final int len;

    OpReluctantFixed(Operation op, int min, int max, int len) {
        super(op, min, max, false);
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
        op = op.optimize(program, flags);
        return this;
    }

    @Override
    @CSharpInnerClass(outer = true, extra = {
            "Saxon.Hej.regex.REMatcher matcher",
            "int position"})
    public IntIterator iterateMatches(final REMatcher matcher, final int position) {
        return new IntIterator() {
            private int pos = position;
            private int count = 0;
            private boolean started = false;

            @Override
            public boolean hasNext() {
                if (!started) {
                    started = true;
                    while (count < min) {
                        IntIterator child = op.iterateMatches(matcher, pos);
                        if (child.hasNext()) {
                            pos = child.next();
                            count++;
                        } else {
                            return false;
                        }
                    }
                    return true;
                }
                if (count < max) {
                    matcher.clearCapturedGroupsBeyond(pos);
                    IntIterator child = op.iterateMatches(matcher, pos);
                    if (child.hasNext()) {
                        pos = child.next();
                        count++;
                        return true;
                    }
                }
                return false;
            }

            @Override
            public int next() {
                return pos;
            }
        };
    }
}

