////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.ReportingSingletonIterator;
import net.sf.saxon.tree.iter.SingletonIterator;

/**
 * A SingletonClosure represents a value that has not yet been evaluated: the value is represented
 * by an expression, together with saved values of all the context variables that the
 * expression depends on. The value of a SingletonClosure is always either a single item
 * or an empty sequence.
 * <p>The expression may depend on local variables and on the context item; these values
 * are held in the saved XPathContext object that is kept as part of the Closure, and they
 * will always be read from that object. The expression may also depend on global variables;
 * these are unchanging, so they can be read from the Bindery in the normal way. Expressions
 * that depend on other contextual information, for example the values of position(), last(),
 * current(), current-group(), should not be evaluated using this mechanism: they should
 * always be evaluated eagerly. This means that the Closure does not need to keep a copy
 * of these context variables.</p>
 */

public class SingletonClosure extends Closure implements Sequence {

    private boolean built = false;
    /*@Nullable*/ private Item value = null;

    /**
     * Constructor should not be called directly, instances should be made using the make() method.
     *
     * @param exp     the expression to be lazily evaluated
     * @param context the context in which the expression should be evaluated
     * @throws XPathException if an error occurs saving the dynamic context
     */

    public SingletonClosure(Expression exp, PullEvaluator inputEvaluator, XPathContext context) throws XPathException {
        setInputEvaluator(inputEvaluator);
        savedXPathContext = context.newContext();
        savedXPathContext.setOrigin(this);
        saveContext(exp, context);
        //Instrumentation.count("SingletonClosure.new()");
    }

    /**
     * Evaluate the expression in a given context to return an iterator over a sequence
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate() {
        try {
            Item item = asItem();
            if (item == null) {
                return EmptyIterator.getInstance();
            } else if (learningEvaluator != null) {
                return new ReportingSingletonIterator(item, learningEvaluator, serialNumber);
            } else {
                return new SingletonIterator(item);
            }
        } catch (XPathException e) {
            throw new UncheckedXPathException(e);
        }
    }

    /**
     * Get the first item in the sequence.
     *
     * @return the first item in the sequence if there is one, or null if the sequence
     * is empty
     * @throws net.sf.saxon.trans.XPathException in the situation where the sequence is evaluated lazily, and
     *                                           evaluation of the first item causes a dynamic error.
     */
    @Override
    public Item head() throws XPathException {
        try {
            return asItem();
        } catch (UncheckedXPathException e) {
            throw e.getXPathException();
        }
    }

    /**
     * Return the value in the form of an Item
     *
     * @return the value in the form of an Item
     * @throws XPathException if an error is detected
     */

    /*@Nullable*/
    public synchronized Item asItem() throws XPathException {   // bug 6161
        if (!built) {
            value = inputEvaluator.iterate(savedXPathContext).next();
            built = true;
            savedXPathContext = null;   // release variables saved in the context to the garbage collector
            if (learningEvaluator != null) {
                learningEvaluator.reportCompletion(serialNumber);
                //Instrumentation.count("SingletonClosure.reportCompletion()");
                learningEvaluator = null;
            }
        }
        return value;
    }

    /**
     * Get the n'th item in the sequence (starting from 0). This is defined for all
     * SequenceValues, but its real benefits come for a SequenceValue stored extensionally.
     *
     * @param n the index of the requested item
     * @return the n'th item in the sequence
     * @throws XPathException if an error is detected
     */

    /*@Nullable*/
    public Item itemAt(int n) throws XPathException {
        if (n != 0) {
            return null;
        }
        return asItem();
    }

    /**
     * Get the length of the sequence
     *
     * @return the length of the sequence
     * @throws XPathException if an error is detected
     */

    public int getLength() throws XPathException {
        return asItem() == null ? 0 : 1;
    }

    /**
     * Return a value containing all the items in the sequence returned by this
     * SequenceIterator
     *
     * @return the corresponding value
     */

    @Override
    @CSharpModifiers(code = {"public", "override"})
    public GroundedValue materialize() throws XPathException {
        try {
            return SequenceTool.itemOrEmpty(asItem());
        } catch (UncheckedXPathException e) {
            throw e.getXPathException();
        }
    }

    @Override
    public SingletonClosure makeRepeatable() {
        return this;
    }

    public boolean isBuilt() {
        return built;
    }

}

