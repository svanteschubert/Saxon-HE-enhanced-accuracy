////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.transpile.CSharpInnerClass;
import net.sf.saxon.z.IntIterator;

/**
 * Operation that wraps a base operation and traces its execution
 */

public class OpTrace extends Operation {

    private Operation base;
    private static int counter = 0;

    OpTrace(Operation base) {
        this.base = base;
    }

    @Override
    @CSharpInnerClass(outer = true,
            extra = {
                    "Saxon.Hej.z.IntIterator baseIter",
                    "int iterNr"
            })
    public IntIterator iterateMatches(REMatcher matcher, int position) {
        final IntIterator baseIter = base.iterateMatches(matcher, position);
        final int iterNr = counter++;
        String clName = baseIter.getClass().getName();
        int lastDot = clName.lastIndexOf(".");
        String iterName = clName.substring(lastDot + 1);
        System.err.println("Iterating over " + base.getClass().getSimpleName() + " " +
                                   base.display() + " at position " + position + " returning " +
                                   iterName + " " + iterNr);
        return new IntIterator() {
            @Override
            public boolean hasNext() {
                boolean b = baseIter.hasNext();
                System.err.println("IntIterator " + iterNr + " hasNext() = " + b);
                return b;
            }

            @Override
            public int next() {
                int n = baseIter.next();
                System.err.println("IntIterator " + iterNr + " next() = " + n);
                return n;
            }
        };
    }

    @Override
    public int getMatchLength() {
        return base.getMatchLength();
    }

    @Override
    public int matchesEmptyString() {
        return base.matchesEmptyString();
    }


    /**
     * Get the maximum depth of looping within this operation
     *
     * @return the maximum number of nested iterations
     */
    @Override
    public int getMaxLoopingDepth() {
        return base.getMaxLoopingDepth();
    }


    @Override
    public Operation optimize(REProgram program, REFlags flags) {
        base = base.optimize(program, flags);
        return this;
    }

    /**
     * Display the operation as a regular expression, possibly in abbreviated form
     *
     * @return the operation in a form that is recognizable as a regular expression or abbreviated
     * regular expression
     */
    @Override
    public String display() {
        return base.display();
    }
}

