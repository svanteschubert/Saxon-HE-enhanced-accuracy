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
 * A function item representing a constructor function for a list type
 */

public class ListConstructorFunction extends AbstractFunction {

    protected ListType targetType;
    protected NamespaceResolver nsResolver;
    protected boolean allowEmpty;
    protected SimpleType memberType;

    /**
     * Create the constructor function.
     *
     * @param targetType the type to which the function will convert its input
     * @param resolver   namespace resolver for use if the target type is namespace-sensitive
     */

    public ListConstructorFunction(ListType targetType, NamespaceResolver resolver, boolean allowEmpty) throws MissingComponentException {
        this.targetType = targetType;
        this.nsResolver = resolver;
        this.allowEmpty = allowEmpty;
        this.memberType = targetType.getItemType();
    }

    /**
     * Get the list type
     *
     * @return the last type to which we are casting
     */

    public ListType getTargetType() {
        return targetType;
    }

    /**
     * Get the list item type (member type)
     * @return the item type of the list
     */

    public SimpleType getMemberType() {
        return memberType;
    }

    /**
     * Ask whether an empty sequence is allowed
     *
     * @return true if passing an empty sequence is allowed (and returns empty)
     */

    public boolean isAllowEmpty() {
        return allowEmpty;
    }


    /**
     * Get the item type of the function item
     *
     * @return the function item's type
     */
    @Override
    public FunctionItemType getFunctionItemType() {
        AtomicType resultType = BuiltInAtomicType.ANY_ATOMIC;
        if (memberType.isAtomicType()) {
            resultType = (AtomicType) memberType;
        }

        SequenceType argType = allowEmpty ? SequenceType.OPTIONAL_ATOMIC : SequenceType.SINGLE_ATOMIC;

        return new SpecificFunctionType(
                new SequenceType[]{argType},
                SequenceType.makeSequenceType(resultType, StaticProperty.ALLOWS_ZERO_OR_MORE));
    }

    /**
     * Get the name of the function, or null if it is anonymous
     *
     * @return the function name, or null for an anonymous inline function
     */
    @Override
    public StructuredQName getFunctionName() {
        return targetType.getStructuredQName();
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
     * Invoke the function
     *
     * @param context the XPath dynamic evaluation context
     * @param args    the actual arguments to be supplied
     * @return the result of invoking the function
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs within the function
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
        if (!(val instanceof StringValue) || val instanceof AnyURIValue) {
            XPathException e = new XPathException("Only xs:string and xs:untypedAtomic can be cast to a list type", "XPTY0004");
            e.setIsTypeError(true);
            throw e;
        }

        ConversionRules rules = context.getConfiguration().getConversionRules();
        CharSequence cs = val.getStringValueCS();
        ValidationFailure failure = targetType.validateContent(cs, nsResolver, rules);
        if (failure != null) {
            throw failure.makeException();
        }
        return targetType.getTypedValue(cs, nsResolver, rules);
    }
}

