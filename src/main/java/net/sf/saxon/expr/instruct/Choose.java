////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.BooleanFn;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XmlProcessingException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.*;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Compiled representation of an xsl:choose or xsl:if element in the stylesheet.
 * Also used for typeswitch in XQuery.
 */

public class Choose extends Instruction implements ConditionalInstruction {

    private Operand[] conditionOps;
    private Operand[] actionOps;
    private boolean isInstruction;


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
        isInstruction = inst;
    }

    /**
     * Ask whether this expression is an instruction. In XSLT streamability analysis this
     * is used to distinguish constructs corresponding to XSLT instructions from other constructs.
     *
     * @return true if this construct originates as an XSLT instruction
     */

    @Override
    public boolean isInstruction() {
        return isInstruction;
    }


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

    public int getNumberOfConditions() {
        return size();
    }
    
    public Expression getCondition(int i) {
        return conditionOps[i].getChildExpression();
    }
    
    public void setCondition(int i, Expression condition) {
        conditionOps[i].setChildExpression(condition);
    }

    public Iterable<Operand> conditions() {
        return Arrays.asList(conditionOps);
    }

    /**
     * Get i'th action operand
     * @param i the action number
     * @return the i'th action to be evaluated when the corresponding condition is true
     */
    
    public Operand getActionOperand(int i) {
        return actionOps[i];
    }

    /**
     * Get i'th action to be performed
     *
     * @param i the action number
     * @return the i'th action to be evaluated when the corresponding condition is true
     */

    public Expression getAction(int i) {
        return actionOps[i].getChildExpression();
    }
    
    public void setAction(int i, Expression action) {
        actionOps[i].setChildExpression(action);
    }

    public Iterable<Operand> actions() {
        return Arrays.asList(actionOps);
    }

    @Override
    public Iterable<Operand> operands() {
        List<Operand> operanda = new ArrayList<Operand>(size()*2);
        for (int i=0; i<size(); i++) {
            operanda.add(conditionOps[i]);
            operanda.add(actionOps[i]);
        }
        return operanda;
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
        int size = size();
        boolean changed = false;
        if (compress) {
            List<Expression> conditions = new ArrayList<>(size);
            List<Expression> actions = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
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
            } else if (conditions.size() != size) {
                Expression[] c = conditions.toArray(new Expression[conditions.size()]);
                Expression[] a = actions.toArray(new Expression[actions.size()]);
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
                Expression[] conditions = new Expression[size-1];
                Expression[] actions = new Expression[size-1];
                for (int i = 0; i < size-1; i++) {
                    conditions[i] = getCondition(i);
                    actions[i] = getAction(i);
                }
                return new Choose(conditions, actions);
            }
        }

        // Flatten an "else if"

        if (Literal.hasEffectiveBooleanValue(getCondition(size - 1), true) &&
                getAction(size - 1) instanceof Choose) {
            Choose choose2 = (Choose) getAction(size - 1);
            int newLen = size + choose2.size() - 1;
            Expression[] c2 = new Expression[newLen];
            Expression[] a2 = new Expression[newLen];
            for (int i=0; i<size-1; i++) {
                c2[i] = getCondition(i);
                a2[i] = getAction(i);
            }
            for (int i=0; i<choose2.size(); i++) {
                c2[i + size - 1] = choose2.getCondition(i);
                a2[i + size - 1] = choose2.getAction(i);
            }
            return new Choose(c2, a2);
        }

        // Rewrite "if (EXP) then true() else false()" as boolean(EXP)

        if (size == 2 &&
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
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        for (int i = 0; i < size(); i++) {
            conditionOps[i].typeCheck(visitor, contextInfo);
            XPathException err = TypeChecker.ebvError(getCondition(i), th);
            if (err != null) {
                err.setLocator(getCondition(i).getLocation());
                err.maybeSetFailingExpression(getCondition(i));
                throw err;
            }
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
                err.maybeSetLocation(getLocation());
                err.maybeSetFailingExpression(getAction(i));
                // mustn't throw the error unless the branch is actually selected, unless its a static or type error
                if (err.isStaticError()) {
                    throw err;
                } else if (err.isTypeError()) {
                    // if this is an "empty" else branch, don't be draconian about the error handling. It might be
                    // the user knows the otherwise branch isn't needed because one of the when branches will always
                    // be satisfied.
                    // Also, don't throw a type error if the branch will never be executed; this can happen with
                    // a typeswitch where the purpose of the condition is to test the type.
                    if (Literal.isEmptySequence(getAction(i)) || Literal.hasEffectiveBooleanValue(getCondition(i), false)) {
                        setAction(i, new ErrorExpression(new XmlProcessingException(err)));
                    } else {
                        throw err;
                    }
                } else {
                    setAction(i, new ErrorExpression(new XmlProcessingException(err)));
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
        int size = size();
        TypeChecker tc = getConfiguration().getTypeChecker(backwardsCompatible);
        for (int i = 0; i < size; i++) {
            try {
                setAction(i, tc.staticTypeCheck(getAction(i), req, role, visitor));
            } catch (XPathException err) {
                if (err.isStaticError()) {
                    throw err;
                }
//                else if (err.isTypeError()) {
//                    visitor.issueWarning("Branch " + (i + 1) + " of conditional will fail with a type error if executed. "
//                        + err.getMessage(), getLocation());
//                }
                ErrorExpression ee = new ErrorExpression(new XmlProcessingException(err));
                ExpressionTool.copyLocationInfo(getAction(i), ee);
                setAction(i, ee);
            }
        }
        // If the last condition isn't true(), then we need to consider the fall-through case, which returns
        // an empty sequence
        if (!Literal.hasEffectiveBooleanValue(getCondition(size - 1), true) &&
                !Cardinality.allowsZero(req.getCardinality())) {
            Expression[] c = new Expression[size + 1];
            Expression[] a = new Expression[size + 1];
            for (int i=0; i<size; i++) {
                c[i] = getCondition(i);
                a[i] = getAction(i);
            }
            c[size] = Literal.makeLiteral(BooleanValue.TRUE, this);
            String cond = size == 1 ? "The condition is not" : "None of the conditions is";
            String message =
                    "Conditional expression: " + cond + " satisfied, so an empty sequence is returned, " +
                            "but this is not allowed as the " + role.getMessage();
            ErrorExpression errExp = new ErrorExpression(message, role.getErrorCode(), true);
            ExpressionTool.copyLocationInfo(this, errExp);
            a[size] = errExp;
            return new Choose(c, a);
        }
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        int size = size();
        for (int i = 0; i < size; i++) {
            conditionOps[i].optimize(visitor, contextItemType);
            Expression ebv = BooleanFn.rewriteEffectiveBooleanValue(getCondition(i), visitor, contextItemType);
            if (ebv != null && ebv != getCondition(i)) {
                setCondition(i, ebv);
            }
            if (getCondition(i) instanceof Literal &&
                    !(((Literal) getCondition(i)).getValue() instanceof BooleanValue)) {
                final boolean b;
                try {
                    b = ((Literal) getCondition(i)).getValue().effectiveBooleanValue();
                } catch (XPathException err) {
                    err.setLocation(getLocation());
                    throw err;
                }
                setCondition(i, Literal.makeLiteral(BooleanValue.get(b), this));
            }
        }
        for (int i = 0; i < size; i++) {
            if (Literal.hasEffectiveBooleanValue(getCondition(i), false)) {
                // Don't bother with optimisation if the code won't be executed: bug 4537
                continue;
            }
            try {
                actionOps[i].optimize(visitor, contextItemType);
            } catch (XPathException err) {
                // mustn't throw the error unless the branch is actually selected, unless its a type error
                if (err.isTypeError()) {
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
                        + ((ErrorExpression) getAction(i)).getMessage(), getAction(i).getLocation());
            }
            if (Literal.hasEffectiveBooleanValue(getCondition(i), true)) {
                // Don't bother with optimisation of subsequent branches if the code won't be executed: bug 4537
                break;
            }
        }

        if (size == 0) {
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
     * @param rebindings
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        int size = size();
        Expression[] c2 = new Expression[size];
        Expression[] a2 = new Expression[size];
        for (int c = 0; c < size; c++) {
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
     * @param contextItemType
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
    public int computeCardinality() {
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
    public int computeSpecialProperties() {
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
     * @param pathMapNodeSet the set of PathMap nodes to which the paths from this expression should be appended
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     *         expression is the first operand of a path expression or filter expression. For an expression that does
     *         navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     *         expressions, it is the same as the input pathMapNode.
     */

    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        // expressions used in a condition contribute paths, but these do not contribute to the result
        for (Operand condition : conditions()) {
            condition.getChildExpression().addToPathMap(pathMap, pathMapNodeSet);
        }
        PathMap.PathMapNodeSet result = new PathMap.PathMapNodeSet();
        for (Operand action : actions()) {
            PathMap.PathMapNodeSet temp = action.getChildExpression().addToPathMap(pathMap, pathMapNodeSet);
            result.addNodeSet(temp);
        }
        return result;
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     *
     * @return a representation of the expression as a string
     */

    public String toString() {
        FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C64);
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
     * Process this instruction, that is, choose an xsl:when or xsl:otherwise child
     * and process it.
     *
     *
     * @param output the destination for the result
     * @param context the dynamic context of this transformation
     * @return a TailCall, if the chosen branch ends with a call of call-template or
     *         apply-templates. It is the caller's responsibility to execute such a TailCall.
     *         If there is no TailCall, returns null.
     * @throws XPathException if any non-recoverable dynamic error occurs
     */

    @Override
    public TailCall processLeavingTail(Outputter output, XPathContext context) throws XPathException {
        int i = choose(context);
        if (i >= 0) {
            Expression action = getAction(i);
            if (action instanceof TailCallReturner) {
                return ((TailCallReturner) action).processLeavingTail(output, context);
            } else {
                action.process(output, context);
                return null;
            }
        }
        return null;
    }

    /**
     * Identify which of the choices to take
     * @param context the dynamic context
     * @return integer the index of the first choice that matches, zero-based; or -1 if none of the choices
     * matches
     * @throws XPathException if evaluating a condition fails
     */

    private int choose(XPathContext context) throws XPathException {
        int size = size();
        for (int i = 0; i < size; i++) {
            final boolean b;
            try {
                b = getCondition(i).effectiveBooleanValue(context);
            } catch (XPathException e) {
                e.maybeSetFailingExpression(getCondition(i));
                throw e;
            }
            if (b) {
                return i;
            }
        }
        return -1;
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
        int i = choose(context);
        return i < 0 ? null : getAction(i).evaluateItem(context);
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
        int i = choose(context);
        return i < 0 ? EmptyIterator.emptyIterator() : getAction(i).iterate(context);
    }


    /**
     * Evaluate an updating expression, adding the results to a Pending Update List.
     * The default implementation of this method, which is used for non-updating expressions,
     * throws an UnsupportedOperationException
     *
     * @param context the XPath dynamic evaluation context
     * @param pul     the pending update list to which the results should be written
     */

    @Override
    public void evaluatePendingUpdates(XPathContext context, PendingUpdateList pul) throws XPathException {
        int i = choose(context);
        if (i >= 0) {
            getAction(i).evaluatePendingUpdates(context, pul);
        }
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
}

