////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon;

import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.accum.Accumulator;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.functions.ExecutableFunctionLibrary;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.OutputURIResolver;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.style.Compilation;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.*;
import net.sf.saxon.trans.rules.RuleManager;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * This <b>PreparedStylesheet</b> class represents a Stylesheet that has been
 * prepared for execution (or "compiled").
 *
 * <p>Note that the PreparedStylesheet object does not contain a reference to the source stylesheet
 * tree (rooted at an XSLStyleSheet object). This allows the source tree to be garbage-collected
 * when it is no longer required.</p>
 *
 * <p>The PreparedStylesheet in XSLT 3.0 represents the set of all packages making up an executable
 * stylesheet.</p>
 */

public class PreparedStylesheet extends Executable {

    private HashMap<URI, PreparedStylesheet> nextStylesheetCache;
    // cache for stylesheets named as "saxon:next-in-chain"

    // definitions of template rules (XSLT only)
    private RuleManager ruleManager;

    // index of named templates.
    private HashMap<StructuredQName, NamedTemplate> namedTemplateTable;

    // Table of components declared in this package or imported from used packages. Key is the symbolic identifier of the
    // component; value is the component itself. Hidden components are not included in this table because their names
    // need not be unique, and because they are not available for reference by name.
    private Map<SymbolicName, Component> componentIndex;

    private StructuredQName defaultInitialTemplate;
    private StructuredQName defaultInitialMode;
    private String messageReceiverClassName;
    private OutputURIResolver outputURIResolver;
    private GlobalParameterSet compileTimeParams;


    /**
     * Constructor - deliberately protected
     *
     * @param compilation Compilation options
     */

    public PreparedStylesheet(Compilation compilation) {
        super(compilation.getConfiguration());
        CompilerInfo compilerInfo = compilation.getCompilerInfo();
        setHostLanguage(HostLanguage.XSLT);
        if (compilerInfo.isSchemaAware()) {
            int localLic = compilation.getPackageData().getLocalLicenseId();
            getConfiguration().checkLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XSLT, "schema-aware XSLT", localLic);
            schemaAware = true;
        }
        defaultInitialMode = compilerInfo.getDefaultInitialMode();
        defaultInitialTemplate = compilerInfo.getDefaultInitialTemplate();
        messageReceiverClassName = compilerInfo.getMessageReceiverClassName();
        outputURIResolver = compilerInfo.getOutputURIResolver();
        compileTimeParams = compilation.getParameters();
    }


    /**
     * Make a Controller from this stylesheet object.
     *
     * @return the new Controller
     * @see net.sf.saxon.Controller
     */

    public XsltController newController() {
        Configuration config = getConfiguration();
        XsltController c = new XsltController(config, this);
        c.setMessageReceiverClassName(messageReceiverClassName);
        c.setOutputURIResolver(outputURIResolver);
        if (defaultInitialMode != null) {
            try {
                c.setInitialMode(defaultInitialMode);
            } catch (XPathException e) {
                // ignore the error if the default initial mode is not defined
            }
        }
        return c;
    }

    /**
     * Get the parameters that were set at compile time. These will generally be static parameters,
     * but it is also permitted to set non-static parameters at compile time.
     *
     * @return the parameters that were set prior to XSLT compilation
     */

    public GlobalParameterSet getCompileTimeParams() {
        return compileTimeParams;
    }

    /**
     * Check that all required parameters have been supplied. Also checks that the supplied
     * parameters dynamically do not conflict with parameters supplied statically. Used in XSLT only.
     *
     * @param params the set of parameters that have been supplied dynamically to the transformer
     *               (null represents an empty set).
     * @throws XPathException if there is a required parameter for which no value has been supplied
     */

    @Override
    public void checkSuppliedParameters(GlobalParameterSet params) throws XPathException {
        for (Map.Entry<StructuredQName, GlobalParam> entry : getGlobalParameters().entrySet()) {
            if (entry.getValue().isRequiredParam()) {
                StructuredQName req = entry.getKey();
                if (getCompileTimeParams().get(req) == null && (params == null || params.get(req) == null)) {
                    XPathException err = new XPathException("No value supplied for required parameter " +
                                                                    req.getDisplayName());
                    err.setErrorCode(getHostLanguage() == HostLanguage.XQUERY ? "XPDY0002" : "XTDE0050");
                    throw err;
                }
            }
        }
        for (StructuredQName name : params.getKeys()) {
            GlobalParam decl = getGlobalParameter(name);
            if (decl != null && decl.isStatic()) {
                throw new XPathException("Parameter $" + name.getDisplayName() +
                                                 " cannot be supplied dynamically because it is declared as static");
            }
            if (compileTimeParams.containsKey(name)) {
                throw new XPathException("Parameter $" + name.getDisplayName() +
                                                 " cannot be supplied dynamically because a value was already supplied at compile time");
            }
        }
        for (StructuredQName name : compileTimeParams.getKeys()) {
            params.put(name, compileTimeParams.get(name));
        }
    }

    @Override
    public StylesheetPackage getTopLevelPackage() {
        return (StylesheetPackage)super.getTopLevelPackage();
    }

    /**
     * Set the RuleManager that handles template rules
     *
     * @param rm the RuleManager containing details of all the template rules
     */

    public void setRuleManager(RuleManager rm) {
        ruleManager = rm;
    }

    /**
     * Get the RuleManager which handles template rules
     *
     * @return the RuleManager registered with setRuleManager
     */

    public RuleManager getRuleManager() {
        return ruleManager;
    }


    /**
     * Register the named template with a given name
     *
     * @param templateName the name of a named XSLT template
     * @param template     the template
     */

    public void putNamedTemplate(StructuredQName templateName, NamedTemplate template) {
        if (namedTemplateTable == null) {
            namedTemplateTable = new HashMap<>(32);
        }
        namedTemplateTable.put(templateName, template);
    }

    /**
     * Get the default initial template name
     * @return the default initial template name
     */

    public StructuredQName getDefaultInitialTemplateName() {
        return defaultInitialTemplate;
    }

    /**
     * Register the index of components
     *
     * @param index the component index
     */

    public void setComponentIndex(Map<SymbolicName, Component> index) {
        componentIndex = index;
    }

    public Component getComponent(SymbolicName name) {
        return componentIndex.get(name);
    }

    /**
     * Ask whether a mode is eligible for invoking from outside the stylesheet
     *
     * @param component the component
     * @return true if the component can be referenced from the calling application
     */

    public boolean isEligibleInitialMode(Component.M component) {
        if (component == null) {
            return false;
        }
        // Rules 1 and 4
        if (component.getVisibility() == Visibility.PUBLIC || component.getVisibility() == Visibility.FINAL) {
            return true;
        }
        // Rule 2
        if (component.getActor().isUnnamedMode()) {
            return true;
        }
        // Rule 3
        StylesheetPackage top = getTopLevelPackage();
        if (component.getActor().getModeName().equals(top.getDefaultMode())) {
            return true;
        }
        // Rule 5 (but see also bug 30405)
        if (!top.isDeclaredModes() && !component.getActor().isEmpty() &&
                (component.getVisibilityProvenance() == VisibilityProvenance.DEFAULTED || component.getVisibility() != Visibility.PRIVATE)) {
            return true;
        }
        return false;
    }


    /**
     * Explain the expression tree for named templates in a stylesheet
     *
     * @param presenter destination for the explanatory output
     * @throws XPathException if writing to the output fails
     */

    public void explainNamedTemplates(ExpressionPresenter presenter) throws XPathException {
        presenter.startElement("namedTemplates");
        if (namedTemplateTable != null) {
            for (NamedTemplate t : namedTemplateTable.values()) {
                presenter.startElement("template");
                presenter.emitAttribute("name", t.getTemplateName().getDisplayName());
                presenter.emitAttribute("line", t.getLineNumber() + "");
                presenter.emitAttribute("module", t.getSystemId());
                if (t.getBody() != null) {
                    t.getBody().export(presenter);
                }
                presenter.endElement();
            }
        }
        presenter.endElement();
    }

    /**
     * Get the properties for xsl:output, including character maps.  The object returned will
     * be a clone of the internal values, and thus it can be mutated
     * without affecting the value held in this {@code PreparedStylesheet}.
     * <p>This method gets the output properties for the unnamed output
     * format in the stylesheet.</p>
     *
     * @return An OutputProperties object reflecting the output properties defined
     * for the default (unnamed) output format in the stylesheet. It may
     * be mutated and supplied to the setOutputProperties() method of the
     * Transformer, without affecting other transformations that use the
     * same stylesheet.
     * @see javax.xml.transform.Transformer#setOutputProperties
     */

    public SerializationProperties getDeclaredSerializationProperties() {
        SerializationProperties details = getPrimarySerializationProperties();
        return new SerializationProperties(new Properties(details.getProperties()), getCharacterMapIndex());
    }

    /**
     * Get a "next in chain" stylesheet. This method is intended for internal use.
     *
     * @param href    the relative URI of the next-in-chain stylesheet
     * @param baseURI the baseURI against which this relativeURI is to be resolved
     * @return the cached stylesheet if present in the cache, or null if not
     */

    /*@Nullable*/
    public PreparedStylesheet getCachedStylesheet(String href, String baseURI) {
        URI abs = null;
        try {
            abs = ResolveURI.makeAbsolute(href, baseURI);
        } catch (URISyntaxException err) {
            //
        }
        PreparedStylesheet result = null;
        if (abs != null && nextStylesheetCache != null) {
            result = nextStylesheetCache.get(abs);
        }
        return result;
    }

    /**
     * Save a "next in chain" stylesheet in compiled form, so that it can be reused repeatedly.
     * This method is intended for internal use.
     *
     * @param href    the relative URI of the stylesheet
     * @param baseURI the base URI against which the relative URI is resolved
     * @param pss     the prepared stylesheet object to be cached
     */

    public void putCachedStylesheet(String href, String baseURI, PreparedStylesheet pss) {
        URI abs = null;
        try {
            abs = ResolveURI.makeAbsolute(href, baseURI);
        } catch (URISyntaxException err) {
            //
        }
        if (abs != null) {
            if (nextStylesheetCache == null) {
                nextStylesheetCache = new HashMap<>(4);
            }
            nextStylesheetCache.put(abs, pss);
        }
    }

    /**
     * Produce an XML representation of the compiled and optimized stylesheet,
     * as a human-readable entity
     *
     * @param presenter defines the destination and format of the output
     * @throws XPathException if output fails
     */

    public void explain(ExpressionPresenter presenter) throws XPathException {
        presenter.startElement("stylesheet");
        presenter.namespace("fn", NamespaceConstant.FN);
        presenter.namespace("xs", NamespaceConstant.SCHEMA);
        explainGlobalVariables(presenter);
        ruleManager.explainTemplateRules(presenter);
        explainNamedTemplates(presenter);
        presenter.startElement("accumulators");
        for (Accumulator acc : getTopLevelPackage().getAccumulatorRegistry().getAllAccumulators()) {
            acc.export(presenter);
        }
        presenter.endElement();
        FunctionLibraryList libList = getFunctionLibrary();
        List<FunctionLibrary> libraryList = libList.getLibraryList();
        presenter.startElement("functions");
        for (FunctionLibrary lib : libraryList) {
            if (lib instanceof ExecutableFunctionLibrary) {
                for (Iterator f = ((ExecutableFunctionLibrary) lib).iterateFunctions(); f.hasNext(); ) {
                    UserFunction func = (UserFunction) f.next();
                    func.export(presenter);
                }
            }
        }
        presenter.endElement();
        presenter.endElement();
        presenter.close();
    }


}

