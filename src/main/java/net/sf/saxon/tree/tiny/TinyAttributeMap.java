////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.om.*;

import java.util.Iterator;

/**
 * An implementation of the AttributeMap interface based directly on the
 * TinyTree data structure.
 */

public class TinyAttributeMap implements AttributeMap {

    private int element;
    private TinyTree tree;
    private int firstAttribute;

    public TinyAttributeMap(/*@NotNull*/ TinyTree tree, int element) {
        this.tree = tree;
        this.element = element;
        firstAttribute = tree.alpha[element];
    }

    /**
     * Return the number of attributes in the list.
     *
     * @return The number of attributes in the list.
     */

    @Override
    public int size() {
        int i = firstAttribute;
        while (i < tree.numberOfAttributes && tree.attParent[i] == element) {
            i++;
        }
        return i - firstAttribute;
    }

    @Override
    public AttributeInfo get(NodeName name) {
        return null;
    }

    @Override
    public AttributeInfo get(String uri, String local) {
        return null;
    }

    @Override
    public AttributeInfo getByFingerprint(int fingerprint, NamePool namePool) {
        return null;
    }

    @Override
    public Iterator<AttributeInfo> iterator() {
        return new AttributeInfoIterator(tree, element);
    }

    @Override
    public AttributeInfo itemAt(int index) {
        int attNr = firstAttribute + index;
        int nc = tree.attCode[attNr];
        CodedName nodeName = new CodedName(nc & NamePool.FP_MASK, tree.prefixPool.getPrefix(nc >> 20), tree.getNamePool());
        return new AttributeInfo(nodeName,
                                 tree.getAttributeType(attNr),
                                 tree.attValue[attNr].toString(),
                                 Loc.NONE,
                                 ReceiverOption.NONE);
    }

//    /**
//     * Determine whether a given attribute has the is-ID property set
//     */
//
//    public boolean isId(int index) {
//        return (getTypeAnnotation(index).getFingerprint() == StandardNames.XS_ID) ||
//                ((tree.attCode[firstAttribute + index] & NamePool.FP_MASK) == StandardNames.XML_ID);
//    }
//
//    /**
//     * Determine whether a given attribute has the is-idref property set
//     */
//
//    public boolean isIdref(int index) {
//        return (getTypeAnnotation(index).getFingerprint() == StandardNames.XS_IDREF) ||
//                (getTypeAnnotation(index).getFingerprint() == StandardNames.XS_IDREFS);
//    }
}

