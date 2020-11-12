////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.NamespaceResolver;

/**
 * This interface represents a simple type, which may be a built-in simple type, or
 * a user-defined simple type.
 */

public interface SimpleType extends SchemaType {

    /**
     * Test whether this Simple Type is an atomic type
     *
     * @return true if this is an atomic type
     */

    @Override
    boolean isAtomicType();

    /**
     * Test whether this Simple Type is a list type
     *
     * @return true if this is a list type
     */
    boolean isListType();

    /**
     * Test whether this Simple Type is a union type
     *
     * @return true if this is a union type
     */

    boolean isUnionType();

    /**
     * Determine whether this is a built-in type or a user-defined type
     *
     * @return true if this is a built-in type
     */

    boolean isBuiltInType();

    /**
     * Get the built-in type from which this type is derived by restriction
     *
     * @return the built-in type from which this type is derived by restriction. This will not necessarily
     *         be a primitive type.
     */

    /*@Nullable*/ SchemaType getBuiltInBaseType();

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
     *         returned by this SequenceIterator will all be of type {@link net.sf.saxon.value.AtomicValue},
     * @throws ValidationException if the supplied value is not in the lexical space of the data type
     */

    AtomicSequence getTypedValue(CharSequence value, /*@Nullable*/ NamespaceResolver resolver, ConversionRules rules)
            throws ValidationException;

    /**
     * Check whether a given input string is valid according to this SimpleType
     *
     * @param value      the input string to be checked
     * @param nsResolver a namespace resolver used to resolve namespace prefixes if the type
     *                   is namespace sensitive. The value supplied may be null; in this case any namespace-sensitive
     *                   content will throw an UnsupportedOperationException.
     * @param rules      the conversion rules from the configuration
     * @return null if validation succeeds; or return a ValidationFailure describing the validation failure
     *         if validation fails. Note that the exception is returned rather than being thrown.
     * @throws UnsupportedOperationException if the type is namespace-sensitive and no namespace
     *                                       resolver is supplied
     */

    /*@Nullable*/
    ValidationFailure validateContent(
            /*@NotNull*/ CharSequence value, /*@Nullable*/ NamespaceResolver nsResolver, /*@NotNull*/ ConversionRules rules);

    /**
     * Test whether this type is namespace sensitive, that is, if a namespace context is needed
     * to translate between the lexical space and the value space. This is true for types derived
     * from, or containing, QNames and NOTATIONs
     *
     * @return true if the type is namespace-sensitive, or if the namespace-sensitivity cannot be determined
     * because there are missing schema components. (However, for xs:anyAtomicType, the result returned is
     * false, even though the type allows xs:QName instances.)
     */

    boolean isNamespaceSensitive();

    /**
     * Determine how values of this simple type are whitespace-normalized.
     *
     * @return one of {@link net.sf.saxon.value.Whitespace#PRESERVE}, {@link net.sf.saxon.value.Whitespace#COLLAPSE},
     *         {@link net.sf.saxon.value.Whitespace#REPLACE}.
     */

    public int getWhitespaceAction();

    /**
     * Apply any pre-lexical facets, other than whitespace. At the moment the only such
     * facet is saxon:preprocess
     *
     * @param input the value to be preprocessed
     * @return the value after preprocessing
     * @throws ValidationException if preprocessing detects that the value is invalid
     */

    public CharSequence preprocess(CharSequence input) throws ValidationException;

    /**
     * Reverse any pre-lexical facets, other than whitespace. At the moment the only such
     * facet is saxon:preprocess. This is called when converting a value of this type to
     * a string
     *
     * @param input the value to be postprocessed: this is the "ordinary" result of converting
     *              the value to a string
     * @return the value after postprocessing
     * @throws ValidationException if postprocessing detects that the value is invalid
     */

    public CharSequence postprocess(CharSequence input) throws ValidationException;

}

