////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.om.Genre;
import net.sf.saxon.om.Item;
import net.sf.saxon.pattern.nodetest.NodeTest;
import net.sf.saxon.type.coercion.CoercionPlan;

import java.util.Optional;


/**
 * ItemType is an interface that allows testing of whether an Item conforms to an
 * expected type. ItemType represents the types in the type hierarchy in the XPath model,
 * as distinct from the schema model: an item type is either item() (matches everything),
 * a node type (matches nodes), an atomic type (matches atomic values), or empty()
 * (matches nothing). Atomic types, represented by the class AtomicType, are also
 * instances of SimpleType in the schema type hierarchy. Node Types, represented by
 * the class NodeTest, are also Patterns as used in XSLT.
 * <p>Saxon assumes that apart from {@link AnyItemType} (which corresponds to <code>item()</code>
 * and matches anything), every ItemType will be either an {@link AtomicType}, a {@link NodeTest},
 * or a {@link FunctionItemType}. User-defined implementations of ItemType must therefore extend one of those
 * three classes/interfaces.</p>
 *
 * @see AtomicType
 * @see NodeTest
 * @see FunctionItemType
 */
public interface ItemType {

    /**
     * Determine the Genre (top-level classification) of this type
     * @return the Genre to which this type belongs, for example node or atomic value
     */

    Genre getGenre();

    /**
     * Determine whether this item type is an atomic type
     *
     * @return true if this is ANY_ATOMIC_TYPE or a subtype thereof
     */

    boolean isAtomicType();

    /**
     * Determine whether this item type is a plain type (that is, whether it can ONLY match
     * atomic values)
     *
     * @return true if this is ANY_ATOMIC_TYPE or a subtype thereof, or a
     *         "plain" union type (that is, unions of atomic types that impose no further restrictions).
     *         Return false if this is a union type whose member types are not all known.
     */

    boolean isPlainType();

    /**
     * Test whether a given item conforms to this type
     *
     * @param item The item to be tested
     * @return true if the item is an instance of this type; false otherwise
     */

    boolean matches(Item item);

    /**
     * Get the primitive item type corresponding to this item type. For item(),
     * this is Type.ITEM. For node(), it is Type.NODE. For specific node kinds,
     * it is the value representing the node kind, for example Type.ELEMENT.
     * For anyAtomicValue and union types it is Type.ATOMIC_VALUE. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that integer, xs:dayTimeDuration, and xs:yearMonthDuration
     * are considered to be primitive types.
     *
     * @return the corresponding primitive type
     */

    /*@NotNull*/
    ItemType getPrimitiveItemType();

    /**
     * Get the primitive type corresponding to this item type. For item(),
     * this is Type.ITEM. For node(), it is Type.NODE. For specific node kinds,
     * it is the value representing the node kind, for example Type.ELEMENT.
     * For anyAtomicValue it is BuiltInAtomicType.ANY_ATOMIC. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that INTEGER is considered to be a primitive type.
     *
     * @return the integer fingerprint of the corresponding primitive type
     */

    int getPrimitiveType();

    /**
     * Get the corresponding {@link UType}. A UType is a union of primitive item
     * types.
     * @return the smallest UType that subsumes this item type
     */

    UType getUType();

    /**
     * Get the default priority when this ItemType is used as an XSLT pattern
     * @return the default priority
     */

    double getDefaultPriority();

    /**
     * Get the default priority normalized into the range 0 to 1
     * @return the default priority plus one divided by two
     */

    default double getNormalizedDefaultPriority() {
        // TODO: no longer conformant to the 4.0 spec
        return (getDefaultPriority() + 1) / 2;
    }

    /**
     * Get the item type of the atomic values that will be produced when an item
     * of this type is atomized
     *
     * @return the best available item type of the atomic values that will be produced when an item
     *         of this type is atomized, or null if it is known that atomization will throw an error.
     */

    PlainType getAtomizedItemType();

    /**
     * Ask whether values of this type are atomizable
     *
     * @return true if some or all instances of this type can be successfully atomized; false
     * if no instances of this type can be atomized
     * @param th the type hierarchy cache
     */

    boolean isAtomizable(TypeHierarchy th);

    /**
     * Get the coercion plan for use when this type is the required type for (say) coercion
     * of arguments in a function call
     * @param version the XPath language version (40 or 31)
     */

    CoercionPlan getCoercionPlan(int version);

    /**
     * Get an alphabetic code representing the type, or at any rate, the nearest built-in type
     * from which this type is derived. The codes are designed so that for any two built-in types
     * A and B, alphaCode(A) is a prefix of alphaCode(B) if and only if A is a supertype of B.
     * @return the alphacode for the nearest containing built-in type. For example: for xs:string
     * return "AS", for xs:boolean "AB", for node() "N", for element() "NE", for map(*) "FM", for
     * array(*) "FA".
     */

    String getBasicAlphaCode();

//    /**
//     * Get the full alpha code for this item type. As well as the basic alpha code, this contains
//     * additional information, for example <code>element(EFG)</code> has a basic alpha code of
//     * <code>NE</code>, but the full alpha code of <code>NE nQ{}EFG</code>.
//     * @return the alpha code for the type
//     */
//
//    default String getFullAlphaCode() {
//        return AlphaCode.fromItemType(this);
//    }

    /**
     * Return a string representation of this ItemType suitable for use in stylesheet
     * export files. This differs from the result of toString() in that it will not contain
     * any references to anonymous types. Note that it may also use the Saxon extended syntax
     * for union types and tuple types. The default implementation returns the result of
     * calling {@code toString()}.
     *
     * @return the string representation as an instance of the XPath SequenceType construct
     */
    default String toExportString() {
        return toString();
    }

    /**
     * Normalize this item type, returning a potentially different item type that matches the same
     * items. For example, {@code record(*)} and {@code map(*)} match the same items. The default
     * implementation returns the item type unchanged. This method does NOT expand item types
     * to an equivalent choice item type.
     * <p>Item types should be normalized before comparison using equals().</p>
     * @return the normalized item type.
     */

    default ItemType normalizeItemType() {
        return this;
    }

    /**
     * Expand this item type to a choice item type. The default delivers a choice
     * with this type as its only member. Implementations for abstract types return
     * a choice of the corresponding concrete types, for example {@code node()} expands
     * to a choice of the seven node kinds. The implementation for a choice type
     * that includes abstract member types should expand these recursively.
     */

    default ChoiceItemType asChoiceItemType() {
        return ChoiceItemType.of(this);
    }

    /**
     * Get extra diagnostic information about why a supplied item does not conform to this
     * item type, if available. If extra information is returned, it should be in the form of a complete
     * sentence, minus the closing full stop. No information should be returned for obvious cases.
     * @param item the item that doesn't match this type
     * @param th the type hierarchy cache
     * @return optionally, a message explaining why the item does not match the type
     */

    Optional<String> explainMismatch(Item item, TypeHierarchy th);

}

