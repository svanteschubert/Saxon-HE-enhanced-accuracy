////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpDelegate;

/**
 * ItemMappingFunction is an interface that must be satisfied by an object passed to a
 * ItemMappingIterator. It represents an object which, given an Item, can return either
 * another Item, or null.
 *
 * <p>NOTE: Java allows a lambda expression to be used where an ItemMappingFunction
 * is needed, but C# does not (it's not possible in C# to have a class implementing
 * a delegate). So if a delegate is wanted, use {@link ItemMapper}, to ensure that
 * it works in both languages.</p>
 */

@FunctionalInterface
@CSharpDelegate(false)
public interface ItemMappingFunction {

    /**
     * Map one item to another item.
     *
     * @param item The input item to be mapped.
     * @return either the output item, or null.
     * @throws XPathException if a dynamic error occurs
     */

    /*@Nullable*/
    Item mapItem(Item item) throws XPathException;

}

