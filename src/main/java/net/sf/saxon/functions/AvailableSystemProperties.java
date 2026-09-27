////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

//import com.saxonica.functions.extfn.EXPathArchive.Archive;
//import com.saxonica.functions.extfn.EXPathBinaryFunctionSet;
//import com.saxonica.functions.extfn.EXPathFileFunctionSet;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.om.AtomicArray;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.QNameValue;

import java.util.ArrayList;
import java.util.List;

public class AvailableSystemProperties extends SystemFunction {

    /**
     * Evaluate the expression (dynamic evaluation)
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        List<AtomicValue> myList = new ArrayList<>();
        myList.add(new QNameValue("xsl", NamespaceUri.XSLT, "version"));
        myList.add(new QNameValue("xsl", NamespaceUri.XSLT, "vendor"));
        myList.add(new QNameValue("xsl", NamespaceUri.XSLT, "vendor-url"));
        myList.add(new QNameValue("xsl", NamespaceUri.XSLT, "product-name"));
        myList.add(new QNameValue("xsl", NamespaceUri.XSLT, "product-version"));
        myList.add(new QNameValue("xsl", NamespaceUri.XSLT, "is-schema-aware"));
        myList.add(new QNameValue("xsl", NamespaceUri.XSLT, "supports-serialization"));
        myList.add(new QNameValue("xsl", NamespaceUri.XSLT, "supports-backwards-compatibility"));
        myList.add(new QNameValue("xsl", NamespaceUri.XSLT, "supports-namespace-axis"));
        myList.add(new QNameValue("xsl", NamespaceUri.XSLT, "supports-streaming"));
        myList.add(new QNameValue("xsl", NamespaceUri.XSLT, "supports-dynamic-evaluation"));
        myList.add(new QNameValue("xsl", NamespaceUri.XSLT, "supports-higher-order-functions"));
        myList.add(new QNameValue("xsl", NamespaceUri.XSLT, "xpath-version"));
        myList.add(new QNameValue("xsl", NamespaceUri.XSLT, "xsd-version"));

        if (context.getConfiguration().getBooleanProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS)) {
            for (String s : System.getProperties().stringPropertyNames()) {
                 myList.add(new QNameValue("", NamespaceUri.NULL, s));
            }
        }

        return new AtomicArray(myList);

    }

    @Override
    public Expression makeFunctionCall(Expression[] arguments) {
        return new SystemFunctionCall(this, arguments) {
            // Suppress early evaluation
            @Override
            public Expression preEvaluate(ExpressionVisitor visitor) {
                return this;
            }
        };
    }
}

// Copyright (c) 2018-2026 Saxonica Limited
