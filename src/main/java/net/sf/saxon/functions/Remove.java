////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.NumericValue;
import net.sf.saxon.z.IntHashSet;
import net.sf.saxon.z.IntIterator;
import net.sf.saxon.z.IntSet;
import net.sf.saxon.z.IntSingletonSet;

/**
 * The XPath 2.0 remove() function
 */

public class Remove extends SystemFunction {

    @Override
    public Expression makeFunctionCall(Expression[] arguments) {

        if (Literal.isAtomic(arguments[1])) {
            Sequence index = ((Literal) arguments[1]).getGroundedValue();
            if (index instanceof IntegerValue) {
                try {
                    long value = ((IntegerValue) index).longValue();
                    if (value <= 0) {
                        return arguments[0];
                    } else if (value == 1) {
                        return new TailExpression(arguments[0], 2);
                    }
                } catch (XPathException err) {
                    //
                }
            }
        }

        return super.makeFunctionCall(arguments);
    }

    /**
     * Evaluate the expression as a general function call
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        IntSet removePositions;
        if (arguments[1] instanceof net.sf.saxon.value.AtomicValue) {
            NumericValue n = (NumericValue) arguments[1].head();
            int pos = (int) n.longValue();
            if (pos < 1) {
                return arguments[0];
            }
            removePositions = new IntSingletonSet(pos);
        } else {
            IntHashSet positions = new IntHashSet();
            NumericValue n;
            SequenceIterator iter = arguments[1].iterate();
            while ((n = (NumericValue)iter.next()) != null) {
                int pos = (int) n.longValue();
                if (pos >= 1) {
                    positions.add(pos);
                }
            }
            if (positions.isEmpty()) {
                return arguments[0];
            }
            removePositions = positions;
        }
        return SequenceTool.toLazySequence(new RemoveIterator(arguments[0].iterate(), removePositions));
    }

    /**
     * An implementation of SequenceIterator that returns all items except the one
     * at a specified position.
     */

    public static class RemoveIterator implements SequenceIterator, LastPositionFinder {

        SequenceIterator base;
        IntSet removePositions;
        int basePosition = 0;
        Item current = null;

        public RemoveIterator(SequenceIterator base, IntSet removePosition) {
            this.base = base;
            this.removePositions = removePosition;
        }

        @Override
        public Item next() {
            current = base.next();
            basePosition++;
            while (current != null && removePositions.contains(basePosition)) {
                current = base.next();
                basePosition++;
            }
            return current;
        }

        @Override
        public void close() {
            base.close();
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
            return SequenceTool.supportsGetLength(base);
        }

        /**
         * Get the last position (that is, the number of items in the sequence). This method is
         * non-destructive: it does not change the state of the iterator.
         * The result is undefined if the next() method of the iterator has already returned null.
         */

        @Override
        public int getLength() {
            int x = SequenceTool.getLength(base);
            int result = x;
            IntIterator iter = removePositions.iterator();
            while (iter.hasNext()) {
                int i = iter.next();
                if (i >= 1 && i <= x) {
                    result--;
                }
            }
            return result;
        }

    }

    @Override
    public String getStreamerName() {
        return "Remove";
    }

}

