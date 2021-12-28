////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.CharSlice;
import net.sf.saxon.type.SchemaType;

import java.util.*;


/**
 * HTMLIndenter: This ProxyReceiver indents HTML elements, by adding whitespace
 * character data where appropriate.
 * The character data is never added when within an inline element.
 * The string used for indentation defaults to three spaces
 *
 * @author Michael Kay
 */


public class HTMLIndenter extends ProxyReceiver {

    // TODO: some of the logic in this class is probably redundant, e.g. the "sameLine" flag. However,
    // indentation is under-tested in the W3C test suites, so it's safest to avoid unnecessary changes.

    // The specification has complex rules for deciding whether something is an inline element and whether
    // indentation is suppressed: the rules for matching names depend on whether it's HTML or XHTML, and which
    // version. But since the rules are of the form: "if the name matches X, then indentation is not allowed,
    // otherwise indentation is allowed but not required", there is no harm in having spurious matches. So we
    // simply do a case-blind match on the local part of the name, which catches all cases where indentation
    // is not allowed, and is very unlikely to upset anyone by not indenting things that we could have indented.

    // We make one exception (see bug 3877): we don't treat "link" as an inline element under any circumstances,
    // though HTML5 has some complex rules that treat it as a phrasal element under some conditions, based on the
    // value of the "rel" attribute.

    final private static String[] formattedTags = {"pre", "script", "style", "textarea", "title", "xmp"};
    // "xmp" is obsolete but still encountered!

    // When elements are classified as inline, indenting whitespace is not added adjacent to the element.

    // See Saxon bug 3839 and W3C bug 30276. We use a list of inline elements that is the union of
    // the HTML4 and HTML5 lists, on the basis that no harm is done treating an element as inline
    // even if the spec doesn't require us to do so. This also means we include elements such as
    // "ins", "del", and "area" that are sometimes inline and sometimes not.

    final private static String[] inlineTags = {
            "a", "abbr", "acronym", "applet", "area",
            "audio", "b", "basefont", "bdi", "bdo", "big", "br", "button", "canvas", "cite", "code", "data",
            "datalist", "del", "dfn", "em", "embed", "font", "i", "iframe", "img", "input", "ins",
            "kbd", "label", /*"link" -- excluded, see bug 3877,*/ "map",
            "mark", "math", "meter", "noscript", "object", "output", "picture",
            "progress", "q", "ruby", "s", "samp", "script", "select", "small", "span",
            "strike", "strong", "sub", "sup", "svg", "template", "textarea",
            "time", "tt", "u", "var", "video", "wbr"};

    final private static Set<String> inlineTable = new HashSet<>(70);
    final private static Set<String> formattedTable = new HashSet<>(10);

    static {
        Collections.addAll(inlineTable, inlineTags);
        Collections.addAll(formattedTable, formattedTags);
    }

    protected char[] indentChars = {'\n', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '};

    private final static int IS_INLINE = 1;
    private final static int IS_FORMATTED = 2;
    private final static int IS_SUPPRESSED = 4;

    private String method;
    private int level = 0;
    private boolean sameLine = false;
    private boolean inFormattedTag = false;
    private boolean afterInline = false;
    //private boolean afterFormatted = true;    // to prevent a newline at the start

    private boolean afterEndElement = false;
    private int[] propertyStack = new int[20];
    private Set<String> suppressed = null;

    public HTMLIndenter(Receiver next, String method) {
        super(next);
    }

    /**
     * Set the properties for this indenter
     *
     * @param props the serialization properties
     */

    public void setOutputProperties(Properties props) {
        String s = props.getProperty(SaxonOutputKeys.SUPPRESS_INDENTATION);
        if (s != null) {
            suppressed = new HashSet<>(8);
            StringTokenizer st = new StringTokenizer(s, " \t\r\n");
            while (st.hasMoreTokens()) {
                String eqName = st.nextToken();
                suppressed.add(FingerprintedQName.fromEQName(eqName).getLocalPart().toLowerCase());
            }
        }
    }

    /**
     * Classify an element name as inline, formatted, or both or neither.
     * This method is overridden in the XHTML indenter
     *
     * @param name the element name
     * @return a bit-significant integer containing flags IS_INLINE and/or IS_FORMATTED
     */

    public int classifyTag(NodeName name) {
        int r = 0;
        if (inlineTable.contains(name.getLocalPart().toLowerCase())) {
            r |= IS_INLINE;
        }
        if (formattedTable.contains(name.getLocalPart().toLowerCase())) {
            r |= IS_FORMATTED;
        }
        if (suppressed != null && suppressed.contains(name.getLocalPart().toLowerCase())) {
            r |= IS_SUPPRESSED;
        }
        return r;
    }

    /**
     * Output element start tag
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        int withinSuppressed = level == 0 ? 0 : (propertyStack[level - 1] & IS_SUPPRESSED);
        int tagProps = classifyTag(elemName) | withinSuppressed;
        if (level >= propertyStack.length) {
            propertyStack = Arrays.copyOf(propertyStack, level * 2);
        }
        propertyStack[level] = tagProps;
        boolean inlineTag = (tagProps & IS_INLINE) != 0;
        if (!inlineTag && !inFormattedTag && !afterInline && /*!afterFormatted &&*/ withinSuppressed == 0 && level != 0) {
            indent();
        }

        nextReceiver.startElement(elemName, type, attributes, namespaces, location, properties);

        inFormattedTag = inFormattedTag || ((tagProps & IS_FORMATTED) != 0);
        level++;
        sameLine = true;
        afterInline = false;
        //afterFormatted = false;
        afterEndElement = false;
    }

    /**
     * Output element end tag
     */

    @Override
    public void endElement() throws XPathException {
        level--;
        boolean thisInline = (propertyStack[level] & IS_INLINE) != 0;
        boolean thisFormatted = (propertyStack[level] & IS_FORMATTED) != 0;
        boolean thisSuppressed = (propertyStack[level] & IS_SUPPRESSED) != 0;
        if (afterEndElement && !thisInline && !thisSuppressed && !afterInline &&
                !sameLine && !inFormattedTag) {
            indent();
            afterInline = false;
            //afterFormatted = false;
        } else {
            afterInline = thisInline;
            //afterFormatted = thisFormatted;
        }
        nextReceiver.endElement();
        inFormattedTag = inFormattedTag && !thisFormatted;
        sameLine = false;
        afterEndElement = true;
    }

    /**
     * Output character data
     */

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (inFormattedTag ||
                ReceiverOption.contains(properties, ReceiverOption.USE_NULL_MARKERS) ||
                ReceiverOption.contains(properties, ReceiverOption.DISABLE_ESCAPING)) {
            // don't split the text if in a tag such as <pre>, or if the text contains the result of
            // expanding a character map or was produced using disable-output-escaping
            nextReceiver.characters(chars, locationId, properties);
        } else {
            // otherwise try to split long lines into multiple lines
            int lastNL = 0;
            for (int i = 0; i < chars.length(); i++) {
                if (chars.charAt(i) == '\n' || (i - lastNL > getLineLength() && chars.charAt(i) == ' ')) {
                    sameLine = false;
                    nextReceiver.characters(chars.subSequence(lastNL, i), locationId, properties);
                    indent();
                    lastNL = i + 1;
                    while (lastNL < chars.length() && chars.charAt(lastNL) == ' ') {
                        lastNL++;
                    }
                }
            }
            if (lastNL < chars.length()) {
                nextReceiver.characters(chars.subSequence(lastNL, chars.length()), locationId, properties);
            }
        }
        afterInline = false;
        afterEndElement = false;
    }

    /**
     * Output a processing instruction
     */

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (afterEndElement && level != 0 && (propertyStack[level - 1] & IS_INLINE) == 0) {
            indent();
        }
        nextReceiver.processingInstruction(target, data, locationId, properties);
        afterEndElement = false;
    }

    /**
     * Output a comment
     */

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (afterEndElement && level != 0 && (propertyStack[level - 1] & IS_INLINE) == 0) {
            indent();
        }
        nextReceiver.comment(chars, locationId, properties);
        afterEndElement = false;
    }

    /**
     * Get the maximum length of lines, after which long lines will be word-wrapped
     *
     * @return the maximum line length
     */

    protected int getLineLength() {
        return 80;
    }

    /**
     * Output white space to reflect the current indentation level
     *
     * @throws net.sf.saxon.trans.XPathException if an error occurs downstream in the pipeline
     */

    private void indent() throws XPathException {
        int spaces = level * getIndentation();
        if (spaces + 1 >= indentChars.length) {
            int increment = 5 * getIndentation();
            if (spaces + 1 > indentChars.length + increment) {
                increment += spaces + 1;
            }
            char[] c2 = new char[indentChars.length + increment];
            System.arraycopy(indentChars, 0, c2, 0, indentChars.length);
            Arrays.fill(c2, indentChars.length, c2.length, ' ');
            indentChars = c2;
        }
        nextReceiver.characters(new CharSlice(indentChars, 0, spaces + 1),
                                Loc.NONE, ReceiverOption.NONE);
        sameLine = false;
    }

    /**
     * Get the number of spaces to be used for indentation
     *
     * @return the number of spaces to be added to the indentation for each level
     */

    protected int getIndentation() {
        return 3;
    }

}

