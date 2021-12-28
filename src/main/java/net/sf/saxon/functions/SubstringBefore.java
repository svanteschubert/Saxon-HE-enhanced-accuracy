////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.lib.SubstringMatcher;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;


/**
 * Implements the fn:substring-before() function with the collation already known
 */
public class SubstringBefore extends CollatingFunctionFixed {

    @Override
    public boolean isSubstringMatchingFunction() {
        return true;
    }

    private static StringValue substringBefore(StringValue arg0, StringValue arg1, SubstringMatcher collator) {
        String s0 = arg0.getStringValue();
        String s1 = arg1.getStringValue();
        StringValue result = new StringValue(collator.substringBefore(s0, s1));
        if (arg0.isKnownToContainNoSurrogates()) {
            result.setContainsNoSurrogates();
        }
        return result;
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public StringValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        StringValue arg1 = (StringValue) arguments[1].head();
        if (arg1 == null || arg1.isZeroLength()) {
            return StringValue.EMPTY_STRING;
        }

        StringValue arg0 = (StringValue) arguments[0].head();
        if (arg0 == null || arg0.isZeroLength()) {
            return StringValue.EMPTY_STRING;
        }

        StringCollator collator = getStringCollator();
        return substringBefore(arg0, arg1, (SubstringMatcher)collator);
    }

    @Override
    public String getCompilerName() {
        return "SubstringBeforeCompiler";
    }

}

