////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
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

/**
 * A ConstantSystemFunction is a zero-argument function that always delivers the same result, supplied
 * at the time the function is instantiated. It is a subclass of system function, which means there must
 * be a "details" entry giving information about the function name, signature, etc
 */

public abstract class ConstantSystemFunction extends SystemFunction  {

    private final GroundedValue value;

    public ConstantSystemFunction(GroundedValue value) {
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

    public static class True extends ConstantSystemFunction {
        public True() {
            super(BooleanValue.TRUE);
        }
    }

    public static class False extends ConstantSystemFunction {
        public False() {
            super(BooleanValue.FALSE);
        }
    }

}

