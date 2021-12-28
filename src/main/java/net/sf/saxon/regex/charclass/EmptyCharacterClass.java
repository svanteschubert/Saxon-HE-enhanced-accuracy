////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.regex.charclass;

import net.sf.saxon.z.IntEmptySet;
import net.sf.saxon.z.IntSet;

/**
 * A character class represents a set of characters for regex matching purposes. The empty
 * character class matches no characters
 */

public class EmptyCharacterClass implements CharacterClass {

    private final static EmptyCharacterClass THE_INSTANCE = new EmptyCharacterClass();

    private final static InverseCharacterClass COMPLEMENT = new InverseCharacterClass(THE_INSTANCE);

    public static EmptyCharacterClass getInstance() {
        return THE_INSTANCE;
    }

    public static CharacterClass getComplement() {
        return COMPLEMENT;
    }

    private EmptyCharacterClass() {
    }

    @Override
    public boolean test(int value) {
        return false;
    }

    @Override
    public boolean isDisjoint(CharacterClass other) {
        // the empty set is disjoint with every other set including itself, in the sense that the
        // intersection of the two sets is empty
        return true;
    }

    @Override
    public IntSet getIntSet() {
        return IntEmptySet.getInstance();
    }
}
