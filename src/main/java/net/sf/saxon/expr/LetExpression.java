////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.elab.*;
import net.sf.saxon.expr.instruct.DocumentInstr;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;


/**
 * A LetExpression represents the XQuery construct let $x := expr return expr. It is used
 * also for XSLT local variables.
 */

public class LetExpression extends Assignation {

    private SequenceEvaluator evaluator = null;
    private boolean needsEagerEvaluation = false;
    private boolean needsLazyEvaluation = false;
    private boolean _isInstruction;

    /**
     * Create a LetExpression
     */

    public LetExpression() {
        //System.err.println("let");
    }


    /**
     * Say whether this expression originates as an XSLT instruction
     *
     * @param inst true if this is an xsl:variable instruction
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
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */

    @Override
    public String getExpressionName() {
        return "let";
    }

    /**
     * Say that this variable must be evaluated eagerly. This is a requirement for example
     * for variables appearing within a try/catch, or for variables referenced from within
     * a construct that uses multi-threading, for example xsl:result-document or a multi-threaded
     * xsl:for-each.
     * @param req set to true if eager evaluation is required
     */

    public void setNeedsEagerEvaluation(boolean req) {
        if (req && needsLazyEvaluation) {
            // No action - see bug #2903, Lazy evaluation wins
        }
        this.needsEagerEvaluation = req;
    }

    public void setNeedsLazyEvaluation(boolean req) {
        if (req && needsEagerEvaluation) {
            this.needsEagerEvaluation = false; // Bug 2903: lazy evaluation wins
        }
        this.needsLazyEvaluation = req;
    }

    public boolean isNeedsLazyEvaluation() {
        return needsLazyEvaluation;
    }

    public boolean isNeedsEagerEvaluation() {
        return needsEagerEvaluation;
    }

    @Override
    public boolean supportsLazyEvaluation() {
        return !needsEagerEvaluation;
    }

    /**
     * Ask whether the expression can be lifted out of a loop, assuming it has no dependencies
     * on the controlling variable/focus of the loop
     * @param forStreaming true if we are handling streaming code (where rewrites need to be more cautious)
     */
    @Override
    public boolean isLiftable(boolean forStreaming) {
        return super.isLiftable(forStreaming) && !needsEagerEvaluation;
    }

    @Override
    public void resetLocalStaticProperties() {
        super.resetLocalStaticProperties();
        references = new ArrayList<>(); // bug 3233
    }

    /**
     * Type-check the expression. This also has the side-effect of counting the number of references
     * to the variable (treating references that occur within a loop specially)
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        // The order of events is critical here. First we ensure that the type of the
        // sequence expression is established. This is used to establish the type of the variable,
        // which in turn is required when type-checking the action part.

        getSequenceOp().typeCheck(visitor, contextInfo);

        Supplier<RoleDiagnostic> role =
                () -> new RoleDiagnostic(RoleDiagnostic.VARIABLE, getVariableQName().getDisplayName(), 0);
        if (visitor.getStaticContext().getXPathVersion() >= 40) {
            TypeChecker tc = visitor.getConfiguration().getTypeChecker(false);
            setSequence(tc.staticTypeCheck(getSequence(), requiredType, role, visitor));
        } else {
            setSequence(TypeChecker.strictTypeCheck(
                    getSequence(), requiredType, role, visitor.getStaticContext()));
        }
        final ItemType actualItemType = getSequence().getItemType();

        refineTypeInformation(actualItemType,
                              getSequence().getCardinality(),
                              getSequence() instanceof Literal ? ((Literal) getSequence()).getGroundedValue() : null,
                              getSequence().getSpecialProperties(), this);

        getActionOp().typeCheck(visitor, contextInfo);
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
     * Static type checking for let expressions is delegated to the expression itself,
     * and is performed on the "action" expression, to allow further delegation to the branches
     * of a conditional
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
        TypeChecker tc = visitor.getConfiguration().getTypeChecker(backwardsCompatible);
        setAction(tc.staticTypeCheck(getAction(), req, roleSupplier, visitor));
        return this;
    }

    /**
     * Perform optimisation of an expression and its subexpressions.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor         an expression visitor
     * @param contextItemType the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        Optimizer opt = visitor.obtainOptimizer();

        // if this is a construct of the form "let $j := EXP return $j" replace it with EXP
        // Remarkably, people do write this, and it can also be produced by previous rewrites
        // Note that type checks will already have been added to the sequence expression

        if (getAction() instanceof VariableReference &&
                ((VariableReference) getAction()).getBinding() == this &&
                !ExpressionTool.changesXsltContext(getSequence())) {
            getSequenceOp().optimize(visitor, contextItemType);
            opt.trace("Eliminated trivial variable " + getVariableName(), getSequence());
            return getSequence();
        }

        if (getSequence() instanceof Literal && opt.isOptionSet(OptimizerOptions.INLINE_VARIABLES)) {
            // inline the variable: replace all references by the constant value
            // This relies on the fact that optimizing the action part will cause any references to be inlined
            opt.trace("Inlined constant variable " + getVariableName(), getSequence());
            replaceVariable(getSequence());
            return getAction().optimize(visitor, contextItemType);
        }

        // if this is an XSLT construct of the form <xsl:variable>text</xsl:variable>, try to replace
        // it by <xsl:variable select="..."/>. This can be done if all the references to the variable use
        // its value as a string (rather than, say, as a node or as a boolean)
        if (getSequence() instanceof DocumentInstr && ((DocumentInstr) getSequence()).isTextOnly()) {
            // Ensure the list of references is accurate
            verifyReferences();
            // Check whether all uses of the variable are atomized or stringified
            if (allReferencesAreFlattened()) {
                Expression stringValueExpression = ((DocumentInstr) getSequence()).getStringValueExpression();
                stringValueExpression = stringValueExpression.typeCheck(visitor, contextItemType);
                setSequence(stringValueExpression);
                requiredType = SequenceType.SINGLE_UNTYPED_ATOMIC;
                adoptChildExpression(getSequence());
                refineTypeInformation(requiredType.getPrimaryType(), requiredType.getCardinality(), null, 0, this);
            }
        }

        // If the initializing expression has (potential) side-effects, prevent optimizations such as elimination
        // of unused variables
        if (getSequence().hasSpecialProperty(StaticProperty.HAS_SIDE_EFFECTS)) {
            needsEagerEvaluation = true;
        }

        // Removal of redundant variables, and inlining of variables that are only used once, depends on accurate
        // knowledge of all references to the variable. The problem is that obtaining this knowledge can be expensive:
        // see bug 2707. On the other hand, failing to do these optimizations is not fatal. So the general approach
        // is that we limit the time spent discovering the information, and we don't do the optimization unless
        // it is safe.

        // Typically on entry to optimize(), the typeCheck() method has already been called, and this has set up
        // a list of references. First we examine this list of references and remove any that are "dead", that is
        // they no longer have this LetExpression as an ancestor in the expression tree. This function also checks
        // whether any of these references are known to be in a loop, and returns true if so.

        hasLoopingReference |= removeDeadReferences();

        if (!needsEagerEvaluation) {

            // If there are less than two references, and none is in a loop, then there is potential
            // for optimization. But we now need to be absolutely sure that we have an accurate list
            // of references.

            boolean considerRemoval = ((references != null && references.size() < 2) || getSequence() instanceof VariableReference) &&
                    !indexedVariable && !hasLoopingReference && !needsEagerEvaluation;

            if (considerRemoval) {
                verifyReferences();
                // At this point the list of references is either accurate, or null
                considerRemoval = references != null;
            }

            if (considerRemoval && references.isEmpty()) {
                // variable is not used - no need to evaluate it
                getActionOp().optimize(visitor, contextItemType);
                opt.trace("Eliminated unused variable " + getVariableName(), getAction());
                return getAction();
            }

            // Don't inline context-dependent variables in a streamable template. See strmode011.
            // The reason for this is that a variable <xsl:variable><xsl:copy-of select="."/></xsl:variable>
            // can be evaluated in streaming mode, but an arbitrary expression using copy() inline can't (e.g.
            // if it appears in a path expression or as an operand of an arithmetic expression)

            if (considerRemoval && references.size() == 1 && ExpressionTool.dependsOnFocus(getSequence()) ) {
                if (visitor.isOptimizeForStreaming()) {
                    considerRemoval = false;
                }
                // Disallow inlining if the focus of the variable reference differs from the containing binding.
                Expression child = references.get(0);
                Expression parent = child.getParentExpression();
                while (parent != null && parent != this) {
                    Operand operand = ExpressionTool.findOperand(parent, child);
                    assert operand != null;
                    if (!operand.hasSameFocus()) {
                        considerRemoval = false;
                        break;
                    }
                    child = parent;
                    parent = child.getParentExpression();
                }
            }

            if (considerRemoval && references.size() == 1) {
                if (ExpressionTool.changesXsltContext(getSequence())) {
                    // Don't inline variables whose initializer might contain a call to xsl:result-document
                    considerRemoval = false;
                } else if ((getSequence().getDependencies() & StaticProperty.DEPENDS_ON_CURRENT_GROUP) != 0) {
                    // Don't inline variables that depend on current-group() or current-grouping-key()
                    considerRemoval = false;
                } else if (references.get(0).isInLoop()) {
                    // Don't inline variables where the variable reference is evaluated repeatedly
                    considerRemoval = false;
                }
            }

            if (considerRemoval && (references.size() == 1 || getSequence() instanceof Literal ||
                                            getSequence() instanceof VariableReference) &&
                    opt.isOptionSet(OptimizerOptions.INLINE_VARIABLES)) {
                inlineReferences();
                opt.trace("Inlined references to $" + getVariableName(), getAction());
                references = null;
                return getAction().optimize(visitor, contextItemType);
            }
        }

        int tries = 0;
        while (tries++ < 5) {
            Expression seq0 = getSequence();
            getSequenceOp().optimize(visitor, contextItemType);
            if (getSequence() instanceof Literal && !indexedVariable && opt.isOptionSet(OptimizerOptions.INLINE_VARIABLES)) {
                return optimize(visitor, contextItemType);
            }
            if (seq0 == getSequence()) {
                break;
            }
        }

        tries = 0;
        while (tries++ < 5) {
            Expression act0 = getAction();
            getActionOp().optimize(visitor, contextItemType);
            if (act0 == getAction()) {
                break;
            }
            if (!indexedVariable && !needsEagerEvaluation) {
                verifyReferences();
                if (references != null && references.size() < 2) {
                    if (references.isEmpty()) {
                        // We may have removed references to the variable; try again at eliminating this expression.
                        hasLoopingReference = false;
                        return optimize(visitor, contextItemType);
                    } else {
                        // there is one remaining reference; try again at inlining if it's not in a loop
                        if (!references.get(0).isInLoop()) {
                            return optimize(visitor, contextItemType);
                        }
                    }
                }
            }
        }

        // Don't use lazy evaluation for a variable that is referenced inside the "try" part of a contained try catch (XSLT3 test try-031)

        //setEvaluator();
        return this;
    }

    public void setEvaluator() {
        if (isIndexedVariable()) {
            PullEvaluator pullEval = getSequence().makeElaborator().elaborateForPull();
            setEvaluator(new IndexedVariableEvaluator(pullEval));
        } else if (needsEagerEvaluation || !getSequence().supportsLazyEvaluation()) {
            setEvaluator(getSequence().makeElaborator().eagerly());
        } else if (needsLazyEvaluation) {
            setEvaluator(getSequence().makeElaborator().lazily(getNominalReferenceCount() > 1, needsLazyEvaluation));
        } else if (evaluator == null) {
            setEvaluator(LearningEvaluator.makeLearningEvaluator(
                    getSequence(),
                    getSequence().makeElaborator().lazily(getNominalReferenceCount() > 1, false)));
        }
    }

    private void inlineReferences() {
        // Note that the list of references might include references that are no longer reachable on the tree.
        // We therefore take no action if (a) the parent of the reference is null, or (b) the reference is
        // not found among the children of its parent.
        for (VariableReference ref : references) {
            Expression parent = ref.getParentExpression();
            if (parent != null) {
                Operand o = ExpressionTool.findOperand(parent, ref);
                if (o != null) {
                    o.setChildExpression(getSequence().copy(new RebindingMap()));
                }
                ExpressionTool.resetStaticProperties(parent);
            }
        }
    }

    /**
     * Return the estimated cost of evaluating an expression. This is a very crude measure based
     * on the syntactic form of the expression (we have no knowledge of data values). We take
     * the cost of evaluating a simple scalar comparison or arithmetic expression as 1 (one),
     * and we assume that a sequence has length 5. The resulting estimates may be used, for
     * example, to reorder the predicates in a filter expression so cheaper predicates are
     * evaluated first.
     * @return a rough estimate of the evaluation cost
     */
    @Override
    public double getCost() {
        return getSequence().getCost() + getAction().getCost();
    }

    /**
     * Determine whether all references to this variable are using the value either
     * (a) by atomizing it, or (b) by taking its string value. (This excludes usages
     * such as testing the existence of a node or taking the effective boolean value).
     *
     * @return true if all references are known to atomize (or stringify) the value,
     *         false otherwise. The value false may indicate "not known".
     */

    private boolean allReferencesAreFlattened() {
        if (references != null) {
            for (VariableReference ref : references) {
                if (!ref.isFlattened()) {
                    return false;
                }
            }
            return true;
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
        return getAction().isVacuousExpression();
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
        getAction().checkPermittedContents(parentType, whole);
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
        return getAction().getImplementationMethod();
    }

    /**
     * Get the properties of this object to be included in trace messages, by supplying
     * the property values to a supplied consumer function
     *
     * @param consumer the function to which the properties should be supplied, as (property name,
     *                 value) pairs.
     */
    @Override
    public void gatherProperties(BiConsumer<String, Object> consumer) {
        consumer.accept("name", getVariableQName());
    }


    /**
     * Iterate over the result of the expression to return a sequence of items
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        // minimize stack consumption by evaluating nested LET expressions iteratively
        LetExpression let = this;
        while (true) {
            Sequence val = let.eval(context);
            context.setLocalVariable(let.getLocalSlotNumber(), val);
            if (let.getAction() instanceof LetExpression) {
                let = (LetExpression) let.getAction();
            } else {
                break;
            }
        }
        return let.getAction().iterate(context);
    }


    /**
     * Evaluate the variable.
     *
     * @param context the dynamic evaluation context
     * @return the result of evaluating the expression that is bound to the variable
     * @throws XPathException if evaluation of the variable fails
     */

    public Sequence eval(XPathContext context) throws XPathException {
        if (evaluator == null) {
            if (needsEagerEvaluation) {
                setEvaluator(getSequence().makeElaborator().eagerly());
            } else {
                setEvaluator(LearningEvaluator.makeLearningEvaluator(
                        getSequence(), getSequence().makeElaborator().lazily(getNominalReferenceCount() > 1, false)));
            }
        }
        try {
            int savedOutputState = context.getTemporaryOutputState();
            context.setTemporaryOutputState(StandardNames.XSL_VARIABLE);
            Sequence result = evaluator.evaluate(context);
            context.setTemporaryOutputState(savedOutputState);
            return result;
        } catch (ClassCastException e) {
            // Probably the evaluation mode is wrong, as a result of an expression rewrite. Try again.
            assert false;
            int savedOutputState = context.getTemporaryOutputState();
            context.setTemporaryOutputState(StandardNames.XSL_VARIABLE);
            Sequence result = ExpressionTool.eagerEvaluate(getSequence(), context);
            context.setTemporaryOutputState(savedOutputState);
            return result;
        }
    }

    /**
     * Evaluate the expression as a singleton
     */

    @Override
    public Item evaluateItem(XPathContext context) throws XPathException {
        return makeElaborator().elaborateForItem().eval(context);
    }

    /**
     * Get the effective boolean value of the expression. This returns false if the value
     * is the empty sequence, a zero-length string, a number equal to zero, or the boolean
     * false. Otherwise it returns true.
     *
     * @param context The context in which the expression is to be evaluated
     * @return the effective boolean value
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */

    @Override
    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        // minimize stack consumption by evaluating nested LET expressions iteratively
        LetExpression let = this;
        while (true) {
            Sequence val = let.eval(context);
            context.setLocalVariable(let.getLocalSlotNumber(), val);
            if (let.getAction() instanceof LetExpression) {
                let = (LetExpression) let.getAction();
            } else {
                break;
            }
        }
        return let.getAction().effectiveBooleanValue(context);
    }

    /**
     * Process this expression as an instruction, writing results to the current
     * outputter
     */

    @Override
    public void process(Outputter output, XPathContext context) throws XPathException {
        dispatchTailCall(makeElaborator().elaborateForPush().processLeavingTail(output, context));
    }


    /**
     * Determine the data type of the items returned by the expression, if possible
     *
     * @return one of the values Type.STRING, Type.BOOLEAN, Type.NUMBER, Type.NODE,
     *         or Type.ITEM (meaning not known in advance)
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return getAction().getItemType();
    }

    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     * @param contextItemType the type of the context item
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        if (isInstruction()) {
            return UType.ANY;
        }
        return getAction().getStaticUType(contextItemType);
    }

    /**
     * Determine the static cardinality of the expression
     */

    @Override
    protected int computeCardinality() {
        return getAction().getCardinality();
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     */

    @Override
    protected int computeSpecialProperties() {
        int props = getAction().getSpecialProperties();
        int seqProps = getSequence().getSpecialProperties();
        if ((seqProps & StaticProperty.NO_NODES_NEWLY_CREATED) == 0) {
            props &= ~StaticProperty.NO_NODES_NEWLY_CREATED;
        }
        return props;
    }

    /**
     * Mark tail function calls
     */

    @Override
    public int markTailFunctionCalls(StructuredQName qName, int arity) {
        return ExpressionTool.markTailFunctionCalls(getAction(), qName, arity);
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings variables whose binding needs to change
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        LetExpression let = new LetExpression();
        rebindings.put(this, let);
        let.indexedVariable = indexedVariable;
        let.hasLoopingReference = hasLoopingReference;
        let.setNeedsEagerEvaluation(needsEagerEvaluation);
        let.setNeedsLazyEvaluation(needsLazyEvaluation);
        let.setVariableQName(variableName);
        let.setRequiredType(requiredType);
        let.setSequence(getSequence().copy(rebindings));
        let.setInstruction(isInstruction());
        ExpressionTool.copyLocationInfo(this, let);
        Expression newAction = getAction().copy(rebindings);
        let.setAction(newAction);
        return let;
    }


    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     *
     * @return a representation of the expression as a string
     */

    public String toString() {
        return "let $" + getVariableEQName() + " := " + getSequence() +
                " return " + ExpressionTool.parenthesize(getAction());
    }

    /**
     * Produce a short string identifying the expression for use in error messages
     *
     * @return a short string, sufficient to identify the expression
     */
    @Override
    public String toShortString() {
        return "let $" + getVariableName() + " := ...";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("let", this);
        out.emitAttribute("var", variableName);
        if (getRequiredType() != SequenceType.ANY_SEQUENCE) {
            out.emitAttribute("as", getRequiredType().toAlphaCode());
        }
        if (isIndexedVariable()) {
            out.emitAttribute("indexable", "true");
        }
        out.emitAttribute("slot", getLocalSlotNumber() + "");
        if (needsEagerEvaluation || needsLazyEvaluation) {
            String flags = (needsEagerEvaluation ? "e" : "")
                    + (needsLazyEvaluation ? "l" : "");
            out.emitAttribute("flags", flags);
        }
        getSequence().export(out);
        getAction().export(out);
        out.endElement();
    }

    public void setEvaluator(SequenceEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public SequenceEvaluator getEvaluator() {
        return evaluator;
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new LetExprElaborator();
    }

    /**
     * Elaborator for a let expression (either {@code let $x := SELECT return ACTION} in XPath, or the
     * equivalent sequence constructor using local {@code xsl:variable} declarations in XSLT).
     *
     * <p>The elaborator allows evaluation in pull, push, or singleton mode.</p>
     */

    public static class LetExprElaborator extends PullElaborator {
        

        private SequenceEvaluator makeSequenceEvaluator(LetExpression let) {
            if (let.evaluator != null) {
                return let.evaluator;
            }
            let.setEvaluator();
            return let.evaluator;
        }

        /**
         * Return a function that evaluates the local variable in this let expression, and in all
         * immediately-nested let expressions. This is done to reduce Java stack usage and the number
         * of method/function calls
         * @param start the initial let expression
         * @param finalAction an output parameter; this is set to the the "return"
         *        expression that follows the nested local variables.
         * @return a function to evaluate all the variables. This is returned as an {@code ItemEvaluator} for
         * convenience, but it is executed for its side-effects (setting variable values in the context
         * stack frame) and always returns null.
         */
        private ItemEvaluator setAllVariables(LetExpression start, List<Expression> finalAction) {
            List<LetExpression> setters = new ArrayList<>();
            setters.add(start);
            Expression next = start.getAction();
            while (next instanceof LetExpression) {
                setters.add((LetExpression)next);
                next = ((LetExpression)next).getAction();
            }
            finalAction.add(next);
            switch (setters.size()) {
                // for a small number of local variables, unroll the loop
                case 1 -> {
                    LetExpression let = setters.get(0);
                    SequenceEvaluator evaluator = makeSequenceEvaluator(let);
                    int slot = setters.get(0).slotNumber;
                    return context -> {
                        context.setLocalVariable(slot, evaluator.evaluate(context));
                        return null;
                    };
                }
                case 2 -> {
                    SequenceEvaluator evaluator0 = makeSequenceEvaluator(setters.get(0));
                    int slot0 = setters.get(0).slotNumber;
                    SequenceEvaluator evaluator1 = makeSequenceEvaluator(setters.get(1));
                    int slot1 = setters.get(1).slotNumber;
                    return context -> {
                        context.setLocalVariable(slot0, evaluator0.evaluate(context));
                        context.setLocalVariable(slot1, evaluator1.evaluate(context));
                        return null;
                    };
                }
                case 3 -> {
                    SequenceEvaluator evaluator0 = makeSequenceEvaluator(setters.get(0));
                    int slot0 = setters.get(0).slotNumber;
                    SequenceEvaluator evaluator1 = makeSequenceEvaluator(setters.get(1));
                    int slot1 = setters.get(1).slotNumber;
                    SequenceEvaluator evaluator2 = makeSequenceEvaluator(setters.get(2));
                    int slot2 = setters.get(2).slotNumber;
                    return context -> {
                        context.setLocalVariable(slot0, evaluator0.evaluate(context));
                        context.setLocalVariable(slot1, evaluator1.evaluate(context));
                        context.setLocalVariable(slot2, evaluator2.evaluate(context));
                        return null;
                    };
                }
                case 4 -> {
                    SequenceEvaluator evaluator0 = makeSequenceEvaluator(setters.get(0));
                    int slot0 = setters.get(0).slotNumber;
                    SequenceEvaluator evaluator1 = makeSequenceEvaluator(setters.get(1));
                    int slot1 = setters.get(1).slotNumber;
                    SequenceEvaluator evaluator2 = makeSequenceEvaluator(setters.get(2));
                    int slot2 = setters.get(2).slotNumber;
                    SequenceEvaluator evaluator3 = makeSequenceEvaluator(setters.get(3));
                    int slot3 = setters.get(3).slotNumber;
                    return context -> {
                        context.setLocalVariable(slot0, evaluator0.evaluate(context));
                        context.setLocalVariable(slot1, evaluator1.evaluate(context));
                        context.setLocalVariable(slot2, evaluator2.evaluate(context));
                        context.setLocalVariable(slot3, evaluator3.evaluate(context));
                        return null;
                    };
                }
                default -> {
                    SequenceEvaluator[] evaluators = new SequenceEvaluator[setters.size()];
                    int[] slots = new int[setters.size()];
                    for (int i = 0; i < setters.size(); i++) {
                        evaluators[i] = makeSequenceEvaluator(setters.get(i));
                        slots[i] = setters.get(i).slotNumber;
                    }
                    return context -> {
                        for (int i = 0; i < slots.length; i++) {
                            context.setLocalVariable(slots[i], evaluators[i].evaluate(context));
                        }
                        return null;
                    };
                }
            }

        }

        @Override
        public SequenceEvaluator eagerly() {
            final LetExpression expr = (LetExpression) getExpression();
            if (expr.needsLazyEvaluation) {
                return lazily(true, true);
            }
            final SequenceEvaluator selectEval = expr.getSequence().makeElaborator().eagerly();
            final SequenceEvaluator actionEval = expr.getAction().makeElaborator().eagerly();
            final int slot = expr.getLocalSlotNumber();
            return new EagerLocalVariableEvaluator(slot, selectEval, actionEval);
        }

        @Override
        public PullEvaluator elaborateForPull() {
            final LetExpression expr = (LetExpression) getExpression();
            final List<Expression> finalAction = new ArrayList<>(1);
            final ItemEvaluator setter = setAllVariables(expr, finalAction);
            final PullEvaluator actionPull = finalAction.get(0).makeElaborator().elaborateForPull();
            return context -> {
                int savedOutputState = context.getTemporaryOutputState();
                context.setTemporaryOutputState(StandardNames.XSL_VARIABLE);
                setter.eval(context);
                context.setTemporaryOutputState(savedOutputState);
                return actionPull.iterate(context);
            };
        }

        @Override
        public PushEvaluator elaborateForPush() {
            final LetExpression expr = (LetExpression) getExpression();
            final List<Expression> finalAction = new ArrayList<>(1);
            final ItemEvaluator setter = setAllVariables(expr, finalAction);
            final PushEvaluator actionPush = finalAction.get(0).makeElaborator().elaborateForPush();
            return (out, context) -> {
                int savedOutputState = context.getTemporaryOutputState();
                context.setTemporaryOutputState(StandardNames.XSL_VARIABLE);
                setter.eval(context);
                context.setTemporaryOutputState(savedOutputState);
                return actionPush.processLeavingTail(out, context);
            };
        }

        @Override
        public ItemEvaluator elaborateForItem() {
            final LetExpression expr = (LetExpression) getExpression();
            final List<Expression> finalAction = new ArrayList<>(1);
            final ItemEvaluator setter = setAllVariables(expr, finalAction);
            final ItemEvaluator actionEval = finalAction.get(0).makeElaborator().elaborateForItem();
            return context -> {
                int savedOutputState = context.getTemporaryOutputState();
                context.setTemporaryOutputState(StandardNames.XSL_VARIABLE);
                setter.eval(context);
                context.setTemporaryOutputState(savedOutputState);
                return actionEval.eval(context);
            };
        }

        @Override
        public UpdateEvaluator elaborateForUpdate() {
            final LetExpression expr = (LetExpression) getExpression();
            final List<Expression> finalAction = new ArrayList<>(1);
            final ItemEvaluator setter = setAllVariables(expr, finalAction);
            final UpdateEvaluator actionEval = finalAction.get(0).makeElaborator().elaborateForUpdate();
            return (context, pul) -> {
                int savedOutputState = context.getTemporaryOutputState();
                context.setTemporaryOutputState(StandardNames.XSL_VARIABLE);
                setter.eval(context);
                context.setTemporaryOutputState(savedOutputState);
                actionEval.registerUpdates(context, pul);
            };
        }
        
    }

    private static class EagerLocalVariableEvaluator implements SequenceEvaluator {

        private final int slot;
        private final SequenceEvaluator selectEval;
        private final SequenceEvaluator actionEval;

        public EagerLocalVariableEvaluator(int slot, SequenceEvaluator selectEval, SequenceEvaluator actionEval) {
            this.slot = slot;
            this.selectEval = selectEval;
            this.actionEval = actionEval;
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
            int savedOutputState = context.getTemporaryOutputState();
            context.setTemporaryOutputState(StandardNames.XSL_VARIABLE);
            Sequence value = selectEval.evaluate(context);
            context.setLocalVariable(slot, value);
            context.setTemporaryOutputState(savedOutputState);
            return actionEval.evaluate(context).materialize();
        }

    }

}

