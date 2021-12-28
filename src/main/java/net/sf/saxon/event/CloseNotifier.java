////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.s9api.Action;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.trans.XPathException;

import java.util.List;

/**
 * A receiver that performs specified actions when closed
 */

public class CloseNotifier extends ProxyReceiver {

    private final List<Action> actionList;

    public CloseNotifier(Receiver next, List<Action> actionList) {
        super(next);
        this.actionList = actionList;
    }


    /**
     * End of output. Note that closing this receiver also closes the rest of the
     * pipeline.
     */
    @Override
    public void close() throws XPathException {
        super.close();
        try {
            if (actionList != null) {
                for (Action action : actionList) {
                    action.act();
                }
            }
        } catch (SaxonApiException e) {
            throw XPathException.makeXPathException(e);
        }
    }

}

