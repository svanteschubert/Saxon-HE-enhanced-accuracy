////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.PackageData;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.BooleanValue;

import java.util.Map;

/**
 * Implement the fn:doc-available() function
 */

public class DocAvailable extends SystemFunction  {

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
        boolean result = false;
        AtomicValue hrefVal = (AtomicValue) arguments[0].head();
        if (hrefVal != null) {

            ParseOptions parseOptions = null;
            try {
                parseOptions = context.getConfiguration().getParseOptions();

                MapItem options = null;
                if (getArity() >= 2) {
                    options = (MapItem) arguments[1].head();
                }
                if (options != null && !options.isEmpty()) {
                    Map<String, GroundedValue> checkedOptions = getDetails().optionDetails.processSuppliedOptions(options, context, 40);
                    parseOptions = Doc.processOptions(checkedOptions, getRetainedStaticContext());

                } else {

                    PackageData pd = getRetainedStaticContext().getPackageData();
                    if (pd instanceof StylesheetPackage) {
                        parseOptions = parseOptions.withSpaceStrippingRule(((StylesheetPackage) pd).getSpaceStrippingRule());
                    } else {
                        parseOptions = parseOptions.withSpaceStrippingRule(IgnorableSpaceStrippingRule.INSTANCE);
                    }


                }
            } catch (XPathException e) {
                if (e.hasErrorCode("FODC0013")) {
                    return BooleanValue.FALSE;
                } else {
                    throw e;
                }
            }

            String href = hrefVal.getStringValue();
            result = docAvailable(href, parseOptions, context);
        }
        return BooleanValue.get(result);
    }

    private boolean docAvailable(String href, ParseOptions parseOptions, XPathContext context) {
        try {
            PackageData packageData = getRetainedStaticContext().getPackageData();

            int schemaValidationMode = parseOptions.getSchemaValidationMode();
            if (!packageData.isSchemaAware() && (schemaValidationMode == Validation.STRICT  || schemaValidationMode == Validation.LAX)) {
                // Regardless of whether it exists, the document will be unavailable because
                // schema validation (STRICT or LAX) is required but not supported
                return false;
            }

            DocumentKey documentKey;
            documentKey = DocumentFn.computeDocumentKey(href, getStaticBaseUriString(), packageData, parseOptions);
            DocumentPool pool = context.getController().getDocumentPool();
            if (pool.isMarkedUnavailable(documentKey)) {
                return false;
            }
            TreeInfo doc = pool.find(documentKey);
            if (doc != null) {
                return true;
            }
            Item item = DocumentFn.makeDoc(href, getStaticBaseUriString(), packageData, parseOptions, context, null, true);
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

