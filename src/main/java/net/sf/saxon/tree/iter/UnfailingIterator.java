////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


/**
 * A SequenceIterator is used to iterate over a sequence. An UnfailingIterator
 * is a SequenceIterator that throws no checked exceptions.
 */

public interface UnfailingIterator extends SequenceIterator {

    /**
     * Get the next item in the sequence.
     *
     * @return the next Item. If there are no more items, return null.
     */

    @Override
    Item next();

    /**
     * Process all the items returned by the iterator, supplying them to a given {@link Consumer}.
     * Note that this method throws no exceptions. This method consumes the iterator.
     * @param consumer the function that is to consume each of the (remaining) items returned by
     *                 the iterator
     */
    default void forEach(Consumer<? super Item> consumer) {
        Item item;
        while ((item = next()) != null) {
            consumer.accept(item);
        }
    }

    /**
     * Create a list containing all the items returned by the iterator.
     * This method consumes the iterator.
     * @return a list containing all the (remaining) items returned by the iterator
     */
    default List<Item> toList() {
        List<Item> list = new ArrayList<>();
        forEach(list::add);
        return list;
    }

}

