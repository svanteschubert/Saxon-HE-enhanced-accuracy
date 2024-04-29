////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;

/**
 * <code>DefaultedArgumentExpression</code> is a placeholder used in a function call for an argument
 * that is evaluated using the default value expression from the function definition, in cases where
 * that expression has not yet been compiled (typically because the function call precedes the
 * function declaration). It should be resolved to a real expression during type checking of the
 * function call.
 */

public class DefaultedArgumentExpression extends PseudoExpression {

    /**
     * Constructor
     */

    public DefaultedArgumentExpression() {
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @param rebindings variables that need to be re-bound
     * @return the copy of the original expression
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        return this;
    }


    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in export() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "defaultValue";
    }


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter destination) throws XPathException {
        throw new UnsupportedOperationException();
    }


    @Override
    public Elaborator getElaborator() {
        throw new UnsupportedOperationException();
    }

    public static class DefaultCollationArgument extends DefaultedArgumentExpression {
        @Override
        public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
            return new StringLiteral(visitor.getStaticContext().getDefaultCollationName());
        }
    }


}

