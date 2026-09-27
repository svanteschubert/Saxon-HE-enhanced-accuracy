////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans.rules;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.instruct.ParameterSet;
import net.sf.saxon.expr.instruct.TailCall;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.nodetest.AnyGNode;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.type.AnyType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.Untyped;

/**
 * The built-in rule set introduced in XSLT 3.0, which is effectively an identity template.
 */
public class ShallowCopyRuleSet implements BuiltInRuleSet {

    private static final ShallowCopyRuleSet INSTANCE_WITH_COPY_NS = new ShallowCopyRuleSet(true);
    private static final ShallowCopyRuleSet INSTANCE_WITH_NO_COPY_NS = new ShallowCopyRuleSet(false);

    /**
     * Get the singleton instance of this class
     *
     * @return the singleton instance
     */

    public static ShallowCopyRuleSet getInstance(boolean copyNamespaces) {
        return copyNamespaces ? INSTANCE_WITH_COPY_NS : INSTANCE_WITH_NO_COPY_NS;
    }

    private final boolean copyNamespaces;

    protected ShallowCopyRuleSet(boolean copyNamespaces) {
        this.copyNamespaces = copyNamespaces;
    }

    /**
     * Perform the built-in template action for a given node.
     * @param item the item to be processed by this built-in rule
     * @param parameters   the parameters supplied to apply-templates
     * @param tunnelParams the tunnel parameters to be passed through
     * @param out  the destination for output
     * @param context      the dynamic evaluation context
     * @param locationId   location of the instruction (apply-templates, apply-imports etc) that caused
     */

    @Override
    public void process(Item item, ParameterSet parameters,
                        ParameterSet tunnelParams, Outputter out, XPathContext context,
                        Location locationId) throws XPathException {
        if (item instanceof NodeInfo node) {
            switch (node.getNodeKind()) {
                case Type.DOCUMENT -> {
                    PipelineConfiguration pipe = out.getPipelineConfiguration();
                    if (out.getSystemId() == null) {
                        out.setSystemId(node.getBaseURI());
                    }
                    out.startDocument(ReceiverOption.NONE);
                    XPathContextMajor c2 = context.newContext();
                    c2.setOrigin(this);
                    c2.trackFocus(node.iterateChildAxis(AnyGNode.TEST));
                    c2.setCurrentComponent(c2.getCurrentMode());  // Bug 3508
                    pipe.setXPathContext(c2);
                    TailCall tc = context.getCurrentMode().getActor().applyTemplates(parameters, tunnelParams, null, out, c2, locationId);
                    while (tc != null) {
                        tc = tc.processLeavingTail();
                    }
                    out.endDocument();
                    pipe.setXPathContext(context);
                }
                case Type.ELEMENT -> {
                    boolean schemaAware = context.getController().getExecutable().isSchemaAware();
                    PipelineConfiguration pipe = out.getPipelineConfiguration();
                    if (out.getSystemId() == null) {
                        out.setSystemId(node.getBaseURI());
                    }
                    NodeName fqn = NameOfNode.makeName(node);
                    out.startElement(fqn, schemaAware ? AnyType.INSTANCE : Untyped.INSTANCE, locationId, ReceiverOption.NONE);
                    if (copyNamespaces) {
                        for (NamespaceBinding ns : node.getAllNamespaces()) {
                            out.namespace(ns.getPrefix(), ns.getNamespaceUri(), ReceiverOption.NONE);
                        }
                    }
                    XPathContextMajor c2 = context.newContext();
                    c2.setCurrentComponent(c2.getCurrentMode());  // Bug 3508
                    pipe.setXPathContext(c2);

                    // apply-templates to all attributes
                    SequenceIterator attributes = node.iterateAttributeAxis(AnyGNode.TEST);
                    if (attributes != EmptyIterator.INSTANCE) {
                        c2.setOrigin(this);
                        c2.trackFocus(attributes);
                        TailCall tc = c2.getCurrentMode().getActor().applyTemplates(parameters, tunnelParams, null, out, c2, locationId);
                        while (tc != null) {
                            tc = tc.processLeavingTail();
                        }
                    }

                    // apply-templates to all children
                    if (node.hasChildNodes()) {
                        c2.trackFocus(node.iterateChildAxis(AnyGNode.TEST));
                        TailCall tc = c2.getCurrentMode().getActor().applyTemplates(parameters, tunnelParams, null, out, c2, locationId);
                        while (tc != null) {
                            tc = tc.processLeavingTail();
                        }
                    }
                    out.endElement();
                    pipe.setXPathContext(context);
                }
                case Type.TEXT ->
                        out.characters(node.getUnicodeStringValue(), locationId, ReceiverOption.NONE);
                case Type.COMMENT ->
                        out.comment(node.getUnicodeStringValue(), locationId, ReceiverOption.NONE);
                case Type.PROCESSING_INSTRUCTION ->
                        out.processingInstruction(node.getLocalPart(), node.getUnicodeStringValue(),
                                                  locationId, ReceiverOption.NONE);
                case Type.ATTRIBUTE ->
                        out.attribute(NameOfNode.makeName(node), (SimpleType) node.getSchemaType(), node.getStringValue(),
                                                 locationId, ReceiverOption.NONE);
                case Type.NAMESPACE ->
                        out.namespace(node.getLocalPart(), NamespaceUri.of(node.getStringValue()), ReceiverOption.NONE);
                default -> {
                }
            }
        } else {
            out.append(item, locationId, ReceiverOption.NONE);
        }

    }


    /**
     * Identify this built-in rule set
     *
     * @return "shallow-copy"
     */

    @Override
    public String getName() {
        return "shallow-copy";
    }

    /**
     * Get the default action for unmatched nodes
     *
     * @param nodeKind the node kind
     * @return the default action for unmatched nodes: one of DEEP_COPY, APPLY_TEMPLATES, DEEP_SKIP, FAIL
     */
    @Override
    public BuiltInRules[] getActionForParentNodes(int nodeKind) {
        return new BuiltInRules[]{
                BuiltInRules.SHALLOW_COPY,
                BuiltInRules.APPLY_TEMPLATES_TO_ATTRIBUTES,
                BuiltInRules.APPLY_TEMPLATES_TO_CHILDREN};
    }
}
