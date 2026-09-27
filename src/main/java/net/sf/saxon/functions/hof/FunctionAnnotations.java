////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.ma.map.SingleEntryMap;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.query.Annotation;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.QNameValue;
import net.sf.saxon.value.SequenceExtent;

import java.util.ArrayList;
import java.util.List;


/**
 * This class implements the function function-annotations(), which is a standard function in XPath 4.0
 */

public class FunctionAnnotations extends SystemFunction {

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        FunctionItem f = (FunctionItem) arguments[0].head();
        AnnotationList annotationList = f.getAnnotations();
        List<Item> result = new ArrayList<>();
        for (Annotation a : annotationList) {
            result.add(
                    new SingleEntryMap(
                        new QNameValue(a.getAnnotationQName(), BuiltInAtomicType.QNAME),
                        SequenceExtent.makeSequenceExtent(a.getAnnotationParameters()),
                        40
            ));
        }
        return SequenceExtent.makeSequenceExtent(result);
    }
}

// Copyright (c) 2024-2026 Saxonica Limited
