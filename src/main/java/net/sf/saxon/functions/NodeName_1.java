////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.QNameValue;

/**
 * This class supports the node-name() function with a single argument
 */

public class NodeName_1 extends ScalarSystemFunction {

    @Override
    public AtomicValue evaluate(Item item, XPathContext context) throws XPathException {
        return nodeName((NodeInfo) item);
    }

    public static QNameValue nodeName(NodeInfo node) {
        if (node.getLocalPart().isEmpty()) {
            return null;
        }
        return new QNameValue(node.getPrefix(), node.getURI(), node.getLocalPart(), BuiltInAtomicType.QNAME);
    }

    @Override
    public String getCompilerName() {
        return "NodeNameFnCompiler";
    }

}

