////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.instruct.AnalyzeString;
import net.sf.saxon.expr.oper.OperandArray;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.Error;
import net.sf.saxon.functions.*;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.ma.map.MapFunctionSet;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.pattern.NodeSetPattern;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.IntegerValue;

import java.util.Arrays;

/**
 * A call to a system-defined function (specifically, a function implemented as an instance
 * of {@link net.sf.saxon.functions.SystemFunction})
 */
public class SystemFunctionCall extends StaticFunctionCall implements Negatable {

    public Evaluator[] argumentEvaluators;

    public SystemFunctionCall(SystemFunction target, Expression[] arguments) {
        super(target, arguments);
        argumentEvaluators = new Evaluator[arguments.length];
        Arrays.fill(argumentEvaluators, Evaluator.LAZY_SEQUENCE);
    }

    /**
     * Set the retained static context
     *
     * @param rsc the static context to be retained
     */
    @Override
    public void setRetainedStaticContext(RetainedStaticContext rsc) {
        super.setRetainedStaticContext(rsc);
        getTargetFunction().setRetainedStaticContext(rsc);
    }

    /**
     * Pre-evaluate a function at compile time. Functions that do not allow
     * pre-evaluation, or that need access to context information, can prevent early
     * evaluation by setting the LATE bit in the function properties.
     *
     * @param visitor an expression visitor
     * @return the result of the early evaluation, or the original expression, or potentially
     * a simplified expression
     * @throws net.sf.saxon.trans.XPathException if evaluation fails
     */
    @Override
    public Expression preEvaluate(ExpressionVisitor visitor) throws XPathException {
        SystemFunction target = getTargetFunction();
        if ((target.getDetails().properties & BuiltInFunctionSet.LATE) == 0) {
            return super.preEvaluate(visitor);
        } else {
            // Early evaluation of this function is suppressed
            return this;
        }
    }

    /**
     * Type-check the expression. This also calls preEvaluate() to evaluate the function
     * if all the arguments are constant; functions that do not require this behavior
     * can override the preEvaluate method.
     *
     * @param visitor     the expression visitor
     * @param contextInfo information about the type of the context item
     */
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        typeCheckChildren(visitor, contextInfo);
        checkFunctionCall(getTargetFunction(), visitor);
        // Give the function an opportunity to use the type information now available
        getTargetFunction().supplyTypeInformation(visitor, contextInfo, getArguments());
        if ((getTargetFunction().getDetails().properties & BuiltInFunctionSet.LATE) == 0) {
            return preEvaluateIfConstant(visitor);
        }
        allocateArgumentEvaluators(getArguments());
        return this;
    }

    public void allocateArgumentEvaluators(Expression[] arguments) {
        for (int i = 0; i < arguments.length; i++) {
            Expression arg = arguments[i];
            int cardinality = isCallOn(Concat.class) ?
                    StaticProperty.ALLOWS_ZERO_OR_ONE :
                    getTargetFunction().getDetails().argumentTypes[i].getCardinality();
            if (arg instanceof Literal) {
                argumentEvaluators[i] = Evaluator.LITERAL;
            } else if (arg instanceof VariableReference) {
                argumentEvaluators[i] = Evaluator.VARIABLE;
            } else if (cardinality == StaticProperty.EXACTLY_ONE) {
                argumentEvaluators[i] = Evaluator.SINGLE_ITEM;
            } else if (cardinality == StaticProperty.ALLOWS_ZERO_OR_ONE) {
                argumentEvaluators[i] = Evaluator.OPTIONAL_ITEM;
            } else {
                argumentEvaluators[i] = Evaluator.LAZY_SEQUENCE;
            }
        }
    }

    @Override
    public SystemFunction getTargetFunction() {
        return (SystemFunction) super.getTargetFunction();
    }

    /**
     * Compute the dependencies of an expression, as the union of the
     * dependencies of its subexpressions. (This is overridden for path expressions
     * and filter expressions, where the dependencies of a subexpression are not all
     * propogated). This method should be called only once, to compute the dependencies;
     * after that, getDependencies should be used.
     *
     * @return the depencies, as a bit-mask
     */
    @Override
    public int getIntrinsicDependencies() {
        int properties = getTargetFunction().getDetails().properties;
        int dep = 0;
        if ((properties & BuiltInFunctionSet.LATE) != 0) {
            dep = StaticProperty.DEPENDS_ON_RUNTIME_ENVIRONMENT;
        }
        if ((properties & BuiltInFunctionSet.FOCUS) != 0) {
            if ((properties & BuiltInFunctionSet.CDOC) != 0) {
                dep |= StaticProperty.DEPENDS_ON_CONTEXT_DOCUMENT;
            }
            if ((properties & BuiltInFunctionSet.CITEM) != 0) {
                dep |= StaticProperty.DEPENDS_ON_CONTEXT_ITEM;
            }
            if ((properties & BuiltInFunctionSet.POSN) != 0) {
                dep |= StaticProperty.DEPENDS_ON_POSITION;
            }
            if ((properties & BuiltInFunctionSet.LAST) != 0) {
                dep |= StaticProperty.DEPENDS_ON_LAST;
            }
        }
        if ((properties & BuiltInFunctionSet.BASE) != 0) {
            dep |= StaticProperty.DEPENDS_ON_STATIC_CONTEXT;
        }
        if ((properties & BuiltInFunctionSet.DCOLL) != 0) {
            dep |= StaticProperty.DEPENDS_ON_STATIC_CONTEXT;
        }
        if (isCallOn(RegexGroup.class) || isCallOn(CurrentMergeGroup.class) || isCallOn(CurrentMergeKey.class)) {
            dep |= StaticProperty.DEPENDS_ON_CURRENT_GROUP;
        }
        return dep;
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
        return getTargetFunction().getCardinality(getArguments());
    }

    /**
     * Compute the special properties of this expression. These properties are denoted by a bit-significant
     * integer, possible values are in class {@link net.sf.saxon.expr.StaticProperty}. The "special" properties are properties
     * other than cardinality and dependencies, and most of them relate to properties of node sequences, for
     * example whether the nodes are in document order.
     *
     * @return the special properties, as a bit-significant integer
     */
    @Override
    protected int computeSpecialProperties() {
        return getTargetFunction().getSpecialProperties(getArguments());
    }

    /**
     * Return the estimated cost of evaluating an expression. This is a very crude measure based
     * on the syntactic form of the expression (we have no knowledge of data values). We take
     * the cost of evaluating a simple scalar comparison or arithmetic expression as 1 (one),
     * and we assume that a sequence has length 5. The resulting estimates may be used, for
     * example, to reorder the predicates in a filter expression so cheaper predicates are
     * evaluated first.
     */
    @Override
    public int getNetCost() {
        return getTargetFunction().getNetCost();
    }

    @Override
    public Expression getScopingExpression() {
        if (isCallOn(RegexGroup.class)) {
            Expression parent = getParentExpression();
            while (parent != null) {
                if (parent instanceof AnalyzeString) {
                    return parent;
                }
                parent = parent.getParentExpression();
            }
            return null;
        } else {
            return super.getScopingExpression();
        }
    }

    /**
     * Ask whether the expression can be lifted out of a loop, assuming it has no dependencies
     * on the controlling variable/focus of the loop
     *
     * @param forStreaming true if we are optimizing for streamed evaluation
     */
    @Override
    public boolean isLiftable(boolean forStreaming) {
        // xsl:map-entry is not liftable when streaming because of the special streamability
        // rules for xsl:map; similarly XPath map constructor expressions.
        // The tests for current-merge-group/key were added to fix bug 3652 - it seems
        // an inelegant solution because it's being handled differently from other context
        // dependencies, but it works.
        return super.isLiftable(forStreaming) &&
                !isCallOn(CurrentMergeGroup.class) && !isCallOn(CurrentMergeKey.class) &&
                (!forStreaming || !isCallOn(MapFunctionSet.MapEntry.class));
    }

    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        Optimizer opt = visitor.obtainOptimizer();
        Expression sf = super.optimize(visitor, contextInfo);
        if (sf == this) {
            // Give the function an opportunity to regenerate the function call, with more information about
            // the types of the arguments than was previously available
            Expression sfo = getTargetFunction().makeOptimizedFunctionCall(visitor, contextInfo, getArguments());
            if (sfo != null) {
                sfo.setParentExpression(getParentExpression());
                ExpressionTool.copyLocationInfo(this, sfo);
                if (sfo instanceof SystemFunctionCall) {
                    ((SystemFunctionCall) sfo).allocateArgumentEvaluators(((SystemFunctionCall) sfo).getArguments());
                }
                return sfo;
            }
        }
        if (sf instanceof SystemFunctionCall && opt.isOptionSet(OptimizerOptions.CONSTANT_FOLDING)) {
            // If any arguments are known to be empty, pre-evaluate the result
            BuiltInFunctionSet.Entry details = ((SystemFunctionCall) sf).getTargetFunction().getDetails();
            if ((details.properties & BuiltInFunctionSet.UO) != 0) {
                // First argument does not need to be in any particular order
                setArg(0, getArg(0).unordered(true, visitor.isOptimizeForStreaming()));
            }
            if (getArity() <= details.resultIfEmpty.length) {
                // the condition eliminates concat, which is a special case.
                for (int i = 0; i < getArity(); i++) {
                    if (Literal.isEmptySequence(getArg(i)) && details.resultIfEmpty[i] != null) {
                        return Literal.makeLiteral(details.resultIfEmpty[i].materialize(), this);
                    }
                }
            }
            ((SystemFunctionCall) sf).allocateArgumentEvaluators(((SystemFunctionCall) sf).getArguments());
        }
        return sf;
    }

    @Override
    public boolean isVacuousExpression() {
        return isCallOn(Error.class);
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
        return getTargetFunction().getResultItemType(getArguments());
    }

    /**
     * Copy an expression. This makes a deep copy.
     * @param rebindings variables that need to be re-bound
     * @return the copy of the original expression
     */
    @Override
    public Expression copy(RebindingMap rebindings) {
        Expression[] args = new Expression[getArity()];
        for (int i = 0; i < args.length; i++) {
            args[i] = getArg(i).copy(rebindings);
        }
        SystemFunction target = getTargetFunction();
        if (target instanceof StatefulSystemFunction) {
            target = ((StatefulSystemFunction) target).copy();
        }
        return target.makeFunctionCall(args);
    }
    /**
     * For an expression that returns an integer or a sequence of integers, get
     * a lower and upper bound on the values of the integers that may be returned, from
     * static analysis. The default implementation returns null, meaning "unknown" or
     * "not applicable". Other implementations return an array of two IntegerValue objects,
     * representing the lower and upper bounds respectively. The values
     * UNBOUNDED_LOWER and UNBOUNDED_UPPER are used by convention to indicate that
     * the value may be arbitrarily large. The values MAX_STRING_LENGTH and MAX_SEQUENCE_LENGTH
     * are used to indicate values limited by the size of a string or the size of a sequence.
     *
     * @return the lower and upper bounds of integer values in the result, or null to indicate
     * unknown or not applicable.
     */
    //@Override
    @Override
    public IntegerValue[] getIntegerBounds() {
        SystemFunction fn = getTargetFunction();
        if ((fn.getDetails().properties & BuiltInFunctionSet.FILTER) != 0) {
            return getArg(0).getIntegerBounds();
        }
        return fn.getIntegerBounds();
    }

    /**
     * Check whether this specific instance of the expression is negatable
     *
     * @param th the TypeHierarchy (in case it's needed)
     * @return true if it is
     */
    @Override
    public boolean isNegatable(TypeHierarchy th) {
        return isCallOn(NotFn.class) || isCallOn(BooleanFn.class) || isCallOn(Empty.class) || isCallOn(Exists.class);
    }

    /**
     * Create an expression that returns the negation of this expression
     *
     * @return the negated expression
     * @throws UnsupportedOperationException if isNegatable() returns false
     */
    @Override
    public Expression negate() {
        SystemFunction fn = getTargetFunction();
        if (fn instanceof NotFn) {
            Expression arg = getArg(0);
            if (arg.getItemType() == BuiltInAtomicType.BOOLEAN && arg.getCardinality() == StaticProperty.EXACTLY_ONE) {
                return arg;
            } else {
                return SystemFunction.makeCall("boolean", getRetainedStaticContext(), arg);
            }
        } else if (fn instanceof BooleanFn) {
            return SystemFunction.makeCall("not", getRetainedStaticContext(), getArg(0));
        } else if (fn instanceof Exists) {
            return SystemFunction.makeCall("empty", getRetainedStaticContext(), getArg(0));
        } else if (fn instanceof Empty) {
            return SystemFunction.makeCall("exists", getRetainedStaticContext(), getArg(0));
        }
        throw new UnsupportedOperationException();
    }

    /**
     * Replace this expression by a simpler expression that delivers the results without regard
     * to order.
     *
     * @param retainAllNodes set to true if the result must contain exactly the same nodes as the
     *                       original; set to false if the result can eliminate (or introduce) duplicates.
     * @param forStreaming   set to true if the result is to be optimized for streaming
     * @return an expression that delivers the same nodes in a more convenient order
     * @throws net.sf.saxon.trans.XPathException if the rewrite fails
     */
    @Override
    public Expression unordered(boolean retainAllNodes, boolean forStreaming) throws XPathException {
        SystemFunction fn = getTargetFunction();
        if (fn instanceof Reverse) {
            return getArg(0);
        }
        if (fn instanceof TreatFn) {
            setArg(0, getArg(0).unordered(retainAllNodes, forStreaming));
        }
        return this;
    }

    /**
     * Add a representation of this expression to a PathMap. The PathMap captures a map of the nodes visited
     * by an expression in a source tree.
     * <p>The default implementation of this method assumes that an expression does no navigation other than
     * the navigation done by evaluating its subexpressions, and that the subexpressions are evaluated in the
     * same context as the containing expression. The method must be overridden for any expression
     * where these assumptions do not hold. For example, implementations exist for AxisExpression, ParentExpression,
     * and RootExpression (because they perform navigation), and for the doc(), document(), and collection()
     * functions because they create a new navigation root. Implementations also exist for PathExpression and
     * FilterExpression because they have subexpressions that are evaluated in a different context from the
     * calling expression.</p>
     *
     * @param pathMap        the PathMap to which the expression should be added
     * @param pathMapNodeSet the PathMapNodeSet to which the paths embodied in this expression should be added
     * @return the pathMapNodeSet representing the points in the source document that are both reachable by this
     * expression, and that represent possible results of this expression. For an expression that does
     * navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     * expressions, it is the same as the input pathMapNode.
     */
    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        if (isCallOn(Doc.class) || isCallOn(DocumentFn.class) || isCallOn(CollectionFn.class)) {
            getArg(0).addToPathMap(pathMap, pathMapNodeSet);
            return new PathMap.PathMapNodeSet(pathMap.makeNewRoot(this));
        } else if (isCallOn(KeyFn.class)) {
            return ((KeyFn) getTargetFunction()).addToPathMap(pathMap, pathMapNodeSet);
        } else {
            return super.addToPathMap(pathMap, pathMapNodeSet);
        }
    }

    /**
     * Convert this expression to an equivalent XSLT pattern
     *
     * @param config the Saxon configuration
     * @return the equivalent pattern
     * @throws net.sf.saxon.trans.XPathException if conversion is not possible
     */
    @Override
    public Pattern toPattern(Configuration config) throws XPathException {
        SystemFunction fn = getTargetFunction();
        if (fn instanceof Root_1) {
            if (getArg(0) instanceof ContextItemExpression ||
                             (getArg(0) instanceof ItemChecker &&
                                      ((ItemChecker) getArg(0)).getBaseExpression() instanceof ContextItemExpression)) {
                return new NodeSetPattern(this);
            }
        }
        return super.toPattern(config);
    }

    @Override
    public Sequence[] evaluateArguments(XPathContext context) throws XPathException {
        OperandArray operanda = getOperanda();
        int numArgs = operanda.getNumberOfOperands();
        Sequence[] actualArgs = new Sequence[numArgs];
        for (int i = 0; i < numArgs; i++) {
            Expression exp = operanda.getOperandExpression(i);
            actualArgs[i] = argumentEvaluators[i].evaluate(exp, context);
        }
        return actualArgs;
    }

    @Override
    public void resetLocalStaticProperties() {
        super.resetLocalStaticProperties();
        if (argumentEvaluators != null) {
            allocateArgumentEvaluators(getArguments());
        }
    }

    @Override
    public void process(Outputter output, XPathContext context) throws XPathException {
        Function target = getTargetFunction();
        if (target instanceof PushableFunction) {
            Sequence[] actualArgs = evaluateArguments(context);
            try {
                ((PushableFunction)target).process(output, context, actualArgs);
            } catch (XPathException e) {
                e.maybeSetLocation(getLocation());
                e.maybeSetContext(context);
                e.maybeSetFailingExpression(this);
                throw e;
            }
        } else {
            super.process(output, context);
        }
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in explain() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "sysFuncCall";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the destination of the output
     */
    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        if (getFunctionName().hasURI(NamespaceConstant.FN)) {
            out.startElement("fn", this);
            out.emitAttribute("name", getFunctionName().getLocalPart());
            getTargetFunction().exportAttributes(out);
            for (Operand o : operands()) {
                o.getChildExpression().export(out);
            }
            getTargetFunction().exportAdditionalArguments(this, out);
            out.endElement();
        } else {
            // Function was implemented as an IntegratedFunctionCall in 9.7 and we retain the same export format
            out.startElement("ifCall", this);
            out.emitAttribute("name", getFunctionName());
            out.emitAttribute("type", getTargetFunction().getFunctionItemType().getResultType().toAlphaCode());
            getTargetFunction().exportAttributes(out);
            for (Operand o : operands()) {
                o.getChildExpression().export(out);
            }
            getTargetFunction().exportAdditionalArguments(this, out);
            out.endElement();
        }
    }

    /**
     * Subclass representing a system function call that has been optimized; this overrides the
     * optimize() method to do nothing, thus ensuring that optimization converges.
     */

    public abstract static class Optimized extends SystemFunctionCall {

        public Optimized(SystemFunction target, Expression[] arguments) {
            super(target, arguments);
        }
        @Override
        public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
            return this; // prevent infinite optimization
        }
    }


}
