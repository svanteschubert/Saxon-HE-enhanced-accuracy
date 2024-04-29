////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.expr.sort.EmptyIntIterator;
import net.sf.saxon.regex.charclass.CharacterClass;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.z.*;

/**
 * A match of a single character in the input against a set of permitted characters
 */

public class OpCharClass extends Operation {
    private final IntPredicateProxy predicate;

    OpCharClass(IntPredicateProxy predicate) {
        this.predicate = predicate;
    }

    public IntPredicateProxy getPredicate() {
        return predicate;
    }

    @Override
    public int getMatchLength() {
        return 1;
    }

    @Override
    public int matchesEmptyString() {
        return MATCHES_ZLS_NEVER;
    }

    @Override
    public CharacterClass getInitialCharacterClass(boolean caseBlind) {
        if (predicate instanceof CharacterClass) {
            return (CharacterClass) predicate;
        } else {
            return super.getInitialCharacterClass(caseBlind);
        }
    }

    @Override
    public IntIterator iterateMatches(REMatcher matcher, int position) {
        UnicodeString in = matcher.search;
        if (position < in.length() && predicate.test(in.codePointAt(position))) {
            return new IntSingletonIterator(position + 1);
        } else {
            return EmptyIntIterator.getInstance();
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
        if (predicate instanceof IntSetPredicate) {
            IntSet s = ((IntSetPredicate) predicate).getIntSet();
            if (s instanceof IntSingletonSet) {
                return "" + (char) ((IntSingletonSet) s).getMember();
            } else if (s instanceof IntRangeSet) {
                StringBuilder fsb = new StringBuilder(64);
                IntRangeSet irs = (IntRangeSet) s;
                fsb.append("[");
                for (int i = 0; i < irs.getNumberOfRanges(); i++) {
                    fsb.append((char) irs.getStartPoints()[1]);
                    fsb.append("-");
                    fsb.append((char) irs.getEndPoints()[1]);
                }
                fsb.append("[");
                return fsb.toString();
            } else {
                return "[....]";
            }
        } else {
            return "[....]";
        }
    }
}

