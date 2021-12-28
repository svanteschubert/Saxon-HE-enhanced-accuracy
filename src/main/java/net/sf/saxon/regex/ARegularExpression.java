////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.value.StringValue;

import java.util.List;
import java.util.function.Function;

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

    public ARegularExpression(CharSequence pattern, String flags, String hostLanguage, List<String> warnings, Configuration config) throws XPathException {
        rawFlags = flags;
        REFlags reFlags;
        try {
            reFlags = new REFlags(flags, hostLanguage);
        } catch (RESyntaxException err) {
            throw new XPathException(err.getMessage(), "FORX0001");
        }
        try {
            rawPattern = UnicodeString.makeUnicodeString(pattern);
            RECompiler comp2 = new RECompiler();
            comp2.setFlags(reFlags);
            regex = comp2.compile(rawPattern);
            if (warnings != null) {
                for (String s : comp2.getWarnings()) {
                    warnings.add(s);
                }
            }
            if (config != null) {
                regex.setBacktrackingLimit(config.getConfigurationProperty(Feature.REGEX_BACKTRACKING_LIMIT));
            }
        } catch (RESyntaxException err) {
            throw new XPathException(err.getMessage(), "FORX0002");
        }
    }

    /**
     * Determine whether the regular expression matches a given string in its entirety
     *
     * @param input the string to match
     * @return true if the string matches, false otherwise
     */
    @Override
    public boolean matches(CharSequence input) {
        if (StringValue.isEmpty(input) && regex.isNullable()) {
            return true;
        }
        REMatcher matcher = new REMatcher(regex);
        return matcher.anchoredMatch(UnicodeString.makeUnicodeString(input));
    }

    /**
     * Determine whether the regular expression contains a match of a given string
     *
     * @param input the string to match
     * @return true if the string matches, false otherwise
     */
    @Override
    public boolean containsMatch(CharSequence input) {
        REMatcher matcher = new REMatcher(regex);
        return matcher.match(UnicodeString.makeUnicodeString(input), 0);
    }

    /**
     * Use this regular expression to tokenize an input string.
     *
     * @param input the string to be tokenized
     * @return a SequenceIterator containing the resulting tokens, as objects of type StringValue
     */
    @Override
    public AtomicIterator tokenize(CharSequence input) {
        return new ATokenIterator(UnicodeString.makeUnicodeString(input), new REMatcher(regex));
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
    public RegexIterator analyze(CharSequence input) {
        return new ARegexIterator(UnicodeString.makeUnicodeString(input), rawPattern, new REMatcher(regex));
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
    public CharSequence replace(CharSequence input, CharSequence replacement) throws XPathException {
        REMatcher matcher = new REMatcher(regex);
        UnicodeString in = UnicodeString.makeUnicodeString(input);
        UnicodeString rep = UnicodeString.makeUnicodeString(replacement);
        try {
            return matcher.replace(in, rep);
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
    public CharSequence replaceWith(CharSequence input, Function<CharSequence, CharSequence> replacer) throws XPathException {
        REMatcher matcher = new REMatcher(regex);
        UnicodeString in = UnicodeString.makeUnicodeString(input);
        try {
            return matcher.replaceWith(in, replacer);
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
}

