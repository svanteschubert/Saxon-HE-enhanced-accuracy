////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.sort.DocumentSorter;
import net.sf.saxon.functions.PositionAndLast;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.NodeSetPattern;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.style.XSLFunction;
import net.sf.saxon.style.XSLTemplate;
import net.sf.saxon.trans.GlobalVariableManager;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.rules.RuleTarget;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.BooleanValue;

/**
 * This class performs optimizations that vary between different versions of the Saxon product.
 * The optimizer is obtained from the Saxon Configuration. This class is the version used in Saxon-HE,
 * which in most cases does no optimization at all: the methods are provided so that they can be
 * overridden in Saxon-EE.
 */
public class Optimizer {

    /*@NotNull*/ protected Configuration config;
    private OptimizerOptions optimizerOptions = OptimizerOptions.FULL_EE_OPTIMIZATION;
    protected boolean tracing;

    /**
     * Create an Optimizer.
     *
     * @param config the Saxon configuration
     */

    public Optimizer(Configuration config) {
        this.config = config;
        this.tracing = config.getBooleanProperty(Feature.TRACE_OPTIMIZER_DECISIONS);
    }

    /**
     * Get the Saxon configuration object
     *
     * @return the configuration
     */

    /*@NotNull*/
    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Set the optimizer options
     *
     * @param options the optimizer options
     */

    public void setOptimizerOptions(OptimizerOptions options) {
        optimizerOptions = options;
    }

    /**
     * Get the optimizer options
     *
     * @return the optimizer options
     */

    public OptimizerOptions getOptimizerOptions() {
        return optimizerOptions;
    }

    /**
     * Ask whether a particular optimizer option is set
     * @param option the code identifying the option, e.g. {@link OptimizerOptions#BYTE_CODE}
     *            @return true if the option is set
     */

    public boolean isOptionSet(int option) {
        return optimizerOptions.isSet(option);
    }

    /**
     * Optimize a value comparison
     *
     * @param vc          the value comparison expression
     * @param visitor     the expression visitor
     * @param contextInfo static context item information
     * @return either the original value comparison, or an optimized equivalent
     * @throws XPathException if things go wrong
     */

    public Expression optimizeValueComparison(
            ValueComparison vc, ExpressionVisitor visitor, ContextItemStaticInfo contextInfo)
            throws XPathException {

        Expression lhs = vc.getLhsExpression();
        Expression rhs = vc.getRhsExpression();

        Expression e2 = optimizePositionVsLast(lhs, rhs, vc.getOperator());
        if (e2 != null) {
            trace("Rewrote position() ~= last()", e2);
            return e2;
        }
        e2 = optimizePositionVsLast(rhs, lhs, Token.inverse(vc.getOperator()));
        if (e2 != null) {
            trace("Rewrote last() ~= position()", e2);
            return e2;
        }
        return vc;
    }

    private Expression optimizePositionVsLast(Expression lhs, Expression rhs, int operator) {

        // optimise [position()=last()] etc

        if (lhs.isCallOn(PositionAndLast.Position.class) &&
                rhs.isCallOn(PositionAndLast.Last.class)) {
            switch (operator) {
                case Token.FEQ:
                case Token.FGE:
                    IsLastExpression iletrue = new IsLastExpression(true);
                    ExpressionTool.copyLocationInfo(lhs, iletrue);
                    return iletrue;
                case Token.FNE:
                case Token.FLT:
                    IsLastExpression ilefalse = new IsLastExpression(false);
                    ExpressionTool.copyLocationInfo(lhs, ilefalse);
                    return ilefalse;
                case Token.FGT:
                    return Literal.makeLiteral(BooleanValue.FALSE, lhs);
                case Token.FLE:
                    return Literal.makeLiteral(BooleanValue.TRUE, lhs);
            }
        }
        return null;

    }

    /**
     * Simplify a GeneralComparison expression
     *
     * @param visitor             the expression visitor
     * @param gc                  the GeneralComparison to be simplified
     * @param backwardsCompatible true if in 1.0 compatibility mode
     * @return the simplified expression
     */

    public Expression optimizeGeneralComparison(ExpressionVisitor visitor,
                                                GeneralComparison gc, boolean backwardsCompatible, ContextItemStaticInfo contextItemType) {
        return gc;
    }

    /**
     * Attempt to optimize a call on saxon:stream(). Return null if no optimization is possible.
     *
     * @param select the expression that selects the items to be copied
     * @return null if no optimization is possible, or an expression that does an optimized
     * copy of these items otherwise
     */

    /*@Nullable*/
    public Expression optimizeSaxonStreamFunction(ExpressionVisitor visitor, ContextItemStaticInfo cisi, Expression select) throws XPathException {
        if (select.getItemType().isPlainType()) {
            return select;
        }
        return null;
    }


    /**
     * Examine a path expression to see whether it can be replaced by a call on the key() function;
     * if so, generate an appropriate key definition and return the call on key(). If not, return null.
     *
     * @param pathExp The path expression to be converted.
     * @param visitor The expression visitor
     * @return the optimized expression, or null if no optimization is possible
     */

    public Expression convertPathExpressionToKey(SlashExpression pathExp, ExpressionVisitor visitor)
            throws XPathException {
        return null;
    }

    /**
     * Try converting a filter expression to a call on the key function. Return the supplied
     * expression unchanged if not possible
     *
     * @param f                 the filter expression to be converted
     * @param visitor           the expression visitor, which must be currently visiting the filter expression f
     * @param indexFirstOperand true if the first operand of the filter comparison is to be indexed;
     *                          false if it is the second operand
     * @param contextIsDoc      true if the context item is known to be a document node
     * @return the optimized expression, or the unchanged expression f if no optimization is possible
     */

    public Expression tryIndexedFilter(FilterExpression f, ExpressionVisitor visitor, boolean indexFirstOperand, boolean contextIsDoc) {
        return f;
    }

    /**
     * Consider reordering the predicates in a filter expression based on cost estimates and other criteria
     *
     * @param f       the filter expression
     * @param visitor expression visitor
     * @param cisi    information about the context item type
     * @return either the original expression, or an optimized replacement
     * @throws XPathException if things go wrong
     */

    public FilterExpression reorderPredicates(FilterExpression f, ExpressionVisitor visitor, ContextItemStaticInfo cisi)
            throws XPathException {
        return f;
    }

    /**
     * Convert a path expression such as a/b/c[predicate] into a filter expression
     * of the form (a/b/c)[predicate]. This is possible whenever the predicate is non-positional.
     * The conversion is useful in the case where the path expression appears inside a loop,
     * where the predicate depends on the loop variable but a/b/c does not.
     *
     * @param pathExp the path expression to be converted
     * @param th      the type hierarchy cache
     * @return the resulting filterexpression if conversion is possible, or null if not
     */

    public FilterExpression convertToFilterExpression(SlashExpression pathExp, TypeHierarchy th)
            throws XPathException {
        return null;
    }

    /**
     * Test whether a filter predicate is indexable.
     *
     * @param filter the predicate expression
     * @return 0 if not indexable; +1 if the predicate is in the form expression=value; -1 if it is in
     * the form value=expression
     */

    public int isIndexableFilter(Expression filter) {
        return 0;
    }

    /**
     * Create an indexed value
     *
     * @param iter the iterator that delivers the sequence of values to be indexed
     * @return the indexed value
     * @throws UnsupportedOperationException this method should not be called in Saxon-HE
     */

    public GroundedValue makeIndexedValue(SequenceIterator iter) throws XPathException {
        throw new UnsupportedOperationException("Indexing requires Saxon-EE");
    }

    public void optimizeNodeSetPattern(NodeSetPattern pattern) {
        // No action in Saxon-HE
    }

    /**
     * Prepare an expression for streaming
     * @param exp the expression to be prepared
     */

    public void prepareForStreaming(Expression exp) throws XPathException {
        // No action except for Saxon-EE
    }

    /**
     * Evaluate the streaming (first) argument of a streamable stylesheet function
     *
     * @param expr    the expression supplied for the value of the streaming argument
     * @param context the XPath evaluation context of the caller
     * @return the (nominal) result of the evaluation
     */

    public Sequence evaluateStreamingArgument(Expression expr, XPathContext context) throws XPathException {
        // non-streaming fallback implementation
        return ExpressionTool.eagerEvaluate(expr, context);
    }

    /**
     * Determine whether it is possible to rearrange an expression so that all references to a given
     * variable are replaced by a reference to ".". This is true of there are no references to the variable
     * within a filter predicate or on the rhs of a "/" operator.
     *
     * @param exp     the expression in question
     * @param binding an array of bindings defining range variables; the method tests that there are no
     *                references to any of these variables within a predicate or on the rhs of "/"
     * @return true if the variable reference can be replaced
     */

    public boolean isVariableReplaceableByDot(Expression exp, Binding[] binding) {
        // TODO: the fact that a variable reference appears inside a predicate (etc) shouldn't stop us
        // rewriting a where clause as a predicate. We just have to bind a new variable:
        // for $x in P where abc[n = $x/m] ==> for $x in P[let $z := . return abc[n = $z/m]
        // We could probably do this in all cases and then let $z be optimized away where appropriate

        for (Operand o : exp.operands()) {
            if (o.hasSameFocus()) {
                if (!isVariableReplaceableByDot(o.getChildExpression(), binding)) {
                    return false;
                }
            } else if (ExpressionTool.dependsOnVariable(o.getChildExpression(), binding)) {
                return false;
            }
        }
        return true;
    }


    /**
     * Make a conditional document sorter. This optimization is attempted
     * when a DocumentSorter is wrapped around a path expression
     *
     * @param sorter the document sorter
     * @param path   the path expression
     * @return the original sorter unchanged when no optimization is possible, which is always the
     * case in Saxon-HE
     */

    public Expression makeConditionalDocumentSorter(DocumentSorter sorter, SlashExpression path) throws XPathException {
        return sorter;
    }

    /**
     * Replace a function call by the body of the function, assuming all conditions for inlining
     * the function are satisfied
     *
     * @param functionCall    the functionCall expression
     * @param visitor         the expression visitor
     * @param contextItemType the context item type
     * @return either the original expression unchanged, or an expression that consists of the inlined
     * function body, with all function parameters bound as required. In Saxon-HE, function inlining is
     * not supported, so the original functionCall is always returned unchanged
     */

    public Expression tryInlineFunctionCall(
            UserFunctionCall functionCall, ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) {
        return functionCall;
    }

    /**
     * Identify expressions within a function or template body that can be promoted to be
     * evaluated as global variables.
     *
     * @param body    the body of the template or function
     * @param visitor the expression visitor
     * @return the expression after subexpressions have been promoted to global variables; or null if
     * nothing has changed
     */

    public Expression promoteExpressionsToGlobal(Expression body, GlobalVariableManager gvManager, ExpressionVisitor visitor)
            throws XPathException {
        return null;
    }

    /**
     * Eliminate common subexpressions. Rewrites (contains(X, 'x') and contains(X, 'y')) as
     * (let $vv:C := X return (contains($vv:C, 'x') and contains($vv:C, 'y'))).
     * Dummy method; the optimization happens only in Saxon-EE.
     * @param in the expression to be optimized
     * @return out the optimized expression (possibly the same as the input).
     */

    public Expression eliminateCommonSubexpressions(Expression in) {
        return in;
    }

    /**
     * Try to convert a Choose expression into a switch
     *
     * @param choose the Choose expression
     * @return the result of optimizing this (the original expression if no optimization was possible)
     */

    public Expression trySwitch(Choose choose, ExpressionVisitor visitor) {
        return choose;
    }


    /**
     * Try to convert an Or expression into a comparison with Literal sequence
     *
     * @param visitor         an expression visitor
     * @param contextItemType the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @param orExpr          the expression to be converted
     * @return the result of optimizing the Or expression (the original expression if no optimization was possible)
     */

    public Expression tryGeneralComparison(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType, OrExpression orExpr) throws XPathException {
        return orExpr;
    }

    /**
     * Extract subexpressions from the body of a function that can be evaluated
     * as global variables
     *
     * @param body  the body of the function
     * @param offer The PromotionOffer. Will be marked to indicate whether any action was taken
     * @return a reference to the new global variable if a variable has been created, or null if not
     */

//    public Expression extractGlobalVariables(Expression body, ExpressionVisitor visitor, PromotionOffer offer)
//            throws XPathException {
//        return null;
//    }

    /**
     * Generate the inversion of the expression comprising the body of a template rules.
     * Supported in Saxon-EE only
     *
     * @param pattern  the match pattern of this template rule
     * @param template the template to be inverted
     */

    public RuleTarget makeInversion(Pattern pattern, NamedTemplate template) throws XPathException {
        return null;
    }

    /**
     * In streaming mode, make the copy operation applied to subexpressions of a complex-content
     * sequence constructor into explicit copy-of operations.
     */

    public void makeCopyOperationsExplicit(Expression parent, Operand child) throws XPathException {
        // no action unless streaming
    }

    /**
     * Check the streamability of a template
     *
     * @param sourceTemplate   the source of the template in the stylesheet tree
     * @param compiledTemplate the compiled template
     * @throws XPathException if the template is declared streamable but does not satisfy the straming rules
     */

    public void checkStreamability(XSLTemplate sourceTemplate, TemplateRule compiledTemplate) throws XPathException {
        // no action unless streaming
    }



    /**
     * In streaming mode, optimizer a QuantifiedExpression for streaming
     *
     * @param expr the expression to be optimized
     * @return the optimized expression
     */

    public Expression optimizeQuantifiedExpressionForStreaming(QuantifiedExpression expr) throws XPathException {
        return expr;
    }


    /**
     * Generate a multi-threaded version of an instruction.
     * Supported in Saxon-EE only; ignored with no action in Saxon-HE and Saxon-PE
     *
     * @param instruction the instruction to be multi-threaded
     * @return the multi-threaded version of the instruction
     */

    public Expression generateMultithreadedInstruction(Expression instruction) {
        return instruction;
    }

    /**
     * Generate Java byte code for an expression
     *
     * @param compilerService
     * @param expr              the expression to be compiled
     * @param objectName        the name of the object (e.g. function) being compiled
     * @param evaluationMethods The evaluation modes for which code is generated. Currently a subset of
     *                          {@link Expression#PROCESS_METHOD}, {@link Expression#ITERATE_METHOD}. If no code is generated for
     */

    public Expression compileToByteCode(ICompilerService compilerService, Expression expr, String objectName, int evaluationMethods) {
        return null;
    }

    /**
     * Insert a ByteCodeCandidate into the expression tree. A ByteCodeCandidate monitors how many
     * times it is executed, and after reaching a certain threshold, generates bytecode for faster
     * evaluation of its target expression
     *
     * @param owner                   the owning construct in the expression tree
     * @param expr                    the child (target) expression
     * @param objectName              name to be used in forming the name of the bytecode class
     * @param requiredEvaluationModes the evaluation modes for which code should be generated
     * @return a new ByteCodeCandidate if the expression is suitable for bytecode generation,
     * or the supplied target expression otherwise.
     */


    public Expression makeByteCodeCandidate(ExpressionOwner owner, Expression expr, String objectName, int requiredEvaluationModes) {
        return expr;
    }

    public void injectByteCodeCandidates(Expression exp) throws XPathException {

    }

    public Expression optimizeNumberInstruction(NumberInstruction ni, ContextItemStaticInfo contextInfo) {
        return null;
    }

    public void assessFunctionStreamability(XSLFunction reporter, UserFunction compiledFunction) throws XPathException {
        throw new XPathException("Streamable stylesheet functions are not supported in Saxon-HE", "XTSE3430");
    }

    /**
     * Trace optimization actions
     *
     * @param message the message to be displayed
     * @param exp     the expression after being rewritten
     */

    public void trace(String message, Expression exp) {
        if (tracing) {
            Logger err = getConfiguration().getLogger();
            err.info("OPT : At line " + exp.getLocation().getLineNumber() + " of " + exp.getLocation().getSystemId());
            err.info("OPT : " + message);
            err.info("OPT : Expression after rewrite: " + exp.toString());
            exp.verifyParentPointers();
        }
    }

    public static void trace(Configuration config, String message, Expression exp) {
        if (config.getBooleanProperty(Feature.TRACE_OPTIMIZER_DECISIONS)) {
            Logger err = config.getLogger();
            err.info("OPT : At line " + exp.getLocation().getLineNumber() + " of " + exp.getLocation().getSystemId());
            err.info("OPT : " + message);
            err.info("OPT : Expression after rewrite: " + exp.toString());
            exp.verifyParentPointers();
        }
    }

}

