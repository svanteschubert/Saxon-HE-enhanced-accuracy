////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.event;

import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

/**
 * An <tt>EventMonitor</tt> is a filter that passes all events down the pipeline unchanged,
 * keeping a note of whether any data has passed through the filter. At any stage it is possible
 * to ask whether any data has been written.
 * @since 9.9
 */

public class EventMonitor extends Outputter {

    private boolean written = false;
    private final Outputter next;

    public EventMonitor(Outputter next){
        this.next = next;
    }

    @Override
    public void startDocument(int properties) throws XPathException {
        written = true;
        next.startDocument(properties);
    }

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             Location location, int properties) throws XPathException {
        written = true;
        next.startElement(elemName, type, location, properties);
    }

    @Override
    public void endElement() throws XPathException {
        written = true;
        next.endElement();
    }

    @Override
    public void attribute(NodeName name, SimpleType type, CharSequence value,
                             Location location, int properties) throws XPathException {
        written = true;
        next.attribute(name, type, value, location, properties);
    }

    @Override
    public void namespace(String prefix, String uri, int properties) throws XPathException {
        written = true;
        next.namespace(prefix, uri, properties);
    }

    @Override
    public void startContent() throws XPathException {
        written = true;
        next.startContent();
    }

    @Override
    public void characters(CharSequence chars, Location location, int properties) throws XPathException {
        written = true;
        next.characters(chars, location, properties);
    }

    @Override
    public void processingInstruction(String name, CharSequence data, Location location, int properties) throws XPathException {
        written = true;
        next.processingInstruction(name, data, location, properties);
    }

    @Override
    public void comment(CharSequence content, Location location, int properties) throws XPathException {
        written = true;
        next.comment(content, location, properties);
    }

    @Override
    public void append(Item item, Location location, int properties) throws XPathException {
        written = true;
        next.append(item, location, properties);
    }

    @Override
    public void endDocument() throws XPathException {
        written = true;
        next.endDocument();
    }

    public boolean hasBeenWrittenTo() {
        return written;
    }
}

