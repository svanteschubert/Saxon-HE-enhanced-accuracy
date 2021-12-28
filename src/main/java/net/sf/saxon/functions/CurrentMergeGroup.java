////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.GroupIterator;
import net.sf.saxon.expr.sort.MergeGroupingIterator;
import net.sf.saxon.expr.sort.MergeInstr;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;

import java.util.HashSet;
import java.util.Set;

/**
 * Implements the XSLT 3.0 function current-merge-group()
 */

public class CurrentMergeGroup extends SystemFunction {

    private boolean isInLoop = false;
    private MergeInstr controllingInstruction = null; // may be unknown, when current group has dynamic scope
    private Set<String> allowedNames = new HashSet<>();

    /**
     * Set the containing xsl:merge instruction, if there is one
     * @param instruction the (innermost) containing xsl:merge instruction
     * @param isInLoop true if the current-merge-group() expression is evaluated more than once during
     * evaluation of the body of the xsl:merge instruction
     */

    public void setControllingInstruction(MergeInstr instruction, boolean isInLoop) {
        this.controllingInstruction = instruction;
        this.isInLoop = isInLoop;
        for (MergeInstr.MergeSource m : instruction.getMergeSources()) {
            String name = m.sourceName;
            if (name != null) {
                allowedNames.add(name);
            }
        }
    }

    /**
     * Get the innermost containing xsl:merge instruction, if there is one
     * @return the innermost containing xsl:merge instruction
     */

    public MergeInstr getControllingInstruction() {
        return controllingInstruction;
    }

    /**
     * Determine whether the current-group() function is executed repeatedly within a single iteration
     * of the containing xsl:for-each-group
     * @return true if it is evaluated repeatedly
     */

    public boolean isInLoop() {
        return isInLoop;
    }

    /**
     * Determine the item type of the value returned by the function
     */

    @Override
    public ItemType getResultItemType() {
        return AnyItemType.getInstance();
    }

    /**
     * Determine the special properties of this expression.
     *
     * @return {@link net.sf.saxon.expr.StaticProperty#NO_NODES_NEWLY_CREATED} (unless the variable is assignable using saxon:assign)
     * @param arguments the actual arguments supplied to the function call
     */
    @Override
    public int getSpecialProperties(Expression[] arguments) {
        return 0;
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
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments /*@NotNull*/) throws XPathException {
        String source = null;
        if (arguments.length > 0) {
            source = arguments[0].head().getStringValue();
        }
        return SequenceTool.toLazySequence(currentGroup(source, context));
    }

    /**
     * Private method to perform the evaluation
     * @param source the first argument to the function, the name of the merge source; or null if not supplied
     * @param c the dynamic evaluation context
     * @return an iterator over the selected items
     * @throws XPathException if a dynamic error occurs
     */

    private SequenceIterator currentGroup(String source, XPathContext c) throws XPathException {
        GroupIterator gi = c.getCurrentMergeGroupIterator();
        if (gi == null) {
            throw new XPathException("There is no current merge group", "XTDE3480");
        }
        if (source == null) {
            return gi.iterateCurrentGroup();
        } else {
            if (!allowedNames.contains(source)) {
                throw new XPathException("Supplied argument (" + source +
                        ") is not the name of any xsl:merge-source in the containing xsl:merge instruction", "XTDE3490");
            }
            return ((MergeGroupingIterator)gi).iterateCurrentGroup(source);
        }

    }

    @Override
    public String getStreamerName() {
        return "CurrentMergeGroup";
    }

}


