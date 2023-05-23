////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.event.SequenceWriter;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

/**
 * This class implements a Receiver that can receive xsl:message output and send it to a
 * user-supplied MessageListener.
 */

class MessageListener2Proxy extends SequenceWriter {

    private MessageListener2 listener;
    private boolean terminate;
    private Location locationId;
    private StructuredQName errorCode;

    protected MessageListener2Proxy(MessageListener2 listener, PipelineConfiguration pipe) {
        super(pipe);
        // See bug 2104. We use the Linked Tree model because the TinyTree can use excessive memory. This
        // is because the initial size allocation is based on the size of source documents, which might be large;
        // also because we store several messages in a single TinyTree; and because we fail to condense the tree.
        setTreeModel(TreeModel.LINKED_TREE);
        this.listener = listener;
    }

    /**
     * Get the wrapped MessageListener
     *
     * @return the wrapped MessageListener
     */

    public MessageListener2 getMessageListener() {
        return listener;
    }


    /**
     * Start of a document node.
     * @param properties
     */

    @Override
    public void startDocument(int properties) throws XPathException {
        terminate = ReceiverOption.contains(properties, ReceiverOption.TERMINATE);
        locationId = null;
        errorCode = null;
        super.startDocument(properties);
    }


    /**
     * Output an element start tag.
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        if (this.locationId == null) {
            this.locationId = location;
        }
        super.startElement(elemName, type, attributes, namespaces, location, properties);
    }

    /**
     * Produce text content output. <BR>
     * @param s          The String to be output
     * @param locationId the location of the node in the source, or of the instruction that created it
     * @param properties bit-significant flags for extra information, e.g. disable-output-escaping  @throws net.sf.saxon.trans.XPathException
     */

    @Override
    public void characters(CharSequence s, Location locationId, int properties) throws XPathException {
        if (this.locationId == null) {
            this.locationId = locationId;
        }
        super.characters(s, locationId, properties);
    }

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (target.equals("error-code") && errorCode == null) {
            errorCode = StructuredQName.fromEQName(data);
            if (this.locationId == null) {
                this.locationId = locationId;
            }
        } else {
            super.processingInstruction(target, data, locationId, properties);
        }
    }

    /**
     * Append an item to the sequence, performing any necessary type-checking and conversion
     */

    @Override
    public void append(Item item, Location locationId, int copyNamespaces) throws XPathException {
        if (this.locationId == null) {
            this.locationId = locationId;
        }
        super.append(item, locationId, copyNamespaces);
    }

    /**
     * Abstract method to be supplied by subclasses: output one item in the sequence.
     *
     * @param item the item to be written to the sequence
     */

    @Override
    public void write(Item item) throws XPathException {
        Location loc;
        if (locationId == null) {
            loc = Loc.NONE;
        } else {
            loc = locationId.saveLocation();
        }
        listener.message(new XdmNode((NodeInfo) item), new QName(errorCode), terminate, loc);
    }
}

