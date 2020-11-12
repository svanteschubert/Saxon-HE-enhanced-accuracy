////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.instruct.TerminationException;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.Error;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;

/**
 * A SingletonAtomizer combines the functions of an Atomizer and a CardinalityChecker: it is used to
 * atomize a sequence of nodes, checking that the result of the atomization contains zero or one atomic
 * values. Note that the input may be a sequence of nodes or atomic values, even though the result must
 * contain at most one atomic value.
 */

public final class SingletonAtomizer extends UnaryExpression {

    private boolean allowEmpty;
    private RoleDiagnostic roleDiagnostic;

    /**
     * Constructor
     *
     * @param sequence   the sequence to be atomized
     * @param role       contains information about where the expression appears, for use in any error message
     * @param allowEmpty true if the result sequence is allowed to be empty.
     */

    public SingletonAtomizer(Expression sequence, RoleDiagnostic role, boolean allowEmpty) {
        super(sequence);
        this.allowEmpty = allowEmpty;
        this.roleDiagnostic = role;
    }

    @Override
    protected OperandRole getOperandRole() {
        return OperandRole.SINGLE_ATOMIC;
    }

    /**
     * Ask if the expression is allowed to return an empty sequence
     * @return true if the expected cardinality is zero-or-one, false if it is exactly-one
     */

    public boolean isAllowEmpty() {
        return allowEmpty;
    }

    /**
     * Simplify an expression
     *
     */

    /*@NotNull*/
    @Override
    public Expression simplify() throws XPathException {
        Expression operand = getBaseExpression().simplify();
        if (operand instanceof Literal && ((Literal) operand).getValue() instanceof AtomicValue) {
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
        ExpressionTool.resetStaticProperties(this);
        if (Literal.isEmptySequence(operand)) {
            if (!allowEmpty) {
                typeError("An empty sequence is not allowed as the " + roleDiagnostic.getMessage(), roleDiagnostic.getErrorCode(), null);
            }
            return operand;
        }
        ItemType operandType = operand.getItemType();
        if (operandType.isPlainType()) {
            return operand;
        }
        if (!operandType.isAtomizable(visitor.getConfiguration().getTypeHierarchy())) {
            XPathException err;
            if (operandType instanceof MapType) {
                err = new XPathException("Cannot atomize a map (" + toShortString() + ")", "FOTY0013");
            } else if (operandType instanceof FunctionItemType) {
                err = new XPathException("Cannot atomize a function item", "FOTY0013");
            } else {
                err = new XPathException(
                        "Cannot atomize an element that is defined in the schema to have element-only content", "FOTY0012");
            }
            err.setIsTypeError(true);
            err.setLocation(getLocation());
            err.setFailingExpression(getParentExpression());
            throw err;
        }
        return this;
    }


    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        Expression exp = super.optimize(visitor, contextInfo);
        if (exp == this) {
            // Since it's an error for the result to have more than one item, there's no point sorting the input
            setBaseExpression(getBaseExpression().unordered(true, false));
            if (getBaseExpression().getItemType().isPlainType() &&
                    !Cardinality.allowsMany(getBaseExpression().getCardinality())) {
                return getBaseExpression();
            }
            return this;
        } else {
            return exp;
        }
    }

    /**
     * Determine the special properties of this expression
     *
     * @return {@link StaticProperty#NO_NODES_NEWLY_CREATED}.
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
        Expression e2 = new SingletonAtomizer(getBaseExpression().copy(rebindings), roleDiagnostic, allowEmpty);
        ExpressionTool.copyLocationInfo(this, e2);
        return e2;
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
        return EVALUATE_METHOD;
    }


    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "SingletonAtomizer";
    }

    /**
     * Get the RoleLocator (used to construct error messages)
     *
     * @return the roleDiagnostic locator
     */

    public RoleDiagnostic getRole() {
        return roleDiagnostic;
    }


    /*@Nullable*/
    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        PathMap.PathMapNodeSet result = getBaseExpression().addToPathMap(pathMap, pathMapNodeSet);
        if (result != null) {
            TypeHierarchy th = getConfiguration().getTypeHierarchy();
            ItemType operandItemType = getBaseExpression().getItemType();
            if (th.relationship(NodeKindTest.ELEMENT, operandItemType) != Affinity.DISJOINT ||
                    th.relationship(NodeKindTest.DOCUMENT, operandItemType) != Affinity.DISJOINT) {
                result.setAtomized();
            }
        }
        return null;
    }

    /**
     * Evaluate as an Item. This should only be called if a singleton or empty sequence is required;
     * it throws a type error if the underlying sequence is multi-valued.
     */

    @Override
    public AtomicValue evaluateItem(XPathContext context) throws XPathException {
        int found = 0;
        AtomicValue result = null;
        SequenceIterator iter = getBaseExpression().iterate(context);
        Item item;
        while ((item = iter.next()) != null) {
            AtomicSequence seq;
            try {
                seq = item.atomize();
            } catch (TerminationException | Error.UserDefinedXPathException e) {
                throw e;
            } catch (XPathException e) {
                if (roleDiagnostic == null) {
                    throw e;
                } else {
                    String message = e.getMessage() + ". Failed while atomizing the " + roleDiagnostic.getMessage();
                    XPathException e2 = new XPathException(message, e.getErrorCodeLocalPart(), e.getLocator());
                    e2.setXPathContext(context);
                    e2.maybeSetLocation(getLocation());
                    throw e2;
                }
            }
            found += seq.getLength();
            if (found > 1) {
                typeError(
                        "A sequence of more than one item is not allowed as the " +
                                roleDiagnostic.getMessage() + CardinalityChecker.depictSequenceStart(getBaseExpression().iterate(context), 3),
                        roleDiagnostic.getErrorCode(), context);
            }
            if (found == 1) {
                result = seq.head();
            }
        }
        if (found == 0 && !allowEmpty) {
            typeError("An empty sequence is not allowed as the " +
                    roleDiagnostic.getMessage(), roleDiagnostic.getErrorCode(), null);
        }
        return result;
    }

    /**
     * Determine the data type of the items returned by the expression, if possible
     *
     * @return a value such as Type.STRING, Type.BOOLEAN, Type.NUMBER. For this class, the
     *         result is always an atomic type, but it might be more specific.
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        boolean isSchemaAware = true;
        try {
            isSchemaAware = getPackageData().isSchemaAware();
        } catch (NullPointerException err) {
            // ultra-cautious code in case expression container has not been set
            if (!getConfiguration().isLicensedFeature(Configuration.LicenseFeature.SCHEMA_VALIDATION)) {
                isSchemaAware = false;
            }
        }
        ItemType in = getBaseExpression().getItemType();
        if (in.isPlainType()) {
            return in;
        } else if (in instanceof NodeTest) {
            UType kinds = in.getUType();
            if (!isSchemaAware) {
                // Some node-kinds always have a typed value that's a string

                if (Atomizer.STRING_KINDS.subsumes(kinds)) {
                    return BuiltInAtomicType.STRING;
                }
                // Some node-kinds are always untyped atomic; some are untypedAtomic provided that the configuration
                // is untyped

                if (Atomizer.UNTYPED_IF_UNTYPED_KINDS.subsumes(kinds)) {
                    return BuiltInAtomicType.UNTYPED_ATOMIC;
                }
            } else {
                if (Atomizer.UNTYPED_KINDS.subsumes(kinds)) {
                    return BuiltInAtomicType.UNTYPED_ATOMIC;
                }
            }

            return in.getAtomizedItemType();
        } else if (in instanceof JavaExternalObjectType) {
            return in.getAtomizedItemType();
        }
        return BuiltInAtomicType.ANY_ATOMIC;
    }

    /**
     * Determine the static cardinality of the expression
     */

    @Override
    public int computeCardinality() {
        if (allowEmpty) {
            return StaticProperty.ALLOWS_ZERO_OR_ONE;
        } else {
            return StaticProperty.EXACTLY_ONE;
        }
    }

    /**
     * Give a string representation of the expression name for use in diagnostics
     *
     * @return the expression name, as a string
     */

    @Override
    public String getExpressionName() {
        return "atomizeSingleton";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out
     */
    @Override
    public void export(ExpressionPresenter out) throws XPathException {

        out.startElement("atomSing", this);
        if (allowEmpty) {
            out.emitAttribute("card", "?");
        }
        out.emitAttribute("diag", getRole().save());
        getBaseExpression().export(out);
        out.endElement();
    }

    @Override
    public String toShortString() {
        return getBaseExpression().toShortString();
    }


}

