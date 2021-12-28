////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.UserFunctionCall;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.om.Function;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.Visibility;
import net.sf.saxon.trans.XPathException;

import java.util.List;


/**
 * A PublicStylesheetFunctionLibrary filters a StylesheetFunctionLibrary to include only those functions
 * whose visibility is final or public. Used by xsl:evaluate
 */

public class PublicStylesheetFunctionLibrary implements FunctionLibrary {



    private FunctionLibrary base;

    /**
     * Create a FunctionLibrary that provides access to public stylesheet functions
     *
     * @param base  the base function library of which this one is a subset
     */
    public PublicStylesheetFunctionLibrary(FunctionLibrary base) {
        this.base = base;
    }

    /**
     * Bind a function, given the URI and local parts of the function name,
     * and the list of expressions supplied as arguments. This method is called at compile
     * time.
     *
     * @param functionName   The name of the function
     * @param staticArgs   The expressions supplied statically in the function call. The intention is
     *                     that the static type of the arguments (obtainable via getItemType() and getCardinality() may
     *                     be used as part of the binding algorithm.
     * @param env          The static context
     * @param reasons      If no matching function is found by the function library, it may add
     *                     a diagnostic explanation to this list explaining why none of the available
     *                     functions could be used.
     * @return An object representing the extension function to be called, if one is found;
     *         null if no extension function was found matching the required name and arity.
     */

    @Override
    public Expression bind(SymbolicName.F functionName, Expression[] staticArgs, StaticContext env, List<String> reasons) {
        Expression baseCall = base.bind(functionName, staticArgs, env, reasons);
        if (baseCall instanceof UserFunctionCall) {
            Component target = ((UserFunctionCall)baseCall).getTarget();
            Visibility v = target.getVisibility();
            if (v == Visibility.PUBLIC || v == Visibility.FINAL) {
                return baseCall;
            } else {
                reasons.add("The function exists, but does not have public visibility");
            }
        }
        return null;

    }

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
     * @throws XPathException
     *          in the event of certain errors, for example attempting to get a function
     *          that is private
     */
    @Override
    public Function getFunctionItem(SymbolicName.F functionName, StaticContext staticContext) throws XPathException {
        Function baseFunction = base.getFunctionItem(functionName, staticContext);
        if (baseFunction instanceof UserFunction) {
            Visibility v = ((UserFunction)baseFunction).getDeclaredVisibility();
            if (v == Visibility.PUBLIC || v == Visibility.FINAL) {
                return baseFunction;
            }
        }
        return null;
    }

    /**
     * Test whether a function with a given name and arity is available
     * <p>This supports the function-available() function in XSLT.</p>
     *
     * @param functionName the qualified name of the function being called
     * @return true if a function of this name and arity is available for calling
     */
    @Override
    public boolean isAvailable(SymbolicName.F functionName) {
        if (base instanceof StylesheetFunctionLibrary) {
            StylesheetPackage pack = ((StylesheetFunctionLibrary)base).getStylesheetPackage();
            UserFunction fn = pack.getFunction(functionName);
            if (fn != null) {
                Visibility v = fn.getDeclaredVisibility();
                return v == Visibility.PUBLIC || v == Visibility.FINAL;
            } else {
                return false;
            }
        } else {
            return base.isAvailable(functionName);
        }
    }

    /**
     * This method creates a copy of a FunctionLibrary: if the original FunctionLibrary allows
     * new functions to be added, then additions to this copy will not affect the original, or
     * vice versa.
     *
     * @return a copy of this function library. This must be an instance of the original class.
     */

    @Override
    public FunctionLibrary copy() {
        return this;
    }

}

