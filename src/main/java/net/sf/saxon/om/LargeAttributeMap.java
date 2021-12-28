////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;


import net.sf.saxon.ma.trie.ImmutableHashTrieMap;
import net.sf.saxon.tree.util.FastStringBuffer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An implementation of AttributeMap suitable for larger collections of attributes (say, more than five).
 * This provides direct access to an attribute by name, avoiding the cost of a sequential search. The
 * map preserves the order of insertion of attributes: this is done by maintaining a doubly-linked list
 * of attributes in insertion order (when an attribute is replaced by another with the same name, it
 * currently occupies the position of the original; but this is not guaranteed).
 */

public class LargeAttributeMap implements AttributeMap {

    private static class AttributeInfoLink {
        AttributeInfo payload;
        NodeName prior;
        NodeName next;
    }

    private ImmutableHashTrieMap<NodeName, AttributeInfoLink> attributes;
    private NodeName first = null;
    private NodeName last = null;
    private int size;

    private LargeAttributeMap() {}

    public LargeAttributeMap(List<AttributeInfo> atts) {
        assert !atts.isEmpty();
        this.attributes = ImmutableHashTrieMap.empty();
        this.size = atts.size();
        AttributeInfoLink current = null;
        for (AttributeInfo att : atts) {
            if (attributes.get(att.getNodeName()) != null) {
                throw new IllegalArgumentException("Attribute map contains duplicates");
            }
            AttributeInfoLink link = new AttributeInfoLink();
            link.payload = att;
            if (current == null) {
                first = att.getNodeName();
            } else {
                current.next = att.getNodeName();
                link.prior = current.payload.getNodeName();
            }
            current = link;
            attributes = attributes.put(att.getNodeName(), link);
        }
        last = current.payload.getNodeName();
    }

    private LargeAttributeMap(ImmutableHashTrieMap<NodeName, AttributeInfoLink> attributes, int size, NodeName first, NodeName last) {
        this.attributes = attributes;
        this.size = size;
        this.first = first;
        this.last = last;
    }

    /**
     * Return the number of attributes in the map.
     *
     * @return The number of attributes in the map.
     */

    @Override
    public int size() {
        return size;
    }

    @Override
    public AttributeInfo get(NodeName name) {
        AttributeInfoLink link = attributes.get(name);
        return link == null ? null : link.payload;
    }

    @Override
    public AttributeInfo get(String uri, String local) {
        NodeName name = new FingerprintedQName("", uri, local);
        return get(name);
    }

    @Override
    public AttributeInfo getByFingerprint(int fingerprint, NamePool namePool) {
        NodeName name = new FingerprintedQName(namePool.getStructuredQName(fingerprint), fingerprint);
        return get(name);
    }

    @Override
    public AttributeMap put(AttributeInfo att) {
        AttributeInfoLink existing = attributes.get(att.getNodeName());
        AttributeInfoLink link = new AttributeInfoLink();
        NodeName last2 = last;
        link.payload = att;
        if (existing == null) {
            link.prior = last;
            last2 = att.getNodeName();
            AttributeInfoLink oldLast = attributes.get(last);
            AttributeInfoLink penult = new AttributeInfoLink();
            penult.payload = oldLast.payload;
            penult.next = att.getNodeName();
            penult.prior = oldLast.prior;
            attributes = attributes.put(last, penult);
        } else {
            link.prior = existing.prior;
            link.next = existing.next;
        }
        ImmutableHashTrieMap<NodeName, AttributeInfoLink> att2 = attributes.put(att.getNodeName(), link);
        int size2 = existing == null ? size + 1 : size;
        return new LargeAttributeMap(att2, size2, first, last2);
    }

    @Override
    public AttributeMap remove(NodeName name) {
        // Not actually used (or tested)
        if (attributes.get(name) == null) {
            return this;
        } else {
            NodeName first2 = first;
            NodeName last2 = last;
            ImmutableHashTrieMap<NodeName, AttributeInfoLink> att2 = attributes.remove(name);
            AttributeInfoLink existing = attributes.get(name);
            if (existing.prior != null) {
                AttributeInfoLink priorLink = attributes.get(existing.prior);
                AttributeInfoLink priorLink2 = new AttributeInfoLink();
                priorLink2.payload = priorLink.payload;
                priorLink2.prior = priorLink.prior;
                priorLink2.next = existing.next;
                att2.put(existing.prior, priorLink2);
            } else {
                first2 = existing.next;
            }
            if (existing.next != null) {
                AttributeInfoLink nextLink = attributes.get(existing.next);
                AttributeInfoLink nextLink2 = new AttributeInfoLink();
                nextLink2.payload = nextLink.payload;
                nextLink2.next = nextLink.next;
                nextLink2.prior = existing.prior;
                att2.put(existing.next, nextLink2);
            } else {
                last2 = existing.prior;
            }
            return new LargeAttributeMap(att2, size - 1, first2, last2);
        }
    }

    @Override
    public Iterator<AttributeInfo> iterator() {
        return new Iterator<AttributeInfo>() {

            NodeName current = first;
            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public AttributeInfo next() {
                AttributeInfoLink link = attributes.get(current);
                current = link.next;
                return link.payload;
            }
        };

    }

    @Override
    public synchronized List<AttributeInfo> asList() {
        List<AttributeInfo> result = new ArrayList<>(size);
        iterator().forEachRemaining(result::add);
        return result;
    }

    @Override
    public String toString() {
        FastStringBuffer sb = new FastStringBuffer(256);
        for (AttributeInfo att : this) {
            sb.cat(att.getNodeName().getDisplayName())
                    .cat("=\"")
                    .cat(att.getValue())
                    .cat("\" ");
        }
        return sb.toString().trim();
    }

}

