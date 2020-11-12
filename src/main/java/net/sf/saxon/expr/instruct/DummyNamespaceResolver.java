////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.tree.jiter.PairIterator;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.NamespaceResolver;

import java.util.Iterator;

/**
 * A dummy namespace resolver used when validating QName-valued attributes written to
 * the result tree. The namespace node might be created after the initial validation
 * of the attribute, so in the first round of validation we only check the lexical form
 * of the value, and we defer prefix checks until later.
 */

public final class DummyNamespaceResolver implements NamespaceResolver {

    private final static DummyNamespaceResolver THE_INSTANCE = new DummyNamespaceResolver();

    /**
     * Return the singular instance of this class
     *
     * @return the singular instance
     */

    public static DummyNamespaceResolver getInstance() {
        return THE_INSTANCE;
    }

    private DummyNamespaceResolver() {
    }


    /**
     * Get the namespace URI corresponding to a given prefix.
     *
     * @param prefix     the namespace prefix
     * @param useDefault true if the default namespace is to be used when the
     *                   prefix is ""
     * @return the uri for the namespace, or null if the prefix is not in scope
     */

    @Override
    public String getURIForPrefix(String prefix, boolean useDefault) {
        if (prefix.isEmpty()) {
            return NamespaceConstant.NULL;
        } else if ("xml".equals(prefix)) {
            return NamespaceConstant.XML;
        } else {
            // this is a dummy namespace resolver, we don't actually know the URI
            return NamespaceConstant.NULL;
        }
    }

    /**
     * Get an iterator over all the prefixes declared in this namespace context. This will include
     * the default namespace (prefix="") and the XML namespace where appropriate
     */

    @Override
    public Iterator<String> iteratePrefixes() {
        return new PairIterator<String>("", "xml");
    }
}

