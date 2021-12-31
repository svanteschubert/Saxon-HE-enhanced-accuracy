////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.trans.XPathException;

/**
 * Abtract class representing a tuple stream (used to evaluate a FLWOR expression) in push mode
 * (where the provider of tuples activates the consumer of those tuples)
 */
public abstract class TuplePush {

    private Outputter outputter;
    
    protected TuplePush(Outputter outputter) {
        this.outputter = outputter;
    }

    protected Outputter getOutputter() {
        return outputter;
    }

    /**
     * Notify the availability of the next tuple. Before calling this method,
     * the supplier of the tuples must set all the variables corresponding
     * to the supplied tuple in the local stack frame associated with the context object
     *
     *
     * @param context the dynamic evaluation context
     * @throws XPathException if a dynamic error occurs
     */

    public abstract void processTuple(XPathContext context) throws XPathException;

    /**
     * Close the tuple stream, indicating that no more tuples will be supplied
     *
     * @throws XPathException if a dynamic error occurs
     */

    public void close() throws XPathException {
        // default implementation takes no action
    }
}

