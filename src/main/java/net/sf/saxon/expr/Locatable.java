////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.s9api.Location;

/**
 * Represents a construct that has a meaningful location for use in diagnostics
 */
public interface Locatable {

    /**
     * Get the location of the construct
     * @return the location. If no location information is available, the method should return {@link Loc#NONE}
     * rather than returning null. However, callers would be well advised to check for the result being null.
     */

    Location getLocation();

}

