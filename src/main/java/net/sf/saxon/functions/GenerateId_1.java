////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.StringEvaluator;
import net.sf.saxon.expr.elab.UnicodeStringEvaluator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.StringElaborator;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.StringValue;

/**
 * This class supports the generate-id() function with one argument
 */

public class GenerateId_1 extends ScalarSystemFunction {


    /**
     * Determine the special properties of this expression. The generate-id()
     * function is a special case: it is considered creative if its operand
     * is creative, so that generate-id(f()) is not taken out of a loop
     * @param arguments the expressions supplied as arguments in the function call
     */

    @Override
    public int getSpecialProperties(Expression[] arguments) {
        int p = super.getSpecialProperties(arguments);
        return p & ~StaticProperty.NO_NODES_NEWLY_CREATED;
    }

    @Override
    public Sequence resultWhenEmpty() {
        return StringValue.EMPTY_STRING;
    }

    @Override
    public AtomicValue evaluate(Item arg, XPathContext context) throws XPathException {
        return generateId((NodeInfo)arg);
    }

    public static StringValue generateId(NodeInfo node) {
        StringBuilder buffer = new StringBuilder(16);
        node.generateId(buffer);
        return new StringValue(buffer.toString());

    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new GenerateIdElaborator();
    }


    public static class GenerateIdElaborator extends StringElaborator {

        public UnicodeStringEvaluator elaborateForUnicodeString(boolean zeroLengthIfAbsent) {
            final SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            final Expression arg = fnc.getArg(0);
            final boolean nullable = Cardinality.allowsZero(arg.getCardinality());
            final ItemEvaluator argEval = arg.makeElaborator().elaborateForItem();

            return context -> {
                NodeInfo node = (NodeInfo) argEval.eval(context);
                if (nullable && node == null) {
                    return EmptyUnicodeString.getInstance();
                }
                return generateId(node).getUnicodeStringValue();
            };

        }

        public StringEvaluator elaborateForString(boolean zeroLengthIfAbsent) {
            final SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            final Expression arg = fnc.getArg(0);
            final boolean nullable = Cardinality.allowsZero(arg.getCardinality());
            final ItemEvaluator argEval = arg.makeElaborator().elaborateForItem();

            return context -> {
                NodeInfo node = (NodeInfo) argEval.eval(context);
                if (nullable && node == null) {
                    return "";
                }
                return generateId(node).getStringValue();
            };

        }
    }
}

