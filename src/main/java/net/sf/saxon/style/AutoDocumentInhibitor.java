// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.type.ItemType;

/**
 * A marker interface implemented by XSLT instructions, which, if they appear in a sequence
 * constructor under 4.0, inhibit the automatic creation of a document node by a variable
 * binding element
 */

public interface AutoDocumentInhibitor {

    /**
     * Get the type of the items returned by this instruction
     * @return the static item type
     */
    ItemType getItemType();
}

