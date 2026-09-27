////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Originally part of Apache's Jakarta project (downloaded January 2012),
 * this file has been extensively modified for integration into Saxon by
 * Michael Kay, Saxonica.
 */

package net.sf.saxon.regex;


import net.sf.saxon.str.*;
import net.sf.saxon.z.IntIterator;
import net.sf.saxon.z.IntPredicateProxy;

import java.util.*;
import java.util.function.BiFunction;


/**
 * Evaluator for regular expressions. A regular expression is represented as
 * an {@link REProgram}, which is constructed using the {@link RECompiler}.
 *
 * <p>Although the regular expression engine was originally based on
 * Apache Jakarta, the run-time evaluator has been completely re-written for Saxon.</p>
 *
 * <p>The design is essentially following the interpreter pattern. The compiled
 * regular expression is represented as a tree of {@link Operation} objects,
 * each of which has an evaluation method {@link Operation#iterateMatches(REMatcher, int)}.
 * This takes as input the current position in the input string, and returns an iterator
 * over the possible positions at which a match using this operation can end.</p>
 *
 * <p>The {@link REMatcher} is stateful</p>
 */
public class REMatcher {

    // State of current program
    REProgram program;                            // Compiled regular expression 'program'
    UnicodeString search;                           // The string being matched against
    History history = new History();
    int maxParen;

    // Parenthesized subexpressions
    CapturedGroupState _captureState;

    // Backreferences
    int[] startBackref;                 // Lazily-allocated array of backref starts
    int[] endBackref;                   // Lazily-allocated array of backref ends

    // MHK note 2025-03-08 It's not clear to me why the backreferences is a separate data
    // structure from the captured group state. There seem to be some subtle differences in
    // the way the two structures are updated when backtracking.

    Operation operation;                // the root of the operation tree
    boolean anchoredMatch;              // true if matches are implicitly anchored, as in XSD


    /**
     * Construct a matcher for a pre-compiled regular expression from program
     * (bytecode) data.
     *
     * @param program Compiled regular expression program
     * @see RECompiler
     */
    public REMatcher(REProgram program) {
        Objects.requireNonNull(program);
        this.program = program;
        this.operation = program.operation;
        this.maxParen = program.maxParens;
        this._captureState = new CapturedGroupState(maxParen);
    }

    /**
     * Returns the current regular expression program in use by this matcher object.
     * @return Regular expression program
     */
    public REProgram getProgram() {
        return program;
    }

    /**
     * Returns the number of parenthesized subexpressions available after a successful match.
     *
     * @return Number of available parenthesized subexpressions
     */
    public int getParenCount() {
        return _captureState.parenCount;
    }

    /**
     * Gets the contents of a parenthesized subexpression after a successful match.
     *
     * @param which Nesting level of subexpression
     * @return String
     */
    public UnicodeString getParen(int which) {
        int start;
        if (which < _captureState.parenCount && (start = getParenStart(which)) >= 0) {
            return search.substring(start, getParenEnd(which));
        }
        return null;
    }

    /**
     * Returns the start index of a given paren level.
     *
     * @param which Nesting level of subexpression
     * @return String index
     */
    public final int getParenStart(int which) {
        if (which < _captureState.startn.length) {
            return _captureState.startn[which];
        }
        return -1;
    }

    /**
     * Returns the end index of a given paren level.
     *
     * @param which Nesting level of subexpression
     * @return String index
     */
    public final int getParenEnd(int which) {
        if (which < _captureState.endn.length) {
            return _captureState.endn[which];
        }
        return -1;
    }

    /**
     * Sets a captured group
     *
     * @param which Which paren level
     * @param start     start index in input string
     * @param end       end index in input string
     * @param inLookahead true if the match is within a lookahead assertion
     */

    protected final void capture(int which, int start, int end, boolean inLookahead) {
        _captureState.startn[which] = start;
        _captureState.endn[which] = end;
        if (inLookahead) {
            _captureState.inLookahead.set(which);
        }
    }

    /**
     * Clear any captured groups whose start position is at or beyond some specified position
     * @param pos the specified position
     */

    protected void clearCapturedGroupsBeyond(int pos) {
        for (int i = 0; i < _captureState.startn.length; i++) {
            if (_captureState.startn[i] >= pos && !_captureState.inLookahead.get(i)) {
                _captureState.endn[i] = _captureState.startn[i];
            }
        }
        if (startBackref != null) {
            for (int i = 0; i < startBackref.length; i++) {
                if (startBackref[i] >= pos && !_captureState.inLookahead.get(i)) {
                    endBackref[i] = startBackref[i];
                }
            }
        }
    }

    /**
     * Match the current regular expression program against the current
     * input string, starting at index i of the input string.  This method
     * is only meant for internal use.
     *
     * @param i        The input string index to start matching at
     * @param anchored true if the regex must match all characters up to the end of the string
     * @return True if the input matched the expression
     */
    protected boolean matchAt(int i, boolean anchored) {
        // Initialize start pointer, paren cache and paren count
        _captureState.parenCount = 1;
        anchoredMatch = anchored;

        // Allocate backref arrays (unless optimizations indicate otherwise)
        if ((program.optimizationFlags & REProgram.OPT_HASBACKREFS) != 0) {
            startBackref = new int[maxParen];
            endBackref = new int[maxParen];
        }

        // Match against string
        int idx;
        IntIterator iter = operation.iterateMatches(this, i);
        if (iter.hasNext()) {
            idx = iter.next();
            capture(0, i, idx, false);
            return true;
        }

        // Didn't match
        _captureState.parenCount = 0;
        return false;
    }

    /**
     * Tests whether the regex matches a string in its entirety, anchored
     * at both ends
     *
     * @param search the string to be matched
     * @return true if the regex matches the whole string
     */

    public boolean isAnchoredMatch(UnicodeString search) {
        this.search = search;
        return matchAt(0, true);
    }

    /**
     * Matches the current regular expression program against a character array,
     * starting at a given index.
     *
     * @param search String to match against
     * @param i      Index to start searching at
     * @return True if string matched
     */
    public boolean match(UnicodeString search, int i) {
        //System.err.println("Matching '" + search + "'");
        Objects.requireNonNull(search);
        // Save string to search
        this.search = search.tidy();

        // Clear the captured group state
        _captureState = new CapturedGroupState(maxParen);

        // Can we optimize the search by looking for new lines?
        if ((program.optimizationFlags & REProgram.OPT_HASBOL) == REProgram.OPT_HASBOL) {
            // Non multi-line matching with BOL: Must match at '0' index
            if (!program.flags.isMultiLine()) {
                return i == 0 && checkPreconditions(i) && matchAt(i, false);
            }

            // Multi-line matching with BOL: Seek to next line
            int nl = i;
            if (matchAt(nl, false)) {
                return true;
            }
            while (true) {
                nl = (int)search.indexOf('\n', nl) + 1;
                if (nl >= search.length() || nl <= 0) {
                    return false; // "^" does not match a NL at the end of the string
                } else {
                    if (matchAt(nl, false)) {
                        return true;
                    }
                }
            }
        }

        // Is the string long enough to match?
        int actualLength = search.length32() - i;
        if (actualLength < program.minimumLength) {
            return false;
        }

        // Can we optimize the search by looking for a prefix string?
        if (program.prefix == null) {
            if (program.initialCharClass != null) {
                // no prefix known; but the first character must match a predicate
                IntPredicateProxy pred = program.initialCharClass;
                for (; !(i >= search.length32()); i++) {
                    if (pred.test(search.codePointAt(i))) {
                        if (matchAt(i, false)) {
                            return true;
                        }
                    }
                }
                return false;
            }
            // Check the preconditions
            if (!checkPreconditions(i)) {
                return false;
            }
            // Unprefixed matching must try for a match at each character
            for (; (i <= search.length32()); i++) {
                // Try a match at index i
                if (matchAt(i, false)) {
                    return true;
                }
            }
            return false;
        } else {
            // Prefix-anchored matching is possible
            UnicodeString prefix = program.prefix;
            int prefixLength = prefix.length32();
            boolean ignoreCase = program.flags.isCaseIndependent();
            for (; !(i + prefixLength - 1 >= search.length()); i++) {
                boolean prefixOK = true;
                if (ignoreCase) {
                    for (int j = i, k = 0; k < prefixLength; j++, k++) {
                        if (!equalCaseBlind(search.codePointAt(j), prefix.codePointAt(k))) {
                            prefixOK = false;
                            break;
                        }
                    }
                } else {
                    for (int j = i, k=0; k < prefixLength; j++, k++) {
                        if (search.codePointAt(j) != prefix.codePointAt(k)) {
                            prefixOK = false;
                            break;
                        }
                    }
                }

                // See if the whole prefix string matched
                if (prefixOK) {
                    // We matched the full prefix at firstChar, so try it
                    if (matchAt(i, false)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * Check the preconditions for a match, testing the precondition at every position
     * from some start point
     * @param start the start position for matching preconditions
     *
     */

    private boolean checkPreconditions(int start) {
        for (RegexPrecondition condition : program.preconditions) {
            if (condition.fixedPosition != -1) {
                boolean match = condition.operation.iterateMatches(this, condition.fixedPosition).hasNext();
                if (!match) {
                    return false;
                }
            } else {
                int i = start;
                if (i < condition.minPosition) {
                    i = condition.minPosition;
                }
                boolean found = false;
                for (; !(i >= search.length()); i++) {
                    if ((condition.fixedPosition == -1 || condition.fixedPosition == i) &&
                        condition.operation.iterateMatches(this, i).hasNext()) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Matches the current regular expression program against a String.
     *
     * @param search String to match against
     * @return True if string matched
     */
    public boolean match(String search) {
        return match(StringView.of(search).tidy(), 0);
    }

    /**
     * Splits a string into an array of strings on regular expression boundaries.
     * This function works the same way as the Perl function of the same name.
     * Given a regular expression of "[ab]+" and a string to split of
     * "xyzzyababbayyzabbbab123", the result would be the array of Strings
     * "[xyzzy, yyz, 123]".
     * <p>Please note that the first string in the resulting array may be an empty
     * string. This happens when the very first character of input string is
     * matched by the pattern.</p>
     *
     * @param s String to split on this regular exression
     * @return A list of strings
     */
    public List<UnicodeString> split(UnicodeString s) {
        // Create new vector
        List<UnicodeString> v = new ArrayList<>();

        // Start at position 0 and search the whole string
        int pos = 0;
        int len = s.length32();

        // Try a match at each position
        while (pos < len && match(s, pos)) {
            // Get start of match
            int start = getParenStart(0);

            // Get end of match
            int newpos = getParenEnd(0);

            // Check if no progress was made
            if (newpos == pos) {
                v.add(s.substring(pos, start + 1));
                newpos++;
            } else {
                v.add(s.substring(pos, start));
            }

            // Move to new position
            pos = newpos;
        }

        // Push remainder even if it's empty
        UnicodeString remainder = s.substring(pos, len);
        v.add(remainder);

        // Return the list
        return v;
    }

    /**
     * Substitutes a string for this regular expression in another string.
     * This method works like the Perl function of the same name.
     * Given a regular expression of "a*b", a String to substituteIn of
     * "aaaabfooaaabgarplyaaabwackyb" and the substitution String "-", the
     * resulting String returned by subst would be "-foo-garply-wacky-".
     * <p>It is also possible to reference the contents of a parenthesized expression
     * with $0, $1, ... $9. A regular expression of "http://[\\.\\w\\-\\?/~_@&amp;=%]+",
     * a String to substituteIn of "visit us: http://www.apache.org!" and the
     * substitution String "&lt;a href=\"$0\"&gt;$0&lt;/a&gt;", the resulting String
     * returned by subst would be
     * "visit us: &lt;a href=\"http://www.apache.org\"&gt;http://www.apache.org&lt;/a&gt;!".</p>
     * <p><i>Note:</i> $0 represents the whole match.</p>
     *
     * @param in          String to substitute within
     * @param replacement String to substitute for matches of this regular expression
     * @return The string substituteIn with zero or more occurrences of the current
     *         regular expression replaced with the substitution String (if this regular
     *         expression object doesn't match at any position, the original String is returned
     *         unchanged).
     */
    public UnicodeString replace(UnicodeString in, UnicodeString replacement) {
        // String to return
        UnicodeString result = EmptyUnicodeString.getInstance();

        // Start at position 0 and search the whole string
        int pos = 0;
        int len = in.length32();

        boolean firstMatch = true;
        boolean simpleReplacement = false;

        // Try a match at each position including the position after the last character
        while (pos <= len && match(in, pos)) {
            // Append chars from input string before match
            result = result.concat(in.substring(pos, getParenStart(0)));

            if (firstMatch) {
                simpleReplacement = program.flags.isLiteral() ||
                        (replacement.indexOf('$') < 0 && replacement.indexOf('\\') < 0);
                firstMatch = false;
            }

            if (!simpleReplacement) {
                // Process references to captured substrings
                int maxCapture = program.maxParens - 1;
                simpleReplacement = true;
                for (int i = 0; i < replacement.length(); i++) {
                    int ch = replacement.codePointAt(i);
                    if (ch == '\\') {
                        simpleReplacement = false;
                        int index = ++i;
                        ch = replacement.codePointAt(index);
                        if (ch == '\\' || ch == '$') {
                            result = result.concat(new UnicodeChar(ch));
                        } else {
                            throw new RESyntaxException("Invalid escape '" + ch + "' in replacement string");
                        }
                    } else if (ch == '$') {
                        simpleReplacement = false;
                        int index = ++i;
                        ch = replacement.codePointAt(index);
                        if (!(ch >= '0' && ch <= '9')) {
                            throw new RESyntaxException("$ in replacement string must be followed by a digit");
                        }
                        int n = ch - '0';
                        if (maxCapture <= 9) {
                            if (maxCapture >= n) {
                                UnicodeString captured = getParen(n);
                                if (captured != null) {
                                    result = result.concat(captured);
                                }
                            }
                        } else {
                            while (true) {
                                if (++i >= replacement.length()) {
                                    break;
                                }
                                ch = replacement.codePointAt(i);
                                if (ch >= '0' && ch <= '9') {
                                    int m = n * 10 + (ch - '0');
                                    if (m > maxCapture) {
                                        i--;
                                        break;
                                    } else {
                                        n = m;
                                    }
                                } else {
                                    i--;
                                    break;
                                }
                            }
                            UnicodeString captured = getParen(n);
                            if (captured != null) {
                                result = result.concat(captured);
                            }
                        }
                    } else {
                        result = result.concat(new UnicodeChar(ch));
                    }
                }

            } else {
                // Append substitution without processing backreferences
                result = result.concat(replacement);
            }

            // Move forward, skipping past match
            int newpos = getParenEnd(0);

            // We always want to make progress!
            if (newpos == pos || newpos == getParenStart(0)) {
                if (newpos == in.length32()) {
                    pos = newpos;
                    break;
                }
                result = result.concat(new UnicodeChar(in.codePointAt(newpos)));
                newpos++;
            } 

            // Try new position
            pos = newpos;

        }

        // If no matches were found, return the input unchanged
        if (firstMatch) {
            return in;
        }

        // If there's remaining input, append it
        result = result.concat(in.substring(pos, len));

        // Return string buffer
        return result.economize();
    }

    /**
     * Substitutes a string for this regular expression in another string.
     * This method works like the Perl function of the same name.
     * Given a regular expression of "a*b", a String to substituteIn of
     * "aaaabfooaaabgarplyaaabwackyb" and the substitution String "-", the
     * resulting String returned by subst would be "-foo-garply-wacky-".
     * <p>It is also possible to reference the contents of a parenthesized expression
     * with $0, $1, ... $9. A regular expression of "http://[\\.\\w\\-\\?/~_@&amp;=%]+",
     * a String to substituteIn of "visit us: http://www.apache.org!" and the
     * substitution String "&lt;a href=\"$0\"&gt;$0&lt;/a&gt;", the resulting String
     * returned by subst would be
     * "visit us: &lt;a href=\"http://www.apache.org\"&gt;http://www.apache.org&lt;/a&gt;!".</p>
     * <p><i>Note:</i> $0 represents the whole match.</p>
     *
     * @param in          String to substitute within
     * @param replacer    Function to process each matching substring and return a replacement
     * @return The string substituteIn with zero or more occurrences of the current
     * regular expression replaced with the substitution String (if this regular
     * expression object doesn't match at any position, the original String is returned
     * unchanged).
     */
    public UnicodeString replaceWith(UnicodeString in, BiFunction<UnicodeString, UnicodeString[], UnicodeString> replacer) {
        // String to return
        UnicodeBuilder sb = new UnicodeBuilder();

        // Start at position 0 and search the whole string
        int pos = 0;
        int len = in.length32();

        // Try a match at each position
        while (pos <= len && match(in, pos)) {
            // Append chars from input string before match
            for (long i = pos; i < getParenStart(0); i++) {
                sb.append(in.codePointAt(i));
            }
            UnicodeString matchingSubstring = in.substring(getParenStart(0), getParenEnd(0));
            int nrOfGroups = program.maxParens - 1;
            UnicodeString[] groups = new UnicodeString[nrOfGroups];
            for (int i=0; i<nrOfGroups; i++) {
                groups[i] = getParen(i+1);
                if (groups[i] == null) {
                    groups[i] = EmptyUnicodeString.getInstance();
                }
            }
            UnicodeString replacement = replacer.apply(matchingSubstring, groups);
            IntIterator iter = replacement.codePoints();
            while (iter.hasNext()) {
                sb.append(iter.next());
            }

            // Move forward, skipping past match
            int newpos = getParenEnd(0);

            // We always want to make progress!
            if (newpos == pos) {
                newpos++;
            }

            // Try new position
            pos = newpos;

        }

        // If there's remaining input, append it
        for (int i = pos; i < len; i++) {
            sb.append(in.codePointAt(i));
        }

        // Return string buffer
        return sb.toUnicodeString();
    }


    /**
     * Test whether the character at a given position is a newline
     *
     * @param i the position of the character to be tested
     * @return true if character at i-th position in the <code>search</code> string is a newline
     */
    boolean isNewline(int i) {
        return search.codePointAt(i) == '\n';
    }

    /**
     * Compares two characters ignoring case.
     *
     * @param c1 first character to compare.
     * @param c2 second character to compare.
     * @return true the first character is equal to the second ignoring case.
     */
    boolean equalCaseBlind(int c1, int c2) {
        if (c1 == c2) {
            return true;
        }
        for (int v : CaseVariants.getCaseVariants(c2)) {
            if (c1 == v) {
                return true;
            }
        }
        return false;
    }

    public CapturedGroupState captureState() {
        return new CapturedGroupState(_captureState);
    }

    public void resetState(CapturedGroupState state) {
        _captureState = new CapturedGroupState(state);
    }

    public static class CapturedGroupState {
        int parenCount;                     // Number of subexpressions matched (num open parens + 1)
        int[] startn;                       // array of sub-expression starts
        int[] endn;                         // array of sub-expression ends
        BitSet inLookahead;

        public CapturedGroupState(int maxParens) {
            parenCount = maxParens;
            startn = new int[maxParens];
            Arrays.fill(startn, -1);
            endn = new int[maxParens];
            Arrays.fill(endn, -1);
            inLookahead = new BitSet(maxParens);
        }

        public CapturedGroupState(CapturedGroupState s) {
            parenCount = s.parenCount;
            startn = Arrays.copyOf(s.startn, s.startn.length);
            endn = Arrays.copyOf(s.endn, s.endn.length);
            inLookahead = (BitSet)s.inLookahead.clone();
        }
    }
}
