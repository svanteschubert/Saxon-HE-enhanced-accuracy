////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

/**
 * The object represents a declaration (that is, a top-level element) in a stylesheet.
 * A declaration exists within a stylesheet module and takes its import precedence
 * from that of the module. The declaration corresponds to a source element in a stylesheet
 * document. However, if a stylesheet module is imported twice with different precedences,
 * then two declarations may share the same source element.
 */

public class ComponentDeclaration {

    private StyleElement sourceElement;
    private StylesheetModule module;

    /**
     * Create a ComponentDeclaration as the combination of a stylesheet module and a declaration
     * within that module
     * @param module the stylesheet module
     * @param source the source declaration within that module
     */

    public ComponentDeclaration(StylesheetModule module, StyleElement source) {
        this.module = module;
        this.sourceElement = source;
    }

    /**
     * Get the module in which this ComponentDeclaration appears
     * @return the module
     */

    public StylesheetModule getModule() {
        return module;
    }

    /**
     * Get the source declaration of this component
     * @return the element in the stylesheet tree corresponding to the declaration
     */

    public StyleElement getSourceElement() {
        return sourceElement;
    }

    /**
     * Get the import precedence of the declaration, which is the same as the import
     * precedence of the module in which it appears
     * @return the import precedence
     */

    public int getPrecedence() {
        return module.getPrecedence();
    }
}
