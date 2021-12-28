////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.instruct.ValueOf;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static net.sf.saxon.type.SchemaComponent.ValidationStatus.VALIDATED;

/**
 * This class represents a built-in atomic type, which may be either a primitive type
 * (such as xs:decimal or xs:anyURI) or a derived type (such as xs:ID or xs:dayTimeDuration).
 */

public class BuiltInAtomicType implements AtomicType, ItemType.WithSequenceTypeCache {

    private int fingerprint;
    private int baseFingerprint;
    private int primitiveFingerprint;
    private UType uType;
    private String alphaCode;
    private boolean ordered = false;
    public StringConverter stringConverter; // may be null for types where conversion rules can vary
    private SequenceType _one;
    private SequenceType _oneOrMore;
    private SequenceType _zeroOrOne;
    private SequenceType _zeroOrMore;

    private static Map<String, BuiltInAtomicType> byAlphaCode = new HashMap<>(60);

    public final static BuiltInAtomicType ANY_ATOMIC =
            makeAtomicType(StandardNames.XS_ANY_ATOMIC_TYPE, AnySimpleType.getInstance(), "A", true);

    public final static BuiltInAtomicType STRING =
            makeAtomicType(StandardNames.XS_STRING, ANY_ATOMIC, "AS", true);

    public final static BuiltInAtomicType BOOLEAN =
            makeAtomicType(StandardNames.XS_BOOLEAN, ANY_ATOMIC, "AB", true);

    public final static BuiltInAtomicType DURATION =
            makeAtomicType(StandardNames.XS_DURATION, ANY_ATOMIC, "AR", false);

    public final static BuiltInAtomicType DATE_TIME =
            makeAtomicType(StandardNames.XS_DATE_TIME, ANY_ATOMIC, "AM", true);

    public final static BuiltInAtomicType DATE =
            makeAtomicType(StandardNames.XS_DATE, ANY_ATOMIC, "AA", true);

    public final static BuiltInAtomicType TIME =
            makeAtomicType(StandardNames.XS_TIME, ANY_ATOMIC, "AT", true);

    public final static BuiltInAtomicType G_YEAR_MONTH =
            makeAtomicType(StandardNames.XS_G_YEAR_MONTH, ANY_ATOMIC, "AH", false);

    public final static BuiltInAtomicType G_MONTH =
            makeAtomicType(StandardNames.XS_G_MONTH, ANY_ATOMIC, "AI", false);

    public final static BuiltInAtomicType G_MONTH_DAY =
            makeAtomicType(StandardNames.XS_G_MONTH_DAY, ANY_ATOMIC, "AJ", false);

    public final static BuiltInAtomicType G_YEAR =
            makeAtomicType(StandardNames.XS_G_YEAR, ANY_ATOMIC, "AG", false);

    public final static BuiltInAtomicType G_DAY =
            makeAtomicType(StandardNames.XS_G_DAY, ANY_ATOMIC, "AK", false);

    public final static BuiltInAtomicType HEX_BINARY =
            makeAtomicType(StandardNames.XS_HEX_BINARY, ANY_ATOMIC, "AX", true);

    public final static BuiltInAtomicType BASE64_BINARY =
            makeAtomicType(StandardNames.XS_BASE64_BINARY, ANY_ATOMIC, "A2", true);

    public final static BuiltInAtomicType ANY_URI =
            makeAtomicType(StandardNames.XS_ANY_URI, ANY_ATOMIC, "AU", true);

    public final static BuiltInAtomicType QNAME =
            makeAtomicType(StandardNames.XS_QNAME, ANY_ATOMIC, "AQ", false);

    public final static BuiltInAtomicType NOTATION =
            makeAtomicType(StandardNames.XS_NOTATION, ANY_ATOMIC, "AN", false);

    public final static BuiltInAtomicType UNTYPED_ATOMIC =
            makeAtomicType(StandardNames.XS_UNTYPED_ATOMIC, ANY_ATOMIC, "AZ", true);

    public final static BuiltInAtomicType DECIMAL =
            makeAtomicType(StandardNames.XS_DECIMAL, ANY_ATOMIC, "AD", true);

    public final static BuiltInAtomicType FLOAT =
            makeAtomicType(StandardNames.XS_FLOAT, ANY_ATOMIC, "AF", true);

    public final static BuiltInAtomicType DOUBLE =
            makeAtomicType(StandardNames.XS_DOUBLE, ANY_ATOMIC, "AO", true);

    public final static BuiltInAtomicType INTEGER =
            makeAtomicType(StandardNames.XS_INTEGER, DECIMAL, "ADI", true);

    public final static BuiltInAtomicType NON_POSITIVE_INTEGER =
            makeAtomicType(StandardNames.XS_NON_POSITIVE_INTEGER, INTEGER, "ADIN", true);

    public final static BuiltInAtomicType NEGATIVE_INTEGER =
            makeAtomicType(StandardNames.XS_NEGATIVE_INTEGER, NON_POSITIVE_INTEGER, "ADINN", true);

    public final static BuiltInAtomicType LONG =
            makeAtomicType(StandardNames.XS_LONG, INTEGER, "ADIL", true);

    public final static BuiltInAtomicType INT =
            makeAtomicType(StandardNames.XS_INT, LONG, "ADILI", true);

    public final static BuiltInAtomicType SHORT =
            makeAtomicType(StandardNames.XS_SHORT, INT, "ADILIS", true);

    public final static BuiltInAtomicType BYTE =
            makeAtomicType(StandardNames.XS_BYTE, SHORT, "ADILISB", true);

    public final static BuiltInAtomicType NON_NEGATIVE_INTEGER =
            makeAtomicType(StandardNames.XS_NON_NEGATIVE_INTEGER, INTEGER, "ADIP", true);

    public final static BuiltInAtomicType POSITIVE_INTEGER =
            makeAtomicType(StandardNames.XS_POSITIVE_INTEGER, NON_NEGATIVE_INTEGER, "ADIPP", true);

    public final static BuiltInAtomicType UNSIGNED_LONG =
            makeAtomicType(StandardNames.XS_UNSIGNED_LONG, NON_NEGATIVE_INTEGER, "ADIPL", true);

    public final static BuiltInAtomicType UNSIGNED_INT =
            makeAtomicType(StandardNames.XS_UNSIGNED_INT, UNSIGNED_LONG, "ADIPLI", true);

    public final static BuiltInAtomicType UNSIGNED_SHORT =
            makeAtomicType(StandardNames.XS_UNSIGNED_SHORT, UNSIGNED_INT, "ADIPLIS", true);

    public final static BuiltInAtomicType UNSIGNED_BYTE =
            makeAtomicType(StandardNames.XS_UNSIGNED_BYTE, UNSIGNED_SHORT, "ADIPLISB", true);

    public final static BuiltInAtomicType YEAR_MONTH_DURATION =
            makeAtomicType(StandardNames.XS_YEAR_MONTH_DURATION, DURATION, "ARY", true);

    public final static BuiltInAtomicType DAY_TIME_DURATION =
            makeAtomicType(StandardNames.XS_DAY_TIME_DURATION, DURATION, "ARD", true);

    public final static BuiltInAtomicType NORMALIZED_STRING =
            makeAtomicType(StandardNames.XS_NORMALIZED_STRING, STRING, "ASN", true);

    public final static BuiltInAtomicType TOKEN =
            makeAtomicType(StandardNames.XS_TOKEN, NORMALIZED_STRING, "ASNT", true);

    public final static BuiltInAtomicType LANGUAGE =
            makeAtomicType(StandardNames.XS_LANGUAGE, TOKEN, "ASNTL", true);

    public final static BuiltInAtomicType NAME =
            makeAtomicType(StandardNames.XS_NAME, TOKEN, "ASNTN", true);

    public final static BuiltInAtomicType NMTOKEN =
            makeAtomicType(StandardNames.XS_NMTOKEN, TOKEN, "ASNTK", true);

    public final static BuiltInAtomicType NCNAME =
            makeAtomicType(StandardNames.XS_NCNAME, NAME, "ASNTNC", true);

    public final static BuiltInAtomicType ID =
            makeAtomicType(StandardNames.XS_ID, NCNAME, "ASNTNCI", true);

    public final static BuiltInAtomicType IDREF =
            makeAtomicType(StandardNames.XS_IDREF, NCNAME, "ASNTNCR", true);

    public final static BuiltInAtomicType ENTITY =
            makeAtomicType(StandardNames.XS_ENTITY, NCNAME, "ASNTNCE", true);

    public final static BuiltInAtomicType DATE_TIME_STAMP =
            makeAtomicType(StandardNames.XS_DATE_TIME_STAMP, DATE_TIME, "AMP", true);

    static {
        // See bug 2524
        ANY_ATOMIC          .stringConverter = StringConverter.StringToString.INSTANCE;
        STRING              .stringConverter = StringConverter.StringToString.INSTANCE;
        LANGUAGE            .stringConverter = StringConverter.StringToLanguage.INSTANCE;
        NORMALIZED_STRING   .stringConverter = StringConverter.StringToNormalizedString.INSTANCE;
        TOKEN               .stringConverter = StringConverter.StringToToken.INSTANCE;
        NCNAME              .stringConverter = StringConverter.StringToNCName.TO_NCNAME;
        NAME                .stringConverter = StringConverter.StringToName.INSTANCE;
        NMTOKEN             .stringConverter = StringConverter.StringToNMTOKEN.INSTANCE;
        ID                  .stringConverter = StringConverter.StringToNCName.TO_ID;
        IDREF               .stringConverter = StringConverter.StringToNCName.TO_IDREF;
        ENTITY              .stringConverter = StringConverter.StringToNCName.TO_ENTITY;
        DECIMAL             .stringConverter = StringConverter.StringToDecimal.INSTANCE;
        INTEGER             .stringConverter = StringConverter.StringToInteger.INSTANCE;
        DURATION            .stringConverter = StringConverter.StringToDuration.INSTANCE;
        G_MONTH             .stringConverter = StringConverter.StringToGMonth.INSTANCE;
        G_MONTH_DAY         .stringConverter = StringConverter.StringToGMonthDay.INSTANCE;
        G_DAY               .stringConverter = StringConverter.StringToGDay.INSTANCE;
        DAY_TIME_DURATION   .stringConverter = StringConverter.StringToDayTimeDuration.INSTANCE;
        YEAR_MONTH_DURATION .stringConverter = StringConverter.StringToYearMonthDuration.INSTANCE;
        TIME                .stringConverter = StringConverter.StringToTime.INSTANCE;
        BOOLEAN             .stringConverter = StringConverter.StringToBoolean.INSTANCE;
        HEX_BINARY          .stringConverter = StringConverter.StringToHexBinary.INSTANCE;
        BASE64_BINARY       .stringConverter = StringConverter.StringToBase64Binary.INSTANCE;
        UNTYPED_ATOMIC      .stringConverter = StringConverter.StringToUntypedAtomic.INSTANCE;

        NON_POSITIVE_INTEGER.stringConverter = new StringConverter.StringToIntegerSubtype(NON_POSITIVE_INTEGER);
        NEGATIVE_INTEGER    .stringConverter = new StringConverter.StringToIntegerSubtype(NEGATIVE_INTEGER);
        LONG                .stringConverter = new StringConverter.StringToIntegerSubtype(LONG);
        INT                 .stringConverter = new StringConverter.StringToIntegerSubtype(INT);
        SHORT               .stringConverter = new StringConverter.StringToIntegerSubtype(SHORT);
        BYTE                .stringConverter = new StringConverter.StringToIntegerSubtype(BYTE);
        NON_NEGATIVE_INTEGER.stringConverter = new StringConverter.StringToIntegerSubtype(NON_NEGATIVE_INTEGER);
        POSITIVE_INTEGER    .stringConverter = new StringConverter.StringToIntegerSubtype(POSITIVE_INTEGER);
        UNSIGNED_LONG       .stringConverter = new StringConverter.StringToIntegerSubtype(UNSIGNED_LONG);
        UNSIGNED_INT        .stringConverter = new StringConverter.StringToIntegerSubtype(UNSIGNED_INT);
        UNSIGNED_SHORT      .stringConverter = new StringConverter.StringToIntegerSubtype(UNSIGNED_SHORT);
        UNSIGNED_BYTE       .stringConverter = new StringConverter.StringToIntegerSubtype(UNSIGNED_BYTE);

        // We were getting an IntelliJ warning here about potential class loading deadlock. See bug #2524. Have moved the
        // static initializers here, and removed the dependency on static initialization in StringConverter, which hopefully
        // solves the problem.

        //NumericType.init();
    }

    public static BuiltInAtomicType fromAlphaCode(String code) {
        return byAlphaCode.get(code);
    }

    private BuiltInAtomicType(int fingerprint) {
        this.fingerprint = fingerprint;
    }


    /**
     * Get the local name of this type
     *
     * @return the local name of this type definition, if it has one. Return null in the case of an
     *         anonymous type.
     */

    @Override
    public String getName() {
        return StandardNames.getLocalName(fingerprint);
    }

    /**
     * Get the corresponding {@link net.sf.saxon.type.UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        return uType;
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
     * @return an EQName identifying the type.
     */
    @Override
    public String getEQName() {
        return "Q{" + NamespaceConstant.SCHEMA + "}" + getName();
    }

    /**
     * Determine whether the type is abstract, that is, whether it cannot have instances that are not also
     * instances of some concrete subtype
     */

    @Override
    public boolean isAbstract() {
        switch (fingerprint) {
            case StandardNames.XS_NOTATION:
            case StandardNames.XS_ANY_ATOMIC_TYPE:
            case StandardNames.XS_NUMERIC:
            case StandardNames.XS_ANY_SIMPLE_TYPE:
                return true;
            default:
                return false;
        }
    }

    /**
     * Determine whether this is a built-in type or a user-defined type
     */

    @Override
    public boolean isBuiltInType() {
        return true;
    }

    /**
     * Get the name of this type as a StructuredQName, unless the type is anonymous, in which case
     * return null
     *
     * @return the name of the atomic type, or null if the type is anonymous.
     */

    /*@NotNull*/
    @Override
    public StructuredQName getTypeName() {
        return new StructuredQName(
                StandardNames.getPrefix(fingerprint),
                StandardNames.getURI(fingerprint),
                StandardNames.getLocalName(fingerprint)
        );
    }

    /**
     * Get an alphabetic code representing the type, or at any rate, the nearest built-in type
     * from which this type is derived. The codes are designed so that for any two built-in types
     * A and B, alphaCode(A) is a prefix of alphaCode(B) if and only if A is a supertype of B.
     *
     * @return the alphacode for the nearest containing built-in type
     */
    @Override
    public String getBasicAlphaCode() {
        return alphaCode;
    }

    /**
     * Get a sequence type representing exactly one instance of this atomic type
     * @return a sequence type representing exactly one instance of this atomic type
     * @since 9.8.0.2
     */

    @Override
    public SequenceType one() {
        if (_one == null) {
            _one = new SequenceType(this, StaticProperty.EXACTLY_ONE);
        }
        return _one;
    }

    /**
     * Get a sequence type representing zero or one instances of this atomic type
     *
     * @return a sequence type representing zero or one instances of this atomic type
     * @since 9.8.0.2
     */

    @Override
    public SequenceType zeroOrOne() {
        if (_zeroOrOne == null) {
            _zeroOrOne = new SequenceType(this, StaticProperty.ALLOWS_ZERO_OR_ONE);
        }
        return _zeroOrOne;
    }

    /**
     * Get a sequence type representing one or more instances of this atomic type
     *
     * @return a sequence type representing one or more instances of this atomic type
     * @since 9.8.0.2
     */

    @Override
    public SequenceType oneOrMore() {
        if (_oneOrMore == null) {
            _oneOrMore = new SequenceType(this, StaticProperty.ALLOWS_ONE_OR_MORE);
        }
        return _oneOrMore;
    }

    /**
     * Get a sequence type representing one or more instances of this atomic type
     *
     * @return a sequence type representing one or more instances of this atomic type
     * @since 9.8.0.2
     */

    @Override
    public SequenceType zeroOrMore() {
        if (_zeroOrMore == null) {
            _zeroOrMore = new SequenceType(this, StaticProperty.ALLOWS_ZERO_OR_MORE);
        }
        return _zeroOrMore;
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
        return ordered || (optimistic && (this == DURATION || this == ANY_ATOMIC));
    }


    /**
     * Get the URI of the schema document where the type was originally defined.
     *
     * @return the URI of the schema document. Returns null if the information is unknown or if this
     *         is a built-in type
     */

    /*@Nullable*/
    @Override
    public String getSystemId() {
        return null;
    }

    /**
     * Determine whether the atomic type is numeric
     *
     * @return true if the type is a built-in numeric type
     */

    public boolean isPrimitiveNumeric() {
        switch (getFingerprint()) {
            case StandardNames.XS_INTEGER:
            case StandardNames.XS_DECIMAL:
            case StandardNames.XS_DOUBLE:
            case StandardNames.XS_FLOAT:
                return true;
            default:
                return false;
        }
    }

    /**
     * Get the validation status - always valid
     */
    @Override
    public final ValidationStatus getValidationStatus() {
        return VALIDATED;
    }

    /**
     * Returns the value of the 'block' attribute for this type, as a bit-significant
     * integer with fields such as {@link SchemaType#DERIVATION_LIST} and {@link SchemaType#DERIVATION_EXTENSION}
     *
     * @return the value of the 'block' attribute for this type
     */

    @Override
    public final int getBlock() {
        return 0;
    }

    /**
     * Gets the integer code of the derivation method used to derive this type from its
     * parent. Returns zero for primitive types.
     *
     * @return a numeric code representing the derivation method, for example {@link SchemaType#DERIVATION_RESTRICTION}
     */

    @Override
    public final int getDerivationMethod() {
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
    public final boolean allowsDerivation(int derivation) {
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
     * Set the base type of this type
     *
     * @param baseFingerprint the namepool fingerprint of the name of the base type
     */

    public final void setBaseTypeFingerprint(int baseFingerprint) {
        this.baseFingerprint = baseFingerprint;
    }

    /**
     * Get the fingerprint of the name of this type
     *
     * @return the fingerprint. Returns an invented fingerprint for an anonymous type.
     */

    @Override
    public final int getFingerprint() {
        return fingerprint;
    }

    /**
     * Get the name of the type as a QName
     *
     * @return a StructuredQName containing the name of the type. The conventional prefix "xs" is used
     *         to represent the XML Schema namespace
     */

    /*@NotNull*/
    @Override
    public final StructuredQName getStructuredQName() {
        return new StructuredQName("xs", NamespaceConstant.SCHEMA, StandardNames.getLocalName(fingerprint));
    }

    /**
     * Get the display name of the type: that is, a lexical QName with an arbitrary prefix
     *
     * @return a lexical QName identifying the type
     */

    @Override
    public String getDisplayName() {
        return StandardNames.getDisplayName(fingerprint);
    }


    /**
     * Ask whether the atomic type is a primitive type.  The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration;
     * xs:untypedAtomic; and all supertypes of these (xs:anyAtomicType, xs:numeric, ...)
     *
     * @return true if the type is considered primitive under the above rules
     */

    @Override
    public final boolean isPrimitiveType() {
        return Type.isPrimitiveAtomicType(fingerprint);
    }

    /**
     * Ask whether this SchemaType is a complex type
     *
     * @return true if this SchemaType is a complex type
     */

    @Override
    public final boolean isComplexType() {
        return false;
    }

    /**
     * Ask whether this is an anonymous type
     *
     * @return true if this SchemaType is an anonymous type
     */

    @Override
    public final boolean isAnonymousType() {
        return false;
    }

    /**
     * Ask whether this is a plain type (a type whose instances are always atomic values)
     *
     * @return true
     */

    @Override
    public boolean isPlainType() {
        return true;
    }

    /**
     * Returns the base type that this type inherits from. This method can be used to get the
     * base type of a type that is known to be valid.
     * If this type is a Simpletype that is a built in primitive type then null is returned.
     *
     * @return the base type.
     * @throws IllegalStateException if this type is not valid.
     */

    /*@Nullable*/
    @Override
    public final SchemaType getBaseType() {
        if (baseFingerprint == -1) {
            return null;
        } else {
            return BuiltInType.getSchemaType(baseFingerprint);
        }
    }

    /**
     * Test whether a given item conforms to this type
     *
     *
     *
     * @param item    The item to be tested
     * @param th      The type hierarchy cache
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item, TypeHierarchy th) {
        return item instanceof AtomicValue && Type.isSubType(((AtomicValue) item).getItemType(), this);
    }

    /**
     * Get the primitive item type corresponding to this item type. For item(),
     * this is Type.ITEM. For node(), it is Type.NODE. For specific node kinds,
     * it is the value representing the node kind, for example Type.ELEMENT.
     * For anyAtomicValue it is Type.ATOMIC_VALUE. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that INTEGER is considered to be a primitive type.
     */

    /*@NotNull*/
    @Override
    public BuiltInAtomicType getPrimitiveItemType() {
        if (isPrimitiveType()) {
            return this;
        } else {
            ItemType s = (ItemType) getBaseType();
            assert s != null;
            if (s.isPlainType()) {
                return (BuiltInAtomicType)s.getPrimitiveItemType();
            } else {
                return this;
            }
        }
    }

    /**
     * Get the primitive type corresponding to this item type. For item(),
     * this is Type.ITEM. For node(), it is Type.NODE. For specific node kinds,
     * it is the value representing the node kind, for example Type.ELEMENT.
     * For anyAtomicValue it is Type.ATOMIC_VALUE. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that INTEGER is considered to be a primitive type.
     */

    @Override
    public int getPrimitiveType() {
        return primitiveFingerprint;
    }

    /**
     * Determine whether this type is supported when using XSD 1.0
     *
     * @return true if this type is permitted in XSD 1.0
     */

    public boolean isAllowedInXSD10() {
        return getFingerprint() != StandardNames.XS_DATE_TIME_STAMP;
    }

    public String toString() {
        return getDisplayName();
    }

    /**
     * Get the item type of the atomic values that will be produced when an item
     * of this type is atomized
     */

    /*@NotNull*/
    @Override
    public AtomicType getAtomizedItemType() {
        return this;
    }

    /**
     * Ask whether values of this type are atomizable
     *
     * @return true unless it is known that these items will be elements with element-only
     *         content, in which case return false
     * @param th The type hierarchy cache
     */

    @Override
    public boolean isAtomizable(TypeHierarchy th) {
        return true;
    }

    /**
     * Returns the base type that this type inherits from. This method can be used to get the
     * base type of a type that is known to be valid.
     * If this type is a Simpletype that is a built in primitive type then null is returned.
     *
     * @return the base type.
     * @throws IllegalStateException if this type is not valid.
     */

    /*@Nullable*/
    public SchemaType getKnownBaseType() {
        return getBaseType();
    }

    /**
     * Test whether this is the same type as another type. They are considered to be the same type
     * if they are derived from the same type definition in the original XML representation (which
     * can happen when there are multiple includes of the same file)
     */

    @Override
    public boolean isSameType(SchemaType other) {
        return other.getFingerprint() == getFingerprint();
    }

    @Override
    public String getDescription() {
        return getDisplayName();
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
        if (type == AnySimpleType.getInstance()) {
            // OK
        } else if (isSameType(type)) {
            // OK
        } else {
            SchemaType base = getBaseType();
            if (base == null) {
                throw new SchemaException("The type " + getDescription() +
                        " is not validly derived from the type " + type.getDescription());
            }
            try {
                base.checkTypeDerivationIsOK(type, block);
            } catch (SchemaException se) {
                throw new SchemaException("The type " + getDescription() +
                        " is not validly derived from the type " + type.getDescription());
            }
        }
    }

    /**
     * Returns true if this SchemaType is a SimpleType
     *
     * @return true (always)
     */

    @Override
    public final boolean isSimpleType() {
        return true;
    }

    /**
     * Test whether this Simple Type is an atomic type
     *
     * @return true, this is an atomic type
     */

    @Override
    public boolean isAtomicType() {
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
        return fingerprint == StandardNames.XS_ID;
    }

    /**
     * Ask whether this type is an IDREF or IDREFS type. This is defined to be any simple type
     * who typed value may contain atomic values of type xs:IDREF: that is, it includes types derived
     * from IDREF or IDREFS by restriction, list, or union
     */

    @Override
    public boolean isIdRefType() {
        return fingerprint == StandardNames.XS_IDREF;
    }

    /**
     * Returns true if this type is derived by list, or if it is derived by restriction
     * from a list type, or if it is a union that contains a list as one of its members
     *
     * @return true if this is a list type
     */

    @Override
    public boolean isListType() {
        return false;
    }

    /**
     * Return true if this type is a union type (that is, if its variety is union)
     *
     * @return true for a union type
     */

    @Override
    public boolean isUnionType() {
        return false;
    }

    /**
     * Determine the whitespace normalization required for values of this type
     *
     * @return one of PRESERVE, REPLACE, COLLAPSE
     */

    @Override
    public int getWhitespaceAction() {
        switch (getFingerprint()) {
            case StandardNames.XS_STRING:
                return Whitespace.PRESERVE;
            case StandardNames.XS_NORMALIZED_STRING:
                return Whitespace.REPLACE;
            default:
                return Whitespace.COLLAPSE;
        }
    }

    /**
     * Returns the built-in base type this type is derived from.
     *
     * @return the first built-in type found when searching up the type hierarchy
     */
    /*@Nullable*/
    @Override
    public SchemaType getBuiltInBaseType() {
        BuiltInAtomicType base = this;
        while ((base != null) && (base.getFingerprint() > 1023)) {
            base = (BuiltInAtomicType) base.getBaseType();
        }
        return base;
    }

    /**
     * Test whether this simple type is namespace-sensitive, that is, whether
     * it is derived from xs:QName or xs:NOTATION.  Note that
     * the result for xs:anyAtomicType is false, even though an instance might be a QName.
     *
     * @return true if this type is derived from xs:QName or xs:NOTATION
     */

    @Override
    public boolean isNamespaceSensitive() {
        BuiltInAtomicType base = this;
        int fp = base.getFingerprint();
        while (fp > 1023) {
            base = (BuiltInAtomicType) base.getBaseType();
            assert base != null;
            fp = base.getFingerprint();
        }

        return fp == StandardNames.XS_QNAME || fp == StandardNames.XS_NOTATION;
    }



    /**
     * Check whether a given input string is valid according to this SimpleType
     *
     * @param value      the input string to be checked
     * @param nsResolver a namespace resolver used to resolve namespace prefixes if the type
     *                   is namespace sensitive. The value supplied may be null; in this case any namespace-sensitive
     *                   content will throw an UnsupportedOperationException.
     * @param rules      conversion rules e.g for namespace-sensitive content
     * @return XPathException if the value is invalid. Note that the exception is returned rather than being thrown.
     *         Returns null if the value is valid.
     * @throws UnsupportedOperationException if the type is namespace-sensitive and no namespace
     *                                       resolver is supplied
     */

    /*@Nullable*/
    @Override
    public ValidationFailure validateContent(CharSequence value, /*@Nullable*/ NamespaceResolver nsResolver,
                                             ConversionRules rules) {
        int f = getFingerprint();
        if (f == StandardNames.XS_STRING ||
                f == StandardNames.XS_ANY_SIMPLE_TYPE ||
                f == StandardNames.XS_UNTYPED_ATOMIC ||
                f == StandardNames.XS_ANY_ATOMIC_TYPE) {
            return null;
        }
        StringConverter converter = stringConverter;
        if (converter == null) {
            converter = getStringConverter(rules);
            if (isNamespaceSensitive()) {
                if (nsResolver == null) {
                    throw new UnsupportedOperationException("Cannot validate a QName without a namespace resolver");
                }
                converter = (StringConverter) converter.setNamespaceResolver(nsResolver);
                ConversionResult result = converter.convertString(value);
                if (result instanceof ValidationFailure) {
                    return (ValidationFailure) result;
                }
                if (fingerprint == StandardNames.XS_NOTATION) {
                    NotationValue nv = (NotationValue) result;
                    // This check added in 9.3. The XSLT spec says that this check should not be performed during
                    // validation. However, this appears to be based on an incorrect assumption: see spec bug 6952
                    if (!rules.isDeclaredNotation(nv.getNamespaceURI(), nv.getLocalName())) {
                        return new ValidationFailure("Notation {" + nv.getNamespaceURI() + "}" +
                                nv.getLocalName() + " is not declared in the schema");
                    }
                }
                return null;
            }
        }
        return converter.validate(value);
    }

    /**
     * Get a StringConverter, an object that converts strings in the lexical space of this
     * data type to instances (in the value space) of the data type.
     * @return a StringConverter to do the conversion. Note that in the case of namespace-sensitive
     * types, the resulting converter needs to be supplied with a NamespaceResolver to handle prefix
     * resolution.
     */


    @Override
    public StringConverter getStringConverter(ConversionRules rules) {
        if (stringConverter != null) {
            return stringConverter;
        }
        switch (fingerprint) {
            case StandardNames.XS_DOUBLE:
            case StandardNames.XS_NUMERIC:
                return rules.getStringToDoubleConverter();
            case StandardNames.XS_FLOAT:
                return new StringConverter.StringToFloat(rules);
            case StandardNames.XS_DATE_TIME:
                return new StringConverter.StringToDateTime(rules);
            case StandardNames.XS_DATE_TIME_STAMP:
                return new StringConverter.StringToDateTimeStamp(rules);
            case StandardNames.XS_DATE:
                return new StringConverter.StringToDate(rules);
            case StandardNames.XS_G_YEAR:
                return new StringConverter.StringToGYear(rules);
            case StandardNames.XS_G_YEAR_MONTH:
                return new StringConverter.StringToGYearMonth(rules);
            case StandardNames.XS_ANY_URI:
                return new StringConverter.StringToAnyURI(rules);
            case StandardNames.XS_QNAME:
                return new StringConverter.StringToQName(rules);
            case StandardNames.XS_NOTATION:
                return new StringConverter.StringToNotation(rules);
            default:
                throw new AssertionError("No string converter available for " + this);
        }
    }

    /**
     * Get the typed value of a node that is annotated with this schema type.
     *
     * @param node the node whose typed value is required
     * @return the typed value.
     * @since 8.5
     */

    @Override
    public AtomicSequence atomize(NodeInfo node) throws XPathException {
        // Fast path for common cases
        CharSequence stringValue = node.getStringValueCS();
        if (stringValue.length() == 0 && node.isNilled()) {
            return AtomicArray.EMPTY_ATOMIC_ARRAY;
        }
        if (fingerprint == StandardNames.XS_STRING) {
            return StringValue.makeStringValue(stringValue);
        } else if (fingerprint == StandardNames.XS_UNTYPED_ATOMIC) {
            return new UntypedAtomicValue(stringValue);
        }
        StringConverter converter = stringConverter;
        if (converter == null) {
            converter = getStringConverter(node.getConfiguration().getConversionRules());
            if (isNamespaceSensitive()) {
                NodeInfo container =
                        node.getNodeKind() == Type.ELEMENT ? node : node.getParent();
                converter = (StringConverter) converter.setNamespaceResolver(container.getAllNamespaces());
            }
        }
        return converter.convertString(stringValue).asAtomic();
    }

    /**
     * Get the typed value corresponding to a given string value, assuming it is
     * valid against this type (and that the containing node is not nilled)
     *
     * @param value    the string value
     * @param resolver a namespace resolver used to resolve any namespace prefixes appearing
     *                 in the content of values. Can supply null, in which case any namespace-sensitive content
     *                 will be rejected.
     * @param rules    the conversion rules to be used
     * @return an iterator over the atomic sequence comprising the typed value. The objects
     *         returned by this SequenceIterator will all be of type {@link AtomicValue}
     * @throws ValidationException This method should be called only if it is known that the value is
     *                             valid. If the value is not valid, there is no guarantee that this method will perform validation,
     *                             but if it does detect a validity error, then it MAY throw a ValidationException.
     */

    /*@NotNull*/
    @Override
    public AtomicSequence getTypedValue(CharSequence value, NamespaceResolver resolver, ConversionRules rules)
            throws ValidationException {
        // Fast path for common cases
        if (fingerprint == StandardNames.XS_STRING) {
            return StringValue.makeStringValue(value);
        } else if (fingerprint == StandardNames.XS_UNTYPED_ATOMIC) {
            return new UntypedAtomicValue(value);
        }
        StringConverter converter = getStringConverter(rules);
        if (isNamespaceSensitive()) {
            converter = (StringConverter) converter.setNamespaceResolver(resolver);
        }
        return converter.convertString(value).asAtomic();
    }

    /**
     * Two types are equal if they have the same fingerprint.
     * Note: it is normally safe to use ==, because we always use the static constants, one instance
     * for each built in atomic type. However, after serialization and deserialization a different instance
     * can appear.
     */

    public boolean equals(Object obj) {
        return obj instanceof BuiltInAtomicType &&
                getFingerprint() == ((BuiltInAtomicType) obj).getFingerprint();
    }

    /**
     * The fingerprint can be used as a hashcode
     */

    public int hashCode() {
        return getFingerprint();
    }


    /**
     * Validate that a primitive atomic value is a valid instance of a type derived from the
     * same primitive type.
     *
     * @param primValue    the value in the value space of the primitive type.
     * @param lexicalValue the value in the lexical space. If null, the string value of primValue
     *                     is used. This value is checked against the pattern facet (if any)
     * @param rules the conversion rules to be used
     * @return null if the value is valid; otherwise, a ValidationFailure object indicating
     *         the nature of the error.
     * @throws UnsupportedOperationException in the case of an external object type
     */

    /*@Nullable*/
    @Override
    public ValidationFailure validate(AtomicValue primValue, CharSequence lexicalValue, ConversionRules rules) {
        switch (fingerprint) {
            case StandardNames.XS_NUMERIC:
            case StandardNames.XS_STRING:
            case StandardNames.XS_BOOLEAN:
            case StandardNames.XS_DURATION:
            case StandardNames.XS_DATE_TIME:
            case StandardNames.XS_DATE:
            case StandardNames.XS_TIME:
            case StandardNames.XS_G_YEAR_MONTH:
            case StandardNames.XS_G_MONTH:
            case StandardNames.XS_G_MONTH_DAY:
            case StandardNames.XS_G_YEAR:
            case StandardNames.XS_G_DAY:
            case StandardNames.XS_HEX_BINARY:
            case StandardNames.XS_BASE64_BINARY:
            case StandardNames.XS_ANY_URI:
            case StandardNames.XS_QNAME:
            case StandardNames.XS_NOTATION:
            case StandardNames.XS_UNTYPED_ATOMIC:
            case StandardNames.XS_DECIMAL:
            case StandardNames.XS_FLOAT:
            case StandardNames.XS_DOUBLE:
            case StandardNames.XS_INTEGER:
                return null;
            case StandardNames.XS_NON_POSITIVE_INTEGER:
            case StandardNames.XS_NEGATIVE_INTEGER:
            case StandardNames.XS_LONG:
            case StandardNames.XS_INT:
            case StandardNames.XS_SHORT:
            case StandardNames.XS_BYTE:
            case StandardNames.XS_NON_NEGATIVE_INTEGER:
            case StandardNames.XS_POSITIVE_INTEGER:
            case StandardNames.XS_UNSIGNED_LONG:
            case StandardNames.XS_UNSIGNED_INT:
            case StandardNames.XS_UNSIGNED_SHORT:
            case StandardNames.XS_UNSIGNED_BYTE:
                return ((IntegerValue) primValue).validateAgainstSubType(this);
            case StandardNames.XS_YEAR_MONTH_DURATION:
            case StandardNames.XS_DAY_TIME_DURATION:
                return null;  // treated as primitive
            case StandardNames.XS_DATE_TIME_STAMP:
                return ((CalendarValue) primValue).getTimezoneInMinutes() == CalendarValue.NO_TIMEZONE
                        ? new ValidationFailure("xs:dateTimeStamp value must have a timezone") : null;
            case StandardNames.XS_NORMALIZED_STRING:
            case StandardNames.XS_TOKEN:
            case StandardNames.XS_LANGUAGE:
            case StandardNames.XS_NAME:
            case StandardNames.XS_NMTOKEN:
            case StandardNames.XS_NCNAME:
            case StandardNames.XS_ID:
            case StandardNames.XS_IDREF:
            case StandardNames.XS_ENTITY:
                return stringConverter.validate(primValue.getStringValueCS());
            default:
                throw new IllegalArgumentException();
        }
    }

    /**
     * Analyze an expression to see whether the expression is capable of delivering a value of this
     * type.
     *
     * @param expression the expression that delivers the content
     * @param kind       the node kind whose content is being delivered: {@link Type#ELEMENT},
     *                   {@link Type#ATTRIBUTE}, or {@link Type#DOCUMENT}
     * @throws net.sf.saxon.trans.XPathException
     *          if the expression will never deliver a value of the correct type
     */

    @Override
    public void analyzeContentExpression(Expression expression, int kind) throws XPathException {
        analyzeContentExpression(this, expression, kind);
    }

    /**
     * Analyze an expression to see whether the expression is capable of delivering a value of this
     * type.
     *
     * @param simpleType the simple type against which the expression is to be checked
     * @param expression the expression that delivers the content
     * @param kind       the node kind whose content is being delivered: {@link Type#ELEMENT},
     *                   {@link Type#ATTRIBUTE}, or {@link Type#DOCUMENT}
     * @throws net.sf.saxon.trans.XPathException
     *          if the expression will never deliver a value of the correct type
     */

    public static void analyzeContentExpression(SimpleType simpleType, Expression expression, int kind)
            throws XPathException {
        if (kind == Type.ELEMENT) {
            expression.checkPermittedContents(simpleType, true);
//            // if we are building the content of an element or document, no atomization will take
//            // place, and therefore the presence of any element or attribute nodes in the content will
//            // cause a validity error, since only simple content is allowed
//            if (Type.isSubType(itemType, NodeKindTest.makeNodeKindTest(Type.ELEMENT))) {
//                throw new XPathException("The content of an element with a simple type must not include any element nodes");
//            }
//            if (Type.isSubType(itemType, NodeKindTest.makeNodeKindTest(Type.ATTRIBUTE))) {
//                throw new XPathException("The content of an element with a simple type must not include any attribute nodes");
//            }
        } else if (kind == Type.ATTRIBUTE) {
            // for attributes, do a check only for text nodes and atomic values: anything else gets atomized
            if (expression instanceof ValueOf || expression instanceof Literal) {
                expression.checkPermittedContents(simpleType, true);
            }
        }
    }

    /**
     * Internal factory method to create a BuiltInAtomicType. There is one instance for each of the
     * built-in atomic types
     *
     * @param fingerprint The name of the type
     * @param baseType    The base type from which this type is derived
     * @param code        Alphabetic code chosen to enable ordering of types according to the type hierarchy
     * @param ordered true if the type is ordered
     * @return the newly constructed built in atomic type
     */
    /*@NotNull*/
    private static BuiltInAtomicType makeAtomicType(int fingerprint, SimpleType baseType, String code, boolean ordered) {
        BuiltInAtomicType t = new BuiltInAtomicType(fingerprint);
        t.setBaseTypeFingerprint(baseType.getFingerprint());
        if (t.isPrimitiveType()) {
            t.primitiveFingerprint = fingerprint;
        } else {
            t.primitiveFingerprint = ((AtomicType) baseType).getPrimitiveType();
        }
        t.uType = UType.fromTypeCode(t.primitiveFingerprint);
        t.ordered = ordered;
        t.alphaCode = code;
        BuiltInType.register(fingerprint, t);
        byAlphaCode.put(code, t);
        return t;
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

    /**
     * Get the list of plain types that are subsumed by this type
     *
     * @return for an atomic type, the type itself; for a plain union type, the list of plain types
     *         in its transitive membership, in declaration order
     */
    /*@NotNull*/
    @Override
    public Set<? extends PlainType> getPlainMemberTypes() {
        return Collections.singleton(this);
    }

    /**
     * Ask whether a built-in type is a numeric type (integer, float, double)
     * @return true if the type is numeric
     */

    public boolean isNumericType() {
        ItemType p = getPrimitiveItemType();
        return p == NumericType.getInstance() || p == DECIMAL ||
                p == DOUBLE || p == FLOAT ||
                p == INTEGER;
    }


}

