////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import net.sf.saxon.event.Builder;
import net.sf.saxon.lib.*;
import net.sf.saxon.s9api.*;
import net.sf.saxon.transpile.CSharpDelegate;
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.transpile.CSharpReplaceBody;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xmlresolver.ResolverFeature;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.net.URI;
import java.text.Collator;
import java.util.*;

/**
 * This is a helper class for classes such as net.sf.saxon.Transform and net.sf.saxon.Query that process
 * command line options
 */
@CSharpModifiers(code = {"internal"})
public class CommandLineOptions {

    public static final int TYPE_BOOLEAN = 1;
    public static final int TYPE_FILENAME = 2;
    public static final int TYPE_CLASSNAME = 3;
    public static final int TYPE_ENUMERATION = 4;
    public static final int TYPE_INTEGER = 5;
    public static final int TYPE_QNAME = 6;
    public static final int TYPE_FILENAME_LIST = 7;
    public static final int TYPE_DATETIME = 8;
    public static final int TYPE_STRING = 9;
    public static final int TYPE_INTEGER_PAIR = 10;

    public static final int VALUE_REQUIRED = 1 << 8;
    public static final int VALUE_PROHIBITED = 2 << 8;

    private final HashMap<String, Integer> recognizedOptions = new HashMap<>();
    private final HashMap<String, String> optionHelp = new HashMap<>();
    private final Properties namedOptions = new Properties();
    private final Properties configOptions = new Properties();
    private final Map<String, Set<String>> permittedValues = new HashMap<>();
    private final Map<String, String> defaultValues = new HashMap<>();
    private final List<String> positionalOptions = new ArrayList<>();
    private final Properties paramValues = new Properties();
    private final Properties paramExpressions = new Properties();
    private final Properties paramFiles = new Properties();
    private final Properties serializationParams = new Properties();

    @CSharpReplaceBody(code="return Saxon.Ejava.io.File.CurrentDirectoryUri();")
    public static URI getCurrentWorkingDirectory() {
        return new File(System.getProperty("user.dir")).toURI();
    }

    /**
     * Set the permitted options.
     *
     * @param option           A permitted option.
     * @param optionProperties of this option, for example whether it is mandatory
     * @param helpText         message to be output if the user needs help concerning this option
     */

    public void addRecognizedOption(String option, int optionProperties, String helpText) {
        recognizedOptions.put(option, optionProperties);
        optionHelp.put(option, helpText);
        if ((optionProperties & 0xff) == TYPE_BOOLEAN) {
            setPermittedValues(option, new String[]{"on", "off"}, "on");
        }
    }

    /**
     * Set the permitted values for an option
     *
     * @param option       the option keyword
     * @param values       the set of permitted values
     * @param defaultValue the default value if the option is supplied but no value is given. May be null if no
     *                     default is defined.
     */

    public void setPermittedValues(String option, String[] values, /*@Nullable*/ String defaultValue) {
        Set<String> valueSet = new HashSet<>(Arrays.asList(values));
        permittedValues.put(option, valueSet);
        if (defaultValue != null) {
            defaultValues.put(option, defaultValue);
        }
    }

    /**
     * Display a list of the values permitted for an option with type enumeration
     *
     * @param permittedValues the set of permitted values
     * @return the set of values as a string, pipe-separated
     */

    private static String displayPermittedValues(/*@NotNull*/ Set<String> permittedValues) {
        StringBuilder sb = new StringBuilder(20);
        for (String val : permittedValues) {
            if ("".equals(val)) {
                sb.append("\"\"");
            } else {
                sb.append(val);
            }
            sb.append('|');
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    /**
     * Set the options actually present on the command line
     *
     * @param args the options supplied on the command line
     * @throws XPathException if an unrecognized or invalid option is found
     */

    public void setActualOptions(/*@NotNull*/ String[] args) throws XPathException {
        for (String arg : args) {
            if ("".equals(arg)) {
                throw new XPathException("An empty string is not a valid command line option");
            } else if ("-".equals(arg)) {
                positionalOptions.add(arg);
//            } else if (arg.equals("--?")) {
//                System.err.println("Configuration features:" + featureKeys());
            } else if (arg.charAt(0) == '-') {
                String option;
                String value = "";
                if (arg.length() > 2 && arg.charAt(1) == '-') {
                    // --featureKey:value format
                    int colon = arg.indexOf(':');
                    if (colon > 0 && colon < arg.length() - 1) {
                        option = arg.substring(2, colon);
                        value = arg.substring(colon + 1);
                        configOptions.setProperty(option, value);
                    } else if (colon > 0 && colon == arg.length() - 1) {
                        option = arg.substring(2, colon);
                        configOptions.setProperty(option, "");
                    } else {
                        option = arg.substring(2);
                        configOptions.setProperty(option, "true");
                    }
                } else {
                    int colon = arg.indexOf(':');
                    if (colon > 0 && colon < arg.length() - 1) {
                        option = arg.substring(1, colon);
                        value = arg.substring(colon + 1);
                    } else {
                        option = arg.substring(1);
                    }
                    if (recognizedOptions.getOrDefault(option, -1) == -1) {
                        throw new XPathException("Command line option -" + option +
                                " is not recognized. Options available: " + displayPermittedOptions());
                    }
                    if (namedOptions.getProperty(option) != null) {
                        throw new XPathException("Command line option -" + option + " appears more than once");
                    } else if ("?".equals(value)) {
                        displayOptionHelp(option);
                        throw new XPathException("No processing requested");
                    } else {
                        if ("".equals(value)) {
                            int prop = recognizedOptions.get(option);
                            if ((prop & VALUE_REQUIRED) != 0) {
                                String msg = "Command line option -" + option + " requires a value";
                                if (permittedValues.get(option) != null) {
                                    msg += ": permitted values are " + displayPermittedValues(permittedValues.get(option));
                                }
                                throw new XPathException(msg);
                            }
                            String defaultValue = defaultValues.get(option);
                            if (defaultValue != null) {
                                value = defaultValue;
                            }
                        } else {
                            int prop = recognizedOptions.get(option);
                            if ((prop & VALUE_PROHIBITED) != 0) {
                                String msg = "Command line option -" + option + " does not expect a value";
                                throw new XPathException(msg);
                            }
                        }
                        Set<String> permitted = permittedValues.get(option);
                        if (permitted != null && !permitted.contains(value)) {
                            throw new XPathException("Bad option value " + arg +
                                    ": permitted values are " + displayPermittedValues(permitted));
                        }
                        namedOptions.setProperty(option, value);
                    }
                }
            } else {
                // handle keyword=value options
                int eq = arg.indexOf('=');
                if (eq >= 1) {
                    String keyword = arg.substring(0, eq);
                    String value = "";
                    if (eq < arg.length() - 1) {
                        value = arg.substring(eq + 1);
                    }
                    char ch = arg.charAt(0);
                    if (ch == '!' && eq >= 2) {
                        serializationParams.setProperty(keyword.substring(1), value);
                    } else if (ch == '?' && eq >= 2) {
                        paramExpressions.setProperty(keyword.substring(1), value);
                    } else if (ch == '+' && eq >= 2) {
                        paramFiles.setProperty(keyword.substring(1), value);
                    } else {
                        paramValues.setProperty(keyword, value);
                    }
                } else {
                    positionalOptions.add(arg);
                }
            }
        }
    }

    /**
     * Test whether there is any keyword=value option present
     *
     * @return true if there are any keyword=value options
     */

    public boolean definesParameterValues() {
        return !serializationParams.isEmpty() ||
                !paramExpressions.isEmpty() ||
                !paramFiles.isEmpty() ||
                !paramValues.isEmpty();
    }

    /**
     * Prescan the command line arguments to see if any of them imply use of a schema-aware processor
     *
     * @return true if a schema-aware processor is needed
     */

    public boolean testIfSchemaAware() {
        return getOptionValue("sa") != null ||
                getOptionValue("outval") != null ||
                getOptionValue("val") != null ||
                getOptionValue("vlax") != null ||
                getOptionValue("xsd") != null ||
                getOptionValue("xsdversion") != null;
    }

    /**
     * Apply options to the Configuration
     *
     * @param processor the s9api Processor object
     * @throws SaxonApiException if invalid options are present
     */

    public void applyToConfiguration(/*@NotNull*/ final Processor processor) throws SaxonApiException {

        Configuration config = processor.getUnderlyingConfiguration();

        if (configOptions.getProperty("?") != null) {
            System.err.println(featureKeys(processor.getSaxonEdition()));
            return;
        }

        for (String name : configOptions.stringPropertyNames()) {
            String value = configOptions.getProperty(name);
            String fullName = "http://saxon.sf.net/feature/" + name;
            if (!name.startsWith("parserFeature?") && !name.startsWith("parserProperty?")) {
                if (FeatureIndex.exists(fullName)) {
                    FeatureData f = FeatureIndex.getData(fullName);
                    if (f == null) {
                        throw new SaxonApiException("Unknown configuration feature " + name);
                    }

                    if (f.type == Boolean.class) {
                        Configuration.requireBoolean(name, value);
                    } else if (f.type == Integer.class) {
                        //noinspection ResultOfMethodCallIgnored
                        Integer.valueOf(value);
                    } else if (f.type != String.class) {
                        throw new SaxonApiException("Property --" + name + " cannot be supplied as a string");
                    }
                } else {
                    throw new SaxonApiException("Unknown configuration property --" + name);
                }
            }
            try {
                processor.getUnderlyingConfiguration().setConfigurationProperty(fullName, value);
            } catch (IllegalArgumentException err) {
                throw new SaxonApiException("Incorrect value for --" + name + ": " + err.getMessage());
            }
        }

        String optionValue = getOptionValue("catalog");
        if (optionValue != null) {
            ArrayList<String> catalogs = new ArrayList<>();
            if ((getOptionValue("u") != null) || isImplicitURI(optionValue)) {
                ResourceRequest request = new ResourceRequest();
                request.nature = "urn:oasis:names:tc:entity:xmlns:xml:catalog";
                request.purpose = ResourceRequest.ANY_PURPOSE;
                for (String s : optionValue.split(";")) {
                    Source sourceInput;
                    try {
                        request.uri = s;
                        sourceInput = new DirectResourceResolver(config).resolve(request);
                    } catch (XPathException e) {
                        throw new SaxonApiException("Catalog file not found: " + s, e);
                    }
                    catalogs.add(sourceInput.getSystemId());
                }
            } else {
                for (String s : optionValue.split(";")) {
                    File catalogFile = new File(s);
                    if (!catalogFile.exists()) {
                        throw new SaxonApiException("Catalog file not found: " + s);
                    }
                    catalogs.add(catalogFile.toURI().toASCIIString());
                }
            }

            setCatalogFiles(config, catalogs);
        }

        optionValue = getOptionValue("dtd");
        if (optionValue != null) {
            int mode = Validation.DEFAULT;
            switch (optionValue) {
                case "on":
                    config.setBooleanProperty(Feature.DTD_VALIDATION, true);
                    mode = Validation.STRICT;
                    break;
                case "off":
                    config.setBooleanProperty(Feature.DTD_VALIDATION, false);
                    mode = Validation.SKIP;
                    break;
                case "recover":
                    config.setBooleanProperty(Feature.DTD_VALIDATION, true);
                    config.setBooleanProperty(Feature.DTD_VALIDATION_RECOVERABLE, true);
                    mode = Validation.LAX;
                    break;
            }
            config.setParseOptions(config.getParseOptions().withDTDValidationMode(mode));
        }

        optionValue = getOptionValue("ea");
        if (optionValue != null) {
            boolean on = Configuration.requireBoolean("ea", optionValue);
            config.getDefaultXsltCompilerInfo().setAssertionsEnabled(on);
        }

        optionValue = getOptionValue("expand");
        if (optionValue != null) {
            boolean on = Configuration.requireBoolean("expand", optionValue);
            config.setParseOptions(config.getParseOptions().withExpandAttributeDefaults(on));
        }

        optionValue = getOptionValue("ext");
        if (optionValue != null) {
            boolean on = Configuration.requireBoolean("ext", optionValue);
            config.setBooleanProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS, on);
        }

        optionValue = getOptionValue("l");
        if (optionValue != null) {
            boolean on = Configuration.requireBoolean("l", optionValue);
            config.setBooleanProperty(Feature.LINE_NUMBERING, on);
        }

        optionValue = getOptionValue("m");
        if (optionValue != null) {
            config.setConfigurationProperty(Feature.MESSAGE_EMITTER_CLASS, optionValue);
        }

        optionValue = getOptionValue("opt");
        if (optionValue != null) {
            config.setConfigurationProperty(Feature.OPTIMIZATION_LEVEL, optionValue);
        }

        optionValue = getOptionValue("or");
        if (optionValue != null) {
            Object resolver;
            try {
                resolver = config.getInstance(optionValue);
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
            if (resolver instanceof OutputURIResolver) {
                config.setConfigurationProperty(Feature.OUTPUT_URI_RESOLVER, (OutputURIResolver) resolver);
            } else {
                throw new SaxonApiException("Class " + optionValue + " is not an OutputURIResolver");
            }
        }

        optionValue = getOptionValue("outval");
        if (optionValue != null) {
            Boolean isRecover = "recover".equals(optionValue);
            config.setConfigurationProperty(Feature.VALIDATION_WARNINGS, isRecover);
            config.setConfigurationProperty(Feature.VALIDATION_COMMENTS, isRecover);
        }

        optionValue = getOptionValue("r");
        if (optionValue != null) {
            try {
                config.setResourceResolver(config.makeResourceResolver(optionValue));
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
        }

        optionValue = getOptionValue("strip");
        if (optionValue != null) {
            config.setConfigurationProperty(Feature.STRIP_WHITESPACE, optionValue);
        }

        optionValue = getOptionValue("T");
        if (optionValue != null) {
            config.setCompileWithTracing(true);
        }

        optionValue = getOptionValue("TJ");
        if (optionValue != null) {
            boolean on = Configuration.requireBoolean("TJ", optionValue);
            config.setBooleanProperty(Feature.TRACE_EXTERNAL_FUNCTIONS, on);
        }

        optionValue = getOptionValue("tree");
        if (optionValue != null) {
            switch (optionValue) {
                case "linked":
                    config.setTreeModel(Builder.LINKED_TREE);
                    break;
                case "tiny":
                    config.setTreeModel(Builder.TINY_TREE);
                    break;
                case "tinyc":
                    config.setTreeModel(Builder.TINY_TREE_CONDENSED);
                    break;
            }
        }

        optionValue = getOptionValue("val");
        if (optionValue != null) {
            if ("strict".equals(optionValue)) {
                processor.setConfigurationProperty(Feature.SCHEMA_VALIDATION, Validation.STRICT);
            } else if ("lax".equals(optionValue)) {
                processor.setConfigurationProperty(Feature.SCHEMA_VALIDATION, Validation.LAX);
            }
        }

        optionValue = getOptionValue("x");
        if (optionValue != null) {
            processor.setConfigurationProperty(Feature.SOURCE_PARSER_CLASS, optionValue);
        }

        optionValue = getOptionValue("xi");
        if (optionValue != null) {
            boolean on = Configuration.requireBoolean("xi", optionValue);
            processor.setConfigurationProperty(Feature.XINCLUDE, on);
        }

        optionValue = getOptionValue("xmlversion");
        if (optionValue != null) {
            processor.setConfigurationProperty(Feature.XML_VERSION, optionValue);
        }

        optionValue = getOptionValue("xsdversion");
        if (optionValue != null) {
            processor.setConfigurationProperty(Feature.XSD_VERSION, optionValue);
        }

        optionValue = getOptionValue("xsiloc");
        if (optionValue != null) {
            boolean on = Configuration.requireBoolean("xsiloc", optionValue);
            processor.setConfigurationProperty(Feature.USE_XSI_SCHEMA_LOCATION, on);
        }

        optionValue = getOptionValue("y");
        if (optionValue != null) {
            processor.setConfigurationProperty(Feature.STYLE_PARSER_CLASS, optionValue);
        }

        // The init option must be done last

        optionValue = getOptionValue("init");
        if (optionValue != null) {
            invokeInitializer(processor, optionValue);
        }
    }

    @CSharpReplaceBody(code="Saxon.Api.Support.InitializationHandler.doInitialization(new Saxon.Api.Processor(processor), initializationClass);")
    private void invokeInitializer(Processor processor, String initializationClass) throws SaxonApiException {
        try {
            Configuration config = processor.getUnderlyingConfiguration();
            Initializer initializer = (Initializer) config.getInstance(initializationClass);
            initializer.initialize(config);
        } catch (TransformerException e) {
            throw new SaxonApiException(e);
        }
    }

    private void setCatalogFiles(Configuration config, List<String> catalogs) {
        ResourceResolver rr = config.getResourceResolver();
        if (rr instanceof ConfigurableResourceResolver) {
            setCatalogFiles(((ConfigurableResourceResolver) rr), catalogs);
        } else {
            throw new IllegalStateException("The resolver in the Configuration is not a ConfigurableResourceResolver");
        }
    }

    @CSharpReplaceBody(code="crr.setFeature(XmlResolver.Features.ResolverFeature.CATALOG_FILES, catalogs);")
    public static void setCatalogFiles(ConfigurableResourceResolver crr, List<String> catalogs) {
        crr.setFeature(ResolverFeature.CATALOG_FILES, catalogs);
    }

    /**
     * Display the list the permitted options
     *
     * @return the list of permitted options, as a string
     */

    public String displayPermittedOptions() {
        String[] options = new String[recognizedOptions.size()];
        ArrayList<String> keys = new ArrayList<>(recognizedOptions.keySet());
        options = keys.toArray(options);
        Arrays.sort(options, Collator.getInstance());
        StringBuilder sb = new StringBuilder(100);
        for (String opt : options) {
            sb.append(" -");
            sb.append(opt);
        }
        sb.append(" --?");
        return sb.toString();
    }

    /**
     * Display help for a specific option on the System.err output (in response to -opt:?)
     *
     * @param option: the option for which help is required
     */

    private void displayOptionHelp(String option) {
        System.err.println("Help for -" + option + " option");
        int prop = recognizedOptions.get(option);
        if ((prop & VALUE_PROHIBITED) == 0) {
            switch (prop & 0xff) {
                case TYPE_BOOLEAN:
                    System.err.println("Value: on|off");
                    break;
                case TYPE_INTEGER:
                    System.err.println("Value: integer");
                    break;
                case TYPE_FILENAME:
                    System.err.println("Value: file name");
                    break;
                case TYPE_FILENAME_LIST:
                    System.err.println("Value: list of file names, semicolon-separated");
                    break;
                case TYPE_CLASSNAME:
                    System.err.println("Value: Java fully-qualified class name");
                    break;
                case TYPE_QNAME:
                    System.err.println("Value: QName in Clark notation ({uri}local)");
                    break;
                case TYPE_STRING:
                    System.err.println("Value: string");
                    break;
                case TYPE_INTEGER_PAIR:
                    System.err.println("Value: int,int");
                    break;
                case TYPE_ENUMERATION:
                    String message = "Value: one of ";
                    message += displayPermittedValues(permittedValues.get(option));
                    System.err.println(message);
                    break;
                default:
                    break;
            }
        }
        System.err.println("Meaning: " + optionHelp.get(option));
    }

    /**
     * Get the value of a named option. Returns null if the option was not present on the command line.
     * Returns "" if the option was present but with no value ("-x" or "-x:").
     *
     * @param option the option keyword
     * @return the option value, or null if not specified.
     */

    public String getOptionValue(String option) {
        return namedOptions.getProperty(option);
    }

    /**
     * Get the options specified positionally, that is, without a leading "-"
     *
     * @return the list of positional options
     */

    /*@NotNull*/
    public List<String> getPositionalOptions() {
        return positionalOptions;
    }

    public void setParams(Processor processor, ParamSetter paramSetter)
            throws SaxonApiException {
        for (String name : paramValues.stringPropertyNames()) {
            String value = paramValues.getProperty(name);
            paramSetter.setParam(QName.fromClarkName(name), new XdmAtomicValue(value, ItemType.UNTYPED_ATOMIC));
        }
        applyFileParameters(processor, paramSetter);
        for (String name : paramExpressions.stringPropertyNames()) {
            String value = paramExpressions.getProperty(name);
            // parameters starting with "?" are taken as XPath expressions
            try {
                XPathCompiler xpc = processor.newXPathCompiler();
                XPathExecutable xpe = xpc.compile(value);
                XdmValue val = xpe.load().evaluate();
                paramSetter.setParam(QName.fromClarkName(name), val);
            } catch (SaxonApiException e) {
                throw new SaxonApiException("Failure evaluating XPath expression {" + value + "} on command line", e.getCause());
            }
        }
    }

    private void applyFileParameters(Processor processor, ParamSetter paramSetter) throws SaxonApiException {
        boolean useURLs = "on".equals(getOptionValue("u"));
        for (String name : paramFiles.stringPropertyNames()) {
            String value = paramFiles.getProperty(name);
            List<Source> sourceList = new ArrayList<>();
            loadDocuments(value, useURLs, processor, true, sourceList);
            if (!sourceList.isEmpty()) {
                List<XdmNode> nodeList = new ArrayList<>(sourceList.size());
                DocumentBuilder builder = processor.newDocumentBuilder();
                for (Source s : sourceList) {
                    nodeList.add(builder.build(s));
                }
                XdmValue nodes = new XdmValue(nodeList);
                paramSetter.setParam(QName.fromClarkName(name), nodes);
            } else {
                paramSetter.setParam(QName.fromClarkName(name), XdmEmptySequence.getInstance());
            }
        }
    }

    /**
     * Set any output properties appearing on the command line in the form {@code !indent=yes}
     * as properties of the supplied {@code Serializer}
     * @param serializer the supplied {@code Serializer}, whose serialization properties
     *                   are to be modified.
     */

    public void setSerializationProperties(Serializer serializer) {
        for (String name : serializationParams.stringPropertyNames()) {
            String key = name;
            String value = serializationParams.getProperty(key);
            // parameters starting with "!" are taken as output properties
            // Allow the prefix "!saxon:" instead of "!{http://saxon.sf.net}"
            if (key.startsWith("saxon:")) {
                key = "{" + NamespaceConstant.SAXON + "}" + key.substring(6);
            }
            serializer.setOutputProperty(QName.fromClarkName(key), value);
        }
    }

    @FunctionalInterface
    @CSharpDelegate(true)
    public interface ParamSetter {
        void setParam(QName qName, XdmValue value);
    }


    /**
     * Apply XSLT 3.0 static parameters to a compilerInfo. Actually this sets all parameter values, whether static or dynamic.
     * This is possible because the stylesheet is compiled for once-only use.
     *
     * @param compiler The XsltCompiler object into which the parameters are copied
     * @throws SaxonApiException if invalid options are found
     */

    public void applyStaticParams(XsltCompiler compiler)
            throws SaxonApiException {
        Processor processor = compiler.getProcessor();
        for (String name : paramValues.stringPropertyNames()) {
            String value = paramValues.getProperty(name);
            compiler.setParameter(QName.fromClarkName(name), new XdmAtomicValue(value, ItemType.UNTYPED_ATOMIC));
        }
        for (String name : paramExpressions.stringPropertyNames()) {
            String value = paramExpressions.getProperty(name);
            // parameters starting with "?" are taken as XPath expressions
            try {
                XPathCompiler xpc = processor.newXPathCompiler();
                XPathExecutable xpe = xpc.compile(value);
                XdmValue val = xpe.load().evaluate();
                compiler.setParameter(QName.fromClarkName(name), val);
            } catch (SaxonApiException e) {
                throw new SaxonApiException("Failure evaluating XPath expression {" + value + "} on command line", e.getCause());
            }
        }

    }


    /**
     * Apply XSLT 3.0 file-valued parameters to an XSLT transformer. Most parameters are applied
     * before compilation, so that the compiler can take advantage of knowing their values; but
     * file-valued parameters (provided as +name=value) are deferred until run-time because of
     * complications storing their values in a SEF file.
     *
     * @param transformer The Xslt30Transformer object into which the parameters are copied
     * @throws SaxonApiException if invalid options are found
     */

    public void applyFileParams(Processor processor, Xslt30Transformer transformer) throws SaxonApiException {
        if (!paramFiles.isEmpty()) {
            Map<QName, XdmValue> params = new HashMap<>();
            //noinspection Convert2MethodRef
            applyFileParameters(processor, (name, value) -> params.put(name, value));
            transformer.setStylesheetParameters(params);
        }
    }


    /**
     * Load a document, or all the documents in a directory, given a filename or URL
     *
     * @param sourceFileName the name of the source file or directory
     * @param useURLs        true if the filename argument is to be treated as a URI
     * @param processor      the Saxon s9api Processor
     * @param useSAXSource   true if the method should use a SAXSource rather than a StreamSource
     * @param sources        an empty list which the method will populate.
     *                       If sourceFileName represents a single source document, a corresponding XdmNode is
     *                       added to the list. If sourceFileName represents a directory, multiple XdmNode
     *                       objects, one for each file in the directory, are added to the list
     * @return true if the supplied sourceFileName was found to be a directory
     * @throws SaxonApiException if access to documents fails
     */

    /*@Nullable*/
    public static boolean loadDocuments(String sourceFileName, boolean useURLs,
                                        Processor processor, boolean useSAXSource, List<Source> sources)
            throws SaxonApiException {

        Source sourceInput;

        Configuration config = processor.getUnderlyingConfiguration();
        if (useURLs || isImplicitURI(sourceFileName)) {
            try {
                ResourceRequest request = new ResourceRequest();
                request.uri = sourceFileName;
                request.nature = ResourceRequest.XML_NATURE;
                request.purpose = ResourceRequest.ANY_PURPOSE;
                sourceInput = request.resolve(config.getResourceResolver(), new DirectResourceResolver(config));
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
            sources.add(sourceInput);
            return false;
        } else if (sourceFileName.equals("-")) {
            // take input from stdin
            if (useSAXSource) {
                sourceInput = new SAXSource(new InputSource(System.in));
            } else {
                sourceInput = new StreamSource(System.in);
            }
            sources.add(sourceInput);
            return false;
        } else {
            File sourceFile = new File(sourceFileName);
            if (!sourceFile.exists()) {
                throw new SaxonApiException("Source file " + sourceFile + " does not exist");
            }
            if (sourceFile.isDirectory()) {
                XMLReader parser = config.getSourceParser();
                String[] files = sourceFile.list();
                if (files != null) {
                    for (String file1 : files) {
                        File file = new File(sourceFile, file1);
                        if (!file.isDirectory() && !file.isHidden()) {
                            if (useSAXSource) {
                                InputSource eis = new InputSource(file.toURI().toString());
                                sourceInput = new SAXSource(parser, eis);
                                // it's safe to use the same parser for each document, as they
                                // will be processed one at a time.
                            } else {
                                sourceInput = new StreamSource(file.toURI().toString());
                            }
                            sources.add(sourceInput);
                        }
                    }
                }
                return true;
            } else {
                if (useSAXSource) {
                    InputSource eis = new InputSource(sourceFile.toURI().toString());
                    sourceInput = new SAXSource(eis);
                } else {
                    sourceInput = new StreamSource(sourceFile.toURI().toString());
                }
                sources.add(sourceInput);
                return false;
            }
        }
    }

    public static boolean isImplicitURI(String name) {
        return name.startsWith("http:") ||
            name.startsWith("https:") ||
            name.startsWith("file:") ||
            name.startsWith("classpath:") ||
            name.startsWith("jar:");
    }

    /*
     * Coerce an output filename that's been specified with a file: URI into a filename.
     *
     * file:path => path
     * file://///path => /path
     * file:///c:/path => c:/path (on Windows)
     *
     */
    public static String coerceImplicitOutputURI(String outputName) {
        if (outputName == null) {
            return null;
        }
        if (outputName.startsWith("file:")) {
            outputName = outputName.substring(5);
            if (outputName.startsWith("/")) {
                outputName = outputName.replaceFirst("^/+", "/");
            }
            if (Version.platform.isWindows() && outputName.matches("^/[A-Za-z]:.*$")) {
                outputName = outputName.substring(1);
            }
        }
        return outputName;
    }

    public static XsdSchema loadAdditionalSchemas(Processor proc, String additionalSchemas)
            throws SaxonApiException {
        if (!proc.isSchemaAware()) {
            throw new SaxonApiException("The -xsd option requires a Saxon-EE license");
        }
        XsdCompiler compiler = proc.newXsdCompiler();
        List<File> schemaFiles = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(additionalSchemas, File.pathSeparator);
        while (st.hasMoreTokens()) {
            String schema = st.nextToken();
            File schemaFile = new File(schema);
            if (!schemaFile.exists()) {
                throw new SaxonApiException("Schema document " + schema + " not found");
            }
            schemaFiles.add(schemaFile);
            //config.addSchemaSource(new StreamSource(schemaFile));
        }
        return compiler.compile(schemaFiles.toArray(new File[0]));
    }

    public static String featureKeys(String edition) {
        final int index = "http://saxon.sf.net/feature/".length();
        StringBuilder sb = new StringBuilder();
        for (String name : FeatureIndex.getNames()) {
            if (FeatureIndex.getData(name).editions.contains(edition)) {
                sb.append("\n  ").append(name.substring(index));
            }
        }
        return sb.toString();
    }

    @CSharpReplaceBody(code="return \"dotnet saxoncs \" + command.GetType().Name.ToLower();")
    public static String getCommandName(Object command) {
        String s = command.getClass().getName();
        if (s.startsWith("cli.Saxon.Cmd.DotNet")) {
            s = s.substring("cli.Saxon.Cmd.DotNet".length());
        }
        return s;
    }

}

