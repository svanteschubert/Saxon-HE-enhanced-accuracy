////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

/**
 * A one-way linked chain of entries in a {@link CustomSet}
 * @param <T> The type of the value held in each link of the chain
 * @implNote This is a separate top-level class (rather than an inner class) for ease of
 * conversion to C#. Making it a separate class avoids the problem of the full class
 * name requiring two type parameters.
 */
class CustomSetEntryChain<T> {
    public T value;
    public CustomSetEntryChain<T> next;

    public CustomSetEntryChain(T value) {
        this.value = value;
        this.next = null;
    }
}

