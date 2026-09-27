////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.EntityResolver2;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.net.URISyntaxException;

/**
 * An implementation of the SAX {@link EntityResolver2} interface implemented by calling
 * a supplied {@link ResourceResolver}
 */
public class EntityResolverWrappingResourceResolver implements EntityResolver2 {

    private final ResourceResolver resourceResolver;

    /**
     * Create an {@link EntityResolver2} by wrapping a {@link ResourceResolver}
     * @param resolver the {@link ResourceResolver} to be wrapped
     */

    public EntityResolverWrappingResourceResolver(ResourceResolver resolver) {
        this.resourceResolver = resolver;
    }

    @Override
    public InputSource getExternalSubset(String name, String baseURI) throws SAXException {
        return null;
    }

    @Override
    public InputSource resolveEntity(String name, String publicId, String baseURI, String systemId) throws SAXException, IOException {
        if (resourceResolver instanceof EntityResolver2) {
            // shortcut
            return ((EntityResolver2)resourceResolver).resolveEntity(name, publicId, baseURI, systemId);
        }
        ResourceRequest request = new ResourceRequest();
        request.baseUri = baseURI;
        request.entityName = name;
        request.publicId = publicId;
        request.nature = ResourceRequest.EXTERNAL_ENTITY_NATURE;
        request.purpose = ResourceRequest.ANY_PURPOSE;

        try {
            if (baseURI == null) {
                request.uri = systemId;
            } else {
                request.baseUri = baseURI;
                request.uri = ResolveURI.makeAbsolute(systemId, baseURI).toString();
            }
        } catch (URISyntaxException e) {
            throw new IOException("Cannot resolve URI supplied for entity resolution", e);
        }


        Source resolved;
        try {
            resolved = resourceResolver.resolve(request);
        } catch (XPathException e) {
            throw new SAXException(e);
        }
        if (resolved == null) {
            return null;
        }
        if (resolved instanceof StreamSource) {
            InputSource is = new InputSource(resolved.getSystemId());
            if (((StreamSource) resolved).getInputStream() != null) {
                is.setByteStream(((StreamSource) resolved).getInputStream());
            }
            if (((StreamSource) resolved).getReader() != null) {
                is.setCharacterStream(((StreamSource) resolved).getReader());
            }
            return is;
        }
        if (resolved instanceof SAXSource) {
            return ((SAXSource) resolved).getInputSource();
        }
        throw new SAXException("Unexpected Source type in resolveEntity()");
    }

    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        return resolveEntity(null, publicId, null, systemId);
    }
}

