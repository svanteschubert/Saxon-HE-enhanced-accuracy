////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.registry;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.functions.CallableFunction;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.functions.hof.AtomicConstructorFunction;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.StructuredQName;
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
        if (functionName.getArity() != 1) {
            return null;
        }
        final NamespaceUri uri = functionName.getComponentName().getNamespaceUri();
        if (uri.equals(NamespaceUri.ANONYMOUS)) {
            return null;
        }
        final String localName = functionName.getComponentName().getLocalPart();
        final SchemaType type = config.getSchemaType(new StructuredQName("", uri, localName));
        if (type == null || type.isComplexType()) {
            return null;
        }
        final NamespaceResolver resolver = ((SimpleType) type).isNamespaceSensitive() ? staticContext.getNamespaceResolver() : null;
        if (type instanceof AtomicType) {
            return new AtomicConstructorFunction((AtomicType) type, resolver);
        } else if (type instanceof ListType) {
            return new ListConstructorFunction((ListType)type, resolver, true);
        } else {
            Callable callable = new CallableDelegate((context, arguments) -> {
                AtomicValue value = (AtomicValue) arguments[0].head();
                if (value == null) {
                    return EmptySequence.getInstance();
                }
                return UnionConstructorFunction.cast(value, (UnionType) type, resolver, context.getConfiguration().getConversionRules());
            });
            SequenceType returnType = ((UnionType) type).getResultTypeOfCast();
            return new CallableFunction(1, callable,
                                        new SpecificFunctionType(new SequenceType[]{SequenceType.OPTIONAL_ATOMIC}, returnType));
        }
    }

    @Override
    public boolean isAvailable(SymbolicName.F functionName, int languageLevel) {
        if (functionName.getArity() != 1) {
            return false;
        }
        final SchemaType type = config.getSchemaType(functionName.getComponentName());
        if (type == null || type.isComplexType()) {
            return false;
        }
        if (type.isAtomicType() && ((AtomicType) type).isAbstract()) {
            return false;
        }
        return type != AnySimpleType.getInstance();
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
            SchemaType st = config.getSchemaType(new StructuredQName("", uri, localName));
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

}
