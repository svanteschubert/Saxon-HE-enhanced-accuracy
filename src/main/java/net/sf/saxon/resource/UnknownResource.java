////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.URIQueryParameters;
import net.sf.saxon.lib.Resource;
import net.sf.saxon.lib.ResourceFactory;
import net.sf.saxon.om.Item;
import net.sf.saxon.query.InputStreamMarker;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

/**
 * The class is an implementation of the generic Resource object (typically an item in a collection)
 * representing a resource whose type is not yet known - typically because it uses an unregistered
 * file extension. We attempt to establish a type for the resource when it is opened, by "sniffing" the content.
 */

public class UnknownResource implements Resource {
    private final Configuration config;
    private final XPathContext context;
    private final AbstractResourceCollection.InputDetails details;

    public static final ResourceFactory FACTORY = CSharp.constructorRef(UnknownResource::new, 2);

    public UnknownResource(XPathContext context, AbstractResourceCollection.InputDetails details) {
        this.config = context.getConfiguration();
        this.context = context;
        this.details = details;
    }

    @Override
    public String getResourceURI() {
        return details.resourceUri;
    }

    /**
     * Get an item representing the resource: in this case a document node for the XML document.
     *
     * @return the document; or null if there is an error and the error is to be ignored
     * @throws XPathException if (for example) XML parsing fails
     */

    @Override
    public Item getItem() throws XPathException {
        InputStream stream;
        if (details.binaryContent != null) {
            stream = new ByteArrayInputStream(details.binaryContent);
        } else {
            try {
                stream = details.getInputStream(config);
            } catch (IOException e) {
                if (details.onError == URIQueryParameters.ON_ERROR_FAIL) {
                    throw new XPathException(e);
                } else {
                    return null;
                }
            }
        }
        if (stream == null) {
            throw new XPathException("Unable to dereference resource URI " + details.resourceUri);
        }
        String mediaType;
        try {
            stream = InputStreamMarker.ensureMarkSupported(stream);
            mediaType = URLConnection.guessContentTypeFromStream(stream);
        } catch (IOException e) {
            mediaType = null;
        }
        if (mediaType == null) {
            mediaType = context.getConfiguration().getMediaTypeForFileExtension("");
        }
        if (mediaType == null || mediaType.equals("application/unknown")) {
            mediaType = "application/binary";
        }
        details.contentType = mediaType;
        details.binaryContent = BinaryResource.readBinaryFromStream(stream, details.resourceUri);
        ResourceFactory delegee = context.getConfiguration().getResourceFactoryForMediaType(mediaType);
        Resource actual = delegee.makeResource(context, details);
        return actual.getItem();
    }

    /**
     * Get the media type (MIME type) of the resource if known
     *
     * @return the string "application/xml"
     */

    @Override
    public String getContentType() {
        return "application/xml";
    }


}
