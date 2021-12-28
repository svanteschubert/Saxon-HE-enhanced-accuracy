////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.sapling;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.*;
import net.sf.saxon.ma.trie.ImmutableList;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.TreeModel;
import net.sf.saxon.s9api.*;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;

import javax.xml.transform.Source;

/**
 * A document node in a sapling tree. A document node may have a sequence of children, each of which
 * is an element, text, comment, or processing-instruction node. A document node has an optional base URI.
 *
 * <p>Like all sapling nodes, a {@code SaplingDocument} is immutable. All operations such as adding children
 *  deliver a new document node. A sapling node generally exists only transiently during tree
 *  construction; to make use of the constructed tree, it will usually be converted to a regular tree when
 *  construction is complete, using {@link #toXdmNode(Processor)} or {@link #toNodeInfo(Configuration)}.</p>
 *
 * <p>{@code SaplingDocument} implements {@link javax.xml.transform.Source}, and a sapling document
 * can therefore be supplied in most Saxon interfaces that expect a Source. However, third-party software
 * that expects a {@code Source} as input is unlikely to recognize this class, unless explicitly
 * stated to the contrary.</p>
 */

public class SaplingDocument extends SaplingNode implements Source {

    private String baseUri;
    private ImmutableList<SaplingNode> reversedChildren = ImmutableList.empty();

    /**
     * Create a sapling document node with no children and no base URI
     */

    public SaplingDocument() {

    }

    /**
     * Create a sapling document node with no children, having a specified base URI
     * @param baseUri the base URI of the document node
     */

    public SaplingDocument(String baseUri) {
        this.baseUri = baseUri;
    }

    /**
     * Set the system identifier for this Source. This method is provided
     * because it is needed to satisfy the {@link Source} interface; however
     * it always throws an exception, because {@code SaplingDocument} is immutable.
     *
     * @param systemId The system identifier as a URL string.
     * @throws UnsupportedOperationException always
     */
    @Override
    public void setSystemId(String systemId) {
        throw new UnsupportedOperationException();
    }

    /**
     * Get the system identifier. This implementation of the
     * method returns the base URI.
     *
     * @return The base URI of the document.
     */
    @Override
    public String getSystemId() {
        return baseUri;
    }

    @Override
    public int getNodeKind() {
        return Type.DOCUMENT;
    }

    private SaplingDocument copy() {
        SaplingDocument d2 = new SaplingDocument(baseUri);
        d2.reversedChildren = reversedChildren;
        return d2;
    }

    /**
     * Add a number of child nodes to a document node, returning a new document node
     * with additional children beyond those already present. The target document is not modified,
     * neither are the added children.
     * @param children The nodes to be added as children.
     * The supplied nodes are added in order after any existing children.
     * @return the new parent document node
     * @throws IllegalArgumentException if any of the nodes supplied as an argument is
     *                                  a document node.
     */

    public SaplingDocument withChild(SaplingNode... children) {
        SaplingDocument e2 = copy();
        for (SaplingNode node : children) {
            switch (node.getNodeKind()) {
                case Type.DOCUMENT:
                    throw new IllegalArgumentException("Cannot add document child to a document node");
                case Type.ELEMENT:
                case Type.TEXT:
                case Type.COMMENT:
                case Type.PROCESSING_INSTRUCTION:
                    e2.reversedChildren = e2.reversedChildren.prepend(node);
                    break;
            }
        }
        return e2;
    }

    @Override
    public void sendTo(Receiver receiver) throws XPathException {
        receiver = new NamespaceReducer(receiver);
        receiver.open();
        receiver.setSystemId(baseUri);
        receiver.startDocument(ReceiverOption.NONE);
        ImmutableList<SaplingNode> children = reversedChildren.reverse();
        for (SaplingNode node : children) {
            node.sendTo(receiver);
        }
        receiver.endDocument();
        receiver.close();
    }

    /**
     * Convert the sapling document to a regular document, returning the {@link NodeInfo} object
     * representing the document node of the resulting tree
     * @param config the Saxon Configuration
     * @return the document node at the root of the constructed tree. The implementation model for the
     * tree will be the default tree model of the Configuration
     * @throws XPathException if construction fails; this could happen if constraints have been violated
     */

    public NodeInfo toNodeInfo(Configuration config) throws XPathException {
        PipelineConfiguration pipe = config.makePipelineConfiguration();
        TreeModel treeModel = config.getParseOptions().getModel();
        Builder builder = treeModel.makeBuilder(pipe);
        builder.open();
        sendTo(builder);
        builder.close();
        return builder.getCurrentRoot();
    }

    /**
     * Convert the sapling document to a regular document, returning the {@link XdmNode} object
     * representing the document node of the resulting tree
     *
     * @param processor the s9api Processor object that is to own the resulting tree
     * @return the document node at the root of the constructed tree. The implementation model for the
     * tree will be the default tree model of the Processor's Configuration
     * @throws SaxonApiException if construction fails; this could happen if constraints have been violated
     */

    public XdmNode toXdmNode(Processor processor) throws SaxonApiException {
        try {
            return (XdmNode)XdmValue.wrap(toNodeInfo(processor.getUnderlyingConfiguration()));
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Serialize the document
     * @param serializer the serializer to be used; the serialized representation of the node
     *                   will be written to the serializer's destination.
     * @throws SaxonApiException if anything goes wrong
     */

    public void serialize(Serializer serializer) throws SaxonApiException {
        Processor proc = serializer.getProcessor();
        send(proc, serializer);
    }

    /**
     * Send the document to an arbitrary destination
     * @param processor  the s9api processor
     * @param destination the destination to which the document will be copied.
     * @throws SaxonApiException if anything goes wrong
     */

    public void send(Processor processor, Destination destination) throws SaxonApiException {
        try {
            PipelineConfiguration pipe = processor.getUnderlyingConfiguration().makePipelineConfiguration();
            sendTo(destination.getReceiver(pipe, new SerializationProperties()));
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

}


