////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.jaxp;

import net.sf.saxon.Version;
import net.sf.saxon.s9api.SAXDestination;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Xslt30Transformer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.transform.sax.SAXSource;
import java.io.IOException;


/**
 * <B>StreamingFilterImpl</B> is an XMLFilter (a SAX2 filter) that performs a transformation
 * taking a SAX stream as input and producing a SAX stream as output, using XSLT 3.0 streaming
 * to process the source
 *
 * @since 9.8.0.4
 */

public class StreamingFilterImpl extends AbstractXMLFilter {

    private Xslt30Transformer transformer;

    StreamingFilterImpl(Xslt30Transformer transformer) {
        this.transformer = transformer;
    }

    /**
     * Parse an XML document - In the context of a Transformer, this means
     * perform a transformation. The method is equivalent to transform().
     *
     * @param input The input source (the XML document to be transformed)
     * @throws SAXException Any SAX exception, possibly
     *                                  wrapping another exception.
     * @throws IOException      An IO exception from the parser,
     *                                  possibly from a byte stream or character stream
     *                                  supplied by the application.
     * @see InputSource
     * @see #parse(String)
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
        if (lexicalHandler != null && lexicalHandler != contentHandler) {
            throw new IllegalStateException("ContentHandler and LexicalHandler must be the same object");
        }
        SAXSource source = new SAXSource();
        source.setInputSource(input);
        source.setXMLReader(parser);
        SAXDestination result = new SAXDestination(contentHandler);

        try {
            transformer.applyTemplates(source, result);
        } catch (SaxonApiException err) {
            throw new SAXException(err);
        }


    }

    /**
     * Get the underlying Transformer. This is a Saxon-specific method that allows the
     * user to set parameters on the transformation, set a URIResolver or ErrorListener, etc.
     *
     * @since Saxon 9.8
     */

    public Xslt30Transformer getTransformer() {
        return transformer;
    }


}

