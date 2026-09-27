////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.serialize.charcode.CharacterSet;
import net.sf.saxon.str.Latin1;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

import javax.xml.transform.OutputKeys;
import java.util.*;

/**
 * CDATAFilter: This ProxyReceiver converts character data to CDATA sections,
 * if the character data belongs to one of a set of element types to be handled this way.
 *
 */


public class CDATAFilter extends ProxyReceiver {

    private UnicodeBuilder buffer = new UnicodeBuilder();
    private final Stack<NodeName> stack = new Stack<>();
    private Set<NodeName> nameList;             // names of cdata elements
    private CharacterSet characterSet;
    private boolean conditional = false;


    /**
     * Create a CDATA Filter
     *
     * @param next the next receiver in the pipeline
     */

    public CDATAFilter(Receiver next) {
        super(next);
    }

    /**
     * Set the properties for this CDATA filter
     *
     * @param details the output properties
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs
     */

    public void setOutputProperties(/*@NotNull*/ Properties details)
            throws XPathException {
        getCdataElements(details);
        characterSet = getConfiguration().getCharacterSetFactory().getCharacterSet(details);
        conditional = "yes".equals(details.getProperty(SaxonOutputKeys.CONDITIONAL_CDATA));
    }

    /**
     * Output element start tag
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties)
            throws XPathException {
        flush();
        stack.push(elemName);
        nextReceiver.startElement(elemName, type, attributes, namespaces, location, properties);
    }

    /**
     * Output element end tag
     */

    @Override
    public void endElement() throws XPathException {
        flush();
        stack.pop();
        nextReceiver.endElement();
    }

    /**
     * Output a processing instruction
     */

    @Override
    public void processingInstruction(String target, UnicodeString data, Location locationId, int properties) throws XPathException {
        flush();
        nextReceiver.processingInstruction(target, data, locationId, properties);
    }

    /**
     * Output character data
     */

    @Override
    public void characters(UnicodeString chars, Location locationId, int properties) throws XPathException {

        if (!ReceiverOption.contains(properties, ReceiverOption.DISABLE_ESCAPING)) {
            buffer.append(chars.toString());
        } else {
            // if the user requests disable-output-escaping, this overrides the CDATA request. We end
            // the CDATA section and output the characters as supplied.
            flush();
            nextReceiver.characters(chars, locationId, properties);
        }
    }

    /**
     * Output a comment
     */

    @Override
    public void comment(UnicodeString chars, Location locationId, int properties) throws XPathException {
        flush();
        nextReceiver.comment(chars, locationId, properties);
    }


    /**
     * Flush the buffer containing accumulated character data,
     * generating it as CDATA where appropriate
     */

    private void flush() throws XPathException {
        boolean cdata;
        int end = (int)buffer.length();
        if (end == 0) {
            return;
        }

        if (stack.isEmpty()) {
            cdata = false;      // text is not part of any element
        } else {
            NodeName top = stack.peek();
            cdata = isCDATA(top);
        }

        UnicodeString content = buffer.toUnicodeString();

        // Saxon serialization property conditional-cdata - don't output CDATA tags unless
        // there are special characters
        if (cdata && conditional && content.indexOf('<') < 0 && content.indexOf('&') < 0) {
            cdata = false;
        }

        if (cdata) {

            // If we're doing Unicode normalization, we need to do this before CDATA processing.
            // In this situation the normalizer will be the next thing in the serialization pipeline.

            if (getNextReceiver() instanceof UnicodeNormalizer) {
                UnicodeString normal = ((UnicodeNormalizer) getNextReceiver()).normalize(content, true);
                buffer = new UnicodeBuilder();
                buffer.accept(normal);
                end = (int)buffer.length();
            }

            // Check that the buffer doesn't include a character not available in the current
            // encoding

            UnicodeString bufferContent = buffer.toUnicodeString();

            int start = 0;
            int k = 0;
            while (k < end) {
                int next = bufferContent.codePointAt(k);
                if (next != 0 && characterSet.inCharset(next)) {
                    k++;
                } else {

                    // flush out the preceding characters as CDATA

                    flushCDATA(bufferContent.substring(start, k));

                    while (true) {
                        // output consecutive non-encodable characters
                        // before restarting the CDATA section
                        //super.characters(CharBuffer.wrap(buffer, k, k+skip), 0, 0);
                        nextReceiver.characters(bufferContent.substring(k, k+1),
                                                Loc.NONE, ReceiverOption.DISABLE_CHARACTER_MAPS);
                        k++;
                        if (k >= end) {
                            break;
                        }
                        next = bufferContent.codePointAt(k);
                        if (characterSet.inCharset(next)) {
                            break;
                        }
                    }
                    start = k;
                }
            }
            flushCDATA(bufferContent.substring(start, end));

        } else {
            nextReceiver.characters(content, Loc.NONE, ReceiverOption.NONE);
        }

        buffer.clear();

    }

    /**
     * Output an array as a CDATA section. At this stage we have checked that all the characters
     * are OK, but we haven't checked that there is no "]]&gt;" sequence in the data
     *
     * @param data the data to be output
     */

    private void flushCDATA(UnicodeString data) throws XPathException {
        data = data.tidy();
        if (data.isEmpty()) {
            return;
        }
        long len = data.length();
        final int chprop =
                ReceiverOption.DISABLE_ESCAPING | ReceiverOption.DISABLE_CHARACTER_MAPS;
        final Location loc = Loc.NONE;
        nextReceiver.characters(CDATA_OPEN, loc, chprop);

        // Check that the character data doesn't include the substring "]]>"
        // Also get rid of any zero bytes inserted by character map expansion

        long i = 0;
        long doneto = 0;
        while (i < len - 2) {
            if (data.codePointAt(i) == ']' && data.codePointAt(i + 1) == ']' && data.codePointAt(i+2) == '>') {
                nextReceiver.characters(data.substring(doneto, i + 2), loc, chprop);
                nextReceiver.characters(CDATA_CLOSE_AND_REOPEN, loc, chprop);
                doneto = i + 2;
            } else if (data.codePointAt(i) == 0) {
                nextReceiver.characters(data.substring(doneto, i), loc, chprop);
                doneto = i + 1;
            }
            i++;
        }
        nextReceiver.characters(data.substring(doneto, len), loc, chprop);
        nextReceiver.characters(CDATA_CLOSE, loc, chprop);
    }

    public static final UnicodeString CDATA_OPEN = Latin1.of("<![CDATA[");
    public static final UnicodeString CDATA_CLOSE = Latin1.of("]]>");
    public static final UnicodeString CDATA_CLOSE_AND_REOPEN = Latin1.of("]]><![CDATA[");


    /**
     * See if a particular element is a CDATA element. Method is protected to allow
     * overriding in a subclass.
     *
     * @param elementName identifies the name of element we are interested
     * @return true if this element is included in cdata-section-elements
     */

    protected boolean isCDATA(NodeName elementName) {
        return nameList.contains(elementName);
    }

    /**
     * Extract the list of CDATA elements from the output properties
     *
     * @param details the output properties
     */

    private void getCdataElements(Properties details) {
        boolean isHTML = "html".equals(details.getProperty(OutputKeys.METHOD));
        boolean isHTML5 = isHTML && "5.0".equals(details.getProperty(OutputKeys.VERSION));
        boolean isHTML4 = isHTML && !isHTML5;
        String cdata = details.getProperty(OutputKeys.CDATA_SECTION_ELEMENTS);
        if (cdata == null) {
            // this doesn't happen, but there's no harm allowing for it
            nameList = new HashSet<>(0);
            return;
        }
        nameList = new HashSet<>(10);
        StringTokenizer st2 = new StringTokenizer(cdata, " \t\n\r", false);
        while (st2.hasMoreTokens()) {
            String expandedName = st2.nextToken();
            StructuredQName sq = StructuredQName.fromClarkName(expandedName);
            NamespaceUri uri = sq.getNamespaceUri();
            if (!isHTML || (isHTML4 && !uri.equals(NamespaceUri.NULL))
                    || (isHTML5 && !uri.equals(NamespaceUri.NULL) && !uri.equals(NamespaceUri.XHTML))) {
                nameList.add(new FingerprintedQName("", uri, sq.getLocalPart()));
            }
        }
    }

}

