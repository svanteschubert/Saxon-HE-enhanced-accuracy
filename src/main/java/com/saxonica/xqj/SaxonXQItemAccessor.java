////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package com.saxonica.xqj;

import net.sf.saxon.om.Item;

import javax.xml.xquery.XQException;

/**
 * All Saxon implementations of XQItemAccessor must implement this interface
 */
public interface SaxonXQItemAccessor {

    /**
     * Get the current item, in the form of a Saxon Item object. This allows access to non-XQJ methods
     * to manipulate the item, which will not necessarily be stable from release to release. The resulting
     * Item will be an instance of either {@link net.sf.saxon.om.NodeInfo} or {@link net.sf.saxon.value.AtomicValue}.
     *
     * @return the current item
     */

    /*@Nullable*/
    Item getSaxonItem() throws XQException;
}

