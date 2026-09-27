////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
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
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.resource.ActiveSAXSource;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharp;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.*;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.transform.sax.SAXSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ParseXmlFragment extends SystemFunction implements Callable {

    public static OptionsParameter makeOptionsParameter(int version) {
        OptionsParameter parseOptions = new OptionsParameter(version);
        parseOptions.addAllowedOption("base-uri", SequenceType.SINGLE_ANY_URI);
        parseOptions.addAllowedOption("strip-space", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        return parseOptions;
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
        AtomicValue input = (AtomicValue) arguments[0].head();
        if (input == null) {
            return EmptySequence.INSTANCE;
        } else {
            Map<String, GroundedValue> checkedOptions = Collections.emptyMap();
            if (getArity() >= 2) {
                MapItem options = (MapItem) arguments[1].head();
                if (options != null) {
                    checkedOptions = getDetails().optionDetails.processSuppliedOptions(options, context, 40);
                }
            }
            String baseUri = getStaticBaseUriString();
            if (checkedOptions.containsKey("base-uri")) {
                baseUri = checkedOptions.get("base-uri").getStringValue();
            }
            boolean stripSpace = false;
            if (checkedOptions.containsKey("strip-space")) {
                stripSpace = checkedOptions.get("strip-space").effectiveBooleanValue();
            }
            return evalParseXmlFragment(input, baseUri, stripSpace, context);
        }
    }



    @CSharpReplaceBody(code="return Saxon.Impl.Helpers.ParseXmlFragment.eval(inputArg, baseUri, stripSpace, context);")
    private NodeInfo evalParseXmlFragment(
            AtomicValue inputArg, String baseUri, boolean stripSpace, XPathContext context)
            throws XPathException {
        NodeInfo node = null;
        ParseXml.RetentiveErrorHandler errorHandler = new ParseXml.RetentiveErrorHandler();

        if (inputArg instanceof StringValue) {
            String inputXml = inputArg.getStringValue();
            if (!inputXml.isEmpty() && inputXml.charAt(0) == 0xFEFF) {  // Strip a leading BOM
                inputXml = inputXml.substring(1);
            }
            inputArg = new StringValue(inputXml);
        }

        int attempt = 0;
        while (attempt++ < 3) {
            try {
                Controller controller = context.getController();
                if (controller == null) {
                    throw new XPathException("parse-xml-fragment() function is not available in this environment");
                }
                Configuration configuration = controller.getConfiguration();

                String skeleton = "<!DOCTYPE z [<!ENTITY e SYSTEM \"http://www.saxonica.com/parse-xml-fragment/actual.xml\">]><z>&e;</z>";
                StringReader skeletonReader = new StringReader(skeleton);

                InputSource is = new InputSource(skeletonReader);
                is.setSystemId(baseUri);
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
                    reader = Version.platform.loadParserForXmlFragments();
                }

                ActiveSAXSource.configureParser(reader);
                source.setXMLReader(reader);
                source.setSystemId(baseUri);

                Builder b = controller.makeBuilder();
                b.setDurability(Durability.TEMPORARY);
                if (b instanceof TinyBuilder) {
                    ((TinyBuilder) b).setStatistics(controller.getConfiguration().getTreeStatistics().FN_PARSE_STATISTICS);
                }
                Receiver s = b;
                ParseOptions options = new ParseOptions()
                        .withSchemaValidationMode(Validation.SKIP)
                        .withDTDValidationMode(Validation.SKIP);
                List<Boolean> safetyCheck = new ArrayList<>();

                if (inputArg instanceof StringValue) {
                    String xml = inputArg.getStringValue();
                    reader.setEntityResolver((publicId, systemId) -> {
                        if ("http://www.saxonica.com/parse-xml-fragment/actual.xml".equals(systemId)) {
                            safetyCheck.add(true);
                            StringReader fragmentReader = new StringReader(xml);
                            InputSource is1 = new InputSource(fragmentReader);
                            is1.setSystemId(baseUri);
                            return is1;
                        } else {
                            return null;
                        }
                    });
                } else if (inputArg instanceof BinaryValue) {
                    byte[] xml = ((BinaryValue)inputArg).getBinaryValue();
                    InputStream stream = new ByteArrayInputStream(xml);
                    reader.setEntityResolver((publicId, systemId) -> {
                        if ("http://www.saxonica.com/parse-xml-fragment/actual.xml".equals(systemId)) {
                            safetyCheck.add(true);
                            InputSource is1 = new InputSource(stream);
                            is1.setSystemId(baseUri);
                            return is1;
                        } else {
                            return null;
                        }
                    });
                }
                if (stripSpace) {
                    options = options.withSpaceStrippingRule(AllElementsSpaceStrippingRule.INSTANCE);
                } else {
                    PackageData pd = getRetainedStaticContext().getPackageData();
                    if (pd instanceof StylesheetPackage) {
                        options = options.withSpaceStrippingRule(((StylesheetPackage) pd).getSpaceStrippingRule());
                        if (((StylesheetPackage) pd).isStripsTypeAnnotations()) {
                            s = configuration.getAnnotationStripper(s);
                        }
                    } else {
                        options = options.withSpaceStrippingRule(IgnorableSpaceStrippingRule.INSTANCE);
                    }
                }
                options = options.withErrorHandler(errorHandler);

                s.setPipelineConfiguration(b.getPipelineConfiguration());

                options = options.withFilter(CSharp.methodRef(OuterElementStripper::new));

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

// Copyright (c) 2012-2026 Saxonica Limited
