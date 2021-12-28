////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;

import net.sf.saxon.functions.URIQueryParameters;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Resource;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Base64BinaryValue;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * This class handles URIs using the data: URI scheme defined in RFC 2397
 */

public class DataURIScheme {

    /**
     * Parse the content of a URI that uses the data: URI scheme defined in RFC 2397, and
     * return a Resource representing the content of the URI
     *
     * @param uri a valid URI using the data: URI scheme
     * @return either a BinaryResource (if the URI specifies "base64") or an UnparsedTextResource
     * (otherwise) representing the data part of the URI (the part after the comma)
     * @throws XPathException if the URI is invalid, or uses an unknown encoding, or cannot be decoded
     */

    public static Resource decode(URI uri) throws XPathException {
        assert uri.getScheme().equals("data");
        String path = uri.getSchemeSpecificPart();
        int comma = path.indexOf(',');
        if (comma < 0) {
            throw new IllegalArgumentException("Missing comma in data URI");
        }
        String header = path.substring(0, comma);
        String content = path.substring(comma + 1);
        boolean isBase64 = header.endsWith(";base64");
        String contentType = header.substring(0, isBase64 ? comma - 7 : comma);
        if (isBase64) {
            try {
                byte[] octets = Base64BinaryValue.decode(content);
                BinaryResource resource =
                        new BinaryResource(uri.toString(), contentType, octets);
                resource.setData(octets);
                return resource;
            } catch (XPathException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
        } else {
            String encoding = getEncoding(contentType);
            if (encoding == null) {
                encoding = "US-ASCII";
            }
            byte[] utf8content = content.getBytes(StandardCharsets.UTF_8);
            AbstractResourceCollection.InputDetails details = new AbstractResourceCollection.InputDetails();
            details.resourceUri = uri.toString();
            details.contentType = getMediaType(contentType);
            details.encoding = encoding;
            details.binaryContent = utf8content;
            details.onError = URIQueryParameters.ON_ERROR_FAIL;
            details.parseOptions = new ParseOptions();
            return new UnparsedTextResource(details);
        }
    }

    private static String getMediaType(String contentType) {
        int semicolon = contentType.indexOf(';');
        if (semicolon < 0) {
            return contentType;
        } else {
            return contentType.substring(0, semicolon);
        }
    }

    private static String getEncoding(String contentType) {
        String[] parts = contentType.split(";");
        for (String part : parts) {
            if (part.startsWith("charset=")) {
                return part.substring(8);
            }
        }
        return null;
    }
}

