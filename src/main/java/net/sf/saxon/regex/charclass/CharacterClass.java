////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.regex.charclass;

import net.sf.saxon.z.IntSet;

import java.util.function.IntPredicate;

/**
 * A character class represents a set of characters for regex matching purposes. It extends IntPredicate,
 * so there is a mechanism for testing whether a particular codepoint is a member of the class. In
 * addition it provides a method for testing whether two classes are disjoint, which is used when
 * optimizing regular expressions.
 */

public interface CharacterClass extends IntPredicate {

    /**
     * Ask whether this character class is known to be disjoint with another character class
     * (that is, the two classes have no characters in common). If in doubt, return false.
     * @param other the other character class
     * @return true if the character classes are known to be disjoint; false if there may
     * be characters in common between the two classes
     */

    boolean isDisjoint(CharacterClass other);

    /**
     * Get the set of matching characters if available. If not available, return null
     */

    IntSet getIntSet();
}
