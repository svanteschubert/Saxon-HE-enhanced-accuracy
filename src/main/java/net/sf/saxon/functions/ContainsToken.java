////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.Whitespace;

/**
 * Implements the fn:contains-token() function with the collation already bound.
 * This function was introduced in XPath 3.1
 */
public class ContainsToken extends CollatingFunctionFixed  {

    @Override
    public boolean isSubstringMatchingFunction() {
        return true;
    }

    private static boolean containsToken(SequenceIterator arg0, UnicodeString arg1, StringCollator collator) throws XPathException {
        if (arg1 == null) {
            return false;
        }
        UnicodeString search = Whitespace.trim(arg1);
        if (search.isEmpty()) {
            return false;
        }
        for (Item item; (item = arg0.next()) != null; ) {
            SequenceIterator tokens = new Whitespace.Tokenizer(item.getUnicodeStringValue());
            for (Item token; (token = tokens.next()) != null; ) {
                if (collator.comparesEqual(search, token.getUnicodeStringValue())) {
                    tokens.close();
                    arg0.close();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Dynamic evaluation
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences.
     *
     * @return true if the search token is present in the input
     * @throws XPathException if a dynamic error occurs
     */

    @Override
    public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        return BooleanValue.get(
            containsToken(arguments[0].iterate(), arguments[1].head().getUnicodeStringValue(), getStringCollator()));

    }

}

