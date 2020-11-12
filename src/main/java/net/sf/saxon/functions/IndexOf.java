////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.IntegerValue;


/**
 * The XPath 2.0 index-of() function, with the collation already known
 */


public class IndexOf extends CollatingFunctionFixed  {


    /**
     * For an expression that returns an integer or a sequence of integers, get
     * a lower and upper bound on the values of the integers that may be returned, from
     * static analysis. The default implementation returns null, meaning "unknown" or
     * "not applicable". Other implementations return an array of two IntegerValue objects,
     * representing the lower and upper bounds respectively. The values
     * UNBOUNDED_LOWER and UNBOUNDED_UPPER are used by convention to indicate that
     * the value may be arbitrarily large. The values MAX_STRING_LENGTH and MAX_SEQUENCE_LENGTH
     * are used to indicate values limited by the size of a string or the size of a sequence.
     *
     * @return the lower and upper bounds of integer values in the result, or null to indicate
     *         unknown or not applicable.
     */
    @Override
    public IntegerValue[] getIntegerBounds() {
        return new IntegerValue[]{Int64Value.PLUS_ONE, Expression.MAX_SEQUENCE_LENGTH};
    }

    @Override
    public void supplyTypeInformation(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType, Expression[] arguments) {
        ItemType type0 = arguments[0].getItemType();
        ItemType type1 = arguments[1].getItemType();
        if (type0 instanceof AtomicType && type1 instanceof AtomicType) {
            preAllocateComparer((AtomicType) type0, (AtomicType) type1, visitor.getStaticContext());
        }
    }

    private static class IndexIterator implements SequenceIterator {
        private int index = 0;
        private SequenceIterator base;
        private BuiltInAtomicType searchType;
        private AtomicComparer comparer;
        private AtomicValue key;

        public IndexIterator(SequenceIterator base, BuiltInAtomicType searchType, AtomicValue key, AtomicComparer comparer) {
            this.base = base;
            this.searchType = searchType;
            this.key = key;
            this.comparer = comparer;
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

        /**
         * Get the next item in the sequence. This method changes the state of the
         * iterator, in particular it affects the result of subsequent calls of
         * position() and current().
         *
         * @return the next item, or null if there are no more items. Once a call
         * on next() has returned null, no further calls should be made. The preferred
         * action for an iterator if subsequent calls on next() are made is to return
         * null again, and all implementations within Saxon follow this rule.
         * @throws net.sf.saxon.trans.XPathException if an error occurs retrieving the next item
         * @since 8.4
         */
        @Override
        public Int64Value next() throws XPathException {
            AtomicValue baseItem;
            while ((baseItem = (AtomicValue) base.next()) != null) {
                index++;
                if (Type.isGuaranteedComparable(searchType, baseItem.getPrimitiveType(), false) &&
                        comparer.comparesEqual(baseItem, key)) {
                    return new Int64Value(index);
                }
            }
            return null;
        }

    }



    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        AtomicComparer comparer = getAtomicComparer(context);
        SequenceIterator seq = arguments[0].iterate();
        AtomicValue val = (AtomicValue) arguments[1].head();
        BuiltInAtomicType searchType = val.getPrimitiveType();
        return SequenceTool.toLazySequence(new IndexIterator(seq, searchType, val, comparer));
    }

    @Override
    public String getStreamerName() {
        return "IndexOf";
    }

}

