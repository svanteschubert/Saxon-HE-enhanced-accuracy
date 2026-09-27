////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.arrays;

import net.sf.saxon.ma.MapOrArray;
import net.sf.saxon.ma.Parcel;
import net.sf.saxon.ma.zeno.ZenoChain;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.Genre;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.tree.iter.SequenceIteratorOverJavaIterator;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.z.IntSet;

/**
 * Interface supported by different implementations of an XDM array item.
 *
 * <p>Saxon uses two main implementations of this interface: {@link SimpleArrayItem}, which is a wrapper
 * over a Java {@code List} (which is assumed immutable), and {@link ImmutableArrayItem}, which is a wrapper
 * over a Saxon {@link ZenoChain}, permitting efficient append and prepend operations without copying the
 * entire content. Many of the expressions and functions that create arrays use a learning strategy to
 * decide which implementation to use: if a significant number of {@code SimpleArrayItem} instances
 * are subsequently converted to an {@code ImmutableArrayItem}, then future evaluations of the same
 * expression will produce an {@code ImmutableArrayItem} directly.</p>
 */
public abstract class ArrayItem extends MapOrArray implements FunctionItem {

    /**
     * Ask whether this function item is an array
     *
     * @return true if this function item is an array, otherwise false
     */

    public final boolean isArray() {
        return true;
    }


    /**
     * Ask whether this function item is a map
     *
     * @return false (it is not a map)
     */
    @Override
    public final boolean isMap() {
        return false;
    }

    /**
     * Get a member of the array
     *
     * @param index the position of the member to retrieve (zero-based)
     * @return the value at the given position.
     * @throws IndexOutOfBoundsException if the index is out of range
     */

    public abstract GroundedValue get(int index);

    /**
     * Get a member of the array at a specific offset, or an empty sequence
     * if the supplied offset is out of range
     *
     * @param index the position of the member to retrieve (zero-based)
     * @return the value at the given position, or an empty sequence
     * if the index is out of range
     */

    public GroundedValue getIfPresent(int index) {
        if (index >= 0 && index < arrayLength()) {
            return get(index);
        } else {
            return EmptySequence.INSTANCE;
        }
    }


    /**
     * Replace a member of the array
     *
     * @param index the position of the member to replace (zero-based)
     * @param newValue the replacement value
     * @return the value at the given position.
     * @throws IndexOutOfBoundsException if the index is out of range
     */

    public abstract ArrayItem put(int index, GroundedValue newValue);

    /**
     * Get the number of members in the array
     *
     * <p>Note: the {@link #getLength() method always returns 1, because an array is an item}</p>
     *
     * @return the number of members in this array.
     */

    public abstract int arrayLength();

    /**
     * Ask whether the array is empty
     *
     * @return true if and only if the size of the array is zero
     */
    
    public boolean isEmpty() {
        return arrayLength() == 0;
    }

    /**
     * Get the list of all members of the array
     * @return an iterator over the members of the array
     */

    public abstract Iterable<GroundedValue> members();

    /**
     * Get an iterator over the members of the array, each represented as a {@link Parcel}
     *
     * @return an {@link SequenceIterator} over the members of the array, represented as parcels
     */
    public SequenceIterator parcels() {
        return new SequenceIteratorOverJavaIterator<GroundedValue>(
                members().iterator(),
                (member, position) -> new Parcel(member));
    }

    /**
     * Add a member to this array
     *
     * @param newMember the member to be added
     * @return the new array, comprising the members of this array and then
     * one additional member.
     */

    public abstract ArrayItem append(GroundedValue newMember);

    /**
     * Concatenate this array with another
     * @param other the second array
     * @return the concatenation of the two arrays; that is, an array
     * containing first the members of this array, and then the members of the other array
     */

    public abstract ArrayItem concat(ArrayItem other);

    /**
     * Remove a member from the array
     *
     *
     * @param index  the position of the member to be removed (zero-based)
     * @return a new array in which the requested member has been removed.
     * @throws IndexOutOfBoundsException if index is out of range
     */

    public abstract ArrayItem remove(int index);

    /**
     * Remove zero or more members from the array
     *
     * @param positions the positions of the members to be removed (zero-based).
     *                  A value that is out of range is ignored.
     * @return a new array in which the requested member has been removed
     * @throws IndexOutOfBoundsException if any of the positions is out of range
     */

    public abstract ArrayItem removeSeveral(IntSet positions);

    /**
     * Get a sub-array given a start and end position
     * @param start the start position (zero based)
     * @param end the end position (the position of the first item not to be returned)
     *            (zero based)
     * @return a new array item containing the sub-array
     * @throws IndexOutOfBoundsException if start, or start+end, is out of range
     */

    public abstract ArrayItem subArray(int start, int end);

    /**
     * Insert a new member into an array
     * @param position the 0-based position that the new item will assume
     * @param member the new member to be inserted
     * @throws IndexOutOfBoundsException if position is out of range
     * @return a new array item with the new member inserted
     */

    public abstract ArrayItem insert(int position, GroundedValue member);

    /**
     * Get the lowest common item type of the members of the array
     * @param th the type hierarchy
     * @return the most specific type to which all the members belong.
     */

    public abstract SequenceType getMemberType(TypeHierarchy th);

    /**
     * Provide a short string showing the contents of the item, suitable
     * for use in error messages
     *
     * @return a depiction of the item suitable for use in error messages
     */
    @Override
    public String toShortString() {
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
    public final Genre getGenre() {
        return Genre.ARRAY;
    }
    
}

// Copyright (c) 2014-2026 Saxonica Limited
