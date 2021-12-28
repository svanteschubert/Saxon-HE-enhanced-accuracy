////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;

import static net.sf.saxon.expr.flwor.Clause.ClauseName.COUNT;

/**
 * A "count" clause in a FLWOR expression
 */
public class CountClause extends Clause {

    private LocalVariableBinding rangeVariable;

    @Override
    public ClauseName getClauseKey() {
        return COUNT;
    }

    @Override
    public CountClause copy(FLWORExpression flwor, RebindingMap rebindings) {
        CountClause c2 = new CountClause();
        c2.rangeVariable = rangeVariable.copy();
        c2.setPackageData(getPackageData());
        c2.setLocation(getLocation());
        return c2;
    }

    public void setRangeVariable(LocalVariableBinding binding) {
        this.rangeVariable = binding;
    }

    public LocalVariableBinding getRangeVariable() {
        return rangeVariable;
    }

    /**
     * Get the number of variables bound by this clause
     *
     * @return the number of variable bindings
     */
    @Override
    public LocalVariableBinding[] getRangeVariables() {
        return new LocalVariableBinding[]{rangeVariable};
    }

    /**
     * Get a tuple stream that implements the functionality of this clause, taking its
     * input from another tuple stream which this clause modifies
     *
     * @param base    the input tuple stream
     * @param context
     * @return the output tuple stream
     */

    @Override
    public TuplePull getPullStream(TuplePull base, XPathContext context) {
        return new CountClausePull(base, this);
    }

    /**
     * Get a push-mode tuple stream that implements the functionality of this clause, supplying its
     * output to another tuple stream
     *
     * @param destination the output tuple stream
     * @param output the destination for the result
     * @param context
     * @return the push tuple stream that implements the functionality of this clause of the FLWOR
     *         expression
     */
    @Override
    public TuplePush getPushStream(TuplePush destination, Outputter output, XPathContext context) {
        return new CountClausePush(output, destination, this);
    }

    /**
     * Process the subexpressions of this clause
     *
     * @param processor the expression processor used to process the subexpressions
     */
    @Override
    public void processOperands(OperandProcessor processor) throws XPathException {
        // no action
    }

    @Override
    public void addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        // no action
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     */
    @Override
    public void explain(ExpressionPresenter out) throws XPathException {
        out.startElement("count");
        out.emitAttribute("var", getRangeVariable().getVariableQName());
        out.endElement();
    }

    public String toString() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C64);
        fsb.append("count $");
        fsb.append(rangeVariable.getVariableQName().getDisplayName());
        return fsb.toString();
    }
}

// Copyright (c) 2011-2020 Saxonica Limited


