////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.*;


/**
 * This class defines the default ResultDocumentResolver. This is a counterpart to the JAXP
 * URIResolver, but is used to map the URI of a secondary result document to a Receiver object
 * which acts as the destination for the new document.
 */

public class StandardResultDocumentResolver implements ResultDocumentResolver {

    private static StandardResultDocumentResolver theInstance = new StandardResultDocumentResolver();

    /**
     * Get a singular instance
     *
     * @return the singleton instance of the class
     */

    public static StandardResultDocumentResolver getInstance() {
        return theInstance;
    }

    @Override
    public Receiver resolve(
            XPathContext context, String href, String baseUri, SerializationProperties properties)
            throws XPathException {
        StreamResult result = resolve(href, baseUri);
        SerializerFactory factory = context.getConfiguration().getSerializerFactory();
        PipelineConfiguration pipe = context.getController().makePipelineConfiguration();
        return factory.getReceiver(result, properties, pipe);
    }

    /**
     * Resolve an output URI
     *
     * @param href The relative URI of the output document. This corresponds to the
     *             href attribute of the xsl:result-document instruction.
     * @param base The base URI that should be used. This is the base output URI,
     *             normally the URI of the principal output file.
     * @return a Result object representing the destination for the XML document
     */

    public StreamResult resolve(String href, /*@Nullable*/ String base) throws XPathException {

        // System.err.println("Output URI Resolver (href='" + href + "', base='" + base + "')");

        String which = "base";
        try {
            URI absoluteURI;
            if (href.isEmpty()) {
                if (base == null) {
                    throw new XPathException("The system identifier of the principal output file is unknown");
                }
                absoluteURI = new URI(base);
            } else {
                which = "relative";
                absoluteURI = new URI(href);
            }
            if (!absoluteURI.isAbsolute()) {
                if (base == null) {
                    throw new XPathException("The system identifier of the principal output file is unknown");
                }
                which = "base";
                URI baseURI = new URI(base);
                which = "relative";
                absoluteURI = baseURI.resolve(href);
            }

            return createResult(absoluteURI);
        } catch (URISyntaxException err) {
            throw new XPathException("Invalid syntax for " + which + " URI", err);
        } catch (IllegalArgumentException err2) {
            throw new XPathException("Invalid " + which + " URI syntax", err2);
        } catch (MalformedURLException err3) {
            throw new XPathException("Resolved URL is malformed", err3);
        } catch (UnknownServiceException err5) {
            throw new XPathException("Specified protocol does not allow output", err5);
        } catch (IOException err4) {
            throw new XPathException("Cannot open connection to specified URL", err4);
        }
    }

    protected StreamResult createResult(URI absoluteURI) throws XPathException, IOException {
        if ("file".equals(absoluteURI.getScheme())) {
            return makeOutputFile(absoluteURI);

        } else {

            // See if the Java VM can conjure up a writable URL connection for us.
            // This is optimistic: I have yet to discover a URL scheme that it can handle "out of the box".
            // But it can apparently be achieved using custom-written protocol handlers.

            URLConnection connection = absoluteURI.toURL().openConnection();
            connection.setDoInput(false);
            connection.setDoOutput(true);
            connection.connect();
            OutputStream stream = connection.getOutputStream();
            StreamResult result = new StreamResult(stream);
            result.setSystemId(absoluteURI.toASCIIString());
            return result;
        }
    }

    /**
     * Create an output file (unless it already exists) and return a reference to it as a Result object
     *
     * @param absoluteURI the URI of the output file (which should typically use the "file" scheme)
     * @return a Result object referencing this output file
     * @throws XPathException if the URI is not writable
     */

    public static synchronized StreamResult makeOutputFile(URI absoluteURI) throws XPathException {
        try {
            File outputFile = new File(absoluteURI);
            if (outputFile.isDirectory()) {
                throw new XPathException("Cannot write to a directory: " + absoluteURI, SaxonErrorCode.SXRD0004);
            }
            if (outputFile.exists() && !outputFile.canWrite()) {
                throw new XPathException("Cannot write to URI " + absoluteURI, SaxonErrorCode.SXRD0004);
            }
            return new StreamResult(outputFile);
        } catch (IllegalArgumentException err) {
            throw new XPathException("Cannot write to URI " + absoluteURI + " (" + err.getMessage() + ")");
        }
    }

    /**
     * Signal completion of the result document. This method is called by the system
     * when the result document has been successfully written. It allows the resolver
     * to perform tidy-up actions such as closing output streams, or firing off
     * processes that take this result tree as input. Note that the OutputURIResolver
     * is stateless, so the original href is supplied to identify the document
     * that has been completed.
     */

    public void close(Result result) throws XPathException {
        if (result instanceof StreamResult) {
            OutputStream stream = ((StreamResult) result).getOutputStream();
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException err) {
                    throw new XPathException("Failed while closing output file", err);
                }
            }
            Writer writer = ((StreamResult) result).getWriter(); // Path not used, but there for safety
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException err) {
                    throw new XPathException("Failed while closing output file", err);
                }
            }
        }
    }

}

