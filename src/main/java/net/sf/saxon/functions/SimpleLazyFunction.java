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
 * A simple function item that takes no arguments and that does not require access to the dynamic context.
 * Unlike a {@link ConstantFunction}, it computes its result lazily (on demand) rather than being
 * supplied with the result in advance.
 */

public class SimpleLazyFunction extends AbstractFunction {

    @FunctionalInterface
    public interface Lambda {
        Sequence call() throws XPathException;
    }

    private final Lambda lambda;
    private final SequenceType resultType;
    private String description;

    public SimpleLazyFunction(Lambda lambda, SequenceType resultType) {
        this.lambda = lambda;
        this.resultType = resultType;
    }

    public SimpleLazyFunction withDescription(String description) {
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
        return new SpecificFunctionType(resultType);
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
        return 0;
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
        return lambda.call();
    }


    /**
     * Output information about this function item to the diagnostic explain() output
     *
     * @param out the destination for the output
     */
    @Override
    public void export(ExpressionPresenter out) {
        throw new UnsupportedOperationException("A SimpleLazyFunction is a transient value that cannot be exported");
    }
}
