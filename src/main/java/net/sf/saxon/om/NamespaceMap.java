////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.lib.NamespaceConstant;

import java.util.*;

/**
 * Holds a set of namespace bindings as a simple immutable map from prefixes to URIs.
 *
 * <p>A NamespaceMap never physically contains a binding for the XML namespace,
 * but some interfaces behave as if it did.</p>
 *
 * <p>The map may or may not contain a binding for the default namespace, represented
 * by the prefix "" (zero-length string)</p>
 *
 * <p>The map must not contain any namespace undeclarations: that is, the namespace will
 * never be "" (zero-length string)</p>
 */

public class NamespaceMap implements NamespaceBindingSet, NamespaceResolver {

    protected String[] prefixes;  // always sorted, for binary search
    protected String[] uris;

    private static String[] emptyArray = new String[]{};

    private static NamespaceMap EMPTY_MAP = new NamespaceMap();

    /**
     * Get a namespace map containing no namespace bindings
     * @return an empty namespace map
     */
    public static NamespaceMap emptyMap() {
        return EMPTY_MAP;
    }

    /**
     * Get a namespace map containing a single namespace binding
     * @param prefix the namespace prefix
     * @param uri the namespace uri
     * @return a map containing the single binding; or an empty map if the binding is
     * the standard binding of the XML namespace
     * @throws IllegalArgumentException for an invalid mapping or if the namespace URI is empty
     */

    public static NamespaceMap of(String prefix, String uri) {
        NamespaceMap map = new NamespaceMap();
        if (map.isPointlessMapping(prefix, uri)) {
            return EMPTY_MAP;
        }
        map.prefixes = new String[]{prefix};
        map.uris  = new String[]{uri};
        return map;
    }

    protected NamespaceMap() {
        prefixes = emptyArray;
        uris = emptyArray;
    }

    protected NamespaceMap newInstance() {
        return new NamespaceMap();
    }

    /**
     * Create a namespace map from a list of namespace bindings
     * @param bindings the list of namespace bindings. If there is more that one
     *                 binding for the same prefix, the last one wins. Any binding
     *                 of the prefix "xml" to the XML namespace is ignored, but
     *                 an incorrect binding of the XML namespace causes an exception.
     * @throws IllegalArgumentException if the "xml" prefix is bound to the wrong
     * namespace, or if any other prefix is bound to the XML namespace
     */

    public NamespaceMap(List<NamespaceBinding> bindings) {
        NamespaceBinding[] bindingArray = bindings.toArray(NamespaceBinding.EMPTY_ARRAY);
        Arrays.sort(bindingArray, Comparator.comparing(NamespaceBinding::getPrefix));
        boolean bindsXmlNamespace = false;
        prefixes = new String[bindingArray.length];
        uris = new String[bindingArray.length];
        for (int i=0; i<bindingArray.length; i++) {
            prefixes[i] = bindingArray[i].getPrefix();
            uris[i] = bindingArray[i].getURI();
            if (prefixes[i].equals("xml")) {
                bindsXmlNamespace = true;
                if (!uris[i].equals(NamespaceConstant.XML)) {
                    throw new IllegalArgumentException("Binds xml prefix to the wrong namespace");
                }
            } else if (uris[i].equals(NamespaceConstant.XML)) {
                throw new IllegalArgumentException("Binds xml namespace to the wrong prefix");
            }
        }
        if (bindsXmlNamespace) {
            remove("xml");
        }
    }

    /**
     * Create a NamespaceMap that captures all the information in a given NamespaceResolver
     *
     * @param resolver the NamespaceResolver
     */

    public static NamespaceMap fromNamespaceResolver(NamespaceResolver resolver) {
        Iterator<String> iter = resolver.iteratePrefixes();
        List<NamespaceBinding> bindings = new ArrayList<>();
        while (iter.hasNext()) {
            String prefix = iter.next();
            String uri = resolver.getURIForPrefix(prefix, true);
            bindings.add(new NamespaceBinding(prefix, uri));
        }
        return new NamespaceMap(bindings);
    }

    public boolean allowsNamespaceUndeclarations() {
        return false;
    }

    /**
     * Get the number of entries in the map
     * @return the number of prefix-uri bindings (excluding any binding for the XML namespace)
     */

    public int size() {
        return prefixes.length;
    }

    /**
     * Ask if the map contains only the binding
     * @return true if the map contains no bindings
     */

    public boolean isEmpty() {
        return prefixes.length == 0;
    }

    /**
     * Get the URI associated with a given prefix. If the supplied prefix is "xml", the
     * XML namespace {@link NamespaceConstant#XML} is returned, even if the map is empty.
     * @param prefix the required prefix (may be an empty string to get the default namespace)
     * @return the associated URI, or null if no mapping is present. Note that we return null
     * when no mapping is present, even for the case where the prefix is the empty string.
     */

    @Override
    public String getURI(String prefix) {
        if (prefix.equals("xml")) {
            return NamespaceConstant.XML;
        }
        int position = Arrays.binarySearch(prefixes, prefix);
        return position >= 0 ? uris[position] : null;
    }

    /**
     * Get the default namespace
     * @return the namespace bound to the prefix "" if there is one, otherwise "".
     */

    public String getDefaultNamespace() {
        // If the prefix "" is present, it will be the first in alphabetical order
        if (prefixes.length > 0 && prefixes[0].isEmpty()) {
            return uris[0];
        } else {
            return "";
        }
    }

    /**
     * Add a new entry to the map, or replace an existing entry. An attempt
     * to add a binding of the "xml" prefix to the XML namespace is silently ignored.
     * @param prefix the prefix whose entry is to be added or replaced. May be zero-length
     *               to represent the default namespace
     * @param uri the URI to be associated with this prefix; if zero-length, any
     *            existing mapping for the prefix is removed.
     * @return a new map containing the added or replaced entry (or this map, unchanged,
     * if the prefix-uri mapping was already present in the old map).
     * @throws IllegalArgumentException if an attempt is made to create an incorrect
     * mapping for the "xml" prefix or URI.
     */

    public NamespaceMap put(String prefix, String uri) {
        if (isPointlessMapping(prefix, uri)) {
            return this;
        }
        int position = Arrays.binarySearch(prefixes, prefix);
        if (position >= 0) {
            // An entry for this prefix already exists
            if (uris[position].equals(uri)) {
                // No change
                return this;
            } else if (uri.isEmpty()) {
                // Delete the entry for the prefix
                NamespaceMap n2 = newInstance();
                n2.prefixes = new String[prefixes.length - 1];
                System.arraycopy(prefixes, 0, n2.prefixes, 0,position);
                System.arraycopy(prefixes, position+1, n2.prefixes, position+1, prefixes.length - position);
                n2.uris = new String[uris.length - 1];
                System.arraycopy(uris, 0, n2.uris, 0, position);
                System.arraycopy(uris, position + 1, n2.uris, position + 1, uris.length - position);
                return n2;
            } else {
                // Replace the entry for the prefix
                NamespaceMap n2 = newInstance();
                n2.prefixes = Arrays.copyOf(prefixes, prefixes.length);
                n2.uris = Arrays.copyOf(uris, uris.length);
                n2.uris[position] = uri;
                return n2;
            }
        } else {
            // No existing entry for the prefix exists
            int insertionPoint = -position - 1;
            String[] p2 = new String[prefixes.length + 1];
            String[] u2 = new String[uris.length + 1];
            System.arraycopy(prefixes, 0, p2, 0, insertionPoint);
            System.arraycopy(uris, 0, u2, 0, insertionPoint);
            p2[insertionPoint] = prefix;
            u2[insertionPoint] = uri;
            System.arraycopy(prefixes, insertionPoint, p2, insertionPoint+1, prefixes.length - insertionPoint);
            System.arraycopy(uris, insertionPoint, u2, insertionPoint+1, prefixes.length - insertionPoint);

            NamespaceMap n2 = newInstance();
            n2.prefixes = p2;
            n2.uris = u2;
            return n2;
        }
    }

    private boolean isPointlessMapping(String prefix, String uri) {
        if (prefix.equals("xml")) {
            if (!uri.equals(NamespaceConstant.XML)) {
                throw new IllegalArgumentException("Invalid URI for xml prefix");
            }
            return true;
        } else if (uri.equals(NamespaceConstant.XML)) {
            throw new IllegalArgumentException("Invalid prefix for XML namespace");
        }
//        if (uri.isEmpty() && !allowsNamespaceUndeclarations()) {
//            throw new IllegalArgumentException("URI must not be zero-length");
//        }
        return false;
    }

    /**
     * Add or remove a namespace binding
     * @param prefix the namespace prefix ("" for the default namespace)
     * @param uri the namespace URI to which the prefix is bound; or "" to indicate that an existing
     *            binding for the prefix is to be removed
     */

    public NamespaceMap bind(String prefix, String uri) {
        if (uri.isEmpty()) {
            return remove(prefix);
        } else {
            return put(prefix, uri);
        }
    }

    /**
     * Remove an entry from the map
     * @param prefix the entry to be removed from the map
     * @return a new map in which the relevant entry has been removed, or this map (unchanged)
     * if the requested entry was not present
     */

    public NamespaceMap remove(String prefix) {
        int position = Arrays.binarySearch(prefixes, prefix);
        if (position >= 0) {
            String[] p2 = new String[prefixes.length - 1];
            String[] u2 = new String[uris.length - 1];
            System.arraycopy(prefixes, 0, p2, 0, position);
            System.arraycopy(uris, 0, u2, 0, position);
            System.arraycopy(prefixes, position+1, p2, position, prefixes.length - position - 1);
            System.arraycopy(uris, position+1, u2, position, uris.length - position - 1);

            NamespaceMap n2 = newInstance();
            n2.prefixes = p2;
            n2.uris = u2;
            return n2;
        } else {
            return this;
        }
    }

    /**
     * Merge the prefix/uri pairs in the supplied delta with the prefix/uri pairs
     * in this namespace map, to create a new namespace map. If a prefix is present
     * in both maps, then the one in delta takes precedence
     * @param delta prefix/uri pairs to be merged into this map
     * @return a new map, the result of the merge
     */

    public NamespaceMap putAll(NamespaceMap delta) {
        if (this == delta) {
            return this;
        } else if (isEmpty()) {
            return delta;
        } else if (delta.isEmpty()) {
            return this;
        } else {
            // Merge of two sorted arrays to produce a sorted array
            String[] p1 = prefixes;
            String[] u1 = uris;
            String[] p2 = delta.prefixes;
            String[] u2 = delta.uris;
            List<String> p3 = new ArrayList<>(p1.length + p2.length);
            List<String> u3 = new ArrayList<>(p1.length + p2.length);
            int i1 = 0;
            int i2 = 0;
            while (true) {
                int c = p1[i1].compareTo(p2[i2]);
                if (c < 0) {
                    p3.add(p1[i1]);
                    u3.add(u1[i1]);
                    if (++i1 >= p1.length) {
                        break;
                    }
                } else if (c > 0) {
                    p3.add(p2[i2]);
                    u3.add(u2[i2]);
                    if (++i2 >= p2.length) {
                        break;
                    }
                } else { // c == 0
                    p3.add(p2[i2]);
                    u3.add(u2[i2]);
                    i1++;
                    i2++;
                    if (i1 >= p1.length || i2 >= p2.length) {
                        break;
                    }
                }
            }
            while (i1 < p1.length) {
                p3.add(p1[i1]);
                u3.add(u1[i1]);
                i1++;
            }
            while (i2 < p2.length) {
                p3.add(p2[i2]);
                u3.add(u2[i2]);
                i2++;
            }
            NamespaceMap n2 = new NamespaceMap();
            n2.prefixes = p3.toArray(new String[]{});
            n2.uris = u3.toArray(new String[]{});
            return n2;
        }
    }

    public NamespaceMap addAll(NamespaceBindingSet namespaces) {
        if (namespaces instanceof NamespaceMap) {
            return putAll((NamespaceMap)namespaces);
        } else {
            NamespaceMap map = this;
            for (NamespaceBinding nb : namespaces) {
                map = map.put(nb.getPrefix(), nb.getURI());
            }
            return map;
        }
    }

    /**
     * Create a map containing all namespace declarations in this map, plus any namespace
     * declarations and minus any namespace undeclarations in the delta map
     * @param delta a map of namespace declarations and undeclarations to be applied
     * @return a map combining the namespace declarations in this map with the declarations
     * and undeclarations in the {@code delta} map.
     */

    public NamespaceMap applyDifferences(NamespaceDeltaMap delta) {
        if (delta.isEmpty()) {
            return this;
        } else {
            // Merge of two sorted arrays to produce a sorted array
            String[] p1 = prefixes;
            String[] u1 = uris;
            String[] p2 = delta.prefixes;
            String[] u2 = delta.uris;
            List<String> prefixes = new ArrayList<>(p1.length + p2.length);
            List<String> uris = new ArrayList<>(p1.length + p2.length);
            int i1 = 0;
            int i2 = 0;
            while (i1 < p1.length && i2 < p2.length) {
                int c = p1[i1].compareTo(p2[i2]);
                if (c < 0) {
                    prefixes.add(p1[i1]);
                    uris.add(u1[i1]);
                    i1++;
                } else if (c > 0) {
                    if (!u2[i2].isEmpty()) {
                        prefixes.add(p2[i2]);
                        uris.add(u2[i2]);
                    }
                    i2++;
                } else { // c == 0
                    if (!u2[i2].isEmpty() || p2[i2].isEmpty()) {
                        prefixes.add(p2[i2]);
                        uris.add(u2[i2]);
                    }
                    i1++;
                    i2++;
                }
            }
            while (i1 < p1.length) {
                prefixes.add(p1[i1]);
                uris.add(u1[i1]);
                i1++;
            }
            while (i2 < p2.length) {
                if (!u2[i2].isEmpty()) {
                    prefixes.add(p2[i2]);
                    uris.add(u2[i2]);
                }
                i2++;
            }
            NamespaceMap n2 = new NamespaceMap();
            n2.prefixes = prefixes.toArray(new String[]{});
            n2.uris = uris.toArray(new String[]{});
            return n2;
        }
    }


    /**
     * Get an iterator over the namespace bindings defined in this namespace map
     * @return an iterator over the namespace bindings. (In the current implementation
     * they will be in alphabetical order of namespace prefix.)
     */

    @Override
    public Iterator<NamespaceBinding> iterator() {
        return new Iterator<NamespaceBinding>() {
            int i = 0;
            @Override
            public boolean hasNext() {
                return i < prefixes.length;
            }

            @Override
            public NamespaceBinding next() {
                NamespaceBinding nb = new NamespaceBinding(prefixes[i], uris[i]);
                i++;
                return nb;
            }
        };
    }

    /**
     * Get all the namespace bindings defined in this namespace map as an array
     * @return the array of namespace bindings
     */

    public NamespaceBinding[] getNamespaceBindings() {
        NamespaceBinding[] result = new NamespaceBinding[prefixes.length];
        for (int i=0; i<prefixes.length; i++) {
            result[i] = new NamespaceBinding(prefixes[i], uris[i]);
        }
        return result;
    }

    /**
     * Get the differences between this NamespaceMap and another NamespaceMap, as an array
     * of namespace declarations and undeclarations
     * @param other typically the namespaces on the parent element, in which case the method
     *              returns the namespace declarations and undeclarations corresponding to the
     *              difference between this child element and its parent.
     * @param addUndeclarations if true, then when a namespace is declared in the {@code other}
     *                          map but not in {@code this} map, a namespace undeclaration
     *                          (binding the prefix to the dummy URI "") will be included
     *                          in the result. If false, namespace undeclarations are included
     *                          in the result only for the default namespace (prefix = "").
     */

    public NamespaceBinding[] getDifferences(NamespaceMap other, boolean addUndeclarations) {
        List<NamespaceBinding> result = new ArrayList<>();
        int i = 0, j = 0;
        while (true) {
            // Merge and combine the two sorted lists of prefix/uri pairs
            if (i < prefixes.length && j < other.prefixes.length) {
                int c = prefixes[i].compareTo(other.prefixes[j]);
                if (c < 0) {
                    // prefix in this namespace map, absent from other
                    result.add(new NamespaceBinding(prefixes[i], uris[i]));
                    i++;
                } else if (c == 0) {
                    // prefix present in both maps
                    if (uris[i].equals(other.uris[j])) {
                        // URI is the same; this declaration is redundant, so omit it from the result
                    } else {
                        // URI is different; use the URI appearing in this map in preference
                        result.add(new NamespaceBinding(prefixes[i], uris[i]));
                    }
                    i++;
                    j++;
                } else {
                    // prefix present in other map, absent from this: maybe add an undeclaration
                    if (addUndeclarations || prefixes[i].isEmpty()) {
                        result.add(new NamespaceBinding(other.prefixes[j], ""));
                    }
                    j++;
                }
            } else if (i < prefixes.length) {
                // prefix in this namespace map, absent from other
                result.add(new NamespaceBinding(prefixes[i], uris[i]));
                i++;
            } else if (j < other.prefixes.length) {
                // prefix present in other map, absent from this: add an undeclaration
                result.add(new NamespaceBinding(other.prefixes[j], ""));
                j++;
            } else {
                return result.toArray(NamespaceBinding.EMPTY_ARRAY);
            }
        }
    }

    /**
     * Get the namespace URI corresponding to a given prefix. Return null
     * if the prefix is not in scope.
     *
     * @param prefix     the namespace prefix. May be the zero-length string, indicating
     *                   that there is no prefix. This indicates either the default namespace or the
     *                   null namespace, depending on the value of useDefault. The prefix "xml" is always
     *                   recognized as corresponding to the XML namespace {@link NamespaceConstant#XML}
     * @param useDefault true if the default namespace is to be used when the
     *                   prefix is "". If false, the method returns "" when the prefix is "". The default
     *                   namespace is a property of the NamespaceResolver; in general it corresponds to
     *                   the "default namespace for elements and types", but that cannot be assumed.
     * @return the uri for the namespace, or null if the prefix is not in scope.
     * The "null namespace" is represented by the pseudo-URI "".
     */


    @Override
    public String getURIForPrefix(String prefix, boolean useDefault) {
        if (prefix.equals("xml")) {
            return NamespaceConstant.XML;
        }
        if (prefix.equals("")) {
            if (useDefault) {
                return getDefaultNamespace();
            } else {
                return "";
            }
        }
        return getURI(prefix);
    }

    /**
     * Get an iterator over the prefixes defined in this namespace map, including
     * the "xml" prefix.
     *
     * @return an iterator over the prefixes. (In the current implementation
     * they will be in alphabetical order, except that the "xml" prefix will always come last.)
     */

    @Override
    public Iterator<String> iteratePrefixes() {
        List<String> prefixList = new ArrayList<>(Arrays.asList(prefixes));
        prefixList.add("xml");
        return prefixList.iterator();
    }

    /**
     * Get the prefixes present in the NamespaceMap, as an array, excluding the "xml" prefix
     * @return the prefixes present in the map, not including the "xml" prefix
     */

    public String[] getPrefixArray() {
        return prefixes;
    }

    public String[] getURIsAsArray() {
        return uris;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (NamespaceBinding nb : this) {
            sb.append(nb.getPrefix()).append("=").append(nb.getURI()).append(" ");
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(prefixes) ^ Arrays.hashCode(uris);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj
                || (obj instanceof NamespaceMap
                    && Arrays.equals(prefixes, ((NamespaceMap)obj).prefixes)
                    && Arrays.equals(uris, ((NamespaceMap)obj).uris));
    }
}

