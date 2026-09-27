////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.str.StringTool;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.value.Whitespace;

import java.util.concurrent.ConcurrentHashMap;

/**
 * This class represents a URI used as a namespace name. NamespaceUri objects are pooled, so that
 * all occurrences of a given namespace name are represented by the same object.
 */

@SuppressWarnings("JavadocLinkAsPlainText")
public class NamespaceUri {

    // A map from strings to NamespaceUris

    private final static ConcurrentHashMap<String, NamespaceUri> stringToNamespaceUri = new ConcurrentHashMap<>(50);

    /**
     * Return the {@code NamespaceUri} object corresponding to a given namespace name supplied as a string
     * @param content the namespace name, supplied as a string
     * @return the corresponding {@code NamespaceUri} object
     */

    @CSharpReplaceBody(code="content ??= \"\";\n"
            + "return Saxon.Impl.Helpers.MapUtils.ComputeIfAbsent(stringToNamespaceUri, Saxon.Hej.value.Whitespace.trim(content), str => new Saxon.Hej.om.NamespaceUri(str));\n"
            )
    public static NamespaceUri of(String content) {
        if (content == null) {
            content = "";
        }
        return stringToNamespaceUri.computeIfAbsent(Whitespace.trim(content), NamespaceUri::new);
    }

    private final String stringContent;
    private final UnicodeString unicodeStringContent;

    private NamespaceUri(String content) {
        this.stringContent = content;
        this.unicodeStringContent = StringTool.fromCharSequence(content);
    }

    /**
     * Return the namespace URI as a string
     * @return the namespace URI as a string
     */

    public String toString() {
        return stringContent;
    }

    /**
     * Return the namespace URI as an instance of {@link UnicodeString}
     * @return the namespace URI as a {@code UnicodeString}
     */

    public UnicodeString toUnicodeString() {
        return unicodeStringContent;
    }

    /**
     * Ask whether this URI represents the "null namespace" (internally, a zero-length string)
     * @return true if this is the null namespace
     */

    public boolean isEmpty() {
        return this == NamespaceUri.NULL;
    }

    /**
     * Construct an unprefixed QName using this namespace and a supplied local name
     * @param localName the local part of the name
     * @return a StructuredQName representing a QName with this namespace URI, with
     * the supplied local part, and with no prefix.
     */

    public StructuredQName qName(String localName) {
        return new StructuredQName("", this, localName);
    }

    /**
     * A URI representing the null namespace (actually, an empty string)
     */

    public static final NamespaceUri NULL = NamespaceUri.of("");

    /**
     * Fixed namespace name for XML: "http://www.w3.org/XML/1998/namespace".
     */
    public static final NamespaceUri XML = NamespaceUri.of("http://www.w3.org/XML/1998/namespace");

    /**
     * Fixed namespace name for XSLT: "http://www.w3.org/1999/XSL/Transform"
     */
    public static final NamespaceUri XSLT = NamespaceUri.of("http://www.w3.org/1999/XSL/Transform");

    /**
     * Current namespace name for SAXON (from 7.0 onwards): "http://saxon.sf.net/"
     */
    public static final NamespaceUri SAXON = NamespaceUri.of("http://saxon.sf.net/");

    /**
     * Old namespace name for SAXON6: "http://icl.com/saxon"
     */
    public static final NamespaceUri SAXON6 = NamespaceUri.of("http://icl.com/saxon");

    /**
     * Fixed namespace name for the export of a Saxon stylesheet package
     */
    public static final NamespaceUri SAXON_XSLT_EXPORT = NamespaceUri.of("http://ns.saxonica.com/xslt/export");

    /**
     * Namespace name for XML Schema: "http://www.w3.org/2001/XMLSchema"
     */
    public static final NamespaceUri SCHEMA = NamespaceUri.of("http://www.w3.org/2001/XMLSchema");

    /**
     * XML-schema-defined namespace for use in instance documents ("xsi")
     */
    public static final NamespaceUri SCHEMA_INSTANCE = NamespaceUri.of("http://www.w3.org/2001/XMLSchema-instance");

    /**
     * Namespace defined in XSD 1.1 for schema versioning
     */
    public static final NamespaceUri SCHEMA_VERSIONING = NamespaceUri.of("http://www.w3.org/2007/XMLSchema-versioning");

    /**
     * Fixed namespace name for SAXON SQL extension: "http://saxon.sf.net/sql"
     */
    public static final NamespaceUri SQL = NamespaceUri.of("http://saxon.sf.net/sql");

    /**
     * Fixed namespace name for EXSLT/Common: "http://exslt.org/common"
     */
    public static final NamespaceUri EXSLT_COMMON = NamespaceUri.of("http://exslt.org/common");

    /**
     * Fixed namespace name for EXSLT/math: "http://exslt.org/math"
     */
    public static final NamespaceUri EXSLT_MATH = NamespaceUri.of("http://exslt.org/math");

    /**
     * Fixed namespace name for EXSLT/sets: "http://exslt.org/sets"
     */
    public static final NamespaceUri EXSLT_SETS = NamespaceUri.of("http://exslt.org/sets");

    /**
     * Fixed namespace name for EXSLT/date: "http://exslt.org/dates-and-times"
     */
    public static final NamespaceUri EXSLT_DATES_AND_TIMES = NamespaceUri.of("http://exslt.org/dates-and-times");

    /**
     * Fixed namespace name for EXSLT/random: "http://exslt.org/random"
     */
    public static final NamespaceUri EXSLT_RANDOM = NamespaceUri.of("http://exslt.org/random");

    /**
     * The standard namespace for functions and operators
     */
    public static final NamespaceUri FN = NamespaceUri.of("http://www.w3.org/2005/xpath-functions");

    /**
     * The standard namespace for XQuery output declarations
     */
    public static final NamespaceUri OUTPUT = NamespaceUri.of("http://www.w3.org/2010/xslt-xquery-serialization");


    /**
     * The standard namespace for system error codes
     */
    public static final NamespaceUri ERR = NamespaceUri.of("http://www.w3.org/2005/xqt-errors");

    /**
     * Predefined XQuery namespace for local functions
     */
    public static final NamespaceUri LOCAL = NamespaceUri.of("http://www.w3.org/2005/xquery-local-functions");

    /**
     * Math namespace for the XPath 3.0 math functions
     */

    public static final NamespaceUri MATH = NamespaceUri.of("http://www.w3.org/2005/xpath-functions/math");

    /**
     * Namespace URI for XPath 3.0 functions associated with maps
     */
    public final static NamespaceUri MAP_FUNCTIONS = NamespaceUri.of("http://www.w3.org/2005/xpath-functions/map");

    /**
     * Namespace URI for XPath 3.1 functions associated with arrays
     */
    public final static NamespaceUri ARRAY_FUNCTIONS = NamespaceUri.of("http://www.w3.org/2005/xpath-functions/array");

    /**
     * Namespace URI for the EXPath Binary module
     */
    public final static NamespaceUri EXPATH_BINARY = NamespaceUri.of("http://expath.org/ns/binary");

    /**
     * Namespace URI for the EXPath File module
     */
    public final static NamespaceUri EXPATH_FILE = NamespaceUri.of("http://expath.org/ns/file");

    /**
     * The XHTML namespace http://www.w3.org/1999/xhtml
     */

    public static final NamespaceUri XHTML = NamespaceUri.of("http://www.w3.org/1999/xhtml");

    /**
     * The SVG namespace http://www.w3.org/2000/svg
     */

    public static final NamespaceUri SVG = NamespaceUri.of("http://www.w3.org/2000/svg");

    /**
     * The MathML namespace http://www.w3.org/1998/Math/MathML
     */

    public static final NamespaceUri MATHML = NamespaceUri.of("http://www.w3.org/1998/Math/MathML");

    /**
     * The XMLNS namespace http://www.w3.org/2000/xmlns/ (used in DOM)
     */

    public static final NamespaceUri XMLNS = NamespaceUri.of("http://www.w3.org/2000/xmlns/");

    /**
     * The XLink namespace http://www.w3.org/1999/xlink
     */

    public static final NamespaceUri XLINK = NamespaceUri.of("http://www.w3.org/1999/xlink");

    /**
     * The xquery namespace http://www.w3.org/2012/xquery for the XQuery 3.0 declare option
     */

    public static final NamespaceUri XQUERY = NamespaceUri.of("http://www.w3.org/2012/xquery");

    /**
     * Namespace for types representing external Java objects: http://saxon.sf.net/java-type
     */

    public static final NamespaceUri JAVA_TYPE = NamespaceUri.of("http://saxon.sf.net/java-type");

    /**
     * Namespace for types representing external .NET objects
     */

    public static final NamespaceUri DOT_NET_TYPE = NamespaceUri.of("http://saxon.sf.net/clitype");

    /**
     * Namespace for names allocated to anonymous types. This exists so that
     * a name fingerprint can be allocated for use as a type annotation.
     */

    public static final NamespaceUri ANONYMOUS = NamespaceUri.of("http://ns.saxonica.com/anonymous-type");

    /**
     * Namespace for the Saxon serialization of the schema component model
     */

    public static final NamespaceUri SCM = NamespaceUri.of("http://ns.saxonica.com/schema-component-model");

    /**
     * URI identifying the Saxon object model for use in the JAXP 1.3 XPath API
     */

    public static final NamespaceUri OBJECT_MODEL_SAXON = NamespaceUri.of("http://saxon.sf.net/jaxp/xpath/om");


    /**
     * URI identifying the XOM object model for use in the JAXP 1.3 XPath API
     */

    public static final NamespaceUri OBJECT_MODEL_XOM = NamespaceUri.of("http://www.xom.nu/jaxp/xpath/xom");

    /**
     * URI identifying the JDOM object model for use in the JAXP 1.3 XPath API
     */

    public static final NamespaceUri OBJECT_MODEL_JDOM = NamespaceUri.of("http://jdom.org/jaxp/xpath/jdom");

    /**
     * URI identifying the AXIOM object model for use in the JAXP 1.3 XPath API
     */

    // Note: this URI is a Saxon invention
    public static final NamespaceUri OBJECT_MODEL_AXIOM = NamespaceUri.of("http://ws.apache.org/jaxp/xpath/axiom");

    /**
     * URI identifying the DOM4J object model for use in the JAXP 1.3 XPath API
     */

    public static final NamespaceUri OBJECT_MODEL_DOM4J = NamespaceUri.of("http://www.dom4j.org/jaxp/xpath/dom4j");

    /**
     * URI identifying the .NET DOM object model (not used, but needed for consistency)
     */

    public static final NamespaceUri OBJECT_MODEL_DOT_NET_DOM = NamespaceUri.of("http://saxon.sf.net/object-model/dotnet/dom");

    /**
     * URI identifying the DOMINO object model (not used, but needed for consistency)
     */

    public static final NamespaceUri OBJECT_MODEL_DOMINO = NamespaceUri.of("http://saxon.sf.net/object-model/domino");

    /**
     * URI for the names of generated variables
     */

    public static final NamespaceUri SAXON_GENERATED_VARIABLE = NamespaceUri.of("http://saxon.sf.net/generated-variable");

    /**
     * URI for the Saxon configuration file
     */

    public static final NamespaceUri SAXON_CONFIGURATION = NamespaceUri.of("http://saxon.sf.net/ns/configuration");

    /**
     * URI for the EXPath zip module
     */

    public static final NamespaceUri EXPATH_ZIP = NamespaceUri.of("http://expath.org/ns/zip");

    /**
     * URI for the user extension calls in SaxonJS
     */
    public static final NamespaceUri GLOBAL_JS = NamespaceUri.of("http://saxonica.com/ns/globalJS");

    /**
     * URI for the user extension calls in SaxonC for C++ and PHP
     */
    public static final NamespaceUri PHP = NamespaceUri.of("http://php.net/xsl");

    /**
     * URI for interactive XSLT extensions in Saxon-CE and SaxonJS
     */
    public static final NamespaceUri IXSL = NamespaceUri.of("http://saxonica.com/ns/interactiveXSLT");
    


    /**
     * Get the URI associated with a commonly-used conventional prefix
     *
     * @param prefix the namespace prefix
     * @return the namespace URI associated with this conventional prefix
     */

    public static NamespaceUri getUriForConventionalPrefix(String prefix) {
        switch (prefix) {
            case "xsl":
                return XSLT;
            case "fn":
                return FN;
            case "xml":
                return XML;
            case "xs":
                return SCHEMA;
            case "xsi":
                return SCHEMA_INSTANCE;
            case "err":
                return ERR;
            case "ixsl":
                return IXSL;
            case "js":
                return GLOBAL_JS;
            case "saxon":
                return SAXON;
            case "vv":
                return SAXON_GENERATED_VARIABLE;
            case "math":
                return MATH;
            case "map":
                return MAP_FUNCTIONS;
            case "array":
                return ARRAY_FUNCTIONS;
            default:
                return null;
        }
    }

    /**
     * Determine whether a namespace is a reserved namespace in XSLT
     *
     * @param uri the namespace URI to be tested
     * @return true if this namespace URI is a reserved namespace in XSLT
     */

    public static boolean isReserved(NamespaceUri uri) {
        return !uri.equals(NamespaceUri.NULL) &&
                (uri.equals(XSLT) ||
                         uri.equals(FN) ||
                         uri.equals(MATH) ||
                         uri.equals(MAP_FUNCTIONS) ||
                         uri.equals(ARRAY_FUNCTIONS) ||
                         uri.equals(XML) ||
                         uri.equals(SCHEMA) ||
                         uri.equals(SCHEMA_INSTANCE) ||
                         uri.equals(ERR) ||
                         uri.equals(XMLNS));
    }

    /**
     * Determine whether a namespace is a reserved namespace in XQuery
     *
     * @param uri the namespace URI to be tested
     * @return true if this namespace URI is reserved in XQuery 3.1
     */

    public static boolean isReservedInQuery31(NamespaceUri uri) {
        return uri.equals(FN) ||
                uri.equals(XML) ||
                uri.equals(SCHEMA) ||
                uri.equals(SCHEMA_INSTANCE) ||
                uri.equals(MATH) ||
                uri.equals(XQUERY) ||
                uri.equals(MAP_FUNCTIONS) ||
                uri.equals(ARRAY_FUNCTIONS);
    }



 }

