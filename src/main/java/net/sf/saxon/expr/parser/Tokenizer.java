////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.om.NameChecker;
import net.sf.saxon.regex.ARegularExpression;
import net.sf.saxon.str.StringTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharp;
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.transpile.CSharpReplaceException;
import net.sf.saxon.value.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.function.Predicate;

/**
 * Tokenizer for XPath and XQuery expressions.
 * <p>Heavily modified in Saxon 13, based on the principles in the XQuery 4.0 specification.
 * Tokenization is independent of the syntactic context: the tokenizer no longer attempts to
 * distinguish whether <code>'*'</code>, for example, is a multiplication operator, a wildcard,
 * or an occurrence indicator; it leaves that decision to the parser.</p>
 * <p>The main complication is "complex tokens": constructs such as string templates, string
 * constructors, and XQuery direct element constructors that contain embedded expressions,
 * always delimited by curly braces. The tokenizer recognises complex tokens by their initial
 * characters, and returns an appropriate {@link Token} object to the parser, which then starts
 * reading its content using calls on {@link Tokenizer#nextChar} calls. When an open brace
 * identifying an embedded expression is encountered, the parser calls
 * {@link Tokenizer#startEmbeddedExpression}, which has the effect that when the matching
 * closing brace is encountered, and "end of expression" is signalled back to the parser</p>
 */


public final class Tokenizer {

//    public static final char FULL_WIDTH_LT = '＜'; // xFF1C
//    public static final char FULL_WIDTH_GT = '＞'; // xFF1E

    @CSharpModifiers(code={"public", "const"})    // The transpiler handles this for int but not for char??
    public final static char NUL = (char)0;

    /**
     * Predicate indicating that tokenization should stop when the end of the input is reached
     */
    public static Predicate<Tokenizer> END_OF_INPUT =
            tok -> tok.inputOffset >= tok.inputLength;

    /**
     * Predicate indicating that tokenization should stop when a "matching" closing curly brace is found
     */
    public static Predicate<Tokenizer> CLOSING_CURLY =
            tok -> tok.inputOffset < tok.inputLength
                    && tok.input.charAt(tok.inputOffset) == '}'
                    && tok.braceDepth <= 0;

    /**
     * Get a predicate to indicate that tokenization should stop when a particular keyword is encountered. Used
     * specifically in Gizmo.
     * @param keyword the keyword that signals the end of an expression
     * @return a suitable predicate
     */
    public static Predicate<Tokenizer> finishOnKeyword(String keyword) {
        return tok -> {
            if (tok.inputOffset >= tok.inputLength) {
                return true;
            }
            if (tok.input.substring(tok.inputOffset).startsWith(keyword)) {
                char beyond = tok.inputOffset + keyword.length() >= tok.inputLength
                        ? NUL
                        : tok.input.charAt(tok.inputOffset + keyword.length());
                return !Character.isLetterOrDigit(beyond);
            }
            return false;
        };
//        return tok -> tok.nextToken instanceof Token.NameToken
//                && ((Token.NameToken)tok.nextToken).getValue().equals(keyword);
    }


    /**
     * Token indicating end of tokenized input (not necessarily the end of the input string)
     */
    public Token currentToken = Token.EOF;
    /**
     * The position in the input string where the current token starts
     */
    public int currentTokenStartOffset = 0;
    /**
     * The next token to be returned
     */
    private Token nextToken = Token.EOF;
    /**
     * The position in the input string of the start of the next token
     */
    private int nextTokenStartOffset = 0;
    /**
     * The string being parsed
     */
    public String input;
    /**
     * The current position within the input string
     */
    public int inputOffset = 0;
    /**
     * The length of the input string (Java characters)
     */
    private int inputLength;
    /**
     * The line number (within the expression) of the current token
     */
    private int lineNumber = 1;
    /**
     * The line number (within the expression) of the next token
     */
    private int nextLineNumber = 1;

    /**
     * The depth of nesting of curly brace tokens
     */

    private int braceDepth = 0;

    /**
     * A stack allowing the previous depth-of-curly-brace nesting to be saved. This is used
     * when embedded expressions are nested (for example a string template within an embedded
     * expression within another string template).
     */
    private final Stack<Integer> braceDepthStack = new Stack<>();

    /**
     * A stack allowing the previous finish condition to be saved. This is used
     * when embedded expressions are nested (for example a string template within an embedded
     * expression within another string template).
     */
    private final Stack<Predicate<Tokenizer>> finishConditionStack = new Stack<>();

    /**
     * A list containing the positions (offsets in the input string) at which newline characters
     * occur
     */
    private List<Integer> newlineOffsets = null;

    /**
     * The token that preceded the current token
     */
    private Token precedingToken = Token.UNKNOWN;

    /**
     * Flag to indicate that this is XQuery as distinct from XPath
     */

    public boolean isXQuery = false;

    /**
     * XPath (or XQuery) language level: e.g. 2.0, 3.0, 3.1, 4.0 (times ten, as an integer)
     */

    public int languageLevel = 20;

    /**
     * Flag to allow Saxon extensions
     */

    public boolean allowSaxonExtensions = false;

    /**
     * Predicate used to decide that tokenization is complete
     */
    private Predicate<Tokenizer> finishCondition = END_OF_INPUT;

    public Tokenizer() {
    }

    /**
     * Set the condition that is used to decide when tokenization is complete
     * @param condition the completion condition. This condition is tested during
     *                  lookAhead() processing. The call on lookAhead() first
     *                  reads past all whitespace and comments, then tests the
     *                  finish condition, and if the finish condition is satisfied
     *                  at that point, it sets the next (pending) token to Token.EOF.
     */
    public void setFinishCondition(Predicate<Tokenizer> condition) {
        finishCondition = condition;
    }

    /**
     * Prepare a string for tokenization.
     * The actual tokens are obtained by calls on next()
     *
     * @param input      the string to be tokenized
     * @param start      start point within the string
     * @param end        end point within the string (last character not read):
     *                   -1 means end of string
     * @throws XPathException if a lexical error occurs, e.g. unmatched
     *                        string quotes
     */
    public void tokenize(String input, int start, int end) throws XPathException {
        nextToken = Token.EOF;
        nextTokenStartOffset = 0;
        inputOffset = start;
        this.input = input;
        this.lineNumber = 0;
        nextLineNumber = 0;
        if (end == -1) {
            inputLength = input.length();
        } else {
            inputLength = end;
        }

        // The tokenizer actually reads one token ahead. The raw lexical analysis performed by
        // the lookAhead() method does not (in general) distinguish names used as QNames from names
        // used for operators, axes, and functions. The next() routine further refines names into the
        // correct category, by looking at the following token. In addition, it combines compound tokens
        // such as "instance of" and "cast as".

        lookAhead();
        next();
    }

    /**
     * Restart tokenisation after, for example, a direct element constructor
     */

    public void restart() throws XPathException {
        tokenize(input, inputOffset, input.length());
    }

    //diagnostic version of next(): change real version to realnext()
    //
    //public void next() throws XPathException {
    //    realnext();
    //    System.err.println("Token: " + currentToken + "[" + tokens[currentToken] + "]");
    //}

    /**
     * Get the next token from the input expression. The type of token is returned in the
     * currentToken variable, the string value of the token in currentTokenValue.
     *
     * @throws XPathException if a lexical error is detected
     */

    public void next() throws XPathException {
        precedingToken = currentToken;
        currentToken = nextToken;
        currentTokenStartOffset = nextTokenStartOffset;
        lineNumber = nextLineNumber;

        //skipWhitespaceAndComments();
        // This is needed because the test of finishCondition looks at characters rather than
        // tokens, so we need to skip insignificant characters

        if (currentToken instanceof Token.ComplexToken) {
            // A complex token is one that includes embedded expressions. We can't look ahead
            // to the next token until we have parsed these.
            nextToken = Token.UNKNOWN;
        } else if (currentToken == Token.EOF) {
            // no action
//        } else if (finishCondition.test(this)) {
//            nextToken = Token.EOF;
        } else {
            lookAhead();
        }

    }


    /**
     * Peek ahead at the next token
     * @return the identifier of the token that is next in the queue.
     */

    public Token peekAhead() {
        return nextToken;
    }

    /**
     * Get the string value of the current name token
     * @return the string value of the current token, assuming it is
     * a name.
     * @throws ClassCastException if the current token is not a NameToken
     */
    public String currentName() {
        return ((Token.NameToken)currentToken).getValue();
    }

    /**
     * Look ahead by one token. This method does the real tokenization work.
     * The method is normally called internally, but the XQuery parser also
     * calls it to resume normal tokenization after dealing with pseudo-XML
     * syntax.
     *
     * @throws XPathException if a lexical error occurs
     */
    public void lookAhead() throws XPathException {

        skipWhitespaceAndComments();

        if (finishCondition.test(this)) {
            nextToken = Token.EOF;
            nextTokenStartOffset = inputOffset;
            return;
        }

        precedingToken = nextToken;
        nextTokenStartOffset = inputOffset;
        String nextTokenValue = "";
        while (true) {
            if (inputOffset >= inputLength) {
                nextToken = Token.EOF;
                return;
            }
            char c = input.charAt(inputOffset++);
            switch (c) {
                case '/':
                    if (isFollowedBy('/')) {
                        inputOffset++;
                        nextToken = Token.SLASH_SLASH;
                        return;
                    }
                    nextToken = Token.SLASH;
                    return;
                case ':':
                    if (isFollowedBy(':')) {
                        inputOffset++;
                        nextToken = Token.COLON_COLON;
                        return;
                    }
                    if (isFollowedBy('=')) {
                        nextToken = Token.COLON_EQUALS;
                        inputOffset++;
                        return;
                    }
                    nextToken = Token.COLON;
                    return;

                case '@':
                    nextToken = Token.AT;
                    return;
                case '?':
                    if (isFollowedBy('[')) {
                        inputOffset++;
                        nextToken = Token.QMARK_LSQB;
                        return;
                    }
                    nextToken = Token.QMARK;
                    return;
                case '[':
                    nextToken = Token.LSQB;
                    return;
                case ']':
                    nextToken = Token.RSQB;
                    return;
                case '{':
                    nextToken = Token.LCURLY;
                    braceDepth++;
                    return;
                case '}':
                    nextToken = Token.RCURLY;
                    braceDepth--;
                    return;
                case ';':
                    nextToken = Token.SEMICOLON;
                    return;
                case '%':
                    nextToken = Token.PERCENT;
                    return;
                case '(':
                    if (isFollowedBy('#') &&
                            (languageLevel < 40 ||
                                     isFollowedBy("# ") || isFollowedBy("#\t") ||
                                     isFollowedBy("#\n") || isFollowedBy("#\r"))) {
                        int pragmaStart = inputOffset-1;
                        inputOffset++;
                        int nestingDepth = 1;
                        while (nestingDepth > 0 && inputOffset < (inputLength - 1)) {
                            if (input.charAt(inputOffset) == '\n') {
                                incrementLineNumber();
                            } else if (input.charAt(inputOffset) == '#' &&
                                    input.charAt(inputOffset + 1) == ')') {
                                nestingDepth--;
                                inputOffset++;
                            } else if (input.charAt(inputOffset) == '(' &&
                                    input.charAt(inputOffset + 1) == '#') {
                                nestingDepth++;
                                inputOffset++;
                            }
                            inputOffset++;
                        }
                        if (nestingDepth > 0) {
                            throw new XPathException("Unclosed XQuery pragma");
                        }

                        nextToken = new Token.Pragma(input.substring(pragmaStart, inputOffset));
                        return;
                    }
                    if (isFollowedBy(':')) {
                        // XPath comment syntax is (: .... :)
                        // Comments may be nested, and may now be empty
                        inputOffset++;
                        int nestingDepth = 1;
                        while (nestingDepth > 0 && inputOffset < (inputLength - 1)) {
                            if (input.charAt(inputOffset) == '\n') {
                                incrementLineNumber();
                            } else if (input.charAt(inputOffset) == ':' &&
                                    input.charAt(inputOffset + 1) == ')') {
                                nestingDepth--;
                                inputOffset++;
                            } else if (input.charAt(inputOffset) == '(' &&
                                    input.charAt(inputOffset + 1) == ':') {
                                nestingDepth++;
                                inputOffset++;
                            }
                            inputOffset++;
                        }
                        if (nestingDepth > 0) {
                            throw new XPathException("Unclosed XPath comment");
                        }
                        lookAhead();
                    } else {
                        nextToken = Token.LPAREN;
                    }
                    return;
                case ')':
                    nextToken = Token.RPAREN;
                    return;
                case '+':
                    nextToken = Token.PLUS;
                    return;
                case '-':
                    if (inputOffset < inputLength && isGreaterThanChar(input.charAt(inputOffset))) {
                        inputOffset++;
                        nextToken = Token.THIN_ARROW;
                        return;
                    }
                    nextToken = Token.MINUS;   // not detected if part of a name
                    return;
                case '=':
                    if (inputOffset < inputLength && isGreaterThanChar(input.charAt(inputOffset))) {
                        inputOffset++;
                        nextToken = Token.FAT_ARROW;
                        return;
                    }
                    if (isFollowedBy("!>")) {
                        inputOffset+=2;
                        nextToken = Token.MAPPING_ARROW;  // Accepted in 4.0 only
                        return;
                    }
                    if (isFollowedBy("?>")) {
                        inputOffset+=2;
                        nextToken = Token.METHOD_CALL;
                        return;
                    }
                    nextToken = Token.EQUALS;
                    return;
                case '!':
                    if (isFollowedBy('=')) {
                        inputOffset++;
                        nextToken = Token.NE;
                        return;
                    }
                    nextToken = Token.BANG;
                    return;
                case '*':
                    if (isFollowedBy(':')
                            && inputOffset + 1 < inputLength
                            && (NameChecker.isNCNameStartChar(input.charAt(inputOffset + 1)))) {
                        inputOffset++;
                        int start = inputOffset;
                        for (; inputOffset < inputLength; inputOffset++) {
                            c = input.charAt(inputOffset);
                            if (c == ':' || !NameChecker.isNCNameChar(c)) {
                                break;
                            }
                        }
                        nextToken = new Token.Wildcard("*", input.substring(start, inputOffset));
                        return;
                    }
                    nextToken = Token.STAR;
                    return;
                case '×':
                    if (languageLevel >= 40) {
                        nextToken = Token.MATH_MULT;
                        return;
                    } else {
                        throw new XPathException("Multiply operator '×' is recognized only when XPath 4.0 is enabled");
                    }
                case '÷':
                    if (languageLevel >= 40) {
                        nextToken = Token.MATH_DIVIDE;
                        return;
                    } else {
                        throw new XPathException("Divide operator '÷' is recognized only when XPath 4.0 is enabled");
                    }
                case ',':
                    nextToken = Token.COMMA;
                    return;
                case '$':
                    nextToken = Token.DOLLAR;
                    return;
                case '~':
                    if (languageLevel < 40) {
                        throw new XPathException("Tilde '~' requires XPath 4.0 to be enabled");
                    }
                    nextToken = Token.TILDE;
                    return;


//                case FULL_WIDTH_GT:
//                    if (languageLevel < 40) {
//                        throw new XPathException("Operator character FULL_WIDTH_GREATER_THAN (xFF1E) requires XPath 4.0 to be enabled");
//                    }
//                    if (isFollowedBy('=')) {
//                        inputOffset++;
//                        nextToken = Token.GE;
//                        return;
//                    }
//                    if (isFollowedBy(FULL_WIDTH_GT)) {
//                        inputOffset++;
//                        nextToken = Token.FOLLOWS;
//                        return;
//                    }
//                    nextToken = Token.GT;
//                    return;
                case '>':
                    if (isFollowedBy('=')) {
                        inputOffset++;
                        nextToken = Token.GE;
                        return;
                    }
                    if (isFollowedBy('>')) {
                        inputOffset++;
                        nextToken = Token.FOLLOWS;
                        return;
                    }
                    nextToken = Token.GT;
                    return;
                case '<':
                    if (inputOffset < inputLength) {
                        int c2 = input.charAt(inputOffset);
                        if (c2 == '=') {
                            inputOffset++;
                            nextToken = Token.LE;
                            return;
                        } else if (c2 == '<') {
                            inputOffset++;
                            nextToken = Token.PRECEDES;
                            return;
                        } else if (isXQuery && c2 == '?') {
                            if (readDirectPIConstructor()) {
                                return;
                            }
                        } else if (isXQuery && c2 == '!') {
                            if (inputOffset + 1 < inputLength) {
                                int c3 = input.charAt(inputOffset + 1);
                                if (c3 == '-' && readDirectCommentConstructor()) {
                                    return;
                                }
                            }
                        } else if (isXQuery && NameChecker.isNCNameStartChar(c2)) {
                            int savedPosition = inputOffset;
                            if (readDirectElementConstructor()) {
                                nextToken = new Token.DirectElementConstructor(this, input, savedPosition-1);
                                nextTokenValue = nextToken.toString();
                                return;
                            }
                        }
                    }
                    nextToken = Token.LT;
                    return;
//                case FULL_WIDTH_LT:
//                    if (languageLevel < 40) {
//                        throw new XPathException("Operator character FULL_WIDTH_LESS_THAN (xFF1C) requires XPath 4.0 to be enabled");
//                    }
//                    if (isFollowedBy('=')) {
//                        inputOffset++;
//                        nextToken = Token.LE;
//                        return;
//                    }
//                    if (inputOffset < inputLength && input.charAt(inputOffset) == FULL_WIDTH_LT) {
//                        inputOffset++;
//                        nextToken = Token.PRECEDES;
//                        return;
//                    }
//                    nextToken = Token.LT;
//                    return;
                case '|':
                    if (isFollowedBy('|')) {
                        inputOffset++;
                        nextToken = Token.CONCAT;
                        return;
                    }
                    nextToken = Token.VBAR;
                    return;
                case '#':
//                    if (inputOffset < inputLength
//                            && languageLevel >= 40
//                            && NameChecker.isNCNameStartChar(input.charAt(inputOffset))) {
//                        nextToken = Token.HASH_BEFORE_NAME;
//                        // indicates that the "#" is the start of a QName literal. We only
//                        // return this if the "#" is followed by a name start character, but
//                        // we leave the parser to read the immediately-following EQName as
//                        // if it were a separate token.
//                    } else {
//                        nextToken = Token.HASH;
//                    }
                    nextToken = Token.HASH;
                    return;
                case '.':
                    if (isFollowedBy('.')) {
                        inputOffset++;
                        nextToken = Token.DOT_DOT;
                        return;
                    }
                    if (inputOffset == inputLength
                            || input.charAt(inputOffset) < '0'
                            || input.charAt(inputOffset) > '9') {
                        nextToken = Token.DOT;
                        return;
                    }
                    CSharp.emitCode("goto case '0';");
                    // otherwise drop through: we have a number starting with a decimal point
                case '0':
                    if (inputOffset < inputLength && languageLevel >= 40) {
                        if (input.charAt(inputOffset) == 'x') {
                            inputOffset++;
                            while (inputOffset < inputLength && "0123456789abcdefABCDEF_".indexOf(input.charAt(inputOffset)) >= 0) {
                                inputOffset++;
                            }
                            String body = input.substring(nextTokenStartOffset + 2, inputOffset);
                            if (body.startsWith("_") || body.endsWith("_")) {
                                throw new XPathException("Underscore not allowed at start or end of hex literal");
                            }
                            body = body.replace("_", "");
                            IntegerValue val = parseHexLiteral(body);
                            nextToken = new Token.NumericLiteral(body, val, 16);
                            return;
                        } else if (input.charAt(inputOffset) == 'b') {
                            inputOffset++;
                            while (inputOffset < inputLength && "01_".indexOf(input.charAt(inputOffset)) >= 0) {
                                inputOffset++;
                            }
                            String body = input.substring(nextTokenStartOffset + 2, inputOffset);
                            if (body.startsWith("_") || body.endsWith("_")) {
                                throw new XPathException("Underscore not allowed at start or end of binary literal");
                            }
                            body = body.replace("_", "");
                            IntegerValue val = parseBinaryLiteral(body);
                            nextToken = new Token.NumericLiteral(body, val, 2);
                            return;
                        }
                    }
                    CSharp.emitCode("goto case '1';");
                    // otherwise drop through: it's not a hex or binary numeric literal
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    // The logic here can return some tokens that are not legitimate numbers,
                    // for example "23e" or "1.0e+". However, this will only happen if the XPath
                    // expression as a whole is syntactically incorrect.
                    // These errors will be caught by the numeric constructor.
                    boolean allowE = true;
                    boolean allowSign = false;
                    boolean allowDot = true;
                    boolean keepGoing = true;
                    boolean allowUnderscore = languageLevel >= 40;
                    while (true) {
                        switch (c) {
                            case '0':
                            case '1':
                            case '2':
                            case '3':
                            case '4':
                            case '5':
                            case '6':
                            case '7':
                            case '8':
                            case '9':
                                allowSign = false;
                                break;
                            case '.':
                                if (allowDot) {
                                    allowDot = false;
                                    allowSign = false;
                                } else {
                                    inputOffset--;
                                    keepGoing = false;
                                    //break numloop;
                                }
                                break;
                            case '_':
                                if (allowUnderscore) {
                                    //System.err.println("InputOffset = " + inputOffset + " InputLength = " + inputLength);
                                    if (inputOffset >= inputLength || "0123456789_".indexOf(input.charAt(inputOffset)) < 0) {
                                        throw new XPathException("Underscore must be followed by a digit (or another underscore)");
                                    }
                                    if (inputOffset < 2 || "0123456789_".indexOf(input.charAt(inputOffset-2)) < 0) {
                                        throw new XPathException("Underscore must be preceded by a digit (or another underscore)");
                                    }
                                    break;
                                } else {
                                    throw new XPathException("Underscore is not allowed in numeric literal unless 4.0 is enabled");
                                }
                            case 'E':
                            case 'e':
                                if (allowE) {
                                    allowSign = true;
                                    allowE = false;
                                } else {
                                    inputOffset--;
                                    keepGoing = false;
                                    //break numloop;
                                }
                                break;
                            case '+':
                            case '-':
                                if (allowSign) {
                                    allowSign = false;
                                } else {
                                    inputOffset--;
                                    keepGoing = false;
                                    //break numloop;
                                }
                                break;
                            default:
                                if (('a' <= c && c <= 'z') || c > 127) {
                                    // this prevents the famous "10div 3"
                                    throw new XPathException("Separator needed after numeric literal");
                                }
                                inputOffset--;
                                keepGoing = false;
                                break;
                                //break numloop;
                        }
                        if (!keepGoing || inputOffset >= inputLength) {
                            break;
                        }
                        c = input.charAt(inputOffset++);
                    }
                    nextTokenValue = input.substring(nextTokenStartOffset, inputOffset).replace("_", "");
                    NumericValue value = NumericValue.parseNumber(nextTokenValue);
                    nextToken = new Token.NumericLiteral(nextTokenValue, value, 10);
                    return;
                case '"':
                case '\'':
                    StringBuilder litStr = new StringBuilder(32);
                    while (true) {
                        inputOffset = input.indexOf(c, inputOffset);
                        if (inputOffset < 0) {
                            inputOffset = nextTokenStartOffset + 1;
                            throw new XPathException("Unmatched quote in expression");
                        }
                        litStr.append(input, nextTokenStartOffset + 1, inputOffset++);
                        if (inputOffset < inputLength) {
                            char n = input.charAt(inputOffset);
                            if (n == c) {
                                // Doubled delimiters
                                litStr.append(c);
                                nextTokenStartOffset = inputOffset;
                                inputOffset++;

                            } else {
                                break;
                            }
                        } else {
                            break;
                        }
                    }

                    // maintain line number if there are newlines in the string

                    for (int i = 0; i < litStr.length(); i++) {
                        if (litStr.charAt(i) == '\n') {
                            incrementLineNumber(nextTokenStartOffset + i + 1);
                        }
                    }
                    nextToken = new Token.StringLiteral(litStr.toString());
                    return;
                case '`':
                    if (isFollowedBy("`[")) {
                        if (!isXQuery) {
                            throw new XPathException("String constructors (starting '``[') are allowed only in XQuery, not XPath");
                        }
                        nextToken = new Token.StringConstructor(this, input, inputOffset);
                        return;
                        
                    } else {
                        nextToken = new Token.StringTemplate(this, input, inputOffset);
                        return;
                    }
                case '\n':
                    incrementLineNumber();
                    CSharp.emitCode("goto case ' ';");
                    // fall through
                case ' ':
                case '\t':
                case '\r':
                    nextTokenStartOffset = inputOffset;
                    break;
                case 'Q':
                    if (isFollowedBy('{')) {
                        // EQName, revised syntax as per bug 15399
                        int close = input.indexOf('}', inputOffset++);
                        if (close < inputOffset) {
                            throw new XPathException("Missing closing brace in EQName");
                        }
                        String uri = input.substring(inputOffset, close);
                        uri = Whitespace.collapseWhitespace(uri); // Bug 29708
                        if (uri.contains("{")) {
                            throw new XPathException("EQName must not contain opening brace");
                        }
                        inputOffset = close + 1;
                        int start = inputOffset;
                        boolean isStar = false;
                        while (inputOffset < inputLength) {
                            char c2 = input.charAt(inputOffset);
                            if (c2 > 0x80 || Character.isLetterOrDigit(c2) || c2 == '_' || c2 == '.' || c2 == '-'
                                || (languageLevel >= 40 && c2 == ':' && inputOffset+1 < inputLength
                                            && NameChecker.isNCNameStartChar(input.charAt(inputOffset + 1)))) {
                                inputOffset++;
                            } else if (c2 == '*' && (start == inputOffset)) {
                                inputOffset++;
                                isStar = true;
                                break;
                            } else {
                                break;
                            }
                        }
                        String localName = input.substring(start, inputOffset);
                        nextTokenValue = "Q{" + uri + "}" + localName;
                        // Reuse Token.NAME because EQName is allowed anywhere that QName is allowed
                        nextToken = isStar
                                ? new Token.Wildcard("Q{" + uri + "}", "*")
                                : new Token.NameToken(nextTokenValue);
                        return;


                    }
                    CSharp.emitCode("goto default;");
                    /* else fall through */
                default:
                    if (c < 0x80 && !Character.isLetter(c)) {
                        throw new XPathException("Invalid character '" + c + "' (x" + Integer.toHexString((int)c) + ") in expression");
                    }
                    CSharp.emitCode("goto case '_';");
                    /* fall through */
                case '_':
                    boolean foundColon = false;
                    boolean breakLoop = false;
                    for (; inputOffset < inputLength; inputOffset++) {
                        c = input.charAt(inputOffset);
                        switch (c) {
                            case ':':
                                if (!foundColon) {
                                    // This is the first colon found. If the following character is a
                                    // name character, keep going. If it is "*", return a Wildcard.
                                    // If it is anything else, treat the colon as terminating the name.
                                    if (inputOffset + 1 < inputLength) {
                                        char nc = input.charAt(inputOffset + 1);
                                        if (nc == '*') {
                                            nextTokenValue = input.substring(nextTokenStartOffset, inputOffset);
                                            nextToken = new Token.Wildcard(nextTokenValue, "*");
                                            inputOffset += 2;
                                            return;
                                        } else if (!NameChecker.isNCNameStartChar(nc)) {
                                            // for example: "let $x:=2", "x:y:z", "x:2"
                                            // end the token before the colon
                                            nextTokenValue = input.substring(nextTokenStartOffset, inputOffset);
                                            nextToken = new Token.NameToken(nextTokenValue);
                                            return;
                                        }
                                    }
                                    foundColon = true;
                                } else {
                                    // This is the second colon
                                    breakLoop = true;
                                }
                                break;
                            case '.':
                            case '-':
                            case '_':
                                break;

                            default:
                                if (c < 0x80 && !Character.isLetterOrDigit(c)) {
                                    breakLoop = true;
                                }
                                break;
                        }
                        if (breakLoop) {
                            break;
                        }
                    }
                    nextTokenValue = input.substring(nextTokenStartOffset, inputOffset);
                    nextToken = new Token.NameToken(nextTokenValue);
                    return;
            }
        }
    }

    private boolean isFollowedBy(char ch) {
        return (inputOffset < inputLength && input.charAt(inputOffset) == ch);
    }

    private boolean isFollowedBy(String s) {
        int len = s.length();
        return inputOffset + len <= inputLength
                && input.substring(inputOffset, inputOffset + len).equals(s);
    }

    /**
     * Skip any whitespace or comments starting at position {@code inputOffset} in the
     * input string; on exit {@code inputOffset} is positioned beyond any such whitespace
     * or comments. The current line and column position are maintained accordingly.
     * @throws XPathException in the event of an unclosed comment.
     */
    private void skipWhitespaceAndComments() throws XPathException {
        while (inputOffset < inputLength) {
            char ch = input.charAt(inputOffset);
            if (ch == ' ' || ch == '\t' || ch == '\r') {
                inputOffset++;
            } else if (ch == '\n') {
                inputOffset++;
                incrementLineNumber();
            } else if (ch == '(' && inputOffset < (inputLength - 1) && input.charAt(inputOffset + 1) == ':') {
                inputOffset+=2;
                int nestingDepth = 1;
                while (nestingDepth > 0 && inputOffset < (inputLength - 1)) {
                    if (input.charAt(inputOffset) == '\n') {
                        incrementLineNumber();
                    } else if (input.charAt(inputOffset) == ':' &&
                            input.charAt(inputOffset + 1) == ')') {
                        nestingDepth--;
                        inputOffset++;
                    } else if (input.charAt(inputOffset) == '(' &&
                            input.charAt(inputOffset + 1) == ':') {
                        nestingDepth++;
                        inputOffset++;
                    }
                    inputOffset++;
                }
                if (nestingDepth > 0) {
                    throw new XPathException("Unclosed XPath comment");
                }
            } else {
                return;
            }
        }
    }

    @CSharpReplaceException(from = "java.lang.NumberFormatException", to = "System.FormatException")
    private static IntegerValue parseHexLiteral(String body) throws XPathException {
        try {
            if (body.length() < 16) {
                if (body.isEmpty()) {
                    // Handled specially because .NET code otherwise crashes
                    throw new XPathException("Empty hex literal");
                }
                long parsed = Long.parseLong(body, 16);
                return new Int64Value(parsed);
            } else {
                BigInteger big = new BigInteger(body, 16);
                return new BigIntegerValue(big);
            }
        } catch (NumberFormatException e) {
            throw new XPathException("Invalid hexadecimal literal");
        }
    }

    private static IntegerValue parseBinaryLiteral(String body) throws XPathException {
        if (body.length() < 64) {
            if (body.isEmpty()) {
                // Handled specially because .NET code otherwise crashes
                throw new XPathException("Empty binary literal");
            }
            long parsed = binaryStringToLong(body);
            return new Int64Value(parsed);
        } else {
            BigInteger big = new BigInteger(body, 2);
            return new BigIntegerValue(big);
        }
    }

    @CSharpReplaceBody(code = "return Convert.ToInt64(input, 2);")
    private static long binaryStringToLong(String input) {
        return Long.parseLong(input, 2);
    }

    private boolean isGreaterThanChar(char c) {
        return c == '>' /* || (languageLevel >= 40 && c == FULL_WIDTH_GT ) */;
    }

    /**
     * Indicate that we are starting to parse an embedded expression (enclosed in braces) within
     * content that is being read character-by-character. The current position must be immediately
     * after an opening brace. The current tokenization status is saved on a stack, and a new tokenization
     * is started at the current position, with the termination condition set to be the matching closing
     * brace.
     * @throws XPathException if, for example, a malformed comment is found
     */

    public void startEmbeddedExpression() throws XPathException {
        if (input.charAt(inputOffset - 1) != '{') {
            throw new AssertionError("Embedded expression must start immediately after '{'");
        }
        braceDepthStack.push(braceDepth);
        finishConditionStack.push(finishCondition);
        finishCondition = CLOSING_CURLY;
        braceDepth = 0;
        lookAhead();
        next();
    }

    /**
     * Indicate that we have finished parsing an embedded expression (within curly braces). The input
     * position must be the closing curly brace, and it is advanced to the next following character. The
     * tokenization state is reset from the saved stack.
     */

    public void endEmbeddedExpression() throws XPathException {
        if (inputOffset >= inputLength) {
            throw new XPathException("Reached end of input while processing an embedded expression: last token starts "
                                             + precedingToken).asStaticError();
        }
        if (input.charAt(inputOffset++) != '}') {
            throw new AssertionError("Embedded expression must end at '}'");
        }
        braceDepth = braceDepthStack.pop();
        finishCondition = finishConditionStack.pop();
    }

    /**
     * Construct a new tokenizer that includes a snapshot of the current state,
     * so it can be restored later. This mechanism is used to achieve a limited
     * backtracking capability and is not fully general.
     * @return a snapshot copy of this tokenizer
     */
    public Tokenizer checkPoint() {
        Tokenizer t2 = new Tokenizer();
        t2.copyFrom(this);
        return t2;
    }

    /**
     * Restore the state of this tokenizer from a snapshot
     * @param checkPoint the snapshot copy made using the {@link #checkPoint()} mechanism.
     */

    public void rollbackTo(Tokenizer checkPoint) {
        copyFrom(checkPoint);
    }

    private void copyFrom(Tokenizer z) {
        inputOffset = z.inputOffset;
        lineNumber = z.lineNumber;
        precedingToken = z.precedingToken;
        currentToken = z.currentToken;
        nextToken = z.nextToken;
        braceDepth = z.braceDepth;
    }


    /**
     * Reposition for reading characters. Needs care!
     */

    public void reposition(int offset) {
        inputOffset = offset;
    }

    /**
     * Read the next character directly. Used by the XQuery parser when parsing pseudo-XML syntax,
     * and also when processing string templates
     *
     * @return the next character from the input, or NUL at the end of the input
     */

    public char nextChar() {
        if (inputOffset < inputLength) {
            char c = input.charAt(inputOffset++);
            if (c == '\n') {
                incrementLineNumber();
                lineNumber++;
            }
            return c;
        } else {
            inputOffset++; // in case of an unreadChar()
            return NUL;
        }
    }

    /**
     * Look ahead to see what the next character will be, without changing the current state
     * @return the next character, or NUL at the end of the input.
     */
    public char peekChar() {
        if (inputOffset < inputLength) {
            return input.charAt(inputOffset);
        } else {
            return NUL;
        }
    }

    /**
     * Look ahead to see what the next character but one will be, without changing the current state
     * @return the next character but one, or NUL at the end of the input.
     */
    public char peekChar2() {
        if (inputOffset < inputLength - 1) {
            return input.charAt(inputOffset + 1);
        } else {
            return NUL;
        }
    }

    /**
     * Increment the line number, making a record of where in the input string the newline character occurred.
     */

    private void incrementLineNumber() {
        nextLineNumber++;
        if (newlineOffsets == null) {
            newlineOffsets = new ArrayList<>(20);
        }
        newlineOffsets.add(inputOffset - 1);
    }

    /**
     * Increment the line number, making a record of where in the input string the newline character occurred.
     *
     * @param offset the place in the input string where the newline occurred
     */

    public void incrementLineNumber(int offset) {
        nextLineNumber++;
        if (newlineOffsets == null) {
            newlineOffsets = new ArrayList<>(20);
        }
        newlineOffsets.add(offset);
    }

    /**
     * Step back one character. If this steps back to a previous line, adjust the line number.
     * If we have already read off the end of the input, do nothing.
     */

    public void unreadChar() {
        if (inputOffset > inputLength) {
            return;
        }
        if (input.charAt(--inputOffset) == '\n') {
            nextLineNumber--;
            lineNumber--;
            if (newlineOffsets != null) {
                newlineOffsets.remove(newlineOffsets.size() - 1);
            }
        }
    }

    /**
     * Get the most recently read text (for use in an error message)
     *
     * @param offset the offset of the offending token, if known, or -1 to use the current offset
     * @return a chunk of text leading up to the error
     */

    String recentText(int offset) {
        if (offset == -1) {
            // if no offset was supplied, we want the text immediately before the current reading position
            if (inputOffset > inputLength) {
                inputOffset = inputLength;
            }
            if (inputOffset < 34) {
                return input.substring(0, inputOffset);
            } else {
                return Whitespace.collapseWhitespace(
                        "..." + input.substring(inputOffset - 30, inputOffset));
            }
        } else {
            // if a specific offset was supplied, we want the text *starting* at that offset
            int end = offset + 30;
            if (end > inputLength) {
                end = inputLength;
            }
            return Whitespace.collapseWhitespace(
                    (offset > 0 ? "..." : "") +
                            input.substring(offset, end));
        }
    }

    /**
     * Get the line number of the current token
     *
     * @return the line number. Line numbers reported by the tokenizer start at zero.
     */

    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Get the column number of the current token
     *
     * @return the column number. Column numbers reported by the tokenizer start at zero.
     */

    public int getColumnNumber() {
        return (int) (getLineAndColumn(currentTokenStartOffset) & 0x7fffffff);
    }

    /**
     * Get the line and column number corresponding to a given offset in the input expression,
     * as a long value with the line number in the top half and the column number in the lower half.
     * Line and column numbers reported by the tokenizer start at zero.
     *
     * @param offset the byte offset in the expression
     * @return the line and column number, packed together
     */

    private long getLineAndColumn(int offset) {
        if (newlineOffsets == null) {
            return offset;
        }
        for (int line = newlineOffsets.size() - 1; line >= 0; line--) {
            int nloffset = newlineOffsets.get(line);
            if (offset > nloffset) {
                return ((long) (line+1) << 32) | (long) (offset - nloffset);
            }
        }
        return offset;
    }

    /**
     * Return the line number corresponding to a given offset in the expression
     *
     * @param offset the byte offset in the expression
     * @return the line number. Line and column numbers reported by the tokenizer start at zero.
     */

    public int getLineNumber(int offset) {
        return (int) (getLineAndColumn(offset) >> 32);
    }

    /**
     * Return the column number corresponding to a given offset in the expression
     *
     * @param offset the byte offset in the expression
     * @return the column number. Line and column numbers reported by the tokenizer start at zero.
     */

    public int getColumnNumber(int offset) {
        return (int) (getLineAndColumn(offset) & 0x7fffffff);
    }

    private static ARegularExpression piRegex = ARegularExpression.compile("\\i\\c*(\\s+.*)?", "");
    public boolean readDirectPIConstructor() {
        int end = input.indexOf("?>", inputOffset);
        if (end < 0) {
            return false;
        }
        if (!piRegex.matches(StringTool.fromCharSequence(input.substring(inputOffset+1, end)))) {
            return false;
        }
        nextToken = new Token.DirectProcessingInstructionConstructor(input.substring(inputOffset + 1, end));
        inputOffset = end + 2;
        return true;
    }

    public Token getPrecedingToken() {
        return precedingToken;
    }

    public boolean readDirectCommentConstructor() {
        // On entry, we have seen "<!-" and are positioned at "!"
        int start = inputOffset + 3;
        if (start > inputLength) {
            return false;
        }
        int end = input.indexOf("-->", start);
        if (end < 0) {
            return false;
        }
        if (!"!--".equals(input.substring(inputOffset, start))) {
            return false;
        }
        nextToken = new Token.DirectCommentConstructor(input.substring(start, end));
        inputOffset = end + 3;
        return true;
    }

    public boolean readDirectElementConstructor() {
        // On entry, we have seen "<X" where X is an NCNameStartChar, and we are positioned at "X"
        // This method checks whether the content looks plausibly like a direct element constructor,
        // and if so it returns true.
        // When called during look-ahead processing, the effect is to identify that an element
        // constructor is present, and return the token START_TAG with the position of inputOffset
        // unchanged. When next() returns the Token.START_TAG, it initiates parsing of the compound
        // token at this offset using its own tokenizer, and resets the position of the original
        // tokenizer on completion.

        int pos = inputOffset;
        while (pos < inputLength) {
            char c = input.charAt(pos);
            if (c == ':' || NameChecker.isNCNameChar(input.charAt(pos))) {
                pos++;
            } else {
                break;
            }
        }
        while (pos < inputLength) {
            if (Whitespace.isWhite(input.charAt(pos))) {
                pos++;
            } else {
                break;
            }
        }
        if (pos >= inputLength) {
            return false;
        }
        int ch = input.charAt(pos);
        if (ch == '/' && pos+1 < inputLength && input.charAt(pos+1) == '>') {
            return true;
        }
        if (ch == '>') {
            return true;
        }
        if (NameChecker.isNCNameChar(ch)) {
            // check that we have an attribute name followed by "="
            int eq = input.indexOf('=', pos);
            if (eq < 0) {
                return false;
            }
            return NameChecker.isQName(StringTool.codePoints(input.substring(pos, eq).trim()));
        }
        return false;
    }

}

