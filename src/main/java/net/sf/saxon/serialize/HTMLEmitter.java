////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.om.AttributeMap;
import net.sf.saxon.om.NamespaceMap;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.util.Stack;

/**
 * This class generates HTML output
 */

public abstract class HTMLEmitter extends XMLEmitter {

    /**
     * Preferred character representations
     */

    private static final int REP_NATIVE = 0;
    private static final int REP_ENTITY = 1;
    private static final int REP_DECIMAL = 2;
    private static final int REP_HEX = 3;

    private final int nonASCIIRepresentation = REP_NATIVE;
    private final int excludedRepresentation = REP_ENTITY;

    private int inScript;
    protected int version = 5;
    private String parentElement;
    private NamespaceUri uri;
    private boolean escapeNonAscii = false;
    private final Stack<NodeName> nodeNameStack = new Stack<>();

    /**
     * Decode preferred representation
     *
     * @param rep string containing preferred representation (native, entity, decimal, or hex)
     * @return integer code for the preferred representation
     */

    private static int representationCode(String rep) {
        rep = rep.toLowerCase();
        switch (rep) {
            case "native":
                return REP_NATIVE;
            case "entity":
                return REP_ENTITY;
            case "decimal":
                return REP_DECIMAL;
            case "hex":
                return REP_HEX;
            default:
                return REP_ENTITY;
        }
    }

    /**
     * Table of HTML tags that have no closing tag
     */

    static HTMLTagHashSet emptyTags = new HTMLTagHashSet(31);


    protected static void setEmptyTag(String tag) {
        emptyTags.add(tag);
    }

    protected static boolean isEmptyTag(String tag) {
        return emptyTags.contains(tag);
    }

    /**
     * Table of boolean attributes
     */

    // we use two HashMaps to avoid unnecessary string concatenations

    // Sizes must be large enough: this hash set cannot grow beyond the initial size
    private static final HTMLTagHashSet booleanAttributes = new HTMLTagHashSet(43);
    private static final HTMLTagHashSet booleanCombinations = new HTMLTagHashSet(57);

    // See http://www.w3.org/TR/html5/index.html#attributes-1 (checked 2014-01-07)

    static {
        setBooleanAttribute("*", "hidden"); // HTML5
        setBooleanAttribute("area", "nohref");
        setBooleanAttribute("audio", "autoplay"); // HTML5
        setBooleanAttribute("audio", "controls"); // HTML5
        setBooleanAttribute("audio", "loop"); // HTML5
        setBooleanAttribute("audio", "muted"); // HTML5
        setBooleanAttribute("button", "disabled");
        setBooleanAttribute("button", "autofocus"); // HTML5
        setBooleanAttribute("button", "formnovalidate"); //HTML5
        setBooleanAttribute("details", "open"); // HTML5
        setBooleanAttribute("dialog", "open"); // HTML5
        setBooleanAttribute("dir", "compact");
        setBooleanAttribute("dl", "compact");
        setBooleanAttribute("fieldset", "disabled"); //HTML5
        setBooleanAttribute("form", "novalidate"); // HTML5
        setBooleanAttribute("frame", "noresize");
        setBooleanAttribute("hr", "noshade");
        setBooleanAttribute("img", "ismap");
        setBooleanAttribute("input", "checked");
        setBooleanAttribute("input", "disabled");
        setBooleanAttribute("input", "multiple"); //HTML5
        setBooleanAttribute("input", "readonly");
        setBooleanAttribute("input", "required"); //HTML5
        setBooleanAttribute("input", "autofocus"); // HTML5
        setBooleanAttribute("input", "formnovalidate"); //HTML5
        setBooleanAttribute("iframe", "seamless"); // HTML5
        setBooleanAttribute("keygen", "autofocus"); // HTML5
        setBooleanAttribute("keygen", "disabled"); //HTML5
        setBooleanAttribute("menu", "compact");
        setBooleanAttribute("object", "declare");
        setBooleanAttribute("object", "typemustmatch"); // HTML5
        setBooleanAttribute("ol", "compact");
        setBooleanAttribute("ol", "reversed"); // HTML5
        setBooleanAttribute("optgroup", "disabled");
        setBooleanAttribute("option", "selected");
        setBooleanAttribute("option", "disabled");
        setBooleanAttribute("script", "defer");
        setBooleanAttribute("script", "async");   // HTML5
        setBooleanAttribute("select", "multiple");
        setBooleanAttribute("select", "disabled");
        setBooleanAttribute("select", "autofocus"); // HTML5
        setBooleanAttribute("select", "required"); // HTML5
        setBooleanAttribute("style", "scoped"); // HTML5
        setBooleanAttribute("td", "nowrap");
        setBooleanAttribute("textarea", "disabled");
        setBooleanAttribute("textarea", "readonly");
        setBooleanAttribute("textarea", "autofocus"); // HTML5
        setBooleanAttribute("textarea", "required"); // HTML5
        setBooleanAttribute("th", "nowrap");
        setBooleanAttribute("track", "default"); // HTML5
        setBooleanAttribute("ul", "compact");
        setBooleanAttribute("video", "autoplay"); // HTML5
        setBooleanAttribute("video", "controls"); // HTML5
        setBooleanAttribute("video", "loop"); // HTML5
        setBooleanAttribute("video", "muted"); // HTML5
    }

    private static void setBooleanAttribute(String element, String attribute) {
        booleanAttributes.add(attribute);
        booleanCombinations.add(element + '+' + attribute);
    }

    private static boolean isBooleanAttribute(String element, String attribute, String value) {
        return attribute.equalsIgnoreCase(value) &&
                booleanAttributes.contains(attribute) &&
                ( booleanCombinations.contains(element + '+' + attribute) ||
                  booleanCombinations.contains("*+" + attribute));
    }

    /**
     * Constructor
     */

    public HTMLEmitter() {

    }

    /**
     * Say that all non-ASCII characters should be escaped, regardless of the character encoding
     *
     * @param escape true if all non ASCII characters should be escaped
     */

    @Override
    public void setEscapeNonAscii(Boolean escape) {
        escapeNonAscii = escape;
    }

    /**
     * Decide whether an element is "serialized as an HTML element" in the language of the 3.0 specification
     *
     * @param name the name of the element
     * @return true if the element is to be serialized as an HTML element
     */

    protected abstract boolean isHTMLElement(NodeName name);

    /**
     * Output start of document
     */

    @Override
    public void open() throws XPathException {
    }

    @Override
    protected void openDocument() throws XPathException {
        assert writer != null;
//        if (writer == null) {
//            makeWriter();
//        }
        if (started) {
            return;
        }
        String byteOrderMark = outputProperties.getProperty(SaxonOutputKeys.BYTE_ORDER_MARK);

        if ("yes".equals(byteOrderMark) &&
                "UTF-8".equalsIgnoreCase(outputProperties.getProperty(OutputKeys.ENCODING))) {
            try {
                writer.writeCodePoint(0xFEFF);
            } catch (java.io.IOException err) {
                // Might be an encoding exception; just ignore it
            }
        }
        if ("yes".equals(outputProperties.getProperty(SaxonOutputKeys.SINGLE_QUOTES))) {
            delimiter = '\'';
            attSpecials = specialInAttSingle;
        }
        inScript = -1000000;
    }

    /**
     * Output the document type declaration
     *
     * @param displayName The element name
     * @param systemId    The DOCTYPE system identifier
     * @param publicId    The DOCTYPE public identifier
     */

    @Override
    protected void writeDocType(NodeName name, String displayName, String systemId, String publicId) throws XPathException {
        super.writeDocType(name, displayName, systemId, publicId);
    }

    /**
     * Output element start tag
     *
     * @param elemName   the name of the element
     * @param type       the type annotation of the element
     * @param attributes the attributes of this element
     * @param namespaces the in-scope namespaces of this element: generally this is all the in-scope
     *                   namespaces, without relying on inheriting namespaces from parent elements
     * @param location   an object providing information about the module, line, and column where the node originated
     * @param properties bit-significant properties of the element node. If there are no relevant
     *                   properties, zero is supplied. The definitions of the bits are in class {@link ReceiverOption}
     * @throws XPathException if an error occurs
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        uri = elemName.getNamespaceUri();
        super.startElement(elemName, type, attributes, namespaces, location, properties);
        parentElement = elementStack.peek();
        if (isHTMLElement(elemName) &&
                (parentElement.equalsIgnoreCase("script") ||
                        parentElement.equalsIgnoreCase("style"))) {
            inScript = 0;
        }
        inScript++;
        nodeNameStack.push(elemName);
    }

    public void startContentOLD() throws XPathException {
        closeStartTag();                   // prevent <xxx/> syntax
    }

    /**
     * Write attribute name=value pair. Overrides the XML behaviour if the name and value
     * are the same (we assume this is a boolean attribute to be minimised), or if the value is
     * a URL.
     */

    @Override
    protected void writeAttribute(NodeName elCode, String attname, String value, int properties) throws XPathException {
        try {
            if (isHTMLElement(elCode)) {
                if (isBooleanAttribute(elCode.getLocalPart(), attname, value)) {
                    writer.write(attname);
                    return;
                }
            }
            if (inScript > 0) {
                properties |= ReceiverOption.DISABLE_ESCAPING;
            }
            super.writeAttribute(elCode, attname, value, properties);
        } catch (java.io.IOException err) {
            throw new XPathException(err);
        }
    }


    /**
     * Escape characters. Overrides the XML behaviour
     */

    @Override
    protected void writeEscape(UnicodeString chars, final boolean inAttribute)
            throws java.io.IOException, XPathException {

        int segstart = 0;
        final boolean[] specialChars = inAttribute ? attSpecials : specialInText;

        if (chars instanceof WhitespaceString) {
            ((WhitespaceString) chars).writeEscape(specialChars, writer);
            return;
        }
        boolean disabled = false;
        chars = chars.tidy();
        int[] codePoints = StringTool.expand(chars);
        while (segstart < codePoints.length) {
            int i = segstart;

            // find a maximal sequence of "ordinary" characters

            if (escapeNonAscii) {
                int c;
                while (i < codePoints.length && (c = codePoints[i]) < 127 && !specialChars[c]) {
                    i++;
                }
            } else {
                int c;
                while (i < codePoints.length &&
                        ((c = codePoints[i]) < 127 ? !specialChars[c] : (characterSet.inCharset(c) && c > 160))) {
                    i++;
                }
            }

            // if this was the whole string, output the string and quit

            if (i == codePoints.length) {
                writer.write(chars.substring(segstart));
                return;
            }

            // otherwise, output this sequence and continue
            if (i > segstart) {
                writer.write(chars.substring(segstart, i));
            }

            final int ch = codePoints[i];
            if (ch == 0) {
                // used to switch escaping on and off
                disabled = !disabled;
            } else if (disabled) {
                writeCodePoint(ch);
            } else if (ch <= 127) {
                // handle a special ASCII character
                if (inAttribute) {
                    if (ch == '<') {
                        writer.writeCodePoint('<');      // not escaped
                    } else if (ch == '>') {
                        writer.writeAscii(StringConstants.ESCAPE_GT);   // recommended for older browsers
                    } else if (ch == '&') {
                        if (i + 1 < codePoints.length && codePoints[i + 1] == '{') {
                            writer.writeCodePoint('&');                   // not escaped if followed by '{'
                        } else {
                            writer.writeAscii(StringConstants.ESCAPE_AMP);
                        }
                    } else if (ch == '\"') {
                        writer.writeAscii(StringConstants.ESCAPE_QUOT);
                    } else if (ch == '\'') {
                        writer.writeAscii(StringConstants.ESCAPE_APOS);
                    } else if (ch == '\n') {
                        writer.writeAscii(StringConstants.ESCAPE_NL);
                    } else if (ch == '\t') {
                        writer.writeAscii(StringConstants.ESCAPE_TAB);
                    } else if (ch == '\r') {
                        writer.writeAscii(StringConstants.ESCAPE_CR);
                    }
                } else {
                    if (ch == '<') {
                        writer.writeAscii(StringConstants.ESCAPE_LT);
                    } else if (ch == '>') {
                        writer.writeAscii(StringConstants.ESCAPE_GT);
                    } else if (ch == '&') {
                        writer.writeAscii(StringConstants.ESCAPE_AMP);
                    } else if (ch == '\r') {
                        writer.writeAscii(StringConstants.ESCAPE_CR);
                    }
                }

            } else if (ch < 160) {
                if (rejectControlCharacters()) {
                    // these control characters are illegal in HTML
                    throw new XPathException("Illegal HTML character: decimal " + ch, "SERE0014");
                } else {
                    characterReferenceGenerator.outputCharacterReference(ch, writer);
                }

            } else if (ch == 160) {
                // always output NBSP as an entity reference
                writer.writeAscii(StringConstants.ESCAPE_NBSP);

            } else if (ch > 65535 || escapeNonAscii || !characterSet.inCharset(ch)) {
                characterReferenceGenerator.outputCharacterReference(ch, writer);
            } else {
                writer.writeCodePoint(ch);
            }
            segstart = ++i;
        }

    }

    /**
     * Ask whether control characters should be rejected: true for HTML4, false for HTML5
     * @return true if control characters should be rejected
     */

    protected abstract boolean rejectControlCharacters();

    /**
     * Close an empty element tag. (This is overridden in XHTMLEmitter).
     *  @param displayName the name of the empty element
     * @param nameCode    the fingerprint of the name of the empty element
     */

    @Override
    protected void writeEmptyElementTagCloser(String displayName, NodeName nameCode) throws IOException {
        if (isHTMLElement(nameCode)) {
            writer.writeAscii(StringConstants.EMPTY_TAG_MIDDLE);
            writer.write(displayName);
            writer.writeCodePoint('>');
        } else {
            writer.writeAscii(StringConstants.EMPTY_TAG_END);
        }
    }

    /**
     * Output an element end tag.
     */

    @Override
    public void endElement() throws XPathException {
        NodeName nodeName = nodeNameStack.pop();
        String name = elementStack.peek();
        inScript--;
        if (inScript == 0) {
            inScript = -1000000;
        }

        if (isEmptyTag(name) && isHTMLElement(nodeName)) {
            if (openStartTag) {
                closeStartTag();
            }
            // no end tag required
            elementStack.pop();
        } else {
            super.endElement();
        }

    }

    /**
     * Character data.
     */

    @Override
    public void characters(UnicodeString chars, Location locationId, int properties)
            throws XPathException {
        if (inScript > 0) {
            properties |= ReceiverOption.DISABLE_ESCAPING;
        }
        super.characters(chars, locationId, properties);
    }

    /**
     * Handle a processing instruction.
     */

    @Override
    public void processingInstruction(String target, UnicodeString data, Location locationId, int properties)
            throws XPathException {
        if (!started) {
            openDocument();
        }
        UnicodeString t = data.tidy();
        if (t.indexOf('>') >= 0) {
            throw new XPathException("A processing instruction in HTML must not contain a > character", "SERE0015");
        }
        try {
            if (openStartTag) {
                closeStartTag();
            }
            writer.writeAscii(StringConstants.PI_START);
            writer.write(target);
            writer.writeCodePoint(' ');
            writer.write(t);
            writer.writeCodePoint('>');
        } catch (java.io.IOException err) {
            throw new XPathException(err);
        }
    }


}

