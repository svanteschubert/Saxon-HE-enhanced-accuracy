////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.ContextItemExpression;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

/**
 * A ContextItemAccessorFunction is a function that takes no arguments, but operates implicitly on the
 * context item. In the case of a dynamic call, the context item that is used is the one at the point
 * where the function item is created.
 *
 * Because these functions depend on the context only, with no additional arguments, the function can
 * be evaluated as soon as the context is bound. The function is therefore replaced at this stage with a
 * constant function, that is, one that always returns the same value.
 */

public class ContextItemAccessorFunction extends ContextAccessorFunction {

    /**
     * Bind a context item to appear as part of the function's closure. If this method
     * has been called, the supplied context item will be used in preference to the
     * context item at the point where the function is actually called.
     * @param context the context to which the function applies. Must not be null.
     */

    @Override
    public Function bindContext(XPathContext context) throws XPathException {
        final Item ci = context.getContextItem();
        if (ci == null) {
            Callable callable = (context1, arguments) -> {
                throw new XPathException("Context item for " +
                    getFunctionName().getDisplayName() + " is absent", "XPDY0002");
            };
            FunctionItemType fit = new SpecificFunctionType(new SequenceType[]{}, SequenceType.ANY_SEQUENCE);
            return new CallableFunction(0, callable, fit);
        }
        ConstantFunction fn = new ConstantFunction(evaluate(ci, context));
        fn.setDetails(getDetails());
        fn.setRetainedStaticContext(getRetainedStaticContext());
        return fn;
    }

    /**
     * Evaluate the function. This is done by creating a function of the same name, with the context item
     * as an explicit argument, and evaluating that.
     * @param item the context item
     * @param context XPath dynamic context (not normally used)
     * @return the result of the function
     * @throws XPathException in the event of a dynamic error
     */

    public GroundedValue evaluate(Item item, XPathContext context) throws XPathException {
        SystemFunction f = SystemFunction.makeFunction(getDetails().name.getLocalPart(), getRetainedStaticContext(), 1);
        return f.call(context, new Sequence[]{item}).materialize();
    }

    /**
     * Evaluate the expression. This method is not normally used, but is provided to satisfy the
     * interface.
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        // Shouldn't be called; but we handle it if it is
        return evaluate(context.getContextItem(), context);
    }

    /**
     * Make a static call on this function, with specified arguments.
     * @param arguments the supplied arguments to the function call. This will always
     *                  be an empty array, since this is a zero-arity function.
     * @return This implementation returns a call on the equivalent arity-1 version
     * of the function, supplying "." as an explicit argument.
     */

    @Override
    public Expression makeFunctionCall(Expression[] arguments) {
        Expression arg = new ContextItemExpression();
        return SystemFunction.makeCall(getFunctionName().getLocalPart(), getRetainedStaticContext(), arg);
    }

    /**
     * Create a system function call on this function in which the context item
     * is supplied as an explicit argument
     * @return an equivalent function call in which the context item is supplied explicitly.
     */

    public Expression makeContextItemExplicit() {
        Expression[] args = new Expression[]{new ContextItemExpression()};
        return SystemFunction.makeCall(getFunctionName().getLocalPart(), getRetainedStaticContext(), args);
    }

    /**
     * Subclass of ContextItemAccessorFunction to handle string-length() and normalize-space().
     * These functions differ by taking string(.) rather than (.) as the implicit argument.
     */

    public static class StringAccessor extends ContextItemAccessorFunction {

        @Override
        public Expression makeFunctionCall(Expression[] arguments) {
            Expression ci = new ContextItemExpression();
            Expression sv = SystemFunction.makeCall("string", getRetainedStaticContext(), ci);
            return SystemFunction.makeCall(getFunctionName().getLocalPart(), getRetainedStaticContext(), sv);
        }

        @Override
        public GroundedValue evaluate(Item item, XPathContext context) throws XPathException {
            SystemFunction f = SystemFunction.makeFunction(getDetails().name.getLocalPart(), getRetainedStaticContext(), 1);
            StringValue val = new StringValue(item.getStringValueCS());
            return f.call(context, new Sequence[]{val}).materialize();
        }

    }

    /**
     * Subclass of ContextItemAccessorFunction to handle number().
     * This function differs by taking data(.) rather than (.) as the implicit argument.
     */

    public static class Number_0 extends ContextItemAccessorFunction {

        @Override
        public Expression makeFunctionCall(Expression[] arguments) {
            Expression ci = new ContextItemExpression();
            Expression sv = SystemFunction.makeCall("data", getRetainedStaticContext(), ci);
            return SystemFunction.makeCall(getFunctionName().getLocalPart(), getRetainedStaticContext(), sv);
        }

        @Override
        public GroundedValue evaluate(Item item, XPathContext context) throws XPathException {
            SystemFunction f = SystemFunction.makeFunction(getDetails().name.getLocalPart(), getRetainedStaticContext(), 1);
            AtomicSequence val = item.atomize();
            switch (val.getLength()) {
                case 0:
                    return DoubleValue.NaN;
                case 1:
                    return f.call(context, new Sequence[]{val.head()}).materialize();
                default:
                    XPathException err = new XPathException(
                        "When number() is called with no arguments, the atomized value of the context node must " +
                            "not be a sequence of several atomic values", "XPTY0004");
                    err.setIsTypeError(true);
                    throw err;
            }
        }

    }


}

