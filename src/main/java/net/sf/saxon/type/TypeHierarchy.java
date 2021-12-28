////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.functions.hof.FunctionSequenceCoercer;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.FunctionAnnotationHandler;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.pattern.*;
import net.sf.saxon.query.Annotation;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.*;
import net.sf.saxon.z.IntHashSet;
import net.sf.saxon.z.IntSet;
import net.sf.saxon.z.IntUniversalSet;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static net.sf.saxon.type.Affinity.*;

/**
 * This class exists to provide answers to questions about the type hierarchy. Because
 * such questions are potentially expensive, it caches the answers. There is one instance of
 * this class for a Configuration.
 */

public class TypeHierarchy {

    private Map<ItemTypePair, Affinity> map;
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
     * Apply the function conversion rules to a value, given a required type.
     *
     * @param value        a value to be converted
     * @param requiredType the required type
     * @param role identifies the value to be converted in error messages
     * @param locator identifies the location for error messages
     * @return the converted value
     * @throws net.sf.saxon.trans.XPathException
     *          if the value cannot be converted to the required type
     */

    public Sequence applyFunctionConversionRules(Sequence value, SequenceType requiredType, final RoleDiagnostic role, Location locator)
            throws XPathException {
        ItemType suppliedItemType = SequenceTool.getItemType(value, this);

        SequenceIterator iterator = value.iterate();
        final ItemType requiredItemType = requiredType.getPrimaryType();

        if (requiredItemType.isPlainType()) {

            // step 1: apply atomization if necessary

            if (!suppliedItemType.isPlainType()) {
                try {
                    iterator = Atomizer.getAtomizingIterator(iterator, false);
                } catch (XPathException e) {
                    ValidationFailure vf = new ValidationFailure(
                            "Failed to atomize the " + role.getMessage() + ": " + e.getMessage());
                    vf.setErrorCode("XPTY0117");
                    throw vf.makeException();
                }
                suppliedItemType = suppliedItemType.getAtomizedItemType();
            }

            // step 2: convert untyped atomic values to target item type

            if (relationship(suppliedItemType, BuiltInAtomicType.UNTYPED_ATOMIC) != DISJOINT &&
                    !isSubType(BuiltInAtomicType.UNTYPED_ATOMIC, requiredItemType)) {
                final boolean nsSensitive = ((SimpleType) requiredItemType).isNamespaceSensitive();
                ItemMappingFunction converter;
                if (nsSensitive) {
                    converter = item -> {
                        if (item instanceof UntypedAtomicValue) {
                            ValidationFailure vf = new ValidationFailure(
                                    "Failed to convert the " + role.getMessage() + ": " +
                                    "Implicit conversion of untypedAtomic value to " + requiredItemType +
                                            " is not allowed");
                            vf.setErrorCode("XPTY0117");
                            throw vf.makeException();
                        } else {
                            return item;
                        }
                    };
                } else if (((SimpleType) requiredItemType).isUnionType()) {
                    final ConversionRules rules = config.getConversionRules();
                    converter = item -> {
                        if (item instanceof UntypedAtomicValue) {
                            try {
                                return ((SimpleType) requiredItemType).getTypedValue(
                                        item.getStringValueCS(), null, rules).head();
                            } catch (ValidationException ve) {
                                ve.setErrorCode("XPTY0004");
                                throw ve;
                            }
                        } else {
                            return item;
                        }
                    };
                } else {
                    converter = item -> {
                        if (item instanceof UntypedAtomicValue) {
                            return Converter.convert(
                                    (UntypedAtomicValue) item, (AtomicType) requiredItemType, config.getConversionRules());
                        } else {
                            return item;
                        }
                    };
                }
                iterator = new ItemMappingIterator(iterator, converter, true);
            }

            // step 3: apply numeric promotion

            if (requiredItemType.equals(BuiltInAtomicType.DOUBLE)) {
                ItemMappingFunction promoter = item -> {
                    if (item instanceof NumericValue) {
                        return (DoubleValue) Converter.convert(
                                (NumericValue)item, BuiltInAtomicType.DOUBLE, config.getConversionRules()).asAtomic();
                    } else {
                        throw new XPathException(
                                "Failed to convert the " + role.getMessage() + ": " +
                                "Cannot promote non-numeric value to xs:double", "XPTY0004");
                    }
                };
                iterator = new ItemMappingIterator(iterator, promoter, true);
            } else if (requiredItemType.equals(BuiltInAtomicType.FLOAT)) {
                ItemMappingFunction promoter = item -> {
                    if (item instanceof DoubleValue) {
                        throw new XPathException(
                                "Failed to convert the " + role.getMessage() + ": " +
                                "Cannot promote xs:double value to xs:float", "XPTY0004");
                    } else if (item instanceof NumericValue) {
                        return (FloatValue) Converter.convert(
                                (NumericValue)item, BuiltInAtomicType.FLOAT, config.getConversionRules()).asAtomic();
                    } else {
                        throw new XPathException(
                                "Failed to convert the " + role.getMessage() + ": " +
                                "Cannot promote non-numeric value to xs:float", "XPTY0004");
                    }
                };
                iterator = new ItemMappingIterator(iterator, promoter, true);
            }

            // step 4: apply URI-to-string promotion

            if (requiredItemType.equals(BuiltInAtomicType.STRING) &&
                    relationship(suppliedItemType, BuiltInAtomicType.ANY_URI) != DISJOINT) {
                ItemMappingFunction promoter = item -> {
                    if (item instanceof AnyURIValue) {
                        return new StringValue(item.getStringValueCS());
                    } else {
                        return item;
                    }
                };
                iterator = new ItemMappingIterator(iterator, promoter, true);
            }
        }

        // step 5: apply function coercion

        iterator = applyFunctionCoercion(iterator, suppliedItemType, requiredItemType, locator);

        // Add a check that the values conform to the required type

        Affinity relation = relationship(suppliedItemType, requiredItemType);

        if (!(relation == SAME_TYPE || relation == SUBSUMED_BY)) {
            ItemTypeCheckingFunction itemChecker =
                    new ItemTypeCheckingFunction(requiredItemType, role, locator, config);
            iterator = new ItemMappingIterator(iterator, itemChecker, true);
        }

        if (requiredType.getCardinality() != StaticProperty.ALLOWS_ZERO_OR_MORE) {
            iterator = new CardinalityCheckingIterator(iterator, requiredType.getCardinality(), role, locator);
        }

        return SequenceTool.toMemoSequence(iterator);

    }

    /**
     * Apply function coercion when a function item is supplied as a parameter in a function call
     *
     * @param iterator         An iterator over the supplied value of the parameter
     * @param suppliedItemType the item type of the supplied value
     * @param requiredItemType the required item type (typically a function item type)
     * @param locator          information for diagnostics
     * @return an iterator over the converted value
     */

    protected SequenceIterator applyFunctionCoercion(
            SequenceIterator iterator,
            ItemType suppliedItemType, ItemType requiredItemType,
            Location locator) {
        if (requiredItemType instanceof FunctionItemType && !((FunctionItemType) requiredItemType).isMapType()
                && !((FunctionItemType) requiredItemType).isArrayType()
                && !(relationship(requiredItemType, suppliedItemType) == Affinity.SUBSUMES)) {

            if (requiredItemType == AnyFunctionType.getInstance()) {
                // no action (the type checking is added later)
                return iterator;

            } else {

                FunctionSequenceCoercer.Coercer coercer = new FunctionSequenceCoercer.Coercer(
                        (SpecificFunctionType) requiredItemType, config, locator);
                return new ItemMappingIterator(iterator, coercer, true);
            }

        } else {
            return iterator;
        }
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

    public Affinity relationship(/*@Nullable*/ ItemType t1, /*@NotNull*/ ItemType t2) {
        Objects.requireNonNull(t1);
        Objects.requireNonNull(t2);
        t1 = stabilize(t1);
        t2 = stabilize(t2);
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
        Affinity result = map.get(pair);
        if (result == null) {
            result = computeRelationship(t1, t2);
            map.put(pair, result);
        }
        return result;
    }

    /**
     * Replace an item type, where necessary, by one that can safely be stored in the cache
     * without causing garbage collection problems. Specifically, a SameNameTest cannot be stored
     * in the cache because it contains a reference to a node in a document.
     * @param in the supplied item type
     * @return either the supplied item type, or an equivalent that can safely be cached.
     */

    private static ItemType stabilize(ItemType in) {
        if (in instanceof SameNameTest) {
            // we don't want to put a SameNameTest in the cache because it locks down the referenced document
            return ((SameNameTest)in).getEquivalentNameTest();
        } else {
            return in;
        }
    }

    /**
     * Determine the relationship of one item type to another. This should be equivalent to the
     * rules for subtype-itemType(t1, t2) (in the XPath31 specification section 2.5.6.2), except that
     * we are computing a more precise relationship:
     *
     * <ul>
     *     <li>If subtype(A, B) and subtype(B, A) then SAME_TYPE</li>
     *     <li>Else, if subtype(A, B) then SUBSUMED_BY</li>
     *     <li>Else, if subtype(A, B) then SUBSUMES</li>
     *     <li>Else, if the value spaces of A and B have a non-empty intersection then OVERLAPS</li>
     *     <li>Else, DISJOINT.</li>
     * </ul>
     *
     * @param t1 the first item type
     * @param t2 the second item type
     * @return {@link Affinity#SAME_TYPE} if the types are the same; {@link Affinity#SUBSUMES} if the first
     *         type subsumes the second (that is, all instances of the second type are also instances
     *         of the first); {@link Affinity#SUBSUMED_BY} if the second type subsumes the first;
     *         {@link Affinity#OVERLAPS} if the two types overlap (have a non-empty intersection, but neither
     *         subsumes the other); {@link Affinity#DISJOINT} if the two types are disjoint (have an empty intersection)
     */

    private Affinity computeRelationship(ItemType t1, ItemType t2) {
        //System.err.println("computeRelationship " + t1 + ", " + t2);
        requireTrueItemType(t1);
        requireTrueItemType(t2);
        try {
            if (t1 == t2) {
                return SAME_TYPE;
            }
            if (t1 instanceof AnyItemType) {
                if (t2 instanceof AnyItemType) {
                    return SAME_TYPE;
                } else {
                    return SUBSUMES;
                }
            } else if (t2 instanceof AnyItemType) {
                return SUBSUMED_BY;
            } else if (t1.isPlainType()) {
                if (t2 instanceof NodeTest || t2 instanceof FunctionItemType || t2 instanceof JavaExternalObjectType) {
                    return DISJOINT;
                } else if (t1 == BuiltInAtomicType.ANY_ATOMIC && t2.isPlainType()) {
                    return SUBSUMES;
                } else if (t2 == BuiltInAtomicType.ANY_ATOMIC) {
                    return SUBSUMED_BY;
                } else if (t1 instanceof AtomicType && t2 instanceof AtomicType) {
                    if (((AtomicType) t1).getFingerprint() == ((AtomicType) t2).getFingerprint()) {
                        return SAME_TYPE;
                    }
                    AtomicType t = (AtomicType)t2;
                    while (true) {
                        if (((AtomicType) t1).getFingerprint() == t.getFingerprint()) {
                            return SUBSUMES;
                        }
                        SchemaType st = t.getBaseType();
                        if (st instanceof AtomicType) {
                            t = (AtomicType)st;
                        } else {
                            break;
                        }
                    }
                    t = (AtomicType)t1;
                    while (true) {
                        if (t.getFingerprint() == ((AtomicType) t2).getFingerprint()) {
                            return SUBSUMED_BY;
                        }
                        SchemaType st = t.getBaseType();
                        if (st instanceof AtomicType) {
                            t = (AtomicType)st;
                        } else {
                            break;
                        }
                    }
                    return DISJOINT;

                } else if (!t1.isAtomicType() && t2.isPlainType()) {
                    // relationship(union, atomic) or relationship(union, union)
                    Set<? extends PlainType> s1 = toSet(((PlainType) t1).getPlainMemberTypes());
                    Set<? extends PlainType> s2 = toSet(((PlainType) t2).getPlainMemberTypes());

                    if (!unionOverlaps(s1, s2)) {
                        return DISJOINT;
                    }

                    boolean gt = s1.containsAll(s2);
                    boolean lt = s2.containsAll(s1);
                    if (gt && lt) {
                        return SAME_TYPE;
                    } else if (gt) {
                        return SUBSUMES;
                    } else if (lt) {
                        return SUBSUMED_BY;
                    } else if (unionSubsumes(s1, s2)) {
                        return SUBSUMES;
                    } else if (unionSubsumes(s2, s1)) {
                        return SUBSUMED_BY;
                    } else {
                        return OVERLAPS;
                    }

                } else if (t1 instanceof AtomicType) {
                    // relationship (atomic, union)
                    Affinity r = relationship(t2, t1);
                    return inverseRelationship(r);

                } else {
                    // all options exhausted
                    throw new IllegalStateException();
                }
            } else if (t1 instanceof NodeTest) {
                if (t2.isPlainType() || t2 instanceof FunctionItemType) {
                    return DISJOINT;
                } else {
                    // both types are NodeTests
                    if (t1 instanceof AnyNodeTest) {
                        if (t2 instanceof AnyNodeTest) {
                            return SAME_TYPE;
                        } else {
                            return SUBSUMES;
                        }
                    } else if (t2 instanceof AnyNodeTest) {
                        return SUBSUMED_BY;
                    } else if (t2 instanceof ErrorType) {
                        return DISJOINT;
                    } else {
                        // first find the relationship between the node kinds allowed
                        Affinity nodeKindRelationship;
                        UType m1 = t1.getUType();
                        UType m2 = t2.getUType();
                        if (!m1.overlaps(m2)) {
                            return DISJOINT;
                        } else if (m1.equals(m2)) {
                            nodeKindRelationship = SAME_TYPE;
                        } else if (m2.subsumes(m1)) {
                            nodeKindRelationship = SUBSUMED_BY;
                        } else if (m1.subsumes(m2)) {
                            nodeKindRelationship = SUBSUMES;
                        } else {
                            nodeKindRelationship = OVERLAPS;
                        }

                        // Now find the relationship between the node names allowed.  See bug 3713
                        Affinity nodeNameRelationship;
                        Optional<IntSet> on1 = ((NodeTest) t1).getRequiredNodeNames();
                        Optional<IntSet> on2 = ((NodeTest) t2).getRequiredNodeNames();

                        if (t1 instanceof QNameTest && t2 instanceof QNameTest) {
                            nodeNameRelationship = nameTestRelationship((QNameTest)t1, (QNameTest)t2);
                        } else if (on1.isPresent() && on1.get() instanceof IntUniversalSet) {
                            if (on2.isPresent() && on2.get() instanceof IntUniversalSet) {
                                nodeNameRelationship = SAME_TYPE;
                            } else {
                                nodeNameRelationship = SUBSUMES;
                            }
                        } else if (on2.isPresent() && on2.get() instanceof IntUniversalSet) {
                            nodeNameRelationship = SUBSUMED_BY;
                        } else if (!(on1.isPresent() && on2.isPresent())) {
                            nodeNameRelationship = t1.equals(t2) ? SAME_TYPE : OVERLAPS;
                        } else {
                            IntSet n1 = on1.get();
                            IntSet n2 = on2.get();
                            if (n1.containsAll(n2)) {
                                if (n1.size() == n2.size()) {
                                    nodeNameRelationship = SAME_TYPE;
                                } else {
                                    nodeNameRelationship = SUBSUMES;
                                }
                            } else if (n2.containsAll(n1)) {
                                nodeNameRelationship = SUBSUMED_BY;
                            } else if (IntHashSet.containsSome(n1, n2)) {
                                nodeNameRelationship = OVERLAPS;
                            } else {
                                nodeNameRelationship = DISJOINT;
                            }
                        }

                        // now find the relationship between the content types allowed

                        Affinity contentRelationship = computeContentRelationship(t1, t2, on1, on2);

                        // now analyse the three different relationsships

                        if (nodeKindRelationship == SAME_TYPE &&
                                nodeNameRelationship == SAME_TYPE &&
                                contentRelationship == SAME_TYPE) {
                            return SAME_TYPE;
                        } else if ((nodeKindRelationship == SAME_TYPE || nodeKindRelationship == SUBSUMES) &&
                                (nodeNameRelationship == SAME_TYPE || nodeNameRelationship == SUBSUMES) &&
                                (contentRelationship == SAME_TYPE || contentRelationship == SUBSUMES)) {
                            return SUBSUMES;
                        } else if ((nodeKindRelationship == SAME_TYPE || nodeKindRelationship == SUBSUMED_BY) &&
                                (nodeNameRelationship == SAME_TYPE || nodeNameRelationship == SUBSUMED_BY) &&
                                (contentRelationship == SAME_TYPE || contentRelationship == SUBSUMED_BY)) {
                            return SUBSUMED_BY;
                        } else if (nodeNameRelationship == DISJOINT || contentRelationship == DISJOINT) {
                            return DISJOINT;
                        } else {
                            return OVERLAPS;
                        }
                    }
                }
            } else if (t1 instanceof AnyExternalObjectType) {
                if (!(t2 instanceof AnyExternalObjectType)) {
                    return DISJOINT;
                }
                if (t1 instanceof JavaExternalObjectType) {
                    if (t2 == AnyExternalObjectType.THE_INSTANCE) {
                        return SUBSUMED_BY;
                    } else if (t2 instanceof JavaExternalObjectType) {
                        return ((JavaExternalObjectType)t1).getRelationship((JavaExternalObjectType)t2);
                    } else {
                        return DISJOINT;
                    }
                }
                if (t2 instanceof JavaExternalObjectType) {
                    return SUBSUMES;
                } else {
                    return DISJOINT;
                }
            } else {
                // t1 is a FunctionItemType
                if (t1 instanceof MapType && t2 instanceof MapType) {
                    if (t1 == MapType.EMPTY_MAP_TYPE) {
                        return SUBSUMED_BY;
                    } else if (t2 == MapType.EMPTY_MAP_TYPE) {
                        return SUBSUMES;
                    }
                    if (t1 == MapType.ANY_MAP_TYPE) {
                        return SUBSUMES;
                    } else if (t2 == MapType.ANY_MAP_TYPE) {
                        return SUBSUMED_BY;
                    }
                    AtomicType k1 = ((MapType)t1).getKeyType();
                    AtomicType k2 = ((MapType)t2).getKeyType();
                    SequenceType v1 = ((MapType)t1).getValueType();
                    SequenceType v2 = ((MapType)t2).getValueType();
                    Affinity keyRel = relationship(k1, k2);
                    Affinity valueRel = sequenceTypeRelationship(v1, v2);
                    Affinity rel = combineRelationships(keyRel, valueRel);
                    if (rel == SAME_TYPE || rel == SUBSUMES || rel == SUBSUMED_BY) {
                        return rel;
                    }
                    // For other relationships, it's more complex because of the need to compare as function type,
                    // so just fall through
                }
                if (t2 instanceof FunctionItemType) {
                    Affinity signatureRelationship = ((FunctionItemType) t1).relationship((FunctionItemType) t2, this);
                    if (signatureRelationship == DISJOINT) {
                        return DISJOINT;
                    } else {
                        Affinity assertionRelationship = SAME_TYPE;
                        AnnotationList first = ((FunctionItemType) t1).getAnnotationAssertions();
                        AnnotationList second = ((FunctionItemType) t2).getAnnotationAssertions();
                        Set<String> namespaces = new HashSet<>();
                        for (Annotation a : first) {
                            namespaces.add(a.getAnnotationQName().getURI());
                        }
                        for (Annotation a : second) {
                            namespaces.add(a.getAnnotationQName().getURI());
                        }
                        for (String ns : namespaces) {
                            FunctionAnnotationHandler handler = config.getFunctionAnnotationHandler(ns);
                            if (handler != null) {
                                Affinity localRel = SAME_TYPE;
                                AnnotationList firstFiltered = first.filterByNamespace(ns);
                                AnnotationList secondFiltered = second.filterByNamespace(ns);
                                if (firstFiltered.isEmpty()) {
                                    if (secondFiltered.isEmpty()) {
                                        // no action
                                    } else {
                                        localRel = SUBSUMES;
                                    }
                                } else {
                                    if (secondFiltered.isEmpty()) {
                                        localRel = SUBSUMED_BY;
                                    } else {
                                        localRel = handler.relationship(firstFiltered, secondFiltered);
                                    }
                                }
                                assertionRelationship = combineRelationships(assertionRelationship, localRel);
                            }
                        }
                        return combineRelationships(signatureRelationship, assertionRelationship);
                    }
                } else {
                    return DISJOINT;
                }
            }
        } catch (MissingComponentException e) {
            return OVERLAPS;
        }
    }

    private static void requireTrueItemType(ItemType t) {
        Objects.requireNonNull(t);
        if (!t.isTrueItemType()) {
            throw new AssertionError(t + " is a non-pure union type");
        }
    }

    private static Affinity nameTestRelationship(QNameTest t1, QNameTest t2) {
        if (t1.equals(t2)) {
            return SAME_TYPE;
        }
        if (t2 instanceof NameTest) {
            return t1.matches(((NameTest) t2).getMatchingNodeName()) ? SUBSUMES : DISJOINT;
        }
        if (t1 instanceof NameTest) {
            return t2.matches(((NameTest) t1).getMatchingNodeName()) ? SUBSUMED_BY : DISJOINT;
        }
        if (t2 instanceof SameNameTest) {
            return t1.matches(((SameNameTest) t2).getMatchingNodeName()) ? SUBSUMES : DISJOINT;
        }
        if (t1 instanceof SameNameTest) {
            return t2.matches(((SameNameTest) t1).getMatchingNodeName()) ? SUBSUMED_BY : DISJOINT;
        }
        if (t1 instanceof NamespaceTest && t2 instanceof NamespaceTest) {
            return DISJOINT;
        }
        if (t1 instanceof LocalNameTest && t2 instanceof LocalNameTest) {
            return DISJOINT;
        }
        return OVERLAPS;
    }

    private static Affinity combineRelationships(Affinity rel1, Affinity rel2) {
        if (rel1 == SAME_TYPE &&
                rel2 == SAME_TYPE) {
            return SAME_TYPE;
        } else if ((rel1 == SAME_TYPE || rel1 == SUBSUMES) &&
                (rel2 == SAME_TYPE || rel2 == SUBSUMES)) {
            return SUBSUMES;
        } else if ((rel1 == SAME_TYPE || rel1 == SUBSUMED_BY) &&
                (rel2 == SAME_TYPE || rel2 == SUBSUMED_BY)) {
            return SUBSUMED_BY;
        } else if (rel1 == DISJOINT ||
                rel2 == DISJOINT) {
            return DISJOINT;
        } else {
            return OVERLAPS;
        }
    }


    /**
     * Convert a collection to a set
     *
     * @param in  the input collection
     * @param <X> the member type of the collection
     * @return a set with the same members as the supplied collection
     */


    private static <X> Set<X> toSet(Iterable<X> in) {
        Set<X> s = new HashSet<>();
        for (X x : in) {
            s.add(x);
        }
        return s;
    }

    /**
     * Ask whether one union type subsumes another
     * @param s1 the member types of the first union type
     * @param s2 the member types of the second union type
     * @return true if every type t2 in s2 is subsumed by the first union type; except that
     * we assume this is the case only if t2 is subsumed by some member type of the first union type.
     */

    private boolean unionSubsumes(Set<? extends PlainType> s1, Set<? extends PlainType> s2) {
        // s1 subsumes s2 if every t2 in s2 is subsumed by some t1 in s1 (we'll discount the possibility
        // of some t2 in s2 being subsumed by a combination of multiple types in s1)
        for (PlainType t2 : s2) {
            boolean t2isSubsumed = false;
            for (PlainType t1 : s1) {
                Affinity rel = relationship(t1, t2);
                if (rel == SUBSUMES || rel == SAME_TYPE) {
                    t2isSubsumed = true;
                    break;
                }
            }
            if (!t2isSubsumed) {
                return false;
            }
        }
        return true;
    }

    /**
     * Ask whether two union types are disjoint
     * @param s1 the set of member types of the first union type
     * @param s2 the set of member types of the second union type
     * @return true if some S1 in s1 has instances in common with some S2 in s2
     */

    private boolean unionOverlaps(Set<? extends PlainType> s1, Set<? extends PlainType> s2) {
        for (PlainType t2 : s2) {
            for (PlainType t1 : s1) {
                Affinity rel = relationship(t1, t2);
                if (rel != DISJOINT) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Compute the relationship between the allowed content-types of two types,
     * for example attribute(*, xs:integer) and attribute(xs:string). Note that
     * although such types are fairly meaningless in a non-schema-aware environment,
     * they are permitted, and supported in Saxon-HE.
     *
     * @param t1 the first type
     * @param t2 the second types
     * @param n1 the set of element names allowed by the first type
     * @param n2 the set of element names allowed by the second type
     * @return the relationship (same type, subsumes, overlaps, subsumed-by)
     */
    protected Affinity computeContentRelationship(ItemType t1, ItemType t2, Optional<IntSet> n1, Optional<IntSet> n2) {
        Affinity contentRelationship;
        if (t1 instanceof DocumentNodeTest) {
            if (t2 instanceof DocumentNodeTest) {
                contentRelationship = relationship(((DocumentNodeTest) t1).getElementTest(),
                        ((DocumentNodeTest) t2).getElementTest());
            } else {
                contentRelationship = SUBSUMED_BY;
            }
        } else if (t2 instanceof DocumentNodeTest) {
            contentRelationship = SUBSUMES;
        } else {
            SchemaType s1 = ((NodeTest) t1).getContentType();
            SchemaType s2 = ((NodeTest) t2).getContentType();
            contentRelationship = schemaTypeRelationship(s1, s2);
        }

        boolean nillable1 = ((NodeTest) t1).isNillable();
        boolean nillable2 = ((NodeTest) t2).isNillable();

        // Adjust the results to take nillability into account
        // Note: although nodes cannot be nilled in a non-schema-aware environment,
        // nillability still affects the relationships between types, for example
        // element(e) and element(e, xs:anyType): see xslt3 test higher-order-functions-034.

        if (nillable1 != nillable2) {
            switch (contentRelationship) {
                case SUBSUMES:
                    if (nillable2) {
                        contentRelationship = OVERLAPS;
                        break;
                    }
                case SUBSUMED_BY:
                    if (nillable1) {
                        contentRelationship = OVERLAPS;
                        break;
                    }
                case SAME_TYPE:
                    if (nillable1) {
                        contentRelationship = SUBSUMES;
                    } else {
                        contentRelationship = SUBSUMED_BY;
                    }
                    break;
                default:
                    break;
            }
        }
        return contentRelationship;
    }

    /**
     * Get the relationship of two sequence types to each other
     * @param s1 the first type
     * @param s2 the second type
     * @return the relationship, as one of the constants
      *         {@link Affinity#SAME_TYPE}, {@link Affinity#SUBSUMES},
      *         {@link Affinity#SUBSUMED_BY}, {@link Affinity#DISJOINT},
     *         {@link Affinity#OVERLAPS}
     */

    public Affinity sequenceTypeRelationship(SequenceType s1, SequenceType s2) {
        int c1 = s1.getCardinality();
        int c2 = s2.getCardinality();
        Affinity cardRel;
        if (c1 == c2) {
            cardRel = SAME_TYPE;
        } else if (Cardinality.subsumes(c1, c2)) {
            cardRel = SUBSUMES;
        } else if (Cardinality.subsumes(c2, c1)) {
            cardRel = SUBSUMED_BY;
        } else if (c1 == StaticProperty.EMPTY && !Cardinality.allowsZero(c2)) {
            return DISJOINT;
        } else if (c2 == StaticProperty.EMPTY && !Cardinality.allowsZero(c1)) {
            return DISJOINT;
        } else {
            cardRel = OVERLAPS;
        }

        Affinity itemRel = relationship(s1.getPrimaryType(), s2.getPrimaryType());

        if (itemRel == DISJOINT) {
            return DISJOINT;
        }

        if (cardRel == SAME_TYPE || cardRel == itemRel) {
            return itemRel;
        }

        if (itemRel == SAME_TYPE) {
            return cardRel;
        }

        return OVERLAPS;
    }

    /**
      * Get the relationship of two schema types to each other
      *
      * @param s1 the first type
      * @param s2 the second type
      * @return the relationship of the two types, as one of the constants
      *         {@link Affinity#SAME_TYPE}, {@link Affinity#SUBSUMES},
      *         {@link Affinity#SUBSUMED_BY}, {@link Affinity#DISJOINT}, {@link Affinity#OVERLAPS}
      */

     public Affinity schemaTypeRelationship(SchemaType s1, SchemaType s2) {
         if (s1.isSameType(s2)) {
             return SAME_TYPE;
         }
         if (s1 instanceof AnyType) {
             return SUBSUMES;
         }
         if (s2 instanceof AnyType) {
             return SUBSUMED_BY;
         }
         if (s1 instanceof Untyped && (s2 == BuiltInAtomicType.ANY_ATOMIC || s2 == BuiltInAtomicType.UNTYPED_ATOMIC)) {
             return OVERLAPS;
         }
         if (s2 instanceof Untyped && (s1 == BuiltInAtomicType.ANY_ATOMIC || s1 == BuiltInAtomicType.UNTYPED_ATOMIC)) {
             return OVERLAPS;
         }
         if (s1 instanceof PlainType && ((PlainType)s1).isPlainType()
                 && s2 instanceof PlainType && ((PlainType) s2).isPlainType()) {
             return relationship((ItemType)s1, (ItemType)s2);

             // See bug 4007. Technically, this isn't quite right. If U is union(X,Y), and V is union(X,Y,Z),
             // then itemType-subtype(U, V) is true (XPath31 2.5.6.2 rule 2), but derives-from(U, V) is false.
             // We're computing the derives-from relationship here (for example, to assess whether element(*, U)
             // is substitutable for element(*, V) in a function signature), and by delegating to test the
             // item type relationship, we are returning true for this case when it should be false.
             // It's not clear whether this difference in the spec is intentional, and it doesn't cause
             // any test cases to fail, so I decided to leave it.  I don't think it causes any problems
             // with type safety, because elements and attributes validated against union(X, Y) will have
             // a type annotation of either X or Y, which means they will be accepted as instances of
             // element(*, union(X,Y,Z)): that is, the instances of element(*, union(X,Y)) are indeed
             // a subset of the instances of element(*, union(X,Y,Z)).               MHK 2018-11-08.
         }
         SchemaType t1 = s1;
         while ((t1 = t1.getBaseType()) != null) {
             if (t1.isSameType(s2)) {
                 return SUBSUMED_BY;
             }
         }
         SchemaType t2 = s2;
         while ((t2 = t2.getBaseType()) != null) {
             if (t2.isSameType(s1)) {
                 return SUBSUMES;
             }
         }
         return DISJOINT;
     }


    public static Affinity inverseRelationship(Affinity relation) {
        switch (relation) {
            case SAME_TYPE:
                return SAME_TYPE;
            case SUBSUMES:
                return SUBSUMED_BY;
            case SUBSUMED_BY:
                return SUBSUMES;
            case OVERLAPS:
                return OVERLAPS;
            case DISJOINT:
                return DISJOINT;
            default:
                throw new IllegalArgumentException();
        }
    }

    public ItemType getGenericFunctionItemType() {
        return AnyItemType.getInstance();
    }



    private static class ItemTypePair {
        ItemType s;
        ItemType t;

        public ItemTypePair(ItemType s, ItemType t) {
            this.s = s;
            this.t = t;
        }

        /**
         * Returns a hash code value for the object.
         *
         * @return a hash code value for this object.
         * @see Object#equals(Object)
         * @see java.util.Hashtable
         */
        public int hashCode() {
            return s.hashCode() ^ t.hashCode();
        }

        /**
         * Indicates whether some other object is "equal to" this one.
         */

        public boolean equals(Object obj) {
            if (obj instanceof ItemTypePair) {
                final ItemTypePair pair = (ItemTypePair) obj;
                return s.equals(pair.s) && t.equals(pair.t);
            } else {
                return false;
            }
        }
    }


}

