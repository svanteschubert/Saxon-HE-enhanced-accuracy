////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.FilterFactory;
import net.sf.saxon.expr.accum.Accumulator;
import net.sf.saxon.om.SpaceStrippingRule;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.om.TreeModel;
import net.sf.saxon.trans.Maker;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.ValidationParams;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.util.*;


/**
 * This class defines options for parsing and/or validating a source document. Some of the options
 * are relevant only when parsing, some only when validating, but they are combined into a single
 * class because the two operations are often performed together.
 */

@SuppressWarnings("WeakerAccess")
public class ParseOptions {

    private int schemaValidation = Validation.DEFAULT;
    private int dtdValidation = Validation.DEFAULT;
    private StructuredQName topLevelElement;
    private SchemaType topLevelType;
    /*@Nullable*/ private transient XMLReader parser = null;
    private Maker<XMLReader> parserMaker;
    /*@Nullable*/ private Boolean wrapDocument = null;
    /*@Nullable*/ private TreeModel treeModel = null;
    //private int stripSpace = Whitespace.UNSPECIFIED;
    private SpaceStrippingRule spaceStrippingRule = null;
    /*@Nullable*/ private Boolean lineNumbering = null;
    private boolean pleaseClose = false;
    /*@Nullable*/ private transient ErrorReporter errorReporter = null;
    /*@Nullable*/ private transient EntityResolver entityResolver = null;
    /*@Nullable*/ private transient ErrorHandler errorHandler = null;
    /*@Nullable*/ private List<FilterFactory> filters = null;
    private boolean continueAfterValidationErrors = false;
    private boolean addCommentsAfterValidationErrors = false;
    private boolean expandAttributeDefaults = true;
    private boolean useXsiSchemaLocation = true;
    private boolean checkEntityReferences = false;
    private boolean stable = true;
    private int validationErrorLimit = Integer.MAX_VALUE;
    /*@Nullable*/ private ValidationParams validationParams = null;
    /*@Nullable*/ private ValidationStatisticsRecipient validationStatisticsRecipient = null;
    private Map<String, Boolean> parserFeatures = null;
    private Map<String, Object> parserProperties = null;
    private InvalidityHandler invalidityHandler = null;
    private Set<? extends Accumulator> applicableAccumulators = null; // null means "all"

    /**
     * Create a ParseOptions object with default options set
     */

    public ParseOptions() {
        //entityResolver = new StandardEntityResolver();
    }

    /**
     * Create a ParseOptions object as a copy of another ParseOptions
     *
     * @param p the ParseOptions to be copied
     */

    public ParseOptions(/*@NotNull*/ ParseOptions p) {
        schemaValidation = p.schemaValidation;
        validationParams = p.validationParams;
        setDTDValidationMode(p.dtdValidation);
        topLevelElement = p.topLevelElement;
        topLevelType = p.topLevelType;
        parserMaker = p.getXMLReaderMaker();
        parser = p.parser;
        wrapDocument = p.wrapDocument;
        treeModel = p.treeModel;
        spaceStrippingRule = p.spaceStrippingRule;
        lineNumbering = p.lineNumbering;
        pleaseClose = p.pleaseClose;
        errorHandler = p.errorHandler;
        errorReporter = p.errorReporter;
        entityResolver = p.entityResolver;
        invalidityHandler = p.invalidityHandler;
        stable = p.stable;
        if (p.filters != null) {
            filters = new ArrayList<>(p.filters);
        }
        setExpandAttributeDefaults(p.expandAttributeDefaults);
        useXsiSchemaLocation = p.useXsiSchemaLocation;
        validationErrorLimit = p.validationErrorLimit;
        continueAfterValidationErrors = p.continueAfterValidationErrors;
        addCommentsAfterValidationErrors = p.addCommentsAfterValidationErrors;
        if (p.parserFeatures != null) {
            parserFeatures = new HashMap<>(p.parserFeatures);
        }
        if (p.parserProperties != null) {
            parserProperties = new HashMap<>(p.parserProperties);
        }
        applicableAccumulators = p.applicableAccumulators;
        checkEntityReferences = p.checkEntityReferences;
        validationStatisticsRecipient = p.validationStatisticsRecipient;
    }

    /**
     * Merge another set of {@code ParseOptions} into these {@code ParseOptions}.
     * Properties in the other {@code ParseOptions} take precedence over properties
     * in these {@code ParseOptions}. "Taking precedence" here means:
     *
     * <ul>
     *     <li>If a non-default value for a property is present in one {@code ParseOptions} and the
     *     default value is present in the other, the non-default value is used.</li>
     *     <li>If both {@code ParseOptions} objects have non-default values for a property, then the
     *     value is taken from the one that "takes precedence".</li>
     * </ul>
     *
     * @param options the set of {@code ParseOptions} properties to be merged in.
     */

    public void merge(/*@NotNull*/ ParseOptions options) {
        if (options.dtdValidation != Validation.DEFAULT) {
            setDTDValidationMode(options.dtdValidation);
        }
        if (options.schemaValidation != Validation.DEFAULT) {
            schemaValidation = options.schemaValidation;
        }
        if (options.invalidityHandler != null) {
            invalidityHandler = options.invalidityHandler;
        }
        if (options.topLevelElement != null) {
            topLevelElement = options.topLevelElement;
        }
        if (options.topLevelType != null) {
            topLevelType = options.topLevelType;
        }
        if (options.parser != null) {
            parser = options.parser;
        }
        if (options.wrapDocument != null) {
            wrapDocument = options.wrapDocument;
        }
        if (options.treeModel != null) {
            treeModel = options.treeModel;
        }
        if (options.spaceStrippingRule != null) {
            spaceStrippingRule = options.spaceStrippingRule;
        }
        if (options.lineNumbering != null) {
            lineNumbering = options.lineNumbering;
        }
        if (options.pleaseClose) {
            pleaseClose = true;
        }
        if (options.errorReporter != null) {
            errorReporter = options.errorReporter;
        }
        if (options.entityResolver != null) {
            entityResolver = options.entityResolver;
        }
        if (options.filters != null) {
            if (filters == null) {
                filters = new ArrayList<>();
            }
            filters.addAll(options.filters);
        }
        if (options.parserFeatures != null) {
            if (parserFeatures == null) {
                parserFeatures = new HashMap<>();
            }
            parserFeatures.putAll(options.parserFeatures);
        }
        if (options.parserProperties != null) {
            if (parserProperties == null) {
                parserProperties = new HashMap<>();
            }
            parserProperties.putAll(options.parserProperties);
        }
        if (!options.expandAttributeDefaults) {
            // expand defaults unless the other options says don't
            setExpandAttributeDefaults(false);
        }
        if (!options.useXsiSchemaLocation) {
            // expand defaults unless the other options says don't
            useXsiSchemaLocation = false;
        }
        if (options.addCommentsAfterValidationErrors) {
            // add comments if either set of options requests it
            addCommentsAfterValidationErrors = true;
        }
        validationErrorLimit = java.lang.Math.min(validationErrorLimit, options.validationErrorLimit);
    }

    /**
     * Merge settings from the Configuration object into these parseOptions
     *
     * @param config the Configuration. Settings from the Configuration are
     *               used only where no setting is present in this ParseOptions object
     */

    public void applyDefaults(/*@NotNull*/ Configuration config) {
        if (dtdValidation == Validation.DEFAULT) {
            setDTDValidationMode(config.isValidation() ? Validation.STRICT : Validation.SKIP);
        }
        if (schemaValidation == Validation.DEFAULT) {
            schemaValidation = config.getSchemaValidationMode();
        }
        if (treeModel == null) {
            treeModel = TreeModel.getTreeModel(config.getTreeModel());
        }
        if (spaceStrippingRule == null) {
            spaceStrippingRule = config.getParseOptions().getSpaceStrippingRule();
        }
        if (lineNumbering == null) {
            lineNumbering = config.isLineNumbering();
        }
        if (errorReporter == null) {
            setErrorReporter(config.makeErrorReporter());
        }

    }

    /**
     * Add a filter to the list of filters to be applied to the raw input
     *
     * @param filterFactory the filterFactory to be added
     */

    public void addFilter(FilterFactory filterFactory) {
        if (filters == null) {
            filters = new ArrayList<>(5);
        }
        filters.add(filterFactory);
    }

    /**
     * Get the list of filters to be applied to the input. Returns null if there are no filters.
     *
     * @return the list of filters, if there are any
     */

    /*@Nullable*/
    public List<FilterFactory> getFilters() {
        return filters;
    }

    /**
     * Get the space-stripping action to be applied to the source document
     *
     * @return the space stripping rule to be used
     */

    public SpaceStrippingRule getSpaceStrippingRule() {
        return spaceStrippingRule;
    }

    /**
     * Set the space-stripping action to be applied to the source document
     *
     * @param rule space stripping rule to be used
     */

    public void setSpaceStrippingRule(SpaceStrippingRule rule) {
        spaceStrippingRule = rule;
    }

    /**
     * Set the tree model to use. Default is the tiny tree
     *
     * @param model typically {@link net.sf.saxon.event.Builder#TINY_TREE},
     *              {@link net.sf.saxon.event.Builder#LINKED_TREE} or {@link net.sf.saxon.event.Builder#TINY_TREE_CONDENSED}
     */

    public void setTreeModel(int model) {
        treeModel = TreeModel.getTreeModel(model);
    }

    /**
     * Add a parser feature to a map, which will be applied to the XML parser later
     *
     * @param uri   The features as a URIs
     * @param value The value given to the feature as boolean
     */
    public void addParserFeature(String uri, boolean value) {
        if (parserFeatures == null) {
            parserFeatures = new HashMap<>();
        }
        parserFeatures.put(uri, value);
    }

    /**
     * Add a parser property to a map, which is applied to the XML parser later
     *
     * @param uri   The properties as a URIs
     * @param value The value given to the properties as a string
     */
    public void addParserProperties(String uri, Object value) {
        if (parserProperties == null) {
            parserProperties = new HashMap<>();
        }
        parserProperties.put(uri, value);
    }

    /**
     * Get a particular parser feature added
     *
     * @param uri The feature name as a URIs
     * @return The feature value as boolean
     */
    public boolean getParserFeature(String uri) {
        return parserFeatures.get(uri);
    }

    /**
     * Get a particular parser property added
     *
     * @param name The properties as a URIs
     * @return The property value (which may be any object)
     */
    public Object getParserProperty(String name) {
        return parserProperties.get(name);
    }

    /**
     * Get all the parser features added
     *
     * @return A map of (feature, value) pairs
     */
    public Map<String, Boolean> getParserFeatures() {
        return parserFeatures;
    }

    /**
     * Get all the parser properties added
     *
     * @return A map of (feature, string) pairs
     */
    public Map<String, Object> getParserProperties() {
        return parserProperties;
    }

    /**
     * Get the tree model that will be used.
     *
     * @return typically {@link net.sf.saxon.event.Builder#TINY_TREE}, {@link net.sf.saxon.event.Builder#LINKED_TREE},
     * or {@link net.sf.saxon.event.Builder#TINY_TREE_CONDENSED},
     * or {@link net.sf.saxon.event.Builder#UNSPECIFIED_TREE_MODEL} if no call on setTreeModel() has been made
     */

    public int getTreeModel() {
        if (treeModel == null) {
            return Builder.UNSPECIFIED_TREE_MODEL;
        }
        return treeModel.getSymbolicValue();
    }

    /**
     * Set the tree model to use. Default is the tiny tree
     *
     * @param model typically one of the constants {@link net.sf.saxon.om.TreeModel#TINY_TREE},
     *              {@link TreeModel#TINY_TREE_CONDENSED}, or {@link TreeModel#LINKED_TREE}. However, in principle
     *              a user-defined tree model can be used.
     * @since 9.2
     */

    public void setModel(TreeModel model) {
        treeModel = model;
    }

    /**
     * Get the tree model that will be used.
     *
     * @return typically one of the constants {@link net.sf.saxon.om.TreeModel#TINY_TREE},
     * {@link TreeModel#TINY_TREE_CONDENSED}, or {@link TreeModel#LINKED_TREE}. However, in principle
     * a user-defined tree model can be used.
     */

    public TreeModel getModel() {
        return treeModel == null ? TreeModel.TINY_TREE : treeModel;
    }


    /**
     * Set whether or not schema validation of this source is required
     *
     * @param option one of {@link Validation#STRICT},
     *               {@link Validation#LAX}, {@link Validation#STRIP},
     *               {@link Validation#PRESERVE}, {@link Validation#DEFAULT}
     */

    public void setSchemaValidationMode(int option) {
        schemaValidation = option;
    }

    /**
     * Get whether or not schema validation of this source is required
     *
     * @return the validation mode requested, or {@link Validation#DEFAULT}
     * to use the default validation mode from the Configuration.
     */

    public int getSchemaValidationMode() {
        return schemaValidation;
    }

    /**
     * Set whether to expand default attributes defined in a DTD or schema.
     * By default, default attribute values are expanded
     *
     * @param expand true if missing attribute values are to take the default value
     *               supplied in a DTD or schema, false if they are to be left as absent
     */

    public void setExpandAttributeDefaults(boolean expand) {
        this.expandAttributeDefaults = expand;
    }

    /**
     * Ask whether to expand default attributes defined in a DTD or schema.
     * By default, default attribute values are expanded
     *
     * @return true if missing attribute values are to take the default value
     * supplied in a DTD or schema, false if they are to be left as absent
     */

    public boolean isExpandAttributeDefaults() {
        return expandAttributeDefaults;
    }

    /**
     * Set the name of the top-level element for validation.
     * If a top-level element is set then the document
     * being validated must have this as its outermost element
     *
     * @param elementName the QName of the required top-level element, or null to unset the value
     */

    public void setTopLevelElement(StructuredQName elementName) {
        topLevelElement = elementName;
    }

    /**
     * Get the name of the top-level element for validation.
     * If a top-level element is set then the document
     * being validated must have this as its outermost element
     *
     * @return the QName of the required top-level element, or null if no value is set
     * @since 9.0
     */

    public StructuredQName getTopLevelElement() {
        return topLevelElement;
    }

    /**
     * Set the type of the top-level element for validation.
     * If this is set then the document element is validated against this type
     *
     * @param type the schema type required for the document element, or null to unset the value
     */

    public void setTopLevelType(SchemaType type) {
        topLevelType = type;
    }

    /**
     * Get the type of the document element for validation.
     * If this is set then the document element of the document
     * being validated must have this type
     *
     * @return the type of the required top-level element, or null if no value is set
     */

    public SchemaType getTopLevelType() {
        return topLevelType;
    }

    /**
     * Set whether or not to use the xsi:schemaLocation and xsi:noNamespaceSchemaLocation attributes
     * in an instance document to locate a schema for validation. Note, these attribute are only used
     * if validation is requested.
     *
     * @param use true if these attributes are to be used, false if they are to be ignored
     */

    public void setUseXsiSchemaLocation(boolean use) {
        useXsiSchemaLocation = use;
    }

    /**
     * Ask whether or not to use the xsi:schemaLocation and xsi:noNamespaceSchemaLocation attributes
     * in an instance document to locate a schema for validation. Note, these attribute are only used
     * if validation is requested.
     *
     * @return true (the default) if these attributes are to be used, false if they are to be ignored
     */

    public boolean isUseXsiSchemaLocation() {
        return useXsiSchemaLocation;
    }

    /**
     * Get the limit on the number of errors reported before schema validation is abandoned. Default
     * is unlimited (Integer.MAX_VALUE)
     *
     * @return the limit on the number of errors
     */

    public int getValidationErrorLimit() {
        return validationErrorLimit;
    }

    /**
     * Set a limit on the number of errors reported before schema validation is abandoned. Default
     * is unlimited (Integer.MAX_VALUE). If set to one, validation is terminated as soon as a single
     * validation error is detected.
     *
     * @param validationErrorLimit the limit on the number of errors
     */

    public void setValidationErrorLimit(int validationErrorLimit) {
        this.validationErrorLimit = validationErrorLimit;
    }

    /**
     * Set whether or not DTD validation of this source is required
     *
     * @param option one of {@link Validation#STRICT},  {@link Validation#LAX},
     *               {@link Validation#STRIP}, {@link Validation#DEFAULT}.
     *               <p>The value {@link Validation#LAX} indicates that DTD validation is
     *               requested, but validation failures are treated as warnings only.</p>
     */

    public void setDTDValidationMode(int option) {
        dtdValidation = option;
        addParserFeature("http://xml.org/sax/features/validation",
                         option == Validation.STRICT || option == Validation.LAX);
    }

    /**
     * Get whether or not DTD validation of this source is required
     *
     * @return the validation mode requested, or {@link Validation#DEFAULT}
     * to use the default validation mode from the Configuration.
     * <p>The value {@link Validation#LAX} indicates that DTD validation is
     * requested, but validation failures are treated as warnings only.</p>
     */

    public int getDTDValidationMode() {
        return dtdValidation;
    }

    /**
     * Say that statistics of component usage are maintained during schema validation, and indicate where
     * they should be sent
     *
     * @param recipient the agent to be notified of the validation statistics on completion of the
     *                  validation episode, May be set to null if no agent is to be notified.
     */

    public void setValidationStatisticsRecipient(/*@Nullable*/ ValidationStatisticsRecipient recipient) {
        validationStatisticsRecipient = recipient;
    }

    /**
     * Ask whether statistics of component usage are maintained during schema validation,
     * and where they will be sent
     *
     * @return the agent to be notified of the validation statistics on completion of the
     * validation episode, or null if none has been nominated
     */

    /*@Nullable*/
    public ValidationStatisticsRecipient getValidationStatisticsRecipient() {
        return validationStatisticsRecipient;
    }

    /**
     * Set whether line numbers are to be maintained in the constructed document
     *
     * @param lineNumbering true if line numbers are to be maintained
     */

    public void setLineNumbering(boolean lineNumbering) {
        this.lineNumbering = lineNumbering;
    }

    /**
     * Get whether line numbers are to be maintained in the constructed document
     *
     * @return true if line numbers are maintained
     */

    public boolean isLineNumbering() {
        return lineNumbering != null && lineNumbering;
    }

    /**
     * Determine whether setLineNumbering() has been called
     *
     * @return true if setLineNumbering() has been called
     */

    public boolean isLineNumberingSet() {
        return lineNumbering != null;
    }

    /**
     * Set the SAX parser (XMLReader) to be used. This method must be used with care, because
     * an XMLReader is not thread-safe. If there is any chance that this ParseOptions object will
     * be used in multiple threads, then this property should not be set. Instead, set the XMLReaderMaker
     * property, which allows a new parser to be created each time it is needed.
     *
     * @param parser the SAX parser
     */

    public void setXMLReader(XMLReader parser) {
        this.parser = parser;
    }

    /**
     * Get the SAX parser (XMLReader) to be used.
     * @return the parser
     */

    /*@Nullable*/
    public XMLReader getXMLReader() {
        return parser;
    }

    /**
     * Set the parser factory class to be used.
     *
     * @param parserMaker a factory object that delivers an XMLReader on demand
     */

    public void setXMLReaderMaker(Maker<XMLReader> parserMaker) {
        this.parserMaker = parserMaker;
    }

    /**
     * Get the parser factory class to be used
     *
     * @return a factory object that delivers an XMLReader on demand, or null if none has been set
     */

    /*@Nullable*/
    public Maker<XMLReader> getXMLReaderMaker() {
        return parserMaker;
    }

    /**
     * Obtain an XMLReader (parser), by making one using the XMLReaderMaker if available, or by
     * returning the registered XMLReader if available, or failing that, return null
     */

    public XMLReader obtainXMLReader() throws XPathException {
        if (parserMaker != null) {
            return parserMaker.make();
        } else if (parser != null) {
            return parser;
        } else {
            return null;
        }
    }


    /**
     * Set an EntityResolver to be used when parsing. Note that this will not be used if an XMLReader
     * has been supplied (in that case, the XMLReader should be initialized with the EntityResolver
     * already set.)
     *
     * @param resolver the EntityResolver to be used. May be null, in which case any existing
     *                 EntityResolver is removed from the options
     */

    public void setEntityResolver(/*@Nullable*/ EntityResolver resolver) {
        entityResolver = resolver;
    }

    /**
     * Get the EntityResolver that will be used when parsing
     *
     * @return the EntityResolver, if one has been set using {@link #setEntityResolver},
     * otherwise null.
     */

    /*@Nullable*/
    public EntityResolver getEntityResolver() {
        return entityResolver;
    }

    /**
     * Set an ErrorHandler to be used when parsing. Note that this will not be used if an XMLReader
     * has been supplied (in that case, the XMLReader should be initialized with the ErrorHandler
     * already set.)
     *
     * @param handler the ErrorHandler to be used, or null to indicate that no ErrorHandler is to be used.
     */

    public void setErrorHandler(/*@Nullable*/ ErrorHandler handler) {
        errorHandler = handler;
    }

    /**
     * Get the ErrorHandler that will be used when parsing
     *
     * @return the ErrorHandler, if one has been set using {@link #setErrorHandler},
     * otherwise null.
     */

    /*@Nullable*/
    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }


    /**
     * Assuming that the contained Source is a node in a tree, indicate whether a tree should be created
     * as a view of this supplied tree, or as a copy.
     *
     * @param wrap if true, the node in the supplied Source is wrapped, to create a view. If false, the node
     *             and its contained subtree is copied. If null, the system default is chosen.
     */

    public void setWrapDocument(/*@Nullable*/ Boolean wrap) {
        wrapDocument = wrap;
    }

    /**
     * Assuming that the contained Source is a node in a tree, determine whether a tree will be created
     * as a view of this supplied tree, or as a copy.
     *
     * @return if true, the node in the supplied Source is wrapped, to create a view. If false, the node
     * and its contained subtree is copied. If null, the system default is chosen.
     * @since 8.8
     */

    /*@Nullable*/
    public Boolean getWrapDocument() {
        return wrapDocument;
    }

    /**
     * <p>Set state of XInclude processing.</p>
     * <p>If XInclude markup is found in the document instance, should it be
     * processed as specified in <a href="http://www.w3.org/TR/xinclude/">
     * XML Inclusions (XInclude) Version 1.0</a>.</p>
     * <p>XInclude processing defaults to <code>false</code>.</p>
     *
     * @param state Set XInclude processing to <code>true</code> or
     *              <code>false</code>
     * @since 8.9
     */
    public void setXIncludeAware(boolean state) {
        addParserFeature("http://apache.org/xml/features/xinclude", state);
    }

    /**
     * <p>Determine whether setXIncludeAware() has been called.</p>
     *
     * @return true if setXIncludeAware() has been called
     */

    public boolean isXIncludeAwareSet() {
        return parserFeatures != null && parserFeatures.get("http://apache.org/xml/features/xinclude") != null;
    }

    /**
     * <p>Get state of XInclude processing.</p>
     *
     * @return current state of XInclude processing. Default value is false.
     */

    public boolean isXIncludeAware() {
        if (parserFeatures == null) {
            return false;
        } else {
            Boolean b = parserFeatures.get("http://apache.org/xml/features/xinclude");
            return b != null && b;
        }
    }

    /**
     * Set an ErrorReporter to be used when parsing
     *
     * @param reporter the ErrorReporter to be used; or null, to indicate that the
     *                 standard ErrorReporter is to be used.
     */

    public void setErrorReporter(/*@Nullable*/ ErrorReporter reporter) {
        if (reporter == null) {
            reporter = new StandardErrorReporter();
        }
        errorReporter = reporter;
    }

    /**
     * Get the ErrorReporter that will be used when parsing
     *
     * @return the ErrorReporter, if one has been set using {@link #setErrorReporter},
     * otherwise null.
     */

    /*@Nullable*/
    public ErrorReporter getErrorReporter() {
        return errorReporter;
    }

    /**
     * Say that processing should continue after a validation error. Note that all validation
     * errors are reported to the error() method of the ErrorListener, and processing always
     * continues except when this method chooses to throw an exception. At the end of the document,
     * a fatal error is thrown if (a) there have been any validation errors, and (b) this option
     * is not set.
     *
     * @param keepGoing true if processing should continue
     */

    public void setContinueAfterValidationErrors(boolean keepGoing) {
        continueAfterValidationErrors = keepGoing;
    }

    /**
     * Ask whether processing should continue after a validation error. Note that all validation
     * errors are reported to the error() method of the ErrorListener, and processing always
     * continues except when this method chooses to throw an exception. At the end of the document,
     * a fatal error is thrown if (a) there have been any validation errors, and (b) this option
     * is not set.
     *
     * @return true if processing should continue
     */

    public boolean isContinueAfterValidationErrors() {
        return continueAfterValidationErrors;
    }

    /**
     * Say that on validation errors, messages explaining the error should (where possible)
     * be written as comments in the validated source document. This option is only relevant when
     * processing continues after a validation error
     *
     * @param keepGoing true if comments should be added
     * @since 9.3. Default is now false; in previous releases this option was always on.
     */

    public void setAddCommentsAfterValidationErrors(boolean keepGoing) {
        addCommentsAfterValidationErrors = keepGoing;
    }

    /**
     * Ask whether on validation errors, messages explaining the error should (where possible)
     * be written as comments in the validated source document. This option is only relevant when
     * processing continues after a validation error
     *
     * @return true if comments should be added
     * @since 9.3
     */

    public boolean isAddCommentsAfterValidationErrors() {
        return addCommentsAfterValidationErrors;
    }

    /**
     * Set the validation parameters. These are the values of variables declared in the schema
     * using the saxon:param extension, and referenced in XSD assertions (or CTA expressions)
     * associated with user-defined types
     *
     * @param params the validation parameters
     */

    public void setValidationParams(ValidationParams params) {
        validationParams = params;
    }

    /**
     * Get the validation parameters. These are the values of variables declared in the schema
     * using the saxon:param extension, and referenced in XSD assertions (or CTA expressions)
     * associated with user-defined types
     *
     * @return the validation parameters
     */

    public ValidationParams getValidationParams() {
        return validationParams;
    }


    /**
     * Say whether to check elements and attributes of type xs:ENTITY (or xs:ENTITIES)
     * against the unparsed entities declared in the document's DTD. This is normally
     * true when performing standalone schema validation, false when invoking validation
     * from XSLT or XQuery.
     *
     * @param check true if entities are to be checked, false otherwise
     */

    public void setCheckEntityReferences(boolean check) {
        this.checkEntityReferences = check;
    }

    /**
     * Ask whether to check elements and attributes of type xs:ENTITY (or xs:ENTITIES)
     * against the unparsed entities declared in the document's DTD. This is normally
     * true when performing standalone schema validation, false when invoking validation
     * from XSLT or XQuery.
     *
     * @return true if entities are to be checked, false otherwise
     */

    public boolean isCheckEntityReferences() {
        return this.checkEntityReferences;
    }

    /**
     * Ask whether the document (or collection) should be stable, that is, if repeated attempts to dereference
     * the same URI are guaranteed to return the same result. By default, documents and collections are stable.
     *
     * @return true if the document or collection is stable
     */

    public boolean isStable() {
        return stable;
    }

    /**
     * Say whether the document (or collection) should be stable, that is, if repeated attempts to dereference
     * the same URI are guaranteed to return the same result. By default, documents and collections are stable.
     *
     * @param stable true if the document or collection is stable
     */

    public void setStable(boolean stable) {
        this.stable = stable;
    }

    /**
     * Get the callback for reporting validation errors
     *
     * @return the registered InvalidityHandler
     */

    public InvalidityHandler getInvalidityHandler() {
        return invalidityHandler;
    }

    /**
     * Set the callback for reporting validation errors
     *
     * @param invalidityHandler the InvalidityHandler to be used for reporting validation failures
     */

    public void setInvalidityHandler(InvalidityHandler invalidityHandler) {
        this.invalidityHandler = invalidityHandler;
    }

    /**
     * Set the list of XSLT 3.0 accumulators that apply to this tree.
     *
     * @param accumulators the accumulators that apply; or null if all accumulators apply.
     *                     (Note, this is not the same as the meaning of #all in the
     *                     use-accumulators attribute, which refers to all accumulators
     *                     declared in a given package).
     */

    public void setApplicableAccumulators(Set<? extends Accumulator> accumulators) {
        this.applicableAccumulators = accumulators;
    }

    /**
     * Set the list of XSLT 3.0 accumulators that apply to this tree.
     *
     * @return the accumulators that apply; or null if all accumulators apply.
     *                     (Note, this is not the same as the meaning of #all in the
     *                     use-accumulators attribute, which refers to all accumulators
     *                     declared in a given package).
     */

    public Set<? extends Accumulator> getApplicableAccumulators() {
        return applicableAccumulators;
    }


    /**
     * Set whether or not the user of this Source is encouraged to close it as soon as reading is finished.
     * Normally the expectation is that any Stream in a StreamSource will be closed by the component that
     * created the Stream. However, in the case of a Source returned by a URIResolver, there is no suitable
     * interface (the URIResolver has no opportunity to close the stream). Also, in some cases such as reading
     * of stylesheet modules, it is possible to close the stream long before control is returned to the caller
     * who supplied it. This tends to make a difference on .NET, where a file often can't be opened if there
     * is a stream attached to it.
     *
     * @param close true if the source should be closed as soon as it has been consumed
     */

    public void setPleaseCloseAfterUse(boolean close) {
        pleaseClose = close;
    }

    /**
     * Determine whether or not the user of this Source is encouraged to close it as soon as reading is
     * finished.
     *
     * @return true if the source should be closed as soon as it has been consumed
     */

    public boolean isPleaseCloseAfterUse() {
        return pleaseClose;
    }

    /**
     * Close any resources held by a given Source. This only works if the underlying Source is one that is
     * recognized as holding closable resources.
     *
     * @param source the source to be closed
     * @since 9.2
     */

    public static void close(/*@NotNull*/ Source source) {
        try {
            if (source instanceof StreamSource) {
                StreamSource ss = (StreamSource) source;
                if (ss.getInputStream() != null) {
                    ss.getInputStream().close();
                }
                if (ss.getReader() != null) {
                    ss.getReader().close();
                }
            } else if (source instanceof SAXSource) {
                InputSource is = ((SAXSource) source).getInputSource();
                if (is != null) {
                    if (is.getByteStream() != null) {
                        is.getByteStream().close();
                    }
                    if (is.getCharacterStream() != null) {
                        is.getCharacterStream().close();
                    }
                }
            } else if (source instanceof AugmentedSource) {
                ((AugmentedSource)source).close();
            }
        } catch (IOException err) {
            // no action
        }
    }

}

