////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.elab;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.VariableReference;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.ma.zeno.ZenoSequence;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
 * Evaluator for expressions that are evaluated in "shared append" mode. This mode is used when the
 * optimizer establishes that a sequence is being built incrementally, typically via a sequence of
 * recursive function calls. The sequence itself is implemented as a {@link ZenoSequence} to make
 * incremental append (at either end) efficient, without copying the entirety of the existing
 * sequence.
 */
public class SharedAppendEvaluator implements SequenceEvaluator {

    private Block.ChainAction[] actions;

    public SharedAppendEvaluator(Block expr) {
        actions = new Block.ChainAction[expr.size()];
        for (int i = 0; i < expr.size(); i++) {
            Expression child = expr.getOperanda()[i].getChildExpression();
            if (child instanceof VariableReference) {
                SequenceEvaluator eval = child.makeElaborator().eagerly();
                actions[i] = (chain, context) -> chain.appendSequence(eval.evaluate(context).materialize());
            } else {
                PullEvaluator pull = child.makeElaborator().elaborateForPull();
                actions[i] = (chain, context) -> {
                    SequenceIterator iter = pull.iterate(context);
                    for (Item item; (item = iter.next()) != null; ) {
                        chain = chain.append(item);
                    }
                    return chain;
                };
            }
        }
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
        ZenoSequence chain = new ZenoSequence();
        for (Block.ChainAction action : actions) {
            chain = action.perform(chain, context);
        }
        return chain;
    }

}
