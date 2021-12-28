package com.saxonica.xqj.pull;

import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.om.*;
import net.sf.saxon.pull.NamespaceContextImpl;
import net.sf.saxon.pull.PullProvider;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingleNodeIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.MissingComponentException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Whitespace;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.List;

/**
 * This class bridges PullProvider events to XMLStreamReader (Stax) events. That is, it acts
 * as an XMLStreamReader, fetching the underlying data from a PullProvider.
 * <p>A PullProvider may provide access to any XDM sequence, whereas an XMLStreamReader always
 * reads a document. The conversion of a sequence to a document follows the rules for
 * "normalizing" a sequence in the Serialization specification: for example, atomic values are
 * converted into text nodes, with adjacent atomic values being space-separated.</p>
 */
public class PullToStax implements XMLStreamReader {

    private PullNamespaceReducer provider;
    private boolean previousAtomic;
    private FastStringBuffer currentTextNode = new FastStringBuffer(FastStringBuffer.C64);
    private int currentStaxEvent = XMLStreamConstants.START_DOCUMENT;
    private XPathException pendingException = null;

    /**
     * Create an XMLStreamReader that reads the contents of a tree rooted at a document or element
     * node
     * @param node the node to be read. This must be a document or element node
     * @throws IllegalArgumentException if the node is not a document or element node
     */

    public static XMLStreamReader getXMLStreamReader(NodeInfo node) {
        int kind = node.getNodeKind();
        if (!(kind == Type.DOCUMENT || kind == Type.ELEMENT)) {
            throw new IllegalArgumentException("Node must be a document or element node");
        }
        PullFromIterator pull = new PullFromIterator(SingleNodeIterator.makeIterator(node));
        pull.setPipelineConfiguration(node.getConfiguration().makePipelineConfiguration());
        return new PullToStax(pull);
    }

    /**
     * Create a PullToStax instance, which wraps a Saxon PullProvider as a Stax XMLStreamReader
     *
     * @param provider the Saxon PullProvider from which the events will be read
     */

    public PullToStax(PullProvider provider) {
        if (provider instanceof PullNamespaceReducer) {
            this.provider = (PullNamespaceReducer) provider;
        } else {
            this.provider = new PullNamespaceReducer(provider);
        }
    }

    @Override
    public int getAttributeCount() {
        return provider.getAttributes().size();
    }

    @Override
    public boolean isAttributeSpecified(int i) {
        return true;
    }

    private List<AttributeInfo> getAttributeList() {
        return provider.getAttributes().asList();
    }

    private AttributeInfo getAttributeInfo(int index) {
        return provider.getAttributes().itemAt(index);
    }

    @Override
    public QName getAttributeName(int i) {
        NodeName attName = getAttributeInfo(i).getNodeName();
        return new QName(attName.getURI(), attName.getLocalPart(), attName.getPrefix());
    }

    @Override
    public String getAttributeLocalName(int i) {
        return getAttributeInfo(i).getNodeName().getLocalPart();
    }

    @Override
    public String getAttributeNamespace(int i) {
        return getAttributeInfo(i).getNodeName().getURI();
    }

    @Override
    public String getAttributePrefix(int i) {
        return getAttributeInfo(i).getNodeName().getPrefix();
    }

    @Override
    public String getAttributeType(int i) {
        try {
            AttributeInfo info = getAttributeInfo(i);
            if (info.isId()) {
                return "ID";
            } else if (info.getType().isIdRefType()) {
                return "IDREFS";
            } else {
                return "CDATA";
            }
        } catch (MissingComponentException e) {
            return "CDATA";
        }
    }

    @Override
    public String getAttributeValue(int i) {
        return getAttributeInfo(i).getValue();
    }

    @Override
    public String getAttributeValue(String uri, String local) {
        return provider.getAttributes().getValue(uri, local);
    }

    @Override
    public int getEventType() {
        return currentStaxEvent;
    }

    /**
     * Return the number of namespaces declared on this element.
     * @return the number of in-scope namespaces
     */

    @Override
    public int getNamespaceCount() {
        NamespaceBinding[] bindings = provider.getNamespaceDeclarations();
        int count = bindings.length;
        for (int i = 0; i < count; i++) {
            if (bindings[i] == null) {
                return i;
            }
        }
        return count;
    }

    @Override
    public String getText() {
        if (previousAtomic) {
            return currentTextNode.toString();
        } else {
            try {
                return provider.getStringValue().toString();
            } catch (XPathException e) {
                pendingException = e;
                return "";
            }
        }
    }

    @Override
    public int getTextLength() {
        if (previousAtomic) {
            return currentTextNode.length();
        } else {
            try {
                return provider.getStringValue().length();
            } catch (XPathException e) {
                pendingException = e;
                return 0;
            }
        }
    }

    @Override
    public int getTextStart() {
        return 0;
    }

    @Override
    public char[] getTextCharacters() {
        if (previousAtomic) {
            return currentTextNode.toCharArray();
        } else {
            try {
                String stringValue = provider.getStringValue().toString();
                char[] chars = new char[stringValue.length()];
                stringValue.getChars(0, chars.length, chars, 0);
                return chars;
            } catch (XPathException e) {
                pendingException = e;
                return new char[0];
            }
        }
    }


    @Override
    public int getTextCharacters(int sourceStart, char[] target, int targetStart, int length) throws XMLStreamException {
        if (pendingException != null) {
            throw new XMLStreamException(pendingException);
        }
        if (previousAtomic) {
            int end = sourceStart + length;
            if (end > currentTextNode.length()) {
                end = currentTextNode.length();
            }
            currentTextNode.getChars(sourceStart, end, target, targetStart);
            return end - sourceStart;
        } else {
            try {
                String stringValue = provider.getStringValue().subSequence(sourceStart, sourceStart + length).toString();
                stringValue.getChars(0, length, target, targetStart);
                return length;
            } catch (XPathException e) {
                pendingException = e;
                return 0;
            }
        }
    }

    @Override
    public int next() throws XMLStreamException {
        if (pendingException != null) {
            throw new XMLStreamException(pendingException);
        }
        if (currentStaxEvent == END_DOCUMENT) {
            throw new IllegalStateException("next() called after END_DOCUMENT");
            // Javadoc is unclear. See Saxon bug 3806, resulting in JDK bug JDK-8204329
        }
        PullProvider.Event p;
        try {
            p = provider.next();
        } catch (XPathException e) {
            throw new XMLStreamException(e);
        }
        switch (p) {
            case START_OF_INPUT:
                return next();
            case ATOMIC_VALUE:
                currentTextNode.setLength(0);
                if (previousAtomic) {
                    currentTextNode.cat(' ');
                }
                currentTextNode.append(provider.getAtomicValue().getStringValue());
                currentStaxEvent = XMLStreamConstants.CHARACTERS;
                break;
            case START_DOCUMENT:
                // STAX doesn't actually report START_DOCUMENT: it's the initial state before reading any events
                currentStaxEvent = XMLStreamConstants.START_DOCUMENT;
                return next();
            case END_DOCUMENT:
                currentStaxEvent = XMLStreamConstants.END_DOCUMENT;
                break;
            case START_ELEMENT:
                currentStaxEvent = XMLStreamConstants.START_ELEMENT;
                break;
            case END_ELEMENT:
                currentStaxEvent = XMLStreamConstants.END_ELEMENT;
                break;
            case TEXT:
                currentStaxEvent = XMLStreamConstants.CHARACTERS;
                break;
            case COMMENT:
                currentStaxEvent = XMLStreamConstants.COMMENT;
                break;
            case PROCESSING_INSTRUCTION:
                currentStaxEvent = XMLStreamConstants.PROCESSING_INSTRUCTION;
                break;
            case END_OF_INPUT:
                currentStaxEvent = XMLStreamConstants.END_DOCUMENT;
                break;
            case ATTRIBUTE:
                throw new XMLStreamException("Free-standing attributes cannot be serialized");
            case NAMESPACE:
                throw new XMLStreamException("Free-standing namespace nodes cannot be serialized");
            default:
                throw new IllegalStateException("Unknown Stax event " + p);

        }
        previousAtomic = p == PullProvider.Event.ATOMIC_VALUE;
        return currentStaxEvent;
    }

    @Override
    public int nextTag() throws XMLStreamException {
        if (pendingException != null) {
            throw new XMLStreamException(pendingException);
        }
        int eventType = next();
        while ((eventType == XMLStreamConstants.CHARACTERS && isWhiteSpace()) // skip whitespace
                || (eventType == XMLStreamConstants.CDATA && isWhiteSpace())
                // skip whitespace
                || eventType == XMLStreamConstants.SPACE
                || eventType == XMLStreamConstants.PROCESSING_INSTRUCTION
                || eventType == XMLStreamConstants.COMMENT
                ) {
            eventType = next();
        }
        if (eventType != XMLStreamConstants.START_ELEMENT && eventType != XMLStreamConstants.END_ELEMENT) {
            throw new XMLStreamException("expected start or end tag", getLocation());
        }
        return eventType;
    }

    @Override
    public void close() throws XMLStreamException {
        if (pendingException != null) {
            throw new XMLStreamException(pendingException);
        }
        provider.close();
    }

    @Override
    public boolean hasName() {
        return currentStaxEvent == XMLStreamConstants.START_ELEMENT ||
                currentStaxEvent == XMLStreamConstants.END_ELEMENT;
    }

    @Override
    public boolean hasNext() throws XMLStreamException {
        if (pendingException != null) {
            throw new XMLStreamException(pendingException);
        }
        return currentStaxEvent != XMLStreamConstants.END_DOCUMENT;
    }

    @Override
    public boolean hasText() {
        return currentStaxEvent == XMLStreamConstants.CHARACTERS || currentStaxEvent == XMLStreamConstants.COMMENT;
    }

    @Override
    public boolean isCharacters() {
        return currentStaxEvent == XMLStreamConstants.CHARACTERS;
    }

    @Override
    public boolean isEndElement() {
        return currentStaxEvent == XMLStreamConstants.END_ELEMENT;
    }

    @Override
    public boolean isStandalone() {
        return false;
    }

    @Override
    public boolean isStartElement() {
        return currentStaxEvent == XMLStreamConstants.START_ELEMENT;
    }

    @Override
    public boolean isWhiteSpace() {
        try {
            return currentStaxEvent == XMLStreamConstants.CHARACTERS && Whitespace.isWhite(provider.getStringValue());
        } catch (XPathException e) {
            pendingException = e;
            return false;
        }
    }

    @Override
    public boolean standaloneSet() {
        return false;
    }


    @Override
    public String getCharacterEncodingScheme() {
        return null;
    }

    @Override
    public String getElementText() throws XMLStreamException {
        if (pendingException != null) {
            throw new XMLStreamException(pendingException);
        }
        if (getEventType() != XMLStreamConstants.START_ELEMENT) {
            throw new XMLStreamException("parser must be on START_ELEMENT to read next text", getLocation());
        }
        int eventType = next();
        StringBuilder content = new StringBuilder();
        while (eventType != XMLStreamConstants.END_ELEMENT) {
            if (eventType == XMLStreamConstants.CHARACTERS
                    || eventType == XMLStreamConstants.CDATA
                    || eventType == XMLStreamConstants.SPACE
                    || eventType == XMLStreamConstants.ENTITY_REFERENCE) {
                content.append(getText());
            } else if (eventType == XMLStreamConstants.PROCESSING_INSTRUCTION
                    || eventType == XMLStreamConstants.COMMENT) {
                // skipping
            } else if (eventType == XMLStreamConstants.END_DOCUMENT) {
                throw new XMLStreamException("unexpected end of document when reading element text content", getLocation());
            } else if (eventType == XMLStreamConstants.START_ELEMENT) {
                throw new XMLStreamException("element text content may not contain START_ELEMENT", getLocation());
            } else {
                throw new XMLStreamException("Unexpected event type " + eventType, getLocation());
            }
            eventType = next();
        }
        return content.toString();
    }

    @Override
    public String getEncoding() {
        return null;
    }

    @Override
    public String getLocalName() {
        return provider.getNodeName().getLocalPart();
    }

    @Override
    public String getNamespaceURI() {
        return provider.getNodeName().getURI();
    }

    @Override
    public String getPIData() {
        if (currentStaxEvent != XMLStreamConstants.PROCESSING_INSTRUCTION) {
            throw new IllegalStateException("Not positioned at a processing instruction");
        }
        try {
            return provider.getStringValue().toString();
        } catch (XPathException e) {
            pendingException = e;
            return "";
        }
    }

    @Override
    public String getPITarget() {
        if (currentStaxEvent != XMLStreamConstants.PROCESSING_INSTRUCTION) {
            throw new IllegalStateException("Not positioned at a processing instruction");
        }
        return provider.getNodeName().getLocalPart();
    }

    @Override
    public String getPrefix() {
        return provider.getNodeName().getPrefix();
    }


    @Override
    public String getVersion() {
        return "1.0";
    }


    @Override
    public String getNamespacePrefix(int i) {
        return provider.getNamespaceDeclarations()[i].getPrefix();
    }

    @Override
    public String getNamespaceURI(int i) {
        return provider.getNamespaceDeclarations()[i].getURI();
    }

    @Override
    public NamespaceContext getNamespaceContext() {
        return new NamespaceContextImpl(provider);
    }

    @Override
    public QName getName() {
        NodeName nn = provider.getNodeName();
        return new QName(nn.getURI(), nn.getLocalPart(), nn.getPrefix());
    }

    @Override
    public Location getLocation() {
        net.sf.saxon.s9api.Location sourceLocator = provider.getSourceLocator();
        if (sourceLocator == null) {
            sourceLocator = Loc.NONE;
        }
        return new SourceStreamLocation(sourceLocator);
    }

    @Override
    public Object getProperty(String s) throws IllegalArgumentException {
        throw new IllegalArgumentException("Unknown property " + s);
    }

    @Override
    public void require(int event, String uri, String local) throws XMLStreamException {
        if (pendingException != null) {
            throw new XMLStreamException(pendingException);
        }
        if (currentStaxEvent != event) {
            throw new XMLStreamException("Required event type is " + event + ", actual event is " + currentStaxEvent);
        }
        if (uri != null && !uri.equals(getNamespaceURI())) {
            throw new XMLStreamException("Required namespace is " + uri + ", actual is " + getNamespaceURI());
        }
        if (local != null && !local.equals(getLocalName())) {
            throw new XMLStreamException("Required local name is " + local + ", actual is " + getLocalName());
        }
    }

    /*@Nullable*/
    @Override
    public String getNamespaceURI(String s) {
        return provider.getURIForPrefix(s, true);
    }

    /**
     * Bridge a SAX SourceLocator to a javax.xml.stream.Location
     */

    public static class SourceStreamLocation implements javax.xml.stream.Location {

        private net.sf.saxon.s9api.Location locator;

        /**
         * Create a StAX SourceStreamLocation object based on a given SAX SourceLocator
         *
         * @param locator the SAX SourceLocator
         */
        public SourceStreamLocation(net.sf.saxon.s9api.Location locator) {
            this.locator = locator;
        }

        @Override
        public int getCharacterOffset() {
            return -1;
        }

        @Override
        public int getColumnNumber() {
            return locator.getColumnNumber();
        }

        @Override
        public int getLineNumber() {
            return locator.getLineNumber();
        }

        @Override
        public String getPublicId() {
            return locator.getPublicId();
        }

        @Override
        public String getSystemId() {
            return locator.getSystemId();
        }
    }

}

// Copyright (c) 2009-2020 Saxonica Limited
