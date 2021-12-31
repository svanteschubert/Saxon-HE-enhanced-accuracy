////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import com.saxonica.ee.schema.UserSimpleType;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.value.UntypedAtomicValue;
import net.sf.saxon.value.Whitespace;

import static net.sf.saxon.type.SchemaComponent.ValidationStatus.VALIDATED;

/**
 * This class has a singleton instance which represents the XML Schema built-in type xs:anySimpleType
 */

public enum AnySimpleType implements SimpleType {

    // Josh Bloch recommends using a single-element enum type to implement singletons
    // (see Effective Java 2nd Edition, Item 3: Enforce the singleton property with a
    // private constructor or an enum type).

    INSTANCE;

    /**
     * Get the local name of this type
     *
     * @return the local name of this type definition, if it has one. Return null in the case of an
     *         anonymous type.
     */

    /*@NotNull*/
    @Override
    public String getName() {
        return "anySimpleType";
    }

    /**
     * Get the target namespace of this type
     *
     * @return the target namespace of this type definition, if it has one. Return null in the case
     *         of an anonymous type, and in the case of a global type defined in a no-namespace schema.
     */

    @Override
    public String getTargetNamespace() {
        return NamespaceConstant.SCHEMA;
    }

    /**
     * Get the name of this type as an EQName, that is, a string in the format Q{uri}local.
     *
     * @return an EQName identifying the type. In the case of an anonymous type, an internally-generated
     *         name is returned
     */
    @Override
    public String getEQName() {
        return "Q{" + NamespaceConstant.SCHEMA + "}anySimpleType";
    }

    /**
     * Determine whether this is a built-in type or a user-defined type
     */

    @Override
    public boolean isBuiltInType() {
        return true;
    }

    /**
     * Ask whether this type is an ID type. This is defined to be any simple type
     * who typed value may contain atomic values of type xs:ID: that is, it includes types derived
     * from ID by restriction, list, or union. Note that for a node to be treated
     * as an ID, its typed value must be a *single* atomic value of type ID; the type of the
     * node, however, can still allow a list.
     */

    @Override
    public boolean isIdType() {
        return false;
    }

    /**
     * Ask whether this type is an IDREF or IDREFS type. This is defined to be any simple type
     * who typed value may contain atomic values of type xs:IDREF: that is, it includes types derived
     * from IDREF or IDREFS by restriction, list, or union
     */

    @Override
    public boolean isIdRefType() {
        return false;
    }

    /**
     * Get the redefinition level. This is zero for a component that has not been redefined;
     * for a redefinition of a level-0 component, it is 1; for a redefinition of a level-N
     * component, it is N+1. This concept is used to support the notion of "pervasive" redefinition:
     * if a component is redefined at several levels, the top level wins, but it is an error to have
     * two versions of the component at the same redefinition level.
     *
     * @return the redefinition level
     */

    @Override
    public int getRedefinitionLevel() {
        return 0;
    }

    /**
     * Get the URI of the schema document containing the definition of this type
     *
     * @return null for a built-in type
     */

    /*@Nullable*/
    @Override
    public String getSystemId() {
        return null;
    }

    /**
     * Get the singular instance of this class
     *
     * @return the singular object representing xs:anyType
     */

    /*@NotNull*/
    public static AnySimpleType getInstance() {
        return INSTANCE;
    }

    /**
     * Get the validation status - always valid
     */
    @Override
    public ValidationStatus getValidationStatus() {
        return VALIDATED;
    }

    /**
     * Get the base type
     *
     * @return AnyType
     */

    @Override
    public SchemaType getBaseType() {
        return AnyType.getInstance();
    }

    /**
     * Test whether this SchemaType is a complex type
     *
     * @return true if this SchemaType is a complex type
     */

    @Override
    public boolean isComplexType() {
        return false;
    }

    /**
     * Test whether this SchemaType is a simple type
     *
     * @return true if this SchemaType is a simple type
     */

    @Override
    public boolean isSimpleType() {
        return true;
    }

    /**
     * Get the fingerprint of the name of this type
     *
     * @return the fingerprint.
     */

    @Override
    public int getFingerprint() {
        return StandardNames.XS_ANY_SIMPLE_TYPE;
    }

    /**
     * Get the name of the type as a StructuredQName
     *
     * @return a StructuredQName identifying the type.  In the case of an anonymous type, an internally-generated
     * name is returned
     */
    @Override
    public StructuredQName getStructuredQName() {
        return NAME;
    }

    public final static StructuredQName NAME = new StructuredQName("xs", NamespaceConstant.SCHEMA, "anySimpleType");

    /**
     * Get a description of this type for use in diagnostics
     *
     * @return the string "xs:anyType"
     */

    /*@NotNull*/
    @Override
    public String getDescription() {
        return "xs:anySimpleType";
    }

    /**
     * Get the display name of the type: that is, a lexical QName with an arbitrary prefix
     *
     * @return a lexical QName identifying the type
     */

    /*@NotNull*/
    @Override
    public String getDisplayName() {
        return "xs:anySimpleType";
    }

    /**
     * Test whether this is the same type as another type. They are considered to be the same type
     * if they are derived from the same type definition in the original XML representation (which
     * can happen when there are multiple includes of the same file)
     */

    @Override
    public boolean isSameType(SchemaType other) {
        return other == INSTANCE;
    }

    /**
     * Get the typed value of a node that is annotated with this schema type.
     *
     * @param node the node whose typed value is required
     * @return the typed value.
     * @since 8.5
     */

    /*@NotNull*/
    @Override
    public AtomicSequence atomize(/*@NotNull*/ NodeInfo node) {
        return new UntypedAtomicValue(node.getStringValueCS());
    }

    /**
     * Check that this type is validly derived from a given type
     *
     * @param type  the type from which this type is derived
     * @param block the derivations that are blocked by the relevant element declaration
     * @throws SchemaException if the derivation is not allowed
     */

    @Override
    public void checkTypeDerivationIsOK(SchemaType type, int block) throws SchemaException {
        if (type == this) {
            return;
        }
        throw new SchemaException("Cannot derive xs:anySimpleType from another type");
    }

    /**
     * Test whether this Simple Type is an atomic type
     *
     * @return false, this is not (necessarily) an atomic type
     */

    @Override
    public boolean isAtomicType() {
        return false;
    }

    @Override
    public boolean isAnonymousType() {
        return false;
    }


    /**
     * Determine whether this is a list type
     *
     * @return false (it isn't a list type)
     */
    @Override
    public boolean isListType() {
        return false;
    }

    /**
     * Determin whether this is a union type
     *
     * @return false (it isn't a union type)
     */
    @Override
    public boolean isUnionType() {
        return false;
    }

    /**
     * Get the built-in ancestor of this type in the type hierarchy
     *
     * @return this type itself
     */
    /*@NotNull*/
    @Override
    public SchemaType getBuiltInBaseType() {
        return this;
    }

    /**
     * Get the typed value corresponding to a given string value, assuming it is
     * valid against this type
     *
     * @param value    the string value
     * @param resolver a namespace resolver used to resolve any namespace prefixes appearing
     *                 in the content of values. Can supply null, in which case any namespace-sensitive content
     *                 will be rejected.
     * @param rules    rules used for type conversion
     * @return an iterator over the atomic sequence comprising the typed value. The objects
     *         returned by this SequenceIterator will all be of type {@link net.sf.saxon.value.AtomicValue}
     */

    /*@NotNull*/
    @Override
    public AtomicSequence getTypedValue(CharSequence value, NamespaceResolver resolver, ConversionRules rules) {
        return new UntypedAtomicValue(value);
    }

    /**
     * Check whether a given input string is valid according to this SimpleType
     *
     * @param value      the input string to be checked
     * @param nsResolver a namespace resolver used to resolve namespace prefixes if the type
     *                   is namespace sensitive. The value supplied may be null; in this case any namespace-sensitive
     *                   content will throw an UnsupportedOperationException.
     * @param rules      rules used for type conversion
     * @return null if validation succeeds (which it always does for this implementation)
     * @throws UnsupportedOperationException if the type is namespace-sensitive and no namespace
     *                                       resolver is supplied
     */
    /*@Nullable*/
    @Override
    public ValidationFailure validateContent(/*@NotNull*/ CharSequence value, NamespaceResolver nsResolver, /*@NotNull*/ ConversionRules rules) {
        return null;
    }

    /**
     * Test whether this type represents namespace-sensitive content
     *
     * @return false
     */
    @Override
    public boolean isNamespaceSensitive() {
        return false;
    }

    /**
     * Returns the value of the 'block' attribute for this type, as a bit-signnificant
     * integer with fields such as {@link SchemaType#DERIVATION_LIST} and {@link SchemaType#DERIVATION_EXTENSION}
     *
     * @return the value of the 'block' attribute for this type
     */

    @Override
    public int getBlock() {
        return 0;
    }

    /**
     * Gets the integer code of the derivation method used to derive this type from its
     * parent. Returns zero for primitive types.
     *
     * @return a numeric code representing the derivation method, for example {@link SchemaType#DERIVATION_RESTRICTION}
     */

    @Override
    public int getDerivationMethod() {
        return SchemaType.DERIVATION_RESTRICTION;
    }

    /**
     * Determines whether derivation (of a particular kind)
     * from this type is allowed, based on the "final" property
     *
     * @param derivation the kind of derivation, for example {@link SchemaType#DERIVATION_LIST}
     * @return true if this kind of derivation is allowed
     */

    @Override
    public boolean allowsDerivation(int derivation) {
        return true;
    }

    /**
     * Get the types of derivation that are not permitted, by virtue of the "final" property.
     *
     * @return the types of derivation that are not permitted, as a bit-significant integer
     *         containing bits such as {@link net.sf.saxon.type.SchemaType#DERIVATION_EXTENSION}
     */
    @Override
    public int getFinalProhibitions() {
        return 0;
    }

    /**
     * Determine how values of this simple type are whitespace-normalized.
     *
     * @return one of {@link net.sf.saxon.value.Whitespace#PRESERVE}, {@link net.sf.saxon.value.Whitespace#COLLAPSE},
     *         {@link net.sf.saxon.value.Whitespace#REPLACE}.
     */

    @Override
    public int getWhitespaceAction() {
        return Whitespace.PRESERVE;
    }

    /**
     * Analyze an expression to see whether the expression is capable of delivering a value of this
     * type.
     *
     * @param expression the expression that delivers the content
     * @param kind       the node kind whose content is being delivered: {@link Type#ELEMENT},
*                   {@link Type#ATTRIBUTE}, or {@link Type#DOCUMENT}
     */

    @Override
    public void analyzeContentExpression(Expression expression, int kind) {
        //return;
    }

    /**
     * Apply any pre-lexical facets, other than whitespace. At the moment the only such
     * facet is saxon:preprocess
     *
     * @param input the value to be preprocessed
     * @return the value after preprocessing
     */

    @Override
    public CharSequence preprocess(CharSequence input) {
        return input;
    }

    /**
     * Reverse any pre-lexical facets, other than whitespace. At the moment the only such
     * facet is saxon:preprocess. This is called when converting a value of this type to
     * a string
     *
     * @param input the value to be postprocessed: this is the "ordinary" result of converting
     *              the value to a string
     * @return the value after postprocessing
     */

    @Override
    public CharSequence postprocess(CharSequence input) throws ValidationException {
        return input;
    }

}

