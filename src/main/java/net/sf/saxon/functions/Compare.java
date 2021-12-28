////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.StringValue;

/**
 * This class implements the XPath 2.0 fn:compare() function
 */

public class Compare extends CollatingFunctionFixed {

    private static Int64Value compare(StringValue s1, StringValue s2, AtomicComparer comparer) throws XPathException {
        if (s1 == null || s2 == null) {
            return null;
        }
        int result = comparer.compareAtomicValues(s1, s2);
        if (result < 0) {
            return Int64Value.MINUS_ONE;
        } else if (result > 0) {
            return Int64Value.PLUS_ONE;
        } else {
            return Int64Value.ZERO;
        }
    }

    @Override
    public ZeroOrOne call(XPathContext context, Sequence[] arguments) throws XPathException {
        StringValue arg0 = (StringValue) arguments[0].head();
        StringValue arg1 = (StringValue) arguments[1].head();
        GenericAtomicComparer comparer = new GenericAtomicComparer(getStringCollator(), context);
        Int64Value result = compare(arg0, arg1, comparer);
        return new ZeroOrOne(result);
    }

}

