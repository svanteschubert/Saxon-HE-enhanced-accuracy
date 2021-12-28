////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.PackageData;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.BooleanValue;

/**
 * Implement the fn:doc-available() function
 */

public class DocAvailable extends SystemFunction  {

    private boolean isDocAvailable(AtomicValue hrefVal, XPathContext context) throws XPathException {
        if (hrefVal == null) {
            return false;
        }
        String href = hrefVal.getStringValue();
        return docAvailable(href, context);
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        return BooleanValue.get(isDocAvailable((AtomicValue) arguments[0].head(), context));
    }

    public boolean docAvailable(String href, XPathContext context) {
        try {
            PackageData packageData = getRetainedStaticContext().getPackageData();
            DocumentKey documentKey = DocumentFn.computeDocumentKey(href, getStaticBaseUriString(), packageData, context);
            DocumentPool pool = context.getController().getDocumentPool();
            if (pool.isMarkedUnavailable(documentKey)) {
                return false;
            }
            TreeInfo doc = pool.find(documentKey);
            if (doc != null) {
                return true;
            }
            Item item = DocumentFn.makeDoc(href, getStaticBaseUriString(), packageData, null, context, null, true);
            if (item != null) {
                return true;
            } else {
                // The document does not exist; ensure that this remains the case
                pool.markUnavailable(documentKey);
                return false;
            }
        } catch (XPathException e) {
            return false;
        }
    }


}

