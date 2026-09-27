/// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.ma.jnode.AnyJNodeType;
import net.sf.saxon.om.Genre;
import net.sf.saxon.om.Item;
import net.sf.saxon.pattern.MultipleNodeKindTest;
import net.sf.saxon.type.coercion.ChoiceCoercionPlan;
import net.sf.saxon.type.coercion.CoercionPlan;
import net.sf.saxon.type.gnode.AnyXNodeType;
import net.sf.saxon.type.gnode.NodeKindType;
import net.sf.saxon.type.gnode.XNodeType;

import java.util.*;
import java.util.function.Predicate;

/**
 * A class that represents a union type declared locally, for example using
 * the XPath 4.0 choice item-type syntax (a|b|c), or internally in Java code.
 */

public class ChoiceItemType implements ItemType, ChoiceType {

    private ChoiceItemType expandedChoiceItemType;
    private UType uType;

    public static ItemType makeChoiceItemType(List<ItemType> memberTypes) {
        for (ItemType member : memberTypes) {
            if (member.getGenre() != Genre.ATOMIC) {
                return new ChoiceItemType(memberTypes);
            }
            if (member instanceof PlainType) {
                for (PlainType at : ((PlainType) member).getPlainMemberTypes()) {
                    if (!(at instanceof AtomicType)) {
                        return new ChoiceItemType(memberTypes);
                    }
                }
            }
        }
        return new LocalUnionType(memberTypes);
    }

    public static ChoiceItemType of(ItemType... memberTypes) {
        List<ItemType> members = Arrays.asList(memberTypes);
        return new ChoiceItemType(members);
    }

    public static ChoiceItemType of(UType uType) {
        List<ItemType> members = new ArrayList<>();
        for (PrimitiveUType primitive : uType.decompose()) {
            members.add(UType.toItemType(primitive));
        }
        return new ChoiceItemType(members);
    }

    protected final List<? extends ItemType> memberTypes;


    /**
     * Creates a new ChoiceItemType.
     *
     * @param memberTypes the atomic member types of the union
     */

    public ChoiceItemType(List<? extends ItemType> memberTypes) {
        this.memberTypes = memberTypes;
    }

    /**
     * Get the alternative member types as a list
     * @return the alternatives making up this choice type
     */
    public List<? extends ItemType> getMemberTypes() {
        return memberTypes;
    }

    public Iterable<? extends ItemType> getAlternatives() {
        return getMemberTypes();
    }

    /**
     * Get the genre of this item type
     *
     * @return the genre
     */
    @Override
    public Genre getGenre() {
        UType uType = getUType();
        if (UType.ANY_ATOMIC.subsumes(uType)) {
            return Genre.ATOMIC;
        } else if (UType.XNODE.subsumes(uType)) {
            return Genre.XNODE;
        } else if (UType.FUNCTION.subsumes(uType)) {
            return Genre.FUNCTION;
        } else if (UType.EXTENSION.subsumes(uType)) {
            return Genre.EXTERNAL;
        } else {
            return Genre.ANY;
        }
    }

    public String getDescription() {
        return toString();
    }


    /**
     * Ask whether this Simple Type is an atomic type
     *
     * @return false, this is not an atomic type
     */

    @Override
    public boolean isAtomicType() {
        return false;
    }

    /**
     * Ask whether this Union type is a "plain type", defined as a union
     * type whose member types are all atomic types or plain unions. That is,
     * it disallows unions that are derived by restriction from another union.
     * The significance of this is that an atomic value will never match
     * a non-plain union type
     */

    @Override
    public boolean isPlainType() {
        return false;
    }

    /**
     * Ask whether there is an alternative in the choice type that
     * satisfies a given condition
     * @param condition the given condition
     * @return true if a matching alternative is found
     */
    public boolean someMemberTypeSatisfies(Predicate<? super ItemType> condition) {
        for (ItemType member : memberTypes) {
            if (condition.test(member)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return an item type that eliminates one (or more) of the alternatives
     * @param condition predicate determining which alternatives to drop
     * @return an ItemType that eliminates the selected choices
     */

    public ItemType eliminating(Predicate<? super ItemType> condition) {
        List<ItemType> newList = new ArrayList<>(memberTypes.size());
        for (ItemType member : memberTypes) {
            if (!condition.test(member)) {
                newList.add(member);
            }
        }
        if (newList.size() == 1) {
            return newList.get(0);
        } else {
            return new ChoiceItemType(newList);
        }
    }

    /**
     * Determine whether this is a built-in type or a user-defined type
     */

    public boolean isBuiltInType() {
        return false;
    }

    /**
     * Determine whether this is a list type
     */

    public boolean isListType() {
        return false;
    }

    /**
     * Return true if this type is a union type (that is, if its variety is union)
     *
     * @return true for a union type
     */

    public boolean isUnionType() {
        return true;
    }

    /**
     * Get the corresponding {@link UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public synchronized UType getUType() {
        if (uType == null) {
            UType u = UType.VOID;
            for (ItemType at : memberTypes) {
                u = u.union(at.getUType());
            }
            uType = u;
        }
       return uType;
    }

    /**
     * Get an alphabetic code representing the type, or at any rate, the nearest built-in type
     * from which this type is derived. The codes are designed so that for any two built-in types
     * A and B, alphaCode(A) is a prefix of alphaCode(B) if and only if A is a supertype of B.
     *
     * @return the alphacode for the nearest containing built-in type
     */
    @Override
    public String getBasicAlphaCode() {
        return "U";
    }

//    public String getFullAlphaCode() {
//        // TODO: integrate this code into AlphaCode.fromItemType()
//        if (memberTypes.size() == 1) {
//            return memberTypes.get(0).getFullAlphaCode();
//        }
//        StringBuilder sb = new StringBuilder("U m[");
//        boolean first = true;
//        for (ItemType member : asChoiceItemType().getMemberTypes()) {
//            if (first) {
//                first = false;
//            } else {
//                sb.append(",");
//            }
//            sb.append(member.getFullAlphaCode());
//        }
//        sb.append("]");
//        return sb.toString();
//
//    }

    /**
     * Test whether a given item conforms to this type
     *
     * @param item The item to be tested
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item) {
        for (ItemType member : memberTypes) {
            if (member.matches(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Method defined in ItemType: get a primitive supertype in the ItemType type hierarchy
     *
     * @return BuiltInAtomicType.ANY_ATOMIC
     */

    /*@NotNull*/
    @Override
    public ItemType getPrimitiveItemType() {
        Set<ItemType> primitives = new HashSet<>();
        ItemType lastPrimitive = null;
        boolean allNumeric = true;
        boolean allXNodes = true;
        boolean allAtomic = true;
        for (ItemType member : memberTypes) {
            ItemType prim = member.getPrimitiveItemType();
            primitives.add(prim);
            lastPrimitive = prim;
            if (!(prim instanceof AtomicType)) {
                allAtomic = false;
            }
            if (!(prim instanceof NumericType)) {
                allNumeric = false;
            }
            if (!(prim instanceof XNodeType)) {
                allXNodes = false;
            }
        }
        if (primitives.size() == 1) {
            return lastPrimitive;
        }
        if (allNumeric) {
            return NumericType.getInstance();
        }
        if (allAtomic) {
            return BuiltInAtomicType.ANY_ATOMIC;
        }
        if (allXNodes) {
            return AnyXNodeType.getInstance();
        }
        return AnyItemType.INSTANCE;
    }

    /**
     * Convert, if possible, to a multiple node kind test
     * @return the equivalent {@link MultipleNodeKindTest} or null if there is no equivalent
     */
    public MultipleNodeKindTest toMultipleNodeKindTest() {
        UType combined = UType.VOID;
        for (ItemType member : memberTypes) {
            if (member instanceof NodeKindType kind) {
                combined = combined.union(kind.getUType());
            } else if (member instanceof AnyJNodeType) {
                combined = combined.union(UType.JNODE);
            } else {
                return null;
            }
        }
        return new MultipleNodeKindTest(combined);
    }

    /**
     * Method defined in ItemType: get a primitive supertype in the ItemType type hierarchy
     *
     * @return StandardNames.XS_ANY_ATOMIC_TYPE
     */

    @Override
    public int getPrimitiveType() {
        return getPrimitiveItemType().getPrimitiveType();
    }

    /*@NotNull*/
    @Override
    public PlainType getAtomizedItemType() {
        return BuiltInAtomicType.ANY_ATOMIC;
    }

    @Override
    public boolean isAtomizable(TypeHierarchy th) {
        for (ItemType member : memberTypes) {
            if (member.isAtomizable(th)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the default priority when this ItemType is used as an XSLT pattern
     *
     * @return the default priority. For a choice type this is defined as the maximum
     * of the default priorities of the member types.
     */
    @Override
    public double getDefaultPriority() {
        double result = Double.NEGATIVE_INFINITY;
        for (ItemType t : memberTypes) {
            result = Double.max(result, t.getDefaultPriority());
        }
        return result;
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ChoiceItemType other
                && other.getMemberTypes().size() == memberTypes.size()
                && other.getUType().equals(getUType())
                && other.hashCode() == hashCode()) {
            for (ItemType member : memberTypes) {
                boolean foundMatch = false;
                for (ItemType otherMember : other.memberTypes) {
                    if (member.equals(otherMember)) {
                        foundMatch = true;
                        break;
                    }
                }
                if (!foundMatch) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int h = 0x0123abcd;
        for (ItemType member : memberTypes) {
            h ^= member.hashCode();
        }
        return h;
    }

    @Override
    public ChoiceItemType asChoiceItemType() {
        return this;
    }

    public synchronized ChoiceItemType expand() {
        if (expandedChoiceItemType == null) {
            boolean expanded = false;
            List<ItemType> members = new ArrayList<>(memberTypes.size());
            for (ItemType member : memberTypes) {
                ChoiceItemType asChoice = member.asChoiceItemType();
                if (asChoice.memberTypes.size() > 1) {
                    for (ItemType subMember : asChoice.memberTypes) {
                        members.add(subMember);
                        expanded = true;
                    }
                } else {
                    members.add(member);
                }
            }
            if (expanded) {
                expandedChoiceItemType = new ChoiceItemType(members);
            } else {
                expandedChoiceItemType = this;
            }
        }
        return expandedChoiceItemType;
    }

    /**
     * Produce a string representation of the type name. If the type is anonymous, an internally-allocated
     * type name will be returned.
     *
     * @return the name of the atomic type in the form Q{uri}local
     */

    public String toString() {
        StringBuilder builder = new StringBuilder("(");
        for (ItemType at : memberTypes) {
            builder.append(at.toString());
            builder.append("|");
        }
        builder.setLength(builder.length() - 1);
        builder.append(")");
        return builder.toString();
    }

    @Override
    public String toExportString() {
        return toString();
    }

    /**
     * Get the coercion plan for use when this type is the required type for (say) coercion
     * of arguments in a function call
     *
     * @param version the XPath language version (40 or 31)
     */
    @Override
    public CoercionPlan getCoercionPlan(int version) {
        return ChoiceCoercionPlan.INSTANCE40;
    }

    @Override
    public Optional<String> explainMismatch(Item item, TypeHierarchy th) {
        return Optional.empty();
    }

    public final static ChoiceItemType CHOICE_OF_ANY = ChoiceItemType.of(
            AnyJNodeType.getInstance(),
            NodeKindType.ELEMENT,
            NodeKindType.ATTRIBUTE,
            NodeKindType.NAMESPACE,
            NodeKindType.DOCUMENT,
            NodeKindType.TEXT,
            NodeKindType.COMMENT,
            NodeKindType.PROCESSING_INSTRUCTION,
            AnyFunctionType.INSTANCE,
            BuiltInAtomicType.STRING,
            BuiltInAtomicType.ANY_URI,
            BuiltInAtomicType.DECIMAL,
            BuiltInAtomicType.DURATION,
            BuiltInAtomicType.G_YEAR,
            BuiltInAtomicType.G_YEAR_MONTH,
            BuiltInAtomicType.G_MONTH,
            BuiltInAtomicType.G_MONTH_DAY,
            BuiltInAtomicType.G_DAY,
            BuiltInAtomicType.TIME,
            BuiltInAtomicType.DATE,
            BuiltInAtomicType.DATE_TIME,
            BuiltInAtomicType.DATE_TIME_STAMP,
            BuiltInAtomicType.BOOLEAN,
            BuiltInAtomicType.DOUBLE,
            BuiltInAtomicType.FLOAT,
            BuiltInAtomicType.HEX_BINARY,
            BuiltInAtomicType.BASE64_BINARY,
            BuiltInAtomicType.QNAME,
            BuiltInAtomicType.NOTATION,
            BuiltInAtomicType.UNTYPED_ATOMIC
    );

    public final static ChoiceItemType CHOICE_OF_GNODE = ChoiceItemType.of(
            AnyJNodeType.getInstance(),
            NodeKindType.ELEMENT,
            NodeKindType.ATTRIBUTE,
            NodeKindType.NAMESPACE,
            NodeKindType.DOCUMENT,
            NodeKindType.TEXT,
            NodeKindType.COMMENT,
            NodeKindType.PROCESSING_INSTRUCTION
    );

    public final static ChoiceItemType CHOICE_OF_XNODE = ChoiceItemType.of(
            NodeKindType.ELEMENT,
            NodeKindType.ATTRIBUTE,
            NodeKindType.NAMESPACE,
            NodeKindType.DOCUMENT,
            NodeKindType.TEXT,
            NodeKindType.COMMENT,
            NodeKindType.PROCESSING_INSTRUCTION
    );

    public final static ChoiceItemType CHOICE_OF_ATOMIC = ChoiceItemType.of(
            BuiltInAtomicType.STRING,
            BuiltInAtomicType.ANY_URI,
            BuiltInAtomicType.DECIMAL,
            BuiltInAtomicType.DURATION,
            BuiltInAtomicType.G_YEAR,
            BuiltInAtomicType.G_YEAR_MONTH,
            BuiltInAtomicType.G_MONTH,
            BuiltInAtomicType.G_MONTH_DAY,
            BuiltInAtomicType.G_DAY,
            BuiltInAtomicType.TIME,
            BuiltInAtomicType.DATE,
            BuiltInAtomicType.DATE_TIME,
            BuiltInAtomicType.DATE_TIME_STAMP,
            BuiltInAtomicType.BOOLEAN,
            BuiltInAtomicType.DOUBLE,
            BuiltInAtomicType.FLOAT,
            BuiltInAtomicType.HEX_BINARY,
            BuiltInAtomicType.BASE64_BINARY,
            BuiltInAtomicType.QNAME,
            BuiltInAtomicType.NOTATION,
            BuiltInAtomicType.UNTYPED_ATOMIC
    );

}

// Copyright (c) 2004-2026 Saxonica Limited
