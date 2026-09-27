////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import net.sf.saxon.event.*;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.expr.sort.RuleBasedSubstringMatcher;
import net.sf.saxon.expr.sort.SimpleCollation;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.push.Push;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.CommandLineOptions;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

import javax.xml.transform.Source;
import java.io.File;
import java.io.OutputStream;
import java.io.Writer;
import java.text.RuleBasedCollator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/**
 * The <code>Processor</code> class serves three purposes: it allows global Saxon configuration options to be set;
 * it acts as a factory for generating XQuery, XPath, and XSLT compilers; and it owns certain shared
 * resources such as the Saxon NamePool and compiled schemas. This is the first object that a
 * Saxon application should create. Once established, a {@code Processor} may be used in multiple threads.
 *
 * <p>It is possible to run more than one Saxon {@code Processor} concurrently, but only when running completely
 * independent workloads. Nothing can be shared between Processor instances. Within a query or transformation,
 * all source documents and schemas must be built using the same {@code Processor}, which must also be used to
 * compile the query or stylesheet.</p>
 *
 * <p>Internally, the {@code Processor} is a wrapper around a Saxon {@link Configuration} object, and the underlying
 * {@code Configuration} is available via the {@link #getUnderlyingConfiguration()} method. This exposes additional
 * Saxon functionality; however, the details may be subject to change from one release to the next.</p>
 */

@CSharpModifiers(code={"internal"})
public class Processor implements Configuration.ApiProvider {

    private Configuration config;
    private SchemaManager schemaManager;


    /**
     * Create a {@code Processor} according to the capabilities available.
     * <p>This will examine what software is installed, and whether or not a license key is available,
     * and return the most capable configuration available within these constraints. For example
     * if the software is Saxon-EE but the license only allows PE capability, it will return
     * a processor with Saxon-PE capabilities.</p>
     * @since 12.0
     */

    public Processor() {
        this(Configuration.newLicensedConfiguration());
    }

    /**
     * Create a {@code Processor}, specifying whether a licensed configuration is required
     *
     * @param licensedEdition indicates whether the Processor requires features of Saxon that need a license
     *                        file (that is, features not available in Saxon HE (Home Edition). If true, the method will create
     *                        a Configuration appropriate to the version of the software that is running: for example, if running
     *                        Saxon-EE, it will create an EnterpriseConfiguration. The method does not at this stage check that a license
     *                        is available, and in the absence of a license, it should run successfully provided no features that
     *                        require licensing are actually used. If the argument is set to false, a plain Home Edition Configuration
     *                        is created unconditionally.
     */

    public Processor(boolean licensedEdition) {
        if (licensedEdition) {
            config = Configuration.newConfiguration();
            if (config.getEditionCode().equals("EE")) {
                schemaManager = makeSchemaManager();
            }
        } else {
            config = new Configuration();
        }
        config.setProcessor(this);
    }

    /**
     * Create a {@code Processor} based on an existing {@link Configuration}. This constructor is useful
     * when new components of an application are to use s9api interfaces but existing
     * components use older interfaces (for example, JAXP). It is also useful in cases where,
     * for example, multiple configurations need to be built using the same configuration file
     * or sharing license data: in such cases, the {@code Configuration} can be manually built, and
     * then wrapped in a {@code Processor}.
     *
     * @param config the {@link Configuration} to be used by this {@code Processor}
     * @since 9.3
     */

    public Processor(/*@NotNull*/ Configuration config) {
        this.config = config;
        if (config.getEditionCode().equals("EE")) {
            schemaManager = makeSchemaManager();
        }
    }

    /**
     * Create a {@code Processor} configured according to the settings in a supplied configuration file.
     *
     * @param source the Source of the configuration file (which is an XML document)
     * @throws SaxonApiException if the configuration file cannot be read, or its contents are invalid
     * @since 9.2
     */

    public Processor(Source source) throws SaxonApiException {
        try {
            config = Configuration.readConfiguration(source);
            if (config.getEditionCode().equals("EE")) {
                schemaManager = makeSchemaManager();
            }
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
        config.setProcessor(this);
    }

    /**
     * Create a {@link DocumentBuilder}. A {@code DocumentBuilder} is used to load source XML documents.
     *
     * @return a newly created {@code DocumentBuilder}
     */

    /*@NotNull*/
    public DocumentBuilder newDocumentBuilder() {
        return new DocumentBuilder(config);
    }

    /**
     * Create a {@link JsonBuilder}. A {@code JsonBuilder} is used to load source JSON documents.
     *
     * @return a newly created {@code JsonBuilder}
     * @since 11
     */

    /*@NotNull*/
    public JsonBuilder newJsonBuilder() {
        return new JsonBuilder(getUnderlyingConfiguration());
    }

    /**
     * Create an {@link XPathCompiler}. An {@code XPathCompiler} is used to compile XPath expressions.
     *
     * @return a newly created {@code XPathCompiler}
     */

    /*@NotNull*/
    public XPathCompiler newXPathCompiler() {
        return new XPathCompiler(this);
    }

    /**
     * Create an {@link XsltCompiler}. An {@code XsltCompiler} is used to compile XSLT stylesheets.
     *
     * @return a newly created {@code XsltCompiler}
     */

    /*@NotNull*/
    public XsltCompiler newXsltCompiler() {
        return new XsltCompiler(this);
    }

    /**
     * Create an {@link XQueryCompiler}. An {@code XQueryCompiler} is used to compile XQuery queries.
     *
     * @return a newly created {@code XQueryCompiler}
     */

    /*@NotNull*/
    public XQueryCompiler newXQueryCompiler() {
        return new XQueryCompiler(this);
    }

    /**
     * Create a {@link Serializer}. A {@code Serializer} is a {@link Destination} for processes
     * such as XSLT transformation, that renders the output as a readable file.
     *
     * @return a new {@code Serializer}
     * @since 9.3
     */

    /*@NotNull*/
    public Serializer newSerializer() {
        return new Serializer(this);
    }

    /**
     * Create a {@link Serializer} initialized to write to a given {@link OutputStream}.
     * <p>Closing the output stream after use is the responsibility of the caller.</p>
     *
     * @param stream The {@link OutputStream} to which the {@code Serializer} will write
     * @return a new {@code Serializer}
     * @since 9.3
     */

    /*@NotNull*/
    public Serializer newSerializer(OutputStream stream) {
        Serializer s = new Serializer(this);
        s.setOutputStream(stream);
        return s;
    }

    /**
     * Create a {@link Serializer} initialized to write to a given {@link Writer}.
     * <p>Closing the writer after use is the responsibility of the caller.</p>
     *
     * @param writer The {@link Writer} to which the {@code Serializer} will write
     * @return a new {@code Serializer}
     * @since 9.3
     */

    /*@NotNull*/
    public Serializer newSerializer(Writer writer) {
        Serializer s = new Serializer(this);
        s.setOutputWriter(writer);
        return s;
    }

    /**
     * Create a {@link Serializer} initialized to write to a given File.
     *
     * @param file The {@link File} to which the Serializer will write
     * @return a new {@code Serializer}
     * @since 9.3
     */

    /*@NotNull*/
    public Serializer newSerializer(File file) {
        Serializer s = new Serializer(this);
        s.setOutputFile(file);
        return s;
    }

    /**
     * Get a new {@link Push} provider. The returned {@link Push} object allows the client
     * application to construct events (such as {@code startElement()}, {@code text()},
     * and {@code endElement()}) and send them to a specified {@link Destination}.
     *
     * @param destination the destination
     * @return a new recipient of {@link Push} events.
     * @throws SaxonApiException if the {@link Destination} is not able to handle the request.
     */

    public Push newPush(Destination destination) throws SaxonApiException {
        PipelineConfiguration pipe = getUnderlyingConfiguration().makePipelineConfiguration();
        SerializationProperties props = new SerializationProperties();
        return new PushToReceiver(destination.getReceiver(pipe, props));
    }
    /**
     * Register a simple external/extension function that is to be made available within any stylesheet, query,
     * or XPath expression compiled under the control of this processor.
     * <p>This interface provides only for simple extension functions that have no side-effects and no dependencies
     * on the static or dynamic context.</p>
     *
     * @param function the implementation of the extension function.
     * @since 9.4
     */

    public void registerExtensionFunction(ExtensionFunction function) {
        ExtensionFunctionDefinitionWrapper wrapper = new ExtensionFunctionDefinitionWrapper(function);
        registerExtensionFunction(wrapper);
    }

    /**
     * Register an extension function that is to be made available within any stylesheet, query,
     * or XPath expression compiled under the control of this processor. This method
     * registers an extension function implemented as an instance of
     * {@link net.sf.saxon.lib.ExtensionFunctionDefinition}, using an arbitrary name and namespace.
     * <p>This interface allows extension functions that have dependencies on the static or dynamic
     * context. It also allows an extension function to declare that it has side-effects, in which
     * case calls to the function will be optimized less aggressively than usual, although the semantics
     * are still to some degree unpredictable.</p>
     *
     * @param function the implementation of the extension function.
     * @since 9.2
     */

    public void registerExtensionFunction(ExtensionFunctionDefinition function) {
        try {
            config.registerExtensionFunction(function);
        } catch (Exception err) {
            throw new IllegalArgumentException(err);
        }
    }


    /**
     * Create an {@link XsdCompiler}. This provides capabilities to load
     * XML schema definitions.
     *
     * @return a new {@code XsdCompiler}, provided the Saxon configuration is a licensed Saxon-EE
     * configuration. In other cases, the method throws an exception.
     * @throws UnsupportedOperationException if this is not a licensed Saxon-EE configuration.
     * @since 13.0.
     */

    public XsdCompiler newXsdCompiler() {
        return config.makeXsdCompiler(this);
    }

    /**
     * Get the associated {@code SchemaManager}. The {@code SchemaManager} provides capabilities to load and cache
     * XML schema definitions. There is exactly one {@code SchemaManager} in a schema-aware Processor, and none
     * in a Processor that is not schema-aware. The {@code SchemaManager} is created automatically by the system.
     *
     * <p>The {@code SchemaManager} is retained in Saxon 13 to provide a degree of compatibility with
     * older releases. However, the model of schema processing has changed. In previous releases, all
     * compiled schema information was held globally in the {@link Configuration} (that is, at the
     * level of the {@link Processor}. From Saxon 13, schemas are more modular, which means that
     * it becomes an application responsibility to ensure that the correct schema is used for each
     * aspect of processing. To take advantage of this flexibility, use of the central {@link SchemaManager}
     * should be replaced with the {@link XsdCompiler}.</p>
     *
     * @return the associated SchemaManager, or null if the Processor is not schema-aware.
     * @deprecated since 13.0. Use {@link #newXsdCompiler}.
     */

    @Deprecated
    public SchemaManager getSchemaManager() {
        return schemaManager;
    }

    /**
     * Test whether this processor is schema-aware
     *
     * @return true if this processor is licensed for schema processing, false otherwise
     */

    public boolean isSchemaAware() {
        return config.isLicensedFeature(Configuration.LicenseFeature.SCHEMA_VALIDATION);
    }

    /**
     * Get the user-visible Saxon product version, for example "9.0.0.1"
     *
     * @return the Saxon product version, as a string
     */

    public String getSaxonProductVersion() {
        return Version.getProductVersion();
    }

    /**
     * Get the short name of the Saxon product edition, for example "EE". This represents the kind of configuration
     * that has been created, rather than the software that has been installed; for example it is possible to
     * instantiate an "HE" configuration even when using the "PE" or "EE" software.
     * @return the Saxon edition code: "EE", "PE", or "HE"
     */

    public String getSaxonEdition() {
        return config.getEditionCode();
    }

    /**
     * Set the version of XML used by this Processor. If the value is set to "1.0", then
     * output documents will be serialized as XML 1.0. This option also affects
     * the characters permitted to appear in queries and stylesheets, and the characters that can appear
     * in names (for example, in path expressions).
     * <p>Note that source documents specifying xml version="1.0" or "1.1" are accepted
     * regardless of this setting.</p>
     *
     * @param version must be one of the strings "1.0" or "1.1"
     * @throws IllegalArgumentException if any string other than "1.0" or "1.1" is supplied
     */

    public void setXmlVersion(/*@NotNull*/ String version) {
        switch (version) {
            case "1.0" -> config.setXMLVersion(Configuration.XML10);
            case "1.1" -> config.setXMLVersion(Configuration.XML11);
            default -> throw new IllegalArgumentException("XmlVersion");
        }
    }

    /**
     * Get the version of XML used by this Processor. If the value is "1.0", then input documents
     * must be XML 1.0 documents, and output documents will be serialized as XML 1.0. This option also affects
     * the characters permitted to appear in queries and stylesheets, and the characters that can appear
     * in names (for example, in path expressions).
     *
     * @return one of the strings "1.0" or "1.1"
     */

    /*@NotNull*/
    public String getXmlVersion() {
        if (config.getXMLVersion() == Configuration.XML10) {
            return "1.0";
        } else {
            return "1.1";
        }
    }

    /**
     * Set a configuration property. This method is useful when it is necessary to identify
     * configuration properties by name; however, the method {@link #setConfigurationProperty(Feature, Object)}
     * is preferred because it is more efficient and offers better type safety.
     *
     * @param name  the name of the option to be set. The names of the options available are listed
     *              as constants in class {@link net.sf.saxon.lib.FeatureKeys}.
     * @param value the value of the option to be set.
     * @throws IllegalArgumentException if the property name is not recognized or if the supplied value
     *                                  is not a valid value for the named property.
     */

    public void setConfigurationProperty(String name, Object value) {
        Objects.requireNonNull(name, "feature name");
        Objects.requireNonNull(value, "feature value");
        if (name.equals(FeatureKeys.CONFIGURATION)) {
            config = (Configuration) value;
        } else {
            config.setConfigurationProperty(name, value);
        }
    }

    /**
     * Get the value of a configuration property
     *
     * @param name the name of the option required. The names of the properties available are listed
     *             as constants in class {@link net.sf.saxon.lib.FeatureKeys}.
     * @return the value of the property, if one is set; or null if the property is unset and there is
     *         no default.
     * @throws IllegalArgumentException if the property name is not recognized
     * @deprecated since 9.9 - use {@link #getConfigurationProperty(Feature)}
     */


    /*@Nullable*/
    @Deprecated
    public Object getConfigurationProperty(/*@NotNull*/ String name) {
        return config.getConfigurationProperty(name);
    }

    /**
     * Set a configuration property
     *
     * @param feature the option to be set. The names of the options available are listed
     *              as constants in class {@link net.sf.saxon.lib.Feature}.
     * @param value the value of the option to be set (which must be of the appropriate type for the
     *              particular feature.
     * @param <T> the type of the value required by the feature (often boolean or string)
     * @throws IllegalArgumentException if the supplied value is not a valid value for the selected feature.
     * @since 9.9 introduced to give a faster and type-safe alternative to
     * {@link #setConfigurationProperty(String, Object)}
     */

    @CSharpReplaceBody(code=""
            + "    if ((feature.code==Saxon.Hej.lib.FeatureCode.CONFIGURATION)) {"
            + "        config = (Saxon.Hej.Configuration)(object)value;"
            + "    } else {\n"
            + "        config.setConfigurationProperty(feature, value);"
            + "    }")
    public <T> void setConfigurationProperty(Feature<T> feature, T value) {
        if (feature == Feature.CONFIGURATION) {
            config = (Configuration) value;
        } else {
            config.setConfigurationProperty(feature, value);
        }
    }

    /**
     * Get the value of a configuration property
     *
     * @param feature the option required. The names of the properties available are listed
     *             as constants in class {@link net.sf.saxon.lib.Feature}.
     * @param <T> the type of the feature (often boolean or string)
     * @return the value of the property, if one is set; or null if the property is unset and there is
     * no default.
     * @since 9.9 introduced to give a faster and type-safe alternative to
     * {@link #getConfigurationProperty(String)}
     */


    /*@Nullable*/
    public <T> T getConfigurationProperty(Feature<T> feature) {
        return config.getConfigurationProperty(feature);
    }

    /**
     * Bind a collation URI to a collation
     *
     * @param uri       the absolute collation URI
     * @param collation a {@link Comparator} object that implements the required collation
     * @throws IllegalArgumentException if an attempt is made to rebind the standard URI
     *                                  for the Unicode codepoint collation or the HTML5 case-blind
     *                                  collation
     * @since 9.6. Changed in 9.8 to allow any Comparator to be supplied as a collation
     */

    public void  declareCollation(String uri, final Comparator<? super String> collation) {
        if (uri.equals(NamespaceConstant.CODEPOINT_COLLATION_URI)) {
            throw new IllegalArgumentException("Cannot redeclare the Unicode codepoint collation URI");
        }
        if (uri.equals(NamespaceConstant.HTML5_CASE_BLIND_COLLATION_URI)) {
            throw new IllegalArgumentException("Cannot redeclare the HTML5 case-blind collation URI");
        }
        if (uri.equals(NamespaceConstant.UNICODE_CASE_BLIND_COLLATION_URI)) {
            throw new IllegalArgumentException("Cannot redeclare the HTML5 case-blind collation URI");
        }
        StringCollator saxonCollation = makeStringCollator(uri, collation);
        config.registerCollation(uri, saxonCollation);
    }

    @CSharpReplaceBody(code="return new Saxon.Hej.expr.sort.SimpleCollation(uri, collation);")
    private static StringCollator makeStringCollator(String uri, Comparator<? super String> collation) {
        if (collation instanceof RuleBasedCollator) {
            return new RuleBasedSubstringMatcher(uri, (RuleBasedCollator) collation);
        } else {
            return new SimpleCollation(uri, collation);
        }
    }

    /**
     * Register a specific URI and bind it to a specific ResourceCollection. A collection that is
     * registered in this way will be returned prior to calling any registered {@link CollectionFinder}.
     * This method should only be used while the configuration is being initialized for use;
     * the effect of adding or replacing collections dynamically while a configuration is in use
     * is undefined.
     *
     * <p>Registered collections take priority over any user-supplied <code>CollectionFinder</code>;
     * if a collection URI has been registered, then it is used before the user-supplied
     * <code>CollectionFinder</code> is invoked.</p>
     *
     * @param collectionURI the collection URI to be registered. Must not be null.
     * @param collection    the ResourceCollection to be associated with this URI. Must not be null.
     * @since 11.0
     */

    public void registerCollection(String collectionURI, ResourceCollection collection) {
        config.registerCollection(collectionURI, collection);
    }

    /**
     * Supply one or more resource catalog files to be used for URI resolution.
     * <p>The call has no effect if the <code>CommonResourceResolver</code> registered with the
     * <code>Configuration</code> does not use catalog files.</p>
     * @param fileNames the files to be used. If no files are supplied, the call removes any existing catalog
     *                  files that were previously registered.
     */

    public void setCatalogFiles(String... fileNames) {
        if (config.getResourceResolver() instanceof ConfigurableResourceResolver) {
            CommandLineOptions.setCatalogFiles(((ConfigurableResourceResolver) config.getResourceResolver() ), Arrays.asList(fileNames));
        }
    }

    /**
     * Get the underlying {@link Configuration} object that underpins this Processor. This method
     * provides an escape hatch to internal Saxon implementation objects that offer a finer and lower-level
     * degree of control than the s9api classes and methods. Some of these classes and methods may change
     * from release to release.
     *
     * @return the underlying Configuration object
     */

    public Configuration getUnderlyingConfiguration() {
        return config;
    }

    /**
     * Write an XdmValue to a given destination.
     *
     * <p>If the destination is a {@link Serializer} then the method <code>processor.writeXdmValue(V, S)</code>
     * is equivalent to calling <code>S.serializeXdmValue(V)</code>.</p>
     *
     * <p>In other cases, the sequence represented by the XdmValue is "normalized"
     * as defined in the serialization specification (this is equivalent to constructing a document node
     * in XSLT or XQuery with this sequence as the content expression), and the resulting document is
     * then copied to the destination. Note that the construction of a document tree will fail if
     * the sequence contains items such as maps and arrays.</p>
     *
     * @param value       the value to be written
     * @param destination the destination to which the value is to be written
     * @throws SaxonApiException if any failure occurs, for example a serialization error
     */

    public void writeXdmValue(XdmValue value, Destination destination) throws SaxonApiException {
        Objects.requireNonNull(value);
        Objects.requireNonNull(destination);
        try {
            if (destination instanceof Serializer) {
                ((Serializer)destination).serializeXdmValue(value);
            } else {
                Receiver out = destination.getReceiver(config.makePipelineConfiguration(), config.obtainDefaultSerializationProperties());
                ComplexContentOutputter tree = new ComplexContentOutputter(out);
                tree.open();
                tree.startDocument(ReceiverOption.NONE);
                for (XdmItem item : value) {
                    tree.append(item.getUnderlyingValue(), Loc.NONE, ReceiverOption.ALL_NAMESPACES);
                }
                tree.endDocument();
                tree.close();
                destination.closeAndNotify();
            }
        } catch (XPathException err) {
            throw new SaxonApiException(err);
        }
    }

    private static class ExtensionFunctionDefinitionWrapper extends ExtensionFunctionDefinition {

        private final ExtensionFunction function;

        public ExtensionFunctionDefinitionWrapper(ExtensionFunction function) {
            this.function = function;
        }

        /**
         * Get the name of the function, as a QName.
         * <p>This method must be implemented in all subclasses</p>
         *
         * @return the function name
         */
        @Override
        public StructuredQName getFunctionQName() {
            return function.getName().getStructuredQName();
        }

        /**
         * Get the minimum number of arguments required by the function
         * <p>The default implementation returns the number of items in the result of calling
         * {@link #getArgumentTypes}</p>
         *
         * @return the minimum number of arguments that must be supplied in a call to this function
         */
        @Override
        public int getMinimumNumberOfArguments() {
            return function.getArgumentTypes().length;
        }

        /**
         * Get the maximum number of arguments allowed by the function.
         * <p>The default implementation returns the value of {@link #getMinimumNumberOfArguments}
         *
         * @return the maximum number of arguments that may be supplied in a call to this function
         */
        @Override
        public int getMaximumNumberOfArguments() {
            return function.getArgumentTypes().length;
        }

        /**
         * Get the required types for the arguments of this function.
         * <p>This method must be implemented in all subtypes.</p>
         *
         * @return the required types of the arguments, as defined by the function signature. Normally
         *         this should be an array of size {@link #getMaximumNumberOfArguments()}; however for functions
         *         that allow a variable number of arguments, the array can be smaller than this, with the last
         *         entry in the array providing the required type for all the remaining arguments.
         */
        /*@NotNull*/
        @Override
        public net.sf.saxon.value.SequenceType[] getArgumentTypes() {
            net.sf.saxon.s9api.SequenceType[] declaredArgs = function.getArgumentTypes();
            net.sf.saxon.value.SequenceType[] types = new net.sf.saxon.value.SequenceType[declaredArgs.length];
            for (int i = 0; i < declaredArgs.length; i++) {
                OccurrenceIndicator occurrenceIndicator = declaredArgs[i].getOccurrenceIndicator();
                types[i] = net.sf.saxon.value.SequenceType.makeSequenceType(
                        declaredArgs[i].getItemType().getUnderlyingItemType(),
                        Cardinality.staticPropertyFromOccurrenceIndicator(occurrenceIndicator));
            }
            return types;
        }

        /**
         * Get the type of the result of the function
         * <p>This method must be implemented in all subtypes.</p>
         *
         * @param suppliedArgumentTypes the static types of the supplied arguments to the function.
         *                              This is provided so that a more precise result type can be returned in the common
         *                              case where the type of the result depends on the types of the arguments.
         * @return the return type of the function, as defined by its function signature
         */
        @Override
        public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
            net.sf.saxon.s9api.SequenceType declaredResult = function.getResultType();
            OccurrenceIndicator occurrenceIndicator = declaredResult.getOccurrenceIndicator();
            return net.sf.saxon.value.SequenceType.makeSequenceType(
                    declaredResult.getItemType().getUnderlyingItemType(),
                    Cardinality.staticPropertyFromOccurrenceIndicator(occurrenceIndicator));
        }

        /**
         * Ask whether the result actually returned by the function can be trusted,
         * or whether it should be checked against the declared type.
         *
         * @return true if the function implementation warrants that the value it returns will
         *         be an instance of the declared result type. The default value is false, in which case
         *         the result will be checked at run-time to ensure that it conforms to the declared type.
         *         If the value true is returned, but the function returns a value of the wrong type, the
         *         consequences are unpredictable. No attempt is made to coerce the returned value to
         *         the declared result type.
         */
        @Override
        public boolean trustResultType() {
            return false;
        }

        /**
         * Ask whether the result of the function depends on the focus, or on other variable parts
         * of the context.
         *
         * @return true if the result of the function depends on the context item, position, or size.
         *         Despite the method name, the method should also return true if the function depends on other
         *         parts of the context that vary from one part of the query/stylesheet to another, for example
         *         the XPath default namespace.
         *         <p>The default implementation returns false.</p>
         *         <p>The method must return true if the function
         *         makes use of any of these values from the dynamic context. Returning true inhibits certain
         *         optimizations, such as moving the function call out of the body of an xsl:for-each loop,
         *         or extracting it into a global variable.</p>
         */
        @Override
        public boolean dependsOnFocus() {
            return false;
        }

        /**
         * Ask whether the function has side-effects. If the function does have side-effects, the optimizer
         * will be less aggressive in moving or removing calls to the function. However, calls on functions
         * with side-effects can never be guaranteed.
         *
         * @return true if the function has side-effects (including creation of new nodes, if the
         *         identity of those nodes is significant). The default implementation returns false.
         */
        @Override
        public boolean hasSideEffects() {
            return false;
        }

        /**
         * Create a call on this function. This method is called by the compiler when it identifies
         * a function call that calls this function.
         */
        /*@NotNull*/
        @Override
        public ExtensionFunctionCall makeCallExpression() {
            return new ExtensionFunctionCall() {
                @Override
                public Sequence call(
                        /*@NotNull*/ XPathContext context, Sequence[] arguments) throws XPathException {
                    XdmValue[] args = new XdmValue[arguments.length];
                    for (int i = 0; i < args.length; i++) {
                        GroundedValue val = arguments[i].materialize();
                        args[i] = XdmValue.wrap(val);
                    }
                    try {
                        XdmValue result = function.call(args);
                        return result.getUnderlyingValue();
                    } catch (SaxonApiException e) {
                        throw new XPathException(e);
                    }
                }
            };
        }
    }

    private SchemaManager makeSchemaManager() {
        SchemaManager manager = null;
        return manager;
    }

    /**
     * Get any schema that has been created using the now-deprecated SchemaManager API
     * @return any schema that has been created using the SchemaManager API, or null if absent
     */
    protected XsdSchema getLegacySchema() {
        if (schemaManager != null && schemaManager.hasLoadedSchema) {
            return schemaManager.getXsdSchema();
        }
        return null;
    }


}

