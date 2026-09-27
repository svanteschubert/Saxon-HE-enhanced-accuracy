////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.flwor.*;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.SortKeyDefinition;
import net.sf.saxon.functions.*;
import net.sf.saxon.functions.registry.ConstructorFunctionLibrary;
import net.sf.saxon.lib.*;
import net.sf.saxon.ma.arrays.ArrayFunctionSet;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.map.MapFunctionSet;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.UnionQNameTest;
import net.sf.saxon.pattern.qname.AnyQNameTest;
import net.sf.saxon.pattern.qname.QNameTest;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.s9api.UnprefixedElementMatchingPolicy;
import net.sf.saxon.serialize.CharacterMap;
import net.sf.saxon.serialize.CharacterMapIndex;
import net.sf.saxon.serialize.SerializationParamsHandler;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.str.Latin1;
import net.sf.saxon.str.StringTool;
import net.sf.saxon.str.StringView;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.style.AttributeValueTemplate;
import net.sf.saxon.trans.*;
import net.sf.saxon.tree.util.NamespaceResolverWithDefault;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;
import net.sf.saxon.z.IntHashSet;
import net.sf.saxon.z.IntPredicateProxy;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * This class defines extensions to the XPath parser to handle the additional
 * syntax supported in XQuery
 */
public class XQueryParser extends XPathParser {

    private boolean memoFunction = false;
    private boolean streaming = false;
    private int errorCount = 0;
    private XPathException firstError = null;

    protected Executable executable;

    private boolean foundCopyNamespaces = false;
    private boolean foundBoundarySpaceDeclaration = false;
    private boolean foundOrderingDeclaration = false;
    private boolean foundEmptyOrderingDeclaration = false;
    private boolean foundDefaultCollation = false;
    private boolean foundConstructionDeclaration = false;
    private boolean foundDefaultFunctionNamespace = false;
    private boolean foundDefaultElementNamespace = false;
    private boolean foundBaseURIDeclaration = false;
    private boolean foundContextItemDeclaration = false;
    private boolean foundDefaultDecimalFormat = false;
    private boolean foundFunctionDeclaration = false;
    private boolean preambleProcessed = false;

    public final Set<NamespaceUri> importedModules = new HashSet<>(5);
    final List<Import> schemaImports = new ArrayList<>(5);
    final List<Import> moduleImports = new ArrayList<>(5);

    private final Set<StructuredQName> outputPropertiesSeen = new HashSet<>(4);
    private Properties parameterDocProperties;

    private final static Set<String> reservedNames = new HashSet<>(200);
    static {       // Revised by PR2481
        String[] kw = ("and case div else eq except follows follows-or-is "
                       + "for ge gt idiv intersect is is-not le let lt mod ne or otherwise "
                       + "precedes precedes-or-is return satisfies to union where while").split("\\s");

        reservedNames.addAll(Arrays.asList(kw));
    }


    /**
     * Constructor for internal use: this class should be instantiated via the QueryModule
     *
     */

    public XQueryParser(StaticContext env) {
        super(env);
        this.languageVersion = 31; // Until proved otherwise
        setLanguage(ParsedLanguage.XQUERY, languageVersion);
    }

    /**
     * Create a new parser of the same kind
     *
     * @return a new parser of the same kind as this one
     */

    private XQueryParser newParser() {
        XQueryParser qp = new XQueryParser(env);
        qp.setLanguage(language, languageVersion);
        qp.setParserExtension(parserExtension);
        return qp;
    }

    /**
     * Create an XQueryExpression
     *
     * @param query      the source text of the query
     * @param mainModule the static context of the query
     * @param config     the Saxon configuration
     * @return the compiled XQuery expression
     * @throws XPathException if the expression contains static errors
     */

    /*@NotNull*/
    public XQueryExpression makeXQueryExpression(
            /*@NotNull*/ String query,
            /*@NotNull*/ QueryModule mainModule,
            /*@NotNull*/ Configuration config) throws XPathException {
        try {
            setLanguage(ParsedLanguage.XQUERY, languageVersion);
            if (config.getXMLVersion() == Configuration.XML10) {
                query = normalizeLineEndings10(query);
            } else {
                query = normalizeLineEndings11(query);
            }
            Executable exec = mainModule.getExecutable();
            if (exec == null) {
                exec = new Executable(config);
                exec.setHostLanguage(HostLanguage.XQUERY);
                exec.setTopLevelPackage(mainModule.getPackageData());
                setExecutable(exec);
                //mainModule.setExecutable(exec);
            }
            GlobalContextRequirement requirement = exec.getGlobalContextRequirement();
            if (requirement != null) {
                requirement.addRequiredSequenceType(mainModule.getRequiredContextValueType(), true);
            } else {
                if (mainModule.getRequiredContextItemType() != null) {
                    if (mainModule.getRequiredContextItemType() != AnyItemType.INSTANCE) {
                        GlobalContextRequirement req = new GlobalContextRequirement();
                        req.setExternal(true);
                        req.addRequiredSequenceType(mainModule.getRequiredContextValueType(), true);
                        exec.setGlobalContextRequirement(req);
                    }
                }
            }

//            setDefaultContainer(new SimpleContainer(mainModule.getPackageData()));

            Properties outputProps = new Properties(config.getDefaultSerializationProperties());
            if (outputProps.getProperty(OutputKeys.METHOD) == null) {
                outputProps.setProperty(OutputKeys.METHOD, "xml");
            }
            parameterDocProperties = new Properties(outputProps);
            exec.setDefaultOutputProperties(new Properties(parameterDocProperties));

            //exec.setLocationMap(new LocationMap());
            FunctionLibraryList libList = new FunctionLibraryList();
            libList.addFunctionLibrary(new ExecutableFunctionLibrary(config));
            exec.setFunctionLibrary(libList);
            // this will be changed later
            setExecutable(exec);

            setCodeInjector(mainModule.getCodeInjector());

            Expression exp = parseQuery(query, mainModule);

            if (streaming) {
                env.getConfiguration().checkLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY, "streaming", -1);
            }

            exec.fixupQueryModules(mainModule);

            // Make the XQueryExpression object

            XQueryExpression queryExp = config.makeXQueryExpression(exp, mainModule, streaming);

            createRunTimeFunctionLibrary(mainModule, config, exec);

            return queryExp;
        } catch (XPathException e) {
            if (!e.hasBeenReported()) {
                reportError(e);
            }
            throw e;
        }
    }

    public static void createRunTimeFunctionLibrary(QueryModule mainModule, Configuration config, Executable exec) {
        // Make the function library that's available at run-time (e.g. for saxon:evaluate() and function-lookup()).
        // This includes all user-defined functions regardless of which module they are in
        int languageVersion = mainModule.getXPathVersion();
        FunctionLibrary userlib = exec.getFunctionLibrary();
        FunctionLibraryList lib = new FunctionLibraryList();
        lib.addFunctionLibrary(mainModule.getBuiltInFunctionSet());
        lib.addFunctionLibrary(config.getBuiltInExtensionLibraryList(languageVersion));
        lib.addFunctionLibrary(new ConstructorFunctionLibrary(config));
        lib.addFunctionLibrary(config.getIntegratedFunctionLibrary());
        lib.addFunctionLibrary(mainModule.getGlobalFunctionLibrary());
        config.addExtensionBinders(lib);
        lib.addFunctionLibrary(userlib);
        exec.setFunctionLibrary(lib);
    }

    /**
     * Normalize line endings in the source query, according to the XML 1.1 rules.
     *
     * @param in the input query
     * @return the query with line endings normalized
     */

    private static String normalizeLineEndings11(/*@NotNull*/ String in) {
        if (in.indexOf((char) 0xd) < 0 && in.indexOf((char) 0x85) < 0 && in.indexOf((char) 0x2028) < 0) {
            return in;
        }
        StringBuilder sb = new StringBuilder(in.length());
        for (int i = 0; i < in.length(); i++) {
            char ch = in.charAt(i);
            switch (ch) {
                case (char) 0x85, (char) 0x2028 -> sb.append((char) 0xa);
                case (char) 0xd -> {
                    if (i < in.length() - 1 && (in.charAt(i + 1) == (char) 0xa || in.charAt(i + 1) == (char) 0x85)) {
                        sb.append((char) 0xa);
                        i++;
                    } else {
                        sb.append((char) 0xa);
                    }
                }
                default -> sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * Normalize line endings in the source query, according to the XML 1.0 rules.
     *
     * @param in the input query
     * @return the query text with line endings normalized
     */

    private static String normalizeLineEndings10(/*@NotNull*/ String in) {
        if (in.indexOf((char) 0xd) < 0) {
            return in;
        }
        StringBuilder sb = new StringBuilder(in.length());
        for (int i = 0; i < in.length(); i++) {
            char ch = in.charAt(i);
            if (ch == 0xd) {
                if (i < in.length() - 1 && in.charAt(i + 1) == (char) 0xa) {
                    sb.append((char) 0xa);
                    i++;
                } else {
                    sb.append((char) 0xa);
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }


    /**
     * Get the executable containing this expression.
     *
     * @return the executable
     */

    /*@NotNull*/
    public Executable getExecutable() {
        return executable;
    }

    /**
     * Set the executable used for this query expression
     *
     * @param exec the executable
     */

    public void setExecutable(/*@NotNull*/ Executable exec) {
        executable = exec;
    }


    /**
     * Callback to tailor the tokenizer
     */

    @Override
    protected void customizeTokenizer(Tokenizer t) {
        t.isXQuery = true;
    }


    /**
     * Say whether the query should be compiled and evaluated to use streaming.
     * This affects subsequent calls on the parseQuery() method. This option requires
     * Saxon-EE.
     *
     * @param option if true, the compiler will attempt to compile a query to be
     *               capable of executing in streaming mode. If the query cannot be streamed,
     *               a compile-time exception is reported. In streaming mode, the source
     *               document is supplied as a stream, and no tree is built in memory. The default
     *               is false.
     * @since 9.6
     */

    public void setStreaming(boolean option) {
        streaming = option;
    }

    /**
     * Ask whether the streaming option has been set, that is, whether
     * subsequent calls on parseQuery() will compile queries to be capable
     * of executing in streaming mode.
     *
     * @return true if the streaming option has been set.
     * @since 9.6
     */

    public boolean isStreaming() {
        return streaming;
    }


    /**
     * Parse a top-level Query.
     * Prolog? Expression
     *
     * @param queryString The text of the query
     * @param env         The static context
     * @return the Expression object that results from parsing
     * @throws net.sf.saxon.trans.XPathException if the expression contains a syntax error
     */

    /*@NotNull*/
    private Expression parseQuery(String queryString, QueryModule env) throws XPathException {
        this.env = Objects.requireNonNull(env);
        charChecker = env.getConfiguration().getValidCharacterChecker();
//        if (defaultContainer == null) {
//            defaultContainer = new TemporaryContainer(env.getConfiguration(), env.getLocationMap(), 1);
//        }
        language = ParsedLanguage.XQUERY;
        t = new Tokenizer();
        t.languageLevel = languageVersion = env.getXPathVersion();
        t.isXQuery = true;
        try {
            t.tokenize(Objects.requireNonNull(queryString), 0, -1);
        } catch (XPathException err) {
            grumble(err.getMessage());
        }

        parseVersionDeclaration();
        t.allowSaxonExtensions =
                        languageVersion >= 40
                                || env.getConfiguration().getBooleanProperty(Feature.ALLOW_SYNTAX_EXTENSIONS);

        env.setXPathVersion(languageVersion);
        env.initializeFunctionLibraries();
        QNameParser qp = new QNameParser(env.getLiveNamespaceResolver())
                .withAcceptEQName(true, languageVersion)
                .withUnescaper(new Unescaper(env.getConfiguration().getValidCharacterChecker()));

        setQNameParser(qp);

        parseProlog();
        processPreamble();

        Expression exp = parseExpression();
        exp = makeTracer(exp, null);

        // Diagnostic code - show the expression before any optimizations
//        ExpressionPresenter ep = ExpressionPresenter.make(env.getConfiguration());
//        exp.explain(ep);
//        ep.close();
        // End of diagnostic code

        if (t.currentToken != Token.EOF) {
            grumble("Unexpected token " + currentTokenDisplay() + ": no further input expected");
        }
        setLocation(exp);
        ExpressionTool.setDeepRetainedStaticContext(exp, env.makeRetainedStaticContext());
        if (errorCount == 0) {
            return exp;
        } else {
            XPathException err = new XPathException("One or more static errors were reported during query analysis");
            err.setHasBeenReported(true);
            err.setErrorCodeQName(firstError.getErrorCodeQName());   // largely for the XQTS test driver
            throw err;
        }
    }

    /**
     * Parse a library module.
     * Prolog? Expression
     *
     * @param queryString The text of the library module.
     * @param env         The static context. The result of parsing
     *                    a library module is that the static context is populated with a set of function
     *                    declarations and variable declarations. Each library module must have its own
     *                    static context objext.
     * @throws XPathException if the expression contains a syntax error
     */

    public final void parseLibraryModule(String queryString, /*@NotNull*/ QueryModule env)
            throws XPathException {
        this.env = env;
        final Configuration config = env.getConfiguration();
        charChecker = config.getValidCharacterChecker();
        if (config.getXMLVersion() == Configuration.XML10) {
            queryString = normalizeLineEndings10(queryString);
        } else {
            queryString = normalizeLineEndings11(queryString);
        }
        Executable exec = env.getExecutable();
        if (exec == null) {
            throw new IllegalStateException("Query library module has no associated Executable");
        }
        executable = exec;
//        defaultContainer = new SimpleContainer(env.getPackageData());
        t = new Tokenizer();
        t.languageLevel = languageVersion = env.getXPathVersion();
        t.isXQuery = true;

        try {
            t.tokenize(queryString, 0, -1);
        } catch (XPathException err) {
            grumble(err.getMessage());
        }
        parseVersionDeclaration();
        t.allowSaxonExtensions =
                        languageVersion >= 40 || env.getConfiguration().getBooleanProperty(Feature.ALLOW_SYNTAX_EXTENSIONS);

        env.setXPathVersion(languageVersion);
        QNameParser qp = new QNameParser(env.getLiveNamespaceResolver())
                .withAcceptEQName(true, languageVersion)
                .withUnescaper(new Unescaper(config.getValidCharacterChecker()));
        setQNameParser(qp);
        env.initializeFunctionLibraries();
        int startOffset = t.currentTokenStartOffset;
        if (!isKeywordPair("module", "namespace")) {
            if (t.currentToken == Token.EOF) {
                grumble("The file imported for module " + env.getModuleNamespace() +
                                (queryString.trim().length() == 0 ? " is empty" : " has no significant content"));
            } else {
                grumble("The file imported for module " + env.getModuleNamespace() + " is not a valid XQuery library module. " +
                                "The content starts: " + Err.truncate30(StringView.of(queryString.substring(startOffset))));
            }
        }
        parseModuleDeclaration();
        parseProlog();
        processPreamble();
        if (t.currentToken != Token.EOF) {
            grumble("Unrecognized content found after the variable and function declarations in a library module");
        }
        if (errorCount != 0) {
            XPathException err = new XPathException("Static errors were reported in the imported library module");
            err.setErrorCodeQName(firstError.getErrorCodeQName());
            throw err;
        }
    }


    private void reportError(/*@NotNull*/ XPathException exception) throws XPathException {
        errorCount++;
        if (firstError == null) {
            firstError = exception;
        }
        ((QueryModule) env).reportStaticError(exception);
        throw exception;
    }

    private static final Pattern encNamePattern = Pattern.compile("^[A-Za-z]([A-Za-z0-9._\\x2D])*$");

    /**
     * Parse the version declaration if present.
     *
     * @throws XPathException in the event of a syntax error.
     */
    private void parseVersionDeclaration() throws XPathException {
        if (tryKeywordPair("xquery", "version")) {
            String queryVersion = unescape(expectStringLiteral());
            String[] allowedVersions = new String[]{"1.0", "3.0", "3.1", "4.0"};
            if (Arrays.binarySearch(allowedVersions, queryVersion) < 0) {
                grumble("Invalid XQuery version " + queryVersion, "XQST0031");
            }
            int declaredVersion = switch (queryVersion) {
                case "1.0" -> 20;  // XQuery 1.0 == XPath 2.0
                case "3.0" -> 30;
                case "3.1" -> 31;
                case "4.0" -> 40;
                default -> 31;
            };
            languageVersion = Integer.max(languageVersion, declaredVersion);
            env.getPackageData().setHostLanguage(HostLanguage.XQUERY, languageVersion);
            nextToken();
            if (isKeyword("encoding")) {
                nextToken();
                String enc = expectStringLiteral();
                if (!encNamePattern.matcher(unescape(enc)).matches()) {
                    grumble("Encoding name contains invalid characters", "XQST0087");
                }
                // we ignore the encoding now: it was handled earlier, while decoding the byte stream
                nextToken();
            }
            readToken(Token.SEMICOLON);
        } else {
            if (tryKeywordPair("xquery", "encoding")) {
                String enc = expectStringLiteral();
                if (!encNamePattern.matcher(unescape(enc)).matches()) {
                    grumble("Encoding name contains invalid characters", "XQST0087");
                }
                // we ignore the encoding now: it was handled earlier, while decoding the byte stream
                nextToken();
                readToken(Token.SEMICOLON);
            }
        }
    }

    /**
     * In a library module, parse the module declaration
     * Syntax: &lt;"module" "namespace"&gt; prefix "=" uri ";"
     *
     * @throws XPathException in the event of a syntax error.
     */

    private void parseModuleDeclaration() throws XPathException {
        readKeyword("module");
        readKeyword("namespace");
        String prefix = readName();
        readToken(Token.EQUALS);
        String ns = expectStringLiteral();
        NamespaceUri uri = NamespaceUri.of(uriLiteral(ns));
        checkProhibitedPrefixes(prefix, uri);
        if (uri.isEmpty()) {
            grumble("Module namespace cannot be \"\"", "XQST0088");
            uri = NamespaceUri.of("http://saxon.fallback.namespace/");   // for error recovery
        }
        nextToken();
        readToken(Token.SEMICOLON);
        try {
            ((QueryModule) env).setModuleNamespace(uri);
            ((QueryModule) env).declarePrologNamespace(prefix, uri);
            executable.addQueryLibraryModule((QueryModule) env);
        } catch (XPathException err) {
            err.setLocator(makeLocation());
            reportError(err);
        }
    }

    /**
     * Parse the query prolog. This method, and its subordinate methods which handle
     * individual declarations in the prolog, cause the static context to be updated
     * with relevant context information. On exit, t.currentToken is the first token
     * that is not recognized as being part of the prolog.
     *
     * @throws XPathException in the event of a syntax error.
     */

    private void parseProlog() throws XPathException {
        //boolean allowSetters = true;
        boolean allowModuleDecl = true;
        boolean allowDeclarations = true;

        while (true) {
            try {
                if (tryKeywordPair("module", "namespace")) {
                    NamespaceUri uri = ((QueryModule) env).getModuleNamespace();
                    if (uri == null) {
                        grumble("Module declaration must not be used in a main module");
                    } else {
                        grumble("Module declaration appears more than once");
                    }
                    if (!allowModuleDecl) {
                        grumble("Module declaration must precede other declarations in the query prolog");
                    }
                }
                allowModuleDecl = false;
                if (tryKeyword("declare")) {
                    if (tryKeyword("namespace")) {
                        if (!allowDeclarations) {
                            grumble("Namespace declarations cannot follow variables, functions, or options");
                        }
                        parseNamespaceDeclaration();
                    } else if (t.currentToken == Token.PERCENT) {
                        // we have read "declare %"
                        processPreamble();
                        if (allowDeclarations) {
                            allowDeclarations = false;
                        }
                        AnnotationList annotationList = parseAnnotationsList();
                        if (isKeyword("function")) {
                            annotationList.check(env.getConfiguration(), "DF");
                            parseFunctionDeclaration(annotationList);
                        } else if (isKeyword("variable")) {
                            annotationList.check(env.getConfiguration(), "DV");
                            parseVariableDeclaration(annotationList);
                        } else if (tryKeyword("type")) {
                            annotationList.check(env.getConfiguration(), "DI");
                            parseItemTypeDeclaration(annotationList);
                        } else {
                            grumble("Annotations can appear only in 'declare variable' and 'declare function'");
                        }
                    } else if (tryKeyword("fixed")) {
                        checkLanguageVersion40("a fixed default namespace");
                        readKeyword("default");
                        String genre = expectName();
                        switch (genre) {
                            case "element":
                                if (!allowDeclarations) {
                                    grumble("Namespace declarations cannot follow variables, functions, or options");
                                }
                                parseDefaultElementNamespace(true);
                                break;
                            case "function":
                                if (!allowDeclarations) {
                                    grumble("Namespace declarations cannot follow variables, functions, or options");
                                }
                                parseDefaultFunctionNamespace();
                                break;
                            default:
                                grumble("After 'declare fixed default', expected 'element' or 'function'");
                                break;
                        }
                    } else if (tryKeyword("default")) {
                        String genre = expectName();
                        switch (genre) {
                            case "element":
                                if (!allowDeclarations) {
                                    grumble("Namespace declarations cannot follow variables, functions, or options");
                                }
                                //allowSetters = false;
                                parseDefaultElementNamespace(false);
                                break;
                            case "function":
                                if (!allowDeclarations) {
                                    grumble("Namespace declarations cannot follow variables, functions, or options");
                                }
                                //allowSetters = false;
                                parseDefaultFunctionNamespace();
                                break;
                            case "collation":
                                if (!allowDeclarations) {
                                    grumble("Collation declarations must appear earlier in the prolog");
                                }
                                parseDefaultCollation();
                                break;
                            case "order":
                                if (!allowDeclarations) {
                                    grumble("Order declarations must appear earlier in the prolog");
                                }
                                parseDefaultOrder();
                                break;
                            case "decimal-format":
                                nextToken();
                                parseDefaultDecimalFormat();
                                break;
                            default:
                                grumble("After 'declare default', expected 'element', 'function', or 'collation'");
                                break;
                        }
                    } else if (tryKeyword("boundary-space")) {
                        if (!allowDeclarations) {
                            grumble("'declare boundary-space' must appear earlier in the query prolog");
                        }
                        parseBoundarySpaceDeclaration();
                    } else if (tryKeyword("ordering")) {
                        if (!allowDeclarations) {
                            grumble("'declare ordering' must appear earlier in the query prolog");
                        }
                        parseOrderingDeclaration();
                    } else if (tryKeyword("copy-namespaces")) {
                        if (!allowDeclarations) {
                            grumble("'declare copy-namespaces' must appear earlier in the query prolog");
                        }
                        parseCopyNamespacesDeclaration();
                    } else if (tryKeyword("base-uri")) {
                        if (!allowDeclarations) {
                            grumble("'declare base-uri' must appear earlier in the query prolog");
                        }
                        parseBaseURIDeclaration();
                    } else if (tryKeyword("decimal-format")) {
                        if (!allowDeclarations) {
                            grumble("'declare decimal-format' must appear earlier in the query prolog");
                        }
                        parseDecimalFormatDeclaration();
                    } else if (isKeyword("variable")) {
                        //allowSetters = false;
                        if (allowDeclarations) {
                            allowDeclarations = false;
                        }
                        processPreamble();
                        parseVariableDeclaration(AnnotationList.EMPTY);
                    } else if (tryKeyword("context")) {
                        if (allowDeclarations) {
                            allowDeclarations = false;
                        }
                        processPreamble();
                        parseContextItemDeclaration();
                    } else if (isKeyword("function")) {
                        if (allowDeclarations) {
                            allowDeclarations = false;
                        }
                        processPreamble();
                        parseFunctionDeclaration(AnnotationList.EMPTY);
                    } else if (tryKeyword("updating")) {
                        expectKeyword("function");
                        if (allowDeclarations) {
                            allowDeclarations = false;
                        }
                        processPreamble();
                        parserExtension.parseUpdatingFunctionDeclaration(this);
                    } else if (tryKeyword("option")) {
                        if (allowDeclarations) {
                            allowDeclarations = false;
                        }
                        parseOptionDeclaration();
                    } else if (tryKeyword("type")) {
                        checkSyntaxExtensions("declare type");
                        if (allowDeclarations) {
                            allowDeclarations = false;
                        }
                        parseItemTypeDeclaration(AnnotationList.EMPTY);
                    } else if (tryKeyword("construction")) {
                        if (!allowDeclarations) {
                            grumble("'declare construction' must appear earlier in the query prolog");
                        }
                        parseConstructionDeclaration();
                    } else if (tryKeyword("revalidation")) {
                        if (!allowDeclarations) {
                            grumble("'declare revalidation' must appear earlier in the query prolog");
                        }
                        parserExtension.parseRevalidationDeclaration(this);
                    } else {
                        grumble("Unknown keyword " + t.currentToken + " after 'declare'");
                    }
                } else if (tryKeyword("import")) {
                    if (tryKeyword("schema")) {
                        if (!allowDeclarations) {
                            grumble("Import schema must appear earlier in the prolog");
                        }
                        parseSchemaImport();
                    } else if (tryKeyword("module")) {
                        if (!allowDeclarations) {
                            grumble("Import module must appear earlier in the prolog");
                        }
                        parseModuleImport();
                    } else {
                        grumble("Unknown keyword " + t.currentToken + " after 'import'");
                    }
                } else if (t.currentToken == Token.EOF) {
                    NamespaceUri uri = ((QueryModule) env).getModuleNamespace();
                    if (uri == null) {
                        grumble("The main module must contain a query expression after any declarations in the prolog");
                    } else {
                        return;
                    }
                } else {
                    return;
                }
                readToken(Token.SEMICOLON);
            } catch (XPathException err) {
                if (err.getLocator() == null) {
                    err.setLocator(makeLocation());
                }
                if (!err.hasBeenReported()) {
                    errorCount++;
                    if (firstError == null) {
                        firstError = err;
                    }
                    reportError(err);
                }
                // we've reported an error, attempt to recover by skipping to the
                // next semicolon
                while (t.currentToken != Token.SEMICOLON) {
                    nextToken();
                    if (t.currentToken == Token.EOF) {
                        return;
//                    } else if (t.currentToken == Token.START_TAG) {
//                        parsePseudoXML(true);
                    }
                }
                nextToken();
            }
        }
    }

    /**
     * Parse the annotations that can appear in a variable or function declaration
     *
     * @return the annotations as a list
     * @throws XPathException in the event of a syntax error
     */

    @Override
    protected AnnotationList parseAnnotationsList() throws XPathException {
        // we have read "declare" and have seen "%" as lookahead
        assert t.currentToken == Token.PERCENT;
        ArrayList<Annotation> annotations = new ArrayList<>();
        while (true) {
            nextToken();
            String name = expectName();
            StructuredQName qName = makeStructuredQName(name, NamespaceUri.XQUERY);
            assert qName != null;
            NamespaceUri uri = qName.getNamespaceUri();
            Annotation annotation = new Annotation(qName);
            if (uri.equals(NamespaceUri.XQUERY)) {
                if (!qName.equals(Annotation.PRIVATE) && !qName.equals(Annotation.PUBLIC) &&
                        !qName.equals(Annotation.UPDATING) && !qName.equals(Annotation.SIMPLE)) {
                    grumble("Unrecognized variable or function annotation " + qName.getDisplayName(), "XQST0045");
                }
            } else if (isReservedInQuery(uri)) {
                grumble("The annotation " + t.currentName() + " is in a reserved namespace", "XQST0045");
            } else {
                // no action - ignore namespaced annotations
            }
            nextToken();
            if (t.currentToken == Token.LPAREN) {
                nextToken();
                if (t.currentToken == Token.RPAREN) {
                    grumble("Annotation parameter list cannot be empty");
                }
                while (true) {

                    AtomicValue arg = parseConstant();


                    annotation.addAnnotationParameter(arg);

                    //nextToken();
                    if (t.currentToken == Token.RPAREN) {
                        nextToken();
                        break;
                    }
                    readToken(Token.COMMA);
                }
            }
            annotations.add(annotation);
            if (t.currentToken != Token.PERCENT) {
                return new AnnotationList(annotations).withConfiguration(env.getConfiguration());
            }
        }
    }


    /**
     * Method called once the setters have been read to do tidying up that can't be done until we've got
     * to the end
     *
     * @throws XPathException if parsing fails
     */

    private void processPreamble() throws XPathException {
        if (preambleProcessed) {
            return;
        }
        preambleProcessed = true;
        if (foundDefaultCollation) {
            String collationName = env.getDefaultCollationName();
            URI collationURI;
            try {
                collationURI = new URI(collationName);
                if (!collationURI.isAbsolute()) {
                    URI base = new URI(env.getStaticBaseURI());
                    collationURI = base.resolve(collationURI);
                    collationName = collationURI.toString();
                }
            } catch (URISyntaxException err) {
                grumble("Default collation name '" + collationName + "' is not a valid URI", "XQST0046");
                collationName = NamespaceConstant.CODEPOINT_COLLATION_URI;
            }
            if (env.getConfiguration().getCollation(collationName) == null) {
                grumble("Default collation name '" + collationName + "' is not a recognized collation", "XQST0038");
                collationName = NamespaceConstant.CODEPOINT_COLLATION_URI;
            }
            ((QueryModule) env).setDefaultCollationName(collationName);
        }

        if (!schemaImports.isEmpty()) {
            applySchemaImports(schemaImports);
        }

        boolean reuseFunctionLibrary = !foundFunctionDeclaration && moduleImports.size() == 1;

        for (Import imp : moduleImports) {
            try {
                applyModuleImport(imp, reuseFunctionLibrary);
            } catch (XPathException err) {
                if (!err.hasBeenReported()) {
                    throw err.maybeWithLocation(makeLocation(imp.offset));
                }
            }
        }
    }

    private void parseDefaultCollation() throws XPathException {
        // <"default" "collation"> StringLiteral
        if (foundDefaultCollation) {
            grumble("default collation appears more than once", "XQST0038");
        }
        foundDefaultCollation = true;
        nextToken();
        String uri = uriLiteral(expectStringLiteral());
        ((QueryModule) env).setDefaultCollationName(uri);
        nextToken();
    }

    /**
     * Parse "declare default order empty (least|greatest)"
     *
     * @throws XPathException if parsing fails
     */
    private void parseDefaultOrder() throws XPathException {
        if (foundEmptyOrderingDeclaration) {
            grumble("empty ordering declaration appears more than once", "XQST0069");
        }
        foundEmptyOrderingDeclaration = true;
        nextToken();
        if (!isKeyword("empty")) {
            grumble("After 'declare default order', expected keyword 'empty'");
        }
        nextToken();
        if (isKeyword("least")) {
            ((QueryModule) env).setEmptyLeast(true);
        } else if (isKeyword("greatest")) {
            ((QueryModule) env).setEmptyLeast(false);
        } else {
            grumble("After 'declare default order empty', expected keyword 'least' or 'greatest'");
        }
        nextToken();
    }

    /**
     * Parse the "declare xmlspace" declaration.
     * Syntax: &lt;"declare" "boundary-space"&gt; ("preserve" | "strip")
     *
     * @throws XPathException if a static error is encountered
     */

    private void parseBoundarySpaceDeclaration() throws XPathException {
        if (foundBoundarySpaceDeclaration) {
            grumble("'declare boundary-space' appears more than once", "XQST0068");
        }
        foundBoundarySpaceDeclaration = true;
        String action = readName();
        if ("preserve".equals(action)) {
            ((QueryModule) env).setPreserveBoundarySpace(true);
        } else if ("strip".equals(action)) {
            ((QueryModule) env).setPreserveBoundarySpace(false);
        } else {
            grumble("boundary-space must be 'preserve' or 'strip'");
        }
    }

    /**
     * Parse the "declare ordering" declaration.
     * Syntax: &lt;"declare" "ordering"&gt; ("ordered" | "unordered")
     *
     * @throws XPathException if parsing fails
     */

    private void parseOrderingDeclaration() throws XPathException {
        if (foundOrderingDeclaration) {
            grumble("ordering mode declaration appears more than once", "XQST0065");
        }
        foundOrderingDeclaration = true;
        String ordering = readName();
        if (!"ordered".equals(ordering) && !"unordered".equals(ordering)) {
            grumble("ordering mode must be 'ordered' or 'unordered'");
        }
    }

    /**
     * Parse the "declare copy-namespaces" declaration.
     * Syntax: &lt;"declare" "copy-namespaces"&gt; ("preserve" | "no-preserve") "," ("inherit" | "no-inherit")
     *
     * @throws XPathException if a static error is encountered
     */

    private void parseCopyNamespacesDeclaration() throws XPathException {
        if (foundCopyNamespaces) {
            grumble("declare copy-namespaces appears more than once", "XQST0055");
        }
        foundCopyNamespaces = true;
        String action = readName();
        if ("preserve".equals(action)) {
            ((QueryModule) env).setPreserveNamespaces(true);
        } else if ("no-preserve".equals(action)) {
            ((QueryModule) env).setPreserveNamespaces(false);
        } else {
            grumble("copy-namespaces must be followed by 'preserve' or 'no-preserve'");
        }
        readToken(Token.COMMA);
        action = readName();
        if ("inherit".equals(action)) {
            ((QueryModule) env).setInheritNamespaces(true);
        } else if ("no-inherit".equals(action)) {
            ((QueryModule) env).setInheritNamespaces(false);
        } else {
            grumble("After the comma in the copy-namespaces declaration, expected 'inherit' or 'no-inherit'");
        }
    }


    /**
     * Parse the "declare construction" declaration.
     * Syntax: &lt;"declare" "construction"&gt; ("preserve" | "strip")
     *
     * @throws XPathException if parsing fails
     */

    private void parseConstructionDeclaration() throws XPathException {
        if (foundConstructionDeclaration) {
            grumble("declare construction appears more than once", "XQST0067");
        }
        foundConstructionDeclaration = true;
        String action = readName();
        int val;
        if ("preserve".equals(action)) {
            val = Validation.PRESERVE;
        } else if ("strip".equals(action)) {
            val = Validation.STRIP;
        } else {
            grumble("construction mode must be 'preserve' or 'strip'");
            val = Validation.STRIP;
        }
        ((QueryModule) env).setConstructionMode(val);
    }

    /**
     * Parse the "declare revalidation" declaration.
     * Syntax: not allowed unless XQuery update is in use
     *
     * @throws XPathException if the syntax is incorrect, or is not allowed in this XQuery processor
     */

    protected void parseRevalidationDeclaration() throws XPathException {
        grumble("declare revalidation is allowed only in XQuery Update");
    }

    /**
     * Parse (and process) the schema import declaration.
     * SchemaImport ::=	"import" "schema" SchemaPrefix? URILiteral ("at" URILiteral ("," URILiteral)*)?
     * SchemaPrefix ::=	("namespace" NCName "=") | ("default" "element" "namespace")
     *
     * @throws XPathException if parsing fails
     */

    private void parseSchemaImport() throws XPathException {
        ensureSchemaAware("import schema");
        Import sImport = new Import();
        String prefix = null;
        sImport.namespaceURI = null;
        sImport.locationURIs = new ArrayList<>(5);
        sImport.offset = t.currentTokenStartOffset;
        boolean fixedDefault = false;
        if (isKeyword("namespace")) {
            prefix = readNamespaceBinding();
        } else {
            if (tryKeyword("fixed")) {
                checkLanguageVersion40("a fixed namespace");
                fixedDefault = true;
            }
            if (tryKeyword("default")) {
                readKeyword("element");
                readKeyword("namespace");
                prefix = "";
            }
        }
        if (t.currentToken instanceof Token.StringLiteral) {
            NamespaceUri uri = NamespaceUri.of(uriLiteral(expectStringLiteral()));
            checkProhibitedPrefixes(prefix, uri);
            sImport.namespaceURI = uri;
            nextToken();
            if (tryKeyword("at")) {
                sImport.locationURIs.add(uriLiteral(expectStringLiteral()));
                nextToken();
                while (t.currentToken == Token.COMMA) {
                    nextToken();
                    sImport.locationURIs.add(uriLiteral(expectStringLiteral()));
                    nextToken();
                }
            } else if (t.currentToken != Token.SEMICOLON) {
                grumble("After the target namespace URI, expected 'at' or ';'");
            }
        } else {
            grumble("After 'import schema', expected 'namespace', 'default', or a string-literal");
        }
        if (prefix != null) {
            try {
                if (prefix.isEmpty()) {
                    ((QueryModule) env).setDefaultElementNamespace(sImport.namespaceURI, fixedDefault);
                } else {
                    if (sImport.namespaceURI == null || sImport.namespaceURI.isEmpty()) {
                        grumble("A prefix cannot be bound to the null namespace", "XQST0057");
                    }
                    ((QueryModule) env).declarePrologNamespace(prefix, sImport.namespaceURI);
                }
            } catch (XPathException err) {
                err.setLocator(makeLocation());
                reportError(err);
            }
        }
        for (Import schemaImport : schemaImports) {
            if (schemaImport.namespaceURI.equals(sImport.namespaceURI)) {
                grumble("Schema namespace '" + sImport.namespaceURI + "' is imported more than once", "XQST0058");
                break;
            }
        }

        schemaImports.add(sImport);

    }

    private String readNamespaceBinding() throws XPathException {
        nextToken();
        String prefix = readName();
        readToken(Token.EQUALS);
        return prefix;
    }

    protected void ensureSchemaAware(String featureName) throws XPathException {
        if (!env.getConfiguration().isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY)) {
            throw new XPathException("This Saxon version and license does not allow use of '" + featureName + "'", "XQST0009");
        }
        env.getConfiguration().checkLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY, featureName, -1);
        getExecutable().setSchemaAware(true);
        getStaticContext().getPackageData().setSchemaAware(true);
    }

//    private void applySchemaImport(/*@NotNull*/ Import sImport) throws XPathException {
//
//        // Do the importing
//
//        Configuration config = env.getConfiguration();
//        //noinspection SynchronizationOnLocalVariableOrMethodParameter
//        synchronized(config) {
//            PackageData pack = env.getPackageData();
//            if (pack.getImportedSchema().includesTargetNamespace(sImport.namespaceURI)) {
//                // Schema was preloaded
//                ((QueryModule) env).addImportedSchema(sImport.namespaceURI, env.getStaticBaseURI(), sImport.locationURIs);
//                return;
//            }
//            parserExtension.processSchemaImports(sImport.namespaceURI, sImport.locationURIs, env.getStaticBaseURI(), (QueryModule)env);
//
//            ((QueryModule) env).addImportedSchema(sImport.namespaceURI, env.getStaticBaseURI(), sImport.locationURIs);
//        }
//    }


    public void applySchemaImports(List<Import> schemaImports) throws XPathException {
        parserExtension.processSchemaImports((QueryModule)env, schemaImports);
    }

    /**
     * Parse (and expand) the module import declaration.
     * Syntax: "import" "module" ("namespace" NCName "=")? uri ("at" uri ("," uri)*)? ";"
     *
     * @throws net.sf.saxon.trans.XPathException if a static error is encountered
     */

    private void parseModuleImport() throws XPathException {
        QueryModule thisModule = (QueryModule) env;
        Import mImport = new Import();
        String prefix = null;
        mImport.namespaceURI = null;
        mImport.locationURIs = new ArrayList<>(5);
        mImport.offset = t.currentTokenStartOffset;
        if (isKeyword("namespace")) {
            prefix = readNamespaceBinding();
        }
        if (t.currentToken instanceof Token.StringLiteral) {
            NamespaceUri uri = NamespaceUri.of(uriLiteral(expectStringLiteral()));
            checkProhibitedPrefixes(prefix, uri);
            mImport.namespaceURI = uri;
            if (mImport.namespaceURI.isEmpty()) {
                grumble("Imported module namespace cannot be \"\"", "XQST0088");
                mImport.namespaceURI = NamespaceUri.of("http://saxon.fallback.namespace/line" + t.getLineNumber());   // for error recovery
            }
            if (importedModules.contains(mImport.namespaceURI)) {
                grumble("Two 'import module' declarations specify the same module namespace", "XQST0047");
            }
            importedModules.add(mImport.namespaceURI);
            ((QueryModule) env).addImportedNamespace(mImport.namespaceURI);
            nextToken();
            if (isKeyword("at")) {
                do {
                    nextToken();
                    mImport.locationURIs.add(uriLiteral(expectStringLiteral()));
                    nextToken();
                } while (t.currentToken == Token.COMMA);
            }
        } else {
            grumble("After 'import module', expected 'namespace' or a string-literal");
        }
        if (prefix != null) {
            try {
                if (mImport.namespaceURI.equals(thisModule.getModuleNamespace()) &&
                        mImport.namespaceURI.equals(thisModule.checkURIForPrefix(prefix))) {
                    // then do nothing: a duplicate declaration in this situation is not an error
                } else {
                    thisModule.declarePrologNamespace(prefix, mImport.namespaceURI);
                }
            } catch (XPathException err) {
                err.setLocator(makeLocation());
                reportError(err);
            }
        }

        moduleImports.add(mImport);
    }

    private void applyModuleImport(/*@NotNull*/ Import mImport, boolean reuseFunctionLibrary) throws XPathException {
        List<QueryModule> existingModules;

        // resolve the location URIs against the base URI
        for (int i = 0; i < mImport.locationURIs.size(); i++) {
            try {
                String uri = mImport.locationURIs.get(i);
                URI abs = ResolveURI.makeAbsolute(uri, env.getStaticBaseURI());
                mImport.locationURIs.set(i, abs.toString());
            } catch (URISyntaxException e) {
                grumble("Invalid URI " + mImport.locationURIs.get(i) + ": " + e.getMessage(), "XQST0046", mImport.offset);
            }
        }

        // See if the URI is that of a separately-compiled query library
        QueryLibrary lib = ((QueryModule) env).getUserQueryContext().getCompiledLibrary(mImport.namespaceURI);
        if (lib != null) {
            executable.addQueryLibraryModule(lib);
            existingModules = new ArrayList<>();
            existingModules.add(lib);
            lib.link((QueryModule) env);

        } else if (!env.getConfiguration().getBooleanProperty(Feature.XQUERY_MULTIPLE_MODULE_IMPORTS)) {
            // Unless this configuration option is set, if we already know a module with the right module URI, then we
            // use it irrespective of its location URI.
            List<QueryModule> list = executable.getQueryLibraryModules(mImport.namespaceURI);
            if (list != null && !list.isEmpty()) {
                ((QueryModule) env).addImportedModule(list.get(0));
                return;
            }
        } else {

            for (int h = mImport.locationURIs.size() - 1; h >= 0; h--) {
                if (executable.isQueryLocationHintProcessed(mImport.locationURIs.get(h))) {
                    mImport.locationURIs.remove(h);
                }
            }

        }

        // If there are no location URIs left, and we already know a module with the right module URI.

        if (mImport.locationURIs.isEmpty()) {
            List<QueryModule> list = executable.getQueryLibraryModules(mImport.namespaceURI);
            if (list != null && !list.isEmpty()) {
                for (QueryModule target : list) {
                    ((QueryModule) env).addImportedModule(target);
                }
                return;
            }
        }

        // Call the module URI resolver to find the remaining modules

        ModuleURIResolver resolver = ((QueryModule) env).getUserQueryContext().getModuleURIResolver();

        String[] hints = new String[mImport.locationURIs.size()];
        for (int h = 0; h < hints.length; h++) {
            hints[h] = mImport.locationURIs.get(h);
        }
        StreamSource[] sources = null;
        if (resolver != null) {
            try {
                sources = resolver.resolve(mImport.namespaceURI.toString(), env.getStaticBaseURI(), hints);
            } catch (XPathException err) {
                grumble("Failed to resolve URI of imported module: " + err.getMessage(), "XQST0059", mImport.offset);
            }
        }
        if (sources == null) {
            resolver = env.getConfiguration().getStandardModuleURIResolver();
            sources = resolver.resolve(mImport.namespaceURI.toString(), env.getStaticBaseURI(), hints);
        }

        for (String hint : mImport.locationURIs) {
            executable.addQueryLocationHintProcessed(hint);
        }

        if (sources.length == 0) {
            grumble("Unable to locate query module " + mImport.namespaceURI, "XQST0059", mImport.offset);
        }

        for (int m = 0; m < sources.length; m++) {
            StreamSource ss = sources[m];
            String baseURI = ss.getSystemId();
            if (baseURI == null) {
                if (m < hints.length) {
                    baseURI = hints[m];
                } else {
                    baseURI = env.getStaticBaseURI();
                    //grumble("No base URI available for imported module", "XQST0059", mImport.offset);
                }
                ss.setSystemId(baseURI);
            }

            // Although the module hadn't been loaded when we started, it might have been loaded since, as
            // a result of a reference from another imported module.
            // TODO: use similar logic when loading schema modules
            existingModules = executable.getQueryLibraryModules(mImport.namespaceURI);
            boolean loaded = false;
            if (existingModules != null && m < hints.length) {
                for (QueryModule existingModule : existingModules) {
                    URI uri = existingModule.getLocationURI();
                    if (uri != null && uri.toString().equals(mImport.locationURIs.get(m))) {
                        loaded = true;
                        break;
                    }
                }
            }
            if (loaded) {
                break;
            }

            try {
                String queryText = QueryReader.readSourceQuery(env.getConfiguration(), ss, charChecker);
                try {
                    if (ss.getInputStream() != null) {
                        ss.getInputStream().close();
                    } else if (ss.getReader() != null) {
                        ss.getReader().close();
                    }
                } catch (IOException e) {
                    throw new XPathException("Failure while closing file for imported query module");
                }
                QueryModule.makeQueryModule(
                        baseURI, executable, (QueryModule) env, queryText, mImport.namespaceURI
                );
            } catch (XPathException err) {
                reportError(err.maybeWithLocation(makeLocation()));
            }
        }
    }

    /**
     * Parse the Base URI declaration.
     * Syntax: &lt;"declare" "base-uri"&gt; uri-literal
     *
     * @throws XPathException if a static error is found
     */

    private void parseBaseURIDeclaration() throws XPathException {
        if (foundBaseURIDeclaration) {
            grumble("Base URI Declaration may only appear once", "XQST0032");
        }
        foundBaseURIDeclaration = true;
        String uri = uriLiteral(expectStringLiteral());
        try {
            // if the supplied URI is relative, try to resolve it
            URI baseURI = new URI(uri);
            if (!baseURI.isAbsolute()) {
                String oldBase = env.getStaticBaseURI();
                uri = ResolveURI.makeAbsolute(uri, oldBase).toString();
            }
            ((QueryModule) env).setBaseURI(uri);
        } catch (URISyntaxException err) {
            // The spec says this "is not intrinsically an error", but can cause a failure later
            ((QueryModule) env).setBaseURI(uri);
        }
        nextToken();
    }


    /**
     * Parse a named decimal format declaration.
     * "declare" "decimal-format" QName (property "=" string-literal)*
     *
     * @throws XPathException if parsing fails
     */

    private void parseDecimalFormatDeclaration() throws XPathException {
        String name = readName();
        StructuredQName formatName = makeStructuredQName(name, NamespaceUri.NULL);
        if (env.getDecimalFormatManager().getNamedDecimalFormat(formatName) != null) {
            grumble("Duplicate declaration of decimal-format " + formatName.getDisplayName(), "XQST0111");
        }
        parseDecimalFormatProperties(formatName);
    }

    /**
     * Parse a default decimal format declaration
     * "declare" "default" "decimal-format" (property "=" string-literal)*
     *
     * @throws XPathException if parsing fails
     */

    private void parseDefaultDecimalFormat() throws XPathException {
        if (foundDefaultDecimalFormat) {
            grumble("Duplicate declaration of default decimal-format", "XQST0111");
        }
        foundDefaultDecimalFormat = true;
        parseDecimalFormatProperties(null);
    }

    private void parseDecimalFormatProperties(/*@Nullable*/ StructuredQName formatName) throws XPathException {
        int outerOffset = t.currentTokenStartOffset;
        DecimalFormatManager dfm = env.getDecimalFormatManager();
        DecimalSymbols dfs = formatName == null ? dfm.getDefaultDecimalFormat() : dfm.obtainNamedDecimalFormat(formatName);
        dfs.setHostLanguage(HostLanguage.XQUERY, 31);
        Set<String> propertyNames = new HashSet<>(10);
        while (t.currentToken != Token.SEMICOLON) {
            int offset = t.currentTokenStartOffset;
            String propertyName = expectName();
            if (propertyNames.contains(propertyName)) {
                grumble("Property name " + propertyName + " is defined more than once", "XQST0114", offset);
            }
            nextToken();
            readToken(Token.EQUALS);
            UnicodeString propertyValue = StringView.of(unescape(expectStringLiteral()));
            nextToken();
            propertyNames.add(propertyName);
            dfs.setProperty(propertyName,propertyValue);
        }


        try {
            dfs.checkConsistency(formatName);
        } catch (XPathException err) {
            grumble(err.getMessage(), "XQST0098", outerOffset);
        }

    }

    /**
     * Parse the "default function namespace" declaration.
     * Syntax: &lt;"declare" "default" "function" "namespace"&gt; StringLiteral
     *
     * @throws XPathException to indicate a syntax error
     */

    private void parseDefaultFunctionNamespace() throws XPathException {
        if (foundDefaultFunctionNamespace) {
            grumble("default function namespace appears more than once", "XQST0066");
        }
        foundDefaultFunctionNamespace = true;
        nextToken();
        readKeyword("namespace");
        NamespaceUri uri = NamespaceUri.of(uriLiteral(expectStringLiteral()));
        if (uri.equals(NamespaceUri.XML) || uri.equals(NamespaceUri.XMLNS)) {
            grumble("Reserved namespace used as default element/type namespace", "XQST0070");
        }
        ((QueryModule) env).setDefaultFunctionNamespace(uri);
        nextToken();
    }

    /**
     * Parse the "default element namespace" declaration.
     * Syntax: &lt;"declare" "default" "element" "namespace"&gt; StringLiteral
     *
     * @throws XPathException to indicate a syntax error
     */

    private void parseDefaultElementNamespace(boolean isFixedDefault) throws XPathException {
        if (foundDefaultElementNamespace) {
            grumble("default element namespace appears more than once", "XQST0066");
        }
        foundDefaultElementNamespace = true;
        nextToken();
        readKeyword("namespace");
        String rawUri = expectStringLiteral();
        if (rawUri.equals("##any")) {
            ((QueryModule) env).setUnprefixedElementMatchingPolicy(UnprefixedElementMatchingPolicy.ANY_NAMESPACE);
        } else {
            NamespaceUri uri = NamespaceUri.of(uriLiteral(rawUri));
            if (uri.equals(NamespaceUri.XML) || uri.equals(NamespaceUri.XMLNS)) {
                grumble("Reserved namespace used as default element/type namespace", "XQST0070");
            }
            ((QueryModule) env).setDefaultElementNamespace(uri, isFixedDefault);
        }
        nextToken();
    }

    /**
     * Parse a namespace declaration in the Prolog.
     * Syntax: &lt;"declare" "namespace"&gt; NCName "=" StringLiteral
     *
     * @throws XPathException if parsing fails or a static error is found
     */

    private void parseNamespaceDeclaration() throws XPathException {
        String prefix = readName();
        if (!NameChecker.isValidNCName(prefix)) {
            grumble("Invalid namespace prefix " + Err.wrap(prefix));
        }
        readToken(Token.EQUALS);
        NamespaceUri uri = NamespaceUri.of(uriLiteral(expectStringLiteral()));
        checkProhibitedPrefixes(prefix, uri);
        if ("xml".equals(prefix)) {
            // disallowed here even if bound to the correct namespace - erratum XQ.E19
            grumble("Namespace prefix 'xml' cannot be declared", "XQST0070");
        }
        try {
            ((QueryModule) env).declarePrologNamespace(prefix, uri);
        } catch (XPathException err) {
            err.setLocator(makeLocation());
            reportError(err);
        }
        nextToken();
    }

    /**
     * Check that a namespace declaration does not use a prohibited prefix or URI (xml or xmlns)
     *
     * @param prefix the prefix to be tested
     * @param uri    the URI being declared
     * @throws XPathException if the prefix is prohibited
     */

    private void checkProhibitedPrefixes(String prefix, NamespaceUri uri) throws XPathException {
        if (prefix != null && !prefix.isEmpty() && !NameChecker.isValidNCName(prefix)) {
            grumble("The namespace prefix " + Err.wrap(prefix) + " is not a valid NCName");
        }
        if (prefix == null) {
            prefix = "";
        }
        if (uri == null) {
            uri = NamespaceUri.NULL;
        }
        if ("xmlns".equals(prefix)) {
            grumble("The namespace prefix 'xmlns' cannot be redeclared", "XQST0070");
        }
        if (uri.equals(NamespaceUri.XMLNS)) {
            grumble("The xmlns namespace URI is reserved", "XQST0070");
        }
        if (uri.equals(NamespaceUri.XML) && !prefix.equals("xml")) {
            grumble("The XML namespace cannot be bound to any prefix other than 'xml'", "XQST0070");
        }
        if (prefix.equals("xml") && !uri.equals(NamespaceUri.XML)) {
            grumble("The prefix 'xml' cannot be bound to any namespace other than " + NamespaceConstant.XML, "XQST0070");
        }
    }

    /**
     * Parse a global variable definition.
     * &lt;"declare" "variable" "$"&gt; VarName TypeDeclaration?
     * ((":=" ExprSingle ) | "external")
     * XQuery 3.0 allows "external := ExprSingle"
     *
     * @param annotations derived from any %-annotations present in XQuery 3.0
     * @throws XPathException if a static error is found
     */

    private void parseVariableDeclaration(AnnotationList annotations) throws XPathException {
        int offset = t.currentTokenStartOffset;
        GlobalVariable var = new GlobalVariable();
        var.setPackageData(env.getPackageData());
        var.setLineNumber(t.getLineNumber() + 1);
        var.setColumnNumber(t.getColumnNumber() + 1);
        var.setSystemId(env.getSystemId());
        if (annotations != null) {
            var.setPrivate(annotations.includes(Annotation.PRIVATE));
        }
        nextToken();
        StructuredQName varQName = readVariableName();
        assert varQName != null;
        var.setVariableQName(varQName);

        NamespaceUri uri = varQName.getNamespaceUri();
        NamespaceUri moduleURI = ((QueryModule) env).getModuleNamespace();
        if (moduleURI != null && !moduleURI.equals(uri) && !(var.isPrivate() && languageVersion >= 40)) {
            grumble("A variable declared in a library module must be in the module namespace", "XQST0048", offset);
        }

        SequenceType requiredType = readOptionalAsClause(SequenceType.ANY_SEQUENCE);
        var.setRequiredType(requiredType);

        if (t.currentToken == Token.COLON_EQUALS) {
            nextToken();
            int refs = ((QueryModule) env).getForwardReferenceCount(varQName);
            Expression exp = parseExprSingle();
            if (((QueryModule) env).getForwardReferenceCount(varQName) > refs && env.getXPathVersion() < 40) {
                grumble("Variable $" + var.getVariableQName().getDisplayName()
                                + " is referenced within its own declaration", "XPST0008");
            }
            exp = makeTracer(exp, varQName);
            if (languageVersion >= 40 && requiredType != SequenceType.ANY_SEQUENCE) {
                TypeChecker checker = env.getConfiguration().getTypeChecker(false);
                ExpressionVisitor visitor = ExpressionVisitor.make(env);
                Supplier<RoleDiagnostic> role = () -> new RoleDiagnostic(RoleDiagnostic.VARIABLE, varQName.getDisplayName(), 0);
                exp = checker.staticTypeCheck(exp, requiredType, role, visitor);
            }
            var.setBody(exp);
        } else if (t.currentToken instanceof Token.NameToken) {
            if (isKeyword("external")) {
                GlobalParam par = new GlobalParam();
                par.setPackageData(env.getPackageData());
                //par.setExecutable(var.getExecutable());
                par.setLineNumber(var.getLineNumber());
                par.setColumnNumber(var.getColumnNumber());
                par.setSystemId(var.getSystemId());
                par.setVariableQName(var.getVariableQName());
                par.setRequiredType(var.getRequiredType());
                var = par;
                nextToken();
                if (t.currentToken == Token.COLON_EQUALS) {
                    nextToken();
                    Expression exp = parseExprSingle();
                    exp = makeTracer(exp, varQName);
                    if (languageVersion >= 40 && requiredType != SequenceType.ANY_SEQUENCE) {
                        TypeChecker checker = env.getConfiguration().getTypeChecker(false);
                        ExpressionVisitor visitor = ExpressionVisitor.make(env);
                        Supplier<RoleDiagnostic> role = () -> new RoleDiagnostic(RoleDiagnostic.VARIABLE, varQName.getDisplayName(), 0);
                        exp = checker.staticTypeCheck(exp, requiredType, role, visitor);
                    }
                    var.setBody(exp);
                }

            } else {
                grumble("Variable must either be initialized or be declared as external");
            }
        } else {
            grumble("Expected ':=' or 'external' in variable declaration");
        }

        QueryModule qenv = (QueryModule) env;
        RetainedStaticContext rsc = env.makeRetainedStaticContext();
        var.setRetainedStaticContext(rsc);
        if (var.getBody() != null) {
            ExpressionTool.setDeepRetainedStaticContext(var.getBody(), rsc);
        }
//        if (qenv.getModuleNamespace() != null &&
//                !uri.equals(qenv.getModuleNamespace())) {
//            grumble("Variable " + Err.wrap(varQName.getDisplayName(), Err.VARIABLE) + " is not defined in the module namespace");
//        }
        try {
            qenv.declareVariable(var);
        } catch (XPathException e) {
            grumble(e.getMessage(), e.getErrorCodeQName(), -1);
        }
    }


    /**
     * Parse a context item or value declaration.
     * "declare" "context" "item"|"value"  TypeDeclaration?
     * ((":=" ExprSingle ) | ("external" (":=" ExprSingle ))
     *
     * @throws XPathException if parsing fails
     */

    private void parseContextItemDeclaration() throws XPathException {
        int offset = t.currentTokenStartOffset;

        boolean isItemDeclaration = isKeyword("item");
        if (!isItemDeclaration) {
            if (isKeyword("value")) {
                if (languageVersion < 40) {
                    grumble("'declare context value' requires XQuery 4.0");
                }
            } else {
                grumble("After 'declare context', expected 'item' (or, in 4.0, 'value')");
            }
        }

        if (foundContextItemDeclaration) {
            grumble("More than one context item declaration found", "XQST0099", offset);
        }
        foundContextItemDeclaration = true;

        GlobalContextRequirement req = new GlobalContextRequirement();
        req.setContextValueOptionality(Optionality.OPTIONAL);

        nextToken();

        SequenceType requiredSequenceType =
                isItemDeclaration ? SequenceType.SINGLE_ITEM : SequenceType.ANY_SEQUENCE;
        if (isKeyword("as")) {
            nextToken();
            if (isItemDeclaration) {
                ItemType requiredType = parseItemType();
                requiredSequenceType = SequenceType.one(requiredType);
            } else {
                requiredSequenceType = parseSequenceType();
            }
        }

        req.addRequiredSequenceType(requiredSequenceType, ((QueryModule)env).isMainModule());
        if (t.currentToken == Token.COLON_EQUALS) {
            if (!((QueryModule) env).isMainModule()) {
                grumble("The context item must not be initialized in a library module", "XQST0113");
            }
            nextToken();
            Expression exp = parseExprSingle();
            exp.setRetainedStaticContext(env.makeRetainedStaticContext());
            ExpressionVisitor visitor = ExpressionVisitor.make(env);
            exp = exp.simplify();
            ContextItemStaticInfo info = env.getConfiguration().makeContextItemStaticInfo(requiredSequenceType, Optionality.OPTIONAL);
            exp.setRetainedStaticContext(env.makeRetainedStaticContext());
            exp = exp.typeCheck(visitor, info);
            req.setDefaultValue(exp);
            req.setExternal(false);
        } else if (tryKeyword("external")) {
            req.setContextValueOptionality(Optionality.OPTIONAL);
            req.setExternal(true);
            if (t.currentToken == Token.COLON_EQUALS) {
                if (!((QueryModule) env).isMainModule()) {
                    grumble("The context item must not be initialized in a library module", "XQST0113");
                }
                nextToken();
                Expression exp = parseExprSingle();
                Supplier<RoleDiagnostic> role =
                        () -> new RoleDiagnostic(RoleDiagnostic.CONTEXT_ITEM, "context item declaration", 0);
                exp = CardinalityChecker.makeCardinalityChecker(exp, StaticProperty.EXACTLY_ONE, role);
                exp.setRetainedStaticContext(env.makeRetainedStaticContext());
                req.setDefaultValue(exp);
            }
        } else {
            grumble("Expected ':=' or 'external' in context item declaration");
        }

        Executable exec = getExecutable();
        if (exec.getGlobalContextRequirement() != null) {
            // the context item is already declared in another module. Compare the required types
            GlobalContextRequirement gcr = exec.getGlobalContextRequirement();
            if (gcr.getDefaultValue() == null && req.getDefaultValue() != null) {
                gcr.setDefaultValue(req.getDefaultValue());
            }
            for (SequenceType otherType : gcr.getRequiredTypes()) {
                if (otherType != SequenceType.ANY_SEQUENCE) {
                    TypeHierarchy th = env.getConfiguration().getTypeHierarchy();
                    Affinity rel = Subsumption.sequenceTypeRelationship(requiredSequenceType, otherType);
                    if (rel == Affinity.DISJOINT) {
                        // the two types are incompatible: fail now
                        grumble("Different modules specify incompatible requirements for the type of the initial context item", "XPTY0004");
                    }
                }
            }
            gcr.addRequiredSequenceType(requiredSequenceType, ((QueryModule) env).isMainModule());
        } else {
            exec.setGlobalContextRequirement(req);
        }
    }

    /**
     * Parse a function declaration.
     * <p>Syntax: <br>
     * &lt;"declare" "function"&gt; QName "(" ParamList? ")" ("as" SequenceType)?
     * (EnclosedExpr | "external")
     * </p>
     * <p>On entry, the "declare function" has already been recognized</p>
     *
     * @param annotations the list of annotations that have been encountered for this function declaration
     * @throws XPathException if a syntax error is found
     */

    public void parseFunctionDeclaration(AnnotationList annotations) throws XPathException {

        foundFunctionDeclaration = true;
        if (annotations.includes(SAXON_MEMO_FUNCTION)) {
            if (env.getConfiguration().getEditionCode().equals("HE")) {
                warning("saxon:memo-function option is ignored under Saxon-HE", SaxonErrorCode.SXJX0001);
            } else {
                memoFunction = true;
            }
        }

        // the next tokens should be the < QNAME "("> pair - now delivered as two tokens
        int offset = t.currentTokenStartOffset;
        nextToken();
        String fName = readName();
        expect(Token.LPAREN);

        NamespaceUri uri;
        StructuredQName qName;
        if (fName.indexOf(':') < 0) {
            if (foundDefaultFunctionNamespace || languageVersion < 40) {
                uri = env.getDefaultFunctionNamespace();
                qName = new StructuredQName("", env.getDefaultFunctionNamespace(), fName);
            } else {
                uri = NamespaceUri.NULL;
                qName = new StructuredQName("", NamespaceUri.NULL, fName);
            }
            if (isReservedFunctionName(fName, languageVersion)) {
                grumble("The name '" + fName + "' is reserved: it cannot be used as an unprefixed function name");
            }
        } else {
            qName = makeStructuredQName(fName, NamespaceUri.NULL);
            uri = qName.getNamespaceUri();
        }

        if (uri.isEmpty() && languageVersion < 40) {
            grumble("The function must be in a namespace", "XQST0060");
        }


        if (isReservedInQuery(uri)) {
            grumble("The function name " + fName + " is in a reserved namespace", "XQST0045");
        }

        XQueryFunction func = new XQueryFunction();
        func.setFunctionName(qName);
        func.setResultType(SequenceType.ANY_SEQUENCE);
        func.setBody(null);
        Location loc = makeNestedLocation(env.getContainingLocation(), t.getLineNumber(offset), t.getColumnNumber(offset), null);
        func.setLocation(loc);
        func.setStaticContext((QueryModule) env);
        func.setMemoFunction(memoFunction);
        func.setUpdating(annotations.includes(Annotation.UPDATING));
        func.setAnnotations(annotations);

        NamespaceUri moduleURI = ((QueryModule) env).getModuleNamespace();
        if (moduleURI != null && !moduleURI.equals(uri) && !(func.isPrivate() && languageVersion >= 40)) {
            grumble("A function in a library module must be in the module namespace", "XQST0048");
        }

        nextToken();
        HashSet<StructuredQName> paramNames = new HashSet<>(8);
        boolean external = false;
        boolean foundDefault = false;
        if (t.currentToken != Token.RPAREN) {
            while (true) {
                //     ParamList   ::=     Param ("," Param)*
                //     Param       ::=     "$" VarName  TypeDeclaration?
                StructuredQName argQName = readVariableName();
                if (paramNames.contains(argQName)) {
                    grumble("Duplicate parameter name " + Err.wrap(argQName.getDisplayName(), Err.VARIABLE), "XQST0039");
                }
                paramNames.add(argQName);
                SequenceType paramType = readOptionalAsClause(SequenceType.ANY_SEQUENCE);

                UserFunctionParameter arg = new UserFunctionParameter();
                arg.setRequiredType(paramType);
                arg.setVariableQName(argQName);
                if (t.currentToken == Token.COLON_EQUALS) {
                    if (languageVersion < 40) {
                        grumble("Default values for function parameters require XQuery 4.0 to be enabled");
                    }
                    foundDefault = true;
                    nextToken();
                    Expression defaultValue = parseExprSingle();
                    defaultValue.setRetainedStaticContext(env.makeRetainedStaticContext());
                    arg.setDefaultValueExpression(() -> defaultValue);
                    arg.setRequired(false);
                } else if (foundDefault) {
                    grumble("If a parameter in a function declaration has a default value, "
                                    + "all subsequent parameters must also have default values");
                }
                func.addParameter(arg);
                if (t.currentToken == Token.RPAREN) {
                    break;
                } else if (t.currentToken == Token.COMMA) {
                    nextToken();
                } else {
                    grumble("Expected ',' or ')' after function argument, found '" +
                                    t.currentToken + '\'');
                }
            }
            // Don't declare the variables until the end, to prevent one parameter being referenced
            // as the default value of another
            for (UserFunctionParameter p : func.getParameterDefinitions()) {
                declareRangeVariable(p);
            }
        }
        nextToken();
        if (tryKeyword("as")) {
            if (func.isUpdating()) {
                grumble("Cannot specify a return type for an updating function", "XUST0028");
            }
            func.setResultType(parseSequenceType());
        }
        if (isKeyword("external")) {
            external = true;
        } else {
            readToken(Token.LCURLY);
            if (t.currentToken == Token.RCURLY) {
                Expression body = Literal.makeEmptySequence();
                body.setRetainedStaticContext(env.makeRetainedStaticContext());
                setLocation(body);
                func.setBody(body);
            } else {
                Expression body = parseExpression();
                func.setBody(body);
                ExpressionTool.setDeepRetainedStaticContext(body, env.makeRetainedStaticContext());
            }
            expect(Token.RCURLY);
        }
        UserFunctionParameter[] params = func.getParameterDefinitions();
        //noinspection UnusedDeclaration
        for (UserFunctionParameter param : params) {
            undeclareRangeVariable();
        }
        nextToken();

        QueryModule qenv = (QueryModule) env;

        if (external) {
            parserExtension.handleExternalFunctionDeclaration(this, func);
        } else {
            try {
                qenv.declareFunction(func);
            } catch (XPathException e) {
                grumble(e.getMessage(), e.getErrorCodeQName(), -1);
            }
        }
        memoFunction = false;
    }

    public static final StructuredQName SAXON_MEMO_FUNCTION = new StructuredQName("saxon", NamespaceUri.SAXON, "memo-function");


    /**
     * Parse a type alias declaration. Allowed only in Saxon-PE and higher
     *
     * @throws XPathException if parsing fails
     */

    protected void parseItemTypeDeclaration(AnnotationList annotations) throws XPathException {
        parserExtension.parseItemTypeDeclaration(this);
    }

    /**
     * Parse an option declaration.
     * <p>Syntax:
     * &lt;"declare" "option"&gt;  QName "string-literal"
     * </p>
     * <p>On entry, the "declare option" has already been recognized</p>
     *
     * @throws XPathException if a syntax error is found
     */

    private void parseOptionDeclaration() throws XPathException {
        String name = readName();
        NamespaceUri defaultUri = NamespaceUri.XQUERY;
        StructuredQName varName = makeStructuredQName(name, defaultUri);
        assert varName != null;
        NamespaceUri uri = varName.getNamespaceUri();

        if (uri.isEmpty()) {
            grumble("The QName identifying an option declaration must be prefixed", "XPST0081");
            return;
        }

        String value = unescape(expectStringLiteral());

        if (uri.equals(NamespaceUri.OUTPUT)) {
            parseOutputDeclaration(varName, value);
        } else if (uri.equals(NamespaceUri.SAXON)) {
            String localName = varName.getLocalPart();
            switch (localName) {
                case "output":
                    setOutputProperty(value);
                    break;
                case "memo-function":
                    value = value.trim();
                    switch (value) {
                        case "true":
                            memoFunction = true;
                            if (env.getConfiguration().getEditionCode().equals("HE")) {
                                warning("saxon:memo-function option is ignored under Saxon-HE", SaxonErrorCode.SXJX0001);
                            }
                            break;
                        case "false":
                            memoFunction = false;
                            break;
                        default:
                            warning("Value of saxon:memo-function must be 'true' or 'false'", SaxonErrorCode.SXWN9042);
                            break;
                    }
                    break;
                case "allow-cycles":
                    warning("Value of saxon:allow-cycles is ignored", SaxonErrorCode.SXWN9042);
                    break;
                default:
                    warning("Unknown Saxon option declaration: " + varName.getDisplayName(), SaxonErrorCode.SXWN9042);
                    break;
            }
        }
        nextToken();
    }

    protected void parseOutputDeclaration(StructuredQName varName, String value) throws XPathException {

        if (!((QueryModule) env).isMainModule()) {
            grumble("Output declarations must not appear in a library module", "XQST0108");
        }
        String localName = varName.getLocalPart();
        if (outputPropertiesSeen.contains(varName)) {
            grumble("Duplicate output declaration (" + varName + ")", "XQST0110");
        }
        outputPropertiesSeen.add(varName);
        switch (localName) {
            case "parameter-document": {
                Configuration config = env.getConfiguration();
                ResourceRequest rr = new ResourceRequest();
                rr.relativeUri = value;
                rr.baseUri = env.getStaticBaseURI();
                try {
                    rr.uri = ResolveURI.makeAbsolute(value, env.getStaticBaseURI()).toString();
                } catch (URISyntaxException err) {
                    throw XPathException.makeXPathException(err);
                }
                rr.nature = NamespaceConstant.OUTPUT;
                rr.purpose = ResourceRequest.ANY_PURPOSE;

                Source source = rr.resolve(config.getResourceResolver(), new DirectResourceResolver(config));

                TreeInfo doc = config.buildDocumentTree(source);
                SerializationParamsHandler ph = new SerializationParamsHandler(parameterDocProperties);
                ph.setSerializationParams(doc.getRootNode());

                CharacterMap characterMap = ph.getCharacterMap();
                if (characterMap != null) {
                    CharacterMapIndex index = new CharacterMapIndex();
                    index.putCharacterMap(characterMap.getName(), characterMap);
                    getExecutable().setCharacterMapIndex(index);
                    parameterDocProperties.setProperty(SaxonOutputKeys.USE_CHARACTER_MAPS, characterMap.getName().getClarkName());
                }
                break;
            }
            case "use-character-maps":
                grumble("Output declaration use-character-maps cannot appear except in a parameter file", "XQST0109");
                break;
            default: {
                Properties props = getExecutable().getPrimarySerializationProperties().getProperties();
                ResultDocument.setSerializationProperty(props,
                                                        NamespaceUri.NULL,
                                                        localName,
                                                        value,
                                                        env.getNamespaceResolver(),
                                                        false,
                                                        env.getConfiguration());
                break;
            }
        }
    }

    /**
     * Handle a saxon:output option declaration. Format:
     * declare option saxon:output "indent = yes"
     *
     * @param property a property name=value pair. The name is the name of a serialization
     *                 property, potentially as a prefixed QName; the value is the value of the property. A warning
     *                 is output for unrecognized properties or values
     */

    private void setOutputProperty(/*@NotNull*/ String property) {
        int equals = property.indexOf("=");
        if (equals < 0) {
            badOutputProperty("no equals sign");
        } else if (equals == 0) {
            badOutputProperty("starts with '=");
        }
        String keyword = Whitespace.trim(property.substring(0, equals));
        String value = equals == property.length() - 1 ? "" : Whitespace.trim(property.substring(equals + 1));

        Properties props = getExecutable().getPrimarySerializationProperties().getProperties();
        try {
            StructuredQName name = makeStructuredQName(keyword, NamespaceUri.NULL);
            String lname = name.getLocalPart();
            NamespaceUri uri = name.getNamespaceUri();
            ResultDocument.setSerializationProperty(props,
                                                    uri,
                                                    lname,
                                                    value,
                                                    env.getNamespaceResolver(),
                                                    false,
                                                    env.getConfiguration());
        } catch (XPathException e) {
            badOutputProperty(e.getMessage());
        }
    }

    private void badOutputProperty(String s) {
        warning("Invalid serialization property (" + s + ")", SaxonErrorCode.SXWN9043);
    }

    /**
     * Parse a FLWOR expression. This replaces the XPath "for" expression.
     * Full syntax:
     * <p>
     * [41] FLWORExpr ::=  (ForClause  | LetClause)+
     * WhereClause? OrderByClause?
     * "return" ExprSingle <br>
     * [42] ForClause ::=  &lt;"for" "$"&gt; VarName TypeDeclaration? PositionalVar? "in" ExprSingle
     * ("," "$" VarName TypeDeclaration? PositionalVar? "in" ExprSingle)* <br>
     * [43] PositionalVar  ::= "at" "$" VarName <br>
     * [44] LetClause ::= &lt;"let" "$"&gt; VarName TypeDeclaration? ":=" ExprSingle
     * ("," "$" VarName TypeDeclaration? ":=" ExprSingle)* <br>
     * [45] WhereClause  ::= "where" Expr <br>
     * [46] OrderByClause ::= (&lt;"order" "by"&gt; | &lt;"stable" "order" "by"&gt;) OrderSpecList <br>
     * [47] OrderSpecList ::= OrderSpec  ("," OrderSpec)* <br>
     * [48] OrderSpec     ::=     ExprSingle  OrderModifier <br>
     * [49] OrderModifier ::= ("ascending" | "descending")?
     * (&lt;"empty" "greatest"&gt; | &lt;"empty" "least"&gt;)?
     * ("collation" StringLiteral)?
     * </p>
     *
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    /*@Nullable*/
    @Override
    protected Expression parseFLWORExpression() throws XPathException {
        FLWORExpression flwor = new FLWORExpression();
        int exprOffset = t.currentTokenStartOffset;
        List<Clause> clauseList = new ArrayList<>(4);
        while (true) {
            int offset = t.currentTokenStartOffset;
            if (isKeyword("for")) {
                Token second = t.peekAhead();
                if (isKeyword(second, "tumbling") || isKeyword(second, "sliding")) {
                    parseWindowClause(flwor, clauseList);
                } else {
                    if (isKeyword(second, "member") || isKeyword(second, "key") || isKeyword(second, "value")) {
                        if (languageVersion < 40) {
                            grumble("The 'for member/key/value' clause requires XQuery 4.0 to be enabled");
                        }
                    }
                    parseForClause(flwor, clauseList);
                }
            } else if (isKeyword("let")) {
                parseLetClause(flwor, clauseList);
            } else if (tryKeyword("count")) {
                parseCountClause(clauseList);
            } else if (tryKeywordPair("group", "by")) {
                parseGroupByClause(flwor, clauseList);
            } else if (tryKeyword("where")) {
                Expression condition = parseExprSingle();
                WhereClause clause = new WhereClause(flwor, condition);
                setLocation(clause, t.currentTokenStartOffset);
                clause.setRepeated(containsLoopingClause(clauseList));
                clauseList.add(clause);
            } else if (isKeyword("while")) {
                if (languageVersion < 40) {
                    grumble("The 'while' clause requires XQuery 4.0 to be enabled");
                }
                nextToken();
                Expression condition = parseExprSingle();
                WhileClause clause = new WhileClause(flwor, condition);
                setLocation(clause, t.currentTokenStartOffset);
                clause.setRepeated(containsLoopingClause(clauseList));
                clauseList.add(clause);
            } else if (isKeyword("trace")) {
                parseTraceClause(flwor, clauseList);
            } else if (isKeyword("stable") || isKeyword("order")) {
                // we read the "stable" keyword but ignore it; Saxon ordering is always stable
                if (isKeyword("stable")) {
                    nextToken();
                    if (!isKeyword("order")) {
                        grumble("'stable' must be followed by 'order by'");
                    }
                }
                TupleExpression tupleExpression = new TupleExpression();
                List<LocalVariableReference> vars = new ArrayList<>();
                for (Clause c : clauseList) {
                    for (LocalVariableBinding b : c.getRangeVariables()) {
                        vars.add(new LocalVariableReference(b));
                    }
                }
                tupleExpression.setVariables(vars);
                List<SortSpec> sortSpecList;
                nextToken();
                if (!isKeyword("by")) {
                    grumble("'order' must be followed by 'by'");
                }
                nextToken();
                sortSpecList = parseSortDefinition();
                SortKeyDefinition[] keys = new SortKeyDefinition[sortSpecList.size()];
                for (int i = 0; i < keys.length; i++) {
                    SortSpec spec = sortSpecList.get(i);
                    SortKeyDefinition key = new SortKeyDefinition(languageVersion);
                    key.setSortKey(sortSpecList.get(i).sortKey, false);
                    key.setOrder(new StringLiteral(spec.ascending ? U_ASCENDING : U_DESCENDING));
                    key.setEmptyLeast(spec.emptyLeast);

                    if (spec.collation != null) {
                        final StringCollator comparator = env.getConfiguration().getCollation(spec.collation);
                        if (comparator == null) {
                            grumble("Unknown collation '" + spec.collation + '\'', "XQST0076");
                        }
                        key.setCollation(comparator);
                    }
                    keys[i] = key;
                }
                OrderByClause clause = new OrderByClause(flwor, keys, tupleExpression);
                clause.setRepeated(containsLoopingClause(clauseList));
                clauseList.add(clause);
            } else {
                break;
            }
            setLocation(clauseList.get(clauseList.size() - 1), offset);
        }

        int returnOffset = t.currentTokenStartOffset;
        readKeyword("return");
        Expression returnExpression = parseExprSingle();
        setLocation(returnExpression, returnOffset);
        returnExpression = makeTracer(returnExpression, null);

        // undeclare all the range variables

        for (int i = clauseList.size() - 1; i >= 0; i--) {
            Clause clause = clauseList.get(i);
            for (int n = 0; n < clause.getRangeVariables().length; n++) {
                undeclareRangeVariable();
            }
        }

        flwor.init(clauseList, returnExpression);
        setLocation(flwor, exprOffset);
        return flwor;

    }

    private final static UnicodeString U_ASCENDING = Latin1.of("ascending");
    private final static UnicodeString U_DESCENDING = Latin1.of("descending");

    /**
     * Make a LetExpression. This returns an ordinary LetExpression if tracing is off, and an EagerLetExpression
     * if tracing is on. This is so that trace events occur in an order that the user can follow.
     *
     * @return the constructed "let" expression
     */

    /*@NotNull*/
    protected LetExpression makeLetExpression() {
        if (((QueryModule) env).getUserQueryContext().isCompileWithTracing()) {
            return new EagerLetExpression();
        } else {
            return new LetExpression();
        }
    }

    protected static boolean containsLoopingClause(List<Clause> clauseList) {
        for (Clause c : clauseList) {
            if (FLWORExpression.isLoopingClause(c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse a ForClause.
     * <p>
     * [42] ForClause ::=  "for" ForBinding ("," ForBinding)* <br>
     * [42a] ForBinding ::= "member"? "$" VarName TypeDeclaration? ("allowing" "empty")? PositionalVar? "in" ExprSingle
     * </p>
     *
     * @param clauseList - the components of the parsed ForClause are appended to the
     *                   supplied list
     * @throws XPathException if parsing fails
     */
    private void parseForClause(FLWORExpression flwor, List<Clause> clauseList) throws XPathException {
        boolean first = true;
        ForQualifier iterand = ForQualifier.FOR_ITEM;

        // "for member $x as T in $array" compiles to
        // "for $temp in array:members($array) let $x as T := $temp?value"

        // "for key $k as T value $v as U in $map" compiles to
        // "for $temp in map:pairs($array) let $k as T := $temp?key, $v as U := $temp?value"

        do {

            nextToken();
            if (isKeyword("member")) {
                iterand = ForQualifier.FOR_MEMBER;
                nextToken();
            } else if (isKeyword("key")) {
                iterand = ForQualifier.FOR_KEY;
                nextToken();
            } else if (isKeyword("value")) {
                iterand = ForQualifier.FOR_VALUE;
                nextToken();
            } else {
                iterand = ForQualifier.FOR_ITEM;
            }

            if (iterand != ForQualifier.FOR_ITEM && languageVersion < 40) {
                grumble("The 'for member/key/value' syntax requires XQuery 4.0 to be enabled");
            }

            int offset = t.currentTokenStartOffset;
            ForClause clause = new ForClause();
            clause.setRepeated(!first || containsLoopingClause(clauseList));
            if (first) {
                first = false;
            }
            setLocation(clause, offset);
            clauseList.add(clause);

            StructuredQName explicitQName = readVariableName();
            StructuredQName iterationQName = explicitQName;
            if (iterand != ForQualifier.FOR_ITEM) {
                iterationQName =
                        new StructuredQName("vv", NamespaceUri.SAXON_GENERATED_VARIABLE, "fm" + clause.hashCode());
            }
            SequenceType iterandType;
            if (iterand == ForQualifier.FOR_MEMBER || iterand == ForQualifier.FOR_VALUE) {
                iterandType = SequenceType.ANY_SEQUENCE;
            } else if (iterand == ForQualifier.FOR_KEY) {
                iterandType = SequenceType.SINGLE_ATOMIC;
            } else {
                iterandType = SequenceType.SINGLE_ITEM;
            }

            boolean explicitType = false;
            if (isKeyword("as")) {
                explicitType = true;
                nextToken();
                iterandType = parseSequenceType();
            }

            boolean allowingEmpty = false;
            if (isKeyword("allowing")) {
                if (iterand != ForQualifier.FOR_ITEM) {
                    grumble("'allowing empty' cannot appear in a 'for member/key/value' clause");
                }
                allowingEmpty = true;
                clause.setAllowingEmpty(true);
                if (!explicitType) {
                    iterandType = SequenceType.OPTIONAL_ITEM;
                }
                nextToken();
                if (!isKeyword("empty")) {
                    grumble("After 'allowing', expected 'empty'");
                }
                nextToken();
            }

            if (explicitType && !allowingEmpty
                    && (iterand == ForQualifier.FOR_ITEM || iterand == ForQualifier.FOR_KEY)
                    && iterandType.getCardinality() != StaticProperty.EXACTLY_ONE) {
                warning("Occurrence indicator on singleton range variable has no effect", SaxonErrorCode.SXWN9039);
                iterandType = SequenceType.one(iterandType.getPrimaryType());
            }

            SequenceType memberType = iterandType;
            if (iterand == ForQualifier.FOR_MEMBER) {
                memberType = SequenceType.ANY_SEQUENCE;
            } else if (iterand == ForQualifier.FOR_KEY || iterand == ForQualifier.FOR_VALUE) {
                memberType = SequenceType.SINGLE_MAP;
            }
            LocalVariableBinding binding =
                    new LocalVariableBinding(iterationQName, memberType);
            clause.setRangeVariable(binding);

            StructuredQName explicitQName2 = null;
            SequenceType explicitType2 = null;

            if (iterand == ForQualifier.FOR_KEY && isKeyword("value")) {
                nextToken();
                explicitQName2 = readVariableName();
                if (!scanOnly && explicitQName2.equals(explicitQName)) {
                    grumble("Multiple variables declared in a single 'for' clause must have different names", "XQST0089");
                }
                if (isKeyword("as")) {
                    nextToken();
                    explicitType2 = parseSequenceType();
                }
            }

            if (isKeyword("at")) {
                nextToken();
                StructuredQName posQName = readVariableName();
                if (!scanOnly && posQName.equals(explicitQName)) {
                    grumble("The two variables declared in a single 'for' clause must have different names", "XQST0089");
                }
                SequenceType positionVarType = allowingEmpty
                        ? BuiltInAtomicType.NON_NEGATIVE_INTEGER.one()
                        : BuiltInAtomicType.POSITIVE_INTEGER.one();
                LocalVariableBinding pos = new LocalVariableBinding(posQName, positionVarType);
                clause.setPositionVariable(pos);
            }
            readKeyword("in");
            Expression collection = parseExprSingle();
            if (iterand == ForQualifier.FOR_MEMBER) {
                collection = ArrayFunctionSet.getInstance(40).makeFunction("members", 1).makeFunctionCall(collection);
            } else if (iterand == ForQualifier.FOR_KEY || iterand == ForQualifier.FOR_VALUE) {
                collection = MapFunctionSet.getInstance(40).makeFunction("entries", 1).makeFunctionCall(collection);
            }
            clause.initSequence(flwor, collection);
            declareRangeVariable(binding);
            if (clause.getPositionVariable() != null) {
                declareRangeVariable(clause.getPositionVariable());
            }
            if (allowingEmpty) {
                checkForClauseAllowingEmpty(flwor, clause);
            }
            if (iterand == ForQualifier.FOR_MEMBER || iterand == ForQualifier.FOR_VALUE) {
                // Generate "let $x as T := map:items($temp"
                LetClause letClause = new LetClause();
                final LocalVariableBinding letBinding = new LocalVariableBinding(explicitQName, iterandType);
                letClause.setRangeVariable(letBinding);
                LocalVariableReference tempRef = new LocalVariableReference(clause.getRangeVariable());
                Expression valueGetter = MapFunctionSet.getInstance(40).makeFunction("items", 1).makeFunctionCall(tempRef);
                letClause.initSequence(flwor, valueGetter);
                declareRangeVariable(letBinding);
                clauseList.add(letClause);
            } else if (iterand == ForQualifier.FOR_KEY) {
                // Generate "let $x as T := map:keys($temp)"
                LetClause letClause = new LetClause();
                final LocalVariableBinding letBinding = new LocalVariableBinding(explicitQName, iterandType);
                letClause.setRangeVariable(letBinding);
                LocalVariableReference tempRef = new LocalVariableReference(clause.getRangeVariable());
                Expression keyGetter = MapFunctionSet.getInstance(40).makeFunction("keys", 1).makeFunctionCall(tempRef);
                letClause.initSequence(flwor, keyGetter);
                declareRangeVariable(letBinding);
                clauseList.add(letClause);
                if (explicitQName2 != null) {
                    LetClause letClause2 = new LetClause();
                    final LocalVariableBinding letBinding2 =
                            new LocalVariableBinding(explicitQName2, explicitType2 == null ? SequenceType.ANY_SEQUENCE : explicitType2);
                    letClause2.setRangeVariable(letBinding2);
                    LocalVariableReference tempRef2 = new LocalVariableReference(clause.getRangeVariable());
                    Expression valueGetter = MapFunctionSet.getInstance(40).makeFunction("items", 1).makeFunctionCall(tempRef2);
                    letClause2.initSequence(flwor, valueGetter);
                    declareRangeVariable(letBinding2);
                    clauseList.add(letClause2);
                }
            }
        } while (t.currentToken == Token.COMMA);
    }

    /**
     * Check a ForClause for an "outer for"
     *
     * @throws net.sf.saxon.trans.XPathException if invalid
     */

    private void checkForClauseAllowingEmpty(FLWORExpression flwor, ForClause clause) throws XPathException {
        if (languageVersion < 30) {
            grumble("The 'allowing empty' option requires XQuery 3.0");
        }
        SequenceType type = clause.getRangeVariable().getRequiredType();
        if (!Cardinality.allowsZero(type.getCardinality())) {
            warning("When 'allowing empty' is specified, the occurrence indicator on the range variable type should be '?'", SaxonErrorCode.SXWN9039);
        }
    }

    /**
     * Parse a LetClause.
     * <p>
     * [44] LetClause ::= &lt;"let" "$"&gt; VarName TypeDeclaration? ":=" ExprSingle
     * ("," "$" VarName TypeDeclaration? ":=" ExprSingle)*
     * </p>
     *
     * @param clauseList - the components of the parsed LetClause are appended to the
     *                   supplied list
     * @throws XPathException if a static error is found
     */
    private void parseLetClause(FLWORExpression flwor, List<Clause> clauseList) throws XPathException {
        boolean first = true;
        do {
            LetClause clause = new LetClause();
            setLocation(clause, t.currentTokenStartOffset);
            clause.setRepeated(containsLoopingClause(clauseList));
            if (first) {
                //clause.offset = t.currentTokenStartOffset;
            }
            clauseList.add(clause);
            nextToken();
            readToken(Token.DOLLAR);
            if (first) {
                first = false;
            } else {
                //clause.offset = t.currentTokenStartOffset;
            }

            StructuredQName var;
            SequenceType requiredType = SequenceType.ANY_SEQUENCE;

            List<StructuredQName> componentNames = new ArrayList<>(2);
            List<SequenceType> componentTypes = new ArrayList<>(2);
            String destructure = null;
            if (languageVersion >= 40 && t.currentToken == Token.LPAREN) {
                var = new StructuredQName("vv", NamespaceUri.SAXON_GENERATED_VARIABLE, "seq" + t.currentTokenStartOffset);
                gatherComponents(componentNames, componentTypes, SequenceType.ANY_SEQUENCE, Token.RPAREN);
                destructure = "seq";
            } else if (languageVersion >= 40 && t.currentToken == Token.LSQB) {
                var = new StructuredQName("vv", NamespaceUri.SAXON_GENERATED_VARIABLE, "arr" + t.currentTokenStartOffset);
                requiredType = ArrayItemType.SINGLE_ARRAY;
                gatherComponents(componentNames, componentTypes, SequenceType.ANY_SEQUENCE, Token.RSQB);
                destructure = "arr";
            } else if (languageVersion >= 40 && t.currentToken == Token.LCURLY) {
                var = new StructuredQName("vv", NamespaceUri.SAXON_GENERATED_VARIABLE, "map" + t.currentTokenStartOffset);
                requiredType = SequenceType.SINGLE_MAP;
                gatherComponents(componentNames, componentTypes, SequenceType.ANY_SEQUENCE, Token.RCURLY);
                destructure = "map";
            } else {
                String name = readName();
                var = makeStructuredQName(name, NamespaceUri.NULL);
            }
            requiredType = readOptionalAsClause(requiredType);


            LocalVariableBinding v = new LocalVariableBinding(var, requiredType);

            readToken(Token.COLON_EQUALS);
            clause.initSequence(flwor, parseExprSingle());
            clause.setRangeVariable(v);
            declareRangeVariable(v);

            // For a destructuring assignment, declare the component variables

            if (destructure != null) {
                switch (destructure) {
                    case "seq":
                        for (int c = 0; c < componentNames.size(); c++) {
                            Expression componentValue;
                            if (c < componentNames.size() - 1) {
                                componentValue = new SubscriptExpression(new LocalVariableReference(v),
                                                                         Literal.makeLiteral(Int64Value.makeIntegerValue(c + 1)));
                            } else {
                                componentValue = new TailExpression(new LocalVariableReference(v), c + 1);
                            }
                            LocalVariableBinding v2 = new LocalVariableBinding(componentNames.get(c), componentTypes.get(c));
                            LetClause subclause = new LetClause();
                            setLocation(subclause, t.currentTokenStartOffset);
                            subclause.setRepeated(containsLoopingClause(clauseList));
                            clauseList.add(subclause);
                            subclause.initSequence(flwor, componentValue);
                            subclause.setRangeVariable(v2);
                            declareRangeVariable(v2);
                        }
                        break;
                    case "arr":
                        for (int c = 0; c < componentNames.size(); c++) {
                            Expression componentValue = ArrayFunctionSet.getInstance(40).makeFunction("get", 2).makeFunctionCall(
                                    new LocalVariableReference(v),
                                    Literal.makeLiteral(Int64Value.makeIntegerValue(c + 1)));
                            LocalVariableBinding v2 = new LocalVariableBinding(componentNames.get(c), componentTypes.get(c));
                            LetClause subclause = new LetClause();
                            setLocation(subclause, t.currentTokenStartOffset);
                            subclause.setRepeated(containsLoopingClause(clauseList));
                            clauseList.add(subclause);
                            subclause.initSequence(flwor, componentValue);
                            subclause.setRangeVariable(v2);
                            declareRangeVariable(v2);
                        }
                        break;
                    case "map":
                        for (int c = 0; c < componentNames.size(); c++) {
                            Expression componentValue = MapFunctionSet.getInstance(40).makeFunction("get", 2).makeFunctionCall(
                                    new LocalVariableReference(v),
                                    new StringLiteral(componentNames.get(c).getLocalPart()));
                            LocalVariableBinding v2 = new LocalVariableBinding(componentNames.get(c), componentTypes.get(c));
                            LetClause subclause = new LetClause();
                            setLocation(subclause, t.currentTokenStartOffset);
                            subclause.setRepeated(containsLoopingClause(clauseList));
                            clauseList.add(subclause);
                            subclause.initSequence(flwor, componentValue);
                            subclause.setRangeVariable(v2);
                            declareRangeVariable(v2);
                        }
                        break;
                    default:
                        throw new IllegalStateException();
                }
            }

        } while (t.currentToken == Token.COMMA);
    }

    /**
     * Parse a CountClause.
     * <p>
     * [44] CountClause ::= &lt;"count" "$"&gt; VarName
     * </p>
     *
     * @param clauseList - the components of the parsed CountClause are appended to the
     *                   supplied list
     * @throws XPathException in the event of a syntax error
     */
    private void parseCountClause(List<Clause> clauseList) throws XPathException {
        CountClause clause = new CountClause();
        setLocation(clause, t.currentTokenStartOffset);
        clause.setRepeated(containsLoopingClause(clauseList));
        clauseList.add(clause);
        StructuredQName varQName = readVariableName();
        SequenceType type = SequenceType.ANY_SEQUENCE;
        LocalVariableBinding v = new LocalVariableBinding(varQName, type);
        clause.setRangeVariable(v);
        declareRangeVariable(v);
    }

    /**
     * Parse a TraceClause. This is a Saxon extension
     * <p>
     * [44] TraceClause ::= "trace" Expr
     * </p>
     *
     * @param clauseList - the components of the parsed TraceClause are appended to the
     *                   supplied list
     * @throws XPathException in the event of a syntax error
     */
    private void parseTraceClause(FLWORExpression flwor, List<Clause> clauseList) throws XPathException {
        DiagnosticClause clause = new DiagnosticClause();
        setLocation(clause, t.currentTokenStartOffset);
        clause.setRepeated(containsLoopingClause(clauseList));
        clauseList.add(clause);
        nextToken();
        clause.initSequence(flwor, parseExpression());
    }

    /**
     * Parse a Group By clause.
     * Handles the full XQuery 3.0 syntax:
     * "group by" ($varname ["collation" URILiteral]) [,...]
     *
     * @param clauseList the list of clauses of the FLWOR expression, to which this clause is added
     * @throws XPathException if there is a syntax error
     */
    private void parseGroupByClause(FLWORExpression flwor, List<Clause> clauseList) throws XPathException {
        GroupByClause clause = new GroupByClause(env.getConfiguration());
        setLocation(clause, t.currentTokenStartOffset);
        clause.setRepeated(containsLoopingClause(clauseList));
        List<StructuredQName> variableNames = new ArrayList<>();
        List<String> collations = new ArrayList<>();
        while (true) {
            SequenceType type = SequenceType.ANY_SEQUENCE;
            StructuredQName varQName = readVariableName();
            if (isKeyword("as")) {
                nextToken();
                type = parseSequenceType();
                if (t.currentToken != Token.COLON_EQUALS) {
                    grumble("In group by, if the type is declared then it must be followed by ':= value'");
                }
            }
            if (t.currentToken == Token.COLON_EQUALS) {
                LetClause letClause = new LetClause();
                setLocation(clause, t.currentTokenStartOffset);
                clauseList.add(letClause);
                nextToken();

                LocalVariableBinding v = new LocalVariableBinding(varQName, type);
                Expression value = parseExprSingle();
                Supplier<RoleDiagnostic> role =
                        () -> new RoleDiagnostic(RoleDiagnostic.MISC, "grouping key", 0);
                Expression atomizedValue = Atomizer.makeAtomizer(value, role);
                letClause.initSequence(flwor, atomizedValue);
                letClause.setRangeVariable(v);
                declareRangeVariable(v);
            }
            variableNames.add(varQName);
            if (isKeyword("collation")) {
                nextToken();
                collations.add(expectStringLiteral());
                nextToken();
            } else {
                collations.add(env.getDefaultCollationName());
            }
            if (t.currentToken == Token.COMMA) {
                nextToken();
            } else {
                break;
            }
        }
        // Each of the variable names acts both as a variable reference (for a variable in the pre-grouping stream)
        // and a variable declaration (for a variable in the post-grouping stream).
        TupleExpression groupingTupleExpr = new TupleExpression();
        TupleExpression retainedTupleExpr = new TupleExpression();
        List<LocalVariableReference> groupingRefs = new ArrayList<>();
        List<LocalVariableReference> retainedRefs = new ArrayList<>();
        List<LocalVariableBinding> groupedBindings = new ArrayList<>();
        for (StructuredQName q : variableNames) {
            boolean found = locateDeclaration(clauseList, groupingRefs, groupedBindings, q);
            if (!found) {
                grumble("The grouping variable " + q.getDisplayName() + " must be the name of a variable bound earlier in the FLWOR expression",
                        "XQST0094");
            }
        }
        groupingTupleExpr.setVariables(groupingRefs);
        clause.initGroupingTupleExpression(flwor, groupingTupleExpr);

        List<LocalVariableBinding> ungroupedBindings = new ArrayList<>();
        for (int i = clauseList.size() - 1; i >= 0; i--) {
            for (LocalVariableBinding b : clauseList.get(i).getRangeVariables()) {
                if (!groupedBindings.contains(b)) {
                    ungroupedBindings.add(b);
                    retainedRefs.add(new LocalVariableReference(b));
                }
            }
        }

        retainedTupleExpr.setVariables(retainedRefs);
        clause.initRetainedTupleExpression(flwor, retainedTupleExpr);

        LocalVariableBinding[] bindings = new LocalVariableBinding[groupedBindings.size() + ungroupedBindings.size()];
        int k = 0;

        for (LocalVariableBinding b : groupedBindings) {
            bindings[k] = new LocalVariableBinding(b.getVariableQName(), b.getRequiredType());
            //declareRangeVariable(bindings[k]);
            k++;
        }

        for (LocalVariableBinding b : ungroupedBindings) {
            ItemType itemType = b.getRequiredType().getPrimaryType();
            bindings[k] = new LocalVariableBinding(b.getVariableQName(),
                                                   SequenceType.zeroOrMore(itemType));
            //declareRangeVariable(bindings[k]);
            k++;
        }

        for (int z = groupedBindings.size(); z < bindings.length; z++) {
            declareRangeVariable(bindings[z]);
        }
        for (int z = 0; z < groupedBindings.size(); z++) {
            declareRangeVariable(bindings[z]);
        }

        clause.setVariableBindings(bindings);
        StringCollator[] stringCollators = new StringCollator[collations.size()];
        for (int i = 0; i < collations.size(); i++) {
            stringCollators[i] = env.getConfiguration().getCollation(collations.get(i));
        }
        clause.setStringCollators(stringCollators);
        clauseList.add(clause);
    }

    private boolean locateDeclaration(List<Clause> clauseList, List<LocalVariableReference> groupingRefs,
                                      List<LocalVariableBinding> groupedBindings, StructuredQName q) {
        for (int i = clauseList.size() - 1; i >= 0; i--) {
            for (LocalVariableBinding b : clauseList.get(i).getRangeVariables()) {
                if (q.equals(b.getVariableQName())) {
                    groupedBindings.add(b);
                    groupingRefs.add(new LocalVariableReference(b));
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Parse a tumbling or sliding window clause.
     *
     * @param clauseList the list of clauses of the FLWOR expression, to which this clause is aded
     * @throws XPathException if there is a syntax error
     */
    private void parseWindowClause(FLWORExpression flwor, List<Clause> clauseList) throws XPathException {
        WindowClause clause = new WindowClause();
        setLocation(clause, t.currentTokenStartOffset);
        clause.setRepeated(containsLoopingClause(clauseList));
        nextToken();
        clause.setIsSlidingWindow(isKeyword("sliding"));
        nextToken();
        if (!isKeyword("window")) {
            grumble("after 'sliding' or 'tumbling', expected 'window', but found " + currentTokenDisplay());
        }
        nextToken();
        StructuredQName windowVarName = readVariableName();
        SequenceType windowType = readOptionalAsClause(SequenceType.ANY_SEQUENCE);

        LocalVariableBinding windowVar = new LocalVariableBinding(windowVarName, windowType);
        clause.setVariableBinding(WindowClause.WINDOW_VAR, windowVar);

        // We can't assume that all the items in the input sequence belong to the item type of the windows: test case SlidingWindowExpr507
        SequenceType windowItemTypeMandatory = SequenceType.SINGLE_ITEM;
        SequenceType windowItemTypeOptional = SequenceType.OPTIONAL_ITEM;

        readKeyword("in");
        clause.initSequence(flwor, parseExprSingle());
        if (isKeyword("start")) {
            nextToken();
            if (t.currentToken == Token.DOLLAR) {
                LocalVariableBinding startItemVar = new LocalVariableBinding(readVariableName(), windowItemTypeMandatory);
                clause.setVariableBinding(WindowClause.START_ITEM, startItemVar);
                declareRangeVariable(startItemVar);
            }
            if (isKeyword("at")) {
                nextToken();
                LocalVariableBinding startPositionVar = new LocalVariableBinding(readVariableName(), BuiltInAtomicType.INTEGER.one());
                clause.setVariableBinding(WindowClause.START_ITEM_POSITION, startPositionVar);
                declareRangeVariable(startPositionVar);
            }
            if (isKeyword("previous")) {
                nextToken();
                LocalVariableBinding startPreviousItemVar = new LocalVariableBinding(readVariableName(), windowItemTypeOptional);
                clause.setVariableBinding(WindowClause.START_PREVIOUS_ITEM, startPreviousItemVar);
                declareRangeVariable(startPreviousItemVar);
            }
            if (isKeyword("next")) {
                nextToken();
                LocalVariableBinding startNextItemVar = new LocalVariableBinding(readVariableName(), windowItemTypeOptional);
                clause.setVariableBinding(WindowClause.START_NEXT_ITEM, startNextItemVar);
                declareRangeVariable(startNextItemVar);
            }
            if (isKeyword("when")) {
                nextToken();
                clause.initStartCondition(flwor, parseExprSingle());
            } else if (languageVersion >= 40) {
                clause.initStartCondition(flwor, Literal.makeLiteral(BooleanValue.TRUE, flwor));
            } else {
                grumble("Expected 'when' condition for window start, but found " + currentTokenDisplay());
            }
        } else if (languageVersion >= 40) {
            clause.initStartCondition(flwor, Literal.makeLiteral(BooleanValue.TRUE, flwor));
        } else {
            grumble("in window clause, expected 'start', but found " + currentTokenDisplay());
        }
        if (isKeyword("only")) {
            clause.setIncludeUnclosedWindows(false);
            nextToken();
        }
        if (isKeyword("end")) {
            nextToken();

            if (t.currentToken == Token.DOLLAR) {
                LocalVariableBinding endItemVar = new LocalVariableBinding(readVariableName(), windowItemTypeMandatory);
                clause.setVariableBinding(WindowClause.END_ITEM, endItemVar);
                declareRangeVariable(endItemVar);
            }
            if (isKeyword("at")) {
                nextToken();
                LocalVariableBinding endPositionVar = new LocalVariableBinding(readVariableName(), BuiltInAtomicType.INTEGER.one());
                clause.setVariableBinding(WindowClause.END_ITEM_POSITION, endPositionVar);
                declareRangeVariable(endPositionVar);
            }
            if (isKeyword("previous")) {
                nextToken();
                LocalVariableBinding endPreviousItemVar = new LocalVariableBinding(readVariableName(), windowItemTypeOptional);
                clause.setVariableBinding(WindowClause.END_PREVIOUS_ITEM, endPreviousItemVar);
                declareRangeVariable(endPreviousItemVar);
            }
            if (isKeyword("next")) {
                nextToken();
                LocalVariableBinding endNextItemVar = new LocalVariableBinding(readVariableName(), windowItemTypeOptional);
                clause.setVariableBinding(WindowClause.END_NEXT_ITEM, endNextItemVar);
                declareRangeVariable(endNextItemVar);
            }
            if (isKeyword("when")) {
                nextToken();
                clause.initEndCondition(flwor, parseExprSingle());
            } else if (languageVersion >= 40) {
                clause.initEndCondition(flwor, Literal.makeLiteral(BooleanValue.TRUE, flwor));
            } else {
                grumble("Expected 'when' condition for window end, but found " + currentTokenDisplay());
            }
        } else {
            // no "end" condition found
            if (clause.isSlidingWindow()) {
                grumble("A sliding window requires an end condition");
            }
        }
        declareRangeVariable(windowVar);
        clauseList.add(clause);
    }

    /**
     * Make a string-join expression that concatenates the string-values of items in
     * a sequence with intervening spaces. This may be simplified later as a result
     * of type-checking.
     *
     * @param exp the base expression, evaluating to a sequence
     * @param env the static context
     * @return a call on string-join to create a string containing the
     * representations of the items in the sequence separated by spaces.
     */

    /*@Nullable*/
    public static Expression makeStringJoin(Expression exp, /*@NotNull*/ StaticContext env) {

        exp = Atomizer.makeAtomizer(exp, null);
        ItemType t = exp.getItemType();
        if (!t.equals(BuiltInAtomicType.STRING) && !t.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            exp = new AtomicSequenceConverter(exp, BuiltInAtomicType.STRING);
            ((AtomicSequenceConverter) exp).allocateConverterStatically(env.getConfiguration(), false);
        }

        if (exp.getCardinality() == StaticProperty.EXACTLY_ONE) {
            return exp;
        } else {
            RetainedStaticContext rsc = new RetainedStaticContext(env);
            Expression fn = SystemFunction.makeCall("string-join", rsc, exp, new StringLiteral(StringValue.SINGLE_SPACE));
            ExpressionTool.copyLocationInfo(exp, fn);
            return fn;
        }
    }

    /**
     * Parse the "order by" clause.
     * <p>[46] OrderByClause ::= (&lt;"order" "by"&gt; | &lt;"stable" "order" "by"&gt;) OrderSpecList <br>
     * [47] OrderSpecList ::= OrderSpec  ("," OrderSpec)* <br>
     * [48] OrderSpec     ::=     ExprSingle  OrderModifier <br>
     * [49] OrderModifier ::= ("ascending" | "descending")?
     * (&lt;"empty" "greatest"&gt; | &lt;"empty" "least"&gt;)?
     * ("collation" StringLiteral)?
     * </p>
     *
     * @return a list of sort specifications (SortSpec), one per sort key
     * @throws XPathException if parsing fails
     */
    /*@NotNull*/
    private List<SortSpec> parseSortDefinition() throws XPathException {
        List<SortSpec> sortSpecList = new ArrayList<>(5);
        while (true) {
            SortSpec sortSpec = new SortSpec();
            sortSpec.sortKey = parseExprSingle();
            sortSpec.ascending = true;
            sortSpec.emptyLeast = ((QueryModule) env).isEmptyLeast();
            sortSpec.collation = env.getDefaultCollationName();
            //t.setState(t.BARE_NAME_STATE);
            if (isKeyword("ascending")) {
                nextToken();
            } else if (isKeyword("descending")) {
                sortSpec.ascending = false;
                nextToken();
            }
            if (isKeyword("empty")) {
                nextToken();
                if (isKeyword("greatest")) {
                    sortSpec.emptyLeast = false;
                    nextToken();
                } else if (isKeyword("least")) {
                    sortSpec.emptyLeast = true;
                    nextToken();
                } else {
                    grumble("'empty' must be followed by 'greatest' or 'least'");
                }
            }
            if (isKeyword("collation")) {
                sortSpec.collation = readCollationName();
            }
            sortSpecList.add(sortSpec);
            if (t.currentToken == Token.COMMA) {
                nextToken();
            } else {
                break;
            }
        }
        return sortSpecList;
    }

    protected String readCollationName() throws XPathException {
        nextToken();
        String collationName = uriLiteral(expectStringLiteral());
        URI collationURI;
        try {
            collationURI = new URI(collationName);
            if (!collationURI.isAbsolute()) {
                URI base = new URI(env.getStaticBaseURI());
                collationURI = base.resolve(collationURI);
                collationName = collationURI.toString();
            }
        } catch (URISyntaxException err) {
            grumble("Collation name '" + collationName + "' is not a valid URI", "XQST0046");
            collationName = NamespaceConstant.CODEPOINT_COLLATION_URI;
        }
        nextToken();
        return collationName;
    }

    private static class SortSpec {
        /*@Nullable*/ public Expression sortKey;
        public boolean ascending;
        public boolean emptyLeast;
        public String collation;
    }

    /**
     * Parse a Typeswitch Expression.
     * This construct is XQuery-only.
     * TypeswitchExpr   ::=
     * "typeswitch" "(" Expr ")"
     * CaseClause+
     * "default" ("$" VarName)? "return" ExprSingle
     * CaseClause   ::=
     * "case" ("$" VarName "as")? SequenceType "return" ExprSingle
     *
     * @throws XPathException if parsing fails
     */

    /*@NotNull*/
    @Override
    protected Expression parseTypeswitchExpression() throws XPathException {
        int offset = t.currentTokenStartOffset;
        nextToken();
        readToken(Token.LPAREN);
        Expression operand = parseExpression();
        List<List<SequenceType>> types = new ArrayList<>(10);
        List<Expression> actions = new ArrayList<>(10);
        readToken(Token.RPAREN);

        // The code generated takes the form:
        //    let $zzz := operand return
        //    if ($zzz instance of t1) then action1
        //    else if ($zzz instance of t2) then action2
        //    else default-action
        //
        // If a variable is declared in a case clause or default clause,
        // then "action-n" takes the form
        //    let $v as type := $zzz return action-n

        // we were generating "let $v as type := $zzz return action-n" but this gives a compile time error if
        // there's a case clause that specifies an impossible type.

        LetExpression outerLet = makeLetExpression();
        outerLet.setRequiredType(SequenceType.ANY_SEQUENCE);
        outerLet.setVariableQName(new StructuredQName("zz", NamespaceUri.SAXON, "zz_typeswitchVar"));
        outerLet.setSequence(operand);

        boolean braced = false;
        if (t.currentToken == Token.LCURLY) {
            checkLanguageVersion40("braces in a typeswitch expression");
            braced = true;
            nextToken();
        }

        while (isKeyword("case")) {
            List<SequenceType> typeList;
            Expression action;
            nextToken();
            if (t.currentToken == Token.DOLLAR) {
                nextToken();
                String var = readName();
                final StructuredQName varQName = makeStructuredQName(var, NamespaceUri.NULL);
                readKeyword("as");
                typeList = parseSequenceTypeList();
                action = makeTracer(
                        parseTypeswitchReturnClause(varQName, outerLet),
                        varQName);
                if (action instanceof TraceExpression) {
                    ((TraceExpression) action).setProperty("type", typeList.get(0).toString());
                }

            } else {
                typeList = parseSequenceTypeList();
                action = makeTracer(parseExprSingle(), null);
                if (action instanceof TraceExpression) {
                    ((TraceExpression) action).setProperty("type", typeList.get(0).toString());
                }
            }
            types.add(typeList);
            actions.add(action);
        }
        if (types.isEmpty()) {
            grumble("At least one case clause is required in a typeswitch");
        }
        readKeyword("default");
        final int defaultOffset = t.currentTokenStartOffset;
        Expression defaultAction;
        if (t.currentToken == Token.DOLLAR) {
            nextToken();
            String var = readName();
            final StructuredQName varQName = makeStructuredQName(var, NamespaceUri.NULL);
            readKeyword("return");
            defaultAction = makeTracer(
                    parseTypeswitchReturnClause(varQName, outerLet),
                    varQName);
        } else {
            readKeyword("return");
            defaultAction = makeTracer(parseExprSingle(), null);
        }

        Expression lastAction = defaultAction;
        // Note, the ragged "choose" later gets flattened into a single-level choose, saving stack space
        for (int i = types.size() - 1; i >= 0; i--) {
            final LocalVariableReference var = new LocalVariableReference(outerLet);
            setLocation(var);
            Expression ioe = new InstanceOfExpression(var, types.get(i).get(0));
            for (int j = 1; j < types.get(i).size(); j++) {
                ioe = new OrExpression(ioe, new InstanceOfExpression(var.copy(new RebindingMap()), types.get(i).get(j)));
            }
            setLocation(ioe);
            final Expression ife = Choose.makeConditional(ioe, actions.get(i), lastAction);
            setLocation(ife);
            lastAction = ife;
        }
        outerLet.setAction(lastAction);
        if (braced) {
            readToken(Token.RCURLY);
        }
        return makeTracer(outerLet, null);
    }

    /*@NotNull*/
    private List<SequenceType> parseSequenceTypeList() throws XPathException {
        List<SequenceType> typeList = new ArrayList<>();
        while (true) {
            SequenceType type = parseSequenceType();
            typeList.add(type);
            if (t.currentToken == Token.VBAR) {
                nextToken();
            } else {
                break;
            }
        }
        readKeyword("return");
        return typeList;
    }

    /*@NotNull*/
    private Expression parseTypeswitchReturnClause(StructuredQName varQName, LetExpression outerLet)
            throws XPathException {
        Expression action;

        LetExpression innerLet = makeLetExpression();
        innerLet.setRequiredType(SequenceType.ANY_SEQUENCE);
        innerLet.setVariableQName(varQName);
        innerLet.setSequence(new LocalVariableReference(outerLet));

        declareRangeVariable(innerLet);
        action = parseExprSingle();
        undeclareRangeVariable();

        innerLet.setAction(action);
        return innerLet;
    }


    /**
     * Parse a Switch Expression.
     * This construct is XQuery-3.0-only.
     * SwitchExpr ::= "switch" ("(" Expr ")")? SwitchCaseClause+ "default" "return" ExprSingle
     * SwitchCaseClause ::= ("case" ExprSingle)+ "return" ExprSingle
     *
     * <p>4.0 allows the parenthesized expression to be omitted, and also allows braces around
     * the cases. This means there are three ways of recognizing the start of the expression:
     * (a) "switch (", (b) "switch {" (c) "switch case"}</p>
     */

    /*@NotNull*/
    @Override
    protected Expression parseSwitchExpression() throws XPathException {

        Expression comparand;
        boolean braced = false;
        nextToken();
        readToken(Token.LPAREN);
        if (t.currentToken == Token.RPAREN) {
            nextToken();
            checkLanguageVersion40("a switch expression with no explicit operand");
            comparand = Literal.makeLiteral(BooleanValue.TRUE);
        } else {
            comparand = parseExpression();
            readToken(Token.RPAREN);
        }
        if (t.currentToken == Token.LCURLY) {
            checkLanguageVersion40("a switch expression with braces");
            braced = true;
            nextToken();
        } else {
            expectKeyword("case");
        }


        List<Expression> conditions = new ArrayList<>(10);
        List<Expression> actions = new ArrayList<>(10);

        // The code generated takes the form:
        //    let $zzz := zero-or-one(atomize(operand)) return
        //    choose
        //      when ($zzz eq t1) then action1
        //      when ($zzz eq t2) then action2
        //      when (true) default-action
        //
        // We rely on the optimizer to convert this to a SwitchExpression in the case where all the case clauses
        // are literal constants.

        LetExpression outerLet = makeLetExpression();
        outerLet.setRequiredType(SequenceType.OPTIONAL_ATOMIC);
        outerLet.setVariableQName(new StructuredQName("zz", NamespaceUri.SAXON, "zz_switchVar"));
        outerLet.setSequence(Atomizer.makeAtomizer(comparand, null));

        do {
            List<Expression> caseExpressions = new ArrayList<>(4);

            expectKeyword("case");
            do {
                nextToken();
                Expression c = parseExprSingle();
                caseExpressions.add(c);
            } while (isKeyword("case"));

            readKeyword("return");

            Expression action = parseExprSingle();
            for (int i = 0; i < caseExpressions.size(); i++) {
                SwitchCaseComparison vc = new SwitchCaseComparison(
                        new LocalVariableReference(outerLet),
                        OperatorSymbol.FEQ,
                        caseExpressions.get(i),
                        languageVersion >= 40);
                if (i == 0) {
                    conditions.add(vc);
                    actions.add(action);
                } else {
                    OrExpression orExpr = new OrExpression(conditions.remove(conditions.size() - 1), vc);
                    conditions.add(orExpr);
                }
                //actions.add((i==0 ? action : action.copy()));
            }

        } while (isKeyword("case"));

        readKeyword("default");
        readKeyword("return");
        Expression defaultExpr = parseExprSingle();
        conditions.add(Literal.makeLiteral(BooleanValue.TRUE));
        actions.add(defaultExpr);

        Choose choice = new Choose(
                conditions.toArray(new Expression[0]),
                actions.toArray(new Expression[conditions.size()]));
        outerLet.setAction(choice);

        if (braced) {
            readToken(Token.RCURLY);
        }
        return makeTracer(outerLet, null);
    }


    /**
     * Parse a Validate Expression.
     * This construct is XQuery-only. The syntax allows:
     * validate mode? { Expr }
     * mode ::= "strict" | "lax"
     *
     * @throws XPathException if parsing fails
     */

    /*@NotNull*/
    @Override
    protected Expression parseValidateExpression() throws XPathException {
        readKeyword("validate");
        int offset = t.currentTokenStartOffset;
        int mode = Validation.STRICT;
        SchemaType requiredType = null;
        ensureSchemaAware("validate expression");
        if (isKeyword("strict")) {
            mode = Validation.STRICT;
            nextToken();
        } else if (isKeyword("lax")) {
            mode = Validation.LAX;
            nextToken();
        } else if (isKeyword("type")) {
            mode = Validation.BY_TYPE;

            nextToken();
            String typeName = readName();
            if (!NameChecker.isQName(StringTool.codePoints(typeName))) {
                grumble("Schema type name expected after 'validate type");
            }
            requiredType = env.getImportedSchema().getSchemaType(
                    makeStructuredQName(typeName, env.getDefaultElementNamespace()));
            if (requiredType == null) {
                grumble("Unknown schema type " + typeName, "XQST0104");
            }
            if (requiredType == Untyped.INSTANCE) {
                mode = Validation.STRIP;
                requiredType = null;
            }
            expect(Token.LCURLY);
        } else if (t.currentToken == Token.LCURLY) {
            mode = Validation.STRICT;
        } else {
            throw new AssertionError("shouldn't be parsing a validate expression");
        }
        nextToken();

        Expression exp = parseExpression();
        if (exp instanceof ParentNodeConstructor) {
            ((ParentNodeConstructor) exp).setValidationAction(
                    env.getImportedSchema(),
                    mode,
                    mode == Validation.BY_TYPE ? requiredType : null);
        } else {
            // the expression must return a single element or document node. The type-
            // checking machinery can't handle a union type, so we just check that it's
            // a node for now. Because we are reusing XSLT copy-of code, we need
            // an ad-hoc check that the node is of the right kind.

            // below code moved to XQuery-specific path in CopyOf
//            try {
//                RoleLocator role = new RoleLocator(RoleLocator.TYPE_OP, "validate", 0);
//                role.setErrorCode("XQTY0030");
//                setLocation(exp);
//                exp = config.getTypeChecker().staticTypeCheck(exp,
//                        SequenceType.SINGLE_NODE,
//                        false,
//                        role, ExpressionVisitor.make(env, getExecutable()));
//            } catch (XPathException err) {
//                grumble(err.getMessage(), err.getErrorCodeQName(), -1);
//            }
            exp = new CopyOf(exp, true, mode, requiredType, true);
            setLocation(exp);
            ((CopyOf) exp).setRequireDocumentOrElement(true);
        }

        readToken(Token.RCURLY);
        return makeTracer(exp, null);
    }

    /**
     * Parse an update map|array Expression.
     *
     * @return the parsed expression; except that this version of the method always
     * throws an exception
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    protected Expression parseDeepUpdateExpression() throws XPathException {
        return parserExtension.parseDeepUpdateExpression(this);
    }

    /**
     * Parse an Extension Expression.
     * Syntax: "(#" QName arbitrary-text "#)")+ "{" expr? "}"
     * The pragma "(#...#)" is represented as a single token,
     * which is the current token on entry to this method.
     *
     * @throws XPathException if parsing fails
     */

    /*@NotNull*/
    @Override
    protected Expression parseExtensionExpression() throws XPathException {
        String pragmaContent = t.currentToken.toString();
        SchemaType requiredType = null;
        Whitespace.Tokenizer tokenizer =
                new Whitespace.Tokenizer(pragmaContent.substring(2, pragmaContent.length() - 2));
        StringValue qname = tokenizer.next();
        if (qname == null) {
            grumble("Missing EQName in pragma");
            return null;
        }
        boolean validateType = false;
        StructuredQName pragmaName = makeStructuredQName(qname.getStringValue(), NamespaceUri.NULL);
        assert pragmaName != null;
        NamespaceUri uri = pragmaName.getNamespaceUri();
        String localName = pragmaName.getLocalPart();
        if (uri.equals(NamespaceUri.SAXON)) {
            if ("validate-type".equals(localName)) {
                if (!env.getConfiguration().isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY)) {
                    warning("Ignoring saxon:validate-type. To use this feature " +
                                    "you need the Saxon-EE processor from https://www.saxonica.com/", SaxonErrorCode.SXWN9042);
                } else {
                    String typeName = tokenizer.next().getStringValue();
                    if (!NameChecker.isQName(StringTool.codePoints(typeName))) {
                        grumble("Schema type name expected in saxon:validate-type pragma: found " + Err.wrap(typeName));
                    }
                    StructuredQName name = makeStructuredQName(typeName, env.getDefaultElementNamespace());
                    Schema schema = env.getImportedSchema();
                    requiredType = schema.getSchemaType(name);
                    if (requiredType == null) {
                        grumble("Unknown schema type " + typeName);
                    }
                    validateType = true;
                }
            } else {
                warning("Ignored pragma " + qname + " (unrecognized Saxon pragma)", SaxonErrorCode.SXWN9042);
            }
        }

        nextToken();
        Expression expr;
        if (t.currentToken instanceof Token.Pragma) {
            // multiple pragmas are allowed in an extension expression
            expr = parseExtensionExpression();
        } else {
            readToken(Token.LCURLY);
            if (t.currentToken == Token.RCURLY) {
                nextToken();
                grumble("Unrecognized pragma, with no fallback expression", "XQST0079");
            }
            expr = parseExpression();
            readToken(Token.RCURLY);
        }
        if (validateType) {
            if (expr instanceof ParentNodeConstructor) {
                ((ParentNodeConstructor) expr).setValidationAction(
                        env.getImportedSchema(), Validation.BY_TYPE, requiredType);
                return expr;
            } else if (expr instanceof AttributeCreator) {
                if (!(requiredType instanceof SimpleType)) {
                    grumble("The type used for validating an attribute must be a simple type");
                }

                //noinspection ConstantConditions
                ((AttributeCreator) expr).setSchemaType((SimpleType) requiredType);
                ((AttributeCreator) expr).setValidationAction(Validation.BY_TYPE);
                return expr;
            } else {
                CopyOf copy = new CopyOf(expr, true, Validation.BY_TYPE, requiredType, true);
                copy.setLocation(makeLocation());
                return copy;
            }
        } else {
            return expr;
        }
    }

    protected boolean isMaybeNamedConstructor() {
        String kind = t.currentName();
        Token ahead = t.peekAhead();
        if (!(ahead instanceof Token.NameToken)) {
            return false;
        }
        String qname = ((Token.NameToken) ahead).getValue();

        return switch (kind) {
            case "element", "attribute", "processing-instruction", "namespace" ->
                    !(languageVersion >= 40 && reservedNames.contains(qname));
            case "update" -> languageVersion >= 40 &&
                    (qname.equals("map") || qname.equals("array"));
            default -> false;
        };
    }

    /**
     * Parse a node constructor. This is allowed only in XQuery. This method handles
     * the XQuery-based "computed" constructors, not the XML-style "direct" constructors.
     * On entry the current token is the node-kind keyword such as "element" or "attribute".
     *
     * @return an Expression for evaluating the parsed constructor
     * @throws XPathException in the event of a syntax error.
     */

    /*@NotNull*/
    @Override
    protected Expression parseComputedNodeConstructor() throws XPathException {
        int offset = t.currentTokenStartOffset;
        String nodeKind = readName();
        if (t.currentToken == Token.LCURLY) {
            return switch (nodeKind) {
                case "document" -> parseDocumentConstructor(offset);
                case "element" -> parseComputedElementConstructor(offset);
                case "attribute" -> parseComputedAttributeConstructor(offset);
                case "text" -> parseTextNodeConstructor(offset);
                case "comment" -> parseCommentConstructor(offset);
                case "processing-instruction" -> parseProcessingInstructionConstructor(offset);
                case "namespace" -> parseNamespaceConstructor(offset);
                default -> failure("Unrecognized keyword '" + nodeKind + "' before {...} ");
            };
        } else {
            String name = null;
            StructuredQName qName = null;
            switch (nodeKind) {
                case "element":
                case "attribute":
                    if (t.currentToken == Token.HASH && languageVersion >= 40) {
                        qName = parseEQName();
                    } else {
                        name = expectName();
                        if (languageVersion >= 40) {
                            checkUnreserved(name);
                        }
                    }
                    break;
                case "namespace":
                case "processing-instruction":
                    if (t.currentToken == Token.HASH && languageVersion >= 40) {
                        nextToken();
                        name = expectName();
                    } else {
                        name = expectName();
                        if (languageVersion >= 40) {
                            checkUnreserved(name);
                        }
                    }
                    break;
                default:
                    grumble("Unknown constructor for named node: " + nodeKind);
                    return null;
            }

            return switch (nodeKind) {
                case "element" -> parseNamedElementConstructor(name, qName, offset);
                case "attribute" -> parseNamedAttributeConstructor(name, qName, offset);
                case "namespace" -> parseNamedNamespaceConstructor(name, offset);
                case "processing-instruction" -> parseNamedProcessingInstructionConstructor(name, offset);
                default -> failure("Unknown constructor for named node: " + nodeKind);
            };
        }
    }

    private Expression failure(String message) throws XPathException {
        grumble(message);
        return null;
    }

    private void checkUnreserved(String name) throws XPathException {
        if (reservedNames.contains(name)) {
            grumble("Keyword `" + name + "` is reserved in XQuery 4.0 - it needs to be written in quotes");
        }
    }

    protected Expression parseDirectPIConstructor() throws XPathException {

        Token.DirectProcessingInstructionConstructor token = (Token.DirectProcessingInstructionConstructor)t.currentToken;
        String body = token.getContent();
        int firstSpace = -1;
        for (int i=0; i<body.length(); i++) {
            if (Whitespace.isWhite(body.charAt(i))) {
                firstSpace = i;
                break;
            }
        }
        String target;
        String data = "";
        if (firstSpace < 0) {
            // there is no data part
            target = body;
        } else {
            // trim leading space from the data part, but not trailing space
            target = body.substring(0, firstSpace);
            firstSpace++;
            while (firstSpace < body.length() && " \t\r\n".indexOf(body.charAt(firstSpace)) >= 0) {
                firstSpace++;
            }
            data = body.substring(firstSpace);
        }

        if (!NameChecker.isValidNCName(target)) {
            grumble("Invalid processing instruction name " + Err.wrap(target));
        }

        if (target.equalsIgnoreCase("xml")) {
            grumble("A processing instruction must not be named 'xml' in any combination of upper and lower case");
        }

        ProcessingInstruction instruction =
                new ProcessingInstruction(new StringLiteral(target));
        instruction.setSelect(new StringLiteral(data));
        setLocation(instruction);
        nextToken();
        return makeTracer(instruction, null);

    }

    protected Expression parseDirectCommentConstructor() throws XPathException {
        Token.DirectCommentConstructor token = (Token.DirectCommentConstructor) t.currentToken;
        String body = token.getContent();
        Comment instruction = new Comment();
        instruction.setSelect(new StringLiteral(body));
        setLocation(instruction);
        nextToken();
        return makeTracer(instruction, null);
    }

    /**
     * Parse document constructor: document {...}
     *
     * @param offset the location in the source query
     * @return the document constructor instruction
     * @throws XPathException if parsing fails
     */

    /*@NotNull*/
    private Expression parseDocumentConstructor(int offset) throws XPathException {
        nextToken();
        Expression content;
        if (t.currentToken == Token.RCURLY && languageVersion >= 31) {
            content = Literal.makeEmptySequence();
        } else {
            content = parseExpression();
        }
        readToken(Token.RCURLY);
        DocumentInstr doc = new DocumentInstr(false, null);
        if (!((QueryModule) env).isPreserveNamespaces()) {
            content = new CopyOf(content, false, Validation.PRESERVE, null, true);
        }
        doc.setValidationAction(env.getImportedSchema(),
                                ((QueryModule) env).getConstructionMode(), null);
        doc.setContentExpression(content);
        setLocation(doc, offset);
        return doc;
    }

    /**
     * Parse an element constructor of the form
     * element {expr} {expr}
     *
     * @param offset location of the expression in the source query
     * @return the compiled instruction
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    private Expression parseComputedElementConstructor(int offset) throws XPathException {
        nextToken();
        // get the expression that yields the element name
        Expression name = parseExpression();
        readToken(Token.RCURLY);
        readToken(Token.LCURLY);
        Expression content = null;
        if (t.currentToken != Token.RCURLY) {
            // get the expression that yields the element content
            content = parseExpression();
            // if the child expression creates another element,
            // suppress validation, as the parent already takes care of it
            if (content instanceof ElementCreator && ((ElementCreator) content).getSchemaType() == null) {
                ((ElementCreator) content).setValidationAction(
                        env.getImportedSchema(),
                        Validation.PRESERVE, null);
            }
            expect(Token.RCURLY);
        }
        nextToken();

        Instruction inst;
        if (name instanceof Literal) {
            GroundedValue vName = ((Literal) name).getGroundedValue();
            // if element name is supplied as a literal, treat it like a direct element constructor
            NodeName elemName;
            if (vName instanceof StringValue && !(vName instanceof AnyURIValue)) {
                String lex = ((StringValue) vName).getStringValue();
                try {
                    QNameParser oldQP = getQNameParser();
                    setQNameParser(oldQP.withUnescaper(null));
                    elemName = makeNodeName(lex, true);
                    setQNameParser(oldQP);
                    elemName.obtainFingerprint(env.getConfiguration().getNamePool());
                } catch (XPathException staticError) {
                    if (staticError.hasErrorCode("XPST0008", "XPST0081")) {
                        staticError.setErrorCode("XQDY0074");
                    } else if (staticError.hasErrorCode("XPST0003")) {
                        //staticError.setErrorCode("XQDY0074");
                        grumble("Invalid QName in element constructor: " + lex, "XQDY0074", offset);
                        return new ErrorExpression();
                    }
                    staticError.setLocator(makeLocation());
                    staticError.setIsStaticError(false);
                    return new ErrorExpression(new XmlProcessingException(staticError));
                }
            } else if (vName instanceof QualifiedNameValue) {
                NamespaceUri uri = ((QualifiedNameValue) vName).getNamespaceURI();
                elemName = new FingerprintedQName("", uri, ((QualifiedNameValue) vName).getLocalName());
                elemName.obtainFingerprint(env.getConfiguration().getNamePool());
            } else {
                grumble("Element name must be either a string or a QName", "XPTY0004", offset);
                return new ErrorExpression();
            }
            Schema schema = env.getImportedSchema();
            inst = new FixedElement(elemName,
                                    ((QueryModule) env).getActiveNamespaceBindings(),
                                    ((QueryModule) env).isInheritNamespaces(),
                                    true, schema, null,
                                    ((QueryModule) env).getConstructionMode());
            if (content == null) {
                content = Literal.makeEmptySequence();
            }
            if (!((QueryModule) env).isPreserveNamespaces()) {
                content = new CopyOf(content, false, Validation.PRESERVE, null, true);
            }
            ((FixedElement) inst).setContentExpression(content);
            setLocation(inst, offset);
            //makeContentConstructor(content, (InstructionWithChildren) inst, offset);
            return makeTracer(inst, elemName.getStructuredQName());
        } else {
            // it really is a computed element constructor: save the namespace context
            NamespaceResolver ns = new NamespaceResolverWithDefault(
                    env.getNamespaceResolver(),
                    env.getDefaultElementNamespace());
            Schema schema = env.getImportedSchema();
            inst = new ComputedElement(name, null, schema, null,
                                       ((QueryModule) env).getConstructionMode(),
                                       ((QueryModule) env).isInheritNamespaces(),
                                       true);
            setLocation(inst);
            if (content == null) {
                content = Literal.makeEmptySequence();
            }
            if (!((QueryModule) env).isPreserveNamespaces()) {
                content = new CopyOf(content, false, Validation.PRESERVE, null, true);
            }
            ((ComputedElement) inst).setContentExpression(content);
            setLocation(inst, offset);
            //makeContentConstructor(content, (InstructionWithChildren) inst, offset);
            return makeTracer(inst, null);
        }
    }

    /**
     * Parse an element constructor of the form
     * <code>element name { expr }</code> or <code>element #qName { expr }</code>.
     * Either {@code name} or {@code qName}
     * must be supplied.
     * @param name the lexical QName of the element
     * @param qName the structured QName of the element
     * @param offset the position in the source query
     * @return the compiled instruction
     * @throws XPathException if parsing fails
     */

    private Expression parseNamedElementConstructor(
            String name, StructuredQName qName, int offset) throws XPathException {
        NodeName nodeName;
        if (qName == null) {
            nodeName = makeNodeName(name, true);
            nextToken();
        } else {
            nodeName = new FingerprintedQName(qName, env.getConfiguration().getNamePool());
        }
        Expression content = null;
        readToken(Token.LCURLY);
        if (t.currentToken != Token.RCURLY) {
            content = parseExpression();
            expect(Token.RCURLY);
        }
        nextToken();
        Schema schema = env.getImportedSchema();
        FixedElement el2 = new FixedElement(nodeName,
                                            ((QueryModule) env).getActiveNamespaceBindings(),
                                            ((QueryModule) env).isInheritNamespaces(),
                                            true, schema, null,
                                            ((QueryModule) env).getConstructionMode());
        setLocation(el2, offset);
        if (content == null) {
            content = Literal.makeEmptySequence();
        }
        if (!((QueryModule) env).isPreserveNamespaces()) {
            content = new CopyOf(content, false, Validation.PRESERVE, null, true);
        }
        el2.setContentExpression(content);
        return makeTracer(el2, nodeName.getStructuredQName());
    }

    /**
     * Parse an attribute constructor of the form
     * attribute {expr} {expr}
     *
     * @param offset position of the expression in the input
     * @return the compiled instruction
     * @throws XPathException if a static error is encountered
     */

    /*@NotNull*/
    private Expression parseComputedAttributeConstructor(int offset) throws XPathException {
        nextToken();
        Expression name = parseExpression();
        readToken(Token.RCURLY);
        readToken(Token.LCURLY);
        Expression content = null;
        if (t.currentToken != Token.RCURLY) {
            content = parseExpression();
            expect(Token.RCURLY);
        }
        nextToken();
        if (name instanceof Literal) {
            GroundedValue vName = ((Literal) name).getGroundedValue();
            if (vName instanceof StringValue && !(vName instanceof AnyURIValue)) {
                String lex = ((StringValue) vName).getStringValue();
                if (lex.equals("xmlns") || lex.startsWith("xmlns:")) {
                    grumble("Cannot create a namespace using an attribute constructor", "XQDY0044", offset);
                }
                NodeName attributeName;
                try {
                    QNameParser oldQP = getQNameParser();
                    setQNameParser(oldQP.withUnescaper(null));
                    attributeName = makeNodeName(lex, false);
                    setQNameParser(oldQP);
                } catch (XPathException staticError) {
                    staticError.setLocator(makeLocation());
                    if (staticError.hasErrorCode("XPST0008", "XPST0081")) {
                        staticError.setErrorCode("XQDY0074");
                    } else if (staticError.hasErrorCode("XPST0003")) {
                        grumble("Invalid QName in attribute constructor: " + lex, "XQDY0074", offset);
                        return new ErrorExpression();
                    }
                    throw staticError;
                }
                if (attributeName.getPrefix().isEmpty() && !attributeName.hasURI(NamespaceUri.NULL)) {
                    attributeName = new FingerprintedQName("_", attributeName.getNamespaceUri(),
                                                           attributeName.getLocalPart(),
                                                           attributeName.getFingerprint());
                }
                FixedAttribute fatt = new FixedAttribute(attributeName,
                                                         Validation.STRIP,
                                                         null);
                fatt.setRejectDuplicates();
                makeSimpleContent(content, fatt, offset);
                return makeTracer(fatt, null);
            } else if (vName instanceof QNameValue) {
                QNameValue qnv = (QNameValue) vName;
                NodeName attributeName = new FingerprintedQName(
                        qnv.getPrefix(), qnv.getNamespaceURI(), qnv.getLocalName());
                attributeName.obtainFingerprint(env.getConfiguration().getNamePool());
                FixedAttribute fatt = new FixedAttribute(attributeName,
                                                         Validation.STRIP,
                                                         null);
                fatt.setRejectDuplicates();
                makeSimpleContent(content, fatt, offset);
                return makeTracer(fatt, null);
            }
        }
        ComputedAttribute att = new ComputedAttribute(name,
                                                      null,
                                                      Validation.STRIP,
                                                      null,
                                                      true);
        att.setRejectDuplicates();
        makeSimpleContent(content, att, offset);
        return makeTracer(att, null);
    }

    /**
     * Parse an attribute constructor of the form
     * <code>attribute name { expr }</code> or <code>attribute #qName { expr }</code>.
     * Either {@code name} or {@code qName}
     * must be supplied.
     * @param attName the lexical QName of the attribute
     * @param qName the structured QName of the attribute
     * @param offset position of the expression in the source
     * @return the parsed expression
     * @throws XPathException if a static error is found
     */

    private Expression parseNamedAttributeConstructor(
            String attName, StructuredQName qName, int offset) throws XPathException {
        String warningMessage = null;
        NodeName attributeName;
        if (qName == null) {
            if (attName.equals("xmlns") || attName.startsWith("xmlns:")) {
                warningMessage = "Cannot create a namespace declaration using an attribute constructor";
            }
            attributeName = makeNodeName(attName, false);
            nextToken();
        } else {
            attributeName = new FingerprintedQName(qName, env.getConfiguration().getNamePool());
        }

        if (!attributeName.hasURI(NamespaceUri.NULL) && attributeName.getPrefix().isEmpty()) {
            // This must be because the name was given as Q{uri}local. Invent a prefix.
            attributeName = new FingerprintedQName(
                    attributeName.hasURI(NamespaceUri.XML) ? "xml" : "_",
                    attributeName.getNamespaceUri(),
                    attributeName.getLocalPart());
        }
        Expression attContent = null;
        readToken(Token.LCURLY);
        if (t.currentToken != Token.RCURLY) {
            attContent = parseExpression();
            expect(Token.RCURLY);
        }
        nextToken();
        if (warningMessage == null) {
            FixedAttribute att2 = new FixedAttribute(attributeName,
                                                     Validation.STRIP,
                                                     null);
            att2.setRejectDuplicates();
            att2.setRetainedStaticContext(env.makeRetainedStaticContext());
            makeSimpleContent(attContent, att2, offset);
            return makeTracer(att2, attributeName.getStructuredQName());
        } else {
            warning(warningMessage, "XQDY0044");
            return new ErrorExpression(warningMessage, "XQDY0044", false);
        }
    }

    private Expression parseTextNodeConstructor(int offset) throws XPathException {
        nextToken();
        Expression value;
        if (t.currentToken == Token.RCURLY && languageVersion >= 31) {
            value = Literal.makeEmptySequence();
        } else {
            value = parseExpression();
        }
        readToken(Token.RCURLY);
        Expression select = stringify(value, true, env);
        ValueOf vof = new ValueOf(select, false, true);
        setLocation(vof, offset);
        return makeTracer(vof, null);
    }

    private Expression parseCommentConstructor(int offset) throws XPathException {
        nextToken();
        Expression value;
        if (t.currentToken == Token.RCURLY && languageVersion >= 31) {
            value = Literal.makeEmptySequence();
        } else {
            value = parseExpression();
        }
        readToken(Token.RCURLY);
        Comment com = new Comment();
        makeSimpleContent(value, com, offset);
        return makeTracer(com, null);
    }

    /**
     * Parse a processing instruction constructor of the form
     * processing-instruction {expr} {expr}
     *
     * @param offset the position of the expression in the source query
     * @return the compiled instruction
     * @throws XPathException if parsing fails
     */

    private Expression parseProcessingInstructionConstructor(int offset) throws XPathException {
        nextToken();
        Expression name = parseExpression();
        readToken(Token.RCURLY);
        readToken(Token.LCURLY);
        Expression content = null;
        if (t.currentToken != Token.RCURLY) {
            content = parseExpression();
            expect(Token.RCURLY);
        }
        nextToken();
        ProcessingInstruction pi = new ProcessingInstruction(name);
        makeSimpleContent(content, pi, offset);
        return makeTracer(pi, null);
    }

    /**
     * Parse a processing instruction constructor of the form
     * processing-instruction name { expr }
     *
     * @param offset position of the expression in the source
     * @return the parsed expression
     * @throws XPathException if a static error is found
     */

    private Expression parseNamedProcessingInstructionConstructor(String target, int offset) throws XPathException {
        String warningMessage = null;
        if (target.equalsIgnoreCase("xml")) {
            warningMessage = "A processing instruction must not be named 'xml' in any combination of upper and lower case";
        }
        if (!NameChecker.isValidNCName(target)) {
            grumble("Invalid processing instruction name " + Err.wrap(target));
        }
        Expression piName = new StringLiteral(target);
        Expression piContent = null;
        nextToken();
        readToken(Token.LCURLY);
        if (t.currentToken != Token.RCURLY) {
            piContent = parseExpression();
            expect(Token.RCURLY);
        }
        nextToken();
        if (warningMessage == null) {
            ProcessingInstruction pi2 = new ProcessingInstruction(piName);
            makeSimpleContent(piContent, pi2, offset);
            return makeTracer(pi2, null);
        } else {
            warning(warningMessage, "XQDY0064");
            return new ErrorExpression(warningMessage, "XQDY0064", false);
        }
    }

    /**
     * Parse a Try/Catch Expression.
     * This construct is XQuery-3.0 only. The syntax allows:
     * try { Expr } catch NameTest ('|' NameTest)* { Expr }
     * We don't currently implement the CatchVars
     */

    @Override
    protected Expression parseTryCatchExpression() throws XPathException {
        if (languageVersion < 30) {
            grumble("try/catch requires XQuery 3.0");
        }
        readKeyword("try");
        readToken(Token.LCURLY);
        int offset = t.currentTokenStartOffset;
        //nextToken();
        Expression tryExpr;
        if (t.currentToken == Token.RCURLY && languageVersion >= 31) {
            tryExpr = Literal.makeEmptySequence();
        } else {
            tryExpr = parseExpression();
        }
        TryCatch tryCatch = new TryCatch(tryExpr);
        setLocation(tryCatch, offset);
        readToken(Token.RCURLY);
        boolean foundOneCatch = false;
        List<QNameTest> tests = new ArrayList<>();
        while (isKeyword("catch")) {
            tests.clear();
            foundOneCatch = true;
            do {
                nextToken();
                Token tok = t.currentToken;
                if (tok instanceof Token.Wildcard) {
                    String prefix = ((Token.Wildcard) tok).getPrefix();
                    String suffix = ((Token.Wildcard) tok).getSuffix();
                    if (prefix.equals("*")) {
                        tests.add(makeLocalQNameTest(suffix));
                    } else {
                        tests.add(makeNamespaceQNameTest(prefix));
                    }
                } else if (tok instanceof Token.NameToken) {
                    tests.add(makeQNameTest(((Token.NameToken)tok).getValue()));
                } else if (tok == Token.STAR) {
                    tests.add(AnyQNameTest.getInstance());
                } else {
                    grumble("Unrecognized name test in catch clause at " + t.currentToken);
                    return null;
                }
                nextToken();
            } while (t.currentToken == Token.VBAR);
            QNameTest test;
            if (tests.size() == 1) {
                test = tests.get(0);
            } else {
                test = new UnionQNameTest(tests);
            }
            catchDepth++;
            Expression catchExpr;
            readToken(Token.LCURLY);
            if (t.currentToken == Token.RCURLY && languageVersion >= 31) {
                catchExpr = Literal.makeEmptySequence();
            } else {
                catchExpr = parseExpression();
            }
            tryCatch.addCatchExpression(test, catchExpr);
            readToken(Token.RCURLY);
            catchDepth--;
        }
        if (isKeyword("finally")) {
            Expression finallyExpr;
            foundOneCatch = true;
            nextToken();
            readToken(Token.LCURLY);
            if (t.currentToken == Token.RCURLY) {
                finallyExpr = Literal.makeEmptySequence();
            } else {
                finallyExpr = parseExpression();
            }
            readToken(Token.RCURLY);
            tryCatch.setFinallyExpression(finallyExpr);
        }
        if (!foundOneCatch) {
            grumble("After try{}, expected 'catch'" +
                    (languageVersion >= 40 ? " or 'finally'" : "") );
        }

        return tryCatch;
    }

    /**
     * Parse a computed namespace constructor of the form
     * namespace {expr}{expr}
     *
     * @param offset the location of the expression in the query source
     * @return the compiled Namespace instruction
     * @throws XPathException in the event of a syntax error
     */

    /*@NotNull*/
    private Expression parseNamespaceConstructor(int offset) throws XPathException {
        if (languageVersion < 30) {
            grumble("Namespace node constructors require XQuery 3.0");
        }
        nextToken();
        Expression nameExpr = parseExpression();
        readToken(Token.RCURLY);
        readToken(Token.LCURLY);
        Expression content = null;
        if (t.currentToken != Token.RCURLY) {
            content = parseExpression();
            expect(Token.RCURLY);
        }
        nextToken();

        NamespaceConstructor instr = new NamespaceConstructor(nameExpr);
        setLocation(instr);
        makeSimpleContent(content, instr, offset);
        return makeTracer(instr, null);
    }

    /**
     * Parse a namespace node constructor of the form
     * namespace name { expr }
     *
     * @param offset the location of the expression in the query source
     * @return the compiled instruction
     * @throws XPathException in the event of a syntax error
     */

    /*@NotNull*/
    private Expression parseNamedNamespaceConstructor(String prefix, int offset) throws XPathException {
        if (languageVersion < 30) {
            grumble("Namespace node constructors require XQuery 3.0");
        }
        if (!NameChecker.isValidNCName(prefix)) {
            grumble("Invalid namespace prefix " + Err.wrap(prefix));
        }
        Expression nsName = new StringLiteral(prefix);
        Expression nsContent = null;
        nextToken();
        readToken(Token.LCURLY);
        if (t.currentToken != Token.RCURLY) {
            nsContent = parseExpression();
            expect(Token.RCURLY);
        }
        nextToken();
        NamespaceConstructor instr = new NamespaceConstructor(nsName);
        makeSimpleContent(nsContent, instr, offset);
        return makeTracer(instr, null);
    }


    /**
     * Make the instructions for the children of a node with simple content (attribute, text, PI, etc)
     *
     * @param content the expression making up the simple content
     * @param inst    the skeletal instruction for creating the node
     * @param offset  the character position of this construct within the source query
     */

    protected void makeSimpleContent(Expression content, SimpleNodeConstructor inst, int offset) {
        if (content == null) {
            inst.setSelect(new StringLiteral(StringValue.EMPTY_STRING));
        } else {
            inst.setSelect(stringify(content, false, env));
        }
        setLocation(inst, offset);
    }

    /**
     * Parse pseudo-XML syntax in direct element constructors, comments, CDATA, etc.
     * This is handled by reading single characters from the Tokenizer until the
     * end of the tag (or an enclosed expression) is enountered.
     * This method is also used to read an end tag. Because an end tag is not an
     * expression, the method in this case returns a StringValue containing the
     * contents of the end tag.
     *
     * @param allowEndTag true if the context allows an End Tag to appear here
     * @return an Expression representing the result of parsing the constructor.
     * If an end tag was read, its contents will be returned as a StringValue.
     * @throws XPathException if parsing fails
     */

    /*@NotNull*/
    private Expression parsePseudoXML(boolean allowEndTag) throws XPathException {
        Expression exp;
        int offset = t.inputOffset;
        // we're reading raw characters, so we don't want the currentTokenStartOffset
        char c = t.nextChar();
        switch (c) {
            case '!' -> {
                c = t.nextChar();
                if (c == '-') {
                    exp = parseCommentConstructor();
                } else if (c == '[') {
                    grumble("A CDATA section is allowed only in element content");
                    return null;
                    // if CDATA were allowed here, we would have already read it
                } else {
                    grumble("Expected '--' or '[CDATA[' after '<!'");
                    return null;
                }
            }
            case '?' -> exp = parsePIConstructor();
            case '/' -> {
                if (allowEndTag) {
                    StringBuilder sb = new StringBuilder(16);
                    while (true) {
                        c = t.nextChar();
                        if (c == '>') {
                            break;
                        } else if (c == Tokenizer.NUL) {
                            grumble("Expected '>' after '/'; found end of input");
                        }
                        sb.append(c);
                    }
                    return new StringLiteral(sb.toString());
                }
                grumble("Unmatched XML end tag");
                return new ErrorExpression();
            }
            case Tokenizer.NUL -> {
                grumble("End of input encountered while parsing direct constructor");
                return new ErrorExpression();
            }
            default -> {
                t.unreadChar();
                exp = parseDirectElementConstructor(allowEndTag);
            }
        }
        setLocation(exp, offset);
        return exp;
    }

    /**
     * Parse a direct element constructor
     *
     * @param isNested true if this constructor is nested directly as part of the content of another
     *                 element constructor. This has the effect that the child element is not copied, which means
     *                 that namespace inheritance (which only happens during copying) has no effect
     * @return the expression representing the constructor
     * @throws XPathException if a syntax error is found
     */

    protected Expression parseDirectElementConstructor(boolean isNested) throws XPathException {
        NamePool pool = env.getConfiguration().getNamePool();
        boolean changesContext = false;
        int offset = t.inputOffset - 1;
        // we're reading raw characters, so we don't want the currentTokenStartOffset
        char c;
        StringBuilder buff = new StringBuilder(64);
        int namespaceCount = 0;
        while (true) {
            c = t.nextChar();
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '/' || c == '>') {
                break;
            } else if (c == Tokenizer.NUL) {
                grumble("Found end of input while reading element name in XQuery element constructor");
            }
            buff.append(c);
        }
        String elname = buff.toString();
        if (elname.isEmpty()) {
            grumble("Expected element name after '<'");
        }

        Set<String> lexicalNames = new HashSet<>(10);
        List<AttributeDetails> attributes = new ArrayList<>(10);
        while (true) {
            // loop through the attributes
            // We must process namespace declaration attributes first;
            // their scope applies to all preceding attribute names and values.
            // But finding the delimiting quote of an attribute value requires the
            // XPath expressions to be parsed, because they may contain nested quotes.
            // So we parse in "scanOnly" mode, which ignores any undeclared namespace
            // prefixes, use the result of this parse to determine the length of the
            // attribute value, save the value, and reparse it when all the namespace
            // declarations have been dealt with.
            c = skipSpaces(c);
            if (c == '/' || c == '>') {
                break;
            } else if (c == Tokenizer.NUL) {
                grumble("End of input encountered within element start tag");
            } 
            int attOffset = t.inputOffset - 1;
            buff.setLength(0);
            // read the attribute name
            do {
                buff.append(c);
                c = t.nextChar();
            } while (c != ' ' && c != '\n' && c != '\r' && c != '\t' && c != '=' && c != Tokenizer.NUL);
            String attName = buff.toString();
            if (!NameChecker.isQName(StringTool.codePoints(attName))) {
                grumble("Invalid attribute name " + Err.wrap(attName, Err.ATTRIBUTE));
            }
            c = skipSpaces(c);
            expectChar(c, '=');
            c = t.nextChar();
            c = skipSpaces(c);

            if (c != '"' && c != '\'') {
                if (c == Tokenizer.NUL) {
                    grumble("End of input encountered within element start tag");
                } else {
                    grumble("Expected ' or \" as attribute delimiter - found '" + c + "'");
                }
            }
            char delim = c;
            if (c != '"' && c != '\'') {
                grumble("Expected ' or \" as attribute delimiter - found '" + c + "'");
            }
            boolean isNamespace = "xmlns".equals(attName) || attName.startsWith("xmlns:");
            int end;
            attOffset = t.inputOffset;
            if (isNamespace) {
                attOffset--;
                end = makeNamespaceContent(t.input, t.inputOffset, delim) + 1;
                changesContext = true;
            } else {
                Expression avt;
                try {
                    end = scanAttributeContent(delim);
                } catch (XPathException err) {
                    if (!err.hasBeenReported()) {
                        grumble(err.getMessage());
                    }
                    throw err;
                }

            }
            if (end >= t.input.length()) {
                grumble("Reached end of input while processing attributes in start tag");
            }
            // save the value with its surrounding quotes
            String val = t.input.substring(attOffset-1, end);
            // and without
            String rval = t.input.substring(attOffset, end-1);

            // account for any newlines found in the value
            // (note, subexpressions between curlies will have been parsed using a different tokenizer)
            String tail = val;
            int pos;
            while ((pos = tail.indexOf('\n')) >= 0) {
                t.incrementLineNumber(t.inputOffset - 1 + pos);
                tail = tail.substring(pos + 1);
            }
            t.inputOffset = end + 1;

            if (isNamespace) {
                // Processing follows the resolution of bug 5083: doubled curly braces represent single
                // curly braces, single curly braces are not allowed.
                StringBuilder sb = new StringBuilder(rval.length());
                boolean prevDelim = false;
                boolean prevOpenCurly = false;
                boolean prevCloseCurly = false;
                for (int i = 0; i < rval.length(); i++) {
                    char n = rval.charAt(i);
                    if (n == delim) {
                        prevDelim = !prevDelim;
                        if (prevDelim) {
                            continue;
                        }
                    }
                    if (n == '{') {
                        prevOpenCurly = !prevOpenCurly;
                        if (prevOpenCurly) {
                            continue;
                        }
                    } else if (prevOpenCurly) {
                        grumble("Namespace must not contain an unescaped opening brace", "XQST0022");
                    }
                    if (n == '}') {
                        prevCloseCurly = !prevCloseCurly;
                        if (prevCloseCurly) {
                            continue;
                        }
                    } else if (prevCloseCurly) {
                        grumble("Namespace must not contain an unescaped closing brace", "XPST0003");
                    }
                    sb.append(n);
                }
                if (prevOpenCurly) {
                    grumble("Namespace must not contain an unescaped opening brace", "XQST0022");
                }
                if (prevCloseCurly) {
                    grumble("Namespace must not contain an unescaped closing brace", "XPST0003");
                }
                rval = sb.toString();
                NamespaceUri uri = NamespaceUri.of(uriLiteral(rval));
                if (!StandardURIChecker.getInstance().isValidURI(uri.toString())) {
                    grumble("Namespace must be a valid URI value", "XQST0046");
                }
                String prefix;
                if ("xmlns".equals(attName)) {
                    prefix = "";
                    if (uri.equals(NamespaceUri.XML)) {
                        grumble("Cannot have the XML namespace as the default namespace", "XQST0070");
                    }
                } else {
                    prefix = attName.substring(6);
                    if (prefix.equals("xml") && !uri.equals(NamespaceUri.XML)) {
                        grumble("Cannot bind the prefix 'xml' to a namespace other than the XML namespace", "XQST0070");
                    } else if (uri.equals(NamespaceUri.XML) && !prefix.equals("xml")) {
                        grumble("Cannot bind a prefix other than 'xml' to the XML namespace", "XQST0070");
                    } else if (prefix.equals("xmlns")) {
                        grumble("Cannot use xmlns as a namespace prefix", "XQST0070");
                    }

                    if (uri.isEmpty()) {
                        if (env.getConfiguration().getXMLVersion() == Configuration.XML10) {
                            grumble("Namespace URI must not be empty", "XQST0085");
                        }
                    }
                }
                namespaceCount++;
                ((QueryModule) env).declareConstructedNamespace(prefix, uri);
            }
            if (lexicalNames.contains(attName)) {
                if (isNamespace) {
                    grumble("Duplicate namespace declaration " + attName, "XQST0071", attOffset);
                } else {
                    grumble("Duplicate attribute name " + attName, "XQST0040", attOffset);
                }
            }
            lexicalNames.add(attName);
//            if (attName.equals("xml:id") && !NameChecker.isValidNCName(rval)) {
//                grumble("Value of xml:id must be a valid NCName", "XQST0082");
//            }
            AttributeDetails a = new AttributeDetails(attName, val, attOffset, end);
            attributes.add(a);

            // on return, the current character is the closing quote
            //c = t.nextChar();
            c = t.input.charAt(t.inputOffset - 1);
            if (!(c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '/' || c == '>')) {
                grumble("There must be whitespace after every attribute except the last");
            }
        }
        StructuredQName qName = null;
        if (scanOnly) {
            qName = StandardNames.getStructuredQName(StandardNames.XSL_ELEMENT);  // any name will do
        } else {
            try {
                String[] parts = NameChecker.getQNameParts(elname);
                NamespaceUri namespace = ((QueryModule) env).checkURIForPrefix(parts[0]);
                if (namespace == null) {
                    grumble("Undeclared prefix in element name " + Err.wrap(elname, Err.ELEMENT), "XPST0081", offset);
                }
                qName = new StructuredQName(parts[0], namespace, parts[1]);
            } catch (QNameException e) {
                grumble("Invalid element name " + Err.wrap(elname, Err.ELEMENT), "XPST0003", offset);
                qName = StandardNames.getStructuredQName(StandardNames.XSL_ELEMENT);  // any name will do
            }
        }
        int validationMode = ((QueryModule) env).getConstructionMode();
        FingerprintedQName fqn = new FingerprintedQName(
                qName.getPrefix(), qName.getNamespaceUri(), qName.getLocalPart(), pool.allocateFingerprint(qName.getNamespaceUri(), qName.getLocalPart()));
        Schema schema = env.getImportedSchema();
        FixedElement elInst = new FixedElement(fqn,
                                               ((QueryModule) env).getActiveNamespaceBindings(),
                                               ((QueryModule) env).isInheritNamespaces(),
                                               !isNested,
                                               schema, null,
                                               validationMode);

        setLocation(elInst, offset);

        List<Expression> contents = new ArrayList<>(10);

        IntHashSet attFingerprints = new IntHashSet(attributes.size());
        // we've checked for duplicate lexical QNames, but not for duplicate expanded-QNames
        for (AttributeDetails a : attributes) {
            String attName = a.lexicalName();
            String attValue = a.value();
            int attOffset = a.startOffset();

            if ("xmlns".equals(attName) || attName.startsWith("xmlns:")) {
                // do nothing
            } else if (scanOnly) {
                // This means we are prescanning an attribute constructor, and we found a nested attribute
                // constructor, which we have prescanned; we now don't want to re-process the nested attribute
                // constructor because it might depend on things like variables declared in the containing
                // attribute constructor, and in any case we're going to come back to it again later.
                // See test qxmp180
            } else {

                // Process the attribute this time "for real". We use the same query parser,
                // but allocating a new Tokenizer
                NodeName attributeName = null;
                NamespaceUri attNamespace;
                try {
                    String[] parts = NameChecker.getQNameParts(attName);
                    if (parts[0].isEmpty()) {
                        // attributes don't use the default namespace
                        attNamespace = NamespaceUri.NULL;
                    } else {
                        attNamespace = ((QueryModule) env).checkURIForPrefix(parts[0]);
                    }
                    if (attNamespace == null) {
                        grumble("Undeclared prefix in attribute name " +
                                        Err.wrap(attName, Err.ATTRIBUTE), "XPST0081", attOffset);
                    }
                    attributeName = new FingerprintedQName(parts[0], attNamespace, parts[1]);
                    int key = attributeName.obtainFingerprint(pool);
                    if (attFingerprints.contains(key)) {
                        grumble("Duplicate expanded attribute name " + attName, "XQST0040", attOffset);
                    }
                    attFingerprints.add(key);
                } catch (QNameException e) {
                    grumble("Invalid attribute name " + Err.wrap(attName, Err.ATTRIBUTE), "XPST0003", attOffset);
                }

                assert attributeName != null;
                FixedAttribute attInst =
                        new FixedAttribute(attributeName, Validation.STRIP, null);

                setLocation(attInst);
                Expression select;
                try {
                    select = makeAttributeContent(attValue, attOffset);
                } catch (XPathException err) {
                    err.setIsStaticError(true);
                    throw err;
                }
                attInst.setRetainedStaticContext(env.makeRetainedStaticContext());
                attInst.setSelect(select);
                attInst.setRejectDuplicates();
                setLocation(attInst);
                contents.add(makeTracer(attInst,
                                        attributeName.getStructuredQName()));
            }
        }
        if (c == '/') {
            // empty element tag
            expectChar(t.nextChar(), '>');
        } else {
            readElementContent(elname, contents);
        }

        Expression[] elk = new Expression[contents.size()];
        for (int i = 0; i < contents.size(); i++) {
            // if the child expression creates another element,
            // suppress validation, as the parent already takes care of it
            if (validationMode != Validation.STRIP) {
                contents.get(i).suppressValidation(validationMode);
            }
            elk[i] = contents.get(i);
        }
        Block block = new Block(elk);
        if (changesContext) {
            block.setRetainedStaticContext(env.makeRetainedStaticContext());
        }
        elInst.setContentExpression(block);

        // reset the in-scope namespaces to what they were before

        for (int n = 0; n < namespaceCount; n++) {
            ((QueryModule) env).undeclareNamespace();
        }

        if (!isNested) {
            t.restart();
        }
        return makeTracer(elInst, qName);
    }

    /**
     * Scan an attribute within a direct element constructor, to determine where the attribute ends.
     * This pre-scan is needed because we can't do a full analysis of embedded expressions until
     * we know the namespace context. Namespace declarations may follow the attribute in question,
     * and we can't find them until we can identify the boundaries of earler attributes.
     *
     * <p>On entry, the tokenizer is positioned at the opening delimiter of the attribute (a single
     * or double quote mark). The method returns the position of the closing delimiter of the
     * attribute. All information obtained by parsing embedded expressions is discarded.</p>
     *
     * @return the offset of the closing delimiter of the attribute value
     * @throws XPathException if parsing fails
     */

    /*@Nullable*/
    private int scanAttributeContent(char delimiter) throws XPathException {

        while (true) {
            char c = t.nextChar();
            if (c == Tokenizer.NUL) {
                grumble("Unclosed attribute in element constructor");
                return t.input.length();
            }
            if (c == delimiter) {
                if (t.peekChar() == delimiter) {
                    c = t.nextChar();
                } else {
                    return t.inputOffset;
                }
            } else if (c == '{' && t.peekChar() != '{') {
                int offset = t.inputOffset;
                // Process an embedded expression
                t.startEmbeddedExpression();
                if (t.currentToken != Token.EOF) {
                    boolean savedScanOnly = scanOnly;
                    scanOnly = true;
                    parseExpression();
                    scanOnly = savedScanOnly;
                    expect(Token.EOF);
                }
                if (t.inputOffset >= t.input.length()) {
                    grumble("Embedded expression not terminated", "XPST0003", offset);
                }
                t.endEmbeddedExpression();
            }
        }
    }

    /**
     * Parse the content of an attribute in a direct element constructor. This may contain nested expressions
     * within curly braces. A particular problem is that the namespaces used in the expression may not yet be
     * known. This means we need the ability to parse in "scanOnly" mode, where undeclared namespace prefixes
     * are ignored.
     * <p>The code was originally based on the XSLT code in {@link AttributeValueTemplate#make}: the main difference is that
     * character entities and built-in entity references need to be recognized and expanded. Also, whitespace
     * needs to be normalized, mimicking the action of an XML parser. An additional difference is that in
     * XSLT, we know where an attribute ends in advance, but in XQuery, we can only discover this by
     * (at least partially) parsing any embedded expressions.</p>
     *
     * @param avt        the content of the attribute as written, including delimiters, and variable
     *                   portions enclosed in curly braces
     * @param offset      the position of the attribute within the overall expression, for diagnostics
     * @return the expression that will evaluate the content of the attribute
     * @throws XPathException if parsing fails
     */

    /*@Nullable*/
    private Expression makeAttributeContent(String avt, int offset) throws XPathException {

        List<Expression> components = new ArrayList<>();
        StringBuilder currentPart = new StringBuilder();
        char delimiter = avt.charAt(0);
        int pos = 1;
        while (true) {
            char c = avt.charAt(pos++);
            if (c == delimiter) {
                // The only significant delimiting quote is the one at the end; any others must be doubled
                if (pos == avt.length()) {
                    if (currentPart.length() > 0) {
                        addStringComponent(components, currentPart.toString(), 0, currentPart.length());
                        currentPart.setLength(0);
                    }
                    Expression[] args = components.toArray(new Expression[0]);
                    Expression result = SystemFunction.makeCall("concat", env.makeRetainedStaticContext(), args);
                    setLocation(result, offset);
                    return result;
                } else {
                    currentPart.append(delimiter);
                    pos++;
                }
            } else if (c == Tokenizer.NUL) {
                grumble("Unclosed attribute in element constructor");
                return null;
            } else if (c == '{') {
                if (avt.charAt(pos) == '{') {
                    currentPart.append('{');
                    pos++;
                } else {
                    if (currentPart.length() > 0) {
                        addStringComponent(components, currentPart.toString(), 0, currentPart.length());
                        currentPart.setLength(0);
                    }
                    Tokenizer savedTokenizer = t;
                    t = new Tokenizer();
                    t.isXQuery = true;
                    t.languageLevel = savedTokenizer.languageLevel;
                    t.setFinishCondition(Tokenizer.CLOSING_CURLY);
                    t.tokenize(avt, pos, avt.length());
                    if (t.currentToken != Token.EOF) {
                        Expression exp = parseExpression();
                        RetainedStaticContext rscSJ = new RetainedStaticContext(env);
                        Expression fnSJ = SystemFunction.makeCall("string-join", rscSJ, exp, new StringLiteral(StringValue.SINGLE_SPACE));
                        ExpressionTool.copyLocationInfo(exp, fnSJ);
                        components.add(fnSJ);
                        expect(Token.EOF);
                    }
                    pos = t.inputOffset + 1;
                    t = savedTokenizer;
                }
            } else if (c == '}') {
                if (avt.charAt(pos) == '}') {
                    currentPart.append('}');
                    pos++;
                } else {
                    grumble("Closing brace ('}') in direct element constructor must be doubled");
                }
            } else {
                currentPart.append(c);
            }
        }
    }

    private void addStringComponent(/*@NotNull*/ List<Expression> components, /*@NotNull*/ String avt, int start, int end)
            throws XPathException {
        // analyze fixed text within the value of a direct attribute constructor.
        if (start < end) {
            StringBuilder sb = new StringBuilder(end - start);
            for (int i = start; i < end; i++) {
                char c = avt.charAt(i);
                switch (c) {
                    case '&' -> {
                        int semic = avt.indexOf(';', i);
                        if (semic < 0) {
                            grumble("No closing ';' found for entity or character reference");
                        } else {
                            String entity = avt.substring(i + 1, semic);
                            sb.append(new Unescaper(env.getConfiguration().getValidCharacterChecker()).analyzeEntityReference(entity));
                            i = semic;
                        }
                    }
                    case '<' -> grumble("The < character must not appear in attribute content");
                    case '\n', '\t' -> sb.append(' ');
                    case '\r' -> {
                        sb.append(' ');
                        if (i + 1 < end && avt.charAt(i + 1) == '\n') {
                            i++;
                        }
                    }
                    default -> sb.append(c);
                }
            }
            components.add(new StringLiteral(sb.toString()));
        }
    }

    /**
     * Parse the content of a namespace declaration attribute in a direct element constructor. This is simpler
     * than an ordinary attribute because it does not contain nested expressions in curly braces. (But see bug 5083).
     *
     * @param avt        the content of the attribute as written, including variable portions enclosed in curly braces
     * @param start      the character position in the attribute value where parsing should start
     * @param terminator a character that is to be taken as marking the end of the expression
     * @return the position of the end of the URI value
     * @throws XPathException if parsing fails
     */

    private int makeNamespaceContent(/*@NotNull*/ String avt, int start, char terminator) throws XPathException {

        int i2, len, last;
        last = start;
        len = avt.length();
        while (last < len) {
            i2 = avt.indexOf(terminator, last);
            if (i2 < 0) {
                XPathException e = new XPathException("Namespace declaration is not properly terminated");
                e.setIsStaticError(true);
                throw e;
            }

            // look for doubled quotes, and skip them (for now)
            if (i2 + 1 < avt.length() && avt.charAt(i2 + 1) == terminator) {
                last = i2 + 2;
                //continue;
            } else {
                last = i2;
                break;
            }
        }

        // return the position of the end of the literal
        return last;

    }

    /**
     * Read the content of a direct element constructor (that is, the content between the start tag
     * and end tag)
     *
     * @param startTag   the element start tag
     * @param components an empty list, to which the expressions comprising the element contents are added
     * @throws XPathException if any static errors are detected
     */
    private void readElementContent(String startTag, List<Expression> components) throws XPathException {
        boolean afterEnclosedExpr = false;
        while (true) {
            // read all the components of the element value
            StringBuilder text = new StringBuilder(64);
            char c;
            boolean containsEntities = false;
            while (true) {
                c = t.nextChar();
                if (c == '<') {
                    // See if we've got a CDATA section
                    if (t.nextChar() == '!') {
                        if (t.nextChar() == '[') {
                            readCDATASection(text);
                            containsEntities = true;
                            continue;
                        } else {
                            t.unreadChar();
                            t.unreadChar();
                        }
                    } else {
                        t.unreadChar();
                    }
                    break;
                } else if (c == '&') {
                    text.append(readEntityReference());
                    containsEntities = true;
                } else if (c == '}') {
                    c = t.nextChar();
                    if (c != '}') {
                        grumble("'}' must be written as '}}' within element content");
                    }
                    text.append(c);
                } else if (c == '{') {
                    if (t.peekChar() == '{') {
                        text.append('{');
                        t.nextChar();
                    } else {
                        break;
                    }
                } else if (c == Tokenizer.NUL) {
                    grumble("Reached end of input while reading XQuery element content");
                } else {
                    if (!charChecker.test(c) && !UTF16CharacterSet.isSurrogate(c)) {
                        grumble("Character code " + c + " is not a valid XML character");
                    }
                    text.append(c);
                }
            }
            String textStr = text.toString();
            if (!textStr.isEmpty() &&
                    (containsEntities |
                             ((QueryModule) env).isPreserveBoundarySpace() ||
                             !Whitespace.isAllWhite(StringView.of(textStr)))) {
                ValueOf inst = new ValueOf(new StringLiteral(new StringValue(textStr)), false, false);
                setLocation(inst);
                components.add(inst);
                afterEnclosedExpr = false;
            }
            if (c == '<') {
                Expression exp = parsePseudoXML(true);
                // An end tag can appear here, and is returned as a string value
                if (exp instanceof StringLiteral) {
                    String endTag = ((StringLiteral) exp).stringify();
                    if (Whitespace.isWhite(endTag.charAt(0))) {
                        grumble("End tag contains whitespace before the name");
                    }
                    endTag = Whitespace.trim(endTag);
                    if (endTag.equals(startTag)) {
                        return;
                    } else {
                        grumble("End tag </" + endTag +
                                        "> does not match start tag <" + startTag + '>', "XQST0118");
                        // error code allocated by spec bug 11609
                    }
                } else {
                    components.add(exp);
                }
            } else {
                // we read an '{' indicating an enclosed expression
                if (afterEnclosedExpr) {
                    Expression previousComponent = components.get(components.size() - 1);
                    boolean previousComponentIsNodeTest = true;
                    UType previousItemType = previousComponent.getStaticUType(UType.ANY);
                    previousComponentIsNodeTest = UType.XNODE.subsumes(previousItemType);
                    if (!previousComponentIsNodeTest) {
                        // Add a zero-length text node, to prevent {"a"}{"b"} generating an intervening space
                        // See tests (qxmp132, qxmp261)
                        ValueOf inst = new ValueOf(new StringLiteral(StringValue.EMPTY_STRING), false, false);
                        setLocation(inst);
                        components.add(inst);
                    }
                }
                t.startEmbeddedExpression();
                if (t.currentToken != Token.EOF) {
                    Expression exp = parseExpression();
                    if (!((QueryModule) env).isPreserveNamespaces()) {
                        exp = new CopyOf(exp, false, Validation.PRESERVE, null, true);
                    }
                    components.add(exp);
                    expect(Token.EOF);
                }
                t.endEmbeddedExpression();
                afterEnclosedExpr = true;
            }
        }
    }

    /*@Nullable*/
    private Expression parsePIConstructor() throws XPathException {

        StringBuilder pi = new StringBuilder(64);
        int firstSpace = -1;
        while (!pi.toString().endsWith("?>")) {
            char c = t.nextChar();
            if (c == Tokenizer.NUL) {
                grumble("Found end of input while reading processing instruction constructor");
            }
            if (firstSpace < 0 && " \t\r\n".indexOf(c) >= 0) {
                firstSpace = pi.length();
            }
            pi.append(c);
        }
        pi.setLength(pi.length() - 2);

        String target;
        String data = "";
        if (firstSpace < 0) {
            // there is no data part
            target = pi.toString();
        } else {
            // trim leading space from the data part, but not trailing space
            target = pi.substring(0, firstSpace);
            firstSpace++;
            while (firstSpace < pi.length() && " \t\r\n".indexOf(pi.charAt(firstSpace)) >= 0) {
                firstSpace++;
            }
            data = pi.substring(firstSpace);
        }

        if (!NameChecker.isValidNCName(target)) {
            grumble("Invalid processing instruction name " + Err.wrap(target));
        }

        if (target.equalsIgnoreCase("xml")) {
            grumble("A processing instruction must not be named 'xml' in any combination of upper and lower case");
        }

        ProcessingInstruction instruction =
                new ProcessingInstruction(new StringLiteral(target));
        instruction.setSelect(new StringLiteral(data));
        setLocation(instruction);
        return instruction;

    }

    private void readCDATASection(StringBuilder cdata) throws XPathException {
        char c;
        // CDATA section
        c = t.nextChar();
        expectChar(c, 'C');
        c = t.nextChar();
        expectChar(c, 'D');
        c = t.nextChar();
        expectChar(c, 'A');
        c = t.nextChar();
        expectChar(c, 'T');
        c = t.nextChar();
        expectChar(c, 'A');
        c = t.nextChar();
        expectChar(c, '[');
        while (!cdata.toString().endsWith("]]>")) {
            char cc = t.nextChar();
            if (cc == Tokenizer.NUL) {
                grumble("No closing ']]>' found for CDATA section");
            }
            cdata.append(cc);
        }
        cdata.setLength(cdata.length() - 3);
    }

    /*@Nullable*/
    private Expression parseCommentConstructor() throws XPathException {

        char c = t.nextChar();
        // XML-like comment
        expectChar(c, '-');
        StringBuilder comment = new StringBuilder(256);
        while (!comment.toString().endsWith("--")) {
            char cc = t.nextChar();
            if (cc == Tokenizer.NUL) {
                grumble("Reached end of input while reading XML comment constructor");
            }
            comment.append(cc);
        }
        if (t.nextChar() != '>') {
            grumble("'--' is not permitted in an XML comment");
        }
        String commentText = comment.substring(0, comment.length() - 2);
        Comment instruction = new Comment();
        instruction.setSelect(new StringLiteral(new StringValue(commentText)));
        setLocation(instruction);
        return instruction;
    }

    /**
     * Convert an expression so it generates a space-separated sequence of strings
     *
     * @param exp           the expression that calculates the content
     * @param noNodeIfEmpty if true, no node is produced when the value of the content
     *                      expression is an empty sequence. If false, the effect of supplying an empty sequence
     *                      is that a node is created whose string-value is a zero-length string. Set to true for
     *                      text node constructors, false for other kinds of node.
     * @param env           the static context
     * @return an expression that computes the content and converts the result to a character string
     */

    public static Expression stringify(Expression exp, boolean noNodeIfEmpty, StaticContext env) {
        // Compare with XSLLeafNodeConstructor.makeSimpleContentConstructor
        // Fast path if given a string literal
        if (exp instanceof StringLiteral) {
            return exp;
        }
        if (exp.getLocalRetainedStaticContext() == null) {
            exp.setRetainedStaticContext(env.makeRetainedStaticContext());
        }
        // Atomize the result
        exp = Atomizer.makeAtomizer(exp, null);
        // Convert each atomic value to a string
        exp = new AtomicSequenceConverter(exp, BuiltInAtomicType.STRING);
        //((AtomicSequenceConverter) exp).allocateConverter(config, false);
        // Join the resulting strings with a separator
        exp = SystemFunction.makeCall("string-join", exp.getRetainedStaticContext(), exp, new StringLiteral(StringValue.SINGLE_SPACE));
        assert exp != null;
        if (noNodeIfEmpty) {
            ((StringJoin) ((SystemFunctionCall) exp).getTargetFunction()).setReturnEmptyIfEmpty(true);
        }
        // All that's left for the instruction to do is to construct the right kind of node
        return exp;
    }

    /**
     * Method to make a string literal from a token identified as a string
     * literal. This is trivial in XPath, but in XQuery the method is overridden
     * to identify pseudo-XML character and entity references
     *
     * @param token the string as written (or as returned by the tokenizer)
     * @param doUnescaping if true, XML escape sequences (`&amp;#xHH;`, etc) are
     *                     recognized and expanded.
     * @return The string value of the string literal, after dereferencing entity and
     * character references
     * @throws XPathException if parsing fails
     */

    @Override
    /*@NotNull*/
    protected Literal makeStringLiteral(/*@NotNull*/ String token, boolean doUnescaping) throws XPathException {
        if (doUnescaping) {
            StringLiteral lit;
            if (token.indexOf('&') == -1) {
                lit = new StringLiteral(token);
            } else {
                CharSequence sb = unescape(token);
                lit = new StringLiteral(StringValue.makeStringValue(sb));
            }
            setLocation(lit);
            return lit;
        } else {
            return super.makeStringLiteral(token, doUnescaping);
        }
    }

    /**
     * Unescape character references and built-in entity references in a string
     *
     * @param token the input string, which may include XML-style character references or built-in
     *              entity references
     * @return the string with character references and built-in entity references replaced by their expansion
     * @throws XPathException if a malformed character or entity reference is found
     */

    /*@NotNull*/
    @Override
    protected String unescape(/*@NotNull*/ String token) throws XPathException {
        return new Unescaper(env.getConfiguration().getValidCharacterChecker()).unescape(token);
    }

    public static class Unescaper {

        private final IntPredicateProxy characterChecker;

        public Unescaper(IntPredicateProxy characterChecker) {
            this.characterChecker = characterChecker;
        }

        public String unescape(String token) throws XPathException {
            StringBuilder sb = new StringBuilder(token.length());
            for (int i = 0; i < token.length(); i++) {
                char c = token.charAt(i);
                if (c == '&') {
                    int semic = token.indexOf(';', i);
                    if (semic < 0) {
                        throw new XPathException("No closing ';' found for entity or character reference", "XPST0003");
                    } else {
                        String entity = token.substring(i + 1, semic);
                        sb.append(analyzeEntityReference(entity));
                        i = semic;
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }


        /*@Nullable*/
        public String analyzeEntityReference(/*@NotNull*/ String entity) throws XPathException {
            if ("lt".equals(entity)) {
                return "<";
            } else if ("gt".equals(entity)) {
                return ">";
            } else if ("amp".equals(entity)) {
                return "&";
            } else if ("quot".equals(entity)) {
                return "\"";
            } else if ("apos".equals(entity)) {
                return "'";
            } else if (entity.length() < 2 || entity.charAt(0) != '#') {
                throw new XPathException("invalid character reference &" + entity + ';', "XPST0003");
            } else {
                //entity = entity.toLowerCase();
                return parseCharacterReference(entity);
            }
        }

        /*@Nullable*/
        private String parseCharacterReference(/*@NotNull*/ String entity) throws XPathException {
            int value = 0;
            if (entity.charAt(1) == 'x') {
                if (entity.length() < 3) {
                    throw new XPathException("No hex digits in hexadecimal character reference", "XPST0003");
                }
                entity = entity.toLowerCase();
                for (int i = 2; i < entity.length(); i++) {
                    int digit = "0123456789abcdef".indexOf(entity.charAt(i));
                    if (digit < 0) {
                        throw new XPathException("Invalid hex digit '" + entity.charAt(i) + "' in character reference", "XPST0003");
                    }
                    value = (value * 16) + digit;
                    if (value > UTF16CharacterSet.NONBMP_MAX) {
                        throw new XPathException("Character reference exceeds Unicode codepoint limit", "XQST0090");
                    }
                }
            } else {
                for (int i = 1; i < entity.length(); i++) {
                    int digit = "0123456789".indexOf(entity.charAt(i));
                    if (digit < 0) {
                        throw new XPathException("Invalid digit '" + entity.charAt(i) + "' in decimal character reference", "XPST0003");
                    }
                    value = (value * 10) + digit;
                    if (value > UTF16CharacterSet.NONBMP_MAX) {
                        throw new XPathException("Character reference exceeds Unicode codepoint limit", "XQST0090");
                    }
                }
            }

            if (!characterChecker.test(value)) {
                throw new XPathException("Invalid XML character reference x"
                                                 + Integer.toHexString(value), "XQST0090");
            }
            // following code borrowed from AElfred
            // Check for surrogates: 00000000 0000xxxx yyyyyyyy zzzzzzzz
            //  (1101|10xx|xxyy|yyyy + 1101|11yy|zzzz|zzzz:
            if (value <= 0x0000ffff) {
                // no surrogates needed
                return "" + (char) value;
            } else {
                assert value <= 0x0010ffff;
                value -= 0x10000;
                // > 16 bits, surrogate needed
                return "" + (char) (0xd800 | (value >> 10))
                        + (char) (0xdc00 | (value & 0x0003ff));
            }
        }

    }

    /**
     * Read a pseudo-XML character reference or entity reference.
     *
     * @return The character represented by the character or entity reference. Note
     * that this is a string rather than a char because a char only accommodates characters
     * up to 65535.
     * @throws XPathException if the character or entity reference is not well-formed
     */

    /*@Nullable*/
    private String readEntityReference() throws XPathException {
        StringBuilder sb = new StringBuilder(64);
        while (true) {
            char c = t.nextChar();
            if (c == ';') {
                break;
            } else if (c == Tokenizer.NUL) {
                grumble("No closing ';' found for entity or character reference");
                return "";     // to keep the Java compiler happy
            }
            sb.append(c);
        }
        String entity = sb.toString();
        return new Unescaper(env.getConfiguration().getValidCharacterChecker()).analyzeEntityReference(entity);
    }

    /**
     * Parse a string constructor: introduced in XQuery 3.1.
     * A StringConstructor is delimited by <code>"``[ ... ]``"</code>
     * and contains embedded expressions signalled by <code>"`{" Expr? "}`"</code>
     */

    @Override
    protected Expression parseStringConstructor() throws XPathException {

        int offset = t.currentTokenStartOffset;
        Token.StringConstructor constructor = (Token.StringConstructor) t.currentToken;
        List<Expression> components = new ArrayList<>();
        StringBuilder currentPart = new StringBuilder();
        boolean finished = false;
        // Absorb the leading ``[` identified by the tokenizer
        t.inputOffset += 2;
        do {
            char c = t.nextChar();
            switch (c) {
                case (char) 0 -> {
                    grumble("Unclosed string template");
                    return null;
                }
                case '`' -> {
                    if (t.peekChar() == '{') {
                        if (currentPart.length() > 0) {
                            components.add(new StringLiteral(currentPart.toString()));
                            currentPart.setLength(0);
                        }
                        t.nextChar();
                        t.startEmbeddedExpression();
                        if (t.currentToken != Token.EOF) {
                            Expression exp = parseExpression();
                            RetainedStaticContext rscSJ = new RetainedStaticContext(env);
                            Expression fnSJ = SystemFunction.makeCall("string-join", rscSJ, exp, new StringLiteral(StringValue.SINGLE_SPACE));
                            ExpressionTool.copyLocationInfo(exp, fnSJ);
                            components.add(fnSJ);
                            expect(Token.EOF);
                        }
                        t.endEmbeddedExpression();
                        char beyond = t.nextChar();
                        if (beyond != '`') {
                            grumble("In string constructor, an embedded expression must end with \"}`\"");
                        }
                    } else {
                        currentPart.append(c);
                    }
                }
                case ']' -> {
                    if (t.peekChar() == '`' && t.peekChar2() == '`') {
                        if (currentPart.length() > 0) {
                            components.add(new StringLiteral(currentPart.toString()));
                            currentPart.setLength(0);
                        }
                        t.nextChar();
                        t.nextChar();
                        finished = true;
                        constructor.close();
                    } else {
                        currentPart.append(c);
                    }
                }
                default -> currentPart.append(c);
            }
        } while (!finished);

        Expression[] args = components.toArray(new Expression[0]);
        Expression result = SystemFunction.makeCall("concat", env.makeRetainedStaticContext(), args);
        setLocation(result, offset);
        return result;



//        int offset = t.currentTokenStartOffset;
//        Token.StringConstructor template = ((Token.StringConstructor)t.currentToken);
//        if (!languageVersion >= 31) {
//            throw new XPathException("String constructor expressions require XQuery 3.1");
//        }
//
//        List<Expression> components = new ArrayList<>();
//
//        // Swallow the start delimiter
//        t.nextChar();
//        t.nextChar();
//
//        StringBuilder currentPart = new StringBuilder();
//        boolean finished = false;
//        do {
//            char c = t.nextChar();
//            switch (c) {
//                case (char) 0:
//                    grumble("Unclosed string constructor");
//                    return null;
//                case '`':
//                    if (t.peekChar() == '{') {
//                        if (currentPart.length() > 0) {
//                            components.add(new StringLiteral(currentPart.toString()));
//                            currentPart.setLength(0);
//                        }
//                        t.nextChar();
//                        t.startEmbeddedExpression();
//                        if (t.currentToken != Token.RCURLY) {
//                            Expression exp = parseExpression();
//                            RetainedStaticContext rscSJ = new RetainedStaticContext(env);
//                            Expression fnSJ = SystemFunction.makeCall("string-join", rscSJ, exp, new StringLiteral(StringValue.SINGLE_SPACE));
//                            ExpressionTool.copyLocationInfo(exp, fnSJ);
//                            components.add(fnSJ);
//                            expect(Token.RCURLY);
//                        }
//                        t.endEmbeddedExpression();
//                        if (t.nextChar() != '`') {
//                            grumble("Expected '}`' after enclosed expression in string constructor");
//                        }
//
//                    } else {
//                        currentPart.append('`');
//                    }
//                    break;
//                case ']':
//                    if (t.peekChar() == '`' && t.peekChar2() == '`') {
//                        if (currentPart.length() > 0) {
//                            components.add(new StringLiteral(currentPart.toString()));
//                            currentPart.setLength(0);
//                        }
//                        t.nextChar();  // Swallow the backticks
//                        t.nextChar();
//                        template.close();
//                        finished = true;
//                    } else {
//                        currentPart.append(']');
//                    }
//                    break;
//                default:
//                    currentPart.append(c);
//                    break;
//            }
//        } while (!finished);
//
//        Expression[] args = components.toArray(new Expression[0]);
//        Expression result = SystemFunction.makeCall("concat", env.makeRetainedStaticContext(), args);
//        setLocation(result, offset);
//        return result;
    }

    protected void requireXQuery(String construct) throws XPathException {
        // No action in XQuery
    }


    /**
     * Handle a URI literal. This is whitespace-normalized as well as being unescaped
     *
     * @param in the string as written
     * @return the URI after unescaping of entity and character references
     * followed by whitespace normalization
     * @throws net.sf.saxon.trans.XPathException if an error is found while unescaping the URI
     */

    public String uriLiteral(/*@NotNull*/ String in) throws XPathException {
        return Whitespace.collapse(unescape(in));
    }

    @Override
    protected boolean atStartOfRelativePath() {
        return super.atStartOfRelativePath()
                || t.currentToken instanceof Token.DirectElementConstructor
                || t.currentToken instanceof Token.StringConstructor
                || (languageVersion < 40 && t.currentToken == Token.LT);
        // "<" after "/" is recognized in XQuery but not in XPath.
        // In Query 3.1, `/ < 5` is an error, but in 4.0 it is a general comparison
    }

    @Override
    protected void testPermittedAxis(int axis, String errorCode) throws XPathException {
        super.testPermittedAxis(axis, errorCode);
        if (axis == AxisInfo.NAMESPACE && language == ParsedLanguage.XQUERY) {
            grumble("The namespace axis is not available in XQuery", errorCode);
        }
    }

    /**
     * Skip whitespace.
     *
     * @param c the current character
     * @return the first character after any whitespace, or NUL at end of input
     */

    private char skipSpaces(char c) {
        while (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
            c = t.nextChar();
        }
        return c;
    }

    /**
     * Test whether the current character is the expected character.
     *
     * @param actual   The character that was read
     * @param expected The character that was expected
     * @throws XPathException if they are different
     */

    private void expectChar(char actual, char expected) throws XPathException {
        if (actual != expected) {
            grumble("Expected '" + expected + "', found " +
                            (actual == Tokenizer.NUL ? "end of input" : "'" + actual + "'"));
        }
    }

    /**
     * Get the current language (XPath or XQuery)
     */

    /*@NotNull*/
    @Override
    protected String getLanguage() {
        return "XQuery";
    }

    private record AttributeDetails (
        String lexicalName,
        String value,
        int startOffset,
        int endOffset) {
    }

    public static class Import {
        public NamespaceUri namespaceURI;
        public List<String> locationURIs;
        public int offset;
    }
}

