////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.event.Builder;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

/**
 * An extension of the NodeInfo interface defining a node that can be updated. The updating methods are
 * closely modelled on the updating primitives defined in the XQuery Update specification, however, their
 * semantics should not be assumed to be identical to those primitives.
 * <p>These primitives may leave the tree in a state where the type annotations of nodes do not correctly
 * describe the structure of the nodes. It is the caller's responsibility to maintain the integrity of
 * type annotations.</p>
 * <p>This interface was introduced in Saxon 9.1 and modified in Saxon 9.2.
 * Some aspects of the semantics are not clearly specified, for example the effect of each
 * operation on namespace bindings, and such details may change in the future. The primary purpose
 * of this interface is as an internal interface for use by XQuery Update; applications use it (or
 * implement it) at their own risk.</p>
 */

public interface MutableNodeInfo extends NodeInfo {

    /**
     * Set the type annotation on a node. This must only be called when the caller has verified (by validation)
     * that the node is a valid instance of the specified type. The call is ignored if the node is not an element
     * or attribute node.
     *
     * @param type the type annotation (possibly including high bits set to indicate the isID, isIDREF, and
     *             isNilled properties)
     */

    void setTypeAnnotation(SchemaType type);

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

    void insertChildren(NodeInfo[] source, boolean atStart, boolean inherit);

    /**
     * Insert a sequence of nodes as siblings of this node.
     * <p>This method takes no action unless the target node is an element, text node, comment, or
     * processing instruction, and one that has a parent node. It also
     * takes no action in respect of any supplied nodes that are not elements, text nodes, comments, or
     * processing instructions.</p>
     * <p>The supplied nodes will form new children of this node. Adjacent text nodes will be merged, and
     * zero-length text nodes removed. The supplied nodes may be modified in situ, for example to change their
     * parent property and to add namespace bindings, or they may be copied, at the discretion of
     * the implementation.</p>
     *
     * @param source  the nodes to be inserted. The implementation determines what implementation classes
     *                of node it will accept; all implementations must accept nodes constructed using the Builder supplied
     *                by the {@link #newBuilder} method on this object. The supplied nodes may be modified in situ, for example
     *                to change their parent property and to add namespace bindings, but this depends on the implementation.
     *                The argument array may be modified as a result of the call.
     * @param before  true if the new nodes are to be inserted before the target node; false if they are
     *                to be inserted after the target node
     * @param inherit true if the inserted nodes are to inherit the namespaces of their new parent; false
     *                if such namespaces are to be undeclared
     * @throws IllegalArgumentException if any supplied node is not a text node, element, comment, or
     *                                  processing instruction or if any of the supplied nodes uses a node implementation that this
     *                                  implementation does not accept.
     */

    void insertSiblings(NodeInfo[] source, boolean before, boolean inherit);

    /**
     * Set the attribute list for this (element) node
     *
     * @param attributes the new attribute list
     * @throws UnsupportedOperationException if this is not an element node
     */

    void setAttributes(AttributeMap attributes);

    /**
     * Remove an attribute from this element node
     * <p>If the target node is not an element, or if the supplied node is not an attribute
     * of this element, this method takes no action.</p>
     * <p>The attribute object itself becomes unusable; any attempt to use this attribute object,
     * or any other object representing the same node, is likely to result in an exception.
     * Calling the {@link #isDeleted} method on the attribute object will return true.</p>
     *
     * @param attribute the attribute node to be removed
     */

    void removeAttribute(NodeInfo attribute);

    /**
     * Add an attribute to this element node.
     * <p>If this node is not an element, the method takes no action.
     * If the element already has an attribute with this name, the method
     * throws an exception.</p>
     * <p>This method does not perform any namespace fixup. It is the caller's responsibility
     * to ensure that any namespace prefix used in the name of the attribute (or in its value
     * if it has a namespace-sensitive type) is declared on this element.</p>
     *
     * @param name       the name of the new attribute
     * @param attType    the type annotation of the new attribute
     * @param value      the string value of the new attribute
     * @param properties properties including IS_ID and IS_IDREF properties
     * @throws IllegalStateException if the element already has an attribute with the given name.
     */

    void addAttribute(NodeName name, SimpleType attType, CharSequence value, int properties);

    /**
     * Remove a namespace node from this node. The namespaces of its descendant nodes are unaffected.
     * The method has no effect on non-element nodes, nor on elements if there is no in-scope namespace
     * with the required prefix, nor if the element name or one of its attributes uses this namespace
     * prefix
     *
     * @param prefix the namespace prefix.
     */

    default void removeNamespace(String prefix) {
        // default: no action
    }

    /**
     * Add a namespace node from this node. The namespaces of its descendant nodes are unaffected.
     * The method has no effect on non-element nodes. If there is an existing namespace using this
     * prefix, the method throws an exception.
     *
     * @param prefix the namespace prefix.
     */

    default void addNamespace(String prefix, String uri) {
        // default: no action
    }

    /**
     * Delete this node (that is, detach it from its parent).
     * <p>Following this call, calling {@link #isDeleted} on the target node or on any of its
     * descendants (or their attributes) will return true, and the node will in all other respects
     * be unusable. The effect of other operations on the node is undefined.</p>
     * <p>If the deleted node has preceding and following siblings that are both text nodes,
     * the two text nodes will be joined into a single text node (the identity of this node
     * with respect to the original text nodes is undefined).</p>
     */

    void delete();

    /**
     * Test whether this MutableNodeInfo object represents a node that has been deleted.
     * Generally, such a node is unusable, and any attempt to use it will result in an exception
     * being thrown.
     *
     * @return true if this node has been deleted
     */

    boolean isDeleted();

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
     *                    The implementation determines what implementation classes
     *                    of node it will accept; all implementations must accept nodes constructed using the Builder supplied
     *                    by the {@link #newBuilder} method on this object. The supplied nodes may be modified in situ, for example
     *                    to change their parent property and to add namespace bindings, but this depends on the implementation.
     *                    The argument array may be modified as a result of the call.
     * @param inherit     true if the replacement nodes are to inherit the namespaces of their new parent; false
     *                    if such namespaces are to be undeclared
     * @throws IllegalArgumentException if any of the replacement nodes is of the wrong kind. When replacing
     *                                  a child node, the replacement nodes must all be elements, text, comment, or PI nodes; when replacing
     *                                  an attribute, the replacement nodes must all be attributes.
     * @throws IllegalStateException    if this node is deleted or if it has no parent node.
     * @throws IllegalStateException    if two replacement attributes have the same name.
     */

    void replace(NodeInfo[] replacement, boolean inherit);

    /**
     * Replace the string-value of this node. If applied to an element or document node, this
     * causes all existing children to be deleted, and replaced with a new text node
     * whose string value is the value supplied. The caller is responsible for checking
     * that the value is valid, for example that comments do not contain a double hyphen; the
     * implementation is not required to check for such conditions.
     *
     * @param stringValue the new string value
     */

    void replaceStringValue(CharSequence stringValue);

    /**
     * Rename this node.
     * <p>This call has no effect if applied to a nameless node, such as a text node or comment.</p>
     * <p>If necessary, a new namespace binding will be added to the target element, or to the element
     * parent of the target attribute</p>
     *
     * @param newName the new name for the node
     * @throws IllegalArgumentException if the new name code is not present in the name pool, or if
     *                                  it has a (prefix, uri) pair in which the
     *                                  prefix is the same as that of an existing in-scope namespace binding and the uri is different from that
     *                                  namespace binding.
     */

    void rename(NodeName newName);

    /**
     * Add a namespace binding (that is, a namespace node) to this element. This call has no effect if applied
     * to a node other than an element.
     *
     * @param nscode The namespace code representing the (prefix, uri) pair of the namespace binding to be
     *               added. If the target element already has a namespace binding with this (prefix, uri) pair, the call has
     *               no effect. If the target element currently has a namespace binding with this prefix and a different URI, an
     *               exception is raised. The new namespace binding will also be in scope for the subtree rooted
     *               at this node (that is, the namespace is implicitly inherited)
     * @throws IllegalArgumentException if the namespace code is not present in the namepool, or if the target
     *                                  element already has a namespace binding for this prefix
     */

    void addNamespace(NamespaceBinding nscode);

    /**
     * Remove type information from this node (and its ancestors, recursively).
     * This method implements the upd:removeType() primitive defined in the XQuery Update specification.
     * (Note: the caller is responsible for updating the set of nodes marked for revalidation)
     */

    void removeTypeAnnotation();

    /**
     * Get a Builder suitable for building nodes that can be attached to this document.
     *
     * @return a new Builder that constructs nodes using the same object model implementation
     * as this one, suitable for attachment to this tree
     */

    Builder newBuilder();

}

