////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.Fold;
import net.sf.saxon.functions.FoldingFunction;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyFunctionType;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;

/**
 * This class implements the function fn:fold-left(), which is a standard function in XPath 3.0
 */

public class FoldLeftFn extends FoldingFunction {

    @Override
    public Fold getFold(XPathContext context, Sequence... arguments) throws XPathException {
        Sequence arg0 = (Sequence)arguments[0];
        return new FoldLeftFold(context, arg0.materialize(), (Function)arguments[1].head());
    }

    public class FoldLeftFold implements Fold {

        private XPathContext context;
        private Function function;
        private Sequence data;
        private int counter;

        public FoldLeftFold(XPathContext context, GroundedValue zero, Function function) {
            this.context = context;
            this.function = function;
            this.data = zero;
            this.counter = 0;
        }

        @Override
        public void processItem(Item item) throws XPathException {
            Sequence[] args = new Sequence[2];
            args[0] = data;
            args[1] = item;
            // The result can be returned as a LazySequence. Since we are passing it to a user-defined
            // function which can read it repeatedly, we need at the very least to wrap it in a MemoSequence.
            // But wrapping MemoSequences too deeply can cause a StackOverflow when the unwrapping finally
            // takes place; so to avoid this, we periodically ground the value as a real in-memory concrete
            // sequence. We don't want to do this every time because it involves allocating memory.
            Sequence result = dynamicCall(function, context, args);
            if (counter++ % 32 == 0) {
                data = result.materialize();
            } else {
                data = result;
            }
        }

        @Override
        public boolean isFinished() {
            return false;
        }

        @Override
        public Sequence result() {
            return data;
        }
    }

    /**
     * Get the return type, given knowledge of the actual arguments
     *
     * @param args the actual arguments supplied
     * @return the best available item type that the function will return
     */
    @Override
    public ItemType getResultItemType(Expression[] args) {
        // Item type of the result is the same as the result item type of the argument function
        ItemType functionArgType = args[2].getItemType();
        if (functionArgType instanceof AnyFunctionType) {
            // will always be true once the query has been successfully type-checked
            return ((AnyFunctionType) args[2].getItemType()).getResultType().getPrimaryType();
        } else {
            return AnyItemType.getInstance();
        }
    }
}

// Copyright (c) 2013-2020 Saxonica Limited
