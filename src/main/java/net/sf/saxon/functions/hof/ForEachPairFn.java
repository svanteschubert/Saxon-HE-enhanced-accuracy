////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.MappingFunction;
import net.sf.saxon.expr.MappingIterator;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.ExternalObject;
import net.sf.saxon.value.ObjectValue;

/**
 * This class implements the function fn:for-each-pair() (formerly fn:map-pairs()), which is a standard function in XQuery 3.0
 */

public class ForEachPairFn extends SystemFunction {

    /**
     * Get the return type, given knowledge of the actual arguments
     *
     * @param args the actual arguments supplied
     * @return the best available item type that the function will return
     */
    @Override
    public ItemType getResultItemType(Expression[] args) {
        // Item type of the result is the same as the result item type of the function
        ItemType fnType = args[2].getItemType();
        if (fnType instanceof SpecificFunctionType) {
            return ((SpecificFunctionType) fnType).getResultType().getPrimaryType();
        } else {
            return AnyItemType.getInstance();
        }
    }

    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return SequenceTool.toLazySequence(evalMapPairs(
                (Function) arguments[2].head(), arguments[0].iterate(), arguments[1].iterate(), context));
    }

    private SequenceIterator evalMapPairs(final Function function,
                                          SequenceIterator seq0,
                                          SequenceIterator seq1,
                                          final XPathContext context) {
        PairedSequenceIterator pairs = new PairedSequenceIterator(seq0, seq1);
        MappingFunction map = item -> {
            Sequence[] pair = (Sequence[]) ((ExternalObject)item).getObject();
            return dynamicCall(function, context, pair).iterate();
        };
        return new MappingIterator(pairs, map);
    }

    /**
     * Iterator to deliver pairs of items from two underlying iterators, read in parallel.
     * The pair of items is returned wrapped in an ObjectValue.
     */

    private static class PairedSequenceIterator implements SequenceIterator {

        private SequenceIterator seq0;
        private SequenceIterator seq1;
        private Sequence[] args = new Sequence[2];

        public PairedSequenceIterator(SequenceIterator seq0,
                                      SequenceIterator seq1) {
            this.seq0 = seq0;
            this.seq1 = seq1;
        }

        @Override
        public ObjectValue<Sequence[]> next() throws XPathException {
            Item i0 = seq0.next();
            if (i0 == null) {
                close();
                return null;
            }
            Item i1 = seq1.next();
            if (i1 == null) {
                close();
                return null;
            }
            args[0] = i0;
            args[1] = i1;
            return new ObjectValue<>(args);
        }

        @Override
        public void close() {
            seq0.close();
            seq1.close();
        }

    }

}

// Copyright (c) 2018-2020 Saxonica Limited
