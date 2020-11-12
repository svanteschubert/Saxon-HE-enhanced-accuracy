////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.om.AttributeMap;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.om.NamespaceMap;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.serialize.charcode.UTF8CharacterSet;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

import javax.xml.transform.OutputKeys;
import java.util.regex.Pattern;

/**
 * This class generates TEXT output
 *
 * @author Michael H. Kay
 */

public class TEXTEmitter extends XMLEmitter {

    private Pattern newlineMatcher = null;
    private String newlineRepresentation = null;

    /**
     * Start of the document.
     */

    @Override
    public void open() throws XPathException {
    }

    @Override
    protected void openDocument() throws XPathException {

        if (writer == null) {
            makeWriter();
        }
        if (characterSet == null) {
            characterSet = UTF8CharacterSet.getInstance();
        }
        // Write a BOM if requested
        String encoding = outputProperties.getProperty(OutputKeys.ENCODING);
        if (encoding == null || encoding.equalsIgnoreCase("utf8")) {
            encoding = "UTF-8";
        }
        String byteOrderMark = outputProperties.getProperty(SaxonOutputKeys.BYTE_ORDER_MARK);
        String nl = outputProperties.getProperty(SaxonOutputKeys.NEWLINE);
        if (nl != null && !nl.equals("\n")) {
            newlineRepresentation = nl;
            newlineMatcher = Pattern.compile("\\n");
        }

        if ("yes".equals(byteOrderMark) && (
                "UTF-8".equalsIgnoreCase(encoding) ||
                        "UTF-16LE".equalsIgnoreCase(encoding) ||
                        "UTF-16BE".equalsIgnoreCase(encoding))) {
            try {
                writer.write('\uFEFF');
            } catch (java.io.IOException err) {
                // Might be an encoding exception; just ignore it
            }
        }
        started = true;
    }

    /**
     * Output the XML declaration. This implementation does nothing.
     */

    @Override
    public void writeDeclaration() {
    }

    /**
     * Produce output using the current Writer. <BR>
     * Special characters are not escaped.
     * @param chars      Character sequence to be output
     * @param locationId the location of the node in the source, or of the instruction that created it
     * @param properties bit fields holding special properties of the characters  @throws XPathException for any failure
     */

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (!started) {
            openDocument();
        }
        if (!ReceiverOption.contains(properties, ReceiverOption.NO_SPECIAL_CHARS)) {
            int badchar = testCharacters(chars);
            if (badchar != 0) {
                throw new XPathException(
                        "Output character not available in this encoding (x" + Integer.toString(badchar, 16) + ")", "SERE0008");
            }
        }
        if (newlineMatcher != null) {
            chars = newlineMatcher.matcher(chars).replaceAll(newlineRepresentation);
        }
        try {
            writer.write(chars.toString());
        } catch (java.io.IOException err) {
            throw new XPathException(err);
        }
    }

    /**
     * Output an element start tag. <br>
     * Does nothing with this output method.
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        previousAtomic = false;
    }

    /**
     * Output an element end tag. <br>
     * Does nothing  with this output method.
     */

    @Override
    public void endElement() {
        // no-op
    }

    /**
     * Output a processing instruction. <br>
     * Does nothing with this output method.
     */

    @Override
    public void processingInstruction(String name, /*@NotNull*/ CharSequence value, Location locationId, int properties)
            throws XPathException {
    }

    /**
     * Output a comment. <br>
     * Does nothing with this output method.
     */

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
    }

}

