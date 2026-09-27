////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.str.TwineBuilder;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.NumericValue;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.z.IntPredicateProxy;

/**
 * This class implements the function fn:codepoints-to-string()
 */

public class CodepointsToString extends SystemFunction implements Callable {

    /**
     * Construct a UnicodeString corresponding to a given sequence of Unicode code values
     *
     * @param tb builder object to contain the constructed string
     * @param chars   iterator delivering the characters as integer values
     * @param checker used to test whether a character is valid in the appropriate XML version
     * @throws net.sf.saxon.trans.XPathException
     *          if any of the integers is not the codepoint of a valid XML character
     */

    private static TwineBuilder unicodeToString(TwineBuilder tb, SequenceIterator chars, IntPredicateProxy checker) throws XPathException {
        while (true) {
            NumericValue nextInt = (NumericValue) chars.next();
            if (nextInt == null) {
                return tb;
            }
            long next = nextInt.longValue();
            if (next < 0 || next > Integer.MAX_VALUE || !checker.test((int) next)) {
                throw new XPathException("codepoints-to-string(): invalid XML character [x" + Integer.toHexString((int) next) + ']', "FOCH0001");
            }
            tb = tb.append((int) next);
        }
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
        TwineBuilder tb = TwineBuilder.make(256);
        SequenceIterator chars = arguments[0].iterate();
        tb = unicodeToString(tb, chars, context.getConfiguration().getValidCharacterChecker());
        return new StringValue(tb.toUnicodeString());
    }

    @Override
    public String getStreamerName() {
        return "CodepointsToString";
    }

}

