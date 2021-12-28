////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.parser.OptimizerOptions;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.lib.ErrorReporter;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.sxpath.IndependentContext;
import net.sf.saxon.sxpath.XPathEvaluator;
import net.sf.saxon.sxpath.XPathExpression;
import net.sf.saxon.sxpath.XPathVariable;
import net.sf.saxon.trans.*;
import net.sf.saxon.value.SequenceType;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An {@code XPathCompiler} object allows XPath queries to be compiled. The compiler holds information that
 * represents the static context for an XPath expression.
 * <p>To construct an {@code XPathCompiler}, use the factory method
 * {@link Processor#newXPathCompiler}.</p>
 * <p>An {@code XPathCompiler} may be used repeatedly to compile multiple
 * queries. Any changes made to the {@code XPathCompiler} (that is, to the
 * static context) do not affect queries that have already been compiled.
 * An {@code XPathCompiler} may be used concurrently in multiple threads, but
 * it should not then be modified once initialized.</p>
 * <p>Changes to an {@code XPathCompiler} are cumulative. There is no simple way to reset
 * the {@code XPathCompiler} to its initial state; instead, simply create a new
 * {@code XPathCompiler}.</p>
 * <p>The <code>XPathCompiler</code> has the ability to maintain a cache of compiled
 * expressions. This is active only if enabled by calling {@link #setCaching(boolean)}.
 * If caching is enabled, then the compiler will recognize an attempt to compile
 * the same expression twice, and will avoid the cost of recompiling it. The cache
 * is emptied by any method that changes the static context for subsequent expressions,
 * for example, {@link #setBaseURI(java.net.URI)}. Unless the cache is emptied,
 * it grows indefinitely: compiled expressions are never discarded.</p>
 *
 * @since 9.0
 */

public class XPathCompiler {

    private Processor processor;
    private XPathEvaluator evaluator;
    private IndependentContext env;
    private ItemType requiredContextItemType;

    private Map<String, XPathExecutable> cache = null;

    /**
     * Protected constructor
     *
     * @param processor the s9api Processor
     */

    protected XPathCompiler(Processor processor) {
        this.processor = processor;
        this.evaluator = new XPathEvaluator(processor.getUnderlyingConfiguration());
        env = (IndependentContext) this.evaluator.getStaticContext();
    }

    /**
     * Get the Processor from which this XPathCompiler was constructed
     *
     * @return the Processor to which this XPathCompiler belongs
     * @since 9.3
     */

    public Processor getProcessor() {
        return processor;
    }

    /**
     * Say whether XPath 1.0 backwards compatibility mode is to be used. In backwards compatibility
     * mode, more implicit type conversions are allowed in XPath expressions, for example it
     * is possible to compare a number with a string. The default is false (backwards compatibility
     * mode is off).
     *
     * @param option true if XPath 1.0 backwards compatibility is to be enabled, false if it is to
     *               be disabled.
     * @since 9.1; changed in 9.8 to throw IllegalStateException if the feature is not available; change
     * reverted by bug 3817.
     */

    public void setBackwardsCompatible(boolean option) {
        if (cache != null) {
            cache.clear();
        }
        env.setBackwardsCompatibilityMode(option);
    }


    /**
     * Ask whether XPath 1.0 backwards compatibility mode is in force.
     *
     * @return true if XPath 1.0 backwards compatibility is enabled, false if it is disabled.
     */

    public boolean isBackwardsCompatible() {
        return env.isInBackwardsCompatibleMode();
    }

    /**
     * Say whether XPath expressions compiled using this {@code XPathCompiler} are
     * schema-aware. They will automatically be schema-aware if the method
     * {@link #importSchemaNamespace(String)} is called. An XPath expression
     * must be marked as schema-aware if it is to handle typed (validated)
     * input documents.
     *
     * @param schemaAware true if expressions are to be schema-aware, false otherwise
     * @throws UnsupportedOperationException if schema-awareness is requested when this is
     * not a licensed Saxon-EE configuration.
     * @since 9.3. Changed in 9.9 to throw an exception if Saxon-EE is not available (previously,
     * the option was either silently ignored, or caused a failure at some later time.)
     */

    public void setSchemaAware(boolean schemaAware) {
        if (schemaAware && !processor.getUnderlyingConfiguration().isLicensedFeature(Configuration.LicenseFeature.SCHEMA_VALIDATION)) {
            throw new UnsupportedOperationException("Schema processing requires a licensed Saxon-EE configuration");
        }
        env.setSchemaAware(schemaAware);
    }

    /**
     * Ask whether XPath expressions compiled using this XPathCompiler are
     * schema-aware. They will automatically be schema-aware if the method
     * {@link #importSchemaNamespace(String)} is called. An XPath expression
     * must be marked as schema-aware if it is to handle typed (validated)
     * input documents.
     *
     * @return true if expressions are to be schema-aware, false otherwise
     * @since 9.3
     */

    public boolean isSchemaAware() {
        return env.getPackageData().isSchemaAware();
    }

    /**
     * Say whether an XPath 2.0, XPath 3.0 or XPath 3.1 processor is required.
     *
     * @param value One of the values 1.0, 2.0, 3.0, 3.05, 3.1.
     *              <p>Setting the option to 1.0 requests an XPath 2.0 processor running in 1.0 compatibility mode;
     *              this is equivalent to setting the language version to 2.0 and backwards compatibility mode to true.
     *              Requesting "3.05" gives XPath 3.0 plus the extensions defined in the XSLT 3.0 specification
     *              (map types and map constructors).</p>
     * @throws IllegalArgumentException if the version is not numerically equal to 1.0, 2.0, 3.0, 3.05, or 3.1.
     * @since 9.3
     */

    public void setLanguageVersion(String value) {
        if (cache != null) {
            cache.clear();
        }
        int version;
        if ("1.0".equals(value)) {
            version = 20;
            env.setBackwardsCompatibilityMode(true);
        } else if ("2.0".equals(value)) {
            version = 20;
        } else if ("3.0".equals(value) || "3.05".equals(value)) {
            version = 30;
        } else if ("3.1".equals(value)) {
            version = 31;
        } else {
            throw new IllegalArgumentException("XPath version");
        }
        env.setXPathLanguageLevel(version);
        env.setDefaultFunctionLibrary(version);
    }

    /**
     * Ask whether an XPath 2.0, XPath 3.0 or XPath 3.1 processor is being used
     *
     * @return version: "2.0", "3.0" or "3.1"
     * @since 9.3
     */

    public String getLanguageVersion() {
        if (env.getXPathVersion() == 20) {
            return "2.0";
        } else if (env.getXPathVersion() == 30) {
            return "3.0";
        } else if (env.getXPathVersion() == 31) {
            return "3.1";
        } else {
            throw new IllegalStateException("Unknown XPath version " + env.getXPathVersion());
        }
    }

    /**
     * Set the static base URI for XPath expressions compiled using this XPathCompiler. The base URI
     * is part of the static context, and is used to resolve any relative URIs appearing within an XPath
     * expression, for example a relative URI passed as an argument to the doc() function. If no
     * static base URI is supplied, then the current working directory is used.
     *
     * @param uri the base URI to be set in the static context. This must be an absolute URI, or null
     *            to indicate that no static base URI is available.
     */

    public void setBaseURI(URI uri) {
        if (cache != null) {
            cache.clear();
        }
        if (uri == null) {
            env.setBaseURI(null);
        } else {
            if (!uri.isAbsolute()) {
                throw new IllegalArgumentException("Supplied base URI must be absolute");
            }
            env.setBaseURI(uri.toString());
        }
    }

    /**
     * Get the static base URI for XPath expressions compiled using this XPathCompiler. The base URI
     * is part of the static context, and is used to resolve any relative URIs appearing within an XPath
     * expression, for example a relative URI passed as an argument to the doc() function. If no
     * static base URI has been explicitly set, this method returns null.
     *
     * @return the base URI from the static context
     */

    public URI getBaseURI() {
        try {
            return new URI(env.getStaticBaseURI());
        } catch (URISyntaxException err) {
            throw new IllegalStateException(err);
        }
    }

    /**
     * Set the error reporter to be used for reporting static warnings during compilation.
     * By default, the {@link ErrorReporter} associated with the Saxon Configuration is used.
     * <p>Note that fatal static errors are always reported in the form
     * of an exception thrown by the {@link #compile(String)} method, so this method only controls
     * the handling of warnings</p>
     * @param reporter the ErrorReporter to which warnings will be notified
     * @since 10.0
     */

    public void setWarningHandler(ErrorReporter reporter) {
        env.setWarningHandler((message, location) -> {
            reporter.report(new XmlProcessingIncident(message, SaxonErrorCode.SXWN9000, location).asWarning());
        });
    }

    /**
     * Declare a namespace binding as part of the static context for XPath expressions compiled using this
     * XPathCompiler
     *
     * @param prefix The namespace prefix. If the value is a zero-length string, this method sets the default
     *               namespace for elements and types.
     * @param uri    The namespace URI. It is possible to specify a zero-length string to "undeclare" a namespace;
     *               in this case the prefix will not be available for use, except in the case where the prefix
     *               is also a zero length string, in which case the absence of a prefix implies that the name
     *               is in no namespace.
     * @throws NullPointerException if either the prefix or uri is null.
     */

    public void declareNamespace(String prefix, String uri) {
        if (cache != null) {
            cache.clear();
        }
        env.declareNamespace(prefix, uri);
    }

    /**
     * Import a schema namespace: that is, add the element and attribute declarations and type definitions
     * contained in a given namespace to the static context for the XPath expression.
     * <p>This method will not cause the schema to be loaded. That must be done separately, using the
     * {@link SchemaManager}. This method will not fail if the schema has not been loaded (but in that case
     * the set of declarations and definitions made available to the XPath expression is empty). The schema
     * document for the specified namespace may be loaded before or after this method is called.</p>
     * <p>This method does not bind a prefix to the namespace. That must be done separately, using the
     * {@link #declareNamespace(String, String)} method.</p>
     *
     * @param uri The schema namespace to be imported. To import declarations in a no-namespace schema,
     *            supply a zero-length string.
     * @since 9.1
     */

    public void importSchemaNamespace(String uri) {
        if (cache != null) {
            cache.clear();
        }
        env.getImportedSchemaNamespaces().add(uri);
        env.setSchemaAware(true);
    }

    /**
     * Say whether undeclared variables are allowed. By default, they are not allowed. When
     * undeclared variables are allowed, it is not necessary to predeclare the variables that
     * may be used in the XPath expression; instead, a variable is automatically declared when a reference
     * to the variable is encountered within the expression.
     *
     * @param allow true if undeclared variables are allowed, false if they are not allowed.
     * @since 9.2
     */

    public void setAllowUndeclaredVariables(boolean allow) {
        if (cache != null) {
            cache.clear();
        }
        env.setAllowUndeclaredVariables(allow);
    }

    /**
     * Ask whether undeclared variables are allowed. By default, they are not allowed. When
     * undeclared variables are allowed, it is not necessary to predeclare the variables that
     * may be used in the XPath expression; instead, a variable is automatically declared when a reference
     * to the variable is encountered within the expression.
     *
     * @return true if undeclared variables are allowed, false if they are not allowed.
     * @since 9.2
     */

    public boolean isAllowUndeclaredVariables() {
        return env.isAllowUndeclaredVariables();
    }

    /**
     * Declare a variable as part of the static context for XPath expressions compiled using this
     * {@code XPathCompiler}. It is an error for the XPath expression to refer to a variable unless it has been
     * declared, unless the method {@link #setAllowUndeclaredVariables(boolean)} has been called to permit
     * undeclared variables. This method declares the existence of the variable, but it does not
     * bind any value to the variable; that is done later, when the XPath expression is evaluated.
     * The variable is allowed to have any type (that is, the required type is <code>item()*</code>).
     *
     * @param qname The name of the variable, expressions as a QName
     */

    public void declareVariable(QName qname) {
        if (cache != null) {
            cache.clear();
        }
        env.declareVariable(qname.getNamespaceURI(), qname.getLocalName());
    }

    /**
     * Declare a variable as part of the static context for XPath expressions compiled using this
     * {@code XPathCompiler}. It is an error for the XPath expression to refer to a variable unless it has been
     * declared, unless the method {@link #setAllowUndeclaredVariables(boolean)} has been called to permit
     * undeclared variables. This method declares the existence of the variable, and defines the required type
     * of the variable, but it does not bind any value to the variable; that is done later,
     * when the XPath expression is evaluated.
     *
     * @param qname       The name of the variable, expressed as a {@link QName}
     * @param itemType    The required item type of the value of the variable, for example {@code ItemType.BOOLEAN}
     * @param occurrences The allowed number of items in the sequence forming the value of the variable
     */

    public void declareVariable(QName qname, ItemType itemType, OccurrenceIndicator occurrences) {
        if (cache != null) {
            cache.clear();
        }
        XPathVariable var = env.declareVariable(qname.getNamespaceURI(), qname.getLocalName());
        var.setRequiredType(
                SequenceType.makeSequenceType(
                        itemType.getUnderlyingItemType(), occurrences.getCardinality()));
    }

    /**
     * Make available a set of functions defined in an XSLT 3.0 package. All functions
     * defined with {@code visibility="public"} (or exposed as public using {@code xsl:expose}
     * become part of the static context for an XPath expression created using this
     * {@code XPathCompiler}. The functions are added to the search path after all existing
     * functions, including functions added using a previous call on this method.
     * <p>Note that if the library package includes functions that reference stylesheet parameters
     * (or global variables that depend on the context item), then there is no way of supplying
     * values for such parameters; calling such functions will cause a run-time error.</p>
     * @param libraryPackage the XSLT compiled library package whose functions are to be made
     *                       available
     * @since 10.0
     */

    public void addXsltFunctionLibrary(XsltPackage libraryPackage) {
        ((FunctionLibraryList)env.getFunctionLibrary()).addFunctionLibrary(
                libraryPackage.getUnderlyingPreparedPackage().getPublicFunctions());
    }

    /**
     * Declare the static type of the context item. If this type is declared, and if a context item
     * is supplied when the query is invoked, then the context item must conform to this type (no
     * type conversion will take place to force it into this type).
     *
     * @param type the required type of the context item, for example, {@link ItemType#ANY_NODE}.
     * @since 9.3
     */

    public void setRequiredContextItemType(ItemType type) {
        requiredContextItemType = type;
        env.setRequiredContextItemType(type.getUnderlyingItemType());
    }

    /**
     * Get the required type of the context item. If no type has been explicitly declared for the context
     * item, an instance of {@link ItemType#ANY_ITEM} (representing the type {@code item()}) is returned.
     *
     * @return the required type of the context item
     * @since 9.3
     */

    public ItemType getRequiredContextItemType() {
        return requiredContextItemType;
    }

    /**
     * Declare the default collation
     *
     * @param uri the absolute URI of the default collation. This URI must identify a known collation;
     *            either one that has been explicitly declared, or one that is recognized implicitly,
     *            such as a UCA collation
     * @throws IllegalStateException if the collation URI is not recognized as a known collation
     * @since 9.4
     */

    public void declareDefaultCollation(String uri) {
        StringCollator c;
        try {
            c = getProcessor().getUnderlyingConfiguration().getCollation(uri);
        } catch (XPathException e) {
            c = null;
        }
        if (c == null) {
            throw new IllegalStateException("Unknown collation " + uri);
        }
        env.setDefaultCollationName(uri);
    }


    /**
     * Say whether the compiler should maintain a cache of compiled expressions.
     *
     * @param caching if set to true, caching of compiled expressions is enabled.
     *                If set to false, any existing cache is cleared, and future compiled expressions
     *                will not be cached until caching is re-enabled. The cache is also cleared
     *                (but without disabling future caching)
     *                if any method is called that changes the static context for compiling
     *                expressions, for example {@link #declareVariable(QName)} or
     *                {@link #declareNamespace(String, String)}.
     * @since 9.3
     */

    public void setCaching(boolean caching) {
        if (caching) {
            if (cache == null) {
                cache = new ConcurrentHashMap<>();
            }
        } else {
            cache = null;
        }
    }

    /**
     * Ask whether the compiler is maintaining a cache of compiled expressions
     *
     * @return true if a cache is being maintained
     * @since 9.3
     */

    public boolean isCaching() {
        return cache != null;
    }

    /**
     * Request fast compilation. Fast compilation will generally be achieved at the expense of run-time performance
     * and quality of diagnostics. Fast compilation is a good trade-off if (a) the expression is known to be correct,
     * and (b) once compiled, it is only executed once against a document of modest size.
     * @param fast set to true to request fast compilation; set to false to revert to the optimization options
     *             defined in the Configuration.
     *
     * @since 9.9
     */

    public void setFastCompilation(boolean fast) {
        if (fast) {
            env.setOptimizerOptions(new OptimizerOptions(0));
        } else {
            env.setOptimizerOptions(getProcessor().getUnderlyingConfiguration().getOptimizerOptions());
        }
    }

    /**
     * Ask if fast compilation has been enabled.
     * @return true if fast compilation has been enabled
     * @since 9.9
     */

    public boolean isFastCompilation() {
        return env.getOptimizerOptions().getOptions() == 0;
    }

    /**
     * Compile an XPath expression, supplied as a character string.
     *
     * @param source A non-null string containing the source text of the XPath expression
     * @return An XPathExecutable which represents the compiled xpath expression object.
     * The XPathExecutable may be run as many times as required, in the same or a different thread.
     * The XPathExecutable is not affected by any changes made to the XPathCompiler once it has been compiled.
     * @throws SaxonApiException if any static error is detected while analyzing the expression.
     *                           <p>Note: prior to Saxon 9.7, static errors were also notified to the ErrorListener associated
     *                           with the containing Processor/Configuration. This is no longer the case.</p>
     */

    public XPathExecutable compile(String source) throws SaxonApiException {
        Objects.requireNonNull(source);
        if (cache != null) {
            synchronized(this) {
                XPathExecutable expr = cache.get(source);
                if (expr == null) {
                    expr = internalCompile(source);
                    cache.put(source, expr);
                }
                return expr;
            }
        } else {
            return internalCompile(source);
        }
    }

    private XPathExecutable internalCompile(String source) throws SaxonApiException {
        try {
            env.getDecimalFormatManager().checkConsistency();
        } catch (net.sf.saxon.trans.XPathException e) {
            throw new SaxonApiException(e);
        }
        XPathEvaluator eval = evaluator;
        IndependentContext ic = env;
        if (ic.isAllowUndeclaredVariables()) {
            // self-declaring variables modify the static context. The XPathCompiler must not change state
            // as the result of compiling an expression, so we need to copy the static context.
            eval = new XPathEvaluator(processor.getUnderlyingConfiguration());
            ic = new IndependentContext(env);
            eval.setStaticContext(ic);
            for (Iterator iter = env.iterateExternalVariables(); iter.hasNext(); ) {
                XPathVariable var = (XPathVariable) iter.next();
                XPathVariable var2 = ic.declareVariable(var.getVariableQName());
                var2.setRequiredType(var.getRequiredType());
            }
        }
        try {
            XPathExpression cexp = eval.createExpression(source);
            return new XPathExecutable(cexp, processor, ic);
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Compile and evaluate an XPath expression, supplied as a character string, with a given
     * context item.
     *
     * @param expression  A non-null string containing the source text of the XPath expression
     * @param contextItem The context item to be used for evaluating the expression. This
     *                    may be null if the expression is to be evaluated with no context item.
     * @return the result of evaluating the XPath expression with this context item. Note that
     * the result is an {@link Iterable}, so that it can be used in a "for-each" loop such as
     * <code>for (XdmItem item : xpath.evaluate("//x", doc) {...}</code>
     * @throws SaxonApiException if any static error is detected while analyzing the expression,
     *                           or if any dynamic error is detected while evaluating it.
     * @since 9.3
     */

    public XdmValue evaluate(String expression, /*@Nullable*/ XdmItem contextItem) throws SaxonApiException {
        Objects.requireNonNull(expression);
        XPathSelector selector = compile(expression).load();
        if (contextItem != null) {
            selector.setContextItem(contextItem);
        }
        return selector.evaluate();
    }

    /**
     * Compile and evaluate an XPath expression whose result is expected to be
     * a single item, with a given context item. The expression is supplied as
     * a character string.
     *
     * @param expression  A non-null string containing the source text of the XPath expression
     * @param contextItem The context item to be used for evaluating the expression. This
     *                    may be null if the expression is to be evaluated with no context item.
     * @return the result of evaluating the XPath expression with this context item.
     * If the result is a singleton it is returned as an XdmItem; if it is an empty
     * sequence, the return value is null. If the expression returns a sequence of more than one item,
     * any items after the first are ignored.
     * @throws SaxonApiException if any static error is detected while analyzing the expression,
     *                           or if any dynamic error is detected while evaluating it.
     * @since 9.3
     */

    public XdmItem evaluateSingle(String expression, XdmItem contextItem) throws SaxonApiException {
        Objects.requireNonNull(expression);
        XPathSelector selector = compile(expression).load();
        if (contextItem != null) {
            selector.setContextItem(contextItem);
        }
        return selector.evaluateSingle();
    }

    /**
     * Compile an XSLT 2.0 pattern, supplied as a character string. The compiled pattern behaves as a boolean
     * expression which, when evaluated in a particular context, returns true if the context node matches
     * the pattern, and false if it does not. An error is reported if there is no context item or it the context
     * item is not a node.
     *
     * @param source A non-null string conforming to the syntax of XSLT 2.0 patterns
     * @return An XPathExecutable representing an expression which evaluates to true when the context node matches
     * the pattern, and false when it does not.
     * @throws SaxonApiException if the pattern contains static errors: for example, if its syntax is incorrect,
     *                           or if it refers to undeclared variables or namespaces
     * @since 9.1
     */

    public XPathExecutable compilePattern(String source) throws SaxonApiException {
        Objects.requireNonNull(source);
        try {
            env.getDecimalFormatManager().checkConsistency();
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }

        try {
            XPathExpression cexp = evaluator.createPattern(source);
            return new XPathExecutable(cexp, processor, env);
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Registers the required decimal format properties, for use in calls to the
     * {@code fn:format-number()} function. The values for different properties may be supplied
     * incrementally; they are only checked for consistency when an XPath expression is compiled.
     *
     * @param format   The name of the decimal format. (Note: it is not possible to set
     *                 properties for the default unnamed decimal format.)
     * @param property The decimal symbols name to update, for example {@code "grouping-separator"}
     * @param value    The value to update the decimal symbol property
     * @throws SaxonApiException if an invalid value is supplied for a decimal format property
     * @throws IllegalArgumentException if the value of {code property} is not one of the recognized
     * decimal format properties
     * @since 9.4
     */
    public void setDecimalFormatProperty(QName format, String property, String value) throws SaxonApiException {
        DecimalFormatManager dfm = env.getDecimalFormatManager();
        if (dfm == null) {
            dfm = new DecimalFormatManager(HostLanguage.XPATH, env.getXPathVersion());
            env.setDecimalFormatManager(dfm);
        }

        DecimalSymbols symbols = dfm.obtainNamedDecimalFormat(format.getStructuredQName());

        try {
            switch (property) {
                case "decimal-separator":
                    symbols.setDecimalSeparator(value);
                    break;
                case "grouping-separator":
                    symbols.setGroupingSeparator(value);
                    break;
                case "exponent-separator":
                    symbols.setExponentSeparator(value);
                    break;
                case "infinity":
                    symbols.setInfinity(value);
                    break;
                case "NaN":
                    symbols.setNaN(value);
                    break;
                case "minus-sign":
                    symbols.setMinusSign(value);
                    break;
                case "percent":
                    symbols.setPercent(value);
                    break;
                case "per-mille":
                    symbols.setPerMille(value);
                    break;
                case "zero-digit":
                    symbols.setZeroDigit(value);
                    break;
                case "digit":
                    symbols.setDigit(value);
                    break;
                case "pattern-separator":
                    symbols.setPatternSeparator(value);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown decimal format attribute " + property);
            }
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }

    }


    /**
     * Escape-hatch method to get the underlying static context object used by the implementation.
     *
     * @return the underlying static context object. In the current implementation this will always
     * be an instance of {@link IndependentContext}.
     * <p>This method provides an escape hatch to internal Saxon implementation objects that offer a finer and
     * lower-level degree of control than the s9api classes and methods. Some of these classes and methods may change
     * from release to release.</p>
     * @since 9.1
     */

    public StaticContext getUnderlyingStaticContext() {
        return env;
    }
}

