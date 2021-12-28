////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.*;


/**
 * This class defines the default OutputURIResolver. This is a counterpart to the JAXP
 * URIResolver, but is used to map the URI of a secondary result document to a Result object
 * which acts as the destination for the new document.
 *
 * @author Michael H. Kay
 */

public class StandardOutputResolver implements OutputURIResolver {

    private static StandardOutputResolver theInstance = new StandardOutputResolver();

    /**
     * Get a singular instance
     *
     * @return the singleton instance of the class
     */

    public static StandardOutputResolver getInstance() {
        return theInstance;
    }

    /**
     * Get an instance of this OutputURIResolver class.
     * <p>This method is called every time an xsl:result-document instruction
     * is evaluated (with an href attribute). The resolve() and close() methods
     * will be called on the returned instance.</p>
     * <p>This OutputURIResolver is stateless (that is, it retains no information
     * between resolve() and close()), so the same instance can safely be returned
     * each time. For a stateful OutputURIResolver, it must either take care to be
     * thread-safe (handling multiple invocations of xsl:result-document concurrently),
     * or it must return a fresh instance of itself for each call.</p>
     */

    @Override
    public StandardOutputResolver newInstance() {
        return this;
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

    @Override
    public Result resolve(String href, /*@Nullable*/ String base) throws XPathException {

        // System.err.println("Output URI Resolver (href='" + href + "', base='" + base + "')");

        String which = "base";
        try {
            URI absoluteURI;
            if (href.isEmpty()) {
                if (base == null) {
                    throw new XPathException("The system identifier of the principal output file is unknown", SaxonErrorCode.SXRD0002);
                }
                absoluteURI = new URI(base);
            } else {
                which = "relative";
                absoluteURI = new URI(href);
            }
            if (!absoluteURI.isAbsolute()) {
                if (base == null) {
                    throw new XPathException("The system identifier of the principal output file is unknown", SaxonErrorCode.SXRD0002);
                }
                which = "base";
                URI baseURI = new URI(base);
                which = "relative";
                absoluteURI = baseURI.resolve(href);
            }

            return createResult(absoluteURI);
        } catch (URISyntaxException err) {
            XPathException xe = new XPathException("Invalid syntax for " + which + " URI");
            xe.setErrorCode(SaxonErrorCode.SXRD0001);
            throw xe;
        } catch (IllegalArgumentException err2) {
            XPathException xe = new XPathException("Invalid " + which + " URI syntax");
            xe.setErrorCode(SaxonErrorCode.SXRD0001);
            throw xe;
        } catch (MalformedURLException err3) {
            XPathException xe = new XPathException("Resolved URL is malformed", err3);
            xe.setErrorCode(SaxonErrorCode.SXRD0001);
            throw xe;
        } catch (UnknownServiceException err4) {
            XPathException xe = new XPathException("Specified protocol does not allow output", err4);
            xe.setErrorCode(SaxonErrorCode.SXRD0001);
            throw xe;
        } catch (IOException err5) {
            XPathException xe = new XPathException("Cannot open connection to specified URL", err5);
            xe.setErrorCode(SaxonErrorCode.SXRD0001);
            throw xe;
        }
    }

    protected Result createResult(URI absoluteURI) throws XPathException, IOException {
        if ("file".equals(absoluteURI.getScheme())) {
            return StandardResultDocumentResolver.makeOutputFile(absoluteURI);

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
     * Signal completion of the result document. This method is called by the system
     * when the result document has been successfully written. It allows the resolver
     * to perform tidy-up actions such as closing output streams, or firing off
     * processes that take this result tree as input. Note that the OutputURIResolver
     * is stateless, so the original href is supplied to identify the document
     * that has been completed.
     */

    @Override
    public void close(Result result) throws XPathException {
        if (result instanceof StreamResult) {
            OutputStream stream = ((StreamResult) result).getOutputStream();
            if (stream != null) {
                try {
                    stream.close();
                } catch (java.io.IOException err) {
                    XPathException xe = new XPathException("Failed while closing output file", err);
                    xe.setErrorCode(SaxonErrorCode.SXRD0003);
                    throw xe;
                }
            }
            Writer writer = ((StreamResult) result).getWriter(); // Path not used, but there for safety
            if (writer != null) {
                try {
                    writer.close();
                } catch (java.io.IOException err) {
                    XPathException xe = new XPathException("Failed while closing output file", err);
                    xe.setErrorCode(SaxonErrorCode.SXRD0003);
                    throw xe;
                }
            }
        }
    }

}

