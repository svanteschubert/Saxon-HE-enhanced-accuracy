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
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Closure;

/**
 * A <code>LearningEvaluator</code> initially performs
 * lazy incremental evaluation of an expression; but if this proves unproductive,
 * it switches after a while to eager evaluation. Lazy evaluation is
 * considered unproductive if the input sequence is read to completion.
 * A MemoClosure reports back if the input sequence is read to completion,
 * and if the number of sequences that are completely read is equal to
 * the total number of evaluations, lazy evaluation is abandoned.
 */
public class LearningEvaluator implements SequenceEvaluator {

    private final static int EVAL_LIMIT = 20;

    private final Expression expression;
    private SequenceEvaluator evaluator;
    private int completed = 0;
    private int count = 0;
    private boolean flipped;

    /**
     * Construct a <code>LearningEvaluator</code>
     * @param expr the expression to be evaluated
     * @param lazy a SequenceEvaluator that evaluates the expression. Although this
     *             is generally obtained by calling <code>getElaborator().lazily()</code>,
     *             it does not necessarily perform lazy evaluation; some expressions
     *             such as literals and variable references choose to evaluate
     *             themselves eagerly all the time.
     */
    public LearningEvaluator(Expression expr, SequenceEvaluator lazy) {
        this.expression = expr;
        this.evaluator = lazy;
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
        if (count > EVAL_LIMIT) {
            return evaluator.evaluate(context);
        } else {
            Sequence result = evaluator.evaluate(context);
            if (result instanceof Closure) {
                ((Closure)result).setLearningEvaluator(this, count);
            } else {
                flipped = true;
            }
            count++;
            return result;
        }
    }

    /**
     * Callback method called when a lazily-evaluated expression has been read to completion
     * @param serialNumber identifies the evaluation
     */

    public void reportCompletion(int serialNumber) {
        // Note, does thread-unsafe updates to the statistics
        // Note: three things might happen to a MemoClosure: it might be read to completion, it might
        // be partially read, and it might never be accessed at all. In the final case we will get no
        // feedback. The condition we want to test for is that of the first N MemoClosures created,
        // each one was read to completion
        //System.err.println("Completion " + expression.toShortString() + " " + serialNumber);
        if (completed++ >= EVAL_LIMIT && completed == count) {
            //System.err.println("Switch " + expression.toShortString() + " to eager evaluation");
            evaluator = (expression.makeElaborator().eagerly());
            count = Integer.MAX_VALUE;
            flipped = true;
        }

    }

    /**
     * Ask whether the LearningEvaluator has decided to use eager evaluation always
     * @return true if sufficient evaluations have been performed to determine
     * that lazy evaluation offers no benefits
     */

    public boolean hasDecidedToEvaluateEagerly() {
        return flipped;
    }

}
