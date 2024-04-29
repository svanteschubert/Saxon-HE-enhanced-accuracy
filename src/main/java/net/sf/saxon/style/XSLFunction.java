////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.TailCallLoop;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.expr.instruct.UserFunctionParameter;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.*;
import net.sf.saxon.query.Annotation;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.trans.*;
import net.sf.saxon.type.Affinity;
import net.sf.saxon.type.PlainType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for xsl:function elements in stylesheet (XSLT 2.0). <BR>
 * Attributes: <br>
 * name gives the name of the function
 * saxon:memo-function=yes|no indicates whether it acts as a memo function.
 */

public class XSLFunction extends StyleElement implements StylesheetComponent {

    private boolean doneAttributes = false;
    /*@Nullable*/ private String nameAtt = null;
    private String asAtt = null;
    private String extraAsAtt = null;
    private SequenceType resultType = SequenceType.ANY_SEQUENCE;
    private SlotManager stackFrameMap;
    private boolean memoFunction = false;
    private String overrideExtensionFunctionAtt = null;
    private boolean overrideExtensionFunction = true;
    private int numberOfParameters = -1;  // -1 means not yet known
    private int numberOfOptionalParameters = -1;  // -1 means not yet known
    private UserFunction compiledFunction;
    private Visibility visibility = Visibility.UNDEFINED;
    private FunctionStreamability streamability;
    private UserFunction.Determinism determinism = UserFunction.Determinism.PROACTIVE;
    private boolean explaining;
    private boolean updating = false;

    /**
     * Get the corresponding Procedure object that results from the compilation of this
     * StylesheetProcedure
     */
    @Override
    public UserFunction getActor() {
        return compiledFunction;
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

    @Override
    protected void prepareAttributes() {

        if (doneAttributes) {
            return;
        }
        doneAttributes = true;

        AttributeMap atts = attributes();

        overrideExtensionFunctionAtt = null;
        String visibilityAtt = null;
        String cacheAtt = null;
        String newEachTimeAtt = null;
        String streamabilityAtt = null;

        for (AttributeInfo att : atts) {
            NodeName name = att.getNodeName();
            NamespaceUri uri = name.getNamespaceUri();
            String local = name.getLocalPart();
            if (uri.isEmpty()) {
                switch (local) {
                    case "name":
                        nameAtt = Whitespace.trim(att.getValue());
                        assert nameAtt != null;
                        StructuredQName functionName = makeQName(nameAtt, null, "name");
                        if (functionName.hasURI(NamespaceUri.NULL)) {
                            functionName = new StructuredQName("saxon", NamespaceUri.SAXON, functionName.getLocalPart());
                            compileError("Function name must be in a namespace", "XTSE0740");
                        }
                        setObjectName(functionName);
                        break;
                    case "as":
                        asAtt = att.getValue();
                        break;
                    case "visibility":
                        visibilityAtt = Whitespace.trim(att.getValue());
                        break;
                    case "streamability":
                        streamabilityAtt = Whitespace.trim(att.getValue());
                        break;
                    case "override":
                        String overrideAtt = Whitespace.trim(att.getValue());
                        boolean override = processBooleanAttribute("override", overrideAtt);
                        if (overrideExtensionFunctionAtt != null) {
                            if (override != overrideExtensionFunction) {
                                compileError("Attributes override-extension-function and override are both used, but do not match", "XTSE0020");
                            }
                        } else {
                            overrideExtensionFunctionAtt = overrideAtt;
                            overrideExtensionFunction = override;
                        }
                        issueWarning("The xsl:function/@override attribute is deprecated; use override-extension-function", SaxonErrorCode.SXWN9014);
                        break;
                    case "override-extension-function":
                        String overrideExtAtt = Whitespace.trim(att.getValue());
                        boolean overrideExt = processBooleanAttribute("override-extension-function", overrideExtAtt);
                        if (overrideExtensionFunctionAtt != null) {
                            if (overrideExt != overrideExtensionFunction) {
                                compileError("Attributes override-extension-function and override are both used, but do not match", "XTSE0020");
                            }
                        } else {
                            overrideExtensionFunctionAtt = overrideExtAtt;
                            overrideExtensionFunction = overrideExt;
                        }
                        break;
                    case "cache":
                        cacheAtt = Whitespace.trim(att.getValue());
                        break;
                    case "new-each-time":
                        newEachTimeAtt = Whitespace.trim(att.getValue());
                        break;
                    default:
                        checkUnknownAttribute(name);
                        break;
                }
            } else if (uri.equals(NamespaceUri.SAXON)) {
                if (isExtensionAttributeAllowed(att.getNodeName().getDisplayName())) {
                    if (local.equals("memo-function")) {
                        issueWarning("saxon:memo-function is deprecated: use cache='yes'", SaxonErrorCode.SXWN9014);
                        if (getConfiguration().isLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION)) {
                            memoFunction = processBooleanAttribute("saxon:memo-function", att.getValue());
                        }
                    } else if (local.equals("as")) {
                        isExtensionAttributeAllowed(name.getDisplayName());
                        extraAsAtt = att.getValue();
                    } else if (local.equals("explain") && isYes(Whitespace.trim(att.getValue()))) {
                        explaining = true;
                    }
                }
            } else if (uri.equals(NamespaceUri.IXSL)) {
                if (isExtensionAttributeAllowed(att.getNodeName().getDisplayName())) {
                    if (local.equals("updating")) {
                        if (getConfiguration().isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XSLT)) {
                            updating = processBooleanAttribute("ixsl:updating", att.getValue());
                        }
                    }
                }
            } else {
                checkUnknownAttribute(name);
            }
        }

        if (nameAtt == null) {
            reportAbsence("name");
            nameAtt = "xsl:unnamed-function-" + generateId();
        }

        if (asAtt != null) {
            try {
                resultType = makeSequenceType(asAtt);
            } catch (XPathException e) {
                compileErrorInAttribute(e, "as");
            }
        }

        if (extraAsAtt != null) {
            SequenceType extraResultType = null;
            try {
                extraResultType = makeExtendedSequenceType(extraAsAtt);
            } catch (XPathException e) {
                compileErrorInAttribute(e, "saxon:as");
                extraResultType = resultType;
            }
            if (asAtt != null) {
                Affinity rel = getConfiguration().getTypeHierarchy().sequenceTypeRelationship(extraResultType, resultType);
                if (rel == Affinity.SAME_TYPE || rel == Affinity.SUBSUMED_BY) {
                    resultType = extraResultType;
                } else {
                    compileErrorInAttribute("When both are present, @saxon:as must be a subtype of @as", "SXER7TBA", "saxon:as");
                }
            } else {
                resultType = extraResultType;
            }
        }

        if (visibilityAtt == null) {
            visibility = Visibility.PRIVATE;
        } else {
            visibility = interpretVisibilityValue(visibilityAtt, "");
        }

        if (streamabilityAtt == null) {
            streamability = FunctionStreamability.UNCLASSIFIED;
        } else {
            streamability = getStreamabilityValue(streamabilityAtt);
            if (streamability.isStreaming()) {
                boolean streamable = processStreamableAtt("yes");
                if (!streamable) {
                    streamability = FunctionStreamability.UNCLASSIFIED;
                }
            }
        }

        if (newEachTimeAtt != null) {
            if ("maybe".equals(newEachTimeAtt)) {
                determinism = UserFunction.Determinism.ELIDABLE;
            } else {
                boolean b = processBooleanAttribute("new-each-time", newEachTimeAtt);
                determinism = b ? UserFunction.Determinism.PROACTIVE : UserFunction.Determinism.DETERMINISTIC;
            }
        }

        boolean cache = false;
        if (cacheAtt != null) {
            cache = processBooleanAttribute("cache", cacheAtt);
        }

        if (determinism == UserFunction.Determinism.DETERMINISTIC || cache) {
            memoFunction = true;
        }
    }

    private FunctionStreamability getStreamabilityValue(String s) {
        if (s.contains(":")) {
            // QNames are allowed but not recognized by Saxon
            makeQName(s, null, "streamability");
            return FunctionStreamability.UNCLASSIFIED;
        }
        try {
            return FunctionStreamability.of(s);
        } catch (IllegalArgumentException ill) {
            invalidAttribute("streamability", "unclassified|absorbing|inspection|filter|shallow-descent|deep-descent|ascent");
            return null;
        }
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     * If there is no name, the value will be -1.
     */

    /*@NotNull*/
    @Override
    public StructuredQName getObjectName() {
        StructuredQName qn = super.getObjectName();
        if (qn == null) {
            nameAtt = Whitespace.trim(getAttributeValue(NamespaceUri.NULL, "name"));
            if (nameAtt == null) {
                return new StructuredQName("saxon", NamespaceUri.SAXON, "badly-named-function" + generateId());
            }
            qn = makeQName(nameAtt, null, "name");
            setObjectName(qn);
        }
        return qn;
    }

    /**
     * Determine whether this type of element is allowed to contain a template-body.
     *
     * @return true: yes, it may contain a general template-body
     */

    @Override
    protected boolean mayContainSequenceConstructor() {
        return true;
    }

    @Override
    protected boolean mayContainParam() {
        return true;
    }

    /**
     * Specify that xsl:param is a permitted child
     */

    @Override
    protected boolean isPermittedChild(StyleElement child) {
        return child instanceof XSLLocalParam;
    }

    @Override
    public Visibility getVisibility() {
        if (visibility == Visibility.UNDEFINED) {
            String vAtt = getAttributeValue(NamespaceUri.NULL, "visibility");
            return vAtt == null ? Visibility.PRIVATE : interpretVisibilityValue(Whitespace.trim(vAtt), "");
        }
        return visibility;
    }

    @Override
    public SymbolicName.F getSymbolicName() {
        return new SymbolicName.F(getObjectName(), getNumberOfParameters());
    }

    @Override
    public void checkCompatibility(Component component) {
        if (compiledFunction == null) {
            getCompiledFunction();
        }

        TypeHierarchy th = getConfiguration().getTypeHierarchy();
        UserFunction other = (UserFunction) component.getActor();
        if (!compiledFunction.getSymbolicName().equals(other.getSymbolicName())) {
            // Can't happen
            compileError("The overriding xsl:function " + nameAtt + " does not match the overridden function: " +
                    "the function name/arity does not match", "XTSE3070");
        }
        if (!compiledFunction.getDeclaredResultType().isSameType(other.getDeclaredResultType(), th)) {
            compileError("The overriding xsl:function " + nameAtt + " does not match the overridden function: " +
                    "the return type does not match", "XTSE3070");
        }
        if (!compiledFunction.getDeclaredStreamability().equals(other.getDeclaredStreamability())) {
            compileError("The overriding xsl:function " + nameAtt + " does not match the overridden function: " +
                    "the streamability category does not match", "XTSE3070");
        }
        if (!compiledFunction.getDeterminism().equals(other.getDeterminism())) {
            compileError("The overriding xsl:function " + nameAtt + " does not match the overridden function: " +
                    "the new-each-time attribute does not match", "XTSE3070");
        }

        for (int i = 0; i < getNumberOfParameters(); i++) {
            if (!compiledFunction.getArgumentType(i).isSameType(other.getArgumentType(i), th)) {
                compileError("The overriding xsl:function " + nameAtt + " does not match the overridden function: " +
                        "the type of the " + RoleDiagnostic.ordinal(i + 1) + " argument does not match", "XTSE3070");
            }
        }
    }

    /**
     * Is override-extension-function="yes"?.
     *
     * @return true if override-extension-function="yes" was specified, otherwise false
     */

    public boolean isOverrideExtensionFunction() {
        if (overrideExtensionFunctionAtt == null) {
            // this is a forwards reference
            prepareAttributes();
        }
        return overrideExtensionFunction;
    }

    public boolean isUpdating() {
        return updating;
    }

    @Override
    public void index(ComponentDeclaration decl, PrincipalStylesheetModule top) {
        //getSkeletonCompiledFunction();
        getCompiledFunction();
        top.indexFunction(decl);
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {

        stackFrameMap = getConfiguration().makeSlotManager();

        // check the element is at the top level of the stylesheet

        checkTopLevel("XTSE0010", true);
        int arity = getNumberOfParameters();
        if (arity == 0 && streamability != FunctionStreamability.UNCLASSIFIED) {
            compileError("A function with no arguments must have streamability=unclassified", "XTSE3155");
        }

        int maxArity = getNumberOfParameters();
        int minArity = maxArity - getNumberOfOptionalParameters();
        if (minArity <= 1 && maxArity >= 1) {
            SchemaType type = getConfiguration().getSchemaType(getObjectName());
            if (type instanceof PlainType) {
                compileError("Stylesheet function clashes with constructor function for an imported atomic type", "XTSE0770");
            }
        }

    }


    /**
     * Compile the function definition to create an executable representation
     * The compileDeclaration() method has the side-effect of binding
     * all references to the function to the executable representation
     * (a UserFunction object)
     *
     * @throws XPathException if compilation fails
     */

    @Override
    public void compileDeclaration(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        Expression exp = compileSequenceConstructor(compilation, decl, false);
        if (exp == null) {
            exp = Literal.makeEmptySequence();
        } else if (Literal.isEmptySequence(exp)) {
            // no action
        } else {
            if (visibility == Visibility.ABSTRACT) {
                compileError("A function defined with visibility='abstract' must have no body");
            }
            exp = exp.simplify();
        }

        UserFunction fn = getCompiledFunction();
        fn.setBody(exp);
        fn.setStackFrameMap(stackFrameMap);
        bindParameterDefinitions(fn);
        fn.setRetainedStaticContext(makeRetainedStaticContext());
        fn.setOverrideExtensionFunction(overrideExtensionFunction);

        if (compilation.getCompilerInfo().getCodeInjector() != null) {
            compilation.getCompilerInfo().getCodeInjector().process(fn);
        }

        Component overridden = getOverriddenComponent();
        if (overridden != null) {
            checkCompatibility(overridden);
        }
    }

    @Override
    public void optimize(ComponentDeclaration declaration) throws XPathException {
        Expression exp = compiledFunction.getBody();
        ExpressionTool.resetPropertiesWithinSubtree(exp);
        ExpressionVisitor visitor = makeExpressionVisitor();
        Expression exp2 = exp.typeCheck(visitor, ContextItemStaticInfo.ABSENT);

        if (streamability.isStreaming()) {
            visitor.setOptimizeForStreaming(true);
        }
        exp2 = ExpressionTool.optimizeComponentBody(exp2, getCompilation(), visitor, ContextItemStaticInfo.ABSENT, true);

        setInstructionLocation(this, exp2);
        compiledFunction.setBody(exp2);

        // Assess the streamability of the function body
        Optimizer optimizer = visitor.getConfiguration().obtainOptimizer();
        if (streamability.isStreaming()) {
            optimizer.assessFunctionStreamability(this, compiledFunction);
        }

        allocateLocalSlots(exp2);
        if (exp2 != exp) {
            compiledFunction.setBody(exp2);
        }

        OptimizerOptions options = getCompilation().getCompilerInfo().getOptimizerOptions();
        if (options.isSet(OptimizerOptions.TAIL_CALLS) && !streamability.isStreaming()) {
            int tailCalls = ExpressionTool.markTailFunctionCalls(exp2, getObjectName(), getNumberOfParameters());
            if (tailCalls != 0) {
                compiledFunction.setTailRecursive(tailCalls > 0, tailCalls > 1);
                exp2 = compiledFunction.getBody();
                compiledFunction.setBody(new TailCallLoop(compiledFunction, exp2));
            }
        }

        //compiledFunction.computeEvaluationMode();

        if (streamability.isStreaming()) {
            compiledFunction.prepareForStreaming();
        }

        if (explaining) {
            exp2.explain(getConfiguration().getLogger());
        }
    }


    /**
     * Get associated stack frame details.
     *
     * @return the associated SlotManager object
     */

    @Override
    public SlotManager getSlotManager() {
        return stackFrameMap;
    }

    /**
     * Get the type of value returned by this function
     *
     * @return the declared result type, or the inferred result type
     * if this is more precise
     */
    public SequenceType getResultType() {
        if (resultType == null) {
            // may be handling a forwards reference - see hof-038
            String asAtt = getAttributeValue(NamespaceUri.NULL, "as");
            if (asAtt != null) {
                try {
                    resultType = makeSequenceType(asAtt);
                } catch (XPathException err) {
                    // the error will be reported when we get round to processing the function declaration
                }
            }
        }
        return resultType == null ? SequenceType.ANY_SEQUENCE : resultType;
    }

    /**
     * Get the number of parameters declared by this function (that is, its arity).
     *
     * @return the arity of the function
     */

    public int getNumberOfParameters() {
        if (numberOfParameters == -1) {
            numberOfParameters = 0;
            for (NodeInfo child : children()) {
                if (child instanceof XSLLocalParam) {
                    numberOfParameters++;
                } else {
                    return numberOfParameters;
                }
            }
        }
        return numberOfParameters;
    }

    /**
     * Get the number of optional parameters declared by this function
     *
     * @return the arity of the function
     */

    public int getNumberOfOptionalParameters() {
        if (numberOfOptionalParameters == -1) {
            numberOfOptionalParameters = 0;
            for (NodeInfo child : children()) {
                if (child instanceof XSLLocalParam) {
                    String requiredAtt = ((XSLLocalParam) child).getAttributeValue("required");
                    if (requiredAtt != null && isNo(Whitespace.trim(requiredAtt))) {
                        numberOfOptionalParameters++;
                    }
                } else {
                    return numberOfOptionalParameters;
                }
            }
        }
        return numberOfOptionalParameters;
    }

    /**
     * Set the definitions of the parameters in the compiled function, as an array.
     *
     * @param fn the compiled object representing the user-written function
     */

    public void setParameterDefinitions(UserFunction fn) {
        UserFunctionParameter[] params = new UserFunctionParameter[getNumberOfParameters()];
        int count = 0;
        int optional = 0;
        for (NodeInfo node : children()) {
            if (node instanceof XSLLocalParam) {
                UserFunctionParameter param = new UserFunctionParameter();
                params[count] = param;
                param.setRequiredType(((XSLLocalParam) node).getRequiredType());
                param.setVariableQName(((XSLLocalParam) node).getVariableQName());
                param.setSlotNumber(((XSLLocalParam) node).getSlotNumber());
                if (XSLLocalParam.isNo(Whitespace.trim(((XSLLocalParam) node).getAttributeValue("required")))) {
                    optional++;
                    param.setRequired(false);
                }
                if (count == 0 && streamability != FunctionStreamability.UNCLASSIFIED) {
                    param.setFunctionStreamability(streamability);
                }
                count++;
            } else {
                break;
            }
        }
        fn.setParameterDefinitions(params);
        fn.setMinimumArity(count - optional);
    }

    /**
     * For each local parameter definition, fix up all references to the function parameter
     * @param fn the compiled user function
     */

    private void bindParameterDefinitions(UserFunction fn) {
        UserFunctionParameter[] params = fn.getParameterDefinitions();
        int count = 0;
        for (NodeInfo node : children(XSLLocalParam.class::isInstance)) {
            UserFunctionParameter param = params[count++];
            param.setRequiredType(((XSLLocalParam) node).getRequiredType());
            param.setVariableQName(((XSLLocalParam) node).getVariableQName());
            param.setSlotNumber(((XSLLocalParam) node).getSlotNumber());
            ((XSLLocalParam) node).getSourceBinding().fixupBinding(param);
        }
    }

    /**
     * Get the argument types
     *
     * @return the declared types of the arguments
     */

    public SequenceType[] getArgumentTypes() {
        SequenceType[] types = new SequenceType[getNumberOfParameters()];
        int count = 0;
        for (NodeInfo node : children(XSLLocalParam.class::isInstance)) {
            types[count++] = ((XSLLocalParam) node).getRequiredType();
        }
        return types;
    }

    /**
     * Get the compiled function
     *
     * @return the object representing the compiled user-written function
     */

    public UserFunction getCompiledFunction() {
        if (compiledFunction == null) {
            prepareAttributes();
            UserFunction fn = getConfiguration().newUserFunction(memoFunction, streamability);
            fn.setPackageData(getCompilation().getPackageData());
            fn.setFunctionName(getObjectName());
            int maxArity = getNumberOfParameters();
            int minArity = maxArity - getNumberOfOptionalParameters();
            fn.setArityRange(minArity, maxArity);
            setParameterDefinitions(fn);
            fn.setResultType(getResultType());
            fn.setLineNumber(getLineNumber());
            fn.setColumnNumber(getColumnNumber());
            fn.setSystemId(getSystemId());
            fn.obtainDeclaringComponent(this);
            fn.setDeclaredVisibility(getDeclaredVisibility());
            fn.setDeclaredStreamability(streamability);
            fn.setDeterminism(determinism);
            fn.setIxslUpdating(updating);
            List<Annotation> annotations = new ArrayList<>();
            if (memoFunction) {
                annotations.add(new Annotation(new StructuredQName("saxon", NamespaceUri.SAXON, "memo-function")));
            }
            fn.setAnnotations(new AnnotationList(annotations));
            fn.setOverrideExtensionFunction(overrideExtensionFunction);
            compiledFunction = fn;
        }
        return compiledFunction;
    }


}

