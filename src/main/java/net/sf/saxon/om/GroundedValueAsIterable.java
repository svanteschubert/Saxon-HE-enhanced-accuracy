////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.tree.jiter.WrappingJavaIterator;

import java.util.Iterator;

/**
 * A class that wraps a {@link GroundedValue} as a Java {@link Iterable}
 */

public class GroundedValueAsIterable implements Iterable<Item> {

    private final GroundedValue value;
    public GroundedValueAsIterable(GroundedValue value) {
        this.value = value;
    }

    public Iterator<Item> iterator() {
        return new WrappingJavaIterator<>(value.iterate());
    }

}

