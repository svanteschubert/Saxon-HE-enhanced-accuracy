////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A helper class for implementing the {@link Destination} interface
 */
public class DestinationHelper {

    private Destination helpee;

    private List<Action> listeners = new ArrayList<>();

    public DestinationHelper(Destination helpee) {
        this.helpee = helpee;
    }

    /**
     * Register a listener to be notified when this destination is closed
     * @param listener an object to be notified when writing to the destination
     *                 is successfully completed
     */

    final public void onClose(Action listener) {
        listeners.add(listener);
    }

    /**
     * Close the destination and notify all registered listeners that it has been closed.
     * This method is intended for internal use by Saxon. The method first calls {@code close}
     * to close the destination, then it calls {@link Consumer#accept} on each of the
     * listeners in turn to notify the fact that it has been closed.
     * @throws SaxonApiException if the close() method throws {@code SaxonApiException}.
     */

    public void closeAndNotify() throws SaxonApiException {
        helpee.close();
        for (Action action : listeners) {
            action.act();
        }
    }

    public List<Action> getListeners() {
        return listeners;
    }


}

