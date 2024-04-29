////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.functions.Reverse;
import net.sf.saxon.om.FocusIterator;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceExtent;

import java.util.List;

/**
 * Class ListIterator, iterates over a sequence of items held in a Java List.
 *
 * <p>Note: There are three subclasses: {@code ListIterator}, which takes a general list of items,
 * {@code NodeListIterator}, which takes a list of nodes, and {@code AtomicListIterator}, which takes
 * a list of atomic values. It might seem simpler to implement this entirely using generics, but that
 * design proves very difficult to translate to C#, where generics behave very differently. With this approach
 * the generics are confined to this abstract class.</p>
 */

public abstract class ListIterator implements SequenceIterator, FocusIterator,
        LastPositionFinder, LookaheadIterator, GroundedIterator,
        ReversibleIterator {

    @Override
    public boolean supportsHasNext() {
        return true;
    }


    public static class Of<T extends Item> extends ListIterator {

        private int index = 0;
        protected List<T> list;

        /**
         * Create a ListIterator over a given List
         *
         * @param list the list: all objects in the list must be instances of {@link Item}
         */

        public Of(List<T> list) {
            index = 0;
            this.list = list;
        }

        @Override
        public boolean hasNext() {
            return index < list.size();
        }

        /*@Nullable*/
        @Override
        public Item next() {
            if (index >= list.size()) {
                return null;
            }
            return list.get(index++);
        }

        @Override
        public boolean supportsGetLength() {
            return true;
        }

        @Override
        public int getLength() {
            return list.size();
        }

        @Override
        public Item current() {
            return list.get(index-1);
        }

        @Override
        public int position() {
            return index;
        }

        public boolean isActuallyGrounded() {
            return true;
        }

        /**
         * Return a Sequence containing all the items in the sequence returned by this
         * SequenceIterator
         *
         * @return the corresponding GroundedValue
         */

        /*@Nullable*/
        @Override
        public GroundedValue materialize() {
            return new SequenceExtent.Of<T>(list);
        }

        @Override
        public GroundedValue getResidue() {
            List<T> l2 = list;
            if (index != 0) {
                l2 = l2.subList(index, l2.size());
            }
            return new SequenceExtent.Of<T>(l2);
        }

        @Override
        public SequenceIterator getReverseIterator() {
            return Reverse.reverseIterator(list);
        }
    }

    /**
     * ListIterator.OfAtomic is a subclass of ListIterator where the list always
     * contains atomic values; it therefore implements the AtomicIterator interface.
     */

    public static class OfAtomic<A extends AtomicValue> extends ListIterator.Of<A> implements AtomicIterator {
        public OfAtomic(List<A> nodes) {
            super(nodes);
        }

        public AtomicValue next() {
            return (AtomicValue)super.next();
        }
    }

}

