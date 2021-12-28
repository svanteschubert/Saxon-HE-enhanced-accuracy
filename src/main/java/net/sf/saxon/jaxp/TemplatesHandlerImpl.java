////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.jaxp;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.CommentStripper;
import net.sf.saxon.event.ReceivingContentHandler;
import net.sf.saxon.event.Stripper;
import net.sf.saxon.om.StylesheetSpaceStrippingRule;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.style.Compilation;
import net.sf.saxon.style.StyleNodeFactory;
import net.sf.saxon.style.UseWhenFilter;
import net.sf.saxon.style.XSLModuleRoot;
import net.sf.saxon.trans.CompilerInfo;
import net.sf.saxon.tree.linked.DocumentImpl;
import net.sf.saxon.tree.linked.ElementImpl;
import net.sf.saxon.tree.linked.LinkedTreeBuilder;
import net.sf.saxon.value.NestedIntegerValue;
import org.xml.sax.Locator;

import javax.xml.transform.Templates;
import javax.xml.transform.sax.TemplatesHandler;


/**
 * <b>TemplatesHandlerImpl</b> implements the javax.xml.transform.sax.TemplatesHandler
 * interface. It acts as a ContentHandler which receives a stream of
 * SAX events representing a stylesheet, and returns a Templates object that
 * represents the compiled form of this stylesheet.
 *
 * @author Michael H. Kay
 */

public class TemplatesHandlerImpl extends ReceivingContentHandler implements TemplatesHandler {

    private Processor processor;
    private LinkedTreeBuilder builder;
    private StyleNodeFactory nodeFactory;
    private Templates templates;
    private String systemId;

    /**
     * Create a TemplatesHandlerImpl and initialise variables. The constructor is protected, because
     * the Filter should be created using newTemplatesHandler() in the SAXTransformerFactory
     * class
     *
     * @param processor the Saxon s9api processor
     */

    protected TemplatesHandlerImpl(Processor processor) {

        this.processor = processor;
        Configuration config = processor.getUnderlyingConfiguration();
        setPipelineConfiguration(config.makePipelineConfiguration());

        CompilerInfo info = new CompilerInfo(config.getDefaultXsltCompilerInfo());
        Compilation compilation = new Compilation(config, info);
        compilation.setMinimalPackageData();
        nodeFactory = compilation.getStyleNodeFactory(true);

        builder = new LinkedTreeBuilder(getPipelineConfiguration());
        builder.setNodeFactory(nodeFactory);
        builder.setLineNumbering(true);

        UseWhenFilter useWhenFilter = new UseWhenFilter(compilation, builder, NestedIntegerValue.TWO);
        StylesheetSpaceStrippingRule rule = new StylesheetSpaceStrippingRule(config.getNamePool());
        Stripper styleStripper = new Stripper(rule, useWhenFilter);
        CommentStripper commentStripper = new CommentStripper(styleStripper);
        setReceiver(commentStripper);

    }

    /**
     * Get the Templates object to be used for a transformation
     */

    /*@Nullable*/
    @Override
    public Templates getTemplates() {
        if (templates == null) {
            DocumentImpl doc = (DocumentImpl) builder.getCurrentRoot();
            if (doc == null) {
                return null;
            }
            ElementImpl top = doc.getDocumentElement();
            if (!(top instanceof XSLModuleRoot)) {
                throw new IllegalStateException("Input is not a stylesheet");
            }
            builder.reset();

            try {
                XsltCompiler compiler = processor.newXsltCompiler();
                templates = new TemplatesImpl(compiler.compile(doc));
            } catch (SaxonApiException tce) {
                // don't know why we aren't allowed to just throw it!
                throw new IllegalStateException(tce.getMessage());
            }
        }

        return templates;
    }

    /**
     * Set the SystemId of the document. Note that if this method is called, any locator supplied
     * to the setDocumentLocator() method is ignored. This also means that no line number information
     * will be available.
     *
     * @param url the system ID (base URI) of the stylesheet document, which will be used in any error
     *            reporting and also for resolving relative URIs in xsl:include and xsl:import. It will also form
     *            the static base URI in the static context of XPath expressions.
     */

    @Override
    public void setSystemId(String url) {
        systemId = url;
        builder.setSystemId(url);
        super.setDocumentLocator(new Locator() {
            @Override
            public int getColumnNumber() {
                return -1;
            }

            @Override
            public int getLineNumber() {
                return -1;
            }

            /*@Nullable*/
            @Override
            public String getPublicId() {
                return null;
            }

            @Override
            public String getSystemId() {
                return systemId;
            }
        });
    }

    /**
     * Callback interface for SAX: not for application use
     */

    @Override
    public void setDocumentLocator(final Locator locator) {
        // If the user has called setSystemId(), we use that system ID in preference to this one,
        // which probably comes from the XML parser possibly via some chain of SAX filters
        if (systemId == null) {
            super.setDocumentLocator(locator);
        }
    }

    /**
     * Get the systemId of the document
     */

    @Override
    public String getSystemId() {
        return systemId;
    }


}

