////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.wrapper;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.NamespaceNode;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.UntypedAtomicValue;

import java.util.function.Predicate;

/**
 * A node in the XML parse tree representing an XML element, character content, or attribute.
 * <p>This implementation of the NodeInfo interface contains common code used by many "wrapper" implementations
 * for external data models.</p>
 *
 * @author Michael H. Kay
 */

public abstract class AbstractNodeWrapper implements NodeInfo, VirtualNode {

    protected TreeInfo treeInfo;

    @Override
    public TreeInfo getTreeInfo() {
        return treeInfo;
    }

    /**
     * Get the node underlying this virtual node. If this is a VirtualNode the method
     * will automatically drill down through several layers of wrapping.
     *
     * @return The underlying node.
     */

    @Override
    public final Object getRealNode() {
        return getUnderlyingNode();
    }

    /**
     * Get the name pool for this node
     *
     * @return the NamePool
     */

    public NamePool getNamePool() {
        return getConfiguration().getNamePool();
    }

    /**
     * Get the typed value.
     *
     * @return the typed value. This will either be a single AtomicValue or a value whose items are
     *         atomic values.
     * @since 8.5 - signature changed in 9.5
     */

    @Override
    public AtomicSequence atomize() throws XPathException {
        switch (getNodeKind()) {
            case Type.COMMENT:
            case Type.PROCESSING_INSTRUCTION:
                return new StringValue(getStringValueCS());
            default:
                return new UntypedAtomicValue(getStringValueCS());
        }
    }

    /**
     * The equals() method compares nodes for identity. It is defined to give the same result
     * as isSameNodeInfo().
     *
     * @param other the node to be compared with this node
     * @return true if this NodeInfo object and the supplied NodeInfo object represent
     *         the same node in the tree.
     * @since 8.7 Previously, the effect of the equals() method was not defined. Callers
     *        should therefore be aware that third party implementations of the NodeInfo interface may
     *        not implement the correct semantics. It is safer to use isSameNodeInfo() for this reason.
     *        The equals() method has been defined because it is useful in contexts such as a Java Set or HashMap.
     */

    public boolean equals(Object other) {
        if (!(other instanceof AbstractNodeWrapper)) {
            return false;
        }
        AbstractNodeWrapper ow = (AbstractNodeWrapper) other;
        return getUnderlyingNode().equals(ow.getUnderlyingNode());
    }

    /**
     * The hashCode() method obeys the contract for hashCode(): that is, if two objects are equal
     * (represent the same node) then they must have the same hashCode()
     *
     * @since 8.7 Previously, the effect of the equals() and hashCode() methods was not defined. Callers
     *        should therefore be aware that third party implementations of the NodeInfo interface may
     *        not implement the correct semantics.
     */

    public int hashCode() {
        return getUnderlyingNode().hashCode();
    }

    /**
     * Get the System ID for the node.
     *
     * @return the System Identifier of the entity in the source document containing the node,
     *         or null if not known. Note this is not the same as the base URI: the base URI can be
     *         modified by xml:base, but the system ID cannot.
     */

    @Override
    public String getSystemId() {
        if (treeInfo instanceof GenericTreeInfo) {
            return ((GenericTreeInfo) treeInfo).getSystemId();
        } else {
            throw new UnsupportedOperationException();
            // must implement in subclass
        }
    }

    /**
     * Set the system ID. Required because NodeInfo implements the JAXP Source interface
     * @param uri the system ID.
     */

    @Override
    public void setSystemId(String uri) {
        if (treeInfo instanceof GenericTreeInfo) {
            ((GenericTreeInfo) treeInfo).setSystemId(uri);
        } else {
            throw new UnsupportedOperationException();
            // must implement in subclass
        }
    }

    /**
     * Get the Base URI for the node, that is, the URI used for resolving a relative URI contained
     * in the node.
     *
     * @return the base URI of the node, taking into account xml:base attributes if present
     */

    @Override
    public String getBaseURI() {
        if (getNodeKind() == Type.NAMESPACE) {
            return null;
        }
        NodeInfo n = this;
        if (getNodeKind() != Type.ELEMENT) {
            n = getParent();
        }
        // Look for an xml:base attribute
        while (n != null) {
            String xmlbase = n.getAttributeValue(NamespaceConstant.XML, "base");
            if (xmlbase != null) {
                return xmlbase;
            }
            n = n.getParent();
        }
        // if not found, return the base URI of the document node
        return getRoot().getSystemId();
    }

    /**
     * Get line number
     *
     * @return the line number of the node in its original source document; or -1 if not available.
     *         Always returns -1 in this implementation.
     */

    @Override
    public int getLineNumber() {
        return -1;
    }

    /**
     * Get column number
     *
     * @return the column number of the node in its original source document; or -1 if not available
     */

    @Override
    public int getColumnNumber() {
        return -1;
    }

    /**
     * Get an immutable copy of this Location object. By default Location objects may be mutable, so they
     * should not be saved for later use. The result of this operation holds the same location information,
     * but in an immutable form.
     */
    @Override
    public Location saveLocation() {
        return this;
    }

    /**
     * Return the string value of the node. The interpretation of this depends on the type
     * of node. For an element it is the accumulated character content of the element,
     * including descendant elements.
     *
     * @return the string value of the node
     */

    @Override
    public String getStringValue() {
        return getStringValueCS().toString();
    }

    /**
     * Get the display name of this node. For elements and attributes this is
     * [prefix:]localname. For unnamed nodes, it is an empty string.
     *
     * @return The display name of this node. For a node with no name, return an
     *         empty string.
     */

    @Override
    public String getDisplayName() {
        String prefix = getPrefix();
        String local = getLocalPart();
        if (prefix.isEmpty()) {
            return local;
        } else {
            return prefix + ":" + local;
        }
    }

    /**
     * Get the string value of a given attribute of this node.
     * <p>The default implementation is suitable for nodes other than elements.</p>
     *
     * @param uri   the namespace URI of the attribute name. Supply the empty string for an attribute
     *              that is in no namespace
     * @param local the local part of the attribute name.
     * @return the attribute value if it exists, or null if it does not exist. Always returns null
     *         if this node is not an element.
     * @since 9.4
     */
    @Override
    public String getAttributeValue(String uri, String local) {
        return null;
    }


    /**
     * Return an iteration over the nodes reached by the given axis from this node
     *
     * @param axisNumber the axis to be used
     * @return a SequenceIterator that scans the nodes reached by the axis in turn.
     */

    @Override
    public AxisIterator iterateAxis(int axisNumber) {
        return iterateAxis(axisNumber, AnyNodeTest.getInstance());
    }

    /**
     * Return an iteration over the nodes reached by the given axis from this node.
     * <p>This superclass provides implementations of the ancestor, ancestor-or-self,
     * following, namespace, parent, preceding, self, and preceding-or-ancestor axes.
     * The other axes are implemented by calling methods iterateAttributes(),
     * iterateChildren(), iterateDescendants(), and iterateSiblings(), which must
     * be provided in a subclass.</p>
     *
     * @param axisNumber the axis to be used
     * @param nodeTest   A pattern to be matched by the returned nodes
     * @return a SequenceIterator that scans the nodes reached by the axis in turn.
     */

    @Override
    public AxisIterator iterateAxis(int axisNumber, Predicate<? super NodeInfo> nodeTest) {
        int nodeKind = getNodeKind();
        switch (axisNumber) {
            case AxisInfo.ANCESTOR:
                if (nodeKind == Type.DOCUMENT) {
                    return EmptyIterator.ofNodes();
                }
                return new Navigator.AxisFilter(
                        new Navigator.AncestorEnumeration(this, false),
                        nodeTest);

            case AxisInfo.ANCESTOR_OR_SELF:
                if (nodeKind == Type.DOCUMENT) {
                    return Navigator.filteredSingleton(this, nodeTest);
                }
                return new Navigator.AxisFilter(
                        new Navigator.AncestorEnumeration(this, true),
                        nodeTest);

            case AxisInfo.ATTRIBUTE:
                if (nodeKind != Type.ELEMENT) {
                    return EmptyIterator.ofNodes();
                }
                return iterateAttributes(nodeTest);

            case AxisInfo.CHILD:
                if (nodeKind == Type.ELEMENT || nodeKind == Type.DOCUMENT) {
                    return iterateChildren(nodeTest);
                } else {
                    return EmptyIterator.ofNodes();
                }

            case AxisInfo.DESCENDANT:
                if (nodeKind == Type.ELEMENT || nodeKind == Type.DOCUMENT) {
                    return iterateDescendants(nodeTest, false);
                } else {
                    return EmptyIterator.ofNodes();
                }

            case AxisInfo.DESCENDANT_OR_SELF:
                if (nodeKind == Type.ELEMENT || nodeKind == Type.DOCUMENT) {
                    return iterateDescendants(nodeTest, true);
                } else {
                    return Navigator.filteredSingleton(this, nodeTest);
                }

            case AxisInfo.FOLLOWING:
                return new Navigator.AxisFilter(
                        new Navigator.FollowingEnumeration(this),
                        nodeTest);

            case AxisInfo.FOLLOWING_SIBLING:
                switch (nodeKind) {
                    case Type.DOCUMENT:
                    case Type.ATTRIBUTE:
                    case Type.NAMESPACE:
                        return EmptyIterator.ofNodes();
                    default:
                        return iterateSiblings(nodeTest, true);
                }

            case AxisInfo.NAMESPACE:
                if (nodeKind != Type.ELEMENT) {
                    return EmptyIterator.ofNodes();
                }
                return NamespaceNode.makeIterator(this, nodeTest);

            case AxisInfo.PARENT:
                return Navigator.filteredSingleton(getParent(), nodeTest);

            case AxisInfo.PRECEDING:
                return new Navigator.AxisFilter(
                        new Navigator.PrecedingEnumeration(this, false),
                        nodeTest);

            case AxisInfo.PRECEDING_SIBLING:
                switch (nodeKind) {
                    case Type.DOCUMENT:
                    case Type.ATTRIBUTE:
                    case Type.NAMESPACE:
                        return EmptyIterator.ofNodes();
                    default:
                        return iterateSiblings(nodeTest, false);
                }

            case AxisInfo.SELF:
                return Navigator.filteredSingleton(this, nodeTest);

            case AxisInfo.PRECEDING_OR_ANCESTOR:
                return new Navigator.AxisFilter(
                        new Navigator.PrecedingEnumeration(this, true),
                        nodeTest);

            default:
                throw new IllegalArgumentException("Unknown axis number " + axisNumber);
        }
    }

    /**
     * Return an iterator over the attributes of this element node.
     * This method is only called after checking that the node is an element.
     *
     * @param nodeTest a test that the returned attributes must satisfy
     * @return an iterator over the attribute nodes. The order of the result,
     *         although arbitrary, must be consistent with document order.
     */

    protected abstract AxisIterator iterateAttributes(Predicate<? super NodeInfo> nodeTest);

    /**
     * Return an iterator over the children of this node.
     * This method is only called after checking that the node is an element or document.
     *
     * @param nodeTest a test that the returned attributes must satisfy
     * @return an iterator over the child nodes, in document order.
     */

    protected abstract AxisIterator iterateChildren(Predicate<? super NodeInfo> nodeTest);

    /**
     * Return an iterator over the siblings of this node.
     * This method is only called after checking that the node is an element, text, comment, or PI node.
     *
     * @param nodeTest a test that the returned siblings must satisfy
     * @param forwards true for following siblings, false for preceding siblings
     * @return an iterator over the sibling nodes, in axis order.
     */

    protected abstract AxisIterator iterateSiblings(Predicate<? super NodeInfo> nodeTest, boolean forwards);

    /**
     * Return an iterator over the descendants of this node.
     * This method is only called after checking that the node is an element or document node.
     *
     * @param nodeTest    a test that the returned descendants must satisfy
     * @param includeSelf true if this node is to be included in the result
     * @return an iterator over the sibling nodes, in axis order.
     */

    protected AxisIterator iterateDescendants(Predicate<? super NodeInfo> nodeTest, boolean includeSelf) {
        AxisIterator iter = new Navigator.DescendantEnumeration(this, includeSelf, true);
        if (!(nodeTest instanceof AnyNodeTest)) {
            iter = new Navigator.AxisFilter(iter, nodeTest);
        }
        return iter;
    }



    /**
     * Get all namespace declarations and undeclarations defined on this element.
     * <p>This method is intended primarily for internal use. User applications needing
     * information about the namespace context of a node should use <code>iterateAxis(Axis.NAMESPACE)</code>.
     * (However, not all implementations support the namespace axis, whereas all implementations are
     * required to support this method.)</p>
     * <p>This implementation of the method is suitable for all nodes other than elements; it returns
     * an empty array.</p>
     *
     * @param buffer If this is non-null, and the result array fits in this buffer, then the result
     *               may overwrite the contents of this array, to avoid the cost of allocating a new array on the heap.
     * @return An array of namespace binding objects representing the namespace declarations and undeclarations present on
     *      this element. For a node other than an element, return null.
     *      If the uri part is "", then this is a namespace undeclaration rather than a declaration.
     *      The XML namespace is never included in the list. If the supplied array is larger than required,
     *      then the first unused entry will be set to null.
     *      <p>For a node other than an element, the method returns null.</p>
     */
    @Override
    public NamespaceBinding[] getDeclaredNamespaces(NamespaceBinding[] buffer) {
        return new NamespaceBinding[0];
    }

    /**
     * Get all the namespace bindings that are in-scope for this element.
     * <p>For an element return all the prefix-to-uri bindings that are in scope. This may include
     * a binding to the default namespace (represented by a prefix of ""). It will never include
     * "undeclarations" - that is, the namespace URI will never be empty; the effect of an undeclaration
     * is to remove a binding from the in-scope namespaces, not to add anything.</p>
     * <p>For a node other than an element, returns null.</p>
     *
     * @return the in-scope namespaces for an element, or null for any other kind of node.
     */
    @Override
    public NamespaceMap getAllNamespaces() {
        if (getNodeKind() == Type.ELEMENT) {
            throw new AssertionError("not implemented for " + getClass());
        }
        return null;
    }

    /**
     * Get the root node - always a document node with this tree implementation
     *
     * @return the NodeInfo representing the containing document
     */

    @Override
    public NodeInfo getRoot() {
        NodeInfo p = this;
        while (true) {
            NodeInfo q = p.getParent();
            if (q==null) {
                return p;
            }
            p = q;
        }
    }


    /**
     * Determine whether the node has any children.
     * This implementation calls iterateAxis, so the subclass implementation of iterateAxis
     * must avoid calling this method.
     */

    @Override
    public boolean hasChildNodes() {
        switch (getNodeKind()) {
            case Type.DOCUMENT:
            case Type.ELEMENT:
                return iterateAxis(AxisInfo.CHILD).next() != null;
            default:
                return false;
        }
    }

    /**
     * Get the fingerprint of the node
     *
     * @return the node's fingerprint, or -1 for an unnamed node
     * @throws UnsupportedOperationException if this method is called for a node where
     *                                       hasFingerprint() returns false;
     */
    @Override
    public int getFingerprint() {
        throw new UnsupportedOperationException();
    }

    /**
     * Test whether a fingerprint is available for the node name
     */
    @Override
    public boolean hasFingerprint() {
        return false;
    }
}

