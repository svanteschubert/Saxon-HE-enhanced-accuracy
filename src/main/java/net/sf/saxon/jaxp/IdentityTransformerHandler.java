////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.jaxp;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceivingContentHandler;
import net.sf.saxon.event.Stripper;
import net.sf.saxon.lib.SerializerFactory;
import net.sf.saxon.om.AllElementsSpaceStrippingRule;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.SAXException;

import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.util.Properties;

/**
 * <b>IdentityTransformerHandler</b> implements the javax.xml.transform.sax.TransformerHandler
 * interface. It acts as a ContentHandler and LexicalHandler which receives a stream of
 * SAX events representing an input document, and performs an identity transformation passing
 * these events to a Result
 *
 * @author Michael H. Kay
 */

public class IdentityTransformerHandler extends ReceivingContentHandler implements TransformerHandler {

    /*@Nullable*/ private Result result;
    private String systemId;
    private IdentityTransformer controller;

    /**
     * Create a IdentityTransformerHandler and initialise variables. The constructor is protected, because
     * the Filter should be created using newTransformerHandler() in the SAXTransformerFactory
     * class
     *
     * @param controller the Controller for this transformation
     */

    protected IdentityTransformerHandler(IdentityTransformer controller) {
        this.controller = controller;
        setPipelineConfiguration(controller.getConfiguration().makePipelineConfiguration());
    }

    /**
     * Get the Transformer used for this transformation
     */

    @Override
    public Transformer getTransformer() {
        return controller;
    }

    /**
     * Set the SystemId of the document
     */

    @Override
    public void setSystemId(String url) {
        systemId = url;
    }

    /**
     * Get the systemId of the document
     */

    @Override
    public String getSystemId() {
        return systemId;
    }

    /**
     * Set the output destination of the transformation
     */

    @Override
    public void setResult(Result result) {
        if (result == null) {
            throw new IllegalArgumentException("Result must not be null");
        }
        this.result = result;
    }

    /**
     * Get the output destination of the transformation
     *
     * @return the output destination
     */

    public Result getResult() {
        return result;
    }

    /**
     * Override the behaviour of startDocument() in ReceivingContentHandler
     */

    @Override
    public void startDocument() throws SAXException {
        if (result == null) {
            result = new StreamResult(System.out);
        }
        try {
            Properties props = controller.getOutputProperties();
            Configuration config = getConfiguration();
            SerializerFactory sf = config.getSerializerFactory();
            Receiver out = sf.getReceiver(result, new SerializationProperties(props));
            setPipelineConfiguration(out.getPipelineConfiguration());
            if (config.isStripsAllWhiteSpace()) {
                out = new Stripper(AllElementsSpaceStrippingRule.getInstance(), out);
            }
            setReceiver(out);
        } catch (XPathException err) {
            throw new SAXException(err);
        }
        super.startDocument();
    }

}

