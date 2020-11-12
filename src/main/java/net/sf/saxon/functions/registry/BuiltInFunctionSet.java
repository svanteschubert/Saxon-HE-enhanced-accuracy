////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.registry;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.OperandUsage;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.functions.OptionsParameter;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.PlainType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;

import java.util.HashMap;
import java.util.List;

/**
 * This class is used to contain information about a set of built-in functions.
 */
public abstract class BuiltInFunctionSet implements FunctionLibrary {

    public static Sequence EMPTY = EmptySequence.getInstance();

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
    public static final int AS_PRIM_ARG0 = 2;     // Result has same primitive type as first argument
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


    private HashMap<String, Entry> functionTable = new HashMap<>(200);
    //private List<BuiltInFunctionSet> importedFunctions = new ArrayList<BuiltInFunctionSet>();

    /**
     * Import another function set (which must be in the same namespace)
     * @param importee the function set to be imported. (No cycles allowed!)
     */

    public final void importFunctionSet(BuiltInFunctionSet importee) {
        if (!importee.getNamespace().equals(getNamespace())) {
            throw new IllegalArgumentException(importee.getNamespace());
        }
        functionTable.putAll(importee.functionTable);
        //importedFunctions.add(importee);
    }

    /**
     * Locate the entry for a function with a given name and arity, if it exists
     * @param name the local part of the function name
     * @param arity the arity of the function. -1 considers all possibly arities and returns an arbitrary function
     *              if one exists with the right name.
     * @return the entry for the required function, or null if not found
     */

    public Entry getFunctionDetails(String name, int arity) {
        if (arity == -1) {
            for (int i=0; i<20; i++) {
                Entry found = getFunctionDetails(name, i);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        String key = name + "#" + arity;
        Entry entry = functionTable.get(key);
        if (entry != null) {
            return entry;
        }
        // Try for a variable-arity function (concat only)
        if (name.equals("concat") && arity >= 2 && getNamespace().equals(NamespaceConstant.FN)) {
            key = "concat#-1";
            entry = functionTable.get(key);
            return entry;
        }
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
     * @param env          The static context of the function call
     * @param reasons      If no matching function is found by the function library, it may add
     *                     a diagnostic explanation to this list explaining why none of the available
     *                     functions could be used.
     * @return An expression equivalent to a call on the specified function, if one is found;
     * null if no function was found matching the required name and arity.
     */
    @Override
    public Expression bind(SymbolicName.F symbolicName, Expression[] staticArgs, StaticContext env, List<String> reasons) {
        StructuredQName functionName = symbolicName.getComponentName();
        int arity = symbolicName.getArity();
        String localName = functionName.getLocalPart();
        if (functionName.hasURI(getNamespace()) && getFunctionDetails(localName, arity) != null) {
            RetainedStaticContext rsc = new RetainedStaticContext(env);
            try {
                SystemFunction fn = makeFunction(localName, arity);
                fn.setRetainedStaticContext(rsc);
                Expression f = fn.makeFunctionCall(staticArgs);
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
        Entry entry = getFunctionDetails(name, arity);
        if (entry == null) {
            String diagName = getNamespace().equals(NamespaceConstant.FN) ?
                    "System function " + name :
                    "Function Q{" + getNamespace() + "}" + name;
            if (getFunctionDetails(name, -1) == null) {
                XPathException err = new XPathException(diagName + "() does not exist or is not available in this environment");
                err.setErrorCode("XPST0017");
                err.setIsStaticError(true);
                throw err;
            } else {
                XPathException err = new XPathException(diagName + "() cannot be called with "
                                                                + pluralArguments(arity));
                err.setErrorCode("XPST0017");
                err.setIsStaticError(true);
                throw err;
            }
        }

        Class functionClass = entry.implementationClass;
        SystemFunction f;
        try {
            f = (SystemFunction) functionClass.newInstance();
        } catch (Exception err) {
            err.printStackTrace();
            throw new AssertionError("Failed to instantiate system function " + name + " - " + err.getMessage());
        }
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
     * @param symbolicName the qualified name of the function being called, together with its arity.
     *                     For legacy reasons, the arity may be set to -1 to mean any arity will do
     * @return true if a function of this name and arity is available for calling
     */
    @Override
    public boolean isAvailable(SymbolicName.F symbolicName) {
        StructuredQName qn = symbolicName.getComponentName();
        return qn.hasURI(getNamespace()) && getFunctionDetails(qn.getLocalPart(), symbolicName.getArity()) != null;
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
    public Function getFunctionItem(SymbolicName.F symbolicName, StaticContext staticContext) throws XPathException {
        StructuredQName functionName = symbolicName.getComponentName();
        int arity = symbolicName.getArity();
        if (functionName.hasURI(getNamespace()) && getFunctionDetails(functionName.getLocalPart(), arity) != null) {
            RetainedStaticContext rsc = staticContext.makeRetainedStaticContext();
            SystemFunction fn = makeFunction(functionName.getLocalPart(), arity);
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
     * @param implementationClass the class used to implement the function
     * @param itemType            the item type of the result of the function
     * @param cardinality         the cardinality of the result of the function
     * @param properties          bitwise properties of the function
     * @return the entry describing the function. The entry is incomplete, it does not yet contain information
     * about the function arguments.
     */

    /*@NotNull*/
    protected Entry register(String name,
                             int arity,
                             Class<? extends SystemFunction> implementationClass,
                             ItemType itemType,
                             int cardinality,
                             int properties) {
        Entry e = new Entry();
        e.name = new StructuredQName(getConventionalPrefix(), getNamespace(), name);
        e.arity = arity;
        e.implementationClass = implementationClass;
        e.itemType = itemType;
        e.cardinality = cardinality;
        e.properties = properties;
        if (e.arity == -1) {
            // special case for concat()
            e.argumentTypes = new SequenceType[1];
            e.resultIfEmpty = new AtomicValue[1];
            e.usage = new OperandUsage[1];
        } else {
            e.argumentTypes = new SequenceType[arity];
            e.resultIfEmpty = (Sequence[])new Sequence[arity];
            e.usage = new OperandUsage[arity];
        }
        functionTable.put(name + "#" + arity, e);
        return e;
    }

    /**
     * Register reduced-arity versions of a function, with arities in a specified range,
     * using common argument and return types
     */

    protected void registerReducedArityVariants(String key, int min, int max) {
        Entry master = functionTable.get(key);
        for (int arity = min; arity <= max; arity++) {
            Entry e = new Entry();
            e.name = master.name;
            e.arity = arity;
            e.implementationClass = master.implementationClass;
            e.itemType = master.itemType;
            e.cardinality = master.cardinality;
            e.properties = master.properties;
            e.argumentTypes = new SequenceType[arity];
            e.resultIfEmpty = (Sequence[])new Sequence[arity];
            e.usage = new OperandUsage[arity];
            for (int i=0; i<arity; i++) {
                e.argumentTypes[i] = master.argumentTypes[i];
                e.resultIfEmpty[i] = master.resultIfEmpty[i];
                e.usage[i] = master.usage[i];
            }
            functionTable.put(e.name.getLocalPart() + "#" + arity, e);
        }
    }

    /**
     * Return the namespace URI for the functions local to this function set.
     * @return the namespace URI of the functions local to this function set.
     * Note that functions imported from another function set may have a different
     * namespace URI.
     */

    public String getNamespace() {
        return NamespaceConstant.FN;
    }

    /**
     * Return a conventional prefix for use with this namespace, typically
     * the prefix used in the documentation of these functions.
     */

    public String getConventionalPrefix() {
        return "fn";
    }

    /**
     * An entry in the table describing the properties of a function
     */
    public static class Entry {
        /**
         * The name of the function as a QName
         */
        public StructuredQName name;
        /**
         * The class containing the implementation of this function (always a subclass of SystemFunction)
         */
        public Class implementationClass;
        /**
         * The arity of the function
         */
        public int arity;
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
         * An array holding the types of the arguments to the function
         */
        public SequenceType[] argumentTypes;
        /**
         * An array holding, for each declared argument, the value that is to be returned if an empty sequence
         * as the value of this argument allows the result to be determined irrespective of the values of the
         * other arguments; null if there is no such calculation possible
         */
        public Sequence[] resultIfEmpty;
        /**
         * Any additional properties. Various bit settings are defined: for example SAME_AS_FIRST_ARGUMENT indicates that
         * the result type is the same as the type of the first argument
         */
        public int properties;
        /**
         * For options parameters, details of the accepted options, their defaults, and required type
         */
        public OptionsParameter optionDetails;

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

        public Entry arg(int a, ItemType type, int options, Sequence resultIfEmpty) {
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
                this.argumentTypes[a] = SequenceType.makeSequenceType(type, cardinality);
                this.resultIfEmpty[a] = resultIfEmpty;
                this.usage[a] = usage;
            } catch (ArrayIndexOutOfBoundsException err) {
                System.err.println("Internal Saxon error: Can't set argument " + a + " of " + name);
            }
            return this;
        }

        /**
         * Add details for options parameters (only applies to one argument, the function is expected to know which)
         */

        public Entry optionDetails(OptionsParameter details) {
            this.optionDetails = details;
            return this;
        }

    }


}

