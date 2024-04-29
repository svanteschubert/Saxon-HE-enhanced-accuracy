////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.functions.CallableFunction;
import net.sf.saxon.functions.InsertBefore;
import net.sf.saxon.functions.OptionsParameter;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.functions.hof.FunctionLiteral;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.arrays.SimpleArrayItem;
import net.sf.saxon.ma.zeno.ZenoSequence;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Function signatures (and pointers to implementations) of the functions defined in the map
 * namespace in XPath 3.1
 */

public class MapFunctionSet extends BuiltInFunctionSet {

    private final static MapFunctionSet instance31 = new MapFunctionSet(31);
    private final static MapFunctionSet instance40 = new MapFunctionSet(40);

    private MapFunctionSet(int version) {
        init(version);
    }

    /**
     * Get the set of functions defined in the F&amp;O spec in the "map" namespace
     * @param version the XPath version (eg 31, 40). Currently any version less than 40
     *                is treated as 31, and any version greater than 40 is treated as 40.
     * @return the function library
     */

    public static MapFunctionSet getInstance(int version) {
        return version >= 40 ? instance40 : instance31;
    }



    private void init(int version) {

        register("merge", 1, e -> e.populate( MapMerge::new, MapType.ANY_MAP_TYPE, ONE, 0)
                .arg(0, MapType.ANY_MAP_TYPE, STAR | INS, null));

        SpecificFunctionType ON_DUPLICATES_CALLBACK_TYPE = new SpecificFunctionType(
                new SequenceType[]{SequenceType.ANY_SEQUENCE, SequenceType.ANY_SEQUENCE},
                SequenceType.ANY_SEQUENCE
        );

        SequenceType oneOnDuplicatesFunction = SequenceType.makeSequenceType(
                ON_DUPLICATES_CALLBACK_TYPE, StaticProperty.EXACTLY_ONE);

        RecordTest KVP_TYPE_EXTENSIBLE = RecordTest.extensible(
                field("key", SequenceType.SINGLE_ATOMIC, false),
                field("value", SequenceType.ANY_SEQUENCE, false));

        RecordTest KVP_TYPE_INEXTENSIBLE = RecordTest.nonExtensible(
                field("key", SequenceType.SINGLE_ATOMIC, false),
                field("value", SequenceType.ANY_SEQUENCE, false));

        OptionsParameter mergeOptionDetails = new OptionsParameter();
        mergeOptionDetails.addAllowedOption("duplicates", SequenceType.SINGLE_STRING, StringValue.bmp("use-first"));
        // duplicates=unspecified is retained because that's what the XSLT 3.0 Rec incorrectly uses
        mergeOptionDetails.setAllowedValues("duplicates", "FOJS0005", "use-first", "use-last", "combine", "reject", "unspecified", "use-any", "use-callback");
        mergeOptionDetails.addAllowedOption(MapMerge.errorCodeKey, SequenceType.SINGLE_STRING, StringValue.bmp("FOJS0003"));
        mergeOptionDetails.addAllowedOption(MapMerge.keyTypeKey, SequenceType.SINGLE_STRING, StringValue.bmp("anyAtomicType"));
        mergeOptionDetails.addAllowedOption(MapMerge.finalKey, SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        mergeOptionDetails.addAllowedOption(MapMerge.onDuplicatesKey, oneOnDuplicatesFunction, null);


        register("merge", 2, e -> e.populate( MapMerge::new, MapType.ANY_MAP_TYPE, ONE, 0)
                .arg(0, MapType.ANY_MAP_TYPE, STAR, null)
                .arg(1, MapType.ANY_MAP_TYPE, ONE, null)
                .setOptionDetails(mergeOptionDetails));

        register("put", 3, e -> e.populate(MapPut::new, MapType.ANY_MAP_TYPE, ONE, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE | ABS, null)
                .arg(2, AnyItemType.getInstance(), STAR | NAV, null));

        register("contains", 2, e -> e.populate(MapContains::new, BuiltInAtomicType.BOOLEAN, ONE, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE | ABS, null));

        register("remove", 2, e -> e.populate(MapRemove::new, MapType.ANY_MAP_TYPE, ONE, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, STAR | ABS, null));

        register("keys", 1, e -> e.populate(MapKeys::new, BuiltInAtomicType.ANY_ATOMIC, STAR, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null));

        register("size", 1, e -> e.populate(MapSize::new, BuiltInAtomicType.INTEGER, ONE, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null));

        register("entry", 2, e -> e.populate( MapEntry::new, MapType.ANY_MAP_TYPE, ONE, 0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, ONE | ABS, null)
                .arg(1, AnyItemType.getInstance(), STAR | NAV, null));

        register("find", 2, e -> e.populate( MapFind::new, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, AnyItemType.getInstance(), STAR | INS, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE | ABS, null));

        ItemType actionType = new SpecificFunctionType(
                new SequenceType[]{SequenceType.SINGLE_ATOMIC, SequenceType.ANY_SEQUENCE},
                SequenceType.ANY_SEQUENCE);

        register("for-each", 2, e -> e.populate(MapForEach::new, AnyItemType.getInstance(), STAR, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null)
                .arg(1, actionType, ONE | INS, null));

        register("get", 2, e -> e.populate( MapGet::new, AnyItemType.getInstance(), STAR, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE | ABS, null));




    }

    @Override
    public NamespaceUri getNamespace() {
        return NamespaceUri.MAP_FUNCTIONS;
    }

    @Override
    public String getConventionalPrefix() {
        return "map";
    }


    /**
     * Implementation of the XPath 3.1 function map:contains(Map, key) =&gt; boolean
     */
    public static class MapContains extends SystemFunction {

        @Override
        public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
            MapItem map = (MapItem) arguments[0].head();
            AtomicValue key = (AtomicValue) arguments[1].head();
            return BooleanValue.get(map.get(key) != null);
        }

    }

    /**
     * Implementation of the proposed XPath 4.0 function map:filter(Map, function(*)) =&gt; Map
     */
    public static class MapFilter extends SystemFunction {

        @Override
        public MapItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            MapItem map = (MapItem) arguments[0].head();
            FunctionItem fn = (FunctionItem) arguments[1].head();
            MapItem result = new HashTrieMap();
            for (KeyValuePair pair : map.keyValuePairs()) {
                BooleanValue match = (BooleanValue)dynamicCall(fn, context, new Sequence[]{pair.key, pair.value}).head();
                if (match.getBooleanValue()) {
                    result = result.addEntry(pair.key, pair.value);
                }
            }
            return result;
        }

    }

    /**
     * Implementation of the XPath 3.1 function map:get(Map, key) =&gt; value
     */
    public static class MapGet extends SystemFunction {

        String pendingWarning = null;

        /**
         * Method called during static type checking. This method may be implemented in subclasses so that functions
         * can take advantage of knowledge of the types of the arguments that will be supplied.
         *
         * @param visitor         an expression visitor, providing access to the static context and configuration
         * @param contextItemType information about whether the context item is set, and what its type is
         * @param arguments       the expressions appearing as arguments in the function call
         */
        @Override
        public void supplyTypeInformation(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType, Expression[] arguments) throws XPathException {
            ItemType it = arguments[0].getItemType();
            if (it instanceof RecordType && arguments.length == 2) {
                if (arguments[1] instanceof StringLiteral) {
                    String key = ((StringLiteral)arguments[1]).stringify();
                    if (((RecordType)it).getFieldType(key) == null) {
                        XPathException xe = new XPathException("Field " + key + " is not defined for tuple type " + it, "SXTT0001");
                        xe.setIsTypeError(true);
                        throw xe;
                    }
                }
                TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
                Affinity relation = th.relationship(arguments[1].getItemType(), BuiltInAtomicType.STRING);
                if (relation == Affinity.DISJOINT) {
                    XPathException xe = new XPathException("Key for tuple type must be a string (actual type is " + arguments[1].getItemType(), "XPTY0004");
                    xe.setIsTypeError(true);
                    throw xe;
                }
            }
        }

        /**
         * Get the return type, given knowledge of the actual arguments
         *
         * @param args the actual arguments supplied
         * @return the best available item type that the function will return
         */
        @Override
        public ItemType getResultItemType(Expression[] args) {
            if (args.length == 2) {
                ItemType mapType = args[0].getItemType();
                if (mapType instanceof RecordTest && args[1] instanceof StringLiteral) {
                    String key = ((StringLiteral) args[1]).stringify();
                    RecordTest tit = (RecordTest) mapType;
                    SequenceType valueType = tit.getFieldType(key);
                    if (valueType == null) {
                        warning("Field " + key + " is not defined in record type");
                        return AnyItemType.getInstance();
                    } else {
                        return valueType.getPrimaryType();
                    }
                } else if (mapType instanceof MapType) {
                    return ((MapType) mapType).getValueType().getPrimaryType();
                }
            }
            return super.getResultItemType(args);
        }

        /**
         * Get the cardinality, given knowledge of the actual arguments
         *
         * @param args the actual arguments supplied
         * @return the most precise available cardinality that the function will return
         */
        @Override
        public int getCardinality(Expression[] args) {
            ItemType mapType = args[0].getItemType();
            if (mapType instanceof RecordTest && args[1] instanceof StringLiteral) {
                String key = ((StringLiteral) args[1]).stringify();
                RecordTest tit = (RecordTest) mapType;
                SequenceType valueType = tit.getFieldType(key);
                if (valueType == null) {
                    warning("Field " + key + " is not defined in record type");
                    return StaticProperty.ALLOWS_MANY;
                } else {
                    return valueType.getCardinality();
                }
            } else if (mapType instanceof MapType) {
                return Cardinality.union(
                        ((MapType) mapType).getValueType().getCardinality(),
                        StaticProperty.ALLOWS_ZERO);
            } else {
                return super.getCardinality(args);
            }
        }

        /**
         * Allow the function to create an optimized call based on the values of the actual arguments
         *
         * @param visitor     the expression visitor
         * @param contextInfo information about the context item
         * @param arguments   the supplied arguments to the function call. Note: modifying the contents
         *                    of this array should not be attempted, it is likely to have no effect.
         * @return either a function call on this function, or an expression that delivers
         * the same result, or null indicating that no optimization has taken place
         * @throws XPathException if an error is detected
         */
        @Override
        public Expression makeOptimizedFunctionCall(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, Expression... arguments) throws XPathException {
            if (pendingWarning != null && !pendingWarning.equals("DONE")) {
                visitor.issueWarning(pendingWarning, SaxonErrorCode.SXWN9038, arguments[0].getLocation());
                pendingWarning = "DONE";
            }
            return null;
        }

        private void warning(String message) {
            if (!"DONE".equals(pendingWarning)) {
                pendingWarning = message;
            }
        }

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            MapItem map = (MapItem) arguments[0].head();
            assert map != null;
            AtomicValue key = (AtomicValue) arguments[1].head();
            Sequence value = map.get(key);
            if (value == null) {
                if (arguments.length > 2) {
                    FunctionItem fn = (FunctionItem)arguments[2].head();
                    return dynamicCall(fn, context, key);
                } else {
                    return EmptySequence.getInstance();
                }
            } else {
                return value;
            }
        }

    }

    /**
     * Implementation of the XPath 3.1 function map:find(item()*, key) =&gt; array
     */
    public static class MapFind extends SystemFunction {

        @Override
        public ArrayItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            List<GroundedValue> result = new ArrayList<>();
            AtomicValue key = (AtomicValue) arguments[1].head();
            processSequence(arguments[0], key, result);
            return new SimpleArrayItem(result);
        }

        private void processSequence(Sequence in, AtomicValue key, List<GroundedValue> result) {
            SequenceTool.supply(in.iterate(), (ItemConsumer<? super Item>) item -> {
                if (item instanceof ArrayItem) {
                    for (Sequence sequence : ((ArrayItem) item).members()) {
                        processSequence(sequence, key, result);
                    }
                } else if (item instanceof MapItem) {
                    GroundedValue value = ((MapItem) item).get(key);
                    if (value != null) {
                        result.add(value);
                    }
                    for (KeyValuePair entry : ((MapItem) item).keyValuePairs()) {
                        processSequence(entry.value, key, result);
                    }
                }
            });
        }

    }

    /**
     * Implementation of the extension function map:entry(key, value) =&gt; Map
     */
    public static class MapEntry extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            AtomicValue key = (AtomicValue) arguments[0].head();
            assert key != null;
            if (arguments[1] instanceof Item) {
                return new SingleEntryMap(key, (Item)arguments[1]);
            }
            GroundedValue value = arguments[1].materialize();
            return new SingleEntryMap(key, value);
        }

        /**
         * Get the return type, given knowledge of the actual arguments
         *
         * @param args the actual arguments supplied
         * @return the best available item type that the function will return
         */
        @Override
        public ItemType getResultItemType(Expression[] args) {
            PlainType ku = args[0].getItemType().getAtomizedItemType();
            AtomicType ka;
            if (ku instanceof AtomicType) {
                ka = (AtomicType)ku;
            } else {
                ka = ku.getPrimitiveItemType();
            }
            return new MapType(ka,
                               SequenceType.makeSequenceType(args[1].getItemType(), args[1].getCardinality()));
        }

        @Override
        public String getStreamerName() {
            return "MapEntry";
        }

    }

    /**
     * Implementation of the function map:for-each(Map, Function) =&gt; item()*
     */
    public static class MapForEach extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            MapItem map = (MapItem) arguments[0].head();
            FunctionItem fn = (FunctionItem) arguments[1].head();
            ZenoSequence results = new ZenoSequence();
            for (KeyValuePair pair : map.keyValuePairs()) {
                Sequence seq = dynamicCall(fn, context, new Sequence[]{pair.key, pair.value});
                GroundedValue val = seq.materialize();
                if (val.getLength() > 0) {
                    results = results.appendSequence(val);
                }
            }
            return results;
        }
    }

    /**
     * Implementation of the proposed 4.0 function map:entries(Map) =&gt; map(*)*
     */
    public static class MapEntries extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            MapItem map = (MapItem) arguments[0].head();
            ZenoSequence results = new ZenoSequence();
            for (KeyValuePair pair : map.keyValuePairs()) {
                SingleEntryMap entry = new SingleEntryMap(pair.key, pair.value);
                results = results.append(entry);
            }
            return results;
        }
    }

    /**
     * Implementation of the proposed 4.0 function map:pair(key, value) =&gt; record(key, value)
     */
    public static class MapPair extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            AtomicValue key = (AtomicValue) arguments[0].head();
            GroundedValue value = arguments[1].materialize();
            DictionaryMap map = new DictionaryMap(2);
            map.initialPut("key", key);
            map.initialPut("value", value);
            return map;
        }
    }

    /**
     * Implementation of the proposed 4.0 function map:pairs(Map) =&gt; record(key, value)*
     */
    public static class MapPairs extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            MapItem map = (MapItem) arguments[0].head();
            ZenoSequence results = new ZenoSequence();
            for (KeyValuePair pair : map.keyValuePairs()) {
                DictionaryMap kvp = new DictionaryMap(2);
                kvp.initialPut("key", pair.key);
                kvp.initialPut("value", pair.value);
                results = results.appendSequence(kvp);
            }
            return results;
        }
    }


    /**
     * Implementation of the proposed XPath 4.0 function
     * map:build($sequence, $key, $value, $combined) as xs:anyAtomicType) =&gt; map(*)
     */
    public static class MapBuild extends SystemFunction {

        @Override
        public Expression makeFunctionCall(Expression... arguments) {
            Expression[] newArgs = new Expression[4];
            newArgs[0] = arguments[0];
            if (arguments.length < 2 || arguments[1] instanceof DefaultedArgumentExpression) {
                newArgs[1] = FunctionLiteral.makeLiteral(SystemFunction.makeFunction40("identity", getRetainedStaticContext(), 1));
            } else {
                newArgs[1] = arguments[1];
            }
            if (arguments.length < 3 || arguments[2] instanceof DefaultedArgumentExpression) {
                newArgs[2] = FunctionLiteral.makeLiteral(SystemFunction.makeFunction40("identity", getRetainedStaticContext(), 1));
            } else {
                newArgs[2] = arguments[2];
            }
            if (arguments.length < 4 || arguments[3] instanceof DefaultedArgumentExpression) {
                newArgs[3] = FunctionLiteral.makeLiteral(
                        new CallableFunction(2,
                                             new CallableDelegate((context, args) -> SequenceExtent.from(
                                                     new InsertBefore.InsertIterator(args[1].iterate(), args[0].iterate(), 1)
                                             )),
                                             new SpecificFunctionType(
                                                     new SequenceType[]{SequenceType.ANY_SEQUENCE, SequenceType.ANY_SEQUENCE},
                                                     SequenceType.ANY_SEQUENCE) {
                                             }));
            } else {
                newArgs[3] = arguments[3];
            }
            setArity(4);
            return super.makeFunctionCall(newArgs);
        }

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            HashTrieMap map = new HashTrieMap();
            FunctionItem keyFunction = (FunctionItem)arguments[1].head();
            FunctionItem valueFunction = (FunctionItem) arguments[2].head();
            FunctionItem combineFunction = (FunctionItem) arguments[3].head();
            SequenceIterator iter = arguments[0].iterate();
            for (Item item; (item = iter.next()) != null; ) {
                AtomicValue key = (AtomicValue)dynamicCall(keyFunction, context, item).head();
                if (key != null) {
                    GroundedValue value = dynamicCall(valueFunction, context, item).materialize();
                    GroundedValue existing = map.get(key);
                    if (existing != null) {
                        value = dynamicCall(combineFunction, context, existing, value).materialize();
                    }
                    map.initialPut(key, value);
                }
            }
            return map;
        }
    }

    /**
     * Implementation of the XPath 3.1 function map:keys(Map) =&gt; atomicValue*
     */
    public static class MapKeys extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            MapItem map = (MapItem) arguments[0].head();
            assert map != null;
            if (arguments.length == 1) {
                return SequenceTool.toLazySequence(map.keys());
            } else {
                FunctionItem fn = (FunctionItem) arguments[1].head();
                ZenoSequence results = new ZenoSequence();
                for (KeyValuePair pair : map.keyValuePairs()) {
                    BooleanValue selected = (BooleanValue)fn.call(context, new Sequence[]{pair.value}).head();
                    if (selected.getBooleanValue()) {
                        results = results.append(pair.key);
                    }
                }
                return results;
            }
        }
    }

    /**
     * Implementation of the function map:merge() =&gt; Map
     * From 9.8, map:merge is also used to implement map constructors in XPath and the xsl:map
     * instruction in XSLT. For this purpose it accepts an additional option to define the error
     * code to be used to signal duplicates.
     */
    public static class MapMerge extends SystemFunction {

        public final static String finalKey = "Q{" + NamespaceConstant.SAXON + "}final";
        public final static String keyTypeKey ="Q{"+NamespaceConstant.SAXON +"}key-type";
        public final static String onDuplicatesKey = "Q{" + NamespaceConstant.SAXON + "}on-duplicates";
        public final static String errorCodeKey = "Q{" + NamespaceConstant.SAXON + "}duplicates-error-code";

        private String duplicates = "use-first";
        private String duplicatesErrorCode = "FOJS0003";
        private FunctionItem onDuplicates = null;
        private boolean allStringKeys = false;
        private boolean treatAsFinal = false;

        /**
         * Allow the function to create an optimized call based on the values of the actual arguments
         *
         * @param visitor     the expression visitor
         * @param contextInfo information about the context item
         * @param arguments   the supplied arguments to the function call. Note: modifying the contents
         *                    of this array should not be attempted, it is likely to have no effect.
         * @return either a function call on this function, or an expression that delivers
         * the same result, or null indicating that no optimization has taken place
         * @throws XPathException if an error is detected
         */
        @Override
        public Expression makeOptimizedFunctionCall(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, Expression... arguments) throws XPathException {
            if (arguments.length == 2 && arguments[1] instanceof Literal) {
                MapItem options = (MapItem) ((Literal)arguments[1]).getGroundedValue().head();
                Map<String, GroundedValue> values = getDetails().optionDetails.processSuppliedOptions(
                        options, visitor.getStaticContext().makeEarlyEvaluationContext());
                String duplicates = ((StringValue) values.get("duplicates")).getStringValue();
                String duplicatesErrorCode = ((StringValue) values.get(errorCodeKey)).getStringValue();
                FunctionItem onDuplicates = (FunctionItem)values.get(onDuplicatesKey);
                if (onDuplicates != null) {
                    duplicates = "use-callback";
                }

                boolean isFinal = ((BooleanValue) values.get(finalKey)).getBooleanValue();
                String keyType = ((StringValue) values.get(keyTypeKey)).getStringValue();
                MapMerge mm2 = (MapMerge)instance31.makeFunction("merge", 1);
                mm2.duplicates = duplicates;
                mm2.duplicatesErrorCode = duplicatesErrorCode;
                mm2.onDuplicates = onDuplicates;
                mm2.allStringKeys = keyType.equals("string");
                mm2.treatAsFinal = isFinal;
                return mm2.makeFunctionCall(arguments[0]);
            }
            return super.makeOptimizedFunctionCall(visitor, contextInfo, arguments);
        }

        /**
         * Get the return type, given knowledge of the actual arguments
         *
         * @param args the actual arguments supplied
         * @return the best available item type that the function will return
         */
        @Override
        public ItemType getResultItemType(Expression[] args) {
            ItemType it = args[0].getItemType();
            if (it == ErrorType.getInstance()) {
                return MapType.EMPTY_MAP_TYPE;
            } else if (it instanceof MapType) {
                boolean maybeCombined = true;  // see bug 3980
                if (args.length == 1) {
                    maybeCombined = false;
                } else if (args[1] instanceof Literal) {
                    MapItem options = (MapItem) ((Literal) args[1]).getGroundedValue().head();
                    GroundedValue dupes = options.get(StringValue.bmp("duplicates"));
                    try {
                        if (dupes != null && !"combine".equals(dupes.getStringValue())) {
                            maybeCombined = false;
                        }
                    } catch (XPathException e) {
                        //
                    }
                }
                if (maybeCombined) {
                    return new MapType(((MapType) it).getKeyType(),
                                       SequenceType.makeSequenceType(((MapType) it).getValueType().getPrimaryType(), StaticProperty.ALLOWS_ZERO_OR_MORE));
                } else {
                    return it;
                }
            } else {
                return super.getResultItemType(args);
            }
        }

        @Override
        public MapItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            try {
                String duplicates = this.duplicates;
                String duplicatesErrorCode = this.duplicatesErrorCode;
                boolean allStringKeys = this.allStringKeys;
                boolean treatAsFinal = this.treatAsFinal;
                FunctionItem onDuplicates = this.onDuplicates;
                if (arguments.length > 1) {
                    MapItem options = (MapItem) arguments[1].head();
                    Map<String, GroundedValue> values = getDetails().optionDetails.processSuppliedOptions(options, context);
                    duplicates = ((StringValue) values.get("duplicates")).getStringValue();
                    duplicatesErrorCode = ((StringValue) values.get(errorCodeKey)).getStringValue();
                    treatAsFinal = ((BooleanValue) values.get(finalKey)).getBooleanValue();
                    allStringKeys = "string".equals(((StringValue)values.get(keyTypeKey)).getStringValue());
                    onDuplicates = (FunctionItem) values.get(onDuplicatesKey);
                    if (onDuplicates != null) {
                        duplicates = "use-callback";
                    }
                }

                if (treatAsFinal && allStringKeys) {
                    // Optimize for a map with string-valued keys that's unlikely to be modified
                    SequenceIterator iter = arguments[0].iterate();
                    DictionaryMap baseMap = new DictionaryMap();
                    MapItem next;
                    switch (duplicates) {
                        // Code is structured (a) to avoid testing "duplicates" within the loop unnecessarily,
                        // and (b) to avoid the "get" operation to look for duplicates when it's not needed.
                        case "unspecified":
                        case "use-any":
                        case "use-last":
                            while ((next = (MapItem) iter.next()) != null) {
                                for (KeyValuePair pair : next.keyValuePairs()) {
                                    if (!(pair.key instanceof StringValue)) {
                                        throw new XPathException("The keys in this map must all be strings (found " + pair.key.getItemType() + ")");
                                    }
                                    baseMap.initialPut(pair.key.getStringValue(), pair.value);
                                }
                            }
                            return baseMap;
                        default:
                            while ((next = (MapItem) iter.next()) != null) {
                                for (KeyValuePair pair : next.keyValuePairs()) {
                                    if (!(pair.key instanceof StringValue)) {
                                        throw new XPathException("The keys in this map must all be strings (found " + pair.key.getItemType() + ")");
                                    }
                                    Sequence existing = baseMap.get(pair.key);
                                    if (existing != null) {
                                        switch (duplicates) {
                                            case "use-first":
                                                // no action
                                                break;
                                            case "combine":
                                                InsertBefore.InsertIterator combinedIter =
                                                        new InsertBefore.InsertIterator(pair.value.iterate(), existing.iterate(), 1);
                                                GroundedValue combinedValue = SequenceTool.toGroundedValue(combinedIter);
                                                baseMap.initialPut(pair.key.getStringValue(), combinedValue);
                                                break;
                                            case "use-callback":
                                                Sequence[] args = new Sequence[]{existing, pair.value};
                                                Sequence combined = onDuplicates.call(context, args);
                                                baseMap.initialPut(pair.key.getStringValue(), combined.materialize());
                                                break;
                                            default:
                                                throw new XPathException("Duplicate key in constructed map: " +
                                                                                 Err.wrap(pair.key.getStringValue()), duplicatesErrorCode);
                                        }
                                    } else {
                                        baseMap.initialPut(pair.key.getStringValue(), pair.value);
                                    }
                                }
                            }
                            return baseMap;
                    }
                } else {
                    SequenceIterator iter = arguments[0].iterate();
                    return mergeMaps(iter, context, duplicates, duplicatesErrorCode, onDuplicates);
                }
            } catch (UncheckedXPathException e) {
                throw e.getXPathException();
            }

        }

        /**
         * Merge a sequence of maps into a single map
         * @param iter iterator over the input maps
         * @param context The XPath dynamic context
         * @param duplicates action to be taken when duplicate keys are encountered
         * @param duplicatesErrorCode if duplicates are not allowed, the error code to be used
         * @param onDuplicates callback to be used when duplicates = "use-callback"
         * @return the merged map
         * @throws XPathException if any error occurs, including detection of disallowed duplicates
         */
        public static MapItem mergeMaps(SequenceIterator iter, XPathContext context,
                                        String duplicates, String duplicatesErrorCode, FunctionItem onDuplicates)
                throws XPathException {
            MapItem baseMap = (MapItem) iter.next();
            if (baseMap == null) {
                return new HashTrieMap();
            } else {
                MapItem next;
                while ((next = (MapItem) iter.next()) != null) {
                    // Merge the next map and the base map. Merge the smaller of the two
                    // maps into the larger. The complication is that this affects duplicates handling.
                    // See bug #4865
                    boolean inverse = next.size() > baseMap.size();
                    MapItem larger = inverse ? next : baseMap;
                    MapItem smaller = inverse ? baseMap : next;
                    String dup = inverse ? invertDuplicates(duplicates) : duplicates;
                    for (KeyValuePair pair : smaller.keyValuePairs()) {
                        Sequence existing = larger.get(pair.key);
                        if (existing != null) {
                            switch (dup) {
                                case "use-first":
                                case "unspecified":
                                case "use-any":
                                    // no action
                                    break;
                                case "use-last":
                                    larger = larger.addEntry(pair.key, pair.value);
                                    break;
                                case "combine": {
                                    InsertBefore.InsertIterator combinedIter =
                                            new InsertBefore.InsertIterator(pair.value.iterate(), existing.iterate(), 1);
                                    try {
                                        GroundedValue combinedValue = SequenceTool.toGroundedValue(combinedIter);
                                        larger = larger.addEntry(pair.key, combinedValue);
                                    } catch (UncheckedXPathException e) {
                                        throw e.getXPathException();
                                    }
                                    break;
                                }
                                case "combine-reverse": {
                                    InsertBefore.InsertIterator combinedIter =
                                            new InsertBefore.InsertIterator(existing.iterate(), pair.value.iterate(), 1);
                                    try {
                                        GroundedValue combinedValue = SequenceTool.toGroundedValue(combinedIter);
                                        larger = larger.addEntry(pair.key, combinedValue);
                                    } catch (UncheckedXPathException e) {
                                        throw e.getXPathException();
                                    }
                                    break;
                                }
                                case "use-callback":
                                    assert onDuplicates != null;
                                    Sequence[] args;
                                    if (inverse) {
                                        args = onDuplicates.getArity() == 2 ?
                                                new Sequence[]{pair.value, existing} :
                                                new Sequence[]{pair.value, existing, pair.key};
                                    } else {
                                        args = onDuplicates.getArity() == 2 ?
                                                new Sequence[]{existing, pair.value} :
                                                new Sequence[]{existing, pair.value, pair.key};
                                    }
                                    Sequence combined = onDuplicates.call(context, args);
                                    larger = larger.addEntry(pair.key, combined.materialize());
                                    break;
                                default:
                                    throw new XPathException("Duplicate key in constructed map: " +
                                                                     Err.wrap(pair.key.getStringValue()), duplicatesErrorCode);
                            }
                        } else {
                            larger = larger.addEntry(pair.key, pair.value);
                        }
                    }
                    baseMap = larger;
                }
                return baseMap;
            }
        }

        private static String invertDuplicates(String duplicates) {
            switch (duplicates) {
                case "use-first":
                case "unspecified":
                case "use-any":
                    return "use-last";
                case "use-last":
                    return "use-first";
                case "combine":
                    return "combine-reverse";
                case "combine-reverse":
                    return "combine";
                default:
                    return duplicates;
            }
        }

        @Override
        public String getStreamerName() {
            return "NewMap";
        }

        /**
         * Export any implicit arguments held in optimized form within the SystemFunction call
         *
         * @param out the export destination
         */
        @Override
        public void exportAdditionalArguments(SystemFunctionCall call, ExpressionPresenter out) throws XPathException {
            if (call.getArity() == 1) {
                HashTrieMap options = new HashTrieMap();
                options.initialPut(StringValue.bmp("duplicates"), new StringValue(duplicates));
                options.initialPut(StringValue.bmp("duplicates-error-code"), new StringValue(duplicatesErrorCode));
                Literal.exportValue(options, out);
            }
        }
    }

    /**
     * Implementation of the function map:of-pairs() =&gt; Map
     */
    public static class MapOfPairs extends SystemFunction {


        @Override
        public MapItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            FunctionItem onDuplicates = null;
            if (arguments.length > 1) {
                onDuplicates = (FunctionItem) arguments[1].head();
            }

            StringValue keyKey = new StringValue("key");
            StringValue valueKey = new StringValue("value");
            MapItem result = new HashTrieMap();
            SequenceIterator iter = arguments[0].iterate();
            for (Item item; (item = iter.next()) != null; ) {
                AtomicValue key = (AtomicValue) ((MapItem) item).get(keyKey);
                GroundedValue suppliedValue = ((MapItem) item).get(valueKey);
                GroundedValue existingValue = result.get(key);
                if (existingValue != null) {
                    if (onDuplicates == null) {
                        GroundedValue newValue = existingValue.concatenate(suppliedValue);
                        result = result.addEntry(key, newValue);
                    } else {
                        GroundedValue newValue =
                                onDuplicates.call(context, new Sequence[]{existingValue, suppliedValue}).materialize();
                        result = result.addEntry(key, newValue);
                    }
                } else {
                    result = result.addEntry(key, suppliedValue);
                }
            }
            return result;
        }
    }


   /**
     * Implementation of the extension function map:put() =&gt; Map
     */

    public static class MapPut extends SystemFunction {

        @Override
        public MapItem call(XPathContext context, Sequence[] arguments) throws XPathException {

            MapItem baseMap = (MapItem) arguments[0].head();

            if (!(baseMap instanceof HashTrieMap)) {
                baseMap = HashTrieMap.copy(baseMap);
            }

            AtomicValue key = (AtomicValue) arguments[1].head();
            GroundedValue value = arguments[2].materialize();
            return baseMap.addEntry(key, value);
        }
    }


    /**
     * Implementation of the XPath 3.1 function map:remove(Map, key) =&gt; value
     */
    public static class MapRemove extends SystemFunction {

        @Override
        public MapItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            MapItem map = (MapItem) arguments[0].head();
            SequenceIterator iter = arguments[1].iterate();
            AtomicValue key;
            while ((key = (AtomicValue) iter.next()) != null) {
                map = map.remove(key);
            }
            return map;
        }

    }

    /**
     * Implementation of the extension function map:size(map) =&gt; integer
     */
    public static class MapSize extends SystemFunction {

        @Override
        public IntegerValue call(XPathContext context, Sequence[] arguments) throws XPathException {
            MapItem map = (MapItem) arguments[0].head();
            return new Int64Value(map.size());
        }
    }

}
