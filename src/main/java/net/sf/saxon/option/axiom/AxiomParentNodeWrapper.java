////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.axiom;

import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.wrapper.AbstractNodeWrapper;
import net.sf.saxon.tree.wrapper.SiblingCountingNode;
import net.sf.saxon.value.UntypedAtomicValue;
import org.apache.axiom.om.OMContainer;
import org.apache.axiom.om.OMDocument;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.om.OMText;

import java.util.Iterator;
import java.util.function.Predicate;

/**
 * A node in the XDM tree; specifically, a node that wraps an Axiom document node or element node.
 *
 * @author Michael H. Kay
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
        return new UntypedAtomicValue(getStringValueCS());
    }

    /**
     * Get the value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String.
     */

    @Override
    public CharSequence getStringValueCS() {
        FastStringBuffer buff = new FastStringBuffer(FastStringBuffer.C256);
        for (Iterator iter = node.getDescendants(false); iter.hasNext(); ) {
            OMNode next = (OMNode) iter.next();
            if (next instanceof OMText) {
                buff.append(((OMText) next).getText());
            }
        }
        return buff.condense();
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
    public void generateId(FastStringBuffer buffer) {
        Navigator.appendSequentialKey(this, buffer, true);
    }

    @Override
    protected final AxisIterator iterateChildren(Predicate<? super NodeInfo> nodeTest) {
        return new ChildWrappingIterator(this, nodeTest);
    }

    @Override
    protected AxisIterator iterateDescendants(Predicate<? super NodeInfo> nodeTest, boolean includeSelf) {
        // Note: for unknown reasons, this method is really slow. See XMark test q7.
        return new DescendantWrappingIterator(this, nodeTest, includeSelf);
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

    private abstract class AxiomWrappingIterator implements AxisIterator {
        Iterator base;
        Predicate<? super NodeInfo> nodeTest;

        public AxiomWrappingIterator(Iterator base, Predicate<? super NodeInfo> nodeTest) {
            this.base = base;
            this.nodeTest = nodeTest;
        }

        @Override
        public NodeInfo next() {
            while (true) {
                if (base.hasNext()) {
                    OMNode node = (OMNode) base.next();
                    if (!isIgnoredNode(node)) {
                        NodeInfo wrapper = wrap(node);
                        if (nodeTest.test(wrapper)) {
                            return wrapper;
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

        public DescendantWrappingIterator(AxiomParentNodeWrapper parentWrapper, Predicate<? super NodeInfo> nodeTest, boolean includeSelf) {
            super(node.getDescendants(includeSelf), nodeTest);
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

        public ChildWrappingIterator(AxiomParentNodeWrapper commonParent, Predicate<? super NodeInfo> nodeTest) {
            super(node.getChildren(), nodeTest);
            this.commonParent = commonParent;
            this.nodeTest = nodeTest;
            this.docWrapper = (AxiomDocument)commonParent.getTreeInfo();
        }

        @Override
        protected NodeInfo wrap(Object node) {
            return AxiomDocument.makeWrapper((OMNode) node, docWrapper, commonParent, index++);
        }
    }


}

