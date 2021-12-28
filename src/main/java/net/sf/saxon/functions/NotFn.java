////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Negatable;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.BooleanValue;

/**
 * This class supports the XPath functions boolean(), not(), true(), and false()
 */


public class NotFn extends SystemFunction {


    @Override
    public void supplyTypeInformation(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType, Expression[] arguments) throws XPathException {
        XPathException err = TypeChecker.ebvError(arguments[0], visitor.getConfiguration().getTypeHierarchy());
        if (err != null) {
            throw err;
        }
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
    public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        return BooleanValue.get(!ExpressionTool.effectiveBooleanValue(arguments[0].iterate()));
    }

    /**
     * Make an expression that either calls this function, or that is equivalent to a call
     * on this function
     *
     * @param arguments the supplied arguments to the function call
     * @return either a function call on this function, or an expression that delivers
     * the same result
     */
    @Override
    public Expression makeFunctionCall(Expression[] arguments) {
        return new SystemFunctionCall(this, arguments) {
            /**
             * Evaluate the effective boolean value
             */

            @Override
            public boolean effectiveBooleanValue(XPathContext c) throws XPathException {
                try {
                    return !getArg(0).effectiveBooleanValue(c);
                } catch (XPathException e) {
                    e.maybeSetLocation(getLocation());
                    e.maybeSetContext(c);
                    throw e;
                }
            }
        };
    }

    /**
     * Allow the function to create an optimized call based on the values of the actual arguments
     *
     * @param visitor     the expression visitor
     * @param contextInfo information about the context item
     * @param arguments   the supplied arguments to the function call
     * @return either a function call on this function, or an expression that delivers
     * the same result, or null indicating that no optimization has taken place
     * @throws net.sf.saxon.trans.XPathException if an error is detected
     */
    @Override
    public Expression makeOptimizedFunctionCall(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, Expression... arguments) throws XPathException {
        TypeHierarchy th = visitor.getStaticContext().getConfiguration().getTypeHierarchy();
        if (arguments[0] instanceof Negatable && ((Negatable) arguments[0]).isNegatable(th)) {
            return ((Negatable) arguments[0]).negate();
        }
        if (arguments[0].getItemType() instanceof NodeTest) {
            SystemFunction empty = SystemFunction.makeFunction("empty", getRetainedStaticContext(), 1);
            return empty.makeFunctionCall(arguments[0]).optimize(visitor, contextInfo);
        }
        return null;
    }

    @Override
    public String getCompilerName() {
        return "NotFnCompiler";
    }

    @Override
    public String getStreamerName() {
        return "NotFn";
    }
}

