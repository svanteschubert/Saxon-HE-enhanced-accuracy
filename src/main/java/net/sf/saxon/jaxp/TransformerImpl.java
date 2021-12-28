////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.jaxp;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.lib.SerializerFactory;
import net.sf.saxon.s9api.*;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XsltController;
import org.xml.sax.XMLFilter;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.TransformerHandler;

/**
 * Saxon implementation of the JAXP Transformer interface.
 * <p>Since Saxon 9.6, JAXP interfaces are implemented as a layer above the s9api interface</p>
 */
public class TransformerImpl extends AbstractTransformerImpl {

    private XsltTransformer xsltTransformer;

    protected TransformerImpl(XsltExecutable e, XsltTransformer t) {
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
            xsltTransformer.setSource(xmlSource);
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
            xsltTransformer.setDestination(destination);

            xsltTransformer.transform();
            if (destination instanceof Serializer && ((Serializer)destination).isMustCloseAfterUse()) {
                destination.close();
            }
        } catch (SaxonApiException e) {
            throw XPathException.makeXPathException(e);
        }
    }

    @Override
    protected void setConvertedParameter(QName name, XdmValue value) {
        xsltTransformer.setParameter(name, value);
    }

    /**
     * Clear all parameters set with setParameter.
     */
    @Override
    public void clearParameters() {
        super.clearParameters();
        xsltTransformer.clearParameters();
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
     * Supply an initial template for a transformation.
     * <p>This is a Saxon extension to the JAXP interface, needed for XSLT 2.0</p>
     * <p>Changed in 9.9 so it no longer validates the supplied name; an incorrect name will
     * lead to an error later.</p>
     *
     * @param name the name of the initial template, in Clark notation (either a local name,
     *             or "{uri}local")
     */

    public void setInitialTemplate(String name) {
        xsltTransformer.setInitialTemplate(QName.fromClarkName(name));
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

    public XsltTransformer getUnderlyingXsltTransformer() {
        return xsltTransformer;
    }

    /**
     * Get the internal Saxon Controller instance that implements this transformation.
     * Note that the Controller interface will not necessarily remain stable in future releases
     * @return the underlying Saxon Controller instance
     */

    @Override
    public XsltController getUnderlyingController() {
        return xsltTransformer.getUnderlyingController();
    }

    /**
     * Create a JAXP TransformerHandler to perform the transformation
     * @return a JAXP TransformerHandler, which allows the transformation to be performed
     * in "push" mode on a SAX pipeline.
     */

    public TransformerHandler newTransformerHandler() {
        return new TransformerHandlerImpl(this);
    }

    /**
     * Create a JAXP XMLFilter which allows this transformation to be added to a SAX pipeline
     * @return the transformation in the form of an XMLFilter
     */

    @Override
    public XMLFilter newXMLFilter() {
        return new FilterImpl(this);
    }

    @Override
    public void reset() {
        super.reset();
        getUnderlyingController().reset();
    }

}

