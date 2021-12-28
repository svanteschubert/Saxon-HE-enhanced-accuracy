////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;


import net.sf.saxon.s9api.Location;
import net.sf.saxon.tree.jiter.MonoIterator;
import net.sf.saxon.type.SimpleType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An implementation of AttributeMap for use when there is exactly one attribute
 */

public class SingletonAttributeMap extends AttributeInfo implements AttributeMap {

    private SingletonAttributeMap(NodeName nodeName,
                                  SimpleType type,
                                  String value,
                                  Location location,
                                  int properties) {
        super(nodeName, type, value, location, properties);
    }

    public static SingletonAttributeMap of(AttributeInfo att) {
        if (att instanceof SingletonAttributeMap) {
            return (SingletonAttributeMap)att;
        } else {
            return new SingletonAttributeMap(att.getNodeName(), att.getType(), att.getValue(), att.getLocation(), att.getProperties());
        }
    }

    /**
     * Return the number of attributes in the map.
     *
     * @return The number of attributes in the map.
     */

    @Override
    public int size() {
        return 1;
    }

    @Override
    public AttributeInfo get(NodeName name) {
        return name.equals(getNodeName()) ? this : null;
    }

    @Override
    public AttributeInfo get(String uri, String local) {
        return getNodeName().getLocalPart().equals(local) && getNodeName().hasURI(uri) ? this : null;
    }

    @Override
    public AttributeInfo getByFingerprint(int fingerprint, NamePool namePool) {
        return getNodeName().obtainFingerprint(namePool) == fingerprint ? this : null;
    }

    @Override
    public AttributeMap put(AttributeInfo att) {
        if (getNodeName().equals(att.getNodeName())) {
            return SingletonAttributeMap.of(att);
        } else {
            List<AttributeInfo> list = new ArrayList<>(2);
            list.add(this);
            list.add(att);
            return new SmallAttributeMap(list);
        }
    }

    @Override
    public AttributeMap remove(NodeName name) {
        return name.equals(getNodeName()) ? EmptyAttributeMap.getInstance() : this;
    }

    @Override
    public Iterator<AttributeInfo> iterator() {
        return new MonoIterator<>(this);
    }

    @Override
    public AttributeMap apply(java.util.function.Function<AttributeInfo, AttributeInfo> mapper) {
        return SingletonAttributeMap.of(mapper.apply(this));
    }

    @Override
    public List<AttributeInfo> asList() {
        List<AttributeInfo> list = new ArrayList<>(1);
        list.add(this);
        return list;
    }

    @Override
    public AttributeInfo itemAt(int index) {
        if (index == 0) {
            return this;
        } else {
            throw new IndexOutOfBoundsException(index + " of 1");
        }
    }
}

