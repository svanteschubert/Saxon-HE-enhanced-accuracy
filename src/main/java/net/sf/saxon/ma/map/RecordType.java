////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.*;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.type.coercion.CoercionPlan;
import net.sf.saxon.type.coercion.RecordCoercionPlan;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An instance of this class represents a specific record item type, for example
 * record(x as xs:double, y as element(employee)).
 *
 * Record types are a proposed extension for XPath 4.0. They were previously introduced
 * as a Saxon extension in Saxon 9.8, under the name "tuple types". The syntax for constructing
 * a record type requires Saxon-PE or higher, but the supporting code is included in
 * Saxon-HE for convenience.
 *
 * Extended in 10.0 to distinguish extensible vs non-extensible record types. Extensible record
 * types permit fields other than those listed to appear; non-extensible record types do not.
 * An extensible record type is denoted by record(... ,*).
 */
public class RecordType implements FunctionItemType {

    private final Map<String, SequenceType> fieldTypes = new HashMap<>();
    private final List<String> fieldNamesInOrder = new ArrayList<>();
    private final Set<String> optionalFields = new HashSet<>();
    private boolean _extensible;
    private StructuredQName name;

    public static RecordType VALUE_RECORD = nonExtensible(
            new Field("value", SequenceType.ANY_SEQUENCE, false)
    );
    public static RecordType KEY_VALUE_RECORD = nonExtensible(
            new Field("key", SequenceType.ATOMIC_SEQUENCE, false),
            new Field("value", SequenceType.ANY_SEQUENCE, false)
    );

    public final static RecordType SCHEMA_TYPE_RECORD = new RecordType(new StructuredQName("fn", NamespaceUri.FN, "schema-type-record"));

    static {
        SCHEMA_TYPE_RECORD.setDetails(
                true,
                f("name", SequenceType.SINGLE_QNAME, false),
                f("is-simple", SequenceType.SINGLE_BOOLEAN, false),
                f("base-type", SequenceType.one(
                        new SpecificFunctionType(SequenceType.optional(SCHEMA_TYPE_RECORD))), false),
                f("primitive-type", SequenceType.one(
                        new SpecificFunctionType(SequenceType.one(SCHEMA_TYPE_RECORD))), true),
                f("variety", SequenceType.one(
                        EnumerationUnionType.of("atomic", "list", "union", "empty", "simple", "element-only", "mixed")), true),
                f("members", SequenceType.one(
                        new SpecificFunctionType(SequenceType.zeroOrMore(SCHEMA_TYPE_RECORD))), true),
                f("simple-content-type", SequenceType.one(
                        new SpecificFunctionType(SequenceType.one(SCHEMA_TYPE_RECORD))), true),
                f("matches", SequenceType.one(
                        new SpecificFunctionType(SequenceType.SINGLE_ATOMIC,
                                                 SequenceType.SINGLE_BOOLEAN)), true),
                f("constructor", SequenceType.one(
                        new SpecificFunctionType(SequenceType.OPTIONAL_ATOMIC,
                                                 SequenceType.OPTIONAL_ATOMIC)), true)
        );
    }


    private static RecordType.Field f(String name, SequenceType type, boolean optional) {
        return new RecordType.Field(name, type, optional);
    }

    /**
     * Class representing one field definition within a record type definition,
     * used only while constructing a record definition
     */
    public static class Field {
        String name;
        SequenceType type;
        boolean optional;

        public Field(String name, SequenceType type, boolean optional) {
            this.name = name;
            this.type = type;
            this.optional = optional;
        }
    }

    /**
     * Construct a dummy RecordTest, details to be supplied later
     */

    public RecordType() {}

    /**
     * Construct a named RecordTest, details to be supplied later
     */

    public RecordType(StructuredQName name) {
        this.name = name;
    }

    /**
     * Construct a RecordTest
     * @param names the names of the fields
     * @param types the types of the fields
     * @param optionalFieldNames a list of the names of the fields that are declared optional
     * @param extensible indicates whether the RecordTest is extensible (allows fields other
     *                   than those declared)
     */

    public RecordType(List<String> names, List<SequenceType> types, Collection<String> optionalFieldNames, boolean extensible) {
        setDetails(names, types, optionalFieldNames, extensible);
    }

    /**
     * Construct a named RecordTest
     *
     * @param names              the names of the fields
     * @param types              the types of the fields
     * @param optionalFieldNames a list of the names of the fields that are declared optional
     * @param extensible         indicates whether the RecordTest is extensible (allows fields other
     *                           than those declared)
     * @param name               the name of the record type
     */

    public RecordType(List<String> names, List<SequenceType> types, Collection<String> optionalFieldNames, boolean extensible, StructuredQName name) {
        setDetails(names, types, optionalFieldNames, extensible);
        this.name = name;
    }

    /**
     * Make an extensible record type
     * @param fields the fields of the record type
     * @return the record type
     */
    public static RecordType extensible(Field... fields) {
        return makeRecordTest(true, fields);
    }

    /**
     * Make a non-extensible record type
     * @param fields the fields of the record type
     * @return the record type
     */
    public static RecordType nonExtensible(Field... fields) {
        return makeRecordTest(false, fields);
    }

    private static RecordType makeRecordTest(boolean extensible, Field... fields) {
        List<String> fieldNames = new ArrayList<>(fields.length);
        List<String> optionalFieldNames = new ArrayList<>(fields.length);
        List<SequenceType> fieldTypes = new ArrayList<>(fields.length);
        for (Field field : fields) {
            fieldNames.add(field.name);
            fieldTypes.add(field.type);
            if (field.optional) {
                optionalFieldNames.add(field.name);
            }
        }
        return new RecordType(fieldNames, fieldTypes, optionalFieldNames, extensible);
    }

    /**
     * Supply the details of the RecordTest. This method is only to be used during initialisation,
     * it is needed so that a RecordTest can refer to itself. Apart from this, the RecordTest
     * is immutable.
     */
    public void setDetails(boolean extensible, Field... fields) {
        this._extensible = extensible;
        for (Field field : fields) {
            fieldNamesInOrder.add(field.name);
            fieldTypes.put(field.name, field.type);
            if (field.optional) {
                optionalFields.add(field.name);
            }
        }
    }

    /**
     * Supply the details of the RecordTest. This method is only to be used during initialisation,
     * it is needed so that a RecordTest can refer to itself. Apart from this, the RecordTest
     * is immutable.
     * @param names the names of the fields
     * @param types the types of the fields
     * @param optionalFieldNames a list of the names of the fields that are declared optional
     * @param extensible indicates whether the RecordTest is extensible (allows fields other
     *                   than those declared)
     */
    public void setDetails(List<String> names, List<SequenceType> types, Collection<String> optionalFieldNames, boolean extensible) {
        for (int i = 0; i < names.size(); i++) {
            fieldNamesInOrder.add(names.get(i));
            fieldTypes.put(names.get(i), types.get(i));
        }
        optionalFields.addAll(optionalFieldNames);
        this._extensible = extensible;
    }

    /**
     * Determine the Genre (top-level classification) of this type
     *
     * @return the Genre to which this type belongs, specifically {@link Genre#MAP}
     */
    @Override
    public Genre getGenre() {
        return Genre.MAP;
    }

    /**
     * Ask whether this function item type is a map type. In this case function coercion (to the map type)
     * will never succeed.
     *
     * @return true if this FunctionItemType is a map type
     */
    @Override
    public boolean isMapType() {
        return true;
    }

    /**
     * Ask whether this function item type is an array type. In this case function coercion (to the array type)
     * will never succeed.
     *
     * @return true if this FunctionItemType is an array type
     */
    @Override
    public boolean isArrayType() {
        return false;
    }

    /**
     * Get the name of the record type, if it has one
     *
     * @return the name of the record type, or null if it has no name
     */

    public StructuredQName getName() {
        return name;
    }

    /**
     * Get the names of all the fields
     *
     * @return the names of the fields (in retained order)
     */

    public Iterable<String> getFieldNames() {
        return fieldNamesInOrder;
    }

    /**
     * Get the number of declared fields
     * @return the number of declared fields
     */

    public int getSize() {
        return fieldNamesInOrder.size();
    }

    /**
     * Get the type of a given field
     * @param field the name of the field
     * @return the type of the field if it is defined, or null otherwise
     */

    public SequenceType getFieldType(String field) {
        return fieldTypes.get(field);
    }

    /**
     * Ask whether a given field is optional
     * @param field the name of the field
     * @return true if the field is defined as an optional field
     */

    public boolean isOptionalField(String field) {
        return optionalFields.contains(field);
    }

    /**
     * Ask whether the record type is extensible, that is, whether fields other than those named are permitted
     *
     * @return true if fields other than the named fields are permitted to appear
     */
    public boolean isExtensible() {
        return _extensible;
    }

    /**
     * Test whether a given item conforms to this type
     *
     * @param item The item to be tested
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item) {
        if (item instanceof MapItem map) {
            for (Map.Entry<String, SequenceType> field : fieldTypes.entrySet()) {
                GroundedValue val = map.get(new StringValue(field.getKey()));
                if (val == null) {
                    if (!isOptionalField(field.getKey())) {
                        return false;
                    }
                } else if (!field.getValue().matches(val)) {
                    return false;
                }
            }
            if (!_extensible) {
                AtomicIterator keyIter = map.keys();
                AtomicValue key;
                while ((key = keyIter.next()) != null) {
                    if (!(key instanceof StringValue) || !fieldTypes.containsKey(key.getStringValue())) {
                        return false;
                    }
                }
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get the arity (number of arguments) of this function type
     *
     * @return the number of argument types in the function signature
     */

    public int getArity() {
        return 1;
    }

    /**
     * Get the argument types of this map, viewed as a function
     *
     * @return the list of argument types of this map, viewed as a function
     */

    @Override
    public SequenceType[] getArgumentTypes() {
        // regardless of the key type, a function call on this map can supply any atomic value
        return new SequenceType[]{SequenceType.SINGLE_ATOMIC};
    }

    /**
     * Get the result type of this record type, viewed as a function
     *
     * @return the result type of this record type, viewed as a function
     */

    @Override
    public SequenceType getResultType() {
        if (_extensible) {
            return SequenceType.ANY_SEQUENCE;
        } else {
            ItemType resultType = null;
            boolean allowsMany = false;
            for (Map.Entry<String, SequenceType> field : fieldTypes.entrySet()) {
               if (resultType == null) {
                   resultType = field.getValue().getPrimaryType();
               } else {
                   resultType = Type.getCommonSuperType(resultType, field.getValue().getPrimaryType());
               }
               allowsMany = allowsMany || Cardinality.allowsMany(field.getValue().getCardinality());
            }
            return SequenceType.makeSequenceType(resultType,
                                                 allowsMany ? StaticProperty.ALLOWS_ZERO_OR_MORE : StaticProperty.ALLOWS_ZERO_OR_ONE);
        }
    }

    /**
     * Get the default priority when this ItemType is used as an XSLT pattern
     *
     * @return the default priority
     */
    @Override
    public double getDefaultPriority() {
        if (fieldTypes.isEmpty() && isExtensible()) {
            return -0.25;
        } else {
            return 0.0;
        }
    }

    /**
     * Produce a representation of this type name for use in error messages.
     *
     * @return a string representation of the type, in notation resembling but not necessarily
     * identical to XPath syntax
     */
    public String toString() {
        return makeString(SequenceType::toString);
    }

    /**
     * Return a string representation of this ItemType suitable for use in stylesheet
     * export files. This differs from the result of toString() in that it will not contain
     * any references to anonymous types. Note that it may also use the Saxon extended syntax
     * for union types and record types.
     *
     * @return the string representation as an instance of the XPath ItemType construct
     */
    @Override
    public String toExportString() {
        return makeString(SequenceType::toExportString);
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
       return  name == null ? "FM" : "FMR";
    }

    /**
     * Return a string representation of the record type
     * @param show a function to use for converting the types of the component fields to strings
     * @return the string representation
     */

    private String makeString(Function<SequenceType, String> show) {
        if (name != null) {
            return "record " + name.getEQName() + "()";
        }
        StringBuilder sb = new StringBuilder(100);
        sb.append("record(");
        boolean first = true;
        for (String key : fieldNamesInOrder) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            if (NameChecker.isValidNCName(key)) {
                sb.append(key);
            } else {
                sb.append('"').append(key).append('"');
            }
            if (isOptionalField(key)) {
                sb.append('?');
            }
            sb.append(" as ");
            SequenceType fieldType = fieldTypes.get(key);
            if (fieldType.getPrimaryType() == this) {
                sb.append("..").append(Cardinality.getOccurrenceIndicator(fieldType.getCardinality()));
            } else {
                sb.append(show.apply(fieldType));
            }
        }
        if (isExtensible()) {
            sb.append(", *");
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Test whether this function type equals another function type
     */

    public boolean equals(Object other) {
        return this == other ||
                other instanceof RecordType
                        && _extensible == ((RecordType) other)._extensible
                        && fieldTypes.equals(((RecordType) other).fieldTypes)
                        && optionalFields.equals(((RecordType) other).optionalFields);
    }

    @Override
    public ItemType normalizeItemType() {
        if (fieldTypes.isEmpty()) {
            return _extensible ? MapType.ANY_MAP_TYPE : MapType.EMPTY_MAP_TYPE;
        }
        return this;
    }

    /**
     * Returns a hash code value for the object.
     */
    @Override
    public int hashCode() {
        // Need to avoid infinite recursion for recursive record types
        int h = 0x27ca481f;
        if (name != null) {
            return h ^ name.hashCode();
        }
        for (Map.Entry<String, SequenceType> entry : fieldTypes.entrySet()) {
            h ^= entry.getKey().hashCode();
            if (entry.getValue().getPrimaryType() == this) {
                h ^= 0x05050505;
            } else {
                h ^= entry.getValue().hashCode();
            }
        }
        return h;
    }

    /**
     * Determine the relationship of one function item type to another
     *
     * @return for example {@link Affinity#SUBSUMES}, {@link Affinity#SAME_TYPE}
     */

    @Override
    public Affinity relationship(FunctionItemType other) {
        if (other == AnyFunctionType.INSTANCE) {
            return Affinity.SUBSUMED_BY;
        } else if (other instanceof RecordType) {
            return recordTypeRelationship((RecordType)other);
        } else if (other == MapType.ANY_MAP_TYPE) {
            return Affinity.SUBSUMED_BY;
        } else if (other.isArrayType()) {
            return Affinity.DISJOINT;
        } else if (other instanceof MapType) {
            return recordToMapRelationship((MapType)other);
        } else {
            Affinity rel;
            rel = new SpecificFunctionType(getArgumentTypes(), getResultType()).relationship(other);
            return rel;
        }
    }

    private Affinity recordToMapRelationship(MapType other) {
        AtomicType recordKeyType = isExtensible() ? BuiltInAtomicType.ANY_ATOMIC : BuiltInAtomicType.STRING;
        Affinity keyRel = Subsumption.computeRelationship(recordKeyType, other.getKeyType());
        if (keyRel == Affinity.DISJOINT) {
            return Affinity.DISJOINT;
        }
        // Handle map(xxx, item()*)
        if (other.getValueType().getPrimaryType().equals(AnyItemType.INSTANCE) && other.getValueType().getCardinality() == StaticProperty.ALLOWS_ZERO_OR_MORE) {
            if (keyRel == Affinity.SUBSUMED_BY || keyRel == Affinity.SAME_TYPE) {
                return Affinity.SUBSUMED_BY;
            } else {
                return Affinity.OVERLAPS;
            }
        } else if (isExtensible()) {
            return Affinity.OVERLAPS;
        } else {
            // The type of every field in the record must be a subtype of the map value type
            for (SequenceType entry : fieldTypes.values()) {
                Affinity rel = Subsumption.sequenceTypeRelationship(entry, other.getValueType());
                if (!(rel == Affinity.SUBSUMED_BY || rel == Affinity.SAME_TYPE)) {
                    return Affinity.OVERLAPS;
                }
            }
            return Affinity.SUBSUMED_BY;
        }
    }

    private Affinity recordTypeRelationship(RecordType other) {
        Set<String> keys = new HashSet<>(fieldTypes.keySet());
        keys.addAll(other.fieldTypes.keySet());
        boolean foundSubsuming = false;
        boolean foundSubsumed = false;
        boolean foundOverlap = false;
        if (isExtensible()) {
            if (!other.isExtensible()) {
                foundSubsuming = true;
            }
        } else if (other.isExtensible()) {
            foundSubsumed = true;
        }
        for (String key : keys) {
            SequenceType t1 = fieldTypes.get(key);
            SequenceType t2 = other.fieldTypes.get(key);
            boolean optional1 = isOptionalField(key);
            boolean optional2 = other.isOptionalField(key);
            if (optional1 != optional2) {
                if (optional1) {
                    foundSubsuming = true;
                }
                if (optional2) {
                    foundSubsumed = true;
                }
            }
            if (t1 == null) {
                if (isExtensible()) {
                    foundSubsuming = true;
                } else if (Cardinality.allowsZero(t2.getCardinality())) {
                    foundOverlap = true;
                } else {
                    return Affinity.DISJOINT;
                }
            } else if (t2 == null) {
                if (other.isExtensible()) {
                    foundSubsumed = true;
                } else if (Cardinality.allowsZero(t1.getCardinality())) {
                    foundOverlap = true;
                } else {
                    return Affinity.DISJOINT;
                }
            } else {
                Affinity a = Subsumption.sequenceTypeRelationship(t1, t2);
                switch (a) {
                    case SAME_TYPE:
                        break;
                    case SUBSUMED_BY:
                        foundSubsumed = true;
                        break;
                    case SUBSUMES:
                        foundSubsuming = true;
                        break;
                    case OVERLAPS:
                        foundOverlap = true;
                        break;
                    case DISJOINT:
                        return Affinity.DISJOINT;
                }
            }
        }
        if (foundOverlap || (foundSubsumed && foundSubsuming)) {
            return Affinity.OVERLAPS;
        } else if (foundSubsuming) {
            return Affinity.SUBSUMES;
        } else if (foundSubsumed) {
            return Affinity.SUBSUMED_BY;
        } else {
            return Affinity.SAME_TYPE;
        }
    }

    /**
     * Get extra diagnostic information about why a supplied item does not conform to this
     * item type, if available. If extra information is returned, it should be in the form of a complete
     * sentence, minus the closing full stop. No information should be returned for obvious cases.
     *
     * @param item the item being matched
     * @param th the type hierarchy cache
     */
    @Override
    public Optional<String> explainMismatch(Item item, TypeHierarchy th) {
        if (item instanceof MapItem) {
            for (Map.Entry<String, SequenceType> entry : fieldTypes.entrySet()) {
                String key = entry.getKey();
                SequenceType required = entry.getValue();
                GroundedValue value = ((MapItem) item).get(new StringValue(key));
                if (value == null) {
                    if (!Cardinality.allowsZero(required.getCardinality()) && !isOptionalField(key)) {
                        return Optional.of("Field " + key + " is absent; it must have a value");
                    }
                } else {
                    if (!required.matches(value)) {
                        String s = "Field " + key + " has value "
                                + Err.depictSequence(value)
                                + " which does not match the required type "
                                + required;
                        Optional<String> more = required.explainMismatch(value, th);
                        if (more.isPresent()) {
                            s += ". " + more.get();
                        }
                        return Optional.of(s);
                    }
                }
            }
            if (!_extensible) {
                AtomicIterator keyIter = ((MapItem)item).keys();
                AtomicValue key;
                while ((key = keyIter.next()) != null) {
                    if (!(key instanceof StringValue)) {
                        return Optional.of("Undeclared field " + key + " is present, but it is not a string, and the record type is not extensible");
                    } else if (!fieldTypes.containsKey(key.getStringValue())) {
                        return Optional.of("Undeclared field " + key + " is present, but the record type is not extensible");
                    }
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Expression makeFunctionSequenceCoercer(Expression exp, Supplier<RoleDiagnostic> role, boolean allow40)
            throws XPathException {
        return new SpecificFunctionType(getArgumentTypes(), getResultType()).makeFunctionSequenceCoercer(exp, role, false);
    }

    /**
     * Get the coercion plan for use when this type is the required type for (say) coercion
     * of arguments in a function call
     *
     * @param version the XPath language version (40 or 31)
     */
    @Override
    public CoercionPlan getCoercionPlan(int version) {
        return RecordCoercionPlan.INSTANCE;
    }

    /**
     * Get the list of Annotation Assertions associated with this function item type
     *
     * @return the list of annotation assertions
     */
    @Override
    public AnnotationList getAnnotationAssertions() {
        return AnnotationList.EMPTY;
    }

    /**
     * Determine whether this item type is an atomic type
     *
     * @return true if this is ANY_ATOMIC_TYPE or a subtype thereof
     */
    @Override
    public boolean isAtomicType() {
        return false;
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
        return false;
    }

    /**
     * Get the primitive item type corresponding to this item type. For item(),
     * this is Type.ITEM. For node(), it is Type.NODE. For specific node kinds,
     * it is the value representing the node kind, for example Type.ELEMENT.
     * For anyAtomicValue and union types it is Type.ATOMIC_VALUE. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that integer, xs:dayTimeDuration, and xs:yearMonthDuration
     * are considered to be primitive types.
     *
     * @return the corresponding primitive type
     */
    @Override
    public ItemType getPrimitiveItemType() {
        return null;
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
        return 0;
    }

    /**
     * Get the corresponding {@link UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        return UType.FUNCTION;
    }

    /**
     * Get the item type of the aXPtomic values that will be produced when an item
     * of this type is atomized
     *
     * @return the best available item type of the atomic values that will be produced when an item
     * of this type is atomized, or null if it is known that atomization will throw an error.
     */
    @Override
    public PlainType getAtomizedItemType() {
        return null;
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
        return false;
    }
}

// Copyright (c) 2011-2026 Saxonica Limited
