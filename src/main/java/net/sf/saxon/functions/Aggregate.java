////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

/**
 * This abstract class provides functionality common to the sum(), avg(), count(),
 * exists(), and empty() functions. These all take a sequence as input and produce a singleton
 * as output; and they are all independent of the ordering of the items in the input.
 */

public abstract class Aggregate extends SystemFunction {

    /**
     * Static analysis: prevent sorting of the argument
     */

//    public void checkArguments(/*@NotNull*/ ExpressionVisitor visitor) throws XPathException {
//        super.checkArguments(visitor);
//        Optimizer opt = getConfiguration().obtainOptimizer();
//        argument[0] = argument[0].unordered(true, false);
//        // we don't care about the order of the results, but we do care about how many nodes there are
//    }

    /**
     * Perform optimisation of an expression and its subexpressions.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor         an expression visitor
     * @param contextInfo the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during this phase
     *          (typically a type error)
     */
//    @Override
//    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
//        optimizeChildren(visitor, contextInfo);
//        setArg(0, getArg(0).unordered(true, visitor.isOptimizeForStreaming()));
//        return this;
//    }


}

