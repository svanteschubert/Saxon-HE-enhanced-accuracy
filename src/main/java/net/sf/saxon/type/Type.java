////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.pattern.*;
import net.sf.saxon.value.*;


/**
 * This class contains static information about types and methods for constructing type codes.
 * The class is never instantiated.
 * <p><i>The constant integers used for type names in earlier versions of this class have been replaced
 * by constants in {@link StandardNames}. The constants representing {@link AtomicType} objects are now
 * available through the {@link BuiltInAtomicType} class.</i></p>
 */

public abstract class Type {

    // Note that the integer codes representing node kinds are the same as
    // the codes allocated in the DOM interface, while the codes for built-in
    // atomic types are fingerprints allocated in StandardNames. These two sets of
    // codes must not overlap!

    /**
     * Type representing an element node - element()
     */

    public static final short ELEMENT = 1;
    /**
     * Item type representing an attribute node - attribute()
     */
    public static final short ATTRIBUTE = 2;
    /**
     * Item type representing a text node - text()
     */
    public static final short TEXT = 3;
    /**
     * Item type representing a text node stored in the tiny tree as compressed whitespace
     */
    public static final short WHITESPACE_TEXT = 4;
    /**
     * Item type representing a processing-instruction node
     */
    public static final short PROCESSING_INSTRUCTION = 7;
    /**
     * Item type representing a comment node
     */
    public static final short COMMENT = 8;
    /**
     * Item type representing a document node
     */
    public static final short DOCUMENT = 9;
    /**
     * Item type representing a namespace node
     */
    public static final short NAMESPACE = 13;
    /**
     * Dummy node kind used in the tiny tree to mark the end of the tree
     */
    public static final short STOPPER = 11;
    /**
     * Dummy node kind used in the tiny tree to contain a parent pointer
     */
    public static final short PARENT_POINTER = 12;
    /**
     * Node kind used in the tiny tree to represent an element node having simple text content
     */
    public static final short TEXTUAL_ELEMENT = 17; // Chosen so kind & 0x0f = Type.ELEMENT

    /**
     * An item type that matches any node
     */

    public static final short NODE = 0;

    public static final ItemType NODE_TYPE = AnyNodeTest.getInstance();

    /**
     * An item type that matches any item
     */

    public static final short ITEM = 88;

    /*@NotNull*/ public static final ItemType ITEM_TYPE = AnyItemType.getInstance();

    /**
     * A type number for function()
     */

    public static final short FUNCTION = 99;


    private Type() {
    }

    /**
     * Test whether a given type is (some subtype of) node()
     *
     * @param type The type to be tested
     * @return true if the item type is node() or a subtype of node()
     */

    public static boolean isNodeType(ItemType type) {
        return type instanceof NodeTest;
    }

    /**
     * Get the ItemType of an Item. This method is used almost entirely for diagnostics, to report
     * the actual type of an item when it differs from the required type. The actual result is
     * not completely well-defined, since there are many item types that a given type conforms
     * to, and it is not always the case that one of them is a subtype of all the others.
     *
     * @param item the item whose type is required
     * @param th   the type hierarchy cache. In most cases this can be null, but the returned type may
     *             then be less precise (for example, the type returned for a map or array will simply
     *             be <code>map(*)</code> or <code>array(*)</code>). For external objects, the parameter
     *             must not be null
     * @return the item type of the item
     */

    /*@NotNull*/
    public static ItemType getItemType(/*@NotNull*/ Item item, /*@Nullable*/ TypeHierarchy th) {
        if (item == null) {
            return AnyItemType.getInstance();
        } else if (item instanceof AtomicValue) {
            return ((AtomicValue) item).getItemType();
        } else if (item instanceof NodeInfo) {
            NodeInfo node = (NodeInfo) item;
            if (th == null) {
                th = node.getConfiguration().getTypeHierarchy();
            }
            switch (node.getNodeKind()) {
                case Type.DOCUMENT:
                    // Need to know whether the document is well-formed and if so what the element type is
                    ItemType elementType = null;
                    for (NodeInfo n : node.children()) {
                        int kind = n.getNodeKind();
                        if (kind == Type.TEXT) {
                            elementType = null;
                            break;
                        } else if (kind == Type.ELEMENT) {
                            if (elementType != null) {
                                elementType = null;
                                break;
                            }
                            elementType = Type.getItemType(n, th);
                        }
                    }
                    if (elementType == null) {
                        return NodeKindTest.DOCUMENT;
                    } else {
                        return new DocumentNodeTest((NodeTest) elementType);
                    }

                case Type.ELEMENT:
                    SchemaType eltype = node.getSchemaType();
                    if (eltype.equals(Untyped.getInstance()) || eltype.equals(AnyType.getInstance())) {
                        return new SameNameTest(node);
                    } else {
                        return new CombinedNodeTest(
                                new SameNameTest(node),
                                Token.INTERSECT,
                                new ContentTypeTest(Type.ELEMENT, eltype, node.getConfiguration(), false));
                    }

                case Type.ATTRIBUTE:
                    SchemaType attype = node.getSchemaType();
                    if (attype.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
                        return new SameNameTest(node);
                    } else {
                        return new CombinedNodeTest(
                                new SameNameTest(node),
                                Token.INTERSECT,
                                new ContentTypeTest(Type.ATTRIBUTE, attype, node.getConfiguration(), false));
                    }

                case Type.TEXT:
                    return NodeKindTest.TEXT;

                case Type.COMMENT:
                    return NodeKindTest.COMMENT;

                case Type.PROCESSING_INSTRUCTION:
                    return NodeKindTest.PROCESSING_INSTRUCTION;

                case Type.NAMESPACE:
                    return NodeKindTest.NAMESPACE;

                default:
                    throw new IllegalArgumentException("Unknown node kind " + node.getNodeKind());
            }
        } else if (item instanceof ExternalObject) {
            if (th == null) {
                throw new IllegalArgumentException("typeHierarchy is required for an external object");
            }
            return ((ExternalObject<?>) item).getItemType(th);
        } else if (item instanceof MapItem) {
            return th == null ? MapType.ANY_MAP_TYPE : ((MapItem)item).getItemType(th);
        } else if (item instanceof ArrayItem) {
            return th == null ? ArrayItemType.ANY_ARRAY_TYPE : new ArrayItemType(((ArrayItem) item).getMemberType(th));
        } else { //if (item instanceof FunctionItem) {
            return ((Function) item).getFunctionItemType();
        }
    }


    /**
     * Output (for diagnostics) a representation of the type of an item. This
     * does not have to be the most specific type
     *
     * @param item the item whose type is to be displayed
     * @return a string representation of the type of the item
     */

    public static String displayTypeName(/*@NotNull*/ Item item) {
        if (item instanceof NodeInfo) {
            NodeInfo node = (NodeInfo) item;
            switch (node.getNodeKind()) {
                case DOCUMENT:
                    return "document-node()";
                case ELEMENT:
                    SchemaType annotation = node.getSchemaType();
                    return "element(" +
                            ((NodeInfo) item).getDisplayName() + ", " +
                            annotation.getDisplayName() + ')';
                case ATTRIBUTE:
                    SchemaType annotation2 = node.getSchemaType();
                    return "attribute(" +
                            ((NodeInfo) item).getDisplayName() + ", " +
                            annotation2.getDisplayName() + ')';
                case TEXT:
                    return "text()";
                case COMMENT:
                    return "comment()";
                case PROCESSING_INSTRUCTION:
                    return "processing-instruction()";
                case NAMESPACE:
                    return "namespace()";
                default:
                    return "";
            }
        } else if (item instanceof ExternalObject) {
            return ObjectValue.displayTypeName(((ExternalObject) item).getObject());
        } else if (item instanceof AtomicValue) {
            return ((AtomicValue) item).getItemType().toString();
        } else if (item instanceof Function) {
            return "function(*)";
        } else {
            return item.getClass().toString();
        }
    }

    /**
     * Get the ItemType object for a built-in atomic or list type
     *
     * @param namespace the namespace URI of the type
     * @param localName the local name of the type
     * @return the ItemType, or null if not found
     */

    /*@Nullable*/
    public static ItemType getBuiltInItemType(String namespace, String localName) {
        SchemaType t = BuiltInType.getSchemaType(
                StandardNames.getFingerprint(namespace, localName));
        if (t instanceof ItemType) {
            return (ItemType) t;
        } else {
            return null;
        }
    }

    /**
     * Get the SimpleType object for a built-in simple type (atomic type or list type)
     *
     * @param namespace the namespace URI of the type
     * @param localName the local name of the type
     * @return the SimpleType, or null if not found
     */

    /*@Nullable*/
    public static SimpleType getBuiltInSimpleType(String namespace, String localName) {
        SchemaType t = BuiltInType.getSchemaType(
                StandardNames.getFingerprint(namespace, localName));
        if (t instanceof SimpleType && ((SimpleType) t).isBuiltInType()) {
            return (SimpleType) t;
        } else {
            return null;
        }
    }

    /**
     * Determine whether one atomic type is a subtype of another (without access to the TypeHierarchy object)
     * @param one the first atomic type
     * @param two the second atomic type
     * @return true if the first atomic type is equal to, or derived by restriction from, the second atomic
     * type
     */

    public static boolean isSubType(AtomicType one, AtomicType two) {
        while (true) {
            if (one.getFingerprint() == two.getFingerprint()) {
                return true;
            }
            SchemaType s = one.getBaseType();
            if (s instanceof AtomicType) {
                one = (AtomicType)s;
            } else {
                return false;
            }
        }
    }

    /**
     * Get a type that is a common supertype of two given item types
     *
     * @param t1 the first item type
     * @param t2 the second item type
     * @param th the type hierarchy cache
     * @return the item type that is a supertype of both
     *         the supplied item types
     */

    /*@NotNull*/
    public static ItemType getCommonSuperType(ItemType t1, ItemType t2, TypeHierarchy th) {
        if (t1 == t2) {
            return t1;
        }
        if (t1 instanceof ErrorType) {
            return t2;
        }
        if (t2 instanceof ErrorType) {
            return t1;
        }
        if (t1 instanceof JavaExternalObjectType && t2 instanceof JavaExternalObjectType) {
            Configuration config = ((JavaExternalObjectType) t1).getConfiguration();
            Class c1 = ((JavaExternalObjectType) t1).getJavaClass();
            Class c2 = ((JavaExternalObjectType) t2).getJavaClass();
            return config.getJavaExternalObjectType(leastCommonSuperClass(c1, c2));
        }
        if (t1 instanceof MapType && t2 instanceof MapType) {
            if (t1 == MapType.EMPTY_MAP_TYPE) {
                return t2;
            }
            if (t2 == MapType.EMPTY_MAP_TYPE) {
                return t1;
            }
            ItemType keyType = getCommonSuperType(((MapType) t1).getKeyType(), ((MapType)t2).getKeyType());
            AtomicType k;
            if (keyType instanceof AtomicType) {
                k = (AtomicType)keyType;
            } else {
                k = keyType.getAtomizedItemType().getPrimitiveItemType();
            }
            SequenceType v = SequenceType.makeSequenceType(
                    getCommonSuperType(((MapType) t1).getValueType().getPrimaryType(), ((MapType) t2).getValueType().getPrimaryType()),
                    Cardinality.union(((MapType) t1).getValueType().getCardinality(), ((MapType) t2).getValueType().getCardinality()));
            return new MapType(k, v);
        }
        Affinity r = th.relationship(t1, t2);
        if (r == Affinity.SAME_TYPE) {
            return t1;
        } else if (r == Affinity.SUBSUMED_BY) {
            return t2;
        } else if (r == Affinity.SUBSUMES) {
            return t1;
        } else {
            return t1.getUType().union(t2.getUType()).toItemType();
        }
    }

    /**
     * Get a type that is a common supertype of two given item types, without the benefit of a TypeHierarchy cache.
     * This will generally give a less precise answer than the method {@link #getCommonSuperType(ItemType, ItemType, TypeHierarchy)}
     *
     * @param t1 the first item type
     * @param t2 the second item type
     * @return an item type that is a supertype of both the supplied item types
     */

    /*@NotNull*/
    public static ItemType getCommonSuperType(/*@NotNull*/ ItemType t1, /*@NotNull*/ ItemType t2) {
        if (t1 == t2) {
            return t1;
        }
        if (t1 instanceof ErrorType) {
            return t2;
        }
        if (t2 instanceof ErrorType) {
            return t1;
        }
        if (t1 == AnyItemType.getInstance() || t2 == AnyItemType.getInstance()) {
            return AnyItemType.getInstance();
        }
        ItemType p1 = t1.getPrimitiveItemType();
        ItemType p2 = t2.getPrimitiveItemType();
        if (p1 == p2) {
            return p1;
        }
        if ((p1 == BuiltInAtomicType.DECIMAL && p2 == BuiltInAtomicType.INTEGER) ||
                (p2 == BuiltInAtomicType.DECIMAL && p1 == BuiltInAtomicType.INTEGER)) {
            return BuiltInAtomicType.DECIMAL;
        }
        if (p1 instanceof BuiltInAtomicType && ((BuiltInAtomicType) p1).isNumericType() &&
                p2 instanceof BuiltInAtomicType && ((BuiltInAtomicType) p2).isNumericType()) {
            return NumericType.getInstance();
        }
        if (t1.isAtomicType() && t2.isAtomicType()) {
            return BuiltInAtomicType.ANY_ATOMIC;
        }
        if (t1 instanceof NodeTest && t2 instanceof NodeTest) {
            return AnyNodeTest.getInstance();
        }
        if (t1 instanceof JavaExternalObjectType && t2 instanceof JavaExternalObjectType) {
            Configuration config = ((JavaExternalObjectType) t1).getConfiguration();
            Class c1 = ((JavaExternalObjectType) t1).getJavaClass();
            Class c2 = ((JavaExternalObjectType) t2).getJavaClass();
            return config.getJavaExternalObjectType(leastCommonSuperClass(c1, c2));
        }
        return AnyItemType.getInstance();

        // Note: for function items, the result is always AnyFunctionType, since all functions have the same primitive type

    }

    public static SequenceType getCommonSuperType(SequenceType t1, SequenceType t2) {
        if (t1.equals(t2)) {
            return t1;
        } else {
            return SequenceType.makeSequenceType(getCommonSuperType(t1.getPrimaryType(), t2.getPrimaryType()),
                                                 Cardinality.union(t1.getCardinality(), t2.getCardinality()));
        }
    }

    private static Class<?> leastCommonSuperClass(Class<?> class1, Class<?> class2) {
        if (class1 == class2) {
            return class1;
        }
        if (class1 == null || class2 == null) {
            return null;
        }
        if (!class1.isArray() && class1.isAssignableFrom(class2)) {
            return class1;
        }
        if (!class2.isArray() && class2.isAssignableFrom(class1)) {
            return class2;
        }
        if (class1.isInterface() || class2.isInterface()) {
            return Object.class;
        }
        return leastCommonSuperClass(class1.getSuperclass(), class2.getSuperclass());
    }


    /**
     * Determine whether a given atomic type is a primitive type. The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration;
     * xs:untypedAtomic; the 7 node kinds; and all supertypes of these (item(), node(), xs:anyAtomicType,
     * xs:numeric, ...)
     *
     * @param fingerprint the item type code to be tested
     * @return true if the type is considered primitive under the above rules
     */
    public static boolean isPrimitiveAtomicType(int fingerprint) {
        return fingerprint >= 0 && (fingerprint <= StandardNames.XS_INTEGER ||
                fingerprint == StandardNames.XS_NUMERIC ||
                fingerprint == StandardNames.XS_UNTYPED_ATOMIC ||
                fingerprint == StandardNames.XS_ANY_ATOMIC_TYPE ||
                fingerprint == StandardNames.XS_DAY_TIME_DURATION ||
                fingerprint == StandardNames.XS_YEAR_MONTH_DURATION ||
                fingerprint == StandardNames.XS_ANY_SIMPLE_TYPE);
    }

    /**
     * Determine whether this type is a type that corresponds exactly to a UType
     *
     * @param fingerprint the item type code to be tested
     * @return true if the type is considered primitive under the above rules
     */
    public static boolean isPrimitiveAtomicUType(int fingerprint) {
        return fingerprint >= 0 && fingerprint <= StandardNames.XS_INTEGER;
    }

    /**
     * Determine whether two primitive atomic types are comparable under the rules for ValueComparisons
     * (that is, untyped atomic values treated as strings)
     *
     * @param t1      the first type to compared.
     *                This must be a primitive atomic type as defined by {@link ItemType#getPrimitiveType}
     * @param t2      the second type to compared.
     *                This must be a primitive atomic type as defined by {@link ItemType#getPrimitiveType}
     * @param ordered true if testing for an ordering comparison (lt, gt, le, ge). False
     *                if testing for an equality comparison (eq, ne)
     * @return true if the types are comparable, as defined by the rules of the "eq" operator; false if they
     *         are not comparable, or if we don't yet know (because some subtypes of the static type are comparable
     *         and others are not)
     */

    public static boolean isGuaranteedComparable(/*@NotNull*/ BuiltInAtomicType t1, /*@NotNull*/ BuiltInAtomicType t2, boolean ordered) {
        if (t1 == t2) {
            return true; // short cut
        }
        if (t1.isPrimitiveNumeric()) {
            return t2.isPrimitiveNumeric();
        }
        if (t1.equals(BuiltInAtomicType.UNTYPED_ATOMIC) || t1.equals(BuiltInAtomicType.ANY_URI)) {
            t1 = BuiltInAtomicType.STRING;
        }
        if (t2.equals(BuiltInAtomicType.UNTYPED_ATOMIC) || t2.equals(BuiltInAtomicType.ANY_URI)) {
            t2 = BuiltInAtomicType.STRING;
        }

        if (!ordered) {
            if (t1.equals(BuiltInAtomicType.DAY_TIME_DURATION)) {
                t1 = BuiltInAtomicType.DURATION;
            }
            if (t2.equals(BuiltInAtomicType.DAY_TIME_DURATION)) {
                t2 = BuiltInAtomicType.DURATION;
            }
            if (t1.equals(BuiltInAtomicType.YEAR_MONTH_DURATION)) {
                t1 = BuiltInAtomicType.DURATION;
            }
            if (t2.equals(BuiltInAtomicType.YEAR_MONTH_DURATION)) {
                t2 = BuiltInAtomicType.DURATION;
            }
        }
        return t1 == t2;
    }

    /**
     * Determine whether two primitive atomic types are comparable under the rules for ValueComparisons
     * (that is, untyped atomic values treated as strings)
     *
     * @param t1      the first type to compared.
     *                This must be a primitive atomic type as defined by {@link ItemType#getPrimitiveType}
     * @param t2      the second type to compared.
     *                This must be a primitive atomic type as defined by {@link ItemType#getPrimitiveType}
     * @param ordered true if testing for an ordering comparison (lt, gt, le, ge). False
     *                if testing for an equality comparison (eq, ne)
     * @return true if the types are guaranteed comparable, as defined by the rules of the "eq" operator,
     *         or if we don't yet know (because some subtypes of the static type are comparable
     *         and others are not). False if they are definitely not comparable.
     */

    public static boolean isPossiblyComparable(/*@NotNull*/ BuiltInAtomicType t1, /*@NotNull*/ BuiltInAtomicType t2, boolean ordered) {
        if (t1 == t2) {
            return true; // short cut
        }
        if (t1.equals(BuiltInAtomicType.ANY_ATOMIC) || t2.equals(BuiltInAtomicType.ANY_ATOMIC)) {
            return true; // meaning we don't actually know at this stage
        }
        if (t1.isPrimitiveNumeric()) {
            return t2.isPrimitiveNumeric();
        }
        if (t1.equals(BuiltInAtomicType.UNTYPED_ATOMIC) || t1.equals(BuiltInAtomicType.ANY_URI)) {
            t1 = BuiltInAtomicType.STRING;
        }
        if (t2.equals(BuiltInAtomicType.UNTYPED_ATOMIC) || t2.equals(BuiltInAtomicType.ANY_URI)) {
            t2 = BuiltInAtomicType.STRING;
        }
        if (t1.equals(BuiltInAtomicType.DAY_TIME_DURATION)) {
            t1 = BuiltInAtomicType.DURATION;
        }
        if (t2.equals(BuiltInAtomicType.DAY_TIME_DURATION)) {
            t2 = BuiltInAtomicType.DURATION;
        }
        if (t1.equals(BuiltInAtomicType.YEAR_MONTH_DURATION)) {
            t1 = BuiltInAtomicType.DURATION;
        }
        if (t2.equals(BuiltInAtomicType.YEAR_MONTH_DURATION)) {
            t2 = BuiltInAtomicType.DURATION;
        }
        return t1 == t2;
    }


    /**
     * Determine whether two primitive atomic types are comparable under the rules for GeneralComparisons
     * (that is, untyped atomic values treated as comparable to anything)
     *
     * @param t1      the first type to compared.
     *                This must be a primitive atomic type as defined by {@link ItemType#getPrimitiveType}
     * @param t2      the second type to compared.
     *                This must be a primitive atomic type as defined by {@link ItemType#getPrimitiveType}
     * @param ordered true if testing for an ordering comparison (lt, gt, le, ge). False
     *                if testing for an equality comparison (eq, ne)
     * @return true if the types are comparable, as defined by the rules of the "=" operator
     */

    public static boolean isGenerallyComparable(/*@NotNull*/ BuiltInAtomicType t1, /*@NotNull*/ BuiltInAtomicType t2, boolean ordered) {
        return t1.equals(BuiltInAtomicType.ANY_ATOMIC)
                || t2.equals(BuiltInAtomicType.ANY_ATOMIC)
                || t1.equals(BuiltInAtomicType.UNTYPED_ATOMIC)
                || t2.equals(BuiltInAtomicType.UNTYPED_ATOMIC)
                || isGuaranteedComparable(t1, t2, ordered);
    }

    /**
     * Determine whether two primitive atomic types are guaranteed comparable under the rules for GeneralComparisons
     * (that is, untyped atomic values treated as comparable to anything). This method returns false if a run-time
     * check is necessary.
     *
     * @param t1      the first type to compared.
     *                This must be a primitive atomic type as defined by {@link ItemType#getPrimitiveType}
     * @param t2      the second type to compared.
     *                This must be a primitive atomic type as defined by {@link ItemType#getPrimitiveType}
     * @param ordered true if testing for an ordering comparison (lt, gt, le, ge). False
     *                if testing for an equality comparison (eq, ne)
     * @return true if the types are comparable, as defined by the rules of the "=" operator
     */

    public static boolean isGuaranteedGenerallyComparable(/*@NotNull*/ BuiltInAtomicType t1, /*@NotNull*/ BuiltInAtomicType t2, boolean ordered) {
        return !(t1.equals(BuiltInAtomicType.ANY_ATOMIC) || t2.equals(BuiltInAtomicType.ANY_ATOMIC))
                && isGenerallyComparable(t1, t2, ordered);
    }


}

