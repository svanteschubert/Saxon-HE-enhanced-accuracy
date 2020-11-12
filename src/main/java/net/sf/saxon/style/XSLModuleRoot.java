////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;

/**
 * Class representing xsl:stylesheet, xsl:transform, or xsl:package
 */
public abstract class XSLModuleRoot extends StyleElement {

    public static final int ANNOTATION_UNSPECIFIED = 0;
    public static final int ANNOTATION_STRIP = 1;
    public static final int ANNOTATION_PRESERVE = 2;

    /**
     * Ask whether it is required that modes be explicitly declared
     *
     * @return true if modes referenced within this package be explicitly declared
     */

    public boolean isDeclaredModes() {
        return false;
    }

    /**
     * Process the attributes of every node in the stylesheet module
     */

    @Override
    public void processAllAttributes() throws XPathException {
        prepareAttributes();
        for (NodeInfo node : children(StyleElement.class::isInstance)) {
            try {
                ((StyleElement) node).processAllAttributes();
            } catch (XPathException err) {
                ((StyleElement) node).compileError(err);
            }
        };
    }


    @Override
    public void index(ComponentDeclaration decl, PrincipalStylesheetModule top) {
        compileError(getDisplayName() + " can appear only as the outermost element", "XTSE0010");
    }

    /**
     * Get the value of the input-type-annotations attribute, for this module alone.
     * The value is an or-ed combination of the two bits
     * {@link #ANNOTATION_STRIP} and {@link #ANNOTATION_PRESERVE}
     *
     * @return the value if the input-type-annotations attribute in this stylesheet module
     */

    public int getInputTypeAnnotationsAttribute()  {
        String inputTypeAnnotationsAtt = getAttributeValue("", "input-type-annotations");
        if (inputTypeAnnotationsAtt != null) {
            switch (inputTypeAnnotationsAtt) {
                case "strip":
                    return ANNOTATION_STRIP;
                case "preserve":
                    return ANNOTATION_PRESERVE;
                case "unspecified":
                    return ANNOTATION_UNSPECIFIED;
                default:
                    compileError("Invalid value for input-type-annotations attribute. " +
                                         "Permitted values are (strip, preserve, unspecified)", "XTSE0020");
                    return ANNOTATION_UNSPECIFIED;
            }
        }
        return -1;
    }

}

