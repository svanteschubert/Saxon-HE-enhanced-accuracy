////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.functions.InsertBefore;
import net.sf.saxon.functions.OptionsParameter;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.arrays.SimpleArrayItem;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Function signatures (and pointers to implementations) of the functions defined in XPath 2.0
 */

public class MapFunctionSet extends BuiltInFunctionSet {

    public static MapFunctionSet THE_INSTANCE = new MapFunctionSet();

    public MapFunctionSet() {
        init();
    }

    public static MapFunctionSet getInstance() {
        return THE_INSTANCE;
    }



    private void init() {

        register("merge", 1, MapMerge.class, MapType.ANY_MAP_TYPE, ONE, 0)
                .arg(0, MapType.ANY_MAP_TYPE, STAR | INS, null);

        SpecificFunctionType ON_DUPLICATES_CALLBACK_TYPE = new SpecificFunctionType(
                new SequenceType[]{SequenceType.ANY_SEQUENCE, SequenceType.ANY_SEQUENCE},
                SequenceType.ANY_SEQUENCE
        );

        SequenceType oneOnDuplicatesFunction = SequenceType.makeSequenceType(
                ON_DUPLICATES_CALLBACK_TYPE, StaticProperty.EXACTLY_ONE);

        OptionsParameter mergeOptionDetails = new OptionsParameter();
        mergeOptionDetails.addAllowedOption("duplicates", SequenceType.SINGLE_STRING, new StringValue("use-first"));
        // duplicates=unspecified is retained because that's what the XSLT 3.0 Rec incorrectly uses
        mergeOptionDetails.setAllowedValues("duplicates", "FOJS0005", "use-first", "use-last", "combine", "reject", "unspecified", "use-any", "use-callback");
        mergeOptionDetails.addAllowedOption(MapMerge.errorCodeKey, SequenceType.SINGLE_STRING, new StringValue("FOJS0003"));
        mergeOptionDetails.addAllowedOption(MapMerge.keyTypeKey, SequenceType.SINGLE_STRING, new StringValue("anyAtomicType"));
        mergeOptionDetails.addAllowedOption(MapMerge.finalKey, SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        mergeOptionDetails.addAllowedOption(MapMerge.onDuplicatesKey, oneOnDuplicatesFunction, null);


        register("merge", 2, MapMerge.class, MapType.ANY_MAP_TYPE, ONE, 0)
                .arg(0, MapType.ANY_MAP_TYPE, STAR, null)
                .arg(1, MapType.ANY_MAP_TYPE, ONE, null)
                .optionDetails(mergeOptionDetails);

        register("entry", 2, MapEntry.class, MapType.ANY_MAP_TYPE, ONE, 0)
                .arg(0, BuiltInAtomicType.ANY_ATOMIC, ONE | ABS, null)
                .arg(1, AnyItemType.getInstance(), STAR | NAV, null);

        register("find", 2, MapFind.class, ArrayItemType.getInstance(), ONE, 0)
                .arg(0, AnyItemType.getInstance(), STAR | INS, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE | ABS, null);

        register("get", 2, MapGet.class, AnyItemType.getInstance(), STAR, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE | ABS, null);

        register("put", 3, MapPut.class, MapType.ANY_MAP_TYPE, ONE, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE | ABS, null)
                .arg(2, AnyItemType.getInstance(), STAR | NAV, null);

        register("contains", 2, MapContains.class, BuiltInAtomicType.BOOLEAN, ONE, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE | ABS, null);

        register("remove", 2, MapRemove.class, MapType.ANY_MAP_TYPE, ONE, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, STAR | ABS, null);

        register("keys", 1, MapKeys.class, BuiltInAtomicType.ANY_ATOMIC, STAR, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null);

        register("size", 1, MapSize.class, BuiltInAtomicType.INTEGER, ONE, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null);

        ItemType actionType = new SpecificFunctionType(
                new SequenceType[]{SequenceType.SINGLE_ATOMIC, SequenceType.ANY_SEQUENCE},
                SequenceType.ANY_SEQUENCE);

        register("for-each", 2, MapForEach.class, AnyItemType.getInstance(), STAR, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null)
                .arg(1, actionType, ONE | INS, null);

        register("untyped-contains", 2, MapUntypedContains.class, BuiltInAtomicType.BOOLEAN, ONE, 0)
                .arg(0, MapType.ANY_MAP_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.ANY_ATOMIC, ONE | ABS, null);


    }

    @Override
    public String getNamespace() {
        return NamespaceConstant.MAP_FUNCTIONS;
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
            if (it instanceof TupleType) {
                if (arguments[1] instanceof Literal) {
                    String key = ((Literal)arguments[1]).getValue().getStringValue();
                    if (((TupleType)it).getFieldType(key) == null) {
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
            ItemType mapType = args[0].getItemType();
            if (mapType instanceof TupleItemType && args[1] instanceof StringLiteral) {
                String key = ((StringLiteral) args[1]).getStringValue();
                TupleItemType tit = (TupleItemType) mapType;
                SequenceType valueType = tit.getFieldType(key);
                if (valueType == null) {
                    warning("Field " + key + " is not defined in tuple type");
                    return AnyItemType.getInstance();
                } else {
                    return valueType.getPrimaryType();
                }
            } else if (mapType instanceof MapType) {
                return ((MapType)mapType).getValueType().getPrimaryType();
            } else {
                return super.getResultItemType(args);
            }
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
            if (mapType instanceof TupleItemType && args[1] instanceof StringLiteral) {
                String key = ((StringLiteral) args[1]).getStringValue();
                TupleItemType tit = (TupleItemType) mapType;
                SequenceType valueType = tit.getFieldType(key);
                if (valueType == null) {
                    warning("Field " + key + " is not defined in tuple type");
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
                visitor.issueWarning(pendingWarning, arguments[0].getLocation());
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
                return EmptySequence.getInstance();
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

        private void processSequence(Sequence in, AtomicValue key, List<GroundedValue> result) throws XPathException {
            in.iterate().forEachOrFail(item -> {
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
     * Implementation of the extension function map:for-each(Map, Function) =&gt; item()*
     */
    public static class MapForEach extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            MapItem map = (MapItem) arguments[0].head();
            Function fn = (Function) arguments[1].head();
            List<GroundedValue> results = new ArrayList<>();
            for (KeyValuePair pair : map.keyValuePairs()) {
                Sequence seq = dynamicCall(fn, context, new Sequence[]{pair.key, pair.value});
                final GroundedValue val = seq.materialize();
                if (val.getLength() > 0) {
                    results.add(val);
                }
            }
            return new Chain(results);
        }
    }

    /**
     * Implementation of the extension function map:keys(Map) =&gt; atomicValue*
     */
    public static class MapKeys extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            MapItem map = (MapItem) arguments[0].head();
            assert map != null;
            return SequenceTool.toLazySequence(map.keys());
        }
    }

    /**
     * Implementation of the extension function map:merge() =&gt; Map
     * From 9.8, map:merge is also used to implement map constructors in XPath and the xsl:map
     * instruction in XSLT. For this purpose it accepts an additional option to define the error
     * code to be used to signal duplicates.
     */
    public static class MapMerge extends SystemFunction {

        final static String finalKey = "Q{" + NamespaceConstant.SAXON + "}final";
        final static String keyTypeKey ="Q{"+NamespaceConstant.SAXON +"}key-type";
        final static String onDuplicatesKey = "Q{" + NamespaceConstant.SAXON + "}on-duplicates";
        final static String errorCodeKey = "Q{" + NamespaceConstant.SAXON + "}duplicates-error-code";

        private String duplicates = "use-first";
        private String duplicatesErrorCode = "FOJS0003";
        private Function onDuplicates = null;
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
                MapItem options = (MapItem) ((Literal)arguments[1]).getValue().head();
                Map<String, Sequence> values = getDetails().optionDetails.processSuppliedOptions(
                        options, visitor.getStaticContext().makeEarlyEvaluationContext());
                String duplicates = ((StringValue) values.get("duplicates")).getStringValue();
                String duplicatesErrorCode = ((StringValue) values.get(errorCodeKey)).getStringValue();
                Function onDuplicates = (Function)values.get(onDuplicatesKey);
                if (onDuplicates != null) {
                    duplicates = "use-callback";
                }

                boolean isFinal = ((BooleanValue) values.get(finalKey)).getBooleanValue();
                String keyType = ((StringValue) values.get(keyTypeKey)).getStringValue();
                MapMerge mm2 = (MapMerge)MapFunctionSet.getInstance().makeFunction("merge", 1);
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
                    MapItem options = (MapItem) ((Literal) args[1]).getValue().head();
                    GroundedValue dupes = options.get(new StringValue("duplicates"));
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
            String duplicates = this.duplicates;
            String duplicatesErrorCode = this.duplicatesErrorCode;
            boolean allStringKeys = this.allStringKeys;
            boolean treatAsFinal = this.treatAsFinal;
            Function onDuplicates = this.onDuplicates;
            if (arguments.length > 1) {
                MapItem options = (MapItem) arguments[1].head();
                Map<String, Sequence> values = getDetails().optionDetails.processSuppliedOptions(options, context);
                duplicates = ((StringValue) values.get("duplicates")).getStringValue();
                duplicatesErrorCode = ((StringValue) values.get(errorCodeKey)).getStringValue();
                treatAsFinal = ((BooleanValue) values.get(finalKey)).getBooleanValue();
                allStringKeys = "string".equals(((StringValue)values.get(keyTypeKey)).getStringValue());
                onDuplicates = (Function) values.get(onDuplicatesKey);
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
                                        case "unspecified":
                                        case "use-any":
                                            // no action
                                            break;
                                        case "use-last":
                                            baseMap.initialPut(pair.key.getStringValue(), pair.value);
                                            break;
                                        case "combine":
                                            InsertBefore.InsertIterator combinedIter =
                                                    new InsertBefore.InsertIterator(pair.value.iterate(), existing.iterate(), 1);
                                            GroundedValue combinedValue = combinedIter.materialize();
                                            baseMap.initialPut(pair.key.getStringValue(), combinedValue);
                                            break;
                                        case "use-callback":
                                            Sequence[] args = new Sequence[]{existing, pair.value};
                                            Sequence combined = onDuplicates.call(context, args);
                                            baseMap.initialPut(pair.key.getStringValue(), combined.materialize());
                                            break;
                                        default:
                                            throw new XPathException("Duplicate key in constructed map: " +
                                                                             Err.wrap(pair.key.getStringValueCS()), duplicatesErrorCode);
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
                                        GroundedValue combinedValue = combinedIter.materialize();
                                        larger = larger.addEntry(pair.key, combinedValue);
                                        break;
                                    }
                                    case "combine-reverse": {
                                        InsertBefore.InsertIterator combinedIter =
                                                new InsertBefore.InsertIterator(existing.iterate(), pair.value.iterate(), 1);
                                        GroundedValue combinedValue = combinedIter.materialize();
                                        larger = larger.addEntry(pair.key, combinedValue);
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

        }

        private String invertDuplicates(String duplicates) {
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
                options.initialPut(new StringValue("duplicates"), new StringValue(duplicates));
                options.initialPut(new StringValue("duplicates-error-code"), new StringValue(duplicatesErrorCode));
                Literal.exportValue(options, out);
            }
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
