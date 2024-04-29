////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.elab;

import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.om.Item;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.value.Cardinality;

/**
 * An {@link ItemElaborator} is an abstract superclass for use by expressions that deliver zero or one items
 * as their result. A concrete subclass must implement the {@link #elaborateForItem()} method; other evaluation
 * methods are implemented (by default) by calling {@link #elaborateForItem()}.
 */
public abstract class ItemElaborator extends Elaborator {

    @Override
    public SequenceEvaluator eagerly() {
        boolean maybeEmpty = Cardinality.allowsZero(getExpression().getCardinality());
        ItemEvaluator ie = elaborateForItem();
        if (maybeEmpty) {
            return new OptionalItemEvaluator(ie);
        } else {
            return new SingleItemEvaluator(ie);
        }
    }

    @Override
    public PullEvaluator elaborateForPull() {
        ItemEvaluator ie = elaborateForItem();
        return context -> SingletonIterator.makeIterator(ie.eval(context));
    }

    @Override
    public PushEvaluator elaborateForPush() {
        ItemEvaluator ie = elaborateForItem();
        return (out, context) -> {
            Item it = ie.eval(context);
            if (it != null) {
                out.append(it);
            }
            return null;
        };
    }

    @Override
    public abstract ItemEvaluator elaborateForItem();

    public BooleanEvaluator elaborateForBoolean() {
        ItemEvaluator ie = elaborateForItem();
        return context -> ExpressionTool.effectiveBooleanValue(ie.eval(context));
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

