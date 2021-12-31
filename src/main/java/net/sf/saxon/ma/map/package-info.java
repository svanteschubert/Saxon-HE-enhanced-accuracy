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
