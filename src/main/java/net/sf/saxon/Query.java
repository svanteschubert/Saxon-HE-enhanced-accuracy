////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon;

////import com.saxonica.ee.schema.PreparedSchema;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.instruct.TerminationException;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.*;
import net.sf.saxon.query.QueryReader;
import net.sf.saxon.query.QueryResult;
import net.sf.saxon.query.UpdateAgent;
import net.sf.saxon.query.XQueryExpression;
import net.sf.saxon.s9api.*;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.str.StringView;
import net.sf.saxon.trace.*;
import net.sf.saxon.trans.CommandLineOptions;
import net.sf.saxon.trans.LicenseException;
import net.sf.saxon.trans.Timer;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharp;
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.type.ConversionResult;
import net.sf.saxon.type.SchemaException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.DateTimeValue;
import org.xml.sax.InputSource;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * This <B>Query</B> class provides a command-line interface to the Saxon XQuery processor.
 * <p>The XQuery syntax supported conforms to the W3C XQuery 1.0 drafts.</p>
 *
 */

public class Query {

    private Processor processor;
    protected Configuration config;
    protected boolean showTime = false;
    protected int repeat = 1;
    protected String sourceXmlFileName = null;
    protected String sourceJsonFileName = null;
    protected String queryFileName = null;
    protected boolean useURLs = false;
    protected String outputFileName = null;
    protected String moduleURIResolverClass = null;
    protected boolean explaining = false;
    protected boolean wrap = false;
    protected boolean streaming = false;
    protected boolean updating = false;
    protected boolean writeback = false;
    protected boolean backup = true;
    protected String explainOutputFileName = null;
    private Logger traceDestination = StandardLogger.makeLogger();
    private boolean closeTraceDestination = false;
    private boolean allowExit = true;
    protected String languageVersion = "3.1";
    protected String nsOption;
    private XsdSchema externalSchema = null;


    /**
     * Get the configuration in use
     *
     * @return the configuration
     */

    protected Configuration getConfiguration() {
        return config;
    }

    /**
     * Main program, can be used directly from the command line.
     * <p>The format is:</p>
     * <p>java net.sf.saxon.Query [options] <i>query-file</i> &gt;<i>output-file</i></p>
     * <p>followed by any number of parameters in the form {keyword=value}... which can be
     * referenced from within the query.</p>
     * <p>This program executes the query in query-file.</p>
     *
     * @param args List of arguments supplied on operating system command line
     */

    public static void main(String[] args) {
        // the real work is delegated to another routine so that it can be used in a subclass
        new Query().execute(args);
    }

    /**
     * Main entry point for running the program from the command line via the
     * static {@link #main} method.
     *
     * <p>This method runs the program, catches any {@link
     * CommandExitException}s, and calling {@link System#exit} with
     * {@link CommandExitException#getCode}.</p>
     * @param args List of arguments supplied on operating system command line
     * @since 12.10
     */

    public void execute(String[] args) {
        try {
            doQuery(args);
        } catch (CommandExitException e) {
            System.exit(e.getCode());
        }
    }

    /**
     * Set the options that are recognized on the command line. This method can be overridden in a subclass
     * to define additional command line options.
     *
     * @param options the CommandLineOptions in which the recognized options are to be registered.
     */

    void setPermittedOptions(CommandLineOptions options) {
        options.addRecognizedOption("backup", CommandLineOptions.TYPE_BOOLEAN,
                                    "Save updated documents before overwriting");
        options.addRecognizedOption("catalog", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Use specified catalog file to resolve URIs");
        options.addRecognizedOption("config", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Use specified configuration file");
//        options.addRecognizedOption("cr", CommandLineOptions.TYPE_CLASSNAME | CommandLineOptions.VALUE_REQUIRED,
//                                    "Use specified collection URI resolver class");
        options.addRecognizedOption("dtd", CommandLineOptions.TYPE_ENUMERATION,
                                    "Validate using DTD");
        options.setPermittedValues("dtd", new String[]{"on", "off", "recover"}, "on");
        options.addRecognizedOption("expand", CommandLineOptions.TYPE_BOOLEAN,
                                    "Expand attribute defaults from DTD or Schema");
        options.addRecognizedOption("explain", CommandLineOptions.TYPE_FILENAME,
                                    "Display compiled expression tree and optimization decisions");
        options.addRecognizedOption("ext", CommandLineOptions.TYPE_BOOLEAN,
                                    "Allow calls to Java extension functions and xsl:result-document");
        options.addRecognizedOption("init", CommandLineOptions.TYPE_CLASSNAME,
                                    "User-supplied code to initialize the Saxon configuration");
        options.addRecognizedOption("json", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Source file for primary JSON input");
        options.addRecognizedOption("l", CommandLineOptions.TYPE_BOOLEAN,
                                    "Maintain line numbers for source documents");
        options.addRecognizedOption("mr", CommandLineOptions.TYPE_CLASSNAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Use named ModuleURIResolver class");
        options.addRecognizedOption("ns", CommandLineOptions.TYPE_STRING | CommandLineOptions.VALUE_REQUIRED,
                                    "Default namespace for element names (URI, or ##any, or ##html5)");
        options.addRecognizedOption("now", CommandLineOptions.TYPE_DATETIME | CommandLineOptions.VALUE_REQUIRED,
                                    "Run with specified current date/time");
        options.addRecognizedOption("o", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Use specified file for primary output");
        options.addRecognizedOption("opt", CommandLineOptions.TYPE_STRING | CommandLineOptions.VALUE_REQUIRED,
                                    "Enable/disable optimization options [-]cfgklmnsvwx");
        options.addRecognizedOption("outval", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                                    "Action when validation of output file fails");
        options.setPermittedValues("outval", new String[]{"recover", "fatal"}, null);
        options.addRecognizedOption("p", CommandLineOptions.TYPE_BOOLEAN,
                                    "Recognize query parameters in URI passed to doc()");
        options.addRecognizedOption("q", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Query filename");
        options.addRecognizedOption("qs", CommandLineOptions.TYPE_STRING | CommandLineOptions.VALUE_REQUIRED,
                                    "Query string (usually in quotes)");
        options.addRecognizedOption("quit", CommandLineOptions.TYPE_BOOLEAN | CommandLineOptions.VALUE_REQUIRED,
                                    "Quit JVM if query fails");
        options.addRecognizedOption("qversion", CommandLineOptions.TYPE_STRING | CommandLineOptions.VALUE_REQUIRED,
                                    "XQuery language version: 3.1 or 4.0. Default is 3.1");
        options.setPermittedValues("qversion", new String[]{"3.1", "4.0"}, "3.1");
        options.addRecognizedOption("r", CommandLineOptions.TYPE_CLASSNAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Use named URIResolver class");
        options.addRecognizedOption("repeat", CommandLineOptions.TYPE_INTEGER | CommandLineOptions.VALUE_REQUIRED,
                                    "Run N times for performance measurement");
        options.addRecognizedOption("s", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Source file for primary XML input");
        options.addRecognizedOption("sa", CommandLineOptions.TYPE_BOOLEAN,
                                    "Run in schema-aware mode");
        options.addRecognizedOption("scmin", CommandLineOptions.TYPE_FILENAME,
                                    "Pre-load schema in SCM format");
        options.addRecognizedOption("stream", CommandLineOptions.TYPE_BOOLEAN,
                                    "Execute in streamed mode");
        options.addRecognizedOption("strip", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                                    "Handling of whitespace text nodes in source documents");
        options.setPermittedValues("strip", new String[]{"none", "all", "ignorable"}, null);
        options.addRecognizedOption("t", CommandLineOptions.TYPE_BOOLEAN,
                                    "Display version and timing information");
        options.addRecognizedOption("T", CommandLineOptions.TYPE_CLASSNAME,
                                    "Use named TraceListener class, or standard TraceListener");
        options.addRecognizedOption("TC", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                "Write coverage data to specified output file");
        options.addRecognizedOption("TJ", CommandLineOptions.TYPE_BOOLEAN,
                                    "Debug binding and execution of extension functions");
        options.setPermittedValues("TJ", new String[]{"on", "off"}, "on");
        options.addRecognizedOption("Tlevel", CommandLineOptions.TYPE_STRING,
                                    "Level of detail for trace listener output");
        options.setPermittedValues("Tlevel", new String[]{"none", "low", "normal", "high"}, "normal");
        options.addRecognizedOption("Tout", CommandLineOptions.TYPE_FILENAME,
                                    "File for trace listener output");
        options.addRecognizedOption("TP", CommandLineOptions.TYPE_FILENAME,
                                    "Use profiling trace listener, with specified output file");
        options.addRecognizedOption("TPxsl", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Stylesheet for formatting -TP output");
        options.addRecognizedOption("traceout", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "File for output of trace() messages");
        options.addRecognizedOption("tree", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                                    "Use specified tree model for source documents");
        options.setPermittedValues("tree", new String[]{"linked", "tiny", "tinyc"}, null);
        options.addRecognizedOption("u", CommandLineOptions.TYPE_BOOLEAN,
                                    "Interpret filename arguments as URIs");
        options.setPermittedValues("u", new String[]{"on", "off"}, "on");
        options.addRecognizedOption("update", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                                    "Enable or disable XQuery updates, or enable the syntax but discard the updates");
        options.setPermittedValues("update", new String[]{"on", "off", "discard"}, null);
        options.addRecognizedOption("val", CommandLineOptions.TYPE_ENUMERATION,
                                    "Apply validation to source documents");
        options.setPermittedValues("val", new String[]{"strict", "lax"}, "strict");
        options.addRecognizedOption("wrap", CommandLineOptions.TYPE_BOOLEAN,
                                    "Wrap result sequence in XML elements");
        options.addRecognizedOption("x", CommandLineOptions.TYPE_CLASSNAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Use named XMLReader class for parsing source documents");
        options.addRecognizedOption("xi", CommandLineOptions.TYPE_BOOLEAN,
                                    "Expand XInclude directives in source documents");
        options.addRecognizedOption("xmlversion", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                                    "Indicate whether XML 1.1 is supported");
        options.setPermittedValues("xmlversion", new String[]{"1.0", "1.1"}, null);
        options.addRecognizedOption("xsd", CommandLineOptions.TYPE_FILENAME_LIST | CommandLineOptions.VALUE_REQUIRED,
                                    "List of schema documents to be preloaded");
        options.addRecognizedOption("xsdversion", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                                    "Indicate whether XSD 1.1 is supported");
        options.setPermittedValues("xsdversion", new String[]{"1.0", "1.1"}, null);
        options.addRecognizedOption("xsiloc", CommandLineOptions.TYPE_BOOLEAN,
                                    "Load schemas named in xsi:schemaLocation (default on)");
        options.addRecognizedOption("?", CommandLineOptions.VALUE_PROHIBITED,
                                    "Display command line help text");

    }




    /**
     * Support method for main program. This support method can also be
     * invoked from subclasses that support the same command line interface.
     *
     * <p>In previous Saxon versions (12.9 and earlier) this method could call
     * {@link System#exit} unless the argument {@code "-quit:off"} was passed.
     * Now it throws a {@link CommandExitException} in this case.
     * {@code CommandExitException} extends {@link RuntimeException}, so
     * behaviour with and without {@code "-quit:off"} is functionally identical
     * for API clients now.</p>
     *
     * @param args    the command-line arguments
     */

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void doQuery(String[] args) {

        CommandLineOptions options = new CommandLineOptions();
        setPermittedOptions(options);
        try {
            options.setActualOptions(args);
        } catch (XPathException err) {
            quit(err.getMessage(), 2);
        }


        boolean schemaAware = false;
        String configFile = options.getOptionValue("config");
        if (configFile != null) {
            try {
                config = Configuration.readConfiguration(new StreamSource(configFile));
                initializeConfiguration(config);
                schemaAware = config.isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY);
            } catch (XPathException e) {
                quit(e.getMessage(), 2);
            }
        }

        if (config == null && !schemaAware) {
            schemaAware = options.testIfSchemaAware();
        }

        if (config == null) {
            config = Configuration.newConfiguration();
            initializeConfiguration(config);
        }

        //config.setHostLanguage(Configuration.XQUERY);
        processor = new Processor(config);
        config.setProcessor(processor);

        // Check the command-line arguments.

        try {
            parseOptions(options);

            XQueryCompiler compiler = processor.newXQueryCompiler();
            compiler.setSchemaAware(schemaAware);
            compiler.setLanguageVersion(languageVersion);
            if (externalSchema != null) {
                compiler.useSchema(externalSchema);
            }

            if (updating) {
                compiler.setUpdatingEnabled(true);
            }

            if (moduleURIResolverClass != null) {
                Object mr = config.getInstance(moduleURIResolverClass);
                if (!(mr instanceof ModuleURIResolver)) {
                    badUsage(moduleURIResolverClass + " is not a ModuleURIResolver");
                }
                if (mr instanceof StandardModuleURIResolver) {
                    ((StandardModuleURIResolver) mr).setConfiguration(config);
                }
                compiler.setModuleURIResolver((ModuleURIResolver) mr);
            }

            config.displayLicenseMessage();
            if (schemaAware && !config.isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY)) {
                if ("EE".equals(config.getEditionCode())) {
                    quit("Installed license does not allow schema-aware query", 2);
                } else {
                    quit("Schema-aware query requires Saxon Enterprise Edition", 2);
                }
            }

            if (nsOption != null) {
                if (nsOption.equals("##any")) {
                    compiler.setUnprefixedElementMatchingPolicy(UnprefixedElementMatchingPolicy.ANY_NAMESPACE);
                } else if (nsOption.equals("##html5")) {
                    compiler.declareNamespace("", NamespaceConstant.XHTML);
                    compiler.setUnprefixedElementMatchingPolicy(UnprefixedElementMatchingPolicy.DEFAULT_NAMESPACE_OR_NONE);
                } else {
                    compiler.declareNamespace("", nsOption);
                }
            }

            if (explaining) {
                config.setBooleanProperty(Feature.TRACE_OPTIMIZER_DECISIONS, true);
            }

            compiler.setStreaming(streaming);

            Source sourceInput = null;

            if (sourceXmlFileName != null) {
                sourceInput = processSourceFile(sourceXmlFileName, useURLs);
            }

            if (repeat > 1 && showTime) {
                for (int c=0; c<20; c++) {
                    // Absorb Java start-up cost
                    compileQuery(compiler, queryFileName, useURLs);
                }
            }
            long startTime = System.nanoTime();
            if (showTime) {
                config.getLogger().info("Analyzing query from " + queryFileName);
            }

            // Compile the query

            XQueryExecutable exp = null;
            try {
                exp = compileQuery(compiler, queryFileName, useURLs);

                if (showTime) {
                    long endTime = System.nanoTime();
                    config.getLogger().info("Analysis time: " + ((endTime - startTime) / 1e6) + " milliseconds");
                    startTime = endTime;
                }
            } catch (SaxonApiException e) {
                if (e.getCause() instanceof XPathException) {
                    XPathException err = (XPathException) e.getCause();
                    int line = -1;
                    String module = null;
                    if (err.getLocator() != null) {
                        line = err.getLocator().getLineNumber();
                        module = err.getLocator().getSystemId();
                    }
                    if (err.hasBeenReported()) {
                        quit("Static error(s) in query", 2);
                    } else {
                        if (line == -1) {
                            config.getLogger().error("Static error in query: " + err.getMessage());
                        } else {
                            config.getLogger().error("Static error at line " + line + " of " + module + ':');
                            config.getLogger().error(err.getMessage());
                        }
                    }
                    exp = null;
                    if (allowExit) {
                        System.exit(2);
                    } else {
                        throw new RuntimeException(err.getMessage());
                    }
                } else {
                    quit(e.getMessage(), 2);
                }
            }

            if (explaining && exp != null) {
                Serializer out;
                if (explainOutputFileName == null || explainOutputFileName.equals("")) {
                    out = processor.newSerializer(System.err);
                } else {
                    out = processor.newSerializer(new File(explainOutputFileName));
                }
                out.setOutputProperty(Serializer.Property.METHOD, "xml");
                out.setOutputProperty(Serializer.Property.INDENT, "yes");
                out.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");
                if (processor.equals(processor.getSaxonEdition())) {
                    out.setOutputProperty(Serializer.Property.SAXON_INDENT_SPACES, "2");
                }
                exp.explain(out);
            }

            // Load the source file

            final XQueryEvaluator evaluator = exp.load();
            evaluator.setTraceFunctionDestination(traceDestination);
            if (options.getOptionValue("now") != null) {
                String now = options.getOptionValue("now");
                ConversionResult dt = DateTimeValue.makeDateTimeValue(StringView.tidy(now), config.getConversionRules());
                if (dt instanceof DateTimeValue) {
                    evaluator.getUnderlyingQueryContext().setCurrentDateTime((DateTimeValue) dt);
                } else {
                    config.getLogger().warning("Invalid dateTime: " + now + " (ignored)");
                }
            }

            processSource(sourceInput, exp, evaluator);

            if (sourceJsonFileName != null) {
                try {
                    Reader jsonReader = new BufferedReader(
                                    new InputStreamReader(new FileInputStream(sourceJsonFileName), StandardCharsets.UTF_8));
                    XdmValue jsonTree = parseJson(processor, jsonReader);
                    if (!jsonTree.isEmptySequence()) {
                        evaluator.setContextItem(jsonTree.itemAt(0));
                    }
                } catch (IOException e) {
                    throw new SaxonApiException("Cannot read JSON input: " + e.getMessage());
                }
            }

            // Supply query parameters

            options.setParams(processor, CSharp.methodRef(evaluator::setExternalVariable));

            // Run the query (repeatedly, if the -repeat option was set)

            startTime = System.nanoTime();
            long totalTime = 0;
            int r;
            for (r = 0; r < repeat; r++) {      // repeat is for internal testing/timing
                try {
                    Serializer serializer;
                    if (outputFileName != null) {
                        File outputFile = new File(outputFileName);
                        if (outputFile.isDirectory()) {
                            quit("Output is a directory", 2);
                        }
                        serializer = processor.newSerializer(outputFile);
                    } else {
                        serializer = processor.newSerializer(System.out);
                    }
                    try {
                        options.setSerializationProperties(serializer);
                    } catch (IllegalArgumentException e) {
                        quit(e.getMessage(), 2);
                    }
                    if (updating && exp.isUpdateQuery()) {
                        serializer.setOutputProperties(
                                exp.getUnderlyingCompiledQuery().getExecutable().getPrimarySerializationProperties().getProperties());
                        runUpdate(exp, evaluator, serializer);
                    } else {
                        runQuery(exp, evaluator, sourceInput, serializer);
                    }
                } catch (SaxonApiException err) {
                    if (err.getCause() instanceof XPathException && ((XPathException) err.getCause()).hasBeenReported()) {
                        String category = ((XPathException) err.getCause()).isTypeError() ? "type" : "dynamic";
                        quit("Query failed with " + category + " error: " + err.getCause().getMessage(), 2);
                    } else {
                        throw err;
                    }
                }

                long endTime = System.nanoTime();

                if (r >= 3) {
                    totalTime += endTime - startTime;
                }
                if (showTime) {
                    if (repeat < 100) {
                        config.getLogger().info("Execution time: " + Timer.showExecutionTimeNano(endTime - startTime));
                        config.getLogger().info(Timer.showMemoryUsed());
                        Instrumentation.report();
                    } else if (totalTime > 1000000000000L) {
                        // quit after 1000 seconds
                        break;
                    }
                }
                startTime = endTime;
            }

            if (repeat > 3) {
                config.getLogger().info("Average execution time: " +
                                           Timer.showExecutionTimeNano(totalTime / (r - 3)));
            }

        } catch (TerminationException err) {
            quit(err.getMessage(), 1);
        } catch (SchemaException err) {
            quit("Schema processing failed: " + err.getMessage(), 2);
        } catch (XPathException | LicenseException | SaxonApiException err) {
            quit("Query processing failed: " + err.getMessage(), 2);
        } catch (TransformerFactoryConfigurationError err) {
            err.printStackTrace();
            quit("Query processing failed", 2);
        } catch (CommandExitException err) {
            throw err;
        } catch (Exception err2) {
            err2.printStackTrace();
            quit("Fatal error during query: " + err2.getClass().getName() + ": " +
                         (err2.getMessage() == null ? " (no message)" : err2.getMessage()), 2);
        }
    }

    /**
     * Support method for main program. This support method can also be invoked from subclasses
     * that support the same command line interface
     *
     * @param args the command-line arguments
     * @param command not used, retained for backwards compatibility
     */

    public void doQuery(String[] args, String command) {
        doQuery(args);
    }

    @CSharpModifiers(code={"internal", "static"})
    @CSharpReplaceBody(code="return new Saxon.Api.JsonBuilder(processor.getUnderlyingConfiguration()).ParseJson(jsonReader);")
    static XdmValue parseJson(Processor processor, Reader jsonReader) throws SaxonApiException {
        return processor.newJsonBuilder().parseJson(jsonReader);
    }

    /**
     * Customisation hook called immediately after the Configuration
     * object is instantiated. The intended purpose of this hook is to allow
     * a subclass to supply an OEM license key programmatically, but it can also
     * be used for other initialization of the Configuration. This method is
     * called before analyzing the command line options, so configuration settings
     * made at this stage may be overridden when the command line options are processed.
     * However, if a configuration file is used, the settings defined in the configuration
     * file will have been applied.
     *
     * @param config the Configuration object
     */

    protected void initializeConfiguration(Configuration config) {
        // no action: provided for subclasses to override
    }

    /**
     * Parse the options supplied on the command line
     *
     * @param options the command line arguments
     * @throws SaxonApiException if failures occur. Note, the method may also invoke System.exit().
     */

    void parseOptions(CommandLineOptions options) throws SaxonApiException {

        // Apply those options which simply update the Configuration

        options.applyToConfiguration(processor);

        // Apply options that are processed locally

        allowExit = !"off".equals(options.getOptionValue("quit"));

        backup = "on".equals(options.getOptionValue("backup"));
        explainOutputFileName = CommandLineOptions.coerceImplicitOutputURI(options.getOptionValue("explain"));
        explaining = explainOutputFileName != null;
        moduleURIResolverClass = options.getOptionValue("mr");
        outputFileName = CommandLineOptions.coerceImplicitOutputURI(options.getOptionValue("o"));
        streaming = "on".equals(options.getOptionValue("stream"));

        nsOption = options.getOptionValue("ns");

        String value = options.getOptionValue("p");
        if ("on".equals(value)) {
            config.setBooleanProperty(Feature.RECOGNIZE_URI_QUERY_PARAMETERS, true);
            useURLs = true;
        }

        value = options.getOptionValue("q");
        if (value != null) {
            queryFileName = value;
        }

        value = options.getOptionValue("qs");
        if (value != null) {
            queryFileName = "{" + value + "}";
        }

        String qv = options.getOptionValue("qversion");
        if (qv == null && config.getBooleanProperty(Feature.ALLOW_SYNTAX_EXTENSIONS)) {
            languageVersion = "4.0";
        } else if (qv != null && !"3.1".equals(qv) && !"4.0".equals(qv)) {
            config.getLogger().warning("-qversion ignored: 3.1 is assumed");
        } else if ("4.0".equals(qv)) {
            languageVersion = "4.0";
        }

        value = options.getOptionValue("repeat");
        if (value != null) {
            try {
                repeat = Integer.parseInt(value);
            } catch (NumberFormatException err) {
                badUsage("Bad number after -repeat");
            }
        }

        sourceXmlFileName = options.getOptionValue("s");
        sourceJsonFileName = options.getOptionValue("json");

        value = options.getOptionValue("t");
        if ("on".equals(value)) {
            config.getLogger().info(config.getProductTitle());
            config.getLogger().info(Version.platform.getPlatformVersion());
            config.setTiming(true);
            showTime = true;
        }

        value = options.getOptionValue("traceout");
        if (value != null) {
            switch (value) {
                case "#err":
                    // no action, this is the default
                    break;
                case "#out":
                    traceDestination = StandardLogger.makeLogger(System.out);
                    break;
                case "#null":
                    traceDestination = null;
                    break;
                default:
                    try {
                        traceDestination = StandardLogger.makeLogger(new File(value));
                        closeTraceDestination = true;
                    } catch (FileNotFoundException e) {
                        badUsage("Trace output file " + value + " cannot be created");
                    }
                    break;
            }
        }

        value = options.getOptionValue("T");
        if (value != null) {
            if (value.isEmpty()) {
                makeXQueryTraceListener(options);
            } else {
                config.setTraceListenerClass(value);
            }
            config.setLineNumbering(true);
        }

        value = options.getOptionValue("TC");
        if (value != null) {
            XQueryCoverageCodeInjector injector = new XQueryCoverageCodeInjector();
            CoverageTraceListener listener = new CoverageTraceListener(injector.histograms);
            processor.setConfigurationProperty(Feature.TRACE_LISTENER, listener);
            processor.setConfigurationProperty(Feature.LINE_NUMBERING, true);
            config.setLineNumbering(true);
            config.getDefaultStaticQueryContext().setCodeInjector(injector);
            if (!value.isEmpty()) {
                try {
                    listener.setOutputDestination(
                            StandardLogger.makeLogger(new File(value)));
                } catch (FileNotFoundException e) {
                    throw new SaxonApiException(e);
                }
            }
        }

        value = options.getOptionValue("Tout");
        if (value != null) {
            config.setTraceListenerOutputFile(value);
            if (options.getOptionValue("T") == null) {
                makeXQueryTraceListener(options);
            }
        }

        value = options.getOptionValue("TP");
        if (value != null) {
            TimingTraceListener traceListener = new TimingTraceListener();
            config.setTraceListener(traceListener);
            config.setLineNumbering(true);
            config.getDefaultStaticQueryContext().setCodeInjector(new TimingCodeInjector());
            if (!value.isEmpty()) {
                try {
                    traceListener.setOutputDestination(
                            StandardLogger.makeLogger(new File(value)));
                } catch (FileNotFoundException e) {
                    badUsage("Trace output file " + value + " cannot be created");
                }
            }
            String formatter = options.getOptionValue("TPxsl");
            if (formatter != null) {
                try {
                    if (useURLs) {
                        traceListener.setStylesheet(new URL(formatter));
                    } else {
                        traceListener.setStylesheet(new File(formatter).toURI().toURL());
                    }
                } catch (MalformedURLException e) {
                    System.err.println("Invalid URL " + formatter + " - ignored");
                }
            }
        }

        value = options.getOptionValue("u");
        if (value != null) {
            useURLs = "on".equals(value);
        }

        value = options.getOptionValue("update");
        if (value != null) {
            if (!"off".equals(value)) {
                updating = true;
            }
            writeback = !"discard".equals(value);
        }

        wrap = "on".equals(options.getOptionValue("wrap"));

        value = options.getOptionValue("x");
        if (value != null) {
            config.setSourceParserClass(value);
        }

        value = options.getOptionValue("?");
        if (value != null) {
            badUsage("");
        }

        if (options.getOptionValue("xsiloc") != null && options.getOptionValue("val") == null) {
            config.getLogger().warning("-xsiloc is ignored when -val is absent");
        }

        if (sourceXmlFileName != null && sourceJsonFileName != null) {
            badUsage("Cannot supply both -s and -json");
        }

        // Apply options defined locally in a subclass

        applyLocalOptions(options, config);

        // Apply positional options

        List<String> positional = options.getPositionalOptions();
        int currentPositionalOption = 0;

        if (queryFileName == null) {
            if (positional.size() == currentPositionalOption) {
                badUsage("No query file name");
            }
            queryFileName = positional.get(currentPositionalOption++);
        }

        if (currentPositionalOption < positional.size()) {
            badUsage("Unrecognized option: " + positional.get(currentPositionalOption));
        }

        String scmInput = options.getOptionValue("scmin");
        if (scmInput != null) {
//            PreparedSchema ps = PreparedSchema.fromScm(config, new StreamSource(value));
//            //compiler.useSchema(null);
            throw new UnsupportedOperationException(); // TODO: implement me
        }

        String additionalSchemas = options.getOptionValue("xsd");
        if (additionalSchemas != null) {
            externalSchema = CommandLineOptions.loadAdditionalSchemas(processor, additionalSchemas);
        }
    }

    private void makeXQueryTraceListener(CommandLineOptions options) {
        XQueryTraceListener listener = new XQueryTraceListener();
        String value = options.getOptionValue("Tout");
        if (value != null) {
            try {
                listener.setOutputDestination(StandardLogger.makeLogger(new PrintStream(value)));
            } catch (FileNotFoundException e) {
                badUsage("Cannot write to " + value);
            }
        }
        value = options.getOptionValue("Tlevel");
        if (value != null) {
            switch (value) {
                case "none":
                    listener.setLevelOfDetail(0);
                    break;
                case "low":
                    listener.setLevelOfDetail(1);
                    break;
                case "normal":
                    listener.setLevelOfDetail(2);
                    break;
                case "high":
                    listener.setLevelOfDetail(3);
                    break;
            }
        }
        config.setTraceListener(listener);
    }

    /**
     * Customisation hook: apply options defined locally in a subclass
     *
     * @param options the CommandLineOptions
     * @param config  the Saxon Configuration
     */

    @SuppressWarnings("EmptyMethod")
    @CSharpModifiers(code={"internal"})
    protected void applyLocalOptions(CommandLineOptions options, Configuration config) {
        // no action: provided for subclasses to override
    }

    /*@Nullable*/
    protected Source processSourceFile(String sourceFileName, boolean useURLs) throws TransformerException {
        Source sourceInput;
        if (useURLs || CommandLineOptions.isImplicitURI(sourceFileName)) {
            ResourceRequest request = new ResourceRequest();
            request.relativeUri = sourceFileName;
            URI cwd = CommandLineOptions.getCurrentWorkingDirectory();
            request.baseUri = cwd.toString();
            request.uri = cwd.resolve(sourceFileName).toString();
            request.nature = ResourceRequest.XML_NATURE;
            request.purpose = ResourceRequest.ANY_PURPOSE;
            sourceInput = request.resolve(config.getResourceResolver(), new DirectResourceResolver(config));
        } else if (sourceFileName.equals("-")) {
            // take input from stdin
            String sysId = new File(System.getProperty("user.dir")).toURI().toASCIIString();
            sourceInput = new StreamSource(System.in, sysId);
        } else {
            File sourceFile = new File(sourceFileName);
            if (!sourceFile.exists()) {
                quit("Source file " + sourceFile + " does not exist", 2);
            }

            if (Version.platform.isJava()) {
                InputSource eis = new InputSource(sourceFile.toURI().toString());
                sourceInput = new SAXSource(eis);
            } else {
                sourceInput = new StreamSource(sourceFile.toURI().toString());
            }
        }
        return sourceInput;
    }

    /**
     * Compile the query
     *
     * @param compiler      the XQuery compiler
     * @param queryFileName the filename holding the query (or "-" for the standard input,
     *                      or the actual query text enclosed in curly braces
     * @param useURLs       true if the filename is in the form of a URI
     * @return the compiled query
     * @throws SaxonApiException if query compilation fails
     * @throws IOException       if the query cannot be read
     */

    /*@Nullable*/
    @CSharpModifiers(code={"internal"})
    protected XQueryExecutable compileQuery(XQueryCompiler compiler, String queryFileName, boolean useURLs)
            throws SaxonApiException, IOException {
        XQueryExecutable exp;
        if (queryFileName.equals("-")) {
            Reader queryReader = standardInputReader();
            compiler.setBaseURI(CommandLineOptions.getCurrentWorkingDirectory());
            exp = compiler.compile(queryReader);
        } else if (queryFileName.startsWith("{") && queryFileName.endsWith("}")) {
            // query is inline on the command line
            String q = queryFileName.substring(1, queryFileName.length() - 1);
            compiler.setBaseURI(CommandLineOptions.getCurrentWorkingDirectory());
            exp = compiler.compile(q);
        } else if (useURLs || CommandLineOptions.isImplicitURI(queryFileName)) {
            ModuleURIResolver resolver = compiler.getModuleURIResolver();
            boolean isStandardResolver = false;
            if (resolver == null) {
                resolver = getConfiguration().getStandardModuleURIResolver();
                isStandardResolver = true;
            }
            while (true) {
                String[] locations = {queryFileName};
                Source[] sources;
                try {
                    sources = resolver.resolve(null, null, locations);
                } catch (Exception e) {
                    if (e instanceof XPathException) {
                        throw new SaxonApiException(e);
                    } else {
                        XPathException xe = new XPathException("Exception in ModuleURIResolver: ", "XQST0059");
                        throw new SaxonApiException(xe);
                    }
                }
                if (sources == null) {
                    if (isStandardResolver) {
                        // this should not happen
                        quit("System problem: standard ModuleURIResolver returned null", 4);
                    } else {
                        resolver = getConfiguration().getStandardModuleURIResolver();
                        isStandardResolver = true;
                    }
                } else {
                    if (sources.length != 1 || !(sources[0] instanceof StreamSource)) {
                        quit("Module URI Resolver must return a single StreamSource", 2);
                    }
                    try {
                        String queryText = QueryReader.readSourceQuery(config, (StreamSource) sources[0], config.getValidCharacterChecker());
                        compiler.setBaseURI(new URI(sources[0].getSystemId()));
                        exp = compiler.compile(queryText);
                    } catch (XPathException|URISyntaxException e) {
                        throw new SaxonApiException(e);
                    }
                    break;
                }
            }
        } else {
            //noinspection EmptyFinallyBlock
            try (InputStream queryStream = new FileInputStream(queryFileName)) {
                compiler.setBaseURI(new File(queryFileName).toURI());
                exp = compiler.compile(queryStream);
            } finally {
                // No action (here for C# conversion)
            }
        }
        return exp;
    }

    @CSharpReplaceBody(code="return System.Console.In;")
    private Reader standardInputReader() {
        return new InputStreamReader(System.in);
    }

    /**
     * Explain the results of query compilation
     *
     * @param exp the compiled expression
     * @throws FileNotFoundException if the destination for the explanation doesn't exist
     * @throws XPathException        if other failures occur
     */

    protected void explain(XQueryExpression exp) throws FileNotFoundException, XPathException {
        StreamResult explainOutput;
        if (explainOutputFileName == null || "".equals(explainOutputFileName)) {
            explainOutput = standardErrorResult();
        } else {
            explainOutput = fileResult(explainOutputFileName);
        }
        SerializationProperties props = ExpressionPresenter.makeDefaultProperties(config);
        Receiver diag = config.getSerializerFactory().getReceiver(explainOutput, props);
        ExpressionPresenter expressionPresenter = new ExpressionPresenter(config, diag);
        exp.explain(expressionPresenter);
    }

    private StreamResult standardErrorResult() {
        return new StreamResult(System.err);
    }

    private StreamResult fileResult(String fileName) throws FileNotFoundException {
        return new StreamResult(new FileOutputStream(fileName));
    }

    /**
     * Process the supplied XML source file
     *
     * @param sourceInput the supplied source
     * @param exp         the compiled XQuery expression
     * @param evaluator   the dynamic query context
     * @throws SaxonApiException if processing fails
     */
    @CSharpModifiers(code = {"internal"})
    protected void processSource(/*@Nullable*/ Source sourceInput, XQueryExecutable exp, XQueryEvaluator evaluator) throws SaxonApiException {
        if (sourceInput != null && !streaming) {
            DocumentBuilder builder = processor.newDocumentBuilder();
            if (exp.isUpdateQuery()) {
                builder.setTreeModel(TreeModel.LINKED_TREE);
            } else {
                builder.setTreeModel(TreeModel.getTreeModel(getConfiguration().getTreeModel()));
            }
            if (externalSchema != null) {
                builder.setSchemaValidator(externalSchema.newValidator());
            }
            if (showTime) {
                config.getLogger().info("Processing " + sourceInput.getSystemId());
            }
            if (!exp.getUnderlyingCompiledQuery().usesContextItem()) {
                config.getLogger().warning("Source document ignored - query can be evaluated without reference to the context item");
                return;
            }
            builder.setDTDValidation(getConfiguration().getBooleanProperty(Feature.DTD_VALIDATION));
            if (getConfiguration().getBooleanProperty(Feature.DTD_VALIDATION_RECOVERABLE)) {
                sourceInput = new AugmentedSource(sourceInput, getConfiguration().getParseOptions());
            }
            XdmNode doc = builder.build(sourceInput);
            evaluator.setContextItem(doc);
        }
    }

    /**
     * Run the query
     *
     * @param exp         the compiled query expression
     * @param evaluator   the dynamic query context
     * @param input       the supplied XML source if any
     * @param destination the destination for serialized results
     * @throws SaxonApiException if the query fails
     */
    @CSharpModifiers(code = {"internal"})
    protected void runQuery(XQueryExecutable exp, XQueryEvaluator evaluator, Source input, Destination destination)
            throws SaxonApiException {
        try {
            if (wrap) {
                try {
                    XQueryExpression e = exp.getUnderlyingCompiledQuery();
                    SequenceIterator results = e.iterator(evaluator.getUnderlyingQueryContext());
                    NodeInfo resultDoc = QueryResult.wrap(results, config);
                    XdmValue wrappedResultDoc = XdmValue.wrap(resultDoc);
                    processor.writeXdmValue(wrappedResultDoc, destination);
                    destination.closeAndNotify();
                } catch (XPathException e1) {
                    throw new SaxonApiException(e1);
                }
            } else if (streaming) {
                evaluator.runStreamed(input, destination);
            } else {
                evaluator.run(destination);
            }
        } finally {
            if (closeTraceDestination && traceDestination != null) {
                traceDestination.close();
            }
        }
    }

    /**
     * Run an updating query
     *
     * @param exp        the compiled query expression
     * @param evaluator  the query evaluator
     * @param serializer the destination for serialized results
     * @throws SaxonApiException if the query fails
     */
    @CSharpModifiers(code = {"internal"})
    protected void runUpdate(XQueryExecutable exp, XQueryEvaluator evaluator, final Serializer serializer)
            throws SaxonApiException {

        try {
            if (serializer.getOutputProperty(Serializer.Property.METHOD) == null) {
                serializer.setOutputProperty(Serializer.Property.METHOD, "xml");
            }
            if (writeback) {
                final List<SaxonApiException> errors = new ArrayList<>(3);
                UpdateAgent agent = (node, controller) -> {
                    try {
                        DocumentPool pool = controller.getDocumentPool();
                        String documentURI = pool.getDocumentURI(node);
                        if (documentURI != null) {
                            rewriteToDisk(node, serializer, backup, showTime ? config.getLogger() : null);
                        } else if (showTime) {
                            config.getLogger().warning("Updated document discarded because it was not read using doc()");
                        }
                    } catch (SaxonApiException err) {
                        config.getLogger().warning(err.getMessage());
                        errors.add(err);
                    }
                };
                evaluator.run();
                try {
                    exp.getUnderlyingCompiledQuery().runUpdate(evaluator.getUnderlyingQueryContext(), agent);
                } catch (XPathException e) {
                    throw new SaxonApiException(e);
                }

                if (!errors.isEmpty()) {
                    throw errors.get(0);
                }
            } else {
                try {
                    if (evaluator.getContextItem() != null) {
                        Set<MutableNodeInfo> affectedDocuments =
                                exp.getUnderlyingCompiledQuery().runUpdate(evaluator.getUnderlyingQueryContext());
                        Item initial = evaluator.getContextItem().getUnderlyingValue().head();
                        //noinspection SuspiciousMethodCalls
                        if (initial instanceof NodeInfo && affectedDocuments.contains(initial)) {
                            processor.writeXdmValue(evaluator.getContextItem(), serializer);
                        }
                    }
                } catch (XPathException e) {
                    throw new SaxonApiException(e);
                }
            }
        } finally {
            if (closeTraceDestination && traceDestination != null) {
                traceDestination.close();
            }
        }


    }

    /**
     * Write an updated document back to disk, using the original URI from which it was read
     *
     * @param doc        an updated document. Must be a document node, or a parentless element, or an
     *                   element that has a document node as its parent. The document will be written to the URI
     *                   found in the systemId property of this node.
     * @param serializer serialization properties
     * @param backup     true if the old document at that location is to be copied to a backup file
     * @param logger     destination for progress messages; if null, no progress messages are written
     * @throws SaxonApiException if the document has no known URI, if the URI is not a writable location,
     *                           or if a serialization error occurs.
     */

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void rewriteToDisk(NodeInfo doc, Serializer serializer, boolean backup, Logger logger)
            throws SaxonApiException {
        switch (doc.getNodeKind()) {
            case Type.DOCUMENT:
                // OK
                break;
            case Type.ELEMENT:
                NodeInfo parent = (NodeInfo)doc.getParent();
                if (parent != null && parent.getNodeKind() != Type.DOCUMENT) {
                    throw new SaxonApiException("Cannot rewrite an element node unless it is top-level");
                }
                break;
            default:
                throw new SaxonApiException("Node to be rewritten must be a document or element node");
        }
        String uri = doc.getSystemId();
        if (uri == null || uri.isEmpty()) {
            throw new SaxonApiException("Cannot rewrite a document with no known URI");
        }
        URI u;
        try {
            u = new URI(uri);
        } catch (URISyntaxException e) {
            throw new SaxonApiException("SystemId of updated document is not a valid URI: " + uri);
        }
        File existingFile = new File(u);
        File dir = existingFile.getParentFile();
        if (backup && existingFile.exists()) {
            File backupFile = new File(dir, existingFile.getName() + ".bak");
            if (logger != null) {
                logger.info("Creating backup file " + backupFile);
            }
            boolean success = existingFile.renameTo(backupFile);
            if (!success) {
                throw new SaxonApiException("Failed to create backup file of " + backupFile);
            }
        }
        if (!existingFile.exists()) {
            if (logger != null) {
                logger.info("Creating file " + existingFile);
            }
            try {
                existingFile.createNewFile();
            } catch (IOException e) {
                throw new SaxonApiException("Failed to create new file " + existingFile);
            }
        } else {
            if (logger != null) {
                logger.info("Overwriting file " + existingFile);
            }
        }
        serializer.setOutputFile(existingFile);
        serializer.getProcessor().writeXdmValue(XdmValue.wrap(doc), serializer);
    }

    /**
     * Exit with a message, by throwing an unchecked exception.
     * If this method throws a {@link CommandExitException} then the
     * catching method will call {@link System#exit} using the value
     * from {@link CommandExitException#getCode}.
     *
     * @throws CommandExitException
     * @param message The message to be output
     * @param code    The result code to be returned to the operating
     *                system shell
     */

    protected void quit(String message, int code) {
        if (config != null && config.getLogger() != null) {
            config.getLogger().warning(message);
        } else {
            System.err.println(message);
        }
        if (allowExit) {
            throw new CommandExitException(code, message);
        } else {
            throw new RuntimeException(message);
        }
    }

    /**
     * Report incorrect usage of the command line, with a list of the options and arguments that are available
     *
     * @param message The error message
     */
    protected void badUsage(String message) {
        Logger logger = config.getLogger();
        if (!"".equals(message)) {
            logger.error(message);
        }
        if (!showTime) {
            logger.info(config.getProductTitle());
        }
        logger.info("Usage: see https://www.saxonica.com/documentation/index.html#!using-xquery/commandline");
        logger.info("Format: " + CommandLineOptions.getCommandName(this) + " options params");
        CommandLineOptions options = new CommandLineOptions();
        setPermittedOptions(options);
        logger.info("Options available:" + options.displayPermittedOptions());
        logger.info("Use -XYZ:? for details of option XYZ or --? to list configuration features");
        logger.info("Params: ");
        logger.info("  param=value           Set query string parameter");
        logger.info("  +param=filename       Set query document parameter");
        logger.info("  ?param=expression     Set query parameter using XPath");
        logger.info("  !param=value          Set serialization parameter");
        if (allowExit) {
            System.exit("".equals(message) ? 0 : 2);
        } else {
            throw new RuntimeException(message);
        }
    }

    /**
     * Utility method to create a file if it does not already exist, including creation of any
     * necessary directories named in the file path
     * @param file the file that is required to exist
     * @throws IOException if file creation fails
     */

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void createFileIfNecessary(File file) throws IOException {
        if (!file.exists()) {
            File directory = file.getParentFile();
            if (directory != null && !directory.exists()) {
                directory.mkdirs();
            }
            file.createNewFile();
        }
    }

    private String getCommandName() {
        String s = getClass().getName();
        if (s.equals("cli.Saxon.Cmd.DotNetQuery")) {
            s = "Query";
        }
        return s;
    }
}

