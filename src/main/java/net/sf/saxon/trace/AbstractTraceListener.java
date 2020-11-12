////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.Controller;
import net.sf.saxon.Version;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.Instruction;
import net.sf.saxon.expr.parser.CodeInjector;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.Mode;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

import java.util.Map;

/**
 * This is the standard trace listener used when the -T option is specified on the command line.
 * There are two variants, represented by subclasses: one for XSLT, and one for XQuery. The two variants
 * differ in that they present the trace output in terms of constructs used in the relevant host language.
 */

public abstract class AbstractTraceListener extends StandardDiagnostics implements TraceListener {
    protected int indent = 0;
    private int detail = 2; // none=0; low=1; normal=2; high=3
    protected Logger out = new StandardLogger();
    /*@NotNull*/ private static StringBuffer spaceBuffer = new StringBuffer("                ");

    /**
     * Get the associated CodeInjector to be used at compile time to generate the tracing calls
     */

    public CodeInjector getCodeInjector() {
        return new TraceCodeInjector();
    }

    /**
     * Set the level of detail required
     * @param level 0=none, 1=low (function and template calls), 2=normal (instructions), 3=high (expressions)
     */

    public void setLevelOfDetail(int level) {
        this.detail = level;
    }

    /**
     * Called at start
     */

    @Override
    public void open(Controller controller) {
        out.info("<trace " +
                "saxon-version=\"" + Version.getProductVersion() + "\" " +
                getOpeningAttributes() + '>');
        indent++;
    }

    protected abstract String getOpeningAttributes();

    /**
     * Called at end
     */

    @Override
    public void close() {
        indent--;
        out.info("</trace>");
    }

    /**
     * Called when an instruction in the stylesheet gets processed
     */

    @Override
    public void enter(Traceable info, Map<String, Object> properties, XPathContext context) {
        if (isApplicable(info)) {
            Location loc = info.getLocation();
            String tag = tag(info);
            String file = abbreviateLocationURI(loc.getSystemId());
            StringBuilder msg = new StringBuilder(AbstractTraceListener.spaces(indent) + '<' + tag);
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof StructuredQName) {
                    val = ((StructuredQName)val).getDisplayName();
                } else if (val instanceof StringValue) {
                    val = ((StringValue)val).getStringValue();
                }
                if (val != null) {
                    msg.append(' ').append(entry.getKey()).append("=\"").append(escape(val.toString())).append('"');
                }
            }

            msg.append(" line=\"").append(loc.getLineNumber()).append('"');

            int col = loc.getColumnNumber();
            if (col >= 0) {
                msg.append(" column=\"").append(loc.getColumnNumber()).append('"');
            }

            msg.append(" module=\"").append(escape(file)).append('"');
            msg.append(">");
            out.info(msg.toString());
            indent++;
        }
    }

    /**
     * Escape a string for XML output (in an attribute delimited by double quotes).
     * This method also collapses whitespace (since the value may be an XPath expression that
     * was originally written over several lines).
     */

    public String escape(/*@Nullable*/ String in) {
        if (in == null) {
            return "";
        }
        CharSequence collapsed = Whitespace.collapseWhitespace(in);
        FastStringBuffer sb = new FastStringBuffer(collapsed.length() + 10);
        for (int i = 0; i < collapsed.length(); i++) {
            char c = collapsed.charAt(i);
            if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '&') {
                sb.append("&amp;");
            } else if (c == '\"') {
                sb.append("&#34;");
            } else if (c == '\n') {
                sb.append("&#xA;");
            } else if (c == '\r') {
                sb.append("&#xD;");
            } else if (c == '\t') {
                sb.append("&#x9;");
            } else {
                sb.cat(c);
            }
        }
        return sb.toString();
    }

    /**
     * Called after an instruction of the stylesheet got processed
     * @param info
     */

    @Override
    public void leave(Traceable info) {
        if (isApplicable(info)) {
            String tag = tag(info);
            indent--;
            out.info(AbstractTraceListener.spaces(indent) + "</" + tag + '>');
        }
    }

    protected boolean isApplicable(Traceable info) {
        return level(info) <= detail;
    }

    protected abstract String tag(Traceable info);

    protected int level(Traceable info) {
        if (info instanceof TraceableComponent) {
            return 1;
        } if (info instanceof Instruction) {
            return 2;
        } else {
            return 3;
        }
    }

    /**
     * Called when an item becomes the context item
     */

    @Override
    public void startCurrentItem(Item item) {
        if (item instanceof NodeInfo && detail > 0) {
            NodeInfo curr = (NodeInfo) item;
            out.info(AbstractTraceListener.spaces(indent) + "<source node=\"" + Navigator.getPath(curr)
                    + "\" line=\"" + curr.getLineNumber()
                    + "\" file=\"" + abbreviateLocationURI(curr.getSystemId())
                    + "\">");
        }
        indent++;
    }

    /**
     * Called after a node of the source tree got processed
     */

    @Override
    public void endCurrentItem(Item item) {
        indent--;
        if (item instanceof NodeInfo && detail > 0) {
            NodeInfo curr = (NodeInfo) item;
            out.info(AbstractTraceListener.spaces(indent) + "</source><!-- " +
                    Navigator.getPath(curr) + " -->");
        }
    }

    /**
     * Get n spaces
     */

    protected static String spaces(int n) {
        while (spaceBuffer.length() < n) {
            spaceBuffer.append(AbstractTraceListener.spaceBuffer);
        }
        return spaceBuffer.substring(0, n);
    }

    /**
     * Set the output destination (default is System.err)
     *
     * @param stream the output destination for tracing output
     */

    @Override
    public void setOutputDestination(Logger stream) {
        out = stream;
    }

    /**
     * Get the output destination
     */

    public Logger getOutputDestination() {
        return out;
    }

    /**
     * Method called when a rule search has completed.
     *  @param rule the rule (or possible built-in ruleset) that has been selected
     * @param mode the mode in operation
     * @param item the item that was checked against
     */
    @Override
    public void endRuleSearch(Object rule, Mode mode, Item item) {
        // do nothing
    }

    /**
     * Method called when a search for a template rule is about to start
     */
    @Override
    public void startRuleSearch() {
        // do nothing
    }
}

