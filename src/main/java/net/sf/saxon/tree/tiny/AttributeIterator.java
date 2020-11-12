////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;

import java.util.EnumSet;

/**
 * AttributeIterator is an iterator over all the attribute nodes of an Element in the TinyTree.
 */

final class AttributeIterator implements AxisIterator, AtomizedValueIterator {

    private final TinyTree tree;
    private final int element;
    private final NodeTest nodeTest;
    private int index;
    private int currentNodeNr;

    /**
     * Constructor. Note: this constructor will only be called if the relevant node
     * is an element and if it has one or more attributes. Otherwise an EmptyEnumeration
     * will be constructed instead.
     *
     * @param tree:     the containing TinyTree
     * @param element:  the node number of the element whose attributes are required
     * @param nodeTest: condition to be applied to the names of the attributes selected
     */

    AttributeIterator(/*@NotNull*/ TinyTree tree, int element, NodeTest nodeTest) {

        this.nodeTest = nodeTest;
        this.tree = tree;
        this.element = element;
        index = tree.alpha[element];
        currentNodeNr = -1;
    }

    /**
     * Move to the next node in the iteration. On completion, currentNodeNr points
     * to the next attribute.
     * @return true if there are more items in the sequence
     */

    private boolean moveToNext() {
        while (true) {
            if (index >= tree.numberOfAttributes || tree.attParent[index] != element) {
                index = Integer.MAX_VALUE;
                currentNodeNr = -1;
                return false;
            }
            SchemaType typeCode = tree.getAttributeType(index);
            if (nodeTest.matches(Type.ATTRIBUTE, new CodedName(tree.attCode[index] & NamePool.FP_MASK, "", tree.getNamePool()), typeCode)) {
                currentNodeNr = index++;
                if (nodeTest instanceof NameTest) {
                    // there can only be one match, so abandon the search after this node
                    index = Integer.MAX_VALUE;
                }
                return true;
            }
            index++;
        }
    }

    /**
     * Get the next item in the sequence. <BR>
     *
     * @return the next Item. If there are no more nodes, return null.
     */

    /*@Nullable*/
    @Override
    public NodeInfo next() {
        if (moveToNext()) {
            return tree.getAttributeNode(currentNodeNr);
        } else {
            return null;
        }
    }

    /**
     * Deliver the atomic value that is next in the atomized result
     *
     * @return the next atomic value
     * @throws net.sf.saxon.trans.XPathException
     *          if a failure occurs reading or atomizing the next value
     */
    @Override
    public AtomicSequence nextAtomizedValue() throws XPathException {
        if (moveToNext()) {
            return tree.getTypedValueOfAttribute(null, currentNodeNr);
        } else {
            return null;
        }
    }

    /**
     * Get properties of this iterator, as a bit-significant integer.
     *
     * @return the properties of this iterator. This will be some combination of
     *         properties such as {@link net.sf.saxon.om.SequenceIterator.Property#GROUNDED}, {@link net.sf.saxon.om.SequenceIterator.Property#LAST_POSITION_FINDER},
     *         and {@link net.sf.saxon.om.SequenceIterator.Property#LOOKAHEAD}. It is always
     *         acceptable to return the value zero, indicating that there are no known special properties.
     *         It is acceptable for the properties of the iterator to change depending on its state.
     */
    @Override
    public EnumSet<Property> getProperties() {
        return EnumSet.of(Property.ATOMIZING);
    }
}

