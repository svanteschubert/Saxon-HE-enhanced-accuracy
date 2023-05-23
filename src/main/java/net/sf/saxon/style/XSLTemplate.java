////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.ErrorExpression;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.instruct.NamedTemplate;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.instruct.TemplateRule;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.trans.*;
import net.sf.saxon.trans.rules.Rule;
import net.sf.saxon.trans.rules.RuleManager;
import net.sf.saxon.type.Affinity;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.BigDecimalValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

import java.util.*;

/**
 * An xsl:template element in the style sheet.
 */

public final class XSLTemplate extends StyleElement implements StylesheetComponent {

    private String matchAtt = null;
    private String modeAtt = null;
    private String nameAtt = null;
    private String priorityAtt = null;
    private String asAtt = null;
    private String visibilityAtt = null;

    private StructuredQName[] modeNames;
    private String diagnosticId;
    private Pattern match;
    private boolean prioritySpecified;
    private double priority;
    private SlotManager stackFrameMap;
    // A compiled named template exists if the template has a name
    private NamedTemplate compiledNamedTemplate;
    // A set of compiled template rules exists if the template has a match pattern: one TemplateRule for each mode
    private Map<StructuredQName, TemplateRule> compiledTemplateRules = new HashMap<>();
    private SequenceType requiredType = SequenceType.ANY_SEQUENCE;
    private boolean declaresRequiredType = false;
    private boolean hasRequiredParams = false;
    private boolean isTailRecursive = false;
    private Visibility visibility = Visibility.PRIVATE;
    private ItemType requiredContextItemType = AnyItemType.getInstance();
    private boolean mayOmitContextItem = true;
    //private boolean maySupplyContextItem = true;
    private boolean absentFocus = false;
    private boolean jitCompilationDone = false;
    private boolean explaining;
    //private Expression body;

    /**
     * Get the corresponding NamedTemplate object that results from the compilation of this
     * StylesheetComponent
     */
    @Override
    public NamedTemplate getActor() {
        return compiledNamedTemplate;
    }

//    public Expression getBody() {
//        return body;
//    }

    @Override
    public void setCompilation(Compilation compilation) {
        super.setCompilation(compilation);
        //compiledNamedTemplate.setPackageData(compilation.getPackageData());
    }

    /**
     * Ask whether this node is a declaration, that is, a permitted child of xsl:stylesheet
     * (including xsl:include and xsl:import).
     *
     * @return true for this element
     */

    @Override
    public boolean isDeclaration() {
        return true;
    }

    /**
     * Ask whether the compilation of the template should be deferred
     *
     * @param compilation the compilation
     * @return true if compilation should be deferred
     */

    public boolean isDeferredCompilation(Compilation compilation) {
        return compilation.isPreScan() && getTemplateName() == null && !compilation.isLibraryPackage();
    }

    /**
     * Determine whether this type of element is allowed to contain a template-body
     *
     * @return true: yes, it may contain a template-body
     */

    @Override
    public boolean mayContainSequenceConstructor() {
        return true;
    }

    @Override
    protected boolean mayContainParam() {
        return true;
    }

    @Override
    protected boolean isWithinDeclaredStreamableConstruct() {
        try {
            for (Mode m : getApplicableModes()) {
                if (m.isDeclaredStreamable()) {
                    return true;
                }
            }
        } catch (XPathException e) {
            return false;
        }
        return false;
    }

    /**
     * Set the required context item type. Used when there is an xsl:context-item child element
     *
     * @param type         the required context item type
     * @param mayBeOmitted true if the context item may be absent
     * @param absentFocus  true if use=absent is specified
     */

    public void setContextItemRequirements(ItemType type, boolean mayBeOmitted, boolean absentFocus) {
        requiredContextItemType = type;
        mayOmitContextItem = mayBeOmitted;
        this.absentFocus = absentFocus;
    }

    /**
     * Specify that xsl:param and xsl:context-item are permitted children
     */

    @Override
    protected boolean isPermittedChild(StyleElement child) {
        return child instanceof XSLLocalParam || child.getFingerprint() == StandardNames.XSL_CONTEXT_ITEM;
    }

    /**
     * Return the name of this template. Note that this may
     * be called before prepareAttributes has been called.
     *
     * @return the name of the template as a Structured QName.
     */

    /*@Nullable*/
    public StructuredQName getTemplateName() {

        if (getObjectName() == null) {
            // allow for forwards references
            String nameAtt = getAttributeValue("", "name");
            if (nameAtt != null) {
                setObjectName(makeQName(nameAtt, null, "name"));
            }
        }
        return getObjectName();
    }

    @Override
    public SymbolicName getSymbolicName() {
        if (getTemplateName() == null) {
            return null;
        } else {
            return new SymbolicName(StandardNames.XSL_TEMPLATE, getTemplateName());
        }
    }

    public ItemType getRequiredContextItemType() {
        return requiredContextItemType;
    }

    public boolean isMayOmitContextItem() {
        return mayOmitContextItem;
    }


    @Override
    public void checkCompatibility(Component component) {
        NamedTemplate other = (NamedTemplate) component.getActor();
        if (!getSymbolicName().equals(other.getSymbolicName())) {
            throw new IllegalArgumentException();
        }

        SequenceType req = requiredType == null ? SequenceType.ANY_SEQUENCE : requiredType;
        if (!req.equals(other.getRequiredType())) {
            compileError("The overriding template has a different required type from the overridden template", "XTSE3070");
            return;
        }

        if (!requiredContextItemType.equals(other.getRequiredContextItemType()) ||
                mayOmitContextItem != other.isMayOmitContextItem() ||
                absentFocus != other.isAbsentFocus()) {
            compileError("The required context item for the overriding template differs from that of the overridden template", "XTSE3070");
            return;
        }

        List<NamedTemplate.LocalParamInfo> otherParams = other.getLocalParamDetails();
        Set<StructuredQName> overriddenParams = new HashSet<>();
        for (NamedTemplate.LocalParamInfo lp0 : otherParams) {
            XSLLocalParam lp1 = getParam(lp0.name);
            if (lp1 == null) {
                if (!lp0.isTunnel) {
                    compileError("The overridden template declares a parameter " +
                                         lp0.name.getDisplayName() + " which is not declared in the overriding template", "XTSE3070");
                }
                return;
            }
            if (!lp1.getRequiredType().equals(lp0.requiredType)) {
                lp1.compileError("The parameter " +
                                         lp0.name.getDisplayName() + " has a different required type in the overridden template", "XTSE3070");
                return;
            }
            if (lp1.isRequiredParam() != lp0.isRequired && !lp0.isTunnel) {
                lp1.compileError("The parameter " +
                                         lp0.name.getDisplayName() + " is " +
                                         (lp1.isRequiredParam() ? "required" : "optional") +
                                         " in the overriding template, but " +
                                         (lp0.isRequired ? "required" : "optional") +
                                         " in the overridden template", "XTSE3070");
                return;
            }
            if (lp1.isTunnelParam() != lp0.isTunnel) {
                lp1.compileError("The parameter " +
                                         lp0.name.getDisplayName() + " is a " +
                                         (lp1.isTunnelParam() ? "tunnel" : "non-tunnel") +
                                         " parameter in the overriding template, but " +
                                         (lp0.isTunnel ? "tunnel" : "non-tunnel") +
                                         " parameter in the overridden template", "XTSE3070");
                return;
            }
            overriddenParams.add(lp0.name);
        }

        for (NodeInfo param : children(XSLLocalParam.class::isInstance)) {
            if (!overriddenParams.contains(((XSLLocalParam) param).getObjectName()) &&
                    ((XSLLocalParam) param).isRequiredParam()) {
                ((XSLLocalParam) param).compileError(
                        "An overriding template cannot introduce a required parameter that is not declared in the overridden template", "XTSE3070");
            }
        }

    }

    public XSLLocalParam getParam(StructuredQName name) {
        for (NodeInfo param : children(XSLLocalParam.class::isInstance)) {
            if (name.equals(((XSLLocalParam) param).getObjectName())) {
                return (XSLLocalParam) param;
            }
        }
        return null;
    }


    @Override
    public void prepareAttributes() {

        AttributeMap atts = attributes();
        String extraAsAtt = null;

        for (AttributeInfo att : atts) {
            NodeName name = att.getNodeName();
            String f = name.getDisplayName();
            if (f.equals("mode")) {
                modeAtt = Whitespace.trim(att.getValue());
            } else if (f.equals("name")) {
                nameAtt = Whitespace.trim(att.getValue());
            } else if (f.equals("match")) {
                matchAtt = att.getValue();
            } else if (f.equals("priority")) {
                priorityAtt = Whitespace.trim(att.getValue());
            } else if (f.equals("as")) {
                asAtt = att.getValue();
            } else if (f.equals("visibility")) {
                visibilityAtt = Whitespace.trim(att.getValue());
            } else if (name.hasURI(NamespaceConstant.SAXON)) {
                isExtensionAttributeAllowed(name.getDisplayName());
                if (name.getLocalPart().equals("as")) {
                    extraAsAtt = att.getValue();
                } else if (name.getLocalPart().equals("explain")) {
                    explaining = isYes(Whitespace.trim(att.getValue()));
                }
            } else {
                checkUnknownAttribute(name);
            }
        }
        try {
            if (modeAtt == null) {
                if (matchAtt != null) {
                    // XSLT 3.0 allows the default mode to be specified on any element
                    StructuredQName defaultMode = getDefaultMode();
                    if (defaultMode == null) {
                        defaultMode = Mode.UNNAMED_MODE_NAME;
                    }
                    modeNames = new StructuredQName[1];
                    modeNames[0] = defaultMode;
                }
            } else {
                if (matchAtt == null) {
                    compileError("The mode attribute must be absent if the match attribute is absent", "XTSE0500");
                }
                getModeNames();
            }
        } catch (XPathException err) {
            err.maybeSetErrorCode("XTSE0280");
            if (err.getErrorCodeLocalPart().equals("XTSE0020")) {
                err.setErrorCode("XTSE0550");
            }
            err.setIsStaticError(true);
            compileError(err);
        }

        if (nameAtt != null) {
            if (getObjectName() == null) {
                StructuredQName qName = makeQName(nameAtt, "XTSE0280", "name");
                setObjectName(qName);
            }
            if (compiledNamedTemplate != null) {
                compiledNamedTemplate.setTemplateName(getObjectName());
            }
            diagnosticId = nameAtt;
        }

        prioritySpecified = priorityAtt != null;
        if (prioritySpecified) {
            if (matchAtt == null) {
                compileError("The priority attribute must be absent if the match attribute is absent", "XTSE0500");
            }
            try {
                // it's got to be a valid decimal, but we want it as a double, so parse it twice
                if (!BigDecimalValue.castableAsDecimal(priorityAtt)) {
                    compileError("Invalid numeric value for priority (" + priority + ')', "XTSE0530");
                }
                priority = Double.parseDouble(priorityAtt);
            } catch (NumberFormatException err) {
                // shouldn't happen
                compileError("Invalid numeric value for priority (" + priority + ')', "XTSE0530");
            }
        }

        if (matchAtt != null) {
            match = makePattern(matchAtt, "match");
            if (diagnosticId == null) {
                diagnosticId = "match=\"" + matchAtt + '\"';
                if (modeAtt != null) {
                    diagnosticId += " mode=\"" + modeAtt + '\"';
                }
            }
        }

        if (match == null && nameAtt == null) {
            compileError("xsl:template must have a name or match attribute (or both)", "XTSE0500");
        }
        if (asAtt != null) {
            try {
                requiredType = makeSequenceType(asAtt);
                declaresRequiredType = true;
            } catch (XPathException e) {
                compileErrorInAttribute(e.getMessage(), e.getErrorCodeLocalPart(), "as");
            }
        }

        if (extraAsAtt != null) {
            SequenceType extraResultType;
            declaresRequiredType = true;
            try {
                extraResultType = makeExtendedSequenceType(extraAsAtt);
            } catch (XPathException e) {
                compileErrorInAttribute(e.getMessage(), e.getErrorCodeLocalPart(), "saxon:as");
                extraResultType = requiredType; // error recovery
            }
            if (asAtt != null) {
                Affinity rel = getConfiguration().getTypeHierarchy().sequenceTypeRelationship(extraResultType, requiredType);
                if (rel == Affinity.SAME_TYPE || rel == Affinity.SUBSUMED_BY) {
                    requiredType = extraResultType;
                } else {
                    compileErrorInAttribute("When both are present, @saxon:as must be a subtype of @as", "SXER7TBA", "saxon:as");
                }
            } else {
                requiredType = extraResultType;
            }
        }

        if (visibilityAtt != null) {
            visibility = interpretVisibilityValue(visibilityAtt, "");
            if (nameAtt == null) {
                compileError("xsl:template/@visibility can be specified only if the template has a @name attribute", "XTSE0020");
            } else {
                compiledNamedTemplate.setDeclaredVisibility(getVisibility());
            }
        }
    }

    @Override
    public void processAllAttributes() throws XPathException {
//        String mode = getAttributeValue("mode");
//        mode = mode == null ? "" : Whitespace.trim(mode);
        if (!isDeferredCompilation(getCompilation())) {
            super.processAllAttributes();      //TODO - sort out the duplicated code. This repeats the code below
        } else {
            processDefaultCollationAttribute();
            processDefaultMode();
            staticContext = new ExpressionContext(this, null);
            processAttributes();

        }
    }

    /**
     * Return the list of mode names to which this template rule is applicable.
     *
     * @return the list of mode names. If the mode attribute is absent, #default is assumed.
     * If #default is present explicitly or implicitly, it is replaced by the default mode, taken
     * from the in-scope default-modes attribute, which defaults to #unnamed. The unnamed mode
     * is represented by {@link Mode#UNNAMED_MODE_NAME}. The token #all translates to
     * {@link Mode#OMNI_MODE}.
     * @throws XPathException if the attribute is invalid.
     */

    public StructuredQName[] getModeNames() throws XPathException {
        if (modeNames == null) {
            // modeAtt is a space-separated list of mode names, or "#default", or "#all"
            if (modeAtt == null) {
                modeAtt = getAttributeValue("mode");
                if (modeAtt == null) {
                    modeAtt = "#default";
                }
            }

            boolean allModes = false;
            String[] tokens = Whitespace.trim(modeAtt).split("[ \t\n\r]+");
            int count = tokens.length;

            modeNames = new StructuredQName[count];
            count = 0;
            for (String s : tokens) {
                StructuredQName mname;
                if ("#default".equals(s)) {
                    mname = getDefaultMode();
                    if (mname == null) {
                        mname = Mode.UNNAMED_MODE_NAME;
                    }
                } else if ("#unnamed".equals(s)) {
                    mname = Mode.UNNAMED_MODE_NAME;
                } else if ("#all".equals(s)) {
                    allModes = true;
                    mname = Mode.OMNI_MODE;
                } else {
                    mname = makeQName(s, "XTSE0550", "mode");
                }
                for (int e = 0; e < count; e++) {
                    if (modeNames[e].equals(mname)) {
                        compileError("In the list of modes, the value " + s + " is duplicated", "XTSE0550");
                    }
                }
                modeNames[count++] = mname;
            }
            if (allModes && (count > 1)) {
                compileError("mode='#all' cannot be combined with other modes", "XTSE0550");
            }
        }
        return modeNames;
    }

    /**
     * Get the modes to which this template rule applies
     *
     * @return the set of modes to which it applies
     * @throws XPathException should not happen
     */

    public Set<Mode> getApplicableModes() throws XPathException {
        StructuredQName[] names = getModeNames();
        Set<Mode> modes = new HashSet<>(names.length);
        RuleManager mgr = getPrincipalStylesheetModule().getRuleManager();
        for (StructuredQName name : names) {
            if (name.equals(Mode.OMNI_MODE)) {
                modes.add(mgr.getUnnamedMode());
                modes.addAll(mgr.getAllNamedModes());
            } else {
                Mode mode = mgr.obtainMode(name, false);
                if (mode != null) {
                    modes.add(mode);
                }
            }
        }
        return modes;
    }

    /**
     * Ask whether this is a template rule with mode="#all
     */

    public boolean isOmniMode() throws XPathException {
        for (StructuredQName name : getModeNames()) {
            if (name.equals(Mode.OMNI_MODE)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        stackFrameMap = getConfiguration().makeSlotManager();
        checkTopLevel("XTSE0010", true);

        // the check for duplicates is now done in the buildIndexes() method of XSLStylesheet
        if (match != null) {
            match = typeCheck("match", match);
            if (match.getItemType() instanceof ErrorType) {
                issueWarning(new XPathException("Pattern will never match anything", SaxonErrorCode.SXWN9015, this));
            }
            if (getPrincipalStylesheetModule().isDeclaredModes()) {
                RuleManager manager = getPrincipalStylesheetModule().getRuleManager();
                if (modeNames != null) {
                    for (StructuredQName name : modeNames) {
                        if (name.equals(Mode.UNNAMED_MODE_NAME) && !manager.isUnnamedModeExplicit()) {
                            compileError("The unnamed mode has not been declared in an xsl:mode declaration", "XTSE3085");
                        }
                        if (manager.obtainMode(name, false) == null) {
                            compileError("Mode name " + name.getDisplayName() + " has not been declared in an xsl:mode declaration", "XTSE3085");
                        }
                    }
                } else {
                    if (!manager.isUnnamedModeExplicit()) {
                        compileError("The unnamed mode has not been declared in an xsl:mode declaration", "XTSE3085");
                    }
                }
            }
            if (visibility == Visibility.ABSTRACT) {
                compileError("An abstract template must have no match attribute");
            }
        }

        // See if there are any required parameters.
        boolean hasContent = false;
        for (NodeInfo param : children(StyleElement.class::isInstance)) {
            if (param.getFingerprint() == StandardNames.XSL_CONTEXT_ITEM) {
                // no action
            } else if (param instanceof XSLLocalParam) {
                if (((XSLLocalParam) param).isRequiredParam()) {
                    hasRequiredParams = true;
                }
            } else {
                hasContent = true;
            }
        }

        if (visibility == Visibility.ABSTRACT && hasContent) {
            compileError("A template with visibility='abstract' must have no body");
        }

    }

    @Override
    public void validateSubtree(ComponentDeclaration decl, boolean excludeStylesheet) throws XPathException {
        if (!isDeferredCompilation(getCompilation())) {
            super.validateSubtree(decl, excludeStylesheet);
        } else {
            try {
                validate(decl);
            } catch (XPathException err) {
                compileError(err);
            }
        }
    }

    @Override
    public void index(ComponentDeclaration decl, PrincipalStylesheetModule top) throws XPathException {
        if (getTemplateName() != null) {
            if (compiledNamedTemplate == null) {
                compiledNamedTemplate = new NamedTemplate(getTemplateName());
            }
            top.indexNamedTemplate(decl);
        }

    }

    /**
     * Mark tail-recursive calls on templates and functions.
     */

    @Override
    public boolean markTailCalls() {
        StyleElement last = getLastChildInstruction();
        return last != null && last.markTailCalls();
    }

    /**
     * Compile: creates the executable form of the template
     */

    @Override
    public void compileDeclaration(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        if (isDeferredCompilation(compilation)) {
            createSkeletonTemplate(compilation, decl);
            //System.err.println("Deferred - " + ++lazy);
            return;
        }
        if (compilation.getCompilerInfo().getOptimizerOptions().isSet(OptimizerOptions.TAIL_CALLS)) {
            isTailRecursive = markTailCalls();
        }
        Expression body = compileSequenceConstructor(compilation, decl, true);
        body.restoreParentPointers();
        RetainedStaticContext rsc = makeRetainedStaticContext();
        if (body.getRetainedStaticContext() == null) {
            body.setRetainedStaticContext(rsc); // bug 2608
        }
        if (match != null && compilation.getConfiguration().getBooleanProperty(Feature.STRICT_STREAMABILITY) &&
                isWithinDeclaredStreamableConstruct()) {
            checkStrictStreamability(body);
        }
        if (getTemplateName() != null) {
            compileNamedTemplate(compilation, body, decl);
        }
        if (match != null) {
            //System.err.println("Rules compiled - " + ++eager);
            compileTemplateRule(compilation, body, decl);
        }
    }

    private void checkStrictStreamability(Expression body) throws XPathException {
        getConfiguration().checkStrictStreamability(this, body);
    }

    private void compileNamedTemplate(Compilation compilation, Expression body, ComponentDeclaration decl) throws XPathException {
        RetainedStaticContext rsc = body.getRetainedStaticContext();
        compiledNamedTemplate.setPackageData(rsc.getPackageData());
        compiledNamedTemplate.setBody(body);
        compiledNamedTemplate.setStackFrameMap(stackFrameMap);
        compiledNamedTemplate.setSystemId(getSystemId());
        compiledNamedTemplate.setLineNumber(getLineNumber());
        compiledNamedTemplate.setColumnNumber(getColumnNumber());
        compiledNamedTemplate.setHasRequiredParams(hasRequiredParams);
        compiledNamedTemplate.setRequiredType(requiredType);
        compiledNamedTemplate.setContextItemRequirements(requiredContextItemType, mayOmitContextItem, absentFocus);
        compiledNamedTemplate.setRetainedStaticContext(rsc);
        compiledNamedTemplate.setDeclaredVisibility(getDeclaredVisibility());
        Component overridden = getOverriddenComponent();
        if (overridden != null) {
            checkCompatibility(overridden);
        }

        ContextItemStaticInfo cisi = getConfiguration().makeContextItemStaticInfo(requiredContextItemType, mayOmitContextItem);
        Expression body2 = refineTemplateBody(body, cisi);
        compiledNamedTemplate.setBody(body2);

        if (getCompilation().getCompilerInfo().getCodeInjector() != null) {
            getCompilation().getCompilerInfo().getCodeInjector().process(compiledNamedTemplate);
        }
    }




    private Expression refineTemplateBody(Expression body, ContextItemStaticInfo cisi) {
        Expression old = body;
        try {
            body = body.simplify();
        } catch (XPathException e) {
            if (e.isReportableStatically()) {
                compileError(e);
            } else {
                body = new ErrorExpression(new XmlProcessingException(e));
                ExpressionTool.copyLocationInfo(old, body);
            }
        }

        Configuration config = getConfiguration();
        if (visibility != Visibility.ABSTRACT) {
            try {
                if (requiredType != null && requiredType != SequenceType.ANY_SEQUENCE) {
                    RoleDiagnostic role =
                            new RoleDiagnostic(RoleDiagnostic.TEMPLATE_RESULT, diagnosticId, 0);
                    role.setErrorCode("XTTE0505");
                    body = config.getTypeChecker(false).staticTypeCheck(body, requiredType, role, makeExpressionVisitor());
                }
            } catch (XPathException err) {
                if (err.isReportableStatically()) {
                    compileError(err);
                }
                body = new ErrorExpression(new XmlProcessingException(err));
                ExpressionTool.copyLocationInfo(old, body);
            }
        }

        try {
            ExpressionVisitor visitor = makeExpressionVisitor();
            body = body.typeCheck(visitor, cisi);
        } catch (XPathException e) {
            compileError(e);
        }

        return body;
    }

    public void compileTemplateRule(Compilation compilation, Expression body, ComponentDeclaration decl) {

        Configuration config = getConfiguration();

        if (getTemplateName() != null) {
            body = body.copy(new RebindingMap());
        }

        ItemType contextItemType;
        ContextItemStaticInfo cisi;
        // the template can't be called by name, so the context item must match the match pattern
        contextItemType = match.getItemType();
        if (contextItemType.equals(ErrorType.getInstance())) {
            // if the match pattern can't match anything, we produce a warning, not a hard error
            contextItemType = AnyItemType.getInstance();
        }
        cisi = config.makeContextItemStaticInfo(contextItemType, false);
        body = refineTemplateBody(body, cisi);

        boolean first = true;
        for (Map.Entry<StructuredQName, TemplateRule> kvp : compiledTemplateRules.entrySet()) {
            if (!kvp.getKey().equals(Mode.OMNI_MODE)) {
                TemplateRule rule = kvp.getValue();
                if (first) {
                    rule.setMatchPattern(match);
                    rule.setBody(body);
                    if (compilation.getCompilerInfo().getCodeInjector() != null) {
                        compilation.getCompilerInfo().getCodeInjector().process(rule);
                        body = rule.getBody();
                    }
                    first = false;
                } else {
                    if (rule.getBody() == null) {
                        body = body.copy(new RebindingMap());
                    } else {
                        body = rule.getBody();
                    }
                }
                setCompiledTemplateRuleProperties(rule, body);
                rule.updateSlaveCopies();
            }
        }

        // following code needed only for diagnostics
        //body.verifyParentPointers();
    }

    private void createSkeletonTemplate(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        StructuredQName[] modes = modeNames;
        if (isOmniMode()) {
            List<StructuredQName> all = new ArrayList<>();
            all.add(Mode.UNNAMED_MODE_NAME);
            RuleManager mgr = getCompilation().getPrincipalStylesheetModule().getRuleManager();
            for (Mode m : mgr.getAllNamedModes()) {
                all.add(m.getModeName());
            }
            modes = all.toArray(new StructuredQName[0]);
        }
        for (StructuredQName modeName : modes) {
            TemplateRule templateRule = compiledTemplateRules.get(modeName);
            if (templateRule == null) {
                templateRule = getConfiguration().makeTemplateRule();
            }
            templateRule.prepareInitializer(compilation, decl, modeName);
            compiledTemplateRules.put(modeName, templateRule);
            RetainedStaticContext rsc = makeRetainedStaticContext();
            templateRule.setPackageData(rsc.getPackageData());
            setCompiledTemplateRuleProperties(templateRule, null);
        }
    }

    private void setCompiledTemplateRuleProperties(TemplateRule templateRule, Expression body) {
        templateRule.setMatchPattern(match);
        templateRule.setBody(body);
        templateRule.setStackFrameMap(stackFrameMap);
        templateRule.setSystemId(getSystemId());
        templateRule.setLineNumber(getLineNumber());
        templateRule.setColumnNumber(getColumnNumber());
        templateRule.setHasRequiredParams(hasRequiredParams);
        templateRule.setRequiredType(requiredType);
        templateRule.setContextItemRequirements(requiredContextItemType, absentFocus);
    }

    /**
     * Code executed when the template is first executed under JIT. If the template is defined in several
     * modes, then this may be called several times, but it only does anything the first time. Mode-specific
     * processing is done in the TemplateRuleInitializer.
     *
     * @param compilation the compilation episode
     * @param decl        the template rule declaration
     * @throws XPathException if anything goes wrong
     */

    public synchronized void jitCompile(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        if (!jitCompilationDone) {
            jitCompilationDone = true;
            compilation.setPreScan(false);
            processAllAttributes();
            checkForJitCompilationErrors(compilation);
            validateSubtree(decl, false);
            checkForJitCompilationErrors(compilation);
            compileDeclaration(compilation, decl);
            checkForJitCompilationErrors(compilation);
        }

    }

    private void checkForJitCompilationErrors(Compilation compilation) throws XPathException {
        if (compilation.getErrorCount() > 0) {
            XPathException e = new XPathException("Errors were reported during JIT compilation of template rule with match=\"" + matchAtt + "\"",
                                                  SaxonErrorCode.SXST0001, this);
            e.setHasBeenReported(true); // only intended as an exception message, not something to report to ErrorListener
            throw e;
        }
    }


    /**
     * Registers the template rule with each Mode that it belongs to.
     *
     * @param declaration Associates this template with a stylesheet module (in principle an xsl:template
     *                    element can be in a document that is imported more than once; these are separate declarations)
     * @throws XPathException if a failure occurs
     */

    public void register(ComponentDeclaration declaration) throws XPathException {
        if (match != null) {
            StylesheetModule module = declaration.getModule();
            RuleManager mgr = getCompilation().getPrincipalStylesheetModule().getRuleManager();
            ExpressionVisitor visitor = ExpressionVisitor.make(getStaticContext());
            for (StructuredQName modeName : getModeNames()) {
                Mode mode = mgr.obtainMode(modeName, false);
                if (mode == null) {
                    if (mgr.existsOmniMode()) {
                        Mode omniMode = mgr.obtainMode(Mode.OMNI_MODE, true);
                        mode = mgr.obtainMode(modeName, true);
                        SimpleMode.copyRules(omniMode.getActivePart(), mode.getActivePart());
                    } else {
                        mode = mgr.obtainMode(modeName, true);
                    }
                } else {
                    boolean ok = getPrincipalStylesheetModule().checkAcceptableModeForPackage(this, mode);
                    if (!ok) {
                        return;
                    }
                }
                Pattern match1 = match.copy(new RebindingMap());
                String typed = mode.getActivePart().getPropertyValue("typed");
                if ("strict".equals(typed) || "lax".equals(typed)) {
                    Pattern match2;
                    try {
                        match2 = match1.convertToTypedPattern(typed);
                    } catch (XPathException e) {
                        e.maybeSetLocation(this);
                        throw e;
                    }
                    if (match2 != match1) {
                        ContextItemStaticInfo info = getConfiguration().makeContextItemStaticInfo(AnyItemType.getInstance(), false);
                        ExpressionTool.copyLocationInfo(match, match2);
                        match2.setOriginalText(match.toString());
                        match2 = match2.typeCheck(visitor, info);
                        match1 = match2;
                    }
                    if (modeNames.length == 1) {
                        // If this is the only mode for the template, then we can use this enhanced match pattern
                        // for subsequent type-checking of the template body.
                        // TODO: we can now do this for all modes...
                        // TODO: but we need to take account of mode=#all, where modeNames.length==1
                        match = match2;
                    }
                }
                TemplateRule rule = compiledTemplateRules.get(modeName);
                if (rule == null) {
                    rule = getConfiguration().makeTemplateRule();
                    compiledTemplateRules.put(modeName, rule);
                }

                double prio = prioritySpecified ? priority : Double.NaN;
                mgr.registerRule(match1, rule, mode, module, prio, mgr.allocateSequenceNumber(), 0);

                if (mode.isDeclaredStreamable()) {
                    rule.setDeclaredStreamable(true);
                    if (!match1.isMotionless()) {
                        boolean fallback = getConfiguration().getBooleanProperty(Feature.STREAMING_FALLBACK);
                        String message = "Template rule is declared streamable but the match pattern is not motionless";
                        if (fallback) {
                            message += "\n  * Falling back to non-streaming implementation";
                            getStaticContext().issueWarning(message, this);
                            rule.setDeclaredStreamable(false);
                            getCompilation().setFallbackToNonStreaming(true);
                        } else {
                            throw new XPathException(message, "XTSE3430", this);
                        }
                    }
                }

                if (mode.getDefaultResultType() != null && !declaresRequiredType) {
                    rule.setRequiredType(mode.getDefaultResultType());
                }

                // if adding a rule to the omniMode (mode='all') add it to all
                // the other modes as well. For all but the first, it needs to
                // be copied because the external component bindings might
                // differ from one mode to another.

                if (mode.getModeName().equals(Mode.OMNI_MODE)) {
                    compiledTemplateRules.put(Mode.UNNAMED_MODE_NAME, rule);
                    mgr.registerRule(match1, rule, mgr.getUnnamedMode(), module, prio, mgr.allocateSequenceNumber(), 0);
                    for (Mode m : mgr.getAllNamedModes()) {
                        if (m instanceof SimpleMode) {
                            TemplateRule ruleCopy = rule.copy();
                            if (m.isDeclaredStreamable()) {
                                ruleCopy.setDeclaredStreamable(true);
                            }
                            compiledTemplateRules.put(m.getModeName(), ruleCopy);
                            mgr.registerRule(match1.copy(new RebindingMap()),
                                             ruleCopy, m, module, prio, mgr.allocateSequenceNumber(), 0);
                        }
                    }
                }
            }
        }
    }

    /**
     * Allocate slot numbers to any local variables declared within a predicate within the match pattern
     */

    public void allocatePatternSlotNumbers() {
        if (match != null) {
            for (TemplateRule templateRule : compiledTemplateRules.values()) {
                for (Rule r : templateRule.getRules()) {
                    // In the case of a union pattern, allocate slots separately for each branch
                    Pattern match = r.getPattern();
                    // first slot in pattern is reserved for current()
                    int nextFree = 0;
                    if ((match.getDependencies() & StaticProperty.DEPENDS_ON_CURRENT_ITEM) != 0) {
                        nextFree = 1;
                    }
                    int slots = match.allocateSlots(getSlotManager(), nextFree);
                    // if the pattern calls user-defined functions, allocate at least one slot,
                    // to force a new context to be created for evaluating patterns (bug 3706)
                    if (slots == 0 && ((match.getDependencies() & StaticProperty.DEPENDS_ON_USER_FUNCTIONS) != 0)) {
                        slots = 1;
                    }
                    if (slots > 0) {
                        RuleManager mgr = getCompilation().getPrincipalStylesheetModule().getRuleManager();
                        boolean appliesToAll = false;
                        for (StructuredQName nc : modeNames) {
                            if (nc.equals(Mode.OMNI_MODE)) {
                                appliesToAll = true;
                                break;
                            }
                            Mode mode = mgr.obtainMode(nc, true);
                            mode.getActivePart().allocatePatternSlots(slots);
                        }
                        if (appliesToAll) {
                            for (Mode m : mgr.getAllNamedModes()) {
                                m.getActivePart().allocatePatternSlots(slots);
                            }
                            mgr.getUnnamedMode().getActivePart().allocatePatternSlots(slots);
                        }
                    }

                }
            }
        }
    }


    /**
     * This method is a bit of a misnomer, because it does more than invoke optimization of the template body.
     * In particular, it also registers the template rule with each Mode that it belongs to.
     *
     * @param declaration Associates this template with a stylesheet module (in principle an xsl:template
     *                    element can be in a document that is imported more than once; these are separate declarations)
     * @throws XPathException if errors are found
     */

    @Override
    public void optimize(ComponentDeclaration declaration) throws XPathException {
        Configuration config = getConfiguration();
        if (compiledNamedTemplate != null) {
            Expression body = compiledNamedTemplate.getBody();
            ContextItemStaticInfo cisi = getConfiguration().makeContextItemStaticInfo(requiredContextItemType, mayOmitContextItem);

            ExpressionVisitor visitor = makeExpressionVisitor();
            body = body.typeCheck(visitor, cisi);
            body = ExpressionTool.optimizeComponentBody(body, getCompilation(), visitor, cisi, true);
            compiledNamedTemplate.setBody(body);

            allocateLocalSlots(body);
            if (explaining) {
                Logger err = getConfiguration().getLogger();
                err.info("Optimized expression tree for named template at line " +
                                 getLineNumber() + " in " + getSystemId() + ':');
                body.explain(err);
            }
            body.restoreParentPointers();
            if (config.isDeferredByteCode(HostLanguage.XSLT) && !isTailRecursive) {
                Optimizer opt = config.obtainOptimizer();
                int evaluationModes = Expression.ITERATE_METHOD | Expression.PROCESS_METHOD;
                compiledNamedTemplate.setBody(opt.makeByteCodeCandidate(compiledNamedTemplate, body, diagnosticId, evaluationModes));
            }
        }
        if (match != null) {
            ItemType contextItemType = getContextItemTypeForTemplateRule();
            ContextItemStaticInfo cisi = config.makeContextItemStaticInfo(contextItemType, false);
            cisi.setContextPostureStriding();
            ExpressionVisitor visitor = makeExpressionVisitor();
            match.resetLocalStaticProperties();
            match = match.optimize(visitor, cisi);

            if (!isDeferredCompilation(getCompilation())) {
                Optimizer opt = getConfiguration().obtainOptimizer();
                try {
                    for (Map.Entry<StructuredQName, TemplateRule> entry : compiledTemplateRules.entrySet()) {
                        if (!entry.getKey().equals(Mode.OMNI_MODE)) {
                            TemplateRule compiledTemplateRule = entry.getValue();
                            Expression templateRuleBody = compiledTemplateRule.getBody();
                            visitor.setOptimizeForStreaming(compiledTemplateRule.isDeclaredStreamable());
                            templateRuleBody = templateRuleBody.typeCheck(visitor, cisi);
                            templateRuleBody = ExpressionTool.optimizeComponentBody(templateRuleBody, getCompilation(), visitor, cisi, true);
                            compiledTemplateRule.setBody(templateRuleBody);
                            opt.checkStreamability(this, compiledTemplateRule);
                            allocateLocalSlots(templateRuleBody);
                            for (Rule r : compiledTemplateRule.getRules()) {
                                Pattern match = r.getPattern();
                                ContextItemStaticInfo info = getConfiguration().makeContextItemStaticInfo(match.getItemType(), false);
                                info.setContextPostureStriding();
                                Pattern m2 = match.optimize(visitor, info);
                                if (compiledTemplateRules.size() > 1) {
                                    m2 = m2.copy(new RebindingMap());
                                }
                                if (m2 != match) {
                                    r.setPattern(m2);
                                }
                            }

                            if (visitor.getConfiguration().isDeferredByteCode(HostLanguage.XSLT) && !isTailRecursive) {
                                int evaluationModes = Expression.ITERATE_METHOD | Expression.PROCESS_METHOD;
                                compiledTemplateRule.setBody(opt.makeByteCodeCandidate(compiledTemplateRule, templateRuleBody, diagnosticId, evaluationModes));
                            }

                            if (explaining) {
                                Logger err = getConfiguration().getLogger();
                                err.info("Optimized expression tree for template rule at line " +
                                                 getLineNumber() + " in " + getSystemId() + ':');
                                templateRuleBody.explain(err);
                            }
                        }
                    }
                } catch (XPathException e) {
                    e.maybeSetLocation(this);
                    compileError(e);
                }
            }
        }

    }

    public ItemType getContextItemTypeForTemplateRule() throws XPathException {
        Configuration config = getConfiguration();
        ItemType contextItemType = match.getItemType();
        if (contextItemType.equals(ErrorType.getInstance())) {
            // if the match pattern can't match anything, we produce a warning, not a hard error
            contextItemType = AnyItemType.getInstance();
        }
        if (requiredContextItemType != AnyItemType.getInstance()) {
            Affinity rel = config.getTypeHierarchy().relationship(contextItemType, requiredContextItemType);
            switch (rel) {
                case DISJOINT:
                    XPathException e = new XPathException("The declared context item type is inconsistent with the match pattern", "XPTY0004", this);
                    e.setIsTypeError(true);
                    throw e;
                case SUBSUMED_BY:
                case OVERLAPS:
                case SAME_TYPE:
                    // no action
                    break;
                case SUBSUMES:
                    contextItemType = requiredContextItemType;
                    break;
            }
        }
        return contextItemType;
    }


    /**
     * Generate byte code for the template (if appropriate)
     *
     * @param opt the optimizer
     * @throws XPathException if byte code generation fails
     */

    @Override
    public void generateByteCode(Optimizer opt) throws XPathException {
        // Generate byte code if appropriate

        if (getCompilation().getCompilerInfo().isGenerateByteCode() && !isTailRecursive) {
            ICompilerService compilerService = getConfiguration().makeCompilerService(HostLanguage.XSLT);
            if (getTemplateName() != null) {
                try {
                    Expression exp = compiledNamedTemplate.getBody();
                    Expression cbody = opt.compileToByteCode(compilerService, exp, nameAtt, Expression.PROCESS_METHOD);
                    if (cbody != null) {
                        compiledNamedTemplate.setBody(cbody);
                    }
                } catch (Exception e) {
                    System.err.println("Failed while compiling named template " + nameAtt);
                    e.printStackTrace();
                    throw new XPathException(e);
                }
            }
            for (TemplateRule compiledTemplateRule : compiledTemplateRules.values()) {
                if (!compiledTemplateRule.isDeclaredStreamable()) {
                    try {
                        Expression exp = compiledTemplateRule.getBody();
                        if (exp != null) {
                            Expression cbody = opt.compileToByteCode(compilerService, exp, matchAtt, Expression.PROCESS_METHOD);
                            if (cbody != null) {
                                compiledTemplateRule.setBody(cbody);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Failed while compiling template rule with match = '" + matchAtt + "'");
                        e.printStackTrace();
                        throw new XPathException(e);
                    }
                }
            }
        }
    }


    /**
     * Get associated Procedure (for details of stack frame)
     */

    @Override
    public SlotManager getSlotManager() {
        return stackFrameMap;
    }


    /**
     * Get the compiled template
     *
     * @return the compiled template
     */

    public NamedTemplate getCompiledNamedTemplate() {
        return compiledNamedTemplate;
    }


    public Pattern getMatch() {
        return match;
    }

    public Map<StructuredQName, TemplateRule> getTemplateRulesByMode() {
        return compiledTemplateRules;
    }
}

