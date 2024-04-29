////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.trans.XPathException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A value that is a sequence containing one or more items. The main use is in declarations of reflexive extension
 * functions, where declaring an argument of type &lt;OneOrMore&lt;IntegerValue&gt;&gt; triggers automatic type
 * checking in the same way as for a native XSLT/XQuery function declaring the type as xs:integer+.
 */

public class OneOrMore<T extends Item> extends ZeroOrMore<T> {

    /**
     * Create a sequence containing zero or one items
     *
     * @param content The content of the sequence
     */

    public OneOrMore(T[] content) {
        super(Arrays.asList(content));
        if (content.length == 0) {
            throw new IllegalArgumentException();
        }
    }

    public OneOrMore(List<T> content) {
        super(content);
        if (content.isEmpty()) {
            throw new IllegalArgumentException();
        }
    }

    public static OneOrMore<Item> makeOneOrMore(Sequence sequence) throws XPathException {
        List<Item> content = new ArrayList<>();
        //noinspection Convert2MethodRef
        SequenceTool.supply(sequence.iterate(), (ItemConsumer<? super Item>) it -> content.add(it));
        if (content.isEmpty()) {
            throw new IllegalArgumentException();
        }
        return new OneOrMore<>(content);
    }
}
