////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.WherePopulatedOutputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Base64BinaryValue;
import net.sf.saxon.value.HexBinaryValue;
import net.sf.saxon.value.StringValue;

/**
 * A compiled xsl:where-populated instruction (formerly xsl:conditional-content).
 */
public class WherePopulated extends UnaryExpression implements ItemMappingFunction {


    /**
     * Create the instruction
     */
    public WherePopulated(Expression base) {
        super(base);
    }

    /**
     * Ask whether this expression is an instruction. In XSLT streamability analysis this
     * is used to distinguish constructs corresponding to XSLT instructions from other constructs.
     *
     * @return true if this construct originates as an XSLT instruction
     */
    @Override
    public boolean isInstruction() {
        return true;
    }

    /**
     * Get the usage (in terms of streamability analysis) of the single operand
     *
     * @return the operand usage
     */
    @Override
    protected OperandRole getOperandRole() {
        return new OperandRole(0, OperandUsage.TRANSMISSION);
    }

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        return new WherePopulated(getBaseExpression().copy(rebindings));
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
        return ITERATE_METHOD | PROCESS_METHOD;
    }

    @Override
    public int computeCardinality() {
        return super.computeCardinality() | StaticProperty.ALLOWS_ZERO;
    }

    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions.
     *
     * @param context supplies the context for evaluation
     * @return a SequenceIterator that can be used to iterate over the result
     * of the expression
     * @throws net.sf.saxon.trans.XPathException if any dynamic error occurs evaluating the
     *                                           expression
     */
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        return new ItemMappingIterator(getBaseExpression().iterate(context), this);
    }

    /**
     * Process the instruction, without returning any tail calls
     *
     *
     * @param output the destination for the result
     * @param context The dynamic context, giving access to the current node,
     *                the current variables, etc.
     * @throws XPathException if a dynamic error occurs
     */
    @Override
    public void process(Outputter output, XPathContext context) throws XPathException {
        WherePopulatedOutputter filter = new WherePopulatedOutputter(output);
        getBaseExpression().process(filter, context);
    }

    /**
     * Map one item to another item.
     *
     * @param item The input item to be mapped.
     * @return either the output item, or null.
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs
     */
    @Override
    public Item mapItem(Item item) throws XPathException {
        return isDeemedEmpty(item) ? null : item;
    }

    /**
     * Test whether an item is empty according to the definition in the spec
     * for xsl:conditional-content
     * @param item the item to be tested
     * @return true if it is "empty", for example, an element without children
     */

    public static boolean isDeemedEmpty(Item item)  {
        if (item instanceof NodeInfo) {
            int kind = ((NodeInfo) item).getNodeKind();
            switch (kind) {
                case Type.DOCUMENT:
                case Type.ELEMENT:
                    return !((NodeInfo) item).hasChildNodes();
                default:
                    return item.getStringValueCS().length() == 0;
            }
        } else if (item instanceof StringValue || item instanceof HexBinaryValue || item instanceof Base64BinaryValue) {
            return item.getStringValueCS().length() == 0;
        } else if (item instanceof MapItem) {
            return ((MapItem)item).isEmpty();
        } else {
            return false;
        }
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should usually match
     * the name used in export() output displaying the expression, unless compatibility constraints
     * intervene.
     */
    @Override
    public String getExpressionName() {
        return "wherePop";
    }



    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("condCont", this);
        getBaseExpression().export(out);
        out.endElement();
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "WherePopulated";
    }
}

