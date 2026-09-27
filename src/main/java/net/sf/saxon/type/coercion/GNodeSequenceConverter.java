////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.coercion;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.PullElaborator;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.ma.MapOrArray;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.jnode.AnyJNodeType;
import net.sf.saxon.ma.jnode.JNode;
import net.sf.saxon.ma.jnode.RootJNode;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.type.gnode.AnyGNodeType;
import net.sf.saxon.value.Cardinality;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;


/**
 * A GNodeSequenceConverter implements the implicit conversion of maps and arrays
 * to JNodes required in the 4.0 specification. Note that this is not part of the
 * general coercion rules; it happens only for the context setting expression
 * of an axis expression where the node-test is not confined to selecting XNodes.
 */

public final class GNodeSequenceConverter extends UnaryExpression {

    private final Supplier<RoleDiagnostic> roleSupplier;

    /**
     * Constructor
     *
     * @param sequence the expression whose value we are checking
     * @param roleSupplier information used in constructing an error message
     */

    public GNodeSequenceConverter(Expression sequence, Supplier<RoleDiagnostic> roleSupplier) {
        super(sequence);
        this.roleSupplier = roleSupplier;
    }

    @Override
    protected OperandRole getOperandRole() {
        return OperandRole.SAME_FOCUS_ACTION;
    }

    /**
     * Get the RoleLocator (used to construct error messages)
     *
     * @return the RoleLocator
     */

    public RoleDiagnostic getRoleLocator() {
        return roleSupplier.get();
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getOperand().typeCheck(visitor, contextInfo);
        Expression operand = getBaseExpression();

        if (operand instanceof Block block) {
            // Do the item-checking on each operand of the block separately (it might not be needed on all items)
            // This is particularly needed for streamability analysis of xsl:map
            List<Expression> checkedOperands = new ArrayList<>();
            for (Operand o : block.operands()) {
                GNodeSequenceConverter checkedOp = new GNodeSequenceConverter(o.getChildExpression(), roleSupplier);
                checkedOperands.add(checkedOp);
            }
            Block newBlock = new Block(checkedOperands.toArray(new Expression[0]));
            ExpressionTool.copyLocationInfo(this, newBlock);
            return newBlock.typeCheck(visitor, contextInfo);
        }
        // When typeCheck is called a second time, we might have more information...

        final TypeHierarchy th = getConfiguration().getTypeHierarchy();
        int card = operand.getCardinality();
        if (card == StaticProperty.EMPTY) {
            //value is always empty, so no item checking needed
            return operand;
        }
        ItemType supplied = operand.getItemType();
        
        Affinity relation = th.relationship(AnyGNodeType.getInstance(), supplied);
        if (relation == Affinity.SAME_TYPE || relation == Affinity.SUBSUMES) {
            return operand;
        }
        Affinity arrayRelationship = th.relationship(ArrayItemType.ANY_ARRAY_TYPE, supplied);
        Affinity mapRelationship = th.relationship(MapType.ANY_MAP_TYPE, supplied);

        if (relation == Affinity.DISJOINT && mapRelationship == Affinity.DISJOINT && arrayRelationship == Affinity.DISJOINT) {
            if (Cardinality.allowsZero(card)) {
                if (!(operand instanceof Literal)) {
                    RoleDiagnostic role = roleSupplier.get();
                    String message = role.composeErrorMessage(
                            AnyGNodeType.getInstance(), operand, th);
                    visitor.getStaticContext().issueWarning(
                            "The only value that can pass type-checking is an empty sequence. " +
                                                                    message, SaxonErrorCode.SXWN9026, getLocation());
                }
            } else {
                RoleDiagnostic role = roleSupplier.get();
                String message = role.composeErrorMessage(AnyGNodeType.getInstance(), operand, th);
                throw new XPathException(message)
                        .withErrorCode(role.getErrorCode())
                        .withLocation(this.getLocation())
                        .asTypeError();
            }
        }
        return this;
    }

    /**
     * Perform optimisation of an expression and its subexpressions.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor     an expression visitor
     * @param contextInfo the static type of "." at the point where this expression is invoked.
     *                    The parameter is set to null if it is known statically that the context item will be undefined.
     *                    If the type of the context item is not known statically, the argument is set to
     *                    {@link Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getOperand().optimize(visitor, contextInfo);
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        Affinity rel = th.relationship(AnyGNodeType.getInstance(), getBaseExpression().getItemType());
        if (rel == Affinity.SAME_TYPE || rel == Affinity.SUBSUMES) {
            return getBaseExpression();
        }
        return this;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides both iterate() and
     * process() methods natively.
     */

    @Override
    public int getImplementationMethod() {
        int m = ITERATE_METHOD | PROCESS_METHOD | ITEM_FEED_METHOD;
        if (!Cardinality.allowsMany(getCardinality())) {
            m |= EVALUATE_METHOD;
        }
        return m;
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "GNodeCoercer";
    }

    /**
     * Iterate over the sequence of values
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        return makeElaborator().elaborateForPull().iterate(context);
    }

    private SequenceIterator convertSequence(SequenceIterator baseIter, XPathContext context) {
        final Expression baseExpr = getBaseExpression();
        final TypeHierarchy th = context.getConfiguration().getTypeHierarchy();
        return new ItemMappingIterator(baseIter, it -> {
            if (it instanceof NodeInfo || it instanceof JNode) {
                return it;
            }
            if (it instanceof MapOrArray ma) {
                return new RootJNode(ma);
            }

            int version = baseExpr.getRetainedStaticContext().getPackageData().getHostLanguageVersion();
            RoleDiagnostic role = roleSupplier.get();
            String message = role.composeErrorMessage(AnyGNodeType.getInstance(), it, th);
            String errorCode = role.getErrorCode();
            XPathException te = new XPathException(message, errorCode)
                    .withFailingExpression(baseExpr)
                    .withLocation(baseExpr.getLocation())
                    .asTypeErrorIf(version >= 40 || !"XPDY0050".equals(errorCode));
            throw new UncheckedXPathException(te);

        });
    }


    /**
     * Evaluate as an Item.
     */

    @Override
    public Item evaluateItem(XPathContext context) throws XPathException {
        return makeElaborator().elaborateForItem().eval(context);
    }

    public Item convertItem(Item it, XPathContext context) throws XPathException {
        final TypeHierarchy th = context.getConfiguration().getTypeHierarchy();
        if (it == null) {
            return null;
        }
        if (it instanceof NodeInfo || it instanceof JNode) {
            return it;
        }
        if (it instanceof MapOrArray ma) {
            return new RootJNode(ma);
        }

        int version = getRetainedStaticContext().getPackageData().getHostLanguageVersion();
        RoleDiagnostic role = roleSupplier.get();
        String message = role.composeErrorMessage(AnyGNodeType.getInstance(), it, th);
        String errorCode = role.getErrorCode();
        throw new XPathException(message, errorCode)
                .withFailingExpression(this)
                .withLocation(this.getLocation())
                .asTypeError();
    }

    /**
     * Process the instruction, without returning any tail calls
     *
     * @param output the destination for the result
     * @param context The dynamic context, giving access to the current node,
     */

    @Override
    public void process(Outputter output, XPathContext context) throws XPathException {
        dispatchTailCall(makeElaborator().elaborateForPush().processLeavingTail(output, context));
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings variable bindings that need to be changed
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        GNodeSequenceConverter exp = new GNodeSequenceConverter(getBaseExpression().copy(rebindings), roleSupplier);
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
    }


    /**
     * Determine the data type of the items returned by the expression
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        ItemType operandType = getBaseExpression().getItemType();
        if (operandType instanceof MapType || operandType instanceof ArrayItemType) {
            return AnyJNodeType.getInstance();
        }
        TypeHierarchy th = getConfiguration().getTypeHierarchy();
        Affinity relationship = th.relationship(AnyGNodeType.getInstance(), operandType);
        return relationship == Affinity.SUBSUMES ? operandType : AnyGNodeType.getInstance();
    }

    /**
     * Convert this expression to an equivalent XSLT pattern
     *
     * @param config      the Saxon configuration
     * @param firstInPath true if an axis step in this expression is to be treated
     *                    as an initial step of the pattern
     * @return the equivalent pattern
     * @throws XPathException if conversion is not possible
     */
    @Override
    public Pattern toPattern(Configuration config, boolean firstInPath) throws XPathException {
        return getBaseExpression().toPattern(config, firstInPath);
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
        return UType.GNODE;
    }


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("coerceToGNode", this);
        out.emitAttribute("diag", roleSupplier.get().save());
        getBaseExpression().export(out);
        out.endElement();
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
        return "treatAs";
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     */

    public String toString() {
        return "toJNode(" + getBaseExpression() + ")";
    }

    @Override
    public String toShortString() {
        return getBaseExpression().toShortString();
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new ItemCheckerElaborator();
    }

    /**
     * Elaborator for a {@code treat as} expression, which is usually system-generated by
     * the type checking phase of the compiler
     */

    public static class ItemCheckerElaborator extends PullElaborator {

        @Override
        public PullEvaluator elaborateForPull() {
            GNodeSequenceConverter exp = (GNodeSequenceConverter) getExpression();
            Expression arg = exp.getBaseExpression();
            PullEvaluator argEval = arg.makeElaborator().elaborateForPull();

            return context -> exp.convertSequence(argEval.iterate(context), context);
        }

        @Override
        public ItemEvaluator elaborateForItem() {
            GNodeSequenceConverter expr = (GNodeSequenceConverter) getExpression();
            Expression arg = expr.getBaseExpression();
            ItemEvaluator argEval = arg.makeElaborator().elaborateForItem();

            return context -> expr.convertItem(argEval.eval(context), context);
        }


    }
}


