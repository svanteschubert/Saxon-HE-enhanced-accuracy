////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ReversibleIterator;
import net.sf.saxon.value.SequenceExtent;

/**
 * Implement XPath function fn:reverse()
 */

public class Reverse extends SystemFunction {

    @Override
    public int getSpecialProperties(Expression[] arguments) {
        int baseProps = arguments[0].getSpecialProperties();
        if ((baseProps & StaticProperty.REVERSE_DOCUMENT_ORDER) != 0) {
            return (baseProps &
                    ~StaticProperty.REVERSE_DOCUMENT_ORDER) |
                    StaticProperty.ORDERED_NODESET;
        } else if ((baseProps & StaticProperty.ORDERED_NODESET) != 0) {
            return (baseProps &
                    ~StaticProperty.ORDERED_NODESET) |
                    StaticProperty.REVERSE_DOCUMENT_ORDER;
        } else {
            return baseProps;
        }
    }

//    /**
//     * Perform optimisation of an expression and its subexpressions.
//     * <p>This method is called after all references to functions and variables have been resolved
//     * to the declaration of the function or variable, and after all type checking has been done.</p>
//     *
//     * @param visitor         an expression visitor
//     * @param contextItemType the static type of "." at the point where this expression is invoked.
//     *                        The parameter is set to null if it is known statically that the context item will be undefined.
//     *                        If the type of the context item is not known statically, the argument is set to
//     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
//     * @return the original expression, rewritten if appropriate to optimize execution
//     * @throws net.sf.saxon.trans.XPathException
//     *          if an error is discovered during this phase
//     *          (typically a type error)
//     */
//    @Override
//    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
//        Expression e2 = super.optimize(visitor, contextItemType);
//        if (e2 != this) {
//            ExpressionTool.copyLocationInfo(this, e2);
//            return e2;
//        }
//        if (!Cardinality.allowsMany(getArg(0).getCardinality())) {
//            return getArg(0);
//        }
//        return this;
//    }


    public static <T extends Item> SequenceIterator getReverseIterator(SequenceIterator forwards) throws XPathException {
        if (forwards instanceof ReversibleIterator) {
            return ((ReversibleIterator) forwards).getReverseIterator();
        } else {
            SequenceExtent extent = new SequenceExtent(forwards);
            return extent.reverseIterate();
        }
    }


    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return SequenceTool.toLazySequence(getReverseIterator(arguments[0].iterate()));
    }

    /**
     * Allow the function to create an optimized call based on the values of the actual arguments
     *
     * @param visitor     the expression visitor
     * @param contextInfo information about the context item
     * @param arguments   the supplied arguments to the function call. Note: modifying the contents
     *                    of this array should not be attempted, it is likely to have no effect.
     * @return either a function call on this function, or an expression that delivers
     * the same result, or null indicating that no optimization has taken place
     * @throws XPathException if an error is detected
     */
    @Override
    public Expression makeOptimizedFunctionCall(
            ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, Expression... arguments)
            throws XPathException {
        // When reverse() has only a zero-or-one argument, there is no need to reverse
        // This often occurs in reverse-axis steps
        if (arguments[0].getCardinality() == StaticProperty.ALLOWS_ZERO_OR_ONE) {
            return arguments[0];
        }
        return super.makeOptimizedFunctionCall(visitor, contextInfo, arguments);

    }

    @Override
    public String getStreamerName() {
        return "Reverse";
    }
}

