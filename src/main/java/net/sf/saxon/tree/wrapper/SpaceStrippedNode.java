////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.wrapper;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.Stripper;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.ComplexType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.Whitespace;

import java.util.function.Predicate;


/**
 * A StrippedNode is a view of a node, in a virtual tree that has whitespace
 * text nodes stripped from it. All operations on the node produce the same result
 * as operations on the real underlying node, except that iterations over the axes
 * take care to skip whitespace-only text nodes that are supposed to be stripped.
 * Note that this class is only used in cases where a pre-built tree is supplied as
 * the input to a transformation, and where the stylesheet does whitespace stripping;
 * if a SAXSource or StreamSource is supplied, whitespace is stripped as the tree
 * is built.
 */

public class SpaceStrippedNode extends AbstractVirtualNode implements WrappingFunction {

    protected SpaceStrippedNode() {
    }

    /**
     * This constructor is protected: nodes should be created using the makeWrapper
     * factory method
     *
     * @param node   The node to be wrapped
     * @param parent The StrippedNode that wraps the parent of this node
     */

    protected SpaceStrippedNode(NodeInfo node, SpaceStrippedNode parent) {
        this.node = node;
        this.parent = parent;
    }

    /**
     * Factory method to wrap a node with a wrapper that implements the Saxon
     * NodeInfo interface.
     *
     * @param node       The underlying node
     * @param docWrapper The wrapper for the document node (must be supplied)
     * @param parent     The wrapper for the parent of the node (null if unknown)
     * @return The new wrapper for the supplied node
     */

    /*@NotNull*/
    protected static SpaceStrippedNode makeWrapper(NodeInfo node,
                                                   SpaceStrippedDocument docWrapper,
                                                   SpaceStrippedNode parent) {
        SpaceStrippedNode wrapper = new SpaceStrippedNode(node, parent);
        wrapper.docWrapper = docWrapper;
        return wrapper;
    }

    /**
     * Factory method to wrap a node within the same document as this node with a VirtualNode
     *
     * @param node   The underlying node
     * @param parent The wrapper for the parent of the node (null if unknown)
     * @return The new wrapper for the supplied node
     */

    /*@NotNull*/
    @Override
    public VirtualNode makeWrapper(NodeInfo node, VirtualNode parent) {
        SpaceStrippedNode wrapper = new SpaceStrippedNode(node, (SpaceStrippedNode) parent);
        wrapper.docWrapper = this.docWrapper;
        return wrapper;
    }

    /**
     * Ask whether a node is preserved after whitespace stripping
     * @param node the node in question
     * @param docWrapper the root of the space-stripped virtual tree
     * @param actualParent the (real) parent of the node in question
     * @return true if the node survives whitespace-stripping
     */

    public static boolean isPreservedNode(NodeInfo node, SpaceStrippedDocument docWrapper, NodeInfo actualParent) {

        // Non-text nodes, non-whitespace nodes, and parentless nodes are preserved
        if (node.getNodeKind() != Type.TEXT || actualParent == null || !Whitespace.isWhite(node.getStringValueCS())) {
            return true;
        }

        // if the node has a simple type annotation, it is preserved
        SchemaType type = actualParent.getSchemaType();
        if (type.isSimpleType() || ((ComplexType) type).isSimpleContent()) {
            return true;
        }

        // if there is an ancestor with xml:space="preserve", it is preserved
        if (docWrapper.containsPreserveSpace()) {
            NodeInfo p = actualParent;
            // the document contains one or more xml:space="preserve" attributes, so we need to see
            // if one of them is on an ancestor of this node
            while (p.getNodeKind() == Type.ELEMENT) {
                String val = p.getAttributeValue(NamespaceConstant.XML, "space");
                if (val != null) {
                    if ("preserve".equals(val)) {
                        return true;
                    } else if ("default".equals(val)) {
                        break;
                    }
                }
                p = p.getParent();
            }
        }

        // if there is an ancestor whose type has an assertion, it is preserved
        if (docWrapper.containsAssertions()) {
            NodeInfo p = actualParent;
            // the document contains one or more xml:space="preserve" attributes, so we need to see
            // if one of them is on an ancestor of this node
            while (p.getNodeKind() == Type.ELEMENT) {
                SchemaType t = p.getSchemaType();
                if (t instanceof ComplexType && ((ComplexType) t).hasAssertions()) {
                    return true;
                }
                p = p.getParent();
            }
        }

        // otherwise it depends on xsl:strip-space
        try {
            int preserve = docWrapper.getStrippingRule().isSpacePreserving(NameOfNode.makeName(actualParent), null);
            return preserve == Stripper.ALWAYS_PRESERVE;
        } catch (XPathException e) {
            // Ambiguity between strip-space and preserve-space. Take the recovery action.
            return true;
        }

    }

    /**
     * Get the typed value.
     *
     * @return the typed value.
     * @since 8.5
     */

    @Override
    public AtomicSequence atomize() throws XPathException {
        if (getNodeKind() == Type.ELEMENT) {
            return getSchemaType().atomize(this);
        } else {
            return node.atomize();
        }
    }

    /**
     * Determine whether this is the same node as another node.
     * <p>Note: a.isSameNode(b) if and only if generateId(a)==generateId(b)</p>
     *
     * @return true if this Node object and the supplied Node object represent the
     *         same node in the tree.
     */

    public boolean equals(Object other) {
        if (other instanceof SpaceStrippedNode) {
            return node.equals(((SpaceStrippedNode) other).node);
        } else {
            return node.equals(other);
        }
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
        if (other instanceof SpaceStrippedNode) {
            return node.compareOrder(((SpaceStrippedNode) other).node);
        } else {
            return node.compareOrder(other);
        }
    }

    /**
     * Get the value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String.
     */

    @Override
    public CharSequence getStringValueCS() {
        // Might not be the same as the string value of the underlying node because of space stripping
        switch (getNodeKind()) {
            case Type.DOCUMENT:
            case Type.ELEMENT:
                AxisIterator iter = iterateAxis(AxisInfo.DESCENDANT, NodeKindTest.makeNodeKindTest(Type.TEXT));
                FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C64);
                while (true) {
                    NodeInfo it = iter.next();
                    if (it == null) {
                        break;
                    }
                    sb.cat(it.getStringValueCS());
                }
                return sb.condense();
            default:
                return node.getStringValueCS();
        }
    }

    /**
     * Get the NodeInfo object representing the parent of this node
     */

    /*@Nullable*/
    @Override
    public NodeInfo getParent() {
        if (parent == null) {
            NodeInfo realParent = node.getParent();
            if (realParent != null) {
                parent = makeWrapper(realParent, (SpaceStrippedDocument) docWrapper, null);
            }
        }
        return parent;
    }

    @Override
    public AxisIterator iterateAxis(int axisNumber, Predicate<? super NodeInfo> nodeTest) {
        if (nodeTest instanceof NodeTest &&
                ((NodeTest)nodeTest).getUType().intersection(UType.TEXT) == UType.VOID ||
                axisNumber == AxisInfo.ATTRIBUTE || axisNumber == AxisInfo.NAMESPACE) {
            // iteration does not include text nodes, so no stripping needed
            return new WrappingIterator(node.iterateAxis(axisNumber, nodeTest), this, getParentForAxis(axisNumber));
        } else {
            return new StrippingIterator(node.iterateAxis(axisNumber, nodeTest), getParentForAxis(axisNumber));
        }
    }

    /**
     * Return an iteration over the nodes reached by the given axis from this node
     *
     * @param axisNumber the axis to be used
     * @return a SequenceIterator that scans the nodes reached by the axis in turn.
     */

    /*@Nullable*/
    @Override
    public AxisIterator iterateAxis(int axisNumber) {
        switch (axisNumber) {
            case AxisInfo.ATTRIBUTE:
            case AxisInfo.NAMESPACE:
                return new WrappingIterator(node.iterateAxis(axisNumber), this, this);
            case AxisInfo.CHILD:
                return new StrippingIterator(node.iterateAxis(axisNumber), this);
            case AxisInfo.FOLLOWING_SIBLING:
            case AxisInfo.PRECEDING_SIBLING:
                SpaceStrippedNode parent = (SpaceStrippedNode) getParent();
                if (parent == null) {
                    return EmptyIterator.ofNodes();
                } else {
                    return new StrippingIterator(node.iterateAxis(axisNumber), parent);
                }
            default:
                return new StrippingIterator(node.iterateAxis(axisNumber), null);
        }
    }

    private SpaceStrippedNode getParentForAxis(int axisNumber) {
        switch (axisNumber) {
            case AxisInfo.CHILD:
            case AxisInfo.ATTRIBUTE:
            case AxisInfo.NAMESPACE:
                return this;
            case AxisInfo.FOLLOWING_SIBLING:
            case AxisInfo.PRECEDING_SIBLING:
                return (SpaceStrippedNode)getParent();
            default:
                return null;
        }
    }

    /**
     * Copy this node to a given outputter (deep copy)
     */

    @Override
    public void copy(Receiver out, int copyOptions, Location locationId) throws XPathException {
        // The underlying code does not do whitespace stripping. So we need to interpose
        // a stripper. Moreover, if the node is typed and we are removing type annotations,
        // then we need to take care that we're not applying space-stripping to the untyped
        // version of the document (test case strip-space-008)
        Receiver temp = out;
        Stripper stripper = new Stripper(((SpaceStrippedDocument) docWrapper).getStrippingRule(), temp);
        node.copy(stripper, copyOptions, locationId);
    }


    /**
     * A StrippingIterator delivers wrappers for the nodes delivered
     * by its underlying iterator. It is used when whitespace stripping
     * may be needed, e.g. for the child axis. It examines all text nodes
     * encountered to see if they need to be stripped, and if so, it
     * skips them.
     */

    private final class StrippingIterator implements AxisIterator {

        AxisIterator base;
        SpaceStrippedNode parent;
        NodeInfo currentVirtualNode;
        int position;

        /**
         * Create a StrippingIterator
         *
         * @param base   The underlying iterator
         * @param parent If all the nodes to be wrapped have the same parent,
         *               it can be specified here. Otherwise specify null.
         */

        public StrippingIterator(AxisIterator base, SpaceStrippedNode parent) {
            this.base = base;
            this.parent = parent;
            position = 0;
        }


        /*@Nullable*/
        @Override
        public NodeInfo next() {
            NodeInfo nextRealNode;
            while (true) {
                nextRealNode = base.next();
                if (nextRealNode == null) {
                    return null;
                }
                if (isPreserved(nextRealNode)) {
                    break;
                }
                // otherwise skip this whitespace text node
            }

            currentVirtualNode = makeWrapper(nextRealNode, (SpaceStrippedDocument) docWrapper, parent);
            position++;
            return currentVirtualNode;
        }

        private boolean isPreserved(NodeInfo nextRealNode) {
            if (nextRealNode.getNodeKind() != Type.TEXT) {
                return true;
            }
            NodeInfo actualParent =
                    parent == null ? nextRealNode.getParent() : parent.node;
            return isPreservedNode(nextRealNode, (SpaceStrippedDocument)docWrapper, actualParent);
        }

        @Override
        public void close() {
            base.close();
        }


    }  // end of class StrippingIterator


}

