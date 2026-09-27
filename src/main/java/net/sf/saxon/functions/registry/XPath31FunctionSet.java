////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.registry;

import net.sf.saxon.functions.*;
import net.sf.saxon.functions.hof.LoadXqueryModule;
import net.sf.saxon.functions.hof.RandomNumberGenerator;
import net.sf.saxon.functions.hof.Sort;
import net.sf.saxon.ma.json.JsonDoc;
import net.sf.saxon.ma.json.JsonToXMLFn;
import net.sf.saxon.ma.json.ParseJsonFn;
import net.sf.saxon.ma.json.XMLToJsonFn;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.pattern.PortableNamedXNodeType;
import net.sf.saxon.type.*;
import net.sf.saxon.type.gnode.AnyXNodeType;
import net.sf.saxon.type.gnode.DocumentNodeType;
import net.sf.saxon.type.gnode.NodeKindType;
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

        register("copy-of", 0, e -> {
            return e.populate(CopyOfFn::new, AnyItemType.getInstance(),
                                  STAR, NEW);
        });

        register("copy-of", 1, e -> {
            return e.populate(CopyOfFn::new, AnyItemType.getInstance(),
                                  STAR, NEW)
                    .arg(0, AnyItemType.getInstance(), STAR | ABS, EMPTY);
        });

        register("default-language", 0, e -> e.populate(DynamicContextAccessor.DefaultLanguage::new, BuiltInAtomicType.LANGUAGE, ONE, DLANG));

        register("generate-id", 0, e -> e.populate( ContextItemAccessorFunction::new, BuiltInAtomicType.STRING, ONE, CITEM | LATE));

        register("generate-id", 1, e -> e.populate( GenerateId_1::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, AnyXNodeType.getInstance(), OPT | INS, StringValue.EMPTY_STRING));

        register("has-children", 0, e -> e.populate( ContextItemAccessorFunction::new, BuiltInAtomicType.BOOLEAN,
                 ONE, CITEM | LATE));

        register("has-children", 1, e -> e.populate( HasChildren_1::new, BuiltInAtomicType.BOOLEAN,
                 ONE, 0)
                .arg(0, AnyXNodeType.getInstance(), OPT | INS, null));

        register("head", 1, e -> {
            return e.populate(HeadFn::new, AnyItemType.getInstance(),
                                  OPT, FILTER)
                    .arg(0, AnyItemType.getInstance(), STAR | TRA, null);
        });

        register("innermost", 1, e -> e.populate(Innermost::new, AnyXNodeType.getInstance(),
                        STAR, 0)
                .arg(0, AnyXNodeType.getInstance(), STAR | NAV, null));

        register("json-doc", 1, e -> {
            return e.populate(JsonDoc::new, AnyItemType.getInstance(),
                                  OPT, LATE)
                    .arg(0, BuiltInAtomicType.STRING, OPT, null);
        });

        register("json-doc", 2, e -> {
            return e.populate(JsonDoc::new, AnyItemType.getInstance(),
                                  OPT, LATE)
                    .arg(0, BuiltInAtomicType.STRING, OPT, null)
                    .arg(1, MapType.ANY_MAP_TYPE, ONE, null)
                    .setOptionDetails(ParseJsonFn.makeOptionsParameter(31));
        });

        register("json-to-xml", 1, e -> {
            return e.populate(JsonToXMLFn::new, AnyItemType.getInstance(),
                                  OPT, LATE | NEW)
                    .arg(0, BuiltInAtomicType.STRING, OPT, null);
        });

        register("json-to-xml", 2, e -> {
            return e.populate(JsonToXMLFn::new, AnyItemType.INSTANCE,
                              OPT, LATE | NEW)
                    .arg(0, BuiltInAtomicType.STRING, OPT, null)
                    .arg(1, MapType.ANY_MAP_TYPE, ONE, null)
                    .setOptionDetails(JsonToXMLFn.makeOptionsParameter(31));
        });

        register("load-xquery-module", 1, e -> e.populate( LoadXqueryModule::new, MapType.ANY_MAP_TYPE, ONE, LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null));

        register("load-xquery-module", 2, e -> e.populate( LoadXqueryModule::new, MapType.ANY_MAP_TYPE, ONE, LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null) // null or EMPTY?
                .arg(1, MapType.ANY_MAP_TYPE, ONE, EMPTY)
                .setOptionDetails(LoadXqueryModule.makeOptionsParameter(31)));


        register("parse-ietf-date", 1, e -> e.populate( ParseIetfDate::new, BuiltInAtomicType.DATE_TIME, OPT, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY));

        register("parse-json", 1, e -> {
            return e.populate(ParseJsonFn::new, AnyItemType.INSTANCE, OPT, 0)
                    .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY);
        });

        register("parse-json", 2, e -> {
            return e.populate(ParseJsonFn::new, AnyItemType.INSTANCE, OPT, 0)
                    .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY)
                    .arg(1, MapType.ANY_MAP_TYPE, ONE, null)
                    .setOptionDetails(ParseJsonFn.makeOptionsParameter(31));
        });

        register("parse-xml", 1, e -> e.populate(ParseXml::new, new DocumentNodeType(NodeKindType.ELEMENT), OPT, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY));

        register("random-number-generator", 0, e -> e.populate( RandomNumberGenerator::new, RandomNumberGenerator.RETURN_TYPE, ONE, LATE));

        register("random-number-generator", 1, e -> e.populate( RandomNumberGenerator::new, RandomNumberGenerator.RETURN_TYPE, ONE, LATE)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, OPT, null));

        register("parse-xml-fragment", 1, e -> e.populate(ParseXmlFragment::new, NodeKindType.DOCUMENT, OPT, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY));

        register("serialize", 2, e -> {
            return e.populate( Serialize::new, BuiltInAtomicType.STRING, ONE, 0)
                    .arg(0, AnyItemType.INSTANCE, STAR, null)
                    .arg(1, ChoiceItemType.of(
                            new PortableNamedXNodeType(Type.ELEMENT, NamespaceUri.OUTPUT.qName("serialization-parameters")),
                            MapType.ANY_MAP_TYPE), OPT, null)
                    // second argument declared as (element(output:serialization-parameters) | map(*))?
                    .setOptionDetails(Serialize.makeOptionsParameter(31));
        });

        // The snapshot function is defined in XSLT 3.0, but we choose to make it available also in XPath/XQuery

        register("snapshot", 0, e -> {
            return e.populate(ContextItemAccessorFunction::new, AnyItemType.INSTANCE, STAR, CITEM | LATE | NEW);
        });

        register("snapshot", 1, e -> {
            return e.populate(SnapshotFn::new, AnyXNodeType.getInstance(),
                                                    STAR, NEW)
                    .arg(0, AnyItemType.INSTANCE, STAR | ABS, EMPTY);
        });

        register("sort", 1, e -> {
            return e.populate(Sort::new, AnyItemType.INSTANCE, STAR, 0)
                    .arg(0, AnyItemType.INSTANCE, STAR, null);
        });

        register("sort", 2, e -> {
            return e.populate(Sort::new, AnyItemType.INSTANCE,
                              STAR, 0)
                    .arg(0, AnyItemType.INSTANCE, STAR, null)
                    .arg(1, BuiltInAtomicType.STRING, OPT, null);
        });

        ft = new SpecificFunctionType(
                SequenceType.SINGLE_ITEM,
                SequenceType.ATOMIC_SEQUENCE);

        register("sort", 3, e -> {
            return e.populate(Sort::new, AnyItemType.INSTANCE,
                              STAR, 0)
                    .arg(0, AnyItemType.INSTANCE, STAR, null)
                    .arg(1, BuiltInAtomicType.STRING, OPT, null)
                    .arg(2, ft, ONE, null);
        });

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
                .setOptionDetails(TransformFn.makeOptionsParameter(31)));

        register("xml-to-json", 1, e -> e.populate( XMLToJsonFn::new, BuiltInAtomicType.STRING,
                 OPT, LATE)
                .arg(0, AnyXNodeType.getInstance(), OPT | ABS, EMPTY));

        register("xml-to-json", 2, e -> e.populate( XMLToJsonFn::new, BuiltInAtomicType.STRING,
                 OPT, LATE)
                .arg(0, AnyXNodeType.getInstance(), OPT | ABS, EMPTY)
                .arg(1, MapType.ANY_MAP_TYPE, ONE | ABS, null)
                .setOptionDetails(XMLToJsonFn.makeOptionsParameter(31)));

    }

}
