////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.Optionality;
import net.sf.saxon.om.FocusIterator;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;


public abstract class PositionAndLast extends ContextAccessorFunction {

    private boolean contextPossiblyUndefined = true;

    /**
     * Get an estimate of the net cost of evaluating the function, excluding the cost of evaluating
     * its arguments. The result is 0 for very simple functions like position() and exists(), 1 by
     * default, and higher values for particularly expensive functions.
     *
     * @return the estimated cost
     */
    @Override
    public int getNetCost() {
        // There is a special reason for returning 0: it prevents a call being loop-lifted.
        // Test WhereExpr026 and WhereExpr027 fail if position() and last() are loop-lifted,
        // because they end up being lazily evaluated as part of a MemoClosure.
        return 0;
    }

    /**
     * Bind a context item to appear as part of the function's closure. If this method
     * has been called, the supplied context item will be used in preference to the
     * context item at the point where the function is actually called.
     *
     * @param context the context to which the function applies. Must not be null.
     */
    @Override
    public FunctionItem bindContext(XPathContext context) {
        Int64Value value;
        try {
            value = evaluateItem(context);
        } catch (final XPathException e) {
            // This happens when we do a dynamic lookup of position() or last() when there is no context item
            SymbolicName.F name = new SymbolicName.F(getFunctionName(), getArity());
//            CallableDelegate callable = new CallableDelegate((context1, arguments) -> {
//                throw e;
//            });
//            callable.setDescription(getDescription());
            return new CallableFunction(name, (cxt, args) -> { throw e; }, getFunctionItemType())
                    .withDescription(getDescription());
        }
        return new ConstantFunction(getFunctionName(), value, SequenceType.SINGLE_INTEGER);
    }

    @Override
    public void supplyTypeInformation(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, Expression[] arguments) throws XPathException {
        super.supplyTypeInformation(visitor, contextInfo, arguments);
        if (contextInfo.getItemType() == ErrorType.getInstance()) {
            throw new XPathException("The context item is absent at this point", "XPDY0002");
        } else {
            contextPossiblyUndefined = contextInfo.getOptionality() != Optionality.REQUIRED;
        }
    }

    /**
     * Ask whether the context item may possibly be undefined
     *
     * @return true if it might be undefined
     */

    public boolean isContextPossiblyUndefined() {
        return contextPossiblyUndefined;
    }

    /**
     * Evaluate in a general context
     */

    public abstract Int64Value evaluateItem(XPathContext c) throws XPathException;

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
    public IntegerValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        return evaluateItem(context);
    }


    public static class Position extends PositionAndLast {
        @Override
        public Int64Value evaluateItem(XPathContext c) throws XPathException {
            FocusIterator currentIterator = c.getCurrentIterator();
            if (currentIterator == null) {
                throw new XPathException("The context item is absent, so position() is undefined")
                        .withXPathContext(c).withErrorCode("XPDY0002");
            }
            return Int64Value.makeIntegerValue(currentIterator.position());
        }

        /**
         * Make an elaborator for a system function call on this function
         *
         * @return a suitable elaborator; or null if no custom elaborator is available
         */
        @Override
        public Elaborator getElaborator() {
            return new PositionFnElaborator();
        }

        public static class PositionFnElaborator extends ItemElaborator {

            public ItemEvaluator elaborateForItem() {
                SystemFunctionCall sfc = (SystemFunctionCall) getExpression();
                Position fn = (Position) sfc.getTargetFunction();
                if (fn.isContextPossiblyUndefined()) {
                    return context -> {
                        FocusIterator focus = context.getCurrentIterator();
                        if (focus == null) {
                            throw new XPathException("The context item is absent, so position() is undefined")
                                    .withXPathContext(context).withLocation(sfc.getLocation()).withErrorCode("XPDY0002");
                        }
                        return Int64Value.makeIntegerValue(focus.position());
                    };
                } else {
                    return context -> Int64Value.makeIntegerValue(context.getCurrentIterator().position());
                }
            }

        }
    }

    public static class Last extends PositionAndLast {
        @Override
        public Int64Value evaluateItem(XPathContext c) throws XPathException {
            try {
                return Int64Value.makeIntegerValue(c.getLast());
            } catch (UncheckedXPathException e) {
                throw XPathException.makeXPathException(e);
            }
        }

        @Override
        public String getStreamerName() {
            return "Last";
        }

        /**
         * Make an elaborator for a system function call on this function
         *
         * @return a suitable elaborator; or null if no custom elaborator is available
         */
        @Override
        public Elaborator getElaborator() {
            return new LastFnElaborator();
        }

        public static class LastFnElaborator extends ItemElaborator {

            public ItemEvaluator elaborateForItem() {
                SystemFunctionCall sfc = (SystemFunctionCall) getExpression();
                Last fn = (Last) sfc.getTargetFunction();
                if (fn.isContextPossiblyUndefined()) {
                    return context -> {
                        FocusIterator focus = context.getCurrentIterator();
                        if (focus == null) {
                            throw new XPathException("The context item is absent, so last() is undefined")
                                    .withXPathContext(context)
                                    .withLocation(sfc.getLocation())
                                    .withErrorCode("XPDY0002");
                        }
                        return Int64Value.makeIntegerValue(context.getLast());
                    };
                } else {
                    return context -> Int64Value.makeIntegerValue(context.getLast());
                }
            }

        }
    }
}

