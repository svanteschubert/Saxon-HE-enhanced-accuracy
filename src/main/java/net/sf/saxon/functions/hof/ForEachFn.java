////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.expr.*;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SpecificFunctionType;

/**
 * This class implements the function fn:for-each() (formerly fn:map), which is a standard function in XQuery 3.0
 */

public class ForEachFn extends SystemFunction {

    /**
     * Get the return type, given knowledge of the actual arguments
     *
     * @param args the actual arguments supplied
     * @return the best available item type that the function will return
     */
    @Override
    public ItemType getResultItemType(Expression[] args) {
        // Item type of the result is the same as the result item type of the function
        ItemType fnType = args[1].getItemType();
        if (fnType instanceof SpecificFunctionType) {
            return ((SpecificFunctionType) fnType).getResultType().getPrimaryType();
        } else {
            return AnyItemType.getInstance();
        }
    }

    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return SequenceTool.toLazySequence(
                evalMap((Function) arguments[1].head(), arguments[0].iterate(), context));
    }

    private SequenceIterator evalMap(final Function function, SequenceIterator base, final XPathContext context) {
        MappingFunction map = new MappingFunction() {
            private final Sequence[] args = new Sequence[1];

            @Override
            public SequenceIterator map(Item item) throws XPathException {
                args[0] = item;
                return dynamicCall(function, context, args).iterate();
            }
        };
        return new MappingIterator(base, map);
    }

}

// Copyright (c) 2018-2020 Saxonica Limited
