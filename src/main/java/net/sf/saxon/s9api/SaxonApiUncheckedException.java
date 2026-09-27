////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.transpile.*;

/**
 * An unchecked exception thrown by the Saxon API. Unchecked exceptions are used only when errors occur in a method
 * for which the interface specification defines no checked exception, for example {@link java.util.Iterator#next()}.
 * The exception always wraps some underlying exception, which can be retrieved using {@link #getCause()}
 */
public class SaxonApiUncheckedException extends RuntimeException {

    /**
     * Create an unchecked exception
     *
     * @param err the underlying cause
     */
    
    public SaxonApiUncheckedException(Throwable err) {
        super(err);
        CSharp.emitCode("throw new NotImplementedException(err.Message);");
    }


    /**
     * Returns the detail message string of this throwable.
     *
     * @return the detail message string of this <code>Throwable</code> instance
     *         (which may be <code>null</code>).
     */
    @Override
    @CSharpModifiers(code={"public", "override"})
    public String getMessage() {
        return getCause().getMessage();
    }
}
