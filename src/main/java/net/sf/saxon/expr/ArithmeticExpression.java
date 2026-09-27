////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.compat.ArithmeticExpression10;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import java.util.function.Supplier;

/**
 * Arithmetic Expression: an expression using one of the operators
 * plus, minus, multiply, div, idiv, mod. Note that this code does not handle backwards
 * compatibility mode: see {@link ArithmeticExpression10}
 */

public class ArithmeticExpression extends BinaryExpression {

    protected Calculator calculator;
    private PlainType itemType;

    /**
     * Create an arithmetic expression
     *
     * @param p0       the first operand
     * @param operator the operator, for example {@link Token#PLUS}
     * @param p1       the second operand
     */

    public ArithmeticExpression(Expression p0, OperatorSymbol operator, Expression p1) {
        super(p0, operator, p1);
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
        return "arithmetic";
    }

    /**
     * Determine the special properties of this expression
     *
     * @return {@link StaticProperty#NO_NODES_NEWLY_CREATED}. This is overridden
     * for some subclasses.
     */
    @Override
    protected int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        return p | StaticProperty.NOT_UNTYPED_ATOMIC;
    }

    /**
     * Set the calculator allocated to evaluate this expression
     * @param calculator the calculator to be used
     */

    public void setCalculator(Calculator calculator) {
        this.calculator = calculator;
    }

    /**
     * Get the calculator allocated to evaluate this expression
     *
     * @return the calculator, a helper object that does the actual calculation
     */

    public Calculator getCalculator() {
        return calculator;
    }

    /**
     * Type-check the expression statically. We try to work out which particular
     * arithmetic function to use if the types of operands are known an compile time.
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        resetLocalStaticProperties();
        getLhs().typeCheck(visitor, contextInfo);
        getRhs().typeCheck(visitor, contextInfo);

        Configuration config = visitor.getConfiguration();
        final TypeHierarchy th = config.getTypeHierarchy();
        final TypeChecker tc = config.getTypeChecker(false);

        Expression oldOp0 = getLhsExpression();
        Expression oldOp1 = getRhsExpression();

        SequenceType atomicType = SequenceType.OPTIONAL_ATOMIC;

        Supplier<RoleDiagnostic> role0 = () -> new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, operator.toString(), 0);
        //role0.setSourceLocator(this);
        setLhsExpression(tc.staticTypeCheck(getLhsExpression(), atomicType, role0, visitor));
        final ItemType itemType0 = getLhsExpression().getItemType();
        if (itemType0 instanceof ErrorType) {
            return getLhsExpression();
        }
        AtomicType type0 = (AtomicType) itemType0.getPrimitiveItemType();
        if (type0.getFingerprint() == StandardNames.XS_UNTYPED_ATOMIC) {
            setLhsExpression(UntypedSequenceConverter.makeUntypedSequenceConverter(config, getLhsExpression(), BuiltInAtomicType.DOUBLE));
            type0 = BuiltInAtomicType.DOUBLE;
        } else if (/*!(operand0 instanceof UntypedAtomicConverter)*/
                (getLhsExpression().getSpecialProperties() & StaticProperty.NOT_UNTYPED_ATOMIC) == 0 &&
                        th.relationship(type0, BuiltInAtomicType.UNTYPED_ATOMIC) != Affinity.DISJOINT) {
            setLhsExpression(UntypedSequenceConverter.makeUntypedSequenceConverter(config, getLhsExpression(), BuiltInAtomicType.DOUBLE));
            type0 = (AtomicType) getLhsExpression().getItemType().getPrimitiveItemType();
        }

        // System.err.println("First operand"); operand0.display(10);

        Supplier<RoleDiagnostic> role1 = () -> new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, operator.toString(), 1);
        setRhsExpression(tc.staticTypeCheck(getRhsExpression(), atomicType, role1, visitor));
        final ItemType itemType1 = getRhsExpression().getItemType();
        if (itemType1 instanceof ErrorType) {
            return getRhsExpression();
        }
        AtomicType type1 = (AtomicType) itemType1.getPrimitiveItemType();
        if (type1.getFingerprint() == StandardNames.XS_UNTYPED_ATOMIC) {
            setRhsExpression(UntypedSequenceConverter.makeUntypedSequenceConverter(config, getRhsExpression(), BuiltInAtomicType.DOUBLE));
            type1 = BuiltInAtomicType.DOUBLE;
        } else if (/*!(operand1 instanceof UntypedAtomicConverter) &&*/
                (getRhsExpression().getSpecialProperties() & StaticProperty.NOT_UNTYPED_ATOMIC) == 0 &&
                        th.relationship(type1, BuiltInAtomicType.UNTYPED_ATOMIC) != Affinity.DISJOINT) {
            setRhsExpression(UntypedSequenceConverter.makeUntypedSequenceConverter(config, getRhsExpression(), BuiltInAtomicType.DOUBLE));
            type1 = (AtomicType) getRhsExpression().getItemType().getPrimitiveItemType();
        }

        if (itemType0.getUType().union(itemType1.getUType()).overlaps(UType.EXTENSION)) {
            throw new XPathException("Arithmetic operators are not defined for external objects")
                    .withLocation(getLocation()).withErrorCode("XPTY0004");
        }

        if (getLhsExpression() != oldOp0) {
            adoptChildExpression(getLhsExpression());
        }

        if (getRhsExpression() != oldOp1) {
            adoptChildExpression(getRhsExpression());
        }

        if (Literal.isEmptySequence(getLhsExpression()) ||
                Literal.isEmptySequence(getRhsExpression())) {
            return Literal.makeEmptySequence();
        }



        if (operator == OperatorSymbol.NEGATE) {
            if (getRhsExpression() instanceof Literal && ((Literal) getRhsExpression()).getGroundedValue() instanceof NumericValue) {
                NumericValue nv = (NumericValue) ((Literal) getRhsExpression()).getGroundedValue();
                return Literal.makeLiteral(nv.negate(), this);
            } else {
                NegateExpression ne = new NegateExpression(getRhsExpression());
                ne.setBackwardsCompatible(false);
                return ne.typeCheck(visitor, contextInfo);
            }
        }

        // Get a calculator to implement the arithmetic operation. If the types are not yet specifically known,
        // we allow this to return an "ANY" calculator which defers the decision. However, we only allow this if
        // at least one of the operand types is AnyAtomicType or (otherwise unspecified) numeric.

        boolean mustResolve = !(type0.equals(BuiltInAtomicType.ANY_ATOMIC) || type1.equals(BuiltInAtomicType.ANY_ATOMIC)
                || type0.equals(NumericType.getInstance()) || type1.equals(NumericType.getInstance()));

        calculator = Calculator.getCalculator(
                type0.getFingerprint(), type1.getFingerprint(), mapOpCode(operator), mustResolve);

        if (calculator == null) {
            throw new XPathException("Arithmetic operator is not defined for arguments of types (" +
                    type0.getDescription() + ", " + type1.getDescription() + ")")
                    .withLocation(getLocation()).asTypeError().withErrorCode("XPTY0004");
        }

        // If the calculator is going to promote arguments to xs:double, then promote any literal arguments now.
        // (Could generalize this, but this is the common case)
        if (calculator instanceof Calculator.DoubleOpDouble) {
            if (getLhsExpression() instanceof Literal && !type0.equals(BuiltInAtomicType.DOUBLE)) {
                GroundedValue value = ((Literal) getLhsExpression()).getGroundedValue();
                if (value instanceof NumericValue) {
                    setLhsExpression(Literal.makeLiteral(new DoubleValue(((NumericValue) value).getDoubleValue()), this));
                }
            }
            if (getRhsExpression() instanceof Literal && !type1.equals(BuiltInAtomicType.DOUBLE)) {
                GroundedValue value = ((Literal) getRhsExpression()).getGroundedValue();
                if (value instanceof NumericValue) {
                    setRhsExpression(Literal.makeLiteral(new DoubleValue(((NumericValue) value).getDoubleValue()), this));
                }
            }
        }

        try {
            if ((getLhsExpression() instanceof Literal) && (getRhsExpression() instanceof Literal)) {
                return Literal.makeLiteral(evaluateItem(visitor.getStaticContext().makeEarlyEvaluationContext()).materialize(), this);
            }
        } catch (XPathException err) {
            // if early evaluation fails, suppress the error: the value might
            // not be needed at run-time, or it might be due to context such as the implicit timezone
            // not being available yet
        }
        return this;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings variables that need to be rebound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        ArithmeticExpression ae = new ArithmeticExpression(getLhsExpression().copy(rebindings), operator, getRhsExpression().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, ae);
        ae.calculator = calculator;
        return ae;
    }

    /**
     * Static method to apply arithmetic to two values
     *
     * @param value0   the first value
     * @param operator the operator as denoted in the Calculator class, for example {@link Calculator#PLUS}
     * @param value1   the second value
     * @param context  the XPath dynamic evaluation context
     * @return the result of the arithmetic operation
     * @throws XPathException if a dynamic error occurs during evaluation
     */

    public static AtomicValue compute(AtomicValue value0, int operator, AtomicValue value1, XPathContext context)
            throws XPathException {
        int p0 = value0.getPrimitiveType().getFingerprint();
        int p1 = value1.getPrimitiveType().getFingerprint();
        Calculator calculator = Calculator.getCalculator(p0, p1, operator, false);
        return calculator.compute(value0, value1, context);
    }

    /**
     * Map operator codes from those in the Token class to those in the Calculator class
     *
     * @param op an operator denoted by a constant in the {@link Token} class, for example {@link Token#PLUS}
     * @return an operator denoted by a constant defined in the {@link Calculator} class, for example
     *         {@link Calculator#PLUS}
     */

    public static int mapOpCode(OperatorSymbol op) {
        switch (op) {
            case PLUS:
                return Calculator.PLUS;
            case MINUS:
            case NEGATE:
                return Calculator.MINUS;
            case TIMES:
                return Calculator.TIMES;
            case DIV:
                return Calculator.DIV;
            case IDIV:
                return Calculator.IDIV;
            case MOD:
                return Calculator.MOD;
            default:
                throw new IllegalArgumentException();
        }

    }

    /**
     * Determine the data type of the expression, insofar as this is known statically
     *
     * @return the atomic type of the result of this arithmetic expression
     */

    /*@NotNull*/
    @Override
    public PlainType getItemType() {
        if (itemType != null) {
            return itemType;
        }
        if (calculator == null) {
            return BuiltInAtomicType.ANY_ATOMIC;  // type is not known statically
        } else {
            ItemType t1 = getLhsExpression().getItemType();
            if (!(t1 instanceof AtomicType)) {
                t1 = t1.getAtomizedItemType();
            }
            ItemType t2 = getRhsExpression().getItemType();
            if (!(t2 instanceof AtomicType)) {
                t2 = t2.getAtomizedItemType();
            }
            PlainType resultType = calculator.getResultType((AtomicType) t1.getPrimitiveItemType(),
                    (AtomicType) t2.getPrimitiveItemType());

            if (resultType.equals(BuiltInAtomicType.ANY_ATOMIC)) {
                // there are a few special cases where we can do better. For example, given X+1, where the type of X
                // is unknown, we can still infer that the result is numeric. (Not so for X*2, however, where it could
                // be a duration)
                TypeHierarchy th = getConfiguration().getTypeHierarchy();
                if ((operator == OperatorSymbol.PLUS || operator == OperatorSymbol.MINUS) &&
                        (NumericType.isNumericType(t2) || NumericType.isNumericType(t1))) {
                    resultType = NumericType.getInstance();
                }
            }
            return itemType = resultType;
        }
    }

    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     * @param contextItemType static information about the context item
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        // The rationale for this is in the XSLT 3.0 spec
        if (getParentExpression() instanceof FilterExpression && ((FilterExpression)getParentExpression()).getRhsExpression() == this) {
            return UType.NUMERIC;
        } else if (operator == OperatorSymbol.NEGATE) {
            return UType.NUMERIC;
        } else {
            return UType.ANY_ATOMIC;
        }
    }

    /**
     * Reset the static properties of the expression to -1, so that they have to be recomputed
     * next time they are used.
     */
    @Override
    public void resetLocalStaticProperties() {
        super.resetLocalStaticProperties();
        itemType = null;
    }

    /**
     * Evaluate the expression.
     */

    @Override
    public AtomicValue evaluateItem(XPathContext context) throws XPathException {
        return (AtomicValue)makeElaborator().elaborateForItem().eval(context);
    }

    @Override
    protected String tag() {
        return "arith";
    }

    @Override
    protected void explainExtraAttributes(ExpressionPresenter out) {
        if (calculator != null) { // May be null during optimizer tracing
            out.emitAttribute("calc", calculator.code());
        }
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new ArithmeticElaborator();
    }


    /**
     * Elaborator for an ArithmeticExpression (for example P + Q)
     */

    public static class ArithmeticElaborator extends ItemElaborator {

        public ItemEvaluator elaborateForItem() {
            final ArithmeticExpression exp = (ArithmeticExpression)getExpression();
            final ItemEvaluator arg0Eval = exp.getLhsExpression().makeElaborator().elaborateForItem();
            final ItemEvaluator arg1Eval = exp.getRhsExpression().makeElaborator().elaborateForItem();
            // Allow the null checks to be skipped if not needed
            final boolean nullable0 = Cardinality.allowsZero(exp.getLhsExpression().getCardinality());
            final boolean nullable1 = Cardinality.allowsZero(exp.getRhsExpression().getCardinality());
            final Calculator calc = exp.getCalculator();
            if (nullable0 || nullable1) {
                return context -> {
                    AtomicValue v0 = (AtomicValue) arg0Eval.eval(context);
                    if (v0 == null) {
                        return null;
                    }
                    AtomicValue v1 = (AtomicValue) arg1Eval.eval(context);
                    if (v1 == null) {
                        return null;
                    }
                    try {
                        return calc.compute(v0, v1, context);
                    } catch (XPathException e) {
                        throw e.maybeWithLocation(exp.getLocation()).maybeWithContext(context);
                    }
                };
            } else if (calc instanceof Calculator.DoublePlusDouble && exp.getRhsExpression() instanceof Literal) {
                // Fast path for common case such as $x + 1
                double addend = ((NumericValue)((Literal)exp.getRhsExpression()).getGroundedValue()).getDoubleValue();
                return context -> new DoubleValue(((NumericValue)arg0Eval.eval(context)).getDoubleValue() + addend);
            } else {
                return context -> {
                    AtomicValue v0 = (AtomicValue) arg0Eval.eval(context);
                    AtomicValue v1 = (AtomicValue) arg1Eval.eval(context);
                    try {
                        return calc.compute(v0, v1, context);
                    } catch (XPathException e) {
                        throw e.maybeWithLocation(exp.getLocation()).maybeWithContext(context);
                    }
                };
            }
        }

    }
}

