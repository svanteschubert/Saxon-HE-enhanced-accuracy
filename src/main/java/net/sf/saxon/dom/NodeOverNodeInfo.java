////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.functions.DeepEqual;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.type.Type;
import org.w3c.dom.*;

import java.util.ArrayList;
import java.util.List;


/**
 * This class implements the DOM Node interface as a wrapper around a Saxon NodeInfo object.
 * <p>The class provides read-only access to the tree; methods that request updates all fail
 * with an UnsupportedOperationException.</p>
 */

public abstract class NodeOverNodeInfo implements Node {

    protected NodeInfo node;

    /**
     * Get the Saxon NodeInfo object representing this node
     *
     * @return the Saxon NodeInfo object
     */

    /*@Nullable*/
    public NodeInfo getUnderlyingNodeInfo() {
        return node;
    }

    /**
     * Factory method to construct a DOM node that wraps an underlying Saxon NodeInfo
     *
     * @param node the Saxon NodeInfo object
     * @return the DOM wrapper node
     */

    public static NodeOverNodeInfo wrap(NodeInfo node) {
        NodeOverNodeInfo n;
        if (node == null) {
            return null;
        }
        switch (node.getNodeKind()) {
            case Type.DOCUMENT:
                n = new DocumentOverNodeInfo();
                break;
            case Type.ELEMENT:
                n = new ElementOverNodeInfo();
                break;
            case Type.ATTRIBUTE:
                n = new AttrOverNodeInfo();
                break;
            case Type.TEXT:
            case Type.COMMENT:
                n = new TextOverNodeInfo();
                break;
            case Type.PROCESSING_INSTRUCTION:
                n = new PIOverNodeInfo();
                break;
            case Type.NAMESPACE:
                n = new AttrOverNodeInfo();
                break;
            default:
                return null;
        }
        n.node = node;
        return n;
    }


    /**
     * Determine whether this is the same node as another node. DOM Level 3 method.
     *
     * @return true if this Node object and the supplied Node object represent the
     *         same node in the tree.
     */

    @Override
    public final boolean isSameNode(Node other) {
        return other instanceof NodeOverNodeInfo &&
                node.equals(((NodeOverNodeInfo) other).node);
    }

    /**
     * The equals() method returns true for two Node objects that represent the same
     * conceptual DOM Node. This is a concession to the Xalan IdentityTransformer, which relies
     * on equals() for DOM Nodes having this behaviour, even though it is not defined in the
     * specification
     *
     * @param obj the object to be compared
     * @return if this node and obj represent the same conceptual DOM node. That is, return
     *         true if isSameNode((Node)obj) returns true
     */

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Node && isSameNode((Node) obj);
    }

    /**
     * Return a hashCode
     *
     * @return a hashCode such that two wrappers over the same underlying node have the same hashCode.
     */

    @Override
    public int hashCode() {
        return node.hashCode();
    }

    /**
     * Get the base URI for the node. Default implementation for child nodes gets
     * the base URI of the parent node.
     */

    @Override
    public String getBaseURI() {
        return node.getBaseURI();
    }

    /**
     * Get the name of this node, following the DOM rules
     *
     * @return The name of the node. For an element this is the element name, for an attribute
     *         it is the attribute name, as a lexical QName. Other node types return conventional names such
     *         as "#text" or "#comment"
     */

    @Override
    public String getNodeName() {
        switch (node.getNodeKind()) {
            case Type.DOCUMENT:
                return "#document";
            case Type.ELEMENT:
                return node.getDisplayName();
            case Type.ATTRIBUTE:
                return node.getDisplayName();
            case Type.TEXT:
                return "#text";
            case Type.COMMENT:
                return "#comment";
            case Type.PROCESSING_INSTRUCTION:
                return node.getLocalPart();
            case Type.NAMESPACE:
                if (node.getLocalPart().isEmpty()) {
                    return "xmlns";
                } else {
                    return "xmlns:" + node.getLocalPart();
                }
            default:
                return "#unknown";
        }
    }

    /**
     * Get the local name of this node, following the DOM rules
     *
     * @return The local name of the node. For an element this is the local part of the element name,
     *         for an attribute it is the local part of the attribute name. Other node types return null.
     */

    @Override
    public String getLocalName() {
        switch (node.getNodeKind()) {
            case Type.ELEMENT:
            case Type.ATTRIBUTE:
                return node.getLocalPart();
            case Type.DOCUMENT:
            case Type.TEXT:
            case Type.COMMENT:
            case Type.PROCESSING_INSTRUCTION:
                return null;
            case Type.NAMESPACE:
                if (node.getLocalPart().isEmpty()) {
                    return "xmlns";
                } else {
                    return node.getLocalPart();
                }
            default:
                return null;
        }
    }


    /**
     * Determine whether the node has any children.
     *
     * @return <code>true</code> if this node has any attributes,
     *         <code>false</code> otherwise.
     */

    @Override
    public boolean hasChildNodes() {
        return node.hasChildNodes();
    }

    /**
     * Returns whether this node has any attributes. We treat the declaration of the XML namespace
     * as being present on every element, and since namespace declarations are treated as attributes,
     * every element has at least one attribute. This method therefore returns true.
     *
     * @return <code>true</code> if this node has any attributes,
     *         <code>false</code> otherwise.
     * @since DOM Level 2
     */

    @Override
    public boolean hasAttributes() {
        return true;
    }

    /**
     * Get the type of this node (node kind, in XPath terminology).
     * Note, the numbers assigned to node kinds
     * in Saxon (see {@link Type}) are the same as those assigned in the DOM
     */

    @Override
    public short getNodeType() {
        short kind = (short) node.getNodeKind();
        if (kind == Type.NAMESPACE) {
            return Type.ATTRIBUTE;
        } else {
            return kind;
        }
    }

    /**
     * Find the parent node of this node.
     *
     * @return The Node object describing the containing element or root node.
     */

    @Override
    public Node getParentNode() {
        return wrap(node.getParent());
    }

    /**
     * Get the previous sibling of the node
     *
     * @return The previous sibling node. Returns null if the current node is the first
     *         child of its parent.
     */

    @Override
    public Node getPreviousSibling() {
        return wrap(node.iterateAxis(AxisInfo.PRECEDING_SIBLING).next());
    }

    /**
     * Get next sibling node
     *
     * @return The next sibling node. Returns null if the current node is the last
     *         child of its parent.
     */

    @Override
    public Node getNextSibling() {
        return wrap(node.iterateAxis(AxisInfo.FOLLOWING_SIBLING).next());
    }

    /**
     * Get first child
     *
     * @return the first child node of this node, or null if it has no children
     */

    @Override
    public Node getFirstChild() {
        return wrap(node.iterateAxis(AxisInfo.CHILD).next());
    }

    /**
     * Get last child
     *
     * @return last child of this node, or null if it has no children
     */

    @Override
    public Node getLastChild() {
        AxisIterator children = node.iterateAxis(AxisInfo.CHILD);
        NodeInfo last = null;
        while (true) {
            NodeInfo next = children.next();
            if (next == null) {
                return wrap(last);
            } else {
                last = next;
            }
        }
    }

    /**
     * Get the node value (as defined in the DOM).
     * This is not generally the same as the XPath string-value: in particular, the
     * node value of an element node is null.
     */

    @Override
    public String getNodeValue() {
        switch (node.getNodeKind()) {
            case Type.DOCUMENT:
            case Type.ELEMENT:
                return null;
            case Type.ATTRIBUTE:
            case Type.TEXT:
            case Type.COMMENT:
            case Type.PROCESSING_INSTRUCTION:
            case Type.NAMESPACE:
                return node.getStringValue();
            default:
                return null;
        }
    }

    /**
     * Set the node value. Always fails
     */

    @Override
    public void setNodeValue(String nodeValue) throws DOMException {
        disallowUpdate();
    }

    /**
     * Return a <code>NodeList</code> that contains all children of this node. If
     * there are no children, this is a <code>NodeList</code> containing no
     * nodes.
     */

    @Override
    public NodeList getChildNodes() {
        List<Node> nodes = new ArrayList<>(10);
        for (NodeInfo child : node.children()) {
            nodes.add(NodeOverNodeInfo.wrap(child));
        }
        return new DOMNodeList(nodes);
    }

    /**
     * Return a <code>NamedNodeMap</code> containing the attributes of this node (if
     * it is an <code>Element</code>) or <code>null</code> otherwise.
     */

    @Override
    public NamedNodeMap getAttributes() {
        return null;
    }

    /**
     * Return the <code>Document</code> object associated with this node.
     */

    @Override
    public Document getOwnerDocument() {
        return (Document) wrap(node.getRoot());
    }

    /**
     * Insert the node <code>newChild</code> before the existing child node
     * <code>refChild</code>. Always fails.
     *
     * @param newChild The node to insert.
     * @param refChild The reference node, i.e., the node before which the
     *                 new node must be inserted.
     * @return The node being inserted.
     * @throws org.w3c.dom.DOMException NO_MODIFICATION_ALLOWED_ERR: Always raised.
     */

    @Override
    public Node insertBefore(Node newChild,
                             Node refChild)
            throws DOMException {
        disallowUpdate();
        return null;
    }

    /**
     * Replace the child node <code>oldChild</code> with
     * <code>newChild</code> in the list of children, and returns the
     * <code>oldChild</code> node. Always fails.
     *
     * @param newChild The new node to put in the child list.
     * @param oldChild The node being replaced in the list.
     * @return The node replaced.
     * @throws org.w3c.dom.DOMException NO_MODIFICATION_ALLOWED_ERR: Always raised.
     */

    @Override
    public Node replaceChild(Node newChild,
                             Node oldChild)
            throws DOMException {
        disallowUpdate();
        return null;
    }

    /**
     * Remove the child node indicated by <code>oldChild</code> from the
     * list of children, and returns it. Always fails.
     *
     * @param oldChild The node being removed.
     * @return The node removed.
     * @throws org.w3c.dom.DOMException NO_MODIFICATION_ALLOWED_ERR: Always raised.
     */

    @Override
    public Node removeChild(Node oldChild) throws DOMException {
        disallowUpdate();
        return null;
    }

    /**
     * Adds the node <code>newChild</code> to the end of the list of children
     * of this node. Always fails.
     *
     * @param newChild The node to add.
     * @return The node added.
     * @throws org.w3c.dom.DOMException <br> NO_MODIFICATION_ALLOWED_ERR: Always raised.
     */

    @Override
    public Node appendChild(Node newChild) throws DOMException {
        disallowUpdate();
        return null;
    }

    /**
     * Returns a duplicate of this node, i.e., serves as a generic copy
     * constructor for nodes. Always fails.
     *
     * @param deep If <code>true</code> , recursively clone the subtree under
     *             the specified node; if <code>false</code> , clone only the node
     *             itself (and its attributes, if it is an <code>Element</code> ).
     * @return The duplicate node.
     */

    @Override
    public Node cloneNode(boolean deep) {
        disallowUpdate();
        return null;
    }

    /**
     * Puts all <code>Text</code> nodes in the full depth of the sub-tree
     * underneath this <code>Node</code>, including attribute nodes, into a
     * "normal" form where only structure (e.g., elements, comments,
     * processing instructions, CDATA sections, and entity references)
     * separates <code>Text</code> nodes, i.e., there are neither adjacent
     * <code>Text</code> nodes nor empty <code>Text</code> nodes.
     *
     * @since DOM Level 2
     */

    @Override
    public void normalize() {
        // null operation; nodes are always normalized
    }

    /**
     * Tests whether the DOM implementation implements a specific feature and
     * that feature is supported by this node.
     *
     * @param feature The name of the feature to test. This is the same name
     *                which can be passed to the method <code>hasFeature</code> on
     *                <code>DOMImplementation</code> .
     * @param version This is the version number of the feature to test. In
     *                Level 2, version 1, this is the string "2.0". If the version is not
     *                specified, supporting any version of the feature will cause the
     *                method to return <code>true</code> .
     * @return Returns <code>true</code> if the specified feature is supported
     *         on this node, <code>false</code> otherwise.
     * @since DOM Level 2
     */

    @Override
    public boolean isSupported(String feature,
                               String version) {
        return (feature.equalsIgnoreCase("XML") || feature.equalsIgnoreCase("Core")) &&
                (version == null || version.isEmpty() ||
                    version.equals("3.0") || version.equals("2.0") || version.equals("1.0"));
    }

    /**
     * The namespace URI of this node, or <code>null</code> if it is
     * unspecified.
     * <br> This is not a computed value that is the result of a namespace
     * lookup based on an examination of the namespace declarations in scope.
     * It is merely the namespace URI given at creation time.
     * <br> For nodes of any type other than <code>ELEMENT_NODE</code> and
     * <code>ATTRIBUTE_NODE</code> and nodes created with a DOM Level 1
     * method, such as <code>createElement</code> from the
     * <code>Document</code> interface, this is always <code>null</code> .
     * Per the  Namespaces in XML Specification  an attribute does not
     * inherit its namespace from the element it is attached to. If an
     * attribute is not explicitly given a namespace, it simply has no
     * namespace.
     *
     * @since DOM Level 2
     */

    @Override
    public String getNamespaceURI() {
        if (node.getNodeKind() == Type.NAMESPACE) {
            return NamespaceConstant.XMLNS;
        }
        String uri = node.getURI();
        return "".equals(uri) ? null : uri;
    }

    /**
     * The namespace prefix of this node, or <code>null</code> if it is
     * unspecified.
     * <br>For nodes of any type other than <code>ELEMENT_NODE</code> and
     * <code>ATTRIBUTE_NODE</code> and nodes created with a DOM Level 1
     * method, such as <code>createElement</code> from the
     * <code>Document</code> interface, this is always <code>null</code>.
     *
     * @since DOM Level 2
     */

    @Override
    public String getPrefix() {
        if (node.getNodeKind() == Type.NAMESPACE) {
            if (node.getLocalPart().isEmpty()) {
                return null;
            } else {
                return "xmlns";
            }
        }
        String p = node.getPrefix();
        return "".equals(p) ? null : p;
    }

    /**
     * Set the namespace prefix of this node. Always fails.
     */

    @Override
    public void setPrefix(String prefix)
            throws DOMException {
        disallowUpdate();
    }

    /**
     * Compare the position of the (other) node in document order with the reference node (this node).
     * DOM Level 3 method.
     *
     * @param other the other node.
     * @return Returns how the node is positioned relatively to the reference
     *         node.
     * @throws org.w3c.dom.DOMException if an error occurs
     */

    @Override
    public short compareDocumentPosition(Node other) throws DOMException {
        final short DOCUMENT_POSITION_DISCONNECTED = 0x01;
        final short DOCUMENT_POSITION_PRECEDING = 0x02;
        final short DOCUMENT_POSITION_FOLLOWING = 0x04;
        final short DOCUMENT_POSITION_CONTAINS = 0x08;
        final short DOCUMENT_POSITION_CONTAINED_BY = 0x10;
        if (!(other instanceof NodeOverNodeInfo)) {
            return DOCUMENT_POSITION_DISCONNECTED;
        }
        int c = node.compareOrder(((NodeOverNodeInfo) other).node);
        if (c == 0) {
            return (short) 0;
        } else if (c == -1) {
            short result = DOCUMENT_POSITION_FOLLOWING;
            short d = compareDocumentPosition(other.getParentNode());
            if (d == 0 || (d & DOCUMENT_POSITION_CONTAINED_BY) != 0) {
                result |= DOCUMENT_POSITION_CONTAINED_BY;
            }
            return result;
        } else if (c == +1) {
            short result = DOCUMENT_POSITION_PRECEDING;
            short d = getParentNode().compareDocumentPosition(other);
            if (d == 0 || (d & DOCUMENT_POSITION_CONTAINS) != 0) {
                result |= DOCUMENT_POSITION_CONTAINS;
            }
            return result;
        } else {
            throw new AssertionError();
        }
    }

    /**
     * Get the text content of a node. This is a DOM Level 3 method. The definition
     * is the same as the definition of the string value of a node in XPath, except
     * in the case of document nodes.
     *
     * @return the string value of the node, or null in the case of document nodes.
     * @throws org.w3c.dom.DOMException if a dynamic error occurs
     */

    @Override
    public String getTextContent() throws DOMException {
        if (node.getNodeKind() == Type.DOCUMENT) {
            return null;
        } else {
            return node.getStringValue();
        }
    }

    /**
     * Set the text content of a node. Always fails.
     *
     * @param textContent the new text content of the node
     * @throws UnsupportedOperationException always
     */

    @Override
    public void setTextContent(String textContent) throws UnsupportedOperationException {
        disallowUpdate();
    }

    /**
     * Get the (first) prefix assigned to a specified namespace URI, or null
     * if the namespace is not in scope. DOM Level 3 method.
     *
     * @param namespaceURI the namespace whose prefix is required
     * @return the corresponding prefix, if there is one, or null if not.
     */

    @Override
    public String lookupPrefix(String namespaceURI) {
        if (node.getNodeKind() == Type.DOCUMENT) {
            return null;
        } else if (node.getNodeKind() == Type.ELEMENT) {
            AxisIterator iter = node.iterateAxis(AxisInfo.NAMESPACE);
            NodeInfo ns;
            while ((ns = iter.next()) != null) {
                if (ns.getStringValue().equals(namespaceURI)) {
                    return ns.getLocalPart();
                }
            }
            return null;
        } else {
            return getParentNode().lookupPrefix(namespaceURI);
        }
    }

    /**
     * Test whether a particular namespace is the default namespace.
     * DOM Level 3 method.
     *
     * @param namespaceURI the namespace to be tested
     * @return true if this is the default namespace
     */

    @Override
    public boolean isDefaultNamespace(String namespaceURI) {
        return namespaceURI.equals(lookupNamespaceURI(""));
    }

    /**
     * Find the URI corresponding to a given in-scope prefix
     *
     * @param prefix The namespace prefix whose namespace URI is required.
     * @return the corresponding namespace URI, or null if the prefix is
     *         not declared.
     */

    @Override
    public String lookupNamespaceURI(String prefix) {
        if (node.getNodeKind() == Type.DOCUMENT) {
            return null;
        } else if (node.getNodeKind() == Type.ELEMENT) {
            AxisIterator iter = node.iterateAxis(AxisInfo.NAMESPACE);
            NodeInfo ns;
            while ((ns = iter.next()) != null) {
                if (ns.getLocalPart().equals(prefix)) {
                    return ns.getStringValue();
                }
            }
            return null;
        } else {
            return getParentNode().lookupNamespaceURI(prefix);
        }
    }

    /**
     * Compare whether two nodes have the same content. This is a DOM Level 3 method.
     *
     * @param arg The node to be compared. This must wrap a Saxon NodeInfo.
     * @return true if the two nodes are deep-equal.
     */

    @Override
    public boolean isEqualNode(Node arg) {
        if (!(arg instanceof NodeOverNodeInfo)) {
            throw new IllegalArgumentException("Other Node must wrap a Saxon NodeInfo");
        }
        try {
            XPathContext context = node.getConfiguration().getConversionContext();
            return DeepEqual.deepEqual(
                SingletonIterator.makeIterator(node),
                SingletonIterator.makeIterator(((NodeOverNodeInfo) arg).node),
                new GenericAtomicComparer(CodepointCollator.getInstance(),
                    context),
                context,
                DeepEqual.INCLUDE_PREFIXES |
                    DeepEqual.INCLUDE_COMMENTS |
                    DeepEqual.COMPARE_STRING_VALUES |
                    DeepEqual.INCLUDE_PROCESSING_INSTRUCTIONS);
        } catch (XPathException err) {
            // can't happen
            return false;
        }
    }

    /**
     * Get a feature of this node. DOM Level 3 method, always returns null.
     *
     * @param feature the required feature
     * @param version the version of the required feature
     * @return the value of the feature. Always null in this implementation
     */

    @Override
    public Object getFeature(String feature, String version) {
        return null;
    }

    /**
     * Set user data. Always throws UnsupportedOperationException in this implementation
     *
     * @param key     name of the user data
     * @param data    value of the user data
     * @param handler handler for the user data
     * @return This implementation always throws an exception
     */

    @Override
    public Object setUserData(String key, Object data, UserDataHandler handler) {
        disallowUpdate();
        return null;
    }

    /**
     * Get user data associated with this node. DOM Level 3 method, always returns
     * null in this implementation
     *
     * @param key identifies the user data required
     * @return always null in this implementation
     */
    @Override
    public Object getUserData(String key) {
        return null;
    }

    /**
     * Internal method used to indicate that update operations are not allowed
     *
     * @throws org.w3c.dom.DOMException always, to indicate that update is not supported in this DOM implementation
     */

    protected static void disallowUpdate() throws DOMException {
        throw new org.w3c.dom.DOMException(
                DOMException.NO_MODIFICATION_ALLOWED_ERR,
                "The Saxon DOM implementation cannot be updated");
    }

}

