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
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XmlProcessingException;
import net.sf.saxon.tree.jiter.MonoIterator;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.value.SequenceType;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;

/**
 * Information about a sub-expression and its relationship to the parent expression
 */

public final class Operand implements Iterable<Operand>, ExpressionOwner {

    private final Expression parentExpression;
    private Expression childExpression;
    private OperandRole role;

    /**
     * Create an operand object
     * @param parentExpression the expression of which this is an operand
     * @param childExpression the actual expression used as the operand
     * @param role information about the role this operand plays within the parent expression
     */

    public Operand(Expression parentExpression, Expression childExpression, OperandRole role) {
        this.parentExpression = parentExpression;
        this.role = role;
        setChildExpression(childExpression);
    }

    /**
     * Get the parent expression of this operand
     *
     * @return the parent expression
     */
    public Expression getParentExpression() {
        return parentExpression;
    }

    /**
     * Get the expression used as the actual operand
     *
     * @return the child expression
     */
    @Override
    public Expression getChildExpression() {
        return childExpression;
    }

    /**
     * Change the child expression used for this operand. This is the only approved way of
     * making structural changes to the expression tree, and the implementation of this
     * method is responsible for maintaining the consistency of the tree
     * @param childExpression the new child expression
     */

    @Override
    public void setChildExpression(Expression childExpression) {
        if (childExpression != this.childExpression) {
            if (role.isConstrainedClass()) {
                if (role.getConstraint() != null) {
                    if (!role.getConstraint().test(childExpression)) {
                        throw new AssertionError();
                    }
                } else if (this.childExpression != null && childExpression.getClass() != this.childExpression.getClass()) {
                    throw new AssertionError();
                }
            }
            this.childExpression = childExpression;
            parentExpression.adoptChildExpression(childExpression);
            parentExpression.resetLocalStaticProperties();
            //childExpression.verifyParentPointers();
        }
    }

    private static final boolean DEBUG = false;
    public void detachChild() {
        if (DEBUG) {
            childExpression.setParentExpression(null);
            StringWriter sw = new StringWriter();
            new XPathException("dummy").printStackTrace(new PrintWriter(sw));
            childExpression = new ErrorExpression("child expression has been detached: " + sw.toString(), "ZZZ", false);
            ExpressionTool.copyLocationInfo(parentExpression, childExpression);
        }
    }

    /**
     * Get the operand role
     * @return the operand role
     */

    public OperandRole getOperandRole() {
        return role;
    }

    /**
     * Set the operand role
     * @param role the operand role
     */

    public void setOperandRole(OperandRole role) {
        this.role = role;
    }

    /**
     * Ask whether the child expression sets a new focus for evaluation of other operands
     *
     * @return true if the child expression is evaluated with the same focus as its parent expression
     */
    public boolean setsNewFocus() {
        return role.setsNewFocus();
    }

    public boolean hasSpecialFocusRules() {
        return role.hasSpecialFocusRules();
    }

    /**
     * Ask whether the child expression is evaluated with the same focus as its parent expression
     *
     * @return true if the child expression is evaluated with the same focus as its parent expression
     */
    public boolean hasSameFocus() {
        return role.hasSameFocus();
    }

    /**
     * Ask whether the operand is a higher-order operand,: typically this means that the child expression
     * is evaluated repeatedly during a single evaluation of the parent expression (but there are some cases
     * like xsl:for-each select=".." where the operand is higher-order despite not being evaluated
     * repeatedly).
     *
     * @return true if the operand is higher-order
     */
    public boolean isHigherOrder() {
        return role.isHigherOrder();
    }
    /**
     * Ask whether the operand is is evaluated repeatedly during a single evaluation of the parent
     * expression. This is true if the operand is higher-order and the operand does not have the
     * {@link OperandRole#SINGLETON} property.
     *
     * @return true if the operand is higher-order
     */
    public boolean isEvaluatedRepeatedly() {
        return role.isEvaluatedRepeatedly();
    }

    /**
     * Get the usage of the operand
     *
     * @return the usage
     */
    public OperandUsage getUsage() {
        return role.getUsage();
    }

    /**
     * Set the usage of the operand
     * @param usage the operand usage
     */

    public void setUsage(OperandUsage usage) {
        role = new OperandRole(role.properties, usage, role.getRequiredType());
    }

    /**
     * Get the required type of the operand
     *
     * @return the required type
     */
    public SequenceType getRequiredType() {
        return role.getRequiredType();
    }

    /**
     * Ask whether the operand is a member of a choice group
     * @return true if the operand is in a choice group
     */

    public boolean isInChoiceGroup() {
        return role.isInChoiceGroup();
    }

    /**
     * Get a singleton iterator that returns this operand only
     * @return a singleton iterator
     */

    @Override
    public Iterator<Operand> iterator() {
        return new MonoIterator<>(this);
    }

    public void typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        try {
            setChildExpression(getChildExpression().typeCheck(visitor, contextInfo));
        } catch (XPathException e) {
            e.maybeSetLocation(getChildExpression().getLocation());
            if (!e.isReportableStatically()) {
                visitor.getStaticContext().issueWarning(
                        "Evaluation will always throw a dynamic error: " + e.getMessage(), getChildExpression().getLocation());
                setChildExpression(new ErrorExpression(new XmlProcessingException(e)));
            } else {
                throw e;
            }
        }
    }

    public void optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        try {
            setChildExpression(getChildExpression().optimize(visitor, contextInfo));
        } catch (XPathException e) {
            e.maybeSetLocation(getChildExpression().getLocation());
            if (!e.isReportableStatically()) {
                visitor.getStaticContext().issueWarning(
                        "Evaluation will always throw a dynamic error: " + e.getMessage(), getChildExpression().getLocation());
                setChildExpression(new ErrorExpression(new XmlProcessingException(e)));
            } else {
                throw e;
            }
        }
    }

    public static OperandUsage typeDeterminedUsage(net.sf.saxon.type.ItemType type) {
        if (type.isPlainType()) {
            return OperandUsage.ABSORPTION;
        } else if (type instanceof NodeTest || type == AnyItemType.getInstance()) {
            return OperandUsage.NAVIGATION;
        } else {
            return OperandUsage.INSPECTION;
        }
    }

}

