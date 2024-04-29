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
 * A back-reference in a regular expression
 */

public class OpBackReference extends Operation {

    int groupNr;

    OpBackReference(int groupNr) {
        this.groupNr = groupNr;
    }

    /**
     * Ask whether the regular expression is known, after static analysis, to match a
     * zero-length string
     *
     * @return false. Returning true means that
     * the expression is known statically to match ""; returning false means that this cannot
     * be determined statically; it does not mean that the expression does not match "".
     * We cannot do the analysis statically where back-references are involved, so we return false.
     */

    @Override
    public int matchesEmptyString() {
        return 0; // no information available
    }

    @Override
    public IntIterator iterateMatches(final REMatcher matcher, final int position) {
        // Get the start and end of the backref
        int s = matcher.startBackref[groupNr];
        int e = matcher.endBackref[groupNr];

        // We don't know the backref yet
        if (s == -1 || e == -1) {
            return EmptyIntIterator.getInstance();
        }

        // The backref is empty size
        if (s == e) {
            return new IntSingletonIterator(position);
        }

        // Get the length of the backref
        int l = e - s;

        // If there's not enough input left, give up.
        UnicodeString search = matcher.search;
        if (position + l - 1 >= search.length()) {
            return EmptyIntIterator.getInstance();
        }

        // Case fold the backref?
        if (matcher.program.flags.isCaseIndependent()) {
            // Compare backref to input
            for (int i = 0; i < l; i++) {
                if (!matcher.equalCaseBlind(search.codePointAt(position + i), search.codePointAt(s + i))) {
                    return EmptyIntIterator.getInstance();
                }
            }
        } else {
            // Compare backref to input
            for (int i = 0; i < l; i++) {
                if (search.codePointAt(position + i) != search.codePointAt(s + i)) {
                    return EmptyIntIterator.getInstance();
                }
            }
        }
        return new IntSingletonIterator(position + l);
    }

    /**
     * Display the operation as a regular expression, possibly in abbreviated form
     *
     * @return the operation in a form that is recognizable as a regular expression or abbreviated
     * regular expression
     */
    @Override
    public String display() {
        return "\\" + groupNr;
    }
}

