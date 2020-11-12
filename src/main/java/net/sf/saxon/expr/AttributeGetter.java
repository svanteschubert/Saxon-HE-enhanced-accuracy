////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.FingerprintedQName;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyElementImpl;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.UntypedAtomicValue;


/**
 * An AttributeGetter is an expression that returns the value of a specific attribute
 * of the context item, provided that it is an untyped element node. That is,
 * it represents the expression data(./@name) assuming that all data is untyped.
 */

public final class AttributeGetter extends Expression {

    //public static final int CHECK_CONTEXT_ITEM_PRESENT = 1;
    public static final int CHECK_CONTEXT_ITEM_IS_NODE = 2;

    private FingerprintedQName attributeName;
    private int requiredChecks = CHECK_CONTEXT_ITEM_IS_NODE;

    public AttributeGetter(FingerprintedQName attributeName) {
        this.attributeName = attributeName;
    }

    public FingerprintedQName getAttributeName() {
        return attributeName;
    }

    /**
     * Say what run-time checks are needed. (This information is only used when generating bytecode)
     * @param checks the run-time checks that need to be performed
     */

    public void setRequiredChecks(int checks) {
        requiredChecks = checks;
    }

    /**
     * Ask what run-time checks are needed. (This information is only used when generating bytecode)
     * @return the run-time checks that need to be performed
     */

    public int getRequiredChecks() {
        return requiredChecks;
    }

    @Override
    public ItemType getItemType() {
        return BuiltInAtomicType.UNTYPED_ATOMIC;
    }

    @Override
    public int computeCardinality() {
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
        AttributeGetter ag2 = new AttributeGetter(attributeName);
        ag2.setRequiredChecks(requiredChecks);
        return ag2;
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
            return val == null ? null : new UntypedAtomicValue(val);
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
            String val = node.getAttributeValue(attributeName.getURI(), attributeName.getLocalPart());
            return val == null ? null : new UntypedAtomicValue(val);
        } else {
            return null;
        }
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
    public int computeHashCode() {
        return 83571 ^ attributeName.hashCode();
    }

    @Override
    public void export(ExpressionPresenter out) {
        out.startElement("attVal", this);
        out.emitAttribute("name", attributeName.getStructuredQName());
        out.emitAttribute("chk", "" + requiredChecks);
        out.endElement();
    }
}

