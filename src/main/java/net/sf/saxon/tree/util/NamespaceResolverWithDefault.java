////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.util;

import net.sf.saxon.om.NamespaceResolver;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * This class is a NamespaceResolver that modifies an underyling NamespaceResolver
 * by changing the mapping of the null prefix to be a specified namespace, rather than
 * the one used by the underlying namespace resolver.
 */
public class NamespaceResolverWithDefault implements NamespaceResolver {

    private NamespaceResolver baseResolver;
    private String defaultNamespace;

    public NamespaceResolverWithDefault(NamespaceResolver base, String defaultNamespace) {
        this.baseResolver = base;
        this.defaultNamespace = defaultNamespace;
    }

    /**
     * Get the namespace URI corresponding to a given prefix. Return null
     * if the prefix is not in scope.
     *
     * @param prefix     the namespace prefix. May be the zero-length string, indicating
     *                   that there is no prefix. This indicates either the default namespace or the
     *                   null namespace, depending on the value of useDefault.
     * @param useDefault true if the default namespace is to be used when the
     *                   prefix is "". If false, the method returns "" when the prefix is "".
     * @return the uri for the namespace, or null if the prefix is not in scope.
     *         The "null namespace" is represented by the pseudo-URI "".
     */

    /*@Nullable*/
    @Override
    public String getURIForPrefix(/*@NotNull*/ String prefix, boolean useDefault) {
        if (useDefault && prefix.isEmpty()) {
            return defaultNamespace;
        } else {
            return baseResolver.getURIForPrefix(prefix, useDefault);
        }
    }

    /**
     * Get an iterator over all the prefixes declared in this namespace context. This will include
     * the default namespace (prefix="") and the XML namespace where appropriate
     */

    @Override
    public Iterator<String> iteratePrefixes() {
        ArrayList<String> list = new ArrayList<String>(10);
        for (Iterator<String> it = baseResolver.iteratePrefixes(); it.hasNext(); ) {
            String p = it.next();
            if (p.length() != 0) {
                list.add(p);
            }
        }
        list.add("");
        return list.iterator();
    }
}

