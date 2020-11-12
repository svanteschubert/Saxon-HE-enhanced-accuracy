////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.QNameValue;
import net.sf.saxon.value.SequenceExtent;

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
        List<QNameValue> myList = new ArrayList<>();
        myList.add(new QNameValue("xsl", NamespaceConstant.XSLT, "version"));
        myList.add(new QNameValue("xsl", NamespaceConstant.XSLT, "vendor"));
        myList.add(new QNameValue("xsl", NamespaceConstant.XSLT, "vendor-url"));
        myList.add(new QNameValue("xsl", NamespaceConstant.XSLT, "product-name"));
        myList.add(new QNameValue("xsl", NamespaceConstant.XSLT, "product-version"));
        myList.add(new QNameValue("xsl", NamespaceConstant.XSLT, "is-schema-aware"));
        myList.add(new QNameValue("xsl", NamespaceConstant.XSLT, "supports-serialization"));
        myList.add(new QNameValue("xsl", NamespaceConstant.XSLT, "supports-backwards-compatibility"));
        myList.add(new QNameValue("xsl", NamespaceConstant.XSLT, "supports-namespace-axis"));
        myList.add(new QNameValue("xsl", NamespaceConstant.XSLT, "supports-streaming"));
        myList.add(new QNameValue("xsl", NamespaceConstant.XSLT, "supports-dynamic-evaluation"));
        myList.add(new QNameValue("xsl", NamespaceConstant.XSLT, "supports-higher-order-functions"));
        myList.add(new QNameValue("xsl", NamespaceConstant.XSLT, "xpath-version"));
        myList.add(new QNameValue("xsl", NamespaceConstant.XSLT, "xsd-version"));

        if (context.getConfiguration().getBooleanProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS)) {
            for (Object s : System.getProperties().keySet()) {
                 myList.add(new QNameValue("", "", s.toString()));
            }
        }

        return SequenceExtent.makeSequenceExtent(myList);

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

// Copyright (c) 2018-2020 Saxonica Limited
