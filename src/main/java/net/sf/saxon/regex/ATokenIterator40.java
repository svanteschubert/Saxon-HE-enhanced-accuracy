////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.value.StringValue;

import java.util.Objects;

/**
 * A ATokenIterator is an iterator over the strings that result from tokenizing a string using a regular expression
 * This is a rewrite for 4.0 (which allows a regex to match a zero-length string; it is based on the implementation
 * of fn:analyze string and could be considerably simplified.
 */

public class ATokenIterator40 implements AtomicIterator {

    private final UnicodeString theString;   // the input string being matched
    private final REMatcher _matcher;    // the Matcher object that does the matching, and holds the state
    private UnicodeString current;     // the string most recently returned by the iterator
    private UnicodeString nextSubstring;        // if the last string was a matching string, null; otherwise the next substring
    //        matched by the regex
    private int prevEnd = 0;    // the position in the input string of the end of the last match or non-match
    // evaluated on demand: a table that indicates for each captured group,
    // what its immediately-containing captured group is.
    private boolean skip = false; // indicates the last match was zero length
    private boolean atStart = true;
    private boolean finished = false;
    private boolean prevMatch = false;
    boolean prevMatchZeroLength = false;

    /**
     * Construct a RegexIterator. Note that the underlying matcher.find() method is called once
     * to obtain each matching substring. But the iterator also returns non-matching substrings
     * if these appear between the matching substrings.
     *
     * @param str     the string to be analysed
     * @param matcher a matcher for the regular expression
     */

    public ATokenIterator40(UnicodeString str, REMatcher matcher) {
        Objects.requireNonNull(str);
        //Objects.requireNonNull(regex);
        Objects.requireNonNull(matcher);
        theString = str;
        //this._regex = regex;
        this._matcher = matcher;
        nextSubstring = null;
    }

    /**
     * Get the next item in the sequence
     *
     * @return the next item in the sequence
     */

    @Override
    public StringValue next() {
        if (finished) {
            return null;
        }

        while (true) {
            boolean first = atStart;
            StringValue candidate = next0();
            atStart = false;
            if (candidate == null) {
                finished = true;
                if (prevMatch && !prevMatchZeroLength) {
                    // If the last thing is a non-zero length matching token, insert a zero-length non-match
                    return StringValue.EMPTY_STRING;
                }
                return null;
            }
            if (isMatching()) {
                boolean prevMatch0 = prevMatch;
                prevMatch = true;
                prevMatchZeroLength = current.isEmpty();
                if (first) {
                    // If the first thing is a non-zero length matching token, insert a zero-length non-match
                    if (!current.isEmpty()) {
                        return StringValue.EMPTY_STRING;
                    }
                }
                if (prevMatch0) {
                    // Between two consecutive matches, insert a zero-length non-match
                    return StringValue.EMPTY_STRING;
                }
            } else {
                prevMatch = false;
                return candidate;
            }
        }
    }

    /**
     * Get the next matching or non-matching substring
     * @return the next matching or non-matching substring
     */

    private StringValue next0() {
        try {
            if (nextSubstring == null && prevEnd >= 0) {
                // we've returned a match (or we're at the start), so find the next match
                int searchStart = prevEnd;
                if (skip) {
                    // previous match was zero-length
                    searchStart++;
                    if (searchStart >= theString.length()) {
                        if (prevEnd < theString.length()) {
                            current = theString.substring(prevEnd);
                            nextSubstring = null;
                        } else {
                            current = null;
                            prevEnd = -1;
                            return null;
                        }
                    }
                }
                if (_matcher.match(theString, searchStart)) {
                    int start = _matcher.getParenStart(0);
                    int end = _matcher.getParenEnd(0);
                    skip = start == end;
                    if (prevEnd == start) {
                        // there's no intervening non-matching string to return
                        nextSubstring = null;
                        current = theString.substring(start, end);
                        prevEnd = end;
                    } else {
                        // return the non-matching substring first
                        current = theString.substring(prevEnd, start);
                        nextSubstring = theString.substring(start, end);
                    }
                } else {
                    // there are no more regex matches, we must return the final non-matching text if any
                    if (prevEnd < theString.length()) {
                        current = theString.substring(prevEnd);
                        nextSubstring = null;
                    } else {
                        // this really is the end...
                        current = null;
                        prevEnd = -1;
                        return null;
                    }
                    prevEnd = -1;
                }
            } else {
                // we've returned a non-match, so now return the match that follows it, if there is one
                if (prevEnd >= 0) {
                    current = nextSubstring;
                    nextSubstring = null;
                    prevEnd = _matcher.getParenEnd(0);
                } else {
                    current = null;
                    return null;
                }
            }
            return currentStringValue();
        } catch (StackOverflowError e) {
            XPathException err = new XPathException.StackOverflow(
                    "Stack overflow (excessive recursion) during regular expression evaluation",
                    SaxonErrorCode.SXRE0001, Loc.NONE);
            throw new UncheckedXPathException(err);
        }
    }

    private StringValue currentStringValue() {
        return new StringValue(current);
    }


    /**
     * Determine whether the current item is a matching item or a non-matching item
     *
     * @return true if the current item (the one most recently returned by next()) is
     * an item that matches the regular expression, or false if it is an item that
     * does not match
     */

    public boolean isMatching() {
        return nextSubstring == null && prevEnd >= 0;
    }

}

