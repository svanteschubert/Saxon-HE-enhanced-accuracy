////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.UType;

/**
 * This is a special pattern that matches the node supplied as the first argument of a call to
 * a streamable stylesheet function; it corresponds to the
 * pattern match="$arg" where $arg is the first argument of the function.
 */
public class StreamingFunctionArgumentPattern extends Pattern {

    private static StreamingFunctionArgumentPattern THE_INSTANCE = new StreamingFunctionArgumentPattern();

    public static StreamingFunctionArgumentPattern getInstance() {
        return THE_INSTANCE;
    }

    protected StreamingFunctionArgumentPattern() {
    }

    /**
     * Get a UType indicating which kinds of items this Pattern can match.
     *
     * @return a UType indicating all the primitive types of item that the pattern can match.
     */
    @Override
    public UType getUType() {
        return UType.ANY_NODE;
    }

    /**
     * Type-check the pattern.
     *
     * @param visitor         the expression visitor
     * @param contextItemType the type of the context item at the point where the pattern
     *                        is defined. Set to null if it is known that the context item is undefined.
     * @return the optimised Pattern
     */

    @Override
    public Pattern typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        return this;
    }

    /**
     * Determine whether this Pattern matches the given Node. This is the main external interface
     * for matching patterns: it sets current() to the node being tested
     *
     * @param item    The NodeInfo representing the Element or other node to be tested against the Pattern
     * @param context The dynamic context. Only relevant if the pattern
     *                uses variables, or contains calls on functions such as document() or key().
     * @return true if the node matches the Pattern, false otherwise
     */

    @Override
    public boolean matches(Item item, XPathContext context) throws XPathException {
        Sequence arg = context.getStackFrame().getStackFrameValues()[0];
        SequenceIterator iter = arg.iterate();
        Item j;
        while ((j = iter.next()) != null) {
            if (j == item) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get a NodeTest that all the nodes matching this pattern must satisfy
     *
     * @return a NodeTest, as specific as possible, which all the matching nodes satisfy
     */

    @Override
    public ItemType getItemType() {
        return AnyNodeTest.getInstance();
    }

    @Override
    public String reconstruct() {
        return "$$";
    }

    @Override
    public void export(ExpressionPresenter presenter) throws XPathException {
        presenter.startElement("p.streamingArg");
        presenter.endElement();
    }

    /**
     * Copy an AnchorPattern.
     * Since there is only one, return the same.
     *
     * @param rebindings variables that need to be rebound
     * @return the original pattern unchanged
     */

    /*@NotNull*/
    @Override
    public Pattern copy(RebindingMap rebindings) {
        return this;
    }
}

