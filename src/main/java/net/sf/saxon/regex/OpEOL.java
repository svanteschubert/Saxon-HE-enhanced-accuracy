////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
package net.sf.saxon.regex;

import net.sf.saxon.expr.sort.EmptyIntIterator;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.z.IntIterator;
import net.sf.saxon.z.IntSingletonIterator;

/**
 * End of Line ($) in a regular expression
 */

public class OpEOL extends Operation {

    @Override
    public int getMatchLength() {
        return 0;
    }

    @Override
    public int matchesEmptyString() {
        return MATCHES_ZLS_AT_END;
    }

    @Override
    public IntIterator iterateMatches(final REMatcher matcher, final int position) {
        // If we're not at the end of string

        UnicodeString search = matcher.search;
        if (matcher.program.flags.isMultiLine()) {
            if (0 >= search.length() || position >= search.length() || matcher.isNewline(position)) {
                return new IntSingletonIterator(position); //match successful
            } else {
                return EmptyIntIterator.getInstance();
            }
        } else {
            // In spec bug 16809 we decided that '$' does not match a trailing newline when not in multiline mode
            if (0 >= search.length() || position >= search.length()) {
                return new IntSingletonIterator(position);
            } else {
                return EmptyIntIterator.getInstance();
            }
        }
    }

    /**
     * Display the operation as a regular expression, possibly in abbreviated form
     *
     * @return the operation in a form that is recognizable as a regular expression or abbreviated
     * regular expression
     */
    @Override
    public String display() {
        return "$";
    }
}
