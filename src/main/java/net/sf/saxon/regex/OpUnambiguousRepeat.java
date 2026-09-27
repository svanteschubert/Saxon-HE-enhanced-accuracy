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
 * Handle a repetition where there is no ambiguity; if the repeated
 * term is matched in the string, then it cannot match anything other than
 * the repeated term. It is also used when the number of occurrences is
 * fixed. In this situation there will never be any need for
 * backtracking, so there is no need to keep any information to support
 * backtracking, and in addition, there is no distinction between greedy
 * and reluctant matching. This operation is used only for a repeated
 * atom or CharClass, which also means that if the repeated term matches
 * then it can only match in one way; a typical example is the term "A*"
 * in the regex "A*B".
 */

public class OpUnambiguousRepeat extends OpRepeat {

    OpUnambiguousRepeat(Operation op, int min, int max) {
        super(op, min, max, true);
    }

    @Override
    public int matchesEmptyString() {
        if (min == 0) {
            return MATCHES_ZLS_ANYWHERE;
        }
        return op.matchesEmptyString();
    }

    @Override
    public int getMatchLength() {
        if (op.getMatchLength() != -1 && min == max) {
            return op.getMatchLength() * min;
        } else {
            return -1;
        }
    }


    /**
     * Get the maximum depth of looping within this operation
     *
     * @return the maximum number of nested iterations
     */
    @Override
    public int getMaxLoopingDepth() {
        return op.getMaxLoopingDepth() + 1;
    }


    @Override
    public Operation optimize(REProgram program, REFlags flags) {
        op = op.optimize(program, flags);
        return this;
    }

    @Override
    public IntIterator iterateMatches(REMatcher matcher, int position) {
        int guard = matcher.search.length32();

        int p = position;
        int matches = 0;
        while (matches < max && p <= guard) {
            IntIterator it = op.iterateMatches(matcher, p);
            if (it.hasNext()) {
                matches++;
                p = it.next();
            } else {
                break;
            }
        }
        if (matches < min) {
            return EmptyIntIterator.getInstance();
        } else {
            return new IntSingletonIterator(p);
        }
    }
}

