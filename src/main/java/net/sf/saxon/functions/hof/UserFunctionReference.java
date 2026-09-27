////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.expr.instruct.UserFunctionParameter;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.functions.AbstractFunction;
import net.sf.saxon.functions.IFunctionWithRetainedParameterNames;
import net.sf.saxon.functions.registry.FunctionDefinition;
import net.sf.saxon.om.*;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;

import java.util.Arrays;

/**
 * A UserFunctionReference is an expression in the form local:f#1 where local:f is a user-defined function.
 * Evaluating the expression returns a function item. The reason that UserFunctionReference is treated
 * as an expression rather than a value is that the binding to the actual function may be deferred, for
 * example if the function is overridden in a different package. In addition, if the function is context
 * dependent, the function item that results will have a captured context, captured at the time the
 * UserFunctionReference is evaluated.
 *
 * <p>Also used on some occasions for references to system functions.</p>
 */

public class UserFunctionReference extends Expression
        implements ComponentInvocation, UserFunctionResolvable, IFunctionWithRetainedParameterNames, Callable {

    private final SymbolicName.F functionName;
    private UserFunction nominalTarget;
    private int bindingSlot = -1;
    private int optimizeCounter = 0;
    private int typeCheckCounter = 0;

    public UserFunctionReference(UserFunction target) {
        this.nominalTarget = target;
        this.functionName = target.getSymbolicName();
    }

    public UserFunctionReference(UserFunction target, SymbolicName.F name) {
        // The name of the reference might be a reduced-arity version of the function name, if it has optional params
        this.nominalTarget = target;
        this.functionName = name;
    }

    public UserFunctionReference(SymbolicName.F name) {
        this.functionName = name;
    }

    @Override
    public void setFunction(UserFunction function) {
        if (!function.getSymbolicName().getComponentName().equals(functionName.getComponentName())) {
            throw new IllegalArgumentException("Function name does not match");
        }

        if (function.getMinimumArity() > functionName.getArity() || (function.getArity() < functionName.getArity())) {
            throw new IllegalArgumentException("Function arity does not match");
        }
        this.nominalTarget = function;
    }

    @Override
    public Expression simplify() throws XPathException {
        // if this is an inline function, simplify the body of that function now
        if (nominalTarget.getFunctionName().hasURI(NamespaceUri.ANONYMOUS) && typeCheckCounter == 0) {
            // Prevent recursive simplification
            typeCheckCounter++;
            nominalTarget.setBody(nominalTarget.getBody().simplify());
            typeCheckCounter--;
        }
        return this;
    }

    /**
     * Perform type checking of an expression and its subexpressions. This is the second phase of
     * static optimization.
     * <p>This checks statically that the operands of the expression have
     * the correct type; if necessary it generates code to do run-time type checking or type
     * conversion. A static type error is reported only if execution cannot possibly succeed, that
     * is, if a run-time type error is inevitable. The call may return a modified form of the expression.</p>
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable. However, the types of such functions and
     * variables may not be accurately known if they have not been explicitly declared.</p>
     *
     * @param visitor     an expression visitor
     * @param contextInfo Information available statically about the context item: whether it is (possibly)
     *                    absent; its static type; its streaming posture.
     * @return the original expression, rewritten to perform necessary run-time type checks,
     * and to perform other type-related optimizations
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        // if this is an inline function, typecheck that function now
        //System.err.println("typeCheck " + this);
        if (nominalTarget.getFunctionName().hasURI(NamespaceUri.ANONYMOUS) && typeCheckCounter == 0) {
            // Prevent recursive type-checking: test case -s:misc-HigherOrderFunctions -t:xqhof2
            typeCheckCounter++;
            nominalTarget.typeCheck(visitor);
            typeCheckCounter--;
        }
        return this;
    }

    /**
     * Perform optimisation of an expression and its subexpressions. This is the third and final
     * phase of static optimization.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor     an expression visitor
     * @param contextInfo the static type of "." at the point where this expression is invoked.
     *                    The parameter is set to null if it is known statically that the context item will be undefined.
     *                    If the type of the context item is not known statically, the argument is set to
     *                    {@link Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        // if this is an inline function, optimize that function now
        //System.err.println("optimize " + this);
        if (nominalTarget.getFunctionName().hasURI(NamespaceUri.ANONYMOUS) && optimizeCounter == 0) {
            // Prevent recursive optimization: test case -s:misc-HigherOrderFunctions -t:xqhof2 ; and bug #5054
            optimizeCounter++;
            Expression o;
            o = nominalTarget.getBody().optimize(visitor, ContextItemStaticInfo.ABSENT);
            nominalTarget.setBody(o);
            SlotManager slotManager = visitor.getConfiguration().makeSlotManager();
            for (int i = 0; i < getArity(); i++) {
                UserFunctionParameter param = nominalTarget.getParameterDefinitions()[i];
                slotManager.allocateSlotNumber(param.getVariableQName(), param);
            }
            ExpressionTool.allocateSlots(o, getArity(), slotManager);
            nominalTarget.setStackFrameMap(slotManager);
            optimizeCounter--;
        }
        return this;
    }

    /**
     * Get the binding slot to be used. This is the offset within the binding vector of the containing
     * component where the actual target component is to be found.
     *
     * @return the offset in the binding vector of the containing package where the target component
     * can be found.
     */
    @Override
    public int getBindingSlot() {
        return bindingSlot;
    }

    /**
     * Get the target component if this is known in advance, that is, if the target component
     * is private or final. Otherwise, return null.
     *
     * @return the bound component if the binding has been fixed
     */
    @Override
    public Component getFixedTarget() {
        return nominalTarget.getDeclaringComponent();
    }

    public UserFunction getNominalTarget() {
        return nominalTarget;
    }

    /**
     * Get the names of the parameters in the underlying function definition
     *
     * @return the names of the parameters, in order
     */
    @Override
    public StructuredQName[] getParameterNames() {
        UserFunction uf = getNominalTarget();
        UserFunctionParameter[] params = uf.getParameterDefinitions();
        StructuredQName[] paramNames = new StructuredQName[params.length];
        Arrays.setAll(paramNames, i -> params[i].getVariableQName());
        return paramNames;
    }

    /**
     * Set the binding slot to be used. This is the offset within the binding vector of the containing
     * component where the actual target component is to be found. The target template is not held directly
     * in the invocation instruction/expression itself because it can be overridden in a using package.
     *
     * @param slot the offset in the binding vector of the containing package where the target component
     *             can be found.
     */
    @Override
    public void setBindingSlot(int slot) {
        bindingSlot = slot;
    }

    /**
     * Get the symbolic name of the component that this invocation references
     *
     * @return the symbolic name of the target component
     */
    @Override
    public SymbolicName getSymbolicName() {
        return functionName;
    }

    /**
     * Get the item type of the function item
     *
     * @param th the type hierarchy cache
     * @return the function item's type
     */
    public FunctionItemType getFunctionItemType(TypeHierarchy th) {
        return nominalTarget.getFunctionItemType();
    }

    /**
     * Get the name of the function, or null if it is anonymous
     *
     * @return the function name, or null for an anonymous inline function
     */
    public StructuredQName getFunctionName() {
        return nominalTarget.getFunctionName();
    }

    /**
     * Get the arity of the function
     *
     * @return the number of arguments in the function signature
     */
    public int getArity() {
        return functionName.getArity();
    }

    /**
     * Compute the static cardinality of this expression
     *
     * @return the computed cardinality, as one of the values {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ZERO_OR_ONE},
     * {@link net.sf.saxon.expr.StaticProperty#EXACTLY_ONE}, {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ONE_OR_MORE},
     * {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ZERO_OR_MORE}
     */
    @Override
    protected int computeCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided directly. The other methods will always be available
     * indirectly, using an implementation that relies on one of the other methods.
     *
     * @return the implementation method, for example {@link #ITERATE_METHOD} or {@link #EVALUATE_METHOD} or
     * {@link #PROCESS_METHOD}
     */
    @Override
    public int getImplementationMethod() {
        return EVALUATE_METHOD;
    }

    /**
     * Determine the data type of the expression, if possible. All expression return
     * sequences, in general; this method determines the type of the items within the
     * sequence, assuming that (a) this is known in advance, and (b) it is the same for
     * all items in the sequence.
     * <p>This method should always return a result, though it may be the best approximation
     * that is available at the time.</p>
     *
     * @return a value such as Type.STRING, Type.BOOLEAN, Type.NUMBER,
     * Type.NODE, or Type.ITEM (meaning not known at compile time)
     */
    @Override
    public ItemType getItemType() {
        if (nominalTarget == null) {
            // happens while reloading from a SEF file
            return AnyFunctionType.INSTANCE;
        }
        return nominalTarget.getFunctionItemType();
    }

    /**
     * Compute the special properties of this expression.
     * @return the special properties, as a bit-significant integer
     */

    protected int computeSpecialProperties() {
        return StaticProperty.COMPUTED_FUNCTION;
    }

    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @param contextItemType the static type of the context item
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        return UType.FUNCTION;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @param rebindings variables that need to be re-bound
     * @return the copy of the original expression
     */
    @Override
    public Expression copy(RebindingMap rebindings) {
        UserFunctionReference ref = new UserFunctionReference(nominalTarget, functionName);
        ref.optimizeCounter = optimizeCounter;
        ref.typeCheckCounter = typeCheckCounter;
        return ref;
    }

    /**
     * Evaluate an expression as a single item. This always returns either a single Item or
     * null (denoting the empty sequence). No conversion is done. This method should not be
     * used unless the static type of the expression is a subtype of "item" or "item?": that is,
     * it should not be called if the expression may return a sequence. There is no guarantee that
     * this condition will be detected.
     *
     * @param context The context in which the expression is to be evaluated
     * @return the node or atomic value that results from evaluating the
     * expression; or null to indicate that the result is an empty
     * sequence
     * @throws net.sf.saxon.trans.XPathException if any dynamic error occurs evaluating the
     *                                           expression
     */
    @Override
    public FunctionItem evaluateItem(XPathContext context) throws XPathException {
        return (FunctionItem) makeElaborator().elaborateForItem().eval(context);
    }

    /**
     * Call the Callable.
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences.
     *                  <p>Generally it is advisable, if calling iterate() to process a supplied sequence, to
     *                  call it only once; if the value is required more than once, it should first be converted
     *                  to a {@link GroundedValue} by calling the utility method
     *                  SequenceTool.toGroundedValue().</p>
     *                  <p>If the expected value is a single item, the item should be obtained by calling
     *                  Sequence.head(): it cannot be assumed that the item will be passed as an instance of
     *                  {@link Item} or {@link AtomicValue}.</p>
     *                  <p>It is the caller's responsibility to perform any type conversions required
     *                  to convert arguments to the type expected by the callee. An exception is where
     *                  this Callable is explicitly an argument-converting wrapper around the original
     *                  Callable.</p>
     * @return the result of the evaluation, in the form of a Sequence. It is the responsibility
     * of the callee to ensure that the type of result conforms to the expected result type.
     * @throws XPathException if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public FunctionItem call(XPathContext context, Sequence[] arguments) throws XPathException {
        return evaluateItem(context);
    }

    @Override
    public String getExpressionName() {
        return "UserFunctionReference";
    }

    public String toString() {
        return getFunctionName().getEQName() + "#" + getArity();
    }

    @Override
    public String toShortString() {
        return getFunctionName().getDisplayName() + "#" + getArity();
    }

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        ExpressionPresenter.ExportOptions options = out.getOptions();
        if (nominalTarget.getDeclaringComponent() == null) {
            // This happens for an inline function declared within a static expression, e.g. one
            // that is bound to a static global variable. There is no separate component registered for
            // such a function, so we expand it inline
            out.startElement("inlineFn");
            nominalTarget.export(out);
            out.endElement();
        } else {
            StylesheetPackage rootPackage = options.rootPackage;
            StylesheetPackage containingPackage = nominalTarget.getDeclaringComponent().getContainingPackage();
            if (rootPackage != null && !(rootPackage == containingPackage || rootPackage.contains(containingPackage))) {
                throw new XPathException("Cannot export a package containing a reference to a user-defined function ("
                                                 + toShortString()
                                                 + ") that is not present in the package being exported");
            }
            out.startElement("ufRef");
            out.emitAttribute("name", nominalTarget.getFunctionName());
            out.emitAttribute("arity", nominalTarget.getArity() + "");
            out.emitAttribute("bSlot", "" + getBindingSlot());
            out.endElement();
        }
    }

    /**
     * Make an elaborator for this expression
     *
     * @return an appropriate {@link Elaborator}
     */
    @Override
    public Elaborator getElaborator() {
        return new UserFunctionReferenceElaborator();
    }

    private static class UserFunctionReferenceElaborator extends ItemElaborator {

        @Override
        public ItemEvaluator elaborateForItem() {
            UserFunctionReference expr = (UserFunctionReference) getExpression();
            if (expr.bindingSlot == -1) {
                return context -> new BoundUserFunction(
                        expr.nominalTarget, expr.getArity(), expr.nominalTarget.getDeclaringComponent(), expr, context.getController());

            } else {
                return context -> {
                    Component targetComponent = context.getTargetComponent(expr.bindingSlot);
                    return new BoundUserFunction(
                            (UserFunction) targetComponent.getActor(), expr.getArity(), targetComponent, expr, context.getController());
                };
            }
        }
    }

    /**
     * A BoundUserFunction represents a user-defined function seen as a component. A single source-level
     * XSLT function may be the actor in several different components (in different stylesheet packages).
     * Although the code of the function is identical in each case, the bindings to other stylesheet components
     * may be different.
     */

    public static class BoundUserFunction extends AbstractFunction implements ContextOriginator {

        private final ExportAgent agent;
        private final FunctionItem function;
        private final int arity;
        private final Component component;
        private final Controller controller; // retained in case a function is returned from a query or stylesheet

        /**
         * Create a bound user function
         * @param function the function in question
         * @param arity the specific arity. This is relevant when the function item has an arity range and we
         *              are binding a function reference to a specific arity version
         * @param component the XSLT component containing the function (the same function in two different
         *                  packages corresponds to different components)
         * @param agent  used when the function needs to be exported to a SEF file
         * @param controller the controller object
         */
        public BoundUserFunction(FunctionItem function, int arity, Component component, ExportAgent agent, Controller controller) {
            this.agent = agent;
            this.function = function;
            this.arity = arity;
            this.component = component;
            this.controller = controller;
        }

        public FunctionItem getTargetFunction() {
            return function;
        }

        public Controller getController() {
            return controller;
        }

        @Override
        public XPathContext makeNewContext(XPathContext oldContext, ContextOriginator originator) {
            if (controller.getConfiguration() != oldContext.getConfiguration()) {
                throw new IllegalStateException("A function created under one Configuration cannot be called under a different Configuration");
            }
            XPathContextMajor c2;
            c2 = controller.newXPathContext();
            c2.setTemporaryOutputState(StandardNames.XSL_FUNCTION);
            c2.setCurrentOutputUri(null);
            c2.setCurrentComponent(component);
            c2.setResourceResolver(oldContext.getResourceResolver());
            c2.setOrigin(originator);
            c2.setCaller(oldContext);
            return function.makeNewContext(c2, originator);
        }


        @Override
        public Sequence call(XPathContext context, Sequence[] args) throws XPathException {
            XPathContext c2 = function.makeNewContext(context, this);
            if (c2 instanceof XPathContextMajor && component != null) {
                ((XPathContextMajor) c2).setCurrentComponent(component);
            }
            if (function.getArity() > args.length) {
                assert function instanceof FunctionDefinition && ((FunctionDefinition)function).getMinimumArity() <= args.length;
                FunctionDefinition fd = (FunctionDefinition)function;
                Sequence[] extendedArgs = Arrays.copyOf(args, fd.getNumberOfParameters());
                for (int i=args.length; i<extendedArgs.length; i++) {
                    extendedArgs[i] = fd.getDefaultValueExpression(i).get()
                            .makeElaborator().lazily(true, false).evaluate(context);
                }
                args = extendedArgs;
            }
            return function.call(c2, args);
        }

        @Override
        public FunctionItemType getFunctionItemType() {
            if (function instanceof UserFunction) {
                return ((UserFunction) function).getFunctionItemType(getArity());
            }
            return function.getFunctionItemType();
        }

        @Override
        public AnnotationList getAnnotations() {
            return function.getAnnotations();
        }

        @Override
        public StructuredQName getFunctionName() {
            return function.getFunctionName();
        }

        @Override
        public int getArity() {
            return arity;
        }

        @Override
        public String getDescription() {
            return function.getDescription();
        }

        @Override
        public void export(ExpressionPresenter out) throws XPathException {
            agent.export(out);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other instanceof BoundUserFunction) {
                BoundUserFunction buf = (BoundUserFunction) other;
                return getTargetFunction() == buf.getTargetFunction() && getArity() == buf.getArity();
            }
            return false;
        }

        @Override
        public int hashCode() {
            return getTargetFunction().hashCode() ^ (getArity() << 13);
        }

        @Override
        public synchronized String getUniqueIdentifier() {
            return function.getDescription();
        }
    }

}

// Copyright (c) 2018-2026 Saxonica Limited
