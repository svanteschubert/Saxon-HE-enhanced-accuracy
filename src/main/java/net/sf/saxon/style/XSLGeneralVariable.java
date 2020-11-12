////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;

/**
 * This class defines common behaviour across xsl:variable, xsl:param, and xsl:with-param
 */

public abstract class XSLGeneralVariable extends StyleElement {

    protected SourceBinding sourceBinding = new SourceBinding(this);

    /**
     * Get the source binding object that holds information about the declared variable.
     */

    public SourceBinding getSourceBinding() {
        return sourceBinding;
    }

    public StructuredQName getVariableQName() {
        return sourceBinding.getVariableQName();
    }

    @Override
    public StructuredQName getObjectName() {
        return sourceBinding.getVariableQName();
    }

    /**
     * Determine whether this type of element is allowed to contain a template-body
     *
     * @return true: yes, it may contain a template-body
     */

    @Override
    public boolean mayContainSequenceConstructor() {
        return true;
    }

    /**
     * Test whether this is a global variable or parameter
     *
     * @return true if this is global
     */

    public boolean isGlobal() {
        return isTopLevel();
        // might be called before the "global" field is initialized
    }

    /**
     * Check that the variable is not already declared, and allocate a slot number
     *
     * @param decl the declaration being validated. A single XSLVariableDeclaration object may represent
     *             multiple declarations if it appears in a stylesheet module that is included/imported more than once
     */

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        sourceBinding.validate();
    }

    /**
     * Hook to allow additional validation of a parent element immediately after its
     * children have been validated.
     */

    @Override
    public void postValidate() throws XPathException {
        sourceBinding.postValidate();
    }


}

