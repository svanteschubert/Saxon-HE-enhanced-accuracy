////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.expr.parser.XPathParser;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.List;

/**
 * This class represents an attribute value template. The class allows an AVT to be parsed, and
 * can construct an Expression that returns the effective value of the AVT.
 * <p>This is an abstract class that is never instantiated, it contains static methods only.</p>
 */

public abstract class AttributeValueTemplate {

    private AttributeValueTemplate() {
    }


    /**
     * Static factory method to create an AVT from an XSLT string representation. The
     * method is also used for text value templates.
     * @param avt the attribute value template (or TVT) as written.
     * @param env the static context
     * @return an expression that computes the value of the attribute / content, as a string.
     * In the case of a TVT this must be further processed to create a text node.
     */

    public static Expression make(String avt, StaticContext env) throws XPathException {

        int languageLevel = env.getXPathVersion();
        List<Expression> components = new ArrayList<>(5);

        int i0, i1, i8, i9;
        int len = avt.length();
        int last = 0;
        while (last < len) {

            i0 = avt.indexOf("{", last);
            i1 = avt.indexOf("{{", last);
            i8 = avt.indexOf("}", last);
            i9 = avt.indexOf("}}", last);

            if ((i0 < 0 || len < i0) && (i8 < 0 || len < i8)) {   // found end of string
                addStringComponent(components, avt, last, len);
                break;
            } else if (i8 >= 0 && (i0 < 0 || i8 < i0)) {             // found a "}"
                if (i8 != i9) {                        // a "}" that isn't a "}}"
                    XPathException err = new XPathException("Closing curly brace in attribute value template \"" + avt.substring(0, len) + "\" must be doubled");
                    err.setErrorCode("XTSE0370");
                    err.setIsStaticError(true);
                    throw err;
                }
                addStringComponent(components, avt, last, i8 + 1);
                last = i8 + 2;
            } else if (i1 >= 0 && i1 == i0) {              // found a doubled "{{"
                addStringComponent(components, avt, last, i1 + 1);
                last = i1 + 2;
            } else if (i0 >= 0) {                        // found a single "{"
                if (i0 > last) {
                    addStringComponent(components, avt, last, i0);
                }
                Expression exp;
                XPathParser parser = env.getConfiguration().newExpressionParser("XP", false, languageLevel);
                //parser.setDefaultContainer(container);
                parser.setLanguage(XPathParser.ParsedLanguage.XPATH, 31);
                parser.setAllowAbsentExpression(true);
                exp = parser.parse(avt, i0 + 1, Token.RCURLY, env);
                exp.setRetainedStaticContext(env.makeRetainedStaticContext());
                exp = exp.simplify();
                last = parser.getTokenizer().currentTokenStartOffset + 1;

                if (env instanceof ExpressionContext && ((ExpressionContext)env).getStyleElement() instanceof XSLAnalyzeString
                        && isIntegerOrIntegerPair(exp)) {
                    env.issueWarning("Found {" + showIntegers(exp) + "} in regex attribute: perhaps {{" +
                        showIntegers(exp) + "}} was intended? (The attribute is an AVT, so curly braces should be doubled)", exp.getLocation());
                }

                if (env.isInBackwardsCompatibleMode()) {
                    components.add(makeFirstItem(exp, env));
                } else {
                    components.add(XSLLeafNodeConstructor.makeSimpleContentConstructor(
                            exp,
                            new StringLiteral(StringValue.SINGLE_SPACE), env).simplify());
                }

            } else {
                throw new IllegalStateException("Internal error parsing AVT");
            }
        }

        Expression result;

        // is it empty?

        if (components.isEmpty()) {
            result = new StringLiteral(StringValue.EMPTY_STRING);
        }

        // is it a single component?

        else if (components.size() == 1) {
            result = components.get(0).simplify();
        }

        // otherwise, return an expression that concatenates the components

        else {
            Expression[] args = new Expression[components.size()];
            components.toArray(args);
            Expression fn = SystemFunction.makeCall("concat", new RetainedStaticContext(env), args);
            result = fn.simplify();
        }

        result.setLocation(env.getContainingLocation());
        return result;

    }

    /**
     * Used to detect warning condition when braces are undoubled in the regex attribute of xsl:analyze-string
     *
     * @param exp an expression
     * @return true if the expression is an integer literal or a pair of two integer literals
     */

    private static boolean isIntegerOrIntegerPair(Expression exp) {
        if (exp instanceof Literal) {
            GroundedValue val = ((Literal) exp).getValue();
            if (val instanceof IntegerValue) {
                return true;
            }
            if (val.getLength() == 2) {
                return val.itemAt(0) instanceof IntegerValue && val.itemAt(1) instanceof IntegerValue;
            }
        }
        return false;
    }

    /**
     * Used to report warning condition when braces are undoubled in the regex attribute of xsl:analyze-string
     *
     * @param exp an expression
     * @return string representation of an integer literal or a pair of two integer literals
     */

    private static String showIntegers(Expression exp) {
        if (exp instanceof Literal) {
            GroundedValue val = ((Literal) exp).getValue();
            if (val instanceof IntegerValue) {
                return val.toString();
            }
            if (val.getLength() == 2) {
                if (val.itemAt(0) instanceof IntegerValue && val.itemAt(1) instanceof IntegerValue) {
                    return val.itemAt(0).toString() + "," + val.itemAt(1).toString();
                }
            }
        }
        return "";
    }

    private static void addStringComponent(List<Expression> components, String avt, int start, int end) {
        if (start < end) {
            components.add(new StringLiteral(avt.substring(start, end)));
        }
    }

    /**
     * Make an expression that extracts the first item of a sequence, after atomization
     */

    /*@NotNull*/
    public static Expression makeFirstItem(Expression exp, StaticContext env) {
        if (Literal.isEmptySequence(exp)) {
            return exp;
        }
        final TypeHierarchy th = env.getConfiguration().getTypeHierarchy();
        if (!exp.getItemType().isPlainType()) {
            exp = Atomizer.makeAtomizer(exp, null);
        }
        if (Cardinality.allowsMany(exp.getCardinality())) {
            exp = FirstItemExpression.makeFirstItemExpression(exp);
        }
        if (!th.isSubType(exp.getItemType(), BuiltInAtomicType.STRING)) {
            exp = new AtomicSequenceConverter(exp, BuiltInAtomicType.STRING);
            ((AtomicSequenceConverter) exp).allocateConverterStatically(env.getConfiguration(), false);
        }
        return exp;
    }

}

