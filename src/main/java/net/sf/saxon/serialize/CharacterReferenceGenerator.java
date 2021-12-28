////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import java.io.Writer;

/**
 * A class that expands a character to a character reference, entity reference, etc,
 * and writes the resulting reference to a writer
 */

public interface CharacterReferenceGenerator {

    /**
     * Generate a character reference
     *
     * @param charval the unicode code point of the character concerned
     * @param writer  the Writer to which the character reference is to be written
     * @throws java.io.IOException if the Writer reports an error
     */
    void outputCharacterReference(int charval, Writer writer) throws java.io.IOException;
}

