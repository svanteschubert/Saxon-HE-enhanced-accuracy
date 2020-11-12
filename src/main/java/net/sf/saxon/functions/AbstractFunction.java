////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.ContextOriginator;
import net.sf.saxon.expr.OperandRole;
import net.sf.saxon.expr.OperandUsage;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.Function;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;

import java.util.Arrays;

/**
 * Abstract superclass (and factory class) for implementations of Function
 */
public abstract class AbstractFunction implements Function {

    /**
     * Get the roles of the arguments, for the purposes of streaming
     *
     * @return an array of OperandRole objects, one for each argument
     */
    @Override
    public OperandRole[] getOperandRoles() {
        OperandRole[] roles = new OperandRole[getArity()];
        Arrays.fill(roles, new OperandRole(0, OperandUsage.NAVIGATION));
        return roles;
    }

    /**
     * Atomize the item.
     *
     * @return the result of atomization
     * @throws net.sf.saxon.trans.XPathException
     *          if atomization is not allowed for this kind of item
     */
    @Override
    public AtomicSequence atomize() throws XPathException {
        throw new XPathException("Function items (other than arrays) cannot be atomized", "FOTY0013");
    }

    /**
     * Ask whether this function is an array
     *
     * @return true if this function item is an array, otherwise false
     */
    @Override
    public boolean isArray() {
        return false;
    }

    /**
     * Ask whether this function is a map
     *
     * @return true if this function item is a map, otherwise false
     */
    @Override
    public boolean isMap() {
        return false;
    }

    /**
     * Get the string value of the function
     *
     * @throws UnsupportedOperationException (the string value of a function is not defined)
     */

    @Override
    public String getStringValue() {
        throw new UnsupportedOperationException("The string value of a function is not defined");
    }

    /**
     * Get the string value of the function
     *
     * @throws UnsupportedOperationException (the string value of a function is not defined)
     */

    @Override
    public CharSequence getStringValueCS() {
        throw new UnsupportedOperationException("The string value of a function is not defined");
    }

    @Override
    public AnnotationList getAnnotations() {
        return AnnotationList.EMPTY;
    }

    /**
     * Get the effective boolean value of the function item
     *
     * @throws XPathException (the EBVof a function item is not defined)
     */

    @Override
    public boolean effectiveBooleanValue() throws XPathException {
        throw new XPathException("A function has no effective boolean value", "XPTY0004");
    }



    public void simplify() throws XPathException {
        // default: no action
    }

    /**
     * Type check the function (may modify it by adding code for converting the arguments)
     *
     * @param visitor         the expression visitor, supplies context information
     * @param contextItemType the context item type at the point where the function definition appears
     * @throws XPathException if any failure (e.g. a type checking failure) occurs
     */

    public void typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        // default: no action
    }

    /**
     * Prepare an XPathContext object for evaluating the function
     *
     * @param callingContext the XPathContext of the function calling expression
     * @param originator
     * @return a suitable context for evaluating the function (which may or may
     * not be the same as the caller's context)
     */

    @Override
    public XPathContext makeNewContext(XPathContext callingContext, ContextOriginator originator) {
        return callingContext;
    }

    /**
     * Test whether this FunctionItem is deep-equal to another function item,
     * under the rules of the deep-equal function
     *
     * @param other the other function item
     */
    @Override
    public boolean deepEquals(Function other, XPathContext context, AtomicComparer comparer, int flags) throws XPathException {
        throw new XPathException("Argument to deep-equal() contains a function item", "FOTY0015");
    }

    /**
     * Output information about this function item to the diagnostic explain() output
     */
    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        throw new UnsupportedOperationException("export() not implemented for " + this.getClass());
    }

    /**
     * Check that result type is SystemFunction or AtomicConstructorFunction
     *
     */
    @Override
    public boolean isTrustedResultType() {
        return false;
    }
}


