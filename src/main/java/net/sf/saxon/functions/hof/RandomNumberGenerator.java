////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.CallableFunction;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.ma.map.DictionaryMap;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.SequenceExtent;
import net.sf.saxon.value.SequenceType;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * This class implements the function random-number-generator(), which is a standard function in XPath 3.1
 */
public class RandomNumberGenerator extends SystemFunction implements Callable {

    public static final MapType RETURN_TYPE = new MapType(BuiltInAtomicType.STRING, SequenceType.SINGLE_ITEM);

    private final static FunctionItemType NEXT_FN_TYPE = new SpecificFunctionType(
            new SequenceType[]{}, // zero arguments
            SequenceType.makeSequenceType(RETURN_TYPE, StaticProperty.ALLOWS_ONE));

    private final static FunctionItemType PERMUTE_FN_TYPE = new SpecificFunctionType(
        new SequenceType[]{SequenceType.ANY_SEQUENCE},
        SequenceType.ANY_SEQUENCE);


    private static MapItem generator(long seed, XPathContext context) throws XPathException {
        Random random = new Random(seed);
        double number = random.nextDouble();
        long nextSeed = random.nextLong();
        DictionaryMap map = new DictionaryMap();
        map.initialPut("number",
            new DoubleValue(number));
        map.initialPut("next",
            new CallableFunction(0, new NextGenerator(nextSeed), NEXT_FN_TYPE));
        map.initialPut("permute",
            new CallableFunction(1, new Permutation(nextSeed), PERMUTE_FN_TYPE));
        return map;
    }

    private static class Permutation implements Callable {
        Long nextSeed;
        public Permutation(Long nextSeed) {
            this.nextSeed = nextSeed;
        }
        /**
         * Call the Callable.
         *
         * @param context   the dynamic evaluation context
         * @param arguments the values of the arguments, supplied as Sequences.
         *                  <p>Generally it is advisable, if calling iterate() to process a supplied sequence, to
         *                  call it only once; if the value is required more than once, it should first be converted
         *                  to a {@link net.sf.saxon.om.GroundedValue} by calling the utility methd
         *                  SequenceTool.toGroundedValue().</p>
         *                  <p>If the expected value is a single item, the item should be obtained by calling
         *                  Sequence.head(): it cannot be assumed that the item will be passed as an instance of
         *                  {@link net.sf.saxon.om.Item} or {@link net.sf.saxon.value.AtomicValue}.</p>
         *                  <p>It is the caller's responsibility to perform any type conversions required
         *                  to convert arguments to the type expected by the callee. An exception is where
         *                  this Callable is explicitly an argument-converting wrapper around the original
         *                  Callable.</p>
         * @return the result of the evaluation, in the form of a Sequence. It is the responsibility
         * of the callee to ensure that the type of result conforms to the expected result type.
         * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs during the evaluation of the expression
         */

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            Sequence input = (Sequence)arguments[0];
            SequenceIterator iterator = input.iterate();
            Item item;
            final List<Item> output = new LinkedList<>();
            Random random = new Random(nextSeed);
            while ((item = iterator.next()) != null) {
                int p = random.nextInt(output.size()+1);
                output.add(p, item);
            }
            return new SequenceExtent(output);
        }

        /**
         * The value of toString() may be used to identify this function in diagnostics
         */

        public String toString() {
            return "random-number-generator.permute";
        }

    }

    private static class NextGenerator implements Callable {
        long nextSeed;
        public NextGenerator(long nextSeed){
            this.nextSeed = nextSeed;
        }

        /**
         * Call the Callable.
         *
         * @param context   the dynamic evaluation context
         * @param arguments the values of the arguments, supplied as Sequences.
         *                  <p>Generally it is advisable, if calling iterate() to process a supplied sequence, to
         *                  call it only once; if the value is required more than once, it should first be converted
         *                  to a {@link net.sf.saxon.om.GroundedValue} by calling the utility methd
         *                  SequenceTool.toGroundedValue().</p>
         *                  <p>If the expected value is a single item, the item should be obtained by calling
         *                  Sequence.head(): it cannot be assumed that the item will be passed as an instance of
         *                  {@link net.sf.saxon.om.Item} or {@link net.sf.saxon.value.AtomicValue}.</p>
         *                  <p>It is the caller's responsibility to perform any type conversions required
         *                  to convert arguments to the type expected by the callee. An exception is where
         *                  this Callable is explicitly an argument-converting wrapper around the original
         *                  Callable.</p>
         * @return the result of the evaluation, in the form of a Sequence. It is the responsibility
         * of the callee to ensure that the type of result conforms to the expected result type.
         * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs during the evaluation of the expression
         */

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            return generator(nextSeed, context);
        }

        /**
         * The value of toString() may be used to identify this function in diagnostics
         */

        public String toString() {
            return "random-number-generator.next";
        }
    }



    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        long seed;
        if (arguments.length == 0) {
            // seed value must be repeatable within execution scope
            seed = context.getCurrentDateTime().getCalendar().getTimeInMillis();
        } else {
            AtomicValue val = (AtomicValue) arguments[0].head();
            seed = val == null ? context.getCurrentDateTime().getCalendar().getTimeInMillis() : val.hashCode();
        }
        return generator(seed, context);
    }


}

// Copyright (c) 2018-2020 Saxonica Limited
