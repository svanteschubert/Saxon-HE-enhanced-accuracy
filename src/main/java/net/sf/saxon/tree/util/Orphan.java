////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.util;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.SingleNodeIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.UntypedAtomicValue;

import java.util.function.Predicate;

/**
 * A node (implementing the NodeInfo interface) representing an attribute, text node,
 * comment, processing instruction, or namespace that has no parent (and of course no children).
 * Exceptionally it is also used (during whitespace stripping) to represent a standalone element.
 * <p>In general this class does not impose constraints defined in the data model: that is the responsibility
 * of the client. For example, the class does not prevent you from creating a comment or text node that has
 * a name or a non-trivial type annotation.</p>
 *
 * @author Michael H. Kay
 */

public final class Orphan implements MutableNodeInfo {

    private short kind;
    /*@Nullable*/ private NodeName nodeName = null;
    private CharSequence stringValue;
    private SchemaType typeAnnotation = null;
    private int options = ReceiverOption.NONE;
    private GenericTreeInfo treeInfo;

    /**
     * Create an Orphan node
     *
     * @param config the Saxon configuration
     */

    public Orphan(Configuration config) {
        treeInfo = new GenericTreeInfo(config);
        treeInfo.setRootNode(this);
    }

    /**
     * Get information about the tree to which this NodeInfo belongs
     *
     * @return the TreeInfo
     * @since 9.7
     */
    @Override
    public TreeInfo getTreeInfo() {
        return treeInfo;
    }

    /**
     * Get the System ID for the node. Note this is not the
     * same as the base URI: the base URI can be modified by xml:base, but
     * the system ID cannot. The base URI is used primarily for resolving
     * relative URIs within the content of the document. The system ID is
     * used primarily in conjunction with a line number, for identifying the
     * location of elements within the source XML, in particular when errors
     * are found. For a document node, the System ID represents the value of
     * the document-uri property as defined in the XDM data model.
     *
     * @return the System Identifier of the entity in the source document
     * containing the node, or null if not known or not applicable.
     * @since 8.4
     */
    @Override
    public String getSystemId() {
        return treeInfo.getSystemId();
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
        return treeInfo.getPublicId();
    }

    /**
     * Set the system identifier for this Source.
     * <p>
     * <p>The system identifier is optional if the source does not
     * get its data from a URL, but it may still be useful to provide one.
     * The application can use a system identifier, for example, to resolve
     * relative URIs and to include in error messages and warnings.</p>
     *
     * @param systemId The system identifier as a URL string.
     */
    @Override
    public void setSystemId(String systemId) {
        treeInfo.setSystemId(systemId);
    }

    /**
     * Get the effective boolean value of this sequence
     * @return the effective boolean value (always true for an Orphan node)
     */
    @Override
    public boolean effectiveBooleanValue() {
        return true;
    }

    /**
     * Set the node kind
     *
     * @param kind the kind of node, for example {@link Type#ELEMENT} or {@link Type#ATTRIBUTE}
     */

    public void setNodeKind(short kind) {
        this.kind = kind;
    }

    /**
     * Set the name of the node
     *
     * @param nodeName the name of the node. May be null for unnamed nodes such as text and comment nodes
     */

    public void setNodeName(/*@Nullable*/ NodeName nodeName) {
        this.nodeName = nodeName;
    }

    /**
     * Set the string value of the node
     *
     * @param stringValue the string value of the node
     */

    public void setStringValue(CharSequence stringValue) {
        this.stringValue = stringValue;
    }

    /**
     * Set the type annotation of the node
     *
     * @param typeAnnotation the type annotation
     */

    @Override
    public void setTypeAnnotation(SchemaType typeAnnotation) {
        this.typeAnnotation = typeAnnotation;
    }

    /**
     * Set the isId property
     *
     * @param id the isId property
     */

    public void setIsId(boolean id) {
        setOption(ReceiverOption.IS_ID, id);
    }

    private void setOption(int option, boolean on) {
        if (on) {
            options |= option;
        } else {
            options &= ~option;
        }
    }

    private boolean isOption(int option) {
        return ReceiverOption.contains(options, option);
    }

    /**
     * Set the isIdref property
     *
     * @param idref the isIdref property
     */

    public void setIsIdref(boolean idref) {
        setOption(ReceiverOption.IS_IDREF, idref);
    }

    /**
     * Set the disable-output-escaping property
     * @param doe true if the property is to be set
     */

    public void setDisableOutputEscaping(boolean doe) {
        setOption(ReceiverOption.DISABLE_ESCAPING, doe);
    }

    /**
     * Return the kind of node.
     *
     * @return one of the values Type.ELEMENT, Type.TEXT, Type.ATTRIBUTE, etc.
     */

    @Override
    public int getNodeKind() {
        return kind;
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
     * Get the typed value.
     * @return the typed value.
     * @since 8.5
     */

    @Override
    public AtomicSequence atomize() throws XPathException {
        switch (getNodeKind()) {
            case Type.COMMENT:
            case Type.PROCESSING_INSTRUCTION:
                return new StringValue(stringValue);
            case Type.TEXT:
            case Type.DOCUMENT:
            case Type.NAMESPACE:
                return new UntypedAtomicValue(stringValue);
            default:
                if (typeAnnotation == null || typeAnnotation == Untyped.getInstance() ||
                        typeAnnotation == BuiltInAtomicType.UNTYPED_ATOMIC) {
                    return new UntypedAtomicValue(stringValue);
                } else {
                    return typeAnnotation.atomize(this);
                }
        }
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
        if (typeAnnotation == null) {
            if (kind == Type.ELEMENT) {
                return Untyped.getInstance();
            } else if (kind == Type.ATTRIBUTE) {
                return BuiltInAtomicType.UNTYPED_ATOMIC;
            }
        }
        return typeAnnotation;
    }

    /**
     * Determine whether this is the same node as another node.
     * <p>Note: a.equals(b) if and only if generateId(a)==generateId(b)</p>
     *
     * @return true if this Node object and the supplied Node object represent the
     *         same node in the tree.
     */

    public boolean equals(NodeInfo other) {
        return this == other;
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
        return super.hashCode();
    }

    /**
     * Get the Base URI for the node, that is, the URI used for resolving a relative URI contained
     * in the node. This will be the same as the System ID unless xml:base has been used.
     */

    /*@Nullable*/
    @Override
    public String getBaseURI() {
        if (kind == Type.PROCESSING_INSTRUCTION) {
            return getSystemId();
        } else {
            return null;
        }
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

        // are they the same node?
        if (this.equals(other)) {
            return 0;
        }
        return this.hashCode() < other.hashCode() ? -1 : +1;
    }

    /**
     * Return the string value of the node.
     *
     * @return the string value of the node
     */

    @Override
    public String getStringValue() {
        return stringValue.toString();
    }

    /**
     * Get the value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String.
     */

    @Override
    public CharSequence getStringValueCS() {
        return stringValue;
    }

    /**
     * Get the local part of the name of this node. This is the name after the ":" if any.
     *
     * @return the local part of the name. For an unnamed node, returns "".
     */

    @Override
    public String getLocalPart() {
        if (nodeName == null) {
            return "";
        } else {
            return nodeName.getLocalPart();
        }
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
        if (nodeName == null) {
            return "";
        } else {
            return nodeName.getURI();
        }
    }

    /**
     * Get the prefix of the name of the node. This is defined only for elements and attributes.
     * If the node has no prefix, or for other kinds of node, return a zero-length string.
     *
     * @return The prefix of the name of the node.
     */

    @Override
    public String getPrefix() {
        if (nodeName == null) {
            return "";
        } else {
            return nodeName.getPrefix();
        }
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
        if (nodeName == null) {
            return "";
        } else {
            return nodeName.getDisplayName();
        }
    }

    /**
     * Get the NodeInfo object representing the parent of this node
     *
     * @return null - an Orphan has no parent.
     */

    /*@Nullable*/
    @Override
    public NodeInfo getParent() {
        return null;
    }

    /**
     * Return an iteration over the nodes reached by the given axis from this node
     *
     * @param axisNumber the axis to be searched, e.g. Axis.CHILD or Axis.ANCESTOR
     * @return a SequenceIterator that scans the nodes reached by the axis in turn.
     */

    /*@NotNull*/
    @Override
    public AxisIterator iterateAxis(int axisNumber) {
        switch (axisNumber) {
            case AxisInfo.ANCESTOR_OR_SELF:
            case AxisInfo.DESCENDANT_OR_SELF:
            case AxisInfo.SELF:
                return SingleNodeIterator.makeIterator(this);
            case AxisInfo.ANCESTOR:
            case AxisInfo.ATTRIBUTE:
            case AxisInfo.CHILD:
            case AxisInfo.DESCENDANT:
            case AxisInfo.FOLLOWING:
            case AxisInfo.FOLLOWING_SIBLING:
            case AxisInfo.NAMESPACE:
            case AxisInfo.PARENT:
            case AxisInfo.PRECEDING:
            case AxisInfo.PRECEDING_SIBLING:
            case AxisInfo.PRECEDING_OR_ANCESTOR:
                return EmptyIterator.ofNodes();
            default:
                throw new IllegalArgumentException("Unknown axis number " + axisNumber);
        }
    }


    /**
     * Return an iteration over the nodes reached by the given axis from this node
     *
     * @param axisNumber the axis to be searched, e.g. Axis.CHILD or Axis.ANCESTOR
     * @param nodeTest   A pattern to be matched by the returned nodes
     * @return a SequenceIterator that scans the nodes reached by the axis in turn.
     */

    /*@NotNull*/
    @Override
    public AxisIterator iterateAxis(int axisNumber, Predicate<? super NodeInfo> nodeTest) {
        switch (axisNumber) {
            case AxisInfo.ANCESTOR_OR_SELF:
            case AxisInfo.DESCENDANT_OR_SELF:
            case AxisInfo.SELF:
                return Navigator.filteredSingleton(this, nodeTest);
            case AxisInfo.ANCESTOR:
            case AxisInfo.ATTRIBUTE:
            case AxisInfo.CHILD:
            case AxisInfo.DESCENDANT:
            case AxisInfo.FOLLOWING:
            case AxisInfo.FOLLOWING_SIBLING:
            case AxisInfo.NAMESPACE:
            case AxisInfo.PARENT:
            case AxisInfo.PRECEDING:
            case AxisInfo.PRECEDING_SIBLING:
            case AxisInfo.PRECEDING_OR_ANCESTOR:
                return EmptyIterator.ofNodes();
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
     * Get the root node of this tree (not necessarily a document node).
     * Always returns this node in the case of an Orphan node.
     */

    /*@NotNull*/
    @Override
    public NodeInfo getRoot() {
        return this;
    }

    /**
     * Determine whether the node has any children.
     *
     * @return false - an orphan node never has any children
     */

    @Override
    public boolean hasChildNodes() {
        return false;
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
    public void generateId(/*@NotNull*/ FastStringBuffer buffer) {
        buffer.cat('Q');
        buffer.append(Integer.toString(hashCode()));
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
     * Ask whether this node has the is-id property
     *
     * @return true if the node is an ID
     */

    @Override
    public boolean isId() {
        return isOption(ReceiverOption.IS_ID) || (kind == Type.ATTRIBUTE && nodeName.equals(StandardNames.XML_ID_NAME));
    }

    /**
     * Ask whether this node has the is-idref property
     *
     * @return true if the node is an IDREF or IDREFS element or attribute
     */

    @Override
    public boolean isIdref() {
        return isOption(ReceiverOption.IS_IDREF);
    }

    /**
     * Ask whether the node has the disable-output-escaping property
     * @return true if the node has the disable-output-escaping property
     */

    public boolean isDisableOutputEscaping() {
        return isOption(ReceiverOption.DISABLE_ESCAPING);
    }

    /**
     * Insert copies of a sequence of nodes as children of this node.
     * <p>This method takes no action unless the target node is a document node or element node. It also
     * takes no action in respect of any supplied nodes that are not elements, text nodes, comments, or
     * processing instructions.</p>
     * <p>The supplied nodes will be copied to form the new children. Adjacent text nodes will be merged, and
     * zero-length text nodes removed.</p>
     *
     * @param source  the nodes to be inserted
     * @param atStart true if the new nodes are to be inserted before existing children; false if they are
     * @param inherit true if the insert nodes are to inherit the namespaces of their new parent; false
     *                if such namespaces are to be undeclared
     */

    @Override
    public void insertChildren(NodeInfo[] source, boolean atStart, boolean inherit) {
        // no action: node is not a document or element node
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
     * @param inherit true if the insert nodes are to inherit the namespaces of their new parent; false
     *                if such namespaces are to be undeclared
     */

    @Override
    public void insertSiblings(NodeInfo[] source, boolean before, boolean inherit) {
        // no action: node has no parent
    }

    /**
     * Set the attribute list for this (element) node
     *
     * @param attributes the new attribute list
     * @throws UnsupportedOperationException if this is not an element node
     */
    @Override
    public void setAttributes(AttributeMap attributes) {
        throw new UnsupportedOperationException();
    }

    /**
     * Remove an attribute from this element node
     * <p>If this node is not an element, or if it has no attribute with the specified name,
     * this method takes no action.</p>
     * <p>The attribute node itself is not modified in any way.</p>
     *
     * @param attribute the attribute node to be removed
     */

    @Override
    public void removeAttribute(NodeInfo attribute) {
        // no action: node is not an element
    }

    /**
     * Add an attribute to this element node.
     * <p>If this node is not an element, or if the supplied node is not an attribute, the method
     * takes no action. If the element already has an attribute with this name, the existing attribute
     * is replaced.</p>
     * @param nameCode   the name of the new attribute
     * @param attType    the type annotation of the new attribute
     * @param value      the string value of the new attribute
     * @param properties properties including IS_ID and IS_IDREF properties
     */

    @Override
    public void addAttribute(NodeName nameCode, SimpleType attType, CharSequence value, int properties) {
        // no action: node is not an element
    }

    /**
     * Delete this node (that is, detach it from its parent).
     * <p>If this node has preceding and following siblings that are both text nodes,
     * the two text nodes will be joined into a single text node (the identity of this node
     * with respect to its predecessors is undefined).</p>
     */

    @Override
    public void delete() {
        // no action other than to mark it deleted: node has no parent from which it can be detached
        kind = -1;
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
        return kind == -1;
    }

    /**
     * Replace this node with a given sequence of nodes
     *
     * @param replacement the replacement nodes
     * @param inherit     true if the replacement nodes are to inherit the namespaces of their new parent; false
     *                    if such namespaces are to be undeclared
     * @throws IllegalArgumentException if any of the replacement nodes is of the wrong kind. When replacing
     *                                  a child node, the replacement nodes must all be elements, text, comment, or PI nodes; when replacing
     *                                  an attribute, the replacement nodes must all be attributes.
     * @throws IllegalStateException    if this node is deleted or if it has no parent node.
     */

    @Override
    public void replace(NodeInfo[] replacement, boolean inherit) {
        throw new IllegalStateException("Cannot replace a parentless node");
    }

    /**
     * Replace the string-value of this node. If applied to an element or document node, this
     * causes all existing children to be deleted, and replaced with a new text node
     * whose string value is the value supplied. The caller is responsible for checking
     * that the value is valid, for example that comments do not contain a double hyphen; the
     * implementation is not required to check for such conditions.
     *
     * @param stringValue the new string value
     */

    @Override
    public void replaceStringValue(CharSequence stringValue) {
        this.stringValue = stringValue;
    }

    /**
     * Rename this node.
     * <p>This call has no effect if applied to a nameless node, such as a text node or comment.</p>
     * <p>If necessary, a new namespace binding will be added to the target element, or to the element
     * parent of the target attribute</p>
     *
     * @param newNameCode the namecode of the new name in the name pool
     * @throws IllegalArgumentException if the new name code is not present in the name pool, or if
     *                                  it has a (prefix, uri) pair in which the
     *                                  prefix is the same as that of an existing in-scope namespace binding and the uri is different from that
     *                                  namespace binding.
     */

    @Override
    public void rename(NodeName newNameCode) {
        if (kind == Type.ATTRIBUTE || kind == Type.PROCESSING_INSTRUCTION) {
            nodeName = newNameCode;
        }
    }

    /**
     * Add a namespace binding (that is, a namespace node) to this element. This call has no effect if applied
     * to a node other than an element.
     *
     * @param nscode  The namespace code representing the (prefix, uri) pair of the namespace binding to be
     *                added. If the target element already has a namespace binding with this (prefix, uri) pair, the call has
     *                no effect. If the target element currently has a namespace binding with this prefix and a different URI, an
     *                exception is raised.
     * @throws IllegalArgumentException if the namespace code is not present in the namepool, or if the target
     *                                  element already has a namespace binding for this prefix
     */

    @Override
    public void addNamespace(NamespaceBinding nscode) {
        // no action: node is not an element
    }

    /**
     * Remove type information from this node (and its ancestors, recursively).
     * This method implements the upd:removeType() primitive defined in the XQuery Update specification.
     * (Note: the caller is responsible for updating the set of nodes marked for revalidation)
     */

    @Override
    public void removeTypeAnnotation() {
        typeAnnotation = BuiltInAtomicType.UNTYPED_ATOMIC;
    }

    /**
     * Get a Builder suitable for building nodes that can be attached to this document.
     * This implementation always throws an exception: the method should only be called on a document or element
     * node when creating new children.
     */

    /*@NotNull*/
    @Override
    public Builder newBuilder() {
        throw new UnsupportedOperationException("Cannot create children for an Orphan node");
    }
}

