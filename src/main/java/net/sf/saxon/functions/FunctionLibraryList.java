////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.query.XQueryFunction;
import net.sf.saxon.query.XQueryFunctionBinder;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;

import java.util.ArrayList;
import java.util.List;

/**
 * A FunctionLibraryList is a list of FunctionLibraries. It is also a FunctionLibrary in its own right.
 * When required, it searches the list of FunctionLibraries to find the required function.
 */
public class FunctionLibraryList implements FunctionLibrary, XQueryFunctionBinder {

    public List<FunctionLibrary> libraryList = new ArrayList<>(8);

    public FunctionLibraryList() {
    }

    /**
     * Add a new FunctionLibrary to the list of FunctionLibraries in this FunctionLibraryList. Note
     * that libraries are searched in the order they are added to the list.
     *
     * @param lib A function library to be added to the list of function libraries to be searched.
     * @return the position of the library in the list
     */

    public int addFunctionLibrary(FunctionLibrary lib) {
        libraryList.add(lib);
        return libraryList.size() - 1;
    }

    /**
     * Get the n'th function library in the list
     */

    public FunctionLibrary get(int n) {
        return libraryList.get(n);
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
     */
    @Override
    public Function getFunctionItem(SymbolicName.F functionName, StaticContext staticContext) throws XPathException {
        for (FunctionLibrary lib : libraryList) {
            Function fi = lib.getFunctionItem(functionName, staticContext);
            if (fi != null) {
                return fi;
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
        for (FunctionLibrary lib : libraryList) {
            if (lib.isAvailable(functionName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Bind an extension function, given the URI and local parts of the function name,
     * and the list of expressions supplied as arguments. This method is called at compile
     * time.
     *
     * @param functionName  The name of the function
     * @param staticArgs   The expressions supplied statically in arguments to the function call.
     *                     The length of this array represents the arity of the function. The intention is
     *                     that the static type of the arguments (obtainable via getItemType() and getCardinality() may
     *                     be used as part of the binding algorithm. In some cases it may be possible for the function
     *                     to be pre-evaluated at compile time, for example if these expressions are all constant values.
     * @param env          The static context
     * @param reasons      If no matching function is found by the function library, it may add
     *                     a diagnostic explanation to this list explaining why none of the available
     *                     functions could be used.
     * @return An object representing the extension function to be called, if one is found;
     *         null if no extension function was found matching the required name and arity.
     */

    @Override
    public Expression bind(SymbolicName.F functionName, Expression[] staticArgs, StaticContext env, List<String> reasons) {
        boolean debug = env.getConfiguration().getBooleanProperty(Feature.TRACE_EXTERNAL_FUNCTIONS);
        Logger err = env.getConfiguration().getLogger();
        if (debug) {
            err.info("Looking for function " + functionName.getComponentName().getEQName() + "#" + functionName.getArity());
        }
        for (FunctionLibrary lib : libraryList) {
            if (debug) {
                err.info("Trying " + lib.getClass().getName());
            }
            Expression func = lib.bind(functionName, staticArgs, env, reasons);
            if (func != null) {
                return func;
            }
        }
        if (debug) {
            err.info("Function " + functionName.getComponentName().getEQName() + " not found!");
        }
        return null;
    }

    /**
     * Get the function declaration corresponding to a given function name and arity
     *
     * @return the XQueryFunction if there is one, or null if not.
     */

    @Override
    public XQueryFunction getDeclaration(StructuredQName functionName, int staticArgs) {
        for (FunctionLibrary lib : libraryList) {
            if (lib instanceof XQueryFunctionBinder) {
                XQueryFunction func = ((XQueryFunctionBinder) lib).getDeclaration(functionName, staticArgs);
                if (func != null) {
                    return func;
                }
            }
        }
        return null;
    }

    /**
     * Get the list of contained FunctionLibraries. This method allows the caller to modify
     * the library list, for example by adding a new FunctionLibrary at a chosen position,
     * by removing a library from the list, or by changing the order of libraries in the list.
     * Note that such changes may violate rules in the
     * language specifications, or assumptions made within the product.
     *
     * @return a list whose members are of class FunctionLibrary
     */

    public List<FunctionLibrary> getLibraryList() {
        return libraryList;
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
        FunctionLibraryList fll = new FunctionLibraryList();
        fll.libraryList = new ArrayList<>(libraryList.size());
        for (int i = 0; i < libraryList.size(); i++) {
            fll.libraryList.add(libraryList.get(i).copy());
        }
        return fll;
    }
}
