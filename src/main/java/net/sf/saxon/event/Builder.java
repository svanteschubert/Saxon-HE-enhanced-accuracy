////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.CommandLineOptions;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;
import net.sf.saxon.type.SchemaType;

/**
 * The abstract Builder class is responsible for taking a stream of SAX events
 * and constructing a Document tree. There is one concrete subclass for each
 * tree implementation.
 *
 */

public abstract class Builder implements Receiver {
    /**
     * Constant denoting a request for the default tree model
     */
    public static final int UNSPECIFIED_TREE_MODEL = -1;
    /**
     * Constant denoting the "linked tree" in which each node is represented as an object
     */
    public static final int LINKED_TREE = 0;
    /**
     * Constant denoting the "tiny tree" in which the tree is represented internally using arrays of integers
     */
    public static final int TINY_TREE = 1;
    /**
     * Constant denoting the "tiny tree condensed", a variant of the tiny tree in which text and attribute nodes
     * sharing the same string value use shared storage for the value.
     */
    public static final int TINY_TREE_CONDENSED = 2;

    public static final int JDOM_TREE = 3;
    public static final int JDOM2_TREE = 4;
    public static final int AXIOM_TREE = 5;
    public static final int DOMINO_TREE = 6;

    /**
     * Constant denoting the "mutable linked tree" in which each node is represented as an object
     */
    public static final int MUTABLE_LINKED_TREE = 7;

    protected PipelineConfiguration pipe;
    protected Configuration config;
    protected NamePool namePool;
    protected String systemId;
    protected String baseURI;
    protected boolean uniformBaseURI = true;
    protected NodeInfo currentRoot;
    protected boolean lineNumbering = false;
    protected boolean useEventLocation = true;

    protected boolean started = false;
    protected boolean timing = false;
    protected boolean open = false;

    private long startTime;

    /**
     * Create a Builder and initialise variables
     */

    public Builder() {
    }

    public Builder(PipelineConfiguration pipe) {
        this.pipe = pipe;
        config = pipe.getConfiguration();
        lineNumbering = config.isLineNumbering();
        namePool = config.getNamePool();
    }

    @Override
    public void setPipelineConfiguration(/*@NotNull*/ PipelineConfiguration pipe) {
        this.pipe = pipe;
        config = pipe.getConfiguration();
        lineNumbering = lineNumbering || config.isLineNumbering();
        namePool = config.getNamePool();
    }

    /*@NotNull*/
    @Override
    public PipelineConfiguration getPipelineConfiguration() {
        return pipe;
    }

    /**
     * Get the Configuration
     *
     * @return the Saxon configuration
     */

    /*@NotNull*/
    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Get a builder monitor for this builder. This must be called immediately after opening the builder,
     * and all events to the builder must thenceforth be sent via the BuilderMonitor.
     *
     * @return a new BuilderMonitor appropriate to this kind of Builder; or null if the Builder does
     *         not provide this service. The default implementation returns null.
     */

    /*@Nullable*/
    public BuilderMonitor getBuilderMonitor() {
        return null;
    }

    /**
     * Say that the system IDs of constructed nodes (especially element nodes) are to be
     * taken from the system ID property of the {@link Location} object passed as a parameter to
     * the {@link Outputter#startElement(NodeName, SchemaType, Location, int)} event. This property
     * should be set to true when building a document from events originating with an XML parser,
     * because the {@link Location} object in this case will have a system ID that changes
     * as external entities are processed. The base URI of a node in this case is determined
     * by the system ID, modified by any <code>xml:base</code> attributes. If the property
     * is set to false, all nodes in the tree have the same system ID, this being supplied
     * as the systemID property of the builder. This will typically be the static base URI
     * of the instruction in the query or stylesheet that was used to construct the root node;
     * in the case of <code>xsl:result-document</code>, it will be the absolutized value of the
     * <code>href</code> attribute of the instruction.
     * @param useEventLocation true if the system ID is to be taken from the Location parameter of
     *                    each event. The default value is true.
     */

    public void setUseEventLocation(boolean useEventLocation) {
        this.useEventLocation = useEventLocation;
    }

    /**
     * Ask whether the system IDs of constructed nodes (especially element nodes) are to be
     * taken from the system ID property of the {@link Location} object passed as a parameter to
     * the {@link Outputter#startElement(NodeName, SchemaType, Location, int)} event.
     * @return true if the system ID is to be taken from the Location parameter of
     *      each event. The default value is true.
     */

    public boolean isUseEventLocation() {
        return useEventLocation;
    }

    /**
     * The SystemId is equivalent to the document-uri property defined in the XDM data model.
     * It should be set only in the case of a document that is potentially retrievable via this URI.
     * This means it should not be set in the case of a temporary tree constructed in the course of
     * executing a query or transformation.
     *
     * @param systemId the SystemId, that is, the document-uri.
     */

    @Override
    public void setSystemId(/*@Nullable*/ String systemId) {
        this.systemId = systemId;
    }

    /**
     * The SystemId is equivalent to the document-uri property defined in the XDM data model.
     * It should be set only in the case of a document that is potentially retrievable via this URI.
     * This means the value will be null in the case of a temporary tree constructed in the course of
     * executing a query or transformation.
     *
     * @return the SystemId, that is, the document-uri.
     */

    /*@Nullable*/
    @Override
    public String getSystemId() {
        return systemId;
    }

    /**
     * Set the base URI of the document node of the tree being constructed by this builder
     *
     * @param baseURI the base URI
     */

    public void setBaseURI(/*@Nullable*/ String baseURI) {
        this.baseURI = baseURI;
    }

    /**
     * Get the base URI of the document node of the tree being constructed by this builder
     *
     * @return the base URI
     */

    /*@Nullable*/
    public String getBaseURI() {
        return baseURI;
    }


    /////////////////////////////////////////////////////////////////////////
    // Methods setting and getting options for building the tree
    /////////////////////////////////////////////////////////////////////////

    /**
     * Set line numbering on or off
     *
     * @param lineNumbering set to true if line numbers are to be maintained for nodes in the tree being
     *                      constructed.
     */

    public void setLineNumbering(boolean lineNumbering) {
        this.lineNumbering = lineNumbering;
    }

    /**
     * Set timing option on or off
     *
     * @param on set to true to turn timing on. This causes the builder to display statistical information
     *           about the tree that is constructed. It corresponds to the command line -t option
     */

    public void setTiming(boolean on) {
        timing = on;
    }

    /**
     * Get timing option
     *
     * @return true if timing information has been requested
     */

    public boolean isTiming() {
        return timing;
    }

    @Override
    public void open() {
        if (timing && !open) {
            String sysId = getSystemId();
            if (sysId == null) {
                sysId = "(unknown systemId)";
            }
            getConfiguration().getLogger().info(
                    "Building tree for " + sysId + " using " + getClass());
            startTime = System.nanoTime();
        }
        open = true;
    }

    @Override
    public void close() throws XPathException {
        if (timing && open) {
            long endTime = System.nanoTime();
            getConfiguration().getLogger().info(
                    "Tree built in " + CommandLineOptions.showExecutionTimeNano(endTime - startTime));
            if (currentRoot instanceof TinyDocumentImpl) {
                ((TinyDocumentImpl) currentRoot).showSize();
            }
            startTime = endTime;
        }
        open = false;
    }

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     *
     * @return true if the Receiver makes any use of this information. If false, the caller
     *         may supply untyped nodes instead of supplying the type annotation
     */

    @Override
    public boolean usesTypeAnnotations() {
        return true;
    }

    /**
     * Get the current root node. This will normally be a document node, but if the root of the tree
     * is an element node, it can be an element.
     *
     * @return the root of the tree that is currently being built, or that has been most recently built
     *         using this builder
     */

    /*@Nullable*/
    public NodeInfo getCurrentRoot() {
        return currentRoot;
    }

    /**
     * Reset the builder to its initial state. The most important effect of calling this
     * method (implemented in subclasses) is to release any links to the constructed document
     * tree, allowing the memory occupied by the tree to released by the garbage collector even
     * if the Builder is still in memory. This can happen because the Builder is referenced from a
     * parser in the Configuration's parser pool.
     */

    public void reset() {
        systemId = null;
        baseURI = null;
        currentRoot = null;
        lineNumbering = false;
        started = false;
        timing = false;
        open = false;
    }

}

