////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.GroundedIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.tree.wrapper.SiblingCountingNode;
import net.sf.saxon.value.SequenceExtent;

/**
 * An iterator that maintains the values of position() and current(), as a wrapper
 * over an iterator which does not maintain these values itself.
 *
 * <p>Note that when a FocusTrackingIterator is used to wrap a SequenceIterator
 * in order to track the values of position() and current(), it is important to ensure
 * (a) that the SequenceIterator is initially positioned at the start of the sequence,
 * and (b) that all calls on next() to advance the iterator are directed at the
 * FocusTrackingIterator, and not at the wrapped SequenceIterator.</p>
 *
 * @since 9.6
 */
public class FocusTrackingIterator
        implements FocusIterator, LookaheadIterator, GroundedIterator, LastPositionFinder {

    private SequenceIterator base;
    private Item curr;
    private int pos = 0;
    private int last = -1;
    private SiblingMemory siblingMemory;


    public static FocusTrackingIterator track(SequenceIterator base) {
        return new FocusTrackingIterator(base);
    }

    public FocusTrackingIterator(SequenceIterator base) {
        this.base = base;
    }

    /**
     * Get the underlying iterator
     * @return the iterator underlying this FocusIterator
     */

    public SequenceIterator getUnderlyingIterator() {
        return base;
    }

    /**
     * Get the next item in the sequence. This method changes the state of the
     * iterator, in particular it affects the result of subsequent calls of
     * position() and current().
     *
     * @return the next item, or null if there are no more items. Once a call
     *         on next() has returned null, no further calls should be made. The preferred
     *         action for an iterator if subsequent calls on next() are made is to return
     *         null again, and all implementations within Saxon follow this rule.
     * @since 8.4
     */
    @Override
    public Item next() {
        curr = base.next();
        if (curr == null) {
            last = pos;
            pos = -1;
        } else {
            pos++;
        }
        return curr;
    }

    /**
     * Get the current value in the sequence (the one returned by the
     * most recent call on next()). This will be null before the first
     * call of next(). This method does not change the state of the iterator.
     *
     * @return the current item, the one most recently returned by a call on
     *         next(). Returns null if next() has not been called, or if the end
     *         of the sequence has been reached.
     * @since 8.4
     */
    @Override
    public Item current() {
        return curr;
    }

    /**
     * Get the current position. This will usually be zero before the first call
     * on next(), otherwise it will be the number of times that next() has
     * been called. Once next() has returned null, the preferred action is
     * for subsequent calls on position() to return -1, but not all existing
     * implementations follow this practice. (In particular, the EmptyIterator
     * is stateless, and always returns 0 as the value of position(), whether
     * or not next() has been called.)
     * <p>This method does not change the state of the iterator.</p>
     *
     * @return the current position, the position of the item returned by the
     *         most recent call of next(). This is 1 after next() has been successfully
     *         called once, 2 after it has been called twice, and so on. If next() has
     *         never been called, the method returns zero. If the end of the sequence
     *         has been reached, the value returned will always be &lt;= 0; the preferred
     *         value is -1.
     * @since 8.4
     */
    @Override
    public int position() {
        return pos;
    }

    /**
     * Get the position of the last item in the sequence. The method is stateless in its external effect:
     * that is, it does not change the values returned by position(), next(), hasNext(), etc. However,
     * it creates a new base iterator which means that the result of calling getUnderlyingIterator()
     * may change.
     * @return the position of the last item
     * @throws UncheckedXPathException if a failure occurs reading the sequence
     */

    @Override
    public int getLength() {
        if (last == -1) {
            if (SequenceTool.supportsGetLength(base)) {
                last = SequenceTool.getLength(base);
            }
            if (last == -1) {
                GroundedValue residue = SequenceExtent.makeResidue(base);
                last = pos + residue.getLength();
                base = residue.iterate();
            }
        }
        return last;
    }

    /**
     * Ask whether this iterator supports use of the {@link #getLength()} method. This
     * method should always be called before calling {@link #getLength()}, because an iterator
     * that implements this interface may support use of {@link #getLength()} in some situations
     * and not in others
     *
     * @return true if the {@link #getLength()} method can be called to determine the length
     * of the underlying sequence.
     */
    @Override
    public boolean supportsGetLength() {
        return true;
    }

    @Override
    public boolean supportsHasNext() {
        return base instanceof LookaheadIterator && ((LookaheadIterator)base).supportsHasNext();
    }


    /**
     * Determine whether there are more items to come. Note that this operation
     * is stateless and it is not necessary (or usual) to call it before calling
     * next(). It is used only when there is an explicit need to tell if we
     * are at the last element.
     * <p>This method must not be called unless the method {@link #supportsHasNext()} returns true.</p>
     *
     * @return true if there are more items in the sequence
     * @throws ClassCastException if the base iterator does not support lookahead processing
     */
    @Override
    public boolean hasNext() {
        assert base instanceof LookaheadIterator;
        return ((LookaheadIterator)base).hasNext();
    }

    /**
     * Return a GroundedValue containing all the items in the sequence returned by this
     * SequenceIterator. This should be an "in-memory" value, not a Closure.
     *
     * @return the corresponding Value
     * @throws UncheckedXPathException in the cases of subclasses (such as the iterator over a MemoClosure)
     *                        which cause evaluation of expressions while materializing the value.
     */
    @Override
    public GroundedValue materialize() {
        return SequenceTool.toGroundedValue(base);
    }

    /**
     * Return a GroundedValue containing all the remaining items in the sequence returned by this
     * SequenceIterator, starting at the current position. This should be an "in-memory" value, not a Closure.
     *
     * @return the corresponding Value
     * @throws UncheckedXPathException in the cases of subclasses (such as the iterator over a MemoClosure)
     *                        which cause evaluation of expressions while materializing the value.
     */
    @Override
    public GroundedValue getResidue()  {
        return SequenceExtent.from(this);
    }

    /**
     * Close the iterator. This indicates to the supplier of the data that the client
     * does not require any more items to be delivered by the iterator. This may enable the
     * supplier to release resources. After calling close(), no further calls on the
     * iterator should be made; if further calls are made, the effect of such calls is undefined.
     * <p>(Currently, closing an iterator is important only when the data is being "pushed" in
     * another thread. Closing the iterator terminates that thread and means that it needs to do
     * no additional work. Indeed, failing to close the iterator may cause the push thread to hang
     * waiting for the buffer to be emptied.)</p>
     *
     * @since 9.1
     */
    @Override
    public void close() {
        base.close();
    }

    public boolean isActuallyGrounded() {
        return (base instanceof GroundedIterator && ((GroundedIterator)base).isActuallyGrounded());
    }

    /**
     * Cached data to support optimization of the getSiblingPosition() method
     */

    private static class SiblingMemory {
        public NodeTest mostRecentNodeTest = null;
        public NodeInfo mostRecentNode = null;
        public int mostRecentPosition = -1;
    }

    /**
     * Get the sibling position of a node: specifically, count how many preceding siblings
     * of a node satisfy the nodetest. This method appears here because it can potentially
     * make use of cached information. When an instruction such as {@code xsl:apply-templates
     * select="*"} (which selects a set of sibling nodes) is used in conjunction with patterns
     * such as {@code match="*[position() mod 2 = 1]}, then calculation of the position of one
     * node in the sequence of siblings can take advantage of the fact that the position of the
     * immediately preceding sibling is already known.
     *
     * <p>This optimization was suggested by the late Sebastian Rahtz, one of Saxon's earliest
     * power users, and it is dedicated to his memory.</p>
     *
     * @param node     the starting node, which is assumed to satisfy the node test
     * @param nodeTest the node test
     * @param max      the maximum number of nodes to be counted
     * @return the number of preceding siblings that satisfy the node test, plus one, unless the
     * number exceeds max, in which case return some number greater than or equal to max.
     */

    public int getSiblingPosition(NodeInfo node, NodeTest nodeTest, int max) {
        if (node instanceof SiblingCountingNode && nodeTest instanceof AnyNodeTest) {
            return ((SiblingCountingNode) node).getSiblingPosition();
        }
        if (siblingMemory == null) {
            siblingMemory = new SiblingMemory();
        } else if (siblingMemory.mostRecentNodeTest.equals(nodeTest) && node.equals(siblingMemory.mostRecentNode)) {
            return siblingMemory.mostRecentPosition;
        }
        SiblingMemory s = siblingMemory;
        AxisIterator prev = node.iterateAxis(AxisInfo.PRECEDING_SIBLING, nodeTest);
        NodeInfo prior;
        int count = 1;
        while ((prior = prev.next()) != null) {
            if (prior.equals(s.mostRecentNode) && nodeTest.equals(s.mostRecentNodeTest)) {
                int result = count + s.mostRecentPosition;
                s.mostRecentNode = node;
                s.mostRecentPosition = result;
                return result;
            }
            if (++count > max) {
                return count;
            }
        }
        s.mostRecentNode = node;
        s.mostRecentPosition = count;
        s.mostRecentNodeTest = nodeTest;
        return count;
    }
}
