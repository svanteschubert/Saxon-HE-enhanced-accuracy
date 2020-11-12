////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;

/**
 * Assignation is an abstract superclass for the kinds of expression
 * that declare range variables: for, some, and every.
 */

public abstract class Assignation extends Expression implements LocalBinding {

    private Operand sequenceOp;
    private Operand actionOp;
    protected int slotNumber = -999;     // slot number for range variable
    // (initialized to ensure a crash if no real slot is allocated)
    protected StructuredQName variableName;
    protected SequenceType requiredType;
    protected boolean isIndexedVariable = false;
    protected boolean hasLoopingReference = false;
    protected List<VariableReference> references = null;

    private final static OperandRole REPEATED_ACTION_ROLE = new OperandRole(OperandRole.HIGHER_ORDER, OperandUsage.TRANSMISSION);

    public Assignation() {
        sequenceOp = new Operand(this, null, OperandRole.NAVIGATE);
        actionOp = new Operand(this, null, this instanceof LetExpression ? OperandRole.SAME_FOCUS_ACTION : REPEATED_ACTION_ROLE);
    }

    public Operand getSequenceOp() {
        return sequenceOp;
    }

    public Operand getActionOp() {
        return actionOp;
    }

    @Override
    public Iterable<Operand> operands() {
        return operandList(sequenceOp, actionOp);
    }


    /**
     * Set the required type (declared type) of the variable
     *
     * @param requiredType the required type
     */
    public void setRequiredType(SequenceType requiredType) {
        this.requiredType = requiredType;
    }

    /**
     * Set the name of the variable
     *
     * @param variableName the name of the variable
     */

    public void setVariableQName(StructuredQName variableName) {
        this.variableName = variableName;
    }


    /**
     * Get the name of the variable
     *
     * @return the variable name, as a QName
     */

    @Override
    public StructuredQName getVariableQName() {
        return variableName;
    }

    @Override
    public StructuredQName getObjectName() {
        return variableName;
    }


    /**
     * Get the declared type of the variable
     *
     * @return the declared type
     */

    @Override
    public SequenceType getRequiredType() {
        return requiredType;
    }

    /**
     * If the variable is bound to an integer, get the minimum and maximum possible values.
     * Return null if unknown or not applicable
     */
    @Override
    public IntegerValue[] getIntegerBoundsForVariable() {
        return getSequence().getIntegerBounds();
    }

    /**
     * If this is a local variable held on the local stack frame, return the corresponding slot number.
     * In other cases, return -1.
     */

    @Override
    public int getLocalSlotNumber() {
        return slotNumber;
    }

    /**
     * Compute the dependencies of an expression, as the union of the
     * dependencies of its sub-expressions. (This is overridden for path expressions
     * and filter expressions, where the dependencies of a sub-expression are not all
     * propagated). This method should be called only once, to compute the dependencies;
     * after that, getDependencies should be used.
     *
     * @return the dependencies, as a bit-mask
     */
    @Override
    public int computeDependencies() {
        int d = super.computeDependencies();
        // Unset the DEPENDS_ON_LOCAL_VARIABLES bit if the only dependencies are to
        // variables declared within the expression itself (typically, the variable
        // bound by this Assignation)
        if (!ExpressionTool.containsLocalVariableReference(this)) {
            d &= ~StaticProperty.DEPENDS_ON_LOCAL_VARIABLES;
        }
        return d;
    }

    /**
     * Get the value of the range variable
     */

    @Override
    public Sequence evaluateVariable(XPathContext context) throws XPathException {
        Sequence actual = context.evaluateLocalVariable(slotNumber);
        if (!(actual instanceof GroundedValue || actual instanceof NodeInfo)) {
            actual = actual.materialize();
            context.setLocalVariable(slotNumber, actual);
        }
        return actual;
    }

    /**
     * Add the "return" or "satisfies" expression, and fix up all references to the
     * range variable that occur within that expression
     *
     * @param action the expression that occurs after the "return" keyword of a "for"
     *               expression, the "satisfies" keyword of "some/every", or the ":=" operator of
     *               a "let" expression.
     */

    public void setAction(Expression action) {
        actionOp.setChildExpression(action);
    }

    /**
     * Indicate whether the binding is local or global. A global binding is one that has a fixed
     * value for the life of a query or transformation; any other binding is local.
     */

    @Override
    public final boolean isGlobal() {
        return false;
    }

    /**
     * Test whether it is permitted to assign to the variable using the saxon:assign
     * extension element. This will only be for an XSLT global variable where the extra
     * attribute saxon:assignable="yes" is present.
     */

    @Override
    public final boolean isAssignable() {
        return false;
    }

    /**
     * Check to ensure that this expression does not contain any inappropriate updating subexpressions.
     * This check is overridden for those expressions that permit updating subexpressions.
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if the expression has a non-permitted updateing subexpression
     */

    @Override
    public void checkForUpdatingSubexpressions() throws XPathException {
        getSequence().checkForUpdatingSubexpressions();
        if (getSequence().isUpdatingExpression()) {
            XPathException err = new XPathException(
                    "An updating expression cannot be used to initialize a variable", "XUST0001");
            err.setLocator(getSequence().getLocation());
            throw err;
        }
        getAction().checkForUpdatingSubexpressions();
    }

    /**
     * Determine whether this is an updating expression as defined in the XQuery update specification
     *
     * @return true if this is an updating expression
     */

    @Override
    public boolean isUpdatingExpression() {
        return getAction().isUpdatingExpression();
    }

    /**
     * Get the action expression
     *
     * @return the action expression (introduced by "return" or "satisfies")
     */

    public Expression getAction() {
        return actionOp.getChildExpression();
    }

    /**
     * Set the "sequence" expression - the one to which the variable is bound
     *
     * @param sequence the expression to which the variable is bound
     */

    public void setSequence(Expression sequence) {
        sequenceOp.setChildExpression(sequence);
    }

    /**
     * Get the "sequence" expression - the one to which the variable is bound
     *
     * @return the expression to which the variable is bound
     */

    public Expression getSequence() {
        return sequenceOp.getChildExpression();
    }

    /**
     * Set the slot number for the range variable
     *
     * @param nr the slot number to be used
     */

    public void setSlotNumber(int nr) {
        slotNumber = nr;
    }

    /**
     * Get the number of slots required. Normally 1, except for a FOR expression with an AT clause, where it is 2.
     *
     * @return the number of slots required
     */

    public int getRequiredSlots() {
        return 1;
    }

    @Override
    public boolean hasVariableBinding(Binding binding) {
        return this == binding;
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
        setAction(getAction().unordered(retainAllNodes, forStreaming));
        return this;
    }

    /**
     * Return the estimated cost of evaluating an expression. This is a very crude measure based
     * on the syntactic form of the expression (we have no knowledge of data values). We take
     * the cost of evaluating a simple scalar comparison or arithmetic expression as 1 (one),
     * and we assume that a sequence has length 5. The resulting estimates may be used, for
     * example, to reorder the predicates in a filter expression so cheaper predicates are
     * evaluated first.
     * @return the estimated cost
     */
    @Override
    public double getCost() {
        return getSequence().getCost() + 5 * getAction().getCost();
    }

    /**
     * Suppress validation on contained element constructors, on the grounds that the parent element
     * is already performing validation. The default implementation does nothing.
     */

    @Override
    public void suppressValidation(int validationMode) {
        getAction().suppressValidation(validationMode);
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
     *         expression, and that represent possible results of this expression. For an expression that does
     *         navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     *         expressions, it is the same as the input pathMapNode.
     */

    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        PathMap.PathMapNodeSet varPath = getSequence().addToPathMap(pathMap, pathMapNodeSet);
        pathMap.registerPathForVariable(this, varPath);
        return getAction().addToPathMap(pathMap, pathMapNodeSet);
    }

    /**
     * Get the display name of the range variable, for diagnostics only
     *
     * @return the lexical QName of the range variable. For system allocated
     *         variables, the conventional namespace prefix "zz" is used.
     */

    public String getVariableName() {
        if (variableName == null) {
            return "zz:var" + computeHashCode();
        } else {
            return variableName.getDisplayName();
        }
    }

    /**
     * Get the name of the range variable as a Name or EQName.
     *
     * @return the name of the range variable. For system allocated
     *         variables, the namespace "http://ns.saxonica.com/anonymous-var"
     *         is used. For names in no namespace, the local name alone is used
     */

    public String getVariableEQName() {
        if (variableName == null) {
            return "Q{http://ns.saxonica.com/anonymous-var}var" + computeHashCode();
        } else if (variableName.hasURI("")) {
            return variableName.getLocalPart();
        } else {
            return variableName.getEQName();
        }
    }

    /**
     * Refine the type information associated with this variable declaration. This is useful when the
     * type of the variable has not been explicitly declared (which is common); the variable then takes
     * a static type based on the type of the expression to which it is bound. The effect of this call
     * is to update the static expression type for all references to this variable.
     * @param type              the inferred item type of the expression to which the variable is bound
     * @param cardinality       the inferred cardinality of the expression to which the variable is bound
     * @param constantValue     the constant value to which the variable is bound (null if there is no constant value)
     * @param properties        other static properties of the expression to which the variable is bound
     * @param currentExpression the expression that binds the variable
     * @throws XPathException if things go wrong
     */

    public void refineTypeInformation(final ItemType type,
                                      final int cardinality,
                                      final GroundedValue constantValue,
                                      final int properties,
                                      final Assignation currentExpression) throws XPathException {
        ExpressionTool.processExpressionTree(currentExpression.getAction(), null, (exp, result) -> {
            if (exp instanceof VariableReference && ((VariableReference)exp).getBinding() == currentExpression) {
                ((VariableReference) exp).refineVariableType(type, cardinality, constantValue, properties);
            }
            return false;
        });
    }

    /**
     * Register a variable reference that refers to the variable bound in this expression
     *
     * @param ref the variable reference
     * @param isLoopingReference - true if the reference occurs within a loop, such as the predicate
     *                           of a filter expression
     */

    @Override
    public void addReference(VariableReference ref, boolean isLoopingReference) {
        hasLoopingReference |= isLoopingReference;
        if (references == null) {
            references = new ArrayList<VariableReference>();
        }
        for (VariableReference vr : references) {
            if (vr == ref) {
                return;
            }
        }
        references.add(ref);
    }

    /**
     * Get the (nominal) count of the number of references to this variable
     *
     * @return zero if there are no references, one if there is a single reference that is not in
     *         a loop, some higher number if there are multiple references (or a single reference in a loop),
     *         or the special value @link RangeVariable#FILTERED} if there are any references
     *         in filter expressions that require searching.
     */

    public int getNominalReferenceCount() {
        if (isIndexedVariable) {
            return FilterExpression.FILTERED;
        } else if (references == null || hasLoopingReference) {
            return 10;
        } else {
            return references.size();
        }
    }

    /**
     * Remove dead references from the reference list of the variable; at the same time, check whether
     * any of the variable references is in a loop, and return true if so. References are considered dead
     * if they do not have this Binding as an ancestor in the expression tree; this typically occurs because
     * they are in a part of the tree that has been rewritten or removed.
     * @return true if any of the references in the reference list occurs within a looping construct.
     */

    protected boolean removeDeadReferences() {
        boolean inLoop = false;
        if (references != null) {
            for (int i = references.size() - 1; i >= 0; i--) {
                // Check whether the reference still has this Assignation as an ancestor in the expression tree
                boolean found = false;
                inLoop |= references.get(i).isInLoop();
                Expression parent = references.get(i).getParentExpression();
                while (parent != null) {
                    if (parent == this) {
                        found = true;
                        break;
                    } else {
                        parent = parent.getParentExpression();
                    }
                }
                if (!found) {
                    references.remove(i);
                }
            }
        }
        return inLoop;
    }

    /**
     * This method recomputes the reference list by scanning the subtree rooted at this variable binding.
     * If the scan proves expensive, or if more than two references are found, or if a looping reference is found,
     * then the scan is abandoned. On completion the reference list for the variable is either accurate, or
     * is null.
     */

    protected void verifyReferences() {
        rebuildReferenceList(false);
    }

    /**
     * Rebuild the reference list to guide subsequent optimization.
     * @param force if true, the search is exhaustive. If false, the search (and therefore the attempt to
     *              inline variables) is abandoned after a while to avoid excessive cost. This happens when
     *              a stylesheet contains very large templates or functions.
     */

    public void rebuildReferenceList(boolean force) {
        int[] results = new int[]{0, force ? Integer.MAX_VALUE : 500};
        List<VariableReference> references = new ArrayList<>();
        countReferences(this, getAction(), references, results);
        this.references = results[1] <= 0 ? null : references;
    }

    private static void countReferences(Binding binding, Expression exp, List<VariableReference> references, int[] results) {
        // results[0] = nominal reference count
        // results[1] = quota nodes visited
        if (exp instanceof LocalVariableReference) {
            LocalVariableReference ref = (LocalVariableReference) exp;
            if (ref.getBinding() == binding) {
                ref.recomputeInLoop();
                results[0] += ref.isInLoop() ? 10 : 1;
                references.add((LocalVariableReference) exp);
            }
        } else if ((exp.getDependencies() & StaticProperty.DEPENDS_ON_LOCAL_VARIABLES) != 0) {
            if (--results[1] <= 0) {
                // abandon the search
                results[0] = 100;
                results[1] = 0;
            } else {
                for (Operand o : exp.operands()) {
                    countReferences(binding, o.getChildExpression(), references, results);
                }
            }
        }
    }

    /**
     * Test whether the variable bound by this let expression should be indexable
     *
     * @return true if the variable should be indexable
     */

    @Override
    public boolean isIndexedVariable() {
        return isIndexedVariable;
    }

    /**
     * Replace all references to the variable bound by this let expression,
     * that occur within the action expression, with the given expression
     *
     *
     * @param seq the expression
     * @return true if the variable was successfully inlined. (Returns false, for example,
     * if a variable reference occurs inside a try/catch, which inhibits inlining).
     */

    public boolean replaceVariable(Expression seq) {
        boolean done = ExpressionTool.inlineVariableReferences(getAction(), this, seq);
        if (done && isIndexedVariable() && seq instanceof VariableReference) {
            Binding newBinding = ((VariableReference) seq).getBinding();
            if (newBinding instanceof Assignation) {
                ((Assignation) newBinding).setIndexedVariable();
            }
        }
        return done;
    }

    /**
     * Indicate that the variable bound by this let expression should be indexable
     * (because it is used in an appropriate filter expression)
     */

    @Override
    public void setIndexedVariable() {
        isIndexedVariable = true;
    }
}

