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
import net.sf.saxon.lib.StandardUnparsedTextResolver;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;

/**
 * This class implements th interface Resource. We handle unparded text here.
 * The Resource objects belong to a collection
 * It is used to support the fn:collection() and fn:uri-collection() functions.
 *
 * @since 9.7
 */
public class UnparsedTextResource implements Resource {
    private String contentType;
    private String encoding;
    private String href;
    private String unparsedText = null;

    /**
     * Create an UnparsedTextResource
     *
     * @param details information about the input
     * @throws XPathException for an unsupported encoding
     */

    public UnparsedTextResource(AbstractResourceCollection.InputDetails details) throws XPathException {
        this.href = details.resourceUri;
        this.contentType = details.contentType;
        this.encoding = details.encoding;
        if (details.characterContent != null) {
            unparsedText = details.characterContent;
        } else if (details.binaryContent != null) {
            if (details.encoding == null) {
                try {
                    InputStream is = new ByteArrayInputStream(details.binaryContent);
                    details.encoding = StandardUnparsedTextResolver.inferStreamEncoding(is, null);
                    is.close();
                } catch (IOException e) {
                    throw new XPathException(e); // cannot happen
                }
            }
            try {
                this.unparsedText = new String(details.binaryContent, details.encoding);
            } catch (UnsupportedEncodingException e) {
                throw new XPathException(e);
            }
        }
    }

    public final static ResourceFactory FACTORY = new ResourceFactory() {
        @Override
        public Resource makeResource(Configuration config, AbstractResourceCollection.InputDetails details) throws XPathException {
            return new UnparsedTextResource(details);
        }
    };

    @Override
    public String getResourceURI() {
        return href;
    }

    public String getEncoding() {
        return encoding;
    }

    public String getContent() throws XPathException {
        if (unparsedText == null) {
            try {
                URL url = new URL(href);
                URLConnection connection = url.openConnection();
                InputStream stream = connection.getInputStream();
                StringBuilder builder = null;

                String enc = encoding;
                if (enc == null) {
                    enc = StandardUnparsedTextResolver.inferStreamEncoding(stream, null);
                }
                builder = CatalogCollection.makeStringBuilderFromStream(stream, enc);
                unparsedText = builder.toString();
            } catch (IOException e) {
                throw new XPathException(e);
            }
        }
        return unparsedText;
    }

    @Override
    public Item getItem(XPathContext context) throws XPathException {
        return new StringValue(getContent());
    }

    /**
     * Get the media type (MIME type) of the resource if known
     *
     * @return the string "text/plain"
     */

    @Override
    public String getContentType() {
        return contentType == null ? "text/plain" : contentType;
    }


}
