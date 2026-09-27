////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.lib.Validation;
import net.sf.saxon.transpile.CSharpReplaceBody;


/**
 * Enumeration class defining different schema validation (or construction) modes
 */
public enum ValidationMode {
    /**
     * Strict validation
     */
    STRICT(Validation.STRICT),
    /**
     * Lax validation
     */
    LAX(Validation.LAX),
    /**
     * Preserve existing type annotations if any
     */
    PRESERVE(Validation.PRESERVE),
    /**
     * Remove any existing type annotations, mark as untyped
     */
    STRIP(Validation.STRIP),
    /**
     * Value indication no preference: the choice is defined elsewhere
     */
    DEFAULT(Validation.DEFAULT);


    private final int number;

    ValidationMode(int number) {
        this.number = number;
    }

    @CSharpReplaceBody(code="return (int)number;")
    public int getNumber() {
        return number;
        //return Validation.fromValidationMode(this);
    }

    /*@NotNull*/
    public static ValidationMode get(int number) {
        return switch (number) {
            case Validation.STRICT -> STRICT;
            case Validation.LAX -> LAX;
            case Validation.STRIP -> STRIP;
            case Validation.PRESERVE -> PRESERVE;
            case Validation.DEFAULT -> DEFAULT;
            default -> DEFAULT;
        };
    }
}

