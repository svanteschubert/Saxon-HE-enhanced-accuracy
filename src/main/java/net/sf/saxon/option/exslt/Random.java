////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.exslt;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.UnfailingIterator;
import net.sf.saxon.value.DoubleValue;

/**
 * This class implements extension functions in the
 * http://exslt.org/random namespace.
 *
 * @author Martin Szugat
 * @version 1.0, 30.06.2004
 *          Rewritten by Michael Kay to generate a SequenceIterator
 */
public abstract class Random {

    /**
     * Returns a sequence of random numbers
     * between 0 and 1.
     *
     * @param numberOfItems number of random items
     *                      in the sequence.
     * @param seed          the initial seed.
     * @return sequence of random numbers as an iterator.
     * @throws IllegalArgumentException <code>numberOfItems</code> is not positive.
     */
    public static SequenceIterator randomSequence(int numberOfItems, double seed)
            throws IllegalArgumentException {
        if (numberOfItems < 1) {
            throw new IllegalArgumentException("numberOfItems supplied to randomSequence() must be positive");
        }
        long javaSeed = Double.doubleToLongBits(seed);
        return new RandomIterator(numberOfItems, javaSeed);
    }

    /**
     * Returns a sequence of random numbers
     * between 0 and 1.
     *
     * @param numberOfItems number of random items
     *                      in the sequence.
     * @return sequence of random numbers.
     * @throws IllegalArgumentException <code>numberOfItems</code> is not positive.
     */
    public static SequenceIterator randomSequence(int numberOfItems)
            throws IllegalArgumentException {
        return randomSequence(numberOfItems, System.currentTimeMillis());
    }

    /**
     * Returns a single random number                                                               X
     * between 0 and 1.
     *
     * @return sequence random number.
     */
    /*@Nullable*/
    public static DoubleValue randomSequence() throws XPathException {
        return (DoubleValue) randomSequence(1).next();
    }

    /**
     * Iterator over a sequence of random numbers
     */

    private static class RandomIterator implements UnfailingIterator {

        protected int position = 0;
        private int count;
        private long seed;
        private java.util.Random generator;

        public RandomIterator(int count, long seed) {
            this.count = count;
            this.seed = seed;
            generator = new java.util.Random(seed);
        }

        /**
         * Get the next item in the sequence. <BR>
         *
         * @return the next item, or null if there are no more items.
         */

        @Override
        public Item next() {
            if (position++ >= count) {
                position = -1;
                return null;
            } else {
                return new DoubleValue(generator.nextDouble());
            }
        }

    }
}

