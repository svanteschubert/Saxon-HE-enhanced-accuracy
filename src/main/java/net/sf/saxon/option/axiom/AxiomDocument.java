////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.axiom;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.GenericTreeInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.tree.iter.AxisIterator;
import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMDocument;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNode;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Information about a tree that wraps an AXIOM document
 */

public class AxiomDocument extends GenericTreeInfo {

    private HashMap<String, NodeInfo> idIndex;

    /**
     * Create a Saxon wrapper for an Axiom document node
     *
     * @param root    The Axiom root node
     * @param baseURI The base URI for all the nodes in the tree
     * @param config  The configuration which defines the name pool used for all
     *                names in this tree
     */
    public AxiomDocument(OMDocument root, String baseURI, Configuration config) {
        super(config);
        setRootNode(new AxiomDocumentNodeWrapper(root, baseURI, config, this));
        setSystemId(baseURI);
    }

    /**
     * Wrap a node in the Axiom document.
     *
     * @param node The node to be wrapped. This must be a node in the same
     *             document (the system does not check for this).
     * @return the wrapping NodeInfo object
     */

    public NodeInfo wrap(OMNode node) {
        return makeWrapper(node, this, null, -1);
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
     * Get the element with a given ID, if any
     *
     * @param id        the required ID value
     * @param getParent true if the parent of the selected node is required (for element-with-id)
     * @return the element with the given ID, or null if there is no such ID
     *         present (or if the parser has not notified attributes as being of
     *         type ID).
     */

    /*@Nullable*/
    @Override
    public NodeInfo selectID(String id, boolean getParent) {
        if (idIndex == null) {
            idIndex = new HashMap<String, NodeInfo>(50);
            AxiomDocumentNodeWrapper doc = (AxiomDocumentNodeWrapper)getRootNode();
            OMDocument omDoc = (OMDocument)doc.node;
            buildIDIndex(omDoc.getOMDocumentElement());
        }
        return idIndex.get(id);
    }


    private void buildIDIndex(OMElement elem) {
        for (Iterator kids = elem.getChildElements(); kids.hasNext(); ) {
            buildIDIndex((OMElement) kids.next());
        }
        for (Iterator atts = elem.getAllAttributes(); atts.hasNext(); ) {
            OMAttribute att = (OMAttribute) atts.next();
            if ("ID".equals(att.getAttributeType()) ||
                    ("id".equals(att.getLocalName()) && NamespaceConstant.XML.equals(att.getNamespaceURI()))) {
                String val = att.getAttributeValue();
                if (idIndex.get(val) == null) {
                    // if ID's aren't unique, the first one wins
                    idIndex.put(val, wrap(elem));
                }
            }
        }
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

