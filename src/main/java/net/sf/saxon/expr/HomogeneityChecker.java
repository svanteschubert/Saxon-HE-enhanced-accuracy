////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.expr.sort.DocumentSorter;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.HomogeneityCheckerIterator;
import net.sf.saxon.type.Affinity;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;

/**
 * This class is an expression that does a run-time check of the result of a "/" expression
 * to ensure that (a) the results consists entirely of atomic values and function items, or entirely of nodes,
 * and (b) if the results are nodes, then they are deduplicated and sorted into document order.
 */
public class HomogeneityChecker extends UnaryExpression {

    public HomogeneityChecker(Expression base) {
        super(base);
    }

    @Override
    protected OperandRole getOperandRole() {
        return OperandRole.INSPECT;
    }

    /**
     * Type-check the expression. Default implementation for unary operators that accept
     * any kind of operand
     */
    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        if (getBaseExpression() instanceof HomogeneityChecker) {
            return getBaseExpression().typeCheck(visitor, contextInfo);
        }
        getOperand().typeCheck(visitor, contextInfo);
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        ItemType type = getBaseExpression().getItemType();
        if (type.equals(ErrorType.getInstance())) {
            return Literal.makeEmptySequence();
        }
        Affinity rel = th.relationship(type, AnyNodeTest.getInstance());
        if (rel == Affinity.DISJOINT) {
            // expression cannot return nodes, so this checker is redundant
              // code deleted by bug 4298
//            if (getBaseExpression() instanceof SlashExpression && ((SlashExpression) getBaseExpression()).getLeadingSteps() instanceof SlashExpression &&
//                    (((SlashExpression) getBaseExpression()).getLeadingSteps().getSpecialProperties() & StaticProperty.ORDERED_NODESET) == 0) {
//                DocumentSorter ds = new DocumentSorter(((SlashExpression) getBaseExpression()).getLeadingSteps());
//                SlashExpression se = new SlashExpression(ds, ((SlashExpression) getBaseExpression()).getLastStep());
//                ExpressionTool.copyLocationInfo(this, se);
//                return se;
//            } else {
                return getBaseExpression();
//            }
        } else if (rel == Affinity.SAME_TYPE || rel == Affinity.SUBSUMED_BY) {
            // expression always returns nodes, so replace this expression with a DocumentSorter
            Expression savedBase = getBaseExpression();
            Expression parent = getParentExpression();
            getOperand().detachChild();
            DocumentSorter ds = new DocumentSorter(savedBase);
            ExpressionTool.copyLocationInfo(this, ds);
            ds.setParentExpression(parent);
            //ds.verifyParentPointers();
            return ds;
        }
        return this;
    }

    /**
     * Convert this expression to an equivalent XSLT pattern
     *
     * @param config the Saxon configuration
     * @return the equivalent pattern
     * @throws net.sf.saxon.trans.XPathException if conversion is not possible
     */
    @Override
    public Pattern toPattern(Configuration config) throws XPathException {
        return getBaseExpression().toPattern(config);
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        if (getBaseExpression() instanceof HomogeneityChecker) {
            return getBaseExpression().optimize(visitor, contextInfo);
        }
        return super.optimize(visitor, contextInfo);
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings
     */
    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        HomogeneityChecker hc = new HomogeneityChecker(getBaseExpression().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, hc);
        return hc;
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
        return ITERATE_METHOD;
    }

    /**
     * Iterate the path-expression in a given context
     *
     * @param context the evaluation context
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(final XPathContext context) throws XPathException {

        // This class delivers the result of the path expression in unsorted order,
        // without removal of duplicates. If sorting and deduplication are needed,
        // this is achieved by wrapping the path expression in a DocumentSorter

        SequenceIterator base = getBaseExpression().iterate(context);
        return new HomogeneityCheckerIterator(base, getLocation());
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "homCheck";
    }
}

