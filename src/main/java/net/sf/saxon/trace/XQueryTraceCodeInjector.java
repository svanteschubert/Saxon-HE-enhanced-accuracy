////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2021 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.LetExpression;
import net.sf.saxon.expr.instruct.FixedAttribute;

/**
 * A code injector designed to support the -T tracing option in XQuery
 */
public class XQueryTraceCodeInjector extends TraceCodeInjector {

    @Override
    protected boolean isApplicable(Expression exp) {
        return exp.isInstruction() || exp instanceof LetExpression || exp instanceof FixedAttribute;
    }
}

