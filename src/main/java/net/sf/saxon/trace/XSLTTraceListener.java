////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.LetExpression;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.parser.CodeInjector;
import net.sf.saxon.functions.Trace;
import net.sf.saxon.lib.NamespaceConstant;

/**
 * A Simple trace listener for XSLT that writes messages (by default) to System.err
 */

public class XSLTTraceListener extends AbstractTraceListener {

    @Override
    public CodeInjector getCodeInjector() {
        return new XSLTTraceCodeInjector();
    }

    /**
     * Generate attributes to be included in the opening trace element
     */

    @Override
    protected String getOpeningAttributes() {
        return "xmlns:xsl=\"" + NamespaceConstant.XSLT + '\"';
    }

    /**
     * Get the trace element tagname to be used for a particular construct. Return null for
     * trace events that are ignored by this trace listener.
     * @param info
     */

    /*@Nullable*/
    @Override
    protected String tag(Traceable info) {
        return tagName(info);
    }

    public static String tagName(Traceable info) {
        if (info instanceof Expression) {
            Expression expr = (Expression) info;
            if (expr instanceof FixedElement) {
                return "LRE";
            } else if (expr instanceof FixedAttribute) {
                return "ATTR";
            } else if (expr instanceof LetExpression) {
                return "xsl:variable";
            } else if (expr.isCallOn(Trace.class)) {
                return "fn:trace";
            } else  {
                return expr.getExpressionName();
            }
        } else if (info instanceof UserFunction){
            return "xsl:function";
        } else if (info instanceof TemplateRule) {
            return "xsl:template";
        } else if (info instanceof NamedTemplate) {
            return "xsl:template";
        } else if (info instanceof GlobalParam) {
            return "xsl:param";
        } else if (info instanceof GlobalVariable) {
            return "xsl:variable";
        } else if (info instanceof Trace) {
            return "fn:trace";
        } else {
            return "misc";
        }
    }

}


