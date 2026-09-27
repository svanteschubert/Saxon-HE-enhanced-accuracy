////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.sxpath;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.EarlyEvaluationContext;
import net.sf.saxon.expr.PackageData;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.functions.registry.ConstructorFunctionLibrary;
import net.sf.saxon.lib.ErrorReporter;
import net.sf.saxon.om.NamespaceMap;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.s9api.UnprefixedElementMatchingPolicy;
import net.sf.saxon.trans.DecimalFormatManager;
import net.sf.saxon.trans.KeyManager;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XmlProcessingIncident;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Schema;
import net.sf.saxon.value.SequenceType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * An abstract and configurable implementation of the StaticContext interface,
 * which defines the static context of an XPath expression.
 * <p>This class implements those parts of the functionality of a static context
 * that tend to be common to most implementations: simple-valued properties such
 * as base URI and default element namespace; availability of the standard
 * function library; and support for collations.</p>
 */

public abstract class AbstractStaticContext implements StaticContext {

    private String baseURI = null;
    private Configuration config;
    private PackageData packageData;
    private Location containingLocation = Loc.NONE;
    private String defaultCollationName;
    private FunctionLibraryList libraryList = new FunctionLibraryList();
    private NamespaceUri defaultFunctionNamespace = NamespaceUri.FN;
    private NamespaceUri defaultElementNamespace = NamespaceUri.NULL;
    private boolean backwardsCompatible = false;
    protected int xpathLanguageLevel = 31;
    protected boolean usingDefaultFunctionLibrary;
    private final Map<StructuredQName, ItemType> typeAliases = new HashMap<>();
    private UnprefixedElementMatchingPolicy unprefixedElementPolicy = UnprefixedElementMatchingPolicy.DEFAULT_NAMESPACE;
    private WarningHandler warningHandler;
    protected NamespaceMap prologNamespaces = NamespaceMap.emptyMap();

    /**
     * Set the Configuration. This is protected so it can be used only by subclasses;
     * the configuration will normally be set at construction time
     *
     * @param config the configuration
     */

    protected void setConfiguration(Configuration config) {
        this.config = config;
        this.defaultCollationName = config.getDefaultCollationName();
        warningHandler = (message, code, locator) -> {
            XmlProcessingIncident incident = new XmlProcessingIncident(message, code, locator).asWarning();
            config.makeErrorReporter().report(incident);
        };
    }

    /**
     * Get the system configuration
     */

    @Override
    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Set data about the unit of compilation (XQuery module, XSLT package)
     *
     * @param packageData the package data
     */
    public void setPackageData(PackageData packageData) {
        this.packageData = packageData;
    }

    /**
     * Get data about the unit of compilation (XQuery module, XSLT package) to which this
     * container belongs
     */
    @Override
    public PackageData getPackageData() {
        return packageData;
    }

    /**
     * Say whether this static context is schema-aware
     *
     * @param aware true if this static context is schema-aware
     */

    public void setSchemaAware(boolean aware) {
        getPackageData().setSchemaAware(aware);
    }

    @Override
    public Schema getImportedSchema() {
        return getPackageData().getImportedSchema("");
    }

    /**
     * Construct a RetainedStaticContext, which extracts information from this StaticContext
     * to provide the subset of static context information that is potentially needed
     * during expression evaluation
     *
     * @return a RetainedStaticContext object: either a newly created one, or one that is
     * reused from a previous invocation.
     */
    @Override
    public RetainedStaticContext makeRetainedStaticContext() {
        return new RetainedStaticContext(this);
    }


    /**
     * Initialize the default function library for XPath.
     * This can be overridden using setFunctionLibrary().
     */

    public final void setDefaultFunctionLibrary(int version) {
        FunctionLibraryList lib = new FunctionLibraryList();
        lib.addFunctionLibrary(config.getXPathFunctionSet(version));
        lib.addFunctionLibrary(config.getBuiltInExtensionLibraryList(version));
        lib.addFunctionLibrary(new ConstructorFunctionLibrary(config));
        lib.addFunctionLibrary(config.getIntegratedFunctionLibrary());
        config.addExtensionBinders(lib);
        setFunctionLibrary(lib);
    }
    /**
     * Add a function library to the list of function libraries
     *
     * @param library the function library to be added
     */

    protected final void addFunctionLibrary(FunctionLibrary library) {
        libraryList.addFunctionLibrary(library);
    }

    /**
     * Register a namespace that is explicitly declared in the prolog of the expression or
     * query module.
     *
     * @param prefix The namespace prefix. Must not be null.
     * @param uri    The namespace URI. Must not be null. The value "" (zero-length string) is used
     *               to undeclare a namespace; it is not an error if there is no existing binding for
     *               the namespace prefix.
     * @throws XPathException if the declaration is invalid
     */
   // @Override
    public void declarePrologNamespace(String prefix, NamespaceUri uri) throws XPathException {
        throw new UnsupportedOperationException("Cannot declare prolog namespaces on an immutable static context");
    }

    /**
     * Construct a dynamic context for early evaluation of constant subexpressions
     */

    @Override
    public XPathContext makeEarlyEvaluationContext() {
        return new EarlyEvaluationContext(getConfiguration());
    }


    @Override
    public Location getContainingLocation() {
        return containingLocation;
    }

    /**
     * Set the containing location, which represents the location of the outermost expression using this
     * static context (typically, subexpressions will have a nested location that refers to this outer
     * containing location)
     *
     * @param location the location map to be used
     */

    public void setContainingLocation(Location location) {
        containingLocation = location;
    }

    /**
     * Set the base URI in the static context
     *
     * @param baseURI the base URI of the expression; the value null is allowed to indicate that the base URI is not available.
     */

    public void setBaseURI(String baseURI) {
        this.baseURI = baseURI;
    }

    /**
     * Get the Base URI, for resolving any relative URI's used
     * in the expression. Used by the document() function, resolve-uri(), etc.
     *
     * @return "" if no base URI has been set
     */

    @Override
    public String getStaticBaseURI() {
        return baseURI == null ? "" : baseURI;
    }

    /**
     * Get the function library containing all the in-scope functions available in this static
     * context. This method is called by the XPath parser when binding a function call in the
     * XPath expression to an implementation of the function.
     */

    @Override
    public FunctionLibrary getFunctionLibrary() {
        return libraryList;
    }

    /**
     * Set the function library to be used
     *
     * @param lib the function library
     */

    public void setFunctionLibrary(FunctionLibraryList lib) {
        libraryList = lib;
        usingDefaultFunctionLibrary = false;
    }

    /**
     * Set the name of the default collation for this static context.
     * @param collationName the name of the default collation
     */

    public void setDefaultCollationName(String collationName) {
        defaultCollationName = collationName;
    }

    /**
     * Get the name of the default collation.
     *
     * @return the name of the default collation; or the name of the codepoint collation
     *         if no default collation has been defined
     */

    @Override
    public String getDefaultCollationName() {
        return defaultCollationName;
    }

    /**
     * Set a callback function that will be called to handle any static warnings found while
     * processing warnings from the XPath parser.
     * <p>Superseded in 12.0 by {@link #setWarningHandler(WarningHandler)}, which allows the callback
     * to receive the error code information as well as the error message and originating location.</p>
     * @param handler the function to be called to handle static warnings. When a warning is
     *                issued, the handler's {@code accept} method is called, supplying the string
     *                of the warning message and a location as the arguments.  The default warning handler sends
     *                the information to the default {@link ErrorReporter} associated with the
     *                Saxon {@link Configuration}.
     * @since 10.0
     */

    public void setWarningHandler(BiConsumer<String, Location> handler) {
        warningHandler = (message, code, loc) -> handler.accept(message, loc);
    }

    /**
     * Set a callback function that will be called to handle any static warnings found while
     * processing warnings from the XPath parser
     *
     * @param handler the function to be called to handle static warnings. When a warning is
     *                issued, the handler's {@code accept} method is called, supplying the string
     *                of the warning message, an error code, and a location as the arguments.  The default warning handler sends
     *                the information to the default {@link ErrorReporter} associated with the
     *                Saxon {@link Configuration}.
     * @since 12.0
     */

    public void setWarningHandler(WarningHandler handler) {
        warningHandler = handler;
    }


    /**
     * Get the callback function that will be called to handle any static warnings found while
     * processing warnings from the XPath parser
     *
     * @return the function to be called to handle static warnings, if one has been supplied.
     *                When a warning is
     *                issued, the handler's {@code accept} method is called, supplying the string
     *                of the warning message and the location information as the two arguments.
     * @since 10.0. Modified in 12.0 to return a {@link WarningHandler}, which is a three-argument
     * callback accepting the error message, error code, and location.
     */

    public WarningHandler getWarningHandler() {
        return warningHandler;
    }

    /**
     * Issue a compile-time warning. This method is used during XPath expression compilation to
     * output warning conditions. The default implementation writes the message to the
     * error reporter registered with the Configuration.
     */

    @Override
    public void issueWarning(String s, String errorCode, Location locator) {
        getWarningHandler().accept(s, errorCode, locator);
    }

    /**
     * Get the system ID of the container of the expression. Used to construct error messages.
     *
     * @return "" always
     */

    @Override
    public String getSystemId() {
        return "";
    }


    /**
     * Get the default namespace URI for elements and types
     * Return NamespaceConstant.NULL (that is, the zero-length string) for the non-namespace
     *
     * @return the default namespace for elements and type
     */

    @Override
    public NamespaceUri getDefaultElementNamespace() {
        return defaultElementNamespace;
    }

    /**
     * Set the default namespace for elements and types
     *
     * @param uri the namespace to be used for unprefixed element and type names.
     *            The value "" (or NamespaceConstant.NULL) represents the non-namespace
     */

    public void setDefaultElementNamespace(NamespaceUri uri) {
        defaultElementNamespace = uri;
    }

    /**
     * Set the default function namespace
     *
     * @param uri the namespace to be used for unprefixed function names.
     *            The value "" (or NamespaceConstant.NULL) represents the non-namespace
     */

    public void setDefaultFunctionNamespace(NamespaceUri uri) {
        defaultFunctionNamespace = uri;
    }

    /**
     * Get the default function namespace.
     * The value "" (or NamespaceConstant.NULL) represents the non-namespace
     *
     * @return the default namesapce for functions
     */

    @Override
    public NamespaceUri getDefaultFunctionNamespace() {
        return defaultFunctionNamespace;
    }

    /**
     * Set the XPath language level supported.
     * The current levels supported are 20 (=2.0), 31 (=3.1), and 40 (=4.0). The default is 3.1.
     *
     * @param level the XPath language level
     * @since 9.3. From 9.8 this only affects the XPath syntax that is accepted;
     * it does not affect the function library that is available, which must be
     * set separately using {@link #setFunctionLibrary(FunctionLibraryList)}
     */

    public void setXPathLanguageLevel(int level) {
        if (level == 40) {
            config.checkLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION, "XPath 4.0", -1);
        }
        xpathLanguageLevel = level;
        if (packageData.getHostLanguageVersion() != level) {
            packageData.setHostLanguage(packageData.getHostLanguage(), level);
        }
        setDefaultFunctionLibrary(level);
    }

    /**
     * Get the XPath language level supported, as an integer (being the actual version
     * number times ten). In Saxon 9.9 the possible values are 20 (XPath 2.0), 30 (XPath 3.0),
     * 31 (XPath 3.1), and 305 (XPath 3.0 plus the extensions defined in XSLT 3.0).
     *
     * @return the XPath language level; the return value will be either 20, 30, 305, or 31
     * @since 9.7
     */

    @Override
    public int getXPathVersion() {
        return xpathLanguageLevel;
    }

    /**
     * Set XPath 1.0 backwards compatibility mode on or off
     *
     * @param option true if XPath 1.0 compatibility mode is to be set to true;
     *               otherwise false
     */

    public void setBackwardsCompatibilityMode(boolean option) {
        backwardsCompatible = option;
    }

    /**
     * Determine whether Backwards Compatible Mode is used
     *
     * @return true if XPath 1.0 compatibility mode is to be set to true;
     *         otherwise false
     */

    @Override
    public boolean isInBackwardsCompatibleMode() {
        return backwardsCompatible;
    }

    /**
     * Set the DecimalFormatManager used to resolve the names of decimal formats used in calls
     * to the format-number() function.
     *
     * @param manager the decimal format manager for this static context, or null if no named decimal
     *                formats are available in this environment.
     */

    public void setDecimalFormatManager(DecimalFormatManager manager) {
        getPackageData().setDecimalFormatManager(manager);
    }

    /**
     * Get the required type of the context item. If no type has been explicitly declared for the context
     * item, an instance of AnyItemType (representing the type item()) is returned.
     *
     * @return the required type of the context item
     * @since 9.3
     */

    @Override
    public ItemType getRequiredContextItemType() {
        return AnyItemType.INSTANCE;
    }

    /**
     * Get the required type of the context value. If no type has been explicitly declared for the context
     * value, the SequenceType item() is returned.
     *
     * @return the required type of the context value
     * @since 13.0
     */

    @Override
    public SequenceType getRequiredContextValueType() {
        return SequenceType.one(getRequiredContextItemType());
    }

    /**
     * Get a DecimalFormatManager to resolve the names of decimal formats used in calls
     * to the format-number() function.
     *
     * @return the decimal format manager for this static context; a newly created empty
     *         DecimalFormatManager if none has been supplied
     * @since 9.2
     */

    @Override
    public DecimalFormatManager getDecimalFormatManager() {
        DecimalFormatManager manager = getPackageData().getDecimalFormatManager();
        if (manager == null) {
            manager = new DecimalFormatManager(HostLanguage.XPATH, xpathLanguageLevel);
            getPackageData().setDecimalFormatManager(manager);
        }
        return manager;
    }

    /**
     * Get the KeyManager, containing definitions of keys available for use.
     *
     * @return the KeyManager. This is used to resolve key names, both explicit calls
     *         on key() used in XSLT, and system-generated calls on key() which may
     *         also appear in XQuery and XPath
     */
    @Override
    public KeyManager getKeyManager() {
        return getPackageData().getKeyManager();
    }

    /**
     * Register an alias for an ItemType. This is a Saxon extension. If {@code typename}
     * has been registered as an alias for, say, map{xs:string, xs:integer*}, then
     * the syntax {@code type(typename)} is accepted anywhere this ItemType would
     * be accepted.
     * @param name the alias name of the type
     * @param type the type to which this alias refers
     */

    public void setTypeAlias(StructuredQName name, ItemType type) {
        typeAliases.put(name, type);
    }

    /**
     * Get type alias. This is a Saxon extension. A type alias is a QName which can
     * be used as a shorthand for an itemtype, using the syntax ~typename anywhere that
     * an item type is permitted.
     *
     * @param typeName the name of the type alias
     * @return the corresponding item type, if the name is recognised; otherwise null.
     */

    @Override
    public ItemType resolveTypeAlias(StructuredQName typeName) {
        return typeAliases.get(typeName);
    }

    /**
     * Set the policy for matching unprefixed element names.
     * @param policy the policy to be used
     */

    public void setUnprefixedElementMatchingPolicy(UnprefixedElementMatchingPolicy policy) {
        this.unprefixedElementPolicy = policy;
    }

    /**
     * Get the policy for matching unprefixed element names.
     *
     * @return the policy to be used
     */

    @Override
    public UnprefixedElementMatchingPolicy getUnprefixedElementMatchingPolicy() {
         return unprefixedElementPolicy;
    }

    /**
     * Interface defining a callback for handling warnings
     */

    @FunctionalInterface
    public interface WarningHandler {
        void accept(String message, String errorCode, Location location);
    }
}

