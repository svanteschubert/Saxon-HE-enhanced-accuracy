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
import net.sf.saxon.str.*;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.transpile.CSharpInnerClass;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.type.AtomicMetadata;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.z.IntIterator;


/**
 * An atomic value of type xs:string. This class is also used for types derived from xs:string.
 * The StringValue class is also used for xs:untypedAtomic; a subclass is used for xs:anyURI values.
 *
 * <p>Internally the value is held as a wrapper around a {@link UnicodeString}, which allows
 * a variety of implementations</p>
 *
 * <p>The {@code equals} and {@code compareTo} methods support ordering in codepoint
 * collation sequence.</p>
 */

public class StringValue extends AtomicValue {


    protected final UnicodeString content;

    public static final StringValue EMPTY_STRING = new StringValue(EmptyUnicodeString.getInstance());
    public static final StringValue SINGLE_SPACE = new StringValue(StringConstants.SINGLE_SPACE);
    public static final StringValue TRUE = new StringValue(StringConstants.TRUE);
    public static final StringValue FALSE = new StringValue(StringConstants.FALSE);
    public static final StringValue CR = new StringValue("\r");
    public static final StringValue LF = new StringValue("\n");

    public static final StringValue ZERO_LENGTH_UNTYPED = StringValue.makeUntypedAtomic(EmptyUnicodeString.getInstance());


    /**
     * Protected constructor for use by subtypes
     */

    protected StringValue() {
        super(BuiltInAtomicType.STRING);
        content = EmptyUnicodeString.getInstance();
    }

    /**
     * Protected constructor for use by subtypes
     */

    protected StringValue(AtomicMetadata typeLabel) {
        super(typeLabel);
        content = EmptyUnicodeString.getInstance();
    }

    /**
     * Construct an instance that wraps a supplied {@code UnicodeString}, with the default
     * type xs:string
     * @param content the {@code UnicodeString} to wrap
     */

    public StringValue(UnicodeString content) {
        this(content, BuiltInAtomicType.STRING);
    }

    /**
     * Construct an instance that wraps a supplied {@code UnicodeString}, with a supplied atomic type
     * @param content the {@code UnicodeString} to wrap
     * @param type    the requested atomic type
     */

    public StringValue(UnicodeString content, AtomicMetadata type) {
        super(type);
        this.content = content;
    }

    /**
     * Constructor from String. Creates an instance of xs:string.
     * @param value the String value.
     */

    public StringValue(String value) {
        this(value, BuiltInAtomicType.STRING);
    }

    /**
     * Constructor from String. Note that although a StringValue may wrap any kind of CharSequence
     * (usually a String, but it can also be, for example, a StringBuffer), the caller
     * is responsible for ensuring that the value is immutable.
     *
     * @param value     the String value.
     * @param typeLabel the type of the value to be created. The caller must ensure that this is
     *                  a type derived from xs:string and that the string is valid against this type.
     */

    public StringValue(String value, AtomicMetadata typeLabel) {
        super(typeLabel);
        this.content = StringTool.fromCharSequence(value);
    }

    /**
     * Factory method for untyped atomic values
     * @param value the string value
     * @return a new untyped atomic value around this string
     */

    public static StringValue makeUntypedAtomic(UnicodeString value) {
        return new StringValue(value, BuiltInAtomicType.UNTYPED_ATOMIC);
    }

    /**
     * Create a copy of this atomic value, with a different type label
     *
     * @param metadata the type label of the new copy. The caller is responsible for checking that
     *                  the value actually conforms to this type.
     */

    @Override
    public StringValue withMetadata(AtomicMetadata metadata) {
        if (metadata == this.metadata) {
            return this;
        } else {
            return new StringValue(this.content, metadata);
        }
    }

    /**
     * Construct a StringValue whose content is known to consist entirely of BMP characters
     * (codepoints less than 65536, with no surrogate pairs)
     * @param content the content of the string, which the caller guarantees to contain
     *                no surrogate pairs
     * @return the corresponding StringValue
     */
    public static StringValue bmp(String content) {
        //TODO: most if not all calls supply a literal String. Use a static constant pool.
        return new StringValue(BMPString.of(content));
    }

    /**
     * Determine the primitive type of the value. This delivers the same answer as
     * getItemType().getPrimitiveItemType(). The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration,
     * and xs:untypedAtomic. For external objects, the result is AnyAtomicType.
     */

    @Override
    public BuiltInAtomicType getPrimitiveType() {
        return getItemType() == BuiltInAtomicType.UNTYPED_ATOMIC ? BuiltInAtomicType.UNTYPED_ATOMIC : BuiltInAtomicType.STRING;
    }

    /**
     * Factory method. Unlike the constructor, this avoids creating a new StringValue in the case
     * of a zero-length string (and potentially other strings, in future)
     *
     * @param value the String value. Null is taken as equivalent to "".
     * @return the corresponding StringValue
     */

    /*@NotNull*/
    public static StringValue makeStringValue(CharSequence value) {
        if (value == null || value.length() == 0) {
            return StringValue.EMPTY_STRING;
        } else {
            return new StringValue(value.toString());
        }
    }

    public static StringValue makeUStringValue(UnicodeString value) {
        if (value == null || value.isEmpty()) {
            return StringValue.EMPTY_STRING;
        } else {
            return new StringValue(value);
        }
    }

    @Override
    public UnicodeString getPrimitiveStringValue() {
        return content;
    }

    /**
     * Get the content of this <code>StringValue</code>
     * @return the content
     */

    public UnicodeString getContent() {
        return content;
    }

    /**
     * Get the length of this string, in code points
     * @return the length of the string in Unicode code points
     */

    public long length() {
        return content.length();
    }

    /**
     * Get the length of this string, in code points
     *
     * @return the length of the string in Unicode code points, provided that it is less than 2^31
     * @throws UnsupportedOperationException if the string contains more than 2^31 code points
     */

    public int length32() {
        return content.length32();
    }


    /**
     * Determine whether the string is a zero-length string. This may
     * be more efficient than testing whether the length is equal to zero
     *
     * @return true if the string is zero length
     */


    public boolean isEmpty() {
        return content.isEmpty();
    }

    /**
     * Iterate over a string, returning a sequence of integers representing the Unicode code-point values
     *
     * @return an iterator over the characters (Unicode code points) in the string
     */

    /*@NotNull*/
    public synchronized AtomicIterator iterateCharacters() {
        return new CodepointIterator(codePoints());
    }

    public StringValue economize() {
        UnicodeString c2 = content.economize();
        if (c2 != content) {
            return new StringValue(c2, metadata);
        }
        return this;
    }

    /**
     * Get an object value that implements the XPath equality and ordering comparison semantics for this value.
     * A context argument is supplied for use in cases where the comparison
     * semantics are context-sensitive, for example where they depend on the implicit timezone or the default
     * collation.
     *
     * @param collator         Collation to be used for comparing strings
     * @param implicitTimezone the XPath dynamic evaluation context, used in cases where the comparison is context
     *                         sensitive
     * @param specVersion
     * @return an Object whose equals() and hashCode() methods implement the XPath comparison semantics
     * with respect to this atomic value. If ordered is specified, the result will either be null if
     * no ordering is defined, or will be a Comparable
     */

    @Override
    public AtomicMatchKey getXPathMatchKey(/*@NotNull*/ StringCollator collator, int implicitTimezone, int specVersion) {
        return collator.getCollationKey(this.getUnicodeStringValue());
    }

    /**
     * Get a value whose equals() method follows the "same key" rules for comparing the keys of a map.
     *
     * @param specVersion
     * @return a value with the property that the equals() and hashCode() methods follow the rules for comparing
     * keys in maps.
     */
    @Override
    public AtomicMatchKey asMapKey(int specVersion) {
        return getUnicodeStringValue();
    }

    /**
     * Get an atomic value that encapsulates this match key. Needed to support the collation-key() function.
     *
     * @return an atomic value that encapsulates this match key
     */

    public Base64BinaryValue getCodepointCollationKey() {
        int len = getContent().length32();
        byte[] result = new byte[len * 3];
        for (int i = 0, j = 0; i < len; i++) {
            int c = getContent().codePointAt(i);
            result[j++] = (byte) (c >> 16);
            result[j++] = (byte) (c >> 8);
            result[j++] = (byte) c;
        }
        return new Base64BinaryValue(result);
    }

    /**
     * Get an iterator over the Unicode codepoints in the value. These will always be full codepoints, never
     * surrogates (surrogate pairs are combined where necessary).
     * @return a sequence of Unicode codepoints
     */

    public IntIterator codePoints() {
        return content.codePoints();
    }

    public int hashCode() {
        // Same algorithm as String#hashCode(), but not cached; and truncated after 100 characters
        int h = 0;
        int count = 0;
        IntIterator iter = codePoints();
        while (iter.hasNext()) {
            h = 31 * h + iter.next();
            if (++count >= 100) {
                break;
            }
        }
        return h;
    }

    /**
     * Test whether this StringValue is equal to another under the rules of the codepoint collation.
     * The type annotation is ignored.
     *
     * @param o the value to be compared with this value
     * @return true if the strings are equal on a codepoint-by-codepoint basis
     */

    public boolean equals(Object o) {
        if (o instanceof StringValue) {
            return content.equals(((StringValue)o).content);
        } else {
            return false;
        }
    }


    /**
     * Get the effective boolean value of a string
     *
     * @return true if the string has length greater than zero
     */

    @Override
    public boolean effectiveBooleanValue() {
        return !isEmpty();
    }

    /**
     * Display as a string. In general toString() for an atomic value displays the value as it would be
     * written in XPath: so this method returns the string with delimiting quotes.
     * @see #getUnicodeStringValue()
     */

    /*@NotNull*/
    public String toString() {
        return getContent().toString();
    }

    @Override
    public UnicodeString getUnicodeStringValue() {
        return content;
    }

    @Override
    public String toShortString() {
        String s = content.toString();
        if (s.length() > 40) {
            s = s.substring(0,20) + " ... " + s.substring(s.length()-20);
        }
        s = "\"" + s + '\"';
        if (getItemType() == BuiltInAtomicType.UNTYPED_ATOMIC) {
            s = "u" + s;
        }
        return s;
    }

    public static class StringXPathComparable implements XPathComparable {

        StringCollator collator;
        UnicodeString value;
        public StringXPathComparable(UnicodeString value, StringCollator collator) {
            this.collator = collator;
            this.value = value;
        }
        @Override
        public int compareTo(XPathComparable o) {
            if (o instanceof StringValue) {
                return collator.compareStrings(value, ((StringValue) o).content);
            } else if (o instanceof StringXPathComparable) {
                return collator.compareStrings(value, ((StringXPathComparable)o).value);
            } else {
                String other = o.toString();
                if (o instanceof AtomicValue) {
                    other = ((AtomicValue) o).getItemType() + " value " + other;
                }
                throw new ClassCastException("Cannot compare xs:string value '"
                                                     + value + "' to " + other);
            }
        }
    }

    @Override
    @CSharpInnerClass(outer=true, extra={"Saxon.Hej.lib.StringCollator collator"})
    public XPathComparable getXPathComparable(StringCollator collator, int implicitTimezone, int specVersion) throws NoDynamicContextException {
        return new StringXPathComparable(getContent(), collator);
    }

    /**
     * Determine whether two atomic values are identical, as determined by XML Schema rules. This is a stronger
     * test than equality (even schema-equality); for example two dateTime values are not identical unless
     * they are in the same timezone.
     * <p>Note that even this check ignores the type annotation of the value. The integer 3 and the short 3
     * are considered identical, even though they are not fully interchangeable. "Identical" means the
     * same point in the value space, regardless of type annotation.</p>
     * <p>NaN is identical to itself.</p>
     *
     * @param v the other value to be compared with this one
     * @return true if the two values are identical, false otherwise.
     */

    @Override
    public boolean isIdentical(/*@NotNull*/ AtomicValue v) {
        return v instanceof StringValue &&
                (this instanceof AnyURIValue == v instanceof AnyURIValue) &&
                (this.isUntypedAtomic() == v.isUntypedAtomic()) &&
                equals(v);
    }

    /**
     * CharacterIterator is used to iterate over the characters in a string,
     * returning them as integers representing the Unicode code-point.
     */


    public final static class CharacterIterator implements AtomicIterator {

        int inpos = 0;        // 0-based index of the current Java char
        private final CharSequence value;

        /**
         * Create an iterator over a string
         * @param value the string
         */

        public CharacterIterator(CharSequence value) {
            this.value = value;
        }

        /*@Nullable*/
        @Override
        public Int64Value next() {
            if (inpos < value.length()) {
                int c = value.charAt(inpos++);
                int current;
                if (c >= 55296 && c <= 56319) {
                    // we'll trust the data to be sound
                    try {
                        current = ((c - 55296) * 1024) + ((int) value.charAt(inpos++) - 56320) + 65536;
                    } catch (StringIndexOutOfBoundsException e) {
                        throw new AssertionError("Invalid surrogate at end of string: " + StringTool.diagnosticDisplay(value.toString()));
                    }
                } else {
                    current = c;
                }
                return new Int64Value(current);
            } else {
                return null;
            }
        }

    }


    public static final class Builder implements UniStringConsumer {
        
        UnicodeBuilder buffer = new UnicodeBuilder();

        @Override
        public UniStringConsumer accept(UnicodeString chars) {
            buffer.accept(chars);
            return this;
        }

        public UnicodeString getStringValue() {
            return buffer.toUnicodeString();
        }

    }

}

