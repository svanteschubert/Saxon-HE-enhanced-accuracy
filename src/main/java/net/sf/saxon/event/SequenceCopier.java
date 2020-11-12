////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
 * Copies a sequence, supplied as a SequenceIterator, to a push pipeline, represented by
 * a SequenceReceiver
 */
public class SequenceCopier {

    private SequenceCopier() {
    }

    /**
     * Copy a sequence, supplied as a SequenceIterator, to a push pipeline, represented by
     * a SequenceReceiver
     * @param in an iterator over the input sequence
     * @param out a Receiver to receive the output sequence. The method will open this receiver at the
     *            start and close it on completion.
     * @throws XPathException if a failure occurs reading the input or writing the output
     */

    public static void copySequence(SequenceIterator in, Receiver out) throws XPathException {
        out.open();
        in.forEachOrFail(out::append);
        out.close();
    }
}

