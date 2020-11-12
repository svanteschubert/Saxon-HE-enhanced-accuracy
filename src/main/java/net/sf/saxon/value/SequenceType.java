////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;

import java.util.Optional;

/**
 * SequenceType: a sequence type consists of a primary type, which indicates the type of item,
 * and a cardinality, which indicates the number of occurrences permitted. Where the primary type
 * is element or attribute, there may also be a content type, indicating the required type
 * annotation on the element or attribute content.
 */

public final class SequenceType {


    private ItemType primaryType;    // the primary type of the item, e.g. "element", "comment", or "integer"
    private int cardinality;    // the required cardinality

    /**
     * A type that allows any sequence of items
     */

    public static final SequenceType ANY_SEQUENCE =
            AnyItemType.getInstance().zeroOrMore();

    /**
     * A type that allows exactly one item, of any kind
     */

    public static final SequenceType SINGLE_ITEM =
            AnyItemType.getInstance().one();

    /**
     * A type that allows zero or one items, of any kind
     */

    public static final SequenceType OPTIONAL_ITEM =
            AnyItemType.getInstance().zeroOrOne();

    /**
     * A type that allows exactly one atomic value
     */

    public static final SequenceType SINGLE_ATOMIC =
            BuiltInAtomicType.ANY_ATOMIC.one();

    /**
     * A type that allows zero or one atomic values
     */

    public static final SequenceType OPTIONAL_ATOMIC =
            BuiltInAtomicType.ANY_ATOMIC.zeroOrOne();
    /*

     * A type that allows zero or more atomic values
     */

    public static final SequenceType ATOMIC_SEQUENCE =
            BuiltInAtomicType.ANY_ATOMIC.zeroOrMore();

    /**
     * A type that allows a single string
     */

    public static final SequenceType SINGLE_STRING =
            BuiltInAtomicType.STRING.one();

    /**
     * A type that allows a single untyped atomic
     */

    public static final SequenceType SINGLE_UNTYPED_ATOMIC =
            BuiltInAtomicType.UNTYPED_ATOMIC.one();

    /**
     * A type that allows a single optional string
     */

    public static final SequenceType OPTIONAL_STRING =
            BuiltInAtomicType.STRING.zeroOrOne();

    /**
     * A type that allows a single boolean
     */

    public static final SequenceType SINGLE_BOOLEAN =
            BuiltInAtomicType.BOOLEAN.one();

    /**
     * A type that allows a single optional boolean
     */

    public static final SequenceType OPTIONAL_BOOLEAN =
            BuiltInAtomicType.BOOLEAN.zeroOrOne();

    /**
     * A type that allows a single integer
     */

    public static final SequenceType SINGLE_INTEGER =
            BuiltInAtomicType.INTEGER.one();

    /**
     * A type that allows a single decimal
     */

    public static final SequenceType SINGLE_DECIMAL =
            BuiltInAtomicType.DECIMAL.one();

    /**
     * A type that allows a single optional integer
     */

    public static final SequenceType OPTIONAL_INTEGER =
            BuiltInAtomicType.INTEGER.zeroOrOne();


    /**
     * A type that allows a single short
     */

    public static final SequenceType SINGLE_SHORT =
            BuiltInAtomicType.SHORT.one();

    /**
     * A type that allows a single optional short
     */

    public static final SequenceType OPTIONAL_SHORT =
            BuiltInAtomicType.SHORT.zeroOrOne();

    /**
     * A type that allows a single short
     */

    public static final SequenceType SINGLE_BYTE =
            BuiltInAtomicType.BYTE.one();

    /**
     * A type that allows a single optional byte
     */

    public static final SequenceType OPTIONAL_BYTE =
            BuiltInAtomicType.BYTE.zeroOrOne();


    /**
     * A type that allows a single double
     */

    public static final SequenceType SINGLE_DOUBLE =
            BuiltInAtomicType.DOUBLE.one();

    /**
     * A type that allows a single optional double
     */

    public static final SequenceType OPTIONAL_DOUBLE =
            BuiltInAtomicType.DOUBLE.zeroOrOne();

    /**
     * A type that allows a single float
     */

    public static final SequenceType SINGLE_FLOAT =
            BuiltInAtomicType.FLOAT.one();

    /**
     * A type that allows a single optional float
     */

    public static final SequenceType OPTIONAL_FLOAT =
            BuiltInAtomicType.FLOAT.zeroOrOne();

    /**
     * A type that allows a single optional decimal
     */

    public static final SequenceType OPTIONAL_DECIMAL =
            BuiltInAtomicType.DECIMAL.zeroOrOne();

    /**
     * A type that allows a single optional anyURI
     */

    public static final SequenceType OPTIONAL_ANY_URI =
            BuiltInAtomicType.ANY_URI.zeroOrOne();

    /**
     * A type that allows a single optional date
     */

    public static final SequenceType OPTIONAL_DATE =
            BuiltInAtomicType.DATE.zeroOrOne();

    /**
     * A type that allows a single optional time
     */

    public static final SequenceType OPTIONAL_TIME =
            BuiltInAtomicType.TIME.zeroOrOne();

    /**
     * A type that allows a single optional gYear
     */

    public static final SequenceType OPTIONAL_G_YEAR =
            BuiltInAtomicType.G_YEAR.zeroOrOne();

    /**
     * A type that allows a single optional gYearMonth
     */

    public static final SequenceType OPTIONAL_G_YEAR_MONTH =
            BuiltInAtomicType.G_YEAR_MONTH.zeroOrOne();

    /**
     * A type that allows a single optional gMonth
     */

    public static final SequenceType OPTIONAL_G_MONTH =
            BuiltInAtomicType.G_MONTH.zeroOrOne();

    /**
     * A type that allows a single optional gMonthDay
     */

    public static final SequenceType OPTIONAL_G_MONTH_DAY =
            BuiltInAtomicType.G_MONTH_DAY.zeroOrOne();
    /**
     * A type that allows a single optional gDay
     */

    public static final SequenceType OPTIONAL_G_DAY =
            BuiltInAtomicType.G_DAY.zeroOrOne();


    /**
     * A type that allows a single optional dateTime
     */

    public static final SequenceType OPTIONAL_DATE_TIME =
            BuiltInAtomicType.DATE_TIME.zeroOrOne();
    /**
     * A type that allows a single optional duration
     */

    public static final SequenceType OPTIONAL_DURATION =
            BuiltInAtomicType.DURATION.zeroOrOne();

    /**
     * A type that allows a single optional yearMonthDuration
     */

    public static final SequenceType OPTIONAL_YEAR_MONTH_DURATION =
            BuiltInAtomicType.YEAR_MONTH_DURATION.zeroOrOne();

    /**
     * A type that allows a single optional dayTimeDuration
     */

    public static final SequenceType OPTIONAL_DAY_TIME_DURATION =
            BuiltInAtomicType.DAY_TIME_DURATION.zeroOrOne();


    /**
     * A type that allows a single xs:QName
     */

    public static final SequenceType SINGLE_QNAME =
            BuiltInAtomicType.QNAME.one();
    /**
     * A type that allows a single optional xs:QName
     */

    public static final SequenceType OPTIONAL_QNAME =
            BuiltInAtomicType.QNAME.zeroOrOne();
    /**
     * A type that allows a single optional xs:NOTATION
     */

    public static final SequenceType OPTIONAL_NOTATION =
            BuiltInAtomicType.NOTATION.zeroOrOne();

    /**
     * A type that allows a single optional xs:Base64Binary
     */

    public static final SequenceType OPTIONAL_BASE64_BINARY =
            BuiltInAtomicType.BASE64_BINARY.zeroOrOne();

    /**
     * A type that allows a single optional xs:hexBinary
     */

    public static final SequenceType OPTIONAL_HEX_BINARY =
            BuiltInAtomicType.HEX_BINARY.zeroOrOne();
    /**
     * A type that allows an optional numeric value
     */

    public static final SequenceType OPTIONAL_NUMERIC =
            makeSequenceType(NumericType.getInstance(), StaticProperty.ALLOWS_ZERO_OR_ONE);

    public static final SequenceType SINGLE_NUMERIC =
            makeSequenceType(NumericType.getInstance(), StaticProperty.EXACTLY_ONE);

    /**
     * A type that allows zero or one nodes
     */

    public static final SequenceType OPTIONAL_NODE =
            AnyNodeTest.getInstance().zeroOrOne();

    /**
     * A type that allows a single node
     */

    public static final SequenceType SINGLE_NODE =
            AnyNodeTest.getInstance().one();

    /**
     * A type that allows a single document node
     */

    public static final SequenceType OPTIONAL_DOCUMENT_NODE =
            NodeKindTest.DOCUMENT.zeroOrOne();

    /**
     * A type that allows a sequence of zero or more nodes
     */

    public static final SequenceType NODE_SEQUENCE =
            AnyNodeTest.getInstance().zeroOrMore();

    /**
     * A type that allows a sequence of zero or more string values
     */
    public static final SequenceType STRING_SEQUENCE =
            BuiltInAtomicType.STRING.zeroOrMore();

    /**
     * A type that allows a single function item
     */

    public static final SequenceType SINGLE_FUNCTION =
            makeSequenceType(AnyFunctionType.ANY_FUNCTION, StaticProperty.EXACTLY_ONE);

    /**
     * A type that allows a sequence of zero or one function items
     */
    public static final SequenceType OPTIONAL_FUNCTION_ITEM =
            makeSequenceType(AnyFunctionType.getInstance(), StaticProperty.ALLOWS_ZERO_OR_ONE);

    /**
     * A type that allows a sequence of zero or mode function items
     */
    public static final SequenceType FUNCTION_ITEM_SEQUENCE =
            makeSequenceType(AnyFunctionType.getInstance(), StaticProperty.ALLOWS_ZERO_OR_MORE);


    /**
     * A type that only permits the empty sequence
     */

    public static final SequenceType EMPTY_SEQUENCE =
            new SequenceType(ErrorType.getInstance(), StaticProperty.EMPTY);

    /**
     * A type that only permits a non-empty sequence
     */

    public static final SequenceType NON_EMPTY_SEQUENCE =
            makeSequenceType(AnyItemType.getInstance(), StaticProperty.ALLOWS_ONE_OR_MORE);

    /**
     * A type that has no instances
     */

    public static final SequenceType VOID =
            makeSequenceType(ErrorType.getInstance(), StaticProperty.ALLOWS_MANY);

    /**
     * Construct an instance of SequenceType.
     *
     * @param primaryType The item type
     * @param cardinality The required cardinality
     */
    public SequenceType(ItemType primaryType, int cardinality) {
        this.primaryType = primaryType;
        if (primaryType instanceof ErrorType && Cardinality.allowsZero(cardinality)) {
            this.cardinality = StaticProperty.EMPTY;
        } else {
            this.cardinality = cardinality;
        }
    }

    /**
     * Construct an instance of SequenceType. This is a factory method: it maintains a
     * pool of SequenceType objects to reduce the amount of object creation.
     *
     * @param primaryType The item type
     * @param cardinality The required cardinality. This must be one of the constants {@link StaticProperty#EXACTLY_ONE},
     *                    {@link StaticProperty#ALLOWS_ONE_OR_MORE}, etc
     * @return the SequenceType (either a newly created object, or an existing one from the cache)
     */

    public static SequenceType makeSequenceType(ItemType primaryType, int cardinality) {

        if (primaryType instanceof ItemType.WithSequenceTypeCache) {
            ItemType.WithSequenceTypeCache bat = (ItemType.WithSequenceTypeCache) primaryType;
            switch (cardinality) {
                case StaticProperty.EXACTLY_ONE:
                    return bat.one();
                case StaticProperty.ALLOWS_ZERO_OR_ONE:
                    return bat.zeroOrOne();
                case StaticProperty.ALLOWS_ZERO_OR_MORE:
                    return bat.zeroOrMore();
                case StaticProperty.ALLOWS_ONE_OR_MORE:
                    return bat.oneOrMore();
                default:
                    // fall through
            }
        }
        if (cardinality == StaticProperty.ALLOWS_ZERO) {
            return SequenceType.EMPTY_SEQUENCE;
        }
        return new SequenceType(primaryType, cardinality);
    }

    /**
     * Get the "primary" part of this required type. E.g. for type element(*, xs:date) the "primary type" is element()
     *
     * @return The item type code of the primary type
     */
    public ItemType getPrimaryType() {
        return primaryType;
    }

    /**
     * Get the cardinality component of this SequenceType. This is one of the constants {@link StaticProperty#EXACTLY_ONE},
     * {@link StaticProperty#ALLOWS_ONE_OR_MORE}, etc
     *
     * @return the required cardinality
     * @see net.sf.saxon.value.Cardinality
     */
    public int getCardinality() {
        return cardinality;
    }

    /**
     * Determine whether a given value is a valid instance of this SequenceType
     *
     * @param value the value to be tested
     * @param th    the type hierarchy cache
     * @return true if the value is a valid instance of this type
     * @throws XPathException if a dynamic error occurs while evaluating the Sequence (this
     *                        won't happen if the sequence is grounded)
     */

    public boolean matches(Sequence value, TypeHierarchy th) throws XPathException {
        int count = 0;
        SequenceIterator iter = value.iterate();
        Item item;
        while ((item = iter.next()) != null) {
            count++;
            if (!primaryType.matches(item, th)) {
                return false;
            }
        }
        return !(count == 0 && !Cardinality.allowsZero(cardinality)
                         || count > 1 && !Cardinality.allowsMany(cardinality));
    }

    /**
     * Get extra diagnostic information about why a supplied item does not conform to this
     * item type, if available. If extra information is returned, it should be in the form of a complete
     * sentence, minus the closing full stop. No information should be returned for obvious cases.
     *
     * @param value the value which has been found not to match this sequence type
     * @param th the TypeHierarchy cache
     */

    public Optional<String> explainMismatch(GroundedValue value, TypeHierarchy th) {
        try {
            int count = 0;
            SequenceIterator iter = value.iterate();
            Item item;
            while ((item = iter.next()) != null) {
                count++;
                if (!primaryType.matches(item, th)) {
                    String s = "The " + RoleDiagnostic.ordinal(count) + " item is not an instance of the required type";
                    Optional<String> more = primaryType.explainMismatch(item, th);
                    if (more.isPresent()) {
                        s = count == 1 ? more.get() : s + ". " + more.get();
                    } else {
                        if (count == 1) {
                            return Optional.empty(); // no new information, so don't say anything
                        }
                    }
                    return Optional.of(s);
                }
            }
            if (count == 0 && !Cardinality.allowsZero(cardinality)) {
                return Optional.of("The type does not allow an empty sequence");
            } else if (count > 1 && !Cardinality.allowsMany(cardinality)) {
                return Optional.of("The type does not allow a sequence of more than one item");
            }
            return Optional.empty();
        } catch (XPathException e) {
            return Optional.empty();
        }
    }

    /**
     * Return a string representation of this SequenceType
     *
     * @return the string representation as an instance of the XPath
     * SequenceType construct
     */
    public String toString() {
        if (cardinality == StaticProperty.ALLOWS_ZERO) {
            return "empty-sequence()";
        } else {
            return primaryType + Cardinality.getOccurrenceIndicator(cardinality);
        }
    }

    /**
     * Return a string representation of this SequenceType suitable for use in stylesheet
     * export files. This differs from the result of toString() in that it will not contain
     * any references to anonymous types. Note that it may also use the Saxon extended syntax
     * for union types and tuple types.
     *
     * @return the string representation as an instance of the XPath SequenceType construct
     */
    public String toExportString() {
        if (cardinality == StaticProperty.ALLOWS_ZERO) {
            return "empty-sequence()";
        } else {
            return primaryType.toExportString() + Cardinality.getOccurrenceIndicator(cardinality);
        }
    }

    public String toAlphaCode() {
        return AlphaCode.fromSequenceType(this);
    }

    /**
     * Returns a hash code value for the object.
     */
    public int hashCode() {
        return primaryType.hashCode() ^ cardinality;
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     */
    public boolean equals(/*@NotNull*/ Object obj) {
        return obj instanceof SequenceType &&
                this.primaryType.equals(((SequenceType) obj).primaryType) &&
                this.cardinality == ((SequenceType) obj).cardinality;
    }

    public boolean isSameType(SequenceType other, TypeHierarchy th) {
        return cardinality == other.cardinality && th.relationship(primaryType, other.primaryType) == Affinity.SAME_TYPE;
    }




}

