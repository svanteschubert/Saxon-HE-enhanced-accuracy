////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.om.Genre;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;

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
 * and matches anything), every ItemType will be either an {@link AtomicType}, a {@link net.sf.saxon.pattern.NodeTest},
 * or a {@link FunctionItemType}. User-defined implementations of ItemType must therefore extend one of those
 * three classes/interfaces.</p>
 *
 * @see AtomicType
 * @see net.sf.saxon.pattern.NodeTest
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
     * Ask whether this {@code ItemType} actually represents an item type in the XDM sense
     * of the term. The only instances that aren't true item types are user-defined union types
     * derived by restriction from other union types or containing list types in their transitive
     * membership ("impure union types")
     * @return true if this is a true item type in the XDM sense
     */

    default boolean isTrueItemType() {
        return true;
    }

    /**
     * Test whether a given item conforms to this type
     *
     *
     * @param item    The item to be tested
     * @param th      The type hierarchy cache. Currently used only when matching function items.
     * @return true if the item is an instance of this type; false otherwise
     */

    boolean matches(Item item, TypeHierarchy th) throws XPathException;

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
     * Get an alphabetic code representing the type, or at any rate, the nearest built-in type
     * from which this type is derived. The codes are designed so that for any two built-in types
     * A and B, alphaCode(A) is a prefix of alphaCode(B) if and only if A is a supertype of B.
     * @return the alphacode for the nearest containing built-in type. For example: for xs:string
     * return "AS", for xs:boolean "AB", for node() "N", for element() "NE", for map(*) "FM", for
     * array(*) "FA".
     */

    String getBasicAlphaCode();

    /**
     * Get the full alpha code for this item type. As well as the basic alpha code, this contains
     * additional information, for example <code>element(EFG)</code> has a basic alpha code of
     * <code>NE</code>, but the full alpha code of <code>NE nQ{}EFG</code>.
     */

    default String getFullAlphaCode() {
        return getBasicAlphaCode();
    }

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
     * Extension of the ItemType interface implemented by some item types, to provide
     * a cache of SequenceType objects based on this item type, with different
     * occurrence indicators.
     */

    interface WithSequenceTypeCache extends ItemType {
        /**
         * Get a sequence type representing exactly one instance of this atomic type
         *
         * @return a sequence type representing exactly one instance of this atomic type
         * @since 9.8.0.2
         */

        SequenceType one();
        /**
         * Get a sequence type representing zero or one instances of this atomic type
         *
         * @return a sequence type representing zero or one instances of this atomic type
         * @since 9.8.0.2
         */

        SequenceType zeroOrOne();

        /**
         * Get a sequence type representing one or more instances of this atomic type
         *
         * @return a sequence type representing one or more instances of this atomic type
         * @since 9.8.0.2
         */

        SequenceType oneOrMore();

        /**
         * Get a sequence type representing one or more instances of this atomic type
         *
         * @return a sequence type representing one or more instances of this atomic type
         * @since 9.8.0.2
         */

        SequenceType zeroOrMore();

    }

    String toString();

    /**
     * Get extra diagnostic information about why a supplied item does not conform to this
     * item type, if available. If extra information is returned, it should be in the form of a complete
     * sentence, minus the closing full stop. No information should be returned for obvious cases.
     * @param item the item that doesn't match this type
     * @param th the type hierarchy cache
     * @return optionally, a message explaining why the item does not match the type
     */

    default Optional<String> explainMismatch(Item item, TypeHierarchy th) {
        return Optional.empty();
    }

}

