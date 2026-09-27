////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.arrays;

//import com.saxonica.functions.qt4.Slice;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.elab.Pingable;
import net.sf.saxon.functions.DeepEqual;
import net.sf.saxon.functions.Fold;
import net.sf.saxon.functions.FoldingFunction;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.ma.Parcel;
import net.sf.saxon.ma.map.*;
import net.sf.saxon.ma.zeno.ZenoSequence;
import net.sf.saxon.om.*;
import net.sf.saxon.str.Twine8;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.*;
import net.sf.saxon.z.IntHashSet;
import net.sf.saxon.z.IntSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Function signatures (and pointers to implementations) of the functions defined in XPath 3.1
 */

public class ArrayFunctionSet extends BuiltInFunctionSet {

    private final static ArrayFunctionSet instance31 = new ArrayFunctionSet(31);
    private final static ArrayFunctionSet instance40 = new ArrayFunctionSet(40);

    private ArrayFunctionSet(int version) {
        init(version);
    }

    public static ArrayFunctionSet getInstance(int version) {
        return version >= 40 ? instance40 : instance31;
    }

    public final static UnicodeString valueRecordKey = new Twine8("value");
    public final static Shape VALUE_RECORD = new Shape(valueRecordKey);

    private void init(int version) {


        register("append", 2, e -> {
            return e.populate(ArrayAppend::new, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                    .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                    .arg(1, AnyItemType.getInstance(), STAR | NAV, null);
        });


        SpecificFunctionType filterFunctionType31 = new SpecificFunctionType(
                SequenceType.ANY_SEQUENCE,
                SequenceType.SINGLE_BOOLEAN);

        SpecificFunctionType filterFunctionType40 = new SpecificFunctionType(
                SequenceType.ANY_SEQUENCE, SequenceType.SINGLE_INTEGER,
                SequenceType.OPTIONAL_BOOLEAN).withAcceptReducedArity();

        final SpecificFunctionType filterFunctionType = version >= 40 ? filterFunctionType40 : filterFunctionType31;

        register("filter", 2, e -> e.populate( ArrayFilter::new, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, filterFunctionType, ONE | INS, null));

        register("flatten", 1, e -> {
            return e.populate(ArrayFlatten::new, AnyItemType.getInstance(), STAR, 0)
                    .arg(0, AnyItemType.getInstance(), STAR | ABS, null);
        });

        SpecificFunctionType foldFunctionType31 = new SpecificFunctionType(
                SequenceType.ANY_SEQUENCE, SequenceType.ANY_SEQUENCE,
                SequenceType.ANY_SEQUENCE);

        SpecificFunctionType foldFunctionType40 = new SpecificFunctionType(
                SequenceType.ANY_SEQUENCE, SequenceType.ANY_SEQUENCE,
                SequenceType.ANY_SEQUENCE).withAcceptReducedArity();

        SpecificFunctionType foldFunctionType = version >= 40 ? foldFunctionType40 : foldFunctionType31;

        register("fold-left", 3, e -> {
            return e.populate(ArrayFoldLeft::new, AnyItemType.getInstance(), STAR, 0)
                    .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                    .arg(1, AnyItemType.getInstance(), STAR | NAV, null)
                    .arg(2, foldFunctionType, ONE | INS, null);
        });


        register("fold-right", 3, e -> {
            return e.populate(ArrayFoldRight::new, AnyItemType.getInstance(), STAR, 0)
                    .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                    .arg(1, AnyItemType.getInstance(), STAR | NAV, null)
                    .arg(2, foldFunctionType, ONE | INS, null);
        });


        SpecificFunctionType forEachFunctionType31 = new SpecificFunctionType(
                SequenceType.ANY_SEQUENCE,
                SequenceType.ANY_SEQUENCE);

        SpecificFunctionType forEachFunctionType40 = new SpecificFunctionType(
                SequenceType.ANY_SEQUENCE, SequenceType.SINGLE_INTEGER,
                SequenceType.ANY_SEQUENCE).withAcceptReducedArity();

        SpecificFunctionType forEachFunctionType = version >= 40 ? forEachFunctionType40 : forEachFunctionType31;

        register("for-each", 2, e -> e.populate( ArrayForEach::new, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, forEachFunctionType, ONE | INS, null));

        SpecificFunctionType forEachPairFunctionType31 = new SpecificFunctionType(
                SequenceType.ANY_SEQUENCE,
                SequenceType.ANY_SEQUENCE,
                SequenceType.ANY_SEQUENCE);

        SpecificFunctionType forEachPairFunctionType40 = new SpecificFunctionType(
                SequenceType.ANY_SEQUENCE, SequenceType.ANY_SEQUENCE, SequenceType.SINGLE_INTEGER,
                SequenceType.ANY_SEQUENCE).withAcceptReducedArity();

        SpecificFunctionType forEachPairFunctionType = version >= 40
                ? forEachPairFunctionType40
                : forEachPairFunctionType31;

        register("for-each-pair", 3, e -> e.populate( ArrayForEachPair::new, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(2, forEachPairFunctionType, ONE | INS, null));

        register("get", 2, e -> {
            return e.populate(ArrayGet::new, AnyItemType.INSTANCE, STAR, 0)
                    .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                    .arg(1, BuiltInAtomicType.INTEGER, ONE | ABS, null);
        });

        register("head", 1, e -> {
            return e.populate(ArrayHead::new, AnyItemType.INSTANCE, STAR, 0)
                    .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null);
        });

        register("insert-before", 3, e -> {
            return e.populate( ArrayInsertBefore::new, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                    .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                    .arg(1, BuiltInAtomicType.INTEGER, ONE | ABS, null)
                    .arg(2, AnyItemType.INSTANCE, STAR | NAV, null);
        });

        register("join", 1, e -> e.populate( ArrayJoin::new, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, STAR | INS, null));

        register("put", 3, e -> {
            return e.populate( ArrayPut::new, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                    .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                    .arg(1, BuiltInAtomicType.INTEGER, ONE | INS, null)
                    .arg(2, AnyItemType.INSTANCE, STAR | NAV, null);
        });

        register("remove", 2, e -> e.populate( ArrayRemove::new, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.INTEGER, STAR | ABS, null));

        register("reverse", 1, e -> e.populate( ArrayReverse::new, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null));

        register("size", 1, e -> e.populate( ArraySize::new, BuiltInAtomicType.INTEGER, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null));

        ItemType sortFunctionType = new SpecificFunctionType(
                SequenceType.ANY_SEQUENCE,
                SequenceType.ATOMIC_SEQUENCE);

        register("sort", 1, e -> e.populate( ArraySort::new, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null));

        register("sort", 2, e -> e.populate( ArraySort::new, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.STRING, OPT | ABS, null));

        register("sort", 3, e -> e.populate( ArraySort::new, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.STRING, OPT | ABS, null)
                .arg(2, sortFunctionType, ONE | INS, null));

        register("subarray", 2, e -> e.populate( ArraySubarray::new, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.INTEGER, ONE | ABS, null));

        register("subarray", 3, e -> e.populate( ArraySubarray::new, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.INTEGER, ONE | ABS, null)
                .arg(2, BuiltInAtomicType.INTEGER, (version>=40 ? OPT : ONE) | ABS, null));



        register("tail", 1, e -> e.populate( ArrayTail::new, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null));

        // TODO: the following functions should be private

        register("_to-sequence", 1, e -> {
            return e.populate(ArrayToSequence::new, AnyItemType.INSTANCE, STAR, 0)
                    .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null);
        });

        register("_from-sequence", 1, e -> {
            return e.populate(ArrayFromSequence::new, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                    .arg(0, AnyItemType.INSTANCE, STAR | INS, null);
        });


    }

    @Override
    public NamespaceUri getNamespace() {
        return NamespaceUri.ARRAY_FUNCTIONS;
    }

    @Override
    public String getConventionalPrefix() {
        return "array";
    }

    /**
     * Check that a number proposed for use as a subscript is greater than zero and less than
     * the maximum subscript allowed by the implementation (2^31-1), returning the value
     * as a Java int
     *
     * @param subscript the proposed subscript (one-based)
     * @param limit the upper limit allowed (usually the size of the array, sometimes arraysize + 1)
     * @return the proposed subscript as an int, if it is in range (still one-based)
     * @throws XPathException if the subscript is 0, negative, or outside the permitted range
     */
    public static int checkSubscript(IntegerValue subscript, int limit) throws XPathException {
        int index = subscript.asSubscript();
        if (index <= 0) {
            throw new XPathException("Array subscript " + subscript.getUnicodeStringValue() + " is out of range", "FOAY0001");
        }
        if (index > limit) {
            throw new XPathException("Array subscript " + subscript.getUnicodeStringValue() +
                                             " exceeds limit (" + limit + ")", "FOAY0001");
        }
        return index;
    }

    /**
     * Abstract superclass for functions that produce an array, and that decide what kind of array implementation
     * to use based on past experience. Specifically, if the generated array is frequently converted to
     * an ImmutableArrayItem, then the function ends up deciding to generate an ImmutableArrayItem in the
     * first place.
     */

    public static abstract class ArrayGeneratingFunction extends SystemFunction implements Pingable {

        private double numberOfCalls = 0;
        private double numberOfConversions = 0;
        private double totalSize = 0;

        /**
         * Callback function, invoked when a SimpleArrayItem created by this function needs to be converted
         * to an {@code ImmutableArrayItem}
         */

        @Override
        public void ping() {
            numberOfConversions++;
        }

        /**
         * Get the estimated number of members in the array, based on past experience
         * @return the average size of arrays previously created, plus a little margin for expansion
         */

        protected int expectedSize() {
            return numberOfCalls < 10 ? 10 : (int)(totalSize / numberOfCalls * 1.05); // allow a little leeway
        }

        /**
         * Construct an array, given a list of members
         * @param members the members of the array
         * @return the constructed array
         */
        protected ArrayItem makeArray(List<GroundedValue> members) {
            if (numberOfConversions > Math.max(10, numberOfCalls * 0.5)) {
                // More than half the calls result in the array being converted...
                return new ImmutableArrayItem(members);
            } else {
                numberOfCalls++;
                totalSize += members.size();
                SimpleArrayItem result = new SimpleArrayItem(members);
                result.requestNotification(this);
                return result;
            }
        }
    }


    /**
     * Implementation of the function array:append(array, item()*) =&gt; array
     */
    public static class ArrayAppend extends SystemFunction {

        @Override
        public ArrayItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            return array.append(arguments[1].materialize());
        }

    }

    /**
     * Implementation of the function array:filter(array, function) =&gt; array
     */
    public static class ArrayFilter extends ArrayGeneratingFunction {

        @Override
        public ArrayItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            FunctionItem fn = (FunctionItem) arguments[1].head();
            List<GroundedValue> list = new ArrayList<>(expectedSize());
            switch (fn.getArity()) {
                case 0: {
                    BooleanValue truth = ((BooleanValue) dynamicCall(fn, context).head());
                    return (truth != null && truth.getBooleanValue()) ? array : SimpleArrayItem.EMPTY_ARRAY;
                }
                case 1: {
                    for (GroundedValue gv : array.members()) {
                        BooleanValue truth = ((BooleanValue) dynamicCall(fn, context, gv).head());
                        if (truth != null && truth.getBooleanValue()) {
                            list.add(gv);
                        }
                    }
                    return makeArray(list);
                }
                case 2:
                    int position = 0;
                    for (GroundedValue gv : array.members()) {
                        BooleanValue truth = ((BooleanValue) dynamicCall(fn, context, gv, new Int64Value(++position)).head());
                        if (truth != null && truth.getBooleanValue()) {
                            list.add(gv);
                        }
                    }
                    return makeArray(list);
                default:
                    throw new XPathException("Wrong arity for array:filter callback function");
            }

        }
    }

    /**
     * Implementation of the function array:flatten =&gt; item()*
     */
    public static class ArrayFlatten extends SystemFunction {

        private static void flatten(Sequence arg, List<Item> out) {
            SequenceTool.supply(arg.iterate(), (ItemConsumer<? super Item>) item -> {
                if (item instanceof ArrayItem) {
                    for (GroundedValue member : ((ArrayItem) item).members()) {
                        flatten(member, out);
                    }
                } else {
                    out.add(item);
                }
            });
        }

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            List<Item> out = new ArrayList<>();
            flatten(arguments[0], out);
            return SequenceExtent.makeSequenceExtent(out);
        }
    }

    /**
     * Implementation of the function array:fold-left(array, item()*, function) =&gt; array
     */
    public static class ArrayFoldLeft extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            int arraySize = array.arrayLength();
            Sequence zero = arguments[1];
            FunctionItem fn = (FunctionItem) arguments[2].head();

            int arity = fn.getArity();
            Sequence[] args = new Sequence[arity];
            for (int i = 0; i < arraySize; i++) {
                if (arity > 0) {
                    args[0] = zero;
                    if (arity > 1) {
                        args[1] = array.get(i);
                    }
                }
                zero = dynamicCall(fn, context, args);
            }
            return zero;
        }
    }


    /**
     * Implementation of the function array:fold-left(array, item()*, function) =&gt; array
     */
    public static class ArrayFoldRight extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            Sequence zero = arguments[1];
            FunctionItem fn = (FunctionItem) arguments[2].head();


            int arity = fn.getArity();
            Sequence[] args = new Sequence[arity];
            for (int i = array.arrayLength() - 1; i >= 0; i--) {
                if (arity > 0) {
                    args[0] = array.get(i);
                    if (arity > 1) {
                        args[1] = zero;
                    }
                }
                zero = dynamicCall(fn, context, args);
            }
            return zero;
        }
    }

    /**
     * Implementation of the proposed 4.0 function array:empty(array)
     */
    public static class ArrayEmpty extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            int len = array.arrayLength();
            return BooleanValue.get(len == 0);
        }

    }

    /**
     * Implementation of the proposed 4.0 function array:foot(array) =&gt; item()*
     */
    public static class ArrayFoot extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            int len = array.arrayLength();
            if (len == 0) {
                throw new XPathException("Argument to array:foot is an empty array", "FOAY0001");
            }
            return array.get(len - 1);
        }

    }

    /**
     * Implementation of the function array:for-each(array, function) =&gt; array
     */
    public static class ArrayForEach extends ArrayGeneratingFunction {

        @Override
        public ArrayItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            FunctionItem fn = (FunctionItem) arguments[1].head();
            int arity = fn.getArity();
            Sequence[] args = new Sequence[arity];
            List<GroundedValue> list = new ArrayList<>(expectedSize());
            int position = 0;
            for (GroundedValue gv : array.members()) {
                if (arity > 0) {
                    args[0] = gv;
                    if (arity > 1) {
                        args[1] = new Int64Value(++position);
                    }
                }
                list.add(dynamicCall(fn, context, args).materialize());
            }
            return makeArray(list);
        }

    }

    /**
     * Implementation of the function array:for-each-pair(array, array, function) =&gt; array
     */
    public static class ArrayForEachPair extends ArrayGeneratingFunction {

        @Override
        public ArrayItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array1 = (ArrayItem) arguments[0].head();
            assert array1 != null;
            ArrayItem array2 = (ArrayItem) arguments[1].head();
            assert array2 != null;
            FunctionItem fn = (FunctionItem) arguments[2].head();
            int arity = fn.getArity();
            Sequence[] args = new Sequence[arity];
            List<GroundedValue> list = new ArrayList<>(expectedSize());
            int i;
            for (i=0; i < array1.arrayLength() && i < array2.arrayLength(); i++) {
                if (arity > 0) {
                    args[0] = array1.get(i);
                    if (arity > 1) {
                        args[1] = array2.get(i);
                        if (arity > 2) {
                            args[2] = new Int64Value(i+1);
                        }
                    }
                }
                list.add(dynamicCall(fn, context, args).materialize());
            }
            return makeArray(list);
        }
    }

    /**
     * Implementation of the function array:get(array, xs:integer) =&gt; item()*
     */
    public static class ArrayGet extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            IntegerValue index = (IntegerValue) arguments[1].head();
            if (arguments.length <= 2) {
                return array.get(checkSubscript(index, array.arrayLength()) - 1);
            } else {
                int i = index.asSubscript();
                if (i <= 0 || i > array.arrayLength()) {
                    return arguments[2];
                } else {
                    return array.get(i - 1);
                }

            }
        }

    }

    /**
     * Implementation of the function array:head(array) =&gt; item()*
     */
    public static class ArrayHead extends SystemFunction {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            if (array.arrayLength() == 0){
                throw new XPathException("Argument to array:head is an empty array","FOAY0001");
            }
            return array.get(0);
        }

    }

    /**
     * Implementation of the function array:insert-before(array, xs:integer, item()*) =&gt; array
     */
    public static class ArrayInsertBefore extends SystemFunction {


        @Override
        public ArrayItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            int index = checkSubscript((IntegerValue) arguments[1].head(), array.arrayLength() + 1) - 1;
            if (index < 0 || index > array.arrayLength()){
                throw new XPathException("Specified position is not in range","FOAY0001");
            }
            Sequence newMember = arguments[2];
            return array.insert(index, newMember.materialize());
        }

    }

    /**
     * Implementation of the function array:join(arrays) =&gt; array
     */
    public static class ArrayJoin extends SystemFunction {

        @Override
        public ArrayItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            SequenceIterator iterator = arguments[0].iterate();
            ArrayItem array = SimpleArrayItem.EMPTY_ARRAY;
            ArrayItem nextArray;
            while ((nextArray = (ArrayItem) iterator.next()) != null) {
                array = array.concat(nextArray);
            }
            return array;
        }

    }


    /**
     * Implementation of the function array:put(arrays, index, newValue) =&gt; array
     */
    public static class ArrayPut extends SystemFunction {

        @Override
        public ArrayItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            int index = checkSubscript((IntegerValue) arguments[1].head(), array.arrayLength()) - 1;
            GroundedValue newVal = arguments[2].materialize();
            return array.put(index, newVal);
        }

    }

    /**
     * Implementation of the function array:remove(array, xs:integer) =&gt; array
     */
    public static class ArrayRemove extends SystemFunction {


        @Override
        public ArrayItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            GroundedValue offsets = arguments[1].materialize();
            if (offsets instanceof IntegerValue) {
                int index = checkSubscript((IntegerValue) offsets, array.arrayLength()) - 1;
                return array.remove(index);
            }
            IntSet positions = new IntHashSet();
            SequenceIterator arg1 = offsets.iterate();
            SequenceTool.supply(arg1, (ItemConsumer<? super Item>) pos -> {
                int index = checkSubscript((IntegerValue) pos, array.arrayLength()) - 1;
                positions.add(index);
            });
            return array.removeSeveral(positions);
        }

    }


    /**
     * Implementation of the function array:reverse(array, xs:integer, xs:integer) =&gt; array
     */
    public static class ArrayReverse extends ArrayGeneratingFunction {

        @Override
        public ArrayItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            List<GroundedValue> list = new ArrayList<>(array.arrayLength());
            int i;
            for (i=0; i < array.arrayLength(); i++) {
                list.add(array.get(array.arrayLength()-i-1));
            }
            return makeArray(list);
        }

    }

    /**
     * Implementation of the function array:size(array) =&gt; integer
     */
    public static class ArraySize extends SystemFunction {

        @Override
        public IntegerValue call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            return new Int64Value(array.arrayLength());
        }

    }

    /**
     * Implementation of the function array:subarray(array, xs:integer, xs:integer) =&gt; array
     */
    public static class ArraySubarray extends SystemFunction {


        @Override
        public ArrayItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            int start = checkSubscript((IntegerValue) arguments[1].head(), array.arrayLength()+1);
            int length;
            if (arguments.length == 3) {
                IntegerValue len = (IntegerValue) arguments[2].head();
                if (len == null) {
                    length = array.arrayLength() - start + 1;
                } else {
                    int signum = len.signum();
                    if (signum < 0) {
                        throw new XPathException("Specified length of subarray is less than zero", "FOAY0002");
                    }
                    length = signum == 0 ? 0 : checkSubscript(len, array.arrayLength());
                }
            } else {
                length = array.arrayLength() - start + 1;
            }
            if (start < 1) {
                throw new XPathException("Start position is less than one","FOAY0001");
            }
            if (start > array.arrayLength() + 1) {
                throw new XPathException("Start position is out of bounds","FOAY0001");
            }
            if (start + length > array.arrayLength() + 1) {
                throw new XPathException("Specified length of subarray is too great for start position given","FOAY0001");
            }
            return array.subArray(start-1, start+length-1);
        }

    }

    /**
     * Implementation of the function array:tail(array) =&gt; item()*
     */
    public static class ArrayTail extends SystemFunction {

        @Override
        public ArrayItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            if (array.arrayLength() < 1){
                throw new XPathException("Argument to array:tail is an empty array","FOAY0001");
            }
            return array.remove(0);
        }
    }

    public static GroundedValue arrayMembers(ArrayItem input) {
        List<MapItem> parcels = new ArrayList<>(input.arrayLength());
        for (GroundedValue member : input.members()) {
            parcels.add(new ShapedMap(VALUE_RECORD, member));
        }
        return SequenceExtent.makeSequenceExtent(parcels);
    }


    /**
     * Implementation of the function array:_to-sequence(array) =&gt; item()* which
     * is used internally for the implementation of array?*
     */

    public static class ArrayToSequence extends SystemFunction {
        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            return toSequence(array);
        }

        public static Sequence toSequence(ArrayItem array) throws XPathException {
            ZenoSequence results = new ZenoSequence();
            for (GroundedValue seq : array.members()) {
                results = results.appendSequence(seq);
            }
            return results;
        }
    }

    /**
     * Implementation of the function array:_from-sequence(item()*) =&gt; array(*) which
     * is used internally for the implementation of array{} and of the saxon:array extension
     */

    public static class ArrayFromSequence extends FoldingFunction implements Pingable {

        private double numberOfCalls = 0;
        private double numberOfConversions = 0;

        /**
         * Callback function, invoked when a SimpleArrayItem created by this function needs to be converted
         * to an {@code ImmutableArrayItem}
         */

        @Override
        public void ping() {
            numberOfConversions++;
        }

        @Override
        public ArrayItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            if (numberOfConversions > Math.max(10, numberOfCalls * 0.5)) {
                return ImmutableArrayItem.from(arguments[0].iterate());
            } else {
                SimpleArrayItem result = SimpleArrayItem.makeSimpleArrayItem(arguments[0].iterate());
                result.requestNotification(this);
                numberOfCalls++;
                return result;
            }
        }

        /**
         * Create the Fold object which is used to perform a streamed evaluation
         *
         * @param context             the dynamic evaluation context
         * @param additionalArguments the values of all arguments other than the first.
         * @return the Fold object used to compute the function
         */
        @Override
        public Fold getFold(XPathContext context, Sequence... additionalArguments) {
            return new Fold() {
                final List<GroundedValue> members = new ArrayList<>();

                /**
                 * Process one item in the input sequence, returning a new copy of the working data
                 *
                 * @param item the item to be processed from the input sequence
                 */
                @Override
                public void processItem(Item item) {
                    members.add(item);
                }

                /**
                 * Ask whether the computation has completed. A function that can deliver its final
                 * result without reading the whole input should return true; this will be followed
                 * by a call on result() to deliver the final result.
                 *
                 * @return true if the result of the function is now available even though not all
                 * items in the sequence have been processed
                 */
                @Override
                public boolean isFinished() {
                    return false;
                }

                /**
                 * Compute the final result of the function, when all the input has been processed
                 *
                 * @return the result of the function
                 */
                @Override
                public Sequence result() {
                    return new SimpleArrayItem(members);
                }
            };
        }
    }


}
