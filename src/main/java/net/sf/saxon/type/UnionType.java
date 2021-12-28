////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;


import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceType;

import java.util.Optional;

import static net.sf.saxon.om.Genre.ATOMIC;

/**
 * Interface representing a union type. This may be either a built-in union type (of which there are
 * currently two, namely ErrorType and NumericType), or a user-defined union type.
 */

public interface UnionType extends ItemType, CastingTarget {

    /**
     * Get the name of the type. If the type is unnamed, return an invented
     * type in the {@link net.sf.saxon.lib.NamespaceConstant#ANONYMOUS} namespace
     * @return the type name
     */

    StructuredQName getTypeName();

    default StructuredQName getStructuredQName() {
        return getTypeName();
    }

    /**
     * Ask whether the union contains a list type among its member types
     *
     * @return true of one of the member types is a list type
     */

    boolean containsListType() throws MissingComponentException;

    /**
     * Get the "plain" types in the transitive membership. Plain types are atomic types and union types that
     * are defined directly in terms of other plain types, without adding any restriction facets.
     *
     * @return the atomic types and plain union types in the transitive membership of the union type.
     */

    Iterable<? extends PlainType> getPlainMemberTypes() throws MissingComponentException;

    /**
     * Get the result type of a cast operation to this union type, as a sequence type.
     *
     * @return the result type of casting, as precisely as possible. For example, if all the member types of
     *         the union are derived from the same primitive type, this will return that primitive type.
     */

    SequenceType getResultTypeOfCast();

    /**
     * Get the typed value corresponding to a given string value, assuming it is
     * valid against this type
     *
     * @param value    the string value
     * @param resolver a namespace resolver used to resolve any namespace prefixes appearing
     *                 in the content of values. Can supply null, in which case any namespace-sensitive content
     *                 will be rejected.
     * @param rules    the conversion rules from the configuration
     * @return the atomic sequence comprising the typed value. The objects
     * returned by this SequenceIterator will all be of type {@link net.sf.saxon.value.AtomicValue},
     * @throws ValidationException if the supplied value is not in the lexical space of the data type
     */

    AtomicSequence getTypedValue(CharSequence value, /*@Nullable*/ NamespaceResolver resolver, ConversionRules rules)
            throws ValidationException;

    /**
     * Validate an atomic value, which is known to be an instance of one of the member types of the
     * union, against any facets (pattern facets or enumeration facets) defined at the level of the
     * union itself.
     * @param value the Atomic Value to be checked. This must be an instance of a member type of the
     *              union
     * @param rules the ConversionRules for the Configuration
     * @return a ValidationFailure if the value is not valid; null if it is valid.
     */

    ValidationFailure checkAgainstFacets(AtomicValue value, ConversionRules rules);

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
        if (item.getGenre() == ATOMIC) {
            FastStringBuffer message = new FastStringBuffer(256);
            message.append("The required type is a union type allowing any of ");

            String punctuation = "(";
            try {
                for (PlainType member: getPlainMemberTypes()) {
                    message.append(punctuation);
                    punctuation = ", ";
                    message.append(member.getTypeName().getDisplayName());
                }
            } catch (MissingComponentException e) {
                message.append("*member types unobtainable*");
            }
            message.append("), but the supplied type ");
            message.append(((AtomicValue)item).getItemType().getDisplayName());
            message.append(" is not any of these");
            return Optional.of(message.toString());
        } else {
            return Optional.empty();
        }
    }

    default String getDescription() {
        if (this instanceof SimpleType) {
            return ((SimpleType)this).getDescription();
        } else {
            return toString();
        }
    }
}

