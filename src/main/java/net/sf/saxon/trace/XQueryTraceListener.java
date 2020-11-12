////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.flwor.ClauseInfo;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.functions.Trace;
import net.sf.saxon.om.Item;
import net.sf.saxon.query.XQueryExpression;
import net.sf.saxon.trans.Mode;

/**
 * A Simple trace listener for XQuery that writes messages (by default) to System.err
 */

public class XQueryTraceListener extends AbstractTraceListener {

    /**
     * Generate attributes to be included in the opening trace element
     */

    /*@NotNull*/
    @Override
    protected String getOpeningAttributes() {
        return "";
    }

    /**
     * Get the trace element tagname to be used for a particular construct. Return null for
     * trace events that are ignored by this trace listener.
     * @param info
     */

    /*@Nullable*/
    @Override
    protected String tag(Traceable info) {
        if (info instanceof TraceableComponent) {
            if (info instanceof GlobalVariable) {
                return "variable";
            } else if (info instanceof UserFunction) {
                return "function";
            } else if (info instanceof XQueryExpression) {
                return "query";
            } else {
                return "misc";
            }
        } else if (info instanceof Trace) {
            return "fn:trace";
        } else if (info instanceof ClauseInfo) {
            return ((ClauseInfo)info).getClause().getClauseKey().toString();
        } else if (info instanceof Expression) {
            String s = ((Expression)info).getExpressionName();
            if (s.startsWith("xsl:")) {
                s = s.substring(4);
            }
            switch (s) {
                case "value-of":
                    return "text";
                case "LRE":
                    return "element";
                case "ATTR":
                    return "attribute";
                default:
                    return s;
            }
        } else {
            return null;
        }
    }

    /**
     * Called at the start of a rule search
     */
    @Override
    public void startRuleSearch() {
        // do nothing
    }

    /**
     * Called at the end of a rule search
     * @param rule the rule that has been selected
     * @param mode
     * @param item
     */
    @Override
    public void endRuleSearch(Object rule, Mode mode, Item item) {
        // do nothing
    }

}

