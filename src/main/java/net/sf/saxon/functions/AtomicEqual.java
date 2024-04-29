////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.BooleanValue;

/**
 * Implements the XPath 4.0 fn:atomic-equal() function.
 */

public class AtomicEqual extends SystemFunction implements Callable {

    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        AtomicValue op1 = (AtomicValue) arguments[0].head();
        AtomicValue op2 = (AtomicValue) arguments[1].head();
        //System.err.println("AtEq " + op1 + " , " + op2 + " = " + op1.asMapKey().equals(op2.asMapKey()));
        return BooleanValue.get(op1.asMapKey().equals(op2.asMapKey()));
    }

}

