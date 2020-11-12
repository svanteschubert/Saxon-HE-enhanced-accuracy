////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.arrays;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.oper.OperandArray;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;


/**
 * An expression that delivers a fixed size array whose members are the result of evaluating
 * corresponding expressions: [a,b,c,d]
 */

public class SquareArrayConstructor extends Expression {


    private OperandArray operanda;


    /**
     * Create an array constructor with defined members
     * @param children expressions defining the members (comma-separated subexpressions)
     */

    public SquareArrayConstructor(List<Expression> children) {
        Expression[] kids = children.toArray(new Expression[0]);
        for (Expression e : children) {
            adoptChildExpression(e);
        }
        setOperanda(new OperandArray(this, kids, OperandRole.NAVIGATE));
    }

    /**
     * Set the data structure for the operands of this expression. This must be created during initialisation of the
     * expression and must not be subsequently changed
     *
     * @param operanda the data structure for expression operands
     */

    protected void setOperanda(OperandArray operanda) {
        this.operanda = operanda;
    }

    /**
     * Get the data structure holding the operands of this expression.
     *
     * @return the data structure holding expression operands
     */

    public OperandArray getOperanda() {
        return operanda;
    }

    public Operand getOperand(int i) {
        return operanda.getOperand(i);
    }

    @Override
    public Iterable<Operand> operands() {
        return operanda.operands();
    }


    @Override
    public String getExpressionName() {
        return "SquareArrayConstructor";
    }

    @Override
    public String getStreamerName() {
        return "ArrayBlock";
    }


    @Override
    public int computeSpecialProperties() {
        return 0;
    }

    /**
     * Is this expression the same as another expression?
     *
     * @param other the expression to be compared with this one
     * @return true if the two expressions are statically equivalent
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SquareArrayConstructor)) {
            return false;
        } else {
            SquareArrayConstructor ab2 = (SquareArrayConstructor) other;
            if (ab2.getOperanda().getNumberOfOperands() != getOperanda().getNumberOfOperands()) {
                return false;
            }
            for (int i = 0; i < getOperanda().getNumberOfOperands(); i++) {
                if (!getOperanda().getOperand(i).equals(ab2.getOperanda().getOperand(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Hashcode supporting equals()
     */

    @Override
    public int computeHashCode() {
        int h = 0x878b92a0;
        for (Operand o : operands()) {
            h ^= o.getChildExpression().hashCode();
        }
        return h;
    }

    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        Expression e = super.typeCheck(visitor, contextInfo);
        if (e != this) {
            return e;
        }
        return preEvaluate(visitor);
    }

    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        Expression e = super.optimize(visitor, contextInfo);
        if (e != this) {
            return e;
        }
        return preEvaluate(visitor);
    }

    private Expression preEvaluate(ExpressionVisitor visitor) {
        boolean allFixed = false;
        for (Operand o : operands()) {
            if (!(o.getChildExpression() instanceof Literal)) {
                return this;
            }
        }
        try {
            return Literal.makeLiteral(evaluateItem(visitor.makeDynamicContext()), this);
        } catch (XPathException e) {
            return this;
        }
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
        List<Expression> m2 = new ArrayList<>(getOperanda().getNumberOfOperands());
        for (Operand o : operands()) {
            m2.add(o.getChildExpression().copy(rebindings));
        }
        SquareArrayConstructor b2 = new SquareArrayConstructor(m2);
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
        ItemType contentType = null;
        int contentCardinality = StaticProperty.EXACTLY_ONE;
        TypeHierarchy th = getConfiguration().getTypeHierarchy();
        for (Expression e : getOperanda().operandExpressions()) {
            if (contentType == null) {
                contentType = e.getItemType();
                contentCardinality = e.getCardinality();
            } else {
                contentType = Type.getCommonSuperType(contentType, e.getItemType(), th);
                contentCardinality = Cardinality.union(contentCardinality, e.getCardinality());
            }
        }
        if (contentType == null) {
            contentType = ErrorType.getInstance();
        }
        return new ArrayItemType(SequenceType.makeSequenceType(contentType, contentCardinality));
    }

    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @param contextItemType the static type of the context item
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        return UType.FUNCTION;
    }

    /**
     * Determine the cardinality of the expression
     */

    @Override
    public final int computeCardinality() {
        // An array is an item!
        return StaticProperty.EXACTLY_ONE;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("arrayBlock", this);
        for (Operand o : operands()) {
            o.getChildExpression().export(out);
        }
        out.endElement();
    }

    @Override
    public String toShortString() {
        int n = getOperanda().getNumberOfOperands();
        switch (n) {
            case 0:
                return "[]";
            case 1:
                return "[" + getOperanda().getOperand(0).getChildExpression().toShortString() + "]";
            case 2:
                return "[" + getOperanda().getOperand(0).getChildExpression().toShortString() + ", " +
                        getOperanda().getOperand(1).getChildExpression().toShortString() + "]";
            default:
                return "[" + getOperanda().getOperand(0).getChildExpression().toShortString() + ", ...]";
        }
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides both iterate() and
     * process() methods natively.
     */

    @Override
    public int getImplementationMethod() {
        return EVALUATE_METHOD;
    }

    /**
     * Evaluate an expression as a single item. This always returns either a single Item or
     * null (denoting the empty sequence). No conversion is done. This method should not be
     * used unless the static type of the expression is a subtype of "item" or "item?": that is,
     * it should not be called if the expression may return a sequence. There is no guarantee that
     * this condition will be detected.
     *
     * @param context The context in which the expression is to be evaluated
     * @return the node or atomic value that results from evaluating the
     * expression; or null to indicate that the result is an empty
     * sequence
     * @throws net.sf.saxon.trans.XPathException if any dynamic error occurs evaluating the
     *                                           expression
     */
    @Override
    public Item evaluateItem(XPathContext context) throws XPathException {
        List<GroundedValue> value = new ArrayList<>(getOperanda().getNumberOfOperands());
        for (Operand o : operands()) {
            GroundedValue s = ExpressionTool.eagerEvaluate(o.getChildExpression(), context);
            value.add(s);
        }
        return new SimpleArrayItem(value);
    }

}
