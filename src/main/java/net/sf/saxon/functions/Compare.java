////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.expr.sort.XPathComparable;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.PrimitiveUType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.StringValue;

/**
 * This class implements the fn:compare() function, introduced in XPath 2.0 and heavily
 * extended in XPath 4.0
 */

public class Compare extends CollatingFunctionFixed {

    private static Int64Value compareStrings(StringValue s1, StringValue s2, AtomicComparer comparer) {
        if (s1 == null || s2 == null) {
            return null;
        }
        int result = comparer.compareAtomicValues(s1, s2);
        if (result < 0) {
            return Int64Value.MINUS_ONE;
        } else if (result > 0) {
            return Int64Value.PLUS_ONE;
        } else {
            return Int64Value.ZERO;
        }
    }

    private PrimitiveUType getTypeCategory(PrimitiveUType t) {
        return switch (t) {
            case STRING,
                 ANY_URI,
                 UNTYPED_ATOMIC ->
                    PrimitiveUType.STRING;
            case FLOAT,
                 DOUBLE,
                 DECIMAL ->
                    PrimitiveUType.DECIMAL;
            case HEX_BINARY,
                 BASE64_BINARY ->
                    PrimitiveUType.HEX_BINARY;
            default -> t;
        };
    }

    private void unorderedError(AtomicType t, int which) throws XPathException {
        throw new XPathException("fn:compare() - " +
                                         (which == 0 ? "first" : "second") +
                                         " argument is of type " + t.getDisplayName() +
                                         ", which has no defined ordering",
                                 "XPTY0004").asTypeError();

    }

    @Override
    public GroundedValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        int specVersion = getRetainedStaticContext().getPackageData().getHostLanguageVersion();
        AtomicValue arg0 = (AtomicValue) arguments[0].head();
        AtomicValue arg1 = (AtomicValue) arguments[1].head();
        if (arg0 == null || arg1 == null) {
            return EmptySequence.INSTANCE;
        }
        PrimitiveUType p0 = getTypeCategory(arg0.getPrimitiveType().getPrimitiveUType());
        PrimitiveUType p1 = getTypeCategory(arg1.getPrimitiveType().getPrimitiveUType());

        if (specVersion < 40 && (p0 != PrimitiveUType.STRING || p1 != PrimitiveUType.STRING)) {
            throw new XPathException("4.0 is not enabled, so fn:compare() can only be used to compare strings", "XPTY0004")
                    .asTypeError();
        }

//        if (p0 == -1) {
//            unorderedError(arg0.getItemType(), 0);
//        }
//        if (p1 == -1) {
//            unorderedError(arg1.getItemType(), 1);
//        }
        if (p0 != p1) {
            throw new XPathException("fn:compare(): cannot compare " +
                    arg0.getItemType() +
                    " to " +
                    arg1.getItemType(), "XPTY0004").asTypeError();
        }
        switch (p0) {
            case STRING:
                GenericAtomicComparer comparer =
                        new GenericAtomicComparer(getStringCollator(), specVersion, context);
                return compareStrings((StringValue)arg0, (StringValue)arg1, comparer);
            case DECIMAL:
                if (arg0.isNaN()) {
                    return arg1.isNaN() ? Int64Value.ZERO : Int64Value.MINUS_ONE;
                } else if (arg1.isNaN()) {
                    return Int64Value.PLUS_ONE;
                }
                int c = arg0.getXPathComparable(null, 0, specVersion).compareTo(
                        arg1.getXPathComparable(null, 0, specVersion));
                return Int64Value.makeIntegerValue(c);
            default:
                XPathComparable c0 = arg0.getXPathComparable(
                        CodepointCollator.getInstance(), context.getImplicitTimezone(), specVersion);
                XPathComparable c1 = arg1.getXPathComparable(
                        CodepointCollator.getInstance(), context.getImplicitTimezone(), specVersion);
                return Int64Value.makeIntegerValue(c0.compareTo(c1));
        }

    }

}

