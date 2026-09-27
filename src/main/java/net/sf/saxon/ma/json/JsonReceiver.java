////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.json;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.*;
import net.sf.saxon.regex.ARegularExpression;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.str.*;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.StringConverter;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.StringToDouble11;
import net.sf.saxon.value.Whitespace;
import net.sf.saxon.z.IntIterator;
import net.sf.saxon.z.IntPredicateLambda;
import net.sf.saxon.z.IntPredicateProxy;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;

/**
 * A Receiver which receives a stream of XML events using the vocabulary defined for the XML representation
 * of JSON in XSLT 3.0, and which generates the corresponding JSON text as a string
 */


public class JsonReceiver implements Receiver {
    private final XPathContext context;
    private PipelineConfiguration pipe;
    private UniStringConsumer output;
    private final UnicodeBuilder textBuffer = new UnicodeBuilder(128);
    private final Stack<NodeName> stack = new Stack<>();
    private boolean atStart = true;
    private boolean indenting = false;
    private boolean escapeSolidus = true;
    private boolean escaped = false;
    private boolean retainNumberFormat = false;
    private final Stack<Set<UnicodeString>> keyChecker = new Stack<>();
    private static final String ERR_INPUT = "FOJS0006";

    private final static UnicodeString U_LSQB_SPACE = Latin1.of("[ ");
    private final static UnicodeString U_LSQB = new UnicodeChar('[');
    private final static UnicodeString U_SPACE_RSQB = Latin1.of(" ]");
    private final static UnicodeString U_RSQB = new UnicodeChar(']');
    private final static UnicodeString U_LCURLY_SPACE = Latin1.of("{ ");
    private final static UnicodeString U_LCURLY = new UnicodeChar('{');
    private final static UnicodeString U_SPACE_RCURLY = Latin1.of(" }");
    private final static UnicodeString U_RCURLY = new UnicodeChar('}');
    private final static UnicodeString U_NULL = Latin1.of("null");
    private final static UnicodeString U_QUOT = new UnicodeChar('"');
    private final static UnicodeString U_NL = StringConstants.NEWLINE;

    public JsonReceiver(PipelineConfiguration pipe, XPathContext context, UniStringConsumer output) {
        Objects.requireNonNull(pipe);
        Objects.requireNonNull(output);
        setPipelineConfiguration(pipe);
        this.output = output;
        this.context = context;
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

    public void setEscapeSolidus(boolean escape) {
        this.escapeSolidus = escape;
    }

    public boolean isEscapeSolidus() {
        return this.escapeSolidus;
    }

    public void setRetainNumberFormat(boolean flag) {
        this.retainNumberFormat = flag;
    }

    @Override
    public void open() throws XPathException {
        output.open();
    }

    @Override
    public void startDocument(int properties) throws XPathException {
//        if (output == null) {
//            output = new StringBuilder(2048);
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
        if (!elemName.hasURI(NamespaceUri.FN)) {
            throw new XPathException("xml-to-json: element found in wrong namespace: " +
                                             elemName.getStructuredQName().getEQName(), ERR_INPUT);
        }

        UnicodeString key = null;
        UnicodeString escapedAtt = null;
        UnicodeString escapedKey = null;
        for (AttributeInfo att : attributes) {
            NodeName attName = att.getNodeName();
            if (attName.hasURI(NamespaceUri.NULL)) {
                switch (attName.getLocalPart()) {
                    case "key":
                        if (!inMap) {
                            throw new XPathException(
                                    "xml-to-json: The key attribute is allowed only on elements within a map", ERR_INPUT);
                        }
                        key = StringView.of(att.getValue());
                        break;
                    case "escaped-key":
                        if (!inMap) {
                            throw new XPathException(
                                    "xml-to-json: The escaped-key attribute is allowed only on elements within a map", ERR_INPUT);
                        }
                        escapedKey = StringView.of(att.getValue());
                        break;
                    case "escaped":
                        boolean allowed = stack.size() == 1 || elemName.getLocalPart().equals("string");
                        // See bugs 29917 and 30077: at the top level, the escaped attribute is ignored
                        // whatever element it appears on
                        if (!allowed) {
                            throw new XPathException(
                                    "xml-to-json: The escaped attribute is allowed only on the <string> element",
                                    ERR_INPUT);
                        }
                        escapedAtt = StringView.of(att.getValue());
                        break;
                    default:
                        throw new XPathException("xml-to-json: Disallowed attribute in input: " + attName.getDisplayName(), ERR_INPUT);
                }
            } else if (attName.hasURI(NamespaceUri.FN)) {
                throw new XPathException("xml-to-json: Disallowed attribute in input: " + attName.getDisplayName(), ERR_INPUT);
            }
            // Attributes in other namespaces are ignored
        }

        if (!atStart) {
            output.accept(StringConstants.COMMA);
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
                    alreadyEscaped = StringConverter.StringToBoolean.INSTANCE
                            .convertString(escapedKey).asAtomic().effectiveBooleanValue();
                } catch (XPathException e) {
                    throw new XPathException("xml-to-json: Value of escaped-key attribute '" + Err.wrap(escapedKey) +
                                                     "' is not a valid xs:boolean", ERR_INPUT);
                }
            }
            key = (alreadyEscaped ?
                           handleEscapedString(key) :
                           escape(key, new EscapeOptions(false, !escapeSolidus, true, isControlChar)));

            UnicodeString normalizedKey = alreadyEscaped ? unescape(key) : key;
            boolean added = keyChecker.peek().add(normalizedKey);
            if (!added) {
                throw new XPathException("xml-to-json: duplicate key value " + Err.wrap(key), ERR_INPUT);
            }

            String base = indenting ? " : " : ":";
            output.accept(new UnicodeChar('"'))
                    .accept(key)
                    .accept(new UnicodeChar('"'))
                    .accept(BMPString.of(base));
        }
        String local = elemName.getLocalPart();
        checkParent(local, parent);
        switch (local) {
            case "array":
                if (indenting) {
                    indent(stack.size());
                    output.accept(U_LSQB_SPACE);
                } else {
                    output.accept(U_LSQB);
                }
                atStart = true;
                break;
            case "map":
                if (indenting) {
                    indent(stack.size());
                    output.accept(U_LCURLY_SPACE);
                } else {
                    output.accept(U_LCURLY);
                }
                atStart = true;
                keyChecker.push(new HashSet<>());
                break;
            case "null":
                //checkParent(local, parent);
                output.accept(U_NULL);
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
        textBuffer.clear();
    }

    private void checkParent(String child, String parent) throws XPathException {
        if ("null".equals(parent) || "string".equals(parent) || "number".equals(parent) || "boolean".equals(parent)) {
            throw new XPathException("xml-to-json: " + Err.indefiniteArticleFor(child, true) + " "
                    + Err.wrap(child, Err.ELEMENT) + " element cannot appear as a child of " + Err.wrap(parent, Err.ELEMENT), ERR_INPUT);
        }
    }

    private final static IntPredicateProxy isControlChar =
            IntPredicateLambda.of(c -> c < 31 || (c >= 127 && c <= 159));

    private final static UnicodeString ZERO = new UnicodeChar('0');
    private final static UnicodeString MINUS_ZERO = new Twine8("-0");
//    private final static EscapeOptions XmlToJsonEscapeOptions =
//            new EscapeOptions(false, !escapeSolidus, true, isControlChar);

    @Override
    public void endElement() throws XPathException {
        NodeName name = stack.pop();
        String local = name.getLocalPart();
        UnicodeString content = textBuffer.toUnicodeString();
        UnicodeString uContent = content;
        if (local.equals("boolean")) {
            try {
                boolean b = StringConverter.StringToBoolean.INSTANCE.convertString(uContent).asAtomic().effectiveBooleanValue();
                output.accept(b ? StringConstants.TRUE : StringConstants.FALSE);
            } catch (XPathException e) {
                throw new XPathException("xml-to-json: Value of <boolean> element is not a valid xs:boolean", ERR_INPUT);
            }
        } else if (local.equals("number")) {
            if (retainNumberFormat) {
                // 4.0 path
                try {
                    double d = StringToDouble11.getInstance().stringToNumber(uContent);
                    if (Double.isNaN(d) || Double.isInfinite(d)) {
                        throw new XPathException("xml-to-json: Infinity and NaN are not allowed", ERR_INPUT);
                    }
                } catch (NumberFormatException e) {
                    throw new XPathException("xml-to-json: Invalid number: " + textBuffer, ERR_INPUT);
                }
                uContent = Whitespace.trim(uContent);
                boolean negative = false;
                if (uContent.codePointAt(0) == '+') {
                    uContent = uContent.substring(1);
                } else if (uContent.codePointAt(0) == '-') {
                    uContent = uContent.substring(1);
                    negative = true;
                }
                // Strip unnecessary leading zeros
                while (uContent.codePointAt(0) == '0' && uContent.length() > 1 &&
                        uContent.codePointAt(1) != '.' &&
                        uContent.codePointAt(1) != 'e' && uContent.codePointAt(1) != 'E') {
                    uContent = uContent.substring(1);
                }
                if (uContent.codePointAt(0) == '.') {
                    uContent = new UnicodeChar('0').concat(uContent);
                }
                if (negative) {
                    uContent = new UnicodeChar('-').concat(uContent);
                }
                long point = uContent.indexOf('.');
                if (point >= 0) {
                    if (point == uContent.length() - 1) {
                        uContent = uContent.concat(new UnicodeChar('0'));
                    } else if (uContent.codePointAt(point + 1) == 'e' || uContent.codePointAt(point + 1) == 'E') {
                        uContent = uContent.substring(0, point + 1).concat(new UnicodeChar('0').concat(uContent.substring(point + 1)));
                    }
                }
                output.accept(uContent);
            } else {
                // 3.1 path
                try {
                    double d = StringToDouble11.getInstance().stringToNumber(uContent);
                    if (Double.isNaN(d) || Double.isInfinite(d)) {
                        throw new XPathException("xml-to-json: Infinity and NaN are not allowed", ERR_INPUT);
                    }
                    output.accept(new DoubleValue(d).getUnicodeStringValue());
                } catch (NumberFormatException e) {
                    throw new XPathException("xml-to-json: Invalid number: " + textBuffer, ERR_INPUT);
                }
            }

        } else if (local.equals("string")) {
            output.accept(U_QUOT);
            if (escaped) {
                output.accept(handleEscapedString(content));
            } else {
                output.accept(escape(content, new EscapeOptions(false, !escapeSolidus, true, isControlChar)));
            }
            output.accept(U_QUOT);
        } else if (!Whitespace.isAllWhite(uContent)) {
            throw new XPathException("xml-to-json: Element " + name.getDisplayName() + " must have no text content", ERR_INPUT);
        }
        textBuffer.clear();
        escaped = false;
        if (local.equals("array")) {
            output.accept(indenting ? U_SPACE_RSQB : U_RSQB);
        } else if (local.equals("map")) {
            keyChecker.pop();
            output.accept(indenting ? U_SPACE_RCURLY : U_RCURLY);
        }
        atStart = false;
    }

    private final static ARegularExpression stripper = ARegularExpression.compile("^(-?)(\\+?0*)([0-9].*)", "");
    private final static UnicodeString replacement = new Twine8("$1$3");
    private UnicodeString stripLeadingZeros(UnicodeString str) throws XPathException {
        return stripper.replace(str, replacement);
    }

    /**
     * Handle a string that is already escaped, and that should remain escaped, while normalizing
     * escape sequences to standard format
     *
     * @param str the input string
     * @return the result string
     * @throws XPathException if the input contains invalid escape sequences
     */

    private static UnicodeString handleEscapedString(UnicodeString str) throws XPathException {
        // check that escape sequences are valid
        unescape(str);
        TwineBuilder tb = TwineBuilder.make(str.length32());
        IntIterator cp = str.codePoints();
        boolean afterEscapeChar = false;
        while(cp.hasNext()) {
            int c = cp.next();
            if (c == '"' && !afterEscapeChar) {
                tb = tb.append('\\').append('"');
            } else if (c < 32 || (c >= 127 && c < 160)) {
                if (c == '\b') {
                    tb = tb.append('\\').append('b');
                } else if (c == '\f') {
                    tb = tb.append('\\').append('f');
                } else if (c == '\n') {
                    tb = tb.append('\\').append('n');
                } else if (c == '\r') {
                    tb = tb.append('\\').append('r');
                } else if (c == '\t') {
                    tb = tb.append('\\').append('t');
                } else {
                    tb = tb.append('\\').append('u').append(hex4(c, true));
                }
            } else if (c == '/' && !afterEscapeChar) {
                tb = tb.append('\\').append('/');
            } else {
                tb = tb.append(c);
            }
            afterEscapeChar = c == '\\' && !afterEscapeChar;
        }
        return tb.toUnicodeString();
    }

    /**
     * @param retainQuot true if the quotation marks should not be escaped
     * @param retainSlash true if solidus (forwards slash) should not be escaped
     * @param upperCaseHex true if hexadecimal digits are to be uppercase
     * @param hexEscapes a predicate identifying characters that should be output as hex escapes using \ u XXXX notation.
     *
     */
    public record EscapeOptions (
        boolean retainQuot,
        boolean retainSlash,
        boolean upperCaseHex,
        IntPredicateProxy hexEscapes
    ) {};

    /**
     * Escape a string using backslash escape sequences as defined in JSON
     *
     * @param in         the input string
     * @param options    options controlling escaping
     * @return the escaped string
     */

    public static UnicodeString escape(UnicodeString in, EscapeOptions options)  {
        TwineBuilder tb = TwineBuilder.make(in.length32());
        IntIterator cp = in.codePoints();
        while (cp.hasNext()) {
            int c = cp.next();
            switch (c) {
                case '"':
                    if (!options.retainQuot()) {
                        tb = tb.append('\\');
                    }
                    tb = tb.append('"');
                    break;
                case '\b':
                    tb = tb.append('\\').append('b');
                    break;
                case '\f':
                    tb = tb.append('\\').append('f');
                    break;
                case '\n':
                    tb = tb.append('\\').append('n');
                    break;
                case '\r':
                    tb = tb.append('\\').append('r');
                    break;
                case '\t':
                    tb = tb.append('\\').append('t');
                    break;
                case '/':
                    if (!options.retainSlash()) {
                        tb = tb.append('\\');
                    }
                    tb = tb.append('/');  // spec bug 29665, saxon bug 2849
                    break;
                case '\\':
                    tb = tb.append('\\').append('\\');
                    break;
                default:
                    if (options.hexEscapes().test(c)) {
                        boolean uc = options.upperCaseHex();
                        if (c > 65535) {
                            tb = tb.append('\\').append('u').append(hex4(UTF16CharacterSet.highSurrogate(c), uc));
                            tb = tb.append('\\').append('u').append(hex4(UTF16CharacterSet.lowSurrogate(c), uc));
                        } else {
                            tb = tb.append('\\').append('u').append(hex4(c, uc));
                        }
                    } else {
                        tb = tb.append(c);
                    }
                    break;
            }
        }
        return tb.toUnicodeString();
    }

    @CSharpReplaceBody(code="return c.ToString(upperCase ? \"X4\" : \"x4\");")
    public static String hex4(int c, boolean upperCase) {
        return String.format(upperCase ? "%04X" : "%04x", c);
    }

    @Override
    public void characters(UnicodeString chars, Location locationId, int properties) throws XPathException {
        if (!stack.empty() && !Whitespace.isAllWhite(chars)) {
            NodeName element = stack.peek();
            String local = element.getLocalPart();
            if (local.equals("map") || local.equals("array")) {
                throw new XPathException("xml-to-json: Element " + local + " must have no text content", ERR_INPUT);
            }
        }
        textBuffer.append(chars);
    }

    @Override
    public void processingInstruction(String name, UnicodeString data, Location locationId, int properties) throws XPathException {
        // no action
    }

    @Override
    public void comment(UnicodeString content, Location locationId, int properties) throws XPathException {
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
     * Add indentation whitespace to the buffer
     *
     * @param depth the level of indentation
     */

    private void indent(int depth) throws XPathException {
        output.accept(StringConstants.NEWLINE);
        for (int i = 0; i < depth; i++) {
            output.accept(StringConstants.SINGLE_SPACE);
        }
    }

    /**
     * Unescape a JSON string literal
     *
     * @param literal the string literal to be processed
     * @return the result of expanding escape sequences
     * @throws net.sf.saxon.trans.XPathException if the input contains invalid escape sequences
     */

    private static UnicodeString unescape(UnicodeString literal) throws XPathException {
        if (literal.indexOf('\\') < 0) {
            return literal;
        }
        UnicodeBuilder buffer = new UnicodeBuilder();
        for (int i = 0; i < literal.length(); i++) {
            int c = literal.codePointAt(i);
            if (c == '\\') {
                if (i++ == literal.length() - 1) {
                    throw new XPathException("String '" + Err.wrap(literal) + "' ends in backslash ", "FOJS0007");
                }
                switch (literal.codePointAt(i)) {
                    case '"':
                        buffer.append('"');
                        break;
                    case '\\':
                        buffer.append('\\');
                        break;
                    case '/':
                        buffer.append('/');
                        break;
                    case 'b':
                        buffer.append('\b');
                        break;
                    case 'f':
                        buffer.append('\f');
                        break;
                    case 'n':
                        buffer.append('\n');
                        break;
                    case 'r':
                        buffer.append('\r');
                        break;
                    case 't':
                        buffer.append('\t');
                        break;
                    case 'u':
                        try {
                            String hex = literal.substring(i + 1, i + 5).toString();
                            int code = Integer.parseInt(hex, 16);
                            buffer.append((char)code);
                            i += 4;
                        } catch (Exception e) {
                            throw new XPathException("Invalid hex escape sequence in string '" + Err.wrap(literal) + "'", "FOJS0007");
                        }
                        break;
                    default:
                        int next = literal.codePointAt(i);
                        String xx = next < 256 ? next + "" : "x" + Integer.toHexString(next);
                        throw new XPathException("Unknown escape sequence \\" + xx, "FOJS0007");
                }
            } else {
                buffer.append(c);
            }
        }
        return buffer.toUnicodeString();
    }
}

// Copyright (c) 2018-2026 Saxonica Limited
