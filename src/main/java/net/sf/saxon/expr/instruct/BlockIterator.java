////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.Operand;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
 * Iterate over the instructions in a sequence of instructions (or an XPath comma expression),
 * concatenating the result of each instruction into a single combined sequence.
 */

public class BlockIterator implements SequenceIterator {

    private Operand[] operanda;
    private int currentChildExpr = 0;
    private SequenceIterator currentIter;
    private XPathContext context;
    private int position = 0;

    public BlockIterator(Operand[] operanda, XPathContext context) {
        this.operanda = operanda;
        this.currentChildExpr = 0;
        this.context = context;
    }

    /**
     * Get the next item in the sequence. <BR>
     *
     * @return the next item, or null if there are no more items.
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs retrieving the next item
     */

    @Override
    public Item next() throws XPathException {
        if (position < 0) {
            return null;
        }
        while (true) {
            if (currentIter == null) {
                currentIter = operanda[currentChildExpr++].getChildExpression().iterate(context);
            }
            Item current = currentIter.next();
            if (current != null) {
                position++;
                return current;
            }
            currentIter = null;
            if (currentChildExpr >= operanda.length) {
                position = -1;
                return null;
            }
        }
    }

    @Override
    public void close() {
        if (currentIter != null) {
            currentIter.close();
        }
    }

}

