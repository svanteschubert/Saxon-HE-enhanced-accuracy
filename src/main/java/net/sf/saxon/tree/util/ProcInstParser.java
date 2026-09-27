////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.util;

import net.sf.saxon.Version;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * ProcInstParser is used to parse pseudo-attributes within Processing Instructions. This is a utility
 * class that is never instantiated.
 */


public class ProcInstParser {

    /**
     * Class is never instantiated
     */

    private ProcInstParser() {
    }

    private static class AttributeProcessor extends XMLFilterImpl {
        List<String> result;
        String name;
        public AttributeProcessor(String name, List<String> result) {
            this.name = name;
            this.result = result;
        }
        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
            String val = atts.getValue(name);
            if (val != null) {
                result.add(val);
            }
        }
    }

    /**
     * Get a pseudo-attribute. This is useful only if the processing instruction data part
     * uses pseudo-attribute syntax, which it does not have to. This syntax is as described
     * in the W3C Recommendation "Associating Style Sheets with XML Documents".
     *
     * @param content the content of the processing instruction
     * @param name the name of the required pseudo-attribute
     * @return the value of the pseudo-attribute if present, or null if not
     * @throws XPathException if the syntax is invalid
     */

    /*@Nullable*/
    public static String getPseudoAttribute(String content, String name) throws XPathException {
        try {
            List<String> result = new ArrayList<>();
            XMLFilterImpl filter = new AttributeProcessor(name, result);
            XMLReader reader = getXMLReader();
            reader.setContentHandler(filter);
            StringReader in = new StringReader("<e " + content + "/>");
            reader.parse(new InputSource(in));
            parserPool.add(reader);
            return result.isEmpty() ? null : result.get(0);
        } catch (SAXException | IOException e) {
            throw new XPathException("Invalid syntax for pseudo-attributes: '" + content + "'. ", SaxonErrorCode.SXCH0005);
        }
    }

    private static XMLReader getXMLReader() {
        if (parserPool == null) {
            parserPool = new ConcurrentLinkedQueue<>();
        }
        XMLReader parser = parserPool.poll();
        if (parser != null) {
            return parser;
        }
        return Version.platform.loadParser();
    }

    private static ConcurrentLinkedQueue<XMLReader> parserPool;

}   
