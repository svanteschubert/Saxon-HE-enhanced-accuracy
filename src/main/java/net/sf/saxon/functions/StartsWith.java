////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.lib.SubstringMatcher;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.StringValue;


/**
 * Implements the fn:starts-with() function, with the collation already known
 */
public class StartsWith extends CollatingFunctionFixed {

    @Override
    public boolean isSubstringMatchingFunction() {
        return true;
    }

    /**
     * Allow the function to create an optimized call based on the values of the actual arguments
     *
     * @param visitor     the expression visitor
     * @param contextInfo information about the context item
     * @param arguments   the supplied arguments to the function call. Note: modifying the contents
     *                    of this array should not be attempted, it is likely to have no effect.
     * @return either a function call on this function, or an expression that delivers
     * the same result, or null indicating that no optimization has taken place
     * @throws XPathException if an error is detected
     */
    @Override
    public Expression makeOptimizedFunctionCall(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, final Expression... arguments) throws XPathException {
        if (getStringCollator() == CodepointCollator.getInstance()) {
            // Performance fast path: bug 3209
            return new SystemFunctionCall.Optimized(this, arguments) {

                @Override
                public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
                    String s0 = getArg(0).evaluateAsString(context).toString();
                    String s1 = getArg(1).evaluateAsString(context).toString();
                    return s0.startsWith(s1);
                }
            };
        } else {
            return super.makeOptimizedFunctionCall(visitor, contextInfo, arguments);
        }
    }


    public static boolean startsWith(StringValue arg0, StringValue arg1, SubstringMatcher collator) {
        if (arg1 == null || arg1.isZeroLength() || collator.comparesEqual(arg1.getPrimitiveStringValue(), "")) {
            return true;
        }

        if (arg0 == null || arg0.isZeroLength()) {
            return false;
        }

        String s0 = arg0.getStringValue();
        String s1 = arg1.getStringValue();

        return collator.startsWith(s0, s1);
    }

    /**
     * Evaluate the function
     */

    @Override
    public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        StringValue s0 = (StringValue) arguments[0].head();
        StringValue s1 = (StringValue) arguments[1].head();
        return BooleanValue.get(startsWith(s0, s1, (SubstringMatcher)getStringCollator()));
    }

    @Override
    public String getCompilerName() {
        return "StartsWithCompiler";
    }

}

