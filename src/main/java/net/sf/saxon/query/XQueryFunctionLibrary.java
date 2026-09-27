////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.functions.CallableFunction;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.functions.hof.UnresolvedXQueryFunctionItem;
import net.sf.saxon.functions.hof.UserFunctionReference;
import net.sf.saxon.ma.trie.ImmutableHashTrieMap;
import net.sf.saxon.ma.trie.TrieKVP;
import net.sf.saxon.ma.zeno.ZenoChain;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.type.Schema;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.SequenceType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An XQueryFunctionLibrary is a function library containing all the user-defined functions available for use within a
 * particular XQuery module: that is, the functions declared in that module, and the functions imported from other
 * modules. It also contains (transiently during compilation) entries for functions that have been referenced
 * but not yet declared, because the body of a function may contain a reference to another function
 * declared later in the same module.
 */

public class XQueryFunctionLibrary implements FunctionLibrary, XQueryFunctionBinder {

    private Configuration config;

    // At the top level we have an index by namespace. For each namespace there is an index
    // by local name. There may be more than one function with a given local name (but different
    // arity ranges); these are held simply as a list, with a sequential search to find the
    // required arity. The index by local name is held as an immutable HashMap which allows
    // sharing in the common case where all the functions with a given namespace are in the
    // same library module. For each namespace / local-name() there is an immutable list of
    // functions, these will have different arity ranges.

    private java.util.HashMap<NamespaceUri, ImmutableHashTrieMap<String, ZenoChain<XQueryFunction>>>
            functionsByNamespace = new java.util.HashMap<>(20);

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
        StructuredQName functionName = function.getFunctionName();
        ImmutableHashTrieMap<String, ZenoChain<XQueryFunction>> localMap =
                functionsByNamespace.get(functionName.getNamespaceUri());
        if (localMap == null) {
            ZenoChain<XQueryFunction> list = makeEmptyList();
            list = list.prepend(function);
            localMap = makeEmptyMap();
            localMap = localMap.put(functionName.getLocalPart(),list);
        } else {
            // Test if the arity range of this function overlaps the arity range of another function
            ZenoChain<XQueryFunction> existingFunctions =
                    localMap.get(functionName.getLocalPart());
            if (existingFunctions == null) {
                existingFunctions = makeEmptyList();
            }
            for (XQueryFunction existing : existingFunctions) {
                if (existing == function) {
                    return;
                }
                if (hasOverlappingArity(function, existing)) {
                    throw new XPathException("Conflicting definition of function " +
                                                     function.getDisplayName() +
                                                     " (see line " + existing.getLineNumber() + " in " + existing.getSystemId() + ')')
                            .withErrorCode("XQST0034").asStaticError().withLocation(function);
                }
            }
            ZenoChain<XQueryFunction> newList = existingFunctions.prepend(function);
            localMap = localMap.put(functionName.getLocalPart(), newList);
        }
        functionsByNamespace.put(functionName.getNamespaceUri(), localMap);
    }

    //@CSharpReplaceBody(code="return System.Collections.Immutable.ImmutableList<Saxon.Hej.query.XQueryFunction>.Empty;")
    private static ZenoChain<XQueryFunction> makeEmptyList() {
        return new ZenoChain<XQueryFunction>();
    }

    @CSharpReplaceBody(code="return System.Collections.Immutable.ImmutableDictionary<string,Saxon.Hej.ma.zeno.ZenoChain<Saxon.Hej.query.XQueryFunction>>.Empty;")
    private static ImmutableHashTrieMap<String, ZenoChain<XQueryFunction>> makeEmptyMap() {
        return ImmutableHashTrieMap.empty();
    }

    private static boolean hasOverlappingArity(XQueryFunction f1, XQueryFunction f2) {
        // From https://stackoverflow.com/questions/3269434,
        // [x1:x2] overlaps [y1:y2] === x1 <= y2 && y1 <= x2
        return f1.getMinimumArity() <= f2.getNumberOfParameters()
                && f2.getMinimumArity() <= f1.getNumberOfParameters();
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
    public FunctionItem getFunctionItem(SymbolicName.F functionName, StaticContext staticContext)
            throws XPathException {
        XQueryFunction fd = getDeclaration(functionName.getComponentName(), functionName.getArity());
        if (fd != null) {
            if (fd.isPrivate() && !fd.getSystemId().equals(staticContext.getStaticBaseURI())) {
                throw new XPathException("Cannot call the private function " +
                        functionName.getComponentName().getDisplayName() + " from outside its module", "XPST0017");
            }
            final UserFunction fn = fd.getUserFunction();
            if (fn == null) {
                // not yet compiled: create a dummy
                UserFunction uf = new UserFunction();
                uf.setFunctionName(functionName.getComponentName());
                uf.setResultType(fd.getResultType());
                uf.setParameterDefinitions(fd.getParameterDefinitions());
                final UserFunctionReference ref = new UserFunctionReference(uf, functionName);
                fd.registerReference(ref);
                return new UnresolvedXQueryFunctionItem(fd, functionName, ref);

            } else if (functionName.getArity() == fd.getNumberOfParameters()) {
                // all arguments supplied
                return fn;
            } else {
                // return a reference to a reduced-arity version in which some of the arguments are defaulted
                Callable callable = new ReducedArityCallable(fd, fn);

                SequenceType[] argTypes = new SequenceType[functionName.getArity()];
                for (int i=0; i< functionName.getArity(); i++) {
                    argTypes[i] = fd.getArgumentTypes()[i];
                }
                SpecificFunctionType functionType = new SpecificFunctionType(argTypes, fd.getResultType());
                return new CallableFunction(functionName, callable::call, functionType);
            }
        } else {
            return null;
        }
    }

    /**
     * Test whether a function with a given name and arity is available
     * <p>This supports the function-available() function in XSLT.</p>
     *
     * @param functionName  the qualified name of the function being called
     * @param schema the schema in question (constructor functions are available
     *               in some schemas and not others)
     * @param languageLevel the XPath language level times 10 (31 = XPath 3.1)
     * @return true if a function of this name and arity is available for calling
     */
    @Override

    public boolean isAvailable(SymbolicName.F functionName, Schema schema, int languageLevel) {
        return getDeclarationByKey(functionName) != null;
    }

    public Iterable<NamespaceUri> getNamespaces() {
        return functionsByNamespace.keySet();
    }

    public ImmutableHashTrieMap<String, ZenoChain<XQueryFunction>> getFunctionsForNamespace(NamespaceUri ns) {
        return functionsByNamespace.get(ns);
    }

    public void addFunctions(NamespaceUri ns,
                             ImmutableHashTrieMap<String, ZenoChain<XQueryFunction>> functions)
    throws XPathException {
        ImmutableHashTrieMap<String, ZenoChain<XQueryFunction>> localMap = functionsByNamespace.get(ns);
        if (localMap == null) {
            functionsByNamespace.put(ns, functions);
        } else {
            for (TrieKVP<String, ZenoChain<XQueryFunction>> kvp : localMap) {
                for (XQueryFunction fn : kvp.value) {
                    declareFunction(fn);
                }
            }
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
    public Expression bind(SymbolicName.F functionName, Expression[] arguments,
                           Map<StructuredQName, Integer> keywords, StaticContext env, List<String> reasons)
            throws XPathException {
        XQueryFunction fd = getDeclaration(functionName.getComponentName(), arguments.length);
        if (fd != null) {
            if (fd.isPrivate() && fd.getStaticContext() != env) {
                reasons.add("Cannot call the private XQuery function " +
                        functionName.getComponentName().getDisplayName() + " from outside its module");
                return null;
            }
            UserFunctionCall ufc = new UserFunctionCall();
            ufc.setFunctionName(fd.getFunctionName());
            int maxArity = fd.getNumberOfParameters();
            if (arguments.length == maxArity && (keywords == null || keywords.isEmpty())) {
                ufc.setArguments(arguments);
            } else {
                Expression[] expandedArgs = UserFunction.makeExpandedArgumentArray(arguments, keywords, fd);
                ufc.setArguments(expandedArgs);
                for (Expression e : expandedArgs) {
                    ufc.adoptChildExpression(e);
                }
            }
            ufc.setStaticType(fd.getResultType());


            // Inject any default value expressions
            for (int i = 0; i < ufc.getArguments().length; i++) {
                if (ufc.getArg(i) instanceof DefaultedArgumentExpression) {
                    Supplier<Expression> def = fd.getParameterDefinitions()[i].getDefaultValueExpression();
                    if (def == null) {
                        throw new XPathException("Argument " + (i + 1) + " has no default value in function "
                                                         + functionName.getComponentName().getEQName(), "XPST0017");
                    }
                    ufc.setArg(i, def.get().copy(new RebindingMap()));

                }
                ufc.adoptChildExpression(ufc.getArg(i));
            }


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
        ImmutableHashTrieMap<String, ZenoChain<XQueryFunction>> localMap =
                functionsByNamespace.get(functionName.getNamespaceUri());
        if (localMap == null) {
            return null;
        }
        ZenoChain<XQueryFunction> homonyms = localMap.get(functionName.getLocalPart());
        if (homonyms != null) {
            for (XQueryFunction f : homonyms) {
                if (f.getMinimumArity() <= staticArgs && f.getNumberOfParameters() >= staticArgs) {
                    return f;
                }
            }
        }
        return null;
    }
    
    /**
     * Get the function declaration corresponding to a given function name and arity, supplied
     * in the form "{uri}local/arity"
     *
     * @param functionKey a string in the form "{uri}local/arity" identifying the required function
     * @return the XQueryFunction if there is one, or null if not.
     */

    public XQueryFunction getDeclarationByKey(SymbolicName.F functionKey) {
        return getDeclaration(functionKey.getComponentName(), functionKey.getArity());
    }

    /**
     * Process all the functions defined in this module
     * @param action an action to be performed on every function defined in this module
     * including those imported from elsewhere.
     * @throws XPathException if any of the defined actions fails with an
     * {@link UncheckedXPathException}
     */

    public void processAllFunctions(Consumer<XQueryFunction> action) throws XPathException {
        for (ImmutableHashTrieMap<String, ZenoChain<XQueryFunction>> localMap : functionsByNamespace.values()) {
            for (TrieKVP<String, ZenoChain<XQueryFunction>> kvp : localMap) {
                for (XQueryFunction fn : kvp.value) {
                    try {
                        action.accept(fn);
                    } catch (UncheckedXPathException e) {
                        throw e.getXPathException();
                    }
                }
            }
        }
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
        processAllFunctions(fn -> {
            try {
                fn.compile();
                Expression body = fn.getBody();
                Expression e2 = body.simplify();
                if (e2 != body) {
                    ExpressionTool.copyLocationInfo(body, e2);
                    fn.setBody(e2);
                }
                for (int i=0; i<fn.getNumberOfParameters(); i++) {
                    Supplier<Expression> init = fn.getDefaultValueExpression(i);
                    if (init != null) {
                        Expression initExp = init.get();
                        e2 = initExp.simplify();
                        if (e2 != initExp) {
                            fn.setDefaultValueExpression(i, e2);
                        }
                    }
                }
            } catch (XPathException err) {
                throw new UncheckedXPathException(err);
            }
        });
        processAllFunctions(fn -> {
            try {
                fn.checkReferences(visitor);
            } catch (XPathException err) {
                throw new UncheckedXPathException(err);
            }
        });
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
        processAllFunctions(fn -> {
            try {
                if (((QueryModule) fn.getStaticContext()).getTopLevelModule() == topModule) {
                    fn.optimize();
                }
            } catch (XPathException err) {
                throw new UncheckedXPathException(err);
            }
        });
    }


    /**
     * Output "explain" information about each declared function
     *
     * @param out the ExpressionPresenter that renders the output
     * @throws XPathException if things go wrong
     */

    public void explainGlobalFunctions(/*@NotNull*/ ExpressionPresenter out) throws XPathException {
        processAllFunctions(fn -> {
            try {
                fn.explain(out);
            } catch (XPathException err) {
                throw new UncheckedXPathException(err);
            }
        });
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
    public UserFunction getUserDefinedFunction(/*@NotNull*/ NamespaceUri uri, /*@NotNull*/ String localName, int arity) {
        SymbolicName.F functionKey = new SymbolicName.F(new StructuredQName("", uri, localName), arity);
        XQueryFunction fd = getDeclarationByKey(functionKey);
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
        qfl.functionsByNamespace = new java.util.HashMap<>(functionsByNamespace);
        return qfl;
    }

    private static class ReducedArityCallable implements Callable {

        private final XQueryFunction declaredFunction;
        private final UserFunction userFunction;

        public ReducedArityCallable(XQueryFunction fd, UserFunction fn) {
            this.declaredFunction = fd;
            this.userFunction = fn;
        }

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            Sequence[] extendedArguments = Arrays.copyOf(arguments, userFunction.getArity());
            for (int i = arguments.length; i < userFunction.getArity(); i++) {
                // Evaluate the default value expression for the omitted argument
                Supplier<Expression> expr = declaredFunction.getParameterDefinitions()[i].getDefaultValueExpression();
                extendedArguments[i] = expr.get().makeElaborator().eagerly().evaluate(context);
            }
            XPathContextMajor c2 = userFunction.makeNewContext(context, userFunction);
            return userFunction.call(c2, extendedArguments);
        }

    }


}
