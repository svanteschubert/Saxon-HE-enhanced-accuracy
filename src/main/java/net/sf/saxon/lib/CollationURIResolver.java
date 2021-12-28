////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.trans.XPathException;

/**
 * A CollationURIResolver accepts a collation name as input, and returns
 * a collation (represented by a {@link StringCollator} as output. A CollationURIResolver
 * can be registered with the Configuration (or with a TransformerFactory)
 * to resolve all collation URIs used in a stylesheet or query.
 */
public interface CollationURIResolver {

    /**
     * Resolve a collation URI (expressed as a string in the form of an absolute URI) and return
     * the corresponding collation.
     *
     * @param collationURI the collation URI as written in the query or stylesheet, after resolving
     * against the base URI where necessary
     * @param config      The configuration. Provided in case the collation URI resolver
     *                    needs it.
     * @return a StringCollator, representing the collation to be used. Note that although
     *         any StringCollator may be returned, functions such as contains() that need to break
     *         a string into its collation units will work only if the returned StringCollator
     *         is a {@link net.sf.saxon.lib.SubstringMatcher}.
     *         <p>If the Collation URI is not recognized, return null.
     *         Note that unlike the JAXP URIResolver, returning null does not cause the default
     *         CollationURIResolver to be invoked; if this is required, the user-written CollationURIResolver
     *         should explicitly instantiate and invoke the {@link StandardCollationURIResolver} before
     *         returning null.</p>
     * @throws XPathException if the form of the URI is recognized, but if it is not valid,
     * for example because it is not supported in this environment. In this case of the UCA collation,
     * this exception is thrown when there are invalid parameters and fallback=no is specified.
     * @since 8.5/8.9 (this interface was introduced provisionally in 8.5, and modified in 8.9 to return
     *        a StringCollator rather than a Comparator). Modified in 9.6 to accept the absolute URI of the
     *        collation, rather than the relative URI and base URI separately, and also to allow an
     *        exception to be thrown
     */

    public StringCollator resolve(String collationURI, Configuration config) throws XPathException;
}

