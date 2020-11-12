////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.om.StructuredQName;

/**
 * XQueryFunctionBinder is an extension of the FunctionLibrary interface used for function libraries
 * that contain user-written XQuery functions. It provides a method that allows the XQueryFunction
 * with a given name and arity to be located.
 */

public interface XQueryFunctionBinder extends FunctionLibrary {

    /**
     * Get the function declaration corresponding to a given function name and arity
     *
     * @param functionName the name of the function as a QName
     * @param staticArgs   the number of expressions supplied as arguments in the function call
     * @return the XQueryFunction if there is one, or null if not.
     */

    /*@Nullable*/
    XQueryFunction getDeclaration(StructuredQName functionName, int staticArgs);

}
