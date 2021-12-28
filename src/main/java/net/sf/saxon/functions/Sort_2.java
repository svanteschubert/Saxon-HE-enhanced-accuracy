////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

import java.util.List;

/**
 * This class implements the function fn:sort#2, according to the new XPath 3.1 spec in bug 29792
 */

public class Sort_2 extends Sort_1 {

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        final List<ItemToBeSorted> inputList = getItemsToBeSorted(arguments[0]);
        return doSort(inputList, getCollation(context, arguments[1]), context);
    }

    protected StringCollator getCollation(XPathContext context, Sequence collationArg) throws XPathException {
        StringValue secondArg = (StringValue)collationArg.head();
        if (secondArg == null) {
            return  context.getConfiguration().getCollation(getRetainedStaticContext().getDefaultCollationName());
        } else {
            return context.getConfiguration().getCollation(secondArg.getStringValue(), getStaticBaseUriString());
        }
    }


}
