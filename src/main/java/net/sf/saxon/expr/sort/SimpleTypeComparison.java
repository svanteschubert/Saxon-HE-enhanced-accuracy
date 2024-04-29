////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.value.*;

import java.util.Arrays;

/**
 * This class implements equality and ordering comparisons between values of simple types:
 * that is, atomic values, and sequences of atomic values, following the XSD-defined rules
 * for equality and ordering comparisons. These are not always the same as the XPath-defined
 * rules. In particular, under XSD rules values are generally only comparable with others
 * of the same primitive type; a double and a float, for example, never compare equal.
 *
 * <p>For ordered data types, the compare() functions follow the usual convention: the return
 * value is -1, 0, or +1 according as the first value is less than, equal to, or greater
 * than the second. For non-ordered data types, if the values are not equal then the functions
 * return {@link SequenceTool#INDETERMINATE_ORDERING}.</p>
 *
 * <p>The class can be used directly to perform comparison between atomic values and sequences.
 * It can also be used to parameterise a {@code CustomSet} or {@code CustomMap}, to provide
 * a set or map that uses explicit equals() logic rather than relying on the {@code equals()}
 * and {@code hashCode()} methods of the supplied objects - thus avoiding the need to create
 * wrapper objects purely to redefine equality semantics.</p>
 *
 * <p>The class is a singleton that cannot be externally instantiated.</p>
 */

public class SimpleTypeComparison implements EqualityMatcher<AtomicSequence> {

    private static final SimpleTypeComparison INSTANCE = new SimpleTypeComparison();

    /**
     * Get the singleton instance of the class
     * @return  the singleton instance
     */
    public static SimpleTypeComparison getInstance() {
        return INSTANCE;
    }

    public int compareSequences(AtomicSequence a1, AtomicSequence a2) {
        if (a1.getLength() == 1 && a2.getLength() == 1) {
            // Shortcut for the most common case
            return compareItems(a1.itemAt(0), a2.itemAt(0));
        } else {
            AtomicIterator iter1 = a1.iterate();
            AtomicIterator iter2 = a2.iterate();
            while (true) {
                AtomicValue item1 = iter1.next();
                AtomicValue item2 = iter2.next();
                if (item1 == null && item2 == null) {
                    return 0;
                }
                if (item1 == null) {
                    return -1;
                } else if (item2 == null) {
                    return +1;
                }
                int c = compareItems(item1, item2);
                if (c != 0) {
                    return c;
                }
            }
        }
    }

    public int compareItems(AtomicValue a1, AtomicValue a2) {
        char c1 = a1.getItemType().getBasicAlphaCode().charAt(1);
        if (c1 == 'Z') {
            c1 = 'S'; // treat untypedAtomic as string
        }
        char c2 = a2.getItemType().getBasicAlphaCode().charAt(1);
        if (c2 == 'Z') {
            c2 = 'S'; // treat untypedAtomic as string
        }
        if (c1 != c2) {
            return SequenceTool.INDETERMINATE_ORDERING;
        }
        switch (c1) {
            case 'S': // string
            case 'Z': // UntypedAtomic
                return CodepointCollator.getInstance().compareStrings(a1.getUnicodeStringValue(), a2.getUnicodeStringValue());
            case 'B': // boolean
                return a1.equals(a2) ? 0 : SequenceTool.INDETERMINATE_ORDERING;
            case 'R': // duration
                DurationValue dur1 = (DurationValue) a1;
                DurationValue dur2 = (DurationValue) a2;
                return dur1.getSchemaComparable().compareTo(dur2.getSchemaComparable());
            case 'M': // dateTime
                DateTimeValue dt1 = (DateTimeValue) a1;
                DateTimeValue dt2 = (DateTimeValue) a2;
                return dt1.getSchemaComparable().compareTo(dt2.getSchemaComparable());
            case 'A': // date
            case 'G': // gYear
            case 'H': // gYearMonth
            case 'I': // gMonth
            case 'J': // gMonthDay
            case 'K': // gDay
                GDateValue gd1 = (GDateValue) a1;
                GDateValue gd2 = (GDateValue) a2;
                return gd1.getSchemaComparable().compareTo(gd2.getSchemaComparable());
            case 'T': // time
                TimeValue t1 = (TimeValue) a1;
                TimeValue t2 = (TimeValue) a2;
                return t1.getSchemaComparable().compareTo(t2.getSchemaComparable());
            case 'X': // hexBinary
                HexBinaryValue h1 = (HexBinaryValue) a1;
                HexBinaryValue h2 = (HexBinaryValue) a2;
                return Arrays.equals(h1.getBinaryValue(), h2.getBinaryValue()) ? 0 : SequenceTool.INDETERMINATE_ORDERING;
            case '2': // base64Binary
                Base64BinaryValue x1 = (Base64BinaryValue) a1;
                Base64BinaryValue x2 = (Base64BinaryValue) a2;
                return Arrays.equals(x1.getBinaryValue(), x2.getBinaryValue()) ? 0 : SequenceTool.INDETERMINATE_ORDERING;
            case 'U': // anyURI
                return a1.equals(a2) ? 0 : SequenceTool.INDETERMINATE_ORDERING;
            case 'Q': // QName
                return a1.equals(a2) ? 0 : SequenceTool.INDETERMINATE_ORDERING;
            case 'N': // NOTATION
                return a1.equals(a2) ? 0 : SequenceTool.INDETERMINATE_ORDERING;
            case 'D': // decimal
                if (a1 instanceof Int64Value && a2 instanceof Int64Value) {
                    return Long.compare(((Int64Value) a1).longValue(), ((Int64Value) a2).longValue());
                } else {
                    return ((DecimalValue) a1).getDecimalValue().compareTo(((DecimalValue) a2).getDecimalValue());
                }
            case 'F': // float
                //Note: Float.compare() gives the wrong answer with NaN and negative zero
                float f1 = ((FloatValue) a1).getFloatValue();
                float f2 = ((FloatValue) a2).getFloatValue();
                if (f1 == f2) {
                    return 0;
                } else {
                    return f1 < f2 ? -1 : 1;
                }
            case 'O': // double
                //Note: Double.compare() gives the wrong answer with NaN and negative zero
                double d1 = ((DoubleValue) a1).getDoubleValue();
                double d2 = ((DoubleValue) a2).getDoubleValue();
                if (d1 == d2) {
                    return 0;
                } else {
                    return d1 < d2 ? -1 : 1;
                }
            default:
                throw new IllegalStateException();
        }


    }

    @Override
    public boolean equal(AtomicSequence a, AtomicSequence b) {
        return compareSequences(a, b) == 0;
    }

    @Override
    public int hash(AtomicSequence a) {
        int h = 0x56825682;
        for (AtomicValue val : a) {
            char type = val.getItemType().getBasicAlphaCode().charAt(1);
            switch (type) {
                case 'S': // string
                case 'Z': // UntypedAtomic
                    h ^= val.getUnicodeStringValue().hashCode();
                    break;
                case 'B': // boolean
                    h ^= ((BooleanValue) val).hashCode();
                    break;
                case 'R': // duration
                    h ^= ((DurationValue) val).getSchemaComparable().hashCode();
                    break;
                case 'M': // dateTime
                    h ^= ((DateTimeValue) val).getSchemaComparable().hashCode();
                    break;
                case 'A': // date
                case 'G': // gYear
                case 'H': // gYearMonth
                case 'I': // gMonth
                case 'J': // gMonthDay
                case 'K': // gDay
                    h ^= ((GDateValue) val).getSchemaComparable().hashCode();
                    break;
                case 'T': // time
                    h ^= ((TimeValue) val).getSchemaComparable().hashCode();
                    break;
                case 'X': // hexBinary
                    h ^= Arrays.hashCode(((HexBinaryValue) val).getBinaryValue());
                    break;
                case '2': // base64Binary
                    h ^= Arrays.hashCode(((Base64BinaryValue) val).getBinaryValue());
                    break;
                case 'U': // anyURI
                    h ^= ((AnyURIValue) val).getUnicodeStringValue().hashCode();
                    break;
                case 'Q': // QName
                case 'N': // NOTATION
                    h ^= ((QualifiedNameValue) val).getStructuredQName().hashCode();
                    break;
                case 'D': // decimal
                case 'F': // float
                case 'O': // double
                    h ^= val.hashCode();
                    break;
                default:
                    throw new IllegalStateException("Alphacode " + type);
            }
            h <<= 1;
        }
        return h;
    }

    public boolean equalOrIdentical(AtomicSequence a1, AtomicSequence a2) {
        if (a1.getLength() == 1 && a2.getLength() == 1) {
            AtomicValue v1 = a1.head();
            AtomicValue v2 = a2.head();
            // Shortcut for the most common case
            return compareItems(v1, v2) == 0 || (v1.isNaN() && v2.isNaN());
        } else {
            AtomicIterator iter1 = a1.iterate();
            AtomicIterator iter2 = a2.iterate();
            while (true) {
                AtomicValue item1 = iter1.next();
                AtomicValue item2 = iter2.next();
                if (item1 == null && item2 == null) {
                    return true;
                }
                if (item1 == null || item2 == null) {
                    return false;
                }
                if (compareItems(item1, item2) == 0 || (item1.isNaN() && item2.isNaN())) {
                    continue;
                }
                return false;
            }
        }
    }
}

