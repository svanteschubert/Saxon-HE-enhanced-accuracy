////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.Sender;
import net.sf.saxon.expr.JPConverter;
import net.sf.saxon.expr.PJConverter;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExternalObjectModel;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.SequenceExtent;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.xpath.XPathConstants;

/**
 * DOMEnvelope is an object model representation in which DOM interfaces are wrapped around
 * Saxon NodeInfo nodes: that is, it implements the DOM on top of a Saxon tree implementation
 * such as the tiny tree or linked tree.
 */

public class DOMEnvelope implements ExternalObjectModel {

    private static final DOMEnvelope THE_INSTANCE = new DOMEnvelope();

    /**
     * Get the singular instance (this class is stateless)
     *
     * @return the singular instance
     */

    public static DOMEnvelope getInstance() {
        return THE_INSTANCE;
    }

    /**
     * Public constructor. (Use the singular instance in preference to creating a new one).
     */

    public DOMEnvelope() {
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
    public PJConverter getPJConverter(Class<?> targetClass) {
        if (NodeOverNodeInfo.class.isAssignableFrom(targetClass)) {
            return new PJConverter() {
                @Override
                public Object convert(Sequence value, Class<?> targetClass, XPathContext context) throws XPathException {
                    return DOMObjectModel.convertXPathValueToObject(value, targetClass);
                }
            };
        } else if (NodeList.class.isAssignableFrom(targetClass)) {
            return new PJConverter() {
                @Override
                public Object convert(Sequence value, Class<?> targetClass, XPathContext context) throws XPathException {
                    return DOMObjectModel.convertXPathValueToObject(value, targetClass);
                }
            };
        } else {
            return null;
        }
    }

    @Override
    public JPConverter getJPConverter(Class sourceClass, Configuration config) {
        if (NodeOverNodeInfo.class.isAssignableFrom(sourceClass)) {
            return new JPConverter() {
                /*@Nullable*/
                @Override
                public Sequence convert(Object object, XPathContext context) {
                    return convertObjectToXPathValue(object);
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
        //return getPJConverter(NodeList.class);
        return null;
    }

    /**
     * Test whether this object model recognizes a given node as one of its own
     *
     * @param object the object to be tested
     * @return true if the supplied object is a node in this object model
     */

    public boolean isRecognizedNode(Object object) {
        return object instanceof NodeOverNodeInfo;
    }

    /**
     * Test whether this object model recognizes a given class as representing a
     * node in that object model. This method will generally be called at compile time.
     *
     * @param nodeClass A class that possibly represents nodes
     * @return true if the class is used to represent nodes in this object model
     */

    public boolean isRecognizedNodeClass(Class nodeClass) {
        return NodeOverNodeInfo.class.isAssignableFrom(nodeClass);
    }

    /**
     * Test whether this object model recognizes a particular kind of JAXP Result object,
     * and if it does, return a Receiver that builds an instance of this data model from
     * a sequence of events. If the Result is not recognised, return null.
     * <p>This implementation always returns null: it is not possible to construct an instance
     * of this object model implementation directly as the result of a JAXP transformation.</p>
     * @return always null in this implementation
     */

    @Override
    public Receiver getDocumentBuilder(Result result) {
        return null;
    }

    /**
     * Test whether this object model recognizes a particular kind of JAXP Source object,
     * and if it does, send the contents of the document to a supplied Receiver, and return true.
     * Otherwise, return false.
     * <p>This implementation returns true only if the source is a DOMSource whose contained node is a
     * a "NodeOverNodeInfo".</p>
     */

    @Override
    public boolean sendSource(Source source, Receiver receiver) throws XPathException {
        if (source instanceof DOMSource) {
            Node startNode = ((DOMSource) source).getNode();
            if (startNode instanceof NodeOverNodeInfo) {
                NodeInfo base = ((NodeOverNodeInfo) startNode).getUnderlyingNodeInfo();
                Sender.send(base, receiver, null);
                return true;
            }
        }
        return false;
    }

    /**
     * Wrap or unwrap a node using this object model to return the corresponding Saxon node. If the supplied
     * source does not belong to this object model, return null
     */

    @Override
    public NodeInfo unravel(Source source, Configuration config) {

        if (source instanceof DOMSource) {
            Node dsnode = ((DOMSource) source).getNode();
            if (dsnode instanceof NodeOverNodeInfo) {
                // Supplied source is a DOM Node wrapping a Saxon node: unwrap it
                return ((NodeOverNodeInfo) dsnode).getUnderlyingNodeInfo();
            }
        }
        return null;
    }

    /**
     * Convert a Java object to an XPath value. If the supplied object is recognized as a representation
     * of a value using this object model, the object model should convert the value to an XPath value
     * and return this as the result. If not, it should return null. If the object is recognized but cannot
     * be converted, an exception should be thrown
     *
     * @param object the object to be converted
     * @return the value after conversion
     */

    private Sequence convertObjectToXPathValue(Object object)  {
        if (object instanceof NodeList) {
            // NodeList needs great care, because Xerces element nodes implement the NodeList interface,
            // with the actual list being the children of the node in question. So we only recognize a
            // NodeList here if it is non-empty, and if all the nodes within it are NodeOverNodeInfo objects.
            NodeList list = (NodeList) object;
            final int len = list.getLength();
            if (len == 0) {
                return null;
            }
            NodeInfo[] nodes = new NodeInfo[len];
            for (int i = 0; i < len; i++) {
                if (list.item(i) instanceof NodeOverNodeInfo) {
                    nodes[i] = ((NodeOverNodeInfo) list.item(i)).getUnderlyingNodeInfo();
                } else {
                    return null;
                }
            }
            return new SequenceExtent(nodes);

            // Note, we accept the nodes in the order returned by the function; there
            // is no requirement that this should be document order.
        } else if (object instanceof NodeOverNodeInfo) {
            return ((NodeOverNodeInfo) object).getUnderlyingNodeInfo();
        } else {
            return null;
        }
    }

}


