////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.expr.parser.XPathParser;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.regex.BMPString;
import net.sf.saxon.regex.GeneralUnicodeString;
import net.sf.saxon.regex.LatinString;
import net.sf.saxon.regex.UnicodeString;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.KeyDefinition;
import net.sf.saxon.trans.Mode;
import net.sf.saxon.trans.rules.BuiltInRuleSet;
import net.sf.saxon.trans.rules.Rule;
import net.sf.saxon.tree.AttributeLocation;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.ValidationException;
import net.sf.saxon.type.ValidationFailure;

import javax.xml.transform.SourceLocator;
import javax.xml.transform.dom.DOMLocator;
import java.util.List;

/**
 * This class is an abstract superclass of classes such as the {@link StandardErrorListener}
 * and {@link StandardInvalidityHandler}, and exists to provide common utility methods for these
 * classes, and for similar user-written classes if required
 */

public class StandardDiagnostics {

    public StandardDiagnostics() {}

    /**
     * Construct a message identifying the location of an error
     * @param loc the location of the error
     * @return a message describing the location
     */

    public String getLocationMessageText(SourceLocator loc) {
        String locMessage = "";
        String systemId = null;
        NodeInfo node = null;
        String path;
        String nodeMessage = null;
        int lineNumber = -1;
        if (loc == null) {
            loc = Loc.NONE;
        }
        if (loc instanceof XPathParser.NestedLocation) {
            loc = ((XPathParser.NestedLocation) loc).getContainingLocation();
        }
        if (loc instanceof AttributeLocation) {
            AttributeLocation saLoc = (AttributeLocation) loc;
            nodeMessage = "in " + saLoc.getElementName().getDisplayName();
            if (saLoc.getAttributeName() != null) {
                nodeMessage += "/@" + saLoc.getAttributeName();
            }
            nodeMessage += ' ';
        } else if (loc instanceof DOMLocator) {
            nodeMessage = "at " + ((DOMLocator) loc).getOriginatingNode().getNodeName() + ' ';
        } else if (loc instanceof NodeInfo) {
            node = (NodeInfo) loc;
            nodeMessage = "at " + node.getDisplayName() + ' ';
        } else if (loc instanceof ValidationException && (node = ((ValidationException) loc).getNode()) != null) {
            nodeMessage = "at " + node.getDisplayName() + ' ';
        } else if (loc instanceof ValidationException && loc.getLineNumber() == -1 && (path = ((ValidationException) loc).getPath()) != null) {
            nodeMessage = "at " + path + ' ';
        } else if (loc instanceof Instruction) {
            String instructionName = getInstructionName((Instruction) loc);
            if (!"".equals(instructionName)) {
                nodeMessage = "at " + instructionName + ' ';
            }
            systemId = loc.getSystemId();
            lineNumber = loc.getLineNumber();
        } else if (loc instanceof Actor) {
            String kind = "procedure";
            if (loc instanceof UserFunction) {
                kind = "function";
            } else if (loc instanceof NamedTemplate) {
                kind = "template";
            } else if (loc instanceof AttributeSet) {
                kind = "attribute-set";
            } else if (loc instanceof KeyDefinition) {
                kind = "key";
            } else if (loc instanceof GlobalParam) {
                kind = "parameter";
            } else if (loc instanceof GlobalVariable) {
                kind = "variable";
            } else if (loc instanceof Mode) {
                kind = "mode";
            }
            systemId = loc.getSystemId();
            lineNumber = loc.getLineNumber();
            nodeMessage = "at " + kind + " ";
            StructuredQName name = ((Actor) loc).getComponentName();
            if (name != null) {
                String n = name.toString();
                if (n.equals("xsl:unnamed")) {
                    n = "(unnamed)";
                }
                nodeMessage += n;
                nodeMessage += " ";
            }
        }
        if (lineNumber == -1) {
            lineNumber = loc.getLineNumber();
        }
        boolean containsLineNumber = lineNumber > 0;
        if (node != null && !containsLineNumber) {
            nodeMessage = "at " + Navigator.getPath(node) + ' ';
        }
        if (nodeMessage != null) {
            locMessage += nodeMessage;
        }
        if (containsLineNumber) {
            locMessage += "on line " + lineNumber + ' ';
            if (loc.getColumnNumber() != -1) {
                locMessage += "column " + loc.getColumnNumber() + ' ';
            }
        }

        if (systemId != null && systemId.isEmpty()) {
            systemId = null;
        }
        if (systemId == null) {
            try {
                systemId = loc.getSystemId();
            } catch (Exception err) {
                err.printStackTrace();
                // no action (can fail with NPE if the expression tree is corrupt)
            }
        }
        if (systemId != null && !systemId.isEmpty()) {
            locMessage += (containsLineNumber ? "of " : "in ") + abbreviateLocationURI(systemId) + ':';
        }
        return locMessage;
    }

    /**
     * Extract a name or phrase identifying the instruction at which an error occurred.
     * This default implementation invokes the static method
     * {@link #getInstructionNameDefault(Instruction)}.
     *
     * @param inst the instruction in question
     * @return the name or description of the instruction,
     * in user-meaningful terms
     */

    public String getInstructionName(Instruction inst) {
        return getInstructionNameDefault(inst);
    }

    /**
     * Extract a name or phrase identifying the instruction at which an error occurred.
     * This default implementation uses terminology that is neutral between XQuery and XSLT,
     * for example "text node constructor" in preference to "xsl:text".
     *
     * @param inst the instruction in question
     * @return the name or description of the instruction,
     * in user-meaningful terms
     */

    public static String getInstructionNameDefault(Instruction inst) {
        try {
            if (inst instanceof FixedElement) {
                StructuredQName qName = inst.getObjectName();
                return "element constructor <" + qName.getDisplayName() + '>';
            } else if (inst instanceof FixedAttribute) {
                StructuredQName qName = inst.getObjectName();
                return "attribute constructor " + qName.getDisplayName() + "=\"{...}\"";
            }
            int construct = inst.getInstructionNameCode();
            if (construct < 0) {
                return "";
            }
            if (construct < 1024 &&
                    construct != StandardNames.XSL_FUNCTION &&
                    construct != StandardNames.XSL_TEMPLATE) {
                // it's a standard name
                if (inst.getPackageData().isXSLT()) {
                    return StandardNames.getDisplayName(construct);
                } else {
                    String s = StandardNames.getDisplayName(construct);
                    int colon = s.indexOf(':');
                    if (colon > 0) {
                        String local = s.substring(colon + 1);
                        if (local.equals("document")) {
                            return "document node constructor";
                        } else if (local.equals("text") || s.equals("value-of")) {
                            return "text node constructor";
                        } else if (local.equals("element")) {
                            return "computed element constructor";
                        } else if (local.equals("attribute")) {
                            return "computed attribute constructor";
                        } else if (local.equals("variable")) {
                            return "variable declaration";
                        } else if (local.equals("param")) {
                            return "external variable declaration";
                        } else if (local.equals("comment")) {
                            return "comment constructor";
                        } else if (local.equals("processing-instruction")) {
                            return "processing-instruction constructor";
                        } else if (local.equals("namespace")) {
                            return "namespace node constructor";
                        }
                    }
                    return s;
                }
            } else {
                return "";
            }

        } catch (Exception err) {
            return "";
        }
    }


    /**
     * Print a stack trace to a specified output destination
     *
     * @param context the XPath dynamic execution context (which holds the head of a linked
     *                list of context objects, representing the execution stack)
     * @param out     the print stream to which the stack trace will be output
     * @param level   the level of detail: 0=none, 1=name and location of function/template,
     *                2=values of variables
     */

    public void printStackTrace(XPathContext context, Logger out, int level) {
        if (level > 0) {
            int depth = 20;
            while (depth-- > 0) {
                Component component = context.getCurrentComponent();
                if (component != null) {
                    if (component.getActor() instanceof Mode) {
                        Rule rule = context.getCurrentTemplateRule();
                        if (rule != null) {
                            StringBuilder sb = new StringBuilder();
                            Location loc = rule.getPattern().getLocation();
                            sb.append("  In template rule with match=\"")
                                    .append(rule.getPattern().toShortString())
                                    .append("\" ");
                            if (loc != null && loc.getLineNumber() != -1) {
                                sb.append("on line ").append(loc.getLineNumber()).append(" ");
                            }
                            if (loc != null && loc.getSystemId() != null) {
                                sb.append("of ").append(abbreviateLocationURI(loc.getSystemId()));
                            }
                            out.error(sb.toString());
                        }
                    } else {
                        out.error(getLocationMessageText((Location)component.getActor()).replace("$at ", "In "));
                    }
                }
                try {
                    context.getStackFrame().getStackFrameMap().showStackFrame(context, out);
                } catch (Exception e) {
                    // no action
                }
                while (!(context instanceof XPathContextMajor)) {
                    context = context.getCaller();
                }

                ContextOriginator originator = ((XPathContextMajor) context).getOrigin();
                if (originator == null || originator instanceof Controller) {
                    return;
                } else {
                    out.error("     invoked by " + showOriginator(originator));
                }
                context = context.getCaller();
            }
        }
    }

    /**
     * Produce text identifying a construct that originates the context for an error
     * @param originator the {@code ContextOriginator} responsible for creating a new
     *                   context
     * @return message text (suitable for use in a phrase such as "called by XXX") for
     * inclusion in an error message.
     */

    protected String showOriginator(ContextOriginator originator) {
        StringBuilder sb = new StringBuilder();
        if (originator == null) {
            sb.append("unknown caller (null)");
        } else if (originator instanceof Instruction) {
            sb.append(getInstructionName((Instruction) originator));
        } else if (originator instanceof UserFunctionCall) {
            sb.append("function call");
        } else if (originator instanceof Controller) {
            sb.append("external application");
        } else if (originator instanceof BuiltInRuleSet) {
            sb.append("built-in template rule (").append(((BuiltInRuleSet) originator).getName()).append(")");
        } else if (originator instanceof KeyDefinition) {
            sb.append("xsl:key definition");
        } else if (originator instanceof GlobalParam) {
            sb.append("global parameter ").append(((GlobalParam) originator).getVariableQName().getDisplayName());
        } else if (originator instanceof GlobalVariable) {
            sb.append(((GlobalVariable) originator).getDescription());
        } else {
            sb.append("unknown caller (").append(originator.getClass()).append(")");
        }
        if (originator instanceof Locatable) {
            Location loc = ((Locatable) originator).getLocation();
            if (loc.getLineNumber() != -1) {
                sb.append(" at ").append(loc.getSystemId() == null ? "line " : (loc.getSystemId() + "#"));
                sb.append(loc.getLineNumber());
            }
        }
        return sb.toString();
    }


    /**
     * A {@link ValidationFailure} relating to a failing assertion contains a (possibly empty)
     * list of nodes responsible for the failure. This may be the node to which the assertion
     * applies, or in the case of an assertion in the form {@code every $x in XX satisfies P}
     * it may be the node in XX for which P was not satisfied. This method converts this list
     * of nodes into a string suitable for inclusion in error messages, typically as a complete
     * sentence. If the list of nodes is empty, the returned string should normally be empty.
     *
     * <p>The default implementation displays a message over several lines, starting with
     * "Nodes for which the assertion fails:", and continuing with one line per node. In the
     * typical case where the nodes are elements, subsequent lines will take the form
     * "element(N) on line L column C of file.xml" if the location is not, or "at x/y/z"
     * (where x/y/z is a path to the node) otherwise.</p>
     *
     * <p>The method is provided so that it can be overridden in a subclass.</p>
     *
     * @param failure the validation failure being reported.
     * @return a string describing the list of nodes contributing to the failure of an
     * assertion.
     */

    protected String formatListOfOffendingNodes(ValidationFailure failure) {
        StringBuilder message = new StringBuilder();
        List<NodeInfo> offendingNodes = failure.getOffendingNodes();
        if (!offendingNodes.isEmpty()) {
            message.append("\n  Nodes for which the assertion fails:");
            for (NodeInfo offender : offendingNodes) {
                String nodeDesc = Type.displayTypeName(offender);
                if (offender.getNodeKind() == Type.TEXT) {
                    nodeDesc += " " + Err.wrap(offender.getStringValueCS(), Err.VALUE);
                }
                if (offender.getLineNumber() != -1) {
                    nodeDesc += " on line " + offender.getLineNumber();
                    if (offender.getColumnNumber() != -1) {
                        nodeDesc += " column " + offender.getColumnNumber();
                    }
                    if (offender.getSystemId() != null) {
                        nodeDesc += " of " + offender.getSystemId();
                    }
                } else {
                    nodeDesc += " at " + Navigator.getPath(offender);
                }
                message.append("\n  * ").append(nodeDesc);
            }
        }
        return message.toString();
    }

    /**
     * Abbreviate a URI for use in diagnostics.
     * <p>
     * This default implementation invokes the static method {@link #abbreviateLocationURIDefault(String)}.
     * <p>
     * This method is intended for use only for location URIs, not for namespace URIs.
     *
     * @param uri the URI to be abbreviated
     * @return the abbreviated URI
     */

    /*@Nullable*/
    public String abbreviateLocationURI(String uri) {
        return abbreviateLocationURIDefault(uri);
    }

    /**
     * Abbreviate a URI for use in diagnostics.
     * <p>
     * This default implementation displays the part of the URI after the last slash.
     * <p>
     * This method is intended for use only for location URIs, not for namespace URIs.
     *
     * @param uri the URI to be abbreviated
     * @return the abbreviated URI
     */

    /*@Nullable*/
    public static String abbreviateLocationURIDefault(String uri) {
        if (uri == null) {
            return "*unknown*";
        }
        int slash = uri.lastIndexOf('/');
        if (slash >= 0 && slash < uri.length() - 1) {
            return uri.substring(slash + 1);
        } else {
            return uri;
        }
    }

    /**
     * Variable defining an absolute limit on the length of an error message;
     * any message longer than this will be truncated by the {@link #wordWrap(String)}
     * method. The value can be assigned. Default value is 1000.
     */

    public int MAX_MESSAGE_LENGTH = 2000;

    /**
     * Variable defining a threshold for word-wrapping a message. If a message
     * is longer than this, then the {@link #wordWrap(String)} method will attempt
     * to break it into shorter lines. The value can be assigned. Default value is 100.
     */

    public int MAX_MESSAGE_LINE_LENGTH = 100;

    /**
     * Variable defining a minimum length for parts of a word-wrapped a message. The
     * {@link #wordWrap(String)} method will not split a message line if it would result
     * in a line shorter than this. The value can be assigned. Default value is 10.
     */

    public int MIN_MESSAGE_LINE_LENGTH = 10;

    /**
     * Variable defining a target line length for messages. If word-wrapping takes place,
     * then it will take place at the last space character found before this column
     * position. The value can be assigned. Default value is 90.
     */

    public int TARGET_MESSAGE_LINE_LENGTH = 90;

    /**
     * Wordwrap an error message into lines of {@link #TARGET_MESSAGE_LINE_LENGTH}
     * characters or less (if possible). Note that existing newlines within the message
     * will be retained.
     *
     * @param message the message to be word-wrapped
     * @return the message after applying word-wrapping
     */

    public String wordWrap(String message) {
        if (message.length() > MAX_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_MESSAGE_LENGTH);
        }
        int nl = message.indexOf('\n');
        if (nl < 0) {
            nl = message.length();
        }
        if (nl > MAX_MESSAGE_LINE_LENGTH) {
            int i = TARGET_MESSAGE_LINE_LENGTH;
            while (message.charAt(i) != ' ' && i > 0) {
                i--;
            }
            if (i > MIN_MESSAGE_LINE_LENGTH) {
                return message.substring(0, i) + "\n  " + wordWrap(message.substring(i + 1));
            } else {
                return message;
            }
        } else if (nl < message.length()) {
            return message.substring(0, nl) + '\n' + wordWrap(message.substring(nl + 1));
        } else {
            return message;
        }
    }

    /**
     * Expand any special characters appearing in a message. In the default implementation,
     * special characters will be output as themselves, followed by a hex codepoint in the
     * form [xHHHHH]: for example {@code §[xA7]}
     *
     * @param in        the message to be expanded
     * @param threshold the codepoint above which characters are considered special.
     * @return the expanded message
     */

    public CharSequence expandSpecialCharacters(CharSequence in, int threshold) {
        if (threshold >= UTF16CharacterSet.NONBMP_MAX) {
            return in;
        }
        int max = 0;
        boolean isAstral = false;
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            if (c > max) {
                max = c;
            }
            if (UTF16CharacterSet.isSurrogate(c)) {
                isAstral = true;
            }
        }
        if (max <= threshold && !isAstral) {
            return in;
        }
        UnicodeString str;
        if (max <= 255) {
            str = new LatinString(in);
        } else if (!isAstral) {
            str = new BMPString(in);
        } else {
            str = new GeneralUnicodeString(in);
        }
        FastStringBuffer fsb = new FastStringBuffer(str.uLength() * 2);
        for (int i = 0; i < str.uLength(); i++) {
            int ch = str.uCharAt(i);
            fsb.appendWideChar(ch);
            if (ch > threshold) {
                fsb.append("[x");
                fsb.append(Integer.toHexString(ch));
                fsb.append("]");
            }
        }
        return fsb;
    }


}

