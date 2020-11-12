////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.jaxp;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceivingContentHandler;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.NoElementsSpaceStrippingRule;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.tree.tiny.TinyBuilder;
import org.xml.sax.SAXException;

import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.TransformerHandler;


/**
 * <b>TransformerHandlerImpl</b> implements the javax.xml.transform.sax.TransformerHandler
 * interface. It acts as a ContentHandler and LexicalHandler which receives a stream of
 * SAX events representing an input document, and performs a transformation treating this
 * SAX stream as the source document of the transformation.
 *
 * <p>The {@code TransformerHandler} is written on the assumption that it is receiving events
 * from a parser configured with {@code http://xml.org/sax/features/namespaces} set to true
 * and {@code http://xml.org/sax/features/namespace-prefixes} set to false. The {@code TransformerHandler}
 * has no control over the feature settings of the sender of the events (which is not even necessarily
 * an {@code XMLReader}), and if the events do not follow this pattern then the class may
 * fail in unpredictable ways.</p>
 */

public class TransformerHandlerImpl extends ReceivingContentHandler implements TransformerHandler {

    private TransformerImpl transformer;
    private Builder builder;
    private Receiver receiver;
    private Result result;
    private String systemId;
    private boolean started = false;

    /**
     * Create a TransformerHandlerImpl and initialise variables. The constructor is protected, because
     * the Filter should be created using newTransformerHandler() in the SAXTransformerFactory
     * class
     *
     * @param transformer the Controller to be used
     */

    protected TransformerHandlerImpl(TransformerImpl transformer) {
        this.transformer = transformer;
        Controller controller = transformer.getUnderlyingXsltTransformer().getUnderlyingController();
        Configuration config = transformer.getConfiguration();
        int validation = controller.getSchemaValidationMode();
        builder = controller.makeBuilder();
        if (builder instanceof TinyBuilder) {
            ((TinyBuilder) builder).setStatistics(config.getTreeStatistics().SOURCE_DOCUMENT_STATISTICS);
        }
        PipelineConfiguration pipe = builder.getPipelineConfiguration();
        ParseOptions options = pipe.getParseOptions();
        options.setCheckEntityReferences(true);
        setPipelineConfiguration(pipe);
        receiver = controller.makeStripper(builder);
        if (controller.isStylesheetStrippingTypeAnnotations()) {
            receiver = config.getAnnotationStripper(receiver);
        }
        if (validation != Validation.PRESERVE) {
            options.setSchemaValidationMode(validation);
            options.setSpaceStrippingRule(NoElementsSpaceStrippingRule.getInstance());
            receiver = config.getDocumentValidator(receiver, getSystemId(), options, null);
        }
        setReceiver(receiver);
    }

    /**
     * Start of a new document. The TransformerHandler is not serially reusable, so this method
     * must only be called once.
     *
     * @throws SAXException                  only if an overriding subclass throws this exception
     * @throws UnsupportedOperationException if an attempt is made to reuse the TransformerHandler by calling
     *                                       startDocument() more than once.
     */

    @Override
    public void startDocument() throws SAXException {
        if (started) {
            throw new UnsupportedOperationException(
                    "The TransformerHandler is not serially reusable. The startDocument() method must be called once only.");
        }
        started = true;
        super.startDocument();
    }

    /**
     * Get the Transformer used for this transformation
     */

    @Override
    public Transformer getTransformer() {
        return transformer;
    }

    /**
     * Set the SystemId of the document. Note that in reporting location information, Saxon gives
     * priority to the system Id reported by the SAX Parser in the Locator passed to the
     * {@link #setDocumentLocator(org.xml.sax.Locator)} method. The SystemId passed to this method
     * is used as the base URI for resolving relative references.
     *
     * @param url the systemId of the source document
     */

    @Override
    public void setSystemId(String url) {
        systemId = url;
        receiver.setSystemId(url);
    }

    /**
     * Get the systemId of the document. This will be the systemId obtained from the Locator passed to the
     * {@link #setDocumentLocator(org.xml.sax.Locator)} method if available, otherwise the SystemId passed
     * to the {@link #setSystemId(String)} method.
     */

    @Override
    public String getSystemId() {
        return systemId;
    }


    /**
     * Set the <code>Result</code> associated with this
     * <code>TransformerHandler</code> to be used for the transformation.
     *
     * @param result A <code>Result</code> instance, should not be
     *               <code>null</code>.
     * @throws IllegalArgumentException if result is invalid for some reason.
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
     * Override the behaviour of endDocument() in ReceivingContentHandler, so that it fires off
     * the transformation of the constructed document
     */

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
        NodeInfo doc = builder.getCurrentRoot();
        doc.getTreeInfo().setSpaceStrippingRule(
                transformer.getUnderlyingXsltTransformer().getUnderlyingController().getSpaceStrippingRule());
        builder.reset();
        if (doc == null) {
            throw new SAXException("No source document has been built");
        }

        try {
            transformer.transform(doc, result);
        } catch (TransformerException err) {
            throw new SAXException(err);
        }
    }

}

