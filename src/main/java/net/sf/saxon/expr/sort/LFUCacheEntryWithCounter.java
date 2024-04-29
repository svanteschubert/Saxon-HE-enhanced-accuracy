////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

/**
 * An entry in a least-recently-used cache
 * @param <W> the type of object held in the cache
 * @implNote Was an inner class of LFUCache; separated for convenience of C# transpilation
 */

class LFUCacheEntryWithCounter<W> {

    public W value;
    int counter;

    public LFUCacheEntryWithCounter(W value) {
        this.value = value;
        this.counter = 0;
    }
}

