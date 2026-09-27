////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.om.*;
import net.sf.saxon.str.StringTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;

/**
 * This class supports the XSLT element-available function.  Note that when running in a 2.0 processor,
 * it only looks for XSLT 2.0 instructions; but when running in a 3.0 processor, it recognizes all
 * elements in the XSLT namespace whether or not they are classified as instructions.
 */

public class ElementAvailable extends SystemFunction {

    public static boolean isXslt30Element(int fp) {
        return switch (fp) {
            case StandardNames.XSL_ACCEPT, StandardNames.XSL_ACCUMULATOR, StandardNames.XSL_ACCUMULATOR_RULE,
                 StandardNames.XSL_ANALYZE_STRING, StandardNames.XSL_APPLY_IMPORTS, StandardNames.XSL_APPLY_TEMPLATES,
                 StandardNames.XSL_ASSERT, StandardNames.XSL_ATTRIBUTE, StandardNames.XSL_ATTRIBUTE_SET,
                 StandardNames.XSL_BREAK, StandardNames.XSL_CALL_TEMPLATE, StandardNames.XSL_CATCH,
                 StandardNames.XSL_CHARACTER_MAP, StandardNames.XSL_CHOOSE, StandardNames.XSL_COMMENT,
                 StandardNames.XSL_CONTEXT_ITEM, StandardNames.XSL_COPY, StandardNames.XSL_COPY_OF,
                 StandardNames.XSL_DECIMAL_FORMAT, StandardNames.XSL_DOCUMENT, StandardNames.XSL_ELEMENT,
                 StandardNames.XSL_EVALUATE, StandardNames.XSL_EXPOSE, StandardNames.XSL_FALLBACK,
                 StandardNames.XSL_FOR_EACH, StandardNames.XSL_FOR_EACH_GROUP, StandardNames.XSL_FORK,
                 StandardNames.XSL_FUNCTION, StandardNames.XSL_GLOBAL_CONTEXT_ITEM, StandardNames.XSL_IF,
                 StandardNames.XSL_IMPORT, StandardNames.XSL_IMPORT_SCHEMA, StandardNames.XSL_INCLUDE,
                 StandardNames.XSL_ITEM_TYPE, StandardNames.XSL_ITERATE, StandardNames.XSL_KEY, StandardNames.XSL_MAP,
                 StandardNames.XSL_MAP_ENTRY, StandardNames.XSL_MATCHING_SUBSTRING, StandardNames.XSL_MERGE,
                 StandardNames.XSL_MERGE_ACTION, StandardNames.XSL_MERGE_KEY, StandardNames.XSL_MERGE_SOURCE,
                 StandardNames.XSL_MESSAGE, StandardNames.XSL_MODE, StandardNames.XSL_NAMESPACE,
                 StandardNames.XSL_NAMESPACE_ALIAS, StandardNames.XSL_NEXT_ITERATION, StandardNames.XSL_NEXT_MATCH,
                 StandardNames.XSL_NON_MATCHING_SUBSTRING, StandardNames.XSL_NUMBER, StandardNames.XSL_ON_COMPLETION,
                 StandardNames.XSL_ON_EMPTY, StandardNames.XSL_ON_NON_EMPTY, StandardNames.XSL_OTHERWISE,
                 StandardNames.XSL_OUTPUT, StandardNames.XSL_OUTPUT_CHARACTER, StandardNames.XSL_OVERRIDE,
                 StandardNames.XSL_PACKAGE, StandardNames.XSL_PARAM, StandardNames.XSL_PERFORM_SORT,
                 StandardNames.XSL_PRESERVE_SPACE, StandardNames.XSL_PROCESSING_INSTRUCTION,
                 StandardNames.XSL_RESULT_DOCUMENT, StandardNames.XSL_SELECT, StandardNames.XSL_SEQUENCE,
                 StandardNames.XSL_SORT, StandardNames.XSL_SOURCE_DOCUMENT, StandardNames.XSL_STRIP_SPACE,
                 StandardNames.XSL_STYLESHEET, StandardNames.XSL_SWITCH, StandardNames.XSL_TEMPLATE,
                 StandardNames.XSL_TEXT, StandardNames.XSL_TRANSFORM, StandardNames.XSL_TRY,
                 StandardNames.XSL_USE_PACKAGE, StandardNames.XSL_VALUE_OF, StandardNames.XSL_VARIABLE,
                 StandardNames.XSL_WHEN, StandardNames.XSL_WHERE_POPULATED, StandardNames.XSL_WITH_PARAM -> true;
            default -> false;
        };
    }

    public static boolean isXslt40Element(int fp) {
        return isXslt30Element(fp)
                || fp == StandardNames.XSL_ARRAY
                || fp == StandardNames.XSL_ARRAY_MEMBER
                || fp == StandardNames.XSL_NOTE
                || fp == StandardNames.XSL_RECORD
                || fp == StandardNames.XSL_SELECT;
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
                String arg = ((StringLiteral) arguments[0]).stringify();
                StructuredQName elem = getElementName(arg);
                if (elem.hasURI(NamespaceUri.XSLT) && elem.getLocalPart().equals("evaluate")) {
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
     * @param targetEdition the target edition that the stylesheet is to run under, e.g. "HE", "JS"
     * @param context     the XPath evaluation context
     * @return true if the instruction is available, in the sense of the XSLT element-available() function
     * @throws XPathException if a dynamic error occurs (e.g., a bad QName)
     */

    private boolean isElementAvailable(String lexicalName, String targetEdition, XPathContext context) throws XPathException {

        StructuredQName qName = getElementName(lexicalName);

        if (qName.hasURI(NamespaceUri.XSLT)) {
            int fp = context.getConfiguration().getNamePool().getFingerprint(NamespaceUri.XSLT, qName.getLocalPart());
            int xsltVersion = getRetainedStaticContext().getPackageData().getHostLanguageVersion();
            if (fp == StandardNames.XSL_EVALUATE) {
                return !context.getConfiguration().getBooleanProperty(Feature.DISABLE_XSL_EVALUATE);
            }
            if (fp == StandardNames.XSL_IMPORT_SCHEMA) {
                return context.getConfiguration().isLicensedFeature(Configuration.LicenseFeature.SCHEMA_VALIDATION)
                        && targetEdition.equals("EE");
            }
            return xsltVersion == 40 ? isXslt40Element(fp) : isXslt30Element(fp);

        } else if (qName.hasURI(NamespaceUri.IXSL) && !targetEdition.equals("JS")) {
            return false;
        }
        return context.getConfiguration().isExtensionElementAvailable(qName);
    }

    private StructuredQName getElementName(String lexicalName) throws XPathException {
        try {
            if (NameChecker.isValidNCName(StringTool.codePoints(lexicalName))) {
                NamespaceUri uri = getRetainedStaticContext().getURIForPrefix("", true);
                return new StructuredQName("", uri, lexicalName);
            } else {
                return StructuredQName.fromLexicalQName(lexicalName, false,
                                                        StructuredQName.QUPL, getRetainedStaticContext());
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

