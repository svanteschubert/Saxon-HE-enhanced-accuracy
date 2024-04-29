////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;

/**
 * Iterate over the instructions in a sequence of instructions (or an XPath comma expression),
 * concatenating the result of each instruction into a single combined sequence.
 *
 * This abstract class has concrete subclasses handling the interpreted and compiled cases
 */

public abstract class AbstractBlockIterator implements SequenceIterator {

    protected int size;
    protected int currentOperand;
    protected SequenceIterator currentIter;
    protected XPathContext context;

    // Zero-argument constructor for use from bytecode
    public AbstractBlockIterator() {}

    public AbstractBlockIterator(int size, XPathContext context) {
        this.size = size;
        this.context = context;
        this.currentOperand = 0;
    }

    /**
     * Initializer for use from bytecode
     *
     * @param size    the size
     * @param context the XPath context
     */

    public void init(int size, XPathContext context) {
        this.size = size;
        this.context = context;
        this.currentOperand = 0;
    }

    /**
     * Get the next item in the sequence.
     *
     * @return the next item, or null if there are no more items.
     */

    @Override
    public Item next() {
        if (currentOperand < 0) {
            return null;
        }
        while (true) {
            if (currentIter == null) {
                try {
                    currentIter = getNthChildIterator(currentOperand++);
                } catch (XPathException e) {
                    throw new UncheckedXPathException(e);
                }
            }
            Item current = currentIter.next();
            if (current != null) {
                return current;
            }
            currentIter = null;
            if (currentOperand >= size) {
                currentOperand = -1;
                return null;
            }
        }
    }

    public abstract SequenceIterator getNthChildIterator(int n) throws XPathException;

    @Override
    public void close() {
        if (currentIter != null) {
            currentIter.close();
        }
        currentOperand = -1;
    }

}

