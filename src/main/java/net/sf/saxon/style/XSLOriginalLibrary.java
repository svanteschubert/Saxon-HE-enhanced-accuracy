////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
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
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;

import java.util.List;

/**
 * A function library that recognizes the function name "xsl:original", which may appear within xsl:override
 */
public class XSLOriginalLibrary implements FunctionLibrary {

    private static XSLOriginalLibrary THE_INSTANCE = new XSLOriginalLibrary();

    public static XSLOriginalLibrary getInstance() {
        return THE_INSTANCE;
    }

    public static StructuredQName XSL_ORIGINAL = new StructuredQName("xsl", NamespaceConstant.XSLT, "original");

    private XSLOriginalLibrary() {}

    @Override
    public Expression bind(SymbolicName.F functionName, Expression[] staticArgs, StaticContext env, List<String> reasons) {
        try {
            Function target = getFunctionItem(functionName, env);
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
    public boolean isAvailable(SymbolicName.F functionName) {
        // xsl:original is not recognized by function-available() - W3C bug 28122
        return false;
    }

    @Override
    public FunctionLibrary copy() {
        return this;
    }

    @Override
    public Function getFunctionItem(SymbolicName.F functionName, StaticContext env) throws XPathException {
        if (functionName.getComponentKind() == StandardNames.XSL_FUNCTION &&
            functionName.getComponentName().hasURI(NamespaceConstant.XSLT) &&
            functionName.getComponentName().getLocalPart().equals("original") &&
            env instanceof ExpressionContext) {
            ExpressionContext expressionContext = (ExpressionContext) env;
            StyleElement overridingFunction = expressionContext.getStyleElement();
            while (!(overridingFunction instanceof XSLFunction)) {
                NodeInfo parent = overridingFunction.getParent();
                if (!(parent instanceof StyleElement)) {
                    return null;
                }
                overridingFunction = (StyleElement) parent;
            }
            SymbolicName originalName = ((XSLFunction) overridingFunction).getSymbolicName();
            XSLOverride override = (XSLOverride) overridingFunction.getParent();
            XSLUsePackage use = (XSLUsePackage) override.getParent();
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

