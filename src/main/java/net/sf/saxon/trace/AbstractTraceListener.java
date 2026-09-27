////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.Controller;
import net.sf.saxon.Version;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.ApplyTemplates;
import net.sf.saxon.expr.instruct.CallTemplate;
import net.sf.saxon.expr.parser.XPathParser;
import net.sf.saxon.functions.TransformFn;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.lib.StandardDiagnostics;
import net.sf.saxon.lib.StandardLogger;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.Mode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.AttributeLocation;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

import java.util.Map;
import java.util.Stack;

/**
 * This is the standard trace listener used when the -T option is specified on the command line.
 * There are two variants, represented by subclasses: one for XSLT, and one for XQuery. The two variants
 * differ in that they present the trace output in terms of constructs used in the relevant host language.
 */

public abstract class AbstractTraceListener extends StandardDiagnostics implements TraceListener {
    protected int indent = 0;
    protected int detail = TraceLevel.NORMAL;
    protected Logger out = StandardLogger.makeLogger();
    private Stack<Object> stack = new Stack<>();
    /*@NotNull*/ private static final StringBuilder spaceBuffer = new StringBuilder("                ");

    /**
     * Set the level of detail required
     * @param level One of {@link TraceLevel#NONE},  {@link TraceLevel#LOW} (function and template calls),
     *              {@link TraceLevel#NORMAL} (instructions), {@link TraceLevel#HIGH} (expressions)
     */

    public void setLevelOfDetail(int level) {
        this.detail = level;
    }

    /**
     * Called at start
     */

    @Override
    public void open(Controller controller) {
        out.info(spaces(indent++) + "<trace " +
                         "saxon-version=\"" + Version.getProductVersion() + "\" " +
                         getOpeningAttributes() + '>');
    }

    protected String getOpeningAttributes() {
        return "";
    }

    /**
     * Called at end
     */

    @Override
    public void close() {
        out.info(spaces(indent--) + "</trace>");
    }

    /**
     * Called when an instruction in the stylesheet gets processed
     */

    @Override
    public void enter(Traceable info, Map<String, Object> properties, XPathContext context) {
        if (isApplicable(info)) {
            stack.push(info);
            Location loc = getLocation(info);
            String file = abbreviateLocationURI(loc.getSystemId());
            String elementTag = tag(info);

            StringBuilder msg = new StringBuilder(AbstractTraceListener.spaces(indent) + '<' + elementTag);

            if (info instanceof Expression && !((Expression) info).isInstruction() && !properties.containsKey("expr")) {
                properties.put("expr", ((Expression) info).toShortString());
            }
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof StructuredQName) {
                    val = ((StructuredQName)val).getDisplayName();
                } else if (val instanceof StringValue) {
                    val = ((StringValue)val).getUnicodeStringValue();
                }
                if (val != null) {
                    msg.append(' ').append(entry.getKey()).append("=").append(escape(val.toString()));
                }
            }

            int line = loc.getLineNumber();
            if (line >= 0) {
                msg.append(" line=\"").append(loc.getLineNumber()).append('"');
            }

            int col = loc.getColumnNumber();
            if (col >= 0) {
                msg.append(" column=\"").append(loc.getColumnNumber()).append('"');
            }

            msg.append(" module=").append(escape(file));
            msg.append(">");
            out.info(msg.toString());
            indent++;
        }
    }


    /**
     * Get the location information for the traceble expression or instruction.
     * This method adjusts the location for XPath expressions contained in attributes
     * of XSLT instructions, to give the location of the containing element, which
     * supplies a simple line number and column number.
     *
     * @param info the traceable whose location is required
     * @return a sanitised Location object
     */
    public static Location getLocation(Traceable info) {
        Location rawLocation = info.getLocation();
        if (rawLocation instanceof XPathParser.NestedLocation) {
            Location container = ((XPathParser.NestedLocation) rawLocation).getContainingLocation();
            if (container instanceof AttributeLocation) {
                return container;
            }
        }
        return rawLocation;
    }

    /**
     * Escape a string for XML output (in an attribute delimited by double quotes).
     * This method also collapses whitespace (since the value may be an XPath expression that
     * was originally written over several lines).
     * @param in the input string
     * @return the escaped string
     */

    public String escape(/*@Nullable*/ String in) {
        if (in == null) {
            return "\"\"";
        }
        char quot = in.contains("\"") ? '\'' : '"';
        String collapsed = Whitespace.collapseWhitespace(in);
        StringBuilder sb = new StringBuilder(collapsed.length() + 10).append(quot);
        for (int i = 0; i < collapsed.length(); i++) {
            char c = collapsed.charAt(i);
            if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '&') {
                sb.append("&amp;");
            } else if (c == quot) {
                sb.append(quot == '"' ? "&quot;" : "&apos;");
            } else if (c == '\n') {
                sb.append("&#xA;");
            } else if (c == '\r') {
                sb.append("&#xD;");
            } else if (c == '\t') {
                sb.append("&#x9;");
            } else {
                sb.append(c);
            }
        }
        return sb.append(quot).toString();
    }

    /**
     * Called after an instruction of the stylesheet got processed
     * @param info trace information
     */

    @Override
    public void leave(Traceable info) {
        if (isApplicable(info)) {
            stack.pop();
            indent--;
            out.info(AbstractTraceListener.spaces(indent) + "</" + tag(info) + '>');
        }
    }

    protected boolean isApplicable(Traceable info) {
        return level(info) <= detail;
    }

    /**
     * Get the trace element tagname to be used for a particular construct. Return null for
     * trace events that are ignored by this trace listener.
     *
     * @param info trace information
     */

    protected String tag(Traceable info) {
        return "expr";
    }


    protected int level(Traceable info) {
        if (info instanceof TraceableComponent || info instanceof ApplyTemplates || info instanceof CallTemplate) {
            return 1;
        }
        if (info instanceof Expression &&
                (((Expression)info).isInstruction() || ((Expression) info).isCallOn(TransformFn.class))) {
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
            stack.push(item);
            NodeInfo curr = (NodeInfo) item;
            out.info(AbstractTraceListener.spaces(indent) + "<source node=\"" + Navigator.getPath(curr)
                    + "\" line=\"" + curr.getLineNumber()
                    + "\" column=\"" + curr.getColumnNumber()
                    + "\" file=\"" + abbreviateLocationURI(curr.getSystemId())
                    + "\">");
            indent++;
        } else if ((item instanceof MapItem) && detail > 0) {
            stack.push(item);
            out.info(AbstractTraceListener.spaces(indent) + "<source map='" + Err.depict(item) + "'>");
        } else if ((item instanceof ArrayItem) && detail > 0) {
            stack.push(item);
            out.info(AbstractTraceListener.spaces(indent) + "<source array='" + Err.depict(item) + "'>");
        }
    }

    /**
     * Called after a node of the source tree got processed
     */

    @Override
    public void endCurrentItem(Item item) {
        if (item instanceof NodeInfo && detail > 0) {
            indent--;
            NodeInfo curr = (NodeInfo) item;
            out.info(AbstractTraceListener.spaces(indent) + "</source><!-- " +
                    Navigator.getPath(curr) + " -->");
            stack.pop();
        } else if ((item instanceof MapItem) && detail > 0) {
            out.info(AbstractTraceListener.spaces(indent) + "</source><!-- map -->");
            stack.pop();
        } else if ((item instanceof ArrayItem) && detail > 0) {
            out.info(AbstractTraceListener.spaces(indent) + "</source><!-- array -->");
            stack.pop();
        }
    }

    /**
     * Get n spaces
     * @param n the requested number of spaces
     * @return a string containing the requested number of spaces
     */

    protected static String spaces(int n) {
        n = Math.max(n, 0);
        while (spaceBuffer.length() < n) {
            spaceBuffer.append(AbstractTraceListener.spaceBuffer);
        }
        return spaceBuffer.substring(0, n);
    }

    /**
     * Method called immediately after the {@link #enter} method corresponding to an
     * {@code xsl:try} instruction (or XQuery try/catch). The method may return an object
     * containing checkpoint information, which will be passed to the {@link #recover}
     * method if an error occurs and is caught.
     *
     * @return arbitrary checkpoint information, or null. This implementation returns
     * the size of the stack.
     */
    @Override
    public Object checkpoint() {
        return stack.size();
    }

    /**
     * Method called when an error is caught by an {@code xsl:catch} (or XQuery try/catch).
     *
     * @param checkpoint A checkpoint object returned by a previous call to the {@link #checkpoint} method.
     * @param error The error that was caught
     */

    @Override
    public void recover(Object checkpoint, XPathException error) {
        out.info(AbstractTraceListener.spaces(indent) + "<error code='"
                         + error.getErrorCodeQName().getLocalPart()
                         + "'>" + error.getMessage() + "</error>");
        while (stack.size() > (int) checkpoint) {
            int size = stack.size();
            Object o = stack.peek();
            if (o instanceof Traceable) {
                leave((Traceable) o);
            } else if (o instanceof Item) {
                endCurrentItem((Item) o);
            }
            if (stack.size() == size) {
                stack.pop();
            }
        }
        out.info(AbstractTraceListener.spaces(indent) + "<catch/>");
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
     * @return the output destination for tracing output
     */

    public Logger getOutputDestination() {
        return out;
    }

    /**
     * Method called when a rule search has completed.
     * @param rule the rule (or possible built-in ruleset) that has been selected
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

