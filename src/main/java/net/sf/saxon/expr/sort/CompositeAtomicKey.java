////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.transpile.CSharpReplaceBody;

import java.util.List;

/**
 * A composite atomic key is a sequence of atomic values; two such keys are matched be comparing the
 * constituent values pairwise. It is used primarily in the xsl:for-each-group instruction.
 */
public class CompositeAtomicKey {

    // This is extracted as a separate class primarily to allow different implementations for Java and C#.
    // This is because <code>List.equals()</code> has the desired semantics on Java, but not on C#.

    private List<AtomicMatchKey> keys;

    /**
     * Construct a composite atomic match key from a list of atomic match keys
     * @param keys the constituent match keys
     */
    public CompositeAtomicKey(List<AtomicMatchKey> keys) {
        this.keys = keys;
    }

    @Override
    @CSharpReplaceBody(code="return obj is CompositeAtomicKey && Saxon.Impl.Helpers.ListUtils.EqualLists(keys, ((CompositeAtomicKey)obj).keys);")
    public boolean equals(Object obj) {
        return obj instanceof CompositeAtomicKey && keys.equals(((CompositeAtomicKey)obj).keys);
    }

    @Override
    @CSharpReplaceBody(code = "return Saxon.Impl.Helpers.ListUtils.ListHashCode(keys);")
    public int hashCode() {
        return keys.hashCode();
    }

}

