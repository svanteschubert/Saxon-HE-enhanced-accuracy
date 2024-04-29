////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.transpile.CSharpSuppressWarnings;

import java.util.Iterator;
import java.util.function.Function;

public class SequenceIteratorOverJavaIterator<J> implements SequenceIterator {

    // See also class IteratorWrapper, which does the same thing without mapping,
    // and WrappingJavaIterator, which does the converse.

    private final Iterator<J> javaIterator;
    private final Function<J, Item> mapper;

    public SequenceIteratorOverJavaIterator(Iterator<J> javaIterator, Function<J, Item> mapper) {
        this.javaIterator = javaIterator;
        this.mapper = mapper;
    }

    @Override
    @CSharpSuppressWarnings("UnsafeIteratorConversion")
    public Item next() {
        if (javaIterator.hasNext()) {
            return mapper.apply(javaIterator.next());
        } else {
            return null;
        }
    }
}


