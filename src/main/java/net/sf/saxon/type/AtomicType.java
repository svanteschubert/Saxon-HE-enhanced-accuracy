////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.Genre;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.value.AtomicValue;

import java.util.Optional;

/**
 * Interface for atomic types (these are either built-in atomic types
 * or user-defined atomic types). An AtomicType is both an ItemType (a possible type
 * for items in a sequence) and a SchemaType (a possible type for validating and
 * annotating nodes).
 */
public interface AtomicType extends SimpleType, PlainType, CastingTarget {

    /**
     * Determine the Genre (top-level classification) of this type
     *
     * @return the Genre to which this type belongs, specifically {@link Genre#ATOMIC}
     */
    @Override
    default Genre getGenre() {
        return Genre.ATOMIC;
    }

    /**
     * Validate that a primitive atomic value is a valid instance of a type derived from the
     * same primitive type.
     *
     * @param primValue    the value in the value space of the primitive type.
     * @param lexicalValue the value in the lexical space. If null, the string value of primValue
     *                     is used. This value is checked against the pattern facet (if any)
     * @param rules the conversion rules for this configuration
     * @return null if the value is valid; otherwise, a ValidationFailure object indicating
     *         the nature of the error.
     * @throws UnsupportedOperationException in the case of an external object type
     */

    ValidationFailure validate(AtomicValue primValue, CharSequence lexicalValue, ConversionRules rules);

    /**
     * Determine whether the atomic type is ordered, that is, whether less-than and greater-than comparisons
     * are permitted
     *
     * @param optimistic if true, the function takes an optimistic view, returning true if ordering comparisons
     *                   are available for some subtype. This mainly affects xs:duration, where the function returns true if
     *                   optimistic is true, false if it is false.
     * @return true if ordering operations are permitted
     */

    boolean isOrdered(boolean optimistic);

    /**
     * Determine whether the type is abstract, that is, whether it cannot have instances that are not also
     * instances of some concrete subtype
     * @return true if the type is abstract
     */

    boolean isAbstract();

    /**
     * Determine whether the atomic type is a primitive type.  The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration;
     * xs:untypedAtomic; and all supertypes of these (xs:anyAtomicType, xs:numeric, ...)
     *
     * @return true if the type is considered primitive under the above rules
     */

    boolean isPrimitiveType();

    /**
     * Ask whether this type is an ID type. This is defined to be any simple type
     * who typed value may contain atomic values of type xs:ID: that is, it includes types derived
     * from ID by restriction, list, or union. Note that for a node to be treated
     * as an ID, its typed value must be a *single* atomic value of type ID; the type of the
     * node, however, can still allow a list.
     */

    @Override
    boolean isIdType();

    /**
     * Ask whether this type is an IDREF or IDREFS type. This is defined to be any simple type
     * who typed value may contain atomic values of type xs:IDREF: that is, it includes types derived
     * from IDREF or IDREFS by restriction, list, or union
     */

    @Override
    boolean isIdRefType();

    /**
     * Determine whether the atomic type is a built-in type. The built-in atomic types are the 41 atomic types
     * defined in XML Schema, plus xs:dayTimeDuration and xs:yearMonthDuration,
     * xs:untypedAtomic, and all supertypes of these (xs:anyAtomicType, xs:numeric, ...)
     */

    @Override
    boolean isBuiltInType();

    /**
     * Get the name of this type as a StructuredQName, unless the type is anonymous, in which case
     * return null
     *
     * @return the name of the atomic type, or null if the type is anonymous.
     */

    @Override
    StructuredQName getTypeName();


    /**
     * Get a StringConverter, an object which converts strings in the lexical space of this
     * data type to instances (in the value space) of the data type.
     * @param rules the conversion rules to be used
     * @return a StringConverter to do the conversion, or null if no built-in converter is available.
     */

    StringConverter getStringConverter(ConversionRules rules);

    /**
     * Get extra diagnostic information about why a supplied item does not conform to this
     * item type, if available. If extra information is returned, it should be in the form of a complete
     * sentence, minus the closing full stop. No information should be returned for obvious cases.
     *
     * @param item the item that doesn't match this type
     * @param th   the type hierarchy cache
     * @return optionally, a message explaining why the item does not match the type
     */
    @Override
    default Optional<String> explainMismatch(Item item, TypeHierarchy th) {
        if (item instanceof AtomicValue) {
            return Optional.of("The supplied value is of type " + ((AtomicValue) item).getItemType());
        } else {
            return Optional.of("The supplied value is " + item.getGenre().getDescription());
        }
    }

    /**
     * Get the default priority when this ItemType is used as an XSLT pattern.
     * @return the default priority. For an atomic type this is 1 minus 0.5^N, where
     * N is the depth of the type in the type hierarchy. The result is 0 for
     * xs:anyAtomicType, 0.5 for a primitive type such as xs:date, and between 0.5 and
     * 1.0 for derived atomic types.
     */
    @Override
    default double getDefaultPriority() {
        if (this == BuiltInAtomicType.ANY_ATOMIC) {
            return 0;
        }
        double factor = 1;
        SchemaType at = this;
        do {
            factor *= 0.5;
            at = at.getBaseType();
        } while (at != BuiltInAtomicType.ANY_ATOMIC);
        return 1 - factor;
    }
}

