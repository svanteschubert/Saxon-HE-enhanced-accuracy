////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.One;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.StringValue;

/**
 * Implement the XPath string-length() function
 */

public class StringLength_1 extends ScalarSystemFunction {


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
        return new IntegerValue[]{Int64Value.ZERO, Expression.MAX_STRING_LENGTH};
    }

    @Override
    public ZeroOrOne resultWhenEmpty() {
        return One.integer(0);
    }

    @Override
    public AtomicValue evaluate(Item arg, XPathContext context) throws XPathException {
        if (arg instanceof StringValue) {
            return Int64Value.makeIntegerValue(((StringValue) arg).getStringLength());
        } else {
            CharSequence s;
            try {
                s = arg.getStringValueCS();
            } catch (UnsupportedOperationException e) {
                throw new XPathException("Cannot get the string value of a function item", "FOTY0013");
            }
            return Int64Value.makeIntegerValue(StringValue.getStringLength(s));
        }
    }

    @Override
    public String getCompilerName() {
        return "StringLengthCompiler";
    }

}

