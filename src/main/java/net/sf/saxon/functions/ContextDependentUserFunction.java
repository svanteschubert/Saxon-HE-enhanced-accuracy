////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.functions.hof.CurriedFunction;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.value.AtomicValue;

public class ContextDependentUserFunction extends AbstractFunction implements IContextAccessorFunction {

    private final UserFunction underlyingFunction;
    private final int arity;
    public ContextDependentUserFunction(UserFunction underlyingFunction, int arity) {
        this.underlyingFunction = underlyingFunction;
        this.arity = arity;
    }

    @Override
    public boolean dependsOnContext() {
        return true;
    }

    /**
     * Call the Callable.
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences.
     *                  <p>Generally it is advisable, if calling iterate() to process a supplied sequence, to
     *                  call it only once; if the value is required more than once, it should first be converted
     *                  to a {@link GroundedValue} by calling the utility method
     *                  SequenceTool.toGroundedValue().</p>
     *                  <p>If the expected value is a single item, the item should be obtained by calling
     *                  Sequence.head(): it cannot be assumed that the item will be passed as an instance of
     *                  {@link Item} or {@link AtomicValue}.</p>
     *                  <p>It is the caller's responsibility to perform any type conversions required
     *                  to convert arguments to the type expected by the callee. An exception is where
     *                  this Callable is explicitly an argument-converting wrapper around the original
     *                  Callable.</p>
     * @return the result of the evaluation, in the form of a Sequence. It is the responsibility
     * of the callee to ensure that the type of result conforms to the expected result type.
     * @throws XPathException if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return bindContext(context).call(context, arguments);
    }

    /**
     * Bind context information to appear as part of the function's closure. If this method
     * has been called, the supplied context will be used in preference to the
     * context at the point where the function is actually called.
     *
     * @param context the context to which the function applies. Must not be null.
     */
    @Override
    public FunctionItem bindContext(XPathContext context) throws XPathException {
        Sequence[] boundValues = new Sequence[underlyingFunction.getArity()];
        int[] mapping = new int[arity];
        for (int i=arity; i< underlyingFunction.getArity(); i++) {
            boundValues[i] = ExpressionTool.eagerEvaluate(
                    underlyingFunction.getParameterDefinitions()[i].getDefaultValueExpression().get(), context);
        }
        for (int i=0; i<arity; i++) {
            mapping[i] = i;
        }
        return new CurriedFunction(underlyingFunction, boundValues, mapping);
    }

    /**
     * Get the item type of the function item
     *
     * @return the function item's type
     */
    @Override
    public FunctionItemType getFunctionItemType() {
        return underlyingFunction.getFunctionItemType(arity);
    }

    /**
     * Get the name of the function, or null if it is anonymous
     *
     * @return the function name, or null for an anonymous inline function
     */
    @Override
    public StructuredQName getFunctionName() {
        return underlyingFunction.getFunctionName();
    }

    /**
     * Get the arity of the function
     *
     * @return the number of arguments in the function signature
     */
    @Override
    public int getArity() {
        return arity;
    }

    /**
     * Get a description of this function for use in error messages. For named functions, the description
     * is the function name (as a lexical QName). For others, it might be, for example, "inline function",
     * or "partially-applied ends-with function".
     *
     * @return a description of the function for use in error messages
     */
    @Override
    public String getDescription() {
        return underlyingFunction.getDescription();
    }
}

