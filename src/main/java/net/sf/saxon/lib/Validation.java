////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;


import net.sf.saxon.s9api.ValidationMode;
import net.sf.saxon.transpile.CSharpReplaceBody;

/**
 * This class contains constants and static methods to manipulate the validation
 * property of a type.
 */

public final class Validation {

    /**
     * Code indicating that the value of a validation request was invalid
     */

    public static final int INVALID = -1;

    /**
     * Code for strict validation
     */

    public static final int STRICT = 1;

    /**
     * Code for lax validation
     */

    public static final int LAX = 2;

    /**
     * Code corresponding to the XSLT option validation=preserve, which indicates
     * that existing type annotations are to be preserved but no new validation is performed.
     */

    public static final int PRESERVE = 3;

    /**
     * Code corresponding to the XSLT option validation=strip, which indicates
     * that existing type annotations are to be removed and no new validation is performed.
     */

    public static final int STRIP = 4;

    /**
     * Synonym for {@link #STRIP}, corresponding to XQuery usage
     */

    public static final int SKIP = 4;   // synonym provided for the XQuery API

    /**
     * Code indicating that no specific validation options were requested
     */

    public static final int DEFAULT = 0;

    /**
     * Code indicating that validation against a named type was requested
     */

    public static final int BY_TYPE = 8;

    /**
     * This class is never instantiated
     */

    private Validation() {
    }

    /**
     * Get the integer validation code corresponding to a given string
     *
     * @param value one of "strict", "lax", "preserve", or "strip"
     * @return the corresponding code {@link #STRICT}, {@link #LAX},
     *         {@link #PRESERVE}, or {@link #STRIP}
     */

    public static int getCode(String value) {
        return switch (value) {
            case "strict" -> STRICT;
            case "lax" -> LAX;
            case "preserve" -> PRESERVE;
            case "strip" -> STRIP;
            default -> INVALID;
        };
    }

    /**
     * Get a string representation of a validation code
     *
     * @param value one of the validation codes defined in this class
     * @return one of the strings "strict", "lax", "preserve", "skip" (sic), or "invalid"
     */

    /*@NotNull*/
    public static String describe(int value) {
        return switch (value) {
            case STRICT -> "strict";
            case LAX -> "lax";
            case PRESERVE -> "preserve";
            case STRIP -> "skip";  // for XQuery
            case BY_TYPE -> "by type";
            default -> "invalid";
        };
    }

    @CSharpReplaceBody(code="return (int)mode;")
    public static int fromValidationMode(ValidationMode mode) {
        return mode.getNumber();
    }
}
