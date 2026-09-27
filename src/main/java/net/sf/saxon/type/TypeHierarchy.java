////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.Configuration;
import net.sf.saxon.pattern.UnionQNameTest;
import net.sf.saxon.pattern.qname.QNameTest;
import net.sf.saxon.type.gnode.NamedXNodeType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static net.sf.saxon.type.Affinity.DISJOINT;
import static net.sf.saxon.type.Affinity.SAME_TYPE;
import static net.sf.saxon.type.Affinity.SUBSUMED_BY;
import static net.sf.saxon.type.Affinity.SUBSUMES;

/**
 * This class exists to provide answers to questions about the type hierarchy. Because
 * such questions are potentially expensive, it caches the answers. There is one instance of
 * this class for a Configuration.
 *
 * The actual logic for computing relationships (without any cache) is in class
 * {@link Subsumption}.
 */

public class TypeHierarchy {

    private final Map<ItemTypePair, Affinity> map;
    protected Configuration config;

    /**
     * Create the type hierarchy cache for a configuration
     *
     * @param config the configuration
     */

    public TypeHierarchy(Configuration config) {
        this.config = config;
        map = new ConcurrentHashMap<>();
    }

    /**
     * Get the nearest named type in the type hierarchy, that is, the nearest type that
     * is not anonymous. (In practice, since types cannot be derived from anonymous types,
     * this will either the type itself, or its immediate base type).
     * @return the nearest type, found by following the {@code getBaseType()} relation
     * recursively, that is not an anonymous type
     */

    public static SchemaType getNearestNamedType(SchemaType type) {
        while (type.isAnonymousType()) {
            type = type.getBaseType();
        }
        return type;
    }

    /**
     * Get the Saxon configuration to which this type hierarchy belongs
     *
     * @return the configuration
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Determine whether type A is type B or one of its subtypes, recursively.
     * "Subtype" here means a type that is subsumed, that is, a type whose instances
     * are a subset of the instances of the other type.
     *
     * @param subtype   identifies the first type
     * @param supertype identifies the second type
     * @return true if the first type is the second type or is subsumed by the second type
     */

    public boolean isSubType(ItemType subtype, /*@NotNull*/ ItemType supertype) {
        if (supertype instanceof BuiltInAtomicType bat) {
            return bat.hasSubType(subtype);
        }
        Affinity relation = relationship(subtype, supertype);
        return relation == SAME_TYPE || relation == SUBSUMED_BY;
    }

    /**
     * Determine the relationship of one item type to another.
     *
     * @param t1 the first item type
     * @param t2 the second item type
     * @return One of:
     * <ul><li>{@link Affinity#SAME_TYPE} if the types are the same;</li>
     *     <li>{@link Affinity#SUBSUMES} if the first
     *         type subsumes the second (that is, all instances of the second type are also instances
     *         of the first);</li>
     *     <li>{@link Affinity#SUBSUMED_BY} if the second type subsumes the first;</li>
     *     <li>{@link Affinity#OVERLAPS} if the two types overlap (have a non-empty intersection, but neither
     *         subsumes the other);</li>
     *     <li>{@link Affinity#DISJOINT} if the two types are disjoint (have an empty intersection)</li></ul>
     */

    public Affinity relationship(ItemType t1, ItemType t2) {
        Objects.requireNonNull(t1);
        Objects.requireNonNull(t2);
        t1 = t1.normalizeItemType();
        t2 = t2.normalizeItemType();
        if (t1.equals(t2)) {
            return SAME_TYPE;
        }
        // Before we look in the cache, which involves computing hash keys, check for some simple and common cases
        if (t2 instanceof AnyItemType) {
            return SUBSUMED_BY;
        }
        if (t1 instanceof AnyItemType) {
            return SUBSUMES;
        }
        if (t1 instanceof BuiltInAtomicType && t2 instanceof BuiltInAtomicType) {
            if (t1.getBasicAlphaCode().startsWith(t2.getBasicAlphaCode())) {
                return SUBSUMED_BY;
            } else if (t2.getBasicAlphaCode().startsWith(t1.getBasicAlphaCode())) {
                return SUBSUMES;
            } else {
                return DISJOINT;
            }
        }
        if (t1 instanceof ErrorType) {
            return SUBSUMED_BY;
        }
        if (t2 instanceof ErrorType) {
            return SUBSUMES;
        }
        ItemTypePair pair = new ItemTypePair(t1, t2);
        if (map.containsKey(pair)) {
            return map.get(pair);
        }
        Affinity affinity = Subsumption.computeRelationship(t1, t2);
        map.put(pair, affinity);
        return affinity;
    }


    /**
     * Replace an item type, where necessary, by one that can safely be stored in the cache
     * without causing garbage collection problems. Specifically, a SameNameTest cannot be stored
     * in the cache because it contains a reference to a node in a document.
     * @param in the supplied item type
     * @return either the supplied item type, or an equivalent that can safely be cached.
     */

    public static ItemType normalizeItemType(ItemType in) {
        // Convert element(a|b, T) to element(a, T) | element(b, T)
        if (in instanceof NamedXNodeType && ((NamedXNodeType)in).getAllowedNodeNames() instanceof UnionQNameTest) {
            Configuration config = ((NamedXNodeType) in).getConfiguration();
            Set<QNameTest> tests = ((UnionQNameTest) ((NamedXNodeType) in).getAllowedNodeNames()).getTests();
            SchemaType contentType = ((NamedXNodeType)in).getContentType();
            int nodeKind = ((NamedXNodeType) in).getNodeKind();
            List<ItemType> memberTypes = new ArrayList<>();
            boolean nillable = ((NamedXNodeType) in).isNillable();
            for (QNameTest test : tests) {
                NamedXNodeType contentTypeTest = new NamedXNodeType(
                        nodeKind, test, contentType, nillable, config);
                memberTypes.add(contentTypeTest);
            }
            return new ChoiceItemType(memberTypes);
        }
        return in;
    }




    /**
     * Convert a collection to a set
     *
     * @param in  the input collection
     * @param <X> the member type of the collection
     * @return a set with the same members as the supplied collection
     */


    public static <X> Set<X> toSet(Iterable<X> in) {
        Set<X> s = new HashSet<>();
        for (X x : in) {
            s.add(x);
        }
        return s;
    }

    /**
     * A pair of item types, used as a cache key
     * @param s one item type
     * @param t another item type
     */
    private record ItemTypePair (ItemType s, ItemType t) {
    }


}

