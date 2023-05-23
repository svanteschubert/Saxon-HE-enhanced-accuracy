////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.accum.Accumulator;
import net.sf.saxon.expr.accum.AccumulatorRegistry;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.expr.parser.Optimizer;
import net.sf.saxon.expr.parser.OptimizerOptions;
import net.sf.saxon.functions.DocumentFn;
import net.sf.saxon.functions.ExecutableFunctionLibrary;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.query.XQueryFunction;
import net.sf.saxon.query.XQueryFunctionLibrary;
import net.sf.saxon.serialize.CharacterMap;
import net.sf.saxon.serialize.CharacterMapIndex;
import net.sf.saxon.trans.Timer;
import net.sf.saxon.trans.*;
import net.sf.saxon.trans.rules.RuleManager;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.linked.DocumentImpl;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Whitespace;
import net.sf.saxon.z.IntHashMap;

import javax.xml.transform.URIResolver;
import java.util.*;


/**
 * Represents both the stylesheet module at the root of the import tree, that is, the module
 * that includes or imports all the others, and also the XSLT package that has this stylesheet
 * module as its root.
 * <p>This version of the StylesheetPackage class represents a trivial package that is constructed
 * by the system to wrap an ordinary (non-package) stylesheet, as available in XSLT 2.0. A subclass
 * is used for "real" packages, currently available only in Saxon-EE.</p>
 */
public class PrincipalStylesheetModule extends StylesheetModule implements GlobalVariableManager {

    private StylesheetPackage stylesheetPackage;
    private boolean declaredModes;


    // table of functions imported from XQuery library modules
    //private XQueryFunctionLibrary queryFunctions;

    // library of functions that are in-scope for XPath expressions in this stylesheet
    //private FunctionLibraryList functionLibrary;

    // index of global variables and parameters, by StructuredQName
    // (overridden variables are excluded).
    private HashMap<StructuredQName, ComponentDeclaration> globalVariableIndex =
            new HashMap<>(20);


    // index of templates - only includes those actually declared within this package
    private HashMap<StructuredQName, ComponentDeclaration> templateIndex = new HashMap<>(20);

    // Table of named stylesheet functions.
    private HashMap<SymbolicName, ComponentDeclaration> functionIndex = new HashMap<>(8);

    // key manager for all the xsl:key definitions in this package
    private KeyManager keyManager;

    // decimal format manager for all the xsl:decimal-format definitions in this package
    private DecimalFormatManager decimalFormatManager;

    // rule manager for template rules
    private RuleManager ruleManager;

    // manager class for accumulator rules (XSLT 3.0 only)
    private AccumulatorRegistry accumulatorManager = null;

    // namespace aliases. This information is needed at compile-time only
    private int numberOfAliases = 0;
    private List<ComponentDeclaration> namespaceAliasList = new ArrayList<>(5);
    private HashMap<String, NamespaceBinding> namespaceAliasMap;
    private Set<String> aliasResultUriSet;

    // attribute sets. A package can contain several declarations attribute sets with the same name.
    // They are indexed in the order they will be applied, that is, highest precedence first
    private Map<StructuredQName, List<ComponentDeclaration>> attributeSetDeclarations = new HashMap<>();

    // cache of stylesheet documents. Note that multiple imports of the same URI
    // lead to the stylesheet tree being reused
    private HashMap<DocumentKey, XSLModuleRoot> moduleCache = new HashMap<>(4);

    private TypeAliasManager typeAliasManager;


    private CharacterMapIndex characterMapIndex;

    private List<Action> fixupActions = new ArrayList<>();
    private boolean needsDynamicOutputProperties = false;


    /**
     * Create a PrincipalStylesheetModule
     *
     * @param sourceElement the xsl:package element at the root of the package manifest
     */

    public PrincipalStylesheetModule(XSLPackage sourceElement) throws XPathException {
        super(sourceElement, 0);
        declaredModes = sourceElement.isDeclaredModes();
        stylesheetPackage = getConfiguration().makeStylesheetPackage();
        CompilerInfo compilerInfo = sourceElement.getCompilation().getCompilerInfo();
        stylesheetPackage.setTargetEdition(compilerInfo.getTargetEdition());
        stylesheetPackage.setRelocatable(compilerInfo.isRelocatable());
        stylesheetPackage.setJustInTimeCompilation(compilerInfo.isJustInTimeCompilation());
        stylesheetPackage.setImplicitPackage(!sourceElement.getLocalPart().equals("package"));

        keyManager = stylesheetPackage.getKeyManager();
        decimalFormatManager = stylesheetPackage.getDecimalFormatManager();

        ruleManager = new RuleManager(stylesheetPackage, compilerInfo);
        ruleManager.getUnnamedMode().makeDeclaringComponent(Visibility.PRIVATE, stylesheetPackage);
        stylesheetPackage.setRuleManager(ruleManager);
        stylesheetPackage.setDeclaredModes(declaredModes);
        StructuredQName defaultMode = sourceElement.getDefaultMode();
        stylesheetPackage.setDefaultMode(sourceElement.getDefaultMode());
        if (defaultMode != null) {
            ruleManager.obtainMode(defaultMode, !declaredModes);
        }


        characterMapIndex = new CharacterMapIndex();
        stylesheetPackage.setCharacterMapIndex(characterMapIndex);

        typeAliasManager = getConfiguration().makeTypeAliasManager();
        stylesheetPackage.setTypeAliasManager(typeAliasManager);

        try {
            setInputTypeAnnotations(sourceElement.getInputTypeAnnotationsAttribute());
        } catch (XPathException err) {
            // it will be reported some other time
        }
    }


    /**
     * Search the package for a component with a given name
     *
     * @param name the symbolic name of the required component
     * @return the requested component if found, or null otherwise
     */

    public Component getComponent(SymbolicName name) {
        return stylesheetPackage.getComponentIndex().get(name);
    }

    /**
     * Get the outermost stylesheet module in a package
     *
     * @return this module (this class represents both the package and its outermost module)
     */

    /*@NotNull*/
    @Override
    public PrincipalStylesheetModule getPrincipalStylesheetModule() {
        return this;
    }

    /**
     * Get the stylesheet package
     *
     * @return the associated stylesheet package
     */

    public StylesheetPackage getStylesheetPackage() {
        return stylesheetPackage;
    }

    /**
     * Get the key manager used to manage xsl:key declarations in this package
     *
     * @return the key manager
     */

    public KeyManager getKeyManager() {
        return keyManager;
    }

    /**
     * Get the decimal format manager used to manage xsl:decimal-format declarations in this package
     *
     * @return the decimal format manager
     */

    public DecimalFormatManager getDecimalFormatManager() {
        return decimalFormatManager;
    }

    /**
     * Get the rule manager used to manage modes declared explicitly or implicitly in this package
     *
     * @return the rule manager
     */

    public RuleManager getRuleManager() {
        return ruleManager;
    }

    /**
     * Ask whether it is required that modes be explicitly declared
     *
     * @return true if modes referenced within this package must be explicitly declared
     */

    public boolean isDeclaredModes() {
        return declaredModes;
    }


    /**
     * Register a callback action to be performed during the fixup phase
     *
     * @param action the callback action
     */

    public void addFixupAction(Action action) {
        fixupActions.add(action);
    }

    /**
     * Say that this stylesheet package needs dynamic output properties
     *
     * @param b true if this stylesheet needs dynamic output properties
     */

    public void setNeedsDynamicOutputProperties(boolean b) {
        needsDynamicOutputProperties = b;
    }


    /**
     * Get an index of character maps declared using xsl:character-map entries in this package
     *
     * @return the character map index
     */

    public CharacterMapIndex getCharacterMapIndex() {
        return characterMapIndex;
    }

    /**
     * Get the type alias manager (type aliases are a Saxon extension)
     *
     * @return the type alias manager
     */

    public TypeAliasManager getTypeAliasManager() {
        return typeAliasManager;
    }


    /**
     * Declare an imported XQuery function
     *
     * @param function the imported function
     * @throws net.sf.saxon.trans.XPathException if an error occurs
     */

    public void declareXQueryFunction(XQueryFunction function) throws XPathException {
        XQueryFunctionLibrary lib = getStylesheetPackage().getXQueryFunctionLibrary();
        if (getStylesheetPackage().getFunction(function.getUserFunction().getSymbolicName()) != null) {
            throw new XPathException("Duplicate declaration of " +
                                             function.getUserFunction().getSymbolicName(), "XQST0034");
        }
        lib.declareFunction(function);
    }

    /**
     * Add a module to the cache of stylesheet modules
     *
     * @param key    the key to be used (based on the absolute URI)
     * @param module the stylesheet document tree corresponding to this absolute URI
     */

    public void putStylesheetDocument(DocumentKey key, XSLStylesheet module) {
        moduleCache.put(key, module);
    }

    /**
     * Get a module from the cache of stylesheet modules
     *
     * @param key the key to be used (based on the absolute URI)
     * @return the stylesheet document tree corresponding to this absolute URI
     */

    public XSLModuleRoot getStylesheetDocument(DocumentKey key) {
        XSLModuleRoot sheet = moduleCache.get(key);
        if (sheet != null) {
            XPathException warning = new XPathException(
                    "Stylesheet module " + key + " is included or imported more than once. " +
                            "This is permitted, but may lead to errors or unexpected behavior");
            sheet.issueWarning(warning);
        }
        return sheet;
    }

    /**
     * Preprocess does all the processing possible before the source document is available.
     * It is done once per stylesheet, so the stylesheet can be reused for multiple source
     * documents. The method is called only on the XSLStylesheet element representing the
     * principal stylesheet module
     *
     * @throws net.sf.saxon.trans.XPathException if errors are found in the stylesheet
     */

    public void preprocess(Compilation compilation) throws XPathException {

        Timer timer = compilation.timer;

        // process any xsl:use-package, xsl:include and xsl:import elements

        spliceUsePackages((XSLPackage) getRootElement(), getRootElement().getCompilation());

        if (Compilation.TIMING) {
            timer.report("spliceIncludes");
        }

        // import schema documents

        importSchemata();

        if (Compilation.TIMING) {
            timer.report("importSchemata");
        }

        // process type aliases

        getTypeAliasManager().processAllDeclarations(topLevel);

        // build indexes for selected top-level elements

        buildIndexes();

        if (Compilation.TIMING) {
            timer.report("buildIndexes");
        }

        // check for use of schema-aware constructs

        checkForSchemaAwareness();

        if (Compilation.TIMING) {
            timer.report("checkForSchemaAwareness");
        }

        // process the attributes of every node in the tree

        processAllAttributes();

        if (Compilation.TIMING) {
            timer.report("processAllAttributes");
        }
        // collect any namespace aliases

        collectNamespaceAliases();

        if (Compilation.TIMING) {
            timer.report("collectNamespaceAliases");
        }

        // fix up references from XPath expressions to variables and functions, for static typing

        for (ComponentDeclaration decl : topLevel) {
            StyleElement inst = decl.getSourceElement();
            if (!inst.isActionCompleted(StyleElement.ACTION_FIXUP)) {
                inst.setActionCompleted(StyleElement.ACTION_FIXUP);
                inst.fixupReferences();
            }
        }

        if (Compilation.TIMING) {
            timer.report("fixupReferences");
        }


        // Validate the whole package (i.e. with included and imported stylesheet modules)

        XSLPackage top = (XSLPackage) getStylesheetElement();
        //setInputTypeAnnotations(top.getInputTypeAnnotationsAttribute());
        ComponentDeclaration decl = new ComponentDeclaration(this, top);
        if (!top.isActionCompleted(StyleElement.ACTION_VALIDATE)) {
            top.setActionCompleted(StyleElement.ACTION_VALIDATE);
            top.validate(null);
            for (ComponentDeclaration d : topLevel) {
                d.getSourceElement().validateSubtree(d, false);
            }
        }

        if (Compilation.TIMING) {
            timer.report("validate");
        }

        // Gather the output properties

        Properties props = gatherOutputProperties(null);
        props.setProperty(SaxonOutputKeys.STYLESHEET_VERSION, getStylesheetPackage().getVersion() + "");
        getStylesheetPackage().setDefaultOutputProperties(props);

        // Handle named output formats for use at run-time

        HashSet<StructuredQName> outputNames = new HashSet<>(5);
        for (ComponentDeclaration outputDecl : topLevel) {
            if (outputDecl.getSourceElement() instanceof XSLOutput) {
                XSLOutput out = (XSLOutput) outputDecl.getSourceElement();
                StructuredQName qName = out.getFormatQName();
                if (qName != null) {
                    outputNames.add(qName);
                }
            }
        }
        if (outputNames.isEmpty()) {
            if (needsDynamicOutputProperties) {
                throw new XPathException(
                        "The stylesheet contains xsl:result-document instructions that calculate the output " +
                                "format name at run-time, but there are no named xsl:output declarations", "XTDE1460");
            }
        } else {
            for (StructuredQName qName : outputNames) {
                Properties oprops = gatherOutputProperties(qName);
                //if (needsDynamicOutputProperties) {  // needed for saxon:serialize
                getStylesheetPackage().setNamedOutputProperties(qName, oprops);
                //}
            }
        }

        if (Compilation.TIMING) {
            timer.report("Register output formats");
        }

        // Index the character maps

        for (ComponentDeclaration d : topLevel) {
            StyleElement inst = d.getSourceElement();
            if (inst instanceof XSLCharacterMap) {
                XSLCharacterMap xcm = (XSLCharacterMap) inst;
                StructuredQName qn = xcm.getCharacterMapName();
                IntHashMap<String> map = new IntHashMap<>();
                xcm.assemble(map);
                characterMapIndex.putCharacterMap(xcm.getCharacterMapName(), new CharacterMap(qn, map));
            }
        }

        if (Compilation.TIMING) {
            timer.report("Index character maps");
        }

    }

    /**
     * Incorporate declarations from used packages
     *
     * @param xslpackage  the used package
     * @param compilation this compilation
     * @throws XPathException if any error is detected in the used package
     */

    protected void spliceUsePackages(XSLPackage xslpackage, Compilation compilation) throws XPathException {
//        CompilerInfo info = compilation.getCompilerInfo();

        // Warning message deleted by bug 3278
//        if (info.isVersionWarning() &&
//                xslpackage.getEffectiveVersion() != 30) {
//            XPathException w = new XPathException(
//                    "Running an XSLT " + xslpackage.getEffectiveVersionAsString() + " stylesheet with an XSLT 3.0 processor");
//            w.setLocator(xslpackage);
//            compilation.reportWarning(w);
//        }

        List<XSLUsePackage> useDeclarations = new ArrayList<>();
        gatherUsePackageDeclarations(compilation, xslpackage, useDeclarations);

        // First pass: gather all the named overriding declarations and add them to the topLevel declaration list
        Set<SymbolicName> overrides = new HashSet<>();
        for (XSLUsePackage use : useDeclarations) {
            gatherOverridingDeclarations(use, compilation, overrides);
        }
        //gatherOverridingDeclarations(compilation, xslpackage, overrides);

        // Second pass: make modified copies of the named components in the used packages
        StylesheetPackage thisPackage = getStylesheetPackage();
        for (XSLUsePackage use : useDeclarations) {
            List<XSLAccept> acceptors = use.getAcceptors();
            thisPackage.addComponentsFromUsedPackage(use.getUsedPackage(), acceptors, overrides);
        }

        // Third pass: process the overriding template rules, creating new mode objects
        for (XSLUsePackage use : useDeclarations) {
            use.gatherRuleOverrides(this, overrides);
        }

        // Now process the declarations contained within this package, both in the top-level module
        // and within its included and imported modules
        spliceIncludes();
    }


    private static void gatherUsePackageDeclarations(Compilation compilation, StyleElement wrapper, List<XSLUsePackage> declarations) throws XPathException {
        for (NodeInfo use : wrapper.children()) {
            if (use instanceof XSLUsePackage) {
                declarations.add((XSLUsePackage) use);
            } else if (use instanceof XSLInclude) {
                String href = Whitespace.trim(use.getAttributeValue("", "href"));
                URIResolver resolver = compilation.getCompilerInfo().getURIResolver();
                DocumentKey key = DocumentFn.computeDocumentKey(href, use.getBaseURI(), compilation.getPackageData(), resolver, false);
                TreeInfo includedTree = compilation.getStylesheetModules().get(key);
                StyleElement incWrapper = (StyleElement) ((DocumentImpl) includedTree.getRootNode()).getDocumentElement();
                gatherUsePackageDeclarations(compilation, incWrapper, declarations);
            }
        }
    }

    private void gatherOverridingDeclarations(XSLUsePackage use, Compilation compilation, Set<SymbolicName> overrides) throws XPathException {
        use.findUsedPackage(compilation.getCompilerInfo());
        use.gatherNamedOverrides(this, topLevel, overrides);
    }


    /**
     * Process import-schema declarations
     *
     * @throws net.sf.saxon.trans.XPathException if errors are detected
     */

    protected void importSchemata() throws XPathException {
        // Outside Saxon-EE, xsl:import-schemas are an error
        for (int i = topLevel.size() - 1; i >= 0; i--) {
            ComponentDeclaration decl = topLevel.get(i);
            if (decl.getSourceElement() instanceof XSLImportSchema) {
                XPathException xe = new XPathException("xsl:import-schema requires Saxon-EE");
                xe.setErrorCode("XTSE1650");
                xe.setLocator(decl.getSourceElement());
                throw xe;
            }
        }

    }


    /**
     * Build indexes for selected top-level declarations
     *
     * @throws net.sf.saxon.trans.XPathException if errors are detected
     */

    private void buildIndexes() throws XPathException {
        // Scan the declarations in reverse order, that is, highest precedence first
        for (int i = topLevel.size() - 1; i >= 0; i--) {
            ComponentDeclaration decl = topLevel.get(i);
            decl.getSourceElement().index(decl, this);
        }
    }

    /**
     * Process the attributes of every node in the stylesheet
     *
     * @throws net.sf.saxon.trans.XPathException if static errors are found in the stylesheet
     */

    public void processAllAttributes() throws XPathException {
        getRootElement().processDefaultCollationAttribute();
        getRootElement().processDefaultMode();
        getRootElement().prepareAttributes();
        for (XSLModuleRoot xss : moduleCache.values()) {
            xss.prepareAttributes();
        }
        for (ComponentDeclaration decl : topLevel) {
            StyleElement inst = decl.getSourceElement();
            if (!inst.isActionCompleted(StyleElement.ACTION_PROCESS_ATTRIBUTES)) {
                inst.setActionCompleted(StyleElement.ACTION_PROCESS_ATTRIBUTES);
                try {
                    inst.processAllAttributes();
                } catch (XPathException err) {
                    decl.getSourceElement().compileError(err);
                }
            }
        }
    }

    /**
     * Add a stylesheet function to the index
     *
     * @param decl The declaration wrapping an XSLFunction object
     */
    protected void indexFunction(ComponentDeclaration decl) {
        HashMap<SymbolicName, Component> componentIndex = stylesheetPackage.getComponentIndex();
        XSLFunction sourceFunction = (XSLFunction) decl.getSourceElement();
        UserFunction compiledFunction = sourceFunction.getCompiledFunction();
        Component declaringComponent = compiledFunction.obtainDeclaringComponent(sourceFunction);
        SymbolicName.F sName = sourceFunction.getSymbolicName();
        //StructuredQName qName = template.getTemplateName();
        if (sName != null) {
            // see if there is already a named function with this precedence
            Component other = componentIndex.get(sName);
            if (other == null) {
                // this is the first
                componentIndex.put(sName, declaringComponent);
                functionIndex.put(sName, decl);
            } else {
                if (other.getDeclaringPackage() == getStylesheetPackage()) {
                    // check the precedences
                    int thisPrecedence = decl.getPrecedence();
                    ComponentDeclaration otherFunction = functionIndex.get(sName);
                    int otherPrecedence = otherFunction.getPrecedence();
                    if (thisPrecedence == otherPrecedence) {
                        sourceFunction.compileError("Duplicate named function (see line " +
                                                            otherFunction.getSourceElement().getLineNumber() + " of " + otherFunction.getSourceElement().getSystemId() + ')', "XTSE0770");
                    } else if (thisPrecedence < otherPrecedence) {
                        //template.setRedundantNamedTemplate();
                    } else {
                        // can't happen, but we'll play safe
                        //other.setRedundantNamedTemplate();
                        componentIndex.put(sName, declaringComponent);
                        functionIndex.put(sName, decl);
                    }
                } else if (sourceFunction.findAncestorElement(StandardNames.XSL_OVERRIDE) != null) {
                    // the new one wins
                    componentIndex.put(sName, declaringComponent);
                    functionIndex.put(sName, decl);
                } else {
                    sourceFunction.compileError("Function " + sName.getShortName() +
                                                        " conflicts with a public function in package " + other.getDeclaringPackage().getPackageName(), "XTSE3050");

                }
            }
        }

    }

    /**
     * Index a global xsl:variable or xsl:param element
     *
     * @param decl The Declaration referencing the XSLVariable or XSLParam element
     * @throws XPathException if an error occurs
     */

    protected void indexVariableDeclaration(ComponentDeclaration decl) throws XPathException {
        XSLGlobalVariable varDecl = (XSLGlobalVariable) decl.getSourceElement();
        StructuredQName qName = varDecl.getSourceBinding().getVariableQName();
        GlobalVariable compiledVariable = (GlobalVariable) varDecl.getActor();
        Component declaringComponent = compiledVariable.obtainDeclaringComponent(varDecl);
        HashMap<SymbolicName, Component> componentIndex = stylesheetPackage.getComponentIndex();
        if (qName != null) {
            // see if there is already a global variable with this precedence
            SymbolicName sName = varDecl.getSymbolicName();
            Component other = componentIndex.get(sName);
            if (other == null) {
                // this is the first
                globalVariableIndex.put(qName, decl);
                componentIndex.put(new SymbolicName(StandardNames.XSL_VARIABLE, qName),
                                   varDecl.getActor().getDeclaringComponent());
            } else {
                if (other.getDeclaringPackage() == getStylesheetPackage()) {
                    // check the precedences
                    int thisPrecedence = decl.getPrecedence();
                    ComponentDeclaration otherVarDecl = globalVariableIndex.get(sName.getComponentName());
                    int otherPrecedence = otherVarDecl.getPrecedence();
                    if (thisPrecedence == otherPrecedence) {
                        StyleElement v2 = otherVarDecl.getSourceElement();
                        if (v2 == varDecl) {
                            varDecl.compileError(
                                    "Global variable or parameter $" + qName.getDisplayName() + " is declared more than once " +
                                            "(caused by including the containing module more than once)",
                                    "XTSE0630");
                        } else {
                            varDecl.compileError("Duplicate global variable/parameter declaration (see line " +
                                                         v2.getLineNumber() + " of " + v2.getSystemId() + ')', "XTSE0630");
                        }
                    } else if (thisPrecedence < otherPrecedence && varDecl != otherVarDecl.getSourceElement()) {
                        varDecl.setRedundant(true);
                    } else if (varDecl != otherVarDecl.getSourceElement()) {
                        ((XSLGlobalVariable) otherVarDecl.getSourceElement()).setRedundant(true);
                        globalVariableIndex.put(qName, decl);
                        componentIndex.put(new SymbolicName(StandardNames.XSL_VARIABLE, qName),
                                           varDecl.getActor().getDeclaringComponent());
                    }
                } else if (varDecl.findAncestorElement(StandardNames.XSL_OVERRIDE) != null) {
                    // the new one wins
                    componentIndex.put(sName, declaringComponent);
                    globalVariableIndex.put(sName.getComponentName(), decl);
                } else {
                    String kind = varDecl instanceof XSLGlobalParam ? "parameter" : "variable";
                    varDecl.compileError("Global " + kind + " $" + sName.getComponentName().getDisplayName() +
                                                 " conflicts with a public variable/parameter in package " + other.getDeclaringPackage().getPackageName(), "XTSE3050");

                }

            }
        }
    }

    /**
     * Get the global variable or parameter with a given name (taking
     * precedence rules into account). This will only return global variables
     * declared in the same package where they are used.
     *
     * @param qName name of the global variable or parameter
     * @return the variable declaration, or null if it does not exist
     */

    public SourceBinding getGlobalVariableBinding(StructuredQName qName) {
        ComponentDeclaration decl = globalVariableIndex.get(qName);
        return decl == null ? null : ((XSLGlobalVariable) decl.getSourceElement()).getSourceBinding();
    }

    /**
     * Add a named template to the index
     *
     * @param decl the declaration of the Template object
     * @throws XPathException if an error occurs
     */
    protected void indexNamedTemplate(ComponentDeclaration decl) throws XPathException {
        HashMap<SymbolicName, Component> componentIndex = stylesheetPackage.getComponentIndex();
        XSLTemplate sourceTemplate = (XSLTemplate) decl.getSourceElement();
        SymbolicName sName = sourceTemplate.getSymbolicName();
        if (sName != null) {
            // see if there is already a named template with this precedence
            Component other = componentIndex.get(sName);
            if (other == null) {
                // this is the first
                //NamedTemplate compiledTemplate = new NamedTemplate();
                NamedTemplate compiledTemplate = ((XSLTemplate) decl.getSourceElement()).getCompiledNamedTemplate();
                Component declaringComponent = compiledTemplate.obtainDeclaringComponent(sourceTemplate);
                componentIndex.put(sName, declaringComponent);
                setLocalParamDetails(sourceTemplate, compiledTemplate);
                templateIndex.put(sName.getComponentName(), decl);
            } else {
                if (other.getDeclaringPackage() == getStylesheetPackage()) {
                    // check the precedences
                    int thisPrecedence = decl.getPrecedence();
                    ComponentDeclaration otherTemplate = templateIndex.get(sName.getComponentName());
                    int otherPrecedence = otherTemplate.getPrecedence();
                    if (thisPrecedence == otherPrecedence) {
                        String errorCode = sourceTemplate.getParent() instanceof XSLOverride ? "XTSE3055" : "XTSE0660";
                        sourceTemplate.compileError("Duplicate named template (see line " +
                                                            otherTemplate.getSourceElement().getLineNumber() + " of " + otherTemplate.getSourceElement().getSystemId() + ')', errorCode);
                    } else if (thisPrecedence < otherPrecedence) {
                        //return;
                    } else {
                        // can't happen, but we'll play safe
                        //other.setRedundantNamedTemplate();
                        NamedTemplate compiledTemplate = new NamedTemplate(sName.getComponentName());
                        Component declaringComponent = compiledTemplate.obtainDeclaringComponent(sourceTemplate);
                        componentIndex.put(sName, declaringComponent);
                        templateIndex.put(sName.getComponentName(), decl);
                        setLocalParamDetails(sourceTemplate, compiledTemplate);
                    }
                } else if (sourceTemplate.findAncestorElement(StandardNames.XSL_OVERRIDE) != null) {
                    // the new one wins
                    NamedTemplate compiledTemplate = sourceTemplate.getCompiledNamedTemplate();//new NamedTemplate();
                    Component declaringComponent = compiledTemplate.obtainDeclaringComponent(sourceTemplate);
                    componentIndex.put(sName, declaringComponent);
                    templateIndex.put(sName.getComponentName(), decl);
                } else {
                    sourceTemplate.compileError("Named template " + sName.getComponentName().getDisplayName() +
                                                        " conflicts with a public named template in package " + other.getDeclaringPackage().getPackageName(), "XTSE3050");

                }
            }
        }
    }


    private static void setLocalParamDetails(XSLTemplate source, NamedTemplate nt) throws XPathException {
        AxisIterator kids = source.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
        List<NamedTemplate.LocalParamInfo> details = new ArrayList<>();
        kids.forEachOrFail(child -> {
            if (child instanceof XSLLocalParam) {
                XSLLocalParam lp = (XSLLocalParam) child;
                lp.prepareTemplateSignatureAttributes();
                NamedTemplate.LocalParamInfo info = new NamedTemplate.LocalParamInfo();
                info.name = lp.getVariableQName();
                info.requiredType = lp.getRequiredType();
                info.isRequired = lp.isRequiredParam();
                info.isTunnel = lp.isTunnelParam();
                details.add(info);
            }
        });
        nt.setLocalParamDetails(details);
    }


    /**
     * Get the named template with a given name
     *
     * @param name the name of the required template
     * @return the template with the given name, if there is one, or null otherwise. If there
     * are several templates with the same name, the one with highest import precedence
     * is returned. Note that this template may subsequently be overridden in another package,
     * but only by another template with a compatible signature.
     */

    public NamedTemplate getNamedTemplate(StructuredQName name) {
        HashMap<SymbolicName, Component> componentIndex = stylesheetPackage.getComponentIndex();
        Component component = componentIndex.get(new SymbolicName(StandardNames.XSL_TEMPLATE, name));
        return component == null ? null : (NamedTemplate) component.getActor();
    }

    /**
     * Add an attribute set to the index
     *
     * @param decl the declaration of the attribute set object
     * @throws XPathException if an error occurs
     */
    protected void indexAttributeSet(ComponentDeclaration decl) throws XPathException {
        //HashMap<SymbolicName, Component> componentIndex = stylesheetPackage.getComponentIndex();
        XSLAttributeSet sourceAttributeSet = (XSLAttributeSet) decl.getSourceElement();
        StructuredQName name = sourceAttributeSet.getAttributeSetName();
        List<ComponentDeclaration> entries = attributeSetDeclarations.get(name);
        if (entries == null) {
            entries = new ArrayList<>();
            attributeSetDeclarations.put(name, entries);
        } else {
            String thisVis = Whitespace.trim(sourceAttributeSet.getAttributeValue("", "visibility"));
            String firstVis = Whitespace.trim(entries.get(0).getSourceElement().getAttributeValue("", "visibility"));
            if (thisVis == null ? firstVis != null : !thisVis.equals(firstVis)) {
                throw new XPathException("Visibility attributes on attribute-sets sharing the same name must all be the same", "XTSE0010");
            }
        }
        entries.add(0, decl);
    }

    /**
     * Return all the attribute set declarations in the package having a particular QName
     *
     * @param name the name of the required declarations
     * @return the set of declarations having this name, or null if there are none
     */

    public List<ComponentDeclaration> getAttributeSetDeclarations(StructuredQName name) {
        return attributeSetDeclarations.get(name);
    }

    /**
     * Combine all like-named xsl:attribute-set declarations in this package into a single Component, whose body
     * is an AttributeSet object. The resulting attribute set Components are saved in the StylesheetPackage
     * object.
     *
     * @throws XPathException if a failure occurs
     */


    public void combineAttributeSets(Compilation compilation) throws XPathException {
        Map<StructuredQName, AttributeSet> index = new HashMap<>();
        for (Map.Entry<StructuredQName, List<ComponentDeclaration>> entry : attributeSetDeclarations.entrySet()) {
            AttributeSet as = new AttributeSet();
            as.setName(entry.getKey());
            as.setPackageData(stylesheetPackage);
            StyleElement firstDecl = entry.getValue().get(0).getSourceElement();
            as.setSystemId(firstDecl.getSystemId());
            as.setLineNumber(firstDecl.getLineNumber());
            as.setColumnNumber(firstDecl.getColumnNumber());
            index.put(entry.getKey(), as);

            Component declaringComponent = as.getDeclaringComponent();
            if (declaringComponent == null) {
                declaringComponent = as.makeDeclaringComponent(Visibility.PRIVATE, stylesheetPackage);
            }
            stylesheetPackage.addComponent(declaringComponent);
        }
        for (Map.Entry<StructuredQName, List<ComponentDeclaration>> entry : attributeSetDeclarations.entrySet()) {
            List<Expression> content = new ArrayList<>();
            Visibility vis = null;
            boolean explicitVisibility = false;
            boolean streamable = false;

            // Bug 3195. If the same xsl:attribute-set element is present more than
            // once in the list, we need to remove all but the last occurrence, otherwise
            // the same expression will be present more than once in the tree. This can
            // happen when a stylesheet module is included/imported more than once.
            List<ComponentDeclaration> entries = new ArrayList<>();
            Set<XSLAttributeSet> elements = new HashSet<>();
            for (int i = entry.getValue().size() - 1; i >= 0; i--) {
                ComponentDeclaration decl = entry.getValue().get(i);
                XSLAttributeSet src = (XSLAttributeSet) decl.getSourceElement();
                if (!elements.contains(src)) {
                    entries.add(0, decl);
                    elements.add(src);
                }
            }
            for (ComponentDeclaration decl : entries) {
                XSLAttributeSet src = (XSLAttributeSet) decl.getSourceElement();
                streamable |= src.isDeclaredStreamable();
                src.compileDeclaration(compilation, decl);
                content.addAll(src.getContainedInstructions());
                vis = src.getVisibility();
                explicitVisibility = explicitVisibility || src.getAttributeValue("", "visibility") != null;
            }

            AttributeSet aSet = index.get(entry.getKey());
            aSet.setDeclaredStreamable(streamable);
            Expression block = Block.makeBlock(content);
            aSet.setBody(block);
            SlotManager frame = getConfiguration().makeSlotManager();
            ExpressionTool.allocateSlots(block, 0, frame);
            aSet.setStackFrameMap(frame);
            VisibilityProvenance provenance = explicitVisibility ? VisibilityProvenance.EXPLICIT : VisibilityProvenance.DEFAULTED;
            aSet.getDeclaringComponent().setVisibility(vis, provenance);

            if (streamable) {
                checkStreamability(aSet);
            }

        }
    }

    /**
     * Check the streamability of an attribute set declared within this stylesheet module.
     * (Null implementation for Saxon-HE).
     *
     * @param aSet the attribute set to be checked
     * @throws XPathException if the streamability rules are not satisifed
     */

    protected void checkStreamability(AttributeSet aSet) throws XPathException {
    }

    /**
     * Get the list of attribute-set declarations associated with a given QName.
     * This is used for xsl:element, xsl:copy, xsl:attribute-set, and on literal
     * result elements
     *
     * @param name the name of the required attribute set
     * @param list a list to hold the list of XSLAttributeSet elements in the stylesheet tree.
     */

    protected void getAttributeSets(StructuredQName name, List<ComponentDeclaration> list) {

        // search for the named attribute set, using all of them if there are several with the
        // same name

        for (ComponentDeclaration decl : topLevel) {
            if (decl.getSourceElement() instanceof XSLAttributeSet) {
                XSLAttributeSet t = (XSLAttributeSet) decl.getSourceElement();
                if (t.getAttributeSetName().equals(name)) {
                    list.add(decl);
                }
            }
        }
    }

    /**
     * Handle an explicitly-declared mode
     *
     * @param decl the declaration of the Mode object
     */
    public void indexMode(ComponentDeclaration decl) {
        XSLMode sourceMode = (XSLMode) decl.getSourceElement();
        StructuredQName modeName = sourceMode.getObjectName();
        if (modeName == null) {
            return; // Not a named mode
        }
        SymbolicName sName = new SymbolicName(StandardNames.XSL_MODE, modeName);
        // see if there is already a named mode with this precedence
        Mode other = getStylesheetPackage().getRuleManager().obtainMode(modeName, false);
        if (other != null && other.getDeclaringComponent().getDeclaringPackage() != getStylesheetPackage()) {
            sourceMode.compileError("Mode " + sName.getComponentName().getDisplayName() +
                                            " conflicts with a public mode declared in package " +
                                            other.getDeclaringComponent().getDeclaringPackage().getPackageName(), "XTSE3050");

        }
    }

    /**
     * Check that it is legitimate to add a given template rule to a given mode
     *
     * @param template the template rule
     * @param mode     the mode
     */

    public boolean checkAcceptableModeForPackage(XSLTemplate template, Mode mode) {
        StylesheetPackage templatePack = template.getPackageData();
        if (mode.getDeclaringComponent() == null) {
            return true;
        }
        StylesheetPackage modePack = mode.getDeclaringComponent().getDeclaringPackage();
        if (templatePack != modePack) {
            NodeInfo parent = template.getParent();
            boolean bad = false;
            if (!(parent instanceof XSLOverride)) {
                bad = true;
            } else {
                NodeInfo grandParent = parent.getParent();
                if (!(grandParent instanceof XSLUsePackage)) {
                    bad = true;
                } else {
                    SymbolicName modeName = mode.getSymbolicName();
                    Component.M usedMode = (Component.M)((XSLUsePackage) grandParent).getUsedPackage().getComponent(modeName);
                    if (usedMode == null) {
                        bad = true;
                    } else if (usedMode.getVisibility() == Visibility.FINAL) {
                        bad = true;
                    }
                }
            }
            if (bad) {
                template.compileError("A template rule cannot be added to a mode declared in a used package " +
                                              "unless the xsl:template declaration appears within an xsl:override child of the appropriate xsl:use-package element",
                                      "XTSE3050");
                return false;
            }
        }
        return true;
    }

    /**
     * Check for schema-awareness.
     * Typed input nodes are recognized if and only if the stylesheet contains an import-schema declaration.
     */

    private void checkForSchemaAwareness() {
        Compilation compilation = getRootElement().getCompilation();
        if (!compilation.isSchemaAware() &&
                getConfiguration().isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XSLT)) {
            for (ComponentDeclaration decl : topLevel) {
                StyleElement node = decl.getSourceElement();
                if (node instanceof XSLImportSchema) {
                    compilation.setSchemaAware(true);
                    return;
                }
            }
        }
    }

    /**
     * Get the class that manages accumulator functions
     *
     * @return the class that manages accumulators. Always null in Saxon-HE
     */

    public AccumulatorRegistry getAccumulatorManager() {
        return accumulatorManager;
    }

    /**
     * Set the class that manages accumulator functions
     *
     * @param accumulatorManager the manager of accumulator functions
     */

    public void setAccumulatorManager(AccumulatorRegistry accumulatorManager) {
        this.accumulatorManager = accumulatorManager;
        stylesheetPackage.setAccumulatorRegistry(accumulatorManager);
    }


    protected void addNamespaceAlias(ComponentDeclaration node) {
        namespaceAliasList.add(node);
        numberOfAliases++;
    }

    /**
     * Get the declared namespace alias for a given namespace URI code if there is one.
     * If there is more than one, we get the last.
     *
     * @param uri The uri used in the stylesheet.
     * @return The namespace binding to be used (prefix and uri): return null
     * if no alias is defined
     */

    protected NamespaceBinding getNamespaceAlias(String uri) {
        return namespaceAliasMap.get(uri);
    }

    /**
     * Determine if a namespace is included in the result-prefix of a namespace-alias
     *
     * @param uri the namespace URI
     * @return true if an xsl:namespace-alias has been defined for this namespace URI
     */

    protected boolean isAliasResultNamespace(String uri) {
        return aliasResultUriSet.contains(uri);
    }

    /**
     * Collect any namespace aliases
     */

    private void collectNamespaceAliases() {
        namespaceAliasMap = new HashMap<>(numberOfAliases);
        aliasResultUriSet = new HashSet<>(numberOfAliases);
        HashSet<String> aliasesAtThisPrecedence = new HashSet<>();
        int currentPrecedence = -1;
        // Note that we are processing the list in reverse stylesheet order,
        // that is, highest precedence first.
        for (int i = 0; i < numberOfAliases; i++) {
            ComponentDeclaration decl = namespaceAliasList.get(i);
            XSLNamespaceAlias xna = (XSLNamespaceAlias) decl.getSourceElement();
            String scode = xna.getStylesheetURI();
            NamespaceBinding resultBinding = xna.getResultNamespaceBinding();
            int prec = decl.getPrecedence();

            // check that there isn't a conflict with another xsl:namespace-alias
            // at the same precedence

            if (currentPrecedence != prec) {
                currentPrecedence = prec;
                aliasesAtThisPrecedence.clear();
                //precedenceBoundary = i;
            }
            if (aliasesAtThisPrecedence.contains(scode)) {
                if (!namespaceAliasMap.get(scode).getURI().equals(resultBinding.getURI())) {
                    xna.compileError("More than one alias is defined for the same namespace", "XTSE0810");
                }
            }
            if (namespaceAliasMap.get(scode) == null) {
                namespaceAliasMap.put(scode, resultBinding);
                aliasResultUriSet.add(resultBinding.getURI());
            }
            aliasesAtThisPrecedence.add(scode);
        }
        namespaceAliasList = null;  // throw it in the garbage
    }

    protected boolean hasNamespaceAliases() {
        return numberOfAliases > 0;
    }

    /**
     * Create an output properties object representing the xsl:output elements in the stylesheet.
     *
     * @param formatQName The name of the output format required. If set to null, gathers
     *                    information for the unnamed output format
     * @return the Properties object containing the details of the specified output format
     * @throws XPathException if a named output format does not exist in
     *                        the stylesheet
     */

    public Properties gatherOutputProperties(/*@Nullable*/ StructuredQName formatQName) throws XPathException {
        boolean found = formatQName == null;
        Configuration config = getConfiguration();
        Properties details = new Properties(config.getDefaultSerializationProperties());
        HashMap<String, Integer> precedences = new HashMap<>(10);
        for (int i = topLevel.size() - 1; i >= 0; i--) {
            ComponentDeclaration decl = topLevel.get(i);
            if (decl.getSourceElement() instanceof XSLOutput) {
                XSLOutput xo = (XSLOutput) decl.getSourceElement();
                if (formatQName == null
                        ? xo.getFormatQName() == null
                        : formatQName.equals(xo.getFormatQName())) {
                    found = true;
                    xo.gatherOutputProperties(details, precedences, decl.getPrecedence());
                }
            }
        }
        if (!found) {
            compileError("Requested output format " + formatQName.getDisplayName() +
                                 " has not been defined", "XTDE1460");
        }
        return details;
    }


    /**
     * Compile the source XSLT stylesheet package
     *
     * @param compilation the compilation episode
     * @throws XPathException if compilation fails for any reason
     */

    protected void compile(Compilation compilation) throws XPathException {

        try {

            Timer timer = compilation.timer;

            //PreparedStylesheet pss = getPreparedStylesheet();
            Configuration config = getConfiguration();

            // If any XQuery functions were imported, fix up all function calls
            // registered against these functions.
            XQueryFunctionLibrary queryFunctions = stylesheetPackage.getXQueryFunctionLibrary();
            Iterator qf = queryFunctions.getFunctionDefinitions();
            while (qf.hasNext()) {
                XQueryFunction f = (XQueryFunction) qf.next();
                f.fixupReferences();
            }

            if (Compilation.TIMING) {
                timer.report("fixup Query functions");
            }

            // Register all modes with the rule manager

            boolean allowImplicit = !getStylesheetPackage().isDeclaredModes();
            for (ComponentDeclaration decl : topLevel) {
                StyleElement snode = decl.getSourceElement();
                if (snode instanceof XSLMode) {
                    getRuleManager().obtainMode(snode.getObjectName(), true);
                }
                if (allowImplicit) {
                    registerImplicitModes(snode, getRuleManager());
                }
            }
            getRuleManager().checkConsistency();

            // Register template rules with the rule manager

            for (ComponentDeclaration decl : topLevel) {
                StyleElement snode = decl.getSourceElement();
                if (snode instanceof XSLTemplate) {
                    ((XSLTemplate) snode).register(decl);
                }
            }

            if (Compilation.TIMING) {
                timer.report("register templates");
            }

            // Adjust the visibility of components based on xsl:expose declarations

            adjustExposedVisibility();

            if (Compilation.TIMING) {
                timer.report("adjust exposed visibility");
            }

            // Call compile method for each top-level object in the stylesheet
            // Note, some declarations (templates) need to be compiled repeatedly if the module
            // is imported repeatedly; others (variables, functions) do not

            for (ComponentDeclaration decl : topLevel) {
                StyleElement snode = decl.getSourceElement();
                if (!snode.isActionCompleted(StyleElement.ACTION_COMPILE)) {
                    snode.setActionCompleted(StyleElement.ACTION_COMPILE);
                    snode.compileDeclaration(compilation, decl);
                }
            }

            if (Compilation.TIMING) {
                timer.report("compile top-level objects (" + topLevel.size() + ")");
            }

            // Call type-check method for each user-defined function in the stylesheet. This is no longer
            // done during the optimize step, to avoid functions being inlined before they are type-checked.

            for (ComponentDeclaration decl : functionIndex.values()) {
                StyleElement node = decl.getSourceElement();
                if (!node.isActionCompleted(StyleElement.ACTION_TYPECHECK)) {
                    node.setActionCompleted(StyleElement.ACTION_TYPECHECK);
                    if (node.getVisibility() != Visibility.ABSTRACT) {
                        ((XSLFunction) node).getCompiledFunction().typeCheck(node.makeExpressionVisitor());
                    }
                }
            }

            if (Compilation.TIMING) {
                timer.report("typeCheck functions (" + functionIndex.size() + ")");
            }

            if (compilation.getErrorCount() > 0) {
                // not much point carrying on
                return;
            }

            // Call optimize() method for each top-level declaration

            optimizeTopLevel();

            if (Compilation.TIMING) {
                timer.report("optimize top level");
            }

            // optimize functions that aren't overridden

            for (ComponentDeclaration decl : functionIndex.values()) {
                StyleElement node = decl.getSourceElement();
                if (!node.isActionCompleted(StyleElement.ACTION_OPTIMIZE)) {
                    node.setActionCompleted(StyleElement.ACTION_OPTIMIZE);
                    ((StylesheetComponent) node).optimize(decl);
                }
            }

            if (Compilation.TIMING) {
                timer.report("optimize functions");
            }

            // Check consistency of decimal formats

            getDecimalFormatManager().checkConsistency();

            if (Compilation.TIMING) {
                timer.report("check decimal formats");
            }

            // Check consistency of modes

            RuleManager ruleManager = getRuleManager();
            //ruleManager.checkConsistency();  // Now done earlier - bug 5118
            ruleManager.computeRankings();
            if (!compilation.isFallbackToNonStreaming()) {
                ruleManager.invertStreamableTemplates();
            }
            if (config.obtainOptimizer().isOptionSet(OptimizerOptions.RULE_SET)) {
                ruleManager.optimizeRules();
            }

            if (Compilation.TIMING) {
                timer.report("build template rule tables");
            }

            // Build a run-time function library. This supports the use of function-available()
            // with a dynamic argument, and extensions such as saxon:evaluate(). The run-time
            // function library differs from the compile-time function library in that both
            // the StylesheetFunctionLibrary's on the library list are replaced by equivalent
            // ExecutableFunctionLibrary's. This is to prevent the retaining of run-time links
            // to the stylesheet document tree.

            ExecutableFunctionLibrary overriding = new ExecutableFunctionLibrary(config);
            ExecutableFunctionLibrary underriding = new ExecutableFunctionLibrary(config);

            for (Component decl : stylesheetPackage.getComponentIndex().values()) {
                Visibility vis = decl.getVisibility();
                if (/*(vis == Visibility.PUBLIC || vis == Visibility.FINAL) &&*/ decl.getActor() instanceof UserFunction) {
                    UserFunction f = (UserFunction) decl.getActor();
                    if (f.isOverrideExtensionFunction()) {
                        overriding.addFunction(f);
                    } else {
                        underriding.addFunction(f);
                    }
                }
            }

            getStylesheetPackage().setFunctionLibraryDetails(null, overriding, underriding);

            if (Compilation.TIMING) {
                timer.report("build runtime function tables");
            }

            // Allocate binding slots to named templates

            for (ComponentDeclaration decl : topLevel) {
                StyleElement inst = decl.getSourceElement();
                if (inst instanceof XSLTemplate) {
                    NamedTemplate proc = ((XSLTemplate) inst).getActor();
                    if (proc != null && proc.getTemplateName() == null) {
                        proc.allocateAllBindingSlots(stylesheetPackage);
                    }
                }
            }

            if (Compilation.TIMING) {
                timer.report("allocate binding slots to named templates");
            }

            // Allocate binding slots to component reference expressions

            HashMap<SymbolicName, Component> componentIndex = stylesheetPackage.getComponentIndex();
            for (Component decl : componentIndex.values()) {
                Actor proc = decl.getActor();
                if (proc != null) {
                    proc.allocateAllBindingSlots(stylesheetPackage);
                }
            }

            if (Compilation.TIMING) {
                timer.report("allocate binding slots to component references");
            }

            // Allocate binding slots in key definitions

            KeyManager keyMan = getKeyManager();
            for (KeyDefinitionSet keySet : keyMan.getAllKeyDefinitionSets()) {
                for (KeyDefinition keyDef : keySet.getKeyDefinitions()) {
                    keyDef.makeDeclaringComponent(Visibility.PRIVATE, getStylesheetPackage());
                    keyDef.allocateAllBindingSlots(stylesheetPackage);
                }
            }

            if (Compilation.TIMING) {
                timer.report("allocate binding slots to key definitions");
            }

            // Allocate binding slots in accumulators

            AccumulatorRegistry accMan = getAccumulatorManager();
            if (accMan != null) {
                for (Accumulator acc : accMan.getAllAccumulators()) {
                    acc.allocateAllBindingSlots(stylesheetPackage);
                }
            }

            if (Compilation.TIMING) {
                timer.report("allocate binding slots to accumulators");
            }

            // Generate byte code where appropriate

            if (compilation.getCompilerInfo().isGenerateByteCode() &&
                    !config.isDeferredByteCode(HostLanguage.XSLT)) {
                if (Compilation.TIMING) {
                    config.getLogger().info("Generating byte code...");
                }

                Optimizer opt = config.obtainOptimizer();
                for (ComponentDeclaration decl : topLevel) {
                    StyleElement inst = decl.getSourceElement();
                    if (inst instanceof StylesheetComponent) {
                        ((StylesheetComponent) inst).generateByteCode(opt);
                    }
                }
            }

            if (Compilation.TIMING) {
                timer.report("inject byte code candidates");
                timer.reportCumulative("total compile time");
            }

        } catch (RuntimeException err) {
            // if syntax errors were reported earlier, then exceptions may occur during this phase
            // due to inconsistency of data structures. We can ignore these exceptions as they
            // will go away when the user corrects the stylesheet
            if (compilation.getErrorCount() == 0) {
                // rethrow the exception
                throw err;
            }
        }
    }

    private void registerImplicitModes(StyleElement element, RuleManager manager) {
        if (element instanceof XSLApplyTemplates || element instanceof XSLTemplate) {
            String modeAtt = element.getAttributeValue("mode");
            if (modeAtt != null) {
                String[] tokens = Whitespace.trim(modeAtt).split("[ \t\n\r]+");
                for (String s : tokens) {
                    if (!s.startsWith("#")) {
                        StructuredQName modeName = element.makeQName(s, null, "mode");
                        SymbolicName sName = new SymbolicName(StandardNames.XSL_MODE, modeName);
                        HashMap<SymbolicName, Component> componentIndex = getStylesheetPackage().getComponentIndex();
                        Component existing = componentIndex.get(sName);
                        if (existing != null && existing.getDeclaringPackage() != getStylesheetPackage()) {
                            if (element instanceof XSLTemplate && !(element.getParent() instanceof XSLOverride)) {
                                element.compileError("A template rule cannot be added to a mode declared in a used package " +
                                                             "unless the xsl:template declaration appears within an xsl:override child of the appropriate xsl:use-package element",
                                                     "XTSE3050");
                            }
                        } else {
                            manager.obtainMode(modeName, true);
                        }
                    }
                }
            }
        }
        NodeInfo child;
        AxisIterator kids = element.iterateAxis(AxisInfo.CHILD);
        while ((child = kids.next()) != null) {
            if (child instanceof StyleElement) {
                registerImplicitModes((StyleElement) child, manager);
            }
        }
    }

    public void optimizeTopLevel() throws XPathException {
        // Call optimize method for each top-level object in the stylesheet
        // But for functions, do it only for those of highest precedence.

        for (ComponentDeclaration decl : topLevel) {
            StyleElement node = decl.getSourceElement();
            if (node instanceof StylesheetComponent && !(node instanceof XSLFunction) &&
                    !node.isActionCompleted(StyleElement.ACTION_OPTIMIZE)) {
                node.setActionCompleted(StyleElement.ACTION_OPTIMIZE);
                ((StylesheetComponent) node).optimize(decl);
            }
            if (node instanceof XSLTemplate) {
                ((XSLTemplate) node).allocatePatternSlotNumbers();
            }
        }
    }


    /**
     * Get an imported schema with a given namespace
     *
     * @param targetNamespace The target namespace of the required schema.
     *                        Supply an empty string for the default namespace
     * @return the required Schema, or null if no such schema has been imported
     */

    protected boolean isImportedSchema(String targetNamespace) {
        return stylesheetPackage.getSchemaNamespaces().contains(targetNamespace);
    }

    protected void addImportedSchema(String targetNamespace) {
        stylesheetPackage.getSchemaNamespaces().add(targetNamespace);
    }

    protected Set<String> getImportedSchemaTable() {
        return stylesheetPackage.getSchemaNamespaces();
    }

    /**
     * Get a character map, identified by the fingerprint of its name.
     * Search backwards through the stylesheet.
     *
     * @param name The character map name being sought
     * @return the identified character map, or null if not found
     */

    public ComponentDeclaration getCharacterMap(StructuredQName name) {
        for (int i = topLevel.size() - 1; i >= 0; i--) {
            ComponentDeclaration decl = topLevel.get(i);
            if (decl.getSourceElement() instanceof XSLCharacterMap) {
                XSLCharacterMap t = (XSLCharacterMap) decl.getSourceElement();
                if (t.getCharacterMapName().equals(name)) {
                    return decl;
                }
            }
        }
        return null;
    }

    /**
     * Adjust visibility of components by applying xsl:expose rules
     */

    public void adjustExposedVisibility() throws XPathException {
        List<XSLExpose> exposeDeclarations = new ArrayList<>(); // xsl:expose declarations in reverse order
        for (ComponentDeclaration decl : topLevel) {
            if (decl.getSourceElement() instanceof XSLExpose) {
                exposeDeclarations.add(0, (XSLExpose) decl.getSourceElement());
            }
        }
        if (exposeDeclarations.isEmpty()) {
            return;
        }

        NamePool pool = getConfiguration().getNamePool();
        Map<SymbolicName, Component> componentIndex = stylesheetPackage.getComponentIndex();
        for (Component component : componentIndex.values()) {
            int fp = component.getComponentKind();
            if (fp == StandardNames.XSL_MODE && ((Mode) component.getActor()).isUnnamedMode()) {
                continue; // bug 5231
            }
            ComponentTest exactNameTest =
                    new ComponentTest(fp,
                                      new NameTest(Type.ELEMENT, new FingerprintedQName(component.getActor().getComponentName(), pool), pool), -1);
            ComponentTest exactFunctionTest = null;
            if (fp == StandardNames.XSL_FUNCTION) {
                Function fn = (Function) component.getActor();
                exactFunctionTest =
                        new ComponentTest(fp,
                                          new NameTest(Type.ELEMENT,
                                                       new FingerprintedQName(fn.getFunctionName(), pool), pool), fn.getArity());
            }
            boolean matched = false;
            for (XSLExpose exposure : exposeDeclarations) {
                Set<ComponentTest> explicitComponentTests = exposure.getExplicitComponentTests();
                if (explicitComponentTests.contains(exactNameTest) ||
                        (exactFunctionTest != null && explicitComponentTests.contains(exactFunctionTest))) {
                    component.setVisibility(exposure.getVisibility(), VisibilityProvenance.EXPOSED);
                    matched = true;
                    break;
                }
            }
            if (!matched && component.getVisibilityProvenance() == VisibilityProvenance.DEFAULTED) {
                // Look for a matching wildcard
                partialWildcardSearch:
                for (XSLExpose exposure : exposeDeclarations) {
                    for (ComponentTest test : exposure.getWildcardComponentTests()) {
                        if (test.isPartialWildcard() && test.matches(component.getActor())) {
                            if (exposure.getVisibility() == Visibility.ABSTRACT && component.getVisibility() != Visibility.ABSTRACT) {
                                XPathException err = new XPathException(
                                        "The non-abstract component " + component.getActor().getSymbolicName() + " cannot be made abstract by means of xsl:expose", "XTSE3025");
                                err.setLocation(exposure);
                                throw err;
                            }
                            component.setVisibility(exposure.getVisibility(), VisibilityProvenance.EXPOSED);
                            matched = true;
                            break partialWildcardSearch;
                        }
                    }
                }
                if (!matched) {
                    anyWildcardSearch:
                    for (XSLExpose exposure : exposeDeclarations) {
                        for (ComponentTest test : exposure.getWildcardComponentTests()) {
                            if (test.matches(component.getActor())) {
                                if (exposure.getVisibility() == Visibility.ABSTRACT && component.getVisibility() != Visibility.ABSTRACT) {
                                    XPathException err = new XPathException(
                                            "The non-abstract component " + component.getActor().getSymbolicName() + " cannot be made abstract by means of xsl:expose", "XTSE3025");
                                    err.setLocation(exposure);
                                    throw err;
                                }
                                component.setVisibility(exposure.getVisibility(), VisibilityProvenance.EXPOSED);
                                break anyWildcardSearch;
                            }
                        }
                    }
                }

            }

        }
    }

    /**
     * Compile time error, specifying an error code
     *
     * @param message   the error message
     * @param errorCode the error code. May be null if not known or not defined
     * @throws XPathException unconditionally
     */

    protected void compileError(String message, String errorCode) throws XPathException {
        XPathException tce = new XPathException(message, errorCode);
        compileError(tce);
    }

    /**
     * Report an error with diagnostic information
     *
     * @param error contains information about the error
     */

    protected void compileError(XPathException error) {
        error.setIsStaticError(true);
        getRootElement().compileError(error);
    }

    protected void fixup() throws XPathException {
        // Perform the fixup actions
        for (Action a : fixupActions) {
            a.doAction();
        }
    }

    protected void complete() throws XPathException {
        stylesheetPackage.complete();
    }

    public SlotManager getSlotManager() {
        return null;
    }


    @Override
    public GlobalVariable getEquivalentVariable(Expression select) {
        return null;  // implemented in Saxon-EE
    }

    @Override
    public void addGlobalVariable(GlobalVariable variable) {
        addGlobalVariable(variable, Visibility.PRIVATE);
    }

    public void addGlobalVariable(GlobalVariable variable, Visibility visibility) {
        Component component = variable.makeDeclaringComponent(visibility, getStylesheetPackage());
        if (variable.getPackageData() == null) {
            variable.setPackageData(stylesheetPackage);
        }
        if (visibility == Visibility.HIDDEN) {
            stylesheetPackage.addHiddenComponent(component);
        } else {
            stylesheetPackage.getComponentIndex().put(
                    new SymbolicName(StandardNames.XSL_VARIABLE, variable.getVariableQName()), component);
        }
    }


}

