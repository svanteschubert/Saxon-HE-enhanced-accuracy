////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.instruct.OriginalFunction;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;

/**
 * A call to a function that is known statically. This is a stricter definition than "static function
 * call" in the XPath grammar, because it excludes calls that might be re-bound to a different function
 * as a result of XSLT overrides in a different package, calls to functions that hold dynamic context
 * information in their closure, and so on.
 */
public class StaticFunctionCall extends FunctionCall implements Callable {

    private Function target;

    public StaticFunctionCall(Function target, Expression[] arguments) {
        if (target.getArity() != arguments.length) {
            throw new IllegalArgumentException("Function call to " + target.getFunctionName() + " with wrong number of arguments (" + arguments.length + ")");
        }
        this.target = target;
        setOperanda(arguments, target.getOperandRoles());
    }

    /**
     * Get the target function to be called
     *
     * @return the target function
     */

    public Function getTargetFunction() {
        return target;
    }


    /**
     * Get the target function to be called
     *
     * @param context the dynamic evaluation context (not used in this implementation)
     * @return the target function
     */
    @Override
    public Function getTargetFunction(XPathContext context) {
        return getTargetFunction();
    }

    /**
     * Get the qualified of the function being called
     *
     * @return the qualified name
     */
    @Override
    public StructuredQName getFunctionName() {
        return target.getFunctionName();
    }

    /**
     * Ask whether this expression is a call on a particular function
     *
     * @param function the implementation class of the function in question
     * @return true if the expression is a call on the function
     */

    @Override
    public boolean isCallOn(Class<? extends SystemFunction> function) {
        return function.isAssignableFrom(target.getClass());
    }

    /**
     * Type-check the expression. This also calls preEvaluate() to evaluate the function
     * if all the arguments are constant; functions that do not require this behavior
     * can override the preEvaluate method.
     *
     * @param visitor the expression visitor
     * @param contextInfo information about the type of the context item
     */
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        checkFunctionCall(target, visitor);
        return super.typeCheck(visitor, contextInfo);
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings  variables that need to be re-bound
     */
    @Override
    public Expression copy(RebindingMap rebindings) {
        Expression[] args = new Expression[getArity()];
        for (int i=0; i<args.length; i++) {
            args[i] = getArg(i).copy(rebindings);
        }
        return new StaticFunctionCall(target, args);
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
        return target.getFunctionItemType().getResultType().getCardinality();
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
        return target.getFunctionItemType().getResultType().getPrimaryType();
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
        UType result = getItemType().getUType();
        for (Operand o : operands()) {
            if (o.getUsage() == OperandUsage.TRANSMISSION) {
                result = result.intersection(o.getChildExpression().getStaticUType(contextItemType));
            }
        }
        return result;
    }

    /**
     * Call the Callable.
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences.
     *                  <p>Generally it is advisable, if calling iterate() to process a supplied sequence, to
     *                  call it only once; if the value is required more than once, it should first be converted
     *                  to a {@link net.sf.saxon.om.GroundedValue} by calling the utility methd
     *                  SequenceTool.toGroundedValue().</p>
     *                  <p>If the expected value is a single item, the item should be obtained by calling
     *                  Sequence.head(): it cannot be assumed that the item will be passed as an instance of
     *                  {@link net.sf.saxon.om.Item} or {@link net.sf.saxon.value.AtomicValue}.</p>
     *                  <p>It is the caller's responsibility to perform any type conversions required
     *                  to convert arguments to the type expected by the callee. An exception is where
     *                  this Callable is explicitly an argument-converting wrapper around the original
     *                  Callable.</p>
     * @return the result of the evaluation, in the form of a Sequence. It is the responsibility
     * of the callee to ensure that the type of result conforms to the expected result type.
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return target.call(context, arguments);
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
        return "staticFunctionCall";
    }

    /**
     * Serialized output of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out destination of the SEF output
     */
    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        if (target instanceof OriginalFunction) {
            ExpressionPresenter.ExportOptions options = (ExpressionPresenter.ExportOptions) out.getOptions();
            OriginalFunction pf = (OriginalFunction) target;
            out.startElement("origFC", this);
            out.emitAttribute("name", pf.getFunctionName());
            out.emitAttribute("pack", options.packageMap.get(pf.getComponent().getContainingPackage()) + "");

            for (Operand o : operands()) {
                o.getChildExpression().export(out);
            }
            out.endElement();
        } else {

            if (target instanceof UnionCastableFunction) {
                // Bug 2611. Bug 3822.
                final UnionType targetType = ((UnionConstructorFunction) target).getTargetType();
                out.startElement("castable", this);
                if (targetType instanceof LocalUnionType) {
                    out.emitAttribute("to", AlphaCode.fromItemType(targetType));
                } else {
                    out.emitAttribute("as", targetType.toExportString());
                }
                out.emitAttribute("flags", "u" + (((UnionConstructorFunction) target).isAllowEmpty() ? "e" : ""));
                for (Operand o : operands()) {
                    o.getChildExpression().export(out);
                }
                out.endElement();
            } else if (target instanceof ListCastableFunction) {
                // Bug 2611. Bug 3822.
                out.startElement("castable", this);
                out.emitAttribute("as", ((ListConstructorFunction) target).getTargetType().getStructuredQName());
                out.emitAttribute("flags", "l" + (((ListConstructorFunction) target).isAllowEmpty() ? "e" : ""));
                for (Operand o : operands()) {
                    o.getChildExpression().export(out);
                }
                out.endElement();
            } else if (target instanceof UnionConstructorFunction) {
                // Bug 2611.
                final UnionType targetType = ((UnionConstructorFunction) target).getTargetType();
                out.startElement("cast", this);
                if (targetType instanceof LocalUnionType) {
                    out.emitAttribute("to", AlphaCode.fromItemType(targetType));
                } else{
                    out.emitAttribute("as", targetType.toExportString());
                }
                out.emitAttribute("flags", "u" + (((UnionConstructorFunction) target).isAllowEmpty() ? "e" : ""));
                for (Operand o : operands()) {
                    o.getChildExpression().export(out);
                }
                out.endElement();
            } else if (target instanceof ListConstructorFunction) {
                // Bug 2611.
                out.startElement("cast", this);
                out.emitAttribute("as", ((ListConstructorFunction) target).getTargetType().getStructuredQName());
                out.emitAttribute("flags", "l" + (((ListConstructorFunction) target).isAllowEmpty() ? "e" : ""));
                for (Operand o : operands()) {
                    o.getChildExpression().export(out);
                }
                out.endElement();
            } else {
                super.export(out);
            }
        }
    }


}

