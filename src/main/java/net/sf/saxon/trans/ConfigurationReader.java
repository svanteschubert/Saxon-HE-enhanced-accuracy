////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;


import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import net.sf.saxon.event.ContentHandlerProxy;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.ResultDocument;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.expr.parser.XPathParser;
import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.XmlProcessingError;
import net.sf.saxon.sxpath.IndependentContext;
import net.sf.saxon.trans.packages.PackageDetails;
import net.sf.saxon.trans.packages.PackageLibrary;
import net.sf.saxon.trans.packages.VersionedPackageName;
import net.sf.saxon.tree.util.FastStringBuffer;
import org.xml.sax.*;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Class used to read a config.xml file and transfer all settings from the file to the Configuration
 */

public class ConfigurationReader implements ContentHandler, NamespaceResolver {

    private int level = 0;
    private String section = null;
    private String subsection = null;
    private FastStringBuffer buffer = new FastStringBuffer(100);
    protected Configuration config;
    private ClassLoader classLoader = null;
    private List<XmlProcessingError> errors = new ArrayList<>();
    private Locator locator;
    private Stack<List<String[]>> namespaceStack = new Stack<>();
    private PackageLibrary packageLibrary;
    private PackageDetails currentPackage;
    private Configuration baseConfiguration;

    public ConfigurationReader() {
    }

    /**
     * Set the ClassLoader to be used for dynamic loading of the configuration, and for dynamic loading
     * of other classes used within the configuration. By default the class loader of this class is used.
     *
     * @param classLoader the ClassLoader to be used
     */

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Set a base Configuration to be used by the new Configuration. The new Configuration
     * shares a NamePool and document number allocator with the base Configuration
     *
     * @param base the base configuration to be used
     */

    public void setBaseConfiguration(Configuration base) {
        this.baseConfiguration = base;
    }

    /**
     * Create a Configuration based on the contents of this configuration file
     *
     * @param source the Source of the configuration file
     * @return the constructed Configuration
     * @throws XPathException if a failure occurs, typically an invalid configuration file
     */

    public Configuration makeConfiguration(Source source) throws XPathException {

        if (source instanceof NodeInfo) {
            ContentHandlerProxy proxy = new ContentHandlerProxy() {
                @Override
                public void startDocument(int properties) throws XPathException {
                    try {
                        getUnderlyingContentHandler().startDocument();
                    } catch (SAXException e) {
                        throw XPathException.makeXPathException(e);
                    }
                }

                @Override
                public void endDocument() throws XPathException {
                    try {
                        getUnderlyingContentHandler().endDocument();
                    } catch (SAXException e) {
                        throw XPathException.makeXPathException(e);
                    }
                }
            };
            proxy.setUnderlyingContentHandler(this);
            proxy.setPipelineConfiguration(((NodeInfo) source).getConfiguration().makePipelineConfiguration());
            proxy.open();
            setDocumentLocator(new Loc(source.getSystemId(), -1, -1));   //Must be after the open() call
            proxy.startDocument(ReceiverOption.NONE);
            ((NodeInfo) source).copy(proxy, CopyOptions.ALL_NAMESPACES, Loc.NONE);
            proxy.endDocument();
            proxy.close();
        } else {
            InputSource is;
            XMLReader parser = null;
            if (source instanceof SAXSource) {
                parser = ((SAXSource) source).getXMLReader();
                is = ((SAXSource) source).getInputSource();
            } else if (source instanceof StreamSource) {
                is = new InputSource(source.getSystemId());
                is.setCharacterStream(((StreamSource) source).getReader());
                is.setByteStream(((StreamSource) source).getInputStream());
            } else {
                throw new XPathException("Source for configuration file must be a StreamSource or SAXSource or NodeInfo");
            }
            if (parser == null) {
                // Don't use the parser from the pool, it might be validating
                parser = Version.platform.loadParser();
                try {
                    parser.setFeature("http://xml.org/sax/features/namespaces", true);
                    parser.setFeature("http://xml.org/sax/features/namespace-prefixes", false);
                } catch (SAXNotRecognizedException | SAXNotSupportedException e) {
                    throw new TransformerFactoryConfigurationError(e);
                }
            }
            try {
                parser.setContentHandler(this);
                parser.parse(is);
            } catch (IOException e) {
                throw new XPathException("Failed to read config file", e);
            } catch (SAXException e) {
                throw new XPathException("Failed to parse config file", e);
            }
        }

        if (!errors.isEmpty()) {
            ErrorReporter reporter;
            if (config == null) {
                reporter = new StandardErrorReporter();
            } else {
                reporter = config.makeErrorReporter();
            }
            for (XmlProcessingError err : errors) {
                reporter.report(err.asWarning());
            }
            throw XPathException.fromXmlProcessingError(errors.get(0));
        }
        if (baseConfiguration != null) {
            config.importLicenseDetails(baseConfiguration);
        }
        return config;
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
    }

    @Override
    public void startDocument() {
        namespaceStack.push(new ArrayList<>());
    }

    @Override
    public void endDocument() {
        namespaceStack.pop();
        if (config != null) {
            config.getDefaultXsltCompilerInfo().setPackageLibrary(packageLibrary);
        }
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) {
        namespaceStack.peek().add(new String[]{prefix, uri});
    }

    @Override
    public void endPrefixMapping(String prefix) {

    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) {
        buffer.setLength(0);
        if (NamespaceConstant.SAXON_CONFIGURATION.equals(uri)) {
            if (level == 0) {
                if (!"configuration".equals(localName)) {
                    error(localName, null, null, "configuration");
                }
                String edition = atts.getValue("edition");
                if (edition == null) {
                    edition = "HE";
                }
                switch (edition) {
                    case "HE":
                        config = new Configuration();
                        break;
                    case "PE":
                        config = Configuration.makeLicensedConfiguration(classLoader, "com.saxonica.config.ProfessionalConfiguration");
                        break;
                    case "EE":
                        config = Configuration.makeLicensedConfiguration(classLoader, "com.saxonica.config.EnterpriseConfiguration");
                        break;
                    default:
                        error("configuration", "edition", edition, "HE|PE|EE");
                        config = new Configuration();
                        break;
                }

                if (baseConfiguration != null) {
                    config.setNamePool(baseConfiguration.getNamePool());
                    config.setDocumentNumberAllocator(baseConfiguration.getDocumentNumberAllocator());
                }

                packageLibrary = new PackageLibrary(config.getDefaultXsltCompilerInfo());
                String licenseLoc = atts.getValue("licenseFileLocation");
                if (licenseLoc != null && !edition.equals("HE") && !config.isLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION)) {
                    String base = locator.getSystemId();
                    try {
                        URI absoluteLoc = ResolveURI.makeAbsolute(licenseLoc, base);
                        config.setConfigurationProperty(FeatureKeys.LICENSE_FILE_LOCATION, absoluteLoc.toString());
                    } catch (Exception err) {
                        XmlProcessingIncident incident = new XmlProcessingIncident("Failed to process license at " + licenseLoc);
                        incident.setCause(err);
                        errors.add(incident);
                    }
                }
                String targetEdition = atts.getValue("targetEdition");
                if (targetEdition != null) {
                    packageLibrary.getCompilerInfo().setTargetEdition(targetEdition);
                }
                String label = atts.getValue("label");
                if (label != null) {
                    config.setLabel(label);
                }
                config.getDynamicLoader().setClassLoader(classLoader);
            }
            if (level == 1) {
                section = localName;
                if ("global".equals(localName)) {
                    readGlobalElement(atts);
                } else if ("serialization".equals(localName)) {
                    readSerializationElement(atts);
                } else if ("xquery".equals(localName)) {
                    readXQueryElement(atts);
                } else if ("xslt".equals(localName)) {
                    readXsltElement(atts);
                } else if ("xsltPackages".equals(localName)) {
                    // no action until later;
                } else if ("xsd".equals(localName)) {
                    readXsdElement(atts);
                } else if ("resources".equals(localName)) {
                    // no action until later
                } else if ("collations".equals(localName)) {
                    // no action until later
                } else if ("localizations".equals(localName)) {
                    readLocalizationsElement(atts);
                } else {
                    error(localName, null, null, null);
                }
            } else if (level == 2) {
                subsection = localName;
                switch (section) {
                    case "resources":
                        if ("fileExtension".equals(localName)) {
                            readFileExtension(atts);
                        }
                        // no action until endElement()
                        break;
                    case "collations":
                        if (!"collation".equals(localName)) {
                            error(localName, null, null, "collation");
                        } else {
                            readCollation(atts);
                        }
                        break;
                    case "localizations":
                        if (!"localization".equals(localName)) {
                            error(localName, null, null, "localization");
                        } else {
                            readLocalization(atts);
                        }
                        break;
                    case "xslt":
                        if ("extensionElement".equals(localName)) {
                            readExtensionElement(atts);
                        } else {
                            error(localName, null, null, null);
                        }
                        break;
                    case "xsltPackages":
                        if ("package".equals(localName)) {
                            readXsltPackage(atts);
                        }
                        break;
                }
            } else if (level == 3) {
                if ("package".equals(subsection)) {
                    if ("withParam".equals(localName)) {
                        readWithParam(atts);
                    } else {
                        error(localName, null, null, null);
                    }
                }
            }
        } else {
            XmlProcessingIncident incident = new XmlProcessingIncident("Configuration elements must be in namespace " + NamespaceConstant.SAXON_CONFIGURATION);
            errors.add(incident);
        }
        level++;
        namespaceStack.push(new ArrayList<>());
    }

    private void readGlobalElement(Attributes atts) {
        Properties props = new Properties();
        for (int i = 0; i < atts.getLength(); i++) {
            String name = atts.getLocalName(i);
            String value = atts.getValue(i);
            if (!value.isEmpty() && atts.getURI(i).isEmpty()) {
                props.put(name, value);
            }
        }
        props.put("#element", "global");
        applyProperty(props, "allowedProtocols", Feature.ALLOWED_PROTOCOLS);
        applyProperty(props, "allowExternalFunctions", Feature.ALLOW_EXTERNAL_FUNCTIONS);
        applyProperty(props, "allowMultiThreading", Feature.ALLOW_MULTITHREADING);
        applyProperty(props, "allowOldJavaUriFormat", Feature.ALLOW_OLD_JAVA_URI_FORMAT);
        applyProperty(props, "allowSyntaxExtensions", Feature.ALLOW_SYNTAX_EXTENSIONS);
        applyProperty(props, "collationUriResolver", Feature.COLLATION_URI_RESOLVER_CLASS);
        applyProperty(props, "collectionFinder", Feature.COLLECTION_FINDER_CLASS);
        applyProperty(props, "compileWithTracing", Feature.COMPILE_WITH_TRACING);
        applyProperty(props, "debugByteCode", Feature.DEBUG_BYTE_CODE);
        applyProperty(props, "debugByteCodeDirectory", Feature.DEBUG_BYTE_CODE_DIR);
        applyProperty(props, "defaultCollation", Feature.DEFAULT_COLLATION);
        applyProperty(props, "defaultCollection", Feature.DEFAULT_COLLECTION);
        applyProperty(props, "defaultRegexEngine", Feature.DEFAULT_REGEX_ENGINE);
        applyProperty(props, "displayByteCode", Feature.DISPLAY_BYTE_CODE);
        applyProperty(props, "dtdValidation", Feature.DTD_VALIDATION);
        applyProperty(props, "dtdValidationRecoverable", Feature.DTD_VALIDATION_RECOVERABLE);
        applyProperty(props, "eagerEvaluation", Feature.EAGER_EVALUATION);
        applyProperty(props, "entityResolver", Feature.ENTITY_RESOLVER_CLASS);
        applyProperty(props, "errorListener", Feature.ERROR_LISTENER_CLASS);
        applyProperty(props, "environmentVariableResolver", Feature.ENVIRONMENT_VARIABLE_RESOLVER_CLASS);
        applyProperty(props, "expandAttributeDefaults", Feature.EXPAND_ATTRIBUTE_DEFAULTS);
        applyProperty(props, "generateByteCode", Feature.GENERATE_BYTE_CODE);
        applyProperty(props, "ignoreSAXSourceParser", Feature.IGNORE_SAX_SOURCE_PARSER);
        applyProperty(props, "lineNumbering", Feature.LINE_NUMBERING);
        applyProperty(props, "markDefaultedAttributes", Feature.MARK_DEFAULTED_ATTRIBUTES);
        applyProperty(props, "maxCompiledClasses", Feature.MAX_COMPILED_CLASSES);
        applyProperty(props, "monitorHotSpotByteCode", Feature.MONITOR_HOT_SPOT_BYTE_CODE);
        applyProperty(props, "optimizationLevel", Feature.OPTIMIZATION_LEVEL);
        applyProperty(props, "parser", Feature.SOURCE_PARSER_CLASS);
        applyProperty(props, "preEvaluateDoc", Feature.PRE_EVALUATE_DOC_FUNCTION);
        applyProperty(props, "preferJaxpParser", Feature.PREFER_JAXP_PARSER);
        applyProperty(props, "recognizeUriQueryParameters", Feature.RECOGNIZE_URI_QUERY_PARAMETERS);
        applyProperty(props, "retainNodeForDiagnostics", Feature.RETAIN_NODE_FOR_DIAGNOSTICS);
        applyProperty(props, "schemaValidation", Feature.SCHEMA_VALIDATION_MODE);
        applyProperty(props, "serializerFactory", Feature.SERIALIZER_FACTORY_CLASS);
        applyProperty(props, "sourceResolver", Feature.SOURCE_RESOLVER_CLASS);
        applyProperty(props, "stableCollectionUri", Feature.STABLE_COLLECTION_URI);
        applyProperty(props, "stableUnparsedText", Feature.STABLE_UNPARSED_TEXT);
        applyProperty(props, "standardErrorOutputFile", Feature.STANDARD_ERROR_OUTPUT_FILE);
        applyProperty(props, "streamability", Feature.STREAMABILITY);
        applyProperty(props, "streamingFallback", Feature.STREAMING_FALLBACK);
        applyProperty(props, "stripSpace", Feature.STRIP_WHITESPACE);
        applyProperty(props, "styleParser", Feature.STYLE_PARSER_CLASS);
        applyProperty(props, "suppressEvaluationExpiryWarning", Feature.SUPPRESS_EVALUATION_EXPIRY_WARNING);
        applyProperty(props, "suppressXPathWarnings", Feature.SUPPRESS_XPATH_WARNINGS);
        applyProperty(props, "suppressXsltNamespaceCheck", Feature.SUPPRESS_XSLT_NAMESPACE_CHECK);
        applyProperty(props, "thresholdForHotspotByteCode", Feature.THRESHOLD_FOR_HOTSPOT_BYTE_CODE);
        applyProperty(props, "timing", Feature.TIMING);
        applyProperty(props, "traceExternalFunctions", Feature.TRACE_EXTERNAL_FUNCTIONS);
        applyProperty(props, "traceListener", Feature.TRACE_LISTENER_CLASS);
        applyProperty(props, "traceListenerOutputFile", Feature.TRACE_LISTENER_OUTPUT_FILE);
        applyProperty(props, "traceOptimizerDecisions", Feature.TRACE_OPTIMIZER_DECISIONS);
        applyProperty(props, "treeModel", Feature.TREE_MODEL_NAME);
        applyProperty(props, "unparsedTextUriResolver", Feature.UNPARSED_TEXT_URI_RESOLVER_CLASS);
        applyProperty(props, "uriResolver", Feature.URI_RESOLVER_CLASS);
        applyProperty(props, "usePiDisableOutputEscaping", Feature.USE_PI_DISABLE_OUTPUT_ESCAPING);
        applyProperty(props, "useTypedValueCache", Feature.USE_TYPED_VALUE_CACHE);
        applyProperty(props, "validationComments", Feature.VALIDATION_COMMENTS);
        applyProperty(props, "validationWarnings", Feature.VALIDATION_WARNINGS);
        applyProperty(props, "versionOfXml", Feature.XML_VERSION);
        applyProperty(props, "xInclude", Feature.XINCLUDE);
    }

    private void applyProperty(Properties props, String attributeName, Feature feature) {
        String value = props.getProperty(attributeName);
        if (value != null) {
            try {
                config.setConfigurationProperty(feature.name, value);
            } catch (IllegalArgumentException e) {
                String message = e.getMessage();
                if (message.startsWith(attributeName)) {
                    message = message.replace(attributeName, "Value");
                }
                if (message.startsWith("Unknown configuration property")) {
                    message = "Property not available in Saxon-" + config.getEditionCode();
                }
                error(props.getProperty("#element"), attributeName, value, message);
            }
        }
    }

    private void readSerializationElement(Attributes atts) {
        Properties props = new Properties();
        for (int i = 0; i < atts.getLength(); i++) {
            String uri = atts.getURI(i);
            String name = atts.getLocalName(i);
            String value = atts.getValue(i);
            if (value.isEmpty()) {
                continue;
            }
            try {
                ResultDocument.setSerializationProperty(props,
                                                        uri, name, value, this, false, config);
            } catch (XPathException e) {
                errors.add(new XmlProcessingException(e));
            }
        }
        config.setDefaultSerializationProperties(props);
    }

    private void readCollation(Attributes atts) {
        Properties props = new Properties();
        String collationUri = null;
        for (int i = 0; i < atts.getLength(); i++) {
            if (atts.getURI(i).isEmpty()) {
                String name = atts.getLocalName(i);
                String value = atts.getValue(i);
                if (value.isEmpty()) {
                    continue;
                }
                if ("uri".equals(name)) {
                    collationUri = value;
                } else {
                    props.put(name, value);
                }
            }
        }
        if (collationUri == null) {
            errors.add(new XmlProcessingIncident("collation specified with no uri"));
        }
        StringCollator collator = null;
        try {
            collator = Version.platform.makeCollation(config, props, collationUri);
        } catch (XPathException e) {
            errors.add(new XmlProcessingIncident(e.getMessage()));
        }
        config.registerCollation(collationUri, collator);

    }

    private void readLocalizationsElement(Attributes atts) {
        for (int i = 0; i < atts.getLength(); i++) {
            if (atts.getURI(i).isEmpty()) {
                String name = atts.getLocalName(i);
                String value = atts.getValue(i);
                if ("defaultLanguage".equals(name) && !value.isEmpty()) {
                    config.setConfigurationProperty(FeatureKeys.DEFAULT_LANGUAGE, value);
                }
                if ("defaultCountry".equals(name) && !value.isEmpty()) {
                    config.setConfigurationProperty(FeatureKeys.DEFAULT_COUNTRY, value);
                }
            }
        }
    }

    private void readLocalization(Attributes atts) {
        String lang = null;
        Properties properties = new Properties();
        for (int i = 0; i < atts.getLength(); i++) {
            if (atts.getURI(i).isEmpty()) {
                String name = atts.getLocalName(i);
                String value = atts.getValue(i);
                if ("lang".equals(name) && !value.isEmpty()) {
                    lang = value;
                } else if (!value.isEmpty()) {
                    properties.setProperty(name, value);
                }
            }
        }
        if (lang != null) {
            LocalizerFactory factory = config.getLocalizerFactory();
            if (factory != null) {
                factory.setLanguageProperties(lang, properties);
            }
        }
    }

    private void readFileExtension(Attributes atts) {
        String extension = atts.getValue("", "extension");
        String mediaType = atts.getValue("", "mediaType");
        if (extension == null) {
            error("fileExtension", "extension", null, null);
        }
        if (mediaType == null) {
            error("fileExtension", "mediaType", null, null);
        }
        config.registerFileExtension(extension, mediaType);
    }

    /**
     * Process details of XSLT extension elements. Overridden in Saxon-PE configuration reader
     *
     * @param atts the attributes of the extensionElement element in the configuration file
     */

    protected void readExtensionElement(Attributes atts) {
        XmlProcessingIncident err = new XmlProcessingIncident("Extension elements are not available in Saxon-HE");
        err.setLocation(Loc.makeFromSax(locator));
        errors.add(err);
    }

    protected void readXsltPackage(Attributes atts) {
        String name = atts.getValue("name");
        if (name == null) {
            String attName = "exportLocation";
            String location = atts.getValue("exportLocation");
            URI uri = null;
            if (location == null) {
                attName = "sourceLocation";
                location = atts.getValue("sourceLocation");
            }
            if (location == null) {
                error("package", attName, null, null);
            }
            try {
                uri = ResolveURI.makeAbsolute(location, locator.getSystemId());
            } catch (URISyntaxException e) {
                error("package", attName, location, "Requires a valid URI.");
            }
            File file = new File(uri);
            try {
                packageLibrary.addPackage(file);
            } catch (XPathException e) {
                error(e);
            }
        } else {
            String version = atts.getValue("version");
            if (version == null) {
                version = "1";
            }
            VersionedPackageName vpn = null;
            PackageDetails details = new PackageDetails();
            try {
                vpn = new VersionedPackageName(name, version);
            } catch (XPathException err) {
                error("package", "version", version, null);
            }
            details.nameAndVersion = vpn;
            currentPackage = details;
            String sourceLoc = atts.getValue("sourceLocation");
            StreamSource source = null;
            if (sourceLoc != null) {
                try {
                    source = new StreamSource(
                            ResolveURI.makeAbsolute(sourceLoc, locator.getSystemId()).toString());
                } catch (URISyntaxException e) {
                    error("package", "sourceLocation", sourceLoc, "Requires a valid URI.");
                }
                details.sourceLocation = source;
            }
            String exportLoc = atts.getValue("exportLocation");
            if (exportLoc != null) {
                try {
                    source = new StreamSource(
                            ResolveURI.makeAbsolute(exportLoc, locator.getSystemId()).toString());
                } catch (URISyntaxException e) {
                    error("package", "exportLocation", exportLoc, "Requires a valid URI.");
                }
                details.exportLocation = source;
            }
            String priority = atts.getValue("priority");
            if (priority != null) {
                try {
                    details.priority = Integer.parseInt(priority);
                } catch (NumberFormatException err) {
                    error("package", "priority", priority, "Requires an integer.");
                }
            }
            details.baseName = atts.getValue("base");
            details.shortName = atts.getValue("shortName");

            packageLibrary.addPackage(details);
        }
    }

    protected void readWithParam(Attributes atts) {
        if (currentPackage.exportLocation != null) {
            error("withParam", null, null, "Not allowed when @exportLocation exists");
        }
        String name = atts.getValue("name");
        if (name == null) {
            error("withParam", "name", null, null);
        }
        QNameParser qp = new QNameParser(this).withAcceptEQName(true);
        StructuredQName qName = null;
        try {
            qName = qp.parse(name, "");
        } catch (XPathException e) {
            error("withParam", "name", name, "Requires valid QName");
        }
        String select = atts.getValue("select");
        if (select == null) {
            error("withParam", "select", null, null);
        }
        IndependentContext env = new IndependentContext(config);
        env.setNamespaceResolver(this);
        XPathParser parser = new XPathParser();
        GroundedValue value = null;
        try {
            Expression exp = parser.parse(select, 0, Token.EOF, env);
            value = exp.iterate(env.makeEarlyEvaluationContext()).materialize();
        } catch (XPathException e) {
            error(e);
        }
        if (currentPackage.staticParams == null) {
            currentPackage.staticParams = new HashMap<>();
        }
        currentPackage.staticParams.put(qName, value);
    }


    private void readXQueryElement(Attributes atts) {
        Properties props = new Properties();
        for (int i = 0; i < atts.getLength(); i++) {
            String name = atts.getLocalName(i);
            String value = atts.getValue(i);
            if (!value.isEmpty() && atts.getURI(i).isEmpty()) {
                props.put(name, value);
            }
        }
        props.put("#element", "xquery");
        applyProperty(props, "allowUpdate", Feature.XQUERY_ALLOW_UPDATE);
        applyProperty(props, "constructionMode", Feature.XQUERY_CONSTRUCTION_MODE);
        applyProperty(props, "defaultElementNamespace", Feature.XQUERY_DEFAULT_ELEMENT_NAMESPACE);
        applyProperty(props, "defaultFunctionNamespace", Feature.XQUERY_DEFAULT_FUNCTION_NAMESPACE);
        applyProperty(props, "emptyLeast", Feature.XQUERY_EMPTY_LEAST);
        applyProperty(props, "inheritNamespaces", Feature.XQUERY_INHERIT_NAMESPACES);
        applyProperty(props, "moduleUriResolver", Feature.MODULE_URI_RESOLVER_CLASS);
        applyProperty(props, "preserveBoundarySpace", Feature.XQUERY_PRESERVE_BOUNDARY_SPACE);
        applyProperty(props, "preserveNamespaces", Feature.XQUERY_PRESERVE_NAMESPACES);
        applyProperty(props, "requiredContextItemType", Feature.XQUERY_REQUIRED_CONTEXT_ITEM_TYPE);
        applyProperty(props, "schemaAware", Feature.XQUERY_SCHEMA_AWARE);
        applyProperty(props, "staticErrorListener", Feature.XQUERY_STATIC_ERROR_LISTENER_CLASS);
        applyProperty(props, "version", Feature.XQUERY_VERSION);
    }

    private void readXsltElement(Attributes atts) {
        Properties props = new Properties();
        for (int i = 0; i < atts.getLength(); i++) {
            String name = atts.getLocalName(i);
            String value = atts.getValue(i);
            if (!value.isEmpty() && atts.getURI(i).isEmpty()) {
                props.put(name, value);
            }
        }
        props.put("#element", "xslt");
        applyProperty(props, "disableXslEvaluate", Feature.DISABLE_XSL_EVALUATE);
        applyProperty(props, "enableAssertions", Feature.XSLT_ENABLE_ASSERTIONS);
        applyProperty(props, "initialMode", Feature.XSLT_INITIAL_MODE);
        applyProperty(props, "initialTemplate", Feature.XSLT_INITIAL_TEMPLATE);
        applyProperty(props, "messageEmitter", Feature.MESSAGE_EMITTER_CLASS);
        applyProperty(props, "outputUriResolver", Feature.OUTPUT_URI_RESOLVER_CLASS);
        applyProperty(props, "recoveryPolicy", Feature.RECOVERY_POLICY_NAME);
        applyProperty(props, "resultDocumentThreads", Feature.RESULT_DOCUMENT_THREADS);
        applyProperty(props, "schemaAware", Feature.XSLT_SCHEMA_AWARE);
        applyProperty(props, "staticErrorListener", Feature.XSLT_STATIC_ERROR_LISTENER_CLASS);
        applyProperty(props, "staticUriResolver", Feature.XSLT_STATIC_URI_RESOLVER_CLASS);
        applyProperty(props, "strictStreamability", Feature.STRICT_STREAMABILITY);
        applyProperty(props, "styleParser", Feature.STYLE_PARSER_CLASS);
        applyProperty(props, "version", Feature.XSLT_VERSION);
        //applyProperty(props, "versionWarning", FeatureKeys.VERSION_WARNING);
    }

    private void readXsdElement(Attributes atts) {
        Properties props = new Properties();
        for (int i = 0; i < atts.getLength(); i++) {
            String name = atts.getLocalName(i);
            String value = atts.getValue(i);
            if (!value.isEmpty() && atts.getURI(i).isEmpty()) {
                props.put(name, value);
            }
        }
        props.put("#element", "xsd");
        applyProperty(props, "assertionsCanSeeComments", Feature.ASSERTIONS_CAN_SEE_COMMENTS);
        applyProperty(props, "implicitSchemaImports", Feature.IMPLICIT_SCHEMA_IMPORTS);
        applyProperty(props, "multipleSchemaImports", Feature.MULTIPLE_SCHEMA_IMPORTS);
        applyProperty(props, "occurrenceLimits", Feature.OCCURRENCE_LIMITS);
        applyProperty(props, "schemaUriResolver", Feature.SCHEMA_URI_RESOLVER_CLASS);
        applyProperty(props, "thresholdForCompilingTypes", Feature.THRESHOLD_FOR_COMPILING_TYPES);
        applyProperty(props, "useXsiSchemaLocation", Feature.USE_XSI_SCHEMA_LOCATION);
        applyProperty(props, "version", Feature.XSD_VERSION);
    }

    private void error(String element, String attribute, String actual, String required) {
        XmlProcessingIncident err;
        if (attribute == null) {
            err = new XmlProcessingIncident("Invalid configuration element " + element);
        } else if (actual == null) {
            err = new XmlProcessingIncident("Missing configuration property " +
                                             element + "/@" + attribute);
        } else {
            err = new XmlProcessingIncident("Invalid configuration property " +
                                             element + "/@" + attribute + ". Supplied value '" + actual + "'. " + required);
        }
        err.setLocation(Loc.makeFromSax(locator));
        errors.add(err);
    }

    protected void error(XPathException err) {
        err.setLocator(Loc.makeFromSax(locator));
        errors.add(new XmlProcessingException(err));
    }

    protected void errorClass(String element, String attribute, String actual, Class required, Exception cause) {
        XmlProcessingIncident err = new XmlProcessingIncident("Invalid configuration property " +
                                                        element + (attribute == null ? "" : "/@" + attribute) +
                                                        ". Supplied value '" + actual +
                                                        "', required value is the name of a class that implements '" + required.getName() + "'");
        err.setCause(cause);
        err.setLocation(Loc.makeFromSax(locator));
        errors.add(err);
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        if (level == 3 && "resources".equals(section)) {
            String content = buffer.toString();
            if (!content.isEmpty()) {
                if ("externalObjectModel".equals(localName)) {
                    try {
                        ExternalObjectModel model = (ExternalObjectModel) config.getInstance(content, null);
                        config.registerExternalObjectModel(model);
                    } catch (XPathException | ClassCastException e) {
                        errorClass("externalObjectModel", null, content, ExternalObjectModel.class, e);
                    }
                } else if ("extensionFunction".equals(localName)) {
                    try {
                        ExtensionFunctionDefinition model = (ExtensionFunctionDefinition) config.getInstance(content, null);
                        config.registerExtensionFunction(model);
                    } catch (XPathException | ClassCastException | IllegalArgumentException e) {
                        errorClass("extensionFunction", null, content, ExtensionFunctionDefinition.class, e);
                    }
                } else if ("schemaDocument".equals(localName)) {
                    try {
                        Source source = getInputSource(content);
                        config.addSchemaSource(source);
                    } catch (XPathException e) {
                        errors.add(new XmlProcessingException(e));
                    }
                } else if ("schemaComponentModel".equals(localName)) {
                    try {
                        Source source = getInputSource(content);
                        config.importComponents(source);
                    } catch (XPathException e) {
                        errors.add(new XmlProcessingException(e));
                    }
                } else if ("fileExtension".equals(localName)) {
                    // already done at startElement time
                } else {
                    error(localName, null, null, null);
                }
            }
        }
        level--;
        buffer.setLength(0);
        namespaceStack.pop();
    }

    private Source getInputSource(String href) throws XPathException {
        try {
            String base = locator.getSystemId();
            URI abs = ResolveURI.makeAbsolute(href, base);
            return new StreamSource(abs.toString());
        } catch (URISyntaxException e) {
            throw new XPathException(e);
        }
    }

    @Override
    public void characters(char ch[], int start, int length) {
        buffer.append(ch, start, length);
    }

    @Override
    public void ignorableWhitespace(char ch[], int start, int length) {

    }

    @Override
    public void processingInstruction(String target, String data) {

    }

    @Override
    public void skippedEntity(String name) {

    }

    /////////////////////////////////////////////////////
    // Implement NamespaceResolver
    /////////////////////////////////////////////////////

    /**
     * Get the namespace URI corresponding to a given prefix. Return null
     * if the prefix is not in scope.
     *
     * @param prefix     the namespace prefix. May be the zero-length string, indicating
     *                   that there is no prefix. This indicates either the default namespace or the
     *                   null namespace, depending on the value of useDefault.
     * @param useDefault true if the default namespace is to be used when the
     *                   prefix is "". If false, the method returns "" when the prefix is "".
     * @return the uri for the namespace, or null if the prefix is not in scope.
     * The "null namespace" is represented by the pseudo-URI "".
     */

    @Override
    public String getURIForPrefix(String prefix, boolean useDefault) {
        for (int i = namespaceStack.size() - 1; i >= 0; i--) {
            List<String[]> list = namespaceStack.get(i);
            for (String[] pair : list) {
                if (pair[0].equals(prefix)) {
                    return pair[1];
                }
            }
        }
        return null;
    }

    /**
     * Get an iterator over all the prefixes declared in this namespace context. This will include
     * the default namespace (prefix="") and the XML namespace where appropriate
     */

    @Override
    public Iterator<String> iteratePrefixes() {
        Set<String> prefixes = new HashSet<>();
        for (List<String[]> list : namespaceStack) {
            for (String[] pair : list) {
                prefixes.add(pair[0]);
            }
        }
        return prefixes.iterator();
    }
}

