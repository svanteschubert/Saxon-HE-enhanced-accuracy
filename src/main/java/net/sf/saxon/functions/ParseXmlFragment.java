////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.Version;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.Sender;
import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.PackageData;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.om.*;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.StringValue;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.transform.sax.SAXSource;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class ParseXmlFragment extends SystemFunction implements Callable {

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
    public ZeroOrOne call(XPathContext context, Sequence[] arguments) throws XPathException {
        StringValue input = (StringValue) arguments[0].head();
        return input == null ? ZeroOrOne.empty() : new ZeroOrOne(evalParseXml(input, context));
    }

    private NodeInfo evalParseXml(StringValue inputArg, XPathContext context) throws XPathException {
        NodeInfo node = null;
        final String baseURI = getStaticBaseUriString();
        ParseXml.RetentiveErrorHandler errorHandler = new ParseXml.RetentiveErrorHandler();

        int attempt = 0;
        while (attempt++ < 3) {
            try {
                Controller controller = context.getController();
                if (controller == null) {
                    throw new XPathException("parse-xml-fragment() function is not available in this environment");
                }
                Configuration configuration = controller.getConfiguration();
                final StringReader fragmentReader = new StringReader(inputArg.getStringValue());

                String skeleton = "<!DOCTYPE z [<!ENTITY e SYSTEM \"http://www.saxonica.com/parse-xml-fragment/actual.xml\">]>\n<z>&e;</z>";
                StringReader skeletonReader = new StringReader(skeleton);

                InputSource is = new InputSource(skeletonReader);
                is.setSystemId(baseURI);
                SAXSource source = new SAXSource(is);
                XMLReader reader;
                if (attempt == 1) {
                    reader = configuration.getSourceParser();
                    if (reader.getEntityResolver() != null) {
                        continue;
                        // we don't want to overwrite the existing EntityResolver; try again
                        // with a clean parser
                    }
                } else {
                    //try {
                        reader = Version.platform.loadParserForXmlFragments();//SAXParserFactoryImpl.newInstance().newSAXParser().getXMLReader();
                    /*} catch (ParserConfigurationException | SAXException e) {
                        throw XPathException.makeXPathException(e);
                    } */
                }

                source.setXMLReader(reader);
                source.setSystemId(baseURI);

                Builder b = controller.makeBuilder();
                if (b instanceof TinyBuilder) {
                    ((TinyBuilder) b).setStatistics(controller.getConfiguration().getTreeStatistics().FN_PARSE_STATISTICS);
                }
                Receiver s = b;
                ParseOptions options = new ParseOptions();
                options.setSchemaValidationMode(Validation.SKIP);
                options.setDTDValidationMode(Validation.SKIP);
                List<Boolean> safetyCheck = new ArrayList<>();
                reader.setEntityResolver((publicId, systemId) -> {
                    if ("http://www.saxonica.com/parse-xml-fragment/actual.xml".equals(systemId)) {
                        safetyCheck.add(true);
                        InputSource is1 = new InputSource(fragmentReader);
                        is1.setSystemId(baseURI);
                        return is1;
                    } else {
                        return null;
                    }
                });
                PackageData pd = getRetainedStaticContext().getPackageData();
                if (pd instanceof StylesheetPackage) {
                    options.setSpaceStrippingRule(((StylesheetPackage) pd).getSpaceStrippingRule());
                    if (((StylesheetPackage) pd).isStripsTypeAnnotations()) {
                        s = configuration.getAnnotationStripper(s);
                    }
                } else {
                    options.setSpaceStrippingRule(IgnorableSpaceStrippingRule.getInstance());
                }
                options.setErrorHandler(errorHandler);

                s.setPipelineConfiguration(b.getPipelineConfiguration());

                options.addFilter(OuterElementStripper::new);

                try {
                    Sender.send(source, s, options);
                } catch (XPathException e) {
                    // this might be because the EntityResolver wasn't called - see bug 4127
                    if (safetyCheck.isEmpty()) {
                        // This means our entity resolver wasn't called. Make one more try, using the
                        // built-in platform default parser; then give up.
                        if (attempt == 2) {
                            XPathException xe = new XPathException("The configured XML parser cannot be used by fn:parse-xml-fragment(), because it ignores the supplied EntityResolver", "FODC0006");
                            errorHandler.captureRetainedErrors(xe);
                            xe.maybeSetContext(context);
                            throw xe;
                        } else {
                            continue;
                        }
                    } else {
                        throw e;
                    }
                }

                node = b.getCurrentRoot();
                b.reset();
            } catch (XPathException err) {
                XPathException xe = new XPathException("First argument to parse-xml-fragment() is not a well-formed and namespace-well-formed XML fragment. XML parser reported: " +
                                                               err.getMessage(), "FODC0006");
                errorHandler.captureRetainedErrors(xe);
                xe.maybeSetContext(context);
                throw xe;
            }
        }
        return node;
    }

    /**
     * Filter to remove the element wrapper added to the document to satisfy the XML parser
     */

    private static class OuterElementStripper extends ProxyReceiver {

        public OuterElementStripper(Receiver next) {
            super(next);
        }

        private int level = 0;
        private boolean suppressStartContent = false;

        /**
         * Notify the start of an element
         */
        @Override
        public void startElement(NodeName elemName, SchemaType type,
                                 AttributeMap attributes, NamespaceMap namespaces,
                                 Location location, int properties) throws XPathException {
            if (level++ > 0) {
                super.startElement(elemName, type, attributes, namespaces, location, properties);
            }
        }

        /**
         * End of element
         */
        @Override
        public void endElement() throws XPathException {
            if (--level > 0) {
                super.endElement();
            }
        }
    }
}

// Copyright (c) 2012-2020 Saxonica Limited
