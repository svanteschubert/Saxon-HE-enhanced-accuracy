////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;

/**
 * The standard JAXP URIResolver is given a relative URI and a base URI and returns the resource
 * identified by this combination. However, to support a stable implementation of the doc() function,
 * Saxon needs to know what the absolute URI is before the resource is fetched, so it can determine whether
 * a document with that absolute URI actually exists.
 * <p>This extended interface defines a URIResolver that separates the two functions of resolving a relative URI
 * against a base URI, and fetching a resource with that absolute URI. If the URI resolver supplied to Saxon
 * implements this interface, the absolute URI associated with a loaded document will be the URI returned by
 * this resolver.</p>
 * <p>The particular motivation for providing this interface is to allow a URIResolver to wrap a .NET XmlResolver,
 * which has additional capability not present in the JAXP interface.</p>
 */

public interface RelativeURIResolver extends URIResolver {

    /**
     * Create an absolute URI from a relative URI and a base URI. This method performs the
     * process which is correctly called "URI resolution": this is purely a syntactic operation
     * on the URI strings, and does not retrieve any resources.
     *
     * @param href A relative or absolute URI, to be resolved against the specified base URI
     * @param base The base URI against which the first argument will be made
     *             absolute if the absolute URI is required.
     * @return A string containing the absolute URI that results from URI resolution. If the resource
     *         needs to be fetched, this absolute URI will be supplied as the href parameter in a subsequent
     *         call to the <code>resolve</code> method.
     * @throws javax.xml.transform.TransformerException
     *          if any failure occurs
     */

    String makeAbsolute(String href, String base)
            throws TransformerException;

    /**
     * Called by the processor when it encounters
     * an xsl:include, xsl:import, or document() function.
     *
     * @param uri The absolute URI to be dereferenced
     * @return A Source object, or null if the href cannot be dereferenced,
     *         and the processor should try to resolve the URI itself.
     * @throws javax.xml.transform.TransformerException
     *          if an error occurs when trying to
     *          resolve the URI.
     */
    Source dereference(String uri)
            throws TransformerException;

    /**
     * Called by the processor when it encounters
     * an xsl:include, xsl:import, or document() function.
     * <p>Despite the name, the main purpose of this method is to dereference the URI, not merely
     * to resolve it.</p>
     * <p>This method is provided because it is required by the interface. When using a RelativeURIResolver,
     * the single-argument dereference() method is preferred. The result of calling this method should be the
     * same as the result of calling <code>dereference(makeAbsolute(href, base))</code></p>
     *
     * @param href An href attribute, which may be relative or absolute.
     * @param base The base URI against which the first argument will be made
     *             absolute if the absolute URI is required.
     * @return A Source object, or null if the href cannot be resolved,
     *         and the processor should try to resolve the URI itself.
     * @throws javax.xml.transform.TransformerException
     *          if an error occurs when trying to
     *          resolve the URI.
     */
    @Override
    Source resolve(String href, String base)
            throws TransformerException;


}

