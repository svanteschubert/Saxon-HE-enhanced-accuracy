////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.One;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.AtomicValue;
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
    public ZeroOrOne resultWhenEmpty() {
        return One.string("");
    }

    @Override
    public AtomicValue evaluate(Item arg, XPathContext context) throws XPathException {
        return generateId((NodeInfo)arg);
    }

    public static StringValue generateId(NodeInfo node) {
        FastStringBuffer buffer = new FastStringBuffer(FastStringBuffer.C16);
        node.generateId(buffer);
        buffer.condense();
        return new StringValue(buffer);

    }

    @Override
    public String getCompilerName() {
        return "GenerateIdCompiler";
    }

}

