////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;

import java.util.Arrays;


/**
 * Represents the set of xsl:param elements at the start of an xsl:iterate instruction
 */

public class LocalParamBlock extends Instruction {

    Operand[] operanda;

    /**
     * Create the block of parameters
     *
     * @param params the parameters
     */

    public LocalParamBlock(LocalParam[] params) {
        operanda = new Operand[params.length];
        for (int i=0; i<params.length; i++) {
            operanda[i] = new Operand(this, params[i], OperandRole.NAVIGATE);
        }
    }

    @Override
    public String getExpressionName() {
        return "params";
    }

    @Override
    public Iterable<Operand> operands() {
        return Arrays.asList(operanda);
    }

    public int getNumberOfParams() {
        return operanda.length;
    }

    @Override
    public int computeSpecialProperties() {
        return 0;
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
        LocalParam[] lps2 = new LocalParam[getNumberOfParams()];
        int i=0;
        for (Operand o : operands()) {
            LocalParam oldLps = (LocalParam) o.getChildExpression();
            LocalParam newLps = oldLps.copy(rebindings);
            rebindings.put(oldLps, newLps);
            lps2[i++] = newLps;
        }
        return new LocalParamBlock(lps2);
    }

    /**
     * Determine the data type of the items returned by this expression
     *
     * @return the data type
     */

    /*@NotNull*/
    @Override
    public final ItemType getItemType() {
        return ErrorType.getInstance();
    }

    /**
     * Determine the cardinality of the expression
     */

    @Override
    public final int getCardinality() {
        return StaticProperty.EMPTY;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("params", this);
        for (Operand o : operands()) {
            o.getChildExpression().export(out);
        }
        out.endElement();
    }


    /*@Nullable*/
    @Override
    public TailCall processLeavingTail(Outputter output, XPathContext context) throws XPathException {
        for (Operand o : operands()) {
            LocalParam param = (LocalParam)o.getChildExpression();
            try {
                context.setLocalVariable(param.getSlotNumber(), param.getSelectValue(context));
            } catch (XPathException e) {
                e.maybeSetLocation(param.getLocation());
                e.maybeSetContext(context);
                throw e;
            }
        }
        return null;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides both iterate() and
     * process() methods natively.
     */

    @Override
    public int getImplementationMethod() {
        return PROCESS_METHOD;
    }


}

