////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.event.FilterFactory;
import net.sf.saxon.event.IDFilter;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.Source;
import java.net.URISyntaxException;

/**
 * A bundle of information made available when requesting resource resolution.
 * Any of these properties may be absent.
 */

public class ResourceRequest {
    public static final String TEXT_NATURE = "https://www.iana.org/assignments/media-types/text/plain";
    public static final String BINARY_NATURE = "https://www.iana.org/assignments/media-types/application/binary";
    public static final String XQUERY_NATURE = "https://www.iana.org/assignments/media-types/application/xquery";
    public static final String XSLT_NATURE = NamespaceConstant.XSLT;
    public static final String XSD_NATURE = NamespaceConstant.SCHEMA;
    public static final String XML_NATURE = "https://www.iana.org/assignments/media-types/application/xml";
    public static final String DTD_NATURE = "https://www.iana.org/assignments/media-types/application/xml-dtd";
    public static final String EXTERNAL_ENTITY_NATURE = "https://www.iana.org/assignments/media-types/application/xml-external-parsed-entity";

    // The whole nature/purpose thing never got fleshed out as completely as it might have. For things
    // where there isn't a defined purpose, just use any purpose...the catalog lookup allows any
    // value to match null.
    public static final String ANY_PURPOSE = null;

    public static final String SCHEMA_NATURE = "http://www.w3.org/2001/XMLSchema";
    public static final String VALIDATION_PURPOSE = "http://www.rddl.org/purposes#validation";
    
    public static final String ANY_NATURE = null;

    /**
     *  The URI to be dereferenced. If the request was for a relative URI reference, this will
     *  be the absolute URI after resolving against the base URI if known; if no base URI is known,
     *  it will be the URI as requested.
     */
    public String uri;


    /**
     *  The base URI that was used to resolve any relative URI, if known.
     */
    public String baseUri;

    /**
     *  The relative URI that was actually requested, where applicable.
     */
    public String relativeUri;

    /**
     *  The public ID of the requested resource, where applicable
     */

    public String publicId;

    /**
     * The name of the requested resource, used when resolving entity references
     */

    public String entityName;

    /**
     * The <code>Nature</code> of the resource, as defined in the RDDL specifications (based on the <code>role</code>
     * attribute in XLink). Some of the allowed values are provided as constants, for example
     * <code>TEXT_NATURE</code>, <code>XSLT_NATURE</code>, <code>XQUERY_NATURE</code>.
     */
    public String nature;

    /**
     * The <code>Purpose</code> of the request, as defined in the RDDL specifications (based on the <code>arcrole</code>
     * attribute in XLink).
     */
    public String purpose;

    /**
     * This boolean flag is set to true when the URI takes the form of a namespace URI (rather than a location
     * hint). Specifically, it is set when resolving an <code>import module</code> declaration in XQuery when only
     * the module namespace is known, and when resolving an <code>xsl:import</code>, or an XML Schema import in XSLT
     * or XQuery, if only the target namespace of the required schema document is known.
     */

    public boolean uriIsNamespace;

    /**
     * This boolean flag is set to true when the URI identifies a document where streamed processing is
     * required. In this case the result must be a StreamSource or SAXSource
     */

    public boolean streamable;

    /**
     * If the encoding is specified out of band, it can be provided here. Requesting an encoding
     * doesn't guarantee that it'll be used. For example, HTTP headers might override it.
     */
    public String requestedEncoding;

    /**
     * Make a copy of a resource request (so that it can be modified without changing the original)
     * @return a copy of the request
     */

    public ResourceRequest copy() {
        ResourceRequest rr = new ResourceRequest();
        rr.relativeUri = relativeUri;
        rr.baseUri = baseUri;
        rr.uri = uri;
        rr.uriIsNamespace = uriIsNamespace;
        rr.publicId = publicId;
        rr.purpose = purpose;
        rr.nature = nature;
        rr.entityName = entityName;
        rr.streamable = streamable;
        rr.requestedEncoding = requestedEncoding;
        return rr;
    }

    /**
     * Resolve the request by passing it to one or more resource resolvers. The resolvers
     * are tried in turn until one of them returns a non-null result; if the final result
     * is null, this method returns null
     * @param resolvers the resource resolvers to be used
     * @return the result from the first resolver that returns a non-null result; or null
     * if none of them does so.
     */

    public Source resolve(ResourceResolver... resolvers) throws XPathException {

        String requestedUri = relativeUri;
        if (requestedUri == null) {
            requestedUri = uri;
        }
        String id = null;

        // Extract any fragment identifier. Note, this code is no longer used to
        // resolve fragment identifiers in URI references passed to the document()
        // function: the code of the document() function handles these itself.

        ResourceRequest adjustedRequest = this;
        int hash = requestedUri.indexOf('#');
        if (hash >= 0) {
            adjustedRequest = copy();
            adjustedRequest.relativeUri = requestedUri.substring(0, hash);
            id = requestedUri.substring(hash + 1);
        }


        Source resolvedSource = null;
        for (ResourceResolver resolver : resolvers) {
            if (resolver != null) {
                Source s = null;
                try {
                    s = resolver.resolve(this);
                } catch (XPathException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof IllegalArgumentException) {
                        IllegalArgumentException iae = (IllegalArgumentException)cause;
                        if (iae.getCause() instanceof URISyntaxException) {
                            throw new XPathException("Invalid URI " + uri, iae.getCause());
                        }
                        throw e;
                    } else {
                        throw e;
                    }
                }
                if (s != null) {
                    resolvedSource = s;
                    break;
                }
            }
        }
        if (resolvedSource != null) {
            if (id != null) {
                final String idFinal = id;
                FilterFactory factory = next -> new IDFilter(next, idFinal);
                resolvedSource = AugmentedSource.makeAugmentedSource(resolvedSource);
                ((AugmentedSource) resolvedSource).addFilter(factory);
            }
        }
        return resolvedSource;
    }


}


