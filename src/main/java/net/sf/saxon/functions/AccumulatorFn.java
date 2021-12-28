////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.accum.Accumulator;
import net.sf.saxon.expr.accum.AccumulatorManager;
import net.sf.saxon.expr.accum.AccumulatorRegistry;
import net.sf.saxon.expr.accum.IAccumulatorData;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XsltController;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;

/**
 * Non-streaming implementation of accumulator-before() and accumulator-after()
 */
public abstract class AccumulatorFn extends SystemFunction {

    public enum Phase {AFTER, BEFORE}

    public abstract Phase getPhase();


    private Sequence getAccumulatorValue(String name, Phase phase, XPathContext context) throws XPathException {
        AccumulatorRegistry registry = getRetainedStaticContext().getPackageData().getAccumulatorRegistry();
        Accumulator accumulator = getAccumulator(name, registry);
        Item node = context.getContextItem();
        if (node == null) {
            throw new XPathException("No context item for evaluation of accumulator function", "XTDE3350", context);
        }
        if (!(node instanceof NodeInfo)) {
            throw new XPathException("Context item for evaluation of accumulator function must be a node", "XTTE3360", context);
        }
        int kind = ((NodeInfo)node).getNodeKind();
        if (kind == Type.ATTRIBUTE || kind == Type.NAMESPACE) {
            throw new XPathException("Context item for evaluation of accumulator function must not be an attribute or namespace node", "XTTE3360", context);
        }

        Sequence streamedAccVal = registry.getStreamingAccumulatorValue((NodeInfo) node, accumulator, phase);
        if (streamedAccVal != null) {
            return streamedAccVal;
        }

        TreeInfo root = ((NodeInfo)node).getTreeInfo();
        XsltController controller = (XsltController)context.getController();
        if (!accumulator.isUniversallyApplicable() && !controller.getAccumulatorManager().isApplicable(root, accumulator)) {
            throw new XPathException("Accumulator " + name + " is not applicable to the current document", "XTDE3362");
        }
        AccumulatorManager manager = controller.getAccumulatorManager();
        IAccumulatorData data = manager.getAccumulatorData(root, accumulator, context);
        return data.getValue((NodeInfo) node, phase == Phase.AFTER);
    }

    /**
     * Get the accumulator, given its name
     *
     * @param name     the name (as written - a lexical EQName)
     * @param registry the accumulator registry, or null if there are no accumulators registered
     * @return the accumulator
     * @throws XPathException the the accumulator is not recognised or if the name is invalid
     */

    private Accumulator getAccumulator(String name, AccumulatorRegistry registry) throws XPathException {
        StructuredQName qName;
        try {
            qName = StructuredQName.fromLexicalQName(name, false, true, getRetainedStaticContext());
        } catch (XPathException err) {
            throw new XPathException("Invalid accumulator name: " + err.getMessage(), "XTDE3340");
        }
        Accumulator accumulator = registry == null ? null : registry.getAccumulator(qName);
        if (accumulator == null) {
            throw new XPathException("Accumulator " + name + " has not been declared", "XTDE3340");
        }
        return accumulator;
    }

    /**
     * Get the return type, given knowledge of the actual arguments
     *
     * @param args the actual arguments supplied
     * @return the best available item type that the function will return
     */

    @Override
    public ItemType getResultItemType(Expression[] args) {
        try {
            if (args[0] instanceof StringLiteral) {
                AccumulatorRegistry registry = getRetainedStaticContext().getPackageData().getAccumulatorRegistry();
                Accumulator accumulator = getAccumulator(((StringLiteral) args[0]).getStringValue(), registry);
                return accumulator.getType().getPrimaryType();
            }
        } catch (Exception e) {
            //
        }
        return super.getResultItemType(args);
    }

    /**
     * Get the cardinality, given knowledge of the actual arguments
     *
     * @param args the actual arguments supplied
     * @return the most precise available cardinality that the function will return
     */
    @Override
    public int getCardinality(Expression[] args) {
        try {
            if (args[0] instanceof StringLiteral) {
                AccumulatorRegistry registry = getRetainedStaticContext().getPackageData().getAccumulatorRegistry();
                Accumulator accumulator = getAccumulator(((StringLiteral) args[0]).getStringValue(), registry);
                return accumulator.getType().getCardinality();
            }
        } catch (Exception e) {
            //
        }
        return super.getCardinality(args);
    }


    /**
     * Call the Callable.
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences.
     *                  <p>Generally it is advisable, if calling iterate() to process a supplied sequence, to
     *                  call it only once; if the value is required more than once, it should first be converted
     *                  to a {@link net.sf.saxon.om.GroundedValue} by calling the utility methd
     *                  SequenceTool.toGroundedValue().</p>
     *                  <p>If the expected value is a single item, the item should be obtained by calling
     *                  Sequence.head(): it cannot be assumed that the item will be passed as an instance of
     *                  {@link net.sf.saxon.om.Item} or {@link net.sf.saxon.value.AtomicValue}.</p>
     *                  <p>It is the caller's responsibility to perform any type conversions required
     *                  to convert arguments to the type expected by the callee. An exception is where
     *                  this Callable is explicitly an argument-converting wrapper around the original
     *                  Callable.</p>
     * @return the result of the evaluation, in the form of a Sequence. It is the responsibility
     *         of the callee to ensure that the type of result conforms to the expected result type.
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        String name = arguments[0].head().getStringValue();
        return getAccumulatorValue(name, getPhase(), context);
    }

    public static class AccumulatorBefore extends AccumulatorFn {

        public AccumulatorBefore() {
            //System.err.println("acc-before");
        }
        @Override
        public Phase getPhase() {
            return Phase.BEFORE;
        }
    }

    public static class AccumulatorAfter extends AccumulatorFn {
        @Override
        public Phase getPhase() {
            return Phase.AFTER;
        }

        @Override
        public String getStreamerName() {
            return "AccumulatorAfter";
        }
    }
}
