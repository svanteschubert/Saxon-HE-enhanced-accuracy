////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.One;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;


/**
 * This class implements the fn:upper-case() function
 */

public class UpperCase extends ScalarSystemFunction {

    @Override
    public AtomicValue evaluate(Item arg, XPathContext context) {
        return StringValue.makeStringValue(arg.getStringValue().toUpperCase());
    }

    @Override
    public One resultWhenEmpty() {
        return ZERO_LENGTH_STRING;
    }

    @Override
    public String getCompilerName() {
        return "ForceCaseCompiler";
    }


}

