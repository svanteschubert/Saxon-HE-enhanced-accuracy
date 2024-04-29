////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;

import java.util.function.BiFunction;

/**
 * This interface represents a compiled regular expression. There are different
 * implementations for different regex engines
 */
public interface RegularExpression {

    /**
     * Determine whether the regular expression matches a given string in its entirety
     *
     * @param input the string to match
     * @return true if the string matches, false otherwise
     */

    boolean matches(UnicodeString input);

    /**
     * Determine whether the regular expression contains a match of a given string
     *
     * @param input the string to match
     * @return true if the string matches, false otherwise
     */

    boolean containsMatch(UnicodeString input);

    /**
     * Use this regular expression to tokenize an input string.
     *
     * @param input the string to be tokenized
     * @return a SequenceIterator containing the resulting tokens, as objects of type StringValue
     */

    AtomicIterator tokenize(UnicodeString input);

    /**
     * Use this regular expression to analyze an input string, in support of the XSLT
     * analyze-string instruction. The resulting RegexIterator provides both the matching and
     * non-matching substrings, and allows them to be distinguished. It also provides access
     * to matched subgroups.
     *
     * @param input the character string to be analyzed using the regular expression
     * @return an iterator over matched and unmatched substrings
     */

    /*@NotNull*/
    RegexIterator analyze(UnicodeString input);

    /**
     * Replace all substrings of a supplied input string that match the regular expression
     * with a replacement string.
     *
     * @param input       the input string on which replacements are to be performed
     * @param replacement the replacement string in the format of the XPath replace() function
     * @return the result of performing the replacement
     * @throws XPathException if the replacement string is invalid
     */

    UnicodeString replace(UnicodeString input, UnicodeString replacement) throws XPathException;

    /**
     * Replace all substrings of a supplied input string that match the regular expression
     * with a replacement string.
     *
     * @param input       the input string on which replacements are to be performed
     * @param replacement a function that is called once for each matching substring, and
     *                    that returns a replacement for that substring
     * @return the result of performing the replacement
     * @throws XPathException if the replacement string is invalid
     */

    UnicodeString replaceWith(UnicodeString input, BiFunction<UnicodeString, UnicodeString[], UnicodeString> replacement) throws XPathException;

    /**
     * Get the flags used at the time the regular expression was compiled.
     *
     * @return a string containing the flags
     */

    String getFlags();

    /**
     * Ask whether the regular expression is using platform-native syntax (Java or .NET), or XPath syntax
     * @return true if using platform-native syntax
     */

    boolean isPlatformNative();


}

