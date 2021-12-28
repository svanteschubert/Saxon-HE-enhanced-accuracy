////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.instruct.Actor;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.Optimizer;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;

/**
 * This interface is implemented by all top-level XSL elements that can contain local variable declarations.
 * Specifically, a top-level xsl:template, xsl:variable, xsl:param, or xsl:function element
 * or an xsl:attribute-set, xsl:accumulator, or xsl:key element.
 */

public interface StylesheetComponent {

    /**
     * Get the SlotManager associated with this stylesheet construct. The SlotManager contains the
     * information needed to manage the local stack frames used by run-time instances of the code.
     *
     * @return the associated SlotManager object
     */

    SlotManager getSlotManager();

    /**
     * Optimize the stylesheet construct
     *
     * @param declaration the combination of the source XSLT element defining the component, and the
     * module in which it appears
     */

    void optimize(ComponentDeclaration declaration) throws XPathException;

    /**
     * Generate byte code if appropriate
     * @param opt the optimizer
     * @throws XPathException if bytecode generation fails
     */

    void generateByteCode(Optimizer opt) throws XPathException;

    /**
     * Get the corresponding Actor object that results from the compilation of this
     * StylesheetComponent
     * @return the compiled ComponentCode
     * @throws XPathException if generating the ComponentBody fails
     * @throws UnsupportedOperationException for second-class components such as keys that support outwards references
     * but not inwards references
     */

    Actor getActor() throws XPathException;

    /**
     * Get the symbolic name of the component, that is, the combination of the component kind and
     * the qualified name
     * @return the component's symbolic name
     */

    SymbolicName getSymbolicName();

    /**
     * Check the compatibility of this component with another component that it is overriding
     * @param component the overridden component
     * @throws XPathException if the components are not compatible (differing signatures)
     */

    void checkCompatibility(Component component) throws XPathException;
}

