////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.MappingIterator;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.AbstractBlockIterator;
import net.sf.saxon.functions.Count;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.Int64Value;

import java.util.function.BiFunction;

/**
 * This class implements the function fn:for-each() which is a standard function from XQuery 3.0
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
                evalMap((FunctionItem) arguments[1].head(), arguments[0].iterate(), context));
    }

    private SequenceIterator evalMap(FunctionItem function, SequenceIterator base, XPathContext context) throws XPathException {
        switch (function.getArity()) {
            case 0:
                int count = Count.count(base);
                GroundedValue val = function.call(context, new Sequence[]{}).materialize();
                return new RepeatingIterator(val, count);
            case 1:
                return MappingIterator.map(base, item -> dynamicCall(function, context, item).iterate());
            case 2:
                return new PositionalMappingIterator(base, (item, pos) -> {
                    try {
                        return dynamicCall(function, context, item, Int64Value.makeIntegerValue(pos));
                    } catch (XPathException e) {
                        throw new UncheckedXPathException(e);
                    }
                });
            default:
                throw new XPathException("Wrong arity for fn:for-each callback");

        }

    }

    public static class PositionalMappingIterator implements SequenceIterator {

        private final SequenceIterator base;
        private final BiFunction<Item, Integer, Sequence> action;
        private SequenceIterator results = null;
        int position = 0;

        /**
         * Construct a MappingIterator that will apply a specified MappingFunction to
         * each Item returned by the base iterator.
         *
         * @param base   the base iterator
         * @param action the mapping function to be applied: a function from items to SequenceIterators.
         */

        public PositionalMappingIterator(SequenceIterator base, BiFunction<Item, Integer, Sequence> action) {
            this.base = base;
            this.action = action;
        }

        @Override
        public Item next() {
            Item nextItem;
            while (true) {
                if (results != null) {
                    nextItem = results.next();
                    if (nextItem != null) {
                        break;
                    } else {
                        results = null;
                    }
                }
                Item nextSource = base.next();
                position++;
                if (nextSource != null) {
                    // Call the supplied mapping function
                    SequenceIterator obj = action.apply(nextSource, position).iterate();

                    // The result may be null (representing an empty sequence),
                    //  or a SequenceIterator (any sequence)

                    if (obj != null) {
                        results = obj;
                        nextItem = results.next();
                        if (nextItem == null) {
                            results = null;
                        } else {
                            break;
                        }
                    }
                    // now go round the loop to get the next item from the base sequence
                } else {
                    results = null;
                    return null;
                }
            }

            return nextItem;
        }

        @Override
        public void close() {
            if (results != null) {
                results.close();
            }
            base.close();
        }


    }

    public static class RepeatingIterator extends AbstractBlockIterator {

        private final GroundedValue baseValue;

        public RepeatingIterator(GroundedValue baseValue, int count) {
            super(count);
            this.baseValue = baseValue;
        }

        @Override
        public SequenceIterator getNthChildIterator(int n) {
            return baseValue.iterate();
        }
    }
}

// Copyright (c) 2018-2026 Saxonica Limited
