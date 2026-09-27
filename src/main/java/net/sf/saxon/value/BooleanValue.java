////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.expr.sort.XPathComparable;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.str.StringConstants;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.Err;
import net.sf.saxon.type.*;

/**
 * A boolean XPath value
 */

public final class BooleanValue extends AtomicValue implements XPathComparable, AtomicMatchKey {
    private final boolean value;

    /**
     * The boolean value TRUE
     */
    public static final BooleanValue TRUE = new BooleanValue(true);
    /**
     * The boolean value FALSE
     */
    public static final BooleanValue FALSE = new BooleanValue(false);

    /**
     * Private Constructor: create a boolean value. Normally, only two instances of this class are
     * ever created, one to represent true and one to represent false. Exceptionally, however,
     * other instances may be created if there is a user-defined subtype of xs:boolean in a schema.
     *
     * @param value the initial value, true or false
     */

    private BooleanValue(boolean value) {
        super(BuiltInAtomicType.BOOLEAN);
        this.value = value;
    }

    /**
     * Factory method: get a BooleanValue
     *
     * @param value true or false, to determine which boolean value is
     *              required
     * @return the BooleanValue requested
     */

    public static BooleanValue get(boolean value) {
        return value ? TRUE : FALSE;
    }

    /**
     * Create a new Boolean value with a user-supplied type label.
     * It is the caller's responsibility to ensure that the value is valid for the subtype
     *
     * @param value     the boolean value
     * @param typeLabel the type label, xs:boolean or a subtype
     */

    public BooleanValue(boolean value, AtomicMetadata typeLabel) {
        super(typeLabel);
        this.value = value;
    }

    /**
     * Create a copy of this atomic value (usually so that the type label can be changed).
     * The type label of the copy will be reset to the primitive type.
     *
     * @param metadata the atomic type label to be added to the copied value
     */

    @Override
    public AtomicValue withMetadata(AtomicMetadata metadata) {
        if (metadata == BuiltInAtomicType.BOOLEAN) {
            return BooleanValue.get(getBooleanValue());
        }
        return new BooleanValue(value, metadata);
    }

    /**
     * Convert a string to a boolean value, using the XML Schema rules (including
     * whitespace trimming)
     *
     * @param s the input string
     * @return the relevant BooleanValue if validation succeeds; or a ValidationFailure if not.
     */

    public static ConversionResult fromString(UnicodeString s) {
        // implementation designed to avoid creating new objects or computing hash codes
        long start = Whitespace.trimmedStart(s);
        long end = Whitespace.trimmedEnd(s);
        if (start >= 0) { // start == -1 means empty string or all whitespace
            long len = end - start;
            if (len == 1) {
                int first = s.codePointAt(start);
                if (first == '0') {
                    return FALSE;
                } else if (first == '1') {
                    return TRUE;
                }
            } else if (len == 4) {
                if (s.codePointAt(start++) == 't'
                        && s.codePointAt(start++) == 'r'
                        && s.codePointAt(start++) == 'u'
                        && s.codePointAt(start) == 'e') {
                    return TRUE;
                }
            } else if (len == 5) {
                if (s.codePointAt(start++) == 'f'
                        && s.codePointAt(start++) == 'a'
                        && s.codePointAt(start++) == 'l'
                        && s.codePointAt(start++) == 's'
                        && s.codePointAt(start) == 'e') {
                    return FALSE;
                }
            }
        }
        ValidationFailure err = new ValidationFailure(
                "The string " + Err.wrap(s, Err.VALUE) + " cannot be cast to a boolean");
        err.setErrorCode("FORG0001");
        return err;
    }


    /**
     * Get the value
     *
     * @return true or false, the actual boolean value of this BooleanValue
     */

    public boolean getBooleanValue() {
        return value;
    }

    /**
     * Get the effective boolean value of this expression
     *
     * @return the boolean value
     */
    @Override
    public boolean effectiveBooleanValue() {
        return value;
    }

    /**
     * Determine the primitive type of the value. This delivers the same answer as
     * getItemType().getPrimitiveItemType(). The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration,
     * and xs:untypedAtomic. For external objects, the result is AnyAtomicType.
     */

    @Override
    public BuiltInAtomicType getPrimitiveType() {
        return BuiltInAtomicType.BOOLEAN;
    }

    /**
     * Convert to string
     *
     * @return "true" or "false"
     */

    @Override
    public UnicodeString getPrimitiveStringValue() {
        return value ? StringConstants.TRUE : StringConstants.FALSE;
    }
    
    /**
     * Get a Comparable value that implements the XPath ordering comparison semantics for this value.
     * Returns null if the value is not comparable according to XPath rules. The default implementation
     * returns null. This is overridden for types that allow ordered comparisons in XPath: numeric, boolean,
     * string, date, time, dateTime, yearMonthDuration, dayTimeDuration, and anyURI.
     *
     * @param collator         the collation to be used when comparing strings
     * @param implicitTimezone the XPath dynamic evaluation context, used in cases where the comparison is context
     *                         sensitive
     * @param specVersion
     * @return an Object whose equals() and hashCode() methods implement the XPath comparison semantics
     * with respect to this atomic value. If ordered is specified, the result will either be null if
     * no ordering is defined, or will be a Comparable
     */

    @Override
    public AtomicMatchKey getXPathMatchKey(StringCollator collator, int implicitTimezone, int specVersion) {
        return this;
    }

    @Override
    public XPathComparable getXPathComparable(StringCollator collator, int implicitTimezone, int specVersion) {
        return this;
    }

    /**
     * Compare the value to another boolean value
     *
     * @param other The other boolean value
     * @return -1 if this one is the lower, 0 if they are equal, +1 if this
     *         one is the higher. False is considered to be less than true.
     * @throws ClassCastException if the other value is not a BooleanValue
     *                            (the parameter is declared as Object to satisfy the Comparable
     *                            interface)
     */

    @Override
    public int compareTo(XPathComparable other) {
        if (other instanceof BooleanValue) {
            if (value == ((BooleanValue)other).value) {
                return 0;
            }
            if (value) {
                return +1;
            }
            return -1;
        } else {
            throw new ClassCastException("Cannot compare xs:boolean to " + other);
        }
    }

    /**
     * Determine whether two boolean values are equal
     *
     * @param other the value to be compared to this value
     * @return true if the other value is a boolean value and is equal to this
     *         value
     * @throws ClassCastException if other value is not xs:boolean or derived therefrom
     */
    public boolean equals(Object other) {
        return other instanceof BooleanValue && value == ((BooleanValue) other).value;
    }

    /**
     * Get a hash code for comparing two BooleanValues
     *
     * @return the hash code
     */
    public int hashCode() {
        return value ? 0x1f2f3f4f : 0x1e2e3e4e;
    }

    /**
     * Diagnostic display of this value as a string
     *
     * @return a string representation of this value: "true()" or "false()"
     */
    @Override
    public String show() {
        return this.getUnicodeStringValue() + "()";
    }
}

