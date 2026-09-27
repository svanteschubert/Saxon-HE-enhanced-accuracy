////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.arrays;

import net.sf.saxon.expr.Atomizer;
import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.CallableDelegate;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.AtomicSortComparer;
import net.sf.saxon.functions.SortBy;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.ma.Parcel;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of the extension function array:sort(array, function) =&gt; array.
 * The array:sort() function is implemented by wrapping each member of the array as
 * a parcel, sorting the parcels using the same machinery as for fn:sort, and then
 * unparceling the results.
 */
public class ArraySort extends ArrayFunctionSet.ArrayGeneratingFunction {

    /**
     * Create a call on this function. This method is called by the compiler when it identifies
     * a function call that calls this function.
     *
     * @return an expression representing a call of this extension function
     */
    @Override
    public ArrayItem call(XPathContext context, Sequence[] arguments) throws XPathException {

        ArrayItem array = (ArrayItem) arguments[0].head();
        SequenceIterator parcels = array.parcels();

        String collationName = getRetainedStaticContext().getDefaultCollationName();
        if (arguments.length > 1) {
            StringValue collationNameItem = (StringValue) arguments[1].head();
            if (collationNameItem != null) {
                collationName = collationNameItem.getStringValue();
            }
        }
        StringCollator collation = context.getConfiguration().getCollation(collationName);
        int version = getRetainedStaticContext().getPackageData().getHostLanguageVersion();
        AtomicComparer comparer = AtomicSortComparer.makeSortComparer(
                collation, StandardNames.XS_ANY_ATOMIC_TYPE, version, context);
        Callable key = null;
        if (arguments.length > 2) {
            FunctionItem suppliedKeyFn = (FunctionItem) arguments[2].head();
            // preprocess Parcels by unparceling them before calling the supplied function
            key = new CallableDelegate((cxt, args) ->
                suppliedKeyFn.call(cxt, new Sequence[]{((Parcel)args[0].head()).getValue()}));
        }
        if (key == null) {
            key = new CallableDelegate((cxt, args) -> Atomizer.atomize(((Parcel)args[0].head()).getValue()));
        }
        GroundedValue sortedParcels =
                SortBy.sort(parcels, SortBy.listOfOne(key), SortBy.listOfOne(comparer), context);

        List<GroundedValue> members = new ArrayList<>(expectedSize());
        for (Item parcel : sortedParcels.asIterable()) {
            members.add(((Parcel)parcel).getValue());
        }
        return makeArray(members);

    }

    /**
     * Lexicographic sort: given two atomic sequences, compare them by comparing individual
     * items until a pair of items is found that differs.
     * @param a the first sequence (must be a sequence of atomic items)
     * @param b the second sequence  (must be a sequence of atomic items)
     * @param comparer comparer for individual items
     * @return -1, 0, or +1 depending on the magnitude relationship
     */
    public static int compareSortKeys(GroundedValue a, GroundedValue b, AtomicComparer comparer) {
        SequenceIterator iteratora = a.iterate();
        SequenceIterator iteratorb = b.iterate();
        while (true) {
            AtomicValue a0 = (AtomicValue) iteratora.next();
            AtomicValue b0 = (AtomicValue) iteratorb.next();
            if (a0 == null) {
                if (b0 == null) {
                    return 0;
                }
                else {
                    return -1;
                }
            } else if (b0 == null) {
                return +1;
            } else {
                int first = comparer.compareAtomicValues(a0, b0);
                if (first != 0) {
                    return first;
                }
            }
        }
    }
}
