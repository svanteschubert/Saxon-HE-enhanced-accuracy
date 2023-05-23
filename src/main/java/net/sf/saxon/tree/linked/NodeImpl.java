////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.NamespaceNode;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.util.SteppingNavigator;
import net.sf.saxon.tree.util.SteppingNode;
import net.sf.saxon.tree.wrapper.SiblingCountingNode;
import net.sf.saxon.type.*;
import net.sf.saxon.value.UntypedAtomicValue;

import java.util.function.Predicate;


/**
 * A node in the "linked" tree representing any kind of node except a namespace node.
 * Specific node kinds are represented by concrete subclasses.
 *
 * @author Michael H. Kay
 */

public abstract class NodeImpl
        implements MutableNodeInfo, SteppingNode<NodeImpl>, SiblingCountingNode, Location {

    /*@Nullable*/ private ParentNodeImpl parent;
    private int index; // Set to -1 when the node is deleted
    /**
     * Chararacteristic letters to identify each type of node, indexed using the node type
     * values. These are used as the initial letter of the result of generate-id()
     */

    /*@NotNull*/ public static final char[] NODE_LETTER =
            {'x', 'e', 'a', 't', 'x', 'x', 'x', 'p', 'c', 'r', 'x', 'x', 'x', 'n'};

    /**
     * To implement {@link Sequence}, this method returns the item itself
     *
     * @return this item
     */

    @Override
    public NodeImpl head() {
        return this;
    }

    /**
     * To implement {@link Sequence}, this method returns a singleton iterator
     * that delivers this item in the form of a sequence
     *
     * @return a singleton iterator that returns this item
     */
//
//    public SingletonIterator<NodeInfo> iterate() {
//        return SingletonIterator.makeIterator(this);
//    }

    /**
     * Get information about the tree to which this NodeInfo belongs
     *
     * @return the TreeInfo
     * @since 9.7
     */
    @Override
    public TreeInfo getTreeInfo() {
        return getPhysicalRoot();
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
     * Get the type annotation
     *
     * @return the type annotation of the base node
     */

    @Override
    public SchemaType getSchemaType() {
        return Untyped.getInstance();
    }

    /**
     * Get the column number of the node.
     * The default implementation returns -1, meaning unknown
     */

    @Override
    public int getColumnNumber() {
        if (parent == null) {
            return -1;
        } else {
            return parent.getColumnNumber();
        }
    }

    /**
     * Get the index position of this node among its siblings (starting from 0)
     *
     * @return 0 for the first child, 1 for the second child, etc. Returns -1 for a node
     * that has been deleted.
     */
    @Override
    public final int getSiblingPosition() {
        return index;
    }

    /**
     * Set the index position. For internal use only
     *
     * @param index the position of the node among its siblings, counting from zero.
     */

    protected final void setSiblingPosition(int index) {
        this.index = index;
    }

    /**
     * Get the typed value.
     *
     * @return the typed value. If requireSingleton is set to true, the result will always be an
     * AtomicValue. In other cases it may be a Value representing a sequence whose items are atomic
     * values.
     * @since 8.5
     */

    @Override
    public AtomicSequence atomize() throws XPathException {
        SchemaType stype = getSchemaType();
        if (stype == Untyped.getInstance() || stype == BuiltInAtomicType.UNTYPED_ATOMIC) {
            return new UntypedAtomicValue(getStringValueCS());
        } else {
            return stype.atomize(this);
        }
    }

    /**
     * Set the system ID of this node. This method is provided so that a NodeInfo
     * implements the javax.xml.transform.Source interface, allowing a node to be
     * used directly as the Source of a transformation
     */

    @Override
    public void setSystemId(String uri) {
        // overridden in DocumentImpl and ElementImpl
        NodeInfo p = getParent();
        if (p != null) {
            p.setSystemId(uri);
        }
    }

    /**
     * Determine whether this is the same node as another node
     *
     * @return true if this Node object and the supplied Node object represent the
     * same node in the tree.
     */

    public boolean equals(NodeInfo other) {
        // default implementation: differs for attribute and namespace nodes
        return this == other;
    }

    /**
     * Get the name of the node. Returns null for an unnamed node
     *
     * @return the name of the node
     */

    public NodeName getNodeName() {
        return null;
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
     * Get the fingerprint of the node. This is used to compare whether two nodes
     * have equivalent names. Return -1 for a node with no name.
     */

    @Override
    public int getFingerprint() {
        NodeName name = getNodeName();
        if (name == null) {
            return -1;
        } else {
            return name.obtainFingerprint(getConfiguration().getNamePool());
        }
    }

    /**
     * Get the attributes of the node. By default there are none; this is overridden for element nodes
     *
     * @return an empty attribute map
     */

    @Override
    public AttributeMap attributes() {
        return EmptyAttributeMap.getInstance();
    }

    /**
     * Get a character string that uniquely identifies this node
     */

    @Override
    public void generateId(/*@NotNull*/ FastStringBuffer buffer) {
        long seq = getSequenceNumber();
        if (seq == -1L) {
            getPhysicalRoot().generateId(buffer);
            buffer.cat(NODE_LETTER[getNodeKind()]);
            buffer.append(Long.toString(seq) + "h" + hashCode());
        } else {
            parent.generateId(buffer);
            buffer.cat(NODE_LETTER[getNodeKind()]);
            buffer.append(Integer.toString(index));
        }
    }

    /**
     * Get the system ID for the node. Default implementation for child nodes.
     */

    /*@Nullable*/
    @Override
    public String getSystemId() {
        return parent.getSystemId();
    }

    /**
     * Get the base URI for the node. Default implementation for child nodes.
     */

    @Override
    public String getBaseURI() {
        return parent.getBaseURI();
    }

    /**
     * Get the node sequence number (in document order). Sequence numbers are monotonic but not
     * consecutive. In the current implementation, parent nodes (elements and roots) have a zero
     * least-significant word, while namespaces, attributes, text nodes, comments, and PIs have
     * the top word the same as their owner and the bottom half reflecting their relative position.
     * This is the default implementation for child nodes.
     * For nodes added by XQuery Update, the sequence number is -1L
     *
     * @return the sequence number if there is one, or -1L otherwise.
     */

    protected long getSequenceNumber() {
        NodeImpl prev = this;
        for (int i = 0; ; i++) {
            if (prev instanceof ParentNodeImpl) {
                long prevseq = prev.getSequenceNumber();
                return prevseq == -1L ? prevseq : prevseq + 0x10000 + i;
                // note the 0x10000 is to leave room for namespace and attribute nodes.
            }
            assert prev != null;
            prev = prev.getPreviousInDocument();
        }

    }

    /**
     * Determine the relative position of this node and another node, in document order.
     * The other node will always be in the same document.
     *
     * @param other The other node, whose position is to be compared with this node
     * @return -1 if this node precedes the other node, +1 if it follows the other
     * node, or 0 if they are the same node. (In this case, isSameNode() will always
     * return true, and the two nodes will produce the same result for generateId())
     */

    @Override
    public final int compareOrder(/*@NotNull*/ NodeInfo other) {
        if (other instanceof NamespaceNode) {
            return 0 - other.compareOrder(this);
        }
        long a = getSequenceNumber();
        long b = ((NodeImpl) other).getSequenceNumber();
        if (a == -1L || b == -1L) {
            // Nodes added by XQuery Update do not have sequence numbers
            return Navigator.compareOrder(this, (NodeImpl) other);
        }
        return Long.compare(a, b);
    }

    /**
     * Get the configuration
     */

    @Override
    public Configuration getConfiguration() {
        return getPhysicalRoot().getConfiguration();
    }

    /**
     * Get the NamePool
     */

    public NamePool getNamePool() {
        return getPhysicalRoot().getNamePool();
    }

    /**
     * Get the prefix part of the name of this node. This is the name before the ":" if any.
     *
     * @return the prefix part of the name. For an unnamed node, return an empty string.
     */

    @Override
    public String getPrefix() {
        NodeName qName = getNodeName();
        return qName == null ? "" : qName.getPrefix();
    }

    /**
     * Get the URI part of the name of this node. This is the URI corresponding to the
     * prefix, or the URI of the default namespace if appropriate.
     *
     * @return The URI of the namespace of this node. For the null namespace, return an
     * empty string. For an unnamed node, return the empty string.
     */

    @Override
    public String getURI() {
        NodeName qName = getNodeName();
        return qName == null ? "" : qName.getURI();
    }

    /**
     * Get the display name of this node. For elements and attributes this is [prefix:]localname.
     * For unnamed nodes, it is an empty string.
     *
     * @return The display name of this node.
     * For a node with no name, return an empty string.
     */

    @Override
    public String getDisplayName() {
        NodeName qName = getNodeName();
        return qName == null ? "" : qName.getDisplayName();
    }

    /**
     * Get the local name of this node.
     *
     * @return The local name of this node.
     * For a node with no name, return "",.
     */

    @Override
    public String getLocalPart() {
        NodeName qName = getNodeName();
        return qName == null ? "" : qName.getLocalPart();
    }

    /**
     * Get the line number of the node within its source document entity
     */

    @Override
    public int getLineNumber() {
        return parent.getLineNumber();
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
     * Find the parent node of this node.
     *
     * @return The Node object describing the containing element or root node.
     */

    /*@Nullable*/
    @Override
    public final NodeImpl getParent() {
        if (parent instanceof DocumentImpl && ((DocumentImpl) parent).isImaginary()) {
            return null;
        }
        return parent;
    }

    /**
     * Get the raw value of the parent pointer. This will usually be the same as the parent node
     * in the XDM model, but in the case of a parentless element it will be a pointer to the "imaginary"
     * document node which is not properly part of the tree.
     *
     * @return either the real parent of this node, or the "imaginary" parent present in the tree
     * implementation to provide a root object for the tree
     */

    /*@Nullable*/
    protected final ParentNodeImpl getRawParent() {
        return parent;
    }

    /**
     * Set the raw parent pointer
     *
     * @param parent the "raw" parent pointer: either the real parent, or a dummy parent
     *               added to ensure that the tree is properly rooted.
     */

    protected final void setRawParent(/*@Nullable*/ ParentNodeImpl parent) {
        this.parent = parent;
    }

    /**
     * Get the previous sibling of the node
     *
     * @return The previous sibling node. Returns null if the current node is the first
     * child of its parent.
     */

    /*@Nullable*/
    @Override
    public NodeImpl getPreviousSibling() {
        if (parent == null) {
            return null;
        }
        return parent.getNthChild(index - 1);
    }


    /**
     * Get next sibling node
     *
     * @return The next sibling node of the required type. Returns null if the current node is the last
     * child of its parent.
     */

    /*@Nullable*/
    @Override
    public NodeImpl getNextSibling() {
        if (parent == null) {
            return null;
        }
        return parent.getNthChild(index + 1);
    }

    /**
     * Get first child - default implementation used for leaf nodes
     *
     * @return null
     */

    /*@Nullable*/
    @Override
    public NodeImpl getFirstChild() {
        return null;
    }

    /**
     * Get last child - default implementation used for leaf nodes
     *
     * @return null
     */

    /*@Nullable*/
    public NodeInfo getLastChild() {
        return null;
    }

    /**
     * Return an iterator over the nodes reached by the given axis from this node
     *
     * @param axisNumber The axis to be iterated over
     * @return an AxisIterator that scans the nodes reached by the axis in turn.
     */

    @Override
    public AxisIterator iterateAxis(int axisNumber) {
        // Fast path for child axis
        if (axisNumber == AxisInfo.CHILD) {
            if (this instanceof ParentNodeImpl) {
                return ((ParentNodeImpl) this).iterateChildren(null);
            } else {
                return EmptyIterator.ofNodes();
            }
        } else {
            return iterateAxis(axisNumber, AnyNodeTest.getInstance());
        }
    }

    /**
     * Return an enumeration over the nodes reached by the given axis from this node
     *
     * @param axisNumber The axis to be iterated over
     * @param nodeTest   A pattern to be matched by the returned nodes
     * @return an AxisIterator that scans the nodes reached by the axis in turn.
     */

    @Override
    public AxisIterator iterateAxis(int axisNumber, Predicate<? super NodeInfo> nodeTest) {

        switch (axisNumber) {
            case AxisInfo.ANCESTOR:
                return new AncestorEnumeration(this, nodeTest, false);

            case AxisInfo.ANCESTOR_OR_SELF:
                return new AncestorEnumeration(this, nodeTest, true);

            case AxisInfo.ATTRIBUTE:
                if (getNodeKind() != Type.ELEMENT) {
                    return EmptyIterator.ofNodes();
                } else {
                    return ((ElementImpl)this).iterateAttributes(nodeTest);
                }
            case AxisInfo.CHILD:
                if (this instanceof ParentNodeImpl) {
                    return ((ParentNodeImpl) this).iterateChildren(nodeTest);
                } else {
                    return EmptyIterator.ofNodes();
                }

            case AxisInfo.DESCENDANT:
                if (getNodeKind() == Type.DOCUMENT &&
                        nodeTest instanceof NameTest &&
                        ((NameTest)nodeTest).getPrimitiveType() == Type.ELEMENT) {
                    return ((DocumentImpl) this).getAllElements(((NameTest)nodeTest).getFingerprint());
                } else if (hasChildNodes()) {
                    return new SteppingNavigator.DescendantAxisIterator<>(this, false, nodeTest);
                } else {
                    return EmptyIterator.ofNodes();
                }

            case AxisInfo.DESCENDANT_OR_SELF:
                return new SteppingNavigator.DescendantAxisIterator<>(this, true, nodeTest);

            case AxisInfo.FOLLOWING:
                return new FollowingEnumeration(this, nodeTest);

            case AxisInfo.FOLLOWING_SIBLING:
                return new FollowingSiblingEnumeration(this, nodeTest);

            case AxisInfo.NAMESPACE:
                if (getNodeKind() != Type.ELEMENT) {
                    return EmptyIterator.ofNodes();
                }
                return NamespaceNode.makeIterator(this, nodeTest);

            case AxisInfo.PARENT:
                NodeInfo parent = getParent();
                if (parent == null) {
                    return EmptyIterator.ofNodes();
                }
                return Navigator.filteredSingleton(parent, nodeTest);

            case AxisInfo.PRECEDING:
                return new PrecedingEnumeration(this, nodeTest);

            case AxisInfo.PRECEDING_SIBLING:
                return new PrecedingSiblingEnumeration(this, nodeTest);

            case AxisInfo.SELF:
                return Navigator.filteredSingleton(this, nodeTest);

            case AxisInfo.PRECEDING_OR_ANCESTOR:
                return new PrecedingOrAncestorEnumeration(this, nodeTest);

            default:
                throw new IllegalArgumentException("Unknown axis number " + axisNumber);
        }
    }

    /**
     * Find the value of a given attribute of this node. <BR>
     * This method is defined on all nodes to meet XSL requirements, but for nodes
     * other than elements it will always return null.
     *
     * @param uri       the namespace uri of an attribute
     * @param localName the local name of an attribute
     * @return the value of the attribute, if it exists, otherwise null
     */

    /*@Nullable*/
    @Override
    public String getAttributeValue( /*@NotNull*/ String uri, /*@NotNull*/ String localName) {
        return null;
    }

    /**
     * Get the root node
     *
     * @return the NodeInfo representing the logical root of the tree. For this tree implementation the
     * root will either be a document node or an element node.
     */

    @Override
    public NodeInfo getRoot() {
        NodeInfo parent = getParent();
        if (parent == null) {
            return this;
        } else {
            return parent.getRoot();
        }
    }

    /**
     * Get the physical root of the tree. This may be an imaginary document node: this method
     * should be used only when control information held at the physical root is required
     *
     * @return the document node, which may be imaginary. In the case of a node that has been detached
     * from the tree by means of a delete() operation, this method returns null.
     */

    /*@Nullable*/
    public DocumentImpl getPhysicalRoot() {
        ParentNodeImpl up = parent;
        while (up != null && !(up instanceof DocumentImpl)) {
            up = up.getRawParent();
        }
        return (DocumentImpl) up;
    }

    /**
     * Get the next node in document order
     *
     * @param anchor the scan stops when it reaches a node that is not a descendant of the specified
     *               anchor node
     * @return the next node in the document, or null if there is no such node
     */

    /*@Nullable*/
    public NodeImpl getNextInDocument(NodeImpl anchor) {
        // find the first child node if there is one; otherwise the next sibling node
        // if there is one; otherwise the next sibling of the parent, grandparent, etc, up to the anchor element.
        // If this yields no result, return null.

        NodeImpl next = getFirstChild();
        if (next != null) {
            return next;
        }
        if (this == anchor) {
            return null;
        }
        next = getNextSibling();
        if (next != null) {
            return next;
        }
        NodeImpl parent = this;
        while (true) {
            parent = parent.getParent();
            if (parent == null) {
                return null;
            }
            if (parent == anchor) {
                return null;
            }
            next = parent.getNextSibling();
            if (next != null) {
                return next;
            }
        }
    }

    @Override
    public NodeImpl getSuccessorElement(NodeImpl anchor, String uri, String local) {
        NodeImpl next = getNextInDocument(anchor);
        while (next != null && !(next.getNodeKind() == Type.ELEMENT &&
                                         (uri == null || uri.equals(next.getURI())) &&
                                         (local == null || local.equals(next.getLocalPart())))) {
            next = next.getNextInDocument(anchor);
        }
        return next;
    }

    /**
     * Get the previous node in document order
     *
     * @return the previous node in the document, or null if there is no such node
     */

    /*@Nullable*/
    public NodeImpl getPreviousInDocument() {

        // finds the last child of the previous sibling if there is one;
        // otherwise the previous sibling element if there is one;
        // otherwise the parent, up to the anchor element.
        // If this reaches the document root, return null.

        NodeImpl prev = getPreviousSibling();
        if (prev != null) {
            return prev.getLastDescendantOrSelf();
        }
        return getParent();
    }

    /*@NotNull*/
    private NodeImpl getLastDescendantOrSelf() {
        NodeImpl last = (NodeImpl) getLastChild();
        if (last == null) {
            return this;
        }
        return last.getLastDescendantOrSelf();
    }

    /**
     * Get all namespace undeclarations and undeclarations defined on this element.
     *
     * @param buffer If this is non-null, and the result array fits in this buffer, then the result
     *               may overwrite the contents of this array, to avoid the cost of allocating a new array on the heap.
     * @return An array of integers representing the namespace declarations and undeclarations present on
     * this element. For a node other than an element, return null. Otherwise, the returned array is a
     * sequence of namespace codes, whose meaning may be interpreted by reference to the name pool. The
     * top half word of each namespace code represents the prefix, the bottom half represents the URI.
     * If the bottom half is zero, then this is a namespace undeclaration rather than a declaration.
     * The XML namespace is never included in the list. If the supplied array is larger than required,
     * then the first unused entry will be set to -1.
     * <p>For a node other than an element, the method returns null.</p>
     */

    /*@Nullable*/
    @Override
    public NamespaceBinding[] getDeclaredNamespaces(NamespaceBinding[] buffer) {
        return null;
    }

    /**
     * Get all the namespace bindings that are in-scope for this element.
     * <p>For an element return all the prefix-to-uri bindings that are in scope. This may include
     * a binding to the default namespace (represented by a prefix of ""). The map does NOT include
     * the implicit binding of the XML namespace. It will never include
     * "undeclarations" - that is, the namespace URI will never be empty; the effect of an undeclaration
     * is to remove a binding from the in-scope namespaces, not to add anything.</p>
     * <p>For a node other than an element, returns null.</p>
     */

    @Override
    public NamespaceMap getAllNamespaces() {
        return null;
    }

    /**
     * Determine whether the node has any children.
     *
     * @return <code>true</code> if the node has any children,
     * <code>false</code> if the node has no children.
     */

    @Override
    public boolean hasChildNodes() {
        return getFirstChild() != null;
    }

    /**
     * Set the type annotation on a node. This must only be called when the caller has verified (by validation)
     * that the node is a valid instance of the specified type. The call is ignored if the node is not an element
     * or attribute node.
     *
     * @param type the type annotation (possibly including high bits set to indicate the isID, isIDREF, and
     *             isNilled properties)
     */

    @Override
    public void setTypeAnnotation(SchemaType type) {
        // no action
    }

    /**
     * Delete this node (that is, detach it from its parent)
     */

    @Override
    public void delete() {
        // Overridden for attribute nodes
        if (parent != null) {
            parent.removeChild(this);
            DocumentImpl newRoot = new DocumentImpl();
            newRoot.setConfiguration(getConfiguration());
            newRoot.setImaginary(true);
            parent = newRoot;
        }
        index = -1;
    }

    /**
     * Test whether this MutableNodeInfo object represents a node that has been deleted.
     * Generally, such a node is unusable, and any attempt to use it will result in an exception
     * being thrown
     *
     * @return true if this node has been deleted
     */

    @Override
    public boolean isDeleted() {
        return index == -1 || (parent != null && parent.isDeleted());
    }

    /**
     * Set the attribute list for this (element) node
     *
     * @param attributes the new attribute list
     * @throws UnsupportedOperationException if this is not an element node
     */
    @Override
    public void setAttributes(AttributeMap attributes) {
        throw new UnsupportedOperationException("setAttributes() applies only to element nodes");
    }

    /**
     * Remove an attribute from this element node
     * <p>If this node is not an element, or if the specified node is not an attribute
     * of this element, this method takes no action.</p>
     * <p>The attribute object itself becomes unusable; any attempt to use this attribute object,
     * or any other object representing the same node, is likely to result in an exception.</p>
     *
     * @param attribute the attribute node to be removed
     */

    @Override
    public void removeAttribute(NodeInfo attribute) {
        // no action (overridden in subclasses)
    }

    /**
     * Add an attribute to this element node.
     * <p>If this node is not an element, or if the supplied node is not an attribute, the method
     * takes no action. If the element already has an attribute with this name, the method
     * throws an exception.</p>
     * <p>This method does not perform any namespace fixup. It is the caller's responsibility
     * to ensure that any namespace prefix used in the name of the attribute (or in its value
     * if it has a namespace-sensitive type) is declared on this element.</p>
     *
     * @param name       the name of the new attribute
     * @param attType    the type annotation of the new attribute
     * @param value      the string value of the new attribute
     * @param properties properties including IS_ID and IS_IDREF properties
     * @param inheritNamespaces
     * @throws IllegalStateException if the element already has an attribute with the given name.
     */

    @Override
    public void addAttribute(NodeName name, SimpleType attType, CharSequence value, int properties, boolean inheritNamespaces) {
        // No action, unless this is an element node
    }

    /**
     * Rename this node
     *
     * @param newNameCode the NamePool code of the new name
     * @param inheritNamespaces
     */

    @Override
    public void rename(NodeName newNameCode, boolean inheritNamespaces) {
        // implemented for node kinds that have a name
    }


    @Override
    public void addNamespace(NamespaceBinding nscode, boolean inheritNamespaces) {
        // implemented for element nodes only
    }

    /**
     * Replace this node with a given sequence of nodes. This node is effectively deleted, and the replacement
     * nodes are attached to the parent of this node in its place.
     * <p>The supplied nodes will become children of this node's parent. Adjacent text nodes will be merged, and
     * zero-length text nodes removed. The supplied nodes may be modified in situ, for example to change their
     * parent property and to add namespace bindings, or they may be copied, at the discretion of
     * the implementation.</p>
     *
     * @param replacement the replacement nodes. If this node is an attribute, the replacements
     *                    must also be attributes; if this node is not an attribute, the replacements must not be attributes.
     *                    source the nodes to be inserted. The implementation determines what implementation classes
     *                    of node it will accept; this implementation will accept attribute, text, comment, and processing instruction
     *                    nodes belonging to any implementation, but elements must be instances of {@link net.sf.saxon.tree.linked.ElementImpl}.
     *                    The supplied nodes will be modified in situ, for example
     *                    to change their parent property and to add namespace bindings, if they are instances of
     *                    {@link net.sf.saxon.tree.linked.ElementImpl}; otherwise they will be copied. If the nodes are copied, then on return
     *                    the supplied source array will contain the copy rather than the original.
     * @param inherit     true if the replacement nodes are to inherit the namespaces of their new parent; false
     *                    if such namespaces are to be undeclared
     * @throws IllegalArgumentException if any of the replacement nodes is of the wrong kind. When replacing
     *                                  a child node, the replacement nodes must all be elements, text, comment, or PI nodes; when replacing
     *                                  an attribute, the replacement nodes must all be attributes.
     * @throws IllegalStateException    if this node is deleted or if it has no parent node.
     *                                  or if two replacement attributes have the same name.
     */

    @Override
    public void replace(NodeInfo[] replacement, boolean inherit) {
        if (isDeleted()) {
            throw new IllegalStateException("Cannot replace a deleted node");
        }
        if (getParent() == null) {
            throw new IllegalStateException("Cannot replace a parentless node");
        }
        assert parent != null;
        parent.replaceChildrenAt(replacement, index, inherit);
        parent = null;
        index = -1; // mark the node as deleted
    }

    /**
     * Insert a sequence of nodes as children of this node.
     * <p>This method takes no action unless the target node is a document node or element node. It also
     * takes no action in respect of any supplied nodes that are not elements, text nodes, comments, or
     * processing instructions.</p>
     * <p>The supplied nodes will form the new children. Adjacent text nodes will be merged, and
     * zero-length text nodes removed. The supplied nodes may be modified in situ, for example to change their
     * parent property and to add namespace bindings, or they may be copied, at the discretion of
     * the implementation.</p>
     *
     * @param source  the nodes to be inserted. The implementation determines what implementation classes
     *                of node it will accept; all implementations must accept nodes constructed using the Builder supplied
     *                by the {@link #newBuilder} method on this object. The supplied nodes may be modified in situ, for example
     *                to change their parent property and to add namespace bindings, but this depends on the implementation.
     *                The argument array may be modified as a result of the call.
     * @param atStart true if the new nodes are to be inserted before existing children; false if they are
     *                to be inserted after existing children
     * @param inherit true if the inserted nodes are to inherit the namespaces of their new parent; false
     *                if such namespaces are to be undeclared
     * @throws IllegalArgumentException if the supplied nodes use a node implementation that this
     *                                  implementation does not accept.
     */

    @Override
    public void insertChildren(NodeInfo[] source, boolean atStart, boolean inherit) {
        // No action: node is not a document or element node
    }

    /**
     * Insert copies of a sequence of nodes as siblings of this node.
     * <p>This method takes no action unless the target node is an element, text node, comment, or
     * processing instruction, and one that has a parent node. It also
     * takes no action in respect of any supplied nodes that are not elements, text nodes, comments, or
     * processing instructions.</p>
     * <p>The supplied nodes must use the same data model implementation as the tree into which they
     * will be inserted.</p>
     *
     * @param source  the nodes to be inserted
     * @param before  true if the new nodes are to be inserted before the target node; false if they are
     * @param inherit true if the inserted nodes are to inherit the namespaces of their new parent; false
     *                if such namespaces are to be undeclared
     */

    @Override
    public void insertSiblings(NodeInfo[] source, boolean before, boolean inherit) {
        if (parent == null) {
            throw new IllegalStateException("Cannot add siblings if there is no parent");
        }
        parent.insertChildrenAt(source, before ? index : index + 1, inherit);
    }


    /**
     * Remove type information from this node (and its ancestors, recursively).
     * This method implements the upd:removeType() primitive defined in the XQuery Update specification
     */

    @Override
    public void removeTypeAnnotation() {
        // no action
    }

    /**
     * Get a Builder suitable for building nodes that can be attached to this document.
     *
     * @return a new Builder that constructs nodes using the same object model implementation
     * as this one, suitable for attachment to this tree
     */

    /*@NotNull*/
    @Override
    public Builder newBuilder() {
        return getPhysicalRoot().newBuilder();
    }

    /**
     * Get the effective boolean value of this sequence
     *
     * @return the effective boolean value
     * @throws XPathException if the sequence has no effective boolean value (for example a sequence of two integers)
     */
    @Override
    public boolean effectiveBooleanValue() throws XPathException {
        return true;
    }
}

