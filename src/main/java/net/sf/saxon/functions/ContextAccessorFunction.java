////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

/**
 * A ContextAccessorFunction is a function that is dependent on the dynamic context. In the case
 * of dynamic function calls, the context is bound at the point where the function is created,
 * not at the point where the function is called.
 */

public abstract class ContextAccessorFunction extends SystemFunction {

    /**
     * Bind a context item to appear as part of the function's closure. If this method
     * has been called, the supplied context item will be used in preference to the
     * context item at the point where the function is actually called.
     * @param context the context to which the function applies. Must not be null.
     */

    public abstract Function bindContext(XPathContext context) throws XPathException;


    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return bindContext(context).call(context, arguments);
    }

}

