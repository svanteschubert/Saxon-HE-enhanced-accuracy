////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.arrays;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.AtomicSortComparer;
import net.sf.saxon.expr.sort.DescendingComparer;
import net.sf.saxon.functions.SortBy;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.ma.Parcel;
import net.sf.saxon.ma.map.*;
import net.sf.saxon.om.*;
import net.sf.saxon.str.Twine8;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.EnumerationUnionType;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.List;

/**
 * This class implements the function array:sort-by, which is a proposed function in XPath 4.0
 */

public class ArraySortBy extends SystemFunction {

    public static class MemberToBeSorted {
        public GroundedValue value;
        public GroundedValue[] sortKeys;
    }

    public static final FunctionItemType keyFunctionType =
        new SpecificFunctionType(SequenceType.ANY_SEQUENCE, SequenceType.ATOMIC_SEQUENCE);
    public static RecordType sortKeyRecord = RecordType.nonExtensible(
            new RecordType.Field("key", SequenceType.optional(keyFunctionType), true),
            new RecordType.Field("collation", SequenceType.OPTIONAL_STRING, true),
            new RecordType.Field("order", SequenceType.optional(EnumerationUnionType.of("ascending", "descending")), true)
    );

    public static Callable dataFn = SortBy.DATA_FN;

    public static Shape keyDefinition =
            new Shape(new Twine8("key"), new Twine8("collation"), new Twine8("order"));

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
        List<Callable> sortKeys = new ArrayList<>();
        List<AtomicComparer> comparers = new ArrayList<>();

        if (details.getLength() == 0) {
            sortKeys.add(dataFn);
            String collationName = getRetainedStaticContext().getDefaultCollationName();
            StringCollator collator = context.getConfiguration().getCollation(collationName);
            if (collator == null) {
                throw new XPathException("Unknown collation " + collationName, "FOCH0002");
            }
            comparers.add(
                    AtomicSortComparer.makeSortComparer(
                            collator, StandardNames.XS_ANY_ATOMIC_TYPE, 40, context));
        } else {

            for (Item f : details.asIterable()) {
                Callable k = (FunctionItem) ((MapItem) f).get(new StringValue("key"));
                sortKeys.add(k == null ? dataFn : k);

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
        }

        final ArrayList<MemberToBeSorted> inputList = new ArrayList<>();
        SequenceIterator iterator = ((ArrayItem)arguments[0].head()).parcels();
        Item item;
        while ((item = iterator.next()) != null) {
            MemberToBeSorted member = new MemberToBeSorted();
            member.value = ((Parcel)item).getValue();
            member.sortKeys = new GroundedValue[sortKeys.size()];
            int k = 0;
            for (Callable callable : sortKeys) {
                member.sortKeys[k++] = callable.call(context, new Sequence[]{((Parcel) item).getValue()}).materialize();
            }
            inputList.add(member);
        }

        try {
            inputList.sort((a, b) -> {
                for (int k = 0; k < sortKeys.size(); k++) {
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
        ArrayList<GroundedValue> outputList = new ArrayList<>(inputList.size());
        for (MemberToBeSorted member : inputList) {
            outputList.add(member.value);
        }
        return new SimpleArrayItem(outputList);
    }



    

}
