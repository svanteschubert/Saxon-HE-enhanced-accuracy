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
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.value.Cardinality;

/**
 * Abstract superclass for elaborators whose primary evaluation method is in push mode. This class provides
 * olternative modes of evaluation (such as pull and item evaluation), all implemented in terms of the
 * push evaluator which must be supplied in a subclass.
 */
public abstract class PushElaborator extends Elaborator {

    @Override
    public PullEvaluator elaborateForPull() {
        Expression expr = getExpression();
        if (Cardinality.allowsMany(expr.getCardinality())) {
            PushEvaluator pushEval = elaborateForPush();
            return context -> {
                Controller controller = context.getController();
                assert controller != null;
                SequenceCollector seq = controller.allocateSequenceOutputter();
                TailCall tc = pushEval.processLeavingTail(new ComplexContentOutputter(seq), context);
                Expression.dispatchTailCall(tc);
                seq.close();
                return seq.iterate();
            };
        } else {
            ItemEvaluator itemEval = elaborateForItem();
            return context -> {
                Item item = itemEval.eval(context);
                return item == null ? EmptyIterator.getInstance() : SingletonIterator.makeIterator(item);
            };
        }
    }

    @Override
    public SequenceEvaluator eagerly() {
        Expression expr = getExpression();
        if (Cardinality.allowsMany(expr.getCardinality())) {
            PushEvaluator pushEval = elaborateForPush();
            return new EagerPushEvaluator(pushEval);
        } else {
            return new OptionalItemEvaluator(elaborateForItem());
        }
    }


    @Override
    public PushEvaluator elaborateForPush() {
        // Must be implemented in a subclass
        throw new UnsupportedOperationException();
    }

    @Override
    public ItemEvaluator elaborateForItem() {
        PushEvaluator pushEval = elaborateForPush();
        return context -> {
            Controller controller = context.getController();
            assert controller != null;
            SequenceCollector seq = controller.allocateSequenceOutputter(1);
            TailCall tc = pushEval.processLeavingTail(new ComplexContentOutputter(seq), context);
            Expression.dispatchTailCall(tc);
            seq.close();
            return seq.getFirstItem();
        };
    }

    public BooleanEvaluator elaborateForBoolean() {
        PullEvaluator pullEval = elaborateForPull();
        return context -> {
            SequenceIterator iter = pullEval.iterate(context);
            return ExpressionTool.effectiveBooleanValue(iter);
        };
    }

    @Override
    public UnicodeStringEvaluator elaborateForUnicodeString(boolean zeroLengthWhenAbsent) {
        ItemEvaluator ie = elaborateForItem();
        return context -> {
            Item item = ie.eval(context);
            return item == null ? handleNullUnicodeString(zeroLengthWhenAbsent) : item.getUnicodeStringValue();
        };
    }


}

