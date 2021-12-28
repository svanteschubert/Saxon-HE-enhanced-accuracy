package net.sf.saxon.om;

import net.sf.saxon.trans.XPathException;
import java.util.Iterator;

/**
 * Class IteratorWrapper - provides an an SequenceIterator over a Java Iterator.
 */
public class IteratorWrapper implements SequenceIterator {

    Iterator<? extends Item> iterator;

    /**
     * Create a IteratorWrapper backed by an iterator
     * @param iterator the iterator that delivers the items in the sequence
     */

    public IteratorWrapper(Iterator<? extends Item> iterator) {
        this.iterator = iterator;
    }


    /**
     * Get the next item in the Iterator
     *
     * @return the next item in the iterator, or null if there are no more items. Once a call
     * on next() has returned null, no further calls should be made.
     */
    @Override
    public Item next() throws XPathException {
        return iterator.hasNext() ? iterator.next() : null;
    }
}


