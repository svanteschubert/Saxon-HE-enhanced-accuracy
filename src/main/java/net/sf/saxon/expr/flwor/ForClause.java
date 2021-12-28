////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.KeyFn;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;

import static net.sf.saxon.expr.flwor.Clause.ClauseName.FOR;

/**
 * A "for" clause in a FLWOR expression
 */
public class ForClause extends Clause {

    private LocalVariableBinding rangeVariable;
    /*@Nullable*/
    private LocalVariableBinding positionVariable;
    private Operand sequenceOp;
    private boolean allowsEmpty;

    public ForClause() {
    }

    @Override
    public ClauseName getClauseKey() {
        return FOR;
    }

    @Override
    public ForClause copy(FLWORExpression flwor, RebindingMap rebindings) {
        ForClause f2 = new ForClause();
        f2.setLocation(getLocation());
        f2.setPackageData(getPackageData());
        f2.rangeVariable = rangeVariable.copy();
        if (positionVariable != null) {
            f2.positionVariable = positionVariable.copy();
        }
        f2.initSequence(flwor, getSequence().copy(rebindings));
        f2.allowsEmpty = allowsEmpty;
        return f2;
    }

    public void initSequence(FLWORExpression flwor, Expression sequence) {
        sequenceOp = new Operand(flwor, sequence, isRepeated() ? OperandRole.REPEAT_NAVIGATE : OperandRole.NAVIGATE);
    }

    public void setSequence(Expression sequence) {
        sequenceOp.setChildExpression(sequence);
    }

    public Expression getSequence() {
        return sequenceOp.getChildExpression();
    }

    /**
     * Set the range variable (the primary variable bound by this clause)
     *
     * @param binding the range variable
     */

    public void setRangeVariable(LocalVariableBinding binding) {
        rangeVariable = binding;
    }

    /**
     * Get the range variable (the primary variable bound by this clause)
     *
     * @return the range variable
     */

    public LocalVariableBinding getRangeVariable() {
        return rangeVariable;
    }

    /**
     * Set the position variable (the variable bound by the "at" clause)
     *
     * @param binding the position variable, or null if there is no position variable
     */

    public void setPositionVariable(/*@Nullable*/ LocalVariableBinding binding) {
        positionVariable = binding;
    }

    /**
     * Get the position variable (the variable bound by the "at" clause)
     *
     * @return the position variable, or null if there is no position variable
     */

    /*@Nullable*/
    public LocalVariableBinding getPositionVariable() {
        return positionVariable;
    }

    /**
     * Get the number of variables bound by this clause
     *
     * @return the number of variable bindings (1 or 2 depending on whether there is a position variable)
     */
    @Override
    public LocalVariableBinding[] getRangeVariables() {
        if (positionVariable == null) {
            return new LocalVariableBinding[]{rangeVariable};
        } else {
            return new LocalVariableBinding[]{rangeVariable, positionVariable};
        }
    }

    /**
     * Say whether the "allowing empty" option is present
     *
     * @param option true if the "allowing empty" option is present
     */

    public void setAllowingEmpty(boolean option) {
        allowsEmpty = option;
    }

    /**
     * Ask whether the "allowing empty" option is present
     *
     * @return true if the "allowing empty" option is present
     */

    public boolean isAllowingEmpty() {
        return allowsEmpty;
    }

    /**
     * Type-check the expression
     */
    @Override
    public void typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        SequenceType decl = rangeVariable.getRequiredType();
        if (allowsEmpty && !Cardinality.allowsZero(decl.getCardinality())) {
            RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.VARIABLE, rangeVariable.getVariableQName().getDisplayName(), 0);
            Expression checker =
                    CardinalityChecker.makeCardinalityChecker(
                            getSequence(), StaticProperty.ALLOWS_ONE_OR_MORE, role);
            setSequence(checker);
        }
        SequenceType sequenceType = SequenceType.makeSequenceType(
                decl.getPrimaryType(), StaticProperty.ALLOWS_ZERO_OR_MORE);
        RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.VARIABLE, rangeVariable.getVariableQName().getDisplayName(), 0);
        setSequence(
                TypeChecker.strictTypeCheck(
                        getSequence(), sequenceType, role, visitor.getStaticContext()));
    }

    /**
     * Get a tuple stream that implements the functionality of this clause, taking its
     * input from another tuple stream which this clause modifies
     *
     * @param base    the input tuple stream
     * @param context the XPath dynamic context
     * @return the output tuple stream
     */
    @Override
    public TuplePull getPullStream(TuplePull base, XPathContext context) {
        if (allowsEmpty) {
            return new ForClauseOuterPull(base, this);
        } else {
            return new ForClausePull(base, this);
        }
    }

    /**
     * Get a push-mode tuple stream that implements the functionality of this clause, supplying its
     * output to another tuple stream
     *
     * @param destination the output tuple stream
     * @param output the destination for the result
     * @param context     the dynamic evaluation context
     * @return the push tuple stream that implements the functionality of this clause of the FLWOR
     *         expression
     */
    @Override
    public TuplePush getPushStream(TuplePush destination, Outputter output, XPathContext context) {
        if (allowsEmpty) {
            return new ForClauseOuterPush(output, destination, this);
        } else {
            return new ForClausePush(output, destination, this);
        }
    }

    /**
     * Convert where clause to a predicate.
     *
     * @param flwor           the FLWOR expression (sans the relevant part of the where clause)
     * @param visitor         the expression visitor
     * @param contextItemType the item type of the context item
     * @param condition       the predicate to be added. This will always be a single term (never a composite condition
     *                        using "and"), as the where clause is split into separate terms before calling this method
     * @return true if the expression has been changed, that is, if the where clause has been converted
     * @throws XPathException if an error is encountered
     */

    public boolean addPredicate(FLWORExpression flwor, ExpressionVisitor visitor, ContextItemStaticInfo contextItemType, Expression condition) throws XPathException {
        Configuration config = getConfiguration();
        Optimizer opt = visitor.obtainOptimizer();
        boolean debug = config.getBooleanProperty(Feature.TRACE_OPTIMIZER_DECISIONS);

        // assert: condition has no dependency on context item. We removed any such dependency before we got here.

        final TypeHierarchy th = config.getTypeHierarchy();
        Expression head = null;
        Expression selection = getSequence();
        ItemType selectionContextItemType = contextItemType == null ? null : contextItemType.getItemType();
        if (getSequence() instanceof SlashExpression) {
            if (((SlashExpression) getSequence()).isAbsolute()) {
                head = ((SlashExpression) getSequence()).getFirstStep();
                selection = ((SlashExpression) getSequence()).getRemainingSteps();
                selectionContextItemType = head.getItemType();
            } else {
                SlashExpression p = ((SlashExpression) getSequence()).tryToMakeAbsolute();
                if (p != null) {
                    setSequence(p);
                    head = p.getFirstStep();
                    selection = p.getRemainingSteps();
                    selectionContextItemType = head.getItemType();
                }
            }
        }

        boolean changed = false;

        // Process each term in the where clause independently

        if (positionVariable != null &&
                (condition instanceof ValueComparison || condition instanceof GeneralComparison || condition instanceof CompareToIntegerConstant) &&
                ExpressionTool.dependsOnVariable(condition, new Binding[]{positionVariable})) {
            ComparisonExpression comp = (ComparisonExpression) condition;
            Expression[] operands = new Expression[]{comp.getLhsExpression(), comp.getRhsExpression()};

            if (ExpressionTool.dependsOnVariable(flwor, new Binding[]{positionVariable})) {
                // cannot convert a positional where clause into a positional predicate if there are
                // other references to the position variable
                return false;
            }

            for (int op = 0; op < 2; op++) {

                // If the where clause is a simple test on the position variable, for example
                //    for $x at $p in EXPR where $p = 5 return A
                // then absorb the where condition into a predicate, rewriting it as
                //    for $x in EXPR[position() = 5] return A
                // This takes advantage of the optimizations applied to positional filter expressions
                // Only do this if the sequence expression has not yet been changed, because
                // the position in a predicate after the first is different.  And only do it if this
                // is the only reference to the position variable, because if there are other references,
                // the existence of the predicate will change the values of the position variable.
                Binding[] thisVar = {this.getRangeVariable()};
                if (positionVariable != null && operands[op] instanceof VariableReference && !changed) {
                    List<VariableReference> varRefs = new ArrayList<>();
                    ExpressionTool.gatherVariableReferences(condition, positionVariable, varRefs);
                    if (varRefs.size() == 1 && varRefs.get(0) == operands[op] &&
                            !ExpressionTool.dependsOnFocus(operands[1 - op]) &&
                            !ExpressionTool.dependsOnVariable(operands[1 - op], thisVar)) {
                        RetainedStaticContext rsc = new RetainedStaticContext(visitor.getStaticContext());
                        Expression position =
                                SystemFunction.makeCall("position", rsc);
                        Expression predicate = condition.copy(new RebindingMap());
                        Operand child = op == 0
                                ? ((ComparisonExpression)predicate).getLhs()
                                : ((ComparisonExpression)predicate).getRhs();
                        child.setChildExpression(position);
                        if (debug) {
                            opt.trace("Replaced positional variable in predicate by position()", predicate);
                        }
                        selection = new FilterExpression(selection, predicate);
                        ExpressionTool.copyLocationInfo(predicate, selection);
                        ContextItemStaticInfo cit = config.makeContextItemStaticInfo(selectionContextItemType, true);
                        selection = selection.typeCheck(visitor, cit);
                        if (!ExpressionTool.dependsOnVariable(flwor, new Binding[]{positionVariable})) {
                            positionVariable = null;
                        }
                        changed = true;
                        break;
                    }
                }
            }
        }

        if (positionVariable == null) {
            Binding[] thisVar = {this.getRangeVariable()};
            if (opt.isVariableReplaceableByDot(condition, thisVar)) {

                // When rewriting the where expression as a filter, we have to replace references to the
                // range variable by references to the context item. If we can do this directly, we do. But
                // if the reference to the range variable occurs inside a predicate, or on the rhs of slash,
                // we have to bind a new variable to the context item. So for example "for $x in S where
                // T[abc = $x]" gets rewritten as "for $x in S[let $dot := . return T[abc = $dot]]"
                //if (useDotDirectly) {
                Expression replacement = new ContextItemExpression();

                boolean found = ExpressionTool.inlineVariableReferences(condition, this.getRangeVariable(), replacement);
                if (found) {
                    ContextItemStaticInfo cit = config.makeContextItemStaticInfo(getSequence().getItemType(), true);
                    Expression predicate = condition.typeCheck(visitor, cit);
                    // If the result of the predicate might be a number, wrap it in a call of boolean()
                    Affinity rel = th.relationship(predicate.getItemType(), BuiltInAtomicType.INTEGER);
                    if (rel != Affinity.DISJOINT) {
                        RetainedStaticContext rsc = new RetainedStaticContext(visitor.getStaticContext());
                        predicate = SystemFunction.makeCall("boolean", rsc, predicate);
                        assert predicate != null;
                    }
                    selection = new FilterExpression(selection, predicate);
                    ExpressionTool.copyLocationInfo(predicate, selection);
                    cit = config.makeContextItemStaticInfo(selectionContextItemType, true);
                    selection = selection.typeCheck(visitor, cit);
                    changed = true;
                }
            }

        }
        if (changed) {

            if (head == null) {
                setSequence(selection);
            } else if (head instanceof RootExpression && selection.isCallOn(KeyFn.class)) {
                setSequence(selection);
            } else {
                Expression path = ExpressionTool.makePathExpression(head, selection);
                if (path instanceof SlashExpression) {
                    ExpressionTool.copyLocationInfo(condition, path);
                    Expression k = visitor.obtainOptimizer().convertPathExpressionToKey((SlashExpression) path, visitor);
                    if (k == null) {
                        setSequence(path);
                    } else {
                        setSequence(k);
                    }
                    sequenceOp.typeCheck(visitor, contextItemType);
                    sequenceOp.optimize(visitor, contextItemType);
                }
            }
        }
        return changed;
    }

    /**
     * Process the subexpressions of this clause
     *
     * @param processor the expression processor used to process the subexpressions
     */
    @Override
    public void processOperands(OperandProcessor processor) throws XPathException {
        processor.processOperand(sequenceOp);
    }

    @Override
    public void gatherVariableReferences(final ExpressionVisitor visitor, Binding binding, List<VariableReference> references) {
        ExpressionTool.gatherVariableReferences(getSequence(), binding, references);
    }

    @Override
    public void refineVariableType(ExpressionVisitor visitor, List<VariableReference> references, Expression returnExpr) {
        ItemType actualItemType = getSequence().getItemType();
        if (actualItemType instanceof ErrorType) {
            actualItemType = AnyItemType.getInstance();
        }
        for (VariableReference ref : references) {
            ref.refineVariableType(actualItemType,
                    allowsEmpty ? StaticProperty.ALLOWS_ZERO_OR_ONE : StaticProperty.EXACTLY_ONE,
                    null, getSequence().getSpecialProperties());
        }
    }

    @Override
    public void addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        PathMap.PathMapNodeSet varPath = getSequence().addToPathMap(pathMap, pathMapNodeSet);
        pathMap.registerPathForVariable(rangeVariable, varPath);
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     */
    @Override
    public void explain(ExpressionPresenter out) throws XPathException {
        out.startElement("for");
        out.emitAttribute("var", getRangeVariable().getVariableQName());
        out.emitAttribute("slot", getRangeVariable().getLocalSlotNumber() + "");
        LocalVariableBinding posVar = getPositionVariable();
        if (posVar != null) {
            out.emitAttribute("at", posVar.getVariableQName());
            out.emitAttribute("at-slot", posVar.getLocalSlotNumber() + "");
        }
        getSequence().export(out);
        out.endElement();
    }

    @Override
    public String toShortString() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C64);
        fsb.append("for $");
        fsb.append(rangeVariable.getVariableQName().getDisplayName());
        fsb.cat(' ');
        LocalVariableBinding posVar = getPositionVariable();
        if (posVar != null) {
            fsb.append("at $");
            fsb.append(posVar.getVariableQName().getDisplayName());
            fsb.cat(' ');
        }
        fsb.append("in ");
        fsb.append(getSequence().toShortString());
        return fsb.toString();
    }

    public String toString() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C64);
        fsb.append("for $");
        fsb.append(rangeVariable.getVariableQName().getDisplayName());
        fsb.cat(' ');
        LocalVariableBinding posVar = getPositionVariable();
        if (posVar != null) {
            fsb.append("at $");
            fsb.append(posVar.getVariableQName().getDisplayName());
            fsb.cat(' ');
        }
        fsb.append("in ");
        fsb.append(getSequence().toString());
        return fsb.toString();
    }
}

