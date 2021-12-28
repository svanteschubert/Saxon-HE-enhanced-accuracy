////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

/**
 * Abstract class representing an extension instruction
 */

public abstract class ExtensionInstruction extends StyleElement {

    /**
     * Determine whether this node is an instruction.
     *
     * @return true - it is an instruction
     */

    @Override
    public final boolean isInstruction() {
        return true;
    }

    /**
     * Determine whether this type of element is allowed to contain an xsl:fallback
     * instruction. Note that this is only relevant if the element is an instruction.
     *
     * @return true: this element (assuming it is an instruction) may contain an xsl:fallback
     *         child instruction, which is ignored if the element is recognized.
     */

    @Override
    public final boolean mayContainFallback() {
        return true;
    }

}
