////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.functions.hof.FunctionSequenceCoercer;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.map.KeyValuePair;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.trans.Err;
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * An instance of this class represents a specific function item type, for example
 * function(xs:int) as xs:boolean
 */
public class SpecificFunctionType extends AnyFunctionType {

    private final SequenceType[] argTypes;
    private final SequenceType resultType;
    private final AnnotationList annotations;


    public final static FunctionItemType COMPONENT_FUNCTION_TYPE =
            new SpecificFunctionType(new SequenceType[]{SequenceType.SINGLE_STRING}, SequenceType.ANY_SEQUENCE);


    public SpecificFunctionType(SequenceType[] argTypes, SequenceType resultType) {
        this.argTypes = Objects.requireNonNull(argTypes);
        this.resultType = Objects.requireNonNull(resultType);
        this.annotations = AnnotationList.EMPTY;
    }

    public SpecificFunctionType(SequenceType[] argTypes, SequenceType resultType, AnnotationList annotations) {
        this.argTypes = Objects.requireNonNull(argTypes);
        this.resultType = Objects.requireNonNull(resultType);
        this.annotations = Objects.requireNonNull(annotations);
    }

    /**
     * Get the arity (number of arguments) of this function type
     *
     * @return the number of argument types in the function signature
     */

    public int getArity() {
        return argTypes.length;
    }

    /**
     * Get the argument types
     *
     * @return the list of argument types
     */

    @Override
    public SequenceType[] getArgumentTypes() {
        return argTypes;
    }

    /**
     * Get the result type
     *
     * @return the result type
     */

    @Override
    public SequenceType getResultType() {
        return resultType;
    }

    /**
     * Get the list of annotation assertions defined on this function item type.
     *
     * @return the list of annotation assertions, or an empty list if there are none
     */

    @Override
    public AnnotationList getAnnotationAssertions() {
        return annotations;
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
        // An instance of a specific function type can be atomized only if it is an array, which
        // means there must be a single argument and it must be of type xs:integer or a supertype.
        if (getArity() != 1) {
            return false;
        }
        ItemType argType = getArgumentTypes()[0].getPrimaryType();
        return th.isSubType(BuiltInAtomicType.INTEGER, argType);
    }

    /**
     * Produce a representation of this type name for use in error messages.
     *
     * @return a string representation of the type, in notation resembling but not necessarily
     *         identical to XPath syntax
     */
    public String toString() {
        StringBuilder sb = new StringBuilder(100);
        sb.append("(function(");
        for (int i = 0; i < argTypes.length; i++) {
            sb.append(argTypes[i].toString());
            if (i < argTypes.length - 1) {
                sb.append(", ");
            }
        }
        sb.append(") as ");
        sb.append(resultType.toString());
        sb.append(')');
        return sb.toString();
    }

    @Override
    @CSharpModifiers(code = {"public", "override"})
    public String toExportString() {
        StringBuilder sb = new StringBuilder(100);
        sb.append("(function(");
        for (int i = 0; i < argTypes.length; i++) {
            sb.append(argTypes[i].toExportString());
            if (i < argTypes.length - 1) {
                sb.append(", ");
            }
        }
        sb.append(") as ");
        sb.append(resultType.toExportString());
        sb.append(')');
        return sb.toString();
    }


    /**
     * Test whether this function type equals another function type
     */

    public boolean equals(Object other) {
        if (other instanceof SpecificFunctionType) {
            SpecificFunctionType f2 = (SpecificFunctionType) other;
            if (!resultType.equals(f2.resultType)) {
                return false;
            }
            if (argTypes.length != f2.argTypes.length) {
                return false;
            }
            for (int i = 0; i < argTypes.length; i++) {
                if (!argTypes[i].equals(f2.argTypes[i])) {
                    return false;
                }
            }
            // Compare the annotations
            if (!getAnnotationAssertions().equals(f2.getAnnotationAssertions())) {
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Returns a hash code value for the object.
     */
    @Override
    public int hashCode() {
        int h = resultType.hashCode() ^ argTypes.length;
        for (SequenceType argType : argTypes) {
            h ^= argType.hashCode();
        }
        return h;
    }

    /**
     * Determine the relationship of one function item type to another. This method is only concerned
     * with the type signatures of the two function item types, and not with their annotation assertions.
     *
     * @return for example {@link Affinity#SUBSUMES}, {@link Affinity#SAME_TYPE}
     */

    @Override
    public Affinity relationship(FunctionItemType other, TypeHierarchy th) {
        if (other == AnyFunctionType.getInstance() || other instanceof AnyFunctionTypeWithAssertions) {
            return Affinity.SUBSUMED_BY;
        } else if (equals(other)) {
            return Affinity.SAME_TYPE;
        } else if (other instanceof ArrayItemType || other instanceof MapType) {
            Affinity rrel = other.relationship(this, th);
            switch (rrel) {
                case SUBSUMES:
                    return Affinity.SUBSUMED_BY;
                case SUBSUMED_BY:
                    return Affinity.SUBSUMES;
                default:
                    return rrel;
            }
        } else {
            if (argTypes.length != other.getArgumentTypes().length) {
                return Affinity.DISJOINT;
            }
            boolean wider = false;
            boolean narrower = false;
            for (int i = 0; i < argTypes.length; i++) {
                Affinity argRel = th.sequenceTypeRelationship(argTypes[i], other.getArgumentTypes()[i]);
                switch (argRel) {
                    case DISJOINT:
                        return Affinity.DISJOINT;
                    case SUBSUMES:
                        narrower = true;
                        break;
                    case SUBSUMED_BY:
                        wider = true;
                        break;
                    case OVERLAPS:
                        wider = true;
                        narrower = true;
                        break;
                    case SAME_TYPE:
                    default:
                        break;
                }
            }

            Affinity resRel = th.sequenceTypeRelationship(resultType, other.getResultType());
            switch (resRel) {
                case DISJOINT:
                    return Affinity.DISJOINT;
                case SUBSUMES:
                    wider = true;
                    break;
                case SUBSUMED_BY:
                    narrower = true;
                    break;
                case OVERLAPS:
                    wider = true;
                    narrower = true;
                    break;
                case SAME_TYPE:
                default:
                    break;
            }

            if (wider) {
                if (narrower) {
                    return Affinity.OVERLAPS;
                } else {
                    return Affinity.SUBSUMES;
                }
            } else {
                if (narrower) {
                    return Affinity.SUBSUMED_BY;
                } else {
                    return Affinity.SAME_TYPE;
                }
            }
        }
    }

    /**
     * Get the default priority when this ItemType is used as an XSLT pattern
     *
     * @return the default priority
     */
    @Override
    public double getDefaultPriority() {
        double prio = 1;
        for (SequenceType st : getArgumentTypes()) {
            prio *= st.getPrimaryType().getNormalizedDefaultPriority();
        }
        return prio;
    }

    /**
     * Test whether a given item conforms to this type
     *
     *
     * @param item The item to be tested
     * @param th the type hierarchy cache
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item, TypeHierarchy th) {
        if (!(item instanceof FunctionItem)) {
            return false;
        }


        if (item instanceof MapItem) {
            // Bug 2938: Essentially a map is an instance of function(X) as Y
            // if (a) X is a subtype of xs:anyAtomicType, and (b) all the values in the map are instances of Y
            // Bug 4692: Adds the condition that the empty sequence must be an instance of Y.
            if (getArity() == 1 &&
                    argTypes[0].getCardinality() == StaticProperty.EXACTLY_ONE &&
                    argTypes[0].getPrimaryType().isPlainType() &&
                    Cardinality.allowsZero(resultType.getCardinality())) {
                for (KeyValuePair pair : ((MapItem) item).keyValuePairs()) {
                    if (!resultType.matches(pair.value, th)) {
                        return false;
                    }
                }
                return true;
            } else {
                return false;
            }
        }

        if (item instanceof ArrayItem) {
            // Bug 2938: Essentially a array is an instance of function(X) as Y
            // if (a) X is a subtype of xs:integer, and (b) all the values in the array are instances of Y
            if (getArity() == 1 &&
                    argTypes[0].getCardinality() == StaticProperty.EXACTLY_ONE &&
                    argTypes[0].getPrimaryType().isPlainType()) {
                Affinity rel = th.relationship(argTypes[0].getPrimaryType(), BuiltInAtomicType.INTEGER);
                if (!(rel == Affinity.SAME_TYPE || rel == Affinity.SUBSUMED_BY)) {
                    return false;
                }
                for (GroundedValue member : ((ArrayItem) item).members()) {
                    if (!resultType.matches(member, th)) {
                        return false;
                    }
                }
                return true;
            } else {
                return false;
            }
        }

        Affinity affinity = th.relationship(((FunctionItem) item).getFunctionItemType(), this);
        return affinity == Affinity.SAME_TYPE || affinity == Affinity.SUBSUMED_BY;
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
    @CSharpModifiers(code={"public", "override"})
    public Optional<String> explainMismatch(Item item, TypeHierarchy th) {
        if (!(item instanceof FunctionItem)) {
            return Optional.empty();
        }

        if (item instanceof MapItem) {
            if (getArity() == 1) {
                if (argTypes[0].getCardinality() == StaticProperty.EXACTLY_ONE &&
                    argTypes[0].getPrimaryType().isPlainType()) {
                    for (KeyValuePair pair : ((MapItem) item).keyValuePairs()) {
                        if (!resultType.matches(pair.value, th)) {
                            String s = "The supplied map contains an entry with key (" + pair.key +
                                    ") whose corresponding value (" + Err.depictSequence(pair.value) +
                                    ") is not an instance of the return type in the function signature (" +
                                    resultType + ")";
                            Optional<String> more = resultType.explainMismatch(pair.value, th);
                            if (more.isPresent()) {
                                s = s + ". " + more.get();
                            }
                            return Optional.of(s);
                        }
                    }
                } else {
                    String s = "The function argument is of type " + argTypes[0] +
                            "; a map can only be supplied for a function type whose argument type is atomic";
                    return Optional.of(s);
                }
            } else {
                String s = "The function arity is " + getArity() + "; a map can only be supplied for a function type with arity 1";
                return Optional.of(s);
            }
        }

        if (item instanceof ArrayItem) {
            // Bug 2938: Essentially a array is an instance of function(X) as Y
            // if (a) X is a subtype of xs:integer, and (b) all the values in the array are instances of Y
            if (getArity() == 1) {
                if (argTypes[0].getCardinality() == StaticProperty.EXACTLY_ONE &&
                        argTypes[0].getPrimaryType().isPlainType()) {
                    Affinity rel = th.relationship(argTypes[0].getPrimaryType(), BuiltInAtomicType.INTEGER);
                    if (!(rel == Affinity.SAME_TYPE || rel == Affinity.SUBSUMED_BY)) {
                        String s = "The function expects an argument of type " + argTypes[0] +
                                "; an array can only be supplied for a function that expects an integer";
                        return Optional.of(s);
                    } else {
                        for (GroundedValue member : ((ArrayItem) item).members()) {
                            if (!resultType.matches(member, th)) {
                                String s = "The supplied array contains an entry (" + Err.depictSequence(member) +
                                        ") is not an instance of the return type in the function signature (" +
                                        resultType + ")";
                                Optional<String> more = resultType.explainMismatch(member, th);
                                if (more.isPresent()) {
                                    s = s + ". " + more.get();
                                }
                                return Optional.of(s);
                            }
                        }
                    }
                } else {
                    String s = "The function argument is of type " + argTypes[0] +
                            "; an array can only be supplied for a function type whose argument type is xs:integer";
                    return Optional.of(s);
                }
            } else {
                String s = "The function arity is " + getArity() + "; an array can only be supplied for a function type with arity 1";
                return Optional.of(s);
            }
        }

        FunctionItemType other = ((FunctionItem) item).getFunctionItemType();
        if (getArity() != ((FunctionItem) item).getArity()) {
            String s = "The required function arity is " + getArity() +
                    "; the supplied function has arity " + ((FunctionItem) item).getArity();
            return Optional.of(s);
        }
        Affinity affinity = th.sequenceTypeRelationship(resultType, other.getResultType());
        if (affinity != Affinity.SAME_TYPE && affinity != Affinity.SUBSUMES) {
            String s = "The return type of the required function is " + resultType +
                    " but the return type of the supplied function is " + other.getResultType();
            return Optional.of(s);
        }
        for (int j=0; j<getArity(); j++) {
            affinity = th.sequenceTypeRelationship(argTypes[j], other.getArgumentTypes()[j]);
            if (affinity != Affinity.SAME_TYPE && affinity != Affinity.SUBSUMED_BY) {
                String s = "The type of the " + RoleDiagnostic.ordinal(j+1) +
                        " argument of the required function is " + argTypes[j] +
                        " but the declared type of the corresponding argument of the supplied function is " +
                        other.getArgumentTypes()[j];
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    @Override
    public Expression makeFunctionSequenceCoercer(Expression exp, Supplier<RoleDiagnostic> role, boolean allow40) {
        return new FunctionSequenceCoercer(exp, this, role, allow40);
    }

}
