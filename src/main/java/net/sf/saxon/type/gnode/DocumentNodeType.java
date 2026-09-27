////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.gnode;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.nodetest.AnyGNode;
import net.sf.saxon.pattern.qname.NoQNameTest;
import net.sf.saxon.pattern.qname.QNameTest;
import net.sf.saxon.trans.Err;
import net.sf.saxon.type.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A DocumentNodeType implements the test document-node(element(~,~))
 */

public class DocumentNodeType extends XNodeType {


    private final XNodeType elementTest;

    public DocumentNodeType(XNodeType elementTest) {
        this.elementTest = elementTest;
    }

    /**
     * Get the corresponding {@link net.sf.saxon.type.UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        return UType.DOCUMENT;
    }


    /**
     * Get the set of allowed node names that this type if capable
     * of matching
     *
     * @return the allowed node names
     */
    @Override
    public QNameTest getAllowedNodeNames() {
        return NoQNameTest.getInstance();
    }

    /**
     * Get an alphabetic code representing the type, or at any rate, the nearest built-in type
     * from which this type is derived. The codes are designed so that for any two built-in types
     * A and B, alphaCode(A) is a prefix of alphaCode(B) if and only if A is a supertype of B.
     *
     * @return the alphacode for the nearest containing built-in type. For example: for xs:string
     * return "AS", for xs:boolean "AB", for node() "N", for element() "NE", for map(*) "FM", for
     * array(*) "FA".
     */
    @Override
    public String getBasicAlphaCode() {
        return "ND";
    }

    /**
     * Test whether a given item conforms to this type
     *
     * @param item The item to be tested
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item) {
        if (!(item instanceof NodeInfo) || ((NodeInfo)item).getNodeKind() != Type.DOCUMENT) {
            return false;
        }
        NodeInfo node = (NodeInfo)item;
        SequenceIterator iter = node.iterateChildAxis(AnyGNode.TEST);
        // The match is true if there is exactly one element node child, no text node
        // children, and the element node matches the element test.
        boolean found = false;
        NodeInfo n;
        while ((n = (NodeInfo)iter.next()) != null) {
            int kind = n.getNodeKind();
            if (kind == Type.TEXT) {
                return false;
            } else if (kind == Type.ELEMENT) {
                if (found) {
                    return false;
                }
                if (elementTest.matches(n)) {
                    found = true;
                } else {
                    return false;
                }
            }
        }
        return found;
    }

    /**
     * Determine the default priority of this node test when used on its own as a Pattern
     */

    @Override
    public final double getDefaultPriority() {
        return elementTest.getDefaultPriority();
    }

    /**
     * Determine the types of nodes to which this pattern applies. Used for optimisation.
     *
     * @return the type of node matched by this pattern. e.g. Type.ELEMENT or Type.TEXT
     */

    @Override
    public int getPrimitiveType() {
        return Type.DOCUMENT;
    }

    /**
     * Get the element test contained within this document test
     *
     * @return the contained element test
     */

    public XNodeType getElementTest() {
        return elementTest;
    }

    public String toString() {
        return "document-node(" + elementTest + ')';
    }

    /**
     * Returns a hash code value for the object.
     */

    public int hashCode() {
        return elementTest.hashCode() ^ 12345;
    }

    public boolean equals(/*@NotNull*/ Object other) {
        return other instanceof DocumentNodeType &&
                ((DocumentNodeType) other).elementTest.equals(elementTest);
    }

    @Override
    public ItemType normalizeItemType() {
        ItemType t2 = elementTest.normalizeItemType();
        if (t2 instanceof XNodeType x2) {
            return new DocumentNodeType(x2);
        }
        if (t2 instanceof ChoiceItemType c2) {
            List<ItemType> members = new ArrayList<>();
            for (ItemType it : c2.getMemberTypes()) {
                members.add(new DocumentNodeType((XNodeType)it));
            }
            return new ChoiceItemType(members);
        }
        return this;
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
        Optional<String> explanation = super.explainMismatch(item, th);
        if (explanation.isPresent()) {
            return explanation;
        }
        NodeInfo node = (NodeInfo) item;
        SequenceIterator iter = node.iterateChildAxis(AnyGNode.TEST);
        // The match is true if there is exactly one element node child, no text node
        // children, and the element node matches the element test.
        boolean found = false;
        NodeInfo n;
        while ((n = (NodeInfo)iter.next()) != null) {
            int kind = n.getNodeKind();
            if (kind == Type.TEXT) {
                return Optional.of("The supplied document node has text node children");
            } else if (kind == Type.ELEMENT) {
                if (found) {
                    return Optional.of("The supplied document node has more than one element child");
                }
                if (elementTest.matches(n)) {
                    found = true;
                } else {
                    String s = "The supplied document node has an element child (" + Err.depict(n) +
                            ") that does not satisfy the element test";
                    Optional<String> more = elementTest.explainMismatch(n, th);
                    if (more.isPresent()) {
                        s += ". " + more.get();
                    }
                    return Optional.of(s);
                }
            }
        }
        return Optional.empty();
    }

}

