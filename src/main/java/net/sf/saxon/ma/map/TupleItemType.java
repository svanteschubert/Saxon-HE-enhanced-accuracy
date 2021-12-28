////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.Genre;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import java.util.*;
import java.util.function.Function;

/**
 * An instance of this class represents a specific tuple item type, for example
 * tuple(x as xs:double, y as element(employee)).
 *
 * Tuple types are a Saxon extension introduced in Saxon 9.8. The syntax for constructing
 * a tuple type requires Saxon-PE or higher, but the supporting code is included in
 * Saxon-HE for convenience.
 *
 * Extended in 10.0 to distinguish extensible vs non-extensible tuple types. Extensible tuple
 * types permit fields other than those listed to appear; non-extensible tuple types do not.
 * An extensible tuple type is denoted by tuple(... ,*).
 */
public class TupleItemType extends AnyFunctionType implements TupleType {

    private Map<String, SequenceType> fields = new HashMap<>();
    private boolean extensible;

    public TupleItemType(List<String> names, List<SequenceType> types, boolean extensible) {
        for (int i=0; i<names.size(); i++) {
            fields.put(names.get(i), types.get(i));
        }
        this.extensible = extensible;
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
     * Get the names of all the fields
     *
     * @return the names of the fields (in arbitrary order)
     */
    @Override
    public Iterable<String> getFieldNames() {
        return fields.keySet();
    }

    /**
     * Get the type of a given field
     * @param field the name of the field
     * @return the type of the field if it is defined, or null otherwise
     */

    @Override
    public SequenceType getFieldType(String field) {
        return fields.get(field);
    }

    /**
     * Ask whether the tuple type is extensible, that is, whether fields other than those named are permitted
     *
     * @return true if fields other than the named fields are permitted to appear
     */
    @Override
    public boolean isExtensible() {
        return extensible;
    }

    /**
     * Test whether a given item conforms to this type
     *
     * @param item The item to be tested
     * @param th type hierarchy data
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item, TypeHierarchy th) throws XPathException {
        if (!(item instanceof MapItem)) {
            return false;
        }
        MapItem map = (MapItem)item;
        for (Map.Entry<String, SequenceType> field : fields.entrySet()) {
            Sequence val = map.get(new StringValue(field.getKey()));
            if (val == null) {
                val = EmptySequence.getInstance();
            }
            if (!field.getValue().matches(val, th)) {
                return false;
            }
        }
        if (!extensible) {
            AtomicIterator<? extends AtomicValue> keyIter = map.keys();
            AtomicValue key;
            while ((key = keyIter.next()) != null) {
                if (!(key instanceof StringValue) || !fields.containsKey(key.getStringValue())) {
                    return false;
                }
            }
        }
        return true;
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
     * Get the result type of this tuple type, viewed as a function
     *
     * @return the result type of this tuple type, viewed as a function
     */

    @Override
    public SequenceType getResultType() {
        if (extensible) {
            return SequenceType.ANY_SEQUENCE;
        } else {
            ItemType resultType = null;
            boolean allowsMany = false;
            for (Map.Entry<String, SequenceType> field : fields.entrySet()) {
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
        // TODO: this algorithm means that adding fields to the tuple type reduces its priority, which is wrong
        double prio = 1;
        for (SequenceType st : fields.values()) {
            prio *= st.getPrimaryType().getNormalizedDefaultPriority();
        }
        return extensible ? 0.5 + prio/2 : prio;
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
     * for union types and tuple types.
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
        return "FM";
    }

    /**
     * Return a string representation of the tuple type
     * @param show a function to use for converting the types of the component fields to strings
     * @return the string representation
     */

    private String makeString(Function<SequenceType, String> show) {
        FastStringBuffer sb = new FastStringBuffer(100);
        sb.append("tuple(");
        boolean first = true;
        for (Map.Entry<String, SequenceType> field : fields.entrySet()) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(field.getKey());
            sb.append(": ");
            sb.append(show.apply(field.getValue()));
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
                other instanceof TupleItemType
                        && extensible == ((TupleItemType) other).extensible
                        && fields.equals(((TupleItemType) other).fields);
    }

    /**
     * Returns a hash code value for the object.
     */
    @Override
    public int hashCode() {
        return fields.hashCode();
    }

    /**
     * Determine the relationship of one function item type to another
     *
     * @return for example {@link Affinity#SUBSUMES}, {@link Affinity#SAME_TYPE}
     */

    @Override
    public Affinity relationship(FunctionItemType other, TypeHierarchy th) {
        if (other == AnyFunctionType.getInstance()) {
            return Affinity.SUBSUMED_BY;
        } else if (other instanceof TupleItemType) {
            return tupleTypeRelationship((TupleItemType)other, th);
        } else if (other == MapType.ANY_MAP_TYPE) {
            return Affinity.SUBSUMED_BY;
        } else if (other.isArrayType()) {
            return Affinity.DISJOINT;
        } else if (other instanceof MapType) {
            return tupleToMapRelationship((MapType)other, th);
        } else {
            Affinity rel;
            rel = new SpecificFunctionType(getArgumentTypes(), getResultType()).relationship(other, th);
            return rel;
        }
    }

    private Affinity tupleToMapRelationship(MapType other, TypeHierarchy th) {
        AtomicType tupleKeyType = isExtensible() ? BuiltInAtomicType.ANY_ATOMIC : BuiltInAtomicType.STRING;
        Affinity keyRel = th.relationship(tupleKeyType, other.getKeyType());
        if (keyRel == Affinity.DISJOINT) {
            return Affinity.DISJOINT;
        }
        // Handle map(xxx, item()*)
        if (other.getValueType().getPrimaryType().equals(AnyItemType.getInstance()) && other.getValueType().getCardinality() == StaticProperty.ALLOWS_ZERO_OR_MORE) {
            if (keyRel == Affinity.SUBSUMED_BY || keyRel == Affinity.SAME_TYPE) {
                return Affinity.SUBSUMED_BY;
            } else {
                return Affinity.OVERLAPS;
            }
        } else if (isExtensible()) {
            return Affinity.OVERLAPS;
        } else {
            // The type of every field in the tuple must be a subtype of the map value type
            for (SequenceType entry : fields.values()) {
                Affinity rel = th.sequenceTypeRelationship(entry, other.getValueType());
                if (!(rel == Affinity.SUBSUMED_BY || rel == Affinity.SAME_TYPE)) {
                    return Affinity.OVERLAPS;
                }
            }
            return Affinity.SUBSUMED_BY;
        }
    }

    private Affinity tupleTypeRelationship(TupleItemType other, TypeHierarchy th) {
        Set<String> keys = new HashSet<>(fields.keySet());
        keys.addAll(other.fields.keySet());
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
            SequenceType t1 = fields.get(key);
            SequenceType t2 = other.fields.get(key);
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
                Affinity a = th.sequenceTypeRelationship(t1, t2);
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
            for (Map.Entry<String, SequenceType> entry : fields.entrySet()) {
                String key = entry.getKey();
                SequenceType required = entry.getValue();
                GroundedValue value = ((MapItem) item).get(new StringValue(key));
                if (value == null) {
                    if (!Cardinality.allowsZero(required.getCardinality())) {
                        return Optional.of("Field " + key + " is absent; it must have a value");
                    }
                } else {
                    try {
                        if (!required.matches(value, th)) {
                            String s = "Field " + key + " has value "
                                    + Err.depictSequence(value)
                                    + " which does not match the required type "
                                    + required.toString();
                            Optional<String> more = required.explainMismatch(value, th);
                            if (more.isPresent()) {
                                s += ". " + more.get();
                            }
                            return Optional.of(s);
                        }
                    } catch (XPathException err) {
                        // shouldn't happen
                        return Optional.empty();
                    }
                }
            }
            if (!extensible) {
                AtomicIterator<? extends AtomicValue> keyIter = ((MapItem)item).keys();
                AtomicValue key;
                while ((key = keyIter.next()) != null) {
                    if (!(key instanceof StringValue)) {
                        return Optional.of("Undeclared field " + key + " is present, but it is not a string, and the tuple type is not extensible");
                    } else if (!fields.containsKey(key.getStringValue())) {
                        return Optional.of("Undeclared field " + key + " is present, but the tuple type is not extensible");
                    }
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Expression makeFunctionSequenceCoercer(Expression exp, RoleDiagnostic role)
            throws XPathException {
        return new SpecificFunctionType(getArgumentTypes(), getResultType()).makeFunctionSequenceCoercer(exp, role);
    }

}

// Copyright (c) 2011-2020 Saxonica Limited
