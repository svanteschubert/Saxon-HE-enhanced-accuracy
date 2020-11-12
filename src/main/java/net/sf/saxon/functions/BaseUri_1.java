////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AnyURIValue;

/**
 * This class implements the fn:base-uri() function in XPath 2.0
 */

public class BaseUri_1 extends SystemFunction implements Callable {

    @Override
    public ZeroOrOne call(XPathContext context, Sequence[] arguments) throws XPathException {
        NodeInfo node = (NodeInfo)arguments[0].head();
        if (node == null) {
            return ZeroOrOne.empty();
        }
        String s = node.getBaseURI();
        if (s == null) {
            return ZeroOrOne.empty();
        }
        return new ZeroOrOne(new AnyURIValue(s));

    }

    @Override
    public String getCompilerName() {
        return "BaseURICompiler";
    }

}

