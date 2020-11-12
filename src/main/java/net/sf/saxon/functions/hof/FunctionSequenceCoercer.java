////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.FunctionAnnotationHandler;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.query.Annotation;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.SequenceType;


/**
 * An FunctionSequenceCoercer is an expression that performs function coercion on a sequence of function items:
 * it takes a sequence of supplied items as input, and wraps each one in a CoercedFunction value, which dynamically
 * converts the supplied arguments to the required type, and converts the result in the opposite direction, or
 * throws a type error if conversion is not possible.
 */

public final class FunctionSequenceCoercer extends UnaryExpression {

    private SpecificFunctionType requiredItemType;
    private RoleDiagnostic role;

    /**
     * Constructor
     *  @param sequence         this must be a sequence of function item values. This is not checked; a ClassCastException
     *                         will occur if the precondition is not satisfied.
     * @param requiredItemType the function item type to which all items in the sequence should be converted,
     */

    public FunctionSequenceCoercer(Expression sequence, SpecificFunctionType requiredItemType,
                                   RoleDiagnostic role) {
        super(sequence);
        this.requiredItemType = requiredItemType;
        this.role = role;
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
        setBaseExpression(getBaseExpression().simplify());
        if (getBaseExpression() instanceof Literal) {
            GroundedValue val =
                    iterate(new EarlyEvaluationContext(getConfiguration())).materialize();
            return Literal.makeLiteral(val, this);
        }
        return this;
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getOperand().typeCheck(visitor, contextInfo);
        final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        if (th.isSubType(getBaseExpression().getItemType(), requiredItemType)) {
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
    public int computeSpecialProperties() {
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
        FunctionSequenceCoercer fsc2 = new FunctionSequenceCoercer(getBaseExpression().copy(rebindings), requiredItemType, role);
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
        SequenceIterator base = getBaseExpression().iterate(context);
        Coercer coercer = new Coercer(requiredItemType, context.getConfiguration(), getLocation());
        return new ItemMappingIterator(base, coercer, true);
    }

    /**
     * Evaluate as an Item. This should only be called if the FunctionSequenceCoercer has cardinality zero-or-one
     */

    /*@Nullable*/
    @Override
    public Function evaluateItem(XPathContext context) throws XPathException {
        Item item = getBaseExpression().evaluateItem(context);
        if (item == null) {
            return null;
        }
        if (!(item instanceof Function)) {
            UType itemType = UType.getUType(item);
            throw new XPathException(role.composeErrorMessage(requiredItemType, itemType), "XPTY0004");
        }
        try {
            checkAnnotations((Function)item, requiredItemType, context.getConfiguration());
        } catch (XPathException err) {
            err.maybeSetLocation(getLocation());
            err.maybeSetContext(context);
            throw err;
        }
        return new CoercedFunction((Function)item, requiredItemType);
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
    public int computeCardinality() {
        return getBaseExpression().getCardinality();
    }

    /**
     * @return the role locator
     */
    public RoleDiagnostic getRole() {
        return role;
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        return super.equals(other) &&
            requiredItemType.equals(((FunctionSequenceCoercer) other).requiredItemType);
    }

    @Override
    public int computeHashCode() {
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
        SequenceType st = SequenceType.makeSequenceType(requiredItemType, StaticProperty.EXACTLY_ONE);
        destination.emitAttribute("to", st.toAlphaCode());
        destination.emitAttribute("diag", role.save());
        getBaseExpression().export(destination);
        destination.endElement();
    }

    private static void checkAnnotations(Function item, FunctionItemType requiredItemType, Configuration config) throws XPathException {
        for (Annotation ann : requiredItemType.getAnnotationAssertions()) {
            FunctionAnnotationHandler handler = config.getFunctionAnnotationHandler(ann.getAnnotationQName().getURI());
            if (handler != null && !handler.satisfiesAssertion(ann, item.getAnnotations())) {
                throw new XPathException(
                        "Supplied function does not satisfy the annotation assertions of the required function type", "XPTY0004");
            }
        }
    }

    public static class Coercer implements ItemMappingFunction {

        private SpecificFunctionType requiredItemType;
        private Configuration config;
        private Location locator;

        public Coercer(SpecificFunctionType requiredItemType, Configuration config, Location locator) {
            this.requiredItemType = requiredItemType;
            this.config = config;
            this.locator = locator;
        }

        @Override
        public Function mapItem(Item item) throws XPathException {
            if (!(item instanceof Function)) {
                throw new XPathException(
                        "Function coercion attempted on an item which is not a function", "XPTY0004", locator);
            }
            try {
                checkAnnotations((Function)item, requiredItemType, config);
                return new CoercedFunction((Function)item, requiredItemType);
            } catch (XPathException err) {
                err.maybeSetLocation(locator);
                throw err;
            }
        }


    }
}

// Copyright (c) 2009-2020 Saxonica Limited
