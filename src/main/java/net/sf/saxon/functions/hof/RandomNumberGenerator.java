////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.SimpleLazyFunction;
import net.sf.saxon.functions.SimpleUnaryFunction;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.ma.map.RecordType;
import net.sf.saxon.ma.map.Shape;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.str.Twine8;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.SequenceExtent;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * This class implements the function random-number-generator(), which is a standard function in XPath 3.1
 */
public class RandomNumberGenerator extends SystemFunction implements Callable {

    public static ItemType RETURN_TYPE = RecordType.extensible(
            new RecordType.Field("number", SequenceType.SINGLE_DOUBLE, false),
            new RecordType.Field("next",
                                 SequenceType.one(
                                         // TODO: the precise type is recursive
                                         new SpecificFunctionType(SequenceType.one(new MapType(BuiltInAtomicType.STRING, SequenceType.SINGLE_ITEM)))),
                                 false),
            new RecordType.Field("permute",
                                 SequenceType.one(
                                         new SpecificFunctionType(SequenceType.ANY_SEQUENCE, SequenceType.ANY_SEQUENCE)),
                                 false));

    private static final Shape SHAPE = new Shape(new Twine8("number"),
                                                 new Twine8("next"),
                                                 new Twine8("permute"));

    private static MapItem generator(long seed) {
        Random random = new Random(seed);
        double number = random.nextDouble();
        long nextSeed = random.nextLong();
        return SHAPE.make(
                // number
                new DoubleValue(number),
                // next
                new SimpleLazyFunction(() -> generator(nextSeed), SequenceType.one(RETURN_TYPE)),
                // permute
                new SimpleUnaryFunction(input -> {
                        SequenceIterator iterator = input.iterate();
                        Item item;
                        final List<Item> output = new ArrayList<>();
                        Random rand = new Random(nextSeed);
                        while ((item = iterator.next()) != null) {
                            int p = rand.nextInt(output.size() + 1);
                            output.add(p, item);
                        }
                        return new SequenceExtent.Of<>(output);
                    }, SequenceType.ANY_SEQUENCE, SequenceType.ANY_SEQUENCE)
        );
    }


    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        long seed;
        if (arguments.length == 0) {
            // seed value must be repeatable within execution scope
            seed = context.getCurrentDateTime().randomSeed();
        } else {
            AtomicValue val = (AtomicValue) arguments[0].head();
            seed = val == null ? context.getCurrentDateTime().randomSeed() : val.hashCode();
        }
        return generator(seed);
    }


}

// Copyright (c) 2018-2026 Saxonica Limited
