////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.axiom;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.JPConverter;
import net.sf.saxon.expr.PJConverter;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExternalObjectModel;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.TreeModel;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.tree.wrapper.VirtualNode;
import net.sf.saxon.type.ItemType;
import org.apache.axiom.om.*;

import javax.xml.transform.Result;
import javax.xml.transform.Source;


/**
 * This interface must be implemented by any third-party object model that can
 * be wrapped with a wrapper that implements the Saxon Object Model (the NodeInfo interface).
 * This implementation of the interface supports wrapping of Axiom Documents.
 */

public class AxiomObjectModel extends TreeModel implements ExternalObjectModel {

    private final static AxiomObjectModel THE_INSTANCE = new AxiomObjectModel();

    public static AxiomObjectModel getInstance() {
        return THE_INSTANCE;
    }

    public AxiomObjectModel() {
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
        return "org.apache.axiom.om.OMDocument";
    }

    /**
     * Get the URI of the external object model as used in the JAXP factory interfaces for obtaining
     * an XPath implementation
     */

    @Override
    public String getIdentifyingURI() {
        return NamespaceConstant.OBJECT_MODEL_AXIOM;
    }

    @Override
    public Builder makeBuilder(PipelineConfiguration pipe) {
        return new AxiomWriter(pipe);
    }

    @Override
    public int getSymbolicValue() {
        return Builder.AXIOM_TREE;
    }

    @Override
    public String getName() {
        return "Axiom";
    }

    /**
     * Test whether this object model recognizes a given node as one of its own
     *
     * @param object the object in question
     * @return true if the object is a JDOM node
     */

    private static boolean isRecognizedNode(Object object) {
        return object instanceof OMDocument ||
                object instanceof OMElement ||
                object instanceof OMAttribute ||
                object instanceof OMText ||
                object instanceof OMComment ||
                object instanceof OMProcessingInstruction ||
                object instanceof OMNamespace;
    }

    /*@Nullable*/
    @Override
    public PJConverter getPJConverter(Class<?> targetClass) {
        if (isRecognizedNodeClass(targetClass)) {
            return new PJConverter() {
                @Override
                public Object convert(Sequence value, Class<?> targetClass, XPathContext context)  {
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
                public Sequence convert(Object object, XPathContext context) {
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
     * Test whether this object model recognizes a given class as representing a
     * node in that object model. This method will generally be called at compile time.
     *
     * @param nodeClass A class that possibly represents nodes
     * @return true if the class is used to represent nodes in this object model
     */

    private boolean isRecognizedNodeClass(Class nodeClass) {
        return OMDocument.class.isAssignableFrom(nodeClass) ||
                OMElement.class.isAssignableFrom(nodeClass) ||
                OMAttribute.class.isAssignableFrom(nodeClass) ||
                OMText.class.isAssignableFrom(nodeClass) ||
                OMComment.class.isAssignableFrom(nodeClass) ||
                OMProcessingInstruction.class.isAssignableFrom(nodeClass) ||
                OMNamespace.class.isAssignableFrom(nodeClass);
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
    public boolean sendSource(Source source, Receiver receiver)  {
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
     *
     * @param object the object to be converted . If this is not a JDOM node, the method returns null
     * @param config the Saxon Configuration
     * @return either an XPath node that wraps the supplied JDOM node, or null
     */

    /*@Nullable*/
    private Sequence convertObjectToXPathValue(Object object, Configuration config) {
        if (isRecognizedNode(object)) {
            if (object instanceof OMDocument) {
                return new AxiomDocument((OMDocument) object, "", config).getRootNode();
            } else if (object instanceof OMNode) {
                AxiomDocument docWrapper = new AxiomDocument(getOMDocument((OMNode) object), "", config);
                return wrapNode(docWrapper, object);
            } else if (object instanceof OMAttribute) {
                AxiomDocument docWrapper = new AxiomDocument(
                        getOMDocument(((OMAttribute) object).getOwner()), "", config);
                return wrapNode(docWrapper, object);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private static OMDocument getOMDocument(OMNode node) {
        OMContainer parent;
        if (node instanceof OMContainer) {
            parent = (OMContainer) node;
        } else {
            parent = node.getParent();
        }
        while (!(parent instanceof OMDocument) && parent != null) {
            parent = node.getParent();
        }
        return (OMDocument) parent;
    }

    /**
     * Convert an XPath value to an object in this object model. If the supplied value can be converted
     * to an object in this model, of the specified class, then the conversion should be done and the
     * resulting object returned. If the value cannot be converted, the method should return null. Note
     * that the supplied class might be a List, in which case the method should inspect the contents of the
     * Value to see whether they belong to this object model.
     *
     * @param value       the XPath value to be converted
     * @param target the Java class required for the result of the conversion
     * @return the object that results from conversion if conversion is possible, or null otherwise
     */


    /*@Nullable*/
    private Object convertXPathValueToObject(Sequence value, Class<?> target) {
        if (value instanceof VirtualNode) {
            Object u = ((VirtualNode) value).getRealNode();
            if (target.isAssignableFrom(u.getClass())) {
                return u;
            }
        }
        return null;
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

    public NodeInfo wrapNode(AxiomDocument document, Object node) {
        if (node instanceof OMDocument) {
            return document.getRootNode();
        } else if (node instanceof OMNode) {
            return AxiomDocument.makeWrapper((OMNode) node, document, null, -1);
        } else if (node instanceof OMAttribute) {
            AxiomElementNodeWrapper elem = (AxiomElementNodeWrapper) wrapNode(document, ((OMAttribute) node).getOwner());
            return new AxiomAttributeWrapper((OMAttribute) node, elem, -1);
        } else {
            throw new IllegalArgumentException();
        }
    }


}

