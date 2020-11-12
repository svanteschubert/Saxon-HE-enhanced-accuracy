////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Operand;
import net.sf.saxon.expr.OperandRole;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;

import java.util.Arrays;


/**
 * This class implements an xsl:fork expression.
 */

public class Fork extends Instruction {

    Operand[] operanda;

    /**
     * Create a Fork instruction
     * @param prongs the children of the xsl:fork instructions.
     */

    public Fork(Operand[] prongs) {
        operanda = new Operand[prongs.length];
        for (int i=0; i<prongs.length; i++) {
            operanda[i] = new Operand(this, prongs[i].getChildExpression(), OperandRole.SAME_FOCUS_ACTION);
        }
    }

    public Fork(Expression[] prongs) {
        operanda = new Operand[prongs.length];
        for (int i = 0; i < prongs.length; i++) {
            operanda[i] = new Operand(this, prongs[i], OperandRole.SAME_FOCUS_ACTION);
        }
    }

    @Override
    public Iterable<Operand> operands() {
        return Arrays.asList(operanda);
    }

    /**
     * Get the namecode of the instruction for use in diagnostics
     *
     * @return a code identifying the instruction: typically but not always
     *         the fingerprint of a name in the XSLT namespace
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_FORK;
    }

    /**
     * Get the number of prongs in the fork
     * @return the number of xsl:sequence children
     */

    public int getSize() {
        return operanda.length;
    }

    /**
     * Get the n'th child expression of the xsl:fork (zero-based)
     * @param i the index of the required subexpression
     * @return the i'th subexpression if it exists
     * @throws IllegalArgumentException if i is out of range
     */

    public Expression getProng(int i) {
        return operanda[i].getChildExpression();
    }

    /**
     * Determine the item type of the value returned by the function
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        if (getSize() == 0) {
            return ErrorType.getInstance();
        }
        ItemType t1 = null;
        for (Operand o : operands()) {
            ItemType t2 = o.getChildExpression().getItemType();
            t1 = t1 == null ? t2 : Type.getCommonSuperType(t1, t2);
            if (t1 instanceof AnyItemType) {
                return t1;  // no point going any further
            }
        }
        return t1;
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "Fork";
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
        Expression[] e2 = new Expression[getSize()];
        int i=0;
        for (Operand o : operands()) {
            e2[i++] = o.getChildExpression().copy(rebindings);
        }
        Fork f2 = new Fork(e2);
        ExpressionTool.copyLocationInfo(this, f2);
        return f2;
    }

    /**
     * Process the instruction, without returning any tail calls. This is the
     * non-streamed mode of operation, which processes the child expressions
     * in turn exactly like a sequence constructor.
     *
     * @param output the destination for the result
     * @param context The dynamic context, giving access to the current node,
     */
    @Override
    public TailCall processLeavingTail(Outputter output, XPathContext context) throws XPathException {
        for (Operand o : operands()) {
            o.getChildExpression().process(output, context);
        }
        return null;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("fork", this);
        for (Operand o : operands()) {
            o.getChildExpression().export(out);
        }
        out.endElement();
    }


}
