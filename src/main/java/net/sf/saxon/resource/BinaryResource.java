////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;


import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.Resource;
import net.sf.saxon.lib.ResourceFactory;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Base64BinaryValue;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

/**
 * A binary resource that might appear in a resource collection.
 */


public class BinaryResource implements Resource {

    private final String href;
    private final String contentType;
    private byte[] data;
    private URLConnection connection = null;

    /**
     * ResourceFactory suitable for creating a BinaryResource
     */

    public static final ResourceFactory FACTORY = (config, details) -> new BinaryResource(details);

    /**
     * Create a binary resource
     *
     * @param in details about the resource
     */

    public BinaryResource(AbstractResourceCollection.InputDetails in) {
        this.contentType = in.contentType;
        this.href = in.resourceUri;
        this.data = in.binaryContent;
    }

    /**
     * Create a binary resource supplying the actual content as a byte array
     * @param href the URI of the resource
     * @param contentType the media type
     * @param content the actual content as a byte array
     */

    public BinaryResource(String href, String contentType, byte[] content) {
        this.contentType = contentType;
        this.href = href;
        this.data = content;
    }

    /**
     * Set the content of the resource as an array of bytes
     *
     * @param data the content of the resource
     */

    public void setData(byte[] data) {
        this.data = data;
    }

    /**
     * Get the content of the resource as an array of bytes
     *
     * @return the content (if it has been set using setData())
     */

    public byte[] getData() {
        return data;
    }

    /**
     * Get the URI of the resource
     *
     * @return the URI of the resource
     */

    @Override
    public String getResourceURI() {
        return href;
    }

    private byte[] readBinaryFromConn(URLConnection con) throws XPathException {
        InputStream raw = null;
        this.connection = con;
        try {
            raw = connection.getInputStream();

            int contentLength = connection.getContentLength();
            InputStream in = new BufferedInputStream(raw);
            if (contentLength < 0) {
                // bug 4475
                byte[] result = readBinaryFromStream(in, connection.getURL().getPath());
                in.close();
                return result;
            } else {
                byte[] data = new byte[contentLength];
                int bytesRead = 0;
                int offset = 0;
                while (offset < contentLength) {
                    bytesRead = in.read(data, offset, data.length - offset);
                    if (bytesRead == -1) {
                        break;
                    }
                    offset += bytesRead;
                }
                in.close();

                if (offset != contentLength) {
                    throw new XPathException("Only read " + offset + " bytes; Expected " + contentLength + " bytes");
                }
                return data;
            }
        } catch (IOException e) {
            throw new XPathException(e);
        }
    }

    /**
     * Utility method to construct an array of bytes from the content of an InputStream
     *
     * @param in   the input stream. The method consumes the input stream but does not close it.
     * @param path file name or URI used only for diagnostics
     * @return byte array representing the content of the InputStream
     * @throws XPathException if a failure occurs obtaining a connection or reading the stream
     */

    public static byte[] readBinaryFromStream(InputStream in, String path) throws XPathException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[16384];

        try {
            while ((nRead = in.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new XPathException("Failed to read: " + path + " " + e);
        }
    }

    /**
     * Get an XDM Item holding the contents of this resource.
     *
     * @param context the XPath evaluation context
     * @return an item holding the contents of the resource. For a binary resource
     * the value will always be a {@link Base64BinaryValue}. This does not mean that the
     * content is actually encoded in Base64 internally; rather it means that when converted
     * to a string, the content is presented in Base64 encoding.
     * @throws XPathException if a failure occurs materializing the resource
     */

    @Override
    public Base64BinaryValue getItem(XPathContext context) throws XPathException {
        if (data != null) {
            return new Base64BinaryValue(data);
        } else if (connection != null) {
            data = readBinaryFromConn(connection);
            return new Base64BinaryValue(data);
        } else {
            try {
                URL url = new URI(href).toURL();
                connection = url.openConnection();
                data = readBinaryFromConn(connection);
                return new Base64BinaryValue(data);
            } catch (URISyntaxException | IOException e) {
                throw new XPathException(e);
            }
        }

    }

    /**
     * Get the media type (MIME type) of the resource if known,
     * for example "image/jpeg".
     *
     * @return the media type if known; otherwise null
     */

    @Override
    public String getContentType() {
        return contentType;
    }

}
