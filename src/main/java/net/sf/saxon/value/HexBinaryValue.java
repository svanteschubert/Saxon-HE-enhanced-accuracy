////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.expr.sort.XPathComparable;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;

import java.util.Arrays;

/**
 * A value of type xs:hexBinary
 */

public class HexBinaryValue extends AtomicValue implements AtomicMatchKey, XPathComparable, ContextFreeAtomicValue {

    private final byte[] binaryValue;


    /**
     * Constructor: create a hexBinary value from a supplied string, in which
     * each octet is represented by a pair of values from 0-9, a-f, A-F
     *
     * @param in character representation of the hexBinary value
     * @throws XPathException if the input is invalid
     */

    public HexBinaryValue(UnicodeString in) throws XPathException {
        super(BuiltInAtomicType.HEX_BINARY);
        UnicodeString s = Whitespace.trim(in);
        int len32 = s.length32();
        if ((len32 & 1) != 0) {
            throw new XPathException("A hexBinary value must contain an even number of characters", "FORG0001");
        }
        binaryValue = new byte[len32 / 2];
        for (int i = 0; i < binaryValue.length; i++) {
            binaryValue[i] = (byte) ((fromHex(s.codePointAt(2 * i)) << 4) +
                    fromHex(s.codePointAt(2 * i + 1)));
        }
    }

    /**
     * Constructor: create a hexBinary value from a given array of bytes
     *
     * @param value the value as an array of bytes
     */

    public HexBinaryValue(byte[] value) {
        super(BuiltInAtomicType.HEX_BINARY);
        binaryValue = value;
    }

    /**
     * Constructor: create a hexBinary value from a given array of bytes and a specified type label
     *
     * @param value the value as an array of bytes
     * @param typeLabel the type label, which must be a subtype of HEX_BINARY
     */

    public HexBinaryValue(byte[] value, AtomicType typeLabel) {
        super(typeLabel);
        binaryValue = value;
    }

    /**
     * Create a primitive copy of this atomic value (usually so that the type label can be changed).
     *
     * @param typeLabel the target type (a derived type from hexBinary)
     */

    /*@NotNull*/
    @Override
    public AtomicValue copyAsSubType(AtomicType typeLabel) {
        return new HexBinaryValue(binaryValue, typeLabel);
    }

    /**
     * Determine the primitive type of the value. This delivers the same answer as
     * getItemType().getPrimitiveItemType(). The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration,
     * and xs:untypedAtomic. For external objects, the result is AnyAtomicType.
     */

    /*@NotNull*/
    @Override
    public BuiltInAtomicType getPrimitiveType() {
        return BuiltInAtomicType.HEX_BINARY;
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
     * Decode a single hex digit
     *
     * @param c the hex digit
     * @return the numeric value of the hex digit
     * @throws XPathException if it isn't a hex digit
     */

    private int fromHex(int c) throws XPathException {
        int d = c < 255 ? "0123456789ABCDEFabcdef".indexOf((char)c) : -1;
        if (d > 15) {
            d = d - 6;
        }
        if (d < 0) {
            throw new XPathException("Invalid hexadecimal digit '" + c + "'", "FORG0001");
        }
        return d;
    }

    /**
     * Convert to string
     *
     * @return the canonical representation.
     */

    /*@NotNull*/
    @Override
    public UnicodeString getPrimitiveStringValue() {
        String digits = "0123456789ABCDEF";
        UnicodeBuilder sb = new UnicodeBuilder(binaryValue.length*2);
        for (byte aBinaryValue : binaryValue) {
            sb.append(digits.charAt((aBinaryValue >> 4) & 0xf));
            sb.append(digits.charAt(aBinaryValue & 0xf));
        }
        return sb.toUnicodeString();
    }


    /**
     * Get the number of octets in the value
     *
     * @return the number of octets (bytes) in the value
     */

    public int getLengthInOctets() {
        return binaryValue.length;
    }

    /*@Nullable*/
    @Override
    public AtomicMatchKey getXPathMatchKey(StringCollator collator, int implicitTimezone) {
        return this;
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
     * @param collator collation to be used for comparing strings
     * @param implicitTimezone to be used for comparing dates/times with no timezone
     * @return a key used for performing the comparison
     */

    @Override
    public XPathComparable getXPathComparable(StringCollator collator, int implicitTimezone) {
        return this;
    }

    @Override
    public XPathComparable getXPathComparable() {
        return this;
    }

    /**
     * Test if the two hexBinary or Base64Binaryvalues are equal.
     */

    public boolean equals(/*@NotNull*/ Object other) {
        return other instanceof HexBinaryValue && Arrays.equals(binaryValue, ((HexBinaryValue) other).binaryValue);
    }

    public int hashCode() {
        return Base64BinaryValue.byteArrayHashCode(binaryValue);
    }

    @Override
    public int compareTo(XPathComparable o) {
        if (o instanceof Base64BinaryValue) {
            o = new HexBinaryValue(((Base64BinaryValue) o).getBinaryValue());
        }
        if (o instanceof HexBinaryValue) {
            byte[] other = ((HexBinaryValue)o).binaryValue;
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
            throw new ClassCastException("Cannot compare xs:hexBinary to " + o.getClass());
        }
    }
}

