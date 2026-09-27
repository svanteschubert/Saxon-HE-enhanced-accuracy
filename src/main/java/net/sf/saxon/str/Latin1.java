////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.str;

import net.sf.saxon.Configuration;

/**
 * An implementation of {@code UnicodeString} that subclasses Twine8 (so it is only suitable
 * for codepoints up to 255), and that also caches a String representing the same characters.
 * This is wasteful of space, so it is used predominantly for static constants.
 */

public class Latin1 extends Twine8 {

    private final String baseString;

    /**
     * Protected constructor
     *
     * @param baseString the string to be wrapped: the caller is responsible for ensuring this
     *                   contains no codepoints above 255
     */

    protected Latin1(String baseString) {
        super(baseString);
        this.baseString = baseString;
    }

    /**
     * Wrap a String, which must contain no surrogates
     *
     * @param base the string. The caller warrants that this string contains no surrogates;
     *             this condition is checked only if Java assertions are enabled.
     * @return the wrapped string.
     */

    public static Latin1 of(String base) {
        if (Configuration.isAssertionsEnabled()) {
            for (int i = 0; i < base.length(); i++) {
                assert base.charAt(i) <= 255;
            }
        }
        return new Latin1(base);
    }


    @Override
    public int compareTo(UnicodeString other) {
        if (other instanceof Latin1) {
            return baseString.compareTo(((Latin1) other).baseString);
        } else {
            return super.compareTo(other);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Latin1) {
            return this.baseString.equals(((Latin1) obj).baseString);
        } else {
            return super.equals(obj);
        }
    }

    @Override
    public String toString() {
        return baseString;
    }

}

