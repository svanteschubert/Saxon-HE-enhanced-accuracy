////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;


import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.flwor.Clause;
import net.sf.saxon.expr.flwor.LocalVariableBinding;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.functions.*;
import net.sf.saxon.functions.hof.DynamicPartialApply;
import net.sf.saxon.functions.hof.FunctionLiteral;
import net.sf.saxon.functions.hof.UnresolvedXQueryFunctionItem;
import net.sf.saxon.functions.hof.UserFunctionReference;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.functions.registry.VendorFunctionSetHE;
import net.sf.saxon.functions.registry.XPath31FunctionSet;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.ma.arrays.ArrayFunctionSet;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.arrays.SimpleArrayItem;
import net.sf.saxon.ma.arrays.SquareArrayConstructor;
import net.sf.saxon.ma.jnode.AnyJNodeType;
import net.sf.saxon.ma.jnode.JNodeType;
import net.sf.saxon.ma.jnode.RootJNodeType;
import net.sf.saxon.ma.jnode.SpecificJNodeType;
import net.sf.saxon.ma.map.*;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.CombinedNodeTest;
import net.sf.saxon.pattern.MultipleNodeKindTest;
import net.sf.saxon.pattern.SelectorTest;
import net.sf.saxon.pattern.UnionQNameTest;
import net.sf.saxon.pattern.nodetest.NodeTest;
import net.sf.saxon.pattern.nodetest.NodeTestStar;
import net.sf.saxon.pattern.qname.*;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.query.QueryModule;
import net.sf.saxon.query.XQueryFunction;
import net.sf.saxon.query.XQueryParser;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.s9api.UnprefixedElementMatchingPolicy;
import net.sf.saxon.str.StringTool;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.style.ExpressionContext;
import net.sf.saxon.style.SourceBinding;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.sxpath.IndependentContext;
import net.sf.saxon.sxpath.XPathVariable;
import net.sf.saxon.trans.*;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.transpile.CSharpSimpleEnum;
import net.sf.saxon.tree.util.IndexedStack;
import net.sf.saxon.type.*;
import net.sf.saxon.type.gnode.*;
import net.sf.saxon.value.*;
import net.sf.saxon.z.IntPredicateProxy;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;


/**
 * Parser for XPath expressions and XSLT patterns.
 * <p>This code was originally inspired by James Clark's xt but has been totally rewritten (several times)</p>
 * <p>The base class handles parsing of XPath 2.0, XPath 3.0 and XPath 3.1 syntax (switched by a languageVersion variable).
 * Subclasses refine this to handle XQuery syntax (1.0, 3.0 and 3.1) and XQuery Update syntax.</p>
 *
 */


public class XPathParser {

    protected Tokenizer t;
    protected StaticContext env;
    protected IndexedStack<LocalBinding> rangeVariables = new IndexedStack<>();
    // The stack holds a list of range variables that are in scope.
    // Each entry on the stack is a Binding object containing details
    // of the variable.

    public IndexedStack<InlineFunctionDetails> inlineFunctionStack = new IndexedStack<>();
    protected QNameParser qNameParser;
    protected ParserExtension parserExtension = new ParserExtension();

    protected IntPredicateProxy charChecker;

    protected boolean allowXPath30XSLTExtensions = false;
    protected boolean allowSaxonExtensions = false;

    protected boolean scanOnly = false;
    // scanOnly is set to true while attributes in direct element constructors
    // are being processed. We need to parse enclosed expressions in the attribute
    // in order to find the end of the attribute value, but we don't yet know the
    // full namespace context at this stage.

    private boolean allowAbsentExpression = false;
    // allowAbsentExpression is a flag that indicates that it is acceptable
    // for the expression to be empty (that is, to consist solely of whitespace and
    // comments). The result of parsing such an expression is equivalent to the
    // result of parsing an empty sequence literal, "()"

    /*@Nullable*/
    protected CodeInjector codeInjector = null;
    private Accelerator accelerator = null;


    @CSharpSimpleEnum
    public enum ParsedLanguage {XPATH, XSLT_PATTERN, SEQUENCE_TYPE, XQUERY, EXTENDED_ITEM_TYPE}

    protected ParsedLanguage language = ParsedLanguage.XPATH; // know which language we are parsing, for diagnostics

    protected int languageVersion = 20; // XPath language level. Note XQuery 1.0 == XPath 2.0.
    protected int catchDepth = 0;


    public record InlineFunctionDetails(
            IndexedStack<LocalBinding> outerVariables,
            // Local variables defined in the immediate outer scope (the father scope)
            List<LocalBinding> outerVariablesUsed,        // Local variables from the outer scope that are actually used
            List<UserFunctionParameter> implicitParams    // Parameters corresponding (1:1) with the above
    ) {
        public InlineFunctionDetails() {
            this(new IndexedStack<LocalBinding>(),
                 new ArrayList<LocalBinding>(4),
                 new ArrayList<UserFunctionParameter>(4));
        }

    }

    public interface Accelerator {

        /**
         * Attempt fast parsing of an expression, provided it is sufficiently simple.
         *
         * @param t          the tokenizer
         * @param env        the static context
         * @param expression the string containing expression to be parsed
         * @param start      start position within the input string
         * @param finished   either EOF or RCURLY, indicating how parsing should end
         * @return either the parsed expression, or null if it is erroneous or too
         * complex to parse.
         */

        Expression parse(Tokenizer t, StaticContext env, String expression, int start, Predicate<Tokenizer> finished);
    }

    /**
     * Create an expression parser
     */

    public XPathParser(StaticContext env) {
        this.env = env;
    }


    public boolean isAllowXPath40Syntax() {
        return languageVersion >= 40;
    }

    /**
     * Set a CodeInjector which can be used to modify or wrap expressions on the tree
     * as the expression is parsed and the tree is constructed. This is typically used
     * to add tracing code.
     *
     * @param injector the code injector to be used
     */

    public void setCodeInjector(/*@Nullable*/ CodeInjector injector) {
        this.codeInjector = injector;
    }

    /**
     * Set a CodeInjector which can be used to modify or wrap expressions on the tree
     * as the expression is parsed and the tree is constructed. This is typically used
     * to add tracing code.
     *
     * @return the code injector in use, if any; or null otherwise
     */

    /*@Nullable*/
    public CodeInjector getCodeInjector() {
        return codeInjector;
    }

    /**
     * Set an accelerator which can be used for fast parsing of special cases
     * @param accelerator a parsing accelerator
     */

    public void setAccelerator(Accelerator accelerator) {
        this.accelerator = accelerator;
    }

    /**
     * Get the tokenizer (the lexical analyzer)
     *
     * @return the tokenizer (the lexical analyzer)
     */

    public Tokenizer getTokenizer() {
        return t;
    }

    /**
     * Get the static context used by this expression parser
     *
     * @return the static context
     */

    public StaticContext getStaticContext() {
        return env;
    }

//    /**
//     * Set the default container for newly constructed expressions
//     *
//     * @param container the default container
//     */
//
//    public void setDefaultContainer(Container container) {
//        this.defaultContainer = container;
//    }
//
//    /**
//     * Get the default container for newly constructed expressions
//     *
//     * @return the default container
//     */
//
//    public Container getDefaultContainer() {
//        return defaultContainer;
//    }

    /**
     * Set a parser extension which can handle extensions to the XPath syntax, e.g. for
     * XQuery update extensions
     * @param extension a parser extension
     */

    public void setParserExtension(ParserExtension extension) {
        this.parserExtension = extension;
    }

    /**
     * Set the depth of nesting within try/catch
     *
     * @param depth the depth of nesting
     */

    public void setCatchDepth(int depth) {
        catchDepth = depth;
    }

    /**
     * Read the next token, catching any exception thrown by the tokenizer
     *
     * @throws XPathException if an invalid token is found
     */

    public void nextToken() throws XPathException {
        try {
            previousToken = t.currentToken;
            t.next();
        } catch (XPathException e) {
            grumble(e.getMessage());
        }
    }

    Token previousToken = Token.UNKNOWN;

    /**
     * Expect a given token; fail if the current token is different. Note that this method
     * does not read any tokens.
     *
     * @param token the expected token
     * @throws XPathException if the current token is not the expected
     *                        token
     */

    public void expect(Token token) throws XPathException {
        if (t.currentToken != token) {
            grumble((previousToken == Token.UNKNOWN ? "" : ("After " + previousToken + " ")) +
                            "expected " + token.toString() +
                            ", found " + t.currentToken.toString());
        }
    }

    /**
     * Skip past the current token, checking that it matches. Equivalent
     * to expect(token) followed by nextToken().
     * @param token the expected token
     * @throws XPathException if the current token is not the one expected
     */
    public void readToken(Token token) throws XPathException {
        expect(token);
        nextToken();
    }

    public AtomicValue parseConstant() throws XPathException {
        boolean negative = t.currentToken == Token.MINUS;
        if (negative) {
            if (languageVersion < 40) {
                grumble("Minus sign in annotation value requires 4.0 to be enabled");
                return null;
            }
            nextToken();
            if (!(t.currentToken instanceof Token.NumericLiteral)) {
                grumble("Minus sign in annotation parameter must be followed by a numeric literal");
            }
        }
        if (t.currentToken instanceof Token.StringLiteral str) {
            String value = str.getValue();
            nextToken();
            return new StringValue(value);
        } else if (t.currentToken instanceof Token.NumericLiteral num) {
            NumericValue value = num.getValue();
            if (negative) {
                value = value.negate();
            }
            nextToken();
            return value;
        } else if (t.currentToken == Token.HASH && languageVersion >= 40) {
            StructuredQName qn = parseEQName();
            return new QNameValue(qn, BuiltInAtomicType.QNAME);
        } else if ((isKeyword("true") || isKeyword("false")) && t.peekAhead() == Token.LPAREN) {
            BooleanValue value = null;
            if (isKeyword("true")) {
                value = BooleanValue.TRUE;
            } else if (isKeyword("false")) {
                value = BooleanValue.FALSE;
            } else {
                grumble("The only function calls allowed in an annotation are true() and false()");
            }
            if (languageVersion < 40) {
                grumble("Annotation values true() and false() require 4.0 to be enabled");
            }
            nextToken();
            readToken(Token.LPAREN);
            readToken(Token.RPAREN);
            return value;
        } else {
            grumble("Expected a constant, found " + t.currentToken);
            return null;
        }
    }


    public String expectStringLiteral() throws XPathException {
        if (t.currentToken instanceof Token.StringLiteral) {
            return ((Token.StringLiteral) t.currentToken).getValue();
        } else {
            grumble((previousToken == Token.UNKNOWN ? "" : ("After " + previousToken + ", ")) +
                            "expected string literal, found " + t.currentToken.toString());
            return null;
        }
    }

    public String expectName() throws XPathException {
        if (!(t.currentToken instanceof Token.NameToken)) {
            grumble((previousToken == Token.UNKNOWN ? "" : ("After " + previousToken + ", ")) +
                            "expected a name, found " + t.currentToken.toString());
        }
        return t.currentName();
    }

    /**
     * Expect the current token to be a name (perhaps a keyword). Return that name, after moving on
     * to the next token.
     * @return the name at the (old) current position
     * @throws XPathException if the current token is not a name
     */
    public String readName() throws XPathException {
        String name = expectName();
        nextToken();
        return name;
    }

    /**
     * Indicate that the current token is expected to be a specific name token,
     * triggering an error if it is not.
     * @param keyword the expected name
     * @throws XPathException if the current token differs from the expected name
     */
    public void expectKeyword(String keyword) throws XPathException {
        if (!isKeyword(keyword)) {
            grumble("expected '" + keyword + "', found " + t.currentToken.toString());
        }
    }

    /**
     * Skip past an expected keyword, failing if the current token is not that keyword
     * @param keyword the expected keyword
     * @throws XPathException if the current token is not the expected keyword
     */
    public void readKeyword(String keyword) throws XPathException {
        expectKeyword(keyword);
        nextToken();
    }

    /**
     * Report a syntax error (a static error with error code XPST0003)
     *
     * @param message the error message
     * @throws XPathException always thrown: an exception containing the
     *                        supplied message
     */

    public void grumble(String message) throws XPathException {
        grumble(message, language == ParsedLanguage.XSLT_PATTERN ? "XTSE0340" : "XPST0003");
    }

    /**
     * Report a static error
     *
     * @param message   the error message
     * @param errorCode the error code
     * @throws XPathException always thrown: an exception containing the
     *                        supplied message
     */

    public void grumble(String message, String errorCode) throws XPathException {
        grumble(message, new StructuredQName("", NamespaceUri.ERR, errorCode), t == null ? -1 : t.inputOffset);
    }

    /**
     * Report a static error, with location information
     *
     * @param message   the error message
     * @param errorCode the error code
     * @param offset    the coded location of the error, or -1 if the location of the current token should be used
     * @throws XPathException always thrown: an exception containing the
     *                        supplied message
     */

    public void grumble(String message, String errorCode, int offset) throws XPathException {
        grumble(message, new StructuredQName("", NamespaceUri.ERR, errorCode), offset);
    }

    /**
     * Report a static error
     *
     * @param message   the error message
     * @param errorCode the error code
     * @param offset    the coded location of the error, or -1 if the location of the current token should be used
     * @throws XPathException always thrown: an exception containing the
     *                        supplied message
     */

    protected void grumble(String message, StructuredQName errorCode, int offset) throws XPathException {
        if (errorCode == null) {
            errorCode = new StructuredQName("err", NamespaceUri.ERR, "XPST0003");
        }
        String nearbyText = null;
        int line = -1;
        int column = -1;
        if (t != null) {
            nearbyText = t.recentText(-1);
            if (offset == -1) {
                line = t.getLineNumber();
                column = t.getColumnNumber();
            } else {
                line = t.getLineNumber(offset);
                column = t.getColumnNumber(offset);
            }
        }

        Location loc = makeNestedLocation(env.getContainingLocation(), line, column, nearbyText);

        XPathException err = new XPathException(message)
                .withLocation(loc)
                .asStaticError()
                .withErrorCode(errorCode);
        err.setIsSyntaxError("XPST0003".equals(errorCode.getLocalPart()));
        err.setHostLanguage(getLanguage());
        throw err;
    }

    protected void grumble(String message, StructuredQName errorCode) throws XPathException {
        grumble(message, errorCode, -1);
    }


    /**
     * Output a warning message
     *
     * @param message the text of the message
     * @param errorCode the error code associated with the warning
     */

    protected void warning(String message, String errorCode) {
        if (!env.getConfiguration().getBooleanProperty(Feature.SUPPRESS_XPATH_WARNINGS)) {
            String s = t.recentText(-1);
            String prefix =
                    (message.startsWith("...") ? "near" : "in") +
                            ' ' + Err.wrap(s) + ":\n    ";
            env.issueWarning(prefix + message, errorCode, makeLocation());
        }
    }

    /**
     * Set the current language (XPath or XQuery, XSLT Pattern, or SequenceType)
     *
     * @param language one of the constants {@link ParsedLanguage#XPATH}, {@link ParsedLanguage#XQUERY},
     * {@link ParsedLanguage#XSLT_PATTERN}, {@link ParsedLanguage#SEQUENCE_TYPE} etc
     * @param version  The XPath or XQuery language version. For XQuery the value must be
     *                 10 (for "1.0"), 30 (for "3.0") or 31 (for "3.1"); for XPath it must be 20 (="2.0"),
     *                 30 (="3.0") or 31 (="3.1"). The value 305 is also recognized to mean XPath 3.0 plus
     *                 the extensions defined in XSLT 3.0
     */

    protected void setLanguage(ParsedLanguage language, int version) {
        if (version == 0) {
            version = 30; // default
        }
        if (version == 305) {
            version = 30;
            allowXPath30XSLTExtensions = true;
        }
        if (version == 40) {
            getStaticContext().getConfiguration().checkLicensedFeature(
                    Configuration.LicenseFeature.PROFESSIONAL_EDITION, "XPath 4.0 Syntax", -1);
        }
        switch (language) {
            case XPATH:
                if (!(version == 20 || version == 30 || version == 31 || version == 40)) {
                    throw new IllegalArgumentException("Unsupported language version " + version);
                }
                break;
            case XSLT_PATTERN:
            case SEQUENCE_TYPE:
                if (!(version == 20 || version == 30 || version == 31 || version == 40)) {
                    throw new IllegalArgumentException("Unsupported language version " + version);
                }
                break;
            case XQUERY:
                if (!(version == 10 || version == 30 || version == 31 || version == 40)) {
                    throw new IllegalArgumentException("Unsupported language version " + version);
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown language " + language);
        }
        this.language = language;
        this.languageVersion = version;

    }

    /**
     * Get the current language (XPath or XQuery)
     *
     * @return a string representation of the language being parsed, for use in error messages
     */

    protected String getLanguage() {
        return switch (language) {
            case XPATH -> "XPath";
            case XSLT_PATTERN -> "XSLT Pattern";
            case SEQUENCE_TYPE -> "SequenceType";
            case XQUERY -> "XQuery";
            default -> "XPath";
        };
    }

    /**
     * Ask if XPath 3.1 is in use
     *
     * @return true if XPath 3.1 syntax (and therefore XQuery 3.1 syntax) is permitted
     */

    public boolean isAllowXPath31Syntax() {
        return languageVersion >= 31;
    }

    /**
     * Set the QNameParser to be used while parsing
     *
     * @param qp the QNameParser
     */

    public void setQNameParser(QNameParser qp) {
        this.qNameParser = qp;
    }

    /**
     * Get the QNameParser to be used while parsing
     *
     * @return the QNameParser
     */

    public QNameParser getQNameParser() {
        return qNameParser;
    }

    /**
     * Display the current token in an error message
     *
     * @return the display representation of the token
     */
    /*@NotNull*/
    protected String currentTokenDisplay() {
        return t.currentToken.toString();
    }

    /**
     * Parse a string representing an expression. This will accept an XPath expression if called on an
     * ExpressionParser, or an XQuery expression if called on a QueryParser.
     *
     * @param expression the expression expressed as a String
     * @param start      offset within the string where parsing is to start
     * @param finished predicate that enables the tokenizer to decide whether it has reached
     *                 the end of the input. When this condition is satisfied the tokenizer
     *                 will return {@code Token#EOF} in response to a next() request.
     * @param env        the static context for the expression
     * @return an Expression object representing the result of parsing
     * @throws XPathException if the expression contains a syntax error
     */

    /*@NotNull*/
    public Expression parse(String expression, int start, Predicate<Tokenizer> finished, StaticContext env)
            throws XPathException {
        this.env = env;
        int languageVersion = env.getXPathVersion();
        if (languageVersion == 20 && language == ParsedLanguage.XQUERY) {
            languageVersion = 10;
        }
        setLanguage(language, languageVersion);

        Expression exp = null;
        int offset;
        if (accelerator != null &&
                env.getUnprefixedElementMatchingPolicy() == UnprefixedElementMatchingPolicy.DEFAULT_NAMESPACE &&
                (expression.length() - start < 30 || finished == Tokenizer.CLOSING_CURLY)) {
            // We need the tokenizer to be visible so that the caller can ask
            // about where the expression ended within the input string
            t = new Tokenizer();
            t.languageLevel = env.getXPathVersion();
            exp = accelerator.parse(t, env, expression, start, finished);
        }

        if (exp == null) {

//            qNameParser = new QNameParser(env.getNamespaceResolver())
//                .withAcceptEQName(languageVersion >= 30, languageVersion)
//                .withErrorOnBadSyntax(language == ParsedLanguage.XSLT_PATTERN ? "XTSE0340" : "XPST0003")
//                .withErrorOnUnresolvedPrefix("XPST0081");

            charChecker = env.getConfiguration().getValidCharacterChecker();
            t = new Tokenizer();
            t.languageLevel = env.getXPathVersion();
            t.setFinishCondition(finished);
            t.allowSaxonExtensions =
                    env.getConfiguration().getBooleanProperty(Feature.ALLOW_SYNTAX_EXTENSIONS) || t.languageLevel == 40;
            offset = t.currentTokenStartOffset;
            customizeTokenizer(t);
            try {
                t.tokenize(expression, start, -1);
            } catch (XPathException err) {
                grumble(err.getMessage());
            }
            if (t.currentToken == Token.EOF) {
                if (allowAbsentExpression) {
                    Expression result = Literal.makeEmptySequence();
                    result.setRetainedStaticContext(env.makeRetainedStaticContext());
                    setLocation(result);
                    return result;
                } else {
                    grumble("The expression is empty");
                }
            }
            exp = parseExpression();
            if (t.currentToken != Token.EOF) {
                grumble("Unexpected token " + currentTokenDisplay() + ": no further input expected");
            }
//            if (t.peekAhead() != Token.EOF && !finished.test(t)) {
//                if (t.currentToken == Token.EOF && finished.test(t)) {
//                    grumble("Missing curly brace after expression in value template", "XTSE0350");
//                } else {
//                    grumble("Unexpected token " + currentTokenDisplay() + " beyond end of expression");
//                }
//            }
            setLocation(exp, offset);
        }
        exp.setRetainedStaticContextThoroughly(env.makeRetainedStaticContext());
        //exp.verifyParentPointers();
        return exp;
    }

    /**
     * Callback to tailor the tokenizer
     *
     * @param t the Tokenizer to be customized
     */

    protected void customizeTokenizer(Tokenizer t) {
        // do nothing
    }

    /**
     * On completion of parsing, get the position in the input string of the first significant content
     * after the expression was terminated. This assumes that the parsing was invoked with a termination
     * condition other than the end of the string being reached.
     */

    public int getOffsetAfterParsing() {
        return t.currentTokenStartOffset;
    }


    /**
     * Parse a string representing a sequence type
     *
     * @param input the string, which should conform to the XPath SequenceType
     *              production
     * @param env   the static context
     * @return a SequenceType object representing the type
     * @throws XPathException if any error is encountered
     */

    public SequenceType parseSequenceType(String input, /*@NotNull*/ StaticContext env) throws XPathException {
        this.env = env;
        setLanguage(ParsedLanguage.SEQUENCE_TYPE, env.getXPathVersion());
        if (qNameParser == null) {
            qNameParser = new QNameParser(env.getNamespaceResolver());
            if (languageVersion >= 30) {
                qNameParser = qNameParser.withAcceptEQName(true, languageVersion);
            }
        }
        language = ParsedLanguage.SEQUENCE_TYPE;
        t = new Tokenizer();
        t.languageLevel = languageVersion;
        t.allowSaxonExtensions =
                env.getConfiguration().getBooleanProperty(Feature.ALLOW_SYNTAX_EXTENSIONS) || t.languageLevel == 40;
        try {
            t.tokenize(input, 0, -1);
        } catch (XPathException err) {
            grumble(err.getMessage());
        }
        SequenceType req = parseSequenceType();
        if (t.currentToken != Token.EOF) {
            grumble("Unexpected token " + currentTokenDisplay() + " beyond end of SequenceType");
        }
        return req;
    }

    /**
     * Parse a string representing an extended item type: specifically, the content of itemType
     * or nodeTest attributes in an exported package. As well as regular itemType syntax, these
     * allow combined node tests separated with "|", "except", or "intersect" operators. Expressions
     * using these operators will always be parenthesized.
     *
     * @param input the string, which should conform to the XPath SequenceType
     *              production
     * @param env   the static context
     * @return a SequenceType object representing the type
     * @throws XPathException if any error is encountered
     */

    public ItemType parseItemType(String input, StaticContext env) throws XPathException {
        this.env = env;
        t = new Tokenizer();
        languageVersion = t.languageLevel = env.getXPathVersion();
        try {
            t.tokenize(input, 0, -1);
        } catch (XPathException err) {
            grumble(err.getMessage());
        }
        ItemType req = parseItemType();
        if (t.currentToken != Token.EOF) {
            grumble("Unexpected token " + currentTokenDisplay() + " beyond end of ItemType");
        }
        return req;
    }

    /**
     * Parse a string representing a sequence type with syntax extensions used in exported stylesheets.
     * Also allows the extensions permitted in saxon:as, e.g. tuple types, type aliases
     *
     * @param input the string, which should conform to the XPath SequenceType
     *              production
     * @param env   the static context
     * @return a SequenceType object representing the type
     * @throws XPathException if any error is encountered
     */

    public SequenceType parseExtendedSequenceType(String input, StaticContext env) throws XPathException {
        this.env = env;
        language = ParsedLanguage.EXTENDED_ITEM_TYPE;
        t = new Tokenizer();
        t.languageLevel = languageVersion = 40;
        allowSaxonExtensions = t.allowSaxonExtensions = true;
        try {
            t.tokenize(input, 0, -1);
        } catch (XPathException err) {
            grumble(err.getMessage());
        }
        SequenceType req = parseSequenceType();
        if (t.currentToken != Token.EOF) {
            grumble("Unexpected token " + currentTokenDisplay() + " beyond end of SequenceType");
        }
        return req;
    }


    //////////////////////////////////////////////////////////////////////////////////
    //                     EXPRESSIONS                                              //
    //////////////////////////////////////////////////////////////////////////////////

    /**
     * Parse a top-level Expression:
     * ExprSingle ( ',' ExprSingle )*
     *
     * @return the Expression object that results from parsing
     * @throws XPathException if the expression contains a syntax error
     */

    /*@NotNull*/
    public Expression parseExpression() throws XPathException {
        int offset = t.currentTokenStartOffset;
        if (languageVersion >= 40) {
            Set<String> prefixes = new HashSet<>();
            if (tryKeywordPair("declare", "default")) {
                if (!(env instanceof StaticContextOverlay)) {
                    env = new StaticContextOverlay(env);
                }
                readKeyword("element");
                readKeyword("namespace");
                String uri = expectStringLiteral();
                nextToken();
                if (uri.equals("##any")) {
                    env.setUnprefixedElementMatchingPolicy(UnprefixedElementMatchingPolicy.ANY_NAMESPACE);
                } else {
                    NamespaceUri nsUri = NamespaceUri.of(uri);
                    if (nsUri == NamespaceUri.XML || nsUri == NamespaceUri.XMLNS) {
                        grumble("Namespace uri " + uri + " is disallowed", "XQST0070");
                    }
                    env.declarePrologNamespace("", nsUri);
                }
                readToken(Token.SEMICOLON);
            }
            while (tryKeywordPair("declare", "namespace")) {
                if (!(env instanceof StaticContextOverlay)) {
                    env = new StaticContextOverlay(env);
                }
                String prefix = readName();
                if (!NameChecker.isValidNCName(prefix)) {
                    grumble("Namespace prefix '" + prefix + "' is not a valid NCName", "XPST0003");
                }
                if (prefixes.contains(prefix)) {
                    grumble("Duplicate declaration of prefix '" + prefix + "'", "XQST0033");
                }
                if (prefix.equals("xmlns")) {
                    grumble("Reserved namespace prefix 'xmlns' cannot be declared", "XQST0070");
                }
                prefixes.add(prefix);
                readToken(Token.EQUALS);
                String uri = expectStringLiteral();
                nextToken();
                NamespaceUri nsUri = NamespaceUri.of(uri);
                if (prefix.equals("xml") || prefix.equals("xmlns")) {
                    grumble("Namespace prefix " + prefix + " is disallowed", "XQST0070");
                }
                if (nsUri == NamespaceUri.XML || nsUri == NamespaceUri.XMLNS) {
                    grumble("Namespace uri " + uri + " is disallowed", "XQST0070");
                }
                env.declarePrologNamespace(prefix, NamespaceUri.of(uri));
                readToken(Token.SEMICOLON);
            }
        }
        if (qNameParser == null) {
            qNameParser = new QNameParser(env.getNamespaceResolver())
                    .withAcceptEQName(languageVersion >= 30, languageVersion)
                    .withErrorOnBadSyntax(language == ParsedLanguage.XSLT_PATTERN ? "XTSE0340" : "XPST0003")
                    .withErrorOnUnresolvedPrefix("XPST0081");
        }
        Expression exp = parseExprSingle();
        ArrayList<Expression> list = null;
        while (t.currentToken == Token.COMMA) {
            // An expression containing a comma often contains many, so we accumulate all the
            // subexpressions into a list before creating the Block expression which reduces it to an array
            if (list == null) {
                list = new ArrayList<>(10);
                list.add(exp);
            }
            nextToken();
            Expression next = parseExprSingle();
            setLocation(next);
            list.add(next);
        }
        if (list != null) {
            exp = Block.makeBlock(list);
            setLocation(exp, offset);
        }
        return exp;
    }

    /**
     * Parse an ExprSingle
     *
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    /*@NotNull*/
    public Expression parseExprSingle() throws XPathException {
        Expression e = parserExtension.parseExtendedExprSingle(this);
        if (e != null) {
            return e;
        }
        // Fast path for a single-token expression (often found in function arguments)
        Token peek = t.peekAhead();
        if (peek == Token.EOF || peek == Token.COMMA || peek == Token.RPAREN || peek == Token.RSQB) {
            Token tok = t.currentToken;
            if (tok instanceof Token.StringLiteral) {
                return parseStringLiteral(true);
            } else if (tok instanceof Token.NumericLiteral) {
                return parseNumericLiteral(true);
            } else if (tok instanceof Token.NameToken) {
                return parseBasicStep(true);
            } else if (tok == Token.DOT) {
                nextToken();
                Expression cie = new ContextItemExpression();
                setLocation(cie);
                return cie;
            } else if (tok == Token.DOT_DOT) {
                nextToken();
                Expression pne = new AxisExpression(AxisInfo.PARENT, null);
                setLocation(pne);
                return pne;
            }
        }

        if (t.currentToken instanceof Token.NameToken) {
            Token afterName = t.peekAhead();
            if (afterName == Token.DOLLAR) {
                switch (t.currentName()) {
                    case "for":
                    case "let":
                        return parseFLWORExpression();
                    case "some":
                    case "every":
                        return parseQuantifiedExpression();
                }
            } else if (afterName instanceof Token.NameToken) {
                if (isKeyword("for")) {
                    return parseFLWORExpression();
                } else if (isKeyword("switch") && isKeyword(afterName, "case")) {
                    return parseSwitchExpression();
                }
            } else if (afterName == Token.LCURLY) {
                if (isKeyword("switch")) {
                    return parseSwitchExpression();
                } else if (isKeyword("try")) {
                    return parseTryCatchExpression();
                }
            } else if (afterName == Token.LPAREN) {
                if (isKeyword("if")) {
                    return parseIfExpression();
                } else if (isKeyword("switch")) {
                    return parseSwitchExpression();
                } else if (isKeyword("typeswitch")) {
                    return parseTypeswitchExpression();
                }
            }
        }
        return parseBinaryExpression(parseUnaryExpression(), 4);
    }


    /**
     * Parse a binary expression, using operator precedence parsing. This is used
     * to parse the part of the grammar consisting largely of binary operators
     * distinguished by precedence: from "or expressions" down to "unary expressions".
     * Algorithm for the mainstream binary operators is from Wikipedia article
     * on precedence parsing;  operator precedences are from the XQuery specification
     * appendix B.
     *
     * @param lhs           Left-hand side "basic expression"
     * @param minPrecedence the minimum precedence of an operator that is to be treated as not terminating the
     *                      current expression
     * @return the parsed expression
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    public Expression parseBinaryExpression(Expression lhs, int minPrecedence) throws XPathException {
        while (true) {
            OperatorSymbol operator = getCurrentOperatorSymbol();
            if (operator == OperatorSymbol.NOT_AN_OPERATOR) {
                break;
            }
            int offset = t.currentTokenStartOffset;
            int prec = OperatorInfo.operatorPrecedence(operator);
            if (prec < minPrecedence) {
                break;
            }
            switch (operator) {
                case INSTANCE_OF:
                case TREAT_AS:
                    nextToken();
                    nextToken();
                    SequenceType seq = parseSequenceType();
                    lhs = makeSequenceTypeExpression(lhs, operator, seq);
                    setLocation(lhs, offset);
//                    if (operatorPrecedence(currentTokenAsOperator()) >= prec) {
//                        grumble("Left operand of '" + Token.tokens[t.currentToken] + "' needs parentheses");
//                    }
                    break;
                case CAST_AS:
                case CASTABLE_AS:
                    nextToken();
                    nextToken();
                    boolean leftParenFollows = t.peekAhead() == Token.LPAREN;
                    CastingTarget at;
                    if (languageVersion >= 40 &&
                            (t.currentToken == Token.LPAREN || (leftParenFollows && t.currentToken instanceof Token.NameToken))) {
                        ItemType type = parseItemType();
                        if (type instanceof CastingTarget) {
                            at = (CastingTarget) type;
                        } else {
                            grumble("Item type " + type + " cannot be used as the target of a cast/castable expression", "XPST0080");
                            return null;
                        }
                    } else {
                        String name = expectName();
                        if (scanOnly) {
                            at = BuiltInAtomicType.STRING;
                        } else {
                            StructuredQName sq = null;
                            try {
                                sq = qNameParser.parse(name, env.getDefaultElementNamespace());
                            } catch (XPathException e) {
                                grumble(e.getMessage(), e.getErrorCodeQName());
                                assert false;
                            }
                            ItemType alias = env.resolveTypeAlias(sq);
                            if (alias != null) {
                                if (alias instanceof CastingTarget) {
                                    at = (CastingTarget) alias;
                                } else {
                                    grumble("The type " + alias + " cannot be used as the target of a cast", "XPST0080");
                                    at = null;
                                }
                            } else {
                                at = getSimpleType(name);
                            }
                        }
                        nextToken();
                    }
                    if (at == BuiltInAtomicType.ANY_ATOMIC) {
                        grumble("No value is castable to xs:anyAtomicType", "XPST0080");
                    }
                    if (at == BuiltInAtomicType.NOTATION) {
                        grumble("No value is castable to xs:NOTATION", "XPST0080");
                    }

                    boolean allowEmpty = t.currentToken == Token.QMARK;
                    if (allowEmpty) {
                        nextToken();
                    }
                    lhs = makeSingleTypeExpression(lhs, operator, at, allowEmpty);
                    setLocation(lhs, offset);
                    if (getCurrentOperatorPrecedence() >= prec) {
                        grumble("Left operand of '" + t.currentToken + "' needs parentheses");
                    }
                    break;
                case FAT_ARROW:
                    lhs = parseArrowPostfix(lhs);
                    break;
                case MAPPING_ARROW:
                    checkLanguageVersion40("the mapping arrow =!>");
                    lhs = parseMappingArrowPostfix(lhs);
                    break;
                case METHOD_CALL:
                    checkLanguageVersion40("the mapping arrow =!>");
                    lhs = parseMappingArrowPostfix(lhs);
                    break;
                default:
                    nextToken();
                    Expression rhs = parseUnaryExpression();
                    while (true) {
                        OperatorSymbol op2 = getCurrentOperatorSymbol();
                        if (op2 == OperatorSymbol.NOT_AN_OPERATOR) {
                            break;
                        }
                        int prec2 = OperatorInfo.operatorPrecedence(op2);
                        if (prec2 <= prec) {
                            break;
                        }
                        rhs = parseBinaryExpression(rhs, prec2);
                    }
                    if (getCurrentOperatorPrecedence() == prec && !allowMultipleOperators(operator)) {
                        String tok = t.currentToken.toString();
                        String message = "Left operand of " + tok + " needs parentheses";
                        if (tok.equals("<") || tok.equals(">")) {
                            // Example input: return <a>3</a><b>4</b> - bug 2659
                            message += ". Or perhaps an XQuery element constructor appears where it is not allowed";
                        }
                        grumble(message);
                    }
                    lhs = makeBinaryExpression(lhs, operator, rhs);
                    setLocation(lhs, offset);
                    break;
            }
        }
        return lhs;
    }

    private int getCurrentOperatorPrecedence() throws XPathException {
        return OperatorInfo.operatorPrecedence(getCurrentOperatorSymbol());
    }

    private OperatorSymbol getCurrentOperatorSymbol() throws XPathException {
        OperatorSymbol op = t.currentToken.getOperatorSymbol();
        if (languageVersion < 40 && t.currentToken instanceof Token.NameToken) {
            String tok = ((Token.NameToken) t.currentToken).getValue();
            if (tok.equals("precedes") || tok.equals("follows") || tok.equals("is-not")) {
                grumble("Operator '" + t.currentToken.toString() + "' requires 4.0 to be enabled");
            }
        }
        if (op != OperatorSymbol.NOT_AN_OPERATOR) {
            return op;
        }
        if (isKeywordPair("instance", "of")) {
            return OperatorSymbol.INSTANCE_OF;
        } else if (isKeywordPair("cast", "as")) {
            return OperatorSymbol.CAST_AS;
        } else if (isKeywordPair("castable", "as")) {
            return OperatorSymbol.CASTABLE_AS;
        } else if (isKeywordPair("treat", "as")) {
            return OperatorSymbol.TREAT_AS;
        }
        return OperatorSymbol.NOT_AN_OPERATOR;
    }

//    private OperatorSymbol readOperator() throws XPathException {
//        OperatorSymbol op = currentOperator();
//        if (op != OperatorSymbol.NOT_AN_OPERATOR) {
//            nextToken();
//        }
//        return op;
//    }

    private OperatorSymbol currentOperator() throws XPathException {
        OperatorSymbol op = t.currentToken.getOperatorSymbol();
        if (op == OperatorSymbol.NOT_AN_OPERATOR) {
            if (isKeywordPair("cast", "as")) {
                nextToken();
                return OperatorSymbol.CAST_AS;
            } else if (isKeywordPair("castable", "as")) {
                nextToken();
                return OperatorSymbol.CASTABLE_AS;
            } else if (isKeywordPair("treat", "as")) {
                nextToken();
                return OperatorSymbol.TREAT_AS;
            } else if (isKeywordPair("instance", "of")) {
                nextToken();
                return OperatorSymbol.INSTANCE_OF;
            }
        }
        return op;
    }


    private boolean allowMultipleOperators(OperatorSymbol op) {
        return switch (op) {
            case FEQ, FNE, FLE, FLT, FGE, FGT, EQUALS, NE, LE, LT, GE, GT, IS, IS_NOT, PRECEDES, FOLLOWS,
                 PRECEDES_OR_IS, FOLLOWS_OR_IS, TO -> false;
            default -> true;
        };
    }


    /*@NotNull*/
    private Expression makeBinaryExpression(Expression lhs, OperatorSymbol operator, Expression rhs) throws XPathException {
        switch (operator) {
            case OR:
                return new OrExpression(lhs, rhs);
            case AND:
                return new AndExpression(lhs, rhs);
            case FEQ:
            case FNE:
            case FLE:
            case FLT:
            case FGE:
            case FGT:
                return new ValueComparison(lhs, operator, rhs);
            case EQUALS:
            case NE:
            case LE:
            case LT:
            case GE:
            case GT:
                return env.getConfiguration().getTypeChecker(env.isInBackwardsCompatibleMode()).makeGeneralComparison(lhs, operator, rhs);
            case IS:
            case PRECEDES:
            case FOLLOWS:
                return new IdentityComparison(lhs, operator, rhs);
            case IS_NOT:
            case PRECEDES_OR_IS:
            case FOLLOWS_OR_IS:
                checkLanguageVersion40();
                return new IdentityComparison(lhs, operator, rhs);
            case TO:
                return new RangeExpression(lhs, rhs);
            case CONCAT: {
                if (languageVersion < 30) {
                    grumble("Concatenation operator ('||') requires XPath 3.0 to be enabled");
                }
                RetainedStaticContext rsc = new RetainedStaticContext(env);
                Configuration config = env.getConfiguration();
                BuiltInFunctionSet lib = config.getXPathFunctionSet(env.getXPathVersion());

                if (lhs.isCallOn(Concat.class)) {
                    Expression[] args = ((SystemFunctionCall) lhs).getArguments();
                    Expression[] newArgs = new Expression[args.length + 1];
                    System.arraycopy(args, 0, newArgs, 0, args.length);
                    newArgs[args.length] = rhs;
                    SystemFunction concat = lib.makeFunction("concat", newArgs.length);
                    concat.setRetainedStaticContext(rsc);
                    return concat.makeFunctionCall(newArgs);
                } else {
                    SystemFunction concat = lib.makeFunction("concat", 2);
                    concat.setRetainedStaticContext(rsc);
                    Expression[] args = new Expression[]{lhs, rhs};
                    return concat.makeFunctionCall(args);
                }
            }
            case PLUS:
            case MINUS:
            case TIMES:
            case DIV:
            case IDIV:
            case MOD:
                return env.getConfiguration().getTypeChecker(env.isInBackwardsCompatibleMode()).makeArithmeticExpression(lhs, operator, rhs);
            case OTHERWISE:
                return makeOtherwiseExpression(lhs, rhs);
            case UNION:
            case INTERSECT:
            case EXCEPT:
                return new VennExpression(lhs, operator, rhs);
            case THIN_ARROW:
                return new ContextValueSetter(lhs, rhs);
            default:
                throw new IllegalArgumentException(operator.toString());
        }
    }

    /**
     * XPath 4.0 extension: A otherwise B, returns if (exists(A)) then A else B
     * @param lhs the A expression
     * @param rhs the B expression
     * @return a conditional expression with the correct semantics
     */
    private Expression makeOtherwiseExpression(Expression lhs, Expression rhs) throws XPathException {
        checkLanguageVersion40("the `otherwise` operator");
        LetExpression let = new LetExpression();
        let.setVariableQName(new StructuredQName("vv", NamespaceUri.ANONYMOUS, "n" + lhs.hashCode()));
        let.setSequence(lhs);
        let.setRequiredType(SequenceType.ANY_SEQUENCE);
        LocalVariableReference v1 = new LocalVariableReference(let.getVariableQName());
        v1.setBinding(let);
        let.addReference(v1, false);
        LocalVariableReference v2 = new LocalVariableReference(let.getVariableQName());
        v2.setBinding(let);
        let.addReference(v2, false);
        RetainedStaticContext rsc = new RetainedStaticContext(env);
        Expression[] conditions = new Expression[]{SystemFunction.makeCall(
                "exists", rsc, v1), Literal.makeLiteral(BooleanValue.TRUE, lhs)};
        Expression[] actions = new Expression[]{v2, rhs};
        let.setAction(new Choose(conditions, actions));
        return let;
    }

    private Expression makeSequenceTypeExpression(Expression lhs, OperatorSymbol operator, SequenceType type) {
        return switch (operator) {
            case INSTANCE_OF -> new InstanceOfExpression(lhs, type);
            case TREAT_AS -> TreatExpression.make(lhs, type);
            default -> throw new IllegalArgumentException();
        };

    }

    private Expression makeSingleTypeExpression(Expression lhs, OperatorSymbol operator, CastingTarget type, boolean allowEmpty)
            throws XPathException {
        if (type instanceof AtomicType && !(type == ErrorType.getInstance())) {
            switch (operator) {
                case CASTABLE_AS:
                    CastableExpression castable = new CastableExpression(lhs, (AtomicType) type, allowEmpty);
                    if (lhs instanceof StringLiteral) {
                        castable.setOperandIsStringLiteral(true);
                    }
                    return castable;

                case CAST_AS:
                    CastExpression cast = new CastExpression(lhs, (AtomicType) type, allowEmpty);
                    if (lhs instanceof StringLiteral) {
                        cast.setOperandIsStringLiteral(true);
                    }
                    return cast;

                default:
                    throw new IllegalArgumentException();
            }
        } else if (languageVersion >= 30) {
            switch (operator) {
                case CASTABLE_AS:
                    if (type instanceof UnionType) {
                        NamespaceResolver resolver = env.getNamespaceResolver();
                        UnionCastableFunction ucf = new UnionCastableFunction((UnionType) type, resolver, allowEmpty);
                        return new StaticFunctionCall(ucf, new Expression[]{lhs});
                    } else if (type instanceof ListType) {
                        NamespaceResolver resolver = env.getNamespaceResolver();
                        ListCastableFunction lcf = new ListCastableFunction((ListType) type, resolver, allowEmpty);
                        return new StaticFunctionCall(lcf, new Expression[]{lhs});
                    }
                    break;
                case CAST_AS:
                    if (type instanceof UnionType) {
                        NamespaceResolver resolver = env.getNamespaceResolver();
                        UnionConstructorFunction ucf = new UnionConstructorFunction((UnionType) type, resolver, allowEmpty);
                        return new StaticFunctionCall(ucf, new Expression[]{lhs});
                    } else if (type instanceof ListType) {
                        NamespaceResolver resolver = env.getNamespaceResolver();
                        ListConstructorFunction lcf = new ListConstructorFunction((ListType) type, resolver, allowEmpty);
                        return new StaticFunctionCall(lcf, new Expression[]{lhs});
                    }
                    break;
                default:
                    throw new IllegalArgumentException();
            }
//            if (type == AnySimpleType.getInstance()) {
//                throw new XPathException("Cannot cast to xs:anySimpleType", "XPST0080");
//            } else {
            throw new XPathException("Cannot cast to " + type.getClass(), "XPST0051");
//            }
        } else {
            throw new XPathException("Casting to list or union types requires XPath 3.0 to be enabled", "XPST0051");
        }

    }

    /**
     * Parse a Typeswitch Expression.
     * This construct is XQuery-only, so the XPath version of this
     * method throws an error unconditionally
     *
     * @return the expression that results from the parsing
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    protected Expression parseTypeswitchExpression() throws XPathException {
        grumble("typeswitch is not allowed in XPath");
        return new ErrorExpression();
    }


    /**
     * Parse a Switch Expression.
     * This construct is XQuery-only.
     * SwitchExpr ::= "switch" "(" Expr ")" SwitchCaseClause+ "default" "return" ExprSingle
     * SwitchCaseClause ::= ("case" ExprSingle)+ "return" ExprSingle
     *
     * @return the parsed expression
     * @throws XPathException in the event of a syntax error
     */

    /*@NotNull*/
    protected Expression parseSwitchExpression() throws XPathException {
        grumble("switch is not allowed in XPath");
        return new ErrorExpression();
    }

    /**
     * Parse a Validate Expression.
     * This construct is XQuery-only, so the XPath version of this
     * method throws an error unconditionally
     *
     * @return the parsed expression; except that this version of the method always
     * throws an exception
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    protected Expression parseValidateExpression() throws XPathException {
        grumble("validate{} expressions are not allowed in XPath");
        return new ErrorExpression();
    }

    /**
     * Parse an update map|array Expression.
     * This construct is XQuery-only, so the XPath version of this
     * method throws an error unconditionally
     *
     * @return the parsed expression; except that this version of the method always
     * throws an exception
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    protected Expression parseDeepUpdateExpression() throws XPathException {
        grumble("update map/array expressions are not allowed in XPath");
        return new ErrorExpression();
    }

    /**
     * Parse an Extension Expression
     * This construct is XQuery-only, so the XPath version of this
     * method throws an error unconditionally
     *
     * @return the parsed expression; except that this version of the method
     * always throws an exception
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    protected Expression parseExtensionExpression() throws XPathException {
        grumble("extension expressions (#...#) are not allowed in XPath");
        return new ErrorExpression();
    }


    /**
     * Parse a try/catch Expression
     * This construct is XQuery-3.0 only, so the XPath version of this
     * method throws an error unconditionally
     *
     * @return the parsed expression; except that this version of the method
     * always throws an exception
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    protected Expression parseTryCatchExpression() throws XPathException {
        grumble("try/catch expressions are not allowed in XPath");
        return new ErrorExpression();
    }

    /**
     * Parse a FOR or LET expression:
     * for 'member'? $x in expr (',' 'member'? $y in expr)* 'return' expr
     * let $x := expr (', $y := expr)* 'return' expr
     * This version of the method handles the subset of the FLWOR syntax allowed in XPath
     *
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    protected Expression parseFLWORExpression() throws XPathException {
        if (isKeyword("let") && languageVersion < 30) {
            grumble("'let' is not permitted in XPath 2.0");
        }
        if (isKeyword("let")) {
            return parseLetExpression();
        } else {
            return parseForExpression(false);
        }
    }

    private Expression parseForExpression(boolean continuation) throws XPathException {
        ForQualifier iterand = ForQualifier.FOR_ITEM;
        Token second = t.peekAhead();
        if (isKeyword(second, "sliding") || isKeyword(second, "tumbling")) {
            grumble("sliding/tumbling windows can only be used in XQuery");
        }
        if (isKeyword(second, "member")) {
            iterand = ForQualifier.FOR_MEMBER;
        } else if (isKeyword(second, "key")) {
            iterand = ForQualifier.FOR_KEY;
        } else if (isKeyword(second, "value")) {
            iterand = ForQualifier.FOR_VALUE;
        }
        if (iterand != ForQualifier.FOR_ITEM && languageVersion < 40) {
            grumble("'for member/key/value' requires XPath 4.0 to be enabled");
        }

        ForExpression result = new ForExpression();
        Assignation target = result;
        int offset = t.currentTokenStartOffset;
        setLocation(result, offset);
        int numberOfVariables = 0;
        if (continuation) {
            // we are currently positioned on a "," within a multi-variable for expression
            nextToken();
            if (languageVersion >= 40) {
                if (tryKeyword("member")) {
                    iterand = ForQualifier.FOR_MEMBER;
                } else if (tryKeyword("key")) {
                    iterand = ForQualifier.FOR_KEY;
                } else if (tryKeyword("value")) {
                    iterand = ForQualifier.FOR_VALUE;
                } else {
                    iterand = ForQualifier.FOR_ITEM;
                }
            }
        } else {
            // we are currently positioned on the "for" or "for member/key/value" token
            nextToken();
            if (iterand != ForQualifier.FOR_ITEM) {
                nextToken();
            }
        }
        SequenceType requiredType = SequenceType.SINGLE_ITEM;
        if (iterand == ForQualifier.FOR_MEMBER || iterand == ForQualifier.FOR_VALUE) {
            requiredType = SequenceType.ANY_SEQUENCE;
        } else if (iterand == ForQualifier.FOR_KEY) {
            requiredType = SequenceType.SINGLE_ATOMIC;
        }

        final StructuredQName varQName = readVariableName();
        result.setVariableQName(varQName);
        //String positionVar = null;

        if (languageVersion >= 40) {
            requiredType = readOptionalAsClause(requiredType);
        }

        StructuredQName varQName2 = null;
        SequenceType requiredType2 = null;
        if (iterand == ForQualifier.FOR_KEY && isKeyword("value")) {
            nextToken();
            varQName2 = readVariableName();
            if (varQName2.equals(varQName)) {
                grumble("Range variables in 'for key/value' expression must have distinct names", "XQST0089");
            }
            requiredType2 = readOptionalAsClause(null);
        }

        LocalVariableBinding positionBinding = null;
        if (languageVersion >= 40 && isKeyword("at")) {
            nextToken();
            StructuredQName positionVarQName = readVariableName();
            if (positionVarQName.equals(varQName) || positionVarQName.equals(varQName2)) {
                grumble("Position variable in 'for' expression has the same name as the range variable", "XQST0089");
            }
            positionBinding = new LocalVariableBinding(positionVarQName, BuiltInAtomicType.INTEGER.one());
            result.setPositionVariable(positionBinding);
        }

        // process the "in" clause
        readKeyword("in");
        Expression collection = parseExprSingle();

        // "for member $m in $array" compiles to
        // "for $temp in array:members($array) let $m := $temp?value"

        // "for key $k value $v in $map" compiles to
        // "for $temp in map:pairs($map) let $k := $temp?key, $v := $temp?value"

        if (iterand != ForQualifier.FOR_ITEM) {
            if (iterand == ForQualifier.FOR_MEMBER) {
                collection = ArrayFunctionSet.getInstance(40).makeFunction("members", 1).makeFunctionCall(collection);
            } else {
                collection = MapFunctionSet.getInstance(40).makeFunction("entries", 1).makeFunctionCall(collection);
            }
            result.setRequiredType(SequenceType.SINGLE_ITEM);
            result.setVariableQName(
                    new StructuredQName("vv", NamespaceUri.SAXON_GENERATED_VARIABLE, "fm" + result.hashCode()));
            result.setSequence(collection);
            declareRangeVariable(result);
            numberOfVariables++;
            if (positionBinding != null) {
                declareRangeVariable(positionBinding);
                numberOfVariables++;
            }
            if (iterand == ForQualifier.FOR_MEMBER) {
                LetExpression temp = new LetExpression();
                setLocation(temp, offset);
                temp.setRequiredType(requiredType);
                temp.setVariableQName(varQName);
                LocalVariableReference tempRef = new LocalVariableReference(result);
                LookupExpression lookup = new LookupExpression(tempRef,
                                                               new StringLiteral("value"));
                temp.setSequence(lookup);
                declareRangeVariable(temp);
                numberOfVariables++;
                result.setAction(temp);
                target = temp;
            } else if (iterand == ForQualifier.FOR_VALUE) {
                LetExpression temp = new LetExpression();
                setLocation(temp, offset);
                temp.setRequiredType(requiredType);
                temp.setVariableQName(varQName);
                LocalVariableReference tempRef = new LocalVariableReference(result);
                Expression valueGetter = MapFunctionSet.getInstance(40).makeFunction("items", 1).makeFunctionCall(tempRef);
                temp.setSequence(valueGetter);
                declareRangeVariable(temp);
                numberOfVariables++;
                result.setAction(temp);
                target = temp;
            } else { //if (iterand == ForExpression.Qualifier.FOR_KEY) {
                LetExpression temp = new LetExpression();
                setLocation(temp, offset);
                temp.setRequiredType(requiredType);
                temp.setVariableQName(varQName);
                LocalVariableReference tempRef = new LocalVariableReference(result);
                Expression keyGetter = MapFunctionSet.getInstance(40).makeFunction("keys", 1).makeFunctionCall(tempRef);
                temp.setSequence(keyGetter);
                declareRangeVariable(temp);
                numberOfVariables++;
                result.setAction(temp);
                target = temp;
                if (varQName2 != null) {
                    LetExpression temp2 = new LetExpression();
                    setLocation(temp2, offset);
                    temp2.setRequiredType(requiredType2 == null ? SequenceType.ANY_SEQUENCE : requiredType2);
                    temp2.setVariableQName(varQName2);
                    LocalVariableReference tempRef2 = new LocalVariableReference(result);
                    Expression valueGetter = MapFunctionSet.getInstance(40).makeFunction("items", 1).makeFunctionCall(tempRef2);
                    temp2.setSequence(valueGetter);
                    declareRangeVariable(temp2);
                    numberOfVariables++;
                    target.setAction(temp2);
                    target = temp2;
                }
            }
        } else {
            result.setRequiredType(requiredType);
            result.setSequence(collection);
            declareRangeVariable(result);
            numberOfVariables++;
            if (positionBinding != null) {
                declareRangeVariable(positionBinding);
                numberOfVariables++;
            }
        }

        Expression action;
        if (tryKeyword("return")) {
            action = parseExprSingle();
        } else if (t.currentToken == Token.COMMA) {
            action = parseForExpression(true);
        } else if (languageVersion >= 40 && isKeyword("for")) {
            action = parseForExpression(false);
        } else if (languageVersion >= 40 && isKeyword("let")) {
            action = parseLetExpression();
        } else {
            grumble("In 'for' expression, expected ',', 'for', 'let', or 'return', but found " + t.currentToken);
            return null;
        }

        target.setAction(action);

        // undeclare all the range variables

        for (int i = 0; i < numberOfVariables; i++) {
            undeclareRangeVariable();
        }

        return makeTracer(result, result.getVariableQName());
    }

    // Parse the component variable bindings of a 4.0 destructured assignment
    protected void gatherComponents(List<StructuredQName> componentNames,
                                    List<SequenceType> componentTypes,
                                    SequenceType defaultType,
                                    Token closingToken) throws XPathException {
        nextToken();
        while (true) {
            componentNames.add(readVariableName());
            componentTypes.add(readOptionalAsClause(defaultType));
            if (t.currentToken == closingToken) {
                nextToken();
                return;
            } else {
                readToken(Token.COMMA);
            }
        }
    }

    private Expression parseLetExpression() throws XPathException {
        int clauses = 0;
        int offset;
        Assignation first = null;
        Assignation previous = null;
        do {
            offset = t.currentTokenStartOffset;
            nextToken();

            readToken(Token.DOLLAR);

            StructuredQName var;
            SequenceType requiredType = SequenceType.ANY_SEQUENCE;

            List<StructuredQName> componentNames = new ArrayList<>(2);
            List<SequenceType> componentTypes = new ArrayList<>(2);
            String destructure = null;
            if (languageVersion >= 40 && t.currentToken == Token.LPAREN) {
                var = new StructuredQName("vv", NamespaceUri.SAXON_GENERATED_VARIABLE, "seq" + offset);
                gatherComponents(componentNames, componentTypes, SequenceType.ANY_SEQUENCE, Token.RPAREN);
                destructure = "seq";
            } else if (languageVersion >= 40 && t.currentToken == Token.LSQB) {
                var = new StructuredQName("vv", NamespaceUri.SAXON_GENERATED_VARIABLE, "arr" + offset);
                requiredType = ArrayItemType.SINGLE_ARRAY;
                gatherComponents(componentNames, componentTypes, SequenceType.ANY_SEQUENCE, Token.RSQB);
                destructure = "arr";
            } else if (languageVersion >= 40 && t.currentToken == Token.LCURLY) {
                var = new StructuredQName("vv", NamespaceUri.SAXON_GENERATED_VARIABLE, "map" + offset);
                requiredType = SequenceType.SINGLE_MAP;
                gatherComponents(componentNames, componentTypes, SequenceType.ANY_SEQUENCE, Token.RCURLY);
                destructure = "map";
            } else {
                String name = readName();
                var = makeStructuredQName(name, NamespaceUri.NULL);
            }
            if (languageVersion >= 40) {
                requiredType = readOptionalAsClause(SequenceType.ANY_SEQUENCE);
            }

            // declare the range variable
            Assignation firstClause;
            Assignation lastClause;
            firstClause = lastClause = new LetExpression();
            firstClause.setRequiredType(requiredType);

            clauses++;
            setLocation(firstClause, offset);
            setLocation(lastClause, offset);
            lastClause.setVariableQName(var);


            // process the  ":=" clause
            readToken(Token.COLON_EQUALS);
            Expression collection = parseExprSingle();
            firstClause.setSequence(collection);
            declareRangeVariable(lastClause);
            if (previous == null) {
                first = firstClause;
            } else {
                previous.setAction(firstClause);
            }
            previous = lastClause;

            // For a destructuring assignment, declare the component variables

            if (destructure != null) {
                switch (destructure) {
                    case "seq":
                        for (int c = 0; c < componentNames.size(); c++) {
                            Expression componentValue;
                            if (c < componentNames.size() - 1) {
                                componentValue = new SubscriptExpression(new LocalVariableReference(lastClause),
                                                                         Literal.makeLiteral(Int64Value.makeIntegerValue(c + 1)));
                            } else {
                                componentValue = new TailExpression(new LocalVariableReference(lastClause), c + 1);
                            }
                            LetExpression componentBinding = new LetExpression();
                            componentBinding.setVariableQName(componentNames.get(c));
                            componentBinding.setRequiredType(componentTypes.get(c));
                            componentBinding.setSequence(componentValue);
                            declareRangeVariable(componentBinding);
                            clauses++;
                            previous.setAction(componentBinding);
                            previous = componentBinding;
                        }
                        break;
                    case "arr":
                        for (int c = 0; c < componentNames.size(); c++) {
                            Expression componentValue = ArrayFunctionSet.getInstance(40).makeFunction("get", 2).makeFunctionCall(
                                    new LocalVariableReference(lastClause),
                                    Literal.makeLiteral(Int64Value.makeIntegerValue(c + 1)));
                            LetExpression componentBinding = new LetExpression();
                            componentBinding.setVariableQName(componentNames.get(c));
                            componentBinding.setRequiredType(componentTypes.get(c));
                            componentBinding.setSequence(componentValue);
                            declareRangeVariable(componentBinding);
                            clauses++;
                            previous.setAction(componentBinding);
                            previous = componentBinding;
                        }
                        break;
                    case "map":
                        for (int c = 0; c < componentNames.size(); c++) {
                            Expression componentValue = MapFunctionSet.getInstance(40).makeFunction("get", 2).makeFunctionCall(
                                    new LocalVariableReference(lastClause),
                                    new StringLiteral(componentNames.get(c).getLocalPart()));
                            LetExpression componentBinding = new LetExpression();
                            componentBinding.setVariableQName(componentNames.get(c));
                            componentBinding.setRequiredType(componentTypes.get(c));
                            componentBinding.setSequence(componentValue);
                            declareRangeVariable(componentBinding);
                            clauses++;
                            previous.setAction(componentBinding);
                            previous = componentBinding;
                        }
                        break;
                    default:
                        throw new IllegalStateException();
                }
            }
        } while (t.currentToken == Token.COMMA || (languageVersion >= 40 && isKeyword("let")));

        // process the "return" expression (called the "action") - or in 4.0 a FOR expression
        if (!(languageVersion >= 40 && isKeyword("for"))) {
            readKeyword("return");
        }
        previous.setAction(parseExprSingle());

        // undeclare all the range variables

        for (int i = 0; i < clauses; i++) {
            undeclareRangeVariable();
        }
        return makeTracer(first, first.getVariableQName());
    }


    /**
     * Parse a quantified expression:
     * (some|every) $x in expr (',' $y in expr)* 'satisfies' expr
     *
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    private Expression parseQuantifiedExpression() throws XPathException {
        int clauses = 0;
        boolean isSome = isKeyword("some");
        QuantifiedExpression first = null;
        QuantifiedExpression previous = null;
        do {
            int offset = t.currentTokenStartOffset;
            nextToken();
            StructuredQName var = readVariableName();
            clauses++;

            // declare the range variable
            QuantifiedExpression v = new QuantifiedExpression();
            v.setVariableQName(var);
            v.setRequiredType(SequenceType.SINGLE_ITEM);
            v.setOperator(isSome ? QuantifiedExpression.Qualifier.SOME : QuantifiedExpression.Qualifier.EVERY);
            setLocation(v, offset);


            if (isKeyword("as") && (language == ParsedLanguage.XQUERY || languageVersion >= 40)) {
                // We use this path for quantified expressions in XQuery and XPath 4.0, which permit an "as" clause
                nextToken();
                SequenceType type = parseSequenceType();
                if (type.getCardinality() != StaticProperty.EXACTLY_ONE) {
                    warning("Occurrence indicator on singleton range variable has no effect", SaxonErrorCode.SXWN9039);
                    type = SequenceType.one(type.getPrimaryType());
                }
                v.setRequiredType(type);
            }

            // process the "in" clause
            readKeyword("in");
            v.setSequence(parseExprSingle());
            declareRangeVariable(v);
            if (previous != null) {
                previous.setAction(v);
            } else {
                first = v;
            }
            previous = v;

        } while (t.currentToken == Token.COMMA);

        // process the "satisfies" expression (called the "action")
        readKeyword("satisfies");
        previous.setAction(parseExprSingle());


        // undeclare all the range variables

        for (int i = 0; i < clauses; i++) {
            undeclareRangeVariable();
        }
        return makeTracer(first, first.getVariableQName());

    }

    /**
     * Parse an IF expression:
     * if '(' expr ')' 'then' expr 'else' expr
     *
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    private Expression parseIfExpression() throws XPathException {
        int ifoffset = t.currentTokenStartOffset;
        nextToken();
        readToken(Token.LPAREN);
        Expression condition = parseExpression();
        readToken(Token.RPAREN);
        int thenoffset = t.currentTokenStartOffset;
        if (t.currentToken == Token.LCURLY) {
            checkLanguageVersion40("curly braces in an `if` expression");
            return parseBracedActions(condition);
        }
        readKeyword("then");
        Expression thenExp = makeTracer(parseExprSingle(), null);
        setLocation(thenExp, thenoffset);
        int elseoffset = t.currentTokenStartOffset;
        readKeyword("else");
        Expression elseExp = makeTracer(parseExprSingle(), null);
        setLocation(elseExp, elseoffset);
        Expression ifExp = Choose.makeConditional(condition, thenExp, elseExp);
        setLocation(ifExp, ifoffset);
        return makeTracer(ifExp, null);
    }

    private Expression parseBracedActions(Expression condition) throws XPathException {
        List<Expression> conditions = new ArrayList<>();
        List<Expression> actions = new ArrayList<>();
        conditions.add(condition);
        nextToken();
        Expression thenExp;
        if (t.currentToken == Token.RCURLY) {
            thenExp = Literal.makeEmptySequence();
        } else {
            thenExp = parseExpression();
            expect(Token.RCURLY);
        }
        actions.add(thenExp);
        nextToken();
        Choose result = new Choose(conditions.toArray(new Expression[]{}), actions.toArray(new Expression[]{}));
        setLocation(result);
        return result;
    }

    /**
     * Analyze a token whose expected value is the name of an atomic type,
     * or in XPath 3.0 a "plain" union type and return the object representing the atomic or union type.
     *
     * @param qname The lexical QName of the atomic type; alternatively, a Clark name
     * @return The atomic type
     * @throws XPathException if the QName is invalid or if no atomic type of that
     *                        name exists as a built-in type or a type in an imported schema
     */
    /*@NotNull*/
    private ItemType getPlainType(String qname) throws XPathException {
        if (scanOnly) {
            return BuiltInAtomicType.STRING;
        }
        StructuredQName sq;
        try {
            sq = qNameParser.parse(qname, env.getDefaultElementNamespace());
        } catch (XPathException e) {
            grumble(e.getMessage(), e.getErrorCodeQName());
            return null;
        }
        return getPlainType(sq);
    }

    public ItemType getPlainType(StructuredQName sq) throws XPathException {
        Configuration config = env.getConfiguration();
        NamespaceUri uri = sq.getNamespaceUri();
        if (uri.isEmpty()) {
            uri = env.getDefaultElementNamespace();
        }
        String local = sq.getLocalPart();
        String qname = sq.getDisplayName();

        if (uri.equals(NamespaceUri.SCHEMA)) {
            ItemType t = Type.getBuiltInItemType(uri, local);
            if (t == null) {
                grumble("Unknown atomic type " + qname, "XPST0051");
                assert false;
            }
            if (t instanceof ErrorType) {
                return t;
            }
            if (t instanceof BuiltInAtomicType) {
                checkAllowedType(env, (BuiltInAtomicType) t);
                return t;
            } else if (t.isPlainType()) {
                return t;
            } else {
                grumble("The type " + qname + " is not atomic", "XPST0051");
                assert false;
            }
        } else if (uri.equals(NamespaceUri.FN) && languageVersion >= 40) {
            ItemType recordType = config.getBuiltInRecordType(local);
            if (recordType == null) {
                grumble("Unknown built-in record type fn:" + local, "XPST0051");
                assert false;
            }
            return recordType;
        } else if (uri.equals(NamespaceUri.JAVA_TYPE)) {
            Class<?> theClass;
            try {
                String className = JavaExternalObjectType.localNameToClassName(local);
                theClass = config.getClass(className, false);
            } catch (XPathException err) {
                grumble("Unknown Java class " + local, "XPST0051");
                return AnyItemType.INSTANCE;
            }
            synchronized(config) {
                return JavaExternalObjectType.of(theClass);
            }
        } else if (uri.equals(NamespaceUri.DOT_NET_TYPE)) {
            return Version.platform.getExternalObjectType(config, uri, local);
        } else {
            if (languageVersion >= 40) {
                ItemType it = env.resolveTypeAlias(sq);
                if (it != null) {
                    return it;
                }
            }
            SchemaType st = env.getImportedSchema().getSchemaType(sq);
            if (st == null) {
                grumble("Unknown simple type " + qname, "XPST0051");
            } else if (st.isAtomicType()) {
                return (AtomicType) st;
            } else if (st instanceof ItemType && ((ItemType) st).isPlainType() && languageVersion >= 30) {
                return (ItemType) st;
            } else if (st.isComplexType()) {
                grumble("Type (" + qname + ") is a complex type", "XPST0051");
                return BuiltInAtomicType.ANY_ATOMIC;
            } else if (((SimpleType) st).isListType()) {
                grumble("Type (" + qname + ") is a list type", "XPST0051");
                return BuiltInAtomicType.ANY_ATOMIC;
            } else if (languageVersion >= 30) {
                grumble("Type (" + qname + ") is a union type that cannot be used as an item type", "XPST0051");
                return BuiltInAtomicType.ANY_ATOMIC;
            } else {
                grumble("The union type (" + qname + ") cannot be used as an item type unless XPath 3.0 is enabled", "XPST0051");
                return BuiltInAtomicType.ANY_ATOMIC;
            }
        }
        grumble("Unknown atomic type " + qname, "XPST0051");
        return BuiltInAtomicType.ANY_ATOMIC;
    }

    private void checkAllowedType(StaticContext env, BuiltInAtomicType type) throws XPathException {
        String s = whyDisallowedType(env.getPackageData(), type);
        if (s != null) {
            grumble(s, "XPST0080");
        }
    }

    /**
     * Determine whether a given built-in type is disallowed in a given environment, and in the
     * case where it is disallowed, return a string explaining why
     *
     * @param pack the containing package
     * @param type the built-in type to be tested
     * @return null if the type is OK to be used; or a string explaining why not.
     */

    public static String whyDisallowedType(PackageData pack, BuiltInAtomicType type) {
        if (!type.isAllowedInXSD10() && pack.getConfiguration().getXsdVersion() == Configuration.XSD10) {
            return "The built-in atomic type " + type.getDisplayName() + " is not recognized unless XSD 1.1 is enabled";
        }
        return null;
    }


    /**
     * Analyze a token whose expected value is the name of a simple type: any type name
     * allowed as the operand of "cast" or "castable".
     *
     * @param qname The lexical QName of the atomic type; alternatively, a Clark name
     * @return The atomic type
     * @throws XPathException if the QName is invalid or if no atomic type of that
     *                        name exists as a built-in type or a type in an imported schema
     */
    /*@NotNull*/
    private CastingTarget getSimpleType(/*@NotNull*/ String qname) throws XPathException {
        if (scanOnly) {
            return BuiltInAtomicType.STRING;
        }
        StructuredQName sq = null;
        try {
            sq = qNameParser.parse(qname, env.getDefaultElementNamespace());
        } catch (XPathException e) {
            grumble(e.getMessage(), e.getErrorCodeQName());
            assert false;
        }
        NamespaceUri uri = sq.getNamespaceUri();
        String local = sq.getLocalPart();

        boolean builtInNamespace = uri.equals(NamespaceUri.SCHEMA);
        if (builtInNamespace) {
            SimpleType target = Type.getBuiltInSimpleType(uri, local);
            if (target == null) {
                grumble("Unknown simple type " + qname, languageVersion >= 30 ? "XQST0052" : "XPST0051");
            } else if (!(target instanceof CastingTarget)) {
                grumble("Unsuitable type for cast: " + target.getDescription(), "XPST0080");
            }
            assert target instanceof CastingTarget;
            CastingTarget t = (CastingTarget) target;
            if (t instanceof BuiltInAtomicType) {
                checkAllowedType(env, (BuiltInAtomicType) t);
            }
            return t;
        } else if (uri.equals(NamespaceUri.DOT_NET_TYPE)) {
            return (AtomicType) Version.platform.getExternalObjectType(env.getConfiguration(), uri, local);

        } else {

            SchemaType st = env.getImportedSchema().getSchemaType(new StructuredQName("", uri, local));
            if (st == null) {
                if (languageVersion >= 30) {
                    grumble("Unknown simple type " + qname, "XQST0052");
                } else {
                    grumble("Unknown simple type " + qname, "XPST0051");
                }
                return BuiltInAtomicType.ANY_ATOMIC;
            }
            if (languageVersion >= 30) {
                // XPath 3.0
                return (CastingTarget) st;

            } else {
                // XPath 2.0
                if (st.isAtomicType()) {
                    return (AtomicType) st;
                } else if (st.isComplexType()) {
                    grumble("Cannot cast to a complex type (" + qname + ")", "XPST0051");
                    return BuiltInAtomicType.ANY_ATOMIC;
                } else if (((SimpleType) st).isListType()) {
                    grumble("Casting to a list type (" + qname + ") requires XPath 3.0", "XPST0051");
                    return BuiltInAtomicType.ANY_ATOMIC;
                } else {
                    grumble("casting to a union type (" + qname + ") requires XPath 3.0", "XPST0051");
                    return BuiltInAtomicType.ANY_ATOMIC;
                }
            }
        }
    }

    /**
     * Parse the sequence type production.
     * The QName must be the name of a built-in schema-defined data type.
     *
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    public SequenceType parseSequenceType() throws XPathException {
        if (isKeyword("empty-sequence")) {
            nextToken();
            readToken(Token.LPAREN);
            readToken(Token.RPAREN);
            return SequenceType.EMPTY_SEQUENCE;
        }
        ItemType primaryType = parseItemType();
        int occurrenceFlag = parseOccurrenceIndicator();
        return SequenceType.makeSequenceType(primaryType, occurrenceFlag);
    }

    public int parseOccurrenceIndicator() throws XPathException {
        int occurrenceFlag;
        Token tok = t.currentToken;
        if (tok == Token.STAR) {
            occurrenceFlag = StaticProperty.ALLOWS_ZERO_OR_MORE;
            nextToken();
        } else if (tok == Token.PLUS) {
            occurrenceFlag = StaticProperty.ALLOWS_ONE_OR_MORE;
            nextToken();
        } else if (tok == Token.QMARK) {
            occurrenceFlag = StaticProperty.ALLOWS_ZERO_OR_ONE;
            nextToken();
        } else {
            occurrenceFlag = StaticProperty.EXACTLY_ONE;
        }
        return occurrenceFlag;
    }

    /**
     * Parse an ItemType within a SequenceType. On entry, the current token
     * is the first token of the item type. On exit, it is the first token
     * after the item type.
     *
     * @return the ItemType after parsing
     * @throws XPathException if a static error is found
     */

    public ItemType parseItemType() throws XPathException {
        ItemType primaryType;
        if (t.currentToken == Token.LPAREN) {
            primaryType = parseParenthesizedItemType();
        } else if (t.currentToken instanceof Token.NameToken) {
            String typeName = t.currentName();
            if (t.peekAhead() == Token.LPAREN) {
                return parseKeywordItemType();
            } else {
                primaryType = getPlainType(typeName);
                nextToken();
            }
        } else if (t.currentToken == Token.PERCENT) {
            AnnotationList annotations = parseAnnotationsList();
            if ((isKeyword("function") || isKeyword("fn")) && t.peekAhead() == Token.LPAREN) {
                primaryType = parseFunctionItemType(annotations);
            } else {
                grumble("Expected 'function(...)' to follow annotation assertions, found " + t.currentToken);
                return null;
            }
        } else {
            grumble("Expected type name in ItemType, found " + t.currentToken);
            return BuiltInAtomicType.ANY_ATOMIC;
        }
        return primaryType;
    }

    /**
     * Parse an item type designated in the form NAME(ARGS) where name is the
     * current token value. On entry we are positioned on a keyword (such as "item")
     * and we know it is followed by a left paren. On exit we are positioned on the
     * next token after the closing parenthesis.
     * @return the item type
     * @throws XPathException if invalid
     */
    private ItemType parseKeywordItemType() throws XPathException {
        String kindName = expectName();
        switch (kindName) {
            case "item":
                nextToken();
                readToken(Token.LPAREN);
                readToken(Token.RPAREN);
                return AnyItemType.INSTANCE;
            case "gnode":
                checkLanguageVersion40("gnode() item type");
                nextToken();
                readToken(Token.LPAREN);
                readToken(Token.RPAREN);
                return AnyGNodeType.getInstance();
            case "node":
            case "element":
            case "attribute":
            case "text":
            case "comment":
            case "processing-instruction":
            case "document-node":
            case "namespace-node":
            case "schema-element":
            case "schema-attribute":
                return parseKindTest(true);
            case "jnode":
                checkLanguageVersion40("jnode() item type");
                return parseJNodeType();
            case "map":
                return parseMapItemType();
            case "array":
                return parseArrayItemType();
            case "fn":
                checkLanguageVersion40();
                return parseFunctionItemType(AnnotationList.EMPTY);
            case "function":
                checkLanguageVersion30();
                return parseFunctionItemType(AnnotationList.EMPTY);
            case "record":
                return parseRecordType(this);
            case "enum":
                return parseEnumType();
            default:
                grumble("Unknown item kind " + kindName);
                return null;

        }
    }

    /**
     * Parse a record type XPath 4.0 extension.
     * On entry we are positioned on the keyword ("record")
     * and we know it is followed by a left paren.
     * Syntax: "record" "(" (name (":" sequenceType)?) ("," (name (":" sequenceType)?))* ")"
     */

    private ItemType parseRecordType(XPathParser p) throws XPathException {
        // The initial "record" has been read
        checkLanguageVersion40("a record type");
        Tokenizer t = p.getTokenizer();
        nextToken();
        p.readToken(Token.LPAREN);
        List<String> fieldNames = new ArrayList<>(6);
        List<String> optionalFieldNames = new ArrayList<>(6);
        List<SequenceType> fieldTypes = new ArrayList<>(6);
        boolean extensible = false;
        RecordType recordTest = new RecordType();
        if (t.currentToken != Token.RPAREN) {
            while (true) {
                String name;
                if (t.currentToken == Token.STAR) {
                    p.grumble("Extensible record types have been dropped from the 4.0 specification");
//                    extensible = true;
//                    p.nextToken();
//                    p.expect(Token.RPAREN);
                    break;
                }
                if (t.currentToken instanceof Token.NameToken) {
                    name = t.currentName();
                    if (!NameChecker.isValidNCName(name)) {
                        p.grumble(Err.wrap(name) + " is not a valid NCName");
                    }
                } else if (t.currentToken instanceof Token.StringLiteral) {
                    name = expectStringLiteral();
                } else {
                    p.grumble("Name of field in tuple must be either an NCName or a quoted string literal");
                    name = "dummy";
                }
                if (fieldNames.contains(name)) {
                    p.grumble("Duplicate field name (" + name + ")");
                    name = "dummy";
                }
                fieldNames.add(name);
                p.nextToken();
                if (t.currentToken == Token.QMARK) {
                    optionalFieldNames.add(name);
                    p.nextToken();
                }
                SequenceType arg = SequenceType.ANY_SEQUENCE;
                if (tryKeyword("as")) {
                    arg = p.parseSequenceType();
                }
                fieldTypes.add(arg);
                if (t.currentToken == Token.RPAREN) {
                    break;
                } else if (t.currentToken == Token.COMMA) {
                    p.nextToken();
                } else {
                    p.grumble("Expected ',' or ')' after field in RecordType, found '" +
                                      t.currentToken + '\'');
                }
            }
        }
        p.nextToken();
        recordTest.setDetails(fieldNames, fieldTypes, optionalFieldNames, extensible);
        return recordTest;
    }

//    /**
//     * Parse a type pattern (XSLT 4.0 extension).
//     * Syntax: "type" "(" itemType ("|" itemType)* ")"
//     *
//     * @return the item type
//     * @throws XPathException if a syntax error is found
//     */
//
//    public ItemType parseTypeQualifier() throws XPathException {
//        checkLanguageVersion40("the syntax type(T)");
//        nextToken();
//        readToken(Token.LPAREN);
//        List<ItemType> memberTypes = new ArrayList<>(6);
//        while (true) {
//            memberTypes.add(parseItemType());
//            if (t.currentToken == Token.VBAR) {
//                nextToken();
//            } else {
//                break;
//            }
//        }
//        readToken(Token.RPAREN);
//        if (memberTypes.size() == 1) {
//            return memberTypes.get(0);
//        }
//        return ChoiceItemType.makeChoiceItemType(memberTypes);
//    }

    /**
     * Parse an enum type (XPath 4.0 proposal).
     * Syntax: "enum" "(" StringLiteral ("," StringLiteral)* ")"
     * @return the item type
     * @throws XPathException if a syntax error is found
     */

    public EnumerationUnionType parseEnumType() throws XPathException {
        // The initial "enum" has been read
        checkLanguageVersion40("enumeration types");
        nextToken();
        readToken(Token.LPAREN);
        List<SingletonEnumType> singletonEnums = new ArrayList<>();

        while (true) {
            String s = expectStringLiteral();
            UnicodeString val = StringTool.fromCharSequence(s);
            singletonEnums.add(new SingletonEnumType(val));
            nextToken();
            if (t.currentToken == Token.RPAREN) {
                break;
            } else if (t.currentToken == Token.COMMA) {
                nextToken();
            } else {
                grumble("Expected ',' or ')' after string literal in enum type, found '" +
                                t.currentToken + '\'');
            }
        }
        nextToken();

        return new EnumerationUnionType(singletonEnums);

    }

    /**
     * Parse the item type used for function items (for higher order functions)
     * function|fn '(' '*' ') |
     * function|fn '(' (SeqType (',' SeqType)*)? ')' 'as' SeqType
     * The "function(" has already been read
     *
     * @param annotations the list of annotation assertions for this function item type
     * @return the ItemType after parsing
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    protected ItemType parseFunctionItemType(AnnotationList annotations) throws XPathException {
        nextToken();
        readToken(Token.LPAREN);
        List<SequenceType> argTypes = new ArrayList<>(3);
        SequenceType resultType;

        if (t.currentToken == Token.STAR) {
            nextToken();
            readToken(Token.RPAREN);
            if (annotations.isEmpty()) {
                return AnyFunctionType.INSTANCE;
            } else {
                return new AnyFunctionTypeWithAssertions(annotations, getStaticContext().getConfiguration());
            }
        } else {
            Set<StructuredQName> paramNames = null;
            while (t.currentToken != Token.RPAREN) {
                if (t.currentToken == Token.DOLLAR && languageVersion >= 40) {
                    // Optional parameter names are allowed (and ignored) in 4.0
                    StructuredQName varName = parseEQName();
                    if (paramNames == null) {
                        paramNames = new HashSet<>();
                    }
                    if (!paramNames.add(varName)) {
                        grumble("Duplicate parameter name " + varName.getEQName(), "XQST0039");
                    }
                    readKeyword("as");
                }
                SequenceType arg = parseSequenceType();
                argTypes.add(arg);
                if (t.currentToken == Token.RPAREN) {
                    break;
                } else if (t.currentToken == Token.COMMA) {
                    nextToken();
                } else {
                    grumble("Expected ',' or ')' after function argument type, found '" +
                                    t.currentToken + '\'');
                }
            }
            nextToken();
            if (tryKeyword("as")) {
                resultType = parseSequenceType();
                SequenceType[] argArray = new SequenceType[argTypes.size()];
                argArray = argTypes.toArray(argArray);
                return new SpecificFunctionType(argArray, resultType, annotations);
            } else if (!argTypes.isEmpty()) {
                grumble("Result type must be given if an argument type is given: expected 'as (type)'");
                return null;
            } else {
                grumble("function() is no longer allowed for a general function type: must be function(*)");
                return null;
            }
        }
    }


    /**
     * Parse the item type used for maps
     * Syntax:
     * map '(' '*' ') |
     * map '(' ItemType ',' SeqType ')' 'as' SeqType
     * The "map" has already been read, and we know it is followed by "("
     * @return the item type of the map
     * @throws XPathException if a parsing error occurs or if the map syntax
     *                        is not available
     */

    /*@NotNull*/
    protected ItemType parseMapItemType() throws XPathException {
        checkMapExtensions();
        Tokenizer t = getTokenizer();
        nextToken();
        readToken(Token.LPAREN);
        if (t.currentToken == Token.STAR) {
            // Allow both to be safe
            nextToken();
            readToken(Token.RPAREN);
            return MapType.ANY_MAP_TYPE;
        } else {
            ItemType keyType = parseItemType();
            readToken(Token.COMMA);
            SequenceType valueType = parseSequenceType();
            readToken(Token.RPAREN);
            if (!(keyType instanceof PlainType)) {
                grumble("Key type of a map must be an atomic or pure union type: found " + keyType);
                return null;
            }
            return new MapType((PlainType) keyType, valueType);
        }
    }

    /**
     * Get the item type used for array items (XPath 3.1)
     * Syntax:
     *    array '(' '*' ') |
     *    array '(' SeqType ')'
     * The current token is the keyword "array"
     * @return the item type of the array
     * @throws XPathException if a parsing error occurs or if the array syntax
     *                        is not available
     */

    /*@NotNull*/
    protected ItemType parseArrayItemType() throws XPathException {
        checkLanguageVersion31();
        Tokenizer t = getTokenizer();
        nextToken();
        readToken(Token.LPAREN);
        if (t.currentToken == Token.STAR) {
            nextToken();
            readToken(Token.RPAREN);
            return ArrayItemType.ANY_ARRAY_TYPE;
        } else {
            SequenceType memberType = parseSequenceType();
            readToken(Token.RPAREN);
            return new ArrayItemType(memberType);
        }
    }

    /**
     * Parse a parenthesized item type (allowed from 3.0). In 4.0 this
     * also allows a choice of item types separated by "|"
     *
     * @return the item type
     * @throws XPathException in the event of a syntax error (or if 3.0 is not enabled)
     */

    /*@NotNull*/
    private ItemType parseParenthesizedItemType() throws XPathException {
        if (languageVersion < 30) {
            grumble("Parenthesized item types require 3.0 to be enabled");
        }
        nextToken();
        List<ItemType> alternatives = new ArrayList<>();
        alternatives.add(parseItemType());
        while (languageVersion >= 40 && t.currentToken == Token.VBAR) {
            nextToken();
            alternatives.add(parseItemType());
        }
        readToken(Token.RPAREN);
        if (alternatives.size() == 1) {
            return alternatives.get(0);
        } else {
            return ChoiceItemType.makeChoiceItemType(alternatives);
        }
    }


    /**
     * Parse a UnaryExpr:<br>
     * ('+'|'-')* ValueExpr
     * parsed as ('+'|'-')? UnaryExpr
     *
     * @return the resulting subexpression. On return, the current token must be the
     * token that follows the expression.
     * @throws XPathException if any error is encountered
     */

    /*@NotNull*/
    private Expression parseUnaryExpression() throws XPathException {
        Expression exp = null;
        if (t.currentToken == Token.MINUS) {
            nextToken();
            Expression operand = parseUnaryExpression();
            exp = makeUnaryExpression(OperatorSymbol.NEGATE, operand);
        } else if (t.currentToken == Token.PLUS) {
            nextToken();
            // Unary plus: can't ignore it completely, it might be a type error, or it might
            // force conversion to a number which would affect operations such as "=".
            Expression operand = parseUnaryExpression();
            exp = makeUnaryExpression(OperatorSymbol.PLUS, operand);
        } else if (t.currentToken instanceof Token.Pragma) {
            exp = parseExtensionExpression();
        } else if (isKeyword("validate")) {
            Token second = t.peekAhead();
            if (second == Token.LCURLY
                    || isKeyword(second, "strict")
                    || isKeyword(second, "lax")
                    || isKeyword(second, "type")) {
                exp = parseValidateExpression();
            }
        }
        if (exp == null) {
            exp = parseSimpleMappingExpression();
        }
        setLocation(exp);
        return exp;
    }

    private Expression makeUnaryExpression(OperatorSymbol operator, Expression operand) {
        if (Literal.isAtomic(operand)) {
            // very early evaluation of expressions like "-1", so they are treated as numeric literals
            AtomicValue val = (AtomicValue) ((Literal) operand).getGroundedValue();
            if (val instanceof NumericValue) {
                if (env.isInBackwardsCompatibleMode()) {
                    val = new DoubleValue(((NumericValue) val).getDoubleValue());
                }
                AtomicValue value = operator == OperatorSymbol.NEGATE ? ((NumericValue) val).negate() : (NumericValue) val;
                return Literal.makeLiteral(value);
            }
        }
        return env.getConfiguration().getTypeChecker(env.isInBackwardsCompatibleMode()).makeArithmeticExpression(
                Literal.makeLiteral(Int64Value.ZERO), operator, operand);
    }

    /**
     * Test whether the current token is one that can start a RelativePathExpression,
     * satisfying the special rule for disambiguation after a lone leading slash.
     *
     * @return true if the current token is to be interpreted as the start of a relative
     * path expression.
     */

    protected boolean atStartOfRelativePath() {
        Token tok = t.currentToken;
        return tok instanceof Token.NameToken
                || tok == Token.AT
                || tok == Token.STAR
                || tok == Token.DOT
                || tok == Token.DOT_DOT
                || tok == Token.LPAREN
                || tok == Token.DOLLAR
                || tok == Token.QMARK
                || tok == Token.LSQB
                || tok == Token.LCURLY
                || tok instanceof Token.Pragma
                || tok instanceof Token.StringLiteral
                || tok instanceof Token.NumericLiteral
                || tok instanceof Token.Wildcard
                || tok instanceof Token.StringTemplate
                ;
    }

    /**
     * Parse a PathExpression. This includes "true" path expressions such as A/B/C, and also
     * constructs that may start a path expression such as a variable reference $name or a
     * parenthesed expression (A|B). Numeric and string literals also come under this heading.
     *
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    /*@NotNull*/
    protected Expression parsePathExpression() throws XPathException {
        int offset = t.currentTokenStartOffset;
        if (t.currentToken == Token.SLASH) {
            nextToken();
            final RootExpression start = new RootExpression();
            setLocation(start);
            if (atStartOfRelativePath()) {
                final Expression path = parseRemainingPath(start);
                setLocation(path, offset);
                return path;
            } else {
                return start;
            }
        } else if (t.currentToken == Token.SLASH_SLASH) {
            nextToken();
            final RootExpression start2 = new RootExpression();
            setLocation(start2, offset);
            final AxisExpression axisExp = new AxisExpression(AxisInfo.DESCENDANT_OR_SELF, null);
            setLocation(axisExp, offset);
            final Expression slashExp = ExpressionTool.makePathExpression(start2, axisExp);
            setLocation(slashExp, offset);
            final Expression exp = parseRemainingPath(slashExp);
            setLocation(exp, offset);
            return exp;
        } else if (t.currentToken instanceof Token.NameToken && t.peekAhead() != Token.LPAREN && t.peekAhead() != Token.HASH) {
            String name = t.currentName();
            if (name.equals("true") || name.equals("false")) {
                warning("The expression is looking for a child element named '" + name +
                                "' - perhaps " + name + "() was intended? To avoid this warning, use child::" +
                                name + " or ./" + name + ".", SaxonErrorCode.SXWN9040);
            } else if (t.currentToken.getOperatorSymbol() != OperatorSymbol.NOT_AN_OPERATOR
                    && language != ParsedLanguage.XSLT_PATTERN
                    && (offset > 0 || t.peekAhead() != Token.EOF)) {
                warning("The keyword '" + name + "' in this context means 'child::" + name +
                                "'. If this was intended, use 'child::" + name + "' or './" + name + "' to avoid this warning.", SaxonErrorCode.SXWN9040);
            }
        }
        return parseRelativePath();

    }

    /**
     * Parse an XPath 3.0 simple mapping expression ("!" operator)
     *
     * @return the parsed expression
     * @throws XPathException in the event of a syntax error
     */

    protected Expression parseSimpleMappingExpression() throws XPathException {
        int offset = t.currentTokenStartOffset;
        Expression exp = parsePathExpression();
        while (t.currentToken == Token.BANG) {
            if (languageVersion < 30) {
                grumble("XPath '!' operator requires XPath 3.0 to be enabled");
            }
            nextToken();
            Expression next = parsePathExpression();
            exp = new ForEach(exp, next);
            setLocation(exp, offset);
        }
        return exp;
    }

    /**
     * Parse a relative path (a sequence of steps). Called when the current token immediately
     * follows a separator (/ or //), or an implicit separator (XYZ is equivalent to ./XYZ)
     *
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    /*@NotNull*/
    protected Expression parseRelativePath() throws XPathException {

        int offset = t.currentTokenStartOffset;
        Expression exp = parseStepExpression(language == ParsedLanguage.XSLT_PATTERN);
        while (t.currentToken == Token.SLASH ||
                t.currentToken == Token.SLASH_SLASH) {
            Token op = t.currentToken;
            nextToken();
            Expression next = parseStepExpression(false);
            if (op == Token.SLASH) {

                //return new RawSlashExpression(start, step);
                exp = new HomogeneityChecker(new SlashExpression(exp, next));
            } else /* (op == Token.SLASH_SLASH)*/ {
                // add implicit descendant-or-self::node() step
                AxisExpression ae = new AxisExpression(AxisInfo.DESCENDANT_OR_SELF, null);
                setLocation(ae, offset);
                Expression one = ExpressionTool.makePathExpression(exp, ae);
                setLocation(one, offset);
                exp = ExpressionTool.makePathExpression(one, next);
                exp = new HomogeneityChecker(exp);
            }
            setLocation(exp, offset);
        }
        return exp;
    }

    /**
     * Parse the remaining steps of an absolute path expression (one starting in "/" or "//"). Note that the
     * token immediately after the "/" or "//" has already been read, and in the case of "/", it has been confirmed
     * that we have a path expression starting with "/" rather than a standalone "/" expression.
     *
     * @param start the initial implicit expression: root() in the case of "/", root()/descendant-or-self::node in
     *              the case of "//"
     * @return the completed path expression
     * @throws XPathException if a static error is found
     */
    /*@NotNull*/
    protected Expression parseRemainingPath(Expression start) throws XPathException {
        int offset = t.currentTokenStartOffset;
        Expression exp = start;
        Token op = Token.SLASH;
        while (true) {
            Expression next = parseStepExpression(false);
            if (op == Token.SLASH) {
                exp = new HomogeneityChecker(new SlashExpression(exp, next));
            } else if (op == Token.SLASH_SLASH) {
                // add implicit descendant-or-self::node() step
                AxisExpression descOrSelf = new AxisExpression(AxisInfo.DESCENDANT_OR_SELF, null);
                setLocation(descOrSelf);
                Expression step = ExpressionTool.makePathExpression(descOrSelf, next);
                setLocation(step);
                exp = ExpressionTool.makePathExpression(exp, step);
                exp = new HomogeneityChecker(exp);
            } else /*if (op == Token.BANG)*/ {
                if (languageVersion < 30) {
                    grumble("XPath '!' operator requires XPath 3.0 to be enabled");
                }
                exp = new ForEach(exp, next);
            }
            setLocation(exp, offset);
            op = t.currentToken;
            if (op != Token.SLASH && op != Token.SLASH_SLASH && op != Token.BANG) {
                break;
            }
            nextToken();
        }
        return exp;
    }


    /**
     * Parse a step (including an optional sequence of predicates)
     *
     * @param firstInPattern true only if we are parsing the first step in a
     *                       RelativePathPattern in the XSLT Pattern syntax
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    /*@NotNull*/
    protected Expression parseStepExpression(boolean firstInPattern) throws XPathException {
        Expression step = parseBasicStep(firstInPattern);

        // When the filter is applied to an Axis step, the nodes are considered in
        // axis order. In all other cases they are considered in document order
        boolean isAxisStep = step instanceof GeneralizedAxisExpression;
        boolean reverse = isAxisStep && !AxisInfo.isForwards[((GeneralizedAxisExpression) step).getAxis()];
        while (true) {
            if (t.currentToken == Token.LSQB) {
                step = parsePredicate(step);
            } else if (!isAxisStep) {
                if (t.currentToken == Token.LPAREN) {
                    // dynamic function call (XQuery 3.0/XPath 3.0 syntax)
                    step = parseDynamicFunctionCall(step, null);
                    setLocation(step);
                } else if (t.currentToken == Token.QMARK) {
                    step = parseLookup(step);
                    setLocation(step);
                } else if (t.currentToken == Token.METHOD_CALL) {
                    step = parseMethodCall(step);
                    setLocation(step);
                } else if (t.currentToken == Token.QMARK_LSQB) {
                    step = parseFilterExprAM(step);
                    setLocation(step);
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        if (reverse) {
            // An AxisExpression such as preceding-sibling::x delivers nodes in axis
            // order, so that positional predicate like preceding-sibling::x[1] work
            // correctly. To satisfy the XPath semantics we turn preceding-sibling::x
            // into reverse(preceding-sibling::x), and preceding-sibling::x[3] into
            // reverse(preceding-sibling::x[3]). The call on reverse() will be eliminated
            // later in the case where the predicate selects a singleton.
            RetainedStaticContext rsc = env.makeRetainedStaticContext();
            step = SystemFunction.makeCall("reverse", rsc, step);
        }
        return step;
    }

    protected Expression parsePredicate(Expression step) throws XPathException {
        nextToken();
        Expression predicate = parsePredicate();
        if (Literal.isConstantZero(predicate)) {
            warning("Positions are numbered from one; the predicate [0] selects nothing", SaxonErrorCode.SXWN9046);
        }
        readToken(Token.RSQB);
        step = new FilterExpression(step, predicate);
        setLocation(step);
        return step;
    }


    protected Expression parseFilterExprAM(Expression step) throws XPathException {
        grumble("The `?[...]` syntax to filter maps and arrays has been dropped from the draft 4.0 specifications");
        return step;
//        checkLanguageVersion40("the `?[...]` syntax to filter maps and arrays");
//        nextToken();
//        Expression predicate = parsePredicate();
//        readToken(Token.RSQB);
//        step = new FilterExpressionAM(step, predicate);
//        setLocation(step);
//        return step;
    }


    protected Expression parseMethodCall(Expression step) throws XPathException {
        checkLanguageVersion40("method calls ( ?> operator )");
        nextToken();
        String methodName = expectName();
        if (!NameChecker.isValidNCName(methodName)) {
            grumble("Method name '" + methodName + "' is not a valid NCName");
        }
        nextToken();
        expect(Token.LPAREN);
        nextToken();

        List<Expression> args = new ArrayList<>();
        args.add(new PlaceHolder(0));
        if (t.currentToken != Token.RPAREN) {
            while (true) {
                Expression arg = parseFunctionArgument();
                args.add(arg);
                if (t.currentToken == Token.COMMA) {
                    nextToken();
                } else {
                    break;
                }
            }
            expect(Token.RPAREN);
        }
        nextToken();

        // Given STEP ?> NAME ( ARG1, ARG2, ...), construct the expression
        // for $map in STEP as map(*) return ($map ? NAME) treat as function(*) ( $map, ARG1, ARG2, ...)

        ForExpression forex = new ForExpression();
        forex.setSequence(step);
        forex.setVariableQName(new StructuredQName("vv", NamespaceUri.SAXON_GENERATED_VARIABLE, "m" + forex.hashCode()));
        forex.setRequiredType(SequenceType.SINGLE_MAP);
        VariableReference var1 = new LocalVariableReference(forex);
        Expression function = new LookupExpression(var1, new StringLiteral(methodName));
        Expression function1 = makeSequenceTypeExpression(function, OperatorSymbol.TREAT_AS, SequenceType.SINGLE_FUNCTION);
        VariableReference var2 = new LocalVariableReference(forex);
        args.set(0, var2);
        Expression call = new DynamicFunctionCall(function1, args);
        forex.setAction(call);
        return forex;
    }

    /**
     * Parse an XPath 3.1 arrow operator ("=&gt;")
     * @param lhs the expression on the left of the arrow operator
     * @return the expression that results from the parsing
     * @throws XPathException if the syntax is wrong
     */

    /*@NotNull*/
    protected Expression parseArrowPostfix(Expression lhs) throws XPathException {
        checkLanguageVersion31();
        readToken(Token.FAT_ARROW);
        return parseArrowRHS(lhs, Token.FAT_ARROW);

    }

    private Expression parseArrowRHS(Expression lhs, Token arrow) throws XPathException {
        Token token = t.currentToken;
        if (token instanceof Token.NameToken &&
                t.peekAhead() == Token.LPAREN &&
                !isReservedFunctionName(t.currentName(), languageVersion)) {
            return parseFunctionCall(t.currentName(), lhs);
        }
        if (token == Token.DOLLAR) {
            int offset = t.currentTokenStartOffset;
            StructuredQName varName = parseEQName();
            Expression var = resolveVariableReference(offset, varName);
            expect(Token.LPAREN);
            return parseDynamicFunctionCall(var, lhs);
        } else if (token == Token.LPAREN) {
            Expression var = parseParenthesizedExpression();
            expect(Token.LPAREN);
            return parseDynamicFunctionCall(var, lhs);
        } else if (languageVersion >= 40) {
            // At this point, the things that are allowed in a DynamicFunctionCall, but not in
            // a RestrictedDynamicCall or FunctionCall, are: Literal, ContextValueRef,
            // OrderedExpr, UnorderedExpr, NodeConstructor, StringTemplate, StringConstructor,
            // and UnaryLookup. Most of these will fail with a type error, which we accept, although
            // it should really be a syntax error. The only ones we actually test for are those that
            // could deliver a function item
            if (token == Token.DOT || token == Token.QMARK ||
                    (isKeyword("ordered") || isKeyword("unordered")) && t.peekAhead() == Token.LCURLY) {
                grumble(token.toString() + " not allowed on RHS of " + arrow.toString());
            }
            Expression fn = parseBasicStep(false);
            expect(Token.LPAREN);
            return parseDynamicFunctionCall(fn, lhs);
        }
        grumble("Unexpected " + token + " after " + arrow.toString());
        return null;
    }

    /**
     * Parse an XPath 4.0 mapping arrow operator ("=!&gt;")
     *
     * @param lhs the expression on the left of the arrow operator
     * @return the expression that results from the parsing
     * @throws XPathException if the syntax is wrong
     */

    /*@NotNull*/
    protected Expression parseMappingArrowPostfix(Expression lhs) throws XPathException {
        checkLanguageVersion40("the mapping arrow =!>");
        nextToken();

        ForExpression forExpr = new ForExpression();
        forExpr.setSequence(lhs);
        StructuredQName varName = new StructuredQName("vv", NamespaceUri.SAXON_GENERATED_VARIABLE, "" + lhs.hashCode());
        forExpr.setVariableQName(varName);
        VariableReference varRef = new LocalVariableReference(forExpr);

        Expression rhs = parseArrowRHS(varRef, Token.MAPPING_ARROW);
        forExpr.setAction(rhs);
        return forExpr;
    }


    /**
     * Parse the expression within a predicate. A separate method so it can be overridden
     *
     * @return the expression within the predicate
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    protected Expression parsePredicate() throws XPathException {
        return parseExpression();
    }

    protected boolean isReservedInQuery(NamespaceUri uri) {
        return NamespaceUri.isReservedInQuery31(uri);
    }

    /**
     * Parse a basic step expression (without the predicates).
     * Specifically, we are looking for either a PrimaryExpr, that is:
     * Literal
     * | VarRef
     * | ParenthesizedExpr
     * | ContextValueRef
     * | FunctionCall
     * | OrderedExpr
     * | UnorderedExpr
     * | NodeConstructor
     * | FunctionItemExpr
     * | MapConstructor
     * | ArrayConstructor
     * | StringTemplate
     * | StringConstructor
     * | UnaryLookup
     * or a full axis step (axis::nodetest) or an abbreviated axis step (.., @nodetest, or
     * SimpleNodeTest, which is a KindTest or NameTest)
     *
     * @param firstInPattern true only if we are parsing the first step in a
     *                       RelativePathPattern in the XSLT Pattern syntax
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    /*@NotNull*/
    protected Expression parseBasicStep(boolean firstInPattern) throws XPathException {
        // Note: from Java 17, this could benefit from the new switch syntax:
        // switch (token) case NameToken n -> { process n }
        // Especially if the simple tokens such as Token.LPAREN were defined as singleton classes.
        Token token = t.currentToken;
        if (token instanceof Token.StringLiteral) {
            return parseStringLiteral(true);
        } else if (token instanceof Token.NumericLiteral) {
            return parseNumericLiteral(true);
        } else if (token instanceof Token.NameToken) {
            if (t.peekAhead() == Token.LPAREN) {
                String name = t.currentName();
                switch (name) {
                    case "namespace-node":
                        if (languageVersion < 30) {
                            return parseFunctionCall(name, null);
                        }
                        testPermittedAxis(AxisInfo.NAMESPACE, "XQST0134");
                        nextToken();
                        readToken(Token.LPAREN);
                        readToken(Token.RPAREN);
                        AxisExpression nsExp = new AxisExpression(AxisInfo.NAMESPACE, NodeKindType.NAMESPACE);
                        setLocation(nsExp);
                        return nsExp;
                    case "node":
                    case "schema-element":
                    case "processing-instruction":
                    case "document-node":
                    case "comment":
                    case "element":
                    case "text": {
                        NodeTest nodeTest = parseNodeTest(Type.ELEMENT);
                        int defaultAxis = (firstInPattern && name.equals("document-node")) ? AxisInfo.SELF : AxisInfo.CHILD;
                        if (firstInPattern && defaultAxis == AxisInfo.CHILD && name.equals("node")) {
                            nodeTest = MultipleNodeKindTest.CHILD_NODE;
                        }
                        AxisExpression ae = new AxisExpression(defaultAxis, nodeTest);
                        setLocation(ae);
                        return ae;
                    }
                    case "jnode": {
                        if (languageVersion < 40) {
                            return parseFunctionCall(name, null);
                        }
                        JNodeType jType = parseJNodeType();
                        int defaultAxis = AxisInfo.CHILD;
                        AxisExpression ae = new AxisExpression(defaultAxis, jType);
                        setLocation(ae);
                        return ae;
                    }
                    case "gnode": {
                        if (languageVersion < 40) {
                            return parseFunctionCall(name, null);
                        }
                        nextToken();
                        readToken(Token.LPAREN);
                        readToken(Token.RPAREN);
                        int defaultAxis = AxisInfo.CHILD;
                        AxisExpression ae = new AxisExpression(defaultAxis, AnyGNodeType.getInstance());
                        setLocation(ae);
                        return ae;
                    }
                    case "attribute":
                    case "schema-attribute": {
                        NodeTest attNodeTest = parseNodeTest(Type.ATTRIBUTE);
                        AxisExpression ae = new AxisExpression(AxisInfo.ATTRIBUTE, attNodeTest);
                        setLocation(ae);
                        return ae;
                    }
                    case "get": {
                        notAllowedInPattern();
                        if (languageVersion < 40) {
                            return parseFunctionCall(name, null);
                        }
                        nextToken();
                        readToken(Token.LPAREN);
                        Expression selector = parseExprSingle();
                        readToken(Token.RPAREN);
                        AxisGetExpression getExpr = new AxisGetExpression(AxisInfo.CHILD, selector);
                        setLocation(getExpr);
                        return getExpr;
                    }
                    case "function":
                        notAllowedInPattern();
                        return parseInlineFunction(AnnotationList.EMPTY);
                    case "fn":
                        if (languageVersion < 40) {
                            return parseFunctionCall(name, null);
                        }
                        notAllowedInPattern();
                        return parseInlineFunction(AnnotationList.EMPTY);
                    default:
                        return parseFunctionCall(name, null);

                }
            } else if (t.peekAhead() == Token.LCURLY) {
                notAllowedInPattern();
                String name = t.currentName();
                switch (name) {
                    case "map":
                        nextToken();
                        expect(Token.LCURLY);
                        return parseMapConstructor();
                    case "array":
                        nextToken();
                        expect(Token.LCURLY);
                        return parseArrayCurlyConstructor();
                    case "function":
                    case "fn":
                        nextToken();
                        return parseFocusFunction(null);
                    case "ordered":
                    case "unordered":
                        // ignore these wrappers
                        nextToken();
                        readToken(Token.LCURLY);
                        if (t.currentToken == Token.RCURLY) {
                            nextToken();
                            return Literal.makeEmptySequence();
                        }
                        Expression wrapped = parseExpression();
                        readToken(Token.RCURLY);
                        return wrapped;
                    case "document":
                    case "element":
                    case "attribute":
                    case "namespace":
                    case "text":
                    case "comment":
                    case "processing-instruction":
                        return parseComputedNodeConstructor();
                    default:
                        grumble("Unrecognized construct " + name + "{...}");
                        return null;

                }

            } else if (t.peekAhead() == Token.HASH) {
                Tokenizer checkPoint = t.checkPoint();
                nextToken();
                Token nextButOne = t.peekAhead();
                t.rollbackTo(checkPoint);
                if (nextButOne instanceof Token.NumericLiteral) {
                    return parseNamedFunctionReference();
                }
                return parseComputedNodeConstructor();

            } else if (t.peekAhead() == Token.COLON_COLON) {
                int axis;
                try {
                    axis = AxisInfo.getAxisNumber(t.currentName());
                } catch (XPathException err) {
                    grumble(err.getMessage());
                    axis = AxisInfo.CHILD; // error recovery
                }
                testPermittedAxis(axis, "XPST0003");
                short principalNodeType = AxisInfo.principalNodeType[axis];
                nextToken();
                readToken(Token.COLON_COLON);
                if (isKeyword("get") && t.peekAhead() == Token.LPAREN) {
                    checkLanguageVersion40("nodeTest get()");
                    nextToken();
                    readToken(Token.LPAREN);
                    Expression selector = parseExpression();
                    readToken(Token.RPAREN);
                    AxisGetExpression axisGetExpr = new AxisGetExpression(axis, selector);
                    setLocation(axisGetExpr);
                    return axisGetExpr;
                }
                NodeTest nodeTest = parseNodeTest(principalNodeType);
                AxisExpression ae = new AxisExpression(axis, nodeTest);
                setLocation(ae);
                return ae;

            } else if (isMaybeNamedConstructor()) {
                String kind = t.currentName();
                String qname = ((Token.NameToken) t.peekAhead()).getValue();
                // See K2-ForExprWithout-15: "for $n in $arg/element return $n". We need to check that
                // the second keyword is followed by a left curly brace. The tokenizer doesn't give us enough
                // lookahead, so we need to rely on backtracking
                switch (kind) {
                    case "element":
                    case "attribute":
                    case "processing-instruction":
                    case "namespace":
                        Tokenizer checkPoint = t.checkPoint();
                        nextToken();
                        Token nextButOne = t.peekAhead();
                        t.rollbackTo(checkPoint);
                        if (nextButOne == Token.LCURLY) {
                            return parseComputedNodeConstructor();
                        }
                        break;
                    case "update":
                        if (qname.equals("map") || qname.equals("array")) {
                            return parseDeepUpdateExpression();
                        }
                        break;
                }
            } else if (t.peekAhead() instanceof Token.StringLiteral) {
                String kind = t.currentName();
                //String qname = ((Token.NameToken) t.peekAhead()).getValue();
                // See K2-ForExprWithout-15: "for $n in $arg/element return $n". We need to check that
                // the second keyword is followed by a left curly brace. The tokenizer doesn't give us enough
                // lookahead, so we need to rely on backtracking
                switch (kind) {
                    case "element":
                    case "attribute":
                    case "processing-instruction":
                    case "namespace":
                        Tokenizer checkPoint = t.checkPoint();
                        nextToken();
                        Token nextButOne = t.peekAhead();
                        t.rollbackTo(checkPoint);
                        if (nextButOne == Token.LCURLY) {
                            return parseComputedNodeConstructor();
                        }
                        break;

                }
            }
            AxisExpression resultExpr = new AxisExpression(AxisInfo.CHILD, parseNodeTest(Type.ELEMENT));
            setLocation(resultExpr);
            return resultExpr;

        } else if (token instanceof Token.Wildcard) {
            QNameTest test = getQNameTestForWildcard((Token.Wildcard) token);
            NodeTest nodeTest = new NamedXNodeType(Type.ELEMENT, test, env.getConfiguration());
            AxisExpression ae = new AxisExpression(AxisInfo.CHILD, nodeTest);
            setLocation(ae);
            nextToken();
            return ae;

        } else if (token == Token.DOT) {
            nextToken();
            Expression cie = new ContextItemExpression();
            setLocation(cie);
            return cie;

        } else if (token == Token.DOLLAR) {
            int offset = t.currentTokenStartOffset;
            StructuredQName variableName = parseEQName();
            return resolveVariableReference(offset, variableName);

        } else if (token == Token.DOT_DOT) {
            nextToken();
            Expression pne = new AxisExpression(AxisInfo.PARENT, null);
            setLocation(pne);
            return pne;

        } else if (token == Token.STAR) {
            nextToken();
            AxisExpression ae = new AxisExpression(AxisInfo.CHILD, new NodeTestStar(Type.ELEMENT));
            setLocation(ae);
            return ae;

        } else if (token == Token.LPAREN) {
            return parseParenthesizedExpression();

        } else if (token == Token.LSQB) {
            return parseArraySquareConstructor();

        } else if (token == Token.QMARK) {
            return parseLookup(new ContextItemExpression());

        } else if (token == Token.AT) {
            nextToken();
            NodeTest nt2 = parseNodeTest(Type.ATTRIBUTE);
            AxisExpression ae2 = new AxisExpression(AxisInfo.ATTRIBUTE, nt2);
            setLocation(ae2);
            return ae2;

        } else if (token == Token.LCURLY) {
            if (languageVersion >= 40) {
                return parseMapConstructor();
            } else {
                grumble("Unexpected `{` after " + previousToken);
            }
            return null;

        } else if (token == Token.PERCENT) {
            AnnotationList annotations = parseAnnotationsList();
            if (!(isKeyword("function") || isKeyword("fn"))) {
                grumble("Expected 'function' to follow the annotation assertion");
            }
            annotations.check(env.getConfiguration(), "IF");
            if (t.peekAhead() == Token.LCURLY) {
                nextToken();
                return parseFocusFunction(annotations);
            } else {
                return parseInlineFunction(annotations);
            }

        } else if (token == Token.HASH && languageVersion >= 40) {
            StructuredQName qn = parseEQName();
            return Literal.makeLiteral(new QNameValue(qn, BuiltInAtomicType.QNAME));

        } else if (token instanceof Token.StringTemplate) {
            checkLanguageVersion40("a string template");
            return parseStringTemplate();

        } else if (token instanceof Token.StringConstructor) {
            return parseStringConstructor();

        } else if (token instanceof Token.DirectCommentConstructor) {
            requireXQuery("A comment constructor");
            Comment instr = new Comment();
            UnicodeString content = StringTool.fromCharSequence(((Token.DirectCommentConstructor) token).getContent());
            try {
                Comment.checkContentXQuery(content);
            } catch (XPathException e) {
                grumble(e.getMessage());
            }
            instr.setSelect(new StringLiteral(content));
            setLocation(instr);
            nextToken();
            return makeTracer(instr, null);

        } else if (token instanceof Token.DirectProcessingInstructionConstructor) {
            requireXQuery("A processing instruction constructor");
            return parseDirectPIConstructor();

        } else if (token instanceof Token.DirectElementConstructor) {
            requireXQuery("An element constructor");
            return parseDirectElementConstructor(false);

        } else {
            grumble("Unexpected token " + currentTokenDisplay() + " before " + t.recentText(t.inputOffset));
            return null;
        }


    }

    protected boolean isMaybeNamedConstructor() {
        return false;
    }

    protected void notAllowedInPattern() throws XPathException {
        // Overridden in pattern parser
    }

    /**
     * Construct a QNameTest corresponding to a Wildcard token (prefix:*, *:local, or Q{uri}*)
     *
     * @param token the wildcard token
     * @return the corresponding NodeTest
     * @throws XPathException for example if the local name is invalid
     */
    private QNameTest getQNameTestForWildcard(Token.Wildcard token) throws XPathException {
        String prefix = token.getPrefix();
        String suffix = token.getSuffix();
        if (prefix.equals("*")) {
            if (!NameChecker.isValidNCName(StringTool.codePoints(suffix))) {
                grumble("Local name `" + suffix + "` contains invalid characters");
            }
            return new LocalQNameTest(suffix);
        } else {
            NamespaceQNameTest test = makeNamespaceQNameTest(prefix);
            return new NamespaceQNameTest(test.getNamespace());
        }
    }

    public Expression parseParenthesizedExpression() throws XPathException {
        nextToken();
        if (t.currentToken == Token.RPAREN) {
            nextToken();
            return makeTracer(Literal.makeEmptySequence(), null);
        }
        Expression seq = parseExpression();
        readToken(Token.RPAREN);
        return seq;
    }

    protected void testPermittedAxis(int axis, String errorCode) throws XPathException {
        if (axis == AxisInfo.PRECEDING_OR_ANCESTOR) {
            grumble("The preceding-or-ancestor axis is for internal use only", errorCode);
        }
        if (languageVersion < 40 && (
                axis == AxisInfo.PRECEDING_SIBLING_OR_SELF ||
                        axis == AxisInfo.FOLLOWING_SIBLING_OR_SELF ||
                        axis == AxisInfo.FOLLOWING_OR_SELF ||
                        axis == AxisInfo.PRECEDING_OR_SELF)) {
            grumble("The " + AxisInfo.axisName[axis] + " axis requires 4.0 to be enabled");
        }

    }


    public Expression parseNumericLiteral(boolean traceable) throws XPathException {
        int offset = t.currentTokenStartOffset;
        NumericValue number = ((Token.NumericLiteral) t.currentToken).getValue();
//        if (number.isNaN()) {
//            grumble("Invalid numeric literal " + Err.wrap(t.currentTokenValue, Err.VALUE));
//        }
        nextToken();
        Literal lit = Literal.makeLiteral(number);
        setLocation(lit, offset);
        //lit.setRetainedStaticContext(env.makeRetainedStaticContext());
        return traceable ? makeTracer(lit, null) : lit;
    }

    protected Expression parseStringLiteral(boolean traceable) throws XPathException {
        Literal literal = makeStringLiteral(expectStringLiteral(), true);
        nextToken();
        return traceable ? makeTracer(literal, null) : literal;
    }

    protected Expression parseStringConstructor() throws XPathException {
        grumble("String constructor expressions are allowed only in XQuery, not in XPath");
        return null;
    }

    protected void requireXQuery(String construct) throws XPathException {
        grumble(construct + " is allowed only in XQuery, not in XPath");
    }

    public Expression parseStringTemplate() throws XPathException {
        Token.StringTemplate template = (Token.StringTemplate) t.currentToken;
        int offset = template.getStartOffset();
        t.reposition(offset);
        List<Expression> components = new ArrayList<>();
        StringBuilder currentPart = new StringBuilder();
        boolean finished = false;
        do {
            char c = t.nextChar();
            switch (c) {
                case (char) 0:
                    grumble("Unclosed string template");
                    return null;
                case '`':
                    if (t.peekChar() == '`') {
                        currentPart.append('`');
                        t.nextChar();
                    } else {
                        if (!currentPart.isEmpty()) {
                            components.add(new StringLiteral(currentPart.toString()));
                            currentPart.setLength(0);
                        }
                        template.close();
                        finished = true;
                    }
                    break;
                case '{':
                    if (t.peekChar() == '{') {
                        currentPart.append('{');
                        t.nextChar();
                    } else {
                        if (!currentPart.isEmpty()) {
                            components.add(new StringLiteral(currentPart.toString()));
                            currentPart.setLength(0);
                        }
                        t.startEmbeddedExpression();
                        if (t.currentToken != Token.EOF) {
                            Expression exp = parseExpression();
                            RetainedStaticContext rscSJ = new RetainedStaticContext(env);
                            Expression fnSJ = SystemFunction.makeCall("string-join", rscSJ, exp, new StringLiteral(StringValue.SINGLE_SPACE));
                            ExpressionTool.copyLocationInfo(exp, fnSJ);
                            components.add(fnSJ);
                            expect(Token.EOF);
                        }
                        t.endEmbeddedExpression();
                    }
                    break;
                case '}':
                    if (t.peekChar() == '}') {
                        currentPart.append('}');
                        t.nextChar();
                    } else {
                        grumble("Closing brace ('}') in string template must be doubled");
                    }
                    break;
                default:
                    currentPart.append(c);
                    break;
            }
        } while (!finished);

        Expression[] args = components.toArray(new Expression[0]);
        Expression result = SystemFunction.makeCall("concat", env.makeRetainedStaticContext(), args);
        setLocation(result, offset);
        return result;
    }

    public StructuredQName parseEQName() throws XPathException {
        nextToken();
        String var = readName();
        if (scanOnly) {
            return new StructuredQName("", NamespaceUri.SAXON_GENERATED_VARIABLE, "dummy");
        }
        StructuredQName vtest = makeStructuredQName(var, NamespaceUri.NULL);
        assert vtest != null;
        return vtest;
    }

    /*@NotNull*/
    public Expression resolveVariableReference(int offset, StructuredQName vtest) throws XPathException {
        // See if it's a range variable or a variable in the context
        if (scanOnly) {
            return Literal.makeEmptySequence();
        }
        LocalBinding b = findRangeVariable(vtest);
        Expression ref;
        if (b != null) {
            ref = new LocalVariableReference(b);
        } else {
            if (catchDepth > 0) {
                for (StructuredQName errorVariable : StandardNames.errorVariables) {
                    if (errorVariable.getLocalPart().equals(vtest.getLocalPart())) {
                        StructuredQName functionName =
                                new StructuredQName("saxon", NamespaceUri.SAXON, "dynamic-error-info");
                        SymbolicName.F sn = new SymbolicName.F(functionName, 1);
                        Expression[] args = new Expression[]{new StringLiteral(vtest.getLocalPart())};
                        return VendorFunctionSetHE.getInstance().bind(sn, args, null, env, new ArrayList<>());
                    }
                }
            }
            try {
                ref = env.bindVariable(vtest);
            } catch (XPathException err) {
                throw err.maybeWithLocation(makeLocation(offset));
            }
        }
        setLocation(ref, offset);
        return ref;
    }

    /**
     * Method to make a string literal from a token identified as a string
     * literal. This is trivial in XPath, but in XQuery the method is overridden
     * to identify pseudo-XML character and entity references. Note that the job of handling
     * doubled string delimiters is done by the tokenizer.
     *
     * @param currentTokenValue the token as read (excluding quotation marks)
     * @param unescape true if (in XQuery only) entity and character references are to be unescaped
     * @return The string value of the string literal
     * @throws net.sf.saxon.trans.XPathException if a static error is found
     */

    /*@NotNull*/
    protected Literal makeStringLiteral(String currentTokenValue, boolean unescape) throws XPathException {
        StringLiteral literal = new StringLiteral(currentTokenValue);
        setLocation(literal);
        return literal;
    }

    /**
     * Unescape character references and built-in entity references in a string. Does nothing
     * in XPath, because XPath does not recognize entity references in string literals
     *
     * @param token the input string, which may include XML-style character references or built-in
     *              entity references
     * @return the string with character references and built-in entity references replaced by their expansion
     * @throws XPathException if a malformed character or entity reference is found
     */

    /*@NotNull*/
    protected String unescape(String token) throws XPathException {
        return token;
    }


    /**
     * Parse a computed node constructor. This is allowed only in XQuery, so the method throws
     * an error for XPath.
     *
     * @return the expression that results from the parsing
     * @throws net.sf.saxon.trans.XPathException if a static error occurs
     */

    /*@NotNull*/
    protected Expression parseComputedNodeConstructor() throws XPathException {
        grumble("Node constructor expressions are allowed only in XQuery, not in XPath");
        return new ErrorExpression();
    }

    protected Expression parseDirectPIConstructor() throws XPathException {
        grumble("Node constructor expressions are allowed only in XQuery, not in XPath");
        return new ErrorExpression();
    }

    /**
     * Parse a direct element constructor
     *
     * @param isNested true if this constructor is nested directly as part of the content of another
     *                 element constructor. This has the effect that the child element is not copied, which means
     *                 that namespace inheritance (which only happens during copying) has no effect
     * @return the expression representing the constructor
     * @throws XPathException if a syntax error is found
     */

    protected Expression parseDirectElementConstructor(boolean isNested) throws XPathException {
        grumble("Node constructor expressions are allowed only in XQuery, not in XPath");
        return new ErrorExpression();
    }

    /**
     * Parse a dynamic function call
     *
     * @param functionItem the expression that determines the function to be called
     * @param prefixArgument the LHS of an arrow operator, or null if this is not part of an arrow expression
     * @return the expression that results from the parsing
     * @throws net.sf.saxon.trans.XPathException if a static error is found
     */

    /*@NotNull*/
    public Expression parseDynamicFunctionCall(Expression functionItem, Expression prefixArgument) throws XPathException {
        checkLanguageVersion30();

        ArrayList<Expression> args = new ArrayList<>(10);
        if (prefixArgument != null) {
            args.add(prefixArgument);
        }
        int placeHolderSequence = 0;

        // the "(" has already been read by the Tokenizer: now parse the arguments
        nextToken();
        if (t.currentToken != Token.RPAREN) {
            while (true) {
                Expression arg;
                Token peek = t.peekAhead();
                if (t.currentToken == Token.QMARK && (peek == Token.COMMA || peek == Token.RPAREN)) {
                    nextToken();
                    // this is a "?" placemarker
                    arg = new PlaceHolder(placeHolderSequence++);
                } else {
                    arg = parseFunctionArgument();
                }
                args.add(arg);
                if (t.currentToken == Token.COMMA) {
                    nextToken();
                } else if (t.currentToken == Token.COLON) {
                    grumble("Keyword arguments are not allowed in a dynamic function call");
                } else {
                    break;
                }
            }
            expect(Token.RPAREN);
        }
        nextToken();

        if (placeHolderSequence == 0) {
            DynamicFunctionCall call = new DynamicFunctionCall(functionItem, args);
            setLocation(call, t.currentTokenStartOffset);
            return call;
        } else {
            Expression result = new DynamicPartialApply(functionItem, args.toArray(new Expression[]{}));
            setLocation(result, t.currentTokenStartOffset);
            return result;
        }
    }

    /**
     * Parse a lookup operator ("?")
     *
     * @param lhs the expression that the function to be called
     * @return the expression that results from the parsing
     * @throws net.sf.saxon.trans.XPathException if a static error is found
     */

    /*@NotNull*/
    protected Expression parseLookup(Expression lhs) throws XPathException {
        checkLanguageVersion31();
        int offset = t.currentTokenStartOffset;
        nextToken();
        Token token = t.currentToken;

        Expression result;

        if (token instanceof Token.NameToken) {
            String name = t.currentName();
            if (!NameChecker.isValidNCName(StringTool.codePoints(name))) {
                grumble("The name following '?' must be a valid NCName");
            }
            nextToken();
            if (name.equals("type") && t.currentToken == Token.LPAREN) {
                // TODO: syntax dropped?
                nextToken();
                SequenceType requiredType = parseSequenceType();
                expect(Token.RPAREN);
                nextToken();
                result = parserExtension.lookupStar(lhs, requiredType, languageVersion >= 40);
            } else {
                result = env.getConfiguration().makeLookupExpression(
                        lhs, new StringLiteral(name), languageVersion >= 40);
            }
        } else if (token instanceof Token.NumericLiteral) {
            NumericValue number = ((Token.NumericLiteral) token).getValue();
            if (languageVersion < 40) {
                if (!(number instanceof IntegerValue)) {
                    grumble("Number following '?' must be an integer");
                }
                if (((Token.NumericLiteral) token).getRadix() != 10) {
                    grumble("Number following '?' must be a decimal integer");
                }
            }
            nextToken();
            result = makeLookupExpression(env.getConfiguration(), lhs, Literal.makeLiteral(number));

        } else if (token == Token.HASH) {
            checkLanguageVersion40("a QName literal after `?`");
            StructuredQName qn = parseEQName();
            result = makeLookupExpression(env.getConfiguration(), lhs,
                                          Literal.makeLiteral(new QNameValue(qn, BuiltInAtomicType.QNAME)));

        } else if (token == Token.STAR) {
            nextToken();
            result = parserExtension.lookupStar(lhs, SequenceType.ANY_SEQUENCE, languageVersion >= 40);
        } else if (token == Token.LPAREN) {
            result = makeLookupExpression(env.getConfiguration(), lhs, parseParenthesizedExpression());
        } else if (token instanceof Token.StringLiteral) {
            checkLanguageVersion40("a string literal after `?`");
            String key = ((Token.StringLiteral) token).getValue();
            result = env.getConfiguration().makeLookupExpression(
                    lhs, new StringLiteral(key), languageVersion >= 40);
            nextToken();
        } else if (token == Token.DOLLAR) {
            checkLanguageVersion40("a variable reference directly after `?`");
            offset = t.currentTokenStartOffset;
            StructuredQName varName = parseEQName();
            result = makeLookupExpression(env.getConfiguration(), lhs, resolveVariableReference(offset, varName));
        } else if (token == Token.DOT) {
            checkLanguageVersion40("`.` after `?`");
            nextToken();
            offset = t.currentTokenStartOffset;
            result = makeLookupExpression(env.getConfiguration(), lhs, new ContextValueExpression());

        } else {
            grumble("Unexpected " + token + " after '?'");
            return null;
        }
        setLocation(result, offset);
        return result;
    }


    protected Expression makeLookupExpression(Configuration config, Expression lhs, Expression rhs) throws XPathException {
        return config.makeLookupExpression(lhs, rhs, languageVersion >= 40);
    }


    /**
     * Parse a NodeTest.
     * One of QName, prefix:*, *:suffix, *, text(), node(), comment(), or
     * processing-instruction(literal?), or element(~,~), attribute(~,~), etc.
     *
     * <p>Note that in 4.0 the semantics depend on whether the context item is an XNode
     * or a JNode, but we don't know that yet. So we have to retain enough information
     * to handle both cases. In particular this means the default node kind for the
     * axis, and the policy for handling unprefixed QNames, both of which are needed
     * in the case where it's an XNode selection.</p>
     *
     * @param nodeKind the node type being sought if one is specified
     * @return the resulting NodeTest object
     * @throws XPathException if any error is encountered
     */

    /*@NotNull*/
    protected NodeTest parseNodeTest(short nodeKind) throws XPathException {
        Token tok = t.currentToken;
        if (tok instanceof Token.NameToken) {
            if (t.peekAhead() == Token.LPAREN) {
                return parseKindTest(false);
            }
            QNameTest test = makeNameTest(nodeKind, ((Token.NameToken) tok).getValue(), nodeKind == Type.ELEMENT);
            boolean asNCName = NameChecker.isValidNCName(((Token.NameToken) tok).getValue());
            nextToken();

            if (nodeKind == Type.ATTRIBUTE || nodeKind == Type.NAMESPACE || !isAllowXPath40Syntax()) {
                // these axes never select JNodes
                return new NamedXNodeType(nodeKind, test, env.getConfiguration());
            }
            return new SelectorTest(test, asNCName, nodeKind);

//            GNodeType type = new NamedXNodeType(nodeKind, test, env.getConfiguration());
//            return new NodeTypeTest(type);
        } else if (tok == Token.LPAREN) {
            return parseUnionNodeTest(nodeKind);
        } else if (tok == Token.STAR) {
            nextToken();
            return new NodeTestStar(nodeKind);
        } else if (tok instanceof Token.Wildcard) {
            nextToken();
            QNameTest test = getQNameTestForWildcard(((Token.Wildcard) tok));
            return new NamedXNodeType(nodeKind, test, env.getConfiguration());
        } else {
            grumble("Unrecognized node test");
            return null;
        }
    }

    protected NodeTest parseUnionNodeTest(short nodeType) throws XPathException {
        nextToken();
        NodeTest test = parseNodeTest(nodeType);
        while (t.currentToken == Token.VBAR) {
            checkLanguageVersion40("choice item types");
            nextToken();
            test = new CombinedNodeTest(test, OperatorSymbol.UNION, parseNodeTest(nodeType));
        }
        readToken(Token.RPAREN);
        return test;
    }

    /**
     * Parse a JNode item type: jnode(selector, contentType) where
     * selector is typically "*" or an NCName or a Constant or (), and contentType
     * is a sequence type
     * @return the parsed jnode item type
     * @throws XPathException on failure
     */

    private JNodeType parseJNodeType() throws XPathException {
        Token firstArg;
        AtomicValue selector = null;
        expectKeyword("jnode");
        nextToken();
        readToken(Token.LPAREN);
        if (t.currentToken == Token.RPAREN) {
            nextToken();
            return AnyJNodeType.getInstance();
        } else {
            firstArg = t.currentToken;
            if (firstArg == Token.LPAREN) {
                nextToken();
                expect(Token.RPAREN);
                nextToken();
                if (t.currentToken == Token.RPAREN) {
                    nextToken();
                    return new RootJNodeType(SequenceType.ANY_SEQUENCE);
                }
            } else if (firstArg == Token.STAR) {
                nextToken();
                if (t.currentToken == Token.RPAREN) {
                    nextToken();
                    return AnyJNodeType.getInstance();
                }
            } else if (firstArg instanceof Token.NameToken) {
                String key = ((Token.NameToken) firstArg).getValue();
                if (!NameChecker.isValidNCName(key)) {
                    grumble("JNode selector " + key + " is not a valid NCName");
                }
                if (t.peekAhead() == Token.LPAREN) {
                    // true() or false()
                    selector = parseConstant();
                } else {
                    selector = new StringValue(key);
                    nextToken();
                }
            } else {
                selector = parseConstant();
            }
            if (t.currentToken == Token.COMMA) {
                nextToken();
                SequenceType contentType;
                if (t.currentToken == Token.STAR) {
                    contentType = SequenceType.ANY_SEQUENCE;
                    nextToken();
                } else {
                    contentType = parseSequenceType();
                }
                readToken(Token.RPAREN);
                return firstArg == Token.LPAREN
                        ? new RootJNodeType(contentType)
                        : new SpecificJNodeType(selector, contentType);
            } else {
                expect(Token.RPAREN);
                nextToken();
                return new SpecificJNodeType(selector, SequenceType.ANY_SEQUENCE);
            }

        }
    }

    // Keywords that can start an item type but not a node test
    private final static String[] notAllowedAsNodeTest = new String[]{"array", "enum", "item", "map", "record"};

    /**
     * Parse a KindTest. On entry we are positioned on a keyword (such as "item")
     * and we know it is followed by a left paren.
     * @param expectItemType if we are allowing any item type, not just a NodeTest/JNodeType
     * @return the KindTest, expressed as a NodeTest object
     * @throws net.sf.saxon.trans.XPathException if a static error is found
     */

    /*@NotNull*/
    private GNodeType parseKindTest(boolean expectItemType) throws XPathException {
        Configuration config = env.getConfiguration();
        NamePool pool = config.getNamePool();

        String kind = expectName();
        if (!expectItemType && Arrays.binarySearch(notAllowedAsNodeTest, kind) >= 0) {
            grumble("Item type " + kind + "() cannot be used as a node test (or as an expression)");
        }
        switch (kind) {
            case "item":
                expectEmptyParens();
                return AnyGNodeType.getInstance();
            case "node":
                expectEmptyParens();
                return AnyXNodeType.getInstance();
            case "text":
                expectEmptyParens();
                return NodeKindType.TEXT;
            case "comment":
                expectEmptyParens();
                return NodeKindType.COMMENT;
            case "namespace-node":
                expectEmptyParens();
                return NodeKindType.NAMESPACE;
            case "document-node":
                nextToken();
                readToken(Token.LPAREN);
                if (t.currentToken == Token.RPAREN) {
                    nextToken();
                    return NodeKindType.DOCUMENT;
                } else if (isKeyword("element") || isKeyword("schema-element")) {
                    XNodeType inner = (XNodeType) parseKindTest(true);
                    readToken(Token.RPAREN);
                    return new DocumentNodeType(inner);
                } else if (languageVersion >= 40) {
                    QNameTest qNameTest = parseNameTestUnion(Type.ELEMENT);
                    readToken(Token.RPAREN);
                    return new DocumentNodeType(
                            new NamedXNodeType(Type.ELEMENT, qNameTest, config));
                } else {
                    grumble("After 'document-node('", " expected 'element' or 'schema-element'");
                    return null;
                }

            case "processing-instruction": {
                Token piNameToken = readParenthesizedToken(true);
                if (piNameToken == null) {
                    return NodeKindType.PROCESSING_INSTRUCTION;
                }
                int fp = -1;
                StructuredQName piQName = null;
                if (piNameToken instanceof Token.StringLiteral) {
                    String piName = Whitespace.trim(unescape(((Token.StringLiteral) piNameToken).getValue()));
                    if (!NameChecker.isValidNCName(StringTool.codePoints(piName))) {
                        // Became an error as a result of XPath erratum XP.E7
                        grumble("Processing instruction name must be a valid NCName", "XPTY0004");
                    } else {
                        piQName = new StructuredQName("", NamespaceUri.NULL, piName);
                        fp = pool.allocateFingerprint(NamespaceUri.NULL, piName);
                    }
                } else if (piNameToken instanceof Token.NameToken) {
                    try {
                        String[] parts = NameChecker.getQNameParts(((Token.NameToken) piNameToken).getValue());
                        if (parts[0].isEmpty()) {
                            piQName = new StructuredQName("", NamespaceUri.NULL, parts[1]);
                        } else {
                            grumble("Processing instruction name must not contain a colon");
                        }
                    } catch (QNameException e) {
                        grumble("Invalid processing instruction name. " + e.getMessage());
                    }
                } else {
                    grumble("Processing instruction name must be a QName or a string literal");
                }
                assert (piQName != null);
                return new NamedXNodeType(Type.PROCESSING_INSTRUCTION, piQName, config);
            }
            case "schema-attribute": {
                Token name = readParenthesizedToken(false);
                if (!(name instanceof Token.NameToken)) {
                    grumble("Expected name after 'schema-attribute('");
                    return null;
                }
                int fp = makeFingerprint(((Token.NameToken) name).getValue(), false);
                IAttributeDecl decl = env.getImportedSchema().getAttributeDecl(fp);
                if (decl == null) {
                    grumble("There is no declaration for attribute @" + name + " in an imported schema", "XPST0008");
                    return null;
                } else {
                    return env.getImportedSchema().makeSchemaAttributeTest(fp);
                }
            }
            case "schema-element": {
                Token name = readParenthesizedToken(false);
                if (!(name instanceof Token.NameToken)) {
                    grumble("Expected name after 'schema-attribute('");
                    return null;
                }
                int fp = makeFingerprint(((Token.NameToken) name).getValue(), true);
                IElementDecl decl = env.getImportedSchema().getElementDecl(fp);

                if (decl == null) {
                    grumble("There is no declaration for element " + name + " in an imported schema", "XPST0008");
                    return null;
                } else {
                    return env.getImportedSchema().makeSchemaElementTest(fp);
                }
            }

            case "attribute":
            case "element":
                boolean isElementTest = kind.equals("element");
                short nodeKind = isElementTest ? Type.ELEMENT : Type.ATTRIBUTE;
                nextToken();
                readToken(Token.LPAREN);
                if (t.currentToken == Token.RPAREN) {
                    nextToken();
                    return isElementTest ? NodeKindType.ELEMENT : NodeKindType.ATTRIBUTE;
                }
                QNameTest qNameTest2 = parseNameTestUnion(nodeKind);

                if (languageVersion < 40) {
                    if (qNameTest2 instanceof NamespaceQNameTest || qNameTest2 instanceof LocalQNameTest) {
                        grumble("Wildcard syntax in item types requires 4.0 to be enabled");
                    } else if (qNameTest2 instanceof UnionQNameTest) {
                        grumble("Union syntax in item types requires 4.0 to be enabled");
                    }
                }

                if (t.currentToken == Token.RPAREN) {
                    nextToken();
                    return new NamedXNodeType(nodeKind, qNameTest2, config);
                } else if (t.currentToken == Token.COMMA) {
                    nextToken();
                    GNodeType result;
                    if (t.currentToken instanceof Token.NameToken) {
                        StructuredQName contentType = makeStructuredQName(
                                ((Token.NameToken) t.currentToken).getValue(), env.getDefaultElementNamespace());
                        SchemaType schemaType = env.getImportedSchema().getSchemaType(contentType);

                        if (schemaType == null) {
                            grumble("Unknown type name " + contentType.getEQName(), "XPST0008");
                            return null;
                        }
                        if (nodeKind == Type.ATTRIBUTE && schemaType.isComplexType()) {
                            warning("An attribute cannot have a complex type", SaxonErrorCode.SXWN9041);
                        }
                        nextToken();
                        boolean nillable = false;
                        if (t.currentToken == Token.QMARK) {
                            nillable = true;
                            if (nodeKind == Type.ATTRIBUTE) {
                                grumble("attribute() tests must not be nillable");
                            }
                            nextToken();
                        }
                        result = new NamedXNodeType(
                                nodeKind, qNameTest2, schemaType, nillable, env.getConfiguration());
                    } else {
                        grumble("Unexpected " + t.currentToken + " after ',' in SequenceType");
                        return null;
                    }
                    readToken(Token.RPAREN);
                    return result;
                } else {
                    grumble("Expected ')' or ',' in SequenceType");
                }
                return null;

            case "jnode": {
                checkLanguageVersion40("NodeTest jnode()");
                return parseJNodeType();

            }
            case "gnode":
                checkLanguageVersion40("NodeTest gnode()");
                expectEmptyParens();
                return AnyGNodeType.getInstance();

            case "record": {
                checkLanguageVersion40("nodeTest record(...)");
                ItemType type = parseRecordType(this);
                return SpecificJNodeType.jTreeRoot(SequenceType.one(type));
            }

            case "array": {
                checkLanguageVersion40("nodeTest " + kind + "()");
                ItemType type = parseArrayItemType();
                return SpecificJNodeType.jTreeRoot(SequenceType.one(type)
                );
            }
            case "map": {
                checkLanguageVersion40("nodeTest " + kind + "()");
                ItemType type = parseMapItemType();
                return SpecificJNodeType.jTreeRoot(SequenceType.one(type)
                );
            }
            case "enum": {
                checkLanguageVersion40("nodeTest " + kind + "()");
                ItemType type = parseEnumType();
                return new SpecificJNodeType(SequenceType.one(type)
                );
            }
            default:
                // can't happen!
                grumble("Unknown node kind " + kind);
                return null;
        }
    }

    /**
     * Read a construct such as "item()". On entry, we are positioned on the keyword "item".
     * If this is followed by "()", we return true, and the current position is the token
     * after the "()". Otherwise, we throw an error.
     * @throws XPathException if the current token is not followed by "()"
     */
    private void expectEmptyParens() throws XPathException {
        Token keyword = t.currentToken;
        nextToken();
        readToken(Token.LPAREN);
        if (t.currentToken == Token.RPAREN) {
            nextToken();
        } else {
            grumble("Expected empty parentheses '()' after '" + keyword + "'");
        }
    }

    /**
     * Read a construct such as schema-element(N). On entry, we are positioned on the
     * keyword (for example "schema-element"). The parameter "optional" indicates whether
     * the name is required. If the syntax is correct, the method returns the name (if present);
     * otherwise we throw an error.
     * @param optional true if the name between the parentheses is optional
     * @return the name found between the parentheses, or null if there was no name and it is not required
     * @throws XPathException on  syntax error
     */

    private Token readParenthesizedToken(boolean optional) throws XPathException {
        Token keyword = t.currentToken;
        nextToken();
        readToken(Token.LPAREN);
        if (t.currentToken == Token.RPAREN) {
            if (optional) {
                nextToken();
                return null;
            } else {
                grumble("Expected a name after '" + keyword + "('");
            }
        } else {
            Token name = t.currentToken;
            nextToken();
            readToken(Token.RPAREN);
            return name;
        }
        return null;
    }


    public QNameTest parseNameTestUnion(short nodeKind) throws XPathException {
        List<QNameTest> tests = new ArrayList<>();
        boolean matchesAll = false;
        while (true) {
            Token tok = t.currentToken;
            if (tok instanceof Token.NameToken) {
                tests.add(makeNameTest(nodeKind, t.currentName(), true));
                nextToken();
            } else if (tok instanceof Token.Wildcard) {
                nextToken();
                QNameTest test = getQNameTestForWildcard((Token.Wildcard) tok);
                tests.add(test);
            } else if (tok == Token.STAR) {
                nextToken();
                matchesAll = true;
            } else {
                grumble("Unrecognized name test at " + t.currentToken);
                return null;
            }
            if (t.currentToken == Token.VBAR) {
                nextToken();
            } else {
                break;
            }
        }
        if (matchesAll) {
            // If there's a "*" in the list, then anything else gets swallowed
            return AnyQNameTest.getInstance();
        }
        if (tests.size() == 1) {
            return tests.get(0);
        }
        return new UnionQNameTest(tests);
    }

    /**
     * Ask whether the syntax namespace-node() is allowed in a node kind test.
     *
     * @return true unless XPath 2.0 / XQuery 1.0 syntax is required
     */

    protected boolean isNamespaceTestAllowed() {
        return languageVersion >= 30;
    }

    /**
     * Check that XPath 3.0 is in use
     *
     * @throws net.sf.saxon.trans.XPathException if XPath 3.0 support was not requested
     */

    protected void checkLanguageVersion30() throws XPathException {
        if (languageVersion < 30) {
            grumble("To use XPath 3.0 syntax, you must configure the XPath parser to handle it");
        }
    }

    /**
     * Check that XPath 3.1 is in use
     *
     * @throws net.sf.saxon.trans.XPathException if XPath 3.1 support was not requested
     */

    protected void checkLanguageVersion31() throws XPathException {
        if (languageVersion < 31) {
            grumble("The XPath parser is not configured to allow use of XPath 3.1 syntax");
        }
    }

    /**
     * Check that XPath/XQuery 4.0 is in use
     *
     * @throws net.sf.saxon.trans.XPathException if XPath 3.1 support was not requested
     */

    protected void checkLanguageVersion40() throws XPathException {
        String lang = getLanguage();
        if (languageVersion < 40) {
            grumble("The parser is not configured to allow use of " + lang + " 4.0 syntax");
        }
    }

    protected void checkLanguageVersion40(String feature) throws XPathException {
        String lang = getLanguage();
        if (languageVersion < 40) {
            grumble("The parser is not configured to allow use of " + lang + " 4.0 syntax, which is needed when using " + feature);
        }
    }

    /**
     * Check that the map syntax is enabled: this covers the extensions to XPath 3.0
     * syntax defined in XSLT 3.0 (and also, of course, in XPath 3.1)
     *
     * @throws net.sf.saxon.trans.XPathException if XPath 3.1 support was not requested
     */

    protected void checkMapExtensions() throws XPathException {
        if (!(languageVersion >= 31 || allowXPath30XSLTExtensions)) {
            grumble("The XPath parser is not configured to allow use of the map syntax from XSLT 3.0 or XPath 3.1");
        }
    }

    /**
     * Check that Saxon syntax extensions are permitted
     * @param construct name of the construct, for use in error messages
     * @throws XPathException if Saxon syntax extensions have not been enabled
     */

    public void checkSyntaxExtensions(String construct) throws XPathException {
        if (languageVersion < 40) {
            grumble("Saxon XPath syntax extensions have not been enabled: " + construct + " is not allowed");
        }
    }

    /**
     * Parse a map constructor. Syntax
     * 3.0/3.1:  map { expr : expr (, expr : expr )*} }
     * 4.0:      { expr : expr (, expr : expr )*} }
     *
     * @return the map expression
     * @throws XPathException if parsing fails
     */

    /*@NotNull*/
    protected Expression parseMapConstructor() throws XPathException {
        checkMapExtensions();

        // have read the "map {" or bare "{"
        int offset = t.currentTokenStartOffset;
        List<Expression> operands = new ArrayList<>();
        boolean allLiteralKeys = true;
        boolean allStringKeys = true;
        boolean allLiteralValues = true;
        nextToken();
        if (t.currentToken != Token.RCURLY) {
            int seq = 0;
            while (true) {
                seq++;
                Expression key = parseExprSingle();
                if (!(key instanceof Literal && ((Literal) key).getGroundedValue() instanceof AtomicValue)) {
                    allLiteralKeys = false;
                }
                if (!(key instanceof StringLiteral)) {
                    allStringKeys = false;
                }
                if (t.currentToken == Token.COLON_EQUALS) {
                    grumble("The ':=' notation is no longer accepted in map expressions: use ':' instead");
                }
                if (t.currentToken == Token.COLON) {
                    nextToken();
                    Expression value = parseExprSingle();
                    operands.add(
                            MapFunctionSet.getInstance(languageVersion).makeFunction("entry", 2).makeFunctionCall(key, value));
                    if (!(value instanceof Literal)) {
                        allLiteralValues = false;
                    }
                } else {
                    checkSyntaxExtensions("nested expressions in map constructors");
                    allLiteralKeys = false;
                    allLiteralValues = false;
                    allStringKeys = false;
                    int finalSeq = seq;
                    Supplier<RoleDiagnostic> role = () -> new RoleDiagnostic(RoleDiagnostic.MAP_CONSTRUCTOR, "", finalSeq);
                    key = new ItemChecker(key, MapType.ANY_MAP_TYPE, role);
                    operands.add(key);
                }
                if (t.currentToken == Token.RCURLY) {
                    break;
                } else {
                    readToken(Token.COMMA);
                    if (t.currentToken == Token.RCURLY) {
                        grumble("Comma is not allowed after final entry in map constructor");
                    }
                }
            }
        }
        nextToken();

        Expression result;
        switch (operands.size()) {
            case 0:
                result = Literal.makeLiteral(EmptyMap.getInstance(languageVersion));
                break;
            case 1:
                if (allLiteralKeys && allLiteralValues) {
                    FunctionCall operand = (FunctionCall) operands.get(0);
                    Literal key = (Literal) operand.getArguments()[0];
                    Literal value = (Literal) operand.getArguments()[1];
                    SingleEntryMap sem = new SingleEntryMap(
                            (AtomicValue) (key.getGroundedValue()),
                            value.getGroundedValue(),
                            languageVersion);
                    result = Literal.makeLiteral(sem);
                } else {
                    GeneralMapBuilder optionsBuilder = AbstractFixedMap.getBuilder(languageVersion);
                    optionsBuilder.put(new StringValue("duplicates"), MapItem.mapConstructorDuplicatesAction);
                    optionsBuilder.put(new QNameValue("", NamespaceUri.SAXON, "allow-streaming"), BooleanValue.FALSE);
                    MapItem optionsMap = optionsBuilder.getCompletedMap();
                    result = MapFunctionSet.getInstance(languageVersion).makeFunction("merge", 2)
                            .makeFunctionCall(operands.get(0), Literal.makeLiteral(optionsMap));
                    result.setRetainedStaticContext(env.makeRetainedStaticContext());
                }

                break;
            default:

                // Check for duplicates now, among key values that are statically known
                Set<AtomicMatchKey> matchKeys = new HashSet<>();
                for (Expression operand : operands) {
                    if (operand.isCallOn(MapFunctionSet.MapEntry.class)) {
                        if (((FunctionCall) operand).getArg(0) instanceof Literal) {
                            final AtomicValue keyValue =
                                    (AtomicValue) ((Literal) ((FunctionCall) operand).getArg(0)).getGroundedValue();

                            AtomicMatchKey key = keyValue.asMapKey(languageVersion);
                            boolean ok = matchKeys.add(key);
                            if (!ok) {
                                grumble("Duplicate key + " + Err.depict(keyValue) + " in map constructor", "XQDY0137");
                                return null;
                            }
                        }
                    }
                }

                if (allLiteralKeys && allLiteralValues) {
                    if (allStringKeys) {
                        StringMapBuilder mapBuilder = new StringMapBuilder(operands.size());
                        for (Expression expression : operands) {
                            FunctionCall operand = (FunctionCall) expression;
                            Literal keyExp = (Literal) operand.getArguments()[0];
                            Literal valueExp = (Literal) operand.getArguments()[1];
                            UnicodeString key = ((StringLiteral) keyExp).getUnicodeString();
                            GroundedValue val = valueExp.getGroundedValue();
                            mapBuilder.put(key, val);
                        }
                        result = Literal.makeLiteral(mapBuilder.getCompletedMap());
                    } else {
                        GeneralMapBuilder builder = FixedMap.getBuilder(languageVersion);
                        for (Expression expression : operands) {
                            FunctionCall operand = (FunctionCall) expression;
                            Literal keyExp = (Literal) operand.getArguments()[0];
                            Literal valueExp = (Literal) operand.getArguments()[1];
                            AtomicValue key = (AtomicValue) keyExp.getGroundedValue();
                            GroundedValue val = valueExp.getGroundedValue();
                            builder.put(key, val);
                        }
                        result = Literal.makeLiteral(builder.getCompletedMap());
                    }
                } else {
                    Expression[] entriesArray = new Expression[operands.size()];
                    for (int i = 0; i < operands.size(); i++) {
                        entriesArray[i] = operands.get(i);
                    }
                    Block block = new Block(entriesArray);
                    MapItem optionsMap;
                    if (allLiteralKeys) {
                        optionsMap = EmptyMap.getInstance(languageVersion);
                    } else {
                        GeneralMapBuilder optionsBuilder = AbstractFixedMap.getBuilder(languageVersion);
                        optionsBuilder.put(new StringValue("duplicates"), MapItem.mapConstructorDuplicatesAction);
                        optionsBuilder.put(new QNameValue("", NamespaceUri.SAXON, "allow-streaming"), BooleanValue.TRUE);
                        optionsMap = optionsBuilder.getCompletedMap();
                    }
                    result = MapFunctionSet.getInstance(languageVersion).makeFunction("merge", 2)
                            .makeFunctionCall(block, Literal.makeLiteral(optionsMap));
                    result.setRetainedStaticContext(env.makeRetainedStaticContext());
                }
                break;
        }
        setLocation(result, offset);
        return result;

    }

    /**
     * Parse a "square" array constructor
     * "[" (exprSingle ("," exprSingle)* )? "]"
     * Applies to XPath/XQuery 3.1 only
     * @return the parsed expression
     * @throws XPathException if the syntax is wrong
     */
    protected Expression parseArraySquareConstructor() throws XPathException {
        checkLanguageVersion31();
        Tokenizer t = getTokenizer();
        int offset = t.currentTokenStartOffset;
        List<Expression> members = new ArrayList<>();
        nextToken();
        if (t.currentToken == Token.RSQB) {
            nextToken();
            SquareArrayConstructor arrayBlock = new SquareArrayConstructor(members);
            arrayBlock.setLocation(makeLocation(offset));
            setLocation(arrayBlock, offset);
            return arrayBlock;
        }
        while (true) {
            Expression member = parseExprSingle();
            members.add(member);
            if (t.currentToken == Token.COMMA) {
                nextToken();
                if (t.currentToken == Token.RSQB) {
                    grumble("Comma is not allowed after final member in array constructor");
                }
                continue;
            } else if (t.currentToken == Token.RSQB) {
                nextToken();
                break;
            }
            grumble("Expected ',' or ']', " + "found " + t.currentToken);
            return new ErrorExpression();
        }
        SquareArrayConstructor block = new SquareArrayConstructor(members);
        block.setLocation(makeLocation(offset));
        return block;
    }

    /**
     * Parse a "curly" array constructor
     * array "{" expr "}"
     * Applies to XPath/XQuery 3.1 only
     *
     * @return the parsed expression
     * @throws XPathException if the syntax is invalid or the construct is not permitted
     */

    protected Expression parseArrayCurlyConstructor() throws XPathException {
        checkLanguageVersion31();
        Tokenizer t = getTokenizer();
        int offset = t.currentTokenStartOffset;
        nextToken();
        if (t.currentToken == Token.RCURLY) {
            nextToken();
            return Literal.makeLiteral(SimpleArrayItem.EMPTY_ARRAY);
        }
        Expression body = parseExpression();
        readToken(Token.RCURLY);

        SystemFunction sf = ArrayFunctionSet.getInstance(40).makeFunction("_from-sequence", 1);
        Expression result = sf.makeFunctionCall(body);
        setLocation(result, offset);
        return result;
    }

    /**
     * Parse a static function call. On entry the current token is the function name.
     * function-name '(' ( Expression (',' Expression )* )? ')'
     *
     * @param prefixArgument left hand operand of arrow operator,
     *                       or null in the case of a conventional function call
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    /*@NotNull*/
    public Expression parseFunctionCall(String fname, Expression prefixArgument) throws XPathException {

        int offset = t.currentTokenStartOffset;
        nextToken();
        expect(Token.LPAREN);
        ArrayList<Expression> args = new ArrayList<>(10);
        if (prefixArgument != null) {
            args.add(prefixArgument);
        }

        StructuredQName nominalName = null;
        try {
            nominalName = scanOnly
                    ? NamespaceUri.SAXON.qName("dummy")
                    : qNameParser.parse(fname, NamespaceUri.NULL);
        } catch (XPathException e) {
            grumble(e.getMessage(), e.getErrorCodeQName());
        }

        int placeHolderSequence = 0;
        Map<StructuredQName, Integer> keywordArgs = null;
        nextToken();
        if (t.currentToken != Token.RPAREN) {
            while (true) {
                Token peek = t.peekAhead();
                Expression arg;
                if (t.currentToken instanceof Token.NameToken
                        && peek == Token.COLON_EQUALS
                        && isAllowArgumentKeywords()) {
                    // keyword argument
                    StructuredQName paramName = qNameParser.parse(t.currentName(), NamespaceUri.NULL);
                    nextToken(); // read the operator
                    nextToken(); // position on the expression giving the value
                    peek = t.peekAhead();
                    if (t.currentToken == Token.QMARK && (peek == Token.COMMA || peek == Token.RPAREN)) {
                        // keyword := ?
                        nextToken();
                        arg = new PlaceHolder(placeHolderSequence++);
                    } else {
                        arg = parseFunctionArgument();
                    }
                    if (keywordArgs == null) {
                        keywordArgs = new HashMap<>();
                    } else if (keywordArgs.containsKey(paramName)) {
                        grumble("Duplicate keyword '" + paramName + "' in function arguments");
                    }
                    keywordArgs.put(paramName, args.size());
                    args.add(arg);
                } else {
                    if (keywordArgs != null) {
                        String msg = "In a function call, keyword arguments must not be followed by positional arguments";
                        if (t.currentToken instanceof Token.NameToken && peek == Token.EQUALS) {
                            msg += ". Perhaps '=' was used after the keyword instead of ':='";
                        }
                        if (t.currentToken instanceof Token.NameToken && peek == Token.COLON) {
                            msg += ". Perhaps ':' was used after the keyword instead of ':='";
                        }
                        grumble(msg);
                    }
                    if (t.currentToken == Token.QMARK && (peek == Token.COMMA || peek == Token.RPAREN)) {
                        nextToken();
                        // this is a "?" placemarker, with no keyword
                        arg = new PlaceHolder(placeHolderSequence++);
                    } else {
                        arg = parseFunctionArgument();
                    }
                    checkAllowedFunctionArgument(arg);
                    args.add(arg);
                }
                if (t.currentToken == Token.COMMA) {
                    nextToken();
                } else {
                    break;
                }
            }
            expect(Token.RPAREN);
        }
        nextToken();

        // In scanOnly mode, it doesn't matter what kind of expression we return
        if (scanOnly) {
            return new StringLiteral(StringValue.EMPTY_STRING);
        }

        Expression[] arguments = new Expression[args.size()];
        arguments = args.toArray(arguments);

        // In XQuery, we defer binding the function until parsing is complete
        if (env instanceof QueryModule qm) {
            if (isReservedFunctionName(fname, env.getXPathVersion())) {
                grumble("Function name " + fname + " is a reserved name");
            }
            Expression fcall = new DraftFunctionCall(fname,
                                                     qm,
                                                     arguments,
                                                     keywordArgs);
            fcall.setRetainedStaticContext(env.makeRetainedStaticContext());
            setLocation(fcall, offset);
            return fcall;

        }

        // Now bind the function name
        StructuredQName functionQName;
        if (NameChecker.isValidNCName(fname) && isAllowXPath40Syntax()) {
            // First try for a no-namespace name, otherwise a name in the default function namespace
            StructuredQName noNamespaceName = new StructuredQName("", NamespaceUri.NULL, fname);
            FunctionItem fit = env.getFunctionLibrary().getFunctionItem(new SymbolicName.F(noNamespaceName, arguments.length), env);
            if (fit != null) {
                functionQName = noNamespaceName;
            } else {
                functionQName = resolveFunctionName(fname);
            }
        } else {
            functionQName = resolveFunctionName(fname);
        }
        if (env.getConfiguration().isDisabledFunction(functionQName)) {
            grumble("Function " + functionQName.getEQName() + " has been disabled in this Saxon Configuration", "XPST0017", offset);
        }

        // Make a function call expression

        List<String> reasons = new ArrayList<>();
        SymbolicName.F functionName = new SymbolicName.F(functionQName, arguments.length);
        Expression exp = env.getFunctionLibrary().bind(functionName, arguments, keywordArgs, env, reasons);
        if (exp == null) {
            exp = reportMissingFunction(offset, functionQName, arguments, reasons);
        }

        // In most cases, bind() returns a function call
        if (exp instanceof FunctionCall fc) {

            // Inject any default value expressions
            for (int i = 0; i < fc.getArguments().length; i++) {
                if (fc.getArg(i) instanceof DefaultedArgumentExpression) {
                    if (fc instanceof UserFunctionCall ufc) {
                        UserFunction uf = ufc.getFunction();
                        Supplier<Expression> def = uf.getParameterDefinitions()[i].getDefaultValueExpression();
                        if (def != null && def.get() != null) {
                            fc.setArg(i, def.get().copy(new RebindingMap()));
                            // Otherwise, no action: it's a forward reference that will be resolved later
                        }
                    } else if (fc instanceof SystemFunctionCall sfc) {
                        Supplier<Expression> def = sfc.getTargetFunction().getDetails().getDefaultValueExpression(i);
                        fc.setArg(i, def == null ? Literal.makeEmptySequence() : def.get());
                    } else {
                        throw new UnsupportedOperationException();
                    }
                }
                exp.adoptChildExpression(fc.getArg(i));
            }

            // If there are placeholders as well as keywords then we take advantage of the fact that
            // the call on bind() will have dealt with mapping keyword arguments to positional arguments,
            // and evaluating defaulted arguments

            if (placeHolderSequence > 0) {
                SymbolicName.F sn2 = new SymbolicName.F(functionName.getComponentName(), fc.getArguments().length);
                FunctionItem target = env.getFunctionLibrary().getFunctionItem(sn2, env);
                Expression targetExp = makeNamedFunctionReference(functionName.getComponentName(), target);
                setLocation(targetExp, offset);
                return new DynamicPartialApply(targetExp, fc.getArguments());
            }

            // There are special rules for certain functions appearing in a pattern
            if (language == ParsedLanguage.XSLT_PATTERN) {
                if (exp.isCallOn(RegexGroup.class)) {
                    return Literal.makeEmptySequence();

                } else if (exp.isCallOn(CurrentMergeGroup.class)) {
                    grumble("The current-merge-group() function cannot be used in a pattern",
                            "XTSE3470", offset);
                    return new ErrorExpression();
                } else if (exp.isCallOn(CurrentMergeKey.class)) {
                    grumble("The current-merge-key() function cannot be used in a pattern",
                            "XTSE3500", offset);
                    return new ErrorExpression();
                } else if (exp.isCallOn(CurrentMergeKeyArray.class)) {
                    grumble("The current-merge-key-array() function cannot be used in a pattern",
                            "XTSE3500", offset);
                    return new ErrorExpression();
                }
            }

        } else if (placeHolderSequence > 0) {
            // This is a partial apply, but bind() returned something other than a FunctionCall
            // For example, this happens with test xqhof10, xs:NCName(?), where bind() returns a
            // CastExpression
            FunctionItem functionItem = env.getFunctionLibrary().getFunctionItem(functionName, env);
            return new DynamicPartialApply(new FunctionLiteral(functionItem), arguments);
        }


        exp.setRetainedStaticContext(env.makeRetainedStaticContext());
        setLocation(exp, offset);
        return makeTracer(exp, nominalName);


//        setLocation(fcall, offset);
//        for (Expression argument : arguments) {
//            if (fcall != argument && argument.getParentExpression() == null
//                    && !nominalName.hasURI(NamespaceUri.GLOBAL_JS)) {
//                // avoid doing this when the function has already been optimized away, e.g. unordered()
//                // Also avoid doing this when a js: function is parsed into an ixsl:call()
//                // TODO move the adoptChildExpression into individual function libraries
//                fcall.adoptChildExpression(argument);
//            }
//        }


    }

    protected boolean isAllowArgumentKeywords() {
        return languageVersion >= 40;
    }

    protected void checkAllowedFunctionArgument(Expression arg) throws XPathException {
    }

//    /**
//     * Process a static function call in which one or more of the argument positions are
//     * represented as "?" placemarkers (indicating partial application or currying)
//     *
//     * @param parser           the XPath parser
//     * @param offset           offset in the query source of the start of the expression
//     * @param name             the function call (as if there were no currying)
//     * @param args             the arguments (with EmptySequence in the placemarker positions)
//     * @return the curried function
//     * @throws XPathException if a dynamic error occurs
//     */
//
//    public Expression makeStaticPartialApply(
//            XPathParser parser, int offset,
//            StructuredQName name, Expression[] args, Map<StructuredQName, Integer> keywordArgs) throws XPathException {
//        StaticContext env = parser.getStaticContext();
//        if (env.getConfiguration().isDisabledFunction(name)) {
//            grumble("Function " + name + " has been disabled", "XPST0018");
//        }
//        FunctionLibrary lib = env.getFunctionLibrary();
//        SymbolicName.F sn = new SymbolicName.F(name, args.length);
//        List<String> reasons = new ArrayList<>();
//        Expression call = lib.bind(sn,args, keywordArgs, env, reasons);
//        return call;
//        FunctionItem target = lib.getFunctionItem(sn, env);
//        if (target == null) {
//            // This will not happen in XQuery; instead, a dummy function will be created in the
//            // UnboundFunctionLibrary in case it's a forward reference to a function not yet compiled
//            List<String> reasons = new ArrayList<>();
//            return parser.reportMissingFunction(offset, name, args, reasons);
//        }
//        if (target instanceof IContextAccessorFunction) {
//            // For a context-dependent function, return a call on function-lookup(), which saves the context
//            SystemFunction lookup = XPath31FunctionSet.getInstance().makeFunction("function-lookup", 2);
//            lookup.setRetainedStaticContext(env.makeRetainedStaticContext());
//            return lookup.makeFunctionCall(Literal.makeLiteral(new QNameValue(name, BuiltInAtomicType.QNAME)),
//                                           Literal.makeLiteral(Int64Value.makeIntegerValue(args.length)));
//
//        }
//        Expression targetExp = makeNamedFunctionReference(name, target);
//        parser.setLocation(targetExp, offset);
//        return makePartialApplication(targetExp, args, keywordArgs);
//    }


    public void handleExternalFunctionDeclaration(XQueryParser p, XQueryFunction func) throws XPathException {
        parserExtension.needExtension(p, "External function declarations");
    }

    /*@NotNull*/
    public Expression reportMissingFunction(int offset, StructuredQName functionName, Expression[] arguments, List<String> reasons) throws XPathException {
        StringBuilder sb = new StringBuilder();
        sb.append("Cannot find a ").append(arguments.length).append(
                "-argument function named ").append(functionName.getEQName()).append("()");
        Configuration config = env.getConfiguration();
        for (String reason : reasons) {
            sb.append(". ").append(reason);
        }
        if (config.getBooleanProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS)) {
            boolean existsWithDifferentArity = false;
            for (int i = 0; i < arguments.length + 5; i++) {
                if (i != arguments.length) {
                    SymbolicName.F sn = new SymbolicName.F(functionName, i);
                    if (env.getFunctionLibrary().isAvailable(sn, env.getImportedSchema(), 31)) {
                        existsWithDifferentArity = true;
                        break;
                    }
                }
            }
            if (existsWithDifferentArity) {
                sb.append(". The namespace URI and local name are recognized, but the number of arguments is wrong");
            } else {
                String supplementary = getMissingFunctionExplanation(functionName, config);
                if (supplementary != null) {
                    sb.append(". ").append(supplementary);
                }
            }
        } else {
            sb.append(". External function calls have been disabled");
        }
        if (env.isInBackwardsCompatibleMode()) {
            // treat this as a dynamic error to be reported only if the function call is executed
            return new ErrorExpression(sb.toString(), "XTDE1425", false);
        } else {
            grumble(sb.toString(), "XPST0017", offset);
            return null;
        }
    }

    /**
     * Get a message containing suggestions as to why a requested function might not be available
     * @param functionName the name of the required function
     * @param config the Saxon configuration
     * @return a suggestion as to why the function was not found; or null if no suggestions can be offered.
     */

    public static String getMissingFunctionExplanation(StructuredQName functionName, Configuration config) {
        String actualURI = functionName.getNamespaceUri().toString();
        String similarNamespace = NamespaceConstant.findSimilarNamespace(actualURI);
        if (similarNamespace != null) {
            if (similarNamespace.equals(actualURI)) {
                switch (similarNamespace) {
                    case NamespaceConstant.FN:
                        return null;
                    case NamespaceConstant.SAXON:
                        if (config.getEditionCode().equals("HE")) {
                            return "Saxon extension functions are not available under Saxon-HE";
                        } else if (!config.isLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION)) {
                            return "Saxon extension functions require a Saxon-PE or Saxon-EE license";
                        }
                        break;
                    case NamespaceConstant.XSLT:
                        if (functionName.getLocalPart().equals("original")) {
                            return "Function name xsl:original is only available within an overriding function";
                        } else {
                            return "There are no functions defined in the XSLT namespace";
                        }
                }
            } else {
                return "Perhaps the intended namespace was '" + similarNamespace + "'";
            }
        } else if (actualURI.contains("java")) {
            return diagnoseCallToJavaMethod(config);
        } else if (actualURI.startsWith("clitype:")) {
            return diagnoseCallToCliMethod(config);
        }
        return null;
    }

    @CSharpReplaceBody(code = "return \"Reflexive calls to external Java methods are not available under SaxonCS\";")
    private static String diagnoseCallToJavaMethod(Configuration config) {
        if (config.getEditionCode().equals("HE")) {
            return "Reflexive calls to Java methods are not available under Saxon-HE";
        } else if (!config.isLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION)) {
            return "Reflexive calls to Java methods require a Saxon-PE or Saxon-EE license, and none was found";
        } else {
            return "For diagnostics on calls to Java methods, use the -TJ command line option " +
                    "or set the Configuration property FeatureKeys.TRACE_EXTERNAL_FUNCTIONS";
        }
    }

    @CSharpReplaceBody(code = "return \"Reflexive calls to external .NET methods are not available under SaxonCS\";")
    private static String diagnoseCallToCliMethod(Configuration config) {
        if (config.getEditionCode().equals("HE")) {
            return "Reflexive calls to external .NET methods are not available under Saxon-HE";
        } else if (!config.isLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION)) {
            return "Reflexive calls to external .NET methods require a Saxon-PE or Saxon-EE license, and none was found";
        } else {
            return "For diagnostics on calls to .NET methods, use the -TJ command line option " +
                    "or call processor.SetProperty(\"http://saxon.sf.net/feature/trace-external-functions\", \"true\")";
        }
    }

    /**
     * Interpret a function name, returning it as a resolved QName
     *
     * @param fname the lexical QName used as the function name; or an EQName presented
     *              by the tokenizer as a name in Clark notation
     * @return the Structured QName obtained by resolving any prefix in the function name
     * @throws XPathException if the supplied name is not a valid QName or if its prefix
     *                        is not in scope
     */

    /*@NotNull*/
    protected StructuredQName resolveFunctionName(String fname) throws XPathException {
        if (scanOnly) {
            return NamespaceUri.SAXON.qName("dummy");
        }
        if (isReservedFunctionName(fname, env.getXPathVersion())) {
            grumble("Function name " + fname + " is a reserved name");
        }
        StructuredQName functionName = null;
        try {
            functionName = qNameParser.parse(fname, env.getDefaultFunctionNamespace());
        } catch (XPathException e) {
            grumble(e.getMessage(), e.getErrorCodeQName());
            assert false;
        }

        if (functionName.hasURI(NamespaceUri.SCHEMA)) {
            ItemType t = Type.getBuiltInItemType(functionName.getNamespaceUri(), functionName.getLocalPart());
            if (t instanceof BuiltInAtomicType) {
                checkAllowedType(env, (BuiltInAtomicType) t);
            }
        }
        return functionName;
    }

    /**
     * Parse an argument to a function call. Separate method so it can
     * be overridden. With higher-order-function syntax in XPath 3.0/XQuery 3.0,
     * this returns null if the pseudo-argument "?" is found.
     *
     * @return the Expression used as the argument, or null if the argument is the place-holder "?"
     * @throws XPathException if the argument expression does not parse correctly
     */

    /*@Nullable*/
    public Expression parseFunctionArgument() throws XPathException {
        return parseExprSingle();
    }

    /**
     * Parse a named function reference
     * Syntax: QName # integer
     * The QName has already been read
     *
     * @return an ExternalObject representing the function item
     * @throws net.sf.saxon.trans.XPathException if a static error is encountered
     */

    /*@NotNull*/
    protected Expression parseNamedFunctionReference() throws XPathException {

        String fname = expectName();
        int offset = t.currentTokenStartOffset;

        StaticContext env = getStaticContext();

        nextToken();
        readToken(Token.HASH);

        if (!(t.currentToken instanceof Token.NumericLiteral)) {
            grumble("Expected number following '#'");
        }
        NumericValue number = ((Token.NumericLiteral) t.currentToken).getValue();
        if (!(number instanceof IntegerValue)) {
            grumble("Number following '#' must be an integer");
        }
        if (((Token.NumericLiteral) t.currentToken).getRadix() != 10) {
            grumble("Number following '#' must be a decimal integer");
        }
        if (number.compareTo(0) < 0 || number.compareTo(Integer.MAX_VALUE) > 0) {
            grumble("Number following '#' is out of range", "FOAR0002");
        }
        int arity = (int) number.longValue();
        nextToken();

        if (env instanceof QueryModule qm) {
            if (NameChecker.isValidNCName(fname) && isReservedFunctionName(fname, languageVersion)) {
                grumble("The unprefixed function name '" + fname + "' is reserved");
            }
            // In XQuery, defer binding till the end, to cope with forwards references
            return new DraftFunctionReference(fname, qm, arity);
        }

        StructuredQName functionName = null;

        if (NameChecker.isValidNCName(fname)) {
            // Function name is unprefixed
            if (isReservedFunctionName(fname, languageVersion)) {
                grumble("The unprefixed function name '" + fname + "' is reserved");
            }
            if (isAllowXPath40Syntax()) {
                // In 4.0, try first for a reference to a no-namespace function, then
                // for a reference to a function in the default function namespace
                SymbolicName.F symbolicName = new SymbolicName.F(
                        new StructuredQName("", NamespaceUri.NULL, fname), arity);
                if (env.getFunctionLibrary().getFunctionItem(symbolicName, env) != null) {
                    functionName = symbolicName.getComponentName();
                }
            }
        }

        if (functionName == null) {
            try {
                functionName = getQNameParser().parse(fname, env.getDefaultFunctionNamespace());
                if (functionName.getPrefix().isEmpty()) {
                    if (isReservedFunctionName(functionName.getLocalPart(), languageVersion)) {
                        grumble("The unprefixed function name '" + functionName.getLocalPart() + "' is reserved");
                    }
                }
            } catch (XPathException e) {
                grumble(e.getMessage(), e.getErrorCodeQName());
                assert functionName != null;
            }
        }

        if (env.getConfiguration().isDisabledFunction(functionName)) {
            grumble("Function " + functionName.getEQName() + " has been disabled in this Saxon Configuration", "XPST0017", offset);
        }

        try {
            FunctionLibrary lib = env.getFunctionLibrary();
            SymbolicName.F sn = new SymbolicName.F(functionName, arity);
            FunctionItem foundFunction = lib.getFunctionItem(sn, env);
            if (foundFunction == null) {
                grumble("Function " + functionName.getEQName() + "#" + arity + " not found", "XPST0017", offset);
            }

            if (foundFunction instanceof IContextAccessorFunction caf && caf.dependsOnContext()) {
                // For a context-dependent function, return a call on function-lookup(), which saves the context
                SystemFunction lookup = XPath31FunctionSet.getInstance().makeFunction("function-lookup", 2);
                lookup.setRetainedStaticContext(env.makeRetainedStaticContext());
                return lookup.makeFunctionCall(Literal.makeLiteral(new QNameValue(functionName, BuiltInAtomicType.QNAME)),
                                               Literal.makeLiteral(Int64Value.makeIntegerValue(arity)));


            }

            Expression ref = makeNamedFunctionReference(functionName, foundFunction);
            setLocation(ref, offset);
            return ref;
        } catch (XPathException e) {
            grumble(e.getMessage(), "XPST0017", offset);
            return null;
        }

    }

    public static Expression makeNamedFunctionReference(StructuredQName functionName, FunctionItem fcf) {
        if (fcf instanceof UserFunction && !functionName.hasURI(NamespaceUri.XSLT)) {
            // This case is treated specially because a UserFunctionReference in XSLT can be redirected
            // at link time to an overriding function. However, this doesn't apply to xsl:original
            return new UserFunctionReference((UserFunction) fcf);
        } else if (fcf instanceof UnresolvedXQueryFunctionItem) {
            return ((UnresolvedXQueryFunctionItem) fcf).getFunctionReference();
        } else {
            return new FunctionLiteral(fcf);
        }
    }


    /**
     * Parse the annotations that can appear in a variable or function declaration
     *
     * @return the annotations as a list
     * @throws XPathException in the event of a syntax error
     */

    protected AnnotationList parseAnnotationsList() throws XPathException {
        grumble("Function annotations are not allowed in XPath");
        return null;
    }

    protected Expression parseInlineFunction(AnnotationList annotations) throws XPathException {
        nextToken();
        readToken(Token.LPAREN);
        List<UserFunctionParameter> params = new ArrayList<>(8);

        int paramSlot = 0;
        while (t.currentToken != Token.RPAREN) {
            //     ParamList   ::=     Param ("," Param)*
            //     Param       ::=     "$" VarName  TypeDeclaration?

            StructuredQName argQName = readVariableName();
            SequenceType paramType = readOptionalAsClause(SequenceType.ANY_SEQUENCE);

            UserFunctionParameter arg = new UserFunctionParameter();
            arg.setRequiredType(paramType);
            arg.setVariableQName(argQName);
            arg.setSlotNumber(paramSlot++);
            params.add(arg);

            if (t.currentToken == Token.RPAREN) {
                break;
            } else if (t.currentToken == Token.COMMA) {
                nextToken();
            } else {
                grumble("Expected ',' or ')' after function argument, found '" +
                                t.currentToken + '\'');
            }
        }
        nextToken();
        SequenceType resultType = readOptionalAsClause(SequenceType.ANY_SEQUENCE);
        return parseInlineFunctionBody(annotations, params, resultType);

    }

    /**
     * Read a variable name ('$' QName). The current token should be the `$` sign; on return,
     * the input is positioned at the next token after the QName.
     * @return the name
     * @throws XPathException if no name was found
     */
    protected StructuredQName readVariableName() throws XPathException {
        readToken(Token.DOLLAR);
        String name = readName();
        return makeStructuredQName(name, NamespaceUri.NULL);
    }

    protected SequenceType readOptionalAsClause(SequenceType defaultType) throws XPathException {
        if (tryKeyword("as")) {
            return parseSequenceType();
        } else {
            return defaultType;
        }
    }

    protected Expression parseInlineFunctionBody(
            AnnotationList annotations,
            List<UserFunctionParameter> params,
            SequenceType resultType) throws XPathException {
        // the next token should be the "{" at the start of the function body

        int offset = t.currentTokenStartOffset;


        InlineFunctionDetails details = new InlineFunctionDetails();
        for (LocalBinding lb : getRangeVariables()) {
            details.outerVariables().push(lb);
        }
        inlineFunctionStack.push(details);
        //noinspection Convert2Diamond
        setRangeVariables(new IndexedStack<LocalBinding>());

        HashSet<StructuredQName> paramNameSet = new HashSet<>(8);
        for (UserFunctionParameter arg : params) {
            if (!scanOnly) {
                if (!paramNameSet.add(arg.getVariableQName())) {
                    grumble("Duplicate parameter name " + Err.wrap(arg.getVariableQName().getEQName(), Err.VARIABLE), "XQST0039");
                }
            }
            declareRangeVariable(arg);
        }

        readToken(Token.LCURLY);
        Expression body;
        if (t.currentToken == Token.RCURLY && languageVersion >= 31) {
            nextToken();
            body = Literal.makeEmptySequence();
        } else {
            body = parseExpression();
            readToken(Token.RCURLY);
        }
        ExpressionTool.setDeepRetainedStaticContext(body, getStaticContext().makeRetainedStaticContext());
        Expression result = makeInlineFunctionValue(this, annotations, details, params, resultType, body);

        setLocation(result, offset);

        for (UserFunctionParameter arg : params) {
            undeclareRangeVariable();
        }
        // restore the previous stack of range variables
        setRangeVariables(details.outerVariables());
        inlineFunctionStack.pop();
        return result;
    }

    public static Expression makeInlineFunctionValue(
            XPathParser p, AnnotationList annotations,
            InlineFunctionDetails details, List<UserFunctionParameter> params,
            SequenceType resultType, Expression body) {
        // Does this function access any outer variables?
        // If so, we create a UserFunction in which the outer variables are defined as extra parameters
        // in addition to the declared parameters, and then we return a call to partial-apply() that
        // sets these additional parameters to the values they have in the calling context.
        int arity = params.size();

        UserFunction uf = new UserFunction();
        uf.setFunctionName(new StructuredQName("anon", NamespaceUri.ANONYMOUS, "f_" + uf.hashCode()));
        uf.setPackageData(p.getStaticContext().getPackageData());
        uf.setBody(body);
        uf.setAnnotations(annotations);
        uf.setResultType(resultType);
        uf.incrementReferenceCount();

        if (uf.getPackageData() instanceof StylesheetPackage pack) {
            // Add the inline function as a private component to the package, so that it can have binding
            // slots allocated for any references to global variables or functions, and so that it will
            // be copied as a hidden component into any using packages
            Component comp = Component.makeComponent(uf, Visibility.PRIVATE, VisibilityProvenance.DEFAULTED, pack, pack);
            uf.setDeclaringComponent(comp);
        }

        Expression result;
        List<UserFunctionParameter> implicitParams = details.implicitParams;
        if (!implicitParams.isEmpty()) {
            int extraParams = implicitParams.size();
            int expandedArity = params.size() + extraParams;
            UserFunctionParameter[] paramArray = new UserFunctionParameter[expandedArity];
            for (int i = 0; i < params.size(); i++) {
                paramArray[i] = params.get(i);
            }
            int k = params.size();
            for (UserFunctionParameter implicitParam : implicitParams) {
                paramArray[k++] = implicitParam;
            }
            uf.setParameterDefinitions(paramArray);
            SlotManager stackFrame = p.getStaticContext().getConfiguration().makeSlotManager();
            for (int i = 0; i < expandedArity; i++) {
                int slot = stackFrame.allocateSlotNumber(paramArray[i].getVariableQName(), paramArray[i]);
                paramArray[i].setSlotNumber(slot);
            }

            ExpressionTool.allocateSlots(body, expandedArity, stackFrame);
            uf.setStackFrameMap(stackFrame);
            result = new UserFunctionReference(uf);

            Expression[] partialArgs = new Expression[expandedArity];
            for (int i = 0; i < arity; i++) {
                partialArgs[i] = new PlaceHolder(i);
            }
            for (int ip = 0; ip < implicitParams.size(); ip++) {
                UserFunctionParameter ufp = implicitParams.get(ip);
                LocalBinding binding = details.outerVariablesUsed.get(ip);
                VariableReference var;
                if (binding instanceof ParserExtension.TemporaryXSLTVariableBinding) {
                    var = new LocalVariableReference(binding);
                    ((ParserExtension.TemporaryXSLTVariableBinding) binding).declaration.registerReference(var);
                } else {
                    var = new LocalVariableReference(binding);
                }
                var.setStaticType(binding.getRequiredType(), null, 0);
                ufp.setRequiredType(binding.getRequiredType());
                partialArgs[ip + arity] = var;
            }
            result = new DynamicPartialApply(result, partialArgs);

        } else {

            // there are no implicit parameters
            UserFunctionParameter[] paramArray = params.toArray(new UserFunctionParameter[0]);
            uf.setParameterDefinitions(paramArray);

            SlotManager stackFrame = p.getStaticContext().getConfiguration().makeSlotManager();
            for (UserFunctionParameter param : paramArray) {
                stackFrame.allocateSlotNumber(param.getVariableQName(), param);
            }

            ExpressionTool.allocateSlots(body, params.size(), stackFrame);
            uf.setStackFrameMap(stackFrame);
            result = new UserFunctionReference(uf);
        }

        if (uf.getPackageData() instanceof StylesheetPackage) {
            // Note: inline functions in XSLT are registered as components; but not if they
            // are declared within a static expression, e.g. the initializer of a static
            // global variable
            ((StylesheetPackage) uf.getPackageData()).addComponent(uf.getDeclaringComponent());
        }
        return result;
    }


    /**
     * Locate a range variable with a given name. (By "range variable", we mean a
     * variable declared within the expression where it is used.)
     *
     * @param qName identifies the name of the range variable
     * @return null if not found (this means the variable is probably a
     * context variable); otherwise the relevant RangeVariable
     */

    /*@Nullable*/
    public LocalBinding findOuterRangeVariable(StructuredQName qName) {
        return findOuterRangeVariable(qName, inlineFunctionStack, getStaticContext());
    }


    /**
     * When a variable reference occurs within an inline function, it might be a reference to a variable declared
     * outside the inline function (which needs to become part of the closure). This code looks for such an outer
     * variable
     *
     * @param qName               the name of the variable
     * @param inlineFunctionStack the stack of inline functions that we are within
     * @param env                 the static context
     * @return a binding for the variable; this will typically be a binding to a newly added parameter
     * for the innermost function in which the variable reference appears. As a side effect, all the inline
     * functions between the declaration of the variable and its use will have this variable as an additional
     * parameter, each one bound to the corresponding parameter in the containing function.
     */

    public static LocalBinding findOuterRangeVariable(StructuredQName qName, IndexedStack<InlineFunctionDetails> inlineFunctionStack, StaticContext env) {
        // If we didn't find the variable, it might be defined in an outer scope.
        LocalBinding b2 = findOuterXPathRangeVariable(qName, inlineFunctionStack);
        if (b2 != null) {
            return b2;
        }
        // It's not an in-scope range variable. If this is a free-standing XPath expression, it might be
        // a parameter declared in the static context
        if (env instanceof IndependentContext && !inlineFunctionStack.isEmpty()) {
            b2 = findXPathParameter(qName, inlineFunctionStack, env);
        }
        // It's not an in-scope range variable. If we're in XSLT, it might be an XSLT-defined local variable
        ExpressionContext xsltContext = ExpressionContext.getXsltExpressionContext(env);
        if (xsltContext != null && !inlineFunctionStack.isEmpty()) {
            b2 = findOuterXSLTVariable(qName, inlineFunctionStack, xsltContext);
        }
        return b2;  // if null, it's not an in-scope range variable
    }

    /**
     * Look for an XPath/XQuery declaration of a variable used inside an inline function, but declared outside
     *
     * @param qName               the name of the variable
     * @param inlineFunctionStack the stack of inline functions that we are within
     * @return a binding to the innermost declaration of the variable
     */
    private static LocalBinding findOuterXPathRangeVariable(StructuredQName qName, IndexedStack<InlineFunctionDetails> inlineFunctionStack) {
        for (int s = inlineFunctionStack.size() - 1; s >= 0; s--) {
            InlineFunctionDetails details = inlineFunctionStack.get(s);
            IndexedStack<LocalBinding> outerVariables = details.outerVariables();
            for (int v = outerVariables.size() - 1; v >= 0; v--) {
                LocalBinding b2 = outerVariables.get(v);
                if (b2.getVariableQName().equals(qName)) {
                    for (int bs = s; bs <= inlineFunctionStack.size() - 1; bs++) {
                        details = inlineFunctionStack.get(bs);
                        boolean found = false;
                        for (int p = 0; p < details.outerVariablesUsed().size() - 1; p++) {
                            if (details.outerVariablesUsed().get(p) == b2) {
                                // the inner function already uses the outer variable
                                b2 = details.implicitParams.get(p);
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            // Need to add an implicit parameter to the inner function
                            details.outerVariablesUsed.add(b2);
                            UserFunctionParameter ufp = new UserFunctionParameter();
                            ufp.setVariableQName(qName);
                            ufp.setRequiredType(b2.getRequiredType());
                            details.implicitParams.add(ufp);
                            b2 = ufp;
                        }
                    }
                    return b2;
                }
            }
            LocalBinding b3 = bindParametersInNestedFunctions(qName, inlineFunctionStack, s);
            if (b3 != null) {
                return b3;
            }
        }
        return null;
    }

    /**
     * Look for a declaration of a variable used inside an inline function, but declared as part of
     * the static context of a free-standing XPath expression
     *
     * @param qName               the name of the variable
     * @param inlineFunctionStack the stack of inline functions that we are within
     * @return a binding to the innermost declaration of the variable
     */

    private static LocalBinding findXPathParameter(
            StructuredQName qName, IndexedStack<InlineFunctionDetails> inlineFunctionStack, StaticContext env) {
        if (env instanceof IndependentContext) {
            XPathVariable var = ((IndependentContext) env).getExternalVariable(qName);
            if (var != null) {
                InlineFunctionDetails details = inlineFunctionStack.get(0);
                LocalBinding innermostBinding;
                boolean found = false;
                for (int p = 0; p < details.outerVariablesUsed.size(); p++) {
                    if (details.outerVariablesUsed.get(p).getVariableQName().equals(qName)) {
                        // the inner function already uses the outer variable
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // Need to add an implicit parameter to the inner function
                    details.outerVariablesUsed.add(var);
                    UserFunctionParameter ufp = new UserFunctionParameter();
                    ufp.setVariableQName(qName);
                    ufp.setRequiredType(var.getRequiredType());
                    details.implicitParams.add(ufp);
                }
                // Now do the same for all inner inline functions, but this time binding to the
                // relevant parameter of the next containing function
                innermostBinding = bindParametersInNestedFunctions(qName, inlineFunctionStack, 0);
                return innermostBinding;
            }
        }
        return null;
    }


    /**
     * Look for an XSLT declaration of a variable used inside an inline function, but declared outside
     *
     * @param qName               the name of the variable
     * @param inlineFunctionStack the stack of inline functions that we are within
     * @return a binding to the innermost declaration of the variable
     */

    private static LocalBinding findOuterXSLTVariable(
            StructuredQName qName, IndexedStack<InlineFunctionDetails> inlineFunctionStack, ExpressionContext env) {
        StructuredQName attName = env.getAttributeName();
        SourceBinding decl = env.getStyleElement().bindLocalVariable(qName, attName);
        if (decl != null) {
            InlineFunctionDetails details = inlineFunctionStack.get(0);
            LocalBinding innermostBinding;
            boolean found = false;
            for (int p = 0; p < details.outerVariablesUsed.size(); p++) {
                if (details.outerVariablesUsed.get(p).getVariableQName().equals(qName)) {
                    // the inner function already uses the outer variable
                    found = true;
                    break;
                }
            }
            if (!found) {
                // Need to add an implicit parameter to the inner function
                details.outerVariablesUsed.add(new ParserExtension.TemporaryXSLTVariableBinding(decl));
                UserFunctionParameter ufp = new UserFunctionParameter();
                ufp.setVariableQName(qName);
                ufp.setRequiredType(decl.getInferredType(true));
                details.implicitParams.add(ufp);
            }
            // Now do the same for all inner inline functions, but this time binding to the
            // relevant parameter of the next containing function
            innermostBinding = bindParametersInNestedFunctions(qName, inlineFunctionStack, 0);
            return innermostBinding;
        }
        return null;
    }


    /**
     * Given that a variable is referenced within an inline function and is declared outside it,
     * add implicit parameters to all the functions that appear in the containment stack between
     * the declaration and the reference, in each case binding the value of the argument to the inner
     * function to the corresponding declared parameter in its containing function.
     *
     * @param qName               the name of the variable
     * @param inlineFunctionStack the stack of nested inline functions
     * @param start               the position in this stack of the function that contains the variable
     *                            declaration
     * @return a binding to the relevant (newly declared) parameter of the innermost function.
     */

    private static LocalBinding bindParametersInNestedFunctions(
            StructuredQName qName, IndexedStack<InlineFunctionDetails> inlineFunctionStack, int start) {
        InlineFunctionDetails details = inlineFunctionStack.get(start);
        List<UserFunctionParameter> params = details.implicitParams;
        for (UserFunctionParameter param : params) {
            if (param.getVariableQName().equals(qName)) {
                // The variable reference corresponds to a parameter of an outer inline function
                // We potentially need to add implicit parameters to any inner inline functions, and
                // bind the variable reference to the innermost of these implicit parameters
                LocalBinding b2 = param;
                for (int bs = start + 1; bs <= inlineFunctionStack.size() - 1; bs++) {
                    details = inlineFunctionStack.get(bs);
                    boolean found = false;
                    for (int p = 0; p < details.outerVariablesUsed.size() - 1; p++) {
                        if (details.outerVariablesUsed.get(p) == param) {
                            // the inner function already uses the outer variable
                            b2 = details.implicitParams.get(p);
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        // Need to add an implicit parameter to the inner function
                        details.outerVariablesUsed.add(param);
                        UserFunctionParameter ufp = new UserFunctionParameter();
                        ufp.setVariableQName(qName);
                        ufp.setRequiredType(param.getRequiredType());
                        details.implicitParams.add(ufp);
                        b2 = ufp;
                    }
                }
                if (b2 != null) {
                    return b2;
                }
            }
        }
        return null;
    }


    public Expression parseFocusFunction(AnnotationList annotations) throws XPathException {
        checkLanguageVersion40("inline focus functions");
        //Tokenizer t = getTokenizer();
        int offset = t.currentTokenStartOffset;

        InlineFunctionDetails details = new InlineFunctionDetails();
        for (LocalBinding lb : getRangeVariables()) {
            details.outerVariables().push(lb);
        }
        inlineFunctionStack.push(details);
        setRangeVariables(new IndexedStack<>());
        nextToken();
        List<UserFunctionParameter> params = new ArrayList<>(1);
        SequenceType resultType = SequenceType.ANY_SEQUENCE;

        StructuredQName argQName = new StructuredQName("saxon", NamespaceUri.SAXON, "dot");

        UserFunctionParameter arg = new UserFunctionParameter();
        arg.setRequiredType(SequenceType.ANY_SEQUENCE);
        arg.setVariableQName(argQName);
        arg.setSlotNumber(0);
        params.add(arg);

        Expression body;
        if (t.currentToken == Token.RCURLY) {
            nextToken();
            body = Literal.makeEmptySequence();
        } else {
            body = parseExpression();
            readToken(Token.RCURLY);
            body.setRetainedStaticContext(getStaticContext().makeRetainedStaticContext());

            LocalVariableReference ref = new LocalVariableReference(arg);
            body = new ContextValueSetter(ref, body);
        }
        Expression result = makeInlineFunctionValue(this, AnnotationList.EMPTY, details, params, resultType, body);

        setLocation(result, offset);
        // restore the previous stack of range variables
        setRangeVariables(details.outerVariables());
        inlineFunctionStack.pop();
        return result;
    }


    /* Must be in alphabetical order, since a binary search is used */

    private final static String[] reservedFunctionNames31 = new String[]{
            "array", "attribute", "comment", "document-node", "element", "empty-sequence", "function", "if", "item", "map",
            "namespace-node", "node", "processing-instruction", "schema-attribute",
            "schema-element", "switch", "text", "typeswitch"
    };

    private final static String[] reservedFunctionNames40 = new String[]{
            "array", "attribute", "comment", "document-node", "element", "enum", "fn", "function", "get", "if", "item",
            "jnode", "map", "namespace-node", "node", "processing-instruction", "schema-attribute",
            "schema-element", "switch", "text", "typeswitch"
    };

    /**
     * Check whether a function name is reserved in XPath 3.1 or 4.0 (when unprefixed)
     *
     * @param name the function name (the local-name as a string)
     * @param version set to 31 for XPath 3.1, 40 for XPath 4.0
     * @return true if the function name is reserved
     */

    public static boolean isReservedFunctionName(String name, int version) {
        int x = Arrays.binarySearch(version >= 40 ? reservedFunctionNames40 : reservedFunctionNames31, name);
        return x >= 0;
    }


    //////////////////////////////////////////////////////////////////////////////////
    // Routines for handling range variables
    //////////////////////////////////////////////////////////////////////////////////

    /**
     * Get the stack of in-scope range variables
     *
     * @return the stack of variables
     */

    public IndexedStack<LocalBinding> getRangeVariables() {
        return rangeVariables;
    }

    /**
     * Set a new stack of in-scope range variables
     *
     * @param variables the stack of variables
     */

    public void setRangeVariables(IndexedStack<LocalBinding> variables) {
        this.rangeVariables = variables;
    }

    /**
     * Declare a range variable (record its existence within the parser).
     * A range variable is a variable declared within an expression, as distinct
     * from a variable declared in the context.
     *
     * @param declaration the variable declaration to be added to the stack
     */

    public void declareRangeVariable(LocalBinding declaration) {
        rangeVariables.push(declaration);
    }

    /**
     * Note when the most recently declared range variable has gone out of scope
     */

    public void undeclareRangeVariable() {
        rangeVariables.pop();
    }

    /**
     * Locate a range variable with a given name. (By "range variable", we mean a
     * variable declared within the expression where it is used.)
     *
     * @param qName identifies the name of the range variable
     * @return null if not found (this means the variable is probably a
     * context variable); otherwise the relevant RangeVariable
     */

    /*@Nullable*/
    protected LocalBinding findRangeVariable(StructuredQName qName) {
        for (int v = rangeVariables.size() - 1; v >= 0; v--) {
            LocalBinding b = rangeVariables.get(v);
            if (b.getVariableQName().equals(qName)) {
                return b;
            }
        }
        return findOuterRangeVariable(qName);
    }

    /**
     * Set the range variable stack. Used when parsing a nested subexpression
     * inside an attribute constructor.
     *
     * @param stack the stack to be used for local variables declared within the expression
     */

    public void setRangeVariableStack(IndexedStack<LocalBinding> stack) {
        rangeVariables = stack;
    }

    /**
     * Make a NameCode, using the static context for namespace resolution
     *
     * @param qname      The name as written, in the form "[prefix:]localname"; alternatively,
     *                   a QName in Clark notation ({uri}local)
     * @param useDefault Defines the action when there is no prefix. If
     *                   true, use the default namespace URI for element names. If false,
     *                   use no namespace URI (as for attribute names).
     * @return the fingerprint, which can be used to identify this name in the
     * name pool
     * @throws XPathException if the name is invalid, or the prefix
     *                        undeclared
     */

    public final int makeFingerprint(/*@NotNull*/ String qname, boolean useDefault) throws XPathException {
        if (scanOnly) {
            return StandardNames.XML_SPACE;
        }
        try {
            NamespaceUri defaultNS = useDefault ? env.getDefaultElementNamespace() : NamespaceUri.NULL;
            StructuredQName sq = qNameParser.parse(qname, defaultNS);
            return env.getConfiguration().getNamePool().allocateFingerprint(sq.getNamespaceUri(), sq.getLocalPart());
        } catch (XPathException e) {
            grumble(e.getMessage(), e.getErrorCodeQName());
            return -1;
        }
    }

    /**
     * Make a NameCode, using the static context for namespace resolution.
     * This variant of the method does not call "grumble" to report any errors
     * to the ErrorListener, it only reports errors by throwing exceptions. This
     * allows the caller to control the message output.
     *
     * @param qname      The name as written, in the form "[prefix:]localname"
     * @param defaultUri Defines the URI to be returned if there is no prefix.
     * @return the structured QName
     * @throws XPathException if the name is invalid, or the prefix
     *                        undeclared or if the name is not a lexically valid QName
     */

    public final StructuredQName makeStructuredQNameSilently(/*@NotNull*/ String qname, NamespaceUri defaultUri)
            throws XPathException {
        if (scanOnly) {
            return NamespaceUri.SAXON.qName("dummy");
        }
        return qNameParser.parse(qname, defaultUri);
    }

    /**
     * Make a Structured QName, using the static context for namespace resolution
     *
     * @param qname      The name as written, in the form "[prefix:]localname"; alternatively, a QName in
     *                   Clark format ({uri}local)
     * @param defaultUri The URI to be used if the name is written as a localname with no prefix
     * @return the QName as an instance of StructuredQName
     * @throws XPathException if the name is invalid, or the prefix
     *                        undeclared
     */

    /*@NotNull*/
    public final StructuredQName makeStructuredQName(/*@NotNull*/ String qname, NamespaceUri defaultUri) throws XPathException {
        try {
            return makeStructuredQNameSilently(qname, defaultUri);
        } catch (XPathException err) {
            grumble(err.getMessage(), err.getErrorCodeQName());
            return NamespaceUri.NULL.qName("error");  // Not executed; here to keep the compiler happy
        }
    }

    /**
     * Make a FingerprintedQName, using the static context for namespace resolution
     *
     * @param qname      The name as written, in the form "[prefix:]localname"; alternatively, a QName in
     *                   Clark format ({uri}local)
     * @param useDefault Defines the action when there is no prefix. If
     *                   true, use the default namespace URI for element names. If false,
     *                   use no namespace URI (as for attribute names).
     * @return the fingerprinted QName
     * @throws XPathException if the name is invalid, or the prefix
     *                        undeclared
     */

    /*@NotNull*/
    public final NodeName makeNodeName(String qname, boolean useDefault) throws XPathException {
        StructuredQName sq = makeStructuredQNameSilently(qname,
                                                         useDefault ? env.getDefaultElementNamespace() : NamespaceUri.NULL);
        String prefix = sq.getPrefix();
        NamespaceUri uri = sq.getNamespaceUri();
        String local = sq.getLocalPart();
        if (uri.isEmpty()) {
            int fp = env.getConfiguration().getNamePool().allocateFingerprint(NamespaceUri.NULL, local);
            return new NoNamespaceName(local, fp);
        } else {
            int fp = env.getConfiguration().getNamePool().allocateFingerprint(uri, local);
            return new FingerprintedQName(prefix, uri, local, fp);
        }
    }


    /**
     * Make a QNameTest, using the static context for namespace resolution.
     *
     * @param nodeKind   the principal node kind of the axis (identified by a constant in
     *                   class Type)
     * @param qname      the lexical QName of the required node
     * @param useDefault true if the unprefixed element matching policy
     *                   should be used when the QName is unprefixed
     * @return a NameTest, representing a pattern that tests for a node of a
     * given node kind and a given name
     * @throws XPathException if the QName is invalid
     */

    /*@NotNull*/
    public QNameTest makeNameTest(int nodeKind, String qname, boolean useDefault)
            throws XPathException {
        NamePool pool = env.getConfiguration().getNamePool();
        NamespaceUri defaultNS = NamespaceUri.NULL;
        boolean isNCName = !qname.startsWith("Q{") && !qname.contains(":");
        if (useDefault && nodeKind == Type.ELEMENT && isNCName) {
            UnprefixedElementMatchingPolicy policy = env.getUnprefixedElementMatchingPolicy();
            switch (policy) {
                case DEFAULT_NAMESPACE:
                    defaultNS = env.getDefaultElementNamespace();
                    break;
                case DEFAULT_NAMESPACE_OR_NONE:
                    defaultNS = env.getDefaultElementNamespace();
                    StructuredQName q = makeStructuredQName(qname, defaultNS);
                    int fp1 = pool.allocateFingerprint(q.getNamespaceUri(), q.getLocalPart());
                    QNameTest test1 = new SpecificQNameTest(pool, fp1);
                    int fp2 = pool.allocateFingerprint(NamespaceUri.NULL, q.getLocalPart());
                    QNameTest test2 = new SpecificQNameTest(pool, fp2);
                    return new UnionQNameTest(test1, test2);
                case ANY_NAMESPACE:
                    if (!NameChecker.isValidNCName(StringTool.codePoints(qname))) {
                        grumble("Invalid name '" + qname + "'");
                    }
                    return new LocalQNameTest(qname);
            }
        }
        StructuredQName qName = makeStructuredQName(qname, defaultNS);
        return new SpecificQNameTest(qName, pool);
    }

    public QNameTest makeQNameTest(String qname)
            throws XPathException {
        NamePool pool = env.getConfiguration().getNamePool();
        StructuredQName q = makeStructuredQName(qname, NamespaceUri.NULL);
        assert q != null;
        int fp = pool.allocateFingerprint(q.getNamespaceUri(), q.getLocalPart());
        return new SpecificQNameTest(pool, fp);
    }

    /**
     * Make a NamespaceTest (name:*)
     *
     * @param prefix the namespace prefix, or a string in the form Q{uri}
     * @return the NamespaceTest, a pattern that matches all nodes in this
     * namespace
     * @throws XPathException if the namespace prefix is not declared
     */

    /*@NotNull*/
    public NamespaceQNameTest makeNamespaceQNameTest(String prefix)
            throws XPathException {
        if (scanOnly) {
            // return an arbitrary namespace if we're only doing a syntax check
            return new NamespaceQNameTest(NamespaceUri.SAXON);
        }
        if (prefix.startsWith("Q{")) {
            String uri = prefix.substring(2, prefix.length() - 1);
            return new NamespaceQNameTest(NamespaceUri.of(uri));
        }
        try {
            StructuredQName sq = qNameParser.parse(prefix + ":dummy", NamespaceUri.NULL);
            return new NamespaceQNameTest(sq.getNamespaceUri());
        } catch (XPathException err) {
            grumble(err.getMessage(), err.getErrorCodeQName());
            return null;
        }
    }

    /**
     * Make a LocalNameTest (*:name)
     *
     * @param localName the requred local name
     * @return a LocalNameTest, a pattern which matches all nodes of a given
     * local name, regardless of namespace
     * @throws XPathException if the local name is invalid
     */

    /*@NotNull*/
    public LocalQNameTest makeLocalQNameTest(String localName)
            throws XPathException {
        if (!NameChecker.isValidNCName(StringTool.codePoints(localName))) {
            grumble("Local name [" + localName + "] contains invalid characters");
        }
        return new LocalQNameTest(localName);
    }

    /**
     * Set location information on an expression. At present this consists of a simple
     * line number. Needed mainly for XQuery.
     *
     * @param exp the expression whose location information is to be set
     */

    protected void setLocation(/*@NotNull*/ Expression exp) {
        setLocation(exp, t.currentTokenStartOffset);
    }

    /**
     * Set location information on an expression. At present only the line number
     * is retained. Needed mainly for XQuery. This version of the method supplies an
     * explicit offset (character position within the expression or query), which the tokenizer
     * can convert to a line number and column number.
     *
     * @param exp    the expression whose location information is to be set
     * @param offset the character position within the expression (ignoring newlines)
     */

    public void setLocation(Expression exp, int offset) {
        if (exp != null) {
            if (exp.getLocation() == null || exp.getLocation() == Loc.NONE) {
                exp.setLocation(makeLocation(offset));
            }
        }
    }

    /**
     * Make a location object corresponding to a specified offset in the query
     * @param offset the offset (character position) in the query
     * @return an object containing location information
     */

    public Location makeLocation(int offset) {
        int line = t.getLineNumber(offset);
        int column = t.getColumnNumber(offset);
        return makeNestedLocation(env.getContainingLocation(), line, column, null);
    }

    /**
     * Set location information on a clause of a FLWOR expression. This version of the method supplies an
     * explicit offset (character position within the expression or query), which the tokenizer
     * can convert to a line number and column number.
     *
     * @param clause the clause whose location information is to be set
     * @param offset the character position within the expression (ignoring newlines)
     */

    public void setLocation(Clause clause, int offset) {
        int line = t.getLineNumber(offset);
        int column = t.getColumnNumber(offset);
        Location loc = makeNestedLocation(env.getContainingLocation(), line, column, null);
        clause.setLocation(loc);
        clause.setPackageData(env.getPackageData());
    }

    private Location mostRecentLocation = Loc.NONE;

    public Location makeLocation() {
        if (t.getLineNumber() == mostRecentLocation.getLineNumber() &&
                t.getColumnNumber() == mostRecentLocation.getColumnNumber() &&
                ((env.getSystemId() == null && mostRecentLocation.getSystemId() == null) ||
                         env.getSystemId().equals(mostRecentLocation.getSystemId()))) {
            return mostRecentLocation;
        } else {
            int line = t.getLineNumber();
            int column = t.getColumnNumber();
            mostRecentLocation = makeNestedLocation(env.getContainingLocation(), line, column, null);
            return mostRecentLocation;
        }
    }

    /**
     * Make a Location object relative to an existing location
     * @param containingLoc the containing location
     * @param line the line number relative to the containing location (zero-based)
     * @param column the column number relative to the containing location (zero-based)
     * @param nearbyText (maybe null) expression text around the point of the error
     * @return a suitable Location object
     */

    public Location makeNestedLocation(Location containingLoc, int line, int column, String nearbyText) {
        if (containingLoc instanceof Loc &&
                containingLoc.getLineNumber() <= 1 && containingLoc.getColumnNumber() == -1 &&
                nearbyText == null) {
            // No extra information available about the container
            return new Loc(env.getSystemId(), line + 1, column + 1);
        } else {
            return new NestedLocation(containingLoc, line, column, nearbyText);
        }
    }


    /**
     * If tracing, wrap an expression in a trace instruction
     *
     * <p>NB, this no longer happens. Instead of creating trace expressions in the course of parsing and buildint
     * the expression tree, trace code is now injected into the tree after the event, when parsing is complete.
     * See {@link ExpressionTool#injectCode(Expression, CodeInjector)}.</p>
     *
     * <p>However, the method has another effect, which is to set the retainedStaticContext in the node in the
     * expression tree.</p>
     *
     * @param exp         the expression to be wrapped
     * @param qName       the name of the construct (if applicable)
     * @return the expression that does the tracing
     */

    public Expression makeTracer(Expression exp,  /*@Nullable*/ StructuredQName qName) {
        exp.setRetainedStaticContextLocally(env.makeRetainedStaticContext());
        return exp;
//        if (codeInjector != null) {
//            return codeInjector.inject(exp);
//        } else {
//            return exp;
//        }
    }

    /**
     * Test whether the current token is a given keyword.
     *
     * @param s The string to be compared with the current token
     * @return true if they are the same
     */

    public boolean isKeyword(String s) {
        return isKeyword(t.currentToken, s);
    }

    public boolean tryKeyword(String s) throws XPathException {
        boolean result = isKeyword(t.currentToken, s);
        if (result) {
            nextToken();
        }
        return result;
    }

    public boolean isKeyword(Token tok, String s) {
        return tok instanceof Token.NameToken && ((Token.NameToken) tok).getValue().equals(s);
    }

    /**
     * Ask if the current token and next token constitute a given keyword pair
     */

    public boolean isKeywordPair(String s1, String s2) {
        return isKeyword(s1) && isKeyword(t.peekAhead(), s2);
    }

    /**
     * Ask if the current token and next token constitute a given keyword pair, and if
     * so, advance to the next token after the keyword pair
     */

    public boolean tryKeywordPair(String s1, String s2) throws XPathException {
        boolean result = isKeyword(s1) && isKeyword(t.peekAhead(), s2);
        if (result) {
            nextToken();
            nextToken();
        }
        return result;
    }


    /**
     * Set that we are parsing in "scan only" mode
     *
     * @param scanOnly true if parsing is to proceed in scan-only mode. In this mode
     *                 namespace bindings are not yet known, so no attempt is made to look up namespace
     *                 prefixes.
     */

    public void setScanOnly(boolean scanOnly) {
        this.scanOnly = scanOnly;
    }


    /**
     * Say whether an absent expression is permitted
     *
     * @param allowEmpty true if it is permitted for the expression to consist
     *                   only of whitespace and comments, in which case the result
     *                   of parsing will be an EmptySequence literal
     */

    public void setAllowAbsentExpression(boolean allowEmpty) {
        this.allowAbsentExpression = allowEmpty;
    }


    /**
     * Ask whether an absent expression is permitted
     *
     * @return true if it is permitted for the expression to consist
     * only of whitespace and comments, in which case the result
     * of parsing will be an EmptySequence literal
     */

    public boolean isAllowAbsentExpression() {
        return this.allowAbsentExpression;
    }

    /**
     * A nested location: for use with XPath expressions and queries nested within some
     * larger document. The location information comes in two parts: the location of the query
     * or expression within the containing document, and the location of an error within the
     * query or XPath expression.
     */

    public static class NestedLocation implements Location {

        private final Location containingLocation;
        private final int localLineNumber;
        private final int localColumnNumber;
        private final String nearbyText;

        /**
         * Create a NestedLocation
         *
         * @param containingLocation the location of the containing construct, typically an attribute or
         *                           text node in an XML document
         * @param localLineNumber    the line number within the containing construct, starting at zero
         * @param localColumnNumber  the column number within the containing construct, starting at zero
         * @param nearbyText         text appearing in the vicinity of the error location
         */

        public NestedLocation(Location containingLocation, int localLineNumber, int localColumnNumber, String nearbyText) {
            this.containingLocation = containingLocation.saveLocation();
            this.localLineNumber = localLineNumber;
            this.localColumnNumber = localColumnNumber;
            this.nearbyText = nearbyText;
        }

        /**
         * Get the location of the container. This is normally used for expressions nested within
         * an XML document, where the container location gives the location of the attribute or text
         * node holding the XPath expression as a whole
         *
         * @return the location of the containing expression or query
         */

        public Location getContainingLocation() {
            return containingLocation;
        }

        /**
         * Get the column number of the error within the expression or query
         *
         * @return the column number. This is generally maintained only during parsing,
         * so it will be returned as -1 (meaning not available) in the case of dynamic
         * errors. Column numbers start at 0. For expressions held within XML attributes,
         * the position is within the attribute after XML attribute-value normalization,
         * which replaces newlines by spaces and expands entity references.
         */

        @Override
        public int getColumnNumber() {
            return localColumnNumber;
        }

        /**
         * Get the system identifier of the expression's container. This will normally
         * be the URI of the document (or external entity) in which the expression appears.
         *
         * @return the system identifier of the expression's container, or null if not known
         */

        @Override
        public String getSystemId() {
            return containingLocation.getSystemId();
        }

        /**
         * Get the public identifier. This will normally be null, but is provided for
         * compatibility with SAX and JAXP interfaces
         *
         * @return the public identifier - usually null
         */

        @Override
        public String getPublicId() {
            return containingLocation.getPublicId();
        }

        /**
         * Get the local line number, that is the line number relative to the start of the
         * expression or query. For expressions held within XML attributes,
         * the position is within the attribute after XML attribute-value normalization,
         * which replaces newlines by spaces and expands entity references; the value
         * will therefore in many cases not be usable. Local line numbers start at 0.
         *
         * @return the local line number within the expression or query. Set to -1
         * if not known.
         */

        public int getLocalLineNumber() {
            return localLineNumber;
        }

        /**
         * Get the line number within the containing entity. This is the sum of the containing
         * location's line number, plus the local line number. Returns -1 if unknown.
         *
         * @return the line number within the containing entity, or -1 if unknown.
         */

        @Override
        public int getLineNumber() {
            return containingLocation.getLineNumber() + localLineNumber;
        }

        /**
         * Get text appearing near to the error (typically a syntax error) within the source
         * text of the expression or query.
         *
         * @return nearby text to the error. May be null.
         */

        public String getNearbyText() {
            return nearbyText;
        }

        /**
         * Save an immutable copy of the location information. This implementation does
         * nothing, because the object is already immutable
         *
         * @return immutable location information.
         */

        @Override
        public Location saveLocation() {
            return this;
        }
    }

}
