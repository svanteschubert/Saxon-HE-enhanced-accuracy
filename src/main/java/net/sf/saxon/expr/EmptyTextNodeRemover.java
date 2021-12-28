////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;


/**
 * This class performs part of the processing in "constructing simple content":
 * it takes an input sequence and eliminates empty text nodes
 * into one.
 *
 * @since 9.3
 */
public class EmptyTextNodeRemover extends UnaryExpression
        implements ItemMappingFunction {

    public EmptyTextNodeRemover(Expression p0) {
        super(p0);
    }

    /**
     * Determine the data type of the expression, if possible. The default
     * implementation for unary expressions returns the item type of the operand
     *
     * @return the item type of the items in the result sequence, insofar as this
     *         is known statically.
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return getBaseExpression().getItemType();
    }

    @Override
    public int computeCardinality() {
        return getBaseExpression().getCardinality() | StaticProperty.ALLOWS_ZERO;
    }

    @Override
    protected OperandRole getOperandRole() {
        return OperandRole.SAME_FOCUS_ACTION;
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
        EmptyTextNodeRemover e2 = new EmptyTextNodeRemover(getBaseExpression().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, e2);
        return e2;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is prefered.
     */

    @Override
    public int getImplementationMethod() {
        return Expression.ITERATE_METHOD | ITEM_FEED_METHOD | WATCH_METHOD;
    }

    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions. This default implementation handles iteration for expressions that
     * return singleton values: for non-singleton expressions, the subclass must
     * provide its own implementation.
     *
     * @param context supplies the context for evaluation
     * @return a SequenceIterator that can be used to iterate over the result
     *         of the expression
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        return new ItemMappingIterator(getBaseExpression().iterate(context), this);
    }

    /**
     * Map an item to another item
     *
     * @param item The input item to be mapped.
     * @return the result of the mapping: maybe null
     * @throws XPathException
     */

    /*@Nullable*/
    @Override
    public Item mapItem(Item item) throws XPathException {
        if (item instanceof NodeInfo &&
            ((NodeInfo) item).getNodeKind() == Type.TEXT &&
            item.getStringValueCS().length() == 0) {
            return null;
        } else {
            return item;
        }
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "EmptyTextNodeRemover";
    }

    @Override
    public String getExpressionName() {
        return "emptyTextNodeRemover";
    }

}

