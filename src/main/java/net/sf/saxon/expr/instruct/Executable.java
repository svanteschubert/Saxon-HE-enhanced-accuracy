////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.PackageData;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.query.QueryModule;
import net.sf.saxon.serialize.CharacterMapIndex;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;

import java.util.*;

/**
 * A compiled stylesheet or a query in executable form.
 * Note that the original stylesheet tree is not retained.
 *
 * In the case of XSLT 3.0, the Executable represents the collection of all packages making up
 * a stylesheet; it is synthesized when the constituent packages are linked, and is not available
 * during compilation (because each package is compiled separately). For XSLT the Executable
 * will always be a {@link net.sf.saxon.PreparedStylesheet}, which is a subclass.
 */

public class Executable {
                                  
    // the Configuration options
    /*@NotNull*/
    private Configuration config;

    // the top level package
    private PackageData topLevelPackage;

    // the set of packages making up this Executable
    private List<PackageData> packages = new ArrayList<>();

    // default output properties (for the unnamed output format)
    private Properties defaultOutputProperties;

    // table of character maps indexed by StructuredQName
    private CharacterMapIndex characterMapIndex;

    // hash table of query library modules
    private HashMap<String, List<QueryModule>> queryLibraryModules;

    // hash set of query module location hints that have been processed
    private HashSet<String> queryLocationHintsProcessed;

    // list of functions available in the static context
    private FunctionLibraryList functionLibrary;

    // flag to indicate whether the principal language is for example XSLT or XQuery
    private HostLanguage hostLanguage = HostLanguage.XSLT;

    // a list of required parameters, identified by the structured QName of their names
    private Map<StructuredQName, GlobalParam> globalParams = new HashMap<>();

    // Hash table of named (and unnamed) output declarations. This is assembled only
    // if there is a need for it: that is, if there is a call on xsl:result-document
    // with a format attribute computed at run-time. The key is a StructuredQName object,
    // the value is a Properties object
    /*@Nullable*/ private HashMap<StructuredQName, Properties> outputDeclarations = null;

    // a boolean, true if the executable represents a stylesheet that uses xsl:result-document
    private boolean createsSecondaryResult = false;

    // a boolean, indicates that the executable is schema-aware. This will true by default only
    // if it statically imports a schema. If the executable is not schema-aware, then
    // all input documents must be untyped.
    protected boolean schemaAware = false;

    // Requirements for the initial context item
    private GlobalContextRequirement globalContextRequirement = null;

    /**
     * Create a new Executable (a collection of stylesheet modules and/or query modules)
     *
     * @param config the Saxon Configuration
     */

    public Executable(Configuration config) {
        setConfiguration(config);
    }

    /**
     * Set the configuration
     *
     * @param config the Configuration
     */

    public void setConfiguration(Configuration config) {
        this.config = config;
    }

    /**
     * Get the configuration
     *
     * @return the Configuration
     */

    /*@NotNull*/
    public Configuration getConfiguration() {
        return config;
    }

    public PackageData getTopLevelPackage() {
        return topLevelPackage;
    }

    public void setTopLevelPackage(PackageData topLevelPackage) {
        this.topLevelPackage = topLevelPackage;
    }

    /**
     * Add information about a package
     * @param data information about a package
     */

    public void addPackage(PackageData data) {
        packages.add(data);
    }

    /**
     * Get information about the packages in this executable
     * @return a list of packages
     */

    public Iterable<PackageData> getPackages() {
        return packages;
    }

    /**
     * Set the host language
     *  @param language     the host language, as a constant such as {@link HostLanguage#XSLT} or
     *                     {@link HostLanguage#XQUERY}
     *
     */

    public void setHostLanguage(HostLanguage language) {
        hostLanguage = language;
    }

    /**
     * Get the host language
     *
     * @return a value identifying the host language: {@link HostLanguage#XQUERY} or {@link HostLanguage#XSLT}
     */

    public HostLanguage getHostLanguage() {
        return hostLanguage;
    }
    
    /**
     * Get the library containing all the in-scope functions in the static context
     *
     * @return the function libary
     */

    public FunctionLibraryList getFunctionLibrary() {
        return functionLibrary;
    }

    /**
     * Set the library containing all the in-scope functions in the static context
     *
     * @param functionLibrary the function libary
     */

    public void setFunctionLibrary(FunctionLibraryList functionLibrary) {
        //System.err.println("***" + this + " setFunctionLib to " + functionLibrary);
        this.functionLibrary = functionLibrary;
    }

    /**
     * Set the index of named character maps
     *
     * @param cmi a hash table that maps the names of character maps
     *            to the HashMap objects representing the character maps
     */

    public void setCharacterMapIndex(CharacterMapIndex cmi) {
        characterMapIndex = cmi;
    }

    /**
     * Get the index of named character maps
     *
     * @return the hash table that maps the names of character maps
     *         to the IntHashMap objects representing the character maps
     */

    public CharacterMapIndex getCharacterMapIndex() {
        if (characterMapIndex == null) {
            characterMapIndex = new CharacterMapIndex();
        }
        return characterMapIndex;
    }

    /**
     * Set the default output properties (the properties for the unnamed output format)
     *
     * @param properties the output properties to be used when the unnamed output format
     *                   is selected
     */

    public void setDefaultOutputProperties(Properties properties) {
        defaultOutputProperties = properties;
    }

    /**
     * Get the primary output properties, that is, the output properties for the primary
     * result destination. In XSLT these are the properties defined on unnamed {@code xsl:output}
     * declarations in the top-level stylesheet package; in XQuery they are the properties
     * defined using {@code "declare option output:x = 'value'"}.
     *
     * @return the properties for the unnamed output format
     */

    public SerializationProperties getPrimarySerializationProperties() {
        if (defaultOutputProperties == null) {
            defaultOutputProperties = new Properties();
        }
        Properties props = defaultOutputProperties;
        return new SerializationProperties(props, getCharacterMapIndex());
    }

    /**
     * Add a named output format
     *
     * @param qName      the structured QName of the output format
     * @param properties the properties of the output format
     */

    public void setOutputProperties(StructuredQName qName, Properties properties) {
        if (outputDeclarations == null) {
            outputDeclarations = new HashMap<>(5);
        }
        outputDeclarations.put(qName, properties);
    }

    /**
     * Get the properties for xsl:output. The object returned will
     * be a clone of the internal values, and thus it can be mutated
     * without mutating the Executable object.
     * <p>The returned properties object is a new, empty, Properties object that is
     * backed by the live properties to supply default values for missing properties.
     * This means that the property values must be read using the getProperty() method.
     * Calling the get() method on the underlying Hashtable will return null.</p>
     * <p>This method gets the output properties for the unnamed output
     * format in the stylesheet.</p>
     *
     * @return A Properties object reflecting the output properties defined
     * for the default (unnamed) output format in the stylesheet. It may
     * be mutated and supplied to the setOutputProperties() method of the
     * Transformer, without affecting other transformations that use the
     * same stylesheet.
     * @see javax.xml.transform.Transformer#setOutputProperties
     */

    public Properties getOutputProperties() {
        return new Properties(defaultOutputProperties);
    }
    
    /**
     * Get a named output format
     *
     * @param qName the name of the output format
     * @return properties the properties of the output format. Return null if there are
     *         no output properties with the given name
     */

    /*@Nullable*/
    public Properties getOutputProperties(StructuredQName qName) {
        if (outputDeclarations == null) {
            return null;
        } else {
            return outputDeclarations.get(qName);
        }
    }

    /**
     * Add an XQuery library module to the configuration. The Executable maintains a table indicating
     * for each module namespace, the set of modules that have been loaded from that namespace. If a
     * module import is encountered that specifies no location hint, all the known modules for that
     * namespace are imported.
     *
     * @param module the library module to be added to this executable
     */

    public void addQueryLibraryModule(QueryModule module) {
        if (queryLibraryModules == null) {
            queryLibraryModules = new HashMap<>(5);
        }
        String uri = module.getModuleNamespace();
        List<QueryModule> existing = queryLibraryModules.get(uri);
        if (existing == null) {
            existing = new ArrayList<>(5);
            existing.add(module);
            queryLibraryModules.put(uri, existing);
        } else if (!existing.contains(module)){
            existing.add(module);
        }
    }

    /**
     * Locate the known XQuery library modules for a given module namespace.
     *
     * @param namespace the module namespace URI
     * @return a list of items each of which is the StaticQueryContext representing a module, or
     *         null if the module namespace is unknown
     */

    /*@Nullable*/
    public List<QueryModule> getQueryLibraryModules(String namespace) {
        if (queryLibraryModules == null) {
            return null;
        }
        return queryLibraryModules.get(namespace);
    }

    /**
     * Get the query library module with a given systemID
     *
     * @param systemId  the SystemId of the required module
     * @param topModule the top-level query module (usually a main module, except when
     *                  importing library modules into XSLT)
     * @return the module with that system id if found, otherwise null
     */

    /*@Nullable*/
    public QueryModule getQueryModuleWithSystemId(/*@NotNull*/ String systemId, /*@NotNull*/ QueryModule topModule) {
        if (systemId.equals(topModule.getSystemId())) {
            return topModule;
        }
        Iterator miter = getQueryLibraryModules();
        while (miter.hasNext()) {
            QueryModule sqc = (QueryModule) miter.next();
            String uri = sqc.getSystemId();
            if (uri != null && uri.equals(systemId)) {
                return sqc;
            }
        }
        return null;
    }

    /**
     * Get an iterator over all the query library modules (does not include the main module)
     *
     * @return an iterator whose returned items are instances of {@link QueryModule}
     */

    public Iterator getQueryLibraryModules() {
        if (queryLibraryModules == null) {
            return Collections.EMPTY_LIST.iterator();
        } else {
            List<QueryModule> modules = new ArrayList<>();
            for (List<QueryModule> queryModules : queryLibraryModules.values()) {
                modules.addAll(queryModules);
            }
            return modules.iterator();
        }
    }

    /**
     * Add a name to the list of query module location hints that have been
     * processed during the construction of this executable
     *
     * @param uri the name to be added (the location URI as it appears in the
     *            "import module" declaration, expanded to an absolute URI by resolving against
     *            the base URI, but before passing to the Module Resolver)
     */

    public void addQueryLocationHintProcessed(String uri) {
        if (queryLocationHintsProcessed == null) {
            queryLocationHintsProcessed = new HashSet<>();
        }
        queryLocationHintsProcessed.add(uri);
    }

    /**
     * Ask whether a query module location hint has already been processed
     *
     * @param uri the query location hint (the location URI as it appears in the
     *            "import module" declaration, expanded to an absolute URI by resolving against
     *            the base URI, but before passing to the Module Resolver)
     * @return true if the location hint has already been processed
     */

    public boolean isQueryLocationHintProcessed(String uri) {
        return queryLocationHintsProcessed != null && queryLocationHintsProcessed.contains(uri);
    }


    /**
     * Fix up global variables and functions in all query modules. This is done right at the end, because
     * recursive imports are permitted
     *
     * @param main           the main query module
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs
     */

    public void fixupQueryModules(QueryModule main) throws XPathException {

        // Bind any previously unbound variables (forwards references)

        main.bindUnboundVariables();

        if (queryLibraryModules != null) {
            for (List<QueryModule> queryModules : queryLibraryModules.values()) {
                for (QueryModule env : queryModules) {
                    env.bindUnboundVariables();
                }
            }
        }

        List<GlobalVariable> varDefinitions = main.fixupGlobalVariables(main.getGlobalStackFrameMap() /*, contextItemVar */);

        main.bindUnboundFunctionCalls();

        if (queryLibraryModules != null) {
            for (List<QueryModule> queryModules : queryLibraryModules.values()) {
                for (QueryModule env : queryModules) {
                    env.bindUnboundFunctionCalls();
                }
            }
        }

        // Note: the checks for circularities between variables and functions have to happen
        // before functions are compiled and optimized, as the optimization can involve function
        // inlining which eliminates the circularities (tests K-InternalVariablesWith-17, errata8-002)

        main.checkForCircularities(varDefinitions, main.getGlobalFunctionLibrary());
        main.fixupGlobalFunctions();

//        if (checkForCycles) {
//            Iterator miter = getQueryLibraryModules();
//            while (miter.hasNext()) {
//                QueryModule module = (QueryModule) miter.next();
//                module.lookForModuleCycles(new Stack<QueryModule>(), 1);
//            }
//        }

        main.typeCheckGlobalVariables(varDefinitions);
        main.optimizeGlobalFunctions();
    }

    /**
     * Explain (that is, output an expression tree) the global variables
     *
     * @param presenter the destination for the explanation of the global variables
     * @throws XPathException if writing to the output fails
     */

    public void explainGlobalVariables(ExpressionPresenter presenter) throws XPathException {
        presenter.startElement("globalVariables");
        for (PackageData pack : getPackages()) {
            for (GlobalVariable var : pack.getGlobalVariableList()) {
                var.export(presenter);
            }
        }
        presenter.endElement();
    }

    /**
     * Register a global parameter
     *
     * @param param the stylesheet parameter (XSLT) or external variable (XQuery) to be registered
     */

    public void registerGlobalParameter(GlobalParam param) {
        globalParams.put(param.getVariableQName(), param);
    }

    /**
     * Get all the registered global parameters
     * @return a list of all the stylesheet parameters (XSLT) or external variables (XQuery)
     */

    public Map<StructuredQName, GlobalParam> getGlobalParameters() {
        return globalParams;
    }

    /**
     * Get the global parameter with a given name, if there is one
     * @param name the parameter name
     * @return the global parameter with this name, if it exists, or null otherwise
     */

    public GlobalParam getGlobalParameter(StructuredQName name) {
        return globalParams.get(name);
    }

    /**
     * Check the supplied parameters. Checks that all required parameters have been supplied, and that
     * no parameters supplied at run-time are declared static in the stylesheet, or were supplied
     * eagerly at compile-time.
     *
     * <p>Used in XSLT only.</p>
     *
     * @param params the set of parameters that have been supplied (null represents an empty set)
     * @throws XPathException if there is a required parameter for which no value has been supplied
     */

    public void checkSuppliedParameters(GlobalParameterSet params) throws XPathException {

    }


    /**
     * Set whether this executable represents a stylesheet that uses xsl:result-document
     * to create secondary output documents
     *
     * @param flag true if the executable uses xsl:result-document
     */

    public void setCreatesSecondaryResult(boolean flag) {
        createsSecondaryResult = flag;
    }

    /**
     * Ask whether this executable represents a stylesheet that uses xsl:result-document
     * to create secondary output documents
     *
     * @return true if the executable uses xsl:result-document
     */

    public boolean createsSecondaryResult() {
        return createsSecondaryResult;
    }

    /**
     * Set the accumulators declared on xsl:global-context-item
     * @param requirement the requirements on the global context item
     */

    public void setGlobalContextRequirement(GlobalContextRequirement requirement) {
        globalContextRequirement = requirement;
    }

    /**
     * Get the constraints on the initial global context item
     * @return the declared initial global context item requirements
     */

    public GlobalContextRequirement getGlobalContextRequirement() {
        return globalContextRequirement;
    }

    /**
     * Check that the supplied global context item meets all requirements, and perform any required
     * conversions or defaulting
     * @param contextItem the supplied context item
     * @param context a context for evaluation (in which the context item will be absent)
     * @return a new/converted context item, or null if the global context item is to be absent
     * @throws XPathException if the supplied item does not meet requirements, for example if it is the
     * wrong type.
     */

    public Item checkInitialContextItem(Item contextItem, XPathContext context) throws XPathException {
        if (globalContextRequirement == null) {
            return contextItem;
        }
        if (contextItem != null && globalContextRequirement.isAbsentFocus()) {
            throw new XPathException("The global context item is required to be absent", "XPDY0002");
        }
        TypeHierarchy th = config.getTypeHierarchy();
        if (contextItem == null) {
            if (!globalContextRequirement.isMayBeOmitted()) {
                // Bug 30173 allocates an error code
                throw new XPathException("A global context item is required, but none has been supplied", "XTDE3086");
            }
            if (globalContextRequirement.getDefaultValue() != null) {
                // XQuery only
                try {
                    contextItem = globalContextRequirement.getDefaultValue().evaluateItem(context);
                } catch (XPathException e) {
                    // XPDY0002 here means there is no context item, which means the default value
                    // of the context item depends on the context item: a circularity.
                    if ("XPDY0002".equals(e.getErrorCodeLocalPart())) {
                        if (e.getMessage().contains("last()") || e.getMessage().contains("position()")) {
                            // no action
                        } else {
                            e.setErrorCode("XQDY0054");
                        }
                    }
                    throw e;
                }
                if (contextItem == null) {
                    throw new XPathException("The context item cannot be initialized to an empty sequence", "XPTY0004");
                }
                for (ItemType type : globalContextRequirement.getRequiredItemTypes()) {
                    if (!type.matches(contextItem, th)) {
                        RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.MISC, "defaulted global context item", 0);
                        String s = role.composeErrorMessage(type, contextItem, th);
                        throw new XPathException(s, "XPTY0004");
                    }
                }
            }
        } else {
            for (ItemType type : globalContextRequirement.getRequiredItemTypes()) {
                if (!type.matches(contextItem, config.getTypeHierarchy())) {
                    RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.MISC, "supplied global context item", 0);
                    String s = role.composeErrorMessage(type, contextItem, th);
                    throw new XPathException(s, getHostLanguage() == HostLanguage.XSLT ? "XTTE0590" : "XPTY0004");
                }
            }
        }
        return contextItem;
    }

    /**
     * Set whether this executable is schema-aware. The initial value is false; it is set to true
     * at compile time if the query or transformation imports a schema. If the value is false, then
     * all documents used at run-time must be untyped
     *
     * @param aware true if the executable is schema-aware
     * @throws IllegalArgumentException if schema-aware processing is requested in a Configuration
     *                                  that is not schema-aware
     */

    public void setSchemaAware(boolean aware) {
//        if (aware) {
//            config.checkLicensedFeature(Configuration.LicenseFeature.SCHEMA_VALIDATION,
//                                        "schema-aware processing", getTopLevelPackage().getLocalLicenseId());
//        }
        schemaAware = aware;
    }

    /**
     * Ask whether this executable is schema-aware, that is, whether the query or transformation
     * imports a schema.
     *
     * @return true if the executable is schema-aware, false if not.
     */

    public boolean isSchemaAware() {
        return schemaAware;
    }

}

