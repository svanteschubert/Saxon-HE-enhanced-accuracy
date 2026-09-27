// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

public interface ArityTwoFunction {

    /**
     * Call a function with two arguments
     * @param context the dynamic evaluation context
     * @param arg0 the first argument
     * @param arg1 the second argument
     * @return the result of the function call
     * @throws XPathException if the call fails with a dynamic error
     */

    Sequence call2(XPathContext context, Sequence arg0, Sequence arg1) throws XPathException;
}


