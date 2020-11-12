////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.jaxp;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.Sender;
import net.sf.saxon.lib.ErrorReporterToListener;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.SerializerFactory;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.XmlProcessingException;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.SAXParseException;

import javax.xml.transform.*;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Properties;

/**
 * Saxon implementation of the JAXP IdentityTransformer.
 * This is used mainly for serializing various kinds of source under
 * the control of serialization parameters.
 */

public class IdentityTransformer extends Transformer {

    private Configuration configuration;
    private Properties localOutputProperties;
    private URIResolver uriResolver;
    private ErrorListener errorListener;

    protected IdentityTransformer(Configuration config) {
        this.configuration = config;
        this.uriResolver = config.getURIResolver();
    }

    /**
     * <p>Reset this <code>Transformer</code> to its original configuration.</p>
     * <p><code>Transformer</code> is reset to the same state as when it was created with
     * {@link javax.xml.transform.TransformerFactory#newTransformer()},
     * {@link javax.xml.transform.TransformerFactory#newTransformer(javax.xml.transform.Source source)} or
     * {@link javax.xml.transform.Templates#newTransformer()}.
     * <code>reset()</code> is designed to allow the reuse of existing <code>Transformer</code>s
     * thus saving resources associated with the creation of new <code>Transformer</code>s.</p>
     * <p>The reset <code>Transformer</code> is not guaranteed to have the same {@link javax.xml.transform.URIResolver}
     * or {@link javax.xml.transform.ErrorListener} <code>Object</code>s, e.g. {@link Object#equals(Object obj)}.
     * It is guaranteed to have a functionally equal <code>URIResolver</code>
     * and <code>ErrorListener</code>.</p>
     *
     * @throws UnsupportedOperationException When implementation does not
     *                                       override this method.
     * @since 1.5
     */
    @Override
    public void reset() {
        localOutputProperties = null;
        uriResolver = getConfiguration().getURIResolver();
        errorListener = null;
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
        this.uriResolver = resolver;
    }

    /**
     * Get an object that will be used to resolve URIs used in
     * document().
     *
     * @return An object that implements the URIResolver interface,
     *         or null.
     */
    @Override
    public URIResolver getURIResolver() {
        return uriResolver;
    }

    /**
     * Set the error event listener in effect for the transformation.
     *
     * @param listener The new error listener.
     * @throws IllegalArgumentException if listener is null.
     */
    @Override
    public void setErrorListener(ErrorListener listener) throws IllegalArgumentException {
        this.errorListener = Objects.requireNonNull(listener);
    }

    /**
     * Get the error event handler in effect for the transformation.
     * Implementations must provide a default error listener.
     *
     * @return The current error handler, which should never be null.
     */
    @Override
    public ErrorListener getErrorListener() {
        return errorListener;
    }

    /**
     * Set the output properties for the transformation.  These
     * properties will override properties set in the Templates
     * with xsl:output.
     * <p>If argument to this function is null, any properties
     * previously set are removed, and the value will revert to the value
     * defined in the templates object.</p>
     * <p>Pass a qualified property key name as a two-part string, the namespace
     * URI enclosed in curly braces ({}), followed by the local name. If the
     * name has a null URL, the String only contain the local name. An
     * application can safely check for a non-null URI by testing to see if the
     * first character of the name is a '{' character.</p>
     * <p>For example, if a URI and local name were obtained from an element
     * defined with &lt;xyz:foo
     * xmlns:xyz="http://xyz.foo.com/yada/baz.html"/&gt;,
     * then the qualified name would be "{http://xyz.foo.com/yada/baz.html}foo".
     * Note that no prefix is used.</p>
     * <p>An <code>IllegalArgumentException</code> is thrown if any of the
     * argument keys are not recognized and are not namespace qualified.</p>
     * <p>As well as the properties defined in the JAXP OutputKeys class,
     * Saxon defines an additional set of properties in {@link net.sf.saxon.lib.SaxonOutputKeys}.
     * These fall into two categories: Constants representing serialization
     * properties defined in XSLT 2.0 (which are not yet supported by JAXP),
     * and constants supporting Saxon extensions to the set of serialization</p>
     * properties.
     *
     * @param properties A set of output properties that will be
     *                   used to override any of the same properties in affect
     *                   for the transformation.
     * @throws IllegalArgumentException When keys are not recognized and
     *                                  are not namespace qualified.
     * @see javax.xml.transform.OutputKeys
     * @see java.util.Properties
     */
    @Override

    public void setOutputProperties(Properties properties) {
        if (properties == null) {
            localOutputProperties = null;
        } else {
            for (String key : properties.stringPropertyNames()) {
                setOutputProperty(key, properties.getProperty(key));
            }
        }
    }

    /**
     * Get the output properties for the transformation.
     * <p>As well as the properties defined in the JAXP OutputKeys class,
     * Saxon defines an additional set of properties in {@link net.sf.saxon.lib.SaxonOutputKeys}.
     * These fall into two categories: Constants representing serialization
     * properties defined in XSLT 2.0 (which are not yet supported by JAXP),
     * and constants supporting Saxon extensions to the set of serialization
     * properties.</p>
     *
     * @return the output properties being used for the transformation,
     * including properties defined in the stylesheet for the unnamed
     * output format
     * @see net.sf.saxon.lib.SaxonOutputKeys
     */

    @Override
    public Properties getOutputProperties() {

        // Make a copy, so that modifications to the returned properties object have no effect (even on the
        // local output properties)

        Properties newProps = new Properties();
        Properties sheetProperties = getStylesheetOutputProperties();
        Enumeration keys = sheetProperties.propertyNames();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            newProps.setProperty(key, sheetProperties.getProperty(key));
        }
        if (localOutputProperties != null) {
            keys = localOutputProperties.propertyNames();
            while (keys.hasMoreElements()) {
                String key = (String) keys.nextElement();
                newProps.setProperty(key, localOutputProperties.getProperty(key));
            }
        }
        return newProps;
    }

    /**
     * Get the output properties defined in the stylesheet. For an identity transformer this is an empty set,
     * but the method is overridden in subclasses.
     *
     * @return the serialization properties defined in the stylesheet, if any.
     */

    protected Properties getStylesheetOutputProperties() {
        return new Properties();
    }

    /**
     * Get the local output properties held in this Transformer object, that is the properties
     * explicitly requested using setOutputProperty() or setOutputProperties()
     *
     * @return the local output properties
     */

    protected Properties getLocalOutputProperties() {
        if (localOutputProperties == null) {
            makeLocalOutputProperties();
        }
        return localOutputProperties;
    }

    /**
     * Make the localOutputProperties object. This is a Properties object containing
     * the properties set by calls on setOutputProperty or setOutputProperties; it does not include
     * properties set in the stylesheet or in the configuration.
     */

    private void makeLocalOutputProperties() {
        localOutputProperties = new Properties();
    }

    /**
     * <p>Get an output property that is in effect for the transformer.</p>
     * <p>If a property has been set using {@link #setOutputProperty},
     * that value will be returned. Otherwise, if a property is explicitly
     * specified in the stylesheet, that value will be returned. If
     * the value of the property has been defaulted, that is, if no
     * value has been set explicitly either with {@link #setOutputProperty} or
     * in the stylesheet, the result may vary depending on
     * implementation and input stylesheet.</p>
     *
     * @param name A non-null String that specifies an output
     *             property name, which may be namespace qualified.
     * @return The string value of the output property, or null
     * if no property was found.
     * @throws IllegalArgumentException If the property is not supported.
     * @see javax.xml.transform.OutputKeys
     */
    @Override
    public String getOutputProperty(String name) throws IllegalArgumentException {
        try {
            getConfiguration().getSerializerFactory().checkOutputProperty(name, null);
        } catch (XPathException err) {
            throw new IllegalArgumentException(err.getMessage());
        }
        String value = null;
        if (localOutputProperties != null) {
            value = localOutputProperties.getProperty(name);
        }
        if (value == null) {
            value = getStylesheetOutputProperties().getProperty(name);
        }
        return value;
    }

    /**
     * Set an output property that will be in effect for the
     * transformation.
     * <p>Pass a qualified property name as a two-part string, the namespace URI
     * enclosed in curly braces ({}), followed by the local name. If the
     * name has a null URL, the String only contain the local name. An
     * application can safely check for a non-null URI by testing to see if the
     * first character of the name is a '{' character.</p>
     * <p>For example, if a URI and local name were obtained from an element
     * defined with &lt;xyz:foo
     * xmlns:xyz="http://xyz.foo.com/yada/baz.html"/&gt;,
     * then the qualified name would be "{http://xyz.foo.com/yada/baz.html}foo".
     * Note that no prefix is used.</p>
     * <p>The Properties object that was passed to {@link #setOutputProperties}
     * won't be effected by calling this method.</p>
     *
     * @param name  A non-null String that specifies an output
     *              property name, which may be namespace qualified.
     * @param value The non-null string value of the output property.
     * @throws IllegalArgumentException If the property is not supported, and is
     *                                  not qualified with a namespace.
     * @see javax.xml.transform.OutputKeys
     */
    @Override
    public void setOutputProperty(String name, String value) throws IllegalArgumentException {
        if (localOutputProperties == null) {
            makeLocalOutputProperties();
        }
        try {
            value = getConfiguration().getSerializerFactory().checkOutputProperty(name, value);
        } catch (XPathException err) {
            throw new IllegalArgumentException(err.getMessage());
        }
        localOutputProperties.setProperty(name, value);
    }

    /**
     * Add a parameter for the transformation.
     * <p>Pass a qualified name as a two-part string, the namespace URI
     * enclosed in curly braces ({}), followed by the local name. If the
     * name has a null URL, the String only contain the local name. An
     * application can safely check for a non-null URI by testing to see if the
     * first character of the name is a '{' character.</p>
     * <p>For example, if a URI and local name were obtained from an element
     * defined with &lt;xyz:foo
     * xmlns:xyz="http://xyz.foo.com/yada/baz.html"/&gt;,
     * then the qualified name would be "{http://xyz.foo.com/yada/baz.html}foo".
     * Note that no prefix is used.</p>
     *
     * @param name  The name of the parameter, which may begin with a
     *              namespace URI in curly braces ({}).
     * @param value The value object.  This can be any valid Java object. It is
     *              up to the processor to provide the proper object coersion or to simply
     *              pass the object on for use in an extension.
     * @throws NullPointerException If value is null.
     */
    @Override
    public void setParameter(String name, Object value) {
        // No action. Since parameters have no effect on the transformation, we ignore them
    }

    /**
     * Get a parameter that was explicitly set with setParameter.
     * <p>This method does not return a default parameter value, which
     * cannot be determined until the node context is evaluated during
     * the transformation process.
     * <p>The Saxon implementation for an IdentityTransformer always
     * returns null, since parameters have no effect on an identity
     * transformation.</p>
     *
     * @param name of <code>Object</code> to get
     * @return A parameter that has been set with setParameter.
     */
    @Override
    public Object getParameter(String name) {
        return null;
    }

    /**
     * Clear all parameters set with setParameter.
     */
    @Override
    public void clearParameters() {
        // No action
    }

    /**
     * Perform identify transformation from Source to Result
     */

    @Override
    public void transform(Source source, Result result)
            throws TransformerException {
        try {
            SerializerFactory sf = getConfiguration().getSerializerFactory();
            Receiver receiver = sf.getReceiver(result, new SerializationProperties(getOutputProperties()));
            ParseOptions options = receiver.getPipelineConfiguration().getParseOptions();
            if (errorListener != null) {
                options.setErrorReporter(new ErrorReporterToListener(errorListener));
            }
            options.setContinueAfterValidationErrors(true);
            Sender.send(source, receiver, options);
        } catch (XPathException err) {
            Throwable cause = err.getException();
            if (cause instanceof SAXParseException) {
                // This generally means the error was already reported.
                // But if a RuntimeException occurs in Saxon during a callback from
                // the Crimson parser, Crimson wraps this in a SAXParseException without
                // reporting it further.
                SAXParseException spe = (SAXParseException) cause;
                cause = spe.getException();
                if (cause instanceof RuntimeException) {
                    reportFatalError(err);
                }
            } else {
                reportFatalError(err);
            }
            throw err;
        }
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    protected void reportFatalError(XPathException err) {
        try {
            if (errorListener != null) {
                errorListener.error(err);
            } else {
                getConfiguration().makeErrorReporter().report(new XmlProcessingException(err));
            }
        } catch (TransformerException e) {
            // no action if error reporting fails
        }
    }

}

