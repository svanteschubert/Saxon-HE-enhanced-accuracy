////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.wrapper;

import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.SchemaType;

import java.util.function.Predicate;


/**
 * AbstractVirtualNode is an abstract superclass for VirtualNode implementations in which
 * the underlying node is itself a Saxon NodeInfo.
 */

public abstract class AbstractVirtualNode implements VirtualNode {

    protected NodeInfo node;
    /*@Nullable*/ protected AbstractVirtualNode parent;     // null means unknown
    protected TreeInfo docWrapper;

    /**
     * Get information about the tree to which this NodeInfo belongs
     *
     * @return the TreeInfo
     * @since 9.7
     */
    @Override
    public TreeInfo getTreeInfo() {
        return docWrapper;
    }

    /**
     * Get the underlying node, to implement the VirtualNode interface
     */

    @Override
    public NodeInfo getUnderlyingNode() {
        return node;
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
        if (node.hasFingerprint()) {
            return node.getFingerprint();
        } else {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Test whether a fingerprint is available for the node name
     */
    @Override
    public boolean hasFingerprint() {
        return node.hasFingerprint();
    }

    /**
     * Get the node underlying this virtual node. If this is a VirtualNode the method
     * will automatically drill down through several layers of wrapping.
     *
     * @return The underlying node.
     */

    @Override
    public Object getRealNode() {
        Object u = this;
        do {
            u = ((VirtualNode) u).getUnderlyingNode();
        } while (u instanceof VirtualNode);
        return u;
    }

    /**
     * Return the type of node.
     *
     * @return one of the values Node.ELEMENT, Node.TEXT, Node.ATTRIBUTE, etc.
     */

    @Override
    public int getNodeKind() {
        return node.getNodeKind();
    }

    /**
     * Get the typed value.
     *
     * @return the typed value. If requireSingleton is set to true, the result will always be an
     *         AtomicValue. In other cases it may be a Value representing a sequence whose items are atomic
     *         values.
     * @since 8.5
     */

    @Override
    public AtomicSequence atomize() throws XPathException {
        // The default implementation atomizes the base node
        return node.atomize();
    }

    /**
     * Get the type annotation
     *
     * @return the type annotation of the base node
     */

    @Override
    public SchemaType getSchemaType() {
        return node.getSchemaType();
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
        if (other instanceof AbstractVirtualNode) {
            return node.equals(((AbstractVirtualNode) other).node);
        } else {
            return node.equals(other);
        }
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
        return node.hashCode() ^ 0x3c3c3c3c;
    }

    /**
     * Get the System ID for the node.
     *
     * @return the System Identifier of the entity in the source document containing the node,
     *         or null if not known. Note this is not the same as the base URI: the base URI can be
     *         modified by xml:base, but the system ID cannot.
     */

    /*@Nullable*/
    @Override
    public String getSystemId() {
        return node.getSystemId();
    }

    @Override
    public void setSystemId(String uri) {
        node.setSystemId(uri);
    }

    /**
     * Get the Base URI for the node, that is, the URI used for resolving a relative URI contained
     * in the node. In the JDOM model, base URIs are held only an the document level. We don't
     * currently take any account of xml:base attributes.
     */

    @Override
    public String getBaseURI() {
        return node.getBaseURI();
    }

    /**
     * Get line number
     *
     * @return the line number of the node in its original source document; or -1 if not available
     */

    @Override
    public int getLineNumber() {
        return node.getLineNumber();
    }

    /**
     * Get column number
     *
     * @return the column number of the node in its original source document; or -1 if not available
     */

    @Override
    public int getColumnNumber() {
        return node.getColumnNumber();
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
     * Determine the relative position of this node and another node, in document order.
     * The other node will always be in the same document.
     *
     * @param other The other node, whose position is to be compared with this node
     * @return -1 if this node precedes the other node, +1 if it follows the other
     *         node, or 0 if they are the same node. (In this case, isSameNode() will always
     *         return true, and the two nodes will produce the same result for generateId())
     */

    @Override
    public int compareOrder(/*@NotNull*/ NodeInfo other) {
        if (other instanceof AbstractVirtualNode) {
            return node.compareOrder(((AbstractVirtualNode) other).node);
        } else {
            return node.compareOrder(other);
        }
    }

    /**
     * Return the string value of the node. The interpretation of this depends on the type
     * of node. For an element it is the accumulated character content of the element,
     * including descendant elements.
     *
     * @return the string value of the node
     */

    @Override
    public final String getStringValue() {
        return getStringValueCS().toString();
    }

    /**
     * Get the value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String.
     */

    @Override
    public CharSequence getStringValueCS() {
        // default implementation returns the string value of the base node
        return node.getStringValueCS();
    }

    /**
     * Get the local part of the name of this node. This is the name after the ":" if any.
     *
     * @return the local part of the name. For an unnamed node, returns null, except for
     *         un unnamed namespace node, which returns "".
     */

    @Override
    public String getLocalPart() {
        return node.getLocalPart();
    }

    /**
     * Get the URI part of the name of this node. This is the URI corresponding to the
     * prefix, or the URI of the default namespace if appropriate.
     *
     * @return The URI of the namespace of this node. For an unnamed node, return null.
     *         For a node with an empty prefix, return an empty string.
     */

    @Override
    public String getURI() {
        return node.getURI();
    }

    /**
     * Get the prefix of the name of the node. This is defined only for elements and attributes.
     * If the node has no prefix, or for other kinds of node, return a zero-length string.
     *
     * @return The prefix of the name of the node.
     */

    @Override
    public String getPrefix() {
        return node.getPrefix();
    }

    /**
     * Get the display name of this node. For elements and attributes this is [prefix:]localname.
     * For unnamed nodes, it is an empty string.
     *
     * @return The display name of this node.
     *         For a node with no name, return an empty string.
     */

    @Override
    public String getDisplayName() {
        return node.getDisplayName();
    }

    /**
     * Return an iteration over the nodes reached by the given axis from this node
     *
     * @param axisNumber the axis to be used
     * @param nodeTest   A pattern to be matched by the returned nodes
     * @return a SequenceIterator that scans the nodes reached by the axis in turn.
     */

    /*@NotNull*/
    @Override
    public AxisIterator iterateAxis(int axisNumber, Predicate<? super NodeInfo> nodeTest) {
        return new Navigator.AxisFilter(iterateAxis(axisNumber), nodeTest);
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
        return node.getAttributeValue(uri, local);
    }

    /**
     * Get the root node
     *
     * @return the NodeInfo representing the root of the tree
     */

    @Override
    public NodeInfo getRoot() {
        NodeInfo p = this;
        while (true) {
            NodeInfo q = p.getParent();
            if (q == null) {
                return p;
            }
            p = q;
        }
    }

    /**
     * Determine whether the node has any children.
     * <p>Note: the result is equivalent to
     * <code>getEnumeration(Axis.CHILD, AnyNodeTest.getInstance()).hasNext()</code></p>
     */

    @Override
    public boolean hasChildNodes() {
        return node.hasChildNodes();
    }

    /**
     * Get a character string that uniquely identifies this node.
     * Note: a.isSameNode(b) if and only if generateId(a)==generateId(b)
     *
     * @param buffer a buffer, into which will be placed
     *               a string that uniquely identifies this node, within this
     *               document. The calling code prepends information to make the result
     *               unique across all documents.
     */

    @Override
    public void generateId(FastStringBuffer buffer) {
        // Note: giving the node the same ID as its underlying node is slightly questionable; depends on usage
        node.generateId(buffer);
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
    @SuppressWarnings("deprecation")
    public NamespaceBinding[] getDeclaredNamespaces(NamespaceBinding[] buffer) {
        return node.getDeclaredNamespaces(buffer);
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
        return node.getAllNamespaces();
    }

    /**
     * Determine whether this node has the is-id property
     *
     * @return true if the node is an ID
     */

    @Override
    public boolean isId() {
        return node.isId();
    }

    /**
     * Determine whether this node has the is-idref property
     *
     * @return true if the node is an IDREF or IDREFS element or attribute
     */

    @Override
    public boolean isIdref() {
        return node.isIdref();
    }

    /**
     * Determine whether the node has the is-nilled property
     *
     * @return true if the node has the is-nilled property
     */

    @Override
    public boolean isNilled() {
        return node.isNilled();
    }
}

