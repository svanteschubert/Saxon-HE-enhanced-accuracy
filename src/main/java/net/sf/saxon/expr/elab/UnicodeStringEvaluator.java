////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.elab;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;

/**
 * A StringEvaluator is a function that typically encapsulates the logic for
 * evaluating an expression that returns a string, in the form of a {@link UnicodeString}
 */
@FunctionalInterface
public interface UnicodeStringEvaluator {

    /**
     * Evaluate the encapsulated expression
     *
     * @param context the evaluation context
     * @return the result, as a {@link UnicodeString}; or null to represent an empty sequence
     * @throws XPathException if a dynamic error occurs
     */

    UnicodeString eval(XPathContext context) throws XPathException;
}

