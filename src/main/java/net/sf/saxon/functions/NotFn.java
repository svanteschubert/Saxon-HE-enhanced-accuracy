////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.elab.BooleanEvaluator;
import net.sf.saxon.expr.elab.BooleanElaborator;
import net.sf.saxon.expr.elab.Elaborator;
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
import net.sf.saxon.transpile.CSharpModifiers;
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
            @CSharpModifiers(code = {"public", "override"})
            public boolean effectiveBooleanValue(XPathContext c) throws XPathException {
                try {
                    return !this.getArg(0).effectiveBooleanValue(c);
                } catch (XPathException e) {
                    throw e.maybeWithLocation(this.getLocation()).maybeWithContext(c);
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
    public String getStreamerName() {
        return "NotFn";
    }

    /**
     * Make an elaborator for a system function call on this function
     *
     * @return a suitable elaborator; or null if no custom elaborator is available
     */
    @Override
    public Elaborator getElaborator() {
        return new NotFnElaborator();
    }

    public static class NotFnElaborator extends BooleanElaborator {

        public BooleanEvaluator elaborateForBoolean() {
            SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            Expression arg = fnc.getArg(0);
            BooleanEvaluator argEval = arg.makeElaborator().elaborateForBoolean();
            return context -> !argEval.eval(context);
        }


    }
}

