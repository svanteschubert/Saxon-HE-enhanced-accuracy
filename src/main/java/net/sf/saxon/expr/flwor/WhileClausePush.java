////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.elab.BooleanEvaluator;
import net.sf.saxon.trans.XPathException;

/**
 * This class represents the tuple stream delivered as the output of a while clause in a
 * FLWOR expression: that is, it supplies all the tuples in its input stream that satisfy
 * a specified predicate until it finds one that doesn't. It does not change the values
 * of any variables in the tuple stream.
 */
public class WhileClausePush extends TuplePush {

    private final TuplePush destination;
    private final BooleanEvaluator predicate;
    private boolean finished = false;

    public WhileClausePush(Outputter outputter, TuplePush destination, BooleanEvaluator predicate) {
        super(outputter);
        this.destination = destination;
        this.predicate = predicate;
    }

    /*
     * Process the next tuple.
     */
    @Override
    public void processTuple(XPathContext context) throws XPathException {
        if (finished) {
            return; // TODO: we have no way to stop the stream of tuples in push mode
        }
        if (predicate.eval(context)) {
            destination.processTuple(context);
        } else {
            finished = true;
        }
    }

    /*
     * Notify the end of the tuple stream
     */
    @Override
    public void close() throws XPathException {
        destination.close();
    }
}

