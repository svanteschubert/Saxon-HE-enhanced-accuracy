////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

/**
 * xsl:include element in the stylesheet. <br>
 */

public class XSLInclude extends XSLGeneralIncorporate {

    /**
     * isImport() returns true if this is an xsl:import statement rather than an xsl:include
     */

    @Override
    public boolean isImport() {
        return false;
    }
}
