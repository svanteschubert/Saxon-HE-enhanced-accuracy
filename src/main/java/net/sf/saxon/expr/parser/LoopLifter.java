////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.ConditionalInstruction;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.SequenceType;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Class to handle loop-lifting optimization, that is, extraction of subexpressions appearing within
 * a loop when there is no dependency on the controlling variable of the loop. This handles both
 * focus-dependent loops (such as xsl:for-each) and variable-dependent loops (such as XPath for-expressions),
 * and also takes into account specialist loops such as xsl:for-each-group, xsl:merge, and xsl:analyze-string.
 *
 * The class is instantiated to perform optimization of a component such as a function or template, and
 * it contains temporary instance-level data pertaining to that function or template.
 */

public class LoopLifter {

    /**
     * Apply loop-lifting to an expression (typically the body of a template or function)
     * @param exp         the expression to which loop lifting is applied
     * @param visitor     the expression visitor
     * @param contextInfo the static type of the context item
     * @return the optimized expression
     * @throws XPathException if any error occurs
     */

    public static Expression process(Expression exp, ExpressionVisitor visitor, ContextItemStaticInfo contextInfo)
    throws XPathException {
        //exp.verifyParentPointers();
        if (exp instanceof Literal || exp instanceof VariableReference) {
            return exp;
        } else {
            LoopLifter lifter = new LoopLifter(exp, visitor.getConfiguration(), visitor.isOptimizeForStreaming());
            RetainedStaticContext rsc = exp.getRetainedStaticContext();
            lifter.gatherInfo(exp);
            lifter.loopLift(exp);
            lifter.root.setRetainedStaticContext(rsc);
            lifter.root.setParentExpression(null);
            if (lifter.changed) {
                ExpressionTool.resetPropertiesWithinSubtree(lifter.root);
                Expression e2 = lifter.root.optimize(visitor, contextInfo);
                e2.setParentExpression(null);
                return e2;
            } else {
                return lifter.root;
            }
        }
    }

    private Expression root;
    private final Configuration config;
    private int sequence = 0;
    private boolean changed = false;
    private boolean tracing = false;
    private boolean streaming = false;
    private final static String MARKER = "marker";

    private static class ExpInfo {
        Expression expression;
        int loopLevel;
        boolean multiThreaded;
        ExpressionSet dependees;
    }

    /**
     * A class representing a set of expressions, compared using object identity rather than
     * equality; the internal implementation is optimised for the dominant case where the
     * set is either empty or contains a single entry. (However, an empty set is actually
     * represented as null).
     */

    private static class ExpressionSet {
        private Expression firstExpression;
        private Map<Expression, String> furtherExpressions;

        public void add(Expression exp) {
            if (firstExpression == null) {
                firstExpression = exp;
            } else if (firstExpression != exp) {
                if (furtherExpressions == null) {
                    furtherExpressions = new IdentityHashMap<>(8);
                    furtherExpressions.put(exp, MARKER);
                } else {
                    furtherExpressions.put(exp, MARKER);
                }
            }
        }

        public void addAll(ExpressionSet other) {
            if (other.firstExpression != null) {
                add(other.firstExpression);
                if (other.furtherExpressions != null) {
                    for (Expression exp : other.furtherExpressions.keySet()) {
                        add(exp);
                    }
                }
            }
        }

        public boolean contains(Expression exp) {
            if (firstExpression == null) {
                return false;
            }
            if (firstExpression == exp) {
                return true;
            }
            if (furtherExpressions != null) {
                return furtherExpressions.containsKey(exp);
            }
            return false;
        }

    }

    private final Map<Expression, ExpInfo> expInfoMap = new IdentityHashMap<>();

    public LoopLifter(Expression root, Configuration config, boolean streaming) {
        this.root = root;
        this.config = config;
        this.tracing = config.getBooleanProperty(Feature.TRACE_OPTIMIZER_DECISIONS);
        this.streaming = streaming;
    }

    public Expression getRoot() {
        return root;
    }

    /**
     * Gather information about an expression. The information (in the form of an ExpInfo object)
     * is added to the expInfoMap, which is indexed by expression.
     * @param exp the expression for which information is required
     */

    public void gatherInfo(Expression exp) {
        gatherInfo(exp, 0, 0, false);
    }

    private void gatherInfo(Expression exp, int level, int loopLevel, boolean multiThreaded) {
        ExpInfo info = new ExpInfo();
        info.expression = exp;
        info.loopLevel = loopLevel;
        info.multiThreaded = multiThreaded;
        expInfoMap.put(exp, info);
        Expression scope = exp.getScopingExpression();
        if (scope != null) {
            markDependencies(exp, scope);
        }
        boolean threaded = multiThreaded || exp.isMultiThreaded(config);
        // Don't loop-lift out of a conditional, because it can lead to type errors,
        // or out of a try/catch, because it can lead to errors not being caught
        Expression choose = getContainingConditional(exp);
        if (choose != null) {
            markDependencies(exp, choose);
        }
        for (Operand o : exp.operands()) {
            gatherInfo(o.getChildExpression(), level+1, o.isEvaluatedRepeatedly() ? loopLevel+1 : loopLevel, threaded);
        }
    }

    private Expression getContainingConditional(Expression exp) {
        Expression parent = exp.getParentExpression();
        while (parent != null) {
            if (parent instanceof ConditionalInstruction) {
                Operand o = ExpressionTool.findOperand(parent, exp);
                if (o == null) {
                    throw new AssertionError();
                }
                if (o.getOperandRole().isInChoiceGroup()) {
                    return parent;
                }
            } else if (parent instanceof TryCatch) {
                return parent;
            }
            exp = parent;
            parent = parent.getParentExpression();
        }
        return null;
    }


    private boolean mayReturnStreamedNodes(Expression exp) {
        // bug 3465: expressions returning streamed nodes cannot be loop-lifted,
        // because such nodes must not be bound to a variable
        // TODO: attempt a more rigorous analysis - see bug 3465
        return streaming && !exp.getItemType().getUType().intersection(UType.ANY_NODE).equals(UType.VOID);
    }

    /**
     * Register the dependencies of an expressions, and its applicable ancestor expressions, on some ancestor
     * expression that binds a variable or the focus
     * @param exp the dependent expression
     * @param variableSetter the expression that sets the focus or the variable in question. May be null, in which
     *                       case no dependencies are marked.
     */

    private void markDependencies(Expression exp, Expression variableSetter) {
        Expression parent;
        if (variableSetter != null) {
            parent = exp;
            while (parent != null && parent != variableSetter) {
                try {
                    ExpInfo parentInfo = expInfoMap.get(parent);
                    if (parentInfo.dependees == null) {
                        parentInfo.dependees = new ExpressionSet();
                    }
                    parentInfo.dependees.add(variableSetter);
                } catch (NullPointerException e) {
                    ExpressionTool.validateTree(parent);
                    e.printStackTrace();
                    throw e;
                }
                parent = parent.getParentExpression();
            }
        }
    }


    private void loopLift(Expression exp) {
        ExpInfo info = expInfoMap.get(exp);
        if (!info.multiThreaded) {
            if (info.loopLevel > 0 && exp.getNetCost() > 0) {
                if (info.dependees == null && exp.isLiftable(streaming) && !mayReturnStreamedNodes(exp)) {
                    root = lift(exp, root);
                } else {
                    Expression child = exp;
                    ExpInfo expInfo = expInfoMap.get(exp);
                    Expression parent = exp.getParentExpression();
                    while (parent != null) {
                        if (expInfo.dependees != null && expInfo.dependees.contains(parent)) {
                            ExpInfo childInfo = expInfoMap.get(child);
                            if (expInfo.loopLevel != childInfo.loopLevel) {
                                Operand o = ExpressionTool.findOperand(parent, child);
                                assert o != null;
                                if (exp.isLiftable(streaming) && !(child instanceof PseudoExpression) && !o.getOperandRole().isConstrainedClass()) {
                                    Expression lifted = lift(exp, child);
                                    o.setChildExpression(lifted);
                                }
                            }
                            break;
                        }
                        child = parent;
                        parent = parent.getParentExpression();
                    }
                }
            }
            for (Operand o : exp.operands()) {
                if (!o.getOperandRole().isConstrainedClass()) {
                    loopLift(o.getChildExpression());
                }
            }
        }
    }

    private Expression lift(Expression child, Expression newAction) {

        changed = true;
        ExpInfo childInfo = expInfoMap.get(child);
        ExpInfo actionInfo = expInfoMap.get(newAction);

        final int hoist = childInfo.loopLevel - actionInfo.loopLevel;

        Expression oldParent = child.getParentExpression();
        Operand oldOperand = ExpressionTool.findOperand(oldParent, child);
        assert oldOperand != null;

        LetExpression let = new LetExpression();
        let.setVariableQName(new StructuredQName("vv", NamespaceUri.SAXON_GENERATED_VARIABLE, "v" + sequence++));
        SequenceType type = SequenceType.makeSequenceType(child.getItemType(), child.getCardinality());
        let.setRequiredType(type);
        ExpressionTool.copyLocationInfo(child, let);
        let.setSequence(child);
        let.setNeedsLazyEvaluation(true);
//        let.setEvaluator(Cardinality.allowsMany(child.getCardinality())
//                                 ? Evaluator.MemoClosureEvaluator.INSTANCE
//                                 : Evaluator.SingletonClosure.INSTANCE);
        let.setAction(newAction);
        let.adoptChildExpression(newAction);
//        if (indexed) {
//            let.setIndexedVariable();
//        }


        ExpInfo letInfo = new ExpInfo();
        letInfo.expression = let;
        letInfo.dependees = childInfo.dependees;
        if (childInfo.dependees != null & actionInfo.dependees != null) {
            letInfo.dependees.addAll(actionInfo.dependees);
        }
        letInfo.loopLevel = actionInfo.loopLevel;
        expInfoMap.put(let, letInfo);

        try {
            ExpressionTool.processExpressionTree(child, null, (expression, result) -> {
                ExpInfo info = expInfoMap.get(expression);
                info.loopLevel -= hoist;
                return false;
            });
        } catch (XPathException e) {
            e.printStackTrace();
        }

        LocalVariableReference var = new LocalVariableReference(let);
        int properties = child.getSpecialProperties() & StaticProperty.NOT_UNTYPED_ATOMIC;
        var.setStaticType(type, null, properties);
        var.setInLoop(true);
        let.addReference(var, true);
        ExpressionTool.copyLocationInfo(child, var);
        oldOperand.setChildExpression(var);

        if (tracing) {
            Logger err = config.getLogger();
            err.info("OPT : At line " + child.getLocation().getLineNumber() + " of " + child.getLocation().getSystemId());
            err.info("OPT : Lifted (" + child.toShortString() + ") above (" + newAction.toShortString() + ") on line " + newAction.getLocation().getLineNumber());
            err.info("OPT : Expression after rewrite: " + let);
        }
        return let;
    }


}

