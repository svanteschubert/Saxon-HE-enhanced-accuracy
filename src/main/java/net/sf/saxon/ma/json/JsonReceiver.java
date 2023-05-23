////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.json;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.CharSequenceConsumer;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.StringConverter;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.StringToDouble11;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.function.IntPredicate;

/**
 * A Receiver which receives a stream of XML events using the vocabulary defined for the XML representation
 * of JSON in XSLT 3.0, and which generates the corresponding JSON text as a string
 */


public class JsonReceiver implements Receiver {

    private PipelineConfiguration pipe;
    private CharSequenceConsumer output;
    private FastStringBuffer textBuffer = new FastStringBuffer(128);
    private Stack<NodeName> stack = new Stack<>();
    private boolean atStart = true;
    private boolean indenting = false;
    private boolean escaped = false;
    private Stack<Set<String>> keyChecker = new Stack<>();
    private Function numberFormatter;

    private static final String ERR_INPUT = "FOJS0006";

    public JsonReceiver(PipelineConfiguration pipe, CharSequenceConsumer output) {
        Objects.requireNonNull(pipe);
        Objects.requireNonNull(output);
        setPipelineConfiguration(pipe);
        this.output = output;
    }

    @Override
    public void setPipelineConfiguration(PipelineConfiguration pipe) {
        this.pipe = pipe;
    }

    @Override
    public PipelineConfiguration getPipelineConfiguration() {
        return pipe;
    }

    @Override
    public void setSystemId(String systemId) {
        // no action
    }

    public void setIndenting(boolean indenting) {
        this.indenting = indenting;
    }

    public boolean isIndenting() {
        return indenting;
    }

    public void setNumberFormatter(Function formatter) {
        assert formatter.getArity() == 1;
        this.numberFormatter = formatter;
    }

    public Function getNumberFormatter() {
        return this.numberFormatter;
    }

    @Override
    public void open() throws XPathException {
        output.open();
    }

    @Override
    public void startDocument(int properties) throws XPathException {
//        if (output == null) {
//            output = new FastStringBuffer(2048);
//        }
    }

    @Override
    public void endDocument() throws XPathException {
        // no action
    }

    @Override
    public void setUnparsedEntity(String name, String systemID, String publicID) throws XPathException {
        // no action
    }

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        String parent = stack.empty() ? null : stack.peek().getLocalPart();
        boolean inMap = "map".equals(parent) || stack.isEmpty();
        stack.push(elemName);
        //started.push(false);
        if (!elemName.hasURI(NamespaceConstant.FN)) {
            throw new XPathException("xml-to-json: element found in wrong namespace: " +
                                             elemName.getStructuredQName().getEQName(), ERR_INPUT);
        }

        String key = null;
        String escapedAtt = null;
        String escapedKey = null;
        for (AttributeInfo att : attributes) {
            NodeName attName = att.getNodeName();
            if (attName.hasURI("")) {
                if (attName.getLocalPart().equals("key")) {
                    if (!inMap) {
                        throw new XPathException(
                                "xml-to-json: The key attribute is allowed only on elements within a map", ERR_INPUT);
                    }
                    key = att.getValue();
                } else if (attName.getLocalPart().equals("escaped-key")) {
                    if (!inMap) {
                        throw new XPathException(
                                "xml-to-json: The escaped-key attribute is allowed only on elements within a map", ERR_INPUT);
                    }
                    escapedKey = att.getValue();
                } else if (attName.getLocalPart().equals("escaped")) {
                    boolean allowed = stack.size() == 1 || elemName.getLocalPart().equals("string");
                    // See bugs 29917 and 30077: at the top level, the escaped attribute is ignored
                    // whatever element it appears on
                    if (!allowed) {
                        throw new XPathException(
                                "xml-to-json: The escaped attribute is allowed only on the <string> element",
                                ERR_INPUT);
                    }
                    escapedAtt = att.getValue();
                } else {
                    throw new XPathException("xml-to-json: Disallowed attribute in input: " + attName.getDisplayName(), ERR_INPUT);
                }
            } else if (attName.hasURI(NamespaceConstant.FN)) {
                throw new XPathException("xml-to-json: Disallowed attribute in input: " + attName.getDisplayName(), ERR_INPUT);
            }
            // Attributes in other namespaces are ignored
        }

        if (!atStart) {
            output.cat(",");
            if (indenting) {
                indent(stack.size());
            }
        }
        if (inMap && !keyChecker.isEmpty()) {
            if (key == null) {
                throw new XPathException("xml-to-json: Child elements of <map> must have a key attribute", ERR_INPUT);
            }
            boolean alreadyEscaped = false;
            if (escapedKey != null) {
                try {
                    alreadyEscaped = StringConverter.StringToBoolean.INSTANCE.convertString(escapedKey).asAtomic().effectiveBooleanValue();
                } catch (XPathException e) {
                    throw new XPathException("xml-to-json: Value of escaped-key attribute '" + Err.wrap(escapedKey) +
                                                     "' is not a valid xs:boolean", ERR_INPUT);
                }
            }
            key = (alreadyEscaped ? handleEscapedString(key) : escape(key, false, new ControlChar())).toString();

            String normalizedKey = alreadyEscaped ? unescape(key) : key;
            boolean added = keyChecker.peek().add(normalizedKey);
            if (!added) {
                throw new XPathException("xml-to-json: duplicate key value " + Err.wrap(key), ERR_INPUT);
            }

            output.cat("\"").cat(key).cat("\"").cat(indenting ? " : " : ":");
        }
        String local = elemName.getLocalPart();
        checkParent(local, parent);
        switch (local) {
            case "array":
                if (indenting) {
                    indent(stack.size());
                    output.cat("[ ");
                } else {
                    output.cat("[");
                }
                atStart = true;
                break;
            case "map":
                if (indenting) {
                    indent(stack.size());
                    output.cat("{ ");
                } else {
                    output.cat("{");
                }
                atStart = true;
                keyChecker.push(new HashSet<String>());
                break;
            case "null":
                //checkParent(local, parent);
                output.cat("null");
                atStart = false;
                break;
            case "string":
                if (escapedAtt != null) {
                    try {
                        escaped = StringConverter.StringToBoolean.INSTANCE.convertString(escapedAtt)
                                .asAtomic().effectiveBooleanValue();
                    } catch (XPathException e) {
                        throw new XPathException("xml-to-json: value of escaped attribute (" +
                                                         escaped + ") is not a valid xs:boolean", ERR_INPUT);
                    }
                }
                //checkParent(local, parent);
                atStart = false;
                break;
            case "boolean":
            case "number":
                //checkParent(local, parent);
                atStart = false;
                break;
            default:
                throw new XPathException("xml-to-json: unknown element <" + local + ">", ERR_INPUT);
        }
        textBuffer.setLength(0);
    }

    private void checkParent(String child, String parent) throws XPathException {
        if ("null".equals(parent) || "string".equals(parent) || "number".equals(parent) || "boolean".equals(parent)) {
            throw new XPathException("xml-to-json: A " + Err.wrap(child, Err.ELEMENT) +
                                             " element cannot appear as a child of " + Err.wrap(parent, Err.ELEMENT), ERR_INPUT);
        }
    }

    @Override
    public void endElement() throws XPathException {
        NodeName name = stack.pop();
        String local = name.getLocalPart();
        if (local.equals("boolean")) {
            try {
                boolean b = StringConverter.StringToBoolean.INSTANCE.convertString(textBuffer).asAtomic().effectiveBooleanValue();
                output.cat(b ? "true" : "false");
            } catch (XPathException e) {
                throw new XPathException("xml-to-json: Value of <boolean> element is not a valid xs:boolean", ERR_INPUT);
            }
        } else if (local.equals("number")) {
            if (numberFormatter == null) {
                try {
                    double d = StringToDouble11.getInstance().stringToNumber(textBuffer);
                    if (Double.isNaN(d) || Double.isInfinite(d)) {
                        throw new XPathException("xml-to-json: Infinity and NaN are not allowed", ERR_INPUT);
                    }
                    output.cat(new DoubleValue(d).getStringValueCS());
                } catch (NumberFormatException e) {
                    throw new XPathException("xml-to-json: Invalid number: " + textBuffer, ERR_INPUT);
                }
            } else {
                Sequence result = SystemFunction.dynamicCall(
                        numberFormatter, pipe.getXPathContext(), new Sequence[]{new StringValue(textBuffer)});
                output.cat(((StringValue) result).getStringValueCS());
            }
        } else if (local.equals("string")) {
            output.cat("\"");
            String str = textBuffer.toString();
            if (escaped) {
                output.cat(handleEscapedString(str));
            } else {
                output.cat(escape(str, false, new ControlChar()));
            }
            output.cat("\"");
        } else if (!Whitespace.isWhite(textBuffer)) {
            throw new XPathException("xml-to-json: Element " + name.getDisplayName() + " must have no text content", ERR_INPUT);
        }
        textBuffer.setLength(0);
        escaped = false;
        if (local.equals("array")) {
            output.cat(indenting ? " ]" : "]");
        } else if (local.equals("map")) {
            keyChecker.pop();
            output.cat(indenting ? " }" : "}");
        }
        atStart = false;
    }

    /**
     * Handle a string that is already escaped, and that should remain escaped, while normalizing
     * escape sequences to standard format
     *
     * @param str the input string
     * @return the result string
     * @throws XPathException if the input contains invalid escape sequences
     */

    private static CharSequence handleEscapedString(String str) throws XPathException {
        // check that escape sequences are valid
        unescape(str);
        FastStringBuffer out = new FastStringBuffer(str.length() * 2);
        boolean afterEscapeChar = false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '"' && !afterEscapeChar) {
                out.append("\\\"");
            } else if (c < 32 || (c >= 127 && c < 160)) {
                if (c == '\b') {
                    out.append("\\b");
                } else if (c == '\f') {
                    out.append("\\f");
                } else if (c == '\n') {
                    out.append("\\n");
                } else if (c == '\r') {
                    out.append("\\r");
                } else if (c == '\t') {
                    out.append("\\t");
                } else {
                    out.append("\\u");
                    String hex = Integer.toHexString(c).toUpperCase();
                    while (hex.length() < 4) {
                        hex = "0" + hex;
                    }
                    out.append(hex);
                }
            } else if (c == '/' && !afterEscapeChar) {
                out.append("\\/");
            } else {
                out.cat(c);
            }
            afterEscapeChar = c == '\\' && !afterEscapeChar;
        }
        return out;
    }


    /**
     * Escape a string using backslash escape sequences as defined in JSON
     *
     * @param in         the input string
     * @param forXml     true if the output is for the json-to-xml function
     * @param hexEscapes a predicate identifying characters that should be output as hex escapes using \ u XXXX notation.
     * @return the escaped string
     */

    public static CharSequence escape(CharSequence in, boolean forXml, IntPredicate hexEscapes) throws XPathException {
        FastStringBuffer out = new FastStringBuffer(in.length());
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            switch (c) {
                case '"':
                    out.append(forXml ? "\"" : "\\\"");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '/':
                    out.append(forXml ? "/" : "\\/");  // spec bug 29665, saxon bug 2849
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                default:
                    if (hexEscapes.test(c)) {
                        out.append("\\u");
                        String hex = Integer.toHexString(c).toUpperCase();
                        while (hex.length() < 4) {
                            hex = "0" + hex;
                        }
                        out.append(hex);
                    } else {
                        out.cat(c);
                    }
            }
        }
        return out;
    }

    private static class ControlChar implements IntPredicate {
        @Override
        public boolean test(int c) {
            return c < 31 || (c >= 127 && c <= 159);
        }
    }

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (!stack.empty() && !Whitespace.isWhite(chars)) {
            NodeName element = stack.peek();
            String local = element.getLocalPart();
            if (local.equals("map") || local.equals("array")) {
                throw new XPathException("xml-to-json: Element " + local + " must have no text content", ERR_INPUT);
            }
        }
        textBuffer.cat(chars);
    }

    @Override
    public void processingInstruction(String name, CharSequence data, Location locationId, int properties) throws XPathException {
        // no action
    }

    @Override
    public void comment(CharSequence content, Location locationId, int properties) throws XPathException {
        // no action
    }

    @Override
    public void close() throws XPathException {
        if (output != null) {
            output.close();
            output = null;
        }
    }

    @Override
    public boolean usesTypeAnnotations() {
        return false;
    }

    @Override
    public String getSystemId() {
        return null;
    }

    /**
     * On completion, get the assembled JSON string
     *
     * @return the JSON string representing the supplied XML content.
     */

    //public String getJsonString() {
//        return output.toString();
//    }

    /**
     * Add indentation whitespace to the buffer
     *
     * @param depth the level of indentation
     */

    private void indent(int depth) throws XPathException {
        output.cat("\n");
        for (int i = 0; i < depth; i++) {
            output.cat("  ");
        }
    }

    /**
     * Unescape a JSON string literal,
     *
     * @param literal the string literal to be processed
     * @return the result of expanding escape sequences
     * @throws net.sf.saxon.trans.XPathException if the input contains invalid escape sequences
     */

    private static String unescape(String literal) throws XPathException {
        if (literal.indexOf('\\') < 0) {
            return literal;
        }
        FastStringBuffer buffer = new FastStringBuffer(literal.length());
        for (int i = 0; i < literal.length(); i++) {
            char c = literal.charAt(i);
            if (c == '\\') {
                if (i++ == literal.length() - 1) {
                    throw new XPathException("String '" + Err.wrap(literal) + "' ends in backslash ", "FOJS0007");
                }
                switch (literal.charAt(i)) {
                    case '"':
                        buffer.cat('"');
                        break;
                    case '\\':
                        buffer.cat('\\');
                        break;
                    case '/':
                        buffer.cat('/');
                        break;
                    case 'b':
                        buffer.cat('\b');
                        break;
                    case 'f':
                        buffer.cat('\f');
                        break;
                    case 'n':
                        buffer.cat('\n');
                        break;
                    case 'r':
                        buffer.cat('\r');
                        break;
                    case 't':
                        buffer.cat('\t');
                        break;
                    case 'u':
                        try {
                            String hex = literal.substring(i + 1, i + 5);
                            int code = Integer.parseInt(hex, 16);
                            buffer.cat((char) code);
                            i += 4;
                        } catch (Exception e) {
                            throw new XPathException("Invalid hex escape sequence in string '" + Err.wrap(literal) + "'", "FOJS0007");
                        }
                        break;
                    default:
                        char next = literal.charAt(i);
                        String xx = next < 256 ? next + "" : "x" + Integer.toHexString(next);
                        throw new XPathException("Unknown escape sequence \\" + xx, "FOJS0007");
                }
            } else {
                buffer.cat(c);
            }
        }
        return buffer.toString();
    }
}

// Copyright (c) 2018-2020 Saxonica Limited
