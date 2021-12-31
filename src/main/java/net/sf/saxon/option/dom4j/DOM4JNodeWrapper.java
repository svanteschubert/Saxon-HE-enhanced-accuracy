////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.dom4j;

import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NamespaceMap;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.util.SteppingNavigator;
import net.sf.saxon.tree.util.SteppingNode;
import net.sf.saxon.tree.wrapper.AbstractNodeWrapper;
import net.sf.saxon.tree.wrapper.SiblingCountingNode;
import net.sf.saxon.type.Type;
import org.dom4j.*;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Predicate;

/**
 * A node in the XML parse tree representing an XML element, character content, or attribute.
 * <p>This is the implementation of the NodeInfo interface used as a wrapper for DOM4J nodes.</p>
 *
 * @author Michael H. Kay
 */

// History: this started life as the NodeWrapper for JDOM nodes; it was then modified by the
// Orbeon team to act as a wrapper for DOM4J nodes, and was shipped with the Orbeon product;
// it has now been absorbed back into Saxon.

public class DOM4JNodeWrapper extends AbstractNodeWrapper implements SiblingCountingNode, SteppingNode<DOM4JNodeWrapper> {

    protected Node node;
    protected short nodeKind;
    /*@Nullable*/ private DOM4JNodeWrapper parent;     // null means unknown
    // Beware: with dom4j, this is an index over the result of content(), which may contain Namespace nodes
    protected int index;            // -1 means unknown
    private NamespaceMap inScopeNamespaces;

    /**
     * This constructor is protected: nodes should be created using the wrap
     * factory method on the DocumentWrapper class
     *
     * @param node   The DOM4J node to be wrapped
     * @param parent The NodeWrapper that wraps the parent of this node
     * @param index  Position of this node among its siblings
     */
    protected DOM4JNodeWrapper(Node node, DOM4JNodeWrapper parent, int index) {
        this.node = node;
        this.parent = parent;
        this.index = index;
    }

    /**
     * Factory method to wrap a DOM4J node with a wrapper that implements the Saxon
     * NodeInfo interface.
     *
     * @param node       The DOM4J node
     * @param docWrapper The wrapper for the Document containing this node
     * @return The new wrapper for the supplied node
     */
    protected static DOM4JNodeWrapper makeWrapper(Node node, DOM4JDocumentWrapper docWrapper) {
        return makeWrapper(node, docWrapper, null, -1);
    }

    /**
     * Factory method to wrap a DOM4J node with a wrapper that implements the Saxon
     * NodeInfo interface.
     *
     * @param node       The DOM4J node
     * @param docWrapper The wrapper for the Document containing this node
     * @param parent     The wrapper for the parent of the DOM4J node
     * @param index      The position of this node relative to its siblings
     * @return The new wrapper for the supplied node
     */

    protected static DOM4JNodeWrapper makeWrapper(Node node, DOM4JDocumentWrapper docWrapper,
                                           DOM4JNodeWrapper parent, int index) {
        DOM4JNodeWrapper wrapper;
        short nodeType = node.getNodeType();
        switch (nodeType) {
            case Node.ELEMENT_NODE:
                wrapper = new DOM4JNodeWrapper(node, parent, index);
                wrapper.nodeKind = Type.ELEMENT;
                break;
            case Node.ATTRIBUTE_NODE:
                wrapper = new DOM4JNodeWrapper(node, parent, index);
                wrapper.nodeKind = Type.ATTRIBUTE;
                break;
            case Node.TEXT_NODE:
            case Node.CDATA_SECTION_NODE:
                wrapper = new DOM4JNodeWrapper(node, parent, index);
                wrapper.nodeKind = Type.TEXT;
                break;
            case Node.DOCUMENT_NODE:
                wrapper = (DOM4JNodeWrapper)docWrapper.getRootNode();
                if (wrapper == null) {
                    wrapper = new DOM4JNodeWrapper(node, parent, index);
                    wrapper.nodeKind = Type.DOCUMENT;
                }
                break;
            case Node.COMMENT_NODE:
                wrapper = new DOM4JNodeWrapper(node, parent, index);
                wrapper.nodeKind = Type.COMMENT;
                break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                wrapper = new DOM4JNodeWrapper(node, parent, index);
                wrapper.nodeKind = Type.PROCESSING_INSTRUCTION;
                break;
            case Node.NAMESPACE_NODE:
                wrapper = new DOM4JNodeWrapper(node, parent, index);
                wrapper.nodeKind = Type.NAMESPACE;
                break;
            default:
                throw new IllegalArgumentException("Bad node type in dom4j! " + node.getClass() + " instance " + node);
        }
        wrapper.treeInfo = docWrapper;
        return wrapper;
    }

    @Override
    public DOM4JDocumentWrapper getTreeInfo() {
        return (DOM4JDocumentWrapper)treeInfo;
    }

    /**
     * Get the underlying DOM4J node, to implement the VirtualNode interface
     */

    @Override
    public Node getUnderlyingNode() {
        return node;
    }

    /**
     * Return the type of node.
     *
     * @return one of the values Node.ELEMENT, Node.TEXT, Node.ATTRIBUTE, etc.
     */

    @Override
    public int getNodeKind() {
        return nodeKind;
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
        return getTreeInfo().getSystemId();
    }

    @Override
    public void setSystemId(String uri) {
        getTreeInfo().setSystemId(uri);
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
        if (other instanceof SiblingCountingNode) {
            return Navigator.compareOrder(this, (SiblingCountingNode) other);
        } else {
            // it must be a namespace node
            return -other.compareOrder(this);
        }
    }

    @Override
    public CharSequence getStringValueCS() {
        return getStringValue(node);
    }

    private static String getStringValue(Node node) {

        Short nodeType = node.getNodeType();
        switch (nodeType) {
            case Node.ELEMENT_NODE:
            case Node.DOCUMENT_NODE:
                return node.getStringValue();
            case Node.ATTRIBUTE_NODE:
            case Node.TEXT_NODE:
            case Node.CDATA_SECTION_NODE:
            case Node.COMMENT_NODE:
            case Node.PROCESSING_INSTRUCTION_NODE:
                return node.getText();
            case Node.NAMESPACE_NODE:
                return ((Namespace) node).getURI();
            default:
                return "";
        }
    }

    /**
     * Get the local part of the name of this node. This is the name after the ":" if any.
     *
     * @return the local part of the name. For an unnamed node, returns "".
     */

    @Override
    public String getLocalPart() {
        switch (nodeKind) {
            case Type.ELEMENT:
            case Type.ATTRIBUTE:
                return node.getName();
            case Type.TEXT:
            case Type.COMMENT:
            case Type.DOCUMENT:
                return "";
            case Type.PROCESSING_INSTRUCTION:
                return ((ProcessingInstruction) node).getTarget();
            case Type.NAMESPACE:
                return ((Namespace) node).getPrefix();
            default:
                return null;
        }
    }

    /**
     * Get the prefix part of the name of this node. This is the name before the ":" if any.
     * (Note, this method isn't required as part of the NodeInfo interface.)
     *
     * @return the prefix part of the name. For an unnamed node, return an empty string.
     */

    @Override
    public String getPrefix() {
        switch (nodeKind) {
            case Type.ELEMENT:
                return ((Element) node).getNamespacePrefix();
            case Type.ATTRIBUTE:
                return ((Attribute) node).getNamespacePrefix();
            default:
                return "";
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
        switch (nodeKind) {
            case Type.ELEMENT:
                return ((Element) node).getNamespaceURI();
            case Type.ATTRIBUTE:
                return ((Attribute) node).getNamespaceURI();
            default:
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
                return ((Element) node).getQualifiedName();
            case Type.ATTRIBUTE:
                return ((Attribute) node).getQualifiedName();
            case Type.PROCESSING_INSTRUCTION:
            case Type.NAMESPACE:
                return getLocalPart();
            default:
                return "";

        }
    }

    /**
     * Get the NodeInfo object representing the parent of this node
     */

    @Override
    public DOM4JNodeWrapper getParent() {
        if (parent == null) {
            Branch parenti = getInternalParent(node, (DOM4JNodeWrapper) treeInfo.getRootNode());
            if (parenti != null) {
                return parent = makeWrapper(parenti, getTreeInfo());
            }
        }
        return parent;
    }

    private static Branch getInternalParent(Node node, DOM4JNodeWrapper container) {
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            return null;
        }
        Element e = node.getParent();
        if (e != null) {
            return e;
        }
        Document d = node.getDocument();
        if (d != null) {
            return d;
        }
        return DOM4JDocumentWrapper.searchForParent((Branch) container.node, node);
    }

    /**
     * Get the index position of this node among its siblings (starting from 0)
     */
    @Override
    public int getSiblingPosition() {
        if (index == -1) {
            int ix = 0;
            getParent();
            AxisIterator iter;
            switch (nodeKind) {
                case Type.ELEMENT:
                case Type.TEXT:
                case Type.COMMENT:
                case Type.PROCESSING_INSTRUCTION:
//                    iter = parent.iterateAxis(Axis.ATTRIBUTE);
//                    break;
                {
                    final DOM4JNodeWrapper parent = getParent();
                    final List children;
                    if (parent.getNodeKind() == Type.DOCUMENT) {
                        children = ((Document) parent.node).content();
                    } else {
                        // Beware: dom4j content() contains Namespace nodes (which is broken)!
                        children = ((Element) parent.node).content();
                    }
                    for (final Object n : children) {
                        if (n == node) {
                            index = ix;
                            return index;
                        }
                        ix++;
                    }
                    throw new IllegalStateException("DOM4J node not linked to parent node");
                }
                case Type.ATTRIBUTE:
                    iter = parent.iterateAxis(AxisInfo.ATTRIBUTE);
                    break;
                case Type.NAMESPACE:
                    iter = parent.iterateAxis(AxisInfo.NAMESPACE);
                    break;
                default:
                    index = 0;
                    return index;
            }
            while (true) {
                NodeInfo n = iter.next();
                if (n == null) {
                    break;
                }
                if (n.equals(this)) {
                    index = ix;
                    return index;
                }
                ix++;
            }
            throw new IllegalStateException("DOM4J node not linked to parent node");
        }
        return index;
    }

    /**
     * Return an iteration over the nodes reached by the given axis from this node
     *
     * //@param axisNumber the axis to be used
     * @param nodeTest A pattern to be matched by the returned nodes
     * @return a SequenceIterator that scans the nodes reached by the axis in turn.
     */

//    public AxisIterator iterateAxis(byte axisNumber, NodeTest nodeTest) {
//        switch (axisNumber) {
//            case AxisInfo.ANCESTOR:
//                if (nodeKind == Type.DOCUMENT) {
//                    return EmptyIterator.ofNodes();
//                }
//                return new Navigator.AxisFilter(
//                        new Navigator.AncestorEnumeration(this, false),
//                        nodeTest);
//
//            case AxisInfo.ANCESTOR_OR_SELF:
//                if (nodeKind == Type.DOCUMENT) {
//                    return Navigator.filteredSingleton(this, nodeTest);
//                }
//                return new Navigator.AxisFilter(
//                        new Navigator.AncestorEnumeration(this, true),
//                        nodeTest);
//
//            case AxisInfo.ATTRIBUTE:
//                if (nodeKind != Type.ELEMENT) {
//                    return EmptyIterator.ofNodes();
//                }
//                return new Navigator.AxisFilter(
//                        new AttributeEnumeration(this),
//                        nodeTest);
//
//            case AxisInfo.CHILD:
//                if (hasChildNodes()) {
//                    return new Navigator.AxisFilter(
//                            new ChildEnumeration(this, true, true),
//                            nodeTest);
//                } else {
//                    return EmptyIterator.ofNodes();
//                }
//
//            case AxisInfo.DESCENDANT:
//                if (hasChildNodes()) {
//                    return new Navigator.AxisFilter(
//                            (new TreeWalker()).newDescendantAxisIterator(this, false, nodeTest),//new Navigator.DescendantEnumeration(this, false, true),
//                            nodeTest);
//                } else {
//                    return EmptyIterator.ofNodes();
//                }
//
//            case AxisInfo.DESCENDANT_OR_SELF:
//                return new Navigator.AxisFilter(
//                        (new TreeWalker()).newDescendantAxisIterator(this, true, nodeTest),//new Navigator.DescendantEnumeration(this, true, true),
//                        nodeTest);
//
//            case AxisInfo.FOLLOWING:
//                return new Navigator.AxisFilter(
//                        new Navigator.FollowingEnumeration(this),
//                        nodeTest);
//
//            case AxisInfo.FOLLOWING_SIBLING:
//                switch (nodeKind) {
//                    case Type.DOCUMENT:
//                    case Type.ATTRIBUTE:
//                    case Type.NAMESPACE:
//                        return EmptyIterator.ofNodes();
//                    default:
//                        return new Navigator.AxisFilter(
//                                new ChildEnumeration(this, false, true),
//                                nodeTest);
//                }
//
//            case AxisInfo.NAMESPACE:
//                if (nodeKind != Type.ELEMENT) {
//                    return EmptyIterator.ofNodes();
//                }
//                return NamespaceNode.makeIterator(this, nodeTest);
//
//            case AxisInfo.PARENT:
//                getParent();
//                return Navigator.filteredSingleton(parent, nodeTest);
//
//            case AxisInfo.PRECEDING:
//                return new Navigator.AxisFilter(
//                        new Navigator.PrecedingEnumeration(this, false),
//                        nodeTest);
//
//            case AxisInfo.PRECEDING_SIBLING:
//                switch (nodeKind) {
//                    case Type.DOCUMENT:
//                    case Type.ATTRIBUTE:
//                    case Type.NAMESPACE:
//                        return EmptyIterator.ofNodes();
//                    default:
//                        return new Navigator.AxisFilter(
//                                new ChildEnumeration(this, false, false),
//                                nodeTest);
//                }
//
//            case AxisInfo.SELF:
//                return Navigator.filteredSingleton(this, nodeTest);
//
//            case AxisInfo.PRECEDING_OR_ANCESTOR:
//                return new Navigator.AxisFilter(
//                        new Navigator.PrecedingEnumeration(this, true),
//                        nodeTest);
//
//            default:
//                throw new IllegalArgumentException("Unknown axis number " + axisNumber);
//        }
//    }
    @Override
    protected AxisIterator iterateAttributes(Predicate<? super NodeInfo> nodeTest) {
        return new Navigator.AxisFilter(
                new AttributeEnumeration(this),
                nodeTest);
    }

    @Override
    protected AxisIterator iterateChildren(Predicate<? super NodeInfo> nodeTest) {
        if (hasChildNodes()) {
            return new Navigator.AxisFilter(
                    new ChildEnumeration(this, true, true),
                    nodeTest);
        } else {
            return EmptyIterator.ofNodes();
        }
    }

    @Override
    protected AxisIterator iterateSiblings(Predicate<? super NodeInfo> nodeTest, boolean forwards) {
        return new Navigator.AxisFilter(
                new ChildEnumeration(this, false, forwards),
                nodeTest);
    }

    @Override
    protected AxisIterator iterateDescendants(Predicate<? super NodeInfo> nodeTest, boolean includeSelf) {
        if (includeSelf) {
            return new SteppingNavigator.DescendantAxisIterator<>(this, true, nodeTest);

        } else {
            if (hasChildNodes()) {
                return new SteppingNavigator.DescendantAxisIterator<>(this, false, nodeTest);
            } else {
                return EmptyIterator.ofNodes();
            }

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
        if (nodeKind == Type.ELEMENT) {
            for (Object o : ((Element) node).attributes()) {
                Attribute att = (Attribute) o;
                if (att.getName().equals(local) && att.getNamespaceURI().equals(uri)) {
                    return att.getValue();
                }
            }
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
        return treeInfo.getRootNode();
    }

    /**
     * Determine whether the node has any children.
     * <p>Note: the result is equivalent to
     * <code>getEnumeration(Axis.CHILD, AnyNodeTest.getInstance()).hasNext()</code></p>
     */

    @Override
    public boolean hasChildNodes() {
        switch (nodeKind) {
            case Type.DOCUMENT:
                return true;
            case Type.ELEMENT:
                // Beware: dom4j content() contains Namespace nodes (which is broken)!
                return ((Branch) node).nodeCount() > 0;
            default:
                return false;
        }
    }

    /**
     * Get a character string that uniquely identifies this node.
     * Note: a.isSameNode(b) if and only if generateId(a)==generateId(b)
     *
     * @param buffer a Buffer to contain a string that uniquely identifies this node, across all
     *               documents
     */

    @Override
    public void generateId(FastStringBuffer buffer) {
        Navigator.appendSequentialKey(this, buffer, true);
        //buffer.append(Navigator.getSequentialKey(this));
    }

    @Override
    public DOM4JNodeWrapper getNextSibling() {
        Branch parenti = (Branch) getParent().node;
        int count = parenti.nodeCount();
        int i = parenti.indexOf(node);
        i++;
        if (i < count) {
            return makeWrapper(parenti.node(i), getTreeInfo());
        }
        return null;
    }


    @Override
    public DOM4JNodeWrapper getPreviousSibling() {
        Branch parenti = (Branch) getParent().node;
        int i = parenti.indexOf(node);
        i--;
        if (i >= 0) {
            return makeWrapper(parenti.node(i), getTreeInfo());
        }
        return null;
    }


    @Override
    public DOM4JNodeWrapper getFirstChild() {
        Node nodei = node;
        if (nodei.hasContent()) {
            Node child;
            int count = ((Branch) nodei).nodeCount();
            for (int i = 0; i < count; i++) {
                child = ((Branch) nodei).node(i);
                if (child.getNodeType() != Node.NAMESPACE_NODE && child.getNodeType() != Node.ATTRIBUTE_NODE) {
                    return makeWrapper(child, getTreeInfo(), this, 0);
                }
            }
        }
        return null;
    }

    @Override
    public DOM4JNodeWrapper getSuccessorElement(DOM4JNodeWrapper anchor, String uri, String local) {
        Node stop = anchor == null ? null : anchor.node;
        Node next = node;
        do {
            next = getFollowingNode(next, stop, (DOM4JNodeWrapper) treeInfo.getRootNode());
        } while (next != null &&
                !(next.getNodeType() == Node.ELEMENT_NODE &&
                        (uri == null || uri.equals(((Element) next).getNamespaceURI())) &&
                        (local == null || local.equals(next.getName()))));
        if (next == null) {
            return null;
        } else {
            return makeWrapper(next, getTreeInfo());
        }
    }

    /**
     * Get the following node in an iteration of descendants
     *
     * @param start  the start node
     * @param anchor the node marking the root of the subtree within which navigation takes place (may be null)
     * @param container the wrapper for the document node
     * @return the next node in document order after the start node, excluding attributes and namespaces
     */

    private static Node getFollowingNode(Node start, Node anchor, DOM4JNodeWrapper container) {
        if (start.hasContent()) {
            Node child;
            int count = ((Branch) start).nodeCount();
            for (int i = 0; i < count; i++) {
                child = ((Branch) start).node(i);
                if (child.getNodeType() != Node.NAMESPACE_NODE && child.getNodeType() != Node.ATTRIBUTE_NODE) {
                    return child;
                }
            }
        }
        if (start == anchor) {
            return null;
        }
        Node p = start;
        while (true) {
            Branch q = getInternalParent(p, container);
            if (q == null) {
                return null;
            }
            int i = q.indexOf(p) + 1;
            if (i < q.nodeCount()) {
                return q.node(i);
            }
            if (q == anchor) {
                return null;
            }
            p = q;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////
    // Axis enumeration classes
    ///////////////////////////////////////////////////////////////////////////////


    private final class AttributeEnumeration implements AxisIterator {

        private Iterator<Attribute> atts;
        private int ix = 0;
        private DOM4JNodeWrapper start;

        AttributeEnumeration(DOM4JNodeWrapper start) {
            this.start = start;
            atts = ((Element) start.node).attributes().iterator();
        }

        @Override
        public final NodeInfo next() {
            if (atts.hasNext()) {
                return makeWrapper(atts.next(), getTreeInfo(), start, ix++);
            } else {
                return null;
            }
        }


    }  // end of class AttributeEnumeration


    /**
     * The class ChildEnumeration handles not only the child axis, but also the
     * following-sibling and preceding-sibling axes. It can also iterate the children
     * of the start node in reverse order, something that is needed to support the
     * preceding and preceding-or-ancestor axes (the latter being used by xsl:number)
     */

    private final class ChildEnumeration implements AxisIterator {

        private DOM4JNodeWrapper start;
        private DOM4JNodeWrapper commonParent;
        private ListIterator<Node> children;
        private int ix = 0;
        private boolean downwards;  // iterate children of start node (not siblings)
        private boolean forwards;   // iterate in document order (not reverse order)

        public ChildEnumeration(DOM4JNodeWrapper start,
                                boolean downwards, boolean forwards) {
            this.start = start;
            this.downwards = downwards;
            this.forwards = forwards;

            if (downwards) {
                commonParent = start;
            } else {
                commonParent = start.getParent();
            }

            if (commonParent.getNodeKind() == Type.DOCUMENT) {
                children = ((Document) commonParent.node).content().listIterator();
            } else {
                children = ((Element) commonParent.node).content().listIterator();
            }

            if (downwards) {
                if (!forwards) {
                    // backwards enumeration: go to the end
                    while (children.hasNext()) {
                        children.next();
                        ix++;
                    }
                }
            } else {
                ix = start.getSiblingPosition();
                // find the start node among the list of siblings
                if (forwards) {
                    for (int i = 0; i <= ix; i++) {
                        children.next();
                    }
                    ix++;
                } else {
                    for (int i = 0; i < ix; i++) {
                        children.next();
                    }
                    ix--;
                }
            }
        }


        @Override
        public NodeInfo next() {
            if (forwards) {
                if (children.hasNext()) {
                    Node nextChild = children.next();
                    if (nextChild instanceof DocumentType || nextChild instanceof Namespace) {
                        ix++; // increment anyway so that makeWrapper() passes the correct index)
                        return next();
                    }
                    if (nextChild instanceof Entity) {
                        throw new IllegalStateException("Unexpanded entity in DOM4J tree");
                    } else {
                        return makeWrapper(nextChild, getTreeInfo(), commonParent, ix++);
                    }
                } else {
                    return null;
                }
            } else {    // backwards
                if (children.hasPrevious()) {
                    Node nextChild = children.previous();
                    if (nextChild instanceof DocumentType || nextChild instanceof Namespace) {
                        ix--; // decrement anyway so that makeWrapper() passes the correct index)
                        return next();
                    }
                    if (nextChild instanceof Entity) {
                        throw new IllegalStateException("Unexpanded entity in DOM4J tree");
                    } else {
                        return makeWrapper(nextChild, getTreeInfo(), commonParent, ix--);
                    }
                } else {
                    return null;
                }
            }
        }

    } // end of class ChildEnumeration


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
        if (!(other instanceof DOM4JNodeWrapper)) {
            return false;
        }
        DOM4JNodeWrapper ow = (DOM4JNodeWrapper) other;
        if (node instanceof Namespace) {
            return getLocalPart().equals(ow.getLocalPart()) && getParent().equals(ow.getParent());
        }
        return node.equals(ow.node);
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
        if (node instanceof Element) {
            final Element elem = (Element) node;
            final List<Namespace> namespaces = elem.declaredNamespaces();

            if (namespaces == null || namespaces.isEmpty()) {
                return NamespaceBinding.EMPTY_ARRAY;
            }
            final int count = namespaces.size();
            if (count == 0) {
                return NamespaceBinding.EMPTY_ARRAY;
            } else {
                NamespaceBinding[] result = buffer == null || count > buffer.length ? new NamespaceBinding[count] : buffer;
                int n = 0;
                for (Namespace namespace : namespaces) {
                    final String prefix = namespace.getPrefix();
                    final String uri = namespace.getURI();

                    result[n++] = new NamespaceBinding(prefix, uri);
                }
                if (count < result.length) {
                    result[count] = null;
                }
                return result;
            }
        } else {
            return null;
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
                NamespaceMap nsMap = getParent().getNodeKind() == Type.ELEMENT
                        ? getParent().getAllNamespaces()
                        : NamespaceMap.emptyMap();
                Element elem = (Element) node;
                List<Namespace> addl = elem.declaredNamespaces();
                Namespace ns = elem.getNamespace();
                String prefix = ns.getPrefix();
                String uri = ns.getURI();
                nsMap = nsMap.bind(prefix, uri);
                if (!addl.isEmpty()) {
                    for (Namespace ns2 : addl) {
                        nsMap = nsMap.bind(ns2.getPrefix(), ns2.getURI());
                    }
                }
                return inScopeNamespaces = nsMap;
            }
        } else {
            return null;
        }
    }
}

