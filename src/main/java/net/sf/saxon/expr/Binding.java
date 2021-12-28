////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

/**
 * Binding is a interface used to represent the run-time properties and methods
 * associated with a variable: specifically, a method to get the value
 * of the variable.
 */

public interface Binding {

    /**
     * Get the declared type of the variable
     *
     * @return the declared type
     */

    SequenceType getRequiredType();

    /**
     * If the variable is bound to an integer, get the minimum and maximum possible values.
     * Return null if unknown or not applicable
     *
     * @return a pair of integers containing the minimum and maximum values for the integer value;
     *         or null if the value is not an integer or the range is unknown
     */

    /*@Nullable*/
    IntegerValue[] getIntegerBoundsForVariable();

    /**
     * Evaluate the variable
     *
     * @param context the XPath dynamic evaluation context
     * @return the result of evaluating the variable
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs while evaluating
     *          the variable
     */

    Sequence evaluateVariable(XPathContext context) throws XPathException;

    /**
     * Indicate whether the binding is local or global. A global binding is one that has a fixed
     * value for the life of a query or transformation; any other binding is local.
     *
     * @return true if the binding is global
     */

    boolean isGlobal();

    /**
     * Test whether it is permitted to assign to the variable using the saxon:assign
     * extension element. This will only be for an XSLT global variable where the extra
     * attribute saxon:assignable="yes" is present.
     *
     * @return true if the binding is assignable
     */

    boolean isAssignable();

    /**
     * Get the name of the variable
     *
     * @return the name of the variable, as a structured QName
     */

    StructuredQName getVariableQName();

    /**
     * Register a variable reference that refers to the variable bound in this expression
     *
     * @param ref the variable reference
     * @param isLoopingReference - true if the reference occurs within a loop, such as the predicate
     *                           of a filter expression
     */

    void addReference(VariableReference ref, boolean isLoopingReference);

}

