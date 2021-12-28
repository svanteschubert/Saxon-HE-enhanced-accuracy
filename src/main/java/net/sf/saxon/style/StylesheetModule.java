////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.event.*;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.DocumentKey;
import net.sf.saxon.om.NoElementsSpaceStrippingRule;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StylesheetSpaceStrippingRule;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.packages.IPackageLoader;
import net.sf.saxon.tree.linked.DocumentImpl;
import net.sf.saxon.tree.linked.LinkedTreeBuilder;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.NestedIntegerValue;
import net.sf.saxon.value.Whitespace;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXSource;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A stylesheet module represents a module of a stylesheet. It is possible for two modules
 * to share the same stylesheet tree in the case where two includes or imports reference
 * the same URI; in this case the two modules will typically have a different import precedence.
 */
public class StylesheetModule {

    private StyleElement rootElement;
    private int precedence;
    private int minImportPrecedence;
    private StylesheetModule importer;
    boolean wasIncluded;


    // the value of the inputTypeAnnotations attribute on this module, combined with the values
    // on all imported/included modules. This is a combination of the bit-significant values
    // ANNOTATION_STRIP and ANNOTATION_PRESERVE.
    private int inputTypeAnnotations = 0;

    // A list of all the declarations in the stylesheet and its descendants, in increasing precedence order
    protected List<ComponentDeclaration> topLevel = new ArrayList<ComponentDeclaration>();

    public StylesheetModule(StyleElement rootElement, int precedence) {
        this.rootElement = rootElement;
        this.precedence = precedence;
    }

    /**
     * Build the tree representation of a stylesheet module
     *
     *
     * @param styleSource the source of the module
     * @param topLevelModule true if this module is the outermost module of a package
     * @param compilation the XSLT compilation episode
     * @param precedence the import precedence for static variables declared
     * in the module. (This is handled differently from the precedence of other components
     * because it needs to be allocated purely sequentially).
     * @return the tree representation of the XML document containing the stylesheet module
     * @throws net.sf.saxon.trans.XPathException
     *          if XML parsing or tree
     *          construction fails
     */
    public static DocumentImpl loadStylesheetModule(
            Source styleSource, boolean topLevelModule, Compilation compilation, NestedIntegerValue precedence) throws XPathException {

        String systemId = styleSource.getSystemId();
        DocumentKey docURI = systemId == null ? null : new DocumentKey(systemId);
        if (systemId != null && compilation.getImportStack().contains(docURI)) {
            throw new XPathException("The stylesheet module includes/imports itself directly or indirectly", "XTSE0180");
        }
        compilation.getImportStack().push(docURI);

        Configuration config = compilation.getConfiguration();
        PipelineConfiguration pipe = config.makePipelineConfiguration();
        pipe.setErrorReporter(compilation.getCompilerInfo().getErrorReporter());
        LinkedTreeBuilder styleBuilder = new LinkedTreeBuilder(pipe);
        pipe.setURIResolver(compilation.getCompilerInfo().getURIResolver());
        styleBuilder.setSystemId(styleSource.getSystemId());
        //styleBuilder.freezeSystemIdAndBaseURI();
        styleBuilder.setNodeFactory(compilation.getStyleNodeFactory(topLevelModule));
        styleBuilder.setLineNumbering(true);

        UseWhenFilter useWhenFilter = new UseWhenFilter(compilation, styleBuilder, precedence);
        useWhenFilter.setSystemId(styleSource.getSystemId());
        StylesheetSpaceStrippingRule rule = new StylesheetSpaceStrippingRule(config.getNamePool());
        Stripper styleStripper = new Stripper(rule, useWhenFilter);
        CommentStripper commentStripper = new CommentStripper(styleStripper);

        // build the stylesheet document

        DocumentImpl doc;

        ParseOptions options = makeStylesheetParseOptions(styleSource, pipe);
        try {
            sendStylesheetSource(styleSource, config, commentStripper, options);
            doc = (DocumentImpl)styleBuilder.getCurrentRoot();
            styleBuilder.reset();
            compilation.getImportStack().pop();
            return doc;
        } catch (XPathException err) {
            if (topLevelModule && !err.hasBeenReported()) {   // bug 2244
                compilation.reportError(err);
            }
            throw err;
        } finally {
            if (options.isPleaseCloseAfterUse()) {
                ParseOptions.close(styleSource);
            }
        }
    }

    private static ParseOptions makeStylesheetParseOptions(Source styleSource, PipelineConfiguration pipe) {
        ParseOptions options;
        if (styleSource instanceof AugmentedSource) {
            options = ((AugmentedSource) styleSource).getParseOptions();
        } else {
            options = new ParseOptions();
        }
        options.setSchemaValidationMode(Validation.STRIP);
        options.setDTDValidationMode(Validation.STRIP);
        options.setLineNumbering(true);
        options.setSpaceStrippingRule(NoElementsSpaceStrippingRule.getInstance());
        options.setErrorReporter(pipe.getErrorReporter());
        return options;
    }


    private static void sendStylesheetSource(Source styleSource, Configuration config, Receiver sourcePipeline, ParseOptions options) throws XPathException {
        boolean knownParser =
                options.getXMLReader() != null ||
                        options.getXMLReaderMaker() != null ||
                        (styleSource instanceof SAXSource && ((SAXSource) styleSource).getXMLReader() != null);

        if (knownParser) {
            Sender.send(styleSource, sourcePipeline, options);
        } else {
            XMLReader styleParser = config.getStyleParser();
            options.setXMLReader(styleParser);
            Sender.send(styleSource, sourcePipeline, options);
            config.reuseStyleParser(styleParser);
        }
    }


    /**
     * Build the tree representation of a stylesheet module
     *
     * @param styleSource    the source of the module. This must contain either XSLT source code
     *                       of a top-level module (which may contains xsl:include, xsl:import, or
     *                       xsl:use-package), or it must contain a package in export format. This method
     *                       is not used when compiling subsidiary modules or library packages.
     * @param compilation    the XSLT compilation episode
     * @return the tree representation of the XML document containing the stylesheet module
     * @throws net.sf.saxon.trans.XPathException if XML parsing or tree
     *                                           construction fails
     */
    public static PreparedStylesheet loadStylesheet (
            Source styleSource, Compilation compilation) throws XPathException {

        if (styleSource instanceof SAXSource &&
                compilation.getConfiguration().getBooleanProperty(Feature.IGNORE_SAX_SOURCE_PARSER)) {
            // This option is provided to allow the parser set by applications such as Ant to be overridden by
            // the parser requested using FeatureKeys.SOURCE_PARSER
            ((SAXSource) styleSource).setXMLReader(null);
        }

        String systemId = styleSource.getSystemId();
        DocumentKey docURI = systemId == null ? null : new DocumentKey(systemId);
        if (systemId != null && compilation.getImportStack().contains(docURI)) {
            throw new XPathException("The stylesheet module includes/imports itself directly or indirectly", "XTSE0180");
        }
        compilation.getImportStack().push(docURI);
        compilation.setMinimalPackageData();

        Configuration config = compilation.getConfiguration();
        PipelineConfiguration pipe = config.makePipelineConfiguration();
        pipe.setErrorReporter(compilation.getCompilerInfo().getErrorReporter());

        LinkedTreeBuilder styleBuilder = new LinkedTreeBuilder(pipe);
        pipe.setURIResolver(compilation.getCompilerInfo().getURIResolver());
        styleBuilder.setSystemId(styleSource.getSystemId());
        //styleBuilder.freezeSystemIdAndBaseURI();
        styleBuilder.setNodeFactory(compilation.getStyleNodeFactory(true));
        styleBuilder.setLineNumbering(true);

        // Pipeline for source XSLT code

        Receiver sourcePipeline;

        UseWhenFilter useWhenFilter = new UseWhenFilter(compilation, styleBuilder, NestedIntegerValue.TWO);
        useWhenFilter.setSystemId(styleSource.getSystemId());

        StylesheetSpaceStrippingRule rule = new StylesheetSpaceStrippingRule(config.getNamePool());
        Stripper styleStripper = new Stripper(rule, useWhenFilter);
        CommentStripper commentStripper = new CommentStripper(styleStripper);

        // Pipeline for compiled XSLT code

        TinyBuilder packageBuilder = new TinyBuilder(pipe);
        packageBuilder.setSystemId(styleSource.getSystemId());
        CheckSumFilter checksummer = new CheckSumFilter(packageBuilder);
        checksummer.setCheckExistingChecksum(true);

        Valve valve = new Valve(NamespaceConstant.SAXON_XSLT_EXPORT, commentStripper, checksummer);
        sourcePipeline = valve;

        // build the stylesheet document

        ParseOptions options = makeStylesheetParseOptions(styleSource, pipe);
        try {
            sendStylesheetSource(styleSource, config, sourcePipeline, options);

            NodeInfo doc;
            if (valve.wasDiverted()) {
                // Implies we have loaded a pre-compiled package
                if (!checksummer.isChecksumCorrect()) {
                    throw new XPathException("Compiled package cannot be loaded: incorrect checksum");
                }
                IPackageLoader loader = config.makePackageLoader();
                StylesheetPackage pack = loader.loadPackageDoc(packageBuilder.getCurrentRoot());
                compilation.setPackageData(pack);
                PreparedStylesheet pss = new PreparedStylesheet(compilation);
                pack.checkForAbstractComponents();
                pack.updatePreparedStylesheet(pss);
                //pss.addPackage(compilation.getPackageData());
                return pss;
            } else {
                // We loaded source XSLT (could be xsl:package or xsl:stylesheet or an LRE...
                doc = styleBuilder.getCurrentRoot();
                styleBuilder.reset();
                compilation.getImportStack().pop();

                PreparedStylesheet pss = new PreparedStylesheet(compilation);
                PrincipalStylesheetModule psm = compilation.compilePackage(doc);
                if (compilation.getErrorCount() > 0) {
                    XPathException e = new XPathException("Errors were reported during stylesheet compilation");
                    e.setHasBeenReported(true); // only intended as an exception message, not something to report to ErrorListener
                    throw e;
                }
                psm.getStylesheetPackage().checkForAbstractComponents();
                psm.getStylesheetPackage().updatePreparedStylesheet(pss);
                pss.addPackage(compilation.getPackageData());
                return pss;

            }
        } catch (XPathException err) {
            if (!err.hasBeenReported()) {   // bug 2244
                compilation.reportError(err);
            }
            throw err;
        } finally {
            if (options.isPleaseCloseAfterUse()) {
                ParseOptions.close(styleSource);
            }
        }
    }


    /**
     * Get the stylesheet specification(s) associated
     * via the xml-stylesheet processing instruction (see
     * http://www.w3.org/TR/xml-stylesheet/) with the document
     * document specified in the source parameter, and that match
     * the given criteria.  Note that it is possible to return several
     * stylesheets, in which case they are applied as if they were
     * a list of imports or cascades.
     *
     * @param config  The Saxon Configuration
     * @param source  The XML source document.
     * @param media   The media attribute to be matched.  May be null, in which
     *                case the prefered templates will be used (i.e. alternate = no).
     *                Note that Saxon does not implement the complex CSS3-based syntax for
     *                media queries. By default, the media value is simply ignored. An algorithm for
     *                comparing the requested media with the declared media can be defined using
     *                the method {@link Configuration#setMediaQueryEvaluator(Comparator)}.
     * @param title   The value of the title attribute to match.  May be null.
     * @param charset The value of the charset attribute to match.  May be null.
     * @return A Source object suitable for passing to the TransformerFactory.
     * @throws net.sf.saxon.trans.XPathException
     *          if any problems occur
     */


    public static Source getAssociatedStylesheet(
            Configuration config, URIResolver resolver, Source source, String media, String title, String charset)
            throws XPathException {
        PIGrabber grabber = new PIGrabber(new Sink(config.makePipelineConfiguration()));
        grabber.setFactory(config);
        grabber.setCriteria(media, title);
        grabber.setBaseURI(source.getSystemId());
        grabber.setURIResolver(resolver);

        try {
            Sender.send(source, grabber, null);
            // this parse will be aborted when the first start tag is found
        } catch (XPathException err) {
            if (grabber.isTerminated()) {
                // do nothing
            } else {
                throw new XPathException(
                        "Failed while looking for xml-stylesheet PI", err);
            }
        }

        try {
            Source[] sources = grabber.getAssociatedStylesheets();
            if (sources == null) {
                throw new XPathException(
                        "No matching <?xml-stylesheet?> processing instruction found");
            }
            return compositeStylesheet(config, source.getSystemId(), sources);
        } catch (TransformerException err) {
            if (err instanceof XPathException) {
                throw (XPathException) err;
            } else {
                throw new XPathException(err);
            }
        }
    }

    /**
     * Process a series of stylesheet inputs, treating them in import or cascade
     * order.  This is mainly for support of the getAssociatedStylesheets
     * method, but may be useful for other purposes.
     *
     * @param config  the Saxon configuration
     * @param baseURI the base URI to be used for the synthesized composite stylesheet
     * @param sources An array of Source objects representing individual stylesheets.
     * @return A Source object representing a composite stylesheet.
     * @throws XPathException if there is a static error in the stylesheet
     */

    private static Source compositeStylesheet(Configuration config, String baseURI, Source[] sources)
            throws XPathException {

        if (sources.length == 1) {
            return sources[0];
        } else if (sources.length == 0) {
            throw new XPathException(
                    "No stylesheets were supplied");
        }

        // create a new top-level stylesheet that imports all the others

        StringBuilder sb = new StringBuilder(250);
        sb.append("<xsl:stylesheet version='1.0' ");
        sb.append(" xmlns:xsl='" + NamespaceConstant.XSLT + "'>");
        for (Source source : sources) {
            sb.append("<xsl:import href='").append(source.getSystemId()).append("'/>");
        }
        sb.append("</xsl:stylesheet>");
        InputSource composite = new InputSource();
        composite.setSystemId(baseURI);
        composite.setCharacterStream(new StringReader(sb.toString()));
        return new SAXSource(config.getSourceParser(), composite);
    }

    public void setImporter(StylesheetModule importer) {
        this.importer = importer;
    }

    public StylesheetModule getImporter() {
        return importer;
    }

    /*@NotNull*/
    public PrincipalStylesheetModule getPrincipalStylesheetModule() {
        return importer.getPrincipalStylesheetModule();
    }

    public StyleElement getRootElement() {
        return rootElement;
    }

    public XSLModuleRoot getStylesheetElement() {
        return (XSLModuleRoot) rootElement;
    }

    public Configuration getConfiguration() {
        return rootElement.getConfiguration();
    }

    public int getPrecedence() {
        return wasIncluded ? importer.getPrecedence() : precedence;
    }

    /**
     * Indicate that this stylesheet was included (by its "importer") using an xsl:include
     * statement as distinct from xsl:import
     */

    public void setWasIncluded() {
        wasIncluded = true;
    }

    /**
     * Set the minimum import precedence of this module, that is, the lowest import precedence of the modules
     * that it imports. This information is used to decide which template rules are eligible for consideration
     * by xsl:apply-imports
     *
     * @param min the minimum import precedence
     */

    public void setMinImportPrecedence(int min) {
        this.minImportPrecedence = min;
    }

    /**
     * Get the minimum import precedence of this module, that is, the lowest import precedence of the modules
     * that it imports. This information is used to decide which template rules are eligible for consideration
     * by xsl:apply-imports
     *
     * @return the minimum import precedence
     */

    public int getMinImportPrecedence() {
        return this.minImportPrecedence;
    }

    /**
     * Process xsl:include and xsl:import elements.
     *
     * @throws XPathException if the included/imported module is invalid
     */

    public void spliceIncludes() throws XPathException {

        if (topLevel == null || topLevel.size() == 0) {
            topLevel = new ArrayList<>(50);
        }
        minImportPrecedence = precedence;
        StyleElement previousElement = rootElement;

        for (NodeInfo child : getStylesheetElement().children()) {
            if (child.getNodeKind() == Type.TEXT) {
                // in an embedded stylesheet, white space nodes may still be there
                if (!Whitespace.isWhite(child.getStringValueCS())) {
                    previousElement.compileError(
                            "No character data is allowed between top-level elements", "XTSE0120");
                }

            } else if (child instanceof DataElement) {
                if (((DataElement) child).getNodeName().getURI().isEmpty()) {
                    Loc loc = new Loc(child);
                    previousElement.compileError(
                            "Top-level elements must be in a namespace: " + ((DataElement) child).getNodeName().getLocalPart() + " is not",
                            "XTSE0130", loc);
                }
            } else {
                previousElement = (StyleElement) child;
                if (child instanceof XSLGeneralIncorporate) {
                    XSLGeneralIncorporate xslinc = (XSLGeneralIncorporate) child;
                    xslinc.processAttributes();

                    // get the included stylesheet. This follows the URL, builds a tree, and splices
                    // in any indirectly-included stylesheets.

                    xslinc.validateInstruction();
                    int errors = ((XSLGeneralIncorporate) child).getCompilation().getErrorCount();
                    StylesheetModule inc =
                            xslinc.getIncludedStylesheet(this, precedence);
                    if (inc == null) {
                        return;  // error has been reported
                    }
                    errors = ((XSLGeneralIncorporate) child).getCompilation().getErrorCount() - errors;
                    if (errors > 0) {
                        xslinc.compileError("Reported " + errors + (errors == 1 ? " error" : " errors") +
                                " in " + (xslinc.isImport() ? "imported" : "included") +
                                " stylesheet module", "XTSE0165");
                    }

                    // after processing the imported stylesheet and any others it brought in,
                    // adjust the import precedence of this stylesheet if necessary

                    if (xslinc.isImport()) {
                        precedence = inc.getPrecedence() + 1;
                    } else {
                        precedence = inc.getPrecedence();
                        inc.setMinImportPrecedence(minImportPrecedence);
                        inc.setWasIncluded();
                    }

                    // copy the top-level elements of the included stylesheet into the top level of this
                    // stylesheet. Normally we add these elements at the end, in order, but if the precedence
                    // of an element is less than the precedence of the previous element, we promote it.
                    // This implements the requirement in the spec that when xsl:include is used to
                    // include a stylesheet, any xsl:import elements in the included document are moved
                    // up in the including document to after any xsl:import elements in the including
                    // document.

                    List<ComponentDeclaration> incchildren = inc.topLevel;
                    for (ComponentDeclaration decl : incchildren) {
                        int last = topLevel.size() - 1;
                        if (last < 0 || decl.getPrecedence() >= topLevel.get(last).getPrecedence()) {
                            topLevel.add(decl);
                        } else {
                            while (last >= 0 && decl.getPrecedence() < topLevel.get(last).getPrecedence()) {
                                last--;
                            }
                            topLevel.add(last + 1, decl);
                        }
                    }
                } else {
                    ComponentDeclaration decl = new ComponentDeclaration(this, (StyleElement) child);
                    topLevel.add(decl);
                }
            }
        }
    }

    /**
     * Get the value of the input-type-annotations attribute, for this module combined with that
     * of all included/imported modules. The value is an or-ed combination of the two bits
     * {@link XSLModuleRoot#ANNOTATION_STRIP} and {@link XSLModuleRoot#ANNOTATION_PRESERVE}
     *
     * @return the value of the input-type-annotations attribute, for this module combined with that
     *         of all included/imported modules
     */

    public int getInputTypeAnnotations() {
        return inputTypeAnnotations;
    }

    /**
     * Set the value of the input-type-annotations attribute, for this module combined with that
     * of all included/imported modules. The value is an or-ed combination of the two bits
     * {@link XSLModuleRoot#ANNOTATION_STRIP} and {@link XSLModuleRoot#ANNOTATION_PRESERVE}
     *
     * @param annotations the value of the input-type-annotations attribute, for this module combined with that
     *                    of all included/imported modules.
     * @throws XPathException if the values of the attribute in different modules are inconsistent
     */

    public void setInputTypeAnnotations(int annotations) throws XPathException {
        inputTypeAnnotations |= annotations;
        if (inputTypeAnnotations == (XSLModuleRoot.ANNOTATION_STRIP | XSLModuleRoot.ANNOTATION_PRESERVE)) {
            getPrincipalStylesheetModule().compileError(
                    "One stylesheet module specifies input-type-annotations='strip', " +
                            "another specifies input-type-annotations='preserve'", "XTSE0265");
        }
        if (annotations == XSLModuleRoot.ANNOTATION_STRIP) {
            getPrincipalStylesheetModule().getStylesheetPackage().setStripsTypeAnnotations(true);
        }
    }


}

