////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.GroupIterator;
import net.sf.saxon.expr.sort.MergeInstr;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;


/**
 * Implements the XSLT function current-grouping-key()
 */

public class CurrentMergeKey extends SystemFunction implements Callable {

    private MergeInstr controllingInstruction = null; // may be unknown, when current group has dynamic scope

    /**
     * Set the containing xsl:for-each-group instruction, if there is one
     *
     * @param instruction the (innermost) containing xsl:for-each-group instruction
     */

    public void setControllingInstruction(MergeInstr instruction) {
        this.controllingInstruction = instruction;
    }

    /**
     * Get the innermost containing xsl:merge instruction, if there is one
     *
     * @return the innermost containing xsl:merge instruction
     */

    public MergeInstr getControllingInstruction() {
        return controllingInstruction;
    }


    /**
     * Make an expression that either calls this function, or that is equivalent to a call
     * on this function
     *
     * @param arguments the supplied arguments to the function call
     * @return either a function call on this function, or an expression that delivers
     * the same result
     */
    @Override
    public Expression makeFunctionCall(Expression... arguments) {
        return new SystemFunctionCall(this, arguments) {
            @Override
            public Expression getScopingExpression() {
                return getControllingInstruction();
            }
        };
    }

    /**
     * Evaluate the expression
     */

    /*@NotNull*/
    //@Override
    public SequenceIterator iterate(XPathContext c) throws XPathException {
        GroupIterator gi = c.getCurrentMergeGroupIterator();
        if (gi == null) {
            throw new XPathException("There is no current merge key", "XTDE3510");
        }
        return gi.getCurrentGroupingKey().iterate();

    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return SequenceTool.toLazySequence(iterate(context));
    }

}


