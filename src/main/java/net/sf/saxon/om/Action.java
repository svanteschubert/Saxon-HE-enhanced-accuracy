////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.trans.XPathException;

/**
 * A functional interface that simply encapsulates a callback action of any kind,
 * allowing the action to fail with an XPathException.
 */

@FunctionalInterface
public interface Action {
    /**
     * Perform the action
     * @throws XPathException if the action fails
     */
    void doAction() throws XPathException;
}

