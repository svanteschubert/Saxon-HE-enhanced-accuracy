////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pull;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.trans.XPathException;

/**
 * This class copies a document by using the pull interface to read the input document,
 * and the push interface to write the output document.
 */
public class PullPushCopier {

    private PullProvider in;
    private Receiver out;

    /**
     * Create a PullPushCopier
     *
     * @param in  a PullProvider from which events will be read
     * @param out a Receiver to which copies of the same events will be written
     */

    public PullPushCopier(PullProvider in, Receiver out) {
        this.out = out;
        this.in = in;
    }

    /**
     * Copy the input to the output. This method will open the output Receiver before appending to
     * it, and will close it afterwards.
     *
     * @throws XPathException if, for example, the input is not well-formed
     */

    public void copy() throws XPathException {
        out.open();
        PullPushTee tee = new PullPushTee(in, out);
        new PullConsumer(tee).consume();
        out.close();
    }

    /**
     * Copy the input to the output. This method relies on the caller to open the output Receiver before
     * use and to close it afterwards.
     *
     * @throws XPathException if, for example, the input is not well-formed
     */

    public void append() throws XPathException {
        PullPushTee tee = new PullPushTee(in, out);
        new PullConsumer(tee).consume();
    }
}

