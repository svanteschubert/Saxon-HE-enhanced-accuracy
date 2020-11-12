////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import java.io.Writer;

/**
 * A class that represents a character as a hexadecimal character reference
 * and writes the result to a supplied Writer
 */

public class HexCharacterReferenceGenerator implements CharacterReferenceGenerator {

    public final static HexCharacterReferenceGenerator THE_INSTANCE = new HexCharacterReferenceGenerator();

    private HexCharacterReferenceGenerator() {
    }

    @Override
    public void outputCharacterReference(int charval, Writer writer) throws java.io.IOException {
        writer.write("&#x");
        writer.write(Integer.toHexString(charval));
        writer.write(';');
    }
}

