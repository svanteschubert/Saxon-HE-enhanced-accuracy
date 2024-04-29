////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;


import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.ResultDocument;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.expr.parser.XPathParser;
import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.s9api.XmlProcessingError;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.sxpath.IndependentContext;
import net.sf.saxon.trans.packages.PackageDetails;
import net.sf.saxon.trans.packages.PackageLibrary;
import net.sf.saxon.trans.packages.VersionedPackageName;
import net.sf.saxon.transpile.CSharp;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.type.SchemaType;
import org.xmlresolver.ResolverFeature;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Class used to read a config.xml file and transfer all settings from the file to the Configuration
 */

public class ConfigurationReader implements Receiver {

    private int level = 0;
    private String section = null;
    private String subsection = null;
    private final StringBuilder buffer = new StringBuilder(100);
    protected Configuration targetConfig;
    private ClassLoader classLoader = null;
    private final List<XmlProcessingError> errors = new ArrayList<>();
    private PackageLibrary packageLibrary;
    private PackageDetails currentPackage;
    private Configuration baseConfiguration;
    private PipelineConfiguration pipe;
    private final Stack<String> localNameStack = new Stack<>();
    private String systemId = null;
    private ArrayList<String> catalogFiles = null;

    private final static int OBSOLETE_PROPERTY = -9999;

    public ConfigurationReader() {
    }

    @Override
    public void setPipelineConfiguration(PipelineConfiguration pipe) {
        this.pipe = pipe;
    }

    @Override
    public PipelineConfiguration getPipelineConfiguration() {
        return pipe;
    }

    @Override
    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    @Override
    public void open() throws XPathException {

    }

    @Override
    public void startDocument(int properties) throws XPathException {

    }

    @Override
    public void setUnparsedEntity(String name, String systemID, String publicID) throws XPathException {

    }

    @Override
    public void processingInstruction(String name, UnicodeString data, Location location, int properties) throws XPathException {

    }

    @Override
    public void comment(UnicodeString content, Location location, int properties) throws XPathException {

    }

    @Override
    public void close() throws XPathException {

    }

    @Override
    public String getSystemId() {
        return systemId;
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
        Configuration localConfig = baseConfiguration;
        if (localConfig == null) {
            if (pipe == null) {
                localConfig = new Configuration();
                setPipelineConfiguration(localConfig.makePipelineConfiguration());
            } else {
                localConfig = pipe.getConfiguration();
            }
        } else {
            if (pipe == null) {
                pipe = localConfig.makePipelineConfiguration();
            }
        }
        setSystemId(source.getSystemId());
        ActiveSource activeSource = Version.platform.resolveSource(source, localConfig);
        activeSource.deliver(this, new ParseOptions());
        // TODO: set an error handler
        // TODO: set location information


        if (!errors.isEmpty()) {
            ErrorReporter reporter;
            XmlProcessingError foundFatal = null;
            if (targetConfig == null) {
                reporter = new StandardErrorReporter();
            } else {
                reporter = targetConfig.makeErrorReporter();
            }
            for (XmlProcessingError err : errors) {
                if (!err.isWarning()) {
                    if (foundFatal == null) {
                        foundFatal = err;
                    }
                }
                reporter.report(err.asWarning());
            }
            if (foundFatal != null) {
                throw XPathException.fromXmlProcessingError(foundFatal);
            }
        }
        if (baseConfiguration != null) {
            targetConfig.importLicenseDetails(baseConfiguration);
        }
        return targetConfig;
    }

    @Override
    public void endDocument() {
        if (targetConfig != null) {
            targetConfig.getDefaultXsltCompilerInfo().setPackageLibrary(packageLibrary);
        }
    }

    @Override
    public void startElement(NodeName elemName, SchemaType type, AttributeMap atts,
                             NamespaceMap namespaces, Location location, int properties) throws XPathException {
        NamespaceUri uri = elemName.getNamespaceUri();
        String localName = elemName.getLocalPart();
        localNameStack.push(localName);
        buffer.setLength(0);
        if (NamespaceUri.SAXON_CONFIGURATION.equals(uri)) {
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
                        targetConfig = new Configuration();
                        break;
                    case "PE":
                        targetConfig = Configuration.makeLicensedConfiguration(classLoader, "com.saxonica.config.ProfessionalConfiguration");
                        break;
                    case "EE":
                        targetConfig = Configuration.makeLicensedConfiguration(classLoader, "com.saxonica.config.EnterpriseConfiguration");
                        break;
                    default:
                        error("configuration", "edition", edition, "HE|PE|EE");
                        targetConfig = new Configuration();
                        break;
                }

                if (baseConfiguration != null) {
                    targetConfig.setNamePool(baseConfiguration.getNamePool());
                    targetConfig.setDocumentNumberAllocator(baseConfiguration.getDocumentNumberAllocator());
                }

                packageLibrary = new PackageLibrary(targetConfig.getDefaultXsltCompilerInfo());
                String licenseLoc = atts.getValue("licenseFileLocation");
                if (licenseLoc != null && !edition.equals("HE")) {
                    String base = getSystemId();
                    try {
                        URI absoluteLoc = ResolveURI.makeAbsolute(licenseLoc, base);
                        targetConfig.setConfigurationProperty(FeatureKeys.LICENSE_FILE_LOCATION, absoluteLoc.toString());
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
                    targetConfig.setLabel(label);
                }
                targetConfig.getDynamicLoader().setClassLoader(classLoader);
            }
            if (level == 1) {
                section = localName;
                if ("global".equals(localName)) {
                    readGlobalElement(atts);
                } else if ("serialization".equals(localName)) {
                    readSerializationElement(atts, namespaces);
                } else if ("xquery".equals(localName)) {
                    readXQueryElement(atts);
                } else if ("xslt".equals(localName)) {
                    readXsltElement(atts);
                } else if ("xsltPackages".equals(localName)) {
                    // no action until later;
                } else if ("xsd".equals(localName)) {
                    readXsdElement(atts);
                } else if ("resources".equals(localName)) {
                    // Initialize the list of catalog files
                    catalogFiles = new ArrayList<>();
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
                        readWithParam(atts, namespaces);
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
    }

    private void readGlobalElement(AttributeMap atts) {
        Properties props = new Properties();
        for (AttributeInfo a : atts) {
            String name = a.getNodeName().getLocalPart();
            String value = a.getValue();
            if (!value.isEmpty() && a.getNodeName().getNamespaceUri().isEmpty()) {
                props.setProperty(name, value);
            }
        }
        props.setProperty("#element", "global");
        applyProperty(props, "allowedProtocols", FeatureCode.ALLOWED_PROTOCOLS, "JN");
        applyProperty(props, "allowExternalFunctions", FeatureCode.ALLOW_EXTERNAL_FUNCTIONS, "J");
        applyProperty(props, "allowMultiThreading", FeatureCode.ALLOW_MULTITHREADING, "JN");
        applyProperty(props, "allowOldJavaUriFormat", FeatureCode.ALLOW_OLD_JAVA_URI_FORMAT, "J");
        applyProperty(props, "allowSyntaxExtensions", FeatureCode.ALLOW_SYNTAX_EXTENSIONS, "JN");
        applyProperty(props, "collationUriResolver", FeatureCode.COLLATION_URI_RESOLVER_CLASS, "J");
        applyProperty(props, "collectionFinder", FeatureCode.COLLECTION_FINDER_CLASS, "J");
        applyProperty(props, "compileWithTracing", FeatureCode.COMPILE_WITH_TRACING, "JN");
        applyProperty(props, "debugByteCode", OBSOLETE_PROPERTY, "J");
        applyProperty(props, "debugByteCodeDirectory", OBSOLETE_PROPERTY, "J");
        applyProperty(props, "defaultCollation", FeatureCode.DEFAULT_COLLATION, "JN");
        applyProperty(props, "defaultCollection", FeatureCode.DEFAULT_COLLECTION, "JN");
        applyProperty(props, "defaultRegexEngine", FeatureCode.DEFAULT_REGEX_ENGINE, "J");
        applyProperty(props, "displayByteCode", OBSOLETE_PROPERTY, "J");
        applyProperty(props, "dtdValidation", FeatureCode.DTD_VALIDATION, "JN");
        applyProperty(props, "dtdValidationRecoverable", FeatureCode.DTD_VALIDATION_RECOVERABLE, "J");
        applyProperty(props, "eagerEvaluation", FeatureCode.EAGER_EVALUATION, "JN");
        applyProperty(props, "entityResolver", FeatureCode.ENTITY_RESOLVER_CLASS, "J");
        applyProperty(props, "environmentVariableResolver", FeatureCode.ENVIRONMENT_VARIABLE_RESOLVER_CLASS, "J");
        applyProperty(props, "errorListener", FeatureCode.ERROR_LISTENER_CLASS, "J");
        applyProperty(props, "expandAttributeDefaults", FeatureCode.EXPAND_ATTRIBUTE_DEFAULTS, "JN");
        applyProperty(props, "generateByteCode", OBSOLETE_PROPERTY, "J");
        applyProperty(props, "ignoreSAXSourceParser", FeatureCode.IGNORE_SAX_SOURCE_PARSER, "J");
        applyProperty(props, "lazyConstructionMode", OBSOLETE_PROPERTY, "JN");
        applyProperty(props, "lineNumbering", FeatureCode.LINE_NUMBERING, "JN");
        applyProperty(props, "markDefaultedAttributes", FeatureCode.MARK_DEFAULTED_ATTRIBUTES, "J");
        applyProperty(props, "maxCompiledClasses", OBSOLETE_PROPERTY, "J");
        applyProperty(props, "monitorHotSpotByteCode", OBSOLETE_PROPERTY, "J");
        applyProperty(props, "optimizationLevel", FeatureCode.OPTIMIZATION_LEVEL, "JN");
        applyProperty(props, "parser", FeatureCode.SOURCE_PARSER_CLASS, "J");
        applyProperty(props, "preEvaluateDoc", FeatureCode.PRE_EVALUATE_DOC_FUNCTION, "JN");
        applyProperty(props, "recognizeUriQueryParameters", FeatureCode.RECOGNIZE_URI_QUERY_PARAMETERS, "JN");
        applyProperty(props, "regexBacktrackingLimit", FeatureCode.REGEX_BACKTRACKING_LIMIT, "JN");
        applyProperty(props, "resourceResolver", FeatureCode.RESOURCE_RESOLVER_CLASS, "J");
        applyProperty(props, "retainNodeForDiagnostics", FeatureCode.RETAIN_NODE_FOR_DIAGNOSTICS, "JN");
        applyProperty(props, "schemaValidation", FeatureCode.SCHEMA_VALIDATION_MODE, "JN");
        applyProperty(props, "serializerFactory", FeatureCode.SERIALIZER_FACTORY_CLASS, "J");
        applyProperty(props, "sourceResolver", FeatureCode.SOURCE_RESOLVER_CLASS, "J");
        applyProperty(props, "stableCollectionUri", FeatureCode.STABLE_COLLECTION_URI, "JN");
        applyProperty(props, "stableUnparsedText", FeatureCode.STABLE_UNPARSED_TEXT, "JN");
        applyProperty(props, "standardErrorOutputFile", FeatureCode.STANDARD_ERROR_OUTPUT_FILE, "JN");
        applyProperty(props, "streamability", FeatureCode.STREAMABILITY, "JN");
        applyProperty(props, "streamingFallback", FeatureCode.STREAMING_FALLBACK, "JN");
        applyProperty(props, "stripSpace", FeatureCode.STRIP_WHITESPACE, "JN");
        applyProperty(props, "styleParser", FeatureCode.STYLE_PARSER_CLASS, "J");
        applyProperty(props, "suppressEvaluationExpiryWarning", FeatureCode.SUPPRESS_EVALUATION_EXPIRY_WARNING, "JN");
        applyProperty(props, "suppressXPathWarnings", FeatureCode.SUPPRESS_XPATH_WARNINGS, "JN");
        applyProperty(props, "suppressXsltNamespaceCheck", FeatureCode.SUPPRESS_XSLT_NAMESPACE_CHECK, "JN");
        applyProperty(props, "thresholdForFunctionInlining", FeatureCode.THRESHOLD_FOR_FUNCTION_INLINING, "JN");
        applyProperty(props, "thresholdForHotspotByteCode", OBSOLETE_PROPERTY, "J");
        applyProperty(props, "timing", FeatureCode.TIMING, "JN");
        applyProperty(props, "traceExternalFunctions", FeatureCode.TRACE_EXTERNAL_FUNCTIONS, "JN");
        applyProperty(props, "traceListener", FeatureCode.TRACE_LISTENER_CLASS, "J");
        applyProperty(props, "traceListenerOutputFile", FeatureCode.TRACE_LISTENER_OUTPUT_FILE, "JN");
        applyProperty(props, "traceOptimizerDecisions", FeatureCode.TRACE_OPTIMIZER_DECISIONS, "JN");
        applyProperty(props, "treeModel", FeatureCode.TREE_MODEL_NAME, "JN");
        // Two spellings accepted: see bug #6201. The correct one (according to the schema) is "unparsedTextURIResolver"
        applyProperty(props, "unparsedTextUriResolver", FeatureCode.UNPARSED_TEXT_URI_RESOLVER_CLASS, "J");
        applyProperty(props, "unparsedTextURIResolver", FeatureCode.UNPARSED_TEXT_URI_RESOLVER_CLASS, "J");
        applyProperty(props, "uriResolver", FeatureCode.URI_RESOLVER_CLASS, "J");
        applyProperty(props, "usePiDisableOutputEscaping", FeatureCode.USE_PI_DISABLE_OUTPUT_ESCAPING, "JN");
        applyProperty(props, "useTypedValueCache", FeatureCode.USE_TYPED_VALUE_CACHE, "JN");
        applyProperty(props, "validationComments", FeatureCode.VALIDATION_COMMENTS, "JN");
        applyProperty(props, "validationWarnings", FeatureCode.VALIDATION_WARNINGS, "JN");
        applyProperty(props, "versionOfXml", FeatureCode.XML_VERSION, "J");
        applyProperty(props, "xInclude", FeatureCode.XINCLUDE, "J");
        applyProperty(props, "xpathVersionForXsd", FeatureCode.XPATH_VERSION_FOR_XSD, "JN");
        applyProperty(props, "xpathVersionForXslt", FeatureCode.XPATH_VERSION_FOR_XSLT, "JN");
        applyProperty(props, "zipUriPattern", FeatureCode.ZIP_URI_PATTERN, "JN");

        for (String name : props.stringPropertyNames()) {
            if (!name.equals("#element")) {
                error("global", name, props.getProperty(name), "#unrecognized");
            }
        }
    }

    private void applyProperty(Properties props, String attributeName, int featureCode, String flags) {
        String value = props.getProperty(attributeName);
        if (value != null) {
            if (featureCode == OBSOLETE_PROPERTY) {
                error(props.getProperty("#element"), attributeName, value, "#obsolete");
                props.remove(attributeName);
                return;
            }
            if (!checkPlatform(flags)) {
                error(props.getProperty("#element"), attributeName, value, "Property " + attributeName + " is not available in SaxonCS");
                return;
            }
            try {
                targetConfig.setConfigurationProperty(FeatureIndex.getData(featureCode).uri, value);
                props.remove(attributeName);
            } catch (IllegalArgumentException e) {
                String message = e.getMessage();
                if (message.startsWith(attributeName)) {
                    message = message.replace(attributeName, "Value");
                }
                if (message.startsWith("Unknown configuration property")) {
                    message = "Property " + attributeName + " is not available in Saxon-" + targetConfig.getEditionCode();
                }
                error(props.getProperty("#element"), attributeName, value, message);
            }
        }
    }

    @CSharpReplaceBody(code="return flags.Contains(\"N\");")
    private boolean checkPlatform(String flags) {
        return flags.contains("J");
    }

    private void readSerializationElement(AttributeMap atts, NamespaceMap nsMap) {
        Properties props = new Properties();
        for (AttributeInfo a : atts) {
            NamespaceUri uri = a.getNodeName().getNamespaceUri();
            String name = a.getNodeName().getLocalPart();
            String value = a.getValue();
            if (value.isEmpty()) {
                continue;
            }
            try {
                ResultDocument.setSerializationProperty(props,
                                                        uri, name, value, nsMap, false, targetConfig);
            } catch (XPathException e) {
                errors.add(new XmlProcessingException(e));
            }
        }
        targetConfig.setDefaultSerializationProperties(props);
    }

    private void readCollation(AttributeMap atts) {
        Properties props = new Properties();
        String collationUri = null;
        for (AttributeInfo a : atts) {
            NamespaceUri uri = a.getNodeName().getNamespaceUri();
            String name = a.getNodeName().getLocalPart();
            String value = a.getValue();
            if (uri.isEmpty()) {
                if (value.isEmpty()) {
                    continue;
                }
                if ("uri".equals(name)) {
                    collationUri = value;
                } else {
                    props.setProperty(name, value);
                }
            }
        }
        if (collationUri == null) {
            errors.add(new XmlProcessingIncident("collation specified with no uri"));
        }
        StringCollator collator = null;
        try {
            collator = Version.platform.makeCollation(targetConfig, props, collationUri);
        } catch (XPathException e) {
            errors.add(new XmlProcessingIncident(e.getMessage()));
        }
        targetConfig.registerCollation(collationUri, collator);

    }

    private void readLocalizationsElement(AttributeMap atts) {
        for (AttributeInfo a : atts) {
            NamespaceUri uri = a.getNodeName().getNamespaceUri();
            String name = a.getNodeName().getLocalPart();
            String value = a.getValue();
            if (uri.isEmpty()) {
                if ("defaultLanguage".equals(name) && !value.isEmpty()) {
                    targetConfig.setConfigurationProperty(FeatureKeys.DEFAULT_LANGUAGE, value);
                }
                if ("defaultCountry".equals(name) && !value.isEmpty()) {
                    targetConfig.setConfigurationProperty(FeatureKeys.DEFAULT_COUNTRY, value);
                }
            }
        }
    }

    private void readLocalization(AttributeMap atts) {
        String lang = null;
        Properties properties = new Properties();
        for (AttributeInfo a : atts) {
            NamespaceUri uri = a.getNodeName().getNamespaceUri();
            String name = a.getNodeName().getLocalPart();
            String value = a.getValue();
            if (uri.isEmpty()) {
                if ("lang".equals(name) && !value.isEmpty()) {
                    lang = value;
                } else if (!value.isEmpty()) {
                    properties.setProperty(name, value);
                }
            }
        }
        if (lang != null) {
            LocalizerFactory factory = targetConfig.getLocalizerFactory();
            if (factory != null) {
                factory.setLanguageProperties(lang, properties);
            }
        }
    }

    private void readFileExtension(AttributeMap atts) {
        String extension = atts.getValue("extension");
        String mediaType = atts.getValue("mediaType");
        if (extension == null) {
            error("fileExtension", "extension", null, null);
        }
        if (mediaType == null) {
            error("fileExtension", "mediaType", null, null);
        }
        targetConfig.registerFileExtension(extension, mediaType);
    }

    /**
     * Process details of XSLT extension elements. Overridden in Saxon-PE configuration reader
     *
     * @param atts the attributes of the extensionElement element in the configuration file
     */

    protected void readExtensionElement(AttributeMap atts) {
        XmlProcessingIncident err = new XmlProcessingIncident(
                "Extension elements are not available in Saxon" +
                Version.getSoftwarePlatform() + "-" + targetConfig.getEditionCode());
        //err.setLocation(Loc.makeFromSax(locator));     // TODO: reinstate location info for diagnostics
        errors.add(err);
    }

    protected void readXsltPackage(AttributeMap atts) {
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
                uri = ResolveURI.makeAbsolute(location, getSystemId());
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
                            ResolveURI.makeAbsolute(sourceLoc, getSystemId()).toString());
                } catch (URISyntaxException e) {
                    error("package", "sourceLocation", sourceLoc, "Requires a valid URI.");
                }
                details.sourceLocation = source;
            }
            String exportLoc = atts.getValue("exportLocation");
            if (exportLoc != null) {
                try {
                    source = new StreamSource(
                            ResolveURI.makeAbsolute(exportLoc, getSystemId()).toString());
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

    protected void readWithParam(AttributeMap atts, NamespaceMap nsMap) {
        if (currentPackage.exportLocation != null) {
            error("withParam", null, null, "Not allowed when @exportLocation exists");
        }
        String name = atts.getValue("name");
        if (name == null) {
            error("withParam", "name", null, null);
        }
        QNameParser qp = new QNameParser(nsMap).withAcceptEQName(true);
        StructuredQName qName = null;
        try {
            qName = qp.parse(name, NamespaceUri.NULL);
        } catch (XPathException e) {
            error("withParam", "name", name, "Requires valid QName");
        }
        String select = atts.getValue("select");
        if (select == null) {
            error("withParam", "select", null, null);
        }
        IndependentContext env = new IndependentContext(targetConfig);
        env.setNamespaceResolver(nsMap);
        XPathParser parser = new XPathParser(env);
        GroundedValue value = null;
        try {
            Expression exp = parser.parse(select, 0, Token.EOF, env);
            value = SequenceTool.toGroundedValue(exp.iterate(env.makeEarlyEvaluationContext()));
        } catch (XPathException e) {
            error(e);
        } catch (UncheckedXPathException e) {
            error(e.getXPathException());
        }
        if (currentPackage.staticParams == null) {
            currentPackage.staticParams = new HashMap<>();
        }
        currentPackage.staticParams.put(qName, value);
    }


    private void readXQueryElement(AttributeMap atts) {
        Properties props = new Properties();
        for (AttributeInfo a : atts) {
            NamespaceUri uri = a.getNodeName().getNamespaceUri();
            String name = a.getNodeName().getLocalPart();
            String value = a.getValue();
            if (!value.isEmpty() && uri.isEmpty()) {
                props.setProperty(name, value);
            }
        }
        props.setProperty("#element", "xquery");
        applyProperty(props, "allowUpdate", FeatureCode.XQUERY_ALLOW_UPDATE, "JN");
        applyProperty(props, "constructionMode", FeatureCode.XQUERY_CONSTRUCTION_MODE, "JN");
        applyProperty(props, "defaultElementNamespace", FeatureCode.XQUERY_DEFAULT_ELEMENT_NAMESPACE, "JN");
        applyProperty(props, "defaultFunctionNamespace", FeatureCode.XQUERY_DEFAULT_FUNCTION_NAMESPACE, "JN");
        applyProperty(props, "emptyLeast", FeatureCode.XQUERY_EMPTY_LEAST, "JN");
        applyProperty(props, "inheritNamespaces", FeatureCode.XQUERY_INHERIT_NAMESPACES, "JN");
        applyProperty(props, "moduleUriResolver", FeatureCode.MODULE_URI_RESOLVER_CLASS, "J");
        applyProperty(props, "multipleModuleImports", FeatureCode.XQUERY_MULTIPLE_MODULE_IMPORTS, "JN");
        applyProperty(props, "preserveBoundarySpace", FeatureCode.XQUERY_PRESERVE_BOUNDARY_SPACE, "JN");
        applyProperty(props, "preserveNamespaces", FeatureCode.XQUERY_PRESERVE_NAMESPACES, "JN");
        applyProperty(props, "requiredContextItemType", FeatureCode.XQUERY_REQUIRED_CONTEXT_ITEM_TYPE, "JN");
        applyProperty(props, "schemaAware", FeatureCode.XQUERY_SCHEMA_AWARE, "JN");
        applyProperty(props, "staticErrorListener", FeatureCode.XQUERY_STATIC_ERROR_LISTENER_CLASS, "J");
        applyProperty(props, "version", FeatureCode.XQUERY_VERSION, "JN");

        for (String name : props.stringPropertyNames()) {
            if (!name.equals("#element")) {
                error("xquery", name, props.getProperty(name), "#unrecognized");
            }
        }
    }

    private void readXsltElement(AttributeMap atts) {
        Properties props = new Properties();
        for (AttributeInfo a : atts) {
            NamespaceUri uri = a.getNodeName().getNamespaceUri();
            String name = a.getNodeName().getLocalPart();
            String value = a.getValue();
            if (!value.isEmpty() && uri.isEmpty()) {
                props.setProperty(name, value);
            }
        }
        props.setProperty("#element", "xslt");
        applyProperty(props, "disableXslEvaluate", FeatureCode.DISABLE_XSL_EVALUATE, "JN");
        applyProperty(props, "enableAssertions", FeatureCode.XSLT_ENABLE_ASSERTIONS, "JN");
        applyProperty(props, "initialMode", FeatureCode.XSLT_INITIAL_MODE, "JN");
        applyProperty(props, "initialTemplate", FeatureCode.XSLT_INITIAL_TEMPLATE, "JN");
        applyProperty(props, "messageEmitter", OBSOLETE_PROPERTY, "J");
        applyProperty(props, "outputUriResolver", FeatureCode.OUTPUT_URI_RESOLVER_CLASS, "J");
        applyProperty(props, "recoveryPolicy", OBSOLETE_PROPERTY, "JN");
        applyProperty(props, "resultDocumentThreads", FeatureCode.RESULT_DOCUMENT_THREADS, "JN");
        applyProperty(props, "schemaAware", FeatureCode.XSLT_SCHEMA_AWARE, "JN");
        applyProperty(props, "staticErrorListener", FeatureCode.XSLT_STATIC_ERROR_LISTENER_CLASS, "J");
        applyProperty(props, "staticUriResolver", FeatureCode.XSLT_STATIC_URI_RESOLVER_CLASS, "J");
        applyProperty(props, "strictStreamability", FeatureCode.STRICT_STREAMABILITY, "JN");
        applyProperty(props, "styleParser", FeatureCode.STYLE_PARSER_CLASS, "J");
        applyProperty(props, "version", FeatureCode.XSLT_VERSION, "JN");
        applyProperty(props, "versionWarning", OBSOLETE_PROPERTY, "JN");

        for (String name : props.stringPropertyNames()) {
            if (!name.equals("#element")) {
                error("xslt", name, props.getProperty(name), "#unrecognized");
            }
        }
    }

    private void readXsdElement(AttributeMap atts) {
        Properties props = new Properties();
        for (AttributeInfo a : atts) {
            NamespaceUri uri = a.getNodeName().getNamespaceUri();
            String name = a.getNodeName().getLocalPart();
            String value = a.getValue();
            if (!value.isEmpty() && uri.isEmpty()) {
                props.setProperty(name, value);
            }
        }
        props.setProperty("#element", "xsd");
        applyProperty(props, "allowUnresolvedSchemaComponents", FeatureCode.ALLOW_UNRESOLVED_SCHEMA_COMPONENTS, "JN");
        applyProperty(props, "assertionsCanSeeComments", FeatureCode.ASSERTIONS_CAN_SEE_COMMENTS, "JN");
        applyProperty(props, "implicitSchemaImports", FeatureCode.IMPLICIT_SCHEMA_IMPORTS, "JN");
        applyProperty(props, "multipleSchemaImports", FeatureCode.MULTIPLE_SCHEMA_IMPORTS, "JN");
        applyProperty(props, "occurrenceLimits", FeatureCode.OCCURRENCE_LIMITS, "JN");
        applyProperty(props, "schemaUriResolver", FeatureCode.SCHEMA_URI_RESOLVER_CLASS, "J");
        applyProperty(props, "thresholdForCompilingTypes", OBSOLETE_PROPERTY, "JN");
        applyProperty(props, "useXsiSchemaLocation", FeatureCode.USE_XSI_SCHEMA_LOCATION, "JN");
        applyProperty(props, "version", FeatureCode.XSD_VERSION, "JN");

        for (String name : props.stringPropertyNames()) {
            if (!name.equals("#element")) {
                error("xsd", name, props.getProperty(name), "#unrecognized");
            }
        }
    }

    private void error(String element, String attribute, String actual, String required) {
        XmlProcessingIncident err;
        if (attribute == null) {
            err = new XmlProcessingIncident("Invalid configuration element " + element);
        } else if (actual == null) {
            err = new XmlProcessingIncident("Missing configuration property " +
                                             element + "/@" + attribute);
        } else if (required.equals("#unrecognized")) {
            err = new XmlProcessingIncident("Unrecognized configuration property " +
                                                    element + "/@" + attribute);
        } else if (required.equals("#obsolete")) {
            err = new XmlProcessingIncident("Obsolete configuration property " +
                                                    element + "/@" + attribute).asWarning();
        } else if (required.contains("is not available in")) {
            err = new XmlProcessingIncident("Configuration property " +
                                                    element + "/@" + attribute + ": " + required);
        } else {
            err = new XmlProcessingIncident("Invalid configuration property " +
                                             element + "/@" + attribute + ". Supplied value: '" + actual + "'; required: '" + required + "'");
        }
        //err.setLocation(Loc.makeFromSax(locator));
        errors.add(err);
    }

    protected void error(XPathException err) {
        //err.setLocator(Loc.makeFromSax(locator));
        errors.add(new XmlProcessingException(err));
    }

    protected void errorClass(String element, String attribute, String actual, Class required, Exception cause) {
        XmlProcessingIncident err = new XmlProcessingIncident("Invalid configuration property " +
                                                        element + (attribute == null ? "" : "/@" + attribute) +
                                                        ". Supplied value '" + actual +
                                                        "', required value is the name of a class that implements '" + required.getName() + "'");
        err.setCause(cause);
        //err.setLocation(Loc.makeFromSax(locator));
        errors.add(err);
    }

    @Override
    public void endElement() throws XPathException {
        String localName = localNameStack.pop();
        if (level == 3 && "resources".equals(section)) {
            String content = buffer.toString();
            if (!content.isEmpty()) {
                if ("externalObjectModel".equals(localName)) {
                    try {
                        ExternalObjectModel model = (ExternalObjectModel) targetConfig.getInstance(content);
                        targetConfig.registerExternalObjectModel(model);
                    } catch (XPathException | ClassCastException e) {
                        errorClass("externalObjectModel", null, content, ExternalObjectModel.class, e);
                    }
                } else if ("extensionFunction".equals(localName)) {
                    try {
                        ExtensionFunctionDefinition model = (ExtensionFunctionDefinition) targetConfig.getInstance(content);
                        targetConfig.registerExtensionFunction(model);
                    } catch (XPathException | ClassCastException | IllegalArgumentException e) {
                        errorClass("extensionFunction", null, content, ExtensionFunctionDefinition.class, e);
                    }
                } else if ("schemaDocument".equals(localName)) {
                    try {
                        Source source = getInputSource(content);
                        targetConfig.addSchemaSource(source);
                    } catch (XPathException e) {
                        errors.add(new XmlProcessingException(e));
                    }
                } else if ("schemaComponentModel".equals(localName)) {
                    try {
                        Source source = getInputSource(content);
                        targetConfig.importComponents(source);
                    } catch (XPathException e) {
                        errors.add(new XmlProcessingException(e));
                    }
                } else if ("catalogFile".equals(localName)) {
                    URI baseURI = URI.create(systemId);
                    catalogFiles.add(baseURI.resolve(content).toString());
                } else if ("fileExtension".equals(localName)) {
                    // already done at startElement time
                } else {
                    error(localName, null, null, null);
                }
            }
        }

        if (level == 2
                && "resources".equals(localName)
                && catalogFiles.size() != 0
                && targetConfig.getResourceResolver() instanceof CatalogResourceResolver) {
            ((CatalogResourceResolver)targetConfig.getResourceResolver()).setFeature(ResolverFeature.CATALOG_FILES, catalogFiles);
        }

        level--;
        buffer.setLength(0);
    }

    private Source getInputSource(String href) throws XPathException {
        try {
            String base = getSystemId();
            URI abs = ResolveURI.makeAbsolute(href, base);
            return new StreamSource(abs.toString());
        } catch (URISyntaxException e) {
            throw new XPathException(e);
        }
    }

    @Override
    public void characters(UnicodeString chars, Location location, int properties) throws XPathException {
        buffer.append(chars.toString());
    }

}

