////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.resource.ResourceLoader;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * A <code>DirectResourceResolver</code> is a {@link ResourceResolver} that resolves requests
 * using the direct, native capabilities of the platform. For example a "file:" URI is resolved
 * by finding the file in filestore, and an "http:" URI is resolved by making an HTTP request.
 */

public class DirectResourceResolver implements ResourceResolver {

    private final Configuration config;

    public DirectResourceResolver(Configuration config) {
        this.config = config;
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

        if (request.uriIsNamespace) {
            return null;  // bug 5266
        }
        ProtocolRestrictor restrictor = config.getProtocolRestrictor();
        if (!"all".equals(restrictor.toString())) {
            try {
                URI u = new URI(request.uri);
                if (!restrictor.test(u)) {
                    throw new XPathException("URIs using protocol " + u.getScheme() + " are not permitted");
                }
            } catch (URISyntaxException err) {
                throw new XPathException("Unknown URI scheme requested " + request.uri);
            }
        }

        InputStream stream;
        if (ResourceRequest.BINARY_NATURE.equals(request.nature)) {
            // This path is used by the unparsed-text resolver when no encoding is supplied;
            // it returns (where possible) a TypedStreamSource containing the binary input
            // stream, together with the content type from the HTTP headers.
            try {
                return ResourceLoader.typedStreamSource(config, request.uri);
            } catch (IOException e) {
                if (e.getMessage().contains(request.uri)) {
                    throw new XPathException("Cannot read binary resource ", e);
                }
                throw new XPathException("Cannot read " + request.uri, e);
            }
        }

        Source ss;
        try {
            // Get an input stream from the request URI
            stream = ResourceLoader.urlStream(config, request.uri);
        } catch (IOException e) {
            stream = null; // Carry on, the XML parser might know what to do with it.
        }

        if (ResourceRequest.TEXT_NATURE.equals(request.nature)) {
            // Typically happens when using unparsed-text() with an explicit encoding
            return new StreamSource(stream, request.uri);
        }

        InputSource is = new InputSource(request.uri);
        is.setByteStream(stream);
        XMLReader parser;
        if (ResourceRequest.XSLT_NATURE.equals(request.nature) || ResourceRequest.XSD_NATURE.equals(request.nature)) {
            parser = config.getStyleParser();
        } else {
            parser = config.getSourceParser();
        }
        ss = new SAXSource(parser, is);
        if (stream != null) {
            // We created the stream, so we must close it after use
            ss = AugmentedSource.makeAugmentedSource(ss);
            ((AugmentedSource) ss).setPleaseCloseAfterUse(true);
        }

        return ss;

    }


}

