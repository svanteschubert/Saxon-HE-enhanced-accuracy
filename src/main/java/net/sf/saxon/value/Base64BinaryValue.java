////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;

import java.util.Arrays;

/**
 * A value of type xs:base64Binary
 * <p><i>Rewritten for Saxon 9.5 to avoid dependency on the open-source Netscape code, whose
 * license many users were unhappy with.</i></p>
 */

public class Base64BinaryValue extends AtomicValue implements AtomicMatchKey, Comparable {

    private byte[] binaryValue;


    /**
     * Constructor: create a base64Binary value from a supplied string in base64 encoding
     *
     * @param s the lexical representation of the base64 binary value. There is no requirement
     *          that whitespace should already be collapsed.
     * @throws net.sf.saxon.trans.XPathException
     *          if the supplied value is not in the lexical
     *          space of the xs:base64Binary data type
     */

    public Base64BinaryValue(/*@NotNull*/ CharSequence s) throws XPathException {
        binaryValue = decode(s);
        typeLabel = BuiltInAtomicType.BASE64_BINARY;
    }

    /**
     * Constructor: create a base64Binary value from a given array of bytes
     *
     * @param value array of bytes holding the octet sequence
     */

    public Base64BinaryValue(byte[] value) {
        binaryValue = value;
        typeLabel = BuiltInAtomicType.BASE64_BINARY;
    }

    /**
     * Create a copy of this atomic value (usually so that the type label can be changed).
     * The type label of the copy will be reset to the primitive type.
     *
     * @param typeLabel the type label to be attached to the value, a subtype of xs:base64Binary
     * @return the copied value
     */

    /*@NotNull*/
    @Override
    public AtomicValue copyAsSubType(AtomicType typeLabel) {
        Base64BinaryValue v = new Base64BinaryValue(binaryValue);
        v.typeLabel = typeLabel;
        return v;
    }

    /**
     * Get the binary value
     *
     * @return the octet sequence that is the typed value
     */

    public byte[] getBinaryValue() {
        return binaryValue;
    }

    /*@NotNull*/
    @Override
    public BuiltInAtomicType getPrimitiveType() {
        return BuiltInAtomicType.BASE64_BINARY;
    }

    /**
     * Convert to string
     *
     * @return the canonical representation.
     */

    /*@NotNull*/
    @Override
    public String getPrimitiveStringValue() {
        return encode(binaryValue).toString();
    }

    /**
     * Get the number of octets in the value
     *
     * @return the number of octets
     */

    public int getLengthInOctets() {
        return binaryValue.length;
    }

    /**
     * Support XML Schema comparison semantics
     */

    /*@NotNull*/
    @Override
    public Comparable getSchemaComparable() {
        return new Base64BinaryComparable();
    }

    /**
     * Private inner class to support XML Schema comparison semantics
     */

    private class Base64BinaryComparable implements Comparable {

        /*@NotNull*/
        public Base64BinaryValue getBase64BinaryValue() {
            return Base64BinaryValue.this;
        }

        @Override
        public int compareTo(/*@NotNull*/ Object o) {
            if (o instanceof Base64BinaryComparable &&
                    Arrays.equals(getBase64BinaryValue().binaryValue,
                            ((Base64BinaryComparable) o).getBase64BinaryValue().binaryValue)) {
                return 0;
            } else {
                return SequenceTool.INDETERMINATE_ORDERING;
            }
        }

        @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
        public boolean equals(/*@NotNull*/ Object o) {
            return compareTo(o) == 0;
        }

        public int hashCode() {
            return Base64BinaryValue.this.hashCode();
        }
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
     * @param ordered  true if an ordered comparison is required. In this case the result is null if the
     *                 type is unordered; in other cases the returned value will be a Comparable.
     * @param collator the collation (not used in this version of the method)
     * @param implicitTimezone  the XPath dynamic evaluation context, used in cases where the comparison is context
     */

    /*@Nullable*/
    @Override
    public AtomicMatchKey getXPathComparable(boolean ordered, StringCollator collator, int implicitTimezone) {
        return this;
    }

    /**
     * Test if the two base64Binary values are equal.
     */

    public boolean equals(/*@NotNull*/ Object other) {
        return other instanceof Base64BinaryValue
                && Arrays.equals(binaryValue, ((Base64BinaryValue) other).binaryValue);
    }

    public int hashCode() {
        return byteArrayHashCode(binaryValue);
    }

    protected static int byteArrayHashCode(/*@NotNull*/ byte[] value) {
        long h = 0;
        for (int i = 0; i < Math.min(value.length, 64); i++) {
            h = (h << 1) ^ value[i];
        }
        return (int) ((h >> 32) ^ h);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Code for converting to/from base64 representation
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private final static String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    private static int[] encoding = new int[64];
    private static int[] decoding = new int[128];

    static {
        Arrays.fill(decoding, -1);
        for (int i = 0; i < alphabet.length(); i++) {
            char c = alphabet.charAt(i);
            encoding[i] = c;
            decoding[c] = i;
        }
    }

    /**
     * Encode a byte sequence into base64 representation
     *
     * @param value the byte sequence
     * @return the base64 representation
     */

    public static CharSequence encode(byte[] value) {
        FastStringBuffer buff = new FastStringBuffer(value.length);
        int whole = value.length - value.length % 3;
        // process bytes 3 at a time: 3 bytes => 4 characters
        for (int i = 0; i < whole; i += 3) {
            // 3 bytes = 24 bits = 4 characters
            int val = ((((int) value[i]) & 0xff) << 16) + ((((int) value[i + 1]) & 0xff) << 8) + ((((int) value[i + 2]) & 0xff));
            buff.cat((char) encoding[(val >> 18) & 0x3f]);
            buff.cat((char) encoding[(val >> 12) & 0x3f]);
            buff.cat((char) encoding[(val >> 6) & 0x3f]);
            buff.cat((char) encoding[val & 0x3f]);
        }
        int remainder = (value.length % 3);
        switch (remainder) {
            case 0:
            default:
                // no action
                break;
            case 1: {
                // pad the final 8 bits to 12 (2 groups of 6)
                int val = ((((int) value[whole]) & 0xff) << 4);
                buff.cat((char) encoding[(val >> 6) & 0x3f]);
                buff.cat((char) encoding[val & 0x3f]);
                buff.append("==");
                break;
            }
            case 2: {
                // pad the final 16 bits to 18 (3 groups of 6)
                int val = ((((int) value[whole]) & 0xff) << 10) + ((((int) value[whole + 1]) & 0xff) << 2);
                buff.cat((char) encoding[(val >> 12) & 0x3f]);
                buff.cat((char) encoding[(val >> 6) & 0x3f]);
                buff.cat((char) encoding[val & 0x3f]);
                buff.append("=");
                break;
            }
        }
        return buff.condense();
    }

    /**
     * Decode a character string in base64 notation to yield the encoded octets
     *
     * @param in the lexical representation
     * @return the array of octets represented
     * @throws XPathException if the format is invalid (as required by XSD, this method
     *                        does draconian error handling, unlike many other base64 decoders which are liberal
     *                        in what they accept)
     */

    public static byte[] decode(CharSequence in) throws XPathException {
        char[] unit = new char[4];
        byte[] result = new byte[in.length()];
        int bytesUsed = 0;
        int i = 0;
        int u = 0;
        int pad = 0;
        int chars = 0;
        char last = 0;

        // process characters 4 at a time: 4 characters => 3 bytes
        while (i < in.length()) {
            char c = in.charAt(i++);
            if (!Whitespace.isWhite(c)) {
                chars++;
                if (c == '=') {
                    // all following chars must be '=' or whitespace
                    pad = 1;
                    for (int k = i; k < in.length(); k++) {
                        char ch = in.charAt(k);
                        if (ch == '=') {
                            pad++;
                            chars++;
                        } else if (Whitespace.isWhite(ch)) {
                            // no action
                        } else {
                            throw new XPathException("Base64 padding character '=' is followed by non-padding characters", "FORG0001");
                        }
                    }
                    if (pad == 1 && "AEIMQUYcgkosw048".indexOf(last) < 0) {
                        throw new XPathException("In base64, if the value ends with a single '=' character, then the preceding character must be" +
                                " one of [AEIMQUYcgkosw048]", "FORG0001");
                    } else if (pad == 2 && "AQgw".indexOf(last) < 0) {
                        throw new XPathException("In base64, if the value ends with '==', then the preceding character must be" +
                                " one of [AQgw]", "FORG0001");
                    }
                    // number of padding characters must be the number required
                    if (pad > 2) {
                        throw new XPathException("Found " + pad + " '=' characters at end of base64 value; max is 2", "FORG0001");
                    }
                    if (pad != ((4 - u) % 4)) {
                        throw new XPathException("Required " + ((4 - u) % 4) + " '=' characters at end of base64 value; found " + pad, "FORG0001");
                    }
                    // append 0 sextets corresponding to number of padding characters
                    for (int p = 0; p < pad; p++) {
                        unit[u++] = 'A';
                    }
                    i = in.length();
                } else {
                    last = c;
                    unit[u++] = c;
                }
                if (u == 4) {
                    int t = (decodeChar(unit[0]) << 18) +
                            (decodeChar(unit[1]) << 12) +
                            (decodeChar(unit[2]) << 6) +
                            (decodeChar(unit[3]));
                    if (bytesUsed + 3 > result.length) {
                        byte[] r2 = new byte[bytesUsed * 2];
                        System.arraycopy(result, 0, r2, 0, bytesUsed);
                        result = r2;
                    }
                    result[bytesUsed++] = (byte) ((t >> 16) & 0xff);
                    result[bytesUsed++] = (byte) ((t >> 8) & 0xff);
                    result[bytesUsed++] = (byte) (t & 0xff);
                    u = 0;
                }
            }
            if (i >= in.length()) {
                bytesUsed -= pad;
                break;
            }
        }
        if (chars % 4 != 0) {
            throw new XPathException("Length of base64 value must be a multiple of four", "FORG0001");
        }
        byte[] r3 = new byte[bytesUsed];
        System.arraycopy(result, 0, r3, 0, bytesUsed);
        return r3;

    }

    private static int decodeChar(char c) throws XPathException {
        int d = c < 128 ? decoding[c] : -1;
        if (d == -1) {
            if (UTF16CharacterSet.isSurrogate(c)) {
                throw new XPathException("Invalid character (surrogate pair) in base64 value", "FORG0001");
            } else {
                throw new XPathException("Invalid character '" + c + "' in base64 value", "FORG0001");
            }
        }
        return d;
    }

    @Override
    public int compareTo(Object o) {
        byte[] other = ((Base64BinaryValue)o).binaryValue;
        int len0 = binaryValue.length;
        int len1 = other.length;
        int shorter = java.lang.Math.min(len0, len1);
        for (int i=0; i<shorter; i++) {
            int a = (int)binaryValue[i] & 0xff;
            int b = (int)other[i] & 0xff;
            if (a != b) {
                return a < b ? -1 : +1;
            }
        }
        return Integer.signum(len0 - len1);
    }
}

