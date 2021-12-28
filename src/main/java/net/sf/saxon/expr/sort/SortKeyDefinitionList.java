////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.expr.Operand;
import net.sf.saxon.expr.OperandRole;
import net.sf.saxon.expr.PseudoExpression;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * The class represents a list of sort key definitions in major-to-minor sort key order. It is not a true
 * expression, because it cannot be evaluated, but it acts as a node in the expression tree, and is therefore
 * classified as a pseudo-expression.
 */
public class SortKeyDefinitionList extends PseudoExpression implements Iterable<SortKeyDefinition> {

    private SortKeyDefinition[] sortKeyDefinitions;

    /**
     * Create a list of sort key definitions
     * @param sortKeyDefinitions the sort key definitions, supplied as an array
     */

    public SortKeyDefinitionList(SortKeyDefinition[] sortKeyDefinitions) {
        this.sortKeyDefinitions = sortKeyDefinitions;
    }

    /**
     * Get the operands of this SortKeyDefinitionList, treating it as a pseudo-expression.
     * @return the operands: specifically, the contained sort key definitions, each of which
     * is also treated as a pseudo-expression.
     */

    @Override
    public Iterable<Operand> operands() {
        List<Operand> list = new ArrayList<Operand>(size());
        for (SortKeyDefinition skd : sortKeyDefinitions) {
            list.add(new Operand(this, skd, OperandRole.INSPECT));
        }
        return list;
    }

    /**
     * Ask whether the expression can be lifted out of a loop, assuming it has no dependencies
     * on the controlling variable/focus of the loop
     * @param forStreaming
     */

    @Override
    public boolean isLiftable(boolean forStreaming) {
        return false;
    }


    /**
     * Ask how many sort key definitions there are
     * @return the number of sort key definitions in the list (always one or more)
     */

    public int size() {
        return sortKeyDefinitions.length;
    }

    /**
     * Get the i'th sort key definition, counting from zero
     * @param i the index of the required sort key definition
     * @return the required sort key definition
     */

    public SortKeyDefinition getSortKeyDefinition(int i) {
        return sortKeyDefinitions[i];
    }

    /**
     * Get an iterator over the sort key definitions
     * @return an iterator over the sort key definitions
     */

    @Override
    public Iterator<SortKeyDefinition> iterator() {
        return Arrays.asList(sortKeyDefinitions).iterator();
    }

    /**
     * Copy this pseudo-expression
     * @return a deep copy
     * @param rebindings
     */

    @Override
    public SortKeyDefinitionList copy(RebindingMap rebindings) {
        SortKeyDefinition[] s2 = new SortKeyDefinition[sortKeyDefinitions.length];
        for (int i=0; i< sortKeyDefinitions.length; i++) {
            s2[i] = sortKeyDefinitions[i].copy(rebindings);
        }
        return new SortKeyDefinitionList(s2);
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided directly. The other methods will always be available
     * indirectly, using an implementation that relies on one of the other methods.
     *
     * @return the implementation method, for example {@link #ITERATE_METHOD} or {@link #EVALUATE_METHOD} or
     * {@link #PROCESS_METHOD}
     */
    @Override
    public int getImplementationMethod() {
        return 0;
    }

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        for (SortKeyDefinition skd : sortKeyDefinitions) {
            skd.export(out);
        }
    }
}

