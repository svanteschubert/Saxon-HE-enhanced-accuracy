////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.Reverse;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyFunctionType;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;

/**
 * This class implements the function fn:fold-right(), which is a standard function in XQuery 1.1
 */

public class FoldRightFn extends SystemFunction {

    /**
     * Get the return type, given knowledge of the actual arguments
     *
     * @param args the actual arguments supplied
     * @return the best available item type that the function will return
     */
    @Override
    public ItemType getResultItemType(Expression[] args) {
        // Item type of the result is the same as the result item type of the function
        ItemType functionArgType = args[2].getItemType();
        if (functionArgType instanceof AnyFunctionType) {
            // will always be true once the query has been successfully type-checked
            return ((AnyFunctionType) functionArgType).getResultType().getPrimaryType();
        } else {
            return AnyItemType.getInstance();
        }
    }

    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return evalFoldRight((Function) arguments[2].head(), arguments[1].materialize(), arguments[0].iterate(), context);
    }

    private Sequence evalFoldRight(final Function function,
                                   Sequence zero, SequenceIterator base, XPathContext context) throws XPathException {
        SequenceIterator reverseBase = Reverse.getReverseIterator(base);
        Sequence[] args = new Sequence[2];
        Item item;
        while ((item = reverseBase.next()) != null) {
            args[0] = item;
            args[1] = zero.materialize();
            try {
                zero = dynamicCall(function, context, args);
            } catch (XPathException e) {
                e.maybeSetContext(context);
                throw e;
            }
        }
        return zero;
    }
}


// Copyright (c) 2018-2020 Saxonica Limited
