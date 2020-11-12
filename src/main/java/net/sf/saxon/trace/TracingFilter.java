////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.Whitespace;

import java.io.PrintStream;

/**
 * A filter that can be inserted into a Receiver pipeline to trace the events that pass through.
 * This class is not normally used in Saxon, but is available for diagnostics when needed.
 */
public class TracingFilter extends ProxyReceiver {

    private static int nextid = 0;
    private int id;
    private String indent = "";
    private PrintStream out = System.err;
    private boolean closed = false;

    /**
     * Create a TracingFilter and allocate a unique Id.
     *
     * @param nextReceiver the underlying receiver to which the events will be sent
     */

    public TracingFilter(Receiver nextReceiver) {
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

    public TracingFilter(Receiver nextReceiver, PrintStream diagnosticOutput) {
        super(nextReceiver);
        id = nextid++;
        out = diagnosticOutput;
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
     * Append an arbitrary item (node or atomic value) to the output
     * @param item           the item to be appended
     * @param locationId     the location of the calling instruction, for diagnostics
     * @param copyNamespaces if the item is an element node, this indicates whether its namespaces
*                       need to be copied. Values are {@link ReceiverOption#ALL_NAMESPACES}; the default (0) means
     */

    @Override
    public void append(Item item, Location locationId, int copyNamespaces) throws XPathException {
        out.println("RCVR " + id + indent + " APPEND " + item.getClass().getName());
        if (nextReceiver instanceof SequenceReceiver) {
            nextReceiver.append(item, locationId, copyNamespaces);
        } else {
            super.append(item, locationId, copyNamespaces);
        }
    }

    /**
     * Character data
     */

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        out.println("RCVR " + id + indent + " CHARACTERS " + (Whitespace.isWhite(chars) ? "(whitespace)" : ""));
        FastStringBuffer sb = new FastStringBuffer(chars.length() * 4);
        sb.cat(chars).append(":");
        for (int i = 0; i < chars.length(); i++) {
            sb.append((int) chars.charAt(i) + " ");
        }
        out.println("    \"" + sb + '\"');
        nextReceiver.characters(chars, locationId, properties);
    }

    /**
     * End of document
     */

    @Override
    public void close() throws XPathException {
        out.println("RCVR " + id + indent + " CLOSE");
        nextReceiver.close();
    }

    /**
     * Output a comment
     */

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        out.println("RCVR " + id + indent + " COMMENT");
        nextReceiver.comment(chars, locationId, properties);
    }

    /**
     * Notify the end of a document node
     */

    @Override
    public void endDocument() throws XPathException {
        out.println("RCVR " + id + indent + " END DOCUMENT");
        nextReceiver.endDocument();
    }

    /**
     * End of element
     */

    @Override
    public void endElement() throws XPathException {
        if (indent.isEmpty()) {
            throw new XPathException("RCVR " + id + " Unmatched end tag");
        }
        indent = indent.substring(2);
        out.println("RCVR " + id + indent + " END ELEMENT");
        nextReceiver.endElement();
    }

    /**
     * Start of event stream
     */

    @Override
    public void open() throws XPathException {
        out.println("RCVR " + id + indent + " OPEN");
        nextReceiver.open();
    }

    /**
     * Processing Instruction
     */

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        out.println("RCVR " + id + indent + " PROCESSING INSTRUCTION");
        nextReceiver.processingInstruction(target, data, locationId, properties);
    }

    /**
     * Start of a document node.
     * @param properties
     */

    @Override
    public void startDocument(int properties) throws XPathException {
        out.println("RCVR " + id + indent + " START DOCUMENT");
        nextReceiver.startDocument(properties);
    }

    /**
     * Notify the start of an element
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        out.println("RCVR " + id + indent + " START ELEMENT " + elemName.getDisplayName());
        indent = indent + "  ";
        nextReceiver.startElement(elemName, type, attributes, namespaces, location, properties);
    }
}

