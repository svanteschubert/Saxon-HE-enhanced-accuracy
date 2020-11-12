////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.DateTimeValue;
import net.sf.saxon.value.StringValue;

/**
 * A DynamicContextAccessor is a function that takes no arguments, but operates implicitly on the
 * dynamic context. In the case of a dynamic call, the context item that is used is the one at the point
 * where the function item is created.
 */

public abstract class DynamicContextAccessor extends SystemFunction {

    private AtomicValue boundValue;

    public void bindContext(XPathContext context) throws XPathException {
        boundValue = evaluate(context);
    }

    public abstract AtomicValue evaluate(XPathContext context) throws XPathException;

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public AtomicValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        if (boundValue != null) {
            return boundValue;
        } else {
            return evaluate(context);
        }
    }

    @Override
    public Expression makeFunctionCall(Expression[] arguments) {
        return new SystemFunctionCall(this, arguments) {
            @Override
            public Item evaluateItem(XPathContext context) throws XPathException {
                // Cut some of the call overhead
                return evaluate(context);
            }

            @Override
            public int getIntrinsicDependencies() {
                return StaticProperty.DEPENDS_ON_RUNTIME_ENVIRONMENT;
            }
        };
    }


    public static class ImplicitTimezone extends DynamicContextAccessor {
        @Override
        public AtomicValue evaluate(XPathContext context) throws XPathException {
            DateTimeValue now = DateTimeValue.getCurrentDateTime(context);
            return now.getComponent(AccessorFn.Component.TIMEZONE);
        }
    }

    public static class CurrentDateTime extends DynamicContextAccessor {
        @Override
        public AtomicValue evaluate(XPathContext context) throws XPathException {
            return DateTimeValue.getCurrentDateTime(context);
        }
    }

    public static class CurrentDate extends DynamicContextAccessor {
        @Override
        public AtomicValue evaluate(XPathContext context) throws XPathException {
            DateTimeValue now = DateTimeValue.getCurrentDateTime(context);
            return now.toDateValue();
        }
    }

    public static class CurrentTime extends DynamicContextAccessor {
        @Override
        public AtomicValue evaluate(XPathContext context) throws XPathException {
            DateTimeValue now = DateTimeValue.getCurrentDateTime(context);
            return now.toTimeValue();
        }
    }

    public static class DefaultLanguage extends DynamicContextAccessor {
        @Override
        public AtomicValue evaluate(XPathContext context) throws XPathException {
            String lang = context.getConfiguration().getDefaultLanguage();
            return new StringValue(lang, BuiltInAtomicType.LANGUAGE);
        }
    }

}

