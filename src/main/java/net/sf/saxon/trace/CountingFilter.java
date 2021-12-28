////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.event.*;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

import java.io.PrintStream;

/**
 * A filter that can be inserted into a Receiver pipeline to count the events that pass through.
 * This class is not normally used in Saxon, but is available for diagnostics when needed. Note
 * that the counters are only maintained if {@link Instrumentation#ACTIVE} is set to true. The counters
 * can be displayed by calling {@link Instrumentation#report()}.
 */
public class CountingFilter extends ProxyReceiver {

    private static int nextid = 0;
    private int id;

    /**
     * Create a TracingFilter and allocate a unique Id.
     *
     * @param nextReceiver the underlying receiver to which the events will be sent
     */

    public CountingFilter(Receiver nextReceiver) {
        super(nextReceiver);
        id = nextid++;
    }

    /**
     * Create a TracingFilter, allocate a unique Id, and supply the destination for diagnostic
     * trace messages
     *
     * @param nextReceiver     the underlying receiver to which the events will be sent
     * @param diagnosticOutput the destination for diagnostic trace messages
     */

    public CountingFilter(Receiver nextReceiver, PrintStream diagnosticOutput) {
        super(nextReceiver);
        id = nextid++;
    }

    /**
     * Get the unique id that was allocated to this TracingFilter
     *
     * @return the unique id (which is included in all diagnostic messages)
     */

    public int getId() {
        return id;
    }

    /**
     * Increment a named counter
     */

    private void count(String counter) {
        Instrumentation.count("Filter " + id + " " + counter);
    }

    /**
     * Append an arbitrary item (node or atomic value) to the output
     * @param item           the item to be appended
     * @param locationId     the location of the calling instruction, for diagnostics
     * @param copyNamespaces if the item is an element node, this indicates whether its namespaces
*                       need to be copied. Values are {@link ReceiverOption#ALL_NAMESPACES}; the default (0) means
     */

    @Override
    public void append(Item item, Location locationId, int copyNamespaces) throws XPathException {
        count("append");
        if (nextReceiver instanceof SequenceReceiver) {
            ((SequenceReceiver) nextReceiver).append(item, locationId, copyNamespaces);
        } else {
            super.append(item, locationId, copyNamespaces);
        }
    }

    /**
     * Character data
     */

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        count("characters");
        nextReceiver.characters(chars, locationId, properties);
    }

    /**
     * End of document
     */

    @Override
    public void close() throws XPathException {
        count("close");
        nextReceiver.close();
    }

    /**
     * Output a comment
     */

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        count("comment");
        nextReceiver.comment(chars, locationId, properties);
    }

    /**
     * Notify the end of a document node
     */

    @Override
    public void endDocument() throws XPathException {
        count("endDocument");
        nextReceiver.endDocument();
    }

    /**
     * End of element
     */

    @Override
    public void endElement() throws XPathException {
        count("endElement");
        nextReceiver.endElement();
    }

    /**
     * Start of event stream
     */

    @Override
    public void open() throws XPathException {
        count("open");
        nextReceiver.open();
    }

    /**
     * Processing Instruction
     */

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        count("processingInstruction");
        nextReceiver.processingInstruction(target, data, locationId, properties);
    }

    /**
     * Start of a document node.
     * @param properties
     */

    @Override
    public void startDocument(int properties) throws XPathException {
        count("startDocument");
        nextReceiver.startDocument(properties);
    }

    /**
     * Notify the start of an element
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        count("startElement");
        nextReceiver.startElement(elemName, type, attributes, namespaces, location, properties);
    }
}

