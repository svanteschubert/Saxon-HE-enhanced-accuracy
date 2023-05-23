////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;


import net.sf.saxon.om.*;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.tree.iter.UnfailingIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.ObjectValue;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;


/**
 * A UType is a union of primitive (atomic, node, or function) item types. It is represented as a simple
 * integer, with bits representing which of the primitive types are present in the union.
 */

public class UType {

    public static final UType VOID = new UType(0);

    public static final UType DOCUMENT = PrimitiveUType.DOCUMENT.toUType();
    public static final UType ELEMENT = PrimitiveUType.ELEMENT.toUType();
    public static final UType ATTRIBUTE = PrimitiveUType.ATTRIBUTE.toUType();
    public static final UType TEXT = PrimitiveUType.TEXT.toUType();
    public static final UType COMMENT = PrimitiveUType.COMMENT.toUType();
    public static final UType PI = PrimitiveUType.PI.toUType();
    public static final UType NAMESPACE = PrimitiveUType.NAMESPACE.toUType();

    public static final UType FUNCTION = PrimitiveUType.FUNCTION.toUType();

    public static final UType STRING = PrimitiveUType.STRING.toUType();
    public static final UType BOOLEAN = PrimitiveUType.BOOLEAN.toUType();
    public static final UType DECIMAL = PrimitiveUType.DECIMAL.toUType();
    public static final UType FLOAT = PrimitiveUType.FLOAT.toUType();
    public static final UType DOUBLE = PrimitiveUType.DOUBLE.toUType();
    public static final UType DURATION = PrimitiveUType.DURATION.toUType();
    public static final UType DATE_TIME = PrimitiveUType.DATE_TIME.toUType();
    public static final UType TIME = PrimitiveUType.TIME.toUType();
    public static final UType DATE = PrimitiveUType.DATE.toUType();
    public static final UType G_YEAR_MONTH = PrimitiveUType.G_YEAR_MONTH.toUType();
    public static final UType G_YEAR = PrimitiveUType.G_YEAR.toUType();
    public static final UType G_MONTH_DAY = PrimitiveUType.G_MONTH_DAY.toUType();
    public static final UType G_DAY = PrimitiveUType.G_DAY.toUType();
    public static final UType G_MONTH = PrimitiveUType.G_MONTH.toUType();
    public static final UType HEX_BINARY = PrimitiveUType.HEX_BINARY.toUType();
    public static final UType BASE64_BINARY = PrimitiveUType.BASE64_BINARY.toUType();
    public static final UType ANY_URI = PrimitiveUType.ANY_URI.toUType();
    public static final UType QNAME = PrimitiveUType.QNAME.toUType();
    public static final UType NOTATION = PrimitiveUType.NOTATION.toUType();

    public static final UType UNTYPED_ATOMIC = PrimitiveUType.UNTYPED_ATOMIC.toUType();

    public static final UType EXTENSION = PrimitiveUType.EXTENSION.toUType();

    public static final UType NUMERIC = DOUBLE.union(FLOAT).union(DECIMAL);
    public static final UType STRING_LIKE = STRING.union(ANY_URI).union(UNTYPED_ATOMIC);



    public static final UType CHILD_NODE_KINDS = ELEMENT.union(TEXT).union(COMMENT).union(PI);
    public static final UType PARENT_NODE_KINDS = DOCUMENT.union(ELEMENT);
    public static final UType ELEMENT_OR_ATTRIBUTE = ELEMENT.union(ATTRIBUTE);
    public static final UType ANY_NODE = CHILD_NODE_KINDS.union(DOCUMENT).union(ATTRIBUTE).union(NAMESPACE);

    public static final UType ANY_ATOMIC = new UType(0x0FFFFF00);

    public static final UType ANY = ANY_NODE.union(ANY_ATOMIC).union(FUNCTION).union(EXTENSION);


    private int bits;

    public UType(int bits) {
        this.bits = bits;
    }

    /**
     * Returns a hash code value for the object.
     *
     * @return a hash code value for this object.
     * @see Object#equals(Object)
     * @see java.util.Hashtable
     */
    @Override
    public int hashCode() {
        return bits;
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     *
     * @param obj the reference object with which to compare.
     * @return <code>true</code> if this object is the same as the obj
     *         argument; <code>false</code> otherwise.
     * @see #hashCode()
     * @see java.util.Hashtable
     */
    @Override
    public boolean equals(Object obj) {
        return obj instanceof UType && bits == ((UType)obj).bits;
    }

    /**
     * Form a UType as the union of two other UTypes
     * @param other the other UType
     * @return the UType representing the union of this UType and the other UType
     */

    public UType union(UType other) {
        if (other == null) {
            new NullPointerException().printStackTrace();
        }
        return new UType(bits | other.bits);
    }

    public UType intersection(UType other) {
        return new UType(bits & other.bits);
    }

    public UType except(UType other) {
        return new UType(bits &~ other.bits);
    }


    public static UType fromTypeCode(int code) {
        switch (code) {
            case Type.NODE:
                return ANY_NODE;
            case Type.ELEMENT:
                return ELEMENT;
            case Type.ATTRIBUTE:
                return ATTRIBUTE;
            case Type.TEXT:
            case Type.WHITESPACE_TEXT:
                return TEXT;
            case Type.DOCUMENT:
                return DOCUMENT;
            case Type.COMMENT:
                return COMMENT;
            case Type.PROCESSING_INSTRUCTION:
                return PI;
            case Type.NAMESPACE:
                return NAMESPACE;

            case Type.FUNCTION:
                return FUNCTION;

            case Type.ITEM:
                return ANY;
            case StandardNames.XS_ANY_ATOMIC_TYPE:
                return ANY_ATOMIC;

            case StandardNames.XS_NUMERIC:
                return NUMERIC;
            case StandardNames.XS_STRING:
                return STRING;
            case StandardNames.XS_BOOLEAN:
                return BOOLEAN;
            case StandardNames.XS_DURATION:
                return DURATION;
            case StandardNames.XS_DATE_TIME:
                return DATE_TIME;
            case StandardNames.XS_DATE:
                return DATE;
            case StandardNames.XS_TIME:
                return TIME;
            case StandardNames.XS_G_YEAR_MONTH:
                return G_YEAR_MONTH;
            case StandardNames.XS_G_MONTH:
                return G_MONTH;
            case StandardNames.XS_G_MONTH_DAY:
                return G_MONTH_DAY;
            case StandardNames.XS_G_YEAR:
                return G_YEAR;
            case StandardNames.XS_G_DAY:
                return G_DAY;
            case StandardNames.XS_HEX_BINARY:
                return HEX_BINARY;
            case StandardNames.XS_BASE64_BINARY:
                return BASE64_BINARY;
            case StandardNames.XS_ANY_URI:
                return ANY_URI;
            case StandardNames.XS_QNAME:
                return QNAME;
            case StandardNames.XS_NOTATION:
                return NOTATION;
            case StandardNames.XS_UNTYPED_ATOMIC:
                return UNTYPED_ATOMIC;
            case StandardNames.XS_DECIMAL:
                return DECIMAL;
            case StandardNames.XS_FLOAT:
                return FLOAT;
            case StandardNames.XS_DOUBLE:
                return DOUBLE;
            case StandardNames.XS_INTEGER:
                return DECIMAL;
            case StandardNames.XS_NON_POSITIVE_INTEGER:
            case StandardNames.XS_NEGATIVE_INTEGER:
            case StandardNames.XS_LONG:
            case StandardNames.XS_INT:
            case StandardNames.XS_SHORT:
            case StandardNames.XS_BYTE:
            case StandardNames.XS_NON_NEGATIVE_INTEGER:
            case StandardNames.XS_POSITIVE_INTEGER:
            case StandardNames.XS_UNSIGNED_LONG:
            case StandardNames.XS_UNSIGNED_INT:
            case StandardNames.XS_UNSIGNED_SHORT:
            case StandardNames.XS_UNSIGNED_BYTE:
                return DECIMAL;
            case StandardNames.XS_YEAR_MONTH_DURATION:
            case StandardNames.XS_DAY_TIME_DURATION:
                return DURATION;
            case StandardNames.XS_DATE_TIME_STAMP:
                return DATE_TIME;
            case StandardNames.XS_NORMALIZED_STRING:
            case StandardNames.XS_TOKEN:
            case StandardNames.XS_LANGUAGE:
            case StandardNames.XS_NAME:
            case StandardNames.XS_NMTOKEN:
            case StandardNames.XS_NCNAME:
            case StandardNames.XS_ID:
            case StandardNames.XS_IDREF:
            case StandardNames.XS_ENTITY:
                return STRING;
            default:
                throw new IllegalArgumentException(""+code);
        }

    }

    /**
     * Get a set containing all the primitive types in this UType
     * @return a set of PrimitiveUTypes each of which represents exactly one primitive type
     */

    public Set<PrimitiveUType> decompose() {
        Set<PrimitiveUType> result = new HashSet<PrimitiveUType>();
        for (PrimitiveUType p : PrimitiveUType.values()) {
            if ((bits & (1<<p.getBit())) != 0) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * Produce a string representation of a UType
     * @return the string representation
     */

    public String toString() {
        Set<PrimitiveUType> components = decompose();
        if (components.isEmpty()) {
            return "U{}";
        }
        FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C256);
        Iterator<PrimitiveUType> iter = components.iterator();
        boolean started = false;
        while (iter.hasNext()) {
            if (started) {
                sb.append("|");
            }
            started = true;
            sb.append(iter.next().toString());
        }
        return sb.toString();
    }

    public String toStringWithIndefiniteArticle() {
        String s = toString();
        if ("aeiouxy".indexOf(s.charAt(0)) >= 0) {
            return "an " + s + " node";
        } else {
            return "a " + s + " node";
        }
    }



    /**
     * Determine whether two UTypes have overlapping membership
     * @param other the second UType
     * @return true if the intersection between the two UTypes is non-empty
     */

    public boolean overlaps(UType other) {
        return (bits & other.bits) != 0;
    }

    /**
     * Ask whether one UType subsumes another
     * @param other the second UType
     * @return true if every item type allowed by this UType is also allowed by the other item type
     */

    public boolean subsumes (UType other) {
        return (bits & other.bits) == other.bits;
    }

    /**
     * Obtain (that is, create or get) an itemType that matches all items whose primitive type is one
     * of the types present in this UType.
     * @return a corresponding ItemType
     */

    public ItemType toItemType() {
        Set<PrimitiveUType> p = decompose();
        if (p.isEmpty()) {
            return ErrorType.getInstance();
        } else if (p.size() == 1) {
            return p.toArray(new PrimitiveUType[1])[0].toItemType();
        } else if (ANY_NODE.subsumes(this)) {
            return AnyNodeTest.getInstance();
        } else if (equals(NUMERIC)) {
            return NumericType.getInstance();
        } else if (ANY_ATOMIC.subsumes(this)) {
            return BuiltInAtomicType.ANY_ATOMIC;
        } else {
            return AnyItemType.getInstance();
        }
    }

    /**
     * Ask whether a given Item is an instance of this UType
     * @param item the item to be tested
     * @return true if this UType matches the supplied item
     */

    public boolean matches(Item item) {
        return subsumes(getUType(item));
    }

    /**
     * Get the UType of an Item
     * @param item the item whose UType is required
     * @return the UType of the item
     */

    public static UType getUType(Item item)  {
        if (item instanceof NodeInfo) {
            return fromTypeCode(((NodeInfo) item).getNodeKind());
        } else if (item instanceof AtomicValue) {
            return ((AtomicValue)item).getUType();
        } else if (item instanceof Function) {
            return UType.FUNCTION;
        } else if (item instanceof ObjectValue) {
            return UType.EXTENSION;
        } else {
            return UType.VOID;
        }
    }

    /**
     * Get the UType of a Sequence
     * @param sequence the sequence whose UType is required
     * @return the UType of the item
     */

    public static UType getUType(GroundedValue sequence)  {
        UnfailingIterator iter = sequence.iterate();
        Item item;
        UType u = UType.VOID;
        while ((item = iter.next()) != null) {
            u = u.union(getUType(item));
        }
        return u;
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

    public static boolean isPossiblyComparable(UType t1, UType t2, boolean ordered) {
        if (t1 == t2) {
            return true; // short cut
        }
        if (t1 == UType.ANY_ATOMIC || t2 == UType.ANY_ATOMIC) {
            return true; // meaning we don't actually know at this stage
        }
        if (t1 == UType.UNTYPED_ATOMIC || t1 == UType.ANY_URI) {
            t1 = UType.STRING;
        }
        if (t2 == UType.UNTYPED_ATOMIC || t2 == UType.ANY_URI) {
            t2 = UType.STRING;
        }
        if (NUMERIC.subsumes(t1)) {
            t1 = NUMERIC;
        }
        if (NUMERIC.subsumes(t2)) {
            t2 = NUMERIC;
        }
        return t1 == t2;
    }

    /**
     * Determine whether two primitive atomic types are comparable under the rules for ValueComparisons
     * (that is, untyped atomic values treated as strings), using the "eq" operator
     *
     * @param t1      the first type to compared.
     *                This must be a primitive atomic type as defined by {@link ItemType#getPrimitiveType}
     * @param t2      the second type to compared.
     *                This must be a primitive atomic type as defined by {@link ItemType#getPrimitiveType}
     * @return true if the types are comparable, as defined by the rules of the "eq" operator; false if they
     *         are not comparable, or if we don't yet know (because some subtypes of the static type are comparable
     *         and others are not)
     */

    public static boolean isGuaranteedComparable(UType t1, UType t2) {
        if (t1 == t2) {
            return true; // short cut
        }
        if (t1 == UType.UNTYPED_ATOMIC || t1 == UType.ANY_URI) {
            t1 = UType.STRING;
        }
        if (t2 == UType.UNTYPED_ATOMIC || t2 == UType.ANY_URI) {
            t2 = UType.STRING;
        }
        if (NUMERIC.subsumes(t1)) {
            t1 = NUMERIC;
        }
        if (NUMERIC.subsumes(t2)) {
            t2 = NUMERIC;
        }
        return t1.equals(t2);
    }

    /**
     * Determine whether two primitive atomic types are comparable under the rules for GeneralComparisons
     * for the "=" operator (that is, untyped atomic values treated as comparable to anything)
     *
     *
     * @param t1      the first type to compared.
     *                This must be a primitive atomic type as defined by {@link net.sf.saxon.type.ItemType#getPrimitiveType}
     * @param t2      the second type to compared.
     *                This must be a primitive atomic type as defined by {@link net.sf.saxon.type.ItemType#getPrimitiveType}
     * @return true if the types are comparable, as defined by the rules of the "=" operator
     */

    public static boolean isGenerallyComparable(UType t1, UType t2) {
        return  t1 == UType.UNTYPED_ATOMIC ||
                t2 == UType.UNTYPED_ATOMIC ||
                isGuaranteedComparable(t1, t2);
    }




}

