////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.SequenceType;


/**
 * This class represents the expression "(dot)", which always returns the context item.
 */

public class ContextItemExpression extends Expression {

    private ContextItemStaticInfo staticInfo = ContextItemStaticInfo.DEFAULT;
    private String errorCodeForAbsentContext = "XPDY0002";
    private boolean absentContextIsTypeError = false; // absurdly, but that's what the spec says

    /**
     * Create the expression
     */

    public ContextItemExpression() {
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
        return "dot";
    }

    /**
     * Create a clone copy of this expression
     *
     * @return a copy of this expression
     * @param rebindings     variables that must be re-bound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        ContextItemExpression cie2 = new ContextItemExpression();
        cie2.staticInfo = staticInfo;
        cie2.setErrorCodeForUndefinedContext(errorCodeForAbsentContext, false);
        ExpressionTool.copyLocationInfo(this, cie2);
        return cie2;
    }

    public void setErrorCodeForUndefinedContext(String errorCode, boolean isTypeError) {
        errorCodeForAbsentContext = errorCode;
        absentContextIsTypeError = isTypeError;
    }

    public String getErrorCodeForUndefinedContext() {
        return errorCodeForAbsentContext;
    }

    /**
     * Set static information about the context item
     * @param info static information about the context item
     */

    public void setStaticInfo(ContextItemStaticInfo info) {
        staticInfo = info;
    }

    /**
     * Type-check the expression.
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, /*@Nullable*/ ContextItemStaticInfo contextInfo) throws XPathException {
        if (contextInfo.getOptionality() == Optionality.PROHIBITED) {
            visitor.issueWarning("Evaluation will always fail: there is no context item", SaxonErrorCode.SXWN9027, getLocation());
            ErrorExpression ee = new ErrorExpression(
                    "There is no context item",
                    getErrorCodeForUndefinedContext(),
                    absentContextIsTypeError);
            ee.setOriginalExpression(this);
            ExpressionTool.copyLocationInfo(this, ee);
            return ee;
        } else if (contextInfo.getCardinality() != StaticProperty.EXACTLY_ONE) {
            // context item is generalized to context value in 4.0 - but only in restricted circumstances
            return new ContextValueExpression();
        } else {
            staticInfo = contextInfo;
        }
        return this;
    }

    /**
     * Perform optimisation of an expression and its subexpressions.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor         an expression visitor
     * @param contextItemType the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        // In XSLT, we don't catch this error at the typeCheck() phase because it's done one XPath expression
        // at a time. So we repeat the check here.
        if (contextItemType == null) {
            throw new XPathException("The context item is undefined at this point")
                    .withErrorCode(getErrorCodeForUndefinedContext()).withLocation(getLocation())
                    .asTypeErrorIf(absentContextIsTypeError);
        }
        return this;
    }

    /**
     * Determine the item type
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return staticInfo.getItemType();
    }

    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @param contextItemType the static type of the context item
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     */

    @Override
    public UType getStaticUType(UType contextItemType) {
        return contextItemType;
    }


    /**
     * Ask whether the context item may possibly be undefined
     *
     * @return true if it might be undefined
     */

    public boolean isContextPossiblyUndefined() {
        return staticInfo.getOptionality() != Optionality.REQUIRED;
    }

    /**
     * Get the static cardinality
     */

    @Override
    protected int computeCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /**
     * Determine the special properties of this expression
     *
     * @return the value {@link StaticProperty#NO_NODES_NEWLY_CREATED}
     */

    @Override
    protected int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        return p | StaticProperty.NO_NODES_NEWLY_CREATED | StaticProperty.CONTEXT_DOCUMENT_NODESET;
    }

    @Override
    public int getImplementationMethod() {
        return EVALUATE_METHOD;
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        return other instanceof ContextItemExpression;
    }

    /**
     * get HashCode for comparing two expressions
     */

    @Override
    protected int computeHashCode() {
        return "ContextItemExpression".hashCode();
    }

    @Override
    public int getIntrinsicDependencies() {
        return StaticProperty.DEPENDS_ON_CONTEXT_ITEM;
    }


    /**
     * Determine whether the expression can be evaluated without reference to the part of the context
     * document outside the subtree rooted at the context node.
     *
     * @return true if the expression has no dependencies on the context node, or if the only dependencies
     *         on the context node are downward selections using the self, child, descendant, attribute, and namespace
     *         axes.
     */

    @Override
    public boolean isSubtreeExpression() {
        return true;
    }

    @Override
    public int getNetCost() {
        return 0;
    }

    /**
     * Convert this expression to an equivalent XSLT pattern
     *
     * @param config      the Saxon configuration
     * @param firstInPath
     * @return the equivalent pattern
     * @throws net.sf.saxon.trans.XPathException if conversion is not possible
     */
    @Override
    public Pattern toPattern(Configuration config, boolean firstInPath) throws XPathException {
        //return AnchorPattern.getInstance();
        throw new XPathException("Cannot convert the expression '.' to a PathExprP");
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "ContextItemExpr";
    }

    /**
     * Iterate over the value of the expression
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        Item item = context.getContextItem();
        if (item == null) {
            reportAbsentContext(context);
        }
        return SingletonIterator.makeIterator(item);
    }

    /**
     * Evaluate the expression
     */

    @Override
    public Item evaluateItem(XPathContext context) throws XPathException {
        Item item = context.getContextItem();
        if (item == null) {
            reportAbsentContext(context);
        }
        return item;
    }

    public void reportAbsentContext(XPathContext context) throws XPathException {
        if (absentContextIsTypeError) {
            typeError("The context item is absent", getErrorCodeForUndefinedContext(), context);
        } else {
            dynamicError("The context item is absent", getErrorCodeForUndefinedContext(), context);
        }
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     */

    public String toString() {
        return ".";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("dot", this);
        ItemType type = getItemType();
        if (!(type == AnyItemType.INSTANCE)) {
            SequenceType st = SequenceType.one(type);
            destination.emitAttribute("type", st.toAlphaCode());
        }
        if (staticInfo.getOptionality() != Optionality.REQUIRED) {
            destination.emitAttribute("flags", "a");
        }
        destination.endElement();
    }

    @Override
    public String toShortString() {
        return ".";
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new ContextItemElaborator();
    }

    /**
     * Elaborator for the context item expression, "dot".
     */

    public static class ContextItemElaborator extends ItemElaborator {

        public ItemEvaluator elaborateForItem() {
            ContextItemExpression cie = (ContextItemExpression) getExpression();
            if (cie.isContextPossiblyUndefined()) {
                return context -> {
                    Item current = context.getContextItem();
                    if (current == null) {
                        cie.reportAbsentContext(context);
                    }
                    return current;
                };
            } else {
                return XPathContext::getContextItem;
            }
        }

        @Override
        public PullEvaluator elaborateForPull() {
            ContextItemExpression cie = (ContextItemExpression) getExpression();
            if (cie.isContextPossiblyUndefined()) {
                return context -> {
                    Item current = context.getContextItem();
                    if (current == null) {
                        cie.reportAbsentContext(context);
                    }
                    return SingletonIterator.makeIterator(current);
                };
            } else {
                return context -> SingletonIterator.makeIterator(context.getContextItem());
            }
        }
    }
}

