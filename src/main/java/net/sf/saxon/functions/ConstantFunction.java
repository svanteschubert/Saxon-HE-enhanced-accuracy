////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.SequenceType;

/**
 * A ConstantFunction is a zero-argument function that always delivers the same result, supplied
 * at the time the function is created.
 */

public class ConstantFunction extends AbstractFunction  {

    private final StructuredQName name;
    private final GroundedValue value;
    private final SequenceType resultType;
    private AnnotationList annotationList;

    /**
     * Create a ConstantFunction
     * @param name the function name.
     * @param value the value to be returned by the function
     * @param resultType the return type of the function (must match the type of {@code value}
     */
    public ConstantFunction(StructuredQName name, GroundedValue value, SequenceType resultType) {
        this.name = name;
        this.value = value;
        this.resultType = resultType;
    }

    /**
     * Create an anonymous ConstantFunction
     *
     * @param value      the value to be returned by the function
     * @param resultType the return type of the function (must match the type of {@code value}
     */
    public ConstantFunction(GroundedValue value, SequenceType resultType) {
        this.name = null;
        this.value = value;
        this.resultType = resultType;
    }

    /**
     * Add an annotation list
     */

    public ConstantFunction withAnnotationList(AnnotationList annotationList) {
        this.annotationList = annotationList;
        return this;
    }

    /**
     * Get the annotation list
     */

    @Override
    public AnnotationList getAnnotations() {
        return annotationList == null ? AnnotationList.EMPTY : annotationList;
    }

    /**
     * Get the item type of the function item
     *
     * @return the function item's type
     */
    @Override
    public FunctionItemType getFunctionItemType() {
        return new SpecificFunctionType(resultType);
    }

    /**
     * Get the name of the function, or null if it is anonymous
     *
     * @return the function name, or null for an anonymous inline function
     */
    @Override
    public StructuredQName getFunctionName() {
        return name;
    }

    /**
     * Get the arity of the function
     *
     * @return the number of arguments in the function signature
     */
    @Override
    public int getArity() {
        return 0;
    }

    /**
     * Get a description of this function for use in error messages. For named functions, the description
     * is the function name (as a lexical QName). For others, it might be, for example, "inline function",
     * or "partially-applied ends-with function".
     *
     * @return a description of the function for use in error messages
     */
    @Override
    public String getDescription() {
        return "fn(){" + value.toShortString() + "}";
    }

    @Override
    public String toShortString() {
        return getDescription();
    }

    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return value;
    }

    public GroundedValue getConstantValue() {
        return value;
    }

}

