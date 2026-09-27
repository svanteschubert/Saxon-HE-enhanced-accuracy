////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.axiom;

import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.nodetest.NodePredicate;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.wrapper.AbstractNodeWrapper;
import net.sf.saxon.tree.wrapper.SiblingCountingNode;
import net.sf.saxon.value.StringValue;
import org.apache.axiom.om.*;
import org.apache.axiom.om.impl.llom.OMDocumentImpl;

import java.util.Iterator;

/**
 * A node in the XDM tree; specifically, a node that wraps an Axiom document node or element node.
 */
public abstract class AxiomParentNodeWrapper extends AbstractNodeWrapper
        implements SiblingCountingNode {

    protected OMContainer node;

    protected AxiomParentNodeWrapper(OMContainer node) {
        this.node = node;
    }

    /**
     * Get the underlying Axiom node, to implement the VirtualNode interface
     */

    @Override
    public OMContainer getUnderlyingNode() {
        return node;
    }

    /**
     * Get the typed value.
     *
     * @return the typed value. If requireSingleton is set to true, the result
     *         will always be an AtomicValue. In other cases it may be a Value
     *         representing a sequence whose items are atomic values.
     */

    @Override
    public AtomicSequence atomize() {
        return StringValue.makeUntypedAtomic(getUnicodeStringValue());
    }

    /**
     * Get the string value of the node as a UnicodeString.
     * @return the node's string value
     */

    @Override
    public UnicodeString getUnicodeStringValue() {
        UnicodeBuilder buff = new UnicodeBuilder();
        for (Iterator iter = node.getDescendants(false); iter.hasNext(); ) {
            OMNode next = (OMNode) iter.next();
            if (next instanceof OMText) {
                buff.append(((OMText) next).getText());
            }
        }
        return buff.toUnicodeString();
    }

    /**
     * Determine whether the node has any children.
     * <p>Note: the result is equivalent to
     * <code>getEnumeration(Axis.CHILD, AnyNodeTest.getInstance()).hasNext()</code></p>
     */

    @Override
    public boolean hasChildNodes() {
        return node.getFirstOMChild() != null;
    }

    /**
     * Get a character string that uniquely identifies this node. Note:
     * a.isSameNode(b) if and only if generateId(a)==generateId(b)
     *
     * @param buffer a buffer to contain a string that uniquely identifies this node, across all documents
     */

    @Override
    public void generateId(StringBuilder buffer) {
        Navigator.appendSequentialKey(this, buffer, true);
    }

    @Override
    protected final SequenceIterator iterateChildren(NodePredicate nodeTest) {
        return new ChildWrappingIterator(this, nodeTest);
    }

    @Override
    protected SequenceIterator iterateDescendants(NodePredicate predicate, boolean includeSelf) {
        // Note: for unknown reasons, this method is really slow. See XMark test q7.
        return new DescendantWrappingIterator(this, predicate, includeSelf);
    }

    private static boolean isIgnoredNode(OMNode node) {
        switch (node.getType()) {
            case OMNode.DTD_NODE:
                return true;
            case OMNode.SPACE_NODE:
                return node.getParent() instanceof OMDocument;
            default:
                return false;
        }
    }

    /**
     * Abstract iterator that takes an iterator over nodes in the Axiom tree and
     * wraps it with an implementation of Saxon's AxisIterator that wraps each
     * successive node as it is found.
     */

    private abstract class AxiomWrappingIterator implements SequenceIterator {
        private final Iterator base;
        private final NodePredicate predicate;

        public AxiomWrappingIterator(Iterator base, NodePredicate predicate) {
            this.base = base;
            this.predicate = predicate;
        }

        @Override
        public NodeInfo next() {
            while (true) {
                if (base.hasNext()) {
                    Object node = base.next();
                    if (node instanceof OMNode || node instanceof OMDocument) {
                        if (node instanceof OMDocument || !isIgnoredNode((OMNode) node)) {
                            NodeInfo wrapper = wrap(node);
                            if (predicate == null || predicate.test(wrapper)) {
                                return wrapper;
                            }
                        }
                    }
                } else {
                    return null;
                }
            }
        }

        protected abstract NodeInfo wrap(Object node);
    }

    /**
     * Iterator over the descendants of a supplied node (optionally including the node itself)
     */

    protected class DescendantWrappingIterator extends AxiomWrappingIterator {
        AxiomParentNodeWrapper parentWrapper;
        AxiomDocument docWrapper;
        boolean includeSelf;

        public DescendantWrappingIterator(AxiomParentNodeWrapper parentWrapper, NodePredicate predicate, boolean includeSelf) {
            super(node.getDescendants(includeSelf), predicate);
            this.parentWrapper = parentWrapper;
            docWrapper = (AxiomDocument)parentWrapper.getTreeInfo();
            this.includeSelf = includeSelf;
        }

        @Override
        protected NodeInfo wrap(Object node) {
            if (node instanceof OMDocument) {
                return docWrapper.getRootNode();
            } else {
                return AxiomDocument.makeWrapper((OMNode) node, docWrapper, null, -1);
            }
        }
    }

    /**
     * Iterator over the children of a supplied node
     */

    protected class ChildWrappingIterator extends AxiomWrappingIterator {

        AxiomParentNodeWrapper commonParent;
        AxiomDocument docWrapper;
        int index = 0;

        public ChildWrappingIterator(AxiomParentNodeWrapper commonParent, NodePredicate predicate) {
            super(node.getChildren(), predicate);
            this.commonParent = commonParent;
            this.docWrapper = (AxiomDocument)commonParent.getTreeInfo();
        }

        @Override
        protected NodeInfo wrap(Object node) {
            return AxiomDocument.makeWrapper((OMNode) node, docWrapper, commonParent, index++);
        }
    }


}

