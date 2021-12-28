////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.tree.tiny.CharSlice;
import net.sf.saxon.tree.tiny.CompressedWhitespace;
import net.sf.saxon.tree.util.FastStringBuffer;

/**
 * This class provides helper methods and constants for handling whitespace
 */
public class Whitespace {

    private Whitespace() {
    }


    /**
     * The values PRESERVE, REPLACE, and COLLAPSE represent the three options for whitespace
     * normalization. They are deliberately chosen in ascending strength order; given a number
     * of whitespace facets, only the strongest needs to be carried out. The option TRIM is
     * used instead of COLLAPSE when all valid values have no interior whitespace; trimming
     * leading and trailing whitespace is then equivalent to the action of COLLAPSE, but faster.
     */

    public static final int PRESERVE = 0;
    public static final int REPLACE = 1;
    public static final int COLLAPSE = 2;
    public static final int TRIM = 3;

    /**
     * The values NONE, IGNORABLE, and ALL identify which kinds of whitespace text node
     * should be stripped when building a source tree. UNSPECIFIED indicates that no
     * particular request has been made. XSLT indicates that whitespace should be stripped
     * as defined by the xsl:strip-space and xsl:preserve-space declarations in the stylesheet
     */

    public static final int NONE = 0;
    public static final int IGNORABLE = 1;
    public static final int ALL = 2;
    public static final int UNSPECIFIED = 3;
    public static final int XSLT = 4;

    /**
     * Test whether a character is whitespace
     *
     * @param ch the character (Unicode codepoint) to be tested
     * @return true if the character is one of tab, newline, carriage return, or space
     */

    public static boolean isWhitespace(int ch) {
        switch (ch) {
            case 9:
            case 10:
            case 13:
            case 32:
                return true;
            default:
                return false;
        }
    }

    /**
     * Apply schema-defined whitespace normalization to a string
     *
     * @param action the action to be applied: one of PRESERVE, REPLACE, or COLLAPSE
     * @param value  the value to be normalized
     * @return the value after normalization
     */

    public static CharSequence applyWhitespaceNormalization(int action, /*@NotNull*/ CharSequence value) {
        switch (action) {
            case PRESERVE:
                return value;
            case REPLACE:
                FastStringBuffer sb = new FastStringBuffer(value.length());
                for (int i = 0; i < value.length(); i++) {
                    char c = value.charAt(i);
                    switch (c) {
                        case '\n':
                        case '\r':
                        case '\t':
                            sb.cat(' ');
                            break;
                        default:
                            sb.cat(c);
                    }
                }
                return sb;
            case COLLAPSE:
                return collapseWhitespace(value);
            case TRIM:
                return trimWhitespace(value);
            default:
                throw new IllegalArgumentException("Unknown whitespace facet value");
        }
    }

    /**
     * Remove all whitespace characters from a string
     *
     * @param value the string from which whitespace is to be removed
     * @return the string without its whitespace. This may be the original value
     *         if it contained no whitespace
     */

    /*@NotNull*/
    public static CharSequence removeAllWhitespace(/*@NotNull*/ CharSequence value) {
        if (containsWhitespace(value)) {
            FastStringBuffer sb = new FastStringBuffer(value.length());
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c > 32 || !C0WHITE[c]) {
                    sb.cat(c);
                }
            }
            return sb;
        } else {
            return value;
        }
    }

    /**
     * Remove leading whitespace characters from a string
     *
     * @param value the string whose leading whitespace is to be removed
     * @return the string with leading whitespace removed. This may be the
     *         original string if there was no leading whitespace
     */

    public static CharSequence removeLeadingWhitespace(/*@NotNull*/ CharSequence value) {
        final int len = value.length();
        // quick exit for common cases...
        if (len == 0 || value.charAt(0) > 32) {
            return value;
        }
        int start = -1;
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c > 32 || !C0WHITE[c]) {
                start = i;
                break;
            }
        }
        if (start == 0) {
            return value;
        } else if (start < 0 || start == len) {
            return "";
        } else {
            return value.subSequence(start, len);
        }
    }

    /**
     * Determine if a string contains any whitespace
     *
     * @param value the string to be tested
     * @return true if the string contains a character that is XML whitespace, that is
     *         tab, newline, carriage return, or space
     */

    public static boolean containsWhitespace(CharSequence value) {
        for (int i = value.length() - 1; i >= 0; ) {
            char c = value.charAt(i--);
            if (c <= 32 && C0WHITE[c]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determine if a string is all-whitespace
     *
     * @param content the string to be tested
     * @return true if the supplied string contains no non-whitespace
     *         characters
     */

    public static boolean isWhite(/*@NotNull*/ CharSequence content) {
        if (content instanceof CompressedWhitespace) {
            return true;
        }
        final int len = content.length();
        for (int i = 0; i < len; ) {
            // all valid XML 1.0 whitespace characters, and only whitespace characters, are <= 0x20
            // But XML 1.1 allows non-white characters that are also < 0x20, so we need a specific test for these
            char c = content.charAt(i++);
            if (c > 32 || !C0WHITE[c]) {
                return false;
            }
        }
        return true;
    }

    /*@NotNull*/ private static boolean[] C0WHITE = {
            false, false, false, false, false, false, false, false,  // 0-7
            false, true, true, false, false, true, false, false,     // 8-15
            false, false, false, false, false, false, false, false,  // 16-23
            false, false, false, false, false, false, false, false,  // 24-31
            true                                                     // 32
    };

    /**
     * Determine if a character is whitespace
     *
     * @param c the character to be tested
     * @return true if the character is a whitespace character
     */

    public static boolean isWhite(char c) {
        return c <= 32 && C0WHITE[c];
    }

    /**
     * Normalize whitespace as defined in XML Schema. Note that this is not the same
     * as the XPath normalize-space() function, which is supported by the
     * {@link #collapseWhitespace} method
     *
     * @param in the string to be normalized
     * @return a copy of the string in which any whitespace character is replaced by
     *         a single space character
     */

    /*@NotNull*/
    public static CharSequence normalizeWhitespace(/*@NotNull*/ CharSequence in) {
        FastStringBuffer sb = new FastStringBuffer(in.length());
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            switch (c) {
                case '\n':
                case '\r':
                case '\t':
                    sb.cat(' ');
                    break;
                default:
                    sb.cat(c);
                    break;
            }
        }
        return sb;
    }

    /**
     * Collapse whitespace as defined in XML Schema. This is equivalent to the
     * XPath normalize-space() function
     *
     * @param in the string whose whitespace is to be collapsed
     * @return the string with any leading or trailing whitespace removed, and any
     *         internal sequence of whitespace characters replaced with a single space character.
     */

    /*@NotNull*/
    public static CharSequence collapseWhitespace(/*@NotNull*/ CharSequence in) {
        if (!containsWhitespace(in)) {
            return in;
        }
        int len = in.length();
        FastStringBuffer sb = new FastStringBuffer(len);
        boolean inWhitespace = true;
        int i = 0;
        for (; i < len; i++) {
            char c = in.charAt(i);
            switch (c) {
                case '\n':
                case '\r':
                case '\t':
                case ' ':
                    if (inWhitespace) {
                        // remove the whitespace
                    } else {
                        sb.cat(' ');
                        inWhitespace = true;
                    }
                    break;
                default:
                    sb.cat(c);
                    inWhitespace = false;
                    break;
            }
        }
        int nlen = sb.length();
        if (nlen > 0 && sb.charAt(nlen - 1) == ' ') {
            sb.setLength(nlen - 1);
        }
        return sb;
    }

    /**
     * Remove leading and trailing whitespace. This has the same effect as collapseWhitespace,
     * but is cheaper, for use by data types that do not allow internal whitespace.
     *
     * @param in the input string whose whitespace is to be removed
     * @return the result of removing excess whitespace
     */
    public static CharSequence trimWhitespace(/*@NotNull*/ CharSequence in) {
        if (in.length() == 0) {
            return in;
        }
        int first = 0;
        int last = in.length() - 1;
        while (true) {
            final char x = in.charAt(first);
            if (x > 32 || !C0WHITE[x]) {
                break;
            }
            if (first++ >= last) {
                return "";
            }
        }
        while (true) {
            final char x = in.charAt(last);
            if (x > 32 || !C0WHITE[x]) {
                break;
            }
            last--;
        }
        if (first == 0 && last == in.length() - 1) {
            return in;
        } else {
            return in.subSequence(first, last + 1);
        }
    }

    /**
     * Trim leading and trailing whitespace from a string, returning a string.
     * This differs from the Java trim() method in that the only characters treated as
     * whitespace are space, \n, \r, and \t. The String#trim() method removes all C0
     * control characters (which is not the same thing under XML 1.1).
     *
     * @param s the string to be trimmed. If null is supplied, null is returned.
     * @return the string with leading and trailing whitespace removed.
     *         Returns null if and only if the supplied argument is null.
     */

    /*@Nullable*/
    public static String trim(/*@Nullable*/ CharSequence s) {
        if (s == null) {
            return null;
        }
        return trimWhitespace(s).toString();
    }

    /**
     * An iterator that splits a string on whitespace boundaries, corresponding to the XPath 3.1 function tokenize#1
     */

    public static class Tokenizer implements AtomicIterator<StringValue> {

        private char[] input;
        private int position;

        public Tokenizer(char[] input) {
            this.input = input;
            this.position = 0;
        }

        public Tokenizer(CharSequence input) {
            this.input = CharSlice.toCharArray(input);
            this.position = 0;
        }

        @Override
        public StringValue next() {
            int start = position;
            int eol = input.length;
            while (start < eol && isWhite(input[start])) {
                start++;
            }
            if (start >= eol) {
                return null;
            }
            int end = start;
            while (end < eol && !isWhite(input[end])) {
                end++;
            }
            position = end;
            return StringValue.makeStringValue(new CharSlice(input, start, end-start));
        }

        @Override
        public void close() {
            // no action
        }

    }
}
