////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.NumericValue;
import net.sf.saxon.value.SequenceType;

import java.util.function.Supplier;

/**
 * Negate Expression: implements the unary minus operator.
 * This expression is initially created as an ArithmeticExpression (or in backwards
 * compatibility mode, an ArithmeticExpression10) to take advantage of the type checking code.
 * So we don't need to worry about type checking or argument conversion.
 */

public class NegateExpression extends UnaryExpression {

    private boolean backwardsCompatible;

    /**
     * Create a NegateExpression
     *
     * @param base the expression that computes the value whose sign is to be reversed
     */

    public NegateExpression(Expression base) {
        super(base);
    }

    /**
     * Set whether the expression is to be evaluated in XPath 1.0 compatibility mode
     *
     * @param compatible true if XPath 1.0 compatibility mode is enabled
     */

    public void setBackwardsCompatible(boolean compatible) {
        backwardsCompatible = compatible;
    }

    /**
     * Ask whether the expression is to be evaluated in XPath 1.0 compatibility mode
     *
     * @return true if XPath 1.0 compatibility mode is enabled
     */

    public boolean isBackwardsCompatible() {
        return backwardsCompatible;
    }

    @Override
    protected OperandRole getOperandRole() {
        return OperandRole.SINGLE_ATOMIC;
    }

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getOperand().typeCheck(visitor, contextInfo);
        Supplier<RoleDiagnostic> role = () -> new RoleDiagnostic(RoleDiagnostic.UNARY_EXPR, "-", 0);
        Expression operand = visitor.getConfiguration().getTypeChecker(backwardsCompatible).staticTypeCheck(
                getBaseExpression(), SequenceType.OPTIONAL_NUMERIC,
                role, visitor);
        setBaseExpression(operand);
        if (operand instanceof Literal) {
            GroundedValue v = ((Literal) operand).getGroundedValue();
            if (v instanceof NumericValue) {
                return Literal.makeLiteral(((NumericValue) v).negate(), this);
            }
        }
        return this;
    }

    /**
     * Determine the data type of the expression, if this is known statically
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return getBaseExpression().getItemType().getPrimitiveItemType();
    }

    @Override
    protected int computeCardinality() {
        return getBaseExpression().getCardinality() & ~StaticProperty.ALLOWS_MANY;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided directly. The other methods will always be available
     * indirectly, using an implementation that relies on one of the other methods.
     *
     * @return the implementation method, for example {@link #ITERATE_METHOD} or {@link #EVALUATE_METHOD} or
     * {@link #PROCESS_METHOD}
     */
    @Override
    public int getImplementationMethod() {
        return EVALUATE_METHOD;
    }

    /**
     * Evaluate the expression.
     */

    @Override
    public NumericValue evaluateItem(XPathContext context) throws XPathException {
        return (NumericValue)makeElaborator().elaborateForItem().eval(context);
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings  variables that need to be re-bound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        NegateExpression exp = new NegateExpression(getBaseExpression().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
    }

    @Override
    protected String displayOperator(Configuration config) {
        return "-";
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "minus";
    }

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("minus", this);
        if (backwardsCompatible) {
            out.emitAttribute("vn", "1");
        }
        getBaseExpression().export(out);
        out.endElement();
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new NegateElaborator();
    }

    /**
     * Elaborator for a negate expression (that is, unary minus)
     */

    public static class NegateElaborator extends ItemElaborator {

        public ItemEvaluator elaborateForItem() {

            final NegateExpression exp = (NegateExpression)getExpression();
            final ItemEvaluator argEval = exp.getBaseExpression().makeElaborator().elaborateForItem();
            final boolean maybeEmpty = Cardinality.allowsZero(exp.getBaseExpression().getCardinality());
            final boolean backwardsCompatible = exp.isBackwardsCompatible();

            if (maybeEmpty) {
                if (backwardsCompatible) {
                    return context -> {
                        NumericValue v1 = (NumericValue) argEval.eval(context);
                        return v1 == null ? DoubleValue.NaN : v1.negate();
                    };
                } else {
                    return context -> {
                        NumericValue v1 = (NumericValue) argEval.eval(context);
                        return v1 == null ? null : v1.negate();
                    };
                }
            } else {
                return context -> ((NumericValue) argEval.eval(context)).negate();
            }
        }

    }
}

