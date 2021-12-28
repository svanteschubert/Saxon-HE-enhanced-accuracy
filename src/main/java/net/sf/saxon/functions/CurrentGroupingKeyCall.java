////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.expr.sort.GroupIterator;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.AtomicValue;


/**
 * Implements the XSLT function current-grouping-key()
 */

public class CurrentGroupingKeyCall extends Expression implements Callable {

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
        return CurrentGroupCall.findControllingInstruction(this);
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
        // Can return an empty sequence in 2.0 mode
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
     * Determine the data type of the expression, if possible. All expression return
     * sequences, in general; this method determines the type of the items within the
     * sequence, assuming that (a) this is known in advance, and (b) it is the same for
     * all items in the sequence.
     * <p>This method should always return a result, though it may be the best approximation
     * that is available at the time.</p>
     *
     * @return a value such as Type.STRING, Type.BOOLEAN, Type.NUMBER,
     * Type.NODE, or Type.ITEM (meaning not known at compile time)
     */
    @Override
    public ItemType getItemType() {
        return BuiltInAtomicType.ANY_ATOMIC;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     */
    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("currentGroupingKey");
        out.endElement();
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings
     */
    @Override
    public Expression copy(RebindingMap rebindings) {
        return new CurrentGroupingKeyCall();
    }

    /**
     * Determine the dependencies
     */

    @Override
    public int getIntrinsicDependencies() {
        return StaticProperty.DEPENDS_ON_CURRENT_GROUP;
    }

    /**
     * Evaluate the expression
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext c) throws XPathException {
        GroupIterator gi = c.getCurrentGroupIterator();
        AtomicSequence result = gi==null ? null : gi.getCurrentGroupingKey();
        if (result == null) {
            XPathException err = new XPathException("There is no current grouping key", "XTDE1071");
            err.setLocation(getLocation());
            throw err;
        }
        return result.iterate();
    }

    /**
     * Call the Callable.
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
}

