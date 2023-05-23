////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.query.QueryModule;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;

/**
 * This class represents a FLWOR expression, evaluated using tuple streams
 */
public class FLWORExpression extends Expression {

    public List<Clause> clauses;
    public Operand returnClauseOp;

    public FLWORExpression() {}

    public void init(List<Clause> clauses, Expression returnClause) {
        this.clauses = clauses;
        boolean looping = false;
        for (Clause c : clauses) {
            if (isLoopingClause(c)) {
                looping = true;
                break;
            }
        }
        this.returnClauseOp = new Operand(this, returnClause, looping ? REPEATED_RETURN : SINGLE_RETURN);
    }

    /**
     * Get the list of clauses of the FLWOR expression, in the order they are written.
     * This excludes the return clause
     *
     * @return the list of clauses
     */
    /*@NotNull*/
    public List<Clause> getClauseList() {
        return clauses;
    }

    public static boolean isLoopingClause(Clause c) {
        return c.getClauseKey() == Clause.ClauseName.FOR || c.getClauseKey() == Clause.ClauseName.GROUP_BY
                || c.getClauseKey() == Clause.ClauseName.WINDOW;
    }

    /**
     * Get the return clause of the FLWOR expression
     *
     * @return the expression contained in the return clause
     */

    /*@NotNull*/
    public Expression getReturnClause() {
        return returnClauseOp.getChildExpression();
    }

    /**
     * Determine whether a given variable binding belongs to this FLWOR expression
     *
     * @param binding the binding being sought
     * @return true if this binding belongs to one of the clauses of this FLWOR expression
     */

    @Override
    public boolean hasVariableBinding(Binding binding) {
        for (Clause c : clauses) {
            if (clauseHasBinding(c, binding)) {
                return true;
            }
        }
        return false;
    }

    private boolean clauseHasBinding(Clause c, Binding binding) {
        for (Binding b : c.getRangeVariables()) {
            if (b == binding) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ask whether common subexpressions found in the operands of this expression can
     * be extracted and evaluated outside the expression itself. The result is irrelevant
     * in the case of operands evaluated with a different focus, which will never be
     * extracted in this way, even if they have no focus dependency.
     *
     * @return false for this kind of expression
     */
    @Override
    public boolean allowExtractingCommonSubexpressions() {
        return false;
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression). The default implementation does nothing.
     *
     *
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during expression
     *          rewriting
     */
    /*@NotNull*/
    @Override
    public Expression simplify() throws XPathException {
        OperandProcessor simplifier =
                op -> op.setChildExpression(op.getChildExpression().simplify());
        for (Clause c : clauses) {
            c.processOperands(simplifier);
        }
        returnClauseOp.setChildExpression(getReturnClause().simplify());
        return this;
    }

    /**
     * Perform type checking of an expression and its subexpressions. This is the second phase of
     * static optimization.
     * <p>This checks statically that the operands of the expression have
     * the correct type; if necessary it generates code to do run-time type checking or type
     * conversion. A static type error is reported only if execution cannot possibly succeed, that
     * is, if a run-time type error is inevitable. The call may return a modified form of the expression.</p>
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable. However, the types of such functions and
     * variables may not be accurately known if they have not been explicitly declared.</p>
     * <p>If the implementation returns a value other than "this", then it is required to ensure that
     * the location information in the returned expression have been set up correctly.
     * It should not rely on the caller to do this, although for historical reasons many callers do so.</p>
     *
     *
       param visitor         an expression visitor
     * @param  contextInfo   static information about the dynamic context
     * @return the original expression, rewritten to perform necessary run-time type checks,
     *         and to perform other type-related optimizations
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during this phase
     *          (typically a type error)
     */
    /*@NotNull*/
    @Override
    public Expression typeCheck(final ExpressionVisitor visitor, final ContextItemStaticInfo contextInfo)
            throws XPathException {

        OperandProcessor typeChecker = op -> op.typeCheck(visitor, contextInfo);
        for (int i = 0; i < clauses.size(); i++) {
            clauses.get(i).processOperands(typeChecker);
            clauses.get(i).typeCheck(visitor, contextInfo);
            LocalVariableBinding[] bindings = clauses.get(i).getRangeVariables();

            for (Binding b : bindings) {
                List<VariableReference> references = new ArrayList<>();
                for (int j = i; j < clauses.size(); j++) {
                    clauses.get(j).gatherVariableReferences(visitor, b, references);
                }
                ExpressionTool.gatherVariableReferences(getReturnClause(), b, references);
                clauses.get(i).refineVariableType(visitor, references, getReturnClause());
            }
        }
        returnClauseOp.typeCheck(visitor, contextInfo);
        return this;
    }

    /**
     * Determine whether this expression implements its own method for static type checking
     *
     * @return true - this expression has a non-trivial implementation of the staticTypeCheck()
     *         method
     */

    @Override
    public boolean implementsStaticTypeCheck() {
        for (Clause c : clauses) {
            switch (c.getClauseKey()) {
                case LET:
                case WHERE:
                    continue;
                default:
                    return false;
            }
        }
        return true;
    }

    /**
     * Static type checking for let expressions is delegated to the expression itself,
     * and is performed on the "return" expression, to allow further delegation to the branches
     * of a conditional
     *
     *
     * @param req                 the required type
     * @param backwardsCompatible true if backwards compatibility mode applies
     * @param role                the role of the expression in relation to the required type
     * @param visitor             an expression visitor
     * @return the expression after type checking (perhaps augmented with dynamic type checking code)
     * @throws XPathException if failures occur, for example if the static type of one branch of the conditional
     *                        is incompatible with the required type
     */

    @Override
    public Expression staticTypeCheck(SequenceType req,
                                      boolean backwardsCompatible,
                                      RoleDiagnostic role, ExpressionVisitor visitor)
            throws XPathException {
        // only called if implementsStaticTypeCheck() returns true
        TypeChecker tc = visitor.getConfiguration().getTypeChecker(backwardsCompatible);
        returnClauseOp.setChildExpression(
                tc.staticTypeCheck(getReturnClause(), req, role, visitor));
        return this;
    }

    /**
     * Determine the data type of the items returned by the expression.
     *
     * @return a value such as Type.STRING, Type.BOOLEAN, Type.NUMBER,
     *         Type.NODE, or Type.ITEM (meaning not known at compile time)
     */
    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return getReturnClause().getItemType();
    }

    /**
     * Compute the static cardinality of this expression
     *
     * @return the computed cardinality, as one of the values {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ZERO_OR_ONE},
     *         {@link net.sf.saxon.expr.StaticProperty#EXACTLY_ONE}, {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ONE_OR_MORE},
     *         {@link net.sf.saxon.expr.StaticProperty#ALLOWS_ZERO_OR_MORE}
     */
    @Override
    protected int computeCardinality() {
        // Assume that simple cases, like a FLWOR whose clauses are all "let" clauses, will have been converted into something else.
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
    }

    /**
     * Get the immediate sub-expressions of this expression, with information about the relationship
     * of each expression to its parent expression. Default implementation
     * returns a zero-length array, appropriate for an expression that has no
     * sub-expressions.
     *
     * @return an iterator containing the sub-expressions of this expression
     */
    @Override
    public Iterable<Operand> operands() {
        final List<Operand> list = new ArrayList<>(5);
        boolean repeatable = false;
        try {
            for (Clause c : clauses) {
                c.processOperands(list::add);
                if (c instanceof ForClause) {
                    repeatable = true;
                }
            }
        } catch (XPathException e) {
            throw new IllegalStateException(e);
        }
        list.add(returnClauseOp);
        return list;

    }

    private static final OperandRole SINGLE_RETURN =
            new OperandRole(0, OperandUsage.TRANSMISSION, SequenceType.ANY_SEQUENCE);

    private static final OperandRole REPEATED_RETURN =
            new OperandRole(OperandRole.HIGHER_ORDER, OperandUsage.TRANSMISSION, SequenceType.ANY_SEQUENCE);

    /**
     * Check to ensure that this expression does not contain any inappropriate updating subexpressions.
     * This check is overridden for those expressions that permit updating subexpressions.
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if the expression has a non-permitted updateing subexpression
     */

    @Override
    public void checkForUpdatingSubexpressions() throws XPathException {
        OperandProcessor processor = op -> {
            op.getChildExpression().checkForUpdatingSubexpressions();
            if (op.getChildExpression().isUpdatingExpression()) {
                throw new XPathException(
                        "An updating expression cannot be used in a clause of a FLWOR expression", "XUST0001");
            }
        };
        for (Clause c : clauses) {
            c.processOperands(processor);
        }
        getReturnClause().checkForUpdatingSubexpressions();
    }

    /**
     * Determine whether this is an updating expression as defined in the XQuery update specification
     *
     * @return true if this is an updating expression
     */

    @Override
    public boolean isUpdatingExpression() {
        return getReturnClause().isUpdatingExpression();
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
        return ITERATE_METHOD | PROCESS_METHOD;
    }

    /**
     * Add a representation of this expression to a PathMap. The PathMap captures a map of the nodes visited
     * by an expression in a source tree.
     * <p>The default implementation of this method assumes that an expression does no navigation other than
     * the navigation done by evaluating its subexpressions, and that the subexpressions are evaluated in the
     * same context as the containing expression. The method must be overridden for any expression
     * where these assumptions do not hold. For example, implementations exist for AxisExpression, ParentExpression,
     * and RootExpression (because they perform navigation), and for the doc(), document(), and collection()
     * functions because they create a new navigation root. Implementations also exist for PathExpression and
     * FilterExpression because they have subexpressions that are evaluated in a different context from the
     * calling expression.</p>
     *
     * @param pathMap        the PathMap to which the expression should be added
     * @param pathMapNodeSet the PathMapNodeSet to which the paths embodied in this expression should be added
     * @return the pathMapNodeSet representing the points in the source document that are both reachable by this
     * expression, and that represent possible results of this expression. For an expression that does
     * navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     * expressions, it is the same as the input pathMapNode.
     */

    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, /*@Nullable*/ PathMap.PathMapNodeSet pathMapNodeSet) {
        for (Clause c : clauses) {
            c.addToPathMap(pathMap, pathMapNodeSet);
        }
        return getReturnClause().addToPathMap(pathMap, pathMapNodeSet);
    }

    /**
     * Inject tracing (or other monitoring) code for each clause
     * @param injector the code injector responsible for processing each clause of the FLWOR expression
     */

    public void injectCode(CodeInjector injector) {
        if (injector != null) {
            for (Clause clause : clauses) {
                // if there's already a TraceClause, do nothing
                if (clause instanceof TraceClause) {
                    return;
                }
            }
            List<Clause> expandedList = new ArrayList<>(clauses.size() * 2);
            expandedList.add(clauses.get(0));
            for (int i = 1; i < clauses.size(); i++) {
                Clause extra = injector.injectClause(this, clauses.get(i - 1));
                if (extra != null) {
                    expandedList.add(extra);
                }
                expandedList.add(clauses.get(i));
            }
            Clause extra = injector.injectClause(this, clauses.get(clauses.size() - 1));
            if (extra != null) {
                expandedList.add(extra);
            }
            clauses = expandedList;
        }
    }
    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     */
    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("FLWOR", this);
        for (Clause c : clauses) {
            c.explain(out);
        }
        out.startSubsidiaryElement("return");
        getReturnClause().export(out);
        out.endSubsidiaryElement();
        out.endElement();
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings variable bindings to be replaced
     */
    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        //verifyParentPointers();
        List<Clause> newClauses = new ArrayList<>();
        FLWORExpression f2 = new FLWORExpression();
        for (Clause c : clauses) {
            Clause c2 = c.copy(f2, rebindings);
            c2.setLocation(c.getLocation());
            c2.setRepeated(c.isRepeated());
            LocalVariableBinding[] oldBindings = c.getRangeVariables();
            LocalVariableBinding[] newBindings = c2.getRangeVariables();
            assert oldBindings.length == newBindings.length;
            for (int i=0; i<oldBindings.length; i++) {
                rebindings.put(oldBindings[i], newBindings[i]);
            }
            newClauses.add(c2);
        }
        f2.init(newClauses, getReturnClause().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, f2);
        return f2;
    }

    /**
     * Replace this expression by a simpler expression that delivers the results without regard
     * to order.
     *
     * @param retainAllNodes set to true if the result must contain exactly the same nodes as the
     *                       original; set to false if the result can eliminate (or introduce) duplicates.
     * @param forStreaming  set to true if optimizing for streaming
     */
    @Override
    public Expression unordered(boolean retainAllNodes, boolean forStreaming) throws XPathException {
        for (Clause c : clauses) {
            if (c instanceof ForClause && ((ForClause)c).getPositionVariable() == null) {
                ((ForClause)c).setSequence(((ForClause)c).getSequence().unordered(retainAllNodes, forStreaming));
            }
        }
        returnClauseOp.setChildExpression(
                getReturnClause().unordered(retainAllNodes, forStreaming));
        return this;
    }

    /*@Nullable*/
    private Binding[] extendBindingList(/*@Nullable*/ Binding[] bindings, /*@Nullable*/ LocalVariableBinding[] moreBindings) {
        if (bindings == null) {
            bindings = new Binding[0];
        }
        if (moreBindings == null || moreBindings.length == 0) {
            return bindings;
        } else {
            Binding[] b2 = new Binding[bindings.length + moreBindings.length];
            System.arraycopy(bindings, 0, b2, 0, bindings.length);
            System.arraycopy(moreBindings, 0, b2, bindings.length, moreBindings.length);
            return b2;
        }
    }

    @Override
    public int getEvaluationMethod() {
        return Expression.PROCESS_METHOD;
    }


    /*@NotNull*/
    @Override
    public Expression optimize(
            final ExpressionVisitor visitor,
            final ContextItemStaticInfo contextItemType) throws XPathException {

        Optimizer opt = visitor.obtainOptimizer();
        //verifyParentPointers();
        // Optimize all the subexpressions
        for (Clause c : clauses) {
            c.processOperands(op -> op.optimize(visitor, contextItemType));
            c.optimize(visitor, contextItemType);
        }

        // Optimize the return expression
        returnClauseOp.setChildExpression(
                getReturnClause().optimize(visitor, contextItemType));

        // For a very simple "for" or "let" expression, convert it to a ForExpression or LetExpression now
        if (clauses.size() == 1) {
            Clause c = clauses.get(0);
            if (c instanceof LetClause ||
                    (c instanceof ForClause && ((ForClause)c).getPositionVariable() == null)) {
                return rewriteForOrLet(visitor, contextItemType);
            }
        }

        // If any 'let' clause declares a variable that is used only once, then inline it. If the variable
        // is not used at all, then eliminate it

        boolean tryAgain;
        boolean changed = false;
        do {
            tryAgain = false;
            for (Clause c : clauses) {
                if (c.getClauseKey() == Clause.ClauseName.LET) {
                    LetClause lc = (LetClause) c;
                    if (!ExpressionTool.dependsOnVariable(this, new Binding[]{lc.getRangeVariable()})) {
                        clauses.remove(c);
                        tryAgain = true;
                        break;
                    }
                    boolean suppressInlining = false;
                    for (Clause c2 : clauses) {
                        if (c2.containsNonInlineableVariableReference(lc.getRangeVariable())) {
                            suppressInlining = true;
                            break;
                        }
                    }
                    if (!suppressInlining) {
                        boolean oneRef = lc.getRangeVariable().getNominalReferenceCount() == 1;
                        boolean simpleSeq = lc.getSequence() instanceof VariableReference ||
                                lc.getSequence() instanceof Literal;
                        if (oneRef || simpleSeq) {
                            ExpressionTool.replaceVariableReferences(this, lc.getRangeVariable(), lc.getSequence(), true);
                            clauses.remove(c);
                            opt.trace("Inlined let $" + lc.getRangeVariable().getVariableQName().getDisplayName(), this);
                            if (clauses.isEmpty()) {
                                return getReturnClause();
                            }
                            tryAgain = true;
                            break;
                        }
                    }
                }
            }
            changed |= tryAgain;
        } while (tryAgain);

        // If changed, remove any redundant trace clauses
        if (changed) {
            for (int i = clauses.size() - 1; i >= 1; i--) {
                if (clauses.get(i).getClauseKey() == Clause.ClauseName.TRACE && clauses.get(i - 1).getClauseKey() == Clause.ClauseName.TRACE) {
                    clauses.remove(i);
                }
            }
        }

        // If any 'where' clause depends on the context item, remove this dependency, because it makes
        // it easier to rearrange where clauses as predicates
        boolean depends = false;
        for (Clause w : clauses) {
            if (w instanceof WhereClause && ExpressionTool.dependsOnFocus(((WhereClause) w).getPredicate())) {
                depends = true;
                break;
            }
        }
        if (depends && contextItemType != null) {
            Expression expr1 = ExpressionTool.tryToFactorOutDot(this, contextItemType.getItemType());
            if (expr1 == null || expr1 == this) {
                //no optimisation possible
                return this;
            }
            resetLocalStaticProperties();
            return expr1.optimize(visitor, contextItemType);
        }

        // Now convert any terms within WHERE clauses where possible into predicates on the appropriate
        // expression bound to a variable on a for clause. This enables the resulting filter expression
        // to be handled using indexing (in Saxon-EE), and it also reduces the number of items that need
        // to be tested against the predicate

        Expression expr2 = rewriteWhereClause(visitor, contextItemType);
        if (expr2 != null && expr2 != this) {
            return expr2.optimize(visitor, contextItemType);
        }

        // If the FLWOR expression consists entirely of FOR and LET clauses, convert it to a ForExpression
        // or LetExpression. This is largely to take advantage of existing optimizations implemented for those
        // expressions.

        boolean allForOrLetExpr = true;
        for (Clause c : clauses) {
            if (c instanceof ForClause) {
                if (((ForClause)c).getPositionVariable() != null) {
                    allForOrLetExpr = false;
                    break;
                }
            } else if (!(c instanceof LetClause)) {
                allForOrLetExpr = false;
                break;
            }

        }

        if (allForOrLetExpr) {
            return rewriteForOrLet(visitor, contextItemType);
        }

        return this;
    }

    /**
     * @param visitor         the expression visitor
     * @param contextItemType the type of the context item
     * @return We return this expression, with WhereClauses moved up as far as possible in the list of clauses.
     *         A Where clause cannot move above a Count clause because it changes the number of tuples in the tuple stream.
     *         Alternatively, return null if no rewriting is possible.
     * @throws XPathException if the rewrite fails for any reason
     */

    /*@Nullable*/
    private Expression rewriteWhereClause(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType)
            throws XPathException {
        WhereClause whereClause;
        int whereIndex = 0;
        class WhereClauseStruct {
            int whereIndex = 0;
            WhereClause whereClause;
        }
        List<WhereClauseStruct> whereList = new ArrayList<>();

        for (Clause c : clauses) {
            if (c instanceof WhereClause) {
                WhereClauseStruct wStruct = new WhereClauseStruct();
                wStruct.whereClause = (WhereClause) c;

                //keep track of whereclause from the end of the list of clauses.
                //We are always attempting to rewrite whereclauses from left to right,
                // therefore index will always be in snyc
                wStruct.whereIndex = clauses.size() - whereIndex;
                whereList.add(wStruct);
            }
            whereIndex++;
        }

        if (whereList.size() == 0) {
            return null;
        }

        while (!whereList.isEmpty()) {
            whereClause = whereList.get(0).whereClause;
            whereIndex = whereList.get(0).whereIndex;
            Expression condition = whereClause.getPredicate();
            List<Expression> list = new ArrayList<>(5);
            BooleanExpression.listAndComponents(condition, list);
            for (int i = list.size() - 1; i >= 0; i--) {
                Expression term = list.get(i);
                for (int c = clauses.size() - whereIndex - 1; c >= 0; c--) {
                    Clause clause = clauses.get(c);
                    Binding[] bindingList = clause.getRangeVariables();

                    // Find the first clause prior to the where clause that declares variables on which the
                    // term of the where clause depends

                    if (ExpressionTool.dependsOnVariable(term, bindingList) || clause.getClauseKey() == Clause.ClauseName.COUNT) {
                        // remove this term from the where clause
                        Expression removedExpr = list.remove(i);
                        if (list.isEmpty()) {
                            // the where clause has no terms left, so remove the clause
                            clauses.remove(clauses.size() - whereIndex);
                        } else {
                            // change the predicate of the where clause to use only those terms that remain
                            whereClause.setPredicate(makeAndCondition(list));
                        }
                        if ((clause instanceof ForClause) && !((ForClause) clause).isAllowingEmpty()) {
                            // if the clause is a "for" clause, try to add the term as a predicate
                            boolean added = ((ForClause) clause).addPredicate(this, visitor, contextItemType, term);
                            //If we cannot add the WhereClause term as a predicate then put it back into the list of clauses
                            if (!added) {
                                clauses.add(c + 1, new WhereClause(this, removedExpr));
                            }
                        } else {
                            // the clause is not a "for" clause, so just move the "where" to this place in the list of clauses
                            WhereClause newWhere = new WhereClause(this, term);
                            newWhere.setLocation(whereClause.getLocation());
                            clauses.add(c + 1, newWhere);
                        }
                        // we found a variable on which the term depends so we can't move it any further
                        break;
                    }
                }
                if (list.size() - 1 == i) {
                    list.remove(i);
                    if (list.isEmpty()) {
                        clauses.remove(clauses.size() - whereIndex);
                    } else {
                        whereClause.setPredicate(makeAndCondition(list));
                    }
                    WhereClause newWhere = new WhereClause(this, term);
                    newWhere.setLocation(whereClause.getLocation());
                    clauses.add(0, newWhere);
                }
            }

            whereList.remove(0);
        }
        return this;
    }

    /**
     * Recursive method to make a list of expressions into a AndExpression
     *
     * @param list of Expression
     * @return And Expression of list of expressions
     */
    private Expression makeAndCondition(List<Expression> list) {
        if (list.size() == 1) {
            return list.get(0);
        } else {
            return new AndExpression(list.get(0), makeAndCondition(list.subList(1, list.size())));
        }
    }

    /**
     * Rewrite a FLWOR expression that consists entirely of "for" and "let" clauses as
     * a LetExpression or ForExpression
     *
     * @param visitor         - ExpressionVisitor
     * @param contextItemType -  ExpressionVisitor.ContextItemTyp
     * @return the rewritten expression
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs
     */
    /*@NotNull*/
    private Expression rewriteForOrLet(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {

        Expression action = getReturnClause();
        CodeInjector injector = null;
        if (visitor.getStaticContext() instanceof QueryModule) {
            injector = ((QueryModule) visitor.getStaticContext()).getCodeInjector();
        }

        for (int i = clauses.size() - 1; i >= 0; i--) {

            if (clauses.get(i) instanceof ForClause) {
                ForClause forClause = (ForClause) clauses.get(i);
                ForExpression forExpr;
                if (forClause.isAllowingEmpty()) {
                    forExpr = new OuterForExpression();
                } else {
                    forExpr = new ForExpression();
                }

                forExpr.setLocation(forClause.getLocation());
                forExpr.setRetainedStaticContext(getRetainedStaticContext());
                //forExpr.setParentExpression(getParentExpression());
                forExpr.setAction(action);

                forExpr.setSequence(forClause.getSequence());
                forExpr.setVariableQName(forClause.getRangeVariable().getVariableQName());
                forExpr.setRequiredType(forClause.getRangeVariable().getRequiredType());
                ExpressionTool.rebindVariableReferences(action, forClause.getRangeVariable(), forExpr);
                action = forExpr;

//                if (injector != null) {
//                    action = injector.inject(action, visitor.getStaticContext(), LocationKind.FOR_EXPRESSION, forExpr.getVariableQName());
//                }

            } else {
                LetClause letClause = (LetClause) clauses.get(i);
                LetExpression letExpr = new LetExpression();
                letExpr.setLocation(letClause.getLocation());
                letExpr.setRetainedStaticContext(getRetainedStaticContext());
                //letExpr.setParentExpression(getParentExpression());
                letExpr.setAction(action);
                letExpr.setSequence(letClause.getSequence());
                letExpr.setVariableQName(letClause.getRangeVariable().getVariableQName());
                letExpr.setRequiredType(letClause.getRangeVariable().getRequiredType());
                if (letClause.getRangeVariable().isIndexedVariable()) {
                    letExpr.setIndexedVariable();
                }
                //letExpr.setRefCount(letClause.getRangeVariable().getNominalReferenceCount());
                ExpressionTool.rebindVariableReferences(action, letClause.getRangeVariable(), letExpr);
                action = letExpr;

//                if (injector != null) {
//                    action = injector.inject(action, visitor.getStaticContext(), LocationKind.LET_EXPRESSION, letExpr.getVariableQName());
//                }
            }

        }
        action = action.typeCheck(visitor, contextItemType);
        action = action.optimize(visitor, contextItemType);
        return action;
    }

    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions. This default implementation handles iteration for expressions that
     * return singleton values: for non-singleton expressions, the subclass must
     * provide its own implementation.
     *
     * @param context supplies the context for evaluation
     * @return a SequenceIterator that can be used to iterate over the result
     *         of the expression
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */
    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        TuplePull stream = new SingularityPull();
        for (Clause c : clauses) {
            stream = c.getPullStream(stream, context);
        }
        return new ReturnClauseIterator(stream, this, context);
    }


    /**
     * Process the instruction, without returning any tail calls
     *
     *
     * @param output the destination for the result
     * @param context The dynamic context, giving access to the current node,
     *                the current variables, etc.
     * @throws XPathException if a dynamic error occurs
     */
    @Override
    public void process(Outputter output, XPathContext context) throws XPathException {
        TuplePush destination = new ReturnClausePush(output, getReturnClause());
        for (int i = clauses.size() - 1; i >= 0; i--) {
            Clause c = clauses.get(i);
            destination = c.getPushStream(destination, output, context);
        }
        destination.processTuple(context);
        destination.close();
    }

    /**
     * Evaluate an updating expression, adding the results to a Pending Update List.
     * The default implementation of this method, which is used for non-updating expressions,
     * throws an UnsupportedOperationException
     *
     * @param context the XPath dynamic evaluation context
     * @param pul     the pending update list to which the results should be written
     * @throws net.sf.saxon.trans.XPathException
     *                                       if evaluation fails
     * @throws UnsupportedOperationException if the expression is not an updating expression
     */


    @Override
    public void evaluatePendingUpdates(XPathContext context, PendingUpdateList pul) throws XPathException {
        TuplePull stream = new SingularityPull();
        for (Clause c : clauses) {
            stream = c.getPullStream(stream, context);
        }
        while (stream.nextTuple(context)) {
            getReturnClause().evaluatePendingUpdates(context, pul);
        }
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
        return "FLWOR";
    }

    /**
     * Produce a short string identifying the expression for use in error messages
     *
     * @return a short string, sufficient to identify the expression
     */
    @Override
    public String toShortString() {
        FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C64);
        sb.append(clauses.get(0).toShortString());
        sb.append(" ... return ");
        sb.append(getReturnClause().toShortString());
        return sb.toString();
    }

    /**
     * Display the expression as a string
     */

    public String toString() {
        FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C64);
        for (Clause c : clauses) {
            sb.append(c.toString());
            sb.cat(' ');
        }
        sb.append(" return ");
        sb.append(getReturnClause().toString());
        return sb.toString();
    }

    /**
     * Determine whether a variable reference found within a clause of a FLWOR expression is a looping
     * reference, that is, whether the variable is used more than once
     *
     * @param binding the variable binding, which may be bound in a clause of the same FLWOR expression,
     *                or in some containing expression
     * @return true if a reference to the variable occurs within a loop relative to the binding, that is, if the
     *         variable's value is used more than once. Note that this method only detects a loop that is due to the clauses
     *         of this FLWOR expression itself. A loop in an inner expression or outer expression of the FLWOR expression must
     *         be detected by the caller.
     */

    public boolean hasLoopingVariableReference(final Binding binding) {

        // Determine the clause that binds the variable (if any)

        int bindingClause = -1;
        for (int i = 0; i < clauses.size(); i++) {
            if (clauseHasBinding(clauses.get(i), binding)) {
                bindingClause = i;
                break;
            }
        }

        boolean boundOutside = bindingClause < 0;
        if (boundOutside) {
            bindingClause = 0;
        }

        // Determine the last clause that contains a reference to the variable.
        // (If any reference to the variable is a looping reference, then the last one will be)

        int lastReferencingClause = clauses.size(); // indicates the return clause
        if (!ExpressionTool.dependsOnVariable(getReturnClause(), new Binding[]{binding})) {
            // artifice to get a response value from the generic processExpression() method
            final List<Boolean> response = new ArrayList<>();
            OperandProcessor checker = op -> {
                if (response.isEmpty() &&
                        ExpressionTool.dependsOnVariable(op.getChildExpression(), new Binding[]{binding})) {
                    response.add(true);
                }
            };
            for (int i = clauses.size() - 1; i >= 0; i--) {
                try {
                    clauses.get(i).processOperands(checker);
                    if (!response.isEmpty()) {
                        lastReferencingClause = i;
                        break;
                    }
                } catch (XPathException e) {
                    assert false;
                }
            }
        }

        // If any clause between the binding clause and the last referencing clause is a looping clause,
        // then the variable is used within a loop

        for (int i = lastReferencingClause - 1; i >= bindingClause; i--) {
            if (isLoopingClause(clauses.get(i))) {
                return true;
            }
        }

        // otherwise there is no loop caused by the clauses of the FLWOR expression itself.

        return false;
    }

}

