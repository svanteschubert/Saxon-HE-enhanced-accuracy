////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;

/**
 * Represents a content type as it appears in HTTP headers, parsed to extract
 * two properties: (a) a boolean indicating whether the media type is XML, and
 * (b) the character encoding.
 */

public class ParsedContentType {

    public boolean isXmlMediaType;
    public String encoding;

    public ParsedContentType(String contentType) {
        String mediaType;
        int pos = contentType.indexOf(';');
        if (pos >= 0) {
            mediaType = contentType.substring(0, pos);
        } else {
            mediaType = contentType;
        }
        mediaType = mediaType.trim();
        isXmlMediaType = (mediaType.startsWith("application/") || mediaType.startsWith("text/")) &&
                (mediaType.endsWith("/xml") || mediaType.endsWith("+xml"));

        String charset = "";
        pos = contentType.toLowerCase().indexOf("charset");
        if (pos >= 0) {
            pos = contentType.indexOf('=', pos + 7);
            if (pos >= 0) {
                charset = contentType.substring(pos + 1);
            }
            if ((pos = charset.indexOf(';')) > 0) {
                charset = charset.substring(0, pos);
            }

            // attributes can have comment fields (RFC 822)
            if ((pos = charset.indexOf('(')) > 0) {
                charset = charset.substring(0, pos);
            }
            // ... and values may be quoted
            if ((pos = charset.indexOf('"')) > 0) {
                charset = charset.substring(pos + 1,
                                            charset.indexOf('"', pos + 2));
            }
            encoding = charset.trim();
        }
    }
}

