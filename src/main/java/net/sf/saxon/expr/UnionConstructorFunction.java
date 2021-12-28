////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.functions.AbstractFunction;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AnyURIValue;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

/**
 * Function to perform a cast to a union type
 */
public class UnionConstructorFunction extends AbstractFunction {

    protected UnionType targetType;
    protected NamespaceResolver resolver;
    protected boolean allowEmpty;

    /**
     * Create a cast expression to a union type
     * @param targetType the union type that is the result of the cast
     * @param allowEmpty true if an empty sequence may be supplied as input, converting to an empty sequence on output
     */

    public UnionConstructorFunction(UnionType targetType, NamespaceResolver resolver, boolean allowEmpty) {
        this.targetType = targetType;
        this.resolver = resolver;
        this.allowEmpty = allowEmpty;
    }

    /**
     * Get the usage (in terms of streamability analysis) of the single operand
     * @return the operand usage
     */

    protected OperandRole getOperandRole() {
        return OperandRole.SINGLE_ATOMIC;
    }

    /**
     * Ask whether the value of the operand is allowed to be empty
     * @return true if an empty sequence may be supplied as input, converting to an empty sequence on output
     */

    public boolean isAllowEmpty() {
        return allowEmpty;
    }

    /**
     * Get the union type that is the target of this cast operation
     * @return the target type of the cast
     */

    public UnionType getTargetType() {
        return targetType;
    }

    /**
     * Get the namespace resolver that will be used to resolve any namespace-sensitive values (such as QNames) when casting
     * @return the namespace resolver, or null if there is none.
     */

    public NamespaceResolver getNamespaceResolver() {
        return resolver;
    }

    /**
     * Get the item type of the function item
     *
     * @return the function item's type
     */
    @Override
    public FunctionItemType getFunctionItemType() {
        SequenceType resultType = targetType.getResultTypeOfCast();

        SequenceType argType = allowEmpty ? SequenceType.OPTIONAL_ATOMIC : SequenceType.SINGLE_ATOMIC;

        return new SpecificFunctionType(
            new SequenceType[]{argType}, resultType);
    }

    /**
     * Get the name of the function, or null if it is anonymous
     *
     * @return the function name, or null for an anonymous inline function
     */
    @Override
    public StructuredQName getFunctionName() {
        return targetType.getTypeName();
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
        return getFunctionName().getDisplayName();
    }

    /**
     * Get the arity of the function
     *
     * @return the number of arguments in the function signature
     */
    @Override
    public int getArity() {
        return 1;
    }


    /**
     * Method to perform the cast of an atomic value to a union type
     *
     * @param value      the input value to be converted. Must not be null.
     * @param context    the evaluation context
     * @return the result of the conversion (may be a sequence if the union includes list types in its membership)
     * @throws net.sf.saxon.trans.XPathException if the conversion fails
     */

    public AtomicSequence cast(AtomicValue value, XPathContext context)
            throws XPathException {
        ConversionRules rules = context.getConfiguration().getConversionRules();
        if (value == null) {
            throw new NullPointerException();
        }

        // 1. If the value is a string or untypedAtomic, try casting to each of the member types

        if (value instanceof StringValue && !(value instanceof AnyURIValue)) {
            try {
                return targetType.getTypedValue(value.getStringValueCS(), resolver, rules);
            } catch (ValidationException e) {
                e.setErrorCode("FORG0001");
                throw e;
            }
        }

        // 2. If the value is an instance of a type in the transitive membership of the union, return it unchanged

        AtomicType label = value.getItemType();
        Iterable<? extends PlainType> memberTypes = ((UnionType)targetType).getPlainMemberTypes();

        // 2a. Is the type annotation itself a member type of the union, and of the union type itself?
        if (((UnionType)targetType).isPlainType()) {
            for (PlainType member : memberTypes) {
                if (label.equals(member)) {
                    return value;
                }
            }
            // 2b. Failing that, is some supertype of the type annotation a member type of the union?
            for (PlainType member : memberTypes) {
                AtomicType t = label;
                while (t != null) {
                    if (t.equals(member)) {
                        return value;
                    } else {
                        t = t.getBaseType() instanceof AtomicType ? (AtomicType) t.getBaseType() : null;
                    }
                }
            }
        }

        // 3. if the value can be cast to any of the member types, return the result of that cast

        for (PlainType type : memberTypes) {
            if (type instanceof AtomicType) {
                Converter c = rules.getConverter(value.getItemType(), (AtomicType) type);
                if (c != null) {
                    ConversionResult result = c.convert(value);
                    if (result instanceof AtomicValue) {
                        // 3b. if the union type has constraining facets then the value must satisfy these
                        if (!targetType.isPlainType()) {
                            ValidationFailure vf = targetType.checkAgainstFacets((AtomicValue)result, rules);
                            if (vf == null) {
                                return (AtomicValue) result;
                            }
                        } else {
                            return (AtomicValue) result;
                        }
                    }
                }
            }
        }

        throw new XPathException("Cannot convert the supplied value to " + targetType.getDescription(), "FORG0001");
    }

    /**
     * Invoke the function
     *
     * @param context the XPath dynamic evaluation context
     * @param args    the actual arguments to be supplied
     * @return the result of invoking the function
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs within the function
     */
    @Override
    public AtomicSequence call(XPathContext context, Sequence[] args) throws XPathException {
        AtomicValue val = (AtomicValue) args[0].head();
        if (val == null) {
            if (allowEmpty) {
                return EmptyAtomicSequence.getInstance();
            } else {
                XPathException e = new XPathException("Cast expression does not allow an empty sequence to be supplied", "XPTY0004");
                e.setIsTypeError(true);
                throw e;
            }
        }
        return cast(val, context);
    }

    /**
     * Static method to perform the cast of an atomic value to a union type
     *
     * @param value      the input value to be converted. Must not be null.
     * @param targetType the union type to which the value is to be converted
     * @param nsResolver the namespace context, required if the type is namespace-sensitive
     * @param rules      the conversion rules
     * @return the result of the conversion (may be a sequence if the union includes list types in its membership)
     * @throws XPathException if the conversion fails
     */

    public static AtomicSequence cast(AtomicValue value, UnionType targetType,
                                      NamespaceResolver nsResolver, ConversionRules rules)
            throws XPathException {
        //ConversionRules rules = context.getConfiguration().getConversionRules();
        if (value == null) {
            throw new NullPointerException();
        }

        // 1. If the value is a string or untypedAtomic, try casting to each of the member types

        if (value instanceof StringValue && !(value instanceof AnyURIValue)) {
            try {
                return targetType.getTypedValue(value.getStringValueCS(), nsResolver, rules);
            } catch (ValidationException e) {
                e.setErrorCode("FORG0001");
                throw e;
            }
        }

        // 2. If the value is an instance of a type in the transitive membership of the union, return it unchanged

        AtomicType label = value.getItemType();
        Iterable<? extends PlainType> memberTypes = targetType.getPlainMemberTypes();

        // 2a. Is the type annotation itself a member type of the union?
        for (PlainType member : memberTypes) {
            if (label.equals(member)) {
                return value;
            }
        }
        // 2b. Failing that, is some supertype of the type annotation a member type of the union?
        for (PlainType member : memberTypes) {
            AtomicType t = label;
            while (t != null) {
                if (t.equals(member)) {
                    return value;
                } else {
                    t = t.getBaseType() instanceof AtomicType ? (AtomicType) t.getBaseType() : null;
                }
            }
        }

        // 3. if the value can be cast to any of the member types, return the result of that cast

        for (PlainType type : memberTypes) {
            if (type instanceof AtomicType) {
                Converter c = rules.getConverter(value.getItemType(), (AtomicType) type);
                if (c != null) {
                    ConversionResult result = c.convert(value);
                    if (result instanceof AtomicValue) {
                        return (AtomicValue) result;
                    }
                }
            }
        }

        throw new XPathException("Cannot convert the supplied value to " + targetType.getDescription(), "FORG0001");
    }


}

// Copyright (c) 2018-2020 Saxonica Limited


