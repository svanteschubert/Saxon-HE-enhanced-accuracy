////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.BooleanValue;

/**
 * This class supports the XPath function boolean()
 */


public class BooleanFn extends SystemFunction {

    @Override
    public void supplyTypeInformation(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType, Expression[] arguments) throws XPathException {
        XPathException err = TypeChecker.ebvError(arguments[0], visitor.getConfiguration().getTypeHierarchy());
        if (err != null) {
            throw err;
        }
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
        exp = ExpressionTool.unsortedIfHomogeneous(exp, forStreaming);
        if (exp instanceof Literal) {
            GroundedValue val = ((Literal)exp).getValue();
            if (val instanceof BooleanValue) {
                return exp;
            }
            return Literal.makeLiteral(
                BooleanValue.get(
                    ExpressionTool.effectiveBooleanValue(val.iterate())), exp);
        }
        if (exp instanceof ValueComparison) {
            ValueComparison vc = (ValueComparison) exp;
            if (vc.getResultWhenEmpty() == null) {
                vc.setResultWhenEmpty(BooleanValue.FALSE);
            }
            return exp;
        } else if (exp.isCallOn(BooleanFn.class)) {
            return ((SystemFunctionCall) exp).getArg(0);
        } else if (th.isSubType(exp.getItemType(), BuiltInAtomicType.BOOLEAN) &&
                exp.getCardinality() == StaticProperty.EXACTLY_ONE) {
            return exp;
        } else if (exp.isCallOn(Count.class)) {
            // rewrite boolean(count(x)) => exists(x)
            Expression exists = SystemFunction.makeCall("exists", exp.getRetainedStaticContext(), ((SystemFunctionCall) exp).getArg(0));
            assert exists != null;
            ExpressionTool.copyLocationInfo(exp, exists);
            return exists.optimize(visitor, contextItemType);
        } else if (exp.getItemType() instanceof NodeTest) {
            // rewrite boolean(x) => exists(x)
            Expression exists = SystemFunction.makeCall("exists", exp.getRetainedStaticContext(), exp);
            assert exists != null;
            ExpressionTool.copyLocationInfo(exp, exists);
            return exists.optimize(visitor, contextItemType);
        } else {
            return null;
        }
    }

    @Override
    public BooleanValue call(XPathContext c, Sequence[] arguments) throws XPathException {
        boolean bValue = ExpressionTool.effectiveBooleanValue(arguments[0].iterate());
        return BooleanValue.get(bValue);
    }

    @Override
    public Expression makeFunctionCall(Expression[] arguments) {
        return new SystemFunctionCall(this, arguments) {

            @Override
            public Expression optimize(/*@NotNull*/ ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
                Expression e = super.optimize(visitor, contextItemType);
                if (e == this) {
                    Expression ebv = rewriteEffectiveBooleanValue(getArg(0), visitor, contextItemType);
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
            public boolean effectiveBooleanValue(XPathContext c) throws XPathException {
                try {
                    return getArg(0).effectiveBooleanValue(c);
                } catch (XPathException e) {
                    e.maybeSetLocation(getLocation());
                    e.maybeSetContext(c);
                    throw e;
                }
            }

            @Override
            public BooleanValue evaluateItem(XPathContext context) throws XPathException {
                return BooleanValue.get(effectiveBooleanValue(context));
            }
        };
    }

    @Override
    public String getCompilerName() {
        return "BooleanFnCompiler";
    }

    @Override
    public String getStreamerName() {
        return "BooleanFn";
    }


}
