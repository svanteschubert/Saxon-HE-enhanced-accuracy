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
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

/**
 * This class supports the name() function with one argument
 */

public class Name_1 extends ScalarSystemFunction {

    @Override
    public StringValue evaluate(Item item, XPathContext context) throws XPathException {
        return StringValue.makeStringValue(((NodeInfo) item).getDisplayName());
    }

    @Override
    public ZeroOrOne resultWhenEmpty() {
        return ZERO_LENGTH_STRING;
    }

    @Override
    public String getCompilerName() {
        return "NameCompiler";
    }

}

