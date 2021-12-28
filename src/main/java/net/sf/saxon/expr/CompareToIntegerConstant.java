////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.DoubleSortComparer;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.NumericValue;

/**
 * This class implements a comparison of a numeric value to an integer constant using one of the operators
 * eq, ne, lt, gt, le, ge. The semantics are identical to ValueComparison, but this is a fast path for an
 * important common case.
 */

public class CompareToIntegerConstant extends CompareToConstant {

    private long comparand;

    /**
     * Create the expression
     *
     * @param operand   the operand to be compared with an integer constant. This must
     *                  have a static type of NUMERIC, and a cardinality of EXACTLY ONE
     * @param operator  the comparison operator,
     *                  one of {@link Token#FEQ}, {@link Token#FNE}, {@link Token#FGE},
     *                  {@link Token#FGT}, {@link Token#FLE}, {@link Token#FLT}
     * @param comparand the integer constant
     */

    public CompareToIntegerConstant(Expression operand, int operator, long comparand) {
        super(operand);
        this.operator = operator;
        this.comparand = comparand;
    }

    /**
     * Get the integer value on the rhs of the expression
     *
     * @return the integer constant
     */

    public long getComparand() {
        return comparand;
    }

    /**
     * Get the effective right-hand-side expression (so that general logic for comparison expressions
     * can be used)
     * @return a Literal representing the RHS expression
     */

    @Override
    public Expression getRhsExpression() {
        return new Literal(new Int64Value(comparand));
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
        CompareToIntegerConstant c2 = new CompareToIntegerConstant(getLhsExpression().copy(rebindings), operator, comparand);
        ExpressionTool.copyLocationInfo(this, c2);
        return c2;
    }

     /**
     * Is this expression the same as another expression?
     *
     * @param other the expression to be compared with this one
     * @return true if the two expressions are statically equivalent
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof CompareToIntegerConstant && ((CompareToIntegerConstant)other).getLhsExpression().isEqual(getLhsExpression())
                && ((CompareToIntegerConstant)other).comparand == comparand
                && ((CompareToIntegerConstant)other).operator == operator;
    }

    /**
     * Hashcode supporting equals()
     */

    @Override
    public int computeHashCode() {
        int h = 0x836b12a0;
        return h + getLhsExpression().hashCode() ^ (int)comparand;
    }


    /**
     * Get the effective boolean value of the expression. This returns false if the value
     * is the empty sequence, a zero-length string, a number equal to zero, or the boolean
     * false. Otherwise it returns true.
     *
     * @param context The context in which the expression is to be evaluated
     * @return the effective boolean value
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */

    @Override
    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        NumericValue n = (NumericValue) getLhsExpression().evaluateItem(context);
        if (n.isNaN()) {
            return operator == Token.FNE;
        }
        int c = n.compareTo(comparand);
        return interpretComparisonResult(c);
    }

    @Override
    public int computeCardinality() {
        return StaticProperty.EXACTLY_ONE;
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
        return "compareToInt";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("compareToInt", this);
        destination.emitAttribute("op", Token.tokens[operator]);
        destination.emitAttribute("val", comparand + "");
        getLhsExpression().export(destination);
        destination.endElement();
    }

    /**
     * <p>The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form.</p>
     * <p>For subclasses of Expression that represent XPath expressions, the result should always be a string that
     * parses as an XPath 3.0 expression</p>
     *
     * @return a representation of the expression as a string
     */
    @Override
    public String toString() {
        return ExpressionTool.parenthesize(getLhsExpression()) + " " +
                Token.tokens[operator] + " " + comparand;
    }

    /**
     * Produce a short string identifying the expression for use in error messages
     *
     * @return a short string, sufficient to identify the expression
     */
    @Override
    public String toShortString() {
        return getLhsExpression().toShortString() + " " + Token.tokens[operator] + " " + comparand;
    }

    /**
     * Get the AtomicComparer used to compare atomic values. This encapsulates any collation that is used
     */

    @Override
    public AtomicComparer getAtomicComparer() {
        return DoubleSortComparer.getInstance();
        // Note: this treats NaN=NaN as true, but it doesn't matter, because the rhs will never be NaN.
    }

}

