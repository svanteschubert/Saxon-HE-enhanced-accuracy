// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.jnode;

import net.sf.saxon.om.Item;
import net.sf.saxon.type.PlainType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.SequenceType;

import java.util.Optional;

/**
 * Represents the type jnode((), T) where T is a sequence type giving the type
 * of the JNode content; the node must be a root JNode (that is, its selector and parent
 * properties must be absent)
 */

public class RootJNodeType extends JNodeType {

    private final SequenceType valueType;


    public RootJNodeType(SequenceType valueType) {
        this.valueType = valueType;
    }

    public SequenceType getValueType() {
        return valueType;
    }

    /**
     * Test whether a given item conforms to this type
     *
     * @param item The item to be tested
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item) {
        return item instanceof JNode j
                && valueType.matches(j.getContent())
                && j.getParent() == null;
    }

    /**
     * Get the item type of the atomic values that will be produced when an item
     * of this type is atomized
     *
     * @return the best available item type of the atomic values that will be produced when an item
     * of this type is atomized, or null if it is known that atomization will throw an error.
     */
    @Override
    public PlainType getAtomizedItemType() {
        return getValueType().getPrimaryType().getAtomizedItemType();
    }

    /**
     * Ask whether values of this type are atomizable
     *
     * @param th the type hierarchy cache
     * @return true if some or all instances of this type can be successfully atomized; false
     * if no instances of this type can be atomized
     */
    @Override
    public boolean isAtomizable(TypeHierarchy th) {
        return getValueType().getPrimaryType().isAtomizable(th);
    }

    /**
     * Get extra diagnostic information about why a supplied item does not conform to this
     * item type, if available. If extra information is returned, it should be in the form of a complete
     * sentence, minus the closing full stop. No information should be returned for obvious cases.
     *
     * @param item the item that doesn't match this type
     * @param th   the type hierarchy cache
     * @return optionally, a message explaining why the item does not match the type
     */
    @Override
    public Optional<String> explainMismatch(Item item, TypeHierarchy th) {
        return Optional.empty();
    }

    /**
     * Return a string representation of this ItemType.
     *
     * @return the string representation
     */
    @Override
    public String toString() {
        return "jnode((), " + valueType.toString() + ")";
    }

    /**
     * Determine whether the content type (if present) is nillable
     *
     * @return true if the content test (when present) can match nodes that are nilled
     */
    @Override
    public boolean isNillable() {
        return true;
    }

    /**
     * Get an alphabetic code representing the type, or at any rate, the nearest built-in type
     * from which this type is derived. The codes are designed so that for any two built-in types
     * A and B, alphaCode(A) is a prefix of alphaCode(B) if and only if A is a supertype of B.
     *
     * @return the alphacode for the nearest containing built-in type. For example: for xs:string
     * return "AS", for xs:boolean "AB", for node() "N", for element() "NE", for map(*) "FM", for
     * array(*) "FA".
     */

    @Override
    public String getBasicAlphaCode() {
        return "JR";
    }
}

