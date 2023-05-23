////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.arrays;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.Fold;
import net.sf.saxon.functions.FoldingFunction;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.*;
import net.sf.saxon.z.IntHashSet;
import net.sf.saxon.z.IntSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Function signatures (and pointers to implementations) of the functions defined in XPath 2.0
 */

public class ArrayFunctionSet extends BuiltInFunctionSet {

    public static ArrayFunctionSet THE_INSTANCE = new ArrayFunctionSet();

    public ArrayFunctionSet() {
        init();
    }

    public static ArrayFunctionSet getInstance() {
        return THE_INSTANCE;
    }

    private void init() {


        register("append", 2, ArrayAppend.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, AnyItemType.getInstance(), STAR | NAV, null);

        ItemType filterFunctionType = new SpecificFunctionType(
                new SequenceType[]{SequenceType.ANY_SEQUENCE},
                SequenceType.SINGLE_BOOLEAN);

        register("filter", 2, ArrayFilter.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, filterFunctionType, ONE | INS, null);

        register("flatten", 1, ArrayFlatten.class, AnyItemType.getInstance(), STAR, 0)
                .arg(0, AnyItemType.getInstance(), STAR | ABS, null);

        ItemType foldFunctionType = new SpecificFunctionType(
                new SequenceType[]{SequenceType.ANY_SEQUENCE, SequenceType.ANY_SEQUENCE},
                SequenceType.ANY_SEQUENCE);

        register("fold-left", 3, ArrayFoldLeft.class, AnyItemType.getInstance(), STAR, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, AnyItemType.getInstance(), STAR | NAV, null)
                .arg(2, foldFunctionType, ONE | INS, null);

        register("fold-right", 3, ArrayFoldRight.class, AnyItemType.getInstance(), STAR, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, AnyItemType.getInstance(), STAR | NAV, null)
                .arg(2, foldFunctionType, ONE | INS, null);

        ItemType forEachFunctionType = new SpecificFunctionType(
                new SequenceType[]{SequenceType.ANY_SEQUENCE},
                SequenceType.ANY_SEQUENCE);

        register("for-each", 2, ArrayForEach.class, AnyItemType.getInstance(), STAR, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, forEachFunctionType, ONE | INS, null);

        register("for-each-pair", 3, ArrayForEachPair.class, AnyItemType.getInstance(), STAR, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(2, foldFunctionType, ONE | INS, null);

        register("get", 2, ArrayGet.class, AnyItemType.getInstance(), STAR, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.INTEGER, ONE | ABS, null);

        register("head", 1, ArrayHead.class, AnyItemType.getInstance(), STAR, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null);

        register("insert-before", 3, ArrayInsertBefore.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.INTEGER, STAR | ABS, null)
                .arg(2, AnyItemType.getInstance(), STAR | NAV, null);

        register("join", 1, ArrayJoin.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, STAR | INS, null);

        register("put", 3, ArrayPut.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.INTEGER, STAR | INS, null)
                .arg(2, AnyItemType.getInstance(), STAR | NAV, null);

        register("remove", 2, ArrayRemove.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.INTEGER, STAR | ABS, null);

        register("reverse", 1, ArrayReverse.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null);

        register("size", 1, ArraySize.class, BuiltInAtomicType.INTEGER, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null);

        ItemType sortFunctionType = new SpecificFunctionType(
                new SequenceType[]{SequenceType.ANY_SEQUENCE},
                SequenceType.ATOMIC_SEQUENCE);

        register("sort", 1, ArraySort.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null);

        register("sort", 2, ArraySort.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.STRING, OPT | ABS, null);

        register("sort", 3, ArraySort.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.STRING, OPT | ABS, null)
                .arg(2, sortFunctionType, ONE | INS, null);

        register("subarray", 2, ArraySubarray.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.INTEGER, ONE | ABS, null);

        register("subarray", 3, ArraySubarray.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null)
                .arg(1, BuiltInAtomicType.INTEGER, ONE | ABS, null)
                .arg(2, BuiltInAtomicType.INTEGER, ONE | ABS, null);

        register("tail", 1, ArrayTail.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null);

        register("_to-sequence", 1, ArrayToSequence.class, AnyItemType.getInstance(), STAR, 0)
                .arg(0, ArrayItemType.ANY_ARRAY_TYPE, ONE | INS, null);

        register("_from-sequence", 1, ArrayFromSequence.class, ArrayItemType.ANY_ARRAY_TYPE, ONE, 0)
                .arg(0, AnyItemType.getInstance(), STAR | INS, null);


    }

    @Override
    public String getNamespace() {
        return NamespaceConstant.ARRAY_FUNCTIONS;
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
            throw new XPathException("Array subscript " + subscript.getStringValue() + " is out of range", "FOAY0001");
        }
        if (index > limit) {
            throw new XPathException("Array subscript " + subscript.getStringValue() +
                                             " exceeds limit (" + limit + ")", "FOAY0001");
        }
        return index;
    }


    /**
     * Implementation of the function array:append(array, item()*) =&gt; array
     */
    public static class ArrayAppend extends SystemFunction {

        @Override
        public ArrayItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            return append(array, arguments[1]);
        }

        public static ArrayItem append(ArrayItem array, Sequence member) throws XPathException {
            List<GroundedValue> list = new ArrayList<>(1);
            list.add(member.materialize());
            SimpleArrayItem otherArray = new SimpleArrayItem(list);
            return array.concat(otherArray);
        }

    }

    /**
     * Implementation of the function array:filter(array, function) =&gt; array
     */
    public static class ArrayFilter extends SystemFunction {

        @Override
        public ArrayItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            Function fn = (Function) arguments[1].head();
            List<GroundedValue> list = new ArrayList<>(1);
            int i;
            for (i=0; i < array.arrayLength(); i++) {
                if (((BooleanValue) dynamicCall(fn, context, new Sequence[]{array.get(i)}).head()).getBooleanValue()) {
                    list.add(array.get(i));
                }
            }
            return new SimpleArrayItem(list);
        }
    }

    /**
     * Implementation of the function array:flatten =&gt; item()*
     */
    public static class ArrayFlatten extends SystemFunction {

        private void flatten(Sequence arg, List<Item> out) throws XPathException {
            arg.iterate().forEachOrFail(item -> {
                if (item instanceof ArrayItem) {
                    for (Sequence member : ((ArrayItem) item).members()) {
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
            Function fn = (Function) arguments[2].head();
            int i;
            for (i=0; i < arraySize; i++) {
                zero = dynamicCall(fn, context, new Sequence[]{zero, array.get(i)});
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
            Function fn = (Function) arguments[2].head();
            int i;
            for (i = array.arrayLength() - 1; i >= 0; i--) {
                zero = dynamicCall(fn, context, new Sequence[]{array.get(i), zero});
            }
            return zero;
        }
    }

    /**
     * Implementation of the function array:for-each(array, function) =&gt; array
     */
    public static class ArrayForEach extends SystemFunction {

        @Override
        public ArrayItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            Function fn = (Function) arguments[1].head();
            List<GroundedValue> list = new ArrayList<>(1);
            int i;
            for (i=0; i < array.arrayLength(); i++) {
                list.add(dynamicCall(fn, context, new GroundedValue[]{array.get(i)}).materialize());
            }
            return new SimpleArrayItem(list);
        }

    }

    /**
     * Implementation of the function array:for-each-pair(array, array, function) =&gt; array
     */
    public static class ArrayForEachPair extends SystemFunction {

        @Override
        public ArrayItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array1 = (ArrayItem) arguments[0].head();
            assert array1 != null;
            ArrayItem array2 = (ArrayItem) arguments[1].head();
            assert array2 != null;
            Function fn = (Function) arguments[2].head();
            List<GroundedValue> list = new ArrayList<>(1);
            int i;
            for (i=0; i < array1.arrayLength() && i < array2.arrayLength(); i++) {
                list.add(dynamicCall(fn, context, new Sequence[]{array1.get(i), array2.get(i)}).materialize());
            }
            return new SimpleArrayItem(list);
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
            return array.get(checkSubscript(index, array.arrayLength()) - 1);
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
            arg1.forEachOrFail(pos -> {
                int index = checkSubscript((IntegerValue)pos, array.arrayLength()) - 1;
                positions.add(index);
            });
            return array.removeSeveral(positions);
        }

    }

    /**
     * Implementation of the function array:reverse(array, xs:integer, xs:integer) =&gt; array
     */
    public static class ArrayReverse extends SystemFunction {

        @Override
        public ArrayItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            ArrayItem array = (ArrayItem) arguments[0].head();
            assert array != null;
            List<GroundedValue> list = new ArrayList<>(1);
            int i;
            for (i=0; i < array.arrayLength(); i++) {
                list.add(array.get(array.arrayLength()-i-1));
            }
            return new SimpleArrayItem(list);
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
                int signum = len.signum();
                if (signum < 0) {
                    throw new XPathException("Specified length of subarray is less than zero", "FOAY0002");
                }
                length = signum == 0 ? 0 : checkSubscript(len, array.arrayLength());
            }
            else {
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
            List<GroundedValue> results = new ArrayList<>();
            for (Sequence seq : array.members()) {
                results.add(seq.materialize());
            }
            return new Chain(results);
        }
    }

    /**
     * Implementation of the function array:_from-sequence(item()*) =&gt; array(*) which
     * is used internally for the implementation of array{} and of the saxon:array extension
     */

    public static class ArrayFromSequence extends FoldingFunction {
        @Override
        public ArrayItem call(XPathContext context, Sequence[] arguments) throws XPathException {
            return SimpleArrayItem.makeSimpleArrayItem(((Sequence)arguments[0]).iterate());
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
                List<GroundedValue> members = new ArrayList<>();

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
                public ArrayItem result() {
                    return new SimpleArrayItem(members);
                }
            };
        }
    }
}
