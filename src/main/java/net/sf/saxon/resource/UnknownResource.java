////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.Resource;
import net.sf.saxon.lib.ResourceFactory;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;

import java.io.BufferedInputStream;
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

    private Configuration config;
    private AbstractResourceCollection.InputDetails details;

    public static final ResourceFactory FACTORY = UnknownResource::new;

    public UnknownResource(Configuration config, AbstractResourceCollection.InputDetails details) {
        this.config = config;
        this.details = details;
    }

    @Override
    public String getResourceURI() {
        return details.resourceUri;
    }

    /**
     * Get an item representing the resource: in this case a document node for the XML document.
     *
     * @param context the XPath evaluation context
     * @return the document; or null if there is an error and the error is to be ignored
     * @throws XPathException if (for example) XML parsing fails
     */

    @Override
    public Item getItem(XPathContext context) throws XPathException {
        InputStream stream;
        if (details.binaryContent != null) {
            stream = new ByteArrayInputStream(details.binaryContent);
        } else {
            try {
                stream = details.getInputStream();
            } catch (IOException e) {
                throw new XPathException(e);
            }
        }
        if (stream == null) {
            throw new XPathException("Unable to dereference resource URI " + details.resourceUri);
        }
        String mediaType;
        try {
            if (!stream.markSupported()) {
                stream = new BufferedInputStream(stream);
            }
            mediaType = URLConnection.guessContentTypeFromStream(stream);
        } catch (IOException e) {
            mediaType = null;
        }
        if (mediaType == null) {
            mediaType = config.getMediaTypeForFileExtension("");
        }
        if (mediaType == null || mediaType.equals("application/unknown")) {
            mediaType = "application/binary";
        }
        details.contentType = mediaType;
        details.binaryContent = BinaryResource.readBinaryFromStream(stream, details.resourceUri);
        ResourceFactory delegee = config.getResourceFactoryForMediaType(mediaType);
        Resource actual = delegee.makeResource(config, details);
        return actual.getItem(context);
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
