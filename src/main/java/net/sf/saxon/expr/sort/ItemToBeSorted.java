////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.om.Item;

/**
 * This class is a specialization of class ObjectToBeSorted for use when
 * the sequence being sorted is a sequence of items (including tuples, which
 * are represented as items).
 */
public class ItemToBeSorted extends ObjectToBeSorted<Item> {

    public ItemToBeSorted(int numberOfSortKeys) {
        super(numberOfSortKeys);
    }
}

