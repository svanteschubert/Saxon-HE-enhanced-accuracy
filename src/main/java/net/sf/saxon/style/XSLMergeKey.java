////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.type.ItemType;

/**
 * An xsl:merge-key element in the stylesheet. <br>
 */

public class XSLMergeKey extends XSLSortOrMergeKey {


    @Override
//    protected boolean seesAvuncularVariables() {
//        return false;
//    }


    public void prepareAttributes() {

        super.prepareAttributes();

        if (stable != null) {
            compileError("The @stable attribute is not allowed in xsl:merge-key", "XTSE0090");
        }

    }

    /**
     * Bind a variable used in this element to the compiled form of the XSLVariable element in which it is
     * declared
     *
     * @param qName The name of the variable
     * @return the XSLVariableDeclaration (that is, an xsl:variable or xsl:param instruction) for the variable,
     *         or null if no declaration of the variable can be found
     */

//    public SourceBinding bindVariable(StructuredQName qName) {
//        return ((StyleElement) this.getParent()).bindVariable(qName);
//    }

    /**
     * Determine the type of item returned by this instruction (only relevant if
     * it is an instruction). Default implementation returns Type.ITEM, indicating
     * that we don't know, it might be anything. Returns null in the case of an element
     * such as xsl:sort or xsl:variable that can appear in a sequence constructor but
     * contributes nothing to the result sequence.
     *
     * @return the item type returned
     */

    /*@Nullable*/
    protected ItemType getReturnedItemType() {
        return null;
    }

    @Override
    protected String getErrorCode() {
        return "XTSE3200";
    }


}
