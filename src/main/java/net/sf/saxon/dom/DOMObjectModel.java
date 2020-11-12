////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.*;
import net.sf.saxon.expr.JPConverter;
import net.sf.saxon.expr.PJConverter;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExternalObjectModel;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.wrapper.VirtualNode;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.SequenceExtent;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.xpath.XPathConstants;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * This interface must be implemented by any third-party object model that can
 * be wrapped with a wrapper that implements the Saxon Object Model (the NodeInfo interface).
 * This implementation of the interface supports wrapping of DOM Documents.
 */

public class DOMObjectModel extends TreeModel implements ExternalObjectModel {

    private static final DOMObjectModel THE_INSTANCE = new DOMObjectModel();
    private static DocumentBuilderFactory factory = null;

    /**
     * Get a reusable instance instance of this class.
     * <p>Note, this is not actually a singleton instance; the class also has a public constructor,
     * which is needed to support the automatic loading of object models into the Configuration.</p>
     *
     * @return the singleton instance
     */

    public static DOMObjectModel getInstance() {
        return THE_INSTANCE;
    }

    /**
     * Create an instance of the DOMObjectModel class.
     * <p>When possible, use the getInstance() method in preference, as the instance is then reusable.</p>
     */

    public DOMObjectModel() {
    }

    /**
     * Get the name of a characteristic class, which, if it can be loaded, indicates that the supporting libraries
     * for this object model implementation are available on the classpath
     *
     * @return by convention (but not necessarily) the class that implements a document node in the relevant
     * external model
     */
    @Override
    public String getDocumentClassName() {
        return "org.w3c.dom.Document";
    }

    /**
     * Get the URI of the external object model as used in the JAXP factory interfaces for obtaining
     * an XPath implementation
     */

    @Override
    public String getIdentifyingURI() {
        return XPathConstants.DOM_OBJECT_MODEL;
    }

    @Override
    public String getName() {
        return "DOM";
    }

    /**
     * Get a converter from XPath values to values in the external object model
     *
     * @param targetClass the required class of the result of the conversion. If this class represents
     *                    a node or list of nodes in the external object model, the method should return a converter that takes
     *                    a native node or sequence of nodes as input and returns a node or sequence of nodes in the
     *                    external object model representation. Otherwise, it should return null.
     * @return a converter, if the targetClass is recognized as belonging to this object model;
     *         otherwise null
     */

    @Override
    public PJConverter getPJConverter(Class<?> targetClass) {
        if (Node.class.isAssignableFrom(targetClass) && !NodeOverNodeInfo.class.isAssignableFrom(targetClass)) {
            return new PJConverter() {
                @Override
                public Object convert(Sequence value, Class<?> targetClass, XPathContext context) throws XPathException {
                    return convertXPathValueToObject(value, targetClass);
                }
            };
        } else if (NodeList.class == targetClass) {
            return new PJConverter() {
                @Override
                public Object convert(Sequence value, Class<?> targetClass, XPathContext context) throws XPathException {
                    return convertXPathValueToObject(value, targetClass);
                }
            };
        } else {
            return null;
        }
    }

    //public NodeSet ns;
    @Override
    public JPConverter getJPConverter(Class sourceClass, Configuration config) {
        if (Node.class.isAssignableFrom(sourceClass) && !NodeOverNodeInfo.class.isAssignableFrom(sourceClass)) {
            return new JPConverter() {
                @Override
                public Sequence convert(Object obj, XPathContext context) {
                    return wrapOrUnwrapNode((Node) obj, context.getConfiguration());
                }

                @Override
                public ItemType getItemType() {
                    return AnyNodeTest.getInstance();
                }
            };
        } else if (NodeList.class.isAssignableFrom(sourceClass)) {
            return new JPConverter() {
                @Override
                public Sequence convert(Object obj, XPathContext context) {
                    Configuration config = context.getConfiguration();
                    NodeList list = (NodeList) obj;
                    final int len = list.getLength();
                    NodeInfo[] nodes = new NodeInfo[len];
                    for (int i = 0; i < len; i++) {
                        nodes[i] = wrapOrUnwrapNode(list.item(i), config);
                    }
                    return new SequenceExtent(nodes);
                }

                @Override
                public ItemType getItemType() {
                    return AnyNodeTest.getInstance();
                }

                @Override
                public int getCardinality() {
                    return StaticProperty.ALLOWS_ZERO_OR_MORE;
                }
            };
        } else if (DOMSource.class.isAssignableFrom(sourceClass)) {
            return new JPConverter() {
                @Override
                public Sequence convert(Object obj, XPathContext context) {
                    return unravel((DOMSource) obj, context.getConfiguration());
                }

                @Override
                public ItemType getItemType() {
                    return AnyNodeTest.getInstance();
                }
            };
        } else if (DocumentWrapper.class == sourceClass) {
            return new JPConverter() {
                @Override
                public Sequence convert(Object obj, XPathContext context) {
                    return ((DocumentWrapper) obj).getRootNode();
                }

                @Override
                public ItemType getItemType() {
                    return AnyNodeTest.getInstance();
                }
            };
        } else {
            return null;
        }
    }

    /**
     * Get a converter that converts a sequence of XPath nodes to this model's representation
     * of a node list.
     *
     * @param node an example of the kind of node used in this model
     * @return if the model does not recognize this node as one of its own, return null. Otherwise
     *         return a PJConverter that takes a list of XPath nodes (represented as NodeInfo objects) and
     *         returns a collection of nodes in this object model
     */

    @Override
    public PJConverter getNodeListCreator(Object node) {
        if (node == null ||
                node instanceof Node ||
                node instanceof DOMSource ||
                node instanceof DocumentWrapper ||
                (node instanceof VirtualNode && ((VirtualNode) node).getRealNode() instanceof Node)) {
            return new PJConverter() {
                /*@Nullable*/
                @Override
                public Object convert(Sequence value, Class<?> targetClass, XPathContext context) throws XPathException {
                    return convertXPathValueToObject(value, NodeList.class);
                }
            };
        } else {
            return null;
        }
    }

    /**
     * Test whether this object model recognizes a particular kind of JAXP Result object,
     * and if it does, return a Receiver that builds an instance of this data model from
     * a sequence of events. If the Result is not recognised, or is not a {@code DOMResult}, return null.
     * @return either a Receiver that can be used to build a DOM instance, or null
     */

    @Override
    public Receiver getDocumentBuilder(Result result) throws XPathException {
        if (result instanceof DOMResult) {
            DOMWriter emitter = new DOMWriter();
            Node root = ((DOMResult) result).getNode();
            if (root instanceof NodeOverNodeInfo && !(((NodeOverNodeInfo) root).getUnderlyingNodeInfo() instanceof MutableNodeInfo)) {
                throw new XPathException("Supplied DOMResult is a non-mutable Saxon implementation");
            }
            // JDK 1.5 adds a nextSibling() property to identify the insertion point among the siblings
            Node nextSibling = ((DOMResult) result).getNextSibling();
            if (root == null) {
                try {
                    if (factory == null) {
                        factory = DocumentBuilderFactory.newInstance();
                    }
                    DocumentBuilder docBuilder = factory.newDocumentBuilder();
                    Document out = docBuilder.newDocument();
                    ((DOMResult) result).setNode(out);
                    emitter.setNode(out);
                } catch (ParserConfigurationException e) {
                    throw new XPathException(e);
                }
            } else {
                emitter.setNode(root);
                emitter.setNextSibling(nextSibling);
            }
            return emitter;
        }
        return null;
    }

    @Override
    public Builder makeBuilder(PipelineConfiguration pipe) {
        DOMWriter dw = new DOMWriter();
        dw.setPipelineConfiguration(pipe);
        return dw;
    }

    /**
     * Test whether this object model recognizes a particular kind of JAXP Source object,
     * and if it does, send the contents of the document to a supplied Receiver, and return true.
     * Otherwise, return false
     */

    @Override
    public boolean sendSource(Source source, Receiver receiver) throws XPathException {
        if (source instanceof DOMSource) {
            sendDOMSource((DOMSource)source, receiver);
            return true;
        }
        return false;
    }

    public static void sendDOMSource(DOMSource source, Receiver receiver) throws XPathException {
        Node startNode = source.getNode();
        if (startNode == null) {
            // Send an empty document
            receiver.open();
            receiver.startDocument(ReceiverOption.NONE);
            receiver.endDocument();
            receiver.close();
        } else {
            DOMSender driver = new DOMSender(startNode, receiver);
            driver.setSystemId(source.getSystemId());
            receiver.open();
            driver.send();
            receiver.close();
        }
    }

    /**
     * Wrap a DOM node using this object model to return the corresponding Saxon node.
     *
     * @param node   the DOM node to be wrapped
     * @param config the Saxon Configuration
     * @return the wrapped DOM node
     * @since 9.2
     */

    public NodeInfo wrap(Node node, Configuration config) {
        // Supplied source is an ordinary DOM Node: wrap it
        Document dom;
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            dom = (Document) node;
        } else {
            dom = node.getOwnerDocument();
        }
        DocumentWrapper docWrapper = new DocumentWrapper(dom, node.getBaseURI(), config);
        return docWrapper.wrap(node);
    }

    /**
     * Copy a DOM node to create a node in a different tree model
     *
     * @param node   the DOM node to be copied
     * @param model  the target tree model
     * @param config the Saxon Configuration
     * @return the copied node
     * @throws net.sf.saxon.trans.XPathException
     *          if the operation fails
     * @since 9.2
     */

    public NodeInfo copy(Node node, TreeModel model, Configuration config) throws XPathException {
        PipelineConfiguration pipe = config.makePipelineConfiguration();
        Builder builder = model.makeBuilder(pipe);
        builder.open();
        Sender.send(new DOMSource(node), builder, null);
        NodeInfo result = builder.getCurrentRoot();
        builder.close();
        return result;
    }

    /**
     * Wrap or unwrap a node using this object model to return the corresponding Saxon node. If the supplied
     * source does not belong to this object model, return null
     */

    @Override
    public NodeInfo unravel(Source source, Configuration config) {

        if (source instanceof DOMSource) {
            Node dsnode = ((DOMSource) source).getNode();
            if (!(dsnode instanceof NodeOverNodeInfo)) {
                // Supplied source is an ordinary DOM Node: wrap it
                Document dom;
                if (dsnode.getNodeType() == Node.DOCUMENT_NODE) {
                    dom = (Document) dsnode;
                } else {
                    dom = dsnode.getOwnerDocument();
                }
                DocumentWrapper docWrapper = new DocumentWrapper(dom, source.getSystemId(), config);
                return docWrapper.wrap(dsnode);
            }
        }
        return null;
    }

    /**
     * Wrap a DOM Node as a NodeInfo, unless it already wraps a NodeInfo, inwhich case unwrap it
     *
     * @param node   the node to be wrapped
     * @param config the Saxon Configuration. This must be the same Configuration, or one that is compatible with,
     *               the Configuration used to compile and execute the query or stylesheet.
     * @return the new wrapper node
     */

    private NodeInfo wrapOrUnwrapNode(Node node, Configuration config) {
        if (node instanceof NodeOverNodeInfo) {
            return ((NodeOverNodeInfo) node).getUnderlyingNodeInfo();
        } else {
            TreeInfo doc = wrapDocument(node, "", config);
            return wrapNode(doc, node);
        }
    }

    /**
     * Convert an XPath value to an object in this object model. If the supplied value can be converted
     * to an object in this model, of the specified class, then the conversion should be done and the
     * resulting object returned. If the value cannot be converted, the method should return null. Note
     * that the supplied class might be a List, in which case the method should inspect the contents of the
     * Value to see whether they belong to this object model.
     *
     * @param value  the XPath value to be converted
     * @param target the class of object required
     * @return the result of the conversion
     * @throws XPathException if the target class is explicitly associated with this object model, but the
     *                        supplied value cannot be converted to the appropriate class
     */

    public static Object convertXPathValueToObject(Sequence value, Class<?> target) throws XPathException {
        // We accept the object if (a) the target class is Node, Node[], or NodeList,
        // or (b) the supplied object is a node, or sequence of nodes, that wrap DOM nodes,
        // provided that the target class is Object or a collection class
        boolean requireDOM =
                Node.class.isAssignableFrom(target) || (target == NodeList.class) ||
                        (target.isArray() && Node.class.isAssignableFrom(target.getComponentType()));

        // Note: we allow the declared type of the method argument to be a subclass of Node. If the actual
        // node supplied is the wrong kind of node, this will result in a Java exception.

        boolean allowDOM =
                target == Object.class || target.isAssignableFrom(ArrayList.class) ||
                        target.isAssignableFrom(HashSet.class) ||
                        (target.isArray() && target.getComponentType() == Object.class);
        if (!(requireDOM || allowDOM)) {
            return null;
        }
        List<Node> nodes = new ArrayList<>(20);

        SequenceIterator iter = value.iterate();
        Item item;
        while ((item = iter.next()) != null) {
            if (item instanceof VirtualNode) {
                Object o = ((VirtualNode) item).getRealNode();
                if (o instanceof Node) {
                    nodes.add((Node) o);
                    continue;
                }
            }
            if (requireDOM) {
                if (item instanceof NodeInfo) {
                    nodes.add(NodeOverNodeInfo.wrap((NodeInfo) item));
                } else {
                    throw new XPathException(
                            "Cannot convert XPath value to Java object: required class is " + target.getName() +
                                    "; supplied value has type " + Type.displayTypeName(item));
                }
            } else {
                return null;    // DOM Nodes are not actually required; let someone else try the conversion
            }
        }

        if (nodes.isEmpty() && !requireDOM) {
            return null;  // empty sequence supplied - try a different mapping
        }
        if (Node.class.isAssignableFrom(target)) {
            if (nodes.size() != 1) {
                throw new XPathException("Cannot convert XPath value to Java object: requires a single DOM Node" +
                        "but supplied value contains " + nodes.size() + " nodes");
            }
            return nodes.get(0);
            // could fail if the node is of the wrong kind
        } else if (target == NodeList.class) {
            return new DOMNodeList(nodes);
        } else if (target.isArray() && target.getComponentType() == Node.class) {
            return nodes.toArray(new Node[0]);
        } else if (target.isAssignableFrom(ArrayList.class)) {
            return nodes;
        } else if (target.isAssignableFrom(HashSet.class)) {
            return new HashSet<>(nodes);
        } else {
            // after all this work, give up
            return null;
        }
    }

    /**
     * Wrap a document node in the external object model in a document wrapper that implements
     * the Saxon DocumentInfo interface. (However, if the supplied object is a wrapper for a Saxon
     * NodeInfo object, then we <i>unwrap</i> it.
     *
     * @param node    a node (any node) in the third party document
     * @param baseURI the base URI of the node (supply "" if unknown)
     * @param config  the Saxon configuration (which among other things provides access to the NamePool)
     * @return the wrapper, which must implement DocumentInfo
     */

    private TreeInfo wrapDocument(Object node, String baseURI, Configuration config) {
        if (node instanceof DocumentOverNodeInfo) {
            return (TreeInfo) ((DocumentOverNodeInfo) node).getUnderlyingNodeInfo();
        }
        if (node instanceof NodeOverNodeInfo) {
            return ((NodeOverNodeInfo) node).getUnderlyingNodeInfo().getTreeInfo();
        }
        if (node instanceof org.w3c.dom.Node) {
            if (((Node) node).getNodeType() == Node.DOCUMENT_NODE) {
                Document doc = (org.w3c.dom.Document) node;
                return new DocumentWrapper(doc, baseURI, config);
            } else if (((Node) node).getNodeType() == Node.DOCUMENT_FRAGMENT_NODE) {
                DocumentFragment doc = (org.w3c.dom.DocumentFragment) node;
                return new DocumentWrapper(doc, baseURI, config);
            } else {
                Document doc = ((org.w3c.dom.Node) node).getOwnerDocument();
                return new DocumentWrapper(doc, baseURI, config);
            }
        }
        throw new IllegalArgumentException("Unknown node class " + node.getClass());
    }

    /**
     * Wrap a node within the external object model in a node wrapper that implements the Saxon
     * VirtualNode interface (which is an extension of NodeInfo)
     *
     * @param document the document wrapper, as a DocumentInfo object
     * @param node     the node to be wrapped. This must be a node within the document wrapped by the
     *                 DocumentInfo provided in the first argument
     * @return the wrapper for the node, as an instance of VirtualNode
     */

    private NodeInfo wrapNode(TreeInfo document, Object node) {
        return ((DocumentWrapper) document).wrap((Node) node);
    }


}

