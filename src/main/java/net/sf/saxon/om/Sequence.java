////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Closure;

/**
 * This interface represents an XDM Value, that is, a sequence of items.
 * <p>Note that different implementations of Sequence might have very different
 * performance characteristics, though all should exhibit the same behaviour.
 * With some sequences, calling iterate() may trigger evaluation of the logic
 * that computes the sequence, and calling iterate() again may cause re-evaluation.</p>
 * <p>Users should avoid assuming that a sequence of length one will always
 * be represented as an instance of Item. If you are confident that the sequence
 * will be of length one, call the head() function to get the first item.</p>
 *
 * @since 9.5. Generified in 9.9. Generics dropped in 10.0.
 */
public interface Sequence {

    /**
     * Get the first item in the sequence.
     *
     * @return the first item in the sequence if there is one, or null if the sequence
     *         is empty
     * @throws XPathException in the situation where the sequence is evaluated lazily, and
     *                        evaluation of the first item causes a dynamic error.
     */

    Item head() throws XPathException;

    /**
     * Get a {@link SequenceIterator} over all the items in the sequence
     *
     * @return an iterator (specifically, a Saxon {@link SequenceIterator}, which is
     * not a {@link java.util.Iterator}) over all the items
     * @throws XPathException in the situation where the sequence is evaluated lazily, and
     *                        constructing an iterator over the items causes a dynamic error.
     */

    SequenceIterator iterate() throws XPathException;

    /**
     * Create a {@link GroundedValue} containing the same items as this Sequence.
     * A {@code GroundedValue} typically contains the entire sequence in memory; at
     * any rate, it guarantees that the entire sequence can be read without any
     * possibility of XPath dynamic errors arising.
     * @return a {@link GroundedValue} containing the same items as this Sequence
     * @throws XPathException if evaluating the contents of the sequence fails with
     * a dynamic error.
     */

    default GroundedValue materialize() throws XPathException {
        return iterate().materialize();
    }

    /**
     * Ensure that the sequence is in a form where it can be evaluated more than once. Some
     * sequences (for example {@link LazySequence} and {@link Closure} can only be evaluated
     * once, and this operation causes these to be grounded. However, making it repeatable
     * is not the same as making it grounded; it does not flush out all errors. Indeed, lazy
     * evaluation relies on this property, because an expression that has been lifted out of
     * a loop must not be evaluated unless the loop is executed at least once, to prevent spurious
     * errors.
     * @return An equivalent sequence that can be repeatedly evaluated
     * @throws XPathException if evaluation fails
     */

    default Sequence makeRepeatable() throws XPathException {
        return this;
    }


}

