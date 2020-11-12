////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.value.SequenceType;

/**
 * BindingReference is a interface used to mark references to a variable declaration. The main
 * implementation is VariableReference, which represents a reference to a variable in an XPath
 * expression, but it is also used to represent a reference to a variable in a saxon:assign instruction.
 */

public interface BindingReference {

    /**
     * Fix up the static type of this variable reference; optionally, supply a constant value for
     * the variable. Also supplies other static properties of the expression to which the variable
     * is bound, for example whether it is an ordered node-set.
     *  @param type          The static type of the variable reference, typically either the declared type
     *                      of the variable, or the static type of the expression to which the variable is bound
     * @param constantValue if non-null, indicates that the value of the variable is known at compile
     *                      time, and supplies the value
     * @param properties    static properties of the expression to which the variable is bound
     */

    void setStaticType(SequenceType type, GroundedValue constantValue, int properties);

    /**
     * Fix up this binding reference to a binding
     *
     * @param binding the Binding to which the variable refers
     */

    void fixup(Binding binding);

}

