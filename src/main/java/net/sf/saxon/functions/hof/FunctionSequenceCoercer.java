////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.PullElaborator;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.FunctionAnnotationHandler;
import net.sf.saxon.om.*;
import net.sf.saxon.query.Annotation;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Affinity;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.SequenceType;

import java.util.function.Supplier;


/**
 * An FunctionSequenceCoercer is an expression that performs function coercion on a sequence of function items:
 * it takes a sequence of supplied items as input, and wraps each one in a CoercedFunction value, which dynamically
 * converts the supplied arguments to the required type, and converts the result in the opposite direction, or
 * throws a type error if conversion is not possible.
 */

public final class FunctionSequenceCoercer extends UnaryExpression {

    private final SpecificFunctionType requiredItemType;
    private final Supplier<RoleDiagnostic> roleSupplier;
    private final boolean allow40;
    private final boolean acceptReducedArity;

    /**
     * Constructor
     *  @param sequence         this must be a sequence of function item values. This is not checked; a ClassCastException
     *                         will occur if the precondition is not satisfied.
     * @param requiredItemType the function item type to which all items in the sequence should be converted,
     */

    public FunctionSequenceCoercer(Expression sequence, SpecificFunctionType requiredItemType,
                                   Supplier<RoleDiagnostic> role, boolean allow40, boolean acceptReducedArity) {
        super(sequence);
        this.requiredItemType = requiredItemType;
        this.roleSupplier = role;
        this.allow40 = allow40;
        this.acceptReducedArity = acceptReducedArity;
        ExpressionTool.copyLocationInfo(sequence, this);
    }

    @Override
    protected OperandRole getOperandRole() {
        return OperandRole.INSPECT;
    }

    /**
     * Simplify an expression
     *
     */

    /*@NotNull*/
    @Override
    public Expression simplify() throws XPathException {
        try {
            setBaseExpression(getBaseExpression().simplify());
            if (getBaseExpression() instanceof Literal) {
                GroundedValue val =
                        SequenceTool.toGroundedValue(iterate(new EarlyEvaluationContext(getConfiguration())));
                return Literal.makeLiteral(val, this);
            }
            return this;
        } catch (UncheckedXPathException e) {
            throw e.getXPathException();
        }
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getOperand().typeCheck(visitor, contextInfo);
        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        // We can't dispense with function coercion just because the supplied function is an instance
        // of the required type, because it might not perform enough checking of arguments: see test VarDecl066.
        // We can only dispense with it if the argument checking and result checking is at least
        // as strict as the required type. For now, do coercion unless the type signatures are identical

        //if (th.isSubType(getBaseExpression().getItemType(), requiredItemType)) {
        if (th.relationship(getBaseExpression().getItemType(), requiredItemType) == Affinity.SAME_TYPE) {
            return getBaseExpression();
        } else {
            return this;
        }
    }

    /**
     * Determine the special properties of this expression
     *
     * @return {@link net.sf.saxon.expr.StaticProperty#NO_NODES_NEWLY_CREATED}.
     */

    @Override
    protected int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        return p | StaticProperty.NO_NODES_NEWLY_CREATED;
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
        FunctionSequenceCoercer fsc2 = new FunctionSequenceCoercer(
                getBaseExpression().copy(rebindings), requiredItemType, roleSupplier, allow40, acceptReducedArity);
        ExpressionTool.copyLocationInfo(this, fsc2);
        return fsc2;
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
        return ITERATE_METHOD;
    }

    /**
     * Iterate over the sequence of functions, wrapping each one in a CoercedFunction object
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(final XPathContext context) throws XPathException {
        return makeElaborator().elaborateForPull().iterate(context);
    }

    /**
     * Evaluate as an Item. This should only be called if the FunctionSequenceCoercer has cardinality zero-or-one
     */

    /*@Nullable*/
    @Override
    public FunctionItem evaluateItem(XPathContext context) throws XPathException {
        return (FunctionItem)makeElaborator().elaborateForItem().eval(context);
    }


    /**
     * Determine the data type of the items returned by the expression, if possible
     *
     * @return a value such as Type.STRING, Type.BOOLEAN, Type.NUMBER, Type.NODE,
     * or Type.ITEM (meaning not known in advance)
     */

    /*@NotNull*/
    @Override
    public SpecificFunctionType getItemType() {
        return requiredItemType;
    }

    /**
     * Determine the static cardinality of the expression
     */

    @Override
    protected int computeCardinality() {
        return getBaseExpression().getCardinality();
    }

    /**
     * @return the role locator
     */
    public RoleDiagnostic getRoleSupplier() {
        return roleSupplier.get();
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        return super.equals(other) &&
            requiredItemType.equals(((FunctionSequenceCoercer) other).requiredItemType);
    }

    @Override
    protected int computeHashCode() {
        return super.computeHashCode() ^ requiredItemType.hashCode();
    }

    @Override
    public String getExpressionName() {
        return "fnCoercer";
    }


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("fnCoercer", this);
        SequenceType st = SequenceType.one(requiredItemType);
        destination.emitAttribute("to", st.toAlphaCode());
        destination.emitAttribute("diag", roleSupplier.get().save());
        String flags = "" + (allow40 ? "4" : "") + (acceptReducedArity ? "r" : "");
        if (!flags.isEmpty()) {
            destination.emitAttribute("flags", "4");
        }
        getBaseExpression().export(destination);
        destination.endElement();
    }

    /**
     * Make an elaborator for this expression
     *
     * @return an appropriate {@link Elaborator}
     */
    @Override
    public Elaborator getElaborator() {
        return new FunctionSequenceCoercerElaborator();
    }

    public static void checkAnnotations(FunctionItem item, FunctionItemType requiredItemType, Configuration config) throws XPathException {
        for (Annotation ann : requiredItemType.getAnnotationAssertions()) {
            FunctionAnnotationHandler handler = config.getFunctionAnnotationHandler(ann.getAnnotationQName().getNamespaceUri());
            if (handler != null && !handler.satisfiesAssertion(ann, item.getAnnotations())) {
                throw new XPathException(
                        "Supplied function does not satisfy the annotation assertions of the required function type", "XPTY0004");
            }
        }
    }

    public static class Coercer implements ItemMappingFunction {

        private final SpecificFunctionType requiredItemType;
        private final Configuration config;
        private final Location locator;
        private final boolean allow40;
        private final boolean acceptReducedArity;

        public Coercer(SpecificFunctionType requiredItemType, Configuration config,
                       Location locator, boolean allow40, boolean acceptReducedArity) {
            this.requiredItemType = requiredItemType;
            this.config = config;
            this.locator = locator;
            this.allow40 = allow40;
            this.acceptReducedArity = acceptReducedArity;
        }

        public FunctionItem mapItem(Item item) throws XPathException {
            if (!(item instanceof FunctionItem)) {
                throw new XPathException(
                        "Function coercion attempted on an item (" + item.toShortString() + ") which is not a function", "XPTY0004", locator);
            }
            try {
                checkAnnotations((FunctionItem)item, requiredItemType, config);
                return new CoercedFunction((FunctionItem)item, requiredItemType, allow40, acceptReducedArity);
            } catch (XPathException err) {
                throw err.maybeWithLocation(locator);
            }
        }


    }

    private static class FunctionSequenceCoercerElaborator extends PullElaborator {

        @Override
        public PullEvaluator elaborateForPull() {
            FunctionSequenceCoercer expr = (FunctionSequenceCoercer) getExpression();
            PullEvaluator base = expr.getBaseExpression().makeElaborator().elaborateForPull();
            Coercer coercer = new Coercer(
                    expr.requiredItemType, expr.getConfiguration(), expr.getLocation(), expr.allow40, expr.acceptReducedArity);
            return context -> new ItemMappingIterator(base.iterate(context), coercer, true);
        }

        @Override
        public ItemEvaluator elaborateForItem() {
            FunctionSequenceCoercer expr = (FunctionSequenceCoercer) getExpression();
            ItemEvaluator base = expr.getBaseExpression().makeElaborator().elaborateForItem();
            Coercer coercer = new Coercer(
                    expr.requiredItemType, expr.getConfiguration(), expr.getLocation(), expr.allow40, expr.acceptReducedArity);
            return context -> {
                Item item = base.eval(context);
                if (item == null) {
                    return null;
                }
                return coercer.mapItem(item);
            };
        }
    }
}

// Copyright (c) 2009-2026 Saxonica Limited
