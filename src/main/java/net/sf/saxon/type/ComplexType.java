////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.type;

import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.z.IntHashSet;


/**
 * A complex type as defined in XML Schema: either a user-defined complex type, or xs:anyType, or xs:untyped.
 * In the non-schema-aware version of the Saxon product, the only complex type encountered is xs:untyped.
 */

public interface ComplexType extends SchemaType {

    int VARIETY_EMPTY = 0;
    int VARIETY_SIMPLE = 1;
    int VARIETY_ELEMENT_ONLY = 2;
    int VARIETY_MIXED = 3;

    int OPEN_CONTENT_ABSENT = 0;
    int OPEN_CONTENT_NONE = 1;
    int OPEN_CONTENT_INTERLEAVE = 2;
    int OPEN_CONTENT_SUFFIX = 3;

    /**
     * Get the variety of this complex type. This will be one of the values
     * {@link #VARIETY_EMPTY}, {@link #VARIETY_MIXED}, {@link #VARIETY_SIMPLE}, or
     * {@link #VARIETY_ELEMENT_ONLY}
     */

    int getVariety();

    /**
     * Test whether this complex type has been marked as abstract. This corresponds to
     * the {abstract} property in the schema component model.
     *
     * @return true if this complex type is abstract.
     */

    boolean isAbstract();

    /**
     * Test whether this complex type has complex content. This represents one aspect of the
     * {content type} property in the schema component model.
     *
     * @return true if and only if this complex type has a complex content model, that is, if its variety is one
     *         of empty, mixed, or element-only.
     */

    boolean isComplexContent();

    /**
     * Test whether this complexType has simple content. This represents one aspect of the
     * {content type} property in the schema component model.
     *
     * @return true if and only if this complex type has a simple content model, that is, if its variety is simple.
     */

    boolean isSimpleContent();

    /**
     * Test whether this complex type has "all" content, that is, a content model
     * using an xs:all compositor
     *
     * @return true if the type has an "all" content model
     */

    boolean isAllContent();

    /**
     * Get the simple content type. This represents one aspect of the
     * {content type} property in the schema component model.
     *
     * @return For a complex type with simple content, returns the simple type of the content.
     *         Otherwise, returns null.
     */

    /*@Nullable*/
    SimpleType getSimpleContentType() throws MissingComponentException;

    /**
     * Test whether this complex type is derived by restriction. This corresponds to one
     * aspect of the {derivation method} property in the schema component model.
     *
     * @return true if this complex type is derived by restriction
     */

    boolean isRestricted();

    /**
     * Test whether the content model of this complex type is empty. This represents one aspect of the
     * {content type} property in the schema component model.
     *
     * @return true if the content model is defined as empty
     */

    boolean isEmptyContent();

    /**
     * Test whether the content model of this complex type allows empty content. This property applies only if
     * this is a complex type with complex content.
     *
     * @return true if empty content is valid
     */

    boolean isEmptiable() throws SchemaException;

    /**
     * Test whether this complex type allows mixed content. This represents one aspect of the
     * {content type} property in the schema component model. This property applies only if
     * this is a complex type with complex content.
     *
     * @return true if mixed content is allowed
     */

    boolean isMixedContent();

    /**
     * Find an element particle within this complex type definition having a given element name
     * (identified by elementName), and return the schema type associated with that element particle.
     * If there is no such particle, return null. If the elementName matches an element wildcard,
     * return the type of the global element declaration with the given name if one exists, or AnyType
     * if none exists and lax validation is permitted by the wildcard.
     *
     * @param elementName        Identifies the name of the child element within this content model
     * @param considerExtensions True if types derived from this type by extension are to be included in the search
     * @return the schema type associated with the child element particle with the given name.
     *         If there is no such particle, return null.
     * @throws MissingComponentException if the schema is incomplete
     */

    /*@Nullable*/
    SchemaType getElementParticleType(int elementName, boolean considerExtensions) throws MissingComponentException;

    /**
     * Find an element particle within this complex type definition having a given element name
     * (identified by elementName), and return the cardinality associated with that element particle,
     * that is, the number of times the element can occur within this complex type. The value is one of
     * {@link net.sf.saxon.expr.StaticProperty#EXACTLY_ONE}, {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ZERO_OR_ONE},
     * {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ZERO_OR_MORE}, {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ONE_OR_MORE},
     * If there is no such particle, return {@link net.sf.saxon.expr.StaticProperty#EMPTY}.
     *
     * @param elementName        Identifies the name of the child element within this content model
     * @param considerExtensions True if types derived from this type by extension are to be included in the search
     * @return the cardinality associated with the child element particle with the given name.
     *         If there is no such particle, return {@link net.sf.saxon.expr.StaticProperty#EMPTY}.
     * @throws MissingComponentException if the schema is incomplete
     */

    int getElementParticleCardinality(int elementName, boolean considerExtensions) throws MissingComponentException;

    /**
     * Find an attribute use within this complex type definition having a given attribute name
     * (identified by fingerprint), and return the schema type associated with that attribute.
     * If there is no such attribute use, return null. If the fingerprint matches an attribute wildcard,
     * return the type of the global attribute declaration with the given name if one exists, or AnySimpleType
     * if none exists and lax validation is permitted by the wildcard.
     * <p>If there are types derived from this type by extension, search those too.</p>
     *
     * @param attributeName Identifies the name of the required attribute within this content model
     * @return the schema type associated with the attribute use identified by the fingerprint.
     *         If there is no such attribute use, return null.
     */

    /*@Nullable*/
    SimpleType getAttributeUseType(StructuredQName attributeName) throws SchemaException;

    /**
     * Find an attribute use within this complex type definition having a given attribute name
     * (identified by fingerprint), and return the cardinality associated with that attribute,
     * which will always be 0, 1, or 0-or-1.
     * If there is no such attribute use, return 0. If the fingerprint matches an attribute wildcard,
     * return 0-or-1.
     * <p>If there are types derived from this type by extension, search those too.</p>
     *
     * @param attributeName Identifies the name of the required attribute within this content model
     * @return the cardinality associated with the attribute use identified by the fingerprint.
     */

    int getAttributeUseCardinality(StructuredQName attributeName) throws SchemaException;

    /**
     * Return true if this type (or any known type derived from it by extension) allows the element
     * to have one or more attributes.
     *
     * @return true if attributes (other than the standard xsi: attributes) are allowed. The value
     *         false indicates that only the standard attributes in the xsi namespace are permitted.
     */

    boolean allowsAttributes() throws MissingComponentException;

    /**
     * Get a list of all the names of elements that can appear as children of an element having this
     * complex type, as integer fingerprints. If the list is unbounded (because of wildcards or the use
     * of xs:anyType), return null.
     * @param children        a set, initially empty, which on return will hold the names of all permitted
     *                        child elements; if the result contains the value -1, this indicates that it
     *                        is not possible to enumerate all the children, typically because of wildcards.
     *                        In this case the other contents of the set should be ignored
     * @param ignoreWildcards true if wildcards should be ignored
     */

    void gatherAllPermittedChildren(IntHashSet children, boolean ignoreWildcards) throws SchemaException;

    /**
     * Get a list of all the names of elements that can appear as descendants of an element having this
     * complex type, as integer fingerprints. If the list is unbounded (because of wildcards or the use
     * of xs:anyType), include a -1 in the result.
     *
     * @param descendants a set, initially empty, which on return will hold the names of all permitted
     *                    descendant elements; if the result contains the value -1, this indicates that
     *                    it is not possible to enumerate all the descendants, typically because of
     *                    wildcards. In this case the other contents of the set should
     *                    be ignored.
     */

    void gatherAllPermittedDescendants(IntHashSet descendants) throws SchemaException;

    /**
     * Assuming an element is a permitted descendant in the content model of this type, determine
     * the type of the element when it appears as a descendant. If it appears with more than one type,
     * return xs:anyType.
     *
     * @param fingerprint the name of the required descendant element
     * @return the type of the descendant element; null if the element cannot appear as a descendant;
     *         anyType if it can appear with several different types
     */

    /*@Nullable*/
    SchemaType getDescendantElementType(int fingerprint) throws SchemaException;

    /**
     * Assuming an element is a permitted descendant in the content model of this type, determine
     * the cardinality of the element when it appears as a descendant.
     *
     * @param elementFingerprint the name of the required descendant element
     * @return the cardinality of the descendant element within this complex type
     */

    int getDescendantElementCardinality(int elementFingerprint) throws SchemaException;

    /**
     * Ask whether this type (or any known type derived from it by extension) allows the element
     * to have children that match a wildcard
     *
     * @return true if the content model of this type, or its extensions, contains an element wildcard
     */

    boolean containsElementWildcard() throws MissingComponentException;

    /**
     * Ask whether there are any assertions defined on this complex type
     *
     * @return true if there are any assertions
     */

    boolean hasAssertions();
}

