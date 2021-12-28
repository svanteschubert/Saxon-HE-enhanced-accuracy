////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans.rules;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.instruct.ParameterSet;
import net.sf.saxon.expr.instruct.TailCall;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.ma.arrays.ArrayFunctionSet;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;

/**
 * A built-in set of template rules that ignores the current node and does an apply-templates
 * to its children.
 */
public class ShallowSkipRuleSet implements BuiltInRuleSet {

    private static ShallowSkipRuleSet THE_INSTANCE = new ShallowSkipRuleSet();

    /**
     * Get the singleton instance of this class
     *
     * @return the singleton instance
     */

    public static ShallowSkipRuleSet getInstance() {
        return THE_INSTANCE;
    }

    private ShallowSkipRuleSet() {
    }

    /**
     * Perform the built-in template action for a given item.
     *
     * @param item         the item to be processed
     * @param parameters   the parameters supplied to apply-templates
     * @param tunnelParams the tunnel parameters to be passed through
     * @param output the destination for the result
     * @param context      the dynamic evaluation context
     * @param locationId   location of the instruction (apply-templates, apply-imports etc) that caused
     *                     the built-in template to be invoked
     * @throws XPathException if any dynamic error occurs
     */

    @Override
    public void process(Item item, ParameterSet parameters,
                        ParameterSet tunnelParams, /*@NotNull*/ Outputter output, XPathContext context,
                        Location locationId) throws XPathException {
        if (item instanceof NodeInfo) {
            NodeInfo node = (NodeInfo) item;
            switch (node.getNodeKind()) {
                case Type.ELEMENT: {
                    XPathContextMajor c2 = context.newContext();
                    c2.setOrigin(this);
                    c2.trackFocus(node.iterateAxis(AxisInfo.ATTRIBUTE));
                    c2.setCurrentComponent(c2.getCurrentMode());  // Bug 3508
                    TailCall tc = c2.getCurrentMode().getActor().applyTemplates(parameters, tunnelParams, null, output, c2, locationId);
                    while (tc != null) {
                        tc = tc.processLeavingTail();
                    }
                }   // fall through!
                case Type.DOCUMENT: {
                    XPathContextMajor c2 = context.newContext();
                    c2.setOrigin(this);
                    c2.trackFocus(node.iterateAxis(AxisInfo.CHILD));
                    c2.setCurrentComponent(c2.getCurrentMode());  // Bug 3508
                    TailCall tc = c2.getCurrentMode().getActor().applyTemplates(parameters, tunnelParams, null, output, c2, locationId);
                    while (tc != null) {
                        tc = tc.processLeavingTail();
                    }
                    return;
                }
                case Type.TEXT:
                case Type.ATTRIBUTE:
                case Type.COMMENT:
                case Type.PROCESSING_INSTRUCTION:
                case Type.NAMESPACE:
                    // no action
            }
        } else if (item instanceof ArrayItem) {
            Sequence seq = ArrayFunctionSet.ArrayToSequence.toSequence((ArrayItem) item);
            SequenceIterator members = seq.iterate();
            XPathContextMajor c2 = context.newContext();
            c2.setOrigin(this);
            c2.trackFocus(members);
            c2.setCurrentComponent(c2.getCurrentMode());  // Bug 3508
            TailCall tc = c2.getCurrentMode().getActor().applyTemplates(parameters, tunnelParams, null, output, c2, locationId);
            while (tc != null) {
                tc = tc.processLeavingTail();
            }
        } else {
            // no action (e.g. for atomic values and function items
        }
    }


    /**
     * Identify this built-in rule set
     *
     * @return "shallow-skip"
     */

    @Override
    public String getName() {
        return "shallow-skip";
    }

    /**
     * Get the default action for unmatched nodes
     *
     * @param nodeKind the node kind
     * @return the default action for unmatched nodes: one of DEEP_COPY, APPLY_TEMPLATES, DEEP_SKIP, FAIL
     */
    @Override
    public int[] getActionForParentNodes(int nodeKind) {
        return new int[]{APPLY_TEMPLATES_TO_ATTRIBUTES, APPLY_TEMPLATES_TO_CHILDREN};
    }
}

