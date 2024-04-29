////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.Operand;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
 * Iterate over the instructions in a sequence of instructions (or an XPath comma expression),
 * concatenating the result of each instruction into a single combined sequence.
 */

public class BlockIterator extends AbstractBlockIterator {

    private final Operand[] operanda;

    public BlockIterator(Operand[] operanda, XPathContext context) {
        super(operanda.length, context);
        this.operanda = operanda;

    }

    @Override
    public SequenceIterator getNthChildIterator(int n) throws XPathException {
        return operanda[n].getChildExpression().iterate(context);
    }


}

