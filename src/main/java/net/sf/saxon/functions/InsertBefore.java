////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.NumericValue;

/**
 * The XPath 2.0 insert-before() function
 */


public class InsertBefore extends SystemFunction {

    /**
     * Evaluate the expression as a general function call
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        NumericValue n = (NumericValue) arguments[1].head();
        int pos = (int) n.longValue();
        return SequenceTool.toLazySequence(
                new InsertIterator(arguments[0].iterate(), arguments[2].iterate(), pos));
    }

    @Override
    public Expression makeFunctionCall(Expression[] arguments) {
        return new SystemFunctionCall(this, arguments) {
            @Override
            public ItemType getItemType() {
                return Type.getCommonSuperType(getArg(0).getItemType(), getArg(2).getItemType());
            }
        };
    }

    /**
     * Insertion iterator. This is supplied with an iterator over the base sequence,
     * an iterator over the sequence to be inserted, and the insert position.
     */

    public static class InsertIterator implements SequenceIterator {

        private SequenceIterator base;
        private SequenceIterator insert;
        private int insertPosition;
        private int position = 0;
        private boolean inserting = false;

        public InsertIterator(SequenceIterator base, SequenceIterator insert, int insertPosition) {
            this.base = base;
            this.insert = insert;
            this.insertPosition = Math.max(insertPosition, 1);
            this.inserting = insertPosition == 1;
        }


        @Override
        public Item next() throws XPathException {
            Item nextItem;
            if (inserting) {
                nextItem = insert.next();
                if (nextItem == null) {
                    inserting = false;
                    nextItem = base.next();
                }
            } else {
                if (position == insertPosition - 1) {
                    nextItem = insert.next();
                    if (nextItem == null) {
                        nextItem = base.next();
                    } else {
                        inserting = true;
                    }
                } else {
                    nextItem = base.next();
                    if (nextItem == null && position < insertPosition - 1) {
                        inserting = true;
                        nextItem = insert.next();
                    }
                }
            }
            if (nextItem == null) {
                position = -1;
                return null;
            } else {
                position++;
                return nextItem;
            }
        }

        @Override
        public void close() {
            base.close();
            insert.close();
        }

    }

    @Override
    public String getStreamerName() {
        return "InsertBefore";
    }

}

