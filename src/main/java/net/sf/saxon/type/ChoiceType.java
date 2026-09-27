// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

/**
 * Interface representing either an XPath-defined choice type or an XSD-defined (pure) union type. The two cases
 * are slightly different. The instances of an XSD-defined union are always atomic items, and they always support
 * (for example) casting from string. But the coercion rules for both cases are very similar.
 */

public interface ChoiceType {

    /**
     * Get the alternative types available within this choice types
     * @return the alternative item types
     */

    Iterable<? extends ItemType> getAlternatives();
}

