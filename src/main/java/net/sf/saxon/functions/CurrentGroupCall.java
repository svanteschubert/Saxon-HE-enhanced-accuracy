////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.ForEachGroup;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.expr.sort.GroupIterator;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.AtomicValue;

/**
 * Implements the XSLT function current-group()
 */

public class CurrentGroupCall extends Expression implements Callable {

    private boolean isInHigherOrderOperand = false;
    private ItemType itemType = AnyItemType.getInstance();
    private ForEachGroup controllingInstruction = null; // may be unknown, when current group has dynamic scope

    /**
     * Get the innermost scoping expression of this expression, for expressions that directly
     * depend on something in the dynamic context. For example, in the case of a local variable
     * reference this is the expression that causes the relevant variable to be bound; for a
     * context item expression it is the innermost focus-setting container. For expressions
     * that have no intrinsic dependency on the dynamic context, the value returned is null;
     * the scoping container for such an expression is the innermost scoping container of its
     * operands.
     *
     * @return the innermost scoping container of this expression
     */
    @Override
    public Expression getScopingExpression() {
        return getControllingInstruction();
    }

    /**
     * Set the containing xsl:for-each-group instruction, if there is one
     *
     * @param instruction   the (innermost) containing xsl:for-each-group instruction
     * @param itemType      the statically inferred item type of the grouping population
     * @param isHigherOrder true typically if the current-group() expression is evaluated more than once during
     *                      evaluation of the body of the for-each-group instruction; or more generally, if there is an operand on
     *                      the path between the controlling for-each-group instruction and this current-group()
     *                      call that is a higher-order operand of its parent instruction.
     */

    public void setControllingInstruction(ForEachGroup instruction, ItemType itemType, boolean isHigherOrder) {
        resetLocalStaticProperties();
        this.controllingInstruction = instruction;
        this.isInHigherOrderOperand = isHigherOrder;
        this.itemType = itemType;
    }

    @Override
    public void resetLocalStaticProperties() {
        super.resetLocalStaticProperties();
        this.controllingInstruction = null;
        this.itemType = AnyItemType.getInstance();
    }

    /**
     * Get the innermost containing xsl:for-each-group instruction, if there is one
     *
     * @return the innermost containing xsl:for-each-group instruction
     */

    public ForEachGroup getControllingInstruction() {
        if (controllingInstruction == null) {
            controllingInstruction = findControllingInstruction(this);
        }
        return controllingInstruction;
    }

    public static ForEachGroup findControllingInstruction(Expression exp) {
        Expression child = exp;
        Expression parent = exp.getParentExpression();
        while (parent != null) {
            if (parent instanceof ForEachGroup &&
                    (child == ((ForEachGroup) parent).getActionExpression() || child == ((ForEachGroup) parent).getSortKeyDefinitionList())) {
                return (ForEachGroup) parent;
            }
            child = parent;
            parent = parent.getParentExpression();
        }
        return null;
    }

    /**
     * Determine whether the current-group() function is executed repeatedly within a single iteration
     * of the containing xsl:for-each-group
     *
     * @return true if it is evaluated repeatedly
     */

    public boolean isInHigherOrderOperand() {
        return isInHigherOrderOperand;
    }

    /**
     * Determine the item type of the value returned by the function
     */

    @Override
    public ItemType getItemType() {
        if (itemType == AnyItemType.getInstance() && controllingInstruction != null) {
            itemType = controllingInstruction.getSelectExpression().getItemType();
        }
        return itemType;
    }

    /**
     * Determine the dependencies
     */

    @Override
    public int getIntrinsicDependencies() {
        return StaticProperty.DEPENDS_ON_CURRENT_GROUP;
    }

    /**
     * Compute the static cardinality of this expression
     *
     * @return the computed cardinality, as one of the values {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ZERO_OR_ONE},
     * {@link net.sf.saxon.expr.StaticProperty#EXACTLY_ONE}, {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ONE_OR_MORE},
     * {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ZERO_OR_MORE}
     */
    @Override
    protected int computeCardinality() {
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
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
        return ITERATE_METHOD;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     */
    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("currentGroup");
        out.endElement();
    }

    /**
     * Determine the special properties of this expression. The properties such as document-ordering are the same as
     * the properties of the grouping population as a whole.
     *
     * @return {@link net.sf.saxon.expr.StaticProperty#NO_NODES_NEWLY_CREATED} (unless the variable is assignable using saxon:assign)
     */
    @Override
    public int computeSpecialProperties() {
        if (getControllingInstruction() == null) {
            return 0;
        } else {
            return controllingInstruction.getSelectExpression().getSpecialProperties();
        }
    }

    @Override
    public Expression copy(RebindingMap rebindings) {
        CurrentGroupCall cg = new CurrentGroupCall();
        cg.isInHigherOrderOperand = isInHigherOrderOperand;
        cg.itemType = itemType;
        cg.controllingInstruction = controllingInstruction;
        return cg;
    }

    /**
     * Return an iteration over the result sequence
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext c) throws XPathException {
        GroupIterator gi = c.getCurrentGroupIterator();
        if (gi == null) {
            XPathException err = new XPathException("There is no current group", "XTDE1061");
            err.setLocation(getLocation());
            throw err;
        }
        return gi.iterateCurrentGroup();
    }

    /**
     * Call the Callable (used from generated bytecode).
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences.
     *                  <p>Generally it is advisable, if calling iterate() to process a supplied sequence, to
     *                  call it only once; if the value is required more than once, it should first be converted
     *                  to a {@link GroundedValue} by calling the utility method
     *                  SequenceTool.toGroundedValue().</p>
     *                  <p>If the expected value is a single item, the item should be obtained by calling
     *                  Sequence.head(): it cannot be assumed that the item will be passed as an instance of
     *                  {@link Item} or {@link AtomicValue}.</p>
     *                  <p>It is the caller's responsibility to perform any type conversions required
     *                  to convert arguments to the type expected by the callee. An exception is where
     *                  this Callable is explicitly an argument-converting wrapper around the original
     *                  Callable.</p>
     * @return the result of the evaluation, in the form of a Sequence. It is the responsibility
     * of the callee to ensure that the type of result conforms to the expected result type.
     * @throws XPathException if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return SequenceTool.toLazySequence(iterate(context));
    }

    /**
     * <p>The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form.</p>
     * <p>For subclasses of Expression that represent XPath expressions, the result should always be a string that
     * parses as an XPath 3.0 expression. The expression produced should be equivalent to the original making certain
     * assumptions about the static context. In general the expansion will make no assumptions about namespace bindings,
     * except that (a) the prefix "xs" is used to refer to the XML Schema namespace, and (b) the default funcion namespace
     * is assumed to be the "fn" namespace.</p>
     * <p>In the case of XSLT instructions and XQuery expressions, the toString() method gives an abstracted view of the syntax
     * that is not designed in general to be parseable.</p>
     *
     * @return a representation of the expression as a string
     */
    @Override
    public String toString() {
        return "current-group()";
    }

    /**
     * Produce a short string identifying the expression for use in error messages
     *
     * @return a short string, sufficient to identify the expression
     */
    @Override
    public String toShortString() {
        return toString();
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "CurrentGroup";
    }

}

