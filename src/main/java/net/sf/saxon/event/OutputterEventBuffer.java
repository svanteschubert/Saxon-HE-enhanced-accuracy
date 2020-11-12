////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.event;

import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

import java.util.ArrayList;
import java.util.List;

/**
 * An <tt>EventBuffer</tt> is a receiver of events that records the events in memory
 * for subsequent replay. It is used, for example, in the implementation of try/catch,
 * where events cannot be written directly to the final serializer in case an error
 * occurs and is caught.
 * <p>Note that events are retained along with their properties, so the class implements
 * "sticky disable-output-escaping" - text nodes can have selected characters marked
 * with the disable-escaping property.</p>
 * @since 9.9
 */

public class OutputterEventBuffer extends Outputter {

    private List<OutputterEvent> buffer = new ArrayList<>();

    public OutputterEventBuffer() {
    }

    public void setBuffer(List<OutputterEvent> buffer) {
        this.buffer = buffer;
    }

    @Override
    public void startDocument(int properties) throws XPathException {
        buffer.add(new OutputterEvent.StartDocument(properties));
    }

    @Override
    public void endDocument() throws XPathException {
        buffer.add(new OutputterEvent.EndDocument());
    }

    @Override
    public void startElement(NodeName elemName, SchemaType typeCode,
                             Location location, int properties) {
        buffer.add(new OutputterEvent.StartElement(elemName, typeCode, location, properties));
    }

    @Override
    public void startElement(NodeName elemName, SchemaType type, AttributeMap attributes, NamespaceMap namespaces, Location location, int properties) throws XPathException {
        buffer.add(new OutputterEvent.StartElement(elemName, type, location, properties));
        for (AttributeInfo att : attributes) {
            buffer.add(new OutputterEvent.Attribute(att.getNodeName(), att.getType(), att.getValue(), att.getLocation(), att.getProperties()));
        }
        for (NamespaceBinding binding : namespaces) {
            buffer.add(new OutputterEvent.Namespace(binding.getPrefix(), binding.getURI(), properties));
        }
        buffer.add(new OutputterEvent.StartContent());
    }

    @Override
    public void attribute(NodeName name, SimpleType type, CharSequence value,
                             Location location, int properties) {
        buffer.add(new OutputterEvent.Attribute(name, type, value.toString(), location, properties));
    }

    @Override
    public void namespace(String prefix, String uri, int properties) {
        buffer.add(new OutputterEvent.Namespace(prefix, uri, properties));
    }

    @Override
    public void startContent()  {
        buffer.add(new OutputterEvent.StartContent());
    }

    @Override
    public void endElement() throws XPathException {
        buffer.add(new OutputterEvent.EndElement());
    }

    @Override
    public void characters(CharSequence chars, Location location, int properties) {
        buffer.add(new OutputterEvent.Text(chars, location, properties));
    }

    @Override
    public void processingInstruction(String name, CharSequence data, Location location, int properties) {
        buffer.add(new OutputterEvent.ProcessingInstruction(name, data, location, properties));
    }

    @Override
    public void comment(CharSequence content, Location location, int properties) {
        buffer.add(new OutputterEvent.Comment(content, location, properties));
    }

    @Override
    public void append(Item item, Location location, int properties) {
        buffer.add(new OutputterEvent.Append(item, location, properties));
    }

    @Override
    public void close() {
        // no action
    }

    /**
     * Replay the captured events to a supplied destination
     * @param out the destination {@code Receiver} to receive the events
     * @throws XPathException if any error occurs
     */

    public void replay(Outputter out) throws XPathException {
        for (OutputterEvent event : buffer) {
            event.replay(out);
        }
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    public void reset() {
        buffer.clear();
    }
}

