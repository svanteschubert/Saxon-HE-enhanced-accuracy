////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.z.IntIterator;
import net.sf.saxon.z.IntSingletonIterator;

/**
 * Match empty string within a regular expression
 */

public class OpNothing extends Operation {

    @Override
    public IntIterator iterateMatches(final REMatcher matcher, final int position) {
        return new IntSingletonIterator(position);
    }

    @Override
    public int matchesEmptyString() {
        return MATCHES_ZLS_ANYWHERE;
    }

    @Override
    public int getMatchLength() {
        return 0;
    }

    /**
     * Display the operation as a regular expression, possibly in abbreviated form
     *
     * @return the operation in a form that is recognizable as a regular expression or abbreviated
     * regular expression
     */
    @Override
    public String display() {
        return "()";
    }
}

