////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.LocalBinding;
import net.sf.saxon.expr.VariableReference;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.FunctionStreamability;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

/**
 * Run-time object representing a formal argument to a user-defined function
 */
public class UserFunctionParameter implements LocalBinding {

    private SequenceType requiredType;
    private StructuredQName variableQName;
    private int slotNumber;
    private int referenceCount = 999;
    // The initial value is deliberately set to indicate "many" so that it will be assumed a parameter
    // is referenced repeatedly until proved otherwise
    private boolean isIndexed = false;
    private FunctionStreamability functionStreamability = FunctionStreamability.UNCLASSIFIED;

    /**
     * Create a UserFunctionParameter
     */

    public UserFunctionParameter() {
    }

    /**
     * Indicate whether the binding is local or global. A global binding is one that has a fixed
     * value for the life of a query or transformation; any other binding is local.
     *
     * @return false (always)
     */

    @Override
    public final boolean isGlobal() {
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
     * Set the slot number to be used by this parameter
     *
     * @param slot the slot number, that is, the position of the parameter value within the local stack frame
     */

    public void setSlotNumber(int slot) {
        slotNumber = slot;
    }

    /**
     * If this is a local variable held on the local stack frame, return the corresponding slot number.
     * In other cases, return -1.
     *
     * @return the slot number, indicating the position of the parameter on the local stack frame
     */

    @Override
    public int getLocalSlotNumber() {
        return slotNumber;
    }

    /**
     * Set the required type of this function parameter
     *
     * @param type the declared type of the parameter
     */

    public void setRequiredType(SequenceType type) {
        requiredType = type;
    }

    /**
     * Get the required type of this function parameter
     *
     * @return the declared type of the parameter
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
     * Set the name of this parameter
     *
     * @param name the name of the parameter
     */

    public void setVariableQName(StructuredQName name) {
        variableQName = name;
    }

    /**
     * Get the name of this parameter
     *
     * @return the name of this parameter
     */

    @Override
    public StructuredQName getVariableQName() {
        return variableQName;
    }

    @Override
    public void addReference(VariableReference ref, boolean isLoopingReference) {

    }

//    /**
//     * Set the (nominal) number of references within the function body to this parameter, where a reference
//     * inside a loop is counted as multiple references
//     *
//     * @param count the nominal number of references
//     */
//
//    public void setReferenceCount(int count) {
//        referenceCount = count;
//    }

    /**
     * Get the (nominal) number of references within the function body to this parameter, where a reference
     * inside a loop is counted as multiple references
     *
     * @return the nominal number of references
     */

    public int getReferenceCount() {
        return referenceCount;
    }

    /**
     * Indicate that this parameter requires (or does not require) support for indexing
     *
     * @param indexed true if support for indexing is required. This will be set if the parameter
     *                is used in a filter expression such as $param[@a = 17]
     */

    public void setIndexedVariable(boolean indexed) {
        isIndexed = indexed;
    }

    @Override
    public void setIndexedVariable() {
        setIndexedVariable(true);
    }

    /**
     * Ask whether this parameter requires support for indexing
     *
     * @return true if support for indexing is required. This will be set if the parameter
     *         is used in a filter expression such as $param[@a = 17]
     */

    @Override
    public boolean isIndexedVariable() {
        return isIndexed;
    }

    /**
     * Evaluate this function parameter
     *
     * @param context the XPath dynamic context
     * @return the value of the parameter
     */

    @Override
    public Sequence evaluateVariable(XPathContext context) {
        return context.evaluateLocalVariable(slotNumber);
    }

    /**
     * If this is the first argument of a streamable stylesheet function,
     * set the streamability category
     * @param ability the streamability category
     */

    public void setFunctionStreamability(FunctionStreamability ability) {
        this.functionStreamability = ability;
    }

    /**
     * If this is the first argument of a streamable stylesheet function,
     * get the streamability category; otherwise return {@link FunctionStreamability#UNCLASSIFIED}
     * @return the streamability category
     */

    public FunctionStreamability getFunctionStreamability() {
        return functionStreamability;
    }


}

