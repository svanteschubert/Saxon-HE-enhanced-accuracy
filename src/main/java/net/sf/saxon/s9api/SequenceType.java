////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

/**
 * A SequenceType is the combination of an ItemType and an OccurrenceIndicator
 */
public class SequenceType {

    private ItemType itemType;
    private OccurrenceIndicator occurrenceIndicator;

    /**
     * Constant representing the universal sequence type <code>item()*</code>, which permits any value
     */
    public final static SequenceType ANY = new SequenceType(ItemType.ANY_ITEM, OccurrenceIndicator.ZERO_OR_MORE);

    /**
     * Constant representing the sequence type <code>empty-sequence()</code>, which permits only one
     * value, namely the empty sequence
     */

    public final static SequenceType EMPTY = new SequenceType(ItemType.ERROR, OccurrenceIndicator.ZERO);

    /**
     * Construct a SequenceType
     *
     * @param itemType            the ItemType
     * @param occurrenceIndicator the permitted number of occurrences of the item in the sequence
     */

    private SequenceType(ItemType itemType, OccurrenceIndicator occurrenceIndicator) {
        this.itemType = itemType;
        this.occurrenceIndicator = occurrenceIndicator;
    }

    /**
     * Factory method to construct a SequenceType
     *
     * @param itemType            the ItemType
     * @param occurrenceIndicator the permitted number of occurrences of the item in the sequence
     * @return the constricted SequenceType
     */

    /*@NotNull*/
    public static SequenceType makeSequenceType(ItemType itemType, OccurrenceIndicator occurrenceIndicator) {
        return new SequenceType(itemType, occurrenceIndicator);
    }

    /**
     * Get the item type
     *
     * @return the item type
     */

    public ItemType getItemType() {
        return itemType;
    }

    /**
     * Get the occurrence indicator
     *
     * @return the occurrence indicator
     */

    public OccurrenceIndicator getOccurrenceIndicator() {
        return occurrenceIndicator;
    }

    /**
     * Test whether two SequenceType objects represent the same type
     *
     * @param other the other SequenceType object
     * @return true if the other object is a SequenceType representing the same type
     * @since 9.5
     */

    public final boolean equals(Object other) {
        return other instanceof SequenceType &&
                ((SequenceType) other).getOccurrenceIndicator().equals(getOccurrenceIndicator()) &&
                ((SequenceType) other).getItemType().equals(getItemType());
    }

    /**
     * Get a hash code with semantics corresponding to the equals() method
     *
     * @return the hash code
     * @since 9.5
     */

    public final int hashCode() {
        return getItemType().hashCode() ^ (getOccurrenceIndicator().hashCode() << 17);
    }

    /**
     * Get the underlying internal SequenceType
     * @return the equivalent instance of {@link net.sf.saxon.value.SequenceType}
     */

    @SuppressWarnings("WeakerAccess")
    public net.sf.saxon.value.SequenceType getUnderlyingSequenceType() {
        return net.sf.saxon.value.SequenceType.makeSequenceType(
                itemType.getUnderlyingItemType(), occurrenceIndicator.getCardinality());
    }

    /**
     * Factory method to construct a s9api {@code SequenceType} from an underlying
     *  instance of {@link net.sf.saxon.value.SequenceType}
     * @since 10.0
     */

    public static SequenceType fromUnderlyingSequenceType(
            Processor processor, net.sf.saxon.value.SequenceType st) {
        ItemTypeFactory factory = new ItemTypeFactory(processor);
        ItemType it = factory.exposeItemType(st.getPrimaryType());
        OccurrenceIndicator oc = OccurrenceIndicator.getOccurrenceIndicator(st.getCardinality());
        return makeSequenceType(it, oc);
    }

}
