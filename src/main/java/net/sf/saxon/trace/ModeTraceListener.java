////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.TemplateRule;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.Item;

import java.util.Stack;

/**
 * A trace listener for XSLT that only handles invocation of template rules; enabled
 * using saxon:trace="yes" on the xsl:mode declaration
 */

public class ModeTraceListener extends AbstractTraceListener {

    private Stack<Item> stack = new Stack<>();

    /**
     * Generate attributes to be included in the opening trace element
     */

    @Override
    protected String getOpeningAttributes() {
        return "xmlns:xsl=\"" + NamespaceConstant.XSLT + '\"';
    }

    @Override
    public void startCurrentItem(Item item) {
        if (stack.empty() || stack.peek() != item) {
            super.startCurrentItem(item);
            stack.push(item);
        }
    }

    @Override
    public void endCurrentItem(Item item) {
        if (stack.peek() == item) {
            super.endCurrentItem(item);
            stack.pop();
        }
    }

    /**
     * Called when an instruction in the stylesheet gets processed
     */

    public void enter(/*@NotNull*/ Traceable info, XPathContext context) {
        if (info instanceof TemplateRule) {
            String file = abbreviateLocationURI(info.getLocation().getSystemId());
            String msg = AbstractTraceListener.spaces(indent)
                    + "<rule match=\""
                    + escape(((TemplateRule) info).getMatchPattern().toString()) + '"'
                    + " line=\"" + info.getLocation().getLineNumber() + '"'
                    + " module=\"" + escape(file) + '"'
                    + '>';
            out.info(msg);
            indent++;
        } 
    }

    @Override
    public void leave(Traceable info) {
        if (info instanceof TemplateRule) {
            indent--;
            out.info(AbstractTraceListener.spaces(indent) + "</rule>");
        }
    }

    @Override
    protected String tag(Traceable info) {
        return "";
    }
}


