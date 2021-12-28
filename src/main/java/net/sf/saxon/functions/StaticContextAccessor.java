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
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;

/**
 * A StaticContextAccessor is a function that takes no arguments, but operates implicitly on the
 * static context. In the case of a dynamic call, the context that is used is the one at the point
 * where the function item is created.
 */

public abstract class StaticContextAccessor extends SystemFunction {

    /**
     * Method to do the actual evaluation, which must be implemented in a subclass
     * @param rsc the retained static context
     * @return the result of the evaluation
     */

    public abstract AtomicValue evaluate(RetainedStaticContext rsc);

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
    public AtomicValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        return evaluate(getRetainedStaticContext());
    }

    @Override
    public Expression makeFunctionCall(Expression[] arguments) {
        return Literal.makeLiteral(evaluate(getRetainedStaticContext()));
    }

    /**
     * Implement the XPath function default-collation()
     */

    public static class DefaultCollation extends StaticContextAccessor {
        @Override
        public AtomicValue evaluate(RetainedStaticContext rsc) {
            return new StringValue(rsc.getDefaultCollationName());
        }
    }

}

