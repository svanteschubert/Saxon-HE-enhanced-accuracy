////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.FocusIterator;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.PrependSequenceIterator;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.IntegerRange;

/**
 * Handler for xsl:for-each elements in a stylesheet. The same class handles the "!" operator in XPath 3.0,
 * which has identical semantics to xsl:for-each, and it is used to support the "/" operator in cases where it
 * is known that the rhs delivers atomic values.
 */

public class ForEach extends Instruction implements ContextSwitchingExpression {

    protected boolean containsTailCall;
    protected Operand selectOp;
    protected Operand actionOp;
    protected Operand separatorOp;
    protected Operand threadsOp;
    protected boolean _isInstruction;

    /**
     * Create an xsl:for-each instruction
     *
     * @param select the select expression
     * @param action the body of the xsl:for-each loop
     */

    public ForEach(Expression select, Expression action) {
        this(select, action, false, null);
    }

    /**
     * Create an xsl:for-each instruction
     *
     * @param select           the select expression
     * @param action           the body of the xsl:for-each loop
     * @param containsTailCall true if the body of the loop contains a tail call on the containing function
     * @param threads          if &gt;1 causes multithreaded execution (Saxon-EE only)
     */

    public ForEach(Expression select, Expression action, boolean containsTailCall, Expression threads) {
        selectOp = new Operand(this, select, OperandRole.FOCUS_CONTROLLING_SELECT);
        actionOp = new Operand(this, action, OperandRole.FOCUS_CONTROLLED_ACTION);
        if (threads != null) {
            threadsOp = new Operand(this, threads, OperandRole.SINGLE_ATOMIC);
        }
        this.containsTailCall = containsTailCall;
    }

    /**
     * Set the separator expression (Saxon extension)
     *
     * @param separator the separator expression
     */

    public void setSeparatorExpression(Expression separator) {
        separatorOp = new Operand(this, separator, OperandRole.SINGLE_ATOMIC);
    }

    public Expression getSeparatorExpression() {
        return separatorOp == null ? null : separatorOp.getChildExpression();
    }

    /**
     * Say whether this ForEach expression originates as an XSLT instruction
     *
     * @param inst true if this is an xsl:for-each instruction; false if it is the XPath "!" operator
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
     * Set whether this expression contains a call (on call-template or apply-templates) that is
     * treated as a tail call because the select expression is known to be a singleton
     *
     * @param tc true if the action expression is to be treated as a tail call
     */

    public void setContainsTailCall(boolean tc) {
        containsTailCall = tc;
    }

    /**
     * Ask whether this expression contains a call (on call-template or apply-templates) that is
     * treated as a tail call because the select expression is known to be a singleton
     *
     * @return true if the action expression is to be treated as a tail call
     */

    public boolean isContainsTailCall() {
        return containsTailCall;
    }


    /**
     * Get the select expression
     *
     * @return the select expression. Note this will have been wrapped in a sort expression
     *         if sorting was requested.
     */

    public Expression getSelect() {
        return selectOp.getChildExpression();
    }

    /**
     * Set the select expression
     * @param select the select expression of the for-each
     */

    public void setSelect(Expression select) {
        selectOp.setChildExpression(select);
    }

    /**
     * Get the action expression (in XSLT, the body of the xsl:for-each instruction
     * @return the action expression
     */

    public Expression getAction() {
        return actionOp.getChildExpression();
    }

    /**
     * Set the action expression (in XSLT, the body of the xsl:for-each instruction)
     * @param action the action expression
     */

    public void setAction(Expression action) {
        actionOp.setChildExpression(action);
    }

    /**
     * Get the expression used to determine how many threads to use when multi-threading
     * @return the saxon:threads expression if present, or null otherwise
     */

    public Expression getThreads() {
        return threadsOp == null ? null : threadsOp.getChildExpression();
    }

    /**
     * Set the expression used to determine how many threads to use when multi-threading
     * @param threads the saxon:threads expression if present, or null otherwise
     */

    public void setThreads(Expression threads) {
        if (threads != null) {
            if (threadsOp == null) {
                threadsOp = new Operand(this, threads, OperandRole.SINGLE_ATOMIC);
            } else {
                threadsOp.setChildExpression(threads);
            }
        }
    }

    /**
     * Get the operands of this expression
     * @return the operands
     */

    @Override
    public Iterable<Operand> operands() {
        return operandSparseList(selectOp, actionOp, separatorOp, threadsOp);
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     *
     * @return the code for name xsl:for-each
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_FOR_EACH;
    }

    /**
     * Get the select expression
     *
     * @return the select expression. Note this will have been wrapped in a sort expression
     *         if sorting was requested.
     */

    @Override
    public Expression getSelectExpression() {
        return getSelect();
    }

    /**
     * Set the select expression
     *
     * @param select the select expression
     */

    public void setSelectExpression(Expression select) {
        this.setSelect(select);
    }

    /**
     * Set the action expression
     *
     * @param action the select expression
     */

    public void setActionExpression(Expression action) {
        this.setAction(action);
    }

    /**
     * Get the subexpression that is evaluated in the new context
     *
     * @return the subexpression evaluated in the context set by the controlling expression
     */
    @Override
    public Expression getActionExpression() {
        return getAction();
    }

    /**
     * Get the number of threads requested
     *
     * @return the value of the saxon:threads attribute
     */

    public Expression getNumberOfThreadsExpression() {
        return getThreads();
    }

    /**
     * Determine the data type of the items returned by this expression
     *
     * @return the data type
     */

    /*@NotNull*/
    @Override
    public final ItemType getItemType() {
        return getAction().getItemType();
    }


    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     * @param contextItemType static type of the context item
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        if (isInstruction()) {
            return super.getStaticUType(contextItemType);
        } else {
            return getAction().getStaticUType(getSelect().getStaticUType(contextItemType));
        }
    }


    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns true if the "action" creates new nodes.
     * (Nodes created by the condition can't contribute to the result).
     */

    @Override
    public final boolean mayCreateNewNodes() {
        int props = getAction().getSpecialProperties();
        return (props & StaticProperty.NO_NODES_NEWLY_CREATED) == 0;
    }

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        selectOp.typeCheck(visitor, contextInfo);

        ItemType selectType = getSelect().getItemType();
        if (selectType == ErrorType.getInstance()) {
            return Literal.makeEmptySequence();
        }

        ContextItemStaticInfo cit = visitor.getConfiguration().makeContextItemStaticInfo(getSelect().getItemType(), false);
        cit.setContextSettingExpression(getSelect());
        actionOp.typeCheck(visitor, cit);

        if (!Cardinality.allowsMany(getSelect().getCardinality())) {
            actionOp.setOperandRole(actionOp.getOperandRole().modifyProperty(OperandRole.SINGLETON, true));
        }

        return this;
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        selectOp.optimize(visitor, contextInfo);

        ContextItemStaticInfo cit = visitor.getConfiguration().makeContextItemStaticInfo(getSelect().getItemType(), false);
        cit.setContextSettingExpression(getSelect());
        actionOp.optimize(visitor, cit);

        if (!visitor.isOptimizeForStreaming()) {
            // Don't eliminate a void for-each if streaming, because it can consume the stream: see test accumulator-015
            if (Literal.isEmptySequence(getSelect())) {
                return getSelect();
            }
            if (Literal.isEmptySequence(getAction())) {
                return getAction();
            }
        }

        if (getSelect().getCardinality() == StaticProperty.EXACTLY_ONE && getAction() instanceof AxisExpression) {
            return new SimpleStepExpression(getSelect(), getAction());
        }

        // Rewrite (1 to $N) ! (. + $M) as ($N to $N + $M)
        if (getSelect() instanceof RangeExpression && isSimpleArithmeticShift(getAction())) {
            ArithmeticExpression arith = (ArithmeticExpression) getAction();

            RangeExpression range = (RangeExpression) getSelect();
            return new RangeExpression(
                    new ArithmeticExpression(range.getStartExpression().copy(new RebindingMap()),
                                             arith.getOperator(),
                                             arith.getRhsExpression().copy(new RebindingMap())),
                    new ArithmeticExpression(range.getEndExpression().copy(new RebindingMap()),
                                             arith.getOperator(),
                                             arith.getRhsExpression().copy(new RebindingMap())))

                    .typeCheck(visitor, contextInfo)
                    .optimize(visitor, contextInfo);
        }


        // Rewrite (1 to 1000) ! (. + $N) as ($N to 1000 + $N)
        if (getSelect() instanceof Literal
                && ((Literal)getSelect()).getGroundedValue() instanceof IntegerRange
                &&  isSimpleArithmeticShift(getAction())
                && getSeparatorExpression() == null) {
            ArithmeticExpression arith = (ArithmeticExpression) getAction();
            IntegerRange range = (IntegerRange) ((Literal) getSelect()).getGroundedValue();
            Expression shift = arith.getRhsExpression();
            Expression newStart = new ArithmeticExpression(
                    Literal.makeLiteral(new Int64Value(range.start), this),
                    arith.getOperator(),
                    shift.copy(new RebindingMap()));
            Expression newEnd = new ArithmeticExpression(
                    Literal.makeLiteral(new Int64Value(range.end), this),
                    arith.getOperator(),
                    shift.copy(new RebindingMap()));

            return new RangeExpression(newStart, newEnd)
                    .typeCheck(visitor, contextInfo)
                    .optimize(visitor, contextInfo);

        }


        if (threadsOp != null && !Literal.isEmptySequence(getThreads()) && !containsTailCall) {
            return visitor.obtainOptimizer().generateMultithreadedInstruction(this);
        }
        return this;
    }


    /**
     * Test whether the supplied expression takes the form (. + X) or (. - X) where X is a literal
     * or variable reference. (We don't allow anything more complex because it would involve
     * introducing a new local variable).
     * @param exp the expression to be tested
     * @return true if it takes the required form
     */
    private boolean isSimpleArithmeticShift(Expression exp) {
        if (!(exp instanceof ArithmeticExpression)) {
            return false;
        }
        ArithmeticExpression arith = (ArithmeticExpression) exp;
        if (!(arith.getLhsExpression() instanceof ContextItemExpression)) {
            return false;
        }
        if (!(arith.getOperator() == Token.PLUS || arith.getOperator() == Token.MINUS)) {
            return false;
        }
        if (!(arith.getRhsExpression() instanceof Literal || arith.getRhsExpression() instanceof VariableReference)) {
            return false;
        }
        return true;
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
        setSelect(getSelect().unordered(retainAllNodes, forStreaming));
        setAction(getAction().unordered(retainAllNodes, forStreaming));
        return this;
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
     * @param pathMapNodeSet the set of nodes in the path map that are affected
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     *         expression is the first operand of a path expression or filter expression. For an expression that does
     *         navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     *         expressions, it is the same as the input pathMapNode.
     */

    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        PathMap.PathMapNodeSet target = getSelect().addToPathMap(pathMap, pathMapNodeSet);
        return getAction().addToPathMap(pathMap, target);
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings variables that need to be re-bound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        ForEach f2 = new ForEach(getSelect().copy(rebindings), getAction().copy(rebindings), containsTailCall, getThreads());
        if (separatorOp != null) {
            f2.setSeparatorExpression(getSeparatorExpression().copy(rebindings));
        }
        ExpressionTool.copyLocationInfo(this, f2);
        f2.setInstruction(isInstruction());
        return f2;
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
        int p = super.computeSpecialProperties();
        if (getSelect().getCardinality() == StaticProperty.EXACTLY_ONE) {
            p |= getAction().getSpecialProperties();
        } else {
            p |= getAction().getSpecialProperties() & StaticProperty.ALL_NODES_UNTYPED;
        }
        return p;
    }

    @Override
    public boolean alwaysCreatesNewNodes() {
        return (getAction() instanceof Instruction) && ((Instruction)getAction()).alwaysCreatesNewNodes();
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
     * Check to ensure that this expression does not contain any inappropriate updating subexpressions.
     * This check is overridden for those expressions that permit updating subexpressions.
     *
     * @throws XPathException if the expression has a non-permitted updating subexpression
     */

    @Override
    public void checkForUpdatingSubexpressions() throws XPathException {
        if (getSelect().isUpdatingExpression()) {
            throw new XPathException(
                    "Updating expression appears in a context where it is not permitted", "XUST0001")
                    .withLocation(getSelect().getLocation());
        }
    }


    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides both iterate() and
     * process() methods natively.
     */

    @Override
    public int getImplementationMethod() {
        return ITERATE_METHOD | PROCESS_METHOD | Expression.WATCH_METHOD | Expression.ITEM_FEED_METHOD;
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
        getAction().checkPermittedContents(parentType, false);
    }

//    @Override
//    public TailCall processLeavingTail(Outputter output, XPathContext context) throws XPathException {
//        Controller controller = context.getController();
//        assert controller != null;
//
//        XPathContextMajor c2 = context.newContext();
//        c2.setOrigin(this);
//        FocusIterator iter = c2.trackFocus(getSelect().iterate(context));
//        c2.setCurrentTemplateRule(null);
//
//        Expression action = getAction();
//        if (containsTailCall) {
//            if (controller.isTracing()) {
//                TraceListener listener = controller.getTraceListener();
//                assert listener != null;
//                Item item = iter.next();
//                if (item == null) {
//                    return null;
//                }
//                listener.startCurrentItem(item);
//                TailCall tc = ((TailCallReturner) action).processLeavingTail(output, c2);
//                listener.endCurrentItem(item);
//                return tc;
//            } else {
//                Item item = iter.next();
//                if (item == null) {
//                    return null;
//                }
//                return ((TailCallReturner) action).processLeavingTail(output, c2);
//            }
//        } else {
//            PipelineConfiguration pipe = output.getPipelineConfiguration();
//            pipe.setXPathContext(c2);
//            NodeInfo separator = null;
//            if (separatorOp != null) {
//                separator = makeSeparator(context);
//            }
//            if (controller.isTracing() || separator != null) {
//                TraceListener listener = controller.getTraceListener();
//                boolean first = true;
//                for (Item item; (item = iter.next()) != null; ) {
//                    if (controller.isTracing()) {
//                        assert listener != null;
//                        listener.startCurrentItem(item);
//                    }
//                    if (separator != null) {
//                        if (first) {
//                            first = false;
//                        } else {
//                            output.append(separator);
//                        }
//                    }
//                    action.process(output, c2);
//                    if (controller.isTracing()) {
//                        listener.endCurrentItem(item);
//                    }
//                }
//            } else {
//                try {
//                    SequenceTool.supply(iter, (ItemConsumer<? super Item>) item -> action.process(output, c2));
//                } catch (UncheckedXPathException err) {
//                    throw err.getXPathException();
//                }
//            }
//            pipe.setXPathContext(context);
//        }
//        return null;
//    }

    protected NodeInfo makeSeparator(XPathContext context) throws XPathException {
        NodeInfo separator;
        UnicodeString sepValue = separatorOp.getChildExpression().evaluateAsString(context);
        Orphan orphan = new Orphan(context.getConfiguration());
        orphan.setNodeKind(Type.TEXT);
        orphan.setStringValue(sepValue);
        separator = orphan;
        return separator;
    }

    /**
     * Return an Iterator to iterate over the values of the sequence.
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
        XPathContextMinor c2 = context.newMinorContext();
        c2.trackFocus(getSelect().iterate(context));
        if (separatorOp == null) {
            return new ContextMappingIterator(c3 -> getAction().iterate(c3), c2);
        } else {
            NodeInfo separator = makeSeparator(context);
            ContextMappingFunction mapper = cxt -> {
               if (cxt.getCurrentIterator().position() == 1) {
                   return getAction().iterate(cxt);
               } else {
                   return new PrependSequenceIterator(separator, getAction().iterate(cxt));
               }
            };
            return new ContextMappingIterator(mapper, c2);
        }
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("forEach", this);
        if (containsTailCall) {
            out.emitAttribute("flags", "t");
        }
        getSelect().export(out);
        getAction().export(out);
        if (separatorOp != null) {
            out.setChildRole("separator");
            separatorOp.getChildExpression().export(out);
        }
        explainThreads(out);
        out.endElement();
    }

    protected void explainThreads(ExpressionPresenter out) throws XPathException {
        // no action in this class: implemented in subclass
    }

    /**
     * <p>The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form.</p>
     * <p>For subclasses of Expression that represent XPath expressions, the result should always be a string that
     * parses as an XPath 3.0 expression</p>
     *
     * @return a representation of the expression as a string
     */
    @Override
    public String toString() {
        return ExpressionTool.parenthesize(getSelect()) + " ! " + ExpressionTool.parenthesize(getAction());
    }

    @Override
    public String toShortString() {
        return getSelect().toShortString() + "!" + getAction().toShortString();
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
        return "forEach";
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "ForEach";
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new ForEachElaborator();
    }

    public static class ForEachElaborator extends PullElaborator {

        private NodeInfo makeSeparator(UnicodeStringEvaluator evaluator, XPathContext context) throws XPathException {
            UnicodeString sepValue = evaluator.eval(context);
            Orphan orphan = new Orphan(context.getConfiguration());
            orphan.setNodeKind(Type.TEXT);
            orphan.setStringValue(sepValue);
            return orphan;
        }

        @Override
        public PullEvaluator elaborateForPull() {
            ForEach forEach = (ForEach) getExpression();
            PullEvaluator select = forEach.getSelectExpression().makeElaborator().elaborateForPull();
            PullEvaluator action = forEach.getActionExpression().makeElaborator().elaborateForPull();
            if (forEach.getSeparatorExpression() == null) {
                ContextMappingFunction mapper = cxt -> action.iterate(cxt);
                return context -> {
                    XPathContextMinor c2 = context.newMinorContext();
                    c2.trackFocus(select.iterate(context));
                    return new ContextMappingIterator(mapper, c2);
                };
            } else {
                UnicodeStringEvaluator sepEval = forEach.getSeparatorExpression().makeElaborator().elaborateForUnicodeString(true);
                return context -> {
                    NodeInfo separator = makeSeparator(sepEval, context);
                    ContextMappingFunction mapper = cxt -> {
                        if (cxt.getCurrentIterator().position() == 1) {
                            return action.iterate(cxt);
                        } else {
                            return new PrependSequenceIterator(separator, action.iterate(cxt));
                        }
                    };
                    XPathContextMinor c2 = context.newMinorContext();
                    c2.trackFocus(select.iterate(context));
                    return new ContextMappingIterator(mapper, c2);
                };

            }
        }

        @Override
        public PushEvaluator elaborateForPush() {
            ForEach forEach = (ForEach)getExpression();
            PullEvaluator select = forEach.getSelectExpression().makeElaborator().elaborateForPull();
            PushEvaluator action = forEach.getActionExpression().makeElaborator().elaborateForPush();
            if (forEach.getSeparatorExpression() == null) {
                if (forEach.containsTailCall) {
                    // This path is used when a for-each has a singleton select and contains a call-template
                    // or apply-templates that is classified as a tail call. The iterator deliberately doesn't
                    // advance beyond the first (and only) item. See bug 5845.
                    return (out, context) -> {
                        XPathContextMinor c2 = context.newMinorContext();
                        FocusIterator iter = c2.trackFocus(select.iterate(context));
                        TailCall tc = null;
                        if (iter.next() != null) {
                            tc = action.processLeavingTail(out, c2);
                        }
                        return tc;
                    };
                } else {
                    return (out, context) -> {
                        XPathContextMinor c2 = context.newMinorContext();
                        FocusIterator iter = c2.trackFocus(select.iterate(context));
                        TailCall tc = null;
                        while (iter.next() != null) {
                            dispatchTailCall(tc);
                            tc = action.processLeavingTail(out, c2);
                        }
                        return tc;
                    };
                }
            } else {
                UnicodeStringEvaluator sepEval = forEach.getSeparatorExpression().makeElaborator().elaborateForUnicodeString(true);
                return (out, context) -> {
                    NodeInfo separator = makeSeparator(sepEval, context);
                    XPathContextMinor c2 = context.newMinorContext();
                    FocusIterator iter = c2.trackFocus(select.iterate(context));
                    TailCall tc = null;
                    if (iter.next() != null) {
                        dispatchTailCall(tc);
                        tc = action.processLeavingTail(out, c2);
                    }
                    while (iter.next() != null) {
                        out.append(separator);
                        dispatchTailCall(tc);
                        tc = action.processLeavingTail(out, c2);
                    }
                    return tc;
                };
            }

        }

        @Override
        public UpdateEvaluator elaborateForUpdate() {
            ForEach forEach = (ForEach) getExpression();
            PullEvaluator select = forEach.getSelectExpression().makeElaborator().elaborateForPull();
            UpdateEvaluator action = forEach.getActionExpression().makeElaborator().elaborateForUpdate();
            return (context, pul) -> {
                XPathContextMinor c2 = context.newMinorContext();
                c2.trackFocus(select.iterate(context));
                SequenceIterator iter = c2.getCurrentIterator();
                while (iter.next() != null) {
                    action.registerUpdates(c2, pul);
                }
            };
        }
    }
}

