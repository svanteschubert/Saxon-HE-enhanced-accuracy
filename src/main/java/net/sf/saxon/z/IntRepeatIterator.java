////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.z;

/**
 * An iterator over a single integer repeated a fixed number of times
 */

public class IntRepeatIterator implements IntIterator {

    private final int value;
    private int count;

    public IntRepeatIterator(int value, int count) {
        this.value = value;
        this.count = count;
    }

    @Override
    public boolean hasNext() {
        return count > 0;
    }

    @Override
    public int next() {
        count--;
        return value;
    }

}
