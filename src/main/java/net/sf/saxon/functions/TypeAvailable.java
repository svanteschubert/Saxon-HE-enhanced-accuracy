////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.JavaExternalObjectType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.BooleanValue;

/**
 * This class supports the XSLT fn:type-available() function.
 */

public class TypeAvailable extends SystemFunction {

    private boolean typeAvailable(String lexicalName, Configuration config) throws XPathException {
        StructuredQName qName;
        try {
            if (lexicalName.indexOf(':') < 0 && !lexicalName.startsWith("Q{")) {
                String uri = getRetainedStaticContext().getURIForPrefix("", true);
                qName = new StructuredQName("", uri, lexicalName);
            } else {
                qName = StructuredQName.fromLexicalQName(lexicalName,
                        false, true,
                        getRetainedStaticContext());
            }
        } catch (XPathException e) {
            e.setErrorCode("XTDE1428");
            throw e;
        }

        String uri = qName.getURI();
        if (uri.equals(NamespaceConstant.JAVA_TYPE)) {
            try {
                String className = JavaExternalObjectType.localNameToClassName(qName.getLocalPart());
                config.getClass(className, false, null);
                return true;
            } catch (XPathException err) {
                return false;
            }
        } else {
            SchemaType type = config.getSchemaType(qName);
            if (type == null) {
                return false;
            }
            return config.getXsdVersion() != 10
                    || !(type instanceof BuiltInAtomicType)
                    || ((BuiltInAtomicType) type).isAllowedInXSD10();
        }
    }

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
    public BooleanValue call(XPathContext context, Sequence[] arguments /*@NotNull*/) throws XPathException {
        String lexicalQName = arguments[0].head().getStringValue();
        return BooleanValue.get(typeAvailable(lexicalQName, context.getConfiguration()));
    }

    /**
     * Make an expression that either calls this function, or that is equivalent to a call
     * on this function
     *
     * @param arguments the supplied arguments to the function call
     * @return either a function call on this function, or an expression that delivers
     * the same result
     */
    @Override
    public Expression makeFunctionCall(Expression[] arguments) {
        try {
            if (arguments[0] instanceof Literal) {
                boolean b = typeAvailable(
                    ((Literal)arguments[0]).getValue().getStringValue(),
                    getRetainedStaticContext().getConfiguration());
                return Literal.makeLiteral(BooleanValue.get(b));
            }
        } catch (XPathException e) {
            // fall through
        }
        return super.makeFunctionCall(arguments);
    }
}

