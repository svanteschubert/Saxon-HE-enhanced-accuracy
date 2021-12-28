////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.MutableNodeInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;

import java.util.Set;

/**
 * A PendingUpdateList is created by updating expressions in XQuery Update.
 * <p>The implementation of this interface is in Saxon-EE.</p>
 */
public interface PendingUpdateList {

    /**
     * Apply the pending updates
     *
     * @param context        the XPath dynamic evaluation context
     * @param validationMode the revalidation mode from the static context
     * @throws XPathException if the operation fails
     */

    void apply(XPathContext context, int validationMode) throws XPathException;

    /**
     * Get the root nodes of the trees that are affected by updates in the pending update list
     *
     * @return the root nodes of affected trees, as a Set
     */

    Set<MutableNodeInfo> getAffectedTrees();

    /**
     * Add a put() action to the pending update list
     *
     * @param node       (the first argument of put())
     * @param uri        (the second argument of put())
     * @param originator the originating put() expression, for diagnostics
     */

    void addPutAction(NodeInfo node, String uri, Expression originator) throws XPathException;
}

