////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.*;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.Err;
import net.sf.saxon.type.coercion.CoercionPlan;
import net.sf.saxon.type.coercion.UnionCoercionPlan;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A class that represents a union type declared locally, for example using
 * the XPath 4.0 choice item-type syntax (a|b|c), or internally in Java code.
 */

public class LocalUnionType extends ChoiceItemType implements PlainType, UnionType {

    /**
     * Get the genre of this item
     *
     * @return the genre
     */
    @Override
    public Genre getGenre() {
        return Genre.ATOMIC;
    }

    @Override
    public StructuredQName getTypeName() {
        return new StructuredQName("", NamespaceUri.ANONYMOUS, "U" + hashCode());
    }

    /**
     * Creates a new Union type.
     *
     * @param memberTypes the atomic member types of the union
     */

    public LocalUnionType(List<ItemType> memberTypes) {
        super(memberTypes);
    }

    public static LocalUnionType of(AtomicType... memberTypes) {
        List<ItemType> members = new ArrayList<>(memberTypes.length);
        Collections.addAll(members, memberTypes);
        return new LocalUnionType(members);
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
     * Ask whether this union type includes any list types among its members
     */

    @Override
    public boolean containsListType() {
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
        return true;
    }

    /**
     * Get the SequenceType that most accurately describes the result of casting a value to this union type
     *
     * @return the most precise SequenceType for the result of casting
     */

    @Override
    public SequenceType getResultTypeOfCast() {
        return SequenceType.optional(this);
    }


    /**
     * Ask whether this type is an ID type. This is defined to be any simple type
     * who typed value may contain atomic values of type xs:ID: that is, it includes types derived
     * from ID by restriction, list, or union. Note that for a node to be treated
     * as an ID in XSD 1.0, its typed value must be a *single* atomic value of type ID; the type of the
     * node, however, can still allow a list or union. This changes in XSD 1.1, where a list of IDs is allowed.
     */

    public boolean isIdType() {
        return someMemberTypeSatisfies(t -> t instanceof AtomicType && ((AtomicType)t).isIdType());
    }

    /**
     * Ask whether this type is an IDREF or IDREFS type. This is defined to be any simple type
     * who typed value may contain atomic values of type xs:IDREF: that is, it includes types derived
     * from IDREF or IDREFS by restriction, list, or union
     */

    public boolean isIdRefType() {
        return someMemberTypeSatisfies(t -> t instanceof AtomicType && ((AtomicType) t).isIdRefType());
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
     * Test whether this type is namespace sensitive, that is, if a namespace context is needed
     * to translate between the lexical space and the value space. This is true for types derived
     * from, or containing, QNames and NOTATIONs
     *
     * @return true if any of the member types is namespace-sensitive, or if namespace sensitivity
     * cannot be determined because there are components missing from the schema.
     */

    @Override
    public boolean isNamespaceSensitive() {
        return someMemberTypeSatisfies(t -> t instanceof AtomicType && ((AtomicType)t).isNamespaceSensitive());
    }

    /**
     * Check whether a given input string is valid according to this SimpleType
     *
     * @param value      the input string to be checked
     * @param nsResolver a namespace resolver used to resolve namespace prefixes if the type
     *                   is namespace sensitive. The value supplied may be null; in this case any namespace-sensitive
     *                   content will throw an UnsupportedOperationException.
     * @param rules      the configuration-wide conversion rules
     * @return null if validation succeeds; return a ValidationFailure describing the validation failure
     *         if validation fails
     * @throws UnsupportedOperationException if the type is namespace-sensitive and no namespace
     *                                       resolver is supplied
     */

    /*@Nullable*/
    public ValidationFailure validateContent(UnicodeString value, NamespaceResolver nsResolver, /*@NotNull*/ ConversionRules rules) {
        for (ItemType at : memberTypes) {
            ValidationFailure err = ((AtomicType)at).validateContent(value, nsResolver, rules);
            if (err == null) {
                return null;
            }
        }
        return new ValidationFailure(
                    "Value " + Err.wrap(value, Err.VALUE) +
                            " does not match any member of union type " + toString());

    }

    /**
     * Validate an atomic value, which is known to be an instance of one of the member types of the
     * union, against any facets (pattern facets or enumeration facets) defined at the level of the
     * union itself.
     *
     * @param value the Atomic Value to be checked. This must be an instance of a member type of the
     *              union
     * @param rules the ConversionRules for the Configuration
     * @return a ValidationFailure if the value is not valid; null if it is valid.
     */
    @Override
    public ValidationFailure checkAgainstFacets(AtomicValue value, ConversionRules rules) {
        return null;
    }

    @Override
    public AtomicValue getTypedValue(UnicodeString value, NamespaceResolver resolver, ConversionRules rules)
            throws ValidationException {
        ValidationException failure = null;
        for (ItemType type : memberTypes) {
            if (type instanceof EnumerationUnionType) {
                try {
                    return ((EnumerationUnionType) type).getTypedValue(value, resolver, rules);
                } catch (ValidationException e) {
                    failure = e;
                }
            } else {
                StringConverter converter = rules.makeStringConverter((AtomicType) type);
                converter.setNamespaceResolver(resolver);
                ConversionResult outcome = converter.convertString(value);
                if (outcome instanceof AtomicValue) {
                    return (AtomicValue) outcome;
                }
            }
        }
        if (failure == null) {
            ValidationFailure ve = new ValidationFailure(
                    "Value " + Err.wrap(value, Err.VALUE) +
                            " does not match any member of union type " + toString());
            failure = ve.makeException();
        }
        throw failure;
    }

    /**
     * Test whether a given item conforms to this type
     *
     * @param item The item to be tested
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item) {
        if (item instanceof AtomicValue) {
            return someMemberTypeSatisfies(at -> at.matches(item));
        } else {
            return false;
        }
    }

    /**
     * Method defined in ItemType: get a primitive supertype in the ItemType type hierarchy
     *
     * @return BuiltInAtomicType.ANY_ATOMIC
     */

    /*@NotNull*/
    @Override
    public AtomicType getPrimitiveItemType() {
        return BuiltInAtomicType.ANY_ATOMIC;
    }

    /**
     * Method defined in ItemType: get a primitive supertype in the ItemType type hierarchy
     *
     * @return StandardNames.XS_ANY_ATOMIC_TYPE
     */

    @Override
    public int getPrimitiveType() {
        return StandardNames.XS_ANY_ATOMIC_TYPE;
    }

    /*@NotNull*/
    @Override
    public PlainType getAtomizedItemType() {
        return this;
    }

    @Override
    public boolean isAtomizable(TypeHierarchy th) {
        return true;
    }

    /**
     * Get the list of plain types that are subsumed by this type
     *
     * @return for an atomic type, the type itself; for a plain union type, the list of plain types
     *         in its transitive membership, in declaration order
     */
    @Override
    public List<? extends PlainType> getPlainMemberTypes()  {
        List<PlainType> members = new ArrayList<>(memberTypes.size());
        for (ItemType it : memberTypes) {
            members.add((PlainType)it);
        }
        return members;
    }

    /**
     * Get the coercion plan for use when this type is the required type for (say) coercion
     * of arguments in a function call
     *
     * @param version the XPath language version (40 or 31)
     */
    @Override
    public CoercionPlan getCoercionPlan(int version) {
        return version >= 40 ? UnionCoercionPlan.INSTANCE40 : UnionCoercionPlan.INSTANCE31;
    }

}

// Copyright (c) 2004-2026 Saxonica Limited
