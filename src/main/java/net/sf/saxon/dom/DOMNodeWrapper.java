////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NamespaceMap;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.util.SteppingNavigator;
import net.sf.saxon.tree.util.SteppingNode;
import net.sf.saxon.tree.wrapper.AbstractNodeWrapper;
import net.sf.saxon.tree.wrapper.SiblingCountingNode;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.UType;
import org.w3c.dom.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.function.Predicate;


/**
 * A node in the XML parse tree representing an XML element, character content, or attribute.
 * This is the implementation of the NodeInfo interface used as a wrapper for DOM nodes.
 * <p>Because the DOM is not thread-safe even when reading, and because Saxon-EE can spawn multiple
 * threads that access the same input tree, all methods that invoke DOM methods are synchronized
 * on the Document object.</p>
 */

@SuppressWarnings({"SynchronizeOnNonFinalField"})
public class DOMNodeWrapper extends AbstractNodeWrapper implements SiblingCountingNode, SteppingNode<DOMNodeWrapper> {

    protected Node node;
    protected short nodeKind;
    private DOMNodeWrapper parent;     // null means unknown
    protected DocumentWrapper docWrapper; // effectively final
    protected int index;            // -1 means unknown
    protected int span = 1;         // the number of adjacent text nodes wrapped by this NodeWrapper.
    // If span>1, node will always be the first of a sequence of adjacent text nodes
    private NamespaceBinding[] localNamespaces = null;
    private NamespaceMap inScopeNamespaces = null;

    /**
     * This constructor is protected: nodes should be created using the makeWrapper
     * factory method
     *
     * @param node       The DOM node to be wrapped
     * @param docWrapper The wrapper for the Document node at the root of the DOM tree. Never null
     *                   except in the case where we are creating the DocumentWrapper itself (which is a subclass).
     * @param parent     The DOMNodeWrapper that wraps the parent of this node. May be null if unknown.
     * @param index      Position of this node among its siblings, 0-based. May be -1 if unknown.
     */
    protected DOMNodeWrapper(Node node, DocumentWrapper docWrapper, /*@Nullable*/ DOMNodeWrapper parent, int index) {
        this.node = node;
        this.parent = parent;
        this.index = index;
        this.docWrapper = docWrapper;
    }

    /**
     * Factory method to wrap a DOM node with a wrapper that implements the Saxon
     * NodeInfo interface.
     *
     * @param node       The DOM node
     * @param docWrapper The wrapper for the containing Document node
     * @return The new wrapper for the supplied node
     * @throws NullPointerException if the node or the document wrapper are null
     */
    protected static DOMNodeWrapper makeWrapper(Node node, DocumentWrapper docWrapper) {
        if (node == null) {
            throw new NullPointerException("NodeWrapper#makeWrapper: Node must not be null");
        }
        if (docWrapper == null) {
            throw new NullPointerException("NodeWrapper#makeWrapper: DocumentWrapper must not be null");
        }
        return makeWrapper(node, docWrapper, null, -1);
    }

    /**
     * Factory method to wrap a DOM node with a wrapper that implements the Saxon
     * NodeInfo interface.
     *
     * @param node       The DOM node
     * @param docWrapper The wrapper for the containing Document node     *
     * @param parent     The wrapper for the parent of the JDOM node
     * @param index      The position of this node relative to its siblings
     * @return The new wrapper for the supplied node
     */

    protected static DOMNodeWrapper makeWrapper(Node node, DocumentWrapper docWrapper,
                                         /*@Nullable*/ DOMNodeWrapper parent, int index) {
        DOMNodeWrapper wrapper;
        switch (node.getNodeType()) {
            case Node.DOCUMENT_NODE:
            case Node.DOCUMENT_FRAGMENT_NODE:
                wrapper = (DOMNodeWrapper)docWrapper.getRootNode();
                if (wrapper == null) {
                    wrapper = new DOMNodeWrapper(node, docWrapper, parent, index);
                    wrapper.nodeKind = Type.DOCUMENT;
                }
                break;
            case Node.ELEMENT_NODE:
                wrapper = new DOMNodeWrapper(node, docWrapper, parent, index);
                wrapper.nodeKind = Type.ELEMENT;
                break;
            case Node.ATTRIBUTE_NODE:
                wrapper = new DOMNodeWrapper(node, docWrapper, parent, index);
                wrapper.nodeKind = Type.ATTRIBUTE;
                break;
            case Node.TEXT_NODE:
                wrapper = new DOMNodeWrapper(node, docWrapper, parent, index);
                wrapper.nodeKind = Type.TEXT;
                break;
            case Node.CDATA_SECTION_NODE:
                wrapper = new DOMNodeWrapper(node, docWrapper, parent, index);
                wrapper.nodeKind = Type.TEXT;
                break;
            case Node.COMMENT_NODE:
                wrapper = new DOMNodeWrapper(node, docWrapper, parent, index);
                wrapper.nodeKind = Type.COMMENT;
                break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                wrapper = new DOMNodeWrapper(node, docWrapper, parent, index);
                wrapper.nodeKind = Type.PROCESSING_INSTRUCTION;
                break;
            case Node.ENTITY_REFERENCE_NODE:
                throw new IllegalStateException("DOM contains entity reference nodes, which Saxon does not support. The DOM should be built using the expandEntityReferences() option");

            default:
                throw new IllegalArgumentException("Unsupported node type in DOM! " + node.getNodeType() + " instance " + node);
        }
        wrapper.treeInfo = docWrapper;
        return wrapper;
    }

    @Override
    public DocumentWrapper getTreeInfo() {
        return (DocumentWrapper)treeInfo;
    }

    /**
     * Get the underlying DOM node, to implement the VirtualNode interface
     */

    @Override
    public Node getUnderlyingNode() {
        return node;
    }

    /**
     * Return the kind of node.
     *
     * @return one of the values Node.ELEMENT, Node.TEXT, Node.ATTRIBUTE, etc.
     */

    @Override
    public int getNodeKind() {
        return nodeKind;
    }

    /**
     * Determine whether this is the same node as another node.
     * <p>Note: a.equals(b) if and only if generateId(a)==generateId(b)</p>
     *
     * @return true if this Node object and the supplied Node object represent the
     *         same node in the tree.
     */

    public boolean equals(Object other) {
        if (!(other instanceof DOMNodeWrapper)) {
            return false;
        }
        if (docWrapper.domLevel3) {
            synchronized (docWrapper.docNode) {
                return node.isSameNode(((DOMNodeWrapper) other).node);
            }
        } else {
            DOMNodeWrapper ow = (DOMNodeWrapper) other;
            return getNodeKind() == ow.getNodeKind() &&
                    equalOrNull(getLocalPart(), ow.getLocalPart()) &&  // redundant, but gives a quick exit
                    getSiblingPosition() == ow.getSiblingPosition() &&
                    getParent().equals(ow.getParent());
        }
    }

    private boolean equalOrNull(String a, String b) {
        return a==null ? b==null : a.equals(b);
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
    public int compareOrder(NodeInfo other) {
        // Use the DOM Level-3 compareDocumentPosition() method
        if (other instanceof DOMNodeWrapper && docWrapper.domLevel3) {
            if (equals(other)) {
                return 0;
            }
            try {
                synchronized (docWrapper.docNode) {
                    short relationship = node.compareDocumentPosition(((DOMNodeWrapper) other).node);
                    if ((relationship &
                            (Node.DOCUMENT_POSITION_PRECEDING | Node.DOCUMENT_POSITION_CONTAINS)) != 0) {
                        return +1;
                    } else if ((relationship &
                            (Node.DOCUMENT_POSITION_FOLLOWING | Node.DOCUMENT_POSITION_CONTAINED_BY)) != 0) {
                        return -1;
                    }
                }
                // otherwise use fallback implementation (e.g. nodes in different documents)
            } catch (DOMException e) {
                // can happen if nodes are from different DOM implementations.
                // use fallback implementation
            }
        }

        if (other instanceof SiblingCountingNode) {
            return Navigator.compareOrder(this, (SiblingCountingNode) other);
        } else {
            // it's presumably a Namespace Node
            return -other.compareOrder(this);
        }
    }

    /**
     * Get the value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String.
     */

    @Override
    public CharSequence getStringValueCS() {
        synchronized (docWrapper.docNode) {
            switch (nodeKind) {
                case Type.DOCUMENT:
                case Type.ELEMENT:
                    NodeList children1 = node.getChildNodes();
                    FastStringBuffer sb1 = new FastStringBuffer(16);
                    expandStringValue(children1, sb1);
                    return sb1;

                case Type.ATTRIBUTE:
                    return emptyIfNull(((Attr) node).getValue());

                case Type.TEXT:
                    if (span == 1) {
                        return emptyIfNull(node.getNodeValue());
                    } else {
                        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C64);
                        Node textNode = node;
                        for (int i = 0; i < span; i++) {
                            fsb.append(emptyIfNull(textNode.getNodeValue()));
                            textNode = textNode.getNextSibling();
                        }
                        return fsb.condense();
                    }

                case Type.COMMENT:
                case Type.PROCESSING_INSTRUCTION:
                    return emptyIfNull(node.getNodeValue());

                default:
                    return "";
            }
        }
    }

    /**
     * Treat a node value of null as an empty string.
     *
     * @param s the node value
     * @return a zero-length string if s is null, otherwise s
     */

    private static String emptyIfNull(String s) {
        return s == null ? "" : s;
    }

    public static void expandStringValue(NodeList list, FastStringBuffer sb) {
        final int len = list.getLength();
        for (int i = 0; i < len; i++) {
            Node child = list.item(i);
            switch (child.getNodeType()) {
                case Node.ELEMENT_NODE:
                    expandStringValue(child.getChildNodes(), sb);
                    break;
                case Node.COMMENT_NODE:
                case Node.PROCESSING_INSTRUCTION_NODE:
                    break;
                case Node.DOCUMENT_TYPE_NODE:
                    break;
                default:
                    sb.append(emptyIfNull(child.getNodeValue()));
                    break;
            }
        }
    }


    /**
     * Get the local part of the name of this node. This is the name after the ":" if any.
     *
     * @return the local part of the name. For an unnamed node, returns "".
     */

    @Override
    public String getLocalPart() {
        synchronized (docWrapper.docNode) {
            switch (getNodeKind()) {
                case Type.ELEMENT:
                case Type.ATTRIBUTE:
                    return getLocalName(node);
                case Type.PROCESSING_INSTRUCTION:
                    return node.getNodeName();
                default:
                    return "";
            }
        }
    }

    /**
     * Get the local name of a DOM element or attribute node.
     *
     * @param node the DOM element or attribute node
     * @return the local name as defined in XDM
     */

    public static String getLocalName(Node node) {
        String s = node.getLocalName();
        if (s == null) {
            // true if the node was created using a DOM level 1 method
            String n = node.getNodeName();
            int colon = n.indexOf(':');
            if (colon >= 0) {
                return n.substring(colon + 1);
            }
            return n;
        } else {
            return s;
        }
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
        synchronized (docWrapper.docNode) {
            if (nodeKind == Type.ELEMENT) {
                return getElementURI((Element) node);
            } else if (nodeKind == Type.ATTRIBUTE) {
                return getAttributeURI((Attr) node);
            }
            return "";
        }
    }

    private static String getElementURI(Element element) {

        // The DOM methods getPrefix() and getNamespaceURI() do not always
        // return the prefix and the URI; they both return null, unless the
        // prefix and URI have been explicitly set in the node by using DOM
        // level 2 interfaces. There's no obvious way of deciding whether
        // an element whose name has no prefix is in the default namespace,
        // other than searching for a default namespace declaration. So we have to
        // be prepared to search.

        // If getPrefix() and getNamespaceURI() are non-null, however,
        // we can use the values.

        String uri = element.getNamespaceURI();
        if (uri != null) {
            return uri;
        }

        // Otherwise we have to work it out the hard way...

        String displayName = element.getNodeName();
        int colon = displayName.indexOf(':');
        String attName = colon < 0 ? "xmlns" : "xmlns:" + displayName.substring(0, colon);

        if (attName.equals("xmlns:xml")) {
            return NamespaceConstant.XML;
        }

        Node node = element;
        do {
            if (((Element) node).hasAttribute(attName)) {
                return ((Element) node).getAttribute(attName);
            }
            node = node.getParentNode();
        } while (node != null && node.getNodeType() == Node.ELEMENT_NODE);

        if (colon < 0) {
            return "";
        } else {
            throw new IllegalStateException("Undeclared namespace prefix in element name " + displayName + " in DOM input");
        }

    }


    private static String getAttributeURI(Attr attr) {

        String uri = attr.getNamespaceURI();
        if (uri != null) {
            return uri;
        }

        // Otherwise we have to work it out the hard way...

        String displayName = attr.getNodeName();
        int colon = displayName.indexOf(':');
        if (colon < 0) {
            return "";
        }
        String attName = "xmlns:" + displayName.substring(0, colon);

        if (attName.equals("xmlns:xml")) {
            return NamespaceConstant.XML;
        }

        Node node = attr.getOwnerElement();
        do {
            if (((Element) node).hasAttribute(attName)) {
                return ((Element) node).getAttribute(attName);
            }
            node = node.getParentNode();
        } while (node != null && node.getNodeType() == Node.ELEMENT_NODE);

        throw new IllegalStateException("Undeclared namespace prefix in attribute name " + displayName + " in DOM input");

    }


    /**
     * Get the prefix of the name of the node. This is defined only for elements and attributes.
     * If the node has no prefix, or for other kinds of node, return a zero-length string.
     *
     * @return The prefix of the name of the node.
     */

    @Override
    public String getPrefix() {
        synchronized (docWrapper.docNode) {
            int kind = getNodeKind();
            if (kind == Type.ELEMENT || kind == Type.ATTRIBUTE) {
                String name = node.getNodeName();
                int colon = name.indexOf(':');
                if (colon < 0) {
                    return "";
                } else {
                    return name.substring(0, colon);
                }
            }
            return "";
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
        switch (nodeKind) {
            case Type.ELEMENT:
            case Type.ATTRIBUTE:
            case Type.PROCESSING_INSTRUCTION:
                synchronized (docWrapper.docNode) {
                    return node.getNodeName();
                }
            default:
                return "";

        }
    }

    /**
     * Get the NodeInfo object representing the parent of this node
     */

    @Override
    public DOMNodeWrapper getParent() {
        if (parent == null) {
            synchronized (docWrapper.docNode) {
                switch (getNodeKind()) {
                    case Type.ATTRIBUTE:
                        parent = makeWrapper(((Attr) node).getOwnerElement(), docWrapper);
                        break;
                    default:
                        Node p = node.getParentNode();
                        if (p == null) {
                            return null;
                        } else {
                            parent = makeWrapper(p, docWrapper);
                        }
                }
            }
        }
        return parent;
    }

    /**
     * Get the index position of this node among its siblings (starting from 0).
     * In the case of a text node that maps to several adjacent siblings in the DOM,
     * the numbering actually refers to the position of the underlying DOM nodes;
     * thus the sibling position for the text node is that of the first DOM node
     * to which it relates, and the numbering of subsequent XPath nodes is not necessarily
     * consecutive.
     * <p>Despite the name, this method also returns a meaningful result for attribute
     * nodes; it returns the position of the attribute among the attributes of its
     * parent element, when they are listed in document order.</p>
     */

    @Override
    public int getSiblingPosition() {
        if (index == -1) {
            synchronized (docWrapper.docNode) {
                switch (nodeKind) {
                    case Type.ELEMENT:
                    case Type.TEXT:
                    case Type.COMMENT:
                    case Type.PROCESSING_INSTRUCTION:
                        int ix = 0;
                        Node start = node;
                        while (true) {
                            start = start.getPreviousSibling();
                            if (start == null) {
                                index = ix;
                                return ix;
                            }
                            ix++;
                        }
                    case Type.ATTRIBUTE:
                        ix = 0;
                        AxisIterator iter = parent.iterateAxis(AxisInfo.ATTRIBUTE);
                        while (true) {
                            NodeInfo n = iter.next();
                            if (n == null || Navigator.haveSameName(this, n)) {
                                index = ix;
                                return ix;
                            }
                            ix++;
                        }

                    case Type.NAMESPACE:
                        ix = 0;
                        iter = parent.iterateAxis(AxisInfo.NAMESPACE);
                        while (true) {
                            NodeInfo n = iter.next();
                            if (n == null || Navigator.haveSameName(this, n)) {
                                index = ix;
                                return ix;
                            }
                            ix++;
                        }
                    default:
                        index = 0;
                        return index;
                }
            }
        }
        return index;
    }

    @Override
    protected AxisIterator iterateAttributes(Predicate<? super NodeInfo> nodeTest) {
        AxisIterator iter = new AttributeEnumeration(this);
        if (nodeTest != AnyNodeTest.getInstance()) {
            iter = new Navigator.AxisFilter(iter, nodeTest);
        }
        return iter;
    }

    private boolean isElementOnly(Predicate<? super NodeInfo> nodeTest) {
        return nodeTest instanceof NodeTest && ((NodeTest) nodeTest).getUType() == UType.ELEMENT;
    }

    @Override
    protected AxisIterator iterateChildren(Predicate<? super NodeInfo> nodeTest) {
        boolean elementOnly = isElementOnly(nodeTest);
        AxisIterator iter = new Navigator.EmptyTextFilter(
                new ChildEnumeration(this, true, true, elementOnly));
        if (nodeTest != AnyNodeTest.getInstance()) {
            iter = new Navigator.AxisFilter(iter, nodeTest);
        }
        return iter;
    }

    @Override
    protected AxisIterator iterateSiblings(Predicate<? super NodeInfo> nodeTest, boolean forwards) {
        boolean elementOnly = isElementOnly(nodeTest);
        AxisIterator iter = new Navigator.EmptyTextFilter(
                new ChildEnumeration(this, false, forwards, elementOnly));
        if (nodeTest != AnyNodeTest.getInstance()) {
            iter = new Navigator.AxisFilter(iter, nodeTest);
        }
        return iter;
    }

    @Override
    protected AxisIterator iterateDescendants(Predicate<? super NodeInfo> nodeTest, boolean includeSelf) {
        return new SteppingNavigator.DescendantAxisIterator(this, includeSelf, nodeTest);
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
        NameTest test = new NameTest(Type.ATTRIBUTE, uri, local, getNamePool());
        AxisIterator iterator = iterateAxis(AxisInfo.ATTRIBUTE, test);
        NodeInfo attribute = iterator.next();
        if (attribute == null) {
            return null;
        } else {
            return attribute.getStringValue();
        }
    }

    /**
     * Get the root node - always a document node with this tree implementation
     *
     * @return the NodeInfo representing the containing document
     */

    @Override
    public NodeInfo getRoot() {
        return docWrapper.getRootNode();
    }

    /**
     * Determine whether the node has any children.
     * <p>Note: the result is equivalent to
     * <code>getEnumeration(Axis.CHILD, AnyNodeTest.getInstance()).hasNext()</code></p>
     */

    @Override
    public boolean hasChildNodes() {
        // An attribute node has child text nodes
        synchronized (docWrapper.docNode) {
            return node.getNodeType() != Node.ATTRIBUTE_NODE && node.hasChildNodes();
        }
    }

    /**
     * Get a character string that uniquely identifies this node.
     * Note: a.isSameNode(b) if and only if generateId(a)==generateId(b)
     *
     * @param buffer a buffer to contain a string that uniquely identifies this node, across all
     *               documents
     */

    @Override
    public void generateId(FastStringBuffer buffer) {
        Navigator.appendSequentialKey(this, buffer, true);
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
        synchronized (docWrapper.docNode) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                if (localNamespaces != null) {
                    return localNamespaces;
                }
                Element elem = (Element) node;
                NamedNodeMap atts = elem.getAttributes();

                if (atts == null) {
                    localNamespaces = NamespaceBinding.EMPTY_ARRAY;
                    return NamespaceBinding.EMPTY_ARRAY;
                }
                int count = 0;
                final int attsLen = atts.getLength();
                for (int i = 0; i < attsLen; i++) {
                    Attr att = (Attr) atts.item(i);
                    String attName = att.getName();
                    if (attName.equals("xmlns")) {
                        count++;
                    } else if (attName.startsWith("xmlns:")) {
                        count++;
                    }
                }
                if (count == 0) {
                    localNamespaces = NamespaceBinding.EMPTY_ARRAY;
                    return NamespaceBinding.EMPTY_ARRAY;
                } else {
                    NamespaceBinding[] result = buffer == null || count > buffer.length ? new NamespaceBinding[count] : buffer;
                    int n = 0;
                    for (int i = 0; i < attsLen; i++) {
                        Attr att = (Attr) atts.item(i);
                        String attName = att.getName();
                        if (attName.equals("xmlns")) {
                            String prefix = "";
                            String uri = att.getValue();
                            result[n++] = new NamespaceBinding(prefix, uri);
                        } else if (attName.startsWith("xmlns:")) {
                            String prefix = attName.substring(6);
                            String uri = att.getValue();
                            result[n++] = new NamespaceBinding(prefix, uri);
                        }
                    }
                    if (count < result.length) {
                        result[count] = null;
                    }
                    localNamespaces = Arrays.copyOf(result, result.length);
                    return result;
                }
            } else {
                return null;
            }
        }
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
            if (inScopeNamespaces != null) {
                return inScopeNamespaces;
            } else {
                NodeInfo parent = getParent();
                NamespaceMap nsMap = parent != null && parent.getNodeKind() == Type.ELEMENT
                        ? parent.getAllNamespaces()
                        : NamespaceMap.emptyMap();
                Element elem = (Element) node;
                NamedNodeMap atts = elem.getAttributes();
                if (atts != null) {
                    int attsLen = atts.getLength();
                    for (int i = 0; i < attsLen; i++) {
                        Attr att = (Attr) atts.item(i);
                        String attName = att.getName();
                        if (attName.startsWith("xmlns")) {
                            if (attName.length() == 5) {
                                nsMap = nsMap.bind("", att.getValue());
                            } else if (attName.charAt(5) == ':') {
                                nsMap = nsMap.bind(attName.substring(6), att.getValue());
                            }
                        }
                    }
                }
                return inScopeNamespaces = nsMap;
            }
        } else {
            // not an element node
            return null;
        }
    }

    /**
     * Determine whether this node has the is-id property
     *
     * @return true if the node is an ID
     */

    @Override
    public boolean isId() {
        synchronized (docWrapper.docNode) {
            return (node instanceof Attr) && ((Attr) node).isId();
        }
    }

    @Override
    public DOMNodeWrapper getNextSibling() {
        synchronized(docWrapper.docNode) {
            Node currNode = node;
            for (int i = 0; i < span; i++) {
                currNode = currNode.getNextSibling();
            }
            if (currNode != null) {
                short type = currNode.getNodeType();
                if (type == Node.DOCUMENT_TYPE_NODE) {
                    currNode = currNode.getNextSibling();
                } else if (type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE) {
                    return spannedWrapper(currNode);
                }
                return makeWrapper(currNode, docWrapper);
            }
            return null;
        }
    }

    private DOMNodeWrapper spannedWrapper(Node currNode) {
        Node currText = currNode;
        int thisSpan = 1;
        while (true) {
            currText = currText.getNextSibling();
            if (currText != null && (currText.getNodeType() == Node.TEXT_NODE || currText.getNodeType() == Node.CDATA_SECTION_NODE)) {
                thisSpan++;
            } else {
                break;
            }
        }
        DOMNodeWrapper spannedText = makeWrapper(currNode, docWrapper);
        spannedText.span = thisSpan;
        return spannedText;
    }


    @Override
    public DOMNodeWrapper getFirstChild() {
        synchronized(docWrapper.docNode) {
            Node currNode = node.getFirstChild();
            if (currNode != null) {
                if (currNode.getNodeType() == Node.DOCUMENT_TYPE_NODE) {
                    currNode = currNode.getNextSibling();
                }
                if (currNode.getNodeType() == Node.TEXT_NODE || currNode.getNodeType() == Node.CDATA_SECTION_NODE) {
                    return spannedWrapper(currNode);
                }
                return makeWrapper(currNode, docWrapper);
            }
            return null;
        }
    }

    @Override
    public DOMNodeWrapper getPreviousSibling() {
        synchronized(docWrapper.docNode) {
            Node currNode = node.getPreviousSibling();
            if (currNode != null) {
                short type = currNode.getNodeType();
                if (type == Node.DOCUMENT_TYPE_NODE) {
                    return null;
                } else if (type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE) {
                    int span = 1;
                    while (true) {
                        Node prev = currNode.getPreviousSibling();
                        if (prev != null && (prev.getNodeType() == Node.TEXT_NODE || prev.getNodeType() == Node.CDATA_SECTION_NODE)) {
                            span++;
                            currNode = prev;
                        } else {
                            break;
                        }
                    }
                    DOMNodeWrapper wrapper = makeWrapper(currNode, docWrapper);
                    wrapper.span = span;
                    return wrapper;
                }
                return makeWrapper(currNode, docWrapper);
            }
            return null;
        }
    }

    @Override
    public DOMNodeWrapper getSuccessorElement(DOMNodeWrapper anchor, String uri, String local) {
        synchronized (docWrapper.docNode) {
            Node stop = anchor == null ? null : anchor.node;
            Node next = node;
            do {
                next = getSuccessorNode(next, stop);
            } while (next != null &&
                    !(next.getNodeType() == Node.ELEMENT_NODE &&
                            (local == null || local.equals(getLocalName(next))) &&
                            (uri == null || uri.equals(getElementURI((Element) next)))));
            if (next == null) {
                return null;
            } else {
                return makeWrapper(next, docWrapper);
            }
        }
    }

    /**
     * Get the following DOM node in an iteration of a subtree
     *
     * @param start  the start DOM node
     * @param anchor the DOM node marking the root of the subtree within which navigation takes place (may be null)
     * @return the next DOM node in document order after the start node, excluding attributes and namespaces
     */

    private static Node getSuccessorNode(Node start, Node anchor) {
        if (start.hasChildNodes()) {
            return start.getFirstChild();
        }
        if (anchor != null && start.isSameNode(anchor)) {
            return null;
        }
        Node p = start;
        while (true) {
            Node s = p.getNextSibling();
            if (s != null) {
                return s;
            }
            p = p.getParentNode();
            if (p == null || (anchor != null && p.isSameNode(anchor))) {
                return null;
            }
        }
    }

    private final class AttributeEnumeration implements AxisIterator, LookaheadIterator {

        private final ArrayList<Node> attList = new ArrayList<>(10);
        private int ix = 0;
        private final DOMNodeWrapper start;
        private DOMNodeWrapper current;

        public  AttributeEnumeration(DOMNodeWrapper start) {
            synchronized (start.docWrapper.docNode) {
                this.start = start;
                NamedNodeMap atts = start.node.getAttributes();
                if (atts != null) {
                    final int attsLen = atts.getLength();
                    for (int i = 0; i < attsLen; i++) {
                        String name = atts.item(i).getNodeName();
                        if (!(name.startsWith("xmlns") &&
                                (name.length() == 5 || name.charAt(5) == ':'))) {
                            attList.add(atts.item(i));
                        }
                    }
                }
                ix = 0;
            }
        }

        @Override
        public boolean hasNext() {
            return ix < attList.size();
        }

        @Override
        public NodeInfo next() {
            if (ix >= attList.size()) {
                return null;
            }
            current = makeWrapper(attList.get(ix), docWrapper, start, ix);
            ix++;
            return current;
        }

        /**
         * Get properties of this iterator, as a bit-significant integer.
         *
         * @return the properties of this iterator. This will be some combination of
         *         properties such as {@link net.sf.saxon.om.SequenceIterator.Property#GROUNDED},
         *         {@link net.sf.saxon.om.SequenceIterator.Property#LAST_POSITION_FINDER},
         *         and {@link net.sf.saxon.om.SequenceIterator.Property#LOOKAHEAD}. It is always
         *         acceptable to return the value zero, indicating that there are no known special properties.
         *         It is acceptable for the properties of the iterator to change depending on its state.
         */

        @Override
        public EnumSet<Property> getProperties() {
            return EnumSet.of(Property.LOOKAHEAD);
        }
    }


    /**
     * The class ChildEnumeration handles not only the child axis, but also the
     * following-sibling and preceding-sibling axes. It can also iterate the children
     * of the start node in reverse order, something that is needed to support the
     * preceding and preceding-or-ancestor axes (the latter being used by xsl:number)
     */

    private final class ChildEnumeration implements AxisIterator, LookaheadIterator {

        private final DOMNodeWrapper start;
        private final DOMNodeWrapper commonParent;
        private final boolean downwards;  // iterate children of start node (not siblings)
        private final boolean forwards;   // iterate in document order (not reverse order)
        private final boolean elementsOnly;
        NodeList childNodes;
        private int childNodesLength;
        private int ix;             // index of the current DOM node within childNodes;
        // in the case of adjacent text nodes, index of the first in the group
        private int currentSpan;    // number of DOM nodes mapping to the current XPath node

        /**
         * Create an iterator over the children or siblings of a given node
         *
         * @param start        the start node for the iteration
         * @param downwards    if true, iterate over the children of the start node; if false, iterate
         *                     over the following or preceding siblings
         * @param forwards     if true, iterate in forwards document order; if false, iterate in reverse
         *                     document order
         * @param elementsOnly if true, retrieve element nodes only; if false, retrieve all nodes
         */
        public ChildEnumeration(DOMNodeWrapper start,
                                boolean downwards, boolean forwards, boolean elementsOnly) {
            synchronized (start.docWrapper.docNode) {
                this.start = start;
                this.downwards = downwards;
                this.forwards = forwards;
                this.elementsOnly = elementsOnly;
                currentSpan = 1;

                if (downwards) {
                    commonParent = start;
                } else {
                    commonParent = start.getParent();
                }

                childNodes = commonParent.node.getChildNodes();
                childNodesLength = childNodes.getLength();
                if (downwards) {
                    currentSpan = 1;
                    if (forwards) {
                        ix = -1;                        // just before first
                    } else {
                        ix = childNodesLength;          // just after last
                    }
                } else {
                    ix = start.getSiblingPosition();    // at current node
                    currentSpan = start.span;
                }
            }
        }

        /**
         * Starting with ix positioned at a node, which in the last in a span, calculate the length
         * of the span, that is the number of DOM nodes mapped to this XPath node.
         *
         * @return the number of nodes spanned
         */

        private int skipPrecedingTextNodes() {
            int count = 0;
            while (ix >= count) {
                Node node = childNodes.item(ix - count);
                short kind = node.getNodeType();
                if (kind == Node.TEXT_NODE || kind == Node.CDATA_SECTION_NODE) {
                    count++;
                } else {
                    break;
                }
            }
            return count == 0 ? 1 : count;
        }

        /**
         * Starting with ix positioned at a node, which in the first in a span, calculate the length
         * of the span, that is the number of DOM nodes mapped to this XPath node.
         *
         * @return the number of nodes spanned
         */

        private int skipFollowingTextNodes() {
            int count = 0;
            int pos = ix;
            final int len = childNodesLength;
            while (pos < len) {
                Node node = childNodes.item(pos);
                short kind = node.getNodeType();
                if (kind == Node.TEXT_NODE || kind == Node.CDATA_SECTION_NODE) {
                    pos++;
                    count++;
                } else {
                    break;
                }
            }
            return count == 0 ? 1 : count;
        }

        @Override
        public boolean hasNext() {
            if (forwards) {
                return ix + currentSpan < childNodesLength;
            } else {
                return ix > 0;
            }
        }

        /*@Nullable*/
        @Override
        public NodeInfo next() {
            synchronized(start.docWrapper.docNode) {
                while (true) {
                    if (forwards) {
                        ix += currentSpan;
                        if (ix >= childNodesLength) {
                            return null;
                        } else {
                            currentSpan = skipFollowingTextNodes();
                            Node currentDomNode = childNodes.item(ix);
                            switch (currentDomNode.getNodeType()) {
                                case Node.DOCUMENT_TYPE_NODE:
                                    continue;
                                case Node.ELEMENT_NODE:
                                    break;
                                default:
                                    if (elementsOnly) {
                                        continue;
                                    } else {
                                        break;
                                    }
                            }
                            DOMNodeWrapper wrapper = makeWrapper(currentDomNode, docWrapper, commonParent, ix);
                            wrapper.span = currentSpan;
                            return wrapper;
                        }
                    } else {
                        ix--;
                        if (ix < 0) {
                            return null;
                        } else {
                            currentSpan = skipPrecedingTextNodes();
                            ix -= currentSpan - 1;
                            Node currentDomNode = childNodes.item(ix);
                            switch (currentDomNode.getNodeType()) {
                                case Node.DOCUMENT_TYPE_NODE:
                                    continue;
                                case Node.ELEMENT_NODE:
                                    break;
                                default:
                                    if (elementsOnly) {
                                        continue;
                                    } else {
                                        break;
                                    }
                            }
                            DOMNodeWrapper wrapper = makeWrapper(currentDomNode, docWrapper, commonParent, ix);
                            wrapper.span = currentSpan;
                            return wrapper;
                        }
                    }
                }
            }
        }

        /**
         * Get properties of this iterator, as a bit-significant integer.
         *
         * @return the properties of this iterator. This will be some combination of
         *         properties such as {@link net.sf.saxon.om.SequenceIterator.Property#GROUNDED},
         *         {@link net.sf.saxon.om.SequenceIterator.Property#LAST_POSITION_FINDER},
         *         and {@link net.sf.saxon.om.SequenceIterator.Property#LOOKAHEAD}. It is always
         *         acceptable to return the value zero, indicating that there are no known special properties.
         *         It is acceptable for the properties of the iterator to change depending on its state.
         */

        @Override
        public EnumSet<Property> getProperties() {
            return EnumSet.of(Property.LOOKAHEAD);
        }

    } // end of class ChildEnumeration


}

