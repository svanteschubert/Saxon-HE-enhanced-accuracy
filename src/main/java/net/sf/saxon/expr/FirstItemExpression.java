////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;


/**
 * A FirstItemExpression returns the first item in the sequence returned by a given
 * base expression
 */

public final class FirstItemExpression extends SingleItemFilter {

    /**
     * Private Constructor
     *
     * @param base A sequence expression denoting sequence whose first item is to be returned
     */

    private FirstItemExpression(Expression base) {
        super(base);
    }

    /**
     * Static factory method
     *
     * @param base A sequence expression denoting sequence whose first item is to be returned
     * @return the FirstItemExpression, or an equivalent
     */

    public static Expression makeFirstItemExpression(Expression base) {
        if (base instanceof FirstItemExpression) {
            return base;
        } else {
            return new FirstItemExpression(base);
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
        Expression e2 = new FirstItemExpression(getBaseExpression().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, e2);
        return e2;
    }

    /**
     * Convert this expression to an equivalent XSLT pattern
     *
     * @param config the Saxon configuration
     * @return the equivalent pattern
     * @throws net.sf.saxon.trans.XPathException
     *          if conversion is not possible
     */
    @Override
    public Pattern toPattern(Configuration config) throws XPathException {
        Pattern basePattern = getBaseExpression().toPattern(config);
        ItemType type = basePattern.getItemType();
        if (type instanceof NodeTest) {
            Expression baseExpr = getBaseExpression();
            if (baseExpr instanceof AxisExpression &&
                ((AxisExpression)baseExpr).getAxis() == AxisInfo.CHILD && basePattern instanceof NodeTestPattern) {
                    return new SimplePositionalPattern((NodeTest) type, 1);
            } else {
                return new GeneralNodePattern(this, (NodeTest) type);
            }
        } else {
            // For a non-node pattern, the predicate [1] is always true
            return basePattern;
        }
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
     * Evaluate the expression
     */

    @Override
    public Item evaluateItem(XPathContext context) throws XPathException {
        SequenceIterator iter = getBaseExpression().iterate(context);
        Item result = iter.next();
        iter.close();
        return result;
    }

    @Override
    public String getExpressionName() {
        return "first";
    }

    @Override
    public String toShortString() {
        return getBaseExpression().toShortString() + "[1]";
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "FirstItemExpression";
    }

}

