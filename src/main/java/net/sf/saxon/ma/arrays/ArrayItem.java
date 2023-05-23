////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.arrays;

import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.Genre;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.z.IntSet;

/**
 * Interface supported by different implementations of an XDM array item
 */
public interface ArrayItem extends Function {

    SequenceType SINGLE_ARRAY_TYPE =
            SequenceType.makeSequenceType(ArrayItemType.ANY_ARRAY_TYPE, StaticProperty.EXACTLY_ONE);

    /**
     * Get a member of the array
     *
     * @param index the position of the member to retrieve (zero-based)
     * @return the value at the given position.
     * @throws IndexOutOfBoundsException if the index is out of range
     */

    GroundedValue get(int index);

    /**
     * Replace a member of the array
     *
     * @param index the position of the member to replace (zero-based)
     * @param newValue the replacement value
     * @return the value at the given position.
     * @throws IndexOutOfBoundsException if the index is out of range
     */

    ArrayItem put(int index, GroundedValue newValue);

    /**
     * Get the number of members in the array
     *
     * <p>Note: the {@link #getLength() method always returns 1, because an array is an item}</p>
     *
     * @return the number of members in this array.
     */

    int arrayLength();

    /**
     * Ask whether the array is empty
     *
     * @return true if and only if the size of the array is zero
     */

    boolean isEmpty();

    /**
     * Get the list of all members of the array
     * @return an iterator over the members of the array
     */

    Iterable<GroundedValue> members();

    /**
     * Concatenate this array with another
     * @param other the second array
     * @return the concatenation of the two arrays; that is, an array
     * containing first the members of this array, and then the members of the other array
     */

    ArrayItem concat(ArrayItem other);

    /**
     * Remove a member from the array
     *
     *
     * @param index  the position of the member to be removed (zero-based)
     * @return a new array in which the requested member has been removed.
     * @throws IndexOutOfBoundsException if index is out of range
     */

    ArrayItem remove(int index);

    /**
     * Remove zero or more members from the array
     *
     * @param positions the positions of the members to be removed (zero-based).
     *                  A value that is out of range is ignored.
     * @return a new array in which the requested member has been removed
     * @throws IndexOutOfBoundsException if any of the positions is out of range
     */

    ArrayItem removeSeveral(IntSet positions);

    /**
     * Get a sub-array given a start and end position
     * @param start the start position (zero based)
     * @param end the end position (the position of the first item not to be returned)
     *            (zero based)
     * @return a new array item containing the sub-array
     * @throws IndexOutOfBoundsException if start, or start+end, is out of range
     */

    ArrayItem subArray(int start, int end);

    /**
     * Insert a new member into an array
     * @param position the 0-based position that the new item will assume
     * @param member the new member to be inserted
     * @throws IndexOutOfBoundsException if position is out of range
     * @return a new array item with the new member inserted
     */

    ArrayItem insert(int position, GroundedValue member);

    /**
     * Get the lowest common item type of the members of the array
     *
     * @return the most specific type to which all the members belong.
     */

    SequenceType getMemberType(TypeHierarchy th);

    /**
     * Provide a short string showing the contents of the item, suitable
     * for use in error messages
     *
     * @return a depiction of the item suitable for use in error messages
     */
    @Override
    default String toShortString() {
        StringBuilder sb = new StringBuilder();
        sb.append("array{");
        int count = 0;
        for (GroundedValue member : members()) {
            if (count++ > 2) {
                sb.append(" ...");
                break;
            }
            sb.append(member.toShortString());
            sb.append(", ");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Get the genre of this item
     *
     * @return the genre: specifically, {@link Genre#ARRAY}.
     */
    @Override
    default Genre getGenre() {
        return Genre.ARRAY;
    }

}

// Copyright (c) 2014-2020 Saxonica Limited
