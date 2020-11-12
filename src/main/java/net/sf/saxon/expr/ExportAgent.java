////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;

/**
 * An export agent performs the job of exporting an expression to a SEF file. Normally
 * the expression itself acts as its own export agent, and includes an export() method
 * to do the exporting. In a few cases, notably literals containing function items,
 * extra machinery is required to export a value, and a
 * {@link net.sf.saxon.functions.hof.UserFunctionReference.BoundUserFunction}
 * in particular includes custom export methods to handle different cases.
 */

public interface ExportAgent {

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     * @throws XPathException if the export fails, for example if an expression is found that won't work
     *                        in the target environment.
     */

    void export(ExpressionPresenter out) throws XPathException;
}
