////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;

/**
 * ItemMapper is an implementation of {@link ItemMappingFunction} that wraps
 * a function typically supplied as a lambda expression.
 *
 * <p>NOTE: Java allows a lambda expression to be used wherever an {@link ItemMappingFunction}
 * is needed, but C# does not (it's not possible in C# to have a class implementing
 * a delegate). So if a lambda expression is wanted, use an {@link ItemMapper}
 * as a wrapper.</p>
 */

public class ItemMapper implements ItemMappingFunction {

    @FunctionalInterface
    public interface Lambda {
        Item map(Item item) throws XPathException;
    }

    private final Lambda lambda;

    private ItemMapper(Lambda lambda) {
        this.lambda = lambda;
    }

    public static ItemMapper of(Lambda lambda) {
        return new ItemMapper(lambda);
    }

    /**
     * Map one item to another item.
     *
     * @param item The input item to be mapped.
     * @return either the output item, or null.
     * @throws XPathException if a dynamic error occurs
     */

    /*@Nullable*/
    public Item mapItem(Item item) throws XPathException {
        return lambda.map(item);
    }

}

