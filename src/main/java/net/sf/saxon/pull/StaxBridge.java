////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pull;


import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XmlProcessingIncident;
import net.sf.saxon.tree.tiny.CharSlice;
import net.sf.saxon.tree.tiny.CompressedWhitespace;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Whitespace;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.EntityDeclaration;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

/**
 * This class implements the Saxon PullProvider API on top of a standard StAX parser
 * (or any other StAX XMLStreamReader implementation)
 */

public class StaxBridge implements PullProvider {

    private XMLStreamReader reader;
    private AttributeMap attributes;
    private PipelineConfiguration pipe;
    private NamePool namePool;
    private HashMap<String, NodeName> nameCache = new HashMap<>();
    private Stack<NamespaceMap> namespaceStack = new Stack<>();
    private List unparsedEntities = null;
    Event currentEvent = Event.START_OF_INPUT;
    int depth = 0;
    boolean ignoreIgnorable = false;
    
    /**
     * Create a new instance of the class
     */

    public StaxBridge() {
        namespaceStack.push(NamespaceMap.emptyMap());
    }

    /**
     * Supply an input stream containing XML to be parsed. A StAX parser is created using
     * the JAXP XMLInputFactory.
     *
     * @param systemId    The Base URI of the input document
     * @param inputStream the stream containing the XML to be parsed
     * @throws XPathException if an error occurs creating the StAX parser
     */

    public void setInputStream(String systemId, InputStream inputStream) throws XPathException {
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            //XMLInputFactory factory = new WstxInputFactory();
            factory.setXMLReporter(new StaxErrorReporter());
            reader = factory.createXMLStreamReader(systemId, inputStream);
        } catch (XMLStreamException e) {
            throw new XPathException(e);
        }
    }

    /**
     * Supply an XMLStreamReader: the events reported by this XMLStreamReader will be translated
     * into PullProvider events
     *
     * @param reader the supplier of XML events, typically an XML parser
     */

    public void setXMLStreamReader(XMLStreamReader reader) {
        this.reader = reader;
    }

    /**
     * Set configuration information. This must only be called before any events
     * have been read.
     */

    @Override
    public void setPipelineConfiguration(PipelineConfiguration pipe) {
        this.pipe = new PipelineConfiguration(pipe);
        this.namePool = pipe.getConfiguration().getNamePool();
        ignoreIgnorable = pipe.getConfiguration().getParseOptions().getSpaceStrippingRule() != NoElementsSpaceStrippingRule.getInstance();
    }

    /**
     * Get configuration information.
     */

    @Override
    public PipelineConfiguration getPipelineConfiguration() {
        return pipe;
    }

    /**
     * Get the XMLStreamReader used by this StaxBridge. This is available only after
     * setInputStream() or setXMLStreamReader() has been called
     *
     * @return the instance of XMLStreamReader allocated when setInputStream() was called,
     *         or the instance supplied directly to setXMLStreamReader()
     */

    public XMLStreamReader getXMLStreamReader() {
        return reader;
    }

    /**
     * Get the name pool
     *
     * @return the name pool
     */

    public NamePool getNamePool() {
        return pipe.getConfiguration().getNamePool();
    }

    /**
     * Get the next event
     *
     * @return an integer code indicating the type of event. The code
     *         {@link net.sf.saxon.pull.PullProvider.Event#END_OF_INPUT} is returned at the end of the sequence.
     */

    @Override
    public Event next() throws XPathException {
        if (currentEvent == Event.START_OF_INPUT) {
            // StAX isn't reporting START_DOCUMENT so we supply it ourselves
            currentEvent = Event.START_DOCUMENT;
            return currentEvent;
        }
        if (currentEvent == Event.END_OF_INPUT || currentEvent == Event.END_DOCUMENT) {
            try {
                reader.close();
            } catch (XMLStreamException e) {
                throw new XPathException(e);
            }
            return Event.END_OF_INPUT;
        }
        try {
            if (reader.hasNext()) {
                int event = reader.next();
                //System.err.println("Read event " + event);
                currentEvent = translate(event);
                if (currentEvent == Event.START_ELEMENT) {
                    NamespaceMap nsMap = namespaceStack.peek();
                    int n = reader.getNamespaceCount();
                    for (int i = 0; i < n; i++) {
                        String prefix = reader.getNamespacePrefix(i);
                        String uri = reader.getNamespaceURI(i);
                        nsMap = nsMap.bind(prefix==null ? "" : prefix, uri==null ? "" : uri);
                    }
                    namespaceStack.push(nsMap);

                    int attCount = reader.getAttributeCount();
                    if (attCount == 0) {
                        attributes = EmptyAttributeMap.getInstance();
                    } else {
                        List<AttributeInfo> attList = new ArrayList<>();
                        NamePool pool = getNamePool();
                        for (int i=0; i<attCount; i++) {
                            QName name = reader.getAttributeName(i);
                            FingerprintedQName fName = new FingerprintedQName(
                                    name.getPrefix(), name.getNamespaceURI(), name.getLocalPart(), pool);
                            String value = reader.getAttributeValue(i);
                            AttributeInfo att = new AttributeInfo(fName, BuiltInAtomicType.UNTYPED_ATOMIC, value, Loc.NONE, 0);
                            attList.add(att);
                        }
                        attributes = AttributeMap.fromList(attList);
                    }
                } else if (currentEvent == Event.END_ELEMENT) {
                    namespaceStack.pop();
                }
            } else {
                currentEvent = Event.END_OF_INPUT;
            }
        } catch (XMLStreamException e) {
            String message = e.getMessage();
            // Following code recognizes the messages produced by the Sun Zephyr parser
            if (message.startsWith("ParseError at")) {
                int c = message.indexOf("\nMessage: ");
                if (c > 0) {
                    message = message.substring(c + 10);
                }
            }
            XPathException err = new XPathException("Error reported by XML parser: " + message, e);
            err.setErrorCode(SaxonErrorCode.SXXP0003);
            err.setLocator(translateLocation(e.getLocation()));
            throw err;
        }
        return currentEvent;
    }


    private Event translate(int event) throws XPathException {
        //System.err.println("EVENT " + event);
        switch (event) {
            case XMLStreamConstants.ATTRIBUTE:
                return Event.ATTRIBUTE;
            case XMLStreamConstants.CDATA:
                return Event.TEXT;
            case XMLStreamConstants.CHARACTERS:
                if (depth == 0 && reader.isWhiteSpace()) {
                    return next();
//                    } else if (reader.isWhiteSpace()) {
//                        return next();
                } else {
//                        System.err.println("TEXT[" + new String(reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength()) + "]");
//                        System.err.println("  ARRAY length " + reader.getTextCharacters().length + "[" + new String(reader.getTextCharacters(), 0, reader.getTextStart() + reader.getTextLength()) + "]");
//                        System.err.println("  START: " + reader.getTextStart() + " LENGTH " + reader.getTextLength());
                    return Event.TEXT;
                }
            case XMLStreamConstants.COMMENT:
                return Event.COMMENT;
            case XMLStreamConstants.DTD:
                unparsedEntities = (List) reader.getProperty("javax.xml.stream.entities");
                return next();
            case XMLStreamConstants.END_DOCUMENT:
                return Event.END_DOCUMENT;
            case XMLStreamConstants.END_ELEMENT:
                depth--;
                return Event.END_ELEMENT;
            case XMLStreamConstants.ENTITY_DECLARATION:
                return next();
            case XMLStreamConstants.ENTITY_REFERENCE:
                return next();
            case XMLStreamConstants.NAMESPACE:
                return Event.NAMESPACE;
            case XMLStreamConstants.NOTATION_DECLARATION:
                return next();
            case XMLStreamConstants.PROCESSING_INSTRUCTION:
                return Event.PROCESSING_INSTRUCTION;
            case XMLStreamConstants.SPACE:
                if (depth == 0) {
                    return next();
                } else if (ignoreIgnorable) {
                    // (Brave attempt, but Woodstox doesn't seem to report ignorable whitespace)
                    return next();
                } else {
                    return Event.TEXT;
                }
            case XMLStreamConstants.START_DOCUMENT:
                return next();  // we supplied the START_DOCUMENT ourselves
            //return START_DOCUMENT;
            case XMLStreamConstants.START_ELEMENT:
                depth++;
                return Event.START_ELEMENT;
            default:
                throw new IllegalStateException("Unknown StAX event " + event);


        }
    }

    /**
     * Get the event most recently returned by next(), or by other calls that change
     * the position, for example getStringValue() and skipToMatchingEnd(). This
     * method does not change the position of the PullProvider.
     *
     * @return the current event
     */

    @Override
    public Event current() {
        return currentEvent;
    }

    /**
     * Get the attributes associated with the current element. This method must
     * be called only after a START_ELEMENT event has been notified. The contents
     * of the returned AttributeMap are immutable.
     * <p>Attributes may be read before or after reading the namespaces of an element,
     * but must not be read after the first child node has been read, or after calling
     * one of the methods skipToEnd(), getStringValue(), or getTypedValue().</p>
     *
     * @return an AttributeCollection representing the attributes of the element
     *         that has just been notified.
     */

    @Override
    public AttributeMap getAttributes() {
        return attributes;
    }

    /**
     * Get the namespace declarations associated with the current element. This method must
     * be called only after a START_ELEMENT event has been notified. In the case of a top-level
     * START_ELEMENT event (that is, an element that either has no parent node, or whose parent
     * is not included in the sequence being read), the NamespaceDeclarations object returned
     * will contain a namespace declaration for each namespace that is in-scope for this element
     * node. In the case of a non-top-level element, the NamespaceDeclarations will contain
     * a set of namespace declarations and undeclarations, representing the differences between
     * this element and its parent.
     * <p>It is permissible for this method to return namespace declarations that are redundant.</p>
     * <p>The NamespaceDeclarations object is guaranteed to remain unchanged until the next START_ELEMENT
     * event, but may then be overwritten. The object should not be modified by the client.</p>
     * <p>Namespaces may be read before or after reading the attributes of an element,
     * but must not be read after the first child node has been read, or after calling
     * one of the methods skipToEnd(), getStringValue(), or getTypedValue().</p>
     */

    @Override
    public NamespaceBinding[] getNamespaceDeclarations() {
        int n = reader.getNamespaceCount();
        if (n == 0) {
            return NamespaceBinding.EMPTY_ARRAY;
        } else {
            NamespaceBinding[] bindings = new NamespaceBinding[n];
            for (int i = 0; i < n; i++) {
                String prefix = reader.getNamespacePrefix(i);
                if (prefix == null) {
                    prefix = "";
                }
                String uri = reader.getNamespaceURI(i);
                if (uri == null) {
                    uri = "";
                }
                bindings[i] = new NamespaceBinding(prefix, uri);
            }
            return bindings;
        }
    }

    /**
     * Skip the current subtree. This method may be called only immediately after
     * a START_DOCUMENT or START_ELEMENT event. This call returns the matching
     * END_DOCUMENT or END_ELEMENT event; the next call on next() will return
     * the event following the END_DOCUMENT or END_ELEMENT.
     */

    @Override
    public Event skipToMatchingEnd() throws XPathException {
        switch (currentEvent) {
            case START_DOCUMENT:
                currentEvent = Event.END_DOCUMENT;
                return currentEvent;
            case START_ELEMENT:
                try {
                    int skipDepth = 0;
                    while (reader.hasNext()) {
                        int event = reader.next();
                        if (event == XMLStreamConstants.START_ELEMENT) {
                            skipDepth++;
                        } else if (event == XMLStreamConstants.END_ELEMENT) {
                            if (skipDepth-- == 0) {
                                currentEvent = Event.END_ELEMENT;
                                return currentEvent;
                            }
                        }
                    }
                } catch (XMLStreamException e) {
                    throw new XPathException(e);
                }
                throw new IllegalStateException(
                        "Element start has no matching element end");
            default:
                throw new IllegalStateException(
                        "Cannot call skipToMatchingEnd() except when at start of element or document");

        }
    }

    /**
     * Close the event reader. This indicates that no further events are required.
     * It is not necessary to close an event reader after {@link net.sf.saxon.pull.PullProvider.Event#END_OF_INPUT} has
     * been reported, but it is recommended to close it if reading terminates
     * prematurely. Once an event reader has been closed, the effect of further
     * calls on next() is undefined.
     */

    @Override
    public void close() {
        try {
            reader.close();
        } catch (XMLStreamException e) {
            //
        }
    }

    /**
     * Get the NodeName identifying the name of the current node. This method
     * can be used after the {@link net.sf.saxon.pull.PullProvider.Event#START_ELEMENT}, {@link net.sf.saxon.pull.PullProvider.Event#PROCESSING_INSTRUCTION},
     * {@link net.sf.saxon.pull.PullProvider.Event#ATTRIBUTE}, or {@link net.sf.saxon.pull.PullProvider.Event#NAMESPACE} events. With some PullProvider implementations,
     * it can also be used after {@link net.sf.saxon.pull.PullProvider.Event#END_ELEMENT}, but this is not guaranteed.
     * If called at other times, the result is undefined and may result in an IllegalStateException.
     * If called when the current node is an unnamed namespace node (a node representing the default namespace)
     * the returned value is null.
     *
     * @return the NodeName. The NodeName can be used to obtain the prefix, local name,
     * and namespace URI.
     */
    @Override
    public NodeName getNodeName() {
        if (currentEvent == Event.START_ELEMENT || currentEvent == Event.END_ELEMENT) {
            String local = reader.getLocalName();
            String uri = reader.getNamespaceURI();
            // We keep a cache indexed by local name, on the assumption that most of the time, a given
            // local name will only ever be used with the same prefix and URI
            NodeName cached = nameCache.get(local);
            if (cached != null && cached.hasURI(uri == null ? "": uri) && cached.getPrefix().equals(reader.getPrefix())) {
                return cached;
            } else {
                int fp = namePool.allocateFingerprint(uri, local);
                if (uri == null) {
                    cached = new NoNamespaceName(local, fp);
                } else {
                    cached = new FingerprintedQName(reader.getPrefix(), uri, local, fp);
                }
                nameCache.put(local, cached);
                return cached;
            }
        } else if (currentEvent == Event.PROCESSING_INSTRUCTION) {
            String local = reader.getPITarget();
            return new NoNamespaceName(local);
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * Get the string value of the current element, text node, processing-instruction,
     * or top-level attribute or namespace node, or atomic value.
     * <p>In other situations the result is undefined and may result in an IllegalStateException.</p>
     * <p>If the most recent event was a {@link net.sf.saxon.pull.PullProvider.Event#START_ELEMENT}, this method causes the content
     * of the element to be read. The current event on completion of this method will be the
     * corresponding {@link net.sf.saxon.pull.PullProvider.Event#END_ELEMENT}. The next call of next() will return the event following
     * the END_ELEMENT event.</p>
     *
     * @return the String Value of the node in question, defined according to the rules in the
     *         XPath data model.
     */

    @Override
    public CharSequence getStringValue() throws XPathException {
        switch (currentEvent) {
            case TEXT:
                CharSlice cs = new CharSlice(reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength());
                return CompressedWhitespace.compress(cs);

            case COMMENT:
                return new CharSlice(reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength());

            case PROCESSING_INSTRUCTION:
                String s = reader.getPIData();
                // The BEA parser includes the separator space in the value,
                // which isn't part of the XPath data model
                return Whitespace.removeLeadingWhitespace(s);

            case START_ELEMENT:
                FastStringBuffer combinedText = null;
                try {
                    int depth = 0;
                    while (reader.hasNext()) {
                        int event = reader.next();
                        if (event == XMLStreamConstants.CHARACTERS) {
                            if (combinedText == null) {
                                combinedText = new FastStringBuffer(FastStringBuffer.C64);
                            }
                            combinedText.append(
                                    reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength());
                        } else if (event == XMLStreamConstants.START_ELEMENT) {
                            depth++;
                        } else if (event == XMLStreamConstants.END_ELEMENT) {
                            if (depth-- == 0) {
                                currentEvent = Event.END_ELEMENT;
                                if (combinedText != null) {
                                    return combinedText.condense();
                                } else {
                                    return "";
                                }
                            }
                        }
                    }
                } catch (XMLStreamException e) {
                    throw new XPathException(e);
                }
            default:
                throw new IllegalStateException("getStringValue() called when current event is " + currentEvent);

        }
    }

    /**
     * Get an atomic value. This call may be used only when the last event reported was
     * ATOMIC_VALUE. This indicates that the PullProvider is reading a sequence that contains
     * a free-standing atomic value; it is never used when reading the content of a node.
     */

    @Override
    public AtomicValue getAtomicValue() {
        throw new IllegalStateException();
    }

    /**
     * Get the type annotation of the current attribute or element node, or atomic value.
     * The result of this method is undefined unless the most recent event was START_ELEMENT,
     * ATTRIBUTE, or ATOMIC_VALUE.
     *
     * @return the type annotation.
     */

    @Override
    public SchemaType getSchemaType() {
        if (currentEvent == Event.START_ELEMENT) {
            return Untyped.getInstance();
        } else if (currentEvent == Event.ATTRIBUTE) {
            return BuiltInAtomicType.UNTYPED_ATOMIC;
        } else {
            return null;
        }
    }

    /**
     * Get the location of the current event.
     * For an event stream representing a real document, the location information
     * should identify the location in the lexical XML source. For a constructed document, it should
     * identify the location in the query or stylesheet that caused the node to be created.
     * A value of null can be returned if no location information is available.
     */

    @Override
    public net.sf.saxon.s9api.Location getSourceLocator() {
        return translateLocation(reader.getLocation());
    }

    /**
     * Translate a StAX Location object to a Saxon Locator
     *
     * @param location the StAX Location object
     * @return a Saxon/SAX SourceLocator object
     */

    private Loc translateLocation(Location location) {
        if (location == null) {
            return Loc.NONE;
        } else {
            return new Loc(location.getSystemId(), location.getLineNumber(), location.getColumnNumber());
        }
    }
    
    /**
     * Get a list of unparsed entities.
     *
     * @return a list of unparsed entities, or null if the information is not available, or
     *         an empty list if there are no unparsed entities. Each item in the list will
     *         be an instance of {@link net.sf.saxon.pull.UnparsedEntity}
     */

    @Override
    public List<UnparsedEntity> getUnparsedEntities() {
        if (unparsedEntities == null) {
            return null;
        }
        List<UnparsedEntity> list = new ArrayList<>(unparsedEntities.size());
        for (Object ent : unparsedEntities) {
            String name = null;
            String systemId = null;
            String publicId = null;
            String baseURI = null;
            if (ent instanceof EntityDeclaration) {
                // This is what we would expect from the StAX API spec
                EntityDeclaration ed = (EntityDeclaration) ent;
                name = ed.getName();
                systemId = ed.getSystemId();
                publicId = ed.getPublicId();
                baseURI = ed.getBaseURI();
            } else if (ent.getClass().getName().equals("com.ctc.wstx.ent.UnparsedExtEntity")) {
                // Woodstox 3.0.0 returns this: use introspection to get the data we need
                try {
                    Class<?> woodstoxClass = ent.getClass();
                    Class<?>[] noArgClasses = new Class<?>[0];
                    Object[] noArgs = new Object[0];
                    Method method = woodstoxClass.getMethod("getName", noArgClasses);
                    name = (String) method.invoke(ent, noArgs);
                    method = woodstoxClass.getMethod("getSystemId", noArgClasses);
                    systemId = (String) method.invoke(ent, noArgs);
                    method = woodstoxClass.getMethod("getPublicId", noArgClasses);
                    publicId = (String) method.invoke(ent, noArgs);
                    method = woodstoxClass.getMethod("getBaseURI", noArgClasses);
                    baseURI = (String) method.invoke(ent, noArgs);
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    //
                }
            }
            if (name != null) {
                if (baseURI != null && systemId != null) {
                    try {
                        systemId = ResolveURI.makeAbsolute(systemId, baseURI).toString();
                    } catch (URISyntaxException err) {
                        //
                    }
                }
                UnparsedEntity ue = new UnparsedEntity();
                ue.setName(name);
                ue.setSystemId(systemId);
                ue.setPublicId(publicId);
                ue.setBaseURI(baseURI);
                list.add(ue);
            }
        }
        return list;
    }

    /**
     * Error reporting class for StAX parser errors
     */

    private class StaxErrorReporter implements XMLReporter {

        @Override
        public void report(String message, String errorType,
                           Object relatedInformation, Location location) {
            XmlProcessingIncident err = new XmlProcessingIncident("Error reported by XML parser: " + message + " (" + errorType + ')');
            err.setLocation(translateLocation(location));
            pipe.getErrorReporter().report(err);
        }

    }

}

