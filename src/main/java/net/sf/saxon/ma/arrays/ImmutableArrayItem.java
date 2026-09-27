////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.arrays;

import net.sf.saxon.ma.zeno.ZenoChain;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.z.IntIterator;
import net.sf.saxon.z.IntSet;

import java.util.Arrays;

/**
 * Implementation of ArrayItem backed by a persistent immutable array, so that operations
 * that "update" the array do not have to copy the whole array
 */

public class ImmutableArrayItem extends AbstractArrayItem {

    private final ZenoChain<GroundedValue> vector;

    /**
     * Create an <code>ImmutableArrayItem</code> as a copy of a supplied
     * <code>SimpleArrayItem</code>
     * @param other the supplied <code>SimpleArrayItem</code>
     */

    public ImmutableArrayItem(ArrayItem other) {
        this.vector = new ZenoChain<GroundedValue>().addAll(other.members());
    }

    /**
     * Create an <code>ImmutableArrayItem</code> , supplying the members of the array as a
     * list (or other iterable) of values
     *
     * @param members the supplied collection of members.
     */

    public ImmutableArrayItem(Iterable<GroundedValue> members ) {
        this.vector = new ZenoChain<GroundedValue>().addAll(members);
    }

    private ImmutableArrayItem(ZenoChain<GroundedValue> vector) {
        this.vector = vector;
    }

    /**
     * Construct an array whose members are all single items, from the items supplied by
     * a {@link SequenceIterator}
     * @param iter delivers the items to make up the array
     * @return an array whose members are single items.
     */

    public static ImmutableArrayItem from(SequenceIterator iter) {
        ZenoChain<GroundedValue> content = new ZenoChain<>();
        for (Item item; (item = iter.next()) != null; ) {
            content = content.add(item);
        }
        return new ImmutableArrayItem(content);
    }
    
    /**
     * Get a member of the array
     *
     * @param index the position of the member to retrieve (zero-based)
     * @return the value at the given position.
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    @Override
    public GroundedValue get(int index) {
        return vector.get(index);
    }

    /**
     * Replace a member of the array
     *
     * @param index    the position of the member to replace (zero-based)
     * @param newValue the replacement value
     * @return the value at the given position.
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    @Override
    public ArrayItem put(int index, GroundedValue newValue)  {
        ZenoChain<GroundedValue> v2 = vector.replace(index, newValue);
        return v2 == vector ? this : new ImmutableArrayItem(v2);
    }

    /**
     * Insert a new member into an array
     *
     * @param position the 0-based position that the new item will assume
     * @param member   the new member to be inserted
     * @return a new array item with the new member inserted
     * @throws IndexOutOfBoundsException if position is out of range
     */
    @Override
    public ArrayItem insert(int position, GroundedValue member) {
        ZenoChain<GroundedValue> v2 = vector.insert(position, member);
        return new ImmutableArrayItem(v2);
    }

    /**
     * Add a member to this array
     *
     * @param newMember the member to be added
     * @return the new array, comprising the members of this array and then
     * one additional member.
     */
    @Override
    public ArrayItem append(GroundedValue newMember) {
        ZenoChain<GroundedValue> v2 = vector.add(newMember);
        return new ImmutableArrayItem(v2);
    }

    /**
     * Get the number of members in the array
     * <p>Note: the {@link #getLength() method always returns 1, because an array is an item}</p>
     *
     * @return the number of members in this array.
     */
    @Override
    public int arrayLength() {
        return vector.size();
    }

    /**
     * Ask whether the array is empty
     *
     * @return true if and only if the size of the array is zero
     */
    @Override
    public boolean isEmpty() {
        return vector.isEmpty();
    }

    /**
     * Get the list of all members of the array
     *
     * @return an iterator over the members of the array
     */
    @Override
    public Iterable<GroundedValue> members() {
        return vector;
    }

    /**
     * Get a subarray given a start and end position
     *
     * @param start the start position (zero based)
     * @param end   the end position (the position of the first item not to be returned)
     *              (zero based)
     * @throws IndexOutOfBoundsException if start, or start+end, is out of range
     */
    @Override
    public ArrayItem subArray(int start, int end) {
        return new ImmutableArrayItem(vector.subList(start, end));
    }

    /**
     * Concatenate this array with another
     *
     * @param other the second array
     * @return the concatenation of the two arrays; that is, an array
     * containing first the members of this array, and then the members of the other array
     */
    @Override
    public ArrayItem concat(ArrayItem other) {
        if (other.arrayLength() == 0) {
            return this;
        }
        ZenoChain<GroundedValue> otherChain;
        if (other instanceof ImmutableArrayItem) {
            otherChain = ((ImmutableArrayItem) other).vector;
        } else {
            otherChain = new ImmutableArrayItem(other).vector;
        }
        ZenoChain<GroundedValue> v2 = vector.addAll(otherChain);
        return new ImmutableArrayItem(v2);
    }

    /**
     * Remove a member from the array
     *
     * @param index the position of the member to be removed (zero-based)
     * @return a new array in which the requested member has been removed.
     * @throws IndexOutOfBoundsException if index is out of range
     */
    @Override
    public ArrayItem remove(int index) {
        ZenoChain<GroundedValue> v2 = vector.remove(index);
        return v2 == vector ? this : new ImmutableArrayItem(v2);
    }

    /**
     * Remove zero or more members from the array
     *
     * @param positions the positions of the members to be removed (zero-based).
     *                  A value that is out of range is ignored.
     * @return a new array in which the requested member has been removed
     */
    @Override
    public ArrayItem removeSeveral(IntSet positions) {
        int[] p = new int[positions.size()];
        int i = 0;
        IntIterator ii = positions.iterator();
        while (ii.hasNext()) {
            p[i++] = ii.next();
        }
        Arrays.sort(p);
        ZenoChain<GroundedValue> v2 = vector;
        for (int j=p.length-1; j>=0; j--) {
            v2 = v2.remove(p[j]);
        }
        return v2 == vector ? this : new ImmutableArrayItem(v2);
    }
    
}

