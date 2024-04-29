////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.ContextOriginator;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.GroundedIterator;

/**
 * A MemoClosure represents a value that has not yet been evaluated: the value is represented
 * by an expression, together with saved values of all the context variables that the
 * expression depends on.
 * <p>The MemoClosure is designed for use when the value is only read several times. The
 * value is saved on the first evaluation and remembered for later use.</p>
 * <p>The MemoClosure maintains a reservoir containing those items in the value that have
 * already been read. When a new iterator is requested to read the value, this iterator
 * first examines and returns any items already placed in the reservoir by previous
 * users of the MemoClosure. When the reservoir is exhausted, it then uses an underlying
 * Input Iterator to read further values of the underlying expression. If the value is
 * not read to completion (for example, if the first user did exists($expr), then the
 * Input Iterator is left positioned where this user abandoned it. The next user will read
 * any values left in the reservoir by the first user, and then pick up iterating the
 * base expression where the first user left off. Eventually, all the values of the
 * expression will find their way into the reservoir, and future users simply iterate
 * over the reservoir contents. Alternatively, of course, the values may be left unread.</p>
 * <p>Delayed evaluation is used only for expressions with a static type that allows
 * more than one item, so the evaluateItem() method will not normally be used, but it is
 * supported for completeness.</p>
 * <p>The expression may depend on local variables and on the context item; these values
 * are held in the saved XPathContext object that is kept as part of the Closure, and they
 * will always be read from that object. The expression may also depend on global variables;
 * these are unchanging, so they can be read from the Bindery in the normal way. Expressions
 * that depend on other contextual information, for example the values of position(), last(),
 * current(), current-group(), should not be evaluated using this mechanism: they should
 * always be evaluated eagerly. This means that the Closure does not need to keep a copy
 * of these context variables.</p>
 * <p>In Saxon-EE, a for-each loop can be multithreaded. If a variable declared outside
 * the loop is evaluated as a MemoClosure, then a reference to the variable within the
 * loop can result in concurrent attempts to evaluate the variable incrementally. This
 * is prevented by synchronizing the evaluation methods.</p>
 */

public class MemoClosure extends Closure implements ContextOriginator {

    private Sequence sequence;


    public MemoClosure(Expression expr, PullEvaluator inputEvaluator, XPathContext context) throws XPathException {
        //Instrumentation.count("MemoClosure(X)");
        setInputEvaluator(inputEvaluator);
        XPathContextMajor c2 = context.newContext();
        c2.setOrigin(this);
        setSavedXPathContext(c2);
        saveContext(expr, context);
    }

    /**
     * Evaluate the expression in a given context to return an iterator over a sequence
     */

    /*@NotNull*/
    @Override
    public synchronized SequenceIterator iterate()  {
        try {
            makeSequence();
        } catch (XPathException e) {
            throw new UncheckedXPathException(e);
        }
        return sequence.iterate();
    }

    private void makeSequence() throws XPathException {
        if (sequence == null) {
            inputIterator = inputEvaluator.iterate(savedXPathContext);
            if (inputIterator instanceof GroundedIterator && ((GroundedIterator)inputIterator).isActuallyGrounded()) {
                sequence = ((GroundedIterator)inputIterator).materialize();
                // If we find that the input iterator is grounded, this means there was no point
                // in doing lazy evaluation. If this happens once, it will probably happen all the time,
                // so send a message back to the binding instruction encouraging it to use eager
                // evaluation in future.
                if (learningEvaluator != null) {
                    learningEvaluator.reportCompletion(serialNumber);
                }
            } else {
                sequence = SequenceTool.toMemoSequence(inputIterator);
                if (sequence instanceof MemoSequence && learningEvaluator != null) {
                    ((MemoSequence) sequence).setLearningEvaluator(learningEvaluator, serialNumber);
                }
            }
        }
    }

    /**
     * Get the n'th item in the sequence, zero-based
     *
     * @param n the index of the required item, starting from zero
     * @return the item at the relevant position
     * @throws XPathException if an error occurs reading the base iterator
     */

    public synchronized Item itemAt(int n) throws XPathException {
        makeSequence();
        if (sequence instanceof GroundedValue) {
            return ((GroundedValue)sequence).itemAt(n);
        } else if (sequence instanceof MemoSequence) {
            return ((MemoSequence) sequence).itemAt(n);
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * Return a value containing all the items in the sequence returned by this
     * SequenceIterator
     *
     * @return the corresponding value
     * @throws net.sf.saxon.trans.XPathException
     *          if a failure occurs reading the input
     */

    /*@Nullable*/
    @Override
    public GroundedValue reduce() throws XPathException {
        try {
            if (sequence instanceof GroundedValue) {
                return (GroundedValue)sequence;
            } else {
                return SequenceTool.toGroundedValue(iterate());
            }
        } catch (UncheckedXPathException e) {
            throw e.getXPathException();
        }
    }

    @Override
    public Sequence makeRepeatable() {
        return this;
    }

    public Sequence getSequenceAsIs() {
        return sequence;
    }

}

