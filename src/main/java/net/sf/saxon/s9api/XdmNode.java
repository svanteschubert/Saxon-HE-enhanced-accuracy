////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.query.QueryResult;
import net.sf.saxon.s9api.streams.Steps;
import net.sf.saxon.s9api.streams.XdmStream;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.wrapper.VirtualNode;
import net.sf.saxon.type.Type;

import javax.xml.transform.Source;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * This class represents a node in the XDM data model. A Node is an {@link XdmItem}, and is therefore an
 * {@link XdmValue} in its own right, and may also participate as one item within a sequence value.
 * <p>An XdmNode is implemented as a wrapper around an object of type {@link net.sf.saxon.om.NodeInfo}.
 * Because this is a key interface within Saxon, it is exposed via this API.</p>
 * <p>The XdmNode interface exposes basic properties of the node, such as its name, its string value, and
 * its typed value. Navigation to other nodes is supported through a single method, {@link #axisIterator},
 * which allows other nodes to be retrieved by following any of the XPath axes.</p>
 * <p>Note that node identity cannot be inferred from object identity. The same node may be represented
 * by different <code>XdmNode</code> instances at different times, or even at the same time. The equals()
 * method on this class can be used to test for node identity.</p>
 * @see net.sf.saxon.sapling.SaplingDocument#toXdmNode(Processor)
 * @see net.sf.saxon.sapling.SaplingElement#toXdmNode(Processor)
 *
 * @since 9.0
 */
public class XdmNode extends XdmItem {

    private XdmNode() {}

    /**
     * Construct an XdmNode as a wrapper around an existing NodeInfo object
     *
     * @param node the NodeInfo object to be wrapped. This can be retrieved using the
     *             {@link #getUnderlyingNode} method.
     * @since 9.2 (previously a protected constructor)
     */

    public XdmNode(NodeInfo node) {
        setValue(node);
    }

    /**
     * Get the kind of node.
     *
     * @return the kind of node, for example {@link XdmNodeKind#ELEMENT} or {@link XdmNodeKind#ATTRIBUTE}
     */

    public XdmNodeKind getNodeKind() {
        switch (getUnderlyingNode().getNodeKind()) {
            case Type.DOCUMENT:
                return XdmNodeKind.DOCUMENT;
            case Type.ELEMENT:
                return XdmNodeKind.ELEMENT;
            case Type.ATTRIBUTE:
                return XdmNodeKind.ATTRIBUTE;
            case Type.TEXT:
                return XdmNodeKind.TEXT;
            case Type.COMMENT:
                return XdmNodeKind.COMMENT;
            case Type.PROCESSING_INSTRUCTION:
                return XdmNodeKind.PROCESSING_INSTRUCTION;
            case Type.NAMESPACE:
                return XdmNodeKind.NAMESPACE;
            default:
                throw new IllegalStateException("nodeKind");
        }
    }

    /**
     * Get a {@link Processor} suitable for use with this {@link XdmNode}.
     * <p>In most cases this will be the original {@link Processor} object used to create
     * the {@link DocumentBuilder} that built the document that contains this node. If
     * that {@code Processor} is not available, it will be a compatible {@code Processor},
     * one that shares the same underlying {@link Configuration}, and hence is initialized
     * with the same configuration settings, schema components, license features, and so on.</p>
     * <p><i>Note: the only case where the original {@code Processor} is not available is when
     * the same {@code Configuration} is used with multiple APIs, for example mixing s9api
     * and JAXP or XQJ in the same application.</i></p>
     *
     * @return a Processor suitable for performing further operations on this node, for example
     * for creating a {@link Serializer} or an {@link XPathCompiler}.
     * @since 9.9
     */

    public Processor getProcessor() {
        Configuration config = getUnderlyingNode().getConfiguration();
        Object originator = config.getProcessor();
        if (originator instanceof Processor) {
            return (Processor)originator;
        } else {
            return new Processor(config);
        }
    }

    /**
     * Get the underlying implementation object representing the value. This method allows
     * access to lower-level Saxon functionality, including classes and methods that offer
     * no guarantee of stability across releases.
     *
     * @return the underlying implementation object representing the value
     * @since 9.8 (previously inherited from XdmValue which returns a Sequence)
     */
    @Override
    public NodeInfo getUnderlyingValue() {
        return (NodeInfo) super.getUnderlyingValue();
    }

    /**
     * Get the name of the node, as a QName
     *
     * @return the name of the node. In the case of unnamed nodes (for example, text and comment nodes)
     *         return null.
     */

    /*@Nullable*/
    public QName getNodeName() {
        NodeInfo n = getUnderlyingNode();
        switch (n.getNodeKind()) {
            case Type.DOCUMENT:
            case Type.TEXT:
            case Type.COMMENT:
                return null;
            case Type.PROCESSING_INSTRUCTION:
            case Type.NAMESPACE:
                if (n.getLocalPart().isEmpty()) {
                    return null;
                } else {
                    return new QName(new StructuredQName("", "", n.getLocalPart()));
                }
            case Type.ELEMENT:
            case Type.ATTRIBUTE:
                return new QName(n.getPrefix(), n.getURI(), n.getLocalPart());
            default:
                return null;
        }
    }

    /**
     * Get the typed value of this node, as defined in XDM
     *
     * @return the typed value. If the typed value is a single atomic value, this will be returned
     * as an instance of {@link XdmAtomicValue}
     * @throws SaxonApiException if an error occurs obtaining the typed value, for example because
     *                           the node is an element with element-only content
     */

    public XdmValue getTypedValue() throws SaxonApiException {
        try {
            AtomicSequence v = getUnderlyingNode().atomize();
            return XdmValue.wrap(v);
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Get the line number of the node in a source document. For a document constructed using the document
     * builder, this is available only if the line numbering option was set when the document was built (and
     * then only for element nodes). If the line number is not available, the value -1 is returned. Line numbers
     * will typically be as reported by a SAX parser: this means that the line number for an element node is the
     * line number containing the closing "&gt;" of the start tag.
     *
     * @return the line number of the node, or -1 if not available.
     */

    public int getLineNumber() {
        return getUnderlyingNode().getLineNumber();
    }

    /**
     * Get the column number of the node in a source document. For a document constructed using the document
     * builder, this is available only if the line numbering option was set when the document was built (and
     * then only for element nodes). If the column number is not available, the value -1 is returned. Column numbers
     * will typically be as reported by a SAX parser: this means that the column number for an element node is the
     * position of the closing "&gt;" of the start tag.
     *
     * @return the column number of the node, or -1 if not available.
     */

    public int getColumnNumber() {
        return getUnderlyingNode().getColumnNumber();
    }

    /**
     * Get a JAXP Source object corresponding to this node, allowing the node to be
     * used as input to transformations or queries.
     * <p>The Source object that is returned will generally be one that is acceptable to Saxon interfaces
     * that expect an instance of <code>javax.xml.transform.Source</code>. However, there is no guarantee
     * that it will be recognized by other products.</p>
     * <p><i>In fact, the current implementation always returns an instance of
     * <code>net.sf.saxon.om.NodeInfo</code>.</i></p>
     *
     * @return a Source object corresponding to this node
     */

    public Source asSource() {
        return getUnderlyingNode();
    }


    /**
     * Get the children of this node
     * @return an {@link Iterable} containing all nodes on the child axis
     * @since 9.9
     */
    public Iterable<XdmNode> children() {
        return select(Steps.child()).asListOfNodes();
    }

    /**
     * Get the element children of this node having a specified
     * local name, irrespective of the namespace
     *
     * @param localName the local name of the child elements to be selected,
     *                  or "*" to select all children that are element nodes
     * @return an {@link Iterable} containing the element children of this node that have the
     * required local name.
     * @since 9.9
     */

    public Iterable<XdmNode> children(String localName) {
        return select(Steps.child(localName)).asListOfNodes();
    }

    /**
     * Get the element children having a specified namespace URI and local name
     *
     * @param uri       the namespace URI of the child elements to be selected:
     *                  supply a zero-length string to indicate the null namespace
     * @param localName the local name of the child elements to be selected
     * @return an {@link Iterable} containing the element children of this node that have the
     * required local name and namespace URI.
     * @since 9.9
     */

    public Iterable<XdmNode> children(String uri, String localName) {
        return select(Steps.child(uri, localName)).asListOfNodes();
    }

    /**
     * Get the nodes found on the child axis that satisfy a supplied {@code Predicate}.
     *
     * @param filter the predicate to be applied
     * @return an {@link Iterable} containing those nodes found on the child axis that satisfy the supplied {@code Predicate}.
     * @since 9.9
     */

    public Iterable<XdmNode> children(Predicate<? super XdmNode> filter) {
        return select(Steps.child(filter)).asListOfNodes();
    }

    /**
     * Get an iterator over the nodes reachable from this node via a given axis.
     *
     * @param axis identifies which axis is to be navigated
     * @return an iterator over the nodes on the specified axis, starting from this node as the
     *         context node. The nodes are returned in axis order, that is, in document order for a forwards
     *         axis and in reverse document order for a reverse axis.
     */

    public XdmSequenceIterator<XdmNode> axisIterator(Axis axis) {
        AxisIterator base = getUnderlyingNode().iterateAxis(axis.getAxisNumber());
        return XdmSequenceIterator.ofNodes(base);
    }

    /**
     * Get an iterator over the nodes reachable from this node via a given axis, selecting only
     * those nodes with a specified name.
     *
     * @param axis identifies which axis is to be navigated
     * @param name identifies the name of the nodes to be selected. The selected nodes will be those
     *             whose node kind is the principal node kind of the axis (that is, attributes for the attribute
     *             axis, namespaces for the namespace axis, and elements for all other axes) whose name matches
     *             the specified name.
     *             <p>For example, specify <code>new QName("", "item")</code> to select nodes with local name
     *             "item", in no namespace.</p>
     * @return an iterator over the nodes on the specified axis, starting from this node as the
     *         context node. The nodes are returned in axis order, that is, in document order for a forwards
     *         axis and in reverse document order for a reverse axis.
     */

    public XdmSequenceIterator<XdmNode> axisIterator(Axis axis, QName name) {
        int kind;
        switch (axis) {
            case ATTRIBUTE:
                kind = Type.ATTRIBUTE;
                break;
            case NAMESPACE:
                kind = Type.NAMESPACE;
                break;
            default:
                kind = Type.ELEMENT;
                break;
        }
        NodeInfo node = getUnderlyingNode();
        NameTest test = new NameTest(kind, name.getNamespaceURI(), name.getLocalName(),
            node.getConfiguration().getNamePool());
        AxisIterator base = node.iterateAxis(axis.getAxisNumber(), test);
        return XdmSequenceIterator.ofNodes(base);
    }

    /**
     * Get the parent of this node
     *
     * @return the parent of this node (a document or element node), or null if this node has no parent.
     */

    public XdmNode getParent() {
        NodeInfo p = getUnderlyingNode().getParent();
        return p == null ? null : (XdmNode) XdmValue.wrap(p);
    }

    /**
     * Get the root of the tree containing this node (which may or may not be a document node)
     *
     * @return the root of the tree containing this node. Returns this node itself if it has no parent.
     * @since 9.9
     */

    public XdmNode getRoot() {
        NodeInfo p = getUnderlyingNode().getRoot();
        return p == null ? null : (XdmNode) XdmValue.wrap(p);
    }

    /**
     * Get the string value of a named attribute of this element
     *
     * @param name the name of the required attribute
     * @return null if this node is not an element, or if this element has no
     *         attribute with the specified name. Otherwise return the string value of the
     *         selected attribute node.
     */

    public String getAttributeValue(QName name) {
        NodeInfo node = getUnderlyingNode();
        return node.getAttributeValue(name.getNamespaceURI(), name.getLocalName());
    }

    /**
     * Get the string value of a named attribute (in no namespace) of this element
     *
     * @param name the name of the required attribute, interpreted as a no-namespace name
     * @return null if this node is not an element, or if this element has no
     * attribute with the specified name. Otherwise return the string value of the
     * selected attribute node.
     * @since 9.9
     */

    public String attribute(String name) {
        return getUnderlyingNode().getAttributeValue("", name);
    }


    /**
     * Get the base URI of this node
     *
     * @return the base URI, as defined in the XDM model. The value may be null if no base URI is known for the
     *         node, for example if the tree was built from a StreamSource with no associated URI, or if the node has
     *         no parent.
     * @throws IllegalStateException if the base URI property of the underlying node is not a valid URI.
     */

    public URI getBaseURI() {
        try {
            String uri = getUnderlyingNode().getBaseURI();
            if (uri == null) {
                return null;
            }
            return new URI(uri);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("baseURI", e);
        }
    }

    /**
     * Get the document URI of this node.
     *
     * @return the document URI, as defined in the XDM model. Returns null if no document URI is known
     * @throws IllegalStateException if the document URI property of the underlying node is not a valid URI.
     * @since 9.1
     */

    public URI getDocumentURI() {
        try {
            String systemId = getUnderlyingNode().getSystemId();
            return systemId == null || systemId.isEmpty() ? null : new URI(systemId);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("documentURI", e);
        }
    }


    /**
     * The hashcode is such that two XdmNode instances have the same hashCode if they represent the same
     * node. Note that the same node might be represented by different XdmNode objects, but these will
     * compare equal.
     *
     * @return a hashCode representing node identity
     */

    public int hashCode() {
        return getUnderlyingNode().hashCode();
    }

    /**
     * The <code>equals()</code> relation between two XdmNode objects is true if they both represent the same
     * node. That is, it corresponds to the "is" operator in XPath.
     *
     * @param other the object to be compared
     * @return true if and only if the other object is an XdmNode instance representing the same node
     */

    public boolean equals(Object other) {
        return other instanceof XdmNode &&
                getUnderlyingNode().equals(((XdmNode) other).getUnderlyingNode());
    }

    /**
     * The toString() method returns a simple XML serialization of the node
     * with defaulted serialization parameters.
     * <p>In the case of an element node, the result will be a well-formed
     * XML document serialized as defined in the W3C XSLT/XQuery serialization specification,
     * using options method="xml", indent="yes", omit-xml-declaration="yes".</p>
     * <p>In the case of a document node, the result will be a well-formed
     * XML document provided that the document node contains exactly one element child,
     * and no text node children. In other cases it will be a well-formed external
     * general parsed entity.</p>
     * <p>In the case of an attribute node, the output is a string in the form
     * <code>name="value"</code>. The name will use the original namespace prefix.</p>
     * <p>In the case of a namespace node, the output is a string in the form of a namespace
     * declaration, that is <code>xmlns="uri"</code> or <code>xmlns:pre="uri"</code>.</p>
     * <p>Other nodes, such as text nodes, comments, and processing instructions, are
     * represented as they would appear in lexical XML. Note: this means that in the case
     * of text nodes, special characters such as <code>&amp;</code> and <code>&lt;</code>
     * are output in escaped form. To get the unescaped string value of a text node, use
     * {@link #getStringValue()} instead.</p>
     * <p><i>For more control over serialization, use the {@link Serializer} class.</i></p>
     *
     * @return a simple XML serialization of the node. Under error conditions the method
     * may return an error message which will always begin with the label "Error: ".
     */

    public String toString() {
        NodeInfo node = getUnderlyingNode();

        if (node.getNodeKind() == Type.ATTRIBUTE) {
            String val = node.getStringValue()
                    .replace("&", "&amp;")
                    .replace("\"", "&quot;")
                    .replace("<", "&lt;");
            return node.getDisplayName() + "=\"" + val + '"';
        } else if (node.getNodeKind() == Type.NAMESPACE) {
            String val = node.getStringValue()
                    .replace("&", "&amp;")
                    .replace("\"", "&quot;")
                    .replace("<", "&lt;");
            String name = node.getDisplayName();
            name = name.equals("") ? "xmlns" : "xmlns:" + name;
            return name + "=\"" + val + '"';
        } else if (node.getNodeKind() == Type.TEXT) {
            return node.getStringValue()
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace("]]>", "]]&gt;");
        }

        try {
            return QueryResult.serialize(node).trim();
        } catch (XPathException err) {
            throw new IllegalStateException(err);
        }
    }

    /**
     * Get the underlying Saxon implementation object representing this node. This provides
     * access to classes and methods in the Saxon implementation that may be subject to change
     * from one release to another.
     *
     * @return the underlying implementation object
     */

    public NodeInfo getUnderlyingNode() {
        return getUnderlyingValue();
    }

    /**
     * In the case of an XdmNode that wraps a node in an external object model such as DOM, JDOM,
     * XOM, or DOM4J, get the underlying wrapped node
     *
     * @return the underlying external node if there is one, or null if this is not an XdmNode that
     *         wraps such an external node
     * @since 9.1.0.2
     */

    public Object getExternalNode() {
        NodeInfo saxonNode = getUnderlyingNode();
        if (saxonNode instanceof VirtualNode) {
            Object externalNode = ((VirtualNode) saxonNode).getRealNode();
            return externalNode instanceof NodeInfo ? null : externalNode;
        } else {
            return null;
        }
    }

    public XdmSequenceIterator<XdmNode> nodeIterator() {
        return XdmSequenceIterator.ofNode(this);
    }

    /**
     * Get a stream comprising the items in this value
     *
     * @return a Stream over the items in this value
     * @since 9.9
     */
    @Override
    public XdmStream<XdmNode> stream() {
        return new XdmStream<>(Stream.of(this));
    }


}

