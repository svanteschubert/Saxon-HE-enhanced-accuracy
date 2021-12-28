////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize.charcode;


/**
 * This interface defines properties of a character set, built in to the Saxon product.
 * This is selected in xsl:output using encoding="encoding-name", where
 * the mapping from an encoding-name to a class is defined in CharacterSetFactory.
 */

public interface CharacterSet {

    /**
     * Determine if a character is present in the character set
     */

    boolean inCharset(int ch);

    /**
     * Get the preferred Java name of the character set. Note that Java in many
     * cases also supports a "historic name".
     */

    String getCanonicalName();

}

