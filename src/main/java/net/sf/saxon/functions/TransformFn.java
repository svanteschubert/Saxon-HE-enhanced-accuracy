////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.*;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.map.HashTrieMap;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.*;
import net.sf.saxon.serialize.CharacterMap;
import net.sf.saxon.serialize.CharacterMapIndex;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.StylesheetCache;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XsltController;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.wrapper.RebasedDocument;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.*;
import org.xml.sax.InputSource;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class implements the function transform(), which is a standard function in XPath 3.1
 */
public class TransformFn extends SystemFunction implements Callable {


    private static String[] transformOptionNames30 = new String[]{
            "package-name", "package-version", "package-node", "package-location", "static-params", "global-context-item",
            "template-params", "tunnel-params", "initial-function", "function-params"
    };

    private final static String dummyBaseOutputUriScheme = "dummy";


    private boolean isTransformOptionName30(String string) {
        for (String s : transformOptionNames30) {
            if (s.equals(string)) {
                return true;
            }
        }
        return false;
    }


    public static OptionsParameter makeOptionsParameter() {
        OptionsParameter op = new OptionsParameter();
        op.addAllowedOption("xslt-version", SequenceType.SINGLE_DECIMAL);
        op.addAllowedOption("stylesheet-location", SequenceType.SINGLE_STRING);
        op.addAllowedOption("stylesheet-node", SequenceType.SINGLE_NODE);
        op.addAllowedOption("stylesheet-text", SequenceType.SINGLE_STRING);
        op.addAllowedOption("stylesheet-base-uri", SequenceType.SINGLE_STRING);
        op.addAllowedOption("base-output-uri", SequenceType.SINGLE_STRING);
        op.addAllowedOption("stylesheet-params", SequenceType.makeSequenceType(MapType.ANY_MAP_TYPE, StaticProperty.EXACTLY_ONE));
        op.addAllowedOption("source-node", SequenceType.SINGLE_NODE);
        op.addAllowedOption("source-location", SequenceType.SINGLE_STRING); // Saxon extension (feature 3619)
        op.addAllowedOption("initial-mode", SequenceType.SINGLE_QNAME);
        op.addAllowedOption("initial-match-selection", SequenceType.ANY_SEQUENCE);
        op.addAllowedOption("initial-template", SequenceType.SINGLE_QNAME);
        op.addAllowedOption("delivery-format", SequenceType.SINGLE_STRING);
        op.addAllowedOption("serialization-params", SequenceType.makeSequenceType(MapType.ANY_MAP_TYPE, StaticProperty.EXACTLY_ONE));
        op.addAllowedOption("vendor-options", SequenceType.makeSequenceType(MapType.ANY_MAP_TYPE, StaticProperty.EXACTLY_ONE));
        op.addAllowedOption("cache", SequenceType.SINGLE_BOOLEAN);
        op.addAllowedOption("package-name", SequenceType.SINGLE_STRING);
        op.addAllowedOption("package-version", SequenceType.SINGLE_STRING);
        op.addAllowedOption("package-node", SequenceType.SINGLE_NODE);
        op.addAllowedOption("package-location", SequenceType.SINGLE_STRING);
        op.addAllowedOption("static-params", SequenceType.makeSequenceType(MapType.ANY_MAP_TYPE, StaticProperty.EXACTLY_ONE));
        op.addAllowedOption("global-context-item", SequenceType.SINGLE_ITEM);
        op.addAllowedOption("template-params", SequenceType.makeSequenceType(MapType.ANY_MAP_TYPE, StaticProperty.EXACTLY_ONE));
        op.addAllowedOption("tunnel-params", SequenceType.makeSequenceType(MapType.ANY_MAP_TYPE, StaticProperty.EXACTLY_ONE));
        op.addAllowedOption("initial-function", SequenceType.SINGLE_QNAME);
        op.addAllowedOption("function-params", ArrayItemType.SINGLE_ARRAY);
        op.addAllowedOption("requested-properties", SequenceType.makeSequenceType(MapType.ANY_MAP_TYPE, StaticProperty.EXACTLY_ONE));
        op.addAllowedOption("post-process", SequenceType.makeSequenceType(
                new SpecificFunctionType(new SequenceType[]{SequenceType.SINGLE_STRING, SequenceType.ANY_SEQUENCE}, SequenceType.ANY_SEQUENCE),
                StaticProperty.EXACTLY_ONE));
                // function(xs:string, item()*) as item()*
        return op;
    }

    /**
     * Check the options supplied:
     * 1. only allow XSLT 3.0 options if using an XSLT 3.0 processor (throw an error if any are supplied and not an XSLT 3.0 processor);
     * 2. ignore any other options not in the specs;
     * 3. validate the types of the option values supplied.
     * This method is called AFTER doing the standard type-checking on options parameters; on entry the map will only contain
     * options with recognized names and type-valid values.
     */

    private void checkTransformOptions(Map<String, Sequence> options, XPathContext context, boolean isXslt30Processor) throws XPathException {
        if (options.isEmpty()) {
            throw new XPathException("No transformation options supplied", "FOXT0002");
        }

        for (String keyName : options.keySet()) {
            if (isTransformOptionName30(keyName) && !isXslt30Processor) {
                throw new XPathException("The transform option " + keyName + " is only available when using an XSLT 3.0 processor", "FOXT0002");
            }
        }
    }

    private String checkStylesheetMutualExclusion(Map<String, Sequence> map) throws XPathException {
        return exactlyOneOf(map, "stylesheet-location", "stylesheet-node", "stylesheet-text");
    }

    private String checkStylesheetMutualExclusion30(Map<String, Sequence> map) throws XPathException {
        String styleOption = exactlyOneOf(map, "stylesheet-location", "stylesheet-node", "stylesheet-text",
                                          "package-name", "package-node", "package-location");
        if (styleOption.equals("package-location")) {
            throw new XPathException("The transform option " + styleOption + " is not implemented in Saxon", "FOXT0002");
        }
        return styleOption;
    }

    private String checkInvocationMutualExclusion(Map<String, Sequence> options) throws XPathException {
        return oneOf(options, "initial-mode", "initial-template");
    }

    /**
     * Check that at most one of a set of keys is present in the map, and return the one
     * that is present.
     * @param map the map to be searched
     * @param keys the keys to look for
     * @return if one of the keys is present, return that key; otherwise return null
     * @throws XPathException if more than one of the keys is present
     */

    private String oneOf(Map<String, Sequence> map, String... keys) throws XPathException {
        String found = null;
        for (String s : keys) {
            if (map.get(s) != null) {
                if (found != null) {
                    throw new XPathException(
                            "The following transform options are mutually exclusive: " + enumerate(keys), "FOXT0002");
                } else {
                    found = s;
                }
            }
        }
        return found;
    }

    /**
     * Check that exactly one of a set of keys is present in the map, and return the one
     * that is present.
     *
     * @param map  the map to be searched
     * @param keys the keys to look for
     * @return if exactly one of the keys is present, return that key
     * @throws XPathException if none of the keys is present or if more than one of the keys is present
     */

    private String exactlyOneOf(Map<String, Sequence> map, String... keys) throws XPathException {
        String found = oneOf(map, keys);
        if (found == null) {
            throw new XPathException("One of the following transform options must be present: " + enumerate(keys));
        }
        return found;
    }

    private String enumerate(String... keys) {
        boolean first = true;
        FastStringBuffer buffer = new FastStringBuffer(256);
        for (String k : keys) {
            if (first) {
                first = false;
            } else {
                buffer.append(" | ");
            }
            buffer.append(k);
        }
        return buffer.toString();
    }

    private String checkInvocationMutualExclusion30(Map<String, Sequence> map) throws XPathException {
        return oneOf(map, "initial-mode", "initial-template", "initial-function");
    }

    private void unsuitable(String option, String value) throws XPathException {
        throw new XPathException("No XSLT processor is available with xsl:" + option + " = " + value, "FOXT0001");
    }

    private boolean asBoolean(AtomicValue value) throws XPathException {
        if (value instanceof BooleanValue) {
            return ((BooleanValue)value).getBooleanValue();
        } else if (value instanceof StringValue) {
            String s = Whitespace.normalizeWhitespace(value.getStringValue()).toString();
            if (s.equals("yes") || s.equals("true") || s.equals("1")) {
                return true;
            } else if (s.equals("no") || s.equals("false") || s.equals("0")) {
                return false;
            }
        }
        throw new XPathException("Unrecognized boolean value " + value, "FOXT0002");
    }

    private void setRequestedProperties(Map<String, Sequence> options, Processor processor) throws XPathException {
        MapItem requestedProps = (MapItem) options.get("requested-properties").head();
        AtomicIterator optionIterator = requestedProps.keys();
        while (true) {
            AtomicValue option = optionIterator.next();
            if (option != null) {
                StructuredQName optionName = ((QNameValue) option.head()).getStructuredQName();
                AtomicValue value = (AtomicValue)requestedProps.get(option).head();
                if (optionName.hasURI(NamespaceConstant.XSLT)) {
                    String localName = optionName.getLocalPart();
                    switch (localName) {
                        case "vendor-url":
                            if (!(value.getStringValue().contains("saxonica.com") || value.getStringValue().equals("Saxonica"))) {
                                unsuitable("vendor-url", value.getStringValue());
                            }
                            break;
                        case "product-name":
                            if (!value.getStringValue().equals("SAXON")) {
                                unsuitable("vendor-url", value.getStringValue());
                            }
                            break;
                        case "product-version":
                            if (!Version.getProductVersion().startsWith(value.getStringValue())) {
                                unsuitable("product-version", value.getStringValue());
                            }
                            break;
                        case "is-schema-aware": {
                            boolean b = asBoolean(value);
                            if (b) {
                                if (processor.getUnderlyingConfiguration().isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XSLT)) {
                                    processor.setConfigurationProperty(Feature.XSLT_SCHEMA_AWARE, true);
                                } else {
                                    unsuitable("is-schema-aware", value.getStringValue());
                                }
                            } else {
                                if (processor.getUnderlyingConfiguration().isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XSLT)) {
                                    unsuitable("is-schema-aware", value.getStringValue());
                                }
                            }
                            break;
                        }
                        case "supports-serialization": {
                            boolean b = asBoolean(value);
                            if (!b) {
                                unsuitable("supports-serialization", value.getStringValue());
                            }
                            break;
                        }
                        case "supports-backwards-compatibility": {
                            boolean b = asBoolean(value);
                            if (!b) {
                                unsuitable("supports-backwards-compatibility", value.getStringValue());
                            }
                            break;
                        }
                        case "supports-namespace-axis": {
                            boolean b = asBoolean(value);
                            if (!b) {
                                unsuitable("supports-namespace-axis", value.getStringValue());
                            }
                            break;
                        }
                        case "supports-streaming": {
                            boolean b = asBoolean(value);
                            if (b) {
                                if (!processor.getUnderlyingConfiguration().isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XSLT)) {
                                    unsuitable("supports-streaming", value.getStringValue());
                                }
                            } else {
                                if (processor.getUnderlyingConfiguration().isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XSLT)) {
                                    processor.setConfigurationProperty(Feature.STREAMABILITY, "off");
                                }
                            }
                            break;
                        }
                        case "supports-dynamic-evaluation": {
                            boolean b = asBoolean(value);
                            if (!b) {
                                processor.setConfigurationProperty(Feature.DISABLE_XSL_EVALUATE, true);
                            }
                            break;
                        }
                        case "supports-higher-order-functions": {
                            boolean b = asBoolean(value);
                            if (!b) {
                                unsuitable("supports-higher-order-functions", value.getStringValue());
                            }
                            break;
                        }
                        case "xpath-version": {
                            String v = value.getStringValue();
                            try {
                                if (Double.parseDouble(v) > 3.1) {
                                    unsuitable("xpath-version", value.getStringValue());
                                }
                            } catch (NumberFormatException nfe) {
                                unsuitable("xpath-version", value.getStringValue());
                            }
                            break;
                        }
                        case "xsd-version": {
                            String v = value.getStringValue();
                            try {
                                if (Double.parseDouble(v) > 1.1) {
                                    unsuitable("xsd-version", value.getStringValue());
                                }
                            } catch (NumberFormatException nfe) {
                                unsuitable("xsd-version", value.getStringValue());
                            }
                            break;
                        }
                    }
                }
            } else {
                break;
            }
        }
    }

    private void setStaticParams(Map<String, Sequence> options, XsltCompiler xsltCompiler, boolean allowTypedNodes) throws XPathException {
        MapItem staticParamsMap = (MapItem) options.get("static-params").head();
        AtomicIterator paramIterator = staticParamsMap.keys();
        while (true) {
            AtomicValue param = paramIterator.next();
            if (param != null) {
                if (!(param instanceof QNameValue)) {
                    throw new XPathException("Parameter names in static-params must be supplied as QNames", "FOXT0002");
                }
                QName paramName = new QName(((QNameValue) param).getStructuredQName());
                GroundedValue value = staticParamsMap.get(param);
                if (!allowTypedNodes) {
                    checkSequenceIsUntyped(value);
                }
                XdmValue paramVal = XdmValue.wrap(value);
                xsltCompiler.setParameter(paramName, paramVal);
            } else {
                break;
            }
        }
    }

    private XsltExecutable getStylesheet(Map<String, Sequence> options, XsltCompiler xsltCompiler, String styleOptionStr, XPathContext context) throws XPathException {
        Item styleOptionItem = options.get(styleOptionStr).head();
        URI stylesheetBaseUri = null;
        Sequence seq;
        if ((seq = options.get("stylesheet-base-uri")) != null) {
            StringValue styleBaseUri = (StringValue) seq.head();
            stylesheetBaseUri = URI.create(styleBaseUri.getStringValue());
            if (!stylesheetBaseUri.isAbsolute()) {
                URI staticBase = getRetainedStaticContext().getStaticBaseUri();
                stylesheetBaseUri = staticBase.resolve(styleBaseUri.getStringValue());
            }
        }
        final List<XmlProcessingError> compileErrors = new ArrayList<>();
        final ErrorReporter originalReporter = xsltCompiler.getErrorReporter();
        xsltCompiler.setErrorReporter(error -> {
            if (!error.isWarning()) {
                compileErrors.add(error);
            }
            originalReporter.report(error);
        });

        boolean cacheable = options.get("static-params") == null;
        if (options.get("cache") != null) {
            cacheable &= ((BooleanValue) options.get("cache").head()).getBooleanValue();
        }

        StylesheetCache cache = context.getController().getStylesheetCache();
        XsltExecutable executable = null;
        switch (styleOptionStr) {
            case "stylesheet-location":
                String stylesheetLocation = styleOptionItem.getStringValue();
                if (cacheable) {
                    executable = cache.getStylesheetByLocation(stylesheetLocation); // if stylesheet is already cached
                }
                if (executable == null) {
                    Source style;
                    try {
                        String base = getStaticBaseUriString();
                        style = xsltCompiler.getURIResolver().resolve(stylesheetLocation, base);
                        // returns null when stylesheetLocation is relative, and (QT3TestDriver) TestURIResolver
                        // is wrongly being used for URIResolver. Next step directs to correct URIResolver.
                        if (style == null) {
                            style = xsltCompiler.getProcessor().getUnderlyingConfiguration().getSystemURIResolver().resolve(stylesheetLocation, base);
                        }
                    } catch (TransformerException e) {
                        throw new XPathException(e);
                    }

                    try {
                        executable = xsltCompiler.compile(style);
                    } catch (SaxonApiException e) {
                        return reportCompileError(e, compileErrors);
                    }
                    if (cacheable) {
                        cache.setStylesheetByLocation(stylesheetLocation, executable);
                    }
                }
                break;
            case "stylesheet-node":
            case "package-node":
                NodeInfo stylesheetNode = (NodeInfo) styleOptionItem;

                if (stylesheetBaseUri != null && !stylesheetNode.getBaseURI().equals(stylesheetBaseUri.toASCIIString())) {

                    // If the stylesheet is supplied as a node, and the stylesheet-base-uri option is supplied, and doesn't match
                    // the base URIs of the nodes (tests fn-transform-19 and fn-transform-41), then we have a bit of a problem.
                    // We wrap the stylesheet into a new virtual tree having the desired base URI.

                    String newBaseUri = stylesheetBaseUri.toASCIIString();
                    RebasedDocument rebased = new RebasedDocument(
                            stylesheetNode.getTreeInfo(),
                            node -> newBaseUri,
                            node -> newBaseUri);

                    stylesheetNode = rebased.getRootNode();
                }

                if (cacheable) {
                    executable = cache.getStylesheetByNode(stylesheetNode); // if stylesheet is already cached
                }
                if (executable == null) {
                    Source source = stylesheetNode;
                    if (stylesheetBaseUri != null) {
                        source = AugmentedSource.makeAugmentedSource(source);
                        source.setSystemId(stylesheetBaseUri.toASCIIString());
                    }
                    try {
                        executable = xsltCompiler.compile(source);
                    } catch (SaxonApiException e) {
                        reportCompileError(e, compileErrors);
                    }
                    if (cacheable) {
                        cache.setStylesheetByNode(stylesheetNode, executable);
                    }
                }
                break;
            case "stylesheet-text":
                String stylesheetText = styleOptionItem.getStringValue();
                if (cacheable) {
                    executable = cache.getStylesheetByText(stylesheetText); // if stylesheet is already cached
                }
                if (executable == null) {
                    StringReader sr = new StringReader(stylesheetText);
                    SAXSource style = new SAXSource(new InputSource(sr));
                    if (stylesheetBaseUri != null) {
                        style.setSystemId(stylesheetBaseUri.toASCIIString());
                    }
                    try {
                        executable = xsltCompiler.compile(style);
                    } catch (SaxonApiException e) {
                        reportCompileError(e, compileErrors);
                    }
                    if (cacheable) {
                        cache.setStylesheetByText(stylesheetText, executable);
                    }
                }
                break;
            case "package-name":
                String packageName = Whitespace.trim(styleOptionItem.getStringValue());
                String packageVersion = null;
                if (options.get("package-version") != null) {
                    packageVersion = options.get("package-version").head().getStringValue();
                }
                try {
                    XsltPackage pack = xsltCompiler.obtainPackage(packageName, packageVersion);
                    if (pack == null) {
                        throw new XPathException("Cannot locate package " + packageName + " version " + packageVersion, "FOXT0002");
                    }
                    executable = pack.link(); // load the already compiled package
                } catch (SaxonApiException e) {
                    if (e.getCause() instanceof XPathException) {
                        throw (XPathException) e.getCause();
                    } else {
                        throw new XPathException(e);
                    }
                }
                break;
        }
        return executable;
    }

    private XsltExecutable reportCompileError(SaxonApiException e, List<XmlProcessingError> compileErrors) throws XPathException {
        for (XmlProcessingError te : compileErrors) {
            // This is primarily so that the right error code is reported as required by the fn:transform spec
//            if (te instanceof XPathException) {
//                if (((XPathException) te).getErrorCodeLocalPart() == null) {
//                    ((XPathException) te).setErrorCode("FOXT0002");
//                }
//                throw (XPathException)te;
//            }
            XPathException xe = XPathException.fromXmlProcessingError(te);
            xe.maybeSetErrorCode("FOXT0002");
            throw xe;
        }
        if (e.getCause() instanceof XPathException) {
            throw (XPathException) e.getCause();
        } else {
            throw new XPathException(e);
        }
    }


    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        Map<String, Sequence> options = getDetails().optionDetails.processSuppliedOptions((MapItem) arguments[0].head(), context);

        Sequence vendorOptionsValue = options.get("vendor-options");
        MapItem vendorOptions = vendorOptionsValue == null ? null : (MapItem) vendorOptionsValue.head();

        Configuration targetConfig = context.getConfiguration();
        boolean allowTypedNodes = true;
        int schemaValidation = Validation.DEFAULT;

        if (vendorOptions != null) {
            Sequence optionValue = vendorOptions.get(new QNameValue("", NamespaceConstant.SAXON, "configuration"));
            if (optionValue != null) {
                NodeInfo configFile = (NodeInfo) optionValue.head();
                targetConfig = Configuration.readConfiguration(configFile, targetConfig);
                allowTypedNodes = false;
                if (!context.getConfiguration().getBooleanProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS)) {
                    targetConfig.setBooleanProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS, false);
                }
            }
            optionValue = vendorOptions.get(new QNameValue("", NamespaceConstant.SAXON, "schema-validation"));
            if (optionValue != null) {
                String valOption = optionValue.head().getStringValue();
                schemaValidation = Validation.getCode(valOption);
            }
        }
        Processor processor = new Processor(true);
        processor.setConfigurationProperty(Feature.CONFIGURATION, targetConfig);
        boolean isXslt30Processor = true;
        checkTransformOptions(options, context, isXslt30Processor);
        boolean useXslt30Processor = isXslt30Processor;
        if (options.get("xslt-version") != null) {
            BigDecimalValue xsltVersion = (BigDecimalValue) options.get("xslt-version").head();
            if ((xsltVersion.compareTo(BigDecimalValue.THREE) >= 0 && !isXslt30Processor) || (xsltVersion.compareTo(BigDecimalValue.THREE) > 0 && isXslt30Processor)) {
                throw new XPathException("The transform option xslt-version is higher than the XSLT version supported by this processor", "FOXT0002");
            }
            useXslt30Processor = xsltVersion.compareTo(BigDecimalValue.THREE) == 0;
        }
        String principalInput = oneOf(options, "source-node", "source-location", "initial-match-selection");

        // Check the rules and restrictions for combinations of transform options
        String invocationOption;
        String invocationName = "invocation";
        String styleOption;

        invocationOption = checkInvocationMutualExclusion30(options);
        // if invocation option is not initial-function or initial-template then check for source-node
        if (invocationOption != null) {
            invocationName = invocationOption;
        }
        if (!invocationName.equals("initial-template") && !invocationName.equals("initial-function") && principalInput == null) {
            //throw new XPathException("A transform must have at least one of the following options: source-node|initial-template|initial-function", "FOXT0002");
            invocationName = "initial-template";
            options.put("initial-template", new QNameValue("", NamespaceConstant.XSLT, "initial-template"));
        }
        // if invocation option is initial-function, then check for function-params
        if (invocationName.equals("initial-function") && options.get("function-params") == null) {
            throw new XPathException("Use of the transform option initial-function requires the function parameters to be supplied using the option function-params", "FOXT0002");
        }
        // function-params should only be used if invocation option is initial-function
        if (!invocationName.equals("initial-function") && options.get("function-params") != null) {
            throw new XPathException("The transform option function-params can only be used if the option initial-function is also used", "FOXT0002");
        }
        styleOption = checkStylesheetMutualExclusion30(options);

        // Set the vendor options (configuration features) on the processor
        if (options.get("requested-properties") != null) {
            setRequestedProperties(options, processor);
        }

        XsltCompiler xsltCompiler = processor.newXsltCompiler();
        xsltCompiler.setURIResolver(context.getURIResolver());
        xsltCompiler.setJustInTimeCompilation(false);

        // Set static params on XsltCompiler before compiling stylesheet (XSLT 3.0 processing only)
        if (options.get("static-params") != null) {
            setStaticParams(options, xsltCompiler, allowTypedNodes);
        }

        XsltExecutable sheet = getStylesheet(options, xsltCompiler, styleOption, context);
        Xslt30Transformer transformer = sheet.load30();

        //Destination primaryDestination = new XdmDestination();
        String deliveryFormat = "document";
        NodeInfo sourceNode = null;
        String sourceLocation = null;
        XdmValue initialMatchSelection = null;
        QName initialTemplate = null;
        QName initialMode = null;
        String baseOutputUri = null;
        Map<QName, XdmValue> stylesheetParams = new HashMap<>();
        MapItem serializationParamsMap = null;
        StringWriter serializedResult = null;
        File serializedResultFile = null;
        XdmItem globalContextItem = null;
        Map<QName, XdmValue> templateParams = new HashMap<>();
        Map<QName, XdmValue> tunnelParams = new HashMap<>();
        QName initialFunction = null;
        XdmValue[] functionParams = null;
        Function postProcessor = null;
        String principalResultKey = "output";

        for (String name : options.keySet()) {
            Sequence value = options.get(name);
            Item head = value.head();
            switch (name) {
                case "source-node":
                    sourceNode = (NodeInfo) head;
                    if (!allowTypedNodes) {
                        checkSequenceIsUntyped(sourceNode);
                    }
                    break;
                case "source-location":
                    sourceLocation = head.getStringValue();
                    break;
                case "initial-template":
                    initialTemplate = new QName(((QNameValue) head).getStructuredQName());
                    break;
                case "initial-mode":
                    initialMode = new QName(((QNameValue) head).getStructuredQName());
                    break;
                case "initial-match-selection":
                    initialMatchSelection = XdmValue.wrap(value);
                    if (!allowTypedNodes) {
                        checkSequenceIsUntyped(value);
                    }
                    break;
                case "delivery-format":
                    deliveryFormat = head.getStringValue();
                    if (!deliveryFormat.equals("document") && !deliveryFormat.equals("serialized") && !deliveryFormat.equals("raw")) {
                        throw new XPathException("The transform option delivery-format should be one of: document|serialized|raw ", "FOXT0002");
                    }
                    break;
                case "base-output-uri":
                    baseOutputUri = head.getStringValue();
                    principalResultKey = baseOutputUri;

                    break;
                case "serialization-params":
                    serializationParamsMap = (MapItem) head;

                    break;
                case "stylesheet-params": {
                    MapItem params = (MapItem) head;
                    processParams(params, stylesheetParams, allowTypedNodes);
                    break;
                }
                case "global-context-item":
                    if (!allowTypedNodes && head instanceof NodeInfo && ((NodeInfo) head).getTreeInfo().isTyped()) {
                        throw new XPathException("Schema-validated nodes cannot be passed to fn:transform() when it runs under a different Saxon Configuration", "FOXT0002");
                    }
                    globalContextItem = (XdmItem) XdmValue.wrap(head);
                    break;
                case "template-params": {
                    MapItem params = (MapItem) head;
                    processParams(params, templateParams, allowTypedNodes);
                    break;
                }
                case "tunnel-params": {
                    MapItem params = (MapItem) head;
                    processParams(params, tunnelParams, allowTypedNodes);
                    break;
                }
                case "initial-function":
                    initialFunction = new QName(((QNameValue) head).getStructuredQName());
                    break;
                case "function-params":
                    ArrayItem functionParamsArray = (ArrayItem) head;
                    functionParams = new XdmValue[functionParamsArray.arrayLength()];
                    for (int i = 0; i < functionParams.length; i++) {
                        Sequence argVal = functionParamsArray.get(i);
                        if (!allowTypedNodes) {
                            checkSequenceIsUntyped(argVal);
                        }
                        functionParams[i] = XdmValue.wrap(argVal);
                    }
                    break;
                case "post-process":
                    postProcessor = (Function) head;
                    break;
            }
        }

        if (baseOutputUri == null) {
            baseOutputUri = getStaticBaseUriString();
        } else {
            try {
                URI base = new URI(baseOutputUri);
                if (!base.isAbsolute()) {
                    base = getRetainedStaticContext().getStaticBaseUri().resolve(baseOutputUri);
                    baseOutputUri = base.toASCIIString();
                }
            } catch (URISyntaxException err) {
                throw new XPathException("Invalid base output URI " + baseOutputUri, "FOXT0002");
            }
        }

        Deliverer deliverer = Deliverer.makeDeliverer(deliveryFormat);
        deliverer.setTransformer(transformer);
        deliverer.setBaseOutputUri(baseOutputUri);
        deliverer.setPrincipalResultKey(principalResultKey);
        deliverer.setPostProcessor(postProcessor, context);

        XsltController controller = transformer.getUnderlyingController();
        controller.setResultDocumentResolver(deliverer);

        Destination destination = deliverer.getPrimaryDestination(serializationParamsMap);
        Sequence result;
        try {
            transformer.setStylesheetParameters(stylesheetParams);
            transformer.setBaseOutputURI(baseOutputUri);
            transformer.setInitialTemplateParameters(templateParams, false);
            transformer.setInitialTemplateParameters(tunnelParams, true);

            if (schemaValidation == Validation.STRICT || schemaValidation == Validation.LAX) {
                if (sourceNode != null) {
                    sourceNode = validate(sourceNode, targetConfig, schemaValidation);
                } else if (sourceLocation != null) {
                    try {
                        String base = getStaticBaseUriString();
                        Source ss = xsltCompiler.getURIResolver().resolve(sourceLocation, base);
                        if (ss == null) {
                            ss = targetConfig.getURIResolver().resolve(sourceLocation, base);
                            if (ss == null) {
                                throw new XPathException("Cannot locate document at sourceLocation " + sourceLocation, "FOXT0003");
                            }
                        }
                        ParseOptions parseOptions = new ParseOptions(targetConfig.getParseOptions());
                        parseOptions.setSchemaValidationMode(schemaValidation);
                        TreeInfo tree = targetConfig.buildDocumentTree(ss, parseOptions);
                        sourceNode = tree.getRootNode();
                        sourceLocation = null;
                    } catch (TransformerException e) {
                        throw XPathException.makeXPathException(e);
                    }
                }
                if (globalContextItem instanceof XdmNode) {
                    NodeInfo v = validate(((XdmNode)globalContextItem).getUnderlyingNode(), targetConfig, schemaValidation);
                    globalContextItem = (XdmNode)XdmValue.wrap(v);
                }
            }

            if (sourceNode != null && globalContextItem == null) {
                transformer.setGlobalContextItem(new XdmNode(sourceNode.getRoot()));
            }
            if (globalContextItem != null) {
                transformer.setGlobalContextItem(globalContextItem);
            }
            if (initialTemplate != null) {
                transformer.callTemplate(initialTemplate, destination);
                result = deliverer.getPrimaryResult();
            } else if (initialFunction != null) {
                transformer.callFunction(initialFunction, functionParams, destination);
                result = deliverer.getPrimaryResult();
            } else {
                if (initialMode != null) {
                    transformer.setInitialMode(initialMode);
                }
                if (initialMatchSelection == null && sourceNode != null) {
                    initialMatchSelection = XdmValue.wrap(sourceNode);
                }
                if (initialMatchSelection == null && sourceLocation != null) {
                    StreamSource stream = new StreamSource(sourceLocation);
                    if (transformer.getUnderlyingController().getInitialMode().isDeclaredStreamable()) {
                        transformer.applyTemplates(stream, destination);
                    } else {
                        transformer.transform(stream,destination);
                    }
                    result = deliverer.getPrimaryResult();
                } else {
                    transformer.applyTemplates(initialMatchSelection, destination);
                    result = deliverer.getPrimaryResult();
                }
            }
        } catch (SaxonApiException e) {
            XPathException e2;
            if (e.getCause() instanceof XPathException) {
                e2 = (XPathException) e.getCause();
                e2.setIsGlobalError(false);
                throw e2;
            } else {
                throw new XPathException(e);
            }
        }

        // Build map of secondary results

        HashTrieMap resultMap = new HashTrieMap();
        resultMap = deliverer.populateResultMap(resultMap);

        // Add primary result

        if (result != null) {
            AtomicValue resultKey = new StringValue(principalResultKey);
            resultMap = resultMap.addEntry(resultKey, result.materialize());
        }
        return resultMap;

    }

    /**
     * Process options such as stylesheet-params, static-params, etc
     * @param suppliedParams an XDM map from QNames to arbitrary values
     * @param checkedParams a Java map which on return will contain the same data, but as a Java map,
     *                      and using s9api representations of the parameter names and values
     * @param allowTypedNodes true if the value of the parameter is allowed to contain schema-validated
     *                        nodes
     * @throws XPathException for example if a parameter is supplied as a string rather than as a QName
     */

    private void processParams(MapItem suppliedParams, Map<QName, XdmValue> checkedParams, boolean allowTypedNodes) throws XPathException {
        AtomicIterator paramIterator = suppliedParams.keys();
        while (true) {
            AtomicValue param = paramIterator.next();
            if (param != null) {
                if (!(param instanceof QNameValue)) {
                    throw new XPathException("The names of parameters must be supplied as QNames", "FOXT0002");
                }
                QName paramName = new QName(((QNameValue) param).getStructuredQName());
                Sequence value = suppliedParams.get(param);
                if (!allowTypedNodes) {
                    checkSequenceIsUntyped(value);
                }
                XdmValue paramVal = XdmValue.wrap(value);
                checkedParams.put(paramName, paramVal);
            } else {
                break;
            }
        }
    }

    private void checkSequenceIsUntyped(Sequence value) throws XPathException {
        SequenceIterator iter = value.iterate();
        Item item;
        while ((item = iter.next()) != null) {
            if (item instanceof NodeInfo && ((NodeInfo) item).getTreeInfo().isTyped()) {
                throw new XPathException("Schema-validated nodes cannot be passed to fn:transform() when it runs under a different Saxon Configuration", "FOXT0002");
            }
        }
    }

    private static NodeInfo validate(NodeInfo node, Configuration config, int validation) throws XPathException {
        ParseOptions options = new ParseOptions(config.getParseOptions());
        options.setSchemaValidationMode(validation);
        return config.buildDocumentTree(node, options).getRootNode();
    }

    /**
     * Deliverer is an abstraction of the common functionality of the various delivery formats
     */

    private static abstract class Deliverer implements ResultDocumentResolver {

        protected Xslt30Transformer transformer;
        protected String baseOutputUri;
        protected String principalResultKey;
        protected Function postProcessor;
        protected XPathContext context;
        protected HashTrieMap resultMap = new HashTrieMap();

        public static Deliverer makeDeliverer(String deliveryFormat) {
            switch (deliveryFormat) {
                case "document":
                    return new DocumentDeliverer();
                case "serialized":
                    return new SerializedDeliverer();
                case "raw":
                    return new RawDeliverer();
                default:
                    throw new IllegalArgumentException("delivery-format");
            }
        }

        public final void setTransformer(Xslt30Transformer transformer) {
            this.transformer = transformer;
        }

        public final void setPrincipalResultKey(String key) {
            this.principalResultKey = key;
        }

        public final void setBaseOutputUri(String uri) {
            this.baseOutputUri = uri;
        }

        public void setPostProcessor(Function postProcessor, XPathContext context) {
            this.postProcessor = postProcessor;
            this.context = context;
        }

        protected URI getAbsoluteUri(String href, String baseUri) throws XPathException {
            URI absolute;
            try {
                absolute = ResolveURI.makeAbsolute(href, baseUri);
            } catch (URISyntaxException e) {
                throw XPathException.makeXPathException(e);
            }
            return absolute;
        }

        /**
         * Return a map containing information about all the secondary result documents
         *
         * @param resultMap a map to be populated, initially empty
         * @return a map containing one entry for each secondary result document that has been written
         * @throws XPathException if a failure occurs
         */

        public abstract HashTrieMap populateResultMap(HashTrieMap resultMap) throws XPathException;

        /**
         * Get the s9api Destination object to be used for the transformation
         *
         * @param serializationParamsMap the serialization parameters requested
         * @return a suitable primaryDestination object, or null in the case of raw mode
         * @throws XPathException if a failure occurs
         */

        public abstract Destination getPrimaryDestination(MapItem serializationParamsMap) throws XPathException;

        /**
         * Common code shared by subclasses to create a serializer
         *
         * @param serializationParamsMap the serialization options
         * @return a suitable Serializer
         */

        protected Serializer makeSerializer(MapItem serializationParamsMap) throws XPathException {
            Serializer serializer = transformer.newSerializer();
            if (serializationParamsMap != null) {
                AtomicIterator paramIterator = serializationParamsMap.keys();
                AtomicValue param;
                while ((param = paramIterator.next()) != null) {
                    // See bug 29440/29443. For the time being, accept both the old and new forms of serialization params
                    QName paramName;
                    if (param instanceof StringValue) {
                        paramName = new QName(param.getStringValue());
                    } else if (param instanceof QNameValue) {
                        paramName = new QName(((QNameValue) param.head()).getStructuredQName());
                    } else {
                        throw new XPathException("Serialization parameters must be strings or QNames", "XPTY0004");
                    }
                    String paramValue = null;
                    GroundedValue supplied = serializationParamsMap.get(param);
                    if (supplied.getLength() > 0) {
                        if (supplied.getLength() == 1) {
                            Item val = supplied.itemAt(0);
                            if (val instanceof StringValue) {
                                paramValue = val.getStringValue();
                            } else if (val instanceof BooleanValue) {
                                paramValue = ((BooleanValue) val).getBooleanValue() ? "yes" : "no";
                            } else if (val instanceof DecimalValue) {
                                paramValue = val.getStringValue();
                            } else if (val instanceof QNameValue) {
                                paramValue = ((QNameValue)val).getStructuredQName().getEQName();
                            } else if (val instanceof MapItem && paramName.getClarkName().equals(SaxonOutputKeys.USE_CHARACTER_MAPS)) {
                                CharacterMap charMap = Serialize.toCharacterMap((MapItem)val);
                                CharacterMapIndex charMapIndex = new CharacterMapIndex();
                                charMapIndex.putCharacterMap(charMap.getName(), charMap);
                                serializer.setCharacterMap(charMapIndex);
                                String existing = serializer.getOutputProperty(Serializer.Property.USE_CHARACTER_MAPS);
                                if (existing == null) {
                                    serializer.setOutputProperty(Serializer.Property.USE_CHARACTER_MAPS,
                                                                 charMap.getName().getEQName());
                                } else {
                                    serializer.setOutputProperty(Serializer.Property.USE_CHARACTER_MAPS,
                                                                 existing + " " + charMap.getName().getEQName());
                                }
                                continue;
                            }
                        }
                        if (paramValue == null) {
                            // if more than one, the only possibility is a sequence of QNames
                            SequenceIterator iter = supplied.iterate();
                            Item it;
                            paramValue = "";
                            while ((it = iter.next()) != null) {
                                if (it instanceof QNameValue) {
                                    paramValue += " " + ((QNameValue)it).getStructuredQName().getEQName();
                                } else {
                                    throw new XPathException("Value of serialization parameter " + paramName.getEQName() + " not recognized", "XPTY0004");
                                }
                            }
                        }
                        Serializer.Property prop = Serializer.getProperty(paramName);
                        if (paramName.getClarkName().equals(OutputKeys.CDATA_SECTION_ELEMENTS)
                                || paramName.getClarkName().equals(SaxonOutputKeys.SUPPRESS_INDENTATION)) {
                            String existing = serializer.getOutputProperty(paramName);
                            if (existing == null) {
                                serializer.setOutputProperty(prop, paramValue);
                            } else {
                                serializer.setOutputProperty(prop, existing + paramValue);
                            }
                        } else {
                            serializer.setOutputProperty(prop, paramValue);
                        }
                    }

                }
            }
            return serializer;
        }

        /**
         * Get the primary result of the transformation, that is, the value to be included in the
         * entry of the result map that describes the principal result tree
         *
         * @return the primary result, or null if there is no primary result (after post-processing if any)
         */

        public abstract Sequence getPrimaryResult() throws XPathException;

        /**
         * Post-process the result if required
         */

        public GroundedValue postProcess(String uri, Sequence result) throws XPathException {
            if (postProcessor != null) {
                result = postProcessor.call(context.newCleanContext(), new Sequence[]{new StringValue(uri), result});
            }
            return result.materialize();
        }
    }

    /**
     * Deliverer for delivery-format="document"
     */

    private static class DocumentDeliverer extends Deliverer {
        private Map<String, TreeInfo> results = new ConcurrentHashMap<>();
        private XdmDestination destination = new XdmDestination();

        public DocumentDeliverer() {
        }

        @Override
        public Destination getPrimaryDestination(MapItem serializationParamsMap) {
            return destination;
        }

        @Override
        public Sequence getPrimaryResult() throws XPathException {
            XdmNode node = destination.getXdmNode();
            return node == null ? null : postProcess(baseOutputUri, node.getUnderlyingNode());
        }

        @Override
        public Receiver resolve(XPathContext context, String href, String baseUri, SerializationProperties properties) throws XPathException {
            URI absolute = getAbsoluteUri(href, baseUri);
            XdmDestination destination = new XdmDestination();
            destination.setDestinationBaseURI(absolute);
            destination.onClose(() -> {
                XdmNode root = destination.getXdmNode();
                results.put(absolute.toASCIIString(), root.getUnderlyingValue().getTreeInfo());
            });
            PipelineConfiguration pipe = context.getController().makePipelineConfiguration();
            return destination.getReceiver(pipe, properties);
        }

        @Override
        public HashTrieMap populateResultMap(HashTrieMap resultMap) {
            for (Map.Entry<String, TreeInfo> entry : results.entrySet()) {
                String uri = entry.getKey();
                resultMap = resultMap.addEntry(new StringValue(uri),
                                               entry.getValue().getRootNode());
            }
            return resultMap;
        }
    }

    /**
     * Deliverer for delivery-format="serialized"
     */

    private static class SerializedDeliverer extends Deliverer {
        private Map<String, String> results = new ConcurrentHashMap<>();
        private Map<String, StringWriter> workInProgress = new ConcurrentHashMap<>();
        private StringWriter primaryWriter;

        public SerializedDeliverer() {
        }

        @Override
        public Destination getPrimaryDestination(MapItem serializationParamsMap) throws XPathException {
            Serializer serializer = makeSerializer(serializationParamsMap);
            primaryWriter = new StringWriter();
            serializer.setOutputWriter(primaryWriter);
            return serializer;
        }

        @Override
        public Sequence getPrimaryResult() throws XPathException {
            String str = primaryWriter.toString();
            if (str.isEmpty()) {
                return null;
            }
            return postProcess(baseOutputUri, new StringValue(str));
        }

        @Override
        public Receiver resolve(XPathContext context, String href, String baseUri, SerializationProperties properties) throws XPathException {
            URI absolute = getAbsoluteUri(href, baseUri);
            if (absolute.getScheme().equals(dummyBaseOutputUriScheme)) {
                throw new XPathException("The location of output documents is undefined: use the transform option base-output-uri", "FOXT0002");
            }
            StringWriter writer = new StringWriter();
            Serializer serializer = makeSerializer(null);
            serializer.setCharacterMap(properties.getCharacterMapIndex());
            serializer.setOutputWriter(writer);
            serializer.onClose(() -> results.put(absolute.toASCIIString(), writer.toString()));
            try {
                PipelineConfiguration pipe = context.getController().makePipelineConfiguration();
                Receiver out = serializer.getReceiver(pipe, properties);
                out.setSystemId(absolute.toASCIIString());
                return out;
            } catch (SaxonApiException e) {
                throw XPathException.makeXPathException(e);
            }
        }

        @Override
        public HashTrieMap populateResultMap(HashTrieMap resultMap) {
            for (Map.Entry<String, String> entry : results.entrySet()) {
                String uri = entry.getKey();
                resultMap = resultMap.addEntry(new StringValue(uri),
                                               new StringValue(entry.getValue()));
            }
            return resultMap;
        }
    }

    private static class RawDeliverer extends Deliverer {
        private Map<String, XdmValue> results = new ConcurrentHashMap<>();
        private RawDestination primaryDestination = new RawDestination();

        public RawDeliverer() {}

        @Override
        public Destination getPrimaryDestination(MapItem serializationParamsMap) {
            return primaryDestination;
        }

        @Override
        public Sequence getPrimaryResult() throws XPathException {
            Sequence actualResult = primaryDestination.getXdmValue().getUnderlyingValue();
            return postProcess(baseOutputUri, actualResult);
        }

        @Override
        public Receiver resolve(XPathContext context, String href, String baseUri, SerializationProperties properties) throws XPathException {
            URI absolute = getAbsoluteUri(href, baseUri);
            RawDestination destination = new RawDestination();
            destination.onClose(() -> results.put(absolute.toASCIIString(), destination.getXdmValue()));
            PipelineConfiguration pipe = context.getController().makePipelineConfiguration();
            return destination.getReceiver(pipe, properties);
        }

        @Override
        public HashTrieMap populateResultMap(HashTrieMap resultMap) {
            for (Map.Entry<String, XdmValue> entry : results.entrySet()) {
                String uri = entry.getKey();
                resultMap = resultMap.addEntry(new StringValue(uri),
                                               entry.getValue().getUnderlyingValue());
            }
            return resultMap;
        }
    }

}
