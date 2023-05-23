////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;


import net.sf.saxon.Controller;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.TraceableComponent;
import net.sf.saxon.trans.FunctionStreamability;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.Visibility;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * This object represents the compiled form of a user-written function
 * (the source can be either an XSLT stylesheet function or an XQuery function).
 * <p>It is assumed that type-checking, of both the arguments and the results,
 * has been handled at compile time. That is, the expression supplied as the body
 * of the function must be wrapped in code to check or convert the result to the
 * required type, and calls on the function must be wrapped at compile time to check or
 * convert the supplied arguments.</p>
 */

public class UserFunction extends Actor implements Function, ContextOriginator, TraceableComponent {

    public enum Determinism {DETERMINISTIC, PROACTIVE, ELIDABLE}
    private final static int MAX_INLININGS = 100;

    private StructuredQName functionName;  // null for an anonymous function
    private boolean tailCalls = false;
    // indicates that the function contains tail calls, not necessarily recursive ones.
    private boolean tailRecursive = false;
    // indicates that the function contains tail calls on itself
    private UserFunctionParameter[] parameterDefinitions;
    private SequenceType resultType;
    private SequenceType declaredResultType;
    protected Evaluator evaluator = null;
    private boolean isUpdating = false;
    private int inlineable = -1; // 0:no 1:yes -1:don't know
    private int inliningCount = 0;
    private boolean overrideExtensionFunction = true;
    private AnnotationList annotations = AnnotationList.EMPTY;
    private FunctionStreamability declaredStreamability = FunctionStreamability.UNCLASSIFIED;
    private Determinism determinism = Determinism.PROACTIVE;
    private int refCount = 0;


    /**
     * Create a user-defined function (the body must be added later)
     */

    public UserFunction() {
    }


    /**
     * Set the function name
     *
     * @param name the function name
     */

    public void setFunctionName(StructuredQName name) {
        functionName = name;
    }

    /**
     * Get the function name
     *
     * @return the function name, as a StructuredQName. Returns null for an anonymous function
     */

    @Override
    public StructuredQName getFunctionName() {
        return functionName;
    }

    /**
     * Get a description of this function for use in error messages. For named functions, the description
     * is the function name (as a lexical QName). For others, it might be, for example, "inline function",
     * or "partially-applied ends-with function".
     *
     * @return a description of the function for use in error messages
     */
    @Override
    public String getDescription() {
        StructuredQName name = getFunctionName();
        if (name.hasURI(NamespaceConstant.ANONYMOUS)) {
            boolean first = true;
             StringBuilder sb = new StringBuilder("function(");
             for (UserFunctionParameter param : getParameterDefinitions()) {
                 if (first) {
                     first = false;
                 } else {
                     sb.append(", ");
                 }
                 sb.append("$").append(param.getVariableQName().getDisplayName());
             }
             sb.append("){");
             Expression body = getBody();
             if (body == null) {
                 sb.append("...");
             } else {
                 sb.append(body.toShortString());
             }
             sb.append("}");
             return sb.toString();
        } else {
            return name.getDisplayName();
        }
    }

    @Override
    public String getTracingTag() {
        return "xsl:function";
    }

    /**
     * Get the properties of this object to be included in trace messages, by supplying
     * the property values to a supplied consumer function
     *
     * @param consumer the function to which the properties should be supplied, as (property name,
     *                 value) pairs.
     */
    @Override
    public void gatherProperties(BiConsumer<String, Object> consumer) {
        consumer.accept("name", getFunctionName());
        consumer.accept("arity", getArity());
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     */

    @Override
    public StructuredQName getObjectName() {
        return functionName;
    }

    @Override
    public SymbolicName.F getSymbolicName() {
        return new SymbolicName.F(functionName, getArity());
    }


    /**
     * Get the type of the function
     *
     * @return the function type
     */

    @Override
    public FunctionItemType getFunctionItemType() {
        SequenceType[] argTypes = new SequenceType[parameterDefinitions.length];
        for (int i = 0; i < parameterDefinitions.length; i++) {
            UserFunctionParameter ufp = parameterDefinitions[i];
            argTypes[i] = ufp.getRequiredType();
        }
        return new SpecificFunctionType(argTypes, resultType, annotations);
    }

    /**
     * Get the roles of the arguments, for the purposes of streaming
     *
     * @return an array of OperandRole objects, one for each argument
     */
    @Override
    public OperandRole[] getOperandRoles() {
        OperandRole[] roles = new OperandRole[getArity()];
        OperandUsage first = null;
        switch (declaredStreamability) {
            case UNCLASSIFIED:
                SequenceType required = getArgumentType(0);
                first = OperandRole.getTypeDeterminedUsage(required.getPrimaryType());
                break;
            case ABSORBING:
                first = OperandUsage.ABSORPTION;
                break;
            case INSPECTION:
                first = OperandUsage.INSPECTION;
                break;
            case FILTER:
                first = OperandUsage.TRANSMISSION;
                break;
            case SHALLOW_DESCENT:
                first = OperandUsage.TRANSMISSION;
                break;
            case DEEP_DESCENT:
                first = OperandUsage.TRANSMISSION;
                break;
            case ASCENT:
                first = OperandUsage.TRANSMISSION;
                break;
        }
        roles[0] = new OperandRole(0, first, getArgumentType(0));
        for (int i = 1; i < roles.length; i++) {
            SequenceType required = getArgumentType(i);
            roles[i] = new OperandRole(0, OperandRole.getTypeDeterminedUsage(required.getPrimaryType()), required);
        }
        return roles;
    }

    /**
     * Ask whether any of the declared arguments accept nodes without atomizing them
     *
     * @return true if this is the case
     */

    public boolean acceptsNodesWithoutAtomization() {
        for (int i = 0; i < getArity(); i++) {
            ItemType type = getArgumentType(i).getPrimaryType();
            if (type instanceof NodeTest || type == AnyItemType.getInstance()) {
                return true;
            }
        }
        return false;
    }

    public boolean isOverrideExtensionFunction() {
        return overrideExtensionFunction;
    }

    public void setOverrideExtensionFunction(boolean overrideExtensionFunction) {
        this.overrideExtensionFunction = overrideExtensionFunction;
    }

    /**
     * Supply a set of annotations
     *
     * @param list the new set of annotations, which will replace any previous annotations on the function
     */

    public void setAnnotations(AnnotationList list) {
        this.annotations = Objects.requireNonNull(list);
    }

    /**
     * Get the annotations defined on this function
     * @return the list of annotations defined on this function
     */

    @Override
    public AnnotationList getAnnotations() {
        return annotations;
    }

    /**
     * Set the determinism of the function. Corresponds to the XSLT 3.0 values new-each-time=yes|no|maybe
     *
     * @param determinism the determinism value for the function
     */

    public void setDeterminism(Determinism determinism) {
        this.determinism = determinism;
    }

    /**
     * Get the determinism of the function.  Corresponds to the XSLT 3.0 values new-each-time=yes|no|maybe
     *
     * @return the determinism value for the function
     */

    public Determinism getDeterminism() {
        return determinism;
    }

    /**
     * Determine the preferred evaluation mode for this function
     */

    public void computeEvaluationMode() {
        if (tailRecursive) {
            // If this function contains tail calls, we evaluate it eagerly, because
            // the caller needs to know whether a tail call was returned or not: if we
            // return a Closure, the tail call escapes into the wild and can reappear anywhere...
            evaluator = ExpressionTool.eagerEvaluator(getBody());
        } else {
            evaluator = ExpressionTool.lazyEvaluator(getBody(), true);
        }
    }

    /**
     * Ask whether the function can be inlined
     *
     * @return true (yes), false (no), or null (don't know)
     */

    /*@Nullable*/
    public Boolean isInlineable() {
        if (inlineable != -1) {
            return inlineable > 0 && inliningCount < MAX_INLININGS;
        }
        if (body == null) {
            // bug 2226
            return null;
        }
        if (body.hasSpecialProperty(StaticProperty.HAS_SIDE_EFFECTS) || tailCalls) {
            // This is mainly to handle current-output-uri()
            return false;
        }
        Component component = getDeclaringComponent();
        if (component != null) {
            Visibility visibility = getDeclaringComponent().getVisibility();
            if (visibility == Visibility.PRIVATE || visibility == Visibility.FINAL) {
                if (inlineable < 0) {
                    return null;
                } else {
                    return inlineable > 0;
                }
            } else {
                return false;
            }
        } else {
            return null;
        }
    }


    /**
     * Say whether this function can be inlined
     *
     * @param inlineable true or false
     */

    public void setInlineable(boolean inlineable) {
        this.inlineable = inlineable ? 1 : 0;
    }

    /**
     * Indicate that the function is being inlined, incrementing a count of how
     * many times it has been inlined
     */

    public void markAsInlined() {
        inliningCount++;
    }

    /**
     * Set the definitions of the declared parameters for this function
     *
     * @param params an array of parameter definitions
     */

    public void setParameterDefinitions(UserFunctionParameter[] params) {
        parameterDefinitions = params;
    }

    /**
     * Get the definitions of the declared parameters for this function
     *
     * @return an array of parameter definitions
     */

    public UserFunctionParameter[] getParameterDefinitions() {
        return parameterDefinitions;
    }

    /**
     * Set the declared result type of the function
     *
     * @param resultType the declared return type
     */

    public void setResultType(SequenceType resultType) {
        this.declaredResultType = resultType;
        this.resultType = resultType;
    }

    /**
     * Indicate whether the function contains a tail call
     *
     * @param tailCalls          true if the function contains a tail call (on any function)
     * @param recursiveTailCalls true if the function contains a tail call (on itself)
     */

    public void setTailRecursive(boolean tailCalls, boolean recursiveTailCalls) {
        this.tailCalls = tailCalls;
        tailRecursive = recursiveTailCalls;
    }

    /**
     * Determine whether the function contains tail calls (on this or other functions)
     *
     * @return true if the function contains tail calls
     */

    public boolean containsTailCalls() {
        return tailCalls;
    }

    /**
     * Determine whether the function contains a tail call, calling itself
     *
     * @return true if the function contains a directly-recursive tail call
     */

    public boolean isTailRecursive() {
        return tailRecursive;
    }

    /**
     * Set whether this is an updating function (as defined in XQuery Update)
     *
     * @param isUpdating true if this is an updating function
     */

    public void setUpdating(boolean isUpdating) {
        this.isUpdating = isUpdating;
    }

    /**
     * Ask whether this is an updating function (as defined in XQuery Update)
     *
     * @return true if this is an updating function
     */

    public boolean isUpdating() {
        return isUpdating;
    }

    /**
     * Set the declared streamability (XSLT 3.0 attribute)
     *
     * @param streamability the declared streamability (defaults to "unclassified")
     */

    public void setDeclaredStreamability(FunctionStreamability streamability) {
        this.declaredStreamability = streamability == null ? FunctionStreamability.UNCLASSIFIED : streamability;
    }

    /**
     * Get the declared streamability (XSLT 3.0 attribute)
     *
     * @return the declared streamability (defaults to "unclassified")
     */

    public FunctionStreamability getDeclaredStreamability() {
        return this.declaredStreamability;
    }

    /**
     * Get the type of value returned by this function
     *
     * @return the declared result type, or the inferred result type
     * if this is more precise
     */

    public SequenceType getResultType() {
        if (resultType == SequenceType.ANY_SEQUENCE && getBody() != null) {
            // see if we can infer a more precise result type. We don't do this if the function contains
            // calls on further functions, to prevent infinite regress.
            if (!containsUserFunctionCalls(getBody())) {
                resultType = SequenceType.makeSequenceType(
                        getBody().getItemType(), getBody().getCardinality());
            }
        }
        return resultType;
    }

    /**
     * Get the declared result type
     *
     * @return the declared result type
     */

    public SequenceType getDeclaredResultType() {
        return declaredResultType;
    }

    /**
     * Determine whether a given expression contains calls on user-defined functions
     *
     * @param exp the expression to be tested
     * @return true if the expression contains calls to user functions.
     */

    private static boolean containsUserFunctionCalls(Expression exp) {
        if (exp instanceof UserFunctionCall) {
            return true;
        }
        for (Operand o : exp.operands()) {
            if (containsUserFunctionCalls(o.getChildExpression())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the required types of an argument to this function
     *
     * @param n identifies the argument in question, starting at 0
     * @return a SequenceType object, indicating the required type of the argument
     */

    public SequenceType getArgumentType(int n) {
        return parameterDefinitions[n].getRequiredType();
    }

    /**
     * Get the evaluation mode. The evaluation mode will be computed if this has not already been done
     *
     * @return the computed evaluation mode
     */

    public Evaluator getEvaluator() {
        if (evaluator == null) {
            computeEvaluationMode();
        }
        return evaluator;
    }

    /**
     * Set the evaluation mode. (Used when reloading a saved package)
     *
     * @param mode the evaluation mode
     */

    public void setEvaluationMode(EvaluationMode mode) {
        evaluator = mode.getEvaluator();
    }

    /**
     * Get the arity of this function
     *
     * @return the number of arguments
     */

    @Override
    public int getArity() {
        return parameterDefinitions.length;
    }

    /**
     * Ask whether this function is a memo function
     *
     * @return false (overridden in a subclass)
     */

    public boolean isMemoFunction() {
        return false;
    }

    public void typeCheck(ExpressionVisitor visitor) throws XPathException {
        Expression exp = getBody();
        if (exp instanceof ValueOf
                && ((ValueOf)exp).getSelect().getItemType().isAtomicType()
                && declaredResultType.getPrimaryType().isAtomicType()
                && declaredResultType.getPrimaryType() != BuiltInAtomicType.STRING) {
            visitor.getStaticContext().issueWarning(
                    "A function that computes atomic values should use xsl:sequence rather than xsl:value-of",
                    getLocation());
        }
        ExpressionTool.resetPropertiesWithinSubtree(exp);
        Expression exp2 = exp;
        try {
            // We've already done the typecheck of each XPath expression, but it's worth doing again at this
            // level because we have more information now.
            ContextItemStaticInfo info = ContextItemStaticInfo.ABSENT;
            exp2 = exp.typeCheck(visitor, info);
            if (resultType != null) {
                RoleDiagnostic role =
                        new RoleDiagnostic(RoleDiagnostic.FUNCTION_RESULT,
                                           functionName == null ? "" : functionName.getDisplayName() + "#" + getArity(), 0);
                role.setErrorCode(getPackageData().isXSLT() && getFunctionName() != null ? "XTTE0780" : "XPTY0004");
                exp2 = visitor.getConfiguration().getTypeChecker(false).staticTypeCheck(exp2, resultType, role, visitor);
            }
        } catch (XPathException err) {
            err.maybeSetLocation(getLocation());
            throw err;
        }
        if (exp2 != exp) {
            setBody(exp2);
        }
    }

    /**
     * Create a context for evaluating this function
     *
     * @param oldContext the existing context of the caller
     * @param originator
     * @return a new context which should be supplied to the call() method.
     */

    @Override
    public XPathContextMajor makeNewContext(XPathContext oldContext, ContextOriginator originator) {
        XPathContextMajor c2 = oldContext.newCleanContext();
        //c2.setReceiver(oldContext.getReceiver());
        c2.setTemporaryOutputState(StandardNames.XSL_FUNCTION);
        c2.setCurrentOutputUri(null);
        c2.setCurrentComponent(getDeclaringComponent());  // default value for the caller to override if necessary
        c2.setOrigin(originator);
        return c2;
    }


    /**
     * Call this function to return a value.
     *
     * @param context    This provides the run-time context for evaluating the function. This should be created
     *                   using {@link Function#makeNewContext(XPathContext, ContextOriginator)}. It must be an instance of XPathContextMajor.
     * @param actualArgs the arguments supplied to the function. These must have the correct
     *                   types required by the function signature (it is the caller's responsibility to check this).
     *                   It is acceptable to supply a {@link net.sf.saxon.value.Closure} to represent a value whose
     *                   evaluation will be delayed until it is needed. The array must be the correct size to match
     *                   the number of arguments: again, it is the caller's responsibility to check this.
     * @return a Value representing the result of the function.
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs while evaluating the function
     */

    @Override
    public Sequence call(XPathContext context, Sequence[] actualArgs)
            throws XPathException {
//        long start = System.nanoTime();
        if (evaluator == null) {
            // should have been done at compile time
            computeEvaluationMode();
        }

        XPathContextMajor c2 = (XPathContextMajor) context;
        c2.setStackFrame(getStackFrameMap(), actualArgs);
        Sequence result;
        try {
            result = evaluator.evaluate(getBody(), c2);
        } catch (XPathException err) {
            err.maybeSetLocation(getLocation());
            err.maybeSetContext(c2);
            throw err;
        } catch (Exception err2) {
            String message = "Internal error evaluating function "
                    + (functionName == null ? "(unnamed)" : functionName.getDisplayName())
                    + (getLineNumber() > 0 ? " at line " + getLineNumber() : "")
                    + (getSystemId() != null ? " in module " + getSystemId() : "");
            throw new RuntimeException(message, err2);
        }
//        result = result.materialize();
//        long end = System.nanoTime();
//        Instrumentation.count(functionName.getLocalPart());
//        Instrumentation.count(functionName.getLocalPart() + "--nanosecs", end - start);
        return result;
    }

    /**
     * Call this function in "push" mode, writing the results to the current output destination.
     *
     * @param context    This provides the run-time context for evaluating the function. It is the caller's
     *                   responsibility to allocate a "clean" context for the function to use; the context that is provided
     *                   will be overwritten by the function.
     * @param actualArgs the arguments supplied to the function. These must have the correct
     *                   types required by the function signature (it is the caller's responsibility to check this).
     *                   It is acceptable to supply a {@link net.sf.saxon.value.Closure} to represent a value whose
     *                   evaluation will be delayed until it is needed. The array must be the correct size to match
     *                   the number of arguments: again, it is the caller's responsibility to check this.
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs while evaluating the function
     */

    public void process(XPathContextMajor context, Sequence[] actualArgs, Outputter output)
            throws XPathException {
        context.setStackFrame(getStackFrameMap(), actualArgs);
        getBody().process(output, context);
    }

    /**
     * Call this function. This method allows an XQuery function to be called directly from a Java
     * application. It creates the environment needed to achieve this
     *
     * @param actualArgs the arguments supplied to the function. These must have the correct
     *                   types required by the function signature (it is the caller's responsibility to check this).
     *                   It is acceptable to supply a {@link net.sf.saxon.value.Closure} to represent a value whose
     *                   evaluation will be delayed until it is needed. The array must be the correct size to match
     *                   the number of arguments: again, it is the caller's responsibility to check this.
     * @param controller This provides the run-time context for evaluating the function. A Controller
     *                   may be obtained by calling {@link net.sf.saxon.query.XQueryExpression#newController}. This may
     *                   be used for a series of calls on functions defined in the same module as the XQueryExpression.
     * @return a Value representing the result of the function.
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs while evaluating the function.
     */

    public Sequence call(Sequence[] actualArgs, Controller controller) throws XPathException {
        return call(controller.newXPathContext(), actualArgs);
    }

    /**
     * Call an updating function.
     *
     * @param actualArgs the arguments supplied to the function. These must have the correct
     *                   types required by the function signature (it is the caller's responsibility to check this).
     *                   It is acceptable to supply a {@link net.sf.saxon.value.Closure} to represent a value whose
     *                   evaluation will be delayed until it is needed. The array must be the correct size to match
     *                   the number of arguments: again, it is the caller's responsibility to check this.
     * @param context    the dynamic evaluation context
     * @param pul        the pending updates list, to which the function's update actions are to be added.
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs while evaluating the function.
     */

    public void callUpdating(Sequence[] actualArgs, XPathContextMajor context, PendingUpdateList pul)
            throws XPathException {
        context.setStackFrame(getStackFrameMap(), actualArgs);
        try {
            getBody().evaluatePendingUpdates(context, pul);
        } catch (XPathException err) {
            err.maybeSetLocation(getLocation());
            throw err;
        }
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied outputstream.
     *
     * @param presenter the expression presenter used to display the structure
     */
    @Override
    public void export(ExpressionPresenter presenter) throws XPathException {
        presenter.startElement("function");
        if (getFunctionName() != null) {
            presenter.emitAttribute("name", getFunctionName());
            presenter.emitAttribute("line", getLineNumber() + "");
            presenter.emitAttribute("module", getSystemId());
            presenter.emitAttribute("eval", getEvaluator().getEvaluationMode().getCode() + "");
        }
        String flags = "";
        if (determinism == Determinism.PROACTIVE) {
            flags += "p";
        } else if (determinism == Determinism.ELIDABLE) {
            flags += "e";
        } else {
            flags += "d";
        }
        if (isMemoFunction()) {
            flags += "m";
        }
        switch (declaredStreamability) {
            case UNCLASSIFIED:
                flags += "U";
                break;
            case ABSORBING:
                flags += "A";
                break;
            case INSPECTION:
                flags += "I";
                break;
            case FILTER:
                flags += "F";
                break;
            case SHALLOW_DESCENT:
                flags += "S";
                break;
            case DEEP_DESCENT:
                flags += "D";
                break;
            case ASCENT:
                flags += "C";
                break;
        }
        presenter.emitAttribute("flags", flags);
        presenter.emitAttribute("as", getDeclaredResultType().toAlphaCode());
        presenter.emitAttribute("slots", getStackFrameMap().getNumberOfVariables() + "");
        for (UserFunctionParameter p : parameterDefinitions) {
            presenter.startElement("arg");
            presenter.emitAttribute("name", p.getVariableQName());
            presenter.emitAttribute("as", p.getRequiredType().toAlphaCode());
            presenter.endElement();
        }
        presenter.setChildRole("body");
        getBody().export(presenter);
        presenter.endElement();
    }

    @Override
    public boolean isExportable() {
        if (!(getPackageData() instanceof StylesheetPackage)) {
            // this must be an imported XQuery function
            return false;
        }
        return refCount > 0 ||
                (getDeclaredVisibility() != null && getDeclaredVisibility() != Visibility.PRIVATE) ||
                ((StylesheetPackage) getPackageData()).isRetainUnusedFunctions();
    }

    @Override
    public boolean isTrustedResultType() {
        return false;
    }

//    /**
//     * Get an iterator over all the items in the sequence (that is, the singleton sequence
//     * consisting of this function item)
//     *
//     * @return an iterator over all the items
//     */
//    public SingletonIterator<Function> iterate() {
//        return new SingletonIterator<>(this);
//    }

    /**
     * Ask whether this function item is a map
     *
     * @return true if this function item is a map, otherwise false
     */
    @Override
    public boolean isMap() {
        return false;
    }

    /**
     * Ask whether this function item is an array
     *
     * @return true if this function item is an array, otherwise false
     */
    @Override
    public boolean isArray() {
        return false;
    }

    /**
     * Test whether this FunctionItem is deep-equal to another function item,
     * under the rules of the deep-equal function
     *
     * @param other    the other function item
     * @param context  the dynamic evaluation context
     * @param comparer the object to perform the comparison
     * @param flags    options for how the comparison is performed
     * @return true if the two function items are deep-equal
     * @throws net.sf.saxon.trans.XPathException if the comparison cannot be performed
     */
    @Override
    public boolean deepEquals(Function other, XPathContext context, AtomicComparer comparer, int flags) throws XPathException {
        XPathException err = new XPathException("Cannot compare functions using deep-equal", "FOTY0015");
        err.setIsTypeError(true);
        err.setXPathContext(context);
        throw err;
    }

    /**
     * Get the n'th item in the value, counting from 0
     *
     * @param n the index of the required item, with 0 representing the first item in the sequence
     * @return the n'th item if it exists, or null otherwise
     */
    @Override
    public Function itemAt(int n) {
        return n == 0 ? this : null;
    }

    /**
     * Get a subsequence of the value
     *
     * @param start  the index of the first item to be included in the result, counting from zero.
     *               A negative value is taken as zero. If the value is beyond the end of the sequence, an empty
     *               sequence is returned
     * @param length the number of items to be included in the result. Specify Integer.MAX_VALUE to
     *               get the subsequence up to the end of the base sequence. If the value is negative, an empty sequence
     *               is returned. If the value goes off the end of the sequence, the result returns items up to the end
     *               of the sequence
     * @return the required subsequence.
     */
    @Override
    public GroundedValue subsequence(int start, int length) {
        return start <= 0 && (start + length) > 0 ? this : EmptySequence.getInstance();
    }

    /**
     * Get the size of the value (the number of items)
     *
     * @return the number of items in the sequence
     */
    @Override
    public int getLength() {
        return 1;
    }

    /**
     * Get the effective boolean value of this sequence
     *
     * @return the effective boolean value
     * @throws net.sf.saxon.trans.XPathException if the sequence has no effective boolean value (for example a sequence of two integers)
     */
    @Override
    public boolean effectiveBooleanValue() throws XPathException {
        return ExpressionTool.effectiveBooleanValue(this);
    }

    /**
     * Reduce the sequence to its simplest form. If the value is an empty sequence, the result will be
     * EmptySequence.getInstance(). If the value is a single atomic value, the result will be an instance
     * of AtomicValue. If the value is a single item of any other kind, the result will be an instance
     * of One. Otherwise, the result will typically be unchanged.
     *
     * @return the simplified sequence
     */
    @Override
    public UserFunction reduce() {
        return this;
    }

    /**
     * Get the first item in the sequence. Differs from the superclass {@link net.sf.saxon.om.Sequence} in that
     * no exception is thrown.
     *
     * @return the first item in the sequence if there is one, or null if the sequence
     * is empty
     */
    @Override
    public UserFunction head() {
        return this;
    }

    /**
     * Get the value of the item as a string. For nodes, this is the string value of the
     * node as defined in the XPath 2.0 data model, except that all nodes are treated as being
     * untyped: it is not an error to get the string value of a node with a complex type.
     * For atomic values, the method returns the result of casting the atomic value to a string.
     * <p>If the calling code can handle any CharSequence, the method {@link #getStringValueCS} should
     * be used. If the caller requires a string, this method is preferred.</p>
     *
     * @return the string value of the item
     * @throws UnsupportedOperationException if the item is a function item (an unchecked exception
     *                                       is used here to avoid introducing exception handling to a large number of paths where it is not
     *                                       needed)
     * @see #getStringValueCS
     * @since 8.4
     */
    @Override
    public String getStringValue() {
        throw new UnsupportedOperationException("A function has no string value");
    }

    /**
     * Get the string value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String. The method satisfies the rule that
     * <code>X.getStringValueCS().toString()</code> returns a string that is equal to
     * <code>X.getStringValue()</code>.
     * <p>Note that two CharSequence values of different types should not be compared using equals(), and
     * for the same reason they should not be used as a key in a hash table.</p>
     * <p>If the calling code can handle any CharSequence, this method should
     * be used. If the caller requires a string, the {@link #getStringValue} method is preferred.</p>
     *
     * @return the string value of the item
     * @throws UnsupportedOperationException if the item is a function item (an unchecked exception
     *                                       is used here to avoid introducing exception handling to a large number of paths where it is not
     *                                       needed)
     * @see #getStringValue
     * @since 8.4
     */
    @Override
    public CharSequence getStringValueCS() {
        return getStringValue();
    }

    /**
     * Atomize the item.
     *
     * @return the result of atomization
     * @throws net.sf.saxon.trans.XPathException if atomization is not allowed
     *                                           for this kind of item
     */
    @Override
    public AtomicSequence atomize() throws XPathException {
        throw new XPathException("Functions cannot be atomized", "FOTY0013");
    }

    public void incrementReferenceCount() {
        refCount++;
    }

    public int getReferenceCount() {
        return refCount;
    }

    public void prepareForStreaming() throws XPathException {
        // method is defined in streamable subclass
    }

}


