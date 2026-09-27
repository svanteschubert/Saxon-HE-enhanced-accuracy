// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.ma.map.MapItem;

/**
 * This class is an attempt to generalise the concept of a type annotation to allow an optional label
 * as well. The idea of labelled values was an experiment, first in Saxon and then in XDM 4.0; it is
 * largely superseded by the introduction of JNodes, but is not yet entirely obsolete.
 */

public interface AtomicMetadata {

    AtomicType getType();

    MapItem getLabel();

}

