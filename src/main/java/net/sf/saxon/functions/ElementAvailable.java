////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;

/**
 * This class supports the XSLT element-available function.  Note that when running in a 2.0 processor,
 * it only looks for XSLT 2.0 instructions; but when running in a 3.0 processor, it recognizes all
 * elements in the XSLT namespace whether or not they are classified as instructions.
 */

public class ElementAvailable extends SystemFunction {

    public static boolean isXslt30Element(int fp) {
        switch (fp) {
            case StandardNames.XSL_ACCEPT:
            case StandardNames.XSL_ACCUMULATOR:
            case StandardNames.XSL_ACCUMULATOR_RULE:
            case StandardNames.XSL_ANALYZE_STRING:
            case StandardNames.XSL_APPLY_IMPORTS:
            case StandardNames.XSL_APPLY_TEMPLATES:
            case StandardNames.XSL_ASSERT:
            case StandardNames.XSL_ATTRIBUTE:
            case StandardNames.XSL_ATTRIBUTE_SET:
            case StandardNames.XSL_BREAK:
            case StandardNames.XSL_CALL_TEMPLATE:
            case StandardNames.XSL_CATCH:
            case StandardNames.XSL_CHARACTER_MAP:
            case StandardNames.XSL_CHOOSE:
            case StandardNames.XSL_COMMENT:
            case StandardNames.XSL_CONTEXT_ITEM:
            case StandardNames.XSL_COPY:
            case StandardNames.XSL_COPY_OF:
            case StandardNames.XSL_DECIMAL_FORMAT:
            case StandardNames.XSL_DOCUMENT:
            case StandardNames.XSL_ELEMENT:
            case StandardNames.XSL_EVALUATE:
            case StandardNames.XSL_EXPOSE:
            case StandardNames.XSL_FALLBACK:
            case StandardNames.XSL_FOR_EACH:
            case StandardNames.XSL_FOR_EACH_GROUP:
            case StandardNames.XSL_FORK:
            case StandardNames.XSL_FUNCTION:
            case StandardNames.XSL_GLOBAL_CONTEXT_ITEM:
            case StandardNames.XSL_IF:
            case StandardNames.XSL_IMPORT:
            case StandardNames.XSL_IMPORT_SCHEMA:
            case StandardNames.XSL_INCLUDE:
            case StandardNames.XSL_ITERATE:
            case StandardNames.XSL_KEY:
            case StandardNames.XSL_MAP:
            case StandardNames.XSL_MAP_ENTRY:
            case StandardNames.XSL_MATCHING_SUBSTRING:
            case StandardNames.XSL_MERGE:
            case StandardNames.XSL_MERGE_ACTION:
            case StandardNames.XSL_MERGE_KEY:
            case StandardNames.XSL_MERGE_SOURCE:
            case StandardNames.XSL_MESSAGE:
            case StandardNames.XSL_MODE:
            case StandardNames.XSL_NAMESPACE:
            case StandardNames.XSL_NAMESPACE_ALIAS:
            case StandardNames.XSL_NEXT_ITERATION:
            case StandardNames.XSL_NEXT_MATCH:
            case StandardNames.XSL_NON_MATCHING_SUBSTRING:
            case StandardNames.XSL_NUMBER:
            case StandardNames.XSL_ON_COMPLETION:
            case StandardNames.XSL_ON_EMPTY:
            case StandardNames.XSL_ON_NON_EMPTY:
            case StandardNames.XSL_OTHERWISE:
            case StandardNames.XSL_OUTPUT:
            case StandardNames.XSL_OUTPUT_CHARACTER:
            case StandardNames.XSL_OVERRIDE:
            case StandardNames.XSL_PACKAGE:
            case StandardNames.XSL_PARAM:
            case StandardNames.XSL_PERFORM_SORT:
            case StandardNames.XSL_PRESERVE_SPACE:
            case StandardNames.XSL_PROCESSING_INSTRUCTION:
            case StandardNames.XSL_RESULT_DOCUMENT:
            case StandardNames.XSL_SEQUENCE:
            case StandardNames.XSL_SORT:
            case StandardNames.XSL_SOURCE_DOCUMENT:
            case StandardNames.XSL_STRIP_SPACE:
            case StandardNames.XSL_STYLESHEET:
            case StandardNames.XSL_TEMPLATE:
            case StandardNames.XSL_TEXT:
            case StandardNames.XSL_TRANSFORM:
            case StandardNames.XSL_TRY:
            case StandardNames.XSL_USE_PACKAGE:
            case StandardNames.XSL_VALUE_OF:
            case StandardNames.XSL_VARIABLE:
            case StandardNames.XSL_WHEN:
            case StandardNames.XSL_WHERE_POPULATED:
            case StandardNames.XSL_WITH_PARAM:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Special-case for element-available('xsl:evaluate') which may be dynamically-disabled,
     * and the spec says that this should be assessed at run-time.  By indicating that the
     * effect of the function depends on the run-time environment, early evaluation at compile
     * time is suppressed.
     */

    @Override
    public int getSpecialProperties(Expression[] arguments) {
        try {
            if (arguments[0] instanceof StringLiteral) {
                String arg = ((StringLiteral) arguments[0]).getStringValue();
                StructuredQName elem = getElementName(arg);
                if (elem.hasURI(NamespaceConstant.XSLT) && elem.getLocalPart().equals("evaluate")) {
                    return super.getSpecialProperties(arguments) | StaticProperty.DEPENDS_ON_RUNTIME_ENVIRONMENT;
                }
            }
        } catch (XPathException e) {
            // drop through
        }
        return super.getSpecialProperties(arguments);
    }

    /**
     * Determine whether a particular instruction is available. Returns true
     * for XSLT instructions, Saxon extension instructions, and registered
     * user-defined extension instructions. In XSLT 3.0 all XSLT elements are recognized.
     * This is a change from XSLT 2.0 where only elements classified as instructions
     * were recognized.
     *
     * @param lexicalName the lexical QName of the element
     * @param edition the target edition that the stylesheet is to run under, e.g. "HE", "JS"
     * @param context     the XPath evaluation context
     * @return true if the instruction is available, in the sense of the XSLT element-available() function
     * @throws XPathException if a dynamic error occurs (e.g., a bad QName)
     */

    private boolean isElementAvailable(String lexicalName, String edition, XPathContext context) throws XPathException {

        StructuredQName qName = getElementName(lexicalName);

        if (qName.hasURI(NamespaceConstant.XSLT)) {
            int fp = context.getConfiguration().getNamePool().getFingerprint(NamespaceConstant.XSLT, qName.getLocalPart());
            boolean known = isXslt30Element(fp);
            if (fp == StandardNames.XSL_EVALUATE) {
                known = known && !context.getConfiguration().getBooleanProperty(Feature.DISABLE_XSL_EVALUATE);
            }
            return known;
        } else if (qName.hasURI(NamespaceConstant.IXSL) && !edition.equals("JS")) {
            return false;
        }
        return context.getConfiguration().isExtensionElementAvailable(qName);
    }

    private StructuredQName getElementName(String lexicalName) throws XPathException {
        try {
            if (lexicalName.indexOf(':') < 0 && NameChecker.isValidNCName(lexicalName)) {
                String uri = getRetainedStaticContext().getURIForPrefix("", true);
                return new StructuredQName("", uri, lexicalName);
            } else {
                return StructuredQName.fromLexicalQName(lexicalName, false,
                                                        true, getRetainedStaticContext());
            }
        } catch (XPathException e) {
            XPathException err = new XPathException("Invalid element name passed to element-available(): " + e.getMessage());
            err.setErrorCode("XTDE1440");
            throw err;
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
    public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        String lexicalQName = arguments[0].head().getStringValue();
        boolean b = isElementAvailable(lexicalQName, getRetainedStaticContext().getPackageData().getTargetEdition(), context);
        return BooleanValue.get(b);
    }
}

