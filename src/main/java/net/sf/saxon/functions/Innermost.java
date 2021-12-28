////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.sort.DocumentOrderIterator;
import net.sf.saxon.expr.sort.GlobalOrderComparer;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Navigator;

import java.util.Properties;

/**
 * This class implements the function fn:has-children(), which is a standard function in XPath 3.0
 */

public class Innermost extends SystemFunction {

    boolean presorted = false;


    @Override
    public int getSpecialProperties(Expression[] arguments) {
        return StaticProperty.ORDERED_NODESET | StaticProperty.PEER_NODESET;
    }

    /**
     * Allow the function to create an optimized call based on the values of the actual arguments
     *
     * @param visitor     the expression visitor
     * @param contextInfo information about the context item
     * @param arguments   the supplied arguments to the function call. Note: modifying the contents
     *                    of this array should not be attempted, it is likely to have no effect.
     * @return either a function call on this function, or an expression that delivers
     * the same result, or null indicating that no optimization has taken place
     * @throws XPathException if an error is detected
     */
    @Override
    public Expression makeOptimizedFunctionCall(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, Expression... arguments) throws XPathException {
        if ((arguments[0].getSpecialProperties() & StaticProperty.PEER_NODESET) != 0) {
            return arguments[0];
        }
        if ((arguments[0].getSpecialProperties() & StaticProperty.ORDERED_NODESET) != 0) {
            presorted = true;
        }
        return super.makeOptimizedFunctionCall(visitor, contextInfo, arguments);
    }

    /**
     * Evaluate the expression in a dynamic call
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return SequenceTool.toLazySequence(innermost(arguments[0].iterate()));
    }

    public SequenceIterator innermost(SequenceIterator in) throws XPathException {
        if (!presorted) {
            in = new DocumentOrderIterator(in, GlobalOrderComparer.getInstance());
        }
        return new InnermostIterator(in);
    }


    @Override
    public void exportAttributes(ExpressionPresenter out) {
        super.exportAttributes(out);
        if (presorted) {
            out.emitAttribute("flags", "p");
        }
    }

    @Override
    public void importAttributes(Properties attributes) throws XPathException {
        super.importAttributes(attributes);
        String flags = attributes.getProperty("flags");
        if (flags != null && flags.contains("p")) {
            presorted = true;
        }
    }


    /**
     * Inner class implementing the logic in the form of an iterator.
     * <p>The principle behind the algorithm is the assertion that in a sorted input
     * sequence, if a node A is immediately followed by a descendant B, then we can
     * discard A; if it is immediately followed by a node B that is not a descendant,
     * then we can emit A. So there is a single-node lookahead. The last node in the input
     * sequence is always emitted. (Note, "descendant" is used loosely here to
     * include attributes and namespaces.)</p>
     */

    private class InnermostIterator implements SequenceIterator {

        SequenceIterator in;
        NodeInfo pending = null;
        int position = 0;

        /**
         * Create an iterator which filters an input sequence to select only those nodes
         * that have no ancestor in the sequence
         *
         * @param in the input sequence, which must be a sequence of nodes in document order with no duplicates
         * @throws XPathException if an error occurs evaluating the input iterator
         */

        public InnermostIterator(SequenceIterator in) throws XPathException {
            this.in = in;
            pending = (NodeInfo)in.next();
        }

        @Override
        public NodeInfo next() throws XPathException {
            if (pending == null) {
                // we're done
                position = -1;
                return null;
            } else {
                while (true) {
                    NodeInfo next = (NodeInfo)in.next();
                    if (next == null) {
                        NodeInfo current = pending;
                        position++;
                        pending = null;
                        return current;
                    }
                    if (Navigator.isAncestorOrSelf(pending, next)) {
                        // discard the pending node
                        pending = next;
                    } else {
                        // emit the pending node
                        position++;
                        NodeInfo current = pending;
                        pending = next;
                        return current;
                    }
                }
            }
        }

        @Override
        public void close() {
            in.close();
        }

    }
}

// Copyright (c) 2012-2020 Saxonica Limited
