////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.UnionType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.SequenceType;

/**
 * Function to test castability to a union type
 */
public class UnionCastableFunction extends UnionConstructorFunction {

    /**
     * Create a cast expression to a union type
     * @param targetType the union type that is the result of the cast
     * @param resolver namespace resolver in case the type is namespace-sensitive
     * @param allowEmpty true if an empty sequence may be supplied as input, converting to an empty sequence on output
     */

    public UnionCastableFunction(UnionType targetType, NamespaceResolver resolver, boolean allowEmpty) {
        super(targetType, resolver, allowEmpty);
    }

    /**
     * Get the item type of the function item
     *
     * @return the function item's type
     */
    @Override
    public FunctionItemType getFunctionItemType() {
        // The function accepts any sequence as input, so it can do its own atomization
        return new SpecificFunctionType(
            new SequenceType[]{SequenceType.ANY_SEQUENCE}, SequenceType.SINGLE_BOOLEAN);
    }

    /**
     * Get the name of the function, or null if it is anonymous
     *
     * @return the function name, or null for an anonymous function
     */
    @Override
    public StructuredQName getFunctionName() {
        return null;
    }

    /**
     * Get the effective boolean value of the expression
     *
     * @param context The context in which the expression is to be evaluated
     * @return the effective boolean value
     * @throws XPathException if an error occurs evaluating the operand
     */

    private boolean effectiveBooleanValue(SequenceIterator iter, XPathContext context) throws XPathException {
        // This method does its own atomization so that it can distinguish between atomization
        // failures and casting failures
        int count = 0;
        Item item;
        while ((item = iter.next()) != null) {
            if (item instanceof NodeInfo) {
                AtomicSequence atomizedValue = item.atomize();
                int length = SequenceTool.getLength(atomizedValue);
                count += length;
                if (count > 1) {
                    return false;
                }
                if (length != 0) {
                    AtomicValue av = atomizedValue.head();
                    if (!castable(av, context)) {
                        return false;
                    }
                }
            } else if (item instanceof AtomicValue) {
                AtomicValue av = (AtomicValue) item;
                count++;
                if (count > 1) {
                    return false;
                }
                if (!castable(av, context)) {
                    return false;
                }
            } else {
                throw new XPathException("Input to 'castable' operator cannot be atomized", "XPTY0004");
            }
        }
        return count != 0 || allowEmpty;
    }

    /**
     * Method to perform the castable check of an atomic value to a union type
     *
     * @param value      the input value to be converted. Must not be null.
     * @param context    the XPath dynamic evaluation context
     * @return the result of the conversion (may be a sequence if the union includes list types in its membership)
     */
    private boolean castable(AtomicValue value, XPathContext context) {
        try {
            cast(value, context);
            return true;
        } catch (XPathException err) {
            return false;
        }
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
    public BooleanValue call(XPathContext context, Sequence[] args) throws XPathException {
        boolean value = effectiveBooleanValue(args[0].iterate(), context);
        return BooleanValue.get(value);
    }


}

// Copyright (c) 2018-2020 Saxonica Limited


