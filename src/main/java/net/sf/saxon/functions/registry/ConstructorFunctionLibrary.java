////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.registry;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.functions.*;
import net.sf.saxon.functions.hof.AtomicConstructorFunction;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;

import java.util.List;
import java.util.Map;

/**
 * The ConstructorFunctionLibrary represents the collection of constructor functions for atomic types. These
 * are provided for the built-in types such as xs:integer and xs:date, and also for user-defined atomic types.
 */

public class ConstructorFunctionLibrary implements FunctionLibrary {

    private final Configuration config;

    /**
     * Create a SystemFunctionLibrary
     *
     * @param config the Configuration
     */

    public ConstructorFunctionLibrary(Configuration config) {
        this.config = config;
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
     * @throws net.sf.saxon.trans.XPathException
     *          in the event of certain errors, for example attempting to get a function
     *          that is private
     */
    @Override
    public FunctionItem getFunctionItem(SymbolicName.F functionName, StaticContext staticContext) throws XPathException {
        int arity = functionName.getArity();;
        if (arity > 1) {
            return null;
        }
        if (arity == 0 && staticContext.getPackageData().getHostLanguageVersion() < 40) {
            return null;
        }
        Schema schema = staticContext.getImportedSchema();
        final NamespaceUri uri = functionName.getComponentName().getNamespaceUri();
        if (uri.equals(NamespaceUri.ANONYMOUS)) {
            return null;
        }
        final String localName = functionName.getComponentName().getLocalPart();
        final SchemaType type = schema.getSchemaType(new StructuredQName("", uri, localName));
        if (type == null || type.isComplexType()) {
            return null;
        }
        if (arity == 0) {
            FunctionItem arity1Function = getFunctionItem(
                    new SymbolicName.F(functionName.getComponentName(), 1), staticContext);
            if (arity1Function == null) {
                return null;
            }
            return new ZeroArityConstructorFunction(arity1Function);
        }
        final NamespaceResolver resolver = ((SimpleType) type).isNamespaceSensitive() ? staticContext.getNamespaceResolver() : null;
        return makeConstructorFunction((SimpleType)type, config.getConversionRules(), resolver);
    }

    /**
     * Make a constructor function for simple type
     * @param type the simple type
     * @param rules the conversion rules in force (XSD 1.1 rules allow "+INF", for example)
     * @param resolver the namespace resolver, needed if the simple type is namespace-sensitive. May be null if
     *                 the type is known not to be namespace-sensitive
     * @return a constructor function for the type. Returns null if the type is abstract, or if it is namespace-sensitive
     * and no resolver is supplied.
     */
    public static FunctionItem makeConstructorFunction(SimpleType type, ConversionRules rules, NamespaceResolver resolver) {
        if (type == AnySimpleType.INSTANCE || type == BuiltInAtomicType.ANY_ATOMIC || type == BuiltInAtomicType.NOTATION) {
            return null; // no constructor is defined for abstract types
        }
        if (resolver == null && type.isNamespaceSensitive()) {
            return null;
        }
        if (type instanceof AtomicType) {
            return new AtomicConstructorFunction((AtomicType) type, resolver);
        } else if (type instanceof ListType) {
            return new ListConstructorFunction((ListType) type, resolver, true);
        } else {
            assert type instanceof UnionType;
            SequenceType returnType = ((UnionType) type).getResultTypeOfCast();
            return new SimpleUnaryFunction(
                arg -> {
                    AtomicValue value = (AtomicValue) arg.head();
                    if (value == null) {
                        return EmptySequence.INSTANCE;
                    }
                    return UnionConstructorFunction.cast(value, (UnionType) type, resolver, rules);
                },
                SequenceType.OPTIONAL_ATOMIC,
                returnType
            );
        }
    }

    @Override
    public boolean isAvailable(SymbolicName.F functionName, Schema schema, int languageLevel) {
        if (functionName.getArity() != 1) {
            return false;
        }
        StructuredQName name = functionName.getComponentName();
        final SchemaType type = schema.getSchemaType(name);
        if (type == null || type.isComplexType()) {
            return false;
        }
        if (type.isAtomicType() && ((AtomicType) type).isAbstract()) {
            return false;
        }
        return type != AnySimpleType.INSTANCE;
    }

    /**
     * Bind a static function call, given the URI and local parts of the function name,
     * and the list of expressions supplied as arguments. This method is called at compile
     * time.
     *
     * @param functionName The QName of the function
     * @param arguments    The expressions supplied statically in the function call. The intention is
     *                     that the static type of the arguments (obtainable via getItemType() and getCardinality() may
     *                     be used as part of the binding algorithm.
     * @param keywords     May be null if no keywords are used in the function call. Otherwise, a map identifying the
     *                     keywords appearing in the function call, and the 0-based position at which they appeared.
     * @param env          The static context
     * @param reasons      If no matching function is found by the function library, it may add
     *                     a diagnostic explanation to this list explaining why none of the available
     *                     functions could be used.
     * @return An object representing the constructor function to be called, if one is found;
     *         null if no constructor function was found matching the required name and arity.
     */

    @Override
    public Expression bind(SymbolicName.F functionName, Expression[] arguments, Map<StructuredQName, Integer> keywords, StaticContext env, List<String> reasons) {
        final NamespaceUri uri = functionName.getComponentName().getNamespaceUri();
        final String localName = functionName.getComponentName().getLocalPart();
        boolean builtInNamespace = uri.equals(NamespaceUri.SCHEMA);
        if (builtInNamespace) {
            int languageVersion = env.getXPathVersion();
            if (languageVersion >= 40 && arguments.length == 0) {
                SymbolicName.F f1 = new SymbolicName.F(functionName.getComponentName(), 1);
                return bind(f1, new Expression[]{new ContextItemExpression()}, keywords, env, reasons);
            } else if (functionName.getArity() != 1) {
                reasons.add("A constructor function must have exactly one argument");
                return null;
            }
            if (keywords != null && !keywords.isEmpty()) {
                if (keywords.size() != 1) {
                    reasons.add("The keyword for the sole argument of a constructor function is 'value'");
                    return null;
                }
                for (Map.Entry<StructuredQName, Integer> kw : keywords.entrySet()) {
                    if (kw.getKey().getEQName().equals("Q{}value")) {
                        if (kw.getValue() != 0) {
                            reasons.add("The 'value' keyword in a constructor function call must be the first and only argument");
                            return null;
                        }
                    } else {
                        reasons.add("The argument keyword '" + kw.getKey().getEQName() + " is not allowed in a constructor function call");
                        return null;
                    }
                }
            }
            SimpleType type = Type.getBuiltInSimpleType(uri, localName);
            if (type != null) {
                if (type.isAtomicType()) {
                    if (((AtomicType) type).isAbstract()) {
                        reasons.add("Abstract type used in constructor function: {" + uri + '}' + localName);
                        return null;
                    } else {
                        CastExpression cast = new CastExpression(arguments[0], (AtomicType) type, true);
                        if (arguments[0] instanceof StringLiteral) {
                            cast.setOperandIsStringLiteral(true);
                        }
                        return cast;
                    }
                } else if (type.isUnionType()) {
                    NamespaceResolver resolver = env.getNamespaceResolver();
                    UnionConstructorFunction ucf = new UnionConstructorFunction((UnionType) type, resolver, true);
                    return new StaticFunctionCall(ucf, arguments);
                } else if (type == AnySimpleType.INSTANCE) {
                    reasons.add("Abstract type used in constructor function: {" + uri + '}' + localName);
                    return null;
                } else {
                    NamespaceResolver resolver = env.getNamespaceResolver();
                    try {
                        ListConstructorFunction lcf = new ListConstructorFunction((ListType)type, resolver, true);
                        return new StaticFunctionCall(lcf, arguments);
                    } catch (MissingComponentException e) {
                        reasons.add("Missing schema component: " + e.getMessage());
                        return null;
                    }
                }
            } else {
                reasons.add("Unknown constructor function: {" + uri + '}' + localName);
                return null;
            }

        }

        // Now see if it's a constructor function for a user-defined type

        if (arguments.length == 1) {
            Schema schema = env.getImportedSchema();
            SchemaType st = schema.getSchemaType(new StructuredQName("", uri, localName));
            if (st instanceof SimpleType) {
                if (st instanceof AtomicType) {
                    return new CastExpression(arguments[0], (AtomicType) st, true);
                } else if (st instanceof ListType && env.getXPathVersion() >= 30) {
                    NamespaceResolver resolver = env.getNamespaceResolver();
                    try {
                        ListConstructorFunction lcf = new ListConstructorFunction((ListType) st, resolver, true);
                        return new StaticFunctionCall(lcf, arguments);
                    } catch (MissingComponentException e) {
                        reasons.add("Missing schema component: " + e.getMessage());
                        return null;
                    }
                } else if (((SimpleType) st).isUnionType() && env.getXPathVersion() >= 30) {
                    NamespaceResolver resolver = env.getNamespaceResolver();
                    UnionConstructorFunction ucf = new UnionConstructorFunction((UnionType) st, resolver, true);
                    return new StaticFunctionCall(ucf, arguments);
                }
            }
        }

        return null;
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
     * A function representing a zero-arity constructor reference such as `xs:integer#0`. These
     * are fairly useless, and the implementation is surprisingly complex, but they are needed
     * for orthogonality. Any attempt to invoke the function ends up invoking the arity-1
     * equivalent, with the atomized value of the context item as the supplied argument value.
     */

    private static class ZeroArityConstructorFunction extends AbstractFunction implements IContextAccessorFunction {

        FunctionItem arityOneConstructor;

        public ZeroArityConstructorFunction(FunctionItem arityOneConstructor) {
            this.arityOneConstructor = arityOneConstructor;
        }

        @Override
        public boolean dependsOnContext() {
            return true;
        }

        /**
         * Call the Callable.
         *
         * @param context   the dynamic evaluation context
         * @param arguments the values of the arguments, supplied as Sequences.
         *                  <p>Generally it is advisable, if calling iterate() to process a supplied sequence, to
         *                  call it only once; if the value is required more than once, it should first be converted
         *                  to a {@link GroundedValue} by calling the utility method
         *                  SequenceTool.toGroundedValue().</p>
         *                  <p>If the expected value is a single item, the item should be obtained by calling
         *                  Sequence.head(): it cannot be assumed that the item will be passed as an instance of
         *                  {@link Item} or {@link AtomicValue}.</p>
         *                  <p>It is the caller's responsibility to perform any type conversions required
         *                  to convert arguments to the type expected by the callee. An exception is where
         *                  this Callable is explicitly an argument-converting wrapper around the original
         *                  Callable.</p>
         * @return the result of the evaluation, in the form of a Sequence. It is the responsibility
         * of the callee to ensure that the type of result conforms to the expected result type.
         * @throws XPathException if a dynamic error occurs during the evaluation of the expression
         */
        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            return bindContext(context).call(context, arguments);
        }

        /**
         * Bind context information to appear as part of the function's closure. If this method
         * has been called, the supplied context will be used in preference to the
         * context at the point where the function is actually called.
         *
         * @param context the context to which the function applies. Must not be null.
         */
        @Override
        public FunctionItem bindContext(XPathContext context) throws XPathException {
            try {
                Item item = context.getContextItem();
                if (item == null) {
                    throw new XPathException("No context item for constructor function", "XPDY0002");
                }
                AtomicSequence atomized = item.atomize();
                GroundedValue result = arityOneConstructor.call(
                                context, new Sequence[]{atomized})
                        .materialize();
                return new ConstantFunction(arityOneConstructor.getFunctionName(),
                                            result,
                                            arityOneConstructor.getFunctionItemType().getResultType());
            } catch (XPathException xe) {
                throw xe;
            } catch (Exception e) {
                throw new XPathException("Unsuitable context for constructor function "
                                                 + getDescription() + ": " + e.getMessage());
            }
        }

        /**
         * Get the item type of the function item
         *
         * @return the function item's type
         */
        @Override
        public FunctionItemType getFunctionItemType() {
            return new SpecificFunctionType(arityOneConstructor.getFunctionItemType().getResultType());
        }

        /**
         * Get the name of the function, or null if it is anonymous
         *
         * @return the function name, or null for an anonymous inline function
         */
        @Override
        public StructuredQName getFunctionName() {
            return arityOneConstructor.getFunctionName();
        }

        /**
         * Get the arity of the function
         *
         * @return the number of arguments in the function signature
         */
        @Override
        public int getArity() {
            return 0;
        }

        /**
         * Get a description of this function for use in error messages. For named functions, the description
         * is the function name (as a lexical QName). For others, it might be, for example, "inline function",
         * or "partially-applied ends-with function".
         *
         * @return a description of the function for use in error messages
         */
        @Override
        public String getDescription() {
            return getFunctionName() + "#0";
        }
    }

}
