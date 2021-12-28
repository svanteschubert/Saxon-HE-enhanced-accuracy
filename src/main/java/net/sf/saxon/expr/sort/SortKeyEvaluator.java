////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;

/**
 * Callback interface used to evaluate sort keys. An instance of this class is passed to the
 * SortedIterator, and is used whenever a sort key value needs to be computed.
 */

public interface SortKeyEvaluator {

    /**
     * Evaluate the n'th sort key of the context item
     */

    /*@Nullable*/
    AtomicValue evaluateSortKey(int n, XPathContext context) throws XPathException;
}

