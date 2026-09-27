////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.BooleanFn;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XmlProcessingException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.jiter.ConcatenatingIterable;
import net.sf.saxon.type.*;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;


/**
 * Compiled representation of an xsl:choose or xsl:if element in the stylesheet.
 * Also used for typeswitch in XQuery.
 */

public class Choose extends Instruction implements ConditionalInstruction {

    private final Operand[] conditionOps;
    private final Operand[] actionOps;
    private boolean _isInstruction;


    // The class implements both xsl:choose and xsl:if. There is a list of boolean
    // expressions (conditions) and a list of corresponding actions: the conditions
    // are evaluated in turn, and when one is found that is true, the corresponding
    // action is evaluated. For xsl:if, there is always one condition and one action.
    // An xsl:otherwise is compiled as if it were xsl:when test="true()". If no
    // condition is satisfied, the instruction returns an empty sequence.



    public final static OperandRole CHOICE_ACTION =
            new OperandRole(OperandRole.IN_CHOICE_GROUP, OperandUsage.TRANSMISSION, SequenceType.ANY_SEQUENCE);

    
    /**
     * Construct an xsl:choose instruction
     *
     * @param conditions the conditions to be tested, in order
     * @param actions    the actions to be taken when the corresponding condition is true
     */

    public Choose(Expression[] conditions, Expression[] actions) {
        assert conditions.length == actions.length;
        conditionOps = new Operand[conditions.length];
        for (int i=0; i<conditions.length; i++) {
            conditionOps[i] = new Operand(this, conditions[i], OperandRole.INSPECT);
        }
        actionOps = new Operand[actions.length];
        for (int i=0; i<actions.length; i++) {
            actionOps[i] = new Operand(this, actions[i], CHOICE_ACTION);
        }
    }

    /**
     * Make a simple conditional expression (if (condition) then (thenExp) else (elseExp)
     *
     * @param condition the condition to be tested
     * @param thenExp   the expression to be evaluated if the condition is true
     * @param elseExp   the expression to be evaluated if the condition is false
     * @return the expression
     */

    public static Expression makeConditional(Expression condition, Expression thenExp, Expression elseExp) {
        if (Literal.isEmptySequence(elseExp)) {
            Expression[] conditions = new Expression[]{condition};
            Expression[] actions = new Expression[]{thenExp};
            return new Choose(conditions, actions);
        } else {
            Expression[] conditions = new Expression[]{condition, Literal.makeLiteral(BooleanValue.TRUE, condition)};
            Expression[] actions = new Expression[]{thenExp, elseExp};
            return new Choose(conditions, actions);
        }
    }

    /**
     * Make a simple conditional expression (if (condition) then (thenExp) else ()
     *
     * @param condition the condition to be tested
     * @param thenExp   the expression to be evaluated if the condition is true
     * @return the expression
     */

    public static Expression makeConditional(Expression condition, Expression thenExp) {
        Expression[] conditions = new Expression[]{condition};
        Expression[] actions = new Expression[]{thenExp};
        return new Choose(conditions, actions);
    }

    /**
     * Say whether this choose expression originates as an XSLT instruction
     *
     * @param inst true if this is an xsl:choose or xsl:if instruction
     */

    public void setInstruction(boolean inst) {
        _isInstruction = inst;
    }

    /**
     * Ask whether this expression is an instruction. In XSLT streamability analysis this
     * is used to distinguish constructs corresponding to XSLT instructions from other constructs.
     *
     * @return true if this construct originates as an XSLT instruction
     */

    @Override
    public boolean isInstruction() {
        return _isInstruction;
    }

    /**
     * Get the number of branches (conditions) in the expression
     * @return the number of conditions (which is the same as the number of actions)
     */

    public int size() {
        return conditionOps.length;
    }

    /**
     * Test whether an expression is a single-branch choose, that is, an expression of the form
     * if (condition) then exp else ()
     *
     * @param exp the expression to be tested
     * @return true if the expression is a choose expression and there is only one condition,
     *         so that the expression returns () if this condition is false
     */

    public static boolean isSingleBranchChoice(Expression exp) {
        return exp instanceof Choose && ((Choose) exp).size() == 1;
    }

    public boolean isActionOperand(Expression child) {
        for (Operand actionOp : actionOps) {
            if (actionOp.getChildExpression() == child) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the i'th condition counting from zero
     * @param i the index of the required condition
     * @return the i'th condition counting from zero
     */

    public Expression getCondition(int i) {
        return conditionOps[i].getChildExpression();
    }

    /**
     * Set the i'th condition counting from zero
     *
     * @param i the index of the required condition
     * @param condition the new value of the i'th condition counting from zero
     */


    public void setCondition(int i, Expression condition) {
        conditionOps[i].setChildExpression(condition);
    }

    /**
     * Get the sequence of conditions
     * @return all the conditions, in order
     */

    public Iterable<Operand> conditions() {
        return Arrays.asList(conditionOps);
    }

    /**
     * Get i'th action operand (counting from zero)
     * @param i the action number (counting from zero)
     * @return the i'th action to be evaluated when the corresponding condition is true
     */
    
    public Operand getActionOperand(int i) {
        return actionOps[i];
    }

    /**
     * Get i'th action to be performed or evaluated (counting from zero)
     *
     * @param i the action number (counting from zero)
     * @return the i'th action to be evaluated when the corresponding condition is true
     */

    public Expression getAction(int i) {
        return actionOps[i].getChildExpression();
    }

    /**
     * Set the i'th action counting from zero
     *
     * @param i         the index of the required action
     * @param action the new value of the i'th action counting from zero
     */

    public void setAction(int i, Expression action) {
        actionOps[i].setChildExpression(action);
    }

    /**
     * Get the sequence of actions
     * @return all the actions, in order
     */

    public Iterable<Operand> actions() {
        return Arrays.asList(actionOps);
    }

    /**
     * Get all the operands of the Choose expresssion - that is, all the conditions and all the actions
     * @return all the operands, in undefined order.
     */

    @Override
    public Iterable<Operand> operands() {
        return new ConcatenatingIterable<>(Arrays.asList(conditionOps), Arrays.asList(actionOps));
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
     * Atomize all the action expressions
     */

    public void atomizeActions() {
        for (int i=0; i<size(); i++) {
            setAction(i, Atomizer.makeAtomizer(getAction(i), null));
        }
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     * We assume that if there was
     * only one condition then it was an xsl:if; this is not necessarily so, but
     * it's adequate for tracing purposes.
     */


    @Override
    public int getInstructionNameCode() {
        return size() == 1 ? StandardNames.XSL_IF : StandardNames.XSL_CHOOSE;
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression).
     *
     *
     *
     * @throws XPathException if an error is discovered during expression
     *                        rewriting
     */

    /*@NotNull*/
    @Override
    public Expression simplify() throws XPathException {
        for (int i = 0; i < size(); i++) {
            setCondition(i, getCondition(i).simplify());
            try {
                setAction(i, getAction(i).simplify());
            } catch (XPathException err) {
                // mustn't throw the error unless the branch is actually selected, unless its a type error
                if (err.isTypeError()) {
                    throw err;
                } else {
                    setAction(i, new ErrorExpression(new XmlProcessingException(err)));
                }
            }
        }
        return this;
    }

    private Expression removeRedundantBranches(ExpressionVisitor visitor) {
        Expression result = removeRedundantBranches0(visitor);
        if (result != this) {
            ExpressionTool.copyLocationInfo(this, result);
        }
        return result;
    }

    private Expression removeRedundantBranches0(ExpressionVisitor visitor) {
        // Eliminate a redundant if (false)

        boolean compress = false;
        for (int i = 0; i < size(); i++) {
            Expression condition = getCondition(i);
            if (condition instanceof Literal) {
                compress = true;
                break;
            }
        }
        int count = size();
        if (compress) {
            List<Expression> conditions = new ArrayList<>(count);
            List<Expression> actions = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Expression condition = getCondition(i);
                if (!Literal.hasEffectiveBooleanValue(condition, false)) {
                    conditions.add(condition);
                    actions.add(getAction(i));
                }
                if (Literal.hasEffectiveBooleanValue(condition, true)) {
                    break;
                }
            }
            if (conditions.isEmpty()) {
                Literal lit = Literal.makeEmptySequence();
                ExpressionTool.copyLocationInfo(this, lit);
                return lit;
            } else if (conditions.size() == 1 && Literal.hasEffectiveBooleanValue(conditions.get(0), true)) {
                return actions.get(0);
            } else if (conditions.size() != count) {
                Expression[] c = conditions.toArray(new Expression[0]);
                Expression[] a = actions.toArray(new Expression[0]);
                Choose result = new Choose(c, a);
                result.setRetainedStaticContext(getRetainedStaticContext());
                return result;
            }
        }

        // See if only condition left is: if (true) then x else ()

        if (size() == 1 && Literal.hasEffectiveBooleanValue(getCondition(0), true)) {
            return getAction(0);
        }

        // Eliminate a redundant <xsl:otherwise/> or "when (test) then ()"

        if (Literal.isEmptySequence(getAction(size() - 1))) {
            if (size() == 1) {
                Literal lit = Literal.makeEmptySequence();
                ExpressionTool.copyLocationInfo(this, lit);
                return lit;
            } else {
                Expression[] conditions = new Expression[count-1];
                Expression[] actions = new Expression[count-1];
                for (int i = 0; i < count-1; i++) {
                    conditions[i] = getCondition(i);
                    actions[i] = getAction(i);
                }
                return new Choose(conditions, actions);
            }
        }

        // Flatten an "else if"

        if (Literal.hasEffectiveBooleanValue(getCondition(count - 1), true) &&
                getAction(count - 1) instanceof Choose) {
            Choose choose2 = (Choose) getAction(count - 1);
            int newLen = count + choose2.size() - 1;
            Expression[] c2 = new Expression[newLen];
            Expression[] a2 = new Expression[newLen];
            for (int i=0; i<count-1; i++) {
                c2[i] = getCondition(i);
                a2[i] = getAction(i);
            }
            for (int i=0; i<choose2.size(); i++) {
                c2[i + count - 1] = choose2.getCondition(i);
                a2[i + count - 1] = choose2.getAction(i);
            }
            return new Choose(c2, a2);
        }

        // Rewrite "if (EXP) then true() else false()" as boolean(EXP)

        if (count == 2 &&
                Literal.isConstantBoolean(getAction(0), true) &&
                Literal.isConstantBoolean(getAction(1), false) &&
                Literal.hasEffectiveBooleanValue(getCondition(1), true)) {
            TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
            if (th.isSubType(getCondition(0).getItemType(), BuiltInAtomicType.BOOLEAN) &&
                    getCondition(0).getCardinality() == StaticProperty.EXACTLY_ONE) {
                return getCondition(0);
            } else {
                return SystemFunction.makeCall("boolean", getRetainedStaticContext(), getCondition(0));
            }
        }
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        for (int i = 0; i < size(); i++) {
            conditionOps[i].typeCheck(visitor, contextInfo);
            TypeChecker.ebvTypeCheck(getCondition(i), visitor);
        }
        // Check that each of the action branches satisfies the expected type. This is a stronger check than checking the
        // type of the top-level expression. It's important with tail recursion not to wrap a tail call in a type checking
        // expression just because a dynamic type check is needed on a different branch of the choice.
        for (int i = 0; i < size(); i++) {
            if (Literal.hasEffectiveBooleanValue(getCondition(i), false)) {
                // Don't do any checking if we know statically the condition will be false, because it could
                // result in spurious warnings: bug 4537
                continue;
            }
            try {
                actionOps[i].typeCheck(visitor, contextInfo);
            } catch (XPathException err) {
                XPathException e2 = err.maybeWithLocation(getLocation())
                        .maybeWithFailingExpression(getAction(i));
                // mustn't throw the error unless the branch is actually selected, unless its a static or type error
                if (e2.isStaticError()) {
                    throw e2;
                } else if (e2.isTypeError()) {
                    // if this is an "empty" else branch, don't be draconian about the error handling. It might be
                    // the user knows the otherwise branch isn't needed because one of the when branches will always
                    // be satisfied.
                    // Also, don't throw a type error if the branch will never be executed; this can happen with
                    // a typeswitch where the purpose of the condition is to test the type.
                    if (Literal.isEmptySequence(getAction(i)) || Literal.hasEffectiveBooleanValue(getCondition(i), false)) {
                        setAction(i, new ErrorExpression(new XmlProcessingException(e2)));
                    } else {
                        throw e2;
                    }
                } else {
                    setAction(i, new ErrorExpression(new XmlProcessingException(e2)));
                }
            }
            if (Literal.hasEffectiveBooleanValue(getCondition(i), true)) {
                break;
            }
        }
        Optimizer opt = visitor.obtainOptimizer();
        if (opt.isOptionSet(OptimizerOptions.CONSTANT_FOLDING)) {
            Expression reduced = removeRedundantBranches(visitor);
            if (reduced != this) {
                return reduced.typeCheck(visitor, contextInfo);
            }
            return reduced;
        }
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
        return true;
    }

    /**
     * Static type checking for conditional expressions is delegated to the expression itself,
     * and is performed separately on each branch of the conditional, so that dynamic checks are
     * added only on those branches where the check is actually required. This also results in a static
     * type error if any branch is incapable of delivering a value of the required type. One reason
     * for this approach is to avoid doing dynamic type checking on a recursive function call as this
     * prevents tail-call optimization being used.
     *
     *
     * @param req                 the required type
     * @param backwardsCompatible true if backwards compatibility mode applies
     * @param roleSupplier                the role of the expression in relation to the required type
     * @param visitor             an expression visitor
     * @return the expression after type checking (perhaps augmented with dynamic type checking code)
     * @throws XPathException if failures occur, for example if the static type of one branch of the conditional
     *                        is incompatible with the required type
     */

    @Override
    public Expression staticTypeCheck(SequenceType req,
                                      boolean backwardsCompatible,
                                      Supplier<RoleDiagnostic> roleSupplier, ExpressionVisitor visitor)
            throws XPathException {
        int count = size();
        TypeChecker tc = getConfiguration().getTypeChecker(backwardsCompatible);
        for (int i = 0; i < count; i++) {
            try {
                setAction(i, tc.staticTypeCheck(getAction(i), req, roleSupplier, visitor));
            } catch (XPathException err) {
                if (err.isStaticError()) {
                    throw err;
                }
                ErrorExpression ee = new ErrorExpression(new XmlProcessingException(err));
                ExpressionTool.copyLocationInfo(getAction(i), ee);
                setAction(i, ee);
            }
        }
        // If the last condition isn't true(), then we need to consider the fall-through case, which returns
        // an empty sequence
        if (!Literal.hasEffectiveBooleanValue(getCondition(count - 1), true) &&
                !Cardinality.allowsZero(req.getCardinality())) {
            Expression[] c = new Expression[count + 1];
            Expression[] a = new Expression[count + 1];
            for (int i=0; i<count; i++) {
                c[i] = getCondition(i);
                a[i] = getAction(i);
            }
            c[count] = Literal.makeLiteral(BooleanValue.TRUE, this);
            String cond = count == 1 ? "The condition is not" : "None of the conditions is";
            RoleDiagnostic role = roleSupplier.get();
            String message =
                    "Conditional expression: " + cond + " satisfied, so an empty sequence is returned, " +
                            "but this is not allowed as the " + role.getMessage();
            ErrorExpression errExp = new ErrorExpression(message, role.getErrorCode(), true);
            ExpressionTool.copyLocationInfo(this, errExp);
            a[count] = errExp;
            return new Choose(c, a);
        }
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        int count = size();
        for (int i = 0; i < count; i++) {
            conditionOps[i].optimize(visitor, contextItemType);
            Expression ebv = BooleanFn.rewriteEffectiveBooleanValue(getCondition(i), visitor, contextItemType);
            if (ebv != null && ebv != getCondition(i)) {
                setCondition(i, ebv);
            }
            if (getCondition(i) instanceof Literal &&
                    !(((Literal) getCondition(i)).getGroundedValue() instanceof BooleanValue)) {
                final boolean b;
                try {
                    b = ((Literal) getCondition(i)).getGroundedValue().effectiveBooleanValue();
                } catch (XPathException err) {
                    throw err.withLocation(getLocation());
                }
                setCondition(i, Literal.makeLiteral(BooleanValue.get(b), this));
            }
        }
        for (int i = 0; i < count; i++) {
            if (Literal.hasEffectiveBooleanValue(getCondition(i), false)) {
                // Don't bother with optimisation if the code won't be executed: bug 4537
                continue;
            }
            try {
                actionOps[i].optimize(visitor, contextItemType);
            } catch (XPathException err) {
                // mustn't throw the error unless the branch is actually selected, unless its a type error
                if (err.isTypeError() && !visitor.isInliningFunctions()) {
                    throw err;
                } else {
                    ErrorExpression ee = new ErrorExpression(new XmlProcessingException(err));
                    ExpressionTool.copyLocationInfo(actionOps[i].getChildExpression(), ee);
                    setAction(i, ee);
                }
            }
            if (getAction(i) instanceof ErrorExpression &&
                    ((ErrorExpression)getAction(i)).isTypeError() &&
                    !Literal.isConstantBoolean(getCondition(i), false) &&
                    !Literal.isConstantBoolean(getCondition(i), true)) {
                // Bug 3933: avoid the warning for an implicit xsl:otherwise branch (constant condition = true)
                visitor.issueWarning("Branch " + (i + 1) + " of conditional will fail with a type error if executed. "
                        + ((ErrorExpression) getAction(i)).getMessage(), SaxonErrorCode.SXWN9027, getAction(i).getLocation());
            }
            if (Literal.hasEffectiveBooleanValue(getCondition(i), true)) {
                // Don't bother with optimisation of subsequent branches if the code won't be executed: bug 4537
                break;
            }
        }

        if (count == 0) {
            return Literal.makeEmptySequence();
        }
        Optimizer opt = visitor.obtainOptimizer();
        if (opt.isOptionSet(OptimizerOptions.CONSTANT_FOLDING)) {
            Expression e = removeRedundantBranches(visitor);
            if (e instanceof Choose) {
                return visitor.obtainOptimizer().trySwitch((Choose) e, visitor);
            } else {
                return e;
            }
        }
        return this;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings the rebinding map
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        int count = size();
        Expression[] c2 = new Expression[count];
        Expression[] a2 = new Expression[count];
        for (int c = 0; c < count; c++) {
            c2[c] = getCondition(c).copy(rebindings);
            a2[c] = getAction(c).copy(rebindings);
        }
        Choose ch2 = new Choose(c2, a2);
        ExpressionTool.copyLocationInfo(this, ch2);
        ch2.setInstruction(isInstruction());
        return ch2;
    }


    /**
     * Check to ensure that this expression does not contain any updating subexpressions.
     * This check is overridden for those expressions that permit updating subexpressions.
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if the expression has a non-permitted updateing subexpression
     */

    @Override
    public void checkForUpdatingSubexpressions() throws XPathException {
        for (Operand o : conditions()) {
            Expression condition = o.getChildExpression();
            condition.checkForUpdatingSubexpressions();
            if (condition.isUpdatingExpression()) {
                XPathException err = new XPathException(
                        "Updating expression appears in a context where it is not permitted", "XUST0001");
                err.setLocator(condition.getLocation());
                throw err;
            }
        }
        boolean updating = false;
        boolean nonUpdating = false;
        for (Operand o : actions()) {
            Expression act = o.getChildExpression();
            act.checkForUpdatingSubexpressions();
            if (ExpressionTool.isNotAllowedInUpdatingContext(act)) {
                if (updating) {
                    XPathException err = new XPathException(
                            "If any branch of a conditional is an updating expression, then all must be updating expressions (or vacuous)",
                            "XUST0001");
                    err.setLocator(act.getLocation());
                    throw err;
                }
                nonUpdating = true;
            }
            if (act.isUpdatingExpression()) {
                if (nonUpdating) {
                    XPathException err = new XPathException(
                            "If any branch of a conditional is an updating expression, then all must be updating expressions (or vacuous)",
                            "XUST0001");
                    err.setLocator(act.getLocation());
                    throw err;
                }
                updating = true;
            }
        }
    }

    /**
     * Determine whether this is an updating expression as defined in the XQuery update specification
     *
     * @return true if this is an updating expression
     */

    @Override
    public boolean isUpdatingExpression() {
        for (Operand o : actions()) {
            if (o.getChildExpression().isUpdatingExpression()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determine whether this is a vacuous expression as defined in the XQuery update specification
     *
     * @return true if this expression is vacuous
     */

    @Override
    public boolean isVacuousExpression() {
        // The Choose is vacuous if all branches are vacuous
        for (Operand action : actions()) {
            if (!action.getChildExpression().isVacuousExpression()) {
                return false;
            }
        }
        return true;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is prefered. For instructions this is the process() method.
     */

    @Override
    public int getImplementationMethod() {
        int m = Expression.PROCESS_METHOD | Expression.ITERATE_METHOD | Expression.WATCH_METHOD;
        if (!Cardinality.allowsMany(getCardinality())) {
            m |= Expression.EVALUATE_METHOD;
        }
        return m;
    }

    /**
     * Mark tail-recursive calls on functions. For most expressions, this does nothing.
     *
     * @return 0 if no tail call was found; 1 if a tail call on a different function was found;
     *         2 if a tail recursive call was found and if this call accounts for the whole of the value.
     */

    @Override
    public int markTailFunctionCalls(StructuredQName qName, int arity) {
        int result = UserFunctionCall.NOT_TAIL_CALL;
        for (Operand action : actions()) {
            result = Math.max(result, action.getChildExpression().markTailFunctionCalls(qName, arity));
        }
        return result;
    }

    /**
     * Get the item type of the items returned by evaluating this instruction
     *
     * @return the static item type of the instruction
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        TypeHierarchy th = getConfiguration().getTypeHierarchy();
        ItemType type = getAction(0).getItemType();
        for (int i = 1; i < size(); i++) {
            type = Type.getCommonSuperType(type, getAction(i).getItemType(), th);
        }
        return type;
    }

    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     * @param contextItemType the static type of the context item
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        if (isInstruction()) {
            return super.getStaticUType(contextItemType);
        } else {
            UType type = getAction(0).getStaticUType(contextItemType);
            for (int i = 1; i < size(); i++) {
                type = type.union(getAction(i).getStaticUType(contextItemType));
            }
            return type;
        }
    }

    /**
     * Compute the cardinality of the sequence returned by evaluating this instruction
     *
     * @return the static cardinality
     */

    @Override
    protected int computeCardinality() {
        int card = 0;
        boolean includesTrue = false;
        for (int i = 0; i < size(); i++) {
            card = Cardinality.union(card, getAction(i).getCardinality());
            if (Literal.hasEffectiveBooleanValue(getCondition(i), true)) {
                includesTrue = true;
            }
        }
        if (!includesTrue) {
            // we may drop off the end and return an empty sequence (typical for xsl:if)
            card = Cardinality.union(card, StaticProperty.ALLOWS_ZERO);
        }
        return card;
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     *
     * @return a set of flags indicating static properties of this expression
     */

    @Override
    protected int computeSpecialProperties() {
        // The special properties of a conditional are those which are common to every branch of the conditional
        int props = getAction(0).getSpecialProperties();
        for (int i = 1; i < size(); i++) {
            props &= getAction(i).getSpecialProperties();
        }
        return props;
    }

    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns true if any of the "actions" creates new nodes.
     * (Nodes created by the conditions can't contribute to the result).
     */

    @Override
    public final boolean mayCreateNewNodes() {
        for (Operand action : actions()) {
            int props = action.getChildExpression().getSpecialProperties();
            if ((props & StaticProperty.NO_NODES_NEWLY_CREATED) == 0) {
                return true;
            }
        }
        return false;
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
        for (int i = 0; i < size(); i++) {
            setAction(i, getAction(i).unordered(retainAllNodes, forStreaming));
        }
        return this;
    }

    /**
     * Check that any elements and attributes constructed or returned by this expression are acceptable
     * in the content model of a given complex type. It's always OK to say yes, since the check will be
     * repeated at run-time. The process of checking element and attribute constructors against the content
     * model of a complex type also registers the type of content expected of those constructors, so the
     * static validation can continue recursively.
     */

    @Override
    public void checkPermittedContents(SchemaType parentType, boolean whole) throws XPathException {
        for (Operand action : actions()) {
            action.getChildExpression().checkPermittedContents(parentType, whole);
        }
    }


    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     *
     * @return a representation of the expression as a string
     */

    public String toString() {
        StringBuilder sb = new StringBuilder(64);
        sb.append("if (");
        for (int i = 0; i < size(); i++) {
            sb.append(getCondition(i).toString());
            sb.append(") then (");
            sb.append(getAction(i).toString());
            if (i == size() - 1) {
                sb.append(")");
            } else {
                sb.append(") else if (");
            }
        }
        return sb.toString();
    }

    @Override
    public String toShortString() {
        return "if(" + getCondition(0).toShortString() + ") then ... else ...";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("choose", this);
        for (int i = 0; i < size(); i++) {
            getCondition(i).export(out);
            getAction(i).export(out);
        }
        out.endElement();
    }

    /**
     * Evaluate an expression as a single item. This always returns either a single Item or
     * null (denoting the empty sequence). No conversion is done. This method should not be
     * used unless the static type of the expression is a subtype of "item" or "item?": that is,
     * it should not be called if the expression may return a sequence. There is no guarantee that
     * this condition will be detected.
     *
     * @param context The context in which the expression is to be evaluated
     * @return the node or atomic value that results from evaluating the
     *         expression; or null to indicate that the result is an empty
     *         sequence
     * @throws XPathException if any dynamic error occurs evaluating the
     *                        expression
     */

    @Override
    public Item evaluateItem(XPathContext context) throws XPathException {
        return makeElaborator().elaborateForItem().eval(context);
    }

    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions. This default implementation relies on the process() method: it
     * "pushes" the results of the instruction to a sequence in memory, and then
     * iterates over this in-memory sequence.
     * <p>In principle instructions should implement a pipelined iterate() method that
     * avoids the overhead of intermediate storage.</p>
     *
     * @param context supplies the context for evaluation
     * @return a SequenceIterator that can be used to iterate over the result
     *         of the expression
     * @throws XPathException if any dynamic error occurs evaluating the
     *                        expression
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        return makeElaborator().elaborateForPull().iterate(context);
    }


    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in explain() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "choose";
    }

    @Override
    public String getStreamerName() {
        return "Choose";
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new ChooseExprElaborator();
    }

    public BooleanEvaluator[] getConditionEvaluators() {
        return ((ChooseExprElaborator)makeElaborator()).makeConditionEvaluators(this);
    }
    /**
     * Elaborator for a "Choose" expression (which may be xsl:if, xsl:choose, or an XPath conditional expression).
     *
     * <p>Provides "push" and "pull" implementations, as well as a singleton implementation.</p>
     */

    public static class ChooseExprElaborator extends PullElaborator {

        private BooleanEvaluator[] conditions;

        public BooleanEvaluator[] getConditionEvaluators() {
            return conditions;
        }

        public synchronized BooleanEvaluator[] makeConditionEvaluators(Choose expr) {
            if (conditions == null) {
                conditions = new BooleanEvaluator[expr.size()];
                for (int i = 0; i < expr.size(); i++) {
                    conditions[i] = expr.getCondition(i).makeElaborator().elaborateForBoolean();
                }
            }
            return conditions;
        }

        public SequenceEvaluator eagerly() {
            final Choose expr = (Choose) getExpression();
            final int count = expr.size();
            makeConditionEvaluators(expr);
            final SequenceEvaluator[] actions = new SequenceEvaluator[count];
            for (int i = 0; i < count; i++) {
                actions[i] = expr.getAction(i).makeElaborator().eagerly();
            }
            return new EagerChooseEvaluator(conditions, actions);
        }

        public PullEvaluator elaborateForPull() {
            final Choose expr = (Choose) getExpression();
            final int count = expr.size();
            final PullEvaluator[] actions = new PullEvaluator[count];
            makeConditionEvaluators(expr);
            for (int i = 0; i < count; i++) {
                actions[i] = expr.getAction(i).makeElaborator().elaborateForPull();
            }
            return switch (count) {
                case 1 -> context -> {
                    if (conditions[0].eval(context)) return actions[0].iterate(context);
                    return EmptyIterator.INSTANCE;
                };
                case 2 -> context -> {
                    if (conditions[0].eval(context)) return actions[0].iterate(context);
                    if (conditions[1].eval(context)) return actions[1].iterate(context);
                    return EmptyIterator.INSTANCE;
                };
                case 3 -> context -> {
                    if (conditions[0].eval(context)) return actions[0].iterate(context);
                    if (conditions[1].eval(context)) return actions[1].iterate(context);
                    if (conditions[2].eval(context)) return actions[2].iterate(context);
                    return EmptyIterator.INSTANCE;
                };
                case 4 -> context -> {
                    if (conditions[0].eval(context)) return actions[0].iterate(context);
                    if (conditions[1].eval(context)) return actions[1].iterate(context);
                    if (conditions[2].eval(context)) return actions[2].iterate(context);
                    if (conditions[3].eval(context)) return actions[3].iterate(context);
                    return EmptyIterator.INSTANCE;
                };
                default -> context -> {
                    for (int i = 0; i < count; i++) {
                        if (conditions[i].eval(context)) {
                            return actions[i].iterate(context);
                        }
                    }
                    return EmptyIterator.INSTANCE;
                };
            };
        }

        @Override
        public ItemEvaluator elaborateForItem() {
            final Choose expr = (Choose) getExpression();
            final int count = expr.size();
            final ItemEvaluator[] actions = new ItemEvaluator[count];
            makeConditionEvaluators(expr);
            for (int i = 0; i < count; i++) {
                actions[i] = expr.getAction(i).makeElaborator().elaborateForItem();
            }
            return switch (count) {
                case 1 -> context -> {
                    if (conditions[0].eval(context)) return actions[0].eval(context);
                    return null;
                };
                case 2 -> context -> {
                    if (conditions[0].eval(context)) return actions[0].eval(context);
                    if (conditions[1].eval(context)) return actions[1].eval(context);
                    return null;
                };
                case 3 -> context -> {
                    if (conditions[0].eval(context)) return actions[0].eval(context);
                    if (conditions[1].eval(context)) return actions[1].eval(context);
                    if (conditions[2].eval(context)) return actions[2].eval(context);
                    return null;
                };
                
                case 4 -> context -> {
                    if (conditions[0].eval(context)) return actions[0].eval(context);
                    if (conditions[1].eval(context)) return actions[1].eval(context);
                    if (conditions[2].eval(context)) return actions[2].eval(context);
                    if (conditions[3].eval(context)) return actions[3].eval(context);
                    return null;
                };
                default -> context -> {
                    for (int i = 0; i < count; i++) {
                        if (conditions[i].eval(context)) {
                            return actions[i].eval(context);
                        }
                    }
                    return null;
                };
            };

        }

        @Override
        public PushEvaluator elaborateForPush() {
            final Choose expr = (Choose) getExpression();
            final int count = expr.size();
            makeConditionEvaluators(expr);
            final PushEvaluator[] actions = new PushEvaluator[count];
            for (int i = 0; i < count; i++) {
                actions[i] = expr.getAction(i).makeElaborator().elaborateForPush();
            }
            switch (count) {
                case 1 -> {
                    final BooleanEvaluator cond0 = conditions[0];
                    final PushEvaluator act0 = actions[0];
                    return (output, context) -> {
                        if (cond0.eval(context)) return act0.processLeavingTail(output, context);
                        return null;
                    };
                }
                case 2 -> {
                    final BooleanEvaluator cond0 = conditions[0];
                    final BooleanEvaluator cond1 = conditions[1];
                    final PushEvaluator act0 = actions[0];
                    final PushEvaluator act1 = actions[1];
                    return (output, context) -> {
                        if (cond0.eval(context)) return act0.processLeavingTail(output, context);
                        if (cond1.eval(context)) return act1.processLeavingTail(output, context);
                        return null;
                    };
                }
                case 3 -> {
                    final BooleanEvaluator cond0 = conditions[0];
                    final BooleanEvaluator cond1 = conditions[1];
                    final BooleanEvaluator cond2 = conditions[2];
                    final PushEvaluator act0 = actions[0];
                    final PushEvaluator act1 = actions[1];
                    final PushEvaluator act2 = actions[2];
                    return (output, context) -> {
                        if (cond0.eval(context)) return act0.processLeavingTail(output, context);
                        if (cond1.eval(context)) return act1.processLeavingTail(output, context);
                        if (cond2.eval(context)) return act2.processLeavingTail(output, context);
                        return null;
                    };
                }
                case 4 -> {
                    final BooleanEvaluator cond0 = conditions[0];
                    final BooleanEvaluator cond1 = conditions[1];
                    final BooleanEvaluator cond2 = conditions[2];
                    final BooleanEvaluator cond3 = conditions[3];
                    final PushEvaluator act0 = actions[0];
                    final PushEvaluator act1 = actions[1];
                    final PushEvaluator act2 = actions[2];
                    final PushEvaluator act3 = actions[3];
                    return (output, context) -> {
                        if (cond0.eval(context)) return act0.processLeavingTail(output, context);
                        if (cond1.eval(context)) return act1.processLeavingTail(output, context);
                        if (cond2.eval(context)) return act2.processLeavingTail(output, context);
                        if (cond3.eval(context)) return act3.processLeavingTail(output, context);
                        return null;
                    };
                }
                default -> {
                    return (output, context) -> {
                        for (int i = 0; i < count; i++) {
                            if (conditions[i].eval(context)) {
                                return actions[i].processLeavingTail(output, context);
                            }
                        }
                        return null;
                    };
                }
            }
        }

        @Override
        public UpdateEvaluator elaborateForUpdate() {
            final Choose expr = (Choose) getExpression();
            final int count = expr.size();
            makeConditionEvaluators(expr);
            final UpdateEvaluator[] actions = new UpdateEvaluator[count];
            for (int i = 0; i < count; i++) {
                actions[i] = expr.getAction(i).makeElaborator().elaborateForUpdate();
            }
            return (context, updates) -> {
                for (int i = 0; i < count; i++) {
                    if (conditions[i].eval(context)) {
                        actions[i].registerUpdates(context, updates);
                        break;
                    }
                }
            };

        }
    }

    private static class EagerChooseEvaluator implements SequenceEvaluator {

        private final BooleanEvaluator[] conditions;
        private final SequenceEvaluator[] actions;
        private final int count;

        public EagerChooseEvaluator(BooleanEvaluator[] conditions, SequenceEvaluator[] actions) {
            this.conditions = conditions;
            this.actions = actions;
            this.count = conditions.length;
        }

        /**
         * Evaluate a construct to produce a value (which might be a lazily evaluated Sequence)
         *
         * @param context the evaluation context
         * @return a Sequence (not necessarily grounded)
         * @throws XPathException if a dynamic error occurs during the evaluation.
         */
        @Override
        public Sequence evaluate(XPathContext context) throws XPathException {
            for (int i = 0; i < count; i++) {
                if (conditions[i].eval(context)) {
                    return actions[i].evaluate(context);
                }
            }
            return EmptySequence.INSTANCE;
        }

    }
}

