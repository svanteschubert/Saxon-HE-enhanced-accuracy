////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.parser.OperatorSymbol;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.jnode.AnyJNodeType;
import net.sf.saxon.ma.jnode.RootJNodeType;
import net.sf.saxon.ma.jnode.SpecificJNodeType;
import net.sf.saxon.ma.map.KeyValuePair;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.ma.map.RecordType;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.UnionQNameTest;
import net.sf.saxon.pattern.qname.*;
import net.sf.saxon.str.StringView;
import net.sf.saxon.str.UnicodeChar;
import net.sf.saxon.type.gnode.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.*;

/**
 * <p>An AlphaCode is a compact, context-independent string representation of a SequenceType</p>
 *
 * <p>The syntax actually handles ItemTypes as well as SequenceTypes; and in addition, it can handle the two examples
 * of NodeTests that are not item types, namely *:local and uri:*. (These become legal item types in 4.0).
 * It can therefore be used in the SEF wherever a SequenceType, ItemType, or NodeTest is required.</p>
 *
 * <p>The first character of an alphacode is the occurrence indicator. This is one of: * (zero or more),
 * + (one or more), ? (zero or one), 0 (exactly zero), 1 (exactly one). If the first character is
 * not one of these, then "1" is assumed; but the occurrence indicator is generally omitted only when
 * representing an item type as distinct from a sequence type.</p>
 *
 * <p>The occurrence indicator is immediately followed by the "primary alphacode" for the item type.
 * These are chosen so that alphacode(T) is a prefix of alphacode(U) if and only if T is a supertype of U.
 * For example, the primary alphacode for xs:integer is "ADI", and the primary alphacode for
 * xs:decimal is "AD", reflecting the fact that xs:integer is a subtype of xs:decimal.
 * The primary alphacodes are as follows:</p>
 *
 * <ul>
 * <li>"" (zero-length string): item()</li>
 *
 * <li>A: xs:anyAtomicType</li>
 * <li>AB: xs:boolean</li>
 *
 * <li>AS: xs:string</li>
 * <li>ASN: xs:normalizedString</li>
 * <li>ASNT: xs:token</li>
 * <li>ASNTL: xs:language</li>
 * <li>ASNTK: xs:NMTOKEN </li>
 * <li>ASNTN: xs:Name</li>
 * <li>ASNTNC: xs:NCName</li>
 * <li>ASNTNCI: xs:ID</li>
 * <li>ASNTNCE: xs:ENTITY</li>
 * <li>ASNTNCR: xs:IDREF</li>
 *
 * <li>ASE s{s1|s2|s3}: enum. The enum values are separated by vertical bars, and special
 * characters (whitespace, vertical bar, backslash, closing brace) are backslash-escaped.</li>
 *
 * <li>AQ: xs:QName</li>
 * <li>AU: xs:anyURI</li>
 * <li>AA: xs:date</li>
 * <li>AM: xs:dateTime</li>
 * <li>AMP: xs:dateTimeStamp</li>
 * <li>AT: xs:time </li>
 * <li>AR: xs:duration  </li>
 * <li>ARD: xs:dayTimeDuration </li>
 * <li>ARY: xs:yearMonthDuration </li>
 * <li>AG: xs:gYear </li>
 * <li>AH: xs:gYearMonth </li>
 * <li>AI: xs:gMonth </li>
 * <li>AJ: xs:gMonthDay </li>
 * <li>AK: xs:gDay </li>
 *
 * <li>AD: xs:decimal </li>
 * <li>ADI: xs:integer </li>
 * <li>ADIN: xs:nonPositiveInteger</li>
 * <li>ADINN: xs:negativeInteger </li>
 * <li>ADIP: xs:nonNegativeInteger</li>
 * <li>ADIPP: xs:positiveInteger </li>
 * <li>ADIPL: xs:unsignedLong </li>
 * <li>ADIPLI: xs:unsignedInt </li>
 * <li>ADIPLIS: xs:unsignedShort </li>
 * <li>ADIPLISB: xs:unsignedByte </li>
 * <li>ADIL: xs:long  </li>
 * <li>ADILI: xs:int </li>
 * <li>ADILIS: xs:short </li>
 * <li>ADILISB: xs:byte </li>
 *
 * <li>AO: xs:double </li>
 * <li>AF: xs:float </li>
 * <li>A2: xs:base64Binary </li>
 * <li>AX: xs:hexBinary  </li>
 * <li>AZ: xs:untypedAtomic </li>
 *
 * <li>N: node() (=xnode)</li>
 * <li>NE: element(*) </li>
 * <li>NA: attribute(*) </li>
 * <li>NT: text() </li>
 * <li>NC: comment() </li>
 * <li>NP: processing-instruction() </li>
 * <li>ND: document-node() </li>
 * <li>NN: namespace-node() </li>
 *
 * <li>F: function(*) </li>
 * <li>FM: map(*) -- including record types</li>
 * <li>FMR: named record type</li>
 * <li>FA: array(*) </li>
 *
 * <li>J: jnode(). Parameters k for the key type (defaulting to string), s for the key value (as a string), c for the content type</li>
 * <li>JR: root jnode(). Parameter c for the content type.</li>
 *
 * <li>E: xs:error </li>
 *
 * <li>U: union (choice) of the alternatives listed in the 'm' property</li>
 *
 * <li>X: external (wrapped) object </li>
 * <li>XJ: external Java object </li>
 * <li>XN: external .NET object </li>
 * <li>XS: external Javascript object </li>
 * </ul>
 *
 * <p>Every item belongs to one or more of these types, and there is always a "most specific" type, which is the
 * one that we choose.</p>
 *
 * <p>Following the occurrence indicator and primary alphacode are zero or more supplementary codes. Each is
 * preceded by a single space, is identified by a single letter, and is followed by a parameter value.
 * For example the sequence type "element(BOOK)" is coded as "1NE nQ{}BOOK" - here 1 is the occurrence indicator,
 * NE indicates an element node, and nQ{}BOOK is the required element name. The identifying letter here
 * is "n". The supplementary codes (which may appear in any order) are as follows:</p>
 *
 * <p>n - Name, as a URI-qualified name. Used for node names when the primary alphacode is one of (NE, NA, NP).
 * Also used for the XSD type name when the type is a user-defined atomic or union type: the basic alphacode
 * then represents the lowest common supertype that is a built-in type.  (Note: we assume that type names
 * are globally unique. This cannot be guaranteed when deploying a SEF file: the schema at the receiving end might
 * vary from that of the sender.) Also used for the class name in the case of external object types (in this case
 * the namespace part will always be "Q{}"). Note that strictly speaking, the forms *:name and name:* can appear
 * in a NameTest, but never in a SequenceType. However, they can be represented in alphacodes using the syntax
 * "n*:name" and "nQ{uri}*" respectively. The syntax "~localname" is used for a name in the XSD namespace.</p>
 *
 * <p>n - may also represent a choice of names, written in square brackets, space separated, for example
 * "n[Q{}A Q{}B]</p>
 *
 * <p>n - also used for named record types (primary code FMR)</p>
 *
 * <ul>
 *
 * <li><p>c - Node content type (XSD type annotation), as a URI-qualified name optionally followed by "?" to indicate
 * nillable. The syntax "~localname" is used for a name in the XSD namespace. Optionally present when the
 * basic code is (NE, NA); omitted for NE when the content is xs:untyped, and for NA when the content is
 * xs:untypedAtomic. Only relevant for schema-aware code.</p></li>
 *
 * <li><p>k - Key type, present when the basic code is FM (i.e. for maps), omitted if the key type is xs:anyAtomicType.
 * The value is the alphacode of the key type, enclosed in square brackets: it will always start with "1A".</p></li>
 *
 * <li><p>v - Value type, present when the basic code is (FM, FA) (i.e. for maps and arrays), omitted if the
 * value type is item()*. The value is the alphacode of the value type, enclosed in square brackets. For example
 * the alphacode for array(xs:string+)* is "*FA v[+AS]".</p></li>
 *
 * <li><p>r - Return type, always present for functions. The value is the alphacode of the return type, enclosed in
 * square brackets.</p></li>
 *
 * <li><p>a - Argument types, always present for functions. The value is an array of alphacodes, enclosed in square
 * brackets and separated by commas. For example, the alphacode for the function fn:dateTime#2 (with signature
 * ($arg1 as xs:date?, $arg2 as xs:time?) as xs:dateTime?) is "1F r[?AM] a[?AA,?AT]"</p>
 *
 *     <p>Also used for record types: indicates the types declared for the fields of the record type.
 *     As a special case, a self-reference field within a record type is represented by "%.." where
 *     % is the occurrence indicator, for example "1.." for a self-reference field with cardinality one.</p></li>
 *
 * <li><p>m - Member types of an anonymous union type. The value is an array of alphacodes for the member
 * types (these will always be atomic types), enclosed in square brackets and comma-separated. The basic code
 * in this case will be "A", indicating xs:anyAtomicType or "U" indicating a general choice type (not necessarily
 * atomic). This is not used for the built-in union type
 * xs:numeric, nor for user-defined atomic types defined in a schema; it is used only for anonymous union types
 * defined using the Saxon extension syntax "union(a, b, c)".</p></li>
 *
 * <li><p>e - Element type of a document-node() type, present optionally when the basic code is ND. The value is an
 * alphacode, which will always start with "1NE".</p></li>
 *
 * <li><p>f, F - Fields of a record type (previously called tuple type). The value is a comma-separated list
 * of tokens, enclosed in square brackets, where each token comprises the name of the component,
 * optionally followed by a question mark if the field is optional. Any ASCII characters in the field
 * name that are not valid NCName characters are escaped by preceding them with a backslash.</p></li>
 *
 * <li><p>i, u, d - Venn type. The item type is the intersection, union, or difference of two item types.
 * The letter "i", "u", or "d" indicates intersection, union, or difference respectively, followed by a list
 * of (currently always two) item types enclosed in square brackets and separated by a comma. The principal
 * type will typically be "N" or "NE". Saxon uses venn types internally to give a more precise inferred type
 * for expressions; it is probably largely unused at run-time, and can therefore be safely ignored when reading a
 * SEF file.</p></li>
 *
 * <li><p>s - Enumeration string. Takes the form <code>s{s1|s2|s3} where s1, s2, s3 are the enumeration constants,
 * with special characters (whitespace, vertical bar, backslash, closing brace) backslash-escaped.</code></p></li>
 *
 * </ul>
 *
 * <p>Named union types have a basic alphacode of "A", followed by the name of the union type in the form
 * "A nQ{uri}local". The syntax "~localname" is used for a name in the XSD namespace, so the built-in union types
 * xs:numeric and xs:error are represented as "A n~numeric" and "A n~error" respectively.</p>
 * <p>
 * TODO: the documentation for union types is not aligned with the current implementation
 */

public class AlphaCode {

    /**
     * Callback interface whereby the AlphaCode parser notifies the caller of events
     * arising during parsing
     *
     * @param <T> The type of container produced by the application to represent the
     *            contents of the AlphaCode. Typically either a Java Map or an XDM MapItem.
     */

    private interface ParserCallBack<T> {
        /**
         * Create an instance of the container. This is used not only for the top-level
         * type, but for any nested types, e.g. in function arguments
         *
         * @return a new container instance
         */
        T makeContainer();

        /**
         * Set a string-valued property in the container
         *
         * @param container the container to be updated
         * @param key       the name of the property
         * @param value     the value of the property
         */
        void setStringProperty(T container, String key, String value);

        /**
         * Set a property in the container whose value is a list of strings
         *
         * @param container the container to be updated
         * @param key       the name of the property
         * @param value     the value of the property
         */
        void setMultiStringProperty(T container, String key, List<String> value);

        /**
         * Set a property in the container whose value is another type
         *
         * @param container the container to be updated
         * @param key       the name of the property
         * @param value     the value of the property, as a nested type
         */
        void setTypeProperty(T container, String key, T value);

        /**
         * Set a property in the container whose value is a list of types
         *
         * @param container the container to be updated
         * @param key       the name of the property
         * @param value     the value of the property, as a list of nested types
         */
        void setMultiTypeProperty(T container, String key, List<T> value);
    }

    /**
     * Implementation of the callback where the container is an AlphaCodeTree
     */

    private static class TreeCallBack implements ParserCallBack<AlphaCodeTree> {

        @Override
        public AlphaCodeTree makeContainer() {
            return new AlphaCodeTree();
        }

        @Override
        public void setStringProperty(AlphaCodeTree tree, String key, String value) {
            switch (key) {
                case "o" -> // cardinality
                        tree.cardinality = value;
                case "p" -> // principal item type
                        tree.principal = value;
                case "n" -> // element or attribute name
                        tree.name = value;
                case "c" -> // element or attribute content type
                        tree.content = value;
                case "z" -> // nillable flag
                        tree.nillable = true;
                default ->
                        throw new IllegalArgumentException("Bad alphacode component " + key);
            }
        }

        @Override
        public void setMultiStringProperty(AlphaCodeTree tree, String key, List<String> value) {
            switch (key) {
                case "f" ->  // fields in tuple type
                        tree.fieldNames = value;
                case "s" -> tree.enumValues = value;
                case "optionalFields" -> {
                    tree.optionalFieldNames = new HashSet<>();
                    tree.optionalFieldNames.addAll(value);
                }
                default -> throw new IllegalArgumentException("Bad alphacode component " + key);
            }
        }

        @Override
        public void setTypeProperty(AlphaCodeTree tree, String key, AlphaCodeTree value) {
            switch (key) {
                case "k" -> // key type of map
                        tree.keyType = value;
                case "v" -> // value type of map, member type of array
                        tree.valueType = value;
                case "r" -> // result type of function
                        tree.resultType = value;
                case "e" -> // element type of document type
                        tree.elementType = value;
                default ->
                        throw new IllegalArgumentException("Bad alphacode component " + key);
            }
        }

        @Override
        public void setMultiTypeProperty(AlphaCodeTree tree, String key, List<AlphaCodeTree> value) {
            switch (key) {
                case "a" -> // argument types of a function
                        tree.argTypes = value;
                case "m" -> // member types of a union
                        tree.members = value;
                case "i" -> {
                    tree.vennOperands = value.toArray(new AlphaCodeTree[]{});
                    tree.vennOperator = OperatorSymbol.INTERSECT;
                }
                case "u" -> {
                    tree.vennOperands = value.toArray(new AlphaCodeTree[]{});
                    tree.vennOperator = OperatorSymbol.UNION;
                }
                case "d" -> {
                    tree.vennOperands = value.toArray(new AlphaCodeTree[]{});
                    tree.vennOperator = OperatorSymbol.EXCEPT;
                }
                default -> throw new IllegalArgumentException("Bad alphacode component " + key);
            }
        }

    }


    /**
     * Inner class implementing the parser for AlphaCodes
     *
     * @param <T> the type of container used by the calling application to hold the result of parsing
     */

    private static class AlphaCodeParser<T> {
        private final String input;
        private int position = 0;
        private final ParserCallBack<T> callBack;

        public AlphaCodeParser(String input, ParserCallBack<T> callBack) {
            this.input = input;
            this.callBack = callBack;
        }

        private int nextChar() {
            if (position >= input.length()) {
                return -1;
            }
            return input.charAt(position++);
        }

        private String nextToken() {
            int inBraces = 0;
            int start = position;
            while (position < input.length()) {
                char ch = input.charAt(position++);
                switch (ch) {
                    case '{':
                        inBraces++;
                        break;
                    case '}':
                        inBraces--;
                        break;
                    case ']':
                    case ',':
                        if (inBraces == 0) {
                            return input.substring(start, --position);
                        }
                        break;
                    case ' ':
                        if (inBraces == 0) {
                            return input.substring(start, position - 1);
                        }
                        break;
                    default:
                        // no action
                }
            }
            return input.substring(start, position);
        }

        private void expect(char c) {
            int d = nextChar();
            if (d != c) {
                throw new IllegalStateException("Expected '" + c + "', found '" + (d == -1 ? "<eof>" : ("" + (char) d)) + "'");
            }
        }

        T parseType(T parent) {
            T container = callBack.makeContainer();
            int indicator = nextChar();
            if (indicator < 0) {
                callBack.setStringProperty(container, "o", "1");
            } else if (("*+1?0".indexOf((char) indicator) >= 0)) {
                if (indicator == 0xB0) {
                    indicator = '0';
                }
                callBack.setStringProperty(container, "o", ("" + (char) indicator));
            } else {
                callBack.setStringProperty(container, "o", "1");
                position--;
            }
            String primary = nextToken();
            callBack.setStringProperty(container, "p", primary);

            while (position < input.length()) {
                char c = input.charAt(position);
                switch (c) {
                    case ']':
                    case ',':
                        return container;
                    case ' ':
                        position++;
                        break;
                    case 'n': {
                        position++;
                        String token = nextToken();
                        if (token.startsWith("~")) {
                            token = "Q{" + NamespaceConstant.SCHEMA + "}" + token.substring(1);
                        }
                        callBack.setStringProperty(container, "n", token);
                        break;
                    }
                    case 'c': {
                        position++;
                        String token = nextToken();
                        if (token.startsWith("~")) {
                            token = "Q{" + NamespaceConstant.SCHEMA + "}" + token.substring(1);
                        }
                        if (token.endsWith("?")) {
                            // nillability: represented in alphaTree as "z":"1"
                            callBack.setStringProperty(container, "z", "1");
                            token = token.substring(0, token.length() - 1);
                        }
                        callBack.setStringProperty(container, "c", token);
                        break;
                    }
                    case 'k':
                    case 'r':
                    case 'v':
                    case 'e':
                        position++;
                        expect('[');
                        T nestedType = parseType(parent);
                        expect(']');
                        callBack.setTypeProperty(container, "" + c, nestedType);
                        break;
                    case 'a':
                    case 'm':
                    case 'i':
                    case 'u':
                    case 'd':
                        position++;
                        expect('[');
                        List<T> nestedTypes = new ArrayList<>();
                        if (input.charAt(position) == ']') {
                            position++;
                            callBack.setMultiTypeProperty(container, "" + c, nestedTypes);
                        } else {
                            while (true) {
                                nestedTypes.add(parseType(container));
                                if (input.charAt(position) == ',') {
                                    position++;
                                } else {
                                    expect(']');
                                    callBack.setMultiTypeProperty(container, "" + c, nestedTypes);
                                    break;
                                }
                            }
                        }
                        break;
                    case 'f': { // tuple field types
                        position++;
                        expect('[');
                        List<String> fieldNames = new ArrayList<>();
                        List<String> optionalFieldNames = new ArrayList<>();
                        StringBuilder currName = new StringBuilder();
                        boolean escaped = false;
                        while (true) {
                            char ch = input.charAt(position++);
                            if (ch == '\\' && !escaped) {
                                escaped = true;
                            } else if (ch == '?' && !escaped) {
                                optionalFieldNames.add(currName.toString());
                            } else if (ch == ',' && !escaped) {
                                fieldNames.add(currName.toString());
                                currName.setLength(0);
                                escaped = false;
                            } else if (ch == ']' && !escaped) {
                                fieldNames.add(currName.toString());
                                currName.setLength(0);
                                callBack.setMultiStringProperty(container, "f", fieldNames);
                                callBack.setMultiStringProperty(container, "optionalFields", optionalFieldNames);
                                break;
                            } else {
                                currName.append(ch);
                                escaped = false;
                            }
                        }
                        break;
                    }
                    case 's': { // enumeration values
                        position++;
                        expect('{');
                        List<String> values = new ArrayList<>();
                        StringBuilder currentValue = new StringBuilder();
                        boolean escaped = false;
                        while (true) {
                            char ch = input.charAt(position++);
                            if (escaped) {
                                escaped = false;
                                currentValue.append(switch (ch) {
                                    case 's' -> ' ';
                                    case 'n' -> '\n';
                                    case 'r' -> '\r';
                                    case 't' -> '\t';
                                    case '|' -> '|';
                                    case '\\' -> '\\';
                                    case '}' -> '}';
                                    default -> throw new IllegalArgumentException("Illegal escape sequence: " + ch);
                                });
                            } else if (ch == '\\') {
                                escaped = true;
                            } else if (ch == '|') {
                                values.add(currentValue.toString());
                                currentValue.setLength(0);
                            } else if (ch == '}') {
                                values.add(currentValue.toString());
                                callBack.setMultiStringProperty(container, "s", values);
                                break;
                            } else {
                                currentValue.append(ch);
                            }
                        }
                        break;
                    }
                    default:
                        throw new IllegalStateException("Expected one of n|c|t|k|r|v|a|u|s, found '" + c + "'");
                }
            }
            return container;
        }
    }


    private final static StringValue SV_O = new StringValue(new UnicodeChar('o'));
    private final static StringValue SV_P = new StringValue(new UnicodeChar('p'));

    /**
     * Serialize the XDM map representation of an alphacode
     *
     * @param map the alphacode represented as an XDM map
     * @return the corresponding alphacode as a string
     */

    public static String fromXdmMap(MapItem map) {
        // TODO: may need updating. Used when running the XX compiler under Saxon/J
        StringBuilder out = new StringBuilder();

        StringValue indicator = (StringValue) map.get(SV_O);
        out.append(indicator == null ? "1" : indicator.getStringValue());

        StringValue alphaCode = (StringValue) map.get(SV_P);
        out.append(alphaCode == null ? "" : alphaCode.getStringValue());

        out.append(" ");

        for (KeyValuePair kvp : map.keyValuePairs()) {
            String key = kvp.key().getStringValue();
            switch (key) {
                case "o":
                case "p":
                    break;
                case "n":
                case "c":
                case "t":
                    out.append(key);
                    out.append(((StringValue) kvp.value()).getStringValue());
                    out.append(" ");
                    break;
                case "k":
                case "r":
                case "v":
                case "e":
                    out.append(key);
                    out.append('[');
                    out.append(fromXdmMap((MapItem) kvp.value()));
                    out.append(']');
                    out.append(" ");
                    break;
                case "a":
                case "u":
                    out.append(key);
                    out.append('[');
                    ArrayItem types = (ArrayItem) kvp.value();
                    boolean first = true;
                    for (GroundedValue t : types.members()) {
                        if (first) {
                            first = false;
                        } else {
                            out.append(",");
                        }
                        out.append(fromXdmMap((MapItem) t));
                    }
                    out.append(']');
                    out.append(" ");
                    break;
                default:
                    throw new IllegalStateException("Unexpected key '" + key + "'");
            }
        }
        return out.toString();
    }

    /**
     * Structured representation of the components of an AlphaCode
     */

    private static class AlphaCodeTree {
        String cardinality;
        String principal;
        String name;
        String content;
        boolean nillable;
        List<AlphaCodeTree> members;
        AlphaCodeTree keyType;
        AlphaCodeTree valueType;
        AlphaCodeTree resultType;
        List<AlphaCodeTree> argTypes;
        AlphaCodeTree elementType;
        OperatorSymbol vennOperator;
        AlphaCodeTree[] vennOperands;
        List<String> fieldNames;
        List<String> enumValues;
        Set<String> optionalFieldNames;
        AlphaCodeTree selfReference;
    }

    /**
     * Convert an AlphaCode to a SequenceType
     *
     * @param input  the input alphacode
     * @param config
     * @param schema the schema (which must contain any user-defined types that are
     *               referenced in the Alphacode)
     * @return the corresponding SequenceType
     * @throws IllegalArgumentException if the input is not a valid AlphaCode
     */

    public static SequenceType toSequenceType(String input, Configuration config, Schema schema) {
        TreeCallBack callBack = new TreeCallBack();
        AlphaCodeParser<AlphaCodeTree> parser = new AlphaCodeParser<>(input, callBack);
        AlphaCodeTree tree = parser.parseType(null);
        return sequenceTypeFromTree(tree, schema);
    }

    /**
     * Convert an AlphaCode to an ItemType. The occurrence indicator of the alphacode
     * may be omitted, or may be "1": any other value is treated as an error.
     *
     * @param input  the input alphacode
     * @param config
     * @param schema the imported schema (which must contain any user-defined types that are
     *               referenced in the Alphacode)
     * @return the corresponding SequenceType
     * @throws IllegalArgumentException if the input is not a valid AlphaCode
     */

    public static ItemType toItemType(String input, Configuration config, Schema schema) {
        SequenceType st = toSequenceType(input, config, schema);
        if (st.getCardinality() != StaticProperty.EXACTLY_ONE) {
            throw new IllegalArgumentException("Supplied alphacode has a cardinality other than 1");
        }
        return st.getPrimaryType();
    }

    /**
     * Convert a tree (that results from parsing an AlphaCode) to a corresponding SequenceType
     *
     * @param tree   the tree resulting from parsing
     * @param schema the imported schema (which must contain any user-defined types that are
     *               referenced in the Alphacode)
     * @return the corresponding SequenceType
     */

    private static SequenceType sequenceTypeFromTree(AlphaCodeTree tree, Schema schema) {
        Configuration config = schema.getConfiguration();
        String principal = tree.principal;
        ItemType itemType = null;
        if (principal.isEmpty()) {
            itemType = AnyItemType.INSTANCE;
        } else if (principal.equals("ASE")) {
            List<String> values = tree.enumValues;
            itemType = EnumerationUnionType.of(values.toArray(new String[]{}));
        } else if (principal.startsWith("A")) {
            BuiltInAtomicType builtIn = BuiltInAtomicType.fromAlphaCode(principal);
            if (builtIn == null) {
                throw new IllegalArgumentException("Unknown type " + principal);
            }
            itemType = builtIn;
            if (tree.name != null) {
                StructuredQName name = StructuredQName.fromEQName40(tree.name);
                SchemaType type = schema.getSchemaType(name);
                if (!(type instanceof PlainType)) {
                    throw new IllegalArgumentException("Schema type " + tree.name + " is not known");
                }
                itemType = (PlainType) type;
            } else if (builtIn == BuiltInAtomicType.ANY_ATOMIC && tree.members != null) {
                List<ItemType> members = new ArrayList<>();
                for (AlphaCodeTree m : tree.members) {
                    SequenceType st = sequenceTypeFromTree(m, schema);
                    if (st.getPrimaryType().isAtomicType()) {
                        final AtomicType primaryType = (AtomicType) st.getPrimaryType();
                        members.add(primaryType);
                    }
                }
                itemType = new LocalUnionType(members);
            }

        } else if (principal.startsWith("N")) {

            String contentName = tree.content;
            StructuredQName contentQName;
            SchemaType contentType = null;
            boolean nillable = tree.nillable;
            if (contentName != null) {
                contentQName = StructuredQName.fromEQName40(contentName);
                contentType = schema.getSchemaType(contentQName);
                if (contentType == null) {
                    throw new IllegalArgumentException("Unknown type " + contentName);
                }
//                contentTest = new ContentTypeTest(principal.equals("NE") ? Type.ELEMENT : Type.ATTRIBUTE,
//                                                  AnyNodeTest.getInstance(), contentType, nillable, config);
            }
            if (tree.vennOperands != null) {
                throw new UnsupportedOperationException(); // TEMPORARILY
//                if (tree.vennOperands.length == 2) {
//                    ItemType nt0 = sequenceTypeFromTree(tree.vennOperands[0], schema).getPrimaryType();
//                    ItemType nt1 = sequenceTypeFromTree(tree.vennOperands[1], schema).getPrimaryType();
//                    itemType = new CombinedNodeTest(nt0, tree.vennOperator, nt1);
//                } else {
//                    // Dangerous short-cut here - we know this will be a union of node kind tests
//                    assert tree.vennOperator == OperatorSymbol.UNION;
//                    UType u = UType.VOID;
//                    for (int i = 0; i < tree.vennOperands.length; i++) {
//                        ItemType it = sequenceTypeFromTree(tree.vennOperands[i], schema).getPrimaryType();
//                        assert it instanceof NodeKindType;
//                        u = u.union(it.getUType());
//                    }
//                    itemType = new MultipleNodeKindTest(u);
//                }
            } else {
//                int kind = Type.XNODE;
//                if (principal.length() >= 2) {
//                    kind = switch (principal.substring(0, 2)) {
//                        case "NT" -> Type.TEXT;
//                        case "NC" -> Type.COMMENT;
//                        case "NN" -> Type.NAMESPACE;
//                        case "NP" -> Type.PROCESSING_INSTRUCTION;
//                        case "ND" -> Type.DOCUMENT;
//                        case "NE" -> Type.ELEMENT;
//                        case "NA" -> Type.ATTRIBUTE;
//                        default -> kind;
//                    };
//                }
                String name = tree.name;
                QNameTest qNameTest = null;
                if (name != null) {
                    qNameTest = parseNameTest(tree.name, config);
                }
                StructuredQName qName = (qNameTest instanceof SpecificQNameTest spec) ? spec.getStructuredQName() : null;
                switch (principal) {
                    case "N":
                        itemType = AnyXNodeType.getInstance();
                        break;
                    case "NT":
                        itemType = NodeKindType.TEXT;
                        break;
                    case "NC":
                        itemType = NodeKindType.COMMENT;
                        break;
                    case "NN":
                        if (name == null) {
                            itemType = NodeKindType.NAMESPACE;
                        } else {
                            itemType = new NamedXNodeType(Type.NAMESPACE, qNameTest, config);
                        }
                        break;
                    case "NP":
                        if (name == null) {
                            itemType = NodeKindType.PROCESSING_INSTRUCTION;
                        } else {
                            itemType = new NamedXNodeType(Type.PROCESSING_INSTRUCTION, qNameTest, config);
                        }
                        break;
                    case "ND":
                        AlphaCodeTree elementType = tree.elementType;
                        if (elementType == null) {
                            itemType = NodeKindType.DOCUMENT;
                        } else {
                            ItemType e = sequenceTypeFromTree(elementType, schema).getPrimaryType();
                            itemType = new DocumentNodeType((XNodeType) e);
                        }
                        break;
                    case "NE":
                        if (contentType == null) {
                            itemType = name == null
                                    ? NodeKindType.ELEMENT
                                    : new NamedXNodeType(Type.ELEMENT,
                                                         qNameTest,
                                                         config);
                            ;
                        } else {
                            itemType = new NamedXNodeType(Type.ELEMENT, qNameTest, contentType, nillable, config);
                        }
                        break;
                    case "NA":
                        if (contentType == null) {
                            itemType = name == null
                                    ? NodeKindType.ATTRIBUTE
                                    : new NamedXNodeType(Type.ATTRIBUTE, qNameTest, config);
                            ;
                        } else {
                            itemType = new NamedXNodeType(Type.ATTRIBUTE, qNameTest, contentType, false, config);
                        }
                        break;
                    case "NES": {
                        assert name != null;
                        IElementDecl decl = schema.getElementDecl(qName);
                        if (decl != null) {
                            try {
                                itemType = schema.makeSchemaElementTest(decl.getFingerprint());
                            } catch (MissingComponentException e) {
                                //
                            } catch (SchemaException e) {
                                throw new IllegalArgumentException(e);
                            }
                        }
                        if (itemType == null) {
                            itemType = new NamedXNodeType(Type.ELEMENT, qName, config);
                        }
                        break;
                    }
                    case "NAS": {
                        assert qName != null;
                        IAttributeDecl decl = schema.getAttributeDecl(qName);
                        if (decl != null) {
                            try {
                                itemType = schema.makeSchemaAttributeTest(decl.getFingerprint());
                            } catch (MissingComponentException e) {
                                //
                            } catch (SchemaException e) {
                                throw new IllegalArgumentException(e);
                            }
                        }
                        if (itemType == null) {
                            itemType = new NamedXNodeType(Type.ATTRIBUTE, qName, config);
                        }
                        break;
                    }
                    default:
                        itemType = AnyXNodeType.getInstance();
                        break;
                }
            }

        } else if (principal.startsWith("G")) {
            itemType = AnyGNodeType.getInstance();
        } else if (principal.equals("J")) {
            SequenceType valueType = tree.valueType != null ? sequenceTypeFromTree(tree.valueType, schema) : null;
            AtomicValue keyValue = null;
            if (valueType == null) {
                itemType = AnyJNodeType.getInstance();
            } else if (tree.keyType != null) {
                AtomicType keyItemType = (AtomicType) sequenceTypeFromTree(tree.keyType, schema).getPrimaryType();
                String lexicalKey = tree.content;
                try {
                    keyValue = (AtomicValue) keyItemType.getTypedValue(StringView.of(lexicalKey), null, ConversionRules.DEFAULT);
                } catch (ValidationException e) {
                    throw new AssertionError(e);
                }
                itemType = new SpecificJNodeType(keyValue, valueType);
            } else {
                itemType = new SpecificJNodeType(valueType);
            }
            //itemType = AnyJNodeType.getInstance();
        } else if (principal.equals("JR")) {
            itemType = new RootJNodeType(sequenceTypeFromTree(tree.valueType, schema));
        } else if (principal.startsWith("F")) {
            if (principal.equals("FA")) {
                AlphaCodeTree valueType = tree.valueType;
                if (valueType == null) {
                    itemType = ArrayItemType.ANY_ARRAY_TYPE;
                } else {
                    itemType = new ArrayItemType(sequenceTypeFromTree(valueType, schema));
                }
            } else if (principal.startsWith("FM")) {
                if (tree.name != null) {
                    StructuredQName qName = StructuredQName.fromEQName40(tree.name);
                    if (qName.hasURI(NamespaceUri.FN)) {
                        itemType = config.getBuiltInRecordType(qName.getLocalPart());
                    } else {
                        throw new IllegalArgumentException("Named record type not in FN namespace");
                    }
                } else if (tree.fieldNames == null) {
                    AlphaCodeTree keyType = tree.keyType;
                    AlphaCodeTree valueType = tree.valueType;
                    if (keyType != null && valueType != null) {
                        PlainType a = (PlainType) sequenceTypeFromTree(keyType, schema).getPrimaryType();
                        SequenceType v = sequenceTypeFromTree(valueType, schema);
                        itemType = new MapType(a, v);
                    } else {
                        itemType = MapType.ANY_MAP_TYPE;
                    }
                } else {
                    List<SequenceType> fieldTypes = new ArrayList<>(tree.argTypes.size());
                    RecordType recordTest = new RecordType();
                    for (AlphaCodeTree t : tree.argTypes) {
                        fieldTypes.add(sequenceTypeFromTree(t, schema));
                    }
                    recordTest.setDetails(tree.fieldNames, fieldTypes, tree.optionalFieldNames, false);
                    itemType = recordTest;
                }
            } else {
                AlphaCodeTree returnType = tree.resultType;
                List<AlphaCodeTree> argTypes = tree.argTypes;
                if (argTypes == null) {
                    itemType = AnyFunctionType.INSTANCE;
                } else {
                    SequenceType r;
                    if (returnType == null) {
                        r = SequenceType.ANY_SEQUENCE;
                    } else {
                        r = sequenceTypeFromTree(returnType, schema);
                    }
                    SequenceType[] a = new SequenceType[argTypes.size()];
                    for (int i = 0; i < a.length; i++) {
                        a[i] = sequenceTypeFromTree(argTypes.get(i), schema);
                    }
                    itemType = new SpecificFunctionType(a, r);
                }
            }
        } else if (principal.equals("U")) {
            // general choice item type introduced in 4.0
            List<ItemType> members = new ArrayList<>();
            boolean allAtomic = true;
            for (AlphaCodeTree m : tree.members) {
                SequenceType st = sequenceTypeFromTree(m, schema);
                final ItemType primaryType = st.getPrimaryType();
                members.add(primaryType);
                if (!primaryType.isAtomicType()) {
                    allAtomic = false;
                }
            }
            if (allAtomic) {
                itemType = new LocalUnionType(members);
            } else {
                itemType = new ChoiceItemType(members);
            }

        } else if (principal.startsWith("X")) {
            Class<?> theClass = Object.class;
            if (tree.name != null) {
                String className = StructuredQName.fromEQName40(tree.name).getLocalPart();
                try {
                    theClass = Class.forName(className);
                } catch (ClassNotFoundException e) {
                    theClass = Object.class;
                }
            }
            itemType = JavaExternalObjectType.of(theClass);
        }
        String indicator = tree.cardinality;
        int cardinality = Cardinality.fromOccurrenceIndicator(indicator);
        return SequenceType.makeSequenceType(itemType, cardinality);
    }

    private static QNameTest parseNameTest(String test, Configuration config) {
        if (test.contains("|")) {
            // TODO: disregard a vertical bar within a namespace URI
            StringTokenizer st = new StringTokenizer(test, "|");
            List<QNameTest> tests = new ArrayList<>();
            while (st.hasMoreTokens()) {
                String token = st.nextToken();
                tests.add(parseNameTest(token, config));
            }
            return new UnionQNameTest(tests);
        } else if (test.contains("*")) {
            if (test.startsWith("*:")) {
                return new LocalQNameTest(test.substring(2));
            } else if (test.endsWith("}*")) {
                String uri = test.substring(2, test.length() - 2);
                return new NamespaceQNameTest(NamespaceUri.of(uri));
            } else {
                return AnyQNameTest.getInstance();
            }
        } else {
            return new SpecificQNameTest(StructuredQName.fromEQName40(test), config.getNamePool());
        }
    }

    private static AlphaCodeTree makeTree(SequenceType sequenceType) {
        AlphaCodeTree tree = makeTree(sequenceType.getPrimaryType());
        if (sequenceType.getCardinality() != StaticProperty.EXACTLY_ONE) {
            tree.cardinality = Cardinality.getOccurrenceIndicator(sequenceType.getCardinality());
        }
        return tree;
    }

    private static AlphaCodeTree makeTree(ItemType primary) {
        AlphaCodeTree result = new AlphaCodeTree();
        result.principal = primary.getBasicAlphaCode();
        result.cardinality = "1";
        if (primary instanceof EnumerationUnionType enumType) {
            result.principal = "ASE";
            result.enumValues = new ArrayList<>();
            for (ItemType et : enumType.getMemberTypes()) {
                result.enumValues.add(((SingletonEnumType) et).getValue().toString());
            }
        } else if (primary instanceof SingletonEnumType sEnumType) {
            result.principal = "ASE";
            result.enumValues = new ArrayList<>(1);
            result.enumValues.add(sEnumType.getValue().toString());
        } else if (primary instanceof AtomicType at && !at.isBuiltInType()) {
            result.name = at.getEQName();
        } else if (primary instanceof UnionType) {
            StructuredQName name = ((UnionType) primary).getTypeName();
            if (name.hasURI(NamespaceUri.SCHEMA)) {
                // built-in union types xs:numeric, xs:error
                result.name = "~" + name.getLocalPart();
            } else if (name.hasURI(NamespaceUri.ANONYMOUS)) {
                // Anonymous union types: Saxon extension defined using the syntax union(A, B, C)
                try {
                    List<AlphaCodeTree> memberMaps = new ArrayList<>();
                    for (PlainType pt : ((UnionType) primary).getPlainMemberTypes()) {
                        memberMaps.add(makeTree(pt));
                    }
                    result.members = memberMaps;
                } catch (MissingComponentException e) {
                    // no action
                }
            } else {
                result.name = name.getEQName();
            }
        } else if (primary instanceof ChoiceItemType) {
            List<AlphaCodeTree> memberMaps = new ArrayList<>();
            for (ItemType pt : ((ChoiceItemType) primary).getAlternatives()) {
                memberMaps.add(makeTree(pt));
            }
            result.members = memberMaps;
        } else if (primary instanceof SchemaNodeTest) {
            StructuredQName name = ((SchemaNodeTest) primary).getNodeName();
            result.name = name.getEQName();
        } else if (primary instanceof NamedXNodeType) {
            QNameTest nt = ((NamedXNodeType) primary).getAllowedNodeNames();
            if (nt != AnyQNameTest.getInstance()) {
                result.name = ((NamedXNodeType) primary).getAllowedNodeNames().exportQNameTest();
            }
            result.content = ((NamedXNodeType) primary).getContentType().getEQName();
            result.nillable = ((NamedXNodeType) primary).isNillable();
        } else if (primary instanceof DocumentNodeType) {
            ItemType content = ((DocumentNodeType) primary).getElementTest();
            result.elementType = makeTree(content);
        } else if (primary instanceof FunctionItemType) {
            if (primary instanceof ArrayItemType) {
                SequenceType memberType = ((ArrayItemType) primary).getMemberType();
                if (memberType != SequenceType.ANY_SEQUENCE) {
                    result.valueType = makeTree(memberType);
                }
            } else if (primary instanceof RecordType rec) {
                if (rec.getName() != null) {
                    result.name = rec.getName().getEQName();
                } else {
                    result.optionalFieldNames = new HashSet<>();
                    result.fieldNames = new ArrayList<>();
                    result.argTypes = new ArrayList<>();
                    for (String s : rec.getFieldNames()) {
                        result.fieldNames.add(s);
                        SequenceType fieldType = rec.getFieldType(s);
                        result.argTypes.add(makeTree(fieldType));
                        if (rec.isOptionalField(s)) {
                            result.optionalFieldNames.add(s);
                        }
                    }
                }
            } else if (primary instanceof MapType) {
                PlainType keyType = ((MapType) primary).getKeyType();
                if (keyType != BuiltInAtomicType.ANY_ATOMIC) {
                    result.keyType = makeTree(keyType);
                }
                SequenceType valueType = ((MapType) primary).getValueType();
                if (valueType != SequenceType.ANY_SEQUENCE) {
                    result.valueType = makeTree(valueType);
                }
            } else {
                SequenceType resultType = ((FunctionItemType) primary).getResultType();
                if (resultType != SequenceType.ANY_SEQUENCE) {
                    result.resultType = makeTree(resultType);
                }
                SequenceType[] argTypes = ((FunctionItemType) primary).getArgumentTypes();
                if (argTypes != null) {
                    List<AlphaCodeTree> argMaps = new ArrayList<>();
                    for (SequenceType argType : argTypes) {
                        argMaps.add(makeTree(argType));
                    }
                    result.argTypes = argMaps;
                }
            }
        } else if (primary instanceof SpecificJNodeType jt) {
            if (jt.getValueType() != null) {
                result.valueType = makeTree(jt.getValueType());
            }
            if (jt.getSelector() != null) {
                result.keyType = makeTree(jt.getSelector().getItemType());
                result.content = jt.getSelector().getStringValue();
            }
        } else if (primary instanceof RootJNodeType rjt) {
            result.valueType = makeTree(rjt.getValueType());
        } else if (primary instanceof ExternalObjectType) {
            result.name = ((ExternalObjectType) primary).getName();
        }
        return result;
    }

    private static String abbreviateEQName(String in) {
        if (in.startsWith("Q{" + NamespaceConstant.SCHEMA + "}")) {
            return "~" + in.substring(("Q{" + NamespaceConstant.SCHEMA + "}").length());
        } else {
            return in;
        }
    }

    private static void alphaCodeFromTree(AlphaCodeTree tree, boolean withCardinality, StringBuilder sb) {
        if (withCardinality) {
            sb.append(tree.cardinality);
        }
        sb.append(tree.principal);

        if (tree.principal.equals("ASE")) {
            // enumeration type
            sb.append(" s{");
            boolean first = true;
            for (String s : tree.enumValues) {
                if (first) {
                    first = false;
                } else {
                    sb.append('|');
                }
                sb.append(escapeEnumValue(s));
            }
            sb.append('}');
        }
        if (tree.name != null) {
            sb.append(" n").append(abbreviateEQName(tree.name.replace(' ', '|')));
        }
        if (tree.content != null) {
            String display = abbreviateEQName(tree.content) + (tree.nillable ? "?" : "");
            if (!display.equals("~anyType?")) {
                sb.append(" c").append(abbreviateEQName(tree.content));
                if (tree.nillable) {
                    sb.append("?");
                }
            }
        }
        if (tree.keyType != null) {
            sb.append(" k[");
            alphaCodeFromTree(tree.keyType, false, sb);
            sb.append("]");
        }
        if (tree.valueType != null) {
            sb.append(" v[");
            alphaCodeFromTree(tree.valueType, true, sb);
            sb.append("]");
        }
        if (tree.resultType != null) {
            sb.append(" r[");
            alphaCodeFromTree(tree.resultType, true, sb);
            sb.append("]");
        }
        if (tree.argTypes != null) {
            sb.append(" a[");
            boolean first = true;
            for (AlphaCodeTree a : tree.argTypes) {
                if (first) {
                    first = false;
                } else {
                    sb.append(",");
                }
                alphaCodeFromTree(a, true, sb);
            }
            sb.append("]");
        }
        if (tree.members != null) {
            sb.append(" m[");
            boolean first = true;
            for (AlphaCodeTree a : tree.members) {
                if (first) {
                    first = false;
                } else {
                    sb.append(",");
                }
                alphaCodeFromTree(a, false, sb);
            }
            sb.append("]");
        }
        if (tree.elementType != null) {
            sb.append(" e[");
            alphaCodeFromTree(tree.elementType, false, sb);
            sb.append("]");
        }
        if (tree.vennOperands != null) {
            String operator =
                    tree.vennOperator == OperatorSymbol.INTERSECT ? "i"
                            : tree.vennOperator == OperatorSymbol.UNION ? "u"
                            : "d";
            sb.append(" ")
                    .append(operator)
                    .append("[");
            for (int i = 0; i < tree.vennOperands.length; i++) {
                if (i != 0) {
                    sb.append(",");
                }
                alphaCodeFromTree(tree.vennOperands[i], false, sb);
            }
            sb.append("]");
        }
        if (tree.fieldNames != null) {
            sb.append(" f[");
            boolean first = true;
            for (String s : tree.fieldNames) {
                if (!first) {
                    sb.append(",");
                } else {
                    first = false;
                }
                sb.append(escapeNCName(s));
                if (tree.optionalFieldNames.contains(s)) {
                    sb.append('?');
                }
            }
            sb.append("]");
        }
    }

    /**
     * Escape a supplied name by prefixing any characters not allowed in an NCName
     * with a backslash
     *
     * @param ncName a name (which may or may not be a valid NCName)
     * @return the supplied name unchanged if it is a valid NCName; otherwise, the
     * name with all invalid ASCII characters backslash-escaped. Invalid non-ASCII
     * character are left unchanged.
     */

    private static String escapeNCName(String ncName) {
        if (NameChecker.isValidNCName(ncName)) {
            return ncName;
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ncName.length(); i++) {
                char c = ncName.charAt(i);
                if (c >= 128 || NameChecker.isNCNameChar(c)) {
                    sb.append(c);
                } else {
                    sb.append("\\").append(c);
                }
            }
            return sb.toString();
        }
    }

    /**
     * Escape a supplied enumeration value
     *
     * @param value a string
     * @return the string with special characters escaped.
     */

    private static String escapeEnumValue(String value) {
        StringBuilder ub = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char cp = value.charAt(i);
            switch (cp) {
                case ' ':
                    ub.append('\\').append('s');
                    break;
                case '\t':
                    ub.append('\\').append('t');
                    break;
                case '\r':
                    ub.append('\\').append('r');
                    break;
                case '\n':
                    ub.append('\\').append('n');
                    break;
                case '|':
                    ub.append('\\').append('|');
                    break;
                case '}':
                    ub.append('\\').append('}');
                    break;
                case '\\':
                    ub.append('\\').append('\\');
                    break;
                default:
                    ub.append(cp);
                    break;
            }
        }
        return ub.toString();
    }

    /**
     * Convert an item type to an alphacode
     *
     * @param type the item type to be converted
     * @return the corresponding alphacode. Note that this will have no occurrence indicator.
     */

    public static String fromItemType(ItemType type) {
        AlphaCodeTree tree = makeTree(type);
        StringBuilder sb = new StringBuilder();
        alphaCodeFromTree(tree, false, sb);
        return sb.toString().trim();
    }

    /**
     * Convert a sequence type to an alphacode
     *
     * @param type the sequence type to be converted
     * @return the corresponding alphacode (including occurrence indicator as the first character)
     */

    public static String fromSequenceType(SequenceType type) {
        if (type == SequenceType.EMPTY_SEQUENCE) {
            return "0";
        }
        String s = fromItemType(type.getPrimaryType());
        if (type.getCardinality() == StaticProperty.EXACTLY_ONE) {
            return "1" + s;
        } else {
            return Cardinality.getOccurrenceIndicator(type.getCardinality()) + s;
        }
    }

}

