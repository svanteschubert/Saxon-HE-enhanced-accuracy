////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.s9api.Location;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.om.*;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;

/**
 * This receiver is inserted into the output pipeline whenever on-empty or on-non-empty is used (XSLT 3.0).
 * It passes all events to the underlying receiver unchanged, but invokes a callback action when the
 * first item is written.
 *
 * <p>A "Significant Item" as detected by this class is an item that is not "vacuous" according to
 * section 8.4.2 of the XSLT 3.0 specification. Note that this is not the same as being "deemed empty"
 * as defined in section 8.4.1: notably, empty elements and empty maps are "deemed empty" but are
 * not "vacuous".</p>
 */
public class SignificantItemDetector extends ProxyOutputter {

    private int level = 0;
    private boolean empty = true;
    private final Action trigger;

    public SignificantItemDetector(Outputter next, Action trigger) {
        super(next);
        this.trigger = trigger;
    }

    private void start() throws XPathException {
        if (/*level==0 && */empty) {
            trigger.doAction();
            empty = false;
        }
    }

    /**
     * Start of a document node.
     */
    @Override
    public void startDocument(int properties) throws XPathException {
        if (level++ != 0) {
            getNextOutputter().startDocument(properties);
        }
    }

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             Location location, int properties) throws XPathException {
        start();
        level++;
        getNextOutputter().startElement(elemName, type, location, properties);
    }

    /**
     * Notify the start of an element, supplying all attributes and namespaces
     */
    @Override
    public void startElement(NodeName elemName, SchemaType type, AttributeMap attributes, NamespaceMap namespaces, Location location, int properties) throws XPathException {
        start();
        level++;
        getNextOutputter().startElement(elemName, type, attributes, namespaces, location, properties);
    }

    /**
     * Notify a namespace binding.
     */
    @Override
    public void namespace(String prefix, NamespaceUri namespaceUri, int properties) throws XPathException {
        start();
        getNextOutputter().namespace(prefix, namespaceUri, properties);
    }

    /**
     * Notify an attribute.
     */
    @Override
    public void attribute(NodeName attName, SimpleType typeCode, String value, Location location, int properties) throws XPathException {
        start();
        getNextOutputter().attribute(attName, typeCode, value, location, properties);
    }

    /**
     * Notify the start of the content, that is, the completion of all attributes and namespaces.
     */
    @Override
    public void startContent() throws XPathException {
        getNextOutputter().startContent();
    }

    @Override
    public void characters(UnicodeString chars, Location locationId, int properties) throws XPathException {
        if (!chars.isEmpty()) {
            start();
        }
        getNextOutputter().characters(chars, locationId, properties);
    }

    @Override
    public void processingInstruction(String target, UnicodeString data, Location locationId, int properties) throws XPathException {
        start();
        getNextOutputter().processingInstruction(target, data, locationId, properties);
    }

    @Override
    public void comment(UnicodeString chars, Location locationId, int properties) throws XPathException {
        start();
        getNextOutputter().comment(chars, locationId, properties);
    }

    public static boolean isSignificant(Item item) {
        if (item instanceof NodeInfo) {
            NodeInfo node = (NodeInfo) item;
            return (node.getNodeKind() != Type.TEXT || !node.getUnicodeStringValue().isEmpty())
                    && (node.getNodeKind() != Type.DOCUMENT || node.hasChildNodes());
        } else if (item instanceof AtomicValue) {
            return !item.getUnicodeStringValue().isEmpty();
        } else if (item instanceof ArrayItem) {
            if (((ArrayItem) item).isEmpty()) {
                return true;
            } else {
                for (Sequence mem : ((ArrayItem) item).members()) {
                    try {
                        SequenceIterator memIter = mem.iterate();
                        Item it;
                        while ((it = memIter.next()) != null) {
                            if (isSignificant(it)) {
                                return true;
                            }
                        }
                    } catch (UncheckedXPathException e) {
                        return true;
                    }
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public void append(Item item, Location locationId, int copyNamespaces) throws XPathException {
        if (isSignificant(item)) {
            start();
        }
        super.append(item, locationId, copyNamespaces);
    }

    @Override
    public void append(Item item) throws XPathException {
        if (isSignificant(item)) {
            start();
        }
        super.append(item);
    }

    /**
     * Notify the end of a document node
     */
    @Override
    public void endDocument() throws XPathException {
        if (--level != 0) {
            getNextOutputter().endDocument();
        }
    }

    /**
     * End of element
     */
    @Override
    public void endElement() throws XPathException {
        level--;
        getNextOutputter().endElement();
    }

    /**
     * Ask if the sequence that has been written so far is considered empty
     * @return true if no significant items have been written (or started)
     */

    public boolean isEmpty() {
        return empty;
    }

}

