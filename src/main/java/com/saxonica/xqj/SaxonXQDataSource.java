////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package com.saxonica.xqj;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.AllElementsSpaceStrippingRule;
import net.sf.saxon.om.IgnorableSpaceStrippingRule;
import net.sf.saxon.om.NoElementsSpaceStrippingRule;
import net.sf.saxon.om.SpaceStrippingRule;
import net.sf.saxon.value.BooleanValue;

import javax.xml.xquery.XQConnection;
import javax.xml.xquery.XQDataSource;
import javax.xml.xquery.XQException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.util.Enumeration;
import java.util.Properties;

/**
 * Saxon implementation of the XQJ XQDataSource interface. The first action of a client application
 * is to instantiate a SaxonXQDataSource. This is done directly: there is no factory class as with JAXP.
 * An application that does not want compile-time references to the Saxon XQJ implementation can instantiate
 * this class dynamically using the reflection API (class.newInstance()).
 * <p>For full Javadoc descriptions of the public methods, see the XQJ specification.</p>
 */
public class SaxonXQDataSource implements XQDataSource, Configuration.ApiProvider {

    private Configuration config;
    private PrintWriter logger;

    /**
     * Create a SaxonXQDataSource using a default configuration.
     * The Configuration will be an EE, PE, or HE configuration depending on the
     * what is found on the classpath
     */

    public SaxonXQDataSource() {
        config = Configuration.newConfiguration();
        config.setProcessor(this);
    }

    /**
     * Create a Saxon XQDataSource with a specific configuration
     *
     * @param config The Saxon configuration to be used
     */

    public SaxonXQDataSource(Configuration config) {
        this.config = config;
    }

    /**
     * Get the Saxon Configuration in use. Changes made to this Configuration will affect this
     * data source and XQJ connections created from it (either before or afterwards). Equally,
     * changes made to this SaxonXQDataSource are reflected in the Configuration object (which means
     * they also impact any other SaxonXQDataSource objects that share the same Configuration).
     *
     * @return the configuration in use.
     */

    public Configuration getConfiguration() {
        return config;
    }

    /*@NotNull*/
    @Override
    public XQConnection getConnection() {
        return new SaxonXQConnection(this);
    }

    /**
     * Get a connection based on an underlying JDBC connection
     *
     * @param con the JDBC connection
     * @return a connection based on an underlying JDBC connection
     * @throws XQException The Saxon implementation of this method always throws
     *                     an XQException, indicating that Saxon does not support connection to a JDBC data source.
     */

    /*@NotNull*/
    @Override
    public XQConnection getConnection(Connection con) throws XQException {
        throw new XQException("Saxon cannot connect to a SQL data source");
    }

    /**
     * Get a connection, by supplying a username and password. The Saxon implementation of this is equivalent
     * to the default constructor: the username and password are ignored.
     *
     * @param username the user name
     * @param password the password
     * @return a connection
     */

    /*@NotNull*/
    @Override
    public XQConnection getConnection(String username, String password) {
        return getConnection();
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public PrintWriter getLogWriter() {
        return logger;
    }

    /**
     * Get a configuration property setting. The properties that are supported, and their meanings, are the
     * same as the properties that can be obtained using "get" methods; for example
     * <code>getProperty("dtdValidation")</code>  returns the same result as <code>getDtdValidation()</code>.
     * <p>Further Saxon configuration properties are available using the URIs defined as constants in
     * {@link net.sf.saxon.lib.FeatureKeys}</p>
     *
     * @param name the name of the configuration property
     * @return the value of the configuration property. Note that in the case of on/off properties this
     *         will be returned as the string true/false
     * @throws XQException if the property name or value is invalid
     */

    /*@Nullable*/
    @Override
    public String getProperty(String name) throws XQException {
        checkNotNull(name, "name");
        switch (name) {
            case "allowExternalFunctions":
                return getAllowExternalFunctions();
            case "dtdValidation":
                return getDtdValidation();
            case "expandAttributeDefaults":
                return getExpandAttributeDefaults();
            case "expandXInclude":
                return getExpandXInclude();
            case "retainLineNumbers":
                return getRetainLineNumbers();
            case "schemaValidationMode":
                return getSchemaValidationMode();
            case "stripWhitespace":
                return getStripWhitespace();
            case "useXsiSchemaLocation":
                return getUseXsiSchemaLocation();
            case "xmlVersion":
                return getXmlVersion();
            case "xsdVersion":
                return getXsdVersion();
            default:
                try {
                    return config.getConfigurationProperty(name).toString();
                } catch (IllegalArgumentException e) {
                    throw new XQException("Property " + name + " is not recognized");
                } catch (NullPointerException err) {
                    throw new XQException("Null property name or value");
                }
        }

    }

    /*@NotNull*/ private static String[] supportedPropertyNames = {
            "allowExternalFunctions",
            "dtdValidation",
            "expandAttributeDefaults",
            "expandXInclude",
            "retainLineNumbers",
            "schemaValidationMode",
            "stripWhitespace",
            "useXsiSchemaLocation",
            "xmlVersion",
            "xsdVersion"
    };

    /*@NotNull*/
    @Override
    public String[] getSupportedPropertyNames() {
        return supportedPropertyNames;
    }

    @Override
    public void setLoginTimeout(int seconds) {
        // no-op
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        logger = out;
    }

    @Override
    public void setProperties(Properties props) throws XQException {
        checkNotNull(props, "props");
        Enumeration iter = props.keys();
        while (iter.hasMoreElements()) {
            String name = (String) iter.nextElement();
            String value = props.getProperty(name);
            setProperty(name, value);
        }
    }

    /**
     * Set a configuration property. The properties that are supported, and their meanings, are the
     * same as the properties that can be obtained using "set" methods; for example
     * <code>setProperty("dtdValidation", "true")</code>  has the same effect as
     * <code>setDtdValidation("true")</code>.
     * <p>Further Saxon configuration properties are available using the URIs defined as constants in
     * {@link net.sf.saxon.lib.FeatureKeys}</p>
     *
     * @param name  the name of the configuration property
     * @param value the value of the configuration property
     * @throws XQException if the property name or value is invalid
     */

    @Override
    public void setProperty(String name, String value) throws XQException {
        SaxonXQDataSource.checkNotNull(name, "name");
        SaxonXQDataSource.checkNotNull(value, "value");
        switch (name) {
            case "allowExternalFunctions":
                setAllowExternalFunctions(value);
                break;
            case "dtdValidation":
                setDtdValidation(value);
                break;
            case "expandAttributeDefaults":
                setExpandAttributeDefaults(value);
                break;
            case "expandXInclude":
                setExpandXInclude(value);
                break;
            case "retainLineNumbers":
                setRetainLineNumbers(value);
                break;
            case "schemaValidationMode":
                setSchemaValidationMode(value);
                break;
            case "stripWhitespace":
                setStripWhitespace(value);
                break;
            case "useXsiSchemaLocation":
                setUseXsiSchemaLocation(value);
                break;
            case "xmlVersion":
                setXmlVersion(value);
                break;
            case "xsdVersion":
                setXsdVersion(value);
                break;
            default:
                try {
                    config.setConfigurationProperty(name, value);
                } catch (IllegalArgumentException err) {
                    throw new XQException("Unrecognized property or invalid value for " + name + ": " + err.getMessage());
                } catch (NullPointerException err) {
                    throw new XQException("Null property name or value");
                }
                break;
        }
    }

    static void checkNotNull(/*@Nullable*/ Object arg, String name) throws XQException {
        if (arg == null) {
            throw new XQException("Argument " + name + " is null");
        }
    }

    /**
     * Say whether queries are allowed to call external functions.
     *
     * @param value set to "true" if external function calls are allowed (default) or "false" otherwise
     */

    public void setAllowExternalFunctions(String value) {
        if ("true".equals(value)) {
            config.setBooleanProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS, true);
        } else if ("false".equals(value)) {
            config.setBooleanProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS, false);
        } else {
            throw new IllegalArgumentException("allowExternalFunctions");
        }
    }

    /**
     * Ask whether queries are allowed to call external functions.
     *
     * @return "true" if external function calls are allowed, "false" otherwise
     */

    /*@NotNull*/
    public String getAllowExternalFunctions() {
        return config.getBooleanProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS) ? "true" : "false";
    }

    /**
     * Say whether source documents are to be parsed with DTD validation enabled
     *
     * @param value "true" if DTD validation is to be enabled, otherwise "false". Default is "false".
     */

    public void setDtdValidation(String value) {
        if ("true".equals(value)) {
            config.setValidation(true);
        } else if ("false".equals(value)) {
            config.setValidation(false);
        } else {
            throw new IllegalArgumentException("dtdValidation");
        }
    }

    /**
     * Ask whether source documents are to be parsed with DTD validation enabled
     *
     * @return "true" if DTD validation is to be enabled, otherwise "false". Default is "false".
     */

    /*@NotNull*/
    public String getDtdValidation() {
        return config.isValidation() ? "true" : "false";
    }

    /**
     * Say whether whether fixed and default values defined
     * in a schema or DTD will be expanded. By default, or if the value is "true" (and for conformance with the
     * specification) validation against a DTD or schema will cause default values defined in the schema
     * or DTD to be inserted into the document. Setting this feature to "false" suppresses this behaviour. In
     * the case of DTD-defined defaults this only works if the XML parser reports whether each attribute was
     * specified in the source or generated by expanding a default value. Not all XML parsers report this
     * information.
     *
     * @param value "true" if default values are to be expanded, otherwise "false". Default is "true".
     */

    public void setExpandAttributeDefaults(String value) {
        if ("true".equals(value)) {
            config.setExpandAttributeDefaults(true);
        } else if ("false".equals(value)) {
            config.setExpandAttributeDefaults(false);
        } else {
            throw new IllegalArgumentException("expandAttributeDefaults");
        }
    }

    /**
     * Ask whether fixed or default values defined in a schema or DTD will be expanded
     *
     * @return "true" if such values will be expanded, otherwise "false"
     */

    /*@NotNull*/
    public String getExpandAttributeDefaults() {
        return config.isExpandAttributeDefaults() ? "true" : "false";
    }

    /**
     * Say whether XInclude processing is to be applied to source documents
     *
     * @param value "true" if XInclude directives are to expanded, otherwise "false". Default is "false".
     */

    public void setExpandXInclude(String value) {
        if ("true".equals(value)) {
            config.setXIncludeAware(true);
        } else if ("false".equals(value)) {
            config.setXIncludeAware(false);
        } else {
            throw new IllegalArgumentException("expandXInclude");
        }
    }

    /**
     * Ask whether XInclude processing is to be applied to source documents
     *
     * @return "true" if  XInclude directives are to expanded, otherwise "false". Default is "false".
     */

    /*@NotNull*/
    public String getExpandXInclude() {
        return config.isXIncludeAware() ? "true" : "false";
    }


    /**
     * Say whether source documents should have line and column information retained. This
     * information is available via extension functions <code>saxon:line-number()</code> and
     * <code>saxon:column-number()</code>
     *
     * @param value "true" if line and column information is to be retained, otherwise "false". Default is "false".
     */

    public void setRetainLineNumbers(String value) {
        if ("true".equals(value)) {
            config.setLineNumbering(true);
        } else if ("false".equals(value)) {
            config.setLineNumbering(false);
        } else {
            throw new IllegalArgumentException("retainLineNumbers");
        }
    }

    /**
     * Ask whether line and column information will be retained for source documents
     *
     * @return "true" if line and column information is retained, otherwise "false"
     */

    /*@NotNull*/
    public String getRetainLineNumbers() {
        return config.isLineNumbering() ? "true" : "false";
    }

    /**
     * Say whether source documents should be validated against a schema
     *
     * @param value set to "strict" if source documents are to be subjected to strict validation,
     *              "lax" if source documents are to be subjected to lax validation, "skip" if source documents
     *              are not to be subjected to schema validation
     */

    public void setSchemaValidationMode(String value) {
        if ("strict".equals(value)) {
            config.setSchemaValidationMode(Validation.STRICT);
        } else if ("lax".equals(value)) {
            config.setSchemaValidationMode(Validation.LAX);
        } else if ("skip".equals(value)) {
            config.setSchemaValidationMode(Validation.SKIP);
        } else {
            throw new IllegalArgumentException("schemaValidationMode");
        }
    }

    /**
     * Ask whether source documents will be validated against a schema
     *
     * @return "strict" if source documents are to be subjected to strict validation,
     *         "lax" if source documents are to be subjected to lax validation, "skip" if source documents
     *         are not to be subjected to schema validation
     */

    public String getSchemaValidationMode() {
        return Validation.toString(config.getSchemaValidationMode());
    }

    /**
     * Say whether whitespace should be stripped when loading source documents
     *
     * @param value "all" if all whitespace text nodes are to be stripped, "ignorable" if
     *              only whitespace text nodes in elements defined in a schema or DTD as having element-only
     *              content are to be stripped, "none" if no whitespace text nodes are to be stripped
     */

    public void setStripWhitespace(String value) {
        if ("all".equals(value)) {
            config.getParseOptions().setSpaceStrippingRule(AllElementsSpaceStrippingRule.getInstance());
        } else if ("ignorable".equals(value)) {
            config.getParseOptions().setSpaceStrippingRule(IgnorableSpaceStrippingRule.getInstance());
        } else if ("none".equals(value)) {
            config.getParseOptions().setSpaceStrippingRule(NoElementsSpaceStrippingRule.getInstance());
        } else {
            throw new IllegalArgumentException("stripWhitespace");
        }
    }

    /**
     * Ask whether whitespace will be stripped when loading source documents
     *
     * @return "all" if all whitespace text nodes are to be stripped, "ignorable" if
     *         only whitespace text nodes in elements defined in a schema or DTD as having element-only
     *         content are to be stripped, "none" if no whitespace text nodes are to be stripped
     */

    /*@NotNull*/
    public String getStripWhitespace() {
        SpaceStrippingRule rule = config.getParseOptions().getSpaceStrippingRule();
        if (rule == AllElementsSpaceStrippingRule.getInstance()) {
            return "all";
        } else if (rule == IgnorableSpaceStrippingRule.getInstance()) {
            return "ignorable";
        } else {
            return "none";
        }
    }

    /**
     * Say whether the schema processor is to take account of xsi:schemaLocation and
     * xsi:noNamespaceSchemaLocation attributes encountered in an instance document being validated
     *
     * @param value set to "true" if these attributes are to be recognized (default) or "false" otherwise
     */

    public void setUseXsiSchemaLocation(String value) {
        if ("true".equals(value)) {
            config.setConfigurationProperty(FeatureKeys.USE_XSI_SCHEMA_LOCATION, BooleanValue.TRUE);
        } else if ("false".equals(value)) {
            config.setConfigurationProperty(FeatureKeys.USE_XSI_SCHEMA_LOCATION, BooleanValue.FALSE);
        } else {
            throw new IllegalArgumentException("useXsiSchemaLocation");
        }
    }

    /**
     * Ask whether the schema processor is to take account of xsi:schemaLocation and
     * xsi:noNamespaceSchemaLocation attributes encountered in an instance document being validated
     *
     * @return "true" if these attributes are to be recognized (default) or "false" otherwise
     */

    /*@NotNull*/
    public String getUseXsiSchemaLocation() {
        Boolean b = config.getConfigurationProperty(Feature.USE_XSI_SCHEMA_LOCATION);
        return b ? "true" : "false";
    }

    /**
     * Say whether XML 1.0 or XML 1.1 rules for XML names are to be followed
     *
     * @param value "1.0" (default) if XML 1.0 rules are to be used, "1.1" if XML 1.1
     *              rules apply
     */

    public void setXmlVersion(String value) {
        if ("1.0".equals(value)) {
            config.setXMLVersion(Configuration.XML10);
        } else if ("1.1".equals(value)) {
            config.setXMLVersion(Configuration.XML11);
        } else {
            throw new IllegalArgumentException("xmlVersion");
        }
    }

    /**
     * Ask whether XML 1.0 or XML 1.1 rules for XML names are to be followed
     *
     * @return "1.0" if XML 1.0 rules are to be used, "1.1" if XML 1.1
     *         rules apply
     */

    /*@NotNull*/
    public String getXmlVersion() {
        return config.getXMLVersion() == Configuration.XML10 ? "1.0" : "1.1";
    }

    /**
     * Say whether XML Schema 1.0 syntax must be used or whether XML Schema 1.1 features are allowed
     *
     * @param value "1.0" (default) if XML Schema 1.0 rules are to be followed, "1.1" if XML Schema 1.1
     *              features may be used
     */

    public void setXsdVersion(String value) {
        if ("1.0".equals(value)) {
            config.setConfigurationProperty(FeatureKeys.XSD_VERSION, "1.0");
        } else if ("1.1".equals(value)) {
            config.setConfigurationProperty(FeatureKeys.XSD_VERSION, "1.1");
        } else {
            throw new IllegalArgumentException("xsdVersion");
        }
    }

    /**
     * Ask whether XML Schema 1.0 syntax must be used or whether XML Schema 1.1 features are allowed
     *
     * @return "1.0" (default) if XML Schema 1.0 rules are to be followed, "1.1" if XML Schema 1.1
     *         features may be used
     */

    /*@Nullable*/
    public String getXsdVersion() {
        return config.getConfigurationProperty(Feature.XSD_VERSION);
    }

    /**
     * Register an extension function that is to be made available within any query. This method
     * allows deep integration of an extension function implemented as an instance of
     * {@link net.sf.saxon.lib.ExtensionFunctionDefinition}, using an arbitrary name and namespace.
     * This supplements the ability to call arbitrary Java methods using a namespace and local name
     * that are related to the Java class and method name.
     * <p><i>This method is a Saxon extension to the XQJ interface</i></p>
     *
     * @param function the class that implements the extension function. This must be a class that extends
     *                 {@link net.sf.saxon.lib.ExtensionFunctionCall}, and it must have a public zero-argument
     *                 constructor
     * @throws IllegalArgumentException if the class cannot be instantiated or does not extend
     *                                  {@link net.sf.saxon.lib.ExtensionFunctionCall}
     * @since 9.2
     */

    public void registerExtensionFunction(ExtensionFunctionDefinition function) {
        try {
            config.registerExtensionFunction(function);
        } catch (Exception err) {
            throw new IllegalArgumentException(err);
        }
    }
}

