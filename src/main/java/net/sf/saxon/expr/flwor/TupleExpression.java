////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.oper.OperandArray;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;

import java.util.ArrayList;
import java.util.List;

/**
 * A tuple expression is an expression that returns a tuple. Specifically,
 * it is a list local variables references; it returns a Tuple item
 * containing the current value of these variables.
 */
public class TupleExpression extends Expression {

    private OperandArray operanda;

    public TupleExpression() {
    }

    /**
      * Set the data structure for the operands of this expression. This must be created during initialisation of the
      * expression and must not be subsequently changed
      * @param operanda the data structure for expression operands
      */

     protected void setOperanda(OperandArray operanda) {
         this.operanda = operanda;
     }

     /**
      * Get the data structure holding the operands of this expression.
      * @return the data structure holding expression operands
      */

     protected OperandArray getOperanda() {
         return operanda;
     }

     @Override
     public Iterable<Operand> operands() {
         return operanda.operands();
     }


    public void setVariables(List<LocalVariableReference> refs) {
        Expression[] e = new Expression[refs.size()];
        for (int i=0; i<refs.size(); i++) {
            e[i] = refs.get(i);
        }
        setOperanda(new OperandArray(this, e, OperandRole.SAME_FOCUS_ACTION));
    }

    public int getSize() {
        return getOperanda().getNumberOfOperands();
    }

    public LocalVariableReference getSlot(int i) {
        return (LocalVariableReference)getOperanda().getOperandExpression(i);
    }

    public void setSlot(int i, LocalVariableReference ref) {
        getOperanda().setOperand(i, ref);
    }

    public boolean includesBinding(Binding binding) {
        for (Operand o : operands()) {
            if (((LocalVariableReference)o.getChildExpression()).getBinding() == binding) {
                return true;
            }
        }
        return false;
    }

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return getConfiguration().getJavaExternalObjectType(Object.class);
    }

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor,
                                /*@Nullable*/ ContextItemStaticInfo contextInfo)
            throws XPathException {
        for (int i=0; i<getSize(); i++) {
            operanda.getOperand(i).typeCheck(visitor, contextInfo);
        }
        return this;
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
        return EVALUATE_METHOD;
    }

    /**
     * Is this expression the same as another expression?
     *
     * @param other the expression to be compared with this one
     * @return true if the two expressions are statically equivalent
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TupleExpression)) {
            return false;
        } else {
            TupleExpression t2 = (TupleExpression)other;
            if (getOperanda().getNumberOfOperands() != t2.getOperanda().getNumberOfOperands()) {
                return false;
            }
            for (int i=0; i<getSize(); i++) {
                if (!getSlot(i).isEqual(t2.getSlot(i))) {
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
        int h = 77;
        for (Operand o : operands()) {
            h ^= o.getChildExpression().hashCode();
        }
        return h;
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
        int n  = getOperanda().getNumberOfOperands();
        List<LocalVariableReference> refs2 = new ArrayList<LocalVariableReference>(n);
        for (int i = 0; i < n; i++) {
            refs2.add ((LocalVariableReference) getSlot(i).copy(rebindings));
        }
        TupleExpression t2 = new TupleExpression();
        ExpressionTool.copyLocationInfo(this, t2);
        t2.setVariables(refs2);
        return t2;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("tuple", this);
        for (Operand o : operands()) {
            o.getChildExpression().export(out);
        }
        out.endElement();
    }


    @Override
    public Tuple evaluateItem(XPathContext context) throws XPathException {
        final int n = getSize();
        Sequence[] tuple = new Sequence[n];
        for (int i=0; i<n; i++) {
            tuple[i] = getSlot(i).evaluateVariable(context);
        }
        return new Tuple(tuple);
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in export() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "tuple";
    }


    /**
     * Set the local variables in the current stack frame to values corresponding to a supplied tuple
     *
     * @param context identifies the stack frame to be modified
     * @param tuple   the tuple containing the current values
     */

    public void setCurrentTuple(XPathContext context, Tuple tuple) throws XPathException {
        Sequence[] members = tuple.getMembers();
        int n = getSize();
        for (int i = 0; i < n; i++) {
            context.setLocalVariable(getSlot(i).getBinding().getLocalSlotNumber(), members[i]);
        }
    }

    /**
     * Get the cardinality of the expression. This is exactly one, in the sense
     * that evaluating the TupleExpression returns a single tuple.
     *
     * @return the static cardinality - EXACTLY_ONE
     */

    @Override
    public int computeCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    @Override
    public int getIntrinsicDependencies() {
        return 0;
    }

}

