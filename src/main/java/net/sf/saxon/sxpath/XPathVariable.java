////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.sxpath;

import net.sf.saxon.expr.LocalBinding;
import net.sf.saxon.expr.VariableReference;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;


/**
 * An object representing an XPath variable for use in the standalone XPath API. The object
 * can only be created by calling the declareVariable method of class {@link IndependentContext}.
 * Note that once declared, this object is thread-safe: it does not hold the actual variable
 * value, which means it can be used with any number of evaluations of a given XPath expression,
 * in series or in parallel.
 * <p>A variable can be given a value by calling
 * {@link XPathDynamicContext#setVariable(XPathVariable, net.sf.saxon.om.Sequence)}.
 * Note that the value of the variable is not held in the XPathVariable object, but in the
 * XPathDynamicContext, which means that the XPathVariable itself can be used in multiple threads.</p>
 */

public final class XPathVariable implements LocalBinding {

    private StructuredQName name;
    private SequenceType requiredType = SequenceType.ANY_SEQUENCE;
    private Sequence defaultValue;
    private int slotNumber;

    /**
     * Private constructor: for use only by the protected factory method make()
     */

    private XPathVariable() {
    }

    /**
     * Factory method, for use by the declareVariable method of class IndependentContext
     *
     * @param name the name of the variable to create
     * @return the constructed XPathVariable
     */

    protected static XPathVariable make(StructuredQName name) {
        XPathVariable v = new XPathVariable();
        v.name = name;
        return v;
    }

    /**
     * Ask whether the binding is local or global. A global binding is one that has a fixed
     * value for the life of a query or transformation; any other binding is local. An XPath
     * variable is treated as a local variable (largely because it is held on the stack frame)
     *
     * @return false (always)
     */

    @Override
    public boolean isGlobal() {
        return false;
    }

    /**
     * Test whether it is permitted to assign to the variable using the saxon:assign
     * extension element. This will only be for an XSLT global variable where the extra
     * attribute saxon:assignable="yes" is present.
     *
     * @return false (always)
     */

    @Override
    public final boolean isAssignable() {
        return false;
    }

    /**
     * Set the required type of this variable. If no required type is specified,
     * the type <code>item()*</code> is assumed.
     *
     * @param requiredType the required type
     */

    public void setRequiredType(SequenceType requiredType) {
        this.requiredType = requiredType;
    }

    /**
     * Get the required type of this variable. If no required type has been specified,
     * the type <code>item()*</code> is returned.
     *
     * @return the required type of the variable
     */

    @Override
    public SequenceType getRequiredType() {
        return requiredType;
    }

    /**
     * If the variable is bound to an integer, get the minimum and maximum possible values.
     * Return null if unknown or not applicable
     */
    /*@Nullable*/
    @Override
    public IntegerValue[] getIntegerBoundsForVariable() {
        return null;
    }

    /**
     * Set the slot number allocated to this variable. This method is for internal use.
     *
     * @param slotNumber the slot number to be allocated
     */

    public void setSlotNumber(int slotNumber) {
        this.slotNumber = slotNumber;
    }

    /**
     * If this is a local variable held on the local stack frame, return the corresponding slot number.
     * In other cases, return -1.
     */

    @Override
    public int getLocalSlotNumber() {
        return slotNumber;
    }

    /**
     * Get the name of the variable as a QNameValue.
     *
     * @return the name of the variable, as a QNameValue
     */

    @Override
    public StructuredQName getVariableQName() {
        return name;
    }

    /**
     * Method called by the XPath expression parser to register a reference to this variable.
     * This method should not be called by users of the API.
     */

    @Override
    public void addReference(VariableReference ref, boolean isLoopingReference) {
        // no action
    }

    /**
     * Set a default value for the variable, to be used if no specific value is
     * supplied when the expression is evaluated
     *
     * @param defaultValue the default value for the variable
     */

    public void setDefaultValue(Sequence defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Get the default value of the variable
     *
     * @return the default value if one has been registered, or null otherwise
     */

    public Sequence getDefaultValue() {
        return defaultValue;
    }

    /**
     * Get the value of the variable. This method is used by the XPath execution engine
     * to retrieve the value. Note that the value is not held within the variable itself,
     * but within the dynamic context.
     *
     * @param context The dynamic evaluation context
     * @return The value of the variable
     */

    @Override
    public Sequence evaluateVariable(XPathContext context) {
        return context.evaluateLocalVariable(slotNumber);
    }

    /**
     * Say that the bound value has the potential to be indexed
     */
    @Override
    public void setIndexedVariable() { }

    /**
     * Ask whether the binding is to be indexed
     *
     * @return true if the variable value can be indexed
     */
    @Override
    public boolean isIndexedVariable() {
        return false;
    }
}

