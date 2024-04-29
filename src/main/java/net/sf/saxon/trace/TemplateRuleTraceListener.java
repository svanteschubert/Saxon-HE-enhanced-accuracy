////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.expr.instruct.TemplateRule;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.Err;

/**
 * A trace listener for XSLT that only handles invocation of template rules; enabled
 * using saxon:trace="yes" on the xsl:mode declaration
 */

public class TemplateRuleTraceListener {

    private int depth = 0;
    private Logger logger;

    public TemplateRuleTraceListener(Logger logger) {
        this.logger = logger;
    }

    public void enter(String instName, Location instLoc, Item item, TemplateRule rule)  {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            builder.append(' ');
        }
        depth++;
        builder.append(instName).append(" at ").append(Err.show(instLoc));
        builder.append(" to ").append(item.toShortString());
        if (item instanceof NodeInfo && ((NodeInfo) item).getLineNumber() != -1) {
            builder.append(" at ").append(Err.abbreviateURI(((NodeInfo) item).getBaseURI()))
                    .append("#").append(((NodeInfo) item).getLineNumber());
        }
        builder.append(" using ");
        if (rule == null) {
            builder.append("built-in rule");
        } else {
            builder.append("rule at ").append(Err.show(rule));
        }
        String message = builder.toString().replace(" .../", " ");
        logger.info(message);
    }

    public void leave() {
        depth--;
    }
}


