////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
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
import net.sf.saxon.expr.instruct.ComponentTracer;
import net.sf.saxon.expr.instruct.NamedTemplate;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.instruct.TemplateRule;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.*;
import net.sf.saxon.pattern.nodetest.NodeTest;
import net.sf.saxon.trans.*;
import net.sf.saxon.trans.rules.Rule;
import net.sf.saxon.trans.rules.RuleManager;
import net.sf.saxon.tree.linked.NodeImpl;
import net.sf.saxon.type.*;
import net.sf.saxon.value.BigDecimalValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

import java.util.*;
import java.util.function.Supplier;

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
    private final List<TemplateRule> compiledTemplateRules = new ArrayList<>();
    private SequenceType requiredType = SequenceType.ANY_SEQUENCE;
    private boolean declaresRequiredType = false;
    private Visibility visibility = Visibility.PRIVATE;
    private ItemType requiredContextItemType = AnyItemType.getInstance();
    private Optionality contextItemOptionality = Optionality.OPTIONAL;
    //private boolean absentFocus = false;
    private boolean jitCompilationDone = false;
    private boolean explaining;
    private List<Pattern> subPatterns;
    /**
     * Get the corresponding NamedTemplate object that results from the compilation of this
     * StylesheetComponent
     */
    @Override
    public NamedTemplate getActor() {
        return compiledNamedTemplate;
    }

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
    protected boolean mayContainSequenceConstructor() {
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
     * @param status  indicates whether the context value is required, optional, or disallowed
     */

    public void setContextItemRequirements(ItemType type, Optionality status) {
        requiredContextItemType = type;
        this.contextItemOptionality = status;
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
            String nameAtt = getAttributeValue(NamespaceUri.NULL, "name");
            if (nameAtt != null) {
                setObjectName(makeQName(nameAtt, null, "name"));
            }
        }
        return getObjectName();
    }

    public String getDiagnosticId() {
        return diagnosticId;
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

    public SequenceType getRequiredType() {
        return requiredType;
    }


    @Override
    public void checkCompatibility(Component component) {
        NamedTemplate other = (NamedTemplate) component.getActor();
        if (!Objects.equals(getSymbolicName(), other.getSymbolicName())) {
            throw new IllegalArgumentException();
        }

        SequenceType req = requiredType == null ? SequenceType.ANY_SEQUENCE : requiredType;
        if (!req.equals(other.getRequiredType())) {
            compileError("The overriding template has a different required type from the overridden template", "XTSE3070");
            return;
        }

        if (!requiredContextItemType.equals(other.getRequiredContextItemType()) ||
                contextItemOptionality != other.getContextItemOptionality()) {
            compileError("The context item requirement for the overriding template differs from that of the overridden template", "XTSE3070");
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
    protected void prepareAttributes() {

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
            } else if (name.hasURI(NamespaceUri.SAXON)) {
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
            }
        } catch (XPathException err) {
            XPathException e2 = err.replacingErrorCode("XTSE0020", "XTSE0550");
            e2.maybeSetErrorCode("XTSE0280");
            e2.setIsStaticError(true);
            compileError(e2);
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

        if (matchAtt != null && match == null) {
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
                compileErrorInAttribute(e, "as");
            }
        }

        if (extraAsAtt != null) {
            compileWarning("saxon:as is deprecated", SaxonErrorCode.SXWN9053);
            SequenceType extraResultType;
            declaresRequiredType = true;
            try {
                extraResultType = makeExtendedSequenceType(extraAsAtt);
            } catch (XPathException e) {
                compileErrorInAttribute(e, "saxon:as");
                extraResultType = requiredType; // error recovery
            }
            if (asAtt != null) {
                Affinity rel = Subsumption.sequenceTypeRelationship(extraResultType, requiredType);
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
                compileError("xsl:template/@visibility can be specified only if the template has a @name attribute", "XTSE0500");
            } else {
                compiledNamedTemplate.setDeclaredVisibility(getVisibility());
            }
        }
    }

    @Override
    public void processAllAttributes() throws XPathException {
        // With JIT compilation enabled, we don't process the attributes of descendant elements
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
     * Return the mode names to which this template rule is applicable.
     *
     * @return the array of mode names. If the mode attribute is absent, #default is assumed.
     * If #default is present explicitly or implicitly, it is replaced by the default mode, taken
     * from the in-scope default-modes attribute, which defaults to #unnamed. The unnamed mode
     * is represented by {@link Mode#UNNAMED_MODE_NAME}. The token #all translates to
     * {@link Mode#OMNI_MODE_NAME}.
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
                    mname = Mode.OMNI_MODE_NAME;
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
            if (name.equals(Mode.OMNI_MODE_NAME)) {
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
     * Ask whether this is a template rule with mode="#all"
     * @return true if this is the case
     * @throws XPathException if the mode attribute is found to be invalid
     */

    public boolean appliesToAllModes() throws XPathException {
        for (StructuredQName name : getModeNames()) {
            if (name.equals(Mode.OMNI_MODE_NAME)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        stackFrameMap = getConfiguration().makeSlotManager();

        StyleElement enclosingMode = null;
        NodeImpl parent = getParent();
        assert parent != null;
        if (getCompilation().getCompilerInfo().getXsltVersion() >= 40
                && parent.getFingerprint() == StandardNames.XSL_MODE) {
            enclosingMode = (StyleElement) parent;
        }
        if (enclosingMode == null) {
            checkTopLevel("XTSE0010", true);
        } else {
            if (matchAtt == null) {
                compileError("A template rule enclosed within xsl:mode must have a match attribute", "XTSE4010");
            }
            if (modeAtt != null) {
                compileError("A template rule enclosed within xsl:mode must not have a mode attribute", "XTSE4010");
            }
            if (nameAtt != null) {
                compileError("A template rule enclosed within xsl:mode must not have a name attribute", "XTSE4010");
            }
            modeNames = new StructuredQName[1];
            modeNames[0] = ((XSLMode)getParent()).getObjectName();
        }

        // the check for duplicates is now done in the buildIndexes() method of XSLStylesheet
        if (match != null) {
            match = typeCheck("match", match);
            if (match.getItemType() instanceof ErrorType) {
                issueWarning("Pattern will never match anything", SaxonErrorCode.SXWN9015);
            }
            if (getPrincipalStylesheetModule().isDeclaredModes()) {
                RuleManager manager = getPrincipalStylesheetModule().getRuleManager();
                StructuredQName[] modes = getModeNames();
                if (modes != null) {
                    for (StructuredQName name : modes) {
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

        boolean hasContent = false;
        for (NodeInfo child : children(StyleElement.class::isInstance)) {
            if (!(child.getFingerprint() == StandardNames.XSL_CONTEXT_ITEM || child.getFingerprint() == StandardNames.XSL_PARAM)) {
                hasContent = true;
                break;
            }
        }

        if (visibility == Visibility.ABSTRACT && hasContent) {
            compileError("A template with visibility='abstract' must have no body");
        }

        // If the pattern matches both XNodes and JNodes, then for the time being,
        // treat it as if it only matched XNodes. See spec issue 2444.

        boolean splitGNode = false;
        if (match instanceof NodeTestPattern ntp && ntp.getNodeTest().getUType().overlaps(UType.JNODE)) {
            // TODO: this doesn't handle other cases such as match="order[price = 3]"
            double priority = match.getDefaultPriority();
            Configuration config = getConfiguration();
            Pattern p1 = new NodeTestPattern(ntp.getNodeTest().asXNodeTest(config));
            p1.setPriority(priority);
            ExpressionTool.copyLocationInfo(match, p1);
            match = p1;
            match.setPriority(priority);
            splitGNode = true;
        }

        // If the pattern is a union pattern and there is no priority specified, split into
        // multiple template rules so each can be given its own priority. This rule changes
        // in 4.0.
        if (match instanceof UnionPattern) {
            subPatterns = new ArrayList<>(2);
            if (prioritySpecified && !splitGNode) {
                subPatterns.add(match);
            } else if (getCompilation().getCompilerInfo().getXsltVersion() >= 40 && !splitGNode) {
                List<Pattern> localSubPatterns = new ArrayList<>(2);
                gatherSubPatterns(match, localSubPatterns);
                if (localSubPatterns.size() > 1) {
                    double prio = localSubPatterns.get(0).getDefaultPriority();
                    for (int i=1; i<localSubPatterns.size(); i++) {
                        if (prio != localSubPatterns.get(i).getDefaultPriority()) {
                            compileWarning("The behavior of a union pattern whose branches have"
                                                   + " different default priority has changed in XSLT 4.0."
                                                   + " For compatibility, allocate an explicit priority,"
                                                   + " or split into multiple template rules",
                                           SaxonErrorCode.SXWN9052);
                            break;
                        }
                    }
                }
                subPatterns.add(match);
            } else {
                gatherSubPatterns(match, subPatterns);
            }
        } else if (match != null) {
            subPatterns = new ArrayList<>(1);
            subPatterns.add(match);
        }

    }

    /**
     * Split UnionPatterns into their component patterns
     * @param match the pattern, possibly a union pattern
     * @param subPatterns output parameter to hold the list of subpatterns after splitting
     */
    private void gatherSubPatterns(Pattern match, List<Pattern> subPatterns) {
        if (match instanceof UnionPattern up) {
            gatherSubPatterns(up.getLHS(), subPatterns);
            gatherSubPatterns(up.getRHS(), subPatterns);
        } else if (match instanceof NodeTestPattern &&
                match.getItemType() instanceof CombinedNodeTest cnt &&
                ((CombinedNodeTest) match.getItemType()).getOperator() == OperatorSymbol.UNION) {
            NodeTest[] nt = cnt.getComponentNodeTests();
            final NodeTestPattern nt0 = new NodeTestPattern(nt[0]);
            subPatterns.add(nt0);
            ExpressionTool.copyLocationInfo(match, nt0);
            final NodeTestPattern nt1 = new NodeTestPattern(nt[1]);
            ExpressionTool.copyLocationInfo(match, nt1);
            subPatterns.add(nt1);
        } else {
            subPatterns.add(match);
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

    /**
     * If this is a named template, then add it to the stylesheet-level component index
     * @param decl the Declaration being indexed. (This corresponds to the StyleElement object
     *             except in cases where one module is imported several times with different precedence.)
     * @param top  represents the outermost XSLStylesheet or XSLPackage element
     */
    @Override
    public void index(ComponentDeclaration decl, PrincipalStylesheetModule top)  {
        if (getTemplateName() != null) {
            if (compiledNamedTemplate == null) {
                compiledNamedTemplate = new NamedTemplate(getTemplateName(), getConfiguration());
            }
            top.indexNamedTemplate(decl);
        }
    }

    /**
     * Mark tail-recursive calls on templates and functions.
     */

    @Override
    protected boolean markTailCalls() {
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
            markTailCalls();
        }
        Expression body = compileSequenceConstructor(compilation, decl, true);
        body.restoreParentPointers();
        RetainedStaticContext rsc = makeRetainedStaticContext();
        if (body.getLocalRetainedStaticContext() == null) {
            body.setRetainedStaticContext(rsc); // bug 2608
        }
        if (match != null && compilation.getConfiguration().getBooleanProperty(Feature.STRICT_STREAMABILITY) &&
                isWithinDeclaredStreamableConstruct()) {
            checkStrictStreamability(body);
        }
        if (getTemplateName() != null) {
            compileNamedTemplate(body);
        }
        if (match != null) {
            //System.err.println("Rules compiled - " + ++eager);
            compileTemplateRule(compilation, body);
        }
    }

    private void checkStrictStreamability(Expression body) throws XPathException {
        getConfiguration().checkStrictStreamability(this, body);
    }

    private void compileNamedTemplate(Expression body)  {
        RetainedStaticContext rsc = body.getRetainedStaticContext();
        compiledNamedTemplate.setPackageData(rsc.getPackageData());
        compiledNamedTemplate.setBody(body);
        compiledNamedTemplate.setStackFrameMap(stackFrameMap);
        compiledNamedTemplate.setSystemId(getSystemId());
        compiledNamedTemplate.setLineNumber(getLineNumber());
        compiledNamedTemplate.setColumnNumber(getColumnNumber());
        compiledNamedTemplate.setRequiredType(requiredType);
        compiledNamedTemplate.setContextItemRequirements(requiredContextItemType, contextItemOptionality);
        compiledNamedTemplate.setRetainedStaticContext(rsc);
        compiledNamedTemplate.setDeclaredVisibility(getDeclaredVisibility());
        Component overridden = getOverriddenComponent();
        if (overridden != null) {
            checkCompatibility(overridden);
        }

        ContextItemStaticInfo cisi = getConfiguration().makeContextItemStaticInfo(requiredContextItemType, contextItemOptionality);
        Expression body2 = refineTemplateBody(body, cisi);
        compiledNamedTemplate.setBody(body2);

        if (getCompilation().getCompilerInfo().getCodeInjector() != null) {
            getCompilation().getCompilerInfo().getCodeInjector().process(compiledNamedTemplate);
        }
    }


    /**
     * Perform expression simplification and type-checking on the body of the template
     * @param body the body of the template rule
     * @param cisi type information about the static context
     * @return the body after simplification and type checking
     */

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
                    Supplier<RoleDiagnostic> role = () ->
                            new RoleDiagnostic(RoleDiagnostic.TEMPLATE_RESULT, diagnosticId, 0, "XTTE0505");
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

    public void compileTemplateRule(Compilation compilation, Expression body) {

        Configuration config = getConfiguration();

        if (getTemplateName() != null) {
            // If this is both a named template and a template rule, treat both as separate
            body = body.copy(new RebindingMap());
        }


        boolean first = true;
        for (TemplateRule rule : compiledTemplateRules) {
            ItemType contextItemType;
            ContextItemStaticInfo cisi;
            // the template can't be called by name, so the context item must match the match pattern
            contextItemType = rule.getMatchPattern().getItemType();
            if (contextItemType.equals(ErrorType.getInstance())) {
                // if the match pattern can't match anything, we produce a warning, not a hard error
                contextItemType = AnyItemType.INSTANCE;
            }
            cisi = config.makeContextItemStaticInfo(contextItemType, contextItemOptionality);
            Expression refinedBody = body.copy(new RebindingMap());
            refinedBody = refineTemplateBody(refinedBody, cisi);

            if (first) {
                //rule.setMatchPattern(match);
                rule.setBody(refinedBody);
                if (compilation.getCompilerInfo().getCodeInjector() != null) {
                    compilation.getCompilerInfo().getCodeInjector().process(rule);
                    refinedBody = rule.getBody();
                }
                first = false;
            } else {
                if (rule.getBody() == null) {
                    refinedBody = body.copy(new RebindingMap());
                    if (refinedBody instanceof ComponentTracer) {
                        ((ComponentTracer) refinedBody).setProperty("match", rule.getMatchPattern());
                    }
                } else {
                    refinedBody = rule.getBody();
                }
            }
            setCompiledTemplateRuleProperties(rule, refinedBody);
        }
    }

    private void createSkeletonTemplate(Compilation compilation, ComponentDeclaration decl) {
        for (TemplateRule templateRule : compiledTemplateRules) {
            templateRule.prepareInitializer(compilation, decl);
            RetainedStaticContext rsc = makeRetainedStaticContext();
            templateRule.setPackageData(rsc.getPackageData());
            setCompiledTemplateRuleProperties(templateRule, null);
        }
    }

    private void setCompiledTemplateRuleProperties(TemplateRule templateRule, Expression body) {
        //templateRule.setMatchPattern(match);
        templateRule.setBody(body);
        templateRule.setStackFrameMap(stackFrameMap);
        templateRule.setSystemId(getSystemId());
        templateRule.setLineNumber(getLineNumber());
        templateRule.setColumnNumber(getColumnNumber());
        //templateRule.setRequiredType(requiredType);
        templateRule.setContextItemRequirements(requiredContextItemType, contextItemOptionality);
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
            XPathException e = new XPathException(
                    "Errors were reported during JIT compilation of template rule with match=\"" + matchAtt + "\"",
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
            Iterable<StructuredQName> modeNames = Arrays.asList(getModeNames());
            if (appliesToAllModes()) {
                modeNames = getCompilation().getAllKnownModeNames();
            }
            for (StructuredQName modeName : modeNames) {
                Mode mode = mgr.obtainMode(modeName, true);
                if (appliesToAllModes() && mode.isEnclosingMode()) {
                    continue;
                }
                boolean ok = getPrincipalStylesheetModule().checkAcceptableModeForPackage(this, mode);
                if (!ok) {
                    return;
                }
                if (mode.isEnclosingMode() && !(getParent() instanceof XSLMode && mode == ((XSLMode)getParent()).getMode())) {
                    compileError("An xsl:template rule must not refer to a mode that contains enclosed template rules "
                                         + "unless it is itself enclosed by that xsl:mode declaration", "XTSE4020");
                }
                int part = 0;

                int seq = mgr.allocateSequenceNumber();
                for (Pattern subPattern : subPatterns) {
                    Pattern subPattern2 = subPattern;

                    TemplateRule rule = getConfiguration().makeTemplateRule();
                    rule.setMode(mode);

                    // Adapt the pattern if the mode has @typed=strict|lax
                    String typed = (String)mode.getActivePart().getPropertyValue("typed");
                    if ("strict".equals(typed) || "lax".equals(typed)) {
                        try {
                            subPattern2 = subPattern.convertToTypedPattern(typed);
                        } catch (XPathException e) {
                            throw e.maybeWithLocation(this);
                        }
                        if (subPattern2 != subPattern) {
                            ExpressionTool.copyLocationInfo(match, subPattern2);
                            subPattern2.setOriginalText(match.toString());
                        }
                    }


                    // Copy the match pattern: in the case where a template rule belongs to multiple modes,
                    // the binding vector for any references to external functions or variables belongs
                    // to the mode, and the slot numbers for these references will vary from one mode to another.
                    subPattern2 = subPattern2.copy(new RebindingMap());
                    rule.setMatchPattern(subPattern2);
                    compiledTemplateRules.add(rule);

                    if (mode.isDeclaredStreamable()) {
                        rule.setDeclaredStreamable(true);
                        if (!subPattern2.isMotionless()) {
                            boolean fallback = getConfiguration().getBooleanProperty(Feature.STREAMING_FALLBACK);
                            String message = "Template rule is declared streamable but the match pattern is not motionless";
                            if (fallback) {
                                message += "\n  * Falling back to non-streaming implementation";
                                getStaticContext().issueWarning(message, SaxonErrorCode.SXWN9024, this);
                                rule.setDeclaredStreamable(false);
                                getCompilation().setFallbackToNonStreaming(true);
                            } else {
                                throw new XPathException(message, "XTSE3430", this);
                            }
                        }
                    }

                    if (mode.getDefaultResultType() != null) {
                        if (!declaresRequiredType) {
                            rule.setRequiredType(mode.getDefaultResultType());
                        } else {
                            Affinity aff = Subsumption.sequenceTypeRelationship(requiredType, mode.getDefaultResultType());
                            if (aff != Affinity.SAME_TYPE && aff != Affinity.SUBSUMED_BY) {
                                compileError("Type declared in xsl:template/@as must be a subtype of the type declared in xsl:mode/@as",
                                             "XTSE4040");
                            }
                        }
                    }

                    double prio = prioritySpecified ? priority : Double.NaN;
                    mgr.registerRule(subPattern2, rule, mode, module, prio, seq, part++);

                }
            }
        }
    }

    /**
     * Allocate slot numbers to any local variables declared within a predicate within the match pattern
     */

    public void allocatePatternSlotNumbers() {
        if (match != null) {
            for (TemplateRule templateRule : compiledTemplateRules) {
                // In the case of a union pattern, allocate slots separately for each branch
                Pattern match = templateRule.getMatchPattern();
                // first slot in pattern is reserved for current()
                int nextFree = 0;
                if ((match.getDependencies() & StaticProperty.DEPENDS_ON_CURRENT_ITEM) != 0) {
                    nextFree = 1;
                }
                //System.err.println("Allocate slots to " + match + " -- " + System.identityHashCode(match) + " in " + templateRule);
                int slots = match.allocateSlots(getSlotManager(), nextFree);
                // if the pattern calls user-defined functions, allocate at least one slot,
                // to force a new context to be created for evaluating patterns (bug 3706)
                if (slots == 0 && ((match.getDependencies() & StaticProperty.DEPENDS_ON_USER_FUNCTIONS) != 0)) {
                    slots = 1;
                }
                if (slots > 0) {
                    templateRule.getMode().getActivePart().allocatePatternSlots(slots);
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
            ContextItemStaticInfo cisi = getConfiguration().makeContextItemStaticInfo(
                    requiredContextItemType, contextItemOptionality);

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
        }
        if (match != null) {

            ExpressionVisitor visitor = makeExpressionVisitor();

            for (TemplateRule compiledTemplateRule : compiledTemplateRules) {
                ItemType contextItemType = getContextItemTypeForTemplateRule(compiledTemplateRule);
                if (compiledTemplateRule.getMode().getModeName().equals(Mode.OMNI_MODE_NAME)) {
                    ContextItemStaticInfo cisi = config.makeContextItemStaticInfo(contextItemType, contextItemOptionality);
                    cisi.setContextPostureStriding();
                    compiledTemplateRule.getMatchPattern().resetLocalStaticProperties();
                    Pattern m2 = compiledTemplateRule.getMatchPattern().copy(new RebindingMap()).optimize(visitor, cisi);
                    compiledTemplateRule.setMatchPattern(m2);
                }
            }

            if (!isDeferredCompilation(getCompilation())) {
                Optimizer opt = getConfiguration().obtainOptimizer();
                try {
                    for (TemplateRule compiledTemplateRule : compiledTemplateRules) {
                        if (!compiledTemplateRule.getMode().getModeName().equals(Mode.OMNI_MODE_NAME)) {
                            ItemType contextItemType = getContextItemTypeForTemplateRule(compiledTemplateRule);
                            ContextItemStaticInfo cisi = config.makeContextItemStaticInfo(contextItemType, contextItemOptionality);
                            cisi.setContextPostureStriding();
                            Expression templateRuleBody = compiledTemplateRule.getBody();
                            visitor.setOptimizeForStreaming(compiledTemplateRule.isDeclaredStreamable());
                            templateRuleBody = templateRuleBody.typeCheck(visitor, cisi);
                            TypeHierarchy th = config.getTypeHierarchy();
                            if (!SequenceType.ANY_SEQUENCE.isSameType(compiledTemplateRule.getRequiredType(), th)) {
                                TypeChecker tc = config.getTypeChecker(false);
                                Supplier<RoleDiagnostic> roleSupplier =
                                        () -> new RoleDiagnostic(RoleDiagnostic.TEMPLATE_RESULT, diagnosticId, 0);
                                templateRuleBody = tc.staticTypeCheck(
                                        templateRuleBody,
                                        compiledTemplateRule.getRequiredType(),
                                        roleSupplier, visitor);
                            }
                            templateRuleBody = ExpressionTool.optimizeComponentBody(templateRuleBody, getCompilation(), visitor, cisi, true);
                            compiledTemplateRule.setBody(templateRuleBody);
                            opt.checkStreamability(this, compiledTemplateRule);
                            allocateLocalSlots(templateRuleBody);
                            for (Rule r : compiledTemplateRule.getRules()) {
                                Pattern match = r.getPattern();
                                ContextItemStaticInfo info =
                                        getConfiguration().makeContextItemStaticInfo(match.getItemType(), contextItemOptionality);
                                info.setContextPostureStriding();
                                Pattern m2 = match.optimize(visitor, info);
                                if (m2 != match) {
                                    r.setPattern(m2);
                                }
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
                    compileError(e.maybeWithLocation(this));
                }

            }
        }

    }

    public ItemType getContextItemTypeForTemplateRule(TemplateRule rule) throws XPathException {
        Configuration config = getConfiguration();
        ItemType contextItemType = rule.getMatchPattern().getItemType();
        if (contextItemType.equals(ErrorType.getInstance())) {
            // if the match pattern can't match anything, we produce a warning, not a hard error
            contextItemType = AnyItemType.INSTANCE;
        }
        if (requiredContextItemType != AnyItemType.INSTANCE) {
            Affinity rel = config.getTypeHierarchy().relationship(contextItemType, requiredContextItemType);
            switch (rel) {
                case DISJOINT:
                    XPathException e = new XPathException("The declared context item type is inconsistent with the match pattern", "XTTE0590", this);
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

}

