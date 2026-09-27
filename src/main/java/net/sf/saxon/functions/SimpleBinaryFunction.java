////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.SequenceType;

/**
 * A simple anonymous function item that takes two arguments
 * and that does not require access to the dynamic context
 */

public class SimpleBinaryFunction extends AbstractFunction {

    String description;

    @FunctionalInterface
    public interface Lambda {
        Sequence call(Sequence arg1, Sequence arg2) throws XPathException;
    }

    private final Lambda lambda;
    private final SequenceType argumentType1;
    private final SequenceType argumentType2;
    private final SequenceType resultType;

    public SimpleBinaryFunction(Lambda lambda, SequenceType argumentType1, SequenceType argumentType2, SequenceType resultType) {
        this.lambda = lambda;
        this.argumentType1 = argumentType1;
        this.argumentType2 = argumentType2;
        this.resultType = resultType;
    }

    public SimpleBinaryFunction withDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Get the item type of the function item
     *
     * @return the function item's type
     */
    @Override
    public FunctionItemType getFunctionItemType() {
        return new SpecificFunctionType(argumentType1, argumentType2, resultType);
    }

    /**
     * Get the name of the function, or null if it is anonymous
     *
     * @return the function name, or null for an anonymous inline function
     */
    @Override
    public StructuredQName getFunctionName() {
        return null;
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
        return description == null
                ? "simple anonymous " + getFunctionItemType()
                : description;
    }

    /**
     * Get the arity of the function
     *
     * @return the number of arguments in the function signature
     */
    @Override
    public int getArity() {
        return 2;
    }

    @Override
    public AnnotationList getAnnotations() {
        return AnnotationList.EMPTY;
    }

    /**
     * Invoke the function
     *
     * @param context the XPath dynamic evaluation context
     * @param args    the actual arguments to be supplied
     * @return the result of invoking the function
     * @throws XPathException
     *          if a dynamic error occurs within the function
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] args) throws XPathException {
        return lambda.call(args[0], args[1]);
    }


    /**
     * Output information about this function item to the diagnostic explain() output
     *
     * @param out the destination for the output
     */
    @Override
    public void export(ExpressionPresenter out) {
        throw new UnsupportedOperationException("A SimpleBinaryFunction is a transient value that cannot be exported");
    }
}
