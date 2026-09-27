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
import net.sf.saxon.ma.zeno.ZenoSequence;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.ListIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.type.gnode.NodeKindType;
import net.sf.saxon.value.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;


/**
 * An expression that delivers the concatenation of the results of its subexpressions. This may
 * represent an XSLT sequence constructor, or an XPath/XQuery expression of the form (a,b,c,d).
 */

public class Block extends Instruction {

    // TODO: allow the last expression in a Block to be a tail-call of a function, at least in push mode

    private final Operand[] operanda;
    private boolean allNodesUntyped;

    /**
     * Create a block, supplying its child expressions
     * @param children the child expressions in order
     */

    public Block(Expression[] children) {
        operanda = new Operand[children.length];
        for (int i=0; i<children.length; i++) {
            operanda[i] = new Operand(this, children[i], OperandRole.SAME_FOCUS_ACTION);
        }
        for (Expression e : children) {
            adoptChildExpression(e);
        }
    }

    @Override
    public boolean isInstruction() {
        return false;
    }

    /**
     * Get the n'th child expression
     * @param n the position of the child expression required (zero-based)
     * @return the child expression at that position
     */
    
    private Expression child(int n) {
        return operanda[n].getChildExpression();
    }

    /**
     * Set the n'th child expression
     * @param n the position of the child expression to be modified (zero-based)
     * @param child the child expression at that position
     */

    private void setChild(int n, Expression child) {
        operanda[n].setChildExpression(child);
    }

    /**
     * Get the number of children
     * @return the number of child subexpressions
     */

    public int size() {
        return operanda.length;
    }

    @Override
    public Iterable<Operand> operands() {
        return Arrays.asList(operanda);
    }

    /**
     * Ask whether this expression is, or contains, the binding of a given variable
     *
     * @param binding the variable binding
     * @return true if this expression is the variable binding (for example a ForExpression
     * or LetExpression) or if it is a FLWOR expression that binds the variable in one of its
     * clauses.
     */
    @Override
    public boolean hasVariableBinding(Binding binding) {
        if (binding instanceof LocalParam) {
            for (Operand o : operanda) {
                if (o.getChildExpression() == binding) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Static factory method to create a block. If one of the arguments is already a block,
     * the contents will be merged into a new composite block
     *
     * @param e1 the first subexpression (child) of the block
     * @param e2 the second subexpression (child) of the block
     * @return a Block containing the two subexpressions, and if either of them is a block, it will
     *         have been collapsed to create a flattened sequence
     */

    public static Expression makeBlock(/*@Nullable*/ Expression e1, Expression e2) {
        if (e1 == null || Literal.isEmptySequence(e1)) {
            return e2;
        }
        if (e2 == null || Literal.isEmptySequence(e2)) {
            return e1;
        }
        if (e1 instanceof Block || e2 instanceof Block) {
            List<Expression> list = new ArrayList<>(10);
            if (e1 instanceof Block) {
                for (Operand o : e1.operands()) {
                    list.add(o.getChildExpression());
                }
            } else {
                list.add(e1);
            }
            if (e2 instanceof Block) {
                for (Operand o : e2.operands()) {
                    list.add(o.getChildExpression());
                }
            } else {
                list.add(e2);
            }

            Expression[] exps = new Expression[list.size()];
            exps = list.toArray(exps);
            return new Block(exps);

        } else {
            Expression[] exps = {e1, e2};
            return new Block(exps);

        }
    }

    /**
     * Static factory method to create a block from a list of expressions
     *
     * @param list      the list of expressions making up this block. The members of the List must
     *                  be instances of Expression. The list is effectively copied; subsequent changes
     *                  to the contents of the list have no effect.
     * @return a Block containing the two subexpressions, and if either of them is a block, it will
     *         have been collapsed to create a flattened sequence
     */

    public static Expression makeBlock(List<Expression> list) {
        if (list.isEmpty()) {
            return Literal.makeEmptySequence();
        } else if (list.size() == 1) {
            return list.get(0);
        } else {
            Expression[] exps = new Expression[list.size()];
            exps = list.toArray(exps);
            return new Block(exps);
        }
    }

    @Override
    public String getExpressionName() {
        return "sequence";
    }

    /**
     * Get the children of this instruction
     *
     * @return the children of this instruction, as an array of Operand objects. May return
     *         a zero-length array if there are no children.
     */

    public Operand[] getOperanda() {
        return operanda;
    }


    @Override
    protected int computeSpecialProperties() {
        if (size() == 0) {
            // An empty sequence has all special properties except "has side effects".
            return StaticProperty.SPECIAL_PROPERTY_MASK & ~StaticProperty.HAS_SIDE_EFFECTS;
        }
        int p = super.computeSpecialProperties();
        if (allNodesUntyped) {
            p |= StaticProperty.ALL_NODES_UNTYPED;
        }
        // if all the expressions are axis expressions, we have a same-document node-set
        boolean allAxisExpressions = true;
        boolean allChildAxis = true;
        boolean allSubtreeAxis = true;
        for (Operand o : operands()) {
            Expression childExpr = o.getChildExpression();
            if (!(childExpr instanceof AxisExpression)) {
                allAxisExpressions = false;
                allChildAxis = false;
                allSubtreeAxis = false;
                break;
            }
            int axis = ((AxisExpression) childExpr).getAxis();
            if (axis != AxisInfo.CHILD) {
                allChildAxis = false;
            }
            if (!AxisInfo.isSubtreeAxis[axis]) {
                allSubtreeAxis = false;
            }
        }
        if (allAxisExpressions) {
            p |= StaticProperty.CONTEXT_DOCUMENT_NODESET |
                    StaticProperty.SINGLE_DOCUMENT_NODESET |
                    StaticProperty.NO_NODES_NEWLY_CREATED;
            // if they all use the child axis, then we have a peer node-set
            if (allChildAxis) {
                p |= StaticProperty.PEER_NODESET;
            }
            if (allSubtreeAxis) {
                p |= StaticProperty.SUBTREE_NODESET;
            }
            // special case: selecting attributes then children, node-set is sorted
            if (size() == 2 &&
                    ((AxisExpression) child(0)).getAxis() == AxisInfo.ATTRIBUTE &&
                    ((AxisExpression) child(1)).getAxis() == AxisInfo.CHILD) {
                p |= StaticProperty.ORDERED_NODESET;
            }
        }
        return p;
    }

    @Override
    public boolean implementsStaticTypeCheck() {
        return true;
    }

    /**
     * Static type checking for let expressions is delegated to the child expressions,
     * to allow further delegation to the branches of a conditional
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
        if (backwardsCompatible && !Cardinality.allowsMany(req.getCardinality())) {
            Expression first = FirstItemExpression.makeFirstItemExpression(this);
            return tc.staticTypeCheck(first, req, roleSupplier, visitor);
        }
        Expression[] checked = new Expression[operanda.length];
        SequenceType subReq = req;
        if (req.getCardinality() != StaticProperty.ALLOWS_ZERO_OR_MORE && req.getCardinality() != StaticProperty.ALLOWS_ZERO) {
            subReq = SequenceType.zeroOrMore(req.getPrimaryType());
        }
        for (int i=0; i<operanda.length; i++) {
            checked[i] = tc.staticTypeCheck(operanda[i].getChildExpression(), subReq, roleSupplier, visitor);
        }
        Block b2 = new Block(checked);
        ExpressionTool.copyLocationInfo(this, b2);
        b2.allNodesUntyped = allNodesUntyped;

        int reqCard = req.getCardinality();
        int suppliedCard = b2.getCardinality();
        if (!Cardinality.subsumes(req.getCardinality(), suppliedCard)) {
            if ((reqCard & suppliedCard) == 0) {
                RoleDiagnostic role = roleSupplier.get();
                throw new XPathException(
                        "The required cardinality of the " + role.getMessage() +
                                " is " + Cardinality.describe(reqCard) +
                                ", but the supplied cardinality is " +
                                Cardinality.describe(suppliedCard), role.getErrorCode(), getLocation())
                        .asTypeError()
                        .withFailingExpression(this);
            } else {
                return CardinalityChecker.makeCardinalityChecker(b2, reqCard, roleSupplier);
            }
        }
        return b2;
    }


    /**
     * Determine whether the block includes any instructions that might return nodes with a type annotation
     * @param insn the instruction (for example this block)
     * @param th the type hierarchy cache
     * @return true if any expression in the block can return type-annotated nodes
     */

    public static boolean neverReturnsTypedNodes(Instruction insn, TypeHierarchy th) {
        for (Operand o : insn.operands()) {
            Expression exp = o.getChildExpression();
            if (!exp.hasSpecialProperty(StaticProperty.ALL_NODES_UNTYPED)) {
                ItemType it = exp.getItemType();
                if (th.relationship(it, NodeKindType.ELEMENT) != Affinity.DISJOINT ||
                        th.relationship(it, NodeKindType.ATTRIBUTE) != Affinity.DISJOINT) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Merge any adjacent instructions that create literal text nodes
     *
     * @return the expression after merging literal text instructions
     */

    public Expression mergeAdjacentTextInstructions() {
        boolean[] isLiteralText = new boolean[size()];
        boolean hasAdjacentTextNodes = false;
        for (int i = 0; i < size(); i++) {
            isLiteralText[i] = child(i) instanceof ValueOf vOf &&
                    vOf.getSelect() instanceof StringLiteral &&
                    !vOf.isDisableOutputEscaping() &&
                    vOf.getCdataExpression() == null;

            if (i > 0 && isLiteralText[i] && isLiteralText[i - 1]) {
                hasAdjacentTextNodes = true;
            }
        }
        if (hasAdjacentTextNodes) {
            List<Expression> content = new ArrayList<>(size());
            UnicodeString pendingText = null;
            for (int i = 0; i < size(); i++) {
                if (isLiteralText[i]) {
                    pendingText = (pendingText == null
                                           ? EmptyUnicodeString.getInstance()
                                           : pendingText).concat(
                                                ((StringLiteral) ((ValueOf) child(i)).getSelect()).getUnicodeString());
                } else {
                    if (pendingText != null) {
                        ValueOf inst = new ValueOf(new StringLiteral(pendingText), false, false);
                        content.add(inst);
                        pendingText = null;
                    }
                    content.add(child(i));
                }
            }
            if (pendingText != null) {
                ValueOf inst = new ValueOf(new StringLiteral(pendingText), false, false);
                content.add(inst);
            }
            return makeBlock(content);
        } else {
            return this;
        }
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings Variables that need to be re-bound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        Expression[] c2 = new Expression[size()];
        for (int c = 0; c < size(); c++) {
            c2[c] = child(c).copy(rebindings);
        }
        Block b2 = new Block(c2);
        for (int c = 0; c < size(); c++) {
            b2.adoptChildExpression(c2[c]);
        }
        b2.allNodesUntyped = allNodesUntyped;
        ExpressionTool.copyLocationInfo(this, b2);
        return b2;
    }

    /**
     * Determine the data type of the items returned by this expression
     *
     * @return the data type
     */

    /*@NotNull*/
    @Override
    public final ItemType getItemType() {
        if (size() == 0) {
            return ErrorType.getInstance();
        }
        ItemType t1 = null;
        TypeHierarchy th = getConfiguration().getTypeHierarchy();
        for (int i = 0; i < size(); i++) {
            Expression childExpr = child(i);
            if (!(childExpr instanceof MessageInstr)) {
                ItemType t = childExpr.getItemType();
                t1 = t1 == null ? t : Type.getCommonSuperType(t1, t, th);
                if (t1 instanceof AnyItemType) {
                    return t1;  // no point going any further
                }
            }
        }
        return t1 == null ? ErrorType.getInstance() : t1;
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
            if (size() == 0) {
                return UType.VOID;
            }
            UType t1 = child(0).getStaticUType(contextItemType);
            for (int i = 1; i < size(); i++) {
                t1 = t1.union(child(i).getStaticUType(contextItemType));
                if (t1 == UType.ANY) {
                    return t1;  // no point going any further
                }
            }
            return t1;
        }
    }

    /**
     * Determine the cardinality of the expression
     */

    @Override
    protected int computeCardinality() {
        if (size() == 0) {
            return StaticProperty.EMPTY;
        }
        int c1 = child(0).getCardinality();
        for (int i = 1; i < size(); i++) {
            c1 = Cardinality.sum(c1, child(i).getCardinality());
            if (c1 == StaticProperty.ALLOWS_MANY) {
                break;
            }
        }
        return c1;
    }

    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns true if any child instruction
     * returns true.
     */

    @Override
    public final boolean mayCreateNewNodes() {
        return someOperandCreatesNewNodes();
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
        if (size() < 2) {
            return;
        }
        boolean updating = false;
        boolean nonUpdating = false;
        for (Operand o : operands()) {
            Expression child = o.getChildExpression();
            if (ExpressionTool.isNotAllowedInUpdatingContext(child)) {
                if (updating) {
                    throw new XPathException(
                            "If any subexpression is updating, then all must be updating", "XUST0001")
                            .withLocation(child.getLocation());
                }
                nonUpdating = true;
            }
            if (child.isUpdatingExpression()) {
                if (nonUpdating) {
                    throw new XPathException(
                            "If any subexpression is updating, then all must be updating", "XUST0001")
                            .withLocation(child.getLocation());
                }
                updating = true;
            }
        }
    }

    /**
     * Determine whether this is a vacuous expression as defined in the XQuery update specification
     *
     * @return true if this expression is vacuous
     */

    @Override
    public boolean isVacuousExpression() {
        // true if all subexpressions are vacuous
        for (Operand o : operands()) {
            Expression child = o.getChildExpression();
            if (!child.isVacuousExpression()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression). The default implementation does nothing.
     *
     *
     *
     * @throws XPathException if an error is discovered during expression
     *                        rewriting
     */

    /*@NotNull*/
    @Override
    public Expression simplify() throws XPathException {
        boolean allAtomic = true;
        boolean nested = false;

        for (int c = 0; c < size(); c++) {
            setChild(c, child(c).simplify());
            if (!Literal.isAtomic(child(c))) {
                allAtomic = false;
            }
            if (child(c) instanceof Block) {
                nested = true;
            } else if (Literal.isEmptySequence(child(c))) {
                nested = true;
            }
        }
        if (size() == 1) {
            Expression e = getOperanda()[0].getChildExpression();
            e.setParentExpression(getParentExpression());
            return e;
        } else if (size() == 0) {
            Expression result = Literal.makeEmptySequence();
            ExpressionTool.copyLocationInfo(this, result);
            result.setParentExpression(getParentExpression());
            return result;
        } else if (nested) {
            List<Expression> list = new ArrayList<>(size() * 2);
            flatten(list);
            Expression[] children = new Expression[list.size()];
            for (int i = 0; i < list.size(); i++) {
                children[i] = list.get(i);
            }
            Block newBlock = new Block(children);
            ExpressionTool.copyLocationInfo(this, newBlock);
            return newBlock.simplify();
        } else if (allAtomic) {
            AtomicValue[] values = new AtomicValue[size()];
            for (int c = 0; c < size(); c++) {
                values[c] = (AtomicValue) ((Literal) child(c)).getGroundedValue();
            }
            @SuppressWarnings("Convert2Diamond")
            Expression result = Literal.makeLiteral(new SequenceExtent.Of<AtomicValue>(values), this);
            result.setParentExpression(getParentExpression());
            return result;
        } else {
            return this;
        }
    }

    /**
     * Simplify the contents of a Block by merging any nested blocks, merging adjacent
     * literals, and eliminating any empty sequences.
     *
     * @param targetList the new list of expressions comprising the contents of the block
     *                   after simplification
     */

    private void flatten(List<Expression> targetList)  {
        List<Item> currentLiteralList = null;
        for (Operand o : operands()) {
            Expression child = o.getChildExpression();
            if (Literal.isEmptySequence(child)) {
                // do nothing, omit it from the output
            } else if (child instanceof Block) {
                flushCurrentLiteralList(currentLiteralList, targetList);
                currentLiteralList = null;
                ((Block) child).flatten(targetList);
            } else if (child instanceof Literal && !(((Literal) child).getGroundedValue() instanceof IntegerRange)) {
                SequenceIterator iterator = ((Literal) child).getGroundedValue().iterate();
                if (currentLiteralList == null) {
                    currentLiteralList = new ArrayList<>(10);
                }
                for (Item item; (item = iterator.next()) != null; ) {
                    currentLiteralList.add(item);
                }
                // no-op
            } else {
                flushCurrentLiteralList(currentLiteralList, targetList);
                currentLiteralList = null;
                targetList.add(child);
            }
        }
        flushCurrentLiteralList(currentLiteralList, targetList);
    }

    private void flushCurrentLiteralList(List<Item> currentLiteralList, List<Expression> list) {
        if (currentLiteralList != null) {
            ListIterator.Of<Item> iter = new ListIterator.Of<>(currentLiteralList);
            Literal lit = Literal.makeLiteral(iter.materialize(), this);
            list.add(lit);
        }
    }

    /**
     * Determine whether the block is a candidate for evaluation using a "shared append expression"
     * where the result of the evaluation is a sequence implemented as a list of subsequences
     * @return true if the block is a candidate for "shared append" processing
     */

    public boolean isCandidateForSharedAppend() {
        for (Operand o : operands()) {
            Expression exp = o.getChildExpression();
            if (exp instanceof VariableReference || exp instanceof Literal) {
                return true;
            }
        }
        return false;
    }

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        typeCheckChildren(visitor, contextInfo);
        if (neverReturnsTypedNodes(this, visitor.getConfiguration().getTypeHierarchy())) {
            resetLocalStaticProperties();
            allNodesUntyped = true;
        }
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        optimizeChildren(visitor, contextInfo);
        boolean canSimplify = false;
        boolean prevLiteral = false;
        // Simplify the expression by collapsing nested blocks and merging adjacent literals
        for (Operand o : operands()) {
            Expression child = o.getChildExpression();
            if (child instanceof Block) {
                canSimplify = true;
                break;
            }
            if (child instanceof Literal && !(((Literal) child).getGroundedValue() instanceof IntegerRange)) {
                if (prevLiteral || Literal.isEmptySequence(child)) {
                    canSimplify = true;
                    break;
                }
                prevLiteral = true;
            } else {
                prevLiteral = false;
            }
        }
        if (canSimplify) {
            List<Expression> list = new ArrayList<>(size() * 2);
            flatten(list);
            Expression result = Block.makeBlock(list);
            result.setRetainedStaticContext(getRetainedStaticContext());
            return result;
        }
        if (size() == 0) {
            return Literal.makeEmptySequence();
        } else if (size() == 1) {
            return child(0);
        } else {
            return this;
        }
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
        for (Operand o : operands()) {
            Expression child = o.getChildExpression();
            child.checkPermittedContents(parentType, false);
        }
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("sequence", this);
        for (Operand o : operands()) {
            Expression child = o.getChildExpression();
            child.export(out);
        }
        out.endElement();
    }

    @Override
    public String toShortString() {
        return "(" + child(0).toShortString() + ", ...)";
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides both iterate() and
     * process() methods natively.
     */

    @Override
    public int getImplementationMethod() {
        return ITERATE_METHOD | PROCESS_METHOD;
    }

    /**
     * Iterate over the results of all the child expressions
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        if (size() == 0) {
            return EmptyIterator.INSTANCE;
        } else if (size() == 1) {
            return child(0).iterate(context);
        } else {
            return new BlockIterator(operanda, context);
        }
    }


    @Override
    public String getStreamerName() {
        return "Block";
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new BlockElaborator();
    }

    @FunctionalInterface
    public interface ChainAction {
        ZenoSequence perform(ZenoSequence in, XPathContext context) throws XPathException;
    }
    /**
     * Elaborator for a "Block", which is typically either an XPath sequence expression (a, b, c)
     * or an XSLT sequence constructor
     */
    public static class BlockElaborator extends PullElaborator {

        @Override
        public SequenceEvaluator lazily(boolean repeatable, boolean lazyEvaluationRequired) {
            final Block expr = (Block) getExpression();
            if (expr.isCandidateForSharedAppend()) {
                return new SharedAppendEvaluator(expr);
            } else {
                return super.lazily(repeatable, lazyEvaluationRequired);
            }
        }

        public PullEvaluator elaborateForPull() {
            final Block expr = (Block) getExpression();
            final Operand[] operanda = expr.getOperanda();
            final int size = operanda.length;
            final PullEvaluator[] actions = new PullEvaluator[size];
            for (int i = 0; i < size; i++) {
                actions[i] = operanda[i].getChildExpression().makeElaborator().elaborateForPull();
            }
            return context -> new BlockIterator(actions, context);
        }

        private static class BlockIterator extends AbstractBlockIterator {

            private final XPathContext context;

            private final PullEvaluator[] pullers;
            public BlockIterator(PullEvaluator[] pullers, XPathContext context) {
                this.pullers = pullers;
                this.context = context;
                init(pullers.length);
            }

            @Override
            public SequenceIterator getNthChildIterator(int n) throws XPathException {
                return pullers[n].iterate(context);
            }
        }

        @Override
        public PushEvaluator elaborateForPush() {
            final Block expr = (Block) getExpression();
            final Operand[] operanda = expr.getOperanda();
            final int size = operanda.length;
            final PushEvaluator[] actions = new PushEvaluator[size];
            for (int i = 0; i < size; i++) {
                actions[i] = operanda[i].getChildExpression().makeElaborator().elaborateForPush();
            }
            switch (size) {
                // Unroll loop for small sequence constructors
                case 2: {
                    PushEvaluator act0 = actions[0];
                    PushEvaluator act1 = actions[1];
                    return (out, context) -> {
                        TailCall tail = act0.processLeavingTail(out, context);
                        Expression.dispatchTailCall(tail);
                        return act1.processLeavingTail(out, context);
                    };
                }
                case 3: {
                    PushEvaluator act0 = actions[0];
                    PushEvaluator act1 = actions[1];
                    PushEvaluator act2 = actions[2];
                    return (out, context) -> {
                        TailCall tail = act0.processLeavingTail(out, context);
                        Expression.dispatchTailCall(tail);

                        tail = act1.processLeavingTail(out, context);
                        Expression.dispatchTailCall(tail);

                        return act2.processLeavingTail(out, context);
                    };
                }
                case 4: {
                    PushEvaluator act0 = actions[0];
                    PushEvaluator act1 = actions[1];
                    PushEvaluator act2 = actions[2];
                    PushEvaluator act3 = actions[3];
                    return (out, context) -> {
                        TailCall tail = act0.processLeavingTail(out, context);
                        Expression.dispatchTailCall(tail);

                        tail = act1.processLeavingTail(out, context);
                        Expression.dispatchTailCall(tail);

                        tail = act2.processLeavingTail(out, context);
                        Expression.dispatchTailCall(tail);

                        return act3.processLeavingTail(out, context);
                    };
                }
                default:
                    return (out, context) -> {
                        TailCall tail = null;
                        for (int i = 0; i < size; i++) {
                            while (tail != null) {
                                tail = tail.processLeavingTail();
                            }
                            tail = actions[i].processLeavingTail(out, context);
                        }
                        return tail;
                    };
            }
        }

        @Override
        public UpdateEvaluator elaborateForUpdate() {
            final Block expr = (Block) getExpression();
            final Operand[] operanda = expr.getOperanda();
            final int size = operanda.length;
            final UpdateEvaluator[] actions = new UpdateEvaluator[size];
            for (int i=0; i<size; i++) {
                actions[i] = expr.child(i).makeElaborator().elaborateForUpdate();
            }
            return (context, pul) -> {
                for (UpdateEvaluator action : actions) {
                     action.registerUpdates(context, pul);
                }
            };
        }
    }
}
