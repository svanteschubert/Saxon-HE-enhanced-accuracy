////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.One;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.regex.EmptyString;
import net.sf.saxon.regex.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.TypeHierarchy;
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

    public static UnicodeString substring(StringValue sv, NumericValue start) {
        UnicodeString s = sv.getUnicodeString();
        int slength = s.uLength();

        long lstart;
        if (start instanceof Int64Value) {
            //noinspection RedundantCast
            lstart = ((Int64Value) start).longValue();
            if (lstart > slength) {
                return EmptyString.THE_INSTANCE;
            } else if (lstart <= 0) {
                lstart = 1;
            }
        } else {
            //NumericValue rstart = start.round();
            // We need to be careful to handle cases such as plus/minus infinity
            if (start.isNaN()) {
                return EmptyString.THE_INSTANCE;
            } else if (start.signum() <= 0) {
                return s;
            } else if (start.compareTo(slength) > 0) {
                // this works even where the string contains surrogate pairs,
                // because the Java length is always >= the XPath length
                return EmptyString.THE_INSTANCE;
            } else {
                lstart = Math.round(start.getDoubleValue());
            }
        }

        if (lstart > s.uLength()) {
            return EmptyString.THE_INSTANCE;
        }
        return s.uSubstring((int) lstart - 1, s.uLength());
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

    public static UnicodeString substring(StringValue sv, NumericValue start, /*@NotNull*/ NumericValue len) {

        int slength = sv.getStringLengthUpperBound();

        long lstart;
        if (start instanceof Int64Value) {
            //noinspection RedundantCast
            lstart = ((Int64Value) start).longValue();
            if (lstart > slength) {
                return EmptyString.THE_INSTANCE;
            }
        } else {
            // We need to be careful to handle cases such as plus/minus infinity and NaN
            if (start.isNaN()) {
                return EmptyString.THE_INSTANCE;
            } else if (start.compareTo(slength) > 0) {
                // this works even where the string contains surrogate pairs,
                // because the Java length is always >= the XPath length
                return EmptyString.THE_INSTANCE;
            } else {
                double dstart = start.getDoubleValue();
                lstart = Double.isInfinite(dstart) ? -Integer.MAX_VALUE : Math.round(dstart);
            }
        }

        long llen;
        if (len instanceof Int64Value) {
            llen = ((Int64Value) len).longValue();
            if (llen <= 0) {
                return EmptyString.THE_INSTANCE;
            }
        } else {
            if (len.isNaN()) {
                return EmptyString.THE_INSTANCE;
            }
            if (len.signum() <= 0) {
                return EmptyString.THE_INSTANCE;
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
            return EmptyString.THE_INSTANCE;
        }

        UnicodeString us = sv.getUnicodeString();
        int clength = us.uLength();
        int a1 = (int) lstart - 1;
        if (a1 >= clength) {
            return EmptyString.THE_INSTANCE;
        }
        int a2 = Math.min(clength, (int) lend - 1);
        if (a1 < 0) {
            if (a2 < 0) {
                return EmptyString.THE_INSTANCE;
            } else {
                a1 = 0;
            }
        }
        return us.uSubstring(a1, a2);
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
    public ZeroOrOne call(XPathContext context, Sequence[] arguments) throws XPathException {
        StringValue arg0 = (StringValue) arguments[0].head();
        if (arg0 == null) {
            return One.string("");
        }
        NumericValue arg1 = (NumericValue) arguments[1].head();
        if (arguments.length == 2) {
            return new ZeroOrOne(StringValue.makeStringValue(substring(arg0, arg1)));
        } else {
            NumericValue arg2 = (NumericValue) arguments[2].head();
            return new ZeroOrOne(StringValue.makeStringValue(substring(arg0, arg1, arg2)));
        }
    }

    @Override
    public String getCompilerName() {
        return "SubstringCompiler";
    }
}

