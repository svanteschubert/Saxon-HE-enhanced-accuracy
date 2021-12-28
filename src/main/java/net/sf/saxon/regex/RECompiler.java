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

import net.sf.saxon.regex.charclass.*;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.Whitespace;
import net.sf.saxon.z.*;

import java.util.function.IntPredicate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A regular expression compiler class.  This class compiles a pattern string into a
 * regular expression program interpretable by the RE evaluator class.  The 'recompile'
 * command line tool uses this compiler to pre-compile regular expressions for use
 * with RE.  For a description of the syntax accepted by RECompiler and what you can
 * do with regular expressions, see the documentation for the RE matcher class.
 *
 * @author <a href="mailto:jonl@muppetlabs.com">Jonathan Locke</a>
 * @author <a href="mailto:gholam@xtra.co.nz">Michael McCallum</a>
 * @version $Id: RECompiler.java 518156 2007-03-14 14:31:26Z vgritsenko $
 * @see net.sf.saxon.regex.REMatcher
 */

/*
 * Changes made for Saxon:
 *
 * - handle full Unicode repertoire (esp non-BMP characters) using UnicodeString class for
 *   both the source string and the regular expression
 * - added support for subtraction in a character class
 * - in a character range, changed the condition start < end to start <= end
 * - removed support for [:POSIX:] construct
 * - added support for \p{} and \P{} classes
 * - removed support for unsupported escapes: f, x, u, b, octal characters; added i and c
 * - changed the handling of hyphens within square brackets, and ^ appearing other than at the start
 * - changed the data structure used for the executable so that terms that match a character class
 *   now reference an IntPredicate that tests for membership of the character in a set
 * - added support for reluctant {n,m}? quantifiers
 * - allow a quantifier on a nullable expression
 * - allow a quantifier on '$' or '^'
 * - some constructs (back-references, non-capturing groups, etc) are conditional on which XPath/XSD version
 *   is in use
 * - regular expression flags are now fixed at the time the RE is compiled, this can no longer be deferred
 *   until the RE is evaluated
 * - split() function includes a zero-length string at the end of the returned sequence if the last
 *   separator is at the end of the string
 * - added support for the 'q' and 'x' flags; improved support for the 'i' flag
 * - added a method to determine whether there is an anchored match (for XSD use)
 * - tests for newline (e.g in multiline mode) now match \n only, as required by the XPath specification
 * - reorganised the executable program to use Operation objects rather than integer opcodes
 * - introduced optimization for non-backtracking + and * operators (with simple operands)
 *
 * Further changes made February 2014:
 * - complete rewrite of the run-time engine to use an interpreter approach directly on the parsed expression
 *   tree, bypassing the generation of a finite state machine. This achieves a substantial reduction in
 *   recursive depth; the old code had one level of recursion per input character in some cases. In addition
 *   the compiled code for expressions involving large finite counters is much more compact.
 */
public class RECompiler {

    // Input state for compiling regular expression
    UnicodeString pattern;                                     // Input string
    int len;                                            // Length of the pattern string
    int idx;                                            // Current input index into ac
    int capturingOpenParenCount;                                         // Total number of paren pairs

    // Node flags
    static final int NODE_NORMAL = 0;                   // No flags (nothing special)
    static final int NODE_TOPLEVEL = 2;                 // True if top level expr

    // {m,n} stacks
    int bracketMin;                                     // Minimum number of matches
    int bracketMax;                                     // Maximum number of matches

    boolean isXPath = true;
    boolean isXPath30 = true;
    boolean isXSD11 = false;
    IntHashSet captures = new IntHashSet();
    boolean hasBackReferences = false;

    REFlags reFlags;

    List<String> warnings;

    private final static boolean TRACING = false;

    /**
     * Constructor.  Creates (initially empty) storage for a regular expression program.
     */
    public RECompiler() {

    }

    /**
     * Set the regular expression flags to be used
     *
     * @param flags the regular expression flags
     */

    public void setFlags(REFlags flags) {
        this.reFlags = flags;
        isXPath = flags.isAllowsXPath20Extensions();
        isXPath30 = flags.isAllowsXPath30Extensions();
        isXSD11 = flags.isAllowsXSD11Syntax();
    }


    private void warning(String s) {
        if (warnings == null) {
            warnings = new ArrayList<>(4);
        }
        warnings.add(s);
    }

    /**
     * On completion of compilation, get any warnings that were generated
     *
     * @return the list of warning messages
     */

    public List<String> getWarnings() {
        if (warnings == null) {
            return Collections.emptyList();
        } else {
            return warnings;
        }
    }

    /**
     * Throws a new internal error exception
     *
     * @throws Error Thrown in the event of an internal error.
     */
    void internalError() throws Error {
        throw new Error("Internal error!");
    }

    /**
     * Throws a new syntax error exception
     *
     * @param s the error message
     * @throws net.sf.saxon.regex.RESyntaxException
     *          Thrown if the regular expression has invalid syntax.
     */
    void syntaxError(String s) throws RESyntaxException {
        throw new RESyntaxException(s, idx);
    }

    /**
     * Optionally add trace code around an operation
     * @param base the operation to which trace code is to be added
     * @return the trace operation; this matches the same strings as the base operation,
     * but traces its execution for diagnostic purposes, provided the TRACING switch is set.
     */

    static Operation trace(Operation base) {
        if (TRACING && !(base instanceof Operation.OpTrace)) {
            return new Operation.OpTrace(base);
        } else {
            return base;
        }
    }

    /**
     * Match bracket {m,n} expression, putting the results in bracket member variables
     *
     * @throws net.sf.saxon.regex.RESyntaxException
     *          Thrown if the regular expression has invalid syntax.
     */
    void bracket() throws RESyntaxException {
        // Current character must be a '{'
        if (idx >= len || pattern.uCharAt(idx++) != '{') {
            internalError();
        }

        // Next char must be a digit
        if (idx >= len || !isAsciiDigit(pattern.uCharAt(idx))) {
            syntaxError("Expected digit");
        }

        // Get min ('m' of {m,n}) number
        FastStringBuffer number = new FastStringBuffer(16);
        while (idx < len && isAsciiDigit(pattern.uCharAt(idx))) {
            number.cat((char) pattern.uCharAt(idx++));
        }
        try {
            bracketMin = Integer.parseInt(number.toString());
        } catch (NumberFormatException e) {
            syntaxError("Expected valid number");
        }

        // If out of input, fail
        if (idx >= len) {
            syntaxError("Expected comma or right bracket");
        }

        // If end of expr, optional limit is 0
        if (pattern.uCharAt(idx) == '}') {
            idx++;
            bracketMax = bracketMin;
            return;
        }

        // Must have at least {m,} and maybe {m,n}.
        if (idx >= len || pattern.uCharAt(idx++) != ',') {
            syntaxError("Expected comma");
        }

        // If out of input, fail
        if (idx >= len) {
            syntaxError("Expected comma or right bracket");
        }

        // If {m,} max is unlimited
        if (pattern.uCharAt(idx) == '}') {
            idx++;
            bracketMax = Integer.MAX_VALUE;
            return;
        }

        // Next char must be a digit
        if (idx >= len || !isAsciiDigit(pattern.uCharAt(idx))) {
            syntaxError("Expected digit");
        }

        // Get max number
        number.setLength(0);
        while (idx < len && isAsciiDigit(pattern.uCharAt(idx))) {
            number.cat((char) pattern.uCharAt(idx++));
        }
        try {
            bracketMax = Integer.parseInt(number.toString());
        } catch (NumberFormatException e) {
            syntaxError("Expected valid number");
        }

        // Optional repetitions must be >= 0
        if (bracketMax < bracketMin) {
            syntaxError("Bad range");
        }

        // Must have close brace
        if (idx >= len || pattern.uCharAt(idx++) != '}') {
            syntaxError("Missing close brace");
        }
    }

    /**
     * Test whether a character is an ASCII decimal digit
     *
     * @param ch the character to be matched
     * @return true if the character is an ASCII digit (0-9)
     */

    private static boolean isAsciiDigit(int ch) {
        return ch >= '0' && ch <= '9';
    }

    /**
     * Match an escape sequence.  Handles quoted chars and octal escapes as well
     * as normal escape characters.  Always advances the input stream by the
     * right amount. This code "understands" the subtle difference between an
     * octal escape and a backref.  You can access the type of ESC_CLASS or
     * ESC_COMPLEX or ESC_BACKREF by looking at pattern[idx - 1].
     * @param inSquareBrackets true if the escape sequence is within square brackets
     * @return an IntPredicate that matches the character or characters represented
     *         by this escape sequence. For a single-character escape this must be an IntValuePredicate
     * @throws net.sf.saxon.regex.RESyntaxException
     *          Thrown if the regular expression has invalid syntax.
     */
    CharacterClass escape(boolean inSquareBrackets) throws RESyntaxException {
        // "Shouldn't" happen
        if (pattern.uCharAt(idx) != '\\') {
            internalError();
        }

        // Escape shouldn't occur as last character in string!
        if (idx + 1 == len) {
            syntaxError("Escape terminates string");
        }

        // Switch on character after backslash
        idx += 2;
        int escapeChar = pattern.uCharAt(idx - 1);
        switch (escapeChar) {

            case 'n':
                return new SingletonCharacterClass('\n');
            case 'r':
                return new SingletonCharacterClass('\r');
            case 't':
                return new SingletonCharacterClass('\t');

            case '\\':
            case '|':
            case '.':
            case '-':
            case '^':
            case '?':
            case '*':
            case '+':
            case '{':
            case '}':
            case '(':
            case ')':
            case '[':
            case ']':
                return new SingletonCharacterClass(escapeChar);

            case '$':
                if (isXPath) {
                    return new SingletonCharacterClass(escapeChar);
                } else {
                    syntaxError("In XSD, '$' must not be escaped");
                }

            case 's':
                return Categories.ESCAPE_s;

            case 'S':
                return Categories.ESCAPE_S;

            case 'i':
                return Categories.ESCAPE_i;

            case 'I':
                return Categories.ESCAPE_I;

            case 'c':
                return Categories.ESCAPE_c;

            case 'C':
                return Categories.ESCAPE_C;

            case 'd':
                return Categories.ESCAPE_d;

            case 'D':
                return Categories.ESCAPE_D;

            case 'w':
                return Categories.ESCAPE_w;

            case 'W':
                return Categories.ESCAPE_W;


            case 'p':
            case 'P':

                if (idx == len) {
                    syntaxError("Expected '{' after \\" + escapeChar);
                }
                if (pattern.uCharAt(idx) != '{') {
                    syntaxError("Expected '{' after \\" + escapeChar);
                }
                int close = pattern.uIndexOf('}', idx++);
                if (close == -1) {
                    syntaxError("No closing '}' after \\" + escapeChar);
                }
                UnicodeString block = pattern.uSubstring(idx, close);
                if (block.uLength() == 1 || block.uLength() == 2) {
                    CharacterClass primary = Categories.getCategory(block.toString());
                    if (primary == null) {
                        syntaxError("Unknown character category " + block.toString());
                    }
                    idx = close + 1;
                    if (escapeChar == 'p') {
                        return primary;
                    } else {
                        return makeComplement(primary);
                    }
                } else if (block.toString().startsWith("Is")) {
                    String blockName = block.toString().substring(2);
                    IntSet uniBlock = UnicodeBlocks.getBlock(blockName);
                    if (uniBlock == null) {
                        // XSD 1.1 says this is not an error, but by default we reject it
                        if (reFlags.isAllowUnknownBlockNames()) {
                            warning("Unknown Unicode block: " + blockName);
                            idx = close + 1;
                            return EmptyCharacterClass.getComplement();
                        } else {
                            syntaxError("Unknown Unicode block: " + blockName);
                        }
                    }
                    idx = close + 1;
                    IntSetCharacterClass primary = new IntSetCharacterClass(uniBlock);
                    if (escapeChar == 'p') {
                        return primary;
                    } else {
                        return makeComplement(primary);
                    }
                } else {
                    syntaxError("Unknown character category: " + block);
                }

            case '0':
                syntaxError("Octal escapes not allowed");

            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':

                if (inSquareBrackets) {
                    syntaxError("Backreference not allowed within character class");
                } else if (isXPath) {
                    int backRef = escapeChar - '0';
                    while (idx < len) {
                        int c1 = "0123456789".indexOf(pattern.uCharAt(idx));
                        if (c1 < 0) {
                            break;
                        } else {
                            int backRef2 = backRef * 10 + c1;
                            if (backRef2 > (capturingOpenParenCount - 1)) {
                                break;
                            } else {
                                backRef = backRef2;
                                idx++;
                            }
                        }

                    }
                    if (!captures.contains(backRef)) {
                        String explanation = backRef > (capturingOpenParenCount - 1) ? "(no such group)" : "(group not yet closed)";
                        syntaxError("invalid backreference \\" + backRef + " " + explanation);
                    }
                    hasBackReferences = true;
                    return new BackReference(backRef);
                } else {
                    syntaxError("digit not allowed after \\");
                }

            default:

                // Other characters not allowed in XSD regexes
                syntaxError("Escape character '" + (char) escapeChar + "' not allowed");
        }
        return null;
    }

    /**
     * For convenience a back-reference is treated as an CharacterClass, although this a fiction
     */

    class BackReference extends SingletonCharacterClass {
        public BackReference(int number) {
            super(number);
        }
    }


    /**
     * Compile a character class (in square brackets)
     *
     * @return an IntPredicate that tests whether a character matches this character class
     * @throws net.sf.saxon.regex.RESyntaxException
     *          Thrown if the regular expression has invalid syntax.
     */
    CharacterClass parseCharacterClass() throws RESyntaxException {
        // Check for bad calling or empty class
        if (pattern.uCharAt(idx) != '[') {
            internalError();
        }

        // Check for unterminated or empty class
        if ((idx + 1) >= len || pattern.uCharAt(++idx) == ']') {
            syntaxError("Missing ']'");
        }

        // Parse class declaration
        int simpleChar;
        boolean positive = true;
        boolean definingRange = false;
        int rangeStart = -1;
        int rangeEnd;
        IntRangeSet range = new IntRangeSet();
        CharacterClass addend = null;
        CharacterClass subtrahend = null;
        if (thereFollows("^")) {
            if (thereFollows("^-[")) {
                syntaxError("Nothing before subtraction operator");
            } else if (thereFollows("^]")) {
                syntaxError("Empty negative character group");
            } else {
                positive = false;
                idx++;
            }
        } else if (thereFollows("-[")) {
            syntaxError("Nothing before subtraction operator");
        }
        while (idx < len && pattern.uCharAt(idx) != ']') {
            int ch = pattern.uCharAt(idx);
            simpleChar = -1;
            switch (ch) {
                case '[':
                    syntaxError("Unescaped '[' within square brackets");
                    break;
                case '\\': {
                    // Escape always advances the stream
                    CharacterClass cc = escape(true);
                    if (cc instanceof SingletonCharacterClass) {
                        simpleChar = ((SingletonCharacterClass) cc).getCodepoint();
                        break;
                    } else {
                        if (definingRange) {
                            syntaxError("Multi-character escape cannot follow '-'");
                        } else if (addend == null) {
                            addend = cc;
                        } else {
                            addend = makeUnion(addend, cc);
                        }
                        continue;
                    }
                }
                case '-':
                    if (thereFollows("-[")) {
                        idx++;
                        subtrahend = parseCharacterClass();
                        if (!thereFollows("]")) {
                            syntaxError("Expected closing ']' after subtraction");
                        }
                    } else if (thereFollows("-]")) {
                        simpleChar = '-';
                        idx++;
                    } else if (rangeStart >= 0) {
                        definingRange = true;
                        idx++;
                        continue;
                    } else if (definingRange) {
                        syntaxError("Bad range");
                    } else if (thereFollows("--") && !thereFollows("--[")) {
                        syntaxError("Unescaped hyphen as start of range");
                    } else if (!isXSD11 && pattern.uCharAt(idx - 1) != '[' && pattern.uCharAt(idx - 1) != '^' && !thereFollows("]") && !thereFollows("-[")) {
                        syntaxError("In XSD 1.0, hyphen is allowed only at the beginning or end of a positive character group");
                    } else {
                        simpleChar = '-';
                        idx++;
                    }
                    break;

                default:
                    simpleChar = ch;
                    idx++;
                    break;
            }

            // Handle simple character simpleChar
            if (definingRange) {
                // if we are defining a range make it now
                rangeEnd = simpleChar;

                // Actually create a range if the range is ok
                if (rangeStart > rangeEnd) {
                    syntaxError("Bad character range: start > end");
                    // Technically this is not an error in XSD, merely a no-op; but it is so
                    // utterly pointless that it is almost certainly a mistake; and we have no
                    // way of indicating warnings.
                }
                range.addRange(rangeStart, rangeEnd);
                if (reFlags.isCaseIndependent()) {
                    // Special-case A-Z and a-z
                    if (rangeStart == 'a' && rangeEnd == 'z') {
                        range.addRange('A', 'Z');
                        for (int v = 0; v < CaseVariants.ROMAN_VARIANTS.length; v++) {
                            range.add(CaseVariants.ROMAN_VARIANTS[v]);
                        }
                    } else if (rangeStart == 'A' && rangeEnd == 'Z') {
                        range.addRange('a', 'z');
                        for (int v = 0; v < CaseVariants.ROMAN_VARIANTS.length; v++) {
                            range.add(CaseVariants.ROMAN_VARIANTS[v]);
                        }
                    } else {
                        for (int k = rangeStart; k <= rangeEnd; k++) {
                            int[] variants = CaseVariants.getCaseVariants(k);
                            for (int variant : variants) {
                                range.add(variant);
                            }
                        }
                    }
                }

                // We are done defining the range
                definingRange = false;
                rangeStart = -1;
            } else {
                // If simple character and not start of range, include it (see XSD 1.1 rules)
                if (thereFollows("-")) {
                    if (thereFollows("-[")) {
                        range.add(simpleChar);
                    } else if (thereFollows("-]")) {
                        range.add(simpleChar);
                    } else if (thereFollows("--[")) {
                        range.add(simpleChar);
                    } else if (thereFollows("--")) {
                        syntaxError("Unescaped hyphen cannot act as end of range");
                    } else {
                        rangeStart = simpleChar;
                    }
                } else {
                    range.add(simpleChar);
                    if (reFlags.isCaseIndependent()) {
                        int[] variants = CaseVariants.getCaseVariants(simpleChar);
                        for (int variant : variants) {
                            range.add(variant);
                        }
                    }
                }
            }
        }

        // Shouldn't be out of input
        if (idx == len) {
            syntaxError("Unterminated character class");
        }

        // Absorb the ']' end of class marker
        idx++;
        CharacterClass result = new IntSetCharacterClass(range);
        if (addend != null) {
            result = makeUnion(result, addend);
        }
        if (!positive) {
            result = makeComplement(result);
        }
        if (subtrahend != null) {
            result = makeDifference(result, subtrahend);
        }
        return result;
    }

    /**
     * Test whether the string starting at the current position is equal to some specified string
     *
     * @param s the string being tested
     * @return true if the specified string is present
     */

    private boolean thereFollows(String s) {
        return idx + s.length() <= len &&
                pattern.uSubstring(idx, idx + s.length()).toString().equals(s);
    }

    /**
     * Make the union of two IntPredicates (matches if p1 matches or p2 matches)
     *
     * @param p1 the first
     * @param p2 the second
     * @return the result
     */

    public static CharacterClass makeUnion(CharacterClass p1, CharacterClass p2) {
        if (p1 == EmptyCharacterClass.getInstance()) {
            return p2;
        }
        if (p2 == EmptyCharacterClass.getInstance()) {
            return p1;
        }
        IntSet is1 = p1.getIntSet();
        IntSet is2 = p2.getIntSet();
        if (is1 == null || is2 == null) {
            return new PredicateCharacterClass(p1.or(p2));
        } else {
            return new IntSetCharacterClass(is1.union(is2));
        }
    }

    /**
     * Make the difference of two IntPredicates (matches if p1 matches and p2 does not match)
     *
     * @param p1 the first
     * @param p2 the second
     * @return the result
     */

    public static CharacterClass makeDifference(CharacterClass p1, CharacterClass p2) {
        if (p1 == EmptyCharacterClass.getInstance()) {
            return p1;
        }
        if (p2 == EmptyCharacterClass.getInstance()) {
            return p1;
        }
        IntSet is1 = p1.getIntSet();
        IntSet is2 = p2.getIntSet();
        if (is1 == null || is2 == null) {
            return new PredicateCharacterClass(new IntExceptPredicate(p1, p2));
        } else {
            return new IntSetCharacterClass(is1.except(is2));
        }
    }

    /**
     * Make the complement of an IntPredicate (matches if p1 does not match)
     *
     * @param p1 the operand
     * @return the result
     */

    public static CharacterClass makeComplement(CharacterClass p1) {
        if (p1 instanceof InverseCharacterClass) {
            return ((InverseCharacterClass) p1).getComplement();
        } else {
            return new InverseCharacterClass(p1);
        }
    }

    /**
     * Absorb an atomic character string.  This method is a little tricky because
     * it can un-include the last character of string if a quantifier operator follows.
     * This is correct because *+? have higher precedence than concatentation (thus
     * ABC* means AB(C*) and NOT (ABC)*).
     *
     * @return Index of new atom node
     * @throws net.sf.saxon.regex.RESyntaxException
     *          Thrown if the regular expression has invalid syntax.
     */
    Operation parseAtom() throws RESyntaxException {

        // Length of atom
        int lenAtom = 0;

        // Loop while we've got input

        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C64);

        atomLoop:

        while (idx < len) {
            // Is there a next char?
            if ((idx + 1) < len) {
                int c = pattern.uCharAt(idx + 1);

                // If the next 'char' is an escape, look past the whole escape
                if (pattern.uCharAt(idx) == '\\') {
                    int idxEscape = idx;
                    escape(false);
                    if (idx < len) {
                        c = pattern.uCharAt(idx);
                    }
                    idx = idxEscape;
                }

                // Switch on next char
                switch (c) {
                    case '{':
                    case '?':
                    case '*':
                    case '+':

                        // If the next character is a quantifier operator and our atom is non-empty, the
                        // current character should bind to the quantifier operator rather than the atom
                        if (lenAtom != 0) {
                            break atomLoop;
                        }
                }
            }

            // Switch on current char
            switch (pattern.uCharAt(idx)) {
                case ']':
                case '.':
                case '[':
                case '(':
                case ')':
                case '|':
                    break atomLoop;

                case '{':
                case '?':
                case '*':
                case '+':

                    // We should have an atom by now
                    if (lenAtom == 0) {
                        // No atom before quantifier
                        syntaxError("No expression before quantifier");
                    }
                    break atomLoop;

                case '}':
                    syntaxError("Unescaped right curly brace");
                    break atomLoop;

                case '\\': {
                    // Get the escaped character (advances input automatically)
                    int idxBeforeEscape = idx;
                    IntPredicate charClass = escape(false);

                    // Check if it's a simple escape (as opposed to, say, a backreference)
                    if (charClass instanceof BackReference || !(charClass instanceof IntValuePredicate)) {
                        // Not a simple escape, so backup to where we were before the escape.
                        idx = idxBeforeEscape;
                        break atomLoop;
                    }

                    // Add escaped char to atom
                    fsb.appendWideChar(((IntValuePredicate) charClass).getTarget());
                    lenAtom++;
                    break;
                }

                case '^':
                case '$':
                    if (isXPath) {
                        break atomLoop;
                    }
                    // else fall through ($ is not a metacharacter in XSD)

                default:

                    // Add normal character to atom
                    fsb.appendWideChar(pattern.uCharAt(idx++));
                    lenAtom++;
                    break;
            }
        }

        // This shouldn't happen
        if (fsb.isEmpty()) {
            internalError();
        }

        // Return the instruction
        return trace(new Operation.OpAtom(UnicodeString.makeUnicodeString(fsb.condense())));
    }


    /**
     * Match a terminal symbol.
     *
     * @param flags Flags
     * @return Index of terminal node (closeable)
     * @throws net.sf.saxon.regex.RESyntaxException
     *          Thrown if the regular expression has invalid syntax.
     */
    Operation parseTerminal(int[] flags) throws RESyntaxException {
        switch (pattern.uCharAt(idx)) {
            case '$':
                if (isXPath) {
                    idx++;
                    return trace(new Operation.OpEOL());
                }
                break;

            case '^':
                if (isXPath) {
                    idx++;
                    return trace(new Operation.OpBOL());
                }
                break;

            case '.':
                idx++;
                IntPredicate predicate;
                if (reFlags.isSingleLine()) {
                    // in XPath with the 's' flag, '.' matches everything
                    predicate = IntSetPredicate.ALWAYS_TRUE;
                } else {
                    // in XSD, "." matches everything except \n and \r. See also bug 15594.
                    predicate = value -> value != '\n' && value != '\r';
                }
                return trace(new Operation.OpCharClass(predicate));

            case '[':
                CharacterClass range = parseCharacterClass();
                return trace(new Operation.OpCharClass(range));

            case '(':
                return parseExpr(flags);

            case ')':
                syntaxError("Unexpected closing ')'");

            case '|':
                internalError();

            case ']':
                syntaxError("Unexpected closing ']'");

            case 0:
                syntaxError("Unexpected end of input");

            case '?':
            case '+':
            case '{':
            case '*':
                syntaxError("No expression before quantifier");

            case '\\': {
                // Don't forget, escape() advances the input stream!
                int idxBeforeEscape = idx;

                CharacterClass esc = escape(false);

                if (esc instanceof BackReference) {
                    // this is a total kludge
                    int backreference = ((BackReference) esc).getCodepoint();
                    if (capturingOpenParenCount <= backreference) {
                        syntaxError("Bad backreference");
                    }
                    return trace(new Operation.OpBackReference(backreference));

                } else if (esc instanceof IntSingletonSet) {
                    // We had a simple escape and we want to have it end up in
                    // an atom, so we back up and fall though to the default handling
                    idx = idxBeforeEscape;

                } else {
                    return trace(new Operation.OpCharClass(esc));
                }

            }
        }

        // Everything above either fails or returns.
        // If it wasn't one of the above, it must be the start of an atom.
        return parseAtom();
    }

    /**
     * Compile a piece consisting of an atom and optional quantifier
     *
     * @param flags Flags passed by reference
     * @return Index of resulting instruction
     * @throws net.sf.saxon.regex.RESyntaxException
     *          Thrown if the regular expression has invalid syntax.
     */
    Operation piece(int[] flags) throws RESyntaxException {

        // Values to pass by reference to terminal()
        int[] terminalFlags = {NODE_NORMAL};

        // Get terminal symbol
        Operation ret = parseTerminal(terminalFlags);

        // Or in flags from terminal symbol
        flags[0] |= terminalFlags[0];

        // Advance input, set NODE_NULLABLE flag and do sanity checks
        if (idx >= len) {
            return ret;
        }

        boolean greedy = true;
        int quantifierType = pattern.uCharAt(idx);
        switch (quantifierType) {
            case '?':
            case '*':
            case '+':

                // Eat quantifier character
                idx++;

                // Drop through

            case '{':

                if (quantifierType == '{') {
                    bracket();
                }


                if (ret instanceof Operation.OpBOL || ret instanceof Operation.OpEOL) {
                    // Pretty meaningless, but legal. If the quantifier allows zero occurrences, ignore the instruction.
                    // Otherwise, ignore the quantifier
                    if (quantifierType == '?' || quantifierType == '*' ||
                            (quantifierType == '{' && bracketMin == 0)) {
                        return new Operation.OpNothing();
                    } else {
                        quantifierType = 0;
                    }
                }
                if (ret.matchesEmptyString() == Operation.MATCHES_ZLS_ANYWHERE) {
                    if (quantifierType == '?') {
                        // can ignore the quantifier
                        quantifierType = 0;
                    } else if (quantifierType == '+') {
                        // '*' and '+' are equivalent
                        quantifierType = '*';
                    } else if (quantifierType == '{') {
                        // bounds are meaningless
                        quantifierType = '*';
                    }
                }

        }

        // If the next character is a '?', make the quantifier non-greedy (reluctant)
        if (idx < len && pattern.uCharAt(idx) == '?') {
            if (!isXPath) {
                syntaxError("Reluctant quantifiers are not allowed in XSD");
            }
            idx++;
            greedy = false;
        }
        int min = 1;
        int max = 1;
        switch (quantifierType) {
            case '{':
                min = this.bracketMin;
                max = this.bracketMax;
                break;
            case '?':
                min = 0;
                max = 1;
                break;
            case '+':
                min = 1;
                max = Integer.MAX_VALUE;
                break;
            case '*':
                min = 0;
                max = Integer.MAX_VALUE;
                break;
        }

        Operation result;
        if (max == 0) {
            result = new Operation.OpNothing();
        } else if (min == 1 && max == 1) {
            return ret;
        } else if (greedy) {
            // Actually do the quantifier now
            if (ret.getMatchLength() == -1) {
                result = trace(new Operation.OpRepeat(ret, min, max, true));
            } else {
                result = new Operation.OpGreedyFixed(ret, min, max, ret.getMatchLength());
            }
        } else {
            if (ret.getMatchLength() == -1) {
                result = new Operation.OpRepeat(ret, min, max, false);
            } else {
                result = new Operation.OpReluctantFixed(ret, min, max, ret.getMatchLength());
            }
        }
        return trace(result);

    }

    /**
     * Compile body of one branch of an or operator (implements concatenation)
     *
     * @return Pointer to first node in the branch
     * @throws net.sf.saxon.regex.RESyntaxException
     *          Thrown if the regular expression has invalid syntax.
     */
    Operation parseBranch() throws RESyntaxException {
        // Get each possibly qnatified piece and concat
        Operation current = null;
        int[] quantifierFlags = new int[1];
        while (idx < len && pattern.uCharAt(idx) != '|' && pattern.uCharAt(idx) != ')') {
            // Get new node
            quantifierFlags[0] = NODE_NORMAL;
            Operation op = piece(quantifierFlags);
            if (current == null) {
                current = op;
            } else {
                current = makeSequence(current, op);
            }
        }

        // If we don't run loop, make a nothing node
        if (current == null) {
            return new Operation.OpNothing();
        }

        return current;
    }

    /**
     * Compile an expression with possible parens around it.  Paren matching
     * is done at this level so we can tie the branch tails together.
     *
     * @param compilerFlags Flag value passed by reference
     * @return Node index of expression in instruction array
     * @throws net.sf.saxon.regex.RESyntaxException
     *          Thrown if the regular expression has invalid syntax.
     */
    private Operation parseExpr(int[] compilerFlags) throws RESyntaxException {
        // Create open paren node unless we were called from the top level (which has no parens)
        int paren = -1;
        int group = 0;
        List<Operation> branches = new ArrayList<>();
        int closeParens = capturingOpenParenCount;
        boolean capturing = true;
        if ((compilerFlags[0] & NODE_TOPLEVEL) == 0 && pattern.uCharAt(idx) == '(') {
            // if its a cluster ( rather than a proper subexpression ie with backrefs )
            if (idx + 2 < len && pattern.uCharAt(idx + 1) == '?' && pattern.uCharAt(idx + 2) == ':') {
                if (!isXPath30) {
                    syntaxError("Non-capturing groups allowed only in XPath3.0");
                }
                paren = 2;
                idx += 3;
                capturing = false;
            } else {
                paren = 1;
                idx++;
                group = capturingOpenParenCount++;
            }
        }
        compilerFlags[0] &= ~NODE_TOPLEVEL;

        // Process contents of first branch node
        branches.add(parseBranch());

        // Loop through branches
        while (idx < len && pattern.uCharAt(idx) == '|') {
            idx++;
            branches.add(parseBranch());
        }

        Operation op;
        if (branches.size() == 1) {
            op = branches.get(0);
        } else {
            op = new Operation.OpChoice(branches);
        }

        // Create an ending node (either a close paren or an OP_END)
        if (paren > 0) {
            if (idx < len && pattern.uCharAt(idx) == ')') {
                idx++;
            } else {
                syntaxError("Missing close paren");
            }
            if (capturing) {
                op = new Operation.OpCapture(op, group);
                captures.add(closeParens);
            } else {
                // return op unchanged
            }
        } else {
            op = makeSequence(op, new Operation.OpEndProgram());
        }

        // Return the node list
        return op;
    }

    private static Operation makeSequence(Operation o1, Operation o2) {
        if (o1 instanceof Operation.OpSequence) {
            if (o2 instanceof Operation.OpSequence) {
                List<Operation> l1 = ((Operation.OpSequence)o1).getOperations();
                List<Operation> l2 = ((Operation.OpSequence)o2).getOperations();
                l1.addAll(l2);
                return o1;
            }
            List<Operation> l1 = ((Operation.OpSequence)o1).getOperations();
            l1.add(o2);
            return o1;
        } else if (o2 instanceof Operation.OpSequence) {
            List<Operation> l2 = ((Operation.OpSequence)o2).getOperations();
            l2.add(0, o1);
            return o2;
        } else {
            List<Operation> list = new ArrayList<>(4);
            list.add(o1);
            list.add(o2);
            return trace(new Operation.OpSequence(list));
        }
    }

    /**
     * Compiles a regular expression pattern into a program runnable by the pattern
     * matcher class 'RE'.
     *
     * @param pattern Regular expression pattern to compile (see RECompiler class
     *                for details).
     * @return A compiled regular expression program.
     * @throws net.sf.saxon.regex.RESyntaxException
     *          Thrown if the regular expression has invalid syntax.
     * @see RECompiler
     * @see net.sf.saxon.regex.REMatcher
     */
    public REProgram compile(UnicodeString pattern) throws RESyntaxException {
        // Initialize variables for compilation
        //System.err.println("Compiling regex " + pattern);
        this.pattern = pattern;                         // Save pattern in instance variable
        len = pattern.uLength();                         // Precompute pattern length for speed
        idx = 0;                                        // Set parsing index to the first character
        capturingOpenParenCount = 1;                                     // Set paren level to 1 (the implicit outer parens)

        if (reFlags.isLiteral()) {

            // 'q' flag is set
            // Create a string node
            Operation ret = new Operation.OpAtom(this.pattern);
            Operation endNode = new Operation.OpEndProgram();
            Operation seq = makeSequence(ret, endNode);
            return new REProgram(seq, capturingOpenParenCount, reFlags);

        } else {

            if (reFlags.isAllowWhitespace()) {
                // 'x' flag is set. Preprocess the expression to strip whitespace, other than between
                // square brackets
                FastStringBuffer sb = new FastStringBuffer(pattern.uLength());
                int nesting = 0;
                boolean astral = false;
                boolean escaped = false;
                for (int i = 0; i < pattern.uLength(); i++) {
                    int ch = pattern.uCharAt(i);
                    if (ch > 65535) {
                        astral = true;
                    }
                    if (ch == '\\' && !escaped) {
                        escaped = true;
                        sb.appendWideChar(ch);
                    } else if (ch == '[' && !escaped) {
                        nesting++;
                        escaped = false;
                        sb.appendWideChar(ch);
                    } else if (ch == ']' && !escaped) {
                        nesting--;
                        escaped = false;
                        sb.appendWideChar(ch);
                    } else if (nesting == 0 && Whitespace.isWhitespace(ch)) {
                        // no action
                    } else {
                        escaped = false;
                        sb.appendWideChar(ch);
                    }
                }
                if (astral) {
                    pattern = new GeneralUnicodeString(sb);
                } else {
                    pattern = new BMPString(sb);
                }
                this.pattern = pattern;
                this.len = pattern.uLength();
            }

            // Initialize pass by reference flags value
            int[] compilerFlags = {NODE_TOPLEVEL};

            // Parse expression
            Operation exp = parseExpr(compilerFlags);

            // Should be at end of input
            if (idx != len) {
                if (pattern.uCharAt(idx) == ')') {
                    syntaxError("Unmatched close paren");
                }
                syntaxError("Unexpected input remains");
            }

            REProgram program = new REProgram(exp, capturingOpenParenCount, reFlags);
            if (hasBackReferences) {
                program.optimizationFlags |= REProgram.OPT_HASBACKREFS;
            }
            return program;

        }

    }


    /**
     * Determine that there is no ambiguity between two branches, that is, if one of them matches then the
     * other cannot possibly match. (This is for optimization, so it does not have to detect all cases; but
     * if it returns true, then the result must be dependable.)
     * @param op0 the first branch
     * @param op1 the second branch
     * @param caseBlind true if the "i" flag is in force
     * @param reluctant true if the first branch is a repeat branch with a reluctant quantifier
     * @return true if it can be established that there is no input sequence that will match both instructions
     */

    static boolean noAmbiguity(Operation op0, Operation op1, boolean caseBlind, boolean reluctant) {
        if (op1 instanceof Operation.OpEndProgram) {
            return !reluctant;
        }
        if (op1 instanceof Operation.OpBOL || op1 instanceof Operation.OpEOL) {
            return true;
        }
        if (op1 instanceof Operation.OpRepeat && ((Operation.OpRepeat)op1).min == 0) {
            return false; //Bug 3429
        }
        CharacterClass c0 = op0.getInitialCharacterClass(caseBlind);
        CharacterClass c1 = op1.getInitialCharacterClass(caseBlind);
        return c0.isDisjoint(c1);
    }

}
