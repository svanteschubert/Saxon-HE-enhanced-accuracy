////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.serialize.charcode.CharacterSet;
import net.sf.saxon.serialize.charcode.UTF8CharacterSet;
import net.sf.saxon.str.UnicodeWriter;
import net.sf.saxon.str.UnicodeWriterToWriter;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * An ExpandedStreamResult is similar to a StreamResult, and is created from a StreamResult. It contains
 * methods to construct a Writer from an OutputStream, and an OutputStream from a File or URI, and
 * (unlike StreamResult) its getWriter() and getOutputStream() methods can therefore be used whether
 * or not the writer and outputstream were explicitly set.
 */
public class ExpandedStreamResult {

    private final Configuration config;
    private Properties outputProperties;
    private final String systemId;
    private Writer writer;
    private OutputStream outputStream;
    private CharacterSet characterSet;
    private String encoding;
    private boolean mustCloseAfterUse = false;

    public ExpandedStreamResult(Configuration config, StreamResult result, Properties outputProperties) throws XPathException {
        this.config = config;
        this.systemId = result.getSystemId();
        this.writer = result.getWriter();
        this.outputStream = result.getOutputStream();
        this.outputProperties = outputProperties;
        this.encoding = outputProperties.getProperty(OutputKeys.ENCODING);
        if (encoding == null) {
            encoding = "UTF8";
        } else if (encoding.equalsIgnoreCase("UTF-8")) {
            encoding = "UTF8";
        } else if (encoding.equalsIgnoreCase("UTF-16")) {
            encoding = "UTF16";
        }

        if (characterSet == null) {
            characterSet = config.getCharacterSetFactory().getCharacterSet(encoding);
        }

        String byteOrderMark = outputProperties.getProperty(SaxonOutputKeys.BYTE_ORDER_MARK);
        if ("no".equals(byteOrderMark) && "UTF16".equals(encoding)) {
            // Java always writes a bom for UTF-16, so if the user doesn't want one, use utf16-be
            encoding = "UTF-16BE";
        } else if (!(characterSet instanceof UTF8CharacterSet)) {

            //if (characterSet instanceof PluggableCharacterSet) {
            encoding = characterSet.getCanonicalName();
        }

    }

    /**
     * Make a UnicodeWriter for an Emitter to use.
     *
     * @return the new UnicodeWriter
     * @throws net.sf.saxon.trans.XPathException if an error occurs
     */

    public UnicodeWriter obtainUnicodeWriter() throws XPathException {
        if (writer != null) {
            return new UnicodeWriterToWriter(writer);
        } else {
            OutputStream os = obtainOutputStream();
            return makeUnicodeWriterFromOutputStream(os);
        }
    }

    protected OutputStream obtainOutputStream() throws XPathException {
        if (outputStream != null) {
            return outputStream;
        }
        String uriString = systemId;
        if (uriString == null) {
            throw new XPathException("Result has no system ID, writer, or output stream defined");
        }

        try {
            File file = makeWritableOutputFile(uriString);
            mustCloseAfterUse = true;
            outputStream = new FileOutputStream(file);
        } catch (FileNotFoundException | URISyntaxException | IllegalArgumentException fnf) {
            throw new XPathException(fnf);
        }

        return outputStream;
    }

    /**
     * Ask whether the unicode writer myst be closed after use. This will typically be true if the writer was
     * created by Saxon, rather than being supplied by the user.
     * @return tru if the unicode writer myst be closed after use
     */

    public boolean isMustCloseAfterUse() {
        return mustCloseAfterUse;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static File makeWritableOutputFile(String uriString) throws URISyntaxException, XPathException {
        URI uri = new URI(uriString);
        if (!uri.isAbsolute()) {
            try {
                uri = new File(uriString).getAbsoluteFile().toURI();
            } catch (Exception e) {
                // if we fail, we'll get another exception
            }
        }
        File file = new File(uri);
        try {
            if ("file".equals(uri.getScheme()) && !file.exists()) {
                File directory = file.getParentFile();
                if (directory != null && !directory.exists()) {
                    directory.mkdirs();
                }
                file.createNewFile();
            }
            if (file.isDirectory()) {
                throw new XPathException("Cannot write to a directory: " + uriString, SaxonErrorCode.SXRD0004);
            }
            if (!file.canWrite()) {
                throw new XPathException("Cannot write to URI " + uriString, SaxonErrorCode.SXRD0004);
            }
        } catch (IOException err) {
            throw new XPathException("Failed to create output file " + uri, err);
        }
        return file;
    }


    /**
     * Determine whether the Emitter wants a Writer for character output or
     * an OutputStream for binary output. The standard Emitters all use a Writer, so
     * this returns true; but a subclass can override this if it wants to use an OutputStream
     *
     * @return true if a Writer is needed, as distinct from an OutputStream
     */

    public boolean usesWriter() {
        return true;
    }

    /**
     * Set the output destination as a character stream
     *
     * @param writer the Writer to use as an output destination
     * @throws net.sf.saxon.trans.XPathException if an error occurs
     */

    public void setWriter(Writer writer) throws XPathException {
        this.writer = writer;

        // If the writer uses a known encoding, change the encoding in the XML declaration
        // to match. Any encoding actually specified in xsl:output is ignored, because encoding
        // is being done by the user-supplied Writer, and not by Saxon itself.

        if (writer instanceof OutputStreamWriter && outputProperties != null) {
            String enc = ((OutputStreamWriter) writer).getEncoding();
            outputProperties.setProperty(OutputKeys.ENCODING, enc);
            characterSet = config.getCharacterSetFactory().getCharacterSet(outputProperties);
        }
    }

    /**
     * Get the output writer
     *
     * @return the Writer being used as an output destination, if any
     */

    public Writer getWriter() {
        return writer;
    }

    /**
     * Make a Writer from an OutputStream.
     * <p>Note that if a specific encoding (other than the default, UTF-8) is required, then
     * it must be defined in the output properties</p>
     *
     * @param stream the OutputStream being used as an output destination
     * @throws net.sf.saxon.trans.XPathException if an error occurs
     */

    private Writer makeWriterFromOutputStream(OutputStream stream) throws XPathException {
        outputStream = stream;

        // If the user supplied an OutputStream, but the Emitter is written to
        // use a Writer (this is the most common case), then we create a Writer
        // to wrap the supplied OutputStream; the complications are to ensure that
        // the character encoding is correct.

        try {
            Charset javaEncoding;
            if (encoding.equalsIgnoreCase("iso-646") || encoding.equalsIgnoreCase("iso646")) {
                javaEncoding = StandardCharsets.US_ASCII;
            } else {
                javaEncoding = Charset.forName(encoding);
            }
            if (encoding.equalsIgnoreCase("UTF8")) {
                writer = new UTF8Writer(outputStream);
            } else {
                writer = new BufferedWriter(new OutputStreamWriter(outputStream, javaEncoding));
            }
            return writer;
        } catch (Exception err) {
            if (encoding.equalsIgnoreCase("UTF8")) {
                throw new XPathException("Failed to create a UTF8 output writer");
            }
            throw new XPathException("Encoding " + encoding + " is not supported", "SESU0007");
        }
    }

    /**
     * Make a Writer from an OutputStream.
     * <p>Note that if a specific encoding (other than the default, UTF-8) is required, then
     * it must be defined in the output properties</p>
     *
     * @param stream the OutputStream being used as an output destination
     * @throws net.sf.saxon.trans.XPathException if an error occurs
     */

    private UnicodeWriter makeUnicodeWriterFromOutputStream(OutputStream stream) throws XPathException {
        outputStream = stream;
        try {
            if (encoding.equalsIgnoreCase("UTF8")) {
                return new UTF8Writer(outputStream);
            } else {
                Writer writer = makeWriterFromOutputStream(stream);
                return new UnicodeWriterToWriter(writer);
            }
        } catch (Exception err) {
            if (encoding.equalsIgnoreCase("UTF8")) {
                throw new XPathException("Failed to create a UTF8 output writer");
            }
            throw new XPathException("Encoding " + encoding + " is not supported", "SESU0007");
        }
    }

    /**
     * Get the output stream
     *
     * @return the OutputStream being used as an output destination, if any
     */

    public OutputStream getOutputStream() {
        return outputStream;
    }

    /**
     * Get the character set
     *
     * @return the CharacterSet
     */

    public CharacterSet getCharacterSet() {
        return characterSet;
    }

}

