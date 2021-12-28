////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

/**
 * A set of declared notations (in the sense of XSD xs:notation)
 */
public interface NotationSet {

    /**
     * Ask whether a given notation name is present in this set of notations
     *
     * @param uri   the URI part of the notation name
     * @param local the local part of the notation name
     * @return true if the notation name is present
     */
    public boolean isDeclaredNotation(String uri, String local);
}

