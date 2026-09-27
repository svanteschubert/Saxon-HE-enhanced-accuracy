////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.trans.XPathException;
import org.xml.sax.InputSource;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Objects;

/**
 * A {@link ResourceResolver} implemented by wrapping a supplied {@link URIResolver}
 */

public class ResourceResolverWrappingURIResolver implements ResourceResolver {

    private final URIResolver uriResolver;

    /**
     * Create a {@link ResourceResolver} by wrapping a supplied {@link URIResolver}
     * @param uriResolver the {@link URIResolver} to which this {@link ResourceResolver} will delegate
     */
    public ResourceResolverWrappingURIResolver(URIResolver uriResolver) {
        Objects.requireNonNull(uriResolver);
        this.uriResolver = uriResolver;
    }

    /**
     * Process a resource request to deliver a resource
     *
     * @param request the resource request
     * @return the returned Source; or null to delegate resolution to another resolver
     * @throws XPathException if the request is invalid in some way, or if the identified resource is unsuitable,
     *                        or if resolution is to fail rather than being delegated to another resolver.
     */
    @Override
    public Source resolve(ResourceRequest request) throws XPathException {
        String href;
        if (request.relativeUri != null && request.baseUri != null) {
            href = request.relativeUri;
        } else if (request.uri != null) {
            href = request.uri;
        } else {
            return null;
        }
        try {
            Source resolved = uriResolver.resolve(href, request.baseUri);
            if (ResourceRequest.TEXT_NATURE.equals(request.nature)
                    && resolved instanceof SAXSource
                    && ((SAXSource) resolved).getInputSource() != null) {
                // bug 5582. When unparsed text is requested, the resource resolver
                // shouldn't return a SAXSource, but a legacy URIResolver may do so
                resolved = convertToStreamSource((SAXSource) resolved);
            }
            return resolved;
        } catch (TransformerException e) {
            throw XPathException.makeXPathException(e);
        }
    }

    /**
     * Convert SAXSource to StreamSource. The XML Reader is ignored, since we're concerned here with non-XML resources.
     * @param resolved the SAXSource to be converted
     * @return an equivalent StreamSource, ignoring any XML Reader.
     */

    public static StreamSource convertToStreamSource(SAXSource resolved) {
        InputSource is = resolved.getInputSource();

        StreamSource ss = new StreamSource();
        ss.setInputStream(is.getByteStream());
        ss.setReader(is.getCharacterStream());
        ss.setSystemId(is.getSystemId());
        ss.setPublicId(is.getPublicId());
        if (is.getEncoding() != null && is.getByteStream() != null && is.getCharacterStream() == null) {
            try {
                ss.setReader(new InputStreamReader(is.getByteStream(), is.getEncoding()));
            } catch (UnsupportedEncodingException e) {
                // no action, we may fail later
            }
        }
        return ss;
    }
    
    public URIResolver getWrappedURIResolver() {
        return uriResolver;
    }
}


