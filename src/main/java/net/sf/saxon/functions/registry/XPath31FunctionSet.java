////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
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
import net.sf.saxon.pattern.DocumentNodeTest;
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

    private static final XPath31FunctionSet THE_INSTANCE = new XPath31FunctionSet();

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

        register("collation-key", 1, e -> e.populate( CollationKeyFn::new, BuiltInAtomicType.BASE64_BINARY,
                 ONE, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, ONE, null));

        register("collation-key", 2, e -> e.populate( CollatingFunctionFree::new, BuiltInAtomicType.BASE64_BINARY,
                 ONE, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, ONE, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("contains-token", 2, e -> e.populate( ContainsToken::new, BuiltInAtomicType.BOOLEAN, ONE, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, STAR, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("contains-token", 3, e -> e.populate( CollatingFunctionFree::new, BuiltInAtomicType.BOOLEAN, ONE, BASE)
                .arg(0, BuiltInAtomicType.STRING, STAR, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null));

        // The copy-of function is defined in XSLT 3.0, but we choose to make it available also in XPath/XQuery

        register("copy-of", 0, e -> e.populate( CopyOfFn::new, AnyItemType.getInstance(),
                 STAR, NEW));

        register("copy-of", 1, e -> e.populate( CopyOfFn::new, AnyItemType.getInstance(),
                 STAR, NEW)
                .arg(0, AnyItemType.getInstance(), STAR | ABS, EMPTY));

        register("default-language", 0, e -> e.populate(DynamicContextAccessor.DefaultLanguage::new, BuiltInAtomicType.LANGUAGE, ONE, DLANG));

        register("generate-id", 0, e -> e.populate( ContextItemAccessorFunction::new, BuiltInAtomicType.STRING, ONE, CITEM | LATE));

        register("generate-id", 1, e -> e.populate( GenerateId_1::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, Type.NODE_TYPE, OPT | INS, StringValue.EMPTY_STRING));

        register("has-children", 0, e -> e.populate( ContextItemAccessorFunction::new, BuiltInAtomicType.BOOLEAN,
                 ONE, CITEM | LATE));

        register("has-children", 1, e -> e.populate( HasChildren_1::new, BuiltInAtomicType.BOOLEAN,
                 ONE, 0)
                .arg(0, AnyNodeTest.getInstance(), OPT | INS, null));

        register("head", 1, e -> e.populate( HeadFn::new, AnyItemType.getInstance(),
                 OPT, FILTER)
                .arg(0, AnyItemType.getInstance(), STAR | TRA, null));

        register("innermost", 1, e -> e.populate( Innermost::new, AnyNodeTest.getInstance(),
                 STAR, 0)
                .arg(0, AnyNodeTest.getInstance(), STAR | NAV, null));

        register("json-doc", 1, e -> e.populate( JsonDoc::new, AnyItemType.getInstance(),
                 OPT, LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null));

        register("json-doc", 2, e -> e.populate( JsonDoc::new, AnyItemType.getInstance(),
                 OPT, LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, MapType.ANY_MAP_TYPE, ONE, null)
                .setOptionDetails(ParseJsonFn.OPTION_DETAILS));

        register("json-to-xml", 1, e -> e.populate( JsonToXMLFn::new, AnyItemType.getInstance(),
                 OPT, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, OPT, null));

        register("json-to-xml", 2, e -> e.populate( JsonToXMLFn::new, AnyItemType.getInstance(),
                 OPT, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, MapType.ANY_MAP_TYPE, ONE, null)
                .setOptionDetails(JsonToXMLFn.OPTION_DETAILS));

        register("load-xquery-module", 1, e -> e.populate( LoadXqueryModule::new, MapType.ANY_MAP_TYPE, ONE, LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null));

        register("load-xquery-module", 2, e -> e.populate( LoadXqueryModule::new, MapType.ANY_MAP_TYPE, ONE, LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null) // null or EMPTY?
                .arg(1, MapType.ANY_MAP_TYPE, ONE, EMPTY)
                .setOptionDetails(LoadXqueryModule.makeOptionsParameter()));


        register("parse-ietf-date", 1, e -> e.populate( ParseIetfDate::new, BuiltInAtomicType.DATE_TIME, OPT, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY));

        register("parse-json", 1, e -> e.populate( ParseJsonFn::new, AnyItemType.getInstance(), OPT, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY));

        register("parse-json", 2, e -> e.populate( ParseJsonFn::new, AnyItemType.getInstance(), OPT, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY)
                .arg(1, MapType.ANY_MAP_TYPE, ONE, null)
                .setOptionDetails(ParseJsonFn.OPTION_DETAILS));

        register("parse-xml", 1, e -> e.populate(ParseXml::new, new DocumentNodeTest(NodeKindTest.ELEMENT), OPT, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY));

        register("random-number-generator", 0, e -> e.populate( RandomNumberGenerator::new, RandomNumberGenerator.RETURN_TYPE, ONE, LATE));

        register("random-number-generator", 1, e -> e.populate( RandomNumberGenerator::new, RandomNumberGenerator.RETURN_TYPE, ONE, LATE)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, OPT, null));

        register("parse-xml-fragment", 1, e -> e.populate( ParseXmlFragment::new, NodeKindTest.DOCUMENT, OPT, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY));

        register("serialize", 2, e -> e.populate( Serialize::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, AnyItemType.getInstance(), STAR, null)
                .arg(1, Type.ITEM_TYPE, OPT, null)
                .setOptionDetails(Serialize.makeOptionsParameter()));

        // The snapshot function is defined in XSLT 3.0, but we choose to make it available also in XPath/XQuery

        register("snapshot", 0, e -> e.populate( ContextItemAccessorFunction::new, AnyItemType.getInstance(), STAR, CITEM | LATE | NEW));

        register("snapshot", 1, e -> e.populate( SnapshotFn::new, AnyNodeTest.getInstance(),
                 STAR, NEW)
                .arg(0, AnyItemType.getInstance(), STAR | ABS, EMPTY));

        register("sort", 1, e -> e.populate( Sort_1::new, AnyItemType.getInstance(), STAR, 0)
                .arg(0, AnyItemType.getInstance(), STAR, null));

        register("sort", 2, e -> e.populate( Sort_2::new, AnyItemType.getInstance(),
                 STAR, 0)
                .arg(0, AnyItemType.getInstance(), STAR, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, null));

        ft = new SpecificFunctionType(
                new SequenceType[]{SequenceType.SINGLE_ITEM},
                SequenceType.ATOMIC_SEQUENCE);

        register("sort", 3, e -> e.populate( Sort_3::new, AnyItemType.getInstance(),
                 STAR, 0)
                .arg(0, AnyItemType.getInstance(), STAR, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, null)
                .arg(2, ft, ONE, null));

        register("string-join", 1, e -> e.populate( StringJoin::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, StringValue.EMPTY_STRING));

        register("string-join", 2, e -> e.populate( StringJoin::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, StringValue.EMPTY_STRING)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("tokenize", 1, e -> e.populate( Tokenize_1::new, BuiltInAtomicType.STRING, STAR, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY));

        register("trace", 1, e -> e.populate( Trace::new, Type.ITEM_TYPE, STAR, AS_ARG0 | LATE)
                .arg(0, Type.ITEM_TYPE, STAR | TRA, null));

        register("transform", 1, e -> e.populate( TransformFn::new, MapType.ANY_MAP_TYPE, ONE, LATE)
                .arg(0, MapType.ANY_MAP_TYPE, ONE, EMPTY)
                .setOptionDetails(TransformFn.makeOptionsParameter()));

        register("xml-to-json", 1, e -> e.populate( XMLToJsonFn::new, BuiltInAtomicType.STRING,
                 OPT, LATE)
                .arg(0, AnyNodeTest.getInstance(), OPT | ABS, EMPTY));

        register("xml-to-json", 2, e -> e.populate( XMLToJsonFn::new, BuiltInAtomicType.STRING,
                 OPT, LATE)
                .arg(0, AnyNodeTest.getInstance(), OPT | ABS, EMPTY)
                .arg(1, MapType.ANY_MAP_TYPE, ONE | ABS, null)
                .setOptionDetails(XMLToJsonFn.makeOptionsParameter()));

    }

}
