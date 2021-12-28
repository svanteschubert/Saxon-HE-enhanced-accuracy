////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;

/**
 * SchemaType is an interface implemented by all schema types: simple and complex types, built-in and
 * user-defined types.
 * <p>There is a hierarchy of interfaces that extend SchemaType, representing the top levels of the schema
 * type system: SimpleType and ComplexType, with SimpleType further subdivided into List, Union, and Atomic
 * types.</p>
 * <p>The implementations of these interfaces are organized into a different hierarchy: on the one side,
 * built-in types such as AnyType, AnySimpleType, and the built-in atomic types and list types; on the other
 * side, user-defined types defined in a schema.</p>
 */

public interface SchemaType extends SchemaComponent {

    // DerivationMethods. These constants are copied from org.w3.dom.TypeInfo. They are redefined here to avoid
    // creating a dependency on the TypeInfo class, which is only available when JAXP 1.3 is available.

    /**
     * If the document's schema is an XML Schema [<a href='http://www.w3.org/TR/2001/REC-xmlschema-1-20010502/'>XML Schema Part 1</a>]
     * , this constant represents the derivation by <a href='http://www.w3.org/TR/2001/REC-xmlschema-1-20010502/#key-typeRestriction'>
     * restriction</a> if complex types are involved, or a <a href='http://www.w3.org/TR/2001/REC-xmlschema-1-20010502/#element-restriction'>
     * restriction</a> if simple types are involved.
     * <br>  The reference type definition is derived by restriction from the
     * other type definition if the other type definition is the same as the
     * reference type definition, or if the other type definition can be
     * reached recursively following the {base type definition} property
     * from the reference type definition, and all the <em>derivation methods</em> involved are restriction.
     */
    int DERIVATION_RESTRICTION = 0x00000001;
    /**
     * If the document's schema is an XML Schema [<a href='http://www.w3.org/TR/2001/REC-xmlschema-1-20010502/'>XML Schema Part 1</a>]
     * , this constant represents the derivation by <a href='http://www.w3.org/TR/2001/REC-xmlschema-1-20010502/#key-typeExtension'>
     * extension</a>.
     * <br>  The reference type definition is derived by extension from the
     * other type definition if the other type definition can be reached
     * recursively following the {base type definition} property from the
     * reference type definition, and at least one of the <em>derivation methods</em> involved is an extension.
     */
    int DERIVATION_EXTENSION = 0x00000002;
    /**
     * If the document's schema is an XML Schema [<a href='http://www.w3.org/TR/2001/REC-xmlschema-1-20010502/'>XML Schema Part 1</a>]
     * , this constant represents the <a href='http://www.w3.org/TR/2001/REC-xmlschema-1-20010502/#element-union'>
     * union</a> if simple types are involved.
     * <br> The reference type definition is derived by union from the other
     * type definition if there exists two type definitions T1 and T2 such
     * as the reference type definition is derived from T1 by
     * <code>DERIVATION_RESTRICTION</code> or
     * <code>DERIVATION_EXTENSION</code>, T2 is derived from the other type
     * definition by <code>DERIVATION_RESTRICTION</code>, T1 has {variety} <em>union</em>, and one of the {member type definitions} is T2. Note that T1 could be
     * the same as the reference type definition, and T2 could be the same
     * as the other type definition.
     */
    int DERIVATION_UNION = 0x00000004;
    /**
     * If the document's schema is an XML Schema [<a href='http://www.w3.org/TR/2001/REC-xmlschema-1-20010502/'>XML Schema Part 1</a>]
     * , this constant represents the <a href='http://www.w3.org/TR/2001/REC-xmlschema-1-20010502/#element-list'>list</a>.
     * <br> The reference type definition is derived by list from the other
     * type definition if there exists two type definitions T1 and T2 such
     * as the reference type definition is derived from T1 by
     * <code>DERIVATION_RESTRICTION</code> or
     * <code>DERIVATION_EXTENSION</code>, T2 is derived from the other type
     * definition by <code>DERIVATION_RESTRICTION</code>, T1 has {variety} <em>list</em>, and T2 is the {item type definition}. Note that T1 could be the same as
     * the reference type definition, and T2 could be the same as the other
     * type definition.
     */
    int DERIVATION_LIST = 0x00000008;

    /**
     * Derivation by substitution.
     * This constant, unlike the others, is NOT defined in the DOM level 3 TypeInfo interface.
     */

    int DERIVE_BY_SUBSTITUTION = 16;

    /**
     * Get the local name of this type
     *
     * @return the local name of this type definition, if it has one. Return null in the case of an
     *         anonymous type.
     */

    /*@Nullable*/
    String getName();

    /**
     * Get the target namespace of this type
     *
     * @return the target namespace of this type definition, if it has one. Return null in the case
     *         of an anonymous type, and in the case of a global type defined in a no-namespace schema.
     */

    /*@Nullable*/
    String getTargetNamespace();

    /**
     * Get the fingerprint of the name of this type
     *
     * @return the fingerprint. Returns an invented fingerprint for an anonymous type.
     */

    int getFingerprint();

    /**
     * Get the display name of the type: that is, a lexical QName with an arbitrary prefix
     *
     * @return a lexical QName identifying the type. In the case of an anonymous type, an internally-generated
     *         name is returned
     */

    String getDisplayName();

    /**
     * Get the name of the type as a StructuredQName
     * @return a StructuredQName identifying the type.  In the case of an anonymous type, an internally-generated
     *         name is returned
     */

    StructuredQName getStructuredQName();

    /**
     * Get the name of this type as an EQName, that is, a string in the format Q{uri}local.
     *
     * @return an EQName identifying the type. In the case of an anonymous type, an internally-generated
     *         name is returned
     */

    String getEQName();

    /**
     * Test whether this SchemaType is a complex type
     *
     * @return true if this SchemaType is a complex type
     */

    boolean isComplexType();

    /**
     * Test whether this SchemaType is a simple type
     *
     * @return true if this SchemaType is a simple type
     */

    boolean isSimpleType();

    /**
     * Test whether this SchemaType is an atomic type
     *
     * @return true if this SchemaType is an atomic type
     */

    boolean isAtomicType();

    /**
     * Test whether this is an anonymous type
     *
     * @return true if this SchemaType is an anonymous type
     */

    boolean isAnonymousType();

    /**
     * Returns the value of the 'block' attribute for this type, as a bit-significant
     * integer with fields such as {@link SchemaType#DERIVATION_LIST} and {@link SchemaType#DERIVATION_EXTENSION}.
     * This corresponds to the property "prohibited substitutions" in the schema component model.
     *
     * @return the value of the 'block' attribute for this type
     */

    int getBlock();

    /**
     * Returns the base type that this type inherits from. This method can be used to get the
     * base type of a type that is known to be valid.
     * If this type is a Simpletype that is a built in primitive type then null is returned.
     *
     * @return the base type, or null if this is xs:anyType (the root of the type hierarchy)
     */

    SchemaType getBaseType();

    /**
     * Get the nearest named type in the type hierarchy, that is, the nearest type that
     * is not anonymous. (In practice, since types cannot be derived from anonymous types,
     * this will either the type itself, or its immediate base type).
     * @return the nearest type, found by following the {@code getBaseType()} relation
     * recursively, that is not an anonymous type
     */

    default SchemaType getNearestNamedType() {
        SchemaType type = this;
        while (type.isAnonymousType()) {
            type = type.getBaseType();
        }
        return type;
    }

    /**
     * Gets the integer code of the derivation method used to derive this type from its
     * parent. Returns zero for primitive types.
     *
     * @return a numeric code representing the derivation method, for example {@link SchemaType#DERIVATION_RESTRICTION}
     */

    int getDerivationMethod();

    /**
     * Get the types of derivation that are not permitted, by virtue of the "final" property.
     *
     * @return the types of derivation that are not permitted, as a bit-significant integer
     *         containing bits such as {@link SchemaType#DERIVATION_EXTENSION}
     */

    int getFinalProhibitions();

    /**
     * Determines whether derivation (of a particular kind)
     * from this type is allowed, based on the "final" property
     *
     * @param derivation the kind of derivation, for example {@link SchemaType#DERIVATION_LIST}
     * @return true if this kind of derivation is allowed
     */

    boolean allowsDerivation(int derivation);

    /**
     * Analyze an XPath expression to see whether the expression is capable of delivering a value of this
     * type. This method is called during static analysis of a query or stylesheet to give compile-time
     * warnings when "impossible" paths are used.
     *
     * @param expression the expression that delivers the content
     * @param kind       the node kind whose content is being delivered: {@link Type#ELEMENT},
     *                   {@link Type#ATTRIBUTE}, or {@link Type#DOCUMENT}
     * @throws XPathException if the expression will never deliver a value of the correct type
     */

    void analyzeContentExpression(Expression expression, int kind) throws XPathException;

    /**
     * Get the typed value of a node that is annotated with this schema type.
     *
     * @param node the node whose typed value is required
     * @return the typed value.
     * @throws net.sf.saxon.trans.XPathException
     *          if the node cannot be atomized, for example if this is a complex type
     *          with element-only content
     * @since 8.5. Changed in 9.5 to return the new type AtomicSequence
     */

    AtomicSequence atomize(NodeInfo node) throws XPathException;

    /**
     * Test whether this is the same type as another type. They are considered to be the same type
     * if they are derived from the same type definition in the original XML representation (which
     * can happen when there are multiple includes of the same file)
     *
     * @param other the other type
     * @return true if this is the same type as other
     */

    boolean isSameType(SchemaType other);

    /**
     * Get a description of this type for use in error messages. This is the same as the display name
     * in the case of named types; for anonymous types it identifies the type by its position in a source
     * schema document.
     *
     * @return text identifing the type, for use in a phrase such as "the type XXXX".
     */

    String getDescription();

    /**
     * Check that this type is validly derived from a given type, following the rules for the Schema Component
     * Constraint "Is Type Derivation OK (Simple)" (3.14.6) or "Is Type Derivation OK (Complex)" (3.4.6) as
     * appropriate.
     *
     * @param base  the base type; the algorithm tests whether derivation from this type is permitted
     * @param block the derivations that are blocked by the relevant element declaration
     * @throws SchemaException if the derivation is not allowed
     */

    void checkTypeDerivationIsOK(SchemaType base, int block) throws SchemaException;

    /**
     * Get the URI of the schema document where the type was originally defined.
     *
     * @return the URI of the schema document. Returns null if the information is unknown or if this
     *         is a built-in type
     */

    /*@Nullable*/
    String getSystemId();

    /**
     * Ask whether this type is an ID type. This is defined to be any simple type
     * who typed value may contain atomic values of type xs:ID: that is, it includes types derived
     * from ID by restriction, list, or union. Note that for a node to be treated
     * as an ID in XSD 1.0, its typed value must be a *single* atomic value of type ID; the type of the
     * node, however, can still allow a list. But in XSD 1.1, a list of IDs is permitted
     *
     * @return true if this type is an ID type
     */

    boolean isIdType() throws MissingComponentException;

    /**
     * Ask whether this type is an IDREF or IDREFS type. This is defined to be any simple type
     * who typed value may contain atomic values of type xs:IDREF: that is, it includes types derived
     * from IDREF or IDREFS by restriction, list, or union
     *
     * @return true if this type is an IDREF type
     */

    boolean isIdRefType() throws MissingComponentException;

}
