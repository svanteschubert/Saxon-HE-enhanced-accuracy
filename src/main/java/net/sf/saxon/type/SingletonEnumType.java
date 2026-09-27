////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

//import com.saxonica.ee.schema.UserSimpleType;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.*;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.coercion.CoercionPlan;
import net.sf.saxon.type.coercion.SingletonEnumerationCoercionPlan;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Represents the type enum("X") - an enumeration type with a single permitted xs:string value.
 * The significance of this is that all enumeration types are regarded as unions of singleton
 * enumeration types; and an individual xs:string instance can be annotated with a singleton
 * enum type, but not with a union type.
 */

public class SingletonEnumType implements AtomicType {

    private final UnicodeString value;
    private final StringValue valueAsItem;

    /**
     * Create an enumeration type permitting a defined value
     * @param value the value to be permitted
     */
    public SingletonEnumType(UnicodeString value) {
        this.value = value;
        this.valueAsItem = new StringValue(value, this);
    }

    public UnicodeString getValue() {
        return value;
    }

    public StringValue getInstance() {
        return valueAsItem;
    }

    public boolean allowsValue(UnicodeString value) {
        return this.value.equals(value);
    }

    public static StringValue of(UnicodeString str) {
        return new StringValue(str, new SingletonEnumType(str));
    }

    /**
     * Validate that a primitive atomic value is a valid instance of a type derived from the
     * same primitive type.
     *
     * @param primValue    the value in the value space of the primitive type.
     * @param lexicalValue the value in the lexical space. If null, the string value of primValue
     *                     is used. This value is checked against the pattern facet (if any)
     * @param rules        the conversion rules for this configuration
     * @return null if the value is valid; otherwise, a ValidationFailure object indicating
     * the nature of the error.
     * @throws UnsupportedOperationException in the case of an external object type
     */
    @Override
    public ValidationFailure validate(AtomicValue primValue, UnicodeString lexicalValue, ConversionRules rules) {
        if (primValue.getPrimitiveType() == BuiltInAtomicType.STRING
                && allowsValue(primValue.getUnicodeStringValue())) {
            return null;
        } else {
            return new ValidationFailure("The string '" + primValue.getStringValue() +
                                                 " is not valid for the enumeration type");
        }
    }

    /**
     * Determine whether the atomic type is ordered, that is, whether less-than and greater-than comparisons
     * are permitted
     *
     * @param optimistic if true, the function takes an optimistic view, returning true if ordering comparisons
     *                   are available for some subtype. This mainly affects xs:duration, where the function returns true if
     *                   optimistic is true, false if it is false.
     * @return true if ordering operations are permitted
     */
    @Override
    public boolean isOrdered(boolean optimistic) {
        return true;
    }

    /**
     * Determine whether the type is abstract, that is, whether it cannot have instances that are not also
     * instances of some concrete subtype
     *
     * @return true if the type is abstract
     */
    @Override
    public boolean isAbstract() {
        return false;
    }

    /**
     * Determine whether the atomic type is a primitive type.  The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration;
     * xs:untypedAtomic; and all supertypes of these (xs:anyAtomicType, xs:numeric, ...)
     *
     * @return true if the type is considered primitive under the above rules
     */
    @Override
    public boolean isPrimitiveType() {
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
     * Determine whether the atomic type is a built-in type. The built-in atomic types are the 41 atomic types
     * defined in XML Schema, plus xs:dayTimeDuration and xs:yearMonthDuration,
     * xs:untypedAtomic, and all supertypes of these (xs:anyAtomicType, xs:numeric, ...)
     */
    @Override
    public boolean isBuiltInType() {
        return false;
    }

    /**
     * Get the name of this type as a StructuredQName, unless the type is anonymous, in which case
     * return null
     *
     * @return the name of the atomic type, or null if the type is anonymous.
     */
    @Override
    public StructuredQName getTypeName() {
        return new StructuredQName("", NamespaceUri.ANONYMOUS, "E" + hashCode());
    }

    @Override
    public double getDefaultPriority() {
        return 0;
    }

    /**
     * Get a StringConverter, an object which converts strings in the lexical space of this
     * data type to instances (in the value space) of the data type.
     *
     * @param rules the conversion rules to be used
     * @return a StringConverter to do the conversion, or null if no built-in converter is available.
     */
    @Override
    public StringConverter getStringConverter(ConversionRules rules) {
        return null;  // TODO
    }

    /**
     * Get the list of plain types that are subsumed by this type
     *
     * @return for an atomic type, the type itself; for a plain union type, the list of plain types
     * in its transitive membership
     */
    @Override
    public List<? extends PlainType> getPlainMemberTypes() {
        return Collections.singletonList((PlainType)this);
    }

    /**
     * Test whether a given item conforms to this type
     *
     * @param item The item to be tested
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item) {
        return item instanceof AtomicValue &&
                ((AtomicValue) item).getItemType().equals(this);
    }

    public boolean matchesString(UnicodeString str) {
        return str.equals(getValue());
    }

    /**
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
    public AtomicType getPrimitiveItemType() {
        return BuiltInAtomicType.STRING;
    }

    /**
     * Determine whether this item type is a plain type (that is, whether it can ONLY match
     * atomic values)
     *
     * @return true if this is ANY_ATOMIC_TYPE or a subtype thereof, or a
     * "plain" union type (that is, unions of atomic types that impose no further restrictions).
     * Return false if this is a union type whose member types are not all known.
     */
    @Override
    public boolean isPlainType() {
        return true;
    }

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
    @Override
    public int getPrimitiveType() {
        return StandardNames.XS_STRING;
    }

    /**
     * Get the corresponding {@link UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        return UType.STRING;
    }

    /**
     * Get the item type of the atomic values that will be produced when an item
     * of this type is atomized
     *
     * @return the best available item type of the atomic values that will be produced when an item
     * of this type is atomized, or null if it is known that atomization will throw an error.
     */
    @Override
    public PlainType getAtomizedItemType() {
        return BuiltInAtomicType.STRING;
    }

    /**
     * Ask whether values of this type are atomizable
     *
     * @param th the type hierarchy cache
     * @return true if some or all instances of this type can be successfully atomized; false
     * if no instances of this type can be atomized
     */
    @Override
    public boolean isAtomizable(TypeHierarchy th) {
        return true;
    }

    /**
     * Get an alphabetic code representing the type, or at any rate, the nearest built-in type
     * from which this type is derived. The codes are designed so that for any two built-in types
     * A and B, alphaCode(A) is a prefix of alphaCode(B) if and only if A is a supertype of B.
     *
     * @return the alphacode for the nearest containing built-in type. For example: for xs:string
     * return "AS", for xs:boolean "AB", for node() "N", for element() "NE", for map(*) "FM", for
     * array(*) "FA".
     */
    @Override
    public String getBasicAlphaCode() {
        return "E"; // TODO: define alphacodes for enumeration types
    }

    /**
     * Test whether this Simple Type is an atomic type
     *
     * @return true if this is an atomic type
     */
    @Override
    public boolean isAtomicType() {
        return true;
    }

    /**
     * Test whether this Simple Type is a list type
     *
     * @return true if this is a list type
     */
    @Override
    public boolean isListType() {
        return false;
    }

    /**
     * Test whether this Simple Type is a union type
     *
     * @return true if this is a union type
     */
    @Override
    public boolean isUnionType() {
        return false;
    }

    /**
     * Get the built-in type from which this type is derived by restriction
     *
     * @return the built-in type from which this type is derived by restriction. This will not necessarily
     * be a primitive type.
     */
    @Override
    public SchemaType getBuiltInBaseType() {
        return BuiltInAtomicType.STRING;
    }

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
     * returned by this SequenceIterator will all be of type {@link AtomicValue},
     * @throws ValidationException if the supplied value is not in the lexical space of the data type
     */
    @Override
    public AtomicSequence getTypedValue(UnicodeString value, NamespaceResolver resolver, ConversionRules rules) throws ValidationException {
        return new StringValue(value, this);
    }

    /**
     * Check whether a given input string is valid according to this SimpleType
     *
     * @param value      the input string to be checked
     * @param nsResolver a namespace resolver used to resolve namespace prefixes if the type
     *                   is namespace sensitive. The value supplied may be null; in this case any namespace-sensitive
     *                   content will throw an UnsupportedOperationException.
     * @param rules      the conversion rules from the configuration
     * @return null if validation succeeds; or return a ValidationFailure describing the validation failure
     * if validation fails. Note that the exception is returned rather than being thrown.
     * @throws UnsupportedOperationException if the type is namespace-sensitive and no namespace
     *                                       resolver is supplied
     */
    @Override
    public ValidationFailure validateContent(UnicodeString value, NamespaceResolver nsResolver, ConversionRules rules) {
        if (allowsValue(value)) {
            return null;
        } else {
            return new ValidationFailure("The string '" + value +
                                                 " is not valid for the enumeration type");
        }
    }

    /**
     * Test whether this type is namespace sensitive, that is, if a namespace context is needed
     * to translate between the lexical space and the value space. This is true for types derived
     * from, or containing, QNames and NOTATIONs
     *
     * @return true if the type is namespace-sensitive, or if the namespace-sensitivity cannot be determined
     * because there are missing schema components. (However, for xs:anyAtomicType, the result returned is
     * false, even though the type allows xs:QName instances.)
     */
    @Override
    public boolean isNamespaceSensitive() {
        return false;
    }

    /**
     * Determine how values of this simple type are whitespace-normalized.
     *
     * @return one of {@link Whitespace#PRESERVE}, {@link Whitespace#COLLAPSE},
     * {@link Whitespace#REPLACE}.
     */
    @Override
    public int getWhitespaceAction() {
        return 0;
    }

    /**
     * Apply any pre-lexical facets, other than whitespace. At the moment the only such
     * facet is saxon:preprocess
     *
     * @param input the value to be preprocessed
     * @return the value after preprocessing
     * @throws ValidationException if preprocessing detects that the value is invalid
     */
    @Override
    public UnicodeString preprocess(UnicodeString input) throws ValidationException {
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
     * @throws ValidationException if postprocessing detects that the value is invalid
     */
    @Override
    public UnicodeString postprocess(UnicodeString input) throws ValidationException {
        return input;
    }

    /**
     * Get the local name of this type
     *
     * @return the local name of this type definition, if it has one. Return null in the case of an
     * anonymous type.
     */
    @Override
    public String getName() {
        return null;
    }

    /**
     * Get the target namespace of this type
     *
     * @return the target namespace of this type definition, if it has one. Return null in the case
     * of an anonymous type, and in the case of a global type defined in a no-namespace schema.
     */
    @Override
    public NamespaceUri getTargetNamespace() {
        return null;
    }

    /**
     * Get the fingerprint of the name of this type
     *
     * @return the fingerprint. Returns an invented fingerprint for an anonymous type.
     */
    @Override
    public int getFingerprint() {
        return -1;
    }

    /**
     * Get the display name of the type: that is, a lexical QName with an arbitrary prefix
     *
     * @return a lexical QName identifying the type. In the case of an anonymous type, an internally-generated
     * name is returned
     */
    @Override
    public String getDisplayName() {
        return getTypeName().getDisplayName();
    }

    /**
     * Get the name of the type as a StructuredQName
     *
     * @return a StructuredQName identifying the type.  In the case of an anonymous type, an internally-generated
     * name is returned
     */
    @Override
    public StructuredQName getStructuredQName() {
        return null;
    }

    /**
     * Get the name of this type as an EQName, that is, a string in the format Q{uri}local.
     *
     * @return an EQName identifying the type. In the case of an anonymous type, an internally-generated
     * name is returned
     */
    @Override
    public String getEQName() {
        return null;
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
     * Test whether this is an anonymous type
     *
     * @return true if this SchemaType is an anonymous type
     */
    @Override
    public boolean isAnonymousType() {
        return true;
    }

    /**
     * Returns the value of the 'block' attribute for this type, as a bit-significant
     * integer with fields such as {@link Derivation#DERIVATION_LIST} and {@link Derivation#DERIVATION_EXTENSION}.
     * This corresponds to the property "prohibited substitutions" in the schema component model.
     *
     * @return the value of the 'block' attribute for this type
     */
    @Override
    public int getBlock() {
        return 0;
    }

    /**
     * Returns the base type that this type inherits from. This method can be used to get the
     * base type of a type that is known to be valid.
     * If this type is a Simpletype that is a built in primitive type then null is returned.
     *
     * @return the base type, or null if this is xs:anyType (the root of the type hierarchy)
     */
    @Override
    public SchemaType getBaseType() {
        return BuiltInAtomicType.STRING;
    }

    /**
     * Gets the integer code of the derivation method used to derive this type from its
     * parent. Returns zero for primitive types.
     *
     * @return a numeric code representing the derivation method, for example {@link Derivation#DERIVATION_RESTRICTION}
     */
    @Override
    public int getDerivationMethod() {
        return Derivation.DERIVATION_RESTRICTION;
    }

    /**
     * Get the types of derivation that are not permitted, by virtue of the "final" property.
     *
     * @return the types of derivation that are not permitted, as a bit-significant integer
     * containing bits such as {@link Derivation#DERIVATION_EXTENSION}
     */
    @Override
    public int getFinalProhibitions() {
        return 0;
    }

    /**
     * Determines whether derivation (of a particular kind)
     * from this type is allowed, based on the "final" property
     *
     * @param derivation the kind of derivation, for example {@link Derivation#DERIVATION_LIST}
     * @return true if this kind of derivation is allowed
     */
    @Override
    public boolean allowsDerivation(int derivation) {
        return true;
    }

    /**
     * Analyze an XPath expression to see whether the expression is capable of delivering a value of this
     * type. This method is called during static analysis of a query or stylesheet to give compile-time
     * warnings when "impossible" paths are used.
     *
     * @param expression the expression that delivers the content
     * @param kind       the node kind whose content is being delivered: {@link Type#ELEMENT},
     *                   {@link Type#ATTRIBUTE}, or {@link Type#DOCUMENT}
     * @param schema
     * @throws XPathException if the expression will never deliver a value of the correct type
     */
    @Override
    public void analyzeContentExpression(Expression expression, int kind, Schema schema) throws XPathException {
        BuiltInAtomicType.STRING.analyzeContentExpression(expression, kind, schema);
    }

    /**
     * Get the typed value of a node that is annotated with this schema type.
     *
     * @param node the node whose typed value is required
     * @return the typed value.
     * @throws XPathException if the node cannot be atomized, for example if this is a complex type
     *                        with element-only content
     * @since 8.5. Changed in 9.5 to return the new type AtomicSequence
     */
    @Override
    public AtomicSequence atomize(NodeInfo node) throws XPathException {
        return null;
    }

    /**
     * Test whether this is the same type as another type. They are considered to be the same type
     * if they are derived from the same type definition in the original XML representation (which
     * can happen when there are multiple includes of the same file)
     *
     * @param other the other type
     * @return true if this is the same type as other
     */
    @Override
    public boolean isSameType(SchemaType other) {
        return other instanceof SingletonEnumType && value.equals(((SingletonEnumType)other).value);
    }

    /**
     * Get a description of this type for use in error messages. This is the same as the display name
     * in the case of named types; for anonymous types it identifies the type by its position in a source
     * schema document.
     *
     * @return text identifing the type, for use in a phrase such as "the type XXXX".
     */
    @Override
    public String getDescription() {
        return toString();
    }

    /**
     * Produce a string representation of the type name. If the type is anonymous, an internally-allocated
     * type name will be returned.
     *
     * @return the name of the atomic type in the form Q{uri}local
     */

    public String toString() {
        return "enum(\"" + value + "\")";
    }

    /**
     * Check that this type is validly derived from a given type, following the rules for the Schema Component
     * Constraint "Is Type Derivation OK (Simple)" (3.14.6) or "Is Type Derivation OK (Complex)" (3.4.6) as
     * appropriate.
     *
     * @param base  the base type; the algorithm tests whether derivation from this type is permitted
     * @param block the derivations that are blocked by the relevant element declaration
     * @throws SchemaException if the derivation is not allowed
     */
    @Override
    public void checkTypeDerivationIsOK(SchemaType base, int block) throws SchemaException {

    }

    /**
     * Get the URI of the schema document where the type was originally defined.
     *
     * @return the URI of the schema document. Returns null if the information is unknown or if this
     * is a built-in type
     */
    @Override
    public String getSystemId() {
        return null;
    }

    /**
     * Get the validation status of this component.
     *
     * @return one of the values {@link SchemaValidationStatus#UNVALIDATED}, {@link SchemaValidationStatus#VALIDATING},
     * {@link SchemaValidationStatus#VALIDATED}, {@link SchemaValidationStatus#INVALID}, {@link SchemaValidationStatus#INCOMPLETE}
     */
    @Override
    public SchemaValidationStatus getValidationStatus() {
        return SchemaValidationStatus.VALIDATED;
    }

    /**
     * Get the coercion plan for use when this type is the required type for (say) coercion
     * of arguments in a function call
     *
     * @param version the XPath language version (40 or 31)
     */
    @Override
    public CoercionPlan getCoercionPlan(int version) {
        return SingletonEnumerationCoercionPlan.INSTANCE;
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

    @Override
    public AtomicType getType() {
        return this;
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     * @param obj the reference object with which to compare.
     * @return {@code true} if this object is the same as the obj
     * argument; {@code false} otherwise.
     * @see #hashCode()
     * @see HashMap
     */
    @Override
    public boolean equals(Object obj) {
        return obj instanceof SingletonEnumType && this.value.equals(((SingletonEnumType)obj).value);
    }

    @Override
    public int hashCode()  {
        return value.hashCode();
    }

}

