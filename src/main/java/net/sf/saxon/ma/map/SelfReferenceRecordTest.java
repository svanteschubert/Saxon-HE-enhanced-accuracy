////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.Genre;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.type.*;
import net.sf.saxon.value.SequenceType;

import java.util.function.Supplier;

/**
 * An instance of this class represents a self-reference within a record type, that is the ".."
 * construct in a record test such as <code>record(data as xs:string, next? as ..)</code>.
 * It contains a reference to the containing record type, and delegates matching operations
 * to the containing type, but needs to handle operations differently such as conversion to a string
 * or alpha-code, or comparison of item types for equality, to avoid going into infinite recursion.
 */
public class SelfReferenceRecordTest extends AnyFunctionType implements RecordType {

    private final RecordTest containingType;

    /**
     * Construct a RecordTest
     * @param containingType the record test containing the self-reference
     */

    public SelfReferenceRecordTest(RecordTest containingType) {
        this.containingType = containingType;
    }

    /**
     * Determine the Genre (top-level classification) of this type
     *
     * @return the Genre to which this type belongs, specifically {@link Genre#MAP}
     */
    @Override
    @CSharpModifiers(code = {"public", "override"})
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
        return containingType.getFieldNames();
    }

    /**
     * Get the type of a given field
     * @param field the name of the field
     * @return the type of the field if it is defined, or null otherwise
     */

    @Override
    public SequenceType getFieldType(String field) {
        return containingType.getFieldType(field);
    }

    /**
     * Ask whether a given field is optional
     * @param field the name of the field
     * @return true if the field is defined as an optional field
     */

    @Override
    public boolean isOptionalField(String field) {
        return containingType.isOptionalField(field);
    }

    /**
     * Ask whether the record type is extensible, that is, whether fields other than those named are permitted
     *
     * @return true if fields other than the named fields are permitted to appear
     */
    @Override
    public boolean isExtensible() {
        return containingType.isExtensible();
    }

    /**
     * Test whether a given item conforms to this type
     *
     * @param item The item to be tested
     * @param th type hierarchy data
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item, TypeHierarchy th) {
        return containingType.matches(item, th);
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
        return containingType.getResultType();
    }

    /**
     * Get the default priority when this ItemType is used as an XSLT pattern
     *
     * @return the default priority
     */
    @Override
    public double getDefaultPriority() {
        return 0.5; // Take care here to avoid recursion!
    }

    /**
     * Produce a representation of this type name for use in error messages.
     *
     * @return a string representation of the type, in notation resembling but not necessarily
     * identical to XPath syntax
     */
    public String toString() {
        return "..";
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
    @CSharpModifiers(code={"public", "override"})
    public String toExportString() {
        return "..";
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
        return "..";
    }

    /**
     * Test whether this function type equals another function type
     */

    public boolean equals(Object other) {
        return this == other ||
                containingType.equals(other) ||
                other instanceof SelfReferenceRecordTest
                        && containingType.equals(((SelfReferenceRecordTest)other).containingType);
    }

    /**
     * Returns a hash code value for the object.
     */
    @Override
    public int hashCode() {
        // Need to avoid infinite recursion for self-reference fields
        return 0x27ca481f;
    }

    /**
     * Determine the relationship of one function item type to another
     *
     * @return for example {@link Affinity#SUBSUMES}, {@link Affinity#SAME_TYPE}
     */

    @Override
    public Affinity relationship(FunctionItemType other, TypeHierarchy th) {
        return containingType.relationship(other, th);
    }

    @Override
    public Expression makeFunctionSequenceCoercer(Expression exp, Supplier<RoleDiagnostic> role, boolean allow40)
            throws XPathException {
        return new SpecificFunctionType(getArgumentTypes(), getResultType()).makeFunctionSequenceCoercer(exp, role, false);
    }

}

// Copyright (c) 2011-2023 Saxonica Limited
