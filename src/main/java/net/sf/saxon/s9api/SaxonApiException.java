////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.expr.instruct.TerminationException;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpInjectMembers;
import net.sf.saxon.transpile.CSharpModifiers;

/**
 * An exception thrown by the Saxon s9api API. This is always a wrapper for some other underlying exception
 */
@CSharpInjectMembers(code="public override string Message {get => InnerException.Message;}")

public class SaxonApiException extends Exception {

    /**
     * Create a SaxonApiException
     *
     * @param cause the underlying cause of the exception
     */

    public SaxonApiException(Throwable cause) {
        super(cause);
    }

    /**
     * Create a SaxonApiException
     *
     * @param cause the underlying cause of the exception
     */

    public SaxonApiException(UncheckedXPathException cause) {
        super(cause.getXPathException());
    }

    /**
     * Create a SaxonApiException
     *
     * @param message the message
     */

    public SaxonApiException(String message) {
        super(new XPathException(message));
    }

    /**
     * Create a SaxonApiException
     *
     * @param message the message
     * @param cause   the underlying cause of the exception
     */

    public SaxonApiException(String message, Throwable cause) {
        super(new XPathException(message, cause));
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

    /**
     * Get the error code associated with the exception, if there is one
     *
     * @return the associated error code, or null if no error code is available
     * @since 9.3
     */

    /*@Nullable*/
    public QName getErrorCode() {
        Throwable cause = getCause();
        if (cause instanceof XPathException) {
            StructuredQName code = ((XPathException) cause).getErrorCodeQName();
            return code == null ? null : new QName(code);
        } else {
            return null;
        }
    }

    /**
     * Get the line number associated with the exception, if known.
     * @return the line number (typically of a line in a stylesheet, query, or schema, but
     * in the case of validation errors it may be a line in a source document); or -1 if not known
     * @since 9.6
     */

    public int getLineNumber() {
        Throwable cause = getCause();
        if (cause instanceof XPathException) {
            Location loc = ((XPathException) cause).getLocator();
            return loc == null ? -1 : loc.getLineNumber();
        } else {
            return -1;
        }
    }

    /**
     * Get the URI of the module associated with the exception, if known.
     * @return the URI of the module (typically a stylesheet, query, or schema, but
     * in the case of validation errors it may be a source document); or null if not known
     * @since 9.6
     */

    public String getSystemId() {
        Throwable cause = getCause();
        if (cause instanceof XPathException) {
            Location loc = ((XPathException) cause).getLocator();
            return loc == null ? null : loc.getSystemId();
        } else {
            return null;
        }
    }

    /**
     * When a transformation is terminated using <code>xsl:message</code> with
     * <code>terminate="yes"</code>, or using <code>xsl:assert</code>,
     * this method provides access to the content
     * of the final message that caused termination.
     * @return the content of the final <code>xsl:message</code> or <code>xsl:assert</code>
     * that caused termination; otherwise null.
     */

    public Message getTerminatingXsltMessage() {
        Throwable cause = getCause();
        if (cause instanceof TerminationException) {
            return ((TerminationException)cause).getFinalMessage();
        }
        return null;
    }
}

