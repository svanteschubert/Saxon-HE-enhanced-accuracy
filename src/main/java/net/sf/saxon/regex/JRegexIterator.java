////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.functions.Count;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.z.IntHashMap;
import net.sf.saxon.z.IntToIntHashMap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;

/**
 * Class JRegexIterator - provides an iterator over matched and unmatched substrings.
 * This implementation of RegexIterator uses the JDK regular expression engine.
 */

public class JRegexIterator implements RegexIterator, LastPositionFinder {

    private String theString;   // the input string being matched
    private Pattern pattern;    // the regex against which the string is matched
    private Matcher matcher;    // the Matcher object that does the matching, and holds the state
    /*@Nullable*/ private String current;     // the string most recently returned by the iterator
    private String next;        // if the last string was a matching string, null; otherwise the next substring
    //        matched by the regex
    private int prevEnd = 0;    // the position in the input string of the end of the last match or non-match
    private IntToIntHashMap nestingTable = null;
    // evaluated on demand: a table that indicates for each captured group,
    // what its immediately-containing captured group is.

    /**
     * Construct a RegexIterator. Note that the underlying matcher.find() method is called once
     * to obtain each matching substring. But the iterator also returns non-matching substrings
     * if these appear between the matching substrings.
     *
     * @param string  the string to be analysed
     * @param pattern the regular expression
     */

    public JRegexIterator(String string, Pattern pattern) {
        theString = string;
        this.pattern = pattern;
        matcher = pattern.matcher(string);
        next = null;
    }

    @Override
    public int getLength() throws XPathException {
        JRegexIterator another = new JRegexIterator(theString, pattern);
        return Count.steppingCount(another);
    }


    /**
     * Get the next item in the sequence
     *
     * @return the next item in the sequence
     */

    @Override
    public StringValue next() {
        if (next == null && prevEnd >= 0) {
            // we've returned a match (or we're at the start), so find the next match
            if (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                if (prevEnd == start) {
                    // there's no intervening non-matching string to return
                    next = null;
                    current = theString.substring(start, end);
                    prevEnd = end;
                } else {
                    // return the non-matching substring first
                    current = theString.substring(prevEnd, start);
                    next = theString.substring(start, end);
                }
            } else {
                // there are no more regex matches, we must return the final non-matching text if any
                if (prevEnd < theString.length()) {
                    current = theString.substring(prevEnd);
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
                prevEnd = matcher.end();
            } else {
                current = null;
                return null;
            }
        }
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
        if (!isMatching()) return null;
        if (number > matcher.groupCount() || number < 0) return "";
        String s = matcher.group(number);
        if (s == null) return "";
        return s;
    }

    /**
     * Get the number of captured groups
     */
    @Override
    public int getNumberOfGroups() {
        return matcher.groupCount();
    }

    /**
     * Process a matching substring, performing specified actions at the start and end of each captured
     * subgroup. This method will always be called when operating in "push" mode; it writes its
     * result to context.getReceiver(). The matching substring text is all written to the receiver,
     * interspersed with calls to the methods onGroupStart() and onGroupEnd().
     *
     * @param action  defines the processing to be performed at the start and end of a group
     */

    @Override
    public void processMatchingSubstring(MatchHandler action) throws XPathException {
        int c = matcher.groupCount();
        if (c == 0) {
            action.characters(current);
        } else {
            // Create a map from positions in the string to lists of actions.
            // The "actions" in each list are: +N: start group N; -N: end group N.
            IntHashMap<List<Integer>> actions = new IntHashMap<List<Integer>>(c);
            for (int i = 1; i <= c; i++) {
                int start = matcher.start(i) - matcher.start();
                if (start != -1) {
                    int end = matcher.end(i) - matcher.start();
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
                            computeNestingTable();
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
            FastStringBuffer buff = new FastStringBuffer(current.length());
            for (int i = 0; i < current.length() + 1; i++) {
                List<Integer> events = actions.get(i);
                if (events != null) {
                    if (buff.length() > 0) {
                        action.characters(buff);
                        buff.setLength(0);
                    }
                    for (Iterator<Integer> ii = events.iterator(); ii.hasNext(); ) {
                        int group = ii.next();
                        if (group > 0) {
                            action.onGroupStart(group);
                        } else {
                            action.onGroupEnd(-group);
                        }
                    }
                }
                if (i < current.length()) {
                    buff.cat(current.charAt(i));
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

    private void computeNestingTable() {
        nestingTable = new IntToIntHashMap(16);
        String s = pattern.pattern();
        int[] stack = new int[s.length()];
        int tos = 0;
        int group = 1;
        int inBrackets = 0;
        stack[tos++] = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\'') {
                i++;
            } else if (ch == '[') {
                inBrackets++;
            } else if (ch == ']') {
                inBrackets--;
            } else if (ch == '(' && s.charAt(i + 1) != '?' && inBrackets == 0) {
                nestingTable.put(group, stack[tos - 1]);
                stack[tos++] = group++;
            } else if (ch == ')' && inBrackets == 0) {
                tos--;
            }
        }
    }


}

