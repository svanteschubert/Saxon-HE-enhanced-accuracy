////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.lib.StandardURIChecker;
import net.sf.saxon.om.*;
import net.sf.saxon.pull.NamespaceContextImpl;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Untyped;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.util.*;
import java.util.function.IntPredicate;

/**
 * This class implements the XmlStreamWriter interface, translating the events into Saxon
 * Receiver events. The Receiver can be anything: a serializer, a schema validator, a tree builder.
 *
 * <p>This class does not itself perform "namespace repairing" as defined in the interface Javadoc
 * (also referred to as "prefix defaulting" in the StaX JSR specification). In normal use, however,
 * the events emitted by this class are piped into a {@link NamespaceReducer} which performs a function
 * very similar to namespace repairing; specifically, it ensures that when elements and attribute are
 * generated with a given namespace URI and local name, then namespace declarations are generated
 * automatically without any explicit need to call the {@link #writeNamespace(String, String)} method.</p>
 *
 * <p>The class will check all names, URIs, and character content for conformance against XML well-formedness
 * rules unless the <code>checkValues</code> option is set to false.</p>
 *
 * <p>The implementation of this class is influenced not only by the Javadoc documentation of the
 * <code>XMLStreamWriter</code> interface (which is woefully inadequate), but also by the helpful
 * but unofficial interpretation of the spec to be found at
 * http://veithen.github.io/2009/11/01/understanding-stax.html</p>
 *
 * <p>Provided that the sequence of events sent to this class is legitimate, the events
 * sent to the supplied {@code Receiver} should constitute a <b>regular sequence</b>
 * as defined in the documentation of class {@link RegularSequenceChecker}.</p>
 *
 * @since 9.3. Rewritten May 2015 to fix bug 2357. Further modified in 9.7.0.2 in light of the discussion
 * of bug 2398, and the interpretation of the spec cited above.
 */
public class StreamWriterToReceiver implements XMLStreamWriter {

    private static boolean DEBUG = false;

    private static class Triple {
        public String prefix;
        public String uri;
        public String local;
        public String value;
    }

    private static class StartTag {
        public Triple elementName;
        public List<Triple> attributes;
        public List<Triple> namespaces;

        public StartTag() {
            elementName = new Triple();
            attributes = new ArrayList<>();
            namespaces = new ArrayList<>();
        }
    }

    private StartTag pendingTag = null;

    /**
     * The receiver to which events will be passed
     */

    private Receiver receiver;
    private Configuration config;

    /**
     * The Checker used for testing valid characters
     */

    private IntPredicate charChecker;

    /**
     * Flag to indicate whether names etc are to be checked for well-formedness
     */

    private boolean isChecking = false;

    /**
     * The current depth of element nesting. -1 indicates outside startDocument/endDocument; non-negative
     * values indicate the number of open start element tags
     */

    private int depth = -1;

    /**
     * Flag indicating that an empty element has been requested.
     */
    private boolean isEmptyElement;

    /**
     * inScopeNamespaces represents namespaces that have been declared in the XML stream
     */
    private NamespaceReducer inScopeNamespaces;

    /**
     * setPrefixes represents the namespace bindings that have been set, at each level of the stack,
     * using {@link #setPrefix}. There is one entry for each level of element nesting, and the entry is a list
     * of NamespaceBinding objects, that is, prefix/uri pairs
     */

    private Stack<List<NamespaceBinding>> setPrefixes = new Stack<>();

    /**
     * rootNamespaceContext is the namespace context supplied at the start, is the final fallback
     * for allocating a prefix to a URI
     */

    private javax.xml.namespace.NamespaceContext rootNamespaceContext = null;

    /**
     * Constructor. Creates a StreamWriter as a front-end to a given Receiver.
     *
     * @param receiver the Receiver that is to receive the events generated
     *                 by this StreamWriter.
     */
    public StreamWriterToReceiver(Receiver receiver) {
        // Events are passed through a NamespaceReducer which maintains the namespace context
        // It also eliminates duplicate namespace declarations, and creates extra namespace declarations
        // where needed to support prefix-uri mappings used on elements and attributes
        PipelineConfiguration pipe = receiver.getPipelineConfiguration();
        this.inScopeNamespaces = new NamespaceReducer(receiver);
        this.receiver = inScopeNamespaces;
        this.config = pipe.getConfiguration();
        //this.receiver = new TracingFilter(this.receiver);
        this.charChecker = pipe.getConfiguration().getValidCharacterChecker();
        this.setPrefixes.push(new ArrayList<>());
        this.rootNamespaceContext = new NamespaceContextImpl(new NamespaceResolver() {
            // See bug 2902; initialise rootNamespaceContext to an empty set of namespaces
            @Override
            public String getURIForPrefix(String prefix, boolean useDefault) {
                return null;
            }

            @Override
            public Iterator<String> iteratePrefixes() {
                List<String> e = Collections.emptyList();
                return e.iterator();
            }
        });
    }


    /**
     * Get the Receiver to which this StreamWriterToReceiver is writing events
     *
     * @return the destination Receiver
     */

    public Receiver getReceiver() {
        return receiver;
    }


    /**
     * Say whether names and values are to be checked for conformance with XML rules
     *
     * @param check true if names and values are to be checked. Default is false;
     */

    public void setCheckValues(boolean check) {
        this.isChecking = check;
    }

    /**
     * Ask whether names and values are to be checked for conformance with XML rules
     *
     * @return true if names and values are to be checked. Default is false;
     */

    public boolean isCheckValues() {
        return this.isChecking;
    }

    private void flushStartTag() throws XMLStreamException {
        if (depth == -1) {
            writeStartDocument();
        }
        if (pendingTag != null) {
            try {
                completeTriple(pendingTag.elementName, false);
                for (Triple t : pendingTag.attributes) {
                    completeTriple(t, true);
                }
                NodeName elemName;
                if (pendingTag.elementName.uri.isEmpty()) {
                    elemName = new NoNamespaceName(pendingTag.elementName.local);
                } else {
                    elemName = new FingerprintedQName(pendingTag.elementName.prefix, pendingTag.elementName.uri, pendingTag.elementName.local);
                }

                NamespaceMap nsMap = NamespaceMap.emptyMap();
                if (!pendingTag.elementName.uri.isEmpty()) {
                    nsMap = nsMap.put(pendingTag.elementName.prefix, pendingTag.elementName.uri);
                }

                for (Triple t : pendingTag.namespaces) {
                    if (t.prefix == null) {
                        t.prefix = "";
                    }
                    if (t.uri == null) {
                        t.uri = "";
                    }
                    if (!t.uri.isEmpty()) {
                        nsMap = nsMap.put(t.prefix, t.uri);
                    }
                }

                AttributeMap attributes = EmptyAttributeMap.getInstance();
                for (Triple t : pendingTag.attributes) {
                    NodeName attName;
                    if (t.uri.isEmpty()) {
                        attName = new NoNamespaceName(t.local);
                    } else {
                        attName = new FingerprintedQName(t.prefix, t.uri, t.local);
                        nsMap = nsMap.put(t.prefix, t.uri);
                    }
                    attributes = attributes.put(new AttributeInfo(attName, BuiltInAtomicType.UNTYPED_ATOMIC, t.value,
                                            Loc.NONE, ReceiverOption.NONE));
                }

                receiver.startElement(elemName, Untyped.getInstance(), attributes, nsMap, Loc.NONE, ReceiverOption.NONE);


                pendingTag = null;
                if (isEmptyElement) {
                    isEmptyElement = false;
                    depth--;
                    setPrefixes.pop();
                    receiver.endElement();
                }
            } catch (XPathException e) {
                throw new XMLStreamException(e);
            }
        }
    }

    /**
     * Fill in the unknown parts of a (prefix, uri, localname) triple by reference to the namespace
     * context.
     * <p>For details see the table in the <code>XMLStreamWriter</code> javadoc.</p>
     * @param t the (prefix, uri, localname) triple. Note that the prefix will be null if not supplied
     *          in the call, and will be "" if an empty string or null was supplied in the call; these
     *          cases are handled differently.
     * @param isAttribute true if this is an attribute name rather than an element name
     * @throws XMLStreamException if the name is invalid or incomplete
     */

    private void completeTriple(Triple t, boolean isAttribute) throws XMLStreamException {
        if (t.local == null) {
            throw new XMLStreamException("Local name of " + (isAttribute ? "Attribute" : "Element") + " is missing");
        }
        if (isChecking && !isValidNCName(t.local)) {
            throw new XMLStreamException("Local name of " + (isAttribute ? "Attribute" : "Element") +
                Err.wrap(t.local) + " is invalid");
        }
        if (t.prefix == null) {
            t.prefix = "";
        }
        if (t.uri == null) {
            t.uri = "";
        }
        if (isChecking && !t.uri.isEmpty() && isInvalidURI(t.uri)) {
            throw new XMLStreamException("Namespace URI " + Err.wrap(t.local) + " is invalid");
        }
        if (t.prefix.isEmpty() && !t.uri.isEmpty()) {
            t.prefix = getPrefixForUri(t.uri);
        }
    }

    private String getDefaultNamespace() {
        for (Triple t : pendingTag.namespaces) {
            if (t.prefix == null || t.prefix.isEmpty()) {
                return t.uri;
            }
        }
        return inScopeNamespaces.getURIForPrefix("", true);
    }

    private String getUriForPrefix(String prefix) {
        for (Triple t : pendingTag.namespaces) {
            if (prefix.equals(t.prefix)) {
                return t.uri;
            }
        }
        return inScopeNamespaces.getURIForPrefix(prefix, false);
    }

    private String getPrefixForUri(String uri) {
        for (Triple t : pendingTag.namespaces) {
            if (uri.equals(t.uri)) {
                return t.prefix == null ? "" : t.prefix;
            }
        }
        String setPrefix = getPrefix(uri);
        if (setPrefix != null) {
            return setPrefix;
        }
        Iterator<String> prefixes = inScopeNamespaces.iteratePrefixes();
        while (prefixes.hasNext()) {
            String p = prefixes.next();
            if (inScopeNamespaces.getURIForPrefix(p, false).equals(uri)) {
                return p;
            }
        }
        return "";
    }

    /**
     * Generate a start element event for an element in no namespace. Note: the element
     * will be in no namespace, even if {@link #setDefaultNamespace(String)} has been called;
     * this is Saxon's interpretation of the intended effect of the StAX specification.
     *
     * @param localName local name of the tag, may not be null
     * @throws XMLStreamException if names are being checked and the name is invalid, or if an error occurs downstream
     * @throws NullPointerException if the supplied local name is null
     */

    @Override
    public void writeStartElement(String localName) throws XMLStreamException {
        if (DEBUG) {
            System.err.println("StartElement " + localName);
        }
        checkNonNull(localName);
        setPrefixes.push(new ArrayList<>());
        flushStartTag();
        depth++;
        pendingTag = new StartTag();
        pendingTag.elementName.local = localName;
    }

    /**
     * Generate a start element event. The name of the element is determined by the supplied
     * namespace URI and local name. The prefix used for the element is determined by the in-scope
     * prefixes established using {@link #setPrefix(String, String)} and/or {@link #setDefaultNamespace(String)}
     * if these include the specified namespace URI; otherwise the namespace will become the default namespace
     * and there will therefore be no prefix.
     *
     * @param namespaceURI the namespace URI of the element name. Must not be null. A zero-length
     *                     string means the element is in no namespace.
     * @param localName    local part of the element name. Must not be null
     * @throws XMLStreamException if names are being checked and are found to be invalid, or if an
     * error occurs downstream in the pipeline.
     * @throws NullPointerException if either argument is null
     */

    @Override
    public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
        if (DEBUG) {
            System.err.println("StartElement Q{" + namespaceURI + "}" + localName);
        }
        checkNonNull(namespaceURI);
        checkNonNull(localName);
        setPrefixes.push(new ArrayList<>());
        flushStartTag();
        depth++;
        pendingTag = new StartTag();
        pendingTag.elementName.local = localName;
        pendingTag.elementName.uri = namespaceURI;

    }

    /**
     * Generate a start element event. The name of the element is determined by the supplied
     * namespace URI and local name, and the prefix will be as supplied in the call.
     *
     * @param prefix       the prefix of the element, must not be null. If the prefix is supplied as a zero-length
     *                     string, the element will nave no prefix (that is, the namespace URI will become the default
     *                     namespace).
     * @param localName    local name of the element, must not be null
     * @param namespaceURI the uri to bind the prefix to, must not be null. If the value is a zero-length string,
     *                     the element will be in no namespace; in this case any prefix is ignored.
     * @throws NullPointerException if any of the arguments is null.
     * @throws XMLStreamException if names are being checked and are found to be invalid, or if an
     * error occurs downstream in the pipeline.
     */

    @Override
    public void writeStartElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        if (DEBUG) {
            System.err.println("StartElement " + prefix + "=Q{" + namespaceURI + "}" + localName);
        }
        checkNonNull(prefix);
        checkNonNull(localName);
        checkNonNull(namespaceURI);
        setPrefixes.push(new ArrayList<>());
        flushStartTag();
        depth++;
        pendingTag = new StartTag();
        pendingTag.elementName.local = localName;
        pendingTag.elementName.uri = namespaceURI;
        pendingTag.elementName.prefix = prefix;
    }

    @Override
    public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
        checkNonNull(namespaceURI);
        checkNonNull(localName);
        flushStartTag();
        writeStartElement(namespaceURI, localName);
        isEmptyElement = true;
    }

    @Override
    public void writeEmptyElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        checkNonNull(prefix);
        checkNonNull(localName);
        checkNonNull(namespaceURI);
        flushStartTag();
        writeStartElement(prefix, localName, namespaceURI);
        isEmptyElement = true;
    }

    @Override
    public void writeEmptyElement(String localName) throws XMLStreamException {
        checkNonNull(localName);
        flushStartTag();
        writeStartElement(localName);
        isEmptyElement = true;
    }

    @Override
    public void writeEndElement() throws XMLStreamException {
        if (DEBUG) {
            System.err.println("EndElement" + depth);
        }
        if (depth <= 0) {
            throw new IllegalStateException("writeEndElement with no matching writeStartElement");
        }
//        if (isEmptyElement) {
//            throw new IllegalStateException("writeEndElement called for an empty element");
//        }
        try {
            flushStartTag();
            setPrefixes.pop();
            receiver.endElement();
            depth--;
        } catch (XPathException err) {
            throw new XMLStreamException(err);
        }
    }

    @Override
    public void writeEndDocument() throws XMLStreamException {
        if (depth == -1) {
            throw new IllegalStateException("writeEndDocument with no matching writeStartDocument");
        }
        try {
            flushStartTag();
            while (depth > 0) {
                writeEndElement();
            }
            receiver.endDocument();
            depth = -1;
        } catch (XPathException err) {
            throw new XMLStreamException(err);
        }
    }

    @Override
    public void close() throws XMLStreamException {
        if (depth >= 0) {
            writeEndDocument();
        }
        try {
            receiver.close();
        } catch (XPathException err) {
            throw new XMLStreamException(err);
        }
    }

    @Override
    public void flush() {
        // no action
    }

    @Override
    public void writeAttribute(String localName, String value) {
        checkNonNull(localName);
        checkNonNull(value);
        if (pendingTag == null) {
            throw new IllegalStateException("Cannot write attribute when not in a start tag");
        }
        Triple t = new Triple();
        t.local = localName;
        t.value = value;
        pendingTag.attributes.add(t);
    }

    @Override
    public void writeAttribute(String prefix, String namespaceURI, String localName, String value) {
        checkNonNull(prefix);
        checkNonNull(namespaceURI);
        checkNonNull(localName);
        checkNonNull(value);
        if (pendingTag == null) {
            throw new IllegalStateException("Cannot write attribute when not in a start tag");
        }
        Triple t = new Triple();
        t.prefix = prefix;
        t.uri = namespaceURI;
        t.local = localName;
        t.value = value;
        pendingTag.attributes.add(t);
    }

    @Override
    public void writeAttribute(String namespaceURI, String localName, String value) {
        checkNonNull(namespaceURI);
        checkNonNull(localName);
        checkNonNull(value);
        Triple t = new Triple();
        t.uri = namespaceURI;
        t.local = localName;
        t.value = value;
        pendingTag.attributes.add(t);

    }

    /**
     * Emits a namespace declaration event.
     *
     * <p>If the prefix argument to this method is the empty string,
     * "xmlns", or null this method will delegate to writeDefaultNamespace.</p>
     *
     * <p>This method does not change the name of any element or attribute; its only use is to write
     * additional or redundant namespace declarations. With this implementation of XMLStreamWriter,
     * this method is needed only to generate namespace declarations for prefixes that do not appear
     * in element or attribute names. If an attempt is made to generate a namespace declaration that
     * conflicts with the prefix-uri bindings in scope for element and attribute names, an exception
     * occurs.</p>
     *
     * @param prefix       the prefix to bind this namespace to
     * @param namespaceURI the uri to bind the prefix to
     * @throws IllegalStateException if the current state does not allow Namespace writing
     * @throws XMLStreamException if things go wrong
     */

    @Override
    public void writeNamespace(String prefix, String namespaceURI) throws XMLStreamException {
        if (prefix == null || prefix.equals("") || prefix.equals("xmlns")) {
            writeDefaultNamespace(namespaceURI);
        } else {
            checkNonNull(namespaceURI);
            if (pendingTag == null) {
                throw new IllegalStateException("Cannot write namespace when not in a start tag");
            }
            Triple t = new Triple();
            t.uri = namespaceURI;
            t.prefix = prefix;
            pendingTag.namespaces.add(t);
        }

    }

    /**
     * Emits a default namespace declaration
     *
     * <p>This method does not change the name of any element or attribute; its only use is to write
     * additional or redundant namespace declarations. With this implementation of XMLStreamWriter,
     * this method is needed only to generate namespace declarations for prefixes that do not appear
     * in element or attribute names. If an attempt is made to generate a namespace declaration that
     * conflicts with the prefix-uri bindings in scope for element and attribute names, an exception
     * occurs.</p>
     *
     * @param namespaceURI the uri to bind the default namespace to
     * @throws IllegalStateException if the current state does not allow Namespace writing
     */

    @Override
    public void writeDefaultNamespace(String namespaceURI) {
        checkNonNull(namespaceURI);
        if (pendingTag == null) {
            throw new IllegalStateException("Cannot write namespace when not in a start tag");
        }
        Triple t = new Triple();
        t.uri = namespaceURI;
        pendingTag.namespaces.add(t);

    }

    @Override
    public void writeComment(String data) throws XMLStreamException {
        flushStartTag();
        if (data == null) {
            data = "";
        }
        try {
            if (!isValidChars(data)) {
                throw new IllegalArgumentException("Invalid XML character in comment: " + data);
            }
            if (isChecking && data.contains("--")) {
                throw new IllegalArgumentException("Comment contains '--'");
            }
            receiver.comment(data, Loc.NONE, ReceiverOption.NONE);
        } catch (XPathException err) {
            throw new XMLStreamException(err);
        }
    }

    @Override
    public void writeProcessingInstruction(String target) throws XMLStreamException {
        writeProcessingInstruction(target, "");
    }

    @Override
    public void writeProcessingInstruction(/*@NotNull*/ String target, /*@NotNull*/ String data) throws XMLStreamException {
        checkNonNull(target);
        checkNonNull(data);
        flushStartTag();
        try {
            if (isChecking) {
                if (!isValidNCName(target) || "xml".equalsIgnoreCase(target)) {
                    throw new IllegalArgumentException("Invalid PITarget: " + target);
                }
                if (!isValidChars(data)) {
                    throw new IllegalArgumentException("Invalid character in PI data: " + data);
                }
            }
            receiver.processingInstruction(target, data, Loc.NONE, ReceiverOption.NONE);
        } catch (XPathException err) {
            throw new XMLStreamException(err);
        }
    }

    @Override
    public void writeCData(/*@NotNull*/ String data) throws XMLStreamException {
        checkNonNull(data);
        flushStartTag();
        writeCharacters(data);
    }

    @Override
    public void writeDTD(String dtd) throws XMLStreamException {
        // no-op
    }

    @Override
    public void writeEntityRef(String name) {
        throw new UnsupportedOperationException("writeEntityRef");
    }

    @Override
    public void writeStartDocument() throws XMLStreamException {
        writeStartDocument("utf-8", "1.0");
    }

    @Override
    public void writeStartDocument(/*@Nullable*/ String version) throws XMLStreamException {
        writeStartDocument("utf-8", version);
    }

    @Override
    public void writeStartDocument(/*@Nullable*/ String encoding, /*@Nullable*/ String version) throws XMLStreamException {
        if (encoding == null) {
            encoding = "utf-8";
        }
        if (version == null) {
            version = "1.0";
        }
        if (depth != -1) {
            throw new IllegalStateException("writeStartDocument must be the first call");
        }
        try {
            receiver.open();
            receiver.startDocument(ReceiverOption.NONE);
        } catch (XPathException err) {
            throw new XMLStreamException(err);
        }
        depth = 0;
    }

    @Override
    public void writeCharacters(String text)
        throws XMLStreamException {
        checkNonNull(text);
        flushStartTag();
        if (!isValidChars(text)) {
            throw new IllegalArgumentException("illegal XML character: " + text);
        }
        try {
            receiver.characters(text, Loc.NONE, ReceiverOption.NONE);
        } catch (XPathException err) {
            throw new XMLStreamException(err);
        }
    }

    @Override
    public void writeCharacters(char[] text, int start, int len)
        throws XMLStreamException {
        checkNonNull(text);
        writeCharacters(new String(text, start, len));
    }

    @Override
    public String getPrefix(String uri) {
        for (int i=setPrefixes.size()-1; i>=0; i--) {
            List<NamespaceBinding> bindings = setPrefixes.get(i);
            for (int j=bindings.size()-1; j>=0; j--) {
                NamespaceBinding binding = bindings.get(j);
                if (binding.getURI().equals(uri)) {
                    return binding.getPrefix();
                }
            }
        }
        if (rootNamespaceContext != null) {
            return rootNamespaceContext.getPrefix(uri);
        }
        return null;
    }

    @Override
    public void setPrefix(String prefix, String uri) {
        // See Saxon bug 2398: this should have stack-like effect
        checkNonNull(prefix);
        if (uri == null) {
            uri = "";
        }
        if (isInvalidURI(uri)) {
            throw new IllegalArgumentException("Invalid namespace URI: " + uri);
        }
        if (!"".equals(prefix) && !isValidNCName(prefix)) {
            throw new IllegalArgumentException("Invalid namespace prefix: " + prefix);
        }
        setPrefixes.peek().add(new NamespaceBinding(prefix, uri));
    }

    @Override
    public void setDefaultNamespace(String uri) {
        setPrefix("", uri);
    }

    @Override
    public void setNamespaceContext(javax.xml.namespace.NamespaceContext context) {
        // Note, we do not enforce the rule that this can only be called once, because the spec is self-contradictory
        // on this point.
        if (depth > 0) {
            throw new IllegalStateException("setNamespaceContext may only be called at the start of the document");
        }
        // Unfortunately the JAXP NamespaceContext class does not allow us to discover all the namespaces
        // that were declared, nor to declare new ones. So we have to retain it separately
        rootNamespaceContext = context;
    }

    /**
     * Return the current namespace context.
     *
     * <p>The specification of this method is hopelessly vague. This method returns a namespace context
     * that contains the namespaces declared using {@link #setPrefix(String, String)} calls that are
     * in-scope at the time, overlaid on the root namespace context that was defined using
     * {@link #setNamespaceContext(NamespaceContext)}. The namespaces bound using {@link #setPrefix(String, String)}
     * are copied, and are therefore unaffected by subsequent changes, but the root namespace context
     * is not copied, because the NamespaceContext interface provides no way of doing so.</p>
     * @return a copy of the current namespace context.
     */
    @Override
    public javax.xml.namespace.NamespaceContext getNamespaceContext() {
        return new NamespaceContext() {
            final NamespaceContext rootNamespaceContext = StreamWriterToReceiver.this.rootNamespaceContext;
            final Map<String, String> bindings = new HashMap<>();

            {
                for (List<NamespaceBinding> list : setPrefixes) {
                    for (NamespaceBinding binding : list) {
                        bindings.put(binding.getPrefix(), binding.getURI());
                    }
                }
            }

            @Override
            public String getNamespaceURI(String prefix) {
                String uri = bindings.get(prefix);
                if (uri != null) {
                    return uri;
                }
                return rootNamespaceContext.getNamespaceURI(prefix);
            }

            @Override
            public String getPrefix(String namespaceURI) {
                for (Map.Entry<String, String> entry : bindings.entrySet()) {
                    if (entry.getValue().equals(namespaceURI)) {
                        return entry.getKey();
                    }
                }
                return rootNamespaceContext.getPrefix(namespaceURI);
            }

            @Override
            public Iterator<String> getPrefixes(String namespaceURI) {
                List<String> prefixes = new ArrayList<>();
                for (Map.Entry<String, String> entry : bindings.entrySet()) {
                    if (entry.getValue().equals(namespaceURI)) {
                        prefixes.add(entry.getKey());
                    }
                }
                Iterator root = rootNamespaceContext.getPrefixes(namespaceURI);
                while (root.hasNext()) {
                    prefixes.add((String)root.next());
                }
                return prefixes.iterator();
            }
        };
    }

    @Override
    public Object getProperty(String name) throws IllegalArgumentException {
        if (name.equals("javax.xml.stream.isRepairingNamespaces")) {
            return receiver instanceof NamespaceReducer;
        } else {
            throw new IllegalArgumentException(name);
        }
    }

    /**
     * Test whether a supplied name is a valid NCName
     *
     * @param name the name to be tested
     * @return true if the name is valid or if checking is disabled
     */

    private boolean isValidNCName(String name) {
        return !isChecking || NameChecker.isValidNCName(name);
    }

    /**
     * Test whether a supplied character string is valid in XML
     *
     * @param text the string to be tested
     * @return true if the string is valid or if checking is disabled
     */

    private boolean isValidChars(String text) {
        return !isChecking || (UTF16CharacterSet.firstInvalidChar(text, charChecker) == -1);
    }

    /**
     * Test whether a supplied namespace URI is a valid URI
     *
     * @param uri the namespace URI to be tested
     * @return true if the name is invalid (unless checking is disabled)
     */

    private boolean isInvalidURI(String uri) {
        return isChecking && !StandardURIChecker.getInstance().isValidURI(uri);
    }

    private void checkNonNull(Object value) {
        if (value == null) {
            throw new NullPointerException();
        }
    }

}

