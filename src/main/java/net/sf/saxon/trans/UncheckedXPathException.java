////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

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

    private XPathException cause;

    public UncheckedXPathException(XPathException cause) {
        this.cause = cause;
    }

    public XPathException getXPathException() {
        return cause;
    }

    @Override
    public String getMessage() {
        return cause.getMessage();
    }
}
