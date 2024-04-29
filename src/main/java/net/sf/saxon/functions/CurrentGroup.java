////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.GroupIterator;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

/**
 * Implements the XSLT function current-group()
 */

public class CurrentGroup extends ContextAccessorFunction {

    /**
     * Make an expression that either calls this function, or that is equivalent to a call
     * on this function
     *
     * @param arguments the supplied arguments to the function call
     * @return either a function call on this function, or an expression that delivers
     * the same result
     */
    @Override
    public Expression makeFunctionCall(Expression... arguments) {
        return new CurrentGroupCall();
    }

    @Override
    public FunctionItem bindContext(XPathContext context) throws XPathException {
        if (getRetainedStaticContext().getPackageData().getHostLanguageVersion() < 40) {
            throw new XPathException("Dynamic call on current-group() fails (the current group is absent)", "XTDE1061");
        }
        GroupIterator gi = context.getCurrentGroupIterator();
        if (gi == null) {
            throw new XPathException("There is no current group", "XTDE1061");
        }
        GroundedValue group = gi.currentGroup();
        ConstantFunction fn = new ConstantFunction(group);
        fn.setDetails(getDetails());
        fn.setRetainedStaticContext(getRetainedStaticContext());
        return fn;
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        throw new XPathException("Dynamic call on current-group() fails (the current group is absent)", "XTDE1061");
    }

}

