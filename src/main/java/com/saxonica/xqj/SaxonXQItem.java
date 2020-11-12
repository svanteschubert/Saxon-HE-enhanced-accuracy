////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package com.saxonica.xqj;

import com.saxonica.xqj.pull.DocumentWrappingPullProvider;
import com.saxonica.xqj.pull.PullToStax;
import com.saxonica.xqj.pull.TreeWalker;
import net.sf.saxon.Configuration;
import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.instruct.ResultDocument;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.lib.SerializerFactory;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pull.PullProvider;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.tree.wrapper.VirtualNode;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.*;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;

import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Result;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xquery.XQConnection;
import javax.xml.xquery.XQException;
import javax.xml.xquery.XQItemType;
import javax.xml.xquery.XQResultItem;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.Properties;

/**
 * This Saxon class is used to implement both the XQItem and XQResultItem interfaces in XQJ.
 * Where the item is not a real XQResultItem, getConnection() will return null.
 */
public class SaxonXQItem extends Closable implements XQResultItem, SaxonXQItemAccessor {

    /*@Nullable*/ private Item item;
    private Configuration config;
    SaxonXQDataFactory dataFactory;

    public SaxonXQItem(/*@Nullable*/ Item item, /*@NotNull*/ SaxonXQDataFactory factory) {
        if (item == null) {
            throw new NullPointerException("item");
        }
        this.item = item;
        dataFactory = factory;
        config = factory.getConfiguration();
        setClosableContainer(factory);
    }

    Configuration getConfiguration() {
        return config;
    }

    /*@Nullable*/
    @Override
    public Item getSaxonItem() {
        return item;
    }

    /*@Nullable*/
    @Override
    public XQConnection getConnection() throws XQException {
        checkNotClosed();
        if (dataFactory instanceof XQConnection) {
            return (XQConnection) dataFactory;
        } else {
            return null;
        }
    }

    @Override
    public String getAtomicValue() throws XQException {
        checkNotClosed();
        if (item instanceof AtomicValue) {
            return item.getStringValue();
        }
        throw new XQException("Failed to getAtomicValue: item is a node, or is closed");
    }

    @Override
    public boolean getBoolean() throws XQException {
        checkNotClosed();
        if (item instanceof BooleanValue) {
            return ((BooleanValue) item).getBooleanValue();
        }
        throw new XQException("Failed in getBoolean: item is not a boolean, or is closed");
    }

    @Override
    public byte getByte() throws XQException {
        checkNotClosed();
        if (item instanceof AtomicValue) {
            AtomicValue prim = (AtomicValue) item;
            return (byte) longValue(prim, -128, 127);
        }
        throw new XQException("Failed in getByte: item is not an atomic value, or is closed");
    }

    private static long longValue(/*@NotNull*/ AtomicValue value, long min, long max) throws XQException {
        if (value instanceof NumericValue) {
            if (value instanceof DoubleValue || value instanceof FloatValue) {
                throw new XQException("Value is a double or float");
            }
            if (!((NumericValue) value).isWholeNumber()) {
                throw new XQException("Value is not a whole number");
            }
            try {
                long val = ((NumericValue) value).longValue();
                if (val >= min && val <= max) {
                    return val;
                } else {
                    throw new XQException("Value is out of range for requested type");
                }
            } catch (XPathException err) {
                XQException xqe = new XQException(err.getMessage());
                xqe.initCause(err);
                throw xqe;
            }
        }
        throw new XQException("Value is not numeric");
    }

    @Override
    public double getDouble() throws XQException {
        checkNotClosed();
        if (item instanceof DoubleValue) {
            return ((DoubleValue) item).getDoubleValue();
        }
        throw new XQException("Failed in getDouble: item is not a double, or is closed");
    }

    @Override
    public float getFloat() throws XQException {
        checkNotClosed();
        if (item instanceof FloatValue) {
            return ((FloatValue) item).getFloatValue();
        }
        throw new XQException("Failed in getFloat: item is not a float, or is closed");
    }

    @Override
    public int getInt() throws XQException {
        checkNotClosed();
        if (item instanceof AtomicValue) {
            AtomicValue prim = (AtomicValue) item;
            return (int) longValue(prim, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        throw new XQException("Failed in getInt: item is not an atomic value, or is closed");
    }

    /*@NotNull*/
    @Override
    public XMLStreamReader getItemAsStream() throws XQException {
        // The spec (section 12.1) requires that the item be converted into a document, and we
        // then read events corresponding to this document
        checkNotClosed();
        PipelineConfiguration pipe = config.makePipelineConfiguration();
        pipe.setHostLanguage(HostLanguage.XQUERY);
        if (item instanceof NodeInfo) {
            PullProvider provider;
            if (((NodeInfo) item).getNodeKind() == Type.DOCUMENT) {
                provider = TreeWalker.makeTreeWalker((NodeInfo) item);
            } else {
                provider = nodeAsDocument((NodeInfo) item);
            }
            provider.setPipelineConfiguration(pipe);
            return new PullToStax(provider);
        } else if (item instanceof AtomicValue) {
            Orphan orphan = new Orphan(config);
            orphan.setNodeKind(Type.TEXT);
            orphan.setStringValue(item.getStringValue());
            return new PullToStax(nodeAsDocument(orphan));
//            EventIterator contentIterator = new SingletonEventIterator(item);
//            EventIterator eventIterator = new BracketedDocumentIterator(contentIterator);
//            eventIterator = new Decomposer(eventIterator, pipe);
//            return new EventToStaxBridge(eventIterator, pipe);
        } else {
            throw new XQException("Cannot deliver functions, maps, or arrays in the form of an XMLStreamReader");
        }
    }

    private PullProvider nodeAsDocument(NodeInfo node) throws XQException {
        switch (node.getNodeKind()) {
            case Type.DOCUMENT:
                return TreeWalker.makeTreeWalker(node);
            case Type.ELEMENT:
            case Type.COMMENT:
            case Type.PROCESSING_INSTRUCTION:
            case Type.TEXT:
                return new DocumentWrappingPullProvider(TreeWalker.makeTreeWalker(node));
            case Type.ATTRIBUTE:
                throw new XQException("Cannot serialize a standalone attribute node");
            case Type.NAMESPACE:
            default:
                throw new XQException("Cannot serialize a standalone namespace node");
        }
    }

    @Override
    public String getItemAsString(/*@Nullable*/ Properties props) throws XQException {
        checkNotClosed();
        if (props == null) {
            props = new Properties();
        } else {
            validateSerializationProperties(props, config);
        }
        props = SaxonXQSequence.setDefaultProperties(props);
        StringWriter writer = new StringWriter();
        writeItem(writer, props);
        return writer.toString();
    }

    protected static void validateSerializationProperties(/*@NotNull*/ Properties props, Configuration config) throws XQException {
        Properties validatedProps = new Properties();
        for (Enumeration e = props.propertyNames(); e.hasMoreElements(); ) {
            String name = (String) e.nextElement();
            String value = props.getProperty(name);
            String localName = name;
            String uri = "";
            if (name.startsWith("{")) {
                StructuredQName qName = StructuredQName.fromClarkName(name);
                localName = qName.getLocalPart();
                uri = qName.getURI();
            }
            try {
                ResultDocument.setSerializationProperty(validatedProps, uri, localName, value, null, false, config);
            } catch (XPathException ex) {
                throw new XQException("Invalid serialization property: " + ex.getMessage());
            }
        }
    }

    /*@NotNull*/
    @Override
    public XQItemType getItemType() throws XQException {
        checkNotClosed();
        if (item instanceof AtomicValue) {
            return new SaxonXQItemType(
                    ((AtomicValue) item).getItemType(),
                    getConfiguration());
        } else if (item instanceof NodeInfo) {
            return new SaxonXQItemType((NodeInfo) item);
        } else {
            throw new XQException("XQJ can only handle nodes and atomic values");
        }
    }

    @Override
    public long getLong() throws XQException {
        checkNotClosed();
        if (item instanceof AtomicValue) {
            AtomicValue prim = (AtomicValue) item;
            return (byte) longValue(prim, Long.MIN_VALUE, Long.MAX_VALUE);
        }
        throw new XQException("Failed in getLong: item is not an atomic value, or is closed");
    }

    @Override
    public Node getNode() throws XQException {
        checkNotClosed();
        if (!(item instanceof NodeInfo)) {
            throw new XQException("Failed in getNode: item is an atomic value, or is closed");
        }
        if (item instanceof VirtualNode) {
            Object n = ((VirtualNode) item).getRealNode();
            if (n instanceof Node) {
                return (Node) n;
            }
        }
        return NodeOverNodeInfo.wrap((NodeInfo) item);
    }

    /*@NotNull*/
    @Override
    public URI getNodeUri() throws XQException {
        checkNotClosed();
        if (item instanceof NodeInfo) {
            try {
                String systemId = ((NodeInfo) item).getSystemId();
                if (systemId == null) {
                    return new URI("");
                }
                return new URI(systemId);
            } catch (URISyntaxException e) {
                throw new XQException("System ID of node is not a valid URI");
            }
        }
        throw new XQException("Item is not a node");
    }

    @Override
    public Object getObject() throws XQException {
        checkNotClosed();
        return dataFactory.getObjectConverter().toObject(this);
    }

    @Override
    public short getShort() throws XQException {
        checkNotClosed();
        if (item instanceof AtomicValue) {
            AtomicValue prim = (AtomicValue) item;
            return (short) longValue(prim, Short.MIN_VALUE, Short.MAX_VALUE);
        }
        throw new XQException("Failed in getShort: item is not an atomic value, or is closed");
    }

    @Override
    public boolean instanceOf(/*@NotNull*/ XQItemType type) throws XQException {
        try {
            checkNotClosed();
            checkNotNull(type);
            return ((SaxonXQItemType) type).getSaxonItemType().matches(item, config.getTypeHierarchy());
        } catch (XPathException e) {
            XQException xqe = new XQException(e.getMessage());
            xqe.initCause(e);
            throw xqe;
        }
    }

    @Override
    public void writeItem(OutputStream os, Properties props) throws XQException {
        checkNotClosed();
        checkNotNull(os);
        writeItemToResult(new StreamResult(os), props);
    }

    @Override
    public void writeItem(Writer ow, Properties props) throws XQException {
        checkNotClosed();
        checkNotNull(ow);
        writeItemToResult(new StreamResult(ow), props);
    }

    @Override
    public void writeItemToResult(Result result) throws XQException {
        checkNotClosed();
        checkNotNull(result);
        writeItemToResult(result, new Properties());
    }

    private void writeItemToResult(Result result, /*@Nullable*/ Properties props) throws XQException {
        checkNotClosed();
        checkNotNull(result);
        if (props == null) {
            props = new Properties();
        }
        props = SaxonXQSequence.setDefaultProperties(props);
        try {
            SerializerFactory sf = config.getSerializerFactory();
            Receiver tr = sf.getReceiver(result, new SerializationProperties(props));
            tr.open();
            tr.append(item, Loc.NONE, ReceiverOption.ALL_NAMESPACES);
            tr.close();
        } catch (XPathException e) {
            XQException xqe = new XQException(e.getMessage());
            xqe.initCause(e);
            throw xqe;
        }
    }

    @Override
    public void writeItemToSAX(ContentHandler saxHandler) throws XQException {
        checkNotClosed();
        checkNotNull(saxHandler);
        writeItemToResult(new SAXResult(saxHandler));
    }

    private void checkNotNull(/*@Nullable*/ Object arg) throws XQException {
        if (arg == null) {
            throw new XQException("Argument is null");
        }
    }

}
