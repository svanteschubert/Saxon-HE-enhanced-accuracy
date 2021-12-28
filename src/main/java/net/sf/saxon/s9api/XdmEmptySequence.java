////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import java.util.Collections;

/**
 * The class <tt>XdmEmptySequence</tt> represents an empty sequence in the XDM Data Model.
 * <p>This is a singleton class: there is only one instance, which may be obtained
 * using the {@link #getInstance} method.</p>
 * <p>An empty sequence may also be represented by an {@link XdmValue} whose length happens to be zero.
 * Applications should therefore not test to see whether an object is an instance of this class
 * in order to decide whether it is empty.</p>
 * <p>Note: in interfaces that expect an {@link XdmItem}, an empty sequence is represented by a
 * Java null value.</p>
 */

public class XdmEmptySequence extends XdmValue {

    private static XdmEmptySequence THE_INSTANCE = new XdmEmptySequence();

    /**
     * Return the singleton instance of this class
     *
     * @return an XdmValue representing an empty sequence
     */

    /*@NotNull*/
    public static XdmEmptySequence getInstance() {
        return THE_INSTANCE;
    }

    private XdmEmptySequence() {
        super(Collections.emptyList());
    }

    /**
     * Get the number of items in the sequence
     *
     * @return the number of items in the value - always zero
     */

    @Override
    public int size() {
        return 0;
    }
}
