////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.AtomicSortComparer;
import net.sf.saxon.functions.SortBy;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

/**
 * This class implements the function fn:sort
 */

public class Sort extends SystemFunction {

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
            key = (FunctionItem) arguments[2].head();
        }
        if (key == null) {
            key = SortBy.DATA_FN;
        }
        return SortBy.sort(arguments[0].iterate(), SortBy.listOfOne(key), SortBy.listOfOne(comparer), context);
    }


}


// Copyright (c) 2018-2026 Saxonica Limited
