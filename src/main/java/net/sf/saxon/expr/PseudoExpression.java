////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;

/**
 * A pseudo-expression is an object that can appear as a node on the expression tree, but which cannot
 * actually be evaluated in its own right. Example are sort key definitions, and XSLT patterns.
 * These have to be nodes on the tree, because they contain subexpressions, so recursive traversals
 * of the tree need to process them. But evaluating a sort key definition or an XSLT pattern throws an exception.
 *
 * <p>A constraint for pseudo-expressions is that rewrite methods (such as simplify, typecheck, optimize etc.)
 * always return an object of the same class (nearly always the same object)</p>
 */
public abstract class PseudoExpression extends Expression {

    private void cannotEvaluate() throws XPathException {
        throw new XPathException("Cannot evaluate pseudo-expression " + getClass().getName());
    }

    @Override
    public int getImplementationMethod() {
        return 0;
    }

    @Override
    protected final int computeCardinality() {
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
    }

    @Override
    public ItemType getItemType() {
        return AnyItemType.getInstance();
    }

    @Override
    public final Item evaluateItem(XPathContext context) throws XPathException {
        cannotEvaluate();
        return null;
    }

    @Override
    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        cannotEvaluate();
        return false;
    }

    @Override
    public final UnicodeString evaluateAsString(XPathContext context) throws XPathException {
        cannotEvaluate();
        return EmptyUnicodeString.getInstance();
    }

    @Override
    public final SequenceIterator iterate(XPathContext context) throws XPathException {
        cannotEvaluate();
        return null;
    }

    @Override
    public void process(Outputter output, XPathContext context) throws XPathException {
        cannotEvaluate();
    }

    /**
     * Make an elaborator for this expression
     *
     * @return an appropriate {@link Elaborator}
     */
    @Override
    public Elaborator getElaborator() {
        throw new UnsupportedOperationException("Cannot evaluate pseudo-expression " + getClass().getName());
    }
}

