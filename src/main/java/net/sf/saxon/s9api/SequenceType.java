////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.value.Cardinality;

/**
 * A {@code SequenceType} is the combination of an {@link ItemType} and an {@link OccurrenceIndicator}.
 *
 * <p>The most convenient way to obtain a {@code SequenceType} is often with
 * a method such as <code>ItemType.BOOLEAN.one()</code>, representing the
 * sequence type <code>xs:boolean</code>, or <code>ItemType.ANY_NODE.zeroOrMore()</code>
 * representing the type <code>node()*</code>.</p>
 */
public class SequenceType {

    private final ItemType itemType;
    private final OccurrenceIndicator occurrenceIndicator;

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
     * Test whether a supplied value is an instance of this SequenceType
     * @param value the value to be tested
     * @return true if <code>value</code> is an instance of this type, as defined by the XPath
     * <code>instance of</code> operator
     * @since 12.0
     */

    public boolean matches(XdmValue value) {
        return value.matches(this);
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
                itemType.getUnderlyingItemType(), Cardinality.staticPropertyFromOccurrenceIndicator(occurrenceIndicator));
    }

    /**
     * Factory method to construct a s9api {@code SequenceType} from an underlying
     *  instance of {@link net.sf.saxon.value.SequenceType}
     * @param processor the processor
     * @param st the internal SequenceType
     * @return the s9api SequenceType
     * @since 10.0
     */

    public static SequenceType fromUnderlyingSequenceType(
            Processor processor, net.sf.saxon.value.SequenceType st) {
        ItemTypeFactory factory = new ItemTypeFactory(processor);
        ItemType it = factory.exposeItemType(st.getPrimaryType());
        OccurrenceIndicator oc = Cardinality.getOccurrenceIndicatorForCardinality(st.getCardinality());
        return makeSequenceType(it, oc);
    }

}
