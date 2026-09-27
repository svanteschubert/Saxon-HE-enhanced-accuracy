////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.*;
import net.sf.saxon.expr.EarlyEvaluationContext;
import net.sf.saxon.expr.JPConverter;
import net.sf.saxon.lib.AugmentedSource;
import net.sf.saxon.lib.ErrorReporter;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.NoElementsSpaceStrippingRule;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.om.TreeModel;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.value.Whitespace;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.net.URI;
import java.util.Objects;

/**
 * A document builder holds properties controlling how a Saxon document tree should be built, and
 * provides methods to invoke the tree construction.
 * <p>This class has no public constructor.  To construct a {@code DocumentBuilder},
 * use the factory method {@link net.sf.saxon.s9api.Processor#newDocumentBuilder()}.</p>
 * <p>All documents used in a single Saxon query, transformation, or validation episode must
 * be built with the same {@link net.sf.saxon.Configuration}. However, there is no requirement that they
 * should use the same <code>DocumentBuilder</code>.</p>
 * <p>Sharing of a <code>DocumentBuilder</code> across multiple threads is not recommended. However,
 * in the current implementation sharing a <code>DocumentBuilder</code> (once initialized) will only
 * cause problems if a <code>SchemaValidator</code> is used.</p>
 *
 * @since 9.0
 */
@CSharpModifiers(code = {"internal"})
public class DocumentBuilder {

    private final Configuration config;
    private SchemaValidator schemaValidator;
    private boolean dtdValidation;
    private boolean lineNumbering;
    private TreeModel treeModel = TreeModel.TINY_TREE;
    private WhitespaceStrippingPolicy whitespacePolicy = WhitespaceStrippingPolicy.UNSPECIFIED;
    private URI baseURI;
    private ErrorReporter errorReporter;

    /**
     * Create a DocumentBuilder. This is a protected constructor. Users should construct a DocumentBuilder
     * by calling the factory method {@link net.sf.saxon.s9api.Processor#newDocumentBuilder()}.
     *
     * @param config the Saxon configuration
     */

    protected DocumentBuilder(Configuration config) {
        this.config = config;
    }

    /**
     * Set the tree model to be used for documents constructed using this DocumentBuilder.
     * By default, the TinyTree is used (irrespective of the TreeModel set in the underlying
     * Configuration).
     *
     * @param model typically one of the constants {@link net.sf.saxon.om.TreeModel#TINY_TREE},
     *              {@link TreeModel#TINY_TREE_CONDENSED}, or {@link TreeModel#LINKED_TREE}. It can also be
     *              an external object model such as {@link net.sf.saxon.option.xom.XOMObjectModel}
     * @since 9.2
     */

    public void setTreeModel(TreeModel model) {
        this.treeModel = model;
    }

    /**
     * Get the tree model to be used for documents constructed using this DocumentBuilder.
     * By default, the TinyTree is used (irrespective of the TreeModel set in the underlying
     * Configuration).
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
     * Supply a callback which will be notified of all errors and warnings
     * encountered during XML parsing using this {@code DocumentBuilder}.
     * <p>If no error reporter is supplied by the caller, error information
     * will be written to the standard error stream.</p>
     *
     * @param reporter a callback function which will be notified of all errors and warnings
     *                 encountered during XML parsing.
     * @since 13.0
     */

    public void setErrorReporter(ErrorReporter reporter) {
        this.errorReporter = reporter;
    }

    /**
     * Get the error reporter previously set using {@link #setErrorReporter(ErrorReporter)}
     * @return the current error reporter, or null if none has been explicitly set
     */

    public ErrorReporter getErrorReporter() {
        return this.errorReporter;
    }


    /**
     * Say whether line and column numbering and is to be enabled for documents constructed using this DocumentBuilder.
     * This has the effect that the line and column number in the original source document is maintained in the constructed
     * tree, for each element node (and only for elements). The line and column number in question are generally the position
     * at which the closing "&gt;" of the element start tag appears.
     * <p>By default, line and column numbers are not maintained.</p>
     * <p>Errors relating to document parsing and validation will generally contain line numbers whether or not
     * this option is set, because such errors are detected during document construction.</p>
     * <p>Line numbering is not available for all kinds of source: for example,
     * it is not available when loading from an existing DOM Document.</p>
     * <p>The resulting line and column numbers are accessible to applications using the
     * XPath extension functions saxon:line-number() and saxon:column-number() applied to a node, or using the
     * Java methods {@link net.sf.saxon.om.NodeInfo#getLineNumber()} and
     * {@link net.sf.saxon.om.NodeInfo#getColumnNumber()}</p>
     * <p>Line and column numbers are maintained only for element nodes; the line number
     * returned for any other node will be that of the most recent element. For an element node, the
     * line and column number are generally that of the closing angle bracket at the end of the start tag
     * (this is what a SAX parser notifies)</p>
     *
     * @param option true if line numbers are to be maintained, false otherwise.
     */

    public void setLineNumbering(boolean option) {
        lineNumbering = option;
    }

    /**
     * Ask whether line and column numbering is enabled for documents loaded using this
     * <code>DocumentBuilder</code>.
     * <p>By default, line and column numbering is disabled.</p>
     * <p>Line numbering is not available for all kinds of source: in particular,
     * it is not available when loading from an existing DOM Document.</p>
     * <p>The resulting line and column numbers are accessible to applications using the
     * extension functions saxon:line-number() and saxon:column-number applied to a node, or using the
     * Java methods {@link net.sf.saxon.om.NodeInfo#getLineNumber()} and
     * {@link net.sf.saxon.om.NodeInfo#getColumnNumber()}</p>
     * <p>Line and column numbers are maintained only for element nodes; the line number
     * returned for any other node will be that of the most recent element. For an element node, the
     * line number is generally that of the closing angle bracket at the end of the start tag
     * (this is what a SAX parser notifies)</p>
     *
     * @return true if line numbering is enabled
     */

    public boolean isLineNumbering() {
        return lineNumbering;
    }

    /**
     * Set options for schema validation. This determines whether schema validation is applied to an input
     * document and whether type annotations in a supplied document are retained. If no schemaValidator
     * is supplied, then schema validation does not take place.
     * <p>This option requires the schema-aware version of the Saxon product (Saxon-EE).</p>
     * <p>The supplied <code>SchemaValidator</code> is not actually used directly when a document is built
     * using {@link #parse(File, Destination)} or {@link #parse(Source, Destination)}
     * (the {@link SchemaValidator#validate(Source)}
     * method is never called). Rather, some of the properties of the <code>SchemaValidator</code> are used to control
     * how schema validation is performed by the <code>DocumentBuilder</code>. The particular properties
     * that take effect include:</p>
     * <ul>
     *     <li>The schema itself</li>
     *     <li>The validation mode (strict or lax)</li>
     *     <li>The required top-level element declaration (see {@link SchemaValidator#setDocumentElementName(QName)}</li>
     *     <li>The required type of the top-level element (see {@link SchemaValidator#setDocumentElementTypeName(QName)}</li>
     *     <li>The option {@link SchemaValidator#isUseXsiSchemaLocation()}</li>
     *     <li>The option {@link SchemaValidator#isExpandAttributeDefaults()}</li>
     *     <li>Validation parameters set using {@link SchemaValidator#setParameter}</li>
     *     <li>The {@link net.sf.saxon.lib.InvalidityHandler}</li>
     * </ul>
     * <p>Properties that do NOT have any effect include:</p>
     * <ul>
     *     <li>The option {@link SchemaValidator#isCollectStatistics()}</li>
     * </ul>
     *
     * @param validator the SchemaValidator to be used
     */

    public void setSchemaValidator(SchemaValidator validator) {
        schemaValidator = validator;
    }

    /**
     * Get the SchemaValidator used to validate documents loaded using this
     * <code>DocumentBuilder</code>.
     *
     * @return the SchemaValidator if one has been set; otherwise null.
     */
    public SchemaValidator getSchemaValidator() {
        return schemaValidator;
    }

    /**
     * Set whether DTD validation should be applied to documents loaded using this
     * <code>DocumentBuilder</code>.
     * <p>By default, no DTD validation takes place.</p>
     *
     * @param option true if DTD validation is to be applied to the document
     */

    public void setDTDValidation(boolean option) {
        dtdValidation = option;
    }

    /**
     * Ask whether DTD validation is to be applied to documents loaded using this <code>DocumentBuilder</code>
     *
     * @return true if DTD validation is to be applied
     */

    public boolean isDTDValidation() {
        return dtdValidation;
    }

    /**
     * Set the whitespace stripping policy applied when loading a document
     * using this <code>DocumentBuilder</code>.
     *
     * <p>If DTD or schema validation is applied, the only permitted setting
     * is {@link WhitespaceStrippingPolicy#IGNORABLE}. Any other value results
     * in an exception from the {@link #build(File)} method</p>
     *
     * @param policy the policy for stripping whitespace-only text nodes from
     *               source documents
     */

    public void setWhitespaceStrippingPolicy(WhitespaceStrippingPolicy policy) {
        whitespacePolicy = policy;
    }

    /**
     * Get the white whitespace stripping policy applied when loading a document
     * using this <code>DocumentBuilder</code>.
     *
     * @return the policy for stripping whitespace-only text nodes
     */

    public WhitespaceStrippingPolicy getWhitespaceStrippingPolicy() {
        return whitespacePolicy;
    }

    /**
     * Set the base URI of a document loaded using this <code>DocumentBuilder</code>.
     * <p>This is used for resolving any relative URIs appearing
     * within the document, for example in references to DTDs and external entities.</p>
     * <p>This information is required when the document is loaded from a source that does not
     * provide an intrinsic URI, notably when loading from a Stream or a DOMSource. The value is
     * ignored when loading from a source that does have an intrinsic base URI.</p>
     *
     * @param uri the base URI of documents loaded using this <code>DocumentBuilder</code>. This
     *            must be an absolute URI.
     * @throws IllegalArgumentException if the baseURI supplied is not an absolute URI
     */

    public void setBaseURI(URI uri) {
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException("Supplied base URI must be absolute");
        }
        baseURI = uri;
    }

    /**
     * Get the base URI of documents loaded using this DocumentBuilder when no other URI is available.
     *
     * @return the base URI to be used, or null if no value has been set.
     */

    public URI getBaseURI() {
        return baseURI;
    }

    /**
     * Set a compiled query to be used for implementing document projection. The effect of using
     * this option is that the tree constructed by the DocumentBuilder contains only those parts
     * of the source document that are needed to answer this query. Running this query against
     * the projected document should give the same results as against the raw document, but the
     * projected document typically occupies significantly less memory. It is permissible to run
     * other queries against the projected document, but unless they are carefully chosen, they
     * will give the wrong answer, because the document being used is different from the original.
     * <p>The query should be written to use the projected document as its initial context item.
     * For example, if the query is <code>//ITEM[COLOR='blue')</code>, then only <code>ITEM</code>
     * elements and their <code>COLOR</code> children will be retained in the projected document.</p>
     * <p>This facility is only available in Saxon-EE; if the facility is not available,
     * calling this method has no effect.</p>
     *
     * @param query the compiled query used to control document projection
     * @since 9.3
     * @deprecated since 13.0 - document projection is no longer supported
     */

    @Deprecated(since="13.0", forRemoval=true)
    public void setDocumentProjectionQuery(XQueryExecutable query) {
        throw new UnsupportedOperationException("Document projection was dropped in Saxon 13");
    }

    /**
     * Get the compiled query to be used for implementing document projection.
     *
     * @return the query set using {@link #setDocumentProjectionQuery} if this
     *         has been called, or null otherwise
     * @since 9.3. In 9.4 the unused and undocumented first argument is removed.
     * @deprecated since 13.0 - document projection is no longer supported
     */

    @Deprecated(since = "13.0", forRemoval = true)
    public XQueryExecutable getDocumentProjectionQuery() {
        return null;
    }

    /**
     * Load an XML document, to create a tree representation of the document in memory.
     *
     * @param source A JAXP Source object identifying the source of the document. This can always be
     *               a {@link javax.xml.transform.stream.StreamSource} or a {@link javax.xml.transform.sax.SAXSource}.
     *               Some kinds of Source are consumed by this method, and should only be used once.
     *               <p>If a SAXSource is supplied, the XMLReader held within the SAXSource may be modified (by setting
     *               features and properties) to reflect the options selected on this DocumentBuilder.</p>
     *               <p>If the source is an instance of {@link net.sf.saxon.om.NodeInfo} then the subtree rooted at this node
     *               will be copied (applying schema validation if requested) to create a new tree.</p>
     *               <p>Saxon also accepts an instance of {@link javax.xml.transform.stax.StAXSource} or
     *               {@link net.sf.saxon.pull.PullSource}, which can be used to supply a document that is to be parsed
     *               using a StAX parser.</p>
     *               <p>(9.8.0.5) This method now (once again) accepts an instance of {@link net.sf.saxon.lib.AugmentedSource}.
     *               If an {@code AugmentedSource} is supplied, the properties of the {@code AugmentedSource} take
     *               precedence over any properties set on this {@code DocumentBuilder}, which in turn take precedence
     *               over properties set at the {@link Processor} or {@link Configuration} level. The concept of
     *               "taking precedence" is explained more fully at {@link ParseOptions#merge(ParseOptions)}</p>
     *
     * @return An <code>XdmNode</code>. This will be
     *         the document node at the root of the tree of the resulting in-memory document.
     * @throws NullPointerException     if the source argument is null
     * @throws IllegalArgumentException if the kind of source is not recognized
     * @throws SaxonApiException        if any other failure occurs building the document, for example
     *                                  a parsing error
     */

    public XdmNode build(Source source) throws SaxonApiException {
        Objects.requireNonNull(source, "source");
        ParseOptions options = getParseOptions(source);
        try {
            TreeInfo doc = config.buildDocumentTree(source, options);
            return new XdmNode(doc.getRootNode());
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    private ParseOptions getParseOptions(Source source) throws SaxonApiException {
        if (!(whitespacePolicy == WhitespaceStrippingPolicy.UNSPECIFIED
                      || whitespacePolicy == WhitespaceStrippingPolicy.IGNORABLE
                      || whitespacePolicy.ordinal() == Whitespace.XSLT)) {
            if (dtdValidation) {
                throw new SaxonApiException("When DTD validation is used, the whitespace stripping policy must be IGNORABLE");
            }
            if (schemaValidator != null) {
                throw new SaxonApiException("When schema validation is used, the whitespace stripping policy must be IGNORABLE");
            }
        }
        ParseOptions options;
        if (source instanceof AugmentedSource) {
            options = ((AugmentedSource)source).getParseOptions();
            source = ((AugmentedSource)source).getContainedSource();
        } else {
            options = config.getParseOptions()
                    .withDTDValidationMode(dtdValidation ? Validation.STRICT : Validation.STRIP);
        }

        if (schemaValidator != null) {
            options = options.withSchema(schemaValidator.getSchema().getUnderlyingSchema());
            options = options.withSchemaValidationMode(schemaValidator.isLax() ? Validation.LAX : Validation.STRICT);
            if (schemaValidator.getDocumentElementName() != null) {
                QName qn = schemaValidator.getDocumentElementName();
                options = options.withTopLevelElement(qn.getStructuredQName());
            }
            if (schemaValidator.getDocumentElementType() != null) {
                options = options.withTopLevelType(schemaValidator.getDocumentElementType());
            }
            options = options.withExpandAttributeDefaults(schemaValidator.isExpandAttributeDefaults());
            options = options.withUseXsiSchemaLocation(schemaValidator.isUseXsiSchemaLocation());
            options = options.withValidationParams(schemaValidator.getValidationParameters());
            options = options.withInvalidityHandler(schemaValidator.getInvalidityHandler());
        }

        if (treeModel != null) {
            options = options.withModel(treeModel);
        }
        if (whitespacePolicy != null && whitespacePolicy != WhitespaceStrippingPolicy.UNSPECIFIED) {
            int option = whitespacePolicy.ordinal();
            if (option == Whitespace.XSLT) {
                options = options.withSpaceStrippingRule(NoElementsSpaceStrippingRule.INSTANCE);
                options = options.withFilter(whitespacePolicy.makeStripper());
            } else {
                options = options.withSpaceStrippingRule(whitespacePolicy.getSpaceStrippingRule());
            }
        }
        options = options.withLineNumbering(lineNumbering);
        if (errorReporter != null) {
            options = options.withErrorReporter(errorReporter);
        }
        if (source.getSystemId() == null && baseURI != null) {
            source.setSystemId(baseURI.toString());
        }
        if (source instanceof AugmentedSource) {
            options = options.merge(((AugmentedSource)source).getParseOptions());
        }
        return options;
    }

    /**
     * Build a document from a supplied XML file
     *
     * @param file the supplied file
     * @return the XdmNode representing the root of the document tree
     * @throws SaxonApiException if any failure occurs retrieving or parsing the document
     */

    public XdmNode build(File file) throws SaxonApiException {
        return build(new StreamSource(file));
    }

    /**
     * Get an {@link org.xml.sax.ContentHandler} that may be used to build the document programmatically.
     *
     * @return a newly constructed {@link BuildingContentHandler}, which implements the <code>ContentHandler</code>
     *         interface. If schema validation has been requested for this <code>DocumentBuilder</code>, then the document constructed
     *         using the <code>ContentHandler</code> will be validated as it is written.
     *         <p>Note that the returned <code>ContentHandler</code> expects namespace scopes to be indicated
     *         explicitly by calls to {@link org.xml.sax.ContentHandler#startPrefixMapping} and
     *         {@link org.xml.sax.ContentHandler#endPrefixMapping}.</p>
     *         <p>If the stream of events supplied to the <code>ContentHandler</code> does not constitute
     *         a well formed (and namespace-well-formed) document, the effect is undefined; Saxon may fail
     *         to detect the error, and construct an unusable tree. </p>
     * @throws SaxonApiException if any failure occurs
     * @since 9.3
     */

    public BuildingContentHandler newBuildingContentHandler() throws SaxonApiException {
        PipelineConfiguration pipe = config.makePipelineConfiguration();
        Builder builder = treeModel.makeBuilder(pipe);
        if (baseURI != null) {
            builder.setSystemId(baseURI.toASCIIString());
        }
        builder.setLineNumbering(lineNumbering);
        Receiver r = builder;
        r = new NamespaceReducer(r);
        r = injectValidator(r, builder);
        return new BuildingContentHandlerImpl(r, builder);
    }
    
    private Receiver injectValidator(Receiver r, Builder builder) throws SaxonApiException {
        if (schemaValidator != null) {
            PipelineConfiguration pipe = builder.getPipelineConfiguration();
            Receiver val = schemaValidator.getReceiver(pipe, config.obtainDefaultSerializationProperties());
            val.setPipelineConfiguration(pipe);
            if (val instanceof ProxyReceiver) {
                ((ProxyReceiver) val).setUnderlyingReceiver(r);
            }
            return val;
        }
        return r;
    }
    
    /**
     * Private implementation of BuildingContentHandler
     */

    private static class BuildingContentHandlerImpl extends ReceivingContentHandler
            implements BuildingContentHandler {

        private final Builder builder;

        public BuildingContentHandlerImpl(Receiver r, Builder b) {
            setReceiver(r);
            setPipelineConfiguration(r.getPipelineConfiguration());
            this.builder = b;
        }

        @Override
        public XdmNode getDocumentNode() {
            return new XdmNode(builder.getCurrentRoot());
        }
    }

    /**
     * Get an {@link javax.xml.stream.XMLStreamWriter} that may be used to build the document programmatically.
     *
     * @return a newly constructed {@link BuildingStreamWriter}, which implements the <code>XMLStreamWriter</code>
     *         interface. If schema validation has been requested for this <code>DocumentBuilder</code>, then the document constructed
     *         using the <code>XMLStreamWriter</code> will be validated as it is written.
     *         <p>If the stream of events supplied to the <code>XMLStreamWriter</code> does not constitute
     *         a well formed (and namespace-well-formed) document, the effect is undefined; Saxon may fail
     *         to detect the error, and construct an unusable tree. </p>
     * @throws SaxonApiException if any failure occurs
     * @since 9.3. Changed in 13 to return the interface type {@link BuildingStreamWriter} rather than
     * a specific implementation type.
     */

    public BuildingStreamWriter newBuildingStreamWriter() throws SaxonApiException {
        PipelineConfiguration pipe = config.makePipelineConfiguration();
        Builder builder = treeModel.makeBuilder(pipe);
        builder.setLineNumbering(lineNumbering);
        Receiver r = builder;
        r = new NamespaceReducer(r);
        r = injectValidator(r, builder);
        return new BuildingStreamWriterImpl(r, builder);
    }

    /**
     * Create a node by wrapping a recognized external node from a supported object model.
     * <p>If the supplied object implements the {@link net.sf.saxon.om.NodeInfo} interface then it
     * will be wrapped as an <code>XdmNode</code> without copying and without change. The <code>NodeInfo</code>
     * must have been created using a {@link net.sf.saxon.Configuration} compatible
     * with the one used by this <code>Processor</code> (specifically, one that uses the same
     * {@link net.sf.saxon.om.NamePool})</p>
     * <p>To wrap nodes from other object models, such as DOM, the support module for the external object
     * model must be on the class path and registered with the Saxon configuration. The support modules
     * for DOM, JDOM, DOM4J and XOM are registered automatically if they can be found on the classpath.</p>
     * <p>It is best to avoid calling this method repeatedly to wrap different nodes in the same document.
     * Each such wrapper conceptually creates a new XDM tree instance with its own identity. Although the
     * memory is shared, operations that rely on node identity might not have the expected result. It is
     * best to create a single wrapper for the document node, and then to navigate to the other nodes in the
     * tree using S9API interfaces.</p>
     *
     * @param node the node in the external tree representation. Either an instance of
     *             {@link net.sf.saxon.om.NodeInfo}, or an instances of a node in an external object model.
     *             Nodes in other object models (such as DOM, JDOM, etc) are recognized only if
     *             the support module for the external object model is known to the Configuration.
     * @return the supplied node wrapped as an XdmNode
     * @throws IllegalArgumentException if the type of object supplied is not recognized. This may be because
     *                                  node was created using a different Saxon Processor, or because the required code for the external
     *                                  object model is not on the class path
     */


    public XdmNode wrap(Object node) throws IllegalArgumentException {
        if (node instanceof NodeInfo) {
            NodeInfo nodeInfo = (NodeInfo) node;
            if (nodeInfo.getConfiguration().isCompatible(config)) {
                return new XdmNode(nodeInfo);
            } else {
                throw new IllegalArgumentException("Supplied NodeInfo was created using a different Configuration");
            }
        } else {
            try {
                JPConverter converter = JPConverter.allocate(node.getClass(), null, config);
                NodeInfo nodeInfo = (NodeInfo) converter.convert(node, new EarlyEvaluationContext(config));
                return XdmItem.wrapItem(nodeInfo);
            } catch (XPathException e) {
                throw new IllegalArgumentException(e.getMessage());
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("Class " + node.getClass() + " is not a recognized external node type");
            }
        }
    }


    /**
     * Parse a source document, sending it to a supplied {@link Destination}
     * <p>The process is streamed; no tree is constructed in memory.</p>
     * @param source The source document to be parsed
     * @param destination The destination to which the document is to be sent
     * @throws SaxonApiException if parsing fails, or if the destination reports an error
     */

    public void parse(Source source, Destination destination) throws SaxonApiException {
        try {
            ParseOptions options = getParseOptions(source);
            PipelineConfiguration pipe = config.makePipelineConfiguration();
            Sender.send(source, destination.getReceiver(pipe, new SerializationProperties()), options);
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Parse a source document from a File, sending it to a supplied {@link Destination}
     * <p>The process is streamed; no tree is constructed in memory.</p>
     * @param file      The file containing the XML source document to be parsed
     * @param destination The destination to which the document is to be sent
     * @throws SaxonApiException if parsing fails, or if the destination reports an error
     */

    public void parse(File file, Destination destination) throws SaxonApiException {
        parse(new StreamSource(file), destination);
    }


}

