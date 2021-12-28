////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.om.Genre;
import net.sf.saxon.om.Item;
import net.sf.saxon.value.SequenceType;


/**
 * An implementation of ItemType that matches any item (node or atomic value)
 */

public class AnyItemType implements ItemType.WithSequenceTypeCache {

    private SequenceType _one;
    private SequenceType _oneOrMore;
    private SequenceType _zeroOrOne;
    private SequenceType _zeroOrMore;

    private AnyItemType() {
    }

    /*@NotNull*/ private static AnyItemType theInstance = new AnyItemType();

    /**
     * Factory method to get the singleton instance
     * @return the singleton instance
     */

    /*@NotNull*/
    public static AnyItemType getInstance() {
        return theInstance;
    }

    /**
     * Determine the Genre (top-level classification) of this type
     *
     * @return the Genre to which this type belongs, specifically {@link Genre#ANY}
     */
    @Override
    public Genre getGenre() {
        return Genre.ANY;
    }

    /**
     * Get the corresponding {@link net.sf.saxon.type.UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        return UType.ANY;
    }

    /**
     * Determine whether this item type is an atomic type
     *
     * @return true if this is ANY_ATOMIC_TYPE or a subtype thereof
     */
    @Override
    public boolean isAtomicType() {
        return false;
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
        return "";
    }

    /**
     * Determine whether this item type is atomic (that is, whether it can ONLY match
     * atomic values)
     *
     * @return false: this type can match nodes or atomic values
     */

    @Override
    public boolean isPlainType() {
        return false;
    }

    /**
     * Test whether a given item conforms to this type
     *
     *
     *
     * @param item    The item to be tested
     * @param th
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item, TypeHierarchy th) {
        return true;
    }

    /**
     * Get the primitive item type corresponding to this item type. For item(),
     * this is Type.ITEM. For node(), it is Type.NODE. For specific node kinds,
     * it is the value representing the node kind, for example Type.ELEMENT.
     * For anyAtomicValue it is Type.ATOMIC_VALUE. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that INTEGER is considered to be a primitive type.
     */

    /*@NotNull*/
    @Override
    public ItemType getPrimitiveItemType() {
        return this;
    }

    @Override
    public int getPrimitiveType() {
        return Type.ITEM;
    }

    /*@NotNull*/
    @Override
    public AtomicType getAtomizedItemType() {
        return BuiltInAtomicType.ANY_ATOMIC;
    }

    /**
     * Ask whether values of this type are atomizable
     *
     * @return true unless it is known that these items will be elements with element-only
     *         content, in which case return false
     * @param th The type hierarchy cache
     */

    @Override
    public boolean isAtomizable(TypeHierarchy th) {
        return true;
    }

    @Override
    public double getDefaultPriority() {
        return -2;
    }

    /*@NotNull*/
    public String toString() {
        return "item()";
    }

    /**
     * Returns a hash code value for the object.
     */

    public int hashCode() {
        return "AnyItemType".hashCode();
    }

    /**
     * Get a sequence type representing exactly one instance of this atomic type
     *
     * @return a sequence type representing exactly one instance of this atomic type
     * @since 9.8.0.2
     */

    @Override
    public SequenceType one() {
        if (_one == null) {
            _one = new SequenceType(this, StaticProperty.EXACTLY_ONE);
        }
        return _one;
    }

    /**
     * Get a sequence type representing zero or one instances of this atomic type
     *
     * @return a sequence type representing zero or one instances of this atomic type
     * @since 9.8.0.2
     */

    @Override
    public SequenceType zeroOrOne() {
        if (_zeroOrOne == null) {
            _zeroOrOne = new SequenceType(this, StaticProperty.ALLOWS_ZERO_OR_ONE);
        }
        return _zeroOrOne;
    }

    /**
     * Get a sequence type representing one or more instances of this atomic type
     *
     * @return a sequence type representing one or more instances of this atomic type
     * @since 9.8.0.2
     */

    @Override
    public SequenceType oneOrMore() {
        if (_oneOrMore == null) {
            _oneOrMore = new SequenceType(this, StaticProperty.ALLOWS_ONE_OR_MORE);
        }
        return _oneOrMore;
    }

    /**
     * Get a sequence type representing one or more instances of this atomic type
     *
     * @return a sequence type representing one or more instances of this atomic type
     * @since 9.8.0.2
     */

    @Override
    public SequenceType zeroOrMore() {
        if (_zeroOrMore == null) {
            _zeroOrMore = new SequenceType(this, StaticProperty.ALLOWS_ZERO_OR_MORE);
        }
        return _zeroOrMore;
    }

}

