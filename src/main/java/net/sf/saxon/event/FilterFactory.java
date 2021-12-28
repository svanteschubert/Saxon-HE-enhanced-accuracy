////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

/**
 * Factory class to create a ProxyReceiver which filters events on a push pipeline
 */
@FunctionalInterface
public interface FilterFactory {

    /**
     * Make a Receiver to filter events on a push pipeline
     *
     * @param next the next receiver in the pipeline
     * @return a Receiver initialized to send events to the next receiver in the pipeine
     */

    Receiver makeFilter(Receiver next);
}
