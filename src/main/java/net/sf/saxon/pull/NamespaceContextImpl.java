////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pull;

import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.transpile.CSharpSuppressWarnings;

import javax.xml.namespace.NamespaceContext;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class bridges between the JAXP 1.3 NamespaceContext interface and Saxon's
 * equivalent NamespaceResolver interface. It allows any implementation of the Saxon
 * NamespaceResolver to be wrapped as a JAXP NamespaceContext.
 */

public class NamespaceContextImpl implements NamespaceResolver
        , NamespaceContext
{

    NamespaceResolver resolver;

    /**
     * Constructor: wrap a Saxon NamespaceResolver as a JAXP NamespaceContext
     *
     * @param resolver the Saxon NamespaceResolver
     */

    public NamespaceContextImpl(NamespaceResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Get the namespace URI corresponding to a given prefix. Return null
     * if the prefix is not in scope.
     *
     * @param prefix     the namespace prefix
     * @param useDefault true if the default namespace is to be used when the
     *                   prefix is ""
     * @return the uri for the namespace, or null if the prefix is not in scope
     */

    /*@Nullable*/
    @Override
    public NamespaceUri getURIForPrefix(String prefix, boolean useDefault) {
        return resolver.getURIForPrefix(prefix, useDefault);
    }

    /**
     * Get an iterator over all the prefixes declared in this namespace context. This will include
     * the default namespace (prefix="") and the XML namespace where appropriate
     */

    @Override
    public Iterator<String> iteratePrefixes() {
        return resolver.iteratePrefixes();
    }

    /**
     * Implement the JAXP getNamespaceURI() method in terms of the Saxon-specific methods
     *
     * @param prefix a namespace prefix
     * @return the corresponding URI, if the prefix is bound, or "" otherwise
     */

    @Override
    public String getNamespaceURI(String prefix) {
        if (prefix.equals("xmlns")) {
            return "http://www.w3.org/2000/xmlns/";
        }
        NamespaceUri uri = resolver.getURIForPrefix(prefix, true);
        return uri == null ? null : uri.toString();
    }

    /**
     * Get the prefix bound to a particular namespace URI, if there is one, or null if not (JAXP method)
     *
     * @param uri the namespace URI
     * @return the prefix bound to the URI if there is one, or null if not
     */

    @Override
    @CSharpSuppressWarnings("UnsafeIteratorConversion")
    public String getPrefix(String uri) {
        Iterator<String> prefixes = iteratePrefixes();
        while (prefixes.hasNext()) {
            String p = prefixes.next();
            NamespaceUri u = resolver.getURIForPrefix(p, true);
            if (u.toString().equals(uri)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Get all the prefixes mapped to a given namespace URI (JAXP method)
     *
     * @param uri the namespace URI
     * @return an iterator over all the prefixes bound to this namespace URI
     */
    @Override
    public Iterator<String> getPrefixes(String uri) {
        List<String> list = new ArrayList<>(4);
        Iterator<String> prefixes = iteratePrefixes();
        prefixes.forEachRemaining(p -> {
            NamespaceUri u = resolver.getURIForPrefix(p, true);
            if (u.toString().equals(uri)) {
                list.add(p);
            }
        });
        return list.iterator();
    }
}

