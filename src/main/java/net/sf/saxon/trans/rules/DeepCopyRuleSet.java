////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans.rules;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.ParameterSet;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;

/**
 * The built-in rule set introduced in XSLT 3.0, which performs a deep copy of any unmatched node.
 */
public class DeepCopyRuleSet implements BuiltInRuleSet {

    private static final DeepCopyRuleSet INSTANCE_WITH_COPY_NS = new DeepCopyRuleSet(true);
    private static final DeepCopyRuleSet INSTANCE_WITH_NO_COPY_NS = new DeepCopyRuleSet(false);

    private boolean copyNamespaces;
    /**
     * Get the singleton instance of this class
     *
     * @return the singleton instance
     */

    public static DeepCopyRuleSet getInstance(boolean copyNamespaces) {
        return copyNamespaces ? INSTANCE_WITH_COPY_NS : INSTANCE_WITH_NO_COPY_NS;
    }

    private DeepCopyRuleSet(boolean copyNamespaces) {
        this.copyNamespaces = copyNamespaces;
    }

    /**
     * Perform the built-in template action for a given node.
     * @param item the item to be processed using the built-in action
     * @param parameters   the parameters supplied to apply-templates
     * @param tunnelParams the tunnel parameters to be passed through
     * @param out the destination for output
     * @param context      the dynamic evaluation context
     * @param locationId   location of the instruction (apply-templates, apply-imports etc) that caused
     */

    @Override
    public void process(Item item, ParameterSet parameters,
                        ParameterSet tunnelParams, Outputter out, XPathContext context,
                        Location locationId) throws XPathException {
        if (item instanceof NodeInfo) {
            NodeInfo node = (NodeInfo) item;
            switch (node.getNodeKind()) {
                case Type.DOCUMENT:
                case Type.ELEMENT: {
                    // TODO: fast path for TinyTree
                    if (out.getSystemId() == null) {
                        out.setSystemId(node.getBaseURI());
                    }
                    int option = (copyNamespaces ? CopyOptions.ALL_NAMESPACES : 0) | CopyOptions.TYPE_ANNOTATIONS;
                    Navigator.copy(node, out, option, locationId);
                    return;
                }
                case Type.TEXT:
                    out.characters(item.getUnicodeStringValue(), locationId, ReceiverOption.NONE);
                    return;

                case Type.COMMENT:
                    out.comment(node.getUnicodeStringValue(), locationId, ReceiverOption.NONE);
                    return;

                case Type.PROCESSING_INSTRUCTION:
                    out.processingInstruction(node.getLocalPart(), node.getUnicodeStringValue(), locationId, ReceiverOption.NONE);
                    return;

                case Type.ATTRIBUTE:
                    out.attribute(NameOfNode.makeName(node), (SimpleType) node.getSchemaType(), node.getStringValue(),
                                                    locationId, ReceiverOption.NONE);
                    return;
                    
                case Type.NAMESPACE:
                    out.namespace(node.getLocalPart(), NamespaceUri.of(node.getStringValue()), ReceiverOption.NONE);
                    return;

                default:
            }
        } else {
            out.append(item, locationId, ReceiverOption.NONE);
        }

    }

    /**
     * Get the default action for unmatched nodes
     *
     * @param nodeKind the node kind
     * @return the default action for unmatched element nodes: one of DEEP_COPY, APPLY_TEMPLATES, SKIP, FAIL
     */
    @Override
    public BuiltInRules[] getActionForParentNodes(int nodeKind) {
        return new BuiltInRules[]{BuiltInRules.DEEP_COPY};
    }

    /**
     * Identify this built-in rule set
     * @return "deep-copy"
     */

    @Override
    public String getName() {
        return "deep-copy";
    }
}
