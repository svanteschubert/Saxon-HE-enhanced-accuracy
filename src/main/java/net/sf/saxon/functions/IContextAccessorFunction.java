////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.trans.XPathException;

/**
 * A ContextAccessorFunction is a function that is (or may be) dependent on the dynamic context. In the case
 * of dynamic function calls, the context is bound at the point where the function is created,
 * not at the point where the function is called.
 */

public interface IContextAccessorFunction {

    /**
     * Ask whether this function is actually dependent on the dynamic context
     * @return true if the function cannot be used unless the dynamic context is first bound
     */

    boolean dependsOnContext();

    /**
     * Bind context information to appear as part of the function's closure. If this method
     * has been called, the supplied context will be used in preference to the
     * context at the point where the function is actually called.
     * @param context the context to which the function applies. Must not be null.
     */

    FunctionItem bindContext(XPathContext context) throws XPathException;


}

