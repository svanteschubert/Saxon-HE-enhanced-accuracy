////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.elab;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.om.Item;

/**
 * The FallbackElaborator performs elaboration of expressions for which no more specific
 * elaborator is available. It produces evaluation functions that simply call the
 * direct evaluation interfaces. Note that when a FallbackElaborator is used, the
 * entire contained subtree will be interpreted in the traditional way, with no benefit
 * from elaboration.
 */

public class FallbackElaborator extends Elaborator {

    public FallbackElaborator() {
        
    }


    @Override
    public SequenceEvaluator eagerly() {
        return new EagerPullEvaluator(elaborateForPull());
    }


    @Override
    public SequenceEvaluator lazily(boolean repeatable, boolean lazyEvaluationRequired) {
        Expression expr = getExpression();
        if (repeatable) {
            return new MemoClosureEvaluator(expr, elaborateForPull());
        } else {
            return new LazyPullEvaluator(elaborateForPull());
        }
    }


    public PullEvaluator elaborateForPull() {
        return context -> getExpression().iterate(context);
    }

    public PushEvaluator elaborateForPush() {
        return (output, context) -> {
            getExpression().process(output, context);
            return null;
        };
    }

    public ItemEvaluator elaborateForItem() {
        return context -> getExpression().evaluateItem(context);
    }

    public BooleanEvaluator elaborateForBoolean() {
        return context -> getExpression().effectiveBooleanValue(context);
    }

    public UnicodeStringEvaluator elaborateForUnicodeString(boolean zeroLengthWhenAbsent) {
        if (zeroLengthWhenAbsent) {
            return context -> getExpression().evaluateAsString(context);
        } else {
            return context -> {
                Item item = getExpression().evaluateItem(context);
                return item == null ? null : item.getUnicodeStringValue();
            };
        }
    }


}

