// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;

/**
 * A PlaceHolder corresponds to the symbol "?" in a partial function application;
 * it is a PseudoExpression because it can appear in the expression tree in the place
 * of an expression, but is never actually evaluated.
 *
 * <p>In 4.0, placeholders can be associated with keywords, and the mapping from arguments
 * of the partially-applied function to arguments of the base function is therefore
 * more complex. The PlaceHolder holds an integer indicating a 0-based offset of the placeholder
 * in the argument list of the partially-applied function.</p>
 */

public class PlaceHolder extends PseudoExpression {

    private final int placeHolderSequence;

    /**
     * Create a placeholder, representing a "?" in a partial function application
     * @param placeHolderSequence the 0-based position of this placeholder among all the
     *                            placeholders in the partial function call
     */

    public PlaceHolder(int placeHolderSequence) {
        this.placeHolderSequence = placeHolderSequence;
    }

    public int getPlaceHolderSequence() {
        return placeHolderSequence;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     * @throws XPathException if the export fails, for example if an expression is found that won't work
     *                        in the target environment.
     */
    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        // no action
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @param rebindings a mutable list of (old binding, new binding) pairs
     *                   that is used to update the bindings held in any
     *                   local variable references that are copied.
     * @return the copy of the original expression
     */
    @Override
    public Expression copy(RebindingMap rebindings) {
        return new PlaceHolder(placeHolderSequence);
    }
}

