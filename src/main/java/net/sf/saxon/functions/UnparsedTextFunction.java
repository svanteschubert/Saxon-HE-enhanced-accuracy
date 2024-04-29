////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.DirectResourceResolver;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.ResourceRequest;
import net.sf.saxon.lib.StandardUnparsedTextResolver;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.str.*;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpInnerClass;
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.transpile.CSharpReplaceException;
import net.sf.saxon.z.IntPredicateProxy;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;


/**
 * Abstract superclass containing common code supporting the functions
 * unparsed-text(), unparsed-text-lines(), and unparsed-text-available()
 */

public abstract class UnparsedTextFunction extends SystemFunction {

    @Override
    public int getSpecialProperties(Expression[] arguments) {
        int p = super.getSpecialProperties(arguments);
        if (getRetainedStaticContext().getConfiguration().getBooleanProperty(Feature.STABLE_UNPARSED_TEXT)) {
            return p;
        } else {
            return p & ~StaticProperty.NO_NODES_NEWLY_CREATED;
            // Pretend the function is creative to prevent the result going into a global variable,
            // which takes excessive memory. Unless we're caching anyway, for stability reasons.
        }
    }


    /**
     * Supporting routine to load one external file given a URI (href) and a baseURI
     * @param absoluteURI the absolutized URI
     * @param encoding the character encoding
     * @param output the consumer to which the contents of the file will be sent
     * @param context the XPath dynamic context
     * @throws XPathException if the file cannot be read
     */

    public static void readFile(URI absoluteURI, String encoding, UniStringConsumer output, XPathContext context)
            throws XPathException {

        final Configuration config = context.getConfiguration();
        IntPredicateProxy checker = config.getValidCharacterChecker();

        // Use the URI machinery to validate and resolve the URIs

        Reader reader;
        try {
            reader = context.getController().getUnparsedTextURIResolver().resolve(absoluteURI, encoding, config);
        } catch (XPathException err) {
            err.maybeSetErrorCode("FOUT1170");
            throw err;
        }
        if (reader == null) {
            ResourceRequest request = new ResourceRequest();
            request.uri = absoluteURI.toString();
            request.nature = ResourceRequest.TEXT_NATURE;
            Source src = request.resolve(config.getResourceResolver(), new DirectResourceResolver(config));
            if (src instanceof StreamSource) {
                reader = StandardUnparsedTextResolver.getReaderFromStreamSource((StreamSource)src, encoding, config, false);
            } else {
                throw new XPathException("unparsed-text(): resolver returned non-StreamSource");
            }
        }
        try {
            readFile(checker, reader, output);
        } catch (java.io.UnsupportedEncodingException encErr) {
            throw new XPathException("Unknown encoding " + Err.wrap(encoding), encErr)
                    .withErrorCode("FOUT1190");
        } catch (java.io.IOException ioErr) {
//            System.err.println("ProxyHost: " + System.getProperty("http.proxyHost"));
//            System.err.println("ProxyPort: " + System.getProperty("http.proxyPort"));
            throw handleIOError(absoluteURI, ioErr);
        }
    }

    public static URI getAbsoluteURI(String href, String baseURI, XPathContext context) throws XPathException {
        URI absoluteURI;
        try {
            absoluteURI = ResolveURI.makeAbsolute(href, baseURI);
        } catch (java.net.URISyntaxException err) {
            handleURISyntaxException(href, baseURI, err);
            return null;
        }
        if (absoluteURI.getFragment() != null) {
            throw new XPathException("URI for unparsed-text() must not contain a fragment identifier", "FOUT1170");
        }

        // The URL dereferencing classes throw all kinds of strange exceptions if given
        // ill-formed sequences of %hh escape characters. So we do a sanity check that the
        // escaping is well-formed according to UTF-8 rules

        EncodeForUri.checkPercentEncoding(absoluteURI.toString());
        return absoluteURI;
    }

    @CSharpReplaceBody(code="handleURISyntaxExceptionCSharp(href, baseURI, err);")
    private static void handleURISyntaxException(String href, String baseURI, java.net.URISyntaxException err)
            throws XPathException {
        throw new XPathException(err.getReason() + ": " + err.getInput(), err)
                .withErrorCode("FOUT1170");
    }


    public static XPathException handleIOError(URI absoluteURI, IOException ioErr) {
        String message = "Failed to read input file";
        if (absoluteURI != null && !ioErr.getMessage().equals(absoluteURI.toString())) {
            message += ' ' + absoluteURI.toString();
        }
        message += " (" + ioErr.getClass().getName() + ')';
        return new XPathException(message, ioErr)
                .withErrorCode(getErrorCode(ioErr));
    }

    @CSharpReplaceBody(code="return \"FOUT1170\";")
    public static String getErrorCode(IOException ioErr) {
        // FOUT1200 should be used when the encoding was inferred, FOUT1190 when it was explicit. We rely on the
        // caller to change FOUT1200 to FOUT1190 when necessary
        if (ioErr instanceof MalformedInputException) {
            return "FOUT1200";
        } else if (ioErr instanceof UnmappableCharacterException) {
            return "FOUT1200";
        } else if (ioErr instanceof CharacterCodingException) {
            return "FOUT1200";
        } else {
            return "FOUT1170";
        }
    }

    /**
     * Read the contents of an unparsed text file
     *
     * @param checker predicate for checking whether characters are valid XML characters
     * @param reader  Reader to be used for reading the file
     * @return the contents of the file, as a {@link UnicodeString}
     * @throws IOException    if a failure occurs reading the file
     * @throws XPathException if the file contains illegal characters
     */

    @CSharpInnerClass(outer=false, extra="Saxon.Hej.str.UnicodeBuilder buffer")
    public static UnicodeString readFile(IntPredicateProxy checker, Reader reader) throws IOException, XPathException {
        UnicodeBuilder buffer = new UnicodeBuilder();
        readFile(checker, reader, new AbstractUniStringConsumer() {
            @Override
            @CSharpModifiers(code={"public", "override"})
            public UniStringConsumer accept(UnicodeString chars) {
                return buffer.accept(chars);
            }
        });
        return buffer.toUnicodeString();
    }

    /**
     * Read the contents of an unparsed text file
     *
     * @param checker predicate for checking whether characters are valid XML characters
     * @param reader  Reader to be used for reading the file
     * @param output a consumer object that is supplied incrementally with the contents of the file
     * @throws IOException    if a failure occurs reading the file
     * @throws XPathException if the file contains illegal characters
     */

    @CSharpReplaceException(from="java.lang.IllegalStateException", to="System.Text.DecoderFallbackException")
    public static void readFile(IntPredicateProxy checker, Reader reader, UniStringConsumer output) throws IOException, XPathException {
        char[] buffer = new char[2048];
        boolean first = true;
        int actual;
        int line = 1;
        int column = 1;
        int mask = 0;
        while (true) {
            try {
                actual = reader.read(buffer, 0, buffer.length);
            } catch (IllegalStateException e) {
                // Proxy for C# System.Text.DecoderFallbackException
                throw new IOException(e.getMessage(), e);
            }
            if (isEndOfFile(actual)) {
                break;
            }
            for (int c = 0; c < actual; ) {
                int ch32 = buffer[c++];
                if (ch32 == '\n') {
                    line++;
                    column = 0;
                }
                column++;
                mask |= ch32;
                if (UTF16CharacterSet.isHighSurrogate(ch32)) {
                    if (c == actual) { // bug 3785, test case fn-unparsed-text-055
                        // We've got a high surrogate right at the end of the buffer.
                        // The path of least resistance is to extend the buffer.
                        char[] buffer2 = new char[2048];
                        int actual2 = reader.read(buffer2, 0, 2048);
                        char[] buffer3 = new char[actual + actual2];
                        System.arraycopy(buffer, 0, buffer3, 0, actual);
                        System.arraycopy(buffer2, 0, buffer3, actual, actual2);
                        buffer = buffer3;
                        actual = actual + actual2;
                    }
                    char low = buffer[c++];
                    ch32 = UTF16CharacterSet.combinePair((char) ch32, low);
                    mask |= ch32;
                }
                if (!checker.test(ch32)) {
                    throw new XPathException("The text file contains a character that is illegal in XML (line=" +
                                                                    line + " column=" + column + " value=hex " + Integer.toHexString(ch32) + ')')
                            .withErrorCode("FOUT1190");
                }
            }
            int start = 0;
            if (first) {
                if (buffer[0] == '\ufeff') {
                    start = 1;
                    actual--;
                }
                first = false;
            }
            if (mask <= 0xff) {
                output.accept(new Twine8(buffer, start, actual));
            } else if (mask <= 0xffff) {
                output.accept(new Twine16(buffer, start, actual));
            } else {
                output.accept(StringView.of(new String(buffer, start, actual)));
            }
        }
        reader.close();
    }

    @CSharpReplaceBody(code="return bytesRead == 0;")
    private static boolean isEndOfFile(int bytesRead) {
        return bytesRead < 0;
    }




}

