////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.QNameException;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.QNameValue;
import net.sf.saxon.value.StringValue;


/**
 * This class supports the fn:QName() function (previously named fn:expanded-QName())
 */

public class QNameFn extends SystemFunction {


    public static QNameValue expandedQName(StringValue namespace, StringValue lexical) throws XPathException {

        String uri;
        if (namespace == null) {
            uri = null;
        } else {
            uri = namespace.getStringValue();
        }

        try {
            final String lex = lexical.getStringValue();
            final String[] parts = NameChecker.getQNameParts(lex);
            // The QNameValue constructor does not check the prefix
            if (!parts[0].isEmpty() && !NameChecker.isValidNCName(parts[0])) {
                XPathException err = new XPathException("Malformed prefix in QName: '" + parts[0] + '\'');
                err.setErrorCode("FOCA0002");
                throw err;
            }
            return new QNameValue(parts[0], uri, parts[1], BuiltInAtomicType.QNAME, true);
        } catch (QNameException e) {
            throw new XPathException(e.getMessage(), "FOCA0002");
        } catch (XPathException err) {
            if (err.getErrorCodeLocalPart().equals("FORG0001")) {
                err.setErrorCode("FOCA0002");
            }
            throw err;
        }
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
    public QNameValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        return expandedQName(
                (StringValue) arguments[0].head(),
                (StringValue) arguments[1].head()
        );
    }

    @Override
    public String getCompilerName() {
        return "QNameFnCompiler";
    }

}

