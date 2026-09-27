////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.z;

/**
 * An iterator over a sequence of positive int values, returning -1 to indicate the end of the sequence.
 * Used primarily for iterating over the codepoints of a Unicode string.
 */
public interface PositiveIntIterator {

    /**
     * Return the next positive integer in the sequence, or -1 if the sequence is exhausted.
     *
     * @return the next integer in the sequence, or -1 if the sequence is exhausted.
     */

    int next();
}
