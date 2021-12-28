////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.expr.BindingReference;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.trans.XPathException;

import java.util.Collections;

/**
 * An UndeclaredVariable object is created when a reference is encountered to a variable
 * that has not yet been declared. This can happen as a result of recursive module imports.
 * These references are resolved at the end of query parsing.
 */

public class UndeclaredVariable extends GlobalVariable {

    public UndeclaredVariable() {
    }

    public void transferReferences(GlobalVariable var) {
        for (BindingReference ref : references) {
            var.registerReference(ref);
        }
        references = Collections.emptyList();
    }

    /*@NotNull*/
    @Override
    public void compile(Executable exec, int slot) throws XPathException {
        throw new UnsupportedOperationException("Attempt to compile a place-holder for an undeclared variable");
    }
}

