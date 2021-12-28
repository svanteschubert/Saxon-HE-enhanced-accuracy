////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.wrapper;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.NamespaceNode;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * This class represents a node that is a virtual copy of another node: that is, it behaves as a node that's the
 * same as another node, but has different identity. Moreover, this class can create a virtual copy of a subtree,
 * so that the parent of the virtual copy is null rather than being a virtual copy of the parent of the original.
 * This means that none of the axes when applied to the virtual copy is allowed to stray outside the subtree.
 * The virtual copy is implemented by means of a reference to the node of which
 * it is a copy, but methods that are sensitive to node identity return a different result.
 *
 */

public class VirtualCopy implements NodeInfo {

    protected Supplier<String> systemIdSupplier;
    protected NodeInfo original;
    protected VirtualCopy parent;
    protected NodeInfo root;        // the node forming the root of the subtree that was copied
    protected VirtualTreeInfo tree;
    private boolean dropNamespaces = false;

    /**
     * Protected constructor: create a virtual copy of a node
     *
     * @param base the node to be copied
     * @param root the node in the source tree corresponding to the root of the virtual tree. This must be an ancestor
     *             of the base node
     */

    protected VirtualCopy(NodeInfo base, NodeInfo root) {
        original = base;
        systemIdSupplier = base::getBaseURI; // computing the base URI can be expensive, so do it lazily
        this.root = root;
    }

    /**
     * Public factory method: create a parentless virtual tree as a copy of an existing node
     *
     * @param original the node to be copied
     * @return the virtual copy. If the original was already a virtual copy, this will be a virtual copy
     *         of the real underlying node.
     */

    public static VirtualCopy makeVirtualCopy(NodeInfo original) {

        VirtualCopy vc;
        // Don't allow copies of copies of copies: define the new copy in terms of the original
        while (original instanceof VirtualCopy) {
            original = ((VirtualCopy) original).original;
        }

        vc = new VirtualCopy(original, original);

        Configuration config = original.getConfiguration();
        VirtualTreeInfo doc = new VirtualTreeInfo(config, vc);
        long docNr = config.getDocumentNumberAllocator().allocateDocumentNumber();
        doc.setDocumentNumber(docNr);
        vc.tree = doc;

        return vc;
    }

    /**
     * Wrap a node within an existing VirtualCopy.
     *
     * @param node the node to be wrapped
     * @return a virtual copy of the node
     */

    /*@NotNull*/
    protected VirtualCopy wrap(NodeInfo node) {
        VirtualCopy vc = new VirtualCopy(node, root);
        vc.tree = tree;
        vc.systemIdSupplier = systemIdSupplier;
        vc.dropNamespaces = dropNamespaces;
        return vc;
    }

    /**
     * Get the original (wrapped) node
     * @return the node of which this one is a virtual copy
     */

    public NodeInfo getOriginalNode() {
        return original;
    }

    /**
     * Get information about the tree to which this NodeInfo belongs
     *
     * @return the TreeInfo
     * @since 9.7
     */
    @Override
    public VirtualTreeInfo getTreeInfo() {
        return tree;
    }

    /**
     * Say that namespaces in the virtual tree should not be copied from the underlying
     * tree. The semantics follow the rules for xsl:copy-of with copy-namespaces="no": that
     * is, the only namespaces that are retained are those explicitly used in element or
     * attribute nodes.
     * @param drop true if namespaces are to be dropped
     */

    public void setDropNamespaces(boolean drop) {
        this.dropNamespaces = drop;
    }

    @Override
    public NamespaceMap getAllNamespaces() {
        if (getNodeKind() == Type.ELEMENT) {
            if (dropNamespaces) {
                NamespaceMap nsMap = NamespaceMap.emptyMap();
                String ns = getURI();
                if (!ns.isEmpty()) {
                    nsMap = nsMap.put(getPrefix(), ns);
                }
                AxisIterator iter = original.iterateAxis(AxisInfo.ATTRIBUTE);
                NodeInfo att;
                while ((att = iter.next()) != null) {
                    if (!att.getURI().equals("")) {
                        nsMap = nsMap.put(att.getPrefix(), att.getURI());
                    }
                }
                return nsMap;
            } else {
                return original.getAllNamespaces();
            }
        } else {
            return null;
        }
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
        return original.getFingerprint();
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
        return original.hasFingerprint();
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
        return original.getNodeKind();
    }

    /**
     * Determine whether this is the same node as another node.
     * Note: a.isSameNodeInfo(b) if and only if generateId(a)==generateId(b).
     * This method has the same semantics as isSameNode() in DOM Level 3, but
     * works on Saxon NodeInfo objects rather than DOM Node objects.
     *
     * @param other the node to be compared with this node
     * @return true if this NodeInfo object and the supplied NodeInfo object represent
     *         the same node in the tree.
     */

    public boolean equals(Object other) {
        return other instanceof VirtualCopy &&
                getTreeInfo() == ((VirtualCopy) other).getTreeInfo() &&
                original.equals(((VirtualCopy) other).original);
    }

    /**
     * The hashCode() method obeys the contract for hashCode(): that is, if two objects are equal
     * (represent the same node) then they must have the same hashCode()
     */

    public int hashCode() {
        return original.hashCode() ^ ((int) (getTreeInfo().getDocumentNumber() & 0x7fffffff) << 19);
    }

    /**
     * Get the System ID for the node.
     *
     * @return the System Identifier of the entity in the source document
     *         containing the node, or null if not known. Note this is not the
     *         same as the base URI: the base URI can be modified by xml:base, but
     *         the system ID cannot.
     */

    @Override
    public String getSystemId() {
        return systemIdSupplier.get();
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
        return Navigator.getBaseURI(this);
    }

    /**
     * Get line number
     *
     * @return the line number of the node in its original source document; or
     *         -1 if not available
     */

    @Override
    public int getLineNumber() {
        return original.getLineNumber();
    }

    /**
     * Get column number
     *
     * @return the column number of the node in its original source document; or -1 if not available
     */

    @Override
    public int getColumnNumber() {
        return original.getColumnNumber();
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
        if (other instanceof VirtualCopy) {
            int c = root.compareOrder(((VirtualCopy) other).root);
            if (c == 0) {
                return original.compareOrder(((VirtualCopy) other).original);
            } else {
                return c;
            }
        } else {
            return other.compareOrder(original);
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
    public String getStringValue() {
        return getStringValueCS().toString();
    }

    /**
     * Get the value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String.
     */

    @Override
    public CharSequence getStringValueCS() {
        return original.getStringValueCS();
    }

    /**
     * Get the local part of the name of this node. This is the name after the ":" if any.
     *
     * @return the local part of the name. For an unnamed node, returns "". Unlike the DOM
     *         interface, this returns the full name in the case of a non-namespaced name.
     */

    @Override
    public String getLocalPart() {
        return original.getLocalPart();
    }

    /**
     * Get the URI part of the name of this node. This is the URI corresponding to the
     * prefix, or the URI of the default namespace if appropriate.
     *
     * @return The URI of the namespace of this node. For an unnamed node,
     *         or for a node with an empty prefix, return an empty
     *         string.
     */

    @Override
    public String getURI() {
        return original.getURI();
    }

    /**
     * Get the prefix of the name of the node. This is defined only for elements and attributes.
     * If the node has no prefix, or for other kinds of node, return a zero-length string.
     *
     * @return The prefix of the name of the node.
     */

    @Override
    public String getPrefix() {
        return original.getPrefix();
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
        return original.getDisplayName();
    }

    /**
     * Get the configuration
     */

    @Override
    public Configuration getConfiguration() {
        return original.getConfiguration();
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
        return original.getSchemaType();
    }

    /**
     * Get the NodeInfo object representing the parent of this node
     *
     * @return the parent of this node; null if this node has no parent
     */

    /*@Nullable*/
    @Override
    public NodeInfo getParent() {
        if (original.equals(root)) {
            return null;
        }
        if (parent == null) {
            NodeInfo basep = original.getParent();
            if (basep == null) {
                return null;
            }
            parent = wrap(basep);
        }
        return parent;
    }

    /**
     * Return an iteration over all the nodes reached by the given axis from this node
     * that match a given NodeTest
     *
     * @param axisNumber an integer identifying the axis; one of the constants
     *                   defined in class net.sf.saxon.om.Axis
     * @param nodeTest   A pattern to be matched by the returned nodes; nodes
     *                   that do not match this pattern are not included in the result
     * @return an AxisIterator that scans the nodes reached by the axis in
     *         turn.
     * @throws UnsupportedOperationException if the namespace axis is
     *                                       requested and this axis is not supported for this implementation.
     * @see net.sf.saxon.om.AxisInfo
     */

    @Override
    public AxisIterator iterateAxis(int axisNumber, Predicate<? super NodeInfo> nodeTest) {
        VirtualCopy newParent = null;
        switch (axisNumber) {
            case AxisInfo.CHILD:
            case AxisInfo.ATTRIBUTE:
                newParent = this;
                break;
            case AxisInfo.SELF:
            case AxisInfo.PRECEDING_SIBLING:
            case AxisInfo.FOLLOWING_SIBLING:
                newParent = parent;
                break;
            // Ensure that the ancestor, ancestor-or-self, following, and preceding axes use an implementation
            // that relies on getParent() to escape from the subtree
            case AxisInfo.ANCESTOR:
                return new Navigator.AxisFilter(
                        new Navigator.AncestorEnumeration(this, false), nodeTest);
            case AxisInfo.ANCESTOR_OR_SELF:
                return new Navigator.AxisFilter(
                        new Navigator.AncestorEnumeration(this, true), nodeTest);
            case AxisInfo.NAMESPACE:
                if (getNodeKind() != Type.ELEMENT) {
                    return EmptyIterator.ofNodes();
                }
                return NamespaceNode.makeIterator(this, nodeTest);
            case AxisInfo.PARENT:
                return Navigator.filteredSingleton(getParent(), nodeTest);
            case AxisInfo.PRECEDING:
                return new Navigator.AxisFilter(
                        new Navigator.PrecedingEnumeration(this, false), nodeTest);
            case AxisInfo.FOLLOWING:
                return new Navigator.AxisFilter(
                        new Navigator.FollowingEnumeration(this), nodeTest);
            case AxisInfo.PRECEDING_OR_ANCESTOR:
                return new Navigator.AxisFilter(
                        new Navigator.PrecedingEnumeration(this, true), nodeTest);

        }
        return makeCopier(original.iterateAxis(axisNumber, nodeTest), newParent, !AxisInfo.isSubtreeAxis[axisNumber]);
    }

    /**
     * Get the string value of a given attribute of this node
     *
     * @param uri   the namespace URI of the attribute name. Supply the empty string for an attribute
     *              that is in no namespace
     * @param local the local part of the attribute name.
     * @return the attribute value if it exists, or null if it does not exist. Always returns null
     *         if this node is not an element.
     */
    @Override
    public String getAttributeValue(/*@NotNull*/ String uri, /*@NotNull*/ String local) {
        return original.getAttributeValue(uri, local);
    }

    /**
     * Get the root node of the tree containing this node
     *
     * @return the NodeInfo representing the top-level ancestor of this node.
     *         This will not necessarily be a document node
     */

    /*@Nullable*/
    @Override
    public NodeInfo getRoot() {
        NodeInfo n = this;
        while (true) {
            NodeInfo p = n.getParent();
            if (p == null) {
                return n;
            }
            n = p;
        }
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
        return original.hasChildNodes();
    }

    /**
     * Get a character string that uniquely identifies this node.
     * Note: a.isSameNode(b) if and only if generateId(a)==generateId(b)
     *
     * @param buffer a buffer, to which will be appended
     *               a string that uniquely identifies this node, across all
     *               documents.
     */

    @Override
    public void generateId(/*@NotNull*/ FastStringBuffer buffer) {
        buffer.append("d");
        buffer.append(Long.toString(getTreeInfo().getDocumentNumber()));
        original.generateId(buffer);
    }

    /**
     * Copy this node to a given outputter
     * @param out         the Receiver to which the node should be copied
     * @param copyOptions a selection of the options defined in {@link CopyOptions}
     * @param locationId  Identifies the location of the instruction
     */

    @Override
    public void copy(Receiver out, int copyOptions, Location locationId) throws XPathException {
        if (dropNamespaces) {
            copyOptions &= ~CopyOptions.ALL_NAMESPACES;
        }
        original.copy(out, copyOptions, locationId);
    }

    /**
     * Get all namespace declarations and undeclarations defined on this element.
     *
     * @param buffer If this is non-null, and the result array fits in this buffer, then the result
     *               may overwrite the contents of this array, to avoid the cost of allocating a new array on the heap.
     * @return An array representing the namespace declarations and undeclarations present on
     *         this element. For a node other than an element, return null. Otherwise, the returned array is a
     *         sequence of NamespaceBinding objects.
     *         The XML namespace is never included in the list. If the supplied array is larger than required,
     *         then the first unused entry will be set to null.
     *         <p>For a VirtualCopy, the method needs to return all namespaces that are in scope for
     *         this element, because the virtual copy is assumed to contain copies of all the in-scope
     *         namespaces of the original.</p>
     */

    @Override
    @SuppressWarnings("deprecation")
    public NamespaceBinding[] getDeclaredNamespaces(NamespaceBinding[] buffer) {
        if (getNodeKind() == Type.ELEMENT) {
            if (dropNamespaces) {
                List<NamespaceBinding> allNamespaces = new ArrayList<>(5);
                String ns = getURI();
                if (ns.isEmpty()) {
                    if (getParent() != null && !getParent().getURI().isEmpty()) {
                        allNamespaces.add(new NamespaceBinding("", ""));
                    }
                } else {
                    allNamespaces.add(new NamespaceBinding(getPrefix(), getURI()));
                }
                for (AttributeInfo att : original.attributes()) {
                    NodeName name = att.getNodeName();
                    if (name.getURI() != null) {
                        NamespaceBinding b = new NamespaceBinding(name.getPrefix(), name.getURI());
                        if (!allNamespaces.contains(b)) {
                            allNamespaces.add(b);
                        }
                    }
                }
                return allNamespaces.toArray(NamespaceBinding.EMPTY_ARRAY);
            } else {
                if (original == root) {
                    List<NamespaceBinding> bindings = new ArrayList<>();
                    for (NamespaceBinding binding : original.getAllNamespaces()) {
                        bindings.add(binding);
                    }
                    return bindings.toArray(NamespaceBinding.EMPTY_ARRAY);
                } else {
                    return original.getDeclaredNamespaces(buffer);
                }
            }
        } else {
            return null;
        }
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
        this.systemIdSupplier = () -> systemId;
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
        return original.atomize();
    }

    /**
     * Determine whether this node has the is-id property
     *
     * @return true if the node is an ID
     */

    @Override
    public boolean isId() {
        return original.isId();
    }

    /**
     * Determine whether this node has the is-idref property
     *
     * @return true if the node is an IDREF or IDREFS element or attribute
     */

    @Override
    public boolean isIdref() {
        return original.isIdref();
    }

    /**
     * Determine whether the node has the is-nilled property
     *
     * @return true if the node has the is-nilled property
     */

    @Override
    public boolean isNilled() {
        return original.isNilled();
    }

    /**
     * Return the public identifier for the current document event.
     * <p>The return value is the public identifier of the document
     * entity or of the external parsed entity in which the markup that
     * triggered the event appears.</p>
     *
     * @return A string containing the public identifier, or
     *         null if none is available.
     * @see #getSystemId
     */
    /*@Nullable*/
    @Override
    public String getPublicId() {
        return original != null ? original.getPublicId() : null;
    }

    /**
     * Ask whether a node in the source tree is within the scope of this virtual copy
     * @param sourceNode the node being tested
     * @return true if the node is within the scope of the subtree
     */

    protected boolean isIncludedInCopy(NodeInfo sourceNode) {
        return Navigator.isAncestorOrSelf(root, sourceNode);
    }

    /**
     * Create an iterator that makes and returns virtual copies of nodes on the original tree
     *
     * @param axis          the axis to be navigated
     * @param newParent     the parent of the nodes in the new virtual tree (may be null)
     * @param testInclusion if true, it is necessary to test whether nodes found in the source tree are
     *                      included in the copy. If false, this test is unnecessary.
     * @return the iterator that does the copying
     */

    protected VirtualCopier makeCopier(AxisIterator axis, VirtualCopy newParent, boolean testInclusion) {
        return new VirtualCopier(axis, newParent, testInclusion);
    }

    /**
     * VirtualCopier implements the XPath axes as applied to a VirtualCopy node. It works by
     * applying the requested axis to the node of which this is a copy. There are two
     * complications: firstly, all nodes encountered must themselves be (virtually) copied
     * to give them a new identity. Secondly, axes that stray outside the subtree rooted at
     * the original copied node must be truncated.
     */

    protected class VirtualCopier implements AxisIterator {

        protected AxisIterator base;
        private VirtualCopy parent;
        protected boolean testInclusion;

        public VirtualCopier(AxisIterator base, VirtualCopy parent, boolean testInclusion) {
            this.base = base;
            this.parent = parent;
            this.testInclusion = testInclusion;
        }


        /**
         * Get the next item in the sequence. <BR>
         *
         * @return the next Item. If there are no more nodes, return null.
         */

        /*@Nullable*/
        @Override
        public NodeInfo next() {
            NodeInfo next = base.next();
            if (next != null) {
                if (testInclusion && !isIncludedInCopy(next)) {
                    // we're only interested in nodes within the subtree that was copied.
                    // Assert: once we find a node outside this subtree, all further nodes will also be outside
                    //         the subtree.
                    return null;
                }
                VirtualCopy vc = wrap(next);
                vc.parent = parent;
                vc.systemIdSupplier = systemIdSupplier;
                next = vc;
            }
            return next;
        }

        @Override
        public void close() {
            base.close();
        }


    }
}

