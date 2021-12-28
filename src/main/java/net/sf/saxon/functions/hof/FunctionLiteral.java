////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.AbstractFunction;
import net.sf.saxon.om.Function;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;

/**
 * A FunctionLiteral is a wrapper around a FunctionItem; it is an expression, whose value is the function
 * that it wraps. Note that a FunctionLiteral can be used only where the binding to a specific function is
 * statically known. This works for constructor functions, for system functions that have no context
 * dependency, and for references to user function (my:f#2) in XQuery, but not in XSLT where the reference
 * cannot be fully resolved until separately-compiled packages are linked. In other cases a
 * {@link UserFunctionReference} is used.
 */

public class FunctionLiteral extends Literal {

    /**
     * Create a literal as a wrapper around a Value
     *
     * @param value     the value of this literal
     */

    public FunctionLiteral(Function value) {
        super(value);
    }

    /**
     * Get the value represented by this Literal
     *
     * @return the constant value
     */

    @Override
    public Function getValue() {
        return (Function) super.getValue();
    }


    /**
     * Simplify an expression
     *
     *
     *
     * @return for a Value, this always returns the value unchanged
     */

    /*@NotNull*/
    @Override
    public Expression simplify() throws XPathException {
        if (getValue() instanceof AbstractFunction) {
            ((AbstractFunction) getValue()).simplify();
        }
        return this;
    }

    /**
     * TypeCheck an expression
     *
     * @return for a Value, this always returns the value unchanged
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        if (getValue() instanceof AbstractFunction) {
            ((AbstractFunction) getValue()).typeCheck(visitor, contextInfo);
        }
        return this;
    }

    /**
     * Determine the data type of the items in the expression, if possible
     *
     * @return for the default implementation: AnyItemType (not known)
     */

    /*@NotNull*/
    @Override
    public FunctionItemType getItemType() {
        return getValue().getFunctionItemType();
    }

    /**
     * Determine the cardinality
     */

    @Override
    public int computeCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /**
     * Compute the static properties of this expression (other than its type). For a
     * Value, the only special property is {@link net.sf.saxon.expr.StaticProperty#NO_NODES_NEWLY_CREATED}.
     *
     * @return the value {@link net.sf.saxon.expr.StaticProperty#NO_NODES_NEWLY_CREATED}
     */


    @Override
    public int computeSpecialProperties() {
        return StaticProperty.NO_NODES_NEWLY_CREATED;
    }

    /**
     * Determine whether this is a vacuous expression as defined in the XQuery update specification
     *
     * @return true if this expression is vacuous
     */

    @Override
    public boolean isVacuousExpression() {
        return false;
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
        FunctionLiteral fl2 = new FunctionLiteral(getValue());
        ExpressionTool.copyLocationInfo(this, fl2);
        return fl2;
    }

    /**
     * Set the retained static context
     *
     * @param rsc the static context to be retained
     */
    @Override
    public void setRetainedStaticContext(RetainedStaticContext rsc) {
        super.setRetainedStaticContext(rsc);
    }

    /**
     * Determine whether two literals are equal, when considered as expressions.
     *
     * @param obj the other expression
     * @return true if the two literals are equal. The test here requires (a) identity in the
     *         sense defined by XML Schema (same value in the same value space), and (b) identical type
     *         annotations. For example the literal xs:int(3) is not equal (as an expression) to xs:short(3),
     *         because the two expressions are not interchangeable.
     */

    public boolean equals(Object obj) {
        return obj instanceof FunctionLiteral && ((FunctionLiteral) obj).getValue() == getValue();

    }

    /**
     * Return a hash code to support the equals() function
     */

    @Override
    public int computeHashCode() {
        return getValue().hashCode();
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
        return "namedFunctionRef";
    }


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        Function f = getValue();
        if (f instanceof UserFunction) {
            new UserFunctionReference((UserFunction) f).export(out);
        } else {
            f.export(out);
        }
    }

}

// Copyright (c) 2009-2020 Saxonica Limited
