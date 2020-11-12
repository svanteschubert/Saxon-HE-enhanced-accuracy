////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.StructuredQName;

/**
 * A "plain type" is either an atomic type, or a union type that (a) imposes no restrictions other
 * than those imposed by its member types, and (b) has exclusively plain types as its member types
 */

public interface PlainType extends ItemType {

    /**
     * Get the name of this type as a structured QName
     * @return the name of this type. If the type is anonymous, an internally-generated type name is returned
     */

    StructuredQName getTypeName();

    /**
     * Test whether this type is namespace sensitive, that is, if a namespace context is needed
     * to translate between the lexical space and the value space. This is true for types derived
     * from, or containing, QNames and NOTATIONs
     *
     * @return true if any of the member types is namespace-sensitive, or if namespace sensitivity
     * cannot be determined because there are components missing from the schema.
     */

    boolean isNamespaceSensitive();

    /**
     * Get the list of plain types that are subsumed by this type
     *
     * @return for an atomic type, the type itself; for a plain union type, the list of plain types
     *         in its transitive membership
     */

    Iterable<? extends PlainType> getPlainMemberTypes() throws MissingComponentException;

    /**
     * Test whether a given item conforms to this type
     *
     * @param item The item to be tested
     * @param th   The type hierarchy cache. Currently used only when matching function items.
     * @return true if the item is an instance of this type; false otherwise
     */

    @Override
    boolean matches(Item item, TypeHierarchy th);

    /**
     * Redeclare getPrimitiveItemType() to return a more specific result type
     * Get the primitive item type corresponding to this item type.
     * For anyAtomicValue and union types it is Type.ATOMIC_VALUE. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that integer, xs:dayTimeDuration, and xs:yearMonthDuration
     * are considered to be primitive types.
     *
     * @return the corresponding primitive type (this is an instance of BuiltInAtomicType in all cases
     * except where this type is xs:error. The class ErrorType does not inherit from BuiltInAtomicType
     * because of multiple inheritance problems).
     */
    @Override
    AtomicType getPrimitiveItemType();
}

