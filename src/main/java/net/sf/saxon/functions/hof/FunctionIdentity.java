////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.AbstractFunction;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;


/**
 * This class implements the function function-identity(), which is a standard function in XPath 4.0
 */

public class FunctionIdentity extends SystemFunction {



    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public StringValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        FunctionItem f = (FunctionItem) arguments[0].head();
        if (f instanceof AbstractFunction) {
            return new StringValue(((AbstractFunction)f).getUniqueIdentifier());
        } else {
            return new StringValue("F_" + System.identityHashCode(f));
        }
    }
}

// Copyright (c) 2012-2026 Saxonica Limited
