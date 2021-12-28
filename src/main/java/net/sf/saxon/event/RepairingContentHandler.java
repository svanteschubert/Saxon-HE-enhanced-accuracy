////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * A RepairingContentHandler is a SAX filter that can be placed on the input pipeline in front of
 * a {@link ReceivingContentHandler} for use in cases where the events supplied by the XML parser
 * are not guaranteed to satisfy all the consistency constraints.
 *
 * <p>In this initial implementation, all it does is to generate a startPrefixMapping call for
 * the namespace used in an element name supplied to startElement(). This is needed when accepting
 * input from the TagSoup HTML parser.</p>
 */

public class RepairingContentHandler extends XMLFilterImpl {

    /**
     * Filter a start element event.
     *
     * @param uri       The element's Namespace URI, or the empty string.
     * @param localName The element's local name, or the empty string.
     * @param qName     The element's qualified (prefixed) name, or the empty
     *                  string.
     * @param atts      The element's attributes.
     * @throws SAXException The client may throw
     *                      an exception during processing.
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        if (uri != null && !uri.isEmpty() && !qName.contains(":")) {
            startPrefixMapping("", uri);
        }
        super.startElement(uri, localName, qName, atts);
    }
}

