////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.IntegerValue;

/**
 * A ConstantFunction is a zero-argument function that always delivers the same result, supplied
 * at the time the function is instantiated.
 */

public class ConstantFunction extends SystemFunction  {

    public GroundedValue value;

    public ConstantFunction(GroundedValue value) {
        this.value = value;
    }

    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return value;
    }

    @Override
    public Expression makeFunctionCall(Expression[] arguments) {
        return Literal.makeLiteral(value);
    }

    @Override
    public IntegerValue[] getIntegerBounds() {
        if (value instanceof IntegerValue) {
            return new IntegerValue[]{(IntegerValue) value, (IntegerValue) value};
        } else {
            return null;
        }
    }

    public static class True extends ConstantFunction {
        public True() {
            super(BooleanValue.TRUE);
        }
    }

    public static class False extends ConstantFunction {
        public False() {
            super(BooleanValue.FALSE);
        }
    }

}

