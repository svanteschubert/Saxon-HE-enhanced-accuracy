////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.stax;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * ReceiverToXMLStreamWriter is a Receiver writes XML by using the XMLStreamWriter
 */
public class ReceiverToXMLStreamWriter implements Receiver {

    protected PipelineConfiguration pipe;
    protected Configuration config;
    protected String systemId;
    protected String baseURI;
    private XMLStreamWriter writer;

    public ReceiverToXMLStreamWriter(XMLStreamWriter writer) {
        this.writer = writer;
    }

    /**
     * Get the XMLStreamWriter to which this Receiver is writing events
     *
     * @return the destination of this ReceiverToXMLStreamWriter
     */

    public XMLStreamWriter getXMLStreamWriter() {
        return writer;
    }

    @Override
    public void setPipelineConfiguration(PipelineConfiguration pipe) {
        this.pipe = pipe;
        config = pipe.getConfiguration();
    }

    @Override
    public PipelineConfiguration getPipelineConfiguration() {
        return pipe;
    }

    @Override
    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    @Override
    public String getSystemId() {
        return systemId;
    }

    @Override
    public void open() throws XPathException {
    }

    @Override
    public void startDocument(int properties) throws XPathException {
        try {
            writer.writeStartDocument();
        } catch (XMLStreamException e) {
            throw new XPathException(e);
        }
    }

    @Override
    public void endDocument() throws XPathException {
        try {
            writer.writeEndDocument();
        } catch (XMLStreamException e) {
            throw new XPathException(e);
        }
    }

    @Override
    public void setUnparsedEntity(String name, String systemID, String publicID) throws XPathException {

    }

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        String local = elemName.getLocalPart();
        String uri = elemName.getURI();
        String prefix = elemName.getPrefix();
        try {
            if (prefix.equals("") && uri.equals("")) {
                writer.writeStartElement(local);
            } else if (prefix.equals("")) {
                writer.writeStartElement(prefix, local, uri);
            } else {
                writer.writeStartElement(prefix, local, uri);
            }
            for (NamespaceBinding ns : namespaces) {
                writer.writeNamespace(ns.getPrefix(), ns.getURI());
            }
            for (AttributeInfo att : attributes) {
                NodeName attName = att.getNodeName();
                String attLocal = attName.getLocalPart();
                String attUri = attName.getURI();
                String attPrefix = attName.getPrefix();
                String value = att.getValue();
                if (attPrefix.equals("") && attUri.equals("")) {
                    writer.writeAttribute(attLocal, value);
                } else if (attPrefix.equals("") & !attUri.equals("")) {
                    writer.writeAttribute(attUri, attLocal, value);
                } else {
                    writer.writeAttribute(attPrefix, attUri, attLocal, value);
                }
            }
        } catch (XMLStreamException e) {
            throw new XPathException(e);
        }

    }

    @Override
    public void endElement() throws XPathException {
        try {
            writer.writeEndElement();
        } catch (XMLStreamException e) {
            throw new XPathException(e);
        }
    }

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        try {
            writer.writeCharacters(chars.toString());
        } catch (XMLStreamException e) {
            throw new XPathException(e);
        }
    }

    @Override
    public void processingInstruction(String name, CharSequence data, Location locationId, int properties) throws XPathException {
        try {
            writer.writeProcessingInstruction(name, data.toString());
        } catch (XMLStreamException e) {
            throw new XPathException(e);
        }
    }

    @Override
    public void comment(CharSequence content, Location locationId, int properties) throws XPathException {
        try {
            writer.writeComment(content.toString());
        } catch (XMLStreamException e) {
            throw new XPathException(e);
        }
    }

    @Override
    public void close() throws XPathException {
        try {
            writer.close();
        } catch (XMLStreamException e) {
            throw new XPathException(e);
        }
    }

    @Override
    public boolean usesTypeAnnotations() {
        return false;
    }
}

