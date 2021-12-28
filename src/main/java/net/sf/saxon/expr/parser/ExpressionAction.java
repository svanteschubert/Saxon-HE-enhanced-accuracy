////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.trans.XPathException;

/**
 * General action class which can be used to process all nodes on an expression tree
 */
public interface ExpressionAction {

    /**
     * Process an expression
     * @param expression the expression to be processed
     * @param result supplied value (of an appropriate type!) which can be updated to return results
     * @return true if processing is now complete and further expressions do not need to be processed
     * @throws XPathException if a failure occurs
     */
    boolean process(Expression expression, Object result) throws XPathException;

}

