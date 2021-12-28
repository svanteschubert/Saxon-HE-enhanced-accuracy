////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.CodedName;
import net.sf.saxon.om.NamePool;

import java.util.Iterator;

/**
 * AttributeIterator is an iterator over all the attribute nodes of an Element in the TinyTree.
 */

final class AttributeInfoIterator implements Iterator<AttributeInfo> {

    private TinyTree tree;
    private int element;
    private int index;

    /**
     * Constructor. Note: this constructor will only be called if the relevant node
     * is an element and if it has one or more attributes. Otherwise an EmptyEnumeration
     * will be constructed instead.
     *
     * @param tree:     the containing TinyTree
     * @param element:  the node number of the element whose attributes are required
     */

    AttributeInfoIterator(TinyTree tree, int element) {

        this.tree = tree;
        this.element = element;
        index = tree.alpha[element];
    }

    @Override
    public boolean hasNext() {
        return index < tree.numberOfAttributes && tree.attParent[index] == element;
    }

    @Override
    public AttributeInfo next() {
        int nc = tree.attCode[index];
        CodedName nodeName = new CodedName(nc & NamePool.FP_MASK, tree.prefixPool.getPrefix(nc >> 20), tree.getNamePool());
        AttributeInfo info = new AttributeInfo(nodeName,
                                               tree.getAttributeType(index),
                                               tree.attValue[index].toString(),
                                               Loc.NONE,
                                               ReceiverOption.NONE);
        index++;
        return info;
    }


}

