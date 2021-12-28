////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.om.FocusIterator;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.*;

/**
 * A FilterIterator filters an input sequence using a filter expression. Note that a FilterIterator
 * is not used where the filter is a constant number (PositionFilter is used for this purpose instead),
 * so this class does no optimizations for numeric predicates.
 */

public class FilterIterator implements SequenceIterator {

    protected FocusIterator base;
    protected Expression filter;
    protected XPathContext filterContext;

    /**
     * Constructor
     *
     * @param base    An iteration of the items to be filtered
     * @param filter  The expression defining the filter predicate
     * @param context The context in which the expression is being evaluated
     */

    public FilterIterator(SequenceIterator base, Expression filter,
                          XPathContext context) {
        this.filter = filter;
        filterContext = context.newMinorContext();
        this.base = filterContext.trackFocus(base);
    }

    /**
     * Set the base iterator
     *
     * @param base    the iterator over the sequence to be filtered
     * @param context the context in which the (outer) filter expression is evaluated
     */

    public void setSequence(SequenceIterator base, XPathContext context) {
        filterContext = context.newMinorContext();
        this.base = filterContext.trackFocus(base);
    }

    /**
     * Get the next item if there is one
     */

    @Override
    public Item next() throws XPathException {
        return getNextMatchingItem();
    }

    /**
     * Get the next item in the base sequence that matches the filter predicate
     * if there is such an item, or null if not.
     *
     * @return the next item that matches the predicate
     * @throws XPathException if a dynamic error occurs
     */

    protected Item getNextMatchingItem() throws XPathException {
        Item next;
        while ((next = base.next()) != null) {
            if (matches()) {
                return next;
            }
        }
        return null;
    }

    /**
     * Determine whether the context item matches the filter predicate
     *
     * @return true if the context item matches
     * @throws XPathException if an error occurs evaluating the match
     */

    protected boolean matches() throws XPathException {

        // This code is carefully designed to avoid reading more items from the
        // iteration of the filter expression than are absolutely essential.

        // The code is almost identical to the code in ExpressionTool#effectiveBooleanValue
        // except for the handling of a numeric result

        SequenceIterator iterator = filter.iterate(filterContext);
        return testPredicateValue(iterator, base.position(), filter);
    }

    public static boolean testPredicateValue(SequenceIterator iterator, long position, Expression filter) throws XPathException {
        Item first = iterator.next();
        if (first == null) {
            return false;
        }
        if (first instanceof NodeInfo) {
            return true;
        } else {
            if (first instanceof BooleanValue) {
                if (iterator.next() != null) {
                    ExpressionTool.ebvError("a sequence of two or more items starting with a boolean", filter);
                }
                return ((BooleanValue) first).getBooleanValue();
            } else if (first instanceof StringValue) {
                if (iterator.next() != null) {
                    ExpressionTool.ebvError("a sequence of two or more items starting with a string", filter);
                }
                return first.getStringValueCS().length() != 0;
            } else if (first instanceof Int64Value) {
                if (iterator.next() != null) {
                    ExpressionTool.ebvError("a sequence of two or more items starting with a numeric value", filter);
                }
                return ((Int64Value) first).longValue() == position;

            } else if (first instanceof NumericValue) {
                if (iterator.next() != null) {
                    ExpressionTool.ebvError("a sequence of two or more items starting with a numeric value", filter);
                }
                return ((NumericValue) first).compareTo(position) == 0;
            } else if (first instanceof AtomicValue) {
                ExpressionTool.ebvError("a sequence starting with an atomic value of type "
                                                + ((AtomicValue) first).getPrimitiveType().getDisplayName()
                                                + " (" + first.toShortString() + ")",
                                        filter);
                return false;
            } else {
                ExpressionTool.ebvError("a sequence starting with " + first.getGenre().getDescription() + " (" + first.toShortString() + ")", filter);
                return false;
            }
        }
    }

    @Override
    public void close() {
        base.close();
    }


    /**
     * Subclass to handle the common special case where it is statically known
     * that the filter cannot return a numeric value
     */

    public static final class NonNumeric extends FilterIterator {

        /**
         * Create a CompiledFilterIterator for the situation where it is known that the filter
         * expression will never evaluate to a number value. For this case we can simply
         * use the effective boolean value of the predicate
         *
         * @param base    iterator over the sequence to be filtered
         * @param filter  the filter expression
         * @param context the current context (for evaluating the filter expression as a whole).
         *                A new context will be created to evaluate the predicate.
         */

        public NonNumeric(SequenceIterator base, Expression filter,
                          XPathContext context) {
            super(base, filter, context);
        }

        /**
         * Determine whether the context item matches the filter predicate
         */

        @Override
        protected boolean matches() throws XPathException {
            return filter.effectiveBooleanValue(filterContext);
        }

    }

}

