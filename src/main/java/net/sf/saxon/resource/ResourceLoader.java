////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;

import net.sf.saxon.Configuration;

import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.HashSet;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

import static net.sf.saxon.resource.EncodingDetector.inferStreamEncoding;

/**
 * The class provides a static method for loading resources from a URL.
 * This method follows HTTP 301 and 302 redirects.
 */

public class ResourceLoader {
    /**
     * The maximum number of redirects to follow before throwing an IOException.
     * If you allow the underlying Java URL class to follow redirects, it gives
     * up after 20 hops.
     */
    public static int MAX_REDIRECTS = 20;

    /**
     * Open a URLConnection to the resource identified by the URI. For HTTP URIs, this
     * method will follow up to MAX_REDIRECTS redirects or until it detects a loop;
     * the connection returned in this case is to the first resource that did not
     * return a 301 or 302 response code.
     *
     * @param url The URL to retrieve.
     * @return An InputStream for the resource content.
     * @throws IOException If more than MAX_REDIRECTS are occur or if a loop is detected.
     */

    public static URLConnection urlConnection(URL url) throws IOException {
        if ("http".equals(url.getProtocol()) || "https".equals(url.getProtocol())) {
            HashSet<String> visited = new HashSet<>();
            String cookies = null;
            int count = MAX_REDIRECTS;
            for (;;) {
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("Accept-Encoding", "gzip");
                if (cookies != null) {
                    conn.setRequestProperty("Cookie", cookies);
                }

                int status = conn.getResponseCode();
                if (status == HttpURLConnection.HTTP_MOVED_PERM
                        || status == HttpURLConnection.HTTP_MOVED_TEMP) {
                    String location = conn.getHeaderField("Location");
                    url = new URL(url, location);
                    cookies = conn.getHeaderField("Set-Cookie");

                    if (visited.contains(location)) {
                        throw new IOException("HTTP redirect loop through " + location);
                    }
                    visited.add(location);

                    count -= 1;
                    if (count < 0) {
                        throw new IOException("HTTP redirects more than " + MAX_REDIRECTS + " times");
                    }
                } else {
                    return conn;
                }
            }
        } else {
            return url.openConnection();
        }
    }

    /**
     * Open a stream to retrieve the content identified by the URI. If the URI is a classpath:
     * URI, then it will be retrieved with the configuration's dynamic loader. For HTTP URIs, this
     * method will follow up to MAX_REDIRECTS redirects or until it detects a loop.
     * This method automatically accepts and decompresses gzip encoded responses.
     *
     * @param config The current configuration.
     * @param url The URL to retrieve.
     * @return An InputStream for the resource content.
     * @throws IOException If more than MAX_REDIRECTS are occur or if a loop is detected.
     */
    public static InputStream urlStream(Configuration config, String url) throws IOException  {
        if (config != null && url.startsWith("classpath:")) {
            final String path;
            if (url.length() > 10 && url.charAt(10) == '/') {
                path = url.substring(11);
            } else {
                path = url.substring(10);
            }
            return config.getDynamicLoader().getResourceAsStream(path);
        } else {
            URLConnection conn = ResourceLoader.urlConnection(new URL(url));
            InputStream inputStream =  conn.getInputStream();
            String contentEncoding = conn.getContentEncoding();
            if ("gzip".equals(contentEncoding)) {
                inputStream = new GZIPInputStream(inputStream);
            }
            return inputStream;
        }
    }

    public static StreamSource typedStreamSource(Configuration config, String url) throws IOException {
        if (config != null && url.startsWith("classpath:")) {
            return new StreamSource(urlStream(config, url), url);
        } else {
            URLConnection conn = ResourceLoader.urlConnection(new URL(url));
            InputStream inputStream = conn.getInputStream();
            if ("gzip".equals(conn.getContentEncoding())) {
                inputStream = new GZIPInputStream(inputStream);
            }
            if (!inputStream.markSupported()) {
                inputStream = new BufferedInputStream(inputStream);
            }
            TypedStreamSource tss = new TypedStreamSource();
            tss.setInputStream(inputStream);
            tss.setContentType(conn.getContentType());
            tss.setSystemId(url);
            return tss;
        }
    }

    /**
     * Open a reader to retrieve the content identified by the URI. This handles HTTP
     * redirects in the same way as {@link #urlStream}, but then it also wraps
     * the stream in a Reader, using the logic prescribed for the {@code fn:unparsed-text}
     * function.
     *
     * @param url The URL to retrieve.
     * @param requestedEncoding The requested encoding. This is used only as a fallback, following
     *                 the rules of the {@code fn:unparsed-text} specification
     * @return A Reader for the resource content.
     * @throws IOException If more than MAX_REDIRECTS are occur or if a loop is detected.
     */
    public static Reader urlReader(Configuration config, String url, String requestedEncoding) throws IOException {
        String resourceEncoding = null;
        // Get any external (HTTP) requestedEncoding label.
        boolean isXmlMediaType = false;

        URLConnection conn = null;
        InputStream inputStream = null;
        if (config != null && url.startsWith("classpath:")) {
            inputStream = ResourceLoader.urlStream(config, url);
        } else {
            conn = ResourceLoader.urlConnection(new URL(url));
            inputStream = conn.getInputStream();
            String contentEncoding = conn.getContentEncoding();
            if ("gzip".equals(contentEncoding)) {
                inputStream = new GZIPInputStream(inputStream);
            }
        }

        if (!inputStream.markSupported()) {
            inputStream = new BufferedInputStream(inputStream);
        }

        // If conn was used and the url isn't a file: URI, try to get encoding information from it
        if (conn != null && !url.startsWith("file:")) {
            // Use the contentType from the HTTP header if available, and parse it
            String contentType = conn.getContentType();
            if (contentType != null) {
                ParsedContentType parsedContentType = new ParsedContentType(contentType);
                isXmlMediaType = parsedContentType.isXmlMediaType;
                resourceEncoding = parsedContentType.encoding;
            }
        }

        try {
            if (requestedEncoding == null) {
                requestedEncoding = "UTF-8";
            }
            if (resourceEncoding == null || isXmlMediaType) {
                resourceEncoding = inferStreamEncoding(inputStream, requestedEncoding, null);
            }
        } catch (IOException e) {
            resourceEncoding = "UTF-8";
        }

        assert resourceEncoding != null;

        return getReaderFromStream(inputStream, resourceEncoding);
    }

    /**
     * Get a reader corresponding to a binary input stream and an encoding. The mapping is such that
     * any encoding errors that are detected lead to a fatal error, rather than being repaired or ignored
     * @param inputStream the input stream. Non-null.
     * @param resourceEncoding the encoding. Non-null
     * @return a corresponding reader.
     * @throws UnsupportedEncodingException if there's a problem with the encoding
     */

    public static BufferedReader getReaderFromStream(InputStream inputStream, String resourceEncoding) throws UnsupportedEncodingException {
        try {
            Objects.requireNonNull(inputStream);
            Objects.requireNonNull(resourceEncoding);
            Charset charset2 = Charset.forName(resourceEncoding);
            // ensure that encoding errors are not recovered
            CharsetDecoder decoder = charset2.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return new BufferedReader(new InputStreamReader(inputStream, decoder));
        } catch (Exception e) {
            throw new UnsupportedEncodingException("Unable to get reader with encoding: " + resourceEncoding);
        }
    }


}
