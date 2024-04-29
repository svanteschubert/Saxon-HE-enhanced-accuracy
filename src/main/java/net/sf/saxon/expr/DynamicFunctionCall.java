////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.elab.*;
import net.sf.saxon.expr.oper.OperandArray;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.ma.arrays.ArrayFunctionSet;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.map.MapFunctionSet;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * This class implements the function fn:apply(), which is a standard function in XQuery 3.1.
 * The fn:apply function is also used internally to implement dynamic function calls.
 */

public class DynamicFunctionCall extends Expression {

    private final Operand targetFunction;
    private final OperandArray suppliedArguments;

    public DynamicFunctionCall(Expression fn, List<Expression> args) {
        targetFunction = new Operand(this, fn, OperandRole.INSPECT);
        suppliedArguments = new OperandArray(this, args.toArray(new Expression[]{}));
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
        return ITERATE_METHOD;
    }

    /**
     * Get the return type
     * @return the best available item type that the function will return
     */

    @Override
    public ItemType getItemType() {
        // Item type of the result is the same as that of the supplied function
        ItemType fnType = targetFunction.getChildExpression().getItemType();
        if (fnType instanceof MapType) {
            return ((MapType)fnType).getValueType().getPrimaryType();
        } else if (fnType instanceof ArrayItemType) {
            return ((ArrayItemType) fnType).getMemberType().getPrimaryType();
        } else if (fnType instanceof FunctionItemType) {
            return ((FunctionItemType) fnType).getResultType().getPrimaryType();
        } else if (fnType instanceof AnyFunctionType) {
            return AnyItemType.getInstance();
        } else {
            return AnyItemType.getInstance();
        }
    }

    /**
     * Compute the static cardinality of this expression
     *
     * @return the computed cardinality, as one of the values {@link StaticProperty#ALLOWS_ZERO_OR_ONE},
     * {@link StaticProperty#EXACTLY_ONE}, {@link StaticProperty#ALLOWS_ONE_OR_MORE},
     * {@link StaticProperty#ALLOWS_ZERO_OR_MORE}. May also return {@link StaticProperty#ALLOWS_ZERO} if
     * the result is known to be an empty sequence, or {@link StaticProperty#ALLOWS_MANY} if
     * if is known to return a sequence of length two or more.
     */
    @Override
    protected int computeCardinality() {
        ItemType fnType = targetFunction.getChildExpression().getItemType();
        if (fnType instanceof MapType) {
            return Cardinality.union(
                    ((MapType) fnType).getValueType().getCardinality(),
                    StaticProperty.ALLOWS_ZERO);
        } else if (fnType instanceof ArrayItemType) {
            return ((ArrayItemType) fnType).getMemberType().getCardinality();
        } else if (fnType instanceof FunctionItemType) {
            return ((FunctionItemType) fnType).getResultType().getCardinality();
        } else {
            return StaticProperty.ALLOWS_ZERO_OR_MORE;
        }
    }

    public int getArity() {
        return suppliedArguments.getNumberOfOperands();
    }

    /**
     * Get the immediate sub-expressions of this expression, with information about the relationship
     * of each expression to its parent expression. Default implementation
     * works off the results of iterateSubExpressions()
     *
     * <p>If the expression is a Callable, then it is required that the order of the operands
     * returned by this function is the same as the order of arguments supplied to the corresponding
     * call() method.</p>
     *
     * @return an iterator containing the sub-expressions of this expression
     */
    @Override
    public Iterable<Operand> operands() {
        List<Operand> allOperands = new ArrayList<>(getArity()+1);
        allOperands.add(targetFunction);
        for (int i=0; i<getArity(); i++) {
            allOperands.add(suppliedArguments.getOperand(i));
        }
        return allOperands;
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
        typeCheckChildren(visitor, contextInfo);

        TypeChecker tc = visitor.getConfiguration().getTypeChecker(false);

        Supplier<RoleDiagnostic> roleSupplier0 = () ->
            new RoleDiagnostic(RoleDiagnostic.DYNAMIC_FUNCTION, targetFunction.getChildExpression().toShortString(), 0);

        targetFunction.setChildExpression(tc.staticTypeCheck(
                targetFunction.getChildExpression(), SequenceType.SINGLE_FUNCTION, roleSupplier0, visitor));

        if (getArity() == 1) {
            Expression target = targetFunction.getChildExpression();
            if (target.getItemType() instanceof MapType) {
                // Convert $map($key) to map:get($map, $key)
                // This improves streamability analysis - see accumulator-053
                return makeGetCall(visitor, MapFunctionSet.getInstance(31), contextInfo);
            } else if (target.getItemType() instanceof ArrayItemType) {
                // Convert $array($key) to array:get($array, $key)
                return makeGetCall(visitor, ArrayFunctionSet.getInstance(31), contextInfo);
            }
        }

        return this;
    }

    /**
     * Create a call on map:get() or array:get() if the function is known to be a map or array
     * @param visitor the expression visitor
     * @param fnSet the map or array function set as appropriate
     * @param contextInfo static information about the context item
     * @return the map:get() or array:get() function call
     * @throws XPathException if anything goes wrong.
     */

    private Expression makeGetCall(ExpressionVisitor visitor, BuiltInFunctionSet fnSet, ContextItemStaticInfo contextInfo) throws XPathException {
        Expression target = targetFunction.getChildExpression();
        Expression key = suppliedArguments.getOperandExpression(0);
        Expression getter = fnSet.makeFunction("get", 2).makeFunctionCall(target, key);
        getter.setRetainedStaticContext(target.getRetainedStaticContext());
        // Use custom diagnostics for type errors on the argument of the call (bug 4772)
        TypeChecker tc = visitor.getConfiguration().getTypeChecker(visitor.getStaticContext().isInBackwardsCompatibleMode());
        if (fnSet == MapFunctionSet.getInstance(31)) {
            Supplier<RoleDiagnostic> role =
                    () -> new RoleDiagnostic(RoleDiagnostic.MISC, "key value supplied when calling a map as a function", 0);
            ((SystemFunctionCall) getter).setArg(1, tc.staticTypeCheck(key, SequenceType.SINGLE_ATOMIC, role, visitor));
        } else {
            Supplier<RoleDiagnostic> role =
                    () -> new RoleDiagnostic(RoleDiagnostic.MISC, "subscript supplied when calling an array as a function", 0);
            ((SystemFunctionCall) getter).setArg(1, tc.staticTypeCheck(key, SequenceType.SINGLE_INTEGER, role, visitor));
        }
        return getter.typeCheck(visitor, contextInfo);
    }


    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions. This default implementation handles iteration for expressions that
     * return singleton values: for non-singleton expressions, the subclass must
     * provide its own implementation.
     *
     * @param context supplies the context for evaluation
     * @return a SequenceIterator that can be used to iterate over the result
     * of the expression
     * @throws XPathException if any dynamic error occurs evaluating the
     *                        expression
     */
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        return makeElaborator().elaborateForPull().iterate(context);
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     * @throws XPathException if the export fails, for example if an expression is found that won't work
     *                        in the target environment.
     */
    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        if ("JS".equals(out.getOptions().target) && out.getOptions().targetVersion == 2) {
            // for backwards compatibility, output a call on saxon:apply
            out.startElement("ifCall", this);
            out.emitAttribute("name", "Q{http://saxon.sf.net/}apply");
            out.emitAttribute("type", "*");
            if (targetFunction.getChildExpression() instanceof Literal) {
                FunctionItem f = (FunctionItem)(((Literal)targetFunction.getChildExpression()).getGroundedValue());
                if (f.getFunctionName() != null) {
                    out.emitAttribute("dyn", f.getFunctionName().getEQName() + "#" + f.getArity());
                }
            }
            targetFunction.getChildExpression().export(out);
            out.startSubsidiaryElement("arrayBlock");
            for (Operand o : suppliedArguments) {
                o.getChildExpression().export(out);
            }
            out.endSubsidiaryElement();
            out.endElement();
        } else {
            out.startElement("dynCall", this);
            targetFunction.getChildExpression().export(out);
            for (Operand o : suppliedArguments) {
                o.getChildExpression().export(out);
            }
            out.endElement();
        }
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @param rebindings a mutable list of (old binding, new binding) pairs
     *                   that is used to update the bindings held in any
     * @return the copy of the original expression
     */
    @Override
    public Expression copy(RebindingMap rebindings) {
        Expression f = targetFunction.getChildExpression().copy(rebindings);
        ArrayList<Expression> args = new ArrayList<>(getArity());
        for (Operand o : suppliedArguments) {
            args.add(o.getChildExpression().copy(rebindings));
        }
        return new DynamicFunctionCall(f, args);
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
        // no action
    }





    /**
     * Make an elaborator for this expression
     *
     * @return an appropriate {@link Elaborator}
     */
    @Override
    public Elaborator getElaborator() {
        return new DynamicFunctionCallElaborator();
    }

    private static class DynamicFunctionCallElaborator extends PullElaborator {
        @Override
        public PullEvaluator elaborateForPull() {
            DynamicFunctionCall expr = (DynamicFunctionCall)getExpression();
            TypeHierarchy th = getConfiguration().getTypeHierarchy();
            SequenceEvaluator[] argEvaluators = new SequenceEvaluator[expr.getArity()];
            for (int i=0; i<argEvaluators.length; i++) {
                Expression arg = expr.suppliedArguments.getOperand(i).getChildExpression();
                argEvaluators[i] = new LearningEvaluator(arg, arg.makeElaborator().lazily(true, false));
            }
            Expression body = expr.targetFunction.getChildExpression();
            ItemEvaluator functionEvaluator = body.makeElaborator().elaborateForItem();
            return context -> {
                FunctionItem fn = (FunctionItem) functionEvaluator.eval(context);
                FunctionItemType fit = fn.getFunctionItemType();
                if (fn.getArity() != argEvaluators.length) {
                    String errorCode = "XPTY0004";
                    throw new XPathException(
                            "Number of arguments required for dynamic call to " + fn.getDescription() + " is " + fn.getArity() +
                                    "; number supplied = " + argEvaluators.length, errorCode)
                            .asTypeError()
                            .withXPathContext(context)
                            .withLocation(expr.getLocation());
                }

                Sequence[] argValues = new Sequence[argEvaluators.length];

                if (fit == AnyFunctionType.ANY_FUNCTION) {
                    for (int i = 0; i < argEvaluators.length; i++) {
                        argValues[i] = argEvaluators[i].evaluate(context);
                    }
                } else {
                    for (int i = 0; i < argEvaluators.length; i++) {
                        SequenceType expected = fit.getArgumentTypes()[i];
                        Sequence actual = argEvaluators[i].evaluate(context);
                        if (!expected.equals(SequenceType.ANY_SEQUENCE)) {
                            Supplier<RoleDiagnostic> role;
                            role = () -> new RoleDiagnostic(RoleDiagnostic.FUNCTION, fn.getDescription(), 0);
                            actual = th.applyFunctionConversionRules(
                                    actual, expected, role, Loc.NONE);
                        }
                        argValues[i] = actual;
                    }
                }

                XPathContext c2 = fn.makeNewContext(context, null);
                c2.setCurrentOutputUri(null);
                if (c2 instanceof XPathContextMajor) {
                    ((XPathContextMajor) c2).setCurrentRegexIterator(null);
                }
                Sequence rawResult = fn.call(c2, argValues);
                if (fn.isTrustedResultType()) {
                    // trust system functions to return a result of the correct type
                    return rawResult.iterate();
                } else {
                    // Check the result of the function
                    Supplier<RoleDiagnostic> resultRole =
                            () -> new RoleDiagnostic(RoleDiagnostic.FUNCTION_RESULT, "fn:apply", -1);
                    return th.applyFunctionConversionRules(
                            rawResult, fit.getResultType(), resultRole, Loc.NONE).iterate();
                }
            };
        }

    }

    /**
     * Produce a short string identifying the expression for use in error messages
     *
     * @return a short string, sufficient to identify the expression
     */
    @Override
    public String toShortString() {
        StringBuilder sb = new StringBuilder(targetFunction.getChildExpression().toShortString())
                .append('(');
        for (Operand op : suppliedArguments) {
            sb.append(op.getChildExpression().toShortString()).append(',');
        }
        sb.setCharAt(sb.length()-1, ')');
        return sb.toString();
    }
}

