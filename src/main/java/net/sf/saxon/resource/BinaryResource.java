////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;


import net.sf.saxon.lib.Resource;
import net.sf.saxon.lib.ResourceFactory;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.value.Base64BinaryValue;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;

/**
 * A binary resource that might appear in a resource collection. Currently limited to 2G octets.
 */

public class BinaryResource implements Resource {

    private final String href;
    private final String contentType;
    private byte[] data;
    private URLConnection connection = null;

    /**
     * ResourceFactory suitable for creating a BinaryResource
     */

    public static final ResourceFactory FACTORY = (context, details) -> new BinaryResource(details);

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
     *
     * @param href        the URI of the resource
     * @param contentType the media type
     * @param content     the actual content as a byte array
     */

    public BinaryResource(String href, String contentType, byte[] content) {
        this.contentType = contentType;
        this.href = href;
        this.data = content;
    }

    @CSharpReplaceBody(code = "return Saxon.Impl.Helpers.StringUtils.encode(s, encoding);")
    public static byte[] encode(String s, String encoding) throws XPathException {
        CharsetEncoder encoder;
        try {
            encoder = Charset.forName(encoding).newEncoder();
        } catch (Exception e) {
            throw new XPathException("Unsupported encoding " + encoding);
        }
        encoder.onMalformedInput(CodingErrorAction.REPORT);

        CharBuffer in = CharBuffer.wrap(s);
        ByteBuffer out = null;
        try {
            out = encoder.encode(in);
        } catch (MalformedInputException e) {
            error("Malformed input in encoding:" + e);
        } catch (UnmappableCharacterException e) {
            error("Unmappable input in encoding:" + e);
        } catch (CharacterCodingException e) {
            error("Character code problem in encoding:" + e);
        }
        byte[] data = new byte[out.limit()];
        System.arraycopy(out.array(), 0, data, 0, out.limit());
        return data;
    }

    public static String decode(byte[] value, String encoding) throws XPathException {
        return decode(value, 0, value.length, encoding);
    }

    @CSharpReplaceBody(code = "return Saxon.Impl.Helpers.StringUtils.decode(value, offset, len, encoding);")
    public static String decode(byte[] value, int offset, int len, String encoding) throws XPathException {
        CharsetDecoder decoder;
        try {
            decoder = Charset.forName(encoding).newDecoder();
        } catch (Exception e) {
            throw new XPathException("Unsupported encoding " + encoding);
        }
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        ByteBuffer in = ByteBuffer.wrap(value, offset, len);
        char[] outChars = new char[len];
        CharBuffer out = CharBuffer.wrap(outChars);
        CoderResult res = decoder.decode(in, out, true);
        if (res.isError()) {
            if (res.isMalformed())
                error("Malformed input found when decoding binary resource");
            if (res.isUnmappable())
                error("Unmappable input found when decoding binary resource");
            error("Other error when decoding binary resource");
        }
        char[] resChars = new char[out.position()];
        System.arraycopy(outChars, 0, resChars, 0, out.position());
        return new String(resChars);
    }

    /**
     * Throw an error
     *
     * @param message the error message
     * @throws XPathException always
     */

    public static void error(String message)
            throws XPathException {
        throw new XPathException(message);
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

            long contentLength = connection.getContentLengthLong();
            if (contentLength > Integer.MAX_VALUE) {
                throw new XPathException("Cannot handle binary resources longer than 2G octets");
            }
            InputStream in = new BufferedInputStream(raw);
            if (contentLength < 0) {
                // bug 4475
                byte[] result = readBinaryFromStream(in, connection.getURL().getPath());
                in.close();
                return result;
            } else {
                byte[] data = new byte[(int)contentLength];
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
     * @return an item holding the contents of the resource. For a binary resource
     * the value will always be a {@link Base64BinaryValue}. This does not mean that the
     * content is actually encoded in Base64 internally; rather it means that when converted
     * to a string, the content is presented in Base64 encoding.
     * @throws XPathException if a failure occurs materializing the resource
     */

    @Override
    public Base64BinaryValue getItem() throws XPathException {
        if (data != null) {
            return new Base64BinaryValue(data);
        } else if (connection != null) {
            data = readBinaryFromConn(connection);
            return new Base64BinaryValue(data);
        } else {
            try {
                connection = ResourceLoader.urlConnection(new URI(href).toURL());
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
