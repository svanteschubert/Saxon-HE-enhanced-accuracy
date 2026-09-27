////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon;

////import com.saxonica.ee.schema.PreparedSchema;

import net.sf.saxon.expr.instruct.GlobalContextRequirement;
import net.sf.saxon.expr.instruct.TerminationException;
import net.sf.saxon.expr.parser.CodeInjector;
import net.sf.saxon.expr.parser.Optionality;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.TreeModel;
import net.sf.saxon.s9api.*;
import net.sf.saxon.str.BMPString;
import net.sf.saxon.style.Compilation;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trace.*;
import net.sf.saxon.trans.Timer;
import net.sf.saxon.trans.*;
import net.sf.saxon.trans.packages.PackageDetails;
import net.sf.saxon.trans.packages.PackageLibrary;
import net.sf.saxon.transpile.CSharp;
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.value.DateTimeValue;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * This <b>Transform</b> class is the command-line entry point to the Saxon XSLT Processor.
 * <p>It is possible to subclass this class to provide a customized command line interface. In writing such
 * a subclass:</p>
 * <ul>
 * <li>The {@link #main} method should instantiate the class and call the {@link #execute} method, passing the
 * argument list. The argument list can be augmented or modified if required: for example, by adding a -config
 * argument to cause the configuration to be initialized from a configuration file.</li>
 * <li>The {@link #initializeConfiguration} method can be implemented to customize the configuration in which
 * the transformation will run.</li>
 * </ul>
 */

public class Transform {

    private Processor processor;
    private XsltCompiler compiler;
    protected boolean useURLs = false;
    protected boolean showTime = false;
    protected int repeat = 1;
    protected String sourceParserName = null;
    protected boolean schemaAware = false;
    protected boolean allowExit = true;
    protected boolean run = true;
    private Logger traceDestination = StandardLogger.makeLogger();
    private boolean closeTraceDestination = false;
    private XsdSchema externalSchema;

    /**
     * Main program, can be used directly from the command line.
     * <p>The format is:</p>
     * <p>java net.sf.saxon.Transform [options] <I>source-file</I> <I>style-file</I> &gt;<I>output-file</I></p>
     * <p>followed by any number of parameters in the form {keyword=value}... which can be
     * referenced from within the stylesheet.</p>
     * <p>This program applies the XSL style sheet in style-file to the source XML document in source-file.</p>
     *
     * @param args List of arguments supplied on operating system command line
     */
    public static void main(String[] args) {
        // the real work is delegated to another routine so that it can be used in a subclass
        new Transform().execute(args);
    }

    /**
     * Main entry point for running the program from the command line via the
     * static {@link #main} method.
     *
     * <p>This method runs the program, catches any {@link
     * CommandExitException}s, and calling {@link System#exit} with the
     * {@code CommandExitException}'s exit code.</p>
     *
     * @param args List of arguments supplied on operating system command line
     * @since 12.10
     */

    public void execute(String[] args) {
        try {
            doTransform(args);
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

    @CSharpModifiers(code = {"internal"})
    public void setPermittedOptions(CommandLineOptions options) {
        options.addRecognizedOption("a", CommandLineOptions.TYPE_BOOLEAN,
                                    "Use <?xml-stylesheet?> processing instruction to identify stylesheet");
        options.addRecognizedOption("catalog", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Use specified catalog file to resolve URIs");
        options.addRecognizedOption("config", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Use specified configuration file");
//        options.addRecognizedOption("cr", CommandLineOptions.TYPE_CLASSNAME | CommandLineOptions.VALUE_REQUIRED,
//                                    "Use specified collection URI resolver class");
        options.addRecognizedOption("dtd", CommandLineOptions.TYPE_ENUMERATION,
                                    "Validate using DTD");
        options.setPermittedValues("dtd", new String[]{"on", "off", "recover"}, "on");
        options.addRecognizedOption("ea", CommandLineOptions.TYPE_BOOLEAN,
                                    "Enable assertions");
        options.addRecognizedOption("expand", CommandLineOptions.TYPE_BOOLEAN,
                                    "Expand attribute defaults from DTD or Schema");
        options.addRecognizedOption("explain", CommandLineOptions.TYPE_FILENAME,
                                    "Display compiled expression tree and optimization decisions in human-readable form");
        options.addRecognizedOption("export", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Export compiled stylesheet to specified SEF file");
        options.addRecognizedOption("ext", CommandLineOptions.TYPE_BOOLEAN,
                                    "Allow calls to Java extension functions and xsl:result-document");
        options.addRecognizedOption("im", CommandLineOptions.TYPE_QNAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Name of initial mode");
        options.addRecognizedOption("init", CommandLineOptions.TYPE_CLASSNAME,
                                    "User-supplied code to initialize the Saxon Configuration");
        options.addRecognizedOption("it", CommandLineOptions.TYPE_QNAME,
                                    "Name of initial template");
        options.addRecognizedOption("jit", CommandLineOptions.TYPE_BOOLEAN,
                                    "Enable just-in-time compilation");
        options.addRecognizedOption("json", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Source JSON file for primary input");
        options.addRecognizedOption("l", CommandLineOptions.TYPE_BOOLEAN,
                                    "Maintain line numbers for source documents");
        options.addRecognizedOption("lib", CommandLineOptions.TYPE_FILENAME_LIST | CommandLineOptions.VALUE_REQUIRED,
                                    "List of file names of library packages used by the stylesheet");
        options.addRecognizedOption("license", CommandLineOptions.TYPE_BOOLEAN,
                                    "Check for local license file");
//        options.addRecognizedOption("m", CommandLineOptions.TYPE_CLASSNAME,
//                                    "Use named class to handle xsl:message output");
        options.addRecognizedOption("nogo", CommandLineOptions.TYPE_BOOLEAN,
                                    "Compile only, no evaluation");
        options.addRecognizedOption("now", CommandLineOptions.TYPE_DATETIME | CommandLineOptions.VALUE_REQUIRED,
                                    "Run with specified current date/time");
        options.addRecognizedOption("ns", CommandLineOptions.TYPE_STRING | CommandLineOptions.VALUE_REQUIRED,
                                    "Default namespace for element names (URI, or ##any, or ##html5)");
        options.addRecognizedOption("o", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Use specified file for primary output");
        options.addRecognizedOption("opt", CommandLineOptions.TYPE_STRING | CommandLineOptions.VALUE_REQUIRED,
                                    "Enable/disable optimization options [-]cfgjklmnrsvwx");
        options.addRecognizedOption("or", CommandLineOptions.TYPE_CLASSNAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Use named OutputURIResolver class");
        options.addRecognizedOption("outval", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                                    "Action when validation of output file fails");
        options.setPermittedValues("outval", new String[]{"recover", "fatal"}, null);
        options.addRecognizedOption("p", CommandLineOptions.TYPE_BOOLEAN,
                                    "Recognize query parameters in URI passed to doc()");
        options.addRecognizedOption("quit", CommandLineOptions.TYPE_BOOLEAN | CommandLineOptions.VALUE_REQUIRED,
                                    "Quit JVM if transformation fails");
        options.addRecognizedOption("r", CommandLineOptions.TYPE_CLASSNAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Use named URIResolver class");
        options.addRecognizedOption("relocate", CommandLineOptions.TYPE_BOOLEAN,
                                    "Produce relocatable packages");
        options.addRecognizedOption("repeat", CommandLineOptions.TYPE_INTEGER | CommandLineOptions.VALUE_REQUIRED,
                                    "Run N times for performance measurement");
        options.addRecognizedOption("s", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Source XML file for primary input");
        options.addRecognizedOption("sa", CommandLineOptions.TYPE_BOOLEAN,
                                    "Run in schema-aware mode");
        options.addRecognizedOption("scmin", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Pre-load schema in SCM format");
        options.addRecognizedOption("strip", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                                    "Handling of whitespace text nodes in source documents");
        options.setPermittedValues("strip", new String[]{"none", "all", "ignorable"}, null);
        options.addRecognizedOption("stublib", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "JSON file holding function signatures, for SaxonJS");
        options.addRecognizedOption("t", CommandLineOptions.TYPE_BOOLEAN,
                                    "Display version and timing information, and names of output files");
        options.addRecognizedOption("target", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                                    "Target Saxon edition for execution via -export");
        options.setPermittedValues("target", new String[]{"EE", "PE", "HE", "JS", "JS2", "JS3"}, null);
        options.addRecognizedOption("T", CommandLineOptions.TYPE_CLASSNAME,
                                    "Use named TraceListener class, or standard TraceListener");
        options.addRecognizedOption("TJ", CommandLineOptions.TYPE_BOOLEAN,
                                    "Debug binding and execution of extension functions");
        options.setPermittedValues("TJ", new String[]{"on", "off"}, "on");
        options.addRecognizedOption("Tlevel", CommandLineOptions.TYPE_STRING,
                                    "Level of detail for trace listener output");
        options.setPermittedValues("Tlevel", new String[]{"none", "low", "normal", "high"}, "normal");
        options.addRecognizedOption("Tout", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "File for trace listener output");
        options.addRecognizedOption("TC", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Write coverage data to specified output file");
        options.addRecognizedOption("TP", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Use profiling trace listener, with specified output file");
        options.addRecognizedOption("TPxsl", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Stylesheet for formatting -TP output");
        options.addRecognizedOption("threads", CommandLineOptions.TYPE_INTEGER | CommandLineOptions.VALUE_REQUIRED,
                                    "Run stylesheet on directory of files divided in N threads");
        options.addRecognizedOption("tree", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                                    "Use specified tree model for source documents");
        options.setPermittedValues("tree", new String[]{"linked", "tiny", "tinyc"}, null);
        options.addRecognizedOption("traceout", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "File for output of trace() and -T output");
        options.addRecognizedOption("u", CommandLineOptions.TYPE_BOOLEAN,
                                    "Interpret filename arguments as URIs");
        options.setPermittedValues("u", new String[]{"on", "off"}, "on");
        options.addRecognizedOption("val", CommandLineOptions.TYPE_ENUMERATION,
                                    "Apply validation to source documents");
        options.setPermittedValues("val", new String[]{"strict", "lax"}, "strict");
        options.addRecognizedOption("versionmsg", CommandLineOptions.TYPE_BOOLEAN,
                                    "No longer used");
        options.addRecognizedOption("warnings", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                                    "No longer used");
        options.setPermittedValues("warnings", new String[]{"silent", "recover", "fatal"}, null);

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
        options.addRecognizedOption("xsl", CommandLineOptions.TYPE_FILENAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Main stylesheet file");
        options.addRecognizedOption("xsltversion", CommandLineOptions.TYPE_ENUMERATION | CommandLineOptions.VALUE_REQUIRED,
                                    "XSLT version: '3.0' or '4.0'");
        options.setPermittedValues("xsltversion", new String[]{"3.0", "4.0"}, null);
        options.addRecognizedOption("y", CommandLineOptions.TYPE_CLASSNAME | CommandLineOptions.VALUE_REQUIRED,
                                    "Use named XMLReader class for parsing stylesheet and schema documents");
        options.addRecognizedOption("?", CommandLineOptions.VALUE_PROHIBITED,
                                    "Display command line help text");

    }

    private static class TransformThread extends Thread {
        private final Transform transform;
        private final File outputDir;
        private final XsltExecutable sheet;
        private final CommandLineOptions options;
        private final List<Source> sources;
        private final int start;

        TransformThread(Transform t, int i, XsltExecutable st, List<Source> s, File out, CommandLineOptions opt) {
            transform = t;
            start = i;
            sheet = st;
            sources = s;
            options = opt;
            outputDir = out;
        }

        public long getStart() {
            return start;
        }

        @Override
        public void run() {
            try {
                processDirectory(transform, sources, sheet, outputDir, options);
            } catch (Exception err) {
                err.printStackTrace();
            }
        }

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

    public void doTransform(String[] args) {

        Configuration config;
        String sourceXmlFileName = null;
        String sourceJsonFileName = null;
        String styleFileName = null;
        File outputFile = null;
        String outputFileName = null;
        boolean useAssociatedStylesheet = false;
        boolean wholeDirectory = false;
        boolean dtdValidation = false;
        String styleParserName = null;
        boolean explain = false;
        boolean export = false;
        String explainOutputFileName = null;
        String exportOutputFileName = null;
        String additionalSchemas = null;
        TraceListener traceListener = null;
        TransformThread[] th = null;
        int threadCount = 0;
        boolean jit = true;


        CommandLineOptions options = new CommandLineOptions();
        setPermittedOptions(options);
        try {
            options.setActualOptions(args);
        } catch (XPathException err) {
            quit(err.getMessage(), 2);
        }


        schemaAware = false;
        String configFileName = options.getOptionValue("config");
        if (configFileName != null) {
            File configFile = new File(configFileName);
            if (!configFile.exists()) {
                quit("Configuration file " + configFileName + " does not exist", 2);
            }
            try {
                config = Configuration.readConfiguration(new StreamSource(configFile.toURI().toString()));
                initializeConfiguration(config);
                processor = new Processor(config);
                schemaAware = config.isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XSLT);
            } catch (XPathException e) {
                quit(e.getMessage(), 2);
            }
        }

        if (processor == null && !schemaAware) {
            schemaAware = options.testIfSchemaAware();
        }

        if (processor == null) {
            processor = new Processor(true);
            config = processor.getUnderlyingConfiguration();
            initializeConfiguration(config);
            //config.setVersionWarning(true);  // unless suppressed by command line options
            try {
                setFactoryConfiguration(schemaAware, null);
                CompilerInfo defaultCompilerInfo = config.getDefaultXsltCompilerInfo();
                defaultCompilerInfo.setSchemaAware(schemaAware);
            } catch (Exception err) {
                err.printStackTrace();
                quit(err.getMessage(), 2);
            }
        }

        config = processor.getUnderlyingConfiguration();

        // Process the named options

        try {

            // Apply those options which simply update the Configuration

            options.applyToConfiguration(processor);

            // Now create the XsltCompiler (it will pick up options from the configuration)

            compiler = processor.newXsltCompiler();

            // Apply options that are processed locally

            allowExit = !"off".equals(options.getOptionValue("quit"));

            useAssociatedStylesheet = "on".equals(options.getOptionValue("a"));

            String value = options.getOptionValue("explain");
            if (value != null) {
                explain = true;
                processor.setConfigurationProperty(Feature.TRACE_OPTIMIZER_DECISIONS, true);
                jit = false;
                compiler.setJustInTimeCompilation(jit);
                if (!"".equals(value)) {
                    explainOutputFileName = CommandLineOptions.coerceImplicitOutputURI(value);
                }
            }
            value = options.getOptionValue("export");
            if (value != null) {
                export = true;
                jit = false;
                compiler.setJustInTimeCompilation(jit);
                compiler.setCompileForExport(true);
                if (!"".equals(value)) {
                    exportOutputFileName = CommandLineOptions.coerceImplicitOutputURI(value);
                }
            }

            value = options.getOptionValue("target");
            if (value != null) {
                compiler.setTargetEdition(value);
            }
            value = options.getOptionValue("relocate");
            if (value != null && !"off".equals(value)) {
                compiler.setRelocatable(true);
            }

            value = options.getOptionValue("jit");
            if (value != null) {
                if ("on".equals(value) && exportOutputFileName == null && run) {
                    if (export) {
                        jit = false;
                        config.getLogger().warning("Warning: -jit:on is ignored when -export:on is set");
                    } else {
                        jit = true;
                    }
                } else if ("off".equals(value)) {
                    jit = false;
                }
                compiler.setJustInTimeCompilation(jit);

            }

            value = options.getOptionValue("lib");
            if (value != null) {
                StringTokenizer st = new StringTokenizer(value, File.pathSeparator);
                Set<File> packs = new HashSet<>();
                while (st.hasMoreTokens()) {
                    String n = st.nextToken();
                    packs.add(new File(n));
                }
                PackageLibrary lib = null;
                try {
                    lib = new PackageLibrary(compiler.getUnderlyingCompilerInfo(), packs);
                } catch (XPathException e) {
                    quit(e.getMessage(), 2);
                }
                compiler.getUnderlyingCompilerInfo().setPackageLibrary(lib);
            }

            value = options.getOptionValue("ns");
            if (value != null) {
                if (value.equals("##any")) {
                    compiler.setUnprefixedElementMatchingPolicy(UnprefixedElementMatchingPolicy.ANY_NAMESPACE);
                } else if (value.equals("##html5")) {
                    compiler.setDefaultElementNamespace(NamespaceConstant.XHTML);
                    compiler.setUnprefixedElementMatchingPolicy(UnprefixedElementMatchingPolicy.DEFAULT_NAMESPACE_OR_NONE);
                } else {
                    compiler.setDefaultElementNamespace(value);
                }
            }

            value = options.getOptionValue("o");
            if (value != null) {
                outputFileName = CommandLineOptions.coerceImplicitOutputURI(value);
            }

            value = options.getOptionValue("nogo");
            if (value != null) {
                if (jit) {
                    config.getLogger().warning("Warning: -jit:on is ignored when -nogo is set");
                }
                run = false;
                compiler.setJustInTimeCompilation(false);
            }

            value = options.getOptionValue("p");
            if ("on".equals(value)) {
                config.setBooleanProperty(Feature.RECOGNIZE_URI_QUERY_PARAMETERS, true);
                useURLs = true;
            }

            value = options.getOptionValue("repeat");
            if (value != null) {
                try {
                    repeat = Integer.parseInt(value);
                } catch (NumberFormatException err) {
                    badUsage("Bad number after -repeat");
                }
            }

            value = options.getOptionValue("s");
            if (value != null) {
                sourceXmlFileName = value;
            }

            value = options.getOptionValue("json");
            if (value != null) {
                sourceJsonFileName = value;
            }

            value = options.getOptionValue("threads");
            if (value != null) {
                threadCount = Integer.parseInt(value);
            }

            value = options.getOptionValue("t");
            if (value != null) {
                config.getLogger().info(config.getProductTitle());
                config.getLogger().info(Version.platform.getPlatformVersion());
                processor.setConfigurationProperty(Feature.TIMING, true);
                showTime = true;
            }

            value = options.getOptionValue("T");
            if (value != null) {

                String out = options.getOptionValue("Tout");
                if (out != null) {
                    config.setTraceListenerOutputFile(out);
                }

                if ("".equals(value)) {
                    traceListener = new XSLTTraceListener();
                    if (out != null) {
                        try {
                            traceListener.setOutputDestination(StandardLogger.makeLogger(new PrintStream(out)));
                        } catch (FileNotFoundException e) {
                            throw new XPathException(e);
                        }
                    }
                } else {
                    traceListener = config.makeTraceListener(value);
                }
                processor.setConfigurationProperty(Feature.TRACE_LISTENER, traceListener);
                processor.setConfigurationProperty(Feature.LINE_NUMBERING, true);

                value = options.getOptionValue("Tlevel");
                if (value != null && traceListener instanceof AbstractTraceListener) {
                    int level = TraceLevel.NONE;
                    try {
                        level = TraceCodeInjector.levelValue(value);
                    } catch (IllegalArgumentException e) {
                        quit("Invalid trace level " + value, 2);
                    }
                    CompilerInfo info = compiler.getUnderlyingCompilerInfo();
                    TraceCodeInjector injector = (TraceCodeInjector) info.getCodeInjector();
                    CodeInjector globalXsltInjector = config.getDefaultXsltCompilerInfo().getCodeInjector();
                    if (globalXsltInjector instanceof XSLTTraceCodeInjector) {
                        // This is to handle the case where the configuration-level options are picked up
                        // in a call of fn:transform - see bug #6498
                        ((XSLTTraceCodeInjector) globalXsltInjector).setTraceLevel(level);
                    }
                    ((AbstractTraceListener) traceListener).setLevelOfDetail(level);
                    injector.setTraceLevel(level);

                }
            }

            value = options.getOptionValue("TC");
            if (value != null) {
                traceListener = new CoverageTraceListener();
                processor.setConfigurationProperty(Feature.TRACE_LISTENER, traceListener);
                processor.setConfigurationProperty(Feature.LINE_NUMBERING, true);
                compiler.setJustInTimeCompilation(false);
                compiler.getUnderlyingCompilerInfo().setCodeInjector(new XSLTTraceCodeInjector());
                if (!value.isEmpty()) {
                    traceListener.setOutputDestination(
                            StandardLogger.makeLogger(new File(value)));
                }
            }

            value = options.getOptionValue("TP");
            if (value != null) {
                traceListener = new TimingTraceListener();
                processor.setConfigurationProperty(Feature.TRACE_LISTENER, traceListener);
                processor.setConfigurationProperty(Feature.LINE_NUMBERING, true);
                compiler.getUnderlyingCompilerInfo().setCodeInjector(new TimingCodeInjector());
                if (!value.isEmpty()) {
                    traceListener.setOutputDestination(
                            StandardLogger.makeLogger(new File(value)));
                }
                String formatter = options.getOptionValue("TPxsl");
                if (formatter != null) {
                    try {
                        if (useURLs) {
                            ((TimingTraceListener) traceListener).setStylesheet(new URL(formatter));
                        } else {
                            ((TimingTraceListener) traceListener).setStylesheet(new File(formatter).toURI().toURL());
                        }
                    } catch (MalformedURLException e) {
                        System.err.println("Invalid URL " + formatter + " - ignored");
                    }
                }
            }

            value = options.getOptionValue("traceout");
            if (value == null) {
                if (traceDestination == null) {
                    traceDestination = config.getLogger();
                }
            } else {
                switch (value) {
                    case "#err":
                        traceDestination = StandardLogger.makeLogger();
                        break;
                    case "#out":
                        traceDestination = StandardLogger.makeLogger(System.out);
                        break;
                    case "#null":
                        traceDestination = null;
                        break;
                    default:
                        traceDestination = StandardLogger.makeLogger(new File(value));
                        closeTraceDestination = true;
                        break;
                }
            }

            value = options.getOptionValue("u");
            if (value != null) {
                useURLs = "on".equals(value);
            }

            value = options.getOptionValue("x");
            if (value != null) {
                sourceParserName = value;
                processor.setConfigurationProperty(Feature.SOURCE_PARSER_CLASS, sourceParserName);
            }

            value = options.getOptionValue("xsd");
            if (value != null) {
                additionalSchemas = value;
            }

            value = options.getOptionValue("xsdversion");
            if (value != null) {
                processor.setConfigurationProperty(Feature.XSD_VERSION, value);
            }

            value = options.getOptionValue("xsl");
            if (value != null) {
                styleFileName = value;
            }

            value = options.getOptionValue("xsltversion");
            if (value != null) {
                compiler.setXsltLanguageVersion(value);
            }

            value = options.getOptionValue("y");
            if (value != null) {
                styleParserName = value;
                processor.setConfigurationProperty(Feature.STYLE_PARSER_CLASS, value);
            }

            value = options.getOptionValue("?");
            if (value != null) {
                badUsage("");
            }


            // Check for a license if appropriate

            if (!config.getEditionCode().equals("HE")) {
                String lic = options.getOptionValue("license");
                if (lic == null || "on".equals(lic)) {
                    config.displayLicenseMessage();
                } else {
                    config.disableLicensing();
                }
            }

            //compilerInfo.setRecoveryPolicy(config.getRecoveryPolicy());

            // Apply options defined locally in a subclass

            applyLocalOptions(options, config);

            // Check consistency of selected options

            if (sourceXmlFileName != null && sourceJsonFileName != null) {
                badUsage("-s and -json options cannot be used together");
            }

            if (options.getOptionValue("it") != null && useAssociatedStylesheet) {
                badUsage("-it and -a options cannot be used together");
            }

            if (sourceJsonFileName != null && useAssociatedStylesheet) {
                badUsage("-json and -a options cannot be used together");
            }

            if (options.getOptionValue("xsiloc") != null && options.getOptionValue("val") == null) {
                config.getLogger().warning("-xsiloc is ignored when -val is absent");
            }

            List<String> positional = options.getPositionalOptions();
            int currentPositionalOption = 0;

            if (run && options.getOptionValue("it") == null && sourceXmlFileName == null && sourceJsonFileName == null) {
                if (positional.size() == currentPositionalOption) {
                    badUsage("No source file name");
                }
                sourceXmlFileName = positional.get(currentPositionalOption++);
            }

            if (!useAssociatedStylesheet && styleFileName == null) {
                if (positional.size() <= currentPositionalOption) {
                    badUsage("No stylesheet file name");
                }
                styleFileName = positional.get(currentPositionalOption++);
            }

            if (currentPositionalOption < positional.size()) {
                badUsage("Unrecognized option: " + positional.get(currentPositionalOption));
            }

            value = options.getOptionValue("scmin");
            if (value != null) {
//                PreparedSchema ps = PreparedSchema.fromScm(config, new StreamSource(value));
//                compiler.useSchema(null);
                throw new UnsupportedOperationException();   // TODO: implement me
            }

            value = options.getOptionValue("stublib");
            if (value != null) {
                compiler.getUnderlyingCompilerInfo().importStubFunctionLibrary(new StreamSource(new File(value)));
            }

            if (additionalSchemas != null) {
                externalSchema = CommandLineOptions.loadAdditionalSchemas(processor, additionalSchemas);
                compiler.useSchema(externalSchema);
            }

            options.applyStaticParams(compiler);

            List<Source> sources = new ArrayList<>();
            if (sourceXmlFileName != null) {
                boolean useSAXSource = sourceParserName != null || dtdValidation;
                wholeDirectory = CommandLineOptions.loadDocuments(sourceXmlFileName, useURLs, processor, useSAXSource, sources);

                sources = preprocess(sources);
                if (wholeDirectory) {
                    if (outputFileName == null) {
                        quit("To process a directory, -o must be specified", 2);
                    } else if (outputFileName.equals(sourceXmlFileName)) {
                        quit("Output directory must be different from input", 2);
                    } else {
                        outputFile = new File(outputFileName);
                        if (!outputFile.isDirectory()) {
                            quit("Input is a directory, but output is not", 2);
                        }
                    }
                }
            }

            if (outputFileName != null && !wholeDirectory) {
                outputFile = new File(outputFileName);
                if (outputFile.isDirectory()) {
                    quit("Output is a directory, but input is not", 2);
                }
            }

            if (useAssociatedStylesheet) {
                if (wholeDirectory) {
                    processDirectoryAssoc(sources, outputFile, options);
                } else {
                    processFileAssoc(sources.get(0), null, outputFile, options);
                }
            } else {

                long startTime = now();

                boolean isURI = useURLs || CommandLineOptions.isImplicitURI(styleFileName);
                XsltExecutable sheet = null;

                Source styleSource = null;

                if (isURI) {
                    ResourceRequest request = new ResourceRequest();
                    request.relativeUri = styleFileName;
                    URI cwd = CommandLineOptions.getCurrentWorkingDirectory();
                    request.baseUri = cwd.toASCIIString();
                    request.uri = cwd.resolve(styleFileName).toString();
                    request.nature = ResourceRequest.XSLT_NATURE;
                    request.purpose = ResourceRequest.ANY_PURPOSE;
                    styleSource = request.resolve(config.getResourceResolver(), new DirectResourceResolver(config));
                } else if (styleFileName.equals("-")) {
                    // take input from stdin
                    String sysId = CommandLineOptions.getCurrentWorkingDirectory().toASCIIString();
                    if (styleParserName != null && Version.platform.isJava()) {
                        XMLReader styleParser = config.getStyleParser();
                        final InputSource inputSource = new InputSource(System.in);
                        inputSource.setSystemId(sysId);
                        styleSource = new SAXSource(styleParser, inputSource);
                    } else {
                        styleSource = new StreamSource(System.in, sysId);
                    }
                } else {
                    PackageLibrary library = config.getDefaultXsltCompilerInfo().getPackageLibrary();
                    PackageDetails details = library.findDetailsForAlias(styleFileName);
                    if (details != null) {
                        XsltPackage pack = compiler.obtainPackageWithAlias(styleFileName);
                        sheet = pack.link();
                    } else {
                        File sheetFile = new File(styleFileName);
                        if (!sheetFile.exists()) {
                            quit("Stylesheet file " + sheetFile + " does not exist", 2);
                        }
                        styleSource = new StreamSource(sheetFile.toURI().toString());
                    }
                }

                if (styleSource == null && sheet == null) {
                    quit("URIResolver for stylesheet file must return a Source", 2);
                }


                if (sheet == null) {
                    if (export && !run) {
                        // bug 4547
                        try {
                            XsltPackage pack = compiler.compilePackage(styleSource);
                            pack.save(new File(exportOutputFileName));
                            if (showTime) {
                                config.getLogger().info("Stylesheet exported to: " + new File(exportOutputFileName).getAbsolutePath());
                            }
                            return;
                        } catch (SaxonApiException err) {
                            quit(err.getMessage(), 2);
                        }
                    }
                    int repeatComp = repeat;
                    if (repeatComp > 20) {
                        repeatComp = 20;
                    }
                    if (repeatComp == 1) {
                        sheet = compiler.compile(styleSource);
                        if (showTime) {
                            long endTime = now();
                            config.getLogger().info("Stylesheet compilation time: " + Timer.showExecutionTimeNano(endTime - startTime));
                        }
                    } else {
                        startTime = now();
                        long totalTime = 0;
                        Logger logger = config.getLogger();
                        int threshold = logger instanceof StandardLogger ? ((StandardLogger)logger).getThreshold() : 0;
                        Consumer<Integer> setLoggingThreshold = logger instanceof StandardLogger
                                ? ((StandardLogger) logger)::setThreshold
                                : level -> {};
                        for (int j = -repeatComp; j < repeatComp; j++) {
                            // Repeat loop is to get reliable performance data
                            if (j == 0) {
                                startTime = now();
                                setLoggingThreshold.accept(threshold);
                                Compilation.TIMING = true;
                            } else if (j < 0) {
                                setLoggingThreshold.accept(Logger.ERROR);
                                Compilation.TIMING = false;
                            }
                            sheet = compiler.compile(styleSource);
                            if (showTime && j >= 0) {
                                long endTime = now();
                                long elapsed = endTime - startTime;
                                config.getLogger().info("Stylesheet compilation time: " + Timer.showExecutionTimeNano(elapsed));
                                startTime = endTime;
                                totalTime += elapsed;
                            }
                        }
                        if (showTime) {
                            config.getLogger().info("Average compilation time: " + Timer.showExecutionTimeNano(totalTime / repeatComp));
                        }
                    }
                    if (schemaAware) {
                        int licenseId = sheet.getUnderlyingCompiledStylesheet().
                                getTopLevelPackage().getLocalLicenseId();
                        config.checkLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XSLT, "schema-aware XSLT", licenseId);
                    }

                }

                if (explain) {
                    Serializer out;
                    if (explainOutputFileName == null) {
                        out = processor.newSerializer(System.err);
                    } else {
                        out = processor.newSerializer(new File(explainOutputFileName));
                    }
                    out.setOutputProperty(Serializer.Property.METHOD, "xml");
                    out.setOutputProperty(Serializer.Property.INDENT, "yes");
                    out.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");
                    if (config.isLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION)) {
                        out.setOutputProperty(Serializer.Property.SAXON_INDENT_SPACES, "2");
                    }
                    sheet.explain(out);
                }
                if (export) {
                    File sef = new File(exportOutputFileName);
                    Query.createFileIfNecessary(sef);
                    sheet.export(new FileOutputStream(sef));
                    if (showTime) {
                        config.getLogger().info("Stylesheet exported to: " + new File(exportOutputFileName).getAbsolutePath());
                    }
                }

                if (run) {

                    try {
                        if (wholeDirectory) {
                            if ((threadCount > 0) && (sources.size() > 1)) {
                                if (threadCount > sources.size()) {
                                    threadCount = sources.size();
                                }

                                //calculate sources per thread
                                int sourcesPerThread = (int) Math.floor(sources.size() / threadCount);

                                //split remainder of sources amongst rem threads
                                int rem = sources.size() % threadCount;
                                th = new TransformThread[threadCount];
                                // long elapsedTime = System.nanoTime();
                                for (int i = 0, j = 0, z = 0; i < sources.size(); j++, i += sourcesPerThread + z) {
                                    z = j < rem ? 1 : 0;  //split remainder of sources amongst rem threads
                                    th[j] = new TransformThread(this, i, sheet, sources.subList(i, i + sourcesPerThread + z), outputFile, options);
                                    th[j].start();
                                }
                                for (TransformThread aTh : th) {
                                    aTh.join();
                                }
                                // elapsedTime = System.nanoTime() - elapsedTime;
                                // System.out.println("Total Elapsed Time: "+elapsedTime/1e6 + "ms");


                            } else {
                                processDirectory(this, sources, sheet, outputFile, options);
                            }
                        } else {
                            Source source = sources == null || sources.isEmpty() ? null : sources.get(0);
                            processFile(source, sheet, outputFile, options);
                        }
                    } finally {
                        if (closeTraceDestination && traceDestination != null) {
                            traceDestination.close();
                        }
                    }

                }
                if (Instrumentation.ACTIVE && !run) {
                    Instrumentation.report();
                    Instrumentation.reset();
                }
            }
        } catch (TerminationException err) {
            quit(err.getMessage(), 1);
        } catch (SaxonApiException err) {
            //err.printStackTrace();
            quit(err.getMessage(), 2);
        } catch (TransformerException err) {
            //err.printStackTrace();
            quit("Transformation failed: " + err.getMessage(), 2);
        } catch (LicenseException err) {
            //err.printStackTrace();
            quit("Transformation failed with license problem: " + err.getMessage(), 2);
        } catch (TransformerFactoryConfigurationError err) {
            //err.printStackTrace();
            quit("Transformation failed with configuration problem: " + err.getMessage(), 2);
        } catch (Exception err2) {
            err2.printStackTrace();
            quit("Fatal error during transformation: " + err2.getClass().getName() + ": " +
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

    public void doTransform(String[] args, String command) {
        doTransform(args);
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
     * Customisation hook called immediately after the Configuration
     * object is instantiated. This hook is retained for backwards
     * compatibility but it is recommended to implement {@link #initializeConfiguration}
     * in preference. This method is called after {@link #initializeConfiguration},
     * but only if the configuration was not created using a configuration file.
     * The default implementation does nothing.
     *
     * @param schemaAware True if the transformation is to be schema-aware
     * @param className   Always null.
     * @throws LicenseException can be thrown if there is no valid license available
     */

    public void setFactoryConfiguration(boolean schemaAware, String className) throws LicenseException {
        // no action
    }

    /**
     * Customisation hook: apply options defined locally in a subclass. This method
     * allows a subclass to recognize and implement command line options that are not recognized
     * by the superclass. To prevent Saxon rejecting such options as errors, the method
     * {@link #setPermittedOptions} must be overridden in the subclass to add details of
     * options recognized in the subclass.
     *
     * @param options the CommandLineOptions. This will contain details of all the options
     *                that were specified on the command line. Those that are recognized by the standard Saxon
     *                command line interface will already have been processed; other options can now be processed
     *                by the subclass.
     * @param config  the Saxon Configuration
     */

    @CSharpModifiers(code = {"internal"})
    protected void applyLocalOptions(CommandLineOptions options, Configuration config) {
        // no action: provided for subclasses to override
    }


    /**
     * Preprocess the list of sources. This method exists so that it can be
     * overridden in a subclass, typically to handle kinds of Source implemented as
     * extensions to the basic Saxon capability
     *
     * @param sources the list of Source objects
     * @return a revised list of Source objects
     * @throws XPathException if a Source object is not recognized
     */

    public List<Source> preprocess(List<Source> sources) throws XPathException {
        return sources;
    }

    /**
     * Get the configuration.
     *
     * @return the Saxon configuration
     */

    protected Configuration getConfiguration() {
        return processor.getUnderlyingConfiguration();
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
        try {
            getConfiguration().getLogger().warning(message);
        } catch (Exception err) {
            System.err.println(message);
        }
        if (allowExit) {
            throw new CommandExitException(code, message);
        } else {
            throw new RuntimeException(message);
        }
    }

    /**
     * Process each file in the source directory using its own associated stylesheet
     *
     * @param sources          The sources in the directory to be processed
     * @param outputDir        The directory in which output files are to be
     *                         created
     * @param options          Command line options
     * @throws Exception when any error occurs during a transformation
     */

    private void processDirectoryAssoc(List<Source> sources, File outputDir,
                                       CommandLineOptions options)
            throws Exception {

        int failures = 0;
        for (Source source : sources) {
            String localName = getLocalFileName(source);
            try {
                processFileAssoc(source, localName, outputDir, options);
            } catch (SaxonApiException err) {
                failures++;
                getConfiguration().getLogger().warning("While processing " + localName +
                                           ": " + err.getMessage() + '\n');
            }
        }
        if (failures > 0) {
            throw new XPathException(failures + " transformation" +
                                             (failures == 1 ? "" : "s") + " failed");
        }
    }

    /**
     * Make an output file in the output directory, with filename extension derived from the
     * media-type produced by the stylesheet
     *
     * @param directory The directory in which the file is to be created
     * @param localName The local name of the file within the
     *                  directory, excluding the file type suffix
     * @param sheet     The Templates object identifying the stylesheet -
     *                  used to determine the output method, and hence the suffix to be
     *                  used for the filename
     * @return The newly created file
     */

    private File makeOutputFile(File directory, String localName, XsltExecutable sheet) {
        String mediaType = sheet.getUnderlyingCompiledStylesheet().getPrimarySerializationProperties().getProperty(OutputKeys.MEDIA_TYPE);
        String suffix = ".xml";
        if ("text/html".equals(mediaType)) {
            suffix = ".html";
        } else if ("text/plain".equals(mediaType)) {
            suffix = ".txt";
        }
        String prefix = localName;
        if (localName.endsWith(".xml") || localName.endsWith(".XML")) {
            prefix = localName.substring(0, localName.length() - 4);
        }
        return new File(directory, prefix + suffix);
    }

    /**
     * Process a single source file using its associated stylesheet(s)
     *
     * @param sourceInput      Identifies the source file to be transformed
     * @param localName        The local name of the file within the
     *                         directory, excluding the file type suffix
     * @param outputFile       The output file to contain the results of the
     *                         transformation
     * @param options          Command line options
     * @throws SaxonApiException If the transformation fails
     */

    private void processFileAssoc(Source sourceInput, String localName, File outputFile,
                                  CommandLineOptions options)
            throws SaxonApiException {
        if (showTime) {
            getConfiguration().getLogger().info("Processing " + sourceInput.getSystemId() + " using associated stylesheet");
        }
        long startTime = now();

        XdmNode sourceDoc = processor.newDocumentBuilder().build(sourceInput);

        Source style = compiler.getAssociatedStylesheet(xdmNodeAsSource(sourceDoc), null, null, null);
        XsltExecutable sheet = compiler.compile(style);

        if (showTime) {
            getConfiguration().getLogger().info("Prepared associated stylesheet " + style.getSystemId());
        }

        Xslt30Transformer transformer = newTransformer(sheet, options);

        File outFile = outputFile;
        if (outFile != null && outFile.isDirectory()) {
            outFile = makeOutputFile(outFile, localName, sheet);
        }

        Serializer serializer =
                outputFile == null ?
                        processor.newSerializer(System.out) :
                        processor.newSerializer(outFile);
        try {
            options.setSerializationProperties(serializer);
        } catch (IllegalArgumentException e) {
            quit(e.getMessage(), 2);
        }

        transformer.setGlobalContextItem(sourceDoc);
        transformer.applyTemplates(sourceDoc, serializer);


        if (showTime) {
            long endTime = now();
            getConfiguration().getLogger().info("Execution time: " + Timer.showExecutionTimeNano(endTime - startTime));
        }
    }

    /**
     * Create a new Transformer. This method is protected so it can be overridden in a subclass, allowing additional
     * options to be set on the Transformer
     *
     * @param sheet            The XsltExecutable object representing the compiled stylesheet
     * @param options          The commmand line options
     * @return the newly constructed Controller to be used for the transformation
     * @throws SaxonApiException if any error occurs
     */

    @CSharpModifiers(code={"internal"})
    protected Xslt30Transformer newTransformer(
            XsltExecutable sheet, CommandLineOptions options) throws SaxonApiException {
        Configuration config = getConfiguration();
        final Xslt30Transformer transformer = sheet.load30();

        transformer.setTraceFunctionDestination(traceDestination);
        String initialMode = options.getOptionValue("im");
        if (initialMode != null) {
            transformer.setInitialMode(QName.fromClarkName(initialMode));
        }

        String now = options.getOptionValue("now");
        if (now != null) {
            try {
                DateTimeValue currentDateTime = (DateTimeValue)DateTimeValue.makeDateTimeValue(
                        BMPString.of(now), config.getConversionRules()).asAtomic();
                transformer.getUnderlyingController().setCurrentDateTime(currentDateTime);
            } catch (XPathException e) {
                throw new SaxonApiException("Failed to set current time: " + e.getMessage(), e);
            }
        }
        // Code to enable/disable assertions at run-time is relevant only when we're running pre-compiled packages
        if ("on".equals(options.getOptionValue("ea"))) {
            transformer.getUnderlyingController().setAssertionsEnabled(true);
        } else if ("off".equals(options.getOptionValue("ea"))) {
            transformer.getUnderlyingController().setAssertionsEnabled(true);
        }

        return transformer;
    }

    /**
     * Get current time in nanoseconds
     *
     * @return the current time in nanoseconds (since VM startup)
     */

    protected static long now() {
        return System.nanoTime();
    }

    /**
     * Process each file in the source directory using the same supplied stylesheet
     *
     * @param sources          The sources in the directory to be processed
     * @param sheet            The Templates object identifying the stylesheet
     * @param outputDir        The directory in which output files are to be
     *                         created
     * @param options          Command line options
     * @throws SaxonApiException when any error occurs during a
     *                           transformation
     */

    private static void processDirectory(Transform t, List<Source> sources, XsltExecutable sheet, File outputDir, CommandLineOptions options)
            throws SaxonApiException {
        int failures = 0;
        for (Source source : sources) {
            String localName = getLocalFileName(source);
            try {
                File outputFile = t.makeOutputFile(outputDir, localName, sheet);
                t.processFile(source, sheet, outputFile, options);
            } catch (SaxonApiException err) {
                failures++;
                t.getConfiguration().getLogger().info("While processing " + localName + ": " + err.getMessage() + '\n');
            }
        }
        if (failures > 0) {
            throw new SaxonApiException(failures + " transformation" +
                                                (failures == 1 ? "" : "s") + " failed");
        }
    }

    private static String getLocalFileName(Source source) {
        try {
            String path = new URI(source.getSystemId()).getPath();
            while (true) {
                int sep = path.indexOf('/');
                if (sep < 0) {
                    return path;
                } else {
                    path = path.substring(sep + 1);
                }
            }
        } catch (URISyntaxException err) {
            throw new IllegalArgumentException(err.getMessage());
        }
    }

    /**
     * Process a single file using a supplied stylesheet
     *
     * @param source           The source XML document to be transformed (maybe null if an initial template
     *                         is specified)
     * @param sheet            The Templates object identifying the stylesheet
     * @param outputFile       The output file to contain the results of the
     *                         transformation
     * @param options          The command line options
     * @throws SaxonApiException If the transformation fails
     */

    @CSharpModifiers(code={"internal"})
    protected void processFile(Source source, XsltExecutable sheet, File outputFile, CommandLineOptions options)
            throws SaxonApiException {

        long totalTime = 0;
        int runs = 0;
        int halfway = repeat / 2 - 1;
        String jsonSource = options.getOptionValue("json");

        for (int r = 0; r < repeat; r++) {      // repeat is for internal testing/timing
            if (showTime) {
                String msg = "Processing ";
                if (source != null) {
                    msg += source.getSystemId();
                } else if (jsonSource != null) {
                    msg += "JSON from " + jsonSource;
                } else {
                    msg += " (no source document)";
                }
                String initialMode = options.getOptionValue("im");
                if (initialMode != null) {
                    msg += " initial mode = " + initialMode;
                }
                String initialTemplateOption = options.getOptionValue("it");
                if (initialTemplateOption != null) {
                    msg += " initial template = " + (initialTemplateOption.isEmpty() ? "xsl:initial-template" : initialTemplateOption);
                }
                getConfiguration().getLogger().info(msg);
            }
            long startTime = now();
            if (r == halfway) {
                runs = 0;
                totalTime = 0;
            }
            runs++;

            if (r < halfway) {
                traceDestination = null;
            }

            Xslt30Transformer transformer = newTransformer(sheet, options);

            Serializer serializer;
            if (outputFile == null) {
                transformer.setBaseOutputURI(CommandLineOptions.getCurrentWorkingDirectory().toASCIIString());
                serializer = processor.newSerializer(System.out);
            } else {
                serializer = processor.newSerializer(outputFile);
            }
            try {
                options.setSerializationProperties(serializer);
            } catch (IllegalArgumentException e) {
                quit(e.getMessage(), 2);
            }

            boolean buildResultTree;
            String buildTreeProperty = serializer.getOutputProperty(Serializer.Property.BUILD_TREE);
            if ("yes".equals(buildTreeProperty)) {
                buildResultTree = true;
            } else if ("no".equals(buildTreeProperty)) {
                buildResultTree = false;
            } else {
                String method = serializer.getOutputProperty(Serializer.Property.METHOD);
                buildResultTree = !("json".equals(method) || "adaptive".equals(method));
            }

            String initialTemplate = options.getOptionValue("it");

            if (source != null) {
                PreparedStylesheet pss = sheet.getUnderlyingCompiledStylesheet();
                GlobalContextRequirement requirement = pss.getGlobalContextRequirement();
                boolean buildSourceTree;
                if (requirement == null) {
                    buildSourceTree = initialTemplate != null ||
                            !transformer.getUnderlyingController().getInitialMode().isDeclaredStreamable();
                } else {
                    buildSourceTree = requirement.getContextValueOptionality() != Optionality.PROHIBITED;
                }
                if (buildSourceTree) {
                    DocumentBuilder builder = processor.newDocumentBuilder();
                    builder.setDTDValidation(getConfiguration().getBooleanProperty(Feature.DTD_VALIDATION));
                    builder.setWhitespaceStrippingPolicy(sheet.getWhitespaceStrippingPolicy());
                    builder.setTreeModel(TreeModel.getTreeModel(getConfiguration().getTreeModel()));
                    if (externalSchema != null) {
                        builder.setSchemaValidator(externalSchema.newValidator());
                    }
                    if (getConfiguration().getBooleanProperty(Feature.DTD_VALIDATION_RECOVERABLE)) {
                        source = new AugmentedSource(source, getConfiguration().getParseOptions());
                    }
                    StylesheetPackage top = pss.getTopLevelPackage();
                    ParseOptions opt = source instanceof AugmentedSource ? ((AugmentedSource)source).getParseOptions() : new ParseOptions();
                    boolean changedOptions = false;
                    if (top.isStripsTypeAnnotations()) {
                        opt = opt.withFilter(
                                receiver -> getConfiguration().getAnnotationStripper(receiver));
                        changedOptions = true;
                    }
                    if (opt.getSchemaValidationMode() != Validation.SKIP && opt.getSchema() == null) {
                        opt = opt.withSchema(pss.getTopLevelPackage().getImportedSchema(""));
                        changedOptions = true;
                    }
                    if (changedOptions) {
                        source = AugmentedSource.makeAugmentedSource(source);
                        ((AugmentedSource) source).setParseOptions(opt);
                    }
                    XdmNode node = builder.build(source);
                    transformer.setGlobalContextItem(node, true);
                    source = xdmNodeAsSource(node);
                }
            }

            XdmItem jsonContextItem = null;
            if (jsonSource != null) {
                try {
                    Reader jsonReader = new BufferedReader(
                            new InputStreamReader(new FileInputStream(jsonSource), StandardCharsets.UTF_8));
                    XdmValue jsonTree = Query.parseJson(processor, jsonReader);
                    if (!jsonTree.isEmptySequence()) {
                        transformer.setGlobalContextItem(jsonContextItem = jsonTree.itemAt(0));
                    }
                } catch (IOException e) {
                    throw new SaxonApiException("Cannot read JSON input: " + e.getMessage());
                }
            }

            options.applyFileParams(processor, transformer);

            if (initialTemplate != null) {
                QName initialTemplateName;
                if (initialTemplate.isEmpty()) {
                    initialTemplateName = new QName("xsl", NamespaceConstant.XSLT, "initial-template");
                } else {
                    initialTemplateName = QName.fromClarkName(initialTemplate);
                }
                if (buildResultTree) {
                    transformer.callTemplate(initialTemplateName, serializer);
                } else {
                    XdmValue result = transformer.callTemplate(initialTemplateName);
                    serializer.serializeXdmValue(result);
                }
            } else if (jsonContextItem != null) {
                if (buildResultTree) {
                    transformer.applyTemplates(jsonContextItem, serializer);
                } else {
                    XdmValue result = transformer.applyTemplates(jsonContextItem);
                    serializer.serializeXdmValue(result);
                }
            } else {
                if (buildResultTree) {
                    transformer.applyTemplates(source, serializer);
                } else {
                    XdmValue result = transformer.applyTemplates(source);
                    serializer.serializeXdmValue(result);
                }
            }

            long endTime = now();
            totalTime += endTime - startTime;
            if (showTime) {
                getConfiguration().getLogger().info("Execution time: " + Timer.showExecutionTimeNano(endTime - startTime));
                getConfiguration().getLogger().info(Timer.showMemoryUsed());
                if (repeat > 1) {
                    getConfiguration().getLogger().info("-------------------------------");
                    Runtime.getRuntime().gc();
                }
                if (Instrumentation.ACTIVE) {
                    CSharp.emitCode("#pragma warning disable 0162");  // Get rid of "unreachable code" warnings
                    Instrumentation.report();
                    Instrumentation.reset();
                    CSharp.emitCode("#pragma warning restore 0162");
                }
            }
            if (repeat == 999999 && totalTime > 60000) {
                break;
            }
        }
        if (repeat > 1) {
            getConfiguration().getLogger().info("*** Average execution time over last " + runs + " runs: " +
                                       Timer.showExecutionTimeNano(totalTime / runs));
        }
    }

    @CSharpReplaceBody(code="return node.Implementation.asActiveSource();")
    private Source xdmNodeAsSource(XdmNode node) {
        return node.asSource();
    }


    /**
     * Report incorrect usage of the command line, with a list of the options and arguments that are available
     *
     * @param message The error message
     */
    protected void badUsage(String message) {
        Logger logger = getConfiguration().getLogger();
        if (!"".equals(message)) {
            logger.error(message);
        }
        if (!showTime) {
            logger.info(getConfiguration().getProductTitle());
        }
        logger.info("Usage: see https://www.saxonica.com/documentation/index.html#!using-xsl/commandline");
        logger.info("Format: " + CommandLineOptions.getCommandName(this) + " options params");
        CommandLineOptions options = new CommandLineOptions();
        setPermittedOptions(options);
        logger.info("Options available:" + options.displayPermittedOptions());
        logger.info("Use -XYZ:? for details of option XYZ");
        logger.info("Params: ");
        logger.info("  param=value           Set stylesheet string parameter");
        logger.info("  +param=filename       Set stylesheet document parameter");
        logger.info("  ?param=expression     Set stylesheet parameter using XPath");
        logger.info("  !param=value          Set serialization parameter");
        if (allowExit) {
            if ("".equals(message)) {
                System.exit(0);
            } else {
                System.exit(2);
            }
        } else {
            throw new RuntimeException(message);
        }
    }
    



}

