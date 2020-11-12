////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pull;

import net.sf.saxon.trans.XPathException;

/**
 * A PullConsumer consumes all the events supplied by a PullProvider, doing nothing
 * with them. The class exists so that PullFilters on the pipeline can produce side-effects.
 * For example, this class can be used to validate a document, where the side effects are
 * error messages.
 */

public class PullConsumer {

    private PullProvider in;

    /**
     * Create a PullConsumer that swallows the events read from a given pull provider
     *
     * @param in the PullProvider from which events are to be read and swallowed up
     */

    public PullConsumer(PullProvider in) {
        this.in = in;
    }

    /**
     * Consume the input
     *
     * @throws net.sf.saxon.trans.XPathException if (for example) the parser input is not well-formed
     *
     */

    public void consume() throws XPathException {
        while (true) {
            if (in.next() == PullProvider.Event.END_OF_INPUT) {
                return;
            }
        }
    }
}

