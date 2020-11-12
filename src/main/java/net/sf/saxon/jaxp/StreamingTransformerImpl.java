////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.jaxp;

import net.sf.saxon.Controller;
import net.sf.saxon.event.*;
import net.sf.saxon.expr.instruct.GlobalParameterSet;
import net.sf.saxon.lib.SerializerFactory;
import net.sf.saxon.s9api.*;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XsltController;
import org.xml.sax.XMLFilter;

import javax.xml.transform.*;
import javax.xml.transform.sax.TransformerHandler;
import java.util.HashMap;
import java.util.Map;

/**
 * Saxon implementation of the JAXP Transformer interface. This implementation of Transformer
 * is used when the factory is a {@link com.saxonica.config.StreamingTransformerFactory}, and
 * the effect is that the {@link #transform(Source, Result)} method accepts a StreamSource
 * or SAXSource and processes it in streaming mode, assuming that the initial mode is labelled
 * with <code>streamable="yes"</code>. The global context item in such a transformation will
 * be absent, so referring to "." within a global variable is an error.
 */
public class StreamingTransformerImpl extends AbstractTransformerImpl {

    private Xslt30Transformer xsltTransformer;
    private Map<QName, XdmValue> convertedParameters = new HashMap<>();

    protected StreamingTransformerImpl(XsltExecutable e, Xslt30Transformer t) {
        super(e);
        this.xsltTransformer = t;
    }

    /**
     * <p>Transform the XML <code>Source</code> to a <code>Result</code>.
     * Specific transformation behavior is determined by the settings of the
     * <code>TransformerFactory</code> in effect when the
     * <code>Transformer</code> was instantiated and any modifications made to
     * the <code>Transformer</code> instance.</p>
     * <p>An empty <code>Source</code> is represented as an empty document
     * as constructed by {@link javax.xml.parsers.DocumentBuilder#newDocument()}.
     * The result of transforming an empty <code>Source</code> depends on
     * the transformation behavior; it is not always an empty
     * <code>Result</code>.</p>
     *
     * @param xmlSource    The XML input to transform.
     * @param outputTarget The <code>Result</code> of transforming the
     *                     <code>xmlSource</code>.
     * @throws XPathException
     *          If an unrecoverable error occurs
     *          during the course of the transformation.
     */
    @Override
    public void transform(Source xmlSource, final Result outputTarget) throws XPathException {
        try {
            xsltTransformer.setStylesheetParameters(convertedParameters);
            if (outputTarget.getSystemId() != null) { //bug 2214
                xsltTransformer.setBaseOutputURI(outputTarget.getSystemId());
            }
            Destination destination = makeDestination(outputTarget);
            if (destination == null) {
                SerializerFactory sf = getConfiguration().getSerializerFactory();
                Receiver r = sf.getReceiver(outputTarget,
                                            new SerializationProperties(getLocalOutputProperties()),
                                            getConfiguration().makePipelineConfiguration());
                transform(xmlSource, r);
                return;
            }

            xsltTransformer.applyTemplates(xmlSource, destination);

        } catch (SaxonApiException e) {
            throw XPathException.makeXPathException(e);
        }
    }

    @Override
    protected void setConvertedParameter(QName name, XdmValue value) {
        convertedParameters.put(name, value);
    }

    /**
     * Clear all parameters set with setParameter.
     */
    @Override
    public void clearParameters() {
        super.clearParameters();
        convertedParameters.clear();
    }

    /**
     * Set an object that will be used to resolve URIs used in
     * document().
     * <p>If the resolver argument is null, the URIResolver value will
     * be cleared and the transformer will no longer have a resolver.</p>
     *
     * @param resolver An object that implements the URIResolver interface,
     *                 or null.
     */
    @Override
    public void setURIResolver(URIResolver resolver) {
        super.setURIResolver(resolver);
        xsltTransformer.setURIResolver(resolver);
    }


    /**
     * Set the error event listener in effect for the transformation.
     *
     * @param listener The new error listener.
     * @throws IllegalArgumentException if listener is null.
     */
    @Override
    public void setErrorListener(ErrorListener listener) throws IllegalArgumentException {
        super.setErrorListener(listener);
        xsltTransformer.setErrorListener(listener);
    }

    /**
     * Supply an initial mode for a transformation.
     * <p>This is a Saxon extension to the JAXP interface, needed for XSLT 2.0</p>
     *
     * @param name the name of the initial mode, in Clark notation (either a local name,
     *             or "{uri}local")
     * @throws IllegalArgumentException if the argument is invalid, for example if the
     *                                  format of the name is incorrect or if there is no mode with this name
     */

    public void setInitialMode(String name) throws IllegalArgumentException {
        xsltTransformer.setInitialMode(QName.fromClarkName(name));
    }

    /**
     * Get the underlying s9api implementation class wrapped by this JAXP Transformer
     * @return the underlying s9api XsltTransformer
     */

    public Xslt30Transformer getUnderlyingXsltTransformer() {
        return xsltTransformer;
    }

    /**
     * Get the internal Saxon Controller instance that implements this transformation.
     * Note that the Controller interface will not necessarily remain stable in future releases
     * @return the underlying Saxon Controller instance
     */

    @Override
    public Controller getUnderlyingController() {
        return xsltTransformer.getUnderlyingController();
    }

    /**
     * Create a JAXP XMLFilter which allows this transformation to be added to a SAX pipeline
     *
     * @return the transformation in the form of an XMLFilter
     */

    @Override
    public XMLFilter newXMLFilter() {
        return new StreamingFilterImpl(xsltTransformer);
    }

    /**
     * Get a TransformerHandler that can be used to run the transformation by feeding in SAX events
     */

    public TransformerHandler newTransformerHandler() throws XPathException {
        XsltController controller = xsltTransformer.getUnderlyingController();
        return new StreamingTransformerHandler(controller);
    }

    public class StreamingTransformerHandler extends ReceivingContentHandler implements TransformerHandler {

        private XsltController controller;
        private String systemId;
        //private ProxyReceiver proxy;

        public StreamingTransformerHandler(XsltController controller) {
            this.controller = controller;

            //setReceiver(proxy);
            //setPipelineConfiguration(proxy.getPipelineConfiguration());
        }

        @Override
        public void setResult(Result result) throws IllegalArgumentException {
            try {
                PipelineConfiguration pipe = controller.makePipelineConfiguration();
                setPipelineConfiguration(pipe);
                controller.initializeController(new GlobalParameterSet());
                Receiver out = controller.getConfiguration().getSerializerFactory().getReceiver(
                        result, new SerializationProperties(), pipe);
                Receiver in = controller.getStreamingReceiver(controller.getInitialMode(),
                                                              new TreeReceiver(out));
                setReceiver(in);
            } catch (XPathException e) {
                throw new IllegalArgumentException(e);
            }
        }

        @Override
        public void setSystemId(String systemID) {
            this.systemId = systemID;
        }

        @Override
        public String getSystemId() {
            return systemId;
        }

        @Override
        public Transformer getTransformer() {
            return StreamingTransformerImpl.this;
        }
    }

}

