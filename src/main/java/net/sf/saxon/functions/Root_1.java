////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.ma.jnode.JNode;
import net.sf.saxon.om.GNode;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.EmptySequence;

/**
 * Implement the XPath 2.0 root() function with one argument. Extended in 4.0 to allow GNodes.
 */


public class Root_1 extends SystemFunction {

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-significant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     * @param arguments the actual arguments to the function call
     */

    @Override
    public int getSpecialProperties(Expression[] arguments) {
        int prop = StaticProperty.ORDERED_NODESET |
                StaticProperty.SINGLE_DOCUMENT_NODESET |
                StaticProperty.NO_NODES_NEWLY_CREATED;
        if ((getArity() == 0) ||
                (arguments[0].getSpecialProperties() & StaticProperty.CONTEXT_DOCUMENT_NODESET) != 0) {
            prop |= StaticProperty.CONTEXT_DOCUMENT_NODESET;
        }
        return prop;
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        GNode node = (GNode) arguments[0].head();
        if (node == null) {
            return EmptySequence.INSTANCE;
        }
        if (node instanceof NodeInfo) {
            return ((NodeInfo) node).getRoot();
        }
        if (node instanceof JNode) {
            JNode current = ((JNode) node);
            JNode parent = ((JNode) node).getParent();
            while (parent != null) {
                current = parent;
                parent = current.getParent();
            }
            return current;
        }
        throw new IllegalArgumentException();
    }

    @Override
    public String getStreamerName() {
        return "Root";
    }

    /**
     * Make an elaborator for a system function call on this function
     *
     * @return a suitable elaborator; or null if no custom elaborator is available
     */
    @Override
    public Elaborator getElaborator() {
        return new RootFnElaborator();
    }

    public static class RootFnElaborator extends ItemElaborator {

        public ItemEvaluator elaborateForItem() {
            final SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            final ItemEvaluator arg0Eval = fnc.getArg(0).makeElaborator().elaborateForItem();
            return context -> {
                GNode focus = (GNode)arg0Eval.eval(context);
                if (focus == null) {
                    return null;
                }
                if (focus instanceof NodeInfo) {
                    return ((NodeInfo)focus).getRoot();
                }
                if (focus instanceof JNode) {
                    JNode current = ((JNode) focus);
                    JNode parent = ((JNode) focus).getParent();
                    while (parent != null) {
                        current = parent;
                        parent = current.getParent();
                    }
                    return current;
                }
                throw new IllegalArgumentException();
            };

        }

    }
}

