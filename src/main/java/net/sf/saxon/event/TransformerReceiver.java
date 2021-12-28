////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.Controller;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XsltController;

import javax.xml.transform.TransformerException;


/**
 * <b>TransformerReceiver</b> is similar in concept to the JAXP TransformerHandler,
 * except that it implements Saxon's Receiver interface rather than the standard
 * SAX2 interface. This means that it allows nodes with type annotations to be
 * passed down a pipeline from one transformation to another.
 */

public class TransformerReceiver extends ProxyReceiver {

    private XsltController controller;
    private Builder builder;
    private Receiver destination;

    /**
     * Create a TransformerReceiver and initialise variables.
     *
     * @param controller the Controller
     */

    public TransformerReceiver(XsltController controller) {
        super(controller.makeBuilder());
        this.controller = controller;
        this.builder = (Builder) getNextReceiver();
        this.builder.setUseEventLocation(false);
    }

    /**
     * Start of event stream
     */

    @Override
    public void open() throws XPathException {
        builder.setSystemId(systemId);
        Receiver stripper = controller.makeStripper(builder);
        if (controller.isStylesheetStrippingTypeAnnotations()) {
            stripper = controller.getConfiguration().getAnnotationStripper(stripper);
        }
        setUnderlyingReceiver(stripper);
        nextReceiver.open();
    }

    /**
     * Get the Controller used for this transformation
     *
     * @return the controller
     */

    public Controller getController() {
        return controller;
    }

    /**
     * Set the SystemId of the document
     */

    @Override
    public void setSystemId(String systemId) {
        super.setSystemId(systemId);
        controller.setBaseOutputURI(systemId);
    }

    /**
     * Set the output destination of the transformation. This method must be called before
     * the transformation can proceed.
     *
     * @param destination the destination to which the transformation output will be written
     */

    public void setDestination(Receiver destination) {
        this.destination = destination;
    }

    /**
     * Get the output destination of the transformation
     *
     * @return the output destination. May be null if no destination has been set.
     */

    /*@Nullable*/
    public Receiver getDestination() {
        return destination;
    }

    /**
     * Override the behaviour of close() in ProxyReceiver, so that it fires off
     * the transformation of the constructed document
     */

    @Override
    public void close() throws XPathException {
        if (builder == null) {
            // already closed
        } else {
            nextReceiver.close();
            NodeInfo doc = builder.getCurrentRoot();
            builder.reset();
            builder = null;
            if (doc == null) {
                throw new XPathException("No source document has been built");
            }
            doc.getTreeInfo().setSpaceStrippingRule(controller.getSpaceStrippingRule());
            if (destination == null) {
                throw new XPathException("No output destination has been supplied");
            }
            try {
                controller.setGlobalContextItem(doc);
                controller.applyTemplates(doc, destination);
            } catch (TransformerException e) {
                throw XPathException.makeXPathException(e);
            }
        }
    }

}

