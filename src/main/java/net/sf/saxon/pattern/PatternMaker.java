////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.trans.XPathException;

/**
 * This is a singleton class used to convert an expression to an equivalent pattern.
 * This version of the class is used to generate conventional XSLT match patterns;
 * there is another version used to generate patterns suitable for streamed evaluation
 * in Saxon-EE.
 */
public class PatternMaker {

    /**
     * Static factory method to make a pattern by converting an expression. The supplied
     * expression is the equivalent expression to the pattern, in the sense that it takes
     * the same syntactic form.
     * <p>Note that this method does NOT check all the rules for XSLT patterns; it deliberately allows
     * a (slightly) wider class of expressions to be converted than XSLT allows.</p>
     * <p>The expression root() at the start of the expression has a special meaning: it refers to
     * the root of the subtree in which the pattern must match, which can be supplied at run-time
     * during pattern matching. This is used for patterns that represent streamable path expressions.</p>
     *
     * @param expression the expression to be converted, which must already have been simplified and
     *                   type-checked
     * @param config     the Saxon configuration
     * @param is30       set to true if XSLT 3.0 syntax is to be accepted
     * @return the compiled pattern
     * @throws net.sf.saxon.trans.XPathException
     *          if the expression cannot be converted
     */

    /*@NotNull*/
    public static Pattern fromExpression(Expression expression, Configuration config, boolean is30) throws XPathException {
        Pattern result = expression.toPattern(config);
        ExpressionTool.copyLocationInfo(expression, result);
//        result.setOriginalText(expression.toString());
//        result.setSystemId(expression.getSystemId());
//        result.setLineNumber(expression.getLineNumber());
        //result.setExecutable(expression.getExecutable());
        return result;
    }

    public static int getAxisForPathStep(Expression step) throws XPathException {
        if (step instanceof AxisExpression) {
            return AxisInfo.inverseAxis[((AxisExpression) step).getAxis()];
        } else if (step instanceof FilterExpression) {
            return getAxisForPathStep(((FilterExpression) step).getSelectExpression());
        } else if (step instanceof FirstItemExpression) {
            return getAxisForPathStep(((FirstItemExpression) step).getBaseExpression());
        } else if (step instanceof SubscriptExpression) {
            return getAxisForPathStep(((SubscriptExpression) step).getBaseExpression());
        } else if (step instanceof SlashExpression) {
            return getAxisForPathStep(((SlashExpression) step).getFirstStep());
        } else if (step instanceof ContextItemExpression) {
            return AxisInfo.SELF;
        } else {
            throw new XPathException("The path in a pattern must contain simple steps");
        }
    }
}

