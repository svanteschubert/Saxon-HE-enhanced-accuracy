////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.expr.parser.XPathParser;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.arrays.SimpleArrayItem;
import net.sf.saxon.ma.map.*;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.*;
import net.sf.saxon.sxpath.IndependentContext;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceExtent;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * An AlphaCode is a compact, context-independent string representation of a SequenceType
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
     * Implementation of the callback where the container is an XDM MapItem, using
     * the {@link DictionaryMap} implementation
     */
    private static class MapItemCallBack implements ParserCallBack<DictionaryMap> {

        @Override
        public DictionaryMap makeContainer() {
            return new DictionaryMap();
        }

        @Override
        public void setStringProperty(DictionaryMap container, String key, String value) {
            container.initialPut(key, new StringValue(value));
        }

        @Override
        public void setMultiStringProperty(DictionaryMap container, String key, List<String> value) {
            List<StringValue> xdmValue = new ArrayList<>();
            for (String v : value) {
                xdmValue.add(new StringValue(v));
            }
            container.initialPut(key, new SequenceExtent(xdmValue));
        }

        @Override
        public void setTypeProperty(DictionaryMap container, String key, DictionaryMap value) {
            container.initialPut(key, value);
        }

        @Override
        public void setMultiTypeProperty(DictionaryMap container, String key, List<DictionaryMap> value) {
            List<GroundedValue> contents = new ArrayList<>(value);
            container.initialPut(key, new SimpleArrayItem(contents));
        }

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
                case "o": // cardinality
                    tree.cardinality = value;
                    break;
                case "p": // principal item type
                    tree.principal = value;
                    break;
                case "n": // element or attribute name
                    tree.name = value;
                    break;
                case "c": // element or attribute content type
                    tree.content = value;
                    break;
                case "z": // nillable flag
                    tree.nillable = true;
                    break;
                case "x": // extensible tuple type flag
                    tree.extensibleTupleType = true;
                    break;
                default:
                    throw new IllegalArgumentException("Bad alphacode component " + key);
            }
        }

        @Override
        public void setMultiStringProperty(AlphaCodeTree tree, String key, List<String> value) {
            if (key.equals("f")) { // fields in tuple type
                tree.fieldNames = value;
            } else {
                throw new IllegalArgumentException("Bad alphacode component " + key);
            }
        }

        @Override
        public void setTypeProperty(AlphaCodeTree tree, String key, AlphaCodeTree value) {
            switch (key) {
                case "k": // key type of map
                    tree.keyType = value;
                    break;
                case "v": // value type of map, member type of array
                    tree.valueType = value;
                    break;
                case "r": // result type of function
                    tree.resultType = value;
                    break;
                case "e": // element type of document type
                    tree.elementType = value;
                    break;
                default:
                    throw new IllegalArgumentException("Bad alphacode component " + key);
            }
        }

        @Override
        public void setMultiTypeProperty(AlphaCodeTree tree, String key, List<AlphaCodeTree> value) {
            switch (key) {
                case "a": // argument types of a function
                    tree.argTypes = value;
                    break;
                case "m": // member types of a union
                    tree.members = value;
                    break;
                case "i": // intersection of types
                    tree.vennOperands = value.toArray(new AlphaCodeTree[]{});
                    tree.vennOperator = Token.INTERSECT;
                    break;
                case "u": // union of types
                    tree.vennOperands = value.toArray(new AlphaCodeTree[]{});
                    tree.vennOperator = Token.UNION;
                    break;
                case "d": // difference of types
                    tree.vennOperands = value.toArray(new AlphaCodeTree[]{});
                    tree.vennOperator = Token.EXCEPT;
                    break;
                default:
                    throw new IllegalArgumentException("Bad alphacode component " + key);
            }
        }

    }


    /**
     * Inner class implementing the parser for Alphacodes
     *
     * @param <T> the type of container used by the calling application to hold the result of parsing
     */

    private static class AlphaCodeParser<T> {
        private String input;
        private int position = 0;
        private ParserCallBack<T> callBack;

        private AlphaCodeParser(String input, ParserCallBack<T> callBack) {
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
                throw new IllegalStateException("Expected '" + c + "', found '" + (d == -1 ? "<eof>" : (char)d) + "'");
            }
        }

        T parseType() {
            T container = callBack.makeContainer();
            int indicator = nextChar();
            if (indicator < 0) {
                callBack.setStringProperty(container, "o", "1");
            } else if (("*+1?0\u00B0".indexOf((char)indicator) >= 0)) {      // TODO: \u00B0 is obsolescent
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
                    case 'n':
                    case 'c':
                        position++;
                        String token = nextToken();
                        if (token.startsWith("~")) {
                            token = "Q{" + NamespaceConstant.SCHEMA + "}" + token.substring(1);
                        }
                        if (c == 'c' && token.endsWith("?")) {
                            // nillability: represented in alphaTree as "z":"1"
                            callBack.setStringProperty(container, "z", "1");
                            token = token.substring(0, token.length()-1);
                        }
                        callBack.setStringProperty(container, "" + c, token);
                        break;
                    case 'k':
                    case 'r':
                    case 'v':
                    case 'e':
                        position++;
                        expect('[');
                        T nestedType = parseType();
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
                                nestedTypes.add(parseType());
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
                    case 'f':  // tuple field types
                    case 'F':  // tuple field types, extensible
                        if (c == 'F') {
                            callBack.setStringProperty(container, "x", "1");
                        }
                        position++;
                        expect('[');
                        List<String> fieldNames = new ArrayList<>();
                        StringBuilder currName = new StringBuilder();
                        boolean escaped = false;
                        while (true) {
                            char ch = input.charAt(position++);
                            if (ch == '\\' && !escaped) {
                                escaped = true;
                            } else if (ch == ',' && !escaped) {
                                fieldNames.add(currName.toString());
                                currName.setLength(0);
                                escaped = false;
                            } else if (ch == ']' && !escaped) {
                                fieldNames.add(currName.toString());
                                currName.setLength(0);
                                callBack.setMultiStringProperty(container, "f", fieldNames);
                                break;
                            } else {
                                currName.append(ch);
                                escaped = false;
                            }
                        }
                        break;
                    default:
                        throw new IllegalStateException("Expected one of n|c|t|k|r|v|a|u, found '" + c + "'");
                }
            }
            return container;
        }
    }

    /**
     * Parse an AlphaCode into an XDM map
     *
     * @param input the input alphacode
     * @return the resulting map
     * @throws IllegalArgumentException if the input is not a valid AlphaCode
     */

    public static MapItem toXdmMap(String input) {
        MapItemCallBack callBack = new MapItemCallBack();
        AlphaCodeParser<DictionaryMap> parser = new AlphaCodeParser<>(input, callBack);
        return parser.parseType();
    }

    public static String fromXdmMap(MapItem map) {
        // TODO: may need updating. Used when running the XX compiler under Saxon/J
        StringBuilder out = new StringBuilder();

        StringValue indicator = (StringValue) map.get(new StringValue("o"));
        out.append(indicator == null ? "1" : indicator.getStringValue());

        StringValue alphaCode = (StringValue) map.get(new StringValue("p"));
        out.append(alphaCode == null ? "" : alphaCode.getStringValue());

        out.append(" ");

        for (KeyValuePair kvp : map.keyValuePairs()) {
            String key = kvp.key.getStringValue();
            switch (key) {
                case "o":
                case "p":
                    break;
                case "n":
                case "c":
                case "t":
                    out.append(key);
                    out.append(((StringValue) kvp.value).getStringValue());
                    out.append(" ");
                    break;
                case "k":
                case "r":
                case "v":
                case "e":
                    out.append(key);
                    out.append('[');
                    out.append(fromXdmMap((MapItem) kvp.value));
                    out.append(']');
                    out.append(" ");
                    break;
                case "a":
                case "u":
                    out.append(key);
                    out.append('[');
                    ArrayItem types = (ArrayItem) kvp.value;
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
        int vennOperator;
        AlphaCodeTree[] vennOperands;
        List<String> fieldNames;
        boolean extensibleTupleType;
    }

    /**
     * Convert an AlphaCode to a SequenceType
     *
     * @param input  the input alphacode
     * @param config the Saxon Configuration (which must contain any user-defined types that are
     *               referenced in the Alphacode)
     * @return the corresponding SequenceType
     * @throws IllegalArgumentException if the input is not a valid AlphaCode
     */

    public static SequenceType toSequenceType(String input, Configuration config) {
        TreeCallBack callBack = new TreeCallBack();
        AlphaCodeParser<AlphaCodeTree> parser = new AlphaCodeParser<>(input, callBack);
        AlphaCodeTree tree = parser.parseType();
        return sequenceTypeFromTree(tree, config);
    }

    /**
     * Convert an AlphaCode to an ItemType. The occurrence indicator of the alphacode
     * may be omitted, or may be "1": any other value is treated as an error.
     *
     * @param input  the input alphacode
     * @param config the Saxon Configuration (which must contain any user-defined types that are
     *               referenced in the Alphacode)
     * @return the corresponding SequenceType
     * @throws IllegalArgumentException if the input is not a valid AlphaCode
     */

    public static ItemType toItemType(String input, Configuration config) {
        SequenceType st = toSequenceType(input, config);
        if (st.getCardinality() != StaticProperty.EXACTLY_ONE) {
            throw new IllegalArgumentException("Supplied alphacode has a cardinality other than 1");
        }
        return st.getPrimaryType();
    }

    /**
     * Convert a tree (that results from parsing an AlphaCode) to a corresponding SequenceType
     *
     * @param tree   the tree resulting from parsing
     * @param config the Saxon Configuration (which must contain any user-defined types that are
     *               referenced in the Alphacode)
     * @return the corresponding SequenceType
     */

    private static SequenceType sequenceTypeFromTree(AlphaCodeTree tree, Configuration config) {
        String principal = tree.principal;
        ItemType itemType = null;
        if (principal.isEmpty()) {
            itemType = AnyItemType.getInstance();
        } else if (principal.startsWith("A")) {
            BuiltInAtomicType builtIn = BuiltInAtomicType.fromAlphaCode(principal);
            if (builtIn == null) {
                throw new IllegalArgumentException("Unknown type " + principal);
            }
            itemType = builtIn;
            if (tree.name != null) {
                SchemaType type = config.getSchemaType(StructuredQName.fromEQName(tree.name));
                if (!(type instanceof PlainType)) {
                    throw new IllegalArgumentException("Schema type " + tree.name + " is not known");
                }
                itemType = (PlainType)type;
            } else if (builtIn == BuiltInAtomicType.ANY_ATOMIC && tree.members != null) {
                List<AtomicType> members = new ArrayList<>();
                for (AlphaCodeTree m : tree.members) {
                    SequenceType st = sequenceTypeFromTree(m, config);
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
            ContentTypeTest contentTest = null;
            boolean nillable = tree.nillable;
            if (contentName != null) {
                contentQName = StructuredQName.fromEQName(contentName);
                SchemaType contentType = config.getSchemaType(contentQName);
                if (contentType == null) {
                    throw new IllegalArgumentException("Unknown type " + contentName);
                }
                contentTest = new ContentTypeTest(principal.equals("NE") ? Type.ELEMENT : Type.ATTRIBUTE,
                                                  contentType, config, nillable);
            }
            if (tree.vennOperands != null) {
                if (tree.vennOperands.length == 2) {
                    NodeTest nt0 = (NodeTest) sequenceTypeFromTree(tree.vennOperands[0], config).getPrimaryType();
                    NodeTest nt1 = (NodeTest) sequenceTypeFromTree(tree.vennOperands[1], config).getPrimaryType();
                    itemType = new CombinedNodeTest(nt0, tree.vennOperator, nt1);
                } else {
                    // Dangerous short-cut here - we know this will be a union of node kind tests
                    assert tree.vennOperator == Token.UNION;
                    UType u = UType.VOID;
                    for (int i=0; i<tree.vennOperands.length; i++) {
                        ItemType it = sequenceTypeFromTree(tree.vennOperands[i], config).getPrimaryType();
                        assert it instanceof NodeKindTest;
                        u = u.union(it.getUType());
                    }
                    itemType = new MultipleNodeKindTest(u);
                }
            } else {
                int kind = Type.NODE;
                if (principal.length() >= 2) {
                    switch (principal.substring(0, 2)) {
                        case "NT":
                            kind = Type.TEXT;
                            break;
                        case "NC":
                            kind = Type.COMMENT;
                            break;
                        case "NN":
                            kind = Type.NAMESPACE;
                            break;
                        case "NP":
                            kind = Type.PROCESSING_INSTRUCTION;
                            break;
                        case "ND":
                            kind = Type.DOCUMENT;
                            break;
                        case "NE":
                            kind = Type.ELEMENT;
                            break;
                        case "NA":
                            kind = Type.ATTRIBUTE;
                            break;

                    }
                }
                String name = tree.name;
                QNameTest partialNameTest = null;
                if (name != null && name.contains("*")) {
                    if (name.startsWith("*:")) {
                        partialNameTest = new LocalNameTest(config.getNamePool(), kind, name.substring(2));
                    } else if (name.endsWith("}*")) {
                        String uri = name.substring(2, name.length() - 2);
                        partialNameTest = new NamespaceTest(config.getNamePool(), kind, uri);
                    }
                }
                if (partialNameTest != null) {
                    itemType = (NodeTest)partialNameTest;
                } else {
                    StructuredQName qName = name == null ? null : StructuredQName.fromEQName(name);
                    switch (principal) {
                        case "N":
                            itemType = AnyNodeTest.getInstance();
                            break;
                        case "NT":
                            itemType = NodeKindTest.TEXT;
                            break;
                        case "NC":
                            itemType = NodeKindTest.COMMENT;
                            break;
                        case "NN":
                            if (name == null) {
                                itemType = NodeKindTest.NAMESPACE;
                            } else {
                                itemType = new NameTest(Type.NAMESPACE, "", qName.getLocalPart(), config.getNamePool());
                            }
                            break;
                        case "NP":
                            if (name == null) {
                                itemType = NodeKindTest.PROCESSING_INSTRUCTION;
                            } else {
                                itemType = new NameTest(Type.PROCESSING_INSTRUCTION, "", qName.getLocalPart(), config.getNamePool());
                            }
                            break;
                        case "ND":
                            AlphaCodeTree elementType = tree.elementType;
                            if (elementType == null) {
                                itemType = NodeKindTest.DOCUMENT;
                            } else {
                                ItemType e = sequenceTypeFromTree(elementType, config).getPrimaryType();
                                itemType = new DocumentNodeTest((NodeTest) e);
                            }
                            break;
                        case "NE":
                            if (qName == null) {
                                if (contentTest == null) {
                                    itemType = NodeKindTest.ELEMENT;
                                } else {
                                    itemType = contentTest;
                                }
                            } else {
                                itemType = new NameTest(Type.ELEMENT, qName.getURI(), qName.getLocalPart(), config.getNamePool());
                                if (contentTest != null) {
                                    itemType = new CombinedNodeTest((NodeTest) itemType, Token.INTERSECT, contentTest);
                                }
                            }
                            break;
                        case "NA":
                            if (qName == null) {
                                if (contentTest == null) {
                                    itemType = NodeKindTest.ATTRIBUTE;
                                } else {
                                    itemType = contentTest;
                                }
                            } else {
                                itemType = new NameTest(Type.ATTRIBUTE, qName.getURI(), qName.getLocalPart(), config.getNamePool());
                                if (contentTest != null) {
                                    itemType = new CombinedNodeTest((NodeTest) itemType, Token.INTERSECT, contentTest);
                                }
                            }
                            break;
                        case "NES": {
                            assert qName != null;
                            SchemaDeclaration decl = config.getElementDeclaration(qName);
                            if (decl != null) {
                                try {
                                    itemType = decl.makeSchemaNodeTest();
                                } catch (MissingComponentException e) {
                                    //
                                }
                            }
                            if (itemType == null) {
                                itemType = new NameTest(Type.ELEMENT, qName.getURI(), qName.getLocalPart(), config.getNamePool());
                            }
                            break;
                        }
                        case "NAS": {
                            assert qName != null;
                            SchemaDeclaration decl = config.getAttributeDeclaration(qName);
                            if (decl != null) {
                                try {
                                    itemType = decl.makeSchemaNodeTest();
                                } catch (MissingComponentException e) {
                                    //
                                }
                            }
                            if (itemType == null) {
                                itemType = new NameTest(Type.ATTRIBUTE, qName.getURI(), qName.getLocalPart(), config.getNamePool());
                            }
                            break;
                        }
                        default:
                            itemType = AnyNodeTest.getInstance();
                    }
                }
            }
        } else if (principal.startsWith("F")) {
            if (principal.equals("FA")) {
                AlphaCodeTree valueType = tree.valueType;
                if (valueType == null) {
                    itemType = ArrayItemType.ANY_ARRAY_TYPE;
                } else {
                    itemType = new ArrayItemType(sequenceTypeFromTree(valueType, config));
                }
            } else if (principal.equals("FM")) {
                if (tree.fieldNames == null) {
                    AlphaCodeTree keyType = tree.keyType;
                    AlphaCodeTree valueType = tree.valueType;
                    if (keyType != null && valueType != null) {
                        AtomicType a = (AtomicType) sequenceTypeFromTree(keyType, config).getPrimaryType();
                        SequenceType v = sequenceTypeFromTree(valueType, config);
                        itemType = new MapType(a, v);
                    } else {
                        itemType = MapType.ANY_MAP_TYPE;
                    }
                } else {
                    List<SequenceType> fieldTypes = new ArrayList<>(tree.argTypes.size());
                    for (AlphaCodeTree t : tree.argTypes) {
                        fieldTypes.add(sequenceTypeFromTree(t, config));
                    }
                    itemType = new TupleItemType(tree.fieldNames, fieldTypes, tree.extensibleTupleType);
                }
            } else {
                AlphaCodeTree returnType = tree.resultType;
                List<AlphaCodeTree> argTypes = tree.argTypes;
                if (argTypes == null) {
                    itemType = AnyFunctionType.getInstance();
                } else {
                    SequenceType r;
                    if (returnType == null) {
                        r = SequenceType.ANY_SEQUENCE;
                    } else {
                        r = sequenceTypeFromTree(returnType, config);
                    }
                    SequenceType[] a = new SequenceType[argTypes.size()];
                    for (int i = 0; i < a.length; i++) {
                        a[i] = sequenceTypeFromTree(argTypes.get(i), config);
                    }
                    itemType = new SpecificFunctionType(a, r);
                }
            }
        } else if (principal.startsWith("X")) {
            Class<?> theClass = Object.class;
            if (tree.name != null) {
                String className = StructuredQName.fromEQName(tree.name).getLocalPart();
                try {
                    theClass = Class.forName(className);
                } catch (ClassNotFoundException e) {
                    theClass = Object.class;
                }
            }
            itemType = new JavaExternalObjectType(config, theClass);
        }
        String indicator = tree.cardinality;
        int cardinality = Cardinality.fromOccurrenceIndicator(indicator);
        return SequenceType.makeSequenceType(itemType, cardinality);
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
        if (primary instanceof AtomicType && !((AtomicType)primary).isBuiltInType()) {
            result.name = ((AtomicType) primary).getEQName();
        } else if (primary instanceof UnionType) {
            StructuredQName name = ((UnionType) primary).getTypeName();
            if (name.getURI().equals(NamespaceConstant.SCHEMA)) {
                // built-in union types xs:numeric, xs:error
                result.name = "~" + name.getLocalPart();
            } else if (name.getURI().equals(NamespaceConstant.ANONYMOUS)) {
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

        } else if (primary instanceof NameTest) {
            StructuredQName name = ((NameTest) primary).getMatchingNodeName();
            result.name = name.getEQName();
        } else if (primary instanceof SchemaNodeTest) {
            StructuredQName name = ((SchemaNodeTest) primary).getNodeName();
            result.name = name.getEQName();
        } else if (primary instanceof LocalNameTest) {
            result.name = "*:" + ((LocalNameTest) primary).getLocalName();
        } else if (primary instanceof NamespaceTest) {
            result.name = "Q{" + ((NamespaceTest) primary).getNamespaceURI() + "}*";
        } else if (primary instanceof CombinedNodeTest) {
            final CombinedNodeTest combi = (CombinedNodeTest) primary;
            String c = combi.getContentTypeForAlphaCode();
            if (c != null) {
                result.content = c;
                result.name = combi.getMatchingNodeName().getEQName();
                result.nillable = combi.isNillable();
            } else {
                result.vennOperator = combi.getOperator();
                result.vennOperands = new AlphaCodeTree[2];
                result.vennOperands[0] = makeTree(combi.getOperand(0));
                result.vennOperands[1] = makeTree(combi.getOperand(1));
            }
        } else if (primary instanceof MultipleNodeKindTest) {
            result.vennOperator = Token.UNION;
            Set<PrimitiveUType> types = primary.getUType().decompose();
            result.vennOperands = new AlphaCodeTree[types.size()];
            int i=0;
            for (PrimitiveUType type : types) {
                 result.vennOperands[i++] = makeTree(type.toItemType());
            }
        } else if (primary instanceof ContentTypeTest) {
            result.content = ((ContentTypeTest) primary).getContentType().getEQName();
        } else if (primary instanceof DocumentNodeTest) {
            ItemType content = ((DocumentNodeTest) primary).getElementTest();
            result.elementType = makeTree(content);
        } else if (primary instanceof FunctionItemType) {
            if (primary instanceof ArrayItemType) {
                SequenceType memberType = ((ArrayItemType) primary).getMemberType();
                if (memberType != SequenceType.ANY_SEQUENCE) {
                    result.valueType = makeTree(memberType);
                }
            } else if (primary instanceof TupleItemType) {
                result.extensibleTupleType = ((TupleItemType)primary).isExtensible();
                result.fieldNames = new ArrayList<>();
                result.argTypes = new ArrayList<>();
                for (String s : ((TupleItemType) primary).getFieldNames()) {
                    result.fieldNames.add(s);
                    result.argTypes.add(makeTree(((TupleItemType) primary).getFieldType(s)));
                }
            } else if (primary instanceof MapType) {
                AtomicType keyType = ((MapType) primary).getKeyType();
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
                    for (SequenceType at : argTypes) {
                        argMaps.add(makeTree(at));
                    }
                    result.argTypes = argMaps;
                }
            }
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
        if (tree.name != null) {
            sb.append(" n").append(abbreviateEQName(tree.name));
        }
        if (tree.content != null) {
            sb.append(" c").append(abbreviateEQName(tree.content));
            if (tree.nillable) {
                sb.append("?");
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
                    tree.vennOperator == Token.INTERSECT ? "i"
                            : tree.vennOperator == Token.UNION ? "u"
                            : "d";
            sb.append(" ")
                    .append(operator)
                    .append("[");
            for (int i=0; i<tree.vennOperands.length; i++) {
                if (i != 0) {
                    sb.append(",");
                }
                alphaCodeFromTree(tree.vennOperands[i], false, sb);
            }
            sb.append("]");
        }
        if (tree.fieldNames != null) {
            sb.append(tree.extensibleTupleType ? " F[" : " f[");
            boolean first = true;
            for (String s : tree.fieldNames) {
                if (!first) {
                    sb.append(",");
                } else {
                    first = false;
                }
                sb.append(s.replace("\\", "\\\\").replace(",", "\\,").replace("]", "\\]"));
            }
            sb.append("]");
        }
    }

    public static String fromItemType(ItemType type) {
        AlphaCodeTree tree = makeTree(type);
        StringBuilder sb = new StringBuilder();
        alphaCodeFromTree(tree, false, sb);
        return sb.toString().trim();
    }

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

    public static String fromLexicalSequenceType(XPathContext context, String input) throws XPathException {
        XPathParser parser = context.getConfiguration().newExpressionParser("XP", false, 31);
        IndependentContext env = new IndependentContext(context.getConfiguration());
        env.declareNamespace("xs", NamespaceConstant.SCHEMA);
        env.declareNamespace("fn", NamespaceConstant.FN);
        SequenceType st = parser.parseSequenceType(input, env);
        return fromSequenceType(st);
    }
}

