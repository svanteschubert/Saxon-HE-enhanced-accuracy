////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Affinity;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.SequenceType;

/**
 * Expression representing a call to a user-written extension
 * function implemented as a subtype of {@link ExtensionFunctionCall}
 */
public class IntegratedFunctionCall extends FunctionCall implements Callable {

    private StructuredQName name;
    private ExtensionFunctionCall function;
    private SequenceType resultType = SequenceType.ANY_SEQUENCE;
    private int state = 0;

    public IntegratedFunctionCall(StructuredQName name, ExtensionFunctionCall function) {
        this.name = name;
        this.function = function;
    }

    /**
     * Set the result type of the function
     * @param resultType the result type
     */

    public void setResultType(SequenceType resultType) {
        this.resultType = resultType;
    }

    /**
     * Get the qualified of the function being called
     *
     * @return the qualified name
     */
    @Override
    public StructuredQName getFunctionName() {
        return name;
    }

    /**
     * Get the target function to be called
     *
     * @param context the dynamic evaluation context
     * @return the target function, or null if unknown
     */
    @Override
    public Function getTargetFunction(XPathContext context) {
        return null;
    }

    /**
     * Get the ExtensionFunctionCall object supplied by the application
     *
     * @return the ExtensionFunctionCall object
     */

    public ExtensionFunctionCall getFunction() {
        return function;
    }


    /**
     * Method supplied by each class of function to check arguments during parsing, when all
     * the argument expressions have been read. This implementation of the method checks the arguments
     * of the supplied function call against the argument types declared as part of the extension function
     * definition, and generates code to do the conversion if necessary.
     *
     * @param visitor the expression visitor
     * @throws net.sf.saxon.trans.XPathException
     *          if the arguments are statically determined to be incompatible
     *          with the declared argument types of the function.
     */

    @Override
    public void checkArguments(/*@NotNull*/ ExpressionVisitor visitor) throws XPathException {
        ExtensionFunctionDefinition definition = function.getDefinition();
        checkArgumentCount(definition.getMinimumNumberOfArguments(), definition.getMaximumNumberOfArguments());
        final int args = getArity();
        SequenceType[] declaredArgumentTypes = definition.getArgumentTypes();
        if (declaredArgumentTypes == null || (args != 0 && declaredArgumentTypes.length == 0)) {
            throw new XPathException("Integrated function " + getDisplayName() +
                    " failed to declare its argument types");
        }
        SequenceType[] actualArgumentTypes = new SequenceType[args];
        TypeChecker tc = visitor.getConfiguration().getTypeChecker(false);
        for (int i = 0; i < args; i++) {
            setArg(i, tc.staticTypeCheck(
                    getArg(i),
                    i < declaredArgumentTypes.length ?
                            declaredArgumentTypes[i] :
                            declaredArgumentTypes[declaredArgumentTypes.length - 1],
                    new RoleDiagnostic(RoleDiagnostic.FUNCTION, getFunctionName().getDisplayName(), i),
                    visitor));

            actualArgumentTypes[i] = SequenceType.makeSequenceType(
                    getArg(i).getItemType(),
                    getArg(i).getCardinality());
        }
        resultType = definition.getResultType(actualArgumentTypes);
        if (state == 0) {
            function.supplyStaticContext(visitor.getStaticContext(), 0, getArguments());
        }
        state++;
    }

    /**
     * Type-check the expression.
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        Expression exp = super.typeCheck(visitor, contextInfo);
        if (exp instanceof IntegratedFunctionCall) {
            Expression exp2 = ((IntegratedFunctionCall) exp).function.rewrite(visitor.getStaticContext(), getArguments());
            if (exp2 == null) {
                return exp;
            } else {
                ExpressionTool.copyLocationInfo(this, exp2);
                return exp2.simplify().typeCheck(visitor, contextInfo).optimize(visitor, contextInfo);
            }
        }
        return exp;
    }

    /**
     * Pre-evaluate a function at compile time. This function does not allow
     * pre-evaluation, so the method returns null.
     *
     * @param visitor an expression visitor
     * @return for this class: always null
     */

    @Override
    public Expression preEvaluate(ExpressionVisitor visitor) {
        return this;
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
     *         Type.NODE, or Type.ITEM (meaning not known at compile time)
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return resultType.getPrimaryType();
    }

    /**
     * Compute the static cardinality of this expression
     *
     * @return the computed cardinality, as one of the values {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ZERO_OR_ONE},
     *         {@link net.sf.saxon.expr.StaticProperty#EXACTLY_ONE}, {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ONE_OR_MORE},
     *         {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ZERO_OR_MORE}
     */

    @Override
    protected int computeCardinality() {
        return resultType.getCardinality();
    }

    /**
     * Determine the intrinsic dependencies of an expression, that is, those which are not derived
     * from the dependencies of its subexpressions. For example, position() has an intrinsic dependency
     * on the context position, while (position()+1) does not. The default implementation
     * of the method returns 0, indicating "no dependencies".
     *
     * @return a set of bit-significant flags identifying the "intrinsic"
     *         dependencies. The flags are documented in class net.sf.saxon.value.StaticProperty
     */

    @Override
    public int getIntrinsicDependencies() {
        ExtensionFunctionDefinition definition = function.getDefinition();
        return definition.dependsOnFocus() ? StaticProperty.DEPENDS_ON_FOCUS : 0;
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
        ExtensionFunctionDefinition definition = function.getDefinition();
        return definition.hasSideEffects() ? StaticProperty.HAS_SIDE_EFFECTS : StaticProperty.NO_NODES_NEWLY_CREATED;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings Variables that need to be re-bound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        ExtensionFunctionCall newCall = function.getDefinition().makeCallExpression();
        newCall.setDefinition(function.getDefinition());
        function.copyLocalData(newCall);
        IntegratedFunctionCall copy = new IntegratedFunctionCall(getFunctionName(), newCall);
        Expression[] args = new Expression[getArity()];
        for (int i = 0; i < args.length; i++) {
            args[i] = getArg(i).copy(rebindings);
        }
        copy.setArguments(args);
        copy.resultType = resultType;
        copy.state = state;
        ExpressionTool.copyLocationInfo(this, copy);
        return copy;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("ifCall", this);
        out.emitAttribute("name", getFunctionName());
        out.emitAttribute("type", resultType.toAlphaCode());
        for (Operand o : operands()) {
            o.getChildExpression().export(out);
        }
        out.endElement();
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
     *         of the expression
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(final XPathContext context) throws XPathException {
        ExtensionFunctionDefinition definition = function.getDefinition();
        Sequence[] argValues = new Sequence[getArity()];
        for (int i = 0; i < argValues.length; i++) {
            argValues[i] = SequenceTool.toLazySequence(getArg(i).iterate(context));
        }
        final RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.FUNCTION_RESULT, getFunctionName().getDisplayName(), 0);
        final Configuration config = context.getConfiguration();
        final TypeHierarchy th = config.getTypeHierarchy();

        SequenceIterator result;
        try {
            result = function.call(context, argValues).iterate();
        } catch (XPathException e) {
            e.maybeSetLocation(getLocation());
            throw e;
        }
        if (!definition.trustResultType()) {
            int card = resultType.getCardinality();
            if (card != StaticProperty.ALLOWS_ZERO_OR_MORE) {
                result = new CardinalityCheckingIterator(result, card, role, getLocation());
            }
            final ItemType type = resultType.getPrimaryType();
            if (type != AnyItemType.getInstance()) {
                result = new ItemMappingIterator(result,
                                                 item -> {
                                                     if (!type.matches(item, th)) {
                                                         String msg = role.composeErrorMessage(type, item, th);
                                                         XPathException err = new XPathException(msg, "XPTY0004");
                                                         err.setLocation(getLocation());
                                                         throw err;
                                                     }
                                                     return item;
                                                 }, true);
            }
            if (th.relationship(type, AnyNodeTest.getInstance()) != Affinity.DISJOINT) {
                result = new ItemMappingIterator(
                        result,
                        new ConfigurationCheckingFunction(context.getConfiguration()), true);
            }
        }
        return result;
    }

    /**
     * Get the effective boolean value of the expression. This returns false if the value
     * is the empty sequence, a zero-length string, a number equal to zero, or the boolean
     * false. Otherwise it returns true.
     *
     * @param context The context in which the expression is to be evaluated
     * @return the effective boolean value
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */

    @Override
    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        Sequence[] argValues = new Sequence[getArity()];
        for (int i = 0; i < argValues.length; i++) {
            argValues[i] = SequenceTool.toLazySequence(getArg(i).iterate(context));
        }
        try {
            return function.effectiveBooleanValue(context, argValues);
        } catch (XPathException e) {
            e.maybeSetLocation(getLocation());
            throw e;
        }
    }

    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return function.call(context, arguments);
    }


    /**
     * This class checks that NodeInfo objects returned by an extension function were created
     * under the right Configuration
     */

    public static class ConfigurationCheckingFunction implements ItemMappingFunction {

        private Configuration config;

        public ConfigurationCheckingFunction(Configuration config) {
            this.config = config;
        }

        /**
         * Map one item to another item.
         *
         * @param item The input item to be mapped.
         * @return either the output item, or null.
         */

        @Override
        public Item mapItem(Item item) throws XPathException {
            if (item instanceof NodeInfo && !config.isCompatible(((NodeInfo) item).getConfiguration())) {
                throw new XPathException(
                        "Node returned by extension function was created with an incompatible Configuration", SaxonErrorCode.SXXP0004);
            }
            return item;
        }
    }
}

