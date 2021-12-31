////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.util;

import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Get a pseudo-attribute. This is useful only if the processing instruction data part
     * uses pseudo-attribute syntax, which it does not have to. This syntax is as described
     * in the W3C Recommendation "Associating Style Sheets with XML Documents".
     *
     * @return the value of the pseudo-attribute if present, or null if not
     */

    /*@Nullable*/
    public static String getPseudoAttribute(/*@NotNull*/ String content, /*@NotNull*/ String name) throws XPathException {

        try {
            List<String> result = new ArrayList<>();
            XMLFilterImpl filter = new XMLFilterImpl() {
                @Override
                public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
                    String val = atts.getValue(name);
                    if (val != null) {
                        result.add(val);
                    }
                }
            };

            XMLReader reader = Version.platform.loadParserForXmlFragments();
            reader.setContentHandler(filter);
            StringReader in = new StringReader("<e " + content + "/>");
            reader.parse(new InputSource(in));
            return result.isEmpty() ? null : result.get(0);
        } catch (/*ParserConfigurationException |*/ SAXException | IOException e) {
            throw new XPathException("Invalid syntax for pseudo-attributes: " + e.getMessage(), SaxonErrorCode.SXCH0005);
        }

    }

}   
