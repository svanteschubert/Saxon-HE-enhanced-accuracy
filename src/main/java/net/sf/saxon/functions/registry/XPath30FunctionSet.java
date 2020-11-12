////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.registry;

import net.sf.saxon.functions.*;
import net.sf.saxon.functions.hof.*;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.type.*;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

/**
 * Function signatures (and pointers to implementations) of the functions defined in XPath 3.0 without the
 * Higher-Order-Functions feature
 */

public class XPath30FunctionSet extends BuiltInFunctionSet {

    private static XPath30FunctionSet THE_INSTANCE = new XPath30FunctionSet();

    public static XPath30FunctionSet getInstance() {
        return THE_INSTANCE;
    }

    private XPath30FunctionSet() {
        init();
    }

    private void init() {

        importFunctionSet(XPath20FunctionSet.getInstance());

        register("analyze-string", 2, RegexFunctionSansFlags.class, NodeKindTest.ELEMENT,
                 ONE, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("analyze-string", 3, AnalyzeStringFn.class, NodeKindTest.ELEMENT,
                 ONE, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("apply", 2, ApplyFn.class, AnyItemType.getInstance(),
                 STAR, LATE)
                .arg(0, AnyFunctionType.getInstance(), ONE, null)
                .arg(1, ArrayItemType.ANY_ARRAY_TYPE, ONE, null);

        register("available-environment-variables", 0, AvailableEnvironmentVariables.class, BuiltInAtomicType.STRING,
                 STAR, LATE);

        register("data", 0, ContextItemAccessorFunction.class, BuiltInAtomicType.ANY_ATOMIC, STAR, CITEM | LATE);

        register("document-uri", 0, ContextItemAccessorFunction.class,
                 BuiltInAtomicType.ANY_URI, OPT, CITEM | LATE);

        register("element-with-id", 1, SuperId.ElementWithId.class, NodeKindTest.ELEMENT, STAR, CITEM | LATE | UO)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY);

        register("element-with-id", 2, SuperId.ElementWithId.class, NodeKindTest.ELEMENT, STAR, UO)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY)
                .arg(1, Type.NODE_TYPE, ONE, null);

        register("environment-variable", 1, EnvironmentVariable.class, BuiltInAtomicType.STRING,
                 OPT, LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null);

        SpecificFunctionType predicate = new SpecificFunctionType(
                new SequenceType[]{SequenceType.SINGLE_ITEM},
                SequenceType.SINGLE_BOOLEAN);

        register("filter", 2, FilterFn.class, AnyItemType.getInstance(),
                 STAR, AS_ARG0 | LATE)
                .arg(0, AnyItemType.getInstance(), STAR | TRA, EMPTY)
                .arg(1, predicate, ONE, null);

        SpecificFunctionType foldLeftArg = new SpecificFunctionType(
                new SequenceType[]{SequenceType.ANY_SEQUENCE, SequenceType.SINGLE_ITEM},
                SequenceType.ANY_SEQUENCE);
        register("fold-left", 3, FoldLeftFn.class, AnyItemType.getInstance(),
                 STAR, LATE)
                .arg(0, AnyItemType.getInstance(), STAR, null)
                .arg(1, AnyItemType.getInstance(), STAR, null)
                .arg(2, foldLeftArg, ONE, null);

        SpecificFunctionType foldRightArg = new SpecificFunctionType(
                new SequenceType[]{SequenceType.SINGLE_ITEM, SequenceType.ANY_SEQUENCE},
                SequenceType.ANY_SEQUENCE);

        register("fold-right", 3, FoldRightFn.class, AnyItemType.getInstance(),
                 STAR, LATE)
                .arg(0, AnyItemType.getInstance(), STAR, null)
                .arg(1, AnyItemType.getInstance(), STAR, null)
                .arg(2, foldRightArg, ONE, null);

        SpecificFunctionType forEachArg = new SpecificFunctionType(
                new SequenceType[]{SequenceType.SINGLE_ITEM},
                SequenceType.ANY_SEQUENCE);
        register("for-each", 2, ForEachFn.class, AnyItemType.getInstance(),
                 STAR, LATE)
                .arg(0, AnyItemType.getInstance(), STAR, EMPTY)
                .arg(1, forEachArg, ONE, null);

        SpecificFunctionType forEachPairArg = new SpecificFunctionType(
                new SequenceType[]{SequenceType.SINGLE_ITEM, SequenceType.SINGLE_ITEM},
                SequenceType.ANY_SEQUENCE);
        register("for-each-pair", 3, ForEachPairFn.class, AnyItemType.getInstance(),
                 STAR, LATE)
                .arg(0, AnyItemType.getInstance(), STAR, EMPTY)
                .arg(1, AnyItemType.getInstance(), STAR, EMPTY)
                .arg(2, forEachPairArg, ONE, null);


        register("format-date", 2, FormatDate.class, BuiltInAtomicType.STRING,
                 OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("format-date", 5, FormatDate.class, BuiltInAtomicType.STRING,
                 OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, OPT, null)
                .arg(3, BuiltInAtomicType.STRING, OPT, null)
                .arg(4, BuiltInAtomicType.STRING, OPT, null);

        register("format-dateTime", 2, FormatDate.class, BuiltInAtomicType.STRING,
                 OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("format-dateTime", 5, FormatDate.class, BuiltInAtomicType.STRING,
                 OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, OPT, null)
                .arg(3, BuiltInAtomicType.STRING, OPT, null)
                .arg(4, BuiltInAtomicType.STRING, OPT, null);

        register("format-integer", 2, FormatInteger.class, AnyItemType.getInstance(), ONE, 0)
                .arg(0, BuiltInAtomicType.INTEGER, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("format-integer", 3, FormatInteger.class, AnyItemType.getInstance(), ONE, 0)
                .arg(0, BuiltInAtomicType.INTEGER, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, OPT, null);

        register("format-number", 2, FormatNumber.class, BuiltInAtomicType.STRING, ONE, LATE)
                .arg(0, NumericType.getInstance(), OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("format-number", 3, FormatNumber.class, BuiltInAtomicType.STRING, ONE, NS | LATE)
                .arg(0, NumericType.getInstance(), OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, OPT, null);

        register("format-time", 2, FormatDate.class, BuiltInAtomicType.STRING,
                 OPT, CARD0)
                .arg(0, BuiltInAtomicType.TIME, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("format-time", 5, FormatDate.class, BuiltInAtomicType.STRING,
                 OPT, CARD0)
                .arg(0, BuiltInAtomicType.TIME, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, OPT, null)
                .arg(3, BuiltInAtomicType.STRING, OPT, null)
                .arg(4, BuiltInAtomicType.STRING, OPT, null);

        register("function-arity", 1, FunctionArity.class, BuiltInAtomicType.INTEGER,
                 ONE, 0)
                .arg(0, AnyFunctionType.getInstance(), ONE, null);

        register("function-lookup", 2, FunctionLookup.class, AnyFunctionType.getInstance(),
                 OPT,
                 FOCUS | DEPENDS_ON_STATIC_CONTEXT | LATE)
                .arg(0, BuiltInAtomicType.QNAME, ONE, null)
                .arg(1, BuiltInAtomicType.INTEGER, ONE, null);

        register("function-name", 1, FunctionName.class, BuiltInAtomicType.QNAME,
                 OPT, 0)
                .arg(0, AnyFunctionType.getInstance(), ONE, null);

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

        register("nilled", 0, ContextItemAccessorFunction.class, BuiltInAtomicType.BOOLEAN, OPT, CITEM | LATE);

        register("node-name", 0, ContextItemAccessorFunction.class, BuiltInAtomicType.QNAME, OPT, CITEM | LATE);

        register("outermost", 1, Outermost.class, AnyNodeTest.getInstance(), STAR, AS_ARG0 | FILTER)
                .arg(0, AnyNodeTest.getInstance(), STAR | TRA, null);

        register("parse-xml", 1, ParseXml.class, NodeKindTest.DOCUMENT, OPT, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY);

        register("parse-xml-fragment", 1, ParseXmlFragment.class, NodeKindTest.DOCUMENT, OPT, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY);

        register("path", 0, ContextItemAccessorFunction.class, BuiltInAtomicType.STRING, OPT, CITEM | LATE);

        register("path", 1, Path_1.class, BuiltInAtomicType.STRING, OPT, 0)
                .arg(0, AnyNodeTest.getInstance(), OPT | NAV, null);

        register("round", 2, Round.class, NumericType.getInstance(), OPT, AS_PRIM_ARG0)
                .arg(0, NumericType.getInstance(), OPT, EMPTY)
                .arg(1, BuiltInAtomicType.INTEGER, ONE, null);

        register("serialize", 1, Serialize.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, AnyItemType.getInstance(), STAR, null);

//        register("serialize", 2, Serialize.class, BuiltInAtomicType.STRING, ONE, XPATH30, 0)
//                .arg(0, AnyItemType.getInstance(), STAR, null)
//                .arg(1, NodeKindTest.ELEMENT, OPT, null);

        register("sort", 1, Sort_1.class, AnyItemType.getInstance(), STAR, 0)
                .arg(0, AnyItemType.getInstance(), STAR, null);

        register("string-join", 1, StringJoin.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, StringValue.EMPTY_STRING);

//        register("string-join", 2, StringJoin.class, BuiltInAtomicType.STRING, ONE, CORE, 0)
//                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, StringValue.EMPTY_STRING)
//                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("tail", 1, TailFn.class, AnyItemType.getInstance(), STAR, AS_ARG0 | FILTER)
                .arg(0, AnyItemType.getInstance(), STAR | TRA, null);

        register("unparsed-text", 1, UnparsedText.class,
                 BuiltInAtomicType.STRING, OPT, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null);

        register("unparsed-text", 2, UnparsedText.class,
                 BuiltInAtomicType.STRING, OPT, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("unparsed-text-available", 1, UnparsedTextAvailable.class,
                 BuiltInAtomicType.BOOLEAN, ONE, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, BooleanValue.FALSE);

        register("unparsed-text-available", 2, UnparsedTextAvailable.class,
                 BuiltInAtomicType.BOOLEAN, ONE, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, BooleanValue.FALSE)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("unparsed-text-lines", 1, UnparsedTextLines.class, BuiltInAtomicType.STRING, STAR, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null);

        register("unparsed-text-lines", 2, UnparsedTextLines.class, BuiltInAtomicType.STRING, STAR, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("uri-collection", 0, UriCollection.class, BuiltInAtomicType.ANY_URI, STAR, LATE);

        register("uri-collection", 1, UriCollection.class, BuiltInAtomicType.ANY_URI, STAR, LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null);


    }


}
