////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AnyURIValue;
import net.sf.saxon.value.StringValue;


/**
 * This class supports the function namespace-uri-for-prefix()
 */

public class NamespaceForPrefix extends SystemFunction implements Callable {

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public ZeroOrOne call(XPathContext context, Sequence[] arguments) throws XPathException {
        AnyURIValue result = namespaceUriForPrefix((StringValue) arguments[0].head(), (NodeInfo) arguments[1].head());
        return new ZeroOrOne(result);
    }

    /**
     * Private supporting method
     *
     * @param p       the prefix
     * @param element the element node
     * @return the corresponding namespace, or null if not in scope
     */

    /*@Nullable*/
    private static AnyURIValue namespaceUriForPrefix(StringValue p, NodeInfo element) {
        String prefix;
        if (p == null) {
            prefix = "";
        } else {
            prefix = p.getStringValue();
        }
        NamespaceResolver resolver = element.getAllNamespaces();
        String uri = resolver.getURIForPrefix(prefix, true);
        if (uri == null || uri.isEmpty()) {
            return null;
        }
        return new AnyURIValue(uri);
    }

}

