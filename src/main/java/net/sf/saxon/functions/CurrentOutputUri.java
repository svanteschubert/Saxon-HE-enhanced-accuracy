////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AnyURIValue;

/**
* This class implements the XSLT 3.0 function current-output-uri()
*/


public class CurrentOutputUri extends SystemFunction implements Callable {

    /**
     * Determine the special properties of this function. The general rule
     * is that a system function call is non-creative if its return type is
     * atomic, or if all its arguments are non-creative. This is overridden
     * for the generate-id() function, which is considered creative if
     * its operand is creative (because the result depends on the
     * identity of the operand)
     * @param arguments the actual arguments to the function call
     */
    @Override
    public int getSpecialProperties(Expression[] arguments) {
        // Prevent inlining of stylesheet functions calling current-output-uri()
        return super.getSpecialProperties(arguments) | StaticProperty.HAS_SIDE_EFFECTS;
    }

    /**
    * Evaluate in a general context
    */

    public AnyURIValue evaluateItem(XPathContext context) throws XPathException {
        String uri = context.getCurrentOutputUri();
        return uri == null ? null : new AnyURIValue(uri);
    }

    /**
     * Call the Callable.
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences.
     *                  <p>Generally it is advisable, if calling iterate() to process a supplied sequence, to
     *                  call it only once; if the value is required more than once, it should first be converted
     *                  to a {@link net.sf.saxon.om.GroundedValue} by calling the utility methd
     *                  SequenceTool.toGroundedValue().</p>
     *                  <p>If the expected value is a single item, the item should be obtained by calling
     *                  Sequence.head(): it cannot be assumed that the item will be passed as an instance of
     *                  {@link net.sf.saxon.om.Item} or {@link net.sf.saxon.value.AtomicValue}.</p>
     *                  <p>It is the caller's responsibility to perform any type conversions required
     *                  to convert arguments to the type expected by the callee. An exception is where
     *                  this Callable is explicitly an argument-converting wrapper around the original
     *                  Callable.</p>
     * @return the result of the evaluation, in the form of a Sequence. It is the responsibility
     *         of the callee to ensure that the type of result conforms to the expected result type.
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public ZeroOrOne<AnyURIValue> call(XPathContext context, Sequence[] arguments) throws XPathException {
        return new ZeroOrOne<>(evaluateItem(context));
    }

}


