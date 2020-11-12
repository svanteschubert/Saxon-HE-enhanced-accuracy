////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.lib.Validation;


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

    private int number;

    private ValidationMode(int number) {
        this.number = number;
    }

    protected int getNumber() {
        return number;
    }

    /*@NotNull*/
    protected static ValidationMode get(int number) {
        switch (number) {
            case Validation.STRICT:
                return STRICT;
            case Validation.LAX:
                return LAX;
            case Validation.STRIP:
                return STRIP;
            case Validation.PRESERVE:
                return PRESERVE;
            case Validation.DEFAULT:
            default:
                return DEFAULT;
        }
    }
}

