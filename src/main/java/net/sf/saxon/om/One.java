////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.tree.iter.ConstrainedIterator;
import net.sf.saxon.value.*;

/**
 * A sequence containing exactly one item. The main use of this class is in declaring the expected arguments
 * of reflexive method calls, where the use of One(T) rather than T emphasizes that the value must not be null/empty,
 * and generates type-checking code to ensure that it is not empty.
 *
 * <p>To extract the wrapped item, use {@link #head()}.</p>
 */
public class One<T extends Item> extends ZeroOrOne<T> {

    /**
     * Create an instance of the class
     * @param item The single item to be contained in the sequence. Must not be null.
     * @throws NullPointerException if item is null.
     */

    public One(T item) {
        super(item);
        if (item == null) {
            throw new NullPointerException();
        }
    }

    /**
     * Return an iterator over this value.
     */
    @Override
    public ConstrainedIterator<T> iterate() {
        return new ConstrainedIterator<T>() {
            boolean gone = false;
            @Override
            public boolean hasNext() {
                return !gone;
            }

            @Override
            public T next() {
                if (gone) {
                    return null;
                } else {
                    gone = true;
                    return head();
                }
            }

            @Override
            public int getLength() {
                return 1;
            }

            @Override
            public GroundedValue materialize() {
                return head();
            }

            @Override
            public GroundedValue getResidue() {
                return gone ? EmptySequence.getInstance() : head();
            }

            @Override
            public SequenceIterator getReverseIterator() {
                return iterate();
            }
        };
    }

    /**
     * Convenience function to create a singleton boolean value
     * @param value the boolean value
     * @return the boolean value wrapped as a One&lt;BooleanValue&gt;
     */

    public static One<BooleanValue> bool (boolean value) {
        return new One<>(BooleanValue.get(value));
    }

    /**
     * Convenience function to create a singleton string value
     * @param value the string value.. If null, the result will represent a zero-length string
     * @return the string value wrapped as a One&lt;StringValue&gt;
     */

    public static One<StringValue> string (String value) {
        return new One<>(new StringValue(value));
    }

    /**
     * Convenience function to create a singleton integer value
     * @param value the integer value
     * @return the integer value wrapped as a One&lt;IntegerValue&gt;
     */

    public static One<IntegerValue> integer (long value) {
        return new One<>(new Int64Value(value));
    }

    /**
     * Convenience function to create a singleton double value
     *
     * @param value the double value
     * @return the double value wrapped as a One&lt;DoubleValue&gt;
     */

    public static One<DoubleValue> dbl(double value) {
        return new One<>(new DoubleValue(value));
    }
}


