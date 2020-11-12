////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.regex.charclass;

import net.sf.saxon.z.IntComplementSet;
import net.sf.saxon.z.IntSet;

/**
 * A character class represents a set of characters for regex matching purposes. An inverse
 * character class is the complement of another character class
 */

public class InverseCharacterClass implements CharacterClass {

    private CharacterClass complement;

    /**
     * Create the complement of a character class, that is, a character class containing exactly
     * those characters that are not included in the supplied character class
     * @param complement the class of which this one is the complement
     */

    public InverseCharacterClass(CharacterClass complement) {
        this.complement = complement;
    }

    /**
     * Get the character class of which this class is the complement
     * @return the complement of this character class
     */

    public CharacterClass getComplement() {
        return complement;
    }

    @Override
    public boolean test(int value) {
        return !complement.test(value);
    }

    @Override
    public boolean isDisjoint(CharacterClass other) {
        return other == complement;
    }

    @Override
    public IntSet getIntSet() {
        IntSet comp = complement.getIntSet();
        return comp == null ? null : new IntComplementSet(complement.getIntSet());
    }
}
