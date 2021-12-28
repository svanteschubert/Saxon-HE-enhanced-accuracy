////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans.rules;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.ParameterSet;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XmlProcessingIncident;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Navigator;


/**
 * A set of built-in template rules that performs the same action as an underlying set of rules,
 * but with the addition of a warning message saying that no user-defined template rules was found.
 * <p>XSLT 3.0 feature</p>
 */
public class RuleSetWithWarnings implements BuiltInRuleSet {


    private BuiltInRuleSet baseRuleSet;

    public RuleSetWithWarnings(BuiltInRuleSet baseRuleSet) {
        this.baseRuleSet = baseRuleSet;
    }

    public BuiltInRuleSet getBaseRuleSet() {
        return baseRuleSet;
    }

    /**
     * Perform the built-in template action for a given item.
     * @param item         the item to be matched
     * @param parameters   the parameters supplied to apply-templates
     * @param tunnelParams the tunnel parameters to be passed through
     * @param output the destination for the result
     * @param context      the dynamic evaluation context
     * @param locationId   location of the instruction (apply-templates, apply-imports etc) that caused
     */

    @Override
    public void process(Item item, ParameterSet parameters,
                        ParameterSet tunnelParams, Outputter output, XPathContext context,
                        Location locationId) throws XPathException {

        outputWarning(item, context);
        baseRuleSet.process(item, parameters, tunnelParams, output, context, locationId);

    }


    /**
     * Identify this built-in rule set
     *
     * @return the base name plus "with warnings"
     */

    @Override
    public String getName() {
        return baseRuleSet + " with warnings";
    }

    public void outputWarning(Item item, XPathContext context) {
        String id = item instanceof NodeInfo ?
                "the node " + Navigator.getPath((NodeInfo) item) :
                "the atomic value " + item.getStringValue();
        XmlProcessingIncident warning = new XmlProcessingIncident("No user-defined template rule matches " + id, "XTDE0555").asWarning();
        context.getController().getErrorReporter().report(warning);
    }

    /**
     * Get the default action for unmatched nodes
     *
     * @param nodeKind the node kind
     * @return the default action for unmatched element nodes: one of DEEP_COPY, APPLY_TEMPLATES, DEEP_SKIP, FAIL
     */
    @Override
    public int[] getActionForParentNodes(int nodeKind) {
        return baseRuleSet.getActionForParentNodes(nodeKind);
    }
}
