////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.elab;

import net.sf.saxon.Controller;
import net.sf.saxon.event.ComplexContentOutputter;
import net.sf.saxon.event.SequenceCollector;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.TailCall;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.NoDynamicContextException;

/**
 * Elaborator for element construction expressions (both fixed and computed). This class
 * provides pull and single-item evaluation of these instructions; these invoke
 * the corresponding push evaluator which must be implemented in a subclass.
 */
public abstract class ComplexNodePushElaborator extends FallbackElaborator {

    @Override
    public PullEvaluator elaborateForPull() {
        PushEvaluator pushEval = this.elaborateForPush();
        return context -> {
            Controller controller = context.getController();
            if (controller == null) {
                throw new NoDynamicContextException("No controller available");
            }
            SequenceCollector seq = controller.allocateSequenceOutputter(1);
            TailCall tc = pushEval.processLeavingTail(new ComplexContentOutputter(seq), context);
            Expression.dispatchTailCall(tc);
            seq.close();
            SequenceIterator result = seq.iterate();
            seq.reset();
            return result;
        };
    }

    @Override
    public ItemEvaluator elaborateForItem() {
        PushEvaluator pushEval = this.elaborateForPush();
        return context -> {
            Controller controller = context.getController();
            if (controller == null) {
                throw new NoDynamicContextException("No controller available");
            }
            SequenceCollector seq = controller.allocateSequenceOutputter(1);
            TailCall tc = pushEval.processLeavingTail(new ComplexContentOutputter(seq), context);
            Expression.dispatchTailCall(tc);
            seq.close();
            Item result = seq.getFirstItem();
            seq.reset();
            return result;
        };
    }

    @Override
    public PushEvaluator elaborateForPush() {
        // Must be implemented in a subclass
        throw new UnsupportedOperationException();
    }



}

