////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.CharSlice;
import net.sf.saxon.tree.util.CharSequenceConsumer;
import net.sf.saxon.tree.util.FastStringBuffer;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;
import java.util.function.IntPredicate;


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

    public static void readFile(URI absoluteURI, String encoding, CharSequenceConsumer output, XPathContext context)
            throws XPathException {

        final Configuration config = context.getConfiguration();
        IntPredicate checker = config.getValidCharacterChecker();

        // Use the URI machinery to validate and resolve the URIs

        Reader reader;
        try {
            reader = context.getController().getUnparsedTextURIResolver().resolve(absoluteURI, encoding, config);
        } catch (XPathException err) {
            err.maybeSetErrorCode("FOUT1170");
            throw err;
        }
        try {
            readFile(checker, reader, output);
        } catch (java.io.UnsupportedEncodingException encErr) {
            XPathException e = new XPathException("Unknown encoding " + Err.wrap(encoding), encErr);
            e.setErrorCode("FOUT1190");
            throw e;
        } catch (java.io.IOException ioErr) {
//            System.err.println("ProxyHost: " + System.getProperty("http.proxyHost"));
//            System.err.println("ProxyPort: " + System.getProperty("http.proxyPort"));
            throw handleIOError(absoluteURI, ioErr, context);
        }
    }

    public static URI getAbsoluteURI(String href, String baseURI, XPathContext context) throws XPathException {
        URI absoluteURI;
        try {
            absoluteURI = ResolveURI.makeAbsolute(href, baseURI);
        } catch (java.net.URISyntaxException err) {
            XPathException e = new XPathException(err.getReason() + ": " + err.getInput(), err);
            e.setErrorCode("FOUT1170");
            throw e;
        }

        if (absoluteURI.getFragment() != null) {
            XPathException e = new XPathException("URI for unparsed-text() must not contain a fragment identifier");
            e.setErrorCode("FOUT1170");
            throw e;
        }

        // The URL dereferencing classes throw all kinds of strange exceptions if given
        // ill-formed sequences of %hh escape characters. So we do a sanity check that the
        // escaping is well-formed according to UTF-8 rules

        EncodeForUri.checkPercentEncoding(absoluteURI.toString());
        return absoluteURI;
    }

    public static XPathException handleIOError(URI absoluteURI, IOException ioErr, XPathContext context) {
        String message = "Failed to read input file";
        if (absoluteURI != null && !ioErr.getMessage().equals(absoluteURI.toString())) {
            message += ' ' + absoluteURI.toString();
        }
        message += " (" + ioErr.getClass().getName() + ')';
        XPathException e = new XPathException(message, ioErr);
        String errorCode = getErrorCode(ioErr);
        e.setErrorCode(errorCode);
        return e;
    }

    private static String getErrorCode(IOException ioErr) {
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
     * @return the contents of the file, as a {@link CharSequence}
     * @throws IOException    if a failure occurs reading the file
     * @throws XPathException if the file contains illegal characters
     */

    public static CharSequence readFile(IntPredicate checker, Reader reader) throws IOException, XPathException {
        FastStringBuffer buffer = new FastStringBuffer(2048);
        readFile(checker, reader, new CharSequenceConsumer() {
            @Override
            public CharSequenceConsumer cat(CharSequence chars) {
                return buffer.cat(chars);
            }

            @Override
            public CharSequenceConsumer cat(char c) {
                return buffer.cat(c);
            }
        });
        return buffer.condense();
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

    public static void readFile(IntPredicate checker, Reader reader, CharSequenceConsumer output) throws IOException, XPathException {
        char[] buffer = new char[2048];
        boolean first = true;
        int actual;
        int line = 1;
        int column = 1;
        boolean latin = true;
        while (true) {
            actual = reader.read(buffer, 0, buffer.length);
            if (actual < 0) {
                break;
            }
            for (int c = 0; c < actual; ) {
                int ch32 = buffer[c++];
                if (ch32 == '\n') {
                    line++;
                    column = 0;
                }
                column++;
                if (ch32 > 255) {
                    latin = false;
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
                    }
                }
                if (!checker.test(ch32)) {
                    XPathException err = new XPathException("The text file contains a character that is illegal in XML (line=" +
                                                                    line + " column=" + column + " value=hex " + Integer.toHexString(ch32) + ')');
                    err.setErrorCode("FOUT1190");
                    throw err;
                }
            }
            if (first) {
                first = false;
                if (buffer[0] == '\ufeff') {
                    // don't include the BOM in the result
                    output.cat(new CharSlice(buffer, 1, actual - 1));
                } else {
                    output.cat(new CharSlice(buffer, 0, actual));
                }
            } else {
                output.cat(new CharSlice(buffer, 0, actual));
            }
        }
        reader.close();
//        if (latin) {
//            return new LatinString(sb);
//        } else {
//            return sb.condense();
//        }
    }




}

