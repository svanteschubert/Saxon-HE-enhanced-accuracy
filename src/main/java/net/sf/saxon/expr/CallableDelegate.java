////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

/**
 * An implementation of {@link Callable} that allows the logic to be supplied as a lambda expression.
 *
 * <p>On Java, it's possible to assign a lambda expression directly to a Callable. But on C#, a Callable
 * isn't a delegate, so lambda expressions can't be used directly; instead, this class is provided as a proxy.</p>
 */

public class CallableDelegate implements Callable {

    @FunctionalInterface
    public interface Lambda {
        Sequence call(XPathContext context, Sequence[] arguments) throws XPathException;
    }

    private final Lambda expression;

    /**
     * Construct a {@link Callable} by supplying an implementation in the form of a lambda expression
     * @param expression a lambda expression (typically) that is invoked to evaluate the {@link Callable}
     */

    public CallableDelegate(Lambda expression) {
        this.expression = expression;
    }

    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return expression.call(context, arguments);
    }
}

