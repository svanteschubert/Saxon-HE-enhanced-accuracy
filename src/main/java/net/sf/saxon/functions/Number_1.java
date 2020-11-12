////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.FunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.One;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ConversionResult;
import net.sf.saxon.type.Converter;
import net.sf.saxon.type.ValidationFailure;
import net.sf.saxon.value.*;

/**
 * Implements the XPath fn:number() function when called with one argument.
 * Note: this function accepts the value "+INF" whether or not XSD 1.1 is enabled.
 */

public class Number_1 extends ScalarSystemFunction {

    @Override
    public AtomicValue evaluate(Item arg, XPathContext context) throws XPathException {
        return toNumber((AtomicValue)arg);
    }

    @Override
    public ZeroOrOne resultWhenEmpty() {
        return new One(DoubleValue.NaN);
    }

    /**
     * Type-check a calling expression.
     */

    /*@NotNull*/
    @Override
    public Expression typeCheckCaller(FunctionCall caller, ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) {
        if (caller.getArg(0).isCallOn(Number_1.class)) {
            // happens through repeated rewriting
            caller.setArg(0, ((FunctionCall)caller.getArg(0)).getArg(0));
        }
        return caller;
    }

    /**
     * The actual implementation logic
     * @param arg0 the atomic value to be converted
     * @return the result of the conversion
     */

    public static DoubleValue toNumber(AtomicValue arg0) {
        if (arg0 instanceof BooleanValue) {
            return Converter.BooleanToDouble.INSTANCE.convert((BooleanValue)arg0);
        } else if (arg0 instanceof NumericValue) {
            return (DoubleValue) Converter.NumericToDouble.INSTANCE.convert((NumericValue)arg0).asAtomic();
        } else if (arg0 instanceof StringValue && !(arg0 instanceof AnyURIValue)) {
            // Always use the XSD 1.1 rules, which permit "+INF"
            ConversionResult cr = StringToDouble11.getInstance().convert((StringValue)arg0);
            if (cr instanceof ValidationFailure) {
                return DoubleValue.NaN;
            } else {
                return (DoubleValue) cr;
            }
        } else {
            return DoubleValue.NaN;
        }
    }

    /**
     * Static method to perform the same conversion as the number() function. This is different from the
     * convert(Type.DOUBLE) in that it produces NaN rather than an error for non-numeric operands.
     *
     * @param value  the value to be converted
     * @param config the Saxon configuration
     * @return the result of the conversion
     */

    public static DoubleValue convert(AtomicValue value, Configuration config) {
        try {
            if (value == null) {
                return DoubleValue.NaN;
            }
            if (value instanceof BooleanValue) {
                return new DoubleValue(((BooleanValue) value).getBooleanValue() ? 1.0e0 : 0.0e0);
            }
            if (value instanceof DoubleValue) {
                return (DoubleValue) value;
            }
            if (value instanceof NumericValue) {
                return new DoubleValue(((NumericValue) value).getDoubleValue());
            }
            if (value instanceof StringValue && !(value instanceof AnyURIValue)) {
                double d = config.getConversionRules().getStringToDoubleConverter().stringToNumber(value.getStringValueCS());
                return new DoubleValue(d);
            }
            return DoubleValue.NaN;
        } catch (NumberFormatException e) {
            return DoubleValue.NaN;
        }
    }

    @Override
    public String getCompilerName() {
        return "NumberFnCompiler";
    }


}

