////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.QNameException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ValidationException;
import net.sf.saxon.value.BigDecimalValue;

import javax.xml.transform.OutputKeys;
import java.math.BigDecimal;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * Provides string constants that can be used to set
 * output properties for a Transformer, or to retrieve
 * output properties from a Transformer or Templates object.
 * <p>These keys are private Saxon keys that supplement the standard keys
 * defined in javax.xml.transform.OutputKeys. As well as Saxon extension
 * attributes, the list includes new attributes defined in XSLT 2.0 which
 * are not yet supported in JAXP</p>
 *
 * <p>Note that for JAXP compatibility, the names of properties use Clark format,
 * that is <code>{uri}local</code>. However, from 10.0, the values of properties
 * containing QNames, such as <code>cdata-section-elements</code>, use EQName
 * format, that is <code>Q{uri}local</code>.</p>
 */

public class SaxonOutputKeys {

    /**
     * This class is not instantiated
     */

    private SaxonOutputKeys() {
    }

    /**
     * String constant representing the saxon:xquery output method name
     */

    /*@NotNull*/ public static final String SAXON_XQUERY_METHOD = "Q{http://saxon.sf.net/}xquery";

    /**
     * String constant representing the saxon:base64Binary output method name
     */

    /*@NotNull*/ public static final String SAXON_BASE64_BINARY_METHOD = "Q{http://saxon.sf.net/}base64Binary";

    /**
     * String constant representing the saxon:hexBinary output method name
     */

    /*@NotNull*/ public static final String SAXON_HEX_BINARY_METHOD = "Q{http://saxon.sf.net/}hexBinary";

    /**
     * String constant representing the saxon:xml-to-json output method name
     */

    /*@NotNull*/ public static final String SAXON_XML_TO_JSON_METHOD = "Q{http://saxon.sf.net/}xml-to-json";

    /**
     * allow-duplicate-names = yes|no.
     * <p>Defines whether duplicate keys are allowed in a JSON map (new in 3.1)</p>
     */

    /*@NotNull*/ public static final String ALLOW_DUPLICATE_NAMES = "allow-duplicate-names";

    /**
     * build-tree = yes|no.
     * <p>Defines whether the raw output is used to build an XML document tree</p>
     */

    /*@NotNull*/ public static final String BUILD_TREE = "build-tree";

    /**
     * saxon:indent-spaces = integer.
     * <p>Defines the number of spaces used for indentation of output</p>
     */

    /*@NotNull*/ public static final String INDENT_SPACES = "{http://saxon.sf.net/}indent-spaces";

    /**
     * saxon:line-length = integer.
     * <p>Defines the desired maximum line length used when indenting output</p>
     */

    /*@NotNull*/ public static final String LINE_LENGTH = "{http://saxon.sf.net/}line-length";

    /**
     * saxon:single-quotes = boolean.
     * <p>Indicates that attributes should be delimited by apostrophes rather than quotation marks</p>
     */

    /*@NotNull*/ public static final String SINGLE_QUOTES = "{http://saxon.sf.net/}single-quotes";


    /**
     * suppress-indentation = list of element names
     * <p>Defines elements within which no indentation will occur</p>
     */

    /*@NotNull*/ public static final String SUPPRESS_INDENTATION = "suppress-indentation";

    /**
     * html-version = decimal
     * <p>Defines the version of HTML. For the XHTML output method this allows separate
     * specification of the XHTML version and the XML version. This is a new serialization
     * parameter in the draft 3.0 specification.</p>
     */

    public static final String HTML_VERSION = "html-version";

    /**
     * item-separator = string
     * <p>Relevant to XQuery, where an arbitrary sequence can be serialized; defines a separator
     * to be inserted between successive items in the sequence.</p>
     */

    public static final String ITEM_SEPARATOR = "item-separator";

    /**
     * json-node-output-method = method-name
     * <p>Defines the serialization method for nodes encountered while serializing as JSON</p>
     */

    /*@NotNull*/ public static final String JSON_NODE_OUTPUT_METHOD = "json-node-output-method";


    /**
     * saxon:attribute-order = list of attribute names
     * <p>Defines an ordering for attributes in the serialized output. Any attribute present in the list
     * will appear correctly ordered according to the list; other attributes will be ordered first by namespace,
     * then by local name.</p>
     */

    /*@NotNull*/ public static final String ATTRIBUTE_ORDER = "{http://saxon.sf.net/}attribute-order";

    /**
     * saxon:canonical = yes/no
     * <p>When used in conjunction with the XML output method, delivers the output in C14N canonical form.
     * Any serialization properties inconsistent with C14N (for example, encoding, indent, or character maps) are
     * ignored if saxon:canonical=yes is specified. Provisions of the W3C serialization specification
     * (for example, requiring the use of empty element tags) are also ignored.</p>
     * @since 9.9
     */

    /*@NotNull*/ public static final String CANONICAL = "{http://saxon.sf.net/}canonical";

    /**
     * saxon:property-order = list of strings
     * <p>Defines an ordering for properties in the serialized JSON output of a map. Any property present in the list
     * will appear correctly ordered according to the list; other attributes will be ordered by name.</p>
     */

    /*@NotNull*/ public static final String PROPERTY_ORDER = "{http://saxon.sf.net/}property-order";

    /**
     * saxon:double-space = list of element names
     * <p>Defines elements that will have an extra blank line added before the start tag, in addition
     * to normal indentation</p>
     */

    /*@NotNull*/ public static final String DOUBLE_SPACE = "{http://saxon.sf.net/}double-space";

    /**
     * saxon:newline = string
     * <p>Defines the sequence of characters used to represent a newline when using the text
     * output method</p>
     */

    /*@NotNull*/ public static final String NEWLINE = "{http://saxon.sf.net/}newline";

    /**
     * stylesheet-version. This serialization parameter is set automatically by the XSLT processor
     * to the value of the version attribute on the principal stylesheet module. This is because
     * in backwards compatibility mode (version="1.0") the default output method for an XHTML result
     * document is XML rather than XHTML.
     */

    /*@NotNull*/ public static final String STYLESHEET_VERSION = "{http://saxon.sf.net/}stylesheet-version";

    /**
     * use-character-map = list-of-qnames.
     * <p>Defines the character maps used in this output definition. The QNames
     * are represented in EQName notation as Q{uri}local-name.</p>
     */

    /*@NotNull*/ public static final String USE_CHARACTER_MAPS = "use-character-maps";


    /**
     * include-content-type = "yes" | "no". This attribute is defined in XSLT 2.0
     * <p>Indicates whether the META tag is to be added to HTML output</p>
     */

    /*@NotNull*/ public static final String INCLUDE_CONTENT_TYPE = "include-content-type";

    /**
     * undeclare-prefixes = "yes" | "no". This attribute is defined in XSLT 2.0
     * <p>Indicates XML 1.1 namespace undeclarations are to be output when required</p>
     */

    /*@NotNull*/ public static final String UNDECLARE_PREFIXES = "undeclare-prefixes";

    /**
     * escape-uri-attributes = "yes" | "no". This attribute is defined in XSLT 2.0
     * <p>Indicates whether HTML attributes of type URI are to be URI-escaped</p>
     */

    /*@NotNull*/ public static final String ESCAPE_URI_ATTRIBUTES = "escape-uri-attributes";

    /**
     * representation = rep1[;rep2].
     * <p>Indicates the preferred way of representing non-ASCII characters in HTML
     * and XML output. rep1 is for characters in the range 128-256, rep2 for those
     * above 256.</p>
     */
    /*@NotNull*/ public static final String CHARACTER_REPRESENTATION = "{http://saxon.sf.net/}character-representation";

    /**
     * saxon:next-in-chain = URI.
     * <p>Indicates that the output is to be piped into another XSLT stylesheet
     * to perform another transformation. The auxiliary property NEXT_IN_CHAIN_BASE_URI
     * records the base URI of the stylesheet element where this attribute was found.</p>
     */
    /*@NotNull*/ public static final String NEXT_IN_CHAIN = "{http://saxon.sf.net/}next-in-chain";
    /*@NotNull*/ public static final String NEXT_IN_CHAIN_BASE_URI = "{http://saxon.sf.net/}next-in-chain-base-uri";

    /**
     * parameter-document = URI.
     * <p>Indicates that the output is to be piped into another XSLT stylesheet
     * to perform another transformation. The auxiliary property NEXT_IN_CHAIN_BASE_URI
     * records the base URI of the stylesheet element where this attribute was found.</p>
     */
    /*@NotNull*/ public static final String PARAMETER_DOCUMENT = "parameter-document";
    /*@NotNull*/ public static final String PARAMETER_DOCUMENT_BASE_URI = "{http://saxon.sf.net/}parameter-document-base-uri";


    /**
     * byte-order-mark = yes|no.
     * <p>Indicates whether UTF-8/UTF-16 output is to start with a byte order mark. Values are "yes" or "no",
     * default is "no"
     */

    /*@NotNull*/ public static final String BYTE_ORDER_MARK = "byte-order-mark";

    /**
     * normalization-form = NFC|NFD|NFKC|NFKD|non.
     * <p>Indicates that a given Unicode normalization form (or no normalization) is required.
     */

    /*@NotNull*/ public static final String NORMALIZATION_FORM = "normalization-form";

    /**
     * recognize-binary = yes|no.
     * <p>If set to "yes", and the output is being written using output method "text", Saxon will recognize
     * two processing instructions &lt;?hex XXXX?&gt; and &lt;b64 XXXX?&gt; containing binary data encoded
     * as a hexBinary or base64 string respectively. The corresponding strings will be decoded as characters
     * in the encoding being used for the output file, and will be written out to the output without checking
     * that they represent valid XML strings.</p>
     */

    /*@NotNull*/ public static final String RECOGNIZE_BINARY = "{http://saxon.sf.net/}recognize-binary";

    /**
     * saxon:require-well-formed = yes|no.
     * <p>Indicates whether a user-supplied ContentHandler requires the stream of SAX events to be
     * well-formed (that is, to have a single element node and no text nodes as children of the root).
     * The default is "no".</p>
     */

    /*@NotNull*/ public static final String REQUIRE_WELL_FORMED = "{http://saxon.sf.net/}require-well-formed";

    /**
     * supply-source-locator = yes|no.
     * <p>If set to "yes", and the output is being sent to a SAXResult (or to a user-supplied content handler),
     * indicates that the SAX Locator made available to the ContentHandler will contain information about the
     * location of the context node in the source document as well as the location in the stylesheet or query.</p>
     */

    /*@NotNull*/ public static final String SUPPLY_SOURCE_LOCATOR = "{http://saxon.sf.net/}supply-source-locator";

    /**
     * wrap="yes"|"no".
     * <p>This property is only available in the XQuery API. The value "yes" indicates that the result
     * sequence produced by the query is to be wrapped, that is, each item in the result is represented
     * as a separate element. This format allows any sequence to be represented as an XML document,
     * including for example sequences consisting of parentless attribute nodes.</p>
     */

    /*@NotNull*/ public static final String WRAP = "{http://saxon.sf.net/}wrap-result-sequence";

    /**
     * Property saxon:unfailing used to indicate that serialization should not fail. This is used when a serialization method such
     * as XML or JSON is invoked from the ADAPTIVE serialization method, and it tailors the error-handling behaviour
     * of the subsidiary output method. There is nothing to stop the property being set directly by the user, though
     * (at least in the XSLT case) the resulting behaviour is non-conformant. The values of the property are yes/no.
     */

    public static final String UNFAILING = "{http://saxon.sf.net/}unfailing";
    
    /**
     * Process a serialization property whose value is a list of element names, for example cdata-section-elements
     *
     *
     * @param value        The value of the property as written
     * @param nsResolver   The namespace resolver to use; may be null if prevalidated is set or if names are supplied
     *                     in EQName format
     * @param useDefaultNS True if unprefixed names are to be treated as being in the default namespace
     * @param prevalidated true if the property has already been validated
     * @param allowStar  true if the pseudo-name "*" is permitted; it will be retained in the output
     * @param errorCode    The error code to return in the event of problems
     * @return The list of element names with lexical QNames replaced by EQName names, starting with a single space
     * @throws XPathException if any error is found in the list of element names, for example, an undeclared namespace prefix
     */

    /*@NotNull*/
    public static String parseListOfNodeNames(
            String value, /*@Nullable*/ NamespaceResolver nsResolver, boolean useDefaultNS, boolean prevalidated, /*@NotNull*/  boolean allowStar, String errorCode)
            throws XPathException {
        StringBuilder s = new StringBuilder();
        StringTokenizer st = new StringTokenizer(value, " \t\n\r", false);
        while (st.hasMoreTokens()) {
            String displayname = st.nextToken();
            if (allowStar && "*".equals(displayname)) {
                s.append(' ').append(displayname);
            } else if (prevalidated || (nsResolver == null)) {
                s.append(' ').append(displayname);
            } else if (displayname.startsWith("Q{")) {
                s.append(' ').append(displayname);
            } else {
                try {
                    String[] parts = NameChecker.getQNameParts(displayname);
                    String muri = nsResolver.getURIForPrefix(parts[0], useDefaultNS);
                    if (muri == null) {
                        throw new XPathException("Namespace prefix '" + parts[0] + "' has not been declared", errorCode);
                    }
                    s.append(" Q{").append(muri).append('}').append(parts[1]);
                } catch (QNameException err) {
                    throw new XPathException("Invalid QName. " + err.getMessage(), errorCode);
                }
            }
        }
        return s.toString();
    }

    /**
     * Ask whether a particular serialization property is to be considered as string-valued,
     * in which case the value is used exactly as specified without any whitespace stripping.
     *
     * <p>The logic here is a little pragmatic.</p>
     * <p>For XSLT (xsl:output and xsl:result-document) the properties doctype-system, doctype-public,
     *    item-separator, and media-type have string type and whitespace should therefore be retained.</p>
     * <p>For values in a parameter document, doctype-system is not whitespace stripped (and is restricted
     *    by a pattern); doctype-public is whitespace-stripped; media-type is whitespace-stripped</p>
     * <p>For values in an fn:serialize parameter map, doctype-system, doctype-public, encoding,
     *    item-separator, media-type, and version are non-stripped strings</p>
     * <p>XQuery output declarations follow the rules for parameter documents</p>
     * <p>Pragmatically, it makes sense to be consistent. Including whitespace in doctype-system,
     *    doctype-public, encoding, version, or media-type is never useful. So we apply
     *    whitespace-stripping to all properties other than item-separator.</p>
     *
     * @param key the property name, in Clark notation
     * @return true if the property is retained as written without whitespace stripping
     */
    public static boolean isUnstrippedProperty(String key) {
        return ITEM_SEPARATOR.equals(key) || NEWLINE.equals(key);
    }

    /**
     * Examine the already-validated properties to see whether the html-version property is present
     * with the decimal value 5.; used to decide whether to produce XHTML 5.0 in the XHTML output
     * method.
     *
     * @param properties the properties to be examined
     * @return true if the properties include html-version="5.0". The property is a decimal value, so
     *         it can also be written, for example, "5" or "+5.00".
     */
    public static boolean isXhtmlHtmlVersion5(Properties properties) {
        String htmlVersion = properties.getProperty(SaxonOutputKeys.HTML_VERSION);
        try {
            return htmlVersion != null &&
                    ((BigDecimalValue)BigDecimalValue.makeDecimalValue(htmlVersion, false).asAtomic())
                            .getDecimalValue().equals(BigDecimal.valueOf(5));
        } catch (ValidationException e) {
            return false;
        }
    }

    /**
     * Examine the already-validated properties to see whether the html-version property is present
     * with the decimal value 5.0, or if absent, the version property is present with the value 5.0.
     * Used to decide whether to produce HTML5 output in the HTML output method.
     *
     * @param properties the properties to be examined
     * @return true if the properties include html-version="5.0". The property is a decimal value, so
     *         it can also be written, for example, "5" or "+5.00".
     */
    public static boolean isHtmlVersion5(Properties properties) {
        String htmlVersion = properties.getProperty(SaxonOutputKeys.HTML_VERSION);
        if (htmlVersion == null) {
            htmlVersion = properties.getProperty(OutputKeys.VERSION);
        }
        if (htmlVersion != null) {
            try {
                return ((BigDecimalValue)BigDecimalValue.makeDecimalValue(htmlVersion, false).asAtomic())
                                .getDecimalValue().equals(BigDecimal.valueOf(5));
            } catch (ValidationException e) {
                return false;
            }
        } else {
            return true; // Change in 10.0 to make HTML5 the default
        }
    }

    public static boolean isBuildTree(Properties properties) {
        String buildTreeProperty = properties.getProperty("build-tree");
        if (buildTreeProperty != null) {
            return "yes".equals(buildTreeProperty);
        }
        String method = properties.getProperty("method");
        return !("json".equals(method) || "adaptive".equals(method));
    }
}

