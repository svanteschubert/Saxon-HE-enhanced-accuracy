////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.xom;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.JPConverter;
import net.sf.saxon.expr.PJConverter;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExternalObjectModel;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.wrapper.VirtualNode;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.SequenceExtent;
import nu.xom.Document;
import nu.xom.Node;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


/**
 * This interface must be implemented by any third-party object model that can
 * be wrapped with a wrapper that implements the Saxon Object Model (the NodeInfo interface).
 * This implementation of the interface supports wrapping of JDOM Documents.
 * <p>This is a singleton class whose instance can be obtained using the {@link #getInstance}
 * method. However, the constructor is public for backwards compatibility.</p>
 * <p>The class extends {@link TreeModel} so that any interface expected a TreeModel, for example
 * {@link net.sf.saxon.s9api.XdmDestination#setTreeModel}, can take <code>XOMObjectModel.getInstance()</code>
 * as an argument.</p>
 */

public class XOMObjectModel extends TreeModel implements ExternalObjectModel {

    private final static XOMObjectModel THE_INSTANCE = new XOMObjectModel();

    public static XOMObjectModel getInstance() {
        return THE_INSTANCE;
    }

    public XOMObjectModel() {
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
        return "nu.xom.Document";
    }

    /**
     * Get the URI of the external object model as used in the JAXP factory interfaces for obtaining
     * an XPath implementation
     */

    @Override
    public String getIdentifyingURI() {
        return NamespaceConstant.OBJECT_MODEL_XOM;
    }

    @Override
    public String getName() {
        return "XOM";
    }

    @Override
    public Builder makeBuilder(PipelineConfiguration pipe) {
        return new XOMWriter(pipe);
    }

    /*@Nullable*/
    @Override
    public PJConverter getPJConverter(Class<?> targetClass) {
        if (isRecognizedNodeClass(targetClass)) {
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

    @Override
    public JPConverter getJPConverter(Class sourceClass, Configuration config) {
        if (isRecognizedNodeClass(sourceClass)) {
            return new JPConverter() {
                @Override
                public Sequence convert(Object object, XPathContext context)  {
                    return convertObjectToXPathValue(object, context.getConfiguration());
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
        return null;
    }

    /**
     * Test whether this object model recognizes a given node as one of its own
     */

    public boolean isRecognizedNode(Object object) {
        return object instanceof Node;
    }

    /**
     * Test whether this object model recognizes a given class as representing a
     * node in that object model. This method will generally be called at compile time.
     *
     * @param nodeClass A class that possibly represents nodes
     * @return true if the class is used to represent nodes in this object model
     */

    public boolean isRecognizedNodeClass(Class nodeClass) {
        return nu.xom.Node.class.isAssignableFrom(nodeClass);
    }

    /**
     * Test whether this object model recognizes a particular kind of JAXP Result object,
     * and if it does, return a Receiver that builds an instance of this data model from
     * a sequence of events. If the Result is not recognised, return null.
     * @return always null
     */

    @Override
    public Receiver getDocumentBuilder(Result result) {
        return null;
    }

    /**
     * Test whether this object model recognizes a particular kind of JAXP Source object,
     * and if it does, send the contents of the document to a supplied Receiver, and return true.
     * Otherwise, return false.
     */

    @Override
    public boolean sendSource(Source source, Receiver receiver) {
        return false;
    }

    /**
     * Wrap or unwrap a node using this object model to return the corresponding Saxon node. If the supplied
     * source does not belong to this object model, return null
     */

    @Override
    public NodeInfo unravel(Source source, Configuration config) {
        return null;
    }

    /**
     * Convert a Java object to an XPath value. If the supplied object is recognized as a representation
     * of a value using this object model, the object model should convert the value to an XPath value
     * and return this as the result. If not, it should return null. If the object is recognized but cannot
     * be converted, an exception should be thrown
     */

    private Sequence convertObjectToXPathValue(Object object, Configuration config)  {
        if (object instanceof Node) {
            return wrapNode((Node) object, config);
        } else if (object instanceof Node[]) {
            NodeInfo[] nodes = new NodeInfo[((Node[]) object).length];
            for (int i = 0; i < nodes.length; i++) {
                nodes[i] = wrapNode(((Node[]) object)[i], config);
            }
            return new SequenceExtent(nodes);
        } else {
            return null;
        }
    }

    private synchronized NodeInfo wrapNode(Node node, Configuration config) {
        return new XOMDocumentWrapper(node.getDocument(), config).wrap(node);
    }

    /**
     * Convert an XPath value to an object in this object model. If the supplied value can be converted
     * to an object in this model, of the specified class, then the conversion should be done and the
     * resulting object returned. If the value cannot be converted, the method should return null. Note
     * that the supplied class might be a List, in which case the method should inspect the contents of the
     * Value to see whether they belong to this object model.
     */

    public Object convertXPathValueToObject(Sequence value, Class<?> targetClass)
            throws XPathException {
        // We accept the object if (a) the target class is Node or Node[],
        // or (b) the supplied object is a node, or sequence of nodes, that wrap XOM nodes,
        // provided that the target class is Object or a collection class
        boolean requireXOM =
                Node.class.isAssignableFrom(targetClass) ||
                        (targetClass.isArray() && Node.class.isAssignableFrom(targetClass.getComponentType()));

        // Note: we allow the declared type of the method argument to be a subclass of Node. If the actual
        // node supplied is the wrong kind of node, this will result in a Java exception.

        boolean allowXOM =
                targetClass == Object.class || targetClass.isAssignableFrom(ArrayList.class) ||
                        targetClass.isAssignableFrom(HashSet.class) ||
                        (targetClass.isArray() && targetClass.getComponentType() == Object.class);
        if (!(requireXOM || allowXOM)) {
            return null;
        }
        List<Node> nodes = new ArrayList<>(20);

        SequenceIterator iter = value.iterate();
        while (true) {
            Item item = iter.next();
            if (item == null) {
                break;
            }
            if (item instanceof VirtualNode) {
                Object o = ((VirtualNode) item).getRealNode();
                if (o instanceof Node) {
                    nodes.add((Node)o);
                } else {
                    if (requireXOM) {
                        throw new XPathException("Extension function required class " + targetClass.getName() +
                                "; supplied value of class " + item.getClass().getName() +
                                " could not be converted");
                    }
                }
            } else if (requireXOM) {
                throw new XPathException("Extension function required class " + targetClass.getName() +
                        "; supplied value of class " + item.getClass().getName() +
                        " could not be converted");
            } else {
                return null;
            }
        }

        if (nodes.isEmpty() && !requireXOM) {
            return null;  // empty sequence supplied - try a different mapping
        }
        if (Node.class.isAssignableFrom(targetClass)) {
            if (nodes.size() != 1) {
                throw new XPathException("Extension function requires a single XOM Node" +
                        "; supplied value contains " + nodes.size() + " nodes");
            }
            return nodes.get(0);
        } else if (targetClass.isArray() && Node.class.isAssignableFrom(targetClass.getComponentType())) {
            Node[] array = (Node[]) Array.newInstance(targetClass.getComponentType(), nodes.size());
            nodes.toArray(array);
            return array;
        } else if (targetClass.isAssignableFrom(ArrayList.class)) {
            return nodes;
        } else if (targetClass.isAssignableFrom(HashSet.class)) {
            return new HashSet<>(nodes);
        } else {
            // after all this work, give up
            return null;
        }
    }

    /**
     * Wrap a document node in the external object model in a document wrapper that implements
     * the Saxon DocumentInfo interface
     *
     * @param node    a node (any node) in the third party document
     * @param config  the Saxon configuration (which among other things provides access to the NamePool)
     * @return the wrapper, which must implement DocumentInfo
     */

    public TreeInfo wrapDocument(Object node, Configuration config) {
        Document documentNode = ((Node) node).getDocument();
        return new XOMDocumentWrapper(documentNode, config);
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

    public NodeInfo wrapNode(TreeInfo document, Object node) {
        if (!(node instanceof Node)) {
            throw new IllegalArgumentException("Object to be wrapped is not a XOM Node: " + node.getClass());
        }
        return ((XOMDocumentWrapper) document).wrap((Node) node);
    }

}


