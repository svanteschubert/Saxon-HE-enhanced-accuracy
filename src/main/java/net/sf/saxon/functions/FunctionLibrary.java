////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;

import java.util.List;
import java.util.Map;

/**
 * A FunctionLibrary handles the binding of function calls in XPath (or XQuery) expressions.
 * There are a number of implementations of this
 * class to handle different kinds of function: system functions, constructor functions, vendor-defined
 * functions, Java extension functions, stylesheet functions, and so on. There is also an implementation
 * {@link net.sf.saxon.functions.FunctionLibraryList} that allows a FunctionLibrary
 * to be constructed by combining other FunctionLibrary objects.
 */

public interface FunctionLibrary {

    /**
     * Supply details of the configuration in use.
     * @param config the configuration. The default implementation does nothing (many function libraries
     *               do not need to know any configuration details)
     */

    default void setConfiguration(Configuration config) {}

    /**
     * Test whether a function with a given name and arity is available
     * <p>This supports the function-available() function in XSLT.</p>
     *
     * @param functionName the qualified name of the function being called, together with its arity.
     *                     For legacy reasons, the arity may be set to -1 to mean any arity will do
     * @param languageLevel the XPath language level (times 10, e.g. 31 for XPath 3.1)
     * @return true if a function of this name and arity is available for calling
     */

    /*@Nullable*/
    boolean isAvailable(SymbolicName.F functionName, int languageLevel);


    /**
     * Bind a function, given the URI and local parts of the function name,
     * and the list of expressions supplied as arguments. This method is called at compile
     * time.
     *
     * @param functionName the QName of the function being called
     * @param staticArgs   Contains the expressions supplied statically in arguments to the function call.
     *                     The intention is
     *                     that the static type of the arguments (obtainable via getItemType() and getCardinality()) may
     *                     be used as part of the binding algorithm. In some cases it may be possible for the function
     *                     to be pre-evaluated at compile time, for example if these expressions are all constant values.
     *                     <p>The conventions of the XPath language demand that the results of a function depend only on the
     *                     values of the expressions supplied as arguments, and not on the form of those expressions. For
     *                     example, the result of f(4) is expected to be the same as f(2+2). The actual expression is supplied
     *                     here to enable the binding mechanism to select the most efficient possible implementation (including
     *                     compile-time pre-evaluation where appropriate).</p>
     * @param keywords     May be null if no keywords are used in the function call. Otherwise, a map identifying the
     *                     keywords appearing in the function call, and the 0-based position at which they appeared.
     * @param env          The static context of the function call
     * @param reasons      If no matching function is found by the function library, it may add
     *                     a diagnostic explanation to this list explaining why none of the available
     *                     functions could be used.
     * @return An expression equivalent to a call on the specified function, if one is found;
     *         null if no function was found matching the required name and arity.
     * @throws XPathException if a matching function was identified, but the call is incorrect,
     * for example because it uses incorrect argument keywords
     */

    /*@Nullable*/
    Expression bind(SymbolicName.F functionName, Expression[] staticArgs,
                    Map<StructuredQName, Integer> keywords, StaticContext env, List<String> reasons) throws XPathException;

    /**
     * This method creates a copy of a FunctionLibrary: if the original FunctionLibrary allows
     * new functions to be added, then additions to this copy will not affect the original, or
     * vice versa.
     *
     * @return a copy of this function library. This must be an instance of the original class.
     */

    FunctionLibrary copy();

    /**
     * Test whether a function with a given name and arity is available; if so, return a function
     * item that can be dynamically called.
     * <p>This supports the function-lookup() function in XPath 3.0.</p>
     *
     * @param functionName  the qualified name of the function being called
     * @param staticContext the static context to be used by the function, in the event that
     *                      it is a system function with dependencies on the static context
     * @return if a function of this name and arity is available for calling, then a corresponding
     *         function item; or null if the function does not exist
     * @throws XPathException in the event of certain errors, for example attempting to get a function
     *                        that is private
     */

    /*@Nullable*/
    FunctionItem getFunctionItem(SymbolicName.F functionName, StaticContext staticContext)
            throws XPathException;



}
