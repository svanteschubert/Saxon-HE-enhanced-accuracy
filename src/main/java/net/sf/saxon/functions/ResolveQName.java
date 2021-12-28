////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.QNameValue;


/**
 * This class supports the resolve-QName function in XPath 2.0
 */

public class ResolveQName extends SystemFunction {

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public ZeroOrOne call(XPathContext context, Sequence[] arguments) throws XPathException {
        AtomicValue lex = (AtomicValue) arguments[0].head();
        return new ZeroOrOne(
                lex == null ? null :
                        resolveQName(lex.getStringValueCS(), (NodeInfo) arguments[1].head()));
    }

    /**
     * Static method to resolve a lexical QName against the namespace context of a supplied element node
     *
     * @param lexicalQName the lexical QName
     * @param element the element node whose namespace context is to be used
     * @return the result of resolving the QName
     * @throws XPathException if the supplied value is not a valid lexical QName, or if its namespace
     * prefix is not in scope
     */

    /*@Nullable*/
    public static QNameValue resolveQName(CharSequence lexicalQName, NodeInfo element) throws XPathException {
        NamespaceResolver resolver = element.getAllNamespaces();
        StructuredQName qName = StructuredQName.fromLexicalQName(lexicalQName, true, false, resolver);
        return new QNameValue(qName, BuiltInAtomicType.QNAME);
    }

}

