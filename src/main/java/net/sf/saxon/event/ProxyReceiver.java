////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

/**
 * A ProxyReceiver is an Receiver that filters data before passing it to another
 * underlying Receiver.
 */

public class ProxyReceiver extends SequenceReceiver {

    /*@NotNull*/
    protected Receiver nextReceiver;

    public ProxyReceiver(/*@NotNull*/ Receiver nextReceiver) {
        super(nextReceiver.getPipelineConfiguration());
        setUnderlyingReceiver(nextReceiver);
        setPipelineConfiguration(nextReceiver.getPipelineConfiguration());
    }

    @Override
    public void setSystemId(String systemId) {
        //noinspection StringEquality
        if (systemId != this.systemId) {
            // use of == rather than equals() is deliberate, since this is only an optimization
            this.systemId = systemId;
            nextReceiver.setSystemId(systemId);
        }
    }

    /**
     * Set the underlying receiver. This call is mandatory before using the Receiver.
     *
     * @param receiver the underlying receiver, the one that is to receive events after processing
     *                 by this filter.
     */

    public void setUnderlyingReceiver(/*@NotNull*/ Receiver receiver) {
        nextReceiver = receiver;
    }

    /**
     * Get the next Receiver in the pipeline
     *
     * @return the next Receiver in the pipeline
     */

    public Receiver getNextReceiver() {
        return nextReceiver;
    }


    @Override
    public void setPipelineConfiguration(/*@NotNull*/ PipelineConfiguration pipe) {
        if (pipelineConfiguration != pipe) {
            pipelineConfiguration = pipe;
            if (nextReceiver.getPipelineConfiguration() != pipe) {
                nextReceiver.setPipelineConfiguration(pipe);
            }
        }
    }

    /**
     * Get the namepool for this configuration
     */

    @Override
    public NamePool getNamePool() {
        return pipelineConfiguration.getConfiguration().getNamePool();
    }

    /**
     * Start of event stream
     */

    @Override
    public void open() throws XPathException {
        nextReceiver.open();
    }

    /**
     * End of output. Note that closing this receiver also closes the rest of the
     * pipeline.
     */

    @Override
    public void close() throws XPathException {
        // Note: It's wrong to assume that because we've finished writing to this
        // receiver, then we've also finished writing to other receivers in the pipe.
        // In the case where the rest of the pipe is to stay open, the caller should
        // either avoid doing the close(), or should first set the underlying receiver
        // to null.
        nextReceiver.close();
    }

    /**
     * Start of a document node.
     */

    @Override
    public void startDocument(int properties) throws XPathException {
        nextReceiver.startDocument(properties);
    }

    /**
     * Notify the end of a document node
     */

    @Override
    public void endDocument() throws XPathException {
        nextReceiver.endDocument();
    }

    /**
     * Notify the start of an element
     *
     * @param elemName   the name of the element.
     * @param type   the type annotation of the element.
     * @param attributes the attributes of this element
     * @param namespaces the in-scope namespaces of this element: generally this is all the in-scope
     *                   namespaces, without relying on inheriting namespaces from parent elements
     * @param location   an object providing information about the module, line, and column where the node originated
     * @param properties bit-significant properties of the element node. If there are no relevant
     *                   properties, zero is supplied. The definitions of the bits are in class {@link ReceiverOption}
     * @throws XPathException if an error occurs
     */
    @Override
    public void startElement(NodeName elemName, SchemaType type, AttributeMap attributes, NamespaceMap namespaces, Location location, int properties) throws XPathException {
        nextReceiver.startElement(elemName, type, attributes, namespaces, location, properties);
    }

    /**
     * End of element
     */

    @Override
    public void endElement() throws XPathException {
        nextReceiver.endElement();
    }

    /**
     * Character data
     */

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        nextReceiver.characters(chars, locationId, properties);
    }


    /**
     * Processing Instruction
     */

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        nextReceiver.processingInstruction(target, data, locationId, properties);
    }

    /**
     * Output a comment
     */

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        nextReceiver.comment(chars, locationId, properties);
    }


    /**
     * Set the URI for an unparsed entity in the document.
     */

    @Override
    public void setUnparsedEntity(String name, String uri, String publicId) throws XPathException {
        nextReceiver.setUnparsedEntity(name, uri, publicId);
    }

    /**
     * Append an arbitrary item (node or atomic value) to the output
     * @param item           the item to be appended
     * @param locationId     the location of the calling instruction, for diagnostics
     * @param properties     if the item is an element node, this indicates whether its namespaces
*                       need to be copied. Values are {@link ReceiverOption#ALL_NAMESPACES}; the default (0) means
     */

    @Override
    public void append(Item item, Location locationId, int properties) throws XPathException {
        nextReceiver.append(item, locationId, properties);
    }

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     *
     * @return true if the Receiver makes any use of this information. If false, the caller
     * may supply untyped nodes instead of supplying the type annotation
     */

    @Override
    public boolean usesTypeAnnotations() {
        return nextReceiver.usesTypeAnnotations();
    }
}

