////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.lib.NamespaceConstant;

import java.util.HashMap;


/**
 * Well-known names used in XSLT processing. These names must all have
 * fingerprints in the range 0-1023, to avoid clashing with codes allocated
 * in a NamePool. We use the top three bits for the namespace, and the bottom
 * seven bits for the local name.
 * <p>Codes in the range 0-100 are used for standard node kinds such as ELEMENT,
 * DOCUMENT, etc, and for built-in types such as ITEM and EMPTY.</p>
 */

public abstract class StandardNames {

    private static final int DFLT_NS = 0;
    private static final int XSL_NS = 1;
    private static final int SAXON_NS = 2;
    private static final int XML_NS = 3;
    private static final int XS_NS = 4;
    private static final int XSI_NS = 5;

    public static final int DFLT = 0;             //   0
    public static final int XSL = 128;            // 128
    public static final int SAXON = 128 * 2;      // 256
    public static final int XML = 128 * 3;        // 384
    public static final int XS = 128 * 4;         // 512
    public static final int XSI = 128 * 5;        // 640

    public static final int XSL_ACCEPT = XSL;
    public static final int XSL_ACCUMULATOR = XSL + 1;
    public static final int XSL_ACCUMULATOR_RULE = XSL + 2;
    public static final int XSL_ANALYZE_STRING = XSL + 3;
    public static final int XSL_APPLY_IMPORTS = XSL + 4;
    public static final int XSL_APPLY_TEMPLATES = XSL + 5;
    public static final int XSL_ASSERT = XSL + 6;
    public static final int XSL_ATTRIBUTE = XSL + 7;
    public static final int XSL_ATTRIBUTE_SET = XSL + 8;
    public static final int XSL_BREAK = XSL + 9;
    public static final int XSL_CALL_TEMPLATE = XSL + 10;
    public static final int XSL_CATCH = XSL + 11;
    public static final int XSL_CHARACTER_MAP = XSL + 13;
    public static final int XSL_CHOOSE = XSL + 14;
    public static final int XSL_COMMENT = XSL + 15;
    public static final int XSL_CONTEXT_ITEM = XSL + 16;
    public static final int XSL_COPY = XSL + 17;
    public static final int XSL_COPY_OF = XSL + 18;
    public static final int XSL_DECIMAL_FORMAT = XSL + 19;
    public static final int XSL_DOCUMENT = XSL + 22;
    public static final int XSL_ELEMENT = XSL + 23;
    public static final int XSL_EXPOSE = XSL + 24;
    public static final int XSL_EVALUATE = XSL + 25;
    public static final int XSL_FALLBACK = XSL + 26;
    public static final int XSL_FOR_EACH = XSL + 27;
    public static final int XSL_FORK = XSL + 28;
    public static final int XSL_FOR_EACH_GROUP = XSL + 29;
    public static final int XSL_FUNCTION = XSL + 30;
    public static final int XSL_GLOBAL_CONTEXT_ITEM = XSL + 31;
    public static final int XSL_IF = XSL + 32;
    public static final int XSL_IMPORT = XSL + 33;
    public static final int XSL_IMPORT_SCHEMA = XSL + 34;
    public static final int XSL_INCLUDE = XSL + 35;
    public static final int XSL_ITERATE = XSL + 36;
    public static final int XSL_KEY = XSL + 37;
    public static final int XSL_MAP = XSL + 38;
    public static final int XSL_MAP_ENTRY = XSL + 39;
    public static final int XSL_MATCHING_SUBSTRING = XSL + 40;
    public static final int XSL_MERGE = XSL + 41;
    public static final int XSL_MERGE_ACTION = XSL + 42;
    public static final int XSL_MERGE_KEY = XSL + 43;
    public static final int XSL_MERGE_SOURCE = XSL + 44;
    public static final int XSL_MESSAGE = XSL + 45;
    public static final int XSL_MODE = XSL + 46;
    public static final int XSL_NAMESPACE = XSL + 47;
    public static final int XSL_NAMESPACE_ALIAS = XSL + 48;
    public static final int XSL_NEXT_ITERATION = XSL + 49;
    public static final int XSL_NEXT_MATCH = XSL + 50;
    public static final int XSL_NON_MATCHING_SUBSTRING = XSL + 51;
    public static final int XSL_NUMBER = XSL + 52;
    public static final int XSL_OTHERWISE = XSL + 53;
    public static final int XSL_ON_COMPLETION = XSL + 54;
    public static final int XSL_ON_EMPTY = XSL + 55;
    public static final int XSL_ON_NON_EMPTY = XSL + 56;
    public static final int XSL_OUTPUT = XSL + 57;
    public static final int XSL_OVERRIDE = XSL + 58;
    public static final int XSL_OUTPUT_CHARACTER = XSL + 59;
    public static final int XSL_PACKAGE = XSL + 60;
    public static final int XSL_PARAM = XSL + 61;
    public static final int XSL_PERFORM_SORT = XSL + 62;
    public static final int XSL_PRESERVE_SPACE = XSL + 63;
    public static final int XSL_PROCESSING_INSTRUCTION = XSL + 64;
    public static final int XSL_RESULT_DOCUMENT = XSL + 65;
    public static final int XSL_SEQUENCE = XSL + 66;
    public static final int XSL_SORT = XSL + 67;
    public static final int XSL_SOURCE_DOCUMENT = XSL + 68;
    public static final int XSL_STRIP_SPACE = XSL + 70;
    public static final int XSL_STYLESHEET = XSL + 71;
    public static final int XSL_TEMPLATE = XSL + 72;
    public static final int XSL_TEXT = XSL + 73;
    public static final int XSL_TRANSFORM = XSL + 74;
    public static final int XSL_TRY = XSL + 75;
    public static final int XSL_USE_PACKAGE = XSL + 76;
    public static final int XSL_VALUE_OF = XSL + 77;
    public static final int XSL_VARIABLE = XSL + 78;
    public static final int XSL_WHEN = XSL + 79;
    public static final int XSL_WHERE_POPULATED = XSL + 80;
    public static final int XSL_WITH_PARAM = XSL + 81;



    public static final int XSL_DEFAULT_COLLATION = XSL + 100;
    public static final int XSL_DEFAULT_MODE = XSL + 101;
    public static final int XSL_DEFAULT_VALIDATION = XSL + 102;
    public static final int XSL_EXCLUDE_RESULT_PREFIXES = XSL + 103;
    public static final int XSL_EXPAND_TEXT = XSL + 104;
    public static final int XSL_EXTENSION_ELEMENT_PREFIXES = XSL + 105;
    public static final int XSL_INHERIT_NAMESPACES = XSL + 106;
    public static final int XSL_TYPE = XSL + 107;
    public static final int XSL_USE_ATTRIBUTE_SETS = XSL + 108;
    public static final int XSL_USE_WHEN = XSL + 109;
    public static final int XSL_VALIDATION = XSL + 110;
    public static final int XSL_VERSION = XSL + 111;
    public static final int XSL_XPATH_DEFAULT_NAMESPACE = XSL + 112;


    public static final int SAXON_ASSIGN = SAXON + 1;
    public static final int SAXON_DEEP_UPDATE = SAXON + 3;
    public static final int SAXON_DO = SAXON + 6;
    public static final int SAXON_DOCTYPE = SAXON + 7;
    public static final int SAXON_ENTITY_REF = SAXON + 8;
    public static final int SAXON_TABULATE_MAPS = SAXON + 9;
    public static final int SAXON_WHILE = SAXON + 15;

    // Schema extension elements
    public static final int SAXON_PARAM = SAXON + 20;
    public static final int SAXON_PREPROCESS = SAXON + 21;
    public static final int SAXON_DISTINCT = SAXON + 22;
    public static final int SAXON_ORDER = SAXON + 23;

    private static final String SAXON_B = '{' + NamespaceConstant.SAXON + '}';
    public static final String SAXON_ASYCHRONOUS = SAXON_B + "asynchronous";
    public static final String SAXON_EXPLAIN = SAXON_B + "explain";


    public static final int XML_BASE = XML + 1;
    public static final int XML_SPACE = XML + 2;
    public static final int XML_LANG = XML + 3;
    public static final int XML_ID = XML + 4;
    public static final int XML_LANG_TYPE = XML + 5;
    public static final int XML_SPACE_TYPE = 6;

    public static final NodeName XML_ID_NAME = new FingerprintedQName("xml", NamespaceConstant.XML, "id", XML_ID);

    public static final int XS_STRING = XS + 1;
    public static final int XS_BOOLEAN = XS + 2;
    public static final int XS_DECIMAL = XS + 3;
    public static final int XS_FLOAT = XS + 4;
    public static final int XS_DOUBLE = XS + 5;
    public static final int XS_DURATION = XS + 6;
    public static final int XS_DATE_TIME = XS + 7;
    public static final int XS_TIME = XS + 8;
    public static final int XS_DATE = XS + 9;
    public static final int XS_G_YEAR_MONTH = XS + 10;
    public static final int XS_G_YEAR = XS + 11;
    public static final int XS_G_MONTH_DAY = XS + 12;
    public static final int XS_G_DAY = XS + 13;
    public static final int XS_G_MONTH = XS + 14;
    public static final int XS_HEX_BINARY = XS + 15;
    public static final int XS_BASE64_BINARY = XS + 16;
    public static final int XS_ANY_URI = XS + 17;
    public static final int XS_QNAME = XS + 18;
    public static final int XS_NOTATION = XS + 19;
    //public static final int XS_PRECISION_DECIMAL = XS + 20;
    public static final int XS_INTEGER = XS + 21;

    // Note that any type code <= XS_INTEGER is considered to represent a
    // primitive type: see Type.isPrimitiveType()

    public static final int XS_NON_POSITIVE_INTEGER = XS + 22;
    public static final int XS_NEGATIVE_INTEGER = XS + 23;
    public static final int XS_LONG = XS + 24;
    public static final int XS_INT = XS + 25;
    public static final int XS_SHORT = XS + 26;
    public static final int XS_BYTE = XS + 27;
    public static final int XS_NON_NEGATIVE_INTEGER = XS + 28;
    public static final int XS_POSITIVE_INTEGER = XS + 29;
    public static final int XS_UNSIGNED_LONG = XS + 30;
    public static final int XS_UNSIGNED_INT = XS + 31;
    public static final int XS_UNSIGNED_SHORT = XS + 32;
    public static final int XS_UNSIGNED_BYTE = XS + 33;
    public static final int XS_NORMALIZED_STRING = XS + 41;
    public static final int XS_TOKEN = XS + 42;
    public static final int XS_LANGUAGE = XS + 43;
    public static final int XS_NMTOKEN = XS + 44;
    public static final int XS_NMTOKENS = XS + 45;      // NB: list type
    public static final int XS_NAME = XS + 46;
    public static final int XS_NCNAME = XS + 47;
    public static final int XS_ID = XS + 48;
    public static final int XS_IDREF = XS + 49;
    public static final int XS_IDREFS = XS + 50;      // NB: list type
    public static final int XS_ENTITY = XS + 51;
    public static final int XS_ENTITIES = XS + 52;      // NB: list type
    public static final int XS_DATE_TIME_STAMP = XS + 53;

    public static final int XS_ANY_TYPE = XS + 60;
    public static final int XS_ANY_SIMPLE_TYPE = XS + 61;

    public static final int XS_INVALID_NAME = XS + 62;
    public static final int XS_ERROR = XS + 63;


    public static final int XS_ALL = XS + 64;
    public static final int XS_ALTERNATIVE = XS + 65;
    public static final int XS_ANNOTATION = XS + 66;
    public static final int XS_ANY = XS + 67;
    public static final int XS_ANY_ATTRIBUTE = XS + 68;
    public static final int XS_APPINFO = XS + 69;
    public static final int XS_ASSERT = XS + 70;
    public static final int XS_ASSERTION = XS + 71;
    public static final int XS_ATTRIBUTE = XS + 72;
    public static final int XS_ATTRIBUTE_GROUP = XS + 73;
    public static final int XS_CHOICE = XS + 74;
    public static final int XS_COMPLEX_CONTENT = XS + 75;
    public static final int XS_COMPLEX_TYPE = XS + 76;
    public static final int XS_DEFAULT_OPEN_CONTENT = XS + 77;
    public static final int XS_DOCUMENTATION = XS + 78;
    public static final int XS_ELEMENT = XS + 79;
    public static final int XS_ENUMERATION = XS + 80;
    public static final int XS_EXTENSION = XS + 81;
    public static final int XS_FIELD = XS + 82;
    public static final int XS_FRACTION_DIGITS = XS + 83;
    public static final int XS_GROUP = XS + 84;
    public static final int XS_IMPORT = XS + 85;
    public static final int XS_INCLUDE = XS + 86;
    public static final int XS_KEY = XS + 87;
    public static final int XS_KEYREF = XS + 88;
    public static final int XS_LENGTH = XS + 89;
    public static final int XS_LIST = XS + 90;
    public static final int XS_MAX_EXCLUSIVE = XS + 91;
    public static final int XS_MAX_INCLUSIVE = XS + 92;
    public static final int XS_MAX_LENGTH = XS + 93;
    public static final int XS_MAX_SCALE = XS + 94;
    public static final int XS_MIN_EXCLUSIVE = XS + 95;
    public static final int XS_MIN_INCLUSIVE = XS + 96;
    public static final int XS_MIN_LENGTH = XS + 97;
    public static final int XS_MIN_SCALE = XS + 98;
    public static final int XS_notation = XS + 99;
    public static final int XS_OPEN_CONTENT = XS + 100;
    public static final int XS_OVERRIDE = XS + 101;
    public static final int XS_PATTERN = XS + 102;
    public static final int XS_REDEFINE = XS + 103;
    public static final int XS_RESTRICTION = XS + 104;
    public static final int XS_SCHEMA = XS + 105;
    public static final int XS_SELECTOR = XS + 106;
    public static final int XS_SEQUENCE = XS + 107;
    public static final int XS_SIMPLE_CONTENT = XS + 108;
    public static final int XS_SIMPLE_TYPE = XS + 109;
    public static final int XS_EXPLICIT_TIMEZONE = XS + 110;
    public static final int XS_TOTAL_DIGITS = XS + 111;
    public static final int XS_UNION = XS + 112;
    public static final int XS_UNIQUE = XS + 113;
    public static final int XS_WHITE_SPACE = XS + 114;

    public static final int XS_UNTYPED = XS + 118;
    public static final int XS_UNTYPED_ATOMIC = XS + 119;
    public static final int XS_ANY_ATOMIC_TYPE = XS + 120;
    public static final int XS_YEAR_MONTH_DURATION = XS + 121;
    public static final int XS_DAY_TIME_DURATION = XS + 122;
    public static final int XS_NUMERIC = XS + 123;


    public static final int XSI_TYPE = XSI + 1;
    public static final int XSI_NIL = XSI + 2;
    public static final int XSI_SCHEMA_LOCATION = XSI + 3;
    public static final int XSI_NO_NAMESPACE_SCHEMA_LOCATION = XSI + 4;
    public static final int XSI_SCHEMA_LOCATION_TYPE = XSI + 5;


    private static String[] localNames = new String[1023];
    private static HashMap<String, Integer> lookup = new HashMap<>(1023);
    public static StructuredQName[] errorVariables = {
            new StructuredQName("err", NamespaceConstant.ERR, "code"),
            new StructuredQName("err", NamespaceConstant.ERR, "description"),
            new StructuredQName("err", NamespaceConstant.ERR, "value"),
            new StructuredQName("err", NamespaceConstant.ERR, "module"),
            new StructuredQName("err", NamespaceConstant.ERR, "line-number"),
            new StructuredQName("err", NamespaceConstant.ERR, "column-number"),
            new StructuredQName("err", NamespaceConstant.ERR, "additional")
    };
    // key is an expanded QName in Clark notation
    // value is a fingerprint, as a java.lang.Integer

    private StandardNames() {
        //pool = namePool;
    }

    private static void bindXSLTName(int constant, String localName) {
        localNames[constant] = localName;
        lookup.put('{' + NamespaceConstant.XSLT + '}' + localName, constant);
    }

    private static void bindSaxonName(int constant, String localName) {
        localNames[constant] = localName;
        lookup.put('{' + NamespaceConstant.SAXON + '}' + localName, constant);
    }

    private static void bindXMLName(int constant, String localName) {
        localNames[constant] = localName;
        lookup.put('{' + NamespaceConstant.XML + '}' + localName, constant);
    }

    private static void bindXSName(int constant, String localName) {
        localNames[constant] = localName;
        lookup.put('{' + NamespaceConstant.SCHEMA + '}' + localName, constant);
    }

    private static void bindXSIName(int constant, String localName) {
        localNames[constant] = localName;
        lookup.put('{' + NamespaceConstant.SCHEMA_INSTANCE + '}' + localName, constant);
    }

    static {

        bindXSLTName(XSL_ACCEPT, "accept");
        bindXSLTName(XSL_ACCUMULATOR, "accumulator");
        bindXSLTName(XSL_ACCUMULATOR_RULE, "accumulator-rule");
        bindXSLTName(XSL_ANALYZE_STRING, "analyze-string");
        bindXSLTName(XSL_APPLY_IMPORTS, "apply-imports");
        bindXSLTName(XSL_APPLY_TEMPLATES, "apply-templates");
        bindXSLTName(XSL_ASSERT, "assert");
        bindXSLTName(XSL_ATTRIBUTE, "attribute");
        bindXSLTName(XSL_ATTRIBUTE_SET, "attribute-set");
        bindXSLTName(XSL_BREAK, "break");
        bindXSLTName(XSL_CALL_TEMPLATE, "call-template");
        bindXSLTName(XSL_CATCH, "catch");
        bindXSLTName(XSL_CHARACTER_MAP, "character-map");
        bindXSLTName(XSL_CHOOSE, "choose");
        bindXSLTName(XSL_COMMENT, "comment");
        bindXSLTName(XSL_CONTEXT_ITEM, "context-item");
        bindXSLTName(XSL_COPY, "copy");
        bindXSLTName(XSL_COPY_OF, "copy-of");
        bindXSLTName(XSL_DECIMAL_FORMAT, "decimal-format");
        bindXSLTName(XSL_DOCUMENT, "document");
        bindXSLTName(XSL_ELEMENT, "element");
        bindXSLTName(XSL_EVALUATE, "evaluate");
        bindXSLTName(XSL_EXPOSE, "expose");
        bindXSLTName(XSL_FALLBACK, "fallback");
        bindXSLTName(XSL_FOR_EACH, "for-each");
        bindXSLTName(XSL_FOR_EACH_GROUP, "for-each-group");
        bindXSLTName(XSL_FORK, "fork");
        bindXSLTName(XSL_FUNCTION, "function");
        bindXSLTName(XSL_GLOBAL_CONTEXT_ITEM, "global-context-item");
        bindXSLTName(XSL_IF, "if");
        bindXSLTName(XSL_IMPORT, "import");
        bindXSLTName(XSL_IMPORT_SCHEMA, "import-schema");
        bindXSLTName(XSL_INCLUDE, "include");
        bindXSLTName(XSL_ITERATE, "iterate");
        bindXSLTName(XSL_KEY, "key");
        bindXSLTName(XSL_MAP, "map");
        bindXSLTName(XSL_MAP_ENTRY, "map-entry");
        bindXSLTName(XSL_MATCHING_SUBSTRING, "matching-substring");
        bindXSLTName(XSL_MERGE, "merge");
        bindXSLTName(XSL_MERGE_SOURCE, "merge-source");
        bindXSLTName(XSL_MERGE_ACTION, "merge-action");
        bindXSLTName(XSL_MERGE_KEY, "merge-key");
        bindXSLTName(XSL_MESSAGE, "message");
        bindXSLTName(XSL_MODE, "mode");
        bindXSLTName(XSL_NEXT_MATCH, "next-match");
        bindXSLTName(XSL_NUMBER, "number");
        bindXSLTName(XSL_NAMESPACE, "namespace");
        bindXSLTName(XSL_NAMESPACE_ALIAS, "namespace-alias");
        bindXSLTName(XSL_NEXT_ITERATION, "next-iteration");
        bindXSLTName(XSL_NON_MATCHING_SUBSTRING, "non-matching-substring");
        bindXSLTName(XSL_ON_COMPLETION, "on-completion");
        bindXSLTName(XSL_ON_EMPTY, "on-empty");
        bindXSLTName(XSL_ON_NON_EMPTY, "on-non-empty");
        bindXSLTName(XSL_OTHERWISE, "otherwise");
        bindXSLTName(XSL_OUTPUT, "output");
        bindXSLTName(XSL_OUTPUT_CHARACTER, "output-character");
        bindXSLTName(XSL_OVERRIDE, "override");
        bindXSLTName(XSL_PACKAGE, "package");
        bindXSLTName(XSL_PARAM, "param");
        bindXSLTName(XSL_PERFORM_SORT, "perform-sort");
        bindXSLTName(XSL_PRESERVE_SPACE, "preserve-space");
        bindXSLTName(XSL_PROCESSING_INSTRUCTION, "processing-instruction");
        bindXSLTName(XSL_RESULT_DOCUMENT, "result-document");
        bindXSLTName(XSL_SEQUENCE, "sequence");
        bindXSLTName(XSL_SORT, "sort");
        bindXSLTName(XSL_SOURCE_DOCUMENT, "source-document");
        bindXSLTName(XSL_STRIP_SPACE, "strip-space");
        bindXSLTName(XSL_STYLESHEET, "stylesheet");
        bindXSLTName(XSL_TEMPLATE, "template");
        bindXSLTName(XSL_TEXT, "text");
        bindXSLTName(XSL_TRANSFORM, "transform");
        bindXSLTName(XSL_TRY, "try");
        bindXSLTName(XSL_USE_PACKAGE, "use-package");
        bindXSLTName(XSL_VALUE_OF, "value-of");
        bindXSLTName(XSL_VARIABLE, "variable");
        bindXSLTName(XSL_WITH_PARAM, "with-param");
        bindXSLTName(XSL_WHEN, "when");
        bindXSLTName(XSL_WHERE_POPULATED, "where-populated");


        bindXSLTName(XSL_DEFAULT_COLLATION, "default-collation");
        bindXSLTName(XSL_DEFAULT_MODE, "default-mode");
        bindXSLTName(XSL_DEFAULT_VALIDATION, "default-validation");
        bindXSLTName(XSL_EXPAND_TEXT, "expand-text");
        bindXSLTName(XSL_EXCLUDE_RESULT_PREFIXES, "exclude-result-prefixes");
        bindXSLTName(XSL_EXTENSION_ELEMENT_PREFIXES, "extension-element-prefixes");
        bindXSLTName(XSL_INHERIT_NAMESPACES, "inherit-namespaces");
        bindXSLTName(XSL_TYPE, "type");
        bindXSLTName(XSL_USE_ATTRIBUTE_SETS, "use-attribute-sets");
        bindXSLTName(XSL_USE_WHEN, "use-when");
        bindXSLTName(XSL_VALIDATION, "validation");
        bindXSLTName(XSL_VERSION, "version");
        bindXSLTName(XSL_XPATH_DEFAULT_NAMESPACE, "xpath-default-namespace");


        bindSaxonName(SAXON_ASSIGN, "assign");
        bindSaxonName(SAXON_DEEP_UPDATE, "deep-update");
        bindSaxonName(SAXON_DISTINCT, "distinct");
        bindSaxonName(SAXON_DO, "do");
        bindSaxonName(SAXON_DOCTYPE, "doctype");
        bindSaxonName(SAXON_ENTITY_REF, "entity-ref");
        bindSaxonName(SAXON_ORDER, "order");
        bindSaxonName(SAXON_WHILE, "while");
        bindSaxonName(SAXON_PARAM, "param");
        bindSaxonName(SAXON_PREPROCESS, "preprocess");


        bindXMLName(XML_BASE, "base");
        bindXMLName(XML_SPACE, "space");
        bindXMLName(XML_LANG, "lang");
        bindXMLName(XML_ID, "id");
        bindXMLName(XML_LANG_TYPE, "_langType");
        bindXMLName(XML_SPACE_TYPE, "_spaceType");

        bindXSName(XS_STRING, "string");
        bindXSName(XS_BOOLEAN, "boolean");
        bindXSName(XS_DECIMAL, "decimal");
        bindXSName(XS_FLOAT, "float");
        bindXSName(XS_DOUBLE, "double");
        bindXSName(XS_DURATION, "duration");
        bindXSName(XS_DATE_TIME, "dateTime");
        bindXSName(XS_TIME, "time");
        bindXSName(XS_DATE, "date");
        bindXSName(XS_G_YEAR_MONTH, "gYearMonth");
        bindXSName(XS_G_YEAR, "gYear");
        bindXSName(XS_G_MONTH_DAY, "gMonthDay");
        bindXSName(XS_G_DAY, "gDay");
        bindXSName(XS_G_MONTH, "gMonth");
        bindXSName(XS_HEX_BINARY, "hexBinary");
        bindXSName(XS_BASE64_BINARY, "base64Binary");
        bindXSName(XS_ANY_URI, "anyURI");
        bindXSName(XS_QNAME, "QName");
        bindXSName(XS_NOTATION, "NOTATION");
        bindXSName(XS_NUMERIC, "numeric");
        bindXSName(XS_INTEGER, "integer");
        bindXSName(XS_NON_POSITIVE_INTEGER, "nonPositiveInteger");
        bindXSName(XS_NEGATIVE_INTEGER, "negativeInteger");
        bindXSName(XS_LONG, "long");
        bindXSName(XS_INT, "int");
        bindXSName(XS_SHORT, "short");
        bindXSName(XS_BYTE, "byte");
        bindXSName(XS_NON_NEGATIVE_INTEGER, "nonNegativeInteger");
        bindXSName(XS_POSITIVE_INTEGER, "positiveInteger");
        bindXSName(XS_UNSIGNED_LONG, "unsignedLong");
        bindXSName(XS_UNSIGNED_INT, "unsignedInt");
        bindXSName(XS_UNSIGNED_SHORT, "unsignedShort");
        bindXSName(XS_UNSIGNED_BYTE, "unsignedByte");
        bindXSName(XS_NORMALIZED_STRING, "normalizedString");
        bindXSName(XS_TOKEN, "token");
        bindXSName(XS_LANGUAGE, "language");
        bindXSName(XS_NMTOKEN, "NMTOKEN");
        bindXSName(XS_NMTOKENS, "NMTOKENS");      // NB: list type
        bindXSName(XS_NAME, "Name");
        bindXSName(XS_NCNAME, "NCName");
        bindXSName(XS_ID, "ID");
        bindXSName(XS_IDREF, "IDREF");
        bindXSName(XS_IDREFS, "IDREFS");      // NB: list type
        bindXSName(XS_ENTITY, "ENTITY");
        bindXSName(XS_ENTITIES, "ENTITIES");      // NB: list type
        bindXSName(XS_DATE_TIME_STAMP, "dateTimeStamp");

        bindXSName(XS_ANY_TYPE, "anyType");
        bindXSName(XS_ANY_SIMPLE_TYPE, "anySimpleType");
        bindXSName(XS_INVALID_NAME, "invalidName");
        bindXSName(XS_ERROR, "error");

        bindXSName(XS_ALL, "all");
        bindXSName(XS_ALTERNATIVE, "alternative");
        bindXSName(XS_ANNOTATION, "annotation");
        bindXSName(XS_ANY, "any");
        bindXSName(XS_ANY_ATTRIBUTE, "anyAttribute");
        bindXSName(XS_APPINFO, "appinfo");
        bindXSName(XS_ASSERT, "assert");
        bindXSName(XS_ASSERTION, "assertion");
        bindXSName(XS_ATTRIBUTE, "attribute");
        bindXSName(XS_ATTRIBUTE_GROUP, "attributeGroup");
        bindXSName(XS_CHOICE, "choice");
        bindXSName(XS_COMPLEX_CONTENT, "complexContent");
        bindXSName(XS_COMPLEX_TYPE, "complexType");
        bindXSName(XS_DEFAULT_OPEN_CONTENT, "defaultOpenContent");
        bindXSName(XS_DOCUMENTATION, "documentation");
        bindXSName(XS_ELEMENT, "element");
        bindXSName(XS_ENUMERATION, "enumeration");
        bindXSName(XS_EXPLICIT_TIMEZONE, "explicitTimezone");
        bindXSName(XS_EXTENSION, "extension");
        bindXSName(XS_FIELD, "field");
        bindXSName(XS_FRACTION_DIGITS, "fractionDigits");
        bindXSName(XS_GROUP, "group");
        bindXSName(XS_IMPORT, "import");
        bindXSName(XS_INCLUDE, "include");
        bindXSName(XS_KEY, "key");
        bindXSName(XS_KEYREF, "keyref");
        bindXSName(XS_LENGTH, "length");
        bindXSName(XS_LIST, "list");
        bindXSName(XS_MAX_EXCLUSIVE, "maxExclusive");
        bindXSName(XS_MAX_INCLUSIVE, "maxInclusive");
        bindXSName(XS_MAX_LENGTH, "maxLength");
        bindXSName(XS_MAX_SCALE, "maxScale");
        bindXSName(XS_MIN_EXCLUSIVE, "minExclusive");
        bindXSName(XS_MIN_INCLUSIVE, "minInclusive");
        bindXSName(XS_MIN_LENGTH, "minLength");
        bindXSName(XS_MIN_SCALE, "minScale");
        bindXSName(XS_notation, "notation");
        bindXSName(XS_OPEN_CONTENT, "openContent");
        bindXSName(XS_OVERRIDE, "override");
        bindXSName(XS_PATTERN, "pattern");
        bindXSName(XS_REDEFINE, "redefine");
        bindXSName(XS_RESTRICTION, "restriction");
        bindXSName(XS_SCHEMA, "schema");
        bindXSName(XS_SELECTOR, "selector");
        bindXSName(XS_SEQUENCE, "sequence");
        bindXSName(XS_SIMPLE_CONTENT, "simpleContent");
        bindXSName(XS_SIMPLE_TYPE, "simpleType");
        bindXSName(XS_TOTAL_DIGITS, "totalDigits");
        bindXSName(XS_UNION, "union");
        bindXSName(XS_UNIQUE, "unique");
        bindXSName(XS_WHITE_SPACE, "whiteSpace");

        bindXSName(XS_UNTYPED, "untyped");
        bindXSName(XS_UNTYPED_ATOMIC, "untypedAtomic");
        bindXSName(XS_ANY_ATOMIC_TYPE, "anyAtomicType");
        bindXSName(XS_YEAR_MONTH_DURATION, "yearMonthDuration");
        bindXSName(XS_DAY_TIME_DURATION, "dayTimeDuration");

        bindXSIName(XSI_TYPE, "type");
        bindXSIName(XSI_NIL, "nil");
        bindXSIName(XSI_SCHEMA_LOCATION, "schemaLocation");
        bindXSIName(XSI_NO_NAMESPACE_SCHEMA_LOCATION, "noNamespaceSchemaLocation");
        bindXSIName(XSI_SCHEMA_LOCATION_TYPE, "anonymous_schemaLocationType");
    }

    /**
     * Get the fingerprint of a system-defined name, from its URI and local name
     *
     * @param uri       the namespace URI
     * @param localName the local part of the name
     * @return the standard fingerprint, or -1 if this is not a built-in name
     */

    public static int getFingerprint(String uri, String localName) {
        Integer fp = lookup.get('{' + uri + '}' + localName);
        if (fp == null) {
            return -1;
        } else {
            return fp;
        }
    }

    /**
     * Get the local part of a system-defined name
     *
     * @param fingerprint the fingerprint of the name
     * @return the local part of the name
     */

    public static String getLocalName(int fingerprint) {
        return localNames[fingerprint];
    }

    /**
     * Get the namespace URI part of a system-defined name
     *
     * @param fingerprint the fingerprint of the name
     * @return the namespace URI part of the name
     * @throws IllegalArgumentException if the fingerprint does not define a known name
     */

    /*@NotNull*/
    public static String getURI(int fingerprint) {
        int c = fingerprint >> 7;
        switch (c) {
            case DFLT_NS:
                return "";
            case XSL_NS:
                return NamespaceConstant.XSLT;
            case SAXON_NS:
                return NamespaceConstant.SAXON;
            case XML_NS:
                return NamespaceConstant.XML;
            case XS_NS:
                return NamespaceConstant.SCHEMA;
            case XSI_NS:
                return NamespaceConstant.SCHEMA_INSTANCE;
            default:
                throw new IllegalArgumentException("Unknown system fingerprint " + fingerprint);
        }
    }

    /**
     * Get the Clark form of a system-defined name, given its name code or fingerprint
     *
     * @param fingerprint the fingerprint of the name
     * @return the local name if the name is in the null namespace, or "{uri}local" otherwise.
     */

    public static String getClarkName(int fingerprint) {
        String uri = getURI(fingerprint);
        if (uri.isEmpty()) {
            return getLocalName(fingerprint);
        } else {
            return '{' + uri + '}' + getLocalName(fingerprint);
        }
    }

    /**
     * Get the conventional prefix of a system-defined name
     *
     * @param fingerprint the fingerprint of the name
     * @return the conventional prefix of the name
     */

    public static String getPrefix(int fingerprint) {
        int c = fingerprint >> 7;
        switch (c) {
            case DFLT_NS:
                return "";
            case XSL_NS:
                return "xsl";
            case SAXON_NS:
                return "saxon";
            case XML_NS:
                return "xml";
            case XS_NS:
                return "xs";
            case XSI_NS:
                return "xsi";
            default:
                return null;
        }
    }

    /**
     * Get the lexical display form of a system-defined name
     *
     * @param fingerprint the fingerprint of the name
     * @return the lexical display form of the name, using a conventional prefix
     */

    public static String getDisplayName(int fingerprint) {
        if (fingerprint == -1) {
            return "(anonymous type)";
        }
        if (fingerprint > 1023) {
            return "(" + fingerprint + ')';
        }
        if ((fingerprint >> 7) == DFLT) {
            return getLocalName(fingerprint);
        }
        return getPrefix(fingerprint) + ':' + getLocalName(fingerprint);
    }

    /**
     * Get a StructuredQName representing a system-defined name
     *
     * @param fingerprint the fingerprint of the name
     * @return a StructuredQName representing the system-defined name. The prefix will be the
     * conventional prefix for the system namespace
     */

    public static StructuredQName getStructuredQName(int fingerprint) {
        return new StructuredQName(getPrefix(fingerprint), getURI(fingerprint), getLocalName(fingerprint));
    }

    /**
     * Get a StructuredQName representing a system-defined name, with no prefix
     *
     * @param fingerprint the fingerprint of the name
     * @return a StructuredQName representing the system-defined name, with no prefix
     */

    public static StructuredQName getUnprefixedQName(int fingerprint) {
        return new StructuredQName("", getURI(fingerprint), getLocalName(fingerprint));
    }

    /**
     * A commonly-used name held in static:
     */

    public final static StructuredQName SQ_XS_INVALID_NAME = getStructuredQName(XS_INVALID_NAME);

}

