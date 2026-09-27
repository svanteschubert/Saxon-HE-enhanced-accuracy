/// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.PullElaborator;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.elab.SequenceEvaluator;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.IContextAccessorFunction;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpSuppressWarnings;
import net.sf.saxon.tree.iter.ListIterator;
import net.sf.saxon.tree.jiter.ConcatenatingIterable;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * This expression class implements the operation of currying (or performing partial application) of a function item.
 * That is, it takes a function item and a set of argument bindings as input, and produces a new function as output.
 * The result of evaluating a {@code DynamicPartialApply} expression is generally a {@link CurriedFunction}, which is a type
 * of {@link FunctionItem}.
 *
 * <p>This class handles dynamic function calls where one or more of the supplied arguments
 * is a "?" placeholder.</p>
 *
 * <p>Evaluation of the {@link DynamicPartialApply} expression returns a function item, specifically
 *       a {@link CurriedFunction} instance. This implements the {@link FunctionItem} interface,
 *       and can therefore be called in the same way as any other function item, by means of a
 *       {@link DynamicFunctionCall}.</p>
 *
 * <p>The {@link CurriedFunction} instance is responsible for mapping the arguments supplied
 * in the dynamic function call to the corresponding arguments of the underlying named
 * function reference.</p>
 *
 **/

public class DynamicPartialApply extends Expression {

    private final Operand functionItemOperand;
    private final Operand[] operanda;

    /**
     * Create a dynamic partial function application expression
     *
     * @param functionItem   the expression that returns the function to be called
     * @param arguments      an array corresponding to the declared arguments of the base function, in order. Each entry
     *                       in the array is either an expression which is evaluated and bound to the corresponding
     *                       argument of the base function, or a {@link PlaceHolder} pseudo-expression.
     */

    public DynamicPartialApply(Expression functionItem, Expression[] arguments) {
        functionItemOperand = new Operand(this, functionItem, OperandRole.INSPECT);
        adoptChildExpression(functionItem);

        operanda = new Operand[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            Expression argCopy = arguments[i];
            operanda[i] = new Operand(this, argCopy, OperandRole.NAVIGATE);
            adoptChildExpression(argCopy);
        }
    }

    public Expression getBaseExpression() {
        return functionItemOperand.getChildExpression();
    }

    public void setBaseExpression(Expression base) {
        functionItemOperand.setChildExpression(base);
    }

    public int getNumberOfPlaceHolders() {
        int n = 0;
        for (Operand o : operanda) {
            if (o.getChildExpression() instanceof PlaceHolder) {
                n++;
            }
        }
        return n;
    }


    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        typeCheckChildren(visitor, contextInfo);

        ItemType baseType = getBaseExpression().getItemType();

        TypeChecker tc = visitor.getConfiguration().getTypeChecker(false);
        String baseName = "saxon:call";
        if (getBaseExpression() instanceof FunctionLiteral) {
            StructuredQName name = ((FunctionLiteral)getBaseExpression()).getGroundedValue().getFunctionName();
            if (name != null && !name.hasURI(NamespaceUri.ANONYMOUS)) {
                baseName = name.getDisplayName();
            }
        }
        final String diagnosticName = baseName;
        for (int i = 0; i < operanda.length; i++) {
            final Operand op = operanda[i];
            Expression arg = op.getChildExpression();
            if (!(arg instanceof PlaceHolder)) {
                if (baseType instanceof SpecificFunctionType && i < ((SpecificFunctionType) baseType).getArity()) {
                    final int pos = i;
                    Supplier<RoleDiagnostic> argRole =
                            () -> new RoleDiagnostic(RoleDiagnostic.PARTIAL_APPLY, diagnosticName, pos);
                    SequenceType requiredArgType = ((SpecificFunctionType) baseType).getArgumentTypes()[i];
                    Expression a3 = tc.staticTypeCheck(
                            arg, requiredArgType, argRole, visitor);
                    if (a3 != arg) {
                        op.setChildExpression(a3);
                    }
                }
            }
        }

        Supplier<RoleDiagnostic> role = () ->
                new RoleDiagnostic(RoleDiagnostic.DYNAMIC_FUNCTION, getBaseExpression().toShortString(), 0);

        setBaseExpression(tc.staticTypeCheck(
                getBaseExpression(), SequenceType.FUNCTION_ITEM_SEQUENCE, role, visitor));

        return this;
    }

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        ItemType baseItemType = getBaseExpression().getItemType();
        SequenceType resultType = SequenceType.ANY_SEQUENCE;
        if (baseItemType instanceof SpecificFunctionType) {
            resultType = ((SpecificFunctionType) baseItemType).getResultType();
        }
        int placeholders = getNumberOfPlaceHolders();
        SequenceType[] argTypes = new SequenceType[placeholders];
        if (baseItemType instanceof SpecificFunctionType) {
            for (int i = 0, j = 0; i < operanda.length; i++) {
                Expression bound = operanda[i].getChildExpression();
                if (bound instanceof PlaceHolder) {
                    argTypes[j++] = ((SpecificFunctionType) baseItemType).getArgumentTypes()[i];
                }
            }
        } else {
            Arrays.fill(argTypes, SequenceType.ANY_SEQUENCE);
        }
        return new SpecificFunctionType(argTypes, resultType);
    }



    /**
     * Compute the special properties of this expression. These properties are denoted by a bit-significant
     * integer, possible values are in class {@link StaticProperty}. The "special" properties are properties
     * other than cardinality and dependencies, and most of them relate to properties of node sequences, for
     * example whether the nodes are in document order.
     *
     * @return the special properties, as a bit-significant integer
     */
    @Override
    protected int computeSpecialProperties() {
        return StaticProperty.COMPUTED_FUNCTION;
    }

    @Override
    public Iterable<Operand> operands() {
        return new ConcatenatingIterable<>(functionItemOperand, Arrays.asList(operanda));
    }

    @Override
    public int getImplementationMethod() {
        return EVALUATE_METHOD;
    }

    /**
     * Is this expression the same as another expression?
     *
     * @param other the expression to be compared with this one
     * @return true if the two expressions are statically equivalent
     */
    @Override
    public boolean equals(Object other) {
        if (other instanceof DynamicPartialApply pa2) {
            if (!getBaseExpression().isEqual(pa2.getBaseExpression())) {
                return false;
            }
            if (operanda.length != pa2.operanda.length) {
                return false;
            }
            for (int i = 0; i< operanda.length; i++) {
                //  TODO: FIXME
                if ((operanda[i] == null) != (pa2.operanda[i] == null)) {
                    return false;
                }
                if (operanda[i] != null && !operanda[i].equals(pa2.operanda[i])) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Hashcode supporting equals()
     */

    @Override
    protected int computeHashCode() {
        int h = 0x236b92a0;
        int i = 0;
        for (Operand o : operands()) {
            Expression e = o.getChildExpression();
            h ^= (e instanceof PlaceHolder) ? i++ : e.hashCode();
        }
        return h;
    }

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("partialApply", this);
        getBaseExpression().export(out);
        for (Operand o : operanda) {
            Expression e = o.getChildExpression();
            if (e instanceof PlaceHolder) {
                out.startElement("null", this);
                out.emitAttribute("at", "" + ((PlaceHolder)e).getPlaceHolderSequence());
                out.endElement();
            } else {
                e.export(out);
            }
        }
        out.endElement();
    }

    @Override
    protected int computeCardinality() {
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
    }

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        Expression[] boundArgumentsCopy = new Expression[operanda.length];
        for (int i = 0; i < operanda.length; i++) {
            boundArgumentsCopy[i] = operanda[i].getChildExpression().copy(rebindings);
        }
        DynamicPartialApply exp = new DynamicPartialApply(getBaseExpression().copy(rebindings), boundArgumentsCopy);
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
    }

    /**
     * <p>The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form.</p>
     * <p>For subclasses of Expression that represent XPath expressions, the result should always be a string that
     * parses as an XPath 3.0 expression. The expression produced should be equivalent to the original making certain
     * assumptions about the static context. In general the expansion will make no assumptions about namespace bindings,
     * except that (a) the prefix "xs" is used to refer to the XML Schema namespace, and (b) the default function namespace
     * is assumed to be the "fn" namespace.</p>
     * <p>In the case of XSLT instructions and XQuery expressions, the toString() method gives an abstracted view of the syntax
     * that is not designed in general to be parseable.</p>
     *
     * @return a representation of the expression as a string
     */
    @Override
    @CSharpSuppressWarnings("UnsafeIteratorConversion")
    public String toString() {
        StringBuilder buff = new StringBuilder(64);
        boolean par = getBaseExpression().operands().iterator().hasNext();
        if (par) {
            buff.append("(").append(getBaseExpression().toString()).append(")");
        } else {
            buff.append(getBaseExpression().toString());
        }
        buff.append("(");
        for (int i = 0; i < operanda.length; i++) {
            if (operanda[i] == null) {
                buff.append("?");
            } else {
                buff.append(operanda[i].getChildExpression().toString());
            }
            if (i != operanda.length - 1) {
                buff.append(", ");
            }
        }
        buff.append(")");
        return buff.toString();
    }

    /**
     * Evaluate this expression at run-time
     *
     * @param context The XPath dynamic evaluation context
     * @return the result of the function, or null to represent an empty sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during evaluation of the function.
     */

    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        return makeElaborator().elaborateForPull().iterate(context);
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in export() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "partialApply";
    }

    /**
     * Make an elaborator for this expression
     *
     * @return an appropriate {@link Elaborator}
     */
    @Override
    public Elaborator getElaborator() {
        return new DynamicPartialApplyElaborator();
    }

    private static class DynamicPartialApplyElaborator extends PullElaborator {

        @Override
        public PullEvaluator elaborateForPull() {
            DynamicPartialApply expr = (DynamicPartialApply) getExpression();
            PullEvaluator functionEval = expr.getBaseExpression().makeElaborator().elaborateForPull();
            final int len = expr.operanda.length;
            SequenceEvaluator[] boundArgumentsEvaluators = new SequenceEvaluator[len];
            final int phCount = expr.getNumberOfPlaceHolders();
            int[] mapping = new int[phCount];
            for (int i = 0; i < len; i++) {
                if (expr.operanda[i].getChildExpression() instanceof PlaceHolder ph) {
                    mapping[ph.getPlaceHolderSequence()] = i;
                } else {
                    boundArgumentsEvaluators[i] = expr.operanda[i].getChildExpression().makeElaborator().eagerly();
                }
            }


            return context -> {
                SequenceIterator fnIter = functionEval.iterate(context);
                FunctionItem f;
                List<Item> result = new ArrayList<>();
                while ((f = (FunctionItem) fnIter.next()) != null) {

                    if (f.getArity() != len) {
                        throw new XPathException(
                                "The number of arguments supplied in the partial function application is " + len +
                                        ", but the arity of the function item is " + f.getArity(), "XPTY0004");
                    }

                    if (f instanceof IContextAccessorFunction icaf) {
                        f = icaf.bindContext(context);
                    }
                    Sequence[] values = new Sequence[len];
                    for (int i = 0; i < boundArgumentsEvaluators.length; i++) {
                        if (boundArgumentsEvaluators[i] != null) {
                            values[i] = boundArgumentsEvaluators[i].evaluate(context);
                        }
                    }

                    result.add(new CurriedFunction(f, values, mapping));
                }
                return new ListIterator.Of<>(result);
            };
        }

    }


}

// Copyright (c) 2021-2026 Saxonica Limited
