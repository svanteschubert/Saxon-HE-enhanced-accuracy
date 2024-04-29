////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.expr.sort.EmptyIntIterator;
import net.sf.saxon.z.IntIterator;
import net.sf.saxon.z.IntSingletonIterator;

/**
 * End of program in a regular expression
 */

public class OpEndProgram extends Operation {

    @Override
    public int getMatchLength() {
        return 0;
    }

    @Override
    public int matchesEmptyString() {
        return MATCHES_ZLS_ANYWHERE;
    }

    @Override
    public IntIterator iterateMatches(final REMatcher matcher, final int position) {
        // An anchored match is successful only if we are at the end of the string.
        // Otherwise, match has succeeded unconditionally
        if (matcher.anchoredMatch) {
            if (position >= matcher.search.length()) {
                return new IntSingletonIterator(position);
            } else {
                return EmptyIntIterator.getInstance();
            }
        } else {
            matcher.setParenEnd(0, position);
            return new IntSingletonIterator(position);
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
        return "\\Z";
    }
}
