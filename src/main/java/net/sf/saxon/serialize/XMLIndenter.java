////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.Event;
import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.CharSlice;
import net.sf.saxon.type.AnyType;
import net.sf.saxon.type.ComplexType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.Whitespace;

import javax.xml.transform.OutputKeys;
import java.util.*;

/**
 * XMLIndenter: This ProxyReceiver indents elements, by adding character data where appropriate.
 * The character data is always added as "ignorable white space", that is, it is never added
 * adjacent to existing character data.
 *
 * @author Michael Kay
 */


public class XMLIndenter extends ProxyReceiver {

    private int level = 0;

    protected char[] indentChars = {'\n', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '};
    private boolean sameline = false;
    private boolean afterStartTag = false;
    private boolean afterEndTag = true;
    private Event.Text pendingWhitespace = null;
    private int line = 0;       // line and column measure the number of lines and columns
    private int column = 0;     // .. in whitespace text nodes between tags
    private int suppressedAtLevel = -1;
    private Set<NodeName> suppressedElements = null;
    private XMLEmitter emitter;
    //private AttributeCollectionImpl bufferedAttributes;
    //private List<NamespaceBindingSet> bufferedNamespaces = new ArrayList<NamespaceBindingSet>(8);


    /**
     * Create an XML Indenter
     *
     * @param next the next receiver in the pipeline, always an XMLEmitter
     */

    public XMLIndenter(XMLEmitter next) {
        super(next);
        emitter = next;
        //bufferedAttributes = new AttributeCollectionImpl(getConfiguration());
    }

    /**
     * Set the properties for this indenter
     *
     * @param props the serialization properties
     */

    public void setOutputProperties(Properties props) {

        String omit = props.getProperty(OutputKeys.OMIT_XML_DECLARATION);
        afterEndTag = omit == null || !"yes".equals(Whitespace.trim(omit)) ||
                props.getProperty(OutputKeys.DOCTYPE_SYSTEM) != null;
        String s = props.getProperty(SaxonOutputKeys.SUPPRESS_INDENTATION);
        if (s == null) {
            s = props.getProperty("{http://saxon.sf.net/}suppress-indentation");
            // for compatibility: since 9.3 also available in default namespace
        }
        if (s != null) {
            suppressedElements = new HashSet<>(8);
            StringTokenizer st = new StringTokenizer(s, " \t\r\n");
            while (st.hasMoreTokens()) {
                String eqName = st.nextToken();
                suppressedElements.add(FingerprintedQName.fromEQName(eqName));
            }
        }

    }

    /**
     * Start of document
     */

    @Override
    public void open() throws XPathException {
        emitter.open();
    }

    /**
     * Output element start tag
     */

    @Override
    public void startElement(NodeName nameCode, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        if (afterStartTag || afterEndTag) {
            if (isDoubleSpaced(nameCode)) {
                nextReceiver.characters("\n", location, ReceiverOption.NONE);
                line = 0;
                column = 0;
            }
            indent();
        } else {
            flushPendingWhitespace();
        }

        level++;
        if (suppressedAtLevel < 0) {
            String xmlSpace = attributes.getValue(NamespaceConstant.XML, "space");
            if (xmlSpace != null && xmlSpace.trim().equals("preserve")) {
                // Note, we are suppressing indentation within an xml:space="preserve" region even if a descendant
                // specifies xml:space="default"
                suppressedAtLevel = level;
            }
        }

        sameline = true;
        afterStartTag = true;
        afterEndTag = false;
        line = 0;
        if (suppressedElements != null && suppressedAtLevel == -1 && suppressedElements.contains(nameCode)) {
            suppressedAtLevel = level;
        }
        if (type != AnyType.getInstance() && type != Untyped.getInstance() && suppressedAtLevel < 0
            && type.isComplexType() && ((ComplexType) type).isMixedContent()) {
            // suppress indentation for elements with mixed content. (Note this also suppresses
            // indentation for all descendants of such elements. We could be smarter than this.)
            suppressedAtLevel = level;
        }

        // Calculate indentation to be applied to attributes/namespaces
        if (suppressedAtLevel < 0) {
            int len = 0;
            for (NamespaceBindingSet nbs : namespaces) {
                for (NamespaceBinding binding : nbs) {
                    String prefix = binding.getPrefix();
                    if (prefix.isEmpty()) {
                        len += 9 + binding.getURI().length();
                    } else {
                        len += prefix.length() + 10 + binding.getURI().length();
                    }
                }
            }
            for (AttributeInfo att : attributes) {
                NodeName name = att.getNodeName();
                String prefix = name.getPrefix();
                len += name.getLocalPart().length()
                        + att.getValue().length()
                        + 4 + (prefix.isEmpty() ? 4 : prefix.length() + 5);
            }
            if (len > getLineLength()) {
                int indent = (level - 1) * getIndentation() + 3;
                emitter.setIndentForNextAttribute(indent);
            }
        }
        nextReceiver.startElement(nameCode, type, attributes, namespaces, location, properties);
    }

    /**
     * Output element end tag
     */

    @Override
    public void endElement() throws XPathException {
        level--;
        if (afterEndTag && !sameline) {
            indent();
        } else {
            flushPendingWhitespace();
        }
        emitter.endElement();
        sameline = false;
        afterEndTag = true;
        afterStartTag = false;
        line = 0;
        if (level == (suppressedAtLevel - 1)) {
            suppressedAtLevel = -1;
            // remove the suppression of indentation
        }
    }

    /**
     * Output a processing instruction
     */

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (afterEndTag) {
            indent();
        } else {
            flushPendingWhitespace();
        }
        emitter.processingInstruction(target, data, locationId, properties);
        //afterStartTag = false;
        //afterEndTag = false;
    }

    /**
     * Output character data
     */

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (suppressedAtLevel < 0 && Whitespace.isWhite(chars)) {
            pendingWhitespace = new Event.Text(chars, locationId, properties);
        } else {
            flushPendingWhitespace();
            for (int i = 0; i < chars.length(); i++) {
                char c = chars.charAt(i);
                if (c == '\n') {
                    sameline = false;
                    line++;
                    column = 0;
                }
                column++;
            }
            emitter.characters(chars, locationId, properties);
            afterStartTag = false;
            afterEndTag = false;
        }
    }

    /**
     * Output a comment
     */

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (afterEndTag) {
            indent();
        } else {
            flushPendingWhitespace();
        }
        emitter.comment(chars, locationId, properties);
        //afterStartTag = false;
        //afterEndTag = false;
    }

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     *
     * @return true if the Receiver makes any use of this information. If false, the caller
     *         may supply untyped nodes instead of supplying the type annotation
     */

    @Override
    public boolean usesTypeAnnotations() {
        return true;
    }

    /**
     * Output white space to reflect the current indentation level
     *
     * @throws XPathException if a downstream error occurs doing the output
     */

    private void indent() throws XPathException {
        if (suppressedAtLevel >= 0) {
            // indentation has been suppressed (e.g. by xmlspace="preserve")
            flushPendingWhitespace();
            return;
        }
        pendingWhitespace = null; // if we're adding new whitespace, we're allowed to discard existing whitespace
        int spaces = level * getIndentation();
        if (line > 0) {
            spaces -= column;
            if (spaces <= 0) {
                return;     // there's already enough white space, don't add more
            }
        }
        if (spaces + 2 >= indentChars.length) {
            int increment = 5 * getIndentation();
            if (spaces + 2 > indentChars.length + increment) {
                increment += spaces + 2;
            }
            char[] c2 = new char[indentChars.length + increment];
            System.arraycopy(indentChars, 0, c2, 0, indentChars.length);
            Arrays.fill(c2, indentChars.length, c2.length, ' ');
            indentChars = c2;
        }
        // output the initial newline character only if line==0
        int start = line == 0 ? 0 : 1;
        //super.characters(indentChars.subSequence(start, start+spaces+1), 0, ReceiverOption.NO_SPECIAL_CHARS);
        emitter.characters(new CharSlice(indentChars, start, spaces + 1),
                           Loc.NONE, ReceiverOption.NO_SPECIAL_CHARS);
        sameline = false;
    }

    private void flushPendingWhitespace() throws XPathException {
        if (pendingWhitespace != null) {
            pendingWhitespace.replay(nextReceiver);
            pendingWhitespace = null;
        }
    }

    @Override
    public void endDocument() throws XPathException {
        if (afterEndTag) {
            emitter.characters("\n", Loc.NONE, ReceiverOption.NONE);  // if permitted, output a trailing newline, for tidier console output
        }
        super.endDocument();
    }

    /**
     * Get the number of spaces to be used for indentation
     *
     * @return the number of spaces to be added to the indentation for each level
     */

    protected int getIndentation() {
        return 3;
    }

    /**
     * Ask whether a particular element is to be double-spaced
     *
     * @param name the element name
     * @return true if double-spacing is in effect for this element
     */

    protected boolean isDoubleSpaced(NodeName name) {
        return false;
    }

    /**
     * Get the suggested maximum length of a line
     *
     * @return the suggested maximum line length (used for wrapping attributes)
     */

    protected int getLineLength() {
        return 80;
    }
}

