////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.xom;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.util.SteppingNavigator;
import net.sf.saxon.tree.util.SteppingNode;
import net.sf.saxon.tree.wrapper.AbstractNodeWrapper;
import net.sf.saxon.tree.wrapper.SiblingCountingNode;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.UntypedAtomicValue;
import nu.xom.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A node in the XML parse tree representing an XML element, character content,
 * or attribute.
 * <p>This is the implementation of the NodeInfo interface used as a wrapper for
 * XOM nodes.</p>
 *
 * @author Michael H. Kay
 * @author Wolfgang Hoschek (ported net.sf.saxon.jdom to XOM)
 */

public class XOMNodeWrapper extends AbstractNodeWrapper implements SiblingCountingNode, SteppingNode<XOMNodeWrapper> {

    protected Node node;

    protected short nodeKind;

    private XOMNodeWrapper parent; // null means unknown

    protected XOMDocumentWrapper docWrapper;

    //represents the index position in it's parent child nodes
    protected int index; // -1 means unknown

    private NamespaceMap inScopeNamespaces = null;


    /**
     * This constructor is protected: nodes should be created using the wrap
     * factory method on the XOMDocumentWrapper class
     *
     * @param node   The XOM node to be wrapped
     * @param parent The XOMNodeWrapper that wraps the parent of this node
     * @param index  Position of this node among its siblings
     */
    XOMNodeWrapper(Node node, XOMNodeWrapper parent, int index) {
        short kind;
        if (node instanceof Element) {
            kind = Type.ELEMENT;
        } else if (node instanceof Text) {
            kind = Type.TEXT;
        } else if (node instanceof Attribute) {
            kind = Type.ATTRIBUTE;
        } else if (node instanceof Comment) {
            kind = Type.COMMENT;
        } else if (node instanceof ProcessingInstruction) {
            kind = Type.PROCESSING_INSTRUCTION;
        } else if (node instanceof Document) {
            kind = Type.DOCUMENT;
        } else {
            throwIllegalNode(node); // moved out of fast path to enable better inlining
            return; // keep compiler happy
        }
        nodeKind = kind;
        this.node = node;
        this.parent = parent;
        this.index = index;
    }

    /**
     * Factory method to wrap a XOM node with a wrapper that implements the
     * Saxon NodeInfo interface.
     *
     * @param node       The XOM node
     * @param docWrapper The wrapper for the Document containing this node
     * @return The new wrapper for the supplied node
     */
    protected final XOMNodeWrapper makeWrapper(Node node, XOMDocumentWrapper docWrapper) {
        return makeWrapper(node, docWrapper, null, -1);
    }

    /**
     * Factory method to wrap a XOM node with a wrapper that implements the
     * Saxon NodeInfo interface.
     *
     * @param node       The XOM node
     * @param docWrapper The wrapper for the Document containing this node
     * @param parent     The wrapper for the parent of the XOM node
     * @param index      The position of this node relative to its siblings
     * @return The new wrapper for the supplied node
     */

    protected final XOMNodeWrapper makeWrapper(Node node, XOMDocumentWrapper docWrapper,
                                               XOMNodeWrapper parent, int index) {

        if (node == docWrapper.node) {
            return docWrapper;
        }
        XOMNodeWrapper wrapper = new XOMNodeWrapper(node, parent, index);
        wrapper.docWrapper = docWrapper;
        wrapper.treeInfo = docWrapper;
        return wrapper;
    }

    private static void throwIllegalNode(/*@Nullable*/ Node node) {
        String str = node == null ?
                "NULL" :
                node.getClass() + " instance " + node;
        throw new IllegalArgumentException("Bad node type in XOM! " + str);
    }

    /**
     * Get the configuration
     */

    @Override
    public Configuration getConfiguration() {
        return docWrapper.getConfiguration();
    }

    /**
     * Get the underlying XOM node, to implement the VirtualNode interface
     */

    @Override
    public Node getUnderlyingNode() {
        return node;
    }


    /**
     * Get the name pool for this node
     *
     * @return the NamePool
     */

    @Override
    public NamePool getNamePool() {
        return docWrapper.getNamePool();
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
     * Get the typed value.
     *
     * @return the typed value. If requireSingleton is set to true, the result
     *         will always be an AtomicValue. In other cases it may be a Value
     *         representing a sequence whose items are atomic values.
     * @since 8.5
     */

    @Override
    public AtomicSequence atomize() {
        switch (getNodeKind()) {
            case Type.COMMENT:
            case Type.PROCESSING_INSTRUCTION:
                return new StringValue(getStringValueCS());
            default:
                return new UntypedAtomicValue(getStringValueCS());
        }
    }

    /**
     * Determine whether this is the same node as another node.
     * <p>Note: a.isSameNode(b) if and only if generateId(a)==generateId(b)</p>
     *
     * @return true if this Node object and the supplied Node object represent
     *         the same node in the tree.
     */

    public boolean equals(Object other) {
        // In XOM equality means identity
        return other instanceof XOMNodeWrapper && node == ((XOMNodeWrapper) other).node;
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
        return docWrapper.getBaseURI();
    }


    /**
     * Get the Base URI for the node, that is, the URI used for resolving a
     * relative URI contained in the node.
     */

    @Override
    public String getBaseURI() {
        return node.getBaseURI();
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
        if (other instanceof XOMNodeWrapper) {
            return compareOrderFast(node, ((XOMNodeWrapper) other).node);
        } else {
            // it must be a namespace node
            return -other.compareOrder(this);
        }
    }

    private static int compareOrderFast(Node first, Node second) {
        /*
           * Unfortunately we do not have a sequence number for each node at hand;
           * this would allow to turn the comparison into a simple sequence number
           * subtraction. Walking the entire tree and batch-generating sequence
           * numbers on the fly is no good option either. However, this rewritten
           * implementation turns out to be more than fast enough.
           */

        // assert first != null && second != null
        // assert first and second MUST NOT be namespace nodes
        if (first == second) {
            return 0;
        }

        ParentNode firstParent = first.getParent();
        ParentNode secondParent = second.getParent();
        if (firstParent == null) {
            if (secondParent != null) {
                return -1; // first node is the root
            }
            // both nodes are parentless, use arbitrary but fixed order:
            return first.hashCode() - second.hashCode();
        }

        if (secondParent == null) {
            return +1; // second node is the root
        }

        // do they have the same parent (common case)?
        if (firstParent == secondParent) {
            int i1 = firstParent.indexOf(first);
            int i2 = firstParent.indexOf(second);

            // note that attributes and namespaces are not children
            // of their own parent (i = -1).
            // attribute (if any) comes before child
            if (i1 != -1) {
                return (i2 != -1) ? i1 - i2 : +1;
            }
            if (i2 != -1) {
                return -1;
            }

            // assert: i1 == -1 && i2 == -1
            // i.e. both nodes are attributes
            Element elem = (Element) firstParent;
            for (int i = elem.getAttributeCount(); --i >= 0; ) {
                Attribute attr = elem.getAttribute(i);
                if (attr == second) {
                    return -1;
                }
                if (attr == first) {
                    return +1;
                }
            }
            throw new IllegalStateException("should be unreachable");
        }

        // find the depths of both nodes in the tree
        int depth1 = 0;
        int depth2 = 0;
        Node p1 = first;
        Node p2 = second;
        while (p1 != null) {
            depth1++;
            p1 = p1.getParent();
            if (p1 == second) {
                return +1;
            }
        }
        while (p2 != null) {
            depth2++;
            p2 = p2.getParent();
            if (p2 == first) {
                return -1;
            }
        }

        // move up one branch of the tree so we have two nodes on the same level
        p1 = first;
        while (depth1 > depth2) {
            p1 = p1.getParent();
            depth1--;
        }
        p2 = second;
        while (depth2 > depth1) {
            p2 = p2.getParent();
            depth2--;
        }

        // now move up both branches in sync until we find a common parent
        while (true) {
            firstParent = p1.getParent();
            secondParent = p2.getParent();
            if (firstParent == null || secondParent == null) {
                // both nodes are documentless, use arbitrary but fixed order
                // based on their root elements
                return p1.hashCode() - p2.hashCode();
                // throw new NullPointerException("XOM tree compare - internal error");
            }
            if (firstParent == secondParent) {
                return firstParent.indexOf(p1) - firstParent.indexOf(p2);
            }
            p1 = firstParent;
            p2 = secondParent;
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
        return node.getValue();
    }

    /**
     * Get the value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String.
     */

    @Override
    public CharSequence getStringValueCS() {
        return node.getValue();
    }

    /**
     * Get the local part of the name of this node. This is the name after the
     * ":" if any.
     *
     * @return the local part of the name. For an unnamed node, returns "".
     */

    @Override
    public String getLocalPart() {
        switch (nodeKind) {
            case Type.ELEMENT:
                return ((Element) node).getLocalName();
            case Type.ATTRIBUTE:
                return ((Attribute) node).getLocalName();
            case Type.PROCESSING_INSTRUCTION:
                return ((ProcessingInstruction) node).getTarget();
            default:
                return "";
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
     * Get the URI part of the name of this node. This is the URI corresponding
     * to the prefix, or the URI of the default namespace if appropriate.
     *
     * @return The URI of the namespace of this node. For an unnamed node, or
     *         for a node with an empty prefix, return an empty string.
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
     * Get the display name of this node. For elements and attributes this is
     * [prefix:]localname. For unnamed nodes, it is an empty string.
     *
     * @return The display name of this node. For a node with no name, return an
     *         empty string.
     */

    @Override
    public String getDisplayName() {
        switch (nodeKind) {
            case Type.ELEMENT:
                return ((Element) node).getQualifiedName();
            case Type.ATTRIBUTE:
                return ((Attribute) node).getQualifiedName();
            case Type.PROCESSING_INSTRUCTION:
                return ((ProcessingInstruction) node).getTarget();
            default:
                return "";
        }
    }

    /**
     * Get the NodeInfo object representing the parent of this node
     */

    @Override
    public XOMNodeWrapper getParent() {
        if (parent == null) {
            ParentNode p = node.getParent();
            if (p != null) {
                parent = makeWrapper(p, docWrapper);
            }
        }
        return parent;
    }

    @Override
    public XOMNodeWrapper getNextSibling() {
        ParentNode parenti = node.getParent();
        if (parenti == null) {
            return null;
        }
        int count = parenti.getChildCount();
        if (index != -1) {
            if ((index + 1) < count) {
                return makeWrapper(parenti.getChild(index + 1), docWrapper, parent, index + 1);
            } else {
                return null;
            }
        }
        index = parenti.indexOf(node);
        if (index + 1 < count) {
            return makeWrapper(parenti.getChild(index + 1), docWrapper, parent, index + 1);
        }
        return null;
    }

    @Override
    public XOMNodeWrapper getPreviousSibling() {
        ParentNode parenti = node.getParent();
        if (parenti == null) {
            return null;
        }
        if (index != -1) {
            if ((index - 1) > 0) {
                return makeWrapper(parenti.getChild(index - 1), docWrapper, parent, index - 1);
            } else {
                return null;
            }
        }
        index = parenti.indexOf(node);
        if (index - 1 > 0) {
            return makeWrapper(parenti.getChild(index - 1), docWrapper, parent, index - 1);
        }
        return null;
    }

    @Override
    public XOMNodeWrapper getFirstChild() {
        if (node.getChildCount() > 0) {
            for (int i = 0; i < node.getChildCount(); i++) {
                Node n = node.getChild(i);
                if (!(n instanceof DocType)) {
                    return makeWrapper(n, docWrapper, this, 0);
                }
            }
        }
        return null;
    }

    @Override
    public XOMNodeWrapper getSuccessorElement(XOMNodeWrapper anchor, String uri, String local) {
        Node stop = anchor == null ? null : anchor.node;
        Node next = node;
        do {
            next = getSuccessorNode(next, stop);
        } while (next != null &&
                !(next instanceof Element &&
                        (uri == null || uri.equals(((Element) next).getNamespaceURI())) &&
                        (local == null || local.equals(((Element) next).getLocalName()))));
        if (next == null) {
            return null;
        } else {
            return makeWrapper(next, docWrapper);
        }
    }

    /**
     * Get the following node in an iteration of descendants
     *
     * @param start  the start node
     * @param anchor the node marking the root of the subtree within which navigation takes place (may be null)
     * @return the next node in document order after the start node, excluding attributes and namespaces
     */

    private static Node getSuccessorNode(Node start, Node anchor) {
        if (start.getChildCount() > 0) {
            return start.getChild(0);
        }
        if (start == anchor) {
            return null;
        }
        Node p = start;
        while (true) {
            ParentNode q = p.getParent();
            if (q == null) {
                return null;
            }
            int i = q.indexOf(p) + 1;
            if (i < q.getChildCount()) {
                return q.getChild(i);
            }
            if (q == anchor) {
                return null;
            }
            p = q;
        }
    }

    /**
     * Get the index position of this node among its siblings (starting from 0)
     */

    @Override
    public int getSiblingPosition() {
        // This method is used only to support generate-id()
        if (index != -1) {
            return index;
        }
        switch (nodeKind) {
            case Type.ATTRIBUTE: {
                Attribute att = (Attribute) node;
                Element p = (Element) att.getParent();
                if (p == null) {
                    return 0;
                }
                for (int i = p.getAttributeCount(); --i >= 0; ) {
                    if (p.getAttribute(i) == att) {
                        index = i;
                        return i;
                    }
                }
                throw new IllegalStateException("XOM node not linked to parent node");
            }

            default: {
                ParentNode p = node.getParent();
                int i = p == null ? 0 : p.indexOf(node);
                if (i == -1) {
                    throw new IllegalStateException("XOM node not linked to parent node");
                }
                index = i;
                return index;
            }
        }
    }

    @Override
    protected AxisIterator iterateAttributes(Predicate<? super NodeInfo> nodeTest) {
        return new Navigator.AxisFilter(
                new AttributeAxisIterator(this, nodeTest),
                nodeTest);
    }

    @Override
    protected AxisIterator iterateChildren(Predicate<? super NodeInfo> nodeTest) {
        if (hasChildNodes()) {
            return new Navigator.AxisFilter(
                    new ChildAxisIterator(this, true, true, nodeTest),
                    nodeTest);
        } else {
            return EmptyIterator.ofNodes();
        }
    }

    @Override
    protected AxisIterator iterateSiblings(Predicate<? super NodeInfo> nodeTest, boolean forwards) {
        return new Navigator.AxisFilter(
                new ChildAxisIterator(this, false, forwards, nodeTest),
                nodeTest);
    }

    @Override
    protected AxisIterator iterateDescendants(Predicate<? super NodeInfo> nodeTest, boolean includeSelf) {
        if (includeSelf) {
            return new SteppingNavigator.DescendantAxisIterator<>(
                    this, true, nodeTest);

        } else {
            if (hasChildNodes()) {
                return new SteppingNavigator.DescendantAxisIterator<>(
                        this, false, nodeTest);
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
            Attribute att = ((Element) node).getAttribute(local, uri);
            if (att != null) {
                return att.getValue();
            }
        }
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
        return docWrapper;
    }

    /**
     * Determine whether the node has any children.
     * <p>Note: the result is equivalent to
     * <code>getEnumeration(Axis.CHILD, AnyNodeTest.getInstance()).hasNext()</code></p>
     */

    @Override
    public boolean hasChildNodes() {
        return node.getChildCount() > 0;
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
        if (node instanceof Element) {
            Element elem = (Element) node;
            int size = elem.getNamespaceDeclarationCount();
            if (size == 0) {
                return NamespaceBinding.EMPTY_ARRAY;
            }
            List<NamespaceBinding> list = new ArrayList<>(size);
            for (int i = 0, j = 0; i < size; i++) {
                String prefix = elem.getNamespacePrefix(i);
                String uri = elem.getNamespaceURI(prefix);
                if (prefix.isEmpty() && uri.isEmpty()) {
                    // include the default namespace undeclaration xmlns="" only if needed
                    ParentNode parent = node.getParent();
                    if (parent instanceof Element && !((Element)parent).getNamespaceURI("").isEmpty()) {
                        list.add(new NamespaceBinding(prefix, uri));
                    }
                } else {
                    list.add(new NamespaceBinding(prefix, uri));
                }
            }
            return list.toArray(NamespaceBinding.EMPTY_ARRAY);
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
                NodeInfo parent = getParent();
                NamespaceMap nsMap = parent != null && parent.getNodeKind() == Type.ELEMENT
                        ? parent.getAllNamespaces()
                        : NamespaceMap.emptyMap();
                Element elem = (Element) node;
                int size = elem.getNamespaceDeclarationCount();
                for (int i = 0; i < size; i++) {
                    String prefix = elem.getNamespacePrefix(i);
                    String uri = elem.getNamespaceURI(prefix);
                    nsMap = nsMap.bind(prefix, uri);
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
        return getNodeKind() == Type.ATTRIBUTE && ((Attribute) node).getType() == Attribute.Type.ID;
    }

    /**
     * Determine whether this node has the is-idref property
     *
     * @return true if the node is an IDREF or IDREFS element or attribute
     */

    @Override
    public boolean isIdref() {
        return getNodeKind() == Type.ATTRIBUTE && (
                ((Attribute) node).getType() == Attribute.Type.IDREF ||
                        ((Attribute) node).getType() == Attribute.Type.IDREFS);
    }


    ///////////////////////////////////////////////////////////////////////////////
    // Axis enumeration classes
    ///////////////////////////////////////////////////////////////////////////////

    /**
     * Handles the attribute axis in a rather direct manner.
     */
    private final class AttributeAxisIterator implements AxisIterator {

        private XOMNodeWrapper start;

        private int cursor;

        private Predicate<? super NodeInfo> nodeTest;

        AttributeAxisIterator(XOMNodeWrapper start, Predicate<? super NodeInfo> test) {
            // use lazy instead of eager materialization (performance)
            this.start = start;
            if (test == AnyNodeTest.getInstance()) {
                test = null;
            }
            nodeTest = test;
            cursor = 0;
        }


        @Override
        public NodeInfo next() {
            NodeInfo curr;
            do { // until we find a match
                curr = advance();
            } while (curr != null && nodeTest != null && !nodeTest.test(curr));
            return curr;
        }

        private NodeInfo advance() {
            Element elem = (Element) start.node;
            if (cursor == elem.getAttributeCount()) {
                return null;
            }
            NodeInfo curr = makeWrapper(elem.getAttribute(cursor), docWrapper, start, cursor);
            cursor++;
            return curr;
        }


    } // end of class AttributeAxisIterator

    /**
     * The class ChildAxisIterator handles not only the child axis, but also the
     * following-sibling and preceding-sibling axes. It can also iterate the
     * children of the start node in reverse order, something that is needed to
     * support the preceding and preceding-or-ancestor axes (the latter being
     * used by xsl:number)
     */
    private final class ChildAxisIterator implements AxisIterator {

        private XOMNodeWrapper commonParent;
        private int ix;
        private boolean forwards; // iterate in document order (not reverse order)

        private ParentNode par;
        private int cursor;

        private Predicate<? super NodeInfo> nodeTest;

        private ChildAxisIterator(XOMNodeWrapper start, boolean downwards, boolean forwards, Predicate<? super NodeInfo> test) {
            this.forwards = forwards;

            if (test == AnyNodeTest.getInstance()) {
                test = null;
            }
            nodeTest = test;

            commonParent = downwards ? start : start.getParent();

            par = (ParentNode) commonParent.node;
            if (downwards) {
                ix = forwards ? 0 : par.getChildCount();
            } else {
                // find the start node among the list of siblings
                ix = par.indexOf(start.node);
                if (forwards) {
                    ix++;
                }
            }
            cursor = ix;
            if (!downwards && !forwards) {
                ix--;
            }
        }


        @Override
        public NodeInfo next() {
            NodeInfo curr;
            do { // until we find a match
                curr = advance();
            } while (curr != null && nodeTest != null && !nodeTest.test(curr));
            return curr;
        }

        private NodeInfo advance() {
            Node nextChild;
            do {
                if (forwards) {
                    if (cursor == par.getChildCount()) {
                        return null;
                    }
                    nextChild = par.getChild(cursor++);
                } else { // backwards
                    if (cursor == 0) {
                        return null;
                    }
                    nextChild = par.getChild(--cursor);
                }
            } while (nextChild instanceof DocType);
            // DocType is not an XPath node; can occur for /child::node()

            NodeInfo curr = makeWrapper(nextChild, docWrapper, commonParent, ix);
            ix += forwards ? 1 : -1;
            return curr;
        }

    }


}

