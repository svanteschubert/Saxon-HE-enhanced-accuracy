////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.ItemChecker;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.Item;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;

/**
 * An ItemType representing the type function(*). Subtypes represent function items with more specific
 * type signatures.
 * <p>Note that although this class has a singleton instance representing the type <code>function(*)</code>,
 * there are also likely to be instances of subclasses representing more specific function types.</p>
 */
public class AnyFunctionType implements FunctionItemType {

    /*@NotNull*/ public final static AnyFunctionType ANY_FUNCTION = new AnyFunctionType();

    /**
     * Get the singular instance of this type (Note however that subtypes of this type
     * may have any number of instances)
     *
     * @return the singular instance of this type
     */

    /*@NotNull*/
    public static AnyFunctionType getInstance() {
        return ANY_FUNCTION;
    }

    /**
     * Get the corresponding {@link net.sf.saxon.type.UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        return UType.FUNCTION;
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
     * Determine whether this item type is atomic (that is, whether it can ONLY match
     * atomic values)
     *
     * @return true if this is ANY_ATOMIC_TYPE or a subtype thereof
     */

    @Override
    public boolean isPlainType() {
        return false;
    }

    /**
     * Ask whether this function item type is a map type. In this case function coercion (to the map type)
     * will never succeed.
     *
     * @return true if this FunctionItemType is a map type
     */
    @Override
    public boolean isMapType() {
        return false;
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
     * Get the default priority when this ItemType is used as an XSLT pattern
     * @return the default priority
     */
    @Override
    public double getDefaultPriority() {
        return -0.5;
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
        return "F";
    }

    /**
     * Get the argument types of the function
     *
     * @return the argument types, as an array of SequenceTypes, or null if this is the generic function
     *         type function(*)
     */
    /*@Nullable*/
    @Override
    public SequenceType[] getArgumentTypes() {
        return null;
    }

    /**
     * Get the list of annotation assertions defined on this function item type.
     * @return the list of annotation assertions, or an empty list if there are none
     */

    @Override
    public AnnotationList getAnnotationAssertions() {
        return AnnotationList.EMPTY;
    }

    /**
     * Test whether a given item conforms to this type
     *
     *
     *
     * @param item    The item to be tested
     * @param th
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item, TypeHierarchy th) throws XPathException {
        return item instanceof Function;
    }

    /**
     * Get the primitive item type corresponding to this item type. For item(),
     * this is Type.ITEM. For node(), it is Type.NODE. For specific node kinds,
     * it is the value representing the node kind, for example Type.ELEMENT.
     * For anyAtomicValue it is Type.ATOMIC_VALUE. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that integer, xs:dayTimeDuration, and xs:yearMonthDuration
     * are considered to be primitive types. For function items it is the singular
     * instance FunctionItemType.getInstance().
     */

    /*@NotNull*/
    @Override
    public final ItemType getPrimitiveItemType() {
        return ANY_FUNCTION;
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
    public final int getPrimitiveType() {
        return Type.FUNCTION;
    }

    /**
     * Produce a representation of this type name for use in error messages.
     *
     * @return a string representation of the type, in notation resembling but not necessarily
     *         identical to XPath syntax
     */

    public String toString() {
        return "function(*)";
    }

    /**
     * Get the item type of the atomic values that will be produced when an item
     * of this type is atomized
     *
     * @return the item type of the atomic values that will be produced when an item
     *         of this type is atomized
     */

    /*@NotNull*/
    @Override
    public PlainType getAtomizedItemType() {
        return null;
    }

    /**
     * Ask whether values of this type are atomizable
     *
     * @return true if some or all instances of this type can be successfully atomized; false
     *      * if no instances of this type can be atomized
     * @param th The type hierarchy cache
     */

    @Override
    public boolean isAtomizable(TypeHierarchy th) {
        return true; // arrays can be atomized
    }

    /**
     * Determine the relationship of one function item type to another
     *
     * @return for example {@link Affinity#SUBSUMES}, {@link Affinity#SAME_TYPE}
     */

    @Override
    public Affinity relationship(FunctionItemType other, TypeHierarchy th) {
        if (other == this) {
            return Affinity.SAME_TYPE;
        } else {
            return Affinity.SUBSUMES;
        }
    }

    /**
     * Create an expression whose effect is to apply function coercion to coerce a function from this type to another type
     *
     * @param exp     the expression that delivers the supplied sequence of function items (the ones in need of coercion)
     * @param role    information for use in diagnostics
     * @return the sequence of coerced functions, each on a function that calls the corresponding original function
     * after checking the parameters
     */

    @Override
    public Expression makeFunctionSequenceCoercer(Expression exp, RoleDiagnostic role)
            throws XPathException {
        return new ItemChecker(exp, this, role);
    }

    /**
     * Get the result type
     *
     * @return the result type
     */

    @Override
    public SequenceType getResultType() {
        return SequenceType.ANY_SEQUENCE;
    }

}

