/// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

//import com.saxonica.ee.schema.UserSimpleType;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.instruct.ValueOf;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.coercion.*;
import net.sf.saxon.value.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.sf.saxon.type.SchemaValidationStatus.VALIDATED;

/**
 * This class represents a built-in atomic type, which may be either a primitive type
 * (such as xs:decimal or xs:anyURI) or a derived type (such as xs:ID or xs:dayTimeDuration).
 */

public class BuiltInAtomicType implements AtomicType, ItemTypeWithSequenceTypeCache, AtomicMetadata {

    private final int fingerprint;
    private final int baseFingerprint;
    private final int primitiveFingerprint;
    private final UType uType;
    private final String alphaCode;
    private final boolean ordered;
    private SequenceType _one;
    private SequenceType _oneOrMore;
    private SequenceType _zeroOrOne;
    private SequenceType _zeroOrMore;

    private static final Map<String, BuiltInAtomicType> byAlphaCode = new HashMap<>(60);

    /**
     * Internal factory method to create a BuiltInAtomicType. There is one instance for each of the
     * built-in atomic types
     *
     * @param fp The name of the type
     * @param baseFp    The base type from which this type is derived
     * @param primFp    Identifies the primitive type (with integer, dayTimeDuration, and yearMonthDuration considered primitive)
     * @param code        Alphabetic code chosen to enable ordering of types according to the type hierarchy
     * @param ordered     true if the type is ordered
     */
    /*@NotNull*/
    private BuiltInAtomicType(int fp, int baseFp, int primFp, String code, boolean ordered) {
        this.fingerprint = fp;
        this.baseFingerprint = baseFp;
        this.primitiveFingerprint = primFp;
        this.uType = UType.fromTypeCode(primitiveFingerprint);
        this.ordered = ordered;
        this.alphaCode = code;

        BuiltInType.register(fp, this);
        byAlphaCode.put(code, this);
    }


    public final static BuiltInAtomicType ANY_ATOMIC
            = new BuiltInAtomicType(StandardNames.XS_ANY_ATOMIC_TYPE,
                                    StandardNames.XS_ANY_SIMPLE_TYPE,
                                    StandardNames.XS_ANY_ATOMIC_TYPE,
                                    "A", true);

    public final static BuiltInAtomicType STRING =
            new BuiltInAtomicType(StandardNames.XS_STRING,
                                  StandardNames.XS_ANY_ATOMIC_TYPE,
                                  StandardNames.XS_STRING,
                                  "AS", true);

    public final static BuiltInAtomicType BOOLEAN =
            new BuiltInAtomicType(StandardNames.XS_BOOLEAN,
                                  StandardNames.XS_ANY_ATOMIC_TYPE,
                                  StandardNames.XS_BOOLEAN,
                                  "AB", true);

    public final static BuiltInAtomicType DURATION =
            new BuiltInAtomicType(StandardNames.XS_DURATION,
                                  StandardNames.XS_ANY_ATOMIC_TYPE,
                                  StandardNames.XS_DURATION,
                                  "AR", false);

    public final static BuiltInAtomicType DATE_TIME =
            new BuiltInAtomicType(StandardNames.XS_DATE_TIME,
                                  StandardNames.XS_ANY_ATOMIC_TYPE,
                                  StandardNames.XS_DATE_TIME,
                                  "AM", true);

    public final static BuiltInAtomicType DATE =
            new BuiltInAtomicType(StandardNames.XS_DATE,
                                  StandardNames.XS_ANY_ATOMIC_TYPE,
                                  StandardNames.XS_DATE,
                                  "AA", true);

    public final static BuiltInAtomicType TIME =
            new BuiltInAtomicType(StandardNames.XS_TIME,
                                  StandardNames.XS_ANY_ATOMIC_TYPE,
                                  StandardNames.XS_TIME,
                                  "AT", true);

    public final static BuiltInAtomicType G_YEAR_MONTH =
            new BuiltInAtomicType(StandardNames.XS_G_YEAR_MONTH,
                                  StandardNames.XS_ANY_ATOMIC_TYPE,
                                  StandardNames.XS_G_YEAR_MONTH,
                                  "AH", false);

    public final static BuiltInAtomicType G_MONTH =
            new BuiltInAtomicType(StandardNames.XS_G_MONTH,
                                  StandardNames.XS_ANY_ATOMIC_TYPE,
                                  StandardNames.XS_G_MONTH,
                                  "AI", false);

    public final static BuiltInAtomicType G_MONTH_DAY =
            new BuiltInAtomicType(StandardNames.XS_G_MONTH_DAY,
                                  StandardNames.XS_ANY_ATOMIC_TYPE,
                                  StandardNames.XS_G_MONTH_DAY,
                                  "AJ", false);

    public final static BuiltInAtomicType G_YEAR =
            new BuiltInAtomicType(StandardNames.XS_G_YEAR,
                                  StandardNames.XS_ANY_ATOMIC_TYPE,
                                  StandardNames.XS_G_YEAR,
                                  "AG", false);

    public final static BuiltInAtomicType G_DAY =
            new BuiltInAtomicType(StandardNames.XS_G_DAY,
                                  StandardNames.XS_ANY_ATOMIC_TYPE,
                                  StandardNames.XS_G_DAY,
                                  "AK", false);

    public final static BuiltInAtomicType HEX_BINARY =
            new BuiltInAtomicType(StandardNames.XS_HEX_BINARY,
                                  StandardNames.XS_ANY_ATOMIC_TYPE,
                                  StandardNames.XS_HEX_BINARY,
                                  "AX", true);

    public final static BuiltInAtomicType BASE64_BINARY =
            new BuiltInAtomicType(StandardNames.XS_BASE64_BINARY,
                                  StandardNames.XS_ANY_ATOMIC_TYPE,
                                  StandardNames.XS_BASE64_BINARY,
                                  "A2", true);

    public final static BuiltInAtomicType ANY_URI =
            new BuiltInAtomicType(StandardNames.XS_ANY_URI,
                                  StandardNames.XS_ANY_ATOMIC_TYPE,
                                  StandardNames.XS_ANY_URI,
                                  "AU", true);

    public final static BuiltInAtomicType QNAME =
            new BuiltInAtomicType(StandardNames.XS_QNAME,
                                  StandardNames.XS_ANY_ATOMIC_TYPE,
                                  StandardNames.XS_QNAME,
                                  "AQ", false);

    public final static BuiltInAtomicType NOTATION =
            new BuiltInAtomicType(StandardNames.XS_NOTATION,
                                  StandardNames.XS_ANY_ATOMIC_TYPE,
                                  StandardNames.XS_NOTATION,
                                  "AN", false);

    public final static BuiltInAtomicType UNTYPED_ATOMIC =
            new BuiltInAtomicType(StandardNames.XS_UNTYPED_ATOMIC,
                                  StandardNames.XS_ANY_ATOMIC_TYPE,
                                  StandardNames.XS_UNTYPED_ATOMIC,
                                  "AZ", true);

    public final static BuiltInAtomicType DECIMAL =
            new BuiltInAtomicType(StandardNames.XS_DECIMAL,
                                  StandardNames.XS_ANY_ATOMIC_TYPE,
                                  StandardNames.XS_DECIMAL,
                                  "AD", true);

    public final static BuiltInAtomicType FLOAT =
            new BuiltInAtomicType(StandardNames.XS_FLOAT,
                                  StandardNames.XS_ANY_ATOMIC_TYPE,
                                  StandardNames.XS_FLOAT,
                                  "AF", true);

    public final static BuiltInAtomicType DOUBLE =
            new BuiltInAtomicType(StandardNames.XS_DOUBLE,
                                  StandardNames.XS_ANY_ATOMIC_TYPE,
                                  StandardNames.XS_DOUBLE,
                                  "AO", true);

    public final static BuiltInAtomicType INTEGER =
            new BuiltInAtomicType(StandardNames.XS_INTEGER,
                                  StandardNames.XS_DECIMAL,
                                  StandardNames.XS_INTEGER, // integer is deemed primitive
                                  "ADI", true);

    public final static BuiltInAtomicType NON_POSITIVE_INTEGER =
            new BuiltInAtomicType(StandardNames.XS_NON_POSITIVE_INTEGER,
                                  StandardNames.XS_INTEGER,
                                  StandardNames.XS_INTEGER,
                                  "ADIN", true);

    public final static BuiltInAtomicType NEGATIVE_INTEGER =
            new BuiltInAtomicType(StandardNames.XS_NEGATIVE_INTEGER,
                                  StandardNames.XS_NON_POSITIVE_INTEGER,
                                  StandardNames.XS_INTEGER,
                                  "ADINN", true);

    public final static BuiltInAtomicType LONG =
            new BuiltInAtomicType(StandardNames.XS_LONG,
                                  StandardNames.XS_INTEGER,
                                  StandardNames.XS_INTEGER,
                                  "ADIL", true);

    public final static BuiltInAtomicType INT =
            new BuiltInAtomicType(StandardNames.XS_INT,
                                  StandardNames.XS_LONG,
                                  StandardNames.XS_INTEGER,
                                  "ADILI", true);

    public final static BuiltInAtomicType SHORT =
            new BuiltInAtomicType(StandardNames.XS_SHORT,
                                  StandardNames.XS_INT,
                                  StandardNames.XS_INTEGER,
                                  "ADILIS", true);

    public final static BuiltInAtomicType BYTE =
            new BuiltInAtomicType(StandardNames.XS_BYTE,
                                  StandardNames.XS_SHORT,
                                  StandardNames.XS_INTEGER,
                                  "ADILISB", true);

    public final static BuiltInAtomicType NON_NEGATIVE_INTEGER =
            new BuiltInAtomicType(StandardNames.XS_NON_NEGATIVE_INTEGER,
                                  StandardNames.XS_INTEGER,
                                  StandardNames.XS_INTEGER,
                                  "ADIP", true);

    public final static BuiltInAtomicType POSITIVE_INTEGER =
            new BuiltInAtomicType(StandardNames.XS_POSITIVE_INTEGER,
                                  StandardNames.XS_NON_NEGATIVE_INTEGER,
                                  StandardNames.XS_INTEGER,
                                  "ADIPP", true);

    public final static BuiltInAtomicType UNSIGNED_LONG =
            new BuiltInAtomicType(StandardNames.XS_UNSIGNED_LONG,
                                  StandardNames.XS_NON_NEGATIVE_INTEGER,
                                  StandardNames.XS_INTEGER,
                                  "ADIPL", true);

    public final static BuiltInAtomicType UNSIGNED_INT =
            new BuiltInAtomicType(StandardNames.XS_UNSIGNED_INT,
                                  StandardNames.XS_UNSIGNED_LONG,
                                  StandardNames.XS_INTEGER,
                                  "ADIPLI", true);

    public final static BuiltInAtomicType UNSIGNED_SHORT =
            new BuiltInAtomicType(StandardNames.XS_UNSIGNED_SHORT,
                                  StandardNames.XS_UNSIGNED_INT,
                                  StandardNames.XS_INTEGER,
                                  "ADIPLIS", true);

    public final static BuiltInAtomicType UNSIGNED_BYTE =
            new BuiltInAtomicType(StandardNames.XS_UNSIGNED_BYTE,
                                  StandardNames.XS_UNSIGNED_SHORT,
                                  StandardNames.XS_INTEGER,
                                  "ADIPLISB", true);

    public final static BuiltInAtomicType YEAR_MONTH_DURATION =
            new BuiltInAtomicType(StandardNames.XS_YEAR_MONTH_DURATION,
                                  StandardNames.XS_DURATION,
                                  StandardNames.XS_YEAR_MONTH_DURATION,
                                  "ARY", true);

    public final static BuiltInAtomicType DAY_TIME_DURATION =
            new BuiltInAtomicType(StandardNames.XS_DAY_TIME_DURATION,
                                  StandardNames.XS_DURATION,
                                  StandardNames.XS_DAY_TIME_DURATION,
                                  "ARD", true);

    public final static BuiltInAtomicType NORMALIZED_STRING =
            new BuiltInAtomicType(StandardNames.XS_NORMALIZED_STRING,
                                  StandardNames.XS_STRING,
                                  StandardNames.XS_STRING,
                                  "ASN", true);

    public final static BuiltInAtomicType TOKEN =
            new BuiltInAtomicType(StandardNames.XS_TOKEN,
                                  StandardNames.XS_NORMALIZED_STRING,
                                  StandardNames.XS_STRING,
                                  "ASNT", true);

    public final static BuiltInAtomicType LANGUAGE =
            new BuiltInAtomicType(StandardNames.XS_LANGUAGE,
                                  StandardNames.XS_TOKEN,
                                  StandardNames.XS_STRING,
                                  "ASNTL", true);

    public final static BuiltInAtomicType NAME =
            new BuiltInAtomicType(StandardNames.XS_NAME,
                                  StandardNames.XS_TOKEN,
                                  StandardNames.XS_STRING,
                                  "ASNTN", true);

    public final static BuiltInAtomicType NMTOKEN =
            new BuiltInAtomicType(StandardNames.XS_NMTOKEN,
                                  StandardNames.XS_TOKEN,
                                  StandardNames.XS_STRING,
                                  "ASNTK", true);

    public final static BuiltInAtomicType NCNAME =
            new BuiltInAtomicType(StandardNames.XS_NCNAME,
                                  StandardNames.XS_NAME,
                                  StandardNames.XS_STRING,
                                  "ASNTNC", true);

    public final static BuiltInAtomicType ID =
            new BuiltInAtomicType(StandardNames.XS_ID,
                                  StandardNames.XS_NCNAME,
                                  StandardNames.XS_STRING,
                                  "ASNTNCI", true);

    public final static BuiltInAtomicType IDREF =
            new BuiltInAtomicType(StandardNames.XS_IDREF,
                                  StandardNames.XS_NCNAME,
                                  StandardNames.XS_STRING,
                                  "ASNTNCR", true);

    public final static BuiltInAtomicType ENTITY =
            new BuiltInAtomicType(StandardNames.XS_ENTITY,
                                  StandardNames.XS_NCNAME,
                                  StandardNames.XS_STRING,
                                  "ASNTNCE", true);

    public final static BuiltInAtomicType DATE_TIME_STAMP =
            new BuiltInAtomicType(StandardNames.XS_DATE_TIME_STAMP,
                                  StandardNames.XS_DATE_TIME,
                                  StandardNames.XS_DATE_TIME,
                                  "AMP", true);

    public static BuiltInAtomicType fromAlphaCode(String code) {
        return byAlphaCode.get(code);
    }

    /**
     * Ask whether an item type is "string-like" in its comparison semantics
     *
     * @param type the item type
     * @return true if the item type is xs:string, xs:anyURI, or xs:untypedAtomic
     */

    public static boolean isStringLike(ItemType type) {
        int fp = type.getPrimitiveType();
        return fp == StandardNames.XS_STRING ||
                fp == StandardNames.XS_ANY_URI ||
                fp == StandardNames.XS_UNTYPED_ATOMIC;
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
     * Expand this item type to a choice item type. The default delivers a choice
     * with this type as its only member. Implementations for abstract types return
     * a choice of the corresponding concrete types, for example {@code node()} expands
     * to a choice of the seven node kinds.
     */
    @Override
    public ChoiceItemType asChoiceItemType() {
        if (fingerprint == StandardNames.XS_ANY_ATOMIC_TYPE) {
            return ChoiceItemType.CHOICE_OF_ATOMIC;
        }
        return ChoiceItemType.of(this);
    }

    /**
     * Get the target namespace of this type
     *
     * @return the target namespace of this type definition, if it has one. Return null in the case
     *         of an anonymous type, and in the case of a global type defined in a no-namespace schema.
     */

    @Override
    public NamespaceUri getTargetNamespace() {
        return NamespaceUri.SCHEMA;
    }

    /**
     * Get the name of this type as an EQName, that is, a string in the format Q{uri}local.
     *
     * @return an EQName identifying the type.
     */
    @Override
    public String getEQName() {
        return "Q{" + NamespaceUri.SCHEMA + "}" + getName();
    }

    /**
     * Determine whether the type is abstract, that is, whether it cannot have instances that are not also
     * instances of some concrete subtype
     */

    @Override
    public boolean isAbstract() {
        return switch (fingerprint) {
            case StandardNames.XS_NOTATION,
                 StandardNames.XS_ANY_ATOMIC_TYPE,
                 StandardNames.XS_NUMERIC,
                 StandardNames.XS_ANY_SIMPLE_TYPE -> true;
            default -> false;
        };
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
     * Ask whether another type is a subtype of this type (or is the type itself)
     * @param other the other type
     * @return true if the other type is a subtype of this type, which includes the case where it is the same type
     */

    public boolean hasSubType(ItemType other) {
        if (this == other) {
            return true;
        }
        if (other instanceof BuiltInAtomicType bat && bat.getBasicAlphaCode().startsWith(getBasicAlphaCode())) {
            return true;
        }
        if (other == ErrorType.getInstance()) {
            return true;
        }
        return other instanceof AtomicType at && Subsumption.derivesFrom(at, this);
    }

    /**
     * Get a sequence type representing exactly one instance of this atomic type
     * @return a sequence type representing exactly one instance of this atomic type
     * @since 9.8.0.2
     */

    @Override
    public SequenceType one() {
        if (_one == null) {
            _one = SequenceType.one(this);
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
            _zeroOrOne = SequenceType.optional(this);
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
            _oneOrMore = SequenceType.oneOrMore(this);
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
            _zeroOrMore = SequenceType.zeroOrMore(this);
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
    public final SchemaValidationStatus getValidationStatus() {
        return VALIDATED;
    }

    /**
     * Returns the value of the 'block' attribute for this type, as a bit-significant
     * integer with fields such as {@link Derivation#DERIVATION_LIST} and {@link Derivation#DERIVATION_EXTENSION}
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
     * @return a numeric code representing the derivation method, for example {@link Derivation#DERIVATION_RESTRICTION}
     */

    @Override
    public final int getDerivationMethod() {
        return Derivation.DERIVATION_RESTRICTION;
    }

    /**
     * Determines whether derivation (of a particular kind)
     * from this type is allowed, based on the "final" property
     *
     * @param derivation the kind of derivation, for example {@link Derivation#DERIVATION_LIST}
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
     *         containing bits such as {@link net.sf.saxon.type.Derivation#DERIVATION_EXTENSION}
     */
    @Override
    public int getFinalProhibitions() {
        return 0;
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
        return new StructuredQName("xs", NamespaceUri.SCHEMA, StandardNames.getLocalName(fingerprint));
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
     * @param item The item to be tested
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item) {
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
                return (BuiltInAtomicType) s.getPrimitiveItemType();
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
        if (type == AnySimpleType.INSTANCE) {
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
        return switch (fingerprint) {
            case StandardNames.XS_STRING -> Whitespace.PRESERVE;
            case StandardNames.XS_NORMALIZED_STRING -> Whitespace.REPLACE;
            default -> Whitespace.COLLAPSE;
        };
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
    public ValidationFailure validateContent(UnicodeString value, /*@Nullable*/ NamespaceResolver nsResolver,
                                             ConversionRules rules) {
        int f = getFingerprint();
        if (f == StandardNames.XS_STRING ||
                f == StandardNames.XS_ANY_SIMPLE_TYPE ||
                f == StandardNames.XS_UNTYPED_ATOMIC ||
                f == StandardNames.XS_ANY_ATOMIC_TYPE) {
            return null;
        }
        StringConverter converter = getStringConverter(rules);

        if (isNamespaceSensitive()) {
            if (nsResolver == null) {
                throw new UnsupportedOperationException("Cannot validate a QName without a namespace resolver");
            }
            converter = (StringConverter) converter.setNamespaceResolver(nsResolver);
            ConversionResult result = converter.convertString(value);
            if (result instanceof ValidationFailure) {
                return (ValidationFailure) result;
            }
            // We no longer check for xs:NOTATION that the notation is declared in the schema. See Saxon bug 7060.
            // The check is needed only when validating the values in the enumeration facet of a type derived
            // from xs:NOTATION (in which case it is done by the schema processor directly). In all other cases,
            // we are checking against a type derived from xs:NOTATION, and the check will be done while
            // down-casting.

// CAN DROP THIS:
//            if (fingerprint == StandardNames.XS_NOTATION) {
//                NotationValue nv = (NotationValue) result;
//                // This check added in 9.3. The XSLT spec says that this check should not be performed during
//                // validation. However, this appears to be based on an incorrect assumption: see spec bug 6952
//                if (!rules.isDeclaredNotation(nv.getNamespaceURI(), nv.getLocalName())) {
//                    return new ValidationFailure("Notation {" + nv.getNamespaceURI() + "}" +
//                                                         nv.getLocalName() + " is not declared in the schema");
//                }
//            }
            return null;
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
        return rules.getConverterFromString(this);
//        if (stringConverter != null) {
//            return stringConverter;
//        }
//        switch (fingerprint) {
//            case StandardNames.XS_DOUBLE:
//            case StandardNames.XS_NUMERIC:
//                return rules.getStringToDoubleConverter();
//            case StandardNames.XS_FLOAT:
//                return new StringConverter.StringToFloat(rules);
//            case StandardNames.XS_DATE_TIME:
//                return new StringConverter.StringToDateTime(rules);
//            case StandardNames.XS_DATE_TIME_STAMP:
//                return new StringConverter.StringToDateTimeStamp(rules);
//            case StandardNames.XS_DATE:
//                return new StringConverter.StringToDate(rules);
//            case StandardNames.XS_G_YEAR:
//                return new StringConverter.StringToGYear(rules);
//            case StandardNames.XS_G_YEAR_MONTH:
//                return new StringConverter.StringToGYearMonth(rules);
//            case StandardNames.XS_ANY_URI:
//                return new StringConverter.StringToAnyURI(rules);
//            case StandardNames.XS_QNAME:
//                return new StringConverter.StringToQName(rules);
//            case StandardNames.XS_NOTATION:
//                return new StringConverter.StringToNotation(rules);
//            default:
//                throw new AssertionError("No string converter available for " + this);
//        }
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
        UnicodeString stringValue = node.getUnicodeStringValue();
        if (stringValue.isEmpty() && node.isNilled()) {
            return AtomicArray.EMPTY_ATOMIC_ARRAY;
        }
        if (fingerprint == StandardNames.XS_STRING) {
            return new StringValue(stringValue.tidy());
        } else if (fingerprint == StandardNames.XS_UNTYPED_ATOMIC) {
            return StringValue.makeUntypedAtomic(stringValue);
        }
        StringConverter converter = getStringConverter(node.getConfiguration().getConversionRules());
        if (isNamespaceSensitive()) {
            NodeInfo container =
                    node.getNodeKind() == Type.ELEMENT ? node : (NodeInfo) node.getParent();
            converter = (StringConverter) converter.setNamespaceResolver(container.getAllNamespaces());
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
    public AtomicSequence getTypedValue(UnicodeString value, NamespaceResolver resolver, ConversionRules rules)
            throws ValidationException {
        // Fast path for common cases
        if (fingerprint == StandardNames.XS_STRING) {
            return new StringValue(value.tidy());
        } else if (fingerprint == StandardNames.XS_UNTYPED_ATOMIC) {
            return StringValue.makeUntypedAtomic(value);
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
    public ValidationFailure validate(AtomicValue primValue, UnicodeString lexicalValue, ConversionRules rules) {
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
                return null;
            case StandardNames.XS_INTEGER:
                if (primValue.getItemType() == BuiltInAtomicType.DECIMAL) {
                    if (((DecimalValue) primValue).isWholeNumber()) {
                        return null;
                    } else {
                        return new ValidationFailure("xs:decimal value " + primValue.toShortString() + " cannot be used where xs:integer is required");
                    }
                } else {
                    return null;
                }
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
                if (primValue instanceof BigDecimalValue && ((BigDecimalValue) primValue).isWholeNumber()) {
                    primValue = IntegerValue.makeIntegerValue(((BigDecimalValue) primValue).getDecimalValue().toBigInteger());
                }
                if (primValue instanceof IntegerValue) {
                    return ((IntegerValue) primValue).validateAgainstSubType(this);
                } else {
                    return new ValidationFailure("xs:decimal value " + primValue.toShortString() +
                                                         " cannot be used where integer subtype " + this + " is required");
                }
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
                StringConverter stringConverter = getStringConverter(rules);
                return stringConverter.validate(primValue.getUnicodeStringValue());
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
     * @param schema
     * @throws net.sf.saxon.trans.XPathException if the expression will never deliver a value of the correct type
     */

    @Override
    public void analyzeContentExpression(Expression expression, int kind, Schema schema) throws XPathException {
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
     * Get the coercion plan for use when this type is the required type for (say) coercion
     * of arguments in a function call
     *
     * @param version the XPath langauge version (40 or 31)
     */
    @Override
    public CoercionPlan getCoercionPlan(int version) {
        if (version >= 40) {
            return switch (fingerprint) {
                case StandardNames.XS_ANY_ATOMIC_TYPE -> AtomicCoercionPlan.getInstance();
                case StandardNames.XS_STRING -> StringCoercionPlan.getInstance();
                case StandardNames.XS_ANY_URI -> AnyURICoercionPlan.getInstance();
                case StandardNames.XS_DECIMAL -> DecimalCoercionPlan.getInstance();
                case StandardNames.XS_DURATION -> AtomicCoercionPlan.getInstance();
                case StandardNames.XS_G_YEAR -> AtomicCoercionPlan.getInstance();
                case StandardNames.XS_G_YEAR_MONTH -> AtomicCoercionPlan.getInstance();
                case StandardNames.XS_G_MONTH -> AtomicCoercionPlan.getInstance();
                case StandardNames.XS_G_MONTH_DAY -> AtomicCoercionPlan.getInstance();
                case StandardNames.XS_G_DAY -> AtomicCoercionPlan.getInstance();
                case StandardNames.XS_TIME -> AtomicCoercionPlan.getInstance();
                case StandardNames.XS_DATE -> AtomicCoercionPlan.getInstance();
                case StandardNames.XS_DATE_TIME -> AtomicCoercionPlan.getInstance();
                case StandardNames.XS_DATE_TIME_STAMP -> AtomicCoercionPlan.getInstance();
                case StandardNames.XS_BOOLEAN -> AtomicCoercionPlan.getInstance();
                case StandardNames.XS_DOUBLE -> DoubleCoercionPlan.getInstance();
                case StandardNames.XS_FLOAT -> FloatCoercionPlan.getInstance(version);
                case StandardNames.XS_HEX_BINARY -> HexBinaryCoercionPlan.getInstance();
                case StandardNames.XS_BASE64_BINARY -> Base64BinaryCoercionPlan.getInstance();
                case StandardNames.XS_QNAME -> QNameCoercionPlan.getInstance();
                case StandardNames.XS_NOTATION -> QNameCoercionPlan.getInstance();
                case StandardNames.XS_UNTYPED_ATOMIC -> AtomicCoercionPlan.getInstance();

                default -> DerivedAtomicCoercionPlan.getInstance();
            };
        } else {
            return switch (fingerprint) {
                case StandardNames.XS_STRING -> StringCoercionPlan.getInstance();
                case StandardNames.XS_ANY_URI -> AnyURICoercionPlan.getInstance();
                case StandardNames.XS_DECIMAL -> DecimalCoercionPlan.getInstance();
                case StandardNames.XS_DOUBLE -> DoubleCoercionPlan.getInstance();
                case StandardNames.XS_FLOAT -> FloatCoercionPlan.getInstance(version);
                case StandardNames.XS_QNAME -> QNameCoercionPlan.getInstance();
                case StandardNames.XS_NOTATION -> QNameCoercionPlan.getInstance();
                default -> AtomicCoercionPlan.getInstance();
            };
        }
    }

    /**
     * Get the corresponding primitive UType. Note this treats integer as decimal, and duration
     * subtypes as xs:duration.
     * @return the corresponding primitive UType
     */

    public PrimitiveUType getPrimitiveUType() {
        return switch (getPrimitiveType()) {
            case StandardNames.XS_STRING -> PrimitiveUType.STRING;
            case StandardNames.XS_ANY_URI -> PrimitiveUType.ANY_URI;
            case StandardNames.XS_DECIMAL -> PrimitiveUType.DECIMAL;
            case StandardNames.XS_DURATION -> PrimitiveUType.DURATION;
            case StandardNames.XS_G_YEAR -> PrimitiveUType.G_YEAR;
            case StandardNames.XS_G_YEAR_MONTH -> PrimitiveUType.G_YEAR_MONTH;
            case StandardNames.XS_G_MONTH -> PrimitiveUType.G_MONTH;
            case StandardNames.XS_G_MONTH_DAY -> PrimitiveUType.G_MONTH_DAY;
            case StandardNames.XS_G_DAY -> PrimitiveUType.G_DAY;
            case StandardNames.XS_TIME -> PrimitiveUType.TIME;
            case StandardNames.XS_DATE -> PrimitiveUType.DATE;
            case StandardNames.XS_DATE_TIME -> PrimitiveUType.DATE_TIME;
            case StandardNames.XS_BOOLEAN -> PrimitiveUType.BOOLEAN;
            case StandardNames.XS_DOUBLE -> PrimitiveUType.DOUBLE;
            case StandardNames.XS_FLOAT -> PrimitiveUType.FLOAT;
            case StandardNames.XS_HEX_BINARY -> PrimitiveUType.HEX_BINARY;
            case StandardNames.XS_BASE64_BINARY -> PrimitiveUType.BASE64_BINARY;
            case StandardNames.XS_QNAME -> PrimitiveUType.QNAME;
            case StandardNames.XS_NOTATION -> PrimitiveUType.NOTATION;
            case StandardNames.XS_UNTYPED_ATOMIC -> PrimitiveUType.UNTYPED_ATOMIC;

            case StandardNames.XS_INTEGER -> PrimitiveUType.DECIMAL;
            case StandardNames.XS_DAY_TIME_DURATION -> PrimitiveUType.DURATION;
            case StandardNames.XS_YEAR_MONTH_DURATION -> PrimitiveUType.DURATION;

            default -> throw new IllegalArgumentException();

        };
    }

    /**
     * Apply any pre-lexical facets, other than whitespace. At the moment the only such
     * facet is saxon:preprocess
     *
     * @param input the value to be preprocessed
     * @return the value after preprocessing
     */

    @Override
    public UnicodeString preprocess(UnicodeString input) {
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
    public UnicodeString postprocess(UnicodeString input) {
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
    public List<? extends PlainType> getPlainMemberTypes() {
        return Collections.singletonList((PlainType) this);
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

    /**
     * Ask whether a built-in type is a duration type (duration, dayTimeDuration, yearMonthDuration)
     *
     * @return true if the type is xs:duration or a subtype
     */

    public boolean isDurationType() {
        return this == DURATION || this == DAY_TIME_DURATION || this == YEAR_MONTH_DURATION;
    }

    @Override
    public AtomicType getType() {
        return this;
    }

    @Override
    public MapItem getLabel() {
        return null;
    }


}

