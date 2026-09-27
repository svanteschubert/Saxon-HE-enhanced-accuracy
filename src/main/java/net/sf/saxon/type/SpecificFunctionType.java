////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.functions.hof.CoercedFunction;
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
import net.sf.saxon.type.coercion.CoercionPlan;
import net.sf.saxon.type.coercion.FunctionItemCoercionPlan;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

import java.util.Arrays;
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
    private boolean acceptReducedArity;


    public final static SpecificFunctionType COMPONENT_FUNCTION_TYPE =
            new SpecificFunctionType(SequenceType.SINGLE_STRING, SequenceType.ANY_SEQUENCE);


    /**
     * Construct a specific function type
     * @param argTypes the types of the arguments to the function
     * @param resultType the type of the function result
     */
    public SpecificFunctionType(SequenceType[] argTypes, SequenceType resultType) {
        this.argTypes = Objects.requireNonNull(argTypes);
        this.resultType = Objects.requireNonNull(resultType);
        this.annotations = AnnotationList.EMPTY;
    }

    /**
     * Construct a specific function type including function annotations
     *
     * @param argTypes   the types of the arguments to the function
     * @param resultType the type of the function result
     * @param annotations the function annotations
     */

    public SpecificFunctionType(SequenceType[] argTypes, SequenceType resultType, AnnotationList annotations) {
        this.argTypes = Objects.requireNonNull(argTypes);
        this.resultType = Objects.requireNonNull(resultType);
        this.annotations = Objects.requireNonNull(annotations);
    }

    /**
     * Convenience variadic constructor
     * @param types the argument types, followed by the result type
     */
    public SpecificFunctionType(SequenceType... types) {
        if (types.length == 0) {
            throw new IllegalArgumentException("No result type supplied");
        }
        this.argTypes = Arrays.copyOf(types, types.length - 1);
        this.resultType = types[types.length - 1];
        this.annotations = AnnotationList.EMPTY;
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
     * Indicate that this function type describes a callback function, and that the caller of this
     * callback is prepared to accept a reduced-arity version of the function directly, without
     * needing it to be wrapped in a {@code CoercedFunction} that accepts and swallows the superfluous
     * arguments. This is useful where it means that the caller does not need to compute additional
     * argument values just for them to be ignored.
     * @return this, after setting the {@code acceptReducedArity} flag.
     */

    public SpecificFunctionType withAcceptReducedArity() {
        acceptReducedArity = true;
        return this;
    }

    public boolean isAcceptReducedArity() {
        return acceptReducedArity;
    }

    /**
     * Ask whether a function of a supplied type is acceptable, despite not being an exact
     * match, without needing to be wrapped in a {@link CoercedFunction}
     * @param supplied the item type of a supplied function
     * @return true if the caller is able to call the supplied function without further wrapping
     */
    public boolean acceptsReducedArityFunction(ItemType supplied) {
        if (supplied instanceof SpecificFunctionType) {
            SpecificFunctionType actual = (SpecificFunctionType) supplied;
            if (!actual.getResultType().equals(getResultType())) {
                return false;
            }
            if (actual.getArity() > getArity()) {
                return false;
            }
            if (!acceptReducedArity && actual.getArity() < getArity() )  {
                return false;
            }
            for (int i=0; i < actual.getArity(); i++) {
                if (!actual.getArgumentTypes()[i].equals(getArgumentTypes()[i])) {
                    return false;
                }
            }
            return true;
        }
        return false;
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
            if (!resultType.normalizeSequenceType().equals(f2.resultType.normalizeSequenceType())) {
                return false;
            }
            if (argTypes.length != f2.argTypes.length) {
                return false;
            }
            for (int i = 0; i < argTypes.length; i++) {
                if (!argTypes[i].normalizeSequenceType().equals(f2.argTypes[i].normalizeSequenceType())) {
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
    public Affinity relationship(FunctionItemType other) {
        if (other == INSTANCE || other instanceof AnyFunctionTypeWithAssertions) {
            return Affinity.SUBSUMED_BY;
        } else if (equals(other)) {
            return Affinity.SAME_TYPE;
        } else if (other instanceof ArrayItemType || other instanceof MapType) {
            Affinity rrel = other.relationship(this);
            return switch (rrel) {
                case SUBSUMES -> Affinity.SUBSUMED_BY;
                case SUBSUMED_BY -> Affinity.SUBSUMES;
                default -> rrel;
            };
        } else {
            if (argTypes.length != other.getArgumentTypes().length) {
                return Affinity.DISJOINT;
            }
            boolean wider = false;
            boolean narrower = false;
            for (int i = 0; i < argTypes.length; i++) {
                Affinity argRel = Subsumption.sequenceTypeRelationship(argTypes[i], other.getArgumentTypes()[i]);
                switch (argRel) {
                    case DISJOINT -> {
                        return Affinity.DISJOINT;
                    }
                    case SUBSUMES -> narrower = true;
                    case SUBSUMED_BY -> wider = true;
                    case OVERLAPS -> {
                        wider = true;
                        narrower = true;
                    }
                    default -> {
                    }
                }
            }

            Affinity resRel = Subsumption.sequenceTypeRelationship(resultType, other.getResultType());
            switch (resRel) {
                case DISJOINT -> {
                    return Affinity.DISJOINT;
                }
                case SUBSUMES -> wider = true;
                case SUBSUMED_BY -> narrower = true;
                case OVERLAPS -> {
                    wider = true;
                    narrower = true;
                }
                default -> {
                }
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
     * @param item The item to be tested
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item) {
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
                    if (!resultType.matches(pair.value())) {
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
                Affinity rel = Subsumption.computeRelationship(argTypes[0].getPrimaryType(), BuiltInAtomicType.INTEGER);
                if (!(rel == Affinity.SAME_TYPE || rel == Affinity.SUBSUMED_BY)) {
                    return false;
                }
                for (GroundedValue member : ((ArrayItem) item).members()) {
                    if (!resultType.matches(member)) {
                        return false;
                    }
                }
                return true;
            } else {
                return false;
            }
        }

        Affinity affinity = Subsumption.computeRelationship(((FunctionItem) item).getFunctionItemType(), this);
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
                        if (!resultType.matches(pair.value())) {
                            String s = "The supplied map contains an entry with key (" + pair.key() +
                                    ") whose corresponding value (" + Err.depictSequence(pair.value()) +
                                    ") is not an instance of the return type in the function signature (" +
                                    resultType + ")";
                            Optional<String> more = resultType.explainMismatch(pair.value(), th);
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
                            if (!resultType.matches(member)) {
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
        Affinity affinity = Subsumption.sequenceTypeRelationship(resultType, other.getResultType());
        if (affinity != Affinity.SAME_TYPE && affinity != Affinity.SUBSUMES) {
            String s = "The return type of the required function is " + resultType +
                    " but the return type of the supplied function is " + other.getResultType();
            return Optional.of(s);
        }
        for (int j=0; j<getArity(); j++) {
            affinity = Subsumption.sequenceTypeRelationship(argTypes[j], other.getArgumentTypes()[j]);
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
        return new FunctionSequenceCoercer(exp, this, role, allow40, acceptReducedArity);
    }

    /**
     * Get the coercion plan for use when this type is the required type for (say) coercion
     * of arguments in a function call
     *
     * @param version the XPath language version (40 or 31)
     */
    @Override
    public CoercionPlan getCoercionPlan(int version) {
        return FunctionItemCoercionPlan.getInstance(version);
    }
}
