////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.parray;

import java.util.Collections;
import java.util.Iterator;

/**
 * Implementation of an immutable empty list
 * @param <E> the (nominal) type of the list elements
 */

public class ImmList0<E> extends ImmList<E> {

    final static ImmList0 INSTANCE = new ImmList0();

    private ImmList0() {}

    @Override
    public E get(int index) {
        throw outOfBounds(index, 0);
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public ImmList<E> replace(int index, E member) {
        throw outOfBounds(index, 0);
    }

    @Override
    public ImmList<E> insert(int index, E member) {
        if (index == 0) {
            return new ImmList1<>(member);
        } else {
            throw outOfBounds(index, 0);
        }
    }

    @Override
    public ImmList<E> append(E member) {
        return new ImmList1<>(member);
    }

    @Override
    public ImmList<E> appendList(ImmList<E> members) {
        return members;
    }

    @Override
    public ImmList<E> remove(int index) {
        throw outOfBounds(index, 0);
    }

    @Override
    public ImmList<E> subList(int start, int end) {
        if (start == 0 && end == 0) {
            return this;
        } else {
            throw outOfBounds(0, 0);
        }
    }

    @Override
    public Iterator<E> iterator() {
        return Collections.emptyIterator();
    }
}

