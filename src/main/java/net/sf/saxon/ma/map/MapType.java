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
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

import java.util.Optional;

/**
 * An instance of this class represents a specific map item type, for example
 * map(x:integer, element(employee))
 */
public class MapType extends AnyFunctionType {

    public final static MapType ANY_MAP_TYPE = new MapType(BuiltInAtomicType.ANY_ATOMIC, SequenceType.ANY_SEQUENCE);

    // The type of a map with no entries. It's handled specially in some static type inferencing rules
    public final static MapType EMPTY_MAP_TYPE = new MapType(BuiltInAtomicType.ANY_ATOMIC, SequenceType.ANY_SEQUENCE, true);

    /**
     * A type that allows a sequence of zero or one map items
     */
    public static final SequenceType OPTIONAL_MAP_ITEM =
            SequenceType.makeSequenceType(ANY_MAP_TYPE, StaticProperty.ALLOWS_ZERO_OR_ONE);
    public static final SequenceType SINGLE_MAP_ITEM =
            SequenceType.makeSequenceType(ANY_MAP_TYPE, StaticProperty.ALLOWS_ONE);
    public static final SequenceType SEQUENCE_OF_MAPS =
            SequenceType.makeSequenceType(ANY_MAP_TYPE, StaticProperty.ALLOWS_ZERO_OR_MORE);

    private AtomicType keyType;
    private SequenceType valueType;
    private boolean mustBeEmpty;

    public MapType(AtomicType keyType, SequenceType valueType) {
        this.keyType = keyType;
        this.valueType = valueType;
        this.mustBeEmpty = false;
    }

    public MapType(AtomicType keyType, SequenceType valueType, boolean mustBeEmpty) {
        this.keyType = keyType;
        this.valueType = valueType;
        this.mustBeEmpty = mustBeEmpty;
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
     * Get the type of the keys
     * @return the type to which all keys must conform
     */

    public AtomicType getKeyType() {
        return keyType;
    }

    /**
     * Get the type of the indexed values
     * @return the type to which all associated values must conform
     */

    public SequenceType getValueType() {
        return valueType;
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
     * Ask whether values of this type are atomizable
     *
     * @return true if some or all instances of this type can be successfully atomized; false
     * * if no instances of this type can be atomized
     * @param th The type hierarchy cache
     */

    @Override
    public boolean isAtomizable(TypeHierarchy th) {
        return false; 
    }

    /**
     * Get the default priority when this ItemType is used as an XSLT pattern
     *
     * @return the default priority
     */
    @Override
    public double getDefaultPriority() {
        return keyType.getNormalizedDefaultPriority() * valueType.getPrimaryType().getNormalizedDefaultPriority();
    }

    /**
     * Test whether a given item conforms to this type
     *
     * @param item The item to be tested
     * @param th
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item, TypeHierarchy th) {
        if (!(item instanceof MapItem)){
            return false;
        }
        if (((MapItem) item).isEmpty()) {
            return true;
        } else if (mustBeEmpty) {
            return false;
        }
        if (this == ANY_MAP_TYPE) {
            return true;
        } else {
            return ((MapItem)item).conforms(keyType, valueType, th);
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
        return new SequenceType[]{SequenceType.makeSequenceType(BuiltInAtomicType.ANY_ATOMIC, StaticProperty.EXACTLY_ONE)};
    }

    /**
     * Get the result type of this map, viewed as a function
     *
     * @return the result type of this map, viewed as a function
     */

    @Override
    public SequenceType getResultType() {
        // a function call on this map can always return ()
        if (Cardinality.allowsZero(valueType.getCardinality())) {
            return valueType;
        } else {
            return SequenceType.makeSequenceType(
                    valueType.getPrimaryType(), Cardinality.union(valueType.getCardinality(), StaticProperty.ALLOWS_ZERO));
        }
    }

    /**
     * Produce a representation of this type name for use in error messages.
     *
     * @return a string representation of the type, in notation resembling but not necessarily
     *         identical to XPath syntax
     */
    public String toString() {
        if (this == ANY_MAP_TYPE) {
            return "map(*)";
        } else if (this == EMPTY_MAP_TYPE) {
            return "map{}";
        } else {
            FastStringBuffer sb = new FastStringBuffer(100);
            sb.append("map(");
            sb.append(keyType.toString());
            sb.append(", ");
            sb.append(valueType.toString());
            sb.append(")");
            return sb.toString();
        }
    }

    /**
     * Return a string representation of this ItemType suitable for use in stylesheet
     * export files. This differs from the result of toString() in that it will not contain
     * any references to anonymous types. Note that it may also use the Saxon extended syntax
     * for union types and tuple types. The default implementation returns the result of
     * calling {@code #toString()}.
     *
     * @return the string representation as an instance of the XPath SequenceType construct
     */
    @Override
    public String toExportString() {
        if (this == ANY_MAP_TYPE) {
            return "map(*)";
        } else if (this == EMPTY_MAP_TYPE) {
            return "map{}";
        } else {
            FastStringBuffer sb = new FastStringBuffer(100);
            sb.append("map(");
            sb.append(keyType.toExportString());
            sb.append(", ");
            sb.append(valueType.toExportString());
            sb.append(")");
            return sb.toString();
        }
    }

    /**
     * Test whether this function type equals another function type
     */

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof MapType) {
            MapType f2 = (MapType) other;
            return keyType.equals(f2.keyType) && valueType.equals(f2.valueType) && mustBeEmpty == f2.mustBeEmpty;
        }
        return false;
    }

    /**
     * Returns a hash code value for the object.
     */
    @Override
    public int hashCode() {
        return keyType.hashCode() ^ valueType.hashCode();
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
        } else if (equals(other)) {
            return Affinity.SAME_TYPE;
        } else if (other == MapType.ANY_MAP_TYPE) {
            return Affinity.SUBSUMED_BY;
        } else if (other.isArrayType()) {
            return Affinity.DISJOINT;
        } else if (other instanceof TupleItemType) {
            return TypeHierarchy.inverseRelationship(other.relationship(this, th));
        } else if (other instanceof MapType) {
            // See bug 3720. Two map types can never be disjoint because the empty
            // map is an instance of every map type
            MapType f2 = (MapType) other;
            Affinity keyRel = th.relationship(keyType, f2.keyType);
            if (keyRel == Affinity.DISJOINT) {
                return Affinity.OVERLAPS;
            }
            Affinity valueRel = th.sequenceTypeRelationship(valueType, f2.valueType);

            if (valueRel == Affinity.DISJOINT) {
                return Affinity.OVERLAPS;
            }
            if (keyRel == valueRel) {
                return keyRel;
            }
            if ((keyRel == Affinity.SAME_TYPE || keyRel == Affinity.SUBSUMES) &&
                    (valueRel == Affinity.SAME_TYPE || valueRel == Affinity.SUBSUMES)) {
                return Affinity.SUBSUMES;
            }
            if ((keyRel == Affinity.SAME_TYPE || keyRel == Affinity.SUBSUMED_BY) &&
                    (valueRel == Affinity.SAME_TYPE || valueRel == Affinity.SUBSUMED_BY)) {
                return Affinity.SUBSUMED_BY;
            }
            return Affinity.OVERLAPS;
        } else {
            // see Bug #4692
            SequenceType st = getResultType();
            if (!Cardinality.allowsZero(st.getCardinality())) {
                st = SequenceType.makeSequenceType(st.getPrimaryType(), Cardinality.union(st.getCardinality(), StaticProperty.ALLOWS_ZERO));
            }
            return new SpecificFunctionType(
                        new SequenceType[]{SequenceType.ATOMIC_SEQUENCE}, st)
                    .relationship(other, th);
        }
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
        if (item instanceof MapItem) {
            for (KeyValuePair kvp : ((MapItem)item).keyValuePairs()) {
                if (!keyType.matches(kvp.key, th)) {
                    String s = "The map contains a key (" + kvp.key + ") of type " + kvp.key.getItemType() +
                            " that is not an instance of the required type " + keyType;
                    return Optional.of(s);
                }
                try {
                    if (!valueType.matches(kvp.value, th)) {
                        String s = "The map contains an entry with key (" + kvp.key +
                                ") whose corresponding value (" + Err.depictSequence(kvp.value) +
                                ") is not an instance of the required type " + valueType;
                        Optional<String> more = valueType.explainMismatch(kvp.value, th);
                        if (more.isPresent()) {
                            s = s + ". " + more.get();
                        }
                        return Optional.of(s);
                    }
                } catch (XPathException e) {
                    // continue
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
