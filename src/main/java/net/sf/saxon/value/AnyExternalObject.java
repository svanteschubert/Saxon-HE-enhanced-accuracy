////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.om.Item;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;

/**
 * An external object is a fourth kind of Item (after nodes, atomic values, and functions):
 * it acts as a wrapper for a Java object (or, in C#, a .NET object).
 *
 * <p>This interface deliberately does not use generics, enabling it to be
 * used in the same way both in Java and C#.</p>
 */

public interface AnyExternalObject extends Item {

    /**
     * Get the Java (or C#) object that is wrapped by this {@code AnyExternalObject}
     * @return the wrapped object
     */

    Object getWrappedObject();

    /**
     * Get the item type of the object
     * @param th the type hierarchy
     * @return the item type
     */

    ItemType getItemType(TypeHierarchy th);
}

