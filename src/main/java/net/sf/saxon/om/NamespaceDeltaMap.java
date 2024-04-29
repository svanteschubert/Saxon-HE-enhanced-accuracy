////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

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

public class NamespaceDeltaMap extends NamespaceMap implements NamespaceBindingSet, NamespaceResolver {

    private static final NamespaceDeltaMap EMPTY_MAP = new NamespaceDeltaMap();

    /**
     * Get a namespace map containing no namespace bindings
     * @return an empty namespace map
     */
    public static NamespaceDeltaMap emptyMap() {
        return EMPTY_MAP;
    }

    private NamespaceDeltaMap() {
        prefixes = new String[]{};
        uris = new NamespaceUri[]{};
    }

    @Override
    protected NamespaceMap makeNamespaceMap() {
        return new NamespaceDeltaMap();
    }

    @Override
    public boolean allowsNamespaceUndeclarations() {
        return true;
    }

    @Override
    public NamespaceDeltaMap put(String prefix, NamespaceUri uri) {
        return (NamespaceDeltaMap)super.put(prefix, uri);
    }

    @Override
    public NamespaceDeltaMap remove(String prefix) {
        return (NamespaceDeltaMap)super.remove(prefix);
    }
}

