// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.coercion;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.value.SequenceType;

import java.util.function.Supplier;

/**
 * A data package collecting together all the information needed when invoking a coercion.
 */
public class CoercionRequest {
    public SequenceType knownType;
    public Configuration config;
    public Supplier<RoleDiagnostic> roleSupplier;
    public Location locator;

    /**
     * Make a coercion request
     *
     * @param knownType    the item type of the supplied value, to the extent this is known
     * @param config       the Saxon configuration
     * @param roleSupplier identifies the value to be converted, in error messages
     * @param locator      identifies the location, for error messages
     */

    public CoercionRequest(SequenceType knownType,
                           Configuration config,
                           Supplier<RoleDiagnostic> roleSupplier,
                           Location locator) {
        this.knownType = knownType;
        this.config = config;
        this.roleSupplier = roleSupplier;
        this.locator = locator;
    }
}

