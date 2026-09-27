////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.registry;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.functions.Concat31;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.functions.OptionsParameter;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.ma.map.RecordType;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.PlainType;
import net.sf.saxon.type.Schema;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.z.IntHashMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * This class is used to contain information about a set of built-in functions.
 */
public abstract class BuiltInFunctionSet implements FunctionLibrary {

    public static Sequence EMPTY = EmptySequence.INSTANCE;

    /**
     * Local names used for cardinality values
     */

    public static final int ONE = StaticProperty.ALLOWS_ONE;
    public static final int OPT = StaticProperty.ALLOWS_ZERO_OR_ONE;
    public static final int STAR = StaticProperty.ALLOWS_ZERO_OR_MORE;
    public static final int PLUS = StaticProperty.ALLOWS_ONE_OR_MORE;

    /**
     * Function properties
     */

    public static final int AS_ARG0 = 1;          // Result has same type as first argument
    public static final int AS_NUM_ARG0 = 2;      // Result has same numeric primitive type as first argument
    public static final int CITEM = 4;            // Depends on context item
    public static final int BASE = 8;             // Depends on base URI
    public static final int NS = 16;              // Depends on namespace context
    public static final int DCOLL = 32;           // Depends on default collation
    public static final int DLANG = 64;           // Depends on default language
    public static final int FILTER = 256;         // Result is a subset of the value of the first arg
    public static final int LATE = 512;           // Disallow compile-time evaluation
    public static final int UO = 1024;            // Ordering in first argument is irrelevant
    public static final int POSN = 1024*2;        // Depends on position
    public static final int LAST = 1024*4;        // Depends on last
    public static final int SIDE = 1024*8;        // Has side-effects
    public static final int CDOC = 1024*16;       // Depends on context document
    public static final int CARD0 = 1024*32;      // Result is empty only if first arg is empty
    public static final int NEW = 1024*64;        // All nodes in the result are newly created
    public static final int CTRL = 1024*128;      // Controls the optimization of its arguments
    public static final int SEQV = 1024 * 256;    // Sequence-variadic, like concat() in 4.0

    public static final int DEPENDS_ON_STATIC_CONTEXT = BASE | NS | DCOLL;
    public static final int FOCUS = CITEM | POSN | LAST | CDOC;


    /**
     * Classification of function arguments for serialization purposes; note that values must not conflict
     * with bit settings used for cardinalities
     */

    protected static final int INS = 1 << 24;   // = usage INSPECTION
    protected static final int ABS = 1 << 25;   // = usage ABSORPTION (implicit when type is atomic)
    protected static final int TRA = 1 << 26;   // = usage TRANSMISSION (node is included in function result)
    protected static final int NAV = 1 << 27;   // = usage NAVIGATION (function navigates from this node)

    /**
     * Convenience method for defining fields of a record type
     * @param name the field name
     * @param type the field type
     * @param optional true if the field is optional
     * @return the field definition
     */

    protected static RecordType.Field field(String name, SequenceType type, boolean optional) {
        return new RecordType.Field(name, type, optional);
    }

    private final HashMap<String, net.sf.saxon.functions.registry.BuiltInFunctionSet.Entry> functionTable = new HashMap<>(200);
    //private List<BuiltInFunctionSet> importedFunctions = new ArrayList<BuiltInFunctionSet>();

    // Sequence-variadic functions (XPath 4.0+) are registered in a table indexed by the local
    // name of the function; the corresponding value is the minimum arity.
    private final HashMap<String, Integer> sequenceVariadicFunctions = new HashMap<>(10);

    /**
     * Import another function set (which must be in the same namespace)
     * @param importee the function set to be imported. (No cycles allowed!)
     */

    public final void importFunctionSet(net.sf.saxon.functions.registry.BuiltInFunctionSet importee) {
        if (!importee.getNamespace().equals(getNamespace())) {
            throw new IllegalArgumentException(importee.getNamespace().toString());
        }
        functionTable.putAll(importee.functionTable);
        sequenceVariadicFunctions.putAll(importee.sequenceVariadicFunctions);
        //importedFunctions.add(importee);
    }

    /**
     * Locate the entry for a function with a given name and arity, if it exists
     * @param name the local part of the function name
     * @param arity the arity of the function. -1 considers all possible arities and returns an arbitrary function
     *              if one exists with the right name.
     * @return the entry for the required function, or null if not found
     */

    public net.sf.saxon.functions.registry.BuiltInFunctionSet.Entry getFunctionDetails(String name, int arity) {
        if (arity == -1) {
            for (int i=0; i<20; i++) {
                net.sf.saxon.functions.registry.BuiltInFunctionSet.Entry found = getFunctionDetails(name, i);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        String key = name + "#" + arity;
        net.sf.saxon.functions.registry.BuiltInFunctionSet.Entry entry = functionTable.get(key);
        if (entry != null) {
            return entry;
        }
        // Try for a generalised (XP40) sequence-variadic function
        int minArity = sequenceVariadicFunctions.getOrDefault(name, -1);
        if (minArity != -1 && arity >= minArity) {
            key = name + "#" + (minArity + 1);
            return functionTable.get(key);
        }
//        // Try for a variable-arity function (concat only up to 3.1)
//        if (name.equals("concat") && arity >= 2 && getNamespace().equals(NamespaceConstant.FN)) {
//            key = "concat#-1";
//            entry = functionTable.get(key);
//            return entry;
//        }
        return null;
    }

    /**
     * Bind a function, given the URI and local parts of the function name,
     * and the list of expressions supplied as arguments. This method is called at compile
     * time.
     *
     * @param symbolicName the symbolic name of the function being called
     * @param staticArgs   May be null; if present, the length of the array must match the
     *                     value of arity. Contains the expressions supplied statically in arguments to the function call.
     *                     The intention is
     *                     that the static type of the arguments (obtainable via getItemType() and getCardinality()) may
     *                     be used as part of the binding algorithm. In some cases it may be possible for the function
     *                     to be pre-evaluated at compile time, for example if these expressions are all constant values.
     *                     <p>The conventions of the XPath language demand that the results of a function depend only on the
     *                     values of the expressions supplied as arguments, and not on the form of those expressions. For
     *                     example, the result of f(4) is expected to be the same as f(2+2). The actual expression is supplied
     *                     here to enable the binding mechanism to select the most efficient possible implementation (including
     *                     compile-time pre-evaluation where appropriate).</p>
     * @param keywords     Keywords used in keyword parameters, with the 0-based integer position of the argument they are used
     *                     on.
     * @param env          The static context of the function call
     * @param reasons      If no matching function is found by the function library, it may add
     *                     a diagnostic explanation to this list explaining why none of the available
     *                     functions could be used.
     * @return An expression equivalent to a call on the specified function, if one is found;
     * null if no function was found matching the required name and arity.
     */
    @Override
    public Expression bind(SymbolicName.F symbolicName, Expression[] staticArgs,
                           Map<StructuredQName, Integer> keywords,
                           StaticContext env, List<String> reasons)
    throws XPathException {
        StructuredQName functionName = symbolicName.getComponentName();
        int arity = symbolicName.getArity();
        String localName = functionName.getLocalPart();
        net.sf.saxon.functions.registry.BuiltInFunctionSet.Entry entry = getFunctionDetails(localName, arity);
        if (functionName.hasURI(getNamespace()) && entry != null) {
            entry.ensurePopulated();
            if ((entry.properties & SEQV) != 0) {
                // sequence-variadic functions in 4.0 (e.g..concat)
                // combine the "variable" arguments into a single argument
                if (env.getXPathVersion() < 40) {
                    // Need to special-case the fn:concat() function prior to XPath 4.0
                    if (localName.equals("concat")) {
                        if (staticArgs.length < 2) {
                            reasons.add("concat() prior to XPath 4.0 requires at least two arguments");
                            return null;
                        }
                    }
                }

            } else if ((keywords != null && !keywords.isEmpty()) || staticArgs.length < entry.maxArity) {
                staticArgs = UserFunction.makeExpandedArgumentArray(staticArgs, keywords, entry);
            }
            RetainedStaticContext rsc = new RetainedStaticContext(env);
            try {
                SystemFunction fn = makeFunction(localName, staticArgs.length);
                fn.setRetainedStaticContext(rsc);
                Expression f = fn.makeFunctionCall(staticArgs);
                if (!(f instanceof SystemFunctionCall)) {
                    for (Expression arg : staticArgs) {
                        if (arg instanceof PlaceHolder) {
                            f = fn.makeTrueFunctionCall(staticArgs);
                            break;
                        }
                    }
                }
                f.setRetainedStaticContext(rsc);
                return f;
            } catch (XPathException e) {
                reasons.add(e.getMessage());
                return null;
            }
        } else {
            return null;
        }
    }

    public SystemFunction makeFunction(String name, int arity) throws XPathException {
        net.sf.saxon.functions.registry.BuiltInFunctionSet.Entry entry = getFunctionDetails(name, arity);
        if (entry == null) {
            String diagName = getNamespace().equals(NamespaceUri.FN) ?
                    "System function " + name :
                    "Function Q{" + getNamespace() + "}" + name;
            if (getFunctionDetails(name, -1) == null) {
                throw new XPathException(diagName + "() does not exist or is not available in this environment")
                        .withErrorCode("XPST0017").asStaticError();
            } else {
                throw new XPathException(diagName + "() cannot be called with "
                                                                + pluralArguments(arity))
                        .withErrorCode("XPST0017").asStaticError();
            }
        }
        entry.ensurePopulated();
        SystemFunction f = entry.implementationFactory.get();
        f.setDetails(entry);
        f.setArity(arity);
        return f;
    }

    /**
     * Utility routine used in constructing error messages
     *
     * @param num the number of arguments
     * @return the string " argument" or "arguments" depending whether num is plural
     */

    private static String pluralArguments(int num) {
        if (num == 0) {
            return "zero arguments";
        }
        if (num == 1) {
            return "one argument";
        }
        return num + " arguments";
    }

    /**
     * Test whether a function with a given name and arity is available
     * <p>This supports the function-available() function in XSLT.</p>
     *
     * @param symbolicName  the qualified name of the function being called, together with its arity.
     *                      For legacy reasons, the arity may be set to -1 to mean any arity will do
     * @param schema
     * @param languageLevel the XPath language level (times 10, e.g. 31 for XPath 3.1)
     * @return true if a function of this name and arity is available for calling
     */
    @Override
    public boolean isAvailable(SymbolicName.F symbolicName, Schema schema, int languageLevel) {
        StructuredQName qn = symbolicName.getComponentName();
        if (!qn.hasURI(getNamespace())) {
            return false;
        }
        net.sf.saxon.functions.registry.BuiltInFunctionSet.Entry entry = getFunctionDetails(qn.getLocalPart(), symbolicName.getArity());
        if (entry == null) {
            return false;
        }
        //if ((entry.properties & SEQV) != 0) {
            // sequence-variadic functions in 4.0 (e.g..concat, codepoints-to-string)
            // combine the "variable" arguments into a single argument
            if (languageLevel < 40 &&
                    symbolicName.getComponentName().getLocalPart().equals("concat") &&
                    symbolicName.getArity() < 2) {
                return false;
            }
        //}
        return true;

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

    /**
     * Test whether a function with a given name and arity is available; if so, return a function
     * item that can be dynamically called.
     * <p>This supports the function-lookup() function in XPath 3.0.</p>
     *
     * @param symbolicName  the qualified name of the function being called
     * @param staticContext the static context to be used by the function, in the event that
     *                      it is a system function with dependencies on the static context
     * @return if a function of this name and arity is available for calling, then a corresponding
     * function item; or null if the function does not exist
     * @throws XPathException in the event of certain errors, for example attempting to get a function
     *                        that is private
     */
    @Override
    public FunctionItem getFunctionItem(SymbolicName.F symbolicName, StaticContext staticContext) throws XPathException {
        StructuredQName functionName = symbolicName.getComponentName();
        int arity = symbolicName.getArity();
        if (functionName.hasURI(getNamespace()) && getFunctionDetails(functionName.getLocalPart(), arity) != null) {
            RetainedStaticContext rsc = staticContext.makeRetainedStaticContext();
            SystemFunction fn = makeFunction(functionName.getLocalPart(), arity);
            if (staticContext.getXPathVersion() < 40 && fn instanceof Concat31 && arity < 2) {
                // Treat concat() specially prior to 4.0
                return null;
            }
            fn.setRetainedStaticContext(rsc);
            return fn;
        } else {
            return null;
        }
    }


    /**
     * Register a system function in the table of function details.
     *
     * @param name                the function name
     * @param arity               the function arity (-1 is treated specially)
     * @param populator           a function to perform initialization of the entry, called
     *                            the first time an entry for the function is actually needed

     * @return the entry describing the function. The entry is incomplete, it does not yet contain information
     * about the function arguments.
     */

    protected Entry register(String name,
                             int arity,
                             java.util.function.Function<Entry, Entry> populator) {
        Entry e = new Entry();
        e.name = new StructuredQName(getConventionalPrefix(), getNamespace(), name);
        e.minArity = arity;
        e.maxArity = arity;
        e.populator = populator;
        e.functionSet = this;
        functionTable.put(name + "#" + arity, e);
        return e;
    }


    protected Entry register(String name,
                             int minArity,
                             int maxArity,
                             java.util.function.Function<Entry, Entry> populator) {
        Entry e = new Entry();
        e.name = new StructuredQName(getConventionalPrefix(), getNamespace(), name);
        e.minArity = minArity;
        e.maxArity = maxArity;
        e.populator = populator;
        e.functionSet = this;
        for (int a = minArity; a <= maxArity; a++) {
            functionTable.put(name + "#" + a, e);
        }
        return e;
    }

    protected Entry registerVariadic(String name,
                             int arity,
                             java.util.function.Function<Entry, Entry> populator) {
        Entry e = register(name, arity, populator);
        sequenceVariadicFunctions.put(name, arity - 1);
        return e;
    }

    /**
     * Return the namespace URI for the functions local to this function set.
     * @return the namespace URI of the functions local to this function set.
     * Note that functions imported from another function set may have a different
     * namespace URI.
     */

    public NamespaceUri getNamespace() {
        return NamespaceUri.FN;
    }

    /**
     * Return a conventional prefix for use with this namespace, typically
     * the prefix used in the documentation of these functions.
     * @return the string "fn"
     */

    public String getConventionalPrefix() {
        return "fn";
    }

    /**
     * An entry in the table describing the properties of a function
     */
    public static class Entry implements FunctionDefinition {
        /**
         * The name of the function as a QName
         */
        public StructuredQName name;
        /**
         * The class containing the implementation of this function (always a subclass of SystemFunction)
         */
        public Supplier<SystemFunction> implementationFactory;

        public java.util.function.Function<Entry, Entry> populator;
        /**
         * The function set in which this function is defined
         */
        public net.sf.saxon.functions.registry.BuiltInFunctionSet functionSet;
        /**
         * The upper bound of the arity range
         */
        public int maxArity;
        /**
         * The lower bound of the arity range
         */
        public int minArity;
        /**
         * The item type of the result of the function
         */
        public ItemType itemType;
        /**
         * The cardinality of the result of the function
         */
        public int cardinality;
        /**
         * The syntactic context of each argument for the purposes of streamability analysis
         */
        public OperandUsage[] usage;
        /**
         * An array holding the names of the parameters to the function
         */
        public String[] paramNames;
        /**
         * An array holding the types of the parameters to the function
         */
        public SequenceType[] paramTypes;
        /**
         * An array holding, for each declared argument, the value that is to be returned if an empty sequence
         * as the value of this argument allows the result to be determined irrespective of the values of the
         * other arguments; null if there is no such calculation possible
         */
        public Sequence[] resultIfEmpty;
        /**
         * An array holding functions to evaluate default arguments. The array is
         * allocated only if there are parameters with default values defined
         */
        public IntHashMap<Supplier<Expression>> defaultValueExpressions;
        /**
         * Any additional properties. Various bit settings are defined: for example SAME_AS_FIRST_ARGUMENT indicates that
         * the result type is the same as the type of the first argument
         */
        public int properties;

        /**
         * For options parameters, details of the accepted options, their defaults, and required type
         */
        public OptionsParameter optionDetails;

        public synchronized void ensurePopulated() {
            if (implementationFactory == null) {
                populator.apply(this);
            }
        }

        public Entry populate(Supplier<SystemFunction> functionFactory,
                              ItemType itemType,
                              int cardinality,
                              int properties) {

            this.implementationFactory = functionFactory;
            this.itemType = itemType;
            this.cardinality = cardinality;
            this.properties = properties;
            if (this.maxArity == -1) {
                // special case for concat()
                this.paramTypes = new SequenceType[1];
                this.resultIfEmpty = new AtomicValue[1];
                this.usage = new OperandUsage[1];
            } else {
                this.paramTypes = new SequenceType[maxArity];
                this.resultIfEmpty = new Sequence[maxArity];
                this.usage = new OperandUsage[maxArity];
            }
            if ((properties & SEQV) != 0) {
                this.functionSet.sequenceVariadicFunctions.put(name.getLocalPart(), this.maxArity - 1);
            }
            NamespaceUri ns = name.getNamespaceUri();
            Map<String, String> paramNameMap;
            if (ns == NamespaceUri.FN) {
                paramNameMap = ParamKeywords.fnParamNames;
            } else if (ns == NamespaceUri.MAP_FUNCTIONS) {
                paramNameMap = ParamKeywords.mapParamNames;
            } else if (ns == NamespaceUri.ARRAY_FUNCTIONS) {
                paramNameMap = ParamKeywords.arrayParamNames;
            } else if (ns == NamespaceUri.MATH) {
                paramNameMap = ParamKeywords.mathParamNames;
            } else {
                paramNameMap = Collections.emptyMap();
            }
            String keywords = paramNameMap.get(name.getLocalPart());
            if (keywords == null) {
                keywords = paramNameMap.get(name.getLocalPart() + "#" + maxArity);
            }
            if (keywords == null) {
                keywords = "a|b|c|d|e|f";
            }
            this.paramNames = keywords.split("\\|");
            return this;
        }

        /**
         * Add information to a function entry about the argument types of the function
         *
         * @param a             the position of the argument, counting from zero
         * @param type          the item type of the argument
         * @param options       the cardinality and usage of the argument
         * @param resultIfEmpty the value returned by the function if an empty sequence appears as the value
         *                      of this argument, in the case when this result is unaffected by any other arguments. Supply null
         *                      if this does not apply.
         * @return this entry (to allow chaining)
         */

        public net.sf.saxon.functions.registry.BuiltInFunctionSet.Entry arg(int a, ItemType type, int options, Sequence resultIfEmpty) {
            assert paramTypes != null;
            int cardinality = options & StaticProperty.CARDINALITY_MASK;
            OperandUsage usage = OperandUsage.NAVIGATION;
            if ((options & ABS) != 0) {
                usage = OperandUsage.ABSORPTION;
            } else if ((options & TRA) != 0) {
                usage = OperandUsage.TRANSMISSION;
            } else if ((options & INS) != 0) {
                usage = OperandUsage.INSPECTION;
            } else if (type instanceof PlainType) {
                usage = OperandUsage.ABSORPTION;
            }
            try {
                this.paramTypes[a] = SequenceType.makeSequenceType(type, cardinality);
                this.resultIfEmpty[a] = resultIfEmpty;
                this.usage[a] = usage;
//                if (defaultValue != null) {
//                    withDefault(a, defaultValue);
//                }
            } catch (ArrayIndexOutOfBoundsException err) {
                System.err.println("Internal Saxon error: Can't set argument " + a + " of " + name);
            }
            return this;
        }

        /**
         * Add information to a function entry about the argument types of the function.
         * This version of the method allows the default value of the argument to be defined.
         * TODO: However, this mechanism isn't fully working. It works for static function calls,
         *   but not for dynamic calls of a reduced-arity version of the function. The function
         *   implementation still needs to be aware in its call() method that some arguments
         *   might be missing.
         *
         * @param a             the position of the argument, counting from zero
         * @param type          the item type of the argument
         * @param options       the cardinality and usage of the argument
         * @param defaultExpr   supplies an expression that acts as the default value
         * @return this entry (to allow chaining)
         */

        public net.sf.saxon.functions.registry.BuiltInFunctionSet.Entry arg(
                int a, ItemType type, int options, Sequence resultIfEmpty, Supplier<Expression> defaultExpr) {
            return arg(a, type, options, resultIfEmpty)
                    .withDefault(a, defaultExpr);


        }

        private net.sf.saxon.functions.registry.BuiltInFunctionSet.Entry withDefault(int arg, Supplier<Expression> defaultValue) {
            if (this.defaultValueExpressions == null) {
                this.defaultValueExpressions = new IntHashMap<>(getNumberOfParameters());
            }
            this.defaultValueExpressions.put(arg, defaultValue);
            // if argument N can be defaulted, then register a variant of the function with arity N-1
            return this;
        }

        /**
         * Add details for options parameters (only applies to one argument, the function is expected to know which)
         */

        public net.sf.saxon.functions.registry.BuiltInFunctionSet.Entry setOptionDetails(OptionsParameter details) {
            this.optionDetails = details;
            return this;
        }

        /**
         * Get the name of the function
         *
         * @return the function name
         */
        @Override
        public StructuredQName getFunctionName() {
            return name;
        }

        @Override
        public int getNumberOfParameters() {
            return maxArity;
        }

        /**
         * Get the number of mandatory arguments (the lower bound of the arity range)
         *
         * @return the number of mandatory arguments
         */
        @Override
        public int getMinimumArity() {
            return minArity;
        }

        /**
         * Get the name (keyword) of the Nth parameter
         *
         * @param i the position of the required parameter
         * @return the expression for computing the value of the Nth parameter
         */
        @Override
        public StructuredQName getParameterName(int i) {
            return new StructuredQName("", "", paramNames[i]);
        }

        /**
         * Get the default value expression of the Nth parameter, if any
         *
         * @param i the position of the required parameter
         * @return the expression for computing the value of the Nth parameter, or null if there is none
         */
        @Override
        public Supplier<Expression> getDefaultValueExpression(int i) {
            if (defaultValueExpressions != null) {
                return defaultValueExpressions.get(i);
            } else {
                return null;
            }
        }

        /**
         * Get the position in the parameter list of a given parameter name
         *
         * @param name the name of the required parameter
         * @return the position of the parameter in the parameter list, or -1 if absent
         */
        @Override
        public int getPositionOfParameter(StructuredQName name) {
            for (int i = 0; i< maxArity; i++) {
                if (paramNames[i].equals(name.getLocalPart())) {
                    return i;
                }
            }
            return -1;
        }
    }

}

