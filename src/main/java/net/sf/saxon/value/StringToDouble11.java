////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.type.StringToDouble;

/**
 * Convert a string to a double using the rules of XML Schema 1.1
 */
public class StringToDouble11 extends StringToDouble {

    private static StringToDouble11 THE_INSTANCE = new StringToDouble11();

    /**
     * Get the singleton instance
     *
     * @return the singleton instance of this class
     */

    /*@NotNull*/
    public static StringToDouble11 getInstance() {
        return THE_INSTANCE;
    }

    protected StringToDouble11() {
    }

    @Override
    protected double signedPositiveInfinity() {
        return Double.POSITIVE_INFINITY;
    }
}

