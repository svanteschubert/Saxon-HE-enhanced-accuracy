/// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
/// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;


import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.functions.ArityTwoFunction;
import net.sf.saxon.functions.OptionsParameter;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.functions.hof.FunctionLiteral;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.arrays.SimpleArrayItem;
import net.sf.saxon.ma.zeno.ZenoSequence;
import net.sf.saxon.om.*;
import net.sf.saxon.str.Twine8;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import java.util.*;

/**
 * Function signatures (and pointers to implementations) of the functions defined in the map
 * namespace in XPath 3.1 and 4.0
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

    public static final RecordType KVP_TYPE_INEXTENSIBLE = RecordType.nonExtensible(
            field("key", SequenceType.SINGLE_ATOMIC, false),
            field("value", SequenceType.ANY_SEQUENCE, false));

    public static final RecordType KVP_TYPE_EXTENSIBLE = RecordType.extensible(
            field("key", SequenceType.SINGLE_ATOMIC, false),
            field("value", SequenceType.ANY_SEQUENCE, false));

    public static OnDuplicatesAction getDuplicatesCombiner(
            Map<String, GroundedValue> options, String defaultAction, String duplicatesErrorCode) throws XPathException {
        GroundedValue duplicatesOption = options.get("duplicates");
        if (duplicatesOption == null) {
            duplicatesOption = new StringValue(defaultAction);
        } else {
            duplicatesOption = duplicatesOption.head();
            if (duplicatesOption == null) {
                duplicatesOption = new StringValue(defaultAction);
            }
        }
        if (duplicatesOption instanceof StringValue) {
            String action = duplicatesOption.getStringValue();
            return switch (action) {
                case "use-first", "use-any", "unspecified" -> // used in XSLT 3.0
                        (x, y, cxt) -> x;
                case "use-last" -> (x, y, cxt) -> y;
                case "combine" -> (x, y, cxt) -> x.concatenate(y);
                case "reject" -> (x, y, cxt) -> {
                    throw new UncheckedXPathException("Duplicate key in map", duplicatesErrorCode);
                };
                default -> throw new AssertionError();
            };
        }
        FunctionItem combineOption = (FunctionItem) duplicatesOption;
        return (x, y, cxt) -> {
            try {
                return combineOption.call(cxt, new Sequence[]{x, y}).materialize();
            } catch (XPathException e) {
                throw new UncheckedXPathException(e);
            }
        };


    }


    private void init(int version) {

        register("merge", 1, e -> e.populate(() -> new MapMerge(version), MapType.ANY_MAP_TYPE, ONE, 0)
                .arg(0, MapType.ANY_MAP_TYPE, STAR | INS, null));

        SpecificFunctionType ON_DUPLICATES_CALLBACK_TYPE = new SpecificFunctionType(
                SequenceType.ANY_SEQUENCE, SequenceType.ANY_SEQUENCE,
                SequenceType.ANY_SEQUENCE
        );

        EnumerationUnionType duplicatesKeywords =
                EnumerationUnionType.of("reject", "use-first", "use-last", "use-any", "combine", "unspecified");
        // duplicates=unspecified is retained because that's what the XSLT 3.0 Rec incorrectly uses

        SequenceType duplicatesOptionType = SequenceType.one(
                ChoiceItemType.of(ON_DUPLICATES_CALLBACK_TYPE, duplicatesKeywords));


        OptionsParameter mergeOptionDetails = new OptionsParameter(version);
        mergeOptionDetails.addAllowedOption("duplicates", duplicatesOptionType, StringValue.bmp("use-first"));
        // duplicates=unspecified is retained because that's what the XSLT 3.0 Rec incorrectly uses
        //mergeOptionDetails.addAllowedOption("retain-order", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);


        register("merge", 2, e -> e.populate(() -> new MapMerge(version), MapType.ANY_MAP_TYPE, ONE, 0)
                .arg(0, MapType.ANY_MAP_TYPE, STAR, null)
                .arg(1, MapType.ANY_MAP_TYPE, version >= 40 ? OPT : ONE, null)
                .setOptionDetails(mergeOptionDetails));

        register("put", 3, e ->
                e.populate(MapPut::new, MapType.ANY_MAP_TYPE, ONE, 0)
                        .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null)
                        .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE | ABS, null)
                        .arg(2, AnyItemType.INSTANCE, STAR | NAV, null));

        register("contains", 2, e -> e.populate(MapContains::new, BuiltInAtomicType.BOOLEAN, ONE, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE | ABS, null));

        register("remove", 2, e -> e.populate(MapRemove::new, MapType.ANY_MAP_TYPE, ONE, AS_ARG0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, STAR | ABS, null));

        register("keys", 1, e -> e.populate(MapKeys::new, BuiltInAtomicType.ANY_ATOMIC, STAR, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null));

        register("size", 1, e -> e.populate(MapSize::new, BuiltInAtomicType.INTEGER, ONE, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null));

        register("entry", 2, e -> e.populate(() -> new MapEntry(version), MapType.ANY_MAP_TYPE, ONE, 0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, ONE | ABS, null)
                .arg(1, AnyItemType.INSTANCE, STAR | NAV, null));

        register("find", 2, e -> e.populate(MapFind::new, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, AnyItemType.INSTANCE, STAR | INS, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE | ABS, null));

        ItemType actionType31 = new SpecificFunctionType(
                SequenceType.SINGLE_ATOMIC,
                SequenceType.ANY_SEQUENCE,
                SequenceType.ANY_SEQUENCE);

        ItemType actionType40 = new SpecificFunctionType(
                SequenceType.SINGLE_ATOMIC,
                SequenceType.ANY_SEQUENCE,
                SequenceType.SINGLE_INTEGER,
                SequenceType.ANY_SEQUENCE);

        register("for-each", 2, e -> e.populate(MapForEach::new, AnyItemType.INSTANCE, STAR, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null)
                .arg(1, version >= 40 ? actionType40 : actionType31, ONE | INS, null));

        register("get", 2, e -> e.populate(MapGet::new, AnyItemType.INSTANCE, STAR, 0)
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

    @FunctionalInterface
    public interface OnDuplicatesAction {
        GroundedValue combine(GroundedValue existing, GroundedValue newValue, XPathContext context);
    }


    /**
     * Implementation of the XPath 3.1 function map:contains(Map, key) =&gt; boolean
     */
    public static class MapContains extends SystemFunction implements ArityTwoFunction {

        @Override
        public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
            return call2(context, arguments[0], arguments[1]);
        }

        public BooleanValue call2(XPathContext context, Sequence arg0, Sequence arg1) throws XPathException {
            MapItem map = (MapItem) arg0.head();
            AtomicValue key = (AtomicValue) arg1.head();
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
            GeneralMapBuilder result = FixedMap.getBuilder(map.getSpecVersion());
            int position = 1;
            for (KeyValuePair pair : map.keyValuePairs()) {
                BooleanValue match = (BooleanValue) dynamicCall(
                        fn, context, pair.key(), pair.value(), Int64Value.makeIntegerValue(position++)).head();
                if (match != null && match.getBooleanValue()) {
                    result.put(pair.key(), pair.value());
                }
            }
            return result.getCompletedMapConfidently();
        }

    }

    /**
     * Implementation of the XPath 3.1 function map:get(Map, key) =&gt; value
     */
    public static class MapGet extends SystemFunction implements ArityTwoFunction {

        public MapGet() {
        }

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
                    String key = ((StringLiteral) arguments[1]).stringify();
                    if (((RecordType) it).getFieldType(key) == null) {
                        XPathException xe = new XPathException("Field " + key + " is not defined for record type " + it, "SXTT0001");
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
                if (mapType instanceof RecordType tit && args[1] instanceof StringLiteral) {
                    String key = ((StringLiteral) args[1]).stringify();
                    SequenceType valueType = tit.getFieldType(key);
                    if (valueType == null) {
                        warning("Field " + key + " is not defined in record type");
                        return AnyItemType.INSTANCE;
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
            if (mapType instanceof RecordType tit && args[1] instanceof StringLiteral) {
                String key = ((StringLiteral) args[1]).stringify();
                SequenceType valueType = tit.getFieldType(key);
                if (valueType == null) {
                    warning("Field " + key + " is not defined in record type");
                    return StaticProperty.ALLOWS_MANY;
                } else {
                    return valueType.getCardinality();
                }
            } else if (mapType instanceof MapType && args.length == 2) {
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
                    return arguments[2];
                } else {
                    return EmptySequence.INSTANCE;
                }
            } else {
                return value;
            }
        }

        /**
         * Call a function with two arguments
         *
         * @param context the dynamic evaluation context
         * @param arg0    the first argument
         * @param arg1    the second argument
         * @return the result of the function call
         * @throws XPathException if the call fails with a dynamic error
         */
        @Override
        public Sequence call2(XPathContext context, Sequence arg0, Sequence arg1) throws XPathException {
            MapItem map = (MapItem) arg0.head();
            assert map != null;
            AtomicValue key = (AtomicValue) arg1.head();
            Sequence value = map.get(key);
            return Objects.requireNonNullElse(value, EmptySequence.INSTANCE);
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
                        processSequence(entry.value(), key, result);
                    }
                }
            });
        }

    }

    /**
     * Implementation of the function map:entry(key, value) =&gt; Map
     */
    public static class MapEntry extends SystemFunction {

        private final int version;

        public MapEntry(int version) {
            this.version = version;
        }

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            AtomicValue key = (AtomicValue) arguments[0].head();
            assert key != null;
            GroundedValue value = arguments[1].materialize();
            return new SingleEntryMap(key, value, version);
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
                ka = (AtomicType) ku;
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

        /**
         * Make an elaborator for a system function call on this function
         *
         * @return a suitable elaborator; or null if no custom elaborator is available
         */
        @Override
        public Elaborator getElaborator() {
            return new MapEntryElaborator();
        }

        public static class MapEntryElaborator extends ItemElaborator {
            @Override
            public ItemEvaluator elaborateForItem() {
                SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
                ItemEvaluator keyElab = fnc.getArg(0).makeElaborator().elaborateForItem();
                int card = fnc.getArg(1).getCardinality();
                int version = fnc.getRetainedStaticContext().getPackageData().getHostLanguageVersion();
                if (Cardinality.allowsMany(card)) {
                    SequenceEvaluator valElab = fnc.getArg(1).makeElaborator().eagerly();
                    return cxt -> new SingleEntryMap((AtomicValue) keyElab.eval(cxt),
                            (GroundedValue) valElab.evaluate(cxt),
                            version);
                } else if (Cardinality.allowsZero(card)) {
                    ItemEvaluator valElab = fnc.getArg(1).makeElaborator().elaborateForItem();
                    return cxt -> {
                        GroundedValue val = valElab.eval(cxt);
                        return new SingleEntryMap((AtomicValue) keyElab.eval(cxt),
                                val == null ? EmptySequence.INSTANCE : val,
                                version);
                    };
                } else {
                    ItemEvaluator valElab = fnc.getArg(1).makeElaborator().elaborateForItem();
                    return cxt -> new SingleEntryMap((AtomicValue) keyElab.eval(cxt),
                            valElab.eval(cxt),
                            version);
                }
            }
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
            int position = 1;
            for (KeyValuePair pair : map.keyValuePairs()) {
                Sequence seq = dynamicCall(fn, context, pair.key(), pair.value(), Int64Value.makeIntegerValue(position++));
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
            return new LazySequence(map.entries());
        }
    }

    /**
     * Implementation of the proposed 4.0 function map:empty(Map) =&gt; boolean
     */
    public static class MapEmpty extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            MapItem map = (MapItem) arguments[0].head();
            return BooleanValue.get(map.isEmpty());
        }
    }

    /**
     * Implementation of the proposed 4.0 function map:items(Map) =&gt; item()*
     */
    public static class MapItems extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            MapItem map = (MapItem) arguments[0].head();
            if (map instanceof SingleEntryMap) {
                // Special-case optimization
                return ((SingleEntryMap) map).getValue();
            }
            ZenoSequence results = new ZenoSequence();
            for (KeyValuePair pair : map.keyValuePairs()) {
                results = results.appendSequence(pair.value());
            }
            return results;
        }

    }


    /**
     * Implementation of the proposed XPath 4.0 function
     * map:build($sequence, $key, $value, $options) =&gt; map(*)
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
                newArgs[3] = Literal.makeLiteral(EmptyMap.INSTANCE_40);
            } else {
                newArgs[3] = arguments[3];
            }
            setArity(4);
            return super.makeFunctionCall(newArgs);
        }

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            GeneralMapBuilder mapBuilder = AbstractFixedMap.getBuilder(40);
            FunctionItem keyFunction = (FunctionItem) arguments[1].head();
            FunctionItem valueFunction = (FunctionItem) arguments[2].head();
            MapItem rawOptions = (MapItem) arguments[3].head();
            if (rawOptions == null) {
                rawOptions = EmptyMap.INSTANCE_40;
            }
            int version = getRetainedStaticContext().getPackageData().getHostLanguageVersion();
            Map<String, GroundedValue> cookedOptions = getDetails().optionDetails.processSuppliedOptions(rawOptions, context, version);
            OnDuplicatesAction action = getDuplicatesCombiner(cookedOptions, "combine", "FOJS0003");
            mapBuilder.setDuplicatesAction(action);

            SequenceIterator iter = arguments[0].iterate();
            int position = 1;
            for (Item item; (item = iter.next()) != null; ) {
                IntegerValue posValue = Int64Value.makeIntegerValue(position++);
                SequenceIterator keys;
                if (keyFunction != null) {
                    keys = dynamicCall(keyFunction, context, item, posValue).iterate();
                } else {
                    keys = SingletonIterator.makeIterator(item);
                }
                AtomicValue key;
                GroundedValue value;
                if (valueFunction != null) {
                    value = dynamicCall(valueFunction, context, item, posValue).materialize();
                } else {
                    value = item;
                }
                while ((key = (AtomicValue) keys.next()) != null) {
                    mapBuilder.put(key, value);
                }
            }
            return mapBuilder.getCompletedMap(context);
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
            if (map instanceof SingleEntryMap) {
                // special-case optimization
                return ((SingleEntryMap) map).getKey();
            }
            return SequenceTool.toLazySequence(map.keys());
        }
    }

    /**
     * Implementation of the function map:merge() =&gt; Map
     * From 9.8, map:merge is also used to implement map constructors in XPath and the xsl:map
     * instruction in XSLT. For this purpose it accepts an additional option to define the error
     * code to be used to signal duplicates.
     */
    public static class MapMerge extends SystemFunction {

        private final int version;

        public MapMerge(int version) {
            this.version = version;
        }


        private final String duplicatesErrorCode = "FOJS0003";


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
                // see bug 3980
                return new MapType(((MapType) it).getKeyType(),
                        SequenceType.zeroOrMore(((MapType) it).getValueType().getPrimaryType()));

            } else {
                return super.getResultItemType(args);
            }
        }


        @Override
        public MapItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            try {
                String duplicatesErrorCode = this.duplicatesErrorCode;
                MapFunctionSet.OnDuplicatesAction action = null;
                if (arguments.length > 1) {
                    MapItem options = (MapItem) arguments[1].head();
                    if (options != null && !options.isEmpty()) {
                        int version = getRetainedStaticContext().getPackageData().getHostLanguageVersion();
                        Map<String, GroundedValue> values = getDetails().optionDetails.processSuppliedOptions(options, context, version);
                        action = getDuplicatesCombiner(values, "use-first", duplicatesErrorCode);
                    }
                }
                if (action == null) {
                    Map<String, GroundedValue> values = new HashMap<>();
                    action = getDuplicatesCombiner(values, "use-first", "FOJS0003");
                }

                SequenceIterator iter = arguments[0].iterate();
                return mergeMaps(iter, context, action, version);

            } catch (UncheckedXPathException e) {
                throw e.getXPathException();
            }

        }

        /**
         * Merge a sequence of maps into a single map
         *
         * @param iter         iterator over the input maps
         * @param context      The XPath dynamic context
         * @param onDuplicates callback to be used when duplicates = "use-callback"
         * @return the merged map
         * @throws XPathException if any error occurs, including detection of disallowed duplicates
         */
        public static MapItem mergeMaps(SequenceIterator iter, XPathContext context,
                                        OnDuplicatesAction onDuplicates, int version)
                throws XPathException {
            MapItem firstMap = (MapItem) iter.next();
            if (firstMap == null) {
                return EmptyMap.getInstance(version);
            }

            // Special case where there is only one map. This happens with a single-entry map constructor
            // such as {"validate":false()}.

            if (iter instanceof SingletonIterator) {
                return firstMap;
            }

            // If the first map is extensible, or if it is large, then we merge the second and subsequent
            // maps into the first using incremental put operations, checking each entry first to see if
            // it is a duplicate. This path is important for performance when a map is constructed incrementally
            // using successive calls on map:merge; it avoids copying the contents of the first map, which
            // can lead to quadratic performance.

            MapItem nextMap;
            if (firstMap instanceof ExtensibleMap || firstMap instanceof DeltaMap || firstMap.size() > 50) {
                while ((nextMap = (MapItem) iter.next()) != null) {

                    for (KeyValuePair kvp : nextMap.keyValuePairs()) {
                        AtomicValue keyi = kvp.key();
                        GroundedValue existing = firstMap.get(keyi);

                        if (existing == null) {
                            firstMap = firstMap.put(keyi, kvp.value());
                        } else if (onDuplicates == null) {
                            throw new XPathException("Duplicate key " + Err.depict(keyi) + " in map", "FOJS0003");
                        } else {
                            GroundedValue newVal;
                            try {
                                newVal = onDuplicates.combine(existing, kvp.value(), context);
                            } catch (UncheckedXPathException e) {
                                throw new XPathException("Duplicate key " + Err.depict(keyi)
                                        + " in map. First value: "
                                        + Err.depictSequence(existing) + "; second value: "
                                        + Err.depictSequence(kvp.value()), e).withErrorCode(e.getXPathException().getErrorCodeQName());
                            }
                            firstMap = firstMap.put(keyi, newVal);
                        }
                    }

                }
                return firstMap;
            }
            // If the first map is small, or inextensible, then we copy all maps to a new MapBuilder, and construct
            // a new map from the builder. This path is taken for a map:merge call produced by compiling a map constructor
            // such as {"a":1, "b":2}.
            GeneralMapBuilder builder = FixedMap.getBuilder(version);
            builder.setDuplicatesAction(onDuplicates);

            for (KeyValuePair kvp : firstMap.keyValuePairs()) {
                builder.put(kvp.key(), kvp.value());
            }

            while ((nextMap = (MapItem) iter.next()) != null) {
                for (KeyValuePair kvp : nextMap.keyValuePairs()) {
                    builder.put(kvp.key(), kvp.value());
                }
            }
            return builder.getCompletedMap(context);
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
                StringMapBuilder options = new StringMapBuilder(2);
                String duplicates = "use-first";
                options.put(new Twine8("duplicates"), new StringValue(duplicates));
                Literal.exportValue(options.getCompletedMap(), out);
            }
        }

        @Override
        public Elaborator getElaborator() {
            return new MapMerge.MapMergeElaborator();
        }

        private static class MapMergeElaborator extends ItemElaborator {

            @Override
            public ItemEvaluator elaborateForItem() {
                SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
                int arity = fnc.getArity();
                PullEvaluator input = fnc.getArg(0).makeElaborator().elaborateForPull();
                if (arity == 2) {
                    if (fnc.getArg(1) instanceof Literal optionsArg) {
                        // options are known statically
                        try {
                            GroundedValue optionsValue = optionsArg.getGroundedValue();
                            MapItem rawOptions;
                            if (optionsValue.getLength() == 0) {
                                rawOptions = EmptyMap.INSTANCE_40;
                            } else {
                                rawOptions = (MapItem) optionsArg.getGroundedValue();
                            }
                            int version = rawOptions.getSpecVersion();
                            XPathContext context = new EarlyEvaluationContext(getConfiguration());
                            Map<String, GroundedValue> cookedOptions = fnc.getTargetFunction().getDetails().optionDetails.processSuppliedOptions(rawOptions, context, version);
                            String duplicatesErrorCode = "FOJS0003";
                            final OnDuplicatesAction dupAction = getDuplicatesCombiner(cookedOptions, "use-first", duplicatesErrorCode);
                            return cxt -> {
                                SequenceIterator maps = input.iterate(cxt);
                                return mergeMaps(maps, cxt, dupAction, version);
                            };
                        } catch (XPathException e) {
                            throw new UncheckedXPathException(e);
                        }
                    } else {
                        // options are computed dynamically
                        ItemEvaluator optionsEval = fnc.getArg(1).makeElaborator().elaborateForItem();
                        OptionsParameter details = fnc.getTargetFunction().getDetails().optionDetails;
                        int version = fnc.getRetainedStaticContext().getPackageData().getHostLanguageVersion();
                        return cxt -> {
                            SequenceIterator maps = input.iterate(cxt);
                            MapItem options = (MapItem) optionsEval.eval(cxt);
                            if (options == null) {
                                options = EmptyMap.INSTANCE_40;
                            }
                            Map<String, GroundedValue> cookedOptions = details.processSuppliedOptions(options, cxt, version);
                            OnDuplicatesAction action = getDuplicatesCombiner(cookedOptions, "use-first", "FOJS0003");
                            return mergeMaps(maps, cxt, action, version);
                        };
                    }
                } else {
                    // options are defaulted
                    OnDuplicatesAction action = (x, y, cxt) -> x;
                    return cxt -> {
                        SequenceIterator maps = input.iterate(cxt);
                        return mergeMaps(maps, cxt, action, 40);
                    };
                }
            }
        }


    }


    /**
     * Implementation of the function map:put() =&gt; Map
     */

    public static class MapPut extends SystemFunction {

        @Override
        public MapItem call(XPathContext context, Sequence[] arguments) throws XPathException {

            MapItem baseMap = (MapItem) arguments[0].head();
            AtomicValue key = (AtomicValue) arguments[1].head();
            GroundedValue value = arguments[2].materialize();
            return baseMap.put(key, value);
        }

        @Override
        public ItemType getResultItemType(Expression[] args) {
            // Return type of existing map, provided the key and value are within this type
            if (args[0].getItemType() instanceof MapType mapType) {
                TypeHierarchy th = args[0].getConfiguration().getTypeHierarchy();
                PlainType keyType = mapType.getKeyType();
                SequenceType valueType = mapType.getValueType();
                ItemType newKeyType = args[1].getItemType();
                ItemType newValueType = args[2].getItemType();
                int newValueCard = args[2].getCardinality();
                if (th.isSubType(newKeyType, keyType) &&
                        th.isSubType(newValueType, valueType.getPrimaryType()) &&
                        Cardinality.subsumes(valueType.getCardinality(), newValueCard)) {
                    return args[0].getItemType();
                }
            }
            return super.getResultItemType(args);
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
