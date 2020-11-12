////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.value.StringValue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

//import java.util.regex.Matcher;
//import java.util.regex.Pattern;

/**
 * A JTokenIterator is an iterator over the strings that result from tokenizing a string using
 * a regular expression, in this case a regular expression evaluated using the JDK regex engine
 */

public class JTokenIterator implements AtomicIterator<StringValue> {

    private CharSequence input;
    private Pattern pattern;
    private Matcher matcher;
    /*@Nullable*/ private CharSequence current;
    private int prevEnd = 0;


    /**
     * Construct a JTokenIterator.
     */

    public JTokenIterator(CharSequence input, Pattern pattern) {
        this.input = input;
        this.pattern = pattern;
        matcher = pattern.matcher(input);
        prevEnd = 0;
    }

    @Override
    public StringValue next() {
        if (prevEnd < 0) {
            current = null;
            return null;
        }

        if (matcher.find()) {
            current = input.subSequence(prevEnd, matcher.start());
            prevEnd = matcher.end();
        } else {
            current = input.subSequence(prevEnd, input.length());
            prevEnd = -1;
        }
        return StringValue.makeStringValue(current);
    }

}

