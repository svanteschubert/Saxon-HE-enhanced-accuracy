////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.registry;

import net.sf.saxon.functions.*;
import net.sf.saxon.functions.Error;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.NumericType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.StringValue;

/**
 * Function signatures (and pointers to implementations) of the functions defined in XPath 2.0
 */

public class XPath20FunctionSet extends BuiltInFunctionSet {

    private static XPath20FunctionSet THE_INSTANCE = new XPath20FunctionSet();

    public static XPath20FunctionSet getInstance() {
        return THE_INSTANCE;
    }

    private XPath20FunctionSet() {
        init();
    }

    private void init() {
        register("abs", 1, Abs.class, NumericType.getInstance(), OPT, AS_PRIM_ARG0)
                .arg(0, NumericType.getInstance(), OPT, EMPTY);

        register("adjust-date-to-timezone", 1, Adjust_1.class, BuiltInAtomicType.DATE, OPT, LATE|CARD0)
                .arg(0, BuiltInAtomicType.DATE, OPT, EMPTY);

        register("adjust-date-to-timezone", 2, Adjust_2.class, BuiltInAtomicType.DATE, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.DAY_TIME_DURATION, OPT, null);

        register("adjust-dateTime-to-timezone", 1, Adjust_1.class, BuiltInAtomicType.DATE_TIME, OPT, LATE|CARD0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY);

        register("adjust-dateTime-to-timezone", 2, Adjust_2.class, BuiltInAtomicType.DATE_TIME, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.DAY_TIME_DURATION, OPT, null);

        register("adjust-time-to-timezone", 1, Adjust_1.class, BuiltInAtomicType.TIME, OPT, LATE|CARD0)
                .arg(0, BuiltInAtomicType.TIME, OPT, EMPTY);

        register("adjust-time-to-timezone", 2, Adjust_2.class, BuiltInAtomicType.TIME, OPT, CARD0)
                .arg(0, BuiltInAtomicType.TIME, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.DAY_TIME_DURATION, OPT, null);

        register("avg", 1, Average.class, BuiltInAtomicType.ANY_ATOMIC, OPT, UO)
                // can't say "same as first argument" because the avg of a set of integers is decimal
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY);

        register("base-uri", 0, ContextItemAccessorFunction.class, BuiltInAtomicType.ANY_URI, OPT, CITEM | BASE | LATE);

        register("base-uri", 1, BaseUri_1.class, BuiltInAtomicType.ANY_URI, OPT, BASE)
                .arg(0, Type.NODE_TYPE, OPT | INS, EMPTY);

        register("boolean", 1, BooleanFn.class, BuiltInAtomicType.BOOLEAN, ONE, 0)
                .arg(0, Type.ITEM_TYPE, STAR | INS, null);

        register("ceiling", 1, Ceiling.class, NumericType.getInstance(), OPT, AS_PRIM_ARG0)
                .arg(0, NumericType.getInstance(), OPT, EMPTY);

        register("codepoint-equal", 2, CodepointEqual.class, BuiltInAtomicType.BOOLEAN, OPT, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.STRING, OPT, EMPTY);

        register("codepoints-to-string", 1, CodepointsToString.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.INTEGER, STAR, null);

        register("collection", 0, CollectionFn.class, Type.ITEM_TYPE, STAR, BASE | LATE);

        register("collection", 1, CollectionFn.class, Type.ITEM_TYPE, STAR, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null);

        register("compare", 2, Compare.class, BuiltInAtomicType.INTEGER, OPT, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.STRING, OPT, EMPTY);

        register("compare", 3, CollatingFunctionFree.class, BuiltInAtomicType.INTEGER, OPT, BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.STRING, OPT, EMPTY)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("concat", -1, Concat.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, OPT, null);
        // Note, this has a variable number of arguments so it is treated specially

        register("contains", 2, Contains.class, BuiltInAtomicType.BOOLEAN, ONE, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, BooleanValue.TRUE);

        register("contains", 3, CollatingFunctionFree.class, BuiltInAtomicType.BOOLEAN, ONE, BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, BooleanValue.TRUE)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("count", 1, Count.class, BuiltInAtomicType.INTEGER, ONE, UO)
                .arg(0, Type.ITEM_TYPE, STAR | INS, Int64Value.ZERO);

        register("current-date", 0, DynamicContextAccessor.CurrentDate.class, BuiltInAtomicType.DATE, ONE, LATE);

        register("current-dateTime", 0, DynamicContextAccessor.CurrentDateTime.class, BuiltInAtomicType.DATE_TIME, ONE, LATE);

        register("current-time", 0, DynamicContextAccessor.CurrentTime.class, BuiltInAtomicType.TIME, ONE, LATE);

        register("data", 1, Data_1.class, BuiltInAtomicType.ANY_ATOMIC, STAR, 0)
                .arg(0, Type.ITEM_TYPE, STAR | ABS, EMPTY);

        register("dateTime", 2, DateTimeConstructor.class, BuiltInAtomicType.DATE_TIME, OPT, 0)
                .arg(0, BuiltInAtomicType.DATE, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.TIME, OPT, EMPTY);

        register("day-from-date", 1, AccessorFn.DayFromDate.class, BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE, OPT, EMPTY);

        register("day-from-dateTime", 1, AccessorFn.DayFromDateTime.class, BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY);

        register("days-from-duration", 1, AccessorFn.DaysFromDuration.class, BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DURATION, OPT, EMPTY);

        register("deep-equal", 2, DeepEqual.class, BuiltInAtomicType.BOOLEAN, ONE, DCOLL)
                .arg(0, Type.ITEM_TYPE, STAR | ABS, null)
                .arg(1, Type.ITEM_TYPE, STAR | ABS, null);

        register("deep-equal", 3, CollatingFunctionFree.class, BuiltInAtomicType.BOOLEAN, ONE, BASE)
                .arg(0, Type.ITEM_TYPE, STAR, null)
                .arg(1, Type.ITEM_TYPE, STAR, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("default-collation", 0, StaticContextAccessor.DefaultCollation.class, BuiltInAtomicType.STRING, ONE, DCOLL);

        register("distinct-values", 1, DistinctValues.class, BuiltInAtomicType.ANY_ATOMIC, STAR, DCOLL | UO)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY);

        register("distinct-values", 2, CollatingFunctionFree.class, BuiltInAtomicType.ANY_ATOMIC, STAR, BASE | UO)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("doc", 1, Doc.class, NodeKindTest.DOCUMENT, OPT, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY);

        register("doc-available", 1, DocAvailable.class, BuiltInAtomicType.BOOLEAN, ONE, BASE | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, BooleanValue.FALSE);

        register("document-uri", 1, DocumentUri_1.class,
                 BuiltInAtomicType.ANY_URI, OPT, LATE)
                .arg(0, Type.NODE_TYPE, OPT | INS, EMPTY);

        register("element-with-id", 1, SuperId.ElementWithId.class, NodeKindTest.ELEMENT, STAR, CDOC | LATE | UO)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY);

        register("element-with-id", 2, SuperId.ElementWithId.class, NodeKindTest.ELEMENT, STAR, UO)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY)
                .arg(1, Type.NODE_TYPE, ONE, null);

        register("empty", 1, Empty.class, BuiltInAtomicType.BOOLEAN, ONE, UO)
                .arg(0, Type.ITEM_TYPE, STAR | INS, BooleanValue.TRUE);

        register("encode-for-uri", 1, EncodeForUri.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING);

        register("ends-with", 2, EndsWith.class, BuiltInAtomicType.BOOLEAN, ONE, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, BooleanValue.TRUE);

        register("ends-with", 3, CollatingFunctionFree.class, BuiltInAtomicType.BOOLEAN, ONE, BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, BooleanValue.TRUE)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("escape-html-uri", 1, EscapeHtmlUri.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING);

        register("error", 0, Error.class, Type.ITEM_TYPE, OPT, LATE);
                // The return type is chosen so that use of the error() function will never give a static type error,
                // on the basis that item()? overlaps every other type, and it's almost impossible to make any
                // unwarranted inferences from it, except perhaps count(error()) lt 2.

        register("error", 1, Error.class, Type.ITEM_TYPE, OPT, LATE)
                .arg(0, BuiltInAtomicType.QNAME, OPT, null);

        register("error", 2, Error.class, Type.ITEM_TYPE, OPT, LATE)
                .arg(0, BuiltInAtomicType.QNAME, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("error", 3, Error.class, Type.ITEM_TYPE, OPT, LATE)
                .arg(0, BuiltInAtomicType.QNAME, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, Type.ITEM_TYPE, STAR, null);

        register("exactly-one", 1, TreatFn.ExactlyOne.class, Type.ITEM_TYPE, ONE, AS_ARG0 | FILTER)
                .arg(0, Type.ITEM_TYPE, STAR | TRA, null);

        register("exists", 1, Exists.class, BuiltInAtomicType.BOOLEAN, ONE, UO)
                .arg(0, Type.ITEM_TYPE, STAR | INS, BooleanValue.FALSE);

        register("false", 0, ConstantFunction.False.class, BuiltInAtomicType.BOOLEAN, ONE, 0);

        register("floor", 1, Floor.class, NumericType.getInstance(), OPT, AS_PRIM_ARG0)
                .arg(0, NumericType.getInstance(), OPT, EMPTY);

        register("hours-from-dateTime", 1, AccessorFn.HoursFromDateTime.class,
                 BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY);

        register("hours-from-duration", 1, AccessorFn.HoursFromDuration.class,
                 BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DURATION, OPT, EMPTY);

        register("hours-from-time", 1, AccessorFn.HoursFromTime.class,
                 BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.TIME, OPT, EMPTY);

        register("id", 1, SuperId.Id.class, NodeKindTest.ELEMENT, STAR, CDOC | LATE | UO)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY);

        register("id", 2, SuperId.Id.class, NodeKindTest.ELEMENT, STAR, LATE | UO)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY)
                .arg(1, Type.NODE_TYPE, ONE | NAV, null);

        register("idref", 1, Idref.class, Type.NODE_TYPE, STAR, CDOC | LATE)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY);

        register("idref", 2, Idref.class, Type.NODE_TYPE, STAR, LATE)
                .arg(0, BuiltInAtomicType.STRING, STAR, EMPTY)
                .arg(1, Type.NODE_TYPE, ONE | NAV, null);

        register("implicit-timezone", 0, DynamicContextAccessor.ImplicitTimezone.class, BuiltInAtomicType.DAY_TIME_DURATION, ONE, LATE);

        register("in-scope-prefixes", 1, InScopePrefixes.class, BuiltInAtomicType.STRING, STAR, 0)
                .arg(0, NodeKindTest.ELEMENT, ONE | INS, null);

        register("index-of", 2, IndexOf.class, BuiltInAtomicType.INTEGER, STAR, DCOLL)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE, null);

        register("index-of", 3, CollatingFunctionFree.class, BuiltInAtomicType.INTEGER, STAR, BASE)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("insert-before", 3, InsertBefore.class, Type.ITEM_TYPE, STAR, 0)
                .arg(0, Type.ITEM_TYPE, STAR | TRA, null)
                .arg(1, BuiltInAtomicType.INTEGER, ONE, null)
                .arg(2, Type.ITEM_TYPE, STAR | TRA, null);

        register("iri-to-uri", 1, IriToUri.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING);

        register("lang", 1, Lang.class, BuiltInAtomicType.BOOLEAN, ONE, CITEM | LATE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null);

        register("lang", 2, Lang.class, BuiltInAtomicType.BOOLEAN, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, Type.NODE_TYPE, ONE | INS, null);

        register("last", 0, PositionAndLast.Last.class, BuiltInAtomicType.INTEGER, ONE, LAST | LATE);

        register("local-name", 0, ContextItemAccessorFunction.class, BuiltInAtomicType.STRING, ONE, CITEM | LATE);

        register("local-name", 1, LocalName_1.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, Type.NODE_TYPE, OPT | INS, StringValue.EMPTY_STRING);

        register("local-name-from-QName", 1, AccessorFn.LocalNameFromQName.class,
                 BuiltInAtomicType.NCNAME, OPT, 0)
                .arg(0, BuiltInAtomicType.QNAME, OPT, EMPTY);

        register("lower-case", 1, LowerCase.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING);

        register("matches", 2, RegexFunctionSansFlags.class, BuiltInAtomicType.BOOLEAN, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("matches", 3, Matches.class, BuiltInAtomicType.BOOLEAN, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("max", 1, Minimax.Max.class, BuiltInAtomicType.ANY_ATOMIC, OPT, DCOLL | UO | CARD0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY);

        register("max", 2, CollatingFunctionFree.class, BuiltInAtomicType.ANY_ATOMIC, OPT, BASE | UO | CARD0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("min", 1, Minimax.Min.class, BuiltInAtomicType.ANY_ATOMIC, OPT, DCOLL | UO | CARD0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY);

        register("min", 2, CollatingFunctionFree.class, BuiltInAtomicType.ANY_ATOMIC, OPT, BASE | UO | CARD0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, EMPTY)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("minutes-from-dateTime", 1, AccessorFn.MinutesFromDateTime.class,
                 BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY);

        register("minutes-from-duration", 1, AccessorFn.MinutesFromDuration.class,
                 BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DURATION, OPT, EMPTY);

        register("minutes-from-time", 1, AccessorFn.MinutesFromTime.class,
                 BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.TIME, OPT, EMPTY);

        register("month-from-date", 1, AccessorFn.MonthFromDate.class,
                 BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE, OPT, EMPTY);

        register("month-from-dateTime", 1, AccessorFn.MonthFromDateTime.class,
                 BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY);

        register("months-from-duration", 1, AccessorFn.MonthsFromDuration.class,
                 BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DURATION, OPT, EMPTY);

        register("name", 0, ContextItemAccessorFunction.class, BuiltInAtomicType.STRING, ONE, CITEM | LATE);

        register("name", 1, Name_1.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, Type.NODE_TYPE, OPT | INS, StringValue.EMPTY_STRING);

        register("namespace-uri", 0, ContextItemAccessorFunction.class, BuiltInAtomicType.ANY_URI, ONE, CITEM | LATE);

        register("namespace-uri", 1, NamespaceUri_1.class, BuiltInAtomicType.ANY_URI, ONE, 0)
                .arg(0, Type.NODE_TYPE, OPT | INS, StringValue.EMPTY_STRING);

        register("namespace-uri-for-prefix", 2, NamespaceForPrefix.class, BuiltInAtomicType.ANY_URI, OPT, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, NodeKindTest.ELEMENT, ONE | INS, null);

        register("namespace-uri-from-QName", 1, AccessorFn.NamespaceUriFromQName.class, BuiltInAtomicType.ANY_URI, OPT, CARD0)
                .arg(0, BuiltInAtomicType.QNAME, OPT, EMPTY);

        register("nilled", 1, Nilled_1.class, BuiltInAtomicType.BOOLEAN, OPT, 0)
                .arg(0, Type.NODE_TYPE, OPT | INS, EMPTY);

        register("node-name", 1, NodeName_1.class, BuiltInAtomicType.QNAME, OPT, 0)
                .arg(0, Type.NODE_TYPE, OPT | INS, EMPTY);

        register("not", 1, NotFn.class, BuiltInAtomicType.BOOLEAN, ONE, 0)
                .arg(0, Type.ITEM_TYPE, STAR | INS, BooleanValue.TRUE);

        register("normalize-space", 0, ContextItemAccessorFunction.StringAccessor.class, BuiltInAtomicType.STRING, ONE, CITEM | LATE);

        register("normalize-space", 1, NormalizeSpace_1.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null);

        register("normalize-unicode", 1, NormalizeUnicode.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING);

        register("normalize-unicode", 2, NormalizeUnicode.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("number", 0, ContextItemAccessorFunction.Number_0.class, BuiltInAtomicType.DOUBLE, ONE, CITEM | LATE);

        register("number", 1, Number_1.class, BuiltInAtomicType.DOUBLE, ONE, 0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, OPT, DoubleValue.NaN);

        register("one-or-more", 1, TreatFn.OneOrMore.class, Type.ITEM_TYPE, PLUS, AS_ARG0 | FILTER)
                .arg(0, Type.ITEM_TYPE, STAR | TRA, null);

        register("position", 0, PositionAndLast.Position.class, BuiltInAtomicType.INTEGER, ONE, POSN | LATE);

        register("prefix-from-QName", 1, AccessorFn.PrefixFromQName.class, BuiltInAtomicType.NCNAME, OPT, 0)
                .arg(0, BuiltInAtomicType.QNAME, OPT, EMPTY);

        register("QName", 2, QNameFn.class, BuiltInAtomicType.QNAME, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("remove", 2, Remove.class, Type.ITEM_TYPE, STAR, AS_ARG0 | FILTER)
                .arg(0, Type.ITEM_TYPE, STAR | TRA, EMPTY)
                .arg(1, BuiltInAtomicType.INTEGER, ONE, null);

        register("replace", 3, RegexFunctionSansFlags.class, BuiltInAtomicType.STRING,
                 ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("replace", 4, Replace.class, BuiltInAtomicType.STRING,
                 ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null)
                .arg(3, BuiltInAtomicType.STRING, ONE, null);

        register("resolve-QName", 2, ResolveQName.class, BuiltInAtomicType.QNAME, OPT, CARD0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY)
                .arg(1, NodeKindTest.ELEMENT, ONE | INS, null);

        register("resolve-uri", 1, ResolveURI.class, BuiltInAtomicType.ANY_URI, OPT, CARD0 | BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null);

        register("resolve-uri", 2, ResolveURI.class, BuiltInAtomicType.ANY_URI, OPT, CARD0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("reverse", 1, Reverse.class, Type.ITEM_TYPE, STAR, AS_ARG0 | FILTER)
                .arg(0, Type.ITEM_TYPE, STAR | NAV, EMPTY);

        register("root", 0, ContextItemAccessorFunction.class, Type.NODE_TYPE, ONE, CITEM | LATE);

        register("root", 1, Root_1.class, Type.NODE_TYPE, OPT, CARD0)
                .arg(0, Type.NODE_TYPE, OPT | NAV, EMPTY);

        register("round", 1, Round.class, NumericType.getInstance(), OPT, AS_PRIM_ARG0)
                .arg(0, NumericType.getInstance(), OPT, EMPTY);

        register("round-half-to-even", 1, RoundHalfToEven.class, NumericType.getInstance(), OPT, AS_PRIM_ARG0)
                .arg(0, NumericType.getInstance(), OPT, EMPTY);

        register("round-half-to-even", 2, RoundHalfToEven.class, NumericType.getInstance(), OPT, AS_PRIM_ARG0)
                .arg(0, NumericType.getInstance(), OPT, EMPTY)
                .arg(1, BuiltInAtomicType.INTEGER, ONE, null);

        register("round-half-up", 1, RoundHalfUp.class, NumericType.getInstance(), OPT, AS_PRIM_ARG0)
                .arg(0, NumericType.getInstance(), OPT, EMPTY);

        register("round-half-up", 2, RoundHalfUp.class, NumericType.getInstance(), OPT, AS_PRIM_ARG0)
                .arg(0, NumericType.getInstance(), OPT, EMPTY)
                .arg(1, BuiltInAtomicType.INTEGER, ONE, null);

        register("seconds-from-dateTime", 1, AccessorFn.SecondsFromDateTime.class, BuiltInAtomicType.DECIMAL, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY);

        register("seconds-from-duration", 1, AccessorFn.SecondsFromDuration.class, BuiltInAtomicType.DECIMAL, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DURATION, OPT, EMPTY);

        register("seconds-from-time", 1, AccessorFn.SecondsFromTime.class, BuiltInAtomicType.DECIMAL, OPT, CARD0)
                .arg(0, BuiltInAtomicType.TIME, OPT, EMPTY);

        register("starts-with", 2, StartsWith.class, BuiltInAtomicType.BOOLEAN, ONE, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, BooleanValue.TRUE);

        register("starts-with", 3, CollatingFunctionFree.class, BuiltInAtomicType.BOOLEAN, ONE, BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, BooleanValue.TRUE)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("static-base-uri", 0, StaticBaseUri.class, BuiltInAtomicType.ANY_URI, OPT, BASE | LATE);

        register("string", 0, ContextItemAccessorFunction.class, BuiltInAtomicType.STRING, ONE, CITEM | LATE);

        register("string", 1, String_1.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, Type.ITEM_TYPE, OPT | ABS, StringValue.EMPTY_STRING);

        register("string-length", 0, ContextItemAccessorFunction.StringAccessor.class, BuiltInAtomicType.INTEGER, ONE, CITEM | LATE);

        register("string-length", 1, StringLength_1.class, BuiltInAtomicType.INTEGER, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, null);

        // Use the 3.0 function signature even if we're running 2.0
        register("string-join", 2, StringJoin.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, StringValue.EMPTY_STRING)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("string-to-codepoints", 1, StringToCodepoints.class, BuiltInAtomicType.INTEGER, STAR, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY);

        register("subsequence", 2, Subsequence_2.class, Type.ITEM_TYPE, STAR, AS_ARG0 | FILTER)
                .arg(0, Type.ITEM_TYPE, STAR | TRA, EMPTY)
                .arg(1, NumericType.getInstance(), ONE, null);

        register("subsequence", 3, Subsequence_3.class, Type.ITEM_TYPE, STAR, AS_ARG0 | FILTER)
                .arg(0, Type.ITEM_TYPE, STAR | TRA, EMPTY)
                .arg(1, NumericType.getInstance(), ONE, null)
                .arg(2, NumericType.getInstance(), ONE, null);

        register("substring", 2, Substring.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING)
                .arg(1, NumericType.getInstance(), ONE, null);

        register("substring", 3, Substring.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING)
                .arg(1, NumericType.getInstance(), ONE, null)
                .arg(2, NumericType.getInstance(), ONE, null);

        register("substring-after", 2, SubstringAfter.class, BuiltInAtomicType.STRING, ONE, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, null);

        register("substring-after", 3, CollatingFunctionFree.class, BuiltInAtomicType.STRING, ONE, BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("substring-before", 2, SubstringBefore.class, BuiltInAtomicType.STRING, ONE, DCOLL)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING);

        register("substring-before", 3, CollatingFunctionFree.class, BuiltInAtomicType.STRING, ONE, BASE)
                .arg(0, BuiltInAtomicType.STRING, OPT, null)
                .arg(1, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("sum", 1, Sum.class, BuiltInAtomicType.ANY_ATOMIC, ONE, UO)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, null);

        register("sum", 2, Sum.class, BuiltInAtomicType.ANY_ATOMIC, OPT, UO)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, STAR, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, OPT, null);

        register("timezone-from-date", 1, AccessorFn.TimezoneFromDate.class, BuiltInAtomicType.DAY_TIME_DURATION, OPT, 0)
                .arg(0, BuiltInAtomicType.DATE, OPT, EMPTY);

        register("timezone-from-dateTime", 1, AccessorFn.TimezoneFromDateTime.class,
                 BuiltInAtomicType.DAY_TIME_DURATION, OPT, 0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY);

        register("timezone-from-time", 1, AccessorFn.TimezoneFromTime.class,
                 BuiltInAtomicType.DAY_TIME_DURATION, OPT, 0)
                .arg(0, BuiltInAtomicType.TIME, OPT, EMPTY);

        register("tokenize", 2, RegexFunctionSansFlags.class, BuiltInAtomicType.STRING, STAR, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("tokenize", 3, Tokenize_3.class, BuiltInAtomicType.STRING, STAR, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("trace", 2, Trace.class, Type.ITEM_TYPE, STAR, AS_ARG0 | LATE)
                .arg(0, Type.ITEM_TYPE, STAR | TRA, null)
                .arg(1, BuiltInAtomicType.STRING, ONE, null);

        register("translate", 3, Translate.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING)
                .arg(1, BuiltInAtomicType.STRING, ONE, null)
                .arg(2, BuiltInAtomicType.STRING, ONE, null);

        register("true", 0, ConstantFunction.True.class, BuiltInAtomicType.BOOLEAN, ONE, 0);

        register("unordered", 1, Unordered.class, Type.ITEM_TYPE, STAR, AS_ARG0 | FILTER | UO)
                .arg(0, Type.ITEM_TYPE, STAR | TRA, EMPTY);

        register("upper-case", 1, UpperCase.class, BuiltInAtomicType.STRING, ONE, 0)
                .arg(0, BuiltInAtomicType.STRING, OPT, StringValue.EMPTY_STRING);

        register("year-from-date", 1, AccessorFn.YearFromDate.class,
                 BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE, OPT, EMPTY);

        register("year-from-dateTime", 1, AccessorFn.YearFromDateTime.class,
                 BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DATE_TIME, OPT, EMPTY);

        register("years-from-duration", 1, AccessorFn.YearsFromDuration.class,
                 BuiltInAtomicType.INTEGER, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DURATION, OPT, EMPTY);

        register("zero-or-one", 1, TreatFn.ZeroOrOne.class, Type.ITEM_TYPE, OPT, AS_ARG0 | FILTER)
                .arg(0, Type.ITEM_TYPE, STAR | TRA, null);
    }

}
