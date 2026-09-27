////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.registry;

import net.sf.saxon.functions.*;
import net.sf.saxon.functions.hof.*;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.PortableNamedXNodeType;
import net.sf.saxon.type.*;
import net.sf.saxon.type.gnode.AnyXNodeType;
import net.sf.saxon.type.gnode.DocumentNodeType;
import net.sf.saxon.type.gnode.NodeKindType;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

/**
 * Function signatures (and pointers to implementations) of the functions defined in XPath 3.0 without the
 * Higher-Order-Functions feature
 */

public class XPath30FunctionSet extends BuiltInFunctionSet {

    private static final XPath30FunctionSet THE_INSTANCE = new XPath30FunctionSet();

    public static XPath30FunctionSet getInstance() {
        return THE_INSTANCE;
    }

    private XPath30FunctionSet() {
        init();
    }

    private void init() {

        importFunctionSet(XPath20FunctionSet.getInstance());

        register("analyze-string", 2, e -> e.populate(RegexFunctionSansFlags::new, NodeKindType.ELEMENT,
                                                      ONE, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        ItemType analyzeStringReturnType =
                     new PortableNamedXNodeType(
                             Type.ELEMENT,
                             new StructuredQName("", NamespaceUri.FN, "analyze-string-result"));
        register("analyze-string", 3, e -> e.populate( AnalyzeStringFn::new, analyzeStringReturnType,
                 ONE, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, OPT, null));

        register("apply", 2, e -> e.populate(ApplyFn::new, AnyItemType.getInstance(), STAR, LATE)
                .arg(0, AnyFunctionType.INSTANCE, ONE, null)
                .arg(1, ArrayItemType.ANY_ARRAY_TYPE, ONE, null));

        register("available-environment-variables", 0, e -> e.populate( AvailableEnvironmentVariables::new, BuiltInAtomicType.STRING,
                 STAR, LATE));

        register("data", 0, e -> e.populate( ContextItemAccessorFunction::new, BuiltInAtomicType.ANY_ATOMIC, STAR, CITEM | LATE));

        register("document-uri", 0, e -> e.populate( ContextItemAccessorFunction::new,
                 BuiltInAtomicType.ANY_URI, OPT, CITEM | LATE));

        register("element-with-id", 1, e -> e.populate(SuperId.ElementWithId::new, NodeKindType.ELEMENT, STAR, CITEM | LATE | UO)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY));

        register("element-with-id", 2, e -> e.populate(SuperId.ElementWithId::new, NodeKindType.ELEMENT, STAR, UO)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY)
                .arg(1, Type.XNODE_TYPE, ONE, null));

        register("environment-variable", 1, e -> e.populate( EnvironmentVariable::new, BuiltInAtomicType.STRING,
                 OPT, LATE)
                .arg(0, BuiltInAtomicType.STRING, ONE, null));

        SpecificFunctionType predicate = new SpecificFunctionType(
                SequenceType.SINGLE_ITEM,
                SequenceType.SINGLE_BOOLEAN);

        register("filter", 2, e -> e.populate(FilterFn::new, AnyItemType.getInstance(), STAR, AS_ARG0 | LATE)
                .arg(0, AnyItemType.getInstance(), STAR | TRA, EMPTY)
                .arg(1, predicate, ONE, null));

        SpecificFunctionType foldLeftArg = new SpecificFunctionType(
                SequenceType.ANY_SEQUENCE, SequenceType.SINGLE_ITEM,
                SequenceType.ANY_SEQUENCE);
        register("fold-left", 3, e -> e.populate(FoldLeftFn::new, AnyItemType.getInstance(), STAR, LATE)
                .arg(0, AnyItemType.getInstance(), STAR, null)
                .arg(1, AnyItemType.getInstance(), STAR, null)
                .arg(2, foldLeftArg, ONE, null));

        SpecificFunctionType foldRightArg = new SpecificFunctionType(
                SequenceType.SINGLE_ITEM, SequenceType.ANY_SEQUENCE,
                SequenceType.ANY_SEQUENCE);

        register("fold-right", 3, e -> e.populate(FoldRightFn::new, AnyItemType.getInstance(), STAR, LATE)
                .arg(0, AnyItemType.getInstance(), STAR, null)
                .arg(1, AnyItemType.getInstance(), STAR, null)
                .arg(2, foldRightArg, ONE, null));

        SpecificFunctionType forEachArg = new SpecificFunctionType(
                SequenceType.SINGLE_ITEM,
                SequenceType.ANY_SEQUENCE);
        register("for-each", 2, e -> e.populate(ForEachFn::new, AnyItemType.INSTANCE, STAR, LATE)
                .arg(0, AnyItemType.INSTANCE, STAR, EMPTY)
                .arg(1, forEachArg, ONE, null));

        SpecificFunctionType forEachPairArg = new SpecificFunctionType(
                SequenceType.SINGLE_ITEM, SequenceType.SINGLE_ITEM,
                SequenceType.ANY_SEQUENCE);
        register("for-each-pair", 3, e -> e.populate(ForEachPairFn::new, AnyItemType.INSTANCE, STAR, LATE)
                .arg(0, AnyItemType.INSTANCE, STAR, EMPTY)
                .arg(1, AnyItemType.INSTANCE, STAR, EMPTY)
                .arg(2, forEachPairArg, ONE, null));


        register("format-date", 2, e -> e.populate( FormatDate::new, BuiltInAtomicType.STRING,
                 OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("format-date", 5, e -> e.populate( FormatDate::new, BuiltInAtomicType.STRING,
                 OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, OPT, null)
                .arg(3, BuiltInAtomicType.STRING, OPT, null)
                .arg(4, BuiltInAtomicType.STRING, OPT, null));

        register("format-dateTime", 2, e -> e.populate( FormatDate::new, BuiltInAtomicType.STRING, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("format-dateTime", 5, e -> e.populate( FormatDate::new, BuiltInAtomicType.STRING, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, OPT, null)
                .arg(3, BuiltInAtomicType.STRING, OPT, null)
                .arg(4, BuiltInAtomicType.STRING, OPT, null));

        register("format-integer", 2, e -> e.populate( FormatInteger::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.INTEGER, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("format-integer", 3, e -> e.populate( FormatInteger::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.INTEGER, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, OPT, null));
        
        register("format-number", 2, e -> e.populate( FormatNumber::new, BuiltInAtomicType.STRING, ONE, LATE)
                .arg(0, NumericType.getInstance(), OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("format-number", 3, e -> e.populate( FormatNumber::new, BuiltInAtomicType.STRING, ONE, NS | LATE)
                .arg(0, NumericType.getInstance(), OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, OPT, null));

        register("format-time", 2, e -> e.populate( FormatDate::new, BuiltInAtomicType.STRING,
                 OPT, CARD0)
                .arg(0, BuiltInAtomicType.TIME, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("format-time", 5, e -> e.populate( FormatDate::new, BuiltInAtomicType.STRING,
                 OPT, CARD0)
                .arg(0, BuiltInAtomicType.TIME, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, OPT, null)
                .arg(3, BuiltInAtomicType.STRING, OPT, null)
                .arg(4, BuiltInAtomicType.STRING, OPT, null));

        register("function-arity", 1, e -> e.populate(FunctionArity::new, BuiltInAtomicType.INTEGER, ONE, 0)
                .arg(0, AnyFunctionType.INSTANCE, ONE, null));

        register("function-lookup", 2, e -> e.populate(FunctionLookup::new, AnyFunctionType.INSTANCE,
                                                   OPT, FOCUS | DEPENDS_ON_STATIC_CONTEXT | LATE)
                .arg(0, BuiltInAtomicType.QNAME, ONE, null)
                .arg(1, BuiltInAtomicType.INTEGER, ONE, null));

        register("function-name", 1, e -> e.populate(FunctionName::new, BuiltInAtomicType.QNAME, OPT, 0)
                .arg(0, AnyFunctionType.INSTANCE, ONE, null));

        register("generate-id", 0, e -> e.populate( ContextItemAccessorFunction::new, BuiltInAtomicType.STRING, ONE, CITEM | LATE));

        register("generate-id", 1, e -> e.populate( GenerateId_1::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, Type.XNODE_TYPE, OPT | INS, StringValue.EMPTY_STRING));

        register("has-children", 0, e -> e.populate( ContextItemAccessorFunction::new, BuiltInAtomicType.BOOLEAN,
                 ONE, CITEM | LATE));

        register("has-children", 1, e -> e.populate( HasChildren_1::new, BuiltInAtomicType.BOOLEAN,
                 ONE, 0)
                .arg(0, AnyXNodeType.getInstance(), OPT | INS, null));

        register("head", 1, e -> {
            return e.populate(HeadFn::new, AnyItemType.INSTANCE,
                              OPT, FILTER)
                    .arg(0, AnyItemType.INSTANCE, STAR | TRA, null);
        });

        register("innermost", 1, e -> e.populate(Innermost::new, AnyXNodeType.getInstance(),
                                                 STAR, 0)
                .arg(0, AnyXNodeType.getInstance(), STAR | NAV, null));

        register("nilled", 0, e -> e.populate( ContextItemAccessorFunction::new, BuiltInAtomicType.BOOLEAN, OPT, CITEM | LATE));

        register("node-name", 0, e -> e.populate( ContextItemAccessorFunction::new, BuiltInAtomicType.QNAME, OPT, CITEM | LATE));

        register("outermost", 1, e -> e.populate(Outermost::new, AnyXNodeType.getInstance(), STAR, AS_ARG0 | FILTER)
                .arg(0, AnyXNodeType.getInstance(), STAR | TRA, null));

        register("parse-xml", 1, e -> e.populate(ParseXml::new, new DocumentNodeType(NodeKindType.ELEMENT), OPT, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY));

        register("parse-xml-fragment", 1, e -> e.populate(ParseXmlFragment::new, NodeKindType.DOCUMENT, OPT, LATE | NEW)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY));

        register("path", 0, e -> e.populate( ContextItemAccessorFunction::new, BuiltInAtomicType.STRING, OPT, CITEM | LATE));

        register("path", 1, e -> e.populate(PathFn::new, BuiltInAtomicType.STRING, OPT, 0)
                .arg(0, AnyXNodeType.getInstance(), OPT | NAV, null));

        register("round", 2, e -> e.populate(Round::new, NumericType.getInstance(), OPT, AS_NUM_ARG0)
                .arg(0, NumericType.getInstance(), OPT, EMPTY)
                .arg(1, BuiltInAtomicType.INTEGER, ONE, null));

        register("serialize", 1, e -> e.populate(Serialize::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, AnyItemType.INSTANCE, STAR, null));

        register("sort", 1, e -> e.populate(Sort::new, AnyItemType.INSTANCE, STAR, 0)
                .arg(0, AnyItemType.INSTANCE, STAR, null));

        register("string-join", 1, e -> e.populate( StringJoin::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, StringValue.EMPTY_STRING));

        register("tail", 1, e -> e.populate(TailFn::new, AnyItemType.INSTANCE, STAR, AS_ARG0 | FILTER)
                .arg(0, AnyItemType.INSTANCE, STAR | TRA, null));

        register("unparsed-text", 1, e -> e.populate( UnparsedText::new,
                 BuiltInAtomicType.STRING, OPT, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null));

        register("unparsed-text", 2, e -> e.populate( UnparsedText::new, BuiltInAtomicType.STRING, OPT, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("unparsed-text-available", 1, e -> e.populate( UnparsedTextAvailable::new,
                 BuiltInAtomicType.BOOLEAN, ONE, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, BooleanValue.FALSE));

        register("unparsed-text-available", 2, e -> e.populate( UnparsedTextAvailable::new,
                 BuiltInAtomicType.BOOLEAN, ONE, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, BooleanValue.FALSE)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("unparsed-text-lines", 1, e -> e.populate( UnparsedTextLines::new, BuiltInAtomicType.STRING, STAR, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null));

        register("unparsed-text-lines", 2, e -> e.populate( UnparsedTextLines::new, BuiltInAtomicType.STRING, STAR, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("uri-collection", 0, e -> e.populate( UriCollection::new, BuiltInAtomicType.ANY_URI, STAR, LATE));

        register("uri-collection", 1, e -> e.populate( UriCollection::new, BuiltInAtomicType.ANY_URI, STAR, LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null));


    }


}
