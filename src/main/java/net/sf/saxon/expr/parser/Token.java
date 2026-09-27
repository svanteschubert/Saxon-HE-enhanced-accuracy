////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.NumericValue;

/**
 * This class holds static constants and methods defining the lexical tokens used in
 * XPath and XQuery, and associated keywords.
 */

public class Token {

    private final String display;
    private final OperatorSymbol operatorSymbol;

    /**
     * Create a token that does not represent an operator symbol
     * @param display the display form of the token for diagnostics
     */
    public Token(String display) {
        this.display = display;
        this.operatorSymbol = OperatorSymbol.NOT_AN_OPERATOR;
    }

    /**
     * Create a token that represents an operator symbol
     * @param display the display form of the token for diagnostics
     * @param operator the operator represented by this token
     */
    public Token(String display, OperatorSymbol operator) {
        this.display = display;
        this.operatorSymbol = operator;
    }

    public String toString() {
        return display;
    }

    public OperatorSymbol getOperatorSymbol() {
        return operatorSymbol;
    }

    /**
     * Pseudo-token representing the end of the expression
     */
    public static final Token EOF = new Token("<eof>");
    public static final Token VBAR = new Token("|", OperatorSymbol.UNION);
    public static final Token SLASH = new Token("/");
    public static final Token AT = new Token("@");
    public static final Token LSQB = new Token("[");
    public static final Token RSQB = new Token("]");

    public static final Token LPAREN = new Token("(");
    public static final Token RPAREN = new Token(")");

    public static final Token LCURLY = new Token("{");
    public static final Token RCURLY = new Token("}");
    
    public static final Token EQUALS = new Token("=", OperatorSymbol.EQUALS);
    public static final Token COMMA = new Token(",");
    public static final Token SLASH_SLASH = new Token("//");
    public static final Token GT = new Token(">", OperatorSymbol.GT);
    public static final Token LT = new Token("<", OperatorSymbol.LT);
    public static final Token GE = new Token(">=", OperatorSymbol.GE);
    public static final Token LE = new Token("<=", OperatorSymbol.LE);
    public static final Token NE = new Token("!=", OperatorSymbol.NE);
    public static final Token PLUS = new Token("+", OperatorSymbol.PLUS);
    public static final Token MINUS = new Token("-", OperatorSymbol.MINUS);
    public static final Token MATH_MULT = new Token("×", OperatorSymbol.TIMES);
    public static final Token MATH_DIVIDE = new Token("÷", OperatorSymbol.DIV);

    public static final Token MAPPING_ARROW = new Token("=!>", OperatorSymbol.MAPPING_ARROW);
    public static final Token DOLLAR = new Token("$");
    public static final Token CONCAT = new Token("||", OperatorSymbol.CONCAT);
    public static final Token COLON = new Token(":");
    public static final Token FAT_ARROW = new Token("=>", OperatorSymbol.FAT_ARROW);
    public static final Token THIN_ARROW = new Token("->", OperatorSymbol.THIN_ARROW);
    public static final Token METHOD_CALL = new Token("?>", OperatorSymbol.METHOD_CALL);
    public static final Token BANG = new Token("!", OperatorSymbol.BANG);
    public static final Token COLON_COLON = new Token("::");
    public static final Token HASH = new Token("#");
    public static final Token QMARK = new Token("?", OperatorSymbol.LOOKUP);
    public static final Token QMARK_LSQB = new Token("?[", OperatorSymbol.AM_FILTER);
    public static final Token TILDE = new Token("~");
    public static final Token COLON_EQUALS = new Token(":=");

    public static final Token PERCENT = new Token("%");


    public static final Token DOT = new Token(".");

    public static final Token DOT_DOT = new Token("..");
    public static final Token STAR = new Token("*", OperatorSymbol.TIMES);
    public static final Token FOLLOWS = new Token(">>", OperatorSymbol.FOLLOWS);
    public static final Token PRECEDES = new Token("<<", OperatorSymbol.PRECEDES);
    public static final Token SEMICOLON = new Token(";");


    /**
     * Pseudo-token representing the start of the expression
     */
    public static final Token UNKNOWN = new Token("<unknown>");

    public static class NameToken extends Token {

        public NameToken(String name) {
            super(name, operatorForName(name));
        }

        public String toString() {
            return("`" + super.toString() + "`");
        }

        public String getValue() {
            return super.toString();
        }

        private static OperatorSymbol operatorForName(String name) {
            return switch (name) {
                case "and" -> OperatorSymbol.AND;
                case "or" -> OperatorSymbol.OR;
                case "div" -> OperatorSymbol.DIV;
                case "idiv" -> OperatorSymbol.IDIV;
                case "mod" -> OperatorSymbol.MOD;
                case "eq" -> OperatorSymbol.FEQ;
                case "ne" -> OperatorSymbol.FNE;
                case "lt" -> OperatorSymbol.FLT;
                case "le" -> OperatorSymbol.FLE;
                case "gt" -> OperatorSymbol.FGT;
                case "ge" -> OperatorSymbol.FGE;
                case "is" -> OperatorSymbol.IS;
                case "union" -> OperatorSymbol.UNION;
                case "intersect" -> OperatorSymbol.INTERSECT;
                case "except" -> OperatorSymbol.EXCEPT;
                case "otherwise" -> OperatorSymbol.OTHERWISE;
                case "to" -> OperatorSymbol.TO;
                case "follows" -> OperatorSymbol.FOLLOWS;
                case "follows-or-is" -> OperatorSymbol.FOLLOWS_OR_IS;
                case "precedes" -> OperatorSymbol.PRECEDES;
                case "precedes-or-is" -> OperatorSymbol.PRECEDES_OR_IS;
                case "is-not" -> OperatorSymbol.IS_NOT;
                default -> OperatorSymbol.NOT_AN_OPERATOR;
            };
        }
    }

    /**
     * Token representing a string literal. Note that expansion of built-in entities
     * such as <code>&amp;lt;</code> is left to the parser.
     */
    public static class StringLiteral extends Token {
        public StringLiteral(String value) {
            super(value);
        }
        public String toString() {
            return '"' + super.toString() + '"';
        }
        public String getValue() {
            return super.toString();
        }
    }

    /**
     * Token representing a numeric literal.
     */

    public static class NumericLiteral extends Token {
        private final NumericValue value;
        private final int radix;
        public NumericLiteral(String lexicalForm, NumericValue value, int radix) {
            super(lexicalForm);
            this.value = value;
            this.radix = radix;
        }
        public NumericValue getValue() {
            return value;
        }

        public int getRadix() {
            return radix;
        }
    }

    /**
     * Token representing a pragma, that is the construct `(#...#)` that
     * acts as the first part of an extension expression. The supplied value
     * is the entire pragma including its delimiters
     */
    public static class Pragma extends Token {
        public Pragma(String value) {
            super(value);
        }

    }

    /**
     * Token representing a partial wildcard: prefix:*, Q{uri}*, or *:suffix
     */

    public static class Wildcard extends Token {

        private final String prefix;
        private final String suffix;
        public Wildcard(String prefix, String suffix) {
            super(prefix.equals("*") ? "*:" + suffix : prefix + ":*");
            this.prefix = prefix;
            this.suffix = suffix;
        }
        public String getPrefix() {
            return prefix;
        }
        public String getSuffix() {
            return suffix;
        }
    }

    public static class DirectCommentConstructor extends Token {

        public DirectCommentConstructor(String content) {
            super(content);
        }

        public String getContent() {
            return super.toString();
        }

        public String toString() {
            return ("<!--" + super.toString() + "-->");
        }
    }

    public static class DirectProcessingInstructionConstructor extends Token {

        public DirectProcessingInstructionConstructor(String content) {
            super(content);
        }

        public String toString() {
            return ("<?" + getContent() + "?>");
        }

        public String getContent() {
            return super.toString();
        }

    }

    /**
     * A "complex token" is used to represent constructs like element constructors
     * and string templates that contain embedded expressions within arbitrary character
     * content..
     *
     * <p>When a complex token is encountered (during lookAhead processing) it is not
     * read to completion, it is saved for later processing. When the parser retrieves
     * a complex token it must parse it by reading individual characters.</p>
     *
     * <p>The complex token provides methods to read and unread characters and to
     * process embedded expressions. When the parser has finished reading the token,
     * it should call the close() method, which resets the tokenizer so that parsing
     * can continue normally.</p>
     */

    public static abstract class ComplexToken extends Token {

        protected final Tokenizer tokenizer;
        protected int startOffset;
        protected String tokenType;

        public ComplexToken(Tokenizer tokenizer, String input, int startOffset) {
            super(input.substring(startOffset, Math.min(input.length(), startOffset + 30)));
            this.tokenizer = tokenizer;
        }

        public void close() throws XPathException {
            tokenizer.currentToken = this;
            tokenizer.lookAhead();
            tokenizer.next();
        }

        public int getStartOffset() {
            return startOffset;
        }

    }

    public static class DirectElementConstructor extends ComplexToken {

        public DirectElementConstructor(Tokenizer tokenizer, String input, int startOffset) {
            super(tokenizer, input, startOffset);
            this.tokenType = "DirectElementConstructor";
        }
    }

    public static class StringTemplate extends ComplexToken {

        public StringTemplate(Tokenizer tokenizer, String input, int startOffset) {
            super(tokenizer, input, startOffset);
            this.tokenType = "StringTemplate";
            this.startOffset = startOffset;
        }
    }

    public static class StringConstructor extends ComplexToken {

        public StringConstructor(Tokenizer tokenizer, String input, int startOffset) {
            super(tokenizer, input, startOffset);
            this.tokenType = "StringConstructor";
        }

    }


}

