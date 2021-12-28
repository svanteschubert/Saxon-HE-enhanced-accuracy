////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.CopyOf;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.wrapper.VirtualCopy;

/**
 * XSLT 3.0 function copy-of(). This compiles into an xsl:copy-of instruction, except when called dynamically.
 */
public class CopyOfFn extends SystemFunction {

    @Override
    public int getCardinality(Expression[] arguments) {
        return arguments[0].getCardinality();
    }

    /**
     * Evaluate the expression (used only for dynamic calls)
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(final XPathContext context, Sequence[] arguments) throws XPathException {
        Sequence in = arguments.length == 0 ? context.getContextItem() : arguments[0];
        SequenceIterator input = in.iterate();
        SequenceIterator output = new ItemMappingIterator(input, item -> {
        if (!(item instanceof NodeInfo)) {
            return item;
        } else {
            VirtualCopy vc = VirtualCopy.makeVirtualCopy((NodeInfo) item);
            vc.getTreeInfo().setCopyAccumulators(true);
            // TODO: set the base URI
            return vc;
        }
        });
        return new LazySequence(output);
    }



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
        Expression arg;
        if (arguments.length == 0) {
            arg = new ContextItemExpression();
        } else {
            arg = arguments[0];
        }
        CopyOf fn = new CopyOf(arg, true, Validation.PRESERVE, null, false);
        fn.setCopyAccumulators(true);
        fn.setSchemaAware(false);
        return fn;
    }
}




