////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.event.*;
import net.sf.saxon.expr.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.expr.sort.DocumentOrderIterator;
import net.sf.saxon.expr.sort.GlobalOrderComparer;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.RelativeURIResolver;
import net.sf.saxon.lib.StandardErrorHandler;
import net.sf.saxon.om.*;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trans.*;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.value.Cardinality;

import javax.xml.transform.Source;
import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import java.net.URI;
import java.net.URISyntaxException;


/**
 * Implements the XSLT document() function
 */

public class DocumentFn extends SystemFunction implements Callable {

    private Location location;

    /**
     * Determine the static cardinality
     */

    @Override
    public int getCardinality(Expression[] arguments) {
        Expression expression = arguments[0];
        if (Cardinality.allowsMany(expression.getCardinality())) {
            return StaticProperty.ALLOWS_ZERO_OR_MORE;
        } else {
            return StaticProperty.ALLOWS_ZERO_OR_ONE;
        }
        // may have to revise this if the argument can be a list-valued element or attribute
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     * @param arguments the expressions supplied as arguments to the function call
     */

    @Override
    public int getSpecialProperties(Expression[] arguments) {
        return StaticProperty.ORDERED_NODESET |
                StaticProperty.PEER_NODESET |
                StaticProperty.NO_NODES_NEWLY_CREATED;
        // Declaring it as a peer node-set expression avoids sorting of expressions such as
        // document(XXX)/a/b/c
        // The document() function might appear to be creative: but it isn't, because multiple calls
        // with the same arguments will produce identical results.
    }

    @Override
    public Expression makeFunctionCall(Expression... arguments) {
        location = arguments[0].getLocation();
        Expression expr = Doc.maybePreEvaluate(this, arguments);
        return expr == null ? super.makeFunctionCall(arguments) : expr;
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        int numArgs = getArity();

        SequenceIterator hrefSequence = arguments[0].iterate();
        String baseURI = null;
        if (numArgs == 2) {
            // we can trust the type checking: it must be a node
            NodeInfo base = (NodeInfo) arguments[1].head();
            baseURI = base.getBaseURI();
            if (baseURI == null) {
                throw new XPathException("The second argument to document() is a node with no base URI", "XTDE1162");
            }
        }

        DocumentMappingFunction map = new DocumentMappingFunction(context);
        map.baseURI = baseURI;
        map.stylesheetURI = getStaticBaseUriString();
        map.packageData = getRetainedStaticContext().getPackageData();
        map.locator = location;

        ItemMappingIterator iter = new ItemMappingIterator(hrefSequence, map);

        //if (Cardinality.allowsMany(expression.getCardinality())) {
            return SequenceTool.toLazySequence(new DocumentOrderIterator(iter, GlobalOrderComparer.getInstance()));
            // this is to make sure we eliminate duplicates: two href's might be the same
//        } else {
//            return SequenceTool.toLazySequence(iter);
//        }
    }

    private static class DocumentMappingFunction implements ItemMappingFunction {

        public String baseURI;
        public String stylesheetURI;
        public Location locator;
        public PackageData packageData;
        public XPathContext context;

        public DocumentMappingFunction(XPathContext context) {
            this.context = context;
        }

        @Override
        public Item mapItem(Item item) throws XPathException {
            String b = baseURI;
            if (b == null) {
                if (item instanceof NodeInfo) {
                    b = ((NodeInfo) item).getBaseURI();
                } else {
                    b = stylesheetURI;
                }
            }
            return makeDoc(item.getStringValue(), b, packageData, null, context, locator, false);
        }
    }

    /**
     * Supporting routine to load one external document given a URI (href) and a baseURI. This is used
     * in the normal case when a document is loaded at run-time (that is, when a Controller is available)
     *
     *
     * @param href    the relative URI
     * @param baseURI the base URI
     * @param packageData the stylesheet (or other) package in which the call appears
     * @param options parse options to be used. May be null.
     * @param c       the dynamic XPath context
     * @param locator used to identify the location of the instruction in event of error
     * @param silent  if true, errors should not be notified to the ErrorListener
     * @return the root of the constructed document, or the selected element within the document
     *         if a fragment identifier was supplied
     * @throws XPathException if reading or parsing the document fails
     */

    public static NodeInfo makeDoc(String href, String baseURI, PackageData packageData, ParseOptions options, XPathContext c, Location locator, boolean silent)
            throws XPathException {

        Configuration config = c.getConfiguration();

        // If the href contains a fragment identifier, strip it out now
        //System.err.println("Entering makeDoc " + href);
        int hash = href.indexOf('#');

        String fragmentId = null;
        if (hash >= 0) {
            if (hash == href.length() - 1) {
                // # sign at end - just ignore it
                href = href.substring(0, hash);
            } else {
                fragmentId = href.substring(hash + 1);
                href = href.substring(0, hash);
                if (!NameChecker.isValidNCName(fragmentId)) {
                    XPathException de = new XPathException("The fragment identifier " + Err.wrap(fragmentId) + " is not a valid NCName");
                    de.setErrorCode("XTDE1160");
                    de.setXPathContext(c);
                    de.setLocator(locator);
                    throw de;
                }
            }
        }

        Controller controller = c.getController();
        if (controller == null) {
            throw new XPathException("doc() function is not available in this environment");
        }

        // Resolve relative URI
        DocumentKey documentKey = computeDocumentKey(href, baseURI, packageData, c);

        // see if the document is already loaded

        TreeInfo doc = config.getGlobalDocumentPool().find(documentKey);
        if (doc != null) {
            return doc.getRootNode();
        }

        DocumentPool pool = controller.getDocumentPool();

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (controller) {
            doc = pool.find(documentKey);
            if (doc != null) {
                return getFragment(doc, fragmentId, c, locator);
            }

            // check that the document was not written by this transformation

            if (controller instanceof XsltController &&
                    !((XsltController)controller).checkUniqueOutputDestination(documentKey)) {
                pool.markUnavailable(documentKey);
                XPathException err = new XPathException(
                        "Cannot read a document that was written during the same transformation: " + documentKey);
                err.setXPathContext(c);
                err.setErrorCode("XTRE1500");
                err.setLocator(locator);
                throw err;
            }

            if (pool.isMarkedUnavailable(documentKey)) {
                XPathException err = new XPathException(
                        "Document has been marked not available: " + documentKey);
                err.setXPathContext(c);
                err.setErrorCode("FODC0002");
                err.setLocator(locator);
                throw err;
            }
        }

        try {

            // Get a Source from the URIResolver

            Source source = resolveURI(href, baseURI, documentKey.toString(), c);


            //System.err.println("URI resolver returned " + source.getClass() + " " + source.getSystemId());
            source = config.getSourceResolver().resolveSource(source, config);
            //System.err.println("Resolved source " + source.getClass() + " " + source.getSystemId());

            TreeInfo newdoc;
            if (source instanceof NodeInfo || source instanceof DOMSource) {
                NodeInfo startNode = controller.prepareInputTree(source);
                newdoc = startNode.getTreeInfo();
            } else {
                Builder b = controller.makeBuilder();
                b.setUseEventLocation(true);
                if (b instanceof TinyBuilder) {
                    ((TinyBuilder) b).setStatistics(config.getTreeStatistics().SOURCE_DOCUMENT_STATISTICS);
                }

                Receiver s = b;
                if (options == null) {
                    options = new ParseOptions(b.getPipelineConfiguration().getParseOptions());
                    if (packageData instanceof StylesheetPackage) {
                        SpaceStrippingRule rule = ((StylesheetPackage)packageData).getSpaceStrippingRule();
                        if (rule != NoElementsSpaceStrippingRule.getInstance()) {
                            options.setSpaceStrippingRule(rule);
                        }
                    }
                    options.setSchemaValidationMode(controller.getSchemaValidationMode());
                }
                b.getPipelineConfiguration().setParseOptions(options);
                if (options.isLineNumbering()) {
                    b.setLineNumbering(true);
                }

                if (silent) {
                    StandardErrorHandler eh = new StandardErrorHandler(controller.getErrorReporter());
                    eh.setSilent(true);
                    options.setErrorHandler(eh);
                }
                if (packageData instanceof StylesheetPackage && ((StylesheetPackage)packageData).isStripsTypeAnnotations()) {
                    s = config.getAnnotationStripper(s);
                }

                PathMap map = controller.getPathMapForDocumentProjection();
                if (map != null) {
                    PathMap.PathMapRoot pathRoot = map.getRootForDocument(documentKey.toString());
                    if (pathRoot != null && !pathRoot.isReturnable() && !pathRoot.hasUnknownDependencies()) {
                        options.addFilter(config.makeDocumentProjector(pathRoot));
                    }
                }
                s.setPipelineConfiguration(b.getPipelineConfiguration());
                try {
                    Sender.send(source, s, options);
                    newdoc = b.getCurrentRoot().getTreeInfo();
                    b.reset();
                } catch (XPathException err) {
                    if (err.getErrorCodeLocalPart() == null || err.getErrorCodeLocalPart().equals("SXXP0003")) {
                        err.setErrorCode("FODC0002");
                    }
                    throw err;
                } finally {
                    if (options.isPleaseCloseAfterUse()) {
                        ParseOptions.close(source);
                    }
                }
            }

            // At this point, we have built the document. But it's possible that another thread
            // has built the same document and put it in the document pool. So we do another
            // check on the document pool, and if this has happened, we discard the document
            // we have just built and use the one from the pool instead.
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (controller) {
                doc = pool.find(documentKey);
                if (doc != null) {
                    return getFragment(doc, fragmentId, c, locator);
                }
                controller.registerDocument(newdoc, documentKey);
                if (controller instanceof XsltController) {
                    ((XsltController) controller).addUnavailableOutputDestination(documentKey);
                }
            }
            return getFragment(newdoc, fragmentId, c, locator);

        } catch (TransformerException err) {
            pool.markUnavailable(documentKey);
            XPathException xerr = XPathException.makeXPathException(err);
            xerr.maybeSetLocation(locator);
            String code = (err.getException() instanceof URISyntaxException) ? "FODC0005" : "FODC0002";
            xerr.maybeSetErrorCode(code);
            throw xerr;
        }
    }

    /**
     * Call the URIResolver to resolve a URI
     *
     * @param href        the supplied relative URI, stripped of any fragment identifier
     * @param baseURI     the base URI
     * @param documentKey the absolute URI if already available, or null otherwise
     * @param context  the dynamic context
     * @return a Source representing the document to be read
     * @throws XPathException if the relative URI cannot be resolved
     */

    public static Source resolveURI(String href, String baseURI, String documentKey, XPathContext context)
            throws XPathException {
        URIResolver resolver = context.getURIResolver();
        Source source;

        if (baseURI == null) {
            try {
                URI uri = new URI(href);
                if (!uri.isAbsolute()) {
                    throw new XPathException("Relative URI passed to document() function (" + href +
                            "); but no base URI is available", "XTDE1162");
                }
            } catch (URISyntaxException e) {
                throw new XPathException("Invalid URI passed to document() function: " + href, "FODC0005");
            }
        }

        try {
            if (resolver instanceof RelativeURIResolver && documentKey != null) {
                source = ((RelativeURIResolver) resolver).dereference(documentKey);
            } else {
                source = resolver.resolve(href, baseURI);
            }
        } catch (Exception ex) {
            //ex.printStackTrace();
            XPathException de = new XPathException("Exception thrown by URIResolver resolving `"
                                                           + href + "` against `" + baseURI + "'", ex);
            if (context.getConfiguration().getBooleanProperty(Feature.TRACE_EXTERNAL_FUNCTIONS)) {
                ex.printStackTrace();
            }
            throw de;
        }

        if (source instanceof StreamSource && ((StreamSource)source).getInputStream() == null && ((StreamSource)source).getReader() == null) {
            String uri = source.getSystemId();
            if (uri != null) {
                resolver = context.getController().getStandardURIResolver();
                try {
                    source = resolver.resolve(uri, "");
                } catch (TransformerException ex) {
                    throw XPathException.makeXPathException(ex);
                }
            } else {
                source = null; // the supplied source is useless, treat it as if the URIResolver had returned null
            }
        }
        // if a user URI resolver returns null, try the standard one
        // (Note, the standard URI resolver never returns null)
        if (source == null && !(resolver instanceof NonDelegatingURIResolver) ) {
            resolver = context.getController().getStandardURIResolver();
            try {
                if (resolver instanceof RelativeURIResolver && documentKey != null) {
                    source = ((RelativeURIResolver) resolver).dereference(documentKey);
                } else {
                    source = resolver.resolve(href, baseURI);
                }
            } catch (TransformerException ex) {
                throw XPathException.makeXPathException(ex);
            }
        }
        return source;

    }

    /**
     * Compute a document key
     */

    protected static DocumentKey computeDocumentKey(String href, String baseURI, PackageData packageData, XPathContext c) throws XPathException {
        // Resolve relative URI
        Controller controller = c.getController();

        URIResolver resolver = controller.getURIResolver();
        if (resolver == null) {
            resolver = controller.getStandardURIResolver();
        }
        return computeDocumentKey(href, baseURI, packageData, resolver, true);
    }

    /**
     * Compute a document key (an absolute URI that can be used to see if a document is already loaded)
     * @param href     the relative URI
     * @param baseURI  the base URI
     * @param packageData the package in which the call to doc() or document() appears (affects options
     *                    such as strip-space)
     * @param resolver the URIResolver
     * @param strip true if the document is subject to whitespace stripping (typically a source document), false
     *              otherwise (typically a stylesheet module)
     */

    public static DocumentKey computeDocumentKey(
            String href, String baseURI, PackageData packageData, URIResolver resolver, boolean strip) {
        String absURI;
        if (resolver instanceof RelativeURIResolver) {
            // If this is the case, the URIResolver is responsible for absolutization as well as dereferencing
            try {
                absURI = ((RelativeURIResolver) resolver).makeAbsolute(href, baseURI);
            } catch (TransformerException e) {
                absURI = '/' + href;
            }
        } else {
            // Saxon takes charge of absolutization, leaving the user URIResolver to handle dereferencing only
            href = ResolveURI.escapeSpaces(href);
            if (baseURI == null) {    // no base URI available
                try {
                    // the href might be an absolute URL
                    absURI = new URI(href).toString();
                } catch (URISyntaxException err) {
                    // it isn't; but the URI resolver might know how to cope
                    absURI = '/' + href;
                }
            } else if (href.isEmpty()) {
                // common case in XSLT, which java.net.URI#resolve() does not handle correctly
                absURI = baseURI;
            } else {
                try {
                    absURI = ResolveURI.makeAbsolute(href, baseURI).toString();
                } catch (URISyntaxException | IllegalArgumentException err) {
                    absURI = baseURI + "/../" + href;
                }
            }
        }
        if (strip && packageData instanceof StylesheetPackage &&
                ((StylesheetPackage) packageData).getSpaceStrippingRule() != NoElementsSpaceStrippingRule.getInstance()) {
            String name = ((StylesheetPackage) packageData).getPackageName();
            if (name != null) {
                return new DocumentKey(absURI, name, ((StylesheetPackage) packageData).getPackageVersion());
            }
        }
        return new DocumentKey(absURI);
    }

    /**
     * Supporting routine to load one external document given a URI (href) and a baseURI. This is used
     * when the document is pre-loaded at compile time.
     *
     * @param href    the relative URI. This must not contain a fragment identifier
     * @param baseURI the base URI
     * @param config  the Saxon configuration
     * @param locator used to identify the location of the instruction in event of error. May be null.
     * @return the root of the constructed document, or the selected element within the document
     *         if a fragment identifier was supplied
     */

    public static NodeInfo preLoadDoc(String href, String baseURI, Configuration config, SourceLocator locator)
            throws XPathException {

        int hash = href.indexOf('#');
        if (hash >= 0) {
            throw new XPathException("Fragment identifier not supported for preloaded documents");
        }

        // Resolve relative URI
        String documentKey;
        URIResolver resolver = config.getURIResolver();
        if (resolver instanceof RelativeURIResolver) {
            try {
                documentKey = ((RelativeURIResolver) resolver).makeAbsolute(href, baseURI);
            } catch (TransformerException e) {
                documentKey = '/' + href;
                baseURI = "";
            }
        } else {
            if (baseURI == null) {    // no base URI available
                try {
                    // the href might be an absolute URL
                    documentKey = new URI(href).toString();
                } catch (URISyntaxException err) {
                    // it isn't; but the URI resolver might know how to cope
                    documentKey = '/' + href;
                    baseURI = "";
                }
            } else if (href.isEmpty()) {
                // common case in XSLT, which java.net.URI#resolve() does not handle correctly
                documentKey = baseURI;
            } else {
                try {
                    documentKey = ResolveURI.makeAbsolute(href, baseURI).toString();
                } catch (URISyntaxException | IllegalArgumentException err) {
                    documentKey = baseURI + "/../" + href;
                }
            }
        }

        // see if the document is already loaded

        TreeInfo doc = config.getGlobalDocumentPool().find(documentKey);
        if (doc != null) {
            return doc.getRootNode();
        }

        try {
            // Get a Source from the URIResolver

            URIResolver r = resolver;
            Source source = null;
            if (r != null) {
                try {
                    source = r.resolve(href, baseURI);
                } catch (Exception ex) {
                    XPathException de = new XPathException("Exception thrown by URIResolver", ex);
                    if (config.getBooleanProperty(Feature.TRACE_EXTERNAL_FUNCTIONS)) {
                        ex.printStackTrace();
                    }
                    de.setLocator(locator);
                    throw de;
                }
            }

            // if a user URI resolver returns null, try the standard one
            // (Note, the standard URI resolver never returns null)
            if (source == null && !(r instanceof NonDelegatingURIResolver)) {
                r = config.getSystemURIResolver();
                source = r.resolve(href, baseURI);
            }
            //System.err.println("URI resolver returned " + source.getClass() + " " + source.getSystemId());
            source = config.getSourceResolver().resolveSource(source, config);
            //System.err.println("Resolved source " + source.getClass() + " " + source.getSystemId());

            TreeInfo newdoc = config.buildDocumentTree(source);
            config.getGlobalDocumentPool().add(newdoc, documentKey);
            return newdoc.getRootNode();

        } catch (TransformerException err) {
            XPathException xerr = XPathException.makeXPathException(err);
            xerr.setLocator(locator);
            xerr.setErrorCode("FODC0002");
            throw new XPathException(err);
        }
    }



    /**
     * Supporting routine to push one external document given a URI (href) and a baseURI to a given Receiver.
     * This method cannot handle fragment identifiers
     *
     * @param href    the relative URI
     * @param baseURL the base URI
     * @param c       the XPath dynamic context
     * @param locator used to identify the lcoation of the instruction in case of error
     * @param out     the destination where the document is to be sent
     */

    public static void sendDoc(String href, String baseURL, XPathContext c,
                               Location locator, Receiver out,
                               ParseOptions parseOptions) throws XPathException {

        PipelineConfiguration pipe = out.getPipelineConfiguration();
        if (pipe == null) {
            pipe = c.getController().makePipelineConfiguration();
            pipe.setXPathContext(c);
            out.setPipelineConfiguration(pipe);
        }

        // Resolve relative URI

        String documentKey;
        if (baseURL == null) {    // no base URI available
            try {
                // the href might be an absolute URL
                documentKey = new URI(href).toString();
            } catch (URISyntaxException err) {
                // it isn't; but the URI resolver might know how to cope
                documentKey = '/' + href;
                baseURL = "";
            }
        } else if (href.isEmpty()) {
            // common case in XSLT, which java.net.URI#resolve() does not handle correctly
            documentKey = baseURL;
        } else {
            try {
                documentKey = ResolveURI.makeAbsolute(href, baseURL).toString();
            } catch (URISyntaxException | IllegalArgumentException err) {
                documentKey = baseURL + "/../" + href;
            }
        }

        Controller controller = c.getController();

        // see if the document is already loaded

        TreeInfo doc = controller.getDocumentPool().find(documentKey);
        Source source = null;
        if (doc != null) {
            source = doc.getRootNode();
        } else {

            try {
                // Get a Source from the URIResolver

                URIResolver r = controller.getURIResolver();
                if (r != null) {
                    source = r.resolve(href, baseURL);
                }

                // if a user URI resolver returns null, try the standard one
                // (Note, the standard URI resolver never returns null)
                if (source == null) {
                    r = controller.getStandardURIResolver();
                    source = r.resolve(href, baseURL);
                }
                if (source instanceof NodeInfo || source instanceof DOMSource) {
                    NodeInfo startNode = controller.prepareInputTree(source);
                    source = startNode.getRoot();
                }
            } catch (TransformerException err) {
                XPathException xerr = XPathException.makeXPathException(err);
                xerr.setLocator(locator);
                xerr.maybeSetErrorCode("FODC0005");
                throw xerr;
            }
        }

        if (controller.getConfiguration().isTiming()) {
            controller.getConfiguration().getLogger().info("Streaming input document " + source.getSystemId());
        }
        out.setPipelineConfiguration(pipe);
        try {
            Sender.send(source, out, parseOptions);
        } catch (XPathException e) {
            e.maybeSetLocation(locator);
            e.maybeSetErrorCode("FODC0002");
            throw e;
        }
    }


    /**
     * Resolve the fragment identifier within a URI Reference.
     * Only "bare names" XPointers are recognized, that is, a fragment identifier
     * that matches an ID attribute value within the target document.
     *
     * @param doc        the document node
     * @param fragmentId the fragment identifier (an ID value within the document)
     * @param context    the XPath dynamic context
     * @return the element within the supplied document that matches the
     *         given id value; or null if no such element is found.
     */

    private static NodeInfo getFragment(TreeInfo doc, String fragmentId, XPathContext context, Location locator) {
        // TODO: we only support one kind of fragment identifier. The rules say
        // that the interpretation of the fragment identifier depends on media type,
        // but we aren't getting the media type from the URIResolver.
        if (fragmentId == null) {
            return doc.getRootNode();
        }
        if (!NameChecker.isValidNCName(fragmentId)) {
            context.getController().warning("Invalid fragment identifier in URI", "XTDE1160", locator);
            return doc.getRootNode();
        }
        return doc.selectID(fragmentId, false);
    }

}

