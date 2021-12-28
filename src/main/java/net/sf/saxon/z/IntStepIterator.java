////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.z;

/**
 * An iterator over a sequence of integers with regular steps, e.g. 2, 4, 6, 8...
 */

public class IntStepIterator implements IntIterator {

    private int current;
    private int step;
    private int limit;

    /**
     * Create an iterator over a sequence with regular steps
     * @param start the first value to be returned
     * @param step the difference between successive values (must be non-zero)
     * @param limit if step&gt;0, the iteration will not deliver any values greater than this limit;
     * if step&lt;0 the iteration will not deliver any values lower than this limit
     */

    public IntStepIterator(int start, int step, int limit) {
        this.current = start;
        this.step = step;
        this.limit = limit;
    }

    @Override
    public boolean hasNext() {
        return step>0 ? current <= limit : current >= limit;
    }

    @Override
    public int next() {
        int n = current;
        current += step;
        return n;
    }

}
