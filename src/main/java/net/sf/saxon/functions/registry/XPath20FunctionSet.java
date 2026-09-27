////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.registry;

import net.sf.saxon.expr.ContextItemExpression;
import net.sf.saxon.functions.*;
import net.sf.saxon.functions.Error;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.transpile.CSharp;
import net.sf.saxon.type.*;
import net.sf.saxon.type.gnode.AnyGNodeType;
import net.sf.saxon.type.gnode.NodeKindType;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.StringValue;

/**
 * Function signatures (and pointers to implementations) of the functions defined in XPath 2.0
 */

public class XPath20FunctionSet extends BuiltInFunctionSet {

    private static final XPath20FunctionSet THE_INSTANCE = new XPath20FunctionSet();

    public static XPath20FunctionSet getInstance() {
        return THE_INSTANCE;
    }

    private XPath20FunctionSet() {
        init();
    }

    private void init() {
        register("abs", 1, e -> e.populate(Abs::new, NumericType.getInstance(), OPT, AS_NUM_ARG0)
                .arg(0, NumericType.getInstance(), OPT, EMPTY));

        register("adjust-date-to-timezone", 1, e -> e.populate(Adjust_1::new, BuiltInAtomicType.DATE, OPT, LATE|CARD0)
                .arg(0, BuiltInAtomicType.DATE, OPT, EMPTY));

        register("adjust-date-to-timezone", 2, e -> e.populate(Adjust_2::new, BuiltInAtomicType.DATE, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.DAY_TIME_DURATION, OPT, null));

        register("adjust-dateTime-to-timezone", 1, e -> e.populate(Adjust_1::new, BuiltInAtomicType.DATE_TIME, OPT, LATE|CARD0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY));

        register("adjust-dateTime-to-timezone", 2, e -> e.populate(Adjust_2::new, BuiltInAtomicType.DATE_TIME, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.DAY_TIME_DURATION, OPT, null));

        register("adjust-time-to-timezone", 1, e -> e.populate(Adjust_1::new, BuiltInAtomicType.TIME, OPT, LATE|CARD0)
                .arg(0, BuiltInAtomicType.TIME, OPT, EMPTY));

        register("adjust-time-to-timezone", 2, e -> e.populate(Adjust_2::new, BuiltInAtomicType.TIME, OPT, CARD0)
                .arg(0, BuiltInAtomicType.TIME, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.DAY_TIME_DURATION, OPT, null));

        register("avg", 1, e -> e.populate(Average::new, BuiltInAtomicType.ANY_ATOMIC, OPT, UO)
                // can't say "same as first argument" because the avg of a set of integers is decimal
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY));

        register("base-uri", 0, e -> e.populate(ContextItemAccessorFunction::new, BuiltInAtomicType.ANY_URI, OPT, CITEM | BASE | LATE));

        register("base-uri", 1, e -> e.populate(BaseUri_1::new, BuiltInAtomicType.ANY_URI, OPT, BASE)
                .arg(0, Type.XNODE_TYPE, OPT | INS, EMPTY));

        register("boolean", 1, e -> e.populate(BooleanFn::new, BuiltInAtomicType.BOOLEAN, ONE, 0)
                .arg(0, Type.ITEM_TYPE, STAR | INS, null));

        register("ceiling", 1, e -> e.populate(Ceiling::new, NumericType.getInstance(), OPT, AS_NUM_ARG0)
                .arg(0, NumericType.getInstance(), OPT, EMPTY));

        register("codepoint-equal", 2, e -> e.populate(CodepointEqual::new, BuiltInAtomicType.BOOLEAN, OPT, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.STRING, OPT, EMPTY));

        register("codepoints-to-string", 1, e -> e.populate(CodepointsToString::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.INTEGER, STAR, null));

        register("collection", 0, e -> e.populate(CollectionFn::new, Type.ITEM_TYPE, STAR, BASE | LATE));

        register("collection", 1, e -> e.populate(CollectionFn::new, Type.ITEM_TYPE, STAR, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null));

        register("compare", 2, e -> e.populate(Compare::new, BuiltInAtomicType.INTEGER, OPT, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.STRING, OPT, EMPTY));

        register("compare", 3, e -> e.populate(CollatingFunctionFree::new, BuiltInAtomicType.INTEGER, OPT, BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.STRING, OPT, EMPTY)
                .arg(2, BuiltInAtomicType.STRING, ONE, null));

        // Concat changes in 4.0:
        registerVariadic("concat", 1, e -> e.populate(Concat31::new, BuiltInAtomicType.STRING, ONE, SEQV)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, OPT, null));

        register("contains", 2, e -> e.populate(Contains::new, BuiltInAtomicType.BOOLEAN, ONE, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, BooleanValue.TRUE));

        register("contains", 3, e -> e.populate(CollatingFunctionFree::new, BuiltInAtomicType.BOOLEAN, ONE, BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, BooleanValue.TRUE)
                .arg(2, BuiltInAtomicType.STRING, ONE, null));

        register("count", 1, e -> e.populate(Count::new, BuiltInAtomicType.INTEGER, ONE, UO)
                .arg(0, Type.ITEM_TYPE, STAR | INS, Int64Value.ZERO));

        register("current-date", 0, e -> e.populate(DynamicContextAccessor.CurrentDate::new, BuiltInAtomicType.DATE, ONE, LATE));

        register("current-dateTime", 0, e -> e.populate(DynamicContextAccessor.CurrentDateTime::new, BuiltInAtomicType.DATE_TIME_STAMP, ONE, LATE));

        register("current-time", 0, e -> e.populate(DynamicContextAccessor.CurrentTime::new, BuiltInAtomicType.TIME, ONE, LATE));

        register("data", 1, e -> e.populate(Data_1::new, BuiltInAtomicType.ANY_ATOMIC, STAR, 0)
                .arg(0, Type.ITEM_TYPE, STAR | ABS, EMPTY));

        register("dateTime", 2, e -> e.populate(DateTimeConstructor::new, BuiltInAtomicType.DATE_TIME, OPT, 0)
                .arg(0, BuiltInAtomicType.DATE, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.TIME, OPT, EMPTY));

        register("day-from-date", 1, e -> e.populate(AccessorFn.DayFromDate::new, BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE, OPT, EMPTY));

        register("day-from-dateTime", 1, e -> e.populate(AccessorFn.DayFromDateTime::new, BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY));

        register("days-from-duration", 1, e -> e.populate(AccessorFn.DaysFromDuration::new, BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DURATION, OPT, EMPTY));

        register("deep-equal", 2, 4, e -> e.populate(DeepEqual20::new, BuiltInAtomicType.BOOLEAN, ONE, BASE)
                .arg(0, Type.ITEM_TYPE, STAR | ABS, null)
                .arg(1, Type.ITEM_TYPE, STAR | ABS, null)
                .arg(2, BuiltInAtomicType.STRING, OPT | ABS, null)
                .arg(3, MapType.ANY_MAP_TYPE, OPT | ABS, null));  // 4th arg not allowed in 2.0/3.1, but we reject it at runtime

        register("default-collation", 0, e -> e.populate(StaticContextAccessor.DefaultCollation::new, BuiltInAtomicType.STRING, ONE, DCOLL));

        register("distinct-values", 1, e -> e.populate(DistinctValues::new, BuiltInAtomicType.ANY_ATOMIC, STAR, DCOLL | UO)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY));

        register("distinct-values", 2, e -> e.populate(CollatingFunctionFree::new, BuiltInAtomicType.ANY_ATOMIC, STAR, BASE | UO)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("doc", 1, e -> e.populate(Doc::new, NodeKindType.DOCUMENT, OPT, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY));

        register("doc-available", 1, e -> e.populate(DocAvailable::new, BuiltInAtomicType.BOOLEAN, ONE, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, BooleanValue.FALSE));

        register("document-uri", 1, e -> e.populate(DocumentUri_1::new, BuiltInAtomicType.ANY_URI, OPT, LATE)
                .arg(0, Type.XNODE_TYPE, OPT | INS, EMPTY));

        register("element-with-id", 1, e -> e.populate(SuperId.ElementWithId::new, NodeKindType.ELEMENT, STAR, CDOC | LATE | UO)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY));

        register("element-with-id", 2, e -> e.populate(SuperId.ElementWithId::new, NodeKindType.ELEMENT, STAR, UO)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY)
                .arg(1, Type.XNODE_TYPE, ONE, null));

        register("empty", 1, e -> e.populate(Empty::new, BuiltInAtomicType.BOOLEAN, ONE, UO)
                .arg(0, Type.ITEM_TYPE, STAR | INS, BooleanValue.TRUE));

        register("encode-for-uri", 1, e -> e.populate(EncodeForUri::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING));

        register("ends-with", 2, e -> e.populate(EndsWith::new, BuiltInAtomicType.BOOLEAN, ONE, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, BooleanValue.TRUE));

        register("ends-with", 3, e -> e.populate(CollatingFunctionFree::new, BuiltInAtomicType.BOOLEAN, ONE, BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, BooleanValue.TRUE)
                .arg(2, BuiltInAtomicType.STRING, ONE, null));

        register("escape-html-uri", 1, e -> e.populate(EscapeHtmlUri::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING));

        register("error", 0, e -> e.populate(Error::new, ErrorType.getInstance(), STAR, LATE));
                // The return type is chosen so that use of the error() function will never give a static type error,
                // on the basis that item()* overlaps every other type, and it's almost impossible to make any
                // unwarranted inferences from it.

        register("error", 1, e -> e.populate(Error::new, ErrorType.getInstance(), STAR, LATE)
                .arg(0, BuiltInAtomicType.QNAME, OPT, null));

        register("error", 2, e -> e.populate(Error::new, ErrorType.getInstance(), STAR, LATE)
                .arg(0, BuiltInAtomicType.QNAME, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("error", 3, e -> e.populate(Error::new, Type.ITEM_TYPE, STAR, LATE)
                .arg(0, BuiltInAtomicType.QNAME, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, Type.ITEM_TYPE, STAR, null));

        register("exactly-one", 1, e -> e.populate(TreatFn.ExactlyOne::new, Type.ITEM_TYPE, ONE, AS_ARG0 | FILTER)
                .arg(0, Type.ITEM_TYPE, STAR | TRA, null));

        register("exists", 1, e -> e.populate(Exists::new, BuiltInAtomicType.BOOLEAN, ONE, UO)
                .arg(0, Type.ITEM_TYPE, STAR | INS, BooleanValue.FALSE));

        register("false", 0, e -> e.populate(ConstantSystemFunction.False::new, BuiltInAtomicType.BOOLEAN, ONE, 0));

        register("floor", 1, e -> e.populate(Floor::new, NumericType.getInstance(), OPT, AS_NUM_ARG0)
                .arg(0, NumericType.getInstance(), OPT, EMPTY));

        register("hours-from-dateTime", 1, e -> e.populate(AccessorFn.HoursFromDateTime::new,
                                                           BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY));

        register("hours-from-duration", 1, e -> e.populate(AccessorFn.HoursFromDuration::new,
                                                           BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DURATION, OPT, EMPTY));

        register("hours-from-time", 1, e -> e.populate(AccessorFn.HoursFromTime::new,
                                                       BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.TIME, OPT, EMPTY));

        register("id", 1, e -> e.populate(SuperId.Id::new, NodeKindType.ELEMENT, STAR, CDOC | LATE | UO)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY));

        register("id", 2, e -> e.populate(SuperId.Id::new, NodeKindType.ELEMENT, STAR, LATE | UO)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY)
                .arg(1, Type.XNODE_TYPE, ONE | NAV, null));

        register("idref", 1, e -> e.populate(Idref::new, Type.XNODE_TYPE, STAR, CDOC | LATE)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY));

        register("idref", 2, e -> e.populate(Idref::new, Type.XNODE_TYPE, STAR, LATE)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY)
                .arg(1, Type.XNODE_TYPE, ONE | NAV, null));

        register("implicit-timezone", 0, e -> e.populate(DynamicContextAccessor.ImplicitTimezone::new, BuiltInAtomicType.DAY_TIME_DURATION, ONE, LATE));

        register("in-scope-prefixes", 1, e -> e.populate(InScopePrefixes::new, BuiltInAtomicType.STRING, STAR, 0)
                .arg(0, NodeKindType.ELEMENT, ONE | INS, null));

        register("index-of", 2, e -> e.populate(IndexOf::new, BuiltInAtomicType.INTEGER, STAR, DCOLL)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE, null));

        register("index-of", 3, e -> e.populate(CollatingFunctionFree::new, BuiltInAtomicType.INTEGER, STAR, BASE)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null));

        register("insert-before", 3, e -> e.populate(InsertBefore::new, Type.ITEM_TYPE, STAR, 0)
                .arg(0, Type.ITEM_TYPE, STAR | TRA, null)
                .arg(1, BuiltInAtomicType.INTEGER, ONE, null)
                .arg(2, Type.ITEM_TYPE, STAR | TRA, null));

        register("iri-to-uri", 1, e -> e.populate(IriToUri::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING));

        register("lang", 1, 2, e -> e.populate(Lang::new, BuiltInAtomicType.BOOLEAN, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, Type.XNODE_TYPE, ONE | INS, null, ContextItemExpression::new));

        register("last", 0, e -> e.populate(PositionAndLast.Last::new, BuiltInAtomicType.INTEGER, ONE, LAST | LATE));

        register("local-name", 0, e -> e.populate(ContextItemAccessorFunction::new, BuiltInAtomicType.STRING, ONE, CITEM | LATE));

        register("local-name", 1, e -> e.populate(LocalName_1::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, Type.XNODE_TYPE, OPT | INS, StringValue.EMPTY_STRING)
        );

        register("local-name-from-QName", 1, e -> e.populate(AccessorFn.LocalNameFromQName::new,
                                                             BuiltInAtomicType.NCNAME, OPT, 0)
                .arg(0, BuiltInAtomicType.QNAME, OPT, EMPTY));

        register("lower-case", 1, e -> e.populate(LowerCase::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING));

        register("matches", 2, e -> e.populate(RegexFunctionSansFlags::new, BuiltInAtomicType.BOOLEAN, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("matches", 3, e -> e.populate(Matches::new, BuiltInAtomicType.BOOLEAN, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null));

        register("max", 1, e -> e.populate(Minimax.Max::new, BuiltInAtomicType.ANY_ATOMIC, OPT, DCOLL | UO | CARD0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY));

        register("max", 2, e -> e.populate(CollatingFunctionFree::new, BuiltInAtomicType.ANY_ATOMIC, OPT, BASE | UO | CARD0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("min", 1, e -> e.populate(Minimax.Min::new, BuiltInAtomicType.ANY_ATOMIC, OPT, DCOLL | UO | CARD0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY));

        register("min", 2, e -> e.populate(CollatingFunctionFree::new, BuiltInAtomicType.ANY_ATOMIC, OPT, BASE | UO | CARD0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("minutes-from-dateTime", 1, e -> e.populate(AccessorFn.MinutesFromDateTime::new,
                                                             BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY));

        register("minutes-from-duration", 1, e -> e.populate(AccessorFn.MinutesFromDuration::new,
                                                             BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DURATION, OPT, EMPTY));

        register("minutes-from-time", 1, e -> e.populate(AccessorFn.MinutesFromTime::new,
                                                         BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.TIME, OPT, EMPTY));

        register("month-from-date", 1, e -> e.populate(AccessorFn.MonthFromDate::new,
                                                       BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE, OPT, EMPTY));

        register("month-from-dateTime", 1, e -> e.populate(AccessorFn.MonthFromDateTime::new,
                                                           BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY));

        register("months-from-duration", 1, e -> e.populate(AccessorFn.MonthsFromDuration::new,
                                                            BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DURATION, OPT, EMPTY));

        register("name", 0, e -> e.populate(ContextItemAccessorFunction::new, BuiltInAtomicType.STRING, ONE, CITEM | LATE));

        register("name", 1, e -> e.populate(Name_1::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, Type.XNODE_TYPE, OPT | INS, StringValue.EMPTY_STRING));

        register("namespace-uri", 0, e -> e.populate(ContextItemAccessorFunction::new, BuiltInAtomicType.ANY_URI, ONE, CITEM | LATE));

        register("namespace-uri", 1, e -> e.populate(NamespaceUriFn_1::new, BuiltInAtomicType.ANY_URI, ONE, 0)
                .arg(0, Type.XNODE_TYPE, OPT | INS, StringValue.EMPTY_STRING));

        register("namespace-uri-for-prefix", 2, e -> e.populate(NamespaceForPrefix::new, BuiltInAtomicType.ANY_URI, OPT, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, NodeKindType.ELEMENT, ONE | INS, null));

        register("namespace-uri-from-QName", 1, e -> e.populate(AccessorFn.NamespaceUriFromQName::new, BuiltInAtomicType.ANY_URI, OPT, CARD0)
                .arg(0, BuiltInAtomicType.QNAME, OPT, EMPTY));

        register("nilled", 1, e -> e.populate(Nilled_1::new, BuiltInAtomicType.BOOLEAN, OPT, 0)
                .arg(0, Type.XNODE_TYPE, OPT | INS, EMPTY));

        register("node-name", 1, e -> e.populate(NodeName_1::new, BuiltInAtomicType.QNAME, OPT, 0)
                .arg(0, Type.XNODE_TYPE, OPT | INS, EMPTY));

        register("not", 1, e -> e.populate(NotFn::new, BuiltInAtomicType.BOOLEAN, ONE, 0)
                .arg(0, Type.ITEM_TYPE, STAR | INS, BooleanValue.TRUE));

        register("normalize-space", 0, e -> e.populate(ContextItemAccessorFunction.StringAccessor::new, BuiltInAtomicType.STRING, ONE, CITEM | LATE));

        register("normalize-space", 1, e -> e.populate(NormalizeSpace_1::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null));

        register("normalize-unicode", 1, e -> e.populate(NormalizeUnicode::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING));

        register("normalize-unicode", 2, e -> e.populate(NormalizeUnicode::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("number", 0, e -> e.populate(ContextItemAccessorFunction.Number_0::new, BuiltInAtomicType.DOUBLE, ONE, CITEM | LATE));

        register("number", 1, e -> e.populate(Number_1::new, BuiltInAtomicType.DOUBLE, ONE, 0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, OPT, DoubleValue.NaN));

        register("one-or-more", 1, e -> e.populate(TreatFn.OneOrMore::new, Type.ITEM_TYPE, PLUS, AS_ARG0 | FILTER)
                .arg(0, Type.ITEM_TYPE, STAR | TRA, null));

        register("position", 0, e -> e.populate(PositionAndLast.Position::new, BuiltInAtomicType.INTEGER, ONE, POSN | LATE));

        register("prefix-from-QName", 1, e -> e.populate(AccessorFn.PrefixFromQName::new, BuiltInAtomicType.NCNAME, OPT, 0)
                .arg(0, BuiltInAtomicType.QNAME, OPT, EMPTY));

        register("QName", 2, e -> e.populate(QNameFn::new, BuiltInAtomicType.QNAME, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("remove", 2, e -> e.populate(Remove::new, Type.ITEM_TYPE, STAR, AS_ARG0 | FILTER)
                .arg(0, Type.ITEM_TYPE, STAR | TRA, EMPTY)
                .arg(1, BuiltInAtomicType.INTEGER, ONE, null));

        register("replace", 3, e -> e.populate(RegexFunctionSansFlags::new, BuiltInAtomicType.STRING,
                 ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null));

        register("replace", 4, e -> e.populate(CSharp.staticRef(Replace::make20), BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null)
                .arg(3, BuiltInAtomicType.STRING, ONE, null));

        register("resolve-QName", 2, e -> e.populate(ResolveQName::new, BuiltInAtomicType.QNAME, OPT, CARD0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY)
                .arg(1, NodeKindType.ELEMENT, ONE | INS, null));

        register("resolve-uri", 1, e -> e.populate(ResolveURI::new, BuiltInAtomicType.ANY_URI, OPT, CARD0 | BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null));

        register("resolve-uri", 2, e -> e.populate(ResolveURI::new, BuiltInAtomicType.ANY_URI, OPT, CARD0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("reverse", 1, e -> e.populate(Reverse::new, Type.ITEM_TYPE, STAR, AS_ARG0 | FILTER)
                .arg(0, Type.ITEM_TYPE, STAR | NAV, EMPTY));

        register("root", 0, e -> e.populate(ContextItemAccessorFunction::new, AnyGNodeType.getInstance(), ONE, CITEM | LATE));

        register("root", 1, e -> e.populate(Root_1::new, AnyGNodeType.getInstance(), OPT, CARD0)
                .arg(0, AnyGNodeType.getInstance(), OPT | NAV, EMPTY));

        register("round", 1, e -> e.populate(Round::new, NumericType.getInstance(), OPT, AS_NUM_ARG0)
                .arg(0, NumericType.getInstance(), OPT, EMPTY));

        register("round-half-to-even", 1, e -> e.populate(RoundHalfToEven::new, NumericType.getInstance(), OPT, AS_NUM_ARG0)
                .arg(0, NumericType.getInstance(), OPT, EMPTY));

        register("round-half-to-even", 2, e -> e.populate(RoundHalfToEven::new, NumericType.getInstance(), OPT, AS_NUM_ARG0)
                .arg(0, NumericType.getInstance(), OPT, EMPTY)
                .arg(1, BuiltInAtomicType.INTEGER, ONE, null));

        register("seconds-from-dateTime", 1, e -> e.populate(AccessorFn.SecondsFromDateTime::new, BuiltInAtomicType.DECIMAL, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY));

        register("seconds-from-duration", 1, e -> e.populate(AccessorFn.SecondsFromDuration::new, BuiltInAtomicType.DECIMAL, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DURATION, OPT, EMPTY));

        register("seconds-from-time", 1, e -> e.populate(AccessorFn.SecondsFromTime::new, BuiltInAtomicType.DECIMAL, OPT, CARD0)
                .arg(0, BuiltInAtomicType.TIME, OPT, EMPTY));

        register("starts-with", 2, e -> e.populate(StartsWith::new, BuiltInAtomicType.BOOLEAN, ONE, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, BooleanValue.TRUE));

        register("starts-with", 3, e -> e.populate(CollatingFunctionFree::new, BuiltInAtomicType.BOOLEAN, ONE, BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, BooleanValue.TRUE)
                .arg(2, BuiltInAtomicType.STRING, ONE, null));

        register("static-base-uri", 0, e -> e.populate(StaticBaseUri::new, BuiltInAtomicType.ANY_URI, OPT, BASE | LATE));

        register("string", 0, e -> e.populate(ContextItemAccessorFunction::new, BuiltInAtomicType.STRING, ONE, CITEM | LATE));

        register("string", 1, e -> e.populate(String_1::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, Type.ITEM_TYPE, OPT | ABS, StringValue.EMPTY_STRING));

        register("string-length", 0, e -> e.populate(ContextItemAccessorFunction.StringAccessor::new, BuiltInAtomicType.INTEGER, ONE, CITEM | LATE));

        register("string-length", 1, e -> e.populate(StringLength_1::new, BuiltInAtomicType.INTEGER, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null));

        // Use the 3.0 function signature even if we're running 2.0
        register("string-join", 2, e -> e.populate(StringJoin::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, StringValue.EMPTY_STRING)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("string-to-codepoints", 1, e -> e.populate(StringToCodepoints::new, BuiltInAtomicType.INTEGER, STAR, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY));

        register("subsequence", 2, e -> e.populate(Subsequence_2::new, Type.ITEM_TYPE, STAR, AS_ARG0 | FILTER)
                .arg(0, Type.ITEM_TYPE, STAR | TRA, EMPTY)
                .arg(1, NumericType.getInstance(), ONE, null));

        register("subsequence", 3, e -> e.populate(Subsequence_3::new, Type.ITEM_TYPE, STAR, AS_ARG0 | FILTER)
                .arg(0, Type.ITEM_TYPE, STAR | TRA, EMPTY)
                .arg(1, NumericType.getInstance(), ONE, null)
                .arg(2, NumericType.getInstance(), ONE, null));

        register("substring", 2, e -> e.populate(Substring::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING)
                .arg(1, NumericType.getInstance(), ONE, null));

        register("substring", 3, e -> e.populate(Substring::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING)
                .arg(1, NumericType.getInstance(), ONE, null)
                .arg(2, NumericType.getInstance(), OPT, null));

        register("substring-after", 2, e -> e.populate(SubstringAfter::new, BuiltInAtomicType.STRING, ONE, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, null));

        register("substring-after", 3, e -> e.populate(CollatingFunctionFree::new, BuiltInAtomicType.STRING, ONE, BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null));

        register("substring-before", 2, e -> e.populate(SubstringBefore::new, BuiltInAtomicType.STRING, ONE, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING));

        register("substring-before", 3, e -> e.populate(CollatingFunctionFree::new, BuiltInAtomicType.STRING, ONE, BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING)
                .arg(2, BuiltInAtomicType.STRING, ONE, null));

        register("sum", 1, e -> e.populate(Sum::new, BuiltInAtomicType.ANY_ATOMIC, ONE, UO)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, null));

        register("sum", 1, 2, e -> e.populate(Sum::new, BuiltInAtomicType.ANY_ATOMIC, OPT, UO)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, OPT, null));

        register("timezone-from-date", 1, e -> e.populate(AccessorFn.TimezoneFromDate::new, BuiltInAtomicType.DAY_TIME_DURATION, OPT, 0)
                .arg(0, BuiltInAtomicType.DATE, OPT, EMPTY));

        register("timezone-from-dateTime", 1, e -> e.populate(AccessorFn.TimezoneFromDateTime::new,
                                                              BuiltInAtomicType.DAY_TIME_DURATION, OPT, 0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY));

        register("timezone-from-time", 1, e -> e.populate(AccessorFn.TimezoneFromTime::new,
                                                          BuiltInAtomicType.DAY_TIME_DURATION, OPT, 0)
                .arg(0, BuiltInAtomicType.TIME, OPT, EMPTY));

        register("tokenize", 2, e -> e.populate(RegexFunctionSansFlags::new, BuiltInAtomicType.STRING, STAR, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("tokenize", 3, e -> e.populate(Tokenize_3::new, BuiltInAtomicType.STRING, STAR, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null));

        register("trace", 2, e -> e.populate(Trace::new, Type.ITEM_TYPE, STAR, AS_ARG0 | LATE)
                .arg(0, Type.ITEM_TYPE, STAR | TRA, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null));

        register("translate", 3, e -> e.populate(Translate::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null));

        register("true", 0, e -> e.populate(ConstantSystemFunction.True::new, BuiltInAtomicType.BOOLEAN, ONE, 0));

        register("unordered", 1, e -> e.populate(Unordered::new, Type.ITEM_TYPE, STAR, AS_ARG0 | FILTER | UO)
                .arg(0, Type.ITEM_TYPE, STAR | TRA, EMPTY));

        register("upper-case", 1, e -> e.populate(UpperCase::new, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING));

        register("year-from-date", 1, e -> e.populate(AccessorFn.YearFromDate::new,
                                                      BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE, OPT, EMPTY));

        register("year-from-dateTime", 1, e -> e.populate(AccessorFn.YearFromDateTime::new,
                                                          BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY));

        register("years-from-duration", 1, e -> e.populate(AccessorFn.YearsFromDuration::new,
                                                           BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DURATION, OPT, EMPTY));

        register("zero-or-one", 1, e -> e.populate(TreatFn.ZeroOrOne::new, Type.ITEM_TYPE, OPT, AS_ARG0 | FILTER)
                .arg(0, Type.ITEM_TYPE, STAR | TRA, null));
    }

}
