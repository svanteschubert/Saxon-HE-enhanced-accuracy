////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.StringEvaluator;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.str.StringView;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyElementImpl;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.StringValue;


/**
 * An AttributeGetter is an expression that returns the value of a specific attribute
 * of the context item, provided that it is an untyped element node. That is,
 * it represents the expression data(./@name) assuming that all data is untyped.
 */

public final class AttributeGetter extends Expression {


    private final FingerprintedQName attributeName;


    public AttributeGetter(FingerprintedQName attributeName) {
        this.attributeName = attributeName;
    }

    public FingerprintedQName getAttributeName() {
        return attributeName;
    }


    @Override
    public ItemType getItemType() {
        return BuiltInAtomicType.UNTYPED_ATOMIC;
    }

    @Override
    protected int computeCardinality() {
        return StaticProperty.ALLOWS_ZERO_OR_ONE;
    }

    @Override
    protected int computeSpecialProperties() {
        return StaticProperty.NO_NODES_NEWLY_CREATED;
    }

    @Override
    public int getIntrinsicDependencies() {
        return StaticProperty.DEPENDS_ON_CONTEXT_ITEM;
    }

    @Override
    public AttributeGetter copy(RebindingMap rebindings) {
        return new AttributeGetter(attributeName);
    }

    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        if (pathMapNodeSet == null) {
            ContextItemExpression cie = new ContextItemExpression();
            pathMapNodeSet = new PathMap.PathMapNodeSet(pathMap.makeNewRoot(cie));
        }
        return pathMapNodeSet.createArc(AxisInfo.ATTRIBUTE, new NameTest(Type.ATTRIBUTE, attributeName, getConfiguration().getNamePool()));
    }

    @Override
    public int getImplementationMethod() {
        return EVALUATE_METHOD;
    }

    @Override
    public Item evaluateItem(XPathContext context) throws XPathException {
        Item item = context.getContextItem();
        if (item instanceof TinyElementImpl) {
            // fast path
            String val = ((TinyElementImpl) item).getAttributeValue(attributeName.getFingerprint());
            return val == null ? null : StringValue.makeUntypedAtomic(StringView.tidy(val));
        }
        if (item == null) {
            // This doesn't actually happen, we don't create an AttributeGetter unless we know statically
            dynamicError("The context item for @" + attributeName.getDisplayName() +
                                 " is absent", "XPDY0002", context);
        }
        if (!(item instanceof NodeInfo)) {
            typeError("The context item for @" + attributeName.getDisplayName() +
                                 " is not a node", "XPDY0002", context);
        }
        assert item instanceof NodeInfo;
        NodeInfo node = (NodeInfo) item;
        if (node.getNodeKind() == Type.ELEMENT) {
            String val = node.getAttributeValue(attributeName.getNamespaceUri(), attributeName.getLocalPart());
            return val == null ? null : StringValue.makeUntypedAtomic(StringView.tidy(val));
        } else {
            return null;
        }
    }

    @Override
    public UnicodeString evaluateAsString(XPathContext context) throws XPathException {
        Item item = context.getContextItem();
        if (item instanceof TinyElementImpl) {
            // fast path
            String val = ((TinyElementImpl) item).getAttributeValue(attributeName.getFingerprint());
            return val == null ? EmptyUnicodeString.getInstance() : StringView.tidy(val);
        }
        if (item == null) {
            // This doesn't actually happen, we don't create an AttributeGetter unless we know statically
            dynamicError("The context item for @" + attributeName.getDisplayName() +
                                 " is absent", "XPDY0002", context);
        }
        if (!(item instanceof NodeInfo)) {
            typeError("The context item for @" + attributeName.getDisplayName() +
                              " is not a node", "XPDY0002", context);
        }
        assert item instanceof NodeInfo;
        NodeInfo node = (NodeInfo) item;
        if (node.getNodeKind() == Type.ELEMENT) {
            String val = node.getAttributeValue(attributeName.getNamespaceUri(), attributeName.getLocalPart());
            if (val != null) {
                return StringView.tidy(val);
            }
        }
        return EmptyUnicodeString.getInstance();
    }

    @Override
    public String getExpressionName() {
        return "attGetter";
    }

    @Override
    public String toShortString() {
        return "@" + attributeName.getDisplayName();
    }

    public String toString() {
        return "data(@" + attributeName.getDisplayName() + ")";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof AttributeGetter && ((AttributeGetter)obj).attributeName.equals(attributeName);
    }

    @Override
    protected int computeHashCode() {
        return 83571 ^ attributeName.hashCode();
    }

    @Override
    public void export(ExpressionPresenter out) {
        out.startElement("attVal", this);
        out.emitAttribute("name", attributeName.getStructuredQName());
        out.endElement();
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new AttributeGetterElaborator();
    }

    /**
     * Elaborator for an AttributeGetter expression (which gets a named attribute of the context item
     * and returns its value as an untyped atomic value)
     */

    public static class AttributeGetterElaborator extends ItemElaborator {

        public ItemEvaluator elaborateForItem() {
            final AttributeGetter exp = (AttributeGetter) getExpression();
            final int fingerprint = exp.getAttributeName().getFingerprint();
            final NamespaceUri uri = exp.getAttributeName().getNamespaceUri();
            final String local = exp.getAttributeName().getLocalPart();
            return context -> {
                Item item = context.getContextItem();
                if (item instanceof TinyElementImpl) {
                    // fast path
                    String val = ((TinyElementImpl) item).getAttributeValue(fingerprint);
                    return val == null ? null : new StringValue(val, BuiltInAtomicType.UNTYPED_ATOMIC);
                }
                assert item instanceof NodeInfo;
                NodeInfo node = (NodeInfo) item;
                if (node.getNodeKind() == Type.ELEMENT) {
                    String val = node.getAttributeValue(uri, local);
                    if (val != null) {
                        return new StringValue(val, BuiltInAtomicType.UNTYPED_ATOMIC);
                    }
                }
                return null;
            };
        }

        /**
         * Get a function that evaluates the underlying expression in the form of
         * a Java string, this being the result of applying fn:string() to the result
         * of the expression; except that if the result of the expression is an empty
         * sequence, the result is "" if {@code zeroLengthWhenAbsent} is set, or null
         * otherwise.
         *
         * @param zeroLengthWhenAbsent if true, then when the result of the expression
         *                             is an empty sequence, the result of the StringEvaluator
         *                             should be a zero-length string. If false, the return value
         *                             should be null.
         * @return an evaluator for the expression that returns a string.
         */
        @Override
        public StringEvaluator elaborateForString(boolean zeroLengthWhenAbsent) {
            final AttributeGetter expr = (AttributeGetter) getExpression();
            final int fingerprint = expr.getAttributeName().getFingerprint();
            final NamespaceUri uri = expr.getAttributeName().getNamespaceUri();
            final String local = expr.getAttributeName().getLocalPart();
            return context -> {
                Item item = context.getContextItem();
                if (item instanceof TinyElementImpl) {
                    // fast path
                    String val = ((TinyElementImpl) item).getAttributeValue(fingerprint);
                    return handlePossiblyNullString(val, zeroLengthWhenAbsent);
                }
                if (!(item instanceof NodeInfo)) {
                    expr.typeError("The context item for @" + expr.getAttributeName().getDisplayName() +
                                           " is not a node", "XPDY0002", context);
                }
                assert item instanceof NodeInfo;
                NodeInfo node = (NodeInfo) item;
                if (node.getNodeKind() == Type.ELEMENT) {
                    String val = node.getAttributeValue(uri, local);
                    return handlePossiblyNullString(val, zeroLengthWhenAbsent);
                }
                return zeroLengthWhenAbsent ? "" : null;
            };
        }
    }
}

