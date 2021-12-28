////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize.charcode;

/**
 * This class defines properties of the US-ASCII character set
 */

public class ASCIICharacterSet implements CharacterSet {

    public static final ASCIICharacterSet theInstance = new ASCIICharacterSet();

    private ASCIICharacterSet() {
    }

    public static ASCIICharacterSet getInstance() {
        return theInstance;
    }

    @Override
    public final boolean inCharset(int c) {
        return c <= 0x7f;
    }

    /*@NotNull*/
    @Override
    public String getCanonicalName() {
        return "US-ASCII";
    }
}
