////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.arrays;

import net.sf.saxon.expr.Atomizer;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.*;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.UnfailingIterator;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of the extension function array:sort(array, function) =&gt; array
 */
public class ArraySort extends SystemFunction {

    private static class MemberToBeSorted{
        public GroundedValue value;
        public GroundedValue sortKey;
        int originalPosition;
    }

    /**
     * Create a call on this function. This method is called by the compiler when it identifies
     * a function call that calls this function.
     *
     * @return an expression representing a call of this extension function
     */
    @Override
    public ArrayItem call(XPathContext context, Sequence[] arguments) throws XPathException {
        ArrayItem array = (ArrayItem) arguments[0].head();
        final List<MemberToBeSorted> inputList = new ArrayList<>(array.arrayLength());
        int i = 0;
        StringCollator collation;
        if (arguments.length == 1) {
            collation = context.getConfiguration().getCollation(getRetainedStaticContext().getDefaultCollationName());
        } else {
            StringValue collName = (StringValue)arguments[1].head();
            if (collName == null) {
                collation = context.getConfiguration().getCollation(getRetainedStaticContext().getDefaultCollationName());
            } else {
                collation = context.getConfiguration().getCollation(collName.getStringValue(), getStaticBaseUriString());
            }
        }
        Function key = null;
        if (arguments.length == 3){
            key = (Function) arguments[2].head();
        }
        for (GroundedValue seq: array.members()){
            MemberToBeSorted member = new MemberToBeSorted();
            member.value = seq;
            member.originalPosition = i++;
            if (key != null) {
                member.sortKey = dynamicCall(key, context, new Sequence[]{seq}).materialize();
            } else {
                member.sortKey = atomize(seq);
            }
            inputList.add(member);
        }
        final AtomicComparer atomicComparer =  AtomicSortComparer.makeSortComparer(
                collation, StandardNames.XS_ANY_ATOMIC_TYPE, context);
        try {
            inputList.sort((a, b) -> {
                int result = compareSortKeys(a.sortKey, b.sortKey, atomicComparer);
                if (result == 0) {
                    // TODO: unnecessary, we are now using a stable sort routine
                    return a.originalPosition - b.originalPosition;
                } else {
                    return result;
                }
            });
            //GenericSorter.quickSort(0, array.arrayLength(), sortable);
        } catch (ClassCastException e) {
            XPathException err = new XPathException("Non-comparable types found while sorting: " + e.getMessage());
            err.setErrorCode("XPTY0004");
            throw err;
        }
        List<GroundedValue> outputList = new ArrayList<>(array.arrayLength());
        for (MemberToBeSorted member: inputList){
            outputList.add(member.value);
        }
        return new SimpleArrayItem(outputList);
    }

    public static int compareSortKeys(GroundedValue a, GroundedValue b, AtomicComparer comparer) {
        UnfailingIterator iteratora = a.iterate();
        UnfailingIterator iteratorb = b.iterate();
        while (true){
            AtomicValue firsta = (AtomicValue) iteratora.next();
            AtomicValue firstb = (AtomicValue) iteratorb.next();
            if (firsta == null){
                if (firstb == null){
                    return 0;
                }
                else {
                    return -1;
                }
            }
            else if (firstb == null){
                return +1;
            }
            else {
                try {
                    int first = comparer.compareAtomicValues(firsta, firstb);
                    if (first == 0){
                        continue;
                    } else {
                        return first;
                    }
                } catch (NoDynamicContextException e) {
                    throw new AssertionError(e);
                }
            }
        }
    }

    private static GroundedValue atomize(Sequence input) throws XPathException {
        SequenceIterator iterator = input.iterate();
        SequenceIterator mapper = Atomizer.getAtomizingIterator(iterator, false);
        return mapper.materialize();
    }
}
