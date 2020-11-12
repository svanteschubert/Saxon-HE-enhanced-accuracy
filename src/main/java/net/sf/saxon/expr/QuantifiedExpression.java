////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.BooleanFn;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.SequenceType;

/**
 * A QuantifiedExpression tests whether some/all items in a sequence satisfy
 * some condition.
 */

public class QuantifiedExpression extends Assignation {

    private int operator;       // Token.SOME or Token.EVERY

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */

    @Override
    public String getExpressionName() {
        return Token.tokens[operator];
    }

    /**
     * Set the operator, either {@link Token#SOME} or {@link Token#EVERY}
     *
     * @param operator the operator
     */

    public void setOperator(int operator) {
        this.operator = operator;
    }

    /**
     * Get the operator, either {@link Token#SOME} or {@link Token#EVERY}
     *
     * @return the operator
     */

    public int getOperator() {
        return operator;
    }

    /**
     * Determine the static cardinality
     */

    @Override
    public int computeCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        // The order of events is critical here. First we ensure that the type of the
        // sequence expression is established. This is used to establish the type of the variable,
        // which in turn is required when type-checking the action part.

        getSequenceOp().typeCheck(visitor, contextInfo);

        if (Literal.isEmptySequence(getSequence())) {
            return Literal.makeLiteral(BooleanValue.get(operator != Token.SOME), this);
        }

        // "some" and "every" have no ordering constraints

        setSequence(getSequence().unordered(false, false));

        SequenceType decl = getRequiredType();
        if (decl.getCardinality() == StaticProperty.ALLOWS_ZERO) {
            XPathException err = new XPathException("Range variable will never satisfy the type empty-sequence()", "XPTY0004");
            err.setIsTypeError(true);
            err.setLocation(getLocation());
            throw err;
        }
        SequenceType sequenceType = SequenceType.makeSequenceType(decl.getPrimaryType(),
                StaticProperty.ALLOWS_ZERO_OR_MORE);
        RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.VARIABLE, getVariableQName().getDisplayName(), 0);
        //role.setSourceLocator(this);
        setSequence(TypeChecker.strictTypeCheck(
                getSequence(), sequenceType, role, visitor.getStaticContext()));
        ItemType actualItemType = getSequence().getItemType();
        refineTypeInformation(actualItemType,
                              StaticProperty.EXACTLY_ONE,
                              null,
                              getSequence().getSpecialProperties(), this);

        //declaration = null;     // let the garbage collector take it

        getActionOp().typeCheck(visitor, contextInfo);
        XPathException err = TypeChecker.ebvError(getAction(), visitor.getConfiguration().getTypeHierarchy());
        if (err != null) {
            err.setLocation(getLocation());
            throw err;
        }
        return this;
    }

    /**
     * Perform optimisation of an expression and its subexpressions.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor         an expression visitor
     * @param contextItemType the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {

        getSequenceOp().optimize(visitor, contextItemType);
        getActionOp().optimize(visitor, contextItemType);
        Expression ebv = BooleanFn.rewriteEffectiveBooleanValue(getAction(), visitor, contextItemType);
        if (ebv != null) {
            setAction(ebv);
            adoptChildExpression(ebv);
        }
        if (Literal.hasEffectiveBooleanValue(ebv, true)) {
            // some $x satisfies true() => exists($x)
            // every $x satisfies true() => true()
            if (getOperator() == Token.SOME) {
                return SystemFunction.makeCall("exists", getRetainedStaticContext(), getSequence());
            } else {
                Expression e2 = new Literal(BooleanValue.TRUE);
                ExpressionTool.copyLocationInfo(this, e2);
                return e2;
            }
        } else if (Literal.hasEffectiveBooleanValue(ebv, false)) {
            // some $x satisfies false() => false()
            // every $x satisfies false() => empty($x)
            if (getOperator() == Token.SOME) {
                Expression e2 = new Literal(BooleanValue.FALSE);
                ExpressionTool.copyLocationInfo(this, e2);
                return e2;
            } else {
                return SystemFunction.makeCall("empty", getRetainedStaticContext(), getSequence());
            }
        }
        if (getSequence() instanceof Literal) {
            GroundedValue seq = ((Literal)getSequence()).getValue();
            int len = seq.getLength();
            if (len == 0) {
                Expression e2 = new Literal(BooleanValue.get(getOperator() == Token.EVERY));
                ExpressionTool.copyLocationInfo(this, e2);
                return e2;
            } else if (len == 1) {
                if (getAction() instanceof VariableReference && ((VariableReference) getAction()).getBinding() == this) {
                    return SystemFunction.makeCall("boolean", getRetainedStaticContext(), getSequence());
                } else {
                    replaceVariable(getSequence());
                    return getAction();
                }
            }
        }

        // if streaming, convert to an expression that can be streamed

        if (visitor.isOptimizeForStreaming()) {
            Expression e3 = visitor.obtainOptimizer().optimizeQuantifiedExpressionForStreaming(this);
            if (e3 != null && e3 != this) {
                return e3.optimize(visitor, contextItemType);
            }
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
     * Check to ensure that this expression does not contain any updating subexpressions.
     * This check is overridden for those expressions that permit updating subexpressions.
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if the expression has a non-permitted updateing subexpression
     */

    @Override
    public void checkForUpdatingSubexpressions() throws XPathException {
        getSequence().checkForUpdatingSubexpressions();
        getAction().checkForUpdatingSubexpressions();
    }

    /**
     * Determine whether this is an updating expression as defined in the XQuery update specification
     *
     * @return true if this is an updating expression
     */

    @Override
    public boolean isUpdatingExpression() {
        return false;
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
        QuantifiedExpression qe = new QuantifiedExpression();
        ExpressionTool.copyLocationInfo(this, qe);
        qe.setOperator(operator);
        qe.setVariableQName(variableName);
        qe.setRequiredType(requiredType);
        qe.setSequence(getSequence().copy(rebindings));
        rebindings.put(this, qe);
        Expression newAction = getAction().copy(rebindings);
        qe.setAction(newAction);
        qe.variableName = variableName;
        qe.slotNumber = slotNumber;
        return qe;
    }


    /**
     * Determine the special properties of this expression
     *
     * @return {@link StaticProperty#NO_NODES_NEWLY_CREATED}.
     */

    @Override
    public int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        return p | StaticProperty.NO_NODES_NEWLY_CREATED;
    }

    /**
     * Evaluate the expression to return a singleton value
     */

    @Override
    public BooleanValue evaluateItem(XPathContext context) throws XPathException {
        return BooleanValue.get(effectiveBooleanValue(context));
    }

    /**
     * Get the result as a boolean
     */

    @Override
    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {

        // First create an iteration of the base sequence.

        SequenceIterator base = getSequence().iterate(context);

        // Now test to see if some or all of the tests are true. The same
        // logic is used for the SOME and EVERY operators

        final boolean some = operator == Token.SOME;
        int slot = getLocalSlotNumber();
        Item it;
        while ((it = base.next()) != null) {
            context.setLocalVariable(slot, it);
            if (some == getAction().effectiveBooleanValue(context)) {
                base.close();
                return some;
            }
        }
        return !some;
    }


    /**
     * Determine the data type of the items returned by the expression
     *
     * @return Type.BOOLEAN
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return BuiltInAtomicType.BOOLEAN;
    }

    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     * @param contextItemType the static type of the context item
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        return UType.BOOLEAN;
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     *
     * @return a representation of the expression as a string
     */

    public String toString() {
        return (operator == Token.SOME ? "some" : "every") + " $" + getVariableEQName() +
                " in " + getSequence() + " satisfies " +
                ExpressionTool.parenthesize(getAction());
    }

    @Override
    public String toShortString() {
        return (operator == Token.SOME ? "some" : "every") + " $" + getVariableName() +
                " in " + getSequence().toShortString() + " satisfies ...";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement(Token.tokens[operator], this);
        out.emitAttribute("var", getVariableQName());
        out.emitAttribute("slot", ""+slotNumber);
        getSequence().export(out);
        getAction().export(out);
        out.endElement();
    }



}

