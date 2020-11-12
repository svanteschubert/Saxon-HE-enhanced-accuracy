////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.StringConverter;
import net.sf.saxon.value.*;


/**
 * Implementation of the fn:avg function
 */
public class Average extends FoldingFunction {

    /**
     * Determine the cardinality of the function.
     */

    @Override
    public int getCardinality(Expression[] arguments) {
        if (!Cardinality.allowsZero(arguments[0].getCardinality())) {
            return StaticProperty.EXACTLY_ONE;
        } else {
            return super.getCardinality(arguments);
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
    public Fold getFold(XPathContext context, Sequence... additionalArguments) {
        return new AverageFold(context);
    }

    private class AverageFold implements Fold {
        private XPathContext context;
        private AtomicValue data;
        private boolean atStart = true;
        private ConversionRules rules;
        private StringConverter toDouble;
        private int count = 0;

        public AverageFold(XPathContext context) {
            this.context = context;
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
            if (next instanceof UntypedAtomicValue) {
                next = toDouble.convert((UntypedAtomicValue)next).asAtomic();
            }
            count++;
            if (atStart) {
                if (next instanceof NumericValue || next instanceof DayTimeDurationValue || next instanceof YearMonthDurationValue) {
                    data = next;
                    atStart = false;
                } else if (next instanceof DurationValue) {
                    throw new XPathException(
                            "Input to avg() contains a duration (" + Err.depict(next) +
                                    ") that is neither an xs:dayTimeDuration nor an xs:yearMonthDuration", "FORG0006");
                } else {
                    throw new XPathException(
                            "Input to avg() contains a value (" + Err.depict(next) +
                                    ") that is neither numeric, nor a duration", "FORG0006");
                }
            } else {
                if (data instanceof NumericValue) {
                    if (!(next instanceof NumericValue)) {
                        throw new XPathException(
                            "Input to avg() contains a mix of numeric and non-numeric values", "FORG0006");
                    }
                    data = ArithmeticExpression.compute(data, Calculator.PLUS, next, context);
                } else if (data instanceof DurationValue) {
                    if (!(next instanceof DurationValue)) {
                        throw new XPathException(
                            "Input to avg() contains a mix of duration and non-duration values", "FORG0006");
                    }
                    try {
                        data = ((DurationValue) data).add((DurationValue) next);
                    } catch (XPathException e) {
                        if ("XPTY0004".equals(e.getErrorCodeLocalPart())){
                            e.setErrorCode("FORG0006");
                        }
                        throw e;
                    }
                } else {
                    throw new XPathException(
                        "Input to avg() contains a value (" + Err.depict(data) +
                                ") that is neither numeric, nor a duration", "FORG0006");
                }
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
         * @throws net.sf.saxon.trans.XPathException
         *          if a dynamic error occurs
         */
        @Override
        public Sequence result() throws XPathException {
            if (atStart) {
                return EmptySequence.getInstance();
            } else if (data instanceof NumericValue) {
                return ArithmeticExpression.compute(data, Calculator.DIV, new Int64Value(count), context);
            } else {
                return ((DurationValue) data).divide(count);
            }
        }
    }



}

