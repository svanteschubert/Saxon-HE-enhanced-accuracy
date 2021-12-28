////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.Controller;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;

/**
 * An UpdateAgent is a callback class that is called to handle a document after it has been updated.
 * Typically the UpdateAgent might take responsibility for writing the updated document back to
 * persistent storage.
 */
public interface UpdateAgent {

    /**
     * Handle an updated document.
     * This method is called by {@link XQueryExpression#runUpdate(DynamicQueryContext, UpdateAgent)}
     * once for each document (or more generally, for the root of each tree) that has been modified
     * by the update query.
     *
     * @param node       the root of the tree that has been updated
     * @param controller the Controller that was used for executing the query
     * @throws XPathException if the callback code cannot handle the updated document
     */

    void update(NodeInfo node, Controller controller) throws XPathException;
}

