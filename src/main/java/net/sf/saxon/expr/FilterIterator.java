////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.*;

/**
 * A FilterIterator filters an input sequence using a filter expression. Note that a FilterIterator
 * is not used where the filter is a constant number (PositionFilter is used for this purpose instead),
 * so this class does no optimizations for numeric predicates.
 */

public class FilterIterator implements SequenceIterator {

    private FocusIterator base;
    private final Expression filter;
    private XPathContext filterContext;

    private final boolean isXPath40;

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
        this.isXPath40 = filter != null &&
                filter.getRetainedStaticContext().getPackageData().getHostLanguageVersion() >= 40;
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
    public Item next() {
        try {
            return getNextMatchingItem();
        } catch (XPathException e) {
            throw new UncheckedXPathException(e);
        }
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
        return isXPath40
                ? testPredicateValue40(iterator, base.position(), filter)
                : testPredicateValue(iterator, base.position(), filter);
    }

    public static boolean testPredicateValue(SequenceIterator iterator, long position, Expression filter) throws XPathException {
        Item first = iterator.next();
        if (first == null) {
            return false;
        }
        if (first instanceof NodeInfo) {
            iterator.close();
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
                return !((StringValue) first).isEmpty();
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
                ExpressionTool.ebvError("a sequence starting with " + Err.describeGenre(first.getGenre()) + " (" + first.toShortString() + ")", filter);
                return false;
            }
        }
    }

    public static boolean testPredicateValue40(SequenceIterator iterator, long position, Expression filter) throws XPathException {
        Item first = iterator.next();
        if (first == null) {
            return false;
        }
        if (first instanceof GNode) {
            iterator.close();
            return true;

        } else if (first instanceof NumericValue val) {
            while (true) {
                if (val.compareTo(position) == 0) {
                    iterator.close();
                    return true;
                }
                Item next = iterator.next();
                if (next == null) {
                    return false;
                }
                if (!(next instanceof NumericValue)) {
                    throw new XPathException("If the first item in the predicate is numeric, "
                                                     + "all following values must be numeric", "XPTY0004");
                }
                val = (NumericValue) next;
            }

        } else if (first instanceof BooleanValue) {
            if (iterator.next() != null) {
                ExpressionTool.ebvError("a sequence of two or more items starting with a boolean", filter);
            }
            return ((BooleanValue) first).getBooleanValue();
        } else if (first instanceof StringValue) {
            if (iterator.next() != null) {
                ExpressionTool.ebvError("a sequence of two or more items starting with a string", filter);
            }
            boolean result = !((StringValue) first).isEmpty();
            iterator.close();
            return result;

        } else if (first instanceof AtomicValue) {
            ExpressionTool.ebvError("a sequence starting with an atomic value of type "
                                            + ((AtomicValue) first).getPrimitiveType().getDisplayName()
                                            + " (" + first.toShortString() + ")",
                                    filter);
            return false;
        } else {
            ExpressionTool.ebvError("a sequence starting with " + Err.describeGenre(first.getGenre()) + " (" + first.toShortString() + ")", filter);
            return false;
        }
    }

    @Override
    public void close() {
        base.close();
    }

}

