////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.value.AtomicValue;

/**
 * A key and a corresponding value to be held in a Map.
 */

public class KeyValuePair  {
    public AtomicValue key;
    public GroundedValue value;

    public KeyValuePair(AtomicValue key, GroundedValue value) {
        this.key = key;
        this.value = value;
    }

}

// Copyright (c) 2010-2020 Saxonica Limited
