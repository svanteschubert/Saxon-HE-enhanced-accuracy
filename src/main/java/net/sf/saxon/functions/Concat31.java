////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.StringElaborator;
import net.sf.saxon.expr.elab.StringEvaluator;
import net.sf.saxon.expr.elab.UnicodeStringEvaluator;
import net.sf.saxon.expr.oper.OperandArray;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.str.UniStringConsumer;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.Arrays;

/**
 * Implementation of the fn:concat() function as defined in XPath 2.0, 3.0, and 3.1
 */


public class Concat31 extends SystemFunction implements PushableFunction {

    @Override
    protected Sequence resultIfEmpty(int arg) {
        return null;
    }

    /**
     * Get the item type of the function item
     *
     * @return the function item's type
     */
    @Override
    public FunctionItemType getFunctionItemType() {
        SequenceType[] argTypes = new SequenceType[getArity()];
        Arrays.fill(argTypes, SequenceType.OPTIONAL_ATOMIC);
        return new SpecificFunctionType(argTypes, SequenceType.SINGLE_STRING);
    }

    /**
     * Allow the function to create an optimized call based on the values of the actual arguments
     *
     * @param visitor     the expression visitor
     * @param contextInfo information about the context item
     * @param arguments   the supplied arguments to the function call. Note: modifying the contents
     *                    of this array should not be attempted, it is likely to have no effect.
     * @return either a function call on this function, or an expression that delivers
     * the same result, or null indicating that no optimization has taken place
     * @throws XPathException if an error is detected
     */
    @Override
    public Expression makeOptimizedFunctionCall(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, final Expression... arguments) throws XPathException {
        if (arguments.length >= 2 && OperandArray.every(arguments,
                arg -> arg.getCardinality() == StaticProperty.EXACTLY_ONE && arg.getItemType() == BuiltInAtomicType.BOOLEAN)) {
            // Warning if all the arguments are booleans: probably a misuse of the '||' operator
            visitor.getStaticContext().issueWarning(
                    "Did you intend to apply string concatenation to boolean operands? "
                            + "Perhaps you intended 'or' rather than '||'. "
                            + "To suppress this warning, use string() on the arguments.", SaxonErrorCode.SXWN9035, arguments[0].getLocation());
        }
        return null;
//        return new SystemFunctionCall.Optimized(this, arguments) {
//            @Override
//            @CSharpModifiers(code = {"public", "override"})
//            public UnicodeString evaluateAsString(XPathContext context) throws XPathException {
//                UnicodeBuilder buffer = new UnicodeBuilder();
//                for (Operand o: operands()) {
//                    Item item = o.getChildExpression().evaluateItem(context);
//                    if (item != null) {
//                        buffer.accept(item.getUnicodeStringValue());
//                    }
//                }
//                return buffer.toUnicodeString();
//            }
//
//            @Override
//            @CSharpModifiers(code = {"public", "override"})
//            public Item evaluateItem(XPathContext context) throws XPathException {
//                return new StringValue(evaluateAsString(context));
//            }
//        };

    }

    @Override
    public StringValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        UnicodeBuilder builder = new UnicodeBuilder();
        for (Sequence arg : arguments) {
            GroundedValue val = arg.materialize();
            if (val.getLength() > 1) {
                throw new XPathException(
                        "Prior to 4.0, arguments to concat() must be single items", "XPTY0004").asTypeError();
            }
            Item head = val.head();
            if (head != null) {
                builder.accept(head.getUnicodeStringValue());
            }
        }
        return new StringValue(builder.toUnicodeString());
    }

    @Override
    public void process(Outputter destination, XPathContext context, Sequence[] arguments) throws XPathException {
        UniStringConsumer output = destination.getStringReceiver(false, Loc.NONE);
        output.open();
        for (Sequence arg : arguments) {
            Item head = arg.head();
            if (head != null) {
                output.accept(head.getUnicodeStringValue());
            }
        }
        output.close();
    }

    /**
     * Get the required type of the nth argument
     */

    @Override
    public SequenceType getRequiredType(int arg) {
        return getDetails().paramTypes[0];
        // concat() is a special case
    }


    @Override
    public Elaborator getElaborator() {
        return new Concat31FnElaborator();
    }


    public static class Concat31FnElaborator extends StringElaborator {

        @Override
        public boolean returnZeroLengthWhenAbsent() {
            return true;
        }

        public UnicodeStringEvaluator elaborateForUnicodeString(boolean zeroLengthWhenAbsent) {
            SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            int arity = fnc.getArity();
            switch (arity) {
                case 2 -> {
                    UnicodeStringEvaluator e0 = fnc.getArg(0).makeElaborator().elaborateForUnicodeString(true);
                    UnicodeStringEvaluator e1 = fnc.getArg(1).makeElaborator().elaborateForUnicodeString(true);
                    return cxt -> e0.eval(cxt).concat(e1.eval(cxt));
                }
                case 3 -> {
                    UnicodeStringEvaluator e0 = fnc.getArg(0).makeElaborator().elaborateForUnicodeString(true);
                    UnicodeStringEvaluator e1 = fnc.getArg(1).makeElaborator().elaborateForUnicodeString(true);
                    UnicodeStringEvaluator e2 = fnc.getArg(2).makeElaborator().elaborateForUnicodeString(true);
                    return cxt -> e0.eval(cxt).concat(e1.eval(cxt)).concat(e2.eval(cxt));
                }
                default -> {
                    UnicodeStringEvaluator[] evaluators = new UnicodeStringEvaluator[arity];
                    for (int i = 0; i < arity; i++) {
                        evaluators[i] = fnc.getArg(i).makeElaborator().elaborateForUnicodeString(true);
                    }
                    return cxt -> {
                        UnicodeBuilder builder = new UnicodeBuilder();
                        for (int i = 0; i < arity; i++) {
                            UnicodeString s = evaluators[i].eval(cxt);
                            builder.append(s);
                        }
                        return builder.toUnicodeString();
                    };
                }
            }
        }

        public StringEvaluator elaborateForString(boolean zeroLengthWhenAbsent) {
            SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            int arity = fnc.getArity();
            switch (arity) {
                case 2: {
                    StringEvaluator e0 = fnc.getArg(0).makeElaborator().elaborateForString(true);
                    StringEvaluator e1 = fnc.getArg(1).makeElaborator().elaborateForString(true);
                    return cxt -> e0.eval(cxt) + e1.eval(cxt);
                }
                case 3: {
                    StringEvaluator e0 = fnc.getArg(0).makeElaborator().elaborateForString(true);
                    StringEvaluator e1 = fnc.getArg(1).makeElaborator().elaborateForString(true);
                    StringEvaluator e2 = fnc.getArg(2).makeElaborator().elaborateForString(true);
                    return cxt -> e0.eval(cxt) + e1.eval(cxt) + e2.eval(cxt);
                }
                default:
                    StringEvaluator[] evaluators = new StringEvaluator[arity];
                    for (int i = 0; i < arity; i++) {
                        evaluators[i] = fnc.getArg(i).makeElaborator().elaborateForString(true);
                    }
                    return cxt -> {
                        StringBuilder builder = new StringBuilder();
                        for (int i = 0; i < arity; i++) {
                            String s = evaluators[i].eval(cxt);
                            builder.append(s);
                        }
                        return builder.toString();
                    };
            }
        }


    }


}

