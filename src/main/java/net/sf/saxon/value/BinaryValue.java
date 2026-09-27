////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.expr.sort.BinaryMatchKey31;
import net.sf.saxon.expr.sort.BinaryMatchKey40;
import net.sf.saxon.expr.sort.XPathComparable;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.type.AtomicMetadata;

import java.util.Arrays;

/**
 * A value of type xs:hexBinary or xs:base64Binary
 */

public abstract class BinaryValue extends AtomicValue implements AtomicMatchKey, XPathComparable {

    protected final byte[] binaryValue;

    protected BinaryValue(AtomicMetadata type, byte[] binaryValue) {
        super(type);
        this.binaryValue = binaryValue;
    }

    protected static int byteArrayHashCode(/*@NotNull*/ byte[] value) {
        long h = 0;
        for (int i = 0; i < Math.min(value.length, 64); i++) {
            h = (h << 1) ^ value[i];
        }
        return (int) ((h >> 32) ^ h);
    }


    /**
     * Get the binary value
     *
     * @return the binary value, as a byte array
     */

    public byte[] getBinaryValue() {
        return binaryValue;
    }

    /**
     * Get the number of octets in the value
     *
     * @return the number of octets (bytes) in the value
     */

    public int getLengthInOctets() {
        return binaryValue.length;
    }

    /**
     * Test whether the value starts with a given octet sequence
     */

    public boolean startsWith(byte[] bytes) {
        return binaryValue.length >= bytes.length
                && Arrays.equals(binaryValue, 0, bytes.length, bytes, 0, bytes.length);
    }

    /*@Nullable*/
    @Override
    public AtomicMatchKey getXPathMatchKey(StringCollator collator, int implicitTimezone, int specVersion) {
        return asMapKey(specVersion);
    }

    @Override
    public AtomicMatchKey asMapKey(int specVersion) {
        return specVersion >= 40 ? new BinaryMatchKey40(this) : new BinaryMatchKey31(this);
    }

    /**
     * Get an object value that implements the XPath equality and ordering comparison semantics for this value.
     * If the ordered parameter is set to true, the result will be a Comparable and will support a compareTo()
     * method with the semantics of the XPath lt/gt operator, provided that the other operand is also obtained
     * using the getXPathComparable() method. In all cases the result will support equals() and hashCode() methods
     * that support the semantics of the XPath eq operator, again provided that the other operand is also obtained
     * using the getXPathComparable() method. A context argument is supplied for use in cases where the comparison
     * semantics are context-sensitive, for example where they depend on the implicit timezone or the default
     * collation.
     *
     * @param collator         collation to be used for comparing strings
     * @param implicitTimezone to be used for comparing dates/times with no timezone
     * @param specVersion      the semantics of binary comparison change in XPath 4.0
     * @return a key used for performing the comparison
     */

    @Override
    public XPathComparable getXPathComparable(StringCollator collator, int implicitTimezone, int specVersion) {
        return specVersion >= 40 ? this : new XPath31Comparable(this);
    }

    /**
     * Test if the two hexBinary or Base64Binary values are equal. This implements the 4.0 semantics (hexBinary
     * and base64Binary are comparable)
     */

    public boolean equals(/*@NotNull*/ Object other) {
        return other instanceof BinaryValue && Arrays.equals(binaryValue, ((BinaryValue) other).binaryValue);
    }

    public int hashCode() {
        return byteArrayHashCode(binaryValue);
    }

    @Override
    public int compareTo(XPathComparable o) {
        if (o instanceof BinaryValue) {
//            if (Version.MAP_SPEC_VERSION < 40 && this instanceof HexBinaryValue != o instanceof HexBinaryValue) {
//                throw new ClassCastException("Cannot compare base64Binary to hexBinary");
//            }
            byte[] other = ((BinaryValue) o).binaryValue;
            int len0 = binaryValue.length;
            int len1 = other.length;
            int shorter = java.lang.Math.min(len0, len1);
            for (int i = 0; i < shorter; i++) {
                int a = (int) binaryValue[i] & 0xff;
                int b = (int) other[i] & 0xff;
                if (a != b) {
                    return a < b ? -1 : +1;
                }
            }
            return Integer.signum(len0 - len1);
        } else {
            throw new ClassCastException("Cannot compare " + getItemType() + " to " + o.toString());
        }
    }

    private static class XPath31Comparable implements XPathComparable {
        private final BinaryValue binaryValue;
        public XPath31Comparable(BinaryValue binaryValue) {
            this.binaryValue = binaryValue;
        }

        @Override
        public int compareTo(XPathComparable o) {
            if (o instanceof XPath31Comparable other) {
                if (binaryValue.getPrimitiveType() != other.binaryValue.getPrimitiveType()) {
                    throw new ClassCastException("Comparison of hexBinary to base64Binary requires 4.0 to be enabled");
                }
                return binaryValue.compareTo(other.binaryValue);
            }
            throw new ClassCastException("Cannot compare " + binaryValue.getPrimitiveType() + " to " + o.getClass().getSimpleName());
        }
    }
}

