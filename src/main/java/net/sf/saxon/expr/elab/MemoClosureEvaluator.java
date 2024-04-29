////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.elab;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.MemoClosure;
import net.sf.saxon.value.SingletonClosure;

/**
 * A {@link MemoClosureEvaluator} is a {@link SequenceEvaluator} that evaluates an expression
 * lazily and incrementally; it evaluates new items in the result as and when they are needed.
 */
public class MemoClosureEvaluator implements SequenceEvaluator {

    final Expression input;
    final PullEvaluator inputEvaluator;
    final boolean singleton;

    public MemoClosureEvaluator(Expression input, PullEvaluator inputEvaluator) {
        this.input = input;
        this.inputEvaluator = inputEvaluator;
        this.singleton = !Cardinality.allowsMany(input.getCardinality());
    }

    /**
     * Evaluate a construct to produce a value (which might be a lazily evaluated Sequence)
     *
     * @param context the evaluation context
     * @return a Sequence (not necessarily grounded)
     * @throws XPathException if a dynamic error occurs during the evaluation.
     */
    @Override
    public Sequence evaluate(XPathContext context) throws XPathException {
        try {
            return singleton
                    ? new SingletonClosure(input, inputEvaluator, context)
                    : new MemoClosure(input, inputEvaluator, context);
        } catch (UncheckedXPathException e) {
            throw e.getXPathException();
        }
    }

}
