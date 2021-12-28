////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
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


import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.z.IntIterator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntPredicate;


/**
 * RE is an efficient, lightweight regular expression evaluator/matcher
 * class. Regular expressions are pattern descriptions which enable
 * sophisticated matching of strings.  In addition to being able to
 * match a string against a pattern, you can also extract parts of the
 * match.  This is especially useful in text parsing! Details on the
 * syntax of regular expression patterns are given below.
 * <p>To compile a regular expression (RE), you can simply construct an RE
 * matcher object from the string specification of the pattern, like this:</p>
 * <pre>
 *  RE r = new RE("a*b");
 * </pre>
 * <p>Once you have done this, you can call either of the RE.match methods to
 * perform matching on a String.  For example:</p>
 * <pre>
 *  boolean matched = r.match("aaaab");
 * </pre>
 * <p>will cause the boolean matched to be set to true because the
 * pattern "a*b" matches the string "aaaab".</p>
 * <p>If you were interested in the <i>number</i> of a's which matched the
 * first part of our example expression, you could change the expression to
 * "(a*)b".  Then when you compiled the expression and matched it against
 * something like "xaaaab", you would get results like this:</p>
 * <pre>
 *  RE r = new RE("(a*)b");                  // Compile expression
 *  boolean matched = r.match("xaaaab");     // Match against "xaaaab"
 *
 *  String wholeExpr = r.getParen(0);        // wholeExpr will be 'aaaab'
 *  String insideParens = r.getParen(1);     // insideParens will be 'aaaa'
 *
 *  int startWholeExpr = r.getParenStart(0); // startWholeExpr will be index 1
 *  int endWholeExpr = r.getParenEnd(0);     // endWholeExpr will be index 6
 *  int lenWholeExpr = r.getParenLength(0);  // lenWholeExpr will be 5
 *
 *  int startInside = r.getParenStart(1);    // startInside will be index 1
 *  int endInside = r.getParenEnd(1);        // endInside will be index 5
 *  int lenInside = r.getParenLength(1);     // lenInside will be 4
 * </pre>
 * <p>You can also refer to the contents of a parenthesized expression
 * within a regular expression itself.  This is called a
 * 'backreference'.  The first backreference in a regular expression is
 * denoted by \1, the second by \2 and so on.  So the expression:</p>
 * <pre>
 *  ([0-9]+)=\1
 * </pre>
 * <p>will match any string of the form n=n (like 0=0 or 2=2).</p>
 * <p>The full regular expression syntax accepted by RE is as defined in the XSD 1.1
 * specification, modified by the XPath 2.0 or 3.0 specifications.</p>
 * <p><b>Line terminators</b></p>
 * <p>A line terminator is a one- or two-character sequence that marks
 * the end of a line of the input character sequence. The following
 * are recognized as line terminators:</p>
 * <ul>
 * <li>A newline (line feed) character ('\n'),</li>
 * <li>A carriage-return character followed immediately by a newline character ("\r\n"),</li>
 * <li>A standalone carriage-return character ('\r'),</li>
 * <li>A next-line character ('\u0085'),</li>
 * <li>A line-separator character ('\u2028'), or</li>
 * <li>A paragraph-separator character ('\u2029).</li>
 * </ul>
 * <p>RE runs programs compiled by the RECompiler class.  But the RE
 * matcher class does not include the actual regular expression compiler
 * for reasons of efficiency.  In fact, if you want to pre-compile one
 * or more regular expressions, the 'recompile' class can be invoked
 * from the command line to produce compiled output like this:</p>
 * <pre>
 *    // Pre-compiled regular expression "a*b"
 *    char[] re1Instructions =
 *    {
 *        0x007c, 0x0000, 0x001a, 0x007c, 0x0000, 0x000d, 0x0041,
 *        0x0001, 0x0004, 0x0061, 0x007c, 0x0000, 0x0003, 0x0047,
 *        0x0000, 0xfff6, 0x007c, 0x0000, 0x0003, 0x004e, 0x0000,
 *        0x0003, 0x0041, 0x0001, 0x0004, 0x0062, 0x0045, 0x0000,
 *        0x0000,
 *    };
 *
 *
 *    REProgram re1 = new REProgram(re1Instructions);
 * </pre>
 * <p>You can then construct a regular expression matcher (RE) object from
 * the pre-compiled expression re1 and thus avoid the overhead of
 * compiling the expression at runtime. If you require more dynamic
 * regular expressions, you can construct a single RECompiler object and
 * re-use it to compile each expression. Similarly, you can change the
 * program run by a given matcher object at any time. However, RE and
 * RECompiler are not threadsafe (for efficiency reasons, and because
 * requiring thread safety in this class is deemed to be a rare
 * requirement), so you will need to construct a separate compiler or
 * matcher object for each thread (unless you do thread synchronization
 * yourself). Once expression compiled into the REProgram object, REProgram
 * can be safely shared across multiple threads and RE objects.</p>
 * <p><i>ISSUES:</i></p>
 * <ul>
 * <li>Not *all* possibilities are considered for greediness when backreferences
 * are involved (as POSIX suggests should be the case).  The POSIX RE
 * "(ac*)c*d[ac]*\1", when matched against "acdacaa" should yield a match
 * of acdacaa where \1 is "a".  This is not the case in this RE package,
 * and actually Perl doesn't go to this extent either!  Until someone
 * actually complains about this, I'm not sure it's worth "fixing".
 * If it ever is fixed, test #137 in RETest.txt should be updated.</li>
 * </ul>
 * <p>This library is based on the Apache Jakarta regex library as downloaded
 * on 3 January 2012. Changes have been made to make the grammar and semantics conform to XSD
 * and XPath rules; these changes are listed in source code comments in the
 * RECompiler source code module.</p>
 *
 * @author <a href="mailto:jonl@muppetlabs.com">Jonathan Locke</a>
 * @author <a href="mailto:ts@sch-fer.de">Tobias Sch&auml;fer</a>
 * @author <a href="mailto:mike@saxonica.com">Michael Kay</a>
 * @see RECompiler
 */
public class REMatcher {

    // Limits
    static final int MAX_PAREN = 16;              // Number of paren pairs

    // State of current program
    REProgram program;                            // Compiled regular expression 'program'
    UnicodeString search;               // The string being matched against
    History history = new History();
    int maxParen = MAX_PAREN;

    // Parenthesized subexpressions
    State captureState = new State();

    // Backreferences
    int[] startBackref;                 // Lazily-allocated array of backref starts
    int[] endBackref;                   // Lazily-allocated array of backref ends

    Operation operation;
    boolean anchoredMatch;


    /**
     * Construct a matcher for a pre-compiled regular expression from program
     * (bytecode) data.
     *
     * @param program Compiled regular expression program
     * @see RECompiler
     */
    public REMatcher(REProgram program) {
        setProgram(program);
    }

    /**
     * Sets the current regular expression program used by this matcher object.
     *
     * @param program Regular expression program compiled by RECompiler.
     * @see RECompiler
     * @see REProgram
     */
    public void setProgram(REProgram program) {
        this.program = program;
        if (program != null && program.maxParens != -1) {
            this.operation = program.operation;
            this.maxParen = program.maxParens;
        } else {
            this.maxParen = MAX_PAREN;
        }
    }

    /**
     * Returns the current regular expression program in use by this matcher object.
     *
     * @return Regular expression program
     * @see #setProgram
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
        return captureState.parenCount;
    }

    /**
     * Gets the contents of a parenthesized subexpression after a successful match.
     *
     * @param which Nesting level of subexpression
     * @return String
     */
    public UnicodeString getParen(int which) {
        int start;
        if (which < captureState.parenCount && (start = getParenStart(which)) >= 0) {
            return search.uSubstring(start, getParenEnd(which));
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
        if (which < captureState.startn.length) {
            return captureState.startn[which];
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
        if (which < captureState.endn.length) {
            return captureState.endn[which];
        }
        return -1;
    }

    /**
     * Sets the start of a paren level
     *
     * @param which Which paren level
     * @param i     Index in input array
     */
    protected final void setParenStart(int which, int i) {
        while (which > captureState.startn.length - 1) {
            int[] s2 = new int[captureState.startn.length * 2];
            System.arraycopy(captureState.startn, 0, s2, 0, captureState.startn.length);
            Arrays.fill(s2, captureState.startn.length, s2.length, -1);
            captureState.startn = s2;
        }
        captureState.startn[which] = i;
    }

    /**
     * Sets the end of a paren level
     *
     * @param which Which paren level
     * @param i     Index in input array
     */
    protected final void setParenEnd(int which, int i) {
        while (which > captureState.endn.length - 1) {
            int[] e2 = new int[captureState.endn.length * 2];
            System.arraycopy(captureState.endn, 0, e2, 0, captureState.endn.length);
            Arrays.fill(e2, captureState.endn.length, e2.length, -1);
            captureState.endn = e2;
        }
        captureState.endn[which] = i;
    }

    /**
     * Clear any captured groups whose start position is at or beyond some specified position
     * @param pos the specified position
     */

    protected void clearCapturedGroupsBeyond(int pos) {
        for (int i = 0; i < captureState.startn.length; i++) {
            if (captureState.startn[i] >= pos) {
                captureState.endn[i] = captureState.startn[i];
            }
        }
        if (startBackref != null) {
            for (int i = 0; i < startBackref.length; i++) {
                if (startBackref[i] >= pos) {
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
        captureState.parenCount = 1;
        anchoredMatch = anchored;
        setParenStart(0, i);

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
            setParenEnd(0, idx);
            return true;
        }

        // Didn't match
        captureState.parenCount = 0;
        return false;
    }

    /**
     * Tests whether the regex matches a string in its entirety, anchored
     * at both ends
     *
     * @param search the string to be matched
     * @return true if the regex matches the whols string
     */

    public boolean anchoredMatch(UnicodeString search) {
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

        // Save string to search
        this.search = search;

        // Clear the captured group state
        captureState = new State();

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
                nl = search.uIndexOf('\n', nl) + 1;
                if (nl >= search.uLength() || nl <= 0) {
                    return false; // "^" does not match a NL at the end of the string
                } else {
                    if (matchAt(nl, false)) {
                        return true;
                    }
                }
            }
        }

        // Is the string long enough to match?
        int actualLength = search.uLength() - i;
        if (actualLength < program.minimumLength) {
            return false;
        }

        // Can we optimize the search by looking for a prefix string?
        if (program.prefix == null) {
            if (program.initialCharClass != null) {
                // no prefix known; but the first character must match a predicate
                IntPredicate pred = program.initialCharClass;
                for (; !search.isEnd(i); i++) {
                    if (pred.test(search.uCharAt(i))) {
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
            for (; !search.isEnd(i - 1); i++) {
                // Try a match at index i
                if (matchAt(i, false)) {
                    return true;
                }
            }
            return false;
        } else {
            // Prefix-anchored matching is possible
            UnicodeString prefix = program.prefix;
            int prefixLength = prefix.uLength();
            boolean ignoreCase = program.flags.isCaseIndependent();
            for (; !search.isEnd(i + prefixLength - 1); i++) {
                boolean prefixOK = true;
                if (ignoreCase) {
                    for (int j = i, k = 0; k < prefixLength; j++, k++) {
                        if (!equalCaseBlind(search.uCharAt(j), prefix.uCharAt(k))) {
                            prefixOK = false;
                            break;
                        }
                    }
                } else {
                    for (int j = i, k=0; k < prefixLength; j++, k++) {
                        if (search.uCharAt(j) != prefix.uCharAt(k)) {
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
                for (; !search.isEnd(i); i++) {
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
        UnicodeString uString = UnicodeString.makeUnicodeString(search);
        return match(uString, 0);
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
     * @return Array of strings
     */
    public List<UnicodeString> split(UnicodeString s) {
        // Create new vector
        List<UnicodeString> v = new ArrayList<UnicodeString>();

        // Start at position 0 and search the whole string
        int pos = 0;
        int len = s.uLength();

        // Try a match at each position
        while (pos < len && match(s, pos)) {
            // Get start of match
            int start = getParenStart(0);

            // Get end of match
            int newpos = getParenEnd(0);

            // Check if no progress was made
            if (newpos == pos) {
                v.add(s.uSubstring(pos, start + 1));
                newpos++;
            } else {
                v.add(s.uSubstring(pos, start));
            }

            // Move to new position
            pos = newpos;
        }

        // Push remainder even if it's empty
        UnicodeString remainder = s.uSubstring(pos, len);
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
    public CharSequence replace(UnicodeString in, UnicodeString replacement) {
        // String to return
        FastStringBuffer sb = new FastStringBuffer(in.uLength() * 2);

        // Start at position 0 and search the whole string
        int pos = 0;
        int len = in.uLength();

        // Try a match at each position
        while (pos < len && match(in, pos)) {
            // Append chars from input string before match
            for (int i = pos; i < getParenStart(0); i++) {
                sb.appendWideChar(in.uCharAt(i));
            }

            if (!program.flags.isLiteral()) {
                // Process references to captured substrings
                int maxCapture = program.maxParens - 1;

                for (int i = 0; i < replacement.uLength(); i++) {
                    int ch = replacement.uCharAt(i);
                    if (ch == '\\') {
                        ch = replacement.uCharAt(++i);
                        if (ch == '\\' || ch == '$') {
                            sb.cat((char) ch);
                        } else {
                            throw new RESyntaxException("Invalid escape '" + ch + "' in replacement string");
                        }
                    } else if (ch == '$') {
                        ch = replacement.uCharAt(++i);
                        if (!(ch >= '0' && ch <= '9')) {
                            throw new RESyntaxException("$ in replacement string must be followed by a digit");
                        }
                        int n = ch - '0';
                        if (maxCapture <= 9) {
                            if (maxCapture >= n) {
                                UnicodeString captured = getParen(n);
                                if (captured != null) {
                                    for (int j = 0; j < captured.uLength(); j++) {
                                        sb.appendWideChar(captured.uCharAt(j));
                                    }
                                }
                            } else {
                                // append a zero-length string (no-op)
                            }
                        } else {
                            while (true) {
                                if (++i >= replacement.uLength()) {
                                    break;
                                }
                                ch = replacement.uCharAt(i);
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
                                for (int j = 0; j < captured.uLength(); j++) {
                                    sb.appendWideChar(captured.uCharAt(j));
                                }
                            }
                        }
                    } else {
                        sb.appendWideChar(ch);
                    }
                }

            } else {
                // Append substitution without processing backreferences
                for (int i = 0; i < replacement.uLength(); i++) {
                    sb.appendWideChar(replacement.uCharAt(i));
                }
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
            sb.appendWideChar(in.uCharAt(i));
        }

        // Return string buffer
        return sb.condense();
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
    public CharSequence replaceWith(UnicodeString in, Function<CharSequence, CharSequence> replacer) {
        // String to return
        FastStringBuffer sb = new FastStringBuffer(in.uLength() * 2);

        // Start at position 0 and search the whole string
        int pos = 0;
        int len = in.uLength();

        // Try a match at each position
        while (pos < len && match(in, pos)) {
            // Append chars from input string before match
            for (int i = pos; i < getParenStart(0); i++) {
                sb.appendWideChar(in.uCharAt(i));
            }
            CharSequence matchingSubstring = in.subSequence(getParenStart(0), getParenEnd(0));
            CharSequence replacement = replacer.apply(matchingSubstring);
            sb.append(replacement);

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
            sb.appendWideChar(in.uCharAt(i));
        }

        // Return string buffer
        return sb.condense();
    }


    /**
     * Test whether the character at a given position is a newline
     *
     * @param i the position of the character to be tested
     * @return true if character at i-th position in the <code>search</code> string is a newline
     */
    boolean isNewline(int i) {
        return search.uCharAt(i) == '\n';
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

    public State captureState() {
        return new State(captureState);
    }

    public void resetState(State state) {
        captureState = new State(state);
    }

    public static class State {
        int parenCount;                     // Number of subexpressions matched (num open parens + 1)
        int[] startn;                       // Lazily-allocated array of sub-expression starts
        int[] endn;                         // Lazily-allocated array of sub-expression ends

        public State() {
            parenCount = 0;
            startn = new int[3];
            startn[0] = startn[1] = startn[2] = -1;
            endn = new int[3];
            endn[0] = endn[1] = endn[2] = -1;
        }

        public State(State s) {
            parenCount = s.parenCount;
            startn = Arrays.copyOf(s.startn, s.startn.length);
            endn = Arrays.copyOf(s.endn, s.endn.length);
        }
    };
}
