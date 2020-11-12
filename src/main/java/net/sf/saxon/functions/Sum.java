////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;


/**
 * Implementation of the fn:sum function
 */
public class Sum extends FoldingFunction {

    /*@NotNull*/
    @Override
    public ItemType getResultItemType(Expression[] args) {
        TypeHierarchy th = getRetainedStaticContext().getConfiguration().getTypeHierarchy();
        ItemType base = Atomizer.getAtomizedItemType(args[0], false, th);
        if (base.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            base = BuiltInAtomicType.DOUBLE;
        }
        if (Cardinality.allowsZero(args[0].getCardinality())) {
            if (getArity() == 1) {
                return Type.getCommonSuperType(base, BuiltInAtomicType.INTEGER, th);
            } else {
                return Type.getCommonSuperType(base, args[1].getItemType(), th);
            }
        } else {
            return base.getPrimitiveItemType();
        }
    }

    @Override
    public int getCardinality(Expression[] arguments) {
        if (getArity() == 1 || arguments[1].getCardinality() == 1) {
            return StaticProperty.EXACTLY_ONE;
        } else {
            return StaticProperty.ALLOWS_ZERO_OR_ONE;
        }
    }

    /**
     * Create the Fold object which actually performs the evaluation.
     *
     * @param context             the dynamic evaluation context
     * @param additionalArguments the values of all arguments other than the first.
     * @return the Fold object used to compute the function
     */
    @Override
    public Fold getFold(XPathContext context, Sequence... additionalArguments) throws XPathException {
        if (additionalArguments.length > 0) {
            AtomicValue z = (AtomicValue)additionalArguments[0].head();
            return new SumFold(context, z);
        } else {
            return new SumFold(context, Int64Value.ZERO);
        }
    }

    /**
     * Static method to compute a total, invoked from compiled bytecode
     * @param in the sequence of items to be summed
     * @param context dynamic context for evaluation
     * @param locator expression location for diagnostics
     * @return null if the input is empty, otherwise the total as defined by the semantics of the sum() function
     * @throws XPathException if a dynamic error occurs
     */

    public static AtomicValue total(SequenceIterator in, XPathContext context, Location locator) throws XPathException {
        try {
            SumFold fold = new SumFold(context, null);
            in.forEachOrFail(fold::processItem);
            return (AtomicValue)fold.result().head();
        } catch (XPathException e) {
            e.maybeSetLocation(locator);
            throw e;
        }
    }

    /**
     * Implementation of Fold class to do the summation in push mode
     */

    private static class SumFold implements Fold {
        private XPathContext context;
        private AtomicValue zeroValue; // null means empty sequence
        private AtomicValue data;
        private boolean atStart = true;
        private ConversionRules rules;
        private StringConverter toDouble;

        public SumFold(XPathContext context, AtomicValue zeroValue) {
            this.context = context;
            this.zeroValue = zeroValue;
            this.rules = context.getConfiguration().getConversionRules();
            this.toDouble = BuiltInAtomicType.DOUBLE.getStringConverter(rules);
        }

        /**
         * Process one item in the input sequence, returning a new copy of the working data
         *
         * @param item the item to be processed from the input sequence
         * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs
         */
        @Override
        public void processItem(Item item) throws XPathException {
            AtomicValue next = (AtomicValue)item;
            if (atStart) {
                atStart = false;
                if (next instanceof UntypedAtomicValue) {
                    data = toDouble.convert(next).asAtomic();
                    return;
                } else if (next instanceof NumericValue || next instanceof DayTimeDurationValue || next instanceof YearMonthDurationValue) {
                    data = next;
                    return;
                } else {
                    XPathException err = new XPathException(
                        "Input to sum() contains a value of type " +
                                next.getPrimitiveType().getDisplayName() +
                                " which is neither numeric, nor a duration");
                    err.setXPathContext(context);
                    err.setErrorCode("FORG0006");
                    throw err;
                }
            }

            if (data instanceof NumericValue) {
                if (next instanceof UntypedAtomicValue) {
                    next = toDouble.convert(next).asAtomic();
                } else if (!(next instanceof NumericValue)) {
                    XPathException err = new XPathException("Input to sum() contains a mix of numeric and non-numeric values");
                    err.setXPathContext(context);
                    err.setErrorCode("FORG0006");
                    throw err;
                }
                data = ArithmeticExpression.compute(data, Calculator.PLUS, next, context);
            } else if (data instanceof DurationValue) {
                if (!((data instanceof DayTimeDurationValue) || (data instanceof YearMonthDurationValue))) {
                    XPathException err = new XPathException("Input to sum() contains a duration that is neither a dayTimeDuration nor a yearMonthDuration");
                    err.setXPathContext(context);
                    err.setErrorCode("FORG0006");
                    throw err;
                }
                if (!(next instanceof DurationValue)) {
                    XPathException err = new XPathException("Input to sum() contains a mix of duration and non-duration values");
                    err.setXPathContext(context);
                    err.setErrorCode("FORG0006");
                    throw err;
                }
                data = ((DurationValue) data).add((DurationValue) next);
            } else {
                XPathException err = new XPathException(
                        "Input to sum() contains a value of type " +
                                data.getPrimitiveType().getDisplayName() +
                                " which is neither numeric, nor a duration");
                err.setXPathContext(context);
                err.setErrorCode("FORG0006");
                throw err;
            }
        }

        /**
         * Ask whether the computation has completed. A function that can deliver its final
         * result without reading the whole input should return true; this will be followed
         * by a call on result() to deliver the final result.
         * @return true if the result of the function is now available even though not all
         * items in the sequence have been processed
         */
        @Override
        public boolean isFinished() {
            return data instanceof DoubleValue && data.isNaN();
        }

        /**
         * Compute the final result of the function, when all the input has been processed
         *
         * @return the result of the function
         */
        @Override
        public Sequence result() {
            if (atStart) {
                return zeroValue == null ? EmptySequence.getInstance() : zeroValue;
            } else {
                return data;
            }
        }
    }

    @Override
    public String getCompilerName() {
        return "SumCompiler";
    }

}

