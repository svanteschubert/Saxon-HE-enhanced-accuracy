////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Atomizer;
import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.CallableDelegate;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.AtomicSortComparer;
import net.sf.saxon.expr.sort.DescendingComparer;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.ma.arrays.ArraySort;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.ma.map.RecordType;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.type.EnumerationUnionType;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.SequenceExtent;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.List;


/**
 * This class implements the function fn:sort-by, which is a proposed function in XPath 4.0.
 * It also provides supporting code underpinning the implementation of fn:sort.
 */

public class SortBy extends SystemFunction {

    public static class ItemToBeSorted {
        public Item value;
        public GroundedValue[] sortKeys;
    }

    public static final FunctionItemType keyFunctionType =
        new SpecificFunctionType(SequenceType.SINGLE_ITEM, SequenceType.ATOMIC_SEQUENCE);
    public static RecordType sortKeyRecord = RecordType.nonExtensible(
            new RecordType.Field("key", SequenceType.optional(keyFunctionType), true),
            new RecordType.Field("collation", SequenceType.OPTIONAL_STRING, true),
            new RecordType.Field("order", SequenceType.optional(EnumerationUnionType.of("ascending", "descending")), true)
    );

    public static Callable DATA_FN =
            new CallableDelegate((cxt, args) -> Atomizer.atomize(args[0]));

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws XPathException if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {

        GroundedValue details = arguments[1].materialize();
        if (details.getLength() == 0) {
            StringCollator collation = context.getConfiguration().getCollation(getRetainedStaticContext().getDefaultCollationName());
            AtomicComparer comparer = AtomicSortComparer.makeSortComparer(collation,StandardNames.XS_ANY_ATOMIC_TYPE, 40, context);
            return sort(arguments[0].iterate(), listOfOne(DATA_FN), listOfOne(comparer), context);
        }

        List<Callable> sortKeys = new ArrayList<>();
        List<AtomicComparer> comparers = new ArrayList<>();

        for (Item f : details.asIterable()) {
            Callable k = (FunctionItem)((MapItem)f).get(new StringValue("key"));
            sortKeys.add(k == null ? DATA_FN : k);

            StringValue c = (StringValue) ((MapItem) f).get(new StringValue("collation"));
            String collation = c == null
                    ? getRetainedStaticContext().getDefaultCollationName()
                    : c.getStringValue();
            StringValue o = (StringValue) ((MapItem) f).get(new StringValue("order"));
            boolean descending = o != null && o.getStringValue().equals("descending");
            StringCollator collator = context.getConfiguration().getCollation(collation);
            if (collator == null) {
                throw new XPathException("Unknown collation " + collation, "FOCH0002");
            }
            int itemType = StandardNames.XS_ANY_ATOMIC_TYPE;
            AtomicComparer comparer = AtomicSortComparer.makeSortComparer(collator, itemType, 40, context);
            if (descending) {
                comparer = new DescendingComparer(comparer);
            }
            comparers.add(comparer);
        }

        return sort(arguments[0].iterate(), sortKeys, comparers, context);

    }

    /**
     * Sort a sequence
     * @param iterator iterator over the input sequence
     * @param keyFunctions list of functions for computing sort key values, major to minor
     * @param comparers list of comparers for comparing sort key values, major to minor
     * @param context XPath dynamic evaluation context
     * @return the sorted sequence
     * @throws XPathException for example if there are incomparable keys present
     */

    public static GroundedValue sort(SequenceIterator iterator,
                                     List<Callable> keyFunctions,
                                     List<AtomicComparer> comparers,
                                     XPathContext context) throws XPathException {

        final ArrayList<ItemToBeSorted> inputList = new ArrayList<>();
        Item item;
        while ((item = iterator.next()) != null) {
            ItemToBeSorted member = new ItemToBeSorted();
            member.value = item;
            member.sortKeys = new GroundedValue[keyFunctions.size()];
            int k = 0;
            for (Callable fn : keyFunctions) {
                member.sortKeys[k++] = fn.call(context, new Sequence[]{item}).materialize();
            }
            inputList.add(member);
        }

        try {
            inputList.sort((a, b) -> {
                for (int k = 0; k < comparers.size(); k++) {
                    int result = ArraySort.compareSortKeys(a.sortKeys[k], b.sortKeys[k], comparers.get(k));
                    if (result != 0) {
                        return result;
                    }
                }
                return 0;
            });
        } catch (ClassCastException e) {
            throw new XPathException("Non-comparable types found while sorting: " + e.getMessage(), "XPTY0004")
                    .asTypeError();
        }
        ArrayList<Item> outputList = new ArrayList<>(inputList.size());
        for (ItemToBeSorted member : inputList) {
            outputList.add(member.value);
        }
        return new SequenceExtent.Of<>(outputList);

    }

    @CSharpReplaceBody(code="return new System.Collections.Generic.List<T>() { element };")
    public static <T> List<T> listOfOne(T element) {
        return List.of(element);
    }

    

}
