////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.om.AttributeMap;
import net.sf.saxon.om.NamespaceMap;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

import java.util.function.Predicate;

/**
 * The CommentStripper class is a filter that removes all comments and processing instructions.
 * It also concatenates text nodes that are split by comments and PIs. This follows the rules for
 * processing stylesheets; it is also used for removing comments and PIs from the tree seen
 * by XPath expressions used to process XSD 1.1 assertions.
 *
 * <p>It also has the ability to deep-skip xsl:note (or any other) elements, as required for XSLT 4.0.</p>
 *
 */


public class CommentStripper extends ProxyReceiver {

    private UnicodeString currentTextNode = null;
    private Predicate<NodeName> skippedElementTest = (NodeName name) -> false;
    private int depthOfHole = 0;

    /**
     * Default constructor for use in subclasses
     *
     * @param next the next receiver in the pipeline
     */

    public CommentStripper(Receiver next) {
        super(next);
    }

    public void setSkippedElementTest(Predicate<NodeName> test) {
        this.skippedElementTest = test;
    }

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties)
            throws XPathException {
        if (depthOfHole == 0) {
            if (skippedElementTest.test(elemName)) {
                depthOfHole++;
            } else {
                flush();
                nextReceiver.startElement(elemName, type, attributes, namespaces, location, properties);
            }
        } else {
            depthOfHole++;
        }
    }

    /**
     * Callback interface for SAX: not for application use
     */

    @Override
    public void endElement() throws XPathException {
        if (depthOfHole > 0) {
            depthOfHole--;
        } else {
            flush();
            nextReceiver.endElement();
        }
    }

    /**
     * Handle a text node. Because we're often handling stylesheets on this path, whitespace text
     * nodes will often be stripped but we can't strip them immediately because of the case
     * [element]   [!-- comment --]text[/element], where the space before the comment is considered
     * significant. But it's worth going to some effort to avoid uncompressing the whitespace in the
     * more common case, so that it can easily be detected and stripped downstream.
     */

    @Override
    public void characters(UnicodeString chars, Location locationId, int properties) throws XPathException {
        if (depthOfHole == 0) {
            if (currentTextNode == null) {
                currentTextNode = chars;
            } else {
                currentTextNode = currentTextNode.concat(chars);
            }
        }
    }

    /**
     * Remove comments
     */

    @Override
    public void comment(UnicodeString chars, Location locationId, int properties) {
    }

    /**
     * Remove processing instructions
     */

    @Override
    public void processingInstruction(String name, UnicodeString data, Location locationId, int properties) {
    }

    /**
     * Flush the character buffer
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if a failure occurs writing the output
     */

    private void flush() throws XPathException {
        if (currentTextNode != null) {
            nextReceiver.characters(currentTextNode, Loc.NONE, ReceiverOption.NONE);
        }
        currentTextNode = null;
    }

}

