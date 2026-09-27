////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.FilterFactory;
import net.sf.saxon.expr.accum.Accumulator;
import net.sf.saxon.ma.trie.ImmutableHashTrieMap;
import net.sf.saxon.ma.trie.TrieKVP;
import net.sf.saxon.om.SpaceStrippingRule;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.om.TreeModel;
import net.sf.saxon.trans.Maker;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.transpile.CSharpSimpleEnum;
import net.sf.saxon.type.Schema;
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
 *
 * <p>Rewritten in 12.x as an immutable class, because the product was previously creating a large number
 * of instances as copies of other instances, without ever making any changes, just in case a change
 * was required.</p>
 */

@SuppressWarnings({"WeakerAccess", "unchecked", "ReplaceNullCheck"})
public class ParseOptions {

    @CSharpSimpleEnum
    private enum Key {
        PARSER_FEATURES,
        PARSER_PROPERTIES,
        ENTITY_RESOLVER,
        XINCLUDE_AWARE,
        XML_READER,
        XML_READER_MAKER,
        ADD_COMMENTS_AFTER_VALIDATION_ERRORS,
        APPLICABLE_ACCUMULATORS,
        CHECK_ENTITY_REFERENCES,
        CONTINUE_AFTER_VALIDATION_ERRORS,
        DTD_VALIDATION,

        ERROR_HANDLER,
        ERROR_REPORTER,
        EXPAND_ATTRIBUTE_DEFAULTS,
        FILTERS,
        INVALIDITY_HANDLER,
        LINE_NUMBERING,
        MODEL,
        PLEASE_CLOSE,
        SCHEMA,
        SCHEMA_VALIDATION,
        SPACE_STRIPPING_RULE,
        STABLE,
        TOP_LEVEL_ELEMENT,
        TOP_LEVEL_TYPE,
        TREE_MODEL,
        TRUSTED,
        USE_XSI_SCHEMA_LOCATION,
        VALIDATION_ERROR_LIMIT,
        VALIDATION_PARAMS,
        VALIDATION_STATISTICS_RECIPIENT,
        WRAP_DOCUMENT
        
    }

    private final ImmutableHashTrieMap<Key, Object> properties;

    private List<Key> cacheKeys;
    private List<Object> cacheValues;
    private List<ParseOptions> cacheResults;

    /**
     * Create a ParseOptions object with default options set
     */

    public ParseOptions() {
        properties = init();
    }

    /**
     * Private constructor for internal use: create a ParseOptions object with supplied content
     * @param properties the initial property values to be set
     */
    private ParseOptions(ImmutableHashTrieMap<Key, Object> properties) {
        this.properties = properties;
    }

    @CSharpReplaceBody(code = "return System.Collections.Immutable.ImmutableDictionary<Saxon.Hej.lib.ParseOptions.Key,System.Object>.Empty;")
    private ImmutableHashTrieMap<Key, Object> init() {
        return ImmutableHashTrieMap.empty();
    }
    /**
     * Look to see if the result of an update is already cached.
     * Return the result if so; otherwise return null
     */

    private synchronized ParseOptions searchCache(Key key, Object value) {
        if (cacheKeys == null) {
            return null;
        }
        for (int i=0; i<cacheKeys.size(); i++) {
            if (cacheKeys.get(i) == key && cacheValues.get(i) == value) {
                return cacheResults.get(i);
            }
        }
        return null;
    }

    /**
     * Add a new entry to the cache
     */

     private synchronized void addToCache(Key key, Object value, ParseOptions result) {
         if (cacheKeys == null) {
             cacheKeys = new ArrayList<>(10);
             cacheValues = new ArrayList<>(10);
             cacheResults = new ArrayList<>(10);
         } else if (cacheKeys.size() >= 10) {
             // rough and ready - empty the cache and start again
             cacheKeys.clear();
             cacheValues.clear();
             cacheResults.clear();
         }
         cacheKeys.add(key);
         cacheValues.add(value);
         cacheResults.add(result);
     }

    /**
     * Return a copy of the ParseOptions object with one property set to a different value
     * @param key the property to be set
     * @param value the new value for the property, or null to remove the property
     * @return a new ParseOptions object (or the original if there is no change)
     */
    private ParseOptions withProperty(Key key, Object value) {
        //Instrumentation.count("ParseOptions change");
        if (value == properties.get(key)) {
            return this;
        }
        //Instrumentation.count("ParseOptions non-trivial change");
        ParseOptions result = searchCache(key, value);
        if (result != null) {
            //Instrumentation.count("ParseOptions cache hit");
            return result;
        }
        //Instrumentation.count("ParseOptions cache miss");
        if (value == null) {
            result = new ParseOptions(properties.remove(key));
        } else {
            result = new ParseOptions(properties.put(key, value));
        }
        addToCache(key, value, result);
        //Instrumentation.count("ParseOptions cache size " + cacheKeys.size());
        return result;
    }

    /**
     * Get the value of a property with a given key
     * @param key the key identifying the required property
     * @return the value of the property (null if it has not been set)
     */
    private Object getProperty(Key key) {
        return properties.get(key);
    }

    /**
     * Ask whether a value has been set for a particular property
     * @param key the key identifying the required property
     * @return true if the property has a value
     */
    private boolean hasProperty(Key key) {
        return properties.get(key) != null;
    }

    /**
     * Get the value of an integer-valued property, returning a default value if the property
     * has not been set.
     * @param key the key identifying the required property
     * @param defaultValue the value to return if no value has been supplied for the property
     * @return the value of the property, or its default
     */
    private int getIntegerProperty(Key key, int defaultValue) {
        Object value = getProperty(key);
        if (value == null) {
            return defaultValue;
        } else {
            return (int)value;
        }
    }

    /**
     * Get the value of a boolean-valued property, returning a default value if the property
     * has not been set.
     *
     * @param key          the key identifying the required property
     * @param defaultValue the value to return if no value has been supplied for the property
     * @return the value of the property, or its default
     */

    private boolean getBooleanProperty(Key key, boolean defaultValue) {
        Object value = getProperty(key);
        if (value == null) {
            return defaultValue;
        } else {
            return (boolean) value;
        }
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
     * @param other the set of {@code ParseOptions} properties to be merged in.
     * @return the merged ParseOptions
     */

    public ParseOptions merge(ParseOptions other) {
        ParseOptions result = this;
        if (other.getDTDValidationMode() != Validation.DEFAULT) {
            result = result.withDTDValidationMode(other.getDTDValidationMode());
        }
        if (other.getSchemaValidationMode() != Validation.DEFAULT) {
            result = result.withSchemaValidationMode(other.getSchemaValidationMode());
        }
        result = result.withPropertyIfNotNull(Key.INVALIDITY_HANDLER, other.getInvalidityHandler());
        result = result.withPropertyIfNotNull(Key.TOP_LEVEL_ELEMENT, other.getTopLevelElement());
        result = result.withPropertyIfNotNull(Key.TOP_LEVEL_TYPE, other.getTopLevelType());
        result = result.withPropertyIfNotNull(Key.SPACE_STRIPPING_RULE, other.getSpaceStrippingRule());
        result = result.withPropertyIfNotNull(Key.TREE_MODEL, other.getTreeModel());


        if (other.hasProperty(Key.LINE_NUMBERING)) {
            result = result.withLineNumbering(other.isLineNumbering());
        }
        if (other.isPleaseCloseAfterUse()) {
            result = result.withPleaseCloseAfterUse(true);
        }

        if (other.getFilters() != null) {
            for (FilterFactory ff : other.getFilters()) {
                 result = result.withFilter(ff);
            }
        }
        if (other.getParserFeatures() != null) {
            for (Map.Entry<String, Boolean> entry : other.getParserFeatures().entrySet()) {
                 result = result.withParserFeature(entry.getKey(), entry.getValue());
            }
        }
        if (other.getParserProperties() != null) {
            for (Map.Entry<String, Object> entry : other.getParserProperties().entrySet()) {
                result = result.withParserProperty(entry.getKey(), entry.getValue());
            }
        }

        if (!other.isExpandAttributeDefaults()) {
            // expand defaults unless the other options says don't
            result = result.withExpandAttributeDefaults(false);
        }
        if (!other.isUseXsiSchemaLocation()) {
            result = result.withUseXsiSchemaLocation(false);
        }
        if (other.isAddCommentsAfterValidationErrors()) {
            // add comments if either set of options requests it
            result = result.withUseXsiSchemaLocation(true);
        }
        result = result.withValidationErrorLimit(
                java.lang.Math.min(this.getValidationErrorLimit(), other.getValidationErrorLimit()));

        result = result.withPropertyIfNotNull(Key.XML_READER, other.getXMLReader());
        result = result.withPropertyIfNotNull(Key.ERROR_REPORTER, other.getErrorReporter());

        return result;
    }

    private ParseOptions withPropertyIfNotNull(Key key, Object value) {
        if (value != null) {
            return withProperty(key, value);
        }
        return this;
    }

    /**
     * Merge settings from the Configuration object into these parseOptions
     *
     * @param config the Configuration. Settings from the Configuration are
     *               used only where no setting is present in this ParseOptions object
     */

    public ParseOptions applyDefaults(Configuration config) {
        ParseOptions result = this;
        if (getDTDValidationMode() == Validation.DEFAULT) {
            result = result.withDTDValidationMode(config.isValidation() ? Validation.STRICT : Validation.SKIP);
        }
        if (getSchemaValidationMode() == Validation.DEFAULT) {
            result = result.withSchemaValidationMode(config.getSchemaValidationMode());
        }
        if (getModel() == null) {
            result = result.withModel(TreeModel.getTreeModel(config.getTreeModel()));
        }
        if (getSpaceStrippingRule() == null) {
            result = result.withSpaceStrippingRule(config.getParseOptions().getSpaceStrippingRule());
        }
        if (getProperty(Key.LINE_NUMBERING) == null) {
            result = result.withProperty(Key.LINE_NUMBERING, config.isLineNumbering());
        }

        if (getProperty(Key.ERROR_REPORTER) == null) {
            result = result.withErrorReporter(config.makeErrorReporter());
        }

        return result;
    }

    /**
     * Add a filter to the list of filters to be applied to the raw input
     *
     * <p>User-supplied filters are applied to the input stream after
     * applying any system-defined filters such as the whitespace stripper
     * and the schema validator.</p>
     * 
     * <p>Example: {@code withFilter(receiver -> new MyFilter(receiver)}, where
     * <code>MyFilter</code> extends {@link net.sf.saxon.event.ProxyReceiver}</p>
     *
     * @param filterFactory the filterFactory to be added
     */

    public ParseOptions withFilter(FilterFactory filterFactory) {
        List<FilterFactory> list = getFilters();
        if (list == null) {
            list = new ArrayList<>(2);
        } else {
            list = new ArrayList<>(list); // to keep it immutable; it's not going to be a long list
        }
        list.add(filterFactory);
        return withProperty(Key.FILTERS, list);
    }

    /**
     * Get the list of filters to be applied to the input. Returns null if there are no filters.
     *
     * @return the list of filters, if there are any
     */

    /*@Nullable*/
    public List<FilterFactory> getFilters() {
        return (List<FilterFactory>)getProperty(Key.FILTERS);
    }

    /**
     * Get the space-stripping action to be applied to the source document
     *
     * @return the space stripping rule to be used
     */

    public SpaceStrippingRule getSpaceStrippingRule() {
        return (SpaceStrippingRule)getProperty(Key.SPACE_STRIPPING_RULE);
    }

    /**
     * Set the space-stripping action to be applied to the source document
     *
     * @param rule space stripping rule to be used
     */

    public ParseOptions withSpaceStrippingRule(SpaceStrippingRule rule) {
        return withProperty(Key.SPACE_STRIPPING_RULE, rule);
    }

    /**
     * Set the tree model to use. Default is the tiny tree
     *
     * @param model typically {@link net.sf.saxon.event.Builder#TINY_TREE},
     *              {@link net.sf.saxon.event.Builder#LINKED_TREE} or {@link net.sf.saxon.event.Builder#TINY_TREE_CONDENSED}
     */


    public ParseOptions withTreeModel(int model) {
        return withProperty(Key.TREE_MODEL, model);
    }

    /**
     * Add a parser feature to a map, which will be applied to the XML parser later
     *
     * @param uri   The features as a URIs
     * @param value The value given to the feature as boolean
     */
    public ParseOptions withParserFeature(String uri, boolean value) {
        Map<String, Boolean> parserFeatures = (Map<String, Boolean>) getProperty(Key.PARSER_FEATURES);
        Map<String, Boolean> parserFeatures2;
        if (parserFeatures == null) {
            parserFeatures2 = new HashMap<>(4);
        } else {
            parserFeatures2 = new HashMap<>(parserFeatures);
        }
        Boolean old = parserFeatures2.put(uri, value);

        return old != null && old == value ? this : withProperty(Key.PARSER_FEATURES, parserFeatures2);
    }

    /**
     * Add a parser property to a map, which is applied to the XML parser later
     *
     * @param uri   The properties as a URIs
     * @param value The value given to the properties as a string
     */
    public ParseOptions withParserProperty(String uri, Object value) {
        Map<String, Object> parserProperties = (Map<String, Object>) getProperty(Key.PARSER_PROPERTIES);
        Map<String, Object> parserProperties2;
        if (parserProperties == null) {
            parserProperties2 = new HashMap<>(4);
        } else {
            parserProperties2 = new HashMap<>(parserProperties);
        }
        Object old;
        if (value != null) {
            old = parserProperties2.put(uri, value);
        } else {
            old = parserProperties2.remove(uri);
        }
        return (old != null && old.equals(value)) ? this : withProperty(Key.PARSER_PROPERTIES, parserProperties2);
    }

    /**
     * Get a particular parser feature added
     *
     * @param uri The feature name as a URIs
     * @return The feature value as boolean (returns false if the feature has not been set, or
     * if it has been set to false)
     */
    public boolean hasParserFeature(String uri) {
        Map<String, Boolean> parserFeatures = (Map<String, Boolean>) getProperty(Key.PARSER_FEATURES);
        if (parserFeatures == null) {
            return false;
        }
        Boolean value = parserFeatures.get(uri);
        return value != null && value;
    }

    /**
     * Ask if a particular parser feature has been set (either to true or false)
     *
     * @param uri The feature name as a URIs
     * @return true if the feature has been set
     */
    public boolean isParserFeatureSet(String uri) {
        Map<String, Boolean> parserFeatures = (Map<String, Boolean>) getProperty(Key.PARSER_FEATURES);
        if (parserFeatures == null) {
            return false;
        }
        Boolean value = parserFeatures.get(uri);
        return value != null;
    }

    /**
     * Get a particular parser property added
     *
     * @param name The properties as a URIs
     * @return The property value (which may be any object), or null if the property has not been set
     */
    public Object getParserProperty(String name) {
        Map<String, Object> parserProperties = (Map<String, Object>) getProperty(Key.PARSER_PROPERTIES);
        if (parserProperties == null) {
            return null;
        } else {
            return parserProperties.get(name);
        }
    }

    /**
     * Get all the parser features added
     *
     * @return A map of (feature, value) pairs
     */
    public Map<String, Boolean> getParserFeatures() {
        Map<String, Boolean> parserFeatures = (Map<String, Boolean>) getProperty(Key.PARSER_FEATURES);
        if (parserFeatures == null) {
            return Collections.emptyMap();
        } else {
            return parserFeatures;
        }
    }

    /**
     * Get all the parser properties added
     *
     * @return A map of (feature, string) pairs
     */
    public Map<String, Object> getParserProperties() {
        Map<String, Object> parserProperties = (Map<String, Object>)getProperty(Key.PARSER_PROPERTIES);
        if (parserProperties == null) {
            return Collections.emptyMap();
        } else {
            return parserProperties;
        }
    }

    /**
     * Get the tree model that will be used.
     *
     * @return typically {@link net.sf.saxon.event.Builder#TINY_TREE}, {@link net.sf.saxon.event.Builder#LINKED_TREE},
     * or {@link net.sf.saxon.event.Builder#TINY_TREE_CONDENSED},
     * or {@link net.sf.saxon.event.Builder#UNSPECIFIED_TREE_MODEL} if no call on setTreeModel() has been made
     */

    public int getTreeModel() {
        TreeModel model = getModel();
        if (model == null) {
            return Builder.UNSPECIFIED_TREE_MODEL;
        }
        return model.getSymbolicValue();
    }

    /**
     * Set the tree model to use. Default is the tiny tree
     *
     * @param model typically one of the constants {@link net.sf.saxon.om.TreeModel#TINY_TREE},
     *              {@link TreeModel#TINY_TREE_CONDENSED}, or {@link TreeModel#LINKED_TREE}. However, in principle
     *              a user-defined tree model can be used.
     * @since 9.2
     */

    public ParseOptions withModel(TreeModel model) {
        return withProperty(Key.MODEL, model);
    }

    /**
     * Get the tree model that will be used.
     *
     * @return typically one of the constants {@link net.sf.saxon.om.TreeModel#TINY_TREE},
     * {@link TreeModel#TINY_TREE_CONDENSED}, or {@link TreeModel#LINKED_TREE}. However, in principle
     * a user-defined tree model can be used.
     */

    public TreeModel getModel() {
        TreeModel treeModel = (TreeModel)getProperty(Key.MODEL);
        return treeModel == null ? TreeModel.TINY_TREE : treeModel;
    }


    /**
     * Set whether or not schema validation of this source is required
     *
     * @param option one of {@link Validation#STRICT},
     *               {@link Validation#LAX}, {@link Validation#STRIP},
     *               {@link Validation#PRESERVE}, {@link Validation#DEFAULT}
     */

    public ParseOptions withSchemaValidationMode(int option) {
        return withProperty(Key.SCHEMA_VALIDATION, option);
    }

    /**
     * Set the schema to be used for validation.
     *
     * @return the schema to be used for validation, assuming validation has been requested.
     * Returns null if validation is not requested.
     */

    public ParseOptions withSchema(Schema schema) {
        return withProperty(Key.SCHEMA, schema);
    }

    /**
     * Get the schema to be used for validation.
     * @return the schema to be used for validation, assuming validation has been requested.
     * Returns null if validation is not requested.
     */

    public Schema getSchema() {
        return (Schema)getProperty(Key.SCHEMA);
    }

    /**
     * Get whether or not schema validation of this source is required
     *
     * @return the validation mode requested, or {@link Validation#DEFAULT}
     * to use the default validation mode from the Configuration.
     */

    public int getSchemaValidationMode() {
        return getIntegerProperty(Key.SCHEMA_VALIDATION, Validation.DEFAULT);
    }

    /**
     * Set whether to expand default attributes defined in a DTD or schema.
     * By default, default attribute values are expanded
     *
     * @param expand true if missing attribute values are to take the default value
     *               supplied in a DTD or schema, false if they are to be left as absent
     */


    public ParseOptions withExpandAttributeDefaults(boolean expand) {
        return withProperty(Key.EXPAND_ATTRIBUTE_DEFAULTS, expand);
    }

    /**
     * Ask whether to expand default attributes defined in a DTD or schema.
     * By default, default attribute values are expanded
     *
     * @return true if missing attribute values are to take the default value
     * supplied in a DTD or schema, false if they are to be left as absent
     */

    public boolean isExpandAttributeDefaults() {
        return getBooleanProperty(Key.EXPAND_ATTRIBUTE_DEFAULTS, true);
    }

    /**
     * Set the name of the top-level element for validation.
     * If a top-level element is set then the document
     * being validated must have this as its outermost element
     *
     * @param elementName the QName of the required top-level element, or null to unset the value
     */

    public ParseOptions withTopLevelElement(StructuredQName elementName) {
        return withProperty(Key.TOP_LEVEL_ELEMENT, elementName);
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
        return (StructuredQName)getProperty(Key.TOP_LEVEL_ELEMENT);
    }

    /**
     * Set the type of the top-level element for validation.
     * If this is set then the document element is validated against this type
     *
     * @param type the schema type required for the document element, or null to unset the value
     */

    public ParseOptions withTopLevelType(SchemaType type) {
        return withProperty(Key.TOP_LEVEL_TYPE, type);
    }

    /**
     * Get the type of the document element for validation.
     * If this is set then the document element of the document
     * being validated must have this type
     *
     * @return the type of the required top-level element, or null if no value is set
     */

    public SchemaType getTopLevelType() {
        return (SchemaType)getProperty(Key.TOP_LEVEL_TYPE);
    }

    /**
     * Set whether or not to use the xsi:schemaLocation and xsi:noNamespaceSchemaLocation attributes
     * in an instance document to locate a schema for validation. Note, these attribute are only used
     * if validation is requested.
     *
     * @param use true if these attributes are to be used, false if they are to be ignored
     */

    public ParseOptions withUseXsiSchemaLocation(boolean use) {
        return withProperty(Key.USE_XSI_SCHEMA_LOCATION, use);
    }

    /**
     * Ask whether or not to use the xsi:schemaLocation and xsi:noNamespaceSchemaLocation attributes
     * in an instance document to locate a schema for validation. Note, these attribute are only used
     * if validation is requested.
     *
     * @return true (the default) if these attributes are to be used, false if they are to be ignored
     */

    public boolean isUseXsiSchemaLocation() {
        return getBooleanProperty(Key.USE_XSI_SCHEMA_LOCATION, true);
    }

    /**
     * Get the limit on the number of errors reported before schema validation is abandoned. Default
     * is unlimited (Integer.MAX_VALUE)
     *
     * @return the limit on the number of errors
     */

    public int getValidationErrorLimit() {
        return getIntegerProperty(Key.VALIDATION_ERROR_LIMIT, Integer.MAX_VALUE);
    }

    /**
     * Set a limit on the number of errors reported before schema validation is abandoned. Default
     * is unlimited (Integer.MAX_VALUE). If set to one, validation is terminated as soon as a single
     * validation error is detected.
     *
     * @param validationErrorLimit the limit on the number of errors
     */

    public ParseOptions withValidationErrorLimit(int validationErrorLimit) {
        return withProperty(Key.VALIDATION_ERROR_LIMIT, validationErrorLimit);
    }

    /**
     * Set whether or not DTD validation of this source is required
     *
     * @param option one of {@link Validation#STRICT},  {@link Validation#LAX},
     *               {@link Validation#STRIP}, {@link Validation#DEFAULT}.
     *               <p>The value {@link Validation#LAX} indicates that DTD validation is
     *               requested, but validation failures are treated as warnings only.</p>
     */

    @CSharpReplaceBody(code="return withProperty(Key.DTD_VALIDATION, option);")
    public ParseOptions withDTDValidationMode(int option) {
        return
                withParserFeature("http://xml.org/sax/features/validation",
                          option == Validation.STRICT || option == Validation.LAX).
                withProperty(Key.DTD_VALIDATION, option);
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
        return getIntegerProperty(Key.DTD_VALIDATION, Validation.SKIP);
    }

    /**
     * Say that statistics of component usage are maintained during schema validation, and indicate where
     * they should be sent
     *
     * @param recipient the agent to be notified of the validation statistics on completion of the
     *                  validation episode, May be set to null if no agent is to be notified.
     */

    public ParseOptions withValidationStatisticsRecipient(ValidationStatisticsRecipient recipient) {
        return withProperty(Key.VALIDATION_STATISTICS_RECIPIENT, recipient);
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
        return (ValidationStatisticsRecipient)getProperty(Key.VALIDATION_STATISTICS_RECIPIENT);
    }

    /**
     * Set whether line numbers are to be maintained in the constructed document
     *
     * @param lineNumbering true if line numbers are to be maintained
     */

    public ParseOptions withLineNumbering(boolean lineNumbering) {
        return withProperty(Key.LINE_NUMBERING, lineNumbering);
    }

    /**
     * Get whether line numbers are to be maintained in the constructed document
     *
     * @return true if line numbers are maintained
     */

    public boolean isLineNumbering() {
        return getBooleanProperty(Key.LINE_NUMBERING, false);
    }

    /**
     * Determine whether setLineNumbering() has been called
     *
     * @return true if setLineNumbering() has been called
     */

    public boolean isLineNumberingSet() {
        return hasProperty(Key.LINE_NUMBERING);
    }

    /**
     * Set the SAX parser (XMLReader) to be used. This method must be used with care, because
     * an XMLReader is not thread-safe. If there is any chance that this ParseOptions object will
     * be used in multiple threads, then this property should not be set. Instead, set the XMLReaderMaker
     * property, which allows a new parser to be created each time it is needed.
     *
     * @param parser the SAX parser
     */

    public ParseOptions withXMLReader(XMLReader parser) {
        return withProperty(Key.XML_READER, parser);
    }

    /**
     * Get the SAX parser (XMLReader) to be used.
     * @return the parser
     */

    /*@Nullable*/
    public XMLReader getXMLReader() {
        return (XMLReader)getProperty(Key.XML_READER);
    }

    /**
     * Set the parser factory class to be used.
     *
     * @param parserMaker a factory object that delivers an XMLReader on demand
     */

    public ParseOptions withXMLReaderMaker(Maker<XMLReader> parserMaker) {
        return withProperty(Key.XML_READER_MAKER, parserMaker);
    }

    /**
     * Get the parser factory class to be used
     *
     * @return a factory object that delivers an XMLReader on demand, or null if none has been set
     */

    /*@Nullable*/
    public Maker<XMLReader> getXMLReaderMaker() {
        return (Maker<XMLReader>)getProperty(Key.XML_READER_MAKER);
    }

    /**
     * Obtain an XMLReader (parser), by making one using the XMLReaderMaker if available, or by
     * returning the registered XMLReader if available, or failing that, return null
     */

    public XMLReader obtainXMLReader() throws XPathException {
        Maker<XMLReader> factory = getXMLReaderMaker();
        if (factory != null) {
            return factory.make();
        } else {
            return getXMLReader();
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

    public ParseOptions withEntityResolver(/*@Nullable*/ EntityResolver resolver) {
        return withProperty(Key.ENTITY_RESOLVER, resolver);
    }

    /**
     * Get the EntityResolver that will be used when parsing
     *
     * @return the EntityResolver, if one has been set using {@link #withEntityResolver},
     * otherwise null.
     */

    /*@Nullable*/
    public EntityResolver getEntityResolver() {
        return (EntityResolver)getProperty(Key.ENTITY_RESOLVER);
    }

    /**
     * Set an ErrorHandler to be used when parsing. Note that this will not be used if an XMLReader
     * has been supplied (in that case, the XMLReader should be initialized with the ErrorHandler
     * already set.)
     *
     * @param handler the ErrorHandler to be used, or null to indicate that no ErrorHandler is to be used.
     */

    public ParseOptions withErrorHandler(ErrorHandler handler) {
        return withProperty(Key.ERROR_HANDLER, handler);
    }

    /**
     * Get the ErrorHandler that will be used when parsing
     *
     * @return the ErrorHandler, if one has been set using {@link #withErrorHandler},
     * otherwise null.
     */

    /*@Nullable*/
    public ErrorHandler getErrorHandler() {
        return (ErrorHandler)getProperty(Key.ERROR_HANDLER);
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
    @CSharpReplaceBody(code="if (state) throw new System.NotSupportedException(\"XInclude is not supported in SaxonCS\"); else return this;")
    public ParseOptions withXIncludeAware(boolean state) {
        return withParserFeature("http://apache.org/xml/features/xinclude", state);
    }

    /**
     * <p>Determine whether setXIncludeAware() has been called.</p>
     *
     * @return true if setXIncludeAware() has been called
     */

    @CSharpReplaceBody(code = "return false;")
    public boolean isXIncludeAwareSet() {
        return isParserFeatureSet("http://apache.org/xml/features/xinclude");
    }

    /**
     * <p>Get state of XInclude processing.</p>
     *
     * @return current state of XInclude processing. Default value is false.
     */

    @CSharpReplaceBody(code = "return false;")
    public boolean isXIncludeAware() {
        return hasParserFeature("http://apache.org/xml/features/xinclude");
    }


    /**
     * Set an ErrorReporter to be used when parsing
     *
     * @param reporter the ErrorReporter to be used; or null, to indicate that the
     *                 standard ErrorReporter is to be used.
     */

    public ParseOptions withErrorReporter(/*@Nullable*/ ErrorReporter reporter) {
        if (reporter == null) {
            reporter = new StandardErrorReporter();
        }
        return withProperty(Key.ERROR_REPORTER, reporter);
    }

    /**
     * Get the ErrorReporter that will be used when parsing
     *
     * @return the ErrorReporter, if one has been set using {@link #withErrorReporter},
     * otherwise null.
     */

    /*@Nullable*/
    public ErrorReporter getErrorReporter() {
        return (ErrorReporter)getProperty(Key.ERROR_REPORTER);
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

    public ParseOptions withContinueAfterValidationErrors(boolean keepGoing) {
        return withProperty(Key.CONTINUE_AFTER_VALIDATION_ERRORS, keepGoing);
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
        return getBooleanProperty(Key.CONTINUE_AFTER_VALIDATION_ERRORS, false);
    }

    /**
     * Say that on validation errors, messages explaining the error should (where possible)
     * be written as comments in the validated source document. This option is only relevant when
     * processing continues after a validation error
     *
     * @param addComments true if comments should be added
     * @since 9.3. Default is now false; in previous releases this option was always on.
     */

    public ParseOptions withAddCommentsAfterValidationErrors(boolean addComments) {
        return withProperty(Key.ADD_COMMENTS_AFTER_VALIDATION_ERRORS, addComments);
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
        return getBooleanProperty(Key.ADD_COMMENTS_AFTER_VALIDATION_ERRORS, false);
    }

    /**
     * Set the validation parameters. These are the values of variables declared in the schema
     * using the saxon:param extension, and referenced in XSD assertions (or CTA expressions)
     * associated with user-defined types
     *
     * @param params the validation parameters
     */

    public ParseOptions withValidationParams(ValidationParams params) {
        return withProperty(Key.VALIDATION_PARAMS, params);
    }

    /**
     * Get the validation parameters. These are the values of variables declared in the schema
     * using the saxon:param extension, and referenced in XSD assertions (or CTA expressions)
     * associated with user-defined types
     *
     * @return the validation parameters
     */

    public ValidationParams getValidationParams() {
        return (ValidationParams)getProperty(Key.VALIDATION_PARAMS);
    }


    /**
     * Say whether to check elements and attributes of type xs:ENTITY (or xs:ENTITIES)
     * against the unparsed entities declared in the document's DTD. This is normally
     * true when performing standalone schema validation, false when invoking validation
     * from XSLT or XQuery.
     *
     * @param check true if entities are to be checked, false otherwise
     */

    public ParseOptions withCheckEntityReferences(boolean check) {
        return withProperty(Key.CHECK_ENTITY_REFERENCES, check);
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
        return getBooleanProperty(Key.CHECK_ENTITY_REFERENCES, false);
    }

    /**
     * Ask whether the document (or collection) should be stable, that is, if repeated attempts to dereference
     * the same URI are guaranteed to return the same result. By default, documents and collections are stable.
     *
     * @return true if the document or collection is stable
     */

    public boolean isStable() {
        return getBooleanProperty(Key.STABLE, true);
    }

    /**
     * Say whether the document (or collection) should be stable, that is, if repeated attempts to dereference
     * the same URI are guaranteed to return the same result. By default, documents and collections are stable.
     *
     * @param stable true if the document or collection is stable
     */

    public ParseOptions withStable(boolean stable) {
        return withProperty(Key.STABLE, stable);
    }

    /**
     * Ask whether the document (or collection) should be trusted, that is, if it is allowed
     * to reference external entities (including external DTDs). By default, at this level of the system,
     * documents and collections are trusted.
     *
     * @return true if the document or collection is trusted
     */

    public boolean isTrusted() {
        return getBooleanProperty(Key.TRUSTED, false);
    }

    /**
     * Say whether the document (or collection) should be trusted, that is, if it is allowed
     * to reference external entities (including external DTDs). By default, at this level of the system,
     * documents and collections are trusted.
     *
     * @param trusted true if the document or collection is trusted
     */

    public ParseOptions withTrusted(boolean trusted) {
        return withProperty(Key.TRUSTED, trusted);
    }

    /**
     * Get the callback for reporting validation errors
     *
     * @return the registered InvalidityHandler
     */

    public InvalidityHandler getInvalidityHandler() {
        return (InvalidityHandler)getProperty(Key.INVALIDITY_HANDLER);
    }

    /**
     * Set the callback for reporting validation errors
     *
     * @param invalidityHandler the InvalidityHandler to be used for reporting validation failures
     */

    public ParseOptions withInvalidityHandler(InvalidityHandler invalidityHandler) {
        return withProperty(Key.INVALIDITY_HANDLER, invalidityHandler);
    }

    /**
     * Set the list of XSLT 3.0 accumulators that apply to this tree.
     *
     * @param accumulators the accumulators that apply; or null if all accumulators apply.
     *                     (Note, this is not the same as the meaning of #all in the
     *                     use-accumulators attribute, which refers to all accumulators
     *                     declared in a given package).
     */

    public ParseOptions withApplicableAccumulators(Set<? extends Accumulator> accumulators) {
        return withProperty(Key.APPLICABLE_ACCUMULATORS, accumulators);
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
        return (Set<? extends Accumulator>)getProperty(Key.APPLICABLE_ACCUMULATORS);
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

    public ParseOptions withPleaseCloseAfterUse(boolean close) {
        return withProperty(Key.PLEASE_CLOSE, close);
    }

    /**
     * Determine whether or not the user of this Source is encouraged to close it as soon as reading is
     * finished.
     *
     * @return true if the source should be closed as soon as it has been consumed
     */

    public boolean isPleaseCloseAfterUse() {
        return getBooleanProperty(Key.PLEASE_CLOSE, false);
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

    /**
     * Returns a string representation of the object.
     * @return a string representation of the object.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (TrieKVP<Key, Object> kvp : properties) {
            sb.append(kvp.key);
            sb.append('=');
            sb.append(kvp.value);
            sb.append(' ');
        }
        return sb.toString();
    }
}

