////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.DescendingComparer;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import java.util.Properties;


/**
 * This class implements the min() and max() functions, with the collation argument already known.
 */

public abstract class Minimax extends CollatingFunctionFixed {


    private PlainType argumentType = BuiltInAtomicType.ANY_ATOMIC;
    private boolean ignoreNaN = false;


    /**
     * Method to be implemented in subclasses to indicate whether the function implements
     * fn:min() or fn:max()
     * @return true if this is the fn:max() function
     */

    public abstract boolean isMaxFunction();

    /**
     * Indicate whether NaN values should be ignored. For the external min() and max() function, a
     * NaN value in the input causes the result to be NaN. Internally, however, min() and max() are also
     * used in such a way that NaN values should be ignored. This is the case for internally-generated min() and max()
     * functions used to support general comparisons.
     *
     * @param ignore true if NaN values are to be ignored when computing the min or max.
     */

    public void setIgnoreNaN(boolean ignore) {
        ignoreNaN = ignore;
    }

    /**
     * Test whether NaN values are to be ignored
     *
     * @return true if NaN values are to be ignored. This is the case for internally-generated min() and max()
     *         functions used to support general comparisons
     */

    public boolean isIgnoreNaN() {
        return ignoreNaN;
    }


    public AtomicComparer getComparer() {
        return getPreAllocatedAtomicComparer();
    }

    public PlainType getArgumentType() {
        return argumentType;
    }

    /**
     * Static analysis: preallocate a comparer if possible
     */

    @Override
    public void supplyTypeInformation(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType, Expression[] arguments) {
        ItemType type = arguments[0].getItemType();
        argumentType = type.getAtomizedItemType();
        if (argumentType instanceof AtomicType) {
            if (argumentType == BuiltInAtomicType.UNTYPED_ATOMIC) {
                argumentType = BuiltInAtomicType.DOUBLE;
            }
            preAllocateComparer((AtomicType) argumentType, (AtomicType) argumentType, visitor.getStaticContext());
        }
    }

    /*@NotNull*/
    @Override
    public ItemType getResultItemType(Expression[] args) {
        TypeHierarchy th = getRetainedStaticContext().getConfiguration().getTypeHierarchy();
        ItemType base = Atomizer.getAtomizedItemType(args[0], false, th);
        if (base.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            base = BuiltInAtomicType.DOUBLE;
        }
        return base.getPrimitiveItemType();
    }
    
    /**
     * Determine the cardinality of the function.
     */

    @Override
    public int getCardinality(Expression[] arguments) {
        if (!Cardinality.allowsZero(arguments[0].getCardinality())) {
            return StaticProperty.EXACTLY_ONE;
        } else {
            return StaticProperty.ALLOWS_ZERO_OR_ONE;
        }
    }

    @Override
    public Expression makeOptimizedFunctionCall(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, Expression... arguments) throws XPathException {
        // test for a singleton: this often happens after (A<B) is rewritten as (min(A) lt max(B))
        int card = arguments[0].getCardinality();
        if (!Cardinality.allowsMany(card)) {
            ItemType it = arguments[0].getItemType().getPrimitiveItemType();
            if (it instanceof BuiltInAtomicType && ((BuiltInAtomicType)it).isOrdered(false)) {
                TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
                if (th.relationship(it, BuiltInAtomicType.UNTYPED_ATOMIC) != Affinity.DISJOINT) {
                    return UntypedSequenceConverter.makeUntypedSequenceConverter(
                            visitor.getConfiguration(), arguments[0], BuiltInAtomicType.DOUBLE).typeCheck(visitor, contextInfo);
                } else {
                    return arguments[0];
                }
            }
        }
        if (arguments[0] instanceof RangeExpression) {
            // typically the min/max is the start/end of the range. But we need to be careful about handling
            // an empty sequence (A to B where A > B)
            if (isMaxFunction()) {
                Expression start = ((RangeExpression) arguments[0]).getLhsExpression();
                Expression end = ((RangeExpression)arguments[0]).getRhsExpression();
                if (start instanceof Literal && end instanceof Literal) {
                    return end;
                }
                return new LastItemExpression(arguments[0]);
            } else {
                return FirstItemExpression.makeFirstItemExpression(arguments[0]);
            }
        }
        return null;
    }


    @Override
    public AtomicComparer getAtomicComparer(XPathContext context)  {
        AtomicComparer comparer = getPreAllocatedAtomicComparer();
        if (comparer != null) {
            return comparer;
        }
        PlainType type = argumentType.getPrimitiveItemType();
        if (type.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            type = BuiltInAtomicType.DOUBLE;
        }
        BuiltInAtomicType prim = (BuiltInAtomicType) type;
        return GenericAtomicComparer.makeAtomicComparer(prim, prim, getStringCollator(), context);
    }

    /**
     * Static method to evaluate the minimum or maximum of a sequence
     *
     * @param iter           Iterator over the input sequence
     * @param isMaxFunction  true for the max() function, false for min()
     * @param atomicComparer an AtomicComparer used to compare values
     * @param ignoreNaN      true if NaN values are to be ignored
     * @param context        dynamic evaluation context
     * @return the min or max value in the sequence, according to the rules of the fn:min() or fn:max() functions
     * @throws XPathException typically if non-comparable values are found in the sequence
     */
    /*@Nullable*/
    public static AtomicValue minimax(SequenceIterator iter, boolean isMaxFunction,
                                      AtomicComparer atomicComparer, boolean ignoreNaN, XPathContext context)
            throws XPathException {

        ConversionRules rules = context.getConfiguration().getConversionRules();
        StringToDouble converter = context.getConfiguration().getConversionRules().getStringToDoubleConverter();
        boolean foundDouble = false;
        boolean foundFloat = false;
        boolean foundNaN = false;
        boolean foundString = false;

        // For the max function, reverse the collator
        if (isMaxFunction) {
            atomicComparer = new DescendingComparer(atomicComparer);
        }
        atomicComparer = atomicComparer.provideContext(context);

        // Process the sequence, retaining the min (or max) so far. This will be an actual value found
        // in the sequence. At the same time, remember if a double and/or float has been encountered
        // anywhere in the sequence, and if so, convert the min/max to double/float at the end. This is
        // done to avoid problems if a decimal is converted first to a float and then to a double.

        // Get the first value in the sequence, ignoring any NaN values if we are ignoring NaN values
        AtomicValue min;
        AtomicValue prim;

        while (true) { // loop only repeats if first item is NaN
            min = (AtomicValue) iter.next();
            if (min == null) {
                return null;
            }
            prim = min;
            if (min instanceof UntypedAtomicValue) {
                try {
                    min = new DoubleValue(converter.stringToNumber(min.getStringValueCS()));
                    prim = min;
                    foundDouble = true;
                } catch (NumberFormatException e) {
                    XPathException de = new XPathException("Failure converting " + Err.wrap(min.getStringValueCS()) + " to a number");
                    de.setErrorCode("FORG0001");
                    de.setXPathContext(context);
                    throw de;
                }
            } else {
                if (prim instanceof DoubleValue) {
                    foundDouble = true;
                } else if (prim instanceof FloatValue) {
                    foundFloat = true;
                } else if (prim instanceof StringValue && !(prim instanceof AnyURIValue)) {
                    foundString = true;
                }
            }
            if (prim.isNaN()) {
                // if there's a NaN in the sequence, return NaN, unless ignoreNaN is set
                if (ignoreNaN) {
                    //continue;   // ignore the NaN and treat the next item as the first real one
                } else if (prim instanceof DoubleValue) {
                    return min; // return double NaN
                } else {
                    // we can't ignore a float NaN, because we might need to promote it to a double NaN
                    foundNaN = true;
                    min = FloatValue.NaN;
                    break;
                }
            } else {
                if (!prim.getPrimitiveType().isOrdered(false)) {
                    XPathException de = new XPathException("Type " + prim.getPrimitiveType() + " is not an ordered type");
                    de.setErrorCode("FORG0006");
                    de.setIsTypeError(true);
                    de.setXPathContext(context);
                    throw de;
                }
                break;          // process the rest of the sequence
            }
        }

        while (true) {
            AtomicValue test = (AtomicValue) iter.next();
            if (test == null) {
                break;
            }
            AtomicValue test2 = test;
            prim = test2;
            if (test instanceof UntypedAtomicValue) {
                try {
                    test2 = new DoubleValue(converter.stringToNumber(test.getStringValueCS()));
                    if (foundNaN) {
                        return DoubleValue.NaN;
                    }
                    prim = test2;
                    foundDouble = true;
                } catch (NumberFormatException e) {
                    XPathException de = new XPathException("Failure converting " + Err.wrap(test.getStringValueCS()) + " to a number");
                    de.setErrorCode("FORG0001");
                    de.setXPathContext(context);
                    throw de;
                }
            } else {
                if (prim instanceof DoubleValue) {
                    if (foundNaN) {
                        return DoubleValue.NaN;
                    }
                    foundDouble = true;
                } else if (prim instanceof FloatValue) {
                    foundFloat = true;
                } else if (prim instanceof StringValue && !(prim instanceof AnyURIValue)) {
                    foundString = true;
                }
            }
            if (prim.isNaN()) {
                // if there's a double NaN in the sequence, return NaN, unless ignoreNaN is set
                if (ignoreNaN) {
                    //continue;
                } else if (foundDouble) {
                    return DoubleValue.NaN;
                } else {
                    // can't return float NaN until we know whether to promote it
                    foundNaN = true;
                }
            } else {
                try {
                    if (atomicComparer.compareAtomicValues(prim, min) < 0) {
                        min = test2;
                    }
                } catch (ClassCastException err) {
                    if (min.getItemType() == test2.getItemType()) {
                        // internal error
                        throw err;
                    } else {
                        XPathException de = new XPathException("Cannot compare " + min.getItemType() + " with " + test2.getItemType());
                        de.setErrorCode("FORG0006");
                        de.setIsTypeError(true);
                        de.setXPathContext(context);
                        throw de;
                    }
                }
            }
        }
        if (foundNaN) {
            return FloatValue.NaN;
        }
        if (foundDouble) {
            if (!(min instanceof DoubleValue)) {
                min = Converter.convert(min, BuiltInAtomicType.DOUBLE, rules);
            }
        } else if (foundFloat) {
            if (!(min instanceof FloatValue)) {
                min = Converter.convert(min, BuiltInAtomicType.FLOAT, rules);
            }
        } else if (min instanceof AnyURIValue && foundString) {
            min = Converter.convert(min, BuiltInAtomicType.STRING, rules);
        }
        return min;

    }

    /**
     * Evaluate the function
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public ZeroOrOne call(XPathContext context, Sequence[] arguments) throws XPathException {
        return new ZeroOrOne(
                minimax(arguments[0].iterate(), isMaxFunction(), getAtomicComparer(context), ignoreNaN, context));
    }

    @Override
    public void exportAttributes(ExpressionPresenter out) {
        super.exportAttributes(out);
        if (ignoreNaN) {
            out.emitAttribute("flags", "i");
        }
    }

    @Override
    public void importAttributes(Properties attributes) throws XPathException {
        super.importAttributes(attributes);
        String flags = attributes.getProperty("flags");
        if (flags != null && flags.contains("i")) {
            setIgnoreNaN(true);
        }
    }

    @Override
    public String getStreamerName() {
        return "Minimax";
    }

    /**
     * Concrete subclass to define the fn:min() function
     */

    public static class Min extends Minimax {
        @Override
        public boolean isMaxFunction() {
            return false;
        }
    }

    /**
     * Concrete subclass to define the fn:max() function
     */

    public static class Max extends Minimax {
        @Override
        public boolean isMaxFunction() {
            return true;
        }
    }

}

