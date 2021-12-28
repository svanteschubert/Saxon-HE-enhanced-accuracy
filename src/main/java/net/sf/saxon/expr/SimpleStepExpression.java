////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.SequenceType;


/**
 * A SimpleStepExpression is a special case of a SlashExpression in which the
 * start expression selects a single item (or nothing), and the step expression is
 * a simple AxisExpression. This is designed to avoid the costs of creating a new
 * dynamic context for expressions (common in XQuery) such as
 * for $b in EXPR return $b/title
 */

public final class SimpleStepExpression extends SlashExpression {

    public SimpleStepExpression(Expression start, Expression step) {
        super(start, step);
        if (!(step instanceof AxisExpression)) {
            throw new IllegalArgumentException();
        }
    }

    private static OperandRole STEP_ROLE = new OperandRole(OperandRole.USES_NEW_FOCUS | OperandRole.HIGHER_ORDER, OperandUsage.TRANSMISSION, SequenceType.ANY_SEQUENCE);

    @Override
    protected OperandRole getOperandRole(int arg) {
        return arg == 0 ? OperandRole.FOCUS_CONTROLLING_SELECT : STEP_ROLE;
    }

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        getLhs().typeCheck(visitor, contextInfo);

        ItemType selectType = getStart().getItemType();
        if (selectType == ErrorType.getInstance()) {
            return Literal.makeEmptySequence();
        }

        ContextItemStaticInfo cit = visitor.getConfiguration().makeContextItemStaticInfo(selectType, false);
        cit.setContextSettingExpression(getStart());

        getRhs().typeCheck(visitor, cit);

        if (!(getStep() instanceof AxisExpression)) {
            if (Literal.isEmptySequence(getStep())) {
                return getStep();
            }
            SlashExpression se = new SlashExpression(getStart(), getStep());
            ExpressionTool.copyLocationInfo(this, se);
            return se;
        }
        if (getStart() instanceof ContextItemExpression && AxisInfo.isForwards[((AxisExpression) getStep()).getAxis()]) {
            return getStep();
        }
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        return this;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings
     */
    /*@NotNull*/
    @Override
    public SimpleStepExpression copy(RebindingMap rebindings) {
        SimpleStepExpression exp = new SimpleStepExpression(getStart().copy(rebindings), getStep().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
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
     * Evaluate the expression, returning an iterator over the result
     *
     * @param context the evaluation context
     */
    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        NodeInfo origin = null;
        try {
            origin = (NodeInfo) getStart().evaluateItem(context);
        } catch (XPathException e) {
            if ("XPDY0002".equals(e.getErrorCodeLocalPart()) && !e.hasBeenReported()) {
                throw new XPathException("The context item for axis step "
                    + toShortString() + " is absent", "XPDY0002", getLocation());
            } else {
                throw e;
            }
        }
        if (origin == null) {
            return EmptyIterator.getInstance();
        }
        return ((AxisExpression) getStep()).iterate(origin);
    }

    @Override
    public String getExpressionName() {
        return "simpleStep";
    }


}

