////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

/**
 * An implementation of AttributeMap representing an empty AttributeMap
 */

public enum EmptyAttributeMap implements AttributeMap {

    INSTANCE;

    public static EmptyAttributeMap getInstance() {
        return INSTANCE;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public AttributeInfo get(NodeName name) {
        return null;
    }

    @Override
    public AttributeInfo get(NamespaceUri uri, String local) {
        return null;
    }

    @Override
    public AttributeInfo getByFingerprint(int fingerprint, NamePool namePool) {
        return null;
    }

    @Override
    public String getValue(NamespaceUri uri, String local) {
        return null;
    }

    @Override
    public String getValue(String local) {
        return null;
    }

    @Override
    public AttributeMap put(AttributeInfo att) {
        return SingletonAttributeMap.of(att);
    }

    @Override
    public AttributeMap remove(NodeName name) {
        return this;
    }

    @Override
    public void verify() {}

    @Override
    public Iterator<AttributeInfo> iterator() {
        return Collections.emptyIterator();
    }

    @Override
    public AttributeMap apply(java.util.function.Function<AttributeInfo, AttributeInfo> mapper) {
        return this;
    }

    @Override
    public ArrayList<AttributeInfo> asList() {
        return new ArrayList<>(0);
    }

    @Override
    public AttributeInfo itemAt(int index) {
        throw new IndexOutOfBoundsException();
    }


}

