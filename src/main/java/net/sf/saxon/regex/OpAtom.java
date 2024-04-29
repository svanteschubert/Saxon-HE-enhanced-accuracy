////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.expr.sort.EmptyIntIterator;
import net.sf.saxon.regex.charclass.CharacterClass;
import net.sf.saxon.regex.charclass.EmptyCharacterClass;
import net.sf.saxon.regex.charclass.IntSetCharacterClass;
import net.sf.saxon.regex.charclass.SingletonCharacterClass;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.z.IntHashSet;
import net.sf.saxon.z.IntIterator;
import net.sf.saxon.z.IntSet;
import net.sf.saxon.z.IntSingletonIterator;

/**
 * A match against a fixed string of any length, within a regular expression
 */

public class OpAtom extends Operation {
    private final UnicodeString atom;
    private final int len;

    OpAtom(UnicodeString atom) {
        this.atom = atom;
        this.len = atom.length32();
    }

    UnicodeString getAtom() {
        return atom;
    }

    @Override
    public int getMatchLength() {
        return len;
    }

    @Override
    public int matchesEmptyString() {
        return len == 0 ? MATCHES_ZLS_ANYWHERE : MATCHES_ZLS_NEVER;
    }

    @Override
    public CharacterClass getInitialCharacterClass(boolean caseBlind) {
        if (len == 0) {
            return EmptyCharacterClass.getInstance();
        } else if (caseBlind) {
            IntSet set;
            int ch = atom.codePointAt(0);
            int[] variants = CaseVariants.getCaseVariants(ch);
            if (variants.length > 0) {
                set = new IntHashSet(variants.length);
                set.add(ch);
                for (int v : variants) {
                    set.add(v);
                }
                return new IntSetCharacterClass(set);
            }
        }
        return new SingletonCharacterClass(atom.codePointAt(0));
    }

    @Override
    public IntIterator iterateMatches(REMatcher matcher, int position) {
        UnicodeString in = matcher.search;
        if (position + len > in.length()) {
            return EmptyIntIterator.getInstance();
        }
        if (matcher.program.flags.isCaseIndependent()) {
            for (int i = 0; i < len; i++) {
                if (!matcher.equalCaseBlind(in.codePointAt(position + i), atom.codePointAt(i))) {
                    return EmptyIntIterator.getInstance();
                }
            }
        } else {
            for (int i = 0; i < len; i++) {
                if (in.codePointAt(position + i) != atom.codePointAt(i)) {
                    return EmptyIntIterator.getInstance();
                }
            }
        }
        return new IntSingletonIterator(position + len);
    }

    @Override
    public String display() {
        return atom.toString();
    }
}

