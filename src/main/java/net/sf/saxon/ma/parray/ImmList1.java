////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.parray;

import net.sf.saxon.tree.jiter.MonoIterator;

import java.util.Iterator;

/**
 * Implementation of an immutable list of length 1 (one)
 * @param <E> the type of the list element
 */

public class ImmList1<E> extends ImmList<E> {

    private E member;

    public ImmList1(E member) {
        this.member = member;
    }

    @Override
    public E get(int index) {
        if (index == 0) {
            return member;
        } else {
            throw outOfBounds(index, 1);
        }
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public ImmList<E> replace(int index, E member) {
        if (index == 0) {
            return new ImmList1<>(member);
        } else {
            throw outOfBounds(index, 1);
        }
    }

    @Override
    public ImmList<E> insert(int index, E member) {
        if (index == 0) {
            return new ImmList2<>(new ImmList1<>(member), this);
        } else if (index == 1) {
            return new ImmList2<>(this, new ImmList1<>(member));
        } else {
            throw outOfBounds(index,1);
        }
    }

    @Override
    public ImmList<E> append(E member) {
        return new ImmList2<>(this, new ImmList1<>(member));
    }

    @Override
    public ImmList<E> appendList(ImmList<E> members) {
        return new ImmList2<>(this, members);
    }

    @Override
    public ImmList<E> remove(int index) {
        if (index == 0) {
            return ImmList.empty();
        } else {
            throw outOfBounds(index, 1);
        }
    }

    @Override
    public ImmList<E> subList(int start, int end) {
        if (start != 0) {
            throw outOfBounds(start, 1);
        }
        if (end == 0) {
            return ImmList.empty();
        } else if (end == 1) {
            return this;
        } else {
            throw outOfBounds(end, 1);
        }
    }

    @Override
    public Iterator<E> iterator() {
        return new MonoIterator<>(member);
    }
}

