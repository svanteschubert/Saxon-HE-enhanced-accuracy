////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.expr.parser;

import net.sf.saxon.expr.Binding;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A map from old bindings to new bindings, maintained during a copy() operation applied
 * to an expression tree. When local variable bindings are copied, the mapping from old to
 * new binding can be added to the list; when local variable references are encountered,
 * the binding can be switched from the old binding to the new.
 *
 * Bindings are compared strictly using object identity. Some classes that implement the
 * Binding interface have their own equals() method that could return true for two
 * bindings that need to be treated as distinct.
 */
public class RebindingMap {

    private Map<Binding, Binding> map = null; // created lazily

    /**
     * Add an entry to the binding map
     * @param oldBinding the existing binding
     * @param newBinding the replacement binding
     */

    public void put(Binding oldBinding, Binding newBinding) {
        if (map == null) {
            map = new IdentityHashMap<Binding, Binding>();
        }
        map.put(oldBinding, newBinding);
    }

    /**
     * Get the new binding corresponding to an existing binding if there is one
     * @param oldBinding the existing binding
     * @return the new binding if one exists, or null otherwise
     */

    public Binding get(Binding oldBinding) {
        return map == null ? null : map.get(oldBinding);
    }
}

