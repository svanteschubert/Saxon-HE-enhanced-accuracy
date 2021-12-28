////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2012 Michael Froh.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.trie;

public final class Tuple2<T1, T2> {
    public final T1 _1;
    public final T2 _2;

    public Tuple2(final T1 v1, final T2 v2) {
        _1 = v1;
        _2 = v2;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Tuple2 tuple = (Tuple2) o;

        if (_1 != null ? !_1.equals(tuple._1) : tuple._1 != null)
            return false;
        if (_2 != null ? !_2.equals(tuple._2) : tuple._2 != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = _1 != null ? _1.hashCode() : 0;
        result = 31 * result + (_2 != null ? _2.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "(" +
            _1 + ',' +
            _2 +
        ')';
    }

}
