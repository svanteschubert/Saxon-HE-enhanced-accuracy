////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.Version;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.Sender;
import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.PackageData;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.om.*;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.tree.linked.DocumentImpl;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.ObjectValue;
import net.sf.saxon.value.SequenceExtent;
import net.sf.saxon.value.StringValue;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class ParseXml extends SystemFunction implements Callable {

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
        StringValue input = (StringValue) arguments[0].head();
        if (input == null) {
            return EmptySequence.getInstance();
        } else if (usePushParser()) {
            return parseXmlPush(input, context);
        } else {
            return parseXmlPull(input, context);
        }
    }

    @CSharpReplaceBody(code="return false;")
    private static boolean usePushParser() {
        return true;
    }

    /**
     * Evaluate the function using a push parser (SAX parser, obtained using JAXP)
     * @param inputArg the string content of the XML to be parsed
     * @param context the XPath evaluation context
     * @return the parsed document, as a tree
     * @throws XPathException if parsing fails
     */
    private NodeInfo parseXmlPush(StringValue inputArg, XPathContext context) throws XPathException {
        String baseURI = getRetainedStaticContext().getStaticBaseUriString();

        RetentiveErrorHandler errorHandler = new RetentiveErrorHandler();
        try {
            Controller controller = context.getController();
            if (controller == null) {
                throw new XPathException("parse-xml() function is not available in this environment");
            }
            Configuration config = controller.getConfiguration();

            StringReader sr = new StringReader(inputArg.getStringValue());
            InputSource is = new InputSource(sr);
            is.setSystemId(baseURI);
            Source source = new SAXSource(is);
            source.setSystemId(baseURI);

            Builder b = TreeModel.TINY_TREE.makeBuilder(controller.makePipelineConfiguration());
            Receiver s = b;
            ParseOptions options = config.getParseOptions();
            PackageData pd = getRetainedStaticContext().getPackageData();
            if (pd instanceof StylesheetPackage) {
                options = options.withSpaceStrippingRule(((StylesheetPackage) pd).getSpaceStrippingRule());
                if (((StylesheetPackage)pd).isStripsTypeAnnotations()) {
                    s = config.getAnnotationStripper(s);
                }
            } else {
                options = options.withSpaceStrippingRule(IgnorableSpaceStrippingRule.getInstance());
            }
            options = options.withErrorHandler(errorHandler);

            s.setPipelineConfiguration(b.getPipelineConfiguration());

            Sender.send(source, s, options);

            TinyDocumentImpl node = (TinyDocumentImpl) b.getCurrentRoot();
            node.setBaseURI(baseURI);
            b.reset();
            return node;
        } catch (XPathException err) {
            String msg = makeParsingErrorMessage(err);
            XPathException xe = new XPathException(msg, "FODC0006");
            errorHandler.captureRetainedErrors(xe);
            xe.maybeSetContext(context);
            throw xe;
        }
    }

    public static class RetentiveErrorHandler implements ErrorHandler {

        public List<SAXParseException> errors = new ArrayList<>();
        public boolean failed = false;

        @Override
        public void error(SAXParseException exception) {
            errors.add(exception);
        }

        @Override
        public void warning(SAXParseException exception) {
            // no action
        }

        @Override
        public void fatalError(SAXParseException exception) {
            errors.add(exception);
            failed = true;
        }

        public void captureRetainedErrors(XPathException xe) {
            List<SAXParseException> retainedErrors = errors;
            if (!retainedErrors.isEmpty()) {
                List<Item> wrappedErrors = new ArrayList<>();
                for (SAXParseException e : retainedErrors) {
                    wrappedErrors.add(new ObjectValue<SAXParseException>(e));
                }
                xe.setErrorObject(SequenceExtent.makeSequenceExtent(wrappedErrors));
            }
        }
    }

    /**
     * Evaluate the function using a pull parser (typically the .NET XmlReader)
     *
     * @param inputArg the string content of the XML to be parsed
     * @param context  the XPath evaluation context
     * @return the parsed document, as a tree
     * @throws XPathException if parsing fails
     */
    private NodeInfo parseXmlPull(StringValue inputArg, XPathContext context) throws XPathException {
        String baseURI = getRetainedStaticContext().getStaticBaseUriString();
        try {
            Controller controller = context.getController();
            if (controller == null) {
                throw new XPathException("parse-xml() function is not available in this environment");
            }
            Configuration config = context.getConfiguration();

            StringReader sr = new StringReader(inputArg.getStringValue());
            StreamSource ss = new StreamSource(sr, baseURI);
            Source pullSource = Version.platform.resolveSource(ss, config);

            Builder b = TreeModel.TINY_TREE.makeBuilder(controller.makePipelineConfiguration());
            Receiver s = b;
            ParseOptions options = config.getParseOptions();
            PackageData pd = getRetainedStaticContext().getPackageData();
            if (pd instanceof StylesheetPackage) {
                options = options.withSpaceStrippingRule(((StylesheetPackage) pd).getSpaceStrippingRule());
                if (((StylesheetPackage) pd).isStripsTypeAnnotations()) {
                    s = config.getAnnotationStripper(s);
                }
            } else {
                options = options.withSpaceStrippingRule(IgnorableSpaceStrippingRule.getInstance());
            }

            s.setPipelineConfiguration(b.getPipelineConfiguration());

            Sender.send(pullSource, s, options);

            NodeInfo root = b.getCurrentRoot();
            if (root instanceof TinyDocumentImpl) {
                TinyDocumentImpl node = (TinyDocumentImpl) root;
                node.setBaseURI(baseURI);
                node.getTreeInfo().setUserData("saxon:document-uri", "");
            } else if (root instanceof DocumentImpl) {
                DocumentImpl node = (DocumentImpl) root;
                node.setBaseURI(baseURI);
                node.getTreeInfo().setUserData("saxon:document-uri", "");
            }
            b.reset();
            return root;
        } catch (XPathException err) {
            String msg = makeParsingErrorMessage(err);
            XPathException xe = new XPathException(msg, "FODC0006");
            xe.maybeSetContext(context);
            throw xe;
        }
    }

    private String makeParsingErrorMessage(XPathException err) {
        String msg = "First argument to parse-xml() is not a well-formed and namespace-well-formed XML document. ";
        msg += err.getMessage();
        Throwable cause = err.getCause();
        if (cause != null) {
            msg += cause.getMessage();
        }
        return msg;
    }

}

// Copyright (c) 2010-2023 Saxonica Limited
