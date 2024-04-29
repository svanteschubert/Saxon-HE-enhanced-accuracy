////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.StaticFunctionCall;
import net.sf.saxon.expr.instruct.OriginalFunction;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;

import java.util.List;
import java.util.Map;

/**
 * A function library that recognizes the function name "xsl:original", which may appear within xsl:override
 */
public class XSLOriginalLibrary implements FunctionLibrary {

    private static final XSLOriginalLibrary THE_INSTANCE = new XSLOriginalLibrary();

    public static XSLOriginalLibrary getInstance() {
        return THE_INSTANCE;
    }

    public static StructuredQName XSL_ORIGINAL = new StructuredQName("xsl", NamespaceUri.XSLT, "original");

    private XSLOriginalLibrary() {}

    @Override
    public Expression bind(SymbolicName.F functionName, Expression[] staticArgs, Map<StructuredQName, Integer> keywords, StaticContext env, List<String> reasons) {
        try {
            FunctionItem target = getFunctionItem(functionName, env);
            if (target == null) {
                return null;
            } else {
                return new StaticFunctionCall(target, staticArgs);
            }
        } catch (XPathException e) {
            reasons.add(e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isAvailable(SymbolicName.F functionName, int languageLevel) {
        // xsl:original is not recognized by function-available() - W3C bug 28122
        return false;
    }

    @Override
    public FunctionLibrary copy() {
        return this;
    }

    @Override
    public FunctionItem getFunctionItem(SymbolicName.F functionName, StaticContext env) throws XPathException {
        if (functionName.getComponentKind() == StandardNames.XSL_FUNCTION &&
                functionName.getComponentName().hasURI(NamespaceUri.XSLT) &&
                functionName.getComponentName().getLocalPart().equals("original") &&
                env instanceof ExpressionContext) {
            ExpressionContext expressionContext = (ExpressionContext) env;
            StyleElement containingElement = expressionContext.getStyleElement();
            XSLFunction overridingFunction = (XSLFunction)containingElement.findAncestorElement(StandardNames.XSL_FUNCTION);
            if (overridingFunction == null) {
                throw new XPathException("Function name xsl:original can only be used within xsl:function", "XTSE3058");
            }
            SymbolicName originalName = overridingFunction.getSymbolicName();
            StyleElement override = (StyleElement)overridingFunction.getParent();
            if (!(override instanceof XSLOverride)) {
                throw new XPathException("Function name xsl:original can only be used within xsl:override", "XPST0017");
            }
            XSLUsePackage use = (XSLUsePackage) override.getParent();
            assert use != null;
            Component overridden = use.getUsedPackage().getComponent(originalName);
            if (overridden == null) {
                throw new XPathException("Function " + originalName + " does not exist in used package", "XTSE3058");
            }
            return new OriginalFunction(overridden);
        } else {
            return null;
        }
    }
}

