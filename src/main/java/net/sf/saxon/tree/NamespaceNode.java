////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.*;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * This class represents a namespace node; it is used in several tree models.
 */

public class NamespaceNode implements NodeInfo {

    NodeInfo element;
    NamespaceBinding nsBinding;
    int position;
    int fingerprint;

    /**
     * Create a namespace node
     *
     * @param element  the parent element of the namespace node
     * @param nscode   the namespace code, representing the prefix and URI of the namespace binding
     * @param position maintains document order among namespace nodes for the same element
     */

    public NamespaceNode(NodeInfo element, NamespaceBinding nscode, int position) {
        this.element = element;
        this.nsBinding = nscode;
        this.position = position;
        fingerprint = -1;  // evaluated lazily to avoid NamePool access
    }

    /**
     * Get information about the tree to which this NodeInfo belongs
     *
     * @return the TreeInfo
     * @since 9.7
     */
    @Override
    public TreeInfo getTreeInfo() {
        return element.getTreeInfo();
    }

    /**
     * To implement {@link Sequence}, this method returns the item itself
     *
     * @return this item
     */

    @Override
    public NodeInfo head() {
        return this;
    }

    /**
     * Get the kind of node. This will be a value such as Type.ELEMENT or Type.ATTRIBUTE
     *
     * @return an integer identifying the kind of node. These integer values are the
     *         same as those used in the DOM
     * @see net.sf.saxon.type.Type
     */

    @Override
    public int getNodeKind() {
        return Type.NAMESPACE;
    }

    /**
     * Determine whether this is the same node as another node.
     * Note: a.equals(b) if and only if generateId(a)==generateId(b).
     * This method has the same semantics as isSameNode() in DOM Level 3, but
     * works on Saxon NodeInfo objects rather than DOM Node objects.
     *
     * @param other the node to be compared with this node
     * @return true if this NodeInfo object and the supplied NodeInfo object represent
     *         the same node in the tree.
     */

    public boolean equals(Object other) {
        return other instanceof NamespaceNode &&
                element.equals(((NamespaceNode) other).element) &&
                nsBinding.equals(((NamespaceNode) other).nsBinding);

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
        return element.hashCode() ^ (position << 13);
    }

    /**
     * Get the System ID for the node.
     *
     * @return the System Identifier of the entity in the source document
     *         containing the node, or null if not known. Note this is not the
     *         same as the base URI: the base URI can be modified by xml:base, but
     *         the system ID cannot.
     */

    /*@Nullable*/
    @Override
    public String getSystemId() {
        return element.getSystemId();
    }

    /**
     * Get the Public ID of the entity containing the node.
     *
     * @return the Public Identifier of the entity in the source document
     * containing the node, or null if not known or not applicable
     * @since 9.7
     */
    @Override
    public String getPublicId() {
        return element.getPublicId();
    }

    /**
     * Get the Base URI for the node, that is, the URI used for resolving a relative URI contained
     * in the node. This will be the same as the System ID unless xml:base has been used.
     *
     * @return the base URI of the node
     */

    /*@Nullable*/
    @Override
    public String getBaseURI() {
        return null;    // the base URI of a namespace node is the empty sequence
    }

    /**
     * Get line number
     *
     * @return the line number of the node in its original source document; or
     *         -1 if not available
     */

    @Override
    public int getLineNumber() {
        return element.getLineNumber();
    }

    /**
     * Get column number
     *
     * @return the column number of the node in its original source document; or
     *         -1 if not available
     */

    @Override
    public int getColumnNumber() {
        return element.getColumnNumber();
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
     * @param other The other node, whose position is to be compared with this
     *              node
     * @return -1 if this node precedes the other node, +1 if it follows the
     *         other node, or 0 if they are the same node. (In this case,
     *         isSameNode() will always return true, and the two nodes will
     *         produce the same result for generateId())
     */

    @Override
    public int compareOrder(/*@NotNull*/ NodeInfo other) {
        if (other instanceof NamespaceNode && element.equals(((NamespaceNode) other).element)) {
            int c = position - ((NamespaceNode) other).position;
            return Integer.compare(c, 0);
        } else if (element.equals(other)) {
            return +1;
        } else {
            return element.compareOrder(other);
        }
    }

    /**
     * Return the string value of the node. The interpretation of this depends on the type
     * of node. For a namespace node, it is the namespace URI.
     *
     * @return the string value of the node
     */

    @Override
    public String getStringValue() {
        return nsBinding.getURI();
    }

    /**
     * Get the value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String.
     */

    @Override
    public CharSequence getStringValueCS() {
        return getStringValue();
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
        return true;
    }

    /**
     * Get fingerprint. The fingerprint is a coded form of the expanded name
     * of the node: two nodes
     * with the same name code have the same namespace URI and the same local name.
     * A fingerprint of -1 should be returned for a node with no name.
     *
     * @return an integer fingerprint; two nodes with the same fingerprint have
     *         the same expanded QName
     */

    @Override
    public int getFingerprint() {
        if (fingerprint == -1) {
            if (nsBinding.getPrefix().isEmpty()) {
                return -1;
            } else {
                fingerprint = element.getConfiguration().getNamePool().allocateFingerprint("", nsBinding.getPrefix());
            }
        }
        return fingerprint;
    }

    /**
     * Get the local part of the name of this node. This is the name after the ":" if any.
     *
     * @return the local part of the name. For an unnamed node, returns "". Unlike the DOM
     *         interface, this returns the full name in the case of a non-namespaced name.
     */

    @Override
    public String getLocalPart() {
        return nsBinding.getPrefix();
    }

    /**
     * Get the URI part of the name of this node. This is the URI corresponding to the
     * prefix, or the URI of the default namespace if appropriate.
     *
     * @return The URI of the namespace of this node. Since the name of a namespace
     *         node is always an NCName (the namespace prefix), this method always returns "".
     */

    /*@NotNull*/
    @Override
    public String getURI() {
        return "";
    }

    /**
     * Get the display name of this node. For elements and attributes this is [prefix:]localname.
     * For unnamed nodes, it is an empty string.
     *
     * @return The display name of this node. For a node with no name, return
     *         an empty string.
     */

    @Override
    public String getDisplayName() {
        return getLocalPart();
    }

    /**
     * Get the prefix of the name of the node. This is defined only for elements and attributes.
     * If the node has no prefix, or for other kinds of node, return a zero-length string.
     *
     * @return The prefix of the name of the node.
     */

    /*@NotNull*/
    @Override
    public String getPrefix() {
        return "";
    }

    /**
     * Get the configuration
     */

    @Override
    public Configuration getConfiguration() {
        return element.getConfiguration();
    }

    /**
     * Get the NamePool that holds the namecode for this node
     *
     * @return the namepool
     */

    public NamePool getNamePool() {
        return getConfiguration().getNamePool();
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
        return BuiltInAtomicType.STRING;
    }

    /**
     * Get the NodeInfo object representing the parent of this node
     *
     * @return the parent of this node; null if this node has no parent
     */

    @Override
    public NodeInfo getParent() {
        return element;
    }

    /**
     * Return an iteration over all the nodes reached by the given axis from this node
     * that match a given NodeTest
     *
     * @param axisNumber an integer identifying the axis; one of the constants
     *                   defined in class net.sf.saxon.om.Axis
     * @param nodeTest   A pattern to be matched by the returned nodes; nodes
     *                   that do not match this pattern are not included in the result
     * @return a NodeEnumeration that scans the nodes reached by the axis in
     *         turn.
     * @throws UnsupportedOperationException if the namespace axis is
     *                                       requested and this axis is not supported for this implementation.
     * @see net.sf.saxon.om.AxisInfo
     */

    @Override
    public AxisIterator iterateAxis(int axisNumber, Predicate<? super NodeInfo> nodeTest) {
        switch (axisNumber) {
            case AxisInfo.ANCESTOR:
                return element.iterateAxis(AxisInfo.ANCESTOR_OR_SELF, nodeTest);

            case AxisInfo.ANCESTOR_OR_SELF:
                if (nodeTest.test(this)) {
                    return new PrependAxisIterator(this, element.iterateAxis(AxisInfo.ANCESTOR_OR_SELF, nodeTest));
                } else {
                    return element.iterateAxis(AxisInfo.ANCESTOR_OR_SELF, nodeTest);
                }

            case AxisInfo.ATTRIBUTE:
            case AxisInfo.CHILD:
            case AxisInfo.DESCENDANT:
            case AxisInfo.DESCENDANT_OR_SELF:
            case AxisInfo.FOLLOWING_SIBLING:
            case AxisInfo.NAMESPACE:
            case AxisInfo.PRECEDING_SIBLING:
                return EmptyIterator.ofNodes();

            case AxisInfo.FOLLOWING:
                return new Navigator.AxisFilter(
                        new Navigator.FollowingEnumeration(this),
                        nodeTest);

            case AxisInfo.PARENT:
                return Navigator.filteredSingleton(element, nodeTest);

            case AxisInfo.PRECEDING:
                return new Navigator.AxisFilter(
                        new Navigator.PrecedingEnumeration(this, false),
                        nodeTest);

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
        return element.getRoot();
    }

    /**
     * Determine whether the node has any children.
     * <p>Note: the result is equivalent to
     * <code>getEnumeration(Axis.CHILD, AnyNodeTest.getInstance()).hasNext()</code></p>
     *
     * @return True if the node has one or more children
     */

    @Override
    public boolean hasChildNodes() {
        return false;
    }

    /**
     * Get a character string that uniquely identifies this node.
     * Note: a.isSameNode(b) if and only if generateId(a)==generateId(b)
     *
     * @param buffer buffer to hold a string that uniquely identifies this node, across all
     *               documents.
     */

    @Override
    public void generateId(/*@NotNull*/ FastStringBuffer buffer) {
        element.generateId(buffer);
        buffer.append("n");
        buffer.append(Integer.toString(position));
    }

    /**
     * Copy this node to a given outputter
     * @param out         the Receiver to which the node should be copied
     * @param copyOptions a selection of the options defined in {@link CopyOptions}
     * @param locationId  If non-zero, identifies the location of the instruction
     */

    @Override
    public void copy(/*@NotNull*/ Receiver out, int copyOptions, Location locationId) throws XPathException {
        out.append(this);
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

    /*@Nullable*/
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
     * Set the system identifier for this Source.
     * <p>The system identifier is optional if the source does not
     * get its data from a URL, but it may still be useful to provide one.
     * The application can use a system identifier, for example, to resolve
     * relative URIs and to include in error messages and warnings.</p>
     *
     * @param systemId The system identifier as a URL string.
     */
    @Override
    public void setSystemId(String systemId) {
        // no action: namespace nodes have the same base URI as their parent
    }

    /**
     * Get the typed value.
     *
     * @return the typed value.
     * @since 8.5
     */

    /*@NotNull*/
    @Override
    public AtomicSequence atomize() throws XPathException {
        return new StringValue(getStringValueCS());
    }

    @Override
    public boolean isStreamed() {
        return element.isStreamed();
    }

    /**
     * Factory method to create an iterator over the in-scope namespace nodes of an element
     *
     * @param element the node whose namespaces are required
     * @param test    used to filter the returned nodes
     * @return an iterator over the namespace nodes that satisfy the test
     */

    /*@NotNull*/
    public static AxisIterator makeIterator(final NodeInfo element, Predicate<? super NodeInfo> test) {
        List<NodeInfo> nodes = new ArrayList<>();
        Iterator<NamespaceBinding> bindings = element.getAllNamespaces().iterator();
        int position = 0;
        boolean foundXML = false;
        while (bindings.hasNext()) {
            NamespaceBinding binding = bindings.next();
            if (binding.getPrefix().equals("xml")) {
                foundXML = true;
            }
            NamespaceNode node = new NamespaceNode(element, binding, position++);
            if (test.test(node)) {
                nodes.add(node);
            }
        }
        if (!foundXML) {
            NamespaceNode node = new NamespaceNode(element, NamespaceBinding.XML, position);
            if (test.test(node)) {
                nodes.add(node);
            }
        }
        return new ListIterator.OfNodes(nodes);
    }
}

