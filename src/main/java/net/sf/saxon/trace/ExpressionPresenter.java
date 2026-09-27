////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.*;
import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.om.*;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.str.BMPString;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpSuppressWarnings;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.Whitespace;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.stream.StreamResult;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

/**
 * This class handles the display of an abstract expression tree in an XML format
 * with some slight resemblance to XQueryX
 */
public class ExpressionPresenter {

    private Configuration config;
    private Receiver receiver;
    private ComplexContentOutputter cco;
    private int depth = 0;
    private boolean inStartTag = false;
    private String nextRole = null;
    private final Stack<Expression> expressionStack = new Stack<>();
    private final Stack<String> nameStack = new Stack<>();
    private NamespaceMap namespaceMap = NamespaceMap.emptyMap();
    private NamespaceUri defaultNamespace;
    private ExportOptions options = new ExportOptions();


    /**
     * Make an uncommitted ExpressionPresenter. This must be followed by a call on init()
     */

    public ExpressionPresenter() {}

    /**
     * Make an ExpressionPresenter that writes indented output to the standard error output
     * destination of the Configuration
     *
     * @param config the Saxon configuration
     */

    public ExpressionPresenter(/*@NotNull*/ Configuration config) {
        this(config, config.getLogger());
    }

    /**
     * Make an ExpressionPresenter that writes indented output to a specified output stream
     *
     * @param config the Saxon configuration
     * @param out    the output destination
     */

    public ExpressionPresenter(Configuration config, StreamResult out) {
        this(config, out, false);
    }

    /**
     * Make an ExpressionPresenter that writes to a specified tree builder
     *
     * @param builder the tree builder
     */

    public ExpressionPresenter(Builder builder) {
        this();
        init(builder.getConfiguration(), builder, false);
    }


    /**
     * Make an ExpressionPresenter that writes indented output to a specified output stream,
     * with optional checksum
     *
     * @param config   the Saxon configuration
     * @param out      the output destination
     * @param checksum true if a checksum is to be written at the end of the file
     */

    public ExpressionPresenter(Configuration config, StreamResult out, boolean checksum) {
        init(config, out, checksum);
    }

    /**
     * Make an ExpressionPresenter that writes indented output to a specified output stream,
     * with checksumming
     *
     * @param config   the Saxon configuration
     * @param out      the output destination
     * @param checksum true if a checksum is to be written at the end of the file
     */

    public void init(Configuration config, StreamResult out, boolean checksum) {
        SerializationProperties props = makeDefaultProperties(config);
        if (config.getXMLVersion() == Configuration.XML11) {
            props.setProperty(OutputKeys.VERSION, "1.1");
        }
        try {
            receiver = config.getSerializerFactory().getReceiver(out, props);
            receiver = new NamespaceReducer(receiver);
            if (checksum) {
                receiver = new CheckSumFilter(receiver);
            }
            cco = new ComplexContentOutputter(receiver);
        } catch (XPathException err) {
            err.printStackTrace();
            throw new InternalError(err.getMessage());
        }
        this.config = config;
        try {
            cco.open();
            cco.startDocument(ReceiverOption.NONE);
        } catch (XPathException err) {
            err.printStackTrace();
            throw new InternalError(err.getMessage());
        }
    }

    /**
     * Make an ExpressionPresenter that writes indented output to a specified output stream,
     * with checksumming
     *
     * @param config   the Saxon configuration
     * @param out      the output destination
     * @param checksum true if a checksum is to be written at the end of the file
     */

    public void init(Configuration config, Receiver out, boolean checksum) {

        receiver = out;
        receiver = new NamespaceReducer(receiver);
        if (checksum) {
            receiver = new CheckSumFilter(receiver);
        }
        cco = new ComplexContentOutputter(receiver);
        this.config = config;
        try {
            cco.open();
            cco.startDocument(ReceiverOption.NONE);
        } catch (XPathException err) {
            err.printStackTrace();
            throw new InternalError(err.getMessage());
        }
    }


    /**
     * Make an ExpressionPresenter that writes indented output to a specified output stream
     *
     * @param config the Saxon configuration
     * @param out    the output stream
     */

    public ExpressionPresenter(Configuration config, Logger out) {
        this(config, out.asStreamResult());
    }

    /**
     * Make an ExpressionPresenter for a given Configuration using a user-supplied Receiver
     * to accept the output
     *
     * @param config   the Configuration
     * @param receiver the user-supplied Receiver
     */

    public ExpressionPresenter(Configuration config, /*@NotNull*/ Receiver receiver) {
        this.config = config;
        this.receiver = receiver;
        this.cco = new ComplexContentOutputter(receiver);
        try {
            cco.open();
            cco.startDocument(ReceiverOption.NONE);
        } catch (XPathException err) {
            err.printStackTrace();
            throw new InternalError(err.getMessage());
        }
    }

    public Receiver getReceiver() {
        return receiver;
    }

    public ComplexContentOutputter getComplexContentOutputter() {
        return cco;
    }

    /**
     * Set the default namespace, used for subsequent calls on startElement.
     * Must be consistent throughout the whole document
     *
     * @param namespace the default namespace
     */

    public void setDefaultNamespace(NamespaceUri namespace) {
        defaultNamespace = namespace;
        namespaceMap = namespaceMap.put("", namespace);
    }

    /**
     * Set the options
     *
     * @param options the options
     */

    public void setOptions(ExportOptions options) {
        this.options = options;
    }

    /**
     * Get the options
     *
     * @return the options, or null if none have been set
     */

    public ExportOptions getOptions() {
        return options;
    }

    /**
     * Say whether the package can be deployed to a different location, with a different base URI
     *
     * @param relocatable if true then static-base-uri() represents the deployed location of the package,
     *                    rather than its compile time location
     */

    public void setRelocatable(boolean relocatable) {
        this.options.relocatable = relocatable;
    }


    /**
     * Make a receiver, using default output properties, with serialized output going
     * to a specified OutputStream
     *
     * @param config the Configuration
     * @param out    the OutputStream
     * @return a Receiver that directs serialized output to this output stream
     * @throws XPathException if a serializer cannot be created
     */

    /*@Nullable*/
    public static Receiver defaultDestination(/*@NotNull*/ Configuration config, Logger out) throws XPathException {
        SerializationProperties props = makeDefaultProperties(config);
        return config.getSerializerFactory().getReceiver(out.asStreamResult(), props);
    }


    /**
     * Make a Properties object containing defaulted serialization attributes for the expression tree
     *
     * @param config the Configuration
     * @return a default set of properties
     */

    /*@NotNull*/
    public static SerializationProperties makeDefaultProperties(Configuration config) {
        SerializationProperties props = new SerializationProperties();
        props.setProperty(OutputKeys.METHOD, "xml");
        props.setProperty(OutputKeys.INDENT, "yes");
        if (config.isLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION)) {
            props.setProperty(SaxonOutputKeys.INDENT_SPACES, "1");
            props.setProperty(SaxonOutputKeys.LINE_LENGTH, "4096");
        }
        props.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        props.setProperty(OutputKeys.ENCODING, "utf-8");
        props.setProperty(OutputKeys.VERSION, "1.0");
        props.setProperty(SaxonOutputKeys.SINGLE_QUOTES, "yes");
        return props;
    }

    /**
     * Start an element representing an expression in the expression tree
     *
     * @param name the name of the element
     * @param expr the expression represented
     * @return the depth of the tree before this element: for diagnostics, this can be compared
     * with the value returned by endElement
     */

    public int startElement(String name, Expression expr) {
        Expression parent = expressionStack.isEmpty() ? null : expressionStack.peek();
        expressionStack.push(expr);
        nameStack.push("*" + name);
        int n = _startElement(name);
        if (parent == null || expr.getRetainedStaticContext() != parent.getRetainedStaticContext()) {
            if (expr.getRetainedStaticContext() == null) {
        //        throw new AssertionError("Export failure: no retained static context on " + expr.toShortString());
            } else {
                emitRetainedStaticContext(expr.getRetainedStaticContext(), parent == null ? null : parent.getRetainedStaticContext());
            }
        }
        String mod = expr.getLocation().getSystemId();
        if (mod != null && parent != null && (parent.getLocation().getSystemId() == null || !parent.getLocation().getSystemId().equals(mod))) {
            emitAttribute("module", truncatedModuleName(mod));
        }
        int lineNr = expr.getLocation().getLineNumber();
        if (parent == null || (parent.getLocation().getLineNumber() != lineNr && lineNr != -1)) {
            emitAttribute("line", lineNr + "");
        }
        return n;
    }

    private String truncatedModuleName(String module) {
        if (options.relocatable) {
            // If not exporting the base URI, cut the filename used for diagnostic location of errors down to its last component
            String[] parts = module.split("/");
            for (int p = parts.length - 1; p >= 0; p--) {
                if (!parts[p].isEmpty()) {
                    return parts[p];
                }
            }
        }
        return module;
    }

    public void emitRetainedStaticContext(RetainedStaticContext sc, RetainedStaticContext parentSC) {
        try {
            if (!options.suppressStaticContext && !options.relocatable && sc.getStaticBaseUri() != null && (parentSC == null || !sc.getStaticBaseUri().equals(parentSC.getStaticBaseUri()))) {
                emitAttribute("baseUri", sc.getStaticBaseUriString());
            }
            if (!sc.getDefaultCollationName().equals(NamespaceConstant.CODEPOINT_COLLATION_URI) &&
                    (parentSC == null || !sc.getDefaultCollationName().equals(parentSC.getDefaultCollationName()))) {
                emitAttribute("defaultCollation", sc.getDefaultCollationName());
            }
            if (!sc.getDefaultElementNamespace().isEmpty() &&
                    (parentSC == null || !sc.getDefaultElementNamespace().equals(parentSC.getDefaultElementNamespace()))) {
                emitAttribute("defaultElementNS", sc.getDefaultElementNamespace().toString());
            }
            if (!options.suppressStaticContext && (parentSC == null || !sc.declaresSameNamespaces(parentSC))) {
                boolean includeXmlNamespace = "JS".equals(getOptions().target) && getOptions().targetVersion >= 2;
                emitAttribute("ns", getNamespacesAsString(sc.getNamespaceMap(), includeXmlNamespace));
            }
        } catch (XPathException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * From a NamespaceMap, produce the SEF concise representation, for example "xs=~ xsl=~ example=file://example.com/"
     * @param sc the namespace map
     * @param includeXmlNamespace for compatibility with SaxonJS 2+, indicate that any explicit binding for the
     *                            XML namespace should be included in the list
     * @return the concise string representation of the namespace map
     * @throws XPathException if the namespace map contains unsuitable namespaces, for example namespace URIs containing
     * whitespace.
     */
    @CSharpSuppressWarnings("UnsafeIteratorConversion")
    public static String getNamespacesAsString(NamespaceMap sc, boolean includeXmlNamespace) throws XPathException {
        // Note that this will throw an UnsupportedOperationException if the context does
        // not allow namespace prefixes to be enumerated: that is, if it is a JAXP static context.
        // Fortunately we don't need to serialize XPath expressions in that scenario.
        UnicodeBuilder ub = new UnicodeBuilder();
        for (Iterator<String> iter = sc.iteratePrefixes(); iter.hasNext(); ) {
            String p = iter.next();
            if (includeXmlNamespace || !p.equals("xml")) { //Bugs 6198, 6274
                NamespaceUri uri = sc.getURIForPrefix(p, true);
                ub.append(p);
                ub.append("=");

                if (uri.equals(NamespaceUri.getUriForConventionalPrefix(p))) {
                    ub.append("~");
                } else {
                    UnicodeString uUri = uri.toUnicodeString();
                    if (Whitespace.containsWhitespace(uUri.codePoints())) {
                        throw new XPathException("Cannot export a stylesheet if namespaces contain whitespace: '" + uri + "'");
                    }
                    ub.append(uUri);
                }
                ub.append(" ");
            }
        }
        return ub.toString().trim();
    }

    /**
     * Start an element
     *
     * @param name the name of the element
     * @return the depth of the tree before this element: for diagnostics, this can be compared
     * with the value returned by endElement
     */

    public int startElement(String name) {
        nameStack.push(name);
        return _startElement(name);
    }

    private int _startElement(String name) {
        //System.err.println("start " + name + " at " + new XPathException("").getStackTrace().length);
        try {
//            if (inStartTag) {
//                cco.startContent();
//                inStartTag = false;
//            }
            NodeName nodeName;
            if (defaultNamespace == null) {
                nodeName = new NoNamespaceName(name);
            } else {
                nodeName = new FingerprintedQName("", defaultNamespace, name);
            }
            cco.startElement(nodeName, Untyped.INSTANCE, Loc.NONE, ReceiverOption.NONE);
            if (nextRole != null) {
                emitAttribute("role", nextRole);
                nextRole = null;
            }
        } catch (XPathException err) {
            err.printStackTrace();
            throw new InternalError(err.getMessage());
        }
//        inStartTag = true;
        return depth++;
    }

    /**
     * Set the role of the next element to be output
     *
     * @param role the value of the role output to be used
     */

    public void setChildRole(String role) {
        nextRole = role;
    }

    /**
     * Output an attribute node
     *
     * @param name  the name of the attribute
     * @param value the value of the attribute
     */

    public void emitAttribute(String name, String value) {
        if (value != null) {
            if (name.equals("module")) {
                value = truncatedModuleName(value);
            }
            try {
                cco.attribute(new NoNamespaceName(name), BuiltInAtomicType.UNTYPED_ATOMIC, value,
                              Loc.NONE, ReceiverOption.NONE);
            } catch (XPathException err) {
                err.printStackTrace();
                throw new InternalError(err.getMessage());
            }
        }
    }

    /**
     * Output a QName-valued attribute node
     *
     * @param name  the name of the attribute
     * @param value the value of the attribute
     */

    public void emitAttribute(String name, StructuredQName value) {
        String attVal = value.getEQName();
        try {
            cco.attribute(new NoNamespaceName(name), BuiltInAtomicType.UNTYPED_ATOMIC,
                          attVal, Loc.NONE, ReceiverOption.NONE);
        } catch (XPathException err) {
            err.printStackTrace();
            throw new InternalError(err.getMessage());
        }
    }

    /**
     * Output a namespace declaration. All namespaces should be declared at the top level.
     *
     * @param prefix the namespace prefix
     * @param uri    the namespace URI
     */

    public void namespace(String prefix, NamespaceUri uri) {
        try {
            cco.namespace(prefix, uri, ReceiverOption.NONE);
        } catch (XPathException e) {
            e.printStackTrace();
            throw new InternalError(e.getMessage());
        }
    }

    /**
     * End an element in the expression tree
     *
     * @return the depth of the tree after ending this element. For diagnostics, this can be compared with the
     * value returned by startElement()
     */

    public int endElement() {
        //System.err.println("end at " + new XPathException("").getStackTrace().length);
        try {
//            if (inStartTag) {
//                cco.startContent();
//                inStartTag = false;
//            }
              cco.endElement();
        } catch (XPathException err) {
            err.printStackTrace();
            throw new InternalError(err.getMessage());
        }
        String name = nameStack.pop();
        if (name.startsWith("*")) {
            expressionStack.pop();
        }
        return --depth;
    }

    /**
     * Start a child element in the output
     *
     * @param name the name of the child element
     */

    public void startSubsidiaryElement(String name) {
        startElement(name);
    }

    /**
     * End a child element in the output
     */

    public void endSubsidiaryElement() {
        endElement();
    }

    /**
     * Close the output
     */

    public void close() {
        try {
            if (receiver instanceof CheckSumFilter) {
                int c = ((CheckSumFilter) receiver).getChecksum();
                cco.processingInstruction(CheckSumFilter.SIGMA, BMPString.of(Integer.toHexString(c)),
                                          Loc.NONE, ReceiverOption.NONE);
                String digest = ((CheckSumFilter) receiver).getDigest();
                cco.processingInstruction(CheckSumFilter.SIGMA2, BMPString.of(digest),
                        Loc.NONE, ReceiverOption.NONE);
            }
            cco.endDocument();
            cco.close();
        } catch (XPathException err) {
            err.printStackTrace();
            throw new InternalError(err.getMessage());
        }
    }

    /**
     * Get the Saxon configuration
     *
     * @return the Saxon configuration
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Get the name pool
     *
     * @return the name pool
     */

    public NamePool getNamePool() {
        return config.getNamePool();
    }

    /**
     * Get the type hierarchy cache
     *
     * @return the type hierarchy cache
     */

    public TypeHierarchy getTypeHierarchy() {
        return config.getTypeHierarchy();
    }

    /**
     * Static method to escape a string using Javascript escaping conventions
     * @param in the string to be escaped
     * @return the escaped string
     */

    public static String jsEscape(String in) {
        StringBuilder out = new StringBuilder(in.length());
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            switch (c) {
                case '\'':
                    out.append("\\'");
                    break;
                case '"':
                    out.append("\\\"");
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
                case '\\':
                    out.append("\\\\");
                    break;
                default:
                    if (c < 32 || (c > 127 && c < 160) || c > UTF16CharacterSet.SURROGATE1_MIN) {
                        out.append("\\u");
                        StringBuilder hex = new StringBuilder(Integer.toHexString(c).toUpperCase());
                        while (hex.length() < 4) {
                            hex.insert(0, "0");
                        }
                        out.append(hex);
                    } else {
                        out.append(c);
                    }
                    break;
            }
        }
        return out.toString();
    }

    public static String jsUnescape(String in) {
        // Parse an escaped string, assuming it is valid
        StringBuilder sb = new StringBuilder(in.length());
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            if (c == '\\') {
                c = in.charAt(++i);
                switch (c) {
                    case '\'':
                    case '\\':
                    case '/':
                    case '"':
                        sb.append(c);
                        break;
                    case 'b':
                        sb.append("\b");
                        break;
                    case 'f':
                        sb.append("\f");
                        break;
                    case 'n':
                        sb.append("\n");
                        break;
                    case 'r':
                        sb.append("\r");
                        break;
                    case 't':
                        sb.append("\t");
                        break;
                    case 'u':
                        String hex = in.substring(i, i + 4);
                        sb.append(Integer.parseInt(hex));
                        i += 4;
                        break;
                    default:
                        throw new IllegalArgumentException();
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static class ExportOptions {
        public String target = "";
        public int targetVersion = 0;
        public boolean relocatable = false;
        public StylesheetPackage rootPackage;
        public Map<Component, Integer> componentMap;
        public Map<StylesheetPackage, Integer> packageMap;
        public boolean explaining;
        public boolean suppressStaticContext;
    }
}

