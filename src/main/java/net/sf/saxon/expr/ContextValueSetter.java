////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.PullElaborator;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.elab.SequenceEvaluator;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.ma.Parcel;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ManualIterator;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.SequenceType;


/**
 * Abstract 4.0 expression of the form "A -> B".
 */

public class ContextValueSetter extends BinaryExpression
        implements ContextSwitchingExpression {

    /**
     * Constructor
     *
     * @param start The left hand operand (which must always select a sequence of nodes).
     * @param step  The step to be followed from each node in the start expression to yield a new
     *              sequence; this may return either nodes or atomic values (but not a mixture of the two)
     */

    public ContextValueSetter(Expression start, Expression step) {
        super(start, OperatorSymbol.THIN_ARROW, step);
    }

    @Override
    protected OperandRole getOperandRole(int arg) {
        return arg==0 ? OperandRole.FOCUS_CONTROLLING_SELECT : OperandRole.FOCUS_CONTROLLED_ACTION;
    }

    @Override
    public String getExpressionName() {
        return "contextValueSetter";
    }

    /**
     * Get the start expression (the left-hand operand)
     *
     * @return the first operand
     */

    @Override
    public Expression getSelectExpression() {
        return getLhsExpression();
    }

    /**
     * Get the step expression (the right-hand operand)
     *
     * @return the second operand
     */

    @Override
    public Expression getActionExpression() {
        return getRhsExpression();
    }

    /**
     * Determine the data type of the items returned by this expression
     *
     * @return the type of the start expression
     */

    /*@NotNull*/
    @Override
    public final ItemType getItemType() {
        return getRhsExpression().getItemType();
    }


    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        return getRhsExpression().getStaticUType(contextItemType);
    }


    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        getLhs().typeCheck(visitor, contextInfo);

        final Configuration config = visitor.getConfiguration();
        SequenceType type = SequenceType.makeSequenceType(
                getLhsExpression().getItemType(), getLhsExpression().getCardinality());
        ContextItemStaticInfo cit = config.makeContextItemStaticInfo(type)
                .withContextSetter(getLhsExpression());
        getRhs().typeCheck(visitor, cit);

        return this;
    }


    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        return super.optimize(visitor, contextItemType);
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
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings variables that need to be re-bound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        return new ContextValueSetter(getLhsExpression().copy(rebindings), getRhsExpression().copy(rebindings));
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-significant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     */

    @Override
    protected int computeSpecialProperties() {
        return getRhsExpression().getSpecialProperties();
    }


    /**
     * Determine the static cardinality of the expression
     */

    @Override
    protected int computeCardinality() {
        return getRhsExpression().getCardinality();
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        if (!(other instanceof ContextValueSetter)) {
            return false;
        }
        ContextValueSetter p = (ContextValueSetter) other;
        return getLhsExpression().isEqual(p.getLhsExpression()) && getRhsExpression().isEqual(p.getRhsExpression());
    }

    /**
     * Get hashCode for comparing two expressions
     */

    @Override
    protected int computeHashCode() {
        return "ContextValueSetter".hashCode() + getLhsExpression().hashCode() + getRhsExpression().hashCode();
    }


    /**
     * Export expression structure to SEF file. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("pipe", this);
        getLhsExpression().export(destination);
        getRhsExpression().export(destination);
        destination.endElement();
    }
    

    public Item evaluateItem(XPathContext context) throws XPathException {
        return makeElaborator().elaborateForItem().eval(context);
    }

    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        return makeElaborator().elaborateForPull().iterate(context);
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new ContextValueSetterElaborator();
    }

    /**
     * Elaborator for a context value setter expression.
     */

    public static class ContextValueSetterElaborator extends PullElaborator {

        @Override
        public PullEvaluator elaborateForPull() {
            ContextValueSetter expr = (ContextValueSetter) getExpression();
            SequenceEvaluator select = expr.getLhsExpression().makeElaborator().eagerly();
            PullEvaluator action = expr.getRhsExpression().makeElaborator().elaborateForPull();
            return context -> {
                XPathContext c2 = context.newMinorContext();
                Sequence lhs = select.evaluate(context);
                if (lhs instanceof Item) {
                    ManualIterator lhsIter = new ManualIterator((Item) lhs);
                    c2.setCurrentIterator(lhsIter);
                    return action.iterate(c2);
                } else {
                    GroundedValue val = lhs.materialize();
                    if (val.getLength() == 1) {
                        // We're being defensive here because there are many paths that can't yet
                        // deal with generalized context values
                        ManualIterator valIter = new ManualIterator(val.head());
                        c2.setCurrentIterator(valIter);
                        return action.iterate(c2);
                    }
                    ManualIterator parcelIter = new ManualIterator(new Parcel(val));
                    c2.setCurrentIterator(parcelIter);
                    return action.iterate(c2);
                }
            };
        }

    }
}

