////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.LearningEvaluator;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ManualIterator;

/**
 * A Closure represents a value that has not yet been evaluated: the value is represented
 * by an expression, together with saved values of all the context variables that the
 * expression depends on.
 * <p>This Closure is designed for use when the value is only read once. If the value
 * is read more than once, a new iterator over the underlying expression is obtained
 * each time: this may (for example in the case of a filter expression) involve
 * significant re-calculation.</p>
 * <p>The expression may depend on local variables and on the context item; these values
 * are held in the saved XPathContext object that is kept as part of the Closure, and they
 * will always be read from that object. The expression may also depend on global variables;
 * these are unchanging, so they can be read from the Bindery in the normal way. Expressions
 * that depend on other contextual information, for example the values of position(), last(),
 * current(), current-group(), should not be evaluated using this mechanism: they should
 * always be evaluated eagerly. This means that the Closure does not need to keep a copy
 * of these context variables.</p>
 */

public abstract class Closure implements Sequence, ContextOriginator {
    
    protected PullEvaluator inputEvaluator;
    protected XPathContextMajor savedXPathContext;
    protected int depth = 0;
    protected LearningEvaluator learningEvaluator;
    protected int serialNumber;
    protected Expression expression; // for diagnostics only

    // The base iterator is used to copy items on demand from the underlying value
    // to the reservoir. It only ever has one instance (for each Closure) and each
    // item is read only once.

    protected SequenceIterator inputIterator;
    
    public Closure() {
    }

    public void saveContext(/*@NotNull*/ Expression expression, /*@NotNull*/ XPathContext context) throws XPathException {
        // Make a copy of all local variables. If the value of any local variable is a closure
        // whose depth exceeds a certain threshold, we evaluate the closure eagerly to avoid
        // creating deeply nested lists of Closures, which consume memory unnecessarily

        // We only copy the local variables if the expression has dependencies on local variables.
        // What's more, we only copy those variables that the expression actually depends on.
        //Instrumentation.count("Closure.saveContext()");
        this.expression = expression; // for diagnostics only
        if ((expression.getDependencies() & StaticProperty.DEPENDS_ON_LOCAL_VARIABLES) != 0) {
            StackFrame localStackFrame = context.getStackFrame();
            Sequence[] local = localStackFrame.getStackFrameValues();
            int[] slotsUsed = expression.getSlotsUsed();  // computed on first call
            if (local != null) {
                final SlotManager stackFrameMap = localStackFrame.getStackFrameMap();
                final Sequence[] savedStackFrame =
                        new Sequence[stackFrameMap.getNumberOfVariables()];
                for (int i : slotsUsed) {
                    if (local[i] instanceof Closure) {
                        int cdepth = ((Closure) local[i]).depth;
                        if (cdepth >= 10) {
                            try {
                                local[i] = SequenceTool.toGroundedValue(local[i].iterate());
                            } catch (UncheckedXPathException e) {
                                throw e.getXPathException();
                            }
                        } else if (cdepth + 1 > depth) {
                            depth = cdepth + 1;
                        }
                    }
                    savedStackFrame[i] = local[i];
                }

                savedXPathContext.setStackFrame(stackFrameMap, savedStackFrame);
            }
        } else if ((expression.getDependencies() & StaticProperty.DEPENDS_ON_OWN_RANGE_VARIABLES) != 0)  {

            // Bug 5913: if the expression does not access external local variables, but uses the stackframe
            // for range variables declared within the expression itself, we need to allocate a clean
            // stack frame for use during lazy evaluation

            StackFrame localStackFrame = context.getStackFrame();
            final SlotManager stackFrameMap = localStackFrame.getStackFrameMap();
            final Sequence[] savedStackFrame =
                    new Sequence[stackFrameMap.getNumberOfVariables()];
            savedXPathContext.setStackFrame(stackFrameMap, savedStackFrame);
        }



        // Make a copy of the context item
        FocusIterator currentIterator = context.getCurrentIterator();
        if (currentIterator != null) {
            Item contextItem = currentIterator.current();
            ManualIterator single = new ManualIterator(contextItem);
            savedXPathContext.setCurrentIterator(single);
            // we don't save position() and last() because we have no way
            // of restoring them. So the caller must ensure that a Closure is not
            // created if the expression depends on position() or last()
        }

    }


    public void setLearningEvaluator(LearningEvaluator learningEvaluator, int serialNumber) {
        this.learningEvaluator = learningEvaluator;
        this.serialNumber = serialNumber;
    }


    /**
     * Get the first item in the sequence.
     *
     * @return the first item in the sequence if there is one, or null if the sequence
     *         is empty
     * @throws net.sf.saxon.trans.XPathException
     *          in the situation where the sequence is evaluated lazily, and
     *          evaluation of the first item causes a dynamic error.
     */
    @Override
    public Item head() throws XPathException {
        try {
            return iterate().next();
        } catch (UncheckedXPathException e) {
            throw e.getXPathException();
        }
    }


    /*@Nullable*/
    public XPathContextMajor getSavedXPathContext() {
        return savedXPathContext;
    }

    public void setInputEvaluator(PullEvaluator inputEvaluator) {
        this.inputEvaluator = inputEvaluator;
    }

    public void setSavedXPathContext(XPathContextMajor savedXPathContext) {
        this.savedXPathContext = savedXPathContext;
    }

    /**
     * Evaluate the expression in a given context to return an iterator over a sequence
     */

    /*@NotNull*/
    @Override
    public abstract SequenceIterator iterate();

    /**
     * Reduce a value to its simplest form. If the value is a closure or some other form of deferred value
     * such as a FunctionCallPackage, then it is reduced to a SequenceExtent. If it is a SequenceExtent containing
     * a single item, then it is reduced to that item. One consequence that is exploited by class FilterExpression
     * is that if the value is a singleton numeric value, then the result will be an instance of NumericValue
     * @return the simplified value
     * @throws XPathException if an error occurs doing the lazy evaluation
     */

    public GroundedValue reduce() throws XPathException {
        return SequenceTool.toGroundedValue(iterate());
    }

    @Override
    public Sequence makeRepeatable() throws XPathException {
        return materialize();
    }

    public Expression getExpression() {
        return expression;
    }
}

