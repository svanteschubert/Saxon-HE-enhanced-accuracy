////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.functions.AccessorFn;
import net.sf.saxon.lib.*;
import net.sf.saxon.s9api.*;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaException;
import net.sf.saxon.value.DayTimeDurationValue;
import net.sf.saxon.value.NumericValue;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.math.BigDecimal;
import java.text.Collator;
import java.util.*;

/**
 * This is a helper class for classes such as net.sf.saxon.Transform and net.sf.saxon.Query that process
 * command line options
 */
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

    private HashMap<String, Integer> recognizedOptions = new HashMap<>();
    private HashMap<String, String> optionHelp = new HashMap<>();
    private Properties namedOptions = new Properties();
    private Properties configOptions = new Properties();
    private Map<String, Set<String>> permittedValues = new HashMap<>();
    private Map<String, String> defaultValues = new HashMap<>();
    private List<String> positionalOptions = new ArrayList<>();
    private Properties paramValues = new Properties();
    private Properties paramExpressions = new Properties();
    private Properties paramFiles = new Properties();
    private Properties serializationParams = new Properties();

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
        FastStringBuffer sb = new FastStringBuffer(20);
        for (String val : permittedValues) {
            if ("".equals(val)) {
                sb.append("\"\"");
            } else {
                sb.append(val);
            }
            sb.cat('|');
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
            if ("-".equals(arg)) {
                positionalOptions.add(arg);
            } else if (arg.equals("--?")) {
                System.err.println("Configuration features:" + featureKeys());
            } else if (arg.charAt(0) == '-') {
                String option;
                String value = "";
                if (arg.length() > 5 && arg.charAt(1) == '-') {
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
                    if (recognizedOptions.get(option) == null) {
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
     * @throws javax.xml.transform.TransformerException
     *          if invalid options are present
     */

    public void applyToConfiguration(/*@NotNull*/ final Processor processor) throws TransformerException {

        Configuration config = processor.getUnderlyingConfiguration();

        for (Enumeration e = configOptions.propertyNames(); e.hasMoreElements(); ) {
            String name = (String) e.nextElement();
            String value = configOptions.getProperty(name);
            String fullName = "http://saxon.sf.net/feature/" + name;
            if (!name.startsWith("parserFeature?") && !name.startsWith("parserProperty?")) {
                Feature<?> f = Feature.byName(fullName);
                if (f == null) {
                    throw new XPathException("Unknown configuration feature " + name);
                }

                if (f.type == Boolean.class) {
                    Configuration.requireBoolean(name, value);
                } else if (f.type == Integer.class) {
                    //noinspection ResultOfMethodCallIgnored
                    Integer.valueOf(value);
                } else if (f.type != String.class) {
                    throw new XPathException("Property --" + name + " cannot be supplied as a string");
                }
            }
            try {
                processor.getUnderlyingConfiguration().setConfigurationProperty(fullName, value);
            } catch (IllegalArgumentException err) {
                throw new XPathException("Incorrect value for --" + name + ": " + err.getMessage());
            }
        }

        String value = getOptionValue("catalog");
        if (value != null) {
            if (getOptionValue("r") != null) {
                throw new XPathException("Cannot use -catalog and -r together");
            }
            if (getOptionValue("x") != null) {
                throw new XPathException("Cannot use -catalog and -x together");
            }
            if (getOptionValue("y") != null) {
                throw new XPathException("Cannot use -catalog and -y together");
            }
            StringBuilder sb = new StringBuilder();
            if ((getOptionValue("u") != null) || isImplicitURI(value)) {
                for (String s : value.split(";")) {
                    Source sourceInput = null;
                    try {
                        sourceInput = config.getURIResolver().resolve(s, null);
                    } catch (TransformerException e) {
                        // no action - try the standard URI resolver instead
                    }
                    if (sourceInput == null) {
                        sourceInput = config.getSystemURIResolver().resolve(s, null);
                    }
                    sb.append(sourceInput.getSystemId()).append(';');
                }

            } else {
                for (String s : value.split(";")) {
                    File catalogFile = new File(s);
                    if (!catalogFile.exists()) {
                        throw new XPathException("Catalog file not found: " + s);
                    }
                    sb.append(catalogFile.toURI().toASCIIString()).append(';');
                }
            }
            value = sb.toString();

            try {
                config.getClass("org.apache.xml.resolver.CatalogManager", false, null);
                XmlCatalogResolver.setCatalog(value, config, getOptionValue("t") != null);
            } catch (XPathException err) {
                throw new XPathException("Failed to load Apache catalog resolver library", err);
            }
        }

        value = getOptionValue("dtd");
        if (value != null) {
            switch (value) {
                case "on":
                    config.setBooleanProperty(Feature.DTD_VALIDATION, true);
                    config.getParseOptions().setDTDValidationMode(Validation.STRICT);
                    break;
                case "off":
                    config.setBooleanProperty(Feature.DTD_VALIDATION, false);
                    config.getParseOptions().setDTDValidationMode(Validation.SKIP);
                    break;
                case "recover":
                    config.setBooleanProperty(Feature.DTD_VALIDATION, true);
                    config.setBooleanProperty(Feature.DTD_VALIDATION_RECOVERABLE, true);
                    config.getParseOptions().setDTDValidationMode(Validation.LAX);
                    break;
            }
        }

        value = getOptionValue("ea");
        if (value != null) {
            boolean on = Configuration.requireBoolean("ea", value);
            config.getDefaultXsltCompilerInfo().setAssertionsEnabled(on);
        }

        value = getOptionValue("expand");
        if (value != null) {
            boolean on = Configuration.requireBoolean("expand", value);
            config.getParseOptions().setExpandAttributeDefaults(on);
        }

        value = getOptionValue("ext");
        if (value != null) {
            boolean on = Configuration.requireBoolean("ext", value);
            config.setBooleanProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS, on);
        }

        value = getOptionValue("l");
        if (value != null) {
            boolean on = Configuration.requireBoolean("l", value);
            config.setBooleanProperty(Feature.LINE_NUMBERING, on);
        }

        value = getOptionValue("m");
        if (value != null) {
            config.setConfigurationProperty(Feature.MESSAGE_EMITTER_CLASS, value);
        }

        value = getOptionValue("opt");
        if (value != null) {
            config.setConfigurationProperty(Feature.OPTIMIZATION_LEVEL, value);
        }

        value = getOptionValue("or");
        if (value != null) {
            Object resolver = config.getInstance(value, null);
            if (resolver instanceof OutputURIResolver) {
                config.setConfigurationProperty(Feature.OUTPUT_URI_RESOLVER, (OutputURIResolver) resolver);
            } else {
                throw new XPathException("Class " + value + " is not an OutputURIResolver");
            }
        }

        value = getOptionValue("outval");
        if (value != null) {
            Boolean isRecover = "recover".equals(value);
            config.setConfigurationProperty(Feature.VALIDATION_WARNINGS, isRecover);
            config.setConfigurationProperty(Feature.VALIDATION_COMMENTS, isRecover);
        }

        value = getOptionValue("r");
        if (value != null) {
            config.setURIResolver(config.makeURIResolver(value));
        }

        value = getOptionValue("strip");
        if (value != null) {
            config.setConfigurationProperty(Feature.STRIP_WHITESPACE, value);
        }

        value = getOptionValue("T");
        if (value != null) {
            config.setCompileWithTracing(true);
        }

        value = getOptionValue("TJ");
        if (value != null) {
            boolean on = Configuration.requireBoolean("TJ", value);
            config.setBooleanProperty(Feature.TRACE_EXTERNAL_FUNCTIONS, on);
        }

        value = getOptionValue("tree");
        if (value != null) {
            switch (value) {
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

        value = getOptionValue("val");
        if (value != null) {
            if ("strict".equals(value)) {
                processor.setConfigurationProperty(Feature.SCHEMA_VALIDATION, Validation.STRICT);
            } else if ("lax".equals(value)) {
                processor.setConfigurationProperty(Feature.SCHEMA_VALIDATION, Validation.LAX);
            }
        }

        value = getOptionValue("x");
        if (value != null) {
            processor.setConfigurationProperty(Feature.SOURCE_PARSER_CLASS, value);
        }

        value = getOptionValue("xi");
        if (value != null) {
            boolean on = Configuration.requireBoolean("xi", value);
            processor.setConfigurationProperty(Feature.XINCLUDE, on);
        }

        value = getOptionValue("xmlversion");
        if (value != null) {
            processor.setConfigurationProperty(Feature.XML_VERSION, value);
        }

        value = getOptionValue("xsdversion");
        if (value != null) {
            processor.setConfigurationProperty(Feature.XSD_VERSION, value);
        }

        value = getOptionValue("xsiloc");
        if (value != null) {
            boolean on = Configuration.requireBoolean("xsiloc", value);
            processor.setConfigurationProperty(Feature.USE_XSI_SCHEMA_LOCATION, on);
        }

        value = getOptionValue("y");
        if (value != null) {
            processor.setConfigurationProperty(Feature.STYLE_PARSER_CLASS, value);
        }

        // The init option must be done last

        value = getOptionValue("init");
        if (value != null) {
            Initializer initializer = (Initializer) config.getInstance(value, null);
            initializer.initialize(config);
        }

    }

    /**
     * Display the list the permitted options
     *
     * @return the list of permitted options, as a string
     */

    public String displayPermittedOptions() {
        String[] options = new String[recognizedOptions.size()];
        options = new ArrayList<>(recognizedOptions.keySet()).toArray(options);
        Arrays.sort(options, Collator.getInstance());
        FastStringBuffer sb = new FastStringBuffer(100);
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
        for (Enumeration e = paramValues.propertyNames(); e.hasMoreElements(); ) {
            String name = (String) e.nextElement();
            String value = paramValues.getProperty(name);
            paramSetter.setParam(QName.fromClarkName(name), new XdmAtomicValue(value, ItemType.UNTYPED_ATOMIC));
        }
        applyFileParameters(processor, paramSetter);
        for (Enumeration e = paramExpressions.propertyNames(); e.hasMoreElements(); ) {
            String name = (String) e.nextElement();
            String value = paramExpressions.getProperty(name);
            // parameters starting with "?" are taken as XPath expressions
            XPathCompiler xpc = processor.newXPathCompiler();
            XPathExecutable xpe = xpc.compile(value);
            XdmValue val = xpe.load().evaluate();
            paramSetter.setParam(QName.fromClarkName(name), val);
        }
    }

    private void applyFileParameters(Processor processor, ParamSetter paramSetter) throws SaxonApiException {
        boolean useURLs = "on".equals(getOptionValue("u"));
        for (Enumeration e = paramFiles.propertyNames(); e.hasMoreElements(); ) {
            String name = (String) e.nextElement();
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
        for (Enumeration e = serializationParams.propertyNames(); e.hasMoreElements(); ) {
            String name = (String) e.nextElement();
            String value = serializationParams.getProperty(name);
            // parameters starting with "!" are taken as output properties
            // Allow the prefix "!saxon:" instead of "!{http://saxon.sf.net}"
            if (name.startsWith("saxon:")) {
                name = "{" + NamespaceConstant.SAXON + "}" + name.substring(6);
            }
            serializer.setOutputProperty(QName.fromClarkName(name), value);
        }
    }

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
        for (Enumeration e = paramValues.propertyNames(); e.hasMoreElements(); ) {
            String name = (String) e.nextElement();
            String value = paramValues.getProperty(name);
            compiler.setParameter(QName.fromClarkName(name), new XdmAtomicValue(value, ItemType.UNTYPED_ATOMIC));
        }
        for (Enumeration e = paramExpressions.propertyNames(); e.hasMoreElements(); ) {
            String name = (String) e.nextElement();
            String value = paramExpressions.getProperty(name);
            // parameters starting with "?" are taken as XPath expressions
            XPathCompiler xpc = processor.newXPathCompiler();
            XPathExecutable xpe = xpc.compile(value);
            XdmValue val = xpe.load().evaluate();
            compiler.setParameter(QName.fromClarkName(name), val);
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
            applyFileParameters(processor, params::put);
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
        XMLReader parser;
        Configuration config = processor.getUnderlyingConfiguration();
        if (useURLs || isImplicitURI(sourceFileName)) {
            try {
                sourceInput = config.getURIResolver().resolve(sourceFileName, null);
                if (sourceInput == null) {
                    sourceInput = config.getSystemURIResolver().resolve(sourceFileName, null);
                }
            } catch (TransformerException e) {
                throw new SaxonApiException(e);
            }
            sources.add(sourceInput);
            return false;
        } else if (sourceFileName.equals("-")) {
            // take input from stdin
            if (useSAXSource) {
                parser = config.getSourceParser();
                sourceInput = new SAXSource(parser, new InputSource(System.in));
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
                parser = config.getSourceParser();
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
                    sourceInput = new SAXSource(config.getSourceParser(), eis);
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
            name.startsWith("classpath:");
    }

    public static void loadAdditionalSchemas(/*@NotNull*/ Configuration config, String additionalSchemas)
            throws SchemaException {
        StringTokenizer st = new StringTokenizer(additionalSchemas, File.pathSeparator);
        while (st.hasMoreTokens()) {
            String schema = st.nextToken();
            File schemaFile = new File(schema);
            if (!schemaFile.exists()) {
                throw new SchemaException("Schema document " + schema + " not found");
            }
            config.addSchemaSource(new StreamSource(schemaFile));
        }
    }

    public static String featureKeys() {
        final int index = "http://saxon.sf.net/feature/".length();
        StringBuilder sb = new StringBuilder();
        Feature.getNames().forEachRemaining(s -> sb.append("\n  ").append(s.substring(index)));
        return sb.toString();
    }

    private static DayTimeDurationValue milliSecond = new DayTimeDurationValue(1, 0, 0, 0, 0, 1000);

    public static String showExecutionTimeNano(long nanosecs) {
        if (nanosecs < 1e9) {
            // time less than one second
            return (nanosecs/1e6) + "ms";
        } else {
            try {
                double millisecs = nanosecs/1e6;
                DayTimeDurationValue d = milliSecond.multiply(millisecs);
                long days = ((NumericValue) d.getComponent(AccessorFn.Component.DAY)).longValue();
                long hours = ((NumericValue) d.getComponent(AccessorFn.Component.HOURS)).longValue();
                long minutes = ((NumericValue) d.getComponent(AccessorFn.Component.MINUTES)).longValue();
                BigDecimal seconds = ((NumericValue) d.getComponent(AccessorFn.Component.SECONDS)).getDecimalValue();
                FastStringBuffer fsb = new FastStringBuffer(256);
                if (days > 0) {
                    fsb.append(days + "days ");
                }
                if (hours > 0) {
                    fsb.append(hours + "h ");
                }
                if (minutes > 0) {
                    fsb.append(minutes + "m ");
                }
                fsb.append(seconds + "s");
                return fsb + " (" + nanosecs / 1e6 + "ms)";
            } catch (XPathException e) {
                return nanosecs / 1e6 + "ms";
            }

        }
    }

    public static String getCommandName(Object command) {
        String s = command.getClass().getName();
        if (s.startsWith("cli.Saxon.Cmd.DotNet")) {
            s = s.substring("cli.Saxon.Cmd.DotNet".length());
        }
        return s;
    }

    public static void showMemoryUsed() {
        long value = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        System.err.println("Memory used: " + (value / 1_000_000) + "Mb");
    }
}

