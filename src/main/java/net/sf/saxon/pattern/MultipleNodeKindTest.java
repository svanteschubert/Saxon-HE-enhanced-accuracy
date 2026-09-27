////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.GNode;
import net.sf.saxon.om.Item;
import net.sf.saxon.pattern.nodetest.NodeTest;
import net.sf.saxon.pattern.qname.AnyQNameTest;
import net.sf.saxon.pattern.qname.QNameTest;
import net.sf.saxon.transpile.CSharpInjectMembers;
import net.sf.saxon.transpile.CSharpUsing;
import net.sf.saxon.type.*;

import java.util.LinkedList;
import java.util.Optional;
import java.util.function.Function;

/**
 * An MultipleNodeKindTest is a nodetest that matches nodes belonging to any subset of possible
 * node kinds, for example element and document nodes, or attribute and namespace nodes
 */

@CSharpUsing(code="Saxon.Hej.type")
@CSharpInjectMembers
public final class MultipleNodeKindTest implements NodeTest {

    public final static MultipleNodeKindTest PARENT_NODE =
            new MultipleNodeKindTest(UType.DOCUMENT.union(UType.ELEMENT).union(UType.JNODE));

    public final static MultipleNodeKindTest PARENT_XNODE =
            new MultipleNodeKindTest(UType.DOCUMENT.union(UType.ELEMENT));

    public final static MultipleNodeKindTest DOC_ELEM_ATTR =
        new MultipleNodeKindTest(UType.DOCUMENT.union(UType.ELEMENT).union(UType.ATTRIBUTE));

    public final static MultipleNodeKindTest LEAF =
            new MultipleNodeKindTest(UType.TEXT.union(UType.COMMENT).union(UType.PI).union(UType.NAMESPACE).union(UType.ATTRIBUTE));

    public final static MultipleNodeKindTest CHILD_NODE =
            new MultipleNodeKindTest(UType.ELEMENT.union(UType.TEXT).union(UType.COMMENT).union(UType.PI));

    UType uType;
    int nodeKindMask;

    public MultipleNodeKindTest(UType u) {
        this.uType = u;
        if (UType.DOCUMENT.overlaps(u)) {
            nodeKindMask |= 1 << Type.DOCUMENT;
        }
        if (UType.ELEMENT.overlaps(u)) {
            nodeKindMask |= 1 << Type.ELEMENT;
        }
        if (UType.ATTRIBUTE.overlaps(u)) {
            nodeKindMask |= 1 << Type.ATTRIBUTE;
        }
        if (UType.TEXT.overlaps(u)) {
            nodeKindMask |= 1 << Type.TEXT;
        }
        if (UType.COMMENT.overlaps(u)) {
            nodeKindMask |= 1 << Type.COMMENT;
        }
        if (UType.PI.overlaps(u)) {
            nodeKindMask |= 1 << Type.PROCESSING_INSTRUCTION;
        }
        if (UType.NAMESPACE.overlaps(u)) {
            nodeKindMask |= 1 << Type.NAMESPACE;
        }
        if (UType.JNODE.overlaps(u)) {
            nodeKindMask |= 1 << Type.JNODE;
        }
    }

    /**
     * Get an item type that all matching nodes must satisfy
     *
     * @return an item type
     */
    @Override
    public ItemType getItemType() {
        return ChoiceItemType.of(uType);
    }

    @Override
    public NodeTest asXNodeTest(Configuration config) {
        return this;
    }

    /**
     * Get the corresponding {@link net.sf.saxon.type.UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        return uType;
    }

    @Override
    public QNameTest getQNameTest() {
        return AnyQNameTest.getInstance();
    }

    /**
     * Determine whether the content type (if present) is nillable
     *
     * @return true if the content test (when present) can match nodes that are nilled
     */
    @Override
    public boolean isNillable() {
        return false;
    }

    /**
     * Get extra diagnostic information about why a supplied item does not conform to this
     * item type, if available. If extra information is returned, it should be in the form of a complete
     * sentence, minus the closing full stop. No information should be returned for obvious cases.
     *
     * @param item the item that doesn't match this type
     * @param th   the type hierarchy cache
     * @return optionally, a message explaining why the item does not match the type
     */
    @Override
    public Optional<String> explainMismatch(Item item, TypeHierarchy th) {
        return Optional.empty();
    }

    /**
     * Test whether this node test is satisfied by a given node. This alternative
     * method is used in the case of nodes where calculating the fingerprint is expensive,
     * for example DOM or JDOM nodes.
     *
     * @param node the node to be matched
     */

    @Override
    public boolean test(GNode node) {
        int nodeKind = node.getNodeKind();
        return (nodeKindMask & (1<<nodeKind)) != 0;
    }


    /**
     * Determine the default priority to use if this pattern appears as a match pattern
     * for a template with no explicit priority attribute.
     */

    @Override
    public double getDefaultPriority() {
        return -0.5;
    }

    /*@NotNull*/
    public String toString() {
        StringBuilder fsb = new StringBuilder(64);
        LinkedList<PrimitiveUType> types = new LinkedList<>(uType.decompose());
        format(types, fsb, ItemType::toString);
        return fsb.toString();
    }

    /*@NotNull*/
    public String toShortString() {
        if (nodeKindMask == CHILD_NODE.nodeKindMask) {
            return "node()";
        }
        StringBuilder fsb = new StringBuilder(64);
        LinkedList<PrimitiveUType> types = new LinkedList<>(uType.decompose());
        format(types, fsb, Object::toString);
        return fsb.toString();
    }



    private void format(LinkedList<PrimitiveUType> list, StringBuilder fsb, Function<ItemType, String> show) {
        if (list.size() == 1) {
            fsb.append(show.apply(UType.toItemType(list.getFirst())));
        } else {
            boolean first = true;
            for (PrimitiveUType pu : list) {
                fsb.append(first ? '(' : '|');
                first = false;
                fsb.append(show.apply(UType.toItemType(pu)));
            }
            fsb.append(')');
        }
    }

    /**
     * Returns a hash code value for the object.
     */

    public int hashCode() {
        return uType.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof MultipleNodeKindTest && uType.equals(((MultipleNodeKindTest)obj).uType);
    }


}

