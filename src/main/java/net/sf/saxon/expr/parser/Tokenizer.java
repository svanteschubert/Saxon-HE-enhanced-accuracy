////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.om.NameChecker;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokenizer for expressions and inputs.
 * <p>This code was originally derived from James Clark's xt, though it has been greatly modified since.
 * See copyright notice at end of file.</p>
 */


public final class Tokenizer {


    private int state = DEFAULT_STATE;
    // we may need to make this a stack at some time

    /**
     * Initial default state of the Tokenizer
     */
    public static final int DEFAULT_STATE = 0;

    /**
     * State in which a name is NOT to be merged with what comes next, for example "("
     */
    public static final int BARE_NAME_STATE = 1;

    /**
     * State in which the next thing to be read is a SequenceType
     */
    public static final int SEQUENCE_TYPE_STATE = 2;
    /**
     * State in which the next thing to be read is an operator
     */

    public static final int OPERATOR_STATE = 3;

    /**
     * The number identifying the most recently read token
     */
    public int currentToken = Token.EOF;
    /**
     * The string value of the most recently read token
     */
    /*@Nullable*/ public String currentTokenValue = null;
    /**
     * The position in the input expression where the current token starts
     */
    public int currentTokenStartOffset = 0;
    /**
     * The number of the next token to be returned
     */
    private int nextToken = Token.EOF;
    /**
     * The string value of the next token to be returned
     */
    private String nextTokenValue = null;
    /**
     * The position in the expression of the start of the next token
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
     * The length of the input string
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
     * List containing the positions (offsets in the input string) at which newline characters
     * occur
     */

    private List<Integer> newlineOffsets = null;

    /**
     * The token number of the token that preceded the current token
     */
    private int precedingToken = Token.UNKNOWN;

    /**
     * The content of the preceding token
     */

    private String precedingTokenValue = "";

    /**
     * Flag to disallow "union" as a synonym for "|" when parsing XSLT 2.0 patterns
     */

    public boolean disallowUnionKeyword;

    /**
     * Flag to indicate that this is XQuery as distinct from XPath
     */

    public boolean isXQuery = false;

    /**
     * XPath language level: e.g. 2.0, 3.0, or 3.1
     */

    public int languageLevel = 20;

    /**
     * Flag to allow Saxon extensions
     */

    public boolean allowSaxonExtensions = false;

    public Tokenizer() {
    }

    /**
     * Get the current tokenizer state
     *
     * @return the current state
     */

    public int getState() {
        return state;
    }

    /**
     * Set the tokenizer into a special state
     *
     * @param state the new state
     */

    public void setState(int state) {
        this.state = state;
        if (state == DEFAULT_STATE) {
            // force the followsOperator() test to return true
            precedingToken = Token.UNKNOWN;
            precedingTokenValue = "";
            currentToken = Token.UNKNOWN;
        } else if (state == OPERATOR_STATE) {
            precedingToken = Token.RPAR;
            precedingTokenValue = ")";
            currentToken = Token.RPAR;
        }
    }

    //
    // Lexical analyser for expressions, queries, and XSLT patterns
    //

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
        nextTokenValue = null;
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
        precedingTokenValue = currentTokenValue;
        currentToken = nextToken;
        currentTokenValue = nextTokenValue;
        if (currentTokenValue == null) {
            currentTokenValue = "";
        }
        currentTokenStartOffset = nextTokenStartOffset;
        lineNumber = nextLineNumber;

        // disambiguate the current token based on the tokenizer state

        switch (currentToken) {
            case Token.NAME:
                int optype = getBinaryOp(currentTokenValue);
                if (optype != Token.UNKNOWN && !followsOperator(precedingToken)) {
                    currentToken = optype;
                }
                break;
            case Token.LT:
                if (isXQuery && followsOperator(precedingToken)) {
                    currentToken = Token.TAG;
                }
                break;
            case Token.STAR:
                if (!followsOperator(precedingToken)) {
                    currentToken = Token.MULT;
                }
                break;
        }

        if (currentToken == Token.TAG || currentToken == Token.RCURLY) {
            // No lookahead after encountering "<" at the start of an XML-like tag.
            // After an RCURLY, the parser must do an explicit lookahead() to continue
            // tokenizing; otherwise it can continue with direct character reading
            return;
        }

        int oldPrecedingToken = precedingToken;
        lookAhead();

        if (currentToken == Token.NAME) {
            if (state == BARE_NAME_STATE) {
                return;
            }
            if (oldPrecedingToken == Token.DOLLAR) {
                return;
            }
            switch (nextToken) {
                case Token.LPAR:
                    int op = getBinaryOp(currentTokenValue);
                    // the test on followsOperator() is to cater for an operator being used as a function name,
                    // e.g. is(): see XQTS test K-FunctionProlog-66
                    if (op == Token.UNKNOWN || followsOperator(oldPrecedingToken)) {
                        currentToken = getFunctionType(currentTokenValue);
                        lookAhead();    // swallow the "("
                    } else {
                        currentToken = op;
                    }
                    break;

                case Token.LCURLY:
                    if (!(state == SEQUENCE_TYPE_STATE)) {
                        currentToken = Token.KEYWORD_CURLY;
                        lookAhead();        // swallow the "{"
                    }
                    break;

                case Token.COLONCOLON:
                    lookAhead();
                    currentToken = Token.AXIS;
                    break;

                case Token.HASH:
                    lookAhead();
                    currentToken = Token.NAMED_FUNCTION_REF;
                    break;

                case Token.COLONSTAR:
                    lookAhead();
                    currentToken = Token.PREFIX;
                    break;

                case Token.DOLLAR:
                    switch (currentTokenValue) {
                        case "for":
                            currentToken = Token.FOR;
                            break;
                        case "some":
                            currentToken = Token.SOME;
                            break;
                        case "every":
                            currentToken = Token.EVERY;
                            break;
                        case "let":
                            currentToken = Token.LET;
                            break;
                        case "count":
                            currentToken = Token.COUNT;
                            break;
                        case "copy":
                            currentToken = Token.COPY;
                            break;
                    }
                    break;

                case Token.PERCENT:
                    if (currentTokenValue.equals("declare")) {
                        currentToken = Token.DECLARE_ANNOTATED;
                    }
                    break;

                case Token.NAME:
                    int candidate = -1;
                    switch (currentTokenValue) {
                        case "element":
                            candidate = Token.ELEMENT_QNAME;
                            break;
                        case "attribute":
                            candidate = Token.ATTRIBUTE_QNAME;
                            break;
                        case "processing-instruction":
                            candidate = Token.PI_QNAME;
                            break;
                        case "namespace":
                            candidate = Token.NAMESPACE_QNAME;
                            break;
                    }
                    if (candidate != -1) {
                        // <'element' QName '{'> constructor
                        // <'attribute' QName '{'> constructor
                        // <'processing-instruction' QName '{'> constructor
                        // <'namespace' QName '{'> constructor

                        String qname = nextTokenValue;
                        String saveTokenValue = currentTokenValue;
                        int savePosition = inputOffset;
                        lookAhead();
                        if (nextToken == Token.LCURLY) {
                            currentToken = candidate;
                            currentTokenValue = qname;
                            lookAhead();
                            return;
                        } else {
                            // backtrack (we don't have 2-token lookahead; this is the
                            // only case where it's needed. So we backtrack instead.)
                            currentToken = Token.NAME;
                            currentTokenValue = saveTokenValue;
                            inputOffset = savePosition;
                            nextToken = Token.NAME;
                            nextTokenValue = qname;
                        }

                    }
                    String composite = currentTokenValue + ' ' + nextTokenValue;
                    Integer val = Token.doubleKeywords.get(composite);
                    if (val == null) {
                        break;
                    } else {
                        currentToken = val;
                        currentTokenValue = composite;
                        // some tokens are actually triples
                        if (currentToken == Token.REPLACE_VALUE) {
                            // this one's a quadruplet - "replace value of node"
                            lookAhead();
                            if (nextToken != Token.NAME || !nextTokenValue.equals("of")) {
                                throw new XPathException("After '" + composite + "', expected 'of'");
                            }
                            lookAhead();
                            if (nextToken != Token.NAME || !nextTokenValue.equals("node")) {
                                throw new XPathException("After 'replace value of', expected 'node'");
                            }
                            nextToken = currentToken;   // to reestablish after-operator state
                        }
                        lookAhead();
                        return;
                    }
                default:
                    // no action needed
            }
        }
    }

    /**
     * Peek ahead at the next token
     */

    int peekAhead() {
        return nextToken;
    }

    /**
     * Force the current token to be treated as an operator if possible
     */

    public void treatCurrentAsOperator() {
        switch (currentToken) {
            case Token.NAME:
                int optype = getBinaryOp(currentTokenValue);
                if (optype != Token.UNKNOWN) {
                    currentToken = optype;
                }
                break;
            case Token.STAR:
                currentToken = Token.MULT;
                break;
        }
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
        precedingToken = nextToken;
        precedingTokenValue = nextTokenValue;
        nextTokenValue = null;
        nextTokenStartOffset = inputOffset;
        for (; ; ) {
            if (inputOffset >= inputLength) {
                nextToken = Token.EOF;
                return;
            }
            char c = input.charAt(inputOffset++);
            switch (c) {
                case '/':
                    if (inputOffset < inputLength
                            && input.charAt(inputOffset) == '/') {
                        inputOffset++;
                        nextToken = Token.SLASH_SLASH;
                        return;
                    }
                    nextToken = Token.SLASH;
                    return;
                case ':':
                    if (inputOffset < inputLength) {
                        if (input.charAt(inputOffset) == ':') {
                            inputOffset++;
                            nextToken = Token.COLONCOLON;
                            return;
                        } else if (input.charAt(inputOffset) == '=') {
                            nextToken = Token.ASSIGN;
                            inputOffset++;
                            return;
                        } else {     // if (input.charAt(inputOffset) == ' ') ??
                            nextToken = Token.COLON;
                            return;
                        }
                    }
                    throw new XPathException("Unexpected colon at start of token");
                case '@':
                    nextToken = Token.AT;
                    return;
                case '?':
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
                    return;
                case '}':
                    nextToken = Token.RCURLY;
                    return;
                case ';':
                    nextToken = Token.SEMICOLON;
                    state = DEFAULT_STATE;
                    return;
                case '%':
                    nextToken = Token.PERCENT;
                    return;
                case '(':
                    if (inputOffset < inputLength && input.charAt(inputOffset) == '#') {
                        inputOffset++;
                        int pragmaStart = inputOffset;
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
                        nextToken = Token.PRAGMA;
                        nextTokenValue = input.substring(pragmaStart, inputOffset - 2);
                        return;
                    }
                    if (inputOffset < inputLength && input.charAt(inputOffset) == ':') {
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
                        nextToken = Token.LPAR;
                    }
                    return;
                case ')':
                    nextToken = Token.RPAR;
                    return;
                case '+':
                    nextToken = Token.PLUS;
                    return;
                case '-':
                    nextToken = Token.MINUS;   // not detected if part of a name
                    return;
                case '=':
                    if (inputOffset < inputLength
                            && input.charAt(inputOffset) == '>') {
                        inputOffset++;
                        nextToken = Token.ARROW;
                        return;
                    }
                    nextToken = Token.EQUALS;
                    return;
                case '!':
                    if (inputOffset < inputLength
                            && input.charAt(inputOffset) == '=') {
                        inputOffset++;
                        nextToken = Token.NE;
                        return;
                    }
                    nextToken = Token.BANG;
                    return;
                case '*':
                    // disambiguation of MULT and STAR is now done later
                    if (inputOffset < inputLength
                            && input.charAt(inputOffset) == ':'
                            && inputOffset + 1 < inputLength
                            && (input.charAt(inputOffset + 1) > 127 || NameChecker.isNCNameStartChar(input.charAt(inputOffset + 1)))) {
                        inputOffset++;
                        nextToken = Token.SUFFIX;
                        return;
                    }
                    nextToken = Token.STAR;
                    return;
                case ',':
                    nextToken = Token.COMMA;
                    return;
                case '$':
                    nextToken = Token.DOLLAR;
                    return;
                case '|':
                    if (inputOffset < inputLength && input.charAt(inputOffset) == '|') {
                        inputOffset++;
                        nextToken = Token.CONCAT;
                        return;
                    }
                    nextToken = Token.UNION;
                    return;
                case '#':
                    nextToken = Token.HASH;
                    return;
                case '<':
                    if (inputOffset < inputLength
                            && input.charAt(inputOffset) == '=') {
                        inputOffset++;
                        nextToken = Token.LE;
                        return;
                    }
                    if (inputOffset < inputLength
                            && input.charAt(inputOffset) == '<') {
                        inputOffset++;
                        nextToken = Token.PRECEDES;
                        return;
                    }
                    nextToken = Token.LT;
                    return;
                case '>':
                    if (inputOffset < inputLength
                            && input.charAt(inputOffset) == '=') {
                        inputOffset++;
                        nextToken = Token.GE;
                        return;
                    }
                    if (inputOffset < inputLength
                            && input.charAt(inputOffset) == '>') {
                        inputOffset++;
                        nextToken = Token.FOLLOWS;
                        return;
                    }
                    nextToken = Token.GT;
                    return;
                case '.':
                    if (inputOffset < inputLength
                            && input.charAt(inputOffset) == '.') {
                        inputOffset++;
                        nextToken = Token.DOTDOT;
                        return;
                    }
                    if (inputOffset < inputLength
                            && input.charAt(inputOffset) == '{') {
                        inputOffset++;
                        nextTokenValue = ".";
                        nextToken = Token.KEYWORD_CURLY;
                        return;
                    }
                    if (inputOffset == inputLength
                            || input.charAt(inputOffset) < '0'
                            || input.charAt(inputOffset) > '9') {
                        nextToken = Token.DOT;
                        return;
                    }
                    // otherwise drop through: we have a number starting with a decimal point
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
                    // The logic here can return some tokens that are not legitimate numbers,
                    // for example "23e" or "1.0e+". However, this will only happen if the XPath
                    // expression as a whole is syntactically incorrect.
                    // These errors will be caught by the numeric constructor.
                    boolean allowE = true;
                    boolean allowSign = false;
                    boolean allowDot = true;
                    numloop:
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
                                    break numloop;
                                }
                                break;
                            case 'E':
                            case 'e':
                                if (allowE) {
                                    allowSign = true;
                                    allowE = false;
                                } else {
                                    inputOffset--;
                                    break numloop;
                                }
                                break;
                            case '+':
                            case '-':
                                if (allowSign) {
                                    allowSign = false;
                                } else {
                                    inputOffset--;
                                    break numloop;
                                }
                                break;
                            default:
                                if (('a' <= c && c <= 'z') || c > 127) {
                                    // this prevents the famous "10div 3"
                                    throw new XPathException("Separator needed after numeric literal");
                                }
                                inputOffset--;
                                break numloop;
                        }
                        if (inputOffset >= inputLength) {
                            break;
                        }
                        c = input.charAt(inputOffset++);
                    }
                    nextTokenValue = input.substring(nextTokenStartOffset, inputOffset);
                    nextToken = Token.NUMBER;
                    return;
                case '"':
                case '\'':
                    nextTokenValue = "";
                    while (true) {
                        inputOffset = input.indexOf(c, inputOffset);
                        if (inputOffset < 0) {
                            inputOffset = nextTokenStartOffset + 1;
                            throw new XPathException("Unmatched quote in expression");
                        }
                        nextTokenValue += input.substring(nextTokenStartOffset + 1, inputOffset++);
                        if (inputOffset < inputLength) {
                            char n = input.charAt(inputOffset);
                            if (n == c) {
                                // Doubled delimiters
                                nextTokenValue += c;
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
                    if (nextTokenValue.indexOf('\n') >= 0) {
                        for (int i = 0; i < nextTokenValue.length(); i++) {
                            if (nextTokenValue.charAt(i) == '\n') {
                                incrementLineNumber(nextTokenStartOffset + i + 1);
                            }
                        }
                    }
                    //nextTokenValue = nextTokenValue.intern();
                    nextToken = Token.STRING_LITERAL;
                    return;
                case '`':
                    if (isXQuery && inputOffset < inputLength - 1
                            && input.charAt(inputOffset) == '`'
                            && input.charAt(inputOffset + 1) == '[') {
                        inputOffset += 2;
                        int j = inputOffset;
                        int newlines = 0;
                        while (true) {
                            if (j >= inputLength) {
                                throw new XPathException("Unclosed string template in expression");
                            }
                            if (input.charAt(j) == '\n') {
                                newlines++;
                            } else if (input.charAt(j) == '`' && j + 1 < inputLength && input.charAt(j + 1) == '{') {
                                nextToken = Token.STRING_CONSTRUCTOR_INITIAL;
                                nextTokenValue = input.substring(inputOffset, j);
                                inputOffset = j + 2;
                                incrementLineNumber(newlines);
                                return;
                            } else if (input.charAt(j) == ']' && j + 2 < inputLength
                                    && input.charAt(j + 1) == '`' && input.charAt(j + 2) == '`') {
                                nextToken = Token.STRING_LITERAL_BACKTICKED;
                                // Can't return STRING_LITERAL because it's not accepted everywhere that a string literal is
                                nextTokenValue = input.substring(inputOffset, j);
                                inputOffset = j + 3;
                                incrementLineNumber(newlines);
                                return;
                            }
                            j++;
                        }
                    } else {
                        throw new XPathException("Invalid character '`' (backtick) in expression");
                    }
                case '\n':
                    incrementLineNumber();
                    // drop through
                case ' ':
                case '\t':
                case '\r':
                    nextTokenStartOffset = inputOffset;
                    break;
                case '\u00B6':

                case 'Q':
                    if (inputOffset < inputLength && input.charAt(inputOffset) == '{') {
                        // EQName, revised syntax as per bug 15399
                        int close = input.indexOf('}', inputOffset++);
                        if (close < inputOffset) {
                            throw new XPathException("Missing closing brace in EQName");
                        }
                        String uri = input.substring(inputOffset, close);
                        uri = Whitespace.collapseWhitespace(uri).toString(); // Bug 29708
                        if (uri.contains("{")) {
                            throw new XPathException("EQName must not contain opening brace");
                        }
                        inputOffset = close + 1;
                        int start = inputOffset;
                        boolean isStar = false;
                        while (inputOffset < inputLength) {
                            char c2 = input.charAt(inputOffset);
                            if (c2 > 0x80 || Character.isLetterOrDigit(c2) || c2 == '_' || c2 == '.' || c2 == '-') {
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
                        nextToken = isStar ? Token.PREFIX : Token.NAME;
                        return;


                    }
                    /* else fall through */
                default:
                    if (c < 0x80 && !Character.isLetter(c)) {
                        throw new XPathException("Invalid character '" + c + "' in expression");
                    }
                    /* fall through */
                case '_':
                    boolean foundColon = false;
                    loop:
                    for (; inputOffset < inputLength; inputOffset++) {
                        c = input.charAt(inputOffset);
                        switch (c) {
                            case ':':
                                if (!foundColon) {
                                    if (precedingToken == Token.QMARK || precedingToken == Token.SUFFIX) {
                                        // only NCName allowed after "? in a lookup expression, or after *:
                                        nextTokenValue = input.substring(nextTokenStartOffset, inputOffset);
                                        nextToken = Token.NAME;
                                        return;
                                    }
                                    if (inputOffset + 1 < inputLength) {
                                        char nc = input.charAt(inputOffset + 1);
                                        if (nc == ':') {
                                            nextTokenValue = input.substring(nextTokenStartOffset, inputOffset);
                                            nextToken = Token.AXIS;
                                            inputOffset += 2;
                                            return;
                                        } else if (nc == '*') {
                                            nextTokenValue = input.substring(nextTokenStartOffset, inputOffset);
                                            nextToken = Token.PREFIX;
                                            inputOffset += 2;
                                            return;
                                        } else if (!(nc == '_' || nc > 127 || Character.isLetter(nc))) {
                                            // for example: "let $x:=2", "x:y:z", "x:2"
                                            // end the token before the colon
                                            nextTokenValue = input.substring(nextTokenStartOffset, inputOffset);
                                            nextToken = Token.NAME;
                                            return;
                                        }
                                    }
                                    foundColon = true;
                                } else {
                                    break loop;
                                }
                                break;
                            case '.':
                            case '-':
                                // If the name up to the "-" or "." is a valid operator, and if the preceding token
                                // is such that an operator is valid here and an NCName isn't, then quit here (bug 2715)
                                if (precedingToken > Token.LAST_OPERATOR &&
                                        !(precedingToken == Token.QMARK || precedingToken == Token.SUFFIX) &&
                                        getBinaryOp(input.substring(nextTokenStartOffset, inputOffset)) != Token.UNKNOWN &&
                                        !(precedingToken == Token.NAME && getBinaryOp(precedingTokenValue) != Token.UNKNOWN)) {
                                    nextToken = getBinaryOp(input.substring(nextTokenStartOffset, inputOffset));
                                    return;
                                }
                                // else fall through
                            case '_':
                                break;

                            default:
                                if (c < 0x80 && !Character.isLetterOrDigit(c)) {
                                    break loop;
                                }
                                break;
                        }
                    }
                    nextTokenValue = input.substring(nextTokenStartOffset, inputOffset);
                    //nextTokenValue = nextTokenValue.intern();
                    nextToken = Token.NAME;
                    return;
            }
        }
    }


    /**
     * Identify a binary operator
     *
     * @param s String representation of the operator - must be interned
     * @return the token number of the operator, or UNKNOWN if it is not a
     *         known operator
     */

    int getBinaryOp(String s) {
        switch (s) {
            case "after":
                return Token.AFTER;
            case "and":
                return Token.AND;
            case "as":
                return Token.AS;
            case "before":
                return Token.BEFORE;
            case "case":
                return Token.CASE;
            case "default":
                return Token.DEFAULT;
            case "div":
                return Token.DIV;
            case "else":
                return Token.ELSE;
            case "eq":
                return Token.FEQ;
            case "except":
                return Token.EXCEPT;
            case "ge":
                return Token.FGE;
            case "gt":
                return Token.FGT;
            case "idiv":
                return Token.IDIV;
            case "in":
                return Token.IN;
            case "intersect":
                return Token.INTERSECT;
            case "into":
                return Token.INTO;
            case "is":
                return Token.IS;
            case "le":
                return Token.FLE;
            case "lt":
                return Token.FLT;
            case "mod":
                return Token.MOD;
            case "modify":
                return Token.MODIFY;
            case "ne":
                return Token.FNE;
            case "or":
                return Token.OR;
            case "otherwise":
                return Token.OTHERWISE;
            case "return":
                return Token.RETURN;
            case "satisfies":
                return Token.SATISFIES;
            case "then":
                return Token.THEN;
            case "to":
                return Token.TO;
            case "union":
                return Token.UNION;
            case "where":
                return Token.WHERE;
            case "with":
                return Token.WITH;
            case "orElse":
                return allowSaxonExtensions ? Token.OR_ELSE : Token.UNKNOWN;
            case "andAlso":
                return allowSaxonExtensions ? Token.AND_ALSO : Token.UNKNOWN;
            default:
                return Token.UNKNOWN;
        }
    }

    /**
     * Distinguish nodekind names, "if", and function names, which are all
     * followed by a "("
     *
     * @param s the name - must be interned
     * @return the token number
     */

    private int getFunctionType(String s) {
        switch (s) {
            case "if":
                return Token.IF;
            case "map":
            case "namespace-node":
            case "array":
            case "function":
                return languageLevel == 20 ? Token.FUNCTION : Token.NODEKIND;
            case "node":
            case "schema-attribute":
            case "schema-element":
            case "processing-instruction":
            case "empty-sequence":
            case "document-node":
            case "comment":
            case "element":
            case "item":
            case "text":
            case "attribute":
                return Token.NODEKIND;
            case "atomic":
            case "tuple":
            case "type":
            case "union":
                return allowSaxonExtensions ? Token.NODEKIND : Token.FUNCTION; // Saxon extension types
            case "switch":
                // Reserved in XPath 3.0, even though only used in XQuery
                return languageLevel == 20 ? Token.FUNCTION : Token.SWITCH;
            case "otherwise":
                return Token.OTHERWISE;
            case "typeswitch":
                return Token.TYPESWITCH;
            default:
                return Token.FUNCTION;
        }
    }

    /**
     * Test whether the previous token is an operator
     *
     * @param precedingToken the token to be tested
     * @return true if the previous token is an operator token
     */

    private boolean followsOperator(int precedingToken) {
        return precedingToken <= Token.LAST_OPERATOR;
    }

    /**
     * Read next character directly. Used by the XQuery parser when parsing pseudo-XML syntax
     *
     * @return the next character from the input
     * @throws StringIndexOutOfBoundsException
     *          if an attempt is made to read beyond
     *          the end of the string. This will only occur in the event of a syntax error in the
     *          input.
     */

    public char nextChar() throws StringIndexOutOfBoundsException {
        char c = input.charAt(inputOffset++);
        //c = normalizeLineEnding(c);
        if (c == '\n') {
            incrementLineNumber();
            lineNumber++;
        }
        return c;
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
            newlineOffsets = new ArrayList<Integer>(20);
        }
        newlineOffsets.add(offset);
    }

    /**
     * Step back one character. If this steps back to a previous line, adjust the line number.
     */

    public void unreadChar() {
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
                        "..." + input.substring(inputOffset - 30, inputOffset)).toString();
            }
        } else {
            // if a specific offset was supplied, we want the text *starting* at that offset
            int end = offset + 30;
            if (end > inputLength) {
                end = inputLength;
            }
            return Whitespace.collapseWhitespace(
                    (offset > 0 ? "..." : "") +
                            input.substring(offset, end)).toString();
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

}

/*

The following copyright notice is copied from the licence for xt, from which the
original version of this module was derived:
--------------------------------------------------------------------------------
Copyright (c) 1998, 1999 James Clark

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be included
in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED ``AS IS'', WITHOUT WARRANTY OF ANY KIND, EXPRESS
OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL JAMES CLARK BE LIABLE FOR ANY CLAIM, DAMAGES OR
OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of James Clark shall
not be used in advertising or otherwise to promote the sale, use or
other dealings in this Software without prior written authorization
from James Clark.
---------------------------------------------------------------------------
*/
