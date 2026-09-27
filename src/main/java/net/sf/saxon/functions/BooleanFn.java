////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.elab.BooleanEvaluator;
import net.sf.saxon.expr.elab.BooleanElaborator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.pattern.nodetest.NodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.BooleanValue;

/**
 * This class supports the XPath function boolean()
 */


public class BooleanFn extends SystemFunction implements ArityOneFunction {

    @Override
    public void supplyTypeInformation(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType, Expression[] arguments) throws XPathException {
        TypeChecker.ebvTypeCheck(arguments[0], visitor);
    }

    /**
     * Optimize an expression whose effective boolean value is required. It is appropriate
     * to apply this rewrite to any expression whose value will be obtained by calling
     * the Expression.effectiveBooleanValue() method (and not otherwise)
     *
     * @param exp             the expression whose EBV is to be evaluated
     * @param visitor         an expression visitor
     * @param contextItemType the type of the context item for this expression
     * @return an expression that returns the EBV of exp, or null if no optimization was possible
     * @throws XPathException if static errors are found
     */

    public static Expression rewriteEffectiveBooleanValue(
            Expression exp, ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        Configuration config = visitor.getConfiguration();
        TypeHierarchy th = config.getTypeHierarchy();
        boolean forStreaming = visitor.isOptimizeForStreaming();
        Expression e2 = ExpressionTool.unsortedIfHomogeneous(exp, forStreaming);
        boolean changed = e2 != exp;
        exp = e2;
        if (exp instanceof Literal) {
            GroundedValue val = ((Literal)exp).getGroundedValue();
            if (val instanceof BooleanValue) {
                return exp;
            }
            return Literal.makeLiteral(
                BooleanValue.get(
                    ExpressionTool.effectiveBooleanValue(val.iterate())), exp);
        }
        if (exp instanceof ValueComparison vc) {
            if (vc.getResultWhenEmpty() == null) {
                vc.setResultWhenEmpty(BooleanValue.FALSE);
            }
            return exp;
        } else if (exp.isCallOn(BooleanFn.class)) {
            return ((SystemFunctionCall) exp).getArg(0);
        } else if (BuiltInAtomicType.BOOLEAN.hasSubType(exp.getItemType()) &&
                exp.getCardinality() == StaticProperty.EXACTLY_ONE) {
            return exp;
        } else if (exp.isCallOn(Count.class)) {
            // rewrite boolean(count(x)) => exists(x)
            Expression exists = SystemFunction.makeCall("exists", exp.getRetainedStaticContext(), ((SystemFunctionCall) exp).getArg(0));
            ExpressionTool.copyLocationInfo(exp, exists);
            return exists.optimize(visitor, contextItemType);
        } else if (exp.getItemType() instanceof NodeTest) {
            // rewrite boolean(x) => exists(x)
            Expression exists = SystemFunction.makeCall("exists", exp.getRetainedStaticContext(), exp);
            ExpressionTool.copyLocationInfo(exp, exists);
            return exists.optimize(visitor, contextItemType);
        } else {
            return changed ? exp : null;
        }
    }

    @Override
    public BooleanValue call(XPathContext c, Sequence[] arguments) throws XPathException {
        boolean bValue = ExpressionTool.effectiveBooleanValue(arguments[0].iterate());
        return BooleanValue.get(bValue);
    }

    @Override
    public BooleanValue call1(XPathContext c, Sequence arg0) throws XPathException {
        boolean bValue = ExpressionTool.effectiveBooleanValue(arg0.iterate());
        return BooleanValue.get(bValue);
    }

    @Override
    public Expression makeFunctionCall(Expression[] arguments) {
        return new SystemFunctionCall(this, arguments) {

            @Override
            public Expression optimize(/*@NotNull*/ ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
                Expression e = super.optimize(visitor, contextItemType);
                if (e == this) {
                    Expression ebv = rewriteEffectiveBooleanValue(this.getArg(0), visitor, contextItemType);
                    if (ebv != null) {
                        ebv = ebv.optimize(visitor, contextItemType);
                        if (ebv.getItemType() == BuiltInAtomicType.BOOLEAN &&
                            ebv.getCardinality() == StaticProperty.EXACTLY_ONE) {
                            ebv.setParentExpression(getParentExpression());
                            return ebv;
                        } else {
                            setArg(0, ebv);
                            adoptChildExpression(ebv);
                            return this;
                        }
                    }
                }
                return e;
            }

            @Override
            @CSharpModifiers(code={"public", "override"})
            public boolean effectiveBooleanValue(XPathContext c) throws XPathException {
                try {
                    return getArg(0).effectiveBooleanValue(c);
                } catch (XPathException e) {
                    throw e.maybeWithLocation(getLocation()).maybeWithContext(c);
                }
            }

            @Override
            @CSharpModifiers(code = {"public", "override"})
            public BooleanValue evaluateItem(XPathContext context) throws XPathException {
                return BooleanValue.get(effectiveBooleanValue(context));
            }
        };
    }

    @Override
    public String getStreamerName() {
        return "BooleanFn";
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new BooleanFnElaborator();
    }

    public static class BooleanFnElaborator extends BooleanElaborator {

        public BooleanEvaluator elaborateForBoolean() {
            SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            Expression arg = fnc.getArg(0);
            return arg.makeElaborator().elaborateForBoolean();
        }

    }
}
