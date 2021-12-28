////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

/**
 * Interface implemented by functions that have a "push" implementation, whereby the result
 * of the function is written incrementally to an {@link Outputter} rather than being
 * returned as the result of a {@code call()} method.
 */

public interface PushableFunction {

    /**
     * Evaluate the function in "push" mode
     * @param destination the destination for the function result
     * @param context the dynamic evaluation context
     * @param arguments the supplied arguments to the function
     * @throws XPathException if a dynamic error occurs during the evaluation
     */

    void process(Outputter destination, XPathContext context, Sequence[] arguments) throws XPathException;
}

