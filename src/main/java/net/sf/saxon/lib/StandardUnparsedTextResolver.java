////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.query.InputStreamMarker;
import net.sf.saxon.resource.EncodingDetector;
import net.sf.saxon.resource.ParsedContentType;
import net.sf.saxon.resource.ResourceLoader;
import net.sf.saxon.resource.TypedStreamSource;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpReplaceBody;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URI;

/**
 * Default implementation of the UnparsedTextURIResolver, used if no other implementation
 * is nominated to the Configuration. This implementation
 * handles anything that the java URL class will handle, plus the <code>classpath</code>
 * URI scheme defined in the Spring framework, and the <code>data</code> URI scheme defined in
 * RFC 2397.
 */

public class StandardUnparsedTextResolver
        implements UnparsedTextURIResolver {

    private boolean debug = false;

    /**
     * Set debugging on or off. In debugging mode, information is written to System.err
     * to trace the process of deducing an encoding.
     *
     * @param debug set to true to enable debugging
     */

    public void setDebugging(boolean debug) {
        this.debug = debug;
    }

    /**
     * Resolve the URI passed to the XSLT unparsed-text() function, after resolving
     * against the base URI.
     *
     * @param absoluteURI the absolute URI obtained by resolving the supplied
     *                    URI against the base URI
     * @param encoding    the encoding requested in the call of unparsed-text(), if any. Otherwise null.
     * @param config      The configuration. Provided in case the URI resolver
     *                    needs it.
     * @return a Reader, which Saxon will use to read the unparsed text. After the text has been read,
     * the close() method of the Reader will be called.
     * @throws net.sf.saxon.trans.XPathException if any failure occurs
     * @since 8.9
     */

    @Override
    public Reader resolve(URI absoluteURI, String encoding, Configuration config) throws XPathException {

        Logger err = config.getLogger();
        if (debug) {
            err.info("unparsed-text(): processing " + absoluteURI);
            err.info("unparsed-text(): requested encoding = " + encoding);
        }
        if (!absoluteURI.isAbsolute()) {
            throw new XPathException("Resolved URI supplied to unparsed-text() is not absolute: " + absoluteURI.toString(),
                                     "FOUT1170");
        }


        ResourceRequest rr = new ResourceRequest();
        rr.uri = absoluteURI.toString();
        rr.nature = encoding == null ? ResourceRequest.BINARY_NATURE : ResourceRequest.TEXT_NATURE;
        rr.purpose = ResourceRequest.ANY_PURPOSE;
        Source resolved = rr.resolve(config.getResourceResolver(), new DirectResourceResolver(config));
        if (resolved == null) {
            throw new XPathException("unparsed-text(): failed to resolve URI " + absoluteURI);
        }

        if (resolved instanceof SAXSource) {
            return getReaderFromSAXSource((SAXSource) resolved, encoding, config, debug);
        }

        if (resolved instanceof AugmentedSource
            && ((AugmentedSource) resolved).getContainedSource() instanceof SAXSource) {
            return getReaderFromSAXSource((SAXSource) ((AugmentedSource) resolved).getContainedSource(), encoding, config, debug);
        }

        if (resolved instanceof StreamSource) {
            return getReaderFromStreamSource((StreamSource)resolved, encoding, config, debug);
        } else {
            throw new XPathException("Resolver for unparsed-text() returned non-StreamSource");
        }
    }

    @CSharpReplaceBody(code="throw new Saxon.Hej.trans.XPathException(\"SAXSources are not supported on .NET\");")
    public static Reader getReaderFromSAXSource(SAXSource source, String encoding, Configuration config, boolean debug) throws XPathException {
        Logger err = config.getLogger();
        InputStream inputStream = null;

        if (source.getInputSource() != null) {
            inputStream = source.getInputSource().getByteStream();
        }

        if (inputStream == null) {
            Reader reader = source.getInputSource().getCharacterStream();
            if (reader != null) {
                return reader;
            }

            String systemId = source.getSystemId();
            if (systemId != null) {
                try {
                    return ResourceLoader.urlReader(config, systemId, encoding);
                } catch (IOException e) {
                    throw new XPathException("unparsed-text(): cannot retrieve " + systemId, e);
                }
            } else {
                throw new XPathException("unparsed-text(): resolver returned empty StreamSource");
            }
        }

        return detectEncoding(inputStream, encoding, config, debug);
    }

    private static Reader detectEncoding(InputStream inputStream, String encoding, Configuration config, boolean debug) throws XPathException {
        try {
            if (encoding == null) {
                Logger err = config.getLogger();
                inputStream = InputStreamMarker.ensureMarkSupported(inputStream);
                encoding = EncodingDetector.inferStreamEncoding(inputStream, "utf-8", debug ? err : null);
                if (debug) {
                    err.info("unparsed-text(): inferred encoding = " + encoding);
                }
            }
        } catch (IOException e) {
            encoding = "UTF-8";
        }
        try {
            return ResourceLoader.getReaderFromStream(inputStream, encoding);
        } catch (UnsupportedEncodingException e) {
            throw new XPathException(e).withErrorCode("FOUT1200");
        }
    }

    public static Reader getReaderFromStreamSource(StreamSource source, String encoding, Configuration config,
                                                   boolean debug) throws XPathException {
        Logger err = config.getLogger();
        InputStream inputStream = source.getInputStream();

        if (inputStream == null) {
            if (source.getReader() != null) {
                return source.getReader();
            } else {
                String systemId = source.getSystemId();
                if (systemId != null) {
                    try {
                        return ResourceLoader.urlReader(config, systemId, encoding);
                    } catch (IOException e) {
                        throw new XPathException("unparsed-text(): cannot retrieve " + systemId, e);
                    }
                } else {
                    throw new XPathException("unparsed-text(): resolver returned empty StreamSource");
                }
            }
        }

        // If we get here, then we've only got an InputStream to work with, so there's no
        // external encoding information or media type. So we use the requested
        // encoding if supplied; otherwise "heuristics" (peeking at the start of the stream),
        // or failing that, UTF-8.

        // Use the contentType from the HTTP header if available
        String contentType = null;
        if (source instanceof TypedStreamSource) {
            contentType = ((TypedStreamSource) source).getContentType();
            if (contentType != null) {
                ParsedContentType parsedContentType = new ParsedContentType(contentType);
                String contentEncoding = parsedContentType.encoding;
                if (contentEncoding != null) {
                    encoding = contentEncoding;
                }
                if (encoding == null) {
                    try {
                        encoding = EncodingDetector.inferStreamEncoding(inputStream, "UTF-8", null);
                    } catch (IOException e) {
                        throw new XPathException("Unable to infer encoding", e);
                    }
                }
            }
            if (encoding != null) {
                try {
                    return ResourceLoader.getReaderFromStream(inputStream, encoding);
                } catch (UnsupportedEncodingException e) {
                    throw new XPathException(e);
                }
            }
        }

        return detectEncoding(inputStream, encoding, config, debug);
    }

    @CSharpReplaceBody(code="throw new Saxon.Hej.trans.XPathException(\"classpath: URI scheme is not supported on .NET\");")
    private static InputStream openClasspathResource(Configuration config, String systemId) throws XPathException {
        return config.getClass().getClassLoader().getResourceAsStream(systemId.substring(10));
    }


//    @CSharpReplaceBody(code = "return new System.IO.StreamReader(stream, System.Text.Encoding.GetEncoding(encoding));")
//    public static Reader makeReaderFromStream(InputStream stream, String encoding) throws XPathException {
//        // The following is necessary to ensure that encoding errors are not recovered.
//        try {
//            Charset charset2 = Charset.forName(encoding);
//            CharsetDecoder decoder = charset2.newDecoder()
//                    .onMalformedInput(CodingErrorAction.REPORT)
//                    .onUnmappableCharacter(CodingErrorAction.REPORT);
//            return new BufferedReader(new InputStreamReader(stream, decoder));
//        } catch (IllegalCharsetNameException | UnsupportedCharsetException icne) {
//            throw new XPathException("Invalid encoding name: " + encoding, "FOUT1190");
//        }
//    }


}

