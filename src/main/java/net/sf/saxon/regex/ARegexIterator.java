////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.z.IntHashMap;
import net.sf.saxon.z.IntToIntHashMap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Class ARegexIterator - provides an iterator over matched and unmatched substrings.
 * This implementation of RegexIterator uses the modified Jakarta regular expression engine.
 */

public class ARegexIterator implements RegexIterator, LastPositionFinder {

    private UnicodeString theString;   // the input string being matched
    private UnicodeString regex;
    private REMatcher matcher;    // the Matcher object that does the matching, and holds the state
    private UnicodeString current;     // the string most recently returned by the iterator
    private UnicodeString next;        // if the last string was a matching string, null; otherwise the next substring
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
     * @param string  the string to be analysed
     * @param matcher a matcher for the regular expression
     */

    public ARegexIterator(UnicodeString string, UnicodeString regex, REMatcher matcher) {
        theString = string;
        this.regex = regex;
        this.matcher = matcher;
        next = null;
    }

    @Override
    public int getLength() throws XPathException {
        ARegexIterator another = new ARegexIterator(theString, regex, new REMatcher(matcher.getProgram()));
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
    public StringValue next() throws XPathException {
        try {
            if (next == null && prevEnd >= 0) {
                // we've returned a match (or we're at the start), so find the next match
                int searchStart = prevEnd;
                if (skip) {
                    // previous match was zero-length
                    searchStart++;
                    if (searchStart >= theString.uLength()) {
                        if (prevEnd < theString.uLength()) {
                            current = theString.uSubstring(prevEnd, theString.uLength());
                            next = null;
                        } else {
                            current = null;
                            prevEnd = -1;
                            return null;
                        }
                    }
                }
                if (matcher.match(theString, searchStart)) {
                    int start = matcher.getParenStart(0);
                    int end = matcher.getParenEnd(0);
                    skip = start == end;
                    if (prevEnd == start) {
                        // there's no intervening non-matching string to return
                        next = null;
                        current = theString.uSubstring(start, end);
                        prevEnd = end;
                    } else {
                        // return the non-matching substring first
                        current = theString.uSubstring(prevEnd, start);
                        next = theString.uSubstring(start, end);
                    }
                } else {
                    // there are no more regex matches, we must return the final non-matching text if any
                    if (prevEnd < theString.uLength()) {
                        current = theString.uSubstring(prevEnd, theString.uLength());
                        next = null;
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
                    current = next;
                    next = null;
                    prevEnd = matcher.getParenEnd(0);
                } else {
                    current = null;
                    return null;
                }
            }
            return currentStringValue();
        } catch (StackOverflowError e) {
            throw new XPathException.StackOverflow(
                    "Stack overflow (excessive recursion) during regular expression evaluation",
                    SaxonErrorCode.SXRE0001, Loc.NONE);
        }
    }

    private StringValue currentStringValue() {
        return StringValue.makeStringValue(current);
    }


    /**
     * Get properties of this iterator, as a bit-significant integer.
     *
     * @return the properties of this iterator. This will be some combination of
     *         properties such as {@link net.sf.saxon.om.SequenceIterator.Property#GROUNDED}, {@link net.sf.saxon.om.SequenceIterator.Property#LAST_POSITION_FINDER},
     *         and {@link net.sf.saxon.om.SequenceIterator.Property#LOOKAHEAD}. It is always
     *         acceptable to return the value zero, indicating that there are no known special properties.
     *         It is acceptable for the properties of the iterator to change depending on its state.
     */

    @Override
    public EnumSet<Property> getProperties() {
        return EnumSet.of(Property.LAST_POSITION_FINDER);
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
        return next == null && prevEnd >= 0;
    }

    /**
     * Get a substring that matches a parenthesised group within the regular expression
     *
     * @param number the number of the group to be obtained
     * @return the substring of the current item that matches the n'th parenthesized group
     *         within the regular expression
     */

    @Override
    public String getRegexGroup(int number) {
        if (!isMatching()) {
            return null;
        }
        if (number >= matcher.getParenCount() || number < 0) return "";
        UnicodeString us = matcher.getParen(number);
        return (us == null ? "" : us.toString());
    }

    /**
     * Get the number of captured groups
     */
    @Override
    public int getNumberOfGroups() {
        return matcher.getParenCount();
    }

    /**
     * Process a matching substring, performing specified actions at the start and end of each captured
     * subgroup. This method will always be called when operating in "push" mode; it writes its
     * result to context.getReceiver(). The matching substring text is all written to the receiver,
     * interspersed with calls to the {@link net.sf.saxon.regex.RegexIterator.MatchHandler} methods onGroupStart() and onGroupEnd().
     *
     * @param action  defines the processing to be performed at the start and end of a group
     */

    @Override
    public void processMatchingSubstring(MatchHandler action) throws XPathException {
        int c = matcher.getParenCount() - 1;
        if (c == 0) {
            action.characters(current.toString());
        } else {
            // Create a map from positions in the string to lists of actions.
            // The "actions" in each list are: +N: start group N; -N: end group N.
            IntHashMap<List<Integer>> actions = new IntHashMap<List<Integer>>(c);
            for (int i = 1; i <= c; i++) {
                int start = matcher.getParenStart(i) - matcher.getParenStart(0);
                if (start != -1) {
                    int end = matcher.getParenEnd(i) - matcher.getParenStart(0);
                    if (start < end) {
                        // Add the start action after all other actions on the list for the same position
                        List<Integer> s = actions.get(start);
                        if (s == null) {
                            s = new ArrayList<Integer>(4);
                            actions.put(start, s);
                        }
                        s.add(i);
                        // Add the end action before all other actions on the list for the same position
                        List<Integer> e = actions.get(end);
                        if (e == null) {
                            e = new ArrayList<Integer>(4);
                            actions.put(end, e);
                        }
                        e.add(0, -i);
                    } else {
                        // zero-length group (start==end). The problem here is that the information available
                        // from Java isn't sufficient to determine the nesting of groups: match("a", "(a(b?))")
                        // and match("a", "(a)(b?)") will both give the same result for group 2 (start=1, end=1).
                        // So we need to go back to the original regex to determine the group nesting
                        if (nestingTable == null) {
                            nestingTable = computeNestingTable(regex);
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
            FastStringBuffer buff = new FastStringBuffer(current.uLength());
            for (int i = 0; i < current.uLength() + 1; i++) {
                List<Integer> events = actions.get(i);
                if (events != null) {
                    if (buff.length() > 0) {
                        action.characters(buff);
                        buff.setLength(0);
                    }
                    for (Integer group : events) {
                        if (group > 0) {
                            action.onGroupStart(group);
                        } else {
                            action.onGroupEnd(-group);
                        }
                    }
                }
                if (i < current.uLength()) {
                    buff.appendWideChar(current.uCharAt(i));
                }
            }
            if (buff.length() > 0) {
                action.characters(buff);
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
        int[] stack = new int[regex.uLength()];
        int tos = 0;
        boolean[] captureStack = new boolean[regex.uLength()];
        int captureTos = 0;
        int group = 1;
        int inBrackets = 0;
        stack[tos++] = 0;
        for (int i = 0; i < regex.uLength(); i++) {
            int ch = regex.uCharAt(i);
            if (ch == '\\') {
                i++;
            } else if (ch == '[') {
                inBrackets++;
            } else if (ch == ']') {
                inBrackets--;
            } else if (ch == '(' && inBrackets == 0) {
                boolean capture = regex.uCharAt(i + 1) != '?';
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

