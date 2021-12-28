////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.*;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.QNameException;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.query.SequenceWrapper;
import net.sf.saxon.serialize.*;
import net.sf.saxon.stax.StAXResultHandlerImpl;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.BigDecimalValue;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stax.StAXResult;
import javax.xml.transform.stream.StreamResult;
import java.io.Writer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Helper class to construct a serialization pipeline for a given result destination
 * and a given set of output properties. The pipeline is represented by a Receiver object
 * to which result tree events are sent.
 * <p>Since Saxon 8.8 is is possible to write a subclass of SerializerFactory and register it
 * with the Configuration, allowing customisation of the Serializer pipeline.</p>
 * <p>The class includes methods for instantiating each of the components used on the Serialization
 * pipeline. This allows a customized SerializerFactory to replace any or all of these components
 * by subclasses that refine the behaviour.</p>
 */

public class SerializerFactory {

    Configuration config;
    PipelineConfiguration pipe;

    /**
     * Create a SerializerFactory
     *
     * @param config the Saxon Configuration
     */

    public SerializerFactory(Configuration config) {
        this.config = config;
    }

    public SerializerFactory(PipelineConfiguration pipe) {
        this.pipe = pipe;
        this.config = pipe.getConfiguration();
    }

    public Configuration getConfiguration() {
        return config;
    }
    /**
     * Create a serializer with given output properties, and return
     * an XMLStreamWriter that can be used to feed events to the serializer.
     *
     * @param result     the destination of the serialized output (wraps a Writer, an OutputStream, or a File)
     * @param properties the serialization properties to be used
     * @return a serializer in the form of an XMLStreamWriter
     * @throws net.sf.saxon.trans.XPathException
     *          if any error occurs
     */

    public StreamWriterToReceiver getXMLStreamWriter(
            StreamResult result,
            Properties properties) throws XPathException {
        Receiver r = getReceiver(result, new SerializationProperties(properties));
        r = new NamespaceReducer(r);
        return new StreamWriterToReceiver(r);
    }

    /**
     * Get a Receiver that wraps a given Result object. Saxon calls this method to construct
     * a serialization pipeline. The method can be overridden in a subclass; alternatively, the
     * subclass can override the various methods used to instantiate components of the serialization
     * pipeline.
     * <p>Note that this method ignores the {@link SaxonOutputKeys#WRAP} output property. If
     * wrapped output is required, the user must create a {@link net.sf.saxon.query.SequenceWrapper} directly.</p>
     * <p>The effect of the method changes in Saxon 9.7 so that for serialization methods other than
     * "json" and "adaptive", the returned Receiver performs the function of "sequence normalization" as
     * defined in the Serialization specification. Previously the client code handled this by wrapping the
     * result in a ComplexContentOutputter (usually as a side-effect of called XPathContext.changeOutputDestination()).
     * Wrapping in a ComplexContentOutputter is no longer necessary, though it does no harm because the ComplexContentOutputter
     * is idempotent.</p>
     *
     * <p>Changed in 9.9 so that no character maps are used. Previously the character maps from the Executable
     * associated with the Controller referenced from the PipelineConfiguration were used.</p>
     *
     * @param result The final destination of the serialized output. Usually a StreamResult,
     *               but other kinds of Result are possible.
     * @param pipe   The PipelineConfiguration.
     * @param props  The serialization properties. If this includes the property {@link SaxonOutputKeys#USE_CHARACTER_MAPS}
     *               then the PipelineConfiguration must contain a non-null Controller, and the Executable associated with this Controller
     *               must have a CharacterMapIndex which is used to resolve the names of the character maps appearing in this property.
     * @return the newly constructed Receiver that performs the required serialization
     * @throws net.sf.saxon.trans.XPathException
     *          if any failure occurs
     * @deprecated since Saxon 9.9: use one of the other {@code getReceiver} methods
     */

    public Receiver getReceiver(Result result,
                                PipelineConfiguration pipe,
                                Properties props)
            throws XPathException {
        return getReceiver(result, new SerializationProperties(props), pipe);
    }

    /**
     * Get a Receiver that wraps a given Result object. Saxon calls this method to construct
     * a serialization pipeline. The method can be overridden in a subclass; alternatively, the
     * subclass can override the various methods used to instantiate components of the serialization
     * pipeline.
     * <p>This version of the method calls {@link #getReceiver(Result, SerializationProperties, PipelineConfiguration)}
     * supplying default output properties, and a {@code PipelineConfiguration} newly constructed using
     * {@link Configuration#makePipelineConfiguration()}.</p>
     *
     * @param result The final destination of the serialized output. Usually a StreamResult,
     *               but other kinds of Result are possible.
     * @throws XPathException if a serializer cannot be created
     */

    public Receiver getReceiver(Result result) throws XPathException {
        return getReceiver(result, new SerializationProperties(), config.makePipelineConfiguration());
    }

    /**
     * Get a Receiver that wraps a given Result object. Saxon calls this method to construct
     * a serialization pipeline. The method can be overridden in a subclass; alternatively, the
     * subclass can override the various methods used to instantiate components of the serialization
     * pipeline.
     * <p>This version of the method calls {@link #getReceiver(Result, SerializationProperties, PipelineConfiguration)}
     * supplying a {@code PipelineConfiguration} newly constructed using {@link Configuration#makePipelineConfiguration()}.</p>
     *
     * @param result The final destination of the serialized output. Usually a StreamResult,
     *               but other kinds of Result are possible.
     * @param params The serialization properties, including character maps
     * @return the newly constructed Receiver that performs the required serialization
     * @throws XPathException if a serializer cannot be created
     */

    public Receiver getReceiver(Result result, SerializationProperties params) throws XPathException {
        return getReceiver(result, params, config.makePipelineConfiguration());
    }

    /**
     * Get a Receiver that wraps a given Result object. Saxon calls this method to construct
     * a serialization pipeline. The method can be overridden in a subclass; alternatively, the
     * subclass can override the various methods used to instantiate components of the serialization
     * pipeline.
     * <p>Note that this method ignores the {@link SaxonOutputKeys#WRAP} output property. If
     * wrapped output is required, the user must create a {@link net.sf.saxon.query.SequenceWrapper} directly.</p>
     * <p>The effect of the method changes in Saxon 9.7 so that for serialization methods other than
     * "json" and "adaptive", the returned Receiver performs the function of "sequence normalization" as
     * defined in the Serialization specification. Previously the client code handled this by wrapping the
     * result in a ComplexContentOutputter (usually as a side-effect of called XPathContext.changeOutputDestination()).
     * Wrapping in a ComplexContentOutputter is no longer necessary, though it does no harm because the ComplexContentOutputter
     * is idempotent.</p>
     *
     * @param result       The final destination of the serialized output. Usually a StreamResult,
     *                     but other kinds of Result are possible.
     * @param params       The serialization properties, including character maps
     * @param pipe         The PipelineConfiguration.
     * @return the newly constructed Receiver that performs the required serialization
     * @throws XPathException if a serializer cannot be created
     */

    public Receiver getReceiver(Result result,
                                SerializationProperties params,
                                PipelineConfiguration pipe)
            throws XPathException {

        Objects.requireNonNull(result);
        Objects.requireNonNull(params);
        Objects.requireNonNull(pipe);

        Properties props = params.getProperties();
        CharacterMapIndex charMapIndex = params.getCharacterMapIndex();
        if (charMapIndex == null) {
            charMapIndex = new CharacterMapIndex();
        }
        String nextInChain = props.getProperty(SaxonOutputKeys.NEXT_IN_CHAIN);
        if (nextInChain != null && !nextInChain.isEmpty()) {
            String href = props.getProperty(SaxonOutputKeys.NEXT_IN_CHAIN);
            String base = props.getProperty(SaxonOutputKeys.NEXT_IN_CHAIN_BASE_URI);
            if (base == null) {
                base = "";
            }
            Properties sansNext = new Properties(props);
            sansNext.setProperty(SaxonOutputKeys.NEXT_IN_CHAIN, "");
            return prepareNextStylesheet(pipe, href, base, result);
        }
        String paramDoc = props.getProperty(SaxonOutputKeys.PARAMETER_DOCUMENT);
        if (paramDoc != null && !paramDoc.isEmpty()) {
            String base = props.getProperty(SaxonOutputKeys.PARAMETER_DOCUMENT_BASE_URI);
            if (base == null) {
                base = result.getSystemId();
            }
            Properties props2 = new Properties(props);
            props2.setProperty(SaxonOutputKeys.PARAMETER_DOCUMENT, "");
            Source source;
            try {
                source = config.getURIResolver().resolve(paramDoc, base);
            } catch (TransformerException e) {
                throw XPathException.makeXPathException(e);
            }
            ParseOptions options = new ParseOptions();
            options.setSchemaValidationMode(Validation.LAX);
            options.setDTDValidationMode(Validation.SKIP);
            TreeInfo doc = config.buildDocumentTree(source);
            SerializationParamsHandler ph = new SerializationParamsHandler();
            ph.setSerializationParams(doc.getRootNode());
            Properties paramDocProps = ph.getSerializationProperties().getProperties();
            Enumeration<?> names = paramDocProps.propertyNames();
            while (names.hasMoreElements()) {
                String name = (String)names.nextElement();
                String value = paramDocProps.getProperty(name);
                props2.setProperty(name, value);
            }
            CharacterMap charMap = ph.getCharacterMap();
            if (charMap != null) {
                props2.setProperty(SaxonOutputKeys.USE_CHARACTER_MAPS, charMap.getName().getClarkName());
                charMapIndex.putCharacterMap(charMap.getName(), charMap);
            }
            props = props2;
            params = new SerializationProperties(props2, charMapIndex);
        }
        if (result instanceof StreamResult) {

            // The "target" is the start of the output pipeline, the Receiver that
            // instructions will actually write to (except that other things like a
            // NamespaceReducer may get added in front of it). The "emitter" is the
            // last thing in the output pipeline, the Receiver that actually generates
            // characters or bytes that are written to the StreamResult.

            SequenceReceiver target;
            String method = props.getProperty(OutputKeys.METHOD);
            if (method == null) {
                return newUncommittedSerializer(result, new Sink(pipe), params);
            }

            Emitter emitter = null;

            switch (method) {
                case "html": {
                    emitter = newHTMLEmitter(props);
                    emitter.setPipelineConfiguration(pipe);
                    target = createHTMLSerializer(emitter, params, pipe);
                    break;
                }
                case "xml": {
                    emitter = newXMLEmitter(props);
                    emitter.setPipelineConfiguration(pipe);
                    target = createXMLSerializer((XMLEmitter) emitter, params);
                    break;
                }
                case "xhtml": {
                    emitter = newXHTMLEmitter(props);
                    emitter.setPipelineConfiguration(pipe);
                    target = createXHTMLSerializer(emitter, params, pipe);
                    break;
                }
                case "text": {
                    emitter = newTEXTEmitter();
                    emitter.setPipelineConfiguration(pipe);
                    target = createTextSerializer(emitter, params);
                    break;
                }
                case "json": {
                    StreamResult sr = (StreamResult) result;
                    props.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                    JSONEmitter je = new JSONEmitter(pipe, sr, props);
                    JSONSerializer js = new JSONSerializer(pipe, je, props);
                    String sortOrder = props.getProperty(SaxonOutputKeys.PROPERTY_ORDER);
                    if (sortOrder != null) {
                        js.setPropertySorter(getPropertySorter(sortOrder));
                    }
                    CharacterMapExpander characterMapExpander = makeCharacterMapExpander(pipe, props, charMapIndex);
                    ProxyReceiver normalizer = makeUnicodeNormalizer(pipe, props);
                    return customizeJSONSerializer(js, props, characterMapExpander, normalizer);

                }
                case "adaptive": {
                    ExpandedStreamResult esr = new ExpandedStreamResult(pipe.getConfiguration(), (StreamResult)result, props);
                    Writer writer = esr.obtainWriter();
                    AdaptiveEmitter je = new AdaptiveEmitter(pipe, writer);
                    je.setOutputProperties(props);
                    CharacterMapExpander characterMapExpander = makeCharacterMapExpander(pipe, props, charMapIndex);
                    ProxyReceiver normalizer = makeUnicodeNormalizer(pipe, props);
                    return customizeAdaptiveSerializer(je, props, characterMapExpander, normalizer);
                }
                default: {
                    if (method.startsWith("Q{" + NamespaceConstant.SAXON + "}")) {
                        CharacterMapExpander characterMapExpander = makeCharacterMapExpander(pipe, props, charMapIndex);
                        ProxyReceiver normalizer = makeUnicodeNormalizer(pipe, props);
                        target = createSaxonSerializationMethod(
                                method, params, pipe, characterMapExpander, normalizer, (StreamResult)result);
                        if (target instanceof Emitter) {
                            emitter = (Emitter) target;
                        }
                    } else {
                        Receiver userReceiver;
                        userReceiver = createUserDefinedOutputMethod(method, props, pipe);
                        if (userReceiver instanceof Emitter) {
                            emitter = (Emitter) userReceiver;
                            target = params.makeSequenceNormalizer(emitter);
                        } else {
                            return params.makeSequenceNormalizer(userReceiver);
                        }
                    }
                }
            }
            if (emitter != null) {
                emitter.setOutputProperties(props);
                StreamResult sr = (StreamResult) result;
                emitter.setStreamResult(sr);
            }
            //target = new RegularSequenceChecker(target); // add this back in for diagnostics only
            target.setSystemId(result.getSystemId());
            return target;
        } else {
            // Handle results other than StreamResult: these generally do not involve serialization
            return getReceiverForNonSerializedResult(result, props, pipe);

        }
    }

    private ProxyReceiver makeUnicodeNormalizer(PipelineConfiguration pipe, Properties props) throws XPathException {
        String normForm = props.getProperty(SaxonOutputKeys.NORMALIZATION_FORM);
        if (normForm != null && !normForm.equals("none")) {
            return newUnicodeNormalizer(new Sink(pipe), props);
        }
        return null;
    }

    private CharacterMapExpander makeCharacterMapExpander(PipelineConfiguration pipe, Properties props, CharacterMapIndex charMapIndex) throws XPathException {
        String useMaps = props.getProperty(SaxonOutputKeys.USE_CHARACTER_MAPS);
        if (useMaps != null) {
            return charMapIndex.makeCharacterMapExpander(useMaps, new Sink(pipe), this);
        }
        return null;
    }

    /**
     * Get a Receiver to handle a result other than a StreamResult. This will generally not involve
     * serialization.
     * @param result the destination
     * @param props the serialization parameters (which in most cases will be ignored)
     * @param pipe the pipeline configuration
     * @return a suitable receiver to accept the raw query or transformation results
     * @throws XPathException if any failure occurs
     */

    private Receiver getReceiverForNonSerializedResult(Result result, Properties props, PipelineConfiguration pipe) throws XPathException {
        if (result instanceof Emitter) {
            if (((Emitter) result).getOutputProperties() == null) {
                ((Emitter) result).setOutputProperties(props);
            }
            return (Emitter) result;
        } else if (result instanceof JSONSerializer) {
            if (((JSONSerializer) result).getOutputProperties() == null) {
                ((JSONSerializer) result).setOutputProperties(props);
            }
            return (JSONSerializer) result;
        } else if (result instanceof AdaptiveEmitter) {
            if (((AdaptiveEmitter) result).getOutputProperties() == null) {
                ((AdaptiveEmitter) result).setOutputProperties(props);
            }
            return (AdaptiveEmitter) result;
        } else if (result instanceof Receiver) {
            Receiver receiver = (Receiver) result;
            receiver.setSystemId(result.getSystemId());
            receiver.setPipelineConfiguration(pipe);
            if (((Receiver) result).handlesAppend() && "no".equals(props.getProperty(SaxonOutputKeys.BUILD_TREE))) {
                return receiver;
                // TODO: handle item-separator
            } else {
                return new TreeReceiver(receiver);
            }
        } else if (result instanceof SAXResult) {
            ContentHandlerProxy proxy = newContentHandlerProxy();
            proxy.setUnderlyingContentHandler(((SAXResult) result).getHandler());
            proxy.setPipelineConfiguration(pipe);
            proxy.setOutputProperties(props);
            if ("yes".equals(props.getProperty(SaxonOutputKeys.SUPPLY_SOURCE_LOCATOR))) {
                if (config.isCompileWithTracing() && pipe.getController() != null) {
                    pipe.getController().addTraceListener(proxy.getTraceListener());
                } else {
                    throw new XPathException(
                            "Cannot use saxon:supply-source-locator unless tracing was enabled at compile time", SaxonErrorCode.SXSE0002);
                }
            }
            //proxy.open();
            return makeSequenceNormalizer(proxy, props);
        } else if (result instanceof StAXResult) {
            StAXResultHandler handler = new StAXResultHandlerImpl();
            Receiver r = handler.getReceiver(result, props);
            r.setPipelineConfiguration(pipe);
            return makeSequenceNormalizer(r, props);
        } else {
            if (pipe != null) {
                // try to find an external object model that knows this kind of Result
                List externalObjectModels = pipe.getConfiguration().getExternalObjectModels();
                for (Object externalObjectModel : externalObjectModels) {
                    ExternalObjectModel model = (ExternalObjectModel) externalObjectModel;
                    Receiver builder = model.getDocumentBuilder(result);
                    if (builder != null) {
                        builder.setSystemId(result.getSystemId());
                        builder.setPipelineConfiguration(pipe);
                        return new TreeReceiver(builder);
                    }
                }
            }
        }

        throw new IllegalArgumentException("Unknown type of result: " + result.getClass());
    }

    public SequenceReceiver makeSequenceNormalizer(Receiver receiver, Properties properties) {
        String method = properties.getProperty(OutputKeys.METHOD);
        if ("json".equals(method) || "adaptive".equals(method)) {
            return receiver instanceof SequenceReceiver ? (SequenceReceiver)receiver : new TreeReceiver(receiver);
        } else {
            PipelineConfiguration pipe = receiver.getPipelineConfiguration();
            SequenceReceiver result;
            String separator = properties.getProperty(SaxonOutputKeys.ITEM_SEPARATOR);
            if (separator == null || "#absent".equals(separator)) {
                result = new SequenceNormalizerWithSpaceSeparator(receiver);
            } else {
                result = new SequenceNormalizerWithItemSeparator(receiver, separator);
            }
            result.setPipelineConfiguration(pipe);
            return result;
        }
    }

    /**
     * Create a serialization pipeline to implement the HTML output method. This method is protected
     * so that it can be customized in a user-written SerializerFactory
     *
     * @param emitter              the emitter at the end of the pipeline (created using the method {@link #newHTMLEmitter}
     * @param params               the serialization properties
     * @param pipe                 the pipeline configuration information
     * @return a Receiver acting as the entry point to the serialization pipeline
     * @throws XPathException if a failure occurs
     */

    protected SequenceReceiver createHTMLSerializer(
            Emitter emitter, SerializationProperties params, PipelineConfiguration pipe) throws XPathException {
        Receiver target;
        target = emitter;
        Properties props = params.getProperties();
        if (!"no".equals(props.getProperty(OutputKeys.INDENT))) {
            target = newHTMLIndenter(target, props);
        }
        target = new NamespaceDifferencer(target, props);
        target = injectUnicodeNormalizer(params, target);
        target = injectCharacterMapExpander(params, target, true);
        String cdataElements = props.getProperty(OutputKeys.CDATA_SECTION_ELEMENTS);
        if (cdataElements != null && !cdataElements.isEmpty()) {
            target = newCDATAFilter(target, props);
        }

        if (SaxonOutputKeys.isHtmlVersion5(props)) {
            target = addHtml5Component(target, props);
        }

        if (!"no".equals(props.getProperty(SaxonOutputKeys.ESCAPE_URI_ATTRIBUTES))) {
            target = newHTMLURIEscaper(target, props);
        }
        if (!"no".equals(props.getProperty(SaxonOutputKeys.INCLUDE_CONTENT_TYPE))) {
            target = newHTMLMetaTagAdjuster(target, props);
        }
        String attributeOrder = props.getProperty(SaxonOutputKeys.ATTRIBUTE_ORDER);
        if (attributeOrder != null && !attributeOrder.isEmpty()) {
            target = newAttributeSorter(target, props);
        }
        if (params.getValidationFactory() != null) {
            target = params.getValidationFactory().makeFilter(target);
        }
        return makeSequenceNormalizer(target, props);
    }

    /**
     * Create a serialization pipeline to implement the text output method. This method is protected
     * so that it can be customized in a user-written SerializerFactory
     *
     * @param emitter              the emitter at the end of the pipeline (created using the method {@link #newTEXTEmitter}
     * @param params                the serialization properties
     * @return a Receiver acting as the entry point to the serialization pipeline
     * @throws XPathException if a failure occurs
     */

    protected SequenceReceiver createTextSerializer(
            Emitter emitter, SerializationProperties params) throws XPathException {
        Properties props = params.getProperties();
        Receiver target;
        target = injectUnicodeNormalizer(params, emitter);
        target = injectCharacterMapExpander(params, target, false);
        target = addTextOutputFilter(target, props);
        if (params.getValidationFactory() != null) {
            target = params.getValidationFactory().makeFilter(target);
        }
        return makeSequenceNormalizer(target, props);
    }

    /**
     * Create a serialization pipeline to implement the JSON output method. This method is protected
     * so that it can be customized in a user-written SerializerFactory
     *
     * @param emitter              the emitter at the end of the pipeline (created using the method {@link #newTEXTEmitter}
     * @param props                the serialization properties
     * @param characterMapExpander the filter to be used for expanding character maps defined in the stylesheet
     * @param normalizer           the filter used for Unicode normalization
     * @return a Receiver acting as the entry point to the serialization pipeline
     */

    protected SequenceReceiver customizeJSONSerializer(
            JSONSerializer emitter, Properties props,
            CharacterMapExpander characterMapExpander, ProxyReceiver normalizer) throws XPathException {
        if (normalizer instanceof UnicodeNormalizer) {
            emitter.setNormalizer(((UnicodeNormalizer)normalizer).getNormalizer());
        }
        if (characterMapExpander != null) {
            emitter.setCharacterMap(characterMapExpander.getCharacterMap());
        }
        return emitter;
    }

    /**
     * Create a serialization pipeline to implement the Adaptive output method. This method is protected
     * so that it can be customized in a user-written SerializerFactory
     *
     * @param emitter              the emitter at the end of the pipeline
     * @param props                the serialization properties
     * @param characterMapExpander the filter to be used for expanding character maps defined in the stylesheet
     * @param normalizer           the filter used for Unicode normalization
     * @return a Receiver acting as the entry point to the serialization pipeline
     */

    protected SequenceReceiver customizeAdaptiveSerializer(
            AdaptiveEmitter emitter, Properties props,
            CharacterMapExpander characterMapExpander, ProxyReceiver normalizer) {
        if (normalizer instanceof UnicodeNormalizer) {
            emitter.setNormalizer(((UnicodeNormalizer) normalizer).getNormalizer());
        }
        if (characterMapExpander != null) {
            emitter.setCharacterMap(characterMapExpander.getCharacterMap());
        }
        return emitter;
    }


    /**
     * Create a serialization pipeline to implement the XHTML output method. This method is protected
     * so that it can be customized in a user-written SerializerFactory
     *
     * @param emitter              the emitter at the end of the pipeline (created using the method {@link #newXHTMLEmitter}
     * @param params               the serialization properties
     * @param pipe                 the pipeline configuration information
     * @return a Receiver acting as the entry point to the serialization pipeline
     * @throws XPathException if a failure occurs
     */

    protected SequenceReceiver createXHTMLSerializer(
            Emitter emitter, SerializationProperties params, PipelineConfiguration pipe) throws XPathException {
        Receiver target = emitter;
        Properties props = params.getProperties();
        if (!"no".equals(props.getProperty(OutputKeys.INDENT))) {
            target = newXHTMLIndenter(target, props);
        }
        target = new NamespaceDifferencer(target, props);
        target = injectUnicodeNormalizer(params, target);
        target = injectCharacterMapExpander(params, target, true);
        String cdataElements = props.getProperty(OutputKeys.CDATA_SECTION_ELEMENTS);
        if (cdataElements != null && !cdataElements.isEmpty()) {
            target = newCDATAFilter(target, props);
        }

        if (SaxonOutputKeys.isXhtmlHtmlVersion5(props)) {
            target = addHtml5Component(target, props);
        }
        if (!"no".equals(props.getProperty(SaxonOutputKeys.ESCAPE_URI_ATTRIBUTES))) {
            target = newXHTMLURIEscaper(target, props);
        }
        if (!"no".equals(props.getProperty(SaxonOutputKeys.INCLUDE_CONTENT_TYPE))) {
            target = newXHTMLMetaTagAdjuster(target, props);
        }
        String attributeOrder = props.getProperty(SaxonOutputKeys.ATTRIBUTE_ORDER);
        if (attributeOrder != null && !attributeOrder.isEmpty()) {
            target = newAttributeSorter(target, props);
        }
        if (params.getValidationFactory() != null) {
            target = params.getValidationFactory().makeFilter(target);
        }
        return makeSequenceNormalizer(target, props);
    }

    /**
     * This method constructs a step in the output pipeline to perform namespace-related
     * tasks for HTML5 serialization. The default implementation adds a NamespaceReducer
     * and an XHTMLPrefixRemover
     *
     * @param target           the Receiver that receives the output of this step
     * @param outputProperties the serialization properties
     * @return a new Receiver to perform HTML5-related namespace manipulation
     */

    public Receiver addHtml5Component(Receiver target, Properties outputProperties) {
        target = new NamespaceReducer(target);
        target = new XHTMLPrefixRemover(target);
        return target;
    }

    /**
     * Create a serialization pipeline to implement the XML output method. This method is protected
     * so that it can be customized in a user-written SerializerFactory
     *
     * @param emitter              the emitter at the end of the pipeline (created using the method {@link #newXMLEmitter}
     * @param params               the serialization properties
     * @return a Receiver acting as the entry point to the serialization pipeline
     * @throws XPathException if a failure occurs
     */

    protected SequenceReceiver createXMLSerializer(
            XMLEmitter emitter, SerializationProperties params) throws XPathException {
        Receiver target;
        Properties props = params.getProperties();
        boolean canonical = "yes".equals(props.getProperty(SaxonOutputKeys.CANONICAL));
        if ("yes".equals(props.getProperty(OutputKeys.INDENT)) || canonical) {
            target = newXMLIndenter(emitter, props);
        } else {
            target = emitter;
        }
        target = new NamespaceDifferencer(target, props);
        if ("1.0".equals(props.getProperty(OutputKeys.VERSION)) &&
                config.getXMLVersion() == Configuration.XML11) {
            // Check result meets XML 1.0 constraints if configuration allows XML 1.1 input but
            // this result document must conform to 1.0
            target = newXML10ContentChecker(target, props);
        }
        target = injectUnicodeNormalizer(params, target);
        if (!canonical) {
            target = injectCharacterMapExpander(params, target, true);
        }
        String cdataElements = props.getProperty(OutputKeys.CDATA_SECTION_ELEMENTS);
        if (cdataElements != null && !cdataElements.isEmpty() && !canonical) {
            target = newCDATAFilter(target, props);
        }
        if (canonical) {
            target = newAttributeSorter(target, props);
            target = newNamespaceSorter(target, props);
        } else {
            String attributeOrder = props.getProperty(SaxonOutputKeys.ATTRIBUTE_ORDER);
            if (attributeOrder != null && !attributeOrder.isEmpty()) {
                target = newAttributeSorter(target, props);
            }
        }
        if (params.getValidationFactory() != null) {
            target = params.getValidationFactory().makeFilter(target);
        }
        return makeSequenceNormalizer(target, props);
    }

    protected SequenceReceiver createSaxonSerializationMethod(
            String method, SerializationProperties params,
            PipelineConfiguration pipe, CharacterMapExpander characterMapExpander,
            ProxyReceiver normalizer, StreamResult result) throws XPathException {
        throw new XPathException("Saxon serialization methods require Saxon-PE to be enabled");
    }

    /**
     * Create a serialization pipeline to implement a user-defined output method. This method is protected
     * so that it can be customized in a user-written SerializerFactory
     *
     * @param method the name of the user-defined output method, as a QName in Clark format
     *               (that is "{uri}local").
     * @param props  the serialization properties
     * @param pipe   the pipeline configuration information
     * @return a Receiver acting as the entry point to the serialization pipeline
     * @throws XPathException if a failure occurs
     */

    protected SequenceReceiver createUserDefinedOutputMethod(String method, Properties props, PipelineConfiguration pipe) throws XPathException {
        Receiver userReceiver;// See if this output method is recognized by the Configuration
        userReceiver = pipe.getConfiguration().makeEmitter(method, props);
        userReceiver.setPipelineConfiguration(pipe);
        if (userReceiver instanceof ContentHandlerProxy &&
                "yes".equals(props.getProperty(SaxonOutputKeys.SUPPLY_SOURCE_LOCATOR))) {
            if (pipe.getConfiguration().isCompileWithTracing() && pipe.getController() != null) {
                pipe.getController().addTraceListener(
                        ((ContentHandlerProxy) userReceiver).getTraceListener());
            } else {
                throw new XPathException(
                        "Cannot use saxon:supply-source-locator unless tracing was enabled at compile time", SaxonErrorCode.SXSE0002);
            }
        }
        return userReceiver instanceof SequenceReceiver ? (SequenceReceiver)userReceiver : new TreeReceiver(userReceiver);
    }

    protected Receiver injectCharacterMapExpander(SerializationProperties params, Receiver out, boolean useNullMarkers) throws XPathException {
        CharacterMapIndex charMapIndex = params.getCharacterMapIndex();
        if (charMapIndex != null) {
            String useMaps = params.getProperties().getProperty(SaxonOutputKeys.USE_CHARACTER_MAPS);
            if (useMaps != null) {
                CharacterMapExpander expander = charMapIndex.makeCharacterMapExpander(useMaps, out, this);
                expander.setUseNullMarkers(useNullMarkers);
                return expander;
            }
        }
        return out;
    }

    protected Receiver injectUnicodeNormalizer(SerializationProperties params, Receiver out) throws XPathException {
        Properties props = params.getProperties();
        String normForm = props.getProperty(SaxonOutputKeys.NORMALIZATION_FORM);
        if (normForm != null && !normForm.equals("none")) {
            return newUnicodeNormalizer(out, props);
        }
        return out;
    }

    /**
     * Create a ContentHandlerProxy. This method exists so that it can be overridden in a subclass.
     *
     * @return the newly created ContentHandlerProxy.
     */

    protected ContentHandlerProxy newContentHandlerProxy() {
        return new ContentHandlerProxy();
    }

    /**
     * Create an UncommittedSerializer. This method exists so that it can be overridden in a subclass.
     *
     * @param result     the result destination
     * @param next       the next receiver in the pipeline
     * @param params the serialization parameters
     * @return the newly created UncommittedSerializer.
     */

    protected UncommittedSerializer newUncommittedSerializer(Result result, Receiver next, SerializationProperties params) {
        return new UncommittedSerializer(result, next, params);
    }

    /**
     * Create a new XML Emitter. This method exists so that it can be overridden in a subclass.
     *
     * @param properties the output properties
     * @return the newly created XML emitter.
     */

    protected Emitter newXMLEmitter(Properties properties) {
        return new XMLEmitter();
    }

    /**
     * Create a new HTML Emitter. This method exists so that it can be overridden in a subclass.
     *
     * @param properties the output properties
     * @return the newly created HTML emitter.
     */

    protected Emitter newHTMLEmitter(Properties properties) {
        HTMLEmitter emitter;
        // Note, we recognize html-version even when running XSLT 2.0.
        if (SaxonOutputKeys.isHtmlVersion5(properties)) {
            emitter = new HTML50Emitter();
        } else {
            emitter = new HTML40Emitter();
        }
        return emitter;
    }

    /**
     * Create a new XHTML Emitter. This method exists so that it can be overridden in a subclass.
     *
     * @param properties the output properties
     * @return the newly created XHTML emitter.
     */

    protected Emitter newXHTMLEmitter(Properties properties) {
        boolean is5 = SaxonOutputKeys.isXhtmlHtmlVersion5(properties);
        return is5 ? new XHTML5Emitter() : new XHTML1Emitter();
    }

    /**
     * Add a filter to the text output method pipeline. This does nothing unless overridden
     * in a subclass
     *
     * @param next       the next receiver (typically the TextEmitter)
     * @param properties the output properties
     * @return the receiver to be used in place of the "next" receiver
     * @throws XPathException if the operation fails
     */

    public Receiver addTextOutputFilter(Receiver next, Properties properties) throws XPathException {
        return next;
    }

    /**
     * Create a new Text Emitter. This method exists so that it can be overridden in a subclass.
     *
     * @return the newly created text emitter.
     */

    protected Emitter newTEXTEmitter() {
        return new TEXTEmitter();
    }

    /**
     * Create a new XML Indenter. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created XML indenter.
     */

    protected ProxyReceiver newXMLIndenter(XMLEmitter next, Properties outputProperties) {
        XMLIndenter r = new XMLIndenter(next);
        r.setOutputProperties(outputProperties);
        return r;
    }

    /**
     * Create a new HTML Indenter. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created HTML indenter.
     */

    protected ProxyReceiver newHTMLIndenter(Receiver next, Properties outputProperties) {
        HTMLIndenter r = new HTMLIndenter(next, "html");
        r.setOutputProperties(outputProperties);
        return r;
    }

    /**
     * Create a new XHTML Indenter. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created XHTML indenter.
     */

    protected ProxyReceiver newXHTMLIndenter(Receiver next, Properties outputProperties) {
        String method = "xhtml";
        String htmlVersion = outputProperties.getProperty("html-version");
        if (htmlVersion != null && htmlVersion.startsWith("5")) {
            method="xhtml5";
        }
        HTMLIndenter r =  new HTMLIndenter(next, method);
        r.setOutputProperties(outputProperties);
        return r;
    }

    /**
     * Create a new XHTML MetaTagAdjuster, responsible for insertion, removal, or replacement of meta
     * elements. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created XHTML MetaTagAdjuster.
     */

    protected MetaTagAdjuster newXHTMLMetaTagAdjuster(Receiver next, Properties outputProperties) {
        MetaTagAdjuster r = new MetaTagAdjuster(next);
        r.setIsXHTML(true);
        r.setOutputProperties(outputProperties);
        return r;
    }

    /**
     * Create a new XHTML MetaTagAdjuster, responsible for insertion, removal, or replacement of meta
     * elements. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created HTML MetaTagAdjuster.
     */

    protected MetaTagAdjuster newHTMLMetaTagAdjuster(Receiver next, Properties outputProperties) {
        MetaTagAdjuster r = new MetaTagAdjuster(next);
        r.setIsXHTML(false);
        r.setOutputProperties(outputProperties);
        return r;
    }

    /**
     * Create a new HTML URI Escaper, responsible for percent-encoding of URIs in
     * HTML output documents. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created HTML URI escaper.
     */

    protected ProxyReceiver newHTMLURIEscaper(Receiver next, Properties outputProperties) {
        return new HTMLURIEscaper(next);
    }

    /**
     * Create a new XHTML URI Escaper, responsible for percent-encoding of URIs in
     * HTML output documents. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created HTML URI escaper.
     */

    protected ProxyReceiver newXHTMLURIEscaper(Receiver next, Properties outputProperties) {
        return new XHTMLURIEscaper(next);
    }

    /**
     * Create a new CDATA Filter, responsible for insertion of CDATA sections where required.
     * This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created CDATA filter.
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs
     */

    protected ProxyReceiver newCDATAFilter(Receiver next, Properties outputProperties) throws XPathException {
        CDATAFilter r = new CDATAFilter(next);
        r.setOutputProperties(outputProperties);
        return r;
    }

    /**
     * Create a new AttributeSorter, responsible for sorting of attributes into a specified order.
     * This method exists so that it can be overridden in a subclass. The Saxon-HE version of
     * this method returns the supplied receiver unchanged (attribute sorting is not supported
     * in Saxon-HE). The AttributeSorter handles both sorting of attributes into a user-specified
     * order (saxon:attribute-order) and sorting into C14N order (saxon:canonical).
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created filter.
     */

    protected Receiver newAttributeSorter(Receiver next, Properties outputProperties) throws XPathException {
        return next;
    }

    /**
     * Create a new NamespaceSorter, responsible for sorting of namespaces into a specified order.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created filter.
     */

    protected Receiver newNamespaceSorter(Receiver next, Properties outputProperties) throws XPathException {
        return next;
    }

    /**
     * Create a new XML 1.0 content checker, responsible for checking that the output conforms to
     * XML 1.0 rules (this is used only if the Configuration supports XML 1.1 but the specific output
     * file requires XML 1.0). This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created XML 1.0 content checker.
     */

    protected ProxyReceiver newXML10ContentChecker(Receiver next, Properties outputProperties) {
        return new XML10ContentChecker(next);
    }

    /**
     * Create a Unicode Normalizer. This method exists so that it can be overridden in a subclass.
     *
     * @param next             the next receiver in the pipeline
     * @param outputProperties the serialization parameters
     * @return the newly created Unicode normalizer.
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs
     */

    protected ProxyReceiver newUnicodeNormalizer(Receiver next, Properties outputProperties) throws XPathException {
        String normForm = outputProperties.getProperty(SaxonOutputKeys.NORMALIZATION_FORM);
        return new UnicodeNormalizer(normForm, next);
    }

    /**
     * Create a new CharacterMapExpander. This method exists so that it can be overridden in a subclass.
     *
     * @param next the next receiver in the pipeline
     * @return the newly created CharacterMapExpander.
     */

    public CharacterMapExpander newCharacterMapExpander(Receiver next) {
        return new CharacterMapExpander(next);
    }

    /**
     * Prepare another stylesheet to handle the output of this one.
     * <p>This method is intended for internal use, to support the
     * <code>saxon:next-in-chain</code> extension.</p>
     *
     * @param pipe the current transformation
     * @param href       URI of the next stylesheet to be applied
     * @param baseURI    base URI for resolving href if it's a relative
     *                   URI
     * @param result     the output destination of the current stylesheet
     * @return a replacement destination for the current stylesheet
     * @throws XPathException if any dynamic error occurs
     */

    public SequenceReceiver prepareNextStylesheet(PipelineConfiguration pipe, String href, String baseURI, Result result)
            throws XPathException {
        pipe.getConfiguration().checkLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION, "saxon:next-in-chain", -1);
        return null;
    }

    /**
     * Get a SequenceWrapper, a class that serializes an XDM sequence with full annotation of item types, node kinds,
     * etc. There are variants for Saxon-HE and Saxon-PE
     * @param destination the place where the wrapped sequence will be sent
     * @return the new SequenceWrapper
     */

    public SequenceWrapper newSequenceWrapper(Receiver destination) {
        return new SequenceWrapper(destination);
    }

    /**
     * Check that a supplied output property is valid, and normalize the value (specifically in the case of boolean
     * values where yes|true|1 are normalized to "yes", and no|false|0 are normalized to "no"). Clark names in the
     * value (<code>{uri}local</code>) are normalized to EQNames (<code>Q{uri}local</code>)
     *
     * @param key     the name of the property, in Clark format
     * @param value   the value of the property. This may be set to null, in which case no validation takes place.
     *                The value must be in JAXP format, that is, with lexical QNames expanded to either EQNames or
     *                Clark names.
     * @return normalized value of the property, or null if the supplied value is null
     * @throws XPathException if the property name or value is invalid
     */

    public String checkOutputProperty(String key, String value) throws XPathException {
        if (!key.startsWith("{")) {
            switch (key) {
                case SaxonOutputKeys.ALLOW_DUPLICATE_NAMES:
                case SaxonOutputKeys.ESCAPE_URI_ATTRIBUTES:
                case SaxonOutputKeys.INCLUDE_CONTENT_TYPE:
                case OutputKeys.INDENT:
                case OutputKeys.OMIT_XML_DECLARATION:

                case SaxonOutputKeys.UNDECLARE_PREFIXES:
                    if (value != null) {
                        value = checkYesOrNo(key, value);
                    }
                    break;
                case SaxonOutputKeys.BUILD_TREE:
                    if (value != null) {
                        value = checkYesOrNo(key, value);
                    }
                    break;
                case SaxonOutputKeys.BYTE_ORDER_MARK:
                    if (value != null) {
                        value = checkYesOrNo(key, value);
                    }
                    break;
                case OutputKeys.CDATA_SECTION_ELEMENTS:
                case SaxonOutputKeys.SUPPRESS_INDENTATION:
                case SaxonOutputKeys.USE_CHARACTER_MAPS:
                    if (value != null) {
                        value = checkListOfEQNames(key, value);
                    }
                    break;
                case OutputKeys.DOCTYPE_PUBLIC:
                    if (value != null) {
                        checkPublicIdentifier(value);
                    }
                    break;
                case OutputKeys.DOCTYPE_SYSTEM:
                    if (value != null) {
                        checkSystemIdentifier(value);
                    }
                    break;
                case OutputKeys.ENCODING:
                    // no constraints
                    break;
                case SaxonOutputKeys.HTML_VERSION:
                    if (value != null) {
                        checkDecimal(key, value);
                    }
                    break;
                case SaxonOutputKeys.ITEM_SEPARATOR:
                    // no checking needed

                    break;
                case OutputKeys.METHOD:
                case SaxonOutputKeys.JSON_NODE_OUTPUT_METHOD:
                    if (value != null) {
                        value = checkMethod(key, value);
                    }
                    break;
                case OutputKeys.MEDIA_TYPE:
                    // no constraints
                    break;
                case SaxonOutputKeys.NORMALIZATION_FORM:
                    if (value != null) {
                        checkNormalizationForm(value);
                    }
                    break;
                case SaxonOutputKeys.PARAMETER_DOCUMENT:
                    // no checking
                    break;
                case OutputKeys.STANDALONE:
                    if (value != null && !value.equals("omit")) {
                        value = checkYesOrNo(key, value);
                    }
                    break;
                case OutputKeys.VERSION:
                    // no constraints
                    break;
                default:
                    throw new XPathException("Unknown serialization parameter " + Err.wrap(key), "XQST0109");
            }
        } else if (key.startsWith("{http://saxon.sf.net/}")) {
            // Some Saxon serialization parameters are recognized in HE if they are used for internal purposes
            switch (key) {
                case SaxonOutputKeys.STYLESHEET_VERSION:
                    // return
                    break;
                case SaxonOutputKeys.PARAMETER_DOCUMENT_BASE_URI:
                    // return
                    break;
                case SaxonOutputKeys.SUPPLY_SOURCE_LOCATOR:
                case SaxonOutputKeys.UNFAILING:
                    if (value != null) {
                        value = checkYesOrNo(key, value);
                    }
                    break;
                default:
                    throw new XPathException("Serialization parameter " + Err.wrap(key, Err.EQNAME) + " is not available in Saxon-HE", "XQST0109");
            }
        } else {
            //return;
        }
        return value;
    }

    protected static String checkYesOrNo(String key, String value) throws XPathException {
        if ("yes".equals(value) || "true".equals(value) || "1".equals(value)) {
            return "yes";
        } else if ("no".equals(value) || "false".equals(value) || "0".equals(value)) {
            return "no";
        } else {
            throw new XPathException("Serialization parameter " + Err.wrap(key) + " must have the value yes|no, true|false, or 1|0", "SEPM0016");
        }
    }

    private String checkMethod(String key, String value) throws XPathException {
        if (!"xml".equals(value) && !"html".equals(value) && !"xhtml".equals(value) && !"text".equals(value)) {
            if (!SaxonOutputKeys.JSON_NODE_OUTPUT_METHOD.equals(key) && ("json".equals(value) || "adaptive".equals(value))) {
                return value;
            }
            if (value.startsWith("{")) {
                value = "Q" + value;
            }
            if (isValidEQName(value)) {
                checkExtensions(value);
            } else {
                throw new XPathException("Invalid value (" + value + ") for serialization method: " +
                                                 "must be xml|html|xhtml|text|json|adaptive, or a QName in 'Q{uri}local' form", "SEPM0016");
            }
        }
        return value;
    }

    private static void checkNormalizationForm(String value) throws XPathException {
        if (!NameChecker.isValidNmtoken(value)) {
            throw new XPathException("Invalid value for normalization-form: " +
                                             "must be NFC, NFD, NFKC, NFKD, fully-normalized, or none", "SEPM0016");
        }
    }

    private static boolean isValidEQName(String value) {
        Objects.requireNonNull(value);
        if (value.isEmpty() || !value.startsWith("Q{")) {
            return false;
        }
        int closer = value.indexOf('}', 2);
        return closer >= 2 &&
                closer != value.length() - 1 &&
                NameChecker.isValidNCName(value.substring(closer + 1));
    }

    private static boolean isValidClarkName(/*@NotNull*/ String value) {
        if (value.startsWith("{")) {
            return isValidEQName("Q" + value);
        } else {
            return isValidEQName("Q{}" + value);
        }
    }

    protected static void checkNonNegativeInteger(String key, String value) throws XPathException {
        try {
            int n = Integer.parseInt(value);
            if (n < 0) {
                throw new XPathException("Value of " + Err.wrap(key) + " must be a non-negative integer", "SEPM0016");
            }
        } catch (NumberFormatException err) {
            throw new XPathException("Value of " + Err.wrap(key) + " must be a non-negative integer", "SEPM0016");
        }
    }

    private static void checkDecimal(String key, String value) throws XPathException {
        if (!BigDecimalValue.castableAsDecimal(value)) {
            throw new XPathException("Value of " + Err.wrap(key) +
                                             " must be a decimal number", "SEPM0016");
        }
    }

    protected static String checkListOfEQNames(String key, String value) throws XPathException {
        StringTokenizer tok = new StringTokenizer(value, " \t\n\r", false);
        StringBuilder builder = new StringBuilder();
        while (tok.hasMoreTokens()) {
            String s = tok.nextToken();
            if (isValidEQName(s) || NameChecker.isValidNCName(s)) {
                builder.append(s);
            } else if (isValidClarkName(s)) {
                if (s.startsWith("{")) {
                    builder.append("Q").append(s);
                } else {
                    builder.append("Q{}").append(s);
                }
            } else {
                throw new XPathException("Value of " + Err.wrap(key) +
                                                 " must be a list of QNames in 'Q{uri}local' notation", "SEPM0016");
            }
            builder.append(" ");
        }
        return builder.toString();
    }

    protected static String checkListOfEQNamesAllowingStar(String key, String value) throws XPathException {
        StringBuilder builder = new StringBuilder();
        StringTokenizer tok = new StringTokenizer(value, " \t\n\r", false);
        while (tok.hasMoreTokens()) {
            String s = tok.nextToken();
            if ("*".equals(s) || isValidEQName(s) || NameChecker.isValidNCName(s)) {
                builder.append(s);
            } else if (isValidClarkName(s)) {
                if (s.startsWith("{")) {
                    builder.append("Q").append(s);
                } else {
                    builder.append("Q{}").append(s);
                }
            } else {
                throw new XPathException("Value of " + Err.wrap(key) +
                                                 " must be a list of QNames in 'Q{uri}local' notation", "SEPM0016");
            }
            builder.append(" ");
        }
        return builder.toString().trim();
    }

    private static Pattern publicIdPattern = Pattern.compile("^[\\s\\r\\na-zA-Z0-9\\-'()+,./:=?;!*#@$_%]*$");

    private static void checkPublicIdentifier(String value) throws XPathException {
        if (!publicIdPattern.matcher(value).matches()) {
            throw new XPathException("Invalid character in doctype-public parameter", "SEPM0016");
        }
    }

    private static void checkSystemIdentifier(/*@NotNull*/ String value) throws XPathException {
        if (value.contains("'") && value.contains("\"")) {
            throw new XPathException("The doctype-system parameter must not contain both an apostrophe and a quotation mark", "SEPM0016");
        }
    }

    /**
     * Process a serialization property whose value is a list of element names, for example cdata-section-elements
     *
     * @param value        The value of the property as written
     * @param nsResolver   The namespace resolver to use; may be null if prevalidated is set or if names are supplied
     *                     in Clark format
     * @param useDefaultNS True if the namespace resolver should be used for unprefixed names; false if
     *                     unprefixed names should be considered to be in no namespace
     * @param prevalidated true if the property has already been validated
     * @param errorCode    The error code to return in the event of problems
     * @return The list of element names with lexical QNames replaced by Clark names, starting with a single space
     * @throws XPathException if any error is found in the list of element names, for example, an undeclared namespace prefix
     */

    /*@NotNull*/
    public static String parseListOfNodeNames(
            String value, NamespaceResolver nsResolver, boolean useDefaultNS, boolean prevalidated, /*@NotNull*/  String errorCode)
            throws XPathException {
        StringBuilder s = new StringBuilder();
        StringTokenizer st = new StringTokenizer(value, " \t\n\r", false);
        while (st.hasMoreTokens()) {
            String displayname = st.nextToken();
            if (prevalidated || (nsResolver == null)) {
                s.append(' ').append(displayname);
            } else if (displayname.startsWith("Q{")) {
                s.append(' ').append(displayname.substring(1));
            } else {
                try {
                    String[] parts = NameChecker.getQNameParts(displayname);
                    String muri = nsResolver.getURIForPrefix(parts[0], useDefaultNS);
                    if (muri == null) {
                        throw new XPathException("Namespace prefix '" + parts[0] + "' has not been declared", errorCode);
                    }
                    s.append(" {").append(muri).append('}').append(parts[1]);
                } catch (QNameException err) {
                    throw new XPathException("Invalid element name. " + err.getMessage(), errorCode);
                }
            }
        }
        return s.toString();
    }

    protected void checkExtensions(String key /*@Nullable*/) throws XPathException {
        throw new XPathException("Serialization property " + Err.wrap(key, Err.EQNAME) + " is not available in Saxon-HE");
    }

    protected Comparator<AtomicValue> getPropertySorter(String sortSpecification) throws XPathException {
        throw new XPathException("Serialization property saxon:property-order is not available in Saxon-HE");
    }


}

