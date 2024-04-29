////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.str;

import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.z.IntIterator;

/**
 * Iterator over a string to produce a sequence of single character strings
 */
public class CodepointIterator implements AtomicIterator {

    final IntIterator codepoints;

    /**
     * Create a codepoint iterator
     * @param codepoints iterator over the underlying codepoints
     */
    public CodepointIterator(IntIterator codepoints) {
        this.codepoints = codepoints;
    }

    @Override
    public AtomicValue next() {
        return codepoints.hasNext() ? new Int64Value(codepoints.next()) : null;
    }

}

