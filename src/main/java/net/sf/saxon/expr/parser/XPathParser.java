////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.flwor.Clause;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.functions.*;
import net.sf.saxon.functions.hof.FunctionLiteral;
import net.sf.saxon.functions.hof.PartialApply;
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
import net.sf.saxon.ma.map.*;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.*;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.query.XQueryFunction;
import net.sf.saxon.query.XQueryParser;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.s9api.UnprefixedElementMatchingPolicy;
import net.sf.saxon.str.StringTool;
import net.sf.saxon.style.ExpressionContext;
import net.sf.saxon.style.SourceBinding;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.sxpath.IndependentContext;
import net.sf.saxon.sxpath.XPathVariable;
import net.sf.saxon.trans.*;
import net.sf.saxon.transpile.CSharp;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.transpile.CSharpReplaceException;
import net.sf.saxon.transpile.CSharpSimpleEnum;
import net.sf.saxon.tree.util.IndexedStack;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;
import net.sf.saxon.z.*;

import java.math.BigInteger;
import java.util.*;

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

    protected boolean allowXPath30Syntax = false;
    protected boolean allowXPath30XSLTExtensions = false;
    protected boolean allowXPath31Syntax = false;
    protected boolean allowXPath40Syntax = false;
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

    protected int languageVersion = 20;
    protected int catchDepth = 0;

    private final static IntToIntHashMap operatorPrecedenceTable = new IntToIntHashMap(30);

    static {
        initializeOperatorPrecedenceTable();
    }

    public static class InlineFunctionDetails {
        public IndexedStack<LocalBinding> outerVariables;    // Local variables defined in the immediate outer scope (the father scope)
        public List<LocalBinding> outerVariablesUsed; // Local variables from the outer scope that are actually used
        public List<UserFunctionParameter> implicitParams; // Parameters corresponding (1:1) with the above
    }

    public interface Accelerator {

        /**
         * Attempt fast parsing of an expression, provided it is sufficiently simple.
         *
         * @param t          the tokenizer
         * @param env        the static context
         * @param expression the string containing expression to be parsed
         * @param start      start position within the input string
         * @param terminator either EOF or RCURLY, indicating how parsing should end
         * @return either the parsed expression, or null if it is erroneous or too
         * complex to parse.
         */

        Expression parse(Tokenizer t, StaticContext env, String expression, int start, int terminator);
    }

    /**
     * Create an expression parser
     */

    public XPathParser(StaticContext env) {
        this.env = env;
    }

    /**
     * Initialize the static operator precedence table
     */

    private static void initializeOperatorPrecedenceTable() {
        operatorPrecedenceTable.setDefaultValue(-1);
        IntToIntHashMap m = operatorPrecedenceTable;
        m.put(Token.QMARK_QMARK, 3);
        m.put(Token.BANG_BANG, 3);
        m.put(Token.OR, 4);
        m.put(Token.OR_ELSE, 4);
        m.put(Token.AND, 5);
        m.put(Token.AND_ALSO, 5);
        m.put(Token.FEQ, 6);
        m.put(Token.FNE, 6);
        m.put(Token.FLT, 6);
        m.put(Token.FGT, 6);
        m.put(Token.FLE, 6);
        m.put(Token.FGE, 6);
        m.put(Token.EQUALS, 6);
        m.put(Token.NE, 6);
        m.put(Token.LT, 6);
        m.put(Token.LE, 6);
        m.put(Token.GT, 6);
        m.put(Token.GE, 6);
        m.put(Token.IS, 6);
        m.put(Token.PRECEDES, 6);
        m.put(Token.FOLLOWS, 6);
        m.put(Token.CONCAT, 7);
        m.put(Token.TO, 9);
        m.put(Token.PLUS, 10);
        m.put(Token.MINUS, 10);
        m.put(Token.MULT, 11);
        m.put(Token.MATH_MULT, 11);
        m.put(Token.DIV, 11);
        m.put(Token.MATH_DIVIDE, 11);
        m.put(Token.IDIV, 11);
        m.put(Token.MOD, 11);
        m.put(Token.OTHERWISE, 12);
        m.put(Token.UNION, 13);
        m.put(Token.INTERSECT, 14);
        m.put(Token.EXCEPT, 14);
        m.put(Token.INSTANCE_OF, 15);
        m.put(Token.TREAT_AS, 16);
        m.put(Token.CASTABLE_AS, 17);
        m.put(Token.CAST_AS, 18);
        m.put(Token.FAT_ARROW, 19);
        m.put(Token.MAPPING_ARROW, 19);

                // remainder commented out because not used in precedence parsing (but perhaps they could be)
//            case Token.BANG:
//                return 20;
//            case Token.SLASH:
//                return 21;
//            case Token.SLASH_SLASH:
//                return 22;
//            case Token.QMARK:
//                return 23;

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
            t.next();
        } catch (XPathException e) {
            grumble(e.getMessage());
        }
    }

    /**
     * Expect a given token; fail if the current token is different. Note that this method
     * does not read any tokens.
     *
     * @param token the expected token
     * @throws XPathException if the current token is not the expected
     *                        token
     */

    public void expect(int token) throws XPathException {
        if (t.currentToken != token) {
            grumble("expected \"" + Token.tokens[token] +
                    "\", found " + currentTokenDisplay());
        }
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
        grumble(message, new StructuredQName("", NamespaceUri.ERR, errorCode), -1);
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

    protected void warning(String message, String errorCode)  {
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
            case EXTENDED_ITEM_TYPE:
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
        this.allowXPath30Syntax = languageVersion >= 30;
        this.allowXPath31Syntax = languageVersion >= 31;
        this.allowXPath40Syntax = languageVersion >= 40;

    }

    /**
     * Get the current language (XPath or XQuery)
     *
     * @return a string representation of the language being parsed, for use in error messages
     */

    protected String getLanguage() {
        switch (language) {
            case XPATH:
                return "XPath";
            case XSLT_PATTERN:
                return "XSLT Pattern";
            case SEQUENCE_TYPE:
                return "SequenceType";
            case XQUERY:
                return "XQuery";
            case EXTENDED_ITEM_TYPE:
                return "Extended ItemType";
            default:
                return "XPath";
        }
    }

    /**
     * Ask if XPath 3.1 is in use
     *
     * @return true if XPath 3.1 syntax (and therefore XQuery 3.1 syntax) is permitted
     */

    public boolean isAllowXPath31Syntax() {
        return allowXPath31Syntax;
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
        if (t.currentToken == Token.NAME) {
            return "name \"" + t.currentTokenValue + '\"';
        } else if (t.currentToken == Token.UNKNOWN) {
            return "(unknown token)";
        } else {
            return '\"' + Token.tokens[t.currentToken] + '\"';
        }
    }

    /**
     * Parse a string representing an expression. This will accept an XPath expression if called on an
     * ExpressionParser, or an XQuery expression if called on a QueryParser.
     *
     * @param expression the expression expressed as a String
     * @param start      offset within the string where parsing is to start
     * @param terminator token to treat as terminating the expression
     * @param env        the static context for the expression
     * @return an Expression object representing the result of parsing
     * @throws XPathException if the expression contains a syntax error
     */

    /*@NotNull*/
    public Expression parse(String expression, int start, int terminator, StaticContext env)
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
                terminator != Token.IMPLICIT_EOF &&
                (expression.length() - start < 30 || terminator == Token.RCURLY)) {
            // We need the tokenizer to be visible so that the caller can ask
            // about where the expression ended within the input string
            t = new Tokenizer();
            t.languageLevel = env.getXPathVersion();
            exp = accelerator.parse(t, env, expression, start, terminator);
        }

        if (exp == null) {

            qNameParser = new QNameParser(env.getNamespaceResolver())
                .withAcceptEQName(allowXPath30Syntax)
                .withErrorOnBadSyntax(language == ParsedLanguage.XSLT_PATTERN ? "XTSE0340" : "XPST0003")
                .withErrorOnUnresolvedPrefix("XPST0081");

            charChecker = env.getConfiguration().getValidCharacterChecker();
            t = new Tokenizer();
            t.languageLevel = env.getXPathVersion();
            allowXPath40Syntax =
                    t.allowSaxonExtensions =
                            env.getConfiguration().getBooleanProperty(Feature.ALLOW_SYNTAX_EXTENSIONS) || t.languageLevel == 40;
            offset = t.currentTokenStartOffset;
            customizeTokenizer(t);
            try {
                t.tokenize(expression, start, -1);
            } catch (XPathException err) {
                grumble(err.getMessage());
            }
            if (t.currentToken == terminator) {
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
            if (t.currentToken != terminator && terminator != Token.IMPLICIT_EOF) {
                if (t.currentToken == Token.EOF && terminator == Token.RCURLY) {
                    grumble("Missing curly brace after expression in value template", "XTSE0350");
                } else {
                    grumble("Unexpected token " + currentTokenDisplay() + " beyond end of expression");
                }
            }
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
                qNameParser = qNameParser.withAcceptEQName(true);
            }
        }
        language = ParsedLanguage.SEQUENCE_TYPE;
        t = new Tokenizer();
        t.languageLevel = languageVersion;
        allowXPath40Syntax =
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

    public ItemType parseExtendedItemType(String input, StaticContext env) throws XPathException {
        this.env = env;
        setLanguage(ParsedLanguage.EXTENDED_ITEM_TYPE, env.getXPathVersion());
        t = new Tokenizer();
        t.languageLevel = env.getXPathVersion();
        allowSaxonExtensions = t.allowSaxonExtensions = true;
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

    public SequenceType parseExtendedSequenceType(String input, /*@NotNull*/ StaticContext env) throws XPathException {
        this.env = env;
        language = ParsedLanguage.EXTENDED_ITEM_TYPE;
        t = new Tokenizer();
        t.languageLevel = languageVersion = 40;
        allowSaxonExtensions = t.allowSaxonExtensions = true;
        allowXPath30Syntax = allowXPath31Syntax = allowXPath40Syntax = true;
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
        // Short-circuit for a single-token expression
        int peek = t.peekAhead();
        if (peek == Token.EOF || peek == Token.COMMA || peek == Token.RPAR || peek == Token.RSQB) {
            switch (t.currentToken) {
                case Token.STRING_LITERAL:
                    return parseStringLiteral(true);
                case Token.NUMBER:
                    return parseNumericLiteral(true);
                case Token.HEX_INTEGER:
                    return parseHexLiteral(true);
                case Token.BINARY_INTEGER:
                    return parseBinaryLiteral(true);
                case Token.NAME:
                case Token.PREFIX:
                case Token.SUFFIX:
                case Token.STAR:
                    return parseBasicStep(true);
                case Token.DOT:
                    nextToken();
                    Expression cie = new ContextItemExpression();
                    setLocation(cie);
                    return cie;
                case Token.DOTDOT:
                    nextToken();
                    Expression pne = new AxisExpression(AxisInfo.PARENT, null);
                    setLocation(pne);
                    return pne;
                case Token.EOF:
                    // fall through
                default:
                    break;
            }
        }
        switch (t.currentToken) {
            case Token.EOF:
                grumble("Expected an expression, but reached the end of the input");
                return null;
            case Token.FOR:
            case Token.LET:
            case Token.FOR_MEMBER:
            case Token.FOR_SLIDING:
            case Token.FOR_TUMBLING:
                return parseFLWORExpression();
            case Token.SOME:
            case Token.EVERY:
                return parseQuantifiedExpression();
            case Token.IF:
                return parseIfExpression();
            case Token.SWITCH:
                return parseSwitchExpression();
            case Token.SWITCH_CASE:
                return parseSwitchExpression();
            case Token.TYPESWITCH:
                return parseTypeswitchExpression();
            case Token.VALIDATE:
            case Token.VALIDATE_STRICT:
            case Token.VALIDATE_LAX:
            case Token.VALIDATE_TYPE:
                return parseValidateExpression();
            case Token.PRAGMA:
                return parseExtensionExpression();  // XQuery only
            case Token.KEYWORD_CURLY:
                if (t.currentTokenValue.equals("try")) {
                    return parseTryCatchExpression();
                }
                // else drop through
                CSharp.emitCode("goto default;");
            default:
                Expression e1 = parseBinaryExpression(parseUnaryExpression(), 4);
                // Process ternary conditional
                if (t.currentToken == Token.QMARK_QMARK) {
                    if (!allowXPath40Syntax) {
                        grumble("Ternary conditionals (A ?? B !! C) require XPath 4.0 to be enabled (also, note this syntax will be withdrawn)");
                    }
                    return parseTernaryExpression(e1);
                }
                return e1;
        }
    }

    /**
     * Parse a ternary conditional expression
     */

    private Expression parseTernaryExpression(Expression condition) throws XPathException {
        nextToken();
        Expression e2 = parseExprSingle();
        expect(Token.BANG_BANG);
        nextToken();
        Expression e3 = parseExprSingle();
        return Choose.makeConditional(condition, e2, e3);
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
        while (getCurrentOperatorPrecedence() >= minPrecedence) {
            int offset = t.currentTokenStartOffset;
            int operator = t.currentToken;
            int prec = getCurrentOperatorPrecedence();
            switch (operator) {
                case Token.INSTANCE_OF:
                case Token.TREAT_AS:
                    nextToken();
                    SequenceType seq = parseSequenceType();
                    lhs = makeSequenceTypeExpression(lhs, operator, seq);
                    setLocation(lhs, offset);
                    if (getCurrentOperatorPrecedence() >= prec) {
                        grumble("Left operand of '" + Token.tokens[t.currentToken] + "' needs parentheses");
                    }
                    break;
                case Token.CAST_AS:
                case Token.CASTABLE_AS:
                    nextToken();
                    CastingTarget at;
                    if (allowXPath40Syntax && t.currentToken == Token.KEYWORD_LBRA && t.currentTokenValue.equals("union")) {
                        // Saxon 9.8 / XPath 4.0 proposed extension
                        at = (CastingTarget) parseItemType();
                    } else {
                        expect(Token.NAME);
                        if (scanOnly) {
                            at = BuiltInAtomicType.STRING;
                        } else {
                            StructuredQName sq = null;
                            try {
                                sq = qNameParser.parse(t.currentTokenValue, env.getDefaultElementNamespace());
                            } catch (XPathException e) {
                                grumble(e.getMessage(), e.getErrorCodeQName());
                                assert false;
                            }
                            ItemType alias = env.resolveTypeAlias(sq);
                            if (alias != null) {
                                if (alias instanceof CastingTarget) {
                                    at = (CastingTarget) alias;
                                } else {
                                    grumble("The type " + t.currentTokenValue + " cannot be used as the target of a cast");
                                    at = null;
                                }
                            } else {
                                at = getSimpleType(t.currentTokenValue);
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
                        grumble("Left operand of '" + Token.tokens[t.currentToken] + "' needs parentheses");
                    }
                    break;
                case Token.FAT_ARROW:
                    lhs = parseArrowPostfix(lhs);
                    break;
                case Token.MAPPING_ARROW:
                    checkLanguageVersion40();
                    lhs = parseMappingArrowPostfix(lhs);
                    break;
                default:
                    nextToken();
                    Expression rhs = parseUnaryExpression();
                    while (getCurrentOperatorPrecedence() > prec) {
                        rhs = parseBinaryExpression(rhs, getCurrentOperatorPrecedence());
                    }
                    if (getCurrentOperatorPrecedence() == prec && !allowMultipleOperators()) {
                        String tok = Token.tokens[t.currentToken];
                        String message = "Left operand of '" + Token.tokens[t.currentToken] + "' needs parentheses";
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

    private boolean allowMultipleOperators() {
        switch (t.currentToken) {
            case Token.FEQ:
            case Token.FNE:
            case Token.FLE:
            case Token.FLT:
            case Token.FGE:
            case Token.FGT:
            case Token.EQUALS:
            case Token.NE:
            case Token.LE:
            case Token.LT:
            case Token.GE:
            case Token.GT:
            case Token.IS:
            case Token.PRECEDES:
            case Token.FOLLOWS:
            case Token.TO:
                return false;
            default:
                return true;
        }
    }

    private int getCurrentOperatorPrecedence() {
        return operatorPrecedence(t.currentToken);
    }

    /**
     * Get the precedence associated with a given operator
     * @param operator the operator in question
     * @return a higher number for higher precedence (closer binding)
     */

    public static int operatorPrecedence(int operator) {
        return operatorPrecedenceTable.get(operator);
    }


        /*@NotNull*/
    private Expression makeBinaryExpression(Expression lhs, int operator, Expression rhs) throws XPathException {
        switch (operator) {
            case Token.OR:
                return new OrExpression(lhs, rhs);
            case Token.AND:
                return new AndExpression(lhs, rhs);
            case Token.FEQ:
            case Token.FNE:
            case Token.FLE:
            case Token.FLT:
            case Token.FGE:
            case Token.FGT:
                return new ValueComparison(lhs, operator, rhs);
            case Token.EQUALS:
            case Token.NE:
            case Token.LE:
            case Token.LT:
            case Token.GE:
            case Token.GT:
                return env.getConfiguration().getTypeChecker(env.isInBackwardsCompatibleMode()).makeGeneralComparison(lhs, operator, rhs);
            case Token.IS:
            case Token.PRECEDES:
            case Token.FOLLOWS:
                return new IdentityComparison(lhs, operator, rhs);
            case Token.TO:
                return new RangeExpression(lhs, rhs);
            case Token.CONCAT: {
                if (!allowXPath30Syntax) {
                    grumble("Concatenation operator ('||') requires XPath 3.0 to be enabled");
                }
                RetainedStaticContext rsc = new RetainedStaticContext(env);
                Configuration config = env.getConfiguration();
                BuiltInFunctionSet lib= config.getXPathFunctionSet(env.getXPathVersion());

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
            case Token.PLUS:
            case Token.MINUS:
            case Token.MULT:
            case Token.DIV:
            case Token.IDIV:
            case Token.MOD:
                return env.getConfiguration().getTypeChecker(env.isInBackwardsCompatibleMode()).makeArithmeticExpression(lhs, operator, rhs);
            case Token.MATH_MULT:
                return env.getConfiguration().getTypeChecker(env.isInBackwardsCompatibleMode()).makeArithmeticExpression(lhs, Token.MULT, rhs);
            case Token.MATH_DIVIDE:
                return env.getConfiguration().getTypeChecker(env.isInBackwardsCompatibleMode()).makeArithmeticExpression(lhs, Token.DIV, rhs);
            case Token.OTHERWISE:
                return makeOtherwiseExpression(lhs, rhs);
            case Token.UNION:
            case Token.INTERSECT:
            case Token.EXCEPT:
                return new VennExpression(lhs, operator, rhs);
            case Token.OR_ELSE: {
                // Compile ($x orElse $y) as (if ($x) then true() else boolean($y))
                RetainedStaticContext rsc = new RetainedStaticContext(env);
                rhs = SystemFunction.makeCall("boolean", rsc, rhs);
                return Choose.makeConditional(lhs, Literal.makeLiteral(BooleanValue.TRUE), rhs);
            }
            case Token.AND_ALSO: {
                // Compile ($x andAlso $y) as (if ($x) then boolean($y) else false())
                RetainedStaticContext rsc = new RetainedStaticContext(env);
                rhs = SystemFunction.makeCall("boolean", rsc, rhs);
                return Choose.makeConditional(lhs, rhs, Literal.makeLiteral(BooleanValue.FALSE));
            }
            default:
                throw new IllegalArgumentException(Token.tokens[operator]);
        }
    }

    /**
     * XPath 4.0 extension: A otherwise B, returns if (exists(A)) then A else B
     * @param lhs the A expression
     * @param rhs the B expression
     * @return a conditional expression with the correct semantics
     */
    private Expression makeOtherwiseExpression (Expression lhs, Expression rhs) throws XPathException {
        checkLanguageVersion40();
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

    private Expression makeSequenceTypeExpression(Expression lhs, int operator, /*@NotNull*/ SequenceType type) {
        switch (operator) {
            case Token.INSTANCE_OF:
                return new InstanceOfExpression(lhs, type);
            case Token.TREAT_AS:
                return TreatExpression.make(lhs, type);
            default:
                throw new IllegalArgumentException();
        }

    }

    private Expression makeSingleTypeExpression(Expression lhs, int operator, CastingTarget type, boolean allowEmpty)
            throws XPathException {
        if (type instanceof AtomicType && !(type == ErrorType.getInstance())) {
            switch (operator) {
                case Token.CASTABLE_AS:
                    CastableExpression castable = new CastableExpression(lhs, (AtomicType) type, allowEmpty);
                    if (lhs instanceof StringLiteral) {
                        castable.setOperandIsStringLiteral(true);
                    }
                    return castable;

                case Token.CAST_AS:
                    CastExpression cast = new CastExpression(lhs, (AtomicType) type, allowEmpty);
                    if (lhs instanceof StringLiteral) {
                        cast.setOperandIsStringLiteral(true);
                    }
                    return cast;

                default:
                    throw new IllegalArgumentException();
            }
        } else if (allowXPath30Syntax) {
            switch (operator) {
                case Token.CASTABLE_AS:
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
                case Token.CAST_AS:
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
        if (t.currentToken == Token.LET && !allowXPath30Syntax) {
            grumble("'let' is not permitted in XPath 2.0");
        }
        if (t.currentToken == Token.FOR_SLIDING || t.currentToken == Token.FOR_TUMBLING) {
            grumble("sliding/tumbling windows can only be used in XQuery");
        }
        if (t.currentToken == Token.FOR_MEMBER && !allowXPath40Syntax) {
            grumble("'for member' requires XPath 4.0 to be enabled");
        }
        if (t.currentToken == Token.LET) {
            return parseLetExpression();
        } else {
            return parseForExpression();
        }
    }

    private Expression parseForExpression() throws XPathException {
        int clauses = 0;
        int offset;
        int operator = t.currentToken;
        Assignation first = null;
        Assignation previous = null;
        do {
            boolean forMember = false;
            offset = t.currentTokenStartOffset;
            nextToken();
            if (isKeyword("member") && clauses > 0) {
                forMember = true;
                nextToken();
            }
            expect(Token.DOLLAR);
            nextToken();
            expect(Token.NAME);
            String var = t.currentTokenValue;

            // declare the range variable
            Assignation firstClause;
            Assignation lastClause;
            if (operator == Token.FOR) {
                firstClause = lastClause = new ForExpression();
                firstClause.setRequiredType(SequenceType.SINGLE_ITEM);
            } else if (operator == Token.FOR_MEMBER) {
                // "for member $m in $array" compiles to "for $temp in array:members($array) let $m := $temp?value"
                firstClause = new ForExpression();
                firstClause.setRequiredType(SequenceType.SINGLE_ITEM);
                firstClause.setVariableQName(
                        new StructuredQName("vv", NamespaceUri.SAXON_GENERATED_VARIABLE, "fm" + firstClause.hashCode()));
                declareRangeVariable(firstClause);
                lastClause = new LetExpression();
                lastClause.setRequiredType(SequenceType.ANY_SEQUENCE);
                LocalVariableReference tempRef = new LocalVariableReference(firstClause);
                LookupExpression lookup = new LookupExpression(tempRef, new StringLiteral("value"));
                lastClause.setSequence(lookup);
                firstClause.setAction(lastClause);
                forMember = true;
                clauses++;
            } else /*if (operator == Token.LET)*/ {
                firstClause = lastClause = new LetExpression();
                firstClause.setRequiredType(SequenceType.ANY_SEQUENCE);
            }

            clauses++;
            setLocation(firstClause, offset);
            setLocation(lastClause, offset);
            lastClause.setVariableQName(makeStructuredQName(var, NamespaceUri.NULL));
            nextToken();

            // process the "in" or ":=" clause
            expect(operator == Token.LET ? Token.ASSIGN : Token.IN);
            nextToken();
            Expression collection = parseExprSingle();
            if (forMember) {
                collection = ArrayFunctionSet.getInstance(40).makeFunction("members", 1).makeFunctionCall(collection);
            }
            firstClause.setSequence(collection);
            declareRangeVariable(lastClause);
            if (previous == null) {
                first = firstClause;
            } else {
                previous.setAction(firstClause);
            }
            previous = lastClause;
        } while (t.currentToken == Token.COMMA || (allowXPath40Syntax && t.currentToken == operator));

        // process the "return" expression (called the "action")
        expect(Token.RETURN);
        nextToken();
        previous.setAction(parseExprSingle());

        // undeclare all the range variables

        for (int i = 0; i < clauses; i++) {
            undeclareRangeVariable();
        }
        return makeTracer(first, first.getVariableQName());
    }

    private Expression parseLetExpression() throws XPathException {
        int clauses = 0;
        int offset;
        Assignation first = null;
        Assignation previous = null;
        do {
            offset = t.currentTokenStartOffset;
            nextToken();
            expect(Token.DOLLAR);
            nextToken();
            expect(Token.NAME);
            String var = t.currentTokenValue;

            // declare the range variable
            Assignation firstClause;
            Assignation lastClause;
            firstClause = lastClause = new LetExpression();
            firstClause.setRequiredType(SequenceType.ANY_SEQUENCE);

            clauses++;
            setLocation(firstClause, offset);
            setLocation(lastClause, offset);
            lastClause.setVariableQName(makeStructuredQName(var, NamespaceUri.NULL));
            nextToken();

            // process the  ":=" clause
            expect(Token.ASSIGN);
            nextToken();
            Expression collection = parseExprSingle();
            firstClause.setSequence(collection);
            declareRangeVariable(lastClause);
            if (previous == null) {
                first = firstClause;
            } else {
                previous.setAction(firstClause);
            }
            previous = lastClause;
        } while (t.currentToken == Token.COMMA || (allowXPath40Syntax && t.currentToken == Token.LET));

        // process the "return" expression (called the "action")
        expect(Token.RETURN);
        nextToken();
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
        int operator = t.currentToken;
        QuantifiedExpression first = null;
        QuantifiedExpression previous = null;
        do {
            int offset = t.currentTokenStartOffset;
            nextToken();
            expect(Token.DOLLAR);
            nextToken();
            expect(Token.NAME);
            String var = t.currentTokenValue;
            clauses++;

            // declare the range variable
            QuantifiedExpression v = new QuantifiedExpression();
            v.setRequiredType(SequenceType.SINGLE_ITEM);
            v.setOperator(operator);
            setLocation(v, offset);

            v.setVariableQName(makeStructuredQName(var, NamespaceUri.NULL));
            nextToken();

            if (t.currentToken == Token.AS && language == ParsedLanguage.XQUERY) {
                // We use this path for quantified expressions in XQuery, which permit an "as" clause
                nextToken();
                SequenceType type = parseSequenceType();
                if (type.getCardinality() != StaticProperty.EXACTLY_ONE) {
                    warning("Occurrence indicator on singleton range variable has no effect", SaxonErrorCode.SXWN9039);
                    type = SequenceType.makeSequenceType(type.getPrimaryType(), StaticProperty.EXACTLY_ONE);
                }
                v.setRequiredType(type);
            }

            // process the "in" clause
            expect(Token.IN);
            nextToken();
            v.setSequence(parseExprSingle());
            declareRangeVariable(v);
            if (previous != null) {
                previous.setAction(v);
            } else {
                first = v;
            }
            previous = v;

        } while (t.currentToken == Token.COMMA);

        // process the "return/satisfies" expression (called the "action")
        expect(Token.SATISFIES);
        nextToken();
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
        // left paren already read
        int ifoffset = t.currentTokenStartOffset;
        nextToken();
        Expression condition = parseExpression();
        expect(Token.RPAR);
        nextToken();
        int thenoffset = t.currentTokenStartOffset;
        if (t.currentToken == Token.LCURLY) {
            checkLanguageVersion40();
            return parseBracedActions(condition);
        }
        expect(Token.THEN);
        nextToken();
        Expression thenExp = makeTracer(parseExprSingle(), null);
        setLocation(thenExp, thenoffset);
        int elseoffset = t.currentTokenStartOffset;
        expect(Token.ELSE);
        nextToken();
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
        Expression thenExp = parseExpression();
        actions.add(thenExp);
        expect(Token.RCURLY);
        t.lookAhead();
        nextToken();
        while (t.currentToken == Token.ELSE) {
            nextToken();
            if (t.currentToken == Token.IF) {
//                nextToken();
//                expect(Token.LPAR);
                nextToken();
                condition = parseExpression();
                expect(Token.RPAR);
                nextToken();
                expect(Token.LCURLY);
                nextToken();
                thenExp = parseExpression();
                expect(Token.RCURLY);
                t.lookAhead();
                nextToken();
                conditions.add(condition);
                actions.add(thenExp);
            } else {
                expect(Token.LCURLY);
                nextToken();
                Expression elseExp = parseExpression();
                expect(Token.RCURLY);
                t.lookAhead();
                nextToken();
                conditions.add(Literal.makeLiteral(BooleanValue.TRUE));
                actions.add(elseExp);
                break;
            }
        }
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

        boolean builtInNamespace = uri.equals(NamespaceUri.SCHEMA);

        if (builtInNamespace) {
            ItemType t = Type.getBuiltInItemType(uri, local);
            if (t == null) {
                grumble("Unknown atomic type " + qname, "XPST0051");
                assert false;
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
        } else if (uri.equals(NamespaceUri.JAVA_TYPE)) {
            Class<?> theClass;
            try {
                String className = JavaExternalObjectType.localNameToClassName(local);
                theClass = config.getClass(className, false);
            } catch (XPathException err) {
                grumble("Unknown Java class " + local, "XPST0051");
                return AnyItemType.getInstance();
            }
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized(config) {
                return JavaExternalObjectType.of(theClass);
            }
        } else if (uri.equals(NamespaceUri.DOT_NET_TYPE)) {
            return Version.platform.getExternalObjectType(config, uri, local);
        } else {
            if (allowXPath40Syntax) {
                ItemType it = env.resolveTypeAlias(sq);
                if (it != null) {
                    return it;
                }
            }
            SchemaType st = config.getSchemaType(sq);
            if (st == null) {
                grumble("Unknown simple type " + qname, "XPST0051");
            } else if (st.isAtomicType()) {
                if (!env.isImportedSchema(uri)) {
                    grumble("Atomic type " + qname + " exists, but its schema definition has not been imported", "XPST0051");
                }
                return (AtomicType) st;
            } else if (st instanceof ItemType && ((ItemType) st).isPlainType() && allowXPath30Syntax) {
                if (!env.isImportedSchema(uri)) {
                    grumble("Type " + qname + " exists, but its schema definition has not been imported", "XPST0051");
                }
                return (ItemType) st;
            } else if (st.isComplexType()) {
                grumble("Type (" + qname + ") is a complex type", "XPST0051");
                return BuiltInAtomicType.ANY_ATOMIC;
            } else if (((SimpleType) st).isListType()) {
                grumble("Type (" + qname + ") is a list type", "XPST0051");
                return BuiltInAtomicType.ANY_ATOMIC;
            } else if (allowXPath30Syntax) {
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
                grumble("Unknown simple type " + qname, allowXPath30Syntax ? "XQST0052" : "XPST0051");
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

            SchemaType st = env.getConfiguration().getSchemaType(new StructuredQName("", uri, local));
            if (st == null) {
                if (allowXPath30Syntax) {
                    grumble("Unknown simple type " + qname, "XQST0052");
                } else {
                    grumble("Unknown simple type " + qname, "XPST0051");
                }
                return BuiltInAtomicType.ANY_ATOMIC;
            }
            if (allowXPath30Syntax) {
                // XPath 3.0
                if (!env.isImportedSchema(uri)) {
                    grumble("Simple type " + qname + " exists, but its target namespace has not been imported in the static context");
                }
                return (CastingTarget) st;

            } else {
                // XPath 2.0
                if (st.isAtomicType()) {
                    if (!env.isImportedSchema(uri)) {
                        grumble("Atomic type " + qname + " exists, but its target namespace has not been imported in the static context");
                    }
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
        boolean disallowIndicator = t.currentTokenValue.equals("empty-sequence");
        ItemType primaryType = parseItemType();
        if (disallowIndicator) {
            // No occurrence indicator allowed
            return SequenceType.makeSequenceType(primaryType, StaticProperty.EMPTY);
        }
        int occurrenceFlag = parseOccurrenceIndicator();
        return SequenceType.makeSequenceType(primaryType, occurrenceFlag);
    }

    public int parseOccurrenceIndicator() throws XPathException {
        int occurrenceFlag;
        switch (t.currentToken) {
            case Token.STAR:
            case Token.MULT:
                // "*" will be tokenized different ways depending on what precedes it
                occurrenceFlag = StaticProperty.ALLOWS_ZERO_OR_MORE;
                // Make the tokenizer ignore the occurrence indicator when classifying the next token
                t.currentToken = Token.RPAR;
                nextToken();
                break;
            case Token.PLUS:
                occurrenceFlag = StaticProperty.ALLOWS_ONE_OR_MORE;
                // Make the tokenizer ignore the occurrence indicator when classifying the next token
                t.currentToken = Token.RPAR;
                nextToken();
                break;
            case Token.QMARK:
                occurrenceFlag = StaticProperty.ALLOWS_ZERO_OR_ONE;
                // Make the tokenizer ignore the occurrence indicator when classifying the next token
                t.currentToken = Token.RPAR;
                nextToken();
                break;
            default:
                occurrenceFlag = StaticProperty.EXACTLY_ONE;
                break;
        }
        return occurrenceFlag;
    }

    /**
     * Parse an ItemType within a SequenceType
     *
     * @return the ItemType after parsing
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    public ItemType parseItemType() throws XPathException {
        ItemType extended = parserExtension.parseExtendedItemType(this);
        return extended == null ? parseSimpleItemType() : extended;
    }

    private ItemType parseSimpleItemType() throws XPathException {
        ItemType primaryType;
        if (t.currentToken == Token.LPAR) {
            primaryType = parseParenthesizedItemType();
            //nextToken();
        } else if (t.currentToken == Token.NAME) {
            primaryType = getPlainType(t.currentTokenValue);
            nextToken();
        } else if (t.currentToken == Token.KEYWORD_LBRA || t.currentToken == Token.FUNCTION) {
            // Which includes things such as "map" and "array"
            switch (t.currentTokenValue) {
                case "item":
                    nextToken();
                    expect(Token.RPAR);
                    nextToken();
                    primaryType = AnyItemType.getInstance();
                    break;
                case "function": {
                    checkLanguageVersion30();
                    AnnotationList annotations = AnnotationList.EMPTY;
                    primaryType = parseFunctionItemType(annotations);
                    break;
                }
                case "fn": {
                    checkLanguageVersion40();
                    AnnotationList annotations = AnnotationList.EMPTY;
                    primaryType = parseFunctionItemType(annotations);
                    break;
                }
                case "map":
                    primaryType = parseMapItemType();
                    break;
                case "array":
                    primaryType = parseArrayItemType();
                    break;
                case "record":
                case "tuple": // Retained as synonym for the time being
                    primaryType = parseRecordTest(this);
                    break;
                case "atomic":
                    // Allowed only in patterns, not in item types??
                    // TODO: not in spec, drop this
                    checkLanguageVersion40();
                    warning("The pattern syntax atomic(typename) is likely to be dropped from the 4.0 specification. Use type(typename) instead.", SaxonErrorCode.SXWN9000);
                    nextToken();
                    expect(Token.NAME);
                    StructuredQName typeName = getQNameParser().parse(
                            t.currentTokenValue, NamespaceUri.SCHEMA);
                    primaryType = getPlainType(typeName);
                    if (!(primaryType instanceof AtomicType)) {
                        grumble("Type " + t.currentTokenValue + " exists, but is not atomic");
                    }
                    nextToken();
                    expect(Token.RPAR);
                    nextToken();
                    break;
                case "union":
                    primaryType = parseUnionType();
                    break;
                case "enum":
                    primaryType = parseEnumType();
                    break;
                case "empty-sequence":
                    nextToken();
                    expect(Token.RPAR);
                    nextToken();
                    primaryType = ErrorType.getInstance();
                    break;
                case "type":
                    checkLanguageVersion40();
                    nextToken();
                    if (t.currentToken == Token.NAME) {
                        StructuredQName qName = getQNameParser().parse(t.currentTokenValue, NamespaceUri.NULL);
                        ItemType realType = getStaticContext().resolveTypeAlias(qName);
                        if (realType != null) {
                            nextToken();
                            expect(Token.RPAR);
                            nextToken();
                            return realType;
                        }
                    }
                    if (language != ParsedLanguage.XSLT_PATTERN) {
                        grumble("In an XPath expression (as distinct from an XSLT pattern), type(N) must refer to a named item type");
                    }
                    ItemType it = parseItemType();
                    expect(Token.RPAR);
                    nextToken();
                    return it;

                default:
                    primaryType = parseKindTest();
                    break;
            }
        } else if (t.currentToken == Token.PERCENT) {
            AnnotationList annotations = parseAnnotationsList();
            if (t.currentTokenValue.equals("function")) {
                primaryType = parseFunctionItemType(annotations);
            } else {
                grumble("Expected 'function' to follow annotation assertions, found " + Token.tokens[t.currentToken]);
                return null;
            }
        } else if (language == ParsedLanguage.EXTENDED_ITEM_TYPE && t.currentToken == Token.PREFIX) {
            String tokv = t.currentTokenValue;
            nextToken();
            return makeNamespaceTest(Type.ELEMENT, tokv);
        } else if (language == ParsedLanguage.EXTENDED_ITEM_TYPE && t.currentToken == Token.SUFFIX) {
            nextToken();
            expect(Token.NAME);
            String tokv = t.currentTokenValue;
            nextToken();
            return makeLocalNameTest(Type.ELEMENT, tokv);
        } else if (language == ParsedLanguage.EXTENDED_ITEM_TYPE && t.currentToken == Token.AT) {
            nextToken();
            if (t.currentToken == Token.PREFIX) {
                String tokv = t.currentTokenValue;
                nextToken();
                return makeNamespaceTest(Type.ATTRIBUTE, tokv);
            } else if (t.currentToken == Token.SUFFIX) {
                nextToken();
                expect(Token.NAME);
                String tokv = t.currentTokenValue;
                nextToken();
                return makeLocalNameTest(Type.ATTRIBUTE, tokv);
            } else {
                grumble("Expected NodeTest after '@'");
                return BuiltInAtomicType.ANY_ATOMIC;
            }
        } else {
            grumble("Expected type name in SequenceType, found " + Token.tokens[t.currentToken]);
            return BuiltInAtomicType.ANY_ATOMIC;
        }
        return primaryType;
    }

    /**
     * Parse a record type (Saxon 9.8 / XPath 4.0 extension).
     * Syntax: "record" "(" (name (":" sequenceType)?) ("," (name (":" sequenceType)?))* ")"
     * The keyword "tuple" is accepted as a synonym, for compatibility
     */

    private ItemType parseRecordTest(XPathParser p) throws XPathException {
        // The initial "record(" has been read
        checkLanguageVersion40();
        Tokenizer t = p.getTokenizer();
        p.nextToken();
        List<String> fieldNames = new ArrayList<>(6);
        List<String> optionalFieldNames = new ArrayList<>(6);
        List<SequenceType> fieldTypes = new ArrayList<>(6);
        boolean extensible = false;
        RecordTest recordTest = new RecordTest();
        while (true) {
            String name;
            if (t.currentToken == Token.STAR || t.currentToken == Token.MULT) {
                extensible = true;
                p.nextToken();
                p.expect(Token.RPAR);
                break;
            }
            if (t.currentToken == Token.NAME) {
                name = t.currentTokenValue;
                if (!NameChecker.isValidNCName(name)) {
                    p.grumble(Err.wrap(name) + " is not a valid NCName");
                }
            } else if (t.currentToken == Token.STRING_LITERAL) {
                name = t.currentTokenValue;
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
            if (t.currentToken == Token.AS) {
                p.nextToken();
                if (t.currentToken == Token.DOTDOT) {
                    // self-reference
                    p.nextToken();
                    int occ = parseOccurrenceIndicator();
                    arg = SequenceType.makeSequenceType(new SelfReferenceRecordTest(recordTest), occ);
                    if (!Cardinality.allowsZero(occ) && !optionalFieldNames.contains(name)) {
                        throw new XPathException("A self-referencing field in a record type must be emptiable or optional", "XPST0140");
                    }
                } else {
                    arg = p.parseSequenceType();
                }
            }
            fieldTypes.add(arg);
            if (t.currentToken == Token.RPAR) {
                break;
            } else if (t.currentToken == Token.COMMA) {
                p.nextToken();
            } else {
                p.grumble("Expected ',' or ')' after field in RecordTest, found '" +
                                  Token.tokens[t.currentToken] + '\'');
            }
        }
        p.nextToken();
        recordTest.setDetails(fieldNames, fieldTypes, optionalFieldNames, extensible);
        return recordTest;
    }

    /**
     * Parse a union type (Saxon 9.8 extension).
     * Syntax: "union" "(" qname ("," qname)* ")"
     * @return the item type
     * @throws XPathException if a syntax error is found
     */

    public ItemType parseUnionType() throws XPathException {
        // The initial "union(" has been read
        checkLanguageVersion40();
        nextToken();
        List<AtomicType> memberTypes = new ArrayList<>(6);

        while (true) {
            if (t.currentToken == Token.KEYWORD_LBRA && t.currentTokenValue.equals("enum")) {
                EnumerationType type = parseEnumType();
                memberTypes.add(type);
            } else {
                expect(Token.NAME);
                if (scanOnly) {
                    memberTypes.add(BuiltInAtomicType.STRING);
                } else {
                    StructuredQName member = getQNameParser().parse(
                            t.currentTokenValue, getStaticContext().getDefaultElementNamespace());
                    ItemType type = getPlainType(member);
                    if (type instanceof AtomicType) {
                        memberTypes.add((AtomicType) type);
                    } else if (type instanceof PlainType) {
                        for (PlainType pt : ((UnionType) type).getPlainMemberTypes()) {
                            if (pt instanceof AtomicType) {
                                memberTypes.add((AtomicType) pt);
                            } else {
                                grumble("Union type " + type + " has a non-atomic member type " + pt);
                            }
                        }
                    } else {
                        grumble("Type " + t.currentTokenValue + " exists, but is not atomic");
                    }
                }
                nextToken();
            }
            if (t.currentToken == Token.RPAR) {
                break;
            } else if (t.currentToken == Token.COMMA) {
                nextToken();
            } else {
                grumble("Expected ',' or ')' after member name in union type, found '" +
                                  Token.tokens[t.currentToken] + '\'');
            }
        }
        nextToken();
        return new LocalUnionType(memberTypes);

    }

    /**
     * Parse an enum type (XPath 4.0 proposal).
     * Syntax: "enum" "(" StringLiteral ("," StringLiteral)* ")"
     * @return the item type
     * @throws XPathException if a syntax error is found
     */

    public EnumerationType parseEnumType() throws XPathException {
        // The initial "enum(" has been read
        checkLanguageVersion40();
        nextToken();
        Set<String> values = new HashSet<>(6);

        while (true) {
            expect(Token.STRING_LITERAL);
            values.add(t.currentTokenValue);
            nextToken();
            if (t.currentToken == Token.RPAR) {
                break;
            } else if (t.currentToken == Token.COMMA) {
                nextToken();
            } else {
                grumble("Expected ',' or ')' after string literal in enum type, found '" +
                                  Token.tokens[t.currentToken] + '\'');
            }
        }
        nextToken();
        return new EnumerationType(values);

    }

    /**
     * Parse the item type used for function items (for higher order functions)
     * Syntax (changed by WG decision on 2009-09-22):
     * function '(' '*' ') |
     * function '(' (SeqType (',' SeqType)*)? ')' 'as' SeqType
     * The "function(" has already been read
     *
     * @param annotations the list of annotation assertions for this function item type
     * @return the ItemType after parsing
     * @throws XPathException if a static error is found
     */

    /*@NotNull*/
    protected ItemType parseFunctionItemType(AnnotationList annotations) throws XPathException {
        nextToken();
        List<SequenceType> argTypes = new ArrayList<>(3);
        SequenceType resultType;

        if (t.currentToken == Token.STAR || t.currentToken == Token.MULT) {
            // Allow both to be safe
            nextToken();
            expect(Token.RPAR);
            nextToken();
            if (annotations.isEmpty()) {
                return AnyFunctionType.getInstance();
            } else {
                return new AnyFunctionTypeWithAssertions(annotations, getStaticContext().getConfiguration());
            }
        } else {
            while (t.currentToken != Token.RPAR) {
                SequenceType arg = parseSequenceType();
                argTypes.add(arg);
                if (t.currentToken == Token.RPAR) {
                    break;
                } else if (t.currentToken == Token.COMMA) {
                    nextToken();
                } else {
                    grumble("Expected ',' or ')' after function argument type, found '" +
                                      Token.tokens[t.currentToken] + '\'');
                }
            }
            nextToken();
            if (t.currentToken == Token.AS) {
                nextToken();
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
                // in the new syntax adopted on 2009-09-22, this case is an error
                // return AnyFunctionType.getInstance();
            }
        }
    }


    /**
     * Parse the item type used for maps (XSLT extension to XPath 3.0)
     * Syntax:
     * map '(' '*' ') |
     * map '(' ItemType ',' SeqType ')' 'as' SeqType
     * The "map(" has already been read
     * @return the item type of the map
     * @throws XPathException if a parsing error occurs or if the map syntax
     *                        is not available
     */

    /*@NotNull*/
    protected ItemType parseMapItemType() throws XPathException {
        checkMapExtensions();
        Tokenizer t = getTokenizer();
        nextToken();
        if (t.currentToken == Token.STAR || t.currentToken == Token.MULT) {
            // Allow both to be safe
            nextToken();
            expect(Token.RPAR);
            nextToken();
            return MapType.ANY_MAP_TYPE;
        } else {
            ItemType keyType = parseItemType();
            expect(Token.COMMA);
            nextToken();
            SequenceType valueType = parseSequenceType();
            expect(Token.RPAR);
            nextToken();
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
     * The "array(" has already been read
     * @return the item type of the array
     * @throws XPathException if a parsing error occurs or if the array syntax
     *                        is not available
     */

    /*@NotNull*/
    protected ItemType parseArrayItemType() throws XPathException {
        checkLanguageVersion31();
        Tokenizer t = getTokenizer();
        nextToken();
        if (t.currentToken == Token.STAR || t.currentToken == Token.MULT) {
            // Allow both to be safe
            nextToken();
            expect(Token.RPAR);
            nextToken();
            return ArrayItemType.ANY_ARRAY_TYPE;
        } else {
            SequenceType memberType = parseSequenceType();
            expect(Token.RPAR);
            nextToken();
            return new ArrayItemType(memberType);
        }
    }

    /**
     * Parse a parenthesized item type (allowed in XQuery 3.0 and XPath 3.0 only)
     *
     * @return the item type
     * @throws XPathException in the event of a syntax error (or if 3.0 is not enabled)
     */

    /*@NotNull*/
    private ItemType parseParenthesizedItemType() throws XPathException {
        if (!allowXPath30Syntax) {
            grumble("Parenthesized item types require 3.0 to be enabled");
        }
        nextToken();
        ItemType primaryType = parseItemType();
        while (primaryType instanceof NodeTest && language == ParsedLanguage.EXTENDED_ITEM_TYPE
                && t.currentToken != Token.RPAR) {
            switch (t.currentToken) {
                case Token.UNION:
                case Token.EXCEPT:
                case Token.INTERSECT:
                    int op = t.currentToken;
                    nextToken();
                    primaryType = new CombinedNodeTest((NodeTest) primaryType, op, (NodeTest) parseItemType());
                    break;
            }
        }
        expect(Token.RPAR);
        nextToken();
        return primaryType;
    }


    /**
     * Parse a UnaryExpr:<br>
     * ('+'|'-')* ValueExpr
     * parsed as ('+'|'-')? UnaryExpr
     *
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    /*@NotNull*/
    private Expression parseUnaryExpression() throws XPathException {
        Expression exp;
        switch (t.currentToken) {
            case Token.MINUS: {
                nextToken();
                Expression operand = parseUnaryExpression();
                exp = makeUnaryExpression(Token.NEGATE, operand);
                break;
            }
            case Token.PLUS: {
                nextToken();
                // Unary plus: can't ignore it completely, it might be a type error, or it might
                // force conversion to a number which would affect operations such as "=".
                Expression operand = parseUnaryExpression();
                exp = makeUnaryExpression(Token.PLUS, operand);
                break;
            }
            case Token.VALIDATE:
            case Token.VALIDATE_STRICT:
            case Token.VALIDATE_LAX:
            case Token.VALIDATE_TYPE:
                exp = parseValidateExpression();
                break;
            case Token.PRAGMA:
                exp = parseExtensionExpression();
                break;

            case Token.KEYWORD_CURLY:
                if (t.currentTokenValue.equals("validate")) {
                    exp = parseValidateExpression();
                    break;
                }
                CSharp.emitCode("goto default;");
                // else fall through
            default:
                exp = parseSimpleMappingExpression();
                break;
        }
        setLocation(exp);
        return exp;
    }

    private Expression makeUnaryExpression(int operator, Expression operand) {
        if (Literal.isAtomic(operand)) {
            // very early evaluation of expressions like "-1", so they are treated as numeric literals
            AtomicValue val = (AtomicValue) ((Literal) operand).getGroundedValue();
            if (val instanceof NumericValue) {
                if (env.isInBackwardsCompatibleMode()) {
                    val = new DoubleValue(((NumericValue) val).getDoubleValue());
                }
                AtomicValue value = operator == Token.NEGATE ? ((NumericValue) val).negate() : (NumericValue) val;
                return Literal.makeLiteral(value);
            }
        }
        return env.getConfiguration().getTypeChecker(env.isInBackwardsCompatibleMode()).makeArithmeticExpression(
                Literal.makeLiteral(Int64Value.ZERO), operator, operand);
    }

    /**
     * Test whether the current token is one that can start a RelativePathExpression
     *
     * @return the resulting subexpression
     */

    protected boolean atStartOfRelativePath() {
        switch (t.currentToken) {
            case Token.AXIS:
            case Token.AT:
            case Token.NAME:
            case Token.PREFIX:
            case Token.SUFFIX:
            case Token.STAR:
            case Token.KEYWORD_LBRA:
            case Token.DOT:
            case Token.DOTDOT:
            case Token.FUNCTION:
            case Token.STRING_LITERAL:
            case Token.NUMBER:
            case Token.HEX_INTEGER:
            case Token.BINARY_INTEGER:
            case Token.LPAR:
            case Token.DOLLAR:
            case Token.PRAGMA:
            case Token.ELEMENT_QNAME:
            case Token.ATTRIBUTE_QNAME:
            case Token.PI_QNAME:
            case Token.NAMESPACE_QNAME:
            case Token.NAMED_FUNCTION_REF:
                return true;
            case Token.KEYWORD_CURLY:
                return t.currentTokenValue.equals("ordered") || t.currentTokenValue.equals("unordered");
            default:
                return false;
        }
    }

    /**
     * Test whether the current token is one that is disallowed after a "leading lone slash".
     * These composite tokens have been parsed as operators, but are not allowed after "/" under the
     * rules of erratum E24
     *
     * @return the resulting subexpression
     */

    protected boolean disallowedAtStartOfRelativePath() {
        switch (t.currentToken) {
            // Although these "double keyword" operators can readily be recognized as operators,
            // they are not permitted after leading "/" under the rules of erratum XQ.E24
            case Token.CAST_AS:
            case Token.CASTABLE_AS:
            case Token.INSTANCE_OF:
            case Token.TREAT_AS:
                return true;
            default:
                return false;
        }
    }

    /**
     * Parse a PathExpresssion. This includes "true" path expressions such as A/B/C, and also
     * constructs that may start a path expression such as a variable reference $name or a
     * parenthesed expression (A|B). Numeric and string literals also come under this heading.
     *
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    /*@NotNull*/
    protected Expression parsePathExpression() throws XPathException {
        int offset = t.currentTokenStartOffset;
        switch (t.currentToken) {
            case Token.SLASH:
                nextToken();
                final RootExpression start = new RootExpression();
                setLocation(start);
                if (disallowedAtStartOfRelativePath()) {
                    grumble("Operator '" + Token.tokens[t.currentToken] + "' is not allowed after '/'");
                }
                if (atStartOfRelativePath()) {
                    final Expression path = parseRemainingPath(start);
                    setLocation(path, offset);
                    return path;
                } else {
                    return start;
                }

            case Token.SLASH_SLASH:
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

            default:
                if (t.currentToken == Token.NAME &&
                        (t.currentTokenValue.equals("true") || t.currentTokenValue.equals("false"))) {
                    warning("The expression is looking for a child element named '" + t.currentTokenValue +
                            "' - perhaps " + t.currentTokenValue + "() was intended? To avoid this warning, use child::" +
                            t.currentTokenValue + " or ./" + t.currentTokenValue + ".", SaxonErrorCode.SXWN9040);
                }
                if (t.currentToken == Token.NAME && t.getBinaryOp(t.currentTokenValue) != Token.UNKNOWN &&
                        language != ParsedLanguage.XSLT_PATTERN && (offset > 0 || t.peekAhead() != Token.EOF)) {
                    String s = t.currentTokenValue;
                    warning("The keyword '" + s + "' in this context means 'child::" + s +
                                    "'. If this was intended, use 'child::" + s + "' or './" + s + "' to avoid this warning.", SaxonErrorCode.SXWN9040);
                }
                return parseRelativePath();
        }

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
            if (!allowXPath30Syntax) {
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
            int op = t.currentToken;
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
        int op = Token.SLASH;
        while (true) {
            Expression next = parseStepExpression(false);
            if (op == Token.SLASH) {

                //return new RawSlashExpression(start, step);
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
                if (!allowXPath30Syntax) {
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
        boolean reverse = (step instanceof AxisExpression) &&
                !AxisInfo.isForwards[((AxisExpression) step).getAxis()];

        while (true) {
            if (t.currentToken == Token.LSQB) {
                step = parsePredicate(step);
            } else if (t.currentToken == Token.LPAR) {
                // dynamic function call (XQuery 3.0/XPath 3.0 syntax)
                step = parseDynamicFunctionCall(step, null);
                setLocation(step);
            } else if (t.currentToken == Token.QMARK) {
                step = parseLookup(step);
                setLocation(step);
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
        expect(Token.RSQB);
        nextToken();
        step = new FilterExpression(step, predicate);
        setLocation(step);
        return step;
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
        nextToken();
        int token = getTokenizer().currentToken;
        if (token == Token.NAME || token == Token.FUNCTION) {
            return parseFunctionCall(lhs);
        } else if (token == Token.DOLLAR) {
            int offset = t.currentTokenStartOffset;
            StructuredQName varName = parseVariableName();
            Expression var = resolveVariableReference(offset, varName);
            expect(Token.LPAR);
            return parseDynamicFunctionCall(var, lhs);
        } else if (token == Token.LPAR) {
            Expression var = parseParenthesizedExpression();
            expect(Token.LPAR);
            return parseDynamicFunctionCall(var, lhs);
        } else if (token == Token.KEYWORD_LBRA && t.currentTokenValue.equals("function")) {
            Expression fn = parseInlineFunction(AnnotationList.EMPTY);
            expect(Token.LPAR);
            return parseDynamicFunctionCall(fn, lhs);
        } else if (token == Token.KEYWORD_CURLY && (t.currentTokenValue.equals("function") || t.currentTokenValue.equals("fn"))) {
            Expression fn = parseFocusFunction(AnnotationList.EMPTY);
            expect(Token.LPAR);
            return parseDynamicFunctionCall(fn, lhs);
        } else {
            grumble("Unexpected " + Token.tokens[token] + " after '=>'");
            return null;
        }
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
        checkLanguageVersion40();
        nextToken();

        ForExpression forExpr = new ForExpression();
        forExpr.setSequence(lhs);
        StructuredQName varName = new StructuredQName("vv", NamespaceUri.SAXON_GENERATED_VARIABLE, ""+lhs.hashCode());
        forExpr.setVariableQName(varName);
        VariableReference varRef = new LocalVariableReference(forExpr);

        int token = getTokenizer().currentToken;
        Expression rhs;
        if (token == Token.NAME || token == Token.FUNCTION) {
            rhs = parseFunctionCall(varRef);
        } else if (token == Token.DOLLAR) {
            int offset = t.currentTokenStartOffset;
            StructuredQName variableName = parseVariableName();
            Expression var = resolveVariableReference(offset, variableName);
            expect(Token.LPAR);
            rhs = parseDynamicFunctionCall(var, varRef);
        } else if (token == Token.LPAR) {
            Expression var = parseParenthesizedExpression();
            expect(Token.LPAR);
            rhs = parseDynamicFunctionCall(var, varRef);
        } else if (token == Token.KEYWORD_LBRA && t.currentTokenValue.equals("function")) {
            Expression fn = parseInlineFunction(AnnotationList.EMPTY);
            expect(Token.LPAR);
            rhs = parseDynamicFunctionCall(fn, varRef);
        } else if (token == Token.KEYWORD_CURLY && (t.currentTokenValue.equals("function") || t.currentTokenValue.equals("fn"))) {
            Expression fn = parseFocusFunction(AnnotationList.EMPTY);
            expect(Token.LPAR);
            rhs = parseDynamicFunctionCall(fn, varRef);
        } else {
            grumble("Unexpected " + Token.tokens[token] + " after '=!>'");
            return null;
        }
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
     * Parse a basic step expression (without the predicates)
     *
     * @param firstInPattern true only if we are parsing the first step in a
     *                       RelativePathPattern in the XSLT Pattern syntax
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    /*@NotNull*/
    protected Expression parseBasicStep(boolean firstInPattern) throws XPathException {
        switch (t.currentToken) {
            case Token.DOLLAR:
                int offset = t.currentTokenStartOffset;
                StructuredQName variableName = parseVariableName();
//                if (allowXPath40Syntax && t.currentToken == Token.THIN_ARROW) {
//                    // This is a single-argument lambda expression, $x -> {EXPR}
//                    List<UserFunctionParameter> params = new ArrayList<>(1);
//                    UserFunctionParameter arg = new UserFunctionParameter();
//                    arg.setRequiredType(SequenceType.ANY_SEQUENCE);
//                    arg.setVariableQName(variableName);
//                    arg.setSlotNumber(0);
//                    params.add(arg);
//                    nextToken();
//                    return parseInlineFunctionBody(AnnotationList.EMPTY, params, SequenceType.ANY_SEQUENCE);
//                } else {
                    // This is a variable reference
                    return resolveVariableReference(offset, variableName);
//                }

            case Token.LPAR:
                if (allowXPath40Syntax
                        && t.thereMightBeAnArrowAhead()
                        && (t.peekAhead() == Token.DOLLAR || t.peekAhead() == Token.RPAR)) {
                    // Possible lambda expression.
                    Tokenizer checkpoint = new Tokenizer();
                    t.copyTo(checkpoint);
                    List<StructuredQName> lambdaParams = parseLambdaParams();
                    if (lambdaParams == null) {
                        // backtrack and resume a normal parse
                        checkpoint.copyTo(t);
                    } else {
                        //nextToken();
                        List<UserFunctionParameter> params = new ArrayList<>(lambdaParams.size());
                        int slotNumber = 0;
                        for (StructuredQName paramName : lambdaParams) {
                            UserFunctionParameter arg = new UserFunctionParameter();
                            arg.setRequiredType(SequenceType.ANY_SEQUENCE);
                            arg.setVariableQName(paramName);
                            arg.setSlotNumber(slotNumber++);
                            params.add(arg);
                        }
                        return parseInlineFunctionBody(AnnotationList.EMPTY, params, SequenceType.ANY_SEQUENCE);
                    }
                }
                return parseParenthesizedExpression();

            case Token.LSQB:
                return parseArraySquareConstructor();

            case Token.STRING_LITERAL:
                return parseStringLiteral(true);

            case Token.STRING_LITERAL_BACKTICKED:
                return parseBackTickedStringLiteral();

            case Token.STRING_CONSTRUCTOR_INITIAL:
                return parseStringConstructor();

            case Token.BACKTICK:
                checkLanguageVersion40();
                return parseStringTemplate();

            case Token.NUMBER:
                return parseNumericLiteral(true);
            case Token.HEX_INTEGER:
                return parseHexLiteral(true);
            case Token.BINARY_INTEGER:
                return parseBinaryLiteral(true);

            case Token.FUNCTION:
                return parseFunctionCall(null);

            case Token.QMARK:
                return parseLookup(new ContextItemExpression());

            case Token.DOT:
                nextToken();
                Expression cie = new ContextItemExpression();
                setLocation(cie);
                return cie;

            case Token.DOTDOT:
                nextToken();
                Expression pne = new AxisExpression(AxisInfo.PARENT, null);
                setLocation(pne);
                return pne;

            case Token.PERCENT: {
                AnnotationList annotations = parseAnnotationsList();
                if (t.currentToken == Token.THIN_ARROW) {
                    checkLanguageVersion40();
                    nextToken();
                    annotations.check(env.getConfiguration(), "IF");
                    if (t.currentToken == Token.LPAR) {
                        return parseInlineFunction(annotations);
                    } else if (t.currentToken == Token.LCURLY) {
                        return parseFocusFunction(annotations);
                    } else {
                        grumble("Expected '(' or '{' after '->'");
                    }
                }
                if (!(t.currentTokenValue.equals("function") || t.currentTokenValue.equals("fn"))) {
                    grumble("Expected 'function' to follow the annotation assertion");
                }
                annotations.check(env.getConfiguration(), "IF");
                if (t.currentToken == Token.KEYWORD_CURLY) {
                    return parseFocusFunction(annotations);
                } else {
                    return parseInlineFunction(annotations);
                }
            }
            case Token.THIN_ARROW: {
                checkLanguageVersion40();
                nextToken();
                if (t.currentToken == Token.LPAR) {
                    AnnotationList annotations = AnnotationList.EMPTY;
                    return parseInlineFunction(annotations);
                } else if (t.currentToken == Token.LCURLY) {
                    AnnotationList annotations = AnnotationList.EMPTY;
                    return parseFocusFunction(annotations);
                } else {
                    grumble("Expected '(' or '{' after '->'");
                }
                break;
            }
            case Token.KEYWORD_LBRA:
                if (t.currentTokenValue.equals("function") || (t.currentTokenValue.equals("fn"))) {
                    AnnotationList annotations = AnnotationList.EMPTY;
                    return parseInlineFunction(annotations);
                }
                CSharp.emitCode("goto case Saxon.Hej.expr.parser.Token.NAME;");
                // fall through!
            case Token.NAME:
            case Token.PREFIX:
            case Token.SUFFIX:
            case Token.STAR:
                byte defaultAxis = AxisInfo.CHILD;
                if (t.currentToken == Token.KEYWORD_LBRA &&
                        (t.currentTokenValue.equals("attribute") || t.currentTokenValue.equals("schema-attribute"))) {
                    defaultAxis = AxisInfo.ATTRIBUTE;
                } else if (t.currentToken == Token.KEYWORD_LBRA && t.currentTokenValue.equals("namespace-node")) {
                    defaultAxis = AxisInfo.NAMESPACE;
                    testPermittedAxis(AxisInfo.NAMESPACE, "XQST0134");
                } else if (firstInPattern && t.currentToken == Token.KEYWORD_LBRA && t.currentTokenValue.equals("document-node")) {
                    defaultAxis = AxisInfo.SELF;
                }
                NodeTest test = parseNodeTest(Type.ELEMENT);
                if (test instanceof AnyNodeTest) {
                    // handles patterns of the form match="node()"
                    if (defaultAxis == AxisInfo.CHILD) {
                        test = MultipleNodeKindTest.CHILD_NODE;
                    } else {
                        test = NodeKindTest.ATTRIBUTE;
                    }
                }
                AxisExpression ae = new AxisExpression(defaultAxis, test);
                setLocation(ae);
                return ae;

            case Token.AT:
                nextToken();
                switch (t.currentToken) {

                    case Token.NAME:
                    case Token.PREFIX:
                    case Token.SUFFIX:
                    case Token.STAR:
                    case Token.KEYWORD_LBRA:
                    case Token.LPAR:
                        AxisExpression ae2 = new AxisExpression(AxisInfo.ATTRIBUTE, parseNodeTest(Type.ATTRIBUTE));
                        setLocation(ae2);
                        return ae2;

                    default:
                        grumble("@ must be followed by a NodeTest");
                        break;
                }
                break;

            case Token.AXIS:
                int axis;
                try {
                    axis = AxisInfo.getAxisNumber(t.currentTokenValue);
                } catch (XPathException err) {
                    grumble(err.getMessage());
                    axis = AxisInfo.CHILD; // error recovery
                }
                testPermittedAxis(axis, "XPST0003");
                short principalNodeType = AxisInfo.principalNodeType[axis];
                nextToken();
                switch (t.currentToken) {

                    case Token.NAME:
                    case Token.PREFIX:
                    case Token.SUFFIX:
                    case Token.STAR:
                    case Token.KEYWORD_LBRA:
                    case Token.LPAR:
                        Expression ax = new AxisExpression(axis, parseNodeTest(principalNodeType));
                        setLocation(ax);
                        return ax;

                    default:
                        grumble("Unexpected token " + currentTokenDisplay() + " after axis name");
                        break;
                }
                break;

            case Token.KEYWORD_CURLY:
                switch (t.currentTokenValue) {
                    case "map":
                        return parseMapExpression();
                    case "array":
                        return parseArrayCurlyConstructor();
                    case "function":
                    case "fn":
                        return parseFocusFunction(null);
                }
                CSharp.emitCode("goto case Saxon.Hej.expr.parser.Token.ELEMENT_QNAME;");
                // else fall through
            case Token.ELEMENT_QNAME:
            case Token.ATTRIBUTE_QNAME:
            case Token.NAMESPACE_QNAME:
            case Token.PI_QNAME:
            case Token.TAG:
                return parseConstructor();

            case Token.NAMED_FUNCTION_REF:
                return parseNamedFunctionReference();

            default:
                grumble("Unexpected token " + currentTokenDisplay() + " at start of expression");
                break;
        }
        return new ErrorExpression();
    }

    public Expression parseParenthesizedExpression() throws XPathException {
        nextToken();
        if (t.currentToken == Token.RPAR) {
            nextToken();
            return makeTracer(Literal.makeEmptySequence(), null);
        }
        Expression seq = parseExpression();
        expect(Token.RPAR);
        nextToken();
        return seq;
    }

    /**
     * Attempt to parse the parameter list of a lambda expression. No errors are thrown, except for
     * unresolvable QNames. If the grammar isn't matched, return null, so the caller can backtrack.
     * @return either a list of parameter names, or null.
     * @throws XPathException only for low-level errors like failure to tokenize, or failure to
     * resolve a QName.
     */
    public List<StructuredQName> parseLambdaParams() throws XPathException {
        List<StructuredQName> result = new ArrayList<>(4);
        nextToken();
        if (t.currentToken == Token.RPAR) {
            nextToken();
            if (t.currentToken == Token.THIN_ARROW) {
                nextToken();
                return result;
            } else {
                return null;
            }
        }
        while (true) {
            if (t.currentToken != Token.DOLLAR) {
                return null;
            }
            nextToken();
            if (t.currentToken == Token.NAME) {
                result.add(makeStructuredQName(t.currentTokenValue, NamespaceUri.NULL));
            } else {
                return null;
            }
            nextToken();
            if (t.currentToken == Token.COMMA) {
                nextToken();
            } else if (t.currentToken == Token.RPAR) {
                nextToken();
                if (t.currentToken == Token.THIN_ARROW) {
                    nextToken();
                    return result;
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
    }

    protected void testPermittedAxis(int axis, String errorCode) throws XPathException {
        if (axis == AxisInfo.PRECEDING_OR_ANCESTOR) {
            grumble("The preceding-or-ancestor axis is for internal use only", errorCode);
        }
    }


    public Expression parseNumericLiteral(boolean traceable) throws XPathException {
        int offset = t.currentTokenStartOffset;
        NumericValue number = NumericValue.parseNumber(t.currentTokenValue);
        if (number.isNaN()) {
            grumble("Invalid numeric literal " + Err.wrap(t.currentTokenValue, Err.VALUE));
        }
        nextToken();
        Literal lit = Literal.makeLiteral(number);
        setLocation(lit, offset);
        //lit.setRetainedStaticContext(env.makeRetainedStaticContext());
        return traceable ? makeTracer(lit, null) : lit;
    }

    @CSharpReplaceException(from="java.lang.NumberFormatException", to="System.FormatException")
    public Expression parseHexLiteral(boolean traceable) throws XPathException {
        try {
            int offset = t.currentTokenStartOffset;
            IntegerValue val;
            if (t.currentTokenValue.length() < 16) {
                if (t.currentTokenValue.isEmpty()) {
                    // Handled specially because .NET code otherwise crashes
                    grumble("Empty hex literal");
                }
                long parsed = Long.parseLong(t.currentTokenValue, 16);
                val = new Int64Value(parsed);
            } else {
                BigInteger big = new BigInteger(t.currentTokenValue, 16);
                val = new BigIntegerValue(big);
            }
            nextToken();
            Literal lit = Literal.makeLiteral(val);
            setLocation(lit, offset);
            return traceable ? makeTracer(lit, null) : lit;
        } catch (NumberFormatException e) {
            grumble("Invalid hex literal");
            return null;
        }
    }

    public Expression parseBinaryLiteral(boolean traceable) throws XPathException {
        try {
            int offset = t.currentTokenStartOffset;
            IntegerValue val;
            if (t.currentTokenValue.length() < 64) {
                if (t.currentTokenValue.isEmpty()) {
                    // Handled specially because .NET code otherwise crashes
                    grumble("Empty binary literal");
                }
                long parsed = binaryStringToLong(t.currentTokenValue);
                val = new Int64Value(parsed);
            } else {
                BigInteger big = new BigInteger(t.currentTokenValue, 2);
                val = new BigIntegerValue(big);
            }
            nextToken();
            Literal lit = Literal.makeLiteral(val);
            setLocation(lit, offset);
            return traceable ? makeTracer(lit, null) : lit;
        } catch (NumberFormatException e) {
            grumble("Invalid binary literal");
            return null;
        }
    }

    @CSharpReplaceBody(code="return Convert.ToInt64(input, 2);")
    private long binaryStringToLong(String input) {
        return Long.parseLong(input, 2);
    }

    protected Expression parseStringLiteral(boolean traceable) throws XPathException {
        Literal literal = makeStringLiteral(t.currentTokenValue, true);
        nextToken();
        return traceable ? makeTracer(literal, null) : literal;
    }

    protected Expression parseBackTickedStringLiteral() throws XPathException {
        Literal literal = makeStringLiteral(t.currentTokenValue, false);
        nextToken();
        return makeTracer(literal, null);
    }

    protected Expression parseStringConstructor() throws XPathException {
        grumble("String constructor expressions are allowed only in XQuery");
        return null;
    }

    public Expression parseStringTemplate() throws XPathException {
        int offset = t.inputOffset;
        // we're reading raw characters
        //t.nextChar(); // lose this one, it's the initial backtick
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
                    c = t.nextChar();
                    if (c == '`') {
                        currentPart.append('`');
                    } else {
                        Expression fixed = new StringLiteral(currentPart.toString());
                        components.add(fixed);
                        finished = true;
                        t.unreadChar();
                        t.lookAhead();
                        nextToken();
                    }
                    break;
                case '{':
                    c = t.nextChar();
                    if (c == '{') {
                        currentPart.append('{');
                    } else {
                        Expression fixed = new StringLiteral(currentPart.toString());
                        components.add(fixed);
                        currentPart.setLength(0);
                        t.unreadChar();
                        t.setState(Tokenizer.DEFAULT_STATE);
                        t.lookAhead();
                        nextToken();
                        if (t.currentToken == Token.RCURLY) {
                            //components.add(Literal.makeEmptySequence());
                        } else {
                            Expression exp = parseExpression();
                            RetainedStaticContext rscSJ = new RetainedStaticContext(env);
                            Expression fnSJ = SystemFunction.makeCall("string-join", rscSJ, exp, new StringLiteral(StringValue.SINGLE_SPACE));
                            ExpressionTool.copyLocationInfo(exp, fnSJ);
                            components.add(fnSJ);
                            expect(Token.RCURLY);
                        }
                    }
                    break;
                case '}':
                    c = t.nextChar();
                    if (c == '}') {
                        currentPart.append('}');
                    } else {
                        grumble("Closing brace ('}') in string template must be doubled");
                    }
                    break;
                default:
                    currentPart.append(c);
                    break;
            }
        } while (!finished);

        RetainedStaticContext rsc = new RetainedStaticContext(env);
        Block block = new Block(components.toArray(new Expression[]{}));
        setLocation(block);
        Expression fn = SystemFunction.makeCall("string-join", rsc, block, new StringLiteral(StringValue.EMPTY_STRING));
        ExpressionTool.copyLocationInfo(block, fn);
        components.add(fn);
        return fn;
    }

    public StructuredQName parseVariableName() throws XPathException {
        nextToken();
        expect(Token.NAME);
        String var = t.currentTokenValue;
        nextToken();
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
                throw err.maybeWithLocation(makeLocation());
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
     * Parse a node constructor. This is allowed only in XQuery, so the method throws
     * an error for XPath.
     *
     * @return the expression that results from the parsing
     * @throws net.sf.saxon.trans.XPathException if a static error occurs
     */

    /*@NotNull*/
    protected Expression parseConstructor() throws XPathException {
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
        IntSet placeMarkers = null;

        // the "(" has already been read by the Tokenizer: now parse the arguments
        nextToken();
        if (t.currentToken != Token.RPAR) {
            while (true) {
                Expression arg;
                int peek = t.peekAhead();
                if (t.currentToken == Token.QMARK && (peek == Token.COMMA || peek == Token.RPAR)) {
                    nextToken();
                    // this is a "?" placemarker
                    if (placeMarkers == null) {
                        placeMarkers = new IntArraySet();
                    }
                    placeMarkers.add(args.size());
                    arg = Literal.makeEmptySequence(); // a convenient fiction
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
            expect(Token.RPAR);
        }
        nextToken();

        if (placeMarkers == null) {
            return generateApplyCall(functionItem, args);
        } else {
            return createDynamicCurriedFunction(this, functionItem, args, placeMarkers);
        }
    }

    protected Expression generateApplyCall(Expression functionItem, ArrayList<Expression> args) {
        DynamicFunctionCall call = new DynamicFunctionCall(functionItem, args);
        setLocation(call, t.currentTokenStartOffset);
        return call;
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
        Tokenizer t = getTokenizer();
        int offset = t.currentTokenStartOffset;
        t.setState(Tokenizer.BARE_NAME_STATE); // Prevent mis-recognition of x?f(2)
        t.currentToken = Token.LPAR;  // Hack to force following symbol to be recognised in post-operator mode
        nextToken();
        int token = t.currentToken;
        t.setState(Tokenizer.OPERATOR_STATE);

        Expression result;
        if (token == Token.NAME) {
            String name = t.currentTokenValue;
            if (!NameChecker.isValidNCName(StringTool.codePoints(name))) {
                grumble("The name following '?' must be a valid NCName");
            }
            nextToken();
            result = lookupName(lhs, name);
        } else if (token == Token.NUMBER) {
            NumericValue number = NumericValue.parseNumber(t.currentTokenValue);
            if (!(number instanceof IntegerValue)) {
                grumble("Number following '?' must be an integer");
            }
            nextToken();

            result = new LookupExpression(lhs, Literal.makeLiteral(number));
        } else if (token == Token.MULT || token == Token.STAR) {
            nextToken();
            result = lookupStar(lhs);
        } else if (token == Token.LPAR) {
            t.setState(Tokenizer.DEFAULT_STATE);

            result = new LookupExpression(lhs, parseParenthesizedExpression());
        } else if (token == Token.STRING_LITERAL) {
            checkLanguageVersion40();
            result = lookupName(lhs, t.currentTokenValue);
            nextToken();
        } else if (token == Token.DOLLAR) {
            checkLanguageVersion40();
            offset = t.currentTokenStartOffset;
            StructuredQName varName = parseVariableName();
            result = new LookupExpression(lhs, resolveVariableReference(offset, varName));
        } else {
            grumble("Unexpected " + Token.tokens[token] + " after '?'");
            return null;
        }
        setLocation(result, offset);
        return result;
    }


    /**
     * Supporting code for lookup expressions (A?B) where B is an NCName
     *
     * @param lhs the LHS operand of the lookup expression
     * @param rhs the RHS operand of the lookup expression
     * @return the result of parsing the expression
     */

    private Expression lookupName(Expression lhs, String rhs) {
        return new LookupExpression(lhs, new StringLiteral(rhs));
    }

    /**
     * Supporting code for lookup expressions (A?B) where B is the wildcard "*"
     *
     * @param lhs the LHS operand of the lookup expression
     * @return the result of parsing the expression
     */

    private static Expression lookupStar(Expression lhs) {
        return new LookupAllExpression(lhs);
    }


    /**
     * Parse a NodeTest.
     * One of QName, prefix:*, *:suffix, *, text(), node(), comment(), or
     * processing-instruction(literal?), or element(~,~), attribute(~,~), etc.
     *
     * @param nodeType the node type being sought if one is specified
     * @return the resulting NodeTest object
     * @throws XPathException if any error is encountered
     */

    /*@NotNull*/
    protected NodeTest parseNodeTest(short nodeType) throws XPathException {
        int tok = t.currentToken;
        String tokv = t.currentTokenValue;
        switch (tok) {
            case Token.LPAR:
                checkLanguageVersion40();
                return parseUnionNodeTest(nodeType);

            case Token.NAME:
                nextToken();
                return makeNameTest(nodeType, tokv, nodeType == Type.ELEMENT);

            case Token.PREFIX:
                nextToken();
                return makeNamespaceTest(nodeType, tokv);

            case Token.SUFFIX:
                nextToken();
                tokv = t.currentTokenValue;
                expect(Token.NAME);
                nextToken();
                return makeLocalNameTest(nodeType, tokv);

            case Token.STAR:
                nextToken();
                return NodeKindTest.makeNodeKindTest(nodeType);

            case Token.KEYWORD_LBRA:
                return parseKindTest();

            default:
                grumble("Unrecognized node test");
                throw new XPathException(""); // unreachable instruction
        }
    }

    protected NodeTest parseUnionNodeTest(short nodeType) throws XPathException {
        nextToken();
        NodeTest test = parseNodeTest(nodeType);
        while (t.currentToken == Token.UNION && !t.currentTokenValue.equals("union")) {
            nextToken();
            test = new CombinedNodeTest(test, Token.UNION, parseNodeTest(nodeType));
        }
        expect(Token.RPAR);
        nextToken();
        return test;
    }

    /**
     * Parse a KindTest
     *
     * @return the KindTest, expressed as a NodeTest object
     * @throws net.sf.saxon.trans.XPathException if a static error is found
     */

    /*@NotNull*/
    private NodeTest parseKindTest() throws XPathException {
        NamePool pool = env.getConfiguration().getNamePool();
        String typeName = t.currentTokenValue;
        boolean empty = false;
        nextToken();
        if (t.currentToken == Token.RPAR) {
            empty = true;
            nextToken();
        }
        switch (typeName) {
            case "item":
                grumble("item() is not allowed in a path expression");
                return null;
            case "node":
                if (empty) {
                    return AnyNodeTest.getInstance();
                } else {
                    grumble("Expected ')': no arguments are allowed in node()");
                    return null;
                }
            case "text":
                if (empty) {
                    return NodeKindTest.TEXT;
                } else {
                    grumble("Expected ')': no arguments are allowed in text()");
                    return null;
                }
            case "comment":
                if (empty) {
                    return NodeKindTest.COMMENT;
                } else {
                    grumble("Expected ')': no arguments are allowed in comment()");
                    return null;
                }
            case "namespace-node":
                if (empty) {
                    if (!isNamespaceTestAllowed()) {
                        grumble("namespace-node() test is not allowed in XPath 2.0/XQuery 1.0");
                    }
                    return NodeKindTest.NAMESPACE;
                } else {
                    if (language == ParsedLanguage.EXTENDED_ITEM_TYPE && t.currentToken == Token.NAME) {
                        String nsName = t.currentTokenValue;
                        nextToken();
                        expect(Token.RPAR);
                        nextToken();
                        return new NameTest(Type.NAMESPACE, NamespaceUri.NULL, nsName, pool);
                    } else {
                        grumble("No arguments are allowed in namespace-node()");
                        return null;
                    }
                }
            case "document-node":
                if (empty) {
                    return NodeKindTest.DOCUMENT;
                } else {
                    if (!(t.currentTokenValue.equals("element") || t.currentTokenValue.equals("schema-element"))) {
                        grumble("Argument to document-node() must be element(...) or schema-element(...)");
                    }
                    NodeTest inner = parseKindTest();
                    expect(Token.RPAR);
                    nextToken();
                    return new DocumentNodeTest(inner);
                }
            case "processing-instruction":
                int fp = -1;
                if (empty) {
                    return NodeKindTest.PROCESSING_INSTRUCTION;
                } else if (t.currentToken == Token.STRING_LITERAL) {
                    String piName = Whitespace.trim(unescape(t.currentTokenValue));
                    if (!NameChecker.isValidNCName(StringTool.codePoints(piName))) {
                        // Became an error as a result of XPath erratum XP.E7
                        grumble("Processing instruction name must be a valid NCName", "XPTY0004");
                    } else {
                        fp = pool.allocateFingerprint(NamespaceUri.NULL, piName);
                    }
                } else if (t.currentToken == Token.NAME) {
                    try {
                        String[] parts = NameChecker.getQNameParts(t.currentTokenValue);
                        if (parts[0].isEmpty()) {
                            fp = pool.allocateFingerprint(NamespaceUri.NULL, parts[1]);
                        } else {
                            grumble("Processing instruction name must not contain a colon");
                        }
                    } catch (QNameException e) {
                        grumble("Invalid processing instruction name. " + e.getMessage());
                    }
                } else {
                    grumble("Processing instruction name must be a QName or a string literal");
                }
                nextToken();
                expect(Token.RPAR);
                nextToken();
                return new NameTest(Type.PROCESSING_INSTRUCTION, fp, pool);

            case "schema-attribute":
                if (empty) {
                    grumble(typeName + "schema-attribute() requires a name to be supplied");
                    return null;
                } else {
                    expect(Token.NAME);
                    String name = t.currentTokenValue;
                    fp = makeFingerprint(name, false);
                    nextToken();
                    expect(Token.RPAR);
                    nextToken();
                    if (!env.isImportedSchema(pool.getURI(fp))) {
                        grumble("No schema has been imported for namespace '" + pool.getURI(fp) + '\'', "XPST0008");
                    }
                    SchemaDeclaration decl = env.getConfiguration().getAttributeDeclaration(fp);
                    if (decl == null) {
                        grumble("There is no declaration for attribute @" + name + " in an imported schema", "XPST0008");
                        return null;
                    } else {
                        return decl.makeSchemaNodeTest();
                    }
                }
            case "schema-element":
                if (empty) {
                    grumble(typeName + "schema-element() requires a name to be supplied");
                    return null;
                } else {
                    expect(Token.NAME);
                    String name = t.currentTokenValue;
                    fp = makeFingerprint(name, true);
                    nextToken();
                    expect(Token.RPAR);
                    nextToken();
                    if (!env.isImportedSchema(pool.getURI(fp))) {
                        grumble("No schema has been imported for namespace '" + pool.getURI(fp) + '\'', "XPST0008");
                    }
                    SchemaDeclaration decl = env.getConfiguration().getElementDeclaration(fp);
                    if (decl == null) {
                        grumble("There is no declaration for element " + name + " in an imported schema", "XPST0008");
                        return null;
                    } else {
                        return decl.makeSchemaNodeTest();
                    }
                }

            case "attribute":
                // drop through

            case "element":
                boolean isElementTest = typeName.equals("element");
                int nodeKind = isElementTest ? Type.ELEMENT : Type.ATTRIBUTE;
                NodeTest nodeTest;
                if (empty) {
                    return isElementTest ? NodeKindTest.ELEMENT : NodeKindTest.ATTRIBUTE;
                }
                List<NodeTest> tests = parseNameTestUnion(nodeKind);
                if (tests.size() == 1) {
                    nodeTest = tests.get(0);
                } else {
                    if (!allowXPath40Syntax) {
                        grumble("NameTestUnion syntax requires 4.0 to be enabled");
                    }
                    nodeTest = NameTestUnion.withTests(tests);
                }
                if (t.currentToken == Token.RPAR) {
                    nextToken();
                    return nodeTest;
                } else if (t.currentToken == Token.COMMA) {
                    nextToken();
                    NodeTest result;
                    if (t.currentToken == Token.NAME) {
                        StructuredQName contentType = makeStructuredQName(t.currentTokenValue, env.getDefaultElementNamespace());
                        NamespaceUri uri = contentType.getNamespaceUri();

                        if (!(uri.equals(NamespaceUri.SCHEMA) || env.isImportedSchema(uri))) {
                            grumble("No schema has been imported for namespace '" + uri + '\'', "XPST0008");
                        }
                        SchemaType schemaType = env.getConfiguration().getSchemaType(contentType);
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
                        if ((schemaType == AnyType.getInstance() && nillable) ||
                                (nodeKind == Type.ATTRIBUTE && schemaType == AnySimpleType.getInstance())) {
                            result = nodeTest;
                        } else {
                            NodeTest typeTest = new ContentTypeTest(nodeKind, schemaType, env.getConfiguration(), nillable);
                            if (nodeTest instanceof NodeKindTest) {
                                // this represents element(*,T) or attribute(*,T)
                                result = typeTest;
                            } else {
                                result = new CombinedNodeTest(nodeTest, Token.INTERSECT, typeTest);
                            }
                        }
                    } else {
                        grumble("Unexpected " + Token.tokens[t.currentToken] + " after ',' in SequenceType");
                        return null;
                    }
                    expect(Token.RPAR);
                    nextToken();
                    return result;
                } else {
                    grumble("Expected ')' or ',' in SequenceType");
                }
                return null;
            default:
                // can't happen!
                grumble("Unknown node kind " + typeName);
                return null;
        }
    }


    public List<NodeTest> parseNameTestUnion(int nodeKind) throws XPathException {
        List<NodeTest> tests = new ArrayList<>();
        boolean matchesAll = false;
        while (true) {
            String tokv = t.currentTokenValue;
            switch (t.currentToken) {
                case Token.NAME:
                    nextToken();
                    tests.add(makeNameTest(nodeKind, tokv, true));
                    break;

                case Token.PREFIX:
                    nextToken();
                    tests.add(makeNamespaceTest(nodeKind, tokv));
                    break;

                case Token.SUFFIX:
                    nextToken();
                    tokv = t.currentTokenValue;
                    if (t.currentToken == Token.NAME) {
                        // OK
                    } else {
                        grumble("Expected name after '*:'");
                    }
                    nextToken();
                    tests.add(makeLocalNameTest(nodeKind, tokv));
                    break;

                case Token.STAR:
                case Token.MULT:
                    nextToken();
                    matchesAll = true;
                    break;

                default:
                    grumble("Unrecognized name test at " + Token.tokens[t.currentToken]);
                    return null;
            }
            if (t.currentToken == Token.UNION && !t.currentTokenValue.equals("union")) {
                // must be "|" not "union"!
                nextToken();
            } else {
                break;
            }
        };
        if (matchesAll) {
            // If there's a "*" in the list, then anything else gets swallowed
            tests.clear();
            tests.add(NodeKindTest.makeNodeKindTest(nodeKind));
        }
        return tests;
    }

    /**
     * Ask whether the syntax namespace-node() is allowed in a node kind test.
     *
     * @return true unless XPath 2.0 / XQuery 1.0 syntax is required
     */

    protected boolean isNamespaceTestAllowed() {
        return allowXPath30Syntax;
    }

    /**
     * Check that XPath 3.0 is in use
     *
     * @throws net.sf.saxon.trans.XPathException if XPath 3.0 support was not requested
     */

    protected void checkLanguageVersion30() throws XPathException {
        if (!allowXPath30Syntax) {
            grumble("To use XPath 3.0 syntax, you must configure the XPath parser to handle it");
        }
    }

    /**
     * Check that XPath 3.1 is in use
     *
     * @throws net.sf.saxon.trans.XPathException if XPath 3.1 support was not requested
     */

    protected void checkLanguageVersion31() throws XPathException {
        if (!allowXPath31Syntax) {
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
        if (!allowXPath40Syntax) {
            grumble("The parser is not configured to allow use of " + lang + " 4.0 syntax");
        }
    }

    /**
     * Check that the map syntax is enabled: this covers the extensions to XPath 3.0
     * syntax defined in XSLT 3.0 (and also, of course, in XPath 3.1)
     *
     * @throws net.sf.saxon.trans.XPathException if XPath 3.1 support was not requested
     */

    protected void checkMapExtensions() throws XPathException {
        if (!(allowXPath31Syntax || allowXPath30XSLTExtensions)) {
            grumble("The XPath parser is not configured to allow use of the map syntax from XSLT 3.0 or XPath 3.1");
        }
    }

    /**
     * Check that Saxon syntax extensions are permitted
     * @param construct name of the construct, for use in error messages
     * @throws XPathException if Saxon syntax extensions have not been enabled
     */

    public void checkSyntaxExtensions(String construct) throws XPathException {
        if (!allowXPath40Syntax) {
            grumble("Saxon XPath syntax extensions have not been enabled: " + construct + " is not allowed");
        }
    }

    /**
     * Parse a map expression. Requires XPath/XQuery 3.0
     * Provisional syntax
     * map { expr : expr (, expr : expr )*} }
     *
     * @return the map expression
     * @throws XPathException if parsing fails
     */

    /*@NotNull*/
    protected Expression parseMapExpression() throws XPathException {
        checkMapExtensions();

        // have read the "map {"
        Tokenizer t = getTokenizer();
        int offset = t.currentTokenStartOffset;
        List<Expression> entries = new ArrayList<>();
        nextToken();
        if (t.currentToken != Token.RCURLY) {
            while (true) {
                Expression key = parseExprSingle();
                if (t.currentToken == Token.ASSIGN) {
                    grumble("The ':=' notation is no longer accepted in map expressions: use ':' instead");
                }
                expect(Token.COLON);
                nextToken();
                Expression value = parseExprSingle();
                Expression entry;
                if (key instanceof Literal && ((Literal)key).getGroundedValue() instanceof AtomicValue
                        && value instanceof Literal) {
                    entry = Literal.makeLiteral(
                            new SingleEntryMap((AtomicValue) ((Literal)key).getGroundedValue(),
                                             ((Literal)value).getGroundedValue()));
                } else {
                    entry = MapFunctionSet.getInstance(31).makeFunction("entry", 2).makeFunctionCall(key, value);
                }
                entries.add(entry);
                if (t.currentToken == Token.RCURLY) {
                    break;
                } else {
                    expect(Token.COMMA);
                    nextToken();
                }
            }
        }
        t.lookAhead(); //manual lookahead after an RCURLY
        nextToken();

        Expression result;
        switch (entries.size()) {
            case 0:
                result = Literal.makeLiteral(new HashTrieMap());
                break;
            case 1:
                result = entries.get(0);
                break;
            default:
                Expression[] entriesArray = new Expression[entries.size()];
                Block block = new Block(entries.toArray(entriesArray));
                HashTrieMap options = new HashTrieMap();
                options.initialPut(new StringValue("duplicates"), new StringValue("reject"));
                options.initialPut(new QNameValue("", NamespaceUri.SAXON, "duplicates-error-code"), new StringValue("XQDY0137"));
                result = MapFunctionSet.getInstance(31).makeFunction("merge", 2).makeFunctionCall(block, Literal.makeLiteral(options));
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
            setLocation(arrayBlock, offset);
            return arrayBlock;
        }
        while (true) {
            Expression member = parseExprSingle();
            members.add(member);
            if (t.currentToken == Token.COMMA) {
                nextToken();
                continue;
            } else if (t.currentToken == Token.RSQB) {
                nextToken();
                break;
            }
            grumble("Expected ',' or ']', " +
                    "found " + Token.tokens[t.currentToken]);
            return new ErrorExpression();
        }
        SquareArrayConstructor block = new SquareArrayConstructor(members);
        setLocation(block, offset);
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
            t.lookAhead(); //manual lookahead after an RCURLY
            nextToken();
            return Literal.makeLiteral(SimpleArrayItem.EMPTY_ARRAY);
        }
        Expression body = parseExpression();
        expect(Token.RCURLY);
        t.lookAhead(); //manual lookahead after an RCURLY
        nextToken();

        SystemFunction sf = ArrayFunctionSet.getInstance(40).makeFunction("_from-sequence", 1);
        Expression result = sf.makeFunctionCall(body);
        setLocation(result, offset);
        return result;
    }

    /**
     * Parse a function call.
     * function-name '(' ( Expression (',' Expression )* )? ')'
     *
     * @param prefixArgument left hand operand of arrow operator,
     *                       or null in the case of a conventional function call
     * @return the resulting subexpression
     * @throws XPathException if any error is encountered
     */

    /*@NotNull*/
    public Expression parseFunctionCall(Expression prefixArgument) throws XPathException {

        String fname = t.currentTokenValue;
        int offset = t.currentTokenStartOffset;
        ArrayList<Expression> args = new ArrayList<>(10);
        if (prefixArgument != null) {
            args.add(prefixArgument);
        }

        StructuredQName functionName = resolveFunctionName(fname);
        IntSet placeMarkers = null;

        // the "(" has already been read by the Tokenizer: now parse the arguments

        Map<StructuredQName, Integer> keywordArgs = null;
        nextToken();
        if (t.currentToken != Token.RPAR) {
            while (true) {
                int peek = t.peekAhead();
                Expression arg;
                if (t.currentToken == Token.NAME && peek == Token.ASSIGN && allowXPath40Syntax) {
                    // keyword argument
                    StructuredQName paramName = qNameParser.parse(t.currentTokenValue, NamespaceUri.NULL);
                    nextToken(); // read the operator
                    nextToken(); // position on the expression giving the value
                    arg = parseExprSingle();
                    if (keywordArgs == null) {
                        keywordArgs = new HashMap<>();
                    } else if (keywordArgs.containsKey(paramName)) {
                        grumble("Duplicate keyword '" + paramName + "'in function arguments");
                    }
                    keywordArgs.put(paramName, args.size());
                    args.add(arg);
                } else {
                    if (keywordArgs != null) {
                        grumble("Keyword arguments must not be followed by positional arguments in a function call");
                    }
                    if (t.currentToken == Token.QMARK && (peek == Token.COMMA || peek == Token.RPAR)) {
                        nextToken();
                        // this is a "?" placemarker
                        if (placeMarkers == null) {
                            placeMarkers = new IntArraySet();
                        }
                        placeMarkers.add(args.size());
                        arg = Literal.makeEmptySequence(); // a convenient fiction
                    } else {
                        arg = parseExprSingle();
                    }
                    args.add(arg);
                }
                if (t.currentToken == Token.COMMA) {
                    nextToken();
                } else {
                    break;
                }
            }
            expect(Token.RPAR);
        }
        nextToken();

        if (scanOnly) {
            return new StringLiteral(StringValue.EMPTY_STRING);
        }

        Expression[] arguments = new Expression[args.size()];
        arguments = args.toArray(arguments);

        if (placeMarkers != null) {
            return makeCurriedFunction(this, offset, functionName, arguments, placeMarkers);
        }

        Expression fcall;
        SymbolicName.F sn = new SymbolicName.F(functionName, args.size());
        List<String> reasons = new ArrayList<>();
        try {
            fcall = env.getFunctionLibrary().bind(sn, arguments, keywordArgs, env, reasons);
        } catch (UncheckedXPathException e) {
            fcall = null;
            reasons.add(e.getMessage());
        }
        if (fcall == null) {
            return reportMissingFunction(offset, functionName, arguments, reasons);
        }
        //  A QName or NOTATION constructor function must be given the namespace context now
//        if (fcall instanceof CastExpression &&
//                ((AtomicType) fcall.getItemType()).isNamespaceSensitive()) {
//            ((CastExpression) fcall).bindStaticContext(env);
//        }
        // There are special rules for certain functions appearing in a pattern
        if (language == ParsedLanguage.XSLT_PATTERN) {
            if (fcall.isCallOn(RegexGroup.class)) {
                return Literal.makeEmptySequence();
            } else if (fcall instanceof CurrentGroupCall) {
                grumble("The current-group() function cannot be used in a pattern",
                        "XTSE1060", offset);
                return new ErrorExpression();
            } else if (fcall instanceof CurrentGroupingKeyCall) {
                grumble("The current-grouping-key() function cannot be used in a pattern",
                        "XTSE1070", offset);
                return new ErrorExpression();
            } else if (fcall.isCallOn(CurrentMergeGroup.class)) {
                grumble("The current-merge-group() function cannot be used in a pattern",
                        "XTSE3470", offset);
                return new ErrorExpression();
            } else if (fcall.isCallOn(CurrentMergeKey.class)) {
                grumble("The current-merge-key() function cannot be used in a pattern",
                        "XTSE3500", offset);
                return new ErrorExpression();
            }
        }
        setLocation(fcall, offset);
        for (Expression argument : arguments) {
            if (fcall != argument && argument.getParentExpression() == null
                    && !functionName.hasURI(NamespaceUri.GLOBAL_JS)) {
                // avoid doing this when the function has already been optimized away, e.g. unordered()
                // Also avoid doing this when a js: function is parsed into an ixsl:call()
                // TODO move the adoptChildExpression into individual function libraries
                fcall.adoptChildExpression(argument);
            }
        }

        return makeTracer(fcall, functionName);

    }

    /**
     * Process a function call in which one or more of the argument positions are
     * represented as "?" placemarkers (indicating partial application or currying)
     *
     * @param parser       the XPath parser
     * @param offset       offset in the query source of the start of the expression
     * @param name         the function call (as if there were no currying)
     * @param args         the arguments (with EmptySequence in the placemarker positions)
     * @param placeMarkers the positions of the placemarkers    @return the curried function
     * @return the curried function
     * @throws XPathException if a dynamic error occurs
     */

    public Expression makeCurriedFunction(
            XPathParser parser, int offset,
            StructuredQName name, Expression[] args, IntSet placeMarkers) throws XPathException {
        StaticContext env = parser.getStaticContext();
        FunctionLibrary lib = env.getFunctionLibrary();
        SymbolicName.F sn = new SymbolicName.F(name, args.length);
        FunctionItem target = lib.getFunctionItem(sn, env);
        if (target == null) {
            // This will not happen in XQuery; instead, a dummy function will be created in the
            // UnboundFunctionLibrary in case it's a forward reference to a function not yet compiled
            List<String> reasons = new ArrayList<>();
            return parser.reportMissingFunction(offset, name, args, reasons);
        }
        Expression targetExp = makeNamedFunctionReference(name, target);
        parser.setLocation(targetExp, offset);
        return curryFunction(targetExp, args, placeMarkers);
    }

    /**
     * Process a function expression in which one or more of the argument positions are
     * represented as "?" placemarkers (indicating partial application or currying)
     *
     * @param functionExp  an expression that returns the function to be curried
     * @param args         the arguments (with EmptySequence in the placemarker positions)
     * @param placeMarkers the positions of the placemarkers
     * @return the curried function
     */

    public static Expression curryFunction(Expression functionExp, Expression[] args, IntSet placeMarkers) {
        IntIterator ii = placeMarkers.iterator();
        while (ii.hasNext()) {
            args[ii.next()] = null;
        }
        return new PartialApply(functionExp, args);
    }


    public Expression createDynamicCurriedFunction(
            XPathParser p, Expression functionItem, ArrayList<Expression> args, IntSet placeMarkers) {
        Expression[] arguments = new Expression[args.size()];
        arguments = args.toArray(arguments);
        Expression result = curryFunction(functionItem, arguments, placeMarkers);
        p.setLocation(result, p.getTokenizer().currentTokenStartOffset);
        return result;
    }

    public void handleExternalFunctionDeclaration(XQueryParser p, XQueryFunction func) throws XPathException {
        parserExtension.needExtension(p, "External function declarations");
    }


    private Expression makeMapExpression(Map<String, Expression> keywordArgs) throws XPathException {
        Expression[] block = new Expression[keywordArgs.size()];
        int i=0;
        for (Map.Entry<String, Expression> entry : keywordArgs.entrySet()) {
            StringLiteral key = new StringLiteral(entry.getKey());
            block[i++] = MapFunctionSet.getInstance(31).makeFunction("entry", 2).makeFunctionCall(key, entry.getValue());
        }
        Block entries = new Block(block);
        return MapFunctionSet.getInstance(31).makeFunction("merge", 1).makeFunctionCall(entries);
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
                    if (env.getFunctionLibrary().isAvailable(sn, 31)) {
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

    @CSharpReplaceBody(code="return \"Reflexive calls to external Java methods are not available under SaxonCS\";")
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
     * Parse a literal function item (introduced in XQuery 1.1)
     * Syntax: QName # integer
     * The QName and # have already been read
     *
     * @return an ExternalObject representing the function item
     * @throws net.sf.saxon.trans.XPathException if a static error is encountered
     */

    /*@NotNull*/
    protected Expression parseNamedFunctionReference() throws XPathException {

        String fname = t.currentTokenValue;
        int offset = t.currentTokenStartOffset;

        StaticContext env = getStaticContext();

        // the "#" has already been read by the Tokenizer: now parse the arity

        nextToken();
        expect(Token.NUMBER);
        NumericValue number = NumericValue.parseNumber(t.currentTokenValue);
        if (!(number instanceof IntegerValue)) {
            grumble("Number following '#' must be an integer");
        }
        if (number.compareTo(0) < 0 || number.compareTo(Integer.MAX_VALUE) > 0) {
            grumble("Number following '#' is out of range", "FOAR0002");
        }
        int arity = (int) number.longValue();
        nextToken();
        StructuredQName functionName = null;

        try {
            functionName = getQNameParser().parse(fname, env.getDefaultFunctionNamespace());
            if (functionName.getPrefix().equals("")) {
                if (XPathParser.isReservedFunctionName(functionName.getLocalPart(), languageVersion)) {
                    grumble("The unprefixed function name '" + functionName.getLocalPart() + "' is reserved in XPath 3.1");
                }
            }
        } catch (XPathException e) {
            grumble(e.getMessage(), e.getErrorCodeQName());
            assert functionName != null;
        }

        FunctionItem fcf = null;
        try {
            FunctionLibrary lib = env.getFunctionLibrary();
            SymbolicName.F sn = new SymbolicName.F(functionName, arity);
            fcf = lib.getFunctionItem(sn, env);
            if (fcf == null) {
                grumble("Function " + functionName.getEQName() + "#" + arity + " not found", "XPST0017", offset);
            }
        } catch (XPathException e) {
            grumble(e.getMessage(), "XPST0017", offset);
        }

        // Special treatment of functions in the system function library that depend on dynamic context; turn these
        // into calls on function-lookup()

        if (functionName.hasURI(NamespaceUri.FN) && fcf instanceof SystemFunction) {
            final BuiltInFunctionSet.Entry details = ((SystemFunction) fcf).getDetails();
            if (fcf instanceof ContextAccessorFunction || (details != null &&
                    (details.properties & (BuiltInFunctionSet.FOCUS | BuiltInFunctionSet.DEPENDS_ON_STATIC_CONTEXT)) != 0)) {
                // For a context-dependent function, return a call on function-lookup(), which saves the context
                SystemFunction lookup = XPath31FunctionSet.getInstance().makeFunction("function-lookup", 2);
                lookup.setRetainedStaticContext(env.makeRetainedStaticContext());
                return lookup.makeFunctionCall(Literal.makeLiteral(new QNameValue(functionName, BuiltInAtomicType.QNAME)),
                                               Literal.makeLiteral(Int64Value.makeIntegerValue(arity)));
            }
        }

        Expression ref = makeNamedFunctionReference(functionName, fcf);
        setLocation(ref, offset);
        return ref;
    }

    private static Expression makeNamedFunctionReference(StructuredQName functionName, FunctionItem fcf) {
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
        List<UserFunctionParameter> params = new ArrayList<>(8);
        SequenceType resultType = SequenceType.ANY_SEQUENCE;
        int paramSlot = 0;
        while (t.currentToken != Token.RPAR) {
            //     ParamList   ::=     Param ("," Param)*
            //     Param       ::=     "$" VarName  TypeDeclaration?
            expect(Token.DOLLAR);
            nextToken();
            expect(Token.NAME);
            String argName = t.currentTokenValue;
            StructuredQName argQName = makeStructuredQName(argName, NamespaceUri.NULL);
            SequenceType paramType = SequenceType.ANY_SEQUENCE;
            nextToken();
            if (t.currentToken == Token.AS) {
                nextToken();
                paramType = parseSequenceType();
            }

            UserFunctionParameter arg = new UserFunctionParameter();
            arg.setRequiredType(paramType);
            arg.setVariableQName(argQName);
            arg.setSlotNumber(paramSlot++);
            params.add(arg);

            if (t.currentToken == Token.RPAR) {
                break;
            } else if (t.currentToken == Token.COMMA) {
                nextToken();
            } else {
                grumble("Expected ',' or ')' after function argument, found '" +
                                Token.tokens[t.currentToken] + '\'');
            }
        }
        t.setState(Tokenizer.BARE_NAME_STATE);
        nextToken();
        if (t.currentToken == Token.AS) {
            t.setState(Tokenizer.SEQUENCE_TYPE_STATE);
            nextToken();
            resultType = parseSequenceType();
        }
        return parseInlineFunctionBody(annotations, params, resultType);

    }

    protected Expression parseInlineFunctionBody(
            AnnotationList annotations,
            List<UserFunctionParameter> params,
            SequenceType resultType) throws XPathException {
        // the next token should be the "{" at the start of the function body

        int offset = t.currentTokenStartOffset;

        InlineFunctionDetails details = new InlineFunctionDetails();
        details.outerVariables = new IndexedStack<>();
        for (LocalBinding lb : getRangeVariables()) {
            details.outerVariables.push(lb);
        }
        details.outerVariablesUsed = new ArrayList<>(4);
        details.implicitParams = new ArrayList<>(4);
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

        expect(Token.LCURLY);
        t.setState(Tokenizer.DEFAULT_STATE);
        nextToken();
        Expression body;
        if (t.currentToken == Token.RCURLY && isAllowXPath31Syntax()) {
            t.lookAhead();
            nextToken();
            body = Literal.makeEmptySequence();
        } else {
            body = parseExpression();
            expect(Token.RCURLY);
            t.lookAhead();  // must be done manually after an RCURLY
            nextToken();
        }
        ExpressionTool.setDeepRetainedStaticContext(body, getStaticContext().makeRetainedStaticContext());
        Expression result = makeInlineFunctionValue(this, annotations, details, params, resultType, body);

        setLocation(result, offset);

        for (UserFunctionParameter arg : params) {
            undeclareRangeVariable();
        }
        // restore the previous stack of range variables
        setRangeVariables(details.outerVariables);
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

        if (uf.getPackageData() instanceof StylesheetPackage) {
            // Add the inline function as a private component to the package, so that it can have binding
            // slots allocated for any references to global variables or functions, and so that it will
            // be copied as a hidden component into any using packages
            StylesheetPackage pack = (StylesheetPackage) uf.getPackageData();
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
                partialArgs[i] = null;
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
            result = new PartialApply(result, partialArgs);

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
        if (env instanceof ExpressionContext && !inlineFunctionStack.isEmpty()) {
            b2 = findOuterXSLTVariable(qName, inlineFunctionStack, env);
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
            IndexedStack<LocalBinding> outerVariables = details.outerVariables;
            for (int v = outerVariables.size() - 1; v >= 0; v--) {
                LocalBinding b2 = outerVariables.get(v);
                if (b2.getVariableQName().equals(qName)) {
                    for (int bs = s; bs <= inlineFunctionStack.size() - 1; bs++) {
                        details = inlineFunctionStack.get(bs);
                        boolean found = false;
                        for (int p = 0; p < details.outerVariablesUsed.size() - 1; p++) {
                            if (details.outerVariablesUsed.get(p) == b2) {
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
            StructuredQName qName, IndexedStack<InlineFunctionDetails> inlineFunctionStack, StaticContext env) {
        StructuredQName attName = ((ExpressionContext) env).getAttributeName();
        SourceBinding decl = ((ExpressionContext) env).getStyleElement().bindLocalVariable(qName, attName);
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
        checkLanguageVersion40();
        //Tokenizer t = getTokenizer();
        int offset = t.currentTokenStartOffset;

        InlineFunctionDetails details = new InlineFunctionDetails();
        details.outerVariables = new IndexedStack<>();
        for (LocalBinding lb : getRangeVariables()) {
            details.outerVariables.push(lb);
        }
        details.outerVariablesUsed = new ArrayList<>(4);
        details.implicitParams = new ArrayList<>(4);
        inlineFunctionStack.push(details);
        setRangeVariables(new IndexedStack<>());
        nextToken();
        List<UserFunctionParameter> params = new ArrayList<>(1);
        SequenceType resultType = SequenceType.ANY_SEQUENCE;

        StructuredQName argQName = new StructuredQName("saxon", NamespaceUri.SAXON, "dot");

        UserFunctionParameter arg = new UserFunctionParameter();
        arg.setRequiredType(SequenceType.SINGLE_ITEM);
        arg.setVariableQName(argQName);
        arg.setSlotNumber(0);
        params.add(arg);

        Expression body;
        if (t.currentToken == Token.RCURLY) {
            t.lookAhead();  // must be done manually after an RCURLY
            nextToken();
            body = Literal.makeEmptySequence();
        } else {
            body = parseExpression();
            expect(Token.RCURLY);
            t.lookAhead();  // must be done manually after an RCURLY
            nextToken();
            body.setRetainedStaticContext(getStaticContext().makeRetainedStaticContext());

            LocalVariableReference ref = new LocalVariableReference(arg);
            body = new ForEach(ref, body);
        }
        Expression result = makeInlineFunctionValue(this, AnnotationList.EMPTY, details, params, resultType, body);

        setLocation(result, offset);
        // restore the previous stack of range variables
        setRangeVariables(details.outerVariables);
        inlineFunctionStack.pop();
        return result;
    }


    /* Must be in alphabetical order, since a binary search is used */

    private final static String[] reservedFunctionNames31 = new String[]{
            "array", "attribute", "comment", "document-node", "element", "empty-sequence", "function", "if", "item", "map",
            "namespace-node", "node", "processing-instruction", "schema-attribute", "schema-element", "switch", "text", "typeswitch"
    };

    private final static String[] reservedFunctionNames40 = new String[]{
            "attribute", "comment", "document-node", "element", "empty-sequence", "fn", "function", "if", "item",
            "namespace-node", "node", "processing-instruction", "schema-attribute", "schema-element", "switch", "text", "typeswitch"
    };

    /**
     * Check whether a function name is reserved in XPath 3.1 or 4.0 (when unprefixed)
     *
     * @param name the function name (the local-name as a string)
     * @param version set to 31 for XPath 3.1, 40 for XPath 4.0
     * @return true if the function name is reserved
     */

    public static boolean isReservedFunctionName(String name, int version) {
        int x = Arrays.binarySearch(version>=40 ? reservedFunctionNames40 : reservedFunctionNames31, name);
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
     * Make a NameTest, using the static context for namespace resolution
     *
     * @param nodeKind   the type of node required (identified by a constant in
     *                   class Type)
     * @param qname      the lexical QName of the required node; alternatively,
     *                   a QName in Clark notation ({uri}local)
     * @param useDefault true if the default namespace should be used when
     *                   the QName is unprefixed
     * @return a NameTest, representing a pattern that tests for a node of a
     * given node kind and a given name
     * @throws XPathException if the QName is invalid
     */

    /*@NotNull*/
    public NodeTest makeNameTest(int nodeKind, /*@NotNull*/ String qname, boolean useDefault)
            throws XPathException {
        NamePool pool = env.getConfiguration().getNamePool();
        NamespaceUri defaultNS = NamespaceUri.NULL;
        if (useDefault && nodeKind == Type.ELEMENT && !qname.startsWith("Q{") && !qname.contains(":")) {
            UnprefixedElementMatchingPolicy policy = env.getUnprefixedElementMatchingPolicy();
            switch (policy) {
                case DEFAULT_NAMESPACE:
                    defaultNS = env.getDefaultElementNamespace();
                    break;
                case DEFAULT_NAMESPACE_OR_NONE:
                    defaultNS = env.getDefaultElementNamespace();
                    StructuredQName q = makeStructuredQName(qname, defaultNS);
                    int fp1 = pool.allocateFingerprint(q.getNamespaceUri(), q.getLocalPart());
                    NameTest test1 = new NameTest(nodeKind, fp1, pool);
                    int fp2 = pool.allocateFingerprint(NamespaceUri.NULL, q.getLocalPart());
                    NameTest test2 = new NameTest(nodeKind, fp2, pool);
                    return new CombinedNodeTest(test1, Token.UNION, test2);
                case ANY_NAMESPACE:
                    if (!NameChecker.isValidNCName(StringTool.codePoints(qname))) {
                        grumble("Invalid name '" + qname + "'");
                    }
                    return new LocalNameTest(pool, nodeKind, qname);
            }
        }
        StructuredQName qName = makeStructuredQName(qname, defaultNS);
        int fp = pool.allocateFingerprint(qName.getNamespaceUri(), qName.getLocalPart());
        return new NameTest(nodeKind, fp, pool);
    }

    public QNameTest makeQNameTest(int nodeKind, String qname)
            throws XPathException {
        NamePool pool = env.getConfiguration().getNamePool();
        StructuredQName q = makeStructuredQName(qname, NamespaceUri.NULL);
        assert q != null;
        int fp = pool.allocateFingerprint(q.getNamespaceUri(), q.getLocalPart());
        return new NameTest(nodeKind, fp, pool);
    }

    /**
     * Make a NamespaceTest (name:*)
     *
     * @param nodeKind integer code identifying the type of node required
     * @param prefix   the namespace prefix
     * @return the NamespaceTest, a pattern that matches all nodes in this
     * namespace
     * @throws XPathException if the namespace prefix is not declared
     */

    /*@NotNull*/
    public NamespaceTest makeNamespaceTest(int nodeKind, String prefix)
            throws XPathException {
        NamePool pool = env.getConfiguration().getNamePool();
        if (scanOnly) {
            // return an arbitrary namespace if we're only doing a syntax check
            return new NamespaceTest(pool, nodeKind, NamespaceUri.SAXON);
        }
        if (prefix.startsWith("Q{")) {
            String uri = prefix.substring(2, prefix.length() - 2);
            return new NamespaceTest(pool, nodeKind, NamespaceUri.of(uri));
        }
        try {
            StructuredQName sq = qNameParser.parse(prefix + ":dummy", NamespaceUri.NULL);
            return new NamespaceTest(pool, nodeKind, sq.getNamespaceUri());
        } catch (XPathException err) {
            grumble(err.getMessage(), err.getErrorCodeQName());
            return null;
        }
    }

    /**
     * Make a LocalNameTest (*:name)
     *
     * @param nodeKind  the kind of node to be matched
     * @param localName the requred local name
     * @return a LocalNameTest, a pattern which matches all nodes of a given
     * local name, regardless of namespace
     * @throws XPathException if the local name is invalid
     */

    /*@NotNull*/
    public LocalNameTest makeLocalNameTest(int nodeKind, String localName)
            throws XPathException {
        if (!NameChecker.isValidNCName(StringTool.codePoints(localName))) {
            grumble("Local name [" + localName + "] contains invalid characters");
        }
        return new LocalNameTest(env.getConfiguration().getNamePool(), nodeKind, localName);
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

    protected boolean isKeyword(String s) {
        return t.currentToken == Token.NAME && t.currentTokenValue.equals(s);
    }

    /**
     * Set that we are parsing in "scan only"
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
