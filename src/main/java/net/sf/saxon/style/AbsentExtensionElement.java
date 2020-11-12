////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.trans.XmlProcessingIncident;
import net.sf.saxon.trans.XPathException;

/**
 * This element is a surrogate for an extension element (or indeed an xsl element)
 * for which no implementation is available.
 */

public class AbsentExtensionElement extends StyleElement {

    @Override
    public boolean isInstruction() {
        return true;
    }

    /**
     * Determine whether this type of element is allowed to contain a template-body
     */

    @Override
    public boolean mayContainSequenceConstructor() {
        return true;
    }

    /**
     * Process the attributes of this element and all its children
     */

    @Override
    public void processAllAttributes() throws XPathException {
        if (reportingCircumstances == OnFailure.IGNORED_INSTRUCTION) {
            return;
        }
        if (reportingCircumstances == OnFailure.REPORT_ALWAYS) {
            compileError(validationError);
        }
        if (isTopLevel() && forwardsCompatibleModeIsEnabled()) {
            // do nothing
        } else {
            super.processAllAttributes();
        }
    }

    @Override
    public void prepareAttributes() {
    }

    /**
     * Recursive walk through the stylesheet to validate all nodes
     *
     * @param decl
     * @param excludeStylesheet
     */

    @Override
    public void validateSubtree(ComponentDeclaration decl, boolean excludeStylesheet) throws XPathException {
        if (reportingCircumstances == OnFailure.IGNORED_INSTRUCTION || (isTopLevel() && forwardsCompatibleModeIsEnabled())) {
            // do nothing
        } else {
            super.validateSubtree(decl, excludeStylesheet);
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
    }

    /*@Nullable*/
    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {

        if (isTopLevel() || reportingCircumstances == OnFailure.IGNORED_INSTRUCTION) {
            return null;
        }

        // if there are fallback children, compile the code for the fallback elements

        if (validationError == null) {
            validationError = new XmlProcessingIncident("Unknown instruction");
        }
        return fallbackProcessing(exec, decl, this);
    }
}

