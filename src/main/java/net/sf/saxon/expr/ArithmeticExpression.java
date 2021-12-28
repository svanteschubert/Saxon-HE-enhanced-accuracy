////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.compat.ArithmeticExpression10;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

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

    public ArithmeticExpression(Expression p0, int operator, Expression p1) {
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
    public int computeSpecialProperties() {
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

        RoleDiagnostic role0 = new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, Token.tokens[operator], 0);
        //role0.setSourceLocator(this);
        setLhsExpression(tc.staticTypeCheck(getLhsExpression(), atomicType, role0, visitor));
        final ItemType itemType0 = getLhsExpression().getItemType();
        if (itemType0 instanceof ErrorType) {
            return Literal.makeEmptySequence();
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

        RoleDiagnostic role1 = new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, Token.tokens[operator], 1);
        //role1.setSourceLocator(this);
        setRhsExpression(tc.staticTypeCheck(getRhsExpression(), atomicType, role1, visitor));
        final ItemType itemType1 = getRhsExpression().getItemType();
        if (itemType1 instanceof ErrorType) {
            return Literal.makeEmptySequence();
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
            XPathException de = new XPathException("Arithmetic operators are not defined for external objects");
            de.setLocation(getLocation());
            de.setErrorCode("XPTY0004");
            throw de;
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



        if (operator == Token.NEGATE) {
            if (getRhsExpression() instanceof Literal && ((Literal) getRhsExpression()).getValue() instanceof NumericValue) {
                NumericValue nv = (NumericValue) ((Literal) getRhsExpression()).getValue();
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
            XPathException de = new XPathException("Arithmetic operator is not defined for arguments of types (" +
                    type0.getDescription() + ", " + type1.getDescription() + ")");
            de.setLocation(getLocation());
            de.setIsTypeError(true);
            de.setErrorCode("XPTY0004");
            throw de;
        }

        // If the calculator is going to promote arguments to xs:double, then promote any literal arguments now.
        // (Could generalize this, but this is the common case)
        if (calculator.code().matches("d.d")) {
            if (getLhsExpression() instanceof Literal && !type0.equals(BuiltInAtomicType.DOUBLE)) {
                GroundedValue value = ((Literal) getLhsExpression()).getValue();
                if (value instanceof NumericValue) {
                    setLhsExpression(Literal.makeLiteral(new DoubleValue(((NumericValue) value).getDoubleValue()), this));
                }
            }
            if (getRhsExpression() instanceof Literal && !type1.equals(BuiltInAtomicType.DOUBLE)) {
                GroundedValue value = ((Literal) getRhsExpression()).getValue();
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
     * For an expression that returns an integer or a sequence of integers, get
     * a lower and upper bound on the values of the integers that may be returned, from
     * static analysis. The default implementation returns null, meaning "unknown" or
     * "not applicable". Other implementations return an array of two IntegerValue objects,
     * representing the lower and upper bounds respectively. The values
     * UNBOUNDED_LOWER and UNBOUNDED_UPPER are used by convention to indicate that
     * the value may be arbitrarily large. The values MAX_STRING_LENGTH and MAX_SEQUENCE_LENGTH
     * are used to indicate values limited by the size of a string or the size of a sequence.
     *
     * @return the lower and upper bounds of integer values in the result, or null to indicate
     *         unknown or not applicable.
     */
    /*@Nullable*/
    @Override
    public IntegerValue[] getIntegerBounds() {
        IntegerValue[] bounds0 = getLhsExpression().getIntegerBounds();
        IntegerValue[] bounds1 = getRhsExpression().getIntegerBounds();
        if (bounds0 == null || bounds1 == null) {
            return null;
        } else {
            switch (operator) {
                case Token.PLUS:
                    return new IntegerValue[]{bounds0[0].plus(bounds1[0]), bounds0[1].plus(bounds1[1])};
                case Token.MINUS:
                    return new IntegerValue[]{bounds0[0].minus(bounds1[1]), bounds0[1].minus(bounds1[0])};
                case Token.MULT:
                    if (getRhsExpression() instanceof Literal) {
                        IntegerValue val1 = bounds1[0];
                        if (val1.signum() > 0) {
                            return new IntegerValue[]{bounds0[0].times(val1), bounds0[1].times(val1)};
                        } else {
                            return null;
                        }
                    } else if (getLhsExpression() instanceof Literal) {
                        IntegerValue val0 = bounds1[0];
                        if (val0.signum() > 0) {
                            return new IntegerValue[]{bounds1[0].times(val0), bounds1[1].times(val0)};
                        } else {
                            return null;
                        }
                    }
                case Token.DIV:
                case Token.IDIV:
                    if (getRhsExpression() instanceof Literal) {
                        IntegerValue val1 = bounds1[0];
                        if (val1.signum() > 0) {
                            try {
                                return new IntegerValue[]{bounds0[0].idiv(val1), bounds0[1].idiv(val1)};
                            } catch (XPathException e) {
                                return null;
                            }
                        }
                    }
                    return null;
                default:
                    return null;
            }
        }
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

    public static int mapOpCode(int op) {
        switch (op) {
            case Token.PLUS:
                return Calculator.PLUS;
            case Token.MINUS:
            case Token.NEGATE:
                return Calculator.MINUS;
            case Token.MULT:
                return Calculator.TIMES;
            case Token.DIV:
                return Calculator.DIV;
            case Token.IDIV:
                return Calculator.IDIV;
            case Token.MOD:
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
                if ((operator == Token.PLUS || operator == Token.MINUS) &&
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
        } else if (operator == Token.NEGATE) {
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
        AtomicValue v0 = (AtomicValue) getLhsExpression().evaluateItem(context);
        if (v0 == null) {
            return null;
        }

        AtomicValue v1 = (AtomicValue) getRhsExpression().evaluateItem(context);
        if (v1 == null) {
            return null;
        }

        try {
            return calculator.compute(v0, v1, context);
        } catch (XPathException e) {
            e.maybeSetLocation(getLocation());
            e.maybeSetFailingExpression(this);
            e.maybeSetContext(context);
            throw e;
        }
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



}

