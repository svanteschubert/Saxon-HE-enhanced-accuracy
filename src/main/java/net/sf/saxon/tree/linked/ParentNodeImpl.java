////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

//import com.sun.tools.javac.util.List;

import net.sf.saxon.event.Builder;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.om.CopyOptions;
import net.sf.saxon.om.Durability;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ArrayIterator;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.SingleNodeIterator;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.Type;

import java.util.Arrays;

/**
 * ParentNodeImpl is an implementation of a non-leaf node (specifically, an Element node
 * or a Document node)
 *
 */


public abstract class ParentNodeImpl extends NodeImpl {

    /*@Nullable*/ private Object _children = null;       // null for no _children
    // a NodeImpl for a single child
    // a NodeImpl[] for >1 child

    private int sequence;               // sequence number allocated during original tree creation.
    // set to -1 for nodes added subsequently by XQuery update

    /**
     * Get the node sequence number (in document order). Sequence numbers are monotonic but not
     * consecutive. In the current implementation, parent nodes (elements and document nodes) have a zero
     * least-significant word, while namespaces, attributes, text nodes, comments, and PIs have
     * the top word the same as their owner and the bottom half reflecting their relative position.
     * For nodes added by XQuery Update, the sequence number is -1L
     *
     * @return the sequence number if there is one, or -1L otherwise.
     */

    @Override
    protected final long getSequenceNumber() {
        return getRawSequenceNumber() == -1 ? -1L : (long) getRawSequenceNumber() << 32;
    }

    protected final int getRawSequenceNumber() {
        return sequence;
    }

    protected final void setRawSequenceNumber(int seq) {
        sequence = seq;
    }

    /**
     * Set the _children of this node
     *
     * @param children null if there are no _children, a single NodeInfo if there is one child, an array of NodeInfo
     *                 if there are multiple _children
     */

    protected final void setChildren(Object children) {
        this._children = children;
    }

    /**
     * Determine if the node has any _children.
     */

    @Override
    public final boolean hasChildNodes() {
        return _children != null;
    }

//    /**
//     * Return the sequence of _children of this node, as an {@code Iterable}. This
//     * method is designed to allow iteration over the _children in a Java "for each" loop,
//     * in the form <code>for (NodeInfo child : children()) {...}</code>
//     *
//     * @return the _children of the node, as an {@code Iterable}.
//     */
//
//    @Override
//    public Iterable<NodeImpl> children() {
//        if (_children == null) {
//            return Collections.emptyList();
//        } else if (_children instanceof NodeImpl) {
//            //noinspection Convert2Diamond
//            return () -> new MonoIterator<NodeImpl>((NodeImpl) _children);
//        } else {
//            return Arrays.asList((NodeImpl[]) _children);
//        }
//    }

    /**
     * Determine how many _children the node has
     *
     * @return the number of _children of this parent node
     */

    public final int getNumberOfChildren() {
        if (_children == null) {
            return 0;
        } else if (_children instanceof NodeImpl) {
            return 1;
        } else {
            return ((NodeInfo[]) _children).length;
        }
    }

    /**
     * Get an enumeration of the _children of this node
     *
     * @param test A NodeTest to be satisfied by the child nodes, or null
     *             if all child node are to be returned
     * @return an iterator over the _children of this node
     */

    protected final AxisIterator iterateChildren(NodeTest test) {
        if (_children == null) {
            return EmptyIterator.ofNodes();
        } else if (_children instanceof NodeImpl) {
            NodeImpl child = (NodeImpl) _children;
            if (test == null || test == AnyNodeTest.getInstance()) {
                return SingleNodeIterator.makeIterator(child);
            } else {
                return Navigator.filteredSingleton(child, test);
            }
        } else {
            if (test == null || test == AnyNodeTest.getInstance()) {
                return new ArrayIterator.OfNodes<NodeImpl>((NodeImpl[]) _children);
            } else {
                return new ChildEnumeration(this, test);
            }
        }
    }


    /**
     * Get the first child node of the element
     *
     * @return the first child node of the required type, or null if there are no _children
     */

    /*@Nullable*/
    @Override
    public final NodeImpl getFirstChild() {
        if (_children == null) {
            return null;
        } else if (_children instanceof NodeImpl) {
            return (NodeImpl) _children;
        } else {
            return ((NodeImpl[]) _children)[0];
        }
    }

    /**
     * Get the last child node of the element
     *
     * @return the last child of the element, or null if there are no _children
     */

    /*@Nullable*/
    @Override
    public final NodeImpl getLastChild() {
        if (_children == null) {
            return null;
        }
        if (_children instanceof NodeImpl) {
            return (NodeImpl) _children;
        }
        NodeImpl[] n = (NodeImpl[]) _children;
        return n[n.length - 1];
    }

    /**
     * Get the nth child node of the element (numbering from 0)
     *
     * @param n identifies the required child
     * @return the last child of the element, or null if there is no n'th child
     */

    /*@Nullable*/
    protected final NodeImpl getNthChild(int n) {
        if (_children == null) {
            return null;
        }
        if (_children instanceof NodeImpl) {
            return n == 0 ? (NodeImpl) _children : null;
        }
        NodeImpl[] nodes = (NodeImpl[]) _children;
        if (n < 0 || n >= nodes.length) {
            return null;
        }
        return nodes[n];
    }

    /**
     * Remove a given child
     *
     * @param child the child to be removed
     */

    protected void removeChild(NodeImpl child) {
        if (_children == null) {
            return;
        }
        if (_children == child) {
            _children = null;
            return;
        }
        NodeImpl[] nodes = (NodeImpl[]) _children;
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] == child) {
                if (nodes.length == 2) {
                    _children = nodes[1 - i];
                } else {
                    NodeImpl[] n2 = new NodeImpl[nodes.length - 1];
                    if (i > 0) {
                        System.arraycopy(nodes, 0, n2, 0, i);
                    }
                    if (i < nodes.length - 1) {
                        System.arraycopy(nodes, i + 1, n2, i, nodes.length - i - 1);
                    }
                    _children = cleanUpChildren(n2);
                }
                break;
            }
        }
    }

    /**
     * Tidy up the _children of the node. Merge adjacent text nodes; remove zero-length text nodes;
     * reallocate index numbers to each of the _children
     *
     * @param children the existing _children
     * @return the replacement array of _children
     */

    /*@NotNull*/
    private NodeImpl[] cleanUpChildren(/*@NotNull*/ NodeImpl[] children) {
        boolean prevText = false;
        int j = 0;
        NodeImpl[] c2 = new NodeImpl[children.length];
        for (NodeImpl node : children) {
            if (node instanceof TextImpl) {
                if (prevText) {
                    TextImpl prev = (TextImpl) c2[j - 1];
                    prev.replaceStringValue(prev.getUnicodeStringValue().concat(node.getUnicodeStringValue()));
                } else if (!node.getUnicodeStringValue().isEmpty()) {
                    prevText = true;
                    node.setSiblingPosition(j);
                    c2[j++] = node;
                }
            } else {
                node.setSiblingPosition(j);
                c2[j++] = node;
                prevText = false;
            }
        }
        if (j == c2.length) {
            return c2;
        } else {
            return Arrays.copyOf(c2, j);
        }
    }


    /**
     * Return the string-value of the node, that is, the concatenation
     * of the character content of all descendent elements and text nodes.
     *
     * @return the accumulated character content of the element, including descendant elements.
     */

    @Override
    public UnicodeString getUnicodeStringValue() {
        UnicodeBuilder sb = null;
        NodeImpl next = getFirstChild();
        while (next != null) {
            if (next instanceof TextImpl) {
                if (sb == null) {
                    sb = new UnicodeBuilder();
                }
                sb.accept(next.getUnicodeStringValue());
            }
            next = next.getNextInDocument(this);
        }
        if (sb == null) {
            return EmptyUnicodeString.getInstance();
        }
        return sb.toUnicodeString();
    }

    /**
     * Add a child node to this node. For system use only. Note: normalizing adjacent text nodes
     * is the responsibility of the caller.
     *
     * @param node  the node to be added as a child of this node. This must be an instance of
     *              {@link net.sf.saxon.tree.linked.NodeImpl}. It will be modified as a result of this call (by setting its
     *              parent property and sibling position)
     * @param index the position where the child is to be added
     */

    protected synchronized void addChild(/*@NotNull*/ NodeImpl node, int index) {
        NodeImpl[] c;
        if (_children == null) {
            c = new NodeImpl[10];
        } else if (_children instanceof NodeImpl) {
            c = new NodeImpl[10];
            c[0] = (NodeImpl) _children;
        } else {
            c = (NodeImpl[]) _children;
        }
        if (index >= c.length) {
            c = Arrays.copyOf(c, c.length*2);
        }
        c[index] = node;
        node.setRawParent(this);
        node.setSiblingPosition(index);
        _children = c;
    }


    /**
     * Insert a sequence of nodes as _children of this node.
     * <p>This method takes no action unless the target node is a document node or element node. It also
     * takes no action in respect of any supplied nodes that are not elements, text nodes, comments, or
     * processing instructions.</p>
     * <p>The supplied nodes will form the new _children. Adjacent text nodes will be merged, and
     * zero-length text nodes removed. The supplied nodes may be modified in situ, for example to change their
     * parent property and to add namespace bindings, or they may be copied, at the discretion of
     * the implementation.</p>
     *
     * @param source  the nodes to be inserted. The implementation determines what implementation classes
     *                of node it will accept; this implementation will accept text, comment, and processing instruction
     *                nodes belonging to any implementation, but elements must be instances of {@link net.sf.saxon.tree.linked.ElementImpl}.
     *                The supplied nodes will be modified in situ, for example
     *                to change their parent property and to add namespace bindings, if they are instances of
     *                {@link net.sf.saxon.tree.linked.ElementImpl}; otherwise they will be copied. If the nodes are copied, then on return
     *                the supplied source array will contain the copy rather than the original.
     * @param atStart true if the new nodes are to be inserted before existing _children; false if they are
     *                to be inserted after existing _children
     * @param inherit true if the inserted nodes are to inherit the namespaces of their new parent; false
     *                if such namespaces are to be undeclared
     * @throws IllegalArgumentException if the supplied nodes use a node implementation that this
     *                                  implementation does not accept.
     */

    @Override
    public void insertChildren(/*@NotNull*/ NodeInfo[] source, boolean atStart, boolean inherit) {
        if (atStart) {
            insertChildrenAt(source, 0, inherit);
        } else {
            insertChildrenAt(source, getNumberOfChildren(), inherit);
        }
    }

    /**
     * Insert children before or after a given existing child
     *
     * @param source  the _children to be inserted. We allow any kind of text, comment, or processing instruction
     *                node, but element nodes must be instances of this NodeInfo implementation.
     * @param index   the position before which they are to be inserted: 0 indicates insertion before the
     *                first child, 1 insertion before the second child, and so on.
     * @param inherit true if the inserted nodes are to inherit the namespaces that are in-scope for their
     *                new parent; false if such namespaces should be undeclared on the _children
     */

    synchronized protected void insertChildrenAt(/*@NotNull*/ NodeInfo[] source, int index, boolean inherit) {
        if (source.length == 0) {
            return;
        }
        NodeImpl[] source2 = adjustSuppliedNodeArray(source, inherit);
        if (_children == null) {
            if (source2.length == 1) {
                _children = source2[0];
                ((NodeImpl) _children).setSiblingPosition(0);
            } else {
                _children = cleanUpChildren(source2);
            }
        } else if (_children instanceof NodeImpl) {
            int adjacent = index == 0 ? source2.length - 1 : 0;
            if (_children instanceof TextImpl && source2[adjacent] instanceof TextImpl) {
                if (index == 0) {
                    source2[adjacent].replaceStringValue(
                            source2[adjacent].getUnicodeStringValue().concat(((TextImpl) _children).getUnicodeStringValue()));
                } else {
                    source2[adjacent].replaceStringValue(
                            ((TextImpl) _children).getUnicodeStringValue().concat(source2[adjacent].getUnicodeStringValue()));
                }
                _children = cleanUpChildren(source2);
            } else {
                NodeImpl[] n2 = new NodeImpl[source2.length + 1];
                if (index == 0) {
                    System.arraycopy(source2, 0, n2, 0, source2.length);
                    n2[source2.length] = (NodeImpl) _children;
                } else {
                    n2[0] = (NodeImpl) _children;
                    System.arraycopy(source2, 0, n2, 1, source2.length);
                }
                _children = cleanUpChildren(n2);
            }
        } else {
            NodeImpl[] n0 = (NodeImpl[]) _children;
            NodeImpl[] n2 = new NodeImpl[n0.length + source2.length];
            System.arraycopy(n0, 0, n2, 0, index);
            System.arraycopy(source2, 0, n2, index, source2.length);
            System.arraycopy(n0, index, n2, index + source2.length, n0.length - index);
            _children = cleanUpChildren(n2);
        }
    }


    /*@NotNull*/
    private NodeImpl convertForeignNode(/*@NotNull*/ NodeInfo source) {
        if (!(source instanceof NodeImpl)) {
            int kind = source.getNodeKind();
            switch (kind) {
                case Type.TEXT:
                    return new TextImpl(source.getUnicodeStringValue());
                case Type.COMMENT:
                    return new CommentImpl(source.getUnicodeStringValue());
                case Type.PROCESSING_INSTRUCTION:
                    return new ProcInstImpl(source.getLocalPart(), source.getUnicodeStringValue());
                case Type.ELEMENT:
                    Builder builder = null;
                    try {
                        builder = new LinkedTreeBuilder(getConfiguration().makePipelineConfiguration(), Durability.MUTABLE);
                        builder.open();
                        source.copy(builder, CopyOptions.ALL_NAMESPACES, Loc.NONE);
                        builder.close();
                    } catch (XPathException e) {
                        throw new IllegalArgumentException(
                                "Failed to convert inserted element node to an instance of net.sf.saxon.om.tree.ElementImpl");

                    }
                    return (NodeImpl)builder.getCurrentRoot();
                    default:
                    throw new IllegalArgumentException(
                            "Cannot insert a node unless it is an element, comment, text node, or processing instruction");
            }
        }
        return (NodeImpl) source;
    }

    /**
     * Replace child at a given index by new _children
     *
     * @param source  the _children to be inserted
     * @param index   the position at which they are to be inserted: 0 indicates replacement of the
     *                first child, replacement of the second child, and so on. The effect is undefined if index
     *                is out of range
     * @param inherit set to true if the new child elements are to inherit the in-scope namespaces
     *                of their new parent
     * @throws IllegalArgumentException if any of the replacement nodes is not an element, text,
     *                                  comment, or processing instruction node
     */

    protected synchronized void replaceChildrenAt(/*@NotNull*/ NodeInfo[] source, int index, boolean inherit) {
        if (_children == null) {
            return;
        }
        NodeImpl[] source2 = adjustSuppliedNodeArray(source, inherit);
        if (_children instanceof NodeImpl) {
            if (source2.length == 0) {
                _children = null;
            } else if (source2.length == 1) {
                _children = source2[0];
            } else {
                NodeImpl[] n2 = new NodeImpl[source2.length];
                System.arraycopy(source2, 0, n2, 0, source.length);
                _children = cleanUpChildren(n2);
            }
        } else {
            NodeImpl[] n0 = (NodeImpl[]) _children;
            NodeImpl[] n2 = new NodeImpl[n0.length + source2.length - 1];
            System.arraycopy(n0, 0, n2, 0, index);
            System.arraycopy(source2, 0, n2, index, source2.length);
            System.arraycopy(n0, index + 1, n2, index + source2.length, n0.length - index - 1);
            _children = cleanUpChildren(n2);
        }
    }

    private NodeImpl[] adjustSuppliedNodeArray(NodeInfo[] source, boolean inherit) {
        NodeImpl[] source2 = new NodeImpl[source.length];
        for (int i = 0; i < source.length; i++) {
            source2[i] = convertForeignNode(source[i]);
            NodeImpl child = source2[i];
            child.setRawParent(this);
            if (child instanceof ElementImpl) {
                // If the child has no xmlns="xxx" declaration, then add an xmlns="" to prevent false inheritance
                // from the new parent
                ((ElementImpl) child).fixupInsertedNamespaces(inherit);
            }
        }
        return source2;
    }


    /**
     * Compact the space used by this node
     *
     * @param size the number of actual _children
     */

    public synchronized void compact(int size) {
        if (size == 0) {
            _children = null;
        } else if (size == 1) {
            if (_children instanceof NodeImpl[]) {
                _children = ((NodeImpl[]) _children)[0];
            }
        } else {
            _children = Arrays.copyOf((NodeImpl[]) _children, size);
        }
    }


}

