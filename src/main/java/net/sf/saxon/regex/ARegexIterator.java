////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.z.IntHashMap;
import net.sf.saxon.z.IntToIntHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Class ARegexIterator - provides an iterator over matched and unmatched substrings.
 * This implementation of RegexIterator uses the modified Jakarta regular expression engine.
 */

public class ARegexIterator implements RegexIterator, LastPositionFinder {

    private final UnicodeString theString;   // the input string being matched
    private final UnicodeString _regex;
    private final REMatcher _matcher;    // the Matcher object that does the matching, and holds the state
    private UnicodeString current;     // the string most recently returned by the iterator
    private UnicodeString nextSubstring;        // if the last string was a matching string, null; otherwise the next substring
    //        matched by the regex
    private int prevEnd = 0;    // the position in the input string of the end of the last match or non-match
    private IntToIntHashMap nestingTable = null;
    // evaluated on demand: a table that indicates for each captured group,
    // what its immediately-containing captured group is.
    private boolean skip = false; // indicates the last match was zero length

    /**
     * Construct a RegexIterator. Note that the underlying matcher.find() method is called once
     * to obtain each matching substring. But the iterator also returns non-matching substrings
     * if these appear between the matching substrings.
     *
     * @param str  the string to be analysed
     * @param matcher a matcher for the regular expression
     */

    public ARegexIterator(UnicodeString str, UnicodeString regex, REMatcher matcher) {
        Objects.requireNonNull(str);
        Objects.requireNonNull(regex);
        Objects.requireNonNull(matcher);
        theString = str;
        this._regex = regex;
        this._matcher = matcher;
        nextSubstring = null;
    }

    @Override
    public boolean supportsGetLength() {
        return true;
    }

    @Override
    public int getLength() {
        ARegexIterator another = new ARegexIterator(theString, _regex, new REMatcher(_matcher.getProgram()));
        int n = 0;
        while (another.next() != null) {
            n++;
        }
        return n;
    }

    /**
     * Get the next item in the sequence
     *
     * @return the next item in the sequence
     */

    @Override
    public StringValue next() {
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
     *         an item that matches the regular expression, or false if it is an item that
     *         does not match
     */

    @Override
    public boolean isMatching() {
        return nextSubstring == null && prevEnd >= 0;
    }

    /**
     * Get a substring that matches a parenthesised group within the regular expression
     *
     * @param number the number of the group to be obtained
     * @return the substring of the current item that matches the n'th parenthesized group
     *         within the regular expression
     */

    @Override

    public UnicodeString getRegexGroup(int number) {
        if (!isMatching()) {
            return null;
        }
        if (number >= _matcher.getParenCount() || number < 0) {
            return EmptyUnicodeString.getInstance();
        }
        UnicodeString us = _matcher.getParen(number);
        return (us == null ? EmptyUnicodeString.getInstance() : us);
    }

    /**
     * Get the number of captured groups
     */
    @Override
    public int getNumberOfGroups() {
        return _matcher.getParenCount();
    }

    /**
     * Process a matching substring, performing specified actions at the start and end of each captured
     * subgroup. This method will always be called when operating in "push" mode; it writes its
     * result to context.getReceiver(). The matching substring text is all written to the receiver,
     * interspersed with calls to the {@link RegexMatchHandler} methods onGroupStart() and onGroupEnd().
     *
     * @param action  defines the processing to be performed at the start and end of a group
     */

    @Override
    public void processMatchingSubstring(RegexMatchHandler action) throws XPathException {
        int c = _matcher.getParenCount() - 1;
        if (c == 0) {
            action.characters(current);
        } else {
            // Create a map from positions in the string to lists of actions.
            // The "actions" in each list are: +N: start group N; -N: end group N.
            IntHashMap<List<Integer>> actions = new IntHashMap<>(c);
            for (int i = 1; i <= c; i++) {
                int start = _matcher.getParenStart(i) - _matcher.getParenStart(0);
                if (start != -1) {
                    int end = _matcher.getParenEnd(i) - _matcher.getParenStart(0);
                    if (start < end) {
                        // Add the start action after all other actions on the list for the same position
                        List<Integer> s = actions.get(start);
                        if (s == null) {
                            s = new ArrayList<>(4);
                            actions.put(start, s);
                        }
                        s.add(i);
                        // Add the end action before all other actions on the list for the same position
                        List<Integer> e = actions.get(end);
                        if (e == null) {
                            e = new ArrayList<>(4);
                            actions.put(end, e);
                        }
                        e.add(0, -i);
                    } else {
                        // zero-length group (start==end). The problem here is that the information available
                        // from Java isn't sufficient to determine the nesting of groups: match("a", "(a(b?))")
                        // and match("a", "(a)(b?)") will both give the same result for group 2 (start=1, end=1).
                        // So we need to go back to the original regex to determine the group nesting
                        if (nestingTable == null) {
                            nestingTable = computeNestingTable(_regex);
                        }
                        int parentGroup = nestingTable.get(i);
                        // insert the start and end events immediately before the end event for the parent group,
                        // if present; otherwise after all existing events for this position
                        List<Integer> s = actions.get(start);
                        if (s == null) {
                            s = new ArrayList<Integer>(4);
                            actions.put(start, s);
                            s.add(i);
                            s.add(-i);
                        } else {
                            int pos = s.size();
                            for (int e = 0; e < s.size(); e++) {
                                if (s.get(e) == -parentGroup) {
                                    pos = e;
                                    break;
                                }
                            }
                            s.add(pos, -i);
                            s.add(pos, i);
                        }

                    }
                }

            }
            UnicodeBuilder buff = new UnicodeBuilder();
            for (int i = 0; i < current.length() + 1; i++) {
                List<Integer> events = actions.get(i);
                if (events != null) {
                    if (!buff.isEmpty()) {
                        action.characters(buff.toUnicodeString());
                        buff.clear();
                    }
                    for (Integer group : events) {
                        if (group > 0) {
                            action.onGroupStart(group);
                        } else {
                            action.onGroupEnd(-group);
                        }
                    }
                }
                if (i < current.length()) {
                    buff.append(current.codePointAt(i));
                }
            }
            if (!buff.isEmpty()) {
                action.characters(buff.toUnicodeString());
            }
        }

    }

    /**
     * Compute a table showing for each captured group number (opening paren in the regex),
     * the number of its parent group. This is done by reparsing the source of the regular
     * expression. This is needed when the result of a match includes an empty group, to determine
     * its position relative to other groups finishing at the same character position.
     */

    public static IntToIntHashMap computeNestingTable(UnicodeString regex) {
        // See bug 3211
        IntToIntHashMap nestingTable = new IntToIntHashMap(16);
        int[] stack = new int[regex.length32()];
        int tos = 0;
        boolean[] captureStack = new boolean[regex.length32()];
        int captureTos = 0;
        int group = 1;
        int inBrackets = 0;
        stack[tos++] = 0;
        for (int i = 0; i < regex.length(); i++) {
            int ch = regex.codePointAt(i);
            if (ch == '\\') {
                i++;
            } else if (ch == '[') {
                inBrackets++;
            } else if (ch == ']') {
                inBrackets--;
            } else if (ch == '(' && inBrackets == 0) {
                boolean capture = regex.codePointAt(i + 1) != '?';
                captureStack[captureTos++] = capture;
                if (capture) {
                    nestingTable.put(group, stack[tos - 1]);
                    stack[tos++] = group++;
                }
            } else if (ch == ')' && inBrackets == 0) {
                boolean capture = captureStack[--captureTos];
                if (capture) {
                    tos--;
                }
            }
        }
        return nestingTable;
    }


}

