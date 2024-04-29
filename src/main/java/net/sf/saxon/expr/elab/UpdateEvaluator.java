////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.elab;

import net.sf.saxon.expr.PendingUpdateList;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.trans.XPathException;

/**
 * Interface implemented by XQuery Update expressions, to evaluate an update expression by
 * adding update primitives to a supplied pending update list.
 */
@FunctionalInterface
public interface UpdateEvaluator {

    /**
     * Add update primitives to a pending update list
     * @param context  The XPath evaluation context
     * @param updates  The pending update list
     * @throws XPathException in the event of a dynamic error
     */
    void registerUpdates(XPathContext context, PendingUpdateList updates) throws XPathException;
}

