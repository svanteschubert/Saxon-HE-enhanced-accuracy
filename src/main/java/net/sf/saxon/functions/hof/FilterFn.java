////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.expr.ItemMappingFunction;
import net.sf.saxon.expr.ItemMappingIterator;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;

/**
 * This class implements the function fn:filter(), which is a standard function in XQuery 3.0
 */

public class FilterFn extends SystemFunction {

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return SequenceTool.toLazySequence(
                evalFilter((Function) arguments[1].head(),
                           arguments[0].iterate(), context));
    }

    private SequenceIterator evalFilter(
            final Function function, SequenceIterator base, final XPathContext context) {
        ItemMappingFunction map = new ItemMappingFunction() {
            private final Sequence[] args = new Sequence[1];

            @Override
            public Item mapItem(Item item) throws XPathException {
                args[0] = item;
                BooleanValue result = (BooleanValue) dynamicCall(function, context, args).head();
                return result.getBooleanValue() ? item : null;
            }
        };
        return new ItemMappingIterator(base, map);
    }

    @Override
    public String getStreamerName() {
        return "FilterFn";
    }
}


// Copyright (c) 2018-2020 Saxonica Limited
