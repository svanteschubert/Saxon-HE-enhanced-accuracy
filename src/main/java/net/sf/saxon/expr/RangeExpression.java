////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.*;


/**
 * A RangeExpression is an expression that represents an integer sequence as
 * a pair of end-points (for example "x to y").
 * If the end-points are equal, the sequence is of length one.
 * <p>From Saxon 7.8, the sequence must be ascending; if the end-point is less
 * than the start-point, an empty sequence is returned. This is to allow
 * expressions of the form "for $i in 1 to count($seq) return ...." </p>
 */

public class RangeExpression extends BinaryExpression {

    /**
     * Construct a RangeExpression
     * @param start expression that computes the start of the range
     * @param end   expression that computes the end of the range
     */

    public RangeExpression(Expression start, Expression end) {
        super(start, Token.TO, end);
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getLhs().typeCheck(visitor, contextInfo);
        getRhs().typeCheck(visitor, contextInfo);

        boolean backCompat = visitor.getStaticContext().isInBackwardsCompatibleMode();
        TypeChecker tc = visitor.getConfiguration().getTypeChecker(backCompat);
        RoleDiagnostic role0 = new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, "to", 0);
        setLhsExpression(tc.staticTypeCheck(
                getLhsExpression(), SequenceType.OPTIONAL_INTEGER, role0, visitor));

        RoleDiagnostic role1 = new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, "to", 1);
        setRhsExpression(tc.staticTypeCheck(
                getRhsExpression(), SequenceType.OPTIONAL_INTEGER, role1, visitor));

        return makeConstantRange();
    }

    /**
     * Perform optimisation of an expression and its subexpressions.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor         an expression visitor
     * @param contextInfo the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getLhs().optimize(visitor, contextInfo);
        getRhs().optimize(visitor, contextInfo);
        return makeConstantRange();

    }

    private Expression makeConstantRange() throws XPathException {
        if (getLhsExpression() instanceof Literal && getRhsExpression() instanceof Literal) {
            GroundedValue v0 = ((Literal) getLhsExpression()).getValue();
            GroundedValue v1 = ((Literal) getRhsExpression()).getValue();
            if (v0 instanceof Int64Value && v1 instanceof Int64Value) {
                long i0 = ((Int64Value) v0).longValue();
                long i1 = ((Int64Value) v1).longValue();
                Literal result;
                if (i0 > i1) {
                    result = Literal.makeEmptySequence();
                } else if (i0 == i1) {
                    result = Literal.makeLiteral(Int64Value.makeIntegerValue(i0), this);
                } else {
                    if (i1 - i0 > Integer.MAX_VALUE) {
                        throw new XPathException("Maximum length of sequence in Saxon is " + Integer.MAX_VALUE, "XPDY0130");
                    }
                    result = Literal.makeLiteral(new IntegerRange(i0, i1), this);
                }
                ExpressionTool.copyLocationInfo(this, result);
                return result;
            }
        }
        return this;
    }


    /**
     * Get the data type of the items returned
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return BuiltInAtomicType.INTEGER;
    }

    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     * @param contextItemType
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        return UType.DECIMAL;
    }

    /**
     * Determine the static cardinality
     */

    @Override
    public int computeCardinality() {
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
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
        IntegerValue[] start = getLhsExpression().getIntegerBounds();
        IntegerValue[] end = getLhsExpression().getIntegerBounds();
        if (start == null || end == null) {
            return null;
        } else {
            // range is from the smallest possible start value to the largest possible end value
            return new IntegerValue[]{start[0], end[1]};
        }
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        RangeExpression exp = new RangeExpression(getLhsExpression().copy(rebindings), getRhsExpression().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
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
        return ITERATE_METHOD;
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in export() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "range";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the output destination for the displayed expression tree
     */
    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("to", this);
        getLhsExpression().export(out);
        getRhsExpression().export(out);
        out.endElement();
    }

    /**
     * Return an iteration over the sequence
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        IntegerValue av1 = (IntegerValue) getLhsExpression().evaluateItem(context);
        IntegerValue av2 = (IntegerValue) getRhsExpression().evaluateItem(context);
        return RangeIterator.makeRangeIterator(av1, av2);
    }

}

