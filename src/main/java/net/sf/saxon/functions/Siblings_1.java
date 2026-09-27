////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.GNode;
import net.sf.saxon.om.LazySequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.pattern.nodetest.AnyGNode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.EmptySequence;

/**
 * Implement the XPath 4.0 siblings() function with one argument
 */


public class Siblings_1 extends SystemFunction {

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
     * @throws XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        GNode node = (GNode) arguments[0].head();
        if (node == null) {
            return EmptySequence.INSTANCE;
        }
        GNode parent = node.getParent();
        if (parent == null || node.getNodeKind() == Type.ATTRIBUTE || node.getNodeKind() == Type.NAMESPACE) {
            return node;
        }
        return new LazySequence(parent.iterateChildAxis(AnyGNode.TEST));
    }



}

