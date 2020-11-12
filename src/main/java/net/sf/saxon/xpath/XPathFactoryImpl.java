////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.xpath;

import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.Validation;

import javax.xml.XMLConstants;
import javax.xml.xpath.*;

/**
 * Saxon implementation of the JAXP 1.3 XPathFactory
 */
public class XPathFactoryImpl extends XPathFactory implements Configuration.ApiProvider {

    private Configuration config;
    private XPathVariableResolver variableResolver;
    private XPathFunctionResolver functionResolver;

    /**
     * Default constructor: this creates a Configuration as well as creating the XPathFactory. Any documents
     * accessed using this XPathFactory must be built using this same Configuration.
     */

    public XPathFactoryImpl() {
        config = Configuration.newConfiguration();
        setConfiguration(config);
        Version.platform.registerAllBuiltInObjectModels(config);
    }

    /**
     * Constructor using a user-supplied Configuration.
     * This constructor is useful if the document to be queried already exists, as it allows the configuration
     * associated with the document to be used with this XPathFactory.
     *
     * @param config the Saxon configuration
     */

    public XPathFactoryImpl(Configuration config) {
        this.config = config;
        config.setProcessor(this);
    }

    /**
     * Set the Configuration for the factory
     *
     * @param config the Saxon Configuration to be used
     */

    public void setConfiguration(Configuration config) {
        this.config = config;
        config.setProcessor(this);
    }

    /**
     * Get the Configuration object used by this XPathFactory
     *
     * @return the Saxon configuration
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Test whether a given object model is supported. Returns true if the object model
     * is the Saxon object model, DOM, JDOM, DOM4J, or XOM
     *
     * @param model The URI identifying the object model.
     * @return true if the object model is one of the following (provided that the supporting
     *         JAR file is available on the classpath)
     *         {@link net.sf.saxon.lib.NamespaceConstant#OBJECT_MODEL_SAXON},
     *         {@link XPathConstants#DOM_OBJECT_MODEL},
     *         {@link net.sf.saxon.lib.NamespaceConstant#OBJECT_MODEL_JDOM}, or
     *         {@link NamespaceConstant#OBJECT_MODEL_XOM}, or
     *         {@link net.sf.saxon.lib.NamespaceConstant#OBJECT_MODEL_DOM4J}.
     *         Saxon also allows user-defined external object models to be registered with the Configuration, and
     *         this method will return true in respect of any such model.
     */
    @Override
    public boolean isObjectModelSupported(String model) {
        boolean debug = System.getProperty("jaxp.debug") != null;
        boolean result = silentIsObjectModelSupported(model);
        if (debug) {
            System.err.println("JAXP: Calling " + getClass().getName() + ".isObjectModelSupported(\"" + model + "\")");
            System.err.println("JAXP: -- returning " + (result ? "true" : "false (check all required libraries are on the class path"));
        }
        return result;
    }

    private boolean silentIsObjectModelSupported(String model) {
        return model.equals(NamespaceConstant.OBJECT_MODEL_SAXON) || config.getExternalObjectModel(model) != null;
    }

    /**
     * Set a feature of this XPath implementation. The features currently
     * recognized are:
     * <ul>
     * <li> {@link XMLConstants#FEATURE_SECURE_PROCESSING} </li>
     * <li> {@link net.sf.saxon.lib.FeatureKeys#SCHEMA_VALIDATION}: requests schema validation of source documents.
     * The property is rejected if the configuration is not schema-aware. </li>
     * </ul>
     * <p>In addition, any Saxon configuration feature (listed in {@link FeatureKeys} can be used
     * provided the value is a boolean. (For non-boolean configuration properties, drop down to the underlying
     * Saxon {@link Configuration} object and call <code>setConfigurationProperty()</code>)</p>
     *
     * @param feature a URI identifying the feature
     * @param b       true to set the feature on, false to set it off
     * @throws XPathFactoryConfigurationException
     *          if the feature name is not recognized
     */

    @Override
    public void setFeature(String feature, boolean b) throws XPathFactoryConfigurationException {
        if (feature.equals(FEATURE_SECURE_PROCESSING)) {
            config.setBooleanProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS, !b);
        } else if (feature.equals(FeatureKeys.SCHEMA_VALIDATION)) {
            config.setSchemaValidationMode(b ? Validation.STRICT : Validation.STRIP);
        } else {
            try {
                config.setBooleanProperty(feature, b);
            } catch (IllegalArgumentException err) {
                throw new XPathFactoryConfigurationException("Unknown or non-boolean feature: " + feature);
            }
        }
    }

    /**
     * Get a feature of this XPath implementation. The only features currently
     * recognized are:
     * <ul>
     * <li> {@link #FEATURE_SECURE_PROCESSING} </li>
     * <li> {@link net.sf.saxon.lib.FeatureKeys#SCHEMA_VALIDATION}: requests schema validation of source documents. </li>
     * </ul>
     * <p>In addition, any Saxon configuration feature (listed in {@link FeatureKeys} can be used
     * provided the value is a boolean. (For non-boolean configuration properties, drop down to the underlying
     * Saxon {@link Configuration} object and call <code>getConfigurationProperty()</code>)</p>
     *
     * @param feature a URI identifying the feature
     * @return true if the feature is on, false if it is off
     * @throws XPathFactoryConfigurationException
     *          if the feature name is not recognized
     */

    @Override
    public boolean getFeature(String feature) throws XPathFactoryConfigurationException {
        if (feature.equals(FEATURE_SECURE_PROCESSING)) {
            return !config.getBooleanProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS);
        } else if (feature.equals(FeatureKeys.SCHEMA_VALIDATION)) {
            return config.getSchemaValidationMode() == Validation.STRICT;
        } else {
            try {
                Object o = config.getConfigurationProperty(feature);
                if (o instanceof Boolean) {
                    return (Boolean) o;
                } else {
                    throw new XPathFactoryConfigurationException(
                            "Configuration property " + feature + " is not a boolean (it is an instance of " + o.getClass() + ")");
                }
            } catch (IllegalArgumentException e) {
                throw new XPathFactoryConfigurationException("Unknown feature: " + feature);
            }
        }
    }

    /**
     * Set a resolver for XPath variables. This will be used to obtain the value of
     * any variable referenced in an XPath expression. The variable resolver must be allocated
     * before the expression is compiled, but it will only be called when the expression
     * is evaluated.
     *
     * @param xPathVariableResolver The object used to resolve references to variables.
     */
    @Override
    public void setXPathVariableResolver(XPathVariableResolver xPathVariableResolver) {
        variableResolver = xPathVariableResolver;
    }

    /**
     * Set a resolver for XPath functions. This will be used to obtain an implementation
     * of any external function referenced in an XPath expression. This is not required for
     * system functions, Saxon extension functions, constructor functions named after types,
     * or extension functions bound using a namespace that maps to a Java class.
     *
     * @param xPathFunctionResolver The object used to resolve references to external functions.
     */

    @Override
    public void setXPathFunctionResolver(XPathFunctionResolver xPathFunctionResolver) {
        functionResolver = xPathFunctionResolver;
    }

    /**
     * Create an XPath evaluator
     *
     * @return an XPath object, which can be used to compile and execute XPath expressions.
     */
    /*@NotNull*/
    @Override
    public XPath newXPath() {
        XPathEvaluator xpath = new XPathEvaluator(config);
        xpath.setXPathFunctionResolver(functionResolver);
        xpath.setXPathVariableResolver(variableResolver);
        return xpath;
    }

    private static String FEATURE_SECURE_PROCESSING = XMLConstants.FEATURE_SECURE_PROCESSING;
    // "http://javax.xml.XMLConstants/feature/secure-processing";

}

