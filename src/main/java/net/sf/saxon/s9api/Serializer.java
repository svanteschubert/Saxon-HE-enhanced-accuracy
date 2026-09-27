////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.*;
import net.sf.saxon.lib.OutputURIResolver;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.lib.SerializerFactory;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.query.QueryResult;
import net.sf.saxon.serialize.CharacterMap;
import net.sf.saxon.serialize.CharacterMapIndex;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharp;
import net.sf.saxon.transpile.CSharpModifiers;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * A <code>Serializer</code> takes an XDM value and outputs a textual representation of the content.
 * Different serialization methods are defined to support different output formats, for example the XML
 * method outputs the content as lexical XML, while the JSON method outputs the content as lexical JSON.
 * <p>To construct a <code>Serializer</code>, use the factory method {@link Processor#newSerializer(File)}.</p>
 * <p><i>Note that this is serialization in the sense of the W3C XSLT and XQuery specifications.
 * This has nothing to do with the serialization of Java objects, or the {@link java.io.Serializable}
 * interface.</i></p>
 * <p>The serialization may be influenced by a number of serialization parameters. A parameter has a name,
 * which is an instance of {@link Serializer.Property}, and a value, which is expressed as a string.
 * The effect of most of the properties is as described in the W3C specification
 * <a href="http://www.w3.org/TR/xslt-xquery-serialization/">XSLT 3.0 and XQuery 3.1 Serialization</a>.
 * Saxon supports all the serialization parameters defined in that specification, together with some
 * additional parameters, whose property names are prefixed "SAXON_".</p>
 * <p>A <code>Serializer</code> may also be created from an {@link Xslt30Transformer} using the method
 * {@link Xslt30Transformer#newSerializer()} or one of its variants. In this case the serializer
 * is initialized with properties taken from the default output declaration in the stylesheet.</p>
 * <p>Serialization parameters set explicitly on the {@code Serializer} take precedence over any
 * serialization parameters supplied when {@link #getReceiver} is called. The {@link #getReceiver} method
 * is supplied with parameters derived from the query or stylesheet, which are merged with the parameters
 * set on this {@code Serializer}.</p>
 * <p>Any serialization parameters that are not given an explicit value using one of these mechanisms
 * are defaulted using the rules for the XSLT 3.0 <code>xsl:output</code> declaration. In particular,
 * this means that the default serialization method (when serializing an XDM document) depends on the
 * name of the first element encountered in the document.</p>
 * <p><i>Changed in 9.9 so the {@code Serializer} now only holds one set of output properties. Additional
 * serialization properties can be supplied
 * when {@link #getReceiver(PipelineConfiguration, SerializationProperties)}
 * is called, but these are not stored within the {@code Serializer} itself.</i></p>
 */
@SuppressWarnings({"ForeachStatement"})
@CSharpModifiers(code = {"internal"})
public class Serializer extends AbstractDestination {

    private Processor processor; // never null
    private final Map<String, String> properties = new HashMap<>(10);  // Property name is in Clark format {uri}local
    private final StreamResult result = new StreamResult();
    private CharacterMapIndex characterMapIndex = null;
    private boolean mustClose = false;

    // Index of permitted property names

    private static final Map<String, Property> standardProperties = new HashMap<>();

    static {
        final Property[] propertyValues = Property.values();
        for (Property p : propertyValues) {
            standardProperties.put(propertyClarkName(p), p);
        }
    }

    /**
     * Enumeration class defining the permitted serialization properties.
     * Names prefixed SAXON are extension properties defined at
     * https://www.saxonica.com/documentation/index.html#!extensions/output-extras/serialization-parameters
     */

    public enum Property {
        /**
         * Set to "yes" or "no" to indicate whether duplicate names are to be allowed
         * in JSON output (the main reason to set this to "yes" is to avoid the cost of
         * checking for duplicates when it is known there will be none)
         */
        ALLOW_DUPLICATE_NAMES(SaxonOutputKeys.ALLOW_DUPLICATE_NAMES),

        /**
         * Set to "yes" or "no" to indicate whether a byte order mark is to be written
         */
        BYTE_ORDER_MARK(SaxonOutputKeys.BYTE_ORDER_MARK),

        /**
         * Set to "yes" or "no" to indicate whether a byte order mark is to be written
         */
        CANONICAL(SaxonOutputKeys.CANONICAL),

        /**
         * Space-separated list of QNames (in Clark form or EQName form) of elements
         * whose content is to be wrapped in CDATA sections
         */
        CDATA_SECTION_ELEMENTS(OutputKeys.CDATA_SECTION_ELEMENTS),

        /**
         * Set to any string to indicate that the output is to include a DOCTYPE declaration with this public id
         */
        DOCTYPE_PUBLIC(OutputKeys.DOCTYPE_PUBLIC),

        /**
         * Set to any string to indicate that the output is to include a DOCTYPE declaration with this system id
         */
        DOCTYPE_SYSTEM(OutputKeys.DOCTYPE_SYSTEM),

        /**
         * Character encoding of output stream
         */
        ENCODING(OutputKeys.ENCODING),

        /**
         * Set to "yes" or "no" to indicate whether a solidus (forwards slash) is to be escaped with a backslash
         * in JSON output. (The backslash is only really needed when the JSON is to be embedded in HTML, but the
         * values is "yes" by default.) New in 4.0
         */
        ESCAPE_SOLIDUS(SaxonOutputKeys.ESCAPE_SOLIDUS),

        /**
         * Set to "yes" or "no" to indicate (for HTML and XHTML) whether URI-valued attributes should be
         * percent-encoded
         */
        ESCAPE_URI_ATTRIBUTES(SaxonOutputKeys.ESCAPE_URI_ATTRIBUTES),

        /**
         * HTML version number
         */
        HTML_VERSION(SaxonOutputKeys.HTML_VERSION),

        /**
         * For HTML and XHTML, set to "yes" or "no" to indicate whether a &lt;meta&gt; element is to be
         * written to indicate the content type and encoding
         */
        INCLUDE_CONTENT_TYPE(SaxonOutputKeys.INCLUDE_CONTENT_TYPE),

        /**
         * Set to "yes" or "no" to indicate whether indentation is required
         */
        INDENT(OutputKeys.INDENT),

        /**
         * Set to a string that will be used to separate adjacent items in an XQuery result sequence
         */
        ITEM_SEPARATOR(SaxonOutputKeys.ITEM_SEPARATOR),

        /**
         * Indicates output in JSON Lines format - one JSON record per line of the output file, newline-separated
         */
        JSON_LINES(SaxonOutputKeys.JSON_LINES),

        /**
         * Output method to be used when nodes are embedded in maps or arrays output using the JSON output method
         */
        JSON_NODE_OUTPUT_METHOD(SaxonOutputKeys.JSON_NODE_OUTPUT_METHOD),

        /**
         * Set to indicate the media type (MIME type) of the output
         */
        MEDIA_TYPE(OutputKeys.MEDIA_TYPE),

        /**
         * Serialization method: xml, html, xhtml, text, json, adaptive; or <code>Q{uri}local</code>
         */
        METHOD(OutputKeys.METHOD),

        /**
         * Set to the name of a Unicode normalization form: "NFC", "NFD", "NFKC", or "NFKD", or
         * "none" to indicate no normalization
         */
        NORMALIZATION_FORM(SaxonOutputKeys.NORMALIZATION_FORM),

        /**
         * Set to "yes" if the XML declaration is to be omitted from the output file
         */
        OMIT_XML_DECLARATION(OutputKeys.OMIT_XML_DECLARATION),

        /**
         * Set to "yes", "no", or "omit" to indicate the required value of the standalone attribute
         * in the XML declaration of the output file
         */
        STANDALONE(OutputKeys.STANDALONE),

        /**
         * Set to a space-separated list of element names (in Clark or EQName notation),
         * identifying elements (such as <code>para</code>) within which no content is to be indented.
         * This is typically because the element contains mixed content in which whitespace is significant.
         * <p>
         * This was originally a Saxon extension to the W3C specifications, but it then became
         * a standard serialization attribute. In previous versions of this API it was therefore
         * named {@code SAXON_SUPPRESS_INDENTATION}
         */
        SUPPRESS_INDENTATION(SaxonOutputKeys.SUPPRESS_INDENTATION),

        /**
         * Set to "yes" or "no" to indicate (for XML 1.1) whether namespaces that go out of scope should
         * be undeclared
         */
        UNDECLARE_PREFIXES(SaxonOutputKeys.UNDECLARE_PREFIXES),

        /**
         * List of names of character maps to be used. Character maps can only be specified in an XSLT
         * stylesheet. Supplied as a space-separated list of QNames in Clark or EQName format.
         */
        USE_CHARACTER_MAPS(SaxonOutputKeys.USE_CHARACTER_MAPS),

        /**
         * Version of output method, for example "1.0" or "1.1" for XML
         */
        VERSION(OutputKeys.VERSION),

        /**
         * Build-tree option (XSLT only), "yes" or "no"
         */
        BUILD_TREE(SaxonOutputKeys.BUILD_TREE),

        /**
         * Saxon extension: set to a space-separated list of attribute names, in Clark or EQName notation,
         * followed optionally by "*" and then another space-separated list of attribute names
         * indicating that attributes present in the list should be serialized in the order
         * indicated: specific attributes listed before the "*" come first in the order specified;
         * then attributes not present in the list (these are sorted first by namespace, then by local name),
         * and finally specific attributes listed after the "*", in the order specified.
         */
        SAXON_ATTRIBUTE_ORDER(SaxonOutputKeys.ATTRIBUTE_ORDER),

        /**
         * Saxon extension (now in 4.0): request canonical XML output. Value is "yes" or "no"
         */
        SAXON_CANONICAL(SaxonOutputKeys.CANONICAL),

        /**
         * Saxon extension to indicate how characters outside the encoding should be represented,
         * for example "hex" for hexadecimal character references, "decimal" for decimal character references
         */
        SAXON_CHARACTER_REPRESENTATION(SaxonOutputKeys.CHARACTER_REPRESENTATION),

        /**
         * Saxon extension for use in conjunction with {@code CDATA_SECTION_ELEMENTS}: when an element is
         * named in {@code CDATA_SECTION_ELEMENTS}, and this property is set, then CDATA tags are output
         * around a text node only if it includes an ampersand or less-than sign.
         */
        SAXON_CONDITIONAL_CDATA(SaxonOutputKeys.CONDITIONAL_CDATA),
            
        /**
         * Saxon extension: set to a space-separated list of element names, in Clark or EQName notation,
         * representing elements that will be preceded by an extra blank line in the output in addition
         * to normal indentation.
         */
        SAXON_DOUBLE_SPACE(SaxonOutputKeys.DOUBLE_SPACE),

        /**
         * Saxon extension: set to an integer (represented as a string) giving the number of spaces
         * by which each level of nesting should be indented. Default is 3.
         */
        SAXON_INDENT_SPACES(SaxonOutputKeys.INDENT_SPACES),

        /**
         * Saxon extension: set to a string containing the internal DTD subset, which is output "as is",
         * without any checking. The content should correspond to the rules for the <code>intSubset</code>
         * production in the XML 1.1 grammar (note that this excludes the square-bracket delimiters).
         * See also the <code>saxon:doctype</code> instruction, which allows the internal subset to be
         * constructed programmatically.
         */
        SAXON_INTERNAL_DTD_SUBSET(SaxonOutputKeys.INTERNAL_DTD_SUBSET),

        /**
         * Saxon extension: set to an integer (represented as a string) giving the desired maximum
         * length of lines when indenting. Default is 80.
         */
        SAXON_LINE_LENGTH(SaxonOutputKeys.LINE_LENGTH),

          /**
         * Saxon extension: set to any string. Indicates the sequence of characters used to represent
         * a newline in the text output method, and in newlines used for indentation in any output
         * methods that use indentation.
         */
        SAXON_NEWLINE(SaxonOutputKeys.NEWLINE),

        /**
         * Saxon extension: set to a space-separated list of strings,
         * followed optionally by "*" and then another space-separated list of strings
         * indicating that keys present in a map should be serialized in the order
         * indicated: specific keys listed before the "*" come first in the order specified;
         * then keys not present in the list (these are sorted alphabetically,
         * and finally specific keys listed after the "*", in the order specified. It is not
         * possible to include keys that contain whitespace.
         */
        SAXON_PROPERTY_ORDER (SaxonOutputKeys.PROPERTY_ORDER),

        /**
         * Saxon extension for use when writing to the text output method; this option causes the processing
         * instructions hex and b64 to be recognized as containing hexBinary or base64 data respectively.
         */
        SAXON_RECOGNIZE_BINARY(SaxonOutputKeys.RECOGNIZE_BINARY),

        /**
         * Saxon extension for use when output is sent to a SAX ContentHandler: indicates that the output
         * is required to be well-formed (exactly one top-level element, no top-level text nodes)
         */
        SAXON_REQUIRE_WELL_FORMED(SaxonOutputKeys.REQUIRE_WELL_FORMED),

        /**
         * Set to "yes" or "no" to indicate whether attribute values should be delimited where possible
         * using single quotes (apostrophes) rather than double quotes.
         */
        SAXON_SINGLE_QUOTES(SaxonOutputKeys.SINGLE_QUOTES),

        /**
         * Set to the version of the serialization specification to be used, for example
         * "3.1" or "4.0". Defaults to 3.1.
         */
        SAXON_SPEC_VERSION(SaxonOutputKeys.SPEC_VERSION),

        /**
         * Saxon extension for interfacing with debuggers; indicates that location information is
         * available for events in this output stream
         */
        SAXON_SUPPLY_SOURCE_LOCATOR(SaxonOutputKeys.SUPPLY_SOURCE_LOCATOR),

        /**
         * Saxon extension, indicates that the output of a query is to be wrapped before serialization,
         * such that each item in the result sequence is enclosed in an element indicating its type
         */
        SAXON_WRAP(SaxonOutputKeys.WRAP);

        private final String name;

        Property(String propertyName) {
            this.name = propertyName;
        }

        /**
         * Get the name of the property expressed as a QName in Clark notation.
         * The namespace will be null for standard serialization properties,
         * and will be the Saxon namespace <code>http://saxon.sf.net/</code> for Saxon extensions
         *
         * @return the name of the serialization property as a QName in Clark notation, {uri}local
         */

        @CSharpModifiers(code={"public", "override"})
        public String toString() {
            return name;
        }

        /**
         * Get the name of the property expressed as a QName.
         * The namespace will be null for standard serialization properties,
         * and will be the Saxon namespace <code>http://saxon.sf.net/</code> for Saxon extensions
         *
         * @return the name of the serialization property as a QName
         */

        public QName getQName() {
            return QName.fromClarkName(name);
        }

        public static Property get(String s) {
            for (Property p : Property.values()) {
                if (p.name.equals(s)) {
                    return p;
                }
            }
            return null;
        }


    }

    public static String propertyClarkName(Property prop) {
        String propName;
        propName = prop.toString();
        return propName;
    }

    /**
     * Create a Serializer belonging to a specific processor
     *
     * @param processor the processor associated with the Serializer. Must not be null.
     */

    protected Serializer(Processor processor) {
        setProcessor(processor);
    }

    /**
     * Set the Processor associated with this Serializer. This will be called automatically if the
     * serializer is created using one of the <code>Processor.newSerializer()</code> methods.
     * @param processor the associated Processor (must not be null)
     * @since 9.3
     */

    public void setProcessor(Processor processor) {
        this.processor = Objects.requireNonNull(processor);
    }

    /**
     * Get the Processor associated with this Serializer.
     * @return the associated Processor. Never null.
     */

    public Processor getProcessor() {
        return processor;
    }

    /**
     * Set output properties, from a {@link Properties} object. The keys in the supplied
     * {@code Properties} object are QNames in Clark format, that is "{uri}local"; any QNames
     * within the values must also be in this format.
     *
     * @param suppliedProperties the output properties to be used. These overwrite any properties
     *                           that have been individually specified using
     *                           {@link #setOutputProperty(Property, String)} setOutputProperty}
     */

    public void setOutputProperties(Properties suppliedProperties) {
        for (String name : suppliedProperties.stringPropertyNames()) {
            properties.put(name, suppliedProperties.getProperty(name));
        }
    }

    /**
     * Set output properties, from an {@link SerializationProperties} object. The keys in the contained
     * {@code Properties} object are QNames in Clark format, that is "{uri}local"; any QNames
     * within the values must also be in this format.
     * <p>The values supplied are typically those specified in the stylesheet or query. In the case of XSLT,
     * they are typically the properties associated with unnamed <code>xsl:output</code> declarations.</p>
     *
     * @param suppliedProperties the output properties to be used. These overwrite any properties
     *                           that have been individually specified using
     *                           {@link #setOutputProperty(Property, String)} setOutputProperty}
     * @since 9.9
     */

    public void setOutputProperties(SerializationProperties suppliedProperties) {
        setOutputProperties(suppliedProperties.getProperties());
        setCharacterMap(suppliedProperties.getCharacterMapIndex());
    }


    /**
     * Say whether the output stream or writer should be closed on completion
     * By default the close method closes the output stream only when the serializer created the output stream itself,
     * that is, when the destination has been supplied as a file rather than a stream.
     * <p>Note that this method causes a call on <code>close()</code>, but it does not cause a call on
     * <code>flush()</code>. With most implementations of <code>OutputStream</code>, and with all
     * conformant implementations of <code>Writer</code>, a call on <code>close()</code>
     * does an implicit <code>flush()</code>, but this cannot be guaranteed.</p>
     * @param value - if true the output stream or writer will be closed when the serialization is complete;
     *              if false it is left open.
     */

    public void setCloseOnCompletion(boolean value) {
        mustClose = value;
    }

    /**
     * Set a character map to be used; more specifically, supply a set of named character maps
     *
     * @param characterMap a set of named character maps. A character map in this set will only
     *                     be used if the name of the character map is added to the value
     *                     of the {@link Serializer.Property#USE_CHARACTER_MAPS} serialization
     *                     property. The character maps in this index are added to the existing
     *                     set of character maps known to the serializer, unless they have the
     *                     same names as existing character maps, in which case the new one
     *                     overwrites the old.
     */

    public void setCharacterMap(CharacterMapIndex characterMap) {
        CharacterMapIndex existingIndex = this.characterMapIndex;
        if (existingIndex == null || existingIndex.isEmpty()) {
            existingIndex = characterMap;
        } else if (characterMap != null && !characterMap.isEmpty() && existingIndex != characterMap) {
            // Merge the character maps
            existingIndex = existingIndex.copy();
            for (CharacterMap map : characterMap) {
                existingIndex.putCharacterMap(map.getName(), map);
            }
        }
        this.characterMapIndex = existingIndex;
    }

    /**
     * Get the character map index in use
     * @return a set of named character maps.
     */
    
    public CharacterMapIndex getCharacterMapIndex() {
        if (characterMapIndex == null) {
            characterMapIndex = new CharacterMapIndex();
        }
        return characterMapIndex;
    }

    /**
     * Set the value of a serialization property. Any existing value of the property is overridden.
     * If the supplied value is null, any existing value of the property is removed.
     * <p>Example:</p>
     * <p><code>serializer.setOutputProperty(Serializer.Property.METHOD, "xml");</code></p>
     * <p>Any serialization properties supplied via this interface take precedence over serialization
     * properties defined in the source stylesheet or query, including properties set dynamically
     * using <code>xsl:result-document</code>. However, they only affect the principal output of a transformation;
     * the serialization of secondary result documents is controlled using an {@link OutputURIResolver}.</p>
     *
     * @param property The name of the property to be set
     * @param value    The value of the property, as a string. The format is generally as defined
     *                 in the <code>xsl:output</code> declaration in XSLT: this means that boolean properties, for
     *                 example, are represented using the strings "yes" and "no". Properties whose values are QNames,
     *                 such as <code>cdata-section-elements</code> are expressed using the Clark representation of
     *                 a QName, that is "{uri}local". Multi-valued properties (again, <code>cdata-section-elements</code>
     *                 is an example) are expressed as a space-separated list.
     * @throws IllegalArgumentException if the value of the property is invalid. The property is
     *                                  validated individually; invalid combinations of properties will be detected only when the properties
     *                                  are actually used to serialize an XML event stream.
     */

    public void setOutputProperty(Property property, /*@Nullable*/ String value) {
        SerializerFactory sf = processor.getUnderlyingConfiguration().getSerializerFactory();
        try {
            value = sf.checkOutputProperty(propertyClarkName(property), value);
        } catch (XPathException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        if (value == null) {
            properties.remove(propertyClarkName(property));
        } else {
            properties.put(propertyClarkName(property), value);
        }
    }

    /**
     * Get the value of a serialization property
     *
     * @param property the name of the required property. This method only considers properties
     *                 explicitly set on this Serialized object, it does not return values
     *                 obtained from the stylesheet or query.
     * @return the value of the required property as a string, or null if the property has
     * not been given any value.
     */

    public String getOutputProperty(Property property) {
        return properties.get(propertyClarkName(property));
    }

    /**
     * Set the value of a serialization property. Any existing value of the property is overridden.
     * If the supplied value is null, any existing value of the property is removed.
     * <p>Example:</p>
     * <p><code>serializer.setOutputProperty(new QName("method"), "xml");</code></p>
     * <p>Any serialization properties supplied via this interface take precedence over serialization
     * properties defined in the source stylesheet or query.</p>
     * <p>Unlike the method {@link #setOutputProperty(Property, String)}, this method allows properties
     * to be set whose names are not in the standard set of property names defined in the W3C specifications,
     * nor in a recognized Saxon extension. This enables properties to be set for use by a custom serialization
     * method.</p>
     *
     * @param property The name of the property to be set
     * @param value    The value of the property, as a string. The format is generally as defined
     *                 in the <code>xsl:output</code> declaration in XSLT: this means that boolean properties, for
     *                 example, are represented using the strings "yes" and "no". Properties whose values are QNames,
     *                 such as <code>cdata-section-elements</code> are expressed using the Clark representation of
     *                 a QName, that is "{uri}local". Multi-valued properties (again, <code>cdata-section-elements</code>
     *                 is an example) are expressed as a space-separated list.
     * @throws IllegalArgumentException if the value of the property is invalid. The property is
     *                 validated individually; invalid combinations of properties will be detected only when the properties
     *                 are actually used to serialize an XML event stream. No validation occurs unless the property
     *                 name is either in no namespace, or in the Saxon namespace
     */

    public void setOutputProperty(QName property, String value) {
        SerializerFactory sf = processor.getUnderlyingConfiguration().getSerializerFactory();
        NamespaceUri uri = property.getNamespaceUri();
        if (uri.isEmpty() || uri.equals(NamespaceUri.SAXON)) {
            try {
                value = sf.checkOutputProperty(property.getClarkName(), value);
            } catch (XPathException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
            if (uri.equals(NamespaceUri.SAXON) && property.getLocalName().equals("next-in-chain")) {
                // reject the next-in-chain property: it's not relevant to a Serializer
                throw new IllegalArgumentException("saxon:next-in-chain is not a valid serialization property");
            }
        }
        String clarkName = property.getClarkName();
        if (value == null) {
            properties.remove(clarkName);
        } else {
            properties.put(clarkName, value);
        }
    }

    /**
     * Get the value of a serialization property.
     *
     * <p>Unlike the method {@link #getOutputProperty(Property)}, this method allows properties
     * to be read whose names are not in the standard set of property names defined in the W3C specifications,
     * nor in a recognized Saxon extension. This enables properties to be set for use by a custom serialization
     * method.</p>
     *
     * @param property the name of the required property
     * @return the value of the required property as a string, or null if the property has
     * not been given any value.
     */

    public String getOutputProperty(QName property) {
        return properties.get(property.getClarkName());
    }

    /**
     * Set the destination of the serialized output, as a Writer.
     * <p>Note that when this option is used, the serializer does not perform character
     * encoding. This also means that it never replaces special characters with XML numeric
     * character references. The final encoding is the responsibility of the supplied Writer.</p>
     * <p>Closing the writer after use is the responsibility of the caller.</p>
     * <p>Calling this method has the side-effect of setting the OutputStream and OutputFile to null.</p>
     *
     * @param writer the Writer to which the serialized XML output will be written.
     */

    public void setOutputWriter(Writer writer) {
        result.setOutputStream(null);
        result.setSystemId((String) null);
        result.setWriter(writer);
        mustClose = false;
    }

    /**
     * Set the destination of the serialized output, as an OutputStream.
     * <p>Closing the output stream after use is the responsibility of the caller.</p>
     * <p>Calling this method has the side-effect of setting the OutputWriter and OutputFile to null.</p>
     *
     * @param stream the OutputStream to which the serialized XML output will be written.
     */

    public void setOutputStream(OutputStream stream) {
        result.setWriter(null);
        result.setSystemId((String) null);
        result.setOutputStream(stream);
        mustClose = false;
    }

    /**
     * Set the destination of the serialized output, as a File.
     * <p>Calling this method has the side-effect of setting the current OutputWriter
     * and OutputStream to null.</p>
     * <p>This method sets the destination base URI to the URI corresponding to the name of the
     * supplied file.</p>
     *
     * @param file the File to which the serialized XML output will be written.
     */

    public void setOutputFile(File file) {
        result.setOutputStream(null);
        result.setWriter(null);
        result.setSystemId(file);
        setDestinationBaseURI(file.toURI());
        mustClose = true;
    }

    /**
     * Serialize an XdmNode to the selected output destination using this serializer
     *
     * @param node The node to be serialized
     * @throws IllegalStateException if no outputStream, Writer, or File has been supplied as the
     *                               destination for the serialized output
     * @throws SaxonApiException     if a serialization error or I/O error occurs
     * @since 9.3
     */

    public void serializeNode(XdmNode node) throws SaxonApiException {
        StreamResult res = result;
        if (res.getOutputStream() == null && res.getWriter() == null && res.getSystemId() == null) {
            throw new IllegalStateException("Either an outputStream, or a Writer, or a File must be supplied");
        }
        serializeNodeToResult(node, res);
    }

    /**
     * Serialize an arbitrary XdmValue to the selected output destination using this serializer.
     *
     * <p>If the supplied <code>XdmValue</code> is an <code>XdmNode</code>, then it is serialized
     * using the {@link #serializeNode(XdmNode)} method. In other cases, it behaves in a way
     * equivalent to XSLT serialization with <code>build-tree="no"</code>; this is the recommended
     * way to output non-node values using the JSON or Adaptive serialization methods.</p>
     *
     * @param value The value to be serialized
     * @throws IllegalStateException if no outputStream, Writer, or File has been supplied as the
     *                               destination for the serialized output
     * @throws SaxonApiException     if a serialization error or I/O error occurs
     * @since 9.3
     */


    public void serializeXdmValue(XdmValue value) throws SaxonApiException {
        if (value instanceof XdmNode) {
            serializeNode((XdmNode) value);
        } else {
            try {
                SerializationProperties properties =
                        new SerializationProperties(getLocallyDefinedProperties(), characterMapIndex);
                QueryResult.serializeSequence(value.getUnderlyingValue().iterate(),
                                              processor.getUnderlyingConfiguration(), result, properties);
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
        }
        closeAndNotify();

    }

    /**
     * Serialize an arbitrary JAXP Source to the selected output destination using this serializer. The supplied
     * sequence is first wrapped in a document node according to the rules given in section 2 (Sequence Normalization) of the
     * <a href="http://www.w3.org/TR/xslt-xquery-serialization/">XSLT/XQuery serialization specification</a>; the resulting
     * document nodes is then serialized using the serialization parameters defined in this serializer.
     *
     * <p>The default serialization properties used by this method correspond to the properties used
     * by an XSLT 3.0 stylesheet with no <code>xsl:output</code> declaration. In particular, the default output
     * method (always one of XML, XHTML, or HTML) depends on the name of the first element node
     * encountered.</p>
     *
     * @param source The value to be serialized
     * @throws IllegalStateException if no outputStream, Writer, or File has been supplied as the
     *                               destination for the serialized output
     * @throws SaxonApiException     if a serialization error or I/O error occurs
     * @since 10.0
     */

    public void serialize(Source source) throws SaxonApiException {
        try {
            SerializerFactory sf = processor.getUnderlyingConfiguration().getSerializerFactory();
            Receiver tr = sf.getReceiver(result, new SerializationProperties(getLocallyDefinedProperties()));
            Sender.send(source, tr, processor.getUnderlyingConfiguration().getParseOptions());
            closeAndNotify();
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Serialize an arbitrary JAXP Source to a string using this serializer. The supplied
     * sequence is first wrapped in a document node according to the rules given in section 2 (Sequence Normalization) of the
     * <a href="http://www.w3.org/TR/xslt-xquery-serialization/">XSLT/XQuery serialization specification</a>; the resulting
     * document nodes is then serialized using the serialization parameters defined in this serializer.
     *
     * <p>The default serialization properties used by this method correspond to the properties used
     * by an XSLT 3.0 stylesheet with no <code>xsl:output</code> declaration. In particular, the default output
     * method (always one of XML, XHTML, or HTML) depends on the name of the first element node
     * encountered.</p>
     *
     * @param source The value to be serialized
     * @return the serialized result, as a string
     * @throws SaxonApiException     if a serialization error or I/O error occurs
     * @since 10.0
     */

    public String serializeToString(Source source) throws SaxonApiException {
        try {
            SerializerFactory sf = processor.getUnderlyingConfiguration().getSerializerFactory();
            StringWriter sw = new StringWriter();
            Receiver tr = sf.getReceiver(new StreamResult(sw),
                                         new SerializationProperties(getLocallyDefinedProperties()));
            Sender.send(source, tr, processor.getUnderlyingConfiguration().getParseOptions());
            closeAndNotify();
            return sw.toString();
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Serialize an arbitrary value using this serializer
     *
     * <p>The default serialization properties used by this method correspond to the properties used
     * by an XSLT 3.0 stylesheet with no <code>xsl:output</code> declaration. In particular, the default output
     * method (always one of XML, XHTML, or HTML) depends on the name of the first element node
     * encountered.</p>
     *
     * @param value The value to be serialized
     * @return the serialized representation of the value
     * @throws SaxonApiException if a serialization error occurs
     * @since 13.0
     */

    public String serializeToString(XdmValue value) throws SaxonApiException {
        StringWriter sw = new StringWriter();
        StreamResult sr = new StreamResult(sw);
        Configuration config = processor.getUnderlyingConfiguration();
        SerializationProperties props = new SerializationProperties(getLocallyDefinedProperties(), characterMapIndex);
        try {
            QueryResult.serializeSequence(value.getUnderlyingValue().iterate(), config, sr, props);
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
        return sw.toString();
    }


    /**
     * Serialize an XdmNode to a string using this serializer
     *
     * <p>The default serialization properties used by this method correspond to the properties used
     * by an XSLT 3.0 stylesheet with no <code>xsl:output</code> declaration. In particular, the default output
     * method (always one of XML, XHTML, or HTML) depends on the name of the first element node
     * encountered.</p>
     *
     * @param node The node to be serialized
     * @return the serialized representation of the node as lexical XML
     * @throws SaxonApiException if a serialization error occurs
     * @since 9.3
     */

    public String serializeNodeToString(XdmNode node) throws SaxonApiException {
        StringWriter sw = new StringWriter();
        StreamResult sr = new StreamResult(sw);
        serializeNodeToResult(node, sr);
        return sw.toString();
    }

    private void serializeNodeToResult(XdmNode node, Result res) throws SaxonApiException {
        try {
            SerializationProperties props = new SerializationProperties(getLocallyDefinedProperties(), characterMapIndex);
            QueryResult.serialize(node.getUnderlyingNode(), res, props);
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Get an XMLStreamWriter that can be used for writing application-generated XML
     * to be output via this serializer.
     *
     * <p>The default serialization properties used by this method correspond to the properties used
     * by an XSLT 3.0 stylesheet with no <code>xsl:output</code> declaration. In particular, the default output
     * method (always one of XML, XHTML, or HTML) depends on the name of the first element node
     * encountered.</p>
     *
     * @return a newly constructed XMLStreamWriter that pipes events into this Serializer
     * @throws SaxonApiException     if any other failure occurs
     * @since 9.3
     */

    public StreamWriterToReceiver getXMLStreamWriter() throws SaxonApiException {
        PipelineConfiguration pipe = processor.getUnderlyingConfiguration().makePipelineConfiguration();
        Receiver r = getReceiver(pipe, getSerializationProperties());
        r = new NamespaceReducer(r);
        return new StreamWriterToReceiver(r);
    }

    /**
     * Get a ContentHandler that can be used to direct the output of a SAX parser (or other
     * source of SAX events) to this serializer.
     *
     * <p>The default serialization properties used by this method correspond to the properties used
     * by an XSLT 3.0 stylesheet with no <code>xsl:output</code> declaration. In particular, the default output
     * method (always one of XML, XHTML, or HTML) depends on the name of the first element node
     * encountered.</p>
     *
     * @return a newly constructed ContentHandler that pipes events into this Serializer
     * @throws SaxonApiException     if any other failure occurs
     * @since 9.7
     */

    public org.xml.sax.ContentHandler getContentHandler() throws SaxonApiException {
        PipelineConfiguration pipe = processor.getUnderlyingConfiguration().makePipelineConfiguration();
        Receiver r = getReceiver(pipe, getSerializationProperties());
        //r = new SequenceNormalizerWithSpaceSeparator(r);
        r = new NamespaceReducer(r);
        ReceivingContentHandler rch = new ReceivingContentHandler();
        rch.setReceiver(r);
        rch.setPipelineConfiguration(r.getPipelineConfiguration());
        return rch;
    }

    /**
     * Get the current output destination.
     *
     * @return an OutputStream, Writer, or File, depending on the previous calls to
     * {@link #setOutputStream}, {@link #setOutputWriter}, or {@link #setOutputFile}; or
     * null, if no output destination has been set up.
     */

    public Object getOutputDestination() {
        if (result.getOutputStream() != null) {
            return result.getOutputStream();
        }
        if (result.getWriter() != null) {
            return result.getWriter();
        }
        String systemId = result.getSystemId();
        if (systemId != null) {
            try {
                return new File(new URI(systemId));
            } catch (URISyntaxException e) {
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Return a receiver to which Saxon will send events. This method is provided
     * primarily for internal use, though it could also be called by user applications
     * wanting to make use of the Saxon serializer.
     *
     * @param pipe The Saxon configuration. This is an internal implementation object
     *               held within the {@link Processor}
     * @param params Serialization parameters originating from the query or stylesheet
     *               (for example, <code>xsl:output</code> declarations or <code>xsl:result-document</code> attributes).
     *               These parameters are combined with those held by this {@code Serializer} itself,
     *               with properties set on the {@code Serializer} taking precedence. If a particular
     *               property has not been set either on the {@code Serializer} object itself
     *               or in the supplied {@code params}, the defaults follow the XSLT 3.0 rules.
     * @return a receiver to which XML events will be sent
     */

    @Override
    public Receiver getReceiver(PipelineConfiguration pipe, SerializationProperties params) throws SaxonApiException {
        try {
            SerializerFactory sf = pipe.getConfiguration().getSerializerFactory();
            SerializationProperties mergedParams = getSerializationProperties().combineWith(params);
            Receiver target = sf.getReceiver(result, mergedParams, pipe);
            if (helper.getListeners() != null) {
                if (target instanceof SequenceNormalizer) {
                    ((SequenceNormalizer)target).onClose(helper.getListeners());
                } else {
                    target = new CloseNotifier(target, helper.getListeners());
                }
            }
            if (target.getSystemId() == null && getDestinationBaseURI() != null) {
                target.setSystemId(getDestinationBaseURI().toASCIIString());
            }
            return target;
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Create a Properties object holding the defined serialization properties. This
     * will be in the same format as JAXP interfaces such as
     * {@link javax.xml.transform.Transformer#getOutputProperties()}
     *
     * @param defaultOutputProperties the default properties
     * @return a newly-constructed Properties object holding the declared serialization properties. Specifically,
     * it holds the properties defined explicitly on this Serializer object, backed by the properties defined
     * in {@code defaultOutputProperties}.
     */

    public Properties getCombinedOutputProperties(Properties defaultOutputProperties) {
        Properties props = defaultOutputProperties == null ? new Properties() : new Properties(defaultOutputProperties);
        for (String key : properties.keySet()) {
            String value = properties.get(key);
            props.setProperty(key, value);
        }
        return props;
    }


    /**
     * Create a Properties object holding the serialization properties explicitly declared
     * within this {@code Serializer} object, and not including any defaults taken from the stylesheet or query.
     * @return a newly-constructed Properties object holding the declared serialization properties. Specifically,
     * it holds the properties defined explicitly on this Serializer object, and excludes any properties defined
     * in named or unnamed <code>xsl:output</code> declarations in the stylesheet, or the equivalent in XQuery.
     */

    protected Properties getLocallyDefinedProperties() {
        Properties props = new Properties();
        for (String key : properties.keySet()) {
            String value = properties.get(key);
            props.setProperty(key, value);
        }
        return props;
    }

    /**
     * Create a {@link SerializationProperties} object holding the serialization properties explicitly declared
     * within this Serializer object (including any character map index), and not including any
     * defaults taken from the stylesheet or query.
     *
     * @return a newly-constructed {@code SerializationProperties} object holding the declared
     * serialization properties. Specifically, it holds the properties defined explicitly on this
     * {@code Serializer} object, and excludes any properties defined in named or unnamed
     * <code>xsl:output</code> declarations in the stylesheet, or the equivalent in XQuery.
     */

    public SerializationProperties getSerializationProperties() {
        return new SerializationProperties(getLocallyDefinedProperties(), characterMapIndex);
    }

    /**
     * Get the JAXP StreamResult object representing the output destination
     * of this serializer
     * @return the JAXP StreamResult object
     */

    protected Result getResult() {
        return result;
    }

    /**
     * Close any resources associated with this destination. Note that this does <b>not</b>
     * close any user-supplied OutputStream or Writer; those must be closed explicitly
     * by the calling application.
     */

    @Override
    public void close() throws SaxonApiException {
        if (mustClose) {
            // This relies on the fact that the SerializerFactory sets the OutputStream
            OutputStream stream = result.getOutputStream();
            if (stream != null) {
                try {
                    stream.close();
                } catch (java.io.IOException err) {
                    throw new SaxonApiException("Failed while closing output file", err);
                }
            }
            Writer writer = result.getWriter();  
            if (writer != null) {
                try {
                    writer.close();
                } catch (java.io.IOException err) {
                    throw new SaxonApiException("Failed while closing output file", err);
                }
            }
        }
    }

    /**
     * Get the Property with a given QName
     *
     * @param name the QName of the required property, which must be either a standard property defined
     *             in the XSLT 3.0 / XQuery 3.0 serialization specification, or an extension property defined by
     *             Saxon in the Saxon namespace
     * @return the corresponding Property object
     * @throws IllegalArgumentException if the name is not a recognized serialization property name.
     * @since 9.6
     */

    public static Property getProperty(QName name) {
        String clarkName = name.getClarkName();
        Property prop = standardProperties.get(clarkName);
        if (prop != null) {
            return prop;
        } else {
            throw new IllegalArgumentException("Unknown serialization property " + clarkName);
        }
    }

    public boolean isMustCloseAfterUse() {
        return mustClose;
    }
}

