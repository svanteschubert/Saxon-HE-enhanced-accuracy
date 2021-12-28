////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.lib.SerializerFactory;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.Whitespace;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import java.util.Properties;

/**
 * This class is used when the decision on which serialization method to use has to be delayed until the first
 * element is read. It buffers comments and processing instructions until that happens; then when the first
 * element arrives it creates a real serialization pipeline and uses that for future output.
 *
 * @author Michael H. Kay
 */

public class UncommittedSerializer extends ProxyReceiver {

    private boolean committed = false;
    private EventBuffer pending = null;
    private Result finalResult;
    private SerializationProperties properties;

    /**
     * Create an uncommitted Serializer
     *
     * @param finalResult      the output destination
     * @param next             the next receiver in the pipeline
     * @param params the serialization properties including character maps
     */

    public UncommittedSerializer(Result finalResult, Receiver next, SerializationProperties params) {
        super(next);
        this.finalResult = finalResult;
        this.properties = params;
    }

    @Override
    public void open() throws XPathException {
        committed = false;
    }

    /**
     * End of document
     */

    @Override
    public void close() throws XPathException {
        // empty output: must send a beginDocument()/endDocument() pair to the content handler
        if (!committed) {
            switchToMethod("xml");
        }
        getNextReceiver().close();
    }

    /**
     * Produce character output using the current Writer. <BR>
     */

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (committed) {
            getNextReceiver().characters(chars, locationId, properties);
        } else {
            if (pending == null) {
                pending = new EventBuffer(getPipelineConfiguration());
            }
            pending.characters(chars, locationId, properties);
            if (!Whitespace.isWhite(chars)) {
                switchToMethod("xml");
            }
        }
    }

    /**
     * Processing Instruction
     */

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (committed) {
            getNextReceiver().processingInstruction(target, data, locationId, properties);
        } else {
            if (pending == null) {
                pending = new EventBuffer(getPipelineConfiguration());
            }
            pending.processingInstruction(target, data, locationId, properties);
        }
    }

    /**
     * Output a comment
     */

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (committed) {
            getNextReceiver().comment(chars, locationId, properties);
        } else {
            if (pending == null) {
                pending = new EventBuffer(getPipelineConfiguration());
            }
            pending.comment(chars, locationId, properties);
        }
    }

    /**
     * Output an element start tag. <br>
     * This can only be called once: it switches to a substitute output generator for XML, XHTML, or HTML,
     * depending on the element name.
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties)
            throws XPathException {
        if (!committed) {
            String name = elemName.getLocalPart();
            String uri = elemName.getURI();
            if (name.equalsIgnoreCase("html") && uri.isEmpty()) {
                switchToMethod("html");
            } else if (name.equals("html") && uri.equals(NamespaceConstant.XHTML)) {
                String version = this.properties.getProperties().getProperty(SaxonOutputKeys.STYLESHEET_VERSION);
                if ("10".equals(version)) {
                    switchToMethod("xml");
                } else {
                    switchToMethod("xhtml");
                }
            } else {
                switchToMethod("xml");
            }
        }
        getNextReceiver().startElement(elemName, type, attributes, namespaces, location, properties);
    }

    @Override
    public void startDocument(int properties) throws XPathException {
        if (committed) {
            getNextReceiver().startDocument(properties);
        } else {
            if (pending == null) {
                pending = new EventBuffer(getPipelineConfiguration());
            }
            pending.startDocument(properties);
        }
    }

    @Override
    public void endDocument() throws XPathException {
        // empty output: must send a beginDocument()/endDocument() pair to the content handler
        if (!committed) {
            switchToMethod("xml");
        }
        getNextReceiver().endDocument();
    }

    /**
     * Switch to a specific emitter once the output method is known
     *
     * @param method the method to switch to (xml, html, xhtml)
     */

    private void switchToMethod(String method) throws XPathException {
        Properties newProperties = new Properties(properties.getProperties());
        newProperties.setProperty(OutputKeys.METHOD, method);
        SerializerFactory sf = getConfiguration().getSerializerFactory();
        SerializationProperties newParams = new SerializationProperties(newProperties, properties.getCharacterMapIndex());
        newParams.setValidationFactory(properties.getValidationFactory());
        Receiver target = sf.getReceiver(finalResult, newParams, getPipelineConfiguration());
        committed = true;
        target.open();
        if (pending != null) {
            pending.replay(target);
            pending = null;
        }
        setUnderlyingReceiver(target);
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
        if (item instanceof NodeInfo) {
            ((NodeInfo) item).copy(this, CopyOptions.ALL_NAMESPACES, locationId);
        } else {
            if (!committed) {
                switchToMethod("xml");
            }
            getNextReceiver().append(item);
        }
    }

}

