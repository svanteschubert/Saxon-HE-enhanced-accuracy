////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.axiom;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.PrependAxisIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.wrapper.SiblingCountingNode;
import net.sf.saxon.tree.wrapper.VirtualNode;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.UntypedAtomicValue;
import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMContainer;
import org.apache.axiom.om.OMElement;

import java.util.Iterator;
import java.util.function.Predicate;

/**
 * A node in the XML parse tree representing an XML element, character content,
 * or attribute.
 * <p>This is the implementation of the NodeInfo interface used as a wrapper for
 * Axiom nodes.</p>
 * <p>Note that in Axiom, an OMAttribute is not an OMNode. Therefore, we need a separate
 * class AttributeWrapper for attribute nodes.</p>
 *
 * @author Michael H. Kay
 */

public class AxiomAttributeWrapper implements NodeInfo, VirtualNode, SiblingCountingNode {

    protected OMAttribute node;

    private AxiomParentNodeWrapper parent; // null means unknown

    protected int index; // -1 means unknown

    /**
     * This constructor is protected: nodes should be created using the wrap
     * factory method on the DocumentWrapper class
     *
     * @param node   The Axiom attribute node to be wrapped
     * @param parent The NodeWrapper that wraps the parent of this attribute node
     * @param index  Position of this node among its siblings
     */
    protected AxiomAttributeWrapper(OMAttribute node, AxiomParentNodeWrapper parent, int index) {
        this.node = node;
        this.parent = parent;
        this.index = index;
    }

    /**
     * Get information about the tree to which this NodeInfo belongs
     *
     * @return the TreeInfo
     * @since 9.7
     */
    @Override
    public TreeInfo getTreeInfo() {
        return parent.getTreeInfo();
    }

    /**
     * Get the configuration
     */

    @Override
    public Configuration getConfiguration() {
        return parent.getConfiguration();
    }

    /**
     * Get the underlying Axiom node, to implement the VirtualNode interface
     */

    @Override
    public OMAttribute getUnderlyingNode() {
        return node;
    }

    /**
     * Get the node underlying this virtual node. If this is a VirtualNode the method
     * will automatically drill down through several layers of wrapping.
     *
     * @return The underlying node.
     */

    @Override
    public OMAttribute getRealNode() {
        return getUnderlyingNode();
    }

    /**
     * Get fingerprint. The fingerprint is a coded form of the expanded name
     * of the node: two nodes
     * with the same name code have the same namespace URI and the same local name.
     * The fingerprint contains no information about the namespace prefix. For a name
     * in the null namespace, the fingerprint is the same as the name code.
     *
     * @return an integer fingerprint; two nodes with the same fingerprint have
     * the same expanded QName. For unnamed nodes (text nodes, comments, document nodes,
     * and namespace nodes for the default namespace), returns -1.
     * @throws UnsupportedOperationException if this kind of node does not hold
     *                                       namepool fingerprints (specifically, if {@link #hasFingerprint()} returns false).
     * @since 8.4 (moved into FingerprintedNode at some stage; then back into NodeInfo at 9.8).
     */
    @Override
    public int getFingerprint() {
        throw new UnsupportedOperationException();
    }

    /**
     * Ask whether this NodeInfo implementation holds a fingerprint identifying the name of the
     * node in the NamePool. If the answer is true, then the {@link #getFingerprint} method must
     * return the fingerprint of the node. If the answer is false, then the {@link #getFingerprint}
     * method should throw an {@code UnsupportedOperationException}. In the case of unnamed nodes
     * such as text nodes, the result can be either true (in which case getFingerprint() should
     * return -1) or false (in which case getFingerprint may throw an exception).
     *
     * @return true if the implementation of this node provides fingerprints.
     * @since 9.8; previously Saxon relied on using <code>FingerprintedNode</code> as a marker interface.
     */
    @Override
    public boolean hasFingerprint() {
        return false;
    }

    /**
     * Return the type of node.
     *
     * @return one of the values Node.ELEMENT, Node.TEXT, Node.ATTRIBUTE, etc.
     */

    @Override
    public int getNodeKind() {
        return Type.ATTRIBUTE;
    }

    /**
     * Get the typed value.
     *
     * @return the typed value. If requireSingleton is set to true, the result
     *         will always be an AtomicValue. In other cases it may be a Value
     *         representing a sequence whose items are atomic values.
     * @since 8.5 (signature changed in 9.5)
     */

    @Override
    public AtomicSequence atomize() {
        return new UntypedAtomicValue(getStringValueCS());
    }

    /**
     * Get the type annotation of this node, if any. The type annotation is represented as
     * SchemaType object.
     * <p>Types derived from a DTD are not reflected in the result of this method.</p>
     *
     * @return For element and attribute nodes: the type annotation derived from schema
     *         validation (defaulting to xs:untyped and xs:untypedAtomic in the absence of schema
     *         validation). For comments, text nodes, processing instructions, and namespaces: null.
     *         For document nodes, either xs:untyped if the document has not been validated, or
     *         xs:anyType if it has.
     * @since 9.4
     */

    @Override
    public SchemaType getSchemaType() {
        return BuiltInAtomicType.UNTYPED_ATOMIC;
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
        // In XOM equality means identity
        return other instanceof AxiomAttributeWrapper && node == ((AxiomAttributeWrapper) other).node;
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
        return node.hashCode();
    }

    /**
     * Get the System ID for the node.
     *
     * @return the System Identifier of the entity in the source document
     *         containing the node, or null if not known. Note this is not the
     *         same as the base URI: the base URI can be modified by xml:base,
     *         but the system ID cannot.
     */

    @Override
    public String getSystemId() {
        return parent.getBaseURI();
    }

    @Override
    public void setSystemId(String uri) {
        //
    }

    /**
     * Get the Base URI for the node, that is, the URI used for resolving a
     * relative URI contained in the node.
     */

    @Override
    public String getBaseURI() {
        return getParent().getBaseURI();
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
     * Determine the relative position of this node and another node, in
     * document order. The other node will always be in the same document.
     *
     * @param other The other node, whose position is to be compared with this
     *              node
     * @return -1 if this node precedes the other node, +1 if it follows the
     *         other node, or 0 if they are the same node. (In this case,
     *         isSameNode() will always return true, and the two nodes will
     *         produce the same result for generateId())
     */

    @Override
    public int compareOrder(NodeInfo other) {
        if (other instanceof AxiomAttributeWrapper) {
            AxiomAttributeWrapper otherAtt = (AxiomAttributeWrapper) other;
            if (this.node == otherAtt.node) {
                return 0;
            } else if (this.parent.equals(otherAtt.parent)) {
                return this.getSiblingPosition() > otherAtt.getSiblingPosition() ? +1 : -1;
            } else {
                return this.parent.compareOrder(otherAtt.parent);
            }
        } else if (other.getNodeKind() == Type.NAMESPACE && parent.equals(other.getParent())) {
            return +1;
        } else if (other.equals(parent)) {
            return +1;
        } else {
            return parent.compareOrder(other);
        }
    }


    /**
     * Return the string value of the node. The interpretation of this depends
     * on the type of node. For an element it is the accumulated character
     * content of the element, including descendant elements.
     *
     * @return the string value of the node
     */

    @Override
    public String getStringValue() {
        return node.getAttributeValue();
    }

    /**
     * Get the value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String.
     */

    @Override
    public CharSequence getStringValueCS() {
        return node.getAttributeValue();
    }

    /**
     * Get the local part of the name of this node. This is the name after the
     * ":" if any.
     *
     * @return the local part of the name. For an unnamed node, returns "".
     */

    @Override
    public String getLocalPart() {
        return node.getLocalName();
    }

    /**
     * Get the prefix of the name of the node. This is defined only for elements and attributes.
     * If the node has no prefix, or for other kinds of node, return a zero-length string.
     *
     * @return The prefix of the name of the node.
     */

    @Override
    public String getPrefix() {
        String prefix = node.getPrefix();
        return prefix == null ? "" : prefix;
    }

    /**
     * Get the URI part of the name of this node. This is the URI corresponding
     * to the prefix, or the URI of the default namespace if appropriate.
     *
     * @return The URI of the namespace of this node. For an unnamed node, or
     *         for a node with an empty prefix, return an empty string.
     */

    @Override
    public String getURI() {
        String uri = node.getNamespaceURI();
        return uri == null ? "" : uri;
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
     * Get the NodeInfo object representing the parent of this node
     */

    @Override
    public NodeInfo getParent() {
        return parent;
    }

    /**
     * Get the index position of this node among its siblings (starting from 0)
     */

    @Override
    public int getSiblingPosition() {
        if (index == -1) {
            OMElement elem = node.getOwner();
            int ix = 0;
            for (Iterator iter = elem.getAllAttributes(); iter.hasNext(); ) {
                if (iter.next() == node) {
                    return index = ix;
                }
                ix++;
            }
            throw new IllegalStateException("parent/attribute mismatch");
        }
        return index;
    }

    /**
     * Return an iteration over the nodes reached by the given axis from this
     * node
     *
     * @param axisNumber the axis to be used
     * @param nodeTest   A pattern to be matched by the returned nodes
     * @return a SequenceIterator that scans the nodes reached by the axis in
     *         turn.
     */

    @Override
    public AxisIterator iterateAxis(int axisNumber, Predicate<? super NodeInfo> nodeTest) {
        // for clarifications, see the W3C specs or:
        // http://msdn.microsoft.com/library/default.asp?url=/library/en-us/xmlsdk/html/xmrefaxes.asp
        switch (axisNumber) {
            case AxisInfo.ANCESTOR:
                return parent.iterateAxis(AxisInfo.ANCESTOR_OR_SELF, nodeTest);

            case AxisInfo.ANCESTOR_OR_SELF:
                if (nodeTest.test(this)) {
                    return new PrependAxisIterator(this, parent.iterateAxis(AxisInfo.ANCESTOR_OR_SELF, nodeTest));
                } else {
                    return parent.iterateAxis(AxisInfo.ANCESTOR_OR_SELF, nodeTest);
                }

            case AxisInfo.ATTRIBUTE:
            case AxisInfo.CHILD:
            case AxisInfo.DESCENDANT:
            case AxisInfo.FOLLOWING_SIBLING:
            case AxisInfo.NAMESPACE:
            case AxisInfo.PRECEDING_SIBLING:
                return EmptyIterator.ofNodes();

            case AxisInfo.FOLLOWING:
                return new Navigator.AxisFilter(new Navigator.FollowingEnumeration(this), nodeTest);

            case AxisInfo.PARENT:
                return Navigator.filteredSingleton(parent, nodeTest);

            case AxisInfo.PRECEDING:
                return new Navigator.AxisFilter(new Navigator.PrecedingEnumeration(this, false), nodeTest);

            case AxisInfo.DESCENDANT_OR_SELF:
            case AxisInfo.SELF:
                return Navigator.filteredSingleton(this, nodeTest);

            case AxisInfo.PRECEDING_OR_ANCESTOR:
                return new Navigator.AxisFilter(new Navigator.PrecedingEnumeration(this, true), nodeTest);

            default:
                throw new IllegalArgumentException("Unknown axis number " + axisNumber);
        }
    }

    /**
     * Get the string value of a given attribute of this node
     *
     * @param uri   the namespace URI of the attribute name. Supply the empty string for an attribute
     *              that is in no namespace
     * @param local the local part of the attribute name.
     * @return the attribute value if it exists, or null if it does not exist. Always returns null
     *         if this node is not an element.
     * @since 9.4
     */

    @Override
    public String getAttributeValue(/*@NotNull*/ String uri, /*@NotNull*/ String local) {
        return null;
    }

    /**
     * Get the root node of the tree containing this node
     *
     * @return the NodeInfo representing the top-level ancestor of this node.
     *         This will not necessarily be a document node
     */

    @Override
    public NodeInfo getRoot() {
        return parent.getRoot();
    }

    /**
     * Determine whether the node has any children.
     * <p>Note: the result is equivalent to
     * <code>getEnumeration(Axis.CHILD, AnyNodeTest.getInstance()).hasNext()</code></p>
     */

    @Override
    public boolean hasChildNodes() {
        return node instanceof OMContainer && ((OMContainer) node).getFirstOMChild() != null;
    }

    /**
     * Get a character string that uniquely identifies this node. Note:
     * a.isSameNode(b) if and only if generateId(a)==generateId(b)
     *
     * @param buffer a buffer to contain a string that uniquely identifies this node, across all documents
     */

    @Override
    public void generateId(FastStringBuffer buffer) {
        Navigator.appendSequentialKey(this, buffer, true);
        //buffer.append(Navigator.getSequentialKey(this));
    }

    /**
     * Get all namespace undeclarations and undeclarations defined on this element.
     *
     * @param buffer If this is non-null, and the result array fits in this buffer, then the result
     *               may overwrite the contents of this array, to avoid the cost of allocating a new array on the heap.
     * @return An array of integers representing the namespace declarations and undeclarations present on
     *         this element. For a node other than an element, return null. Otherwise, the returned array is a
     *         sequence of namespace codes, whose meaning may be interpreted by reference to the name pool. The
     *         top half word of each namespace code represents the prefix, the bottom half represents the URI.
     *         If the bottom half is zero, then this is a namespace undeclaration rather than a declaration.
     *         The XML namespace is never included in the list. If the supplied array is larger than required,
     *         then the first unused entry will be set to -1.
     *         <p>For a node other than an element, the method returns null.</p>
     */

    @Override
    public NamespaceBinding[] getDeclaredNamespaces(NamespaceBinding[] buffer) {
        return null;
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
        return null;
    }

    /**
     * Determine whether this node has the is-id property
     *
     * @return true if the node is an ID
     */

    @Override
    public boolean isId() {
        return "ID".equals(node.getAttributeType());
    }

    /**
     * Determine whether this node has the is-idref property
     *
     * @return true if the node is an IDREF or IDREFS element or attribute
     */

    @Override
    public boolean isIdref() {
        String type = node.getAttributeType();
        return "IDREF".equals(type) || "IDREFS".equals(type);
    }

}

