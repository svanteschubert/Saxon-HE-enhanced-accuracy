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

/**
 * TeeOutputter: a SequenceReceiver that duplicates received events to two different destinations
 */

public class TeeOutputter extends SequenceReceiver {

    private Receiver seq1;
    private Receiver seq2;

    public TeeOutputter(Receiver seq1, Receiver seq2) {
        super(seq1.getPipelineConfiguration());
        this.seq1 = seq1;
        this.seq2 = seq2;
    }

    /**
     * Set the first destination
     *
     * @param seq1 the first output destination
     */

    protected void setFirstDestination(Receiver seq1) {
        this.seq1 = seq1;
    }

    /**
     * Set the second destination
     *
     * @param seq2 the second output destination
     */

    protected void setSecondDestination(Receiver seq2) {
        this.seq2 = seq2;
    }

    /**
     * Get the first destination
     *
     * @return the first output destination
     */

    protected Receiver getFirstDestination() {
        return seq1;
    }

    /**
     * Get the second destination
     *
     * @return the second output destination
     */

    protected Receiver getSecondDestination() {
        return seq2;
    }

    /**
     * Pass on information about unparsed entities
     * @param name     The name of the unparsed entity
     * @param systemID The system identifier of the unparsed entity
     * @param publicID The public identifier of the unparsed entity
     * @throws XPathException in the event of an error
     */

    @Override
    public void setUnparsedEntity(String name, String systemID, String publicID) throws XPathException {
        seq1.setUnparsedEntity(name, systemID, publicID);
        seq2.setUnparsedEntity(name, systemID, publicID);
    }

    /**
     * Output an item (atomic value or node) to the sequence
     */

    @Override
    public void append(Item item, Location locationId, int properties) throws XPathException {
        seq1.append(item, locationId, properties);
        seq2.append(item, locationId, properties);
    }

    @Override
    public void open() throws XPathException {
        super.open();
        seq1.open();
        seq2.open();
    }

    /**
     * Notify the start of a document node
     */

    @Override
    public void startDocument(int properties) throws XPathException {
        seq1.startDocument(properties);
        seq2.startDocument(properties);
    }

    /**
     * Notify the end of a document node
     */

    @Override
    public void endDocument() throws XPathException {
        seq1.endDocument();
        seq2.endDocument();
    }

    /**
     * Notify the start of an element
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties)
            throws XPathException {
        seq1.startElement(elemName, type, attributes, namespaces, location, properties);
        seq2.startElement(elemName, type, attributes, namespaces, location, properties);
    }



    /**
     * Notify the end of an element. The receiver must maintain a stack if it needs to know which
     * element is ending.
     */

    @Override
    public void endElement() throws XPathException {
        seq1.endElement();
        seq2.endElement();
    }

    /**
     * Notify character data. Note that some receivers may require the character data to be
     * sent in a single event, but in general this is not a requirement.
     * @param chars      The characters
     * @param locationId an integer which can be interpreted using a LocationMap to return
     *                   information such as line number and system ID. If no location information is available,
     *                   the value zero is supplied.
     * @param properties Bit significant value. The following bits are defined:
     */

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        seq1.characters(chars, locationId, properties);
        seq2.characters(chars, locationId, properties);
    }

    /**
     * Output a processing instruction
     *
     * @param name       The PI name. This must be a legal name (it will not be checked).
     * @param data       The data portion of the processing instruction
     * @param locationId an integer which can be interpreted using a LocationMap to return
     *                   information such as line number and system ID. If no location information is available,
     *                   the value zero is supplied.
     * @param properties Additional information about the PI. The following bits are
     *                   defined:
     *                   <dl>
     *                   <dt>CHECKED</dt>    <dd>Data is known to be legal (e.g. doesn't contain "?&gt;")</dd>
     *                   </dl>
     * @throws IllegalArgumentException the content is invalid for an XML processing instruction
     */

    @Override
    public void processingInstruction(String name, CharSequence data, Location locationId, int properties) throws XPathException {
        seq1.processingInstruction(name, data, locationId, properties);
        seq2.processingInstruction(name, data, locationId, properties);
    }

    /**
     * Notify a comment. Comments are only notified if they are outside the DTD.
     *
     * @param content    The content of the comment
     * @param locationId an integer which can be interpreted using a LocationMap to return
     *                   information such as line number and system ID. If no location information is available,
     *                   the value zero is supplied.
     * @param properties Additional information about the comment. The following bits are
     *                   defined:
     *                   <dl>
     *                   <dt>CHECKED</dt>    <dd>Comment is known to be legal (e.g. doesn't contain "--")</dd>
     *                   </dl>
     * @throws IllegalArgumentException the content is invalid for an XML comment
     */

    @Override
    public void comment(CharSequence content, Location locationId, int properties) throws XPathException {
        seq1.comment(content, locationId, properties);
        seq2.comment(content, locationId, properties);
    }

    /**
     * Notify the end of the event stream
     */

    @Override
    public void close() throws XPathException {
        seq1.close();
        seq2.close();
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
        return seq1.usesTypeAnnotations() || seq2.usesTypeAnnotations();
    }
}

