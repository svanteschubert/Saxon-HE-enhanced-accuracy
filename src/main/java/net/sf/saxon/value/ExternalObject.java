////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.om.Item;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;

/**
 * An item that wraps an external (Java or .NET) object
 * @param <T> the static class of the wrapped object
 */

public interface ExternalObject<T> extends Item {

    /**
     * Get the wrapped object
     * @return the wrapped object. This will never be null.
     */

    T getObject();

    /**
     * Get the item type of the object
     * @return the item type
     */

    ItemType getItemType(TypeHierarchy th);
}

