////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.registry;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.om.StructuredQName;

/**
 * A {@code FunctionDefinition} represents the declaration of a user-defined function in XQuery or XSLT; in 4.0
 * a function definition has an arity range. The minimum arity is the number of mandatory parameters,
 * the maximum arity is the number of optional plus mandatory parameters; optional parameters have a default
 * value which is supplied as an expression.
 */
public interface FunctionDefinition {

    /**
     * Get the name of the function
     * @return the function name
     */
    StructuredQName getFunctionName();

    /**
     * Get the number of declared parameters (the upper bound of the arity range)
     * @return the number of declared parameters
     */

    int getNumberOfParameters();

    /**
     * Get the number of mandatory arguments (the lower bound of the arity range)
     * @return the number of mandatory arguments
     */

    int getMinimumArity();

    /**
     * Get the name (keyword) of the Nth parameter
     *
     * @param i the position of the required parameter
     * @return the expression for computing the value of the Nth parameter
     */
    StructuredQName getParameterName(int i);

    /**
     * Get the default value expression of the Nth parameter, if any
     *
     * @param i the position of the required parameter
     * @return the expression for computing the value of the Nth parameter, or null if there is none
     */

    Expression getDefaultValueExpression(int i);

    /**
     * Get the position in the parameter list of a given parameter name
     *
     * @param name the name of the required parameter
     * @return the position of the parameter in the parameter list, or -1 if absent
     */

    int getPositionOfParameter(StructuredQName name);

}

