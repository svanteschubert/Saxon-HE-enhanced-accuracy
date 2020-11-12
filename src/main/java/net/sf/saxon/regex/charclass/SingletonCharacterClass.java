////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.regex.charclass;

import net.sf.saxon.z.IntSet;
import net.sf.saxon.z.IntSingletonSet;

/**
 * A character class represents a set of characters for regex matching purposes. A singleton
 * character class matches exactly one character
 */

public class SingletonCharacterClass implements CharacterClass {

    private int codepoint;

    public SingletonCharacterClass(int codepoint) {
        this.codepoint = codepoint;
    }

    @Override
    public boolean test(int value) {
        return value == codepoint;
    }

    @Override
    public boolean isDisjoint(CharacterClass other) {
        return !other.test(codepoint);
    }

    public int getCodepoint() {
        return codepoint;
    }

    @Override
    public IntSet getIntSet() {
        return new IntSingletonSet(codepoint);
    }


}
