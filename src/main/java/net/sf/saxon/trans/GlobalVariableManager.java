////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.GlobalVariable;

/**
 * This interface provides access to a collection of global variables. This abstraction is used by the optimizer
 * to handle the rather different ways that global variables are managed in XSLT and XQuery, as a result of
 * XSLT packaging.
 */

public interface GlobalVariableManager {

    /**
     * Get an existing global variable whose initializer is a given expression,
     * if there is one. Note that this depends on the logic for detecting equivalence
     * of expressions, which is necessarily approximate. Expressions with dependencies
     * on the static context should never be considered equivalent. If no equivalent global
     * variable is found, return null. An implementation can always return null if
     * it wants to avoid a lengthy search.
     * @return an existing global variable with the same select expression, if one
     * can be found; otherwise null.
     */

    GlobalVariable getEquivalentVariable(Expression select);

    /**
     * Add a global variable to the collection
     * @param variable the variable to be added
     * @throws XPathException if errors occur (some implementations of the method have side-effects,
     * such as causing the variable declaration to be compiled)
     */

    void addGlobalVariable(GlobalVariable variable) throws XPathException;

}

// Copyright (c) 2018-2020 Saxonica Limited

