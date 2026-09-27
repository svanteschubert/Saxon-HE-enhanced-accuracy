////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;


import net.sf.saxon.ma.jnode.AnyJNodeType;
import net.sf.saxon.ma.jnode.JNode;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.Err;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.transpile.CSharpSuppressWarnings;
import net.sf.saxon.type.gnode.AnyGNodeType;
import net.sf.saxon.type.gnode.AnyXNodeType;
import net.sf.saxon.type.gnode.NodeKindType;
import net.sf.saxon.value.AtomicValue;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;


/**
 * A UType is a union of primitive (atomic, node, or function) item types. It is represented as a simple
 * integer, with bits representing which of the primitive types are present in the union.
 */

public class UType {

    public static final UType VOID = new UType(0);

    public static final UType DOCUMENT = of(PrimitiveUType.DOCUMENT);
    public static final UType ELEMENT = of(PrimitiveUType.ELEMENT);
    public static final UType ATTRIBUTE = of(PrimitiveUType.ATTRIBUTE);
    public static final UType TEXT = of(PrimitiveUType.TEXT);
    public static final UType COMMENT = of(PrimitiveUType.COMMENT);
    public static final UType PI = of(PrimitiveUType.PI);
    public static final UType NAMESPACE = of(PrimitiveUType.NAMESPACE);

    public static final UType JNODE = of(PrimitiveUType.JNODE);

    public static final UType FUNCTION = of(PrimitiveUType.FUNCTION);

    public static final UType STRING = of(PrimitiveUType.STRING);
    public static final UType BOOLEAN = of(PrimitiveUType.BOOLEAN);
    public static final UType DECIMAL = of(PrimitiveUType.DECIMAL);
    public static final UType FLOAT = of(PrimitiveUType.FLOAT);
    public static final UType DOUBLE = of(PrimitiveUType.DOUBLE);
    public static final UType DURATION = of(PrimitiveUType.DURATION);
    public static final UType DATE_TIME = of(PrimitiveUType.DATE_TIME);
    public static final UType TIME = of(PrimitiveUType.TIME);
    public static final UType DATE = of(PrimitiveUType.DATE);
    public static final UType G_YEAR_MONTH = of(PrimitiveUType.G_YEAR_MONTH);
    public static final UType G_YEAR = of(PrimitiveUType.G_YEAR);
    public static final UType G_MONTH_DAY = of(PrimitiveUType.G_MONTH_DAY);
    public static final UType G_DAY = of(PrimitiveUType.G_DAY);
    public static final UType G_MONTH = of(PrimitiveUType.G_MONTH);
    public static final UType HEX_BINARY = of(PrimitiveUType.HEX_BINARY);
    public static final UType BASE64_BINARY = of(PrimitiveUType.BASE64_BINARY);
    public static final UType ANY_URI = of(PrimitiveUType.ANY_URI);
    public static final UType QNAME = of(PrimitiveUType.QNAME);
    public static final UType NOTATION = of(PrimitiveUType.NOTATION);

    public static final UType UNTYPED_ATOMIC = of(PrimitiveUType.UNTYPED_ATOMIC);

    public static final UType EXTENSION = of(PrimitiveUType.EXTENSION);

    public static final UType NUMERIC = DOUBLE.union(FLOAT).union(DECIMAL);
    public static final UType STRING_LIKE = STRING.union(ANY_URI).union(UNTYPED_ATOMIC);
    public static final UType BINARY = HEX_BINARY.union(BASE64_BINARY);


    public static final UType CHILD_NODE_KINDS = ELEMENT.union(TEXT).union(COMMENT).union(PI);
    public static final UType PARENT_NODE_KINDS = DOCUMENT.union(ELEMENT);
    public static final UType ELEMENT_OR_ATTRIBUTE = ELEMENT.union(ATTRIBUTE);
    public static final UType XNODE = CHILD_NODE_KINDS.union(DOCUMENT).union(ATTRIBUTE).union(NAMESPACE);
    public static final UType GNODE = XNODE.union(JNODE);

    public static final UType ANY_ATOMIC = UType.STRING
            .union(UType.BOOLEAN)
            .union(UType.DECIMAL)
            .union(UType.FLOAT)
            .union(UType.DOUBLE)
            .union(UType.DURATION)
            .union(UType.DATE_TIME)
            .union(UType.TIME)
            .union(UType.DATE)
            .union(UType.G_YEAR_MONTH)
            .union(UType.G_YEAR)
            .union(UType.G_MONTH)
            .union(UType.G_MONTH_DAY)
            .union(UType.G_DAY)
            .union(UType.HEX_BINARY)
            .union(UType.BASE64_BINARY)
            .union(UType.ANY_URI)
            .union(UType.QNAME)
            .union(UType.NOTATION)
            .union(UType.UNTYPED_ATOMIC);

    @CSharpReplaceBody(code="return new UType(1 << (int)prim);")
    private static UType of(PrimitiveUType prim) {
         return new UType(1 << prim.getBit());
    }

    public static final UType ANY = XNODE.union(JNODE).union(ANY_ATOMIC).union(FUNCTION).union(EXTENSION);

    // XNodes that cannot have children
    public static final UType LEAF = UType.TEXT
            .union(UType.COMMENT)
            .union(UType.PI)
            .union(UType.NAMESPACE)
            .union(UType.ATTRIBUTE);

    
    private final int bits;
    
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
        return switch (code) {
            case Type.XNODE -> XNODE;
            case Type.ELEMENT -> ELEMENT;
            case Type.ATTRIBUTE -> ATTRIBUTE;
            case Type.TEXT, Type.WHITESPACE_TEXT -> TEXT;
            case Type.DOCUMENT -> DOCUMENT;
            case Type.COMMENT -> COMMENT;
            case Type.PROCESSING_INSTRUCTION -> PI;
            case Type.NAMESPACE -> NAMESPACE;
            case Type.GNODE -> GNODE;
            case Type.JNODE -> JNODE;
            case Type.FUNCTION -> FUNCTION;
            case Type.ITEM -> ANY;
            case StandardNames.XS_ANY_ATOMIC_TYPE -> ANY_ATOMIC;
            case StandardNames.XS_NUMERIC -> NUMERIC;
            case StandardNames.XS_STRING -> STRING;
            case StandardNames.XS_BOOLEAN -> BOOLEAN;
            case StandardNames.XS_DURATION -> DURATION;
            case StandardNames.XS_DATE_TIME -> DATE_TIME;
            case StandardNames.XS_DATE -> DATE;
            case StandardNames.XS_TIME -> TIME;
            case StandardNames.XS_G_YEAR_MONTH -> G_YEAR_MONTH;
            case StandardNames.XS_G_MONTH -> G_MONTH;
            case StandardNames.XS_G_MONTH_DAY -> G_MONTH_DAY;
            case StandardNames.XS_G_YEAR -> G_YEAR;
            case StandardNames.XS_G_DAY -> G_DAY;
            case StandardNames.XS_HEX_BINARY -> HEX_BINARY;
            case StandardNames.XS_BASE64_BINARY -> BASE64_BINARY;
            case StandardNames.XS_ANY_URI -> ANY_URI;
            case StandardNames.XS_QNAME -> QNAME;
            case StandardNames.XS_NOTATION -> NOTATION;
            case StandardNames.XS_UNTYPED_ATOMIC -> UNTYPED_ATOMIC;
            case StandardNames.XS_DECIMAL -> DECIMAL;
            case StandardNames.XS_FLOAT -> FLOAT;
            case StandardNames.XS_DOUBLE -> DOUBLE;
            case StandardNames.XS_INTEGER -> DECIMAL;
            case StandardNames.XS_NON_POSITIVE_INTEGER,
                    StandardNames.XS_NEGATIVE_INTEGER,
                    StandardNames.XS_LONG,
                    StandardNames.XS_INT,
                    StandardNames.XS_SHORT,
                    StandardNames.XS_BYTE,
                    StandardNames.XS_NON_NEGATIVE_INTEGER,
                    StandardNames.XS_POSITIVE_INTEGER,
                    StandardNames.XS_UNSIGNED_LONG,
                    StandardNames.XS_UNSIGNED_INT,
                    StandardNames.XS_UNSIGNED_SHORT,
                    StandardNames.XS_UNSIGNED_BYTE ->
                    DECIMAL;
            case StandardNames.XS_YEAR_MONTH_DURATION,
                    StandardNames.XS_DAY_TIME_DURATION -> DURATION;
            case StandardNames.XS_DATE_TIME_STAMP -> DATE_TIME;
            case StandardNames.XS_NORMALIZED_STRING,
                    StandardNames.XS_TOKEN,
                    StandardNames.XS_LANGUAGE,
                    StandardNames.XS_NAME,
                    StandardNames.XS_NMTOKEN,
                    StandardNames.XS_NCNAME,
                    StandardNames.XS_ID,
                    StandardNames.XS_IDREF,
                    StandardNames.XS_ENTITY ->
                    STRING;
            default -> throw new IllegalArgumentException("" + code);
        };

    }

    public static ItemType toItemType(PrimitiveUType prim) {
        return switch (prim) {
            case DOCUMENT -> NodeKindType.DOCUMENT;
            case ELEMENT -> NodeKindType.ELEMENT;
            case ATTRIBUTE -> NodeKindType.ATTRIBUTE;
            case TEXT -> NodeKindType.TEXT;
            case COMMENT -> NodeKindType.COMMENT;
            case PI -> NodeKindType.PROCESSING_INSTRUCTION;
            case NAMESPACE -> NodeKindType.NAMESPACE;
            case JNODE -> AnyJNodeType.getInstance();
            case FUNCTION -> AnyFunctionType.INSTANCE;
            case STRING -> BuiltInAtomicType.STRING;
            case BOOLEAN -> BuiltInAtomicType.BOOLEAN;
            case DECIMAL -> BuiltInAtomicType.DECIMAL;
            case FLOAT -> BuiltInAtomicType.FLOAT;
            case DOUBLE -> BuiltInAtomicType.DOUBLE;
            case DURATION -> BuiltInAtomicType.DURATION;
            case DATE_TIME -> BuiltInAtomicType.DATE_TIME;
            case TIME -> BuiltInAtomicType.TIME;
            case DATE -> BuiltInAtomicType.DATE;
            case G_YEAR_MONTH -> BuiltInAtomicType.G_YEAR_MONTH;
            case G_YEAR -> BuiltInAtomicType.G_YEAR;
            case G_MONTH_DAY -> BuiltInAtomicType.G_MONTH_DAY;
            case G_DAY -> BuiltInAtomicType.G_DAY;
            case G_MONTH -> BuiltInAtomicType.G_MONTH;
            case HEX_BINARY -> BuiltInAtomicType.HEX_BINARY;
            case BASE64_BINARY -> BuiltInAtomicType.BASE64_BINARY;
            case ANY_URI -> BuiltInAtomicType.ANY_URI;
            case QNAME -> BuiltInAtomicType.QNAME;
            case NOTATION -> BuiltInAtomicType.NOTATION;
            case UNTYPED_ATOMIC -> BuiltInAtomicType.UNTYPED_ATOMIC;
            case EXTENSION ->
                //return JavaExternalObjectType.EXTERNAL_OBJECT_TYPE;
                    AnyItemType.INSTANCE;
            default -> AnyItemType.INSTANCE;
        };
    }

    /**
     * Get a set containing all the primitive types in this UType
     * @return a set of PrimitiveUTypes each of which represents exactly one primitive type
     */

    @CSharpReplaceBody(code="System.Collections.Generic.ISet<Saxon.Hej.type.PrimitiveUType> result = new System.Collections.Generic.HashSet<Saxon.Hej.type.PrimitiveUType>();\n"
            + "            foreach (Saxon.Hej.type.PrimitiveUType p in Enum.GetValues<Saxon.Hej.type.PrimitiveUType>()) {\n"
            + "                if (((bits&(1<<((int)p)))) != 0) {\n"
            + "                    result.Add(p);\n"
            + "                }\n"
            + "            }\n"
            + "            return result;")

    public Set<PrimitiveUType> decompose() {
        Set<PrimitiveUType> result = new HashSet<>();
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

    @CSharpSuppressWarnings("UnsafeIteratorConversion")
    public String toString() {
        Set<PrimitiveUType> components = decompose();
        if (components.isEmpty()) {
            return "U{}";
        }
        StringBuilder sb = new StringBuilder(256);
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
        return Err.indefiniteArticleFor(toString(), false) + " " + this + " node";
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

    @CSharpSuppressWarnings("UnsafeIteratorConversion")
    public ItemType toItemType() {
        Set<PrimitiveUType> p = decompose();
        if (p.isEmpty()) {
            return ErrorType.getInstance();
        } else if (p.size() == 1) {
            Iterator<PrimitiveUType> iter = p.iterator();
            return iter.hasNext() ? toItemType(iter.next()) : null;
        } else if (XNODE.equals(this)) {
            return AnyXNodeType.getInstance();
        } else if (GNODE.equals(this)) {
            return AnyGNodeType.getInstance();
        } else if (equals(NUMERIC)) {
            return NumericType.getInstance();
        } else {
            return ChoiceItemType.of(this);
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
        } else if (item instanceof FunctionItem) {
            return UType.FUNCTION;
        } else if (item instanceof JNode) {
            return UType.JNODE;
        } else if (item.getGenre() == Genre.EXTERNAL) {
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
        SequenceIterator iter = sequence.iterate();
        UType u = UType.VOID;
        for (Item item; (item = iter.next()) != null; ) {
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

