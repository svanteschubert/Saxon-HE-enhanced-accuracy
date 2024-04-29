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
 * Open paren (captured group) within a regular expression
 */

public class OpCapture extends Operation {

    int groupNr;
    Operation childOp;

    OpCapture(Operation childOp, int group) {
        this.childOp = childOp;
        this.groupNr = group;
    }

    @Override
    public int getMatchLength() {
        return childOp.getMatchLength();
    }

    @Override
    public int getMinimumMatchLength() {
        return childOp.getMinimumMatchLength();
    }

    @Override
    public int matchesEmptyString() {
        return childOp.matchesEmptyString();
    }

    @Override
    public Operation optimize(REProgram program, REFlags flags) {
        childOp = childOp.optimize(program, flags);
        return this;
    }

    @Override
    @CSharpInnerClass(outer = true, extra = {
            "Saxon.Hej.regex.REMatcher matcher",
            "int position",
            "Saxon.Hej.z.IntIterator basis"
    })
    public IntIterator iterateMatches(final REMatcher matcher, final int position) {
        if ((matcher.program.optimizationFlags & REProgram.OPT_HASBACKREFS) != 0) {
            matcher.startBackref[groupNr] = position;
        }
        final IntIterator basis = childOp.iterateMatches(matcher, position);
        return new IntIterator() {
            @Override
            public boolean hasNext() {
                return basis.hasNext();
            }

            @Override
            public int next() {
                int next = basis.next();
                // Increase valid paren count
                if (groupNr >= matcher._captureState.parenCount) {
                    matcher._captureState.parenCount = groupNr + 1;
                }

                // Don't set paren if already set later on
                //if (matcher.getParenStart(groupNr) == -1) {
                matcher.setParenStart(groupNr, position);
                matcher.setParenEnd(groupNr, next);
                //}
                if ((matcher.program.optimizationFlags & REProgram.OPT_HASBACKREFS) != 0) {
                    matcher.startBackref[groupNr] = position;
                    matcher.endBackref[groupNr] = next;
                }
                return next;
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
        return "(" + childOp.display() + ")";
    }
}
