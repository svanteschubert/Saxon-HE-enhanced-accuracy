////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.trans.SymbolicName;

/**
 * Represents an expression or instruction such as call-template, or a user function call, or
 * a global variable reference, that needs to be bound to a target component, and can potentially
 * be re-bound when the containing component is copied into another package.
 */
public interface ComponentInvocation {

    /**
     * Get the target component if this is known in advance, that is, if the target component
     * is private or final, or in some other cases such as xsl:original. Otherwise, return null.
     * @return the bound component if the binding has been fixed
     */

    Component getFixedTarget();

    /**
     * Set the binding slot to be used. This is the offset within the binding vector of the containing
     * component where the actual target component is to be found. The target template is not held directly
     * in the invocation instruction/expression itself because it can be overridden in a using package.
     *
     * @param slot the offset in the binding vector of the containing package where the target component
     *             can be found.
     */

    void setBindingSlot(int slot);

    /**
     * Get the binding slot to be used. This is the offset within the binding vector of the containing
     * component where the actual target component is to be found.
     *
     * @return the offset in the binding vector of the containing package where the target component
     *         can be found.
     */

    int getBindingSlot();

    /**
     * Get the symbolic name of the component that this invocation references
     * @return the symbolic name of the target component, or null if there is no component referenced
     */

    SymbolicName getSymbolicName();


}

