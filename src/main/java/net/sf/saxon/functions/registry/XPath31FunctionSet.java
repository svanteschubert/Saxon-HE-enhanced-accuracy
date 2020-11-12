////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.registry;

import net.sf.saxon.functions.*;
import net.sf.saxon.functions.hof.LoadXqueryModule;
import net.sf.saxon.functions.hof.RandomNumberGenerator;
import net.sf.saxon.functions.hof.Sort_3;
import net.sf.saxon.ma.json.JsonDoc;
import net.sf.saxon.ma.json.JsonToXMLFn;
import net.sf.saxon.ma.json.ParseJsonFn;
import net.sf.saxon.ma.json.XMLToJsonFn;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

/**
 * Function signatures (and pointers to implementations) of the functions defined in XPath 3.1 without the
 * Higher-Order-Functions feature
 */

public class XPath31FunctionSet extends BuiltInFunctionSet {

    private static XPath31FunctionSet THE_INSTANCE = new XPath31FunctionSet();

    public static XPath31FunctionSet getInstance() {
        return THE_INSTANCE;
    }

    private XPath31FunctionSet() {
        init();
    }

    private void init() {

        importFunctionSet(XPath20FunctionSet.getInstance());
        importFunctionSet(XPath30FunctionSet.getInstance());

        SpecificFunctionType ft;


        register("collation-key", 1, CollationKeyFn.class, BuiltInAtomicType.BASE64_BINARY,
                 OPT, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("collation-key", 2, CollatingFunctionFree.class, BuiltInAtomicType.BASE64_BINARY,
                 OPT, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("contains-token", 2, ContainsToken.class, BuiltInAtomicType.BOOLEAN, ONE, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, STAR, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("contains-token", 3, CollatingFunctionFree.class, BuiltInAtomicType.BOOLEAN, ONE, BASE)
                .arg(0, BuiltInAtomicType.STRING, STAR, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        // The copy-of function is defined in XSLT 3.0, but we choose to make it available also in XPath/XQuery

        register("copy-of", 0, CopyOfFn.class, AnyItemType.getInstance(),
                 STAR, NEW);

        register("copy-of", 1, CopyOfFn.class, AnyItemType.getInstance(),
                 STAR, NEW)
                .arg(0, AnyItemType.getInstance(), STAR | ABS, EMPTY);

        register("default-language", 0, DynamicContextAccessor.DefaultLanguage.class, BuiltInAtomicType.LANGUAGE, ONE, DLANG);

        register("generate-id", 0, ContextItemAccessorFunction.class, BuiltInAtomicType.STRING, ONE, CITEM | LATE);

        register("generate-id", 1, GenerateId_1.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, Type.NODE_TYPE, OPT | INS, StringValue.EMPTY_STRING);

        register("has-children", 0, ContextItemAccessorFunction.class, BuiltInAtomicType.BOOLEAN,
                 ONE, CITEM | LATE);

        register("has-children", 1, HasChildren_1.class, BuiltInAtomicType.BOOLEAN,
                 OPT, 0)
                .arg(0, AnyNodeTest.getInstance(), OPT | INS, null);

        register("head", 1, HeadFn.class, AnyItemType.getInstance(),
                 OPT, FILTER)
                .arg(0, AnyItemType.getInstance(), STAR | TRA, null);

        register("innermost", 1, Innermost.class, AnyNodeTest.getInstance(),
                 STAR, 0)
                .arg(0, AnyNodeTest.getInstance(), STAR | NAV, null);

        register("json-doc", 1, JsonDoc.class, AnyItemType.getInstance(),
                 OPT, LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null);

        register("json-doc", 2, JsonDoc.class, AnyItemType.getInstance(),
                 OPT, LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, MapType.ANY_MAP_TYPE, ONE, null)
                .optionDetails(ParseJsonFn.OPTION_DETAILS);

        register("json-to-xml", 1, JsonToXMLFn.class, AnyItemType.getInstance(),
                 OPT, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, OPT, null);

        register("json-to-xml", 2, JsonToXMLFn.class, AnyItemType.getInstance(),
                 OPT, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, MapType.ANY_MAP_TYPE, ONE, null)
                .optionDetails(JsonToXMLFn.OPTION_DETAILS);

        register("load-xquery-module", 1, LoadXqueryModule.class, MapType.ANY_MAP_TYPE, ONE, LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        register("load-xquery-module", 2, LoadXqueryModule.class, MapType.ANY_MAP_TYPE, ONE, LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null) // null or EMPTY?
                .arg(1, MapType.ANY_MAP_TYPE, ONE, EMPTY)
                .optionDetails(LoadXqueryModule.makeOptionsParameter());


        register("parse-ietf-date", 1, ParseIetfDate.class, BuiltInAtomicType.DATE_TIME, OPT, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY);

        register("parse-json", 1, ParseJsonFn.class, AnyItemType.getInstance(), OPT, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY);

        register("parse-json", 2, ParseJsonFn.class, AnyItemType.getInstance(), OPT, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY)
                .arg(1, MapType.ANY_MAP_TYPE, ONE, null)
                .optionDetails(ParseJsonFn.OPTION_DETAILS);

        register("parse-xml", 1, ParseXml.class, NodeKindTest.DOCUMENT, OPT, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY);

        register("random-number-generator", 0, RandomNumberGenerator.class, RandomNumberGenerator.RETURN_TYPE, ONE, LATE);

        register("random-number-generator", 1, RandomNumberGenerator.class, RandomNumberGenerator.RETURN_TYPE, ONE, LATE)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, OPT, null);

        register("parse-xml-fragment", 1, ParseXmlFragment.class, NodeKindTest.DOCUMENT, OPT, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY);

        register("serialize", 2, Serialize.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, AnyItemType.getInstance(), STAR, null)
                .arg(1, Type.ITEM_TYPE, OPT, null)
                .optionDetails(Serialize.makeOptionsParameter());

        // The snapshot function is defined in XSLT 3.0, but we choose to make it available also in XPath/XQuery

        register("snapshot", 0, ContextItemAccessorFunction.class, AnyItemType.getInstance(), STAR, CITEM | LATE | NEW);

        register("snapshot", 1, SnapshotFn.class, AnyNodeTest.getInstance(),
                 STAR, NEW)
                .arg(0, AnyItemType.getInstance(), STAR | ABS, EMPTY);

        register("sort", 1, Sort_1.class, AnyItemType.getInstance(), STAR, 0)
                .arg(0, AnyItemType.getInstance(), STAR, null);

        register("sort", 2, Sort_2.class, AnyItemType.getInstance(),
                 STAR, 0)
                .arg(0, AnyItemType.getInstance(), STAR, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, null);

        ft = new SpecificFunctionType(
                new SequenceType[]{SequenceType.SINGLE_ITEM},
                SequenceType.ATOMIC_SEQUENCE);

        register("sort", 3, Sort_3.class, AnyItemType.getInstance(),
                 STAR, 0)
                .arg(0, AnyItemType.getInstance(), STAR, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, null)
                .arg(2, ft, ONE, null);

        register("string-join", 1, StringJoin.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, StringValue.EMPTY_STRING);

        register("string-join", 2, StringJoin.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, StringValue.EMPTY_STRING)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("tokenize", 1, Tokenize_1.class, BuiltInAtomicType.STRING, STAR, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY);

        register("trace", 1, Trace.class, Type.ITEM_TYPE, STAR, AS_ARG0 | LATE)
                .arg(0, Type.ITEM_TYPE, STAR | TRA, null);

        register("transform", 1, TransformFn.class, MapType.ANY_MAP_TYPE, ONE, LATE)
                .arg(0, MapType.ANY_MAP_TYPE, ONE, EMPTY)
                .optionDetails(TransformFn.makeOptionsParameter());

        register("xml-to-json", 1, XMLToJsonFn.class, BuiltInAtomicType.STRING,
                 OPT, LATE)
                .arg(0, AnyNodeTest.getInstance(), OPT | ABS, EMPTY);

        register("xml-to-json", 2, XMLToJsonFn.class, BuiltInAtomicType.STRING,
                 OPT, LATE)
                .arg(0, AnyNodeTest.getInstance(), OPT | ABS, EMPTY)
                .arg(1, MapType.ANY_MAP_TYPE, ONE | ABS, null)
                .optionDetails(XMLToJsonFn.makeOptionsParameter());

    }

}
