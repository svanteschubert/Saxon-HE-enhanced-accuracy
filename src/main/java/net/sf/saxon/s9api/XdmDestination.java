////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.event.*;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.TreeModel;
import net.sf.saxon.serialize.SerializationProperties;

import java.net.URI;

/**
 * An <code>XdmDestination</code> is a {@link Destination} in which an {@link XdmNode}
 * is constructed to hold the output of a query or transformation:
 * that is, a tree using Saxon's implementation of the XDM data model
 * <p>No data needs to be supplied to the <code>XdmDestination</code> object. The query or transformation
 * populates an <code>XdmNode</code>, which may then be retrieved using the <code>getXdmNode</code>
 * method.</p>
 * <p>An <code>XdmDestination</code> is designed to hold a single tree rooted at a document node.
 * If the result of a query is an arbitrary sequence, it will be normalized according to the rules
 * of the "sequence normalization" operation described in the W3C XSLT/XQuery Serialization
 * Recommendation, which results in either a single document node, or an error.</p>
 * <p>An XdmDestination can be reused to hold the results of a second query or transformation only
 * if the {@link #reset} method is first called to reset its state.</p>
 * <p>If an XDM tree is to be built from a lexical XML document, or programmatically from the application
 * by writing a sequence of events, the recommended mechanism is to use a {@link DocumentBuilder} rather
 * than this class.</p>
 * @since 9.1. Changed in 9.9 to perform sequence normalization, so the result is always a single
 * document node. To get the raw results of a query or stylesheet without sequence normalization,
 * use a {@link RawDestination}.
 */

public class XdmDestination extends AbstractDestination {

    TreeModel treeModel = TreeModel.TINY_TREE;
    Builder builder;

    public XdmDestination() { }

    /**
     * Set the base URI for the document node that will be created when the XdmDestination is written to.
     * This method must be called before writing to the destination; it has no effect on an XdmNode that
     * has already been constructed.
     *
     * @param baseURI the base URI for the node that will be constructed when the XdmDestination is written to.
     *                This must be an absolute URI
     * @throws IllegalArgumentException if the baseURI supplied is not an absolute URI
     * @since 9.1
     */

    public void setBaseURI(URI baseURI) {
        if (!baseURI.isAbsolute()) {
            throw new IllegalArgumentException("Supplied base URI must be absolute");
        }
        setDestinationBaseURI(baseURI);
    }

    /**
     * Get the base URI that will be used for the document node when the XdmDestination is written to.
     *
     * @return the base URI that will be used for the node that is constructed when the XdmDestination is written to.
     * @since 9.1
     */

    public URI getBaseURI() {
        return getDestinationBaseURI();
    }

    /**
     * Set the tree model to be used for documents constructed using this XdmDestination.
     * By default, the TinyTree is used.
     *
     * @param model typically one of the constants {@link net.sf.saxon.om.TreeModel#TINY_TREE},
     *              {@link TreeModel#TINY_TREE_CONDENSED}, or {@link TreeModel#LINKED_TREE}. However, in principle
     *              a user-defined tree model can be used.
     * @since 9.2
     */

    public void setTreeModel(TreeModel model) {
        this.treeModel = model;
    }

    /**
     * Get the tree model to be used for documents constructed using this XdmDestination.
     * By default, the TinyTree is used.
     *
     * @return the tree model in use: typically one of the constants {@link net.sf.saxon.om.TreeModel#TINY_TREE},
     *         {@link net.sf.saxon.om.TreeModel#TINY_TREE_CONDENSED}, or {@link TreeModel#LINKED_TREE}. However, in principle
     *         a user-defined tree model can be used.
     * @since 9.2
     */

    public TreeModel getTreeModel() {
        return treeModel;
    }

    /**
     * Return a Receiver. Saxon calls this method to obtain a Receiver, to which it then sends
     * a sequence of events representing the content of an XML document.
     *
     * @param pipe The Saxon configuration. This is supplied so that the destination can
     *               use information from the configuration (for example, a reference to the name pool)
     *               to construct or configure the returned Receiver.
     * @param params The serialization properties requested. Largely irrelevant for this {@code Destination},
     *               except perhaps for {@code item-separator}.
     * @return the Receiver to which events are to be sent.
     */

    @Override
    public Receiver getReceiver(PipelineConfiguration pipe, SerializationProperties params) {
        TreeModel model = treeModel;
        if (model == null) {
            int m = pipe.getParseOptions().getTreeModel();
            if (m != Builder.UNSPECIFIED_TREE_MODEL) {
                model = TreeModel.getTreeModel(m);
            }
            if (model == null) {
                model = TreeModel.TINY_TREE;
            }
        }
        builder = model.makeBuilder(pipe);
        String systemId = getBaseURI() == null ? null : getBaseURI().toASCIIString();
        if (systemId != null) {
            builder.setUseEventLocation(false);
            builder.setBaseURI(systemId);
        }
        SequenceNormalizer sn = params.makeSequenceNormalizer(builder);
        sn.setSystemId(systemId);
        sn.onClose(helper.getListeners());
        return sn;
        //return new TracingFilter(sn);
    }

    /**
     * Close the destination, allowing resources to be released. Saxon calls this method when
     * it has finished writing to the destination.
     */

    @Override
    public void close() {
        // no action
    }

    /**
     * Return the node at the root of the tree, after it has been constructed.
     * <p>This method should not be called while the tree is under construction.</p>
     *
     * @return the root node of the tree (always a document or element node); or null if
     *         nothing is written to the tree (for example, the result of a query that returns the
     *         empty sequence)
     * @throws IllegalStateException if called during the execution of the process that
     *                               is writing the tree.
     */

    public XdmNode getXdmNode() {
        if (builder == null) {
            throw new IllegalStateException("The document has not yet been built");
        }
        NodeInfo node = builder.getCurrentRoot();
        return node == null ? null : (XdmNode) XdmValue.wrap(node);
    }

    /**
     * Allow the <code>XdmDestination</code> to be reused, without resetting other properties
     * of the destination.
     */

    public void reset() {
        builder = null;
    }


}

