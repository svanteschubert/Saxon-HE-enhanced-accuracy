////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.QNameValue;
import net.sf.saxon.value.StringValue;


/**
 * This class supports the fn:QName() function
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
            final String[] parts = NameChecker.getQNameParts(lexical.getStringValue());
            // The QNameValue constructor does not check the prefix
            if (!parts[0].isEmpty() && !NameChecker.isValidNCName(parts[0])) {
                throw new XPathException("Malformed prefix in QName: '" + parts[0] + '\'', "FOCA0002");
            }
            return new QNameValue(parts[0], NamespaceUri.of(uri), parts[1], BuiltInAtomicType.QNAME, true);
        } catch (QNameException e) {
            throw new XPathException(e.getMessage(), "FOCA0002");
        } catch (XPathException err) {
            throw err.replacingErrorCode("FORG0001", "FOCA0002");
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
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return expandedQName(
                (StringValue) arguments[0].head(),
                (StringValue) arguments[1].head()
        );
    }

    /**
     * Make an elaborator for a system function call on this function
     *
     * @return a suitable elaborator; or null if no custom elaborator is available
     */
    @Override
    public Elaborator getElaborator() {
        return new QNameFnElaborator();
    }

    public static class QNameFnElaborator extends ItemElaborator {

        public ItemEvaluator elaborateForItem() {
            SystemFunctionCall sfc = (SystemFunctionCall) getExpression();
            if (sfc.getArity() == 2) {
                ItemEvaluator arg0eval = sfc.getArg(0).makeElaborator().elaborateForItem();
                ItemEvaluator arg1eval = sfc.getArg(1).makeElaborator().elaborateForItem();
                return context -> expandedQName((StringValue) arg0eval.eval(context), (StringValue) arg1eval.eval(context));
            } else {
                ItemEvaluator arg0eval = sfc.getArg(0).makeElaborator().elaborateForItem();
                NamespaceResolver resolver = sfc.getRetainedStaticContext();
                return context -> {
                    Item in = arg0eval.eval(context);
                    if (in == null) {
                        return null;
                    }
                    StructuredQName qn = StructuredQName.fromLexicalQName(in.getStringValue(), false, true, resolver);
                    return new QNameValue(qn, BuiltInAtomicType.QNAME);
                };
            }
        }

    }
}

