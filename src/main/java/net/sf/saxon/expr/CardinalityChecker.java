////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.TypeCheckingFilter;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.PullElaborator;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.elab.PushEvaluator;
import net.sf.saxon.expr.instruct.TailCall;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.pattern.DocumentNodeTest;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.TwoItemIterator;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.IntegerValue;

import java.util.function.Supplier;


/**
 * A CardinalityChecker implements the cardinality checking of "treat as": that is,
 * it returns the supplied sequence, checking that its cardinality is correct
 */

public final class CardinalityChecker extends UnaryExpression {

    private int requiredCardinality = -1;
    private final Supplier<RoleDiagnostic> roleSupplier;

    /**
     * Private Constructor: use factory method
     *
     * @param sequence    the base sequence whose cardinality is to be checked
     * @param cardinality the required cardinality
     * @param role        information to be used in error reporting
     */

    private CardinalityChecker(Expression sequence, int cardinality, Supplier<RoleDiagnostic> role) {
        super(sequence);
        requiredCardinality = cardinality;
        this.roleSupplier = role;
        //computeStaticProperties();
        //adoptChildExpression(sequence);
    }

    /**
     * Factory method to construct a CardinalityChecker. The method may create an expression that combines
     * the cardinality checking with the functionality of the underlying expression class
     *
     * @param sequence    the base sequence whose cardinality is to be checked
     * @param cardinality the required cardinality
     * @param roleSupplier        information to be used in error reporting
     * @return a new Expression that does the CardinalityChecking (not necessarily a CardinalityChecker)
     */

    public static Expression makeCardinalityChecker(Expression sequence, int cardinality, Supplier<RoleDiagnostic> roleSupplier) {
        Expression result;
        if (sequence instanceof Literal && Cardinality.subsumes(cardinality, SequenceTool.getCardinality(((Literal) sequence).getGroundedValue()))) {
            return sequence;
        }
        if (sequence instanceof Atomizer && !Cardinality.allowsMany(cardinality)) {
            Expression base = ((Atomizer) sequence).getBaseExpression();
            result = new SingletonAtomizer(base, roleSupplier, Cardinality.allowsZero(cardinality));
        } else {
            result = new CardinalityChecker(sequence, cardinality, roleSupplier);
        }
        ExpressionTool.copyLocationInfo(sequence, result);
        return result;
    }

    @Override
    protected OperandRole getOperandRole() {
        return OperandRole.SAME_FOCUS_ACTION;
    }

    /**
     * Get the required cardinality
     *
     * @return the cardinality required by this checker
     */

    public int getRequiredCardinality() {
        return requiredCardinality;
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getOperand().typeCheck(visitor, contextInfo);
        Expression base = getBaseExpression();
        if (requiredCardinality == StaticProperty.ALLOWS_ZERO_OR_MORE ||
                Cardinality.subsumes(requiredCardinality, base.getCardinality())) {
            return base;
        }
        return this;
    }

    /**
     * Perform optimisation of an expression and its subexpressions.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor         an expression visitor
     * @param contextInfo the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getOperand().optimize(visitor, contextInfo);
        Expression base = getBaseExpression();
        if (requiredCardinality == StaticProperty.ALLOWS_ZERO_OR_MORE ||
                Cardinality.subsumes(requiredCardinality, base.getCardinality())) {
            return base;
        }
        if ((base.getCardinality() & requiredCardinality) == 0) {
            RoleDiagnostic role = roleSupplier.get();
            throw new XPathException("The " + role.getMessage() +
                    " does not satisfy the cardinality constraints", role.getErrorCode())
                    .withLocation(getLocation())
                    .asTypeErrorIf(role.isTypeError());
        }
        // do cardinality checking before item checking (may avoid the need for a mapping iterator)
        if (base instanceof ItemChecker) {
            ItemChecker checker = (ItemChecker) base;
            Expression other = checker.getBaseExpression();
            // change this -> checker -> other to checker -> this -> other
            setBaseExpression(other);
            checker.setBaseExpression(this);
            checker.setParentExpression(null);
            return checker;
        }
        return this;
    }


//    /**
//     * Set the error code to be returned (this is used when evaluating the functions such
//     * as exactly-one() which have their own error codes)
//     *
//     * @param code the error code to be used
//     */
//
//    public void setErrorCode(String code) {
//        roleSupplier.setErrorCode(code);
//    }

    /**
     * Get the RoleLocator, which contains diagnostic information for use if the cardinality check fails
     *
     * @return the diagnostic information
     */

    public RoleDiagnostic getRoleLocator() {
        return roleSupplier.get();
    }

    public Supplier<RoleDiagnostic> getRoleSupplier() {
        return roleSupplier;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides both iterate() and
     * process() methods natively.
     */

    @Override
    public int getImplementationMethod() {
        int m = ITERATE_METHOD | PROCESS_METHOD | ITEM_FEED_METHOD;
        if (!Cardinality.allowsMany(requiredCardinality)) {
            m |= EVALUATE_METHOD;
        }
        return m;
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
        return checkCardinality(base, context);
    }

    public SequenceIterator checkCardinality(SequenceIterator base, XPathContext context) throws XPathException {

        // If the base iterator knows how many items there are, then check it now rather than wasting time

        if (SequenceTool.supportsGetLength(base)) {
            int count = SequenceTool.getLength(base);
            if (count == 0 && !Cardinality.allowsZero(requiredCardinality)) {
                RoleDiagnostic role = roleSupplier.get();
                typeError("An empty sequence is not allowed as the " +
                        role.getMessage(), role.getErrorCode(), context);
            } else if (count == 1 && requiredCardinality == StaticProperty.EMPTY) {
                RoleDiagnostic role = roleSupplier.get();
                typeError("The only value allowed for the " +
                        role.getMessage() + " is an empty sequence", role.getErrorCode(), context);
            } else if (count > 1 && !Cardinality.allowsMany(requiredCardinality)) {
                RoleDiagnostic role = roleSupplier.get();
                typeError("A sequence of more than one item is not allowed as the " +
                        role.getMessage() + depictSequenceStart(base, 2),
                          role.getErrorCode(), context);
            }
            return base;
        }

        // Otherwise return an iterator that does the checking on the fly

        try {
            return new CardinalityCheckingIterator(base, requiredCardinality, roleSupplier, getLocation());
        } catch (XPathException e) {
            throw e.maybeWithContext(context);
        }

    }

    /**
     * Show the first couple of items in a sequence in an error message
     *
     * @param seq iterator over the sequence
     * @param max maximum number of items to be shown
     * @return a message display of the contents of the sequence
     */

    public static String depictSequenceStart(SequenceIterator seq, int max) {
        StringBuilder sb = new StringBuilder(64);
        int count = 0;
        sb.append(" (");
        Item next;
        while ((next = seq.next()) != null) {
            if (count++ > 0) {
                sb.append(", ");
            }
            if (count > max) {
                sb.append("...) ");
                return sb.toString();
            }

            sb.append(Err.depict(next));
        }
        sb.append(") ");
        return sb.toString();
    }

    /**
     * Evaluate as an Item. For this class, this implies checking that the underlying
     * expression delivers a singleton.
     */

    /*@Nullable*/
    @Override
    public Item evaluateItem(XPathContext context) throws XPathException {
        try {
            SequenceIterator iter = getBaseExpression().iterate(context);
            Item first = iter.next();
            if (first == null) {
                if (!Cardinality.allowsZero(requiredCardinality)) {
                    RoleDiagnostic role = roleSupplier.get();
                    typeError("An empty sequence is not allowed as the " +
                            role.getMessage(), role.getErrorCode(), context);
                }
                return null;
            } else {
                if (requiredCardinality == StaticProperty.EMPTY) {
                    RoleDiagnostic role = roleSupplier.get();
                    typeError("An empty sequence is required as the " +
                        role.getMessage(), role.getErrorCode(), context);
                    return null;
                }
                Item second = iter.next();
                if (second != null) {
                    RoleDiagnostic role = roleSupplier.get();
                    typeError("A sequence of more than one item is not allowed as the " +
                        role.getMessage() + depictSequenceStart(
                                new TwoItemIterator(first, second), 2), role.getErrorCode(), context);
                    return null;
                }
            }
            return first;
        } catch (UncheckedXPathException e) {
            throw e.getXPathException();
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
        PushEvaluator pusher = makeElaborator().elaborateForPush();
        TailCall tc = pusher.processLeavingTail(output, context);
        Expression.dispatchTailCall(tc);
    }

    /**
     * Determine the data type of the items returned by the expression, if possible
     *
     * @return a value such as Type.STRING, Type.BOOLEAN, Type.NUMBER, Type.NODE,
     *         or Type.ITEM (meaning not known in advance)
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return getBaseExpression().getItemType();
    }

    /**
     * Determine the static cardinality of the expression
     */

    @Override
    protected int computeCardinality() {
        return requiredCardinality;
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     */

    @Override
    protected int computeSpecialProperties() {
        return getBaseExpression().getSpecialProperties();
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
        CardinalityChecker c2 = new CardinalityChecker(getBaseExpression().copy(rebindings), requiredCardinality, roleSupplier);
        ExpressionTool.copyLocationInfo(this, c2);
        return c2;
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        return super.equals(other) &&
                requiredCardinality == ((CardinalityChecker) other).requiredCardinality;
    }

    /**
     * get HashCode for comparing two expressions. Note that this hashcode gives the same
     * result for (A op B) and for (B op A), whether or not the operator is commutative.
     */

    @Override
    protected int computeHashCode() {
        return super.computeHashCode() ^ requiredCardinality;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("check", this);
        String occ = Cardinality.getOccurrenceIndicator(requiredCardinality);
        if (occ.equals("")) {
            occ = "1";
        }
        out.emitAttribute("card", occ);
        out.emitAttribute("diag", roleSupplier.get().save());
        getBaseExpression().export(out);
        out.endElement();
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form. For subclasses of Expression that represent XPath expressions, the result should always
     * be a string that parses as an XPath 3.0 expression.
     */
    @Override
    public String toString() {
        Expression operand = getBaseExpression();
        switch (requiredCardinality) {
            case StaticProperty.ALLOWS_ONE:
                return "exactly-one(" + operand + ")";
            case StaticProperty.ALLOWS_ZERO_OR_ONE:
                return "zero-or-one(" + operand + ")";
            case StaticProperty.ALLOWS_ONE_OR_MORE:
                return "one-or-more(" + operand + ")";
            case StaticProperty.EMPTY:
                return "must-be-empty(" + operand + ")";
            default:
                return "check(" + operand + ")";
        }
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
        return "CheckCardinality";
    }

    @Override
    public String toShortString() {
        return getBaseExpression().toShortString();
    }


    @Override
    public String getStreamerName() {
        return "CardinalityChecker";
    }

    @Override
    public void setLocation(Location id) {
        super.setLocation(id);
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new CardinalityCheckerElaborator();
    }

    /**
     * Elaborator for a {@code treat as} expression, which is usually system-generated by
     * the type checking phase of the compiler
     */

    public static class CardinalityCheckerElaborator extends PullElaborator {

        @Override
        public PullEvaluator elaborateForPull() {
            CardinalityChecker expr = (CardinalityChecker) getExpression();
            Expression arg = expr.getBaseExpression();
            PullEvaluator argEval = arg.makeElaborator().elaborateForPull();
            return context -> expr.checkCardinality(argEval.iterate(context), context);
        }

        @Override
        public PushEvaluator elaborateForPush() {
            CardinalityChecker expr = (CardinalityChecker) getExpression();
            Expression next = expr.getBaseExpression();

            ItemType type = Type.ITEM_TYPE;
            if (next instanceof ItemChecker) {
                type = ((ItemChecker) next).getRequiredType();
                next = ((ItemChecker) next).getBaseExpression();
            }
            if ((next.getImplementationMethod() & PROCESS_METHOD) != 0 && !(type instanceof DocumentNodeTest)) {
                ItemType finalType = type;
                PushEvaluator pushEval = next.makeElaborator().elaborateForPush();
                return (output, context) -> {
                    TypeCheckingFilter filter = new TypeCheckingFilter(output);
                    filter.setRequiredType(finalType, expr.requiredCardinality, expr.roleSupplier.get(), expr.getLocation());
                    TailCall tc = pushEval.processLeavingTail(filter, context);
                    Expression.dispatchTailCall(tc);
                    try {
                        filter.finalCheck();
                    } catch (XPathException e) {
                        throw e.maybeWithLocation(expr.getLocation());
                    }
                    return null;
                };

            } else {
                // Force pull-mode evaluation

                PullEvaluator argEval = next.makeElaborator().elaborateForPull();
                return (output, context) -> {
                    SequenceIterator iter = expr.checkCardinality(argEval.iterate(context), context);
                    for (Item item; (item = iter.next()) != null;) {
                        output.append(item);
                    }
                    return null;
                };
            }

        }
    }
}

