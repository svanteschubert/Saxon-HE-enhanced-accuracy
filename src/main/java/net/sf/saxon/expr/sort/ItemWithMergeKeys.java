////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;

import java.util.ArrayList;
import java.util.List;

/**
 * A class representing an item together with its merge keys and the name of the merge source object
 * from which it derived, suitable for encapsulation as an ObjectValue.
 * The input sequences are mapped to sequences of these items, and the merge keys are then stripped off
 * before applying the merge action.
 */

public class ItemWithMergeKeys {
    Item baseItem;
    List<AtomicValue> sortKeyValues;
    String sourceName;

    /**
     * Create an item, calculate with its merge keys, and construct the composite item in which
     * the merge key values are saved
     *
     * @param bItem   the item to be encapsulated
     * @param sKeys   the merge key definitions
     * @param name    the merge source name
     * @param context the dynamic context, used for evaluating the merge keys for the item
     * @throws net.sf.saxon.trans.XPathException if evaluation of a sort key fails
     */

    ItemWithMergeKeys(Item bItem, SortKeyDefinitionList sKeys, String name, XPathContext context) throws XPathException {
        baseItem = bItem;
        sourceName = name;
        sortKeyValues = new ArrayList<AtomicValue>(sKeys.size());

        for (SortKeyDefinition sKey : sKeys) {
            sortKeyValues.add((AtomicValue) sKey.getSortKey().evaluateItem(context));
        }

    }
}


