////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.SymbolicName;

/**
 * The interface {@code IFunctionWithRetainedParameterNames} represents a function item that retains
 * information about the names of parameters in an underlying function definition. This is used
 * when parsing a partial function application that associates keywords with placeholders. The parser
 * calls {@link FunctionLibrary#getFunctionItem(SymbolicName.F, StaticContext)} to get the function
 * that is being partially applied, but it also needs information about the names of the parameters,
 * which is not available from every {@link FunctionItem}.
 */

public interface IFunctionWithRetainedParameterNames {

    /**
     * Get the names of the parameters in the underlying function definition
     * @return the names of the parameters, in order
     */
    StructuredQName[] getParameterNames();


}

