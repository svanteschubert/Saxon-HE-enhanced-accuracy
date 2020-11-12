////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.jaxp;

import net.sf.saxon.Version;
import net.sf.saxon.event.ContentHandlerProxy;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import java.io.IOException;


/**
 * <B>FilterImpl</B> is an XMLFilter (a SAX2 filter) that performs a transformation
 * taking a SAX stream as input and producing a SAX stream as output.
 *
 * @author Michael H. Kay
 */

public class FilterImpl extends AbstractXMLFilter {

    private TransformerImpl transformer;

    FilterImpl(TransformerImpl transformer) {
        this.transformer = transformer;
    }

    /**
     * Parse an XML document - In the context of a Transformer, this means
     * perform a transformation. The method is equivalent to transform().
     *
     * @param input The input source (the XML document to be transformed)
     * @throws org.xml.sax.SAXException Any SAX exception, possibly
     *                                  wrapping another exception.
     * @throws java.io.IOException      An IO exception from the parser,
     *                                  possibly from a byte stream or character stream
     *                                  supplied by the application.
     * @see org.xml.sax.InputSource
     * @see #parse(java.lang.String)
     * @see #setEntityResolver
     * @see #setDTDHandler
     * @see #setContentHandler
     * @see #setErrorHandler
     */

    @Override
    public void parse(InputSource input) throws IOException, SAXException {
        if (parser == null) {
            try {
                parser = Version.platform.loadParser();
            } catch (Exception err) {
                throw new SAXException(err);
            }
        }
        SAXSource source = new SAXSource();
        source.setInputSource(input);
        source.setXMLReader(parser);
        ContentHandlerProxy result = new ContentHandlerProxy();
        result.setPipelineConfiguration(transformer.getConfiguration().makePipelineConfiguration());
        result.setUnderlyingContentHandler(contentHandler);

        if (lexicalHandler != null) {
            result.setLexicalHandler(lexicalHandler);
        }
        try {
            //result.open();
            result.setOutputProperties(transformer.getOutputProperties());
            transformer.transform(source, result);
        } catch (TransformerException err) {
            Throwable cause = err.getException();
            if (cause != null && cause instanceof SAXException) {
                throw (SAXException) cause;
            } else if (cause != null && cause instanceof IOException) {
                throw (IOException) cause;
            } else {
                throw new SAXException(err);
            }
        }


    }

    /**
     * Get the underlying Transformer. This is a Saxon-specific method that allows the
     * user to set parameters on the transformation, set a URIResolver or ErrorListener, etc.
     *
     * @since Saxon 7.2
     */

    public Transformer getTransformer() {
        return transformer;
    }


}

