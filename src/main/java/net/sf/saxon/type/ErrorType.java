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
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.UntypedAtomicValue;
import net.sf.saxon.value.Whitespace;

import java.util.Collections;
import java.util.Optional;

import static net.sf.saxon.type.SchemaComponent.ValidationStatus.VALIDATED;


/**
 * This class has a singleton instance which represents the XML Schema 1.1 built-in type xs:error.
 */

public final class ErrorType extends NodeTest implements AtomicType, UnionType, PlainType {

    /*@NotNull*/ private static ErrorType theInstance = new ErrorType();

    /**
     * Private constructor
     */
    private ErrorType() {
    }

    /**
     * Get the corresponding {@link net.sf.saxon.type.UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        return UType.VOID;
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
        return "error";
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
        return "Q{" + NamespaceConstant.SCHEMA + "}error";
    }



    @Override
    public boolean containsListType() {
        return false;
    }

    @Override
    public Iterable<PlainType> getPlainMemberTypes() {
        return Collections.emptySet();
    }

    /**
     * Determine whether this is a built-in type or a user-defined type
     */

    @Override
    public boolean isBuiltInType() {
        return true;
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
    public static ErrorType getInstance() {
        return theInstance;
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

    /*@NotNull*/
    @Override
    public SchemaType getBaseType() {
        return AnySimpleType.getInstance();
    }

    /**
     * Returns the base type that this type inherits from. This method can be used to get the
     * base type of a type that is known to be valid.
     *
     * @return the base type.
     */

    /*@NotNull*/
    public SchemaType getKnownBaseType() {
        return getBaseType();
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
        return StandardNames.XS_ERROR;
    }

    /**
     * Get the name of the nodes matched by this nodetest, if it matches a specific name.
     * Return null if the node test matches nodes of more than one name
     */
    @Override
    public StructuredQName getMatchingNodeName() {
        return StandardNames.getStructuredQName(StandardNames.XS_ERROR);
    }

    /**
     * Get the name of the type as a StructuredQName
     *
     * @return a StructuredQName identifying the type.  In the case of an anonymous type, an internally-generated
     * name is returned
     */
    @Override
    public StructuredQName getTypeName() {
        return new StructuredQName("xs", NamespaceConstant.SCHEMA, "error");
    }

    /**
     * Get a description of this type for use in diagnostics
     *
     * @return the string "xs:anyType"
     */

    /*@NotNull*/
    @Override
    public String getDescription() {
        return "xs:error";
    }

    /**
     * Get the display name of the type: that is, a lexical QName with an arbitrary prefix
     *
     * @return a lexical QName identifying the type
     */

    /*@NotNull*/
    @Override
    public String getDisplayName() {
        return "xs:error";
    }

    /**
     * Test whether this is the same type as another type. They are considered to be the same type
     * if they are derived from the same type definition in the original XML representation (which
     * can happen when there are multiple includes of the same file)
     */

    @Override
    public boolean isSameType(SchemaType other) {
        return other instanceof ErrorType;
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
     * @throws net.sf.saxon.type.SchemaException
     *          if the derivation is not allowed
     */

    @Override
    public void checkTypeDerivationIsOK(/*@NotNull*/ SchemaType type, int block) throws SchemaException {
        if (type == this || type == AnySimpleType.getInstance()) {
            return;
        }
        throw new SchemaException("Type xs:error is not validly derived from " + type.getDescription());
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
     * @return true (this is a union type with no members)
     */
    @Override
    public boolean isUnionType() {
        return true;
    }

    /**
     * Get the built-in ancestor of this type in the type hierarchy
     *
     * @return xs:anySimpleType
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
     * @param rules    the conversion rules
     * @return an iterator over the atomic sequence comprising the typed value. The objects
     *         returned by this SequenceIterator will all be of type {@link net.sf.saxon.value.AtomicValue}
     * @throws ValidationException if the supplied value is not in the lexical space of the data type (which is
     *                             always true for this type)
     */

    /*@NotNull*/
    @Override
    public AtomicSequence getTypedValue(CharSequence value, NamespaceResolver resolver, ConversionRules rules) throws ValidationException {
        throw new ValidationFailure("Cast to xs:error always fails").makeException();
    }

    /**
     * Get a StringConverter, an object which converts strings in the lexical space of this
     * data type to instances (in the value space) of the data type.
     *
     * @return a StringConverter to do the conversion, or null if no built-in converter is available.
     */
    @Override
    public StringConverter getStringConverter(ConversionRules rules) {
        return null;
    }

    /**
     * Check whether a given input string is valid according to this SimpleType
     *
     * @param value      the input string to be checked
     * @param nsResolver a namespace resolver used to resolve namespace prefixes if the type
     *                   is namespace sensitive. The value supplied may be null; in this case any namespace-sensitive
     *                   content will throw an UnsupportedOperationException.
     * @param rules      the conversion rules
     * @return null if validation succeeds (which it never does for this implementation)
     * @throws UnsupportedOperationException if the type is namespace-sensitive and no namespace
     *                                       resolver is supplied
     */
    /*@NotNull*/
    @Override
    public ValidationFailure validateContent(/*@NotNull*/ CharSequence value, NamespaceResolver nsResolver, /*@NotNull*/ ConversionRules rules) {
        return new ValidationFailure("No content is ever valid against the type xs:error");
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
     * integer with fields such as {@link net.sf.saxon.type.SchemaType#DERIVATION_LIST} and {@link net.sf.saxon.type.SchemaType#DERIVATION_EXTENSION}
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
     * @return a numeric code representing the derivation method, for example {@link net.sf.saxon.type.SchemaType#DERIVATION_RESTRICTION}
     */

    @Override
    public int getDerivationMethod() {
        return SchemaType.DERIVATION_RESTRICTION;
    }

    /**
     * Determines whether derivation (of a particular kind)
     * from this type is allowed, based on the "final" property
     *
     * @param derivation the kind of derivation, for example {@link net.sf.saxon.type.SchemaType#DERIVATION_LIST}
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
        return SchemaType.DERIVATION_EXTENSION | SchemaType.DERIVATION_RESTRICTION | SchemaType.DERIVATION_LIST |
                SchemaType.DERIVATION_UNION;
    }

    /**
     * Determine how values of this simple type are whitespace-normalized.
     *
     * @return one of {@link net.sf.saxon.value.Whitespace#PRESERVE}, {@link net.sf.saxon.value.Whitespace#COLLAPSE},
     *         {@link net.sf.saxon.value.Whitespace#REPLACE}.
     */

    @Override
    public int getWhitespaceAction() {
        return Whitespace.COLLAPSE;
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
    public void analyzeContentExpression(Expression expression, int kind) throws XPathException {
        throw new XPathException("No expression can ever return a value of type xs:error");
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
    public CharSequence postprocess(CharSequence input) {
        return input;
    }

    @Override
    public boolean isPlainType() {
        return true;
    }

    @Override
    public boolean matches(Item item, TypeHierarchy th) {
        return false;
    }

    @Override
    public boolean matches(int nodeKind, NodeName name, SchemaType annotation) {
        return false;
    }

    @Override
    public AtomicType getPrimitiveItemType() {
        return this;
    }

    @Override
    public int getPrimitiveType() {
        return Type.ITEM;
    }

    @Override
    public double getDefaultPriority() {
        return -1000;
    }

    @Override
    public AtomicType getAtomizedItemType() {
        return BuiltInAtomicType.UNTYPED_ATOMIC;
    }

    @Override
    public boolean isAtomizable(TypeHierarchy th) {
        return false;
    }

    @Override
    public SequenceType getResultTypeOfCast() {
        // The return type is chosen so that use of the error() function will never give a static type error,
        // on the basis that item()? overlaps every other type, and it's almost impossible to make any
        // unwarranted inferences from it, except perhaps count(error()) lt 2.
        return SequenceType.OPTIONAL_ITEM;
    }

    /**
     * Return a string representation of this ItemType suitable for use in stylesheet
     * export files. This differs from the result of toString() in that it will not contain
     * any references to anonymous types. Note that it may also use the Saxon extended syntax
     * for union types and tuple types. The default implementation returns the result of
     * calling {@code toString()}.
     *
     * @return the string representation as an instance of the XPath SequenceType construct
     */
    @Override
    public String toExportString() {
        return toString();
    }

    public String toString() {
        return "xs:error";
    }

    /**
     * Validate that a primitive atomic value is a valid instance of a type derived from the
     * same primitive type.
     *
     * @param primValue    the value in the value space of the primitive type.
     * @param lexicalValue the value in the lexical space. If null, the string value of primValue
     *                     is used. This value is checked against the pattern facet (if any)
     * @param rules        the conversion rules
     * @return null if the value is valid; otherwise, a ValidationFailure object indicating
     *         the nature of the error.
     * @throws UnsupportedOperationException in the case of an external object type
     */
    @Override
    public ValidationFailure validate(AtomicValue primValue, CharSequence lexicalValue, ConversionRules rules) {
        return new ValidationFailure("No value is valid against type xs:error");
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
        return false;
    }

    /**
     * Determine whether the type is abstract, that is, whether it cannot have instances that are not also
     * instances of some concrete subtype
     */
    @Override
    public boolean isAbstract() {
        return true;
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
     * Get the name of this type as a StructuredQName, unless the type is anonymous, in which case
     * return null
     *
     * @return the name of the atomic type, or null if the type is anonymous.
     */
    @Override
    public StructuredQName getStructuredQName() {
        return new StructuredQName("xs", NamespaceConstant.SCHEMA, "error");
    }

    /**
     * Validate an atomic value, which is known to be an instance of one of the member types of the
     * union, against any facets (pattern facets or enumeration facets) defined at the level of the
     * union itself.
     *
     * @param value the Atomic Value to be checked. This must be an instance of a member type of the
     *              union
     * @param rules the ConversionRules for the Configuration
     * @return a ValidationFailure if the value is not valid; null if it is valid.
     */
    @Override
    public ValidationFailure checkAgainstFacets(AtomicValue value, ConversionRules rules) {
        return null;
    }

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
    public Optional<String> explainMismatch(Item item, TypeHierarchy th) {
        return Optional.of("Evaluation of the supplied expression will always fail");
    }


}

