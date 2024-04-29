////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.value.StringValue;

/**
 * A ATokenIterator is an iterator over the strings that result from tokenizing a string using a regular expression
 */

public class ATokenIterator implements AtomicIterator {

    private final UnicodeString input;
    private final REMatcher matcher;
    private StringValue current;
    private int prevEnd;


    /**
     * Construct an ATokenIterator.
     */

    public ATokenIterator(UnicodeString input, REMatcher matcher) {
        this.input = input;
        this.matcher = matcher;
        prevEnd = 0;
    }

    @Override
    public StringValue next() {
        if (prevEnd < 0) {
            current = null;
            return null;
        }

        if (matcher.match(input, prevEnd)) {
            int start = matcher.getParenStart(0);
            current = new StringValue(input.substring(prevEnd, start));
            prevEnd = matcher.getParenEnd(0);
        } else {
            current = new StringValue(input.substring(prevEnd));
            prevEnd = -1;
        }
        return currentStringValue();
    }

    private StringValue currentStringValue() {
        return current;
    }

}

