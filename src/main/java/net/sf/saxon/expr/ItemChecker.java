////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.TypeCheckingFilter;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.CombinedNodeTest;
import net.sf.saxon.pattern.DocumentNodeTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.IntegerValue;

import java.util.ArrayList;
import java.util.List;


/**
 * A ItemChecker implements the item type checking of "treat as": that is,
 * it returns the supplied sequence, checking that all its items are of the correct type
 */

public final class ItemChecker extends UnaryExpression {

    private ItemType requiredItemType;
    private RoleDiagnostic role;

    /**
     * Constructor
     *
     * @param sequence the expression whose value we are checking
     * @param itemType the required type of the items in the sequence
     * @param role     information used in constructing an error message
     */

    public ItemChecker(Expression sequence, ItemType itemType, RoleDiagnostic role) {
        super(sequence);
        requiredItemType = itemType;
        this.role = role;
        //adoptChildExpression(sequence);
    }

    /**
     * Get the required type
     *
     * @return the required type of the items in the sequence
     */

    public ItemType getRequiredType() {
        return requiredItemType;
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
        return role;
    }

    /**
     * Simplify an expression
     *
     */

    /*@NotNull*/
    @Override
    public Expression simplify() throws XPathException {
        Expression operand = getBaseExpression().simplify();
        if (requiredItemType instanceof AnyItemType) {
            return operand;
        }
        setBaseExpression(operand);
        return this;
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getOperand().typeCheck(visitor, contextInfo);
        Expression operand = getBaseExpression();

        if (operand instanceof Block) {
            // Do the item-checking on each operand of the block separately (it might not be needed on all items)
            // This is particularly needed for streamability analysis of xsl:map
            Block block = (Block)operand;
            List<Expression> checkedOperands = new ArrayList<>();
            for (Operand o : block.operands()) {
                ItemChecker checkedOp = new ItemChecker(o.getChildExpression(), requiredItemType, role);
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
        Affinity relation = th.relationship(requiredItemType, supplied);
        if (relation == Affinity.SAME_TYPE || relation == Affinity.SUBSUMES) {
            return operand;
        } else if (relation == Affinity.DISJOINT) {
            if (requiredItemType.equals(BuiltInAtomicType.STRING) && th.isSubType(supplied, BuiltInAtomicType.ANY_URI)) {
                // URI promotion will take care of this at run-time
                return operand;
            } else if (Cardinality.allowsZero(card)) {
                if (!(operand instanceof Literal)) {
                    String message = role.composeErrorMessage(
                            requiredItemType, operand, th);
                    visitor.getStaticContext().issueWarning(
                            "The only value that can pass type-checking is an empty sequence. " +
                                                                    message, getLocation());
                }
            } else {
                String message = role.composeErrorMessage(requiredItemType, operand, th);
                XPathException err = new XPathException(message);
                err.setErrorCode(role.getErrorCode());
                err.setLocation(this.getLocation());
                err.setIsTypeError(role.isTypeError());
                throw err;
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
        Affinity rel = th.relationship(requiredItemType, getBaseExpression().getItemType());
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
        return "ItemChecker";
    }

    /**
     * For an expression that returns an integer or a sequence of integers, get
     * a lower and upper bound on the values of the integers that may be returned, from
     * static analysis. The default implementation returns null, meaning "unknown" or
     * "not applicable". Other implementations return an array of two IntegerValue objects,
     * representing the lower and upper bounds respectively. The values
     * UNBOUNDED_LOWER and UNBOUNDED_UPPER are used by convention to indicate that
     * the value may be arbitrarily large. The values MAX_STRING_LENGTH and MAX_SEQUENCE_LENGTH
     * are used to indicate values limited by the size of a string or the size of a sequence.
     *
     * @return the lower and upper bounds of integer values in the result, or null to indicate
     *         unknown or not applicable.
     */
    /*@Nullable*/
    @Override
    public IntegerValue[] getIntegerBounds() {
        return getBaseExpression().getIntegerBounds();
    }

    /**
     * Iterate over the sequence of values
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        SequenceIterator base = getBaseExpression().iterate(context);
        return new ItemMappingIterator(base, getMappingFunction(context), true);
    }

    /**
     * Get the mapping function used to implement this item check. This mapping function is applied
     * to each item in the input sequence.
     *
     * @param context The dynamic context used to evaluate the mapping function
     * @return the mapping function. This will be an identity mapping: the output sequence is the same
     *         as the input sequence, unless the dynamic type checking reveals an error.
     */

    public ItemMappingFunction getMappingFunction(XPathContext context) {
        return new ItemTypeCheckingFunction(requiredItemType, role, getBaseExpression(), context.getConfiguration());
    }

    /**
     * Evaluate as an Item.
     */

    @Override
    public Item evaluateItem(XPathContext context) throws XPathException {
        final TypeHierarchy th = context.getConfiguration().getTypeHierarchy();
        Item item = getBaseExpression().evaluateItem(context);
        if (item == null) {
            return null;
        }
        if (requiredItemType.matches(item, th)) {
            return item;
        } else if (requiredItemType.getUType().subsumes(UType.STRING) && BuiltInAtomicType.ANY_URI.matches(item, th)) {
            return item;
        } else {
            String message = role.composeErrorMessage(requiredItemType, item, th);
            String errorCode = role.getErrorCode();
            if ("XPDY0050".equals(errorCode)) {
                // error in "treat as" assertion
                dynamicError(message, errorCode, context);
            } else {
                typeError(message, errorCode, context);
            }
            return null;
        }
    }

    /**
     * Process the instruction, without returning any tail calls
     *
     * @param output the destination for the result
     * @param context The dynamic context, giving access to the current node,
     */

    @Override
    public void process(Outputter output, XPathContext context) throws XPathException {
        Expression next = getBaseExpression();
        int card = StaticProperty.ALLOWS_ZERO_OR_MORE;
        if (next instanceof CardinalityChecker) {
            card = ((CardinalityChecker) next).getRequiredCardinality();
            next = ((CardinalityChecker) next).getBaseExpression();
        }
        if ((next.getImplementationMethod() & PROCESS_METHOD) != 0 && !(requiredItemType instanceof DocumentNodeTest)) {
            TypeCheckingFilter filter = new TypeCheckingFilter(output);
            filter.setRequiredType(requiredItemType, card, role, getLocation());
            next.process(filter, context);
            filter.finalCheck();
        } else {
            // Force pull-mode evaluation
            super.process(output, context);
        }
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
        ItemChecker exp = new ItemChecker(getBaseExpression().copy(rebindings), requiredItemType, role);
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
        TypeHierarchy th = getConfiguration().getTypeHierarchy();
        Affinity relationship = th.relationship(requiredItemType, operandType);
        switch (relationship) {
            case OVERLAPS:
                if (requiredItemType instanceof NodeTest && operandType instanceof NodeTest) {
                    return new CombinedNodeTest((NodeTest) requiredItemType, Token.INTERSECT, (NodeTest) operandType);
                } else {
                    // we don't know how to intersect atomic types, it doesn't actually happen
                    return requiredItemType;
                }

            case SUBSUMES:
            case SAME_TYPE:
                // shouldn't happen, but it doesn't matter
                return operandType;
            case SUBSUMED_BY:
            default:
                return requiredItemType;
        }
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
        return UType.fromTypeCode(requiredItemType.getPrimitiveType());
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        return super.equals(other) &&
                requiredItemType == ((ItemChecker) other).requiredItemType;
    }

    /**
     * get HashCode for comparing two expressions. Note that this hashcode gives the same
     * result for (A op B) and for (B op A), whether or not the operator is commutative.
     */

    @Override
    public int computeHashCode() {
        return super.computeHashCode() ^ requiredItemType.hashCode();
    }


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("treat", this);
        out.emitAttribute("as", AlphaCode.fromItemType(requiredItemType));
        out.emitAttribute("diag", role.save());
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
        String typeDesc = requiredItemType.toString();
        return "(" + getBaseExpression() + ") treat as " + typeDesc;
    }

    @Override
    public String toShortString() {
        return getBaseExpression().toShortString();
    }

}


