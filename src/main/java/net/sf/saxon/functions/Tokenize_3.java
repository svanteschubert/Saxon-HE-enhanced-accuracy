////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.EmptySequence;


/**
 * This class implements the 3-argument tokenize() function for regular expression matching. This returns a
 * sequence of strings representing the unmatched substrings: the separators which match the
 * regular expression are not returned.
 */

public class Tokenize_3 extends RegexFunction {

    @Override
    protected boolean allowRegexMatchingEmptyString() {
        return false;
    }

    /**
     * Evaluate the expression dynamically
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        AtomicValue sv = (AtomicValue) arguments[0].head();
        if (sv == null) {
            return EmptySequence.getInstance();
        }
        CharSequence input = sv.getStringValueCS();
        if (input.length() == 0) {
            return EmptySequence.getInstance();
        }
        RegularExpression re = getRegularExpression(arguments);
        return SequenceTool.toLazySequence(re.tokenize(input));
    }
}

