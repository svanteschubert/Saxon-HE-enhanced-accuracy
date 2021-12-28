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

/**
 * The class GeneralComparison20 specializes GeneralComparison for the case where
 * the comparison is done with 2.0 semantics (i.e. with backwards compatibility off).
 * It differs from the superclass in that it will never turn the expression into
 * a GeneralComparison10, which could lead to non-terminating optimizations
 */
public class GeneralComparison20 extends GeneralComparison {

    /**
     * Create a relational expression identifying the two operands and the operator
     *
     * @param p0 the left-hand operand
     * @param op the operator, as a token returned by the Tokenizer (e.g. Token.LT)
     * @param p1 the right-hand operand
     */
    public GeneralComparison20(Expression p0, int op, Expression p1) {
        super(p0, op, p1);
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
        GeneralComparison20 gc = new GeneralComparison20(getLhsExpression().copy(rebindings), operator, getRhsExpression().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, gc);
        gc.setRetainedStaticContext(getRetainedStaticContext());
        gc.comparer = comparer;
        gc.singletonOperator = singletonOperator;
        gc.needsRuntimeCheck = needsRuntimeCheck;
        gc.comparisonCardinality = comparisonCardinality;
        return gc;
    }

    @Override
    protected GeneralComparison getInverseComparison() {
        GeneralComparison20 gc = new GeneralComparison20(getRhsExpression(), Token.inverse(operator), getLhsExpression());
        gc.setRetainedStaticContext(getRetainedStaticContext());
        return gc;
    }


}

