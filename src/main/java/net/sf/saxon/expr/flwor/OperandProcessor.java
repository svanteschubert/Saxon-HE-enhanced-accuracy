////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.expr.Operand;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpDelegate;

/**
 * Interface representing a visitor of the clauses of a FLWOR expression that can process and
 * modify the operands of each clause
 */
@FunctionalInterface
@CSharpDelegate(true)
public interface OperandProcessor {

    void processOperand(Operand expr) throws XPathException;

}

