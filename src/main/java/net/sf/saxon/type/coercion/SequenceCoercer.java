////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.coercion;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.PullElaborator;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.type.UType;
import net.sf.saxon.type.gnode.AnyXNodeType;
import net.sf.saxon.type.gnode.GNodeType;
import net.sf.saxon.type.gnode.XNodeType;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

import java.util.function.Supplier;


/**
 * An SequenceCoercer is an expression that performs coercion on a sequence of items:
 */

public class SequenceCoercer extends UnaryExpression {

    protected final SequenceType requiredType;
    protected final Supplier<RoleDiagnostic> roleSupplier;
    protected final boolean allow40;


    /**
     * Constructor
     *  @param sequence         this must be a sequence of function item values. This is not checked; a ClassCastException
     *                         will occur if the precondition is not satisfied.
     * @param requiredType the function item type to which all items in the sequence should be converted,
     */

    protected SequenceCoercer(Expression sequence, SequenceType requiredType,
                           Supplier<RoleDiagnostic> role, boolean allow40) {
        super(sequence);
        this.requiredType = requiredType;
        this.roleSupplier = role;
        this.allow40 = allow40;
        UType itemType = requiredType.getPrimaryType().getUType();
        if (itemType.overlaps(UType.XNODE)) {
            getOperand().setOperandRole(getOperand().getOperandRole().withUsage(OperandUsage.TRANSMISSION));
        }
        ExpressionTool.copyLocationInfo(sequence, this);
    }

    public static Expression makeSequenceCoercer(Expression sequence, SequenceType requiredType,
                                                 Supplier<RoleDiagnostic> role, boolean allow40) {
        // No real coercion happens for nodes - only checking that supplied value is of the right type.
        // Also, nodes are handled specially when streaming. So if the required type is a node type,
        // we use an ItemChecker and/or CardinalityChecker.


        ItemType requiredItemType = requiredType.getPrimaryType();
        Genre supplied = sequence.getItemType().getGenre();
        if (requiredItemType.isAtomicType() && supplied == Genre.XNODE) {
            sequence = new Atomizer(sequence, role);
        }
//        if (requiredItemType == AnyGNodeType.getInstance() && (supplied == Genre.ARRAY || supplied == Genre.MAP)) {
//            requiredType = SequenceType.makeSequenceType(AnyJNodeType.getInstance(), requiredType.getCardinality());
//            requiredItemType = AnyJNodeType.getInstance();
//        }
        if (requiredItemType instanceof XNodeType) {
            Expression result = sequence;
            if (requiredType.getCardinality() != StaticProperty.ALLOWS_ZERO_OR_MORE) {
                result = CardinalityChecker.makeCardinalityChecker(result, requiredType.getCardinality(), role);
            }
            if (!(sequence.getItemType() instanceof XNodeType && requiredItemType == AnyXNodeType.getInstance())) {
                result = new ItemChecker(result, requiredItemType, role);
            }
            return result;
        }
        if (requiredItemType instanceof GNodeType) {
            Expression result = sequence;
            if (requiredType.getCardinality() != StaticProperty.ALLOWS_ZERO_OR_MORE) {
                result = CardinalityChecker.makeCardinalityChecker(result, requiredType.getCardinality(), role);
            }
            return new ItemChecker(result, requiredItemType, role);
        }
//        if (requiredItemType == AnyGNodeType.getInstance()) {
//            Expression result = sequence;
//            if (requiredType.getCardinality() != StaticProperty.ALLOWS_ZERO_OR_MORE) {
//                result = CardinalityChecker.makeCardinalityChecker(result, requiredType.getCardinality(), role);
//            }
//            return new GNodeSequenceConverter(result, role);
//        }
        return new SequenceCoercer(sequence, requiredType, role, allow40);
    }

    @Override
    protected OperandRole getOperandRole() {
        return OperandRole.ATOMIC_SEQUENCE;
    }

    public SequenceType getRequiredType() {
        return requiredType;
    }

    @Override
    protected int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        if (requiredType.getPrimaryType().isAtomicType()) {
            return p | StaticProperty.NO_NODES_NEWLY_CREATED;
        }
        return p;
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

        // We may now have more type information available meaning that we now know that no coercion is needed.

        // If the static type of the expression already satisfies the required type, then in general
        // no coercion is required. The exception is with functions, where function coercion is always done,
        // to ensure that the parameters of a function call are checked against the required types even
        // if the actual function accepts a supertype.

        ItemType reqItemType = requiredType.getPrimaryType();
        if (reqItemType instanceof SpecificFunctionType) {
            return this; // Function coercion is mandatory
        }
        int reqCardinality = requiredType.getCardinality();
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        Expression supplied = getOperand().getChildExpression();
        if (Cardinality.subsumes(reqCardinality, supplied.getCardinality())) {
            // no cardinality check needed
            if (th.isSubType(supplied.getItemType(), reqItemType)) {
                // no item type check needed
                return supplied;
            }
        }

        return this;

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
        SequenceCoercer fsc2 = new SequenceCoercer(
                getBaseExpression().copy(rebindings), requiredType, roleSupplier, allow40);
        ExpressionTool.copyLocationInfo(this, fsc2);
        return fsc2;
    }

    public Pattern toPattern(Configuration config, boolean firstInPath) throws XPathException {
        if (requiredType.equals(SequenceType.GNODE_SEQUENCE) || requiredType.equals(SequenceType.NODE_SEQUENCE)) {
            return getBaseExpression().toPattern(config, firstInPath);
        } else {
            return super.toPattern(config, firstInPath);
        }
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
     * Evaluate the expression as a sequence iterator
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(final XPathContext context) throws XPathException {
        return makeElaborator().elaborateForPull().iterate(context);
    }

    /**
     * Evaluate the expression as an Item.
     */

    /*@Nullable*/
    @Override
    public Item evaluateItem(XPathContext context) throws XPathException {
        return makeElaborator().elaborateForItem().eval(context);
    }


    /**
     * Determine the data type of the items returned by the expression
     *
     * @return a value such as Type.STRING, Type.BOOLEAN, Type.NUMBER, Type.NODE,
     * or Type.ITEM (meaning not known in advance)
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return requiredType.getPrimaryType();
    }

    /**
     * Determine the static cardinality of the expression
     */

    @Override
    protected int computeCardinality() {
        return requiredType.getCardinality();
    }

    /**
     * @return the role locator
     */
    public RoleDiagnostic getRoleDiagnostic() {
        return roleSupplier.get();
    }

    public Supplier<RoleDiagnostic> getRoleSupplier() {
        return roleSupplier;
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        return super.equals(other) &&
            requiredType.equals(((SequenceCoercer) other).requiredType);
    }

    @Override
    protected int computeHashCode() {
        return super.computeHashCode() ^ requiredType.hashCode();
    }

    @Override
    public String getExpressionName() {
        return "coercer{" + requiredType + "}";
    }


    /**
     * Export expression to SEF file. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("coercer", this);
        destination.emitAttribute("type", requiredType.toAlphaCode());
        destination.emitAttribute("diag", roleSupplier.get().save());
        String flags = allow40 ? "4" : "";
        if (!flags.isEmpty()) {
            destination.emitAttribute("flags", "4");
        }
        getBaseExpression().export(destination);
        destination.endElement();
    }

    @Override
    public String getStreamerName() {
        return "Coercer";
    }

    /**
     * Construct a coercion plan
     */

    public CoercionPlan getCoercionPlan() {
        CoercionPlan plan = requiredType.getPrimaryType().getCoercionPlan(getPackageData().getHostLanguageVersion());
        if (plan == null) {
            plan = new IdentityCoercionPlan();
        }
        return plan;
    }

    /**
     * Make an elaborator for this expression
     *
     * @return an appropriate {@link Elaborator}
     */
    @Override
    public Elaborator getElaborator() {
        return new SequenceCoercerElaborator();
    }



    private static class SequenceCoercerElaborator extends PullElaborator {

        @Override
        public PullEvaluator elaborateForPull() {
            SequenceCoercer expr = (SequenceCoercer) getExpression();
            SequenceType requiredType = expr.requiredType;
            SequenceType suppliedType = SequenceType.makeSequenceType(expr.getItemType(), expr.getCardinality());
            CoercionPlan plan = expr.getCoercionPlan();
            PullEvaluator base = expr.getBaseExpression().makeElaborator().elaborateForPull();
            CoercionRequest request = new CoercionRequest(
                    suppliedType, expr.getConfiguration(), expr.roleSupplier, expr.getLocation());
            return context -> plan.coerceSequence(base.iterate(context), requiredType, request);

            // TODO: isolate the case where the only thing needed is a CardinalityChecker
        }

    }
}

// Copyright (c) 2024-2026 Saxonica Limited
