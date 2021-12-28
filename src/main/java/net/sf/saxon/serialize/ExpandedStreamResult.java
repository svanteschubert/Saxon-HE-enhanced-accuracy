////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.serialize.charcode.CharacterSet;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.serialize.charcode.UTF8CharacterSet;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * An ExpandedStreamResult is similar to a StreamResult, and is created from a StreamResult. It contains
 * methods to construct a Writer from an OutputStream, and an OutputStream from a File or URI, and
 * (unlike StreamResult) its getWriter() and getOutputStream() methods can therefore be used whether
 * or not the writer and outputstream were explicitly set.
 */
public class ExpandedStreamResult {

    private Configuration config;
    private Properties outputProperties;
    private String systemId;
    private Writer writer;
    private OutputStream outputStream;
    private CharacterSet characterSet;
    private String encoding;
    private boolean mustClose;
    private boolean allCharactersEncodable;

    public ExpandedStreamResult(Configuration config, StreamResult result, Properties outputProperties) throws XPathException {
        this.config = config;
        this.systemId = result.getSystemId();
        this.writer = result.getWriter();
        this.outputStream = result.getOutputStream();
        this.encoding = outputProperties.getProperty(OutputKeys.ENCODING);
        if (encoding == null) {
            encoding = "UTF8";
            allCharactersEncodable = true;
        } else if (encoding.equalsIgnoreCase("UTF-8")) {
            encoding = "UTF8";
            allCharactersEncodable = true;
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
     * Make a Writer for this Emitter to use, given a StreamResult.
     *
     * @throws net.sf.saxon.trans.XPathException if an error occurs
     */

    public Writer obtainWriter() throws XPathException {
        if (writer != null) {
            return writer;
        } else {
            OutputStream os = obtainOutputStream();
            writer = makeWriterFromOutputStream(os);
            return writer;
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
            outputStream = new FileOutputStream(file);
            // Set the outputstream in the StreamResult object so that the
            // call on OutputURIResolver.close() can close it
            //streamResult.setOutputStream(outputStream);
            mustClose = true;
        } catch (FileNotFoundException | URISyntaxException | IllegalArgumentException fnf) {
            throw new XPathException(fnf);
        }

        return outputStream;
    }


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
            allCharactersEncodable = characterSet instanceof UTF8CharacterSet ||
                characterSet instanceof UTF16CharacterSet;
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
     * Set the output destination as a byte stream.
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

        //if (usesWriter()) {


            //while (true) {
                try {
                    String javaEncoding = encoding;
                    if (encoding.equalsIgnoreCase("iso-646") || encoding.equalsIgnoreCase("iso646")) {
                        javaEncoding = "US-ASCII";
                    }
                    if (encoding.equalsIgnoreCase("UTF8")) {
                        writer = new UTF8Writer(outputStream);
                    } else {
                        writer = new BufferedWriter(
                            new OutputStreamWriter(
                                outputStream, javaEncoding));
                    }
                    return writer;
                    //break;
                } catch (Exception err) {
                    if (encoding.equalsIgnoreCase("UTF8")) {
                        throw new XPathException("Failed to create a UTF8 output writer");
                    }
                    throw new XPathException("Encoding " + encoding + " is not supported", "SESU0007");
//                    XPathException de = new XPathException("Encoding " + encoding + " is not supported: using UTF8");
//                    de.setErrorCode("SESU0007");
//                    getPipelineConfiguration().getErrorReporter().error(de);
//                    encoding = "UTF8";
//                    characterSet = UTF8CharacterSet.getInstance();
//                    allCharactersEncodable = true;
//                    outputProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
                }
            //}
        //}

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
     */

    public CharacterSet getCharacterSet() {
        return characterSet;
    }

}

