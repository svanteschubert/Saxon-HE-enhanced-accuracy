////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.str.BMPString;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Glue class to interface the Jakarta regex engine to Saxon
 * (The prefix 'A' indicates an Apache regular expression, as distinct from
 * a JDK regular expression).
 */

public class ARegularExpression implements RegularExpression {

    UnicodeString rawPattern;
    String rawFlags;
    REProgram regex;

    /**
     * Create and compile a regular expression
     *
     * @param pattern      the regular expression
     * @param flags        the flags (ixsmq)
     * @param hostLanguage one of "XP20", "XP30", "XSD10", "XSD11". Also allow combinations, e.g. "XP20/XSD11".
     * @param warnings     a list to be populated with any warnings arising during compilation of the regex
     * @param config       the Saxon Configuration: may be null
     * @throws XPathException if the regular expression is invalid
     */

    public ARegularExpression(UnicodeString pattern, String flags, String hostLanguage, List<String> warnings, Configuration config) throws XPathException {
        rawFlags = flags;
        REFlags reFlags;
        try {
            reFlags = new REFlags(flags, hostLanguage);
        } catch (RESyntaxException err) {
            throw new XPathException(err.getMessage(), "FORX0001");
        }
        try {
            rawPattern = pattern;
            RECompiler comp2 = new RECompiler();
            comp2.setFlags(reFlags);
            regex = comp2.compile(rawPattern);
            if (warnings != null) {
                warnings.addAll(comp2.getWarnings());
            }
            if (config != null) {
                regex.setBacktrackingLimit(config.getConfigurationProperty(Feature.REGEX_BACKTRACKING_LIMIT));
            }
        } catch (RESyntaxException err) {
            throw new XPathException(err.getMessage(), "FORX0002");
        }
    }

    /**
     * Static factory method intended for simple static regular expressions known to be correct
     * @param pattern the regular expression, using XPath 3.1 syntax
     * @param flags regular expression flags
     * @return the compiled regular expression
     * @throws IllegalArgumentException if the regular expression or the flags are invalid
     */

    public static ARegularExpression compile(String pattern, String flags) {
        try {
            return new ARegularExpression(BMPString.of(pattern), flags, "XP31", null, null);
        } catch (XPathException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Static factory method intended for simple static regular expressions known to be correct
     */

    public static ARegularExpression compile(UnicodeString pattern, String flags) {
        try {
            return new ARegularExpression(pattern, flags, "XP31", null, null);
        } catch (XPathException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Determine whether the regular expression matches a given string in its entirety
     *
     * @param input the string to match
     * @return true if the string matches, false otherwise
     */
    @Override
    public boolean matches(UnicodeString input) {
        if (input.isEmpty() && regex.isNullable()) {
            return true;
        }
        REMatcher matcher = new REMatcher(regex);
        return matcher.isAnchoredMatch(input.tidy());
    }

    /**
     * Determine whether the regular expression contains a match of a given string
     *
     * @param input the string to match
     * @return true if the string matches, false otherwise
     */
    @Override
    public boolean containsMatch(UnicodeString input) {
        REMatcher matcher = new REMatcher(regex);
        return matcher.match(input.tidy(), 0);
    }

    /**
     * Use this regular expression to tokenize an input string.
     *
     * @param input the string to be tokenized
     * @return a SequenceIterator containing the resulting tokens, as objects of type StringValue
     */
    @Override
    public AtomicIterator tokenize(UnicodeString input) {
        return new ATokenIterator(input.tidy(), new REMatcher(regex));
    }

    /**
     * Use this regular expression to analyze an input string, in support of the XSLT
     * analyze-string instruction. The resulting RegexIterator provides both the matching and
     * non-matching substrings, and allows them to be distinguished. It also provides access
     * to matched subgroups.
     *
     * @param input the character string to be analyzed using the regular expression
     * @return an iterator over matched and unmatched substrings
     */
    @Override
    public RegexIterator analyze(UnicodeString input) {
        return new ARegexIterator(input.tidy(), rawPattern, new REMatcher(regex));
    }

    /**
     * Replace all substrings of a supplied input string that match the regular expression
     * with a replacement string.
     *
     * @param input       the input string on which replacements are to be performed
     * @param replacement the replacement string in the format of the XPath replace() function
     * @return the result of performing the replacement
     * @throws net.sf.saxon.trans.XPathException
     *          if the replacement string is invalid
     */
    @Override
    public UnicodeString replace(UnicodeString input, UnicodeString replacement) throws XPathException {
        REMatcher matcher = new REMatcher(regex);
        try {
            return matcher.replace(input.tidy(), replacement);
        } catch (RESyntaxException err) {
            throw new XPathException(err.getMessage(), "FORX0004");
        }
    }

    /**
     * Replace all substrings of a supplied input string that match the regular expression
     * with a replacement string.
     *
     * @param input       the input string on which replacements are to be performed
     * @param replacer    the replacement string in the format of the XPath replace() function
     * @return the result of performing the replacement
     * @throws net.sf.saxon.trans.XPathException if the replacement string is invalid
     */
    @Override
    public UnicodeString replaceWith(UnicodeString input, BiFunction<UnicodeString, UnicodeString[], UnicodeString> replacer) throws XPathException {
        REMatcher matcher = new REMatcher(regex);
        try {
            return matcher.replaceWith(input.tidy(), replacer);
        } catch (RESyntaxException err) {
            throw new XPathException(err.getMessage(), "FORX0004");
        }
    }

    /**
     * Get the flags used at the time the regular expression was compiled.
     *
     * @return a string containing the flags
     */
    @Override
    public String getFlags() {
        return rawFlags;
    }

    /**
     * Ask whether the regular expression is using platform-native syntax (Java or .NET), or XPath syntax
     *
     * @return true if using platform-native syntax
     */
    @Override
    public boolean isPlatformNative() {
        return false;
    }
}

