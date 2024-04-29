////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;


import java.util.ArrayList;
import java.util.List;

/**
 * AttributeMap represents an immutable collection of attributes available on a particular element
 * node. An AttributeMap is an ordered collection of AttributeInfo objects. The order of the object
 * represents document order.
 */

public interface AttributeMap extends Iterable<AttributeInfo> {

    /**
     * Return the number of attributes in the map.
     *
     * @return The number of attributes in the map.
     */

    int size();

    /**
     * Get the attribute with a given name, if it exists
     * @param name the name of the required attribute
     * @return the required attribute if it exists
     */

    default AttributeInfo get(NodeName name) {
        for (AttributeInfo att : this) {
            if (att.getNodeName().equals(name)) {
                return att;
            }
        }
        return null;
    }

    /**
     * Get the attribute with a given name, if it exists
     *
     * @param uri the namespace part of the name of the required attribute
     * @param local the local part of the name of the required attribute
     * @return the required attribute if it exists
     */

    default AttributeInfo get(NamespaceUri uri, String local) {
        for (AttributeInfo att : this) {
            NodeName attName = att.getNodeName();
            if (attName.getLocalPart().equals(local) && attName.hasURI(uri)) {
                return att;
            }
        }
        return null;
    }

    default AttributeInfo getByFingerprint(int fingerprint, NamePool namePool) {
        for (AttributeInfo att : this) {
            NodeName attName = att.getNodeName();
            if (attName.obtainFingerprint(namePool) == fingerprint) {
                return att;
            }
        }
        return null;
    }

    /**
     * Get the value of the attribute with a given name, if it exists
     *
     * @param uri the namespace URI part of the name of the required attribute
     * @param local the local part of the name of the required attribute
     * @return the value of the required attribute if it exists, or null otherwise
     */

    default String getValue(NamespaceUri uri, String local) {
        AttributeInfo att = get(uri, local);
        return att==null ? null : att.getValue();
    }

    /**
     * Get the value of a no-namespace attribute with a given local name, if it exists
     *
     * @param local the local part of the name of the required attribute; the attribute
     *              must be in no namespace
     * @return the value of the required attribute if it exists, or null otherwise
     */

    default String getValue(String local) {
        AttributeInfo att = get(NamespaceUri.NULL, local);
        return att == null ? null : att.getValue();
    }


    /**
     * Replace or add an attribute, to create a new AttributeMap
     * @param att the attribute to be added or replaced
     * @return the new AttributeMap
     */

    default AttributeMap put(AttributeInfo att) {
        List<AttributeInfo> list = new ArrayList<>(size() + 1);
        for (AttributeInfo a : this) {
            if (!a.getNodeName().equals(att.getNodeName())) {
                list.add(a);
            }
        }
        list.add(att);
        return SequenceTool.attributeMapFromList(list);
    }

    /**
     * Remove an existing attribute, to create a new AttributeMap
     * @param name the name of the attribute to be removed (if it exists)
     * @return a new attribute map in which the specified attribute is omitted. If the
     * attribute map contains no attribute with the given name, the input attribute map
     * (or one equivalent to it) is returned unchanged
     */

    default AttributeMap remove(NodeName name) {
        List<AttributeInfo> list = new ArrayList<>(size());
        for (AttributeInfo a : this) {
            if (!a.getNodeName().equals(name)) {
                list.add(a);
            }
        }
        return SequenceTool.attributeMapFromList(list);
    }

    /**
     * Verify the consistency of the attribute map. This performs an implementation-defined
     * set of checks that might include checking for duplicates, checking that names are
     * well-formed, checking for inappropriate use of the xml and xmlns namespaces, and
     * checking the constraint that attributes in a namespace have a prefix, and vice
     * versa. The default implementation does nothing.
     * @throws IllegalStateException if the attribute map is invalid.
     */
    default void verify() {}

    /**
     * Apply a function to every attribute in an attribute map, returning a new
     * attribute map
     * @param mapper the mapping function to be applied to each attribute in the attribute map
     * @return the attribute containing the attributes after the mapping function has been
     * applied
     */
    default AttributeMap apply(java.util.function.Function<AttributeInfo, AttributeInfo> mapper) {
        List<AttributeInfo> list = new ArrayList<>(size());
        for (AttributeInfo a : this) {
            list.add(mapper.apply(a));
        }
        return SequenceTool.attributeMapFromList(list);
    }

    /**
     * Get the contents of the AttributeMap as a list of {@link AttributeInfo} objects.
     * <p>The order of the returned list must be consistent with document order, with
     * the order of the attribute axis, and with position-based retrieval of individual
     * {@link AttributeInfo} objects; multiple calls are not required to return the
     * same list, but they must be consistent in their ordering.</p>
     * <p>Modifying the returned list has no effect on the AttributeMap</p>
     * @return a list of attributes in the AttributeMap
     */

    default ArrayList<AttributeInfo> asList() {
        ArrayList<AttributeInfo> list = new ArrayList<>(size());
        for (AttributeInfo a : this) {
            list.add(a);
        }
        return list;
    }

    /**
     * Get the AttributeInfo with a given index.
     * @param index the index position, zero-based. The order of index positions
     *              of attributes in an attribute map reflects document order.
     * @return the AttributeInfo at the given position. In an immutable tree the result will always
     * be equivalent to calling {@code asList().get(index)}. However, if the tree has been modified,
     * then the index values of the attributes may not be contiguous.
     * @throws IndexOutOfBoundsException if the index is out of range
     */

    default AttributeInfo itemAt(int index) {
        return asList().get(index);
    }

}

