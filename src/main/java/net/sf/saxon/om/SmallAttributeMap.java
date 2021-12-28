////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An implementation of AttributeMap suitable for small collections of attributes (typically, up to five).
 * Searching for a particular attribute involves a sequential search, and adding a new attribute constructs
 * a full copy.
 *
 * <p>A {@code SmallAttributeMap} retains attribute order, so there may be situations in which it is appropriate
 * to use this structure even for larger attribute sets.</p>
 */

public class SmallAttributeMap implements AttributeMap {

    final static int LIMIT = 8;

    private List<AttributeInfo> attributes;

    public SmallAttributeMap(List<AttributeInfo> attributes) {
        // TODO: check uniqueness of names?
        this.attributes = new ArrayList<>(attributes);
    }

    /**
     * Return the number of attributes in the map.
     *
     * @return The number of attributes in the map.
     */

    @Override
    public int size() {
        return attributes.size();
    }

    @Override
    public AttributeInfo get(NodeName name) {
        for (AttributeInfo info : attributes) {
            if (info.getNodeName().equals(name)) {
                return info;
            }
        }
        return null;
    }

    @Override
    public AttributeInfo get(String uri, String local) {
        for (AttributeInfo info : attributes) {
            NodeName name = info.getNodeName();
            if (name.getLocalPart().equals(local) && name.hasURI(uri)) {
                return info;
            }
        }
        return null;
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
        return attributes.iterator();
    }

    @Override
    public List<AttributeInfo> asList() {
        return new ArrayList<>(attributes);
    }

    @Override
    public AttributeInfo itemAt(int index) {
        return attributes.get(index);
    }
}

