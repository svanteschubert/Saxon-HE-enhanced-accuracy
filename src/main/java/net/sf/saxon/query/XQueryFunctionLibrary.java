////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.functions.hof.UnresolvedXQueryFunctionItem;
import net.sf.saxon.functions.hof.UserFunctionReference;
import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.SpecificFunctionType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * An XQueryFunctionLibrary is a function library containing all the user-defined functions available for use within a
 * particular XQuery module: that is, the functions declared in that module, and the functions imported from other
 * modules. It also contains (transiently during compilation) a list of function calls within the module that have not
 * yet been bound to a specific function declaration.
 */

public class XQueryFunctionLibrary implements FunctionLibrary, XQueryFunctionBinder {

    private Configuration config;

    // The functions in this library are represented using a HashMap
    // The key of the hashmap is an object that encodes the QName of the function and its arity
    // The value in the hashmap is an XQueryFunction
    /*@NotNull*/ private HashMap<SymbolicName, XQueryFunction> functions =
            new HashMap<>(20);

    /**
     * Create an XQueryFunctionLibrary
     *
     * @param config the Saxon configuration
     */

    public XQueryFunctionLibrary(Configuration config) {
        this.config = config;
    }

    /**
     * Set the Configuration options
     *
     * @param config the Saxon configuration
     */

    @Override
    public void setConfiguration(Configuration config) {
        this.config = config;
    }

    /**
     * Get the Configuration options
     *
     * @return the Saxon configuration
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Register a user-defined XQuery function
     *
     * @param function the function to be registered
     * @throws XPathException if there is an existing function with the same name and arity
     */

    public void declareFunction(/*@NotNull*/ XQueryFunction function) throws XPathException {
        SymbolicName keyObj = function.getIdentificationKey();
        XQueryFunction existing = functions.get(keyObj);
        if (existing == function) {
            return;
        }
        if (existing != null) {
            XPathException err = new XPathException("Duplicate definition of function " +
                    function.getDisplayName() +
                    " (see line " + existing.getLineNumber() + " in " + existing.getSystemId() + ')');
            err.setErrorCode("XQST0034");
            err.setIsStaticError(true);
            err.setLocator(function);
            throw err;
        }
        functions.put(keyObj, function);
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
    public Function getFunctionItem(SymbolicName.F functionName, StaticContext staticContext)
            throws XPathException {
        XQueryFunction fd = functions.get(functionName);
        if (fd != null) {
            if (fd.isPrivate() && !fd.getSystemId().equals(staticContext.getStaticBaseURI())) {
                throw new XPathException("Cannot call the private function " +
                        functionName.getComponentName().getDisplayName() + " from outside its module", "XPST0017");
            }
            final UserFunction fn = fd.getUserFunction();
            FunctionItemType type = new SpecificFunctionType(
                    fd.getArgumentTypes(), fd.getResultType(), fd.getAnnotations());
            if (fn == null) {
                // not yet compiled: create a dummy
                UserFunction uf = new UserFunction();
                uf.setFunctionName(functionName.getComponentName());
                uf.setResultType(fd.getResultType());
                uf.setParameterDefinitions(fd.getParameterDefinitions());
                final UserFunctionReference ref = new UserFunctionReference(uf);
                fd.registerReference(ref);
                return new UnresolvedXQueryFunctionItem(fd, functionName, ref);

            } else {
                return fn;
            }
        } else {
            return null;
        }
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
        return functions.get(functionName) != null;
    }

    /**
     * Inner class containing information about a reference to a function whose declaration
     * has not yet been encountered. The references gets fixed up later, once information
     * about all user-declared functions is available.
     */

    public static class UnresolvedCallable implements UserFunctionResolvable, Callable {
        SymbolicName.F symbolicName;
        UserFunction function;

        public UnresolvedCallable(SymbolicName.F symbolicName) {
            this.symbolicName = symbolicName;
        }

        public StructuredQName getFunctionName() {
            return symbolicName.getComponentName();
        }

        public int getArity() {
            return symbolicName.getArity();
        }

        //public void setFunctionItem(CallableFunctionItem fi) {
        //    this.functionItem = fi;
        //}

        /**
         * Evaluate the expression
         *
         * @param context   the dynamic evaluation context
         * @param arguments the values of the arguments, supplied as Sequences
         * @return the result of the evaluation, in the form of a Sequence
         * @throws net.sf.saxon.trans.XPathException
         *          if a dynamic error occurs during the evaluation of the expression
         */
        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            if (function == null) {
                throw new XPathException("Forwards reference to XQuery function has not been resolved");
            }
            Sequence[] args = new Sequence[arguments.length];
            for (int i = 0; i < arguments.length; i++) {    // TODO: is this copying necessary?
                args[i] = arguments[i].materialize();
            }
            return function.call(context.newCleanContext(), args);
        }

        @Override
        public void setFunction(UserFunction function) {
            this.function = function;
        }

        public UserFunction getFunction() {
            return function;
        }
    }

    /**
     * Identify a (namespace-prefixed) function appearing in the expression. This
     * method is called by the XQuery parser to resolve function calls found within
     * the query.
     * <p>Note that a function call may appear earlier in the query than the definition
     * of the function to which it is bound. Unlike XSLT, we cannot search forwards to
     * find the function definition. Binding of function calls is therefore a two-stage
     * process; at the time the function call is parsed, we simply register it as
     * pending; subsequently at the end of query parsing all the pending function
     * calls are resolved. Another consequence of this is that we cannot tell at the time
     * a function call is parsed whether it is a call to an internal (XSLT or XQuery)
     * function or to an extension function written in Java.
     *
     * @return an Expression representing the function call. This will normally be
     *         a FunctionCall, but it may be rewritten as some other expression.
     */

    /*@Nullable*/
    @Override
    public Expression bind(/*@NotNull*/ SymbolicName.F functionName, /*@NotNull*/  Expression[] arguments, StaticContext env, List<String> reasons) {
        XQueryFunction fd = functions.get(functionName);
        if (fd != null) {
            if (fd.isPrivate() && fd.getStaticContext() != env) {
                reasons.add("Cannot call the private XQuery function " +
                        functionName.getComponentName().getDisplayName() + " from outside its module");
                return null;
            }
            UserFunctionCall ufc = new UserFunctionCall();
            ufc.setFunctionName(fd.getFunctionName());
            ufc.setArguments(arguments);
            ufc.setStaticType(fd.getResultType());
            UserFunction fn = fd.getUserFunction();
            if (fn == null) {
                // not yet compiled
                fd.registerReference(ufc);
            } else {
                ufc.setFunction(fn);
            }
            return ufc;
        } else {
            return null;
        }
    }

    /**
     * Get the function declaration corresponding to a given function name and arity
     *
     * @return the XQueryFunction if there is one, or null if not.
     */

    @Override
    public XQueryFunction getDeclaration(StructuredQName functionName, int staticArgs) {
        SymbolicName functionKey = XQueryFunction.getIdentificationKey(
                functionName, staticArgs);
        return functions.get(functionKey);
    }

    /**
     * Get the function declaration corresponding to a given function name and arity, supplied
     * in the form "{uri}local/arity"
     *
     * @param functionKey a string in the form "{uri}local/arity" identifying the required function
     * @return the XQueryFunction if there is one, or null if not.
     */

    public XQueryFunction getDeclarationByKey(SymbolicName functionKey) {
        return functions.get(functionKey);
    }

    /**
     * Get an iterator over the Functions defined in this module
     *
     * @return an Iterator, whose items are {@link XQueryFunction} objects. It returns
     *         all function known to this module including those imported from elsewhere; they
     *         can be distinguished by their namespace.
     */

    public Iterator<XQueryFunction> getFunctionDefinitions() {
        return functions.values().iterator();
    }

    /**
     * Fixup all references to global functions. This method is called
     * on completion of query parsing. Each XQueryFunction is required to
     * bind all references to that function to the object representing the run-time
     * executable code of the function.
     * <p>This method is for internal use.</p>
     *
     * @param env the static context for the main query body.
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs
     */

    protected void fixupGlobalFunctions(/*@NotNull*/ QueryModule env) throws XPathException {
        ExpressionVisitor visitor = ExpressionVisitor.make(env);
        for (XQueryFunction fn : functions.values()) {
            fn.compile();
        }
        for (XQueryFunction fn : functions.values()) {
            fn.checkReferences(visitor);
        }
    }

    /**
     * Optimize the body of all global functions. This may involve inlining functions calls
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs
     * @param topModule the top level module of the compilation unit whose functions are
     *                  to be optimized; functions in separately-compiled imported libraries
     *                  are unaffected.
     */

    protected void optimizeGlobalFunctions(QueryModule topModule) throws XPathException {
        for (XQueryFunction fn : functions.values()) {
            if (((QueryModule)fn.getStaticContext()).getTopLevelModule() == topModule) {
                fn.optimize();
            }
        }
    }


    /**
     * Output "explain" information about each declared function
     *
     * @param out the ExpressionPresenter that renders the output
     */

    public void explainGlobalFunctions(/*@NotNull*/ ExpressionPresenter out) throws XPathException {
        for (XQueryFunction fn : functions.values()) {
            fn.explain(out);
        }
    }

    /**
     * Get the function with a given name and arity. This method is provided so that XQuery functions
     * can be called directly from a Java application. Note that there is no type checking or conversion
     * of arguments when this is done: the arguments must be provided in exactly the form that the function
     * signature declares them.
     *
     * @param uri       the uri of the function name
     * @param localName the local part of the function name
     * @param arity     the number of arguments.
     * @return the function identified by the URI, local name, and arity; or null if there is no such function
     */

    /*@Nullable*/
    public UserFunction getUserDefinedFunction(/*@NotNull*/ String uri, /*@NotNull*/ String localName, int arity) {
        SymbolicName functionKey = new SymbolicName.F(new StructuredQName("", uri, localName), arity);
        XQueryFunction fd = functions.get(functionKey);
        if (fd == null) {
            return null;
        }
        return fd.getUserFunction();
    }

    /**
     * This method creates a copy of a FunctionLibrary: if the original FunctionLibrary allows
     * new functions to be added, then additions to this copy will not affect the original, or
     * vice versa.
     *
     * @return a copy of this function library. This must be an instance of the original class.
     */

    /*@NotNull*/
    @Override
    public FunctionLibrary copy() {
        XQueryFunctionLibrary qfl = new XQueryFunctionLibrary(config);
        qfl.functions = new HashMap<>(functions);
        return qfl;
    }


}
