////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.axiom;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.GenericTreeInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.type.Type;
import org.apache.axiom.om.OMDocument;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNode;

import java.util.function.Predicate;

/**
 * The document node in an AXIOM tree
 */

public class AxiomDocumentNodeWrapper extends AxiomParentNodeWrapper  {

    /**
     * Create a Saxon wrapper for an Axiom document node
     *
     * @param root    The Axiom root node
     * @param baseURI The base URI for all the nodes in the tree
     * @param config  The configuration which defines the name pool used for all
     *                names in this tree
     */
    public AxiomDocumentNodeWrapper(OMDocument root, String baseURI, Configuration config) {
        super(root);
        if (!config.isLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION)) {
            config.requireProfessionalLicense("Axiom");
        }
        treeInfo = new AxiomDocument(root, baseURI, config);
        ((GenericTreeInfo)treeInfo).setRootNode(this);
    }

    /**
     * Create a Saxon wrapper for an Axiom document node(internal constructor used when the TreeInfo
     * already exists)
     *
     * @param root     The Axiom root node
     * @param baseURI  The base URI for all the nodes in the tree
     * @param config   The configuration which defines the name pool used for all
     *                 names in this tree
     * @param treeInfo object containing information about the tree as a whole
     */
    AxiomDocumentNodeWrapper(OMDocument root, String baseURI, Configuration config, TreeInfo treeInfo) {
        super(root);
        this.treeInfo = treeInfo;
    }


    /**
     * Factory method to wrap an Axiom node with a wrapper that implements the
     * Saxon NodeInfo interface.
     *
     * @param node       The Axiom node (an element, text, processing-instruction, or comment node)
     * @param docWrapper The wrapper for the Document containing this node
     * @param parent     The wrapper for the parent of the Axiom node. May be null if not known.
     * @param index      The position of this node relative to its siblings. May be -1 if not known
     * @return The new wrapper for the supplied node
     */

    protected static NodeInfo makeWrapper(OMNode node, AxiomDocument docWrapper,
                                          AxiomParentNodeWrapper parent, int index) {
        if (node instanceof OMDocument) {
            return docWrapper.getRootNode();
        }
        if (node instanceof OMElement) {
            return new AxiomElementNodeWrapper((OMElement) node, docWrapper, parent, index);
        } else {
            return new AxiomLeafNodeWrapper(node, docWrapper, parent, index);
        }
    }

    /**
     * Ask whether the document contains any nodes whose type annotation is anything other than
     * UNTYPED
     *
     * @return true if the document contains elements whose type is other than UNTYPED
     */
    public boolean isTyped() {
        return false;
    }

    /**
     * Get the index position of this node among its siblings (starting from 0)
     *
     * @return 0 for the first child, 1 for the second child, etc.
     */
    @Override
    public int getSiblingPosition() {
        return 0;
    }


    /**
     * Get the kind of node. This will be a value such as {@link net.sf.saxon.type.Type#ELEMENT}
     * or {@link net.sf.saxon.type.Type#ATTRIBUTE}. There are seven kinds of node: documents, elements, attributes,
     * text, comments, processing-instructions, and namespaces.
     *
     * @return an integer identifying the kind of node. These integer values are the
     * same as those used in the DOM
     * @see net.sf.saxon.type.Type
     * @since 8.4
     */
    @Override
    public int getNodeKind() {
        return Type.DOCUMENT;
    }

    /**
     * Determine whether this is the same node as another node.
     * <p>Note that two different NodeInfo instances can represent the same conceptual node.
     * Therefore the "==" operator should not be used to test node identity. The equals()
     * method should give the same result as isSameNodeInfo(), but since this rule was introduced
     * late it might not apply to all implementations.</p>
     * <p>Note: a.isSameNodeInfo(b) if and only if generateId(a)==generateId(b).</p>
     * <p>This method has the same semantics as isSameNode() in DOM Level 3, but
     * works on Saxon NodeInfo objects rather than DOM Node objects.</p>
     *
     * @param other the node to be compared with this node
     * @return true if this NodeInfo object and the supplied NodeInfo object represent
     * the same node in the tree.
     */
    public boolean equals(Object other) {
        return other == this;
    }

    /**
     * Get the Base URI for the node, that is, the URI used for resolving a relative URI contained
     * in the node.
     */
    @Override
    public String getBaseURI() {
        return getTreeInfo().getSystemId();
    }

    /**
     * Get the System ID for the node. Note this is not the
     * same as the base URI: the base URI can be modified by xml:base, but
     * the system ID cannot. The base URI is used primarily for resolving
     * relative URIs within the content of the document. The system ID is
     * used primarily in conjunction with a line number, for identifying the
     * location of elements within the source XML, in particular when errors
     * are found. For a document node, the System ID represents the value of
     * the document-uri property as defined in the XDM data model.
     *
     * @return the System Identifier of the entity in the source document
     * containing the node, or null if not known or not applicable.
     * @since 8.4
     */
    @Override
    public String getSystemId() {
        return getTreeInfo().getSystemId();
    }

    /**
     * Set the system identifier for this Source.
     * <p>The system identifier is optional if the source does not
     * get its data from a URL, but it may still be useful to provide one.
     * The application can use a system identifier, for example, to resolve
     * relative URIs and to include in error messages and warnings.</p>
     *
     * @param systemId The system identifier as a URL string.
     */
    @Override
    public void setSystemId(String systemId) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * Get the NodeInfo object representing the parent of this node
     */

    @Override
    public NodeInfo getParent() {
        return null;
    }


    /**
     * Get the local part of the name of this node. This is the name after the
     * ":" if any.
     *
     * @return the local part of the name. For an unnamed node, returns "".
     */

    @Override
    public String getLocalPart() {
        return "";
    }

    /**
     * Get the prefix of the name of the node. This is defined only for elements and attributes.
     * If the node has no prefix, or for other kinds of node, return a zero-length string.
     *
     * @return The prefix of the name of the node.
     */

    @Override
    public String getPrefix() {
        return "";
    }

    /**
     * Get the URI part of the name of this node. This is the URI corresponding
     * to the prefix, or the URI of the default namespace if appropriate.
     *
     * @return The URI of the namespace of this node. For an unnamed node, or
     * for a node with an empty prefix, return an empty string.
     */

    @Override
    public String getURI() {
        return "";
    }

    /**
     * Get the display name of this node. For elements and attributes this is
     * [prefix:]localname. For unnamed nodes, it is an empty string.
     *
     * @return The display name of this node. For a node with no name, return an
     * empty string.
     */

    @Override
    public String getDisplayName() {
        return "";
    }

    /**
     * Get the root node of the tree containing this node
     *
     * @return the NodeInfo representing the top-level ancestor of this node.
     * This will not necessarily be a document node. If this node has no parent,
     * then the method returns this node.
     * @since 8.4
     */
    @Override
    public NodeInfo getRoot() {
        return this;
    }

    @Override
    protected AxisIterator iterateAttributes(Predicate<? super NodeInfo> nodeTest) {
        return EmptyIterator.ofNodes();
    }

    @Override
    protected AxisIterator iterateSiblings(Predicate<? super NodeInfo> nodeTest, boolean forwards) {
        return EmptyIterator.ofNodes();
    }

    /**
     * Determine the relative position of this node and another node, in
     * document order. The other node will always be in the same document.
     *
     * @param other The other node, whose position is to be compared with this
     *              node
     * @return -1 if this node precedes the other node, +1 if it follows the
     * other node, or 0 if they are the same node. (In this case,
     * isSameNode() will always return true, and the two nodes will
     * produce the same result for generateId())
     */

    @Override
    public int compareOrder(NodeInfo other) {
        return other == this ? 0 : -1;
    }

    protected static class FollowingSiblingIterator implements AxisIterator {

        private OMNode start;
        private OMNode currentOMNode;
        private AxiomParentNodeWrapper commonParent;
        private AxiomDocument docWrapper;

        public FollowingSiblingIterator(OMNode start, AxiomParentNodeWrapper commonParent, AxiomDocument docWrapper) {
            this.start = start;
            this.currentOMNode = start;
            this.commonParent = commonParent;
            this.docWrapper = docWrapper;
        }

        @Override
        public NodeInfo next() {
            if (currentOMNode == null) {
                return null;
            }
            currentOMNode = currentOMNode.getNextOMSibling();
            if (currentOMNode == null) {
                return null;
            } else {
                return makeWrapper(currentOMNode, docWrapper, commonParent, -1);
            }
        }

    }

    protected static class PrecedingSiblingIterator implements AxisIterator {

        private OMNode start;
        private OMNode currentOMNode;
        private AxiomParentNodeWrapper commonParent;
        private AxiomDocument docWrapper;

        public PrecedingSiblingIterator(OMNode start, AxiomParentNodeWrapper commonParent, AxiomDocument docWrapper) {
            this.start = start;
            this.currentOMNode = start;
            this.commonParent = commonParent;
            this.docWrapper = docWrapper;
        }

        @Override
        public NodeInfo next() {
            if (currentOMNode == null) {
                return null;
            }
            currentOMNode = currentOMNode.getPreviousOMSibling();
            if (currentOMNode == null) {
                return null;
            } else {
                return makeWrapper(currentOMNode, docWrapper, commonParent, -1);
            }
        }

    }
}

