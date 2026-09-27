////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.elab;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Cardinality;

/**
 * Abstract implementation of {@link Elaborator} for expressions that primarily evaluate in pull mode,
 * that is, by returning a {@link SequenceIterator} over the result. The method {@link #elaborateForPull()}
 * must be implemented in subclasses; other evaluation methods such as {@link #elaborateForPush()} are
 * by default implemented by calling {@link #elaborateForPull()}, though they may have optimized
 * implementations in subclasses.
 */
public abstract class PullElaborator extends Elaborator {

    @Override
    public abstract PullEvaluator elaborateForPull();

    @Override
    public SequenceEvaluator eagerly() {
        PullEvaluator pull = elaborateForPull();
        return new EagerPullEvaluator(pull);
    }

    @Override
    public PushEvaluator elaborateForPush() {
        PullEvaluator pull = elaborateForPull();
        return (out, context) -> {
            try {
                SequenceIterator iter = pull.iterate(context);
                for (Item it; (it = iter.next()) != null; ) {
                    out.append(it);
                }
                return null;
            } catch (UncheckedXPathException err) {
                throw err.getXPathException();
            }
        };
    }

    @Override
    public ItemEvaluator elaborateForItem() {
        PullEvaluator pull = elaborateForPull();
        return context -> {
            try {
                return pull.iterate(context).next();
            } catch (UncheckedXPathException err) {
                throw err.getXPathException();
            }
        };
    }

    public BooleanEvaluator elaborateForBoolean() {
        Expression expr = getExpression();
        if (Cardinality.allowsMany(expr.getCardinality())) {
            PullEvaluator pull = elaborateForPull();
            return context -> {
                try {
                    return ExpressionTool.effectiveBooleanValue(pull.iterate(context));
                } catch (UncheckedXPathException err) {
                    throw err.getXPathException().maybeWithLocation(expr.getLocation());
                } catch (XPathException err) {
                    throw err.maybeWithLocation(expr.getLocation());
                }
            };
        } else {
            ItemEvaluator pull = elaborateForItem();
            return context -> {
                try {
                    return ExpressionTool.effectiveBooleanValue(pull.eval(context));
                } catch (UncheckedXPathException err) {
                    throw err.getXPathException().maybeWithLocation(expr.getLocation());
                } catch (XPathException err) {
                    throw err.maybeWithLocation(expr.getLocation());
                }
            };
        }
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

