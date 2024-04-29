////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.NumericValue;
import net.sf.saxon.value.StringValue;

/**
 * This class implements the XPath substring() function
 */

public class Substring extends SystemFunction implements Callable {

    /**
     * Type-check the expression. This also calls preEvaluate() to evaluate the function
     * if all the arguments are constant; functions that do not require this behavior
     * can override the preEvaluate method.
     */

    /*@NotNull*/
    @Override
    public Expression typeCheckCaller(FunctionCall caller, ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        Expression e2 = super.typeCheckCaller(caller, visitor, contextInfo);
        if (e2 != caller) {
            return e2;
        }
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        if (caller.getArg(1).isCallOn(Number_1.class)) {
            Expression a1 = ((StaticFunctionCall)caller.getArg(1)).getArg(0);
            if (th.isSubType(a1.getItemType(), BuiltInAtomicType.INTEGER)) {
                caller.setArg(1, a1);
            }
        }
        if (getArity() > 2 && caller.getArg(2).isCallOn(Number_1.class)) {
            Expression a2 = ((StaticFunctionCall) caller.getArg(2)).getArg(0);
            if (th.isSubType(a2.getItemType(), BuiltInAtomicType.INTEGER)) {
                caller.setArg(2, a2);
            }
        }
        return caller;
    }

    /**
     * Implement the substring function with two arguments.
     *
     * @param sv    the string value
     * @param start the numeric offset (1-based) of the first character to be included in the result
     *              (if not an integer, the XPath rules apply)
     * @return the substring starting at this position.
     */

    public static StringValue substring(StringValue sv, NumericValue start) {
        long slength = sv.length();

        long lstart;
        if (start instanceof Int64Value) {
            //noinspection RedundantCast
            lstart = ((Int64Value) start).longValue();
            if (lstart > slength) {
                return StringValue.EMPTY_STRING;
            } else if (lstart <= 0) {
                lstart = 1;
            }
        } else {
            //NumericValue rstart = start.round();
            // We need to be careful to handle cases such as plus/minus infinity
            if (start.isNaN()) {
                return StringValue.EMPTY_STRING;
            } else if (start.signum() <= 0) {
                return sv;
            } else if (start.compareTo(slength) > 0) {
                return StringValue.EMPTY_STRING;
            } else {
                lstart = Math.round(start.getDoubleValue());
            }
        }

        if (lstart > slength) {
            return StringValue.EMPTY_STRING;
        }
        return new StringValue(sv.getContent().substring((int) lstart - 1, slength));
    }

    /**
     * Implement the substring function with three arguments.
     *
     *
     * @param sv      the string value
     * @param start   the numeric offset (1-based) of the first character to be included in the result
     *                (if not an integer, the XPath rules apply)
     * @param len     the length of the required substring (again, XPath rules apply)
     * @return the substring starting at this position.
     */

    public static StringValue substring(StringValue sv, NumericValue start, /*@NotNull*/ NumericValue len) {

        long slength = sv.length();

        long lstart;
        if (start instanceof Int64Value) {
            //noinspection RedundantCast
            lstart = ((Int64Value) start).longValue();
            if (lstart > slength) {
                return StringValue.EMPTY_STRING;
            }
        } else {
            // We need to be careful to handle cases such as plus/minus infinity and NaN
            if (start.isNaN()) {
                return StringValue.EMPTY_STRING;
            } else if (start.compareTo(slength) > 0) {
                // this works even where the string contains surrogate pairs,
                // because the Java length is always >= the XPath length
                return StringValue.EMPTY_STRING;
            } else {
                double dstart = start.getDoubleValue();
                lstart = Double.isInfinite(dstart) ? -Integer.MAX_VALUE : Math.round(dstart);
            }
        }

        long llen;
        if (len instanceof Int64Value) {
            llen = ((Int64Value) len).longValue();
            if (llen <= 0) {
                return StringValue.EMPTY_STRING;
            }
        } else {
            if (len.isNaN()) {
                return StringValue.EMPTY_STRING;
            }
            if (len.signum() <= 0) {
                return StringValue.EMPTY_STRING;
            }
            double dlen = len.getDoubleValue();
            if (Double.isInfinite(dlen)) {
                llen = Integer.MAX_VALUE;
            } else {
                llen = Math.round(len.getDoubleValue());
            }
        }
        long lend = lstart + llen;
        if (lend < lstart) {
            return StringValue.EMPTY_STRING;
        }

        int a1 = (int) lstart - 1;
        if (a1 >= slength) {
            return StringValue.EMPTY_STRING;
        }
        long a2 = Math.min(slength, (int) lend - 1);
        if (a1 < 0) {
            if (a2 < 0) {
                return StringValue.EMPTY_STRING;
            } else {
                a1 = 0;
            }
        }
        return new StringValue(sv.getContent().substring(a1, a2));
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public StringValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        StringValue arg0 = (StringValue) arguments[0].head();
        if (arg0 == null) {
            return StringValue.EMPTY_STRING;
        }
        NumericValue arg1 = (NumericValue) arguments[1].head();
        if (arguments.length == 2) {
            return substring(arg0, arg1);
        } else {
            NumericValue arg2 = (NumericValue) arguments[2].head();
            if (arg2 == null) {
                // Third argument can be an empty sequence in 4.0
                if (getRetainedStaticContext().getPackageData().getHostLanguageVersion() < 40) {
                    XPathException err = new XPathException("3rd argument of substring() must not be an empty sequence (unless 4.0 is enabled)", "XPTY0004");
                    err.setIsTypeError(true);
                    throw err;
                } else {
                    return substring(arg0, arg1);
                }
            }
            return substring(arg0, arg1, arg2);
        }
    }

    /**
     * Make an elaborator for a system function call on this function
     *
     * @return a suitable elaborator; or null if no custom elaborator is available
     */
    @Override
    public Elaborator getElaborator() {
        return new SubstringFnElaborator();
    }

    public static class SubstringFnElaborator extends ItemElaborator {

        public ItemEvaluator elaborateForItem() {
            final SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            final ItemEvaluator arg0Eval = fnc.getArg(0).makeElaborator().elaborateForItem();
            final ItemEvaluator arg1Eval = fnc.getArg(1).makeElaborator().elaborateForItem();
            final boolean nullable = Cardinality.allowsZero(fnc.getArg(0).getCardinality());
            if (fnc.getArity() == 2) {
                return context -> {
                    StringValue sv = (StringValue)arg0Eval.eval(context);
                    if (nullable && sv == null) {
                        return StringValue.EMPTY_STRING;
                    }
                    NumericValue start = (NumericValue)arg1Eval.eval(context);
                    return substring(sv, start);
                };
            } else {
                final ItemEvaluator arg2Eval = fnc.getArg(2).makeElaborator().elaborateForItem();
                final boolean disallowEmpty = fnc.getRetainedStaticContext().getPackageData().getHostLanguageVersion() < 40;
                return context -> {
                    StringValue sv = (StringValue) arg0Eval.eval(context);
                    if (nullable && sv == null) {
                        return StringValue.EMPTY_STRING;
                    }
                    NumericValue start = (NumericValue) arg1Eval.eval(context);
                    NumericValue len = (NumericValue) arg2Eval.eval(context);
                    if (len == null) {
                        // Third argument can be an empty sequence in 4.0
                        if (disallowEmpty) {
                            XPathException err = new XPathException("3rd argument of substring() must not be an empty sequence (unless 4.0 is enabled)", "XPTY0004");
                            err.setIsTypeError(true);
                            throw err;
                        } else {
                            return substring(sv, start);
                        }
                    }
                    return substring(sv, start, len);
                };
            }
        }

    }
}

