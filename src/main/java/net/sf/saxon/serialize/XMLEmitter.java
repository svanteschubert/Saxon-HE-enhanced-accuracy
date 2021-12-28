////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.serialize.charcode.UTF8CharacterSet;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.CharSlice;
import net.sf.saxon.tree.tiny.CompressedWhitespace;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.Whitespace;

import javax.xml.transform.OutputKeys;
import java.util.Arrays;
import java.util.Properties;
import java.util.Stack;

/**
 * XMLEmitter is an Emitter that generates XML output
 * to a specified destination.
 */

public class XMLEmitter extends Emitter {

    // NOTE: we experimented with XMLUTF8Emitter which combines XML escaping and UTF8 encoding
    // into a single loop. Scrapped it because we couldn't measure any benefits - but there
    // ought to be, in theory. Perhaps we weren't buffering the writes carefully enough.

    protected boolean canonical = false;
    protected boolean started = false;
    protected boolean startedElement = false;
    protected boolean openStartTag = false;
    protected boolean declarationIsWritten = false;
    protected NodeName elementCode;
    protected int indentForNextAttribute = -1;
    protected boolean undeclareNamespaces = false;
    protected boolean unfailing = false;
    protected char delimiter = '"';
    protected boolean[] attSpecials = specialInAtt;

    // The element stack holds the display names (lexical QNames) of elements that
    // have been started but not finished. It is used to obtain the element name
    // for the end tag.

    protected Stack<String> elementStack = new Stack<>();

    // For other names we use a hashtable. It

    private boolean indenting = false;
    private String indentChars = "\n                                                          ";
    private boolean requireWellFormed = false;
    protected CharacterReferenceGenerator characterReferenceGenerator = HexCharacterReferenceGenerator.THE_INSTANCE;


    static boolean[] specialInText;         // lookup table for special characters in text
    static boolean[] specialInAtt;          // lookup table for special characters in attributes
    static boolean[] specialInAttSingle;    // lookup table for special characters in attributes with single-quote delimiter
    // create look-up table for ASCII characters that need special treatment

    static {
        specialInText = new boolean[128];
        for (int i = 0; i <= 31; i++) {
            specialInText[i] = true;  // allowed in XML 1.1 as character references
        }
        for (int i = 32; i <= 127; i++) {
            specialInText[i] = false;
        }
        //    note, 0 is used to switch escaping on and off for mapped characters
        specialInText['\n'] = false;
        specialInText['\t'] = false;
        specialInText['\r'] = true;
        specialInText['<'] = true;
        specialInText['>'] = true;
        specialInText['&'] = true;

        specialInAtt = new boolean[128];
        for (int i = 0; i <= 31; i++) {
            specialInAtt[i] = true; // allowed in XML 1.1 as character references
        }
        for (int i = 32; i <= 127; i++) {
            specialInAtt[i] = false;
        }
        specialInAtt[(char) 0] = true;
        // used to switch escaping on and off for mapped characters
        specialInAtt['\r'] = true;
        specialInAtt['\n'] = true;
        specialInAtt['\t'] = true;
        specialInAtt['<'] = true;
        specialInAtt['>'] = true;
        specialInAtt['&'] = true;
        specialInAtt['\"'] = true;

        specialInAttSingle = Arrays.copyOf(specialInAtt, 128);
        specialInAttSingle['\"'] = false;
        specialInAttSingle['\''] = true;
    }

    /**
     * Set the character reference generator to be used for generating hexadecimal or decimal
     * character references
     *
     * @param generator the character reference generator to be used
     */

    public void setCharacterReferenceGenerator(CharacterReferenceGenerator generator) {
        this.characterReferenceGenerator = generator;
    }

    /**
     * Say that all non-ASCII characters should be escaped, regardless of the character encoding
     *
     * @param escape true if all non ASCII characters should be escaped
     */

    public void setEscapeNonAscii(Boolean escape) {
        // no action (not currently supported for this output method
    }

    /**
     * Start of the event stream. Nothing is done at this stage: the opening of the output
     * file is deferred until some content is written to it.
     */

    @Override
    public void open() throws XPathException {
    }

    /**
     * Start of a document node. Nothing is done at this stage: the opening of the output
     * file is deferred until some content is written to it.
     * @param properties
     */

    @Override
    public void startDocument(int properties) throws XPathException {
    }

    /**
     * Notify the end of a document node
     */

    @Override
    public void endDocument() throws XPathException {
        // Following code removed as a result of bug 2323. If a failure occurs during xsl:result-document processing,
        // and the output is being written to a SAXResult, then the ContentHandler.endDocument() method is called in order
        // to close any open files; and this calls Emitter.endDocument() at a point where the output is incomplete.
//        if (!elementStack.isEmpty()) {
//            throw new IllegalStateException("Attempt to end document in serializer when elements are unclosed");
//        }
    }

    /**
     * Do the real work of starting the document. This happens when the first
     * content is written.
     *
     * @throws XPathException if an error occurs opening the output file
     */

    protected void openDocument() throws XPathException {
        if (writer == null) {
            makeWriter();
        }
        if (characterSet == null) {
            characterSet = UTF8CharacterSet.getInstance();
        }
        if (outputProperties == null) {
            outputProperties = new Properties();
        }

        undeclareNamespaces = "yes".equals(outputProperties.getProperty(SaxonOutputKeys.UNDECLARE_PREFIXES));
        canonical = "yes".equals(outputProperties.getProperty(SaxonOutputKeys.CANONICAL));
        unfailing = "yes".equals(outputProperties.getProperty(SaxonOutputKeys.UNFAILING));

        if ("yes".equals(outputProperties.getProperty(SaxonOutputKeys.SINGLE_QUOTES))) {
            delimiter = '\'';
            attSpecials = specialInAttSingle;
        }
        writeDeclaration();
    }

    /**
     * Output the XML declaration
     */

    public void writeDeclaration() throws XPathException {
        if (declarationIsWritten) {
            return;
        }
        declarationIsWritten = true;
        try {
            indenting = "yes".equals(outputProperties.getProperty(OutputKeys.INDENT));

            String byteOrderMark = outputProperties.getProperty(SaxonOutputKeys.BYTE_ORDER_MARK);
            String encoding = outputProperties.getProperty(OutputKeys.ENCODING);
            if (encoding == null || encoding.equalsIgnoreCase("utf8") || canonical) {
                encoding = "UTF-8";
            }

            if ("yes".equals(byteOrderMark) && !canonical && (
                    "UTF-8".equalsIgnoreCase(encoding) ||
                            "UTF-16LE".equalsIgnoreCase(encoding) ||
                            "UTF-16BE".equalsIgnoreCase(encoding))) {
                writer.write('\uFEFF');
            }

            String omitXMLDeclaration = outputProperties.getProperty(OutputKeys.OMIT_XML_DECLARATION);
            if (omitXMLDeclaration == null) {
                omitXMLDeclaration = "no";
            }

            if (canonical) {
                omitXMLDeclaration = "yes";
            }

            String version = outputProperties.getProperty(OutputKeys.VERSION);
            if (version == null) {
                version = getConfiguration().getXMLVersion() == Configuration.XML10 ? "1.0" : "1.1";
            } else {
                if (!version.equals("1.0") && !version.equals("1.1")) {
                    if (unfailing) {
                        version = "1.0";
                    } else {
                        XPathException err = new XPathException("XML version must be 1.0 or 1.1");
                        err.setErrorCode("SESU0013");
                        throw err;
                    }
                }
                if (!version.equals("1.0") && omitXMLDeclaration.equals("yes") &&
                        outputProperties.getProperty(OutputKeys.DOCTYPE_SYSTEM) != null) {
                    if (!unfailing) {
                        XPathException err = new XPathException("Values of 'version', 'omit-xml-declaration', and 'doctype-system' conflict");
                        err.setErrorCode("SEPM0009");
                        throw err;
                    }
                }
            }

            String undeclare = outputProperties.getProperty(SaxonOutputKeys.UNDECLARE_PREFIXES);
            if ("yes".equals(undeclare)) {
                undeclareNamespaces = true;
            }

            if (version.equals("1.0") && undeclareNamespaces) {
                if (unfailing) {
                    undeclareNamespaces = false;
                } else {
                    XPathException err = new XPathException("Cannot undeclare namespaces with XML version 1.0");
                    err.setErrorCode("SEPM0010");
                    throw err;
                }
            }

            String standalone = outputProperties.getProperty(OutputKeys.STANDALONE);
            if ("omit".equals(standalone)) {
                standalone = null;
            }

            if (standalone != null) {
                requireWellFormed = true;
                if (omitXMLDeclaration.equals("yes") && !unfailing) {
                    XPathException err = new XPathException("Values of 'standalone' and 'omit-xml-declaration' conflict");
                    err.setErrorCode("SEPM0009");
                    throw err;
                }
            }

            String systemId = outputProperties.getProperty(OutputKeys.DOCTYPE_SYSTEM);
            if (systemId != null && !"".equals(systemId)) {
                requireWellFormed = true;
            }

            if (omitXMLDeclaration.equals("no")) {
                writer.write("<?xml version=\"" + version + "\" " + "encoding=\"" + encoding + '\"' +
                                     (standalone != null ? " standalone=\"" + standalone + '\"' : "") + "?>");
                // don't write a newline character: it's wrong if the output is an
                // external general parsed entity
            }
        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
    }

    /**
     * Output the document type declaration
     *
     * @param name        the qualified name of the element
     * @param displayName The element name as displayed
     * @param systemId    The DOCTYPE system identifier
     * @param publicId    The DOCTYPE public identifier
     * @throws net.sf.saxon.trans.XPathException if an error occurs writing to the output
     */

    protected void writeDocType(NodeName name, String displayName, String systemId, String publicId) throws XPathException {
        try {
            if (!canonical) {
                if (declarationIsWritten && !indenting) {
                    // don't add a newline if indenting, because the indenter will already have done so
                    writer.write("\n");
                }
                writer.write("<!DOCTYPE " + displayName + '\n');
                String quotedSystemId = null;
                if (systemId != null) {
                    if (systemId.contains("\"")) {
                        quotedSystemId = "'" + systemId + "'";
                    } else {
                        quotedSystemId = '"' + systemId + '"';
                    }
                }
                if (systemId != null && publicId == null) {
                    writer.write("  SYSTEM " + quotedSystemId + ">\n");
                } else if (systemId == null && publicId != null) {     // handles the HTML case
                    writer.write("  PUBLIC \"" + publicId + "\">\n");
                } else {
                    writer.write("  PUBLIC \"" + publicId + "\" " + quotedSystemId + ">\n");
                }
            }
        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
    }

    /**
     * End of the document.
     */

    @Override
    public void close() throws XPathException {
        // if nothing has been written, we should still create the file and write an XML declaration
        if (!started) {
            openDocument();
        }
        try {
            if (writer != null) {
                writer.flush();
            }
        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
        super.close();
    }

    /**
     * Start of an element. Output the start tag, escaping special characters.
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        previousAtomic = false;
        if (!started) {
            openDocument();
        } else if (requireWellFormed && elementStack.isEmpty() && startedElement && !unfailing) {
            XPathException err = new XPathException("When 'standalone' or 'doctype-system' is specified, " +
                                                            "the document must be well-formed; but this document contains more than one top-level element");
            err.setErrorCode("SEPM0004");
            throw err;
        }
        startedElement = true;

        String displayName = elemName.getDisplayName();
        if (!allCharactersEncodable) {
            int badchar = testCharacters(displayName);
            if (badchar != 0) {
                XPathException err = new XPathException("Element name contains a character (decimal + " +
                                                                badchar + ") not available in the selected encoding");
                err.setErrorCode("SERE0008");
                throw err;
            }
        }

        elementStack.push(displayName);
        elementCode = elemName;

        try {
            if (!started) {
                String systemId = outputProperties.getProperty(OutputKeys.DOCTYPE_SYSTEM);
                String publicId = outputProperties.getProperty(OutputKeys.DOCTYPE_PUBLIC);
                // Treat "" as equivalent to absent. This goes beyond what the spec strictly allows.
                if ("".equals(systemId)) {
                    systemId = null;
                }
                if ("".equals(publicId)) {
                    publicId = null;
                }
                if (systemId != null) {
                    requireWellFormed = true;
                    writeDocType(elemName, displayName, systemId, publicId);
                } else if (writeDocTypeWithNullSystemId()) {
                    writeDocType(elemName, displayName, null, publicId);
                }
                started = true;
            }
            if (openStartTag) {
                closeStartTag();
            }
            writer.write('<');
            writer.write(displayName);

            if (indentForNextAttribute >= 0) {
                indentForNextAttribute += displayName.length();
            }

            boolean isFirst = true;

            for (NamespaceBinding ns : namespaces) {
                namespace(ns.getPrefix(), ns.getURI(), isFirst);
                isFirst = false;
            }

            for (AttributeInfo att : attributes) {
                attribute(att.getNodeName(), att.getValue(), att.getProperties(), isFirst);
                isFirst = false;
            }

            openStartTag = true;
            indentForNextAttribute = -1;

        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
    }

    protected boolean writeDocTypeWithNullSystemId() {
        return false;
    }

    public void namespace(String nsprefix, String nsuri, boolean isFirst) throws XPathException {
        try {

            String sep = isFirst ? " " : getAttributeIndentString();

            if (nsprefix.isEmpty()) {
                writer.write(sep);
                writeAttribute(elementCode, "xmlns", nsuri, ReceiverOption.NONE);
            } else if (nsprefix.equals("xml")) {
                //return;
            } else {
                int badchar = testCharacters(nsprefix);
                if (badchar != 0) {
                    XPathException err = new XPathException("Namespace prefix contains a character (decimal + " +
                                                                    badchar + ") not available in the selected encoding");
                    err.setErrorCode("SERE0008");
                    throw err;
                }
                if (undeclareNamespaces || !nsuri.isEmpty()) {
                    writer.write(sep);
                    writeAttribute(elementCode, "xmlns:" + nsprefix, nsuri, ReceiverOption.NONE);
                }
            }

        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
    }

    /**
     * Set the indentation to be used for attributes (this excludes the length of the
     * element name itself)
     * @param indent the number of spaces to be output before each attribute (on a new line)
     */

    public void setIndentForNextAttribute(int indent) {
        indentForNextAttribute = indent;
    }

    private void attribute(NodeName nameCode, CharSequence value, int properties, boolean isFirst)
            throws XPathException {

        String displayName = nameCode.getDisplayName();
        if (!allCharactersEncodable) {
            int badchar = testCharacters(displayName);
            if (badchar != 0) {
                if (unfailing) {
                    displayName = convertToAscii(displayName);
                } else {
                    XPathException err = new XPathException("Attribute name contains a character (decimal + " +
                                                                    badchar + ") not available in the selected encoding");
                    err.setErrorCode("SERE0008");
                    throw err;
                }
            }
        }

        try {
            writer.write(isFirst ? " " : getAttributeIndentString());
        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }


        writeAttribute(
                elementCode,
                displayName,
                value,
                properties);


    }

    protected String getAttributeIndentString() {
        if (indentForNextAttribute < 0) {
            return " ";
        } else {
            int indent = indentForNextAttribute;
            while (indent >= indentChars.length()) {
                //noinspection StringConcatenationInLoop
                indentChars += "                     ";
            }
            return indentChars.substring(0, indent);
        }
    }

    /**
     * Mark the end of the start tag
     *
     * @throws XPathException if an IO exception occurs
     */

    public void closeStartTag() throws XPathException {
        try {
            if (openStartTag) {
                writer.write('>');
                openStartTag = false;
            }
        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
    }

    /**
     * Close an empty element tag. (This is overridden in XHTMLEmitter).
     *
     * @param displayName the name of the empty element
     * @param nameCode    the fingerprint of the name of the empty element
     * @return the string used to close an empty element tag.
     */

    protected String emptyElementTagCloser(String displayName, NodeName nameCode) {
        return canonical ? "></" + displayName + ">" : "/>";
    }

    /**
     * Write attribute name=value pair.
     *
     * @param elCode     The element name is not used in this version of the
     *                   method, but is used in the HTML subclass.
     * @param attname    The attribute name, which has already been validated to ensure
     *                   it can be written in this encoding
     * @param value      The value of the attribute
     * @param properties Any special properties of the attribute
     * @throws net.sf.saxon.trans.XPathException if an error occurs
     */

    protected void writeAttribute(NodeName elCode, String attname, CharSequence value, int properties) throws XPathException {
        try {
            String val = value.toString();
            writer.write(attname);
            if (ReceiverOption.contains(properties, ReceiverOption.NO_SPECIAL_CHARS)) {
                writer.write('=');
                writer.write(delimiter);
                writer.write(val);
                writer.write(delimiter);
            } else if (ReceiverOption.contains(properties, ReceiverOption.USE_NULL_MARKERS)) {
                // null (0) characters will be used before and after any section of
                // the value generated from a character map
                writer.write('=');
                char delim = val.indexOf('"') >= 0 && val.indexOf('\'') < 0 ? '\'' : delimiter;
                writer.write(delim);
                writeEscape(value, true);
                writer.write(delim);
            } else {
                writer.write("=");
                writer.write(delimiter);
                writeEscape(value, true);
                writer.write(delimiter);
            }
        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
    }


    /**
     * Test that all characters in a name (for example) are supported in the target encoding.
     *
     * @param chars the characters to be tested
     * @return zero if all the characters are available, or the value of the
     * first offending character if not
     */

    protected int testCharacters(CharSequence chars) {
        for (int i = 0; i < chars.length(); i++) {
            char c = chars.charAt(i);
            if (c > 127) {
                if (UTF16CharacterSet.isHighSurrogate(c)) {
                    int cc = UTF16CharacterSet.combinePair(c, chars.charAt(++i));
                    if (!characterSet.inCharset(cc)) {
                        return cc;
                    }
                } else if (!characterSet.inCharset(c)) {
                    return c;
                }
            }
        }
        return 0;
    }

    /**
     * Where characters are not available in the selected encoding, substitute them
     */

    protected String convertToAscii(CharSequence chars) {
        FastStringBuffer buff = new FastStringBuffer(chars.length());
        for (int i = 0; i < chars.length(); i++) {
            char c = chars.charAt(i);
            if (c >= 20 && c < 127) {
                buff.cat(c);
            } else {
                buff.append("_" + (int) c + "_");
            }
        }
        return buff.toString();
    }

    /**
     * End of an element.
     */

    @Override
    public void endElement() throws XPathException {
        String displayName = elementStack.pop();
        try {
            if (openStartTag) {
                writer.write(emptyElementTagCloser(displayName, elementCode));
                openStartTag = false;
            } else {
                writer.write("</");
                writer.write(displayName);
                writer.write('>');
            }
        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
    }

    /**
     * Character data.
     */

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (!started) {
            openDocument();
        }

        if (requireWellFormed && elementStack.isEmpty() && !Whitespace.isWhite(chars) && !unfailing) {
            XPathException err = new XPathException("When 'standalone' or 'doctype-system' is specified, " +
                                                            "the document must be well-formed; but this document contains a top-level text node");
            err.setErrorCode("SEPM0004");
            throw err;
        }

        try {
            if (openStartTag) {
                closeStartTag();
            }

            if (ReceiverOption.contains(properties, ReceiverOption.NO_SPECIAL_CHARS)) {
                writeCharSequence(chars);
            } else if (!ReceiverOption.contains(properties, ReceiverOption.DISABLE_ESCAPING)) {
                writeEscape(chars, false);
            } else {
                // disable-output-escaping="yes"
                if (testCharacters(chars) == 0) {
                    if (!ReceiverOption.contains(properties, ReceiverOption.USE_NULL_MARKERS)) {
                        // null (0) characters will be used before and after any section of
                        // the value generated from a character map
                        writeCharSequence(chars);
                    } else {
                        // Need to strip out any null markers. See test output-html109
                        final int len = chars.length();
                        for (int i = 0; i < len; i++) {
                            char c = chars.charAt(i);
                            if (c != 0) {
                                writer.write(c);
                            }
                        }
                    }
                } else {
                    // Using disable output escaping with characters
                    // that are not available in the target encoding
                    // The required action is to ignore d-o-e in respect of those characters that are
                    // not available in the encoding. This is slow...
                    final int len = chars.length();
                    for (int i = 0; i < len; i++) {
                        char c = chars.charAt(i);
                        if (c != 0) {
                            if (c > 127 && UTF16CharacterSet.isHighSurrogate(c)) {
                                char[] pair = new char[2];
                                pair[0] = c;
                                pair[1] = chars.charAt(++i);
                                int cc = UTF16CharacterSet.combinePair(c, pair[1]);
                                if (!characterSet.inCharset(cc)) {
                                    writeEscape(new CharSlice(pair), false);
                                } else {
                                    writeCharSequence(new CharSlice(pair));
                                }
                            } else {
                                char[] ca = {c};
                                if (!characterSet.inCharset(c)) {
                                    writeEscape(new CharSlice(ca), false);
                                } else {
                                    writeCharSequence(new CharSlice(ca));
                                }
                            }
                        }
                    }
                }
            }
        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
    }

    /**
     * Write a CharSequence (without any escaping of special characters): various implementations
     *
     * @param s the character sequence to be written
     * @throws java.io.IOException in the event of a failure to write to the output file
     */

    public void writeCharSequence(CharSequence s) throws java.io.IOException {
        if (s instanceof String) {
            writer.write((String) s);
        } else if (s instanceof CharSlice) {
            ((CharSlice) s).write(writer);
        } else if (s instanceof FastStringBuffer) {
            ((FastStringBuffer) s).write(writer);
        } else if (s instanceof CompressedWhitespace) {
            ((CompressedWhitespace) s).write(writer);
        } else {
            writer.write(s.toString());
        }
    }


    /**
     * Handle a processing instruction.
     */

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties)
            throws XPathException {
        if (!started) {
            openDocument();
        }
        int x = testCharacters(target);
        if (x != 0) {
            if (unfailing) {
                target = convertToAscii(target);
            } else {
                XPathException err = new XPathException("Character in processing instruction name cannot be represented " +
                                                                "in the selected encoding (code " + x + ')');
                err.setErrorCode("SERE0008");
                throw err;
            }
        }
        x = testCharacters(data);
        if (x != 0) {
            if (unfailing) {
                data = convertToAscii(data);
            } else {
                XPathException err = new XPathException("Character in processing instruction data cannot be represented " +
                                                                "in the selected encoding (code " + x + ')');
                err.setErrorCode("SERE0008");
                throw err;
            }
        }
        try {
            if (openStartTag) {
                closeStartTag();
            }
            writer.write("<?" + target + (data.length() > 0 ? ' ' + data.toString() : "") + "?>");
        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
    }

    /**
     * Write contents of array to current writer, after escaping special characters.
     * This method converts the XML special characters (such as &lt; and &amp;) into their
     * predefined entities.
     *
     * @param chars       The character sequence containing the string
     * @param inAttribute Set to true if the text is in an attribute value
     */

    protected void writeEscape(final CharSequence chars, final boolean inAttribute)
            throws java.io.IOException, XPathException {
        int segstart = 0;
        boolean disabled = false;
        final boolean[] specialChars = inAttribute ? attSpecials : specialInText;

        if (chars instanceof CompressedWhitespace) {
            ((CompressedWhitespace) chars).writeEscape(specialChars, writer);
            return;
        }

        final int clength = chars.length();
        while (segstart < clength) {
            int i = segstart;
            // find a maximal sequence of "ordinary" characters
            while (i < clength) {
                final char c = chars.charAt(i);
                if (c < 127) {
                    if (specialChars[c]) {
                        break;
                    } else {
                        i++;
                    }
                } else if (c < 160) {
                    break;
                } else if (c == 0x2028) {
                    break;
                } else if (UTF16CharacterSet.isHighSurrogate(c)) {
                    break;
                } else if (!characterSet.inCharset(c)) {
                    break;
                } else {
                    i++;
                }
            }

            // if this was the whole string write it out and exit
            if (i >= clength) {
                if (segstart == 0) {
                    writeCharSequence(chars);
                } else {
                    writeCharSequence(chars.subSequence(segstart, i));
                }
                return;
            }

            // otherwise write out this sequence
            if (i > segstart) {
                writeCharSequence(chars.subSequence(segstart, i));
            }

            // examine the special character that interrupted the scan
            final char c = chars.charAt(i);
            if (c == 0) {
                // used to switch escaping on and off
                disabled = !disabled;
            } else if (disabled) {
                if (c > 127) {
                    if (UTF16CharacterSet.isHighSurrogate(c)) {
                        int cc = UTF16CharacterSet.combinePair(c, chars.charAt(i + 1));
                        if (!characterSet.inCharset(cc)) {
                            XPathException de = new XPathException("Character x" + Integer.toHexString(cc) +
                                                                           " is not available in the chosen encoding");
                            de.setErrorCode("SERE0008");
                            throw de;
                        }
                    } else if (!characterSet.inCharset(c)) {
                        XPathException de = new XPathException("Character " + c + " (x" + Integer.toHexString((int) c) +
                                                                       ") is not available in the chosen encoding");
                        de.setErrorCode("SERE0008");
                        throw de;
                    }
                }
                writer.write(c);
            } else if (c < 127) {
                // process special ASCII characters
                switch (c) {
                    case '<':
                        writer.write("&lt;");
                        break;
                    case '>':
                        writer.write("&gt;");
                        break;
                    case '&':
                        writer.write("&amp;");
                        break;
                    case '\"':
                        writer.write("&#34;");
                        break;
                    case '\'':
                        writer.write("&#39;");
                        break;
                    case '\n':
                        writer.write("&#xA;");
                        break;
                    case '\r':
                        writer.write("&#xD;");
                        break;
                    case '\t':
                        writer.write("&#x9;");
                        break;
                    default:
                        // C0 control characters
                        characterReferenceGenerator.outputCharacterReference(c, writer);
                        break;
                }
            } else if (c < 160 || c == 0x2028) {
                // XML 1.1 requires these characters to be written as character references
                characterReferenceGenerator.outputCharacterReference(c, writer);
            } else if (UTF16CharacterSet.isHighSurrogate(c)) {
                char d = chars.charAt(++i);
                int charval = UTF16CharacterSet.combinePair(c, d);
                if (characterSet.inCharset(charval)) {
                    writer.write(c);
                    writer.write(d);
                } else {
                    characterReferenceGenerator.outputCharacterReference(charval, writer);
                }
            } else {
                // process characters not available in the current encoding
                characterReferenceGenerator.outputCharacterReference(c, writer);
            }
            segstart = ++i;
        }
    }


    /**
     * Handle a comment.
     */

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (!started) {
            openDocument();
        }
        int x = testCharacters(chars);
        if (x != 0) {
            if (unfailing) {
                chars = convertToAscii(chars);
            } else {
                XPathException err = new XPathException("Character in comment cannot be represented " +
                                                                "in the selected encoding (code " + x + ')');
                err.setErrorCode("SERE0008");
                throw err;
            }
        }
        try {
            if (openStartTag) {
                closeStartTag();
            }
            writer.write("<!--");
            writer.write(chars.toString());
            writer.write("-->");
        } catch (java.io.IOException err) {
            throw new XPathException("Failure writing to " + getSystemId(), err);
        }
    }

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     *
     * @return true if the Receiver makes any use of this information. If false, the caller
     * may supply untyped nodes instead of supplying the type annotation
     */

    @Override
    public boolean usesTypeAnnotations() {
        return false;
    }

    /**
     * Ask whether anything has yet been written
     *
     * @return true if content has been output
     */

    public boolean isStarted() {
        return started;
    }


}

