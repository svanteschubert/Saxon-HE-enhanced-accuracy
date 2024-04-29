////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.elab.StringEvaluator;
import net.sf.saxon.expr.elab.UnicodeStringEvaluator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.StringElaborator;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.SimpleNodeConstructor;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;


/**
 * Implement XPath function string() with a single argument
 */

public class String_1 extends ScalarSystemFunction {

    @Override
    public AtomicValue evaluate(Item arg, XPathContext context) throws XPathException {
        try {
            return new StringValue(arg.getUnicodeStringValue());
        } catch (UncheckedXPathException err) {
            throw err.getXPathException();
        }
    }

    @Override
    public Sequence resultWhenEmpty() {
        return StringValue.EMPTY_STRING;
    }

    /**
     * Allow the function to create an optimized call based on the values of the actual arguments
     *
     * @param visitor     the expression visitor
     * @param contextInfo information about the context item
     * @param arguments   the supplied arguments to the function call
     * @return either a function call on this function, or an expression that delivers
     * the same result, or null indicating that no optimization has taken place
     * @throws net.sf.saxon.trans.XPathException if an error is detected
     */
    @Override
    public Expression makeOptimizedFunctionCall(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, Expression... arguments) throws XPathException {
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        Expression arg = arguments[0];
        if (th.isSubType(arg.getItemType(), BuiltInAtomicType.STRING) &&
                arg.getCardinality() == StaticProperty.EXACTLY_ONE) {
            return arg;
        }
        if (arg instanceof SimpleNodeConstructor && arg.getCardinality() == StaticProperty.EXACTLY_ONE) {
            return ((SimpleNodeConstructor) arg).getSelect();
        }
        return null;
    }

    @Override
    public String getStreamerName() {
        return "StringFn";
    }

    /**
     * Make an elaborator for a system function call on this function
     *
     * @return a suitable elaborator; or null if no custom elaborator is available
     */
    @Override
    public Elaborator getElaborator() {
        return new StringFnElaborator();
    }

    public static class StringFnElaborator extends StringElaborator {

        public UnicodeStringEvaluator elaborateForUnicodeString(boolean zeroLengthWhenAbsent) {
            SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            Expression arg = fnc.getArg(0);
            UnicodeStringEvaluator argEval = arg.makeElaborator().elaborateForUnicodeString(true);
            return context -> {
                try {
                    return argEval.eval(context);
                } catch (UncheckedXPathException err) {
                    throw err.getXPathException();
                }
            };
        }

        public StringEvaluator elaborateForString(boolean zeroLengthWhenAbsent) {
            SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            Expression arg = fnc.getArg(0);
            StringEvaluator argEval = arg.makeElaborator().elaborateForString(true);
            return context -> {
                try {
                    return argEval.eval(context);
                } catch (UncheckedXPathException err) {
                    throw err.getXPathException();
                }
            };
        }
    }
}

