////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.SignificantItemDetector;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.Action;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * An XSLT 3.0 sequence constructor containing xsl:on-empty and/or xsl:on-non-empty instructions
 */

public class ConditionalBlock extends Instruction {

    private Operand[] operanda;
    private boolean allNodesUntyped;

    /**
     * Create a block, supplying its child expressions
     * @param children the child expressions in order
     */

    public ConditionalBlock(Expression[] children) {
        operanda = new Operand[children.length];
        for (int i=0; i<children.length; i++) {
            operanda[i] = new Operand(this, children[i], OperandRole.SAME_FOCUS_ACTION);
            if (children[i] instanceof OnEmptyExpr) {
                operanda[i].setOperandRole(OperandRole.SAME_FOCUS_ACTION.withConstrainedClass());
            }
        }
    }

    /**
     * Create a block, supplying its child expressions
     *
     * @param children the child expressions in order
     */

    public ConditionalBlock(List<Expression> children) {
        this(children.toArray(new Expression[children.size()]));
    }

    /**
     * Get the n'th child expression
     * @param n the position of the child expression required (zero-based)
     * @return the child expression at that position
     */

    public Expression getChildExpression(int n) {
        return operanda[n].getChildExpression();
    }

    /**
     * Get the number of children
     * @return the number of child subexpressions
     */

    public int size() {
        return operanda.length;
    }

    @Override
    public Iterable<Operand> operands() {
        return Arrays.asList(operanda);
    }


    @Override
    public String getExpressionName() {
        return "condSeq";
    }


    @Override
    public int computeSpecialProperties() {
        if (size() == 0) {
            // An empty sequence has all special properties except "has side effects".
            return StaticProperty.SPECIAL_PROPERTY_MASK & ~StaticProperty.HAS_SIDE_EFFECTS;
        }
        int p = super.computeSpecialProperties();
        if (allNodesUntyped) {
            p |= StaticProperty.ALL_NODES_UNTYPED;
        }
        // if all the expressions are axis expressions, we have a same-document node-set
        boolean allAxisExpressions = true;
        boolean allChildAxis = true;
        boolean allSubtreeAxis = true;
        for (Operand o : operands()) {
            Expression child = o.getChildExpression();
            if (!(child instanceof AxisExpression)) {
                allAxisExpressions = false;
                allChildAxis = false;
                allSubtreeAxis = false;
                break;
            }
            int axis = ((AxisExpression) child).getAxis();
            if (axis != AxisInfo.CHILD) {
                allChildAxis = false;
            }
            if (!AxisInfo.isSubtreeAxis[axis]) {
                allSubtreeAxis = false;
            }
        }
        if (allAxisExpressions) {
            p |= StaticProperty.CONTEXT_DOCUMENT_NODESET |
                    StaticProperty.SINGLE_DOCUMENT_NODESET |
                    StaticProperty.NO_NODES_NEWLY_CREATED;
            // if they all use the child axis, then we have a peer node-set
            if (allChildAxis) {
                p |= StaticProperty.PEER_NODESET;
            }
            if (allSubtreeAxis) {
                p |= StaticProperty.SUBTREE_NODESET;
            }
            // special case: selecting attributes then children, node-set is sorted
            if (size() == 2 &&
                    ((AxisExpression) getChildExpression(0)).getAxis() == AxisInfo.ATTRIBUTE &&
                    ((AxisExpression) getChildExpression(1)).getAxis() == AxisInfo.CHILD) {
                p |= StaticProperty.ORDERED_NODESET;
            }
        }
        return p;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings variables to be rebound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        Expression[] c2 = new Expression[size()];
        for (int c = 0; c < size(); c++) {
            c2[c] = getChildExpression(c).copy(rebindings);
        }
        ConditionalBlock b2 = new ConditionalBlock(c2);
        for (int c = 0; c < size(); c++) {
            b2.adoptChildExpression(c2[c]);
        }
        b2.allNodesUntyped = allNodesUntyped;
        ExpressionTool.copyLocationInfo(this, b2);
        return b2;
    }

    /**
     * Determine the data type of the items returned by this expression
     *
     * @return the data type
     */

    /*@NotNull*/
    @Override
    public final ItemType getItemType() {
        if (size() == 0) {
            return ErrorType.getInstance();
        }
        ItemType t1 = getChildExpression(0).getItemType();
        TypeHierarchy th = getConfiguration().getTypeHierarchy();
        for (int i = 1; i < size(); i++) {
            t1 = Type.getCommonSuperType(t1, getChildExpression(i).getItemType(), th);
            if (t1 instanceof AnyItemType) {
                return t1;  // no point going any further
            }
        }
        return t1;
    }

    /**
     * Determine the cardinality of the expression
     */

    @Override
    public final int getCardinality() {
        if (size() == 0) {
            return StaticProperty.EMPTY;
        }
        int c1 = getChildExpression(0).getCardinality();
        for (int i = 1; i < size(); i++) {
            c1 = Cardinality.sum(c1, getChildExpression(i).getCardinality());
            if (c1 == StaticProperty.ALLOWS_ZERO_OR_MORE) {
                break;
            }
        }
        return c1;
    }

    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns true if any child instruction
     * returns true.
     */

    @Override
    public final boolean mayCreateNewNodes() {
        return someOperandCreatesNewNodes();
    }


    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        typeCheckChildren(visitor, contextInfo);
        if (Block.neverReturnsTypedNodes(this, visitor.getConfiguration().getTypeHierarchy())) {
            resetLocalStaticProperties();
            allNodesUntyped = true;
        }
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        Expression e = super.optimize(visitor, contextInfo);
        if (e != this) {
            return e;
        }
        // This code was written when xsl:on-empty instructions were allowed anywhere, so it's more general
        // than strictly necessary.
        int lastOrdinaryInstruction = -1;
        boolean alwaysNonEmpty = false;
        boolean alwaysEmpty = true;
        for (int c = 0; c < size(); c++) {
            if (!(getChildExpression(c) instanceof OnEmptyExpr || getChildExpression(c) instanceof OnNonEmptyExpr)) {
                lastOrdinaryInstruction = c;
                if (getChildExpression(c).getItemType().getUType().intersection(UType.DOCUMENT.union(UType.TEXT)).equals(UType.VOID)) {
                    int card = getChildExpression(c).getCardinality();
                    if (!Cardinality.allowsZero(card)) {
                        alwaysNonEmpty = true;
                    }
                    if (card != StaticProperty.ALLOWS_ZERO) {
                        alwaysEmpty = false;
                    }
                } else {
                    alwaysEmpty = false;
                    alwaysNonEmpty = false;
                    break;
                }
            }
        }
        if (alwaysEmpty) {
            visitor.getStaticContext().issueWarning("The result of the sequence constructor will always be empty, so xsl:on-empty " +
                "instructions will always be evaluated, and xsl:on-non-empty instructions will never be evaluated", getLocation());
            List<Expression> retain = new ArrayList<Expression>();
            for (int c = 0; c < size(); c++) {
                if (getChildExpression(c) instanceof OnNonEmptyExpr) {
                    // no action
                } else if (getChildExpression(c) instanceof OnEmptyExpr) {
                    retain.add(((OnEmptyExpr) getChildExpression(c)).getBaseExpression());
                } else {
                    retain.add(getChildExpression(c));
                }
            }
            return Block.makeBlock(retain);
        }
        if (alwaysNonEmpty) {
            visitor.getStaticContext().issueWarning("The result of the sequence constructor will never be empty, so xsl:on-empty " +
                "instructions will never be evaluated, and xsl:on-non-empty instructions will always be evaluated", getLocation());
            List<Expression> retain = new ArrayList<Expression>();
            for (int c = 0; c < size(); c++) {
                if (getChildExpression(c) instanceof OnEmptyExpr) {
                    // no action
                } else if (getChildExpression(c) instanceof OnNonEmptyExpr) {
                    retain.add(((OnNonEmptyExpr) getChildExpression(c)).getBaseExpression());
                } else {
                    retain.add(getChildExpression(c));
                }
            }
            return Block.makeBlock(retain);
        }
        if (lastOrdinaryInstruction == -1) {
            // all instructions are either xsl:on-empty or xsl:on-non-empty
            // We can discard the xsl:on-non-empty instructions, and make the on-empty instructions unconditional
            List<Expression> retain = new ArrayList<Expression>();
            for (int c = 0; c < size(); c++) {
                if (getChildExpression(c) instanceof OnEmptyExpr) {
                    retain.add(((OnEmptyExpr) getChildExpression(c)).getBaseExpression());
                }
            }
            return Block.makeBlock(retain);
        }
        return this;
    }


    /**
     * Check that any elements and attributes constructed or returned by this expression are acceptable
     * in the content model of a given complex type. It's always OK to say yes, since the check will be
     * repeated at run-time. The process of checking element and attribute constructors against the content
     * model of a complex type also registers the type of content expected of those constructors, so the
     * static validation can continue recursively.
     */

    @Override
    public void checkPermittedContents(SchemaType parentType, boolean whole) throws XPathException {
        for (Operand o : operands()) {
            Expression child = o.getChildExpression();
            child.checkPermittedContents(parentType, false);
        }
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("condSeq", this);
        for (Operand o : operands()) {
            Expression child = o.getChildExpression();
            child.export(out);
        }
        out.endElement();
    }

    @Override
    public String toShortString() {
        return "(" + getChildExpression(0).toShortString() + ", ...)";
    }



    @Override
    @SuppressWarnings("StatementWithEmptyBody")
    public TailCall processLeavingTail(Outputter output, final XPathContext context) throws XPathException {

        final List<OnNonEmptyExpr> onNonEmptyPending = new ArrayList<>();

        Action action = () -> {
            for (Expression e : onNonEmptyPending) {
                e.process(output, context);
            }
        };

        SignificantItemDetector significantItemDetector = new SignificantItemDetector(output, action);

        for (Operand o : operands()) {
            Expression child = o.getChildExpression();
            try {
                if (child instanceof OnEmptyExpr) {
                    // Ignore on-empty instructions until the end
                } else if (child instanceof OnNonEmptyExpr) {
                    if (significantItemDetector.isEmpty()) {
                        onNonEmptyPending.add((OnNonEmptyExpr)child);
                    } else {
                        child.process(output, context);
                    }
                } else {
                    child.process(significantItemDetector, context);
                }

            } catch (XPathException e) {
                e.maybeSetLocation(child.getLocation());
                e.maybeSetContext(context);
                throw e;
            }
        }

        // At the end, if the content produced until now is empty, process the on-empty instructions
        if (significantItemDetector.isEmpty()) {
            for (Operand o : operands()) {
                Expression child = o.getChildExpression();
                if (child instanceof OnEmptyExpr) {
                    child.process(output, context);
                }
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

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "ConditionalBlock";
    }
}
