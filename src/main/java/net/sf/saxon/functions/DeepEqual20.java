////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;

/**
 * XSLT 2.0 deep-equal() function, where the collation is already known.
 * Supports deep comparison of two sequences (of nodes and/or atomic values)
 * optionally using a collation
 */

public class DeepEqual20 extends CollatingFunctionFixed {




//    @Override
//    public Expression makeFunctionCall(Expression... arguments) {
//        Expression[] newArgs = new Expression[4];
//        newArgs[0] = arguments[0];
//        newArgs[1] = arguments[1];
//        if (arguments.length < 3 || arguments[2] instanceof DefaultedArgumentExpression) {
//            newArgs[2] = new StringLiteral(getRetainedStaticContext().getDefaultCollationName());
//        } else {
//            newArgs[2] = arguments[2];
//        }
//        if (arguments.length < 4 || arguments[3] instanceof DefaultedArgumentExpression) {
//            if (version < 40) {
//                throw new UncheckedXPathException("4-argument version of fn:deep-equal requires 4.0 to be enabled");
//            } else {
//                newArgs[3] = Literal.makeLiteral(new DictionaryMap());
//            }
//        } else {
//            newArgs[3] = arguments[3];
//        }
//        setArity(4);
//        return super.makeFunctionCall(newArgs);
//    }



    /**
     * Execute a dynamic call to the function
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences.
     * @return the result of the evaluation, in the form of a Sequence. It is the responsibility
     * of the callee to ensure that the type of result conforms to the expected result type.
     * @throws XPathException (should not happen)
     */

    @Override
    public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        DeepEqual deepEqual40 = ((DeepEqual)DeepEqual.makeDeepEqual(20).get());
        deepEqual40.setRetainedStaticContext(getRetainedStaticContext());
        return deepEqual40.call(context, arguments);
    }

    @Override
    public String getStreamerName() {
        return "DeepEqual";
    }


}

