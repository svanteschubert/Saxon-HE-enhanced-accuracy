////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.PullElaborator;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.IntegerRange;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

import java.math.BigInteger;
import java.util.function.Supplier;


/**
 * A RangeExpression is an expression that represents an integer sequence as
 * a pair of end-points (for example "x to y"). This implementation also
 * supports the experimental syntax "x by y to z", with y defaulting to 1.
 */

public class RangeExpression extends Expression {

    private final Operand start;
    private final Operand end;

    /**
     * Construct a RangeExpression with increment of 1
     * @param start expression that computes the start of the range
     * @param end   expression that computes the end of the range
     */

    public RangeExpression(Expression start, Expression end) {
        this.start = new Operand(this, start, OperandRole.SINGLE_ATOMIC);
        this.end = new Operand(this, end, OperandRole.SINGLE_ATOMIC);
        adoptChildExpression(start);
        adoptChildExpression(end);
    }

    public Expression getStartExpression() {
        return start.getChildExpression();
    }

    public Expression getEndExpression() {
        return end.getChildExpression();
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        start.typeCheck(visitor, contextInfo);
        end.typeCheck(visitor, contextInfo);

        boolean backCompat = visitor.getStaticContext().isInBackwardsCompatibleMode();
        TypeChecker tc = visitor.getConfiguration().getTypeChecker(backCompat);
        Supplier<RoleDiagnostic> role0 = () -> new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, "to", 0);
        start.setChildExpression(tc.staticTypeCheck(
                getStartExpression(), SequenceType.OPTIONAL_INTEGER, role0, visitor));

        Supplier<RoleDiagnostic> role2 = () -> new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, "to", 1);
        end.setChildExpression(tc.staticTypeCheck(
                getEndExpression(), SequenceType.OPTIONAL_INTEGER, role2, visitor));

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
        start.optimize(visitor, contextInfo);
        end.optimize(visitor, contextInfo);
        return makeConstantRange();

    }

    /**
     * Get the immediate sub-expressions of this expression, with information about the relationship
     * of each expression to its parent expression. Default implementation
     * works off the results of iterateSubExpressions()
     *
     * <p>If the expression is a Callable, then it is required that the order of the operands
     * returned by this function is the same as the order of arguments supplied to the corresponding
     * call() method.</p>
     *
     * @return an iterator containing the sub-expressions of this expression
     */
    @Override
    public Iterable<Operand> operands() {
        return operandList(start, end);
    }

    private Expression makeConstantRange() throws XPathException {
        if (getStartExpression() instanceof Literal
                && getEndExpression() instanceof Literal) {
            Expression result;
            GroundedValue v0 = ((Literal) getStartExpression()).getGroundedValue();
            GroundedValue v2 = ((Literal) getEndExpression()).getGroundedValue();
            if (v0.getLength()==0 || v2.getLength()==0) {
                result = Literal.makeEmptySequence();
            } else if (v0 instanceof Int64Value && v2 instanceof Int64Value) {
                long i0 = ((Int64Value) v0).longValue();
                long i2 = ((Int64Value) v2).longValue();
                if (i0 > i2) {
                    result = Literal.makeEmptySequence();
                } else {
                    if (Math.abs((i2 - i0)) > Integer.MAX_VALUE) {
                        throw new XPathException("Maximum length of sequence in Saxon is " + Integer.MAX_VALUE, "XPDY0130");
                    }
                    result = Literal.makeLiteral(new IntegerRange(i0, 1, i2), this);
                }
            } else {
                BigInteger i0 = ((IntegerValue) v0).asBigInteger();
                BigInteger i2 = ((IntegerValue) v2).asBigInteger();
                if (i0.equals(BigInteger.ZERO) || i0.compareTo(i2) > 0) {
                    result = Literal.makeEmptySequence();
                } else if (i0.equals(i2)) {
                    result = Literal.makeLiteral(Int64Value.makeIntegerValue(i0), this);
                } else {
//                    if (Math.abs((i2 - i0) / i1) > Integer.MAX_VALUE) {
//                        throw new XPathException("Maximum length of sequence in Saxon is " + Integer.MAX_VALUE, "XPDY0130");
//                    }
                    return this;
                }
            }
            ExpressionTool.copyLocationInfo(this, result);
            return result;
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
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        return UType.DECIMAL;
    }

    /**
     * Determine the static cardinality
     */

    @Override
    protected int computeCardinality() {
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
        IntegerValue[] start = getStartExpression().getIntegerBounds();
        IntegerValue[] end = getEndExpression().getIntegerBounds();
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
     * @param rebindings variables that need to be re-bound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        RangeExpression exp = new RangeExpression(
                getStartExpression().copy(rebindings),
                getEndExpression().copy(rebindings));
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
     * Determine the special properties of this expression
     *
     * @return {@link StaticProperty#NO_NODES_NEWLY_CREATED}.
     */

    @Override
    protected int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        return p | StaticProperty.NO_NODES_NEWLY_CREATED;
    }


    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        if (other instanceof RangeExpression && hasCompatibleStaticContext((Expression) other)) {
            RangeExpression b = (RangeExpression) other;
            Expression start1 = getStartExpression();
            Expression end1 = getEndExpression();
            Expression start2 = b.getStartExpression();
            Expression end2 = b.getEndExpression();
            return start1.equals(start2) && end1.equals(end2);
        }
        return false;
    }

    /**
     * Compute a hash code, which will then be cached for later use
     *
     * @return a computed hash code
     */
    @Override
    protected int computeHashCode() {
        return getStartExpression().hashCode() ^ (getEndExpression().hashCode()<<7);
    }

    /**
     * <p>The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form.</p>
     * <p>For subclasses of Expression that represent XPath expressions, the result should always be a string that
     * parses as an XPath 3.0 expression. The expression produced should be equivalent to the original making certain
     * assumptions about the static context. In general the expansion will make no assumptions about namespace bindings,
     * except that (a) the prefix "xs" is used to refer to the XML Schema namespace, and (b) the default function namespace
     * is assumed to be the "fn" namespace.</p>
     * <p>In the case of XSLT instructions and XQuery expressions, the toString() method gives an abstracted view of the syntax
     * that is not designed in general to be parseable.</p>
     *
     * @return a representation of the expression as a string
     */
    @Override
    public String toString() {
        return getStartExpression().toString() + " to " + getEndExpression().toString();
    }

    @Override
    public String toShortString() {
        return getStartExpression().toShortString() + " to " + getEndExpression().toShortString();
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the output destination for the displayed expression tree
     */
    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        // TODO: export "by" expression
        out.startElement("to", this);
        getStartExpression().export(out);
        getEndExpression().export(out);
        out.endElement();
    }

    /**
     * Return an iteration over the sequence
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        IntegerValue av1 = (IntegerValue) getStartExpression().evaluateItem(context);
        IntegerValue av3 = (IntegerValue) getEndExpression().evaluateItem(context);
        return AscendingRangeIterator.makeRangeIterator(av1, Int64Value.PLUS_ONE, av3);
    }

    @Override
    public Elaborator getElaborator() {
        return new RangeElaborator();
    }

    public static class RangeElaborator extends PullElaborator {
        @Override
        public PullEvaluator elaborateForPull() {
            RangeExpression expr = (RangeExpression) getExpression();
            ItemEvaluator iv1 = expr.getStartExpression().makeElaborator().elaborateForItem();
            ItemEvaluator iv3 = expr.getEndExpression().makeElaborator().elaborateForItem();
            return (context) -> {
                IntegerValue av1 = (IntegerValue) iv1.eval(context);
                IntegerValue av3 = (IntegerValue) iv3.eval(context);
                return AscendingRangeIterator.makeRangeIterator(av1, Int64Value.PLUS_ONE, av3);
            };
        }

    }

}

