////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.expr.instruct.UserFunctionParameter;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.registry.FunctionDefinition;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.*;
import net.sf.saxon.tree.jiter.PairIterator;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

/**
 * A user-defined function in an XQuery module
 */

public class XQueryFunction implements Declaration, Location, FunctionDefinition {
    private StructuredQName functionName;
    private final List<UserFunctionParameter> parameters;
    private SequenceType resultType;
    private Expression body = null;
    private List<UserFunctionResolvable> references = new ArrayList<>(10);
    private Location location;
    /*@Nullable*/ private UserFunction compiledFunction = null;
    private boolean memoFunction;
    private NamespaceResolver namespaceResolver;
    private QueryModule staticContext;
    private boolean updating = false;
    private AnnotationList annotations = AnnotationList.EMPTY;
    private int mandatoryParams = 0;

    /**
     * Create an XQuery function
     */

    public XQueryFunction() {
        parameters = new ArrayList<>(8);
    }

    /**
     * Get data about the unit of compilation (XQuery module, XSLT package) to which this
     * container belongs
     * @return the package information
     */
    public PackageData getPackageData() {
        return staticContext.getPackageData();
    }

    /**
     * Set the name of the function
     *
     * @param name the name of the function as a StructuredQName object
     */

    public void setFunctionName(StructuredQName name) {
        functionName = name;
    }

    /**
     * Add an argument to the list of parameters
     *
     * @param param the formal declaration of the parameter to be added
     */

    public void addParameter(UserFunctionParameter param) {
        parameters.add(param);
        if (param.getDefaultValueExpression() == null) {
            mandatoryParams++;
        }
    }

    /**
     * Set the required result type of the function
     *
     * @param resultType the declared result type of the function
     */

    public void setResultType(SequenceType resultType) {
        this.resultType = resultType;
    }

    /**
     * Set the body of the function
     *
     * @param body the expression forming the body of the function
     */

    public void setBody(/*@Nullable*/ Expression body) {
        this.body = body;
//        if (body != null) {
//            body.setContainer(this);
//        }
    }

    /**
     * Get the body of the function
     *
     * @return the expression making up the body of the function
     */

    /*@Nullable*/
    public Expression getBody() {
        return body;
    }

    /**
     * Set the location of the source declaration of the function
     *
     * @param location the source location
     */

    public void setLocation(Location location) {
        this.location = location;
    }

    /**
     * Get the name of the function as a structured QName
     *
     * @return the name of the function as a structured QName
     */

    public StructuredQName getFunctionName() {
        return functionName;
    }

    /**
     * Get the name of the function for display in error messages
     *
     * @return the name of the function as a lexical QName
     */

    public String getDisplayName() {
        return functionName.getDisplayName();
    }

    /**
     * Get an identifying key for this function, which incorporates the URI and local part of the
     * function name plus the arity
     *
     * @return an identifying key
     */

    /*@NotNull*/
    public SymbolicName getIdentificationKey() {
        return new SymbolicName.F(functionName, parameters.size());
    }

    /**
     * Construct what the identification key would be for a function with given URI, local name, and arity
     *
     * @param qName the name of the function
     * @param arity the number of arguments
     * @return an identifying key
     */

    public static SymbolicName getIdentificationKey(StructuredQName qName, int arity) {
        return new SymbolicName.F(qName, arity);
    }

    /**
     * Get the result type of the function
     *
     * @return the declared result type
     */

    public SequenceType getResultType() {
        return resultType;
    }

    /**
     * Set the static context for this function
     *
     * @param env the static context for the module in which the function is declared
     */

    public void setStaticContext(QueryModule env) {
        staticContext = env;
    }

    /**
     * Get the static context for this function
     *
     * @return the static context for the module in which the function is declared
     */

    public StaticContext getStaticContext() {
        return staticContext;
    }

    /**
     * Get the declared types of the arguments of this function
     *
     * @return an array, holding the types of the arguments in order
     */

    /*@NotNull*/
    public SequenceType[] getArgumentTypes() {
        SequenceType[] types = new SequenceType[parameters.size()];
        for (int i = 0; i < parameters.size(); i++) {
            types[i] = parameters.get(i).getRequiredType();
        }
        return types;
    }

    /**
     * Get the definitions of the arguments to this function
     *
     * @return an array of UserFunctionParameter objects, one for each argument
     */

    public UserFunctionParameter[] getParameterDefinitions() {
        UserFunctionParameter[] params = new UserFunctionParameter[parameters.size()];
        return parameters.toArray(params);
    }

    /**
     * Get the position in the parameter list of a given parameter name
     * @param name the name of the required parameter
     * @return the position of the parameter in the parameter list, or -1 if absent
     */

    public int getPositionOfParameter(StructuredQName name) {
        int pos = 0;
        for (UserFunctionParameter p : parameters) {
            if (p.getVariableQName().equals(name)) {
                return pos;
            }
            pos++;
        }
        return -1;
    }

    /**
     * Get the name of the Nth parameter, if any
     *
     * @param i the position of the required parameter
     * @return the name (keyword) of the Nth parameter
     */

    public StructuredQName getParameterName(int i) {
        return parameters.get(i).getVariableQName();
    }


    /**
     * Get the default value expression of the Nth parameter, if any
     *
     * @param i the position of the required parameter
     * @return the expression for computing the value of the Nth parameter, or null if there is none
     */

    public Supplier<Expression> getDefaultValueExpression(int i) {
        return parameters.get(i).getDefaultValueExpression();
    }

    /**
     * Set the default value expression of the Nth parameter
     *
     * @param i the position of the required parameter
     * @param exp the expression for computing the value of the Nth parameter
     */

    public void setDefaultValueExpression(int i, Expression exp) {
        parameters.get(i).setDefaultValueExpression(() -> exp);
    }

    /**
     * Get the maximum arity of the function
     * @return the arity (the maximum number of arguments including optional arguments)
     */

    public int getNumberOfParameters() {
        return parameters.size();
    }

    /**
     * Get the minimum arity of the function
     * @return the number of mandatory parameters
     */

    public int getMinimumArity() {
        return mandatoryParams;
    }

    /**
     * Register a call on this function
     *
     * @param ufc a user function call that references this function.
     */

    public void registerReference(UserFunctionResolvable ufc) {
        references.add(ufc);
    }

    /**
     * Set that this is, or is not, a memo function. A memo function remembers the results of calls
     * on the function so that the a subsequent call with the same arguments simply look up the result
     *
     * @param isMemoFunction true if this is a memo function.
     */

    public void setMemoFunction(boolean isMemoFunction) {
        memoFunction = isMemoFunction;
    }

    /**
     * Find out whether this is a memo function
     *
     * @return true if this is a memo function
     */

    public boolean isMemoFunction() {
        return memoFunction;
    }

    /**
     * Set whether this is an updating function (as defined in XQuery Update)
     *
     * @param isUpdating true if this is an updating function
     */

    public void setUpdating(boolean isUpdating) {
        this.updating = isUpdating;
    }

    /**
     * Ask whether this is an updating function (as defined in XQuery Update)
     *
     * @return true if this is an updating function
     */

    public boolean isUpdating() {
        return updating;
    }

    /**
     * Set the annotations on this function
     *
     * @param annotations the annotations, indexed by annotation name
     */

    public void setAnnotations(AnnotationList annotations) {
        this.annotations = annotations;
        if (compiledFunction != null) {
            compiledFunction.setAnnotations(annotations);
        }
        if (annotations.includes(Annotation.UPDATING)) {
            setUpdating(true);
        }
    }

    /**
     * Get the annotations defined on this function
     *
     * @return the list of annotations defined on this function
     */

    public AnnotationList getAnnotations() {
        return annotations;
    }

    /**
     * Ask whether the function has an annotation with a particular name
     * @param name the name of the required annotation
     * @return true if the function has an annotation with this name
     */

    public boolean hasAnnotation(StructuredQName name) {
        return annotations.includes(name);
    }


    /**
     * Ask whether this is a private function (as defined in XQuery 3.0)
     *
     * @return true if this is a private function
     */

    public boolean isPrivate() {
        return hasAnnotation(Annotation.PRIVATE);
    }

    /**
     * Compile this function to create a run-time definition that can be interpreted (note, this
     * has nothing to do with Java code generation)
     *
     * @throws XPathException if errors are found
     */

    public void compile() throws XPathException {
        Configuration config = staticContext.getConfiguration();
        try {
            // If a query function is imported into several modules, then the compile()
            // method will be called once for each importing module. If the compiled
            // function already exists, then this is a repeat call, and the only thing
            // needed is to fix up references to the function from within the importing
            // module.

            if (compiledFunction == null) {
                ExpressionVisitor visitor = ExpressionVisitor.make(staticContext);

                SlotManager map = config.makeSlotManager();
                UserFunctionParameter[] params = getParameterDefinitions();
                for (int i = 0; i < params.length; i++) {
                    params[i].setSlotNumber(i);
                    map.allocateSlotNumber(params[i].getVariableQName(), params[i]);
                    if (params[i].getDefaultValueExpression() != null) {
                        Expression defaultExpr = params[i].getDefaultValueExpression().get();
                        Expression def2 = defaultExpr.typeCheck(visitor, ContextItemStaticInfo.DEFAULT);
                        params[i].setDefaultValueExpression(() -> def2);
                    }
                }

                // type-check the body of the function

                RetainedStaticContext rsc = null;
                try {
                    rsc = getStaticContext().makeRetainedStaticContext();
                    body.setRetainedStaticContext(rsc);

                    body = body.simplify().typeCheck(visitor, ContextItemStaticInfo.ABSENT);

                    // Try to extract new global variables from the body of the function
                    //body = config.getOptimizer().promoteExpressionsToGlobal(body, visitor);

                    //body.setContainer(this);
                    Supplier<RoleDiagnostic> role = () ->
                            new RoleDiagnostic(RoleDiagnostic.FUNCTION_RESULT, functionName.getDisplayName(), 0);
                    body = config.getTypeChecker(false).staticTypeCheck(body, resultType, role, visitor);

                } catch (XPathException e) {
                    e.maybeSetLocation(this);
                    if (e.isReportableStatically()) {
                        throw e;
                    } else {
                        Expression newBody = new ErrorExpression(new XmlProcessingException(e));
                        ExpressionTool.copyLocationInfo(body, newBody);
                        body = newBody;
                    }
                }

                compiledFunction = config.newUserFunction(memoFunction, FunctionStreamability.UNCLASSIFIED);
                compiledFunction.setRetainedStaticContext(rsc);
                compiledFunction.setPackageData(staticContext.getPackageData());
                compiledFunction.setBody(body);
                compiledFunction.setFunctionName(functionName);
                compiledFunction.setParameterDefinitions(params);
                compiledFunction.setResultType(getResultType());
                compiledFunction.setLineNumber(location.getLineNumber());
                compiledFunction.setColumnNumber(location.getColumnNumber());
                compiledFunction.setSystemId(location.getSystemId());
                compiledFunction.setStackFrameMap(map);
                compiledFunction.setUpdating(updating);
                compiledFunction.setAnnotations(annotations);

                if (staticContext.getUserQueryContext().isCompileWithTracing()) {
                    namespaceResolver = staticContext.getNamespaceResolver();
                    staticContext.getCodeInjector().process(compiledFunction);
                    body = compiledFunction.getBody();
//                    ComponentTracer trace = new ComponentTracer(compiledFunction);
//                    trace.setLocation(location);
//                    body = trace;
                }

            }
            // bind all references to this function to the UserFunction object

            fixupReferences();

        } catch (XPathException e) {
            e.maybeSetLocation(this);
            throw e;
        }
    }

    /**
     * Optimize the body of this function
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if execution fails, for example because the function is updating
     *                                           and contains constructs not allowed in an updating function, or vice-versa.
     */

    public void optimize() throws XPathException {
        body.checkForUpdatingSubexpressions();
        if (updating) {
            if (ExpressionTool.isNotAllowedInUpdatingContext(body)) {
                XPathException err = new XPathException(
                        "The body of an updating function must be an updating expression", "XUST0002");
                err.setLocator(body.getLocation());
                throw err;
            }
        } else {
            //body.checkForUpdatingSubexpressions(); 
            if (body.isUpdatingExpression()) {
                XPathException err = new XPathException(
                        "The body of a non-updating function must be a non-updating expression", "XUST0001");
                err.setLocator(body.getLocation());
                throw err;
            }
        }
        ExpressionVisitor visitor = ExpressionVisitor.make(staticContext);
        Optimizer opt = visitor.obtainOptimizer();
        int arity = parameters.size();
        if (opt.isOptionSet(OptimizerOptions.MISCELLANEOUS)) {
            body = body.optimize(visitor, ContextItemStaticInfo.ABSENT);
        }
        body.setParentExpression(null);
        if (opt.isOptionSet(OptimizerOptions.LOOP_LIFTING)) {
            body = LoopLifter.process(body, visitor, ContextItemStaticInfo.ABSENT);
        }
        if (opt.isOptionSet(OptimizerOptions.EXTRACT_GLOBALS)) {
            GlobalVariableManager manager = new GlobalVariableManager() {
                @Override
                public void addGlobalVariable(GlobalVariable variable) throws XPathException {
                    PackageData pd = staticContext.getPackageData();
                    variable.setPackageData(pd);
                    SlotManager sm = pd.getGlobalSlotManager();
                    int slot = sm.allocateSlotNumber(variable.getVariableQName(),  null);
                    variable.compile(staticContext.getExecutable(), slot);
                    pd.addGlobalVariable(variable);
                }

                @Override
                public GlobalVariable getEquivalentVariable(Expression select) {
                    return null;
                }
            };

            // Try to extract new global variables from the body of the function
            Expression b2 = opt.promoteExpressionsToGlobal(body, manager, visitor);
            if (b2 != null) {
                body = body.optimize(visitor, ContextItemStaticInfo.ABSENT);
            }
        }
        
        // mark tail calls within the function body
        if (opt.getOptimizerOptions().isSet(OptimizerOptions.TAIL_CALLS) && !updating) {
            int tailCalls = ExpressionTool.markTailFunctionCalls(body, functionName, arity);
            if (tailCalls != 0) {
                compiledFunction.setBody(body);
                compiledFunction.setTailRecursive(tailCalls > 0, tailCalls > 1);
                body = new TailCallLoop(compiledFunction, body);
            }
        }
        compiledFunction.setBody(body);

        //compiledFunction.computeEvaluationMode();
        ExpressionTool.allocateSlots(body, arity, compiledFunction.getStackFrameMap());

    }

    /**
     * Fix up references to this function
     */

    public void fixupReferences() {
        for (UserFunctionResolvable ufc : references) {
            ufc.setFunction(compiledFunction);
        }
    }

    /**
     * Type-check references to this function
     *
     * @param visitor the expression visitor
     * @throws XPathException if type errors are found
     */

    public void checkReferences(ExpressionVisitor visitor) throws XPathException {
        for (UserFunctionResolvable ufr : references) {
            if (ufr instanceof UserFunctionCall) {
                UserFunctionCall ufc = (UserFunctionCall) ufr;
                ufc.checkFunctionCall(compiledFunction, visitor);
                //ufc.computeArgumentEvaluationModes();
            }
        }

        // clear the list of references, so that more can be added in another module
        references = new ArrayList<>(0);

    }

    /**
     * Produce diagnostic output showing the compiled and optimized expression tree for a function
     *
     * @param out the destination to be used
     * @throws XPathException if things go wrong
     */
    public void explain(/*@NotNull*/ ExpressionPresenter out) throws XPathException {
        out.startElement("declareFunction");
        out.emitAttribute("name", functionName.getDisplayName());
        out.emitAttribute("arity", "" + getNumberOfParameters());
        if (compiledFunction == null) {
            out.emitAttribute("unreferenced", "true");
        } else {
            if (compiledFunction.isMemoFunction()) {
                out.emitAttribute("memo", "true");
            }
            out.emitAttribute("tailRecursive", compiledFunction.isTailRecursive() ? "true" : "false");
            body.export(out);
        }
        out.endElement();
    }

    /**
     * Get the callable compiled function contained within this XQueryFunction definition.
     *
     * @return the compiled function object
     */

    /*@Nullable*/
    public UserFunction getUserFunction() {
        return compiledFunction;
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     * @return the function name
     */

    public StructuredQName getObjectName() {
        return functionName;
    }

    /**
     * Get the system identifier (URI) of the source module containing
     * the instruction. This will generally be an absolute URI. If the system
     * identifier is not known, the method may return null. In some cases, for example
     * where XML external entities are used, the correct system identifier is not
     * always retained.
     */

    @Override
    public String getSystemId() {
        return location.getSystemId();
    }

    /**
     * Get the line number of the instruction in the source stylesheet module.
     * If this is not known, or if the instruction is an artificial one that does
     * not relate to anything in the source code, the value returned may be -1.
     */

    @Override
    public int getLineNumber() {
        return location.getLineNumber();
    }

    /**
     * Return the public identifier for the current document event.
     *
     * @return A string containing the public identifier, or
     * null if none is available.
     * @see #getSystemId
     */
    /*@Nullable*/
    @Override
    public String getPublicId() {
        return null;
    }

    /**
     * Return the column number
     *
     * @return The column number, or -1 if none is available.
     * @see #getLineNumber
     */

    @Override
    public int getColumnNumber() {
        return -1;
    }

    /**
     * Get an immutable copy of this Location object. By default Location objects may be mutable, so they
     * should not be saved for later use. The result of this operation holds the same location information,
     * but in an immutable form.
     */
    @Override
    public Location saveLocation() {
        return this;
    }

    /**
     * Get the namespace context of the instruction. This will not always be available, in which
     * case the method returns null.
     * @return a resolver representing the namespace context for the function
     */

    public NamespaceResolver getNamespaceResolver() {
        return namespaceResolver;
    }

    /**
     * Get the value of a particular property of the instruction. Properties
     * of XSLT instructions are generally known by the name of the stylesheet attribute
     * that defines them.
     *
     * @param name The name of the required property
     * @return The value of the requested property, or null if the property is not available
     */

    /*@Nullable*/
    public Object getProperty(String name) {
        if ("name".equals(name)) {
            return functionName.getDisplayName();
        } else if ("as".equals(name)) {
            return resultType.toString();
        } else {
            return null;
        }
    }

    /**
     * Get an iterator over all the properties available. The values returned by the iterator
     * will be of type String, and each string can be supplied as input to the getProperty()
     * method to retrieve the value of the property.
     * @return the properties available
     */

    /*@NotNull*/
    public Iterator<String> getProperties() {
        return new PairIterator<>("name", "as");
    }

    /**
     * Get the host language (XSLT, XQuery, XPath) used to implement the code in this container
     *
     * @return {@link HostLanguage#XQUERY}
     */

    public HostLanguage getHostLanguage() {
        return HostLanguage.XQUERY;
    }

}

