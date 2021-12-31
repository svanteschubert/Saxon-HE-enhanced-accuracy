////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.functions.registry.ConstructorFunctionLibrary;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.s9api.XmlProcessingError;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.TraceCodeInjector;
import net.sf.saxon.trans.*;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SchemaType;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * This class represents a query module, and includes information about the static context of the query module.
 * The class is intended for internal Saxon use. User settings that affect the static context are made in the
 * StaticQueryContext object, and those settings are copied to each QueryModule when the query module is compiled.
 */

public class QueryModule implements StaticContext {
    private boolean isMainModule;
    private final Configuration config;
    private StaticQueryContext userQueryContext;
    private final QueryModule topModule;
    private URI locationURI;
    private String baseURI;
    private String moduleNamespace; // null only if isMainModule is false
    private HashMap<String, String> explicitPrologNamespaces;
    private Stack<NamespaceBinding> activeNamespaces;  // The namespace bindings declared in element constructors
    private HashMap<StructuredQName, GlobalVariable> variables;
    // global variables declared in this module
    private HashMap<StructuredQName, GlobalVariable> libraryVariables;
    // all global variables defined in library modules
    // defined only on the top-level module
    private HashMap<StructuredQName, UndeclaredVariable> undeclaredVariables;
    private HashSet<String> importedSchemata;    // The schema target namespaces imported into this module
    private HashMap<String, HashSet<String>> loadedSchemata;
    // For the top-level module only, all imported schemas for all modules,
    // Key is the targetNamespace, value is the set of absolutized location URIs
    private Executable executable;
    private List<QueryModule> importers;  // A list of QueryModule objects representing the modules that import this one,
    // Null for the main module
    // This is needed *only* to implement the rules banning cyclic imports
    private FunctionLibraryList functionLibraryList;
    private XQueryFunctionLibrary globalFunctionLibrary;      // used only on a top-level module
    private int localFunctionLibraryNr;
    private int importedFunctionLibraryNr;
    private int unboundFunctionLibraryNr;
    private Set<String> importedModuleNamespaces;
    private boolean inheritNamespaces = true;
    private boolean preserveNamespaces = true;
    private int constructionMode = Validation.PRESERVE;
    private String defaultFunctionNamespace;
    private String defaultElementNamespace;
    private boolean preserveSpace = false;
    private boolean defaultEmptyLeast = true;
    private String defaultCollationName;
    private int revalidationMode = Validation.SKIP;
    private boolean isUpdating = false;
    private ItemType requiredContextItemType = AnyItemType.getInstance(); // must be the same for all modules
    private DecimalFormatManager decimalFormatManager = null;   // used only in XQuery 3.0
    private CodeInjector codeInjector;
    private PackageData packageData;
    private RetainedStaticContext moduleStaticContext = null;
    private Location moduleLocation;
    private OptimizerOptions optimizerOptions;

    /**
     * Create a QueryModule for a main module, copying the data that has been set up in a
     * StaticQueryContext object
     *
     * @param sqc the StaticQueryContext object from which this module is initialized
     * @throws XPathException if information supplied is invalid
     */

    public QueryModule(/*@NotNull*/ StaticQueryContext sqc) throws XPathException {
        config = sqc.getConfiguration();
        isMainModule = true;
        topModule = this;
        activeNamespaces = new Stack<>();
        baseURI = sqc.getBaseURI();
        defaultCollationName = sqc.getDefaultCollationName();
        try {
            locationURI = baseURI == null ? null : new URI(baseURI);
        } catch (URISyntaxException err) {
            throw new XPathException("Invalid location URI: " + baseURI);
        }
        executable = sqc.makeExecutable();
        importers = null;
        init(sqc);

        PackageData pd = new PackageData(config);
        pd.setHostLanguage(HostLanguage.XQUERY);
        pd.setSchemaAware(isSchemaAware());
        packageData = pd;

        for (Iterator<GlobalVariable> vars = sqc.iterateDeclaredGlobalVariables(); vars.hasNext(); ) {
            GlobalVariable var = vars.next();
            declareVariable(var);
            pd.addGlobalVariable(var);
            var.setPackageData(pd);
        }


        executable.setTopLevelPackage(pd);
        executable.addPackage(pd);

        if (sqc.getModuleLocation() == null) {
            moduleLocation = new Loc(sqc.getSystemId(), 1, -1);
        } else {
            moduleLocation = sqc.getModuleLocation();
        }
        optimizerOptions = sqc.getOptimizerOptions();
    }

    /**
     * Create a QueryModule for a library module.
     *
     * @param config   the Saxon configuration
     * @param importer the module that imported this module. This may be null, in the case where
     *                 the library module is being imported into an XSLT stylesheet
     */

    public QueryModule(Configuration config, /*@Nullable*/ QueryModule importer) {
        this.config = config;
        importers = null;
        if (importer == null) {
            topModule = this;
        } else {
            topModule = importer.topModule;
            userQueryContext = importer.userQueryContext;
            importers = new ArrayList<>(2);
            importers.add(importer);
        }
        init(userQueryContext);
        packageData = importer.getPackageData();
        activeNamespaces = new Stack<>();
        executable = null;
        optimizerOptions = importer.optimizerOptions;
    }

    /**
     * Initialize data from a user-supplied StaticQueryContext object
     *
     * @param sqc the user-supplied StaticQueryContext. Null if this is a library module imported
     *            into XSLT.
     */

    private void init(/*@Nullable*/ StaticQueryContext sqc) {
        //reset();
        userQueryContext = sqc;
        variables = new HashMap<>(10);
        undeclaredVariables = new HashMap<>(5);
        if (isTopLevelModule()) {
            libraryVariables = new HashMap<>(10);
        }
        importedSchemata = new HashSet<>(5);
        //importedSchemata.add(NamespaceConstant.JSON);
        importedModuleNamespaces = new HashSet<>(5);
        moduleNamespace = null;
        activeNamespaces = new Stack<>();

        explicitPrologNamespaces = new HashMap<>(10);
        if (sqc != null) {
            //executable = sqc.getExecutable();
            inheritNamespaces = sqc.isInheritNamespaces();
            preserveNamespaces = sqc.isPreserveNamespaces();
            preserveSpace = sqc.isPreserveBoundarySpace();
            defaultEmptyLeast = sqc.isEmptyLeast();
            defaultFunctionNamespace = sqc.getDefaultFunctionNamespace();
            defaultElementNamespace = sqc.getDefaultElementNamespace();
            defaultCollationName = sqc.getDefaultCollationName();
            constructionMode = sqc.getConstructionMode();
            if (constructionMode == Validation.PRESERVE && !sqc.isSchemaAware()) {
                // if not schema-aware, generate untyped output by default
                constructionMode = Validation.STRIP;
            }
            requiredContextItemType = sqc.getRequiredContextItemType();
            isUpdating = sqc.isUpdatingEnabled();
            codeInjector = sqc.getCodeInjector();
            optimizerOptions = sqc.getOptimizerOptions();
        }
        initializeFunctionLibraries(sqc);
    }

    /**
     * Supporting method to load an imported library module.
     * Used also by saxon:import-query in XSLT.
     * <p>This method is intended for internal use only.</p>
     *
     * @param baseURI      The base URI and location URI of the module
     * @param executable   The Executable
     * @param importer     The importing query module (used to check for cycles). This is null
     *                     when loading a query module from XSLT.
     * @param query        The text of the query, after decoding and normalizing line endings
     * @param namespaceURI namespace of the query module to be loaded
     * @return The StaticQueryContext representing the loaded query module
     * @throws XPathException if an error occurs
     */

    /*@NotNull*/
    public static QueryModule makeQueryModule(
            String baseURI, /*@NotNull*/ Executable executable, /*@NotNull*/ QueryModule importer,
            String query, String namespaceURI) throws XPathException {
        Configuration config = executable.getConfiguration();
        QueryModule module = new QueryModule(config, importer);
        try {
            module.setLocationURI(new URI(baseURI));
        } catch (URISyntaxException e) {
            throw new XPathException("Invalid location URI " + baseURI, e);
        }
        module.setBaseURI(baseURI);
        module.setExecutable(executable);
        module.setModuleNamespace(namespaceURI);

        executable.addQueryLibraryModule(module);
        XQueryParser qp = (XQueryParser) config.newExpressionParser(
                "XQ", importer.isUpdating(), 31);
        if (importer.getCodeInjector() != null) {
            qp.setCodeInjector(importer.getCodeInjector());
        } else if (config.isCompileWithTracing()) {
            qp.setCodeInjector(new TraceCodeInjector());
        }
        QNameParser qnp = new QNameParser(module.getLiveNamespaceResolver())
            .withAcceptEQName(importer.getXPathVersion() >= 30)
            .withUnescaper(new XQueryParser.Unescaper(config.getValidCharacterChecker()));
        qp.setQNameParser(qnp);

        qp.parseLibraryModule(query, module);

        String namespace = module.getModuleNamespace();
        if (namespace == null) {
            XPathException err = new XPathException("Imported module must be a library module");
            err.setErrorCode("XQST0059");
            err.setIsStaticError(true);
            throw err;
        }
        if (!namespace.equals(namespaceURI)) {
            XPathException err = new XPathException("Imported module's namespace does not match requested namespace");
            err.setErrorCode("XQST0059");
            err.setIsStaticError(true);
            throw err;
        }

        return module;
    }

    /**
     * Reset function libraries
     *
     * @param sqc The static query context set up by the caller
     */

    private void initializeFunctionLibraries(/*@Nullable*/ StaticQueryContext sqc) {
        Configuration config = getConfiguration();
        if (isTopLevelModule()) {
            globalFunctionLibrary = new XQueryFunctionLibrary(config);
        }

        functionLibraryList = new FunctionLibraryList();
        functionLibraryList.addFunctionLibrary(getBuiltInFunctionSet());
        functionLibraryList.addFunctionLibrary(config.getBuiltInExtensionLibraryList());
        functionLibraryList.addFunctionLibrary(new ConstructorFunctionLibrary(config));

        localFunctionLibraryNr = functionLibraryList.addFunctionLibrary(
                new XQueryFunctionLibrary(config));

        importedFunctionLibraryNr = functionLibraryList.addFunctionLibrary(
                new ImportedFunctionLibrary(this, getTopLevelModule().getGlobalFunctionLibrary()));

        if (sqc != null && sqc.getExtensionFunctionLibrary() != null) {
            functionLibraryList.addFunctionLibrary(sqc.getExtensionFunctionLibrary());
        }

        functionLibraryList.addFunctionLibrary(config.getIntegratedFunctionLibrary());
        config.addExtensionBinders(functionLibraryList);

        unboundFunctionLibraryNr = functionLibraryList.addFunctionLibrary(
                new UnboundFunctionLibrary());
    }

    public BuiltInFunctionSet getBuiltInFunctionSet() {
        if (isUpdating()) {
            return config.getXQueryUpdateFunctionSet();
        } else {
            return config.getXPath31FunctionSet();
        }
    }

    /**
     * Get the Saxon Configuration
     *
     * @return the Saxon Configuration
     */

    @Override
    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Get package data. This is a small data object containing information about the unit
     * of compilation, which in the case of XQuery is a query module
     *
     * @return data about this query module
     */

    @Override
    public PackageData getPackageData() {
        return packageData;
    }

    /**
     * Set the package data. This method is used when we want the QueryModule to share the same
     * package data as another module: notably when fn:load-query-module creates a "dummy" main
     * module to go with the dynamic library module
     * @param packageData the package information
     */

    public void setPackageData(PackageData packageData) {
        this.packageData = packageData;
    }

    /**
     * Test whether this is a "top-level" module. This is true for a main module and also for a
     * module directly imported into an XSLT stylesheet. It may also be true in future for independently-compiled
     * modules
     *
     * @return true if this is top-level module
     */

    public boolean isTopLevelModule() {
        return this == topModule;
    }

    /**
     * Set whether this is a "Main" module, in the sense of the XQuery language specification
     *
     * @param main true if this is a main module, false if it is a library module
     */

    public void setIsMainModule(boolean main) {
        isMainModule = main;
    }

    /**
     * Ask whether this is a "main" module, in the sense of the XQuery language specification
     *
     * @return true if this is a main module, false if it is a library model
     */

    public boolean isMainModule() {
        return isMainModule;
    }

    /**
     * Check whether this module is allowed to import a module with namespace N. Note that before
     * calling this we have already handled the exception case where a module imports another in the same
     * namespace (this is the only case where cycles are allowed, though as a late change to the spec they
     * are no longer useful, since they cannot depend on each other cyclically)
     *
     * @param namespace the namespace to be tested
     * @return true if the import is permitted
     */

    public boolean mayImportModule(/*@NotNull*/ String namespace) {
        if (namespace.equals(moduleNamespace)) {
            return false;
        }
        if (importers == null) {
            return true;
        }
        for (QueryModule importer : importers) {
            if (!importer.mayImportModule(namespace)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Ask whether expressions compiled under this static context are schema-aware.
     * They must be schema-aware if the expression is to handle typed (validated) nodes
     *
     * @return true if expressions are schema-aware
     */
    public boolean isSchemaAware() {
        return executable.isSchemaAware();
    }

    /**
     * Get the optimization options in use. By default these are taken from the
     * {@link Configuration}
     *
     * @return the optimization options in use
     */

    @Override
    public OptimizerOptions getOptimizerOptions() {
        return optimizerOptions;
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
        // The only part of the RetainedStaticContext that can change as the query module is parsed is the
        // "activeNamespaces", that is, namespaces declared on direct element constructors. If this is empty,
        // we can reuse the top-level static context on each request.
        if (activeNamespaces.empty()) {
            if (moduleStaticContext == null) {
                moduleStaticContext = new RetainedStaticContext(this);
            }
            return moduleStaticContext;
        } else {
            return new RetainedStaticContext(this);
        }
    }


    /**
     * Set the namespace inheritance mode
     *
     * @param inherit true if namespaces are inherited, false if not
     * @since 8.4
     */

    public void setInheritNamespaces(boolean inherit) {
        inheritNamespaces = inherit;
    }

    /**
     * Get the namespace inheritance mode
     *
     * @return true if namespaces are inherited, false if not
     * @since 8.4
     */

    public boolean isInheritNamespaces() {
        return inheritNamespaces;
    }

    /**
     * Set the namespace copy mode
     *
     * @param inherit true if namespaces are preserved, false if not
     */

    public void setPreserveNamespaces(boolean inherit) {
        preserveNamespaces = inherit;
    }

    /**
     * Get the namespace copy mode
     *
     * @return true if namespaces are preserved, false if not
     */

    public boolean isPreserveNamespaces() {
        return preserveNamespaces;
    }

    /**
     * Set the construction mode for this module
     *
     * @param mode one of {@link net.sf.saxon.lib.Validation#STRIP}, {@link net.sf.saxon.lib.Validation#PRESERVE}
     */

    public void setConstructionMode(int mode) {
        constructionMode = mode;
    }

    /**
     * Get the current construction mode
     *
     * @return one of {@link net.sf.saxon.lib.Validation#STRIP}, {@link net.sf.saxon.lib.Validation#PRESERVE}
     */

    public int getConstructionMode() {
        return constructionMode;
    }

    /**
     * Set the policy for preserving boundary space
     *
     * @param preserve true if boundary space is to be preserved, false if it is to be stripped
     */

    public void setPreserveBoundarySpace(boolean preserve) {
        preserveSpace = preserve;
    }

    /**
     * Ask whether the policy for boundary space is "preserve" or "strip"
     *
     * @return true if the policy is to preserve boundary space, false if it is to strip it
     */

    public boolean isPreserveBoundarySpace() {
        return preserveSpace;
    }

    /**
     * Set the option for where an empty sequence appears in the collation order, if not otherwise
     * specified in the "order by" clause
     *
     * @param least true if the empty sequence is considered less than any other value (the default),
     *              false if it is considered greater than any other value
     */

    public void setEmptyLeast(boolean least) {
        defaultEmptyLeast = least;
    }

    /**
     * Ask what is the option for where an empty sequence appears in the collation order, if not otherwise
     * specified in the "order by" clause
     *
     * @return true if the empty sequence is considered less than any other value (the default),
     *         false if it is considered greater than any other value
     */

    public boolean isEmptyLeast() {
        return defaultEmptyLeast;
    }


    /**
     * Get the function library object that holds details of global functions
     *
     * @return the library of global functions
     */

    public XQueryFunctionLibrary getGlobalFunctionLibrary() {
        return globalFunctionLibrary;
    }

    /**
     * Get the function library object that holds details of imported functions
     *
     * @return the library of imported functions
     */

    /*@NotNull*/
    public ImportedFunctionLibrary getImportedFunctionLibrary() {
        return (ImportedFunctionLibrary) functionLibraryList.get(importedFunctionLibraryNr);
    }

    /**
     * Register that this module imports a particular module namespace
     * <p>This method is intended for internal use.</p>
     *
     * @param uri the URI of the imported namespace.
     */

    public void addImportedNamespace(String uri) {
        if (importedModuleNamespaces == null) {
            importedModuleNamespaces = new HashSet<>(5);
        }
        importedModuleNamespaces.add(uri);
        getImportedFunctionLibrary().addImportedNamespace(uri);
    }

    /**
     * Ask whether this module directly imports a particular namespace
     * <p>This method is intended for internal use.</p>
     *
     * @param uri the URI of the possibly-imported namespace.
     * @return true if the schema for the namespace has been imported
     */

    public boolean importsNamespace(String uri) {
        return importedModuleNamespaces != null &&
                importedModuleNamespaces.contains(uri);
    }

    /**
     * Get the QueryModule for the top-level module. This will normally be a main module,
     * but in the case of saxon:import-query it will be the library module that is imported into
     * the stylesheet
     *
     * @return the StaticQueryContext object associated with the top level module
     */

    public QueryModule getTopLevelModule() {
        return topModule;
    }

    /**
     * Get the Executable, an object representing the compiled query and its environment.
     * <p>This method is intended for internal use only.</p>
     *
     * @return the Executable
     */

    /*@Nullable*/
    public Executable getExecutable() {
        return executable;
    }

    /**
     * Set the executable.
     * <p>This method is intended for internal use only.</p>
     *
     * @param executable the Executable
     */

    public void setExecutable(Executable executable) {
        this.executable = executable;
//        if (!executable.isSchemaAware()) {
//            constructionMode = Validation.STRIP;
//        }
    }

    /**
     * Get the StaticQueryContext object containing options set up by the user
     *
     * @return the user-created StaticQueryContext object
     */

    /*@Nullable*/
    public StaticQueryContext getUserQueryContext() {
        return userQueryContext;
    }

    /**
     * Get the LocationMap, an data structure used to identify the location of compiled expressions within
     * the query source text.
     * <p>This method is intended for internal use only.</p>
     *
     * @return the LocationMap
     */

    @Override
    public Location getContainingLocation() {
        return moduleLocation;
    }

    /**
     * Set the namespace for a library module.
     * <p>This method is for internal use only.</p>
     *
     * @param uri the module namespace URI of the library module. Null is allowed only
     *            for a main module, not for a library module.
     */

    public void setModuleNamespace(/*@Nullable*/ String uri) {
        moduleNamespace = uri;
    }

    /**
     * Get the namespace of the current library module.
     * <p>This method is intended primarily for internal use.</p>
     *
     * @return the module namespace, or null if this is a main module
     */

    /*@Nullable*/
    public String getModuleNamespace() {
        return moduleNamespace;
    }

    /**
     * Set the location URI for a module
     *
     * @param uri the location URI
     */

    public void setLocationURI(URI uri) {
        locationURI = uri;
        moduleLocation = new Loc(locationURI.toString(), 1, -1);
    }

    /**
     * Get the location URI for a module
     *
     * @return the location URI
     */

    /*@Nullable*/
    public URI getLocationURI() {
        return locationURI;
    }

    /**
     * Get the System ID for a module
     *
     * @return the location URI
     */

    /*@Nullable*/
    @Override
    public String getSystemId() {
        return locationURI == null ? null : locationURI.toString();
    }

    /**
     * Set the base URI for a module
     *
     * @param uri the base URI
     */

    public void setBaseURI(String uri) {
        baseURI = uri;
    }

    /**
     * Get the base URI for a module
     *
     * @return the base URI
     */

    @Override
    public String getStaticBaseURI() {
        return baseURI;
    }


    /**
     * Get the stack frame map for global variables.
     * <p>This method is intended for internal use.</p>
     *
     * @return the stack frame map (a SlotManager) for global variables.
     */

    public SlotManager getGlobalStackFrameMap() {
        return getPackageData().getGlobalSlotManager();
    }

    /**
     * Declare a global variable. A variable must normally be declared before an expression referring
     * to it is compiled, but there are exceptions where a set of modules in the same namespace
     * import each other cyclically. Global variables are normally declared in the Query Prolog, but
     * they can also be predeclared using the Java API. All global variables are held in the QueryModule
     * for the main module. The fact that a global variable is present therefore does not mean that it
     * is visible: there are two additional conditions (a) the module namespace must be imported into the
     * module where the reference appears, and (b) the declaration must not be in the same module and textually
     * after the reference.
     * <p>Note that the same VariableDeclaration object cannot be used with more than one query.  This is because
     * the VariableDeclaration is modified internally to hold a list of references to all the places where
     * the variable is used.</p>
     *
     * @param var the Variable declaration being declared
     * @throws XPathException if a static error is detected
     */

    public void declareVariable(/*@NotNull*/ GlobalVariable var) throws XPathException {
        StructuredQName key = var.getVariableQName();
        if (variables.get(key) != null) {
            GlobalVariable old = variables.get(key);
            if (old == var || old.getUltimateOriginalVariable() == var.getUltimateOriginalVariable()) {
                // do nothing
            } else {
                String oldloc = " (see line " + old.getLineNumber();
                String oldSysId = old.getSystemId();
                if (oldSysId != null &&
                        !oldSysId.equals(var.getSystemId())) {
                    oldloc += " in module " + old.getSystemId();
                }
                oldloc += ")";
                XPathException err = new XPathException("Duplicate definition of global variable "
                        + var.getVariableQName().getDisplayName()
                        + oldloc);
                err.setErrorCode("XQST0049");
                err.setIsStaticError(true);
                err.setLocation(var);
                throw err;
            }
        }
        variables.put(key, var);
        getPackageData().addGlobalVariable(var);

        final HashMap<StructuredQName, GlobalVariable> libVars = getTopLevelModule().libraryVariables;
        GlobalVariable old = libVars.get(key);
        if (old == null || old == var) {
            // do nothing
        } else {
            XPathException err = new XPathException("Duplicate definition of global variable "
                    + var.getVariableQName().getDisplayName()
                    + " (see line " + old.getLineNumber() + " in module " + old.getSystemId() + ')');
            err.setErrorCode("XQST0049");
            err.setIsStaticError(true);
            err.setLocation(var);
            throw err;
        }

        if (!isMainModule()) {
            libVars.put(key, var);
        }
    }

    /**
     * Get all global variables imported into this module
     *
     * @return a collection of global variables.
     * In the case of a main module, this includes only variables imported into this module,
     * it does not include variables declared within this module. In the case of a library module,
     * it includes both locally declared and imported variables. Blame history.
     */

    public Iterable<GlobalVariable> getGlobalVariables() {
        return libraryVariables.values();
    }

    /**
     * Fixup all references to global variables.
     * <p>This method is for internal use by the Query Parser only.</p>
     *
     * @param globalVariableMap a SlotManager that holds details of the assignment of slots to global variables.
     * @return a list containing the global variable definitions.
     * @throws XPathException if compiling a global variable definition fails
     */

    public List<GlobalVariable> fixupGlobalVariables(SlotManager globalVariableMap) throws XPathException {
        List<GlobalVariable> varDefinitions = new ArrayList<>(20);
        List<Iterator<GlobalVariable>> iters = new ArrayList<>();
        iters.add(variables.values().iterator());
        iters.add(libraryVariables.values().iterator());


        for (Iterator<GlobalVariable> iter : iters) {
            while (iter.hasNext()) {
                GlobalVariable var = iter.next();
                if (!varDefinitions.contains(var)) {
                    int slot = globalVariableMap.allocateSlotNumber(var.getVariableQName());
                    var.compile(getExecutable(), slot);
                    varDefinitions.add(var);
                }
            }
        }
        return varDefinitions;
    }

    /**
     * Look for module cycles. This is a restriction introduced in the PR specification because of
     * difficulties in defining the formal semantics.
     * <p>[Definition: A module M1 directly depends on another module M2 (different from M1) if a
     * variable or function declared in M1 depends on a variable or function declared in M2.]
     * It is a static error [err:XQST0093] to import a module M1 if there exists a sequence
     * of modules M1 ... Mi ... M1 such that each module directly depends on the next module
     * in the sequence (informally, if M1 depends on itself through some chain of module dependencies.)</p>
     *
     * @param referees   a Stack containing the chain of module import references leading to this
     *                   module
     * @param lineNumber used for diagnostics
     * @throws net.sf.saxon.trans.XPathException
     *          if cycles are found
     */

    public void lookForModuleCycles(/*@NotNull*/ Stack<QueryModule> referees, int lineNumber) throws XPathException {
        if (referees.contains(this)) {
            int s = referees.indexOf(this);
            referees.push(this);
            StringBuilder message = new StringBuilder("Circular dependency between modules. ");
            for (int i = s; i < referees.size() - 1; i++) {
                QueryModule next = referees.get(i + 1);
                if (i == s) {
                    message.append("Module ").append(getSystemId()).append(" references module ").append(next.getSystemId());
                } else {
                    message.append(", which references module ").append(next.getSystemId());
                }
            }
            message.append('.');
            XPathException err = new XPathException(message.toString());
            err.setErrorCode("XQST0093");
            err.setIsStaticError(true);
            Loc loc = new Loc(getSystemId(), lineNumber, -1);
            err.setLocator(loc);
            throw err;
        } else {
            referees.push(this);
            Iterator<GlobalVariable> viter = getModuleVariables();
            while (viter.hasNext()) {
                GlobalVariable gv = viter.next();
                //GlobalVariable gvc = gv.getCompiledVariable(); // will be null if the global variable is unreferenced
                Expression select = gv.getBody();
                if (select != null) {
                    List<Binding> list = new ArrayList<>(10);
                    ExpressionTool.gatherReferencedVariables(select, list);
                    for (Binding b : list) {
                        if (b instanceof GlobalVariable) {
                            String uri = ((GlobalVariable) b).getSystemId();
                            StructuredQName qName = b.getVariableQName();
                            boolean synthetic = qName.hasURI(NamespaceConstant.SAXON_GENERATED_VARIABLE);
                            if (!synthetic && uri != null && !uri.equals(getSystemId())) {
                                QueryModule sqc = executable.getQueryModuleWithSystemId(uri, topModule);
                                if (sqc != null) {
                                    sqc.lookForModuleCycles(referees, ((GlobalVariable) b).getLineNumber());
                                }
                            }
                        }
                    }
                    List<UserFunction> fList = new ArrayList<>(5);
                    ExpressionTool.gatherCalledFunctions(select, fList);
                    for (UserFunction f : fList) {
                        String uri = f.getSystemId();
                        if (uri != null && !uri.equals(getSystemId())) {
                            QueryModule sqc = executable.getQueryModuleWithSystemId(uri, topModule);
                            if (sqc != null) {
                                sqc.lookForModuleCycles(referees, f.getLineNumber());
                            }
                        }
                    }
                }
            }
            Iterator<XQueryFunction> fiter = getLocalFunctionLibrary().getFunctionDefinitions();
            while (fiter.hasNext()) {
                XQueryFunction gf = fiter.next();

                Expression body = gf.getUserFunction().getBody();
                if (body != null) {
                    List<Binding> vList = new ArrayList<>(10);
                    ExpressionTool.gatherReferencedVariables(body, vList);
                    for (Binding b : vList) {
                        if (b instanceof GlobalVariable) {
                            String uri = ((GlobalVariable) b).getSystemId();
                            StructuredQName qName = b.getVariableQName();
                            boolean synthetic = qName.hasURI(NamespaceConstant.SAXON) && "gg".equals(qName.getPrefix());
                            if (!synthetic && uri != null && !uri.equals(getSystemId())) {
                                QueryModule sqc = executable.getQueryModuleWithSystemId(uri, topModule);
                                if (sqc != null) {
                                    sqc.lookForModuleCycles(referees, ((GlobalVariable) b).getLineNumber());
                                }
                            }
                        }
                    }
                    List<UserFunction> fList = new ArrayList<>(10);
                    ExpressionTool.gatherCalledFunctions(body, fList);
                    for (UserFunction f : fList) {
                        String uri = f.getSystemId();
                        if (uri != null && !uri.equals(getSystemId())) {
                            QueryModule sqc = executable.getQueryModuleWithSystemId(uri, topModule);
                            if (sqc != null) {
                                sqc.lookForModuleCycles(referees, f.getLineNumber());
                            }
                        }
                    }
                }
            }
            referees.pop();
        }
    }

    /**
     * Get global variables declared in this module
     *
     * @return an Iterator whose items are GlobalVariable objects
     */

    public Iterator<GlobalVariable> getModuleVariables() {
        return variables.values().iterator();
    }

    /**
     * Check for circular definitions of global variables.
     * <p>This method is intended for internal use</p>
     *
     * @param compiledVars          a list of {@link GlobalVariable} objects to be checked
     * @param globalFunctionLibrary the library of global functions
     * @throws net.sf.saxon.trans.XPathException
     *          if a circularity is found
     */

    public void checkForCircularities(/*@NotNull*/ List<GlobalVariable> compiledVars, /*@NotNull*/ XQueryFunctionLibrary globalFunctionLibrary) throws XPathException {
        Iterator<GlobalVariable> iter = compiledVars.iterator();
        Stack<Object> stack = null;
        while (iter.hasNext()) {
            if (stack == null) {
                stack = new Stack<>();
            }
            GlobalVariable gv = iter.next();
            if (gv != null) {
                gv.lookForCycles(stack, globalFunctionLibrary);
            }
        }
    }


    /**
     * Perform type checking on global variables.
     * <p>This method is intended for internal use</p>
     *
     * @param compiledVars a list of {@link GlobalVariable} objects to be checked
     * @throws net.sf.saxon.trans.XPathException
     *          if a type error occurs
     */

    public void typeCheckGlobalVariables(/*@NotNull*/ List<GlobalVariable> compiledVars) throws XPathException {
        ExpressionVisitor visitor = ExpressionVisitor.make(this);
        for (GlobalVariable compiledVar : compiledVars) {
            compiledVar.typeCheck(visitor);
        }
        if (isMainModule()) {
            GlobalContextRequirement gcr = executable.getGlobalContextRequirement();
            if (gcr != null && gcr.getDefaultValue() != null) {
                ContextItemStaticInfo info = getConfiguration().makeContextItemStaticInfo(AnyItemType.getInstance(), true);
                gcr.setDefaultValue(gcr.getDefaultValue().typeCheck(visitor, info));
            }
        }
    }

    /**
     * Bind a variable used in a query to the expression in which it is declared.
     * <p>This method is provided for use by the XQuery parser, and it should not be called by the user of
     * the API, or overridden, unless variables are to be declared using a mechanism other than the
     * declareVariable method of this class.</p>
     *
     * @param qName the name of the variable to be bound
     * @return a VariableReference object representing a reference to a variable on the abstract syntac rtee of
     *         the query.
     */

    /*@NotNull*/
    @Override
    public Expression bindVariable(/*@NotNull*/ StructuredQName qName) throws XPathException {
        GlobalVariable var = variables.get(qName);
        if (var == null) {
            String uri = qName.getURI();
            if ((uri.equals("") && isMainModule()) || uri.equals(moduleNamespace) || importsNamespace(uri)) {
                QueryModule main = getTopLevelModule();
                var = main.libraryVariables.get(qName);
                if (var == null) {
                    // If the namespace has been imported there's the possibility that
                    // the variable declaration hasn't yet been read, because of the limited provision
                    // for cyclic imports. In XQuery 3.0 forwards references are more generally allowed.
                    //if (getLanguageVersion() >= 30) {
                        UndeclaredVariable uvar = undeclaredVariables.get(qName);
                        if (uvar != null) {
                            // second or subsequent reference to the as-yet-undeclared variable
                            GlobalVariableReference ref = new GlobalVariableReference(qName);
                            uvar.registerReference(ref);
                            return ref;
                        } else {
                            // first reference to the as-yet-undeclared variable
                            uvar = new UndeclaredVariable();
                            uvar.setPackageData(main.getPackageData());
                            uvar.setVariableQName(qName);
                            GlobalVariableReference ref = new GlobalVariableReference(qName);
                            uvar.registerReference(ref);
                            undeclaredVariables.put(qName, uvar);
                            return ref;
                        }
//                    } else {
//                        XPathException err = new XPathException("Variable $" + qName.getDisplayName() + " has not been declared");
//                        err.setErrorCode("XPST0008");
//                        err.setIsStaticError(true);
//                        throw err;
//                    }
                } else {
                    if (var.isPrivate()) {
                        XPathException err = new XPathException("Variable $" + qName.getDisplayName() + " is private");
                        err.setErrorCode("XPST0008");
                        err.setIsStaticError(true);
                        throw err;
                    }
                }
            } else {
                // If the namespace hasn't been imported then we might as well throw the error right away
                XPathException err = new XPathException("Variable $" + qName.getDisplayName() + " has not been declared");
                err.setErrorCode("XPST0008");
                err.setIsStaticError(true);
                throw err;
            }
        } else {
            if (var.isPrivate() && (var.getSystemId() == null || !var.getSystemId().equals(getSystemId()))) {
                String message = "Variable $" + qName.getDisplayName() + " is private";
                if (var.getSystemId() == null) {
                    message += " (no base URI known)";
                }
                XPathException err = new XPathException(message, "XPST0008");
                err.setIsStaticError(true);
                throw err;
            }
        }
        GlobalVariableReference vref = new GlobalVariableReference(qName);
        var.registerReference(vref);
        return vref;
    }

    /**
     * Get the function library containing all the in-scope functions available in this static
     * context (that is, the functions available in this query module).
     * <p>This method is provided for use by advanced applications.
     * The details of the interface are subject to change.</p>
     *
     * @return the FunctionLibrary used. For XQuery, this will always be a FunctionLibraryList.
     * @see net.sf.saxon.functions.FunctionLibraryList
     */

    @Override
    public FunctionLibrary getFunctionLibrary() {
        return functionLibraryList;
    }

    /**
     * Get the functions declared locally within this module
     *
     * @return a FunctionLibrary object containing the function declarations
     */

    /*@NotNull*/
    public XQueryFunctionLibrary getLocalFunctionLibrary() {
        return (XQueryFunctionLibrary) functionLibraryList.get(localFunctionLibraryNr);
    }

    /**
     * Register a user-defined XQuery function.
     * <p>This method is intended for internal use only.</p>
     *
     * @param function the function being declared
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs, for example
     *          a duplicate function name
     */

    public void declareFunction(/*@NotNull*/ XQueryFunction function) throws XPathException {
        Configuration config = getConfiguration();
        if (function.getNumberOfArguments() == 1) {
            StructuredQName name = function.getFunctionName();
            SchemaType t = config.getSchemaType(name);
            if (t != null && t.isAtomicType()) {
                XPathException err = new XPathException("Function name " + function.getDisplayName() +
                        " clashes with the name of the constructor function for an atomic type");
                err.setErrorCode("XQST0034");
                err.setIsStaticError(true);
                throw err;
            }
        }
        XQueryFunctionLibrary local = getLocalFunctionLibrary();
        local.declareFunction(function);
        //if (!function.isPrivate()) {
        QueryModule main = getTopLevelModule();
        main.globalFunctionLibrary.declareFunction(function);
        //}
    }

    /**
     * Bind function calls that could not be bound when first encountered. These
     * will either be forwards references to functions declared later in the same query module,
     * or in modules that are being imported recursively, or errors.
     * <p>This method is for internal use only.</p>
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if a function call refers to a function that has
     *          not been declared
     */

    public void bindUnboundFunctionCalls() throws XPathException {
        UnboundFunctionLibrary lib = (UnboundFunctionLibrary) functionLibraryList.get(unboundFunctionLibraryNr);
        lib.bindUnboundFunctionReferences(functionLibraryList, getConfiguration());
    }

    /**
     * Fixup all references to global functions. This method is called
     * on completion of query parsing. Each XQueryFunction is required to
     * bind all references to that function to the object representing the run-time
     * executable code of the function.
     * <p>This method is for internal use only. It is called only on the StaticQueryContext for the main
     * query body (not for library modules).</p>
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs
     */

    public void fixupGlobalFunctions() throws XPathException {
        globalFunctionLibrary.fixupGlobalFunctions(this);
    }

    /**
     * Optimize the body of all global functions.
     * <p>This method is for internal use only. It is called only on the StaticQueryContext for the main
     * query body (not for library modules).</p>
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs during optimization
     */

    public void optimizeGlobalFunctions() throws XPathException {
        globalFunctionLibrary.optimizeGlobalFunctions(this);
    }


    /**
     * Output "explain" information about each declared function.
     * <p>This method is intended primarily for internal use.</p>
     *
     * @param out the expression presenter used to display the output
     */

    public void explainGlobalFunctions(ExpressionPresenter out) throws XPathException{
        globalFunctionLibrary.explainGlobalFunctions(out);
    }

    /**
     * Get the function with a given name and arity. This method is provided so that XQuery functions
     * can be called directly from a Java application. Note that there is no type checking or conversion
     * of arguments when this is done: the arguments must be provided in exactly the form that the function
     * signature declares them.
     *
     * @param uri       the uri of the function name
     * @param localName the local part of the function name
     * @param arity     the number of arguments.
     * @return the user-defined function, or null if no function with the given name and arity can be located
     * @since 8.4
     */

    public UserFunction getUserDefinedFunction(String uri, String localName, int arity) {
        return globalFunctionLibrary.getUserDefinedFunction(uri, localName, arity);
    }

    /**
     * Bind unbound variables (these are typically variables that reference another module
     * participating in a same-namespace cycle, since local forwards references are not allowed)
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs, for example if the
     *          variable reference cannot be resolved or if the variable is private
     */

    public void bindUnboundVariables() throws XPathException {
        for (UndeclaredVariable uv : undeclaredVariables.values()) {
            StructuredQName qName = uv.getVariableQName();
            GlobalVariable var = variables.get(qName);
            if (var == null) {
                String uri = qName.getURI();
                if (importsNamespace(uri)) {
                    QueryModule main = getTopLevelModule();
                    var = main.libraryVariables.get(qName);
                }
            }
            if (var == null) {
                XPathException err = new XPathException("Unresolved reference to variable $" +
                        uv.getVariableQName().getDisplayName());
                err.setErrorCode("XPST0008");
                err.setIsStaticError(true);
                throw err;
            } else if (var.isPrivate() && !var.getSystemId().equals(getSystemId())) {
                XPathException err = new XPathException("Cannot reference a private variable in a different module");
                err.setErrorCode("XPST0008");
                err.setIsStaticError(true);
                throw err;
            } else {
                uv.transferReferences(var);
            }
        }
    }

    /**
     * Add an imported schema to this static context. A query module can reference
     * types in a schema provided two conditions are satisfied: the schema containing those
     * types has been loaded into the Configuration, and the target namespace has been imported
     * by this query module. This method achieves the second of these conditions. It does not
     * cause the schema to be loaded.
     *
     * @param targetNamespace The target namespace of the schema to be added
     * @param baseURI         The base URI against which the locationURIs are to be absolutized
     * @param locationURIs    a list of strings containing the absolutized URIs of the "location hints" supplied
     *                        for this schema
     * @since 8.4
     */

    public void addImportedSchema(String targetNamespace, String baseURI, /*@NotNull*/ List<String> locationURIs) {
        if (importedSchemata == null) {
            importedSchemata = new HashSet<>(5);
        }
        importedSchemata.add(targetNamespace);
        HashMap<String, HashSet<String>> loadedSchemata = getTopLevelModule().loadedSchemata;
        if (loadedSchemata == null) {
            loadedSchemata = new HashMap<>(5);
            getTopLevelModule().loadedSchemata = loadedSchemata;
        }
        HashSet<String> entries = loadedSchemata.get(targetNamespace);
        if (entries == null) {
            entries = new HashSet<>(locationURIs.size());
            loadedSchemata.put(targetNamespace, entries);
        }
        for (String relative : locationURIs) {
            try {
                URI abs = ResolveURI.makeAbsolute(relative, baseURI);
                entries.add(abs.toString());
            } catch (URISyntaxException e) {
                // ignore the URI if it's not valid
            }
        }
    }

    /**
     * Ask whether a given schema target namespace has been imported
     *
     * @param namespace The namespace of the required schema. Supply "" for
     *                  a no-namespace schema.
     * @return The schema if found, or null if not found.
     * @since 8.4
     */

    @Override
    public boolean isImportedSchema(String namespace) {
        return importedSchemata != null && importedSchemata.contains(namespace);
    }

    /**
     * Get the set of imported schemas
     *
     * @return a Set, the set of URIs representing the names of imported schemas
     */

    /*@Nullable*/
    @Override
    public Set<String> getImportedSchemaNamespaces() {
        if (importedSchemata == null) {
            return Collections.emptySet();
        } else {
            return importedSchemata;
        }
    }

    /**
     * Report a static error in the query (via the registered ErrorListener)
     *
     * @param err the error to be signalled
     */

    public void reportStaticError(/*@NotNull*/ XPathException err) {
        if (!err.hasBeenReported()) {
            reportStaticError(new XmlProcessingException(err));
            err.setHasBeenReported(true);
        }
    }

    /**
     * Report a static error in the query (via the registered ErrorListener)
     *
     * @param err the error to be signalled
     */

    public void reportStaticError(XmlProcessingError err) {
        userQueryContext.getErrorReporter().report(err);
        if (err.getFatalErrorMessage() != null) {
            throw new XmlProcessingAbort(err.getFatalErrorMessage());
        }
    }
    
    /**
     * Construct a dynamic context for early evaluation of constant subexpressions
     *
     * @return a dynamic context object
     */

    /*@NotNull*/
    @Override
    public XPathContext makeEarlyEvaluationContext() {
        return new EarlyEvaluationContext(getConfiguration());
    }


    /**
     * Get the name of the default collation.
     *
     * @return the name of the default collation; or the name of the codepoint collation
     *         if no default collation has been defined
     */

    /*@Nullable*/
    @Override
    public String getDefaultCollationName() {
        if (defaultCollationName == null) {
            defaultCollationName = NamespaceConstant.CODEPOINT_COLLATION_URI;
        }
        return defaultCollationName;
    }

    /**
     * Set the name of the default collation
     * @param collation the URI of the default collation
     */

    public void setDefaultCollationName(String collation) {
        defaultCollationName = collation;
    }

    /**
     * Register a namespace that is explicitly declared in the prolog of the query module.
     *
     * @param prefix The namespace prefix. Must not be null.
     * @param uri    The namespace URI. Must not be null. The value "" (zero-length string) is used
     *               to undeclare a namespace; it is not an error if there is no existing binding for
     *               the namespace prefix.
     * @throws net.sf.saxon.trans.XPathException
     *          if the declaration is invalid
     */

    public void declarePrologNamespace(/*@Nullable*/ String prefix, /*@Nullable*/ String uri) throws XPathException {
        if (prefix == null) {
            throw new NullPointerException("Null prefix supplied to declarePrologNamespace()");
        }
        if (uri == null) {
            throw new NullPointerException("Null namespace URI supplied to declarePrologNamespace()");
        }
        if (prefix.equals("xml") != uri.equals(NamespaceConstant.XML)) {
            XPathException err = new XPathException("Invalid declaration of the XML namespace");
            err.setErrorCode("XQST0070");
            err.setIsStaticError(true);
            throw err;
        }
        if (explicitPrologNamespaces.get(prefix) != null) {
            XPathException err = new XPathException("Duplicate declaration of namespace prefix \"" + prefix + '"');
            err.setErrorCode("XQST0033");
            err.setIsStaticError(true);
            throw err;
        } else {
            explicitPrologNamespaces.put(prefix, uri);
        }
    }

    /**
     * Declare an active namespace, that is, a namespace which as well as affecting the static
     * context of the query, will also be copied to the result tree when element constructors
     * are evaluated. When searching for a prefix-URI binding, active namespaces are searched
     * first, then passive namespaces. Active namespaces are later undeclared (in reverse sequence)
     * using {@link #undeclareNamespace()}.
     * <p>This method is intended for internal use only.</p>
     *
     * @param prefix the namespace prefix
     * @param uri    the namespace URI
     */

    public void declareActiveNamespace(/*@Nullable*/ String prefix, /*@Nullable*/ String uri) {
        if (prefix == null) {
            throw new NullPointerException("Null prefix supplied to declareActiveNamespace()");
        }
        if (uri == null) {
            throw new NullPointerException("Null namespace URI supplied to declareActiveNamespace()");
        }

        NamespaceBinding entry = new NamespaceBinding(prefix, uri);
        activeNamespaces.push(entry);

    }

    /**
     * Undeclare the most recently-declared active namespace. This method is called
     * when a namespace declaration goes out of scope (while processing an element end tag).
     * It is NOT called when an XML 1.1-style namespace undeclaration is encountered.
     * <p>This method is intended for internal use only.</p>
     *
     * @see #declareActiveNamespace(String, String)
     */

    public void undeclareNamespace() {
        activeNamespaces.pop();
    }

    /**
     * Return a NamespaceResolver which is "live" in the sense that, as the parse proceeeds,
     * it always uses the namespaces declarations in scope at the relevant time
     * @return a live NamespaceResolver
     */

    public NamespaceResolver getLiveNamespaceResolver() {
        return new NamespaceResolver() {
            /**
             * Get the namespace URI corresponding to a given prefix. Return null
             * if the prefix is not in scope.
             *
             * @param prefix     the namespace prefix. May be the zero-length string, indicating
             *                   that there is no prefix. This indicates either the default namespace or the
             *                   null namespace, depending on the value of useDefault.
             * @param useDefault true if the default namespace is to be used when the
             *                   prefix is "". If false, the method returns "" when the prefix is "". The default
             *                   namespace is a property of the NamespaceResolver; in general it corresponds to
             *                   the "default namespace for elements and types", but that cannot be assumed.
             * @return the uri for the namespace, or null if the prefix is not in scope.
             *         The "null namespace" is represented by the pseudo-URI "".
             */
            @Override
            public String getURIForPrefix(String prefix, boolean useDefault) {
                return checkURIForPrefix(prefix);
            }

            /**
             * Get an iterator over all the prefixes declared in this namespace context. This will include
             * the default namespace (prefix="") and the XML namespace where appropriate
             *
             * @return an iterator over all the prefixes for which a namespace binding exists, including
             *         the zero-length string to represent the null/absent prefix if it is bound
             */
            @Override
            public Iterator<String> iteratePrefixes() {
                return getNamespaceResolver().iteratePrefixes();
            }
        };
    }


    /**
     * Get the URI for a prefix if there is one, return null if not.
     * This method is used by the XQuery parser to resolve namespace prefixes.
     * <p>This method is intended primarily for internal use.</p>
     *
     * @param prefix The prefix. Supply "" to obtain the default namespace for elements and types.
     * @return the corresponding namespace URI, or null if the prefix has not
     *         been declared. If the prefix is "" and the default namespace is the non-namespace,
     *         return "".
     */

    /*@Nullable*/
    public String checkURIForPrefix(/*@NotNull*/ String prefix) {
        // Search the active namespaces first, then the passive ones.
        if (activeNamespaces != null) {
            for (int i = activeNamespaces.size() - 1; i >= 0; i--) {
                if (activeNamespaces.get(i).getPrefix().equals(prefix)) {
                    String uri = activeNamespaces.get(i).getURI();
                    if (uri.equals("") && !prefix.equals("")) {
                        // the namespace is undeclared
                        return null;
                    }
                    return uri;
                }
            }
        }
        if (prefix.isEmpty()) {
            return defaultElementNamespace;
        }
        String uri = explicitPrologNamespaces.get(prefix);
        if (uri != null) {
            // A zero-length URI means the prefix was undeclared in the prolog, and we mustn't look elsewhere
            return uri.isEmpty() ? null : uri;
        }

        if (userQueryContext != null) {
            uri = userQueryContext.getNamespaceForPrefix(prefix);
            if (uri != null) {
                return uri;
            }
        }
        return null;
    }


    /**
     * Get the default XPath namespace for elements and types. Note that this is not necessarily
     * the default namespace declared in the query prolog; within an expression, it may change in response
     * to namespace declarations on element constructors.
     *
     * @return the default namespace, or {@link NamespaceConstant#NULL} for the non-namespace
     */

    /*@Nullable*/
    @Override
    public String getDefaultElementNamespace() {
        return checkURIForPrefix("");
    }

    /**
     * Set the default element namespace as declared in the query prolog
     *
     * @param uri the default namespace for elements and types
     */

    public void setDefaultElementNamespace(String uri) {
        defaultElementNamespace = uri;
    }

    /**
     * Get the default function namespace
     *
     * @return the default namespace for function names
     */

    @Override
    public String getDefaultFunctionNamespace() {
        return defaultFunctionNamespace;
    }

    /**
     * Set the default function namespace
     *
     * @param uri the default namespace for functions
     */

    public void setDefaultFunctionNamespace(String uri) {
        defaultFunctionNamespace = uri;
    }

    /**
     * Set the revalidation mode. This is used only if XQuery Updates are in use, in other cases
     * the value is ignored.
     *
     * @param mode the revalidation mode. This must be one of {@link Validation#STRICT},
     *             {@link Validation#LAX}, or {@link Validation#SKIP}
     */

    public void setRevalidationMode(int mode) {
        if (mode == Validation.STRICT || mode == Validation.LAX || mode == Validation.SKIP) {
            revalidationMode = mode;
        } else {
            throw new IllegalArgumentException("Invalid mode " + mode);
        }
    }

    /**
     * Get the revalidation mode. This is used only if XQuery Updates are in use, in other cases
     * the value is ignored.
     *
     * @return the revalidation mode. This will be one of {@link Validation#STRICT},
     *         {@link Validation#LAX}, or {@link Validation#SKIP}
     */

    public int getRevalidationMode() {
        return revalidationMode;
    }

    /**
     * Get an list of all active namespaces. This returns the namespace bindings
     * declared using xmlns="XXX" or xmlns:p="XXX" declarations on containing direct
     * element constructors. Namespace undeclarations are ignored, except to the extent
     * that an xmlns="" declaration cancels an outer xmlns="XXX" declaration.
     * <p>This method is for internal use only.</p>
     *
     * @return a map of namespace bindings (prefix/uri pairs).
     */

    /*@NotNull*/
    NamespaceMap getActiveNamespaceBindings() {
        if (activeNamespaces == null) {
            return NamespaceMap.emptyMap();
        }
        NamespaceMap result = NamespaceMap.emptyMap();
        HashSet<String> prefixes = new HashSet<>(10);
        for (int n = activeNamespaces.size() - 1; n >= 0; n--) {
            NamespaceBinding an = activeNamespaces.get(n);
            if (!prefixes.contains(an.getPrefix())) {
                prefixes.add(an.getPrefix());
                if (!an.getURI().isEmpty()) {
                    result = result.put(an.getPrefix(), an.getURI());
                }
            }
        }
        return result;
    }

    /**
     * Get a copy of the Namespace Context. This method is used internally
     * by the query parser when a construct is encountered that needs
     * to save the namespace context for use at run-time. Note that unlike other implementations of
     * StaticContext, the state of the QueryModule changes as the query is parsed, with different namespaces
     * in scope at different times. It's therefore necessary to compute the whole namespace context each time.
     * <p>This method is for internal use only.</p>
     */

    /*@NotNull*/
    @Override
    public NamespaceResolver getNamespaceResolver() {
        NamespaceMap result = NamespaceMap.emptyMap();

        HashMap<String, String> userDeclaredNamespaces = userQueryContext.getUserDeclaredNamespaces();

        for (Map.Entry<String, String> e : userDeclaredNamespaces.entrySet()) {
            result = result.put(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, String> e : explicitPrologNamespaces.entrySet()) {
            result = result.put(e.getKey(), e.getValue());
        }
        if (!defaultElementNamespace.isEmpty()) {
            result = result.put("", defaultElementNamespace);
        }
        if (activeNamespaces == null) {
            return result;
        }
        HashSet<String> prefixes = new HashSet<>(10);
        for (int n = activeNamespaces.size() - 1; n >= 0; n--) {
            NamespaceBinding an = activeNamespaces.get(n);
            if (!prefixes.contains(an.getPrefix())) {
                prefixes.add(an.getPrefix());
                if (an.getURI().isEmpty()) {
                    result = result.remove(an.getPrefix());
                } else {
                    result = result.put(an.getPrefix(), an.getURI());
                }
            }
        }
        return result;
    }

    /**
     * Get the required type of the context item. If no type has been explicitly declared for the context
     * item, an instance of AnyItemType (representing the type item()) is returned.
     *
     * @return the required type of the context item. Note that this is the same for all modules.
     * @since 9.3
     */

    @Override
    public ItemType getRequiredContextItemType() {
        return requiredContextItemType;
    }

    /**
     * Get a DecimalFormatManager to resolve the names of decimal formats used in calls
     * to the format-number() function.
     *
     * @return the decimal format manager for this static context, or null if named decimal
     *         formats are not supported in this environment.
     */

    /*@Nullable*/
    @Override
    public DecimalFormatManager getDecimalFormatManager() {
        if (decimalFormatManager == null) {
            decimalFormatManager = new DecimalFormatManager(HostLanguage.XQUERY, getXPathVersion());
        }
        return decimalFormatManager;
    }

    /**
     * Issue a compile-time warning. This method is used during XQuery expression compilation to
     * output warning conditions.
     * <p>This method is intended for internal use only.</p>
     */

    @Override
    public void issueWarning(String s, Location locator) {
        XmlProcessingIncident err = new XmlProcessingIncident(s).asWarning();
        err.setLocation(locator);
        err.setHostLanguage(HostLanguage.XQUERY);
        userQueryContext.getErrorReporter().report(err);
    }

    /**
     * Determine whether Backwards Compatible Mode is used
     *
     * @return false; XPath 1.0 compatibility mode is not supported in XQuery
     * @since 8.4
     */

    @Override
    public boolean isInBackwardsCompatibleMode() {
        return false;
    }

    /**
     * Ask whether the query module is allowed to be updating
     *
     * @return true if the query module is allowed to use the XQuery Update facility
     * @since 9.1
     */

    public boolean isUpdating() {
        return isUpdating;
    }

    /**
     * Get the XPath language level supported, as an integer (being the actual version
     * number times ten). In Saxon 9.9 the only value supported for XQuery is 3.1
     *
     * @return the XPath language level; the return value will be 31
     * @since 9.7
     */

    @Override
    public int getXPathVersion() {
        return 31;
    }

    /**
     * Get the CodeInjector if one is in use
     *
     * @return the code injector if there is one
     */

    public CodeInjector getCodeInjector() {
        return codeInjector;
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
        return packageData.getKeyManager();
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
        return getPackageData().obtainTypeAliasManager().getItemType(typeName);
    }

    /**
     * Inner class containing information about an active namespace entry
     */

    private static class ActiveNamespace {
        /*@Nullable*/ public String prefix;
        /*@Nullable*/ public String uri;
    }
}

