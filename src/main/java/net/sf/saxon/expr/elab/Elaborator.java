////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.elab;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.value.Cardinality;

/**
 * This class is at the heart of the mechanism for elaborating expressions.
 *
 * <p>This mechanism evaluates expressions in two stages. The first stage (the elaboration stage),
 * converts the expression to a lambda function, specifically a function of the context that can
 * be called to produce the expression result. The second stage invokes this function (supplying
 * the context as an argument) to generate the result.</p>
 *
 * <p>The mechanism is complicated by the fact that expressions can be evaluated in different modes.
 * Specifically: pull evaluation delivers an iterator over the result, item evaluation delivers
 * the singleton result as an item; push evaluation sends the results to an {@link Outputter},
 * boolean evaluation returns the effective boolean value, and string evaluation returns the
 * string value. Update evaluation is used for XQuery update expressions.</p>
 *
 * <p>The {@code Elaborator} class is the abstract root of a hierarchy of classes for elaborating
 * different kinds of expression.</p>
 */

public abstract class Elaborator {

    private Expression expression;

    public Elaborator() {
    }

    /**
     * Get the expression being elaborated
     * @return the expression
     */
    public Expression getExpression() {
        return expression;
    }

    /**
     * Set the expression being elaborated
     * @param expr the expression
     */

    public void setExpression(Expression expr) {
        this.expression = expr;
    }

    /**
     * Get the Saxon Configuration
     * @return  the Configuration
     */

    protected Configuration getConfiguration() {
        return expression.getConfiguration();
    }


    /**
     * Get a function that evaluates the underlying expression eagerly
     *
     * @return an evaluator for the expression that returns a {@link GroundedValue}. The class
     * {@code SequenceEvaluator} is declared to return a {@link Sequence} not a {@code GroundedValue};
     * we can't specialize it because it's a limitation of C# delegates, but the result can safely
     * be cast to {@link GroundedValue}.
     */

    public SequenceEvaluator eagerly() {
        Expression expr = getExpression();
        int m = expr.getImplementationMethod();
        if ((m & Expression.EVALUATE_METHOD) != 0 && !Cardinality.allowsMany(expr.getCardinality())) {
            ItemEvaluator itemEvaluator = elaborateForItem();
            return new SingleItemEvaluator(itemEvaluator);
        } else if ((m & Expression.ITERATE_METHOD) != 0) {
            PullEvaluator pullEvaluator = elaborateForPull();
            return new EagerPullEvaluator(pullEvaluator);
        } else {
            PushEvaluator pushEvaluator = elaborateForPush();
            return new EagerPushEvaluator(pushEvaluator);
        }
    }

    /**
     * Get a function that evaluates the underlying expression lazily
     *
     * @param repeatable             true if the resulting {@link Sequence} must be usable repeatedly; false
     *                               if it only needs to be used once
     * @param lazyEvaluationRequired true if the expression MUST be evaluated lazily, for example to prevent
     *                               spurious errors or side-effects if it has been lifted out of a loop
     * @return an evaluator for the expression that returns a {@link Sequence} (which may be a lazy sequence)
     */

    public SequenceEvaluator lazily(boolean repeatable, boolean lazyEvaluationRequired) {
        Expression expr = getExpression();
        if (lazyEvaluationRequired) {
            return new MemoClosureEvaluator(expr, elaborateForPull());
        } else if (!expr.supportsLazyEvaluation()) {
            return eagerly();
        } else if (repeatable) {
            return new LearningEvaluator(
                    expr, new MemoClosureEvaluator(expr, elaborateForPull()));
        } else {
            PullEvaluator pullEvaluator = elaborateForPull();
            return new LazyPullEvaluator(pullEvaluator);
        }
    }

    /**
     * Get a function that evaluates the underlying expression in the form of
     * a {@link SequenceIterator}
     * @return an evaluator for the expression that returns a {@link SequenceIterator}
     */

    public abstract PullEvaluator elaborateForPull();

    /**
     * Get a function that evaluates the underlying expression in push mode, by
     * writing events to an {@link net.sf.saxon.event.Outputter}
     *
     * @return an evaluator for the expression in push mode
     */

    public abstract PushEvaluator elaborateForPush();

    /**
     * Get a function that evaluates the underlying expression in the form of
     * a {@link Item}. This must only be called for expressions whose result
     * has cardinality zero or one.
     *
     * @return an evaluator for the expression that returns an {@link Item}, or
     * null to represent an empty sequence.
     */

    public abstract ItemEvaluator elaborateForItem();

    /**
     * Get a function that evaluates the underlying expression in the form of
     * a boolean, this being the effective boolean value of the expression.
     *
     * @return an evaluator for the expression that returns a boolean.
     */

    public abstract BooleanEvaluator elaborateForBoolean();

    /**
     * Get a function that evaluates the underlying expression in the form of
     * a unicode string, this being the result of applying fn:string() to the result
     * of the expression.
     *
     * @param zeroLengthWhenAbsent if true, then when the result of the expression
     *                             is an empty sequence, the result of the StringEvaluator
     *                             should be a zero-length string. If false, the return value
     *                             should be null. For an expression or function that never
     *                             returns an empty sequence (for example, a call on string() or
     *                             normalize-space()), the argument has no effect.
     * @return an evaluator for the expression that returns a string.
     */

    public abstract UnicodeStringEvaluator elaborateForUnicodeString(boolean zeroLengthWhenAbsent);

    /**
     * Get a function that evaluates the underlying expression in the form of
     * a Java string, this being the result of applying fn:string() to the result
     * of the expression.
     *
     * @param zeroLengthWhenAbsent if true, then when the result of the expression
     *                             is an empty sequence, the result of the StringEvaluator
     *                             should be a zero-length string. If false, the return value
     *                             should be null.
     * @return an evaluator for the expression that returns a string.
     */

    public StringEvaluator elaborateForString(boolean zeroLengthWhenAbsent) {
        UnicodeStringEvaluator evaluator = elaborateForUnicodeString(zeroLengthWhenAbsent);
        return context -> {
            UnicodeString u = evaluator.eval(context);
            return u == null ? handleNullString(zeroLengthWhenAbsent) : u.toString();
        };
    }


    /**
     * Helper method that returns either a zero length string or null, depending
     * on the parameter
     * @param zeroLengthWhenAbsent if true, return a zero length string; if false, return null
     */

    protected final UnicodeString handleNullUnicodeString(boolean zeroLengthWhenAbsent) {
        return zeroLengthWhenAbsent ? EmptyUnicodeString.getInstance() : null;
    }

    protected final UnicodeString handlePossiblyNullUnicodeString(UnicodeString str, boolean zeroLengthWhenAbsent) {
        if (str == null && zeroLengthWhenAbsent) {
            return EmptyUnicodeString.getInstance();
        } else {
            return str;
        }
    }

    /**
     * Helper method that returns either a zero length string or null, depending
     * on the parameter
     *
     * @param zeroLengthWhenAbsent if true, return a zero length string; if false, return null
     */

    protected final String handleNullString(boolean zeroLengthWhenAbsent) {
        return zeroLengthWhenAbsent ? "" : null;
    }

    protected final String handlePossiblyNullString(String str, boolean zeroLengthWhenAbsent) {
        if (str == null && zeroLengthWhenAbsent) {
            return "";
        } else {
            return str;
        }
    }

    public UpdateEvaluator elaborateForUpdate() {
        throw new UnsupportedOperationException("Update not supported");
    }

}

