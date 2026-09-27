////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.AscendingRangeIterator;
import net.sf.saxon.expr.DescendingRangeIterator;
import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.instruct.AbstractBlockIterator;
import net.sf.saxon.functions.Count;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.GroundedIterator;
import net.sf.saxon.tree.wrapper.VirtualNode;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import java.util.List;

/**
 * Utility class for manipulating sequences. Some of these methods should be regarded
 * as temporary scaffolding while the model is in transition.
 */
public class SequenceTool {

    /**
     * Constant returned by compareTo() method to indicate an indeterminate ordering between two values
     */

    public static final int INDETERMINATE_ORDERING = Integer.MIN_VALUE;

    /**
     * Produce a GroundedValue containing the same values as a supplied sequence.
     *
     * @param iterator the supplied sequence. The iterator may or may not be consumed as a result of
     *                 passing it to this method.
     * @return a GroundedValue containing the same items
     * @throws UncheckedXPathException if a failure occurs reading the input iterator
     */

    public static GroundedValue toGroundedValue(SequenceIterator iterator) {
        if (iterator instanceof GroundedIterator && ((GroundedIterator)iterator).isActuallyGrounded()) {
            return ((GroundedIterator) iterator).materialize();
        } else {
            return SequenceExtent.from(iterator).reduce();
        }
    }

    /**
     * Produce a Sequence containing the same values as a supplied sequence; the input is
     * read progressively as required, and saved in a buffer as it is read in case it is needed
     * again. But if the iterator is already backed by a grounded value, we return that value.
     *
     * @param iterator the supplied sequence. The iterator may or may not be consumed as a result of
     *                 passing it to this method.
     * @return a Sequence containing the same items
     * @throws XPathException if a failure occurs reading the input iterator
     */

    public static Sequence toMemoSequence(SequenceIterator iterator) throws XPathException {
        if (iterator instanceof EmptyIterator) {
            return EmptySequence.INSTANCE;
        } else if (iterator instanceof GroundedIterator && ((GroundedIterator) iterator).isActuallyGrounded()) {
            try {
                return toGroundedValue(iterator);
            } catch (UncheckedXPathException e) {
                throw e.getXPathException();
            }
        } else {
            return new MemoSequence(iterator);
        }
    }


    /**
     * Construct a sequence capable of returning the same items as an iterator,
     * without incurring the cost of evaluating the iterator and storing all
     * the items.
     *
     * @param iterator the supplied sequence. The iterator may or may not be consumed as a result of
     *                 passing it to this method.
     * @return a Sequence containing the same items as the supplied iterator
     * @throws XPathException if a failure occurs reading the input iterator
     */

    public static Sequence toLazySequence(SequenceIterator iterator) throws XPathException {
        if (iterator instanceof GroundedIterator && ((GroundedIterator) iterator).isActuallyGrounded() &&
                !(iterator instanceof AscendingRangeIterator) &&
                !(iterator instanceof DescendingRangeIterator)) {
            try {
                return toGroundedValue(iterator);
            } catch (UncheckedXPathException e) {
                throw e.getXPathException();
            }
        } else {
            return new LazySequence(iterator);
        }
    }

    /**
     * Ask whether a SequenceIterator supports the capability to call {@code getLength()} to establish
     * the number of items in the sequence.
     * @param iterator the iterator we are asking about
     * @return true if the iterator is a {@link LastPositionFinder} with this
     * capability. Note that some iterators implement this interface, but do not actually have the
     * capability to determine the length, because they delegate to another iterator.
     */

    public static boolean supportsGetLength(SequenceIterator iterator) {
        return iterator instanceof LastPositionFinder && ((LastPositionFinder)iterator).supportsGetLength();
    }

    /**
     * Get the number of items in the sequence identified by a {@link SequenceIterator}.
     * This method can only be used if {@link #supportsGetLength(SequenceIterator)} has been called
     * and returns true. The length is returned regardless of the current position of the iterator.
     * The state of the iterator is not changed
     *
     * @param iterator the iterator we are asking about
     * @return the number of items in the sequence
     * @throws UnsupportedOperationException if the iterator does not have this capability
     */

    public static int getLength(SequenceIterator iterator) {
        try {
            return ((LastPositionFinder)iterator).getLength();
        } catch (ClassCastException e) {
            throw new UnsupportedOperationException("getLength() not available in " + iterator.getClass());
        }
    }

    /**
     * Supply the (remaining) items in a sequence to a consumer of items
     * @param iter a sequence iterator, which will be consumed by calling this method
     * @param consumer the consumer which will be called to process the remaining items
     *                 in the sequence, in turn
     * @throws UncheckedXPathException if the computation of the input sequence reports an XPathException,
     * or if the consumer throws an XPathException
     */

    public static void supply(SequenceIterator iter, ItemConsumer<? super Item> consumer) {
        try {
            for (Item item; (item = iter.next()) != null; ) {
                consumer.accept(item);
            }
        } catch (XPathException e) {
            throw new UncheckedXPathException(e);
        }
    }

    public static boolean isUnrepeatable(Sequence seq) {
        return seq instanceof LazySequence ||
                (seq instanceof Closure && !(seq instanceof MemoClosure || seq instanceof SingletonClosure));
    }

    /**
     * Get the length of a sequence (the number of items it contains)
     *
     * @param sequence the sequence
     * @return the length in items
     * @throws XPathException if an error occurs (due to lazy evaluation)
     */

    public static int getLength(Sequence sequence) throws XPathException {
        if (sequence instanceof GroundedValue) {
            return ((GroundedValue) sequence).getLength();
        }
        return Count.count(sequence.iterate());
    }

    /**
     * Ask whether the length of a sequence is exactly N
     *
     * <p>Note: this is more efficient than counting the items and testing whether the result is
     * N, because the sequence only needs to be read as far as the Nth item.</p>
     *
     * @param iter   an iterator over the sequence in question (which is typically consumed)
     * @param length the supposed length
     * @return true if and only if the length of the sequence is the supposed length
     * @throws XPathException if an error is detected
     */

    public static boolean hasLength(SequenceIterator iter, int length) throws XPathException {
        if (SequenceTool.supportsGetLength(iter)) {
            return ((LastPositionFinder) iter).getLength() == length;
        } else {
            int n = 0;
            while (iter.next() != null) {
                if (n++ == length) {
                    iter.close();
                    return false;
                }
            }
            return length == 0;
        }
    }


    /**
     * Determine whether two sequences have the same number of items. This is more efficient
     * than comparing the counts, because the longer sequence is evaluated only as far as the
     * length of the shorter sequence. The method consumes the supplied iterators.
     *
     * @param a iterator over the first sequence
     * @param b iterator over the second sequence
     * @return true if the lengths of the two sequences are the same
     */

    public static boolean sameLength(SequenceIterator a, SequenceIterator b) {
        if (SequenceTool.supportsGetLength(a) && SequenceTool.supportsGetLength(b)) {
            return ((LastPositionFinder) a).getLength() == ((LastPositionFinder) b).getLength();
        } else {
            while (true) {
                Item itA = a.next();
                Item itB = b.next();
                if (itA == null || itB == null) {
                    if (itA != null) {
                        a.close();
                    }
                    if (itB != null) {
                        b.close();
                    }
                    return itA == null && itB == null;
                }
            }
        }
    }

    /**
     * Get the item at a given offset in a sequence. Uses zero-base indexing
     *
     * @param sequence the input sequence
     * @param index    the 0-based subscript
     * @return the n'th item if it exists, or null otherwise
     * @throws UncheckedXPathException for example if the value is a closure that needs to be
     *                        evaluated, and evaluation fails
     */

    public static Item itemAt(Sequence sequence, int index)  {
        if (sequence instanceof Item && index == 0) {
            return (Item)sequence;
        }
        try {
            return sequence.materialize().itemAt(index);
        } catch (XPathException e) {
            throw new UncheckedXPathException(e);
        }
    }

    /**
     * Static method to make an Item from a Value
     *
     * @param sequence the value to be converted
     * @return null if the value is an empty sequence; or the only item in the value
     * if it is a singleton sequence
     * @throws net.sf.saxon.trans.XPathException if the supplied Sequence contains multiple items
     */

    /*@Nullable*/
    public static Item asItem(Sequence sequence) throws XPathException {
        if (sequence instanceof Item) {
            return (Item) sequence;
        }
        SequenceIterator iter = sequence.iterate();
        Item first = iter.next();
        if (first == null) {
            return null;
        }
        if (iter.next() != null) {
            throw new XPathException("Sequence contains more than one item");
        }
        return first;
    }

    /**
     * Factory method to create a FocusIterator wrapping a supplied SequenceIterator
     * @param basis the SequenceIterator to be wrapped. This must be positioned at the start.
     * @return a FocusIterator that returns the same items as the supplied iterator, while
     * tracking position() and current().
     */

    public static FocusIterator focusTracker(SequenceIterator basis) {
        if (basis instanceof FocusIterator) {
            return (FocusIterator) basis;
        } else {
            return new FocusTrackingIterator(basis);
        }
    }

    /**
     * Convert an XPath value to a Java object.
     * An atomic value is returned as an instance
     * of the best available Java class. If the item is a node, the node is "unwrapped",
     * to return the underlying node in the original model (which might be, for example,
     * a DOM or JDOM node).
     *
     * @param item the item to be converted
     * @return the value after conversion
     * @throws net.sf.saxon.trans.XPathException if an error occurs: for example, if the XPath value is
     *                                           an integer and is too big to fit in a Java long
     */

    public static Object convertToJava(/*@NotNull*/ Item item) throws XPathException {
        switch (item.getGenre()) {
            case XNODE:
                Object node = item;
                while (node instanceof VirtualNode) {
                    // strip off any layers of wrapping
                    node = ((VirtualNode) node).getRealNode();
                }
                return node;
            case FUNCTION:
            case ARRAY:
            case MAP:
                return item;
            case EXTERNAL:
                return ((AnyExternalObject) item).getWrappedObject();
            case ATOMIC:
                AtomicValue value = (AtomicValue) item;
                switch (value.getItemType().getPrimitiveType()) {
                    case StandardNames.XS_STRING:
                    case StandardNames.XS_UNTYPED_ATOMIC:
                    case StandardNames.XS_ANY_URI:
                    case StandardNames.XS_DURATION:
                    case StandardNames.XS_TIME:
                        return value.getStringValue();
                    case StandardNames.XS_BOOLEAN:
                        return ((BooleanValue) value).getBooleanValue() ? Boolean.TRUE : Boolean.FALSE;
                    case StandardNames.XS_DECIMAL:
                        return ((DecimalValue) value).getDecimalValue();
                    case StandardNames.XS_INTEGER:
                        return ((NumericValue) value).longValue();
                    case StandardNames.XS_DOUBLE:
                        return ((DoubleValue) value).getDoubleValue();
                    case StandardNames.XS_FLOAT:
                        return ((FloatValue) value).getFloatValue();
                    case StandardNames.XS_DATE_TIME:
                        return ((DateTimeValue) value).getCalendar().getTime();
                    case StandardNames.XS_DATE:
                        return ((DateValue) value).getCalendar().getTime();
                    case StandardNames.XS_BASE64_BINARY:
                        return ((Base64BinaryValue) value).getBinaryValue();
                    case StandardNames.XS_HEX_BINARY:
                        return ((HexBinaryValue) value).getBinaryValue();
                    default:
                        return item;
                }
            default:
                return item;
        }
    }

    /**
     * Get the string value of a sequence. For an item, this is same as the result
     * of calling the XPath string() function. For a sequence of more than one item,
     * it is the concatenation of the individual string values of the items in the sequence,
     * space-separated.
     *
     * @param sequence the input sequence
     * @return a string representation of the items in the supplied sequence
     * @throws XPathException if the sequence contains an item with no string value,
     *                        for example a function item
     */

    public static UnicodeString getStringValue(Sequence sequence) throws XPathException {
        UnicodeBuilder ub = new UnicodeBuilder();
        supply(sequence.iterate(), (ItemConsumer<? super Item>) item -> {
            if (!ub.isEmpty()) {
                ub.append(' ');
            }
            ub.accept(item.getUnicodeStringValue());
        });
        return ub.toUnicodeString();
    }

    /**
     * Get the string value of a sequence. For an item, this is same as the result
     * of calling the XPath string() function. For a sequence of more than one item,
     * it is the concatenation of the individual string values of the items in the sequence,
     * space-separated.
     *
     * @param sequence the input sequence
     * @return a string representation of the items in the supplied sequence
     * @throws XPathException if the sequence contains an item with no string value,
     *                        for example a function item
     */

    public static String stringify(Sequence sequence) throws XPathException {
        StringBuilder sb = new StringBuilder(64);
        supply(sequence.iterate(), (ItemConsumer<? super Item>) item -> {
            if (sb.length() != 0) {
                sb.append(' ');
            }
            sb.append(item.getStringValue());
        });
        return sb.toString();
    }

    /**
     * Get the item type of the items in a sequence. If the sequence is heterogeneous,
     * the method returns the lowest common supertype. If the sequence is empty, it returns
     * ErrorType (the type to which no instance can belong). If the sequence is lazy,
     * it is not consumed; instead {@link AnyItemType} is returned
     *
     * @param sequence the input sequence
     * @param th       the Type Hierarchy cache
     * @return the lowest common supertype of the types of the items in the sequence
     */

    public static ItemType getItemType(Sequence sequence, TypeHierarchy th) {
        if (sequence instanceof Item) {
            return Type.getItemType((Item) sequence, th);
        } else if (sequence instanceof IntegerRange) {
            return BuiltInAtomicType.INTEGER;
        } else if (sequence instanceof GroundedValue) {
            try {
                ItemType type = null;
                SequenceIterator iter = sequence.iterate();
                for (Item item; (item = iter.next()) != null; ) {
                    if (type == null) {
                        type = Type.getItemType(item, th);
                    } else {
                        type = Type.getCommonSuperType(type, Type.getItemType(item, th), th);
                    }
                    if (type == AnyItemType.INSTANCE) {
                        break;
                    }
                }
                return type == null ? ErrorType.getInstance() : type;
            } catch (UncheckedXPathException err) {
                return AnyItemType.INSTANCE;
            }
        } else {
            return AnyItemType.INSTANCE;
        }
    }

    public static SequenceType getSequenceType(GroundedValue value, TypeHierarchy th) {
        ItemType it = getItemType(value, th);
        int cardinality = value.getLength() <= 1 ? StaticProperty.ALLOWS_ZERO_OR_ONE : StaticProperty.ALLOWS_ONE_OR_MORE;
        return SequenceType.makeSequenceType(it, cardinality);
    }

    /**
     * Get the UType of the items in a sequence. If the sequence is heterogeneous,
     * the method returns the lowest common supertype. If the sequence is empty, it returns
     * ErrorType (the type to which no instance can belong)
     *
     * @param sequence the input sequence
     * @return the lowest common supertype of the types of the items in the sequence
     */

    public static UType getUType(Sequence sequence) {
        if (sequence instanceof Item) {
            return UType.getUType((Item) sequence);
        } else if (sequence instanceof GroundedValue) {
            UType type = UType.VOID;
            SequenceIterator iter = sequence.iterate();
            for (Item item; (item = iter.next()) != null; ) {
                type = type.union(UType.getUType(item));
                if (type == UType.ANY) {
                    break;
                }
            }
            return type;
        } else {
            return UType.ANY;
        }
    }


    /**
     * Get the cardinality of a sequence. Note: if the sequence is lazy, it is consumed.
     *
     * @param sequence the supplied sequence
     * @return the cardinality, as one of the constants {@link StaticProperty#ALLOWS_ZERO} (for an empty sequence)
     * {@link StaticProperty#EXACTLY_ONE} (for a singleton), or {@link StaticProperty#ALLOWS_ONE_OR_MORE} (for
     * a sequence with more than one item)
     */

    public static int getCardinality(Sequence sequence) {
        if (sequence instanceof Item) {
            return StaticProperty.EXACTLY_ONE;
        }
        if (sequence instanceof GroundedValue) {
            int len = ((GroundedValue) sequence).getLength();
            switch (len) {
                case 0:
                    return StaticProperty.ALLOWS_ZERO;
                case 1:
                    return StaticProperty.EXACTLY_ONE;
                default:
                    return StaticProperty.ALLOWS_ONE_OR_MORE;
            }
        }
        try {
            SequenceIterator iter = sequence.iterate();
            Item item = iter.next();
            if (item == null) {
                return StaticProperty.ALLOWS_ZERO;
            }
            item = iter.next();
            return item == null ? StaticProperty.EXACTLY_ONE : StaticProperty.ALLOWS_ONE_OR_MORE;
        } catch (UncheckedXPathException err) {
            return StaticProperty.ALLOWS_ONE_OR_MORE;
        }
    }

    /**
     * Process a supplied value by copying it to the current output destination
     *
     * @param value      the sequence to be processed
     * @param output the destination for the result
     * @param locationId (can be set to -1 if absent) information about the location of the value,
     *                   which can be resolved by reference to the PipelineConfiguration of the current output
     *                   destination
     * @throws net.sf.saxon.trans.XPathException if an error occurs (for example if the value is
     *                                           a closure that needs to be evaluated)
     */

    public static void process(Sequence value, Outputter output, Location locationId) throws XPathException {
        try {
            supply(value.iterate(), (ItemConsumer<? super Item>) it -> output.append(it, locationId, ReceiverOption.ALL_NAMESPACES));
        } catch (UncheckedXPathException e) {
            throw e.getXPathException().maybeWithLocation(locationId);
        }
    }

    /**
     * Make an array of general-purpose Sequence objects of a given length
     * @param length the length of the returned array
     * @return the new array
     */

    public static Sequence[] makeSequenceArray(int length) {
        return new Sequence[length];
    }

    /**
     * Make an array of general-purpose Sequence objects with supplied contents
     * @param items the contents for the array
     * @return the new array
     */

    public static Sequence[] fromItems(Item... items) {
        Sequence[] seq = new Sequence[items.length];
        System.arraycopy(items, 0, seq, 0, items.length);
        return seq;
    }


    /**
     * Construct an AttributeMap given a list of {@link AttributeInfo} objects
     * representing the individual attributes.
     * @param list the list of attributes. It is the caller's responsibility
     *             to ensure that this list contains no duplicates. The method
     *             may detect this, but is not guaranteed to do so. Calling
     *             {@link AttributeMap#verify} after constructing the attribute map verifies
     *             that there are no duplicates. The order of items in the input
     *             list is not necessarily preserved.
     * @return an AttributeMap containing the specified attributes.
     * @throws IllegalArgumentException if duplicate attributes are detected
     */

    public static AttributeMap attributeMapFromList(List<AttributeInfo> list) {
        int n = list.size();
        if (n == 0) {
            return EmptyAttributeMap.INSTANCE;
        } else if (n == 1) {
            return SingletonAttributeMap.of(list.get(0));
        } else if (n <= SmallAttributeMap.LIMIT) {
            return new SmallAttributeMap(list);
        } else {
            return new LargeAttributeMap(list);
        }
    }

    /**
     * Helper method to convert an Item or null to a Sequence
     * @param item the item to convert
     * @return the converted sequence
     */

    public static GroundedValue itemOrEmpty(Item item) {
        if (item == null) {
            return EmptySequence.INSTANCE;
        } else {
            return item;
        }
    }

    public static Sequence joinSequences(Sequence[] sequences) {
        return new LazySequence(new MultiSequenceIterator(sequences));
    }

    public static class MultiSequenceIterator extends AbstractBlockIterator {

        private final Sequence[] sequences;

        public MultiSequenceIterator(Sequence[] sequences) {
            init(sequences.length);
            this.sequences = sequences;
        }

        @Override
        public SequenceIterator getNthChildIterator(int n) {
            return sequences[n].iterate();
        }
    }
}

