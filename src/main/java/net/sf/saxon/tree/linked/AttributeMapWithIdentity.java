////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;


import net.sf.saxon.om.*;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.ListIterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An implementation of AttributeMap suitable for representing attributes on a mutable tree.
 * Unlike an ordinary AttributeMap, each AttributeInfo has a persistent index value, which
 * remains unchanged when attributes are added or removed or renamed. New attributes are allocated
 * an index value greater than any previously-allocated index value.
 *
 * <p>However, an {@code AttributeMapWithIdentity}, like any other {@code AttributeMap},
 * is an immutable object.</p>
 */

public class AttributeMapWithIdentity implements AttributeMap {

    private List<AttributeInfo> attributes;

    AttributeMapWithIdentity(List<AttributeInfo> attributes) {
        this.attributes = attributes;
    }

    /**
     * Return the number of attributes in the map.
     *
     * @return The number of attributes in the map.
     */

    @Override
    public int size() {
        int count = 0;
        for (AttributeInfo att : attributes) {
            if (!(att instanceof AttributeInfo.Deleted)) {
                count++;
            }
        }
        return count;
    }

    public AxisIterator iterateAttributes(ElementImpl owner) {
        List<NodeInfo> list = new ArrayList<>(attributes.size());
        for (int i=0; i<attributes.size(); i++) {
            AttributeInfo att = attributes.get(i);
            if (!(att instanceof AttributeInfo.Deleted)) {
                list.add(new AttributeImpl(owner, i));
            }
        }
        return new ListIterator.OfNodes(list);
    }

    private boolean isDeleted(AttributeInfo info) {
        return (info instanceof AttributeInfo.Deleted);
    }

    @Override
    public AttributeInfo get(NodeName name) {
        for (AttributeInfo info : attributes) {
            if (info.getNodeName().equals(name) && !(info instanceof AttributeInfo.Deleted)) {
                return info;
            }
        }
        return null;
    }

    @Override
    public AttributeInfo get(String uri, String local) {
        for (AttributeInfo info : attributes) {
            NodeName name = info.getNodeName();
            if (name.getLocalPart().equals(local) && name.hasURI(uri) && !(info instanceof AttributeInfo.Deleted)) {
                return info;
            }
        }
        return null;
    }

    public int getIndex(String uri, String local) {
        for (int i=0; i<attributes.size(); i++) {
            AttributeInfo info = attributes.get(i);
            NodeName name = info.getNodeName();
            if (name.getLocalPart().equals(local) && name.hasURI(uri)) {
                return i;
            }
        }
        return -1;
    }

    public AttributeMapWithIdentity set(int index, AttributeInfo info) {
        List<AttributeInfo> newList = new ArrayList<>(attributes);
        if (index >= 0 && index < attributes.size()) {
            newList.set(index, info);
        } else if (index == attributes.size()) {
            newList.add(info);
        }
        return new AttributeMapWithIdentity(newList);
    }

    public AttributeMapWithIdentity add(AttributeInfo info) {
        List<AttributeInfo> newList = new ArrayList<>(attributes);
        newList.add(info);
        return new AttributeMapWithIdentity(newList);
    }

    /**
     * Remove an existing attribute, to create a new AttributeMap
     *
     * @param index the index of the attribute to be removed (if it exists)
     * @return a new attribute map in which the specified attribute is marked as deleted.
     */

    public AttributeMapWithIdentity remove(int index) {
        List<AttributeInfo> newList = new ArrayList<>(attributes);
        if (index >= 0 && index < attributes.size()) {
            AttributeInfo.Deleted del = new AttributeInfo.Deleted(attributes.get(index));
            newList.set(index, del);
        }
        return new AttributeMapWithIdentity(newList);
    }

    @Override
    public AttributeInfo getByFingerprint(int fingerprint, NamePool namePool) {
        for (AttributeInfo info : attributes) {
            NodeName name = info.getNodeName();
            if (name.obtainFingerprint(namePool) == fingerprint) {
                return info;
            }
        }
        return null;
    }

    @Override
    public Iterator<AttributeInfo> iterator() {
        return attributes.stream().filter(info -> !(info instanceof AttributeInfo.Deleted)).iterator();
    }

    @Override
    public List<AttributeInfo> asList() {
        return attributes.stream().filter(info -> !(info instanceof AttributeInfo.Deleted)).collect(Collectors.toList());
    }

    @Override
    public AttributeInfo itemAt(int index) {
        return attributes.get(index);
    }
}

