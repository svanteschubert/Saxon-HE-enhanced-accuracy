////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.regex.ARegularExpression;
import net.sf.saxon.str.*;
import net.sf.saxon.transpile.CSharpDelegate;
import net.sf.saxon.transpile.CSharpSimpleEnum;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.z.IntIterator;

import java.util.regex.Pattern;

/**
 * This class provides helper methods and constants for handling whitespace
 */
public class Whitespace {

    private final static ARegularExpression anyWhitespace =
            ARegularExpression.compile(StringTool.fromLatin1("[ \\n\\r\\t]+"), "");

    private final static Pattern J_oneWhitespace = Pattern.compile("[ \\n\\r\\t]");
    private final static Pattern J_anyWhitespace = Pattern.compile("[ \\n\\r\\t]+");


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
     * Apply schema-defined whitespace normalization to a string
     *
     * @param action the action to be applied: one of PRESERVE, REPLACE, or COLLAPSE
     * @param value  the value to be normalized
     * @return the value after normalization
     */

    public static UnicodeString applyWhitespaceNormalization(int action, UnicodeString value) {
        switch (action) {
            case PRESERVE:
                return value;
            case REPLACE:
                UnicodeBuilder sb = new UnicodeBuilder(value.length32());
                IntIterator iter = value.codePoints();
                while (iter.hasNext()) {
                    int c = iter.next();
                    switch (c) {
                        case '\n':
                        case '\r':
                        case '\t':
                            sb.append(' ');
                            break;
                        default:
                            sb.append(c);
                            break;
                    }
                }
                return sb.toUnicodeString();
            case COLLAPSE:
                return collapseWhitespace(value);
            case TRIM:
                return trim(value);
            default:
                throw new IllegalArgumentException("Unknown whitespace facet value");
        }
    }

    /**
     * Remove all whitespace characters from a string
     *
     * @param value the string from which whitespace is to be removed
     * @return the string without its whitespace.
     */

    /*@NotNull*/
    public static String removeAllWhitespace(String value) {
        return J_oneWhitespace.matcher(value).replaceAll("");
    }

    /**
     * Remove leading whitespace characters from a string
     *
     * @param value the string whose leading whitespace is to be removed
     * @return the string with leading whitespace removed. This may be the
     *         original string if there was no leading whitespace
     */

    public static UnicodeString removeLeadingWhitespace(UnicodeString value) {
        long start = trimmedStart(value);
        if (start == 0) {
            return value;
        } else if (start < 0) {
            return EmptyUnicodeString.getInstance();
        } else {
            return value.substring(start);
        }
    }

    /**
     * Determine if a string contains any whitespace
     *
     * @param codePoints the string to be tested, as a codepoint iterator
     * @return true if the string contains a character that is XML whitespace, that is
     *         tab, newline, carriage return, or space
     */

    public static boolean containsWhitespace(IntIterator codePoints) {
        while (codePoints.hasNext()) {
            int c = codePoints.next();
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
     *         characters. (So the result is true for a zero-length string.)
     */

    public static boolean isAllWhite(UnicodeString content) {
        if (content instanceof WhitespaceString) {
            return true;
        }
        return content.indexWhere(ch -> !isWhite(ch), 0) < 0;
    }

    /*@NotNull*/ private static final boolean[] C0WHITE = {
            false, false, false, false, false, false, false, false,  // 0-7
            false, true, true, false, false, true, false, false,     // 8-15
            false, false, false, false, false, false, false, false,  // 16-23
            false, false, false, false, false, false, false, false,  // 24-31
            true                                                     // 32
    };

    @CSharpSimpleEnum
    private enum TokenCategory {INITIAL_WHITESPACE, SEPARATOR_WHITESPACE, FINAL_WHITESPACE, CONTENT}

    @FunctionalInterface
    @CSharpDelegate(true)
    private interface TokenHandler {
        void handleToken(int start, int end, TokenCategory category);
    }

    /**
     * This method takes as input an IntIterator over the characters or code points of a string. It is supplied
     * with a callback function which is invoked for each significant substring, identified by start and end
     * position; each substring is labelled as being either leading whitespace, separator whitespace, trailing
     * whitespace, or a (non-whitespace) token.
     */

    private static void tokenize(IntIterator input, TokenHandler handler) {
        int position = 0;
        int tokenStart = 0;
        TokenCategory currentCategory = TokenCategory.INITIAL_WHITESPACE;
        while (input.hasNext()) {
            int ch = input.next();
            if (isWhite(ch)) {
                if (currentCategory == TokenCategory.CONTENT) {
                    handler.handleToken(tokenStart, position, currentCategory);
                    tokenStart = position;
                    currentCategory = TokenCategory.SEPARATOR_WHITESPACE;
                }
            } else {
                if (currentCategory != TokenCategory.CONTENT) {
                    if (position > 0) {
                        handler.handleToken(tokenStart, position, currentCategory);
                    }
                    tokenStart = position;
                    currentCategory = TokenCategory.CONTENT;
                }
            }
            position++;
        }
        if (position > tokenStart) {
            if (currentCategory == TokenCategory.SEPARATOR_WHITESPACE) {
                handler.handleToken(tokenStart, position, TokenCategory.FINAL_WHITESPACE);
            } else {
                handler.handleToken(tokenStart, position, currentCategory);
            }
        }
    }

    /**
     * Determine if a character is whitespace
     *
     * @param c the character or codepoint to be tested
     * @return true if the character is a whitespace character
     */

    public static boolean isWhite(int c) {
        return c <= 32 && C0WHITE[c];
    }

    /**
     * Normalize whitespace as defined in XML Schema. Note that this is not the same
     * as the XPath normalize-space() function, which is supported by the
     * {@link #collapseWhitespace} method
     *
     * @param input the string to be normalized
     * @return a copy of the string in which any whitespace character is replaced by
     *         a single space character
     */

    /*@NotNull*/
    public static UnicodeString normalizeWhitespace(UnicodeString input) {
        UnicodeBuilder sb = new UnicodeBuilder(input.length32());
        IntIterator iter = input.codePoints();
        while (iter.hasNext()) {
            int c = iter.next();
            switch (c) {
                case '\n':
                case '\r':
                case '\t':
                    sb.append(' ');
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toUnicodeString();
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
    public static UnicodeString collapseWhitespace(UnicodeString in) {
        if (!containsWhitespace(in.codePoints())) {
            return in;
        }
        final long len = trimmedEnd(in);
        UnicodeBuilder sb = new UnicodeBuilder(in.length32());
        boolean inWhitespace = true;
        for (long i = 0; i < len; i++) {
            int c = in.codePointAt(i);
            switch (c) {
                case '\n':
                case '\r':
                case '\t':
                case ' ':
                    if (!inWhitespace) {
                        sb.append(0x20);
                        inWhitespace = true;
                    }
                    break;
                default:
                    sb.append(c);
                    inWhitespace = false;
                    break;
            }
        }
        return sb.toUnicodeString();
    }

    /**
     * Collapse whitespace as defined in XML Schema. This is equivalent to the
     * XPath normalize-space() function
     *
     * @param in the string whose whitespace is to be collapsed
     * @return the string with any leading or trailing whitespace removed, and any
     * internal sequence of whitespace characters replaced with a single space character.
     */

    public static String collapseWhitespace(String in) {
        if (!containsWhitespace(StringTool.codePoints(in))) {
            return in;
        }
        return trim(J_anyWhitespace.matcher(in).replaceAll(" "));
    }

    /**
     * Get the codepoint offset of the first non-whitespace character in the string
     * @param in the input string
     * @return the index of the first non-whitespace character; or -1 if the string consists
     * entirely of whitespace (including the case where the string is zero-length)
     */

    public static long trimmedStart(UnicodeString in) {
        long len = in.length();
        for (int i = 0; i < len; i++) {
            if (!isWhite(in.codePointAt(i))) {
                return i;
            }
        }
        return -1L;
    }

    /**
     * Get the codepoint offset of the first whitespace character in trailing whitespace in the string
     *
     * @param in the input string
     * @return the index of the last non-whitespace character plus one; or zero if the string consists
     * entirely of whitespace
     */

    public static long trimmedEnd(UnicodeString in) {
        long len = in.length();
        for (long i = len - 1; i >= 0; i--) {
            if (!isWhite(in.codePointAt(i))) {
                return i+1;
            }
        }
        return 0;
    }

    /**
     * Trim whitespace: return the input string with leading and trailing whitespace removed
     * @param in  the input string
     * @return he input string with leading and trailing whitespace removed
     */

    public static UnicodeString trim(UnicodeString in) {
        long start = trimmedStart(in);
        if (start == -1) {
            // All whitespace
            return EmptyUnicodeString.getInstance();
        }
        long end = trimmedEnd(in);
        if (start == 0 && end == in.length()) {
            // No leading or trailing whitespace
            return in;
        }
        return in.substring(start, end);
    }

    /**
     * Trim whitespace: return the input string with leading and trailing whitespace removed.
     * Note that this differs from {@link String#trim} because the definition of whitespace
     * is different.
     * @param in the input string
     * @return he input string with leading and trailing whitespace removed
     */

    public static String trim(String in) {
        if (in == null) {
            return null;
        }
        int firstNonWhite = -1;
        int lastNonWhite = -1;
        int len = in.length();
        for (int i = 0; i < len; i++) {
            if (!isWhite(in.charAt(i))) {
                firstNonWhite = i;
                break;
            }
        }
        if (firstNonWhite == -1) {
            // All whitespace
            return "";
        }
        for (int i = len - 1; i >= firstNonWhite; i--) {
            if (!isWhite(in.charAt(i))) {
                lastNonWhite = i;
                break;
            }
        }
        if (firstNonWhite == 0 && lastNonWhite == in.length()) {
            // No leading or trailing whitespace
            return in;
        }
        return in.substring(firstNonWhite, lastNonWhite + 1);
    }

    public static UnicodeString collapse(UnicodeString in) {
        UnicodeBuilder builder = new UnicodeBuilder(in.length32());
        tokenize(in.codePoints(), (s, e, cat) -> {
            @SuppressWarnings("UnnecessaryLocalVariable")
            TokenCategory category = cat; // redeclared to assist C# conversion
            switch (category) {
                case CONTENT:
                    builder.accept(in.substring(s, e));
                    break;
                case SEPARATOR_WHITESPACE:
                    builder.append(' ');
                    break;
                default:
                    // no action
                    break;
            }
        });
        return builder.toUnicodeString();
    }

    public static String collapse(CharSequence in) {
        StringBuilder builder = new StringBuilder(in.length());
        tokenize(StringTool.codePoints(in), (s, e, cat) -> {
            @SuppressWarnings("UnnecessaryLocalVariable")
            TokenCategory category = cat; // redeclared to assist C# conversion
            switch (category) {
                case CONTENT:
                    builder.append(in.subSequence(s, e));
                    break;
                case SEPARATOR_WHITESPACE:
                    builder.append(' ');
                    break;
                default:
                    // no action
                    break;
            }
        });
        return builder.toString();
    }

    public static UnicodeString normalize(UnicodeString in) {
        UnicodeBuilder builder = new UnicodeBuilder(in.length32());
        tokenize(in.codePoints(), (s, e, cat) -> {
            @SuppressWarnings("UnnecessaryLocalVariable")
            TokenCategory category = cat; // redeclared to assist C# conversion
            switch (category) {
                case CONTENT:
                    builder.accept(in.substring(s, e));
                    break;
                case SEPARATOR_WHITESPACE:
                    for (int i=s; i<e; i++) {
                        builder.append(' ');
                    }
                    break;
                default:
                    // no action
                    break;
            }
        });
        return builder.toUnicodeString();
    }

    public static String normalize(CharSequence in) {
        StringBuilder builder = new StringBuilder(in.length());
        tokenize(StringTool.codePoints(in), (s, e, cat) -> {
            @SuppressWarnings("UnnecessaryLocalVariable")
            TokenCategory category = cat; // redeclared to assist C# conversion
            switch (category) {
                case CONTENT:
                    builder.append(in.subSequence(s, e));
                    break;
                case SEPARATOR_WHITESPACE:
                    for (int i = s; i < e; i++) {
                        builder.append(' ');
                    }
                    break;
                default:
                    // no action
            }
        });
        return builder.toString();
    }


    /**
     * An iterator that splits a string on whitespace boundaries, corresponding to the XPath 3.1 function tokenize#1
     */

    public static class Tokenizer implements AtomicIterator {

        private final UnicodeString input;
        private long position;

        public Tokenizer(String input) {
            this.input = StringView.tidy(input);
            this.position = 0;
        }

        public Tokenizer(UnicodeString input) {
            this.input = input.tidy();
            this.position = 0;
        }

        @Override
        public StringValue next() {
            long start = position;
            long eol = input.length();
            while (start < eol && isWhite(input.codePointAt(start))) {
                start++;
            }
            if (start >= eol) {
                return null;
            }
            long end = start;
            while (end < eol && !isWhite(input.codePointAt(end))) {
                end++;
            }
            position = end;
            return new StringValue(input.substring(start, end));
        }

    }
}
