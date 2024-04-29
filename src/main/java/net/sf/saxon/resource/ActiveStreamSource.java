////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.lib.ActiveSource;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.transform.stream.StreamSource;

/**
 * An extension of the {@link StreamSource} object providing a {@link #deliver(Receiver, ParseOptions)} method
 * so that the contents of the source can be delivered to a supplied {@link Receiver}
 */

public class ActiveStreamSource extends StreamSource implements ActiveSource {

    /**
     * Create an <code>ActiveStreamSource</code> wrapping a supplied {@link StreamSource}
     * @param in the supplied {@link StreamSource}
     */
    public ActiveStreamSource(StreamSource in) {
        setInputStream(in.getInputStream());
        setReader(in.getReader());
        setSystemId(in.getSystemId());
        setPublicId(in.getPublicId());
    }

    /**
     * Deliver the content of the source to a supplied Receiver
     *
     * @param receiver the receiver to which events representing the parsed XML document will be sent
     * @param options  options for parsing the source
     * @throws XPathException if parsing fails for any reason. The detailed diagnostics will
     *                        have been sent to the error reporter.
     */
    @Override
    public void deliver(Receiver receiver, ParseOptions options) throws XPathException {
        Configuration config = receiver.getPipelineConfiguration().getConfiguration();
        String url = getSystemId();
        InputSource is = new InputSource(url);
        is.setCharacterStream(getReader());
        is.setByteStream(getInputStream());
        boolean reuseParser = false;
        XMLReader parser = options.obtainXMLReader();
        if (parser == null) {
            parser = config.getSourceParser();
            if (options.getEntityResolver() != null /*&& parser.getEntityResolver() == null*/) {
                parser.setEntityResolver(options.getEntityResolver());
            }
            try {
                parser.setFeature("http://xml.org/sax/features/validation", options.getDTDValidationMode() == Validation.STRICT);
            } catch (SAXException err) {
                throw new XPathException("The XML parser does not support DTD-based validation", SaxonErrorCode.SXXP0003);
            }
            reuseParser = true;
        }
        //System.err.println("Using parser: " + parser.getClass().getName());
        ActiveSAXSource sax = new ActiveSAXSource(parser, is);
        sax.setSystemId(url);
        sax.deliver(receiver, options);
        if (reuseParser) {
            config.reuseSourceParser(parser);
        }
    }
}

