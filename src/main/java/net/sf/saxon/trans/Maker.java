////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

/**
 * Interface representing a factory class for instances of a specific type
 */
public interface Maker<T> {

    /**
     * Obtain an instance of type T, either by making a new instance or by reusing an existing instance
     * @throws XPathException if the attempt fails
     */

    T make() throws XPathException;

}

// Copyright (c) 2015-2020 Saxonica Limited
