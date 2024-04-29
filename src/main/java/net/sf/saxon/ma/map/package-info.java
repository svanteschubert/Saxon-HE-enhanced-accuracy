////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * <p>This package implements maps, as introduced in XSLT 3.0 and XQuery 3.1:
 * maps provide a dictionary-like data structure.</p>
 * <p>Maps are immutable, so that adding a new entry to a map creates a new map.</p>
 * <p>The entries in a map are (Key, Value) pairs. The key is always an atomic value; the value
 * may be any XPath sequence.</p>
 * <p>There are functions (each supported by its own implementation class) to create a new map,
 * to add an entry to a map, to get an entry from a map, and to list all the keys that are present
 * in a map.</p>
 */
package net.sf.saxon.ma.map;
