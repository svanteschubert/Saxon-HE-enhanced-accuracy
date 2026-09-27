////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.expr.PackageData;
import net.sf.saxon.expr.instruct.GlobalParameterSet;
import net.sf.saxon.lib.ErrorReporter;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.s9api.XmlProcessingError;
import net.sf.saxon.trans.Timer;
import net.sf.saxon.trans.*;
import net.sf.saxon.trans.packages.PackageDetails;
import net.sf.saxon.trans.packages.PackageLibrary;
import net.sf.saxon.trans.packages.UsePack;
import net.sf.saxon.trans.packages.VersionedPackageName;
import net.sf.saxon.tree.util.IndexedStack;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.gnode.NodeKindType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.NestedIntegerValue;

import javax.xml.transform.Source;
import java.util.*;

/**
 * Represents an XSLT compilation episode, compiling a single package.
 */
public class Compilation {

    /**
     * Information about a stylesheet module
     * @param key URI-based unique identifier for the module
     * @param precedence precedence of the module
     */
    private record ModuleInfo(DocumentKey key, NestedIntegerValue precedence) {
    }

    /**
     * Information about a static variable or parameter
     * @param value the value of the variable
     * @param precedence the import precedence of the variable
     * @param isParam true if this is a parameter, falso for a variable.
     */
    private record ValueAndPrecedence(GroundedValue value,
                                      NestedIntegerValue precedence,
                                      boolean isParam) {
    }

    // diagnostic switch to control output of timing information
    public static boolean TIMING = false;
    private final Configuration config;
    private final CompilerInfo compilerInfo;
    private PrincipalStylesheetModule principalStylesheetModule;
    private int errorCount = 0;
    private boolean schemaAware;
    private final QNameParser qNameParser;
    private final Map<StructuredQName, ValueAndPrecedence> staticVariables = new HashMap<>();
    private final Map<DocumentKey, TreeInfo> stylesheetModules = new HashMap<>();
    private final IndexedStack<ModuleInfo> importStack = new IndexedStack<>(); // handles both include and import
    private PackageData packageData;
    private boolean preScan = true;
    private boolean createsSecondaryResultDocuments = false;
    private boolean libraryPackage = false;
    private VersionedPackageName expectedNameAndVersion = null;
    private final List<UsePack> packageDependencies = new ArrayList<>();
    private List<VersionedPackageName> usingPackages = new ArrayList<>();
    private GlobalParameterSet suppliedParameters;
    private boolean fallbackToNonStreaming = false;
    private Set<StructuredQName> referencedModes = new HashSet<>();
    public Timer timer = null;
    private HashSet<ModuleInfo> loadedModules = new HashSet<>();



    /**
     * Create a compilation object ready to perform an XSLT compilation
     *
     * @param config the Saxon Configuration
     * @param info   compilation options
     */

    public Compilation(Configuration config, CompilerInfo info) {
        this.config = config;
        this.compilerInfo = info;
        schemaAware = info.isSchemaAware();
        preScan = info.isJustInTimeCompilation();
        suppliedParameters = compilerInfo.getParameters();
        referencedModes.add(Mode.UNNAMED_MODE_NAME);

        qNameParser = new QNameParser(null)
                .withAcceptEQName(true, info.getXsltVersion())
                .withErrorOnBadSyntax("XTSE0020")
                .withErrorOnUnresolvedPrefix("XTSE0280");

        if (TIMING) {
            timer = new Timer();
        }
    }

    /**
     * Static factory method: Compile an XSLT stylesheet consisting of a single package
     *
     * @param config       the Saxon Configuration
     * @param compilerInfo the compilation options
     * @param source       the source of the root stylesheet module in the package to be compiled. This may
     *                     contain an xsl:package element at its root, or it may be a conventional xsl:stylesheet or xsl:transform,
     *                     or a "simplified stylesheet" rooted at a literal result element
     * @return the PreparedStylesheet representing the result of the compilation
     * @throws XPathException if any errors occur. The errors will have been reported to the ErrorListener
     *                        contained in the CompilerInfo.
     */

    public static PreparedStylesheet compileSingletonPackage(Configuration config, CompilerInfo compilerInfo, Source source) throws XPathException {
        try {
            Compilation compilation = new Compilation(config, compilerInfo);
            return StylesheetModule.loadStylesheet(source, compilation);

        } catch (XPathException err) {
            if (!err.hasBeenReported()) {
                compilerInfo.getErrorReporter().report(new XmlProcessingException(err));
            }
            throw err;
        }
    }

    /**
     * Supply the name/version of the package that invoked this compilation by virtue of an xsl:use-package
     * declaration, together with the users of that package, recursively. The package is not allowed to
     * use the packages in this list, because that would create a cycle of dependencies
     * @param users the packages that directly or indirectly use this package.
     */

    public void setUsingPackages(List<VersionedPackageName> users) {
        this.usingPackages = users;
    }

    public void setPackageData(PackageData pack) {
        this.packageData = pack;
    }

    public void setMinimalPackageData() {
        if (getPackageData() == null) {
            // Create a temporary PackageData for use during use-when processing
            PackageData pd = new PackageData(getConfiguration());
            pd.setHostLanguage(HostLanguage.XSLT, compilerInfo.getXsltVersion());
            pd.setTargetEdition(compilerInfo.getTargetEdition());
            pd.setSchemaAware(schemaAware);
            packageData = pd;
        }
    }

    public void setExpectedNameAndVersion(VersionedPackageName vpn) {
        this.expectedNameAndVersion = vpn;
    }

    /**
     * Method called internally while compiling a package to register that an xsl:use-package declaration has been encountered.
     * @param use details of the package dependency (name and version of the dependee package)
     */

    public void registerPackageDependency(UsePack use) {
        packageDependencies.add(use);
    }

    /**
     * After the first phase of processing, we have gathered information about the xsl:use-package elements in the stylesheet.
     * We now ensure that these dependencies are satisfied and are bound to actual loaded packages, invoking the compiler
     * recursively to load the package if necessary. If there are cyclic references, these must be detected.
     * @param thisPackage the package being checked
     * @throws XPathException if errors are found
     */

    public void satisfyPackageDependencies(XSLPackage thisPackage) throws XPathException {

//        if (expectedNameAndVersion != null && !thisPackage.getNameAndVersion().equalsIgnoringSuffix(expectedNameAndVersion)) {
//            throw new XPathException("Name and version of package in XSLT source [" + thisPackage.getNameAndVersion() +
//            "] do not match name and version in configuration file [" + expectedNameAndVersion + "]");
//        }
        PackageLibrary library = compilerInfo.getPackageLibrary();
        library.getCompilerInfo().setTargetEdition(compilerInfo.getTargetEdition());
        XPathException lastError = null;

        for (UsePack use : packageDependencies) {
            PackageDetails details = library.findPackage(use.packageName, use.ranges);
            if (details == null) {
                throw new XPathException("Cannot find package " + use.packageName + " (version " + use.ranges + ")",
                                         "XTSE3000", use.location);
            }
            if (details.loadedPackage != null) {
                StylesheetPackage used = details.loadedPackage;
                VersionedPackageName existing =
                        new VersionedPackageName(used.getPackageName(), used.getPackageVersion());
                if (usingPackages.contains(existing)) {
                    // Report a cycle of package dependencies
                    StringBuilder buffer = new StringBuilder(1024);
                    for (VersionedPackageName n : usingPackages) {
                        buffer.append(n.packageName);
                        buffer.append(", ");
                    }
                    buffer.append("and ");
                    buffer.append(thisPackage.getName());
                    throw new XPathException("There is a cycle of package dependencies involving " + buffer, "XTSE3005");
                }
            }
            try {
                List<VersionedPackageName> disallowed = new ArrayList<>(usingPackages);
                disallowed.add(details.nameAndVersion);
                library.obtainLoadedPackage(details, disallowed, getCompilerInfo().getPreLoadedSchemata());
            } catch (XPathException err) {
                err.maybeSetErrorCode("XTSE3000");
                if (!err.hasBeenReported()) {
                    reportError(err);
                }
                lastError = err;
            }

        }
        if (lastError != null) {
            throw lastError;
        }
    }

    /**
     * Compile a stylesheet package
     *
     * @param source the XML document containing the top-level stylesheet module in the package,
     *               which may be an xsl:package element, an xsl:stylesheet or xsl:transform element, or a
     *               "simplified stylesheet" rooted at a literal result element
     * @return the StylesheetPackage representing the result of the compilation
     * @throws XPathException if any error occurs while compiling the package
     */

    public PrincipalStylesheetModule compilePackage(Source source) throws XPathException {
        setMinimalPackageData();
        NodeInfo document;
        NodeInfo outermost = null;
        NodeInfo root = null;
        if (source instanceof NodeSource) {
            root = ((NodeSource)source).getNode();
        } else if (source instanceof NodeInfo) {
            root = ((NodeInfo)source);
        }
        if (root != null) {
            if (root.getNodeKind() == Type.DOCUMENT) {
                document = root;
                outermost = (NodeInfo)document.iterateChildAxis(NodeKindType.ELEMENT).next();
            } else if (root.getNodeKind() == Type.ELEMENT) {
                document = root.getRoot();
                outermost = root;
            }
        }

        if (!(outermost instanceof XSLPackage)) {
            document = StylesheetModule.loadStylesheetModule(source, true, this, NestedIntegerValue.TWO);
            outermost = (NodeInfo)document.iterateChildAxis(NodeKindType.ELEMENT).next();
        }
        if (outermost == null) {
            throw new XPathException("No stylesheet element found at " + source.getSystemId(), "XPST0010");
        }

        if (outermost instanceof LiteralResultElement lre) {
            document = LiteralResultElement.makeStylesheet(lre, true);
            outermost = (NodeInfo)document.iterateChildAxis(NodeKindType.ELEMENT).next();
        } else if (compilerInfo.getXsltVersion() >= 40 && outermost instanceof StyleElement se && se.isInstruction()) {
            document = LiteralResultElement.makeStylesheet(se, true);
            outermost = (NodeInfo) document.iterateChildAxis(NodeKindType.ELEMENT).next();
        }

        XSLPackage xslpackage;
        try {
            if (outermost instanceof XSLPackage) {
                xslpackage = (XSLPackage) outermost;
            } else {
                throw new XPathException(
                        "Outermost element must be xsl:package, xsl:stylesheet, or xsl:transform (found "
                                + outermost.getDisplayName() + ")", "XPST0010")
                        .withLocation(outermost);
            }
        } catch (XPathException e) {
            if (!e.hasBeenReported()) {
                getCompilerInfo().getErrorReporter().report(new XmlProcessingException(e));
            }
            throw e;
        }
        if (Compilation.TIMING) {
            timer.report("Built stylesheet documents");
        }
        CompilerInfo info = getCompilerInfo();
        StyleNodeFactory factory = getStyleNodeFactory(true);
        PrincipalStylesheetModule psm = factory.newPrincipalModule(xslpackage);
        StylesheetPackage pack = psm.getStylesheetPackage();
        pack.setLanguageVersion(xslpackage.getVersion());
        pack.setPackageVersion(xslpackage.getPackageVersion());
        pack.setPackageName(xslpackage.getName());
        pack.setSchemaAware(info.isSchemaAware() || isSchemaAware());
        pack.setLanguageVersion(info.getXsltVersion());
        pack.createFunctionLibrary();
        if (compilerInfo.getStubFunctionLibrary() != null) {
            pack.getFunctionLibrary().addFunctionLibrary(compilerInfo.getStubFunctionLibrary());
        }
        psm.getRuleManager().setCompilerInfo(info);
        setPrincipalStylesheetModule(psm);
        packageData = null;

        satisfyPackageDependencies(xslpackage);

        if (TIMING) {
            timer.report("Preparing package");
        }

        try {
            psm.preprocess(this);
        } catch (XPathException e) {
            info.getErrorReporter().report(new XmlProcessingException(e));
            throw e;
        }

        if (getErrorCount() == 0) {
            try {
                psm.fixup();
            } catch (XPathException e) {
                reportError(e);
            }
        }

        if (TIMING) {
            timer.report("Fixup");
        }

        // Compile groups of like-named attribute sets into a single attributeSet object
        if (getErrorCount() == 0) {
            try {
                psm.combineAttributeSets(this);
            } catch (XPathException e) {
                reportError(e);
            }
        }

        if (TIMING) {
            timer.report("Combine attribute sets");
        }
        
        // Compile the stylesheet package
        if (getErrorCount() == 0) {
            try {
                psm.compile(this);
            } catch (XPathException e) {
                reportError(e);
            }
        }

        if (getErrorCount() == 0) {
            try {
                psm.complete();
            } catch (XPathException e) {
                reportError(e);
            }
        }

        if (TIMING) {
            timer.report("Completion");
        }

        psm.getStylesheetPackage().setCreatesSecondaryResultDocuments(createsSecondaryResultDocuments);

        if (isFallbackToNonStreaming()) {
            psm.getStylesheetPackage().setFallbackToNonStreaming();
        }

        if (TIMING) {
            timer.report("Streaming fallback");
        }
        return psm;
    }

    /**
     * Get the Saxon Configuration used by this Compilation
     *
     * @return the Saxon Configuration
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Get the compilation options used by this compilation
     *
     * @return the compilation options
     */

    public CompilerInfo getCompilerInfo() {
        return compilerInfo;
    }

    /**
     * Get information about the package that was compiled in the course of this Compilation
     *
     * @return package information
     */

    public PackageData getPackageData() {
        if (packageData != null) {
            return packageData;
        }
        return principalStylesheetModule == null ? null : principalStylesheetModule.getStylesheetPackage();
    }

    /**
     * Ask whether this compilation is schema-aware. It is schema-aware either if this is explicitly
     * requested in the supplied CompilerInfo, or if this is explicitly requested within the stylesheet
     * source code, for example by the presence of an <code>xsl:import-schema</code> declaration or a
     * <code>validation</code> attribute
     *
     * @return true if the compilation is schema-aware
     */

    public boolean isSchemaAware() {
        return schemaAware;
    }

    /**
     * Say that this compilation is schema-aware. This method is called internally during the course
     * of a compilation if an <code>xsl:import-schema</code> declaration is encountered.
     *
     * @param schemaAware true if the compilation is schema-aware.
     */

    public void setSchemaAware(boolean schemaAware) {
        this.schemaAware = schemaAware;
        getPackageData().setSchemaAware(schemaAware);
    }

    /**
     * Get the StyleNodeFactory used to build the stylesheet tree
     *
     * @param topLevel true if the factory is for the top-level (package) module of a package
     * @return the StyleNodeFactory
     */

    public StyleNodeFactory getStyleNodeFactory(boolean topLevel) {
        StyleNodeFactory factory = getConfiguration().makeStyleNodeFactory(this);
        factory.setTopLevelModule(topLevel);
        return factory;
    }

    private void setPrincipalStylesheetModule(PrincipalStylesheetModule module) {
        this.principalStylesheetModule = module;
    }

    /**
     * Get the (most recent) stylesheet package compiled using this Compilation
     *
     * @return the most recent stylesheet package compiled
     */

    public PrincipalStylesheetModule getPrincipalStylesheetModule() {
        return principalStylesheetModule;
    }

    /**
     * Report a compile time error. This calls the errorListener to output details
     * of the error, and increments an error count.
     *
     * @param err the exception containing details of the error
     */

    public void reportError(XmlProcessingError err) {
        ErrorReporter reporter = compilerInfo.getErrorReporter();
        if (reporter != null) {
            reporter.report(err);
        }
        errorCount++;
        if (err.getTerminationMessage() != null) {
            throw new XmlProcessingAbort(err.getTerminationMessage());
        }
    }

    /**
     * Report a compile time error. This calls the errorListener to output details
     * of the error, and increments an error count.
     *
     * @param err the exception containing details of the error
     */

    public void reportError(XPathException err) {
        err.setHostLanguage(HostLanguage.XSLT);
        ErrorReporter el = compilerInfo.getErrorReporter();
        if (el == null) {
            el = getConfiguration().makeErrorReporter();
        }
        if (!err.hasBeenReported()) {
            errorCount++;
            try {
                el.report(new XmlProcessingException(err));
                err.setHasBeenReported(true);
            } catch (Exception err2) {
                // ignore secondary error
            }
        } else if (errorCount == 0) {
            errorCount++;
        }
    }

    /**
     * Get the number of errors reported so far
     *
     * @return the number of errors reported
     */

    public int getErrorCount() {
        return errorCount;
    }

    /**
     * Report a compile time warning. This calls the errorListener to output details
     * of the warning.
     *
     * @param err an exception holding details of the warning condition to be
     *            reported
     */

    public void reportWarning(XPathException err) {
        err.setHostLanguage(HostLanguage.XSLT);
        ErrorReporter reporter = compilerInfo.getErrorReporter();
        if (reporter == null) {
            reporter = getConfiguration().makeErrorReporter();
        }
        if (reporter != null) {
            XmlProcessingException error = new XmlProcessingException(err);
            error.setWarning(true);
            reporter.report(error);
        }
    }

    public void reportWarning(String message, String errorCode, Location location) {
        XmlProcessingIncident error = new XmlProcessingIncident(message, errorCode, location).asWarning();
        error.setHostLanguage(HostLanguage.XSLT);
        compilerInfo.getErrorReporter().report(error);
    }

    /**
     * Declare a static variable. This is called internally by the stylesheet compiler when a static
     * variable declaration is encountered.
     *
     * @param name       the name of the variable
     * @param value      the value of the variable
     * @param precedence the import precedence in the form of a "decimal" value (e.g. 2.14.6)
     * @param isParam    true if this is an xsl:param rather than an xsl:variable
     * @throws XPathException if, for example, the value of the variable is incompatible with other
     *                        variables having the same name
     */

    public void declareStaticVariable(StructuredQName name, GroundedValue value, NestedIntegerValue precedence, boolean isParam) throws XPathException {
        ValueAndPrecedence vp = staticVariables.get(name);
        if (vp != null) {
            if (vp.precedence.compareTo(precedence) < 0) {
                // new value must be compatible with the old, see spec bug 24478
                if (!valuesAreCompatible(value, vp.value)) {
                    throw new XPathException("Incompatible values assigned for static variable " + name.getDisplayName(), "XTSE3450");
                }
                if (vp.isParam  != isParam) {
                    throw new XPathException("Static variable " + name.getDisplayName() + " cannot be redeclared as a param", "XTSE3450");
                }
            } else {
                return; // ignore the new value
            }
        }
        staticVariables.put(name, new ValueAndPrecedence(value, precedence, isParam));
    }

    /**
     * Test whether two values are the same in the sense of error XTSE3450
     *
     * @param val0 the first value
     * @param val1 the second value
     * @return true if the values are the same: if they are atomic values, they must be "identical";
     * if they are nodes, they must be the same node.
     */

    private boolean valuesAreCompatible(GroundedValue val0, GroundedValue val1) {
        if (val0.getLength() != val1.getLength()) {
            return false;
        }
        if (val0.getLength() == 1) {
            Item i0 = val0.head();
            Item i1 = val1.head();
            if (i0 instanceof AtomicValue) {
                return i1 instanceof AtomicValue && ((AtomicValue) i0).isIdentical((AtomicValue) i1);
            } else if (i0 instanceof NodeInfo) {
                return i1 instanceof NodeInfo && i0.equals(i1);
            } else {
                return i0 == i1;
            }
        } else {
            for (int i = 0; i < val0.getLength(); i++) {
                if (!valuesAreCompatible(val0.itemAt(i), val1.itemAt(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Get the value of a static variable
     *
     * @param name the name of the required variable
     * @return the value of the variable if there is one, or null if the variable is undeclared
     */

    public GroundedValue getStaticVariable(StructuredQName name) {
        ValueAndPrecedence vp = staticVariables.get(name);
        return vp == null ? null : vp.value;
    }

    /**
     * Get the precedence of a static variable
     *
     * @param name the name of the required variable
     * @return the precedence of the variable if there is one, or null if the variable is undeclared
     */

    public NestedIntegerValue getStaticVariablePrecedence(StructuredQName name) {
        ValueAndPrecedence vp = staticVariables.get(name);
        return vp == null ? null : vp.precedence;
    }

    /**
     * Get the map of stylesheet modules. This includes an entry for every stylesheet module
     * in the compilation; the key is the absolute URI, and the value is the corresponding
     * document.
     *
     * @return the map from absolute URIs to (documents containing) stylesheet modules
     */

    public Map<DocumentKey, TreeInfo> getStylesheetModules() {
        return stylesheetModules;
    }

    /**
     * Push details of a new included/imported module onto the module stack
     * @param key represents the normalized URI of the module
     * @param precedence the import precedence of the module
     */

    public void pushModule(DocumentKey key, NestedIntegerValue precedence) {
        importStack.push(new ModuleInfo(key, precedence));
    }

    /**
     * Pop details of an included/imported module off the stack
     */
    public void popModule() {
        importStack.pop();
    }

    /**
     * Determine whether the include/import stack contains a stylesheet module with a given URI,
     * and if so, return the precedence of the topmost such occurrence
     * @param key the document key of the required stylesheet module
     * @return the import precedence of the module if found, or null otherwise
     */
    public NestedIntegerValue stackContainsModule(DocumentKey key) {
        for (int pos = importStack.size()-1; pos>=0; pos--) {
            DocumentKey dKey = importStack.get(pos).key;
            if (dKey != null && dKey.equals(key)) {
                return importStack.get(pos).precedence;
            }
        }
        return null;
    }

    /**
     * Get the QNameParser for parsing QNames in this compilation
     * Note that the namespaceResolver will be unitialized
     * @return the QNameParser
     */

    public QNameParser getQNameParser() {
        return qNameParser;
    }

    /**
     * Ask whether this is the compile-time phase of a just-in-time compilation, during which template
     * rules are not fully processed
     * @return true if just-in-time compilation is in force and this is the static processing phase
     * in which code is not fully processed. Return false if just-in-time compilation is not in force,
     * or if this is the run-time phase during which full static analysis and code generation takes place.
     */

    public boolean isPreScan() {
        return preScan;
    }

    /**
     * Say whether this is the compile-time phase of a just-in-time compilation, during which template
     * rules are not fully processed
     *
     * @param preScan true if just-in-time compilation is in force and this is the static processing phase
     * in which code is not fully processed. Set to false if just-in-time compilation is not in force,
     * or if this is the run-time phase during which full static analysis and code generation takes place.
     */

    public void setPreScan(boolean preScan) {
        this.preScan = preScan;
    }

    /**
     * Ask whether the package has encountered an xsl:result-document instruction
     *
     * @return true if the package contains an xsl:result-document instruction
     */

    public boolean isCreatesSecondaryResultDocuments() {
        return createsSecondaryResultDocuments;
    }

    /**
     * Say whether the compilation has encountered an xsl:result-document instruction
     *
     * @param createsSecondaryResultDocuments true if the package contains an xsl:result-document instruction
     */

    public void setCreatesSecondaryResultDocuments(boolean createsSecondaryResultDocuments) {
        this.createsSecondaryResultDocuments = createsSecondaryResultDocuments;
    }

    /**
     * Ask whether the package being compiled is a library package
     * @return true if this is a library package
     */

    public boolean isLibraryPackage() {
        return libraryPackage;
    }

    /**
     * Say whether the package being compiled is a library package
     * @param libraryPackage if this is a library package
     */

    public void setLibraryPackage(boolean libraryPackage) {
        this.libraryPackage = libraryPackage;
    }

    /**
     * Set the value of a stylesheet parameter. Static (compile-time) parameters must be provided using
     * this method on the XsltCompiler object, prior to stylesheet compilation. Non-static parameters
     * may also be provided using this method if their values will not vary from one transformation
     * to another. No error occurs at this stage if the parameter is unknown, or if the value is incorrect
     * for the parameter. Setting a value for a parameter overwrites any previous value set for the
     * same parameter.
     *
     * @param name the name of the stylesheet parameter
     * @param seq  the value of the stylesheet parameter
     */

    public void setParameter(StructuredQName name, GroundedValue seq) {
        suppliedParameters.put(name, seq);
    }

    /**
     * Get the values of all stylesheet parameters that have been set using the
     * {@link #setParameter(net.sf.saxon.om.StructuredQName, net.sf.saxon.om.GroundedValue)}
     * method.
     *
     * @return the set of all parameters that have been set.
     */

    public GlobalParameterSet getParameters() {
        return suppliedParameters;
    }

    /**
     * Clear the values of all stylesheet parameters that have been set using the
     * {@link #setParameter(net.sf.saxon.om.StructuredQName, net.sf.saxon.om.GroundedValue)}
     * method; and decouple the parameters held by this Compilation from those defined
     * in the originating CompilerInfo
     */

    public void clearParameters() {
        suppliedParameters = new GlobalParameterSet();
    }

    /**
     * Ask whether a non-streamable construct has been found, forcing the entire stylesheet
     * to fall back to unstreamed processing
     * @return true if the stylesheet must fall back to unstreamed processing
     */

    public boolean isFallbackToNonStreaming() {
        return fallbackToNonStreaming;
    }

    /**
     * This method is called when a non-streamable construct is found, and the configuration option
     * {@link net.sf.saxon.lib.Feature#STREAMING_FALLBACK} has been set; the effect is to mark the whole
     * stylesheet as non-streamable
     * @param fallbackToNonStreaming true if a construct has been found that is declared streamable but
     *                               not actually streamable, if fallback processing was requested.
     */

    public void setFallbackToNonStreaming(boolean fallbackToNonStreaming) {
        this.fallbackToNonStreaming = fallbackToNonStreaming;
    }

    /**
     * Get all mode names referenced anywhere in the stylesheet (including the unnamed mode).
     * This list is built up during the static use-when phase of processing. It is used to expand
     * {@code mode="#all"} appearing on {@code xsl:template}.
     * @return the set of all mode names used.
     */
    public Set<StructuredQName> getAllKnownModeNames() {
        return referencedModes;
    }

    public void addLoadedModule(DocumentKey key, NestedIntegerValue precedence) {
        loadedModules.add(new ModuleInfo(key, precedence));
    }

    public boolean existsLoadedModule(DocumentKey key, NestedIntegerValue precedence) {
        return loadedModules.contains(new ModuleInfo(key, precedence));
    }


}
