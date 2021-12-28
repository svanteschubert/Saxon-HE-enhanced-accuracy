////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.trans.XPathException;

/**
 * An instance of CollectionFinder can be registered with the Saxon configuration; it is called in response
 * to calls on the fn:collection() and fn:uri-collection() functions.
 *
 * When these functions are called, the {@link #findCollection(XPathContext, String)} method is
 * called to get a {@link ResourceCollection} object representing the collection of resources identified by
 * the supplied collection URI.
 *
 * @since 9.7: Supersedes URICollectionResolver.
 * The interface was changed to return Resource objects, to allow streamed
 * processing of the documents in a collection; and to pass a properties object that
 * can be used to indicate whether the collection is stable (that is, repeated requests
 * return the same result)
 */

@FunctionalInterface
public interface CollectionFinder {

    /**
     * Locate the collection of resources corresponding to a collection URI.
     * @param context The XPath dynamic evaluation context
     * @param collectionURI The collection URI: an absolute URI, formed by resolving the argument
     *                      supplied to the fn:collection or fn:uri-collection against the static
     *                      base URI
     * @return a ResourceCollection object representing the resources in the collection identified
     * by this collection URI. Result should not be null.
     * @throws XPathException if the collection was not found
     */

    ResourceCollection findCollection(XPathContext context, String collectionURI) throws XPathException;

}
