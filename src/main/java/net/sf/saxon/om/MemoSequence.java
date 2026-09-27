////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.expr.elab.LearningEvaluator;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpSimpleEnum;
import net.sf.saxon.tree.iter.ArrayIterator;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.GroundedIterator;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceExtent;

import java.util.Arrays;

/**
 * A Sequence implementation that represents a lazy evaluation of a supplied iterator. Items
 * are read from the base iterator only when they are first required, and are then remembered
 * within the local storage of the MemoSequence, which eventually (if the sequence is read
 * to the end) contains the entire value.
 */

public class MemoSequence implements Sequence {

    private final SequenceIterator inputIterator;

    private Item[] reservoir = null;
    private int used;

    private LearningEvaluator learningEvaluator;
    private int serialNumber;

    @CSharpSimpleEnum
    private enum State {
        // State in which no items have yet been read
        UNREAD,
        // State in which zero or more items are in the reservoir and it is not known
        // whether more items exist
        MAYBE_MORE,
        // State in which all the items are in the reservoir
        ALL_READ,
        // State in which we are getting the base iterator. If the closure is called in this state,
        // it indicates a recursive entry, which is only possible on an error path
        BUSY,
        // State in which we know that the value is an empty sequence
        EMPTY,
        // State in which we have already encountered and reported an error in reading this variable
        // It's possible further attempts will be made to read it again: see bug 6440.
        ERROR }

    private State state = State.UNREAD;

    public MemoSequence(SequenceIterator iterator) {
        this.inputIterator = iterator;
    }


    public void setLearningEvaluator(LearningEvaluator caller, int serialNumber) {
        this.learningEvaluator = caller;
        this.serialNumber = serialNumber;
    }


    @Override
    public Item head() throws XPathException {
        return iterate().next();
    }


    @Override
    public synchronized SequenceIterator iterate() {
        switch (state) {
            case UNREAD:
                state = State.BUSY;
                if (inputIterator instanceof EmptyIterator) {
                    state = State.EMPTY;
                    return inputIterator;
                }
                reservoir = new Item[50];
                used = 0;
                state = State.MAYBE_MORE;
                return new ProgressiveIterator(this);

            case MAYBE_MORE:
                return new ProgressiveIterator(this);

            case ALL_READ:
                switch (used) {
                    case 0:
                        state = State.EMPTY;
                        return EmptyIterator.INSTANCE;
                    case 1:
                        assert reservoir != null;
                        return SingletonIterator.makeIterator(reservoir[0]);
                    default:
                        return new ArrayIterator.Of<>(reservoir, 0, used);
                }

            case BUSY:
                // recursive entry: can happen if there is a circularity involving variable and function definitions
                // Can also happen if variable evaluation is attempted in a debugger, hence the cautious message
                XPathException de = new XPathException("Attempt to access a variable while it is being evaluated", "XTDE0640");
                throw new UncheckedXPathException(de);

            case EMPTY:
                return EmptyIterator.INSTANCE;

            case ERROR:
                XPathException e2 = new XPathException("Attempting to read a local variable when an error in that variable has already been reported", "XTDE0640");
                throw new UncheckedXPathException(e2);

            default:
                throw new IllegalStateException("Unknown iterator state");

        }
    }

    /**
     * Get the Nth item in the sequence (0-based), reading new items into the internal reservoir if necessary
     * @param n the index of the required item
     * @return the Nth item if it exists, or null otherwise
     * @throws XPathException if the input sequence cannot be read
     */

    public synchronized Item itemAt(int n) throws XPathException {
        if (n < 0) {
            return null;
        }
        if (reservoir != null && n < used) {
            return reservoir[n];
        }
        if (state == State.ALL_READ || state == State.EMPTY) {
            return null;
        }
        if (state == State.ERROR) {
            throw new XPathException("Attempting to read a local variable when an error in that variable has already been reported", "XTDE0640");
        }
        if (state == State.UNREAD) {
            Item item = inputIterator.next();
            if (item == null) {
                state = State.EMPTY;
                return null;
            } else {
                state = State.MAYBE_MORE;
                reservoir = new Item[50];
                append(item);
                if (n == 0) {
                    return item;
                }
            }
        }
        // We have read some items from the input sequence but not enough. Read as many more as are needed.
        int diff = n - used + 1;
        try {
            while (diff-- > 0) {
                Item i = inputIterator.next();
                if (i == null) {
                    state = State.ALL_READ;
                    condense();
                    return null;
                }
                append(i);
                state = State.MAYBE_MORE;
            }
        } catch (UncheckedXPathException e) {
            state = State.ERROR;
            throw e.getXPathException();
        }
        //noinspection ConstantConditions
        return reservoir[n];

    }


    /**
     * Append an item to the reservoir
     *
     * @param item the item to be added
     */

    private void append(Item item) {
        assert reservoir != null;
        if (used >= reservoir.length) {
            reservoir = Arrays.copyOf(reservoir, used * 2);
        }
        reservoir[used++] = item;
    }

    /**
     * Release unused space in the reservoir (provided the amount of unused space is worth reclaiming)
     */

    private void condense() {
        if (reservoir != null && reservoir.length - used > 30) {
            reservoir = Arrays.copyOf(reservoir, used);
        }
    }


    /**
     * A ProgressiveIterator starts by reading any items already held in the reservoir;
     * when the reservoir is exhausted, it reads further items from the inputIterator,
     * copying them into the reservoir as they are read.
     */

    public final static class ProgressiveIterator
            implements SequenceIterator, LastPositionFinder, GroundedIterator {

        private final MemoSequence container;
        private int position = -1;  // zero-based position in the reservoir of the
                                    // item most recently read

        /**
         * Create a ProgressiveIterator
         * @param container the containing MemoSequence
         */

        public ProgressiveIterator(MemoSequence container) {
            this.container = container;
        }

        /**
         * Get the containing MemoSequence
         * @return the containing MemoSequence
         */

        public MemoSequence getMemoSequence() {
            return container;
        }

        /*@Nullable*/
        @Override
        public Item next() {
            synchronized (container) {
                // synchronized for the case where a multi-threaded xsl:for-each is reading the variable
                if (position == -2) {   // means we've already returned null once, keep doing so if called again.
                    return null;
                }
                if (++position < container.used) {
                    assert container.reservoir != null;
                    return container.reservoir[position];
                } else if (container.state == State.ALL_READ) {
                    // someone else has read the input to completion in the meantime
                    position = -2;
                    return null;
                } else {
                    assert container.inputIterator != null;
                    Item i = null;
                    try {
                        i = container.inputIterator.next();
                        if (i == null) {
                            container.state = State.ALL_READ;
                            container.condense();
                            position = -2;
                            reportCompletion();
                            return null;
                        }
                    } catch (UncheckedXPathException e) {
                        container.state = State.ERROR;
                        throw e;
                    }
                    position = container.used;
                    container.append(i);
                    container.state = State.MAYBE_MORE;
                    return i;
                }
            }
        }

        @Override
        public boolean supportsGetLength() {
            return true;
        }

        /**
         * Get the last position (that is, the number of items in the sequence)
         */

        @Override
        public int getLength() {
            if (container.state == State.ALL_READ) {
                return container.used;
            } else if (container.state == State.EMPTY) {
                return 0;
            } else {
                // save the current position
                int savePos = position;
                // fill the reservoir
                //noinspection StatementWithEmptyBody
                while (next() != null) {}
                // reset the current position
                position = savePos;
                // return the total number of items
                return container.used;
            }
        }

        public boolean isActuallyGrounded() {
            return true;
        }

        /**
         * Return a value containing all the items in the sequence returned by this
         * SequenceIterator
         *
         * @return the corresponding value
         */

        /*@Nullable*/
        @Override
        public GroundedValue materialize() {
            if (container.state == State.ALL_READ) {
                return makeExtent();
            } else if (container.state == State.EMPTY) {
                return EmptySequence.INSTANCE;
            } else {
                // save the current position
                int savePos = position;
                // fill the reservoir
                while (next() != null) {
                }
                // reset the current position
                position = savePos;
                // return all the items
                return makeExtent();
            }
        }

        private GroundedValue makeExtent() {
            if (container.used == container.reservoir.length) {
                if (container.used == 0) {
                    return EmptySequence.INSTANCE;
                } else if (container.used == 1) {
                    return container.reservoir[0];
                } else {
                    return new SequenceExtent.Of<Item>(container.reservoir);
                }
            } else {
                return SequenceExtent.makeSequenceExtent(
                        Arrays.asList(container.reservoir).subList(0, container.used));
            }
        }

        @Override
        public GroundedValue getResidue() {
            if (container.state == State.EMPTY || position >= container.used || position == -2) {
                return EmptySequence.INSTANCE;
            } else if (container.state == State.ALL_READ) {
                return SequenceExtent.makeSequenceExtent(
                        Arrays.asList(container.reservoir).subList(position + 1, container.used));
            } else {
                // save the current position
                int savePos = position;
                // fill the reservoir
                //noinspection StatementWithEmptyBody
                while (next() != null) {
                }
                // reset the current position
                position = savePos;
                // return all the items
                return SequenceExtent.makeSequenceExtent(
                        Arrays.asList(container.reservoir).subList(position + 1, container.used));
            }
        }

        /**
         * Close the iterator. This indicates to the supplier of the data that the client
         * does not require any more items to be delivered by the iterator. This may enable the
         * supplier to release resources. After calling close(), no further calls on the
         * iterator should be made; if further calls are made, the effect of such calls is undefined.
         * <p>For example, the iterator returned by the unparsed-text-lines() function has a close() method
         * that causes the underlying input stream to be closed, whether or not the file has been read
         * to completion.</p>
         * <p>Closing an iterator is important when the data is being "pushed" in
         * another thread. Closing the iterator terminates that thread and means that it needs to do
         * no additional work. Indeed, failing to close the iterator may cause the push thread to hang
         * waiting for the buffer to be emptied.</p>
         * <p>Closing an iterator is not necessary if the iterator is read to completion: if a call
         * on {@link #next()} returns null, the iterator will be closed automatically. An explicit
         * call on {@link #close()} is needed only when iteration is abandoned prematurely.</p>
         *
         * @since 9.1. Default implementation added in 9.9.
         */
        @Override
        public void close() {
            if (container.state == State.ALL_READ) {
                reportCompletion();
            }
        }

        private void reportCompletion() {
            // When we've finished with the iterator, provide feedback to the binding instruction
            // as to whether all the data was read, or whether there was an early exit. This can
            // be used to switch the evaluation strategy from lazy evaluation to eager evaluation.
            // In fact we only notify when the iterator is read to completion, otherwise we
            // would get multiple notifications for constructs like `if (!empty(x)) then x`.
            if (container.learningEvaluator != null) {
                container.learningEvaluator.reportCompletion(container.serialNumber);
            }
        }


    }

}

