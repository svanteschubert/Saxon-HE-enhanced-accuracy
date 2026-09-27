////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.str.TwineBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicMetadata;
import net.sf.saxon.type.BuiltInAtomicType;

/**
 * A value of type xs:hexBinary
 */

public class HexBinaryValue extends BinaryValue {

    /**
     * Constructor: create a hexBinary value from a supplied string, in which
     * each octet is represented by a pair of values from 0-9, a-f, A-F
     *
     * @param in character representation of the hexBinary value
     * @throws XPathException if the input is invalid
     */

    public HexBinaryValue(UnicodeString in) throws XPathException {
        super(BuiltInAtomicType.HEX_BINARY, fromString(in));
    }

    private static byte[] fromString(UnicodeString in) throws XPathException {
        UnicodeString s = Whitespace.trim(in);
        int len32 = s.length32();
        if ((len32 & 1) != 0) {
            throw new XPathException("A hexBinary value must contain an even number of characters", "FORG0001");
        }
        byte[] binaryValue = new byte[len32 / 2];
        for (int i = 0; i < binaryValue.length; i++) {
            binaryValue[i] = (byte) ((fromHex(s.codePointAt(2 * i)) << 4) +
                                             fromHex(s.codePointAt(2 * i + 1)));
        }
        return binaryValue;
    }

    /**
     * Constructor: create a hexBinary value from a given array of bytes
     *
     * @param value the value as an array of bytes
     */

    public HexBinaryValue(byte[] value) {
        super(BuiltInAtomicType.HEX_BINARY, value);
    }

    /**
     * Constructor: create a hexBinary value from a given array of bytes and a specified type label
     *
     * @param value the value as an array of bytes
     * @param typeLabel the type label, which must be a subtype of HEX_BINARY
     */

    public HexBinaryValue(byte[] value, AtomicMetadata typeLabel) {
        super(typeLabel, value);
    }

    /**
     * Create a primitive copy of this atomic value (usually so that the type label can be changed).
     *
     * @param metadata the target type (a derived type from hexBinary)
     */

    /*@NotNull*/
    @Override
    public AtomicValue withMetadata(AtomicMetadata metadata) {
        return new HexBinaryValue(binaryValue, metadata);
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
     * Decode a single hex digit
     *
     * @param c the hex digit
     * @return the numeric value of the hex digit
     * @throws XPathException if it isn't a hex digit
     */

    private static int fromHex(int c) throws XPathException {
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
        TwineBuilder tb = TwineBuilder.make(binaryValue.length*2);
        for (byte aBinaryValue : binaryValue) {
            tb = tb.append(digits.charAt((aBinaryValue >> 4) & 0xf));
            tb = tb.append(digits.charAt(aBinaryValue & 0xf));
        }
        return tb.toUnicodeString();
    }


}

