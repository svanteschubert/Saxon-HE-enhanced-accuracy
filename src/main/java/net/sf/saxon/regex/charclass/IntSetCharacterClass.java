////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.regex.charclass;

import net.sf.saxon.z.IntSet;

/**
 * A character class represents a set of characters for regex matching purposes. An
 * IntSetCharacterClass is a character class represented by a set of integer
 * codepoints
 */

public class IntSetCharacterClass implements CharacterClass {

    private IntSet intSet;

    public IntSetCharacterClass(IntSet intSet) {
        this.intSet = intSet;
    }

    @Override
    public IntSet getIntSet() {
        return intSet;
    }

    @Override
    public boolean test(int value) {
        return intSet.contains(value);
    }

    /**
     * Ask whether this character class is known to be disjoint with another character class
     * (that is, the two classes have no characters in common). If in doubt, return false.
     * @param other the other character class
     * @return true if the character classes are known to be disjoint; false if there may
     * be characters in common between the two classes
     */

    @Override
    public boolean isDisjoint(CharacterClass other) {
        if (other instanceof IntSetCharacterClass) {
            return intSet.intersect(((IntSetCharacterClass) other).intSet).isEmpty();
        } else if (other instanceof InverseCharacterClass) {
            return other.isDisjoint(this);
        } else {
            return false;
        }
    }
}
