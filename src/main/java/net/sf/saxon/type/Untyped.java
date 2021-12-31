////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import com.saxonica.ee.schema.UserComplexType;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.value.UntypedAtomicValue;
import net.sf.saxon.z.IntHashSet;

import static net.sf.saxon.type.SchemaComponent.ValidationStatus.VALIDATED;

/**
 * This class has a singleton instance which represents the complex type xdt:untyped,
 * used for elements that have not been validated.
 */

public enum Untyped implements ComplexType {

    // Josh Bloch recommends using a single-element enum type to implement singletons
    // (see Effective Java 2nd Edition, Item 3: Enforce the singleton property with a
    // private constructor or an enum type).

    INSTANCE;

    /**
     * Get the singular instance of this class
     *
     * @return the singular object representing xs:anyType
     */

    /*@NotNull*/
    public static Untyped getInstance() {
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
     * Get the local name of this type
     *
     * @return the local name of this type definition, if it has one. Return null in the case of an
     *         anonymous type.
     */

    /*@NotNull*/
    @Override
    public String getName() {
        return "untyped";
    }

    /**
     * Get the name of this type as an EQName, that is, a string in the format Q{uri}local.
     *
     * @return an EQName identifying the type, specifically "Q{http://www.w3.org/2001/XMLSchema}untyped"
     */
    @Override
    public String getEQName() {
        return "Q{" + NamespaceConstant.SCHEMA + "}untyped";
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
     * Get the variety of this complex type. This will be one of the values
     * {@link #VARIETY_EMPTY}, {@link #VARIETY_MIXED}, {@link #VARIETY_SIMPLE}, or
     * {@link #VARIETY_ELEMENT_ONLY}
     */

    @Override
    public int getVariety() {
        return VARIETY_MIXED;
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
        return 0;
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
        return false;
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
     * Check that this type is validly derived from a given type
     *
     * @param type  the type from which this type is derived
     * @param block the derivations that are blocked by the relevant element declaration
     */

    @Override
    public void checkTypeDerivationIsOK(SchemaType type, int block) {

    }

    /**
     * Get the fingerprint of the name of this type
     *
     * @return the fingerprint. Returns an invented fingerprint for an anonymous type.
     */

    @Override
    public int getFingerprint() {
        return StandardNames.XS_UNTYPED;
    }

    /**
     * Get the display name of the type: that is, a lexical QName with an arbitrary prefix
     *
     * @return a lexical QName identifying the type
     */

    /*@NotNull*/
    @Override
    public String getDisplayName() {
        return "xs:untyped";
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

    public final static StructuredQName NAME =
        new StructuredQName("xs", NamespaceConstant.SCHEMA, "untyped");

    /**
     * Test whether this SchemaType is a complex type
     *
     * @return true if this SchemaType is a complex type
     */

    @Override
    public boolean isComplexType() {
        return true;
    }

    /**
     * Test whether this is an anonymous type
     *
     * @return true if this SchemaType is an anonymous type
     */

    @Override
    public boolean isAnonymousType() {
        return false;
    }

    /**
     * Returns the base type that this type inherits from. This method can be used to get the
     * base type of a type that is known to be valid.
     * If this type is a Simpletype that is a built in primitive type then null is returned.
     *
     * @return the base type.
     * @throws IllegalStateException if this type is not valid.
     */

    /*@NotNull*/
    public SchemaType getKnownBaseType() throws IllegalStateException {
        return AnyType.getInstance();
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
     * Returns the base type that this type inherits from.
     * If this type is a Simpletype that is a built in primitive type then null is returned.
     *
     * @return the base type.
     */

    /*@NotNull*/
    @Override
    public SchemaType getBaseType() {
        return AnyType.getInstance();
    }

    /**
     * Test whether this ComplexType has been marked as abstract.
     *
     * @return false: this class is not abstract.
     */

    @Override
    public boolean isAbstract() {
        return false;
    }

    /**
     * Test whether this SchemaType is a simple type
     *
     * @return true if this SchemaType is a simple type
     */

    @Override
    public boolean isSimpleType() {
        return false;
    }

    /**
     * Test whether this SchemaType is an atomic type
     *
     * @return true if this SchemaType is an atomic type
     */

    @Override
    public boolean isAtomicType() {
        return false;
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
     * Test whether this complex type has complex content
     *
     * @return true: this complex type has complex content
     */
    @Override
    public boolean isComplexContent() {
        return true;
    }

    /**
     * Test whether this complex type has simple content
     *
     * @return false: this complex type has complex content
     */

    @Override
    public boolean isSimpleContent() {
        return false;
    }

    /**
     * Test whether this complex type has "all" content, that is, a content model
     * using an xs:all compositor
     *
     * @return false: this complex type does not use an "all" compositor
     */

    @Override
    public boolean isAllContent() {
        return false;
    }

    /**
     * For a complex type with simple content, return the simple type of the content.
     * Otherwise, return null.
     *
     * @return null: this complex type does not have simple content
     */

    /*@Nullable*/
    @Override
    public SimpleType getSimpleContentType() {
        return null;
    }

    /**
     * Test whether this complex type is derived by restriction
     *
     * @return true: this type is treated as a restriction of xs:anyType
     */
    @Override
    public boolean isRestricted() {
        return true;
    }

    /**
     * Test whether the content type of this complex type is empty
     *
     * @return false: the content model is not empty
     */

    @Override
    public boolean isEmptyContent() {
        return false;
    }

    /**
     * Test whether the content model of this complexType allows empty content
     *
     * @return true: the content is allowed to be empty
     */

    @Override
    public boolean isEmptiable() {
        return true;
    }

    /**
     * Test whether this complex type allows mixed content
     *
     * @return true: mixed content is allowed
     */

    @Override
    public boolean isMixedContent() {
        return true;
    }

    /**
     * Get a description of this type for use in diagnostics
     *
     * @return the string "xs:anyType"
     */

    /*@NotNull*/
    @Override
    public String getDescription() {
        return "xs:untyped";
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
     * Find an element particle within this complex type definition having a given element name
     * (identified by fingerprint), and return the schema type associated with that element particle.
     * If there is no such particle, return null. If the fingerprint matches an element wildcard,
     * return the type of the global element declaration with the given name if one exists, or AnyType
     * if none exists and lax validation is permitted by the wildcard.
     * @param elementName        Identifies the name of the child element within this content model
     * @param considerExtensions  True if types derived from this type by extension are to be included in the search
     */

    /*@NotNull*/
    @Override
    public SchemaType getElementParticleType(int elementName, boolean considerExtensions) {
        return this;
    }

    /**
     * Find an element particle within this complex type definition having a given element name
     * (identified by fingerprint), and return the cardinality associated with that element particle,
     * that is, the number of times the element can occur within this complex type. The value is one of
     * {@link net.sf.saxon.expr.StaticProperty#EXACTLY_ONE}, {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ZERO_OR_ONE},
     * {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ZERO_OR_MORE}, {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ONE_OR_MORE},
     * If there is no such particle, return zero.
     * @param elementName        Identifies the name of the child element within this content model
     * @param considerExtensions True if types derived from this type by extension are to be included in the search
     */

    @Override
    public int getElementParticleCardinality(int elementName, boolean considerExtensions) {
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
    }

    /**
     * Find an attribute use within this complex type definition having a given attribute name
     * (identified by fingerprint), and return the schema type associated with that attribute.
     * If there is no such attribute use, return null. If the fingerprint matches an attribute wildcard,
     * return the type of the global attribute declaration with the given name if one exists, or AnySimpleType
     * if none exists and lax validation is permitted by the wildcard.
     *
     * @param attributeName Identifies the name of the child element within this content model
     */

    /*@NotNull*/
    @Override
    public SimpleType getAttributeUseType(StructuredQName attributeName) {
        return BuiltInAtomicType.UNTYPED_ATOMIC;
    }

    /**
     * Find an attribute use within this complex type definition having a given attribute name
     * (identified by fingerprint), and return the cardinality associated with that attribute,
     * which will always be 0, 1, or 0-or-1.
     * If there is no such attribute use, return null. If the fingerprint matches an attribute wildcard,
     * return the type of the global attribute declaration with the given name if one exists, or AnySimpleType
     * if none exists and lax validation is permitted by the wildcard.
     * <p>If there are types derived from this type by extension, search those too.</p>
     *
     * @param attributeName Identifies the name of the child element within this content model
     * @return the schema type associated with the attribute use identified by the fingerprint.
     *         If there is no such attribute use, return null.
     */

    @Override
    public int getAttributeUseCardinality(StructuredQName attributeName) {
        return StaticProperty.ALLOWS_ZERO_OR_ONE;
    }

    /**
     * Return true if this type (or any known type derived from it by extension) allows the element
     * to have one or more attributes.
     *
     * @return true if attributes are allowed
     */

    @Override
    public boolean allowsAttributes() {
        return true;
    }


    /**
     * Get a list of all the names of elements that can appear as children of an element having this
     * complex type, as integer fingerprints. If the list is unbounded (because of wildcards or the use
     * of xs:anyType), return null.
     * @param children        a set, initially empty, which on return will hold the names of all permitted
     *                        child elements; if the result contains the value XS_INVALID_NAME, this indicates that it is not possible to enumerate
     *                        all the children, typically because of wildcards. In this case the other contents of the set should
     * @param ignoreWildcards True if wildcard particles in the content model should be ignored
     */

    @Override
    public void gatherAllPermittedChildren(IntHashSet children, boolean ignoreWildcards) {
        children.add(-1);
    }

    /**
     * Get a list of all the names of elements that can appear as descendants of an element having this
     * complex type, as integer fingerprints. If the list is unbounded (because of wildcards or the use
     * of xs:anyType), return null.
     *
     * @param descendants an integer set, initially empty, which on return will hold the fingerprints of all permitted
     *                    descendant elements; if the result contains the value XS_INVALID_NAME, this indicates that it is not possible to enumerate
     *                    all the descendants, typically because of wildcards. In this case the other contents of the set should
     *                    be ignored.
     */

    @Override
    public void gatherAllPermittedDescendants(/*@NotNull*/ IntHashSet descendants) {
        descendants.add(-1);
    }

    /**
     * Assuming an element is a permitted descendant in the content model of this type, determine
     * the type of the element when it appears as a descendant. If it appears with more than one type,
     * return xs:anyType.
     *
     * @param fingerprint the name of the required descendant element
     * @return the type of the descendant element; null if the element cannot appear as a descendant;
     *         anyType if it can appear with several different types
     */

    /*@NotNull*/
    @Override
    public SchemaType getDescendantElementType(int fingerprint) {
        return this;
    }

    /**
     * Assuming an element is a permitted descendant in the content model of this type, determine
     * the cardinality of the element when it appears as a descendant.
     *
     * @param elementFingerprint the name of the required descendant element
     * @return the cardinality of the descendant element within this complex type
     */

    @Override
    public int getDescendantElementCardinality(int elementFingerprint) {
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
    }

    /**
     * Ask whether this type (or any known type derived from it by extension) allows the element
     * to have children that match a wildcard
     *
     * @return true if the content model of this type, or its extensions, contains an element wildcard
     */

    @Override
    public boolean containsElementWildcard() {
        return true;
    }

    /**
     * Ask whether there are any assertions defined on this complex type
     *
     * @return true if there are any assertions
     */
    @Override
    public boolean hasAssertions() {
        return false;
    }


}

