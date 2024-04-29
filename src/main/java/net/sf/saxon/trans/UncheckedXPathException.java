////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.transpile.CSharpModifiers;

/**
 * When implementing certain interfaces Saxon is not able to throw a checked exception because
 * the interface definition does not allow it. In these circumstances the checked exception
 * is wrapped in an unchecked exception, which is thrown in its place. The intent is that
 * the unchecked exception will be caught and "unwrapped" before the calling application
 * gets to see it.
 *
 * <p>User-written callback functions (such as {@link net.sf.saxon.lib.ErrorReporter} may also
 * throw an {@code UncheckedXPathException}; this will generally cause the query or transformation
 * to be aborted.</p>
 */

public class UncheckedXPathException extends RuntimeException {

    /**
     * Create an unchecked XPath exception that wraps a supplied checked exception
     * @param cause the checked exception to be wrapped
     */

    public UncheckedXPathException(XPathException cause) {
        super(cause);
    }

    /**
     * Create an unchecked XPath exception with supplied error message
     * @param message the error message
     */

    @CSharpModifiers(code = {"public", "override"})
    public UncheckedXPathException(String message) {
        super(new XPathException(message));
    }

    /**
     * Create an unchecked XPath exception with supplied error message and error code
     * @param message the error message
     * @param errorCode the local part of the error code
     */

    public UncheckedXPathException(String message, String errorCode) {
        super(new XPathException(message, errorCode));
    }

    public UncheckedXPathException(Throwable cause) {
        super(new XPathException(cause));
    }

    /**
     * Get the underlying (checked) XPathException
     * @return the checked XPathException wrapped by this UncheckedXPathException
     */
    public XPathException getXPathException() {
        return (XPathException)getCause();
    }

    @Override
    @CSharpModifiers(code = {"public", "override"})
    public String getMessage() {
        return getCause().getMessage();
    }
}
