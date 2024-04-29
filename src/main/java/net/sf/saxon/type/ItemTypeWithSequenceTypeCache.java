////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.value.SequenceType;

/**
 * Extension of the ItemType interface implemented by some item types, to provide
 * a cache of SequenceType objects based on this item type, with different
 * occurrence indicators.
 */

public interface ItemTypeWithSequenceTypeCache extends ItemType {
    /**
     * Get a sequence type representing exactly one instance of this atomic type
     *
     * @return a sequence type representing exactly one instance of this atomic type
     * @since 9.8.0.2
     */

    SequenceType one();

    /**
     * Get a sequence type representing zero or one instances of this atomic type
     *
     * @return a sequence type representing zero or one instances of this atomic type
     * @since 9.8.0.2
     */

    SequenceType zeroOrOne();

    /**
     * Get a sequence type representing one or more instances of this atomic type
     *
     * @return a sequence type representing one or more instances of this atomic type
     * @since 9.8.0.2
     */

    SequenceType oneOrMore();

    /**
     * Get a sequence type representing one or more instances of this atomic type
     *
     * @return a sequence type representing one or more instances of this atomic type
     * @since 9.8.0.2
     */

    SequenceType zeroOrMore();

}
