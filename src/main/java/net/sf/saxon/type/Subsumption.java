// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

//import com.saxonica.ee.config.SubsumptionEE;
import net.sf.saxon.Configuration;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.lib.FunctionAnnotationHandler;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.jnode.JNodeType;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.pattern.qname.QNameTest;
import net.sf.saxon.query.Annotation;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.type.gnode.*;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

import java.util.*;

import static net.sf.saxon.type.Affinity.DISJOINT;
import static net.sf.saxon.type.Affinity.OVERLAPS;
import static net.sf.saxon.type.Affinity.SAME_TYPE;
import static net.sf.saxon.type.Affinity.SUBSUMED_BY;
import static net.sf.saxon.type.Affinity.SUBSUMES;

/**
 * Utility class (static methods only) for computing relationships among classes.
 * This logic is configuration-independent and stateless. It is often accessed
 * via class {@link net.sf.saxon.type.TypeHierarchy}, which maintains a cache
 * of the results, local to a {@link Configuration}.
 */


public abstract class Subsumption {
    /**
     * Determine the relationship of one item type to another. This should be equivalent to the
     * rules for subtype-itemType(t1, t2) (in the XPath31 specification section 2.5.6.2), except that
     * we are computing a more precise relationship:
     *
     * <ul>
     *     <li>If subtype(A, B) and subtype(B, A) then SAME_TYPE</li>
     *     <li>Else, if subtype(A, B) then SUBSUMED_BY</li>
     *     <li>Else, if subtype(B, A) then SUBSUMES</li>
     *     <li>Else, if the value spaces of A and B have a non-empty intersection then OVERLAPS</li>
     *     <li>Else, DISJOINT.</li>
     * </ul>
     *
     * @param t1 the first item type
     * @param t2 the second item type
     * @return {@link Affinity#SAME_TYPE} if the types are the same; {@link Affinity#SUBSUMES} if the first
     * type subsumes the second (that is, all instances of the second type are also instances
     * of the first); {@link Affinity#SUBSUMED_BY} if the second type subsumes the first;
     * {@link Affinity#OVERLAPS} if the two types overlap (have a non-empty intersection, but neither
     * subsumes the other); {@link Affinity#DISJOINT} if the two types are disjoint (have an empty intersection)
     */

//    public static Affinity computeRelationship(ItemType t1, ItemType t2) {
//        Affinity rel = computeRelationship0(t1, t2);
//        System.err.println("computeRelationship(" + t1.getFullAlphaCode() + ", " + t2.getFullAlphaCode() + ") = " + rel);
//        return rel;
//    }

    public static Affinity computeRelationship (ItemType t1, ItemType t2){
        if (t1 == t2) {
            return SAME_TYPE;
        }

        if (t1 instanceof EnumerationUnionType && !(t2 instanceof EnumerationUnionType)) {
            if (t2 == BuiltInAtomicType.STRING) {
                return SUBSUMED_BY;
            } else {
                return computeRelationship(BuiltInAtomicType.STRING, t2);
            }
        }

        if (t2 instanceof EnumerationUnionType && !(t1 instanceof EnumerationUnionType)) {
            if (t1 == BuiltInAtomicType.STRING) {
                return SUBSUMES;
            } else {
                return computeRelationship(t1, BuiltInAtomicType.STRING);
            }
        }

        if (t1 instanceof ChoiceItemType || t2 instanceof ChoiceItemType) {
            ChoiceItemType c1 = t1.normalizeItemType().asChoiceItemType().expand();
            ChoiceItemType c2 = t2.normalizeItemType().asChoiceItemType().expand();
            return choiceItemRelationship(c1, c2);
        }

        if (t2 == ErrorType.getInstance()) {
            return SUBSUMES;
        }

        return switch (t1.getGenre()) {
            case ANY -> relationshipFromAny(t1, t2);
            case MAP -> relationshipFromMap((FunctionItemType) t1, t2);
            case ARRAY -> relationshipFromArray((ArrayItemType) t1, t2);
            case JNODE -> relationshipFromJNode((JNodeType) t1, t2);
            case XNODE -> relationshipFromXNode((XNodeType) t1, t2);
            case ATOMIC -> relationshipFromAtomic((PlainType) t1, t2);
            case FUNCTION -> relationshipFromFunction((FunctionItemType) t1, t2);
            case EXTERNAL -> relationshipFromExternal((ExternalObjectType) t1, t2);
        };
    }

    private static Affinity relationshipFromAny(ItemType t1, ItemType t2) {
        if (t1 == AnyItemType.INSTANCE) {
            return SUBSUMES;
        }
        if (t1 instanceof AnyGNodeType) {
            return t2 instanceof GNodeType ? SUBSUMES : DISJOINT;
        }
        if (t1 == ErrorType.getInstance()) {
            return SUBSUMED_BY;
        }
        throw new IllegalArgumentException("Unsupported item type: " + t1);
    }

    private static Affinity relationshipFromMap(FunctionItemType f1, ItemType t2) {
        if (t2 == AnyItemType.INSTANCE || t2 == MapType.ANY_MAP_TYPE || t2 == AnyFunctionType.INSTANCE) {
            return SUBSUMED_BY;
        }
        if (t2 == MapType.EMPTY_MAP_TYPE) {
            return SUBSUMES;
        }
        if (f1 instanceof MapType m1) {
            if (t2 instanceof MapType m2) {
                PlainType k1 = m1.getKeyType();
                PlainType k2 = m2.getKeyType();
                SequenceType v1 = m1.getValueType();
                SequenceType v2 = m2.getValueType();
                Affinity keyRel = computeRelationship(k1, k2);
                Affinity valueRel = sequenceTypeRelationship(v1, v2);
                Affinity rel = combineRelationships(keyRel, valueRel);
                if (rel == SAME_TYPE || rel == SUBSUMES || rel == SUBSUMED_BY) {
                    return rel;
                }
                // For other relationships, it's more complex because of the need to compare as function type,
                // so just fall through
            }
        }
        if (t2 instanceof FunctionItemType f2) {
            // includes the case where f1 is a record type
           return f1.relationship(f2);
        }

        return DISJOINT;

    }

    private static Affinity relationshipFromArray(ArrayItemType a1, ItemType t2) {
        if (t2 == AnyItemType.INSTANCE || t2 == ArrayItemType.ANY_ARRAY_TYPE || t2 == AnyFunctionType.INSTANCE) {
            return SUBSUMED_BY;
        }
        if (t2 instanceof ArrayItemType a2) {
            return sequenceTypeRelationship(a1.getMemberType(), a2.getMemberType());
        }
        if (t2 instanceof FunctionItemType f2) {
            return a1.relationship(f2);
        }
        return DISJOINT;
    }

    private static Affinity relationshipFromJNode(JNodeType j1, ItemType t2) {
        if (t2 == AnyItemType.INSTANCE || t2 == AnyGNodeType.getInstance()) {
            return SUBSUMED_BY;
        }
        if (t2 instanceof JNodeType j2) {
            return sequenceTypeRelationship(j1.getValueType(), j2.getValueType());
        }
        return DISJOINT;
    }

    private static Affinity relationshipFromAtomic(PlainType p1, ItemType t2) {
        if (p1 instanceof AtomicType a1) {
            if (t2 == BuiltInAtomicType.ANY_ATOMIC || t2 == AnyItemType.INSTANCE) {
                return SUBSUMED_BY;
            }
            if (a1 == BuiltInAtomicType.ANY_ATOMIC) {
                return t2 instanceof AtomicType ? SUBSUMES : DISJOINT;
            }
            if (t2 instanceof AtomicType a2) {

                if (a1.getFingerprint() == a2.getFingerprint()) {
                    return SAME_TYPE;
                }
                // For built-in atomic types, use the alphacode, which is designed to
                // capture the type hierarchy: for example AD = decimal, ADI = integer, ADIL = long.
                if (a1 instanceof BuiltInAtomicType && a2 instanceof BuiltInAtomicType) {
                    String alpha1 = a1.getBasicAlphaCode();
                    String alpha2 = a2.getBasicAlphaCode();
                    if (alpha1.startsWith(alpha2)) {
                        return SUBSUMED_BY;
                    }
                    if (alpha2.startsWith(alpha1)) {
                        return SUBSUMES;
                    }
                    return DISJOINT;
                }
                if (derivesFrom(a2, a1)) {
                    return SUBSUMES;
                }
                if (derivesFrom(a1, a2)) {
                    return SUBSUMED_BY;
                }

                return DISJOINT;
            }
            if (t2.isPlainType()) {
                // relationship(atomic, union)
                Affinity inv = computeRelationship(t2, p1);
                return inverseRelationship(inv);
            }
        } else {
            if (t2 instanceof PlainType p2 && t2.isPlainType()) {
                // relationship(union, atomic) or relationship(union, union)
                Set<? extends PlainType> s1 = toSet(p1.getPlainMemberTypes());
                Set<? extends PlainType> s2 = toSet(p2.getPlainMemberTypes());

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

            } else {
                Affinity inv = computeRelationship(t2, p1);
                return inverseRelationship(inv);
            }
        }

        return DISJOINT;
    }

    private static Affinity relationshipFromXNode(XNodeType n1, ItemType t2) {

        ItemType t1 = n1.normalizeItemType();
        if (t1 instanceof ChoiceItemType) {
            return computeRelationship(t1, t2);
        }

        if (t2 == AnyXNodeType.getInstance() || t2 == AnyGNodeType.getInstance() || t2 == AnyItemType.INSTANCE) {
            return SUBSUMED_BY;
        }

        if (t2 instanceof AnyGNodeType) {
            return SUBSUMED_BY;
        }
        if (t2 instanceof XNodeType n2) {

            // Compare schema-element and schema-attribute tests (Saxon bug 6910)

            Optional<Affinity> schemaElementAffinity = schemaDeclarationAffinity(n1, n2);
            if (schemaElementAffinity.isPresent()) {
                return schemaElementAffinity.get();
            }

            // first find the relationship between the node kinds allowed
            Affinity nodeKindRelationship;
            UType m1 = n1.getUType();
            UType m2 = n2.getUType();
            if (!m1.overlaps(m2)) {
                return DISJOINT;
            } else if (m1.equals(m2)) {
                nodeKindRelationship = SAME_TYPE;
            } else if (m2.subsumes(m1)) {
                nodeKindRelationship = SUBSUMED_BY;
            } else if (m1.subsumes(m2)) {
                nodeKindRelationship = SUBSUMES;
            } else {
                return OVERLAPS;
            }


            // Now find the relationship between the node names allowed.  See bug 3713
            Affinity nodeNameRelationship = nameTestRelationship(
                    n1.getAllowedNodeNames(),
                    n2.getAllowedNodeNames());

            if (nodeNameRelationship == DISJOINT || nodeNameRelationship == OVERLAPS) {
                return nodeNameRelationship;
            }

            // now find the relationship between the content types allowed

            Affinity contentRelationship = computeContentRelationship(n1, n2);

            // now analyse the three different relationships

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
            } else if (contentRelationship == DISJOINT) {
                return DISJOINT;
            } else {
                return OVERLAPS;
            }
        }
        return DISJOINT;
    }

    private static Affinity relationshipFromFunction(FunctionItemType f1, ItemType t2) {
        if (t2 == AnyFunctionType.INSTANCE || t2 == AnyItemType.INSTANCE) {
            return SUBSUMED_BY;
        }
        if (t2 instanceof ArrayItemType || t2 instanceof MapType) {
            return inverseRelationship(computeRelationship(t2, f1));
        }
        if (t2 instanceof FunctionItemType f2) {
            Affinity signatureRelationship = f1.relationship(f2);
            if (signatureRelationship == DISJOINT) {
                return DISJOINT;
            } else {
                Affinity assertionRelationship = SAME_TYPE;
                AnnotationList first = f1.getAnnotationAssertions();
                AnnotationList second = f2.getAnnotationAssertions();

                Configuration config = null;
                if (!first.isEmpty()) {
                    config = first.getConfiguration();
                } else if (!second.isEmpty()) {
                    config = second.getConfiguration();
                }
                if (config != null) {
                    // implies one of the annotation lists is non-empty
                    Set<NamespaceUri> namespaces = new HashSet<>();
                    for (Annotation a : first) {
                        namespaces.add(a.getAnnotationQName().getNamespaceUri());
                    }
                    for (Annotation a : second) {
                        namespaces.add(a.getAnnotationQName().getNamespaceUri());
                    }
                    for (NamespaceUri ns : namespaces) {
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
                }
                return combineRelationships(signatureRelationship, assertionRelationship);
            }
        } else {
            return DISJOINT;
        }
    }

    private static Affinity relationshipFromExternal(ExternalObjectType e1, ItemType t2) {

        if (!(t2 instanceof AnyExternalObjectType)) {
            return DISJOINT;
        }
        if (e1 instanceof JavaExternalObjectType j1) {
            if (t2 == AnyExternalObjectType.THE_INSTANCE) {
                return SUBSUMED_BY;
            } else if (t2 instanceof JavaExternalObjectType j2) {
                return j1.getRelationship(j2);
            } else {
                return DISJOINT;
            }
        }
        if (t2 instanceof JavaExternalObjectType) {
            return SUBSUMES;
        } else {
            return DISJOINT;
        }
    }

    public static boolean derivesFrom(AtomicType t1, AtomicType t2) {
        SchemaType t = t1;
        do {
            if (t.getFingerprint() == t2.getFingerprint()) {
                return true;
            }
            t = t.getBaseType();
        } while (t instanceof AtomicType);
        return false;
    }

//    private static void requireTrueItemType(ItemType t) {
//        Objects.requireNonNull(t);
//        if (t instanceof UnionType && !t.isPlainType()) {
//            throw new AssertionError(t + " is a non-pure union type");
//        }
//    }

    private static Affinity nameTestRelationship(QNameTest t1, QNameTest t2) {
        if (t1 == null || t2 == null) {
            return SAME_TYPE;
        }
        return t1.relationship(t2);
    }

    public static Affinity combineRelationships(Affinity rel1, Affinity rel2) {
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
     * Compute the relationship between the allowed content-types of two types,
     * for example attribute(*, xs:integer) and attribute(xs:string). Note that
     * although such types are fairly meaningless in a non-schema-aware environment,
     * they are permitted, and supported in Saxon-HE.
     *
     * @param t1 the first type
     * @param t2 the second type
     * @return the relationship, as one of the constants
     * {@link Affinity#SAME_TYPE}, {@link Affinity#SUBSUMES},
     * {@link Affinity#SUBSUMED_BY}, {@link Affinity#DISJOINT}, {@link Affinity#OVERLAPS}
     */
    public static Affinity computeContentRelationship(XNodeType t1, XNodeType t2) {
        Affinity contentRelationship;
        if (t1 instanceof DocumentNodeType) {
            if (t2 instanceof DocumentNodeType) {
                contentRelationship = computeRelationship(((DocumentNodeType) t1).getElementTest(),
                                                          ((DocumentNodeType) t2).getElementTest());
            } else {
                contentRelationship = SUBSUMED_BY;
            }
        } else if (t2 instanceof DocumentNodeType) {
            contentRelationship = SUBSUMES;
        } else {
            SchemaType s1 = t1.getContentType();
            SchemaType s2 = t2.getContentType();
            contentRelationship = schemaTypeRelationship(s1, s2);
        }

        boolean nillable1 = t1.isNillable();
        boolean nillable2 = t2.isNillable();

        // Adjust the results to take nillability into account
        // Note: although nodes cannot be nilled in a non-schema-aware environment,
        // nillability still affects the relationships between types, for example
        // element(e) and element(e, xs:anyType): see xslt3 test higher-order-functions-034.

        if (nillable1 != nillable2) {
            switch (contentRelationship) {
                case SUBSUMES:
                    if (nillable2) {
                        contentRelationship = OVERLAPS;
                    }
                    break;
                case SUBSUMED_BY:
                    if (nillable1) {
                        contentRelationship = OVERLAPS;
                    }
                    break;
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
     *
     * @param s1 the first type
     * @param s2 the second type
     * @return the relationship, as one of the constants
     * {@link Affinity#SAME_TYPE}, {@link Affinity#SUBSUMES},
     * {@link Affinity#SUBSUMED_BY}, {@link Affinity#DISJOINT},
     * {@link Affinity#OVERLAPS}
     */

    public static Affinity sequenceTypeRelationship(SequenceType s1, SequenceType s2) {
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

        boolean e1 = isEmptySequenceType(s1);
        boolean e2 = isEmptySequenceType(s2);
        if (e1 || e2) {
            return e1 && e2 ? SAME_TYPE : cardRel;
        }

        Affinity itemRel = computeRelationship(s1.getPrimaryType(), s2.getPrimaryType());

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

    private static boolean isEmptySequenceType(SequenceType s) {
        return s == SequenceType.EMPTY_SEQUENCE
                || s.getPrimaryType() == ErrorType.getInstance() && Cardinality.allowsZero(s.getCardinality());
    }

    @CSharpReplaceBody(code="Affinity[][] result = new Affinity[rows][];\n"
            + "            for (int i=0; i<rows; i++) {\n"
            + "                result[i] = new Affinity[columns];\n"
            + "            }\n"
            + "            return result;")
    private static Affinity[][] makeAffinityTable(int rows, int columns) {
        return new Affinity[rows][columns];
    }

    public static Affinity choiceItemRelationship(ChoiceItemType c1, ChoiceItemType c2) {
        // Algorithm (issue #1395):  A ⊆ B is true if every item type a among the
        // alternatives of A satisfies a ⊆ b for some item type b among the alternatives of B.
        // However, we are interested in more than just the subtype relationship. So we apply
        // the following rules:
        //
        // * Let SUB := every predicate on the RHS is subsumed by (or same as) a predicate on the LHS
        // * Let SUP := every predicate on the LHS is subsumed by (or same as) a predicate on the RHS
        // * If SUB && SUP return SAME
        // * If SUB return SUBSUMES
        // * If SUP return SUBSUMED_BY
        // * If every pair of item types is disjoint, then DISJOINT
        // * Else OVERLAPS
        //System.err.println("choiceRelationship /// c1=" + c1.getFullAlphaCode() + " /// c2=" + c2.getFullAlphaCode());

        Affinity[][] affinities = makeAffinityTable(c1.memberTypes.size(), c2.memberTypes.size());
        boolean allDIS = true;
        for (int i = 0; i < c1.memberTypes.size(); i++) {
            for (int j = 0; j < c2.memberTypes.size(); j++) {
                affinities[i][j] = computeRelationship(c1.memberTypes.get(i), c2.memberTypes.get(j));
                if (affinities[i][j] != DISJOINT) {
                    allDIS = false;
                }
            }
        }
        if (allDIS) {
            return DISJOINT;
        }

        // Let SUP := every predicate on the LHS is subsumed by (or same as) a predicate on the RHS

        boolean allSUP = true;
        for (int i = 0; i < c1.memberTypes.size(); i++) {
            boolean foundSUP = false;
            for (int j = 0; j < c2.memberTypes.size(); j++) {
                Affinity a = affinities[i][j];
                if (a == SAME_TYPE || a == SUBSUMED_BY) {
                    foundSUP = true;
                    break;
                }
            }
            if (!foundSUP) {
                allSUP = false;
                break;
            }
        }

        // Let SUB := every predicate on the RHS is subsumed by (or same as) a predicate on the LHS

        boolean allSUB = true;
        for (int j = 0; j < c2.memberTypes.size(); j++) {
            boolean foundSUB = false;
            for (int i = 0; i < c1.memberTypes.size(); i++) {
                Affinity a = affinities[i][j];
                if (a == SAME_TYPE || a == SUBSUMES) {
                    foundSUB = true;
                    break;
                }
            }
            if (!foundSUB) {
                allSUB = false;
                break;
            }
        }

        if (allSUB && allSUP) {
            return SAME_TYPE;
        }
        if (allSUB) {
            return SUBSUMES;
        }
        if (allSUP) {
            return SUBSUMED_BY;
        }
        return OVERLAPS;
    }

    /**
     * Get the relationship of two schema types to each other
     *
     * @param s1 the first type
     * @param s2 the second type
     * @return the relationship of the two types, as one of the constants
     * {@link Affinity#SAME_TYPE}, {@link Affinity#SUBSUMES},
     * {@link Affinity#SUBSUMED_BY}, {@link Affinity#DISJOINT}, {@link Affinity#OVERLAPS}
     */

    public static Affinity schemaTypeRelationship(SchemaType s1, SchemaType s2) {
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
        if (s1 instanceof PlainType && ((PlainType) s1).isPlainType()
                && s2 instanceof PlainType && ((PlainType) s2).isPlainType()) {
            return computeRelationship((ItemType) s1, (ItemType) s2);

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
        return switch (relation) {
            case SAME_TYPE -> SAME_TYPE;
            case SUBSUMES -> SUBSUMED_BY;
            case SUBSUMED_BY -> SUBSUMES;
            case OVERLAPS -> OVERLAPS;
            case DISJOINT -> DISJOINT;
        };
    }

    /**
     * Compare two item types if they are both schema-element tests. This method is implemented
     * in a subclass for Saxon-EE: without schema-awareness, schema-element tests do not arise.
     *
     * @param t1 the first item type
     * @param t2 the second item type
     * @return the relationship between the two, if one is a schema-element test; otherwise
     * Optional.empty().
     */
    private static Optional<Affinity> schemaDeclarationAffinity(XNodeType t1, XNodeType t2) {
        Optional<Affinity> result = Optional.empty();
        return result;
    }

        /**
         * Convert a collection to a set
         *
         * @param in  the input collection
         * @param <X> the member type of the collection
         * @return a set with the same members as the supplied collection
         */


        private static <X> Set <X> toSet(Iterable <X> in) {
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

        private static boolean unionSubsumes (Set < ? extends PlainType > s1, Set < ? extends PlainType > s2){
            // s1 subsumes s2 if every t2 in s2 is subsumed by some t1 in s1 (we'll discount the possibility
            // of some t2 in s2 being subsumed by a combination of multiple types in s1)
            for (PlainType t2 : s2) {
                boolean t2isSubsumed = false;
                for (PlainType t1 : s1) {
                    Affinity rel = computeRelationship(t1, t2);
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

        private static boolean unionOverlaps (Set < ? extends PlainType > s1, Set < ? extends PlainType > s2){
            for (PlainType t2 : s2) {
                for (PlainType t1 : s1) {
                    Affinity rel = computeRelationship(t1, t2);
                    if (rel != DISJOINT) {
                        return true;
                    }
                }
            }
            return false;
        }

    }

